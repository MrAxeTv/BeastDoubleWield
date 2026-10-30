package me.mraxetv.beastdoublewield.placeholder;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.compat.ServerAccess;
import me.mraxetv.beastdoublewield.util.HandAccess;
import me.mraxetv.beastdoublewield.util.ItemRules;
import me.mraxetv.beastlib.api.scheduler.SchedulerTask;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/** Maintains an optional disposable main-hand carrier without replacing genuine items. */
public final class PlaceholderService implements Listener, AutoCloseable {
    private final BeastDoubleWieldPlugin plugin;
    private final ArmClientSupport armClients;
    private final Set<UUID> inventoryOpen = new HashSet<>();
    private final Set<UUID> cleanupPending = new HashSet<>();
    private final Map<UUID, Integer> nextProfileAttempt = new HashMap<>();
    private SchedulerTask timer;
    private boolean enabled;
    private boolean armEnabled;
    private boolean closed;
    private long generation;
    private String state = "not started";

    public PlaceholderService(BeastDoubleWieldPlugin plugin) { this.plugin = plugin; this.armClients = new ArmClientSupport(plugin); }

    public static void validate(ConfigurationSection config) {
        if (config.contains("placeholder.enabled") && !(config.get("placeholder.enabled") instanceof Boolean))
            throw new IllegalArgumentException("placeholder.enabled must be true or false");
        if (config.contains("placeholder.first-person-arm") && !(config.get("placeholder.first-person-arm") instanceof Boolean))
            throw new IllegalArgumentException("placeholder.first-person-arm must be true or false");
    }

    /** Reconfiguration is optional and must not invalidate otherwise valid gameplay settings. */
    public void reload() {
        if (closed) return;
        generation++;
        enabled = false;
        armEnabled = false;
        nextProfileAttempt.clear();
        stopTimer();
        for (Player player : plugin.getServer().getOnlinePlayers()) cleanup(player);
        cleanupPending.clear();
        if (!plugin.getConfig().getBoolean("placeholder.enabled", false)) { state = "disabled"; return; }
        if (!PlaceholderItem.supported()) {
            state = "unavailable (requires 1.14+)";
            plugin.getLogger().warning("The optional invisible input placeholder requires Minecraft 1.14 or newer; core gameplay remains enabled.");
            return;
        }
        try {
            long token = generation;
            SchedulerTask next = plugin.getScheduler().runGlobalRepeating(plugin, () -> {
                if (generation == token) tick();
            }, 1L, 2L);
            if (next == null || next.isDone()) throw new IllegalStateException("Placeholder scheduler rejected its task");
            timer = next;
            enabled = true;
            state = "enabled (resource pack required)";
            if (plugin.getConfig().getBoolean("placeholder.first-person-arm", false)) {
                armEnabled = PlaceholderItem.armSupported(plugin.getServer().getBukkitVersion());
                state += armEnabled ? ", first-person arm enabled (26.3 shader pack required)"
                        : ", first-person arm unavailable (requires Paper 26.3/component API)";
                if (!armEnabled) plugin.getLogger().warning("First-person arms require the verified Paper 26.3 shader/component API path; retaining the invisible input placeholder.");
            }
            plugin.getLogger().info("Invisible input placeholder enabled. Install the matching optional pack manually; otherwise its feather is visible.");
        } catch (RuntimeException failure) {
            state = "unavailable";
            plugin.getLogger().log(Level.WARNING, "Could not start optional input placeholders", failure);
        }
    }

    public String describe() { return "placeholder=" + state; }

