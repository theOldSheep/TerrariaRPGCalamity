package terraria.entity.monster;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import terraria.util.EntityHelper;

import java.util.HashMap;

public class MonsterSlime extends EntitySlime {
    protected HashMap<String, Object> extraVariables = new HashMap<>();
    Player target;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public MonsterSlime(World world) {
        super(world);
        die();
    }
    protected void initExtraInformation() {
        getAttributeInstance(GenericAttributes.FOLLOW_RANGE).setValue(444);
        int size = 1;
        float health = 444;
        HashMap<String, Double> attrMap = new HashMap<>();
        setSize(size, false);
        getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
        setHealth(health);
        EntityHelper.setMetadata(getBukkitEntity(), "attrMap", attrMap);
    }
    public MonsterSlime(org.bukkit.entity.Player target, String type) {
        super(((CraftWorld) target.getWorld()).getHandle());
        this.target = target;
        // does not get removed if far away.
        this.persistent = true;
        // set location
        Location spawnLoc = target.getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        setHeadRotation(0);
        // add to world
        ((CraftWorld) target.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // attributes etc.
        initExtraInformation();
        setCustomName(type);
        setCustomNameVisible(true);
    }
    @Override
    public void die() {
        super.die();
    }
}
