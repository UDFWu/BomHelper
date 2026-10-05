package com.scsb.bomhelper.service;

import com.scsb.bomhelper.config.GitLabProperties;
import com.scsb.bomhelper.dto.gitlab.GitLabGroup;
import com.scsb.bomhelper.dto.gitlab.GitLabProject;
import com.scsb.bomhelper.dto.gitlab.GitLabProjectMember;
import com.scsb.bomhelper.dto.gitlab.GitLabUser;
import com.scsb.bomhelper.dto.gitlab.OAuthTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 與 GitLab Server 溝通的服務：
 * 1. 使用帳號密碼向 GitLab OAuth 端點換取 access_token
 * 2. 取得當前使用者資料
 * 3. 取得使用者所屬的 Group、可存取的 Project
 *
 * 註：若使用者啟用 2FA，無法使用帳號密碼 grant_type=password，
 *     建議改用 Personal Access Token；本系統登入頁面也支援 PAT 模式。
 */
@Service
public class GitLabService {

    private static final Logger log = LoggerFactory.getLogger(GitLabService.class);

    private final RestClient gitLabRestClient;
    private final GitLabProperties properties;
    private volatile String adminAccessToken;
    private volatile Instant adminTokenExpiresAt = Instant.EPOCH;
    private record SupervisorDirectory(Map<String, List<GitLabProjectMember>> groups,
                                       Map<String, List<GitLabProjectMember>> projects,
                                       Map<String, String> groupPaths,
                                       Map<String, String> projectNames) { }
    private volatile SupervisorDirectory supervisorDirectory =
            new SupervisorDirectory(Map.of(), Map.of(), Map.of(), Map.of());

    /** Refresh after either local or GitLab login. Publish only a complete snapshot. */
    public synchronized void refreshSupervisorDirectory() {
        String token = getAdminAccessToken();
        if (token == null) return;
        try {
            Map<String, List<GitLabProjectMember>> groups = new HashMap<>();
            Map<String, List<GitLabProjectMember>> projects = new HashMap<>();
            Map<String, String> groupPaths = new HashMap<>();
            Map<String, String> projectNames = new HashMap<>();
            for (GitLabGroup group : fetchAllPages("/api/v4/groups?all_available=true", token, GitLabGroup[].class)) {
                String id = group.getId().toString();
                List<GitLabProjectMember> members = fetchSupervisors("groups", id, token);
                groups.put(id, members);
                if (group.getFullPath() != null) {
                    groups.put(group.getFullPath(), members);
                    groupPaths.put(id, group.getFullPath());
                }
            }
            for (GitLabProject project : fetchAllPages("/api/v4/projects?simple=true", token, GitLabProject[].class)) {
                String id = project.getId().toString();
                List<GitLabProjectMember> members = fetchSupervisors("projects", id, token);
                projects.put(id, members);
                if (project.getPathWithNamespace() != null) projects.put(project.getPathWithNamespace(), members);
                if (project.getName() != null) {
                    projectNames.put(id, project.getName());
                    if (project.getPathWithNamespace() != null) projectNames.put(project.getPathWithNamespace(), project.getName());
                }
            }
            supervisorDirectory = new SupervisorDirectory(Map.copyOf(groups), Map.copyOf(projects), Map.copyOf(groupPaths), Map.copyOf(projectNames));
            log.info("GitLab Group/Project 主管名單更新完成");
        } catch (Exception e) {
            // Do not log response bodies or credentials; retain the last complete snapshot.
            log.warn("GitLab 主管名單更新失敗，保留前次完整名單");
        }
    }

    private <T> List<T> fetchAllPages(String endpoint, String token, Class<T[]> type) {
        List<T> all = new ArrayList<>();
        int size = Math.max(1, Math.min(100, properties.getPageSize()));
        for (int page = 1; ; page++) {
            T[] items = gitLabRestClient.get()
                    .uri(endpoint + (endpoint.contains("?") ? "&" : "?") + "per_page=" + size + "&page=" + page)
                    .header("Authorization", "Bearer " + token).retrieve().body(type);
            if (items == null || items.length == 0) break;
            Collections.addAll(all, items);
            if (items.length < size) break;
        }
        return List.copyOf(all);
    }