    private void tick() {
        if (!enabled || closed) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (cleanupPending.contains(player.getUniqueId())) continue;
            try { reconcile(player); }
            catch (RuntimeException failure) {
                cleanup(player);
                plugin.getLogger().log(Level.WARNING, "Could not reconcile an input placeholder for " + player.getUniqueId(), failure);
            }
        }
    }

    /** Server thread only. Package visibility keeps inventory-state regression tests direct. */
    void reconcile(Player player) {
        if (HandAccess.isOffhandAction(player)) return;
        PlayerInventory inventory = player.getInventory();
        int selected = inventory.getHeldItemSlot();
        boolean eligible = eligible(player) && spareStorageSlot(inventory, selected);
        boolean keepArm = eligible && armEnabled && armClients.allowed(player);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            boolean validSlot = PlaceholderItem.isArm(item) ? keepArm && slot == 40 : eligible && slot == selected;
            if (PlaceholderItem.isOwned(item) && (!validSlot || item.getAmount() != 1))
                inventory.setItem(slot, null);
        }
        if (PlaceholderItem.isOwned(player.getItemOnCursor())) player.setItemOnCursor(null);
        if (keepArm && empty(player.getItemOnCursor()) && empty(inventory.getItemInOffHand())) {
            int now = player.getTicksLived();
            Integer next = nextProfileAttempt.get(player.getUniqueId());
            if (next == null || now >= next) {
                ItemStack arm = PlaceholderItem.createArm(player);
                if (arm != null) { inventory.setItemInOffHand(arm); nextProfileAttempt.remove(player.getUniqueId()); }
                else nextProfileAttempt.put(player.getUniqueId(), now + 100);
            }
        }
        // The offhand carrier already generates air-use input. Keep the actual main hand
        // empty so vanilla continues to draw its normal first-person arm.
        if (PlaceholderItem.isArm(inventory.getItemInOffHand())) {
            if (PlaceholderItem.isOwned(inventory.getItem(selected))) inventory.setItem(selected, null);
        } else if (eligible && empty(player.getItemOnCursor()) && empty(inventory.getItem(selected))) {
            inventory.setItem(selected, PlaceholderItem.create());
        }
    }

    private boolean eligible(Player player) {
        if (!enabled || closed || !player.isOnline() || player.isDead() || !plugin.isEnabled(player)
                || !player.hasPermission("beastdoublewield.attack") || inventoryOpen.contains(player.getUniqueId())
                || player.getOpenInventory().getType() != InventoryType.CRAFTING
                || plugin.getConfig().getStringList("disabled-worlds").contains(player.getWorld().getName())) return false;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return false;
        ConfigurationSection attack = plugin.getConfig().getConfigurationSection("attack");
        return plugin.getConfig().getBoolean("swing.air", false) && attack != null
                && attack.getBoolean("allow-empty-hand", false)
                && ItemRules.canUse(player, player.getInventory().getItemInOffHand(), attack);
    }

    // Never consume the last genuinely free storage slot: real pickups must still fit.
    private static boolean spareStorageSlot(PlayerInventory inventory, int selected) {
        for (int slot = 0; slot < 36; slot++) if (slot != selected
                && (empty(inventory.getItem(slot)) || PlaceholderItem.isOwned(inventory.getItem(slot)))) return true;
        return false;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getAmount() < 1 || ServerAccess.air(item.getType());
    }

    /** Also used after a successful personal toggle and to remove stale items after a crash. */
    public void cleanup(Player player) {
        HandAccess.afterOffhand(player, () -> {
            try {
                PlayerInventory inventory = player.getInventory();
                clearOwned(inventory);
                if (player.getOpenInventory() != null) clearOwned(player.getOpenInventory().getTopInventory());
                if (PlaceholderItem.isOwned(player.getItemOnCursor())) player.setItemOnCursor(null);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not remove an owned input placeholder for " + player.getUniqueId(), failure);
            }
        });
    }

    private void suspendInventory(Player player) {
        inventoryOpen.add(player.getUniqueId());
        queueCleanup(player);
    }

    private void queueCleanup(Player player) {
        if (closed || !PlaceholderItem.supported() || !cleanupPending.add(player.getUniqueId())) return;
        long token = generation;
        try {
            SchedulerTask next = plugin.getScheduler().runPlayerDelayed(plugin, player, () -> {
                if (generation != token) return;
                cleanupPending.remove(player.getUniqueId());
                if (!closed) cleanup(player);
            }, 1L, () -> {
                if (generation == token) cleanupPending.remove(player.getUniqueId());
            });
            if (next == null || next.isDone()) cleanupPending.remove(player.getUniqueId());
        } catch (RuntimeException failure) {
            cleanupPending.remove(player.getUniqueId());
            plugin.getLogger().log(Level.WARNING, "Could not defer input placeholder cleanup", failure);
        }
    }

    private static void clearOwned(Inventory inventory) {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++)
            if (PlaceholderItem.isOwned(inventory.getItem(slot))) inventory.setItem(slot, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            inventoryOpen.add(player.getUniqueId());
            cleanup(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void closeInventory(InventoryCloseEvent event) {
        inventoryOpen.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        suspendInventory(player);
        boolean ownedHotbar = event.getHotbarButton() >= 0
                && PlaceholderItem.isOwned(player.getInventory().getItem(event.getHotbarButton()));
        boolean ownedSwap = "SWAP_OFFHAND".equals(event.getClick().name())
                && PlaceholderItem.isOwned(player.getInventory().getItemInOffHand());
        if (PlaceholderItem.isOwned(event.getCurrentItem()) || PlaceholderItem.isOwned(event.getCursor()) || ownedHotbar || ownedSwap
                || (event instanceof CraftItemEvent && containsOwned(event.getInventory())))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        suspendInventory((Player) event.getWhoClicked());
        if (PlaceholderItem.isOwned(event.getOldCursor())
                || event.getNewItems().values().stream().anyMatch(PlaceholderItem::isOwned)
                || event.getRawSlots().stream().anyMatch(slot -> PlaceholderItem.isOwned(event.getView().getItem(slot))))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) {
        // Older CraftBukkit packet handlers dereference the event's resulting
        // stacks even though current API setters permit null. Use explicit AIR.
        if (PlaceholderItem.isOwned(event.getMainHandItem())) event.setMainHandItem(new ItemStack(Material.AIR));
        if (PlaceholderItem.isOwned(event.getOffHandItem())) event.setOffHandItem(new ItemStack(Material.AIR));
        queueCleanup(event.getPlayer());
    }

    // Registered after OffhandInput, so a custom attack has already run. A remaining
    // vanilla interaction must never give our disposable item to an item holder.
    // A feather has no vanilla feeding/equipment use on other mobs: allow normal
    // mounting, trading, and other interactions that do not transfer this item.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void entity(PlayerInteractEntityEvent event) { protectEntityInteraction(event); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityAt(PlayerInteractAtEntityEvent event) { protectEntityInteraction(event); }

    // Input listeners run first. Denying just the owned item's use preserves chest/door
    // interactions while preventing skull placement and right-click helmet equipping.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void use(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (PlaceholderItem.isArm(event.getItem())) event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(BlockPlaceEvent event) {
        if (PlaceholderItem.isOwned(event.getItemInHand())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void dispense(BlockDispenseEvent event) {
        if (PlaceholderItem.isOwned(event.getItem())) event.setCancelled(true);
    }

    private void protectEntityInteraction(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if (!(target instanceof ItemFrame || target instanceof ArmorStand)
                && (target.getType() == null || !"ALLAY".equals(target.getType().name()))) return;
        Player player = event.getPlayer();
        ItemStack held = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (PlaceholderItem.isOwned(held)) {
            event.setCancelled(true);
            queueCleanup(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent event) {
        if (PlaceholderItem.isOwned(event.getItemDrop().getItemStack())) event.setCancelled(true);
        queueCleanup(event.getPlayer());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(PlayerPickupItemEvent event) {
        if (PlaceholderItem.isOwned(event.getItem().getItemStack())) { event.setCancelled(true); event.getItem().remove(); }
        else cleanup(event.getPlayer());
        queueCleanup(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spawn(ItemSpawnEvent event) {
        if (PlaceholderItem.isOwned(event.getEntity().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hopper(InventoryMoveItemEvent event) {
        if (PlaceholderItem.isOwned(event.getItem())) {
            event.setCancelled(true);
            try { plugin.getScheduler().runGlobalDelayed(plugin, () -> clearOwned(event.getSource()), 1L); }
            catch (RuntimeException failure) { plugin.getLogger().log(Level.WARNING, "Could not defer stray placeholder cleanup", failure); }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void craft(PrepareItemCraftEvent event) {
        if (containsOwned(event.getInventory())) event.getInventory().setResult(null);
    }

    private static boolean containsOwned(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) if (PlaceholderItem.isOwned(item)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        event.getDrops().removeIf(PlaceholderItem::isOwned);
        cleanup(event.getEntity());
        inventoryOpen.remove(event.getEntity().getUniqueId());
        cleanupPending.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) { cleanup(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        cleanup(event.getPlayer());
        inventoryOpen.remove(event.getPlayer().getUniqueId());
        cleanupPending.remove(event.getPlayer().getUniqueId());
        nextProfileAttempt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void held(PlayerItemHeldEvent event) { cleanup(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void gameMode(PlayerGameModeChangeEvent event) { cleanup(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void world(PlayerChangedWorldEvent event) { cleanup(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent event) { cleanup(event.getPlayer()); }

    @Override public void close() {
        if (closed) return;
        generation++;
        closed = true; enabled = false; state = "stopped";
        stopTimer();
        for (Player player : plugin.getServer().getOnlinePlayers()) cleanup(player);
        inventoryOpen.clear(); cleanupPending.clear();
        nextProfileAttempt.clear();
    }

    private void stopTimer() {
        SchedulerTask previous = timer;
        timer = null;
        if (previous != null) try { previous.cancel(); }
        catch (RuntimeException failure) {
            // The generation/closed guards also invalidate a task if a scheduler
            // implementation rejects cancellation during its own shutdown.
            plugin.getLogger().log(Level.WARNING, "Could not cancel the placeholder timer", failure);
        }
    }
}
