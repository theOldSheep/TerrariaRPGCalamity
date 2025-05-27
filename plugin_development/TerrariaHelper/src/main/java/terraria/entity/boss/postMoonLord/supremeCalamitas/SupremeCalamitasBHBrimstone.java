package terraria.entity.boss.postMoonLord.supremeCalamitas;

import net.minecraft.server.v1_12_R1.PacketPlayOutBoss;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;
import terraria.entity.boss.BossHelper;
import terraria.util.*;

import java.util.Collection;
import java.util.HashSet;

public class SupremeCalamitasBHBrimstone implements ISupremeCalamitasBH {
    static final int WAIT_TICKS = 20 * 5;
    static final int DURATION_TICKS = 20 * 18;
    static final double HOVER_DISTANCE = 32;
    static final double DART_SPEED = 3;
    static final double BLAST_SPEED = 3.5;
    static final String[][] BEGIN_MSG = new String[][] {
            new String[]{"自我上一次能和如此有趣的靶子一起回忆祂的魔法，已经过了很久了。"},
            new String[]{"你驾驭着强大的力量，但你使用这股力量只为了自己的私欲。"},
    };
    static final AimHelper.AimHelperOptions AIM_HELPER_BLAST;
    final double healthRatio;
    static {
        AIM_HELPER_BLAST = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(BLAST_SPEED)
                .setAccelerationMode(true);
    }
    EntityHelper.ProjectileShootInfo shootInfoBlast, shootInfoDart;

    SupremeCalamitas owner;
    int index = 0;
    Collection<Projectile> projectiles;

    SupremeCalamitasBHBrimstone(SupremeCalamitas owner, double healthRatio) {
        this.owner = owner;
        this.healthRatio = healthRatio;


        shootInfoDart = new EntityHelper.ProjectileShootInfo(owner.getBukkitEntity(), new Vector(),
                SupremeCalamitas.attrMapPrjHigh, "灾厄飞弹");
        shootInfoBlast = new EntityHelper.ProjectileShootInfo(owner.getBukkitEntity(), new Vector(),
                SupremeCalamitas.attrMapPrjExtreme, "灾厄亡魂");
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

        owner.bossbar.title = CraftChatMessage.fromString(SupremeCalamitas.BOSS_TYPE.msgName + " - 想起「硫邦之灵」", true)[0];
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
        if (index >= WAIT_TICKS && index % 10 == 0) {
            shootProjectile();
        }
        index ++;
    }


    private void shootProjectile() {
        Location eyeLoc = ((LivingEntity) owner.getBukkitEntity()).getEyeLocation();
        // dart
        shootInfoDart.shootLoc = eyeLoc;
        for (Vector direction : MathHelper.getCircularProjectileDirections(
                9, 4, 45, owner.target, shootInfoDart.shootLoc, DART_SPEED)) {
            shootInfoDart.velocity = direction;
            EntityHelper.spawnProjectile(shootInfoDart);
        }
        // blast
        Vector direction = MathHelper.getDirection(eyeLoc,
                AimHelper.helperAimEntity(eyeLoc, owner.target, AIM_HELPER_BLAST), BLAST_SPEED);
        shootInfoBlast.velocity = direction;
        for (Vector offsetDir : MathHelper.getCircularProjectileDirections(
                5, 3, 90, direction, 2.5)) {
            shootInfoBlast.shootLoc = eyeLoc.clone().add(offsetDir);
            EntityHelper.spawnProjectile(shootInfoBlast);
        }
    }
}
