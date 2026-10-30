package me.mraxetv.beastdoublewield.combat;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.api.OffhandAttackEvent;
import me.mraxetv.beastdoublewield.util.HandAccess;
import me.mraxetv.beastdoublewield.util.ItemRules;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OffhandCombatTest {
    private BeastDoubleWieldPlugin plugin;
    private Player player;
    private LivingEntity target;
    private World world;
    private PluginManager events;
    private PlayerInventory inventory;
    private ItemStack main;
    private ItemStack off;
    private AttributeInstance speed;
    private YamlConfiguration config;
    private OffhandCombat combat;
    private MockedStatic<ItemRules> rules;
    private MockedStatic<HandAccess> hands;
    private MockedStatic<WeaponAttributes> attributes;

    @BeforeEach
    void setup() {
        plugin = mock(BeastDoubleWieldPlugin.class);
        player = mock(Player.class);
        world = mock(World.class);
        events = mock(PluginManager.class);
        Server server = mock(Server.class);
        inventory = mock(PlayerInventory.class);
        main = mock(ItemStack.class);
        off = mock(ItemStack.class);
        speed = mock(AttributeInstance.class);
        config = new YamlConfiguration();
        config.set("attack.enabled", true);
        config.set("attack.reach", 3.0);
        config.set("disabled-worlds", Collections.emptyList());
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OffhandCombatTest"));
        when(plugin.isEnabled(player)).thenReturn(true);
        when(server.getPluginManager()).thenReturn(events);
        when(world.getName()).thenReturn("world");
        when(world.getPVP()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("beastdoublewield.attack")).thenReturn(true);
        when(player.hasLineOfSight(any(Entity.class))).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getTicksLived()).thenReturn(100);
        when(player.getEyeLocation()).thenReturn(new Location(world, 0, 1.6, 0));
        when(player.getInventory()).thenReturn(inventory);
        when(player.getAttribute(Attribute.GENERIC_ATTACK_SPEED)).thenReturn(speed);
        when(speed.getValue()).thenReturn(1.6);
        when(inventory.getItemInMainHand()).thenReturn(main);
        when(inventory.getItemInOffHand()).thenReturn(off);
        when(main.clone()).thenReturn(main);
        when(off.clone()).thenReturn(off);
        target = victim(LivingEntity.class);
        rules = mockStatic(ItemRules.class);
        rules.when(() -> ItemRules.canUse(eq(player), any(), any())).thenReturn(true);
        rules.when(() -> ItemRules.isWeapon(any())).thenReturn(true);
        hands = mockStatic(HandAccess.class);
        hands.when(() -> HandAccess.withOffhand(eq(player), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        attributes = mockStatic(WeaponAttributes.class);
        attributes.when(() -> WeaponAttributes.withOffhand(eq(player), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(3)).get());
        combat = new OffhandCombat(plugin);
    }

    private <T extends Entity> T victim(Class<T> type) {
        T entity = mock(type);
        when(entity.isValid()).thenReturn(true);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getBoundingBox()).thenReturn(new BoundingBox(1, 0, 0, 2, 2, 1));
        return entity;
    }

    @AfterEach
    void cleanup() {
        combat.clear();
        attributes.close();
        hands.close();
        rules.close();
    }

    @Test
    void cancelledIntentNeitherAttacksNorConsumesCooldown() {
        doAnswer(call -> { ((OffhandAttackEvent) call.getArgument(0)).setCancelled(true); return null; })
                .when(events).callEvent(isA(OffhandAttackEvent.class));
        assertTrue(combat.attack(player, target));
        verify(player, never()).attack(any());
        verify(player, never()).swingOffHand();
        doNothing().when(events).callEvent(any());
        assertTrue(combat.attack(player, target));
        verify(player).attack(target);
    }

    @Test
    void npcAndDisabledPvpRejectBeforeIntent() {
        when(target.hasMetadata("NPC")).thenReturn(true);
        assertFalse(combat.attack(player, target));
        Player opponent = victim(Player.class);
        when(world.getPVP()).thenReturn(false);
        assertFalse(combat.attack(player, opponent));
        verify(events, never()).callEvent(any());
        verify(player, never()).attack(any());
    }

    @Test
    void worldLineOfSightAndReachAreRequired() {
        when(target.getWorld()).thenReturn(mock(World.class));
        assertFalse(combat.attack(player, target));
        when(target.getWorld()).thenReturn(world);
        when(player.hasLineOfSight(target)).thenReturn(false);
        assertFalse(combat.attack(player, target));
        when(player.hasLineOfSight(target)).thenReturn(true);
        when(target.getBoundingBox()).thenReturn(new BoundingBox(3.01, 0, 0, 4, 2, 1));
        assertFalse(combat.attack(player, target));
        verify(events, never()).callEvent(any());
    }

    @Test
    void reachUsesNearestHitboxSurface() {
        when(target.getBoundingBox()).thenReturn(new BoundingBox(3.0, -1, -1, 10, 10, 10));
        assertTrue(combat.attack(player, target));
        verify(player).attack(target);
    }

    @Test
    void mountUseIsPreservedUnlessSneaking() {
        Pig pig = victim(Pig.class);
        assertFalse(combat.attack(player, pig));
        when(player.isSneaking()).thenReturn(true);
        assertTrue(combat.attack(player, pig));
        verify(player).attack(pig);
    }

    @Test void boatsAndFramesKeepUsePriorityAndCanBeAttackedWhileSneaking() {
        Entity boat = victim(org.bukkit.entity.Boat.class);
        Entity frame = victim(org.bukkit.entity.ItemFrame.class);
        assertFalse(combat.attack(player, boat)); assertFalse(combat.attack(player, frame));
        when(player.isSneaking()).thenReturn(true);
        assertTrue(combat.attack(player, boat)); verify(player).attack(boat);
        combat.clear();
        assertTrue(combat.attack(player, frame)); verify(player).attack(frame);
    }

    @Test void endCrystalsUseNativeAttackInsteadOfManualExplosion() {
        Entity crystal = victim(org.bukkit.entity.EnderCrystal.class);
        assertTrue(combat.attack(player, crystal)); verify(player).attack(crystal);
    }

    @Test
    void replacingOffhandDuringIntentDoesNotAttack() {
        doAnswer(call -> { when(inventory.getItemInOffHand()).thenReturn(mock(ItemStack.class)); return null; })
                .when(events).callEvent(any());
        assertTrue(combat.attack(player, target));
        verify(player, never()).attack(any());
        attributes.verifyNoInteractions();
    }

    @Test
    void replacingMainHandDuringIntentDoesNotAttack() {
        doAnswer(call -> { when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class)); return null; })
                .when(events).callEvent(any());
        assertTrue(combat.attack(player, target));
        verify(player, never()).attack(any());
        attributes.verifyNoInteractions();
    }

    @Test
    void changingHotbarSlotDuringIntentDoesNotAttack() {
        doAnswer(call -> { when(inventory.getHeldItemSlot()).thenReturn(1); return null; })
                .when(events).callEvent(any());
        assertTrue(combat.attack(player, target));
        verify(player, never()).attack(any());
    }

    @Test
    void teleportingVictimDuringIntentDoesNotAttack() {
        doAnswer(call -> { when(target.getWorld()).thenReturn(mock(World.class)); return null; })
                .when(events).callEvent(any());
        assertTrue(combat.attack(player, target));
        verify(player, never()).attack(any());
    }

    @Test
    void cooldownIsSharedAcrossTargetsAndAdmitsAtExactTick() {
        LivingEntity other = victim(LivingEntity.class);
        assertTrue(combat.attack(player, target));
        when(player.getTicksLived()).thenReturn(112);
        assertTrue(combat.attack(player, other));
        verify(player, never()).attack(other);
        when(player.getTicksLived()).thenReturn(113);
        assertTrue(combat.attack(player, other));
        verify(player).attack(target);
        verify(player).attack(other);
        verify(events, times(2)).callEvent(isA(OffhandAttackEvent.class));
    }

    @Test
    void disabledCooldownStillDeduplicatesSameTick() {
        config.set("attack.cooldown", false);
        assertTrue(combat.attack(player, target));
        assertTrue(combat.attack(player, target));
        verify(player).attack(target);
        when(player.getTicksLived()).thenReturn(101);
        assertTrue(combat.attack(player, target));
        verify(player, times(2)).attack(target);
    }

    @Test
    void nativeExceptionReleasesInFlightGuard() {
        doThrow(new IllegalStateException("native failure")).doNothing().when(player).attack(target);
        assertThrows(IllegalStateException.class, () -> combat.attack(player, target));
        when(player.getTicksLived()).thenReturn(200);
        assertTrue(combat.attack(player, target));
        verify(player, times(2)).attack(target);
        verify(player).swingOffHand();
    }

    @Test
    void recursiveIntentCannotDispatchSecondAttack() {
        AtomicInteger intents = new AtomicInteger();
        doAnswer(call -> {
            assertEquals(1, intents.incrementAndGet(), "Reentrant intent event was dispatched");
            assertTrue(combat.attack(player, target), "Reentrant input remains owned by offhand combat");
            return null;
        }).when(events).callEvent(isA(OffhandAttackEvent.class));
        assertTrue(combat.attack(player, target));
        assertEquals(1, intents.get());
        verify(player).attack(target);
    }

    @Test
    void clearDuringIntentInvalidatesOldAttackButAllowsNewOne() {
        doAnswer(call -> { combat.clear(); return null; }).when(events).callEvent(any());
        assertTrue(combat.attack(player, target));
        verify(player, never()).attack(any());
        doNothing().when(events).callEvent(any());
        assertTrue(combat.attack(player, target));
        verify(player).attack(target);
    }

    @Test
    void forgottenPlayerDoesNotRetainCooldown() {
        assertTrue(combat.attack(player, target));
        combat.forget(player.getUniqueId());
        assertTrue(combat.attack(player, target));
        verify(player, times(2)).attack(target);
    }
}
