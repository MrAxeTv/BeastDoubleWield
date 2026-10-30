package me.mraxetv.beastdoublewield.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class UpdateManifestTest {
    @Test void readsOnlyAReleaseAndAnInformationalHttpsDownloadLink() {
        UpdateManifest release = parse("# release\nversion: '1.2.0'\ndownload-url: 'https://example.org/download'\n");
        assertEquals("1.2.0", release.getVersion());
        assertTrue(release.isNewerThan("1.1.0")); assertFalse(release.isNewerThan("2.0.0"));
    }
    @Test void rejectsYamlTagsAliasesDuplicateKeysAndMissingFields() {
        for (String text : new String[]{"version: !!java.net.URL whatever", "version: 1.0.0", "version: 1.0.0\nversion: 2.0.0",
                "version: 1.0.0\ndownload-url: http://example.org/", "version: &v 1.0.0\ndownload-url: *v",
                "version: '1.0.0\ndownload-url: https://example.org/", "version: 1.0.0\ncommands: [stop]"})
            assertThrows(IllegalArgumentException.class, () -> parse(text));
    }
    @Test void sizeLimitAppliesBeforeParsing() {
        assertThrows(IllegalArgumentException.class, () -> UpdateManifest.parse(new byte[8193]));
    }
    private UpdateManifest parse(String text) { return UpdateManifest.parse(text.getBytes(StandardCharsets.UTF_8)); }
}
