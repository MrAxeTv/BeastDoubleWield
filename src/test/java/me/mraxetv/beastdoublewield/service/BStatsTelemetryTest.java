package me.mraxetv.beastdoublewield.service;

import me.mraxetv.beastdoublewield.lib.bstats.Metrics;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BStatsTelemetryTest {
    @TempDir Path directory;

    @Test void initializesOnlyOnceWithRegisteredIdAndShutsDownOnlyOnce() {
        Plugin plugin = mock(Plugin.class);
        try (MockedConstruction<Metrics> constructed = mockConstruction(Metrics.class, (metrics, context) -> {
            assertSame(plugin, context.arguments().get(0));
            assertEquals(35021, context.arguments().get(1));
        })) {
            BStatsTelemetry telemetry = new BStatsTelemetry(plugin);
            telemetry.start();
            telemetry.start();
            assertEquals(1, constructed.constructed().size());
            assertTrue(telemetry.describe().contains("bStats=initialized"));
            assertTrue(telemetry.describe().contains("plugins/bStats/config.yml"));
            telemetry.close();
            telemetry.close();
            telemetry.start();
            verify(constructed.constructed().get(0), times(1)).shutdown();
            assertEquals(1, constructed.constructed().size());
            assertTrue(telemetry.describe().contains("bStats=stopped"));
        }
    }

    @Test void shutdownBeforeInitializationNeverCreatesClient() {
        try (MockedConstruction<Metrics> constructed = mockConstruction(Metrics.class)) {
            BStatsTelemetry telemetry = new BStatsTelemetry(mock(Plugin.class));
            telemetry.close();
            telemetry.start();
            assertTrue(constructed.constructed().isEmpty());
        }
    }

    @Test void optionalMetricsFailureDoesNotPreventGameplayStartupOrRepeatedlyRetry() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        try (MockedConstruction<Metrics> constructed = mockConstruction(Metrics.class, (metrics, context) -> {
            throw new IllegalStateException("fixture initialization failure");
        })) {
            BStatsTelemetry telemetry = new BStatsTelemetry(plugin);
            assertDoesNotThrow(telemetry::start);
            assertTrue(telemetry.describe().contains("bStats=unavailable"));
            assertDoesNotThrow(telemetry::start);
            assertDoesNotThrow(telemetry::close);
            verify(plugin, times(1)).getLogger();
        }
    }

    @Test void suppliedClientHonorsExistingGlobalOptOutAndShutsDownScheduler() throws Exception {
        Path plugins = directory.resolve("plugins");
        Path config = plugins.resolve("bStats/config.yml");
        Files.createDirectories(config.getParent());
        byte[] original = ("enabled: false\nserverUuid: fixture-existing-server\n"
                + "logFailedRequests: false\n").getBytes(StandardCharsets.UTF_8);
        Files.write(config, original);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(plugins.resolve("BeastDoubleWield").toFile());
        Metrics metrics = new Metrics(plugin, BStatsTelemetry.PLUGIN_ID);
        ScheduledThreadPoolExecutor scheduler;
        try {
            Field baseField = Metrics.class.getDeclaredField("metricsBase");
            baseField.setAccessible(true);
            Object base = baseField.get(metrics);
            Field enabledField = Metrics.MetricsBase.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            assertEquals(false, enabledField.get(base));
            Field schedulerField = Metrics.MetricsBase.class.getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            scheduler = (ScheduledThreadPoolExecutor) schedulerField.get(base);
            assertTrue(scheduler.getQueue().isEmpty(), "Global opt-out must schedule no submissions");
            assertEquals(0, scheduler.getPoolSize(), "No submission worker should start while opted out");
            assertArrayEquals(original, Files.readAllBytes(config), "Existing global settings must be preserved");
            assertFalse(YamlConfiguration.loadConfiguration(config.toFile()).getBoolean("enabled"));
        } finally {
            metrics.shutdown();
        }
        assertTrue(scheduler.isShutdown());
    }
}
