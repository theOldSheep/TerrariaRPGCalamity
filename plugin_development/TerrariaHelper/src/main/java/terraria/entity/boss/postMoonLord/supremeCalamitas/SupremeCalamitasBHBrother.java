package terraria.entity.boss.postMoonLord.supremeCalamitas;

import net.minecraft.server.v1_12_R1.PacketPlayOutBoss;
import org.bukkit.Location;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class SupremeCalamitasBHBrother implements ISupremeCalamitasBH {
    static final double HOVER_DISTANCE = 32, HOVER_DISTANCE_BROTHERS = 24, HOVER_DISTANCE_BROTHERS_OFFSET = 12;
    static final String[][] BEGIN_MSG = new String[][] {
            new String[]{"只是单有过去形态的空壳罢了，或许在其中依然残存他们的些许灵魂也说不定。"},
            new String[]{"你想见见我的家人吗？听上去挺可怕，不是么？"},
    };
    final double healthRatio;
    SupremeCalamitas owner;
    ArrayList<SupremeCalamitasBrother> brothers = new ArrayList<>();


    SupremeCalamitasBHBrother(SupremeCalamitas owner, double healthRatio) {
        this.owner = owner;
        this.healthRatio = healthRatio;
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
        for (SupremeCalamitasBrother e : brothers) {
            if (e.isAlive()) return true;
        }
        return false;
    }

    @Override
    public void refresh() {
    }

    @Override
    public void begin() {
        BossHelper.sendBossMessages(20, 0, owner.getBukkitEntity(),
                SupremeCalamitas.MSG_PREFIX, BEGIN_MSG[PlayerHelper.hasDefeated(owner.target, SupremeCalamitas.BOSS_TYPE) ? 0 : 1]);

        owner.bossbar.title = CraftChatMessage.fromString(SupremeCalamitas.BOSS_TYPE.msgName + " - 禁符「余烬挽歌」", true)[0];
        owner.bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_NAME);

        brothers.add( new SupremeCalamitasBrother(owner, 0) );
        brothers.add( new SupremeCalamitasBrother(owner, 1) );
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

        LivingEntity bukkitEntity = (LivingEntity) owner.getBukkitEntity();
        Player target = owner.target;

        // remove dead brother
        for (int i = 0; i < brothers.size(); i ++) {
            if (brothers.get(i).isAlive())
                continue;
            brothers.remove(i);
            i --;
        }
        if (brothers.isEmpty()) return;
        // brother movement; attack handled within brother's class
        double yaw = MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        Vector directDir = MathHelper.vectorFromYawPitch_approx(yaw, 0)
                .multiply(HOVER_DISTANCE - HOVER_DISTANCE_BROTHERS);
        Location hoverLoc = bukkitEntity.getLocation().add(directDir);
        if (brothers.size() == 1) {
            brothers.get(0).desiredLoc = hoverLoc;
        }
        else {
            Vector offsetDir = MathHelper.vectorFromYawPitch_approx(yaw + 90, 0)
                    .multiply(HOVER_DISTANCE_BROTHERS_OFFSET);
            brothers.get(0).desiredLoc = hoverLoc.clone().add(offsetDir);
            brothers.get(1).desiredLoc = hoverLoc.clone().subtract(offsetDir);
        }
    }
}
