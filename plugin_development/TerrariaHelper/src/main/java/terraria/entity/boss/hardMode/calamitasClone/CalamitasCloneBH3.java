package terraria.entity.boss.hardMode.calamitasClone;

import net.minecraft.server.v1_12_R1.PacketPlayOutBoss;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;
import terraria.util.AimHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

import java.util.Collection;
import java.util.HashSet;

public class CalamitasCloneBH3 implements ICalamitasCloneBH {
    static final int WAIT_TICKS = 20;
    static final int SPLASH_DELAY = 25;
    static final int DURATION_TICKS = 20 * 15;
    static final double PROJECTILE_SPEED = 2;
    EntityHelper.ProjectileShootInfo shootInfoDirect, shootInfoSplash;
    CalamitasClone owner;
    int index = 0;
    Collection<Projectile> projectiles;
    AimHelper.AimHelperOptions aimHelperDirect, aimHelperSplash;

    CalamitasCloneBH3(CalamitasClone owner) {
        this.owner = owner;

        this.shootInfoDirect = new EntityHelper.ProjectileShootInfo(owner.getBukkitEntity(), new Vector(),
                CalamitasClone.attrMapHellFireball, "焰火亡魂");
        this.shootInfoSplash = new EntityHelper.ProjectileShootInfo(owner.getBukkitEntity(), new Vector(),
                CalamitasClone.attrMapHellFireball, "深渊亡魂");
        projectiles = new HashSet<>();

        aimHelperDirect = new AimHelper.AimHelperOptions("焰火亡魂")
                .setProjectileSpeed(PROJECTILE_SPEED)
                .setIntensity(0.4);
        aimHelperSplash = new AimHelper.AimHelperOptions("深渊亡魂")
                .setProjectileSpeed(PROJECTILE_SPEED);

        owner.bossbar.title = CraftChatMessage.fromString(CalamitasClone.BOSS_TYPE.msgName + " - □□「？？？？」", true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);
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
    public void finish() {
        owner.bossbar.title = CraftChatMessage.fromString(CalamitasClone.BOSS_TYPE.msgName, true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);
    }

    @Override
    public void tick() {
        LivingEntity ownerLivingEntity = (LivingEntity) owner.getBukkitEntity();
        Player target = owner.target;
        // movement
        owner.getBukkitEntity().setVelocity(MathHelper.getDirection(
                owner.getBukkitEntity().getLocation(), owner.target.getEyeLocation().add(new Vector(0, 12, 0)),
                CalamitasClone.SPEED, true));
        // do not attack for the first few ticks
        if (index >= WAIT_TICKS) {
            // spawn projectiles
            // downward projectiles
            if (index % 2 == 0) {
                shootInfoDirect.shootLoc = ownerLivingEntity.getEyeLocation();
                shootInfoDirect.velocity = MathHelper.getDirection(
                        shootInfoDirect.shootLoc,
                        AimHelper.helperAimEntity(ownerLivingEntity, target, aimHelperDirect),
                        PROJECTILE_SPEED);
                projectiles.add( EntityHelper.spawnProjectile(shootInfoDirect) );
            }
            // subsequent projectiles
            for (Projectile proj : projectiles) {
                if (proj.isDead()) continue;
                if (proj.getTicksLived() >= SPLASH_DELAY) {
                    aimHelperSplash.setRandomOffsetRadius(Math.random() * 5);
                    shootInfoSplash.shootLoc = proj.getLocation();
                    shootInfoSplash.velocity = MathHelper.getDirection(
                            shootInfoSplash.shootLoc,
                            AimHelper.helperAimEntity(shootInfoSplash.shootLoc, target, aimHelperSplash),
                            PROJECTILE_SPEED);
                    EntityHelper.spawnProjectile(shootInfoSplash);
                    proj.remove();
                }
            }
            // purge projectiles set every second
            if (index % 20 == 0) {
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
