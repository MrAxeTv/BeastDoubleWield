package me.mraxetv.beastdoublewield.interaction;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.combat.OffhandCombat;
import me.mraxetv.beastdoublewield.compat.ServerAccess;
import me.mraxetv.beastdoublewield.mining.OffhandMining;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Input boundary tests: permissions and native break/damage are exercised by their services. */
class OffhandInputTest {
    private BeastDoubleWieldPlugin plugin;
    private OffhandCombat combat;
    private OffhandMining mining;
    private Player player;
    private PlayerInventory inventory;
    private Block block;
    private YamlConfiguration config;
    private OffhandInput input;

    @BeforeEach void setup() {
        plugin = mock(BeastDoubleWieldPlugin.class);
        combat = mock(OffhandCombat.class);
        mining = mock(OffhandMining.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        block = mock(Block.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.isEnabled(player)).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
        when(block.getType()).thenReturn(Material.STONE);
        input = new OffhandInput(plugin, combat, mining);
    }

    private PlayerInteractEvent click(EquipmentSlot hand) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, hand);
    }

    private PlayerInteractEvent air(EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.HAND ? inventory.getItemInMainHand() : inventory.getItemInOffHand();
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, held, null, BlockFace.SELF, hand);
    }

    private MockedStatic<ServerAccess> airSwings() {
        config.set("Swing.Air", true);
        config.set("Attack.Enabled", true);
        config.set("Attack.GameModes", Arrays.asList("SURVIVAL"));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.hasPermission("beastdoublewield.attack")).thenReturn(true);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.IRON_SWORD));
        // Assert server-side swing requests; a client controls its own arm rendering.
        MockedStatic<ServerAccess> server = mockStatic(ServerAccess.class);
        server.when(() -> ServerAccess.air(any(Material.class))).thenCallRealMethod();
        server.when(() -> ServerAccess.materialName(any(Material.class))).thenCallRealMethod();
        server.when(() -> ServerAccess.targetEntity(player, 3.0)).thenReturn(null);
        server.when(() -> ServerAccess.swing(player)).thenAnswer(invocation -> null);
        return server;
    }

    @Test void offhandAirCallbackWorksForNullAirAndZeroAmountMainHand() {
        try (MockedStatic<ServerAccess> server = airSwings()) {
            ItemStack[] empty = {null, new ItemStack(Material.AIR), new ItemStack(Material.SHIELD, 0)};
            for (int index = 0; index < empty.length; index++) {
                when(inventory.getItemInMainHand()).thenReturn(empty[index]);
                when(player.getTicksLived()).thenReturn(index + 1);
                input.interact(air(EquipmentSlot.OFF_HAND));
            }
            server.verify(() -> ServerAccess.swing(player), times(3));
            verifyNoInteractions(mining, combat);
        }
    }

    @Test void emptyMainFallbackCanAttackARayTargetOnce() {
        try (MockedStatic<ServerAccess> server = airSwings()) {
            Entity target = mock(Entity.class);
            server.when(() -> ServerAccess.targetEntity(player, 3.0)).thenReturn(target);
            when(combat.attack(player, target)).thenReturn(true);
            PlayerInteractEvent offhand = air(EquipmentSlot.OFF_HAND);
            input.interact(offhand);
            input.interact(air(EquipmentSlot.HAND));
            verify(combat, times(1)).attack(player, target);
            verify(mining, times(1)).stop(player.getUniqueId());
            assertTrue(offhand.isCancelled());
            server.verify(() -> ServerAccess.swing(player), never());
        }
    }

    @Test void occupiedMainHandUsesOnlyMainCallbackForAir() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.STICK));
        try (MockedStatic<ServerAccess> server = airSwings()) {
            input.interact(air(EquipmentSlot.OFF_HAND));
            server.verify(() -> ServerAccess.swing(player), never());
            input.interact(air(EquipmentSlot.HAND));
            input.interact(air(EquipmentSlot.OFF_HAND));
            server.verify(() -> ServerAccess.swing(player), times(1));
        }
    }

    @Test void emptyMainPairedAirCallbacksSwingOnceAndLaterInputStillWorks() {
        try (MockedStatic<ServerAccess> server = airSwings()) {
            input.interact(air(EquipmentSlot.HAND));
            input.interact(air(EquipmentSlot.OFF_HAND));
            server.verify(() -> ServerAccess.swing(player), times(1));
            when(player.getTicksLived()).thenReturn(1);
            input.interact(air(EquipmentSlot.OFF_HAND));
            input.interact(air(EquipmentSlot.HAND));
            server.verify(() -> ServerAccess.swing(player), times(2));
        }
    }

    @Test void deniedAirCallbackCannotBeBypassedByTheOtherHandInThatTick() {
        try (MockedStatic<ServerAccess> server = airSwings()) {
            PlayerInteractEvent denied = air(EquipmentSlot.HAND);
            denied.setUseItemInHand(Event.Result.DENY);
            input.interact(denied);
            input.interact(air(EquipmentSlot.OFF_HAND));
            when(player.getTicksLived()).thenReturn(1);
            PlayerInteractEvent deniedOffhand = air(EquipmentSlot.OFF_HAND);
            deniedOffhand.setUseItemInHand(Event.Result.DENY);
            input.interact(deniedOffhand);
            input.interact(air(EquipmentSlot.HAND));
            server.verify(() -> ServerAccess.swing(player), never());
            server.verify(() -> ServerAccess.targetEntity(player, 3.0), never());
            verifyNoInteractions(mining, combat);
        }
    }

    @Test void fallbackAirInputStillRequiresPermissionAndEnabledAirSwing() {
        try (MockedStatic<ServerAccess> server = airSwings()) {
            when(player.hasPermission("beastdoublewield.attack")).thenReturn(false);
            input.interact(air(EquipmentSlot.OFF_HAND));
            when(player.getTicksLived()).thenReturn(1);
            when(player.hasPermission("beastdoublewield.attack")).thenReturn(true);
            config.set("Swing.Air", false);
            input.interact(air(EquipmentSlot.OFF_HAND));
            when(player.getTicksLived()).thenReturn(2);
            config.set("Swing.Air", true);
            config.set("Attack.Enabled", false);
            input.interact(air(EquipmentSlot.OFF_HAND));
            server.verify(() -> ServerAccess.swing(player), never());
        }
    }

    @Test void emptyOffhandFistNeedsAllowanceAndAnActualMainHandAirInput() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.STICK));
        try (MockedStatic<ServerAccess> server = airSwings()) {
            when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.AIR));
            input.interact(air(EquipmentSlot.HAND));
            server.verify(() -> ServerAccess.swing(player), never());
            when(player.getTicksLived()).thenReturn(1);
            config.set("Attack.AllowEmptyHand", true);
            input.interact(air(EquipmentSlot.HAND));
            server.verify(() -> ServerAccess.swing(player), times(1));
        }
    }

    @Test void quitClearsAirDeduplicationForARejoiningPlayer() {
        try (MockedStatic<ServerAccess> server = airSwings()) {
            input.interact(air(EquipmentSlot.OFF_HAND));
            input.quit(new PlayerQuitEvent(player, "left"));
            input.interact(air(EquipmentSlot.OFF_HAND));
            server.verify(() -> ServerAccess.swing(player), times(2));
        }
    }

    @Test void itemUseDenialNeverStartsAnOffhandAction() {
        PlayerInteractEvent event = click(EquipmentSlot.HAND);
        event.setUseItemInHand(Event.Result.DENY);
        input.interact(event);
        verifyNoInteractions(mining, combat);
        assertEquals(Event.Result.DENY, event.useItemInHand());
    }

    @Test void protectedBlockDenialNeverStartsMining() {
        PlayerInteractEvent event = click(EquipmentSlot.HAND);
        event.setUseInteractedBlock(Event.Result.DENY);
        input.interact(event);
        verifyNoInteractions(mining, combat);
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
    }

    @Test void mainAndOffhandCallbacksStartOnlyOneMiningAction() {
        when(mining.begin(player, block)).thenReturn(true);
        PlayerInteractEvent main = click(EquipmentSlot.HAND);
        PlayerInteractEvent off = click(EquipmentSlot.OFF_HAND);
        input.interact(main);
        input.interact(off);
        verify(mining, times(1)).begin(player, block);
        verifyNoInteractions(combat);
        assertTrue(main.isCancelled());
        assertFalse(off.isCancelled());
    }

    @Test void featureDisabledByGlobalOrPersonalGateLeavesInputUntouched() {
        when(plugin.isEnabled(player)).thenReturn(false);
        PlayerInteractEvent event = click(EquipmentSlot.HAND);
        input.interact(event);
        verifyNoInteractions(mining, combat);
        assertFalse(event.isCancelled());
    }

    @Test void ordinaryBlockUseWinsUnlessSneaking() {
        when(block.getType()).thenReturn(Material.CHEST);
        input.interact(click(EquipmentSlot.HAND));
        verifyNoInteractions(mining, combat);
        when(player.isSneaking()).thenReturn(true);
        input.interact(click(EquipmentSlot.HAND));
        verify(mining).begin(player, block);
    }

    @Test void mainHandUseWinsUnlessAdministratorDisablesPreservation() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.SHIELD));
        input.interact(click(EquipmentSlot.HAND));
        verifyNoInteractions(mining, combat);
        config.set("Interaction.PreserveMainHandUse", false);
        input.interact(click(EquipmentSlot.HAND));
        verify(mining).begin(player, block);
    }

    @Test void mainHandAxeStrippingKeepsNativeInputAndStopsOffhandMining() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIAMOND_AXE));
        when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.DIAMOND_SWORD));
        when(block.getType()).thenReturn(Material.OAK_LOG);
        when(mining.begin(player, block)).thenReturn(true);
        PlayerInteractEvent main = click(EquipmentSlot.HAND);
        PlayerInteractEvent off = click(EquipmentSlot.OFF_HAND);
        input.interact(main);
        input.interact(off);
        assertFalse(main.isCancelled(), "Applicable main-hand stripping must reach vanilla");
        assertEquals(Event.Result.DEFAULT, main.useItemInHand());
        assertEquals(Event.Result.ALLOW, main.useInteractedBlock());
        assertFalse(off.isCancelled());
        verify(mining, never()).begin(player, block);
        verify(mining).stop(player.getUniqueId());
        verifyNoInteractions(combat);
    }

    @Test void axeWithoutAnApplicableBlockActionKeepsOffhandMining() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIAMOND_AXE));
        when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.DIAMOND_SWORD));
        when(mining.begin(player, block)).thenReturn(true);
        for (Material target : Arrays.asList(Material.STRIPPED_OAK_LOG, Material.STONE)) {
            when(block.getType()).thenReturn(target);
            PlayerInteractEvent main = click(EquipmentSlot.HAND);
            input.interact(main);
            input.interact(click(EquipmentSlot.OFF_HAND));
            assertTrue(main.isCancelled());
        }
        verify(mining, times(2)).begin(player, block);
        verify(mining, never()).stop(any(UUID.class));
        verifyNoInteractions(combat);
    }

    @Test void axeStillAllowsOffhandAirSwingAndEntityAttack() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIAMOND_AXE));
        try (MockedStatic<ServerAccess> server = airSwings()) {
            when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.DIAMOND_SWORD));
            input.interact(air(EquipmentSlot.HAND));
            input.interact(air(EquipmentSlot.OFF_HAND));
            server.verify(() -> ServerAccess.swing(player), times(1));

            Entity target = mock(Entity.class);
            when(combat.attack(player, target)).thenReturn(true);
            PlayerInteractEntityEvent main = new PlayerInteractEntityEvent(player, target, EquipmentSlot.HAND);
            input.entity(main);
            input.entity(new PlayerInteractEntityEvent(player, target, EquipmentSlot.OFF_HAND));
            verify(combat, times(1)).attack(player, target);
            verify(mining).stop(player.getUniqueId());
            assertTrue(main.isCancelled());
        }
    }

    @Test void axeBlockActionPreservationCanBeDisabled() {
        config.set("Interaction.PreserveMainHandUse", false);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIAMOND_AXE));
        when(block.getType()).thenReturn(Material.OAK_LOG);
        when(mining.begin(player, block)).thenReturn(true);
        PlayerInteractEvent event = click(EquipmentSlot.HAND);
        input.interact(event);
        assertTrue(event.isCancelled());
        verify(mining).begin(player, block);
        verify(mining, never()).stop(any(UUID.class));
    }

    @Test void sneakingStillPreservesApplicableMainHandAxeUse() {
        config.set("Interaction.PreserveBlockUse", false);
        when(player.isSneaking()).thenReturn(true);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIAMOND_AXE));
        when(block.getType()).thenReturn(Material.OAK_LOG);
        PlayerInteractEvent event = click(EquipmentSlot.HAND);
        input.interact(event);
        assertFalse(event.isCancelled());
        verify(mining, never()).begin(player, block);
        verify(mining).stop(player.getUniqueId());
    }

    @Test void deniedAxeUseIsNotReenabledOrClaimedByTheOtherHand() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIAMOND_AXE));
        when(block.getType()).thenReturn(Material.OAK_LOG);
        PlayerInteractEvent itemDenied = click(EquipmentSlot.HAND);
        itemDenied.setUseItemInHand(Event.Result.DENY);
        PlayerInteractEvent blockDenied = click(EquipmentSlot.HAND);
        blockDenied.setUseInteractedBlock(Event.Result.DENY);
        PlayerInteractEvent cancelled = click(EquipmentSlot.HAND);
        cancelled.setCancelled(true);
        for (PlayerInteractEvent event : Arrays.asList(itemDenied, blockDenied, cancelled)) {
            Event.Result itemResult = event.useItemInHand();
            Event.Result blockResult = event.useInteractedBlock();
            input.interact(event);
            input.interact(click(EquipmentSlot.OFF_HAND));
            assertEquals(itemResult, event.useItemInHand());
            assertEquals(blockResult, event.useInteractedBlock());
        }
        verifyNoInteractions(mining, combat);
    }

    @Test void switchingFromOffhandMiningToStrippingStopsTheExistingSession() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIAMOND_AXE));
        when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.DIAMOND_SWORD));
        when(mining.begin(player, block)).thenReturn(true);
        input.interact(click(EquipmentSlot.HAND));
        when(block.getType()).thenReturn(Material.OAK_LOG);
        PlayerInteractEvent strip = click(EquipmentSlot.HAND);
        input.interact(strip);
        InOrder order = inOrder(mining);
        order.verify(mining).begin(player, block);
        order.verify(mining).stop(player.getUniqueId());
        order.verifyNoMoreInteractions();
        assertFalse(strip.isCancelled());
    }

    @Test void ineligibleMiningDoesNotCancelOrdinaryInteraction() {
        when(mining.begin(player, block)).thenReturn(false);
        PlayerInteractEvent event = click(EquipmentSlot.HAND);
        input.interact(event);
        assertFalse(event.isCancelled());
    }

    @Test void cancellationCanBeExplicitlyDisabledForEligibleMining() {
        config.set("Mine.CancelInteraction", false);
        when(mining.begin(player, block)).thenReturn(true);
        PlayerInteractEvent event = click(EquipmentSlot.HAND);
        input.interact(event);
        verify(mining).begin(player, block);
        assertFalse(event.isCancelled());
    }

    @Test void offhandEntityCallbackCannotAttackAgain() {
        Entity target = mock(Entity.class);
        when(combat.attack(player, target)).thenReturn(true);
        PlayerInteractEntityEvent main = new PlayerInteractEntityEvent(player, target, EquipmentSlot.HAND);
        PlayerInteractEntityEvent off = new PlayerInteractEntityEvent(player, target, EquipmentSlot.OFF_HAND);
        input.entity(main);
        input.entity(off);
        verify(combat, times(1)).attack(player, target);
        verify(mining, times(1)).stop(player.getUniqueId());
        assertTrue(main.isCancelled());
        assertFalse(off.isCancelled());
    }

    @Test void rejectedEntityActionKeepsMountingOrTradingInteraction() {
        Entity target = mock(Entity.class);
        when(combat.attack(player, target)).thenReturn(false);
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, target, EquipmentSlot.HAND);
        input.entity(event);
        assertFalse(event.isCancelled());
        verifyNoInteractions(mining);
    }
}
