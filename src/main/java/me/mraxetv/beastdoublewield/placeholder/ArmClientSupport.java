package me.mraxetv.beastdoublewield.placeholder;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.util.UUID;
import java.util.function.BiFunction;

/** A local translation API must positively identify the tested client protocol. */
final class ArmClientSupport {
    // Read from the official installed 26.3 client's version.json.
    static final int PROTOCOL_26_3 = 777;
    private final BeastDoubleWieldPlugin owner;
    private final BiFunction<Plugin, UUID, Integer> query;

    ArmClientSupport(BeastDoubleWieldPlugin owner) { this(owner, ArmClientSupport::viaProtocol); }
    ArmClientSupport(BeastDoubleWieldPlugin owner, BiFunction<Plugin, UUID, Integer> query) {
        this.owner = owner; this.query = query;
    }

    boolean allowed(Player player) {
        if (owner.getIntegrations() != null && owner.getIntegrations().isBedrock(player)) return false;
        PluginManager manager = owner.getServer().getPluginManager();
        Plugin via = manager == null ? null : manager.getPlugin("ViaVersion");
        if (via == null || !via.isEnabled()) return true;
        Integer protocol = query.apply(via, player.getUniqueId());
        return protocol != null && protocol == PROTOCOL_26_3 && via.isEnabled()
                && manager.getPlugin("ViaVersion") == via;
    }

    private static Integer viaProtocol(Plugin via, UUID id) {
        try {
            ClassLoader loader = via.getClass().getClassLoader();
            Class<?> facade = Class.forName("com.viaversion.viaversion.api.Via", false, loader);
            Class<?> apiType = Class.forName("com.viaversion.viaversion.api.ViaAPI", false, loader);
            Object api = facade.getMethod("getAPI").invoke(null);
            if (api == null || !Integer.valueOf(5).equals(apiType.getMethod("majorVersion").invoke(api))) return null;
            Object result = apiType.getMethod("getPlayerVersion", UUID.class).invoke(api, id);
            return result instanceof Integer ? (Integer) result : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { return null; }
    }
}
