package com.scsb.bomhelper.util;

import java.math.BigInteger;

/** Lightweight comparison for common dotted, dashed, and underscored package versions. */
public final class VersionComparator {

    private VersionComparator() { }

    public static int compare(String left, String right) {
        String[] leftParts = normalize(left).split("[._+\\-]");
        String[] rightParts = normalize(right).split("[._+\\-]");
        int size = Math.max(leftParts.length, rightParts.length);

        for (int i = 0; i < size; i++) {
            String leftPart = i < leftParts.length ? leftParts[i] : "";
            String rightPart = i < rightParts.length ? rightParts[i] : "";
            int comparison = comparePart(leftPart, rightPart);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static String normalize(String version) {
        if (version == null) return "";
        String normalized = version.trim();
        return normalized.startsWith("v") || normalized.startsWith("V")
                ? normalized.substring(1) : normalized;
    }

    private static int comparePart(String left, String right) {
        if (left.equals(right)) return 0;
        if (left.isEmpty()) return isZero(right) ? 0 : -1;
        if (right.isEmpty()) return isZero(left) ? 0 : 1;

        boolean leftNumeric = left.matches("\\d+");
        boolean rightNumeric = right.matches("\\d+");
        if (leftNumeric && rightNumeric) {
            return new BigInteger(left).compareTo(new BigInteger(right));
        }
        // A numbered release segment sorts after a pre-release qualifier such as rc or snapshot.
        if (leftNumeric) return 1;
        if (rightNumeric) return -1;
        return left.compareToIgnoreCase(right);
    }

    private static boolean isZero(String value) {
        return value.isEmpty() || (value.matches("\\d+") && new BigInteger(value).signum() == 0);
    }
}
