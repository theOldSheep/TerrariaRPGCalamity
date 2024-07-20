package terraria.entity.boss;

import org.bukkit.entity.Projectile;

import java.util.ArrayList;
import java.util.HashSet;

public class BossProjectilesManager {
    HashSet<Projectile> projectiles = new HashSet<>();

    public void handleProjectile(Projectile projectile) {
        projectiles.add(projectile);
    }
    public void dropOutdated() {
        ArrayList<Projectile> toDrop = new ArrayList<>();
        for (Projectile proj : projectiles) {
            if (proj.isDead())
                toDrop.add(proj);
        }
        for (Projectile projToRemove : toDrop) {
            projectiles.remove(projToRemove);
        }
    }
    public void killAll() {
        dropOutdated();
        for (Projectile proj : projectiles) {
            proj.remove();
        }
        projectiles.clear();
    }
}
