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
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.HitEntityInfo;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class TerrariaMinecart extends EntityMinecartRideable {
    static HashMap<String, MinecartType> minecartTypeHashMap = new HashMap<>();
    enum MinecartType {
        // total speed: 0.5
        NORMAL("矿车", 1, 0.25, 30, 170),
        // total speed: 0.7
        MECHANIC("机械矿车", 1,0.35, 100, 570),
        // total speed: 0.9
        RAINBOW("彩虹猫矿车", 2,0.3, 50, 310);
        public final String name;
        public final int movementTickAdditional;
        public final double maxSpeed, damageBasic, damageAdaptive;
        MinecartType(String name, int movementTickAdditional, double maxSpeed, double damageBasic, double damageAdaptive) {
            this.name = name;
            this.maxSpeed = maxSpeed;
            this.movementTickAdditional = movementTickAdditional;
            this.damageBasic = damageBasic;
            this.damageAdaptive = damageAdaptive;

            minecartTypeHashMap.put(name, this);
        }
    }
    MinecartType type;
    HashMap<String, Double> attrMap;
    ArrayList<org.bukkit.entity.Entity> damageCD = new ArrayList<>();
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
        double damage = type.damageBasic + speedRatio * type.damageAdaptive;
        double knockback = 2 + speedRatio * 8;
        attrMap.put("damage", damage);
        attrMap.put("knockback", knockback);
    }
    // handle collision
    public void handleCollisionDamage() {
        AxisAlignedBB bb = getBoundingBox();
        double xWidth = (bb.d - bb.a) / 2, zWidth = (bb.f - bb.c) / 2, height = (bb.e - bb.b) / 2;
        Vector initLoc = new Vector(bb.a + xWidth, bb.b + height, bb.c + zWidth);
        Set<HitEntityInfo> toDamage = HitEntityInfo.getEntitiesHit(bukkitEntity.getWorld(),
                initLoc, initLoc.clone().add(bukkitEntity.getVelocity()),
                xWidth, height, zWidth,
                (Entity entity) -> EntityHelper.checkCanDamage(bukkitEntity, entity.getBukkitEntity(), false));
        for (HitEntityInfo hitEntityInfo : toDamage) {
            org.bukkit.entity.Entity victimBukkit = hitEntityInfo.getHitEntity().getBukkitEntity();
            if (!damageCD.contains(victimBukkit)) {
                EntityHelper.damageCD(damageCD, victimBukkit, 10);
                EntityHelper.handleDamage(bukkitEntity, victimBukkit,
                        attrMap.getOrDefault("damage", 100d), EntityHelper.DamageReason.DIRECT_DAMAGE);
            }
        }
    }
}
