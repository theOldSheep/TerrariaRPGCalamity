package terraria.util;

import com.earth2me.essentials.api.Economy;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.libs.jline.internal.Nullable;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.boss.event.celestialPillar.CelestialPillar;
import terraria.entity.projectile.HitEntityInfo;
import terraria.gameplay.EventAndTime;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Level;

public class PlayerHelper {
    public enum GameProgress {
        PRE_WALL_OF_FLESH, PRE_PLANTERA, PRE_MOON_LORD, PRE_PROFANED_GODDESS, POST_PROFANED_GODDESS;
    }
    // constants
    private static HashMap<String, Double> defaultPlayerAttrMap = new HashMap<>(60);
    private static HashSet<String> defaultPlayerEffectInflict = new HashSet<>(8);
    public static final int playerExtraInventorySize = 54;
    static {
        // init default player attribute map
        defaultPlayerAttrMap.put("armorPenetration", 0d);
        defaultPlayerAttrMap.put("ammoConsumptionRate", 1d);
        defaultPlayerAttrMap.put("arrowConsumptionRate", 1d);
        defaultPlayerAttrMap.put("bounce", 0d);
        defaultPlayerAttrMap.put("crit", 0d);
        defaultPlayerAttrMap.put("critDamage", 100d);
        defaultPlayerAttrMap.put("critMagic", 0d);
        defaultPlayerAttrMap.put("critMelee", 0d);
        defaultPlayerAttrMap.put("critRanged", 0d);
        defaultPlayerAttrMap.put("critTrueMelee", 0d);
        defaultPlayerAttrMap.put("damage", 0d);
        defaultPlayerAttrMap.put("damageArrowMulti", 1d);
        defaultPlayerAttrMap.put("damageBulletMulti", 1d);
        defaultPlayerAttrMap.put("damageMagicMulti", 1d);
        defaultPlayerAttrMap.put("damageMeleeMulti", 1d);
        defaultPlayerAttrMap.put("damageMulti", 1d);
        defaultPlayerAttrMap.put("damageRangedMulti", 1d);
        defaultPlayerAttrMap.put("damageRocketMulti", 1d);
        defaultPlayerAttrMap.put("damageSummonMulti", 1d);
        defaultPlayerAttrMap.put("damageTakenMulti", 1d);
        defaultPlayerAttrMap.put("damageContactTakenMulti", 1d);
        defaultPlayerAttrMap.put("damageTrueMeleeMulti", 1d);
        defaultPlayerAttrMap.put("defence", 0d);
        defaultPlayerAttrMap.put("defenceMulti", 1d);
        defaultPlayerAttrMap.put("fishingHooks", 1d);
        defaultPlayerAttrMap.put("fishingPower", 0d);
        defaultPlayerAttrMap.put("flightTimeMulti", 1d);
        defaultPlayerAttrMap.put("healthMulti", 1d);
        defaultPlayerAttrMap.put("invulnerabilityTick", 10d);
        defaultPlayerAttrMap.put("knockback", 0d);
        defaultPlayerAttrMap.put("knockbackResistance", 0d);
        defaultPlayerAttrMap.put("knockbackMeleeMulti", 1d);
        defaultPlayerAttrMap.put("knockbackMulti", 1d);
        defaultPlayerAttrMap.put("manaRegen", 0d);
        defaultPlayerAttrMap.put("manaRegenMulti", 1d);
        defaultPlayerAttrMap.put("manaUse", 0d);
        defaultPlayerAttrMap.put("manaUseMulti", 1d);
        defaultPlayerAttrMap.put("maxHealth", 200d);
        defaultPlayerAttrMap.put("maxHealthMulti", 1d);
        defaultPlayerAttrMap.put("maxMana", 20d);
        defaultPlayerAttrMap.put("meleeReachMulti", 1d);
        defaultPlayerAttrMap.put("minionDamagePenaltyMulti", 0.5d);
        defaultPlayerAttrMap.put("minionLimit", 1d);
        defaultPlayerAttrMap.put("mobLimit", 15d);
        defaultPlayerAttrMap.put("mobSpawnRate", 0.05d);
        defaultPlayerAttrMap.put("mobSpawnRateMulti", 1d);
        defaultPlayerAttrMap.put("penetration", 0d);
        defaultPlayerAttrMap.put("powerPickaxe", 0d);
        defaultPlayerAttrMap.put("projectileSpeed", 0d);
        defaultPlayerAttrMap.put("projectileSpeedMulti", 1d);
        defaultPlayerAttrMap.put("projectileSpeedArrowMulti", 1d);
        defaultPlayerAttrMap.put("reachExtra", 0d);
        defaultPlayerAttrMap.put("regen", 0d);
        defaultPlayerAttrMap.put("regenMulti", 1d);
        defaultPlayerAttrMap.put("sentryLimit", 2d);
        defaultPlayerAttrMap.put("speed", 0.2d);
        defaultPlayerAttrMap.put("speedMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMagicMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMeleeMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMiningMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedRangedMulti", 1d);
        defaultPlayerAttrMap.put("useTime", 0d);
        // init default player buff inflict map
        defaultPlayerEffectInflict.add("buffInflict");
        defaultPlayerEffectInflict.add("buffInflictMagic");
        defaultPlayerEffectInflict.add("buffInflictMelee");
        defaultPlayerEffectInflict.add("buffInflictRanged");
        defaultPlayerEffectInflict.add("buffInflictSummon");
        defaultPlayerEffectInflict.add("buffInflictTrueMelee");
    }
    // getters
    public static HashMap<String, ArrayList<String>> getDefaultPlayerEffectInflict() {
        HashMap<String, ArrayList<String>> result = new HashMap<>(defaultPlayerEffectInflict.size() * 2);
        for (String key : defaultPlayerEffectInflict) result.put(key, new ArrayList<>(6));
        return result;
    }
    public static HashMap<String, ArrayList<String>> getPlayerEffectInflict(Metadatable player) {
        try {
            return (HashMap<String, ArrayList<String>>) EntityHelper.getMetadata(player, EntityHelper.MetadataName.PLAYER_BUFF_INFLICT).value();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
    public static HashMap<String, Double> getDefaultPlayerAttributes() {
        return (HashMap<String, Double>) defaultPlayerAttrMap.clone();
    }
    public static double getMoney(Player ply) {
        try {
            BigDecimal money = Economy.getMoneyExact(ply.getUniqueId());
            return money == null ? 0d : money.doubleValue();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] getMoney ");
            e.printStackTrace();
            return 0d;
        }
    }
    public static Inventory getInventory(Player ply, String key) {
        return ((HashMap<String, Inventory>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_INVENTORIES).value())
                .get(key);
    }
    public static String getArmorSet(Player ply) {
        return EntityHelper.getMetadata(ply, EntityHelper.MetadataName.ARMOR_SET).asString();
    }
    public static int getAccessoryAmount(Player ply) {
        return getPlayerDataFile(ply).getInt("stats.maxAccessories", 6);
    }
    public static HashSet<String> getAccessories(Entity entity) {
        try {
            MetadataValue value = EntityHelper.getMetadata(entity, EntityHelper.MetadataName.ACCESSORIES);
            return value != null ? (HashSet<String>) (value.value()) : new HashSet<>();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Entity Helper] getAccessories", e);
        }
        return new HashSet<>();
    }
    public static ItemStack getFirstItem(Player ply, Predicate<ItemStack> itemPredicate, boolean testVoidBag) {
        // in the player's inventory
        Inventory plyInv = ply.getInventory();
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem != null && itemPredicate.test(currItem)) {
                return currItem;
            }
        }
        // in the player's void bag
        if (testVoidBag && PlayerHelper.hasVoidBag(ply)) {
            Inventory voidBagInv = PlayerHelper.getInventory(ply, "voidBag");
            for (ItemStack currItem : voidBagInv.getContents()) {
                if (currItem != null && itemPredicate.test(currItem)) {
                    return currItem;
                }
            }
        }
        // item not found
        return null;
    }
    public static ArrayList<ItemStack> getItems(Player ply, Predicate<ItemStack> itemPredicate, boolean testVoidBag) {
        // in the player's inventory
        ArrayList<ItemStack> result = new ArrayList<>();
        Inventory plyInv = ply.getInventory();
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem != null && itemPredicate.test(currItem)) {
                result.add(currItem);
            }
        }
        // in the player's void bag
        if (testVoidBag && PlayerHelper.hasVoidBag(ply)) {
            Inventory voidBagInv = PlayerHelper.getInventory(ply, "voidBag");
            for (ItemStack currItem : voidBagInv.getContents()) {
                if (currItem != null && itemPredicate.test(currItem)) {
                    result.add(currItem);
                }
            }
        }
        // return all found items
        return result;
    }
    public static Location getSpawnLocation(Player ply) {
        if (ply.getBedSpawnLocation() != null) {
            return ply.getBedSpawnLocation();
        }
        return Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE)
                .getHighestBlockAt(0, 0).getLocation().add(0, 1, 0);
    }
    public static GameProgress getGameProgress(Player player) {
        ConfigurationSection bossDefeatedSection = getPlayerDataFile(player).getConfigurationSection("bossDefeated");
        if (bossDefeatedSection == null)
            return GameProgress.PRE_WALL_OF_FLESH;
        if (bossDefeatedSection.getBoolean("亵渎天神", false))
            return GameProgress.POST_PROFANED_GODDESS;
        if (bossDefeatedSection.getBoolean("月球领主", false))
            return GameProgress.PRE_PROFANED_GODDESS;
        if (bossDefeatedSection.getBoolean("世纪之花", false))
            return GameProgress.PRE_MOON_LORD;
        if (bossDefeatedSection.getBoolean("血肉之墙", false))
            return GameProgress.PRE_PLANTERA;
        return GameProgress.PRE_WALL_OF_FLESH;
    }
    public static GameProgress getGameProgress(String bossProgress) {
        switch (bossProgress) {
            case "血肉之墙":
            case "史莱姆皇后":
            case "极地之灵":
            case "双子魔眼":
            case "硫磺火元素":
            case "毁灭者":
            case "渊海灾虫":
            case "机械骷髅王":
            case "灾厄之眼":
                return GameProgress.PRE_PLANTERA;
            case "世纪之花":
            case "阿娜希塔和利维坦":
            case "白金星舰":
            case "石巨人":
            case "瘟疫使者歌莉娅":
            case "光之女皇":
            case "猪鲨公爵":
            case "毁灭魔像":
            case "拜月教邪教徒":
            case "星神游龙":
                return GameProgress.PRE_MOON_LORD;
            case "月球领主":
            case "亵渎守卫":
            case "痴愚金龙":
            case "噬魂幽花":
                return GameProgress.PRE_PROFANED_GODDESS;
            case "亵渎天神":
            case "硫海遗爵":
            case "神明吞噬者":
            case "丛林龙，犽戎":
            case "星流巨械":
            case "至尊灾厄":
                return GameProgress.POST_PROFANED_GODDESS;
        }
        return GameProgress.PRE_WALL_OF_FLESH;
    }
    public static double getPlayerMoveYaw(Player ply) {
        HashSet<String> allKeysPressed = (HashSet<String>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_KEYS_PRESSED).value();
        String movementKeyDown = "";
        boolean fwd = allKeysPressed.contains("W"),
                rev = allKeysPressed.contains("S"),
                lft = allKeysPressed.contains("A"),
                rt = allKeysPressed.contains("D");
        if (fwd && !rev)
            movementKeyDown += "W";
        else if (rev && !fwd)
            movementKeyDown += "S";
        if (lft && !rt)
            movementKeyDown += "A";
        else if (rt && !lft)
            movementKeyDown += "D";
        return getPlayerMoveYaw(ply, movementKeyDown);
    }
    public static double getPlayerMoveYaw(Player ply, String movementKeyDown) {
        double horizontalMoveYaw = ((CraftPlayer) ply).getHandle().yaw;
        switch (movementKeyDown) {
            case "":
                horizontalMoveYaw = 1e9;
                break;
            case "A":
                horizontalMoveYaw -= 90;
                break;
            case "S":
                horizontalMoveYaw += 180;
                break;
            case "D":
                horizontalMoveYaw += 90;
                break;
            case "WA":
                horizontalMoveYaw -= 45;
                break;
            case "WD":
                horizontalMoveYaw += 45;
                break;
            case "SA":
                horizontalMoveYaw -= 135;
                break;
            case "SD":
                horizontalMoveYaw += 135;
                break;
        }
        return horizontalMoveYaw;
    }
    public static int getPlayerHealthTier(Player ply) {
        MetadataValue metadataValue = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_HEALTH_TIER);
        if (metadataValue != null)
            return metadataValue.asInt();
        ConfigurationSection playerDataFile = getPlayerDataFile(ply);
        return playerDataFile.getInt("stats.healthTier", 5);
    }
    public static int getPlayerManaTier(Player ply) {
        MetadataValue metadataValue = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_TIER);
        if (metadataValue != null)
            return metadataValue.asInt();
        ConfigurationSection playerDataFile = getPlayerDataFile(ply);
        return playerDataFile.getInt("stats.manaTier", 1);
    }
    public static int getMaxHealthByTier(int tier) {
        if (tier < 21) return tier * 40;
        if (tier < 41) return 800 + (tier - 20) * 10;
        switch (tier) {
            case 41:
                return 1050;
            case 42:
                return 1100;
            case 43:
                return 1150;
            default:
                return 1200;
        }
    }
    public static int getMaxManaByTier(int tier) {
        if (tier < 11) return tier * 20;
        switch (tier) {
            case 11:
                return 250;
            case 12:
                return 300;
            default:
                return 350;
        }
    }
    public static String getPlayerDataFilePath(Player ply) {
        return TerrariaHelper.Constants.DATA_PLAYER_FOLDER_DIR + ply.getName() + ".yml";
    }
    public static YmlHelper.YmlSection getPlayerDataFile(Player ply) {
        String filePath = getPlayerDataFilePath(ply);
        return YmlHelper.getFile(filePath);
    }
    public static boolean hasDefeated(Player player, String progressToCheck) {
        if (progressToCheck.length() == 0)
            return true;
        switch (progressToCheck) {
            case "机械一王":
            case "机械二王":
            case "机械三王":
                int amountDefeated = 0;
                if (hasDefeated(player, BossHelper.BossType.THE_TWINS.msgName)) amountDefeated ++;
                if (hasDefeated(player, BossHelper.BossType.SKELETRON_PRIME.msgName)) amountDefeated ++;
                if (hasDefeated(player, BossHelper.BossType.THE_DESTROYER.msgName)) amountDefeated ++;
                switch (progressToCheck) {
                    case "机械一王":
                        return amountDefeated >= 1;
                    case "机械二王":
                        return amountDefeated >= 2;
                    case "机械三王":
                        return amountDefeated >= 3;
                }
        }
        YmlHelper.YmlSection fileSection = getPlayerDataFile(player);
        return fileSection.getBoolean("bossDefeated." + progressToCheck, false);
    }
    public static void setDefeated(Player player, String progressToAdd, boolean defeated) {
        YmlHelper.YmlSection fileSection = getPlayerDataFile(player);
        fileSection.set("bossDefeated." + progressToAdd, defeated);
    }
    public static boolean isProperlyPlaying(Player player) {
        if (!player.isOnline()) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        return !player.getScoreboardTags().contains("unauthorized");
    }
    public static boolean hasVoidBag(Player ply) {
        ItemStack voidBag = ItemHelper.getItemFromDescription("虚空袋", false, new ItemStack(Material.BEDROCK));
        return ply.getInventory().contains(voidBag);
    }
    public static boolean canHoldAny(Player ply, ItemStack item) {
        if (item == null) return true;
        String itemType = ItemHelper.splitItemName(item)[1];
        // these items get consumed once picked up
        switch (itemType) {
            case "铜币":
            case "银币":
            case "金币":
            case "铂金币":
            case "心":
            case "星":
            case "生命强化焰":
            case "伤害强化焰":
            case "魔力强化焰":
                return true;
        }
        // check airs first, they are a lot less performance costly.
        // backpack
        Inventory plyInv = ply.getInventory();
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem == null || currItem.getType() == Material.AIR) return true;
        }
        // void bag
        boolean hasVoidBag = hasVoidBag(ply);
        Inventory voidBagInv = getInventory(ply, "voidBag");
        if (hasVoidBag && voidBagInv != null) {
            for (ItemStack currItem : voidBagInv.getContents())
                if (currItem == null || currItem.getType() == Material.AIR) return true;
        }
        // then we check for stacking
        // items that can not stack do not need any additional check.
        int maxStackSize = item.getMaxStackSize();
        if (maxStackSize <= 1) return false;
        // backpack
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem.isSimilar(item) && maxStackSize > currItem.getAmount()) return true;
        }
        // void bag
        if (hasVoidBag && voidBagInv != null) {
            for (ItemStack currItem : voidBagInv.getContents())
                if (currItem.isSimilar(item) && maxStackSize > currItem.getAmount()) return true;
        }
        return false;
    }
    // setters
    public static void setArmorSet(Player ply, String armorSet) {
        try {
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ARMOR_SET, armorSet);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] setArmorSet ", e);
        }
    }
    public static void setMoney(Player ply, double amount) {
        try {
            Economy.setMoney(ply.getUniqueId(), BigDecimal.valueOf(amount));
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] setMoney ", e);
        }
    }
    public static void resetPlayerFlightTime(Player ply) {
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_INDEX, 0);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_PROGRESS, 0);
    }
    // threads
    public static void threadArmorAccessory() {
        // setup projectile attributes
        HashMap<String, Double> attrMapChlorophyte = new HashMap<>(5);
        {
            attrMapChlorophyte.put("damage", 200d);
            attrMapChlorophyte.put("knockback", 0d);
        }
        HashMap<String, Double> attrMapVolatileGelatinJr = new HashMap<>(5);
        {
            attrMapVolatileGelatinJr.put("damage", 20d);
            attrMapVolatileGelatinJr.put("knockback", 0d);
        }
        HashMap<String, Double> attrMapVolatileGelatin = new HashMap<>(5);
        {
            attrMapVolatileGelatin.put("damage", 20d);
            attrMapVolatileGelatin.put("knockback", 0d);
        }
        HashMap<String, Double> attrMapSpore = new HashMap<>(5);
        {
            attrMapSpore.put("damage", 125d);
            attrMapSpore.put("knockback", 0d);
        }
        // every 2 ticks (1/10 second)
        AtomicInteger tickIndex = new AtomicInteger();
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                try {
                    // validate the current player
                    if (PlayerHelper.isProperlyPlaying(ply)) {
                        double health = ply.getHealth(), maxHealth = ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        // handle armor sets
                        switch (getArmorSet(ply)) {
                            case "叶绿魔法套装":
                            case "叶绿射手套装":
                            case "叶绿战士套装":
                                // chlorophyte
                                if (tickIndex.get() % 3 == 0) {
                                    double distanceSqr = 999999;
                                    Entity target = null;
                                    for (Entity e : ply.getWorld().getNearbyEntities(ply.getEyeLocation(), 25, 25, 25)) {
                                        // ignore if is not a valid enemy
                                        if (!(EntityHelper.checkCanDamage(ply, e, true)))
                                            continue;
                                        double distSqr = e.getLocation().distanceSquared(ply.getLocation());
                                        // ignore if is further than current
                                        if (distSqr > distanceSqr)
                                            continue;
                                        distanceSqr = distSqr;
                                        target = e;
                                    }
                                    if (target != null) {
                                        Vector v = MathHelper.getDirection(ply.getEyeLocation(),
                                                target instanceof  LivingEntity ?
                                                        ((LivingEntity) target).getEyeLocation() : target.getLocation(), 1.5);
                                        EntityHelper.spawnProjectile(ply, v, attrMapChlorophyte,
                                                EntityHelper.DamageType.ARROW,"树叶");
                                    }
                                }
                                break;
                        }
                        // handle accessories
                        HashSet<String> accessories = getAccessories(ply);
                        for (String accessory : accessories) {
                            switch (accessory) {
                                case "新手版挥发明胶":
                                case "挥发明胶": {
                                    int shootInterval = accessory.equals("新手版挥发明胶") ? 10 : 5;
                                    if (tickIndex.get() % shootInterval == 0) {
                                        double distanceSqr = 10000d;
                                        Entity target = null;
                                        for (Entity e : ply.getWorld().getNearbyEntities(ply.getEyeLocation(), 12, 12, 12)) {
                                            // is not a valid enemy
                                            if (!(EntityHelper.checkCanDamage(ply, e, true)))
                                                continue;
                                            // has no vision
                                            if (! ply.hasLineOfSight(e))
                                                continue;
                                            // further than current
                                            double distSqr = e.getLocation().distanceSquared(ply.getLocation());
                                            if (distSqr > distanceSqr)
                                                continue;
                                            // record target
                                            distanceSqr = distSqr;
                                            target = e;
                                        }
                                        if (target != null) {
                                            Vector v;
                                            if (target instanceof LivingEntity)
                                                v = ((LivingEntity) target).getEyeLocation().subtract(ply.getEyeLocation()).toVector();
                                            else v = target.getLocation().subtract(ply.getEyeLocation()).toVector();
                                            if (accessory.equals("新手版挥发明胶")) {
                                                v.normalize().multiply(0.5);
                                                EntityHelper.spawnProjectile(ply, v, attrMapVolatileGelatinJr,
                                                        EntityHelper.DamageType.ARROW, "挥发明胶");
                                            } else {
                                                v.normalize().multiply(0.6);
                                                EntityHelper.spawnProjectile(ply, v, attrMapVolatileGelatin,
                                                        EntityHelper.DamageType.ARROW, "挥发明胶");
                                            }
                                        }
                                    }
                                    break;
                                }
                                case "孢子囊": {
                                    if (tickIndex.get() % 3 == 0) {
                                        Vector velocity = MathHelper.randomVector();
                                        velocity.multiply(0.25);
                                        Location spawnLoc = ply.getEyeLocation().add(
                                                Math.random() * 10 - 5, Math.random() * 8 - 3, Math.random() * 10 - 5);
                                        EntityHelper.spawnProjectile(ply, spawnLoc, velocity, attrMapSpore,
                                                EntityHelper.DamageType.MAGIC, "孢子球");
                                    }
                                    break;
                                }
                                // equipments that provide buff
                                case "冰冻海龟壳": {
                                    if (health * 2 < maxHealth)
                                        EntityHelper.applyEffect(ply, "冰障", 20);
                                    break;
                                }
                                case "圣骑士护盾": {
                                    if (health * 4 > maxHealth)
                                        EntityHelper.applyEffect(ply, "圣骑士护盾", 20);
                                    break;
                                }
                                case "冰冻护盾":
                                case "寒霜壁垒":
                                case "神之壁垒": {
                                    if (health * 4 > maxHealth)
                                        EntityHelper.applyEffect(ply, "圣骑士护盾", 20);
                                    if (health * 2 < maxHealth)
                                        EntityHelper.applyEffect(ply, "冰障", 20);
                                    break;
                                }
                                case "血肉图腾": {
                                    EntityHelper.applyEffect(ply, "血肉图腾", 1220);
                                    break;
                                }
                                case "钨钢屏障生成仪": {
                                    if (! EntityHelper.hasEffect(ply, "钨钢屏障"))
                                        EntityHelper.applyEffect(ply, "钨钢屏障", 1600);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] threadArmorAccessory ", e);
                }
            }
            tickIndex.getAndIncrement();
        }, 0, 2);
    }
    public static void threadAttribute() {
        int delay = 6;
        // every 6 ticks (0.3 second)
        // note that players would have their stats updated
        // when they use their weapon after switching hot bar slot or closes an inventory.
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                if (isProperlyPlaying(ply))
                    setupAttribute(ply);
            }
        }, 0, delay);
    }
    public static void threadBackground() {
        // every 10 ticks (1/2 second)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                try {
                    String last = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BACKGROUND).asString();
                    // setup proper background to display
                    String current = "";
                    MetadataValue forceBackground = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_FORCED_BACKGROUND);
                    if (forceBackground == null) {
                        // sky darkens when fighting duke fishron in tier 3
                        if (BossHelper.bossMap.containsKey(BossHelper.BossType.DUKE_FISHRON.msgName)) {
                            LivingEntity fishron = BossHelper.bossMap.get(BossHelper.BossType.DUKE_FISHRON.msgName).get(0);
                            if (fishron.getHealth() / fishron.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() <= 0.4)
                                current = "猪鲨";
                        }
                        if (BossHelper.bossMap.containsKey(BossHelper.BossType.MOON_LORD.msgName))
                            current = "虚空";
                        else if (BossHelper.bossMap.containsKey(BossHelper.BossType.CALAMITAS_CLONE.msgName))
                            current = "血月";
                        else if (BossHelper.bossMap.containsKey(BossHelper.BossType.THE_PLAGUEBRINGER_GOLIATH.msgName) )
                            current = "猪鲨";
                        else if (ply.getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE)) {
                            if (EventAndTime.currentEvent != EventAndTime.Events.NONE)
                                current = EventAndTime.currentEvent.toString();
                            // background for celestial pillars
                            for (CelestialPillar pillar : EventAndTime.pillars.values())
                                if (pillar.getBukkitEntity().getWorld().equals(ply.getWorld()) &&
                                        pillar.getBukkitEntity().getLocation().distanceSquared(ply.getLocation()) <
                                                CelestialPillar.EFFECTED_RADIUS_SQR) {
                                    current = pillar.getName().replace("柱", "");
                                    break;
                                }
                        }
                    } else {
                        current = forceBackground.asString();
                    }
                    // setup client target time
                    int time = 0;
                    switch (current) {
                        case "虚空":
                            time = 22000;
                            break;
                        case "血月":
                            time = 22940;
                            break;
                        case "日耀":
                            time = 23000;
                            break;
                        case "星璇":
                            time = 23080;
                            break;
                        case "星云":
                            time = 23150;
                            break;
                        case "星尘":
                            time = 23300;
                            break;
                        case "猪鲨":
                            time = 23365;
                            break;
                        case "日食":
                            time = 20000;
                            break;
                        // no change in background
                        default:
                            current = "";
                    }
                    if (!last.equals(current)) {
                        if (current.equals("")) {
                            ply.resetPlayerTime();
                            ply.resetPlayerWeather();
                            ply.removePotionEffect(PotionEffectType.NIGHT_VISION);
                        } else {
                            ply.setPlayerTime(time, false);
                            ply.setPlayerWeather(WeatherType.DOWNFALL);
                            ply.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0));
                        }
                        // update last background
                        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BACKGROUND, current);
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] threadBackground ", e);
                }
            }
        }, 0, 10);
    }
    public static void threadBGM() {
        boolean printBGMDebugInfo = false;
        // every 4 ticks (1/5 second)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                try {
                    String last = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM).asString();
                    long startedPlayingTime = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM_TIME).asLong();

                    // setup proper music to play
                    String current = "";
                    long currentTime = Calendar.getInstance().getTimeInMillis();
                    MetadataValue forceBGM = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_FORCED_BGM);
                    // no jukebox
                    if (forceBGM == null) {
                        World plyWorld = ply.getWorld();
                        String worldName = plyWorld.getName();
                        double minBossDistance = 999999;
                        // boss
                        for (String bossName : BossHelper.bossMap.keySet()) {
                            ArrayList<LivingEntity> bossArrayList = BossHelper.bossMap.get(bossName);
                            // if a placeholder is in the boss map, move on
                            if (bossArrayList.isEmpty())
                                continue;
                            // if the boss has multiple phases
                            switch (bossName) {
                                case "阿娜希塔和利维坦":
                                    if (bossArrayList.get(1).isDead()) bossName += "1";
                                    else bossName += "2";
                                    break;
                            }
                            // get the first living component of current boss
                            double currDist = 99999;
                            int index = 0;
                            Entity currBoss = bossArrayList.get(0);
                            while (currBoss.isDead()) {
                                index ++;
                                if (index >= bossArrayList.size()) {
                                    break;
                                }
                                currBoss = bossArrayList.get(index);
                            }
                            // get the nearest boss
                            if (plyWorld.equals(currBoss.getWorld()))
                                currDist = currBoss.getLocation().distanceSquared(ply.getLocation());
                            if (currDist < minBossDistance) {
                                minBossDistance = currDist;
                                current = TerrariaHelper.soundConfig.getString("boss." + bossName, "");
                            }
                        }
                        // events
                        if (current.equals("")) {
                            // other events
                            if (EventAndTime.currentEvent != EventAndTime.Events.NONE)
                                current = TerrariaHelper.soundConfig.getString(
                                        "event." + EventAndTime.currentEvent, "");
                            // celestial pillars
                            if (ply.getLocation().getY() >= 50 && worldName.equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE)) {
                                for (CelestialPillar pillar : EventAndTime.pillars.values())
                                    if (pillar.getBukkitEntity().getWorld().equals(plyWorld) &&
                                            pillar.getBukkitEntity().getLocation().distanceSquared(ply.getLocation()) <
                                                    CelestialPillar.EFFECTED_RADIUS_SQR) {
                                        current = "lunar_tower";
                                        break;
                                    }
                            }
                        }
                        // no event/boss : normal environment bgm
                        boolean isDayTime = WorldHelper.isDayTime(plyWorld);
                        if (current.equals("")) {
                            WorldHelper.BiomeType biomeType = WorldHelper.BiomeType.getBiome(ply);
                            switch (WorldHelper.HeightLayer.getHeightLayer(ply.getLocation())) {
                                case SPACE:
                                    current = isDayTime ? "space_night" : "space";
                                    break;
                                case UNDERGROUND:
                                case CAVERN:
                                    switch (biomeType) {
                                        case NORMAL:
                                        case CORRUPTION:
                                        case HALLOW:
                                        case DESERT:
                                        case OCEAN:
                                        case TUNDRA:
                                        case JUNGLE:
                                        case ASTRAL_INFECTION:
                                            current = biomeType.toString().toLowerCase() + "_underground";
                                            break;
                                        default:
                                            current = biomeType.toString().toLowerCase();
                                    }
                                    break;
                                default:
                                    switch (biomeType) {
                                        case ABYSS:
                                        case DUNGEON:
                                        case TEMPLE:
                                        case BRIMSTONE_CRAG:
                                        case UNDERWORLD:
                                        case ASTRAL_INFECTION:
                                        case CORRUPTION:
                                        case DESERT:
                                        case SULPHUROUS_OCEAN:
                                        case SUNKEN_SEA:
                                        case TUNDRA:
                                            // always the same bgm
                                            current = biomeType.toString().toLowerCase();
                                            break;
                                        case HALLOW:
                                            current = isDayTime ? "hallow" : "normal_night";
                                            break;
                                        case METEOR:
                                            current = "eerie";
                                            break;
                                        default:
                                            current = biomeType.toString().toLowerCase() + (isDayTime ? "" : "_night");
                                    }
                            }
                        }
                    }
                    else {
                        current = forceBGM.asString();
                    }
                    switch (current) {
                        case "return_to_slime":
                            if (! last.equals(current)) {
                                // just started playing this music
                                current += "_full";
                                // prevent instantly switching out from the full version
                                if (! last.equals(current))
                                    startedPlayingTime = currentTime;
                            }
                            break;
                    }
                    // play music if needed
                    long musicDuration = TerrariaHelper.soundConfig.getLong("lengths." + current, 0L);
                    if (printBGMDebugInfo) ply.sendMessage(current + ", " + (musicDuration + startedPlayingTime - currentTime) + " ms left.");
                    boolean shouldPlayMusic = false;
                    // full song finished playing
                    if (musicDuration + startedPlayingTime < currentTime) {
                        shouldPlayMusic = true;
                        // if the current one playing is the full version, next one shall be reduced version
                        if (current.endsWith("_full")) current = current.replace("_full", "");
                    }
                    else if (!last.equals(current))
                        shouldPlayMusic = true;
                    if (shouldPlayMusic) {
                        if (printBGMDebugInfo) ply.sendMessage(current + ", replayed!");
                        ply.stopSound("music." + last);
                        ply.playSound(ply.getEyeLocation(), "music." + current, SoundCategory.MUSIC, Float.MAX_VALUE, 1f);
                        // update last bgm metadata
                        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM, current);
                        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM_TIME, currentTime);
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] threadBGM ", e);
                }
            }
        }, 0, 4);
    }
    public static void threadGrapplingHook() {
        // every 3 ticks (~1/7 second)
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                try {
                    ArrayList<Entity> hooks = (ArrayList<Entity>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOKS).value();
                    boolean shouldRemoveHooks = false;
                    String hookItemName = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_ITEM).asString();
                    String[] hookNameInfo = ItemHelper.splitItemName(ply.getInventory().getItemInOffHand());
                    if (!PlayerHelper.isProperlyPlaying(ply)) {
                        // not survival mode or not logged in etc
                        shouldRemoveHooks = true;
                    }
                    else if (!hookNameInfo[1].equals(hookItemName)) {
                        // hook item do not match
                        shouldRemoveHooks = true;
                    }
                    else if (hooks.size() == 0 || !hooks.get(0).getWorld().equals(ply.getWorld())) {
                        // no grappling hook or world changed
                        shouldRemoveHooks = true;
                    }
                    if (shouldRemoveHooks) {
                        for (Entity hook : hooks) hook.remove();
                        ply.setGravity(true);
                    } else {
                        // get center location information
                        World plyWorld = ply.getWorld();
                        Location center = new Location(plyWorld, 0, 0, 0);
                        int hookedAmount = 0;
                        YmlHelper.YmlSection config = TerrariaHelper.hookConfig;
                        double hookReach = config.getDouble(hookItemName + ".reach", 12),
                                hookPullSpeed = config.getDouble(hookItemName + ".playerSpeed", 0.1);
                        hookReach = hookReach * hookReach * 4;
                        HashSet<Entity> hooksToRemove = new HashSet<>();
                        for (Entity hook : hooks) {
                            // if the hook is too far away
                            if (ply.getLocation().subtract(hook.getLocation()).lengthSquared() > hookReach) {
                                hook.remove();
                            }
                            // if the hook is removed by any mean, schedule its removal from the list
                            if (hook.isDead()) {
                                hooksToRemove.add(hook);
                                continue;
                            }
                            // if the hook is in place
                            if (hook.getVelocity().lengthSquared() < 1e-5) {
                                // only account for the pulling force when the hook is in a solid block
                                if (hook.getLocation().getBlock().getType().isSolid()) {
                                    center.add(hook.getLocation());
                                    hookedAmount++;
                                }
                                // if the block is not solid anymore, remove this hook.
                                else {
                                    hook.remove();
                                }
                            }
                            // draw chain
                            Location drawCenterLoc = ply.getLocation().add(0, 1, 0);
                            Vector dVec = hook.getLocation().subtract(drawCenterLoc).toVector();
                            if (dVec.lengthSquared() > 0) {
                                double dVecLength = dVec.length();
                                // offset vector prevents color block spamming the screen
                                Vector offsetVector = dVec.clone().multiply(1.5 / dVecLength);
                                GenericHelper.handleParticleLine(dVec, drawCenterLoc.add(offsetVector),
                                        new GenericHelper.ParticleLineOptions()
                                                .setLength(dVecLength - 1.5)
                                                .setWidth(0.25, false)
                                                .setAlpha(0.25f)
                                                .setStepsize(1)
                                                .setTicksLinger(3)
                                                .setParticleColor(EntityHelper.getMetadata(hook,
                                                        EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_COLOR).asString()));
                            }
                        }
                        for (Entity hook : hooksToRemove) hooks.remove(hook);
                        if (hookedAmount >= 1) {
                            ply.setGravity(false);
                            resetPlayerFlightTime(ply);
                            ply.setFallDistance(0);
                            center.multiply(1 / (double) hookedAmount);
                            Vector thrust = center.subtract(ply.getEyeLocation()).toVector();
                            if (thrust.lengthSquared() > hookPullSpeed * hookPullSpeed * 36)
                                thrust.normalize().multiply(hookPullSpeed);
                            else if (thrust.lengthSquared() > 0)
                                thrust.multiply(0.5);
                            ply.setVelocity(thrust);
                        } else
                            // no hook attached to ground
                            ply.setGravity(true);
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] threadGrapplingHook ", e);
                }
            }
        }, 0, 1);
    }
    public static void threadLastLocation() {
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_SECOND_LAST_LOCATION,
                        EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_LOCATION).value());
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_LOCATION,
                        EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_CURRENT_LOCATION).value());
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_CURRENT_LOCATION, ply.getLocation());
            }
        }, 0, 1);
    }
    public static void threadMonsterCritterSpawn() {
        // every 5 ticks
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                if (!isProperlyPlaying(ply)) continue;
                // critter spawn
                CritterHelper.naturalCritterSpawn(ply);
                // monster spawn rate
                HashMap<String, Double> attrMap = EntityHelper.getAttrMap(ply);
                double spawnRate = attrMap.getOrDefault("mobSpawnRate", 0.1) *
                        attrMap.getOrDefault("mobSpawnRateMulti", 1d);
                WorldHelper.HeightLayer heightLayer = WorldHelper.HeightLayer.getHeightLayer(ply.getLocation());
                boolean isSurfaceOrSpace =
                        heightLayer == WorldHelper.HeightLayer.SURFACE ||
                                heightLayer == WorldHelper.HeightLayer.SPACE;
                if (isSurfaceOrSpace) {
                    // monster spawn rate when an event is present
                    if (EventAndTime.currentEvent != null) {
                        spawnRate = 0.35;
                    }
                    // monster spawn rate when near a celestial pillar
                    for (CelestialPillar pillar : EventAndTime.pillars.values()) {
                        if (pillar.getBukkitEntity().getWorld() != ply.getWorld())
                            continue;
                        if (pillar.getBukkitEntity().getLocation().distanceSquared(ply.getLocation()) < CelestialPillar.EFFECTED_RADIUS_SQR) {
                            spawnRate = 0.6;
                        }
                    }
                }
                // spawn monster
                for (int i = 0; i < MathHelper.randomRound(spawnRate); i ++) {
                    MonsterHelper.naturalMobSpawning(ply);
                }
            }
        }, 0, 5);
    }
    public static void threadMovement() {
        // every 1 tick
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                // validate the current player
                if (!PlayerHelper.isProperlyPlaying(ply))
                    continue;
                // reset thrust variable if player is on ground
                if (ply.isOnGround()) {
                    resetPlayerFlightTime(ply);
                }
                // setup variables
                int thrustIndex = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_INDEX).asInt();
                int thrustProgress = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_PROGRESS).asInt();
