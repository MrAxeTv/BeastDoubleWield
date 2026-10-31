package me.mraxetv.beastdoublewield.service;

import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.net.URISyntaxException;

/** Immutable network configuration, validated before any active service is replaced. */
public final class ServiceSettings {
    final boolean updates;
    final boolean notifyAdmins;
    final URI manifest;
    final long updateMinutes;
    final boolean metrics;
    final URI endpoint;
    final long metricMinutes;

    private ServiceSettings(boolean updates, boolean notifyAdmins, URI manifest, long updateMinutes,
                            boolean metrics, URI endpoint, long metricMinutes) {
        this.updates = updates; this.notifyAdmins = notifyAdmins; this.manifest = manifest;
        this.updateMinutes = updateMinutes; this.metrics = metrics; this.endpoint = endpoint;
        this.metricMinutes = metricMinutes;
    }

    public static ServiceSettings read(ConfigurationSection config) {
        boolean updates = bool(config, "Updates.Enabled", true);
        boolean notify = bool(config, "Updates.NotifyAdmins", true);
        boolean metrics = bool(config, "Metrics.Enabled", false);
        URI manifest = endpoint(config.getString("Updates.ManifestUrl", ""), "Updates.ManifestUrl");
        URI endpoint = endpoint(config.getString("Metrics.Endpoint", ""), "Metrics.Endpoint");
        long updateMinutes = minutes(config, "Updates.IntervalMinutes", 360, 5, 10080);
        long metricMinutes = minutes(config, "Metrics.IntervalMinutes", 30, 5, 1440);
        return new ServiceSettings(updates, notify, manifest, updateMinutes, metrics, endpoint, metricMinutes);
    }

    static URI endpoint(String text, String setting) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            URI uri = new URI(text.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null)
                throw new IllegalArgumentException(setting + " must be an HTTPS URL without credentials or a fragment");
            return uri;
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException(setting + " is not a valid URL", invalid);
        }
    }

    private static boolean bool(ConfigurationSection config, String path, boolean fallback) {
        if (!config.contains(path)) return fallback;
        Object value = config.get(path);
        if (!(value instanceof Boolean)) throw new IllegalArgumentException(path + " must be true or false");
        return (Boolean) value;
    }
    private static long minutes(ConfigurationSection config, String path, long fallback, long min, long max) {
        if (!config.contains(path)) return fallback;
        Object value = config.get(path);
        if (!(value instanceof Number)) throw new IllegalArgumentException(path + " must be a whole number");
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number < min || number > max || number != Math.floor(number))
            throw new IllegalArgumentException(path + " must be a whole number from " + min + " to " + max);
        return (long) number;
    }
}
