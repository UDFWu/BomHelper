package com.scsb.bomhelper.controller;

import com.scsb.bomhelper.security.GitLabUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 登入相關頁面：
 * - GET /login    顯示登入畫面（已登入則直接導向首頁）
 * - GET /me       提供前端取得目前登入使用者資訊（含可存取的 group / project）
 *
 * 登入處理（POST /login）由 Spring Security 的 UsernamePasswordAuthenticationFilter 處理，
 * 委派給 GitLabAuthenticationProvider。
 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "登入失敗：請確認 GitLab 帳號密碼是否正確，"
                    + "或您的帳號是否啟用 2FA。");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "您已成功登出。");
        }
        return "login";
    }

    /**
     * 提供前端 (e.g. iframe page) 取得目前登入者資訊，含 GitLab 授權範圍。
     */
    @GetMapping(value = "/me", produces = "application/json")
    @org.springframework.web.bind.annotation.ResponseBody
    public Map<String, Object> currentUser(@AuthenticationPrincipal GitLabUserPrincipal principal) {
        if (principal == null) {
            return Map.of("authenticated", false);
        }
        return Map.of(
                "authenticated", true,
                "username", principal.getUsername(),
                "displayName", principal.getDisplayName(),
                "email", principal.getGitLabUser().getEmail() == null ? "" : principal.getGitLabUser().getEmail(),
                "isAdmin", Boolean.TRUE.equals(principal.getGitLabUser().getIsAdmin()),
                "groupIds", principal.getAuthorizedGroupIds(),
                "projectIds", principal.getAuthorizedProjectIds(),
                "groupCount", principal.getGroups().size(),
                "projectCount", principal.getProjects().size()
        );
    }

    /**
     * 提供前端取得目前登入者的 Groups 清單（id + name）
     */
    @GetMapping(value = "/me/groups", produces = "application/json")
    @org.springframework.web.bind.annotation.ResponseBody
    public List<Map<String, Object>> myGroups(@AuthenticationPrincipal GitLabUserPrincipal principal) {
        if (principal == null) {
            return List.of();
        }
        return principal.getGroups().stream()
                .map(g -> Map.<String, Object>of(
                        "id", g.getId() == null ? "" : String.valueOf(g.getId()),
                        "name", g.getName() == null ? "" : g.getName(),
                        "fullPath", g.getFullPath() == null ? "" : g.getFullPath()))
                .toList();
    }

    /**
     * 提供前端取得目前登入者的 Projects 清單（id + name + path）
     */
    @GetMapping(value = "/me/projects", produces = "application/json")
    @org.springframework.web.bind.annotation.ResponseBody
    public List<Map<String, Object>> myProjects(@AuthenticationPrincipal GitLabUserPrincipal principal) {
        if (principal == null) {
            return List.of();
        }
        return principal.getProjects().stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId() == null ? "" : String.valueOf(p.getId()),
                        "name", p.getName() == null ? "" : p.getName(),
                        "pathWithNamespace",
                        p.getPathWithNamespace() == null ? "" : p.getPathWithNamespace()))
                .toList();
    }
}
