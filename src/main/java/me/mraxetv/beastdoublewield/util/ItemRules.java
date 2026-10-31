package me.mraxetv.beastdoublewield.util;
import me.mraxetv.beastdoublewield.compat.ServerAccess;
import me.mraxetv.beastdoublewield.placeholder.PlaceholderItem;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
        if (PlaceholderItem.isArm(item)) item = new ItemStack(Material.AIR);
        else if (PlaceholderItem.isOwned(item)) return false;
        if (rules == null || !rules.getBoolean("Enabled", true)) return false;
        if (!rules.getBoolean(player.isSneaking() ? "Sneaking" : "Standing", true)) return false;
        if (!rules.getStringList("GameModes").contains(player.getGameMode().name())) return false;
        boolean empty = item == null || ServerAccess.air(item.getType()) || item.getAmount() < 1;
        if (empty && !rules.getBoolean("AllowEmptyHand", false)) return false;
        String material = empty ? "AIR" : ServerAccess.materialName(item.getType());
        String rawMaterial = empty ? "AIR" : item.getType().name();
        List<Pattern> patterns = FILTERS.get(rules);
        if (patterns == null) { patterns = compile(rules); FILTERS.put(rules, patterns); }
        boolean match = false;
        for (Pattern pattern : patterns) if (pattern.matcher(material).matches() || pattern.matcher(rawMaterial).matches()) { match = true; break; }
        if ("WHITELIST".equals(rules.getString("Materials.Mode", "BLACKLIST"))) return match;
        return !match;
    }

    public static boolean isWeapon(ItemStack item) {
        if (PlaceholderItem.isArm(item)) return false;
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

    /** Preserve a vanilla axe action only on a block it can actually transform. */
    public static boolean hasBlockUseAction(ItemStack item, Block block) {
        if (item == null || item.getAmount() < 1 || item.getType() == null || block == null
                || block.getType() == null || !ServerAccess.materialName(item.getType()).endsWith("_AXE")) return false;
        return AxeTargets.BLOCKS.contains(block.getType().name());
    }

    private static final class AxeTargets {
        private static final Set<String> BLOCKS = find();
        private static Set<String> find() {
            Set<String> available = new HashSet<>();
            for (Material material : Material.values()) available.add(material.name());
            Set<String> targets = new HashSet<>();
            for (String name : available) if (axeTarget(name, available)) targets.add(name);
            return Collections.unmodifiableSet(targets);
        }
    }

    // Resolve against this server's material vocabulary: no hard references to
    // post-1.20 blocks, and no stripping on versions without stripped variants.
    // Paper 26.3 has no public query for effective BLOCK_TRANSFORMER rules. This
    // recognizes vanilla axe targets; native code still owns the actual action.
    static boolean axeTarget(String name, Set<String> available) {
        if (name.startsWith("LEGACY_") || !available.contains(name)) return false;
        if (available.contains("STRIPPED_" + name)) return true;
        String bare = name.startsWith("WAXED_") ? name.substring(6) : name;
        if (!bare.contains("COPPER") && !bare.endsWith("LIGHTNING_ROD")) return false;
        if (!bare.equals(name)) return available.contains(bare);
        String previous;
        if (name.startsWith("OXIDIZED_")) previous = "WEATHERED_" + name.substring(9);
        else if (name.startsWith("WEATHERED_")) previous = "EXPOSED_" + name.substring(10);
        else if (name.startsWith("EXPOSED_")) {
            previous = name.substring(8);
            if (previous.equals("COPPER")) previous = "COPPER_BLOCK";
        } else return false;
        return available.contains(previous);
    }

    public static void validate(FileConfiguration config) {
        for (String path : Arrays.asList("Enabled", "Interaction.PreserveEntityUse", "Interaction.PreserveBlockUse",
                "Interaction.PreserveMainHandUse", "Swing.Air", "Attack.ProtectNpcs", "Attack.Cooldown", "Mine.Sounds")) bool(config, path);
        for (String action : Arrays.asList("Attack", "Mine")) {
            ConfigurationSection section = config.getConfigurationSection(action);
            if (section == null) throw new IllegalArgumentException("Missing section: " + action);
            for (String name : Arrays.asList("Enabled", "AllowEmptyHand", "Standing", "Sneaking", "CorrectToolOnly", "CancelInteraction"))
                bool(config, action + "." + name);
            number(config, action + ".Reach", 0.5, 6.0);
            String mode = section.getString("Materials.Mode", "BLACKLIST");
            if (!"BLACKLIST".equals(mode) && !"WHITELIST".equals(mode))
                throw new IllegalArgumentException(action + ".Materials.Mode must be BLACKLIST or WHITELIST");
            if (!section.isList("GameModes")) throw new IllegalArgumentException(action + ".GameModes must be a list");
            for (String value : section.getStringList("GameModes")) {
                try { GameMode.valueOf(value); }
                catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid game mode: " + value); }
            }
            if (!section.isList("Materials.Patterns")) throw new IllegalArgumentException(action + ".Materials.Patterns must be a list");
            FILTERS.put(section, compile(section));
        }
        number(config, "Mine.InputTimeoutTicks", 2, 40);
        if (config.getDouble("Mine.InputTimeoutTicks") != config.getInt("Mine.InputTimeoutTicks"))
            throw new IllegalArgumentException("Mine.InputTimeoutTicks must be an integer");
        if (config.contains("Mine.InputProgressTicks")) {
            number(config, "Mine.InputProgressTicks", 2, 40);
            if (config.getDouble("Mine.InputProgressTicks") != config.getInt("Mine.InputProgressTicks"))
                throw new IllegalArgumentException("Mine.InputProgressTicks must be an integer");
        }
        if (!config.isList("DisabledWorlds")) throw new IllegalArgumentException("DisabledWorlds must be a list");
    }

    private static void bool(FileConfiguration config, String path) {
        if (config.contains(path) && !(config.get(path) instanceof Boolean))
            throw new IllegalArgumentException(path + " must be true or false");
    }

    private static List<Pattern> compile(ConfigurationSection section) {
        List<Pattern> result = new ArrayList<>();
        for (String text : section.getStringList("Materials.Patterns")) {
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
