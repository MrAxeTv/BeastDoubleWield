package me.mraxetv.beastdoublewield.util;
import me.mraxetv.beastdoublewield.compat.ServerAccess;
import me.mraxetv.beastdoublewield.placeholder.PlaceholderItem;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Validated, per-action rules; item names and lore never confer identity or permission. */
public final class ItemRules {
    private static final Map<ConfigurationSection, List<Pattern>> FILTERS = new WeakHashMap<>();
    private ItemRules() { }

    public static boolean canUse(Player player, ItemStack item, ConfigurationSection rules) {
        if (PlaceholderItem.isOwned(item)) return false;
        if (rules == null || !rules.getBoolean("enabled", true)) return false;
        if (!rules.getBoolean(player.isSneaking() ? "sneaking" : "standing", true)) return false;
        if (!rules.getStringList("game-modes").contains(player.getGameMode().name())) return false;
        boolean empty = item == null || ServerAccess.air(item.getType()) || item.getAmount() < 1;
        if (empty && !rules.getBoolean("allow-empty-hand", false)) return false;
        String material = empty ? "AIR" : ServerAccess.materialName(item.getType());
        String rawMaterial = empty ? "AIR" : item.getType().name();
        List<Pattern> patterns = FILTERS.get(rules);
        if (patterns == null) { patterns = compile(rules); FILTERS.put(rules, patterns); }
        boolean match = false;
        for (Pattern pattern : patterns) if (pattern.matcher(material).matches() || pattern.matcher(rawMaterial).matches()) { match = true; break; }
        if ("WHITELIST".equals(rules.getString("materials.mode", "BLACKLIST"))) return match;
        return !match;
    }

    public static boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_SPADE") || name.endsWith("_HOE") || name.equals("TRIDENT") || name.equals("MACE")
                || ServerAccess.modifiers(item, org.bukkit.inventory.EquipmentSlot.HAND, org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE)
                    .stream().anyMatch(modifier -> modifier.getAmount() > 0);
    }

    public static boolean hasUseAction(ItemStack item) {
        if (item == null || ServerAccess.air(item.getType())) return false;
        Material material = item.getType();
        String name = ServerAccess.materialName(material);
        if ("INK_SACK".equals(material.name()) && item.getDurability() == 15) return true;
        return material.isEdible() || material.isBlock() || name.contains("BUCKET") || name.contains("POTION")
                || name.endsWith("BOW") || name.endsWith("_SEEDS") || name.endsWith("SPAWN_EGG")
                || Arrays.asList("SHIELD", "TRIDENT", "SPYGLASS", "GOAT_HORN", "FISHING_ROD", "LEAD", "SHEARS",
                "FLINT_AND_STEEL", "FIRE_CHARGE", "FIREWORK_ROCKET", "ENDER_PEARL", "EGG", "SNOWBALL", "BONE_MEAL").contains(name);
    }

    public static void validate(FileConfiguration config) {
        for (String path : Arrays.asList("enabled", "interaction.preserve-entity-use", "interaction.preserve-block-use",
                "interaction.preserve-main-hand-use", "swing.air", "attack.protect-npcs", "attack.cooldown", "mine.sounds")) bool(config, path);
        for (String action : Arrays.asList("attack", "mine")) {
            ConfigurationSection section = config.getConfigurationSection(action);
            if (section == null) throw new IllegalArgumentException("Missing section: " + action);
            for (String name : Arrays.asList("enabled", "allow-empty-hand", "standing", "sneaking", "correct-tool-only", "cancel-interaction"))
                bool(config, action + "." + name);
            number(config, action + ".reach", 0.5, 6.0);
            String mode = section.getString("materials.mode", "BLACKLIST");
            if (!"BLACKLIST".equals(mode) && !"WHITELIST".equals(mode))
                throw new IllegalArgumentException(action + ".materials.mode must be BLACKLIST or WHITELIST");
            if (!section.isList("game-modes")) throw new IllegalArgumentException(action + ".game-modes must be a list");
            for (String value : section.getStringList("game-modes")) {
                try { GameMode.valueOf(value); }
                catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid game mode: " + value); }
            }
            if (!section.isList("materials.patterns")) throw new IllegalArgumentException(action + ".materials.patterns must be a list");
            FILTERS.put(section, compile(section));
        }
        number(config, "mine.input-timeout-ticks", 2, 40);
        if (config.getDouble("mine.input-timeout-ticks") != config.getInt("mine.input-timeout-ticks"))
            throw new IllegalArgumentException("mine.input-timeout-ticks must be an integer");
        if (!config.isList("disabled-worlds")) throw new IllegalArgumentException("disabled-worlds must be a list");
    }

    private static void bool(FileConfiguration config, String path) {
        if (config.contains(path) && !(config.get(path) instanceof Boolean))
            throw new IllegalArgumentException(path + " must be true or false");
    }

    private static List<Pattern> compile(ConfigurationSection section) {
        List<Pattern> result = new ArrayList<>();
        for (String text : section.getStringList("materials.patterns")) {
            try { result.add(Pattern.compile(text)); }
            catch (PatternSyntaxException bad) { throw new IllegalArgumentException(section.getCurrentPath() + ": invalid material pattern " + text, bad); }
        }
        return result;
    }

    private static void number(FileConfiguration config, String path, double min, double max) {
        Object raw = config.get(path);
        if (!(raw instanceof Number)) throw new IllegalArgumentException(path + " must be numeric");
        double value = ((Number) raw).doubleValue();
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(path + " must be between " + min + " and " + max);
    }
}
