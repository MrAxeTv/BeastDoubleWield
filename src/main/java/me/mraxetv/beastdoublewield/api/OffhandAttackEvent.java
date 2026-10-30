package me.mraxetv.beastdoublewield.api;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/** Synchronous intent event, before native attack. Cancellation prevents the attack. */
public final class OffhandAttackEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Entity target;
    private final ItemStack item;
    private boolean cancelled;
    public OffhandAttackEvent(Player player, Entity target, ItemStack item) {
        super(player); this.target = target; this.item = item.clone();
    }
    public Entity getTarget() { return target; }
    public ItemStack getItem() { return item.clone(); }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean value) { cancelled = value; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
