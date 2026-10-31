package me.mraxetv.beastdoublewield.mining;

import me.mraxetv.beastdoublewield.compat.ServerAccess;
import me.mraxetv.beastdoublewield.placeholder.PlaceholderItem;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Scopes cached modern mining attributes around a synchronous hand transaction. */
public final class MiningAttributes {
    private static final String EFFICIENCY_KEY = "minecraft:enchantment.efficiency/mainhand";
    private static final Enchantment EFFICIENCY = efficiencyEnchantment();
    private static final Bindings BINDINGS = new Bindings(attribute("MINING_EFFICIENCY"),
            attribute("BLOCK_BREAK_SPEED"), attribute("SUBMERGED_MINING_SPEED"), ModifierKeys.find());

    private MiningAttributes() { }

    /** Invoke before HandAccess swaps the inventory; the action must finish synchronously. */
    public static <T> T withOffhand(Player player, Supplier<T> action) {
        return withOffhand(player, BINDINGS, action);
    }

    // Explicit bindings let the Java 8/Spigot-baseline tests model optional modern APIs.
    static <T> T withOffhand(Player player, Bindings bindings, Supplier<T> action) {
        if (bindings.efficiency == null && bindings.breakSpeed == null && bindings.submergedSpeed == null)
            return action.get();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = PlaceholderItem.logicalItem(player.getInventory().getItemInOffHand());
        List<Runnable> undo = new ArrayList<>();
        T result = null;
        Throwable failure = null;
        try {
            for (Attribute attribute : Arrays.asList(bindings.efficiency, bindings.breakSpeed, bindings.submergedSpeed)) {
                if (attribute == null) continue;
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) continue;
                Set<String> handIds = new HashSet<>();
                for (ItemStack item : Arrays.asList(main, off))
                    for (EquipmentSlot slot : Arrays.asList(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND))
                        for (AttributeModifier modifier : ServerAccess.modifiers(item, slot, attribute))
                            handIds.add(bindings.keys.identity(modifier));
                boolean efficiency = attribute == bindings.efficiency && bindings.keys.supported();
                for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                    if (handIds.contains(bindings.keys.identity(modifier))
                            || (efficiency && EFFICIENCY_KEY.equals(bindings.keys.key(modifier)))) {
                        instance.removeModifier(modifier);
                        undo.add(() -> {
                            if (find(instance, bindings.keys, bindings.keys.identity(modifier)) == null)
                                instance.addModifier(modifier);
                        });
                    }
                }
                List<AttributeModifier> replacements = new ArrayList<>(ServerAccess.modifiers(off, EquipmentSlot.HAND, attribute));
                replacements.addAll(ServerAccess.modifiers(main, EquipmentSlot.OFF_HAND, attribute));
                if (efficiency) {
                    int level = efficiencyLevel(off);
                    if (level > 0) replacements.add(bindings.keys.efficiency((double) level * level + 1.0));
                }
                for (AttributeModifier replacement : replacements) {
                    String id = bindings.keys.identity(replacement);
                    if (find(instance, bindings.keys, id) != null) continue;
                    instance.addModifier(replacement);
                    undo.add(() -> {
                        AttributeModifier current = find(instance, bindings.keys, id);
                        // AttributeInstance round-trips slot groups as ANY. Compare
                        // effective identity/value instead, preserving a listener's replacement.
                        if (current != null && current.getOperation() == replacement.getOperation()
                                && Double.compare(current.getAmount(), replacement.getAmount()) == 0)
                            instance.removeModifier(current);
                    });
                }
            }
            result = action.get();
        } catch (RuntimeException | Error error) {
            failure = error;
        } finally {
            for (int i = undo.size() - 1; i >= 0; i--) try { undo.get(i).run(); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw (RuntimeException) failure;
        return result;
    }

    private static AttributeModifier find(AttributeInstance instance, ModifierKeys keys, String identity) {
        for (AttributeModifier modifier : instance.getModifiers())
            if (identity.equals(keys.identity(modifier))) return modifier;
        return null;
    }

    private static Attribute attribute(String name) {
        for (String field : new String[]{name, "PLAYER_" + name}) try {
            return (Attribute) Attribute.class.getField(field).get(null);
        } catch (ReflectiveOperationException | LinkageError unavailable) { }
        return null;
    }

    private static int efficiencyLevel(ItemStack item) {
        if (item == null || item.getAmount() < 1 || item.getType() == null || ServerAccess.air(item.getType())) return 0;
        return EFFICIENCY == null ? 0 : item.getEnchantmentLevel(EFFICIENCY);
    }

    private static Enchantment efficiencyEnchantment() {
        for (String field : new String[]{"EFFICIENCY", "DIG_SPEED"}) try {
            return (Enchantment) Enchantment.class.getField(field).get(null);
        } catch (ReflectiveOperationException | LinkageError unavailable) { }
        return null;
    }

    static final class Bindings {
        final Attribute efficiency, breakSpeed, submergedSpeed;
        final ModifierKeys keys;
        Bindings(Attribute efficiency, Attribute breakSpeed, Attribute submergedSpeed, ModifierKeys keys) {
            this.efficiency = efficiency; this.breakSpeed = breakSpeed; this.submergedSpeed = submergedSpeed; this.keys = keys;
        }
    }

    static final class ModifierKeys {
        final Method getter;
        final Constructor<? extends AttributeModifier> constructor;
        ModifierKeys(Method getter, Constructor<? extends AttributeModifier> constructor) {
            this.getter = getter; this.constructor = constructor;
        }
        static ModifierKeys find() {
            try {
                return new ModifierKeys(AttributeModifier.class.getMethod("getKey"),
                        AttributeModifier.class.getConstructor(NamespacedKey.class, double.class, AttributeModifier.Operation.class));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return new ModifierKeys(null, null);
            }
        }
        boolean supported() { return getter != null && constructor != null; }
        String key(AttributeModifier modifier) {
            if (getter == null) return null;
            try { return String.valueOf(getter.invoke(modifier)); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
        }
        String identity(AttributeModifier modifier) {
            String key = key(modifier);
            return key == null ? "uuid:" + modifier.getUniqueId() : "key:" + key;
        }
        AttributeModifier efficiency(double amount) {
            try {
                return constructor.newInstance(NamespacedKey.minecraft("enchantment.efficiency/mainhand"), amount,
                        AttributeModifier.Operation.ADD_NUMBER);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not scope vanilla mining efficiency", failure);
            }
        }
    }
}
