package me.mraxetv.beastdoublewield.combat;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.bukkit.Material;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeaponAttributesTest {
    @Test void suppliesOffhandDamageAndSpeedThenRestoresEvenAfterFailure() {
        Player player = mock(Player.class);
        AttributeInstance damage = instance(player, Attribute.GENERIC_ATTACK_DAMAGE);
        AttributeInstance speed = instance(player, Attribute.GENERIC_ATTACK_SPEED);
        UUID damageId = UUID.randomUUID(), speedId = UUID.randomUUID();
        AttributeModifier swordDamage = new AttributeModifier(damageId, "weapon", 6, AttributeModifier.Operation.ADD_NUMBER);
        AttributeModifier axeDamage = new AttributeModifier(damageId, "weapon", 8, AttributeModifier.Operation.ADD_NUMBER);
        AttributeModifier swordSpeed = new AttributeModifier(speedId, "speed", -2.4, AttributeModifier.Operation.ADD_NUMBER);
        AttributeModifier axeSpeed = new AttributeModifier(speedId, "speed", -3.2, AttributeModifier.Operation.ADD_NUMBER);
        AttributeModifier strength = new AttributeModifier(UUID.randomUUID(), "potion", 3, AttributeModifier.Operation.ADD_NUMBER);
        damage.addModifier(swordDamage); damage.addModifier(strength); speed.addModifier(swordSpeed);
        ItemStack sword = weapon(swordDamage, swordSpeed), axe = weapon(axeDamage, axeSpeed);
        assertThrows(IllegalStateException.class, () -> WeaponAttributes.withOffhand(player, sword, axe, () -> {
            assertEquals(new HashSet<>(Arrays.asList(axeDamage, strength)), new HashSet<>(damage.getModifiers()));
            assertEquals(Collections.singleton(axeSpeed), new HashSet<>(speed.getModifiers()));
            throw new IllegalStateException("damage listener failed");
        }));
        assertEquals(new HashSet<>(Arrays.asList(swordDamage, strength)), new HashSet<>(damage.getModifiers()));
        assertEquals(Collections.singleton(swordSpeed), new HashSet<>(speed.getModifiers()));
    }

    private AttributeInstance instance(Player player, Attribute attribute) {
        AttributeInstance result = mock(AttributeInstance.class);
        List<AttributeModifier> values = new ArrayList<>();
        when(result.getModifiers()).thenAnswer(call -> new ArrayList<>(values));
        doAnswer(call -> { values.add(call.getArgument(0)); return null; }).when(result).addModifier(any());
        doAnswer(call -> { values.remove(call.getArgument(0)); return null; }).when(result).removeModifier(any());
        when(player.getAttribute(attribute)).thenReturn(result); return result;
    }
    private ItemStack weapon(AttributeModifier damage, AttributeModifier speed) {
        ItemStack item = mock(ItemStack.class); ItemMeta meta = mock(ItemMeta.class);
        when(item.getType()).thenReturn(Material.DIAMOND_AXE); when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasAttributeModifiers()).thenReturn(true);
        Multimap<Attribute, AttributeModifier> map = ArrayListMultimap.create();
        map.put(Attribute.GENERIC_ATTACK_DAMAGE, damage); map.put(Attribute.GENERIC_ATTACK_SPEED, speed);
        when(meta.getAttributeModifiers(EquipmentSlot.HAND)).thenReturn(map);
        return item;
    }
}
