package me.mraxetv.beastdoublewield.combat;
import me.mraxetv.beastdoublewield.compat.ServerAccess;

import com.google.common.collect.Multimap;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Supplier;

/** Bukkit inventory changes do not immediately refresh native equipped attributes. */
final class WeaponAttributes {
    private WeaponAttributes() { }
    static <T> T withOffhand(Player player, ItemStack main, ItemStack off, Supplier<T> action) {
        List<Runnable> undo = new ArrayList<>();
        try {
            for (Attribute attribute : Arrays.asList(Attribute.GENERIC_ATTACK_DAMAGE, Attribute.GENERIC_ATTACK_SPEED)) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) continue;
                Set<UUID> weaponIds = new HashSet<>();
                for (ItemStack stack : Arrays.asList(main, off)) for (EquipmentSlot slot : Arrays.asList(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND))
                    for (AttributeModifier modifier : modifiers(stack, slot, attribute)) weaponIds.add(modifier.getUniqueId());
                for (AttributeModifier current : new ArrayList<>(instance.getModifiers())) {
                    if (weaponIds.contains(current.getUniqueId())) {
                        instance.removeModifier(current);
                        undo.add(() -> { if (!contains(instance, current.getUniqueId())) instance.addModifier(current); });
                    }
                }
                List<AttributeModifier> replacements = new ArrayList<>(modifiers(off, EquipmentSlot.HAND, attribute));
                replacements.addAll(modifiers(main, EquipmentSlot.OFF_HAND, attribute));
                for (AttributeModifier modifier : replacements) {
                    if (contains(instance, modifier.getUniqueId())) continue;
                    instance.addModifier(modifier);
                    undo.add(() -> {
                        for (AttributeModifier active : new ArrayList<>(instance.getModifiers()))
                            if (active.equals(modifier)) instance.removeModifier(active);
                    });
                }
            }
            return action.get();
        } finally { for (int i = undo.size() - 1; i >= 0; i--) undo.get(i).run(); }
    }

    private static boolean contains(AttributeInstance instance, UUID id) {
        for (AttributeModifier modifier : instance.getModifiers()) if (modifier.getUniqueId().equals(id)) return true;
        return false;
    }

    private static Collection<AttributeModifier> modifiers(ItemStack item, EquipmentSlot slot, Attribute attribute) {
        return ServerAccess.modifiers(item, slot, attribute);
    }
}
