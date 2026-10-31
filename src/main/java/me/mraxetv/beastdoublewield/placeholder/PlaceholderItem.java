package me.mraxetv.beastdoublewield.placeholder;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** The invisible-pack carrier is an owned input aid, never a genuine inventory reward. */
public final class PlaceholderItem {
    public static final int MODEL_ID = 35021;
    public static final int ARM_CLASSIC_MODEL_ID = 35022;
    public static final int ARM_SLIM_MODEL_ID = 35023;
    private static final boolean SUPPORTED = supportedApi();

    private PlaceholderItem() { }

    public static boolean supported() { return SUPPORTED; }

    public static ItemStack create() {
        if (!SUPPORTED) throw new IllegalStateException("The optional placeholder requires Minecraft 1.14 or newer");
        return Modern.create();
    }

    public static boolean isOwned(ItemStack item) {
        return SUPPORTED && item != null && item.getAmount() > 0
                && ((item.getType() == Material.FEATHER && item.hasItemMeta() && Modern.isOwned(item)) || isArm(item));
    }

    /** The visual offhand carrier is logically AIR, unlike the main-hand input feather. */
    public static boolean isArm(ItemStack item) {
        return SUPPORTED && item != null && item.getType() != null
                && (item.getType() == Material.FEATHER || "PLAYER_HEAD".equals(item.getType().name()))
                && item.getAmount() > 0 && item.hasItemMeta() && Modern.isArm(item);
    }

    public static ItemStack logicalItem(ItemStack item) {
        return isArm(item) ? new ItemStack(Material.AIR) : item;
    }

    /** Whether native inventory quick-move would choose this item's offhand slot. */
    public static boolean isOffhandEquipment(ItemStack item) {
        return EquipmentSupport.LOOKUP.isOffhand(item);
    }

    /** Keep optional component discovery separate from skin/profile initialization. */
    private static final class EquipmentSupport {
        private static final EquipmentLookup LOOKUP = EquipmentLookup.find();
    }

    /** Uses effective components, including defaults and explicit removals. */
    static final class EquipmentLookup {
        private final Object equippableType;
        private final java.lang.reflect.Method getData, slot, defaultSlot;

        EquipmentLookup(Object equippableType, java.lang.reflect.Method getData,
                        java.lang.reflect.Method slot, java.lang.reflect.Method defaultSlot) {
            this.equippableType = equippableType;
            this.getData = getData;
            this.slot = slot;
            this.defaultSlot = defaultSlot;
        }

        static EquipmentLookup find() {
            java.lang.reflect.Method fallback = null;
            try { fallback = Material.class.getMethod("getEquipmentSlot"); }
            catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { }
            try {
                Class<?> valued = Class.forName("io.papermc.paper.datacomponent.DataComponentType$Valued");
                java.lang.reflect.Method getter = ItemStack.class.getMethod("getData", valued);
                Object type = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes").getField("EQUIPPABLE").get(null);
                java.lang.reflect.Method slot = Class.forName("io.papermc.paper.datacomponent.item.Equippable").getMethod("slot");
                return new EquipmentLookup(type, getter, slot, fallback);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                return new EquipmentLookup(null, null, null, fallback);
            }
        }

        boolean isOffhand(ItemStack item) {
            if (item == null || item.getAmount() < 1 || item.getType() == null || item.getType() == Material.AIR) return false;
            try {
                Object equipmentSlot;
                if (getData != null) {
                    Object component = getData.invoke(item, equippableType);
                    // Null is an effective absence/removal, not an unavailable API.
                    if (component == null) return false;
                    equipmentSlot = slot.invoke(component);
                } else {
                    if (defaultSlot == null) return false;
                    equipmentSlot = defaultSlot.invoke(item.getType());
                }
                return equipmentSlot instanceof Enum && "OFF_HAND".equals(((Enum<?>) equipmentSlot).name());
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                // A failed query cannot establish offhand intent for this stack.
                return false;
            }
        }
    }

    public static boolean armSupported(String version) {
        if (!SUPPORTED || !armVersion(version)) return false;
        try {
            org.bukkit.entity.Player.class.getMethod("getPlayerProfile");
            Class.forName("org.bukkit.inventory.meta.SkullMeta").getMethod("setOwnerProfile", Class.forName("org.bukkit.profile.PlayerProfile"));
            return Modern.ARM_COMPONENTS != null;
        } catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
    }

