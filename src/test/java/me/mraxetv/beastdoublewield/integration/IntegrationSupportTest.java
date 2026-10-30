package me.mraxetv.beastdoublewield.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationSupportTest {
    @Test void detectsBedrockOnlyWithPositiveApiEvidence() {
        PluginManager plugins = mock(PluginManager.class); Plugin floodgate = mock(Plugin.class);
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(floodgate.isEnabled()).thenReturn(true); when(plugins.getPlugin("floodgate")).thenReturn(floodgate);
        assertTrue(new IntegrationSupport(plugins, (owner, id) -> true).isBedrock(player));
        assertFalse(new IntegrationSupport(plugins, (owner, id) -> false).isBedrock(player));
        assertFalse(new IntegrationSupport(plugins, (owner, id) -> null).isBedrock(player));
    }
    @Test void providerDisablingDuringQueryCannotSupplyIdentity() {
        PluginManager plugins = mock(PluginManager.class); Plugin geyser = mock(Plugin.class);
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(geyser.isEnabled()).thenReturn(true); when(plugins.getPlugin("Geyser-Spigot")).thenReturn(geyser);
        assertFalse(new IntegrationSupport(plugins, (owner, id) -> { when(owner.isEnabled()).thenReturn(false); return true; }).isBedrock(player));
    }
    @Test void absentApiDoesNotGuessFromNames() {
        PluginManager plugins = mock(PluginManager.class); Player player = mock(Player.class);
        when(player.getName()).thenReturn(".BedrockPlayer");
        IntegrationSupport support = new IntegrationSupport(plugins, (owner, id) -> { fail("No provider to query"); return false; });
        assertFalse(support.isBedrock(player));
        assertTrue(support.status().get("mcMMO").contains("not installed"));
        assertThrows(UnsupportedOperationException.class, () -> support.status().put("test", "value"));
    }
}
