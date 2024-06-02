package terraria.entity.boss.postMoonLord.yharon;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Slime;

import java.util.ArrayList;
import java.util.List;








public class TornadoSlime {
    private Location center;
    private List<Slime> slimes;

    public TornadoSlime(Location center) {
        this.center = center;
        this.slimes = new ArrayList<>();
        spawn();
        for (Slime slime : slimes) {
            slime.setGravity(false);
            slime.setAI(false);
        }
    }

    public void spawn() {
        for (int i = 0; i < 20; i++) {
            Slime slime = (Slime) center.getWorld().spawnEntity(center, EntityType.SLIME);
            slime.setCustomName("TornadoSlime");
            slimes.add(slime);
        }
    }

    public void update() {
        for (int i = 0; i < slimes.size(); i++) {
            Slime slime = slimes.get(i);
            double angle = Math.toRadians(i * 30 - slime.getTicksLived());
            double heightOffset = i * 0.5;
            double radiusOffset = i * 0.05;
            double radius = radiusOffset;

            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            double y = center.getY() + heightOffset;

            slime.teleport(new Location(center.getWorld(), x, y, z));
            slime.setSize(Math.min(i / 2 + 1, 5));
        }
    }
}