package probe;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.combat.OffhandCombat;
import me.mraxetv.beastdoublewield.placeholder.PlaceholderItem;
import me.mraxetv.beastdoublewield.util.HandAccess;
import me.mraxetv.beastdoublewield.compat.ServerAccess;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.ItemMeta;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

/** Disposable native-server API fixture, with a synthetic player and no network client. */
public final class ArmRuntimeProbe extends JavaPlugin implements Listener {
    private int passed, failed, damageEvents, nativeDamageEvents, cancelledDamageEvents;
    private boolean cancelDamage;
    private UUID protectedTarget;
    private Player actor;
    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskLater(this, () -> {
            try { run(); }
            catch(Throwable e) { failed++; getLogger().log(java.util.logging.Level.SEVERE,"ARM_PROBE_ERROR",e); }
            finally {
                getLogger().info("ARM_RESULT passed="+passed+" failed="+failed
                        +" nativeDamageEvents="+nativeDamageEvents+" emptyHandDamageEvents="+damageEvents
                        +" cancelledDamageEvents="+cancelledDamageEvents);
                getServer().shutdown();
            }
        }, 20);
    }
    private void run() throws Exception {
        BeastDoubleWieldPlugin plugin=(BeastDoubleWieldPlugin)getServer().getPluginManager().getPlugin("BeastDoubleWield");
        check("plugin enabled on actual 26.3",plugin!=null && plugin.isEnabled());
        World world=getServer().getWorlds().get(0);
        GameProfile identity=new GameProfile(UUID.randomUUID(),"BDWArmProbe");
        net.minecraft.server.MinecraftServer server=((CraftServer)getServer()).getServer();
        ServerPlayer nativePlayer=new ServerPlayer(server,((CraftWorld)world).getHandle(),identity,ClientInformation.createDefault());
        nativePlayer.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),nativePlayer,CommonListenerCookie.createInitial(identity,false));
        nativePlayer.setPos(0.5,80,0.5);
        Player actual=nativePlayer.getBukkitEntity();
        PlayerProfile profile=Bukkit.createPlayerProfile(identity.id(),identity.name());
        PlayerTextures texture=profile.getTextures();
        // A complete synthetic texture property exercises metadata without a skin lookup.
        texture.setSkin(new URL("https://textures.minecraft.net/texture/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),PlayerTextures.SkinModel.CLASSIC);
        profile.setTextures(texture);
        actor=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)-> {
            if(method.getName().equals("isOnline") || method.getName().equals("hasPermission"))return true;
            if(method.getName().equals("getPlayerProfile"))return profile;
            try{return method.invoke(actual,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
        for(int x=-2;x<5;x++)for(int z=-2;z<3;z++) {
            world.getBlockAt(x,79,z).setType(Material.STONE);
            for(int y=80;y<84;y++)world.getBlockAt(x,y,z).setType(Material.AIR);
        }
        actor.getInventory().clear();
        actor.getInventory().setItem(8,new ItemStack(Material.DIAMOND,3));
        Method reconcile=plugin.getPlaceholderService().getClass().getDeclaredMethod("reconcile",Player.class);
        reconcile.setAccessible(true); reconcile.invoke(plugin.getPlaceholderService(),actor);
        ItemStack arm=actor.getInventory().getItemInOffHand().clone();
        check("empty offhand receives owned non-placeable feather",arm.getType()==Material.FEATHER && PlaceholderItem.isOwned(arm));
        check("main hand stays truly empty",actor.getInventory().getItemInMainHand().getType().isAir());
        ItemMeta head=arm.getItemMeta();
        check("classic model selection",head.getCustomModelData()==35022);
        ItemStack expectedHead=new ItemStack(Material.PLAYER_HEAD);
        SkullMeta expectedMeta=(SkullMeta)expectedHead.getItemMeta();expectedMeta.setOwnerProfile(profile);expectedHead.setItemMeta(expectedMeta);
        check("detached complete skin retained",expectedHead.getData(DataComponentTypes.PROFILE).equals(arm.getData(DataComponentTypes.PROFILE)));
        check("native profile renderer selected",arm.getData(DataComponentTypes.ITEM_MODEL).asString().equals("minecraft:player_head"));
        check("carrier cannot equip as helmet",!arm.hasData(DataComponentTypes.EQUIPPABLE));
        ItemStack restored=ItemStack.deserializeBytes(arm.serializeAsBytes());
        check("native serialization retains profile and ownership",restored.equals(arm) && PlaceholderItem.isOwned(restored));
        HandAccess.withOffhand(actor,()-> {
            check("native operation sees actual AIR",actor.getInventory().getItemInMainHand().getType().isAir());
            return null;
        });
        check("carrier restored after native scope",actor.getInventory().getItemInOffHand().equals(arm));
        ItemStack genuine=new ItemStack(Material.GOLD_INGOT,4);
        HandAccess.withOffhand(actor,()-> {actor.getInventory().setItemInMainHand(genuine);return null;});
        check("real replacement survives scope",actor.getInventory().getItemInOffHand().equals(genuine));
        reconcile.invoke(plugin.getPlaceholderService(),actor);
        check("real offhand never overwritten",actor.getInventory().getItemInOffHand().equals(genuine));
        actor.getInventory().setItemInOffHand(arm);
        Cow cow=world.spawn(new Location(world,2.2,80,0.5),Cow.class);cow.setAI(false);
        double before=cow.getHealth();
        nativePlayer.resetAttackStrengthTicker();
        for(int i=0;i<30;i++)nativePlayer.tick();
        new OffhandCombat(plugin).attack(actor,cow);
        double armDamage=before-cow.getHealth();
        check("native damage event sees empty acting hand",damageEvents==1);
        check("arm retained after combat",PlaceholderItem.isOwned(actor.getInventory().getItemInOffHand()));
        Cow control=world.spawn(new Location(world,2.2,80,0.5),Cow.class);control.setAI(false);
        double controlBefore=control.getHealth();
        nativePlayer.resetAttackStrengthTicker();
        for(int i=0;i<30;i++)nativePlayer.tick();
        ServerAccess.attack(actor,control);
        double fistDamage=controlBefore-control.getHealth();
        getLogger().info("ARM_DAMAGE carrier="+armDamage+" ordinary-fist="+fistDamage);
        check("carrier matches native empty fist damage",armDamage>0 && Math.abs(armDamage-fistDamage)<0.001);
        control.remove();cow.remove();
        // A fresh target prevents hurt invulnerability from making unchanged health
        // pass without a native damage event. Observe cancellation again at MONITOR.
        Cow protectedCow=world.spawn(new Location(world,2.2,80,0.5),Cow.class);protectedCow.setAI(false);
        protectedCow.setNoDamageTicks(0);
        cancelDamage=true;protectedTarget=protectedCow.getUniqueId();before=protectedCow.getHealth();
        int nativeBefore=nativeDamageEvents, emptyBefore=damageEvents, cancelledBefore=cancelledDamageEvents;
        nativePlayer.resetAttackStrengthTicker();
        for(int i=0;i<30;i++)nativePlayer.tick();
        boolean claimed=new OffhandCombat(plugin).attack(actor,protectedCow);
        check("native protection cancellation observed and prevents damage",claimed
                && nativeDamageEvents==nativeBefore+1 && damageEvents==emptyBefore+1
                && cancelledDamageEvents==cancelledBefore+1 && protectedCow.getHealth()==before);
        cancelDamage=false;protectedTarget=null;protectedCow.remove();
        plugin.getPlaceholderService().cleanup(actor);
        check("cleanup removes only owned arm",actor.getInventory().getItemInOffHand().getType().isAir() && actor.getInventory().getItem(8).getAmount()==3);
        texture.setSkin(texture.getSkin(),PlayerTextures.SkinModel.SLIM);profile.setTextures(texture);
        reconcile.invoke(plugin.getPlaceholderService(),actor);
        check("slim model selection",actor.getInventory().getItemInOffHand().getItemMeta().getCustomModelData()==35023);
        plugin.getPlaceholderService().cleanup(actor);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageByEntityEvent event) {
        if(actor==null || !event.getDamager().getUniqueId().equals(actor.getUniqueId()))return;
        nativeDamageEvents++;
        if(((Player)event.getDamager()).getInventory().getItemInMainHand().getType().isAir())damageEvents++;
        if(cancelDamage && event.getEntity().getUniqueId().equals(protectedTarget))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void observeCancellation(EntityDamageByEntityEvent event) {
        if(actor!=null && event.getDamager().getUniqueId().equals(actor.getUniqueId())
                && event.getEntity().getUniqueId().equals(protectedTarget) && event.isCancelled())cancelledDamageEvents++;
    }
    private void check(String label,boolean value) {if(value)passed++;else failed++;getLogger().info("ARM_CHECK "+(value?"PASS ":"FAIL ")+label);}
}
