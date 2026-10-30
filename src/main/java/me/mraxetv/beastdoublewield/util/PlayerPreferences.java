package me.mraxetv.beastdoublewield.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/** UUID preferences work on every offhand-capable server, including pre-PDC releases. */
public final class PlayerPreferences {
    private final File file;
    private final Set<String> disabled = new HashSet<>();
    private final Set<String> known = new HashSet<>();
    private final Plugin plugin;
    public PlayerPreferences(Plugin plugin) throws Exception {
        this.plugin = plugin; file = new File(plugin.getDataFolder(), "players.yml");
        if (file.isFile()) {
            YamlConfiguration stored = new YamlConfiguration(); stored.load(file);
            disabled.addAll(stored.getStringList("disabled")); known.addAll(stored.getStringList("known"));
        }
    }
    public boolean enabled(Player player) {
        String id = player.getUniqueId().toString();
        if (known.add(id) && Boolean.FALSE.equals(oldPreference(player))) disabled.add(id);
        return !disabled.contains(id);
    }
    public void setEnabled(Player player, boolean enabled) throws IOException {
        String id = player.getUniqueId().toString();
        boolean previous = disabled.contains(id); known.add(id);
        if (enabled) disabled.remove(id); else disabled.add(id);
        try { save(); }
        catch (IOException failure) { if (previous) disabled.add(id); else disabled.remove(id); throw failure; }
    }
    public void save() throws IOException {
        YamlConfiguration stored = new YamlConfiguration();
        List<String> sorted = new ArrayList<>(disabled); Collections.sort(sorted); stored.set("disabled", sorted);
        sorted = new ArrayList<>(known); Collections.sort(sorted); stored.set("known", sorted);
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        stored.save(temporary);
        try { java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private Boolean oldPreference(Player player) {
        // Lazily migrate the first build's PDC setting without linking modern classes on 1.9.
        try {
            Class<?> keyClass = Class.forName("org.bukkit.NamespacedKey");
            Class<?> typeClass = Class.forName("org.bukkit.persistence.PersistentDataType");
            Object key = keyClass.getConstructor(Plugin.class, String.class).newInstance(plugin, "enabled");
            Object container = Player.class.getMethod("getPersistentDataContainer").invoke(player);
            if (container == null) return null;
            Method get = Class.forName("org.bukkit.persistence.PersistentDataContainer").getMethod("get", keyClass, typeClass);
            Object value = get.invoke(container, key, typeClass.getField("BYTE").get(null));
            return value == null ? null : ((Byte) value).byteValue() != 0;
        } catch (ReflectiveOperationException unavailable) { return null; }
    }
}
