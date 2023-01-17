package terraria.entity.monster;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.HashMap;
import java.util.Map;

public class MonsterSlime extends EntitySlime {
    protected HashMap<String, Object> extraVariables = new HashMap<>();
    Player target;
    String monsterType, monsterVariant;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public MonsterSlime(World world) {
        super(world);
        die();
    }
    protected void initExtraInformation(Player ply, String monsterProgressRequired) {
        MonsterHelper.initMonsterInfo(ply, monsterProgressRequired, this, monsterType, monsterVariant);
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
        // get the variant to use
        String progressRequired;
        {
            Map.Entry<String, String> temp = MonsterHelper.getMonsterVariantAndProgress(target, type);
            this.monsterType = type;
            this.monsterVariant = temp.getKey();
            progressRequired = temp.getValue();
        }
        // add to world
        ((CraftWorld) target.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // attributes etc.
        initExtraInformation(target, progressRequired);
    }
    @Override
    public void die() {
        super.die();
    }
    @Override
    public void B_() {
        super.B_();

    }
}
