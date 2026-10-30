package me.mraxetv.beastdoublewield.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** A synchronous hand transaction. Never hold this scope across a task or tick. */
public final class HandAccess {
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static final Map<UUID, List<Runnable>> AFTER = new HashMap<>();
    private HandAccess() { }

    /** Useful for other plugins' synchronous damage/break listeners. Server thread only. */
    public static boolean isOffhandAction(Player player) { return ACTIVE.contains(player.getUniqueId()); }

    /** Lifecycle cleanup can wait for a synchronous hand scope even during plugin shutdown. */
    public static void afterOffhand(Player player, Runnable operation) {
        UUID id = player.getUniqueId();
        if (!ACTIVE.contains(id)) { operation.run(); return; }
        AFTER.computeIfAbsent(id, ignored -> new ArrayList<>()).add(operation);
    }

    public static <T> T withOffhand(Player player, Supplier<T> operation) {
        if (!ACTIVE.add(player.getUniqueId())) throw new IllegalStateException("Nested offhand operation");
        PlayerInventory inventory = null;
        int slot = -1;
        ItemStack main = null;
        boolean mainWritten = false;
        boolean swapped = false;
        T result = null;
        Throwable failure = null;
        try {
            inventory = player.getInventory();
            slot = inventory.getHeldItemSlot();
            main = inventory.getItem(slot);
            ItemStack off = inventory.getItemInOffHand();
            inventory.setItem(slot, off);
            mainWritten = true;
            inventory.setItemInOffHand(main);
            swapped = true;
            result = operation.get();
        } catch (RuntimeException | Error error) {
            failure = error;
        } finally {
            try {
                if (swapped) {
                    // Read back the exact mutated stacks. Native damage, breakage, consumption
                    // and other listeners' changes belong to the hand performing the action.
                    ItemStack used = inventory.getItem(slot);
                    ItemStack other = inventory.getItemInOffHand();
                    inventory.setItem(slot, other);
                    inventory.setItemInOffHand(used);
                } else if (mainWritten) {
                    inventory.setItem(slot, main);
                }
            } catch (RuntimeException | Error error) {
                failure = combine(failure, error);
            } finally {
                ACTIVE.remove(player.getUniqueId());
                List<Runnable> callbacks = AFTER.remove(player.getUniqueId());
                if (callbacks != null) for (Runnable callback : callbacks) {
                    try { callback.run(); }
                    catch (RuntimeException | Error error) { failure = combine(failure, error); }
                }
            }
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw (RuntimeException) failure;
        return result;
    }

    private static Throwable combine(Throwable first, Throwable later) {
        if (first == null) return later;
        if (first != later) first.addSuppressed(later);
        return first;
    }
}