//                Bukkit.broadcastMessage("IndexProgress: " + thrustIndex + ", " + thrustProgress);
                // if this is the first thrust, save the progress
                HashSet<String> accessorySet = (HashSet<String>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.ACCESSORIES).value();
                if (thrustIndex == 0 && thrustProgress == 0)
                    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES_FLIGHT_BACKUP,
                            accessorySet.clone());
                int extraJumpTime = 0;
                int thrustProgressMax = 0;
                double maxSpeed = 1, horizontalSpeed = 0.5;
                double maxAcceleration = 0.5;
                List<String> accessory = (List<String>) EntityHelper.getMetadata(ply,
                        EntityHelper.MetadataName.ACCESSORIES_LIST).value();
                Set<String> availableAccessory = (Set<String>) EntityHelper.getMetadata(ply,
                        EntityHelper.MetadataName.ACCESSORIES_FLIGHT_BACKUP).value();
                Entity entityToPush = ply;
                String accessoryUsed = "";
                boolean isThrusting = ply.getScoreboardTags().contains("temp_thrusting");
                boolean isWing = false;
                boolean gliding = false;
                // if the player is mounting
                if (ply.getVehicle() != null) {
//                    Bukkit.broadcastMessage("FLYING WITH VEHICLE");
                    entityToPush = ply.getVehicle();
                    // get mount (jump/flight) info
                    String mountType = GenericHelper.trimText(entityToPush.getName());
                    ConfigurationSection mountSection = TerrariaHelper.mountConfig.getConfigurationSection(mountType);
                    if (mountSection != null) {
                        thrustProgressMax = mountSection.getInt("maxProgress", 0);
                        maxSpeed = mountSection.getDouble("maxSpeed", 1d);
                        maxAcceleration = mountSection.getDouble("maxAcceleration", 0.5d);
                        horizontalSpeed = mountSection.getDouble("horizontalSpeed", 0.5d);
                        extraJumpTime = mountSection.getInt("extraJumpTime", 0);
                        accessoryUsed = mountType;
                        // after mounting, accessory (wings etc.) can not be used until landed.
                        thrustIndex = 999999;
                    }
                }
                // the player is not on mount
                else {
                    // if the player is not flying
                    if (!isThrusting) {
                        // the player can not save a part of jumping progress for a "double jump" after leaving the ground
                        if (thrustIndex < 0) {
                            thrustIndex = 0;
                            thrustProgress = 0;
                        }
                    }
                    // if the space bar is being pressed and the player should continue jumping/flying
                    else {
                        // handle jump
                        if (ply.isOnGround()) {
                            thrustIndex = -1;
                            // if the player has any wing accessory, do not jump.
                            for (String checkAcc : accessory) {
                                ConfigurationSection wingSection = TerrariaHelper.wingConfig.getConfigurationSection(checkAcc);
                                // if the accessory does not provide any flying/extra jump, do not consider it
                                if (wingSection == null)
                                    continue;
                                // if the first flying accessory is a pair of wings, do not jump
                                if (wingSection.getBoolean("isWing", false)) {
                                    thrustIndex = 0;
                                }
                                // we are only checking the first flying accessory
                                break;
                            }
                        }
                        // if the player is not jumping on ground
                        if (thrustIndex >= 0) {
                            // determine the accessory to use
                            int thrustIndexInitial = thrustIndex;
                            for (; thrustIndex < accessory.size(); thrustIndex++) {
                                String currAcc = accessory.get(thrustIndex);
                                // the accessory was not around when the player started flying
                                // note that the first part is necessary, because
                                // the accessory is removed from set as soon as the player starts using the accessory to fly, not afterwards
                                if (thrustIndexInitial != thrustIndex && !availableAccessory.contains(currAcc))
                                    continue;
                                ConfigurationSection wingSection = TerrariaHelper.wingConfig.getConfigurationSection(currAcc);
                                // if the accessory does not provide any flying/extra jump
                                if (wingSection == null)
                                    continue;
                                // prevent player from exploiting one pair of wings by moving around accessories
//                                Bukkit.broadcastMessage("AVAILABLE ACCESSORIES: " + availableAccessory);
                                availableAccessory.remove(currAcc);
//                                Bukkit.broadcastMessage("AVAILABLE ACCESSORIES AFTER DELETION: " + availableAccessory);
                                // the accessory should be good to go here
                                extraJumpTime = wingSection.getInt("extraJumpTime", 0);
                                thrustProgressMax = wingSection.getInt("flightTime", 0);
                                maxSpeed = wingSection.getDouble("maxSpeed", 1d);
                                maxAcceleration = wingSection.getDouble("maxAcceleration", 0.5d);
                                horizontalSpeed = wingSection.getDouble("horizontalSpeed", 0.5d);
                                isWing = wingSection.getBoolean("isWing", false);
                                accessoryUsed = currAcc;
//                                Bukkit.broadcastMessage("FLYING WITH ACCESSORY, " + thrustIndex + "(" + accessoryUsed);
                                break;
                            }
                            // no rocket boots or double jump on ground
                            if (!isWing && ply.isOnGround()) {
                                extraJumpTime = 0;
                                thrustProgressMax = 0;
                            }
                        }
                        // if the player is jumping
                        else {
                            thrustProgressMax = 6;
                            maxSpeed = 0.6;
                            maxAcceleration = 0.35;
                            horizontalSpeed = 0.2;
                        }
                        // if no valid accessory is being selected, attempt to glide
                        if (extraJumpTime + thrustProgressMax <= 0) {
//                            Bukkit.broadcastMessage("ATTEMPT JUMP");
                            for (String currAcc : accessory) {
                                ConfigurationSection wingSection = TerrariaHelper.wingConfig.getConfigurationSection(currAcc);
                                if (wingSection == null)
                                    continue;
                                isWing = wingSection.getBoolean("isWing", false);
                                // non-wing flying accessories should not be able to glide
                                if (!isWing)
                                    continue;
                                // the accessory should be a wing here
//                                    Bukkit.broadcastMessage("GLIDE");
                                gliding = true;
                                thrustProgressMax = 999999;
                                maxSpeed = 0.5;
                                maxAcceleration = 0.05;
                                horizontalSpeed = wingSection.getDouble("horizontalSpeed", 0.5d) / 2;
                                accessoryUsed = currAcc;
                                break;
                            }
                        }
                    }
                }
                // update velocity
                if (isThrusting) {
                    double speedMulti = 1, accelerationMulti = 1;
                    {
                        thrustProgressMax *= EntityHelper.getAttrMap(ply).getOrDefault("flightTimeMulti", 1d);
                        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(ply);
                        double speedMultiAttribute = attrMap.getOrDefault("speedMulti", 1d);
                        // speed multiplier that exceeds 100% are only 50% as effective on wings
                        if (speedMultiAttribute > 1d)
                            speedMultiAttribute = 1 + (speedMultiAttribute - 1) * 0.5;
                        speedMulti *= speedMultiAttribute;
                        accelerationMulti *= speedMultiAttribute / 2;
                    }
                    if (thrustProgress < extraJumpTime + thrustProgressMax) {
//                        Bukkit.broadcastMessage(thrustProgress + "/" + extraJumpTime + ", " + thrustProgressMax);
                        ply.setFallDistance(0);
                        // movement direction
                        Vector moveDir;
                        {
                            if (gliding) moveDir = new Vector(0, maxSpeed * -1, 0);
                            else moveDir = new Vector(0, maxSpeed, 0);
                            double horizontalMoveYaw = getPlayerMoveYaw(ply);
                            if (horizontalMoveYaw < 1e5) {
                                moveDir.add(MathHelper.vectorFromYawPitch_quick(horizontalMoveYaw, 0).multiply(horizontalSpeed));
                            }
                        }
                        moveDir.multiply(speedMulti);
                        Vector acceleration = moveDir.clone().subtract(entityToPush.getVelocity());
                        double accLength = acceleration.length();
                        if (accLength > maxAcceleration) {
                            acceleration.multiply(maxAcceleration * accelerationMulti / accLength);
                        }
                        // regularize acceleration so that horizontal speed (especially from dash) do not get decreased
                        {
                            Vector moveDirHor = moveDir.clone().setY(0);
                            Vector accelerationComponent = MathHelper.vectorProjection(moveDirHor, acceleration);
                            // acceleration has a component that goes in the opposite direction
                            if (moveDirHor.dot(accelerationComponent) < 0) {
                                acceleration.subtract(accelerationComponent);
                            }
                        }
                        entityToPush.setVelocity(entityToPush.getVelocity().add(acceleration));
                        // extra jump
                        if (thrustProgress < extraJumpTime) {
                            // particles
                            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                                    for (int i = 0; i < 4; i++)
                                        entityToPush.getWorld().spawnParticle(Particle.CLOUD,
                                                entityToPush.getLocation().add((double) xOffset / 2, 0, (double) zOffset / 2)
                                                        .add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5),
                                                0, xOffset, 0, zOffset, 0.1);
                                }
                            }
                            // extra jump do not get consumed continuously
                            ply.removeScoreboardTag("temp_thrusting");
                        }
                        // rocket boots / wings
                        else {
                            // particles
                            switch (accessoryUsed) {
                                case "火箭靴":
                                case "幽灵靴": {
                                    entityToPush.getWorld().spawnParticle(Particle.CLOUD,
                                            entityToPush.getLocation()
                                                    .add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5),
                                            0, 0, -1, 0, 0.175);
                                    break;
                                }
                            }
                            // make sure that gravity does not work against the wings
                            ply.setGravity(false);
                        }
                        if (!gliding) {
                            thrustProgress++;
                        }
                    }
                    // flight time depleted
                    if (thrustProgress >= extraJumpTime + thrustProgressMax) {
//                    Bukkit.broadcastMessage("FLIGHT TIME DEPLETED!");
                        // if the player is using accessory to fly
                        ply.setGravity(true);
                        if (!ply.isInsideVehicle()) {
                            thrustProgress = 0;
                            thrustIndex++;
                        }
                        // so that the next flying accessory do not automatically start using
                        ply.removeScoreboardTag("temp_thrusting");
                    }
                }
                // save variables
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_INDEX, thrustIndex);
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_PROGRESS, thrustProgress);
            }
        }, 0, 1);
    }
    public static void threadRegen() {
        boolean debugMessage = false;
        int delay = 2;
        double perTickMulti = (double)delay / 20;
        // every 4 ticks (1/5 second)
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                if (ply.getScoreboardTags().contains("unauthorized")) continue;
                try {
                    MetadataValue respawnCD = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.RESPAWN_COUNTDOWN);
                    if (respawnCD != null) {
                        int ticksRemaining = respawnCD.asInt();
                        ticksRemaining -= delay;
                        if (ticksRemaining > 0) {
                            ply.setGameMode(GameMode.SPECTATOR);
                            ply.setFlySpeed(0);
                            ply.setFallDistance(0);
                            sendActionBar(ply, "§a重生倒计时： " + ticksRemaining / 20);
                            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.RESPAWN_COUNTDOWN, ticksRemaining);
                        } else {
                            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.RESPAWN_COUNTDOWN, null);
                            ply.setGameMode(GameMode.SURVIVAL);
                            ply.teleport(getSpawnLocation(ply));
                            sendActionBar(ply, "");
                            // reset minion index, sentry index etc.
                            initPlayerStats(ply, false);
                        }
                    } else {
                        if (!isProperlyPlaying(ply)) continue; // waiting to revive etc.
                        // basic variable setup
                        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(ply);
                        HashSet<String> accessories = getAccessories(ply);
                        HashMap<String, Integer> effectMap = EntityHelper.getEffectMap(ply);
                        Location currLoc = ply.getLocation();
                        Location lastLoc = (Location) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_LOCATION).value();
                        boolean moved = lastLoc.getWorld().equals(currLoc.getWorld()) && lastLoc.distanceSquared(currLoc) > 1e-5;
                        // make sure mana do not exceed maximum
                        {
                            int level = Math.min(ply.getLevel(), attrMap.get("maxMana").intValue());
                            ply.setLevel(level);
                        }
                        // health regen
                        {
                            // init variables
                            double healthRegenTime = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.REGEN_TIME).asDouble();
                            double effectiveRegenTime;
                            if (healthRegenTime <= 100) effectiveRegenTime = 0;
                            else if (healthRegenTime < 600) effectiveRegenTime = (healthRegenTime - 100) / 100;
                            else effectiveRegenTime = 5 + ((healthRegenTime - 600) / 200);
                            effectiveRegenTime = Math.floor(effectiveRegenTime);
                            if (debugMessage) ply.sendMessage(effectiveRegenTime + "|" + healthRegenTime);
                            double maxHealth = ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            double regenerationRate = ((maxHealth * 0.85 / 800) + 0.15) * effectiveRegenTime;
                            regenerationRate *= moved ? 0.5 : 1.25;
                            // additional hardcoded accessories etc
                            double additionalHealthRegen = attrMap.getOrDefault("regen", 0d); // from accessories etc
                            if (!moved && accessories.contains("闪亮石")) {
                                regenerationRate += 8;
                                healthRegenTime += delay * 4;
                            }
                            // regen
                            double regenAmount = (regenerationRate + additionalHealthRegen) * perTickMulti * attrMap.getOrDefault("regenMulti", 1d);
                            if (accessories.contains("再生护符") && (ply.getHealth() + regenAmount) * 2 >= maxHealth) {
                                regenAmount = - Math.abs(regenAmount);
                            }
                            double healthAmount = Math.min(ply.getHealth() + regenAmount, maxHealth);
                            if (healthAmount > 0) {
                                ply.setHealth(healthAmount);
                                healthRegenTime += delay;
                                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.REGEN_TIME, Math.min(healthRegenTime, 1201));
                            } else {
                                EntityHelper.handleDeath(ply, ply, ply, EntityHelper.DamageType.NEGATIVE_REGEN, null);
                            }
                        }
                        // mana regen
                        {
                            double manaRegenDelay = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_DELAY).asDouble();
                            double manaRegenCounter = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_COUNTER).asDouble();
                            boolean hasManaRegenBand = accessories.contains("魔力再生手环");
                            boolean hasManaRegenPotionEffect = effectMap.containsKey("魔力再生");
                            if (manaRegenDelay > 0) {
                                // waiting for regen CD
                                manaRegenDelay -= moved ? delay : delay * 2;
                                if (hasManaRegenBand) manaRegenDelay -= delay;
                                if (hasManaRegenPotionEffect) manaRegenDelay -= delay;
                                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_DELAY, manaRegenDelay);
                            } else {
                                // regeneration
                                double manaRegenBonus = attrMap.getOrDefault("manaRegen", 0d);
                                double maxMana = attrMap.getOrDefault("maxMana", 20d);
                                // players with mana regen buff regenerates mana as if their mana is full
                                double manaRatio = hasManaRegenPotionEffect ? 1d : (double) ply.getLevel() / maxMana;
                                double manaRegenRate = ((maxMana * (moved ? 1d / 6 : 1d / 2)) + 1 + manaRegenBonus) * (manaRatio * 0.8 + 0.2) * 1.15;
                                // if the player is currently using item: no natural mana regen; bonus regen is reduced
                                switch (EntityHelper.getDamageType(ply)) {
                                    case SUMMON:
                                    case MAGIC:
                                        if (ply.getScoreboardTags().contains("temp_useCD"))
                                            manaRegenRate = manaRegenBonus * (manaRatio * 0.8 + 0.2);
                                        break;
                                }
                                if (ply.getLevel() < maxMana) {
                                    manaRegenCounter += manaRegenRate * delay * attrMap.getOrDefault("manaRegenMulti", 1d);
                                    double regenAmount = Math.floor(manaRegenCounter / 40);
                                    manaRegenCounter %= 40;
                                    int levelResult = ply.getLevel() + (int) regenAmount;
                                    ply.setLevel((int) Math.min(levelResult, maxMana));
                                    if (ply.getLevel() >= maxMana)
                                        ply.playSound(ply.getLocation(), Sound.BLOCK_NOTE_PLING, 2, 2);
                                    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_COUNTER, manaRegenCounter);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] threadRegen ", e);
                }
            }
        }, 0, delay);
    }
    public static void threadSaveInventories() {
        // thread to save player inventories every 5 seconds
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(),
                () -> {
                    for (Player ply : Bukkit.getOnlinePlayers()) {
                        PlayerHelper.saveData(ply);
                    }
                }, 100, 100);
    }
    private static int getSpecialBiomeBlockType(Block blockToCheck, boolean isInUndergroundOrCavern) {
        switch (blockToCheck.getType()) {
            // lizard or dungeon
            case SMOOTH_BRICK: {
                if (isInUndergroundOrCavern) {
                    int data = blockToCheck.getData();
                    switch (data) {
                        case 1:
                            // lizard
                            if (WorldHelper.BiomeType.getBiome(blockToCheck.getLocation()) == WorldHelper.BiomeType.JUNGLE)
                                return 2;
                        case 2:
                            // dungeon
                            return 1;
                    }
                }
                break;
            }
            // meteor
            case RED_GLAZED_TERRACOTTA:
                return 3;
        }
        // not a special biome
        return 0;
    }
    public static void threadSpecialBiome() {
        double distCheck = 48;
        Vector[] vectorsToCheck = new Vector[] {
                new Vector(distCheck, distCheck, distCheck), new Vector(-distCheck, -distCheck, -distCheck),
                new Vector(distCheck, -distCheck, distCheck), new Vector(-distCheck, distCheck, -distCheck),
                new Vector(distCheck, distCheck, -distCheck), new Vector(-distCheck, -distCheck, distCheck)};
        // every 20 ticks (1 second)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                if (isProperlyPlaying(ply)) {
                    // check height layer
                    boolean isInUndergroundOrCavern;
                    switch (WorldHelper.HeightLayer.getHeightLayer(ply.getLocation())) {
                        case UNDERGROUND:
                        case CAVERN:
                            isInUndergroundOrCavern = true;
                            break;
                        default:
                            isInUndergroundOrCavern = false;
                    }
                    WorldHelper.BiomeType plyBiome = WorldHelper.BiomeType.NORMAL;
                    // check dungeon and lizard temple
                    int[] specialBlocks = {0, 0, 0, 0};
                    World wld = ply.getWorld();
                    for (Vector vector : vectorsToCheck) {
                        MovingObjectPosition blockMovePos = HitEntityInfo.rayTraceBlocks(
                                wld, ply.getEyeLocation().toVector(), ply.getEyeLocation().toVector().add(vector));
                        if (blockMovePos != null) {
                            Vector locVec = MathHelper.toBukkitVector(blockMovePos.pos)
                                    .add(vector.clone().multiply(1e-9));
                            Block blk = wld.getBlockAt(locVec.toLocation(wld));
                            int index = getSpecialBiomeBlockType(blk, isInUndergroundOrCavern);
                            specialBlocks[ index ] = specialBlocks[ index ] + 1;
                        }
                    }
                    if (specialBlocks[1] >= 3)
                        plyBiome = WorldHelper.BiomeType.DUNGEON;
                    else if (specialBlocks[2] >= 3)
                        plyBiome = WorldHelper.BiomeType.TEMPLE;
                    else if (specialBlocks[3] > 1)
                        plyBiome = WorldHelper.BiomeType.METEOR;
                    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_BIOME, plyBiome);
                }
            }
        }, 0, 20);
    }
    // others
    public static void initPlayerStats(Player ply, boolean joinOrRespawn) {
        // metadata and scoreboard tag
        EntityHelper.initEntityMetadata(ply);
        {
            List<String> scoreboardTagsToRemove = new ArrayList<>();
            for (String scoreboardTag : ply.getScoreboardTags()) {
                if (scoreboardTag.startsWith("temp"))
                    scoreboardTagsToRemove.add(scoreboardTag);
            }
            for (String scoreboardTag : scoreboardTagsToRemove) {
                ply.removeScoreboardTag(scoreboardTag);
            }
        }
        // vanilla attribute and vanilla status setup
        ply.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_DIGGING, 999999999, 9, false, false), true);
        ply.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(Double.MAX_VALUE);
        ply.setFoodLevel(0);
        ply.setGravity(true);
        // crafting variables
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_CRAFTING_STATION, "CLOSED");
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_CRAFTING_RECIPE_INDEX, -1);
        // weapon use variables
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_INTERNAL_ITEM_START_USE_CD, 0);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_ITEM_SWING_AMOUNT, 0);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_NEXT_MINION_INDEX, 0);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_NEXT_SENTRY_INDEX, 0);
        // object variables
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MINION_LIST, new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_SENTRY_LIST, new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES, new HashSet<String>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES_LIST, new ArrayList<String>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_SECOND_LAST_LOCATION, ply.getLocation());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_LOCATION, ply.getLocation());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_CURRENT_LOCATION, ply.getLocation());
        // the accessory set cached when first thrusting, to prevent buggy behaviour.
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES_FLIGHT_BACKUP, new HashSet<String>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.EFFECTS, new HashMap<String, Integer>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ATTRIBUTE_MAP, PlayerHelper.getDefaultPlayerAttributes());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_BUFF_INFLICT, PlayerHelper.getDefaultPlayerEffectInflict());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOKS, new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_KEYS_PRESSED, new HashSet<String>());
        // movement and control variable
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_ITEM, "");
        resetPlayerFlightTime(ply);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_DIRECTION, "");
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_KEY_PRESSED_MS, Calendar.getInstance().getTimeInMillis());
        setArmorSet(ply, "");
        // bgm, biome and background
        // prevent duplicated soundtrack etc.
        if (joinOrRespawn) {
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BACKGROUND, "");
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM, "normal");
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM_TIME, 0L);
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_BIOME, WorldHelper.BiomeType.NORMAL);
        }
        // health, mana and regeneration
        if (joinOrRespawn) {
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_HEALTH_TIER, getPlayerHealthTier(ply));
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_TIER, getPlayerManaTier(ply));
        }
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_SECOND_LAST_LOCATION, ply.getLocation());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_LOCATION, ply.getLocation());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.REGEN_TIME, 0d);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_DELAY, 0d);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_COUNTER, 0d);
        // extra setups on join
        if (joinOrRespawn) {
            // load inventories
            loadInventories(ply);
            // join team
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_TEAM, "red");
            // reset monsters spawned to 0
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MONSTER_SPAWNED_AMOUNT, 0);
            // remove teleportation target
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_TELEPORT_TARGET, null);
        }
        // reset attribute
        setupAttribute(ply);
    }
    public static void setupAttribute(Player ply) {
        try {
            ply.removeScoreboardTag("toolChanged");
            // reset buff immunity etc. Do not reset damage type, doing such will cause faulty minion damage.
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.BUFF_IMMUNE, new HashMap<String, Integer>());
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_BUFF_INFLICT, getDefaultPlayerEffectInflict());
            // re-initialize attribute map
            // attrMap is being overridden after newAttrMap is ready to prevent client glitch (especially on max mana)
            HashMap<String, Double> formerAttrMap = EntityHelper.getAttrMap(ply);
            HashMap<String, Double> newAttrMap = getDefaultPlayerAttributes();
            newAttrMap.put("maxHealth", (double) getMaxHealthByTier(getPlayerHealthTier(ply)) );
            newAttrMap.put("maxMana", (double) getMaxManaByTier(getPlayerManaTier(ply)) );
            // potion effect
            HashMap<String, Integer> effectMap = EntityHelper.getEffectMap(ply);
            for (Map.Entry<String, Integer> effectInfo : effectMap.entrySet()) {
                String effect = effectInfo.getKey();
                int ticksRemaining = effectInfo.getValue();
                switch (effect) {
                    case "魔力烧蚀":
                        double potency = ticksRemaining / 400d;
                        // sqrt(i) * 6^(i + 1) = sqrt(i) * e^( ln6 * (i + 1) )
                        double healthLoss = Math.sqrt(potency) * Math.exp(1.791759 * (potency + 1));
                        EntityHelper.tweakAttribute(ply, newAttrMap, "regen", healthLoss + "", false);
                        break;
                    case "魔力疾病":
                        EntityHelper.tweakAttribute(ply, newAttrMap, "damageMagicMulti",
                                ((double)-ticksRemaining / 800) + "", true);
                        break;
                    default: {
                        String attributesPath = "effects." + effect + ".attributes";
                        ConfigurationSection effectSection = TerrariaHelper.buffConfig.getConfigurationSection(attributesPath);
                        if (effectSection != null) {
                            int buffLevel = EntityHelper.getEffectLevel(effect, ticksRemaining);
                            Set<String> attributesTweaked = effectSection.getKeys(false);
                            for (String attr : attributesTweaked) {
                                // other buff may contain non-string attributes, but leveled buff usually do not.
                                // so it is safe to read the attribute from level > 1 as double.
                                // if a buff can be leveled but its level is 1, it does not matter as multiplication is not required.
                                if (buffLevel > 1)
                                    EntityHelper.tweakAttribute(ply, newAttrMap, attr,
                                            (effectSection.getDouble(attr) * buffLevel) + "", true);
                                else
                                    EntityHelper.tweakAttribute(ply, newAttrMap, attr,
                                            effectSection.getString(attr), true);
                            }
                        }
                    }
                }
            }
            PlayerInventory plyInv = ply.getInventory();
            ItemStack plyTool = plyInv.getItemInMainHand();
            // weapon
            {
                String toolCombatType = ItemHelper.getItemCombatType(plyTool);
                if (toolCombatType.equals("武器")) {
                    EntityHelper.tweakAttribute(ply, newAttrMap, plyTool, true);
                }
            }
            // armor
            {
                List<ItemStack> armors = new ArrayList<>();
                {
                    ItemStack helm = plyInv.getHelmet();
                    if (helm != null) {
                        helm = ItemHelper.getRawItem(ItemHelper.splitItemName(helm)[1]);
                        armors.add(helm);
                        plyInv.setHelmet(helm);
                    }
                }
                {
                    ItemStack chestPlate = plyInv.getChestplate();
                    if (chestPlate != null) {
                        chestPlate = ItemHelper.getRawItem(ItemHelper.splitItemName(chestPlate)[1]);
                        armors.add(chestPlate);
                        plyInv.setChestplate(chestPlate);
                    }
                }
                {
                    ItemStack leggings = plyInv.getLeggings();
                    if (leggings != null) {
                        leggings = ItemHelper.getRawItem(ItemHelper.splitItemName(leggings)[1]);
                        armors.add(leggings);
                        plyInv.setLeggings(leggings);
                    }
                }
                // basic attributes
                for (ItemStack armorPiece : armors) {
                    EntityHelper.tweakAttribute(ply, newAttrMap, armorPiece, true);
                }
                // set bonus
                String armorSet = "";
                if (armors.size() == 3) {
                    List<String> possibleArmorSets = null;
                    for (ItemStack armorPiece : armors) {
                        String currPieceType = ItemHelper.splitItemName(armorPiece)[1];
                        List<String> currPieceSets = TerrariaHelper.armorSetConfig.getStringList("pieces." + currPieceType);
                        if (possibleArmorSets == null) {
                            possibleArmorSets = currPieceSets;
                        } else {
                            List<String> setsToRemove = new ArrayList<>();
                            for (String possibleArmorSet : possibleArmorSets) {
                                if (!currPieceSets.contains(possibleArmorSet))
                                    setsToRemove.add(possibleArmorSet);
                            }
                            possibleArmorSets.removeAll(setsToRemove);
                            if (possibleArmorSets.size() == 0) break;
                        }
                    }
                    // if a unique set bonus is found
                    if (possibleArmorSets != null && possibleArmorSets.size() == 1) {
                        armorSet = possibleArmorSets.get(0);
                        ConfigurationSection setBonusSection = TerrariaHelper.armorSetConfig.getConfigurationSection(
                                "sets." + armorSet);
                        // add lore
                        List<String> loreToAdd = setBonusSection.getStringList("lore");
                        for (ItemStack armorPiece : armors) {
                            ItemMeta meta = armorPiece.getItemMeta();
                            List<String> lore = meta.getLore();
                            lore.addAll(loreToAdd);
                            meta.setLore(lore);
                            armorPiece.setItemMeta(meta);
                        }
                        plyInv.setHelmet(armors.get(0));
                        plyInv.setChestplate(armors.get(1));
                        plyInv.setLeggings(armors.get(2));
                        // tweak attributes
                        EntityHelper.tweakAllAttributes(ply, newAttrMap,
                                setBonusSection.getConfigurationSection("attributes"), true);
                    }
                }
                setArmorSet(ply, armorSet);
            }
            // accessories
            {
                Set<String> accessories = new HashSet<>(12);
                List<String> accessoryList = new ArrayList<>(7);
                int accessoryAmount = getAccessoryAmount(ply);
                // setup accessory list
                for (int idx = 1; idx <= accessoryAmount; idx ++) {
                    ItemStack currAcc = DragoncoreHelper.getSlotItem(ply, "accessory" + idx);
                    if (currAcc == null || currAcc.getType() == Material.AIR) continue;
                    String currAccType = ItemHelper.splitItemName(currAcc)[1];
                    // special accessory activation restrictions
                    switch (currAccType) {
                        case "太阳石": {
                            if (!WorldHelper.isDayTime(ply.getWorld()))
                                continue;
                            break;
                        }
                        case "月亮石": {
                            if (WorldHelper.isDayTime(ply.getWorld()))
                                continue;
                            break;
                        }
                        case "魔能谐振仪": {
                            if (ply.getLevel() * 2 > formerAttrMap.getOrDefault("maxMana", 20d))
                                EntityHelper.tweakAttribute(ply, newAttrMap,
                                        "regen", "6", false);
                            break;
                        }
                        case "恼怒项链": {
                            if (ply.getHealth() * 2 > ply.getMaxHealth())
                                EntityHelper.tweakAttribute(ply, newAttrMap,
                                        "damageMulti", "0.2", true);
                            break;
                        }
                        case "归一心元石": {
                            HashMap<String, String> toTweak = new HashMap<>(17);
                            if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.SUPREME_WITCH_CALAMITAS.msgName)) {
                                toTweak.put("maxHealthMulti", "0.1");
                                toTweak.put("useSpeedMeleeMulti", "0.05");
                                toTweak.put("regen", "6");
                                toTweak.put("crit", "5");
                                toTweak.put("damageMulti", "0.1");
                                toTweak.put("damageTakenMulti", "-0.05");
                                toTweak.put("defence", "20");
                                toTweak.put("speedMulti", "0.1");
                                toTweak.put("flightTimeMulti", "0.2");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.EXO_MECHS.msgName)) {
                                toTweak.put("maxHealthMulti", "0.09");
                                toTweak.put("useSpeedMeleeMulti", "0.049");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "4");
                                toTweak.put("damageMulti", "0.098");
                                toTweak.put("damageTakenMulti", "-0.049");
                                toTweak.put("defence", "18");
                                toTweak.put("speedMulti", "0.098");
                                toTweak.put("flightTimeMulti", "0.196");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.YHARON_DRAGON_OF_REBIRTH.msgName)) {
                                toTweak.put("maxHealthMulti", "0.09");
                                toTweak.put("useSpeedMeleeMulti", "0.048");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "4");
                                toTweak.put("damageMulti", "0.095");
                                toTweak.put("damageTakenMulti", "-0.047");
                                toTweak.put("defence", "18");
                                toTweak.put("speedMulti", "0.096");
                                toTweak.put("flightTimeMulti", "0.193");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.THE_DEVOURER_OF_GODS.msgName)) {
                                toTweak.put("maxHealthMulti", "0.09");
                                toTweak.put("useSpeedMeleeMulti", "0.047");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "4");
                                toTweak.put("damageMulti", "0.095");
                                toTweak.put("damageTakenMulti", "-0.047");
                                toTweak.put("defence", "18");
                                toTweak.put("speedMulti", "0.095");
                                toTweak.put("flightTimeMulti", "0.189");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.PROVIDENCE_THE_PROFANED_GODDESS.msgName)) {
                                toTweak.put("maxHealthMulti", "0.08");
                                toTweak.put("useSpeedMeleeMulti", "0.042");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "4");
                                toTweak.put("damageMulti", "0.084");
                                toTweak.put("damageTakenMulti", "-0.042");
                                toTweak.put("defence", "16");
                                toTweak.put("speedMulti", "0.084");
                                toTweak.put("flightTimeMulti", "0.168");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.MOON_LORD.msgName)) {
                                toTweak.put("maxHealthMulti", "0.07");
                                toTweak.put("useSpeedMeleeMulti", "0.039");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "3");
                                toTweak.put("damageMulti", "0.079");
                                toTweak.put("damageTakenMulti", "-0.039");
                                toTweak.put("defence", "14");
                                toTweak.put("speedMulti", "0.079");
                                toTweak.put("flightTimeMulti", "0.157");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.ASTRUM_DEUS.msgName)) {
                                toTweak.put("maxHealthMulti", "0.07");
                                toTweak.put("useSpeedMeleeMulti", "0.038");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "3");
                                toTweak.put("damageMulti", "0.077");
                                toTweak.put("damageTakenMulti", "-0.038");
                                toTweak.put("defence", "14");
                                toTweak.put("speedMulti", "0.077");
                                toTweak.put("flightTimeMulti", "0.154");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.EMPRESS_OF_LIGHT.msgName)) {
                                toTweak.put("maxHealthMulti", "0.06");
                                toTweak.put("useSpeedMeleeMulti", "0.035");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "3");
                                toTweak.put("damageMulti", "0.07");
                                toTweak.put("damageTakenMulti", "-0.035");
                                toTweak.put("defence", "12");
                                toTweak.put("speedMulti", "0.07");
                                toTweak.put("flightTimeMulti", "0.139");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.PLANTERA.msgName)) {
                                toTweak.put("maxHealthMulti", "0.06");
                                toTweak.put("useSpeedMeleeMulti", "0.03");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "3");
                                toTweak.put("damageMulti", "0.061");
                                toTweak.put("damageTakenMulti", "-0.03");
                                toTweak.put("defence", "12");
                                toTweak.put("speedMulti", "0.061");
                                toTweak.put("flightTimeMulti", "0.121");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.BRIMSTONE_ELEMENTAL.msgName)) {
                                toTweak.put("maxHealthMulti", "0.05");
                                toTweak.put("useSpeedMeleeMulti", "0.028");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.055");
                                toTweak.put("damageTakenMulti", "-0.028");
                                toTweak.put("defence", "10");
                                toTweak.put("speedMulti", "0.057");
                                toTweak.put("flightTimeMulti", "0.114");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.CRYOGEN.msgName)) {
                                toTweak.put("maxHealthMulti", "0.04");
                                toTweak.put("useSpeedMeleeMulti", "0.024");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.048");
                                toTweak.put("damageTakenMulti", "-0.024");
                                toTweak.put("defence", "8");
                                toTweak.put("speedMulti", "0.048");
                                toTweak.put("flightTimeMulti", "0.096");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.WALL_OF_FLESH.msgName)) {
                                toTweak.put("maxHealthMulti", "0.04");
                                toTweak.put("useSpeedMeleeMulti", "0.022");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.045");
                                toTweak.put("damageTakenMulti", "-0.022");
                                toTweak.put("defence", "8");
                                toTweak.put("speedMulti", "0.045");
                                toTweak.put("flightTimeMulti", "0.089");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.THE_SLIME_GOD.msgName)) {
                                toTweak.put("maxHealthMulti", "0.04");
                                toTweak.put("useSpeedMeleeMulti", "0.021");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.043");
                                toTweak.put("damageTakenMulti", "-0.021");
                                toTweak.put("defence", "8");
                                toTweak.put("speedMulti", "0.043");
                                toTweak.put("flightTimeMulti", "0.086");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.THE_HIVE_MIND.msgName)) {
                                toTweak.put("maxHealthMulti", "0.03");
                                toTweak.put("useSpeedMeleeMulti", "0.018");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.036");
                                toTweak.put("damageTakenMulti", "-0.018");
                                toTweak.put("defence", "6");
                                toTweak.put("speedMulti", "0.036");
                                toTweak.put("flightTimeMulti", "0.071");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.EYE_OF_CTHULHU.msgName)) {
                                toTweak.put("maxHealthMulti", "0.03");
                                toTweak.put("useSpeedMeleeMulti", "0.015");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.03");
                                toTweak.put("damageTakenMulti", "-0.015");
                                toTweak.put("defence", "6");
                                toTweak.put("speedMulti", "0.03");
                                toTweak.put("flightTimeMulti", "0.061");
                            }
                            else {
                                toTweak.put("maxHealthMulti", "0.02");
                                toTweak.put("useSpeedMeleeMulti", "0.012");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "1");
                                toTweak.put("damageMulti", "0.025");
                                toTweak.put("damageTakenMulti", "-0.012");
                                toTweak.put("defence", "4");
                                toTweak.put("speedMulti", "0.025");
                                toTweak.put("flightTimeMulti", "0.05");
                            }
                            for (String attributeKey : toTweak.keySet()) {
                                EntityHelper.tweakAttribute(ply, newAttrMap,
                                        attributeKey, toTweak.get(attributeKey), true);
                            }
                            break;
                        }
                    }
                    // attribute
                    EntityHelper.tweakAttribute(ply, newAttrMap, currAcc, true);
                    // accessory type
                    accessories.add(currAccType);
                    accessoryList.add(currAccType);
                }
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES, accessories);
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES_LIST, accessoryList);
            }
            // extra handling
            if (ItemHelper.splitItemName(plyTool)[1].equals("月神Prime")) {
                EntityHelper.tweakAttribute(ply, newAttrMap, "critDamage",
                        (newAttrMap.getOrDefault("crit", 4d) * 0.5) + "", true);
            }

            // post-initialization
            // setup max health and max mana
            ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(
                    newAttrMap.getOrDefault("maxHealth", 200d) *
                            newAttrMap.getOrDefault("maxHealthMulti", 1d));
            // setup walking speed
            double walkingSpeed = newAttrMap.getOrDefault("speed", 0.2d) *
                    newAttrMap.getOrDefault("speedMulti", 1d);
            if (walkingSpeed < 0d)
                walkingSpeed = 0d;
            if (Math.abs(ply.getWalkSpeed() - walkingSpeed) > 1e-9) {
                ply.setWalkSpeed((float) walkingSpeed);
            }
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ATTRIBUTE_MAP, newAttrMap);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] setupAttribute ", e);
        }
    }
    public static void loadInventories(Player ply) {
        HashMap<String, Inventory> inventories = new HashMap<>();
        YmlHelper.YmlSection plyFile = getPlayerDataFile(ply);
        // storage inventories (piggy bank, void bag)
        List<String> otherInvs = TerrariaHelper.settingConfig.getStringList("settings.playerInventories");
        for (String invName : otherInvs) {
            String title = TerrariaHelper.settingConfig.getString("settings.playerInventoryTitles." + invName, "");
            List<String> contents = plyFile.getStringList("inventory." + invName);
            if (contents == null) contents = new ArrayList<>(1);
            Inventory inv = Bukkit.createInventory(ply, playerExtraInventorySize, title);
            int slot = 0;
            for (String itemInfo : contents) {
                inv.setItem(slot, ItemHelper.getItemFromDescription(itemInfo, false));
                slot ++;
            }
            inventories.put(invName, inv);
        }
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_INVENTORIES, inventories);
    }
    public static void saveData(Player ply) {
        // save inventory
        saveInventories(ply);
        // save max health and max mana tier
        ConfigurationSection playerDataFile = getPlayerDataFile(ply);
        playerDataFile.set("stats.healthTier", getPlayerHealthTier(ply));
        playerDataFile.set("stats.manaTier", getPlayerManaTier(ply));
    }
    public static void saveInventories(Player ply) {
        HashMap<String, Inventory> inventories = (HashMap<String, Inventory>) EntityHelper.getMetadata(ply,
                EntityHelper.MetadataName.PLAYER_INVENTORIES).value();
        YmlHelper.YmlSection plyFile = getPlayerDataFile(ply);
        for (String invType : inventories.keySet()) {
            Inventory currInv = inventories.get(invType);
            ArrayList<String> result = new ArrayList<>(playerExtraInventorySize);
            for (int i = 0; i < playerExtraInventorySize; i ++) {
                result.add(ItemHelper.getItemDescription(currInv.getItem(i)));
            }
            plyFile.set("inventory." + invType, result);
        }
    }
    public static void handleArmorSetActiveEffect(Player ply) {
        if (!PlayerHelper.isProperlyPlaying(ply))
            return;
        switch (getArmorSet(ply)) {
            case "硫火套装": {
                EntityHelper.applyEffect(ply, "硫火狂怒", 200);
                break;
            }
            case "瘟疫死神套装": {
                EntityHelper.applyEffect(ply, "瘟疫狂暴", 100);
                break;
            }
        }
    }
    public static void handleGrapplingHook(Player ply) {
        if (!PlayerHelper.isProperlyPlaying(ply))
            return;
        List<Entity> hooks = (ArrayList<Entity>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOKS).value();
        String hookItemName = ItemHelper.splitItemName(ply.getInventory().getItemInOffHand())[1];
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_ITEM, hookItemName);
        World hookWorld = ply.getWorld();
        YmlHelper.YmlSection config = TerrariaHelper.hookConfig;
        int hookAmount = config.getInt(hookItemName + ".amount", 0);
        if (hooks.size() >= hookAmount) {
            // removed the first hook on blocks if trying to launch more hooks than the player has
            Entity removed = null;
            for (Entity hook : hooks) {
                if (hook.getVelocity().lengthSquared() < 1e-5) {
                    hook.remove();
                    removed = hook;
                    break;
                }
            }
            if (removed != null) hooks.remove(removed);
            else return;
        }
        // velocity
        double hookSpeed = config.getDouble(hookItemName + ".velocity", 10) / 6;
        EntityPlayer nms_ply = ((CraftPlayer) ply).getHandle();
        double yaw = nms_ply.yaw,
                pitch = nms_ply.pitch;
        Vector velocity = MathHelper.vectorFromYawPitch_quick(yaw, pitch);
        velocity.multiply(hookSpeed);
        Entity hookEntity = EntityHelper.spawnProjectile(ply, velocity, new HashMap<>(), "钩爪");
        // pre-set particle item
        List<String> hookColors = config.getStringList(hookItemName + ".particleItem");
        for (Entity hook : hooks) {
            hookColors.remove(EntityHelper.getMetadata(hook, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_COLOR).asString());
        }
        String color = hookColors.size() > 0 ? hookColors.get(0) : "125|125|125";
        EntityHelper.setMetadata(hookEntity, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_COLOR, color);
        // mark hook entity as a hook
        hookEntity.addScoreboardTag("isHook");
        hooks.add(hookEntity);
    }
    public static int giveItem(Player ply, ItemStack item, boolean dropExtra) {
        if (item == null) return 0;
        int amountInitial = item.getAmount();
        int amountRemaining = amountInitial;
        String itemType = ItemHelper.splitItemName(item)[1];
        switch (itemType) {
            case "铜币":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", 1, 1);
                setMoney(ply, getMoney(ply) + 100 * amountRemaining);
                return 0;
            case "银币":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", 1, 1);
                setMoney(ply, getMoney(ply) + 10000 * amountRemaining);
                return 0;
            case "金币":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", 1, 1);
                setMoney(ply, getMoney(ply) + 1000000 * amountRemaining);
                return 0;
            case "铂金币":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", 1, 1);
                setMoney(ply, getMoney(ply) + 100000000 * amountRemaining);
                return 0;
            case "心":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", 1, 1);
                heal(ply, 20);
                return 0;
            case "星":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", 1, 1);
                restoreMana(ply, 100);
                return 0;
            case "生命强化焰":
            case "伤害强化焰":
            case "魔力强化焰": {
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", 1, 1);
                String effect;
                switch (itemType) {
                    case "生命强化焰":
                        effect = "生命星云";
                        break;
                    case "伤害强化焰":
                        effect = "伤害星云";
                        break;
                    default:
                        effect = "魔力星云";
                }
                String team = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_TEAM).asString();
                for (Player plyEffect : ply.getWorld().getPlayers()) {
                    if (isProperlyPlaying(plyEffect) && EntityHelper.getMetadata(plyEffect, EntityHelper.MetadataName.PLAYER_TEAM).asString().equals(team) && plyEffect.getLocation().distanceSquared(ply.getLocation()) < 3600)
                        EntityHelper.applyEffect(ply, effect, 480);
                }
                return 0;
            }
        }
        try {
            amountRemaining = ItemHelper.addItemToGenericInventory(item, ply.getInventory());
            if (amountRemaining > 0) {
                // put the item in the player's void bag, if the player has a void bag in the inventory
                if (hasVoidBag(ply)) {
                    Inventory voidBagInv = getInventory(ply, "voidBag");
                    if (voidBagInv != null)
                        amountRemaining = ItemHelper.addItemToGenericInventory(item, voidBagInv);
                }
            }
            if (dropExtra && amountRemaining > 0) {
                ItemStack itemToDrop = item.clone();
                itemToDrop.setAmount(amountRemaining);
                ItemHelper.dropItem(ply.getEyeLocation(), itemToDrop);
            }
            if (amountRemaining < amountInitial) ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", 1, 1);
            return amountRemaining;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "PlayerHelper.giveItem", e);
            return amountRemaining;
        }
    }
    public static void heal(LivingEntity ply, double amount) {
        heal(ply, amount, false);
    }
    public static void heal(LivingEntity ply, double amount, boolean displayActualAmount) {
        double healAmount = Math.min(ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() - ply.getHealth(), amount);
        ply.setHealth(ply.getHealth() + healAmount);
        GenericHelper.displayHolo(ply, displayActualAmount ? healAmount : amount, false, "回血");
    }
    public static void restoreMana(Player ply, double amount) {
        int restoreAmount = (int) Math.min(EntityHelper.getAttrMap(ply).getOrDefault("maxMana", 20d) - ply.getLevel(), amount);
        ply.setLevel(ply.getLevel() + restoreAmount);
        GenericHelper.displayHolo(ply, amount, false, "回蓝");
    }
    public static void sendActionBar(Player player, String message) {
        IChatBaseComponent actionBar = IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + message + "\"}");
        PacketPlayOutTitle packet = new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.ACTIONBAR, actionBar);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }
    private static void spectreProjectileTick(Vector currDir, LivingEntity target, int idx, double speed, Player dPly, Location loc, double num, boolean healingOrDamage, String color) {
        if (!target.getWorld().equals(loc.getWorld())) return;
        // if the target becomes invalid, terminate this projectile and start a new identical one
        boolean shouldRetarget = false;
        if (target instanceof Player && !isProperlyPlaying((Player) target)) shouldRetarget = true;
        else if (target.getHealth() <= 0) shouldRetarget = true;
        if (shouldRetarget) {
            createSpectreProjectile(dPly, loc, num, healingOrDamage, color);
            return;
        }
        // ticking mechanism
        Location targetLoc = target.getEyeLocation();
        if (idx > 5) {
            if (idx == 6) speed = 1.5;
            double distSqr = loc.distanceSquared(targetLoc);
            if (distSqr < speed * speed) {
                // if the projectile reaches its target
                currDir = targetLoc.clone().subtract(loc).toVector();
                speed = Math.sqrt(distSqr);
                distSqr = -1;
            } else {
                // if the projectile does not yet reach its target
                Vector dV = targetLoc.clone().subtract(loc).toVector();
                if (dV.lengthSquared() > 0) {
                    // set vector length of currDir to: min( sqrt(dV.length()) , 4)
                    if (currDir.lengthSquared() > 0)
                        MathHelper.setVectorLengthSquared(currDir, Math.min(dV.length(), 16));
                    MathHelper.setVectorLength(dV, 4);
                    currDir.add(dV);
                    speed += 0.15;
                }
            }
            // hits its target
            if (distSqr < 0) {
                if (healingOrDamage) {
                    heal((Player) target, num);
                } else {
                    EntityHelper.handleDamage(dPly, target, num, EntityHelper.DamageReason.SPECTRE);
                }
                return;
            }
        } else
            speed += 0.2;
        MathHelper.setVectorLength(currDir, speed);
        GenericHelper.handleParticleLine(currDir, loc,
                new GenericHelper.ParticleLineOptions()
                        .setLength(speed)
                        .setWidth(0.2)
                        .setStepsize(0.6)
                        .setTicksLinger(4)
                        .setAlpha(0.75f)
                        .setParticleColor(color));
        loc.add(currDir);
        Vector finalCurrDir = currDir;
        double finalSpeed = speed;
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () ->
                spectreProjectileTick(finalCurrDir, target, idx + 1, finalSpeed, dPly, loc, num, healingOrDamage, color),
                2);
    }
    public static void createSpectreProjectile(Player dPly, Location loc, double num, boolean healingOrDamage, String color) {
        Entity target = null;
        if (healingOrDamage) {
            String team = EntityHelper.getMetadata(dPly, EntityHelper.MetadataName.PLAYER_TEAM).asString();
            double targetHealth = 1e9;
            // get the teammate nearby that has the lowest health
            for (Player ply : dPly.getWorld().getPlayers()) {
                // only heal teammates within 48 blocks radius
                if (isProperlyPlaying(ply) &&
                        ply.getHealth() < targetHealth && (ply.getHealth() + 1) < ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() &&
                        ply.getLocation().distanceSquared(dPly.getLocation()) < 2304 &&
                        EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_TEAM).asString().equals(team)) {
                    target = ply;
                    targetHealth = ply.getHealth();
                }
            }
        } else {
            // target a random target that is valid.
            ArrayList<Entity> candidates = new ArrayList<>(35);
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 24, 24, 24)) {
                if (EntityHelper.checkCanDamage(dPly, e)) {
                    candidates.add(e);
                }
            }
            if (candidates.size() > 0)
                target = candidates.get((int) (Math.random() * candidates.size()));
        }
        if (target != null) {
            spectreProjectileTick(MathHelper.randomVector(), (LivingEntity) target, 0, 0.5, dPly, loc, num, healingOrDamage, color);
        }
    }
    public static void playerMagicArmorSet(Player dPly, Entity v, double dmg) {
        if (!v.getScoreboardTags().contains("isMonster")) return;
        String armorSet = getArmorSet(dPly);
        switch (armorSet) {
            case "星云套装": {
                if (dPly.getScoreboardTags().contains("tempNebulaCD")) break;
                ItemStack nebulaItem = new ItemStack(Material.FLINT_AND_STEEL);
                double rdm = Math.random();
                ItemMeta meta = nebulaItem.getItemMeta();
                if (rdm < 1d/9)
                    meta.setDisplayName("§c生命强化焰");
                else if (rdm < 2d/9)
                    meta.setDisplayName("§d伤害强化焰");
                else if (rdm < 1d/3)
                    meta.setDisplayName("§9魔力强化焰");
                else break;
                nebulaItem.setItemMeta(meta);
                v.getWorld().dropItemNaturally(v.getLocation().add(0, 1.5d, 0), nebulaItem);
                dPly.addScoreboardTag("tempNebulaCD");
                Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                    if (dPly.isOnline()) dPly.removeScoreboardTag("tempNebulaCD");
                }, 10);
                break;
            }
            case "幽灵吸血套装":
            case "幽灵输出套装": {
                if (dmg > 2) {
                    if (dPly.getScoreboardTags().contains("tempSpectreCD")) break;
                    dPly.addScoreboardTag("tempSpectreCD");
                    int coolDownTicks;
                    if (armorSet.equals("幽灵吸血套装")) {
                        int projectilePower = (int) Math.min( 200, Math.ceil(dmg * 0.08) );
                        createSpectreProjectile(dPly, v.getLocation().add(0, 1.5d, 0), projectilePower, true, "255|255|255");
                        // 40 health/second = 2 health/tick
                        coolDownTicks = (int) Math.ceil(projectilePower / 2d);
                    } else {
                        int projectilePower = (int) Math.ceil(dmg * 0.5);
                        createSpectreProjectile(dPly, v.getLocation().add(0, 1.5d, 0), projectilePower, false, "255|255|255");
                        // 400 dmg/second = 20 dmg/tick
                        coolDownTicks = (int) Math.ceil(projectilePower / 20d);
                    }
                    Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                        if (dPly.isOnline()) dPly.removeScoreboardTag("tempSpectreCD");
                    }, coolDownTicks);
                }
                break;
            }
        }
        HashSet<String> accessories = PlayerHelper.getAccessories(dPly);
        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(dPly);
        if (accessories.contains("魔能谐振仪")) {
            if (dmg > 2 && !(dPly.getScoreboardTags().contains("tempSpectreCD"))) {
                dPly.addScoreboardTag("tempSpectreCD");
                int projectilePower = (int) Math.min( 50, Math.ceil(dmg * 0.125) );
                double manaRatio = dPly.getLevel() / attrMap.getOrDefault("maxMana", 20d);
                createSpectreProjectile(dPly, v.getLocation().add(0, 1.5d, 0),
                        Math.ceil(projectilePower * manaRatio), true, "90|127|197");
                // 10 health/second = 0.5 health/tick
                int coolDownTicks = (int) Math.ceil(projectilePower / 0.5);
                Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                    if (dPly.isOnline()) dPly.removeScoreboardTag("tempSpectreCD");
                }, coolDownTicks);
            }
        }
    }
    public static void handleDash(Player ply, double yaw, double pitch) {
        if (!PlayerHelper.isProperlyPlaying(ply))
            return;
        // can not dash if player has dash cooldown
        if (ply.getScoreboardTags().contains("temp_dashCD"))
            return;
        Collection<Entity> hooks = (Collection<Entity>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOKS).value();
        // can not dash if any grappling hook is on the wall
        for (Entity hook : hooks) {
            if (hook.isOnGround()) return;
        }
        double dashSpeed = 0;
        int dashCD = -1;
        // armor set dash
        String armorSet = getArmorSet(ply);
        switch (armorSet) {
            case "水晶刺客套装":
                dashSpeed = 0.9;
                dashCD = 30;
                break;
        }
        // accessory dash
        if (dashCD < 0) {
            Collection<String> accessories = getAccessories(ply);
            for (String accessory : accessories) {
                switch (accessory) {
                    case "克苏鲁之眼盾":
                        dashSpeed = 1;
                        dashCD = 35;
                        break;
                    case "宝光盾牌":
                        dashSpeed = 1.15;
                        dashCD = 30;
                        break;
                    case "阿斯加德之英勇":
                        dashSpeed = 1.3;
                        dashCD = 30;
                        break;
                }
                if (dashCD > 0) break;
            }
        }
        // dash if applicable
        if (dashCD > 0) {
            HashMap<String, Double> attrMap = EntityHelper.getAttrMap(ply);
            dashSpeed *= attrMap.getOrDefault("speedMulti", 1d);
            Vector dashVelocity = MathHelper.vectorFromYawPitch_quick(yaw, pitch).multiply(dashSpeed);
            ply.setVelocity(ply.getVelocity().add(dashVelocity));
            ply.addScoreboardTag("temp_dashCD");
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> ply.removeScoreboardTag("temp_dashCD"), dashCD);
        }
    }
}
