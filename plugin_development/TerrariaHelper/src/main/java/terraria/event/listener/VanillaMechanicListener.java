package terraria.event.listener;

import net.minecraft.server.v1_12_R1.EntityFishingHook;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftFish;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import terraria.TerrariaHelper;
import terraria.entity.others.TerrariaFishingHook;
import terraria.util.*;

import java.util.ArrayList;
import java.util.Set;


public class VanillaMechanicListener implements Listener {
    // deny Vanilla Minecraft's compensation-tick
    public static final long MS_TO_NS_RATE = 1000000;
    public static final long NORMAL_TICK_MS = 50;
    public static final long NORMAL_TICK_NS = NORMAL_TICK_MS * MS_TO_NS_RATE;
    // when the server tick is running ahead by at least this amount, the mechanism would take effect.
    public static final long BUFFER_THRESHOLD = (long) (TerrariaHelper.optimizationConfig.getDouble(
            "tickCompensationSetting.bufferThreshold", 1) * NORMAL_TICK_NS);
    // when the server tick is running ahead by at least this amount, the mechanism would halt 50 ms each tick.
    public static final long MAX_EFFECT_THRESHOLD = (long) (TerrariaHelper.optimizationConfig.getDouble(
            "tickCompensationSetting.bufferThresholdMax", 5) * NORMAL_TICK_NS);
    // only trigger this effect when a boss is alive
    public static final boolean BOSS_ACTIVE_ONLY = TerrariaHelper.optimizationConfig.getBoolean(
            "tickCompensationSetting.bossFightOnly", true);
    long lastTickNS = System.nanoTime(), bufferNS = 0;
    private void tick() {
        long currNanoTime = System.nanoTime();
        long timeDiff = currNanoTime - lastTickNS;
        lastTickNS = currNanoTime;

        // if boss is not alive and this mechanism is therefore disabled
        if (BossHelper.bossMap.isEmpty() && BOSS_ACTIVE_ONLY) {
            bufferNS = 0;
            return;
        }

        // modify the buffer
        bufferNS += (NORMAL_TICK_NS - timeDiff);
//        Bukkit.broadcastMessage("BUFFER: " + (double) bufferNS / MS_TO_NS_RATE + ", timediff " + (double) timeDiff / MS_TO_NS_RATE);
        // positive buffer size enough to trigger tick compensation
        if (bufferNS > BUFFER_THRESHOLD) {
            int timeSleepMS = (int) Math.ceil(NORMAL_TICK_MS * Math.min(1d,
                    (double) (bufferNS - BUFFER_THRESHOLD) / (MAX_EFFECT_THRESHOLD - BUFFER_THRESHOLD)) );
            bufferNS -= timeSleepMS * MS_TO_NS_RATE;
//            Bukkit.broadcastMessage("SLP:" + timeSleepMS + " -> BUFFER: " + (double) bufferNS / MS_TO_NS_RATE +
//                    "Compensated len: " + (timeSleepMS + (double) (timeDiff) / MS_TO_NS_RATE) );
            try {
                Thread.sleep(timeSleepMS);
            } catch (Exception ignored) {
            }
        }
        // excessive negative buffer size is not allowed
        if (bufferNS < -BUFFER_THRESHOLD) {
            bufferNS = -BUFFER_THRESHOLD;
        }
    }
    public VanillaMechanicListener() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), this::tick, 1, 1);
    }
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
    public void onAirChange(EntityAirChangeEvent e) {
        if (e.getEntity() instanceof Player) {
            Player ply = (Player) e.getEntity();
            MetadataValue mtv = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_AIR);
            if (mtv != null && e.getAmount() != mtv.asInt())
                e.setCancelled(true);
        }
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
        // remove sentries, minions and mount
        if (! e.isCancelled()) {
            double distSqr = e.getFrom().getWorld() != e.getTo().getWorld() ? Double.MAX_VALUE :
                            e.getFrom().distanceSquared(e.getTo());
            // remove sentry and minion on teleport to somewhere far away ( another world or 30 blocks away )
            if (distSqr > 900) {
                // sentry
                for (Entity entity :
                        ((ArrayList<Entity>) EntityHelper.getMetadata(e.getPlayer(), EntityHelper.MetadataName.PLAYER_SENTRY_LIST).value()) ) {
                    entity.remove();
                }
                // minion
                for (Entity entity :
                        ((ArrayList<Entity>) EntityHelper.getMetadata(e.getPlayer(), EntityHelper.MetadataName.PLAYER_MINION_LIST).value()) ) {
                    entity.remove();
                }
            }
            // dismount on any teleport 4 blocks away
            if (distSqr > 16) {
                Entity mount = PlayerHelper.getMount(e.getPlayer());
                if (mount != null)
                    mount.remove();
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
                e.setCancelled(true);
                break;
            }
        }
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
        else
            WorldHelper.makeEmptyBlock(e.getToBlock(), false);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (WorldHelper.isSpawnProtected(e.getBlockClicked().getLocation(), e.getPlayer())) {
            PlayerHelper.sendActionBar(e.getPlayer(), "请勿破坏出生点附近的方块！");
            e.setCancelled(true);
        }
        WorldHelper.makeEmptyBlock(e.getBlockClicked().getRelative(e.getBlockFace() ), false);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (WorldHelper.isSpawnProtected(
                e.getBlockClicked().getRelative(e.getBlockFace() ).getLocation(), e.getPlayer())) {
            PlayerHelper.sendActionBar(e.getPlayer(), "请勿在出生点附近放置方块！");
            e.setCancelled(true);
        }
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
            case MELON_STEM:
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
    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() == Material.WEB)
            e.setCancelled(true);
        else if (WorldHelper.isSpawnProtected(e.getBlock().getLocation(), e.getPlayer())) {
            PlayerHelper.sendActionBar(e.getPlayer(), "请勿在出生点附近放置方块！");
            e.setCancelled(true);
        }
    }

    // world related
    @EventHandler(priority = EventPriority.LOW)
    public void onChunkUnload(ChunkUnloadEvent evt) {
        if (evt.isCancelled()) return;
        // do not unload chunks with boss/celestial pillar/npc near it
        for (int i = -1; i <= 1; i ++)
            for (int j = -1; j <= 1; j ++) {
                int chunkX = evt.getChunk().getX() + i, chunkZ = evt.getChunk().getZ() + j;
                if (! evt.getWorld().isChunkLoaded(chunkX, chunkZ) ) {
                    continue;
                }
                Chunk testChunk = evt.getWorld().getChunkAt(chunkX, chunkZ);
                for (Entity entity : testChunk.getEntities()) {
                    Set<String> scoreboardTags = entity.getScoreboardTags();
                    if (scoreboardTags.contains("isBOSS") || scoreboardTags.contains("isPillar") || scoreboardTags.contains("isNPC")) {
                        evt.setCancelled(true);
                        return;
                    }
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
            // remove projectiles on chunk unload
            else if (entity instanceof Projectile) {
                entity.remove();
            }
        }
    }
}
