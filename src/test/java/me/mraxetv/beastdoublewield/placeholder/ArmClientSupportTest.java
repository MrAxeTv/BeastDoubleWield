package me.mraxetv.beastdoublewield.placeholder;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.integration.IntegrationSupport;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArmClientSupportTest {
    @Test void translationMustReportExactProtocolAndBedrockNeverGetsArm() {
        BeastDoubleWieldPlugin owner = mock(BeastDoubleWieldPlugin.class);
        Server server = mock(Server.class); PluginManager manager = mock(PluginManager.class);
        Player player = mock(Player.class); Plugin via = mock(Plugin.class);
        IntegrationSupport integrations = mock(IntegrationSupport.class);
        when(owner.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(manager);
        when(owner.getIntegrations()).thenReturn(integrations);
        AtomicReference<Integer> version = new AtomicReference<>();
        ArmClientSupport clients = new ArmClientSupport(owner, (plugin, id) -> version.get());
        assertTrue(clients.allowed(player));
        when(manager.getPlugin("ViaVersion")).thenReturn(via); when(via.isEnabled()).thenReturn(true);
        assertFalse(clients.allowed(player));
        version.set(776); assertFalse(clients.allowed(player));
        version.set(777); assertTrue(clients.allowed(player));
        when(integrations.isBedrock(player)).thenReturn(true); assertFalse(clients.allowed(player));
    }

    @Test void unavailableViaApiFailsClosed() {
        BeastDoubleWieldPlugin owner = mock(BeastDoubleWieldPlugin.class);
        Server server = mock(Server.class); PluginManager manager = mock(PluginManager.class);
        Plugin via = mock(Plugin.class); Player player = mock(Player.class);
        when(owner.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("ViaVersion")).thenReturn(via); when(via.isEnabled()).thenReturn(true);
        assertFalse(new ArmClientSupport(owner).allowed(player));
    }
}
