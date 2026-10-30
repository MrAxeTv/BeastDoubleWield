package me.mraxetv.beastdoublewield.placeholder;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceholderItemTest {
    private final NamespacedKey marker = new NamespacedKey("beastdoublewield", "input_placeholder");

    private ItemStack feather(PersistentDataContainer data) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getType()).thenReturn(Material.FEATHER);
        when(item.getAmount()).thenReturn(1);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        return item;
    }

    @Test void modelAndDisplayNameNeverConferOwnershipWithoutPrivateMarker() {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        ItemStack ordinary = feather(data);
        when(ordinary.getItemMeta().getCustomModelData()).thenReturn(35021);
        when(ordinary.getItemMeta().getDisplayName()).thenReturn(" ");
        assertFalse(PlaceholderItem.isOwned(ordinary));
        verify(data, never()).get(any(), any());
        when(data.has(marker, PersistentDataType.BYTE)).thenReturn(true);
        when(data.get(marker, PersistentDataType.BYTE)).thenReturn((byte) 1);
        assertTrue(PlaceholderItem.isOwned(ordinary));
    }

    @Test void wrongTypeOrUnknownMarkerVersionIsNotAnOwnedItem() {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.has(marker, PersistentDataType.STRING)).thenReturn(true);
        when(data.get(marker, PersistentDataType.BYTE)).thenThrow(new IllegalArgumentException("wrong primitive type"));
        ItemStack malformed = feather(data);
        assertDoesNotThrow(() -> assertFalse(PlaceholderItem.isOwned(malformed)));
        verify(data, never()).get(marker, PersistentDataType.BYTE);
        when(data.has(marker, PersistentDataType.BYTE)).thenReturn(true);
        doReturn((byte) 2).when(data).get(marker, PersistentDataType.BYTE);
        assertFalse(PlaceholderItem.isOwned(malformed));
    }
}
