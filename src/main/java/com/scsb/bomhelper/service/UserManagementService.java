package com.scsb.bomhelper.service;

import com.scsb.bomhelper.entity.BomUser;
import com.scsb.bomhelper.repository.BomUserRepository;
import com.scsb.bomhelper.security.GitLabUserPrincipal;
import com.scsb.bomhelper.security.LocalPasswords;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import com.scsb.bomhelper.security.AccountPolicy;

@Service
public class UserManagementService {
    private final BomUserRepository users;
    private final EntityManager entityManager;
    private final PasswordEncoder passwords = LocalPasswords.encoder();

    public UserManagementService(BomUserRepository users, EntityManager entityManager) {
        this.users = users;
        this.entityManager = entityManager;
    }

    // Recheck persisted role/status: an old admin session must not bypass revocation.
    public void requireAdmin(GitLabUserPrincipal actor) {
        if (actor == null || !actor.isUserAdmin()) throw new AccessDeniedException("需要管理員權限。");
        var account = users.findById(actor.getUsername()).orElse(null);
        if (account == null || !AccountPolicy.isActiveAdmin(account.getAuthorityCode(), account.getStatus())) {
            throw new AccessDeniedException("管理員帳號已停用或權限已變更，請重新登入。");
        }
    }

    @Transactional(readOnly = true)
    public Page<UserSummary> list(GitLabUserPrincipal actor, int page) {
        requireAdmin(actor);
        return users.findAll(PageRequest.of(Math.max(0, page), 20, Sort.by("userId")))
                .map(u -> new UserSummary(u.getUserId(), u.getAuthorityCode(), u.getStatus(),
                        u.getCreatedBy(), u.getCreatedDate(), u.getUpdatedBy(), u.getUpdatedDate()));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public void create(GitLabUserPrincipal actor, String userId, String password, String confirmation,
                       String authorityCode, String status) {
        requireAdmin(actor);
        String id = userId == null ? "" : userId.trim();
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._@-]{0,99}")) {
            throw new IllegalArgumentException("帳號須為 1–100 個英數字或 . _ @ -，並以英數字開頭。");
        }
        if (password == null || password.length() < 8 || password.length() > 256 || password.isBlank()) {
            throw new IllegalArgumentException("密碼須為 8–256 字元。");
        }
        if (!password.equals(confirmation)) throw new IllegalArgumentException("兩次密碼輸入不一致。");
        if (!AccountPolicy.isLocalRole(authorityCode) || !AccountPolicy.isValidStatus(status)) {
            throw new IllegalArgumentException("請選擇有效的本機帳號權限與狀態；0113 帳號由 GitLab 同步。");
        }
        if (users.existsByUserIdIgnoreCase(id)) throw new IllegalArgumentException("此使用者帳號已存在。");
        var now = LocalDateTime.now();
        var account = new BomUser();
        account.setUserId(id);
        account.setUserPassValidWord(passwords.encode(password));
        account.setAuthorityCode(authorityCode);
        account.setStatus(status);
        account.setCreatedBy(actor.getUsername());
        account.setCreatedDate(now);
        account.setUpdatedBy(actor.getUsername());
        account.setUpdatedDate(now);
        // Insert only: concurrent creations cannot overwrite an existing account via JPA merge.
        entityManager.persist(account);
        entityManager.flush();
    }

    @Transactional
    public void updateStatus(GitLabUserPrincipal actor, String userId, String status) {
        requireAdmin(actor);
        if (!AccountPolicy.isValidStatus(status)) throw new IllegalArgumentException("帳號狀態僅可為 A（啟用）或 D（停用）。");
        var account = users.findByUserIdIgnoreCase(userId == null ? "" : userId.trim())
                .orElseThrow(() -> new IllegalArgumentException("找不到此使用者帳號。"));
        account.setStatus(status);
        account.setUpdatedBy(actor.getUsername());
        account.setUpdatedDate(LocalDateTime.now());
        users.saveAndFlush(account);
    }

    public record UserSummary(String userId, String authorityCode, String status, String createdBy,
                              LocalDateTime createdDate, String updatedBy, LocalDateTime updatedDate) { }
}
