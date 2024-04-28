package terraria.worldgen.overworld;

import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Arrays;
import java.util.Random;

public class NoiseGeneratorTest implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length >= 1) {
                double toFind = Double.parseDouble(args[0]);
                player.sendMessage("The noise you want to find is: " + toFind);
                int xStart = new Random().nextInt(100000);
                int zStart = new Random().nextInt(100000);
                for (int i = 0; i > -200000; i -= 10) {
                    for (int j = 0; j > -200000; j -= 10) {
                        int blockX = xStart + i, blockZ = zStart + j;
                        double noise;
                        switch (args[1]) {
                            case "-2":
                                noise = OverworldChunkGenerator.riverGenerator.noise(blockX, blockZ,
                                        2, 0.5, false);
                                break;
                            case "-1":
                                noise = OverworldChunkGenerator.lakeGenerator.noise(blockX, blockZ,
                                        2, 0.5, false);
                                break;
                            default:
                                noise = OverworldBiomeGenerator.getBiomeFeature(blockX, blockZ).features[Integer.parseInt(args[1])];
                        }
                        Location loc = new Location(player.getWorld(), blockX, 150, blockZ);
                        if (Math.abs(noise - toFind) < 0.001 &&
                                (args[1].equals("0") ||
                                        (loc.getBlock().getBiome() != Biome.OCEAN && loc.getBlock().getBiome() != Biome.FROZEN_OCEAN)) ) {
                            player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN);
                            player.sendMessage("Found!" + toFind + ", actual noise: " + noise);
                            return true;
                        }
                    }
                }
            }
            else {
                player.sendMessage("Did not find the noise you wanted to go to :(");
                int blockX = player.getLocation().getBlockX(), blockZ = player.getLocation().getBlockZ();
                player.sendMessage("river: " +
                        OverworldChunkGenerator.riverGenerator.noise(blockX, blockZ, 2, 0.5, false));
                player.sendMessage("lake: " +
                        OverworldChunkGenerator.lakeGenerator.noise(blockX, blockZ, 2, 0.5, false));
                player.sendMessage("features: " +
                        Arrays.toString(OverworldBiomeGenerator.getBiomeFeature(blockX, blockZ).features));
            }
        }

        return false;
    }
}
