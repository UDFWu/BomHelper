package com.scsb.bomhelper.service;

import com.scsb.bomhelper.dto.gitlab.GitLabUser;
import com.scsb.bomhelper.entity.BomUser;
import com.scsb.bomhelper.security.AccountPolicy;
import com.scsb.bomhelper.repository.BomUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class GitLabUserSyncService {
    private final BomUserRepository users;

    public GitLabUserSyncService(BomUserRepository users) { this.users = users; }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public void synchronize(GitLabUser gitlab) {
        String username = gitlab.getUsername();
        if (username == null || username.isBlank() || username.length() > 100) {
            throw new BadCredentialsException("GitLab 帳號資料不完整。");
        }
        if (gitlab.getState() != null && !"active".equalsIgnoreCase(gitlab.getState())) {
            throw new BadCredentialsException("GitLab 帳號未啟用。");
        }
        var now = LocalDateTime.now();
        var user = users.findByUserIdIgnoreCase(username).orElse(null);
        if (user != null) {
            // Existing identities are never rewritten, even when they are local accounts.
            if (!AccountPolicy.isActive(user.getStatus())) {
                throw new BadCredentialsException("帳號已停用。");
            }
            return;
        }
        user = new BomUser();
        user.setUserId(username);
        user.setCreatedBy("system");
        user.setCreatedDate(now);
        user.setAuthorityCode(AccountPolicy.GITLAB);
        user.setUserPassValidWord(null);
        user.setStatus("A");
        user.setUpdatedBy("system");
        user.setUpdatedDate(now);
        users.saveAndFlush(user);
    }
}
