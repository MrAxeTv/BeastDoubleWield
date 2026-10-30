package me.mraxetv.beastdoublewield.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MetricsPayloadTest {
    @Test void aggregatePayloadContainsNoPersistentOrPlayerIdentity() {
        String json = new String(MetricsPayload.create("0.2.0", "1.20.2", "1.8.0_412", 4, 12, 6), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"javaMajor\":\"8\""));
        assertTrue(json.contains("\"players\":4")); assertTrue(json.contains("\"offhandBlocksBroken\":6"));
        assertFalse(json.contains("uuid")); assertFalse(json.contains("hostname")); assertFalse(json.contains("address"));
        assertFalse(json.contains("412"));
    }
    @Test void quotesUntrustedVersionTextAndClampsNegativeCounters() {
        String json = new String(MetricsPayload.create("evil\"\n\\", "1.20.2", "26.0.1", -1, -2, -3), StandardCharsets.UTF_8);
        assertTrue(json.contains("evil\\\"\\u000a\\\\"));
        assertTrue(json.contains("\"players\":0")); assertTrue(json.contains("\"offhandAttackAttempts\":0"));
        assertTrue(json.contains("\"javaMajor\":\"26\""));
    }
}
