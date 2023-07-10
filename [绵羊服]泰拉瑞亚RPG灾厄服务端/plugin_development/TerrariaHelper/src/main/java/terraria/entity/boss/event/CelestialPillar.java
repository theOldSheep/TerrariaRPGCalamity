package terraria.entity.boss.event;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.gameplay.EventAndTime;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class CelestialPillar extends EntityGiantZombie {
    // pillar-specific
    HashSet<Location> allMonsterDeathLoc = new HashSet<>();
    public BossBattleServer bossbar;
    public PillarTypes pillarType = null;
    int amountShieldLeft = SHIELD_AMOUNT;
    // static
    static GenericHelper.ParticleLineOptions particleOption = new GenericHelper.ParticleLineOptions()
            .setParticleColor("255|125|125")
            .setTicksLinger(3)
            .setWidth(0.1)
            .setStepsize(0.25);
    public static final int SHIELD_AMOUNT = 200;
    public static final double PARTICLE_LENGTH_PER_STEP = 1, EFFECTED_RADIUS = 200,
            EFFECTED_RADIUS_SQR = EFFECTED_RADIUS * EFFECTED_RADIUS;
    public enum PillarTypes {
        SOLAR("日耀柱"), NEBULA("星云柱"), STARDUST("星尘柱"), VORTEX("星璇柱");
        final String pillarName;
        PillarTypes(String name) {
            this.pillarName = name;
        }
    }
    // static helper functions
    public static CelestialPillar getPillarByType(PillarTypes typeToFind) {
        return EventAndTime.pillars.get(typeToFind);
    }
    public static void handlePillarMonsterDeath(PillarTypes typePillar, Location deathLoc) {
        CelestialPillar pillar = getPillarByType(typePillar);
        if (pillar != null && pillar.allMonsterDeathLoc.size() < pillar.amountShieldLeft) {
            pillar.allMonsterDeathLoc.add(deathLoc);
        }
    }
    public static void handleSinglePillarSpawn(Location centerLoc, PillarTypes pillarType) {
        Location actualSpawnLoc = centerLoc.add(Math.random() * 500 - 250, 0, Math.random() * 500 - 250);
        actualSpawnLoc = actualSpawnLoc.getWorld().getHighestBlockAt(actualSpawnLoc).getLocation().add(0, 20, 0);
        actualSpawnLoc.getChunk().load();
        EventAndTime.pillars.put(pillarType, new CelestialPillar(
                ((CraftWorld) centerLoc.getWorld()).getHandle(), pillarType, actualSpawnLoc) );
    }
    public static void handlePillarSpawn() {
        if (!EventAndTime.pillars.isEmpty())
            return;
        Bukkit.broadcastMessage("§d§l天界生物要入侵了！");
        ArrayList<PillarTypes> pillarsPendingSpawn = new ArrayList<>( Arrays.asList(PillarTypes.values()) );
        org.bukkit.World pillarWorld = Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE);
        handleSinglePillarSpawn(new Location(pillarWorld, 750, 0, 750),
                pillarsPendingSpawn.remove( (int) (Math.random() * pillarsPendingSpawn.size()) ));
        handleSinglePillarSpawn(new Location(pillarWorld, 750, 0, -750),
                pillarsPendingSpawn.remove( (int) (Math.random() * pillarsPendingSpawn.size()) ));
        handleSinglePillarSpawn(new Location(pillarWorld, -750, 0, 750),
                pillarsPendingSpawn.remove( (int) (Math.random() * pillarsPendingSpawn.size()) ));
        handleSinglePillarSpawn(new Location(pillarWorld, -750, 0, -750),
                pillarsPendingSpawn.remove( (int) (Math.random() * pillarsPendingSpawn.size()) ));
    }


    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public CelestialPillar(World world) {
        super(world);
        super.die();
    }
    public CelestialPillar(World world, PillarTypes pillarType, Location spawnLoc) {
        super(world);
        this.pillarType = pillarType;
        // set location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        setHeadRotation(0);
        // add to world
        world.addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // init boss bar
        bossbar = new BossBattleServer(
                CraftChatMessage.fromString(
                        String.format("%1$s [x = %2$.0f, z = %3$.0f]", pillarType.pillarName, spawnLoc.getX(), spawnLoc.getZ())
                        , true)[0],
                BossBattle.BarColor.WHITE, BossBattle.BarStyle.PROGRESS);
        for (Player ply : Bukkit.getOnlinePlayers()) {
            if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.GOLEM.msgName))
                bossbar.addPlayer( ((CraftPlayer) ply).getHandle() );
        }
        bossbar.setVisible(true);
        // setup generic information
        {
            this.persistent = true;
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
            setCustomName(pillarType.pillarName);
            setCustomNameVisible(true);
            bukkitEntity.addScoreboardTag("isMonster");
            bukkitEntity.addScoreboardTag("isPillar");
            bukkitEntity.addScoreboardTag("noDamage");
            goalSelector = new PathfinderGoalSelector(world.methodProfiler != null ? world.methodProfiler : null);
            targetSelector = new PathfinderGoalSelector(world.methodProfiler != null ? world.methodProfiler : null);
        }
        // setup attribute
        {
            HashMap<String, Double> attrMap;
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 1d);
            attrMap.put("defence", 40d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            // damage multiplier
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MAGIC);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init health and slime size
        {
            double health = 40000;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
    }
    @Override
    public void die() {
        super.die();
        // remove boss bar display
        bossbar.setVisible(false);
        // if pillar type is not initialized, simply return
        if (pillarType == null)
            return;
        // handle destruction
        EventAndTime.pillars.remove(pillarType);
        // drop item, between 75 and 125
        int amountToDropLeft = (int) (75 + Math.random() * 50);
        ItemStack itemToDrop = ItemHelper.getItemFromDescription(
                pillarType.pillarName.replace("柱", "碎片"));
        if (itemToDrop != null && itemToDrop.getType() != org.bukkit.Material.AIR) {
            while (amountToDropLeft > 0) {
                Location dropLoc = bukkitEntity.getLocation().add(
                        Math.random() * 12 - 6,
                        Math.random() * 20 - 8,
                        Math.random() * 12 - 6);
                int currentDropAmount = (int) (5 + Math.random() * 15);
                if (currentDropAmount > amountToDropLeft) currentDropAmount = amountToDropLeft;
                amountToDropLeft -= currentDropAmount;
                itemToDrop.setAmount(currentDropAmount);
                ItemHelper.dropItem(dropLoc, itemToDrop, false);
            }
        }
        // status message
        switch (EventAndTime.pillars.size()) {
            case 3:
                Bukkit.broadcastMessage("§d§o你的头脑变得麻木...");
                break;
            case 2:
                Bukkit.broadcastMessage("§d§o你痛苦不堪...");
                break;
            case 1:
                Bukkit.broadcastMessage("§d§o阴森的声音在你耳边萦绕不绝...");
                break;
            default:
                Player bossTarget = null;
                double minDistSqr = 1e7;
                for (Player checkPlayer : bukkitEntity.getWorld().getPlayers()) {
                    double currDistSqr = bukkitEntity.getLocation().distanceSquared(checkPlayer.getLocation());
                    if (currDistSqr < minDistSqr) {
                        minDistSqr = currDistSqr;
                        bossTarget = checkPlayer;
                    }
                }
                if (bossTarget != null) {
                    Bukkit.broadcastMessage("§d§o月亮末日慢慢逼近...");
                    BossHelper.spawnBoss(bossTarget, BossHelper.BossType.MOON_LORD);
                }
        }
    }
    @Override
    public void B_() {
        super.B_();
        // update boss bar
        if (amountShieldLeft > 0) {
            bossbar.setProgress( (float) amountShieldLeft / SHIELD_AMOUNT );
        }
        else {
            if (bossbar.color != BossBattle.BarColor.RED) {
                bossbar.color = BossBattle.BarColor.RED;
                bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
            }
            bossbar.setProgress( getHealth() / getMaxHealth() );
        }
        // handle monster death locations
        {
            HashSet<Location> updatedLocations = new HashSet<>();
            Location destination = ((LivingEntity) bukkitEntity).getEyeLocation();
            for (Location loc : allMonsterDeathLoc) {
                // init direction
                Vector direction = MathHelper.getDirection(loc, destination, PARTICLE_LENGTH_PER_STEP, true);
                double dirLen = direction.length();
                // handle finished location
                if (dirLen < PARTICLE_LENGTH_PER_STEP - 1e-5) {
                    amountShieldLeft --;
                    if (amountShieldLeft <= 0) {
                        removeScoreboardTag("noDamage");
                    }
                }
                // move the location towards destination and record unfinished location
                else {
                    loc.add(direction);
                    updatedLocations.add(loc);
                    // display particle
                    particleOption.setLength(dirLen);
                    GenericHelper.handleParticleLine(direction, loc, particleOption);
                }
            }
            // update death locations
            allMonsterDeathLoc = updatedLocations;
        }
    }
}
