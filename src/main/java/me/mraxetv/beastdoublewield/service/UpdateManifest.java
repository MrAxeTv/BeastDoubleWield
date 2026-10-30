package me.mraxetv.beastdoublewield.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Tiny owner-hosted YAML format. Download URLs are informational, never fetched or installed. */
public final class UpdateManifest {
    public static final int MAX_BYTES = 8192;
    private final String version;
    private final URI download;
    private UpdateManifest(String version, URI download) { this.version = version; this.download = download; }
    public String getVersion() { return version; }
    public URI getDownload() { return download; }
    public boolean isNewerThan(String installed) { return ReleaseVersion.parse(version).compareTo(ReleaseVersion.parse(installed)) > 0; }

    public static UpdateManifest parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Update manifest is empty or too large");
        // Deliberately accept only the two scalar keys, not general YAML objects,
        // aliases or tags. This remains bounded on legacy servers' older YAML libs.
        String version = null, link = null;
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon < 1) throw new IllegalArgumentException("Invalid manifest line");
            String key = line.substring(0, colon).trim(), value = scalar(line.substring(colon + 1).trim());
            if (key.equals("version") && version == null) version = value;
            else if (key.equals("download-url") && link == null) link = value;
            else throw new IllegalArgumentException("Unknown or repeated manifest key: " + key);
        }
        ReleaseVersion.parse(version);
        URI download = ServiceSettings.endpoint(link, "download-url");
        if (download == null) throw new IllegalArgumentException("Update manifest requires download-url");
        return new UpdateManifest(version, download);
    }

    private static String scalar(String text) {
        if (text.length() > 2048) throw new IllegalArgumentException("Manifest value too long");
        if (text.startsWith("\"") || text.startsWith("'")) {
            char quote = text.charAt(0);
            if (text.length() < 2 || text.charAt(text.length() - 1) != quote)
                throw new IllegalArgumentException("Unterminated manifest value");
            text = text.substring(1, text.length() - 1);
        }
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\\') >= 0)
            throw new IllegalArgumentException("Escapes are not supported in manifest values");
        return text;
    }
}
