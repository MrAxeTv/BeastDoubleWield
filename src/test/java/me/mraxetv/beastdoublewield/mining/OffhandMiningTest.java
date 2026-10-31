package me.mraxetv.beastdoublewield.mining;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.api.OffhandBlockBreakEvent;
import me.mraxetv.beastdoublewield.combat.OffhandCombat;
import me.mraxetv.beastdoublewield.interaction.OffhandInput;
import me.mraxetv.beastdoublewield.util.HandAccess;
import me.mraxetv.beastdoublewield.util.ItemRules;
import me.mraxetv.beastlib.api.scheduler.SchedulerService;
import me.mraxetv.beastlib.api.scheduler.SchedulerTask;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.SoundGroup;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
import java.util.concurrent.atomic.AtomicReference;
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
    private SoundGroup sounds;
    private PlayerInventory inventory;
    private ItemStack tool;
    private PluginManager events;
    private YamlConfiguration config;
    private OffhandMining mining;
    private int currentTick;
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
        sounds = mock(SoundGroup.class);
        inventory = mock(PlayerInventory.class);
        tool = mock(ItemStack.class);
        World world = mock(World.class);
        Server server = mock(Server.class);
        events = mock(PluginManager.class);
        SchedulerService scheduler = mock(SchedulerService.class);
        config = new YamlConfiguration();
        config.set("Mine.Enabled", true);
        config.set("Mine.Sounds", false);
        config.set("Mine.InputTimeoutTicks", 8);
        config.set("DisabledWorlds", Collections.emptyList());
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getScheduler()).thenReturn(scheduler);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OffhandMiningTest"));
        when(plugin.isEnabled(player)).thenReturn(true);
        when(server.getPluginManager()).thenReturn(events);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getTicksLived()).thenAnswer(call -> currentTick);
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
        when(block.getLocation()).thenAnswer(call -> new Location(world, 0, 0, 0));
        when(data.getAsString()).thenReturn("minecraft:stone");
        when(data.getSoundGroup()).thenReturn(sounds);
        when(sounds.getBreakSound()).thenReturn(Sound.BLOCK_STONE_BREAK);
        when(sounds.getHitSound()).thenReturn(Sound.BLOCK_STONE_HIT);
        when(sounds.getVolume()).thenReturn(1.0F);
        when(sounds.getPitch()).thenReturn(1.0F);
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

    private void runTick(int taskIndex) {
        currentTick++;
        ticks.get(taskIndex).run();
    }

    private Block targetNextBlock() {
        Block next = mock(Block.class);
        World world = block.getWorld();
        when(next.getWorld()).thenReturn(world);
        when(next.getX()).thenReturn(1);
        when(next.getType()).thenReturn(Material.STONE);
        when(next.getBlockData()).thenReturn(data);
        when(next.getLocation()).thenAnswer(call -> new Location(world, 1, 0, 0));
        when(next.getBreakSpeed(player)).thenReturn(0.5F);
        when(player.rayTraceBlocks(anyDouble(), eq(FluidCollisionMode.NEVER)))
                .thenReturn(new RayTraceResult(new Vector(1, 0, 0), next, BlockFace.NORTH));
        return next;
    }

    @Test
    void mainHandAxeCanMineDirtPathWithRealEmptyOffhandWhenMiningAllowsIt() {
        // Real ItemStack equality delegates null metadata comparison to Bukkit.
        ItemFactory factory = mock(ItemFactory.class);
        when(factory.equals(isNull(), isNull())).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
            verifyEmptyOffhandMining(new ItemStack(Material.AIR));
        }
    }

    @Test
    void mainHandAxeCanMineDirtPathWithSkinArmAsActualAirDuringNativeActions() {
        ItemStack arm = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer tags = mock(PersistentDataContainer.class);
        NamespacedKey armKey = new NamespacedKey("beastdoublewield", "first_person_arm");
        when(arm.getType()).thenReturn(Material.FEATHER);
        when(arm.getAmount()).thenReturn(1);
        when(arm.hasItemMeta()).thenReturn(true);
        when(arm.getItemMeta()).thenReturn(meta);
        when(arm.clone()).thenReturn(arm);
        when(meta.getPersistentDataContainer()).thenReturn(tags);
        when(tags.has(armKey, PersistentDataType.BYTE)).thenReturn(true);
        when(tags.get(armKey, PersistentDataType.BYTE)).thenReturn((byte) 1);
        verifyEmptyOffhandMining(arm);
    }

    private void verifyEmptyOffhandMining(ItemStack originalOffhand) {
        // Use the actual input filters and hand transaction across the full mining
        // session. Only Bukkit's native speed/break boundary remains simulated.
        itemRules.close();
        itemRules = mockStatic(ItemRules.class, CALLS_REAL_METHODS);
        handAccess.close();
        handAccess = mockStatic(HandAccess.class, CALLS_REAL_METHODS);
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        AtomicReference<ItemStack> main = new AtomicReference<>(axe);
        AtomicReference<ItemStack> offhand = new AtomicReference<>(originalOffhand);
        when(inventory.getHeldItemSlot()).thenReturn(2);
        when(inventory.getItem(2)).thenAnswer(call -> main.get());
        when(inventory.getItemInMainHand()).thenAnswer(call -> main.get());
        when(inventory.getItemInOffHand()).thenAnswer(call -> offhand.get());
        doAnswer(call -> { main.set(call.getArgument(1)); return null; }).when(inventory).setItem(eq(2), any());
        doAnswer(call -> { offhand.set(call.getArgument(0)); return null; }).when(inventory).setItemInOffHand(any());
        when(block.getType()).thenReturn(Material.DIRT_PATH);
        when(data.getAsString()).thenReturn("minecraft:dirt_path");
        config.set("Attack.AllowEmptyHand", true);
        config.set("Mine.GameModes", Collections.singletonList("SURVIVAL"));
        config.set("Mine.CorrectToolOnly", false);
        config.set("Mine.AllowEmptyHand", false);
        config.set("Mine.Materials.Mode", "WHITELIST");
        config.set("Mine.Materials.Patterns", Collections.singletonList("AIR"));
        OffhandCombat combat = mock(OffhandCombat.class);
        OffhandInput input = new OffhandInput(plugin, combat, mining);
        PlayerInteractEvent denied = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                axe, block, BlockFace.UP, EquipmentSlot.HAND);
        input.interact(denied);
        assertFalse(denied.isCancelled());
        assertTrue(tasks.isEmpty(), "Attack empty-hand allowance cannot enable mining");

        doAnswer(call -> {
            BlockDamageEvent event = call.getArgument(0);
            assertEquals(Material.AIR, event.getItemInHand().getType());
            assertEquals(Material.AIR, main.get().getType());
            assertSame(axe, offhand.get());
            return null;
        }).when(events).callEvent(isA(BlockDamageEvent.class));
        doAnswer(call -> {
            OffhandBlockBreakEvent event = call.getArgument(0);
            assertEquals(Material.AIR, event.getItem().getType());
            return null;
        }).when(events).callEvent(isA(OffhandBlockBreakEvent.class));
        when(block.getBreakSpeed(player)).thenAnswer(call -> {
            assertEquals(Material.AIR, main.get().getType());
            assertSame(axe, offhand.get());
            return 0.5F;
        });
        when(player.breakBlock(block)).thenAnswer(call -> {
            assertEquals(Material.AIR, main.get().getType());
            assertSame(axe, offhand.get());
            return true;
        });
        config.set("Mine.AllowEmptyHand", true);
        PlayerInteractEvent allowed = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                axe, block, BlockFace.UP, EquipmentSlot.HAND);
        input.interact(allowed);
        input.interact(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                originalOffhand, block, BlockFace.UP, EquipmentSlot.OFF_HAND));
        assertTrue(allowed.isCancelled());
        assertEquals(1, tasks.size());
        assertSame(axe, main.get());
        assertSame(originalOffhand, offhand.get());
        runTick(0);
        assertSame(axe, main.get());
        assertSame(originalOffhand, offhand.get());
        runTick(0);
        verify(player).breakBlock(block);
        verify(events).callEvent(isA(BlockDamageEvent.class));
        verify(events).callEvent(isA(OffhandBlockBreakEvent.class));
        verify(block, times(2)).getBreakSpeed(player);
        verifyNoInteractions(combat);
        assertSame(axe, main.get());
        assertEquals(originalOffhand, offhand.get());
    }

    @Test
    void successfulBreakReturnsCompletionSoundToMiningPlayer() {
        when(sounds.getVolume()).thenReturn(2.0F);
        when(sounds.getPitch()).thenReturn(1.25F);
        BlockData air = mock(BlockData.class);
        when(player.breakBlock(block)).thenAnswer(call -> {
            assertEquals(0L, soundCalls(player), "Completion must wait for native success");
            when(block.getType()).thenReturn(Material.AIR);
            when(block.getBlockData()).thenReturn(air);
            return true;
        });
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player).breakBlock(block);
        ticks.get(0).run();
        assertFalse(config.getBoolean("Mine.Sounds"), "The existing toggle only controls hit sounds");
        verify(player).playSound(new Location(block.getWorld(), 0.5, 0.5, 0.5),
                Sound.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.5F, 1.0F);
        assertEquals(1L, soundCalls(player), "The mining player needs exactly one completion sound");
        assertEquals(0L, soundCalls(block.getWorld()), "Nearby players already receive the native break effect");
        verify(air, never()).getSoundGroup();
    }

    private long soundCalls(Object recipient) {
        return mockingDetails(recipient).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("playSound")).count();
    }

    @Test
    void cancelledCompletionDoesNotPlayASuccessSound() {
        doAnswer(call -> { ((OffhandBlockBreakEvent) call.getArgument(0)).setCancelled(true); return null; })
                .when(events).callEvent(isA(OffhandBlockBreakEvent.class));
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player, never()).breakBlock(any());
        assertEquals(0L, soundCalls(player));
        assertEquals(0L, soundCalls(block.getWorld()));
    }

    @Test
    void failedNativeBreakDoesNotPlayASuccessSound() {
        when(player.breakBlock(block)).thenReturn(false);
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player).breakBlock(block);
        assertEquals(0L, soundCalls(player));
        assertEquals(0L, soundCalls(block.getWorld()));
        verify(plugin, never()).recordBlockBreak();
    }

    @Test
    void enabledHitSoundsDoNotReplaceOrDuplicateCompletionSound() {
        config.set("Mine.Sounds", true);
        when(player.breakBlock(block)).thenReturn(true);
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player).playSound(block.getLocation(), Sound.BLOCK_STONE_HIT, 0.25F, 1.0F);
        verify(player).playSound(new Location(block.getWorld(), 0.5, 0.5, 0.5),
                Sound.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.0F, 0.8F);
        assertEquals(2L, soundCalls(player));
        assertEquals(0L, soundCalls(block.getWorld()));
    }

    @Test
    void soundSnapshotFailureDoesNotPreventNativeBreakOrSuccessBookkeeping() {
        when(data.getSoundGroup()).thenThrow(new IllegalStateException("sound lookup failed"));
        when(player.breakBlock(block)).thenReturn(true);
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player).breakBlock(block);
        verify(plugin).recordBlockBreak();
        assertEquals(0L, soundCalls(player));
        assertTrue(mining.begin(player, targetNextBlock()));
        assertEquals(1, tasks.size(), "Feedback failure must not bypass the completed break's delay");
    }

    @Test
    void soundPlaybackFailureDoesNotLoseSuccessBookkeepingOrRetryFeedback() {
        doThrow(new IllegalStateException("sound playback failed")).when(player).playSound(any(Location.class),
                eq(Sound.BLOCK_STONE_BREAK), eq(SoundCategory.BLOCKS), anyFloat(), anyFloat());
        when(player.breakBlock(block)).thenReturn(true);
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        ticks.get(0).run();
        verify(player).breakBlock(block);
        verify(plugin).recordBlockBreak();
        assertEquals(1L, soundCalls(player));
        assertTrue(mining.begin(player, targetNextBlock()));
        assertEquals(1, tasks.size());
    }

    @Test
    void teleportDuringNativeBreakDoesNotPlayOldWorldSoundInTheNewWorld() {
        World elsewhere = mock(World.class);
        when(player.breakBlock(block)).thenAnswer(call -> {
            when(player.getWorld()).thenReturn(elsewhere);
            return true;
        });
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player).breakBlock(block);
        verify(plugin).recordBlockBreak();
        assertEquals(0L, soundCalls(player));
        assertEquals(0L, soundCalls(block.getWorld()));
    }

    @Test
    void disconnectDuringNativeBreakDoesNotAttemptCompletionSound() {
        when(player.breakBlock(block)).thenAnswer(call -> {
            when(player.isOnline()).thenReturn(false);
            return true;
        });
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player).breakBlock(block);
        verify(plugin).recordBlockBreak();
        assertEquals(0L, soundCalls(player));
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
    void progressiveBreakDoesNotImmediatelyStartMiningTheNextBlock() {
        when(player.breakBlock(block)).thenReturn(true);
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player).breakBlock(block);

        Block next = targetNextBlock();
        for (currentTick = 2; currentTick <= 7; currentTick++) {
            assertTrue(mining.begin(player, next));
            assertEquals(1, tasks.size(), "Held mining must retain the native post-break delay");
        }
        verify(next, never()).getBreakSpeed(player);
        assertEquals(8, currentTick);
        assertTrue(mining.begin(player, next));
        assertEquals(2, tasks.size());
        runTick(1);
        verify(player, never()).breakBlock(next);
        runTick(1);
        verify(player).breakBlock(next);
    }

    @Test
    void oneInputDoesNotKeepMiningForEightSilentTicksByDefault() {
        config.set("Mine.InputTimeoutTicks", null);
        when(block.getBreakSpeed(player)).thenReturn(0.125F);
        assertTrue(mining.begin(player, block));
        for (int i = 0; i < 8; i++) runTick(0);
        verify(player, never()).breakBlock(any());
        verify(block, times(4)).getBreakSpeed(player);
        verify(player, times(1)).swingOffHand();
        verify(tasks.get(0), never()).cancel();
        runTick(0);
        verify(tasks.get(0)).cancel();
    }

    @Test
    void inputEveryFourTicksMinesContinuouslyWithoutDuplicatePacketSpeedup() {
        when(block.getBreakSpeed(player)).thenReturn(0.125F);
        assertTrue(mining.begin(player, block));
        for (int i = 0; i < 8; i++) {
            if (i % 4 == 0) {
                assertTrue(mining.begin(player, block));
                assertTrue(mining.begin(player, block));
            }
            if (i == 7) verify(player, never()).breakBlock(any());
            runTick(0);
        }
        assertEquals(1, tasks.size());
        verify(events).callEvent(isA(BlockDamageEvent.class));
        verify(block, times(8)).getBreakSpeed(player);
        verify(player).breakBlock(block);
    }

    @Test
    void lateInputResumesRetainedProgressWithoutCatchingUpSilentTicks() {
        when(block.getBreakSpeed(player)).thenReturn(0.125F);
        assertTrue(mining.begin(player, block));
        for (int i = 0; i < 6; i++) runTick(0);
        verify(block, times(4)).getBreakSpeed(player);
        verify(player).sendBlockDamage(block.getLocation(), 0.5F);
        verify(tasks.get(0), never()).cancel();
        assertTrue(mining.begin(player, block));
        runTick(0);
        verify(player).sendBlockDamage(block.getLocation(), 0.625F);
        verify(player, never()).breakBlock(any());
        runTick(0);
        runTick(0);
        verify(player, never()).breakBlock(any());
        runTick(0);
        verify(player).breakBlock(block);
        assertEquals(1, tasks.size());
        verify(events).callEvent(isA(BlockDamageEvent.class));
    }

    @Test
    void configuredProgressWindowCanPauseBeforeTheRetainedSessionExpires() {
        config.set("Mine.InputProgressTicks", 2);
        when(block.getBreakSpeed(player)).thenReturn(0.125F);
        assertTrue(mining.begin(player, block));
        for (int i = 0; i < 8; i++) runTick(0);
        verify(block, times(2)).getBreakSpeed(player);
        verify(player).sendBlockDamage(block.getLocation(), 0.25F);
        verify(player, never()).breakBlock(any());
        verify(tasks.get(0), never()).cancel();
        runTick(0);
        verify(tasks.get(0)).cancel();
    }

    @Test
    void shorterInputTimeoutLimitsTheProgressWindow() {
        config.set("Mine.InputTimeoutTicks", 2);
        config.set("Mine.InputProgressTicks", 40);
        when(block.getBreakSpeed(player)).thenReturn(0.125F);
        assertTrue(mining.begin(player, block));
        for (int i = 0; i < 5; i++) runTick(0);
        verify(block, times(2)).getBreakSpeed(player);
        verify(player, never()).breakBlock(any());
        verify(tasks.get(0)).cancel();
    }

    @Test
    void trueInstantSurvivalBreakDoesNotAddTheProgressiveDelay() {
        when(block.getBreakSpeed(player)).thenReturn(1.0F);
        when(player.breakBlock(block)).thenReturn(true);
        assertTrue(mining.begin(player, block));
        runTick(0);
        verify(player).breakBlock(block);
        assertTrue(mining.begin(player, targetNextBlock()));
        assertEquals(2, tasks.size());
    }

    @Test
    void skillInstantSurvivalBreakDoesNotAddTheProgressiveDelay() {
        doAnswer(call -> { ((BlockDamageEvent) call.getArgument(0)).setInstaBreak(true); return null; })
                .when(events).callEvent(isA(BlockDamageEvent.class));
        when(player.breakBlock(block)).thenReturn(true);
        assertTrue(mining.begin(player, block));
        runTick(0);
        verify(player).breakBlock(block);
        assertTrue(mining.begin(player, targetNextBlock()));
        assertEquals(2, tasks.size());
    }

    @Test
    void creativeBreakKeepsTheNativeFiveTickDelay() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.breakBlock(block)).thenReturn(true);
        assertTrue(mining.begin(player, block));
        runTick(0);
        Block next = targetNextBlock();
        for (currentTick = 1; currentTick <= 6; currentTick++) {
            assertTrue(mining.begin(player, next));
            assertEquals(1, tasks.size());
        }
        assertTrue(mining.begin(player, next));
        assertEquals(2, tasks.size());
    }

    @Test
    void speedingUpAnExistingProgressiveBreakStillKeepsTheDelay() {
        when(block.getBreakSpeed(player)).thenReturn(0.25F, 1.0F);
        when(player.breakBlock(block)).thenReturn(true);
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player).breakBlock(block);
        assertTrue(mining.begin(player, targetNextBlock()));
        assertEquals(1, tasks.size());
    }

    @Test
    void deniedNativeBreakDoesNotDelayTheNextEligibleTarget() {
        when(player.breakBlock(block)).thenReturn(false);
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player).breakBlock(block);
        assertTrue(mining.begin(player, targetNextBlock()));
        assertEquals(2, tasks.size());
    }

    @Test
    void cancelledCustomBreakDoesNotDelayTheNextEligibleTarget() {
        doAnswer(call -> { ((OffhandBlockBreakEvent) call.getArgument(0)).setCancelled(true); return null; })
                .when(events).callEvent(isA(OffhandBlockBreakEvent.class));
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        verify(player, never()).breakBlock(any());
        assertTrue(mining.begin(player, targetNextBlock()));
        assertEquals(2, tasks.size());
    }

    @Test
    void stoppingCannotBypassPostBreakDelayButReloadClearsOldTiming() {
        when(player.breakBlock(block)).thenReturn(true);
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        Block next = targetNextBlock();
        mining.stop(player.getUniqueId());
        assertTrue(mining.begin(player, next));
        assertEquals(1, tasks.size());
        mining.close();
        assertTrue(mining.begin(player, next));
        assertEquals(2, tasks.size());
    }

    @Test
    void nativeBreakListenerCannotCreateASessionBeforeTheRestartDelayIsKnown() {
        final Block[] next = new Block[1];
        when(player.breakBlock(block)).thenAnswer(call -> {
            next[0] = targetNextBlock();
            assertTrue(mining.begin(player, next[0]));
            assertEquals(1, tasks.size());
            return true;
        });
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        assertTrue(mining.begin(player, next[0]));
        assertEquals(1, tasks.size());
        currentTick = 8;
        assertTrue(mining.begin(player, next[0]));
        assertEquals(2, tasks.size());
    }

    @Test
    void reloadDuringNativeBreakKeepsItsBarrierAndDoesNotRestoreOldTiming() {
        final Block[] next = new Block[1];
        when(player.breakBlock(block)).thenAnswer(call -> {
            mining.close();
            next[0] = targetNextBlock();
            assertTrue(mining.begin(player, next[0]));
            assertEquals(1, tasks.size());
            return true;
        });
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        assertTrue(mining.begin(player, next[0]));
        assertEquals(2, tasks.size());
    }

    @Test
    void deathClearsCompletedMiningDelayBeforeANewPlayerClock() {
        when(player.breakBlock(block)).thenReturn(true);
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        mining.onDeath(death);
        currentTick = 0;
        assertTrue(mining.begin(player, targetNextBlock()));
        assertEquals(2, tasks.size());
    }

    @Test
    void deathInsideNativeBreakDoesNotReinstallTheClearedDelay() {
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        when(player.breakBlock(block)).thenAnswer(call -> {
            when(player.isDead()).thenReturn(true);
            mining.onDeath(death);
            return true;
        });
        assertTrue(mining.begin(player, block));
        runTick(0);
        runTick(0);
        when(player.isDead()).thenReturn(false);
        currentTick = 0;
        assertTrue(mining.begin(player, targetNextBlock()));
        assertEquals(2, tasks.size());
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
        config.set("Mine.InputTimeoutTicks", 2);
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
        config.set("Mine.InputTimeoutTicks", 2);
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
        config.set("Mine.CorrectToolOnly", true);
        assertFalse(mining.begin(player, block));
        config.set("Mine.CorrectToolOnly", false);
        config.set("DisabledWorlds", Collections.singletonList("world"));
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
