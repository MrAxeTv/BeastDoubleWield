package me.mraxetv.beastdoublewield.service;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastlib.api.scheduler.SchedulerTask;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Optional owner-configured services. No request is made while its URL is blank. */
public final class RuntimeServices implements Listener, AutoCloseable {
    public enum CheckResult { QUEUED, DISABLED, UNCONFIGURED, BUSY, BACKOFF, CLOSED }
    private final BeastDoubleWieldPlugin plugin;
    private final AtomicLong attacks = new AtomicLong();
    private final AtomicLong breaks = new AtomicLong();
    private final AtomicReference<Object> updateFlight = new AtomicReference<>();
    private final AtomicReference<Object> metricsFlight = new AtomicReference<>();
    private final String installedVersion;
    private volatile long generation;
    private volatile boolean running;
    private ServiceSettings settings;
    private ThreadPoolExecutor worker;
    private BoundedHttps http;
    private SchedulerTask timer;
    private UpdateManifest latest;
    private String updateState = "not started";
    private String metricState = "not started";
    private long nextUpdate, nextMetrics, nextManual;
    private int updateFailures, metricFailures;

    public RuntimeServices(BeastDoubleWieldPlugin plugin) {
        this.plugin = plugin;
        installedVersion = plugin.getDescription().getVersion();
    }

    public static void validate(ConfigurationSection config) { ServiceSettings.read(config); }

