package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.omg.CORBA.TypeCodePackage.BadKind;

import java.util.HashMap;
import java.util.Random;

public class StructurePopulator extends BlockPopulator {
    static final int STRUCT_INTERVAL = 3, CHECK_STEPS_RADIUS = 0;
    static final Material MAT_BRICK = Material.SMOOTH_BRICK;
    static final byte DATA_DUNGEON = 2, DATA_LIZARD = 1, DATA_NONE = 0;
    boolean isSurface;

    public StructurePopulator(boolean surfaceOrUnderground) {
        super();
        this.isSurface = surfaceOrUnderground;
    }

    protected void registerBlockPlane(World wld, HashMap<Block, Boolean> blocks,
                                      int centerX, int y, int centerZ, int radius, boolean boolReg) {
        registerBlockPlane(wld, blocks, centerX - radius, centerZ - radius, y,
                centerX + radius, centerZ + radius, boolReg);
    }
    protected void registerBlockPlane(World wld, HashMap<Block, Boolean> blocks,
                                      int minX, int minZ, int y, int maxX, int maxZ, boolean boolReg) {
        for (int x = minX; x <= maxX; x ++) {
            for (int z = minZ; z <= maxZ; z ++) {
                blocks.put(wld.getBlockAt(x, y, z), boolReg);
            }
        }
    }
    protected void setBlocks(HashMap<Block, Boolean> blocks, Material trueMat, Material falseMat, byte trueDt, byte falseDt) {
        for (Block b : blocks.keySet()) {
            if (blocks.get(b)) {
                b.setType( trueMat );
                if (trueDt != 0)
                    b.setData(trueDt);
            }
            else {
                b.setType( falseMat );
                if (falseDt != 0)
                    b.setData(falseDt);
            }
        }
    }


    protected void generateSpawnShelter(World wld) {
        int radius = 24;
        HashMap<Block, Boolean> floor = new HashMap<>(), fence = new HashMap<>();
        registerBlockPlane(wld, floor, 0, OverworldChunkGenerator.LAND_HEIGHT - 1, 0, radius, true);
        registerBlockPlane(wld, fence, 0, OverworldChunkGenerator.LAND_HEIGHT, 0, radius, true);
        registerBlockPlane(wld, fence, 0, OverworldChunkGenerator.LAND_HEIGHT, 0, radius - 1, false);
        setBlocks(floor, Material.GRASS, Material.GRASS, DATA_NONE, DATA_NONE);
        setBlocks(fence, Material.FENCE, Material.AIR, DATA_NONE, DATA_NONE);
    }
    protected void generateDungeonEntrance(World wld, int blockX, int blockZ, int surfaceLevel) {
        HashMap<Block, Boolean> struct = new HashMap<>();
        for (int y = 1; y < surfaceLevel - 6; y ++) {
            registerBlockPlane(wld, struct, blockX, y, blockZ, 5, true);
            registerBlockPlane(wld, struct, blockX, y, blockZ, 2, false);
        }
        setBlocks(struct, MAT_BRICK, Material.AIR, DATA_DUNGEON, DATA_NONE);
    }
    protected void generateDungeonEntranceBuilding(World wld, int blockX, int blockZ, int surfaceLevel) {
        HashMap<Block, Boolean> struct = new HashMap<>();
        // floor
        int currY = surfaceLevel - 6;
        for (int i = 0; i < 9; i ++) {
            registerBlockPlane(wld, struct, blockX, currY, blockZ, 20 - i, true);
            registerBlockPlane(wld, struct, blockX, currY, blockZ, 2, false);
            currY ++;
        }
        // room space and pillar
        int[] pillarXOffsets = { -9, -9, -9, -9, -4, -4,  4,  4,  9,  9,  9,  9 };
        int[] pillarZOffsets = { -9, -4,  4,  9, -9,  9, -9,  9, -9, -4,  4,  9 };
        for (int i = 0; i < 8; i ++) {
            // space
            registerBlockPlane(wld, struct, blockX, currY, blockZ, 12, false);
            // pillars
            for (int idx = 0; idx < pillarXOffsets.length; idx ++)
                registerBlockPlane(wld, struct,
                        blockX + pillarXOffsets[idx], currY, blockZ + pillarZOffsets[idx], 1, true);
            currY ++;
        }
        // ceiling
        for (int i = 0; i < 2; i ++) {
            registerBlockPlane(wld, struct, blockX, currY, blockZ, 12, true);
            currY ++;
        }
        // set the blocks
        setBlocks(struct, MAT_BRICK, Material.AIR, DATA_DUNGEON, DATA_NONE);
    }
    protected void generateDungeon(World wld, int blockX, int blockZ) {
        if (isSurface) {
            Bukkit.broadcastMessage("DG: " + blockX + ", " + blockZ);
            int surfaceLevel = wld.getHighestBlockYAt(blockX, blockZ);
            generateDungeonEntrance(wld, blockX, blockZ, surfaceLevel);
            generateDungeonEntranceBuilding(wld, blockX, blockZ, surfaceLevel);
        }
    }


