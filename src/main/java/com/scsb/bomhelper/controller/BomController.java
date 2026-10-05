package com.scsb.bomhelper.controller;

import com.scsb.bomhelper.repository.BomReportRepository;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;

import com.scsb.bomhelper.entity.BomComponent;
import com.scsb.bomhelper.repository.BomComponentRepository;
import com.scsb.bomhelper.repository.BomDependencyRepository; // 💡 1. 補上 Import
import com.scsb.bomhelper.repository.BomVulnerabilityRepository;
import com.scsb.bomhelper.security.GitLabUserPrincipal;
import com.scsb.bomhelper.service.BomImportService;
import com.scsb.bomhelper.service.GitLabService;
import com.scsb.bomhelper.dto.gitlab.GitLabProjectMember;
import com.scsb.bomhelper.util.VersionComparator;
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
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/bom")
public class BomController {

    private static final Logger log = LoggerFactory.getLogger(BomController.class);

    private final BomImportService bomImportService;
    private final BomComponentRepository bomComponentRepository;
    private final BomDependencyRepository bomDependencyRepository; // 💡 2. 宣告 Repository
    private final BomVulnerabilityRepository bomVulnerabilityRepository;
    private final GitLabService gitLabService;
    private final BomReportRepository bomReportRepository;

    // 💡 3. 將 BomDependencyRepository 加入建構子中，讓 Spring Boot 自動注入
    public BomController(BomImportService bomImportService,
                         BomComponentRepository bomComponentRepository,
                         BomDependencyRepository bomDependencyRepository,
                         BomVulnerabilityRepository bomVulnerabilityRepository,
                         GitLabService gitLabService,
                         BomReportRepository bomReportRepository) {
        this.bomImportService = bomImportService;
        this.bomComponentRepository = bomComponentRepository;
        this.bomDependencyRepository = bomDependencyRepository;
        this.bomVulnerabilityRepository = bomVulnerabilityRepository;
        this.gitLabService = gitLabService;
        this.bomReportRepository = bomReportRepository;
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
        String effectiveImportedBy = principal.getUsername();

        try {
            bomImportService.importSbom(file, gitlabGroupId, effectiveImportedBy);
            return ResponseEntity.ok("BOM 檔案解析並寫入資料庫成功！");

        } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("報告匯入衝突，本次交易已回復，請重新上傳。");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("BOM import failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("BOM 檔案處理失敗，請確認 XML 格式或聯絡管理員。");
        }
    }

