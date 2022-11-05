package terraria.worldgen.overworld;

import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
                        if (args.length == 1)
                            noise = OverworldChunkGenerator.terrainGenerator.noise(blockX, blockZ, 0.5, 0.5, false);
                        else
                            switch (args[1]) {
                                case "1":
                                    noise = OverworldChunkGenerator.riverGenerator.noise(blockX, blockZ, 0.5, 0.5, false);
                                    break;
                                case "2":
                                    noise = OverworldChunkGenerator.lakeGenerator.noise(blockX, blockZ, 0.5, 0.5, false);
                                    break;
                                default:
                                    noise = OverworldChunkGenerator.terrainGenerator.noise(blockX, blockZ, 0.5, 0.5, false);
                            }
                        if (Math.abs(noise - toFind) < 0.001) {
                            player.teleport(new Location(player.getWorld(), blockX, 150, blockZ));
                            player.sendMessage("Found!" + toFind + ", actual noise: " + noise);
                            return true;
                        }
                    }
                }
            }
            player.sendMessage("Did not find the noise you wanted to go to :(");
        }

        return false;
    }
}
