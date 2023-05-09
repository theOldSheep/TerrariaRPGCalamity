package terraria.entity.minion;

import net.minecraft.server.v1_12_R1.EntityZombieHusk;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import terraria.util.EntityHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class MinionHusk extends EntityZombieHusk {
    org.bukkit.entity.Player owner;
    int damageInvincibilityTicks, index, minionSlot, minionSlotMax;
    double basicDamage;
    org.bukkit.entity.Entity minionInList;
    boolean hasContactDamage, hasTeleported, protectOwner, sentryOrMinion, targetNeedLineOfSight;
    String minionType;
    ArrayList<Entity> damageCD;
    ItemStack originalStaff;
    HashMap<String, Double> attrMap;
    HashMap<String, Object> extraVariables = new HashMap<>();
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public MinionHusk(World world) {
        super(world);
        die();
    }
    public MinionHusk(org.bukkit.entity.Player owner, int minionSlot, int minionSlotMax,
                       boolean sentryOrMinion, boolean hasContactDamage,
                       String minionType, HashMap<String, Double> attrMap, ItemStack originalStaff) {
        this(owner, minionSlot, minionSlotMax, null, sentryOrMinion, hasContactDamage, minionType, attrMap, originalStaff);
    }
    public MinionHusk(org.bukkit.entity.Player owner, int minionSlot, int minionSlotMax,
                       org.bukkit.entity.Entity minionInList, boolean sentryOrMinion, boolean hasContactDamage,
                       String minionType, HashMap<String, Double> attrMap, ItemStack originalStaff) {
        super(((CraftWorld) owner.getWorld()).getHandle());
        this.owner = owner;
        this.minionSlot = minionSlot;
        this.minionSlotMax = minionSlotMax;
        if (minionInList == null)
            this.minionInList = this.getBukkitEntity();
        else
            this.minionInList = minionInList;
        this.sentryOrMinion = sentryOrMinion;
        this.hasContactDamage = hasContactDamage;
        this.minionType = minionType;
        this.hasTeleported = false;
        this.targetNeedLineOfSight = true;
        this.protectOwner = true;
        this.damageInvincibilityTicks = 15;
        this.damageCD = new ArrayList<>();
        this.attrMap = attrMap;
        this.originalStaff = originalStaff;
        this.basicDamage = attrMap.getOrDefault("damage", 10d);
        // does not get removed if far away.
        this.persistent = true;
        // set location
        Location spawnLoc = owner.getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        setHeadRotation(0);
        // add to world
        ((CraftWorld) owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // attributes etc.
        setBaby(true);
        EntityHelper.setMetadata(getBukkitEntity(), "attrMap", attrMap);
        addScoreboardTag("isMinion");
        EntityHelper.setMetadata(getBukkitEntity(), "damageSourcePlayer", owner);
        EntityHelper.setDamageType(getBukkitEntity(), EntityHelper.DamageType.SUMMON);
        addScoreboardTag("noDamage");
        addScoreboardTag("noMelee");
        setCustomName(minionType);
        setCustomNameVisible(true);
        // prevent items being picked up by the minion
        ((Husk) getBukkitEntity()).getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        // minions other than dwarves should not need any goal selector etc. that are redundant and laggy
        if (!minionType.equals("矮人")) {
            this.goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        }
        this.targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        switch (minionType) {
            case "矮人": {
                getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0.5d);
                ((LivingEntity) getBukkitEntity()).addPotionEffect(new PotionEffect(
                        PotionEffectType.JUMP,
                        999999,
                        1,
                        false,
                        false
                ));
                break;
            }
        }
        getAttributeInstance(GenericAttributes.maxHealth).setValue(444);
        getAttributeInstance(GenericAttributes.FOLLOW_RANGE).setValue(444);
        setHealth(444f);
    }
    // basic ticking
    @Override
    public void B_() {
        super.B_();
        // update attribute
        if (this.ticksLived % 10 == 0) {
            MinionHelper.updateAttrMap(this.attrMap, owner, originalStaff);
        }
        // validation
        if (!MinionHelper.validate(this.getBukkitEntity(), owner, minionSlot, minionSlotMax, minionInList, sentryOrMinion)) {
            die();
            return;
        }
        // setup target
        if (!MinionHelper.checkTargetIsValidEnemy(this, ((CraftPlayer) owner).getHandle(), getGoalTarget(), targetNeedLineOfSight)
                || ticksLived % 10 == 0)
            MinionHelper.setTarget(this, ((CraftPlayer) owner).getHandle(), targetNeedLineOfSight, protectOwner);
        // extra ticking AI
        Vector velocity = new Vector(motX, motY, motZ);
        Collection<Entity> allMinions = (Collection<Entity>) EntityHelper.getMetadata(owner, sentryOrMinion ? "sentries" : "minions").value();
        LivingEntity target, minionBukkit = (LivingEntity) getBukkitEntity();
        if (getGoalTarget() != null) target = (LivingEntity) (getGoalTarget().getBukkitEntity());
        else target = owner;
        boolean targetIsOwner = target == owner;
        switch (minionType) {
            case "矮人": {
                if (!targetIsOwner && ticksLived % 12 == 0) {
                    Vector v = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                    double dist = v.length();
                    if (dist > 1e-9) {
                        v.multiply(2.5 / dist);
                        EntityHelper.spawnProjectile(minionBukkit, v, attrMap, "投矛");
                    }
                }
                break;
            }
        }
        // strike all enemies in path
        if (hasContactDamage) {
            MinionHelper.handleContactDamage(this, hasTeleported, isBaby() ? 0.5 : 1, basicDamage, damageCD, damageInvincibilityTicks);
        }
        motX = velocity.getX();
        motY = velocity.getY();
        motZ = velocity.getZ();
        // reset teleported
        hasTeleported = false;
        // add 1 to index
        index ++;
    }
}
