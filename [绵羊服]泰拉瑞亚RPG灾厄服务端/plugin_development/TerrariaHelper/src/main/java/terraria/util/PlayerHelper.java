package terraria.util;

import com.earth2me.essentials.api.Economy;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.IChatBaseComponent;
import net.minecraft.server.v1_12_R1.PacketPlayOutTitle;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.libs.jline.internal.Nullable;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.*;
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
import terraria.gameplay.Event;

import java.math.BigDecimal;
import java.util.*;
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
        defaultPlayerAttrMap.put("damageSummonMulti", 0.75d);
        defaultPlayerAttrMap.put("damageTakenMulti", 1d);
        defaultPlayerAttrMap.put("damageContactTakenMulti", 1d);
        defaultPlayerAttrMap.put("damageTrueMeleeMulti", 1d);
        defaultPlayerAttrMap.put("defence", 0d);
        defaultPlayerAttrMap.put("defenceMulti", 1d);
        defaultPlayerAttrMap.put("fishingHooks", 1d);
        defaultPlayerAttrMap.put("fishingPower", 0d);
        defaultPlayerAttrMap.put("healthMulti", 1d);
        defaultPlayerAttrMap.put("healthTier", 5d);
        defaultPlayerAttrMap.put("invulnerabilityTick", 10d);
        defaultPlayerAttrMap.put("knockback", 0d);
        defaultPlayerAttrMap.put("knockbackResistance", 0d);
        defaultPlayerAttrMap.put("knockbackMeleeMulti", 1d);
        defaultPlayerAttrMap.put("knockbackMulti", 1d);
        defaultPlayerAttrMap.put("manaRegen", 0d);
        defaultPlayerAttrMap.put("manaRegenMulti", 1d);
        defaultPlayerAttrMap.put("manaUse", 0d);
        defaultPlayerAttrMap.put("manaUseMulti", 1d);
        defaultPlayerAttrMap.put("manaTier", 1d);
        defaultPlayerAttrMap.put("maxHealth", 200d);
        defaultPlayerAttrMap.put("maxHealthMulti", 1d);
        defaultPlayerAttrMap.put("maxMana", 20d);
        defaultPlayerAttrMap.put("meleeReachMulti", 1d);
        defaultPlayerAttrMap.put("minionDamagePenaltyMulti", 0.5d);
        defaultPlayerAttrMap.put("minionLimit", 1d);
        defaultPlayerAttrMap.put("mobLimit", 15d);
        defaultPlayerAttrMap.put("mobSpawnRate", 0.25d);
        defaultPlayerAttrMap.put("mobSpawnRateMulti", 1d);
        defaultPlayerAttrMap.put("penetration", 0d);
        defaultPlayerAttrMap.put("powerPickaxe", 0d);
        defaultPlayerAttrMap.put("projectileSpeed", 0d);
        defaultPlayerAttrMap.put("projectileSpeedMulti", 1d);
        defaultPlayerAttrMap.put("projectileSpeedArrowMulti", 1d);
        defaultPlayerAttrMap.put("reachExtra", 0d);
        defaultPlayerAttrMap.put("regen", 0d);
        defaultPlayerAttrMap.put("regenMulti", 1d);
        defaultPlayerAttrMap.put("sentryLimit", 1d);
        defaultPlayerAttrMap.put("speed", 0.2d);
        defaultPlayerAttrMap.put("speedMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMagicMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMeleeMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMiningMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedRangedMulti", 1d);
        defaultPlayerAttrMap.put("useTime", 0d);
        // test!
        defaultPlayerAttrMap.put("maxHealth", 1200d);
        defaultPlayerAttrMap.put("maxMana", 350d);
        defaultPlayerAttrMap.put("healthTier", 44d);
        defaultPlayerAttrMap.put("manaTier", 13d);
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
            return (HashMap<String, ArrayList<String>>) EntityHelper.getMetadata(player, "effectInflict").value();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
    public static HashMap<String, Double> getDefaultPlayerAttributes() {
        return (HashMap<String, Double>) defaultPlayerAttrMap.clone();
    }
    public static double getMoney(Player ply) {
        try {
            return Economy.getMoneyExact(ply.getUniqueId()).doubleValue();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] getMoney ", e);
            return 0d;
        }
    }
    public static Inventory getInventory(Player ply, String key) {
        return ((HashMap<String, Inventory>) EntityHelper.getMetadata(ply, "inventories").value())
                .get(key);
    }
    public static int getAccessoryAmount(Player ply) {
        return YmlHelper.getFile("plugins/PlayerData/" + ply.getName() + ".yml").getInt("stats.maxAccessories", 5);
    }
    public static HashSet<String> getAccessories(Entity entity) {
        try {
            return (HashSet<String>) EntityHelper.getMetadata(entity, "accessory").value();
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
        if (ply.getBedSpawnLocation() != null && !ply.getScoreboardTags().contains("bedCancelled"))
            return ply.getBedSpawnLocation();
        return Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE).getSpawnLocation();
    }
    public static GameProgress getGameProgress(Player player) {
        ConfigurationSection bossDefeatedSection = getPlayerDataFile(player).getConfigurationSection("bossDefeated");
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
                return GameProgress.PRE_PROFANED_GODDESS;
            case "亵渎天神":
            case "噬魂幽花":
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
        HashSet<String> allKeysPressed = (HashSet<String>) EntityHelper.getMetadata(ply, "keysPressed").value();
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
    public static YmlHelper.YmlSection getPlayerDataFile(Player ply) {
        return YmlHelper.getFile("plugins/PlayerData/" + ply.getName() + ".yml");
    }
    public static boolean hasDefeated(Player player, String progressToCheck) {
        YmlHelper.YmlSection fileSection = getPlayerDataFile(player);
        return fileSection.getBoolean("bossDefeated." + progressToCheck, false);
    }
    public static boolean isProperlyPlaying(Player player) {
        if (!player.isOnline()) return false;
        if (player.getGameMode() != GameMode.SURVIVAL) return false;
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
    public static void setMoney(Player ply, double amount) {
        try {
            Economy.setMoney(ply.getUniqueId(), BigDecimal.valueOf(amount));
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] setMoney ", e);
        }
    }
    public static void resetPlayerFlightTime(Player ply) {
        EntityHelper.setMetadata(ply, "thrustIndex", 0);
        EntityHelper.setMetadata(ply, "thrustProgress", 0);
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
            attrMapSpore.put("damage", 225d);
            attrMapSpore.put("knockback", 0d);
        }
        // every 5 ticks (1/4 second)
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                try {
                    // validate the current player
                    if (PlayerHelper.isProperlyPlaying(ply)) {
                        double health = ply.getHealth(), maxHealth = ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        int tickIndex = ply.getTicksLived();
                        // handle armor sets
                        switch (EntityHelper.getMetadata(ply, "armorSet").asString()) {
                            case "叶绿魔法套装":
                            case "叶绿射手套装":
                            case "叶绿战士套装":
                                // chlorophyte
                                if (tickIndex % 8 == 0) {
                                    double distanceSqr = 10000d;
                                    Entity target = null;
                                    for (Entity e : ply.getWorld().getNearbyEntities(ply.getEyeLocation(), 25, 25, 25)) {
                                        // is not a valid enemy
                                        if (!(EntityHelper.checkCanDamage(ply, e, true))) break;
                                        double distSqr = e.getLocation().distanceSquared(ply.getLocation());
                                        // further than current
                                        if (distSqr > distanceSqr) break;
                                        // player can not see it
                                        if (!ply.hasLineOfSight(e)) break;
                                        distanceSqr = distSqr;
                                        target = e;
                                    }
                                    if (target != null) {
                                        Vector v;
                                        if (target instanceof LivingEntity) v = ((LivingEntity) target).getEyeLocation().subtract(ply.getEyeLocation()).toVector();
                                        else v = target.getLocation().subtract(ply.getEyeLocation()).toVector();
                                        v.normalize().multiply(1.5);
                                        EntityHelper.spawnProjectile(ply, v, attrMapChlorophyte, "Arrow","叶绿树叶");
                                    }
                                }
                                break;
                        }
                        // handle accessories
                        HashSet<String> accessories = getAccessories(ply);
                        for (String accessory : accessories) {
                            switch (accessory) {
                                case "新手版挥发明胶":
                                case "挥发明胶":
                                    if (tickIndex % 20 == 0) {
                                        double distanceSqr = 10000d;
                                        Entity target = null;
                                        for (Entity e : ply.getWorld().getNearbyEntities(ply.getEyeLocation(), 12, 12, 12)) {
                                            // is not a valid enemy
                                            if (!(EntityHelper.checkCanDamage(ply, e, true))) break;
                                            double distSqr = e.getLocation().distanceSquared(ply.getLocation());
                                            // further than current
                                            if (distSqr > distanceSqr) break;
                                            // player can not see it
                                            if (!ply.hasLineOfSight(e)) break;
                                            distanceSqr = distSqr;
                                            target = e;
                                        }
                                        if (target != null) {
                                            Vector v;
                                            if (target instanceof LivingEntity) v = ((LivingEntity) target).getEyeLocation().subtract(ply.getEyeLocation()).toVector();
                                            else v = target.getLocation().subtract(ply.getEyeLocation()).toVector();
                                            if (accessory.equals("新手版挥发明胶")) {
                                                v.normalize().multiply(0.5);
                                                EntityHelper.spawnProjectile(ply, v, attrMapVolatileGelatinJr, "Arrow","新手版挥发明胶");
                                            } else {
                                                v.normalize().multiply(0.6);
                                                EntityHelper.spawnProjectile(ply, v, attrMapVolatileGelatin, "Arrow","挥发明胶");
                                            }
                                        }
                                    }
                                    break;
                                case "孢子囊":
                                    if (tickIndex % 10 == 0) {
                                        Location spawnLoc = ply.getLocation().add(Math.random() * 10 - 5, Math.random() * 4 - 1, Math.random() * 10 - 5);
                                        if (!spawnLoc.getBlock().getType().isSolid()) {
                                            Vector velocity = new Vector(Math.random() * 0.1 - 0.05, Math.random() * 0.1 - 0.05, Math.random() * 0.1 - 0.05);
                                            EntityHelper.spawnProjectile(ply, velocity, attrMapSpore, "Arrow","孢子球");
                                        }
                                    }
                                    break;
                                case "圣骑士护盾":
                                    if (health * 4 > maxHealth)
                                        EntityHelper.applyEffect(ply, "圣骑士护盾", 20);
                                    break;
                                case "冰冻护盾":
                                case "寒霜壁垒":
                                case "神之壁垒":
                                    if (health * 4 > maxHealth)
                                        EntityHelper.applyEffect(ply, "圣骑士护盾", 20);
                                    if (health * 2 > maxHealth)
                                        EntityHelper.applyEffect(ply, "冰障", 20);
                                    break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] threadArmorAccessory ", e);
                }
            }
        }, 0, 5);
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
                    String last = EntityHelper.getMetadata(ply, "lastBackground").asString();
                    // setup proper background to display
                    String current = "";
                    MetadataValue forceBackground = EntityHelper.getMetadata(ply, "forceBackground");
                    if (forceBackground == null) {
                        if (BossHelper.bossMap.containsKey("月球领主")) current = "虚空";
                        if (ply.getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE)) {
                            if (BossHelper.bossMap.containsKey("猪鲨公爵")) {
                                LivingEntity fishron = (LivingEntity) BossHelper.bossMap.get("猪鲨公爵").get(0);
                                if (fishron.getHealth() / fishron.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() <= 0.15)
                                    current = "猪鲨";
                            } else if (!Event.currentEvent.equals("")) current = Event.currentEvent;
                            if (ply.getLocation().getY() >= 50) {
                                for (Entity pillar : Event.pillars)
                                    if (pillar.getWorld().equals(ply.getWorld()) &&
                                            pillar.getLocation().distanceSquared(ply.getLocation()) < 22500) {
                                        current = pillar.getName().replace("柱", "");
                                        break;
                                    }
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
                        EntityHelper.setMetadata(ply, "lastBackground", current);
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
                    String last = EntityHelper.getMetadata(ply, "lastBGM").asString();
                    long lastTime = EntityHelper.getMetadata(ply, "lastBGMTime").asLong();

                    // setup proper music to play
                    String current = "";
                    long currentTime = Calendar.getInstance().getTimeInMillis();
                    MetadataValue forceBGM = EntityHelper.getMetadata(ply, "forceBGM");
                    if (forceBGM == null) {
                        // no jukebox
                        World plyWorld = ply.getWorld();
                        String worldName = plyWorld.getName();
                        double minBossDistance = 999999;
                        for (String bossName : BossHelper.bossMap.keySet()) {
                            // if the boss has multiple phases
                            switch (bossName) {
                                case "阿娜希塔与利维坦":
                                    if (BossHelper.bossMap.get(bossName).size() == 1) bossName += "1";
                                    else bossName += "2";
                                    break;
                            }
                            double currDist = 99999;
                            Entity currBoss = BossHelper.bossMap.get(bossName).get(0);
                            if (plyWorld.equals(currBoss.getWorld())) currDist = currBoss.getLocation().distanceSquared(ply.getLocation());
                            if (currDist < minBossDistance) {
                                minBossDistance = currDist;
                                current = TerrariaHelper.soundConfig.getString("boss." + bossName, "");
                                switch (bossName) {
                                    case "史莱姆之神":
                                        if (! last.equals(current)) {
                                            // just started playing this music
                                            current += "_full";
                                        }
                                        break;
                                }
                            }
                        }
                        if (current.equals("")) {
                            // other events
                            if (!Event.currentEvent.equals("")) current = Event.currentEvent;
                            // lunar towers
                            if (ply.getLocation().getY() >= 50 && worldName.equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE)) {
                                for (Entity pillar : Event.pillars)
                                    if (pillar.getWorld().equals(plyWorld) &&
                                            pillar.getLocation().distanceSquared(ply.getLocation()) < 22500) {
                                        current = "lunar_tower";
                                        break;
                                    }
                            }
                        }
                        // no event/boss
                        boolean isDayTime = WorldHelper.isDayTime(plyWorld);
                        if (current.equals("")) {
                            WorldHelper.BiomeType biomeType = WorldHelper.BiomeType.getBiome(ply.getLocation());
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
                                        case DUNGEON:
                                        case TEMPLE:
                                        case UNDERWORLD:
                                        case BRIMSTONE_CRAG:
                                        case CORRUPTION:
                                        case ASTRAL_INFECTION:
                                        case DESERT:
                                        case SUNKEN_SEA:
                                        case SULPHUROUS_OCEAN:
                                        case ABYSS:
                                            // always the same bgm
                                            current = biomeType.toString().toLowerCase();
                                            break;
                                        case HALLOW:
                                            current = isDayTime ? "hallow" : "normal_night";
                                            break;
                                        default:
                                            current = biomeType.toString().toLowerCase() + (isDayTime ? "" : "_night");
                                    }
                            }
                        }
                    } else {
                        current = forceBGM.asString();
                        switch (current) {
                            case "return_to_slime":
                                if (! last.equals(current)) {
                                    // just started playing this music
                                    current += "_full";
                                }
                                break;
                        }
                    }
                    // play music if needed
                    long musicDuration = TerrariaHelper.soundConfig.getLong("lengths." + current, 0L);
                    if (printBGMDebugInfo) ply.sendMessage(current + ", " + (musicDuration + lastTime - currentTime) + " ms left.");
                    boolean shouldPlayMusic = false;
                    if (musicDuration + lastTime < currentTime) {
                        // full song finished playing
                        shouldPlayMusic = true;
                        // if the current one playing is the full version, next one shall be reduced version
                        if (current.endsWith("_full")) current = current.replace("_full", "");
                    } else if (!last.equals(current)) shouldPlayMusic = true;
                    if (shouldPlayMusic) {
                        if (printBGMDebugInfo) ply.sendMessage(current + ", replayed!");
                        ply.stopSound("music." + last);
                        ply.playSound(ply.getEyeLocation(), "music." + current, SoundCategory.MUSIC, Float.MAX_VALUE, 1f);
                        // update last bgm metadata
                        EntityHelper.setMetadata(ply, "lastBGM", current);
                        EntityHelper.setMetadata(ply, "lastBGMTime", currentTime);
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
                    ArrayList<Entity> hooks = (ArrayList<Entity>) EntityHelper.getMetadata(ply, "hooks").value();
                    boolean shouldRemoveHooks = false;
                    String hookItemName = EntityHelper.getMetadata(ply, "grapplingHookItem").asString();
                    String[] hookNameInfo = ItemHelper.splitItemName(ply.getInventory().getItemInOffHand());
                    if (!PlayerHelper.isProperlyPlaying(ply)) {
                        // not survival mode or not logged in etc
                        shouldRemoveHooks = true;
                    } else if (!hookNameInfo[1].equals(hookItemName)) {
                        // hook item do not match
                        shouldRemoveHooks = true;
                    } else if (hooks.size() == 0 || !hooks.get(0).getWorld().equals(ply.getWorld())) {
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
                        YmlHelper.YmlSection config = YmlHelper.getFile("plugins/Data/hooks.yml");
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
                            if (hook.isOnGround()) {
                                center.add(hook.getLocation());
                                hookedAmount++;
                            }
                            // draw chain
                            Vector dVec = hook.getLocation().subtract(ply.getEyeLocation()).toVector();
                            if (dVec.lengthSquared() > 0) {
                                double dVecLength = dVec.length();
                                // offset vector prevents color block spamming the screen
                                Vector offsetVector = dVec.clone().multiply(1/dVecLength);
                                GenericHelper.handleParticleLine(dVec, ply.getEyeLocation().add(offsetVector),
                                        new GenericHelper.ParticleLineOptions()
                                                .setLength(dVecLength)
                                                .setWidth(0.25, false)
                                                .setAlpha(0.25f)
                                                .setStepsize(1)
                                                .setTicksLinger(3)
                                                .setParticleColor(EntityHelper.getMetadata(hook, "color").asString()));
                            }
                        }
                        for (Entity hook : hooksToRemove) hooks.remove(hook);
                        if (hookedAmount >= 1) {
                            ply.setGravity(false);
                            EntityHelper.setMetadata(ply, "thrustIndex", 0);
                            EntityHelper.setMetadata(ply, "thrustProgress", 0);
                            ply.setFallDistance(0);
                            center.multiply(1 / (double) hookedAmount);
                            Vector thrust = center.subtract(ply.getEyeLocation()).toVector();
                            if (thrust.lengthSquared() > hookPullSpeed * hookPullSpeed * 36)
                                thrust.normalize().multiply(hookPullSpeed);
                            else if (thrust.lengthSquared() > 0)
                                thrust.multiply(0.1666667);
                            ply.setVelocity(thrust);
                        } else
                            // no hook attached to ground
                            ply.setGravity(true);
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] threadGrapplingHook ", e);
                }
            }
        }, 0, 3);
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
                int thrustIndex = EntityHelper.getMetadata(ply, "thrustIndex").asInt();
                int thrustProgress = EntityHelper.getMetadata(ply, "thrustProgress").asInt();
