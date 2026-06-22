package com.scsb.bomhelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ComponentDto {

    @JacksonXmlProperty(isAttribute = true)
    private String type;

    @JacksonXmlProperty(isAttribute = true, localName = "bom-ref")
    private String bomRef;

    @JacksonXmlProperty(localName = "group")
    private String group;

    @JacksonXmlProperty(localName = "name")
    private String name;

    @JacksonXmlProperty(localName = "version")
    private String version;

    @JacksonXmlProperty(localName = "purl")
    private String purl;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getBomRef() { return bomRef; }
    public void setBomRef(String bomRef) { this.bomRef = bomRef; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getPurl() { return purl; }
    public void setPurl(String purl) { this.purl = purl; }
}
