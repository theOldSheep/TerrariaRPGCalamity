package terraria.entity.boss.postMoonLord.supremeCalamitas;

import net.minecraft.server.v1_12_R1.PacketPlayOutBoss;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.entity.boss.BossHelper;
import terraria.entity.projectile.RotatingRingProjectile;
import terraria.util.AimHelper;
import terraria.util.EntityHelper;
import terraria.util.PlayerHelper;

public class SupremeCalamitasBHRing implements ISupremeCalamitasBH {
    static final int WAIT_TICKS = 20 * 5;
    static final int DURATION_TICKS = 20 * 18;
    static final double HOVER_DISTANCE = 32;
    static final double ANGLE_CHANGE = 0.2, RADIUS_GROW = 2;
    static final int NUM_PROJ = 16;
    static final String[][] BEGIN_MSG = new String[][] {
            new String[]{"距离你上次勉强才击败我的克隆体也没过多久。那玩意就是个失败品，不是么？"},
            new String[]{"想要加入亡魂与其共舞吗？"},
    };
    final double healthRatio;
    EntityHelper.ProjectileShootInfo shootInfo;
    SupremeCalamitas owner;
    int index = 0;

    SupremeCalamitasBHRing(SupremeCalamitas owner, double healthRatio) {
        this.owner = owner;
        this.healthRatio = healthRatio;

        this.shootInfo = new EntityHelper.ProjectileShootInfo(owner.getBukkitEntity(), new Vector(),
                SupremeCalamitas.attrMapPrjHigh, "灾厄舞魂");
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

        owner.bossbar.title = CraftChatMessage.fromString(SupremeCalamitas.BOSS_TYPE.msgName + " - 慰灵「亡魂之舞」", true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);
    }

    @Override
    public void finish() {
        owner.bossbar.title = CraftChatMessage.fromString(SupremeCalamitas.BOSS_TYPE.msgName, true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);
    }

    @Override
    public void tick() {
        // movement
        owner.generalMovement(HOVER_DISTANCE);
        // do not attack for the first few ticks
        if (index >= WAIT_TICKS) {
            // spawn projectiles
            if (index % 15 == 0) {
                spawnRing(RotatingRingProjectile.RotationDirection.CLOCKWISE, 0);
                spawnRing(RotatingRingProjectile.RotationDirection.COUNTER_CLOCKWISE, 90);
            }
        }
        index ++;
    }

    private void spawnRing(RotatingRingProjectile.RotationDirection dir, double initAngle) {
        LivingEntity ownerLivingEntity = (LivingEntity) owner.getBukkitEntity();
        Player target = owner.target;
        RotatingRingProjectile.RingProperties ringProperties = new RotatingRingProjectile.RingProperties.Builder()
                .withCenterLocation(ownerLivingEntity.getEyeLocation())
                .withRotationDirection(dir)
                .withAngleChange(ANGLE_CHANGE)
                .withInitialRotationDegrees(initAngle)
                .withRadiusMultiplier(RADIUS_GROW)
                .withPlayer(target)
                .build();

        RotatingRingProjectile.summonRingOfProjectiles(shootInfo, target, ringProperties, Math.random() * 360, NUM_PROJ);
    }
}
