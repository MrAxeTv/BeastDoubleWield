package me.mraxetv.beastdoublewield.service;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastlib.api.scheduler.SchedulerService;
import me.mraxetv.beastlib.api.scheduler.SchedulerTask;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeServicesTest {
    @Test void unconfiguredAndDisabledServicesNeverScheduleNetworkWork() {
        BeastDoubleWieldPlugin plugin = mock(BeastDoubleWieldPlugin.class);
        SchedulerService scheduler = mock(SchedulerService.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("BeastDoubleWield", "0.2.0", "unused.Main"));
        when(plugin.getConfig()).thenReturn(config); when(plugin.getScheduler()).thenReturn(scheduler);
        RuntimeServices services = new RuntimeServices(plugin); services.start();
        assertEquals(RuntimeServices.CheckResult.UNCONFIGURED, services.checkNow(null));
        assertEquals("updates=not configured, custom metrics=disabled", services.describe());
        config.set("Updates.Enabled", false); services.reload();
        assertEquals(RuntimeServices.CheckResult.DISABLED, services.checkNow(null));
        services.close(); assertEquals(RuntimeServices.CheckResult.CLOSED, services.checkNow(null));
        verifyNoInteractions(scheduler);
    }
    @Test void invalidReloadLeavesOldServiceRunning() {
        BeastDoubleWieldPlugin plugin = mock(BeastDoubleWieldPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("BeastDoubleWield", "0.2.0", "unused.Main"));
        when(plugin.getConfig()).thenReturn(config);
        RuntimeServices services = new RuntimeServices(plugin); services.start();
        config.set("Updates.ManifestUrl", "http://invalid.example/feed");
        assertThrows(IllegalArgumentException.class, services::reload);
        assertEquals(RuntimeServices.CheckResult.UNCONFIGURED, services.checkNow(null)); services.close();
    }
    @Test void schedulingFailureDoesNotStopOrReplaceOldService() {
        BeastDoubleWieldPlugin plugin = mock(BeastDoubleWieldPlugin.class);
        SchedulerService scheduler = mock(SchedulerService.class);
        SchedulerTask oldTask = mock(SchedulerTask.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("Updates.ManifestUrl", "https://updates.example.org/old");
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("BeastDoubleWield", "0.2.0", "unused.Main"));
        when(plugin.getConfig()).thenReturn(config); when(plugin.getScheduler()).thenReturn(scheduler);
        when(scheduler.runGlobalRepeating(eq(plugin), any(Runnable.class), eq(20L), eq(1200L)))
                .thenReturn(oldTask).thenThrow(new IllegalStateException("scheduler unavailable"));
        RuntimeServices services = new RuntimeServices(plugin); services.start();
        config.set("Updates.ManifestUrl", "https://updates.example.org/new");
        assertThrows(IllegalStateException.class, services::reload);
        assertEquals("updates=waiting, custom metrics=disabled", services.describe());
        verify(oldTask, never()).cancel();
        services.close(); verify(oldTask).cancel();
    }
    @Test void silentlyRejectedReplacementTaskIsCancelledAndOldStateRetained() {
        BeastDoubleWieldPlugin plugin = mock(BeastDoubleWieldPlugin.class);
        SchedulerService scheduler = mock(SchedulerService.class);
        SchedulerTask rejected = mock(SchedulerTask.class); when(rejected.isDone()).thenReturn(true);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("BeastDoubleWield", "0.2.0", "unused.Main"));
        when(plugin.getConfig()).thenReturn(config); when(plugin.getScheduler()).thenReturn(scheduler);
        RuntimeServices services = new RuntimeServices(plugin); services.start();
        config.set("Updates.ManifestUrl", "https://updates.example.org/new");
        when(scheduler.runGlobalRepeating(eq(plugin), any(Runnable.class), eq(20L), eq(1200L))).thenReturn(rejected);
        assertThrows(IllegalStateException.class, services::reload);
        assertEquals(RuntimeServices.CheckResult.UNCONFIGURED, services.checkNow(null));
        verify(rejected).cancel(); services.close();
    }
}