    private List<GitLabProjectMember> fetchSupervisors(String resource, String id, String token) {
        return fetchAllPages("/api/v4/" + resource + "/" + id + "/members/all", token, GitLabProjectMember[].class)
                .stream().filter(member -> Integer.valueOf(50).equals(member.getAccessLevel())
                        || Integer.valueOf(40).equals(member.getAccessLevel())).toList();
    }

    public GitLabService(@Qualifier("gitLabRestClient") RestClient gitLabRestClient,
                         GitLabProperties properties) {
        this.gitLabRestClient = gitLabRestClient;
        this.properties = properties;
    }

    // =============================================================
    // OAuth：以帳號密碼換取 access_token
    // =============================================================

    /**
     * 透過 GitLab OAuth2 "Resource Owner Password Credentials" 流程取得 access_token。
     *
     * @throws GitLabAuthException 帳號密碼錯誤、2FA 限制或網路錯誤
     */
    public OAuthTokenResponse loginWithPassword(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", password);

        try {
            return gitLabRestClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes());
                        log.warn("GitLab OAuth 失敗：status={}, body={}", res.getStatusCode(), body);
                        if (res.getStatusCode().value() == 401) {
                            throw new GitLabAuthException("帳號或密碼錯誤。");
                        }
                        throw new GitLabAuthException("GitLab 認證失敗 (" + res.getStatusCode() + ")");
                    })
                    .body(OAuthTokenResponse.class);
        } catch (GitLabAuthException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("GitLab OAuth 連線錯誤：{}", e.getMessage());
            throw new GitLabAuthException("無法連線到 GitLab Server：" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("GitLab OAuth 未預期錯誤", e);
            throw new GitLabAuthException("GitLab 認證發生未預期錯誤：" + e.getMessage(), e);
        }
    }

    // =============================================================
    // 取得當前使用者
    // =============================================================

    public GitLabUser fetchCurrentUser(String accessToken) {
        return gitLabRestClient.get()
                .uri("/api/v4/user")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new GitLabAuthException("無法取得 GitLab 使用者資訊 (" + res.getStatusCode() + ")");
                })
                .body(GitLabUser.class);
    }

    // =============================================================
    // 取得使用者授權範圍：Groups / Projects
    // =============================================================

    /**
     * 取得當前使用者所屬的所有 Group（含子 Group），會自動分頁直到取完。
     */
    public List<GitLabGroup> fetchUserGroups(String accessToken) {
        List<GitLabGroup> all = new ArrayList<>();
        int page = 1;
        int perPage = properties.getPageSize();
        while (true) {
            String uri = String.format(
                    "/api/v4/groups?membership=true&all_available=false&per_page=%d&page=%d",
                    perPage, page);
            GitLabGroup[] groups = gitLabRestClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new GitLabAuthException("取得 Groups 失敗 (" + res.getStatusCode() + ")");
                    })
                    .body(GitLabGroup[].class);
            if (groups == null || groups.length == 0) {
                break;
            }
            Collections.addAll(all, groups);
            if (groups.length < perPage) {
                break;
            }
            page++;
            // 防呆：最多查詢 50 頁，避免不正常情況下無窮迴圈
            if (page > 50) break;
        }
        return all;
    }

    /**
     * 取得當前使用者「身為成員」可存取的 Project，分頁取完。
     */
    public List<GitLabProject> fetchUserProjects(String accessToken) {
        List<GitLabProject> all = new ArrayList<>();
        int page = 1;
        int perPage = properties.getPageSize();
        while (true) {
            String uri = String.format(
                    "/api/v4/projects?membership=true&simple=true&per_page=%d&page=%d",
                    perPage, page);
            GitLabProject[] projects = gitLabRestClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new GitLabAuthException("取得 Projects 失敗 (" + res.getStatusCode() + ")");
                    })
                    .body(GitLabProject[].class);
            if (projects == null || projects.length == 0) {
                break;
            }
            Collections.addAll(all, projects);
            if (projects.length < perPage) {
                break;
            }
            page++;
            if (page > 50) break;
        }
        return all;
    }

    /**
     * Retrieves project members, including inherited group members. GitLab access levels are
     * 50 for Owner and 40 for Maintainer.
     */
    public List<GitLabProjectMember> fetchProjectMembers(String projectId, String accessToken) {
        if (projectId == null || projectId.isBlank() || accessToken == null || accessToken.isBlank()) {
            return List.of();
        }

        try {
            String encodedProjectId = UriUtils.encodePathSegment(projectId, StandardCharsets.UTF_8);
            GitLabProjectMember[] members = gitLabRestClient.get()
                    .uri("/api/v4/projects/" + encodedProjectId + "/members/all?per_page=100")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new GitLabAuthException("取得 Project 成員失敗 (" + res.getStatusCode() + ")");
                    })
                    .body(GitLabProjectMember[].class);
            return members == null ? List.of() : List.of(members);
        } catch (Exception e) {
            log.warn("無法取得 GitLab Project {} 的 Owner/Maintainer：{}", projectId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Retrieves all direct and inherited Group members using the configured GitLab administrator.
     * This ensures every authorized search user can see the Group's Owner and Maintainer contacts.
     */
    public List<GitLabProjectMember> fetchGroupMembersAsAdmin(String groupId) {
        if (groupId == null || groupId.isBlank()) return List.of();

        List<GitLabProjectMember> cached = supervisorDirectory.groups().get(groupId);
        if (cached != null) return cached;

        String adminToken = getAdminAccessToken();
        if (adminToken == null) return List.of();

        List<GitLabProjectMember> all = new ArrayList<>();
        int page = 1;
        int perPage = Math.max(1, Math.min(100, properties.getPageSize()));
        try {
            while (true) {
                GitLabProjectMember[] members = gitLabRestClient.get()
                        .uri("/api/v4/groups/{group}/members/all?per_page={size}&page={page}", groupId, perPage, page)
                        .header("Authorization", "Bearer " + adminToken)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, res) -> {
                            throw new GitLabAuthException("取得 Group 成員失敗 (" + res.getStatusCode() + ")");
                        })
                        .body(GitLabProjectMember[].class);
                if (members == null || members.length == 0) break;
                Collections.addAll(all, members);
                if (members.length < perPage) break;
                page++;
            }
            return all;
        } catch (Exception e) {
            log.warn("無法取得 GitLab Group {} 的 Owner/Maintainer：{}", groupId, e.getMessage());
            return List.of();
        }
    }

    /** Project paths include the full namespace, so sibling projects never share contacts. */
    public List<GitLabProjectMember> fetchProjectMembersAsAdmin(String groupId, String projectId) {
        if (projectId == null || projectId.isBlank()) return List.of();
        SupervisorDirectory directory = supervisorDirectory;
        String namespacePath = directory.groupPaths().getOrDefault(groupId == null ? "" : groupId, groupId);
        String key = projectId.matches("[0-9]+") || projectId.contains("/")
                ? projectId : namespacePath + "/" + projectId;
        List<GitLabProjectMember> cached = directory.projects().get(key);
        if (cached != null) return cached;
        String token = getAdminAccessToken();
        if (token == null) return List.of();
        int perPage = Math.max(1, Math.min(100, properties.getPageSize()));
        List<GitLabProjectMember> all = new ArrayList<>();
        try {
            String namespace = groupId;
            if (!projectId.matches("[0-9]+") && !projectId.contains("/")
                    && groupId != null && groupId.matches("[0-9]+")) {
                GitLabGroup group = gitLabRestClient.get().uri("/api/v4/groups/{id}", groupId)
                        .header("Authorization", "Bearer " + token).retrieve().body(GitLabGroup.class);
                if (group == null || group.getFullPath() == null) return List.of();
                namespace = group.getFullPath();
            }
            String path = projectId.matches("[0-9]+") || projectId.contains("/")
                    ? projectId : namespace + "/" + projectId;
            for (int page = 1; ; page++) {
                // A URI template variable is encoded exactly once, including namespace slashes.
                GitLabProjectMember[] members = gitLabRestClient.get()
                        .uri("/api/v4/projects/{project}/members/all?per_page={size}&page={page}", path, perPage, page)
                        .header("Authorization", "Bearer " + token)
                        .retrieve().body(GitLabProjectMember[].class);
                if (members == null || members.length == 0) break;
                Collections.addAll(all, members);
                if (members.length < perPage) break;
            }
            return all;
        } catch (Exception e) {
            log.warn("無法取得 GitLab Project {}/{} 的 Owner/Maintainer", groupId, projectId);
            return List.of();
        }
    }

    /** Resolve the display name, rather than using the stored ID or namespace as a filename. */
    public String fetchProjectNameAsAdmin(String groupId, String projectId) {
        if (projectId == null || projectId.isBlank()) return null;
        SupervisorDirectory directory = supervisorDirectory;
        String namespace = directory.groupPaths().getOrDefault(groupId == null ? "" : groupId, groupId);
        String path = projectId.matches("[0-9]+") || projectId.contains("/")
                ? projectId : namespace + "/" + projectId;
        String name = directory.projectNames().get(path);
        if (name != null) return name;
        String token = getAdminAccessToken();
        if (token == null) return null;
        try {
            if (!projectId.matches("[0-9]+") && !projectId.contains("/")
                    && groupId != null && groupId.matches("[0-9]+") && !directory.groupPaths().containsKey(groupId)) {
                GitLabGroup group = gitLabRestClient.get().uri("/api/v4/groups/{id}", groupId)
                        .header("Authorization", "Bearer " + token).retrieve().body(GitLabGroup.class);
                if (group == null || group.getFullPath() == null) return null;
                path = group.getFullPath() + "/" + projectId;
            }
            GitLabProject project = gitLabRestClient.get().uri("/api/v4/projects/{project}", path)
                    .header("Authorization", "Bearer " + token).retrieve().body(GitLabProject.class);
            return project == null ? null : project.getName();
        } catch (Exception e) {
            log.warn("無法取得 GitLab Project 名稱，下載檔名使用專案代號");
            return null;
        }
    }

    private synchronized String getAdminAccessToken() {
        if (properties.getAdminToken() != null && !properties.getAdminToken().isBlank()) {
            return properties.getAdminToken();
        }
        if (adminAccessToken != null && Instant.now().isBefore(adminTokenExpiresAt)) {
            return adminAccessToken;
        }
        if (properties.getAdminUsername() == null || properties.getAdminUsername().isBlank()
                || properties.getAdminPassword() == null || properties.getAdminPassword().isBlank()) {
            log.warn("未設定 GitLab admin token 或管理者帳密，無法查詢主管名單。");
            return null;
        }
        try {
            OAuthTokenResponse token = loginWithPassword(properties.getAdminUsername(), properties.getAdminPassword());
            if (token == null || token.getAccessToken() == null || token.getAccessToken().isBlank()) {
                return null;
            }
            long expiresIn = token.getExpiresIn() == null ? 300 : token.getExpiresIn();
            adminAccessToken = token.getAccessToken();
            adminTokenExpiresAt = Instant.now().plusSeconds(Math.max(30, expiresIn - 30));
            return adminAccessToken;
        } catch (Exception e) {
            log.warn("GitLab 管理者登入失敗，無法查詢 Group Owner/Maintainer：{}", e.getMessage());
            return null;
        }
    }

    // =============================================================
    // Exception
    // =============================================================

    /**
     * GitLab 認證 / 連線失敗時拋出
     */
    public static class GitLabAuthException extends RuntimeException {
        public GitLabAuthException(String message) {
            super(message);
        }
        public GitLabAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
