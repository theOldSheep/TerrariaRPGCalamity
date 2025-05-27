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

public class SupremeCalamitasBHEvaporation implements ISupremeCalamitasBH {
    static final int WAIT_TICKS = 20 * 5;
    static final int DURATION_TICKS = 20 * 18;
    static final double HOVER_DISTANCE = 16;
    static final double MAIN_PROJECTILE_SPEED = 3.9;
    static final double BLAST_SPEED = 3.25;
    static final String SPREAD_SCOREBOARD_TAG = "SPREAD";
    static AimHelper.AimHelperOptions BLAST_AIM_HELPER;
    static {
        BLAST_AIM_HELPER = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(BLAST_SPEED)
                .setAccelerationMode(true)
                .setRandomOffsetRadius(2);
    }
    static final String[][] BEGIN_MSG = new String[][] {
            new String[]{"我挺好奇，自我们第一次交手后，你是否有在梦魇中见到过这些？"},
            new String[]{"别想着逃跑。只要你还活着，痛苦就不会离你而去。"},
    };
    final double healthRatio;
    EntityHelper.ProjectileShootInfo shootInfo;
    SupremeCalamitas owner;
    int index = 0;
    Collection<Projectile> projectiles;

    SupremeCalamitasBHEvaporation(SupremeCalamitas owner, double healthRatio) {
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

        owner.bossbar.title = CraftChatMessage.fromString(SupremeCalamitas.BOSS_TYPE.msgName + " - 想起「蒸海硫火」", true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);
    }

    @Override
    public void finish() {
        owner.bossbar.title = CraftChatMessage.fromString(SupremeCalamitas.BOSS_TYPE.msgName, true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);
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
        owner.generalHover(HOVER_DISTANCE);
        // do not attack for the first few ticks
        if (index >= WAIT_TICKS) {
            // spawn projectiles
            shootInfo.shootLoc = ownerLivingEntity.getEyeLocation();
            shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, target.getEyeLocation(), MAIN_PROJECTILE_SPEED);
            Projectile newProj = EntityHelper.spawnProjectile(shootInfo);
            if (index % 10 == 0) newProj.addScoreboardTag(SPREAD_SCOREBOARD_TAG);
            projectiles.add( newProj );
            // blast projectiles
            for (Projectile proj : projectiles) {
                if (proj.isDead()) continue;
                if (proj.getTicksLived() >= 15) {
                    EntityHelper.ProjectileShootInfo blastShootInfo = owner.shootInfoHellBlast;
                    blastShootInfo.shootLoc = proj.getLocation();
                    double spreadAngle;
                    if (proj.getScoreboardTags().contains(SPREAD_SCOREBOARD_TAG)) {
                        spreadAngle = 21;
                        BLAST_AIM_HELPER.setAccelerationMode(true);
                    } else {
                        spreadAngle = 1;
                        BLAST_AIM_HELPER.setAccelerationMode(false);
                    }
                    for (Vector velocity : MathHelper.getEvenlySpacedProjectileDirections(
                            10, spreadAngle,
                            target, blastShootInfo.shootLoc, BLAST_AIM_HELPER, BLAST_SPEED)) {
                        blastShootInfo.velocity = velocity;
                        EntityHelper.spawnProjectile(blastShootInfo);
                    }
                    proj.remove();
                }
            }
            // purge projectiles set
            if (index % 10 == 0) {
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
