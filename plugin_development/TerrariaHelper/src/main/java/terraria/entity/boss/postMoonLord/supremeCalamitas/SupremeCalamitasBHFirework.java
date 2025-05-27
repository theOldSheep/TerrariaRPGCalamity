package terraria.entity.boss.postMoonLord.supremeCalamitas;

import net.minecraft.server.v1_12_R1.PacketPlayOutBoss;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;
import terraria.entity.boss.BossHelper;
import terraria.util.AimHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.PlayerHelper;

import java.util.Collection;
import java.util.HashSet;

public class SupremeCalamitasBHFirework implements ISupremeCalamitasBH {
    static final int WAIT_TICKS = 20 * 5;
    static final int DURATION_TICKS = 20 * 18;
    static final double HOVER_DISTANCE = 32;
    static final double MAIN_PROJECTILE_SPEED = 1.5;
    static final double BLAST_SPEED = 3.25;
    static final AimHelper.AimHelperOptions BLAST_AIM_HELPER;
    static {
        BLAST_AIM_HELPER = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(BLAST_SPEED)
                .setAccelerationMode(true);
    }
    static final String[][] BEGIN_MSG = new String[][] {
            new String[]{"如果你想感受一下四级烫伤的话，你可算是找对人了。"},
            new String[]{"你享受地狱之旅么？"},
    };
    static final String[][] END_MSG = new String[][] {
            new String[]{"他日若你魂销魄散，你会介意我将你的骨头和血肉融入我的造物中吗？"},
            new String[]{"真奇怪，你应该已经死了才对......"},
    };
    final double healthRatio;
    EntityHelper.ProjectileShootInfo shootInfo;
    SupremeCalamitas owner;
    int index = 0;
    Collection<Projectile> projectiles;

    SupremeCalamitasBHFirework(SupremeCalamitas owner, double healthRatio) {
        this.owner = owner;
        this.healthRatio = healthRatio;

        this.shootInfo = new EntityHelper.ProjectileShootInfo(owner.getBukkitEntity(), new Vector(),
                SupremeCalamitas.attrMapPrjExtreme, "焰火亡魂");
        projectiles = new HashSet<>();
    }

    @Override
    public boolean isStrict() {
        return true;
    }

    @Override
    public double healthRatio() {
        return healthRatio;
    }

    @Override
    public boolean inProgress() {
        return index <= DURATION_TICKS;
    }

    @Override
    public void refresh() {
        this.index = 0;
    }

    @Override
    public void begin() {
        BossHelper.sendBossMessages(20, 0, owner.getBukkitEntity(),
                SupremeCalamitas.MSG_PREFIX, BEGIN_MSG[PlayerHelper.hasDefeated(owner.target, SupremeCalamitas.BOSS_TYPE) ? 0 : 1]);

        owner.bossbar.title = CraftChatMessage.fromString(SupremeCalamitas.BOSS_TYPE.msgName + " - 硫焰「亡魂之雨」", true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);
    }

    @Override
    public void finish() {
        owner.bossbar.title = CraftChatMessage.fromString(SupremeCalamitas.BOSS_TYPE.msgName, true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);
        // do not trigger if ended by losing target
        if (!inProgress()) {
            BossHelper.sendBossMessages(20, 0, owner.getBukkitEntity(),
                    SupremeCalamitas.MSG_PREFIX, END_MSG[PlayerHelper.hasDefeated(owner.target, SupremeCalamitas.BOSS_TYPE) ? 0 : 1]);
        }
        // remove projectiles
        for (Projectile proj : projectiles) {
            proj.remove();
        }
    }

    @Override
    public void tick() {
        LivingEntity ownerLivingEntity = (LivingEntity) owner.getBukkitEntity();
        Player target = owner.target;
        // movement
        owner.generalMovement(HOVER_DISTANCE);
        // do not attack for the first few ticks
        if (index >= WAIT_TICKS) {
            // spawn projectiles
            // upward, main projectile
            if (index % 6 == 0) {
                Vector horizontalOffset = ownerLivingEntity.getEyeLocation().subtract(target.getEyeLocation()).toVector();
                horizontalOffset.setY(0);
                double velLen = horizontalOffset.length();
                if (velLen < 1e-5) {
                    velLen = 1;
                    horizontalOffset = new Vector(1, 0, 0);
                }
                horizontalOffset.multiply(1 / velLen);
                shootInfo.shootLoc = ownerLivingEntity.getEyeLocation();
                shootInfo.velocity = horizontalOffset.clone().multiply(-1).add(new Vector(0, 2, 0));
                MathHelper.setVectorLength(shootInfo.velocity, MAIN_PROJECTILE_SPEED);
                projectiles.add( EntityHelper.spawnProjectile(shootInfo) );
            }
            // blast projectiles
            for (Projectile proj : projectiles) {
                if (proj.isDead()) continue;
                if (proj.getTicksLived() >= 20) {
                    EntityHelper.ProjectileShootInfo blastShootInfo = owner.shootInfoHellBlast;
                    blastShootInfo.shootLoc = proj.getLocation();
                    for (Vector velocity : MathHelper.getEvenlySpacedProjectileDirections(
                            12, 25,
                            target, blastShootInfo.shootLoc, BLAST_AIM_HELPER, BLAST_SPEED)) {
                        blastShootInfo.velocity = velocity;
                        EntityHelper.spawnProjectile(blastShootInfo);
                    }
                    proj.remove();
                }
            }
            // purge projectiles set
            if (index % 40 == 0) {
                HashSet<Projectile> purgedProj = new HashSet<>();
                for (Projectile proj : projectiles) {
                    if (proj.isDead()) continue;
                    purgedProj.add(proj);
                }
                projectiles = purgedProj;
            }
        }
        index ++;
    }
}
