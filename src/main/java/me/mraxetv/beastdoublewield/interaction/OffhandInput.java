package me.mraxetv.beastdoublewield.interaction;
import me.mraxetv.beastdoublewield.compat.ServerAccess;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.combat.OffhandCombat;
import me.mraxetv.beastdoublewield.mining.OffhandMining;
import me.mraxetv.beastdoublewield.util.HandAccess;
import me.mraxetv.beastdoublewield.util.ItemRules;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Claims one input for the actual offhand, including air use with an empty main hand. */
public final class OffhandInput implements Listener {
    private final BeastDoubleWieldPlugin plugin;
    private final OffhandCombat combat;
    private final OffhandMining mining;
    private final Map<UUID, Integer> lastAirInput = new HashMap<>();
    public OffhandInput(BeastDoubleWieldPlugin plugin, OffhandCombat combat, OffhandMining mining) {
        this.plugin = plugin; this.combat = combat; this.mining = mining;
    }
    private boolean enabled(Player player) {
        return plugin.isEnabled(player) && !HandAccess.isOffhandAction(player)
                && !(plugin.getConfig().getBoolean("Interaction.PreserveMainHandUse", true)
                && !emptyMainHand(player)
                && ItemRules.hasUseAction(player.getInventory().getItemInMainHand()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND && !(event.getAction() == Action.RIGHT_CLICK_AIR
                && event.getHand() == EquipmentSlot.OFF_HAND && emptyMainHand(player))) return;
        if (!enabled(player)) return;
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            // Vanilla skips air-use packets for an empty hand, so only the occupied
            // offhand may report the click. With both hands empty it sends no air-use
            // packet at all. Keep main-hand input for clients that do report it.
            int tick = player.getTicksLived();
            Integer previous = lastAirInput.put(player.getUniqueId(), tick);
            // Record denied input too: a second hand callback must not bypass it.
            if (previous != null && previous == tick) return;
        }
        // Air interactions are often pre-cancelled by vanilla because no block action exists.
        if (event.useItemInHand() == Event.Result.DENY) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.useInteractedBlock() == Event.Result.DENY || event.getClickedBlock() == null) return;
            if (plugin.getConfig().getBoolean("Interaction.PreserveMainHandUse", true)
                    && ItemRules.hasBlockUseAction(player.getInventory().getItemInMainHand(), event.getClickedBlock())) {
                // Native tool actions depend on the target; an axe should still
                // allow offhand input against blocks it cannot transform.
                mining.stop(player.getUniqueId());
                return;
            }
            if (plugin.getConfig().getBoolean("Interaction.PreserveBlockUse", true)
                    && ServerAccess.interactable(event.getClickedBlock().getType()) && !player.isSneaking()) return;
            if (mining.begin(player, event.getClickedBlock()) && plugin.getConfig().getBoolean("Mine.CancelInteraction", true))
                event.setCancelled(true);
            return;
        }
        Entity target = ServerAccess.targetEntity(player, plugin.getConfig().getDouble("Attack.Reach", 3));
        if (target != null && combat.attack(player, target)) {
            mining.stop(player.getUniqueId());
            if (plugin.getConfig().getBoolean("Attack.CancelInteraction", true)) event.setCancelled(true);
        } else if (target == null && plugin.getConfig().getBoolean("Swing.Air", false)
                && player.hasPermission("beastdoublewield.attack")
                && !plugin.getConfig().getStringList("DisabledWorlds").contains(player.getWorld().getName())
                && ItemRules.canUse(player, player.getInventory().getItemInOffHand(), plugin.getConfig().getConfigurationSection("Attack"))) {
            ServerAccess.swing(player);
        }
    }

    private static boolean emptyMainHand(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        return main == null || main.getAmount() <= 0 || ServerAccess.air(main.getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entity(PlayerInteractEntityEvent event) { handleEntity(event); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityAt(PlayerInteractAtEntityEvent event) { handleEntity(event); }
    private void handleEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !enabled(event.getPlayer())) return;
        if (combat.attack(event.getPlayer(), event.getRightClicked())) {
            mining.stop(event.getPlayer().getUniqueId());
            if (plugin.getConfig().getBoolean("Attack.CancelInteraction", true)) event.setCancelled(true);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        lastAirInput.remove(event.getPlayer().getUniqueId());
        mining.stop(event.getPlayer().getUniqueId()); combat.forget(event.getPlayer().getUniqueId());
    }
    @EventHandler public void world(PlayerChangedWorldEvent event) {
        lastAirInput.remove(event.getPlayer().getUniqueId());
        mining.stop(event.getPlayer().getUniqueId()); combat.forget(event.getPlayer().getUniqueId());
    }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        lastAirInput.remove(event.getPlayer().getUniqueId());
        mining.stop(event.getPlayer().getUniqueId()); combat.forget(event.getPlayer().getUniqueId());
    }
}