    static boolean armVersion(String version) {
        return version != null && version.matches("26\\.3(?:|-.*|\\.build\\.[0-9]+(?:-[A-Za-z0-9._-]+)?)");
    }

    /** Returns null when no complete skin is available; never starts a profile lookup. */
    public static ItemStack createArm(org.bukkit.entity.Player player) { return Modern.createArm(player); }

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
        private static final org.bukkit.NamespacedKey ARM_KEY = new org.bukkit.NamespacedKey("beastdoublewield", "first_person_arm");
        private static final java.lang.reflect.Method HIDE_TOOLTIP = hideTooltipMethod();
        private static final ArmComponents ARM_COMPONENTS = ArmComponents.find();

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

        static boolean isArm(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;
            org.bukkit.persistence.PersistentDataContainer data = meta.getPersistentDataContainer();
            return data.has(ARM_KEY, org.bukkit.persistence.PersistentDataType.BYTE)
                    && Byte.valueOf((byte) 1).equals(data.get(ARM_KEY, org.bukkit.persistence.PersistentDataType.BYTE));
        }

        static ItemStack createArm(org.bukkit.entity.Player player) {
            org.bukkit.profile.PlayerProfile original = player.getPlayerProfile();
            if (original == null || original.getUniqueId() == null) return null;
            org.bukkit.profile.PlayerProfile profile = original.clone();
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();
            if (textures == null || textures.getSkin() == null) return null;
            if (ARM_COMPONENTS == null) return null;
            // Build the resolved profile through supported Bukkit SkullMeta, then move
            // only PROFILE to a feather. The actual carrier cannot place/equip a skull.
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta headMeta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            if (headMeta == null) return null;
            headMeta.setOwnerProfile(profile);
            head.setItemMeta(headMeta);
            ItemStack item = new ItemStack(Material.FEATHER);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            meta.setDisplayName(" ");
            meta.setLore(null);
            meta.setCustomModelData(textures.getSkinModel() == org.bukkit.profile.PlayerTextures.SkinModel.SLIM
                    ? ARM_SLIM_MODEL_ID : ARM_CLASSIC_MODEL_ID);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            meta.getPersistentDataContainer().set(ARM_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            if (HIDE_TOOLTIP != null) try { HIDE_TOOLTIP.invoke(meta, true); }
            catch (ReflectiveOperationException ignored) { }
            item.setItemMeta(meta);
            return ARM_COMPONENTS.apply(head, item) ? item : null;
        }

        /** Optional public Paper component APIs, never an NMS field or method. */
        private static final class ArmComponents {
            private final Object profileType;
            private final java.lang.reflect.Method getData, setData, setModel;
            private ArmComponents(Object profileType, java.lang.reflect.Method getData,
                                  java.lang.reflect.Method setData, java.lang.reflect.Method setModel) {
                this.profileType = profileType; this.getData = getData; this.setData = setData; this.setModel = setModel;
            }
            static ArmComponents find() {
                try {
                    Class<?> valued = Class.forName("io.papermc.paper.datacomponent.DataComponentType$Valued");
                    Object profile = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes").getField("PROFILE").get(null);
                    return new ArmComponents(profile, ItemStack.class.getMethod("getData", valued),
                            ItemStack.class.getMethod("setData", valued, Object.class),
                            ItemMeta.class.getMethod("setItemModel", org.bukkit.NamespacedKey.class));
                } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { return null; }
            }
            boolean apply(ItemStack head, ItemStack feather) {
                try {
                    Object profile = getData.invoke(head, profileType);
                    if (profile == null) return false;
                    ItemMeta meta = feather.getItemMeta();
                    setModel.invoke(meta, org.bukkit.NamespacedKey.minecraft("player_head"));
                    feather.setItemMeta(meta);
                    setData.invoke(feather, profileType, profile);
                    return true;
                } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { return false; }
            }
        }

        private static java.lang.reflect.Method hideTooltipMethod() {
            try { return ItemMeta.class.getMethod("setHideTooltip", boolean.class); }
            catch (NoSuchMethodException unavailable) { return null; }
        }
    }
}
