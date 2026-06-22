package com.scsb.bomhelper.controller;

import com.scsb.bomhelper.service.BomImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CI / CD 整合用的端點 —— 給 Jenkins pipeline 直接上傳 CycloneDX BOM 報告。
 *
 * 設計重點：
 *  - 與使用者用的 /api/v1/bom/upload 區隔，路徑前綴為 /api/ci/v1/...
 *  - 在 SecurityConfig 中 permitAll，不需要登入
 *  - 回傳 JSON，方便 Jenkins shell 用 jq 或 curl 解析
 *
 * 安全性提醒：
 *   本端點不做身份驗證，僅適合於「內部網路（Jenkins → BomHelper）」場景。
 *   建議搭配下列至少一項保護：
 *     1. 反向代理 (nginx) 或防火牆只允許 Jenkins 主機 IP 存取
 *     2. 之後可擴充為共享 API Key (X-API-Key header) 驗證
 */
@RestController
@RequestMapping("/api/ci/v1/bom")
public class CiBomController {

    private static final Logger log = LoggerFactory.getLogger(CiBomController.class);

    private final BomImportService bomImportService;

    public CiBomController(BomImportService bomImportService) {
        this.bomImportService = bomImportService;
    }

    /**
     * Jenkins pipeline 直接上傳 BOM 報告。
     *
     * 範例 curl：
     *   curl -X POST http://<bomhelper-host>:8080/api/ci/v1/bom/upload \
     *        -F "file=@target/sbom-report.xml" \
     *        -F "gitlabGroupId=12345" \
     *        -F "gitlabProjectId=67890" \
     *        -F "importedBy=jenkins:my-pipeline#42"
     *
     * @param file              CycloneDX XML 檔案 (multipart/form-data)
     * @param gitlabGroupId     GitLab Group ID（必填）
     * @param gitlabProjectId   GitLab Project ID（必填；蓋過 XML metadata 自動推斷的值）
     * @param importedBy        匯入來源識別字串，預設 "jenkins"
     * @return                  JSON 結果
     */
    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadFromCi(
            @RequestParam("file") MultipartFile file,
            @RequestParam("gitlabGroupId") String gitlabGroupId,
            @RequestParam("gitlabProjectId") String gitlabProjectId,
            @RequestParam(value = "importedBy", required = false, defaultValue = "jenkins") String importedBy) {

        Map<String, Object> body = new LinkedHashMap<>();

        // 基本參數驗證
        if (file == null || file.isEmpty()) {
            body.put("success", false);
            body.put("message", "上傳的檔案為空。");
            return ResponseEntity.badRequest().body(body);
        }
        if (gitlabGroupId == null || gitlabGroupId.isBlank()) {
            body.put("success", false);
            body.put("message", "缺少必要參數：gitlabGroupId");
            return ResponseEntity.badRequest().body(body);
        }
        if (gitlabProjectId == null || gitlabProjectId.isBlank()) {
            body.put("success", false);
            body.put("message", "缺少必要參數：gitlabProjectId");
            return ResponseEntity.badRequest().body(body);
        }

        log.info("[CI] 收到 BOM 上傳：groupId={}, projectId={}, importedBy={}, fileName={}, size={}",
                gitlabGroupId, gitlabProjectId, importedBy,
                file.getOriginalFilename(), file.getSize());

        try {
            bomImportService.importSbom(file, gitlabGroupId, gitlabProjectId, importedBy);

            body.put("success", true);
            body.put("message", "BOM 檔案匯入成功");
            body.put("gitlabGroupId", gitlabGroupId);
            body.put("gitlabProjectId", gitlabProjectId);
            body.put("importedBy", importedBy);
            body.put("fileName", file.getOriginalFilename());
            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {
            // 商業邏輯錯誤（例如 ScanId 重複、XML 缺欄位）
            log.warn("[CI] BOM 匯入被拒：{}", e.getMessage());
            body.put("success", false);
            body.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(body);

        } catch (Exception e) {
            log.error("[CI] BOM 匯入失敗", e);
            body.put("success", false);
            body.put("message", "BOM 檔案處理失敗：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    /**
     * 健康檢查 —— Jenkins pipeline 啟動時可先打一次確認服務存活。
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "BomHelper CI Upload Endpoint");
        return ResponseEntity.ok(body);
    }
}
