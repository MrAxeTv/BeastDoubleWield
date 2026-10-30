package me.mraxetv.beastdoublewield;

import me.mraxetv.beastdoublewield.service.BStatsTelemetry;
import me.mraxetv.beastlib.api.scheduler.SchedulerServices;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationReloadTest {
    @TempDir Path directory;

    private BeastDoubleWieldPlugin pluginWithSettings(YamlConfiguration current) throws Exception {
        BeastDoubleWieldPlugin plugin = mock(BeastDoubleWieldPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        doCallRealMethod().when(plugin).getConfig();
        doCallRealMethod().when(plugin).reloadSettings();
        Field field = BeastDoubleWieldPlugin.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(plugin, current);
        return plugin;
    }

    @Test void malformedYamlNeverReplacesActiveConfiguration() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.set("enabled", true);
        BeastDoubleWieldPlugin plugin = pluginWithSettings(current);
        Files.write(directory.resolve("config.yml"), "attack: [unterminated\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidConfigurationException.class, plugin::reloadSettings);
        assertSame(current, plugin.getConfig());
        assertTrue(plugin.getConfig().getBoolean("enabled"));
        verify(plugin, never()).getYamlFiles();
    }

    @Test void invalidGameplayConfigurationNeverReplacesActiveConfiguration() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.set("enabled", true);
        BeastDoubleWieldPlugin plugin = pluginWithSettings(current);
        Files.write(directory.resolve("config.yml"), "enabled: false\nattack:\n  reach: 999\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, plugin::reloadSettings);
        assertSame(current, plugin.getConfig());
        assertTrue(plugin.getConfig().getBoolean("enabled"));
        verify(plugin, never()).getYamlFiles();
    }

    @Test void successfulSettingsReloadDoesNotRestartBStatsOrResetItsSchedule() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        BeastDoubleWieldPlugin plugin = pluginWithSettings(current);
        BStatsTelemetry telemetry = mock(BStatsTelemetry.class);
        Field field = BeastDoubleWieldPlugin.class.getDeclaredField("bStatsTelemetry");
        field.setAccessible(true);
        field.set(plugin, telemetry);
        YamlConfiguration candidate;
        try (InputStreamReader resource = new InputStreamReader(
                getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            candidate = YamlConfiguration.loadConfiguration(resource);
        }
        candidate.save(directory.resolve("config.yml").toFile());
        plugin.reloadSettings();
        assertNotSame(current, plugin.getConfig());
        assertSame(telemetry, field.get(plugin));
        verifyNoInteractions(telemetry);
    }

    @Test void pluginDisableClosesOwnedBStatsClient() throws Exception {
        BeastDoubleWieldPlugin plugin = mock(BeastDoubleWieldPlugin.class);
        BStatsTelemetry telemetry = mock(BStatsTelemetry.class);
        Field field = BeastDoubleWieldPlugin.class.getDeclaredField("bStatsTelemetry");
        field.setAccessible(true);
        field.set(plugin, telemetry);
        doCallRealMethod().when(plugin).onDisable();
        try (org.mockito.MockedStatic<SchedulerServices> services = mockStatic(SchedulerServices.class)) {
            services.when(SchedulerServices::isAvailable).thenReturn(false);
            plugin.onDisable();
        }
        verify(telemetry).close();
    }
}
