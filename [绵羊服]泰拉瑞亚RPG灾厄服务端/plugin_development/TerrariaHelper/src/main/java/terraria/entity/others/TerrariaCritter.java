package terraria.entity.others;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MiscDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.DroppedItemWatcher;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.List;

public class TerrariaCritter extends EntitySilverfish {
    String type, critterCategory, idleSound;
    List<String> particle;
    int indexAI = -1;
    Vector acceleration = new Vector(), velocity = new Vector();


    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public TerrariaCritter(World world) {
        super(world);
        die();
    }
    public TerrariaCritter(String type, Location spawnLoc, String critterCategory) {
        super(((CraftWorld) spawnLoc.getWorld()).getHandle());
        // does not get removed if far away.
        this.persistent = true;
        // setup variables
        this.type = type;
        this.critterCategory = critterCategory;
        // set location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        setHeadRotation(0);
        // add to world
        ((CraftWorld) spawnLoc.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // disguise
        ItemStack disguiseItem = ItemHelper.getItemFromDescription(type);
        MiscDisguise disguise = new MiscDisguise(DisguiseType.DROPPED_ITEM, 0);
        ((DroppedItemWatcher) disguise.getWatcher() ).setItemStack(disguiseItem);
        DisguiseAPI.disguiseEntity(bukkitEntity, disguise);
        // underworld critters are fireproof
        switch (critterCategory) {
            case "Lavafly":
            case "MagmaSnail":
                EntityHelper.applyEffect(bukkitEntity, "黑曜石皮", 99999);
        }
        // some categories are unaffected by gravity
        switch (critterCategory) {
            case "Bird":
            case "Butterfly":
            case "Firefly":
            case "Lavafly":
                setNoGravity(true);
        }
        // health and other properties
        getAttributeInstance(GenericAttributes.maxHealth).setValue(20d);
        setHealth(20f);

        addScoreboardTag("isAnimal");

        setCustomName( disguiseItem.getItemMeta().getDisplayName() );
        setCustomNameVisible(true);

        MethodProfiler methodProfiler = world != null && world.methodProfiler != null ? world.methodProfiler : null;
        goalSelector = new PathfinderGoalSelector(methodProfiler);
        goalSelector.a(1, new PathfinderGoalRandomStrollLand(this, 1.0));
        targetSelector = new PathfinderGoalSelector(methodProfiler);
        // initialize idle sound and particle
        idleSound = TerrariaHelper.animalConfig.getString("idleSounds." + type);
        particle = TerrariaHelper.animalConfig.getStringList("particleColor." + type);
    }

    @Override
    public void B_() {
        super.B_();
        indexAI ++;
        // remove on timeout
        if (ticksLived > 1600) {
            die();
            return;
        }
        // additional features
        if (type.equals("七彩草蛉") && WorldHelper.BiomeType.getBiome(bukkitEntity.getLocation()) != WorldHelper.BiomeType.HALLOW) {
            die();
            return;
        }
        // additional movement
        switch (critterCategory) {
            // bird flees in addition to regular movement
            case "Bird": {
                // take no fall damage
                fallDistance = 0f;
                // 15 * 15
                double nearestDistSqr = 225;
                Player nearestPlayer = null;
                for (Entity entity : bukkitEntity.getNearbyEntities(15, 15, 15)) {
                    if (entity instanceof Player && PlayerHelper.isProperlyPlaying( (Player) entity )) {
                        double currDistSqr = bukkitEntity.getLocation().distanceSquared(entity.getLocation());
                        if (currDistSqr < nearestDistSqr) {
                            nearestDistSqr = currDistSqr;
                            nearestPlayer = (Player) entity;
                        }
                    }
                }
                // flee from players close to the entity
                if (nearestPlayer != null) {
                    indexAI = -15;
                    Location flyTargetLoc = bukkitEntity.getLocation();
                    flyTargetLoc.setY(nearestPlayer.getLocation().getY() + 5);
                    acceleration = MathHelper.getDirection(nearestPlayer.getLocation(),
                            flyTargetLoc, 10);
                }
                // slowly land after far away for 1 second
                else if (indexAI > 0) {
                    acceleration = new Vector(0, -0.2, 0);
                }
                // update velocity
                velocity.multiply(0.9);
                velocity.add(acceleration);
                if (velocity.lengthSquared() > 1e-5)
                    velocity.normalize().multiply(0.25);
                bukkitEntity.setVelocity(velocity);
                break;
            }
            case "Butterfly":
            case "Firefly":
            case "Lavafly": {
                // take no fall damage
                fallDistance = 0f;
                // initialize a new direction every 75 ticks
                if (indexAI % 75 == 0) {
                    acceleration = MathHelper.vectorFromYawPitch_quick(Math.random() * 360, 0);
                    acceleration.multiply(0.15);
                }
                // adjust vertical velocity every 5 ticks
                if (indexAI % 5 == 0) {
                    int checkDist = critterCategory.equals("Lavafly") ? 20 : 10;
                    Block checkBlock = bukkitEntity.getLocation().getBlock();
                    boolean flyUpward = false;
                    for (int i = 0; i < checkDist; i ++) {
                        if (checkBlock.getType() != Material.AIR) {
                            flyUpward = true;
                            break;
                        }
                        checkBlock = checkBlock.getRelative(0, -1, 0);
                    }
                    acceleration.setY( (flyUpward ? 1 : -1) * 0.075);
                }
                // update velocity
                velocity.multiply(0.9);
                velocity.add(acceleration);
                if (velocity.lengthSquared() > 1e-5)
                    velocity.normalize().multiply(0.15);
                bukkitEntity.setVelocity(velocity);
                break;
            }
            default: {
                if (type.endsWith("蚱蜢")) {
                    if (indexAI % 85 == 0 && bukkitEntity.isOnGround()) {
                        Vector jumpDir = MathHelper.vectorFromYawPitch_quick(Math.random() * 360, 0);
                        jumpDir.multiply(0.6);
                        jumpDir.setY(0.75);
                        bukkitEntity.setVelocity( jumpDir );
                    }
                }
            }
        }
        // idle sound
        if (ticksLived % 300 == 200 && idleSound != null) {
            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity." + idleSound + ".idle", 3f, 1f);
        }
        // particle affect
        if (ticksLived % 3 == 0 && particle.size() > 0) {
            String particleColor = particle.get( (int) (Math.random() * particle.size()) );
            Location particleLoc = bukkitEntity.getLocation();
            particleLoc.add(
                    Math.random() - 0.5,
                    Math.random() - 0.25,
                    Math.random() - 0.5);
            GenericHelper.handleParticleLine(new Vector(0, 1, 0), particleLoc,
                    new GenericHelper.ParticleLineOptions()
                            .setParticleColor(particleColor)
                            .setLength(0.1)
                            .setWidth(0.25));
        }
    }
}
