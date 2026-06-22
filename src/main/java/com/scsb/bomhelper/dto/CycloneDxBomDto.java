package com.scsb.bomhelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CycloneDxBomDto {

    @JacksonXmlProperty(isAttribute = true)
    private String serialNumber;

    @JacksonXmlProperty(isAttribute = true)
    private String version;

    @JacksonXmlProperty(localName = "metadata")
    private MetadataDto metadata;

    @JacksonXmlElementWrapper(localName = "components")
    @JacksonXmlProperty(localName = "component")
    private List<ComponentDto> components;

    @JacksonXmlElementWrapper(localName = "externalReferences")
    @JacksonXmlProperty(localName = "reference")
    private List<ExternalReferenceDto> externalReferences;

    @JacksonXmlElementWrapper(localName = "dependencies")
    @JacksonXmlProperty(localName = "dependency")
    private List<DependencyDto> dependencies;

    @JacksonXmlElementWrapper(localName = "vulnerabilities")
    @JacksonXmlProperty(localName = "vulnerability")
    private List<VulnerabilityDto> vulnerabilities;

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public MetadataDto getMetadata() { return metadata; }
    public void setMetadata(MetadataDto metadata) { this.metadata = metadata; }

    public List<ComponentDto> getComponents() { return components; }
    public void setComponents(List<ComponentDto> components) { this.components = components; }

    public List<ExternalReferenceDto> getExternalReferences() { return externalReferences; }
    public void setExternalReferences(List<ExternalReferenceDto> externalReferences) {
        this.externalReferences = externalReferences;
    }

    public List<DependencyDto> getDependencies() { return dependencies; }
    public void setDependencies(List<DependencyDto> dependencies) { this.dependencies = dependencies; }

    public List<VulnerabilityDto> getVulnerabilities() { return vulnerabilities; }
    public void setVulnerabilities(List<VulnerabilityDto> vulnerabilities) {
        this.vulnerabilities = vulnerabilities;
    }
}
