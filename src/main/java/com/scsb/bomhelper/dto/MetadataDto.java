package com.scsb.bomhelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MetadataDto {

    @JacksonXmlProperty(localName = "timestamp")
    private String timestamp;

    @JacksonXmlElementWrapper(localName = "tools")
    @JacksonXmlProperty(localName = "tool")
    private List<Tool> tools;

    @JacksonXmlProperty(localName = "component")
    private Component component;

    @JacksonXmlElementWrapper(localName = "properties")
    @JacksonXmlProperty(localName = "property")
    private List<Property> properties;

    // ==========================================
    // Nested XML structure classes
    // ==========================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tool {
        @JacksonXmlProperty(localName = "vendor")
        private String vendor;

        @JacksonXmlProperty(localName = "name")
        private String name;

        @JacksonXmlProperty(localName = "version")
        private String version;

        public String getVendor() { return vendor; }
        public void setVendor(String vendor) { this.vendor = vendor; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Component {
        @JacksonXmlProperty(isAttribute = true)
        private String type;

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

        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getPurl() { return purl; }
        public void setPurl(String purl) { this.purl = purl; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Property {
        @JacksonXmlProperty(isAttribute = true)
        private String name;

        @JacksonXmlText
        private String value;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    // ==========================================
    // Helper methods (so service code stays clean)
    // ==========================================

    /** Get the Scan ID property. */
    public String getScanId() {
        if (this.properties != null) {
            for (Property prop : this.properties) {
                if ("Scan ID".equalsIgnoreCase(prop.getName())) {
                    return prop.getValue();
                }
            }
        }
        return null;
    }

    /** Get the project name (e.g., iq_application_...). */
    public String getProjectName() {
        return this.component != null ? this.component.getName() : null;
    }

    /** Get the scanning tool name (e.g., Nexus IQ Server). */
    public String getToolName() {
        if (this.tools != null && !this.tools.isEmpty()) {
            return this.tools.get(0).getName();
        }
        return null;
    }

    /** Get the scanning tool version. */
    public String getToolVersion() {
        if (this.tools != null && !this.tools.isEmpty()) {
            return this.tools.get(0).getVersion();
        }
        return null;
    }

    // ==========================================
    // Getters / Setters
    // ==========================================

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public List<Tool> getTools() { return tools; }
    public void setTools(List<Tool> tools) { this.tools = tools; }

    public Component getComponent() { return component; }
    public void setComponent(Component component) { this.component = component; }

    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }
}
