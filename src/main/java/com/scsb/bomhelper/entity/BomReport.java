package com.scsb.bomhelper.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "\"BomReport\"")
public class BomReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "\"Id\"")
    private Integer id;

    @Column(name = "\"SerialNumber\"", length = 100)
    private String serialNumber;

    @Column(name = "\"ScanId\"", length = 100, nullable = false, unique = true)
    private String scanId;

    @Column(name = "\"GitlabGroupId\"", length = 100, nullable = false)
    private String gitlabGroupId;

    @Column(name = "\"GitlabProjectId\"", length = 100, nullable = false)
    private String gitlabProjectId;

    @Column(name = "\"Timestamp\"")
    private Date timestamp;

    @Column(name = "\"RawXmlContent\"", columnDefinition = "xml")
    private String rawXmlContent;

    @Column(name = "\"ImportedBy\"", length = 100, nullable = false)
    private String importedBy;

    @Column(name = "\"ImportDate\"", insertable = false, updatable = false)
    private Date importDate;

    // ==========================================
    // Relationships: One-to-Many
    // ==========================================

    @OneToMany(mappedBy = "bomReport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BomComponent> components = new ArrayList<>();

    @OneToMany(mappedBy = "bomReport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BomDependency> dependencies = new ArrayList<>();

    @OneToMany(mappedBy = "bomReport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BomVulnerability> vulnerabilities = new ArrayList<>();

    // ==========================================
    // Helper methods to keep both sides of the relationship in sync
    // ==========================================

    public void addComponent(BomComponent component) {
        components.add(component);
        component.setBomReport(this);
    }

    public void removeComponent(BomComponent component) {
        components.remove(component);
        component.setBomReport(null);
    }

    public void addDependency(BomDependency dependency) {
        dependencies.add(dependency);
        dependency.setBomReport(this);
    }

    public void removeDependency(BomDependency dependency) {
        dependencies.remove(dependency);
        dependency.setBomReport(null);
    }

    public void addVulnerability(BomVulnerability vulnerability) {
        vulnerabilities.add(vulnerability);
        vulnerability.setBomReport(this);
    }

    public void removeVulnerability(BomVulnerability vulnerability) {
        vulnerabilities.remove(vulnerability);
        vulnerability.setBomReport(null);
    }

    // ==========================================
    // Getters / Setters
    // ==========================================

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getScanId() { return scanId; }
    public void setScanId(String scanId) { this.scanId = scanId; }

    public String getGitlabGroupId() { return gitlabGroupId; }
    public void setGitlabGroupId(String gitlabGroupId) { this.gitlabGroupId = gitlabGroupId; }

    public String getGitlabProjectId() { return gitlabProjectId; }
    public void setGitlabProjectId(String gitlabProjectId) { this.gitlabProjectId = gitlabProjectId; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public String getRawXmlContent() { return rawXmlContent; }
    public void setRawXmlContent(String rawXmlContent) { this.rawXmlContent = rawXmlContent; }

    public String getImportedBy() { return importedBy; }
    public void setImportedBy(String importedBy) { this.importedBy = importedBy; }

    public Date getImportDate() { return importDate; }
    public void setImportDate(Date importDate) { this.importDate = importDate; }

    public List<BomComponent> getComponents() { return components; }
    public void setComponents(List<BomComponent> components) { this.components = components; }

    public List<BomDependency> getDependencies() { return dependencies; }
    public void setDependencies(List<BomDependency> dependencies) { this.dependencies = dependencies; }

    public List<BomVulnerability> getVulnerabilities() { return vulnerabilities; }
    public void setVulnerabilities(List<BomVulnerability> vulnerabilities) { this.vulnerabilities = vulnerabilities; }
}
