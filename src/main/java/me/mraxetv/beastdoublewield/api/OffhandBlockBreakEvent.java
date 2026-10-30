package me.mraxetv.beastdoublewield.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/** Fired when mining completes, before the server's normal BlockBreakEvent. */
public final class OffhandBlockBreakEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Block block;
    private final ItemStack item;
    private boolean cancelled;
    public OffhandBlockBreakEvent(Player player, Block block, ItemStack item) {
        super(player); this.block = block; this.item = item.clone();
    }
    public Block getBlock() { return block; }
    public ItemStack getItem() { return item.clone(); }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean value) { cancelled = value; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
