package com.scsb.bomhelper.service;

import com.scsb.bomhelper.config.GitLabProperties;
import com.scsb.bomhelper.dto.gitlab.GitLabGroup;
import com.scsb.bomhelper.dto.gitlab.GitLabProject;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
