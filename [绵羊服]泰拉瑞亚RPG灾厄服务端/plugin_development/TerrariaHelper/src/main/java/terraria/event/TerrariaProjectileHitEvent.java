package terraria.event;

import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileHitEvent;

public class TerrariaProjectileHitEvent extends ProjectileHitEvent {

    // modified version of event factory call event, as that event may not be cancelled.
    public static TerrariaProjectileHitEvent callProjectileHitEvent(net.minecraft.server.v1_12_R1.Entity entity) {
        TerrariaProjectileHitEvent event = new TerrariaProjectileHitEvent((Projectile)entity.getBukkitEntity());
        entity.world.getServer().getPluginManager().callEvent(event);
        return event;
    }
    public static TerrariaProjectileHitEvent callProjectileHitEvent(net.minecraft.server.v1_12_R1.Entity entity, MovingObjectPosition position) {
        Block hitBlock = null;
        if (position.type == MovingObjectPosition.EnumMovingObjectType.BLOCK) {
            BlockPosition blockposition = position.a();
            hitBlock = entity.getBukkitEntity().getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
        }

        TerrariaProjectileHitEvent event = new TerrariaProjectileHitEvent((Projectile)entity.getBukkitEntity(), position.entity == null ? null : position.entity.getBukkitEntity(), hitBlock);
        entity.world.getServer().getPluginManager().callEvent(event);
        return event;
    }
    public boolean cancelled = false;

    public TerrariaProjectileHitEvent(Projectile projectile) {
        super(projectile);
    }

    public TerrariaProjectileHitEvent(Projectile projectile, Entity hitEntity) {
        super(projectile, hitEntity);
    }

    public TerrariaProjectileHitEvent(Projectile projectile, Block hitBlock) {
        super(projectile, hitBlock);
    }

    public TerrariaProjectileHitEvent(Projectile projectile, Entity hitEntity, Block hitBlock) {
        super(projectile, hitEntity, hitBlock);
    }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelState) {this.cancelled = cancelState;}
}
