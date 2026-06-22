package com.scsb.bomhelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DependencyDto {

    // attribute: ref="..."
    @JacksonXmlProperty(isAttribute = true, localName = "ref")
    private String ref;

    // Nested <dependency> children. Must set useWrapping = false because the inner
    // <dependency> tags are NOT wrapped by an extra <dependencies> element.
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "dependency")
    private List<DependencyDto> dependencies;

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }

    public List<DependencyDto> getDependencies() { return dependencies; }
    public void setDependencies(List<DependencyDto> dependencies) { this.dependencies = dependencies; }
}
