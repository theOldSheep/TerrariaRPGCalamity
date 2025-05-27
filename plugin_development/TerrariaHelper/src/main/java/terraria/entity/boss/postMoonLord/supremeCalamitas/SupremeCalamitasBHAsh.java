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

public class SupremeCalamitasBHAsh implements ISupremeCalamitasBH {
    static final int WAIT_TICKS = 20 * 5;
    static final int DURATION_TICKS = 20 * 25;
    static final double HOVER_DISTANCE = 32;
    static final double PROJECTILE_SPEED = 2.75;
    static final AimHelper.AimHelperOptions BLAST_AIM_HELPER;
    static {
        BLAST_AIM_HELPER = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(PROJECTILE_SPEED)
                .setAccelerationMode(true);
    }
    static final String[][] BEGIN_MSG = new String[][] {
            new String[]{"这难道不令人激动么？"},
            new String[]{
                    "给我停下！",
                    "如果我在这里失败，我就再无未来可言。",
                    "一旦你战胜了我，你就只剩下一条道路。",
                    "而那条道路......同样也无未来可言。",
                    "这场战斗的输赢对你而言毫无意义！那你又有什么理由干涉这一切！"},
    };
    final double healthRatio;
    EntityHelper.ProjectileShootInfo shootInfo;
    SupremeCalamitas owner;
    int index = 0;
    Collection<Projectile> projectiles;

    SupremeCalamitasBHAsh(SupremeCalamitas owner, double healthRatio) {
        this.owner = owner;
        this.healthRatio = healthRatio;

        this.shootInfo = new EntityHelper.ProjectileShootInfo(owner.getBukkitEntity(), new Vector(),
                SupremeCalamitas.attrMapPrjHigh, "灾厄烬魂");
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

        owner.bossbar.title = CraftChatMessage.fromString(SupremeCalamitas.BOSS_TYPE.msgName + " - 终焉「湮灭余烬」", true)[0];
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
        owner.generalMovement(HOVER_DISTANCE);
        // do not attack for the first few ticks
        if (index >= WAIT_TICKS) {
            // spawn projectiles
            if (index % 12 == 0) {
                shootInfo.setLockedTarget(target);
                shootInfo.shootLoc = ownerLivingEntity.getEyeLocation();
                for (Vector shootDir : MathHelper.getEvenlySpacedProjectileDirections(
                        20, 21, target, shootInfo.shootLoc, PROJECTILE_SPEED)) {
                    shootInfo.velocity = shootDir;
                    projectiles.add( EntityHelper.spawnProjectile(shootInfo) );
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
