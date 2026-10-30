package me.mraxetv.beastdoublewield.service;

import me.mraxetv.beastdoublewield.lib.bstats.Metrics;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/** Owns one unmodified bStats client for the plugin's enabled lifetime. */
public final class BStatsTelemetry implements AutoCloseable {
    public static final int PLUGIN_ID = 35021;

    private final Plugin plugin;
    private Metrics metrics;
    private boolean started;
    private boolean closed;
    private String state = "not started";

    public BStatsTelemetry(Plugin plugin) { this.plugin = plugin; }

    /** bStats itself owns opt-out, collection, and its submission schedule. */
    public void start() {
        if (started || closed) return;
        started = true;
        try {
            metrics = new Metrics(plugin, PLUGIN_ID);
            state = "initialized";
        } catch (RuntimeException failure) {
            state = "unavailable";
            plugin.getLogger().log(Level.WARNING, "Could not initialize bStats metrics", failure);
        }
    }

    /** Initialization is not proof that metrics are enabled or have reached bStats. */
    public String describe() {
        return "bStats=" + state + " (ID " + PLUGIN_ID + "; opt-out: plugins/bStats/config.yml)";
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
        state = "stopped";
    }
}
