package me.mraxetv.beastdoublewield.util;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemRulesTest {
    private YamlConfiguration defaults() throws Exception {
        YamlConfiguration result = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) { result.load(reader); }
        return result;
    }
    @Test void shippedConfigIsValid() throws Exception { ItemRules.validate(defaults()); }
    @Test void miningProgressWindowIsOptionalButMustBeAnIntegerWithinBounds() throws Exception {
        YamlConfiguration config = defaults();
        config.set("Mine.InputProgressTicks", null);
        assertDoesNotThrow(() -> ItemRules.validate(config));
        for (Object invalid : Arrays.asList(1, 41, 3.5, "four", Double.NaN)) {
            config.set("Mine.InputProgressTicks", invalid);
            assertThrows(IllegalArgumentException.class, () -> ItemRules.validate(config));
        }
        config.set("Mine.InputProgressTicks", 4);
        assertDoesNotThrow(() -> ItemRules.validate(config));
    }
    @Test void rejectsBadRegexModeRangesAndBoolean() throws Exception {
        for (String path : Arrays.asList("Attack.Materials.Patterns", "Attack.Materials.Mode", "Attack.Reach", "Enabled")) {
            YamlConfiguration config = defaults();
            config.set(path, path.endsWith("Patterns") ? Arrays.asList("[") : path.endsWith("Reach") ? Double.NaN : "invalid");
            assertThrows(IllegalArgumentException.class, () -> ItemRules.validate(config), path);
        }
    }
    @Test void materialRulesAndStanceCompose() throws Exception {
        YamlConfiguration config = defaults();
        Player player = mock(Player.class); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        ItemStack item = mock(ItemStack.class); when(item.getAmount()).thenReturn(1); when(item.getType()).thenReturn(Material.SHIELD);
        assertFalse(ItemRules.canUse(player, item, config.getConfigurationSection("Attack")));
        when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
        assertTrue(ItemRules.canUse(player, item, config.getConfigurationSection("Attack")));
        config.set("Attack.Standing", false);
        assertFalse(ItemRules.canUse(player, item, config.getConfigurationSection("Attack")));
        when(player.isSneaking()).thenReturn(true);
        assertTrue(ItemRules.canUse(player, item, config.getConfigurationSection("Attack")));
    }
    @Test void whitelistRejectsItemsNotExplicitlyIncluded() throws Exception {
        YamlConfiguration config = defaults(); config.set("Attack.Materials.Mode", "WHITELIST"); config.set("Attack.Materials.Patterns", Arrays.asList(".*_SWORD"));
        Player player = mock(Player.class); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        ItemStack item = mock(ItemStack.class); when(item.getAmount()).thenReturn(1); when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
        assertTrue(ItemRules.canUse(player, item, config.getConfigurationSection("Attack")));
        when(item.getType()).thenReturn(Material.DIAMOND_AXE);
        assertFalse(ItemRules.canUse(player, item, config.getConfigurationSection("Attack")));
    }

    @Test void axeUseRequiresTransformableTargetAndNonemptyAxe() {
        Block block = mock(Block.class);
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        for (Material target : Arrays.asList(Material.OAK_LOG, Material.BIRCH_WOOD,
                Material.CRIMSON_STEM, Material.WARPED_HYPHAE, Material.BAMBOO_BLOCK,
                Material.EXPOSED_COPPER, Material.OXIDIZED_CUT_COPPER_STAIRS,
                Material.WAXED_COPPER_BLOCK, Material.WAXED_OXIDIZED_CUT_COPPER_SLAB)) {
            when(block.getType()).thenReturn(target);
            assertTrue(ItemRules.hasBlockUseAction(axe, block), target.name());
        }
        for (Material target : Arrays.asList(Material.STRIPPED_OAK_LOG, Material.STRIPPED_BAMBOO_BLOCK,
                Material.OAK_PLANKS, Material.OAK_LEAVES, Material.COPPER_BLOCK,
                Material.CUT_COPPER, Material.LIGHTNING_ROD, Material.STONE, Material.AIR)) {
            when(block.getType()).thenReturn(target);
            assertFalse(ItemRules.hasBlockUseAction(axe, block), target.name());
        }
        when(block.getType()).thenReturn(Material.OAK_LOG);
        for (ItemStack item : Arrays.asList(null, new ItemStack(Material.AIR),
                new ItemStack(Material.DIAMOND_AXE, 0), new ItemStack(Material.DIAMOND_PICKAXE),
                new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.DIAMOND_SHOVEL)))
            assertFalse(ItemRules.hasBlockUseAction(item, block));
        assertFalse(ItemRules.hasBlockUseAction(axe, null));
        assertFalse(ItemRules.hasUseAction(axe), "An axe must not block offhand air/entity input globally");
    }

    @Test void runtimeVocabularySupportsNewWoodAndOxidizingLightningRods() {
        Set<String> available = new HashSet<>(Arrays.asList("POPLAR_LOG", "STRIPPED_POPLAR_LOG",
                "POPLAR_WOOD", "STRIPPED_POPLAR_WOOD", "LIGHTNING_ROD", "EXPOSED_LIGHTNING_ROD",
                "WEATHERED_LIGHTNING_ROD", "OXIDIZED_LIGHTNING_ROD", "WAXED_LIGHTNING_ROD",
                "WAXED_OXIDIZED_LIGHTNING_ROD", "COPPER_CHEST", "EXPOSED_COPPER_CHEST",
                "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST", "WAXED_COPPER_CHEST"));
        for (String target : Arrays.asList("POPLAR_LOG", "POPLAR_WOOD", "EXPOSED_LIGHTNING_ROD",
                "WEATHERED_LIGHTNING_ROD", "OXIDIZED_LIGHTNING_ROD", "WAXED_LIGHTNING_ROD",
                "WAXED_OXIDIZED_LIGHTNING_ROD", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST",
                "OXIDIZED_COPPER_CHEST", "WAXED_COPPER_CHEST"))
            assertTrue(ItemRules.axeTarget(target, available), target);
        for (String target : Arrays.asList("LIGHTNING_ROD", "COPPER_CHEST", "STRIPPED_POPLAR_LOG"))
            assertFalse(ItemRules.axeTarget(target, available), target);
    }

    @Test void missingTransformationCounterpartsAndLegacyLogsDoNotClaimUse() {
        Set<String> available = new HashSet<>(Arrays.asList("LOG", "LOG_2", "LEGACY_LOG", "OAK_LOG",
                "OXIDIZED_COPPER", "EXPOSED_COPPER", "WAXED_COPPER_BLOCK", "WEATHERED_STONE", "EXPOSED_STONE"));
        for (String target : available) assertFalse(ItemRules.axeTarget(target, available), target);
        assertFalse(ItemRules.axeTarget("WAXED_LIGHTNING_ROD", available));
    }
}
