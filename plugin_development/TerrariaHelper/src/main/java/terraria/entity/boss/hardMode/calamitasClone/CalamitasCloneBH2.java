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

public class CalamitasCloneBH2 implements ICalamitasCloneBH {
    CalamitasClone owner;
    Cataclysm cataclysm;
    Catastrophe catastrophe;

    CalamitasCloneBH2(CalamitasClone owner) {
        this.owner = owner;

        catastrophe = new Catastrophe(owner.target, owner);
        cataclysm = new Cataclysm(owner.target, owner);

        owner.bossbar.title = CraftChatMessage.fromString(CalamitasClone.BOSS_TYPE.msgName + " - □□「？？？？」", true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);
    }

    @Override
    public boolean inProgress() {
        return catastrophe.isAlive() || cataclysm.isAlive();
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
    }
}
