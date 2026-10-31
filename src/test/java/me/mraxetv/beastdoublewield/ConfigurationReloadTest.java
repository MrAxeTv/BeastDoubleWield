package me.mraxetv.beastdoublewield;

import me.mraxetv.beastdoublewield.service.BStatsTelemetry;
import me.mraxetv.beastdoublewield.service.RuntimeServices;
import me.mraxetv.beastdoublewield.config.LanguageCatalog;
import me.mraxetv.beastlib.lib.boostedyaml.YamlDocument;
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
        when(plugin.getResource(anyString())).thenAnswer(call ->
                getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
        doCallRealMethod().when(plugin).getConfig();
        doCallRealMethod().when(plugin).getMessages();
        doCallRealMethod().when(plugin).stateName(anyBoolean());
        doCallRealMethod().when(plugin).reloadSettings();
        Field field = BeastDoubleWieldPlugin.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(plugin, current);
        return plugin;
    }

    @Test void malformedYamlNeverReplacesActiveConfiguration() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.set("Enabled", true);
        BeastDoubleWieldPlugin plugin = pluginWithSettings(current);
        Files.write(directory.resolve("config.yml"), "attack: [unterminated\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidConfigurationException.class, plugin::reloadSettings);
        assertSame(current, plugin.getConfig());
        assertTrue(plugin.getConfig().getBoolean("Enabled"));
        verify(plugin, never()).getYamlFiles();
    }

    @Test void invalidGameplayConfigurationNeverReplacesActiveConfiguration() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.set("Enabled", true);
        BeastDoubleWieldPlugin plugin = pluginWithSettings(current);
        byte[] original = "enabled: false\ndisabled-worlds: [lobby]\nattack:\n  reach: 999\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("config.yml"), original);
        assertThrows(IllegalArgumentException.class, plugin::reloadSettings);
        assertSame(current, plugin.getConfig());
        assertTrue(plugin.getConfig().getBoolean("Enabled"));
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml")));
        assertFalse(Files.exists(directory.resolve("config.yml.pre-PascalCase.bak")));
        assertFalse(Files.exists(directory.resolve("Lang/English.yml")));
        assertFalse(Files.exists(directory.resolve("Lang/en.yml")));
        verify(plugin, never()).getYamlFiles();
    }

    @Test void legacyValuesAndMessagesMigrateBeforeDefaultsAndRuntimePublication() throws Exception {
        BeastDoubleWieldPlugin plugin = pluginWithSettings(new YamlConfiguration());
        byte[] old = ("enabled: true\ndisabled-worlds: [lobby]\ninteraction:\n  preserve-main-hand-use: false\n"
                + "updates:\n  manifest-url: ''\n  notify-admins: false\nattack:\n  game-modes: [SURVIVAL]\n").getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("config.yml"), old);
        Files.createDirectories(directory.resolve("Lang"));
        Files.write(directory.resolve("Lang/English.yml"),
                "Prefix: 'Owner prefix'\nHelp: |-\n  first line\n  second line\n".getBytes(StandardCharsets.UTF_8));
        plugin.reloadSettings();
        assertFalse(plugin.getConfig().getBoolean("Interaction.PreserveMainHandUse"));
        assertEquals("", plugin.getConfig().getString("Updates.ManifestUrl"));
        assertEquals(java.util.Collections.singletonList("lobby"), plugin.getConfig().getStringList("DisabledWorlds"));
        Field field = BeastDoubleWieldPlugin.class.getDeclaredField("language");
        field.setAccessible(true);
        YamlDocument language = (YamlDocument) field.get(plugin);
        assertEquals("Owner prefix", language.getString("Prefix"));
        assertEquals("first line\nsecond line", language.getString("Help"));
        assertArrayEquals(old, Files.readAllBytes(directory.resolve("config.yml.pre-PascalCase.bak")));
        verify(plugin, never()).getYamlFiles();
    }

    @Test void malformedLanguageDoesNotMigrateEitherFileOrActiveSettings() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        BeastDoubleWieldPlugin plugin = pluginWithSettings(current);
        byte[] config = "enabled: true\ndisabled-worlds: []\n".getBytes(StandardCharsets.UTF_8);
        byte[] language = "Prefix: [unterminated\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("config.yml"), config);
        Files.createDirectories(directory.resolve("Lang"));
        Files.write(directory.resolve("Lang/English.yml"), language);
        InvalidConfigurationException malformed = assertThrows(InvalidConfigurationException.class, plugin::reloadSettings);
        assertTrue(malformed.getMessage().contains("Lang/English.yml"));
        assertNotNull(malformed.getCause());
        assertSame(current, plugin.getConfig());
        assertArrayEquals(config, Files.readAllBytes(directory.resolve("config.yml")));
        assertArrayEquals(language, Files.readAllBytes(directory.resolve("Lang/English.yml")));
        assertFalse(Files.exists(directory.resolve("config.yml.pre-PascalCase.bak")));
    }

    @Test void runtimePreparationFailureRestoresOriginalFilesAndActiveSettings() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        BeastDoubleWieldPlugin plugin = pluginWithSettings(current);
        byte[] original = "enabled: true\ndisabled-worlds: [old-world]\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("config.yml"), original);
        RuntimeServices runtime = mock(RuntimeServices.class);
        doThrow(new IllegalStateException("scheduler rejected replacement")).when(runtime).reload();
        Field field = BeastDoubleWieldPlugin.class.getDeclaredField("runtimeServices");
        field.setAccessible(true);
        field.set(plugin, runtime);
        assertThrows(IllegalStateException.class, plugin::reloadSettings);
        assertSame(current, plugin.getConfig());
        assertNull(plugin.getMessages());
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml")));
        assertFalse(Files.exists(directory.resolve("Lang/English.yml")));
        for (String code : LanguageCatalog.codes())
            assertFalse(Files.exists(directory.resolve(LanguageCatalog.resource(code))));
    }

    @Test void languageSelectionReloadsMessagesAndStateNamesButInvalidReplacementKeepsBothActive() throws Exception {
        BeastDoubleWieldPlugin plugin = pluginWithSettings(new YamlConfiguration());
        Files.createDirectories(directory.resolve("Lang"));
        Files.write(directory.resolve("config.yml"), "Language: en\n".getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("Lang/en.yml"), "Prefix: 'English owner'\nStateOn: 'enabled owner'\n".getBytes(StandardCharsets.UTF_8));
        plugin.reloadSettings();
        assertEquals("English owner", plugin.getMessages().getString("Prefix"));
        assertEquals("enabled owner", plugin.stateName(true));
        Files.write(directory.resolve("config.yml"), "Language: HR\n".getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("Lang/hr.yml"), "Prefix: 'Croatian owner'\nStateOn: 'uključeno'\nStateOff: 'isključeno'\n".getBytes(StandardCharsets.UTF_8));
        plugin.reloadSettings();
        assertEquals("hr", plugin.getConfig().getString("Language"));
        assertEquals("Croatian owner", plugin.getMessages().getString("Prefix"));
        assertEquals("uključeno", plugin.stateName(true));
        assertEquals("isključeno", plugin.stateName(false));
        org.bukkit.configuration.file.FileConfiguration active = plugin.getConfig();
        YamlDocument activeMessages = plugin.getMessages();
        byte[] invalidConfig = "Language: zh_TW\n".getBytes(StandardCharsets.UTF_8);
        byte[] invalidLanguage = "Status: [not, text]\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("config.yml"), invalidConfig);
        Files.write(directory.resolve("Lang/zh-TW.yml"), invalidLanguage);
        assertThrows(IllegalArgumentException.class, plugin::reloadSettings);
        assertSame(active, plugin.getConfig());
        assertSame(activeMessages, plugin.getMessages());
        assertArrayEquals(invalidConfig, Files.readAllBytes(directory.resolve("config.yml")));
        assertArrayEquals(invalidLanguage, Files.readAllBytes(directory.resolve("Lang/zh-TW.yml")));
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
