package com.scsb.bomhelper.util;

import com.scsb.bomhelper.security.LocalPasswords;
import java.util.Arrays;

/** Run with the application classpath; never pass plaintext passwords as command arguments. */
public final class LocalPasswordCli {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) throw new IllegalStateException("請在互動式終端機執行。");
        char[] password = console.readPassword("Password (8-256 characters): ");
        if (password == null) return;
        try {
            if (password.length < 8 || password.length > 256) {
                throw new IllegalArgumentException("密碼必須為 8 至 256 字元。");
            }
            System.out.println(LocalPasswords.encoder().encode(new String(password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
