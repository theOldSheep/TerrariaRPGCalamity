package terraria.entity.others;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MiscDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.DroppedItemWatcher;
import net.minecraft.server.v1_12_R1.*;
import net.minecraft.server.v1_12_R1.MathHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.HitEntityInfo;
import terraria.util.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class TerrariaMinecart extends EntityMinecartRideable {
    static HashMap<String, MinecartType> minecartTypeHashMap = new HashMap<>();
    static
    enum MinecartType {
        // total speed: 0.5
        NORMAL("矿车", 1, 0.25, 30, 135),
        // total speed: 0.7
        MECHANIC("机械矿车", 1,0.35, 50, 360),
        // total speed: 1
        RAINBOW("彩虹猫矿车", 3,0.25, 100, 666),
        // total speed: 1.2
        GOD_CART("神明矿车", 3,0.3, 175, 1250);
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
    MinecartType type;
    HashMap<String, Double> attrMap;
    ArrayList<org.bukkit.entity.Entity> damageCD = new ArrayList<>();
    boolean collisionDamage = false;
    Player owner;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public TerrariaMinecart(World world) {
        super(world);
        die();
    }
    public TerrariaMinecart(String playerTool, Location spawnLoc, Player owner) {
        super(((CraftWorld) spawnLoc.getWorld()).getHandle());
        // setup variables
        this.type = minecartTypeHashMap.getOrDefault(playerTool, MinecartType.NORMAL);
        this.maxSpeed = type.maxSpeed;
        this.owner = owner;
        // set location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        setHeadRotation(0);
        // add to world
        ((CraftWorld) spawnLoc.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // other settings
        addScoreboardTag("noDamage");
        addScoreboardTag("ignoreCanDamageCheck");
        // attribute
        {
            attrMap = new HashMap<>();
        }
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_SOURCE, owner);

        setCustomName( this.type.name );
        setCustomNameVisible(false);

        bukkitEntity.addPassenger(owner);
    }


    @Override
    public void B_() {
        initCollisionDamage();
        super.B_();
        handleCollisionDamage();
        for (int i = 0; i < type.movementTickAdditional; i ++) {
            super.B_();
            handleCollisionDamage();
        }
        // remove on empty
        if (passengers.isEmpty()) {
            die();
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
        Vector initLoc = new Vector(bb.a + xWidth, bb.b + height, bb.c + zWidth);
        Set<HitEntityInfo> toDamage = HitEntityInfo.getEntitiesHit(bukkitEntity.getWorld(),
                initLoc, initLoc.clone().add(bukkitEntity.getVelocity()),
                xWidth * 2, height * 2, zWidth * 2,
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