    @GetMapping("/reports/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Integer id,
            @AuthenticationPrincipal GitLabUserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        var report = bomReportRepository.findById(id).orElse(null);
        // Hide existence of reports outside the caller's existing search scope.
        if (report == null || !(principal.canViewAllReports()
                || principal.getAuthorizedGroupIds().contains(report.getGitlabGroupId())
                || principal.getAuthorizedProjectIds().contains(report.getGitlabProjectId()))) {
            return ResponseEntity.notFound().build();
        }
        byte[] xmlBytes = report.getRawXmlBytes();
        boolean originalUpload = xmlBytes != null;
        if (!originalUpload) {
            if (report.getRawXmlContent() == null) return ResponseEntity.notFound().build();
            xmlBytes = report.getRawXmlContent().getBytes(StandardCharsets.UTF_8);
        }
        String projectName = gitLabService.fetchProjectNameAsAdmin(report.getGitlabGroupId(), report.getGitlabProjectId());
        if (projectName == null || projectName.isBlank()) {
            projectName = report.getGitlabProjectId();
            projectName = projectName.substring(projectName.lastIndexOf('/') + 1);
        }
        String filename = projectName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip() + "-scan-report.xml";
        return ResponseEntity.ok()
                .contentType(originalUpload ? MediaType.APPLICATION_XML
                        : new MediaType("application", "xml", StandardCharsets.UTF_8))
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .header("Cache-Control", "no-store")
                .body(xmlBytes);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchBom(
            @RequestParam("type") String searchType,
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "versionOperator", required = false, defaultValue = "=") String versionOperator,
            @AuthenticationPrincipal GitLabUserPrincipal principal) {

        try {
            List<BomComponent> components;
            boolean isAdmin = principal != null && principal.canViewAllReports();
            boolean hasVersionFilter = "component".equalsIgnoreCase(searchType)
                    && version != null && !version.isBlank();
            if (hasVersionFilter && !Set.of("=", ">", ">=", "<", "<=").contains(versionOperator)) {
                return ResponseEntity.badRequest().build();
            }

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
                log.info("[Search] user={}, type={}, keyword={}, version={}, groupIds={}, projectIds={}",
                        principal == null ? "anonymous" : principal.getUsername(),
                        searchType, keyword, hasVersionFilter ? version : null, groupIds, projectIds);
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

            if (hasVersionFilter) {
                components = components.stream()
                        .filter(component -> matchesVersion(component.getVersion(), version, versionOperator))
                        .toList();
            }

            Map<String, Map<String, Double>> scoresByScanId = new HashMap<>();
            Map<List<String>, ProjectManagers> managersByProject = new HashMap<>();

            List<Map<String, Object>> resultList = new ArrayList<>();
            for (BomComponent comp : components) {
                String scanId = comp.getBomReport().getScanId();
                Map<String, Double> scoresByReference = scoresByScanId.computeIfAbsent(scanId,
                        this::findMaximumScoresByReference);
                ProjectManagers managers = managersByProject.computeIfAbsent(
                        List.of(comp.getBomReport().getGitlabGroupId(), comp.getBomReport().getGitlabProjectId()),
                        key -> findProjectManagers(key.get(0), key.get(1)));

                Map<String, Object> map = new HashMap<>();
                map.put("reportId", comp.getBomReport().getId());
                map.put("componentName", comp.getName());
                map.put("componentVersion", comp.getVersion());
                map.put("purl", comp.getPurl());
                map.put("gitlabGroupId", comp.getBomReport().getGitlabGroupId());
                map.put("gitlabProjectId", comp.getBomReport().getGitlabProjectId());
                map.put("timestamp", comp.getBomReport().getTimestamp());
                map.put("importedBy", comp.getBomReport().getImportedBy());
                map.put("vulnerabilityScore", scoresByReference.getOrDefault(comp.getBomRef(), 0.0));
                map.put("supervisors", managers.supervisors());

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

    private Map<String, Double> findMaximumScoresByReference(String scanId) {
        return bomVulnerabilityRepository.findMaximumScoresByScanId(scanId).stream()
                .filter(row -> row[0] != null && row[1] != null)
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).doubleValue()));
    }

    private ProjectManagers findProjectManagers(String groupId, String projectId) {
        List<GitLabProjectMember> members = gitLabService.fetchProjectMembersAsAdmin(groupId, projectId);
        List<String> owners = memberNames(members, 50);
        List<String> maintainers = memberNames(members, 40);
        List<String> supervisors = new ArrayList<>();
        owners.forEach(name -> supervisors.add("Owner：" + name));
        maintainers.forEach(name -> supervisors.add("Maintainer：" + name));
        return new ProjectManagers(supervisors);
    }

    private List<String> memberNames(List<GitLabProjectMember> members, int accessLevel) {
        return members.stream()
                .filter(member -> Integer.valueOf(accessLevel).equals(member.getAccessLevel()))
                .map(member -> member.getName() == null || member.getName().isBlank()
                        ? member.getUsername() : member.getName() + (member.getUsername() == null ? "" : " (@" + member.getUsername() + ")"))
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(Comparator.comparing(String::toLowerCase))
                .toList();
    }

    private boolean matchesVersion(String componentVersion, String requestedVersion, String operator) {
        int comparison = VersionComparator.compare(componentVersion, requestedVersion);
        return switch (operator) {
            case "=" -> comparison == 0;
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            default -> false;
        };
    }

    private record ProjectManagers(List<String> supervisors) { }
}
