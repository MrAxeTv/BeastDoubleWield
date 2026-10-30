package me.mraxetv.beastdoublewield.service;

import java.nio.charset.StandardCharsets;

/** An aggregate snapshot. Contains no player names, UUIDs, server ID, address or host name. */
public final class MetricsPayload {
    private MetricsPayload() { }
    public static byte[] create(String pluginVersion, String serverVersion, String javaVersion,
                                int players, long attacks, long breaks) {
        String json = "{\"schema\":1,\"plugin\":\"BeastDoubleWield\",\"pluginVersion\":" + quote(pluginVersion)
                + ",\"serverVersion\":" + quote(serverVersion) + ",\"javaMajor\":" + quote(javaMajor(javaVersion))
                + ",\"players\":" + Math.max(0, players) + ",\"offhandAttackAttempts\":" + Math.max(0, attacks)
                + ",\"offhandBlocksBroken\":" + Math.max(0, breaks) + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
    static String javaMajor(String text) {
        if (text == null) return "unknown";
        String value = text.startsWith("1.") ? text.substring(2) : text;
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
        return end == 0 ? "unknown" : value.substring(0, Math.min(end, 3));
    }
    static String quote(String text) {
        if (text == null) text = "unknown";
        if (text.length() > 128) text = text.substring(0, 128);
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32 || Character.isSurrogate(c)) result.append(String.format("\\u%04x", (int) c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
}