    private static int getBlockCoordByChunk(int chunkCoord) {
        return (chunkCoord << 4) + 8;
    }
    private static OverworldBiomeGenerator.BiomeFeature getBiomeFeature(int chunkX, int chunkZ) {
        return OverworldBiomeGenerator.getBiomeFeature( getBlockCoordByChunk(chunkX), getBlockCoordByChunk(chunkZ) );
    }
    private static boolean shouldGenerateStructure(OverworldBiomeGenerator.BiomeFeature curr, int chunkX, int chunkZ) {
        int[] chunkXCoord = new int[CHECK_STEPS_RADIUS * 2 + 1];
        int[] chunkZCoord = new int[CHECK_STEPS_RADIUS * 2 + 1];
        for (int i = -CHECK_STEPS_RADIUS; i <= CHECK_STEPS_RADIUS; i ++) {
            chunkXCoord[i + CHECK_STEPS_RADIUS] = getBlockCoordByChunk(chunkX + i * STRUCT_INTERVAL);
            chunkZCoord[i + CHECK_STEPS_RADIUS] = getBlockCoordByChunk(chunkZ + i * STRUCT_INTERVAL);
        }
        for (int i = -CHECK_STEPS_RADIUS; i <= CHECK_STEPS_RADIUS; i ++) {
            for (int j = -CHECK_STEPS_RADIUS; j <= CHECK_STEPS_RADIUS; j ++) {
                if (i == 0 && j == 0)
                    continue;
                OverworldBiomeGenerator.BiomeFeature feature = OverworldBiomeGenerator.getBiomeFeature(
                        chunkXCoord[i + CHECK_STEPS_RADIUS], chunkZCoord[i + CHECK_STEPS_RADIUS] );
                if (feature.evaluatedBiome == curr.evaluatedBiome) {
                    double diff = feature.biomeSignificance - curr.biomeSignificance;
                    // the other chunk is more significant
                    if (diff > 0)
                        return false;
                    // equally significant: break tie by chunk X, then by chunk Z.
                    if (diff == 0) {
                        if (i > 0)
                            return false;
                        if (j > 0)
                            return false;
                    }

                }
            }
        }
        // no termination: this is appropriate.
        return true;
    }
    @Override
    public void populate(World wld, Random rdm, Chunk chunk) {
        int chunkX = chunk.getX(), chunkZ = chunk.getZ();
        // the simple shelter around spawn point
        if (isSurface && chunkX == 0 && chunkZ == 0) {
            generateSpawnShelter(wld);
        }
        // other structures
        else if (chunkX % STRUCT_INTERVAL == 0 && chunkZ % STRUCT_INTERVAL == 0) {
            int blockX = getBlockCoordByChunk(chunkX), blockZ = getBlockCoordByChunk(chunkZ);
            if (Math.abs(blockX) < OverworldBiomeGenerator.SPAWN_LOC_PROTECTION_RADIUS &&
                    Math.abs(blockZ) < OverworldBiomeGenerator.SPAWN_LOC_PROTECTION_RADIUS)
                return;
            // determine the structure to generate; for non-structure biomes, do not bother further check.
            OverworldBiomeGenerator.BiomeFeature feature = getBiomeFeature(chunkX, chunkZ);
            switch (feature.evaluatedBiome) {
                case NORMAL:
                case JUNGLE:
                    break;
                default:
                    return;
            }
            // determine if the structure should be generated
            if (shouldGenerateStructure(feature, chunkX, chunkZ)) {
                Bukkit.broadcastMessage("YES");
                switch (feature.evaluatedBiome) {
                    case NORMAL:
                        generateDungeon(wld, blockX, blockZ);
                        break;
                    case JUNGLE:
                        generateDungeon(wld, blockX, blockZ);
                        break;
                }
            }

        }
    }
}
