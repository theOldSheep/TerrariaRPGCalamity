package terraria.event.listener;

import com.bekvon.bukkit.residence.event.ResidenceCreationEvent;
import com.bekvon.bukkit.residence.protection.CuboidArea;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import terraria.TerrariaHelper;

public class ResidenceListener implements Listener {
    static final int PROTECTION_RADIUS = TerrariaHelper.settingConfig.getInt("miscSetting.minimumResidenceDistFromSpawn", 200);
    CuboidArea getSpawnProtectionArea() {
        World surfaceWorld = Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE);
        return new CuboidArea(
                new Location(surfaceWorld, -PROTECTION_RADIUS, 1, -PROTECTION_RADIUS),
                new Location(surfaceWorld, PROTECTION_RADIUS, 254, PROTECTION_RADIUS));
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onResidenceCreation(ResidenceCreationEvent evt) {
        if (evt.getPhysicalArea().checkCollision(getSpawnProtectionArea()) ) {
            evt.getPlayer().sendMessage(String.format("请离开出生点%d格外创建领地！", PROTECTION_RADIUS));
            evt.setCancelled(true);
        }
    }
}
