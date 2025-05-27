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

public class SupremeCalamitasBHFinalConversation implements ISupremeCalamitasBH {
    static final int DURATION_TICKS = 20 * 15;
    static final double HOVER_DISTANCE = 24;
    static final String[][] BEGIN_MSG = new String[][] {
            new String[]{
                    "了不起的表现，我认可你的胜利。",
                    "毫无疑问，你会遇见比我更加强大的敌人。",
                    "我相信你不会犯下和他一样的错误。",
                    "至于你的未来会变成什么样子，我很期待。"},
            new String[]{
                    "哪怕他抛弃了一切，他的力量也不会消失。",
                    "我已没有余力去怨恨他了，对你也是如此......",
                    "现在，一切都取决于你。"}
    };
    final double healthRatio;
    SupremeCalamitas owner;
    int index = 0;

    SupremeCalamitasBHFinalConversation(SupremeCalamitas owner, double healthRatio) {
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
    }

    @Override
    public void finish() {
        owner.die();
    }

    @Override
    public void tick() {
        // movement
        owner.generalMovement(HOVER_DISTANCE);
        index ++;
    }
}
