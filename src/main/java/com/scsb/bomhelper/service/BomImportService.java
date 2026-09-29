package com.scsb.bomhelper.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import javax.xml.stream.XMLStreamException;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.scsb.bomhelper.dto.CycloneDxBomDto;
import com.scsb.bomhelper.dto.DependencyDto;
import com.scsb.bomhelper.dto.VulnerabilityDto;
import com.scsb.bomhelper.entity.BomComponent;
import com.scsb.bomhelper.entity.BomDependency;
import com.scsb.bomhelper.entity.BomReport;
import com.scsb.bomhelper.entity.BomVulnerability;
import com.scsb.bomhelper.repository.BomReportRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.stream.XMLInputFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;

@Service
@Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
public class BomImportService {

    private final BomReportRepository bomReportRepository;
    private final XmlMapper xmlMapper;

    public BomImportService(BomReportRepository bomReportRepository) {
        this.bomReportRepository = bomReportRepository;

        // 💡 忽略 XML 命名空間，避免解析失敗
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        this.xmlMapper = new XmlMapper(inputFactory);
        this.xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 原始版本：projectId 由 XML metadata 推斷。
     * 給「使用者透過網頁上傳」的場景使用。
     */
    public void importSbom(MultipartFile file, String gitlabGroupId, String importedBy) throws Exception {
        importSbom(file, gitlabGroupId, null, importedBy);
    }

    /**
     * 進階版本：允許呼叫端（例如 Jenkins CI）直接指定 gitlabProjectId。
     * 若 gitlabProjectId 為 null/空字串，仍會回退到從 XML metadata.projectName 推斷。
     */
    public void importSbom(MultipartFile file,
                           String gitlabGroupId,
                           String gitlabProjectId,
                           String importedBy) throws Exception {

        // 1. 讀取原始 XML 內容
        byte[] bytes = file.getBytes();
        String rawXml;
        try {
            var reader = xmlMapper.getFactory().getXMLInputFactory()
                    .createXMLStreamReader(new ByteArrayInputStream(bytes));
            try {
                String encoding = reader.getEncoding();
                rawXml = new String(bytes, encoding == null ? StandardCharsets.UTF_8
                        : Charset.forName(encoding)).replaceFirst("^\\uFEFF", "");
            } finally {
                reader.close();
            }
        } catch (XMLStreamException | IllegalArgumentException e) {
            throw new IllegalArgumentException("無法解析 BOM XML 的編碼或格式。", e);
        }
        if (rawXml.contains("<!DOCTYPE")) {
            throw new IllegalArgumentException("BOM XML 不允許 DOCTYPE 或外部實體。");
        }

        // 2. 解析 XML 到 DTO
        CycloneDxBomDto dto;
        try {
            dto = xmlMapper.readValue(rawXml, CycloneDxBomDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("無法解析 BOM XML 格式。", e);
        }

        if (dto == null) {
            throw new IllegalArgumentException("無法解析 SBOM 檔案內容。");
        }

        // 3. 建立主檔實體 (BomReport)
        BomReport report = new BomReport();
        report.setGitlabGroupId(gitlabGroupId);
        report.setImportedBy(importedBy);

        // Preserve every line break and indentation. Downloads are encoded as UTF-8.
        String downloadXml = rawXml.replaceFirst(
                "(?s)^(<\\?xml\\s+[^?]*?encoding\\s*=\\s*)[\"'][^\"']+[\"']", "$1\"UTF-8\"");
        report.setRawXmlContent(downloadXml);
        report.setSerialNumber(dto.getSerialNumber());

        // 4. 解析 Metadata 資訊
        if (dto.getMetadata() != null) {

            // 4.1 處理掃描時間
            String timestampStr = dto.getMetadata().getTimestamp();
            if (timestampStr != null && !timestampStr.trim().isEmpty()) {
                try {
                    report.setTimestamp(Date.from(Instant.parse(timestampStr)));
                } catch (Exception e) {
                    System.err.println("時間解析失敗: " + timestampStr);
                }
            }

            // 4.2 擷取 Scan ID
            String scanId = dto.getMetadata().getScanId();
            if (scanId == null || scanId.trim().isEmpty()) {
                throw new IllegalArgumentException("XML 中缺少關鍵的 'Scan ID' 屬性。");
            }
            report.setScanId(scanId);

            // 4.4 處理專案代號
            String rawProjectName = dto.getMetadata().getProjectName();
            if (rawProjectName != null) {
                String prefix = "iq_application_";
                report.setGitlabProjectId(rawProjectName.startsWith(prefix) ?
                        rawProjectName.substring(prefix.length()) : rawProjectName);
            }
        }

        // 4.5 若呼叫端有明確指定 gitlabProjectId（例如 Jenkins 直接帶上來），則蓋過 XML 推斷的值
        if (gitlabProjectId != null && !gitlabProjectId.isBlank()) {
            report.setGitlabProjectId(gitlabProjectId);
        }

        report.setGitlabGroupId(requiredIdentifier(report.getGitlabGroupId(), "GitLab Group"));
        report.setGitlabProjectId(requiredIdentifier(report.getGitlabProjectId(), "GitLab Project"));
        report.setScanId(requiredIdentifier(report.getScanId(), "Scan ID"));
        report.setImportedBy(requiredIdentifier(importedBy, "上傳者"));

        // 5. 轉換 Components
        if (dto.getComponents() != null) {
            dto.getComponents().forEach(c -> {
                BomComponent component = new BomComponent();
                component.setGroupName(c.getGroup());
                component.setName(c.getName());
                component.setVersion(c.getVersion());
                component.setPurl(c.getPurl());
                component.setBomRef(c.getBomRef());

                report.addComponent(component);
            });
        }

        // 🚀 6. 轉換 Dependencies (相依關係)
        if (dto.getDependencies() != null) {
            for (DependencyDto parentDep : dto.getDependencies()) {
                String parentRef = parentDep.getRef();

                // 如果這個父節點底下有子節點，就把它們攤平存進資料庫
                if (parentRef != null && parentDep.getDependencies() != null) {
                    for (DependencyDto childDep : parentDep.getDependencies()) {
                        BomDependency dependency = new BomDependency();
                        dependency.setParentRef(parentRef);
                        dependency.setChildRef(childDep.getRef());

                        report.addDependency(dependency);
                    }
                }
            }
        }

        // 🚀 7. 轉換 Vulnerabilities (漏洞資訊)
        if (dto.getVulnerabilities() != null) {
            for (VulnerabilityDto vDto : dto.getVulnerabilities()) {
                BomVulnerability vuln = new BomVulnerability();
                vuln.setVulnId(vDto.getId());

                // 設定分數與嚴重等級
                vuln.setSeverity(vDto.getSeverity());
                vuln.setScore(vDto.getBaseScore());

                // 設定 CVSS 向量 (如果有的話)
                if (vDto.getRatings() != null && !vDto.getRatings().isEmpty()) {
                    vuln.setCvssVector(vDto.getRatings().get(0).getVector());
                }

                // 將 CWE List 轉為逗號分隔字串 (例如 "674,208")
                if (vDto.getCwes() != null && !vDto.getCwes().isEmpty()) {
                    String cweStr = vDto.getCwes().stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(","));
                    vuln.setCwe(cweStr);
                }

                // 設定受影響的套件 ref (用來關聯 BomComponent)
                vuln.setAffectedRef(vDto.getAffectedRef());

                report.addVulnerability(vuln);
            }
        }

        // 8. 儲存到資料庫 (透過 CascadeType.ALL 自動寫入所有關聯子表)
        // Serializable + database unique key protect concurrent imports, including first uploads.
        var previous = bomReportRepository.findByGitlabGroupIdAndGitlabProjectId(
                report.getGitlabGroupId(), report.getGitlabProjectId());
        var scanOwner = bomReportRepository.findByScanId(report.getScanId());
        if (scanOwner.isPresent() && previous.stream().noneMatch(
                old -> old.getId().equals(scanOwner.get().getId()))) {
            throw new IllegalArgumentException("此 Scan ID 已由另一個 GitLab 專案使用。");
        }
        bomReportRepository.deleteAll(previous);
        bomReportRepository.flush(); // Release ScanId and project keys before the replacement insert.
        bomReportRepository.saveAndFlush(report);
    }

    private String requiredIdentifier(String value, String label) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new IllegalArgumentException(label + " 必須為 1 至 100 字元。");
        }
        return value.trim();
    }
}
