package me.mraxetv.beastdoublewield.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tick-based offhand admission limit, in addition to the native shared cooldown. */
public final class AttackLimiter {
    private final Map<UUID, Long> ready = new HashMap<>();
    public boolean isReady(UUID id, long tick) { return !ready.containsKey(id) || tick >= ready.get(id); }
    public void record(UUID id, long tick, double speed) {
        double safe = Double.isFinite(speed) && speed > 0 ? speed : 4.0;
        ready.put(id, tick + Math.max(1L, Math.min(1200L, (long) Math.ceil(20.0 / safe))));
    }
    public void forget(UUID id) { ready.remove(id); }
    public void clear() { ready.clear(); }
}
