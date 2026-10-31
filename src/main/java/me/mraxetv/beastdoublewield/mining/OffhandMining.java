package me.mraxetv.beastdoublewield.mining;
import me.mraxetv.beastdoublewield.compat.ServerAccess;
import me.mraxetv.beastdoublewield.placeholder.PlaceholderItem;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.api.OffhandBlockBreakEvent;
import me.mraxetv.beastdoublewield.util.HandAccess;
import me.mraxetv.beastdoublewield.util.ItemRules;
import me.mraxetv.beastlib.api.scheduler.SchedulerTask;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Server-thread mining sessions. Right-click packets refresh a bounded progress
 * window and a longer inactivity lease; there is no generic right-button-up event.
 * The server supplies both mining speed and the final native block break.
 */
public final class OffhandMining implements Listener, AutoCloseable {
    private final BeastDoubleWieldPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Integer> restartAfter = new HashMap<>();
    private final Set<UUID> completing = new HashSet<>();
    private long generation;

    public OffhandMining(BeastDoubleWieldPlugin plugin) {
        this.plugin = plugin;
    }

    /** Starts mining, or refreshes the input lease without resetting progress. */
    public boolean begin(Player player, Block block) {
        if (block == null || !eligible(player, block)) {
            stop(player.getUniqueId());
            return false;
        }
        UUID id = player.getUniqueId();
        if (completing.contains(id)) return true;
        Integer ready = restartAfter.get(id);
        if (ready != null) {
            // Subtraction also preserves this short interval across int tick wrap.
            if (ready - player.getTicksLived() > 0) return true;
            restartAfter.remove(id);
        }
        Session previous = sessions.get(id);
        if (previous != null && sameBlock(previous.block, block) && unchanged(previous)) {
            previous.silentTicks = 0;
            return true;
        }
        stop(id);
        Session session = new Session(player, block, generation);
        sessions.put(id, session);
        try {
            BlockDamageEvent damage = HandAccess.withOffhand(player, () -> {
                BlockDamageEvent intent = new BlockDamageEvent(player, block, PlaceholderItem.logicalItem(session.tool.clone()), false);
                plugin.getServer().getPluginManager().callEvent(intent);
                return intent;
            });
            if (damage.isCancelled() || sessions.get(id) != session || session.generation != generation
                    || !unchanged(session) || !eligible(player, block)) {
                finish(session, true); return true;
            }
            session.instant = damage.getInstaBreak();
            session.task = plugin.getScheduler().runEntityRepeating(plugin, player,
                    () -> tick(session), 1L, 1L, () -> retire(session));
            if (session.task.isDone()) {
                finish(session, true);
                return false;
            }
            return true;
        } catch (RuntimeException failure) {
            finish(session, true);
            plugin.getLogger().log(Level.WARNING, "Could not schedule offhand mining for " + id, failure);
            return false;
        }
    }

