package com.scsb.bomhelper.security;

import com.scsb.bomhelper.dto.gitlab.GitLabGroup;
import com.scsb.bomhelper.dto.gitlab.GitLabProject;
import com.scsb.bomhelper.dto.gitlab.GitLabUser;
import com.scsb.bomhelper.dto.gitlab.OAuthTokenResponse;
import com.scsb.bomhelper.service.GitLabService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自訂 AuthenticationProvider：
 * 將使用者輸入的帳號 / 密碼交給 GitLab Server 驗證，
 * 驗證成功後再撈取使用者的 Groups / Projects 權限，
 * 包裝成 GitLabUserPrincipal 存到 SecurityContext / Session。
 */
@Component
public class GitLabAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(GitLabAuthenticationProvider.class);

    private final GitLabService gitLabService;

    public GitLabAuthenticationProvider(GitLabService gitLabService) {
        this.gitLabService = gitLabService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = String.valueOf(authentication.getPrincipal());
        String password = String.valueOf(authentication.getCredentials());

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BadCredentialsException("請輸入 GitLab 帳號與密碼");
        }

        try {
            // 1) 用帳號密碼向 GitLab 換 access_token
            OAuthTokenResponse token = gitLabService.loginWithPassword(username, password);
            if (token == null || token.getAccessToken() == null) {
                throw new BadCredentialsException("GitLab 未回傳有效的 token");
            }

            // 2) 撈取當前使用者資料
            GitLabUser gitLabUser = gitLabService.fetchCurrentUser(token.getAccessToken());
            if (gitLabUser == null) {
                throw new BadCredentialsException("無法取得 GitLab 使用者資訊");
            }

            // 3) 撈取使用者擁有權限的 Groups / Projects（將用於弱點掃描資料過濾）
            List<GitLabGroup> groups = gitLabService.fetchUserGroups(token.getAccessToken());
            List<GitLabProject> projects = gitLabService.fetchUserProjects(token.getAccessToken());

            log.info("GitLab 登入成功：user={}, groups={}, projects={}",
                    gitLabUser.getUsername(),
                    groups.size(),
                    projects.size());

            GitLabUserPrincipal principal =
                    new GitLabUserPrincipal(gitLabUser, token.getAccessToken(), groups, projects);

            // 4) 建立通過驗證的 Authentication 物件，回傳給 Spring Security
            UsernamePasswordAuthenticationToken result =
                    new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
            result.setDetails(authentication.getDetails());
            return result;

        } catch (GitLabService.GitLabAuthException e) {
            log.warn("GitLab 認證失敗：{}", e.getMessage());
            throw new BadCredentialsException(e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
