package terraria.entity.projectile;

import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import net.minecraft.server.v1_12_R1.Vec3D;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.boss.postMoonLord.yharon.Infernado;
import terraria.entity.boss.postMoonLord.yharon.Yharon;
import terraria.util.EntityHelper;
import terraria.util.ItemUseHelper;
import terraria.util.MathHelper;
import terraria.util.PlayerHelper;

import java.util.ArrayList;

public class YharonTornado extends GenericProjectile {
    Yharon boss;
    Player target;

    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public YharonTornado(World world) {
        super(world);
        boss = null;
        super.die();
    }
    public YharonTornado(EntityHelper.ProjectileShootInfo shootInfo, Yharon boss) {
        super(shootInfo);
        // initialize variables
        this.boss = boss;
        this.target = boss.getTarget();
    }


    @Override
    public void die() {
        super.die();

        // Spawn the Infernado
        Location infernadoLocation = bukkitEntity.getLocation().add(new Vector(0, -10, 0));
        new Infernado(boss, infernadoLocation, new ArrayList<>(), 0, false);
    }

    @Override
    public void B_() {
        super.B_();

        this.target = boss.getTarget();
        super.lockedTarget = ((CraftEntity) target).getHandle();
    }
}
