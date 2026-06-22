package com.scsb.bomhelper.config;

import com.scsb.bomhelper.security.GitLabUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全域 ControllerAdvice：將目前登入的 GitLab 使用者資訊注入到所有 Thymeleaf 模板中。
 * 在模板中可使用：
 *   ${currentUser?.displayName}
 *   ${currentUser?.gitLabUser?.email}
 *   ${currentUser != null}
 */
@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("currentUser")
    public GitLabUserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getPrincipal() instanceof GitLabUserPrincipal p) {
            return p;
        }
        return null;
    }
}