//                Bukkit.broadcastMessage("IndexProgress: " + thrustIndex + ", " + thrustProgress);
                // if this is the first thrust, save the progress
                HashSet<String> accessorySet = (HashSet<String>) EntityHelper.getMetadata(ply, "accessory").value();
                if (thrustIndex == 0 && thrustProgress == 0)
                    EntityHelper.setMetadata(ply, "accessoryThrust",
                            accessorySet.clone());
                int extraJumpTime = 0;
                int thrustProgressMax = 0;
                double maxSpeed = 1, horizontalSpeed = 0.5;
                double maxAcceleration = 0.5;
                List<String> accessory = (List<String>) EntityHelper.getMetadata(ply, "accessoryList").value();
                Set<String> availableAccessory = (Set<String>) EntityHelper.getMetadata(ply, "accessoryThrust").value();
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
                        // the player can not keep jumping after leaving the ground
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
                        if (accessorySet.contains("翱翔徽章")) {
                            thrustProgressMax *= 1.5;
                            accelerationMulti *= 1.5;
                        }
                        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(ply);
                        double speedMultiAttribute = attrMap.getOrDefault("speedMulti", 1d);
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
                EntityHelper.setMetadata(ply, "thrustIndex", thrustIndex);
                EntityHelper.setMetadata(ply, "thrustProgress", thrustProgress);
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
                    MetadataValue respawnCD = EntityHelper.getMetadata(ply, "respawnCD");
                    if (respawnCD != null) {
                        int ticksRemaining = respawnCD.asInt();
                        ticksRemaining -= delay;
                        if (ticksRemaining > 0) {
                            ply.setGameMode(GameMode.SPECTATOR);
                            ply.setFlySpeed(0);
                            ply.setFallDistance(0);
                            sendActionBar(ply, "§a重生倒计时： " + ticksRemaining / 20);
                            EntityHelper.setMetadata(ply, "respawnCD", ticksRemaining);
                        } else {
                            EntityHelper.setMetadata(ply, "respawnCD", null);
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
                        Location lastLoc = (Location) EntityHelper.getMetadata(ply, "lastLocation").value();
                        EntityHelper.setMetadata(ply, "lastLocation", currLoc);
                        boolean moved = lastLoc.getWorld().equals(currLoc.getWorld()) && lastLoc.distanceSquared(currLoc) > 1e-5;
                        // make sure health or mana do not exceed maximum
                        {
                            ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(
                                    attrMap.getOrDefault("maxHealth", 200d) *
                                            attrMap.getOrDefault("maxHealthMulti", 1d));
                            int level = Math.min(ply.getLevel(), attrMap.get("maxMana").intValue());
                            ply.setLevel(level);
                        }
                        // health regen
                        {
                            // init variables
                            double healthRegenTime = EntityHelper.getMetadata(ply, "regenTime").asDouble();
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
                            double healthAmount = Math.min(ply.getHealth() + regenAmount, maxHealth);
                            if (healthAmount > 0) {
                                ply.setHealth(healthAmount);
                                healthRegenTime += delay;
                                EntityHelper.setMetadata(ply, "regenTime", Math.min(healthRegenTime, 1201));
                            } else {
                                EntityHelper.handleDeath(ply, ply, ply, "Negative_Regen");
                            }
                        }
                        // mana regen
                        {
                            double manaRegenDelay = EntityHelper.getMetadata(ply, "manaRegenDelay").asDouble();
                            double manaRegenCounter = EntityHelper.getMetadata(ply, "manaRegenCounter").asDouble();
                            boolean hasManaRegenBand = accessories.contains("魔力再生手环");
                            boolean hasManaRegenPotionEffect = effectMap.containsKey("魔力再生");
                            if (manaRegenDelay > 0) {
                                // waiting for regen CD
                                manaRegenDelay -= moved ? delay : delay * 2;
                                if (hasManaRegenBand) manaRegenDelay -= delay;
                                if (hasManaRegenPotionEffect) manaRegenDelay -= delay;
                                EntityHelper.setMetadata(ply, "manaRegenDelay", manaRegenDelay);
                            } else {
                                // regeneration
                                double manaRegenBonus = attrMap.getOrDefault("manaRegen", 0d);
                                double maxMana = attrMap.getOrDefault("maxMana", 20d);
                                // players with mana regen buff regenerates mana as if their mana is full
                                double manaRatio = hasManaRegenPotionEffect ? 1d : (double) ply.getLevel() / maxMana;
                                double manaRegenRate = ((maxMana * (moved ? 1d / 6 : 1d / 2)) + 1 + manaRegenBonus) * (manaRatio * 0.8 + 0.2) * 1.15;
                                if (ply.getLevel() < maxMana) {
                                    manaRegenCounter += manaRegenRate * delay * attrMap.getOrDefault("manaRegenMulti", 1d);
                                    double regenAmount = Math.floor(manaRegenCounter / 40);
                                    manaRegenCounter %= 40;
                                    int levelResult = ply.getLevel() + (int) regenAmount;
                                    ply.setLevel((int) Math.min(levelResult, maxMana));
                                    if (ply.getLevel() >= maxMana)
                                        ply.playSound(ply.getLocation(), Sound.BLOCK_NOTE_PLING, 2, 2);
                                    EntityHelper.setMetadata(ply, "manaRegenCounter", manaRegenCounter);
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
        // attribute and vanilla status setup
        ply.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_DIGGING, 999999999, 9, false, false), true);
        ply.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(Double.MAX_VALUE);
        ply.setFoodLevel(0);
        ply.setGravity(true);
        // crafting variables
        EntityHelper.setMetadata(ply, "craftingStation", "CLOSED");
        EntityHelper.setMetadata(ply, "recipeNumber", -1);
        // weapon use variables
        EntityHelper.setMetadata(ply, "useCDInternal", 0);
        EntityHelper.setMetadata(ply, "swingAmount", 0);
        EntityHelper.setMetadata(ply, "nextMinionIndex", 0);
        EntityHelper.setMetadata(ply, "nextSentryIndex", 0);
        // object variables
        EntityHelper.setMetadata(ply, "minions", new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, "sentries", new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, "accessory", new HashSet<String>());
        EntityHelper.setMetadata(ply, "accessoryList", new ArrayList<String>());
            // the accessory set cached when first thrusting, to prevent buggy behaviour.
        EntityHelper.setMetadata(ply, "accessoryThrust", new HashSet<String>());
        EntityHelper.setMetadata(ply, "effects", new HashMap<String, Integer>());
        EntityHelper.setMetadata(ply, "attrMap", PlayerHelper.getDefaultPlayerAttributes());
        EntityHelper.setMetadata(ply, "effectInflict", PlayerHelper.getDefaultPlayerEffectInflict());
        EntityHelper.setMetadata(ply, "hooks", new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, "keysPressed", new HashSet<String>());
        // mob spawning variable
        EntityHelper.setMetadata(ply, "mobAmount", 0);
        EntityHelper.setMetadata(ply, "biome", "normal");
        // movement and control variable
        EntityHelper.setMetadata(ply, "grapplingHookItem", "");
        EntityHelper.setMetadata(ply, "thrustIndex", 0);
        EntityHelper.setMetadata(ply, "thrustProgress", 0);
        EntityHelper.setMetadata(ply, "chargeDir", "");
        EntityHelper.setMetadata(ply, "chargeDirLastPressed", Calendar.getInstance().getTimeInMillis());
        // other variable
        if (joinOrRespawn) {
            EntityHelper.setMetadata(ply, "team", "red");
        }
        EntityHelper.setMetadata(ply, "armorSet", "");
        // bgm and background
        if (joinOrRespawn) {
            EntityHelper.setMetadata(ply, "lastBackground", "");
            EntityHelper.setMetadata(ply, "lastBGM", "normal");
            EntityHelper.setMetadata(ply, "lastBGMTime", 0L);
        }
        // regeneration
        EntityHelper.setMetadata(ply, "lastLocation", ply.getLocation());
        EntityHelper.setMetadata(ply, "regenTime", 0d);
        EntityHelper.setMetadata(ply, "manaRegenDelay", 0d);
        EntityHelper.setMetadata(ply, "manaRegenCounter", 0d);
        // inventories
        if (joinOrRespawn) {
            loadInventories(ply);
        }
    }
    public static void setupAttribute(Player ply) {
        try {
            ply.removeScoreboardTag("toolChanged");
            // re-initialize attribute map
            // attrMap is being overridden after newAttrMap is ready to prevent client glitch (especially on max mana)
            HashMap<String, Double> newAttrMap = getDefaultPlayerAttributes();
            EntityHelper.setMetadata(ply, "effectInflict", getDefaultPlayerEffectInflict());
            // potion effect
            HashMap<String, Integer> effectMap = EntityHelper.getEffectMap(ply);
            for (Map.Entry<String, Integer> effectInfo : effectMap.entrySet()) {
                String effect = effectInfo.getKey();
                int ticksRemaining = effectInfo.getValue();
                switch (effect) {
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
            // weapon
            {
                ItemStack tool = plyInv.getItemInMainHand();
                String toolCombatType = ItemHelper.getItemCombatType(tool);
                if (toolCombatType.equals("武器")) {
                    EntityHelper.tweakAttribute(ply, newAttrMap, tool, true);
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
                EntityHelper.setMetadata(ply, "armorSet", armorSet);
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
                            return;
                        }
                        case "月亮石": {
                            if (WorldHelper.isDayTime(ply.getWorld()))
                                continue;
                            return;
                        }
                    }
                    // attribute
                    EntityHelper.tweakAttribute(ply, newAttrMap, currAcc, true);
                    // accessory type
                    accessories.add(currAccType);
                    accessoryList.add(currAccType);
                }
                EntityHelper.setMetadata(ply, "accessory", accessories);
                EntityHelper.setMetadata(ply, "accessoryList", accessoryList);
            }
            // setup walking speed
            double walkingSpeed = newAttrMap.getOrDefault("speed", 0.2d) *
                    newAttrMap.getOrDefault("speedMulti", 1d);
            if (Math.abs(ply.getWalkSpeed() - walkingSpeed) > 1e-9) {
                ply.setWalkSpeed((float) walkingSpeed);
            }
            EntityHelper.setMetadata(ply, "attrMap", newAttrMap);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] setupAttribute ", e);
        }
    }
    public static void loadInventories(Player ply) {
        HashMap<String, Inventory> inventories = new HashMap<>();
        YmlHelper.YmlSection plyFile = YmlHelper.getFile("plugins/PlayerData/" + ply.getName() + ".yml");
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
        EntityHelper.setMetadata(ply, "inventories", inventories);
    }
    public static void saveInventories(Player ply) {
        HashMap<String, Inventory> inventories = (HashMap<String, Inventory>) EntityHelper.getMetadata(ply, "inventories").value();
        YmlHelper.YmlSection plyFile = YmlHelper.getFile("plugins/PlayerData/" + ply.getName() + ".yml");
        for (String invType : inventories.keySet()) {
            Inventory currInv = inventories.get(invType);
            ArrayList<String> result = new ArrayList<>(playerExtraInventorySize);
            for (int i = 0; i < playerExtraInventorySize; i ++) {
                result.add(ItemHelper.getItemDescription(currInv.getItem(i)));
            }
            plyFile.set("inventory." + invType, result);
        }
    }
    @Nullable
    public static void handleGrapplingHook(Player ply) {
        List<Entity> hooks = (ArrayList<Entity>) EntityHelper.getMetadata(ply, "hooks").value();
        String hookItemName = ItemHelper.splitItemName(ply.getInventory().getItemInOffHand())[1];
        EntityHelper.setMetadata(ply, "grapplingHookItem", hookItemName);
        World hookWorld = ply.getWorld();
        YmlHelper.YmlSection config = YmlHelper.getFile("plugins/Data/hooks.yml");
        int hookAmount = config.getInt(hookItemName + ".amount", 0);
        if (hooks.size() >= hookAmount) {
            // removed the first hook on blocks if trying to launch more hooks than the player has
            Entity removed = null;
            for (Entity hook : hooks) {
                if (hook.isOnGround()) {
                    hook.remove();
                    removed = hook;
                    break;
                }
            }
            if (removed != null) hooks.remove(removed);
            else return;
        }
        Arrow hookEntity = (Arrow) hookWorld.spawnEntity(ply.getEyeLocation(), EntityType.ARROW);
        hookEntity.setShooter(ply);
        // velocity
        double hookSpeed = config.getDouble(hookItemName + ".velocity", 10) / 6;
        EntityPlayer nms_ply = ((CraftPlayer) ply).getHandle();
        double yaw = nms_ply.yaw,
                pitch = nms_ply.pitch;
        Vector velocity = MathHelper.vectorFromYawPitch_quick(yaw, pitch);
        velocity.multiply(hookSpeed);
        hookEntity.setGravity(false);
        hookEntity.setVelocity(velocity);
        // pre-set particle item
        List<String> hookColors = config.getStringList(hookItemName + ".particleItem");
        for (Entity hook : hooks) {
            hookColors.remove(EntityHelper.getMetadata(hook, "color").asString());
        }
        String color = hookColors.size() > 0 ? hookColors.get(0) : "125|125|125";
        EntityHelper.setMetadata(hookEntity, "color", color);
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
                String team = EntityHelper.getMetadata(ply, "team").asString();
                for (Player plyEffect : ply.getWorld().getPlayers()) {
                    if (isProperlyPlaying(plyEffect) && EntityHelper.getMetadata(plyEffect, "team").asString().equals(team) && plyEffect.getLocation().distanceSquared(ply.getLocation()) < 3600)
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
    public static void heal(Player ply, double amount) {
        double healAmount = Math.min(ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() - ply.getHealth(), amount);
        ply.setHealth(ply.getHealth() + healAmount);
        GenericHelper.displayHolo(ply, amount, false, "回血");
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
            if (idx == 6) speed = 1;
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
                    speed += 0.1;
                }
            }
            // hits its target
            if (distSqr < 0) {
                if (healingOrDamage) {
                    heal((Player) target, num);
                } else {
                    EntityHelper.handleDamage(dPly, target, num, "Spectre");
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
            String team = EntityHelper.getMetadata(dPly, "team").asString();
            double targetHealth = 1e9;
            // get the teammate nearby that has the lowest health
            for (Player ply : dPly.getWorld().getPlayers()) {
                // only heal teammates within 48 blocks radius
                if (isProperlyPlaying(ply) &&
                        ply.getHealth() < targetHealth && (ply.getHealth() + 1) < ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() &&
                        ply.getLocation().distanceSquared(dPly.getLocation()) < 2304 &&
                        EntityHelper.getMetadata(ply, "team").asString().equals(team)) {
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
        String armorSet = EntityHelper.getMetadata(dPly, "armorSet").asString();
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
                        double projectilePower = (int) Math.ceil(dmg * 0.08);
                        createSpectreProjectile(dPly, v.getLocation().add(0, 1.5d, 0), Math.ceil(projectilePower), true, "255|255|255");
                        // 80 health/second
                        coolDownTicks = (int) Math.ceil(projectilePower / 4);
                    } else {
                        double projectilePower = (int) Math.ceil(dmg * 0.5);
                        createSpectreProjectile(dPly, v.getLocation().add(0, 1.5d, 0), Math.ceil(projectilePower), false, "255|255|255");
                        // 800 dmg/second
                        coolDownTicks = (int) Math.ceil(projectilePower / 40);
                    }
                    Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                        if (dPly.isOnline()) dPly.removeScoreboardTag("tempSpectreCD");
                    }, coolDownTicks);
                }
                break;
            }
        }
    }
    public static void handleDash(Player ply, double yaw, double pitch) {
        // can not dash if player has dash cooldown
        if (ply.getScoreboardTags().contains("temp_dashCD"))
            return;
        Collection<Entity> hooks = (Collection<Entity>) EntityHelper.getMetadata(ply, "hooks").value();
        // can not dash if any grappling hook is on the wall
        for (Entity hook : hooks) {
            if (hook.isOnGround()) return;
        }
        double dashSpeed = 0;
        int dashCD = -1;
        // armor set dash
        String armorSet = EntityHelper.getMetadata(ply, "armorSet").asString();
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
