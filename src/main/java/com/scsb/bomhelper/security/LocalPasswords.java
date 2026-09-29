package com.scsb.bomhelper.security;

import java.util.Map;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

public final class LocalPasswords {
    private LocalPasswords() { }

    public static PasswordEncoder encoder() {
        var pbkdf2 = new Pbkdf2PasswordEncoder("", 16, 600_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        String id = "pbkdf2-sha256-600000";
        return new DelegatingPasswordEncoder(id, Map.of(id, pbkdf2));
    }
}
