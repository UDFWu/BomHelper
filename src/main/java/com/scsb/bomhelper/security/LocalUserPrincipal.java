package com.scsb.bomhelper.security;

import com.scsb.bomhelper.dto.gitlab.GitLabUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Reuses the application's principal contract without granting GitLab administrator access. */
public class LocalUserPrincipal extends GitLabUserPrincipal {
    private final String authorityCode;

    public LocalUserPrincipal(String userId, String authorityCode) {
        super(identity(userId), null, List.of(), List.of());
        this.authorityCode = authorityCode;
    }

    private static GitLabUser identity(String userId) {
        var user = new GitLabUser();
        user.setUsername(userId);
        user.setName(userId);
        user.setState("active");
        user.setIsAdmin(false);
        return user;
    }

    public String getAuthorityCode() { return authorityCode; }

    @Override
    public boolean canViewAllReports() { return AccountPolicy.isLocalRole(authorityCode); }

    @Override
    public boolean isUserAdmin() { return AccountPolicy.ADMIN.equals(authorityCode); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_" + authorityCode));
    }
}
