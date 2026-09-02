package com.scsb.bomhelper.dto.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** GitLab project member returned by /projects/:id/members/all. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabProjectMember {

    @JsonProperty("name")
    private String name;

    @JsonProperty("username")
    private String username;

    @JsonProperty("access_level")
    private Integer accessLevel;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Integer getAccessLevel() { return accessLevel; }
    public void setAccessLevel(Integer accessLevel) { this.accessLevel = accessLevel; }
}
