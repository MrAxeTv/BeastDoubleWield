package probe;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.combat.OffhandCombat;
import me.mraxetv.beastdoublewield.placeholder.PlaceholderItem;
import me.mraxetv.beastdoublewield.util.HandAccess;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.logging.Level;

/** Destructive only inside the explicitly disposable loopback acceptance fixture. */
public final class PlaceholderProbe extends JavaPlugin implements Listener {
    private BeastDoubleWieldPlugin plugin;
    private Player player;
    private int passed, failed, stage;
    private boolean cleanDuringDamage;
    private final NamespacedKey key = new NamespacedKey("beastdoublewield", "input_placeholder");
    @Override public void onEnable() {
        plugin = (BeastDoubleWieldPlugin) getServer().getPluginManager().getPlugin("BeastDoubleWield");
        getServer().getPluginManager().registerEvents(this, this);
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        if (joined.getName().equals("BDWObserver")) {
            later(20, () -> { joined.setGameMode(GameMode.SPECTATOR); joined.teleport(new Location(joined.getWorld(), 2, 72, 2)); });
            return;
        }
        if (!joined.getName().equals("BDWPlaceholder")) return;
        player = joined;
        player.addAttachment(this, "beastdoublewield.attack", true);
        player.addAttachment(this, "beastdoublewield.use", true);
        player.addAttachment(this, "beastdoublewield.toggle", true);
        player.addAttachment(this, "beastdoublewield.admin", true);
        if (stage == 10) { later(8, this::disable); return; }
        later(30, () -> {
            World world = player.getWorld();
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            for (int x = -3; x < 6; x++) for (int z = -3; z < 5; z++) {
                world.getBlockAt(x, 69, z).setType(Material.STONE);
                for (int y = 70; y < 75; y++) world.getBlockAt(x, y, z).setType(Material.AIR);
            }
            player.setGameMode(GameMode.SURVIVAL); player.setFoodLevel(20);
            player.teleport(new Location(world, 0.5, 70, 0.5, -90, 0));
            player.getInventory().clear(); player.getInventory().setHeldItemSlot(0);
            player.getInventory().setItem(8, new ItemStack(Material.DIAMOND, 3));
            later(8, this::metadata);
        });
    }
    private void metadata() {
        ItemStack item = player.getInventory().getItemInMainHand();
        check("empty main receives owned feather", PlaceholderItem.isOwned(item) && item.getAmount() == 1);
        ItemMeta meta = item.getItemMeta();
        check("native PDC byte ownership", Byte.valueOf((byte)1).equals(meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE)));
        check("native CustomModelData 35021", meta.hasCustomModelData() && meta.getCustomModelData() == 35021);
        check("blank visible name and no lore", meta.hasDisplayName() && meta.getDisplayName().trim().isEmpty() && !meta.hasLore());
        player.getInventory().setItemInMainHand(new ItemStack(Material.GOLD_INGOT, 4));
        later(6, () -> {
            check("genuine main-hand stack preserved", player.getInventory().getItemInMainHand().getType() == Material.GOLD_INGOT && player.getInventory().getItemInMainHand().getAmount() == 4);
            ItemStack ordinary = new ItemStack(Material.FEATHER, 2);
            ItemMeta ordinaryMeta = ordinary.getItemMeta(); ordinaryMeta.setCustomModelData(35021);
            ordinaryMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "not-owned"); ordinary.setItemMeta(ordinaryMeta);
            player.getInventory().setItemInMainHand(ordinary);
            player.getInventory().setItem(5, PlaceholderItem.create());
            plugin.getPlaceholderService().cleanup(player);
            check("wrong-type marker and matching model remain genuine", !PlaceholderItem.isOwned(ordinary) && player.getInventory().getItemInMainHand().getAmount() == 2);
            check("wrong-type marker does not abort later cleanup", player.getInventory().getItem(5) == null);
            player.getInventory().setItemInMainHand(null);
            later(8, this::villager);
        });
    }
    private void villager() {
        Villager trader = player.getWorld().spawn(new Location(player.getWorld(), 2.3, 70, 0.5), Villager.class);
        trader.setAI(false); trader.setProfession(Villager.Profession.FARMER);
        MerchantRecipe trade = new MerchantRecipe(new ItemStack(Material.BREAD), 10);
        trade.addIngredient(new ItemStack(Material.WHEAT, 3));
        trader.setRecipes(java.util.Collections.singletonList(trade));
        check("villager check starts with main-hand placeholder", PlaceholderItem.isOwned(player.getInventory().getItemInMainHand()));
        later(4, () -> {
            player.sendMessage("PH_VILLAGER " + trader.getEntityId());
            later(20, () -> {
                check("placeholder preserves native villager trade interaction", player.getOpenInventory().getType() == InventoryType.MERCHANT);
                player.closeInventory(); trader.remove();
                later(8, () -> { stage = 1; player.sendMessage("PH_AIR"); later(40, () -> { if (stage == 1) throw new IllegalStateException("Air packet acknowledgement missing"); }); });
            });
        });
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender != player || args.length != 2 || !args[0].equals("swing") || stage != 1) return true;
        check("native air-use packet emits offhand swing with empty offhand", Integer.parseInt(args[1]) > 0 && player.getInventory().getItemInOffHand().getType() == Material.AIR);
        stage = 2; later(25, this::combat); return true;
    }
    private void combat() {
        player.getInventory().setItemInOffHand(new ItemStack(Material.DIAMOND_SWORD));
        Cow target = player.getWorld().spawn(new Location(player.getWorld(), 2.3, 70, 0.5), Cow.class);
        target.setAI(false); target.setMaxHealth(40); target.setHealth(40);
        cleanDuringDamage = true;
        boolean claimed = new OffhandCombat(plugin).attack(player, target);
        cleanDuringDamage = false;
        check("native offhand attack damages target", claimed && target.getHealth() < 40);
        check("cleanup inside damage callback preserves real offhand weapon", player.getInventory().getItemInOffHand().getType() == Material.DIAMOND_SWORD && player.getInventory().getItemInOffHand().getDurability() > 0);
        check("queued cleanup removes restored placeholder after attack", !PlaceholderItem.isOwned(player.getInventory().getItemInMainHand()) && !HandAccess.isOffhandAction(player));
        target.remove();
        later(8, () -> {
            player.setItemOnCursor(new ItemStack(Material.EMERALD, 2));
            player.sendMessage("PH_CLICK"); later(12, this::cursor);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void damaged(EntityDamageByEntityEvent event) {
        if (event.getDamager() == player && cleanDuringDamage) {
            check("damage callback sees active offhand transaction", HandAccess.isOffhandAction(player));
            plugin.getPlaceholderService().cleanup(player);
            check("cleanup waits until hand transaction ends", PlaceholderItem.isOwned(player.getInventory().getItemInOffHand()));
        }
    }
    private void cursor() {
        check("cancelled placeholder click preserves genuine cursor", player.getItemOnCursor().getType() == Material.EMERALD && player.getItemOnCursor().getAmount() == 2);
        check("inventory cleanup removed placeholder", ownedCount() == 0);
        player.getInventory().setItem(9, player.getItemOnCursor()); player.setItemOnCursor(null);
        player.sendMessage("PH_CLOSE");
        later(8, () -> { check("placeholder resumes after inventory close", PlaceholderItem.isOwned(player.getInventory().getItemInMainHand())); player.sendMessage("PH_SWAP"); later(8, this::swap); });
    }
    private void swap() {
        check("swap retains genuine offhand sword in main hand", player.getInventory().getItemInMainHand().getType() == Material.DIAMOND_SWORD);
        check("swap discards only placeholder", player.getInventory().getItemInOffHand().getType() == Material.AIR && ownedCount() == 0);
        player.getInventory().setItem(7, player.getInventory().getItemInMainHand()); player.getInventory().setItemInMainHand(null);
        later(8, () -> { player.sendMessage("PH_DROP"); later(10, this::drop); });
    }
    private void drop() {
        check("owned drop never becomes world item", player.getWorld().getEntitiesByClass(Item.class).stream().noneMatch(item -> PlaceholderItem.isOwned(item.getItemStack())));
        realItems("after cursor/swap/drop");
        plugin.setEnabled(player, false);
        check("personal toggle cleans placeholder immediately", ownedCount() == 0);
        later(6, () -> {
            check("personal off stays empty", ownedCount() == 0);
            plugin.setEnabled(player, true);
            later(6, this::reload);
        });
    }
    private void reload() {
        check("personal toggle on recreates placeholder", ownedCount() == 1);
        plugin.getConfig().set("placeholder.enabled", false);
        try { plugin.getConfig().save(new File(plugin.getDataFolder(), "config.yml")); plugin.reloadSettings(); }
        catch (Exception error) { throw new IllegalStateException(error); }
        check("config reload disabling placeholder cleans it", ownedCount() == 0);
        plugin.getConfig().set("placeholder.enabled", true);
        try { plugin.getConfig().save(new File(plugin.getDataFolder(), "config.yml")); plugin.reloadSettings(); }
        catch (Exception error) { throw new IllegalStateException(error); }
        later(8, () -> { check("config reload re-enables placeholder", ownedCount() == 1); stage = 9; player.setHealth(0); });
    }
    @EventHandler(priority = EventPriority.MONITOR) public void death(PlayerDeathEvent event) {
        if (event.getEntity() != player || stage != 9) return;
        check("death drops exclude placeholder", event.getDrops().stream().noneMatch(PlaceholderItem::isOwned));
        check("death cleanup removes placeholder with keepInventory", ownedCount() == 0);
        realItems("on death");
    }
    @EventHandler(priority = EventPriority.MONITOR) public void respawn(PlayerRespawnEvent event) {
        if (event.getPlayer() != player || stage != 9) return;
        event.setRespawnLocation(new Location(player.getWorld(), 0.5, 70, 0.5, -90, 0));
        later(8, () -> { check("placeholder resumes after respawn", ownedCount() == 1); stage = 10; player.sendMessage("PH_RECONNECT"); });
    }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) {
        if (event.getPlayer() == player && stage == 10) { check("quit cleanup removes placeholder", ownedCount() == 0); realItems("on quit"); }
    }
    private void disable() {
        realItems("after reconnect");
        check("placeholder resumes after reconnect", ownedCount() == 1);
        getServer().getPluginManager().disablePlugin(plugin);
        check("plugin disable cleans owned items", ownedCount() == 0);
        realItems("after disable");
        stage = 11;
        getLogger().info("PH_RESULT passed=" + passed + " failed=" + failed + " server=" + getServer().getBukkitVersion());
        player.sendMessage("PH_DONE passed=" + passed + " failed=" + failed);
        later(30, () -> getServer().shutdown());
    }
    private int ownedCount() {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) if (PlaceholderItem.isOwned(item)) count += item.getAmount();
        if (PlaceholderItem.isOwned(player.getItemOnCursor())) count += player.getItemOnCursor().getAmount();
        return count;
    }
    private void realItems(String moment) {
        int diamonds = 0, emeralds = 0;
        for (ItemStack item : player.getInventory().getContents()) if (item != null) {
            if (item.getType() == Material.DIAMOND) diamonds += item.getAmount();
            if (item.getType() == Material.EMERALD) emeralds += item.getAmount();
        }
        check("real item quantities preserved " + moment, diamonds == 3 && emeralds == 2);
    }
    private void check(String name, boolean success) {
        if (success) passed++; else failed++;
        getLogger().info("PH_CHECK " + (success ? "PASS " : "FAIL ") + name);
    }
    private void later(long ticks, Runnable action) {
        getServer().getScheduler().runTaskLater(this, () -> {
            try { action.run(); }
            catch (Throwable error) { failed++; getLogger().log(Level.SEVERE, "PH_ERROR", error); getServer().shutdown(); }
        }, ticks);
    }
}
