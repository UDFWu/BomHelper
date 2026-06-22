package com.scsb.bomhelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalReferenceDto {

    @JacksonXmlProperty(isAttribute = true)
    private String type;

    @JacksonXmlProperty(localName = "url")
    private String url;

    @JacksonXmlProperty(localName = "comment")
    private String comment;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
