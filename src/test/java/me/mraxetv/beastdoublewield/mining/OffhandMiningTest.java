package me.mraxetv.beastdoublewield.mining;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.api.OffhandBlockBreakEvent;
import me.mraxetv.beastdoublewield.util.HandAccess;
import me.mraxetv.beastdoublewield.util.ItemRules;
import me.mraxetv.beastlib.api.scheduler.SchedulerService;
import me.mraxetv.beastlib.api.scheduler.SchedulerTask;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OffhandMiningTest {
    private BeastDoubleWieldPlugin plugin;
    private Player player;
    private Block block;
    private BlockData data;
    private PlayerInventory inventory;
    private ItemStack tool;
    private PluginManager events;
    private YamlConfiguration config;
    private OffhandMining mining;
    private final List<Runnable> ticks = new ArrayList<>();
    private final List<SchedulerTask> tasks = new ArrayList<>();
    private MockedStatic<ItemRules> itemRules;
    private MockedStatic<HandAccess> handAccess;

    @BeforeEach
    void setup() {
        plugin = mock(BeastDoubleWieldPlugin.class);
        player = mock(Player.class);
        block = mock(Block.class);
        data = mock(BlockData.class);
        inventory = mock(PlayerInventory.class);
        tool = mock(ItemStack.class);
        World world = mock(World.class);
        Server server = mock(Server.class);
        events = mock(PluginManager.class);
        SchedulerService scheduler = mock(SchedulerService.class);
        config = new YamlConfiguration();
        config.set("mine.enabled", true);
        config.set("mine.sounds", false);
        config.set("mine.input-timeout-ticks", 8);
        config.set("disabled-worlds", Collections.emptyList());
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getScheduler()).thenReturn(scheduler);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OffhandMiningTest"));
        when(plugin.isEnabled(player)).thenReturn(true);
        when(server.getPluginManager()).thenReturn(events);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("beastdoublewield.mine")).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWorld()).thenReturn(world);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInOffHand()).thenReturn(tool);
        when(tool.getType()).thenReturn(Material.DIAMOND_PICKAXE);
        when(tool.getAmount()).thenReturn(1);
        when(tool.clone()).thenReturn(tool);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(block.getWorld()).thenReturn(world);
        when(block.getType()).thenReturn(Material.STONE);
        when(block.getBlockData()).thenReturn(data);
        when(block.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(data.getAsString()).thenReturn("minecraft:stone");
        when(block.getBreakSpeed(player)).thenReturn(0.5F);
        when(player.rayTraceBlocks(anyDouble(), eq(FluidCollisionMode.NEVER)))
                .thenReturn(new RayTraceResult(new Vector(), block, BlockFace.NORTH));
        when(scheduler.runEntityRepeating(eq(plugin), eq(player), any(Runnable.class), eq(1L), eq(1L), any(Runnable.class)))
                .thenAnswer(call -> {
                    SchedulerTask task = mock(SchedulerTask.class);
                    tasks.add(task);
                    ticks.add(call.getArgument(2));
                    return task;
                });
        itemRules = mockStatic(ItemRules.class);
        itemRules.when(() -> ItemRules.canUse(eq(player), eq(tool), any())).thenReturn(true);
        handAccess = mockStatic(HandAccess.class);
        handAccess.when(() -> HandAccess.withOffhand(eq(player), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        mining = new OffhandMining(plugin);
    }

    @AfterEach
    void cleanup() {
        mining.close();
        handAccess.close();
        itemRules.close();
    }

    @Test
    void accumulatesNativeSpeedAndBreaksExactlyOnce() {
        assertTrue(mining.begin(player, block));
        ticks.get(0).run();
        verify(player, never()).breakBlock(any());
        verify(player).sendBlockDamage(block.getLocation(), 0.5F);
        ticks.get(0).run();
        ticks.get(0).run();
        verify(events).callEvent(isA(OffhandBlockBreakEvent.class));
        verify(player).breakBlock(block);
        verify(tasks.get(0)).cancel();
        verify(block, never()).breakNaturally();
        verify(block, never()).breakNaturally(any(ItemStack.class));
    }

    @Test
    void cancelledCustomEventPreventsNativeBreak() {
        doAnswer(call -> { ((OffhandBlockBreakEvent) call.getArgument(0)).setCancelled(true); return null; })
                .when(events).callEvent(isA(OffhandBlockBreakEvent.class));
        assertTrue(mining.begin(player, block));
        ticks.get(0).run();
        ticks.get(0).run();
        verify(player, never()).breakBlock(any());
        verify(tasks.get(0)).cancel();
    }

    @Test
    void refreshedInputKeepsProgressWithoutStartingAnotherTask() {
        config.set("mine.input-timeout-ticks", 2);
        when(block.getBreakSpeed(player)).thenReturn(0.15F);
        for (int i = 0; i < 7; i++) {
            assertTrue(mining.begin(player, block));
            ticks.get(0).run();
        }
        assertEquals(1, tasks.size());
        verify(events).callEvent(isA(BlockDamageEvent.class));
        verify(player).breakBlock(block);
    }

    @Test void cancelledMiningStartDoesNotScheduleOrBreak() {
        doAnswer(call -> { ((BlockDamageEvent) call.getArgument(0)).setCancelled(true); return null; })
                .when(events).callEvent(isA(BlockDamageEvent.class));
        assertTrue(mining.begin(player, block));
        assertTrue(tasks.isEmpty());
        verify(player, never()).breakBlock(any());
        verify(events, never()).callEvent(isA(OffhandBlockBreakEvent.class));
    }

    @Test void skillInstantBreakUsesOneNativeCompletion() {
        doAnswer(call -> { ((BlockDamageEvent) call.getArgument(0)).setInstaBreak(true); return null; })
                .when(events).callEvent(isA(BlockDamageEvent.class));
        assertTrue(mining.begin(player, block));
        ticks.get(0).run(); ticks.get(0).run();
        verify(player).breakBlock(block);
        verify(block, never()).getBreakSpeed(player);
        verify(events).callEvent(isA(OffhandBlockBreakEvent.class));
    }

    @Test void changedToolDuringMiningStartCannotInheritSkillResult() {
        doAnswer(call -> { when(inventory.getItemInOffHand()).thenReturn(mock(ItemStack.class)); return null; })
                .when(events).callEvent(isA(BlockDamageEvent.class));
        assertTrue(mining.begin(player, block));
        assertTrue(tasks.isEmpty());
        verify(player, never()).breakBlock(any());
    }

    @Test
    void missingInputExpiresAndClearsCracks() {
        config.set("mine.input-timeout-ticks", 2);
        when(block.getBreakSpeed(player)).thenReturn(0.01F);
        assertTrue(mining.begin(player, block));
        for (int i = 0; i < 10; i++) ticks.get(0).run();
        verify(player, never()).breakBlock(any());
        verify(player).sendBlockDamage(block.getLocation(), 0.0F);
        verify(tasks.get(0)).cancel();
    }

    @Test
    void lookingAwayStopsMining() {
        assertTrue(mining.begin(player, block));
        when(player.rayTraceBlocks(anyDouble(), any())).thenReturn(null);
        ticks.get(0).run();
        verify(player, never()).breakBlock(any());
        verify(tasks.get(0)).cancel();
    }

    @Test
    void replacedBlockCannotInheritExistingProgress() {
        assertTrue(mining.begin(player, block));
        ticks.get(0).run();
        when(data.getAsString()).thenReturn("minecraft:diamond_block");
        ticks.get(0).run();
        verify(player, never()).breakBlock(any());
        verify(tasks.get(0)).cancel();
    }

    @Test
    void eventListenersCannotReplaceToolAndStillBreak() {
        doAnswer(call -> { when(inventory.getItemInOffHand()).thenReturn(mock(ItemStack.class)); return null; })
                .when(events).callEvent(isA(OffhandBlockBreakEvent.class));
        assertTrue(mining.begin(player, block));
        ticks.get(0).run();
        ticks.get(0).run();
        verify(player, never()).breakBlock(any());
    }

    @Test
    void closeDuringCompletionEventInvalidatesInFlightBreak() {
        doAnswer(call -> { mining.close(); return null; })
                .when(events).callEvent(isA(OffhandBlockBreakEvent.class));
        assertTrue(mining.begin(player, block));
        ticks.get(0).run();
        ticks.get(0).run();
        verify(player, never()).breakBlock(any());
    }

    @Test
    void closeCanBeFollowedByNewSessionAfterReload() {
        assertTrue(mining.begin(player, block));
        mining.close();
        assertTrue(mining.begin(player, block));
        ticks.get(0).run();
        ticks.get(1).run();
        ticks.get(1).run();
        assertEquals(2, tasks.size());
        verify(player).breakBlock(block);
    }

    @Test
    void wrongToolAndDisabledWorldRejectBeforeScheduling() {
        config.set("mine.correct-tool-only", true);
        assertFalse(mining.begin(player, block));
        config.set("mine.correct-tool-only", false);
        config.set("disabled-worlds", Collections.singletonList("world"));
        assertFalse(mining.begin(player, block));
        assertTrue(tasks.isEmpty());
    }

    @Test
    void zeroHardnessUsesInstantNativeBreak() {
        when(block.getBreakSpeed(player)).thenReturn(Float.POSITIVE_INFINITY);
        assertTrue(mining.begin(player, block));
        ticks.get(0).run();
        verify(player).breakBlock(block);
    }

    @Test
    void unbreakableBlockStopsWithoutCallingBreak() {
        when(block.getBreakSpeed(player)).thenReturn(0.0F);
        assertTrue(mining.begin(player, block));
        ticks.get(0).run();
        verify(player, never()).breakBlock(any());
        verify(tasks.get(0)).cancel();
    }
}
