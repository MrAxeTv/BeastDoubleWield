package probe;

import me.mraxetv.beastdoublewield.BeastDoubleWieldPlugin;
import me.mraxetv.beastdoublewield.combat.OffhandCombat;
import me.mraxetv.beastdoublewield.compat.ServerAccess;
import me.mraxetv.beastdoublewield.util.HandAccess;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;


/** Test-only fixture. Never packaged or exported with BeastDoubleWield. */
public final class RuntimeProbe extends JavaPlugin implements Listener {
    private Player player;
    private BeastDoubleWieldPlugin plugin;
    private Cow target;
    private boolean cancelDamage, cancelBreak;
    private int damageEvents, breakEvents, damageStarts;
    private int passed, failed;
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        if (!event.getPlayer().getName().equals("BDWProbe")) return;
        player = event.getPlayer(); plugin = (BeastDoubleWieldPlugin)getServer().getPluginManager().getPlugin("BeastDoubleWield");
        later(40, () -> {
            check("public BeastLib 1.7.0 enabled",getServer().getPluginManager().getPlugin("BeastLib").isEnabled() && "1.7.0".equals(getServer().getPluginManager().getPlugin("BeastLib").getDescription().getVersion()));
            check("legacy placeholder safely unavailable","placeholder=unavailable (requires 1.14+)".equals(plugin.getPlaceholderService().describe()));
            check("legacy placeholder does not insert carrier",!player.getInventory().contains(Material.FEATHER));
            World world = player.getWorld();
            for (int x=-3; x<7; x++) for(int z=-3;z<4;z++) {
                world.getBlockAt(x,69,z).setType(Material.STONE);
                for(int y=70;y<75;y++) world.getBlockAt(x,y,z).setType(Material.AIR);
            }
            player.setGameMode(GameMode.SURVIVAL); player.setFoodLevel(20);
            player.teleport(new Location(world,0.5,70,0.5,-90,0));
            player.getInventory().clear();
            gear(Material.DIAMOND_AXE);
            target = cow();
            later(30,this::attack);
        });
    }
    private void attack() {
        Block soundBlock = player.getWorld().getBlockAt(0,69,0);
        ServerAccess.hitSound(player,soundBlock);
        check("legacy block-hit sound binding",true);
        check("empty hand is not a preferred tool",!ServerAccess.preferredTool(soundBlock,new ItemStack(Material.AIR)));
        OffhandCombat combat = new OffhandCombat(plugin);
        double before = target.getHealth();
        getLogger().info("BDW_ATTRIBUTE_OBSERVATION offhand-defaults="+ServerAccess.modifiers(player.getInventory().getItemInOffHand(),org.bukkit.inventory.EquipmentSlot.HAND,org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE));
        boolean claimed=combat.attack(player,target);
        getLogger().info("BDW_ATTACK_OBSERVATION claimed="+claimed+" hp-before="+before+" hp-after="+target.getHealth()
                +" player="+player.getLocation()+" target="+target.getLocation()+" valid="+target.isValid()
                +" sight="+player.hasLineOfSight(target)+" permission="+player.hasPermission("beastdoublewield.attack"));
        check("native offhand axe damage", before-target.getHealth() >= 8.9);
        check("main-hand retained", player.getInventory().getItemInMainHand().getType()==Material.STICK);
        check("offhand durability applied", player.getInventory().getItemInOffHand().getDurability()>0);
        check("native damage event", damageEvents==1);
        later(30, () -> {
            cancelDamage=true; double health=target.getHealth();
            combat.attack(player,target);
            check("cancelled damage protects health", target.getHealth()==health); cancelDamage=false;
            target.remove(); target=cow();
            gear(Material.DIAMOND_SWORD);
            later(30,()-> { player.sendMessage("BDW_TARGET "+target.getEntityId()); later(40,this::networkAttack); });
        });
    }
    private void networkAttack() {
        check("network right-click attacks",target.getHealth()<40);
        target.remove(); gear(Material.DIAMOND_PICKAXE);
        Block block=player.getWorld().getBlockAt(2,70,0); block.setType(Material.STONE);
        int previous=breakEvents;
        player.sendMessage("BDW_BLOCK 2 70 0");
        later(100,()-> {
            check("network held right-click mining",block.getType()==Material.AIR);
            check("one native break",breakEvents==previous+1);
            check("block-damage integration",damageStarts>0);
            check("mining tool wear retained",player.getInventory().getItemInOffHand().getDurability()>0);
            protectedBreak();
        });
    }
    private void protectedBreak() {
        Block block=player.getWorld().getBlockAt(2,70,0); block.setType(Material.STONE); cancelBreak=true;
        boolean result=HandAccess.withOffhand(player,()->ServerAccess.breakBlock(player,block));
        check("cancelled break returns false",!result);
        check("protected block retained",block.getType()==Material.STONE);
        cancelBreak=false;
        done();
    }
    private Cow cow() {
        Cow cow=player.getWorld().spawn(new Location(player.getWorld(),2.3,70,0.5),Cow.class);
        cow.setAI(false);cow.setMaxHealth(40);cow.setHealth(40);return cow;
    }
    private void done() {
        getLogger().info("BDW_PROBE_RESULT passed="+passed+" failed="+failed+" server="+getServer().getBukkitVersion());
        player.sendMessage("BDW_PROBE_DONE passed="+passed+" failed="+failed);
    }
    private void gear(Material material) {
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        player.getInventory().setItemInOffHand(new ItemStack(material));
    }
    private void check(String name,boolean success) {
        if(success)passed++;else failed++;
        getLogger().info("BDW_CHECK "+(success?"PASS ":"FAIL ")+name);
    }
    private void later(long ticks,Runnable task) { getServer().getScheduler().runTaskLater(this,()-> {
        try {task.run();} catch(Throwable error) {failed++; getLogger().log(java.util.logging.Level.SEVERE,"PROBE_ERROR",error);}
    },ticks); }
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageByEntityEvent event) {
        if(event.getDamager()!=player)return;
        getLogger().info("BDW_DAMAGE_OBSERVATION raw="+event.getDamage()+" final="+event.getFinalDamage()+" attack-attribute="
                +player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).getValue()+" main="+player.getInventory().getItemInMainHand());
        if(HandAccess.isOffhandAction(player))damageEvents++;
        if(cancelDamage)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void broken(BlockBreakEvent event) {
        if(event.getPlayer()!=player)return;breakEvents++;if(cancelBreak)event.setCancelled(true);
    }
    @EventHandler public void start(BlockDamageEvent event) {if(event.getPlayer()==player)damageStarts++;}
}
