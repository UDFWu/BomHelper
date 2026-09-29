package com.scsb.bomhelper.security;

/** Application-owned account rules. The database only persists account attributes. */
public final class AccountPolicy {
    public static final String ADMIN = "9999";
    public static final String SECURITY = "0170";
    public static final String GITLAB = "0113";

    private AccountPolicy() { }

    public static boolean isLocalRole(String role) {
        return SECURITY.equals(role) || ADMIN.equals(role);
    }

    public static boolean isActive(String status) { return "A".equals(status); }

    public static boolean isValidStatus(String status) {
        return isActive(status) || "D".equals(status);
    }

    public static boolean isActiveAdmin(String role, String status) {
        return ADMIN.equals(role) && isActive(status);
    }
}
