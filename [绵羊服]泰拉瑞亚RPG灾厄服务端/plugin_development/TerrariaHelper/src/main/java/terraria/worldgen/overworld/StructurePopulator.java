package terraria.worldgen.overworld;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;
import terraria.worldgen.Maze;
import terraria.worldgen.MazeGenerator;
import terraria.worldgen.MazeGeneratorPrim;

import java.util.*;

public class StructurePopulator extends BlockPopulator {
    // no duplication within 300 blocks (20 chunks)
    static final int STRUCT_INTERVAL = 5, CHECK_STEPS_RADIUS = 4;
    static final Material MAT_BRICK = Material.SMOOTH_BRICK, MAT_SPECIAL_STONE = Material.STAINED_CLAY;
    static final byte DATA_ASTRAL = 15, DATA_SUNKEN_SEA = 11, DATA_DUNGEON = 2, DATA_LIZARD = 1, DATA_NONE = 0;
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
        StructureInfo floor = new StructureInfo(Material.GRASS, Material.GRASS, DATA_NONE, DATA_NONE),
                fence = new StructureInfo(Material.FENCE, Material.AIR, DATA_NONE, DATA_NONE);
        floor.planRegisterBlockPlane(wld, 0, OverworldChunkGenerator.LAND_HEIGHT - 1, 0, radius_grass, true, true);
        fence.planRegisterBlockPlane(wld, 0, OverworldChunkGenerator.LAND_HEIGHT, 0, radius, true, true);
        fence.planRegisterBlockPlane(wld, 0, OverworldChunkGenerator.LAND_HEIGHT, 0, radius - 1, false, true);
        floor.performOperations();
        fence.performOperations();
    }

    // helper functions to generate the dungeon parts
    protected void generateDungeonEntrance(World wld, StructPosInfo posInfo) {
        StructureInfo struct = new StructureInfo(MAT_BRICK, Material.AIR, DATA_DUNGEON, DATA_NONE);
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
            struct.planRegisterBlockPlane(wld, posInfo.x, y, posInfo.z, 6, true, true);
            struct.planRegisterBlockPlane(wld, posInfo.x, y, posInfo.z, 3, false, true);
            struct.planSetBlocks();
            posInfo.x += xOffset;
            posInfo.z += zOffset;
            offsetRemainingDuration --;
            if (offsetRemainingDuration % 2 == 0) {
                posInfo.y = getSurfaceY(wld, posInfo.x, posInfo.z, false);
            }
        }
        struct.performOperations();
    }
    protected void generateDungeonEntranceBuilding(World wld, StructPosInfo posInfo) {
        StructureInfo struct = new StructureInfo(MAT_BRICK, Material.AIR, DATA_DUNGEON, DATA_NONE);
        // floor
        int currY = posInfo.y - 6;
        for (int i = 0; i < 9; i ++) {
            struct.planRegisterBlockPlane(wld, posInfo.x, currY, posInfo.z, 20 - i, true, true);
            struct.planRegisterBlockPlane(wld, posInfo.x, currY, posInfo.z, 2, false, true);
            struct.planSetBlocks();
            currY ++;
        }
        // room space and pillar
        int[] pillarXOffsets = { -9, -9, -9, -9, -4, -4,  4,  4,  9,  9,  9,  9 };
        int[] pillarZOffsets = { -9, -4,  4,  9, -9,  9, -9,  9, -9, -4,  4,  9 };
        for (int i = 0; i < 8; i ++) {
            // space
            struct.planRegisterBlockPlane(wld, posInfo.x, currY, posInfo.z, 12, false, true);
            // pillars
            for (int idx = 0; idx < pillarXOffsets.length; idx ++)
                struct.planRegisterBlockPlane(wld,
                        posInfo.x + pillarXOffsets[idx], currY, posInfo.z + pillarZOffsets[idx],
                        1, true, true);
            struct.planSetBlocks();
            currY ++;
        }
        // ceiling
        for (int i = 0; i < 2; i ++) {
            struct.planRegisterBlockPlane(wld, posInfo.x, currY, posInfo.z, 12, true, true);
            currY ++;
        }
        // set the blocks
        struct.performOperations();
    }
    // dungeon underground parts
    protected void generateDungeonUndergroundCorridor(World wld, StructPosInfo posInfo, int maxRec,
                                                      ArrayList<StructPosInfo> allRooms, ArrayList<Integer> allRoomSize,
                                                      StructureInfo struct) {
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
            struct.planRegisterBlockPlane(wld, posInfo.x, posInfo.y, posInfo.z, 6, true, false);
            struct.planRegisterBlockPlane(wld, posInfo.x, posInfo.y, posInfo.z, 4, false, true);
            // prevent next room from getting too far away, causing connection issues
            if (corridorLength != 1) {
                posInfo.x += xOffset;
                posInfo.y--;
                posInfo.z += zOffset;
            }
            offsetRemainingDuration --;

            struct.planSetBlocks();
        }
        // generate room
        generateDungeonUndergroundRoom(wld, posInfo, maxRec - 1, allRooms, allRoomSize, struct);
    }
    protected void generateDungeonUndergroundRoom(World wld, StructPosInfo posInfo, int subsequentRooms,
                                                  ArrayList<StructPosInfo> allRooms, ArrayList<Integer> allRoomSize,
                                                  StructureInfo struct) {
        int roomRadius = (int) (16 + Math.random() * 8), roomHeight = (int) (16 + Math.random() * 8);
        // the final, the biggest room
        if (subsequentRooms < 0 && posInfo.y < 100) {
            roomRadius = (int) (40 + Math.random() * 8);
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
        for (int i = -3; i <= roomHeight + 3; i ++) {
            struct.planRegisterBlockPlane(wld, posInfo.x, posInfo.y, posInfo.z, roomRadius + 4, true, false);
            if (i >= 0 && i <= roomHeight)
                struct.planRegisterBlockPlane(wld, posInfo.x, posInfo.y, posInfo.z, roomRadius, false, true);
            posInfo.y --;
            struct.planSetBlocks();
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
                                                     StructureInfo struct) {
        StructPosInfo posRandomRoom = allRooms.get( roomIdx );
        // ensure the deepest room is not broken by other room generation
        if (recLevel > 0 && posRandomRoom.y < 150)
            return;
        // initialize the position to generate the new path
        StructPosInfo posNewCorridor = new StructPosInfo(posRandomRoom);
        posNewCorridor.y += 3;
        int offset = allRoomSize.get(roomIdx) - 3;
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
        StructureInfo struct = new StructureInfo(MAT_BRICK, Material.AIR, DATA_DUNGEON, DATA_NONE);
        int roomsTotal = 10 + (int) ( Math.random() * 5 );
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
        struct.performOperations();
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
        Material oreMat = OrePopulator.OreMaterial.ASTRAL.material;
        StructureInfo struct = new StructureInfo(oreMat, MAT_SPECIAL_STONE, DATA_NONE, DATA_ASTRAL);
        {
            int radius = 1;
            // the base
            height -= 3;
            for (int i = -3; i < 21; i ++) {
                struct.planRegisterBlockPlanarCircle(wld, blockX, height, blockZ, radius, false, true);
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
                struct.planRegisterBlockPlanarCircle(wld, blockX, height, blockZ, radius, true, true);
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
        struct.performOperations();
        // the altar on the top
        wld.getBlockAt(blockX, height, blockZ).setType(Material.ENDER_PORTAL_FRAME, false);
    }

    // jungle temple
    protected void buildLizardMaze(StructureInfo structure, World wld, StructPosInfo posInfo,
                                int mazeRadius, MazeGenerator generator, int wallHeight, int zoomSize) {
        int mazeSize = mazeRadius * 2 + 1, zoomRadius = zoomSize / 2;
        Maze maze = new Maze(mazeSize, mazeSize);
        generator.generate(maze);

        // carve the maze
        for (int x = -mazeRadius; x <= mazeRadius; x++) {
            for (int z = -mazeRadius; z <= mazeRadius; z++) {
                if (maze.hasWall(x + mazeRadius, z + mazeRadius))
                    continue;
                for (int y = 0; y < wallHeight; y++) {
                    structure.planRegisterBlockPlane(wld,
                            posInfo.x + x * zoomSize, posInfo.y + y, posInfo.z + z * zoomSize,
                            zoomRadius, false, true);
                }
            }
            structure.planSetBlocks();
        }
    }
    protected void buildStair(StructureInfo structure, StructPosInfo startPos, StructPosInfo centerPos,
                              World wld, int radius, int heightTotal, int stairWidth, int stairClearingHeight) {
        int phase;
        if (Math.abs(startPos.x - centerPos.x) >= radius) {
            phase = (startPos.x > centerPos.x) ? 0 : 2;
        }
        else {
            phase = (startPos.z > centerPos.z) ? 1 : 3;
        }
        int stairRad = stairWidth / 2;
        for (int i = 1; i <= heightTotal; i ++) {
            // set blocks
            structure.planRegisterBlockPlane(wld, startPos.x, startPos.y, startPos.z, stairRad, true, true);
            for (int j = 1; j <= stairClearingHeight; j ++)
                structure.planRegisterBlockPlane(wld, startPos.x, startPos.y + j, startPos.z, stairRad, false, true);

            // update pos
            if (i != heightTotal) {
                switch (phase) {
                    case 0:
                        startPos.z += stairWidth;
                        break;
                    case 1:
                        startPos.x -= stairWidth;
                        break;
                    case 2:
                        startPos.z -= stairWidth;
                        break;
                    case 3:
                        startPos.x += stairWidth;
                        break;
                }
                startPos.y --;
            }
            // next direction
            boolean shouldTurn = false;
            switch (phase) {
                case 0:
                case 2:
                    shouldTurn = Math.abs(startPos.z - centerPos.z) >= radius;
                    break;
                case 1:
                case 3:
                    shouldTurn = Math.abs(startPos.x - centerPos.x) >= radius;
                    break;
            }
            if (shouldTurn) {
                phase = (phase + 1) % 4;
            }
        }
        structure.planSetBlocks();
    }
    protected void fineTuneLizardTemple(World wld, StructureInfo structure, StructPosInfo basePos) {
        double lastXOffset, lastZOffset;
        Random rdm = new Random();
        Block baseBlk = wld.getBlockAt(basePos.x, basePos.y, basePos.z);
        // the entrance
        {
            Vector fwd, side;
            int randomNum = rdm.nextInt(4);
            switch (randomNum) {
                case 0:
                    fwd = new Vector(0, 0, 1);
                    side = new Vector(1, 0, 0);
                    break;
                case 1:
                    fwd = new Vector(0, 0, -1);
                    side = new Vector(1, 0, 0);
                    break;
                case 2:
                    fwd = new Vector(1, 0, 0);
                    side = new Vector(0, 0, 1);
                    break;
                case 3:
                default:
                    fwd = new Vector(-1, 0, 0);
                    side = new Vector(0, 0, 1);
                    break;
            }
            // arrays of [fwd, up, sideMin, sideMax]
            int[][] positions = {
                    {20, 63, -1, 1},
                    {21, 60, 0, 0}, {21, 61, 0, 0}, {21, 62, -1, 1}, {21, 63, -2, 2},
                    {22, 60, 0, 0}, {22, 61, -1, 1}, {22, 62, -2, 2},
                    {23, 60, -2, 2}, {23, 61, -3, 3},
                    {24, 60, -3, 3},
            };
            lastXOffset = fwd.getX() * 20;
            lastZOffset = fwd.getZ() * 20;
            for (int[] pos : positions) {
                Vector dir = fwd.clone().multiply(pos[0])
                        .add(side.clone().multiply(pos[2]))
                        .add(new Vector(0, pos[1], 0) );
                for (int offset = pos[2]; offset <= pos[3]; offset ++) {
                    structure.planRegisterSingleBlock(baseBlk.getRelative(dir.getBlockX(), dir.getBlockY(), dir.getBlockZ()),
                            false, true);
                    dir.add(side);
                }
            }
        }
        // two top levels
        int levelY = 60, mazeRadius = 10;
        for (int i = 0; i < 2; i ++) {
            int newXOffset = (int) lastXOffset, newZOffset = (int) lastZOffset;
            while ( (Math.abs(newXOffset - lastXOffset) < 6 && Math.abs(newZOffset - lastZOffset) < 6) ||
                    (newXOffset % 6 < 3 || newZOffset % 6 < 3)) {
                newXOffset = rdm.nextInt(mazeRadius + 1) * 2 - mazeRadius;
                newZOffset = rdm.nextInt(mazeRadius + 1) * 2 - mazeRadius;
            }
            // place the hole
            for (int j = 1; j <= 4; j ++)
                structure.planRegisterSingleBlock(baseBlk.getRelative(newXOffset, levelY - j, newZOffset), false, true);
            // setup for next iteration
            lastXOffset = newXOffset;
            lastZOffset = newZOffset;
            levelY -= 4;
//            mazeRadius += 2;
        }
        // bottom 3 levels
        levelY = 48;
        mazeRadius = 9;
        for (int i = 0; i < 2; i ++) {
            int newXOffset = (int) lastXOffset, newZOffset = (int) lastZOffset;
            while ( Math.abs(newXOffset - lastXOffset) < 16 && Math.abs(newZOffset - lastZOffset) < 16) {
                newXOffset = (rdm.nextInt(mazeRadius + 1) * 2 - mazeRadius) * 3;
                newZOffset = (rdm.nextInt(mazeRadius + 1) * 2 - mazeRadius) * 3;
            }
            // place the stairs
            StructPosInfo startPos = new StructPosInfo(basePos.x + newXOffset,
                    basePos.y + levelY - 1, basePos.z + newZOffset);
            StructPosInfo centerPos = new StructPosInfo(basePos.x + newXOffset,basePos.y + levelY, basePos.z + newZOffset);
            buildStair(structure, startPos, centerPos, wld, 1, 8, 1, 4);
            // setup for next iteration
            lastXOffset = newXOffset;
            lastZOffset = newZOffset;
            levelY -= 8;
            mazeRadius += 2;
        }
        // last bottom level; connect to the boss room
        {
            int newXOffset = (int) lastXOffset, newZOffset = (int) lastZOffset;
            while ( Math.abs(newXOffset - lastXOffset) < 24 && Math.abs(newZOffset - lastZOffset) < 24) {
                int rdmInt = rdm.nextInt(4);
                switch (rdmInt) {
                    case 0:
                        newXOffset = 3;
                        newZOffset = 39;
                        break;
                    case 1:
                        newXOffset = -3;
                        newZOffset = -39;
                        break;
                    case 2:
                        newXOffset = 39;
                        newZOffset = -3;
                        break;
                    case 3:
                        newXOffset = -39;
                        newZOffset = 3;
                        break;
                }
            }
            StructPosInfo startPosSmallerStair = new StructPosInfo(basePos.x + newXOffset, basePos.y + levelY - 1, basePos.z + newZOffset);
            StructPosInfo centerPosSmallerStair = new StructPosInfo(startPosSmallerStair);
            StructPosInfo largerStairPos = new StructPosInfo(centerPosSmallerStair);
            // place the larger stairs
            {
                largerStairPos.y -= 7;
                buildStair(structure, largerStairPos, basePos, wld, 39, 22, 3, 5);
            }
            // place the smaller stairs
            {
                buildStair(structure, startPosSmallerStair, centerPosSmallerStair, wld, 1, 8, 1, 4);
            }
            // place the corridor to the boss room
            int dx = 0, dz = 0, height = 4;
            if (Math.abs(largerStairPos.x - basePos.x) > Math.abs(largerStairPos.z - basePos.z)) {
                dx = largerStairPos.x > basePos.x ? -1 : 1;
            }
            else {
                dz = largerStairPos.z > basePos.z ? -1 : 1;
            }
            largerStairPos.x += dx;
            largerStairPos.z += dz;
            for (int h = 1; h <= height; h ++) {
                structure.planRegisterBlockPlane(wld, largerStairPos.x, largerStairPos.y + h, largerStairPos.z, 1, false, true);
            }
        }
    }
    protected void generateLizardTemple(World wld, int blockX, int blockZ) {
        int startY = 50 + (int) (Math.random() * 100);
        StructureInfo structure = new StructureInfo(MAT_BRICK, Material.AIR, DATA_LIZARD, DATA_NONE);
        // pyramid structure itself
        for (int i = 0; i < 28; i ++) {
            structure.planRegisterBlockPlane(wld, blockX, startY + i, blockZ, 56, true, true);
            if (i >= 4)
                structure.planRegisterBlockPlane(wld, blockX, startY + i, blockZ, 36, false, true);
            structure.planSetBlocks();
        }
        for (int i = 28; i < 64; i ++) {
            structure.planRegisterBlockPlane(wld, blockX, startY + i, blockZ, 84-i, true, true);
            structure.planSetBlocks();
        }
        // the maze
        MazeGeneratorPrim mazeGen = new MazeGeneratorPrim();
        // the top levels
        {
            int topLevelY = startY + 60, topMazeRad = 20;
            for (int i = 0; i < 2; i++) {
                buildLizardMaze(structure, wld, new StructPosInfo(blockX, topLevelY, blockZ),
                        topMazeRad, mazeGen, 2, 1);
                topLevelY -= 4;
//                topMazeRad += 2;
            }
        }
        // the bottom levels
        {
            int bottomLevelY = startY + 48, bottomMazeRad = 9;
            for (int i = 0; i < 3; i++) {
                buildLizardMaze(structure, wld, new StructPosInfo(blockX, bottomLevelY, blockZ),
                        bottomMazeRad, mazeGen, 4, 3);
                bottomLevelY -= 8;
                bottomMazeRad += 2;
            }
        }
        // fine tune (entrance, stairs etc.)
        fineTuneLizardTemple(wld, structure, new StructPosInfo(blockX, startY, blockZ));
        // place the blocks
        structure.performOperations();
    }

    // the eye of sunken sea
    protected void generateEyeOfSunkenSea(World wld, int blockX, int blockY, int blockZ, boolean oreOrRock) {
        // struct: shell, core
        StructureInfo struct;
        int innerRad, outerRad;
        if (oreOrRock) {
            struct = new StructureInfo(OrePopulator.OreMaterial.SEA_PRISM.material, Material.STATIONARY_WATER, DATA_NONE, DATA_NONE);
            innerRad = 4;
            outerRad = 9;
        }
        else {
            struct = new StructureInfo(Material.STATIONARY_WATER, MAT_SPECIAL_STONE, DATA_NONE, DATA_SUNKEN_SEA);
            innerRad = 16;
            outerRad = 40;
        }
        // place blocks
        for (int yOffset = outerRad - 1; yOffset > -outerRad; yOffset --) {
            int radOuter = (int) Math.round( Math.sqrt( outerRad * outerRad - yOffset * yOffset) );
            struct.planRegisterBlockPlanarCircle(wld, blockX, blockY + yOffset, blockZ, radOuter, true, true);
            if (yOffset < innerRad && yOffset > -innerRad) {
                int radInner = (int) Math.round( Math.sqrt( innerRad * innerRad - yOffset * yOffset) );
                struct.planRegisterBlockPlanarCircle(wld, blockX, blockY + yOffset, blockZ, radInner, false, true);
            }
            struct.planSetBlocks();
        }
        // inner ore core at last
        if (! oreOrRock) {
            struct.planOperation(() -> generateEyeOfSunkenSea(wld, blockX, blockY, blockZ, true));
        }
        struct.performOperations();
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
                case ASTRAL_INFECTION:
                case JUNGLE:
                case NORMAL:
                case DESERT:
                case SUNKEN_SEA:
                    break;
                default:
                    return;
            }
            // determine if the structure should be generated
            if (shouldGenerateStructure(feature, chunkX, chunkZ)) {
                switch (feature.evaluatedBiome) {
                    case ASTRAL_INFECTION:
                        if (isSurface)
                            generateAstral(wld, blockX, blockZ);
                        break;
                    case JUNGLE:
                        if (! isSurface)
                            generateLizardTemple(wld, blockX, blockZ);
                        break;
                    case NORMAL:
                        generateDungeon(wld, blockX, blockZ);
                        break;
                    case DESERT:
                    case SUNKEN_SEA:
                        if (! isSurface)
                            generateEyeOfSunkenSea(wld, blockX, 100 + rdm.nextInt(50), blockZ, false);
                        break;
                }
            }

        }
    }
}
