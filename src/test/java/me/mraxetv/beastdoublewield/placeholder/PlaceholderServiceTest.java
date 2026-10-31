package me.mraxetv.beastdoublewield.placeholder;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.util.HandAccess;
import me.mraxetv.beastdoublewield.util.ItemRules;
import me.mraxetv.beastlib.api.scheduler.SchedulerService;
import me.mraxetv.beastlib.api.scheduler.SchedulerTask;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceholderServiceTest {
    private BeastDoubleWieldPlugin plugin;
    private Player player;
    private PlayerInventory inventory;
    private Inventory top;
    private InventoryView view;
    private YamlConfiguration config;
    private PlaceholderService service;
    private SchedulerService scheduler;
    private final ItemStack[] contents = new ItemStack[41];
    private final ItemStack[] topContents = new ItemStack[5];
    private final AtomicReference<ItemStack> cursor = new AtomicReference<>();
    private final Set<ItemStack> owned = Collections.newSetFromMap(new IdentityHashMap<ItemStack, Boolean>());
    private final Set<ItemStack> arms = Collections.newSetFromMap(new IdentityHashMap<ItemStack, Boolean>());
    private final List<Runnable> deferred = new ArrayList<>();
    private MockedStatic<PlaceholderItem> items;
    private MockedConstruction<ArmClientSupport> clients;

    @BeforeEach void setup() {
        // These inventory tests use a positively verified client; protocol paths
        // and API failures are exercised separately by ArmClientSupportTest.
        clients = mockConstruction(ArmClientSupport.class,
                (client, context) -> when(client.allowed(any(Player.class))).thenReturn(true));
        items = mockStatic(PlaceholderItem.class);
        items.when(PlaceholderItem::supported).thenReturn(true);
        items.when(() -> PlaceholderItem.isOwned(any())).thenAnswer(call -> owned.contains(call.getArgument(0)));
        items.when(() -> PlaceholderItem.isArm(any())).thenAnswer(call -> arms.contains(call.getArgument(0)));
        plugin = mock(BeastDoubleWieldPlugin.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        top = mock(Inventory.class);
        view = mock(InventoryView.class);
        scheduler = mock(SchedulerService.class);
        Server server = mock(Server.class);
        World world = mock(World.class);
        config = new YamlConfiguration();
        config.set("OffhandArm.Enabled", false);
        config.set("Swing.Air", true);
        config.set("Attack.Enabled", true);
        config.set("Attack.AllowEmptyHand", true);
        config.set("Attack.GameModes", Arrays.asList("SURVIVAL", "ADVENTURE"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getScheduler()).thenReturn(scheduler);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PlaceholderServiceTest"));
        when(plugin.isEnabled(player)).thenReturn(true);
        doReturn(Collections.singletonList(player)).when(server).getOnlinePlayers();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("beastdoublewield.attack")).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(view.getType()).thenReturn(InventoryType.CRAFTING);
        when(view.getTopInventory()).thenReturn(top);
        when(inventory.getSize()).thenReturn(contents.length);
        when(inventory.getHeldItemSlot()).thenReturn(2);
        when(inventory.getItem(anyInt())).thenAnswer(call -> contents[(Integer) call.getArgument(0)]);
        when(inventory.getContents()).thenReturn(contents);
        when(inventory.getItemInOffHand()).thenAnswer(call -> contents[40]);
        when(inventory.getItemInMainHand()).thenAnswer(call -> contents[2]);
        doAnswer(call -> { contents[(Integer) call.getArgument(0)] = call.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), any());
        doAnswer(call -> { contents[40] = call.getArgument(0); return null; }).when(inventory).setItemInOffHand(any());
        when(top.getSize()).thenReturn(topContents.length);
        when(top.getItem(anyInt())).thenAnswer(call -> topContents[(Integer) call.getArgument(0)]);
        when(top.getContents()).thenReturn(topContents);
        doAnswer(call -> { topContents[(Integer) call.getArgument(0)] = call.getArgument(1); return null; })
                .when(top).setItem(anyInt(), any());
        when(player.getItemOnCursor()).thenAnswer(call -> cursor.get());
        doAnswer(call -> { cursor.set(call.getArgument(0)); return null; }).when(player).setItemOnCursor(any());
        when(scheduler.runGlobalRepeating(eq(plugin), any(), eq(1L), eq(2L))).thenReturn(mock(SchedulerTask.class));
        when(scheduler.runPlayerDelayed(eq(plugin), eq(player), any(), eq(1L), any())).thenAnswer(call -> {
            deferred.add(call.getArgument(2)); return mock(SchedulerTask.class);
        });
        when(scheduler.runGlobalDelayed(eq(plugin), any(), eq(1L))).thenAnswer(call -> {
            deferred.add(call.getArgument(1)); return mock(SchedulerTask.class);
        });
        service = new PlaceholderService(plugin);
        service.reload();
    }

    @AfterEach void cleanup() {
        service.close();
        try { items.verify(PlaceholderItem::create, never()); }
        finally { items.close(); clients.close(); }
    }

    private ItemStack ownedItem() {
        ItemStack item = new ItemStack(Material.FEATHER, 1);
        owned.add(item);
        return item;
    }

    private ItemStack ownedArm() {
        ItemStack item = new ItemStack(Material.FEATHER);
        owned.add(item); arms.add(item); return item;
    }

    private void enableArms() {
        enableArms(true);
    }

    private void enableArms(boolean completeProfile) {
        config.set("OffhandArm.Enabled", true);
        when(plugin.getServer().getBukkitVersion()).thenReturn("26.3-R0.1-SNAPSHOT");
        items.when(() -> PlaceholderItem.armSupported("26.3-R0.1-SNAPSHOT")).thenReturn(true);
        items.when(() -> PlaceholderItem.createArm(player)).thenAnswer(call -> completeProfile ? ownedArm() : null);
        service.reload();
    }

    @Test void offhandArmFlagStartsTheArmAndDisablingCleansOnlyOwnedItems() {
        assertEquals("offhandArm=disabled", service.describe());
        SchedulerTask task = mock(SchedulerTask.class);
        when(scheduler.runGlobalRepeating(eq(plugin), any(), eq(1L), eq(2L))).thenReturn(task);
        ItemStack genuine = new ItemStack(Material.FEATHER, 12);
        contents[2] = genuine;
        enableArms();
        assertTrue(arms.contains(contents[40]));
        assertEquals("offhandArm=first-person arm enabled (26.3 shader pack required)", service.describe());
        assertFalse(config.contains("Placeholder"), "The new flag must work without either obsolete setting");

        config.set("OffhandArm.Enabled", false);
        service.reload();
        service.reconcile(player);
        assertEquals("offhandArm=disabled", service.describe());
        assertNull(contents[40]);
        assertSame(genuine, contents[2]);
        verify(task).cancel();
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @Test void offhandArmFlagRequiresABoolean() {
        YamlConfiguration proposed = new YamlConfiguration();
        assertDoesNotThrow(() -> PlaceholderService.validate(proposed));
        for (boolean enabled : new boolean[]{false, true}) {
            proposed.set("OffhandArm.Enabled", enabled);
            assertDoesNotThrow(() -> PlaceholderService.validate(proposed));
        }
        for (Object invalid : Arrays.asList("true", 1, Collections.singletonList(true))) {
            proposed.set("OffhandArm.Enabled", invalid);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> PlaceholderService.validate(proposed));
            assertEquals("OffhandArm.Enabled must be true or false", failure.getMessage());
        }
    }

    @Test void armUsesOnlyEmptyOffhandAndLeavesMainHandActuallyEmpty() {
        enableArms();
        service.reconcile(player);
        ItemStack arm = contents[40];
        assertTrue(arms.contains(arm)); assertNull(contents[2]);
        service.reconcile(player); assertSame(arm, contents[40]);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        contents[40] = sword;
        service.reconcile(player);
        assertSame(sword, contents[40]); assertNull(contents[2]);
        contents[2] = sword; contents[40] = null;
        service.reconcile(player);
        assertSame(sword, contents[2]); assertTrue(arms.contains(contents[40]));
    }

    @Test void unverifiedClientGetsNoCarrierOrProfileLookup() {
        when(clients.constructed().get(0).allowed(player)).thenReturn(false);
        enableArms();
        service.reconcile(player);
        assertNull(contents[2]);
        assertNull(contents[40]);
        items.verify(() -> PlaceholderItem.createArm(player), never());
    }

    @Test void losingClientCompatibilityRemovesOnlyOwnedArmAndPreservesRealItems() {
        ItemStack main = new ItemStack(Material.DIAMOND_AXE);
        contents[2] = main;
        enableArms();
        assertTrue(arms.contains(contents[40]));
        when(clients.constructed().get(0).allowed(player)).thenReturn(false);
        service.reconcile(player);
        assertNull(contents[40]);
        assertSame(main, contents[2]);
        ItemStack offhand = new ItemStack(Material.DIAMOND_SWORD);
        contents[40] = offhand;
        service.reconcile(player);
        assertSame(offhand, contents[40]);
        assertSame(main, contents[2]);
    }

    @Test void unsupportedOrUnresolvedArmLeavesBothHandsEmptyAndRetriesProfile() {
        config.set("OffhandArm.Enabled", true);
        service.reload(); service.reconcile(player);
        assertNull(contents[40]); assertNull(contents[2]);
        assertTrue(service.describe().contains("unavailable"));
        enableArms(false);
        service.reconcile(player); service.reconcile(player);
        assertNull(contents[40]); assertNull(contents[2]);
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
        when(player.getTicksLived()).thenReturn(99);
        service.reconcile(player);
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
        when(player.getTicksLived()).thenReturn(100);
        items.when(() -> PlaceholderItem.createArm(player)).thenAnswer(call -> ownedArm());
        service.reconcile(player);
        assertNull(contents[2]); assertTrue(arms.contains(contents[40]));
        items.verify(() -> PlaceholderItem.createArm(player), times(2));
    }

    @Test void armHonorsAirWhitelistEmptyHandPermissionAndCleanup() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        config.set("Attack.Materials.Mode", "WHITELIST");
        config.set("Attack.Materials.Patterns", Collections.singletonList("AIR"));
        // Use a fresh section because configured filters are immutable until reload validation.
        YamlConfiguration rules = new YamlConfiguration();
        rules.set("AllowEmptyHand", true); rules.set("GameModes", Collections.singletonList("SURVIVAL"));
        rules.set("Materials.Mode", "WHITELIST"); rules.set("Materials.Patterns", Collections.singletonList("AIR"));
        assertTrue(ItemRules.canUse(player, arm, rules));
        assertFalse(ItemRules.isWeapon(arm));
        rules.set("AllowEmptyHand", false); assertFalse(ItemRules.canUse(player, arm, rules));
        config.set("Attack.AllowEmptyHand", false);
        service.reconcile(player); assertNull(contents[40]);
        config.set("Attack.AllowEmptyHand", true); service.reconcile(player);
        service.close(); assertNull(contents[40]); assertNull(contents[2]);
    }

    @Test void armCannotPlaceEquipOrDispenseButBlockUseRemainsAvailable() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        org.bukkit.event.player.PlayerInteractEvent use = mock(org.bukkit.event.player.PlayerInteractEvent.class);
        when(use.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
        when(use.getItem()).thenReturn(arm);
        service.use(use);
        verify(use).setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        verify(use, never()).setUseInteractedBlock(any());
        when(use.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_AIR);
        service.use(use); verify(use, times(2)).setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        org.bukkit.event.block.BlockPlaceEvent place = mock(org.bukkit.event.block.BlockPlaceEvent.class);
        when(place.getItemInHand()).thenReturn(arm); service.place(place); verify(place).setCancelled(true);
        org.bukkit.event.block.BlockDispenseEvent dispense = mock(org.bukkit.event.block.BlockDispenseEvent.class);
        when(dispense.getItem()).thenReturn(arm); service.dispense(dispense); verify(dispense).setCancelled(true);
    }

    private void flush() {
        List<Runnable> tasks = new ArrayList<>(deferred);
        deferred.clear();
        tasks.forEach(Runnable::run);
    }

    private InventoryClickEvent inventoryClick(ClickType click, InventoryAction action, ItemStack current) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        AtomicReference<Boolean> cancelled = new AtomicReference<>(false);
        when(event.isCancelled()).thenAnswer(call -> cancelled.get());
        doAnswer(call -> { cancelled.set(call.getArgument(0)); return null; }).when(event).setCancelled(anyBoolean());
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCurrentItem()).thenReturn(current);
        when(event.getCursor()).thenAnswer(call -> cursor.get());
        when(event.getClick()).thenReturn(click);
        when(event.getAction()).thenReturn(action);
        when(event.getHotbarButton()).thenReturn(-1);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlot()).thenReturn(10);
        return event;
    }

    private void closeView() {
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        service.closeInventory(close);
    }

    private InventoryClickEvent offhandClick(ClickType click, InventoryAction action) {
        InventoryClickEvent event = inventoryClick(click, action, contents[40]);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getSlot()).thenReturn(40);
        when(event.getRawSlot()).thenReturn(45);
        return event;
    }

    @Test void disabledArmLeavesBothHandsEmptyAndNeverReplacesGenuineFeathersOrOtherItems() {
        service.reconcile(player);
        assertNull(contents[2]); assertNull(contents[40]);
        service.reconcile(player);
        assertNull(contents[2]); assertNull(contents[40]);
        assertTrue(service.describe().contains("disabled"));
        ItemStack genuineFeather = new ItemStack(Material.FEATHER, 1);
        contents[2] = genuineFeather;
        service.reconcile(player);
        assertSame(genuineFeather, contents[2]);
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        contents[2] = diamond;
        service.reconcile(player);
        assertSame(diamond, contents[2]);
    }

    @Test void realOffhandToolNeverCausesAMainHandPlaceholder() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        contents[40] = tool;
        service.reconcile(player);
        assertSame(tool, contents[40]); assertNull(contents[2]);
        enableArms();
        service.reconcile(player); service.reconcile(player);
        assertSame(tool, contents[40]); assertNull(contents[2]);
        items.verify(() -> PlaceholderItem.createArm(player), never());
    }

    @Test void hotbarChangesPreserveExistingArmAndCleanOnlyMisplacedCarriers() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        contents[3] = genuine;
        contents[2] = ownedItem(); contents[8] = ownedArm();
        clearInvocations(inventory);
        service.held(new PlayerItemHeldEvent(player, 2, 3));
        assertSame(arm, contents[40], "A hotbar scroll must not remove the offhand arm");
        when(inventory.getHeldItemSlot()).thenReturn(3);
        service.reconcile(player);
        assertSame(arm, contents[40]); assertSame(genuine, contents[3]);
        assertNull(contents[2]); assertNull(contents[8]);
        verify(inventory, never()).setItemInOffHand(any());
        verify(inventory, never()).setItem(eq(40), any());
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @Test void selectingTheOnlyFreeStorageSlotDoesNotRemoveTheArm() {
        enableArms();
        ItemStack genuine = new ItemStack(Material.STONE, 64);
        Arrays.fill(contents, 0, 36, genuine);
        contents[3] = null;
        service.reconcile(player);
        ItemStack arm = contents[40];
        assertTrue(arms.contains(arm));
        when(inventory.getHeldItemSlot()).thenReturn(3);
        service.reconcile(player);
        assertSame(arm, contents[40], "The empty main-hand slot is still free storage");
        assertNull(contents[3]);
    }

    @Test void hotbarReconciliationWaitsForNativeHandRestoration() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        contents[2] = genuine;
        HandAccess.withOffhand(player, () -> {
            service.held(new PlayerItemHeldEvent(player, 2, 3));
            assertEquals(Material.AIR, contents[2].getType());
            assertSame(genuine, contents[40]);
            return null;
        });
        assertSame(genuine, contents[2]); assertSame(arm, contents[40]);
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @Test void hotbarChangePreservesRealOffhandAndDisabledCleanupStillRemovesStaleCarriers() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        contents[40] = tool; enableArms(); contents[2] = ownedItem();
        service.held(new PlayerItemHeldEvent(player, 2, 3));
        assertSame(tool, contents[40]); assertNull(contents[2]);
        config.set("OffhandArm.Enabled", false); service.reload();
        contents[40] = ownedArm(); contents[2] = ownedItem(); contents[5] = tool;
        service.held(new PlayerItemHeldEvent(player, 2, 3));
        assertNull(contents[40]); assertNull(contents[2]); assertSame(tool, contents[5]);
        items.verify(() -> PlaceholderItem.createArm(player), never());
    }

    @Test void reconciliationRemovesStaleInputFeathersAndPreservesOrdinaryFeathers() {
        enableArms();
        ItemStack genuine = new ItemStack(Material.FEATHER, 12);
        contents[2] = ownedItem(); contents[8] = ownedItem(); contents[9] = genuine;
        service.reconcile(player);
        assertNull(contents[2]); assertNull(contents[8]); assertSame(genuine, contents[9]);
        assertTrue(arms.contains(contents[40]));
        contents[2] = genuine;
        service.reconcile(player);
        assertSame(genuine, contents[2]);
    }

    @Test void disablingRemovesOnlyOwnedInventoryCursorAndContainerItems() {
        ItemStack genuine = new ItemStack(Material.FEATHER, 12);
        contents[2] = ownedItem(); contents[6] = genuine; contents[40] = ownedItem();
        cursor.set(ownedItem()); topContents[1] = ownedItem(); topContents[3] = genuine;
        config.set("OffhandArm.Enabled", false);
        service.reload();
        service.reconcile(player);
        assertNull(contents[2]); assertNull(contents[40]); assertNull(cursor.get()); assertNull(topContents[1]);
        assertSame(genuine, contents[6]); assertSame(genuine, topContents[3]);
    }

    @Test void armRequiresAFreeStorageSlotButNeverOccupiesIt() {
        enableArms();
        ItemStack genuine = new ItemStack(Material.STONE, 64);
        Arrays.fill(contents, 0, 36, genuine);
        service.reconcile(player);
        assertSame(genuine, contents[2]); assertNull(contents[40]);
        contents[2] = null;
        service.reconcile(player);
        assertNull(contents[2]); assertTrue(arms.contains(contents[40]));
        contents[2] = genuine;
        service.reconcile(player);
        assertSame(genuine, contents[2]); assertNull(contents[40]);
    }

    @Test void personalPermissionGameModeAndConfigurationGatesRemoveExistingCarrier() {
        enableArms();
        service.reconcile(player);
        assertTrue(arms.contains(contents[40]));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        service.reconcile(player); assertNull(contents[40]);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        service.reconcile(player); assertTrue(arms.contains(contents[40]));
        when(plugin.isEnabled(player)).thenReturn(false);
        service.reconcile(player); assertNull(contents[40]);
        when(plugin.isEnabled(player)).thenReturn(true);
        service.reconcile(player); assertTrue(arms.contains(contents[40]));
        when(player.hasPermission("beastdoublewield.attack")).thenReturn(false);
        service.reconcile(player); assertNull(contents[40]);
        when(player.hasPermission("beastdoublewield.attack")).thenReturn(true);
        service.reconcile(player); assertTrue(arms.contains(contents[40]));
        config.set("Swing.Air", false);
        service.reconcile(player); assertNull(contents[40]);
        config.set("Swing.Air", true);
        service.reconcile(player); assertTrue(arms.contains(contents[40]));
        config.set("Attack.AllowEmptyHand", false);
        service.reconcile(player); assertNull(contents[40]);
        config.set("Attack.AllowEmptyHand", true);
        service.reconcile(player); assertTrue(arms.contains(contents[40]));
        config.set("DisabledWorlds", Collections.singletonList("world"));
        service.reconcile(player); assertNull(contents[40]); assertNull(contents[2]);
    }

    @Test void inventoryClickCancelsOwnedTransferThenCleansAfterEventWithoutTouchingRealCursor() {
        contents[2] = ownedItem();
        ItemStack carrier = contents[2];
        ItemStack genuine = new ItemStack(Material.DIAMOND);
        cursor.set(genuine);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCurrentItem()).thenReturn(carrier);
        when(event.getCursor()).thenReturn(genuine);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getHotbarButton()).thenReturn(-1);
        service.click(event);
        verify(event).setCancelled(true);
        assertSame(carrier, contents[2], "Never rewrite inventory slots inside the click handler");
        flush();
        assertNull(contents[2]); assertSame(genuine, cursor.get());
        cursor.set(null);
        service.reconcile(player); assertNull(contents[2], "Keep the aid suspended while inventory is open");
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        service.closeInventory(close);
        service.reconcile(player);
        assertNull(contents[2], "Obsolete main-hand placeholders must never return");
    }

    @Test void armInventoryTransferPausesThenResumesWithoutAMainHandPlaceholder() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND);
        cursor.set(genuine);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCurrentItem()).thenReturn(arm);
        when(event.getCursor()).thenReturn(genuine);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getHotbarButton()).thenReturn(-1);
        service.click(event);
        verify(event).setCancelled(true);
        assertSame(arm, contents[40]);
        flush();
        assertNull(contents[40]); assertNull(contents[2]); assertSame(genuine, cursor.get());
        cursor.set(null);
        service.reconcile(player);
        assertNull(contents[40]); assertNull(contents[2]);
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        service.closeInventory(close);
        service.reconcile(player);
        assertTrue(arms.contains(contents[40])); assertNull(contents[2]);
    }

    @Test void emptyOffhandClicksAndEmptyInventoryFSwapKeepOriginalArm() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        clearInvocations(inventory, player);
        for (ClickType type : Arrays.asList(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT,
                ClickType.DROP, ClickType.CONTROL_DROP, ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND)) {
            InventoryClickEvent click = offhandClick(type, InventoryAction.PICKUP_ALL);
            if (type == ClickType.NUMBER_KEY) when(click.getHotbarButton()).thenReturn(1);
            service.click(click); flush(); service.reconcile(player);
            verify(click).setCancelled(true);
            assertSame(arm, contents[40], "An empty/no-op arm click must not delete it");
        }
        InventoryClickEvent swap = inventoryClick(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, null);
        service.click(swap); flush(); service.reconcile(player);
        verify(swap).setCancelled(true); assertSame(arm, contents[40]);
        verify(inventory, never()).setItem(anyInt(), any());
        verify(inventory, never()).setItemInOffHand(any());
        verify(player, never()).setItemOnCursor(any());
        verify(scheduler, never()).runPlayerDelayed(eq(plugin), eq(player), any(), eq(1L), any());
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @Test void reloadClearsStaleInventoryTrackingAndRestoresOnlineArmImmediately() {
        enableArms(); service.reconcile(player);
        service.click(inventoryClick(ClickType.LEFT, InventoryAction.PICKUP_ALL, new ItemStack(Material.STONE)));
        contents[40] = null;
        service.reload();
        assertTrue(arms.contains(contents[40]), "Reload must reconcile eligible online players despite stale click tracking");
    }

    @Test void inventoryFSwapLetsNativeMoveGenuineSourceWithoutMovingItInListener() {
        enableArms();
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        contents[10] = genuine;
        // Native restrictions can report NOTHING while the feather occupies the
        // offhand; the native handler rechecks the real slot after the mask.
        InventoryClickEvent click = inventoryClick(ClickType.SWAP_OFFHAND, InventoryAction.NOTHING, genuine);
        clearInvocations(inventory, player);
        service.click(click);
        assertFalse(click.isCancelled()); assertEquals(Material.AIR, contents[40].getType());
        assertSame(genuine, contents[10]); assertNull(cursor.get());
        verify(inventory, never()).setItem(anyInt(), any());
        verify(player, never()).setItemOnCursor(any());
        contents[40] = genuine; contents[10] = null;
        service.finishClick(click); flush(); service.reconcile(player);
        assertSame(genuine, contents[40]); assertNull(contents[10]);
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @Test void cursorAndNumberKeyReplacementMaskOnlyOwnedDestination() {
        enableArms();
        for (ClickType type : Arrays.asList(ClickType.LEFT, ClickType.RIGHT, ClickType.NUMBER_KEY)) {
            closeView(); contents[40] = null; cursor.set(null); service.reconcile(player);
            ItemStack genuine = new ItemStack(Material.DIAMOND, 12);
            InventoryClickEvent click = offhandClick(type, InventoryAction.SWAP_WITH_CURSOR);
            if (type == ClickType.NUMBER_KEY) {
                contents[1] = genuine; when(click.getHotbarButton()).thenReturn(1);
            } else cursor.set(genuine);
            clearInvocations(inventory, player);
            service.click(click);
            assertFalse(click.isCancelled()); assertEquals(Material.AIR, contents[40].getType());
            assertSame(genuine, type == ClickType.NUMBER_KEY ? contents[1] : cursor.get());
            verify(inventory, never()).setItem(anyInt(), any());
            verify(player, never()).setItemOnCursor(any());
            // Model native semantics: right click places one; other paths move all.
            if (type == ClickType.RIGHT) {
                contents[40] = new ItemStack(Material.DIAMOND, 1);
                cursor.set(new ItemStack(Material.DIAMOND, 11));
            } else {
                contents[40] = genuine;
                if (type == ClickType.NUMBER_KEY) contents[1] = null; else cursor.set(null);
            }
            service.finishClick(click); flush();
            assertEquals(type == ClickType.RIGHT ? 1 : 12, contents[40].getAmount());
            if (type == ClickType.RIGHT) assertEquals(11, cursor.get().getAmount());
        }
    }

    @Test void earlierCancellationLeavesArmAndRealCursorUntouched() {
        enableArms();
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND);
        cursor.set(genuine);
        InventoryClickEvent click = offhandClick(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
        click.setCancelled(true);
        clearInvocations(inventory, player);
        service.click(click); service.finishClick(click); flush();
        assertTrue(click.isCancelled()); assertSame(arm, contents[40]); assertSame(genuine, cursor.get());
        verify(inventory, never()).setItemInOffHand(any());
        verify(player, never()).setItemOnCursor(any());
        verify(scheduler, never()).runPlayerDelayed(eq(plugin), eq(player), any(), eq(1L), any());
    }

    @Test void laterCancellationRestoresSameArmAtMonitorOrNextTick() {
        enableArms();
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND);
        cursor.set(genuine);
        for (boolean atMonitor : new boolean[]{true, false}) {
            InventoryClickEvent click = offhandClick(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
            service.click(click);
            assertEquals(Material.AIR, contents[40].getType());
            if (!atMonitor) service.finishClick(click);
            click.setCancelled(true);
            if (atMonitor) {
                service.finishClick(click);
                assertSame(arm, contents[40], "A denied click must be resynchronized with the original arm");
            }
            flush();
            assertSame(arm, contents[40]); assertSame(genuine, cursor.get());
        }
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @Test void nativeNoOpAndViewChangeRestoreArmButRealReplacementAlwaysWins() {
        enableArms();
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        contents[10] = genuine;
        InventoryClickEvent noOp = inventoryClick(ClickType.SWAP_OFFHAND, InventoryAction.NOTHING, genuine);
        service.click(noOp);
        when(view.getType()).thenReturn(InventoryType.CHEST);
        // The native menu may decline a move or discard a packet after a view change.
        service.finishClick(noOp); flush();
        assertSame(arm, contents[40]); assertSame(genuine, contents[10]);
        InventoryClickEvent replaced = inventoryClick(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, genuine);
        service.click(replaced);
        ItemStack supplied = new ItemStack(Material.SHIELD);
        contents[40] = supplied;
        replaced.setCancelled(true);
        service.finishClick(replaced); flush();
        assertSame(supplied, contents[40]); assertSame(genuine, contents[10]);
    }

    @Test void replacementRecoveryWaitsUntilNativeHandScopeFinishes() {
        enableArms();
        ItemStack arm = contents[40];
        ItemStack main = new ItemStack(Material.DIAMOND_SWORD);
        contents[2] = main;
        InventoryClickEvent click = inventoryClick(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, main);
        service.click(click);
        HandAccess.withOffhand(player, () -> {
            flush();
            assertEquals(Material.AIR, contents[2].getType()); assertSame(main, contents[40]);
            return null;
        });
        assertSame(main, contents[2]); assertSame(arm, contents[40]);
    }

    @Test void replacementIsNotMaskedWhenRecoveryTaskIsRejectedOrRetired() {
        enableArms();
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND);
        cursor.set(genuine);
        for (String failure : Arrays.asList("null", "done", "throw", "retired")) {
            when(scheduler.runPlayerDelayed(eq(plugin), eq(player), any(), eq(1L), any())).thenAnswer(call -> {
                if (failure.equals("throw")) throw new IllegalStateException("test rejection");
                if (failure.equals("null")) return null;
                SchedulerTask task = mock(SchedulerTask.class);
                if (failure.equals("done")) when(task.isDone()).thenReturn(true);
                if (failure.equals("retired")) ((Runnable) call.getArgument(4)).run();
                return task;
            });
            InventoryClickEvent click = offhandClick(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
            service.click(click);
            assertTrue(click.isCancelled()); assertSame(arm, contents[40]); assertSame(genuine, cursor.get());
        }
    }

    @Test void cleanupReloadAndDisableInvalidatePendingArmRestoration() {
        enableArms();
        ItemStack genuine = new ItemStack(Material.DIAMOND);
        for (String lifecycle : Arrays.asList("cleanup", "reload", "disable")) {
            closeView(); contents[40] = null; cursor.set(null); service.reconcile(player);
            ItemStack oldArm = contents[40];
            cursor.set(genuine);
            InventoryClickEvent click = offhandClick(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
            service.click(click); cursor.set(null);
            if (lifecycle.equals("cleanup")) service.cleanup(player);
            else if (lifecycle.equals("reload")) service.reload();
            else service.close();
            ItemStack expected = contents[40];
            service.finishClick(click); flush();
            assertSame(expected, contents[40]);
            assertNotSame(oldArm, contents[40]);
        }
    }

    @Test void reloadRespectsActualContainerCursorAndNativeHandScope() {
        enableArms();
        when(view.getType()).thenReturn(InventoryType.CHEST);
        service.reload(); assertNull(contents[40]);
        closeView(); when(view.getType()).thenReturn(InventoryType.CRAFTING);
        cursor.set(new ItemStack(Material.DIAMOND));
        service.reload(); assertNull(contents[40]);
        cursor.set(null); service.reconcile(player);
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        contents[2] = genuine;
        HandAccess.withOffhand(player, () -> {
            service.reload();
            assertEquals(Material.AIR, contents[2].getType()); assertSame(genuine, contents[40]);
            return null;
        });
        assertSame(genuine, contents[2]); assertTrue(arms.contains(contents[40]));
    }

    @Test void listenerReloadDuringNativeReplacementDoesNotInsertAnotherCarrier() {
        enableArms();
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        contents[10] = genuine;
        InventoryClickEvent click = inventoryClick(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, genuine);
        service.click(click);
        assertEquals(Material.AIR, contents[40].getType());
        service.reload();
        assertEquals(Material.AIR, contents[40].getType(), "A listener reload must not refill the in-flight destination");
        contents[40] = genuine; contents[10] = null;
        service.finishClick(click); flush(); service.reconcile(player);
        assertSame(genuine, contents[40]); assertNull(contents[10]);
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @Test void laterUncancellationCleansLeakedCarrierWithoutTouchingRealReplacement() {
        enableArms();
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        for (boolean fromContainer : new boolean[]{true, false}) {
            closeView(); when(view.getType()).thenReturn(InventoryType.CRAFTING);
            contents[40] = null; cursor.set(null); service.reconcile(player);
            ItemStack arm = contents[40];
            InventoryClickEvent click;
            if (fromContainer) {
                when(view.getType()).thenReturn(InventoryType.CHEST);
                topContents[1] = genuine;
                click = inventoryClick(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, genuine);
                when(click.getRawSlot()).thenReturn(1);
            } else {
                cursor.set(genuine);
                click = offhandClick(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
            }
            service.click(click);
            click.setCancelled(true); service.finishClick(click);
            assertSame(arm, contents[40]);
            // A later listener changes cancellation again; native swaps the restored
            // carrier into the original source. Deferred cleanup must remove it.
            click.setCancelled(false);
            contents[40] = genuine;
            if (fromContainer) topContents[1] = arm; else cursor.set(arm);
            flush();
            assertSame(genuine, contents[40]); assertNull(topContents[1]); assertNull(cursor.get());
        }
    }

    @Test void olderRecoveryCannotClearOrRestoreOverANewerReservation() {
        enableArms();
        ItemStack genuine = new ItemStack(Material.DIAMOND);
        cursor.set(genuine);
        InventoryClickEvent oldClick = offhandClick(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
        service.click(oldClick);
        Runnable oldRecovery = deferred.remove(0);
        service.cleanup(player); closeView(); cursor.set(null); service.reconcile(player);
        ItemStack newArm = contents[40]; cursor.set(genuine);
        InventoryClickEvent newClick = offhandClick(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
        service.click(newClick);
        oldRecovery.run();
        assertEquals(Material.AIR, contents[40].getType());
        newClick.setCancelled(true); service.finishClick(newClick); flush();
        assertSame(newArm, contents[40]); assertSame(genuine, cursor.get());
    }

    @Test void recoveryDoesNotRestoreAnArmWhosePrivateOwnershipWasRemoved() {
        enableArms();
        ItemStack arm = contents[40];
        cursor.set(new ItemStack(Material.DIAMOND));
        InventoryClickEvent click = offhandClick(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
        service.click(click);
        arms.remove(arm); owned.remove(arm);
        click.setCancelled(true); service.finishClick(click); flush();
        assertEquals(Material.AIR, contents[40].getType());
    }

    @Test void openingContainerPreservesArmButPausesInsertionUntilClosed() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        when(view.getType()).thenReturn(InventoryType.CHEST);
        InventoryOpenEvent open = mock(InventoryOpenEvent.class);
        when(open.getPlayer()).thenReturn(player);
        when(open.getInventory()).thenReturn(top);
        clearInvocations(inventory);
        service.open(open);
        assertSame(arm, contents[40], "Opening a container must preserve the existing arm");
        service.reconcile(player); flush();
        assertSame(arm, contents[40]);
        verify(inventory, never()).setItem(anyInt(), any());
        verify(inventory, never()).setItemInOffHand(any());
        contents[40] = null;
        service.reconcile(player);
        assertNull(contents[40], "Never insert a carrier while the view is open");
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        service.closeInventory(close);
        when(view.getType()).thenReturn(InventoryType.CRAFTING);
        service.reconcile(player);
        assertTrue(arms.contains(contents[40]));
    }

    @Test void unrelatedInventoryClickAndDragPreserveArmAndRealCursor() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND, 3);
        ItemStack current = new ItemStack(Material.STONE, 64);
        cursor.set(genuine); contents[8] = current;
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getCurrentItem()).thenReturn(current);
        when(click.getCursor()).thenReturn(genuine);
        when(click.getClick()).thenReturn(ClickType.LEFT);
        when(click.getHotbarButton()).thenReturn(-1);
        clearInvocations(inventory, player);
        service.click(click); flush(); service.reconcile(player);
        assertSame(arm, contents[40], "An unrelated inventory click must preserve the arm");
        assertSame(genuine, cursor.get()); assertSame(current, contents[8]);
        verify(click, never()).setCancelled(true);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(player);
        when(drag.getOldCursor()).thenReturn(genuine);
        when(drag.getNewItems()).thenReturn(Collections.singletonMap(8, genuine));
        when(drag.getRawSlots()).thenReturn(Collections.singleton(8));
        when(drag.getView()).thenReturn(view);
        when(view.getItem(8)).thenReturn(current);
        service.drag(drag); flush(); service.reconcile(player);
        assertSame(arm, contents[40]); assertSame(genuine, cursor.get());
        verify(drag, never()).setCancelled(true);
        verify(inventory, never()).setItem(anyInt(), any());
        verify(inventory, never()).setItemInOffHand(any());
        verify(player, never()).setItemOnCursor(any());
        verify(scheduler, never()).runPlayerDelayed(eq(plugin), eq(player), any(), eq(1L), any());
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
        contents[40] = null; cursor.set(null);
        service.reconcile(player);
        assertNull(contents[40], "A tracked inventory interaction pauses insertion until close");
        closeView(); service.reconcile(player);
        assertTrue(arms.contains(contents[40]));
    }

    @Test void unrelatedShiftDropCollectAndNumberKeyClicksPreserveArm() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.FEATHER, 12);
        contents[1] = genuine;
        cursor.set(new ItemStack(Material.FEATHER, 3));
        ClickType[] clicks = {ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.DROP,
                ClickType.CONTROL_DROP, ClickType.DOUBLE_CLICK, ClickType.NUMBER_KEY};
        InventoryAction[] actions = {InventoryAction.PICKUP_HALF, InventoryAction.MOVE_TO_OTHER_INVENTORY,
                InventoryAction.DROP_ONE_SLOT, InventoryAction.DROP_ALL_SLOT,
                InventoryAction.COLLECT_TO_CURSOR, InventoryAction.HOTBAR_SWAP};
        clearInvocations(inventory, player);
        for (int index = 0; index < clicks.length; index++) {
            InventoryClickEvent click = inventoryClick(clicks[index], actions[index], genuine);
            if (clicks[index] == ClickType.NUMBER_KEY) when(click.getHotbarButton()).thenReturn(1);
            service.click(click); flush(); service.reconcile(player);
            verify(click, never()).setCancelled(true);
            assertSame(arm, contents[40]); assertSame(genuine, contents[1]);
        }
        verify(inventory, never()).setItem(anyInt(), any());
        verify(inventory, never()).setItemInOffHand(any());
        verify(player, never()).setItemOnCursor(any());
        verify(scheduler, never()).runPlayerDelayed(eq(plugin), eq(player), any(), eq(1L), any());
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @Test void ownedCursorHotbarAndCraftingTransfersCleanAfterEventAndPreserveRealReplacement() {
        enableArms();
        ItemStack genuine = new ItemStack(Material.DIAMOND, 3);
        for (String source : Arrays.asList("cursor", "hotbar", "crafting")) {
            closeView(); contents[40] = null; cursor.set(null);
            service.reconcile(player);
            ItemStack arm = contents[40];
            InventoryClickEvent click;
            if (source.equals("crafting")) {
                click = mock(CraftItemEvent.class);
                when(click.getWhoClicked()).thenReturn(player);
                when(click.getCurrentItem()).thenReturn(genuine);
                when(click.getClick()).thenReturn(ClickType.SHIFT_LEFT);
                when(click.getHotbarButton()).thenReturn(-1);
                when(click.getInventory()).thenReturn(mock(org.bukkit.inventory.CraftingInventory.class));
                when(click.getInventory().getContents()).thenReturn(new ItemStack[]{ownedItem()});
            } else {
                click = inventoryClick(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, genuine);
                if (source.equals("cursor")) cursor.set(ownedArm());
                if (source.equals("hotbar")) {
                    contents[1] = ownedItem(); when(click.getHotbarButton()).thenReturn(1);
                }
            }
            clearInvocations(inventory, player);
            service.click(click);
            verify(click).setCancelled(true);
            assertSame(arm, contents[40]);
            verify(inventory, never()).setItem(anyInt(), any());
            verify(inventory, never()).setItemInOffHand(any());
            verify(player, never()).setItemOnCursor(any());
            // Another listener may supply real items before deferred cleanup runs.
            contents[40] = genuine; cursor.set(genuine);
            flush(); service.reconcile(player);
            assertSame(genuine, contents[40]); assertSame(genuine, cursor.get());
            assertNull(contents[1]);
        }
    }

    @Test void dragOntoActualOffhandCancelsWithoutWritingInsideEvent() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND, 3);
        cursor.set(genuine);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(player);
        when(drag.getOldCursor()).thenReturn(genuine);
        when(drag.getNewItems()).thenReturn(Collections.singletonMap(45, genuine));
        when(drag.getRawSlots()).thenReturn(Collections.singleton(45));
        when(drag.getView()).thenReturn(view);
        when(view.getItem(45)).thenReturn(arm);
        clearInvocations(inventory, player);
        service.drag(drag);
        verify(drag).setCancelled(true);
        verify(inventory, never()).setItem(anyInt(), any());
        verify(inventory, never()).setItemInOffHand(any());
        verify(player, never()).setItemOnCursor(any());
        flush(); service.reconcile(player);
        assertNull(contents[40]); assertSame(genuine, cursor.get());
    }

    @Test void offhandEquipmentShiftMasksOnlyCarrierThenAllowsNativeMove() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack shield = new ItemStack(Material.SHIELD);
        contents[10] = shield;
        items.when(() -> PlaceholderItem.isOffhandEquipment(shield)).thenReturn(true);
        InventoryClickEvent first = inventoryClick(ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, shield);
        clearInvocations(inventory);
        service.click(first);
        assertFalse(first.isCancelled());
        assertEquals(Material.AIR, contents[40].getType()); assertSame(shield, contents[10]);
        verify(inventory, never()).setItem(anyInt(), any());
        // The native menu, not the plugin, moves the real source after listeners.
        contents[40] = shield; contents[10] = null;
        service.finishClick(first); flush(); service.reconcile(player);
        assertSame(shield, contents[40]); assertNull(contents[10]);
    }

    @Test void equipmentShiftOutsideNativeAutoequipSourcesPreservesArm() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack shield = new ItemStack(Material.SHIELD);
        items.when(() -> PlaceholderItem.isOffhandEquipment(shield)).thenReturn(true);
        for (int slot : new int[]{0, 1, 4, 5, 8}) {
            InventoryClickEvent click = inventoryClick(ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, shield);
            when(click.getRawSlot()).thenReturn(slot);
            service.click(click); flush(); service.reconcile(player);
            verify(click, never()).setCancelled(true); assertSame(arm, contents[40]);
        }
        when(view.getType()).thenReturn(InventoryType.CHEST);
        InventoryClickEvent chest = inventoryClick(ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, shield);
        service.click(chest); flush(); service.reconcile(player);
        verify(chest, never()).setCancelled(true); assertSame(arm, contents[40]);
        verify(scheduler, never()).runPlayerDelayed(eq(plugin), eq(player), any(), eq(1L), any());
    }

    @Test void openViewStillHonorsGameplayEligibilityWithoutReinsertingArm() {
        enableArms(); service.reconcile(player);
        when(view.getType()).thenReturn(InventoryType.CHEST);
        InventoryOpenEvent open = mock(InventoryOpenEvent.class);
        when(open.getPlayer()).thenReturn(player); when(open.getInventory()).thenReturn(top);
        service.open(open);
        when(player.hasPermission("beastdoublewield.attack")).thenReturn(false);
        service.reconcile(player); assertNull(contents[40]);
        when(player.hasPermission("beastdoublewield.attack")).thenReturn(true);
        service.reconcile(player); assertNull(contents[40]);
        closeView(); when(view.getType()).thenReturn(InventoryType.CRAFTING);
        service.reconcile(player); assertTrue(arms.contains(contents[40]));
    }

    @Test void relatedInventoryCleanupInsideNativeActionWaitsForHandRestoration() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        contents[2] = genuine;
        InventoryClickEvent click = inventoryClick(ClickType.LEFT, InventoryAction.PICKUP_ALL, arm);
        HandAccess.withOffhand(player, () -> {
            service.click(click); flush();
            assertEquals(Material.AIR, contents[2].getType()); assertSame(genuine, contents[40]);
            return null;
        });
        verify(click).setCancelled(true);
        assertSame(genuine, contents[2]); assertNull(contents[40]);
        service.reconcile(player); assertNull(contents[40]);
    }

    @Test void swapKeepsGenuineItemAndDiscardsOnlyTheCarrierDestination() {
        contents[2] = ownedItem();
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(player, genuine, contents[2]);
        service.swap(event);
        assertSame(genuine, event.getMainHandItem()); assertEquals(Material.AIR, event.getOffHandItem().getType());
        assertFalse(event.isCancelled());
        contents[2] = event.getMainHandItem(); contents[40] = event.getOffHandItem();
        flush(); service.reconcile(player);
        assertSame(genuine, contents[2]); assertEquals(Material.AIR, contents[40].getType());
    }

    @Test void swapFromLeakedOffhandUsesNonNullAirForOlderPacketHandlers() {
        ItemStack carrier = ownedItem();
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(player, carrier, genuine);
        service.swap(event);
        assertEquals(Material.AIR, event.getMainHandItem().getType());
        assertSame(genuine, event.getOffHandItem());
        assertFalse(event.isCancelled());
    }

    @Test void shutdownInsideNativeActionWaitsForHandRestorationAndPreservesOriginalError() {
        contents[2] = ownedItem();
        ItemStack carrier = contents[2];
        ItemStack weapon = new ItemStack(Material.DIAMOND_SWORD);
        contents[40] = weapon;
        IllegalStateException failure = new IllegalStateException("native failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> HandAccess.withOffhand(player, () -> {
            service.close();
            assertSame(weapon, contents[2]); assertSame(carrier, contents[40]);
            throw failure;
        })));
        assertNull(contents[2]); assertSame(weapon, contents[40]);
        assertFalse(HandAccess.isOffhandAction(player));
    }

    @Test void successfulNativeActionAlsoDefersCleanupUntilHandsAreRestored() {
        contents[2] = ownedItem();
        ItemStack carrier = contents[2];
        ItemStack weapon = new ItemStack(Material.DIAMOND_SWORD);
        contents[40] = weapon;
        assertEquals("success", HandAccess.withOffhand(player, () -> {
            service.cleanup(player);
            assertSame(weapon, contents[2]); assertSame(carrier, contents[40]);
            return "success";
        }));
        assertNull(contents[2]); assertSame(weapon, contents[40]);
    }

    @Test void dragOverCarrierIsCancelledWithoutDestroyingRealCursor() {
        contents[2] = ownedItem();
        ItemStack genuine = new ItemStack(Material.DIAMOND, 3);
        cursor.set(genuine);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(player);
        when(drag.getOldCursor()).thenReturn(genuine);
        when(drag.getNewItems()).thenReturn(Collections.singletonMap(8, genuine));
        when(drag.getRawSlots()).thenReturn(Collections.singleton(8));
        when(drag.getView()).thenReturn(view);
        when(view.getItem(8)).thenAnswer(call -> contents[2]);
        service.drag(drag);
        verify(drag).setCancelled(true);
        flush();
        assertNull(contents[2]); assertSame(genuine, cursor.get());
    }

    @Test void quitAndDisabledJoinRemoveStaleCarrierWithoutTouchingRealItems() {
        contents[2] = ownedItem();
        ItemStack genuine = new ItemStack(Material.FEATHER, 12);
        contents[7] = genuine;
        service.quit(new PlayerQuitEvent(player, "quit"));
        assertNull(contents[2]); assertSame(genuine, contents[7]);
        config.set("OffhandArm.Enabled", false);
        service.reload();
        contents[2] = ownedItem();
        service.join(new PlayerJoinEvent(player, "join"));
        assertNull(contents[2]); assertSame(genuine, contents[7]);
    }

    @Test void framesAndArmorStandsCannotReceiveOwnedItemsEvenWhenFeatureDisabled() {
        contents[2] = ownedItem();
        PlayerInteractEntityEvent frame = new PlayerInteractEntityEvent(player, mock(ItemFrame.class), EquipmentSlot.HAND);
        service.entity(frame);
        assertTrue(frame.isCancelled());
        flush(); assertNull(contents[2]);
        config.set("OffhandArm.Enabled", false);
        service.reload();
        contents[2] = ownedItem();
        PlayerInteractAtEntityEvent stand = new PlayerInteractAtEntityEvent(player, mock(ArmorStand.class), new Vector(), EquipmentSlot.HAND);
        service.entityAt(stand);
        assertTrue(stand.isCancelled());
        flush(); assertNull(contents[2]);
        ItemStack genuine = new ItemStack(Material.FEATHER);
        contents[2] = genuine;
        PlayerInteractEntityEvent realFrame = new PlayerInteractEntityEvent(player, mock(ItemFrame.class), EquipmentSlot.HAND);
        service.entity(realFrame);
        assertFalse(realFrame.isCancelled()); assertSame(genuine, contents[2]);
        contents[40] = ownedItem();
        PlayerInteractEntityEvent leakedOffhand = new PlayerInteractEntityEvent(player, mock(ItemFrame.class), EquipmentSlot.OFF_HAND);
        service.entity(leakedOffhand);
        assertTrue(leakedOffhand.isCancelled());
        flush(); assertNull(contents[40]); assertSame(genuine, contents[2]);
    }

    @Test void carrierPreservesTradingAndMountingButCannotBeGivenToAllay() {
        contents[2] = ownedItem();
        ItemStack carrier = contents[2];
        PlayerInteractEntityEvent villager = new PlayerInteractEntityEvent(player, mock(Villager.class), EquipmentSlot.HAND);
        service.entity(villager);
        assertFalse(villager.isCancelled()); assertSame(carrier, contents[2]);
        PlayerInteractEntityEvent vehicle = new PlayerInteractEntityEvent(player, mock(Vehicle.class), EquipmentSlot.HAND);
        service.entity(vehicle);
        assertFalse(vehicle.isCancelled()); assertSame(carrier, contents[2]);
        Entity allay = mock(Entity.class);
        when(allay.getType()).thenReturn(EntityType.ALLAY);
        PlayerInteractEntityEvent give = new PlayerInteractEntityEvent(player, allay, EquipmentSlot.HAND);
        service.entity(give);
        assertTrue(give.isCancelled());
        flush(); assertNull(contents[2]);
        ItemStack genuine = new ItemStack(Material.FEATHER);
        contents[2] = genuine;
        PlayerInteractEntityEvent realGive = new PlayerInteractEntityEvent(player, allay, EquipmentSlot.HAND);
        service.entity(realGive);
        assertFalse(realGive.isCancelled()); assertSame(genuine, contents[2]);
    }

    @Test void deathDropAndWorldSpawnNeverYieldAReusableCarrier() {
        contents[2] = ownedItem();
        ItemStack carrier = contents[2];
        ItemStack genuine = new ItemStack(Material.DIAMOND);
        List<ItemStack> drops = new ArrayList<>(Arrays.asList(carrier, genuine));
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        when(death.getDrops()).thenReturn(drops);
        service.death(death);
        assertEquals(Collections.singletonList(genuine), drops); assertNull(contents[2]);
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(carrier);
        ItemSpawnEvent spawn = new ItemSpawnEvent(entity);
        service.spawn(spawn);
        assertTrue(spawn.isCancelled());
    }

    @Test void droppingCarrierIsCancelledAndCleanupDoesNotDeleteRealItems() {
        contents[2] = ownedItem();
        Item carrierEntity = mock(Item.class);
        when(carrierEntity.getItemStack()).thenReturn(contents[2]);
        PlayerDropItemEvent drop = new PlayerDropItemEvent(player, carrierEntity);
        service.drop(drop);
        assertTrue(drop.isCancelled());
        flush(); assertNull(contents[2]);
        ItemStack genuine = new ItemStack(Material.DIAMOND);
        contents[2] = genuine;
        when(carrierEntity.getItemStack()).thenReturn(genuine);
        PlayerDropItemEvent genuineDrop = new PlayerDropItemEvent(player, carrierEntity);
        service.drop(genuineDrop);
        flush();
        assertFalse(genuineDrop.isCancelled()); assertSame(genuine, contents[2]);
    }

    @Test void ordinaryMainHandDropsKeepArmWithoutCleanupOrRecreation() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(genuine);
        clearInvocations(inventory);
        for (boolean cancelled : new boolean[]{false, true}) {
            PlayerDropItemEvent drop = new PlayerDropItemEvent(player, entity);
            drop.setCancelled(cancelled);
            service.drop(drop);
            flush();
            assertSame(arm, contents[40], "An ordinary drop must not remove the offhand arm");
            assertEquals(cancelled, drop.isCancelled());
            service.reconcile(player);
            assertSame(arm, contents[40]);
        }
        verify(inventory, never()).setItem(anyInt(), any());
        verify(inventory, never()).setItemInOffHand(any());
        verify(scheduler, never()).runPlayerDelayed(eq(plugin), eq(player), any(), eq(1L), any());
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @SuppressWarnings("deprecation")
    @Test void ordinaryPickupsKeepArmWithoutCleanupOrRecreation() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND);
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(genuine);
        clearInvocations(inventory);
        for (boolean cancelled : new boolean[]{false, true}) {
            PlayerPickupItemEvent pickup = new PlayerPickupItemEvent(player, entity, 0);
            pickup.setCancelled(cancelled);
            service.pickup(pickup);
            assertSame(arm, contents[40], "An ordinary pickup must not remove the offhand arm");
            flush(); service.reconcile(player);
            assertSame(arm, contents[40]); assertEquals(cancelled, pickup.isCancelled());
        }
        verify(entity, never()).remove();
        verify(inventory, never()).setItem(anyInt(), any());
        verify(inventory, never()).setItemInOffHand(any());
        verify(scheduler, never()).runPlayerDelayed(eq(plugin), eq(player), any(), eq(1L), any());
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @SuppressWarnings("deprecation")
    @Test void ordinaryItemEventsInsideNativeActionLeaveBothHandsUntouched() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack main = new ItemStack(Material.DIAMOND_SWORD);
        contents[2] = main;
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(new ItemStack(Material.DIAMOND));
        HandAccess.withOffhand(player, () -> {
            clearInvocations(inventory);
            service.drop(new PlayerDropItemEvent(player, entity));
            service.pickup(new PlayerPickupItemEvent(player, entity, 0));
            flush();
            assertEquals(Material.AIR, contents[2].getType()); assertSame(main, contents[40]);
            verify(inventory, never()).setItem(anyInt(), any());
            verify(inventory, never()).setItemInOffHand(any());
            return null;
        });
        assertSame(main, contents[2]); assertSame(arm, contents[40]);
        flush(); service.reconcile(player);
        assertSame(main, contents[2]); assertSame(arm, contents[40]);
        verify(scheduler, never()).runPlayerDelayed(eq(plugin), eq(player), any(), eq(1L), any());
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @SuppressWarnings("deprecation")
    @Test void ownedPickupRemovesOnlyStrayEntityAndPreservesExistingArm() {
        enableArms(); service.reconcile(player);
        ItemStack arm = contents[40];
        clearInvocations(inventory);
        for (ItemStack stray : Arrays.asList(ownedItem(), ownedArm())) {
            Item entity = mock(Item.class);
            when(entity.getItemStack()).thenReturn(stray);
            PlayerPickupItemEvent pickup = new PlayerPickupItemEvent(player, entity, 0);
            service.pickup(pickup);
            assertTrue(pickup.isCancelled()); verify(entity).remove();
            flush(); service.reconcile(player);
            assertSame(arm, contents[40]);
        }
        verify(inventory, never()).setItem(anyInt(), any());
        verify(inventory, never()).setItemInOffHand(any());
        items.verify(() -> PlaceholderItem.createArm(player), times(1));
    }

    @Test void cancelledOwnedDropIsCleanedAfterNativeRestoration() {
        ItemStack carrier = ownedItem();
        ItemStack genuine = new ItemStack(Material.DIAMOND_SWORD);
        contents[40] = genuine;
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(carrier);
        PlayerDropItemEvent drop = new PlayerDropItemEvent(player, entity);
        service.drop(drop);
        assertTrue(drop.isCancelled()); assertNull(contents[2]); assertSame(genuine, contents[40]);
        // The native packet handler restores a cancelled drop after listeners return.
        contents[2] = carrier;
        flush();
        assertNull(contents[2]); assertSame(genuine, contents[40]);
    }

    @SuppressWarnings("deprecation")
    @Test void postPickupAndDropReconciliationStillHonorsFullStorage() {
        enableArms();
        ItemStack stone = new ItemStack(Material.STONE, 64);
        Arrays.fill(contents, 0, 36, stone); contents[3] = null;
        service.reconcile(player);
        ItemStack arm = contents[40];
        ItemStack genuine = new ItemStack(Material.DIAMOND, 64);
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(genuine);
        service.pickup(new PlayerPickupItemEvent(player, entity, 0));
        assertSame(arm, contents[40]);
        contents[3] = genuine;
        service.reconcile(player);
        assertNull(contents[40]); assertSame(genuine, contents[3]);
        service.drop(new PlayerDropItemEvent(player, entity));
        contents[3] = null;
        service.reconcile(player);
        assertTrue(arms.contains(contents[40])); assertNull(contents[3]);
    }

    @Test void hopperAndCraftingCannotTurnCarrierIntoRealResources() {
        ItemStack carrier = ownedItem();
        topContents[1] = carrier;
        InventoryMoveItemEvent move = spy(new InventoryMoveItemEvent(top, carrier, mock(Inventory.class), true));
        // The real event clones its stack; this fixture models ownership by identity.
        doReturn(carrier).when(move).getItem();
        service.hopper(move);
        assertTrue(move.isCancelled());
        flush(); assertNull(topContents[1]);
        org.bukkit.inventory.CraftingInventory crafting = mock(org.bukkit.inventory.CraftingInventory.class);
        when(crafting.getContents()).thenReturn(new ItemStack[]{carrier, new ItemStack(Material.STICK)});
        PrepareItemCraftEvent prepare = mock(PrepareItemCraftEvent.class);
        when(prepare.getInventory()).thenReturn(crafting);
        service.craft(prepare);
        verify(crafting).setResult(null);
    }

    @Test void leakedOffhandCarrierCannotBypassEmptyHandPolicy() {
        ItemStack carrier = ownedItem();
        config.set("Attack.AllowEmptyHand", false);
        assertFalse(ItemRules.canUse(player, carrier, config.getConfigurationSection("Attack")));
        contents[40] = carrier;
        service.reconcile(player);
        assertNull(contents[40]);
    }
}
