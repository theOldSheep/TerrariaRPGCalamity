package terraria.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import terraria.util.DamageHelper;

public class TerrariaDamageEvent extends EntityEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final DamageHelper.DamageInfoBus damageInfo;

    public TerrariaDamageEvent(DamageHelper.DamageInfoBus damageInfo) {
        super(damageInfo.victim);
        this.damageInfo = damageInfo;
    }

    @Override
    public boolean isCancelled() {
        return damageInfo.isCancelled();
    }

    @Override
    public void setCancelled(boolean b) {
        damageInfo.setCancelled(b);
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public DamageHelper.DamageInfoBus getDetails() {
        return damageInfo;
    }
}
