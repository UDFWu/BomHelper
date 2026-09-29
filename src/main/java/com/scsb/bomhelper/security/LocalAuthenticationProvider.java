package com.scsb.bomhelper.security;

import com.scsb.bomhelper.repository.BomUserRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import com.scsb.bomhelper.util.JasyptCli;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class LocalAuthenticationProvider implements AuthenticationProvider {
    private final BomUserRepository users;
    private final PasswordEncoder passwords = LocalPasswords.encoder();
    private final String dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    private final String masterKey;

    public LocalAuthenticationProvider(BomUserRepository users,
            @Value("${jasypt.encryptor.password:${JASYPT_ENCRYPTOR_PASSWORD:}}") String masterKey) {
        this.users = users;
        this.masterKey = masterKey;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        if (password.length() > 256) throw invalidCredentials();
        var user = users.findById(authentication.getName()).orElse(null);
        boolean matches;
        try {
            String stored = user == null ? dummyHash : user.getUserPassValidWord();
            if (stored != null && stored.startsWith("ENC(") && stored.endsWith(")")) {
                if (masterKey.isBlank()) throw invalidCredentials();
                String plain = JasyptCli.buildEncryptor(masterKey).decrypt(stored.substring(4, stored.length() - 1));
                matches = MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), plain.getBytes(StandardCharsets.UTF_8));
            } else {
                matches = passwords.matches(password, stored);
            }
        } catch (IllegalArgumentException | org.jasypt.exceptions.EncryptionOperationNotPossibleException ex) {
            throw invalidCredentials();
        }
        if (!matches || user == null || !AccountPolicy.isActive(user.getStatus())
                || !AccountPolicy.isLocalRole(user.getAuthorityCode())) throw invalidCredentials();
        var principal = new LocalUserPrincipal(user.getUserId(), user.getAuthorityCode());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("帳號或密碼錯誤，或帳號已停用。");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
