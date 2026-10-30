package me.mraxetv.beastdoublewield.combat;
import me.mraxetv.beastdoublewield.compat.ServerAccess;
import me.mraxetv.beastdoublewield.placeholder.PlaceholderItem;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.api.OffhandAttackEvent;
import me.mraxetv.beastdoublewield.util.HandAccess;
import me.mraxetv.beastdoublewield.util.ItemRules;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

public final class OffhandCombat {
    private final BeastDoubleWieldPlugin plugin;
    private final AttackLimiter limiter = new AttackLimiter();
    private final Set<UUID> inFlight = new HashSet<>();
    private long generation;
    public OffhandCombat(BeastDoubleWieldPlugin plugin) { this.plugin = plugin; }

    /** True means this input belongs to offhand combat, including its cooldown. */
    public boolean attack(Player player, Entity target) {
        if (!eligible(player, target) || HandAccess.isOffhandAction(player)) return false;
        if (!inFlight.add(player.getUniqueId())) return true;
        try { return perform(player, target); }
        finally { inFlight.remove(player.getUniqueId()); }
    }

    private boolean perform(Player player, Entity target) {
        if (!limiter.isReady(player.getUniqueId(), player.getTicksLived())) return true;
        long started = generation;
        ItemStack weapon = player.getInventory().getItemInOffHand().clone();
        ItemStack main = player.getInventory().getItemInMainHand().clone();
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack acting = PlaceholderItem.logicalItem(weapon);
        OffhandAttackEvent intent = new OffhandAttackEvent(player, target, acting);
        plugin.getServer().getPluginManager().callEvent(intent);
        if (intent.isCancelled() || started != generation) return true;
        // Listeners may teleport, remove the victim, or replace items. Never act on stale intent.
        if (!eligible(player, target) || player.getInventory().getHeldItemSlot() != slot
                || !weapon.equals(player.getInventory().getItemInOffHand())
                || !main.equals(player.getInventory().getItemInMainHand())) return true;
        WeaponAttributes.withOffhand(player, main, acting, () -> {
            AttributeInstance speed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            double rate = plugin.getConfig().getBoolean("attack.cooldown", true) && speed != null ? speed.getValue() : 20;
            limiter.record(player.getUniqueId(), player.getTicksLived(), rate);
            HandAccess.withOffhand(player, () -> { ServerAccess.attack(player, target); return null; });
            plugin.recordAttack();
            return null;
        });
        ServerAccess.swing(player);
        return true;
    }

    private boolean eligible(Player player, Entity target) {
        ConfigurationSection rules = plugin.getConfig().getConfigurationSection("attack");
        if (!player.isOnline() || player.isDead() || !plugin.isEnabled(player)
                || !player.hasPermission("beastdoublewield.attack")
                || plugin.getConfig().getStringList("disabled-worlds").contains(player.getWorld().getName())
                || !ItemRules.canUse(player, player.getInventory().getItemInOffHand(), rules)) return false;
        if (rules.getBoolean("correct-tool-only") && !ItemRules.isWeapon(player.getInventory().getItemInOffHand())) return false;
        if (target == null || target.equals(player) || !target.isValid() || target.isDead()
                || !(target instanceof Damageable || target instanceof Vehicle || target instanceof Hanging || target instanceof EnderCrystal)
                || !player.getWorld().equals(target.getWorld())) return false;
        if (rules.getBoolean("protect-npcs", true) && target.hasMetadata("NPC")) return false;
        if (target instanceof Player && !player.getWorld().getPVP()) return false;
        if (plugin.getConfig().getBoolean("interaction.preserve-entity-use", true) && !player.isSneaking()
                && (target instanceof Vehicle || target instanceof Tameable || target instanceof Hanging || target instanceof Pig || target instanceof ArmorStand
                || (target.getType() != null && java.util.Arrays.asList("HORSE", "DONKEY", "MULE", "SKELETON_HORSE", "ZOMBIE_HORSE", "LLAMA", "TRADER_LLAMA",
                    "VILLAGER", "WANDERING_TRADER", "STRIDER", "ALLAY", "CAMEL").contains(target.getType().name())))) return false;
        double reach = rules.getDouble("reach", 3.0);
        return ServerAccess.distanceSquared(player, target) <= reach * reach
                && player.hasLineOfSight(target);
    }

    public void forget(UUID player) { limiter.forget(player); }
    public void clear() { generation++; limiter.clear(); }
}
