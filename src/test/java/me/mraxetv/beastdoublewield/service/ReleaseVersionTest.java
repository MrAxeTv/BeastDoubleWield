package me.mraxetv.beastdoublewield.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseVersionTest {
    @Test void comparesNumbersRatherThanLexicalDigits() {
        assertTrue(ReleaseVersion.parse("1.10.0").compareTo(ReleaseVersion.parse("1.9.99")) > 0);
        assertEquals(0, ReleaseVersion.parse("v1.2").compareTo(ReleaseVersion.parse("1.2.0+build.3")));
    }
    @Test void releaseIsNewerThanSnapshotAndPrereleaseNumericPartsOrderNumerically() {
        assertTrue(ReleaseVersion.parse("0.1.0").compareTo(ReleaseVersion.parse("0.1.0-SNAPSHOT")) > 0);
        assertTrue(ReleaseVersion.parse("1.0.0-rc.10").compareTo(ReleaseVersion.parse("1.0.0-rc.2")) > 0);
        assertTrue(ReleaseVersion.parse("1.0.0-rc.abc").compareTo(ReleaseVersion.parse("1.0.0-rc.2")) > 0);
        assertTrue(ReleaseVersion.parse("1.0.0-alpha.1").compareTo(ReleaseVersion.parse("1.0.0-alpha")) > 0);
    }
    @Test void rejectsVersionStringsThatCannotBeOrderedReliably() {
        for (String text : new String[]{"latest", "", "1.2.3 /evil", "1.2.3\n2.0.0", "1.2.3-", "1.2.3.4.5"})
            assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse(text));
    }
}
