package terraria.entity;

import net.minecraft.server.v1_12_R1.EntityPotion;
import net.minecraft.server.v1_12_R1.ItemStack;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.util.Vector;

public class TerrariaProjectile extends EntityPotion {
    public String projectileType;
    public int penetration, bounce;
    public boolean bouncePenetrationBounded, thruWall, slideOffWall;
    private boolean getSlideOffWall() {
        switch (projectileType) {
            case "月光束":
            case "月光弓":
                return true;
            default:
                return false;
        }
    }
    public static ItemStack generateItemStack(String projectileType) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.SPLASH_POTION);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
        meta.setDisplayName(projectileType);
        meta.setColor(org.bukkit.Color.fromRGB(255, 255, 255));
        item.setItemMeta(meta);
        return CraftItemStack.asNMSCopy(item);
    }
    public TerrariaProjectile(org.bukkit.Location loc, ItemStack projectileItem, Vector velocity, String projectileType,
                              int bounce, int penetration, boolean bouncePenetrationBounded, boolean thruWall) {
        super(((CraftWorld) loc.getWorld()).getHandle(), loc.getX(), loc.getY(), loc.getZ(), projectileItem);
        this.motX = velocity.getX();
        this.motY = velocity.getY();
        this.motZ = velocity.getZ();
        this.projectileType = projectileType;
        this.bounce = bounce;
        this.penetration = penetration;
        this.bouncePenetrationBounded = bouncePenetrationBounded;
        this.thruWall = thruWall;
        this.slideOffWall = getSlideOffWall();
    }

    // override functions
}
