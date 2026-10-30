package me.mraxetv.beastdoublewield.compat;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Capability-based Bukkit boundary. Public signatures only use API types present
 * in 1.9; later API references are isolated in the lazily loaded Modern class.
 * Legacy actions still enter the server's native attack/break code, never a
 * hand-written damage, drop, or world-mutation substitute.
 */
public final class ServerAccess {
    private static final java.util.regex.Pattern CRAFT_VERSION = java.util.regex.Pattern.compile("^org\\.bukkit\\.craftbukkit\\.v1_(\\d+)_R\\d+\\.");
    private static final boolean ATTACK = hasMethod(LivingEntity.class, "attack", Entity.class);
    private static final boolean BREAK = hasMethod(Player.class, "breakBlock", Block.class);
    private static final boolean SPEED = hasMethod(Block.class, "getBreakSpeed", Player.class);
    private static final boolean TOOL = hasMethod(Block.class, "isPreferredTool", ItemStack.class);
    private static final boolean SWING = hasMethod(LivingEntity.class, "swingOffHand");
    private static final boolean CRACK = hasMethod(Player.class, "sendBlockDamage", Location.class, float.class);
    private static final boolean DATA = hasMethod(Block.class, "getBlockData");
    private static final boolean BOUNDS = hasMethod(Entity.class, "getBoundingBox");
    private static final boolean INTERACTION = hasMethod(Material.class, "isInteractable");
    private static final boolean ATTRIBUTES = hasMethod(Material.class, "getDefaultAttributeModifiers", EquipmentSlot.class);
    private static final boolean SOUND = classExists("org.bukkit.SoundGroup");
    private static final Set<String> LEGACY_INTERACTIVE = new HashSet<>(Arrays.asList(
            "CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "WORKBENCH", "FURNACE", "BURNING_FURNACE", "ANVIL",
            "BREWING_STAND", "ENCHANTMENT_TABLE", "BEACON", "HOPPER", "DISPENSER", "DROPPER", "LEVER",
            "NOTE_BLOCK", "JUKEBOX", "CAKE_BLOCK", "BED_BLOCK", "FENCE_GATE", "TRAP_DOOR", "IRON_TRAPDOOR",
            "WOODEN_DOOR", "IRON_DOOR_BLOCK", "DIODE_BLOCK_OFF", "DIODE_BLOCK_ON", "REDSTONE_COMPARATOR_OFF",
            "REDSTONE_COMPARATOR_ON", "COMMAND", "COMMAND_REPEATING", "COMMAND_CHAIN", "STRUCTURE_BLOCK"));

    private ServerAccess() { }

