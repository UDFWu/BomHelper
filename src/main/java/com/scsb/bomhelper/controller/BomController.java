package com.scsb.bomhelper.controller;

import com.scsb.bomhelper.entity.BomComponent;
import com.scsb.bomhelper.repository.BomComponentRepository;
import com.scsb.bomhelper.repository.BomDependencyRepository; // 💡 1. 補上 Import
import com.scsb.bomhelper.security.GitLabUserPrincipal;
import com.scsb.bomhelper.service.BomImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bom")
public class BomController {

    private static final Logger log = LoggerFactory.getLogger(BomController.class);

    private final BomImportService bomImportService;
    private final BomComponentRepository bomComponentRepository;
    private final BomDependencyRepository bomDependencyRepository; // 💡 2. 宣告 Repository

    // 💡 3. 將 BomDependencyRepository 加入建構子中，讓 Spring Boot 自動注入
    public BomController(BomImportService bomImportService,
                         BomComponentRepository bomComponentRepository,
                         BomDependencyRepository bomDependencyRepository) {
        this.bomImportService = bomImportService;
        this.bomComponentRepository = bomComponentRepository;
        this.bomDependencyRepository = bomDependencyRepository;
    }

    /**
     * 接收上傳的 CycloneDX BOM XML 檔案與專案權限資訊。
     * 改良：
     *  - importedBy 預設為登入的 GitLab 帳號（若前端未帶或為空）
     *  - 驗證登入者是否擁有指定 GitLab Group 的權限，避免越權上傳
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadBomFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("gitlabGroupId") String gitlabGroupId,
            @RequestParam(value = "importedBy", required = false) String importedBy,
            @AuthenticationPrincipal GitLabUserPrincipal principal) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("上傳的檔案為空。");
        }
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("尚未登入。");
        }

        // 權限檢查：使用者必須對指定的 GitLab Group 有權限；GitLab Admin 不受限制
        if (!principal.isAdmin()
                && !principal.getAuthorizedGroupIds().contains(gitlabGroupId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("您沒有 GitLab Group [" + gitlabGroupId + "] 的權限，無法上傳該報告。");
        }

        // 若前端沒傳 importedBy，就用目前登入的 GitLab 帳號
        String effectiveImportedBy = (importedBy != null && !importedBy.isBlank())
                ? importedBy
                : principal.getUsername();

        try {
            bomImportService.importSbom(file, gitlabGroupId, effectiveImportedBy);
            return ResponseEntity.ok("BOM 檔案解析並寫入資料庫成功！");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("BOM 檔案處理失敗: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchBom(
            @RequestParam("type") String searchType,
            @RequestParam("keyword") String keyword,
            @AuthenticationPrincipal GitLabUserPrincipal principal) {

        try {
            List<BomComponent> components;
            boolean isAdmin = principal != null && principal.isAdmin();

            if (isAdmin) {
                // ★ GitLab Admin：不受 Group / Project 限制，看全部資料
                if ("application".equalsIgnoreCase(searchType)) {
                    components = bomComponentRepository
                            .findByBomReport_GitlabProjectIdContainingIgnoreCase(keyword);
                } else {
                    components = bomComponentRepository.findByNameContainingIgnoreCase(keyword);
                }
            } else {
                // 一般使用者：依在 GitLab 上的 Group / Project 權限做過濾
                List<String> groupIds = principal == null ? List.of() : principal.getAuthorizedGroupIds();
                List<String> projectIds = principal == null ? List.of() : principal.getAuthorizedProjectIds();
                log.info("[Search] user={}, type={}, keyword={}, groupIds={}, projectIds={}",
                        principal == null ? "anonymous" : principal.getUsername(),
                        searchType, keyword, groupIds, projectIds);
                if (groupIds.isEmpty() && projectIds.isEmpty()) {
                    return ResponseEntity.ok(new ArrayList<>());
                }
                // SQL IN clause 不接受空集合，至少塞一個不可能值
                List<String> safeGroupIds = groupIds.isEmpty() ? List.of("__none__") : groupIds;
                List<String> safeProjectIds = projectIds.isEmpty() ? List.of("__none__") : projectIds;

                if ("application".equalsIgnoreCase(searchType)) {
                    // 模式 1：依專案名稱 (GitlabProjectId) 查詢該報表下所有組件 —— 套用權限過濾
                    components = bomComponentRepository.findAuthorizedComponentsByProjectId(
                            keyword, safeGroupIds, safeProjectIds);
                } else {
                    // 模式 2：依組件名稱 (Name) 查詢受影響的專案 —— 套用權限過濾
                    components = bomComponentRepository.findAuthorizedComponentsByName(
                            keyword, safeGroupIds, safeProjectIds);
                }
            }

            List<Map<String, Object>> resultList = new ArrayList<>();
            for (BomComponent comp : components) {
                Map<String, Object> map = new HashMap<>();
                map.put("componentName", comp.getName());
                map.put("componentVersion", comp.getVersion());
                map.put("purl", comp.getPurl());
                map.put("gitlabGroupId", comp.getBomReport().getGitlabGroupId());
                map.put("gitlabProjectId", comp.getBomReport().getGitlabProjectId());
                map.put("timestamp", comp.getBomReport().getTimestamp());

                // 💡 簡單判斷依賴關係：如果該套件在 Dependency 表中是別人的 Child，則標記為間接依賴
                boolean isTransitive = bomDependencyRepository.checkIsTransitive(
                        comp.getBomRef(), comp.getBomReport().getScanId());

                map.put("isTransitive", isTransitive);

                resultList.add(map);
            }
            return ResponseEntity.ok(resultList);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}