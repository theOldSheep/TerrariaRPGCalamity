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
    public enum OreMaterial {
        // vanilla
        COPPER(Material.COAL_ORE), IRON(Material.IRON_ORE), SILVER(Material.LAPIS_ORE), GOLD(Material.GOLD_ORE),
        METEORITE(Material.RED_GLAZED_TERRACOTTA), HELLSTONE(Material.MAGMA), LIFE_CRYSTAL(Material.EMERALD_ORE),
        COBALT(Material.LAPIS_BLOCK), MYTHRIL(Material.EMERALD_BLOCK), ADAMANTITE(Material.REDSTONE_ORE),
        CHLOROPHYTE(Material.MOSSY_COBBLESTONE),
        // calamity
        SEA_PRISM(Material.SEA_LANTERN), AERIALITE(Material.DIAMOND_ORE),
        CHARRED(Material.QUARTZ_ORE), CRYONIC(Material.DIAMOND_BLOCK),
        PERENNIAL(Material.LIME_GLAZED_TERRACOTTA), SCORIA(Material.COAL_BLOCK),
        ASTRAL(Material.REDSTONE_BLOCK), EXODIUM(Material.BLACK_GLAZED_TERRACOTTA),
        UELIBLOOM(Material.BROWN_GLAZED_TERRACOTTA), AURIC(Material.YELLOW_GLAZED_TERRACOTTA);

        public final Material material;
        OreMaterial(Material mat) {
            this.material = mat;
        }
    }
    static int SURFACE = 50,
            UNDERGROUND = 0,
            CAVERN = -100,
            DEEP_CAVERN = -150,
            ABYSS_MID = -75;
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
                            // underground chests
                            case WOOD:
                            case CHEST:
                                break;
                            default:
                                blk.setType(oreType, false);
                        }
                    }
                }
            }
        }
    }
    void generateGenericOre(World wld, Random rdm, Chunk chunk, int yMax, int stepSize, OreMaterial oreName, int size) {
        Material oreType = oreName.material;
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
        Material lifeCrystalMat = OreMaterial.LIFE_CRYSTAL.material;
        chunk.getBlock(xRdm, appropriateY.get( (int) (rdm.nextDouble() * appropriateY.size()) ), zRdm)
                .setType(lifeCrystalMat, false);
    }
    void generateCopper(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, SURFACE, 32, OreMaterial.COPPER, 4);
    }
    void generateIron(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, UNDERGROUND, 40, OreMaterial.IRON, 4);
    }
    void generateSilver(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, CAVERN, 48, OreMaterial.SILVER, 4);
    }
    void generateGold(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, DEEP_CAVERN, 64, OreMaterial.GOLD, 4);
    }
    void generateMeteorite(World wld, Random rdm, Chunk chunk) {
        if (yOffset != 0) return; // only surface world get this ore
        if (rdm.nextDouble() < 0.001) {
            int xCenter = (chunk.getX() << 4) + 5 + (int) (rdm.nextDouble() * 6),
                    zCenter = (chunk.getZ() << 4) + 5 + (int) (rdm.nextDouble() * 6);
            int height = wld.getHighestBlockYAt(xCenter, zCenter);
            if (height < OverworldChunkGenerator.LAND_HEIGHT || height > OverworldChunkGenerator.LAND_HEIGHT + 20) return;
            Material oreMat = OreMaterial.METEORITE.material;
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
                        if (blk.getType().isSolid()) blk.setType(distSqr < 64 ? Material.AIR : oreMat, false);
                    }
                    for (int ySet = height; true; ySet --) {
                        double distSqr = (ySet - height) * (ySet - height) + horDistSqr;
                        if (distSqr > 100) break;
                        Block blk = wld.getBlockAt(xSet, ySet, zSet);
                        if (blk.getType().isSolid()) blk.setType(distSqr < 64 ? Material.AIR : oreMat, false);
                    }
                }
            }
        }
    }
    void generateCobalt(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, UNDERGROUND, 48, OreMaterial.COBALT, 4);
    }
    void generateMythril(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, CAVERN, 64, OreMaterial.MYTHRIL, 5);
    }
    void generateAdamantite(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, DEEP_CAVERN, 72, OreMaterial.ADAMANTITE, 5);
    }
    void generateChlorophyte(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.MUTATED_JUNGLE)
            generateGenericOre(wld, rdm, chunk, CAVERN, 128, OreMaterial.CHLOROPHYTE, 5);
    }
    // Calamity
    // pre-hardmode
    void generateSeaPrism(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.MUTATED_DESERT)
            generateGenericOre(wld, rdm, chunk, UNDERGROUND, 72, OreMaterial.SEA_PRISM, 5);
    }
    void generateAerialite(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, CAVERN, 80, OreMaterial.AERIALITE, 6);
    }
    // hardmode
    // charred ore only generates in the hell level.
    void generateCryonic(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.MUTATED_TAIGA_COLD)
            generateGenericOre(wld, rdm, chunk, UNDERGROUND, 96, OreMaterial.CRYONIC, 6);
    }
    void generatePerennial(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.MUTATED_JUNGLE)
            generateGenericOre(wld, rdm, chunk, UNDERGROUND, 96, OreMaterial.PERENNIAL, 6);
    }
    void generateScoria(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.DEEP_OCEAN)
            generateGenericOre(wld, rdm, chunk, ABYSS_MID, 80, OreMaterial.SCORIA, 5);
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
            Material oreMat = OreMaterial.EXODIUM.material;
            // set spherical cluster of ore
            for (int xOffset = -10; xOffset <= 10; xOffset ++) {
                for (int zOffset = -10; zOffset <= 10; zOffset ++) {
                    int horDistSqr = (xOffset * xOffset) + (zOffset * zOffset);
                    if (horDistSqr > 100) continue;
                    int xSet = xCenter + xOffset, zSet = zCenter + zOffset;
                    for (int ySet = height - 10; ySet <= height + 10; ySet ++) {
                        if ((ySet - height) * (ySet - height) + horDistSqr > 100) continue;
                        wld.getBlockAt(xSet, ySet, zSet).setType(oreMat, false);
                    }
                }
            }
        }
    }
    void generateUelibloom(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.MUTATED_JUNGLE)
            generateGenericOre(wld, rdm, chunk, CAVERN, 160, OreMaterial.UELIBLOOM, 6);
    }
    void generateAuric(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, DEEP_CAVERN, 256, OreMaterial.AURIC, 8);
    }
    void generateHellstone(World wld, Random rdm, Chunk chunk) {
        generateGenericOre(wld, rdm, chunk, 75, 24, OreMaterial.HELLSTONE, 4);
    }
    void generateCharred(World wld, Random rdm, Chunk chunk) {
        if (wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16) == Biome.SAVANNA)
            generateGenericOre(wld, rdm, chunk, 60, 32, OreMaterial.CHARRED, 5);
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