    public static void attack(Player player, Entity victim) {
        // The 1.16 API exposed LivingEntity.attack but its Craft implementation
        // called the mob attack path even for players, producing no player hit.
        // Use the real EntityHuman operation on that entire versioned generation.
        boolean legacyPlayer = false;
        for (Class<?> type = player.getClass(); type != null; type = type.getSuperclass())
            if (requiresNativePlayerAttack(type.getName())) { legacyPlayer = true; break; }
        if (ATTACK && !legacyPlayer) Modern.attack(player, victim); else LegacyNative.attack(player, victim);
    }
    public static boolean breakBlock(Player player, Block block) {
        return BREAK ? Modern.breakBlock(player, block) : LegacyNative.breakBlock(player, block);
    }
    public static float breakSpeed(Player player, Block block) {
        return SPEED ? Modern.breakSpeed(player, block) : LegacyNative.breakSpeed(player, block);
    }
    public static boolean preferredTool(Block block, ItemStack tool) {
        // Before 1.11, CraftItemStack.asNMSCopy(AIR) returns null rather than an
        // empty native stack. An empty hand is not a specialised mining tool.
        if (tool == null || air(tool.getType()) || tool.getAmount() < 1) return false;
        return TOOL ? Modern.preferredTool(block, tool) : LegacyNative.preferredTool(block, tool);
    }
    public static void swing(Player player) {
        if (SWING) Modern.swing(player); else LegacyNative.swing(player);
    }
    public static void crack(Player player, Block block, float progress) {
        if (CRACK) Modern.crack(player, block, progress); else LegacyNative.crack(player, block, progress);
    }
    public static void hitSound(Player player, Block block) {
        if (SOUND) Modern.hitSound(player, block); else LegacyNative.hitSound(player, block);
    }
    @SuppressWarnings("deprecation")
    public static String blockKey(Block block) {
        return DATA ? Modern.blockKey(block) : block.getType().name() + ":" + block.getData();
    }
    public static boolean air(Material material) {
        String name = material.name();
        return "AIR".equals(name) || "CAVE_AIR".equals(name) || "VOID_AIR".equals(name) || "LEGACY_AIR".equals(name);
    }
    /** Stable user-facing names for legacy aliases relevant to offhand input. */
    public static String materialName(Material material) {
        String name = material.name();
        if (name.startsWith("LEGACY_")) name = name.substring(7);
        switch (name) {
            case "LEASH": return "LEAD";
            case "MONSTER_EGG": return "SPAWN_EGG";
            case "FIREWORK": return "FIREWORK_ROCKET";
            case "FIREBALL": return "FIRE_CHARGE";
            case "SEEDS": return "WHEAT_SEEDS";
            case "EXP_BOTTLE": return "EXPERIENCE_BOTTLE";
            default:
                if (name.endsWith("_SPADE")) name = name.substring(0, name.length() - 6) + "_SHOVEL";
                if (name.startsWith("WOOD_")) name = "WOODEN_" + name.substring(5);
                if (name.startsWith("GOLD_")) name = "GOLDEN_" + name.substring(5);
                return name;
        }
    }
    public static boolean interactable(Material material) {
        if (INTERACTION) return Modern.interactable(material);
        String name = material.name();
        return LEGACY_INTERACTIVE.contains(name) || name.endsWith("_DOOR") || name.endsWith("_FENCE_GATE")
                || name.endsWith("_BUTTON") || name.endsWith("_SHULKER_BOX");
    }
    public static Collection<AttributeModifier> modifiers(ItemStack item, EquipmentSlot slot, Attribute attribute) {
        if (item == null || air(item.getType())) return Collections.emptyList();
        return ATTRIBUTES ? Modern.modifiers(item, slot, attribute) : LegacyNative.modifiers(item, slot, attribute);
    }
    public static Block targetBlock(Player player, double reach) {
        return BOUNDS ? Modern.targetBlock(player, reach) : LegacyNative.ray(player, reach).block;
    }
    public static Entity targetEntity(Player player, double reach) {
        if (BOUNDS) return Modern.targetEntity(player, reach);
        LegacyNative.Ray obstruction = LegacyNative.ray(player, reach);
        double nearest = Math.min(reach, obstruction.distance);
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector(), direction = eye.getDirection();
        Entity selected = null;
        for (Entity candidate : player.getNearbyEntities(reach, reach, reach)) {
            if (candidate.equals(player) || !candidate.isValid()) continue;
            double hit = rayIntersection(origin, direction, LegacyNative.bounds(candidate), nearest, 0.05);
            if (hit >= 0 && hit < nearest && player.hasLineOfSight(candidate)) { nearest = hit; selected = candidate; }
        }
        return selected;
    }
    public static double distanceSquared(Player player, Entity target) {
        return distanceSquared(player.getEyeLocation().toVector(), BOUNDS ? Modern.bounds(target) : LegacyNative.bounds(target));
    }
    static double distanceSquared(Vector point, double[] box) {
        double x = Math.max(box[0], Math.min(point.getX(), box[3]));
        double y = Math.max(box[1], Math.min(point.getY(), box[4]));
        double z = Math.max(box[2], Math.min(point.getZ(), box[5]));
        return point.distanceSquared(new Vector(x, y, z));
    }
    /** Slab intersection, returning distance along the normalized ray or -1. */
    static double rayIntersection(Vector origin, Vector direction, double[] box, double limit, double expand) {
        double lower = 0, upper = limit;
        double[] start = {origin.getX(), origin.getY(), origin.getZ()};
        double[] ray = {direction.getX(), direction.getY(), direction.getZ()};
        for (int axis = 0; axis < 3; axis++) {
            double minimum = box[axis] - expand, maximum = box[axis + 3] + expand;
            if (Math.abs(ray[axis]) < 1.0E-10) { if (start[axis] < minimum || start[axis] > maximum) return -1; continue; }
            double a = (minimum - start[axis]) / ray[axis], b = (maximum - start[axis]) / ray[axis];
            lower = Math.max(lower, Math.min(a, b)); upper = Math.min(upper, Math.max(a, b));
            if (upper < lower) return -1;
        }
        return lower;
    }
    public static String backendSummary() {
        boolean nativeAttack = !ATTACK || (Bukkit.getServer() != null && requiresNativePlayerAttack(Bukkit.getServer().getClass().getName()));
        return "attack=" + (nativeAttack ? "native legacy" : "Bukkit") + ", break=" + (BREAK ? "Bukkit" : "native legacy")
                + ", mining speed=" + (SPEED ? "Bukkit" : "native legacy") + ", geometry=" + (BOUNDS ? "Bukkit" : "native legacy");
    }
    static boolean requiresNativePlayerAttack(String className) {
        java.util.regex.Matcher version = CRAFT_VERSION.matcher(className);
        if (!version.find()) return false;
        int minor = Integer.parseInt(version.group(1));
        return minor >= 9 && minor <= 16;
    }
    private static boolean hasMethod(Class<?> owner, String name, Class<?>... parameters) {
        try { owner.getMethod(name, parameters); return true; } catch (NoSuchMethodException missing) { return false; }
    }
    private static boolean classExists(String name) {
        try { Class.forName(name, false, ServerAccess.class.getClassLoader()); return true; } catch (ClassNotFoundException missing) { return false; }
    }

