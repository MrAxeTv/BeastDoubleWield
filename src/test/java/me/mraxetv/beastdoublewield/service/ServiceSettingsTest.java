package me.mraxetv.beastdoublewield.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiceSettingsTest {
    @Test void defaultSettingsNeverMakeANetworkRequest() {
        ServiceSettings settings = ServiceSettings.read(new YamlConfiguration());
        assertNull(settings.manifest); assertNull(settings.endpoint); assertFalse(settings.metrics);
    }
    @Test void disallowsInsecureCredentialedAndMalformedEndpoints() {
        for (String uri : new String[]{"http://example.org/feed", "file:///tmp/feed", "https://user:pass@example.org/feed",
                "https://example.org/feed#fragment", "https:///relative", "https://bad host/"})
            assertThrows(IllegalArgumentException.class, () -> ServiceSettings.endpoint(uri, "endpoint"));
    }
    @Test void validatesBooleansAndIntervalsBeforePublication() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("metrics.enabled", "true"); assertThrows(IllegalArgumentException.class, () -> ServiceSettings.read(yaml));
        yaml.set("metrics.enabled", true); yaml.set("metrics.interval-minutes", 0);
        assertThrows(IllegalArgumentException.class, () -> ServiceSettings.read(yaml));
        yaml.set("metrics.interval-minutes", 5.5);
        assertThrows(IllegalArgumentException.class, () -> ServiceSettings.read(yaml));
        yaml.set("metrics.interval-minutes", 5);
        yaml.set("metrics.endpoint", "https://metrics.example.org/v1");
        assertEquals("metrics.example.org", ServiceSettings.read(yaml).endpoint.getHost());
    }
}
