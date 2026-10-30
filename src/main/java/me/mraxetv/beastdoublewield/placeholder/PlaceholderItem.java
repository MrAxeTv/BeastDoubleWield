package me.mraxetv.beastdoublewield.placeholder;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** The invisible-pack carrier is an owned input aid, never a genuine inventory reward. */
public final class PlaceholderItem {
    public static final int MODEL_ID = 35021;
    private static final boolean SUPPORTED = supportedApi();

    private PlaceholderItem() { }

    public static boolean supported() { return SUPPORTED; }

    public static ItemStack create() {
        if (!SUPPORTED) throw new IllegalStateException("The optional placeholder requires Minecraft 1.14 or newer");
        return Modern.create();
    }

    public static boolean isOwned(ItemStack item) {
        return SUPPORTED && item != null && item.getType() == Material.FEATHER
                && item.getAmount() > 0 && item.hasItemMeta() && Modern.isOwned(item);
    }

    private static boolean supportedApi() {
        try {
            ItemMeta.class.getMethod("getPersistentDataContainer");
            ItemMeta.class.getMethod("setCustomModelData", Integer.class);
            return true;
        } catch (NoSuchMethodException | LinkageError unavailable) { return false; }
    }

    /** Do not resolve modern metadata types on the legacy core-plugin path. */
    private static final class Modern {
        private static final org.bukkit.NamespacedKey KEY = new org.bukkit.NamespacedKey("beastdoublewield", "input_placeholder");
        private static final java.lang.reflect.Method HIDE_TOOLTIP = hideTooltipMethod();

        static ItemStack create() {
            ItemStack item = new ItemStack(Material.FEATHER, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) throw new IllegalStateException("This server cannot create placeholder item metadata");
            meta.setDisplayName(" ");
            meta.setLore(null);
            meta.setCustomModelData(MODEL_ID);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            meta.getPersistentDataContainer().set(KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            if (HIDE_TOOLTIP != null) try { HIDE_TOOLTIP.invoke(meta, true); }
            catch (ReflectiveOperationException ignored) { /* Blank name remains the older-client fallback. */ }
            item.setItemMeta(meta);
            return item;
        }

        static boolean isOwned(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;
            org.bukkit.persistence.PersistentDataContainer data = meta.getPersistentDataContainer();
            return data.has(KEY, org.bukkit.persistence.PersistentDataType.BYTE)
                    && Byte.valueOf((byte) 1).equals(data.get(KEY, org.bukkit.persistence.PersistentDataType.BYTE));
        }

        private static java.lang.reflect.Method hideTooltipMethod() {
            try { return ItemMeta.class.getMethod("setHideTooltip", boolean.class); }
            catch (NoSuchMethodException unavailable) { return null; }
        }
    }
}