    /** No legacy caller resolves the modern-only parameter/return classes here. */
    private static final class Modern {
        static void attack(Player player, Entity target) { player.attack(target); }
        static boolean breakBlock(Player player, Block block) { return player.breakBlock(block); }
        static float breakSpeed(Player player, Block block) { return block.getBreakSpeed(player); }
        static boolean preferredTool(Block block, ItemStack tool) { return block.isPreferredTool(tool); }
        static void swing(Player player) { player.swingOffHand(); }
        static void crack(Player player, Block block, float progress) { player.sendBlockDamage(block.getLocation(), progress); }
        static String blockKey(Block block) { return block.getBlockData().getAsString(); }
        static boolean interactable(Material material) { return material.isInteractable(); }
        static void hitSound(Player player, Block block) {
            org.bukkit.SoundGroup group = block.getBlockData().getSoundGroup();
            player.playSound(block.getLocation(), group.getHitSound(), group.getVolume() * 0.25F, group.getPitch());
        }
        static Block targetBlock(Player player, double reach) {
            org.bukkit.util.RayTraceResult result = player.rayTraceBlocks(reach, org.bukkit.FluidCollisionMode.NEVER);
            return result == null ? null : result.getHitBlock();
        }
        static Entity targetEntity(Player player, double reach) {
            org.bukkit.util.RayTraceResult result = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                    reach, org.bukkit.FluidCollisionMode.NEVER, true, 0.05, entity -> !entity.equals(player) && entity.isValid());
            return result == null ? null : result.getHitEntity();
        }
        static double[] bounds(Entity entity) {
            org.bukkit.util.BoundingBox box = entity.getBoundingBox();
            return new double[]{box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ()};
        }
        static Collection<AttributeModifier> modifiers(ItemStack item, EquipmentSlot slot, Attribute attribute) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            com.google.common.collect.Multimap<Attribute, AttributeModifier> values = meta != null && meta.hasAttributeModifiers()
                    ? meta.getAttributeModifiers(slot) : item.getType().getDefaultAttributeModifiers(slot);
            return values == null ? Collections.emptyList() : values.get(attribute);
        }
    }
}
