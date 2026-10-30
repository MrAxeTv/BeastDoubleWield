package me.mraxetv.beastdoublewield.util;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemRulesTest {
    private YamlConfiguration defaults() throws Exception {
        YamlConfiguration result = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) { result.load(reader); }
        return result;
    }
    @Test void shippedConfigIsValid() throws Exception { ItemRules.validate(defaults()); }
    @Test void rejectsBadRegexModeRangesAndBoolean() throws Exception {
        for (String path : Arrays.asList("attack.materials.patterns", "attack.materials.mode", "attack.reach", "enabled")) {
            YamlConfiguration config = defaults();
            config.set(path, path.endsWith("patterns") ? Arrays.asList("[") : path.endsWith("reach") ? Double.NaN : "invalid");
            assertThrows(IllegalArgumentException.class, () -> ItemRules.validate(config), path);
        }
    }
    @Test void materialRulesAndStanceCompose() throws Exception {
        YamlConfiguration config = defaults();
        Player player = mock(Player.class); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        ItemStack item = mock(ItemStack.class); when(item.getAmount()).thenReturn(1); when(item.getType()).thenReturn(Material.SHIELD);
        assertFalse(ItemRules.canUse(player, item, config.getConfigurationSection("attack")));
        when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
        assertTrue(ItemRules.canUse(player, item, config.getConfigurationSection("attack")));
        config.set("attack.standing", false);
        assertFalse(ItemRules.canUse(player, item, config.getConfigurationSection("attack")));
        when(player.isSneaking()).thenReturn(true);
        assertTrue(ItemRules.canUse(player, item, config.getConfigurationSection("attack")));
    }
    @Test void whitelistRejectsItemsNotExplicitlyIncluded() throws Exception {
        YamlConfiguration config = defaults(); config.set("attack.materials.mode", "WHITELIST"); config.set("attack.materials.patterns", Arrays.asList(".*_SWORD"));
        Player player = mock(Player.class); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        ItemStack item = mock(ItemStack.class); when(item.getAmount()).thenReturn(1); when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
        assertTrue(ItemRules.canUse(player, item, config.getConfigurationSection("attack")));
        when(item.getType()).thenReturn(Material.DIAMOND_AXE);
        assertFalse(ItemRules.canUse(player, item, config.getConfigurationSection("attack")));
    }
}
