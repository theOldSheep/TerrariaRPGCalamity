package terraria.worldgen.overworld;

import org.bukkit.util.noise.PerlinOctaveGenerator;
import terraria.TerrariaHelper;
import terraria.worldgen.Interpolate;

import java.util.Random;

import static terraria.worldgen.overworld.OverworldChunkGenerator.*;

public class OverworldNoiseProviders {
    public static PerlinOctaveGenerator RIVER_NOISE, STONE_VEIN_NOISE;
    // index: continentalness factor, erosion factor
    public static Interpolate[][]
            NORMAL_INTERPOLATE,
            JUNGLE_INTERPOLATE,
            ASTRAL_INTERPOLATE,
            DESERT_INTERPOLATE,
            OCEAN_INTERPOLATE,
            SULPHUROUS_OCEAN_INTERPOLATE;
    // provides the index (allow decimal - which determines the interpolation ratio) of interpolate matrix
    // element 0: takes 0~1 (strictly, continentalness factor)
    // element 1: takes -1~1 (not strictly, from erosion noise)
    public static Interpolate[]
            NORMAL_INTERPOLATE_COORDINATOR,
            OCEAN_INTERPOLATE_COORDINATOR,
            SULPHUROUS_OCEAN_INTERPOLATE_COORDINATOR;
    public static Interpolate
            // for rivers
            RIVER_RATIO_PROVIDER, RIVER_ERODE_PROVIDER;
    static {
        // terrain noise functions
        Random rdm = new Random(TerrariaHelper.WORLD_SEED);

        RIVER_NOISE = new PerlinOctaveGenerator(rdm.nextLong(), OCTAVES_RIVER);
        RIVER_NOISE.setScale(0.0005);

        STONE_VEIN_NOISE = new PerlinOctaveGenerator(rdm.nextLong(), OCTAVES_STONE);
        STONE_VEIN_NOISE.setScale(0.05);
        // constants

        // relevance providers
        {
            // note: continentalness low -> high = terrain low (ocean) -> high (mountains)
            // note: erosion low -> high = terrain high -> low
            NORMAL_INTERPOLATE_COORDINATOR = new Interpolate[]{
                    new Interpolate(new Interpolate.InterpolatePoint[]{
                            Interpolate.InterpolatePoint.create(0.3, 0),
                            Interpolate.InterpolatePoint.create(0.35, 1),
                            Interpolate.InterpolatePoint.create(0.65, 1),
                            Interpolate.InterpolatePoint.create(0.75, 2),
                    }, "normal_coordinator_cont"),
                    new Interpolate(new Interpolate.InterpolatePoint[]{
                            Interpolate.InterpolatePoint.create(-0.2 , 2),
                            Interpolate.InterpolatePoint.create(-0.1 , 1),
                            Interpolate.InterpolatePoint.create(0.1  , 1),
                            Interpolate.InterpolatePoint.create(0.3  , 0),
                    }, "normal_coordinator_eros")
            };
            OCEAN_INTERPOLATE_COORDINATOR = new Interpolate[]{
                    new Interpolate(new Interpolate.InterpolatePoint[]{
                            Interpolate.InterpolatePoint.create(0.1 , 0),
                            Interpolate.InterpolatePoint.create(0.25, 1),
                            Interpolate.InterpolatePoint.create(0.4 , 1),
                            Interpolate.InterpolatePoint.create(0.65, 2),
                    }, "ocean_coordinator_cont"),
                    new Interpolate(new Interpolate.InterpolatePoint[]{
                            Interpolate.InterpolatePoint.create(0, 0),
                    }, "ocean_coordinator_eros")
            };
            SULPHUROUS_OCEAN_INTERPOLATE_COORDINATOR = new Interpolate[]{
                    new Interpolate(new Interpolate.InterpolatePoint[]{
                            Interpolate.InterpolatePoint.create(0.1 , 0),
                            Interpolate.InterpolatePoint.create(0.15, 1),
                    }, "sulphurous_ocean_coordinator_cont"),
                    new Interpolate(new Interpolate.InterpolatePoint[]{
                            Interpolate.InterpolatePoint.create(0, 0),
                    }, "sulphurous_ocean_coordinator_eros")
            };
        }

        // normal biomes
        {
            // plains variants
            Interpolate PLAINS_LAKE, PLAINS_NORM, PLAINS_HILL;
            PLAINS_LAKE = new Interpolate(new Interpolate.InterpolatePoint[] {
                    Interpolate.InterpolatePoint.create(-1, SEA_LEVEL - LAKE_DEPTH - 2),
                    Interpolate.InterpolatePoint.create(1, SEA_LEVEL - LAKE_DEPTH),
            }, "plains_lake_hm");
            PLAINS_NORM = new Interpolate(new Interpolate.InterpolatePoint[] {
                    Interpolate.InterpolatePoint.create(-1, LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1, LAND_HEIGHT + 5),
            }, "plains_norm_hm");
            PLAINS_HILL = new Interpolate(new Interpolate.InterpolatePoint[] {
                    Interpolate.InterpolatePoint.create(-1, LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1, LAND_HEIGHT + 10),
            }, "plains_hill_hm");
            // rolling hills variants
            Interpolate ROLLING_HILLS_LAKE, ROLLING_HILLS_LOWER, ROLLING_HILLS_HIGHER;
            ROLLING_HILLS_LAKE = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1, SEA_LEVEL - LAKE_DEPTH),
                    Interpolate.InterpolatePoint.create(1, LAND_HEIGHT + 10),
            }, "rolling_hills_lake_hm");
            ROLLING_HILLS_LOWER = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1, SEA_LEVEL - LAKE_DEPTH),
                    Interpolate.InterpolatePoint.create(-0.25, LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1, LAND_HEIGHT + ROLLING_HILLS_HEIGHT / 2d),
            }, "rolling_hills_lower_hm");
            ROLLING_HILLS_HIGHER = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1, LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1 , LAND_HEIGHT + ROLLING_HILLS_HEIGHT),
            }, "rolling_hills_higher_hm");
            // mountains variants
            Interpolate MOUNTAINS_LAKE, MOUNTAINS_FOOTHILL, MOUNTAINS_NORM;
            MOUNTAINS_LAKE = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1  , SEA_LEVEL - LAKE_DEPTH),
                    Interpolate.InterpolatePoint.create(-0.3, SEA_LEVEL - LAKE_DEPTH + 5),
                    Interpolate.InterpolatePoint.create(0.1 , LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1   , LAND_HEIGHT + 25),
            }, "mountains_lake_hm");
            MOUNTAINS_FOOTHILL = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1, LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1 , LAND_HEIGHT + ROLLING_HILLS_HEIGHT),
            }, "mountains_foothill_hm");
            MOUNTAINS_NORM = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1, LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1, LAND_HEIGHT + MOUNTAIN_HEIGHT),
            }, "mountains_hm");

            // assemble them together
            NORMAL_INTERPOLATE = new Interpolate[][]{
                    {PLAINS_LAKE, PLAINS_NORM, PLAINS_HILL},
                    {ROLLING_HILLS_LAKE, ROLLING_HILLS_LOWER, ROLLING_HILLS_HIGHER},
                    {MOUNTAINS_LAKE, MOUNTAINS_FOOTHILL, MOUNTAINS_NORM}
            };
        }

        // jungle
        {
            Interpolate JUNGLE_LAKE, JUNGLE_SWAMP, JUNGLE_FLATLAND_WET, JUNGLE_FLATLAND, JUNGLE_HILL;
            JUNGLE_LAKE = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1       , SEA_LEVEL - 15),
                    Interpolate.InterpolatePoint.create(1        , SEA_LEVEL - 10),
            }, "jungle_lake_hm");
            JUNGLE_SWAMP = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1       , SEA_LEVEL - 10),
                    Interpolate.InterpolatePoint.create(-0.6     , SEA_LEVEL),
                    Interpolate.InterpolatePoint.create(-0.4     , SEA_LEVEL + 2),
                    Interpolate.InterpolatePoint.create(-0.2     , SEA_LEVEL),
                    Interpolate.InterpolatePoint.create(0        , SEA_LEVEL - 5),
                    Interpolate.InterpolatePoint.create(0.2      , SEA_LEVEL + 1),
                    Interpolate.InterpolatePoint.create(0.4      , SEA_LEVEL + 3),
                    Interpolate.InterpolatePoint.create(0.6      , SEA_LEVEL + 1),
                    Interpolate.InterpolatePoint.create(1        , SEA_LEVEL - 5),
            }, "jungle_swamp_hm");
            JUNGLE_FLATLAND_WET = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1 , SEA_LEVEL - 10),
                    Interpolate.InterpolatePoint.create(0.3, SEA_LEVEL + 5),
                    Interpolate.InterpolatePoint.create(1  , LAND_HEIGHT),
            }, "jungle_flatland_wet_hm");
            JUNGLE_FLATLAND = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1 , SEA_LEVEL - 10),
                    Interpolate.InterpolatePoint.create(0  , LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1  , LAND_HEIGHT + 10),
            }, "jungle_flatland_hm");
            JUNGLE_HILL = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1  , SEA_LEVEL - 10),
                    Interpolate.InterpolatePoint.create(1   , LAND_HEIGHT + ROLLING_HILLS_HEIGHT),
            }, "jungle_hill_hm");

            // assemble
            JUNGLE_INTERPOLATE = new Interpolate[][]{
                    {JUNGLE_LAKE, JUNGLE_SWAMP, JUNGLE_FLATLAND_WET},
                    {JUNGLE_SWAMP, JUNGLE_FLATLAND_WET, JUNGLE_FLATLAND},
                    {JUNGLE_FLATLAND_WET, JUNGLE_FLATLAND, JUNGLE_HILL}
            };
        }

        // astral infection
        {
            Interpolate ASTRAL_LAKE, ASTRAL_FLATLAND, ASTRAL_HILLS, ASTRAL_PLATEAU;
            ASTRAL_LAKE = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1       , SEA_LEVEL - LAKE_DEPTH),
                    Interpolate.InterpolatePoint.create(1        , SEA_LEVEL - LAKE_DEPTH + 5),
            }, "astral_lake_hm");
            ASTRAL_FLATLAND = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1       , SEA_LEVEL - LAKE_DEPTH),
                    Interpolate.InterpolatePoint.create(0        , LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1        , LAND_HEIGHT + 5),
            }, "astral_flatland_hm");
            ASTRAL_HILLS = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1, SEA_LEVEL - 10),
                    Interpolate.InterpolatePoint.create(0 , LAND_HEIGHT + 10),
                    Interpolate.InterpolatePoint.create(1 , LAND_HEIGHT + ROLLING_HILLS_HEIGHT),
            }, "astral_hills_hm");
            ASTRAL_PLATEAU = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1  , LAND_HEIGHT + ROLLING_HILLS_HEIGHT / 2d + 8),
                    Interpolate.InterpolatePoint.create(-0.5, LAND_HEIGHT + ROLLING_HILLS_HEIGHT / 2d),
                    Interpolate.InterpolatePoint.create(-0.4, LAND_HEIGHT + 5),
                    Interpolate.InterpolatePoint.create(0   , LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(0.4 , LAND_HEIGHT + 10),
                    Interpolate.InterpolatePoint.create(0.5 , LAND_HEIGHT + ROLLING_HILLS_HEIGHT),
                    Interpolate.InterpolatePoint.create(1   , LAND_HEIGHT + ROLLING_HILLS_HEIGHT + 10),
            }, "astral_plateau_hm");

            // assemble
            ASTRAL_INTERPOLATE = new Interpolate[][]{
                    {ASTRAL_LAKE, ASTRAL_FLATLAND, ASTRAL_HILLS},
                    {ASTRAL_FLATLAND, ASTRAL_HILLS, ASTRAL_PLATEAU},
                    {ASTRAL_HILLS, ASTRAL_PLATEAU, ASTRAL_PLATEAU}
            };
        }

        // desert
        {
            Interpolate DESERT_OASIS, DESERT_FLATLAND, DESERT_DUNES, DESERT_HILLS;
            DESERT_OASIS = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1       , SEA_LEVEL - 10),
                    Interpolate.InterpolatePoint.create(1        , SEA_LEVEL - 8),
            }, "desert_oasis_hm");
            DESERT_FLATLAND = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1       , LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1        , LAND_HEIGHT + 5),
            }, "desert_flatland_hm");
            DESERT_DUNES = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1, LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(1 , LAND_HEIGHT + ROLLING_HILLS_HEIGHT / 2d),
            }, "desert_dunes_hm");
            DESERT_HILLS = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1  , LAND_HEIGHT),
                    Interpolate.InterpolatePoint.create(0   , LAND_HEIGHT + 10),
                    Interpolate.InterpolatePoint.create(1   , LAND_HEIGHT + ROLLING_HILLS_HEIGHT),
            }, "desert_hills_hm");

            // assemble
            DESERT_INTERPOLATE = new Interpolate[][]{
                    {DESERT_OASIS, DESERT_FLATLAND, DESERT_DUNES},
                    {DESERT_FLATLAND, DESERT_DUNES, DESERT_DUNES},
                    {DESERT_DUNES, DESERT_DUNES, DESERT_HILLS}
            };
        }

        // ocean
        {
            Interpolate OCEAN_BEACH, OCEAN_NORM, OCEAN_DEEP;
            OCEAN_BEACH = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1       , SEA_LEVEL - 5),
                    Interpolate.InterpolatePoint.create(1        , LAND_HEIGHT),
            }, "ocean_beach_hm");
            OCEAN_NORM = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1  , SEA_LEVEL - 15),
                    Interpolate.InterpolatePoint.create(0   , SEA_LEVEL - 5),
                    Interpolate.InterpolatePoint.create(1   , SEA_LEVEL + 10),
            }, "ocean_norm_hm");
            OCEAN_DEEP = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1  , SEA_LEVEL - 30),
                    Interpolate.InterpolatePoint.create(1   , SEA_LEVEL - 20),
            }, "ocean_deep_hm");

            // assemble
            OCEAN_INTERPOLATE = new Interpolate[][]{
                    {OCEAN_BEACH},
                    {OCEAN_NORM},
                    {OCEAN_DEEP}
            };
        }

        // sulphurous ocean
        {
            Interpolate SULPHUROUS_OCEAN_BEACH, SULPHUROUS_OCEAN_DEEP;
            SULPHUROUS_OCEAN_BEACH = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(-1       , SEA_LEVEL - 5),
                    Interpolate.InterpolatePoint.create(1        , LAND_HEIGHT),
            }, "sulphurous_ocean_beach_hm");
            SULPHUROUS_OCEAN_DEEP = new Interpolate(new Interpolate.InterpolatePoint[]{
                    Interpolate.InterpolatePoint.create(0   , 0),
            }, "sulphurous_ocean_deep_hm");

            // assemble
            SULPHUROUS_OCEAN_INTERPOLATE = new Interpolate[][]{
                    {SULPHUROUS_OCEAN_BEACH},
                    {SULPHUROUS_OCEAN_DEEP}
            };
        }

        // river
        RIVER_RATIO_PROVIDER = new Interpolate(new Interpolate.InterpolatePoint[]{
                Interpolate.InterpolatePoint.create(-0.05  , 0),
                Interpolate.InterpolatePoint.create(0      , 0.9),
                Interpolate.InterpolatePoint.create(0.05   , 0),
        }, "river_ratio_map");
        RIVER_ERODE_PROVIDER = new Interpolate(new Interpolate.InterpolatePoint[]{
                Interpolate.InterpolatePoint.create(-0.2   , 0),
                Interpolate.InterpolatePoint.create(-0.05  , 0.3),
                Interpolate.InterpolatePoint.create(0.05   , 0.3),
                Interpolate.InterpolatePoint.create(0.2    , 0),
        }, "river_erode_map");
    }
}
