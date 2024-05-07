package terraria.worldgen.overworld;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.BlockPopulator;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;
import terraria.worldgen.Maze;
import terraria.worldgen.MazeGeneratorPrim;

import java.util.*;

public class StructurePopulator extends BlockPopulator {
    // no duplication within 300 blocks (20 chunks)
    static final int STRUCT_INTERVAL = 5, CHECK_STEPS_RADIUS = 4;
    static final Material MAT_BRICK = Material.SMOOTH_BRICK, MAT_ASTRAL_STONE = Material.STAINED_CLAY;
    static final byte DATA_ASTRAL = 15, DATA_DUNGEON = 2, DATA_LIZARD = 1, DATA_NONE = 0;
    boolean isSurface;

    public StructurePopulator(boolean surfaceOrUnderground) {
        super();
        this.isSurface = surfaceOrUnderground;
    }
    protected static class StructPosInfo {
        int x, z, y;
        public StructPosInfo(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        public StructPosInfo(StructPosInfo posOther) {
            this.x = posOther.x;
            this.z = posOther.z;
            this.y = posOther.y;
        }
    }

    protected void registerSingleBlock(HashMap<Block, Boolean> blocks, Block blk, boolean boolReg, boolean override) {
        if (!override && blocks.containsKey(blk))
            return;
        blocks.put(blk, boolReg);
    }
    protected void registerBlockPlane(World wld, HashMap<Block, Boolean> blocks,
                                      int centerX, int y, int centerZ, int radius, boolean boolReg) {
        registerBlockPlane(wld, blocks, centerX - radius, centerZ - radius, y,
                centerX + radius, centerZ + radius, boolReg, true);
    }
    protected void registerBlockPlane(World wld, HashMap<Block, Boolean> blocks,
                                      int centerX, int y, int centerZ, int radius, boolean boolReg, boolean override) {
        registerBlockPlane(wld, blocks, centerX - radius, centerZ - radius, y,
                centerX + radius, centerZ + radius, boolReg, override);
    }
    protected void registerBlockPlane(World wld, HashMap<Block, Boolean> blocks,
                                      int minX, int minZ, int y, int maxX, int maxZ, boolean boolReg, boolean override) {
        for (int x = minX; x <= maxX; x ++) {
            for (int z = minZ; z <= maxZ; z ++) {
                Block blk = wld.getBlockAt(x, y, z);
                registerSingleBlock(blocks, blk, boolReg, override);
            }
        }
    }
    protected void registerBlockPlanarCircle(World wld, HashMap<Block, Boolean> blocks,
                                      int centerX, int y, int centerZ, int radius, boolean boolReg, boolean override) {
        double radSqr = radius * radius + 1e-3;
        double[] offsetSqr = new double[radius + 1];
        for (int i = 0; i <= radius; i ++)
            offsetSqr[i] = i * i;
        // do not attempt to "optimize" the loop - it will cause funny-looking circles.
        for (int xOffset = 0; xOffset <= radius; xOffset ++) {
            for (int zOffset = 0; zOffset <= radius; zOffset ++) {
                if (offsetSqr[xOffset] + offsetSqr[zOffset] > radSqr)
                    continue;

                registerSingleBlock(blocks, wld.getBlockAt(centerX + xOffset, y, centerZ + zOffset), boolReg, override);
                // flipped z
                if (zOffset != 0)
                    registerSingleBlock(blocks, wld.getBlockAt(centerX + xOffset, y, centerZ - zOffset), boolReg, override);
                // flipped x
                if (xOffset != 0)
                    registerSingleBlock(blocks, wld.getBlockAt(centerX - xOffset, y, centerZ + zOffset), boolReg, override);
                // flipped both
                if (xOffset != 0 && zOffset != 0)
                    registerSingleBlock(blocks, wld.getBlockAt(centerX - xOffset, y, centerZ - zOffset), boolReg, override);
            }
        }
    }
    protected void setBlocks(HashMap<Block, Boolean> blocks, Material trueMat, Material falseMat, byte trueDt, byte falseDt) {
        for (Block b : blocks.keySet()) {
            // break trees/grass
            switch (b.getType()) {
                case LOG:
                case LOG_2:
                case LEAVES:
                case LEAVES_2:
                    WorldHelper.attemptDestroyVegetation(b, true, false);
                    break;
                case DIRT:
                case GRASS:
                    WorldHelper.attemptDestroyVegetation(b.getRelative(BlockFace.UP), true, false);
            }

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
    protected int getSurfaceY(World wld, int x, int z, boolean ignoreWater) {
        Block highestBlk = wld.getHighestBlockAt(x, z);
        boolean invalid = true;
        while (invalid) {
            switch (highestBlk.getType()) {
                case LOG:
                case LOG_2:
                case LEAVES:
                case LEAVES_2:
                // do not spawn on exodium!
                case BLACK_GLAZED_TERRACOTTA:
                    break;
                // do not place structures lower than water surface
                case WATER:
                case STATIONARY_WATER:
                    invalid = ignoreWater;
                    break;
                default:
                    invalid = ! (highestBlk.getType().isSolid());
            }
            highestBlk = highestBlk.getRelative(BlockFace.DOWN);
        }
        return highestBlk.getY();
    }


    // helper function to generate the spawn point shelter
    protected void generateSpawnShelter(World wld) {
        int radius = 24, radius_grass = 40;
        HashMap<Block, Boolean> floor = new HashMap<>(), fence = new HashMap<>();
        registerBlockPlane(wld, floor, 0, OverworldChunkGenerator.LAND_HEIGHT - 1, 0, radius_grass, true);
        registerBlockPlane(wld, fence, 0, OverworldChunkGenerator.LAND_HEIGHT, 0, radius, true);
        registerBlockPlane(wld, fence, 0, OverworldChunkGenerator.LAND_HEIGHT, 0, radius - 1, false);
        setBlocks(floor, Material.GRASS, Material.GRASS, DATA_NONE, DATA_NONE);
        setBlocks(fence, Material.FENCE, Material.AIR, DATA_NONE, DATA_NONE);
    }

    // helper functions to generate the dungeon parts
    protected void generateDungeonEntrance(World wld, StructPosInfo posInfo) {
        HashMap<Block, Boolean> struct = new HashMap<>();
        int xOffset = 0, zOffset = 0, offsetRemainingDuration = -999;
        for (int y = 1; y < posInfo.y - 6; y ++) {
            if (offsetRemainingDuration == 0) {
                xOffset = 0;
                zOffset = 0;
            }
            else if (offsetRemainingDuration <= -4) {
                offsetRemainingDuration = 8 + (int) (Math.random() * 8);
                xOffset = (int) (Math.random() * 3) - 1;
                zOffset = (int) (Math.random() * 3) - 1;
            }
            registerBlockPlane(wld, struct, posInfo.x, y, posInfo.z, 6, true);
            registerBlockPlane(wld, struct, posInfo.x, y, posInfo.z, 3, false);
            posInfo.x += xOffset;
            posInfo.z += zOffset;
            offsetRemainingDuration --;
            if (offsetRemainingDuration % 2 == 0) {
                posInfo.y = getSurfaceY(wld, posInfo.x, posInfo.z, false);
            }
        }
        setBlocks(struct, MAT_BRICK, Material.AIR, DATA_DUNGEON, DATA_NONE);
    }
    protected void generateDungeonEntranceBuilding(World wld, StructPosInfo posInfo) {
        HashMap<Block, Boolean> struct = new HashMap<>();
        // floor
        int currY = posInfo.y - 6;
        for (int i = 0; i < 9; i ++) {
            registerBlockPlane(wld, struct, posInfo.x, currY, posInfo.z, 20 - i, true);
            registerBlockPlane(wld, struct, posInfo.x, currY, posInfo.z, 2, false);
            currY ++;
        }
        // room space and pillar
        int[] pillarXOffsets = { -9, -9, -9, -9, -4, -4,  4,  4,  9,  9,  9,  9 };
        int[] pillarZOffsets = { -9, -4,  4,  9, -9,  9, -9,  9, -9, -4,  4,  9 };
        for (int i = 0; i < 8; i ++) {
            // space
            registerBlockPlane(wld, struct, posInfo.x, currY, posInfo.z, 12, false);
            // pillars
            for (int idx = 0; idx < pillarXOffsets.length; idx ++)
                registerBlockPlane(wld, struct,
                        posInfo.x + pillarXOffsets[idx], currY, posInfo.z + pillarZOffsets[idx], 1, true);
            currY ++;
        }
        // ceiling
        for (int i = 0; i < 2; i ++) {
            registerBlockPlane(wld, struct, posInfo.x, currY, posInfo.z, 12, true);
            currY ++;
        }
        // set the blocks
        setBlocks(struct, MAT_BRICK, Material.AIR, DATA_DUNGEON, DATA_NONE);
    }
    // dungeon underground parts
    protected void generateDungeonUndergroundCorridor(World wld, StructPosInfo posInfo, int maxRec,
                                                      ArrayList<StructPosInfo> allRooms, ArrayList<Integer> allRoomSize,
                                                      HashMap<Block, Boolean> struct) {
        int xOffset = 0, zOffset = 0,
                // the main branch would start vertical, so the player would not get stuck.
                offsetRemainingDuration = maxRec == -1 ? 0 : -999;
        // corridor
        for (int corridorLength = 20 + (int) (Math.random() * 10); corridorLength > 0; corridorLength --) {
            if (offsetRemainingDuration == 0) {
                xOffset = 0;
                zOffset = 0;
            }
            else if (offsetRemainingDuration <= -3) {
                offsetRemainingDuration = 8 + (int) (Math.random() * 4);
                xOffset = (int) (Math.random() * 5) - 2;
                zOffset = (int) (Math.random() * 5) - 2;
            }
            registerBlockPlane(wld, struct, posInfo.x, posInfo.y, posInfo.z, 6, true, false);
            registerBlockPlane(wld, struct, posInfo.x, posInfo.y, posInfo.z, 4, false, true);
            posInfo.x += xOffset;
            posInfo.y --;
            posInfo.z += zOffset;
            offsetRemainingDuration --;
        }
        // generate room
        generateDungeonUndergroundRoom(wld, posInfo, maxRec - 1, allRooms, allRoomSize, struct);
    }
    protected void generateDungeonUndergroundRoom(World wld, StructPosInfo posInfo, int subsequentRooms,
                                                  ArrayList<StructPosInfo> allRooms, ArrayList<Integer> allRoomSize,
                                                  HashMap<Block, Boolean> struct) {
        int roomRadius = (int) (16 + Math.random() * 8), roomHeight = (int) (16 + Math.random() * 8);
        // the final, the biggest room
        if (subsequentRooms < 0 && posInfo.y < 100) {
            roomRadius = (int) (40 + Math.random() * 16);
            roomHeight = Math.min(posInfo.y - 10, 48);
            // do not add further rooms!
            subsequentRooms = 1;
        }
        // make sure the room is not connected at the center
        int xOffset = (int) (Math.random() * roomRadius), zOffset = (int) (Math.random() * roomRadius);
        if (Math.random() < 0.5)
            xOffset *= -1;
        if (Math.random() < 0.5)
            zOffset *= -1;
        // spawn the room; offset y up by 3 to fill the ceiling.
        posInfo.x += xOffset;
        posInfo.y += 3;
        posInfo.z += zOffset;
        for (int i = -3; i < roomHeight + 3; i ++) {
            registerBlockPlane(wld, struct, posInfo.x, posInfo.y, posInfo.z, roomRadius + 3, true, false);
            if (i >= 0 && i < roomHeight)
                registerBlockPlane(wld, struct, posInfo.x, posInfo.y, posInfo.z, roomRadius, false, true);
            posInfo.y --;
        }
        // register the room!
        allRooms.add(new StructPosInfo(posInfo));
        allRoomSize.add(roomRadius);
        // next corridor if needed
        if (subsequentRooms != 0) {
            generateDungeonSubsequentCorridor(wld, allRooms.size() - 1, subsequentRooms, allRooms, allRoomSize, struct);
        }
    }
    protected void generateDungeonSubsequentCorridor(World wld, int roomIdx, int recLevel,
                                                     ArrayList<StructPosInfo> allRooms, ArrayList<Integer> allRoomSize,
                                                     HashMap<Block, Boolean> struct) {
        StructPosInfo posRandomRoom = allRooms.get( roomIdx );
        // ensure the deepest room is not broken by other room generation
        if (recLevel > 0 && posRandomRoom.y < 100)
            return;
        // initialize the position to generate the new path
        StructPosInfo posNewCorridor = new StructPosInfo(posRandomRoom);
        posNewCorridor.y += 4;
        int offset = allRoomSize.get(roomIdx);
        if (Math.random() < 0.5)
            offset *= -1;
        if (Math.random() < 0.5)
            posNewCorridor.x += offset;
        else
            posNewCorridor.z += offset;
        // generate the new branch
        generateDungeonUndergroundCorridor(wld, posNewCorridor, recLevel, allRooms, allRoomSize, struct);
    }
    protected void planDungeonUnderground(World wld, StructPosInfo posInfo) {
        HashMap<Block, Boolean> struct = new HashMap<>();
        int roomsTotal = 15 + (int) ( Math.random() * 5 );
        ArrayList<StructPosInfo> allRooms = new ArrayList<>();
        ArrayList<Integer> allRoomSize = new ArrayList<>();
        // initialize the main branch
        generateDungeonUndergroundCorridor(wld, posInfo, -1, allRooms, allRoomSize, struct);
        // append new branches when needed
        while (allRooms.size() < roomsTotal) {
            int idx = (int) (Math.random() * allRooms.size());
            generateDungeonSubsequentCorridor(wld, idx, MathHelper.randomRound(1.5), allRooms, allRoomSize, struct);
        }
        // finally, place the blocks
        setBlocks(struct, MAT_BRICK, Material.AIR, DATA_DUNGEON, DATA_NONE);
    }
    // the overall dungeon generation mechanism
    protected void generateDungeon(World wld, int blockX, int blockZ) {
        if (isSurface) {
            int surfaceLevel = getSurfaceY(wld, blockX, blockZ, false);
            StructPosInfo posInfo = new StructPosInfo(blockX, surfaceLevel, blockZ);
            generateDungeonEntrance(wld, posInfo);
            generateDungeonEntranceBuilding(wld, posInfo);
        }
        else {
            StructPosInfo posInfo = new StructPosInfo(blockX, WorldHelper.CAVERN_Y_BELOW_BEDROCK, blockZ);
            planDungeonUnderground(wld, posInfo);
        }
    }

    // astral infection altar
    protected void generateAstral(World wld, int blockX, int blockZ) {
        int height = getSurfaceY(wld, blockX, blockZ, true);
        // the base; true-ore, false-astral dirt
        HashMap<Block, Boolean> struct = new HashMap<>();
        {
            int radius = 1;
            // the base
            height -= 3;
            for (int i = -3; i < 21; i ++) {
                registerBlockPlanarCircle(wld, struct, blockX, height, blockZ, radius, false, true);
                height ++;
                switch (i) {
                    case 0:
                    case 1:
                        radius -= 2;
                        break;
                    case 2:
                    case 4:
                    case 16:
                    case 17:
                    case 19:
                        radius --;
                        break;
                    case 11:
                    case 12:
                    case 14:
                        radius ++;
                        break;
                    case -2:
                    case -1:
                        radius += 2;
                        break;
                    case -3:
                        radius += 3;
                        break;
                }
            }
            // the sphere of ores
            height -= 2;
            radius = 1;
            for (int i = 0; i < 10; i ++) {
                registerBlockPlanarCircle(wld, struct, blockX, height, blockZ, radius, true, true);
                height ++;
                switch (i) {
                    case 0:
                    case 3:
                    case 4:
                        radius ++;
                        break;
                    case 5:
                    case 6:
                        radius += 2;
                        break;
                    case 8:
                        radius --;
                        break;
                }
            }
        }
        // set the base&ore
        Material oreMat = OrePopulator.oreMaterials.getOrDefault("ASTRAL", Material.STONE);
        setBlocks(struct, oreMat, MAT_ASTRAL_STONE, DATA_NONE, DATA_ASTRAL);
        // the altar on the top
        wld.getBlockAt(blockX, height, blockZ).setType(Material.ENDER_PORTAL_FRAME);
    }

    // jungle temple
    public void buildMaze(Location startLocation, int width, int height, MazeGeneratorPrim generator,
                          Material wallBlock, Material corridorBlock, int wallHeight, int zoomSize) {
        // Generate the maze (exterior walls already included)
        Maze maze = new Maze(width, height);
        generator.generate(maze);

        // Entrance and Exit Determination
        boolean entranceTop = (new Random()).nextBoolean();
        int entranceX = entranceTop ? 1 : width - 2;
        int exitX = entranceTop ? width - 2 : 1;
        int entranceZ = 0;
        int exitZ = height - 1;

        // Carve the entrance & exit into the maze info
        maze.setWall(entranceX, entranceZ, false);
        maze.setWall(exitX, exitZ, false);

        // Use HashMap to store the block plan
        HashMap<Block, Boolean> blocks = new HashMap<>();
        int startX = startLocation.getBlockX(), startY = startLocation.getBlockY(), startZ = startLocation.getBlockZ();
        World world = startLocation.getWorld();

        // build the floor; -1 accounts for that maxX and maxZ are inclusive.
        registerBlockPlane(world, blocks, startX, startZ, startY,
                startX + maze.getWidth() * zoomSize - 1, startZ + maze.getHeight() * zoomSize - 1, true, true);

        // Build the structure using your registerBlockPlane function
        for (int x = 0; x < maze.getWidth(); x++) {
            for (int z = 0; z < maze.getHeight(); z++) {
                // Build walls with height; -1 accounts for that maxX and maxZ are inclusive.
                boolean wallFlag = maze.hasWall(x, z);
                for (int y = 1; y <= wallHeight; y++) {
                    registerBlockPlane(world, blocks, startX + x * zoomSize, startZ + z * zoomSize, startY + y,
                            startX + (x + 1) * zoomSize - 1, startZ + (z + 1) * zoomSize - 1, wallFlag, true);
                }
            }
        }

        // Finally, place the structure in the world
        setBlocks(blocks, wallBlock, corridorBlock, (byte) 0, (byte) 0);
    }
    protected void generateLizardTemple(World wld, int blockX, int blockZ) {
        StructPosInfo structPos = new StructPosInfo(blockX, 50 + (int) (Math.random() * 100), blockZ);
        // TODO

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
                        chunkXCoord[i + CHECK_STEPS_RADIUS], chunkZCoord[j + CHECK_STEPS_RADIUS] );
                if (feature.evaluatedBiome == curr.evaluatedBiome) {
                    double diff = feature.biomeSignificance - curr.biomeSignificance;
                    boolean lessSignificant = false;
                    // the other chunk is more significant
                    if (diff > 0)
                        lessSignificant = true;
                    // equally significant: break tie by chunk X, then by chunk Z.
                    else if (diff == 0) {
                        if (i > 0)
                            lessSignificant = true;
                        else if (j > 0)
                            lessSignificant = true;
                    }
                    // deny the generation
                    if (lessSignificant) {
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
                case ASTRAL_INFECTION:
                    break;
                default:
                    return;
            }
            // determine if the structure should be generated
            if (shouldGenerateStructure(feature, chunkX, chunkZ)) {
                switch (feature.evaluatedBiome) {
                    case NORMAL:
                        generateDungeon(wld, blockX, blockZ);
                        break;
                    case JUNGLE:
                        if (! isSurface)
                            generateLizardTemple(wld, blockX, blockZ);
                        break;
                    case ASTRAL_INFECTION:
                        if (isSurface)
                            generateAstral(wld, blockX, blockZ);
                        break;
                }
            }

        }
    }
}
