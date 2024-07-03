package terraria.entity.others;

import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityMinecartRideable;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.HitEntityInfo;
import terraria.util.EntityHelper;
import terraria.util.PlayerHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class TerrariaMinecart extends EntityMinecartRideable {
    static HashMap<String, MinecartType> minecartTypeHashMap = new HashMap<>();
    enum MinecartType {
        // total speed: 0.675
        NORMAL("矿车", 2, 0.225, 30, 135),
        // total speed: 1
        MECHANIC("机械矿车", 3,0.25, 50, 360),
        // total speed: 1.375
        RAINBOW("彩虹猫矿车", 4,0.275, 100, 666),
        // total speed: 1.6
        GOD_CART("神明矿车", 3,0.4, 175, 1250);
        public final String name;
        public final int movementTickAdditional;
        public final double maxSpeed, damageBasic, damageAdaptive;
        MinecartType(String name, int movementTickAdditional, double maxSpeed, double damageBasic, double damageMax) {
            this.name = name;
            this.maxSpeed = maxSpeed;
            this.movementTickAdditional = movementTickAdditional;
            this.damageBasic = damageBasic;
            this.damageAdaptive = damageMax - damageBasic;

            minecartTypeHashMap.put(name, this);
        }
    }
    // Leave this line intact. Otherwise, the MinecartType will not initialize.
    static {
        if (MinecartType.values().length > 0)
            TerrariaHelper.LOGGER.log(Level.SEVERE, "No minecart type is currently available. Initializing the minecart mapping.");
    }
    MinecartType type;
    HashMap<String, Double> attrMap;
    HashSet<org.bukkit.entity.Entity> damageCD = new HashSet<>();
    boolean collisionDamage = false;
    Player owner;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public TerrariaMinecart(World world) {
        super(world);
        die();
    }
    public TerrariaMinecart(String playerTool, Location spawnLoc, Player owner) {
        super(((CraftWorld) spawnLoc.getWorld()).getHandle());
        // if no proper minecart is provided, cancel this initialization
        if (! minecartTypeHashMap.containsKey(playerTool)) {
            die();
            return;
        }
        // setup variables
        this.type = minecartTypeHashMap.get(playerTool);
        this.maxSpeed = type.maxSpeed;
        this.owner = owner;
        // set location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        setHeadRotation(0);
        // add to world
        ((CraftWorld) spawnLoc.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // other settings
        addScoreboardTag("isMount");
        addScoreboardTag("ignoreCanDamageCheck");
        // attribute
        {
            attrMap = new HashMap<>();
        }
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_SOURCE, owner);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, owner);

        setCustomName( this.type.name );
        setCustomNameVisible(false);

        bukkitEntity.addPassenger(owner);
    }


    @Override
    public void B_() {
        // remove when dismounted
        if (! bukkitEntity.getPassengers().contains(owner)) {
            die();
            return;
        }
        initCollisionDamage();
        super.B_();
        handleCollisionDamage();
        // additional ticking available only if owner is not targeted by a boss
        if (! PlayerHelper.isTargetedByBOSS(owner)) {
            for (int i = 0; i < type.movementTickAdditional; i++) {
                super.B_();
                handleCollisionDamage();
            }
        }
    }

    public void initCollisionDamage() {
        double speedRatio = bukkitEntity.getVelocity().length() / maxSpeed;
        if (speedRatio < 0.1) {
            collisionDamage = false;
            return;
        }
        collisionDamage = true;
        double damage = type.damageBasic + speedRatio * type.damageAdaptive;
        // knockback force between 3 and 25
        double knockback = 3 + speedRatio * 22;
        attrMap.put("damage", damage);
        attrMap.put("knockback", knockback);
    }
    // handle collision
    public void handleCollisionDamage() {
        if (!collisionDamage)
            return;
        AxisAlignedBB bb = getBoundingBox();
        double xWidth = (bb.d - bb.a) / 2, zWidth = (bb.f - bb.c) / 2, height = (bb.e - bb.b) / 2;
        // collision damage also includes the player's height
        Vector initLoc = new Vector(bb.a + xWidth, bb.b + height + 1, bb.c + zWidth);
        Set<HitEntityInfo> toDamage = HitEntityInfo.getEntitiesHit(bukkitEntity.getWorld(),
                initLoc, initLoc.clone().add(bukkitEntity.getVelocity()),
                xWidth * 1.25, height * 1.25 + 1, zWidth * 1.25,
                (Entity entity) -> EntityHelper.checkCanDamage(bukkitEntity, entity.getBukkitEntity(), false));
        for (HitEntityInfo hitEntityInfo : toDamage) {
            org.bukkit.entity.Entity victimBukkit = hitEntityInfo.getHitEntity().getBukkitEntity();
            // do not collide with passenger
            if (bukkitEntity.getPassengers().contains(victimBukkit))
                continue;
            if (!damageCD.contains(victimBukkit)) {
                EntityHelper.damageCD(damageCD, victimBukkit, 10);
                EntityHelper.handleDamage(bukkitEntity, victimBukkit,
                        attrMap.getOrDefault("damage", 100d), EntityHelper.DamageReason.DIRECT_DAMAGE);
            }
        }
    }
}
