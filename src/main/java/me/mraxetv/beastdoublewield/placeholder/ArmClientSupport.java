package me.mraxetv.beastdoublewield.placeholder;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Public connection/translation APIs must positively identify the tested protocol. */
final class ArmClientSupport {
    // Read from the official installed 26.3 client's version.json.
    static final int PROTOCOL_26_3 = 777;
    private static final Method PAPER_PROTOCOL = paperProtocolMethod();
    private final BeastDoubleWieldPlugin owner;
    private final BiFunction<Plugin, UUID, Integer> query;
    private final Function<Player, Integer> nativeQuery;

    ArmClientSupport(BeastDoubleWieldPlugin owner) { this(owner, ArmClientSupport::viaProtocol); }
    ArmClientSupport(BeastDoubleWieldPlugin owner, BiFunction<Plugin, UUID, Integer> query) {
        this(owner, query, ArmClientSupport::paperProtocol);
    }
    ArmClientSupport(BeastDoubleWieldPlugin owner, BiFunction<Plugin, UUID, Integer> query,
                     Function<Player, Integer> nativeQuery) {
        this.owner = owner; this.query = query; this.nativeQuery = nativeQuery;
    }

    boolean allowed(Player player) {
        try {
            if (owner.getIntegrations() != null && owner.getIntegrations().isBedrock(player)) return false;
            PluginManager manager = owner.getServer().getPluginManager();
            if (manager == null) return false;
            Plugin via = manager.getPlugin("ViaVersion");
            if (via == null) {
                // Paper exposes the connection's handshake protocol. A translating
                // proxy can still hide the original client behind its backend leg.
                Integer protocol = nativeQuery.apply(player);
                return exact(protocol) && manager.getPlugin("ViaVersion") == null;
            }
            // A present but disabled/broken translator cannot establish the client.
            if (!via.isEnabled()) return false;
            Integer protocol = query.apply(via, player.getUniqueId());
            return exact(protocol) && via.isEnabled() && manager.getPlugin("ViaVersion") == via;
        } catch (LinkageError | RuntimeException unavailable) { return false; }
    }

    private static boolean exact(Integer protocol) {
        return protocol != null && protocol == PROTOCOL_26_3;
    }

    private static Method paperProtocolMethod() {
        try { return Player.class.getMethod("getProtocolVersion"); }
        catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { return null; }
    }

    private static Integer paperProtocol(Player player) {
        if (PAPER_PROTOCOL == null) return null;
        try {
            Object result = PAPER_PROTOCOL.invoke(player);
            return result instanceof Integer ? (Integer) result : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { return null; }
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
