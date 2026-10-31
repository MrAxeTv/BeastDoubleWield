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

/** Maintains the optional offhand arm and removes obsolete owned input placeholders. */
public final class PlaceholderService implements Listener, AutoCloseable {
    private final BeastDoubleWieldPlugin plugin;
    private final ArmClientSupport armClients;
    private final Set<UUID> inventoryOpen = new HashSet<>();
    private final Set<UUID> cleanupPending = new HashSet<>();
    private final Map<UUID, Integer> nextProfileAttempt = new HashMap<>();
    private final Map<UUID, ArmReplacement> replacements = new HashMap<>();
    private SchedulerTask timer;
    private boolean enabled;
    private boolean armEnabled;
    private boolean closed;
    private long generation;
    private String state = "not started";

    public PlaceholderService(BeastDoubleWieldPlugin plugin) { this.plugin = plugin; this.armClients = new ArmClientSupport(plugin); }

    public static void validate(ConfigurationSection config) {
        if (config.contains("OffhandArm.Enabled") && !(config.get("OffhandArm.Enabled") instanceof Boolean))
            throw new IllegalArgumentException("OffhandArm.Enabled must be true or false");
    }

    /** Reconfiguration is optional and must not invalidate otherwise valid gameplay settings. */
    public void reload() {
        if (closed) return;
        generation++;
        enabled = false;
        armEnabled = false;
        nextProfileAttempt.clear();
        Set<UUID> activeReplacements = new HashSet<>(replacements.keySet());
        replacements.clear();
        inventoryOpen.clear();
        stopTimer();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            cleanup(player);
            if (activeReplacements.contains(player.getUniqueId())
                    || player.getOpenInventory().getType() != InventoryType.CRAFTING)
                inventoryOpen.add(player.getUniqueId());
        }
        cleanupPending.clear();
        if (!plugin.getConfig().getBoolean("OffhandArm.Enabled", false)) { state = "disabled"; return; }
        if (!PlaceholderItem.armSupported(plugin.getServer().getBukkitVersion())) {
            state = "first-person arm unavailable (requires Paper 26.3/component API)";
            plugin.getLogger().warning("First-person arms require the verified Paper 26.3 shader/component API path; empty hands remain empty.");
            return;
        }
        try {
            long token = generation;
            SchedulerTask next = plugin.getScheduler().runGlobalRepeating(plugin, () -> {
                if (generation == token) tick();
            }, 1L, 2L);
            if (next == null || next.isDone()) throw new IllegalStateException("Offhand arm scheduler rejected its task");
            timer = next;
            enabled = true;
            armEnabled = true;
            state = "first-person arm enabled (26.3 shader pack required)";
            plugin.getLogger().info("Optional first-person offhand arm enabled. Install the matching 26.3 pack manually; otherwise its carrier can appear as a player head.");
            for (Player player : plugin.getServer().getOnlinePlayers()) HandAccess.afterOffhand(player, () -> {
                if (!closed && generation == token) reconcileSafely(player);
            });
        } catch (RuntimeException failure) {
            state = "unavailable";
            plugin.getLogger().log(Level.WARNING, "Could not start optional first-person arms", failure);
        }
    }

    public String describe() { return "offhandArm=" + state; }

    private void tick() {
        if (!enabled || closed) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (cleanupPending.contains(player.getUniqueId())) continue;
            reconcileSafely(player);
        }
    }

    private void reconcileSafely(Player player) {
        try { reconcile(player); }
        catch (RuntimeException failure) {
            cleanup(player);
            plugin.getLogger().log(Level.WARNING, "Could not reconcile a first-person arm for " + player.getUniqueId(), failure);
        }
    }

    /** Server thread only. Package visibility keeps inventory-state regression tests direct. */
    void reconcile(Player player) {
        if (HandAccess.isOffhandAction(player) || replacements.containsKey(player.getUniqueId())) return;
        PlayerInventory inventory = player.getInventory();
        boolean eligible = eligible(player) && spareStorageSlot(inventory);
        boolean keepArm = eligible && armEnabled && armClients.allowed(player);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            boolean validSlot = PlaceholderItem.isArm(item) && keepArm && slot == 40;
            if (PlaceholderItem.isOwned(item) && (!validSlot || item.getAmount() != 1))
                inventory.setItem(slot, null);
        }
        if (PlaceholderItem.isOwned(player.getItemOnCursor())) player.setItemOnCursor(null);
        if (keepArm && !inventoryOpen.contains(player.getUniqueId())
                && !cleanupPending.contains(player.getUniqueId())
                && player.getOpenInventory().getType() == InventoryType.CRAFTING
                && empty(player.getItemOnCursor()) && empty(inventory.getItemInOffHand())) {
            int now = player.getTicksLived();
            Integer next = nextProfileAttempt.get(player.getUniqueId());
            if (next == null || now >= next) {
                ItemStack arm = PlaceholderItem.createArm(player);
                if (arm != null) { inventory.setItemInOffHand(arm); nextProfileAttempt.remove(player.getUniqueId()); }
                else nextProfileAttempt.put(player.getUniqueId(), now + 100);
            }
        }
        // Never fill the main hand. Real offhand items and the optional arm already
        // provide air-use input; unsupported or missing arms leave both hands empty.
    }

    private boolean eligible(Player player) {
        if (!enabled || closed || !player.isOnline() || player.isDead() || !plugin.isEnabled(player)
                || !player.hasPermission("beastdoublewield.attack")
                || plugin.getConfig().getStringList("DisabledWorlds").contains(player.getWorld().getName())) return false;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return false;
        ConfigurationSection attack = plugin.getConfig().getConfigurationSection("Attack");
        return plugin.getConfig().getBoolean("Swing.Air", false) && attack != null
                && attack.getBoolean("AllowEmptyHand", false)
                && ItemRules.canUse(player, player.getInventory().getItemInOffHand(), attack);
    }

    // The arm occupies only the offhand; every free storage slot, selected or not,
    // remains available for real pickups.
    private static boolean spareStorageSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 36; slot++)
            if (empty(inventory.getItem(slot)) || PlaceholderItem.isOwned(inventory.getItem(slot))) return true;
        return false;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getAmount() < 1 || ServerAccess.air(item.getType());
    }

    /** Also used after a successful personal toggle and to remove stale items after a crash. */
    public void cleanup(Player player) {
        replacements.remove(player.getUniqueId());
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
            // A valid offhand arm is independent of the open view. Only an actual
            // leaked carrier in that view needs cleanup, after the open event.
            if (containsOwned(event.getInventory())) queueCleanup(player);
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
        // Personal inventory opening has no normal InventoryOpenEvent. A click
        // pauses insertion until close, but must not remove an existing arm.
        inventoryOpen.add(player.getUniqueId());
        boolean ownedHotbar = event.getHotbarButton() >= 0
                && PlaceholderItem.isOwned(player.getInventory().getItem(event.getHotbarButton()));
        boolean ownedSwap = "SWAP_OFFHAND".equals(event.getClick().name())
                && PlaceholderItem.isOwned(player.getInventory().getItemInOffHand());
        ItemStack arm = player.getInventory().getItemInOffHand();
        boolean validArm = PlaceholderItem.isArm(arm) && arm.getAmount() == 1
                && armEnabled && eligible(player) && spareStorageSlot(player.getInventory())
                && armClients.allowed(player) && !HandAccess.isOffhandAction(player);
        boolean ownSlot = player.getInventory().equals(event.getClickedInventory()) && event.getSlot() == 40;
        if (validArm && !PlaceholderItem.isOwned(event.getCursor()) && !ownedHotbar) {
            boolean replacement = false;
            if (ownSlot && PlaceholderItem.isArm(event.getCurrentItem())) {
                if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)
                    replacement = genuine(event.getCursor());
                else if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0 && event.getHotbarButton() < 9)
                    replacement = genuine(player.getInventory().getItem(event.getHotbarButton()));
                if (!replacement) { event.setCancelled(true); return; }
            } else if (ownedSwap && !PlaceholderItem.isOwned(event.getCurrentItem())) {
                replacement = event.getRawSlot() >= 0 && !ownSlot && genuine(event.getCurrentItem());
                if (!replacement) { event.setCancelled(true); return; }
            } else replacement = shiftIntoOwnedOffhand(event, player);
            if (replacement) {
                // Native click handling re-reads the slot after all listeners. Only
                // mask our disposable item; native code moves the real stack/cursor.
                if (!event.isCancelled()) beginReplacement(player, event, arm);
                return;
            }
        }
        if (PlaceholderItem.isOwned(event.getCurrentItem()) || PlaceholderItem.isOwned(event.getCursor()) || ownedHotbar || ownedSwap
                || shiftIntoOwnedOffhand(event, player)
                || (event instanceof CraftItemEvent && containsOwned(event.getInventory()))) {
            event.setCancelled(true);
            queueCleanup(player);
        }
    }

    private static boolean shiftIntoOwnedOffhand(InventoryClickEvent event, Player player) {
        // Native personal-inventory quick-move autoequips offhand equipment only
        // from storage/hotbar; container, crafting and armor sources differ.
        return event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && event.getView().getType() == InventoryType.CRAFTING
                && event.getRawSlot() >= 9 && event.getRawSlot() <= 44
                && PlaceholderItem.isArm(player.getInventory().getItemInOffHand())
                && PlaceholderItem.isOffhandEquipment(event.getCurrentItem());
    }

    private static boolean genuine(ItemStack item) {
        return !empty(item) && !PlaceholderItem.isOwned(item);
    }

    private void beginReplacement(Player player, InventoryClickEvent event, ItemStack arm) {
        UUID id = player.getUniqueId();
        if (closed || cleanupPending.contains(id) || replacements.containsKey(id)) {
            event.setCancelled(true); return;
        }
        ArmReplacement replacement = new ArmReplacement(event, arm, generation);
        replacements.put(id, replacement);
        SchedulerTask task = null;
        try {
            task = plugin.getScheduler().runPlayerDelayed(plugin, player, () ->
                    HandAccess.afterOffhand(player, () -> finishReplacement(player, replacement)), 1L,
                    () -> { if (replacements.get(id) == replacement) replacements.remove(id); });
            if (task == null || task.isDone() || replacements.get(id) != replacement)
                throw new IllegalStateException("Arm replacement recovery task was rejected");
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        } catch (RuntimeException failure) {
            event.setCancelled(true);
            restoreReplacement(player, replacement);
            if (replacements.get(id) == replacement) replacements.remove(id);
            if (task != null) try { task.cancel(); } catch (RuntimeException ignored) { }
            plugin.getLogger().log(Level.WARNING, "Could not prepare a native offhand item replacement", failure);
        }
    }

    /** Restore before a denied native click is resynchronized; later denials use the task. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void finishClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player) || !event.isCancelled()) return;
        Player player = (Player) event.getWhoClicked();
        ArmReplacement replacement = replacements.get(player.getUniqueId());
        if (replacement != null && replacement.event == event)
            HandAccess.afterOffhand(player, () -> restoreReplacement(player, replacement));
    }

    private void restoreReplacement(Player player, ArmReplacement replacement) {
        if (replacements.get(player.getUniqueId()) != replacement || generation != replacement.generation
                || closed || !armEnabled || cleanupPending.contains(player.getUniqueId())) return;
        try {
            if (PlaceholderItem.isArm(replacement.arm) && replacement.arm.getAmount() == 1
                    && empty(player.getInventory().getItemInOffHand()) && eligible(player)
                    && spareStorageSlot(player.getInventory()) && armClients.allowed(player))
                player.getInventory().setItemInOffHand(replacement.arm);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not restore an optional offhand arm", failure);
        }
    }

    private void finishReplacement(Player player, ArmReplacement replacement) {
        UUID id = player.getUniqueId();
        if (replacements.get(id) != replacement) return;
        restoreReplacement(player, replacement);
        if (replacements.get(id) != replacement) return;
        replacements.remove(id);
        if (!closed && generation == replacement.generation && player.isOnline()) {
            // If a later listener changed cancellation again, remove any stray
            // carrier from the original view; genuine items are never touched.
            clearOwned(replacement.event.getView().getTopInventory());
            if (!cleanupPending.contains(id)) reconcileSafely(player);
        }
    }

    private static final class ArmReplacement {
        private final InventoryClickEvent event;
        private final ItemStack arm;
        private final long generation;
        private ArmReplacement(InventoryClickEvent event, ItemStack arm, long generation) {
            this.event = event; this.arm = arm; this.generation = generation;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        inventoryOpen.add(player.getUniqueId());
        if (PlaceholderItem.isOwned(event.getOldCursor())
                || event.getNewItems().values().stream().anyMatch(PlaceholderItem::isOwned)
                || event.getRawSlots().stream().anyMatch(slot -> PlaceholderItem.isOwned(event.getView().getItem(slot)))) {
            event.setCancelled(true);
            queueCleanup(player);
        }
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
        // Normal drops do not move the offhand arm. Let the regular reconciliation
        // inspect the inventory after vanilla finishes the drop or cancellation.
        if (!PlaceholderItem.isOwned(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        // Vanilla may restore a cancelled owned drop into an inventory slot.
        queueCleanup(event.getPlayer());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(PlayerPickupItemEvent event) {
        if (PlaceholderItem.isOwned(event.getItem().getItemStack())) { event.setCancelled(true); event.getItem().remove(); }
        // Only the stray entity needs removal. Ordinary pickups and existing valid
        // arms require no inventory writes; the regular tick rechecks free storage.
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
        if (inventory == null) return false;
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
    public void held(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // A hotbar change does not move the offhand. Revalidate without deleting a
        // valid arm, and wait for native hand restoration if a listener changes slots.
        HandAccess.afterOffhand(player, () -> {
            if (!cleanupPending.contains(player.getUniqueId())) reconcileSafely(player);
        });
    }

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
        inventoryOpen.clear(); cleanupPending.clear(); replacements.clear();
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
