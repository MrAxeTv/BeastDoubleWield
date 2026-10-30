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
    private final List<Runnable> deferred = new ArrayList<>();
    private MockedStatic<PlaceholderItem> items;

    @BeforeEach void setup() {
        items = mockStatic(PlaceholderItem.class);
        items.when(PlaceholderItem::supported).thenReturn(true);
        items.when(() -> PlaceholderItem.isOwned(any())).thenAnswer(call -> owned.contains(call.getArgument(0)));
        items.when(PlaceholderItem::create).thenAnswer(call -> ownedItem());
        plugin = mock(BeastDoubleWieldPlugin.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        top = mock(Inventory.class);
        view = mock(InventoryView.class);
        scheduler = mock(SchedulerService.class);
        Server server = mock(Server.class);
        World world = mock(World.class);
        config = new YamlConfiguration();
        config.set("placeholder.enabled", true);
        config.set("swing.air", true);
        config.set("attack.enabled", true);
        config.set("attack.allow-empty-hand", true);
        config.set("attack.game-modes", Arrays.asList("SURVIVAL", "ADVENTURE"));
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

    @AfterEach void cleanup() { service.close(); items.close(); }

    private ItemStack ownedItem() {
        ItemStack item = new ItemStack(Material.FEATHER, 1);
        owned.add(item);
        return item;
    }

    private void flush() {
        List<Runnable> tasks = new ArrayList<>(deferred);
        deferred.clear();
        tasks.forEach(Runnable::run);
    }

    @Test void fillsOnlyEmptySelectedSlotAndNeverReplacesGenuineFeathersOrOtherItems() {
        service.reconcile(player);
        ItemStack carrier = contents[2];
        assertTrue(owned.contains(carrier));
        service.reconcile(player);
        assertSame(carrier, contents[2]);
        ItemStack genuineFeather = new ItemStack(Material.FEATHER, 1);
        contents[2] = genuineFeather;
        service.reconcile(player);
        assertSame(genuineFeather, contents[2]);
        assertEquals(1, owned.size());
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        contents[2] = diamond;
        service.reconcile(player);
        assertSame(diamond, contents[2]);
    }

    @Test void disablingRemovesOnlyOwnedInventoryCursorAndContainerItems() {
        ItemStack genuine = new ItemStack(Material.FEATHER, 12);
        contents[2] = ownedItem(); contents[6] = genuine; contents[40] = ownedItem();
        cursor.set(ownedItem()); topContents[1] = ownedItem(); topContents[3] = genuine;
        config.set("placeholder.enabled", false);
        service.reload();
        service.reconcile(player);
        assertNull(contents[2]); assertNull(contents[40]); assertNull(cursor.get()); assertNull(topContents[1]);
        assertSame(genuine, contents[6]); assertSame(genuine, topContents[3]);
    }

    @Test void neverOccupiesLastAvailableStorageSlot() {
        ItemStack genuine = new ItemStack(Material.STONE, 64);
        Arrays.fill(contents, 0, 36, genuine);
        contents[2] = null;
        service.reconcile(player);
        assertNull(contents[2]);
        contents[3] = null;
        service.reconcile(player);
        assertTrue(owned.contains(contents[2]));
        contents[3] = genuine;
        service.reconcile(player);
        assertNull(contents[2]);
        assertSame(genuine, contents[3]);
    }

    @Test void personalPermissionGameModeAndConfigurationGatesRemoveExistingCarrier() {
        service.reconcile(player);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        service.reconcile(player); assertNull(contents[2]);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(plugin.isEnabled(player)).thenReturn(false);
        service.reconcile(player); assertNull(contents[2]);
        when(plugin.isEnabled(player)).thenReturn(true);
        when(player.hasPermission("beastdoublewield.attack")).thenReturn(false);
        service.reconcile(player); assertNull(contents[2]);
        when(player.hasPermission("beastdoublewield.attack")).thenReturn(true);
        config.set("swing.air", false);
        service.reconcile(player); assertNull(contents[2]);
        config.set("swing.air", true);
        config.set("attack.allow-empty-hand", false);
        service.reconcile(player); assertNull(contents[2]);
        config.set("attack.allow-empty-hand", true);
        config.set("disabled-worlds", Collections.singletonList("world"));
        service.reconcile(player); assertNull(contents[2]);
    }

    @Test void inventoryClickCancelsOwnedTransferThenCleansAfterEventWithoutTouchingRealCursor() {
        service.reconcile(player);
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
        assertTrue(owned.contains(contents[2]));
    }

    @Test void swapKeepsGenuineItemAndDiscardsOnlyTheCarrierDestination() {
        service.reconcile(player);
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
        service.reconcile(player);
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
        service.reconcile(player);
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
        service.reconcile(player);
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
        service.reconcile(player);
        ItemStack genuine = new ItemStack(Material.FEATHER, 12);
        contents[7] = genuine;
        service.quit(new PlayerQuitEvent(player, "quit"));
        assertNull(contents[2]); assertSame(genuine, contents[7]);
        config.set("placeholder.enabled", false);
        service.reload();
        contents[2] = ownedItem();
        service.join(new PlayerJoinEvent(player, "join"));
        assertNull(contents[2]); assertSame(genuine, contents[7]);
    }

    @Test void framesAndArmorStandsCannotReceiveOwnedItemsEvenWhenFeatureDisabled() {
        service.reconcile(player);
        PlayerInteractEntityEvent frame = new PlayerInteractEntityEvent(player, mock(ItemFrame.class), EquipmentSlot.HAND);
        service.entity(frame);
        assertTrue(frame.isCancelled());
        flush(); assertNull(contents[2]);
        config.set("placeholder.enabled", false);
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
        service.reconcile(player);
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
        service.reconcile(player);
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
        service.reconcile(player);
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
        config.set("attack.allow-empty-hand", false);
        assertFalse(ItemRules.canUse(player, carrier, config.getConfigurationSection("attack")));
        contents[40] = carrier;
        service.reconcile(player);
        assertNull(contents[40]);
    }
}
