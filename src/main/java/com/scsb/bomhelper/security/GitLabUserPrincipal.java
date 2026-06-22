package com.scsb.bomhelper.security;

import com.scsb.bomhelper.dto.gitlab.GitLabGroup;
import com.scsb.bomhelper.dto.gitlab.GitLabProject;
import com.scsb.bomhelper.dto.gitlab.GitLabUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring Security principal that carries every piece of GitLab data we need:
 *   - access_token   → for future GitLab API calls
 *   - groupIds       → identifiers used to filter BomReport rows the user can see
 *   - projectIds     → identifiers used to filter BomReport rows the user can see
 */
public class GitLabUserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final GitLabUser gitLabUser;
    private final String accessToken;
    private final List<GitLabGroup> groups;
    private final List<GitLabProject> projects;

    /**
     * Identifiers used to match against BomReport.gitlabGroupId.
     * Includes the numeric ID, path, and full_path of every Group the user belongs to.
     */
    private final List<String> authorizedGroupIds;

    /**
     * Identifiers used to match against BomReport.gitlabProjectId.
     * Includes the numeric ID, path, path_with_namespace, and name of every Project
     * the user belongs to.
     */
    private final List<String> authorizedProjectIds;

    public GitLabUserPrincipal(GitLabUser gitLabUser,
                               String accessToken,
                               List<GitLabGroup> groups,
                               List<GitLabProject> projects) {
        this.gitLabUser = gitLabUser;
        this.accessToken = accessToken;
        this.groups = groups == null ? Collections.emptyList() : groups;
        this.projects = projects == null ? Collections.emptyList() : projects;

        // Group identifier set (deduplicated, insertion-ordered)
        Set<String> groupIdentifiers = new LinkedHashSet<>();
        for (GitLabGroup g : this.groups) {
            addIfNotBlank(groupIdentifiers, g.getId() == null ? null : String.valueOf(g.getId()));
            addIfNotBlank(groupIdentifiers, g.getPath());
            addIfNotBlank(groupIdentifiers, g.getFullPath());
        }
        this.authorizedGroupIds = List.copyOf(groupIdentifiers);

        // Project identifier set
        Set<String> projectIdentifiers = new LinkedHashSet<>();
        for (GitLabProject p : this.projects) {
            addIfNotBlank(projectIdentifiers, p.getId() == null ? null : String.valueOf(p.getId()));
            addIfNotBlank(projectIdentifiers, p.getPath());
            addIfNotBlank(projectIdentifiers, p.getPathWithNamespace());
            addIfNotBlank(projectIdentifiers, p.getName());
        }
        this.authorizedProjectIds = List.copyOf(projectIdentifiers);
    }

    private static void addIfNotBlank(Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value);
        }
    }

    // ------- UserDetails -------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (Boolean.TRUE.equals(gitLabUser.getIsAdmin())) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"),
                           new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return gitLabUser.getUsername();
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() {
        return gitLabUser.getState() == null || "active".equalsIgnoreCase(gitLabUser.getState());
    }

    /** Display name for the UI. */
    public String getDisplayName() {
        return gitLabUser.getName() != null ? gitLabUser.getName() : gitLabUser.getUsername();
    }

    /**
     * Whether the user is a GitLab system administrator.
     * Admin users bypass Group / Project filtering and can see all reports.
     */
    public boolean isAdmin() {
        return Boolean.TRUE.equals(gitLabUser.getIsAdmin());
    }

    // ------- Accessors -------

    public GitLabUser getGitLabUser() { return gitLabUser; }
    public String getAccessToken() { return accessToken; }
    public List<GitLabGroup> getGroups() { return groups; }
    public List<GitLabProject> getProjects() { return projects; }
    public List<String> getAuthorizedGroupIds() { return authorizedGroupIds; }
    public List<String> getAuthorizedProjectIds() { return authorizedProjectIds; }
}
