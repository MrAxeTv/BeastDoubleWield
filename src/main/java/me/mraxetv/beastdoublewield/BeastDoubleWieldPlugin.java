package me.mraxetv.beastdoublewield;

import me.mraxetv.beastdoublewield.combat.OffhandCombat;
import me.mraxetv.beastdoublewield.command.DoubleWieldCommand;
import me.mraxetv.beastdoublewield.interaction.OffhandInput;
import me.mraxetv.beastdoublewield.mining.OffhandMining;
import me.mraxetv.beastdoublewield.util.ItemRules;
import me.mraxetv.beastdoublewield.util.PlayerPreferences;
import me.mraxetv.beastdoublewield.service.RuntimeServices;
import me.mraxetv.beastdoublewield.service.BStatsTelemetry;
import me.mraxetv.beastdoublewield.integration.IntegrationSupport;
import me.mraxetv.beastdoublewield.compat.ServerAccess;
import me.mraxetv.beastdoublewield.placeholder.PlaceholderService;
import me.mraxetv.beastlib.api.BeastLibAPI;
import me.mraxetv.beastlib.api.scheduler.SchedulerServices;
import me.mraxetv.beastlib.lib.boostedyaml.YamlDocument;
import me.mraxetv.beastlib.utils.BUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/** Owns configuration and the BeastLib-backed lifecycle of the plugin. */
public final class BeastDoubleWieldPlugin extends JavaPlugin implements BeastLibAPI {
    private FileConfiguration settings;
    private YamlDocument language;
    private BUtils<BeastDoubleWieldPlugin> utils;
    private PlayerPreferences preferences;
    private OffhandCombat combat;
    private OffhandMining mining;
    private RuntimeServices runtimeServices;
    private BStatsTelemetry bStatsTelemetry;
    private IntegrationSupport integrations;
    private PlaceholderService placeholders;
    public IntegrationSupport getIntegrations() { return integrations; }
    public RuntimeServices getRuntimeServices() { return runtimeServices; }
    public BStatsTelemetry getBStatsTelemetry() { return bStatsTelemetry; }
    public PlaceholderService getPlaceholderService() { return placeholders; }
    public void recordAttack() { if (runtimeServices != null) runtimeServices.recordAttack(); }
    public void recordBlockBreak() { if (runtimeServices != null) runtimeServices.recordBlockBreak(); }

    @Override public YamlDocument getMessages() { return language; }
    @Override public BUtils<BeastDoubleWieldPlugin> getUtils() { return utils; }
    @Override public FileConfiguration getConfig() { return settings == null ? super.getConfig() : settings; }

    @Override public void onEnable() {
        try {
            if (!SchedulerServices.isAvailable()) throw new IllegalStateException("BeastLib scheduler is unavailable");
            if (getScheduler().isFolia()) throw new IllegalStateException("Folia is not supported by this version of BeastDoubleWield");
            saveDefaultConfig();
            preferences = new PlayerPreferences(this);
            getYamlFiles().load(this, "config.yml", "config.yml", true);
            reloadSettings();
            utils = new BUtils<BeastDoubleWieldPlugin>(this) {
                @Override public String getPrefix() { return language.getString("Prefix", "&8[&6BeastDoubleWield&8] &r"); }
                @Override public String setPlaceholders(CommandSender sender, String text) {
                    return setColor(text.replace("%prefix%", getPrefix())
                            .replace("%player%", sender == null ? "" : sender.getName()));
                }
            };
            combat = new OffhandCombat(this);
            mining = new OffhandMining(this);
            integrations = new IntegrationSupport(this);
            OffhandInput input = new OffhandInput(this, combat, mining);
            getServer().getPluginManager().registerEvents(input, this);
            getServer().getPluginManager().registerEvents(mining, this);
            runtimeServices = new RuntimeServices(this);
            getServer().getPluginManager().registerEvents(runtimeServices, this);
            runtimeServices.start();
            PluginCommand registered = getCommand("beastdoublewield");
            if (registered == null) throw new IllegalStateException("Missing beastdoublewield command in plugin.yml");
            DoubleWieldCommand command = new DoubleWieldCommand(this);
            registered.setExecutor(command);
            registered.setTabCompleter(command);
            placeholders = new PlaceholderService(this);
            getServer().getPluginManager().registerEvents(placeholders, this);
            placeholders.reload();
            bStatsTelemetry = new BStatsTelemetry(this);
            bStatsTelemetry.start();
            getLogger().info("BeastDoubleWield " + getDescription().getVersion() + " enabled with BeastLib services.");
            getLogger().info(ServerAccess.backendSummary());
        } catch (Exception error) {
            getLogger().log(Level.SEVERE, "Cannot enable BeastDoubleWield: " + error.getMessage(), error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** Parses and validates the whole replacement before publishing it to gameplay. */
    public void reloadSettings() throws Exception {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(new File(getDataFolder(), "config.yml"));
        ItemRules.validate(candidate);
        RuntimeServices.validate(candidate);
        PlaceholderService.validate(candidate);
        YamlDocument nextLanguage = getYamlFiles().load(this, "Lang/English.yml", "Lang/English.yml", true);
        FileConfiguration previous = settings;
        YamlDocument previousLanguage = language;
        settings = candidate;
        language = nextLanguage;
        try { if (runtimeServices != null) runtimeServices.reload(); }
        catch (RuntimeException failure) { settings = previous; language = previousLanguage; throw failure; }
        if (mining != null) mining.close();
        if (combat != null) combat.clear();
        if (placeholders != null) placeholders.reload();
    }

    /** Global and personal switches; permissions/world restrictions are checked by gameplay. */
    public boolean isEnabled(Player player) {
        return getConfig().getBoolean("enabled", true) && isPersonallyEnabled(player);
    }

    public boolean isPersonallyEnabled(Player player) {
        return preferences == null || preferences.enabled(player);
    }

    public void setEnabled(Player player, boolean enabled) {
        try { preferences.setEnabled(player, enabled); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Could not save player preference", failure); }
        if (!enabled && mining != null) mining.stop(player.getUniqueId());
        if (placeholders != null) placeholders.cleanup(player);
    }

    public void message(CommandSender sender, String key, Object... replacements) {
        String text = language.getString(key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2)
            text = text.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        for (String line : text.split("\n", -1)) utils.sendMessage(sender, line);
    }

    @Override public void onDisable() {
        if (placeholders != null) placeholders.close();
        if (bStatsTelemetry != null) bStatsTelemetry.close();
        if (runtimeServices != null) runtimeServices.close();
        if (mining != null) mining.close();
        if (combat != null) combat.clear();
        if (SchedulerServices.isAvailable()) getScheduler().cancelTasks(this);
        if (preferences != null) try { preferences.save(); }
        catch (java.io.IOException failure) { getLogger().log(Level.SEVERE, "Could not save player preferences", failure); }
    }
}
