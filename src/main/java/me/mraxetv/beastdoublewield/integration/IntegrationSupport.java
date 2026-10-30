package me.mraxetv.beastdoublewield.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Optional APIs are queried through their owning plugin without a hard class dependency. */
public final class IntegrationSupport {
    private final PluginManager plugins;
    private final BedrockProbe bedrock;

    public IntegrationSupport(Plugin owner) { this(owner.getServer().getPluginManager(), IntegrationSupport::queryBedrock); }
    IntegrationSupport(PluginManager plugins, BedrockProbe bedrock) { this.plugins = plugins; this.bedrock = bedrock; }

    /** A positive answer is API evidence, never a username or UUID-format guess. */
    public boolean isBedrock(Player player) {
        for (String name : new String[]{"floodgate", "Geyser-Spigot"}) {
            Plugin provider = active(name);
            if (provider == null) continue;
            Boolean result = bedrock.test(provider, player.getUniqueId());
            if (Boolean.TRUE.equals(result) && provider.isEnabled() && plugins.getPlugin(name) == provider) return true;
        }
        return false;
    }

    public String clientDescription(Player player) {
        return isBedrock(player) ? "Bedrock (Geyser/Floodgate API); offhand animation depends on the client"
                : "Java or Bedrock client without a local detection API";
    }

    /** Status describes available hooks rather than asserting untested gameplay compatibility. */
    public Map<String, String> status() {
        Map<String, String> status = new LinkedHashMap<>();
        Plugin enchants = active("BeastEnchants");
        status.put("BeastEnchants", enchants == null ? "not installed/enabled" : label(enchants) + (hasClass(enchants,
                "me.mraxetv.beastenchants.CombatSource") ? "; physical offhand source validation available" : "; update required for delayed offhand effects"));
        Plugin skills = active("mcMMO");
        status.put("mcMMO", skills == null ? "not installed/enabled" : label(skills) + "; native combat, mining-start and break events");
        for (String name : new String[]{"floodgate", "Geyser-Spigot"}) {
            Plugin provider = active(name);
            status.put(name, provider == null ? "not installed/enabled" : label(provider) + "; optional Bedrock identity API");
        }
        return Collections.unmodifiableMap(status);
    }

    private Plugin active(String name) {
        Plugin value = plugins.getPlugin(name); return value != null && value.isEnabled() ? value : null;
    }
    private static String label(Plugin plugin) { return plugin.getDescription().getVersion(); }
    private static boolean hasClass(Plugin plugin, String type) {
        try { Class.forName(type, false, plugin.getClass().getClassLoader()); return true; }
        catch (ClassNotFoundException | LinkageError absent) { return false; }
    }
    private static Boolean queryBedrock(Plugin provider, UUID player) {
        boolean floodgate = provider.getName().equals("floodgate");
        try {
            Class<?> type = Class.forName(floodgate ? "org.geysermc.floodgate.api.FloodgateApi" : "org.geysermc.geyser.api.GeyserApi",
                    false, provider.getClass().getClassLoader());
            Method factory = type.getMethod(floodgate ? "getInstance" : "api");
            Object api = factory.invoke(null);
            if (api == null) return null;
            Object result = type.getMethod(floodgate ? "isFloodgatePlayer" : "isBedrockPlayer", UUID.class).invoke(api, player);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof VirtualMachineError) throw (VirtualMachineError) cause;
            if (cause instanceof ThreadDeath) throw (ThreadDeath) cause;
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { return null; }
    }
    interface BedrockProbe { Boolean test(Plugin plugin, UUID player); }
}
