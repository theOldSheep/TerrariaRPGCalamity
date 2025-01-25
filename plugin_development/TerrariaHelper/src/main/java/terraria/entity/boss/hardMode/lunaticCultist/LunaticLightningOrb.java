package terraria.entity.boss.hardMode.lunaticCultist;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;

public class LunaticLightningOrb extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.LUNATIC_CULTIST;
    HashMap<String, Double> attrMap;
    Player target = null;
    // other variables and AI
    LunaticCultist owner;

    static GenericHelper.ParticleLineOptions lightningParticleOption;
    static GenericHelper.StrikeLineOptions lightningStrikeOption;
    static {
        lightningParticleOption = new GenericHelper.ParticleLineOptions()
                .setVanillaParticle(false)
                .setParticleColor("255|255|255")
                .setTicksLinger(20);
        lightningStrikeOption = new GenericHelper.StrikeLineOptions()
                .setParticleInfo(lightningParticleOption)
                .setLingerTime(20)
                .setLingerDelay(1);
    }


    private void strikeLightningBolt() {
        Location startLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        Vector strikeDirection = MathHelper.getDirection(startLoc, target.getEyeLocation(), 1);
        GenericHelper.handleStrikeLightning(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                MathHelper.getVectorYaw(strikeDirection), MathHelper.getVectorPitch(strikeDirection),
                80, 8, 1, 5, 2, "255|255|255",
                new ArrayList<>(), attrMap, lightningStrikeOption);
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = owner.target;
            // disappear if no target is available or owner is dead
            if (target == null || !owner.isAlive()) {
                die();
                return;
            }
            // if target is valid, attack
            else {
                // thunder strike
                if (ticksLived % 10 == 0) {
                    strikeLightningBolt();
                }
                // timeout
                if (ticksLived > 50)
                    die();
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // no collision dmg for lunatic cultist clone
    }
    // default constructor to handle chunk unload
    public LunaticLightningOrb(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public LunaticLightningOrb(Player summonedPlayer, LunaticCultist owner) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation().add(0, 5, 0);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        setCustomName("闪电珠");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("noDamage");
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 660d);
            attrMap.put("knockback", 4d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MAGIC);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // size and other properties
        {
            setSize(3, false);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // AI
        AI();
    }
}
