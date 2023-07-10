package terraria.util;

import net.ess3.api.events.UserWarpEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import terraria.TerrariaHelper;

public class WorldHelper {
    public enum HeightLayer {
        SPACE("太空"), SURFACE("地表"), UNDERGROUND("地下"), CAVERN("洞穴"), UNDERWORLD("地狱");
        public final String name;
        HeightLayer(String name) {
            this.name = name;
        }
        public static HeightLayer getHeightLayer(Location loc) {
            switch (loc.getWorld().getName()) {
                case TerrariaHelper.Constants.WORLD_NAME_SURFACE: {
                    if (loc.getY() > 200) return SPACE;
                    if (loc.getY() > 50) return SURFACE;
                    return UNDERGROUND;
                }
                case TerrariaHelper.Constants.WORLD_NAME_CAVERN: {
                    return CAVERN;
                }
                case TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD: {
                    return UNDERWORLD;
                }
                default:
                    return SURFACE;
            }
        }
    }
    public enum BiomeType {
        ABYSS("深渊"), ASTRAL_INFECTION("星辉瘟疫"), BRIMSTONE_CRAG("硫火之崖"), CORRUPTION("腐化之地"),
        DESERT("沙漠"), DUNGEON("地牢"), HALLOW("神圣之地"), JUNGLE("丛林"), METEOR("陨石"),
        NORMAL("森林"), OCEAN("海洋"), SPACE("太空"), SULPHUROUS_OCEAN("硫磺海"),
        SUNKEN_SEA("沉沦之海"), TEMPLE("丛林神庙"), TUNDRA("雪原"), UNDERWORLD("地狱");
        public final String name;
        BiomeType(String name) {
            this.name = name;
        }
        public static BiomeType getBiome(Player ply) {
            return getBiome(ply, true);
        }
        public static BiomeType getBiome(Player ply, boolean considerSpace) {
            BiomeType biomeType = (BiomeType) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_BIOME).value();
            if (biomeType != NORMAL) return biomeType;
            return getBiome(ply.getLocation(), considerSpace);
        }
        public static BiomeType getBiome(Location loc) {
            return getBiome(loc, true);
        }
        public static BiomeType getBiome(Location loc, boolean considerSpace) {
            if (considerSpace) {
                HeightLayer height = HeightLayer.getHeightLayer(loc);
                if (height == HeightLayer.SPACE) {
                    return SPACE;
                }
            }
            Biome biome = loc.getWorld().getBiome(loc.getBlockX(), loc.getBlockZ());
            switch (biome) {
                case HELL:
                    return UNDERWORLD;
                case SAVANNA:
                    return BRIMSTONE_CRAG;
                case MUSHROOM_ISLAND:
                case MUSHROOM_ISLAND_SHORE:
                    return CORRUPTION;
                case ICE_FLATS:
                case MUTATED_ICE_FLATS:
                    return HALLOW;
                case MESA:
                case MUTATED_MESA:
                    return ASTRAL_INFECTION;
                case DESERT:
                    return DESERT;
                case MUTATED_DESERT:
                    return SUNKEN_SEA;
                case BEACHES:
                case OCEAN:
                    return OCEAN;
                case FROZEN_OCEAN:
                case COLD_BEACH:
                    return SULPHUROUS_OCEAN;
                case DEEP_OCEAN:
                    return ABYSS;
                case TAIGA_COLD:
                case MUTATED_TAIGA_COLD:
                    return TUNDRA;
                case JUNGLE:
                case MUTATED_JUNGLE:
                    return JUNGLE;
                default:
                    return NORMAL;
            }
        }
    }
    public static int getMoonPhase() {
        World overworld = Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE);
        if (overworld != null) {
            return (int) ((overworld.getFullTime() / 24000) % 8);
        }
        return 0;
    }
    public static boolean isDayTime(World wld) {
        return isDayTime(wld.getTime());
    }
    public static boolean isDayTime(long timeInTick) {
        return ! (MathHelper.isBetween(timeInTick, 13500, 22500));
    }
    public static void initWorldRules(World wld) {
        // game rules
        wld.setGameRuleValue("doDaylightCycle",     "false");
        wld.setGameRuleValue("doMobSpawning",       "false");
        wld.setGameRuleValue("doWeatherCycle",      "false");
        wld.setGameRuleValue("keepInventory",       "true");
        wld.setGameRuleValue("maxEntityCramming",   "1");
        wld.setGameRuleValue("randomTickSpeed",     "0");
        // clear weather
        wld.setThundering(false);
        wld.setStorm(false);
    }
    // TODO
    public static void worldRandomTick(World wld) {

    }
}
