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
        // create worlds
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
            try {
                if (Bukkit.getServer().getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE) == null) {
                    Bukkit.getLogger().info("正在尝试初始化地面世界！");
                    World surfaceWorld = new WorldCreator(TerrariaHelper.Constants.WORLD_NAME_SURFACE)
                            .generator(OverworldChunkGenerator.getInstance())
                            .environment(World.Environment.NORMAL)
                            .type(WorldType.CUSTOMIZED)
                            .generateStructures(false)
                            .seed(TerrariaHelper.worldSeed)
                            .createWorld();
                    Bukkit.getLogger().info("正在尝试初始化地面世界游戏规则！");
                    WorldHelper.initWorldRules(surfaceWorld);
                }
                if (Bukkit.getServer().getWorld(TerrariaHelper.Constants.WORLD_NAME_CAVERN) == null) {
                    Bukkit.getLogger().info("正在尝试初始化洞穴世界！");
                    World cavernWorld = new WorldCreator(TerrariaHelper.Constants.WORLD_NAME_CAVERN)
                            .generator(CavernChunkGenerator.getInstance())
                            .environment(World.Environment.NORMAL)
                            .type(WorldType.CUSTOMIZED)
                            .generateStructures(false)
                            .seed(TerrariaHelper.worldSeed)
                            .createWorld();
                    Bukkit.getLogger().info("正在尝试初始化洞穴世界游戏规则！");
                    WorldHelper.initWorldRules(cavernWorld);
                }
                if (Bukkit.getServer().getWorld(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD) == null) {
                    Bukkit.getLogger().info("正在尝试初始化地狱世界！");
                    World underworldWorld = new WorldCreator(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD)
                            .generator(UnderworldChunkGenerator.getInstance())
                            .environment(World.Environment.NORMAL)
                            .type(WorldType.CUSTOMIZED)
                            .generateStructures(false)
                            .seed(TerrariaHelper.worldSeed)
                            .createWorld();
                    Bukkit.getLogger().info("正在尝试初始化地狱世界游戏规则！");
                    WorldHelper.initWorldRules(underworldWorld);
                }
                // load residence
                Residence.getInstance().onEnable();
                Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(), () ->
                        Residence.getInstance().onEnable(), 1);
            } catch (Exception e) {
                Bukkit.getLogger().info("初始化世界时发生错误！");
                e.printStackTrace();
                Bukkit.getLogger().info("正在关闭服务器……");
                Bukkit.getServer().shutdown();
            }
            Bukkit.getLogger().info("世界初始化尝试完毕！");
        }, 1);
    }
    public static boolean firstJoin = true;
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFirstPlayerJoin(PlayerJoinEvent evt) {
        if (!firstJoin)
            return;
        firstJoin = false;

        // load residence
        if (Bukkit.getPluginManager().isPluginEnabled(Residence.getInstance())) {
            try {
                Residence.getInstance().onEnable();
            } catch (Exception e) {
                TerrariaHelper.getInstance().getLogger().log(Level.SEVERE, "Error when trying to let residence reload its yml saves");
                e.printStackTrace();
            }
        }
    }
}
