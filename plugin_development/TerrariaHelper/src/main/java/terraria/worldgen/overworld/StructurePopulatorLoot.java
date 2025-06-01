package terraria.worldgen.overworld;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.craftbukkit.v1_12_R1.block.CraftChest;
import org.bukkit.generator.BlockPopulator;
import terraria.TerrariaHelper;
import terraria.util.ItemUseHelper;
import terraria.util.WorldHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static terraria.worldgen.overworld.StructurePopulatorBiomeCenter.DATA_NONE;

public class StructurePopulatorLoot extends BlockPopulator {
    int SPAWN_POINT_PROTECTION_CHUNK_RADIUS = 8;
    double SURFACE_CHEST_RATE = TerrariaHelper.optimizationConfig.getDouble("worldGen.params.surfaceChestRate", 0.02);
    double CAVERN_CHEST_RATE = TerrariaHelper.optimizationConfig.getDouble("worldGen.params.cavernChestRate", 0.05);
    boolean isSurface;

    static final Set<WorldHelper.BiomeType> BIOMES_AVAILABLE;

    static {
        BIOMES_AVAILABLE = new HashSet<>();
        BIOMES_AVAILABLE.add(WorldHelper.BiomeType.NORMAL);
        BIOMES_AVAILABLE.add(WorldHelper.BiomeType.TUNDRA);
        BIOMES_AVAILABLE.add(WorldHelper.BiomeType.DESERT);
    }

    public StructurePopulatorLoot(boolean surfaceOrUnderground) {
        super();
        this.isSurface = surfaceOrUnderground;
    }

    @Override
    public void populate(World wld, Random rdm, Chunk chunk) {
        int chunkX = chunk.getX(), chunkZ = chunk.getZ();
        if (Math.abs(chunkX) <= SPAWN_POINT_PROTECTION_CHUNK_RADIUS || Math.abs(chunkZ) <= SPAWN_POINT_PROTECTION_CHUNK_RADIUS)
            return;

        if (isSurface) {
            if (Math.random() < SURFACE_CHEST_RATE) {
                generateSurfaceChest(wld,
                        (chunkX << 4) + (int) (Math.random() * 16),
                        (chunkZ << 4) + (int) (Math.random() * 16));
            }
        } else {
            if (Math.random() < CAVERN_CHEST_RATE) {
                generateCavernChest(wld,
                        (chunkX << 4) + 8,
                        (chunkZ << 4) + 8);
            }
        }
    }

    // surface chests
    protected void generateSurfaceChest(World wld, int blockX, int blockZ) {
        // get proper block to spawn at
        int blockY = StructurePopulatorBiomeCenter.getSurfaceY(wld, blockX, blockZ, true) + 1;
        Block block = wld.getBlockAt(blockX, blockY, blockZ);
        if (block.getType() != Material.AIR) return;
        WorldHelper.BiomeType biome = WorldHelper.BiomeType.getBiome(new Location(wld, blockX, blockY, blockZ), false);
        if (!BIOMES_AVAILABLE.contains(biome)) return;

        // set block & place items
        placeChest(block, "世界生成.地表箱" + biome.displayName, "世界生成.地表箱");
    }

    // cavern chests
    protected void generateCavernChest(World wld, int blockX, int blockZ) {
        Location loc = StructurePopulatorBiomeCenter.getCavernOpening(wld, blockX, blockZ,
                2, 3,
                25, 225, 5);
        blockX = loc.getBlockX();
        int blockY = loc.getBlockY();
        blockZ = loc.getBlockZ();

        Block block = wld.getBlockAt(blockX, blockY, blockZ);
        if (block.getType() != Material.AIR) return;
        WorldHelper.BiomeType biome = WorldHelper.BiomeType.getBiome(new Location(wld, blockX, blockY, blockZ), false);
        if (!BIOMES_AVAILABLE.contains(biome)) return;

        // set platform & pillar
        StructureInfo struct = new StructureInfo(Material.WOOD, Material.AIR, (byte) 0, (byte) 0);
        struct.planRegisterBlockPlane(wld, blockX, blockY - 1, blockZ, 3, true, true);
        Block pillar = block.getRelative(0, -2, 0);
        while (pillar.getY() >= 1 && !pillar.getType().isSolid()) {
            struct.planRegisterSingleBlock(pillar, true, true);
            pillar = pillar.getRelative(BlockFace.DOWN);
        }
        struct.performOperations();

        // set block & place items
        placeChest(block, "世界生成.洞穴箱" + biome.displayName, "世界生成.洞穴箱");
    }

    protected void placeChest(Block block, String... lootCrateNames) {
        try {
            block.setType(Material.CHEST);
            block.setData((byte) (int) (Math.random() * 4));

            Chest chest = (Chest) block.getState();
            for (String cratePath: lootCrateNames) {
                ItemUseHelper.getCrateItems(null, cratePath)
                        .forEach((loot) -> chest.getBlockInventory().addItem(loot));
            }
        } catch (Exception ignored) {
        }
    }
}