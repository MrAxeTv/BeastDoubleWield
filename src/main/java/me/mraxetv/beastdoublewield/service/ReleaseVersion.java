package me.mraxetv.beastdoublewield.service;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Numeric release comparison with SemVer-style prerelease ordering and ignored build metadata. */
public final class ReleaseVersion implements Comparable<ReleaseVersion> {
    private static final Pattern FORMAT = Pattern.compile("^[vV]?([0-9]+(?:\\.[0-9]+){0,3})(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z.-]+)?$");
    private final BigInteger[] numbers;
    private final String[] pre;
    private ReleaseVersion(BigInteger[] numbers, String[] pre) { this.numbers = numbers; this.pre = pre; }

    public static ReleaseVersion parse(String text) {
        if (text == null || text.length() > 128) throw new IllegalArgumentException("Invalid release version");
        Matcher matcher = FORMAT.matcher(text.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid release version: " + text);
        String[] parts = matcher.group(1).split("\\.");
        BigInteger[] numbers = new BigInteger[parts.length];
        for (int i = 0; i < parts.length; i++) numbers[i] = new BigInteger(parts[i]);
        return new ReleaseVersion(numbers, matcher.group(2) == null ? new String[0] : matcher.group(2).split("\\."));
    }

    @Override public int compareTo(ReleaseVersion other) {
        for (int i = 0; i < Math.max(numbers.length, other.numbers.length); i++) {
            BigInteger left = i < numbers.length ? numbers[i] : BigInteger.ZERO;
            BigInteger right = i < other.numbers.length ? other.numbers[i] : BigInteger.ZERO;
            int order = left.compareTo(right);
            if (order != 0) return order;
        }
        if (pre.length == 0 || other.pre.length == 0)
            return pre.length == other.pre.length ? 0 : pre.length == 0 ? 1 : -1;
        for (int i = 0; i < Math.min(pre.length, other.pre.length); i++) {
            String left = pre[i], right = other.pre[i];
            boolean ln = left.matches("[0-9]+"), rn = right.matches("[0-9]+");
            int order = ln && rn ? new BigInteger(left).compareTo(new BigInteger(right))
                    : ln != rn ? ln ? -1 : 1 : left.compareTo(right);
            if (order != 0) return order;
        }
        return Integer.compare(pre.length, other.pre.length);
    }
}
