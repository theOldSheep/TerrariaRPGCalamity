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

public class CalamitasCloneBH1 implements ICalamitasCloneBH {
    EntityHelper.ProjectileShootInfo shootInfo;
    static final int DURATION_TICKS = 20 * 15;
    static final double PROJECTILE_SPEED = 1.5;
    CalamitasClone owner;
    int index = 0;
    Collection<Projectile> projectiles;

    CalamitasCloneBH1(CalamitasClone owner) {
        this.owner = owner;

        this.shootInfo = new EntityHelper.ProjectileShootInfo(owner.getBukkitEntity(), new Vector(),
                CalamitasClone.attrMapHellFireball, "焰火亡魂");
        projectiles = new HashSet<>();

        owner.bossbar.title = CraftChatMessage.fromString(CalamitasClone.BOSS_TYPE.msgName + " - □□「？？？？」", true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);
    }

    @Override
    public boolean inProgress() {
        return index <= DURATION_TICKS;
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
        Vector horizontalOffset = ownerLivingEntity.getEyeLocation().subtract(target.getEyeLocation()).toVector();
        horizontalOffset.setY(0);
        double velLen = horizontalOffset.length();
        if (velLen < 1e-5) {
            velLen = 1;
            horizontalOffset = new Vector(1, 0, 0);
        }
        horizontalOffset.multiply(1 / velLen);
        owner.getBukkitEntity().setVelocity(MathHelper.getDirection(
                owner.getBukkitEntity().getLocation(), owner.target.getEyeLocation().add(horizontalOffset.clone().multiply(12)),
                CalamitasClone.SPEED, true));
        // do not attack for first 10 ticks
        if (index > 10) {
            // spawn projectiles
            // upward, main projectile
            if (index % 10 == 0) {
                shootInfo.shootLoc = ownerLivingEntity.getEyeLocation();
                shootInfo.velocity = horizontalOffset.clone().multiply(-1).add(new Vector(0, 2, 0));
                MathHelper.setVectorLength(shootInfo.velocity, PROJECTILE_SPEED);
                projectiles.add( EntityHelper.spawnProjectile(shootInfo) );
            }
            // manage all projectiles
            for (Projectile proj : projectiles) {
                if (proj.isDead()) continue;
                if (proj.getTicksLived() >= 10 && proj.getTicksLived() % 9 == 5) {
                    owner.psiHellBlast.shootLoc = proj.getLocation();
                    owner.psiHellBlast.velocity = MathHelper.getDirection(
                            owner.psiHellBlast.shootLoc,
                            AimHelper.helperAimEntity(owner.psiHellBlast.shootLoc, target, CalamitasClone.hellBlastAimHelper),
                            CalamitasClone.HELL_BLAST_SPEED);
                    EntityHelper.spawnProjectile(owner.psiHellBlast);
                }
            }

        }
        index ++;
    }
}
