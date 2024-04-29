package terraria.event.listener;

import com.bekvon.bukkit.residence.Residence;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;
import terraria.worldgen.overworld.OverworldChunkGenerator;
import terraria.worldgen.overworld.cavern.CavernChunkGenerator;
import terraria.worldgen.underworld.UnderworldChunkGenerator;

import java.lang.reflect.Method;
import java.util.logging.Level;


public class WorldRegisterListener implements Listener {
    @org.bukkit.event.EventHandler(priority = EventPriority.LOWEST)
    public void onPrepareCreateWorld(WorldLoadEvent evt) {
        if (! evt.getWorld().getName().equals("world"))
            return;
    }
}