    /** Invoke on the server thread, after settings have been validated and published. */
    public void start() { reload(); }
    public void reload() {
        ServiceSettings next = ServiceSettings.read(plugin.getConfig());
        BoundedHttps nextHttp = null;
        ThreadPoolExecutor nextWorker = null;
        SchedulerTask nextTimer = null;
        try {
            if ((next.updates && next.manifest != null) || (next.metrics && next.endpoint != null)) {
                nextHttp = new BoundedHttps();
                nextWorker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2), runnable -> {
                    Thread thread = new Thread(runnable, "BeastDoubleWield-network"); thread.setDaemon(true); return thread;
                });
                nextWorker.allowCoreThreadTimeOut(true);
                // Preparation is on the server thread and the task starts next second.
                // No callback can observe this instance until the swap below completes.
                nextTimer = plugin.getScheduler().runGlobalRepeating(plugin, this::poll, 20L, 1200L);
                if (nextTimer == null || nextTimer.isDone()) throw new IllegalStateException("Network service scheduler rejected replacement task");
            }
        } catch (RuntimeException failure) {
            if (nextTimer != null) nextTimer.cancel();
            if (nextHttp != null) nextHttp.close();
            if (nextWorker != null) nextWorker.shutdownNow();
            throw failure;
        }
        close();
        http = nextHttp; worker = nextWorker; timer = nextTimer;
        settings = next; running = true;
        nextUpdate = nextMetrics = nextManual = 0;
        updateFailures = metricFailures = 0; latest = null;
        updateState = !settings.updates ? "disabled" : settings.manifest == null ? "not configured" : "waiting";
        metricState = !settings.metrics ? "disabled" : settings.endpoint == null ? "not configured" : "waiting";
    }

    public void recordAttack() { attacks.incrementAndGet(); }
    public void recordBlockBreak() { breaks.incrementAndGet(); }

    /** Contains only local status; this method never initiates I/O. Server thread only. */
    public String describe() { return "updates=" + updateState + ", custom metrics=" + metricState; }
    public UpdateManifest getLatest() { return latest; }

    public CheckResult checkNow(CommandSender sender) {
        if (!running) return CheckResult.CLOSED;
        if (!settings.updates) return CheckResult.DISABLED;
        if (settings.manifest == null) return CheckResult.UNCONFIGURED;
        if (updateFlight.get() != null) return CheckResult.BUSY;
        if (System.nanoTime() < nextManual) return CheckResult.BACKOFF;
        nextManual = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
        return requestUpdate(sender) ? CheckResult.QUEUED : CheckResult.BUSY;
    }

    private void poll() {
        if (!running) return;
        long now = System.nanoTime();
        if (settings.updates && settings.manifest != null && now >= nextUpdate) requestUpdate(null);
        if (settings.metrics && settings.endpoint != null && now >= nextMetrics) requestMetrics();
    }

    private boolean requestUpdate(CommandSender requester) {
        Object flight = new Object();
        if (!updateFlight.compareAndSet(null, flight)) return false;
        long token = generation;
        BoundedHttps transport = http;
        ServiceSettings config = settings;
        updateState = "checking";
        try {
            worker.execute(() -> {
                UpdateManifest manifest = null;
                String error = null;
                try {
                    manifest = UpdateManifest.parse(transport.get(config.manifest));
                    manifest.isNewerThan(installedVersion); // Validate installed and remote versions before publication.
                } catch (Exception failure) { error = failure.getClass().getSimpleName() + ": " + failure.getMessage(); }
                final UpdateManifest result = manifest;
                final String failure = error;
                if (!complete(token, () -> {
                    if (!updateFlight.compareAndSet(flight, null)) return;
                    if (failure != null) {
                        updateFailures++;
                        updateState = "failed (retry scheduled)";
                        nextUpdate = deadline(backoff(updateFailures, config.updateMinutes));
                        plugin.getLogger().warning("Update check failed: " + failure);
                        if (requester != null) message(requester, "UpdateFailed");
                        return;
                    }
                    updateFailures = 0; nextUpdate = deadline(config.updateMinutes);
                    latest = result;
                    boolean newer = result.isNewerThan(installedVersion);
                    updateState = newer ? "available " + result.getVersion() : "current";
                    if (requester != null) {
                        if (newer) notifyUpdate(requester, result); else message(requester, "UpdateCurrent");
                    } else if (newer) {
                        plugin.getLogger().info("BeastDoubleWield " + result.getVersion() + " is available: " + result.getDownload());
                        if (config.notifyAdmins) for (Player player : plugin.getServer().getOnlinePlayers())
                            if (player.hasPermission("beastdoublewield.update")) notifyUpdate(player, result);
                    }
                })) updateFlight.compareAndSet(flight, null);
            });
            return true;
        } catch (RejectedExecutionException full) {
            updateFlight.compareAndSet(flight, null); updateState = "busy"; nextUpdate = deadline(1); return false;
        }
    }

    private void requestMetrics() {
        Object flight = new Object();
        if (!metricsFlight.compareAndSet(null, flight)) return;
        long token = generation;
        BoundedHttps transport = http;
        ServiceSettings config = settings;
        // Capture Bukkit-owned values here. The worker receives immutable bytes only.
        byte[] payload = MetricsPayload.create(installedVersion, plugin.getServer().getBukkitVersion(),
                System.getProperty("java.version", "unknown"), plugin.getServer().getOnlinePlayers().size(), attacks.get(), breaks.get());
        metricState = "sending";
        try {
            worker.execute(() -> {
                String error = null;
                try { transport.post(config.endpoint, payload); }
                catch (Exception failure) { error = failure.getClass().getSimpleName() + ": " + failure.getMessage(); }
                final String failure = error;
                if (!complete(token, () -> {
                    if (!metricsFlight.compareAndSet(flight, null)) return;
                    if (failure == null) {
                        metricFailures = 0; nextMetrics = deadline(config.metricMinutes); metricState = "sent";
                    } else {
                        metricFailures++; nextMetrics = deadline(backoff(metricFailures, config.metricMinutes));
                        metricState = "failed (retry scheduled)";
                        plugin.getLogger().warning("Metrics submission failed: " + failure);
                    }
                })) metricsFlight.compareAndSet(flight, null);
            });
        } catch (RejectedExecutionException full) {
            metricsFlight.compareAndSet(flight, null); metricState = "busy"; nextMetrics = deadline(1);
        }
    }

    private boolean complete(long token, Runnable completion) {
        if (!running || generation != token) return false;
        try {
            SchedulerTask delivery = plugin.getScheduler().runGlobal(plugin, () -> {
                if (running && generation == token && plugin.isEnabled()) completion.run();
            });
            return delivery != null && !delivery.isDone();
        } catch (RuntimeException unavailable) {
            // Shutdown cancels owned tasks and advances generation. Never deliver from the worker.
            return false;
        }
    }

    private void message(CommandSender sender, String key) {
        if (!(sender instanceof Player) || ((Player) sender).isOnline()) plugin.message(sender, key);
    }
    private void notifyUpdate(CommandSender sender, UpdateManifest release) {
        if (!(sender instanceof Player) || ((Player) sender).isOnline())
            plugin.message(sender, "UpdateAvailable", "version", release.getVersion(), "url", release.getDownload());
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (!running || settings == null || !settings.notifyAdmins || latest == null || !latest.isNewerThan(installedVersion)) return;
        long token = generation;
        Player player = event.getPlayer();
        plugin.getScheduler().runPlayerDelayed(plugin, player, () -> {
            if (running && token == generation && player.isOnline() && player.hasPermission("beastdoublewield.update") && latest != null)
                notifyUpdate(player, latest);
        }, 40L);
    }

    static long backoff(int failures, long regularMinutes) {
        // Every failure waits at least five minutes; exponential retry never exceeds a day.
        long retry = 5L << Math.min(8, Math.max(0, failures - 1));
        return Math.min(1440L, Math.max(Math.min(regularMinutes, 60L), retry));
    }
    private static long deadline(long minutes) { return System.nanoTime() + TimeUnit.MINUTES.toNanos(minutes); }

    @Override public void close() {
        running = false; generation++;
        if (timer != null) { timer.cancel(); timer = null; }
        if (http != null) { http.close(); http = null; }
        if (worker != null) { worker.shutdownNow(); worker = null; }
        updateFlight.set(null); metricsFlight.set(null);
        updateState = metricState = "stopped";
    }
}
