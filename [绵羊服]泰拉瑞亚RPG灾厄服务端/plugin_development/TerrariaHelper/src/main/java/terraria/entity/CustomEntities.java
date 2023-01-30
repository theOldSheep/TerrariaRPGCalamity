package terraria.entity;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.entity.EntityType;
import terraria.entity.minion.MinionCaveSpider;
import terraria.entity.minion.MinionHusk;
import terraria.entity.minion.MinionSlime;
import terraria.entity.monster.MonsterHusk;
import terraria.entity.monster.MonsterSlime;
import terraria.entity.monster.MonsterZombie;

/*
class from https://www.spigotmc.org/threads/nms-tutorials-2-custom-nms-entities-1-11.205192/
 */
public enum CustomEntities {

    MINION_SLIME       ("MinionSlime",      55, EntityType.SLIME,           EntitySlime.class,      MinionSlime.class),
    MINION_HUSK        ("MinionHusk",       23, EntityType.HUSK,            EntityZombieHusk.class, MinionHusk .class),
    MINION_CAVE_SPIDER ("MinionCaveSpider", 59, EntityType.CAVE_SPIDER,     EntityCaveSpider.class, MinionCaveSpider.class),
    MONSTER_SLIME      ("MonsterSlime",     55, EntityType.SLIME,           EntitySlime.class,      MonsterSlime.class),
    MONSTER_ZOMBIE     ("MonsterZombie",    54, EntityType.ZOMBIE,          EntityZombie.class,     MonsterZombie.class),
    MONSTER_HUSK       ("MonsterHusk",      23, EntityType.HUSK,            EntityZombieHusk.class, MonsterHusk.class);

    private String name;
    private int id;
    private EntityType entityType;
    private Class<? extends Entity> nmsClass;
    private Class<? extends Entity> customClass;
    private MinecraftKey key;
    private MinecraftKey oldKey;

    private CustomEntities(String name, int id, EntityType entityType, Class<? extends Entity> nmsClass, Class<? extends Entity> customClass) {
        this.name = name;
        this.id = id;
        this.entityType = entityType;
        this.nmsClass = nmsClass;
        this.customClass = customClass;
        this.key = new MinecraftKey(name);
        this.oldKey = EntityTypes.b.b(nmsClass);
    }

    public static void registerEntities() { for (CustomEntities ce : CustomEntities.values()) ce.register(); }
    public static void unregisterEntities() { for (CustomEntities ce : CustomEntities.values()) ce.unregister(); }

    private void register() {
        EntityTypes.d.add(key);
        EntityTypes.b.a(id, key, customClass);
    }

    private void unregister() {
        EntityTypes.d.remove(key);
        EntityTypes.b.a(id, oldKey, nmsClass);
    }

    public String getName() {
        return name;
    }

    public int getID() {
        return id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public Class<?> getCustomClass() {
        return customClass;
    }
}