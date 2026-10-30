package me.mraxetv.beastdoublewield.placeholder;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.mockito.MockedConstruction;
import java.net.URL;
import java.util.UUID;

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

    @Test void armHasIndependentOwnershipAndLogicalAirIdentity() {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        ItemStack head = feather(data);
        when(head.getType()).thenReturn(Material.PLAYER_HEAD);
        NamespacedKey armKey = new NamespacedKey("beastdoublewield", "first_person_arm");
        when(data.has(armKey, PersistentDataType.BYTE)).thenReturn(true);
        when(data.get(armKey, PersistentDataType.BYTE)).thenReturn((byte)1);
        assertTrue(PlaceholderItem.isArm(head)); assertTrue(PlaceholderItem.isOwned(head));
        assertEquals(Material.AIR, PlaceholderItem.logicalItem(head).getType());
        when(head.getType()).thenReturn(Material.DIAMOND);
        assertFalse(PlaceholderItem.isOwned(head));
    }

    @Test void versionGateDoesNotGuessOtherClientGenerations() {
        assertTrue(PlaceholderItem.armVersion("26.3-R0.1-SNAPSHOT"));
        assertTrue(PlaceholderItem.armVersion("26.3.build.188-stable"));
        assertTrue(PlaceholderItem.armVersion("26.3"));
        assertFalse(PlaceholderItem.armVersion("26.30-R0.1-SNAPSHOT"));
        assertFalse(PlaceholderItem.armVersion("26.3.build.bad"));
        assertFalse(PlaceholderItem.armVersion("26.2-R0.1-SNAPSHOT"));
        assertFalse(PlaceholderItem.armVersion("1.20.2-R0.1-SNAPSHOT"));
        assertFalse(PlaceholderItem.armVersion(null));
    }

    @Test void unavailableComponentsCannotResolveOrMutateExistingProfile() throws Exception {
        Player player = mock(Player.class);
        PlayerProfile source = mock(PlayerProfile.class), detached = mock(PlayerProfile.class);
        PlayerTextures textures = mock(PlayerTextures.class);
        SkullMeta meta = mock(SkullMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(player.getPlayerProfile()).thenReturn(source);
        when(source.getUniqueId()).thenReturn(UUID.randomUUID());
        when(source.clone()).thenReturn(detached);
        when(detached.getTextures()).thenReturn(textures);
        when(textures.getSkin()).thenReturn(new URL("https://textures.minecraft.net/texture/example"));
        when(meta.getPersistentDataContainer()).thenReturn(data);
        try (MockedConstruction<ItemStack> constructed = mockConstruction(ItemStack.class,
                (item, context) -> when(item.getItemMeta()).thenReturn(meta))) {
            when(textures.getSkinModel()).thenReturn(PlayerTextures.SkinModel.SLIM);
            PlaceholderItem.createArm(player);
            when(textures.getSkinModel()).thenReturn(PlayerTextures.SkinModel.CLASSIC);
            PlaceholderItem.createArm(player);
        }
        verify(source, never()).update(); verify(detached, never()).update();
        verify(source, never()).setTextures(any());
        verify(source, times(2)).clone();
    }

    @Test void missingSkinDoesNotCreateUnresolvedHeadOrLookup() {
        Player player = mock(Player.class);
        PlayerProfile source = mock(PlayerProfile.class);
        when(player.getPlayerProfile()).thenReturn(source);
        assertNull(PlaceholderItem.createArm(player));
        verify(source, never()).update();
        verify(source, never()).clone();
    }
}
