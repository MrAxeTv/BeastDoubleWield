package me.mraxetv.beastdoublewield.mining;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import me.mraxetv.beastdoublewield.util.HandAccess;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MiningAttributesTest {
    private static final Attribute EFFICIENCY = Attribute.GENERIC_LUCK;
    private static final Attribute BREAK_SPEED = Attribute.GENERIC_MOVEMENT_SPEED;
    private static final Attribute SUBMERGED_SPEED = Attribute.GENERIC_FLYING_SPEED;
    private static final String EFFICIENCY_KEY = "minecraft:enchantment.efficiency/mainhand";

    @Test void mainEfficiencyVDoesNotLeakIntoPlainOffhand() throws Exception {
        Fixture f = new Fixture(5, 0);
        f.efficiency.put(modifier(EFFICIENCY_KEY, 26));
        AtomicReference<Material> acting = new AtomicReference<>();
        double observed = f.run(() -> {
            acting.set(f.inventory.getItemInMainHand().getType());
            return f.efficiency.instance.getValue();
        });
        assertEquals(Material.IRON_PICKAXE, acting.get());
        assertEquals(0, observed, "An unenchanted offhand must not inherit cached main-hand Efficiency V");
        assertEquals(26, f.efficiency.instance.getValue());
        f.assertHandsRestored();
    }

    @Test void offhandEfficiencyVContributesWithoutMainHandEnchantment() throws Exception {
        Fixture f = new Fixture(0, 5);
        assertEquals(26, f.run(() -> f.efficiency.instance.getValue()));
        assertEquals(0, f.efficiency.instance.getValue());
        f.assertHandsRestored();
    }

    @Test void differentEfficiencyLevelsPreserveUnrelatedNamespacedBuffs() throws Exception {
        Fixture f = new Fixture(5, 3);
        f.efficiency.put(modifier(EFFICIENCY_KEY, 26));
        f.efficiency.put(modifier("other:enchantment.efficiency/mainhand", 4));
        assertEquals(14, f.run(() -> f.efficiency.instance.getValue()));
        assertEquals(30, f.efficiency.instance.getValue());
    }

    @Test void allMiningAttributesFollowLogicalHandsAndKeepArmorModifiers() throws Exception {
        Fixture f = new Fixture(0, 0);
        f.main.add(EquipmentSlot.HAND, EFFICIENCY, modifier("test:main_eff", 3, EquipmentSlot.HAND));
        f.off.add(EquipmentSlot.HAND, EFFICIENCY, modifier("test:off_eff", 7, EquipmentSlot.HAND));
        f.efficiency.put(modifier("test:main_eff", 3));
        f.efficiency.put(modifier("test:armor_eff", 2));
        f.main.add(EquipmentSlot.HAND, BREAK_SPEED, modifier("test:main_break", 2, EquipmentSlot.HAND));
        f.main.add(EquipmentSlot.OFF_HAND, BREAK_SPEED, modifier("test:other_hand_break", .3, EquipmentSlot.OFF_HAND));
        f.off.add(EquipmentSlot.HAND, BREAK_SPEED, modifier("test:off_break", 4, EquipmentSlot.HAND));
        f.breakSpeed.put(modifier("test:main_break", 2));
        f.breakSpeed.put(new KeyedModifier(key("test:armor_break"), .2, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        f.main.add(EquipmentSlot.HAND, SUBMERGED_SPEED, modifier("test:main_water", .1, EquipmentSlot.HAND));
        f.off.add(EquipmentSlot.HAND, SUBMERGED_SPEED, modifier("test:off_water", .4, EquipmentSlot.HAND));
        f.submerged.put(modifier("test:main_water", .1));
        f.submerged.put(modifier("test:armor_water", .05));
        f.run(() -> {
            assertEquals(9, f.efficiency.instance.getValue());
            assertEquals(6.36, f.breakSpeed.instance.getValue(), 1.0e-10);
            assertEquals(.65, f.submerged.instance.getValue(), 1.0e-10);
            return null;
        });
        assertEquals(5, f.efficiency.instance.getValue());
        assertEquals(3.6, f.breakSpeed.instance.getValue(), 1.0e-10);
        assertEquals(.35, f.submerged.instance.getValue(), 1.0e-10);
        assertFalse(f.efficiency.modifiers.containsKey(key("test:off_eff")));
        f.assertHandsRestored();
    }

    @Test void actionFailureRestoresBothHandsAndAttributesButKeepsNewExternalBuff() throws Exception {
        Fixture f = new Fixture(5, 3);
        f.efficiency.put(modifier(EFFICIENCY_KEY, 26));
        IllegalStateException expected = new IllegalStateException("native mining failed");
        assertSame(expected, assertThrows(IllegalStateException.class, () -> f.run(() -> {
            assertEquals(10, f.efficiency.instance.getValue());
            f.efficiency.put(modifier("other:new_buff", 4));
            throw expected;
        })));
        assertEquals(30, f.efficiency.instance.getValue());
        f.assertHandsRestored();
    }

    @Test void cleanupPreservesAnotherListenersReplacementUnderSameKey() throws Exception {
        Fixture f = new Fixture(5, 3);
        f.efficiency.put(modifier(EFFICIENCY_KEY, 26));
        f.run(() -> {
            f.efficiency.put(modifier(EFFICIENCY_KEY, 77));
            return null;
        });
        assertEquals(77, f.efficiency.instance.getValue());
    }

    @Test void olderServersWithoutMiningAttributesRunWithoutInventoryMutation() {
        Player player = mock(Player.class);
        MiningAttributes.Bindings absent = new MiningAttributes.Bindings(null, null, null, new MiningAttributes.ModifierKeys(null, null));
        assertEquals("ok", MiningAttributes.withOffhand(player, absent, () -> "ok"));
        verifyNoInteractions(player);
    }

    @Test void vanillaModifierCleanupDoesNotDependOnEquipmentSlotRoundTrip() throws Exception {
        Fixture f = new Fixture(0, 0);
        f.off.add(EquipmentSlot.HAND, BREAK_SPEED, modifier("test:off_speed", 4, EquipmentSlot.HAND));
        assertEquals(5, f.run(() -> f.breakSpeed.instance.getValue()));
        assertEquals(1, f.breakSpeed.instance.getValue());
        assertTrue(f.breakSpeed.modifiers.isEmpty(), "Native attributes return ANY slot; the scoped HAND modifier must still be removed");
    }

    private static final class Fixture {
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final Tool main, off;
        final AtomicReference<ItemStack> selected = new AtomicReference<>(), offhand = new AtomicReference<>();
        final State efficiency = new State(0), breakSpeed = new State(1), submerged = new State(.2);
        final MiningAttributes.Bindings bindings;
        Fixture(int mainEfficiency, int offEfficiency) throws Exception {
            main = new Tool(Material.DIAMOND_PICKAXE, mainEfficiency);
            off = new Tool(Material.IRON_PICKAXE, offEfficiency);
            selected.set(main.item); offhand.set(off.item);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getInventory()).thenReturn(inventory);
            when(player.getAttribute(EFFICIENCY)).thenReturn(efficiency.instance);
            when(player.getAttribute(BREAK_SPEED)).thenReturn(breakSpeed.instance);
            when(player.getAttribute(SUBMERGED_SPEED)).thenReturn(submerged.instance);
            when(inventory.getHeldItemSlot()).thenReturn(0);
            when(inventory.getItem(0)).thenAnswer(call -> selected.get());
            when(inventory.getItemInMainHand()).thenAnswer(call -> selected.get());
            when(inventory.getItemInOffHand()).thenAnswer(call -> offhand.get());
            doAnswer(call -> { selected.set(call.getArgument(1)); return null; }).when(inventory).setItem(eq(0), any());
            doAnswer(call -> { offhand.set(call.getArgument(0)); return null; }).when(inventory).setItemInOffHand(any());
            bindings = new MiningAttributes.Bindings(EFFICIENCY, BREAK_SPEED, SUBMERGED_SPEED,
                    new MiningAttributes.ModifierKeys(KeyedModifier.class.getMethod("getKey"),
                            KeyedModifier.class.getConstructor(NamespacedKey.class, double.class, AttributeModifier.Operation.class)));
        }
        <T> T run(Supplier<T> action) {
            return MiningAttributes.withOffhand(player, bindings, () -> HandAccess.withOffhand(player, action));
        }
        void assertHandsRestored() { assertSame(main.item, selected.get()); assertSame(off.item, offhand.get()); }
    }

    private static final class Tool {
        final ItemStack item = mock(ItemStack.class);
        final Map<EquipmentSlot, Multimap<Attribute, AttributeModifier>> modifiers = new EnumMap<>(EquipmentSlot.class);
        Tool(Material type, int efficiency) {
            ItemMeta meta = mock(ItemMeta.class);
            when(item.getType()).thenReturn(type); when(item.getAmount()).thenReturn(1);
            when(item.getItemMeta()).thenReturn(meta); when(meta.hasAttributeModifiers()).thenReturn(true);
            when(meta.getAttributeModifiers(any(EquipmentSlot.class))).thenAnswer(call -> modifiers.get(call.getArgument(0)));
            when(item.getEnchantmentLevel(any(Enchantment.class))).thenReturn(efficiency);
        }
        void add(EquipmentSlot slot, Attribute attribute, AttributeModifier modifier) {
            modifiers.computeIfAbsent(slot, ignored -> ArrayListMultimap.create()).put(attribute, modifier);
        }
    }

    private static final class State {
        final AttributeInstance instance = mock(AttributeInstance.class);
        final Map<NamespacedKey, AttributeModifier> modifiers = new LinkedHashMap<>();
        State(double base) {
            when(instance.getModifiers()).thenAnswer(call -> new ArrayList<>(modifiers.values()));
            doAnswer(call -> { put(call.getArgument(0)); return null; }).when(instance).addModifier(any());
            doAnswer(call -> { modifiers.remove(((KeyedModifier) call.getArgument(0)).getKey()); return null; }).when(instance).removeModifier(any());
            when(instance.getValue()).thenAnswer(call -> {
                double value = base;
                for (AttributeModifier m : modifiers.values()) if (m.getOperation() == AttributeModifier.Operation.ADD_NUMBER) value += m.getAmount();
                double additive = value;
                for (AttributeModifier m : modifiers.values()) if (m.getOperation() == AttributeModifier.Operation.ADD_SCALAR) value += additive * m.getAmount();
                for (AttributeModifier m : modifiers.values()) if (m.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) value *= 1 + m.getAmount();
                return value;
            });
        }
        void put(AttributeModifier modifier) {
            // CraftAttributeInstance snapshots erase slot assignment (ANY).
            KeyedModifier keyed = (KeyedModifier) modifier;
            modifiers.put(keyed.getKey(), new KeyedModifier(keyed.getKey(), keyed.getAmount(), keyed.getOperation()));
        }
    }

    public static final class KeyedModifier extends AttributeModifier {
        private final NamespacedKey key;
        public KeyedModifier(NamespacedKey key, double amount, Operation operation) { this(key, amount, operation, null); }
        KeyedModifier(NamespacedKey key, double amount, Operation operation, EquipmentSlot slot) {
            super(UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)), key.toString(), amount, operation, slot);
            this.key = key;
        }
        public NamespacedKey getKey() { return key; }
    }
    private static KeyedModifier modifier(String key, double value) { return modifier(key, value, null); }
    private static KeyedModifier modifier(String key, double value, EquipmentSlot slot) {
        return new KeyedModifier(key(key), value, AttributeModifier.Operation.ADD_NUMBER, slot);
    }
    private static NamespacedKey key(String key) { return NamespacedKey.fromString(key); }
}
