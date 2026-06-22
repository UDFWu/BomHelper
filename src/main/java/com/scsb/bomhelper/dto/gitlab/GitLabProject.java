package com.scsb.bomhelper.dto.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload item of GitLab /api/v4/projects.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabProject {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("path")
    private String path;

    @JsonProperty("path_with_namespace")
    private String pathWithNamespace;

    @JsonProperty("name_with_namespace")
    private String nameWithNamespace;

    @JsonProperty("web_url")
    private String webUrl;

    @JsonProperty("default_branch")
    private String defaultBranch;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getPathWithNamespace() { return pathWithNamespace; }
    public void setPathWithNamespace(String pathWithNamespace) { this.pathWithNamespace = pathWithNamespace; }

    public String getNameWithNamespace() { return nameWithNamespace; }
    public void setNameWithNamespace(String nameWithNamespace) { this.nameWithNamespace = nameWithNamespace; }

    public String getWebUrl() { return webUrl; }
    public void setWebUrl(String webUrl) { this.webUrl = webUrl; }

    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
}
