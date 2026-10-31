package me.mraxetv.beastdoublewield.command;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.service.BStatsTelemetry;
import me.mraxetv.beastdoublewield.service.RuntimeServices;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DoubleWieldCommandTest {
    private BeastDoubleWieldPlugin plugin;
    private DoubleWieldCommand command;
    private Command bukkitCommand;

    @BeforeEach void setup() {
        plugin = mock(BeastDoubleWieldPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DoubleWieldCommandTest"));
        command = new DoubleWieldCommand(plugin);
        bukkitCommand = mock(Command.class);
    }

    @Test void deniedToggleCannotMutatePersistentPreference() {
        Player player = mock(Player.class);
        assertTrue(command.onCommand(player, bukkitCommand, "bdw", new String[]{"toggle", "off"}));
        verify(plugin, never()).setEnabled(any(Player.class), anyBoolean());
        verify(plugin).message(player, "NoPermission");
        assertTrue(command.onTabComplete(player, bukkitCommand, "bdw", new String[]{"toggle", ""}).isEmpty());
    }

    @Test void toggleUsesPersonalPreferenceEvenWhenServerGloballyDisabled() {
        Player player = mock(Player.class);
        when(player.hasPermission("beastdoublewield.toggle")).thenReturn(true);
        when(plugin.isPersonallyEnabled(player)).thenReturn(true);
        when(plugin.isEnabled(player)).thenReturn(false);
        command.onCommand(player, bukkitCommand, "bdw", new String[]{"toggle"});
        verify(plugin).setEnabled(player, false);
        verify(plugin).message(player, "Disabled");
    }

    @Test void explicitStateIsIdempotentAndInvalidStateDoesNotWrite() {
        Player player = mock(Player.class);
        when(player.hasPermission("beastdoublewield.toggle")).thenReturn(true);
        command.onCommand(player, bukkitCommand, "bdw", new String[]{"toggle", "on"});
        verify(plugin).setEnabled(player, true);
        clearInvocations(plugin);
        command.onCommand(player, bukkitCommand, "bdw", new String[]{"toggle", "maybe"});
        verify(plugin, never()).setEnabled(any(Player.class), anyBoolean());
    }

    @Test void consoleCannotToggleAPlayerPreference() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("beastdoublewield.toggle")).thenReturn(true);
        command.onCommand(sender, bukkitCommand, "bdw", new String[]{"toggle"});
        verify(plugin, never()).setEnabled(any(Player.class), anyBoolean());
        verify(plugin).message(sender, "PlayerOnly");
    }

    @Test void reloadRequiresItsOwnPermissionAndFailedReloadReportsFailure() throws Exception {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("beastdoublewield.use")).thenReturn(true);
        command.onCommand(sender, bukkitCommand, "bdw", new String[]{"reload"});
        verify(plugin, never()).reloadSettings();
        when(sender.hasPermission("beastdoublewield.reload")).thenReturn(true);
        doThrow(new IOException("Malformed configuration")).when(plugin).reloadSettings();
        command.onCommand(sender, bukkitCommand, "bdw", new String[]{"reload"});
        verify(plugin).reloadSettings();
        verify(plugin).message(sender, "ReloadFailed");
        verify(plugin, never()).message(sender, "Reloaded");
    }

    @Test void administratorMayReloadWithoutGeneralUsePermission() throws Exception {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("beastdoublewield.admin")).thenReturn(true);
        command.onCommand(sender, bukkitCommand, "bdw", new String[]{"reload"});
        verify(plugin).reloadSettings();
        verify(plugin).message(sender, "Reloaded");
        assertTrue(command.onTabComplete(sender, bukkitCommand, "bdw", new String[]{"re"}).contains("reload"));
    }

    @Test void statusSeparatesCustomTelemetryFromBStats() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("beastdoublewield.use")).thenReturn(true);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("BeastDoubleWield", "0.1.0", "unused.Main"));
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        RuntimeServices custom = mock(RuntimeServices.class);
        BStatsTelemetry bstats = mock(BStatsTelemetry.class);
        when(plugin.getRuntimeServices()).thenReturn(custom);
        when(plugin.getBStatsTelemetry()).thenReturn(bstats);
        when(custom.describe()).thenReturn("updates=not configured, custom metrics=disabled");
        when(bstats.describe()).thenReturn("bStats=initialized (ID 35021; opt-out: plugins/bStats/config.yml)");
        command.onCommand(sender, bukkitCommand, "bdw", new String[]{"status"});
        verify(plugin).message(sender, "ServicesStatus", "state", "updates=not configured, custom metrics=disabled");
        verify(plugin).message(sender, "ServicesStatus", "state", "bStats=initialized (ID 35021; opt-out: plugins/bStats/config.yml)");
    }

    @Test void statusUsesTranslatedGlobalAndPersonalStateLabels() {
        Player player = mock(Player.class);
        when(player.hasPermission("beastdoublewield.use")).thenReturn(true);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("BeastDoubleWield", "1.1-SNAPSHOT", "unused.Main"));
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.isPersonallyEnabled(player)).thenReturn(false);
        when(plugin.stateName(true)).thenReturn("uključeno");
        when(plugin.stateName(false)).thenReturn("isključeno");
        command.onCommand(player, bukkitCommand, "bdw", new String[]{"status"});
        verify(plugin).message(player, "Status", "version", "1.1-SNAPSHOT", "global", "uključeno");
        verify(plugin).message(player, "PersonalStatus", "state", "isključeno");
    }
}