    public void stop(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session != null) finish(session, true);
    }

    /** Clears active sessions; this instance can be reused after a config reload. */
    @Override
    public void close() {
        generation++;
        for (Session session : new ArrayList<>(sessions.values())) finish(session, true);
        restartAfter.clear();
    }

    private void tick(Session session) {
        if (sessions.get(session.player.getUniqueId()) != session) return;
        try {
            int lease = Math.max(1, Math.min(40, plugin.getConfig().getInt("Mine.InputTimeoutTicks", 8)));
            if (++session.silentTicks > lease || !unchanged(session)
                    || !eligible(session.player, session.block)) {
                finish(session, true);
                return;
            }
            int progressWindow = Math.min(lease, Math.max(1, Math.min(40,
                    plugin.getConfig().getInt("Mine.InputProgressTicks", 4))));
            // Hold partial progress for late input, but never mine or catch up
            // through the entire inactivity grace period after a possible release.
            if (session.silentTicks > progressWindow) return;
            float increment = session.instant || session.player.getGameMode() == GameMode.CREATIVE ? 1.0F
                    : MiningAttributes.withOffhand(session.player, () -> HandAccess.withOffhand(session.player,
                            () -> ServerAccess.breakSpeed(session.player, session.block)));
            // Zero/negative speed includes unbreakable blocks. Positive infinity is
            // legitimate for zero-hardness blocks, and means an immediate break.
            if (Float.isNaN(increment) || increment <= 0.0F) {
                finish(session, true);
                return;
            }
            session.progressSteps++;
            session.progress += increment;
            if (++session.animationTicks % 4 == 1) {
                ServerAccess.swing(session.player);
                if (plugin.getConfig().getBoolean("Mine.Sounds", true)) {
                    ServerAccess.hitSound(session.player, session.block);
                }
            }
            if (session.progress >= 1.0F) {
                complete(session);
            } else {
                ServerAccess.crack(session.player, session.block, session.progress);
            }
        } catch (RuntimeException failure) {
            finish(session, true);
            plugin.getLogger().log(Level.WARNING,
                    "Stopped offhand mining after an operation failed for " + session.player.getUniqueId(), failure);
        }
    }

    private void complete(Session session) {
        // Remove first: a listener may re-enter this service or reload the plugin.
        finish(session, true);
        OffhandBlockBreakEvent event = new OffhandBlockBreakEvent(session.player,
                session.block, PlaceholderItem.logicalItem(session.tool.clone()));
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || session.generation != generation || sessions.containsKey(session.playerId)
                || !unchanged(session) || !eligible(session.player, session.block)) return;
        // Native listeners can re-enter the service. Keep a completion barrier
        // until the break result establishes whether a restart pause is needed.
        if (!completing.add(session.playerId)) return;
        try {
            Runnable breakSound = null;
            try {
                breakSound = ServerAccess.prepareBreakSound(session.player, session.block);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.FINE, "Could not prepare an offhand block break sound", failure);
            }
            if (HandAccess.withOffhand(session.player, () -> ServerAccess.breakBlock(session.player, session.block))) {
                if (session.generation == generation && session.player.isOnline() && !session.player.isDead()
                        && (session.progressSteps > 1 || session.player.getGameMode() == GameMode.CREATIVE)) {
                    // Native held left-click skips five continuation ticks after
                    // a progressive break. True instant Survival breaks omit it.
                    restartAfter.put(session.playerId, session.player.getTicksLived() + 6);
                }
                plugin.recordBlockBreak();
                // Native destruction notifies nearby players but excludes the
                // actor, whose ordinary left-click client predicts this sound.
                // Right-click mining needs that missing actor-only feedback.
                if (breakSound != null && session.player.isOnline()
                        && session.player.getWorld().equals(session.block.getWorld())) {
                    try {
                        breakSound.run();
                    } catch (RuntimeException failure) {
                        plugin.getLogger().log(Level.FINE, "Could not play an offhand block break sound", failure);
                    }
                }
            }
        } finally { completing.remove(session.playerId); }
        // breakBlock dispatches BlockBreakEvent and BlockDropItemEvent and applies
        // vanilla loot, experience, tool damage and protection checks. No manual
        // block removal or duplicate drops happen here, including on cancellation.
    }

    private boolean eligible(Player player, Block block) {
        ConfigurationSection rules = plugin.getConfig().getConfigurationSection("Mine");
        if (rules == null || !rules.getBoolean("Enabled", true) || !plugin.isEnabled(player)
                || !player.isOnline() || player.isDead() || !player.hasPermission("beastdoublewield.mine")
                || !player.getWorld().equals(block.getWorld())
                || plugin.getConfig().getStringList("DisabledWorlds").contains(player.getWorld().getName())
                || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                || block.isEmpty() || block.isLiquid()) return false;
        ItemStack tool = player.getInventory().getItemInOffHand();
        if (!ItemRules.canUse(player, tool, rules)
                || (rules.getBoolean("CorrectToolOnly", false) && !ServerAccess.preferredTool(block, PlaceholderItem.logicalItem(tool)))) return false;
        double reach = Math.max(1.0, Math.min(6.0, rules.getDouble("Reach", 4.5)));
        if (!Double.isFinite(reach)) return false;
        Block hit = ServerAccess.targetBlock(player, reach);
        return hit != null && sameBlock(block, hit);
    }

    private boolean unchanged(Session session) {
        return session.player.getWorld().equals(session.block.getWorld())
                && session.player.getInventory().getHeldItemSlot() == session.heldSlot
                && session.tool.equals(session.player.getInventory().getItemInOffHand())
                && session.block.getWorld().isChunkLoaded(session.block.getX() >> 4, session.block.getZ() >> 4)
                && session.blockData.equals(ServerAccess.blockKey(session.block));
    }

    private static boolean sameBlock(Block first, Block second) {
        return first.getWorld().equals(second.getWorld()) && first.getX() == second.getX()
                && first.getY() == second.getY() && first.getZ() == second.getZ();
    }

    private void finish(Session session, boolean resetAnimation) {
        if (sessions.get(session.player.getUniqueId()) != session) return;
        sessions.remove(session.player.getUniqueId());
        if (session.task != null) session.task.cancel();
        if (resetAnimation && session.player.isOnline()
                && session.player.getWorld().equals(session.block.getWorld())) {
            try {
                ServerAccess.crack(session.player, session.block, 0.0F);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.FINE, "Could not clear an offhand mining animation", failure);
            }
        }
    }

    // A retired entity must not be accessed from the retirement callback.
    private void retire(Session session) {
        if (sessions.get(session.playerId) == session) sessions.remove(session.playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stop(id);
        restartAfter.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        stop(id);
        restartAfter.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { stop(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldItem(PlayerItemHeldEvent event) { stop(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { stop(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { stop(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) { stop(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) { stop(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) { stop(event.getWhoClicked().getUniqueId()); }

    private static final class Session {
        private final Player player;
        private final UUID playerId;
        private final Block block;
        private final String blockData;
        private final ItemStack tool;
        private final int heldSlot;
        private final long generation;
        private SchedulerTask task;
        private int silentTicks;
        private int animationTicks;
        private float progress;
        private int progressSteps;
        private boolean instant;

        private Session(Player player, Block block, long generation) {
            this.player = player;
            this.playerId = player.getUniqueId();
            this.block = block;
            this.blockData = ServerAccess.blockKey(block);
            this.tool = player.getInventory().getItemInOffHand().clone();
            this.heldSlot = player.getInventory().getHeldItemSlot();
            this.generation = generation;
        }
    }
}
