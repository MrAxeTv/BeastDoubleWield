package me.mraxetv.beastdoublewield.command;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Commands use Bukkit registration so the plugin always retains its namespaced entry. */
public final class DoubleWieldCommand implements CommandExecutor, TabCompleter {
    private final BeastDoubleWieldPlugin plugin;

    public DoubleWieldCommand(BeastDoubleWieldPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isEnabled()) return true;
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("integrations")) {
            if (!allowed(sender, "use")) return denied(sender);
            if (plugin.getIntegrations() != null) {
                for (java.util.Map.Entry<String,String> entry : plugin.getIntegrations().status().entrySet())
                    plugin.message(sender, "IntegrationStatus", "name", entry.getKey(), "state", entry.getValue());
                if (sender instanceof Player) plugin.message(sender, "IntegrationStatus", "name", "Client",
                        "state", plugin.getIntegrations().clientDescription((Player)sender));
            }
            return true;
        }
        if (action.equals("update")) {
            if (!allowed(sender, "update")) return denied(sender);
            if (plugin.getRuntimeServices() != null)
                plugin.message(sender, "UpdateRequest", "state", plugin.getRuntimeServices().checkNow(sender).name());
            return true;
        }
        if (action.equals("reload")) {
            if (!allowed(sender, "reload")) return denied(sender);
            if (args.length != 1) return help(sender);
            try {
                plugin.reloadSettings();
                plugin.message(sender, "Reloaded");
            } catch (Exception error) {
                plugin.getLogger().log(Level.WARNING, "Configuration reload rejected; active settings were retained", error);
                plugin.message(sender, "ReloadFailed");
            }
            return true;
        }
        if (action.equals("toggle")) {
            if (!allowed(sender, "toggle")) return denied(sender);
            if (!(sender instanceof Player)) { plugin.message(sender, "PlayerOnly"); return true; }
            Player player = (Player) sender;
            boolean enabled = !plugin.isPersonallyEnabled(player);
            if (args.length > 2) return help(sender);
            if (args.length == 2) {
                if (args[1].equalsIgnoreCase("on")) enabled = true;
                else if (args[1].equalsIgnoreCase("off")) enabled = false;
                else return help(sender);
            }
            try { plugin.setEnabled(player, enabled); }
            catch (IllegalStateException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not persist player preference", failure);
                plugin.message(sender, "PreferenceFailed"); return true;
            }
            plugin.message(sender, enabled ? "Enabled" : "Disabled");
            return true;
        }
        if (!allowed(sender, "use")) return denied(sender);
        if (action.equals("status") && args.length == 1) {
            plugin.message(sender, "Status", "version", plugin.getDescription().getVersion(),
                    "global", plugin.getConfig().getBoolean("enabled", true) ? "on" : "off");
            if (sender instanceof Player)
                plugin.message(sender, "PersonalStatus", "state", plugin.isPersonallyEnabled((Player) sender) ? "on" : "off");
            if (plugin.getRuntimeServices() != null) plugin.message(sender, "ServicesStatus", "state", plugin.getRuntimeServices().describe());
            if (plugin.getBStatsTelemetry() != null) plugin.message(sender, "ServicesStatus", "state", plugin.getBStatsTelemetry().describe());
            if (plugin.getPlaceholderService() != null) plugin.message(sender, "ServicesStatus", "state", plugin.getPlaceholderService().describe());
            return true;
        }
        return help(sender);
    }

    private boolean allowed(CommandSender sender, String suffix) {
        return sender.hasPermission("beastdoublewield." + suffix) || sender.hasPermission("beastdoublewield.admin");
    }
    private boolean denied(CommandSender sender) { plugin.message(sender, "NoPermission"); return true; }
    private boolean help(CommandSender sender) {
        plugin.message(sender, "Help");
        if (allowed(sender, "reload")) plugin.message(sender, "AdminHelp");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.isEnabled() || args.length == 0) return Collections.emptyList();
        List<String> options = new ArrayList<String>();
        if (args.length == 1) {
            if (allowed(sender, "use")) { options.add("help"); options.add("status"); options.add("integrations"); }
            if (allowed(sender, "toggle") && sender instanceof Player) options.add("toggle");
            if (allowed(sender, "reload")) options.add("reload");
            if (allowed(sender, "update")) options.add("update");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")
                && allowed(sender, "toggle") && sender instanceof Player) {
            options.add("on"); options.add("off");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<String>();
        for (String option : options) if (option.startsWith(prefix)) matches.add(option);
        return matches;
    }
}
