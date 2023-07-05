package terraria.entity.others;

import net.minecraft.server.v1_12_R1.EntitySilverfish;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import terraria.entity.monster.MonsterHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class TerrariaCritter extends EntitySilverfish {
    Player target;
    String monsterType, monsterVariant;
    int indexAI = 0;
    double defaultSpeed = 0.2;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public TerrariaCritter(World world) {
        super(world);
        die();
    }
    public TerrariaCritter(Player target, String type, Location spawnLoc) {
        super(((CraftWorld) target.getWorld()).getHandle());
        this.target = target;
        // does not get removed if far away.
        this.persistent = true;
        // set location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        setHeadRotation(0);
        // add to world
        ((CraftWorld) target.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
    }

    @Override
    public void B_() {
        super.B_();
        // additional movement
    }
}
