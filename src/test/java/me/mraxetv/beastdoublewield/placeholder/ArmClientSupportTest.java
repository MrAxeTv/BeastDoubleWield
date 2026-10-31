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
    @Test void unknownClientProtocolDoesNotAssumeNativeCompatibility() {
        BeastDoubleWieldPlugin owner = mock(BeastDoubleWieldPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        Player player = mock(Player.class);
        when(owner.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        boolean allowed = new ArmClientSupport(owner).allowed(player);
        assertFalse(allowed, "Unknown client protocol must not receive the arm renderer");
    }

    @Test void translationMustReportExactProtocolAndBedrockNeverGetsArm() {
        BeastDoubleWieldPlugin owner = mock(BeastDoubleWieldPlugin.class);
        Server server = mock(Server.class); PluginManager manager = mock(PluginManager.class);
        Player player = mock(Player.class); Plugin via = mock(Plugin.class);
        IntegrationSupport integrations = mock(IntegrationSupport.class);
        when(owner.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(manager);
        when(owner.getIntegrations()).thenReturn(integrations);
        AtomicReference<Integer> version = new AtomicReference<>();
        ArmClientSupport clients = new ArmClientSupport(owner, (plugin, id) -> version.get(), client -> 777);
        assertTrue(clients.allowed(player));
        when(manager.getPlugin("ViaVersion")).thenReturn(via); when(via.isEnabled()).thenReturn(true);
        assertFalse(clients.allowed(player));
        version.set(776); assertFalse(clients.allowed(player));
        version.set(777); assertTrue(clients.allowed(player));
        when(integrations.isBedrock(player)).thenReturn(true); assertFalse(clients.allowed(player));
    }

    @Test void directConnectionsRequireExactPositivePaperProtocol() {
        BeastDoubleWieldPlugin owner = mock(BeastDoubleWieldPlugin.class);
        Server server = mock(Server.class); PluginManager manager = mock(PluginManager.class);
        Player player = mock(Player.class);
        when(owner.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(manager);
        AtomicReference<Integer> protocol = new AtomicReference<>();
        ArmClientSupport clients = new ArmClientSupport(owner, (plugin, id) -> 777, client -> protocol.get());
        for (Integer version : new Integer[]{null, -1, 0, 47, 776, 778, 800}) {
            protocol.set(version);
            assertFalse(clients.allowed(player), "Unverified protocol: " + version);
        }
        protocol.set(777);
        assertTrue(clients.allowed(player));
    }

    @Test void disabledTranslatorCannotFallBackToBackendProtocol() {
        BeastDoubleWieldPlugin owner = mock(BeastDoubleWieldPlugin.class);
        Server server = mock(Server.class); PluginManager manager = mock(PluginManager.class);
        Plugin via = mock(Plugin.class); Player player = mock(Player.class);
        when(owner.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("ViaVersion")).thenReturn(via);
        ArmClientSupport clients = new ArmClientSupport(owner, (plugin, id) -> 777, client -> 777);
        assertFalse(clients.allowed(player));
    }

    @Test void translatedClientResultTakesPriorityOverTheBackendHandshake() {
        BeastDoubleWieldPlugin owner = mock(BeastDoubleWieldPlugin.class);
        Server server = mock(Server.class); PluginManager manager = mock(PluginManager.class);
        Plugin via = mock(Plugin.class); Player player = mock(Player.class);
        when(owner.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("ViaVersion")).thenReturn(via); when(via.isEnabled()).thenReturn(true);
        AtomicReference<Integer> protocol = new AtomicReference<>(776);
        ArmClientSupport clients = new ArmClientSupport(owner, (plugin, id) -> protocol.get(), client -> 777);
        assertFalse(clients.allowed(player));
        protocol.set(778); assertFalse(clients.allowed(player));
        protocol.set(null); assertFalse(clients.allowed(player));
        protocol.set(777); assertTrue(clients.allowed(player));
    }

    @Test void queryFailuresRemainIneligible() {
        BeastDoubleWieldPlugin owner = mock(BeastDoubleWieldPlugin.class);
        Server server = mock(Server.class); PluginManager manager = mock(PluginManager.class);
        Plugin via = mock(Plugin.class); Player player = mock(Player.class);
        when(owner.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(manager);
        ArmClientSupport clients = new ArmClientSupport(owner,
                (plugin, id) -> { throw new LinkageError("unavailable translator"); },
                client -> { throw new IllegalStateException("disconnected"); });
        assertFalse(clients.allowed(player));
        when(manager.getPlugin("ViaVersion")).thenReturn(via); when(via.isEnabled()).thenReturn(true);
        assertFalse(clients.allowed(player));
    }

    @Test void translatorMustRemainEnabledAndRegisteredThroughoutQuery() {
        BeastDoubleWieldPlugin owner = mock(BeastDoubleWieldPlugin.class);
        Server server = mock(Server.class); PluginManager manager = mock(PluginManager.class);
        Plugin via = mock(Plugin.class); Player player = mock(Player.class);
        when(owner.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("ViaVersion")).thenReturn(via); when(via.isEnabled()).thenReturn(true);
        ArmClientSupport disabling = new ArmClientSupport(owner, (plugin, id) -> {
            when(via.isEnabled()).thenReturn(false); return 777;
        }, client -> 777);
        assertFalse(disabling.allowed(player));
        when(via.isEnabled()).thenReturn(true);
        ArmClientSupport removed = new ArmClientSupport(owner, (plugin, id) -> {
            when(manager.getPlugin("ViaVersion")).thenReturn(null); return 777;
        }, client -> 777);
        assertFalse(removed.allowed(player));
    }

    @Test void knownBedrockConnectionIsRejectedEvenWithNativeProtocolMatch() {
        BeastDoubleWieldPlugin owner = mock(BeastDoubleWieldPlugin.class);
        Server server = mock(Server.class); PluginManager manager = mock(PluginManager.class);
        Player player = mock(Player.class); IntegrationSupport integrations = mock(IntegrationSupport.class);
        when(owner.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(manager);
        when(owner.getIntegrations()).thenReturn(integrations);
        when(integrations.isBedrock(player)).thenReturn(true);
        assertFalse(new ArmClientSupport(owner, (plugin, id) -> 777, client -> 777).allowed(player));
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
