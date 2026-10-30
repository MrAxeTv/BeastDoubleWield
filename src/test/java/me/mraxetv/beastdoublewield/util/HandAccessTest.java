package me.mraxetv.beastdoublewield.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandAccessTest {
    private Player player;
    private PlayerInventory inventory;
    private final ItemStack originalMain = mock(ItemStack.class);
    private final ItemStack originalOff = mock(ItemStack.class);
    private AtomicReference<ItemStack> main;
    private AtomicReference<ItemStack> off;
    @BeforeEach void setup() {
        player = mock(Player.class); inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHeldItemSlot()).thenReturn(2);
        main = new AtomicReference<>(originalMain); off = new AtomicReference<>(originalOff);
        when(inventory.getItem(2)).thenAnswer(call -> main.get()); when(inventory.getItemInOffHand()).thenAnswer(call -> off.get());
        doAnswer(call -> { main.set(call.getArgument(1)); return null; }).when(inventory).setItem(eq(2), any());
        doAnswer(call -> { off.set(call.getArgument(0)); return null; }).when(inventory).setItemInOffHand(any());
    }
    @Test void restoresDamagedOffhandRatherThanOldSnapshot() {
        ItemStack damaged = mock(ItemStack.class);
        HandAccess.withOffhand(player, () -> {
            assertSame(originalOff, main.get()); assertSame(originalMain, off.get());
            assertTrue(HandAccess.isOffhandAction(player)); main.set(damaged); return null;
        });
        assertSame(originalMain, main.get()); assertSame(damaged, off.get()); assertFalse(HandAccess.isOffhandAction(player));
    }
    @Test void brokenItemStaysBrokenAndExceptionRestoresOtherHand() {
        assertThrows(IllegalArgumentException.class, () -> HandAccess.withOffhand(player, () -> {
            main.set(null); throw new IllegalArgumentException("provider failed");
        }));
        assertSame(originalMain, main.get()); assertNull(off.get()); assertFalse(HandAccess.isOffhandAction(player));
    }
    @Test void nestedOperationCannotSwapTwice() {
        HandAccess.withOffhand(player, () -> {
            assertThrows(IllegalStateException.class, () -> HandAccess.withOffhand(player, () -> null));
            assertTrue(HandAccess.isOffhandAction(player)); return null;
        });
        assertSame(originalMain, main.get()); assertSame(originalOff, off.get());
    }
    @Test void partialSetupRollsBackAndReleasesGuard() {
        doThrow(new IllegalArgumentException("write failed")).when(inventory).setItemInOffHand(originalMain);
        assertThrows(IllegalArgumentException.class, () -> HandAccess.withOffhand(player, () -> null));
        assertSame(originalMain, main.get()); assertSame(originalOff, off.get()); assertFalse(HandAccess.isOffhandAction(player));
    }
    @Test void acquisitionFailureDoesNotPoisonFutureActions() {
        when(player.getInventory()).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class, () -> HandAccess.withOffhand(player, () -> null));
        assertFalse(HandAccess.isOffhandAction(player));
    }
    @Test void selectedSlotChangeDoesNotOverwriteNewSlot() {
        HandAccess.withOffhand(player, () -> { when(inventory.getHeldItemSlot()).thenReturn(5); return null; });
        assertSame(originalMain, main.get()); assertSame(originalOff, off.get());
        verify(inventory, never()).setItem(eq(5), any());
    }

    @Test void shutdownCleanupRunsOnlyAfterHandsAndScopeAreRestored() {
        HandAccess.withOffhand(player, () -> {
            HandAccess.afterOffhand(player, () -> {
                assertFalse(HandAccess.isOffhandAction(player));
                assertSame(originalMain, main.get());
                assertSame(originalOff, off.get());
                main.set(null);
            });
            assertSame(originalOff, main.get());
            assertSame(originalMain, off.get());
            return null;
        });
        assertNull(main.get());
        assertSame(originalOff, off.get());
    }

    @Test void failingCleanupDoesNotHideNativeFailureOrSkipRemainingCleanup() {
        IllegalArgumentException nativeFailure = new IllegalArgumentException("native operation");
        IllegalStateException cleanupFailure = new IllegalStateException("first cleanup");
        Throwable actual = assertThrows(IllegalArgumentException.class, () -> HandAccess.withOffhand(player, () -> {
            HandAccess.afterOffhand(player, () -> { throw cleanupFailure; });
            HandAccess.afterOffhand(player, () -> { assertFalse(HandAccess.isOffhandAction(player)); main.set(null); });
            throw nativeFailure;
        }));
        assertSame(nativeFailure, actual);
        assertArrayEquals(new Throwable[]{cleanupFailure}, actual.getSuppressed());
        assertNull(main.get());
        assertSame(originalOff, off.get());
    }

    @Test void visualArmBecomesRealAirForNativeActionAndReturnsAfterward() {
        try (org.mockito.MockedStatic<me.mraxetv.beastdoublewield.placeholder.PlaceholderItem> items =
                     mockStatic(me.mraxetv.beastdoublewield.placeholder.PlaceholderItem.class)) {
            items.when(() -> me.mraxetv.beastdoublewield.placeholder.PlaceholderItem.isArm(originalOff)).thenReturn(true);
            HandAccess.withOffhand(player, () -> {
                assertEquals(org.bukkit.Material.AIR, main.get().getType());
                assertSame(originalMain, off.get()); return null;
            });
            assertSame(originalOff, off.get()); assertSame(originalMain, main.get());
            ItemStack granted = new ItemStack(org.bukkit.Material.DIAMOND);
            HandAccess.withOffhand(player, () -> { main.set(granted); return null; });
            assertSame(granted, off.get()); assertSame(originalMain, main.get());
        }
    }
}
