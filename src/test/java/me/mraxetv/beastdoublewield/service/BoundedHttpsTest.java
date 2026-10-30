package me.mraxetv.beastdoublewield.service;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class BoundedHttpsTest {
    @Test void responseMayEqualButCannotExceedMaximum() throws Exception {
        assertEquals(8192, BoundedHttps.read(new ByteArrayInputStream(new byte[8192]), 8192).length);
        assertThrows(IOException.class, () -> BoundedHttps.read(new ByteArrayInputStream(new byte[8193]), 8192));
    }
    @Test void exponentialRetryHasFloorAndCap() {
        assertEquals(60, RuntimeServices.backoff(1, 360));
        assertEquals(5, RuntimeServices.backoff(1, 5));
        assertEquals(1280, RuntimeServices.backoff(200, 360));
    }
}
