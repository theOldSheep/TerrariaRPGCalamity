package terraria.event.listener;

import net.minecraft.server.v1_12_R1.EntityFishingHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftFish;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import terraria.TerrariaHelper;
import terraria.entity.others.TerrariaFishingHook;
import terraria.util.*;

import java.util.ArrayList;
import java.util.Set;


public class VanillaMechanicListener implements Listener {
    // entity related
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryCLose(InventoryCloseEvent evt) {
        Inventory inv = evt.getInventory();
        InventoryHolder invHolder = inv.getHolder();
        if (! (invHolder instanceof Chest) )
            return;
        Chest invHolderChest = (Chest) invHolder;
        String monsterSpawnType = null;
        for (ItemStack item : inv.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            String itemType = ItemHelper.splitItemName(item)[1];
            if (monsterSpawnType != null)
                return;
            switch (itemType) {
                case "光明钥匙":
                    monsterSpawnType = "神圣宝箱怪";
                    break;
                case "夜光钥匙":
                    monsterSpawnType = "腐化宝箱怪";
                    break;
                default:
                    return;
            }
        }
        if (monsterSpawnType != null) {
            invHolder.getInventory().clear();
            invHolderChest.getBlock().setType(Material.AIR);
            MonsterHelper.spawnMob(monsterSpawnType, invHolderChest.getLocation(), (Player) evt.getPlayer());
        }
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onDismount(VehicleExitEvent evt) {
        if (evt.isCancelled())
            return;
        if (evt.getExited() instanceof Player)
            evt.getVehicle().remove();
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDeath(EntityDeathEvent evt) {
        evt.setDroppedExp(0);
        evt.getDrops().clear();
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onEat(PlayerItemConsumeEvent evt) {
        evt.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSprint(PlayerToggleSprintEvent e) {
        e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public void onTeleport(PlayerTeleportEvent e) {
        // no teleport from those reasons
        switch (e.getCause()) {
            case END_PORTAL:
            case NETHER_PORTAL:
            case END_GATEWAY:
            case SPECTATE:
                e.setCancelled(true);
        }
        // remove sentries and minions on teleport to somewhere far away ( another world or 30 blocks away )
        if (! e.isCancelled() &&
                (e.getFrom().getWorld() != e.getTo().getWorld() || e.getFrom().distanceSquared(e.getTo()) > 900) ) {
            for (Entity entity :
                    ((ArrayList<Entity>) EntityHelper.getMetadata(e.getPlayer(), EntityHelper.MetadataName.PLAYER_SENTRY_LIST).value()) ) {
                entity.remove();
            }
            for (Entity entity :
                    ((ArrayList<Entity>) EntityHelper.getMetadata(e.getPlayer(), EntityHelper.MetadataName.PLAYER_MINION_LIST).value()) ) {
                entity.remove();
            }
        }
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onSleep(PlayerBedEnterEvent evt) {
        evt.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onWorldChange(PlayerTeleportEvent e) {
        if (e.getFrom().getWorld().equals(e.getTo().getWorld())) return;
        // prevent the player from entering vanilla worlds
        String destinationWorldName = e.getTo().getWorld().getName();
        if (! ( destinationWorldName.equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE) ||
                destinationWorldName.equals(TerrariaHelper.Constants.WORLD_NAME_CAVERN) ||
                destinationWorldName.equals(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD))) {
            e.setCancelled(true);
            return;
        }
        // reset player stats after world change
        PlayerHelper.initPlayerStats(e.getPlayer(), true);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onExplode(EntityExplodeEvent e) {
        e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onVanillaProjectileSpawn(ProjectileLaunchEvent e) {
        if (e.isCancelled())
            return;
        Entity spawned = e.getEntity();
        switch (spawned.getType()) {
            case FISHING_HOOK: {
                EntityFishingHook hook = ((CraftFish) spawned).getHandle();
                if (!(hook instanceof TerrariaFishingHook)) {
                    e.setCancelled(true);
                    spawned.remove();
                }
                break;
            }
            case ARROW: {
                ProjectileSource source = e.getEntity().getShooter();
                if (source instanceof LivingEntity) {
                    LivingEntity shooter = (LivingEntity) source;
                    EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                            shooter, spawned.getVelocity(), EntityHelper.getAttrMap(shooter), "木箭");
                    shootInfo.properties.put("noGravityTicks", 0);
                    EntityHelper.spawnProjectile(shootInfo);
                }
                e.setCancelled(true);
                break;
            }
        }
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onVehicleHit(VehicleEntityCollisionEvent e) {
        e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onHandItemSwap(PlayerSwapHandItemsEvent e) {
        e.setCancelled(true);
    }

    // stop any portal from forming
    @EventHandler(priority = EventPriority.LOW)
    public void onPortalForm(PortalCreateEvent e) {
        e.setCancelled(true);
    }
    // liquid related
    @EventHandler(priority = EventPriority.LOW)
    public void onLiquidFlow(BlockFromToEvent e) {
        // blocks such as life fruit and Plantera's bulb should not be destroyed by flowing liquid.
        if (e.getToBlock().getType() == Material.SKULL)
            e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (e.isCancelled())
            return;
        Block liquidBlock = e.getBlockClicked().getRelative(e.getBlockFace());
        // water will evaporate quickly in underworld after placing
        if (e.getBucket() == Material.WATER_BUCKET &&
                e.getPlayer().getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD)) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                Block currLiquidBlock = liquidBlock.getLocation().getBlock();
                switch (currLiquidBlock.getType()) {
                    case WATER:
                    case STATIONARY_WATER:
                        currLiquidBlock.setType(Material.AIR);
                }
            }, 15);
        }
        // life fruit etc. should be broken by placing water here
        if (liquidBlock.getType() == Material.SKULL) {
            GameplayHelper.playerBreakBlock(liquidBlock, e.getPlayer());
        }
    }
    // stop most vanilla block tick mechanics
    @EventHandler(priority = EventPriority.LOW)
    public void onBlockForm(BlockFormEvent e) {
        if (e.isCancelled())
            return;
        switch (e.getNewState().getType()) {
            case OBSIDIAN:
            case STONE:
                break;
            case COBBLESTONE:
                e.getNewState().setType(Material.STONE);
                break;
            default:
                e.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onVanillaCropGrow(BlockGrowEvent e) {
        e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onPhysics(BlockPhysicsEvent e) {
        switch (e.getBlock().getType()) {
            // those blocks should not be destroyed by vanilla mechanism as long as the block below it is intact
            case SAPLING:
            case PUMPKIN_STEM:
            case YELLOW_FLOWER:
            case RED_ROSE:
            case RED_MUSHROOM:
            case BROWN_MUSHROOM:
            case CACTUS:
                if (e.getBlock().getRelative(BlockFace.DOWN).getType().isSolid())
                    e.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onExplode(ExplosionPrimeEvent e) {
        e.setCancelled(true);
    }
    // spider web can not be placed
    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() == Material.WEB)
            e.setCancelled(true);
    }

    // world related
    @EventHandler(priority = EventPriority.LOW)
    public void onChunkUnload(ChunkUnloadEvent evt) {
        if (evt.isCancelled()) return;
        // do not unload chunks with boss/celestial pillar/npc in it
        for (Entity entity : evt.getChunk().getEntities()) {
            Set<String> scoreboardTags = entity.getScoreboardTags();
            if (scoreboardTags.contains("isBOSS") || scoreboardTags.contains("isPillar") || scoreboardTags.contains("isNPC")) {
                evt.setCancelled(true);
                return;
            }
        }
        // if the chunk is unloaded
        for (Entity entity : evt.getChunk().getEntities()) {
            Set<String> scoreboardTags = entity.getScoreboardTags();
            // remove fallen stars on chunk unload
            if (scoreboardTags.contains("isFallenStar") ) {
                entity.removeScoreboardTag("isFallenStar");
                entity.remove();
            }
        }
    }
}
