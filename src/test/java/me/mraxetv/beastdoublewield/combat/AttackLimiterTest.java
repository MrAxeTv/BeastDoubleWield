package me.mraxetv.beastdoublewield.combat;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class AttackLimiterTest {
    @Test void axeCannotBorrowBareHandSpeed() {
        AttackLimiter limiter = new AttackLimiter(); UUID player = UUID.randomUUID();
        assertTrue(limiter.isReady(player, 20)); limiter.record(player, 20, 0.8);
        assertFalse(limiter.isReady(player, 44)); assertTrue(limiter.isReady(player, 45));
    }
    @Test void stateIsSeparateAndRemovedOnDisconnect() {
        AttackLimiter limiter = new AttackLimiter(); UUID player = UUID.randomUUID();
        limiter.record(player, 20, 1); assertTrue(limiter.isReady(UUID.randomUUID(), 21));
        limiter.forget(player); assertTrue(limiter.isReady(player, 21));
    }
    @Test void invalidValuesCannotCreateInfiniteOrNegativeCooldown() {
        AttackLimiter limiter = new AttackLimiter(); UUID player = UUID.randomUUID();
        limiter.record(player, 10, Double.NaN); assertFalse(limiter.isReady(player, 10)); assertTrue(limiter.isReady(player, 15));
        limiter.record(player, 20, Double.MIN_VALUE); assertTrue(limiter.isReady(player, 1220));
    }
}
