package terraria.worldgen.overworld;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import terraria.TerrariaHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class OrePopulator extends BlockPopulator {
    int yOffset;
    public static HashMap<String, Material> oreMaterials;
    static {
        oreMaterials = new HashMap<>(30);
        oreMaterials.put("COPPER",              Material.COAL_ORE);
        oreMaterials.put("IRON",                Material.IRON_ORE);
        oreMaterials.put("SILVER",              Material.LAPIS_ORE);
        oreMaterials.put("GOLD",                Material.GOLD_ORE);
        oreMaterials.put("METEORITE",           Material.RED_GLAZED_TERRACOTTA);
        oreMaterials.put("HELLSTONE",           Material.MAGMA);
        oreMaterials.put("COBALT",              Material.LAPIS_BLOCK);
        oreMaterials.put("LIFE_CRYSTAL",        Material.EMERALD_ORE);
        oreMaterials.put("MYTHRIL",             Material.EMERALD_BLOCK);
        oreMaterials.put("ADAMANTITE",          Material.REDSTONE_ORE);
        oreMaterials.put("CHARRED",             Material.QUARTZ_ORE);
        oreMaterials.put("CHLOROPHYTE",         Material.MOSSY_COBBLESTONE);
        oreMaterials.put("SEA_PRISM",           Material.SEA_LANTERN);
        oreMaterials.put("AERIALITE",           Material.DIAMOND_ORE);
        oreMaterials.put("CRYONIC",             Material.DIAMOND_BLOCK);
        oreMaterials.put("PERENNIAL",           Material.LIME_GLAZED_TERRACOTTA);
        oreMaterials.put("SCORIA",              Material.COAL_BLOCK);
        oreMaterials.put("ASTRAL",              Material.REDSTONE_BLOCK);
        oreMaterials.put("EXODIUM",             Material.BLACK_GLAZED_TERRACOTTA);
        oreMaterials.put("UELIBLOOM",           Material.BROWN_GLAZED_TERRACOTTA);
        oreMaterials.put("AURIC",               Material.YELLOW_GLAZED_TERRACOTTA);
    }
    static int SURFACE = 50,
            UNDERGROUND = 0,
            CAVERN = -100,
            DEEP_CAVERN = -150;
    public OrePopulator(int yOffset) {
        this.yOffset = yOffset;
    }
    // helper functions
    void generateSingleVein(World wld, Chunk chunk, Material oreType, int blockX, int blockY, int blockZ, int size) {
        double radius = (size - 1d) / 2d,
                maxDistSqr = radius * radius * 1.25; // (radius * ) ^ 2
        for (int i = 0; i < size; i ++) {
            int oreX = blockX + i;
            for (int j = 0; j < size; j++) {
                int oreY = blockY + j;
                for (int k = 0; k < size; k++) {
                    int oreZ = blockZ + k;
                    if (oreY <= 0 || oreY >= 255) continue; // no overriding bedrock/outside of world
                    double xDistFromCenter = i - radius,
                            yDistFromCenter = j - radius,
                            zDistFromCenter = k - radius;
                    double distSqr = xDistFromCenter * xDistFromCenter + yDistFromCenter * yDistFromCenter + zDistFromCenter * zDistFromCenter;
                    if (distSqr > maxDistSqr) continue; // make the shape less sharp

                    Block blk = wld.getBlockAt(oreX, oreY, oreZ);
                    if (blk.getType().isSolid()) {
                        switch (blk.getType()) {
                            case BEDROCK:
                            // lizard temple/dungeon
                            case SMOOTH_BRICK:
                                break;
                            default:
                                blk.setType(oreType);
                        }
                    }
                }
            }
        }
    }
    void generateGenericOre(World wld, Random rdm, Chunk chunk, int yMax, int stepSize, String oreName, int size) {
        Material oreType = oreMaterials.getOrDefault(oreName, Material.STONE);
        int blockXStart = chunk.getX() << 4, blockZStart = chunk.getZ() << 4;
        yMax = Math.min(256, yMax - yOffset) - stepSize;
        int modulo = 15 - size;
        for (int y = yMax; y >= -stepSize; y -= stepSize) {
            int rdmNum = rdm.nextInt();
            double xRdm = rdmNum & 63;
            rdmNum = rdmNum >> 6;
            double yRdm = rdmNum & 255;
            rdmNum = rdmNum >> 8;
            double zRdm = rdmNum & 63;
            // rescale x, y and z so the ore do not generate beyond the chunk
            xRdm = modulo *   xRdm / 63;
            yRdm = stepSize * yRdm / 255;
            zRdm = modulo *   zRdm / 63;
            generateSingleVein(wld, chunk,
                    oreType,
                    blockXStart + (int) xRdm,
                    y + (int) yRdm,
                    blockZStart + (int) zRdm,
                    size);
        }
    }

    // first, all ores from vanilla Terraria
    void generateLifeCrystal(World wld, Random rdm, Chunk chunk) {
        // there is 10% chance for a chunk to spawn a life crystal
        if (rdm.nextDouble() > 0.1) return;
        int rdmNum = rdm.nextInt();
        int xRdm = rdmNum & 15;
        rdmNum = rdmNum >> 6;
        int zRdm = rdmNum & 15;
        ArrayList<Integer> appropriateY = new ArrayList<>(3);
        // find appropriate heights that crystal can spawn at
        boolean solidBelow, solidCurr = chunk.getBlock(xRdm, 1, zRdm).getType().isSolid();
        for (int i = 2; i < 253; i ++) {
            solidBelow = solidCurr;
            solidCurr = chunk.getBlock(xRdm, i, zRdm).getType().isSolid();
            if (solidBelow && !solidCurr)
                appropriateY.add(i);
        }
        // return if no place is available
        if (appropriateY.isEmpty())
            return;
        // set block
        Material lifeCrystalMat = oreMaterials.getOrDefault("LIFE_CRYSTAL", Material.EMERALD_ORE);
        chunk.getBlock(xRdm, appropriateY.get( (int) (rdm.nextDouble() * appropriateY.size()) ), zRdm)
                .setType(lifeCrystalMat);
    }
    void generateCopper(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, SURFACE, 32, "COPPER", 4);
    }
    void generateIron(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, UNDERGROUND, 40, "IRON", 4);
    }
    void generateSilver(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, CAVERN, 48, "SILVER", 4);
    }
    void generateGold(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, DEEP_CAVERN, 64, "GOLD", 4);
    }
    void generateMeteorite(World wld, Random rdm, Chunk chunk) {
        if (yOffset != 0) return; // only surface world get this ore
        if (rdm.nextDouble() < 0.001) {
            int xCenter = (chunk.getX() << 4) + 5 + (int) (rdm.nextDouble() * 6),
                    zCenter = (chunk.getZ() << 4) + 5 + (int) (rdm.nextDouble() * 6);
            int height = wld.getHighestBlockYAt(xCenter, zCenter);
            if (height < OverworldChunkGenerator.LAND_HEIGHT || height > OverworldChunkGenerator.LAND_HEIGHT + 20) return;
            Material oreMat = oreMaterials.getOrDefault("METEORITE", Material.STONE);
            height -= 6;
            // set spherical cluster of ore
            for (int xOffset = -10; xOffset <= 10; xOffset ++) {
                for (int zOffset = -10; zOffset <= 10; zOffset ++) {
                    int horDistSqr = (xOffset * xOffset) + (zOffset * zOffset);
                    if (horDistSqr > 100) continue;
                    int xSet = xCenter + xOffset, zSet = zCenter + zOffset;
                    for (int ySet = height + 1; true; ySet ++) {
                        double distSqr = (ySet - height) * (ySet - height) + horDistSqr;
                        if (distSqr > 100) break;
                        Block blk = wld.getBlockAt(xSet, ySet, zSet);
                        if (blk.getType().isSolid()) blk.setType(distSqr < 64 ? Material.AIR : oreMat);
                    }
                    for (int ySet = height; true; ySet --) {
                        double distSqr = (ySet - height) * (ySet - height) + horDistSqr;
                        if (distSqr > 100) break;
                        Block blk = wld.getBlockAt(xSet, ySet, zSet);
                        if (blk.getType().isSolid()) blk.setType(distSqr < 64 ? Material.AIR : oreMat);
                    }
                }
            }
        }
    }
    void generateCobalt(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, UNDERGROUND, 48, "COBALT", 4);
    }
    void generateMythril(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, CAVERN, 64, "MYTHRIL", 5);
    }
    void generateAdamantite(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, DEEP_CAVERN, 72, "ADAMANTITE", 5);
    }
    void generateChlorophyte(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.MUTATED_JUNGLE)
            generateGenericOre(wld, rdm, chunk, CAVERN, 128, "CHLOROPHYTE", 5);
    }
    // Calamity
    // pre-hardmode
    void generateSeaPrism(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.MUTATED_DESERT)
            generateGenericOre(wld, rdm, chunk, UNDERGROUND, 72, "SEA_PRISM", 5);
    }
    void generateAerialite(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, CAVERN, 80, "AERIALITE", 6);
    }
    // hardmode
    // charred ore only generates in the hell level.
    void generateCryonic(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.MUTATED_TAIGA_COLD)
            generateGenericOre(wld, rdm, chunk, UNDERGROUND, 96, "CRYONIC", 6);
    }
    void generatePerennial(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.MUTATED_JUNGLE)
            generateGenericOre(wld, rdm, chunk, UNDERGROUND, 96, "PERENNIAL", 6);
    }
    void generateScoria(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.DEEP_OCEAN)
            generateGenericOre(wld, rdm, chunk, CAVERN, 80, "SCORIA", 5);
    }
    // post-moon lord

    void generateExodium(World wld, Random rdm, Chunk chunk) {
        if (yOffset < 0) return; // only surface world get this ore
        if (rdm.nextDouble() < 0.001) {
            int xCenter = (chunk.getX() << 4) + (int) (rdm.nextDouble() * 16),
                    zCenter = (chunk.getZ() << 4) + (int) (rdm.nextDouble() * 16),
                    height = 215 + rdm.nextInt(20);
            int worldHeight = wld.getHighestBlockYAt(xCenter, zCenter);
            if (worldHeight > 125) return; // prevents getting too close to ground
            Material oreMat = oreMaterials.getOrDefault("EXODIUM", Material.STONE);
            // set spherical cluster of ore
            for (int xOffset = -10; xOffset <= 10; xOffset ++) {
                for (int zOffset = -10; zOffset <= 10; zOffset ++) {
                    int horDistSqr = (xOffset * xOffset) + (zOffset * zOffset);
                    if (horDistSqr > 100) continue;
                    int xSet = xCenter + xOffset, zSet = zCenter + zOffset;
                    for (int ySet = height - 10; ySet <= height + 10; ySet ++) {
                        if ((ySet - height) * (ySet - height) + horDistSqr > 100) continue;
                        wld.getBlockAt(xSet, ySet, zSet).setType(oreMat);
                    }
                }
            }
        }
    }
    void generateUelibloom(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.MUTATED_JUNGLE)
            generateGenericOre(wld, rdm, chunk, CAVERN, 160, "UELIBLOOM", 6);
    }
    void generateAuric(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, DEEP_CAVERN, 256, "AURIC", 8);
    }
    void generateHellstone(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, 75, 24, "HELLSTONE", 4);
    }
    void generateCharred(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.SAVANNA)
            generateGenericOre(wld, rdm, chunk, 60, 32, "CHARRED", 5);
    }
    // TODO
    void generateUndergroundLake(World world, Random random, Chunk chunk) {
        // source code from https://bukkit.fandom.com/wiki/Developing_a_World_Generator_Plugin
        // which is converted from vanilla minecraft's lake generator
        if (random.nextInt(100) < 10) {
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();

            int randomX = chunkX * 16 + random.nextInt(16);
            int randomZ = chunkZ * 16 + random.nextInt(16);
            int y;

            for (y = 1; world.getBlockAt(randomX, y, randomZ).getType() != Material.AIR; y++) {
                if (y >= 225) return;
            }
            y -= 7;

            Block block = world.getBlockAt(randomX + 8, y, randomZ + 8);

            if (world.getEnvironment() == World.Environment.NORMAL && y + yOffset > -150) {
                block.setType(Material.WATER);
            } else {
                block.setType(Material.LAVA);
            }

            boolean[] booleans = new boolean[2048];

            int i = random.nextInt(4) + 4;

            int j, j1, k1;

            for (j = 0; j < i; ++j) {
                double d0 = random.nextDouble() * 6.0D + 3.0D;
                double d1 = random.nextDouble() * 4.0D + 2.0D;
                double d2 = random.nextDouble() * 6.0D + 3.0D;
                double d3 = random.nextDouble() * (16.0D - d0 - 2.0D) + 1.0D + d0 / 2.0D;
                double d4 = random.nextDouble() * (8.0D - d1 - 4.0D) + 2.0D + d1 / 2.0D;
                double d5 = random.nextDouble() * (16.0D - d2 - 2.0D) + 1.0D + d2 / 2.0D;

                for (int k = 1; k < 15; ++k) {
                    for (int l = 1; l < 15; ++l) {
                        for (int i1 = 0; i1 < 7; ++i1) {
                            double d6 = (k - d3) / (d0 / 2.0D);
                            double d7 = (i1 - d4) / (d1 / 2.0D);
                            double d8 = (l - d5) / (d2 / 2.0D);
                            double d9 = d6 * d6 + d7 * d7 + d8 * d8;

                            if (d9 < 1.0D) {
                                booleans[(k * 16 + l) * 8 + i1] = true;
                            }
                        }
                    }
                }
            }

            for (j = 0; j < 16; ++j) {
                for (k1 = 0; k1 < 16; ++k1) {
                    for (j1 = 0; j1 < 8; ++j1) {
                        if (booleans[(j * 16 + k1) * 8 + j1]) {
                            world.getBlockAt(randomX + j, y + j1, randomZ + k1).setType(j1 > 4 ? Material.AIR : block.getType());
                        }
                    }
                }
            }

            for (j = 0; j < 16; ++j) {
                for (k1 = 0; k1 < 16; ++k1) {
                    for (j1 = 4; j1 < 8; ++j1) {
                        if (booleans[(j * 16 + k1) * 8 + j1]) {
                            int X1 = randomX + j;
                            int Y1 = y + j1 - 1;
                            int Z1 = randomZ + k1;

                            if (world.getBlockAt(X1, Y1, Z1).getType() == Material.DIRT) {
                                world.getBlockAt(X1, Y1, Z1).setType(Material.GRASS);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void populate(World wld, Random rdm, Chunk chunk) {
//        generateUndergroundLake(wld, rdm, chunk);
        // underworld
        if (wld.getName().equals(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD)) {
            generateHellstone(wld, rdm, chunk);
            generateCharred(wld, rdm, chunk);
        }
        // overworld
        else {
            // vanilla Terraria
            generateCopper(wld, rdm, chunk);
            generateIron(wld, rdm, chunk);
            generateSilver(wld, rdm, chunk);
            generateGold(wld, rdm, chunk);
            generateMeteorite(wld, rdm, chunk);
            generateCobalt(wld, rdm, chunk);
            generateMythril(wld, rdm, chunk);
            generateAdamantite(wld, rdm, chunk);
            generateChlorophyte(wld, rdm, chunk);
            // Calamity, pre-hardmode
            generateSeaPrism(wld, rdm, chunk);
            generateAerialite(wld, rdm, chunk);
            // Calamity, hardmode
            generateCryonic(wld, rdm, chunk);
            generatePerennial(wld, rdm, chunk);
            generateScoria(wld, rdm, chunk);
            // Calamity, post-moon lord
            generateExodium(wld, rdm, chunk);
            generateUelibloom(wld, rdm, chunk);
            generateAuric(wld, rdm, chunk);
            // life crystal should spawn as the last one, otherwise other ore would override it
            if (wld.getName().equals(TerrariaHelper.Constants.WORLD_NAME_CAVERN)) {
                generateLifeCrystal(wld, rdm, chunk);
            }
        }
    }
}
