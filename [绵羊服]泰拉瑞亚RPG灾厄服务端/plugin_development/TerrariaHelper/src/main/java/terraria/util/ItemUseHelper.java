package terraria.util;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.others.PlayerTornado;
import terraria.entity.projectile.*;
import terraria.entity.others.TerrariaFishingHook;
import terraria.entity.minion.MinionCaveSpider;
import terraria.entity.minion.MinionHusk;
import terraria.entity.minion.MinionSlime;
import terraria.gameplay.EventAndTime;
import terraria.gameplay.Setting;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ItemUseHelper {
    public enum QuickBuffType {
        NONE, HEALTH, MANA, BUFF;
    }
    public static final String SOUND_GENERIC_SWING = "item.genericSwing", SOUND_BOW_SHOOT = "item.bowShoot",
            SOUND_GUN_FIRE = "item.gunfire", SOUND_GUN_FIRE_LOUD = "entity.generic.explode",
            SOUND_ARK_PARRY = "item.ark.parry", SOUND_ARK_SCISSOR_CUT = "item.ark.snap";
    protected static final double MELEE_MIN_STRIKE_RADIUS = 0.25;
    public static int applyCD(Player ply, double CD) {
        int coolDown = (int) CD;
        if (Math.random() < CD % 1) coolDown ++;
        if (coolDown == 0 && CD > 0) coolDown = 1;
        return applyCD(ply, coolDown);
    }
    public static int applyCD(Player ply, int CD) {
        ply.addScoreboardTag("temp_useCD");
        MetadataValue lastCDInternal = EntityHelper.getMetadata(ply,
                EntityHelper.MetadataName.PLAYER_INTERNAL_LAST_ITEM_START_USE_CD);
        long lastCDApply;
        if (lastCDInternal == null) {
            lastCDApply = 0;
        } else {
            lastCDApply = lastCDInternal.asLong() + 1;
        }
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_INTERNAL_ITEM_START_USE_CD, lastCDApply);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_INTERNAL_LAST_ITEM_START_USE_CD, lastCDApply);
        ItemStack tool = ply.getInventory().getItemInMainHand();
        // the CD <= 0: never stops on its own
        PacketPlayOutSetCooldown packet = new PacketPlayOutSetCooldown(CraftItemStack.asNMSCopy(tool).getItem(), CD <= 0 ? 1919810 : CD);
        ((CraftPlayer) ply).getHandle().playerConnection.sendPacket(packet);
        if (CD > 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                if (ply.isOnline() && EntityHelper.getMetadata(ply,
                        EntityHelper.MetadataName.PLAYER_INTERNAL_ITEM_START_USE_CD).asLong() == lastCDApply) {
                    if (!PlayerHelper.isProperlyPlaying(ply)) {
                        ply.removeScoreboardTag("temp_autoSwing");
                        ply.removeScoreboardTag("temp_isLoadingWeapon");
                    }
                    // handle next use
                    ply.removeScoreboardTag("temp_useCD");
                    if (ply.getScoreboardTags().contains("temp_autoSwing")) {
                        playerUseItem(ply);
                    }
                }
            }, CD);
        }
        return CD;
    }
    // util functions for use item
    protected static void playerSwingPickaxe(Player ply, HashMap<String, Double> attrMap, boolean isRightClick) {
        ply.playSound(ply.getEyeLocation(), "item.genericSwing", SoundCategory.PLAYERS, 1, 1);
        double pickaxeReach = 4 + attrMap.getOrDefault("reachExtra", 0d);
        pickaxeReach *= attrMap.getOrDefault("meleeReachMulti", 1d);
        // mine block if applicable
        Block blk = ply.getTargetBlock(GameplayHelper.noMiningSet, (int) Math.round(pickaxeReach));
        if (blk != null) GameplayHelper.playerMineBlock(blk, ply);
        // left click swings until stopped
        if (!isRightClick)
            ply.addScoreboardTag("temp_autoSwing");
        double useCD = attrMap.getOrDefault("useTime", 20d);
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) *
                attrMap.getOrDefault("useSpeedMeleeMulti", 1d) *
                attrMap.getOrDefault("useSpeedMiningMulti", 1d);
        useCD /= useSpeed;
        applyCD(ply, useCD);
    }
    protected static void playerSwingFishingRod(Player ply, HashMap<String, Double> attrMap, String hookType) {
        // retracting hooks
        if (ply.getScoreboardTags().contains("temp_autoSwing")) {
            ply.removeScoreboardTag("temp_isLoadingWeapon");
            ply.removeScoreboardTag("temp_autoSwing");
            applyCD(ply, 10);
            return;
        }
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        Vector facingDir = MathHelper.vectorFromYawPitch_approx(plyNMS.yaw, plyNMS.pitch);
        facingDir.multiply(1.5);
        double hookAmountTemp = attrMap.getOrDefault("fishingHooks", 1d);
        // prevent accidental inaccuracy
        hookAmountTemp += 0.01;
        int hookAmount = (int) hookAmountTemp;
        // other variables
        boolean lavaProof = false;
        switch (hookType) {
            case "熔线钓竿":
            case "金钓竿":
            case "饮食者钓竿":
            case "岩缝取鱼者":
            case "鳕鱼吞噬者":
                lavaProof = true;
        }
        float fishingPower = attrMap.get("fishingPower").floatValue();
        EntityHuman shooter = ((CraftPlayer) ply).getHandle();
        CraftWorld wld = (CraftWorld) ply.getWorld();
        // before spawning projectile, turn on the auto swing and apply CD
        ply.addScoreboardTag("temp_autoSwing");
        ply.addScoreboardTag("temp_isLoadingWeapon");
        applyCD(ply, 0);
        // spawn fishhooks
        for (int i = 0; i < hookAmount; i ++) {
            Vector shootVel = facingDir.clone();
            if (hookAmount > 1) {
                shootVel.add(MathHelper.randomVector().multiply(0.5));
                shootVel.normalize().multiply(1.5);
            }
            // spawn fishhook
            TerrariaFishingHook entity = new TerrariaFishingHook(shooter.getWorld(), shooter, lavaProof, fishingPower);
            entity.motX = shootVel.getX();
            entity.motY = shootVel.getY();
            entity.motZ = shootVel.getZ();
            wld.addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }
    }
    protected static boolean playerUseEventSummon(Player ply, String itemName, ItemStack itemStack) {
        boolean successful = false;
        boolean isDayTime = WorldHelper.isDayTime(ply.getWorld());
        int useCD = 20;
        switch (itemName) {
            case "终末石":
                if (ply.getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE)) {
                    successful = EventAndTime.startBossRush();
                }
                break;
            case "日耀碑牌":
                if (isDayTime) {
                    successful = EventAndTime.initializeEvent(EventAndTime.Events.SOLAR_ECLIPSE);
                }
                break;
            case "调皮礼物":
                if (!isDayTime) {
                    successful = EventAndTime.initializeEvent(EventAndTime.Events.FROST_MOON);
                }
                break;
            case "南瓜月勋章":
                if (!isDayTime) {
                    successful = EventAndTime.initializeEvent(EventAndTime.Events.PUMPKIN_MOON);
                }
                break;
            case "玻璃平台":
                // init blocks
                HashMap<Integer, ArrayList<Location>> blocks = new HashMap<>();
                for (int i = -15; i <= 15; i ++)
                    for (int j = -15; j <= 15; j ++) {
                        int delay = Math.abs(i) + Math.abs(j);
                        if (!blocks.containsKey(delay))
                            blocks.put(delay, new ArrayList<>());
                        blocks.get(delay).add(ply.getLocation().add(i, -1, j));
                    }
                // schedule task
                for (int delay : blocks.keySet()) {
                    Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(), () -> {
                        for (Location loc : blocks.get(delay)) {
                            Block blk = loc.getBlock();
                            if (blk.getType().isSolid()) continue;
                            // validate permission, then change block
                            if (GameplayHelper.playerBreakBlock(blk, ply, true, true, false, false))
                                blk.setType(Material.GLASS);
                        }
                    }, delay);
                }
                successful = true;
                break;
        }
        if (successful) {
            itemStack.setAmount( itemStack.getAmount() - 1 );
            applyCD(ply, useCD);
        }
        return successful;
    }
    protected static boolean playerUseCritter(Player ply, String itemName, ItemStack itemStack) {
        String critterCategory = TerrariaHelper.animalConfig.getString("animalType." + itemName);
        if (critterCategory != null) {
            CritterHelper.spawnCritter(itemName, ply.getLocation(), critterCategory);
            itemStack.setAmount( itemStack.getAmount() - 1);
            return true;
        }
        return false;
    }
    protected static boolean playerUseMiscellaneous(Player ply, String itemName) {
        switch (itemName) {
            case "钱币槽": {
                ply.openInventory(PlayerHelper.getInventory(ply, "piggyBank"));
                return true;
            }
            case "虚空袋": {
                ply.openInventory(PlayerHelper.getInventory(ply, "voidBag"));
                return true;
            }
            case "垃圾桶": {
                ply.openInventory(PlayerHelper.getInventory(ply, "trashBin"));
                return true;
            }
            case "日耀天塔柱":
            case "星璇天塔柱":
            case "星云天塔柱":
            case "星尘天塔柱":
            case "虚空天塔柱":
            case "血月天塔柱": {
                if ( ! ply.getScoreboardTags().contains("temp_useCD") ) {
                    String targetBackground = itemName.replace("天塔柱", "");
                    MetadataValue forceBackground = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_FORCED_BACKGROUND);
                    // update force background
                    if (forceBackground == null || ! forceBackground.asString().equals(targetBackground)) {
                        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_FORCED_BACKGROUND, targetBackground);
                    }
                    // remove force background
                    else {
                        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_FORCED_BACKGROUND, null);
                    }
                    applyCD(ply, 20);
                }
                return true;
            }
            case "阿瑞斯外骨骼": {
                PlayerHelper.showAresExoskeletonConfig(ply);
                return true;
            }
        }
        return false;
    }
    protected static boolean potionEffectNecessary(Player ply, String effect) {
        HashMap<String, Integer> allEffects = EntityHelper.getEffectMap(ply);
        // no need to drink the potion if the effect is already in place.
        if (allEffects.containsKey(effect)) return false;
        // no need to drink the potion if a superior version is in effect.
        for (String effectSuperior : EntityHelper.buffSuperior.getOrDefault(effect, new HashSet<>()))
            if (allEffects.containsKey(effectSuperior)) return false;
        return true;
    }
    protected static boolean playerUsePotion(Player ply, String itemType, ItemStack potion, QuickBuffType quickBuffType) {
        // to prevent vanilla items being regarded as a proper potion and consumed.
        if (itemType.length() == 0) return false;
        // potion can not be consumed when cursed. Do not delete this line as this is also triggered from key strike.
        if (EntityHelper.hasEffect(ply, "诅咒")) return false;
        boolean successful = false;
        switch (itemType) {
            case "回城药水": {
                if (quickBuffType == QuickBuffType.NONE) {
                    ply.teleport(PlayerHelper.getSpawnLocation(ply));
                    successful = true;
                }
                break;
            }
            case "虫洞药水": {
                if (quickBuffType == QuickBuffType.NONE) {
                    if (!ply.getScoreboardTags().contains("wormHolePotionUsed")) {
                        ply.sendMessage("§a请输入\"传送到 (您想传送的玩家名称)\"发送传送请求哦~");
                        ply.addScoreboardTag("wormHolePotionUsed");
                        successful = true;
                    } else {
                        ply.sendMessage("§a您上次服用的虫洞药水还未生效哦~");
                    }
                }
                break;
            }
            case "恶魔之心": {
                ConfigurationSection plyData = PlayerHelper.getPlayerDataFile(ply);
                int accessoryAmount = plyData.getInt("stats.maxAccessories", 6);
                if (accessoryAmount < 7) {
                    plyData.set("stats.maxAccessories", 7);
                    ply.sendMessage("§a使用成功，已解锁第七个饰品栏");
                    successful = true;
                }
                else {
                    ply.sendMessage("§a您已经使用过恶魔之心了");
                }
                break;
            }
            default: {
                HashSet<String> accessories = PlayerHelper.getAccessories(ply);
                ConfigurationSection potionConfig = TerrariaHelper.potionItemConfig.getConfigurationSection(itemType);
                HashMap<String, Double> buffsProvided = new HashMap<>(4);
                if (potionConfig != null) {
                    // initializes the buff provided by the potion
                    for (String node : potionConfig.getKeys(false)) {
                        buffsProvided.put(node, potionConfig.getDouble(node, 20d));
                    }
                    // determine if the potion can be used here when player demands to use a
                    switch (quickBuffType) {
                        case NONE:
                            successful = true;
                            break;
                        case HEALTH:
                            successful = buffsProvided.containsKey("health");
                            break;
                        case MANA:
                            // restoration potions are not considered mana potion.
                            successful = buffsProvided.containsKey("mana") && !buffsProvided.containsKey("health");
                            break;
                        case BUFF:
                            for (String potionEffect : buffsProvided.keySet()) {
                                // if the intention is to get some buff, potential (max) health/mana potion should not be considered.
                                if (potionEffect.equals("health") ||
                                        potionEffect.equals("mana") ||
                                        potionEffect.equals("maxHealth") ||
                                        potionEffect.equals("maxMana")) {
                                    successful = false;
                                    break;
                                } else if (potionEffectNecessary(ply, potionEffect)) {
                                    successful = true;
                                }
                            }
                            break;
                    }
                    // health potions are not supposed to be used if potion sickness debuff is active
                    if (buffsProvided.containsKey("health") && EntityHelper.hasEffect(ply, "耐药性"))
                        successful = false;
                    // items that provides permanent max health can only be used in a certain order
                    if (buffsProvided.containsKey("maxHealth") ) {
                        int currTier = PlayerHelper.getPlayerHealthTier(ply);
                        switch (itemType) {
                            case "生命水晶":
                                successful = currTier < 20;
                                break;
                            case "生命果":
                                successful = PlayerHelper.hasDefeated(ply, BossHelper.BossType.WALL_OF_FLESH) &&
                                        currTier < 40 && currTier >= 20;
                                break;
                            case "血橙":
                                successful = currTier == 40;
                                break;
                            case "奇迹之果":
                                successful = currTier == 41;
                                break;
                            case "旧神浆果":
                                successful = currTier == 42;
                                break;
                            case "龙果":
                                successful = currTier == 43;
                                break;
                            default:
                                successful = false;
                        }
                    }
                    // items that provides permanent max mana can only be used in a certain order
                    if (buffsProvided.containsKey("maxMana") ) {
                        int currTier = PlayerHelper.getPlayerManaTier(ply);
                        switch (itemType) {
                            case "魔力星":
                            case "附魔星鱼":
                                successful = currTier < 10;
                                break;
                            case "彗星碎片":
                                successful = currTier == 10;
                                break;
                            case "飘渺之核":
                                successful = currTier == 11;
                                break;
                            case "幻影之心":
                                successful = currTier == 12;
                                break;
                            default:
                                successful = false;
                        }
                    }
                    // if the potion can be used, apply health/mana/buff
                    if (successful) {
                        for (String potionInfo: buffsProvided.keySet()) {
                            double potionPotency = buffsProvided.get(potionInfo);
                            switch (potionInfo) {
                                // if the item permanently increases max health
                                case "maxHealth": {
                                    applyCD(ply, 15);
                                    // permanently increase max health
                                    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_HEALTH_TIER,
                                            PlayerHelper.getPlayerHealthTier(ply) + 1);
                                    PlayerHelper.setupAttribute(ply);
                                    PlayerHelper.heal(ply, potionPotency);
                                    break;
                                }
                                // if the item permanently increases max mana
                                case "maxMana": {
                                    applyCD(ply, 15);
                                    // permanently increase max mana
                                    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_TIER,
                                            PlayerHelper.getPlayerManaTier(ply) + 1);
                                    PlayerHelper.setupAttribute(ply);
                                    PlayerHelper.restoreMana(ply, potionPotency);
                                    break;
                                }
                                // if the potion has healing ability
                                case "health": {
                                    PlayerHelper.heal(ply, potionPotency);
                                    int duration = 1200;
                                    if (itemType.equals("恢复药水")) duration *= 0.75;
                                    if (accessories.contains("炼金石") ||
                                            accessories.contains("神话护身符") ||
                                            accessories.contains("神圣护符") ||
                                            accessories.contains("神之壁垒"))
                                        duration *= 0.75;
                                    EntityHelper.applyEffect(ply, "耐药性", duration);
                                    break;
                                }
                                // if the potion recovers mana
                                case "mana": {
                                    String debuffType = "魔力疾病";
                                    if ( accessories.contains("魔能熔毁仪") )
                                        debuffType = "魔力熔蚀";
                                    else if (accessories.contains("混乱石") )
                                        debuffType = "魔力烧蚀";
                                    PlayerHelper.restoreMana(ply, potionPotency);
                                    EntityHelper.applyEffect(ply, debuffType, 160);
                                    break;
                                }
                                // otherwise, apply the potion effect
                                default: {
                                    EntityHelper.applyEffect(ply, potionInfo, (int) potionPotency);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (successful) {
            // remove a potion item
            potion.setAmount(potion.getAmount() - 1);
            // play consumption sound
            String sound = "entity.generic.drink";
            if (!itemType.endsWith("药水"))
                sound = "entity.generic.eat";
            ply.getWorld().playSound(ply.getEyeLocation(), sound, SoundCategory.PLAYERS, 1, 1);
            // potion use cool down if the potion is being drank manually etc.
            if (quickBuffType == QuickBuffType.NONE)
                applyCD(ply, 10);
        }
        return successful;
    }
    public static boolean playerQuickUsePotion(Player ply, QuickBuffType quickBuffType) {
        if (!PlayerHelper.isProperlyPlaying(ply))
            return false;
        Inventory plyInv = ply.getInventory();
        if (quickBuffType == QuickBuffType.NONE) return false;
        boolean successfullyConsumed = false;
        // for health and mana, only one item is needed to be successfully consumed.
        boolean consumeAllOrOne = quickBuffType == QuickBuffType.BUFF;
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem != null &&
                    playerUsePotion(ply, ItemHelper.splitItemName(currItem)[1], currItem, quickBuffType))
                if (consumeAllOrOne) {
                    successfullyConsumed = true;
                } else {
                    return true;
                }
        }
        // in the player's void bag
        if (PlayerHelper.hasVoidBag(ply)) {
            Inventory voidBagInv = PlayerHelper.getInventory(ply, "voidBag");
            for (ItemStack currItem : voidBagInv.getContents()) {
                if (currItem != null &&
                        playerUsePotion(ply, ItemHelper.splitItemName(currItem)[1], currItem, quickBuffType))
                    if (consumeAllOrOne) {
                        successfullyConsumed = true;
                    } else {
                        return true;
                    }
            }
        }
        return successfullyConsumed;
    }
    // weapon use helper functions below
    protected static void handleFinishLoading(Player ply, ItemStack weapon) {
        // weapon durability
        setDurability(weapon, 1, 0);
    }
    protected static void displayLoadingProgress(Player ply, ItemStack weapon, int currLoad, int maxLoad) {
        // weapon durability
        setDurability(weapon, maxLoad, currLoad);
        // title message
        double fillProgress = (double) currLoad / maxLoad;
        String colorCode;
        switch ((int) fillProgress * 5) {
            case 1:
                colorCode = "§4";
                break;
            case 2:
                colorCode = "§c";
                break;
            case 3:
                colorCode = "§e";
                break;
            case 4:
                colorCode = "§a";
                break;
            default:
                colorCode = "§2";
        }
        int barLengthTotal = 50;
        int barLengthReady = (int) (barLengthTotal * fillProgress);
        StringBuilder infoText = new StringBuilder(colorCode + "装填:[");
        for (int i = 0; i < barLengthReady; i ++)
            infoText.append("|");
        infoText.append("§8");
        for (int i = barLengthReady; i < barLengthTotal; i ++)
            infoText.append("|");
        infoText.append(colorCode).append("]");
        PlayerHelper.sendActionBar(ply, infoText.toString());
    }
    // smart aiming: helps the player to aim with a non-homing weapon in a 3-dimension world
    // note that for getAimLoc, the function accounts for projectile-specific info, such as acceleration / gravity.
    public static Vector getPlayerAimDir(Player ply, Location startShootLoc, double projectileVelocity, String projectileType,
                                         boolean tickOffsetOrSpeed, int tickOffset) {
        // default to acceleration-aim mode
        EntityHelper.AimHelperOptions aimHelperOptions = new EntityHelper.AimHelperOptions(projectileType)
                .setAccelerationMode(Setting.getOptionBool(ply, Setting.Options.AIM_HELPER_ACCELERATION))
                .setAimMode(tickOffsetOrSpeed)
                .setTicksTotal(tickOffset)
                .setProjectileSpeed(projectileVelocity);
        // get targeted location
        Location targetLoc = getPlayerTargetLoc(ply, aimHelperOptions, true);
        // send the new direction
        Vector dir = targetLoc.subtract(startShootLoc).toVector();
        if (dir.lengthSquared() < 1e-5)
            dir = new Vector(1, 0, 0);
        return dir;
    }
    public static Location getPlayerTargetLoc(Player ply, EntityHelper.AimHelperOptions aimHelperInfo, boolean strictMode) {
        return getPlayerTargetLoc(ply, 0d, aimHelperInfo, strictMode);
    }
    public static Location getPlayerTargetLoc(Player ply, double traceDist, double entityEnlargeRadius,
                                              EntityHelper.AimHelperOptions aimHelperInfo, boolean strictMode) {
        return getPlayerTargetLoc(ply, traceDist, entityEnlargeRadius, 0d, aimHelperInfo, strictMode);
    }
    // trace dist: distance to trace into a block/entity
    // enlarge radius: max error distance allowed to target an entity that is not directly in line of sight
    // strict mode: do not target critters and entities that are strictly speaking, non-enemy
    public static Location getPlayerTargetLoc(Player ply, double blockDist,
                                              EntityHelper.AimHelperOptions aimHelperInfo, boolean strictMode) {
        return getPlayerTargetLoc(ply,
                Setting.getOptionDouble(ply, Setting.Options.AIM_HELPER_DISTANCE),
                Setting.getOptionDouble(ply, Setting.Options.AIM_HELPER_RADIUS),
                blockDist, aimHelperInfo, strictMode);
    }
    public static Location getPlayerTargetLoc(Player ply, double traceDist, double entityEnlargeRadius, double blockDist,
                                              EntityHelper.AimHelperOptions aimHelperInfo, boolean strictMode) {
        Location targetLoc = null;
        World plyWorld = ply.getWorld();
        EntityPlayer nmsPly = ((CraftPlayer) ply).getHandle();
        Vector lookDir = MathHelper.vectorFromYawPitch_approx(nmsPly.yaw, nmsPly.pitch);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_TARGET_LOC_CACHE, null);
        boolean tracedEntity = false;
        {
            Vector eyeLoc = ply.getEyeLocation().toVector();
            Vector endLoc = eyeLoc.clone().add(lookDir.clone().multiply(traceDist));
            // the block the player is looking at, if near enough
            {
                MovingObjectPosition rayTraceResult = HitEntityInfo.rayTraceBlocks(
                        plyWorld,
                        eyeLoc.clone(),
                        endLoc);
                if (rayTraceResult != null) {
                    endLoc = MathHelper.toBukkitVector(rayTraceResult.pos);
                    if (blockDist > 0d) {
                        Vector blockDistOffset = endLoc.clone().subtract(ply.getEyeLocation().toVector());
                        blockDistOffset.normalize().multiply(blockDist);
                        endLoc.subtract(blockDistOffset);
                    }
                    targetLoc = endLoc.toLocation(plyWorld);
                }
            }
            // the enemy the player is looking at, if applicable
            Vector traceStart = eyeLoc.clone();
            Vector traceEnd = endLoc.clone();
            if (eyeLoc.distanceSquared(endLoc) > entityEnlargeRadius * entityEnlargeRadius) {
                traceStart.add(lookDir.clone().multiply(entityEnlargeRadius / 2));
                traceEnd.subtract(lookDir.clone().multiply(entityEnlargeRadius / 2));
            }
            Set<HitEntityInfo> hits = HitEntityInfo.getEntitiesHit(
                    plyWorld, traceStart, traceEnd,
                    entityEnlargeRadius,
                    (net.minecraft.server.v1_12_R1.Entity target) -> EntityHelper.checkCanDamage(ply, target.getBukkitEntity(), strictMode));
            if (hits.size() > 0) {
                HitEntityInfo hitInfo = hits.iterator().next();
                Entity hitEntity = hitInfo.getHitEntity().getBukkitEntity();
                targetLoc = EntityHelper.helperAimEntity(ply, hitEntity, aimHelperInfo);
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_TARGET_LOC_CACHE, hitEntity);
                tracedEntity = true;
            }
            // if the target location is still null, that is, no block/entity being hit
            if (targetLoc == null) {
                targetLoc = ply.getEyeLocation().add(lookDir.clone().multiply(traceDist));
            }
        }
        // add random offset and set cache to the location if entity is not found
        if (!tracedEntity) {
            double randomOffset = aimHelperInfo.randomOffsetRadius, randomOffsetHalved = randomOffset / 2d;
            targetLoc.add(Math.random() * randomOffset - randomOffsetHalved,
                    Math.random() * randomOffset - randomOffsetHalved,
                    Math.random() * randomOffset - randomOffsetHalved);
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_TARGET_LOC_CACHE, targetLoc.clone());
        }
        return targetLoc;
    }
    public static Location getPlayerCachedTargetLoc(Player ply, EntityHelper.AimHelperOptions aimHelperInfo) {
        MetadataValue metadataValue = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_TARGET_LOC_CACHE);
        if (metadataValue == null)
            return ply.getEyeLocation();
        // if a location is cached
        if (metadataValue.value() instanceof Location)
            return ((Location) metadataValue.value()).clone();
        // otherwise, this must be an entity cached
        Entity targetEntity = (Entity) metadataValue.value();
        // if the entity is below bedrock layer (usually boss AI phase that should not be targeted)
        if (targetEntity.getLocation().getY() < 0d)
            return ply.getEyeLocation();
        return EntityHelper.helperAimEntity(ply, targetEntity, aimHelperInfo);
    }
    // special weapon attack helper functions below
    protected static void handleSingleZenithSwingAnimation(Player ply, HashMap<String, Double> attrMap,
                                                         Location centerLoc, Vector reachVector, Vector offsetVector,
                                                         Collection<Entity> exceptions, String color,
                                                         GenericHelper.StrikeLineOptions strikeLineInfo,
                                                         int index, int indexMax, boolean displayParticle) {
        if (index >= indexMax) return;
        double progress = (double) index / indexMax;
        double reachMulti = MathHelper.xcos_degree(180 + progress * 360);
        double offsetMulti = MathHelper.xsin_degree(180 + progress * 360);
        Location strikeLoc = centerLoc.clone()
                .add(reachVector.clone().multiply(reachMulti))
                .add(offsetVector.clone().multiply(offsetMulti));
        Vector strikeDir = strikeLoc.clone().subtract(centerLoc).toVector();
        GenericHelper.handleStrikeLine(ply, strikeLoc,
                MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                6, 0.5, "天顶剑", color, exceptions, attrMap, strikeLineInfo.setDisplayParticle(displayParticle));
        int delayAmount = ((index + 1) * 16 / indexMax) - (index * 16 / indexMax);
        if (delayAmount > 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> handleSingleZenithSwingAnimation(ply, attrMap, centerLoc, reachVector, offsetVector, exceptions, color, strikeLineInfo, index + 1, indexMax, true), delayAmount);
        } else {
            handleSingleZenithSwingAnimation(ply, attrMap, centerLoc, reachVector, offsetVector, exceptions, color, strikeLineInfo, index + 1, indexMax, false);
        }
    }
    protected static void handleSingleZenithStrike(Player ply, HashMap<String, Double> attrMap, List<String> colors, GenericHelper.StrikeLineOptions strikeLineInfo) {
        // setup vector info etc.
        EntityPlayer nmsPly = ((CraftPlayer) ply).getHandle();
        Vector lookDir = MathHelper.vectorFromYawPitch_approx(nmsPly.yaw, nmsPly.pitch);
        Location targetLoc = getPlayerTargetLoc(ply, 4,
                new EntityHelper.AimHelperOptions().setTicksTotal(8).setAimMode(true), true);
        Location centerLoc = targetLoc.clone().add(ply.getEyeLocation()).multiply(0.5);
        Vector reachVec = centerLoc.clone().subtract(ply.getEyeLocation()).toVector();
        Vector offsetVec = MathHelper.getNonZeroCrossProd(reachVec, reachVec);
        // set the display rotation of item sprite
        Vector orthogonalVec = offsetVec.getCrossProduct(reachVec);
        strikeLineInfo.particleInfo.setRightOrthogonalDir( orthogonalVec );
        // offset centerLoc backwards by 2 blocks to prevent strike line visual effect spamming the screen
        // and shorten reachVec by 1 block
        double reachLength = reachVec.length() - 1;
        if (reachVec.lengthSquared() > 1) {
            reachVec.multiply(reachLength / (reachLength + 1));
        }
        centerLoc.subtract(lookDir.clone().multiply(2));
        // offset vector should be proportionally scaled according to reach length
        // it should be around 0.3 - 0.5 of reach length
        offsetVec.normalize().multiply(reachLength * (0.3 + Math.random() * 0.2));
        // strike
//        int loopAmount = (int) (reachLength * 4);
        int loopAmount = 50;
        String color = "255|0|0";
        if (colors != null && colors.size() > 0) color = colors.get((int) (Math.random() * colors.size()));
        handleSingleZenithSwingAnimation(ply, attrMap, centerLoc, reachVec, offsetVec, new HashSet<>(), color, strikeLineInfo, 0, loopAmount, true);
    }
    protected static Location getScissorBladeLoc(Location alternativeBladeStartStrikeLoc, Vector alternativeBladeDir, Vector targetBladeDir,
                                                 double bladeSize, double intersectionLength) {
        double offsetLen = bladeSize * intersectionLength;
        return alternativeBladeStartStrikeLoc.clone()
                .add(alternativeBladeDir.clone().multiply(offsetLen))
                .subtract(targetBladeDir.clone().multiply(offsetLen));
    }
    // warning: this function modifies attrMap and damaged!
    protected static void handleMeleeSwing(Player ply, HashMap<String, Double> attrMap, Vector lookDir,
                                           Collection<Entity> damaged, ConfigurationSection weaponSection,
                                           double yawMin, double yawMax, double pitchMin, double pitchMax,
                                           String weaponType, ItemStack weaponItem, double size,
                                           boolean dirFixed, int interpolateType, int currentIndex, int maxIndex, int swingAmount) {
        if (!PlayerHelper.isProperlyPlaying(ply)) return;
        Vector aimDir = getPlayerMeleeDir(ply, size);
        final double plyYaw = MathHelper.getVectorYaw(aimDir), plyPitch = MathHelper.getVectorPitch(aimDir);
        if (!dirFixed) {
            // stab
            if (interpolateType == 0) {
                yawMin = plyYaw;
                yawMax = plyYaw;
                pitchMin = plyPitch;
                pitchMax = plyPitch;
                lookDir = MathHelper.vectorFromYawPitch_approx(yawMin, pitchMin);
            }
            // swing
            else {
                yawMin = plyYaw;
                yawMax = plyYaw;
            }
        }
        List<String> particleColors = weaponSection.getStringList("particleColor");
        String color = "102|255|255";
        GenericHelper.StrikeLineOptions strikeLineInfo =
                new GenericHelper.StrikeLineOptions()
                        .setThruWall(false);
        switch (interpolateType) {
            // if the player is dealing melee damage, display the player's weapon instead of particle
            case 0:
            case 1:
                if (EntityHelper.getDamageType(ply) == EntityHelper.DamageType.MELEE) {
                    // create a new weapon item that has the same material and name of the weapon used, but without lore
                    // it is used as a holographic display, the absence of lore prevents lag.
                    ItemStack displayItem = new ItemStack(weaponItem.getType());
                    ItemMeta displayMeta = displayItem.getItemMeta();
                    displayMeta.setDisplayName(weaponItem.getItemMeta().getDisplayName());
                    displayItem.setItemMeta(displayMeta);

                    strikeLineInfo.setParticleInfo(new GenericHelper.ParticleLineOptions()
                            .setParticleOrItem(false)
                            .setSpriteItem(displayItem)
                            .setTicksLinger(1)
                            .setRightOrthogonalDir(MathHelper.vectorFromYawPitch_approx(yawMin + 90, 0)));
                }
                break;
            // special settings for whips
            case 2:
                strikeLineInfo.setDecayCoef(weaponSection.getDouble("damageMultiPerHit", 1d))
                        .setWhipBonusDamage(weaponSection.getDouble("bonusDamage", 0d))
                        .setWhipBonusCrit(weaponSection.getDouble("bonusCrit", 0d))
                        .setParticleInfo(new GenericHelper.ParticleLineOptions()
                                .setParticleColor(particleColors)
                                .setTicksLinger(1));
        }

        double strikeRadius;
        // whip
        if (interpolateType == 2)
            strikeRadius = MELEE_MIN_STRIKE_RADIUS * 2;
        else
            strikeRadius = Math.max(size / 24d, MELEE_MIN_STRIKE_RADIUS);
        // "stab"
        if (interpolateType == 0) {
            boolean shouldStrike;
            double strikeYaw = yawMin, strikePitch = pitchMin;
            Location startStrikeLoc = ply.getLocation().add(0, 1, 0).add(lookDir);
            switch (weaponType) {
                case "星光": {
                    shouldStrike = currentIndex % 2 == 0;
                    if (shouldStrike) {
                        // prevent DPS loss due to damage invincibility frame
                        damaged = new ArrayList<>();
                        color = particleColors.get((int) (Math.random() * particleColors.size()));
                        if (currentIndex > 0) ply.getWorld().playSound(ply.getLocation(), SOUND_GENERIC_SWING, SoundCategory.PLAYERS, 1f, 1f);
                        strikeYaw += Math.random() * 30 - 15;
                        strikePitch += Math.random() * 30 - 15;
                    }
                    break;
                }
                case "水电剑":
                case "潜渊震荡者": {
                    shouldStrike = currentIndex <= 5;
                    if (shouldStrike) {
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    Vector projVel = MathHelper.randomVector();
                                    projVel.multiply(0.2);
                                    EntityHelper.spawnProjectile(ply, hitLoc, projVel,
                                            attrMap, EntityHelper.DamageType.MELEE, "电火花");
                                });
                    }
                    break;
                }
                case "卢克雷西亚": {
                    shouldStrike = currentIndex <= 2;
                    if (shouldStrike) {
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    // melee invulnerability tick
                                    int invulnerabilityTicks = 3;
                                    EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                            EntityHelper.getInvulnerabilityTickName(EntityHelper.DamageType.MELEE),
                                            invulnerabilityTicks);
                                    EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                            EntityHelper.getInvulnerabilityTickName(EntityHelper.DamageType.TRUE_MELEE),
                                            invulnerabilityTicks);
                                });
                    }
                    break;
                }
                case "伽利略短剑": {
                    shouldStrike = currentIndex <= 5;
                    if (shouldStrike) {
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    // predict enemy location
                                    EntityHelper.AimHelperOptions aimHelper = new EntityHelper.AimHelperOptions()
                                            .setAimMode(true)
                                            .setTicksTotal(8)
                                            .setRandomOffsetRadius(1.5);
                                    Location predictedLoc = EntityHelper.helperAimEntity(ply, entityHit, aimHelper);
                                    Vector projVel = MathHelper.vectorFromYawPitch_approx(
                                            Math.random() * 360, 70 + Math.random() * 20);
                                    projVel.multiply(24);
                                    predictedLoc.subtract(projVel);
                                    projVel.multiply(1/8d);
                                    EntityHelper.spawnProjectile(ply, predictedLoc, projVel, attrMap,
                                            EntityHelper.DamageType.MELEE, "伽利略小行星");
                                });
                    }
                    break;
                }
                case "硫磺火矛": {
                    shouldStrike = currentIndex <= 5;

                    if (shouldStrike) {
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    // explosion
                                    EntityHelper.handleEntityExplode(ply, 1, new ArrayList<>(), hitLoc,
                                            1, 5);
                                    // projectile
                                    for (int i = (int) (Math.random() * 3); i < 3; i++) {
                                        Vector projVel = MathHelper.randomVector();
                                        projVel.multiply(0.35);
                                        EntityHelper.spawnProjectile(ply, hitLoc, projVel,
                                                attrMap, EntityHelper.DamageType.MELEE, "硫磺火间歇泉");
                                    }
                                });
                    }
                    break;
                }
                case "火山之矛": {
                    shouldStrike = currentIndex <= 5;
                    if (shouldStrike) {
                        double critRate = (attrMap.getOrDefault("crit", 4d) +
                                attrMap.getOrDefault("critMelee", 0d) +
                                attrMap.getOrDefault("critTrueMelee", 0d)) / 100;
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    // explosion
                                    EntityHelper.handleEntityExplode(ply, 1, new ArrayList<>(), hitLoc,
                                            1, 5);
                                    // projectile
                                    if (Math.random() < critRate)
                                        for (int i = (int) (Math.random() * 2); i < 2; i++) {
                                            Vector projVel = MathHelper.randomVector();
                                            projVel.multiply(1);
                                            EntityHelper.spawnProjectile(ply, hitLoc, projVel,
                                                    attrMap, EntityHelper.DamageType.MELEE, "追踪火球");
                                        }
                                });
                    }
                    break;
                }
                case "瘟疫长枪": {
                    shouldStrike = currentIndex <= 5;

                    if (shouldStrike) {
                        HashMap<String, Double> plagueSeekerAttrMap = (HashMap<String, Double>) attrMap.clone();
                        plagueSeekerAttrMap.put("damage", plagueSeekerAttrMap.get("damage") * 0.4);
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    // plague seekers
                                    for (int i = 0; i < 5; i++) {
                                        Vector projVel = MathHelper.randomVector();
                                        projVel.multiply(1.5);
                                        EntityHelper.spawnProjectile(ply, hitLoc, projVel,
                                                plagueSeekerAttrMap, EntityHelper.DamageType.MELEE, "瘟疫搜寻者");
                                    }
                                });
                    }
                    break;
                }
                case "磁能分割刀": {
                    shouldStrike = currentIndex <= 5;
                    if (shouldStrike) {
                        Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 4, "能量脉冲", false, 0);
                        projVel.normalize().multiply(4);

                        HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                        projAttrMap.put("damage", projAttrMap.get("damage") * 0.65);

                        EntityHelper.spawnProjectile(ply, projVel,
                                projAttrMap, EntityHelper.DamageType.MELEE, "能量脉冲");
                    }
                    break;
                }
                case "幻星矛": {
                    shouldStrike = currentIndex <= 5;
                    if (shouldStrike) {
                        double critRate = (attrMap.getOrDefault("crit", 4d) +
                                attrMap.getOrDefault("critMelee", 0d) +
                                attrMap.getOrDefault("critTrueMelee", 0d)) / 100;
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    // comet
                                    if (Math.random() < critRate) {
                                        Vector projVel = MathHelper.randomVector();
                                        Vector locOffset = projVel.clone();
                                        projVel.multiply(1.5);
                                        locOffset.multiply(30);
                                        EntityHelper.spawnProjectile(ply, hitLoc.subtract(locOffset), projVel,
                                                attrMap, EntityHelper.DamageType.MELEE, "彗星");
                                    }
                                });
                    }
                    break;
                }
                case "镀金鸟喙": {
                    shouldStrike = currentIndex <= 5;
                    if (shouldStrike) {
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> PlayerHelper.heal(ply, 3));
                    }
                    break;
                }
                case "女妖之爪": {
                    shouldStrike = currentIndex <= 5;
                    if (shouldStrike) {
                        Location shootLoc = ply.getEyeLocation().add( ply.getLocation().getDirection().multiply(size) );

                        Vector projVel = getPlayerAimDir(ply, shootLoc, 2.5, "灵魂鬼爪", false, 0);
                        projVel.normalize().multiply(2.5);

                        EntityHelper.spawnProjectile(ply, shootLoc, projVel,
                                attrMap, EntityHelper.DamageType.MELEE, "灵魂鬼爪");
                    }
                    break;
                }
                case "钨钢螺丝刀_RIGHT_CLICK": {
                    shouldStrike = currentIndex <= 3;
                    if (shouldStrike) {
                        double finalStrikeYaw = strikeYaw;
                        double finalStrikePitch = strikePitch;
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    EntityHelper.getAttrMap(entityHit).put("damageMeleeMulti", 10.0);
                                    EntityHelper.getAttrMap(entityHit).put("crit", 100.0);
                                    Vector projVel = MathHelper.vectorFromYawPitch_approx(finalStrikeYaw, finalStrikePitch);
                                    projVel.multiply(3);
                                    entityHit.setVelocity(projVel);
                                    hitLoc.getWorld().playSound(hitLoc, SOUND_ARK_PARRY, SoundCategory.PLAYERS, 1f, 1);
                                })
                                .setShouldDamageFunction((entity) -> entity.getScoreboardTags().contains("isWulfrumScrew"))
                                .setLingerDelay(5);
                    }
                    break;
                }
                case "旧神之誓约_RIGHT_CLICK": {
                    strikeRadius = 0.5;
                    shouldStrike = currentIndex * 1.75 < maxIndex;
                    if (shouldStrike) {
                        // init dash velocity
                        if (currentIndex == 0) {
                            Vector plyVel = lookDir.clone();
                            plyVel.multiply(2);
                            EntityHelper.setVelocity(ply, plyVel);
                        }
                        // hitting enemies will lock the player in a proper place
                        int finalCurrentIndex = currentIndex;
                        Location finalStartStrikeLoc = startStrikeLoc;
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    if (hitIndex == 1) {
                                        ply.setFallDistance(0f);
                                        // repels the player when the strike would finish soon
                                        if ( ( (finalCurrentIndex + 3) * 1.75) >= maxIndex) {
                                            Vector plyVel = MathHelper.getDirection(hitLoc, finalStartStrikeLoc, 2);
                                            EntityHelper.setVelocity(ply, plyVel);
                                        }
                                        // otherwise, keep a safe distance from enemy
                                        else {
                                            Vector offsetDir = MathHelper.getDirection(hitLoc, finalStartStrikeLoc, 5.5);
                                            Vector plyVel = MathHelper.getDirection(
                                                    finalStartStrikeLoc, hitLoc.add(offsetDir), 1.75, true);
                                            EntityHelper.setVelocity(ply, plyVel);
                                        }
                                    }
                                })
                                .setDamageCD(1);
                    }
                    break;
                }
                case "破碎方舟_RIGHT_CLICK":
                case "远古方舟_RIGHT_CLICK":
                case "元素方舟_RIGHT_CLICK":
                case "鸿蒙方舟_RIGHT_CLICK": {
                    double scissorsConnLen = 0.3333;
                    String parryCDScoreboardTag = "temp_parryCD";
                    shouldStrike = ! ply.getScoreboardTags().contains(parryCDScoreboardTag);
                    if (shouldStrike) {
                        // parry
                        int invulnerabilityTicks, coolDown;
                        switch (weaponType) {
                            case "破碎方舟_RIGHT_CLICK":
                                strikeRadius = 0.4;
                                invulnerabilityTicks = 12;
                                coolDown = 160;
                                break;
                            case "远古方舟_RIGHT_CLICK":
                                strikeRadius = 0.55;
                                invulnerabilityTicks = 13;
                                coolDown = 140;
                                break;
                            case "元素方舟_RIGHT_CLICK":
                            case "鸿蒙方舟_RIGHT_CLICK":
                                boolean isArkOfElements = weaponType.equals("元素方舟_RIGHT_CLICK");
                                strikeRadius = isArkOfElements ? 0.65 : 0.75;
                                invulnerabilityTicks = isArkOfElements ? 14 : 15;
                                coolDown = 120;
                                break;
                            default:
                                invulnerabilityTicks = 10;
                                coolDown = 100;
                        }
                        // parry function
                        Collection<Entity> finalDamagedList = damaged;
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    // hit projectile: respective invulnerability tick
                                    if (entityHit instanceof Projectile) {
                                        EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                                EntityHelper.getInvulnerabilityTickName(EntityHelper.getDamageType(entityHit)),
                                                invulnerabilityTicks);
                                    }
                                    // hit entity: apply melee invulnerability tick
                                    else {
                                        EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                                EntityHelper.getInvulnerabilityTickName(EntityHelper.DamageType.MELEE),
                                                invulnerabilityTicks);
                                        EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                                EntityHelper.getInvulnerabilityTickName(EntityHelper.DamageType.TRUE_MELEE),
                                                invulnerabilityTicks);
                                    }
                                    // recharge
                                    if (hitIndex == 1) {
                                        setDurability(weaponItem, 10, 10);
                                        ply.playSound(ply.getEyeLocation(), SOUND_ARK_PARRY, SoundCategory.PLAYERS, 2f, 2f);
                                    }
                                })
                                // should not damage an enemy twice
                                .setDamageCD(30)
                                // should parry entities that can damage player (and not in damage CD list)
                                .setShouldDamageFunction( (e) ->
                                        EntityHelper.checkCanDamage(e, ply, true) && !finalDamagedList.contains(e));
                        // render scissors
                        switch (weaponType) {
                            case "元素方舟_RIGHT_CLICK":
                            case "鸿蒙方舟_RIGHT_CLICK": {
                                boolean isArkOfElements = weaponType.equals("元素方舟_RIGHT_CLICK");
                                int cutIndex = maxIndex - 2;
                                double rotationOffset;
                                if (currentIndex >= cutIndex) {
                                    // play cut sound
                                    if (currentIndex == cutIndex) {
                                        ply.getWorld().playSound(ply.getLocation(), SOUND_ARK_SCISSOR_CUT, SoundCategory.PLAYERS, 5f, 1f);
                                    }
                                    rotationOffset = 0;
                                }
                                else {
                                    rotationOffset = 45d - 45d * (currentIndex) / cutIndex;
                                }
                                // second scissor part handling
                                ItemStack scissorItem = strikeLineInfo.particleInfo.spriteItem;
                                {
                                    // sprite for blade
                                    {
                                        ItemMeta newMeta = scissorItem.getItemMeta();
                                        newMeta.setDisplayName( (isArkOfElements ? "元素方舟" : "鸿蒙方舟") + "1");
                                        scissorItem.setItemMeta(newMeta);
                                        strikeLineInfo.particleInfo.setSpriteItem(scissorItem);
                                    }
                                    // strike
                                    double alternativeStrikePitch = strikePitch + rotationOffset;
                                    Location bladeLoc = getScissorBladeLoc(startStrikeLoc,
                                            MathHelper.vectorFromYawPitch_approx(strikeYaw, strikePitch),
                                            MathHelper.vectorFromYawPitch_approx(strikeYaw, alternativeStrikePitch),
                                            size, scissorsConnLen);
                                    GenericHelper.handleStrikeLine(ply, bladeLoc,
                                            strikeYaw, alternativeStrikePitch,
                                            size, strikeRadius, weaponType, color, damaged, attrMap, strikeLineInfo);
                                }
                                // damage-handling scissor part orientation
                                // sprite for blade
                                {
                                    strikeLineInfo = strikeLineInfo.clone();
                                    scissorItem = strikeLineInfo.particleInfo.spriteItem;
                                    ItemMeta newMeta = scissorItem.getItemMeta();
                                    newMeta.setDisplayName( (isArkOfElements ? "元素方舟" : "鸿蒙方舟") + "2");
                                    scissorItem.setItemMeta(newMeta);
                                    strikeLineInfo.particleInfo.setSpriteItem(scissorItem);
                                }
                                double strikePitchOriginal = strikePitch;
                                strikePitch -= rotationOffset;
                                startStrikeLoc = getScissorBladeLoc(startStrikeLoc,
                                        MathHelper.vectorFromYawPitch_approx(strikeYaw, strikePitchOriginal),
                                        MathHelper.vectorFromYawPitch_approx(strikeYaw, strikePitch),
                                        size, scissorsConnLen);
                            }
                            break;
                        }
                        // handle cool down after fully finishing this swing
                        if (currentIndex == maxIndex)
                            EntityHelper.handleEntityTemporaryScoreboardTag(ply, parryCDScoreboardTag, coolDown);
                    }
                    // reset item use CD if the parry is still in cool down
                    else {
                        applyCD(ply, 1);
                    }
                    break;
                }
                case "残缺环境刃[伟岸之枯涸]":
                case "环境之刃[伟岸之枯涸]": {
                    shouldStrike = true;
                    strikeYaw = plyYaw;
                    strikePitch = plyPitch;
                    // hitting block will knock back the user
                    Location finalStartStrikeLoc1 = startStrikeLoc;
                    strikeLineInfo
                            .setBlockHitFunction((hitLoc, movingObjectPosition) -> {
                                if (!ply.isOnGround()) {
                                    ply.setFallDistance(0f);
                                    // repels the player
                                    Vector plyVel = MathHelper.getDirection(hitLoc, finalStartStrikeLoc1, 1.5);
                                    EntityHelper.setVelocity(ply, plyVel);
                                }
                            });
                    break;
                }
                case "残缺环境刃[反抗之衰朽]": {
                    strikeRadius = 0.5;
                    shouldStrike = currentIndex * 2 < maxIndex;
                    if (shouldStrike) {
                        // init dash velocity
                        if (currentIndex == 0 && ply.isOnGround()) {
                            Vector plyVel = lookDir.clone();
                            plyVel.multiply(1.5);
                            EntityHelper.setVelocity(ply, plyVel);
                        }
                        // hitting enemies will knock back the player
                        Location finalStartStrikeLoc2 = startStrikeLoc;
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    if (hitIndex == 1) {
                                        ply.setFallDistance(0f);
                                        // repels the player
                                        Vector plyVel = MathHelper.getDirection(hitLoc, finalStartStrikeLoc2, 0.75);
                                        EntityHelper.setVelocity(ply, plyVel);
                                        // heal for a small amount
                                        PlayerHelper.heal(ply, 3, false);
                                    }
                                })
                                .setLingerDelay(1);
                    }
                    break;
                }
                case "环境之刃[反抗之衰朽]": {
                    strikeRadius = 0.5;
                    shouldStrike = currentIndex < 8;
                    if (shouldStrike) {
                        // init dash velocity
                        if (currentIndex == 0) {
                            Vector plyVel = lookDir.clone();
                            plyVel.multiply(1.75);
                            EntityHelper.setVelocity(ply, plyVel);
                        }
                        // hitting enemies will knock back the player
                        int overrideCD = 12 - currentIndex;
                        Location finalStartStrikeLoc3 = startStrikeLoc;
                        strikeLineInfo
                                .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                    if (hitIndex == 1) {
                                        ply.setFallDistance(0f);
                                        // repels the player
                                        Vector plyVel = MathHelper.getDirection(hitLoc, finalStartStrikeLoc3, 1);
                                        EntityHelper.setVelocity(ply, plyVel);
                                        // heal for a small amount
                                        PlayerHelper.heal(ply, 4, false);
                                        applyCD(ply, overrideCD);
                                    }
                                })
                                .setLingerDelay(1);
                    }
                    break;
                }
                case "真·环境之刃[变幻之潮]":
                case "环境之刃[嫌恶之永存]": {
                    shouldStrike = true;
                    // add a charge
                    if (currentIndex == 0) {
                        int currCharge = getDurability(weaponItem, 8);
                        if (currCharge < 8)
                            setDurability(weaponItem, 8, currCharge + 1);
                    }
                    break;
                }
                case "环境之刃[嫌恶之永存]_RIGHT_CLICK": {
                    strikeRadius = 0.5;
                    shouldStrike = getDurability(weaponItem, 8) == 8;
                    if (shouldStrike) {
                        // dash
                        {
                            Vector plyVel = MathHelper.vectorFromYawPitch_approx(strikeYaw, strikePitch);
                            plyVel.multiply(2);
                            EntityHelper.setVelocity(ply, plyVel);
                        }
                        // remove all stacks on last blow
                        if (currentIndex == maxIndex) {
                            setDurability(weaponItem, 8, 0);
                        }

                        // explosion
                        strikeLineInfo
                                .setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                                    EntityHelper.handleEntityExplode(ply, 2.5, new ArrayList<>(), hitLoc);
                                });
                        if (currentIndex % 2 == 0)
                            strikeLineInfo
                                    .setBlockHitFunction( (hitLoc, movingObjectPosition) -> {
                                        EntityHelper.handleEntityExplode(ply, 3, new ArrayList<>(), hitLoc);
                                    });
                    }
                    // prevent dash
                    else {
                        applyCD(ply, 5);
                        currentIndex = maxIndex;
                    }
                    break;
                }
                case "真·环境之刃[变幻之潮]_RIGHT_CLICK": {
                    strikeRadius = 0.5;
                    shouldStrike = getDurability(weaponItem, 8) == 8;
                    if (shouldStrike) {
                        // dash
                        {
                            Vector plyVel = MathHelper.vectorFromYawPitch_approx(strikeYaw, strikePitch);
                            plyVel.multiply(2);
                            EntityHelper.setVelocity(ply, plyVel);
                        }
                        // remove all stacks on last blow
                        if (currentIndex == maxIndex) {
                            setDurability(weaponItem, 8, 0);
                        }

                        // no direct damage
                        strikeLineInfo.setShouldDamageFunction( (entity) -> false);
                        // explosion
                        if (currentIndex % 3 == 0)
                            strikeLineInfo
                                    .setBlockHitFunction( (hitLoc, movingObjectPosition) -> {
                                        ArrayList<Entity> blastDamageCD = new ArrayList<>();
                                        EntityHelper.handleEntityExplode(ply, 4, blastDamageCD, hitLoc);

                                        for (int i = 5; i <= 20; i += 5) {
                                            Location currExplodeLoc = hitLoc.clone();
                                            switch (movingObjectPosition.direction) {
                                                case UP:
                                                    currExplodeLoc.add(0, i, 0);
                                                    break;
                                                case DOWN:
                                                    currExplodeLoc.add(0, -i, 0);
                                                    break;
                                                case SOUTH:
                                                    currExplodeLoc.add(0, 0, i);
                                                    break;
                                                case NORTH:
                                                    currExplodeLoc.add(0, 0, -i);
                                                    break;
                                                case EAST:
                                                    currExplodeLoc.add(i, 0, 0);
                                                    break;
                                                case WEST:
                                                    currExplodeLoc.add(-i, 0, 0);
                                                    break;
                                            }
                                            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                                                    () -> EntityHelper.handleEntityExplode(ply, 4, blastDamageCD, currExplodeLoc),
                                                    i);
                                        }
                                    });
                    }
                    // prevent dash
                    else {
                        applyCD(ply, 5);
                        currentIndex = maxIndex;
                    }
                    break;
                }
                case "真·环境之刃[血腾之愤]": {
                    AtomicInteger durability = new AtomicInteger(getDurability(weaponItem, 100));
                    double durabilityRatio = durability.get() / 100d;
                    strikeRadius = 0.5 + 0.5 * durabilityRatio;
                    size = 6 + 4 * durabilityRatio;
                    shouldStrike = true;

                    // durability tweak
                    if (currentIndex % 5 == 0)
                        durability.getAndDecrement();
                    // save durability
                    setDurability(weaponItem, 100, durability.get());
                    Location finalStartStrikeLoc4 = startStrikeLoc;
                    strikeLineInfo
                            .setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                                if (hitIdx == 1) {
                                    setDurability(weaponItem, 100, durability.get() + 15);
                                }
                            })
                            .setBlockHitFunction((hitLoc, movingObjectPosition) -> {
                                if (!ply.isOnGround() && ply.isSneaking()) {
                                    ply.setFallDistance(0f);
                                    // repels the player
                                    Vector plyVel = MathHelper.getDirection(hitLoc, finalStartStrikeLoc4, 1.5);
                                    EntityHelper.setVelocity(ply, plyVel);
                                }
                            });
                    break;
                }
                case "真·环境之刃[血腾之愤]_RIGHT_CLICK": {
                    AtomicInteger durability = new AtomicInteger(getDurability(weaponItem, 100));
                    strikeRadius = 0.75;
                    size = 7.5;
                    shouldStrike = currentIndex < 8;

                    // dash attempt
                    if (currentIndex == 0) {
                        if (durability.get() >= 50) {
                            Vector plyVel = lookDir.clone();
                            plyVel.multiply(2.25);
                            EntityHelper.setVelocity(ply, plyVel);
                        }
                        // save durability
                        durability.addAndGet(-12);
                        setDurability(weaponItem, 100, durability.get());
                    }
                    // on-hit functions
                    int overrideCD = 8 - currentIndex;
                    Location finalStartStrikeLoc5 = startStrikeLoc;
                    strikeLineInfo
                            .setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                                if (hitIdx == 1) {
                                    // save durability
                                    setDurability(weaponItem, 100, durability.get() + 10);

                                    ply.setFallDistance(0f);
                                    // repels the player
                                    Vector plyVel = MathHelper.getDirection(hitLoc, finalStartStrikeLoc5, 1.5);
                                    EntityHelper.setVelocity(ply, plyVel);
                                    // heal for a small amount
                                    PlayerHelper.heal(ply, 8, false);
                                    applyCD(ply, overrideCD);
                                }
                            });

                    break;
                }
                case "银河[北斗之眸]": {
                    AtomicInteger durability = new AtomicInteger(getDurability(weaponItem, 100));
                    double durabilityRatio = durability.get() / 100d;
                    strikeRadius = 0.75 * (1 + durabilityRatio);
                    size = 10 + 5 * durabilityRatio;
                    shouldStrike = true;

                    // passive projectile
                    if (currentIndex == 0) {
                        HashMap<String, Double> passiveProjAttrMap = (HashMap<String, Double>) attrMap.clone();
                        passiveProjAttrMap.put("damage", 300d);
                        // projectile
                        Vector projVel = MathHelper.randomVector();
                        projVel.multiply(2.5);
                        EntityHelper.spawnProjectile(ply, projVel, passiveProjAttrMap,
                                EntityHelper.DamageType.MELEE, "巨蟹之礼星环");
                    }
                    // durability tweak
                    if (currentIndex % 2 == 0)
                        setDurability(weaponItem, 100, durability.incrementAndGet());
                    strikeLineInfo
                            .setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                                if (hitIdx == 1) {
                                    setDurability(weaponItem, 100, durability.get() + 5);
                                }
                            });
                    break;
                }
                case "银河[北斗之眸]_RIGHT_CLICK": {
                    AtomicInteger durability = new AtomicInteger(getDurability(weaponItem, 100));
                    double durabilityRatio = durability.get() / 100d;
                    strikeRadius = 0.75 * (1 + durabilityRatio);
                    size = 10 + 5 * durabilityRatio;
                    shouldStrike = currentIndex < 10;

                    // passive projectile
                    if (currentIndex == 0) {
                        HashMap<String, Double> passiveProjAttrMap = (HashMap<String, Double>) attrMap.clone();
                        passiveProjAttrMap.put("damage", 300d);
                        // projectile
                        Vector projVel = MathHelper.randomVector();
                        projVel.multiply(2.5);
                        EntityHelper.spawnProjectile(ply, projVel, passiveProjAttrMap,
                                EntityHelper.DamageType.MELEE, "巨蟹之礼星环");
                    }
                    // dash attempt
                    if (currentIndex == 0) {
                        if (durability.get() >= 25) {
                            Vector plyVel = lookDir.clone();
                            plyVel.multiply(3);
                            EntityHelper.setVelocity(ply, plyVel);
                        }
                        // save durability
                        durability.addAndGet(-25);
                        setDurability(weaponItem, 100, durability.get());
                    }
                    // on-hit functions
                    int overrideCD = 10 - currentIndex;
                    Location finalStartStrikeLoc5 = startStrikeLoc;
                    strikeLineInfo
                            .setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                                if (hitIdx == 1) {
                                    // save durability
                                    setDurability(weaponItem, 100, durability.get() + 30);

                                    ply.setFallDistance(0f);
                                    // repels the player
                                    Vector plyVel = MathHelper.getDirection(hitLoc, finalStartStrikeLoc5, 1.5);
                                    EntityHelper.setVelocity(ply, plyVel);
                                    // heal for a small amount
                                    PlayerHelper.heal(ply, 10, false);
                                    applyCD(ply, overrideCD);
                                }
                            });

                    break;
                }
                case "银河[仙女之跃]": {
                    shouldStrike = true;
                    AtomicInteger durability = new AtomicInteger(getDurability(weaponItem, 100));

                    // passive projectile
                    if (currentIndex == 0) {
                        HashMap<String, Double> passiveProjAttrMap = (HashMap<String, Double>) attrMap.clone();
                        passiveProjAttrMap.put("damage", 300d);
                        // projectile
                        Vector projVel = MathHelper.randomVector();
                        projVel.multiply(2.5);
                        EntityHelper.spawnProjectile(ply, projVel, passiveProjAttrMap,
                                EntityHelper.DamageType.MELEE, "巨蟹之礼星环");
                    }
                    // deal low damage during charging phase
                    attrMap.put("damage", 1000d);
                    // recharge attack
                    setDurability(weaponItem, 100, durability.incrementAndGet());
                    break;
                }
                case "银河[仙女之跃]_RIGHT_CLICK": {
                    shouldStrike = true;
                    AtomicInteger durability = new AtomicInteger(getDurability(weaponItem, 100));
                    double durabilityRatio = durability.get() / 100d;
                    // large blade

                    // no dash if not sufficiently charged
                    if (durability.get() < 50) {
                        applyCD(ply, 1);
                        return;
                    }
                    // passive projectile
                    if (currentIndex == 0) {
                        HashMap<String, Double> passiveProjAttrMap = (HashMap<String, Double>) attrMap.clone();
                        passiveProjAttrMap.put("damage", 300d);
                        // projectile
                        Vector projVel = MathHelper.randomVector();
                        projVel.multiply(2.5);
                        EntityHelper.spawnProjectile(ply, projVel, passiveProjAttrMap,
                                EntityHelper.DamageType.MELEE, "巨蟹之礼星环");
                    }

                    // dash
                    // damage and blade size increased proportional to charge
                    if (currentIndex == 0) {
                        strikeRadius = 0.5 + durabilityRatio;
                        size = 8 + 4 * durabilityRatio;
                        attrMap.put("damage", attrMap.get("damage") * 6 * durabilityRatio);
                    }
                    // reset the durability after finishing the dash
                    else if (currentIndex == maxIndex) {
                        setDurability(weaponItem, 100, 0);
                    }

                    // maintain the dash
                    Vector plyVel = lookDir.clone();
                    plyVel.multiply(3);
                    EntityHelper.setVelocity(ply, plyVel);

                    // strike options
                    Vector finalLookDir1 = lookDir.clone();
                    Location traceStartLoc = startStrikeLoc.subtract(lookDir.clone().multiply(3));
                    strikeLineInfo
                            // invulnerability tick on-hit
                            .setDamagedFunction((hitIdx, hitEntity, hitLoc) -> {
                                int invulnerabilityTicks = 5;
                                EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                        EntityHelper.getInvulnerabilityTickName(EntityHelper.DamageType.MELEE),
                                        invulnerabilityTicks);
                                EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                        EntityHelper.getInvulnerabilityTickName(EntityHelper.DamageType.TRUE_MELEE),
                                        invulnerabilityTicks);
                                EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                        EntityHelper.getInvulnerabilityTickName(EntityHelper.DamageType.ARROW),
                                        invulnerabilityTicks);
                                EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                        EntityHelper.getInvulnerabilityTickName(EntityHelper.DamageType.MAGIC),
                                        invulnerabilityTicks);
                            })
                            // releases all charges & create massive explosion after colliding with terrain
                            .setBlockHitFunction((hitLoc, movingObjectPosition) -> {
                                // reset fall distance
                                ply.setFallDistance(0f);
                                // reset weapon charge
                                setDurability(weaponItem, 100, 0);
                                // explosion
                                int explosionAmount = (int) (12.5 * durabilityRatio);
                                for (int explosionIdx = 0; explosionIdx < explosionAmount; explosionIdx ++) {
                                    ArrayList<Entity> blastDamageCD = new ArrayList<>();
                                    Vector traceDir = MathHelper.randomVector();
                                    // continue the loop if the vector is not in the right direction (within 60 degrees)
                                    if ( (MathHelper.getAngleRadian(traceDir, finalLookDir1)) > (Math.PI / 3) ) {
                                        explosionIdx --;
                                        continue;
                                    }
                                    // trace the block to spawn a pillar
                                    Location traceEndLoc = traceStartLoc.clone().add(traceDir.clone().multiply(24));
                                    MovingObjectPosition traceResult = HitEntityInfo.rayTraceBlocks(
                                            hitLoc.getWorld(), traceStartLoc.toVector(), traceEndLoc.toVector());
                                    // continue the loop if the trace did not land
                                    if (traceResult == null) {
                                        explosionIdx --;
                                        continue;
                                    }
                                    // explosion pillar
                                    traceDir.multiply(5);
                                    switch (traceResult.direction) {
                                        case UP:
                                        case DOWN:
                                            traceDir.setY(traceDir.getY() * -1);
                                            break;
                                        case EAST:
                                        case WEST:
                                            traceDir.setX(traceDir.getX() * -1);
                                            break;
                                        case SOUTH:
                                        case NORTH:
                                            traceDir.setZ(traceDir.getZ() * -1);
                                            break;
                                    }
                                    Location currExplodeLoc = MathHelper.toBukkitVector(traceResult.pos)
                                            .toLocation(hitLoc.getWorld());
                                    for (int i = 0; i <= 5; i ++) {
                                        Location cachedLoc = currExplodeLoc.clone();
                                        if (i <= 1) {
                                            // projectile
                                            EntityHelper.spawnProjectile(ply, cachedLoc, traceDir, EntityHelper.getAttrMap(ply),
                                                    EntityHelper.DamageType.MELEE, "银河之雷");
                                        }
                                        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                                                () -> {
                                                    // explosion
                                                    EntityHelper.handleEntityExplode(ply, 4, blastDamageCD, cachedLoc);
                                                },
                                                i * 4);
                                        currExplodeLoc.add(traceDir);
                                    }
                                }
                            });
                    break;
                }
                case "焚灭天惩": {
                    shouldStrike = true;
                    // update strike direction; this weapon is held like a wand
                    strikePitch = -45;
                    // summons meteors from the sky
                    int hitDelay = 5 + (int) (Math.random() * 5);
                    Location targetLoc = getPlayerTargetLoc(ply,
                            new EntityHelper.AimHelperOptions()
                                    .setAimMode(true)
                                    .setTicksTotal(hitDelay)
                                    .setRandomOffsetRadius(5), true);
                    Vector projVel = MathHelper.vectorFromYawPitch_approx(Math.random() * 360d, 70 + Math.random() * 30d);
                    projVel.multiply(2.5 + Math.random());
                    targetLoc.subtract(projVel.clone().multiply(hitDelay));

                    EntityHelper.spawnProjectile(ply, targetLoc, projVel, attrMap, EntityHelper.DamageType.MELEE, "焚灭天惩燃烧陨石");
                    break;
                }
                case "光棱破碎者": {
                    shouldStrike = true;
                    double strikeRatio = (double) currentIndex / maxIndex;
                    // interpolate yaw and pitch
                    {
                        double interpolateOffset[] = GenericHelper.getDirectionInterpolateOffset(
                                strikeYaw, strikePitch, plyYaw, plyPitch, 0.4);
                        double interpolatedDir[] = GenericHelper.interpolateDirection(
                                strikeYaw, strikePitch, interpolateOffset[0], interpolateOffset[1]);
                        strikeYaw = interpolatedDir[0];
                        strikePitch = interpolatedDir[1];
                    }
                    // shoot rainbow instead of normal blade on later swing animation
                    int swingIdx = swingAmount % 32;
                    if (swingIdx >= 8) {
                        double interpolateRatio = ( (swingIdx - 8) + ((double) currentIndex / maxIndex) ) / 32.000001d;
                        ArrayList<Color> rainbowColor = new ArrayList<>();
                        rainbowColor.add(Color.fromRGB(255, 0, 0));
                        rainbowColor.add(Color.fromRGB(255, 255, 0));
                        rainbowColor.add(Color.fromRGB(0, 255, 0));
                        rainbowColor.add(Color.fromRGB(0, 255, 255));
                        rainbowColor.add(Color.fromRGB(0, 0, 255));
                        rainbowColor.add(Color.fromRGB(255, 0, 255));
                        Color particleColor = GenericHelper.getInterpolateColor(interpolateRatio, rainbowColor);
                        size = 64;
                        strikeRadius = 1.5;
                        strikeLineInfo.particleInfo
                                .setParticleOrItem(true)
                                .setParticleColor(
                                        particleColor.getRed() + "|" + particleColor.getGreen() + "|" + particleColor.getBlue())
                                .setTicksLinger(1)
                                .setIntensityMulti(0.2);
                        strikeLineInfo
                                .setThruWall(false)
                                .setDamageCD(15);
                    }
                    break;
                }
                case "星流之刃_RIGHT_CLICK": {
                    double dashVel = 2.25;
                    shouldStrike = true;
                    int currCharge = getDurability(weaponItem, 8);
                    // cancel attack if charge remains
                    if (currentIndex == 0 && currCharge > 0) {
                        applyCD(ply, 2);
                        currentIndex = maxIndex;
                        return;
                    }
                    // dash velocity as well as base charge gain
                    Vector plyVel;
                    if (currentIndex == 0) {
                        // base charge gain (4 stacks out of 5)
                        setDurability(weaponItem, 8, 7);
                        plyVel = lookDir.clone();
                    }
                    else
                        plyVel = EntityHelper.getRawVelocity(ply).normalize();
                    // end dash earlier after recoil (hitting entity); do not update velocity
                    if (plyVel.dot(lookDir) < 0) {
                        if (currentIndex + 5 < maxIndex) {
                            currentIndex = maxIndex - 5;
                            applyCD(ply, 5);
                        }
                    }
                    // init and maintain dash velocity as well as init base charge gain
                    else {
                        plyVel.multiply(dashVel);
                        EntityHelper.setVelocity(ply, plyVel);
                    }
                    // remove dash velocity on timeout
                    if (currentIndex == maxIndex)
                        EntityHelper.setVelocity(ply, new Vector());

                    // hitting enemies will knock back the player and fully charge the weapon
                    Vector lookDirCopy = lookDir.clone().multiply(-1);
                    strikeLineInfo
                            .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                // full durability gain
                                setDurability(weaponItem, 8, 8);
                                // damaging beams
                                for (int i = 1; i <= 5; i ++) {
                                    Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(), () -> {
                                        Vector projVel = MathHelper.randomVector();
                                        projVel.multiply(8);
                                        Location spawnLoc = hitLoc.clone().add(entityHit.getLocation()).multiply(0.5);
                                        spawnLoc.subtract(projVel);
                                        projVel.multiply(2);
                                        EntityHelper.spawnProjectile(ply, spawnLoc, projVel, attrMap,
                                                EntityHelper.DamageType.MELEE, "星流斩切光束");
                                    }, i);
                                }
                                // recoil
                                EntityHelper.setVelocity(ply, lookDirCopy);
                            })
                            .setLingerDelay(1);
                    break;
                }
                default:
                    shouldStrike = currentIndex <= 5;
                    if (shouldStrike && particleColors.size() > 0) {
                        color = particleColors.get(0);
                    }
            }
            if (shouldStrike)
                GenericHelper.handleStrikeLine(ply, startStrikeLoc, strikeYaw, strikePitch,
                        size, strikeRadius, weaponType, color, damaged, attrMap, strikeLineInfo);
        }
        // "swing" or "whip"
        else {
            if (weaponType.equals("天顶剑")) {
                if (currentIndex % 2 == 0) {
                    String[] candidateItems = {"泰拉之刃", "彩虹猫之刃", "狂星之怒", "无头骑士剑", "种子弯刀", "铜质短剑"};
                    strikeLineInfo.particleInfo.setSpriteItem(ItemHelper.getRawItem(
                            candidateItems[(int) (Math.random() * candidateItems.length) ]));
                    handleSingleZenithStrike(ply, (HashMap<String, Double>) attrMap.clone(), particleColors, strikeLineInfo);
                }
            } else {
                if (particleColors.size() > 0)
                    color = particleColors.get(0);
                int loopTimes = Math.max(maxIndex, 35);
                int indexStart = loopTimes * currentIndex / (maxIndex + 1);
                int indexEnd = loopTimes * (currentIndex + 1) / (maxIndex + 1);
                // special weapon mechanism
                switch (weaponType) {
                    case "捕虫网":
                    case "金捕虫网": {
                        strikeLineInfo
                                .setShouldDamageFunction((e) ->
                                        e.getScoreboardTags().contains("isAnimal") && !(e.isDead()))
                                .setDamagedFunction((hitIdx, hitEntity, hitLoc) -> {
                                    ItemHelper.dropItem(hitLoc, hitEntity.getName());
                                    hitEntity.remove();
                                });
                        break;
                    }
                    case "狂星之怒": {
                        if (currentIndex == 0) {
                            EntityHelper.AimHelperOptions aimHelper = new EntityHelper.AimHelperOptions()
                                    .setAimMode(true)
                                    .setTicksTotal(10);
                            Location aimLoc = getPlayerTargetLoc(ply, aimHelper, true);
                            for (int i = 0; i < 3; i ++) {
                                Location targetLoc = aimLoc.clone();
                                targetLoc.add(
                                        Math.random() * 2 - 1,
                                        Math.random() * 2 - 1,
                                        Math.random() * 2 - 1);
                                Location shootLoc = aimLoc.add(Math.random() * 16 - 8, 25, Math.random() * 16 - 8);
                                Vector velocity = targetLoc.clone().subtract(shootLoc).toVector().multiply(0.1);
                                EntityHelper.spawnProjectile(ply, shootLoc, velocity, attrMap,
                                        EntityHelper.DamageType.MELEE, "狂星之怒");
                            }
                        }
                        break;
                    }
                    case "高斯短匕":
                    case "誓约与禁忌之刃":
                    case "炼狱":
                    case "地狱龙锋":
                    case "绝对零度":
                    case "庇护之刃": {
                        double explodeRad;
                        double explodeRate;
                        int explodeDuration, explodeDelay;
                        switch (weaponType) {
                            case "高斯短匕":
                            case "绝对零度":
                                explodeRad = 1.25;
                                explodeRate = 1;
                                explodeDuration = 1;
                                explodeDelay = 5;
                                break;
                            case "誓约与禁忌之刃":
                            case "炼狱":
                                explodeRad = 1.5;
                                explodeRate = (attrMap.getOrDefault("crit", 4d) +
                                        attrMap.getOrDefault("critMelee", 0d) +
                                        attrMap.getOrDefault("critTrueMelee", 0d)) / 100;
                                explodeDuration = 1;
                                explodeDelay = 5;
                                break;
                            case "地狱龙锋":
                                explodeRad = 0.75;
                                explodeRate = 1;
                                explodeDuration = 1;
                                explodeDelay = 5;
                                break;
                            case "庇护之刃":
                                explodeRad = 1.5;
                                explodeRate = 1;
                                explodeDuration = 24;
                                explodeDelay = 5;
                                break;
                            default:
                                explodeRad = 2;
                                explodeRate = 1;
                                explodeDuration = 1;
                                explodeDelay = 5;
                        }
                        strikeLineInfo
                                .setDamagedFunction((hitIdx, hitEntity, hitLoc) -> {
                                    if (Math.random() < explodeRate)
                                        EntityHelper.handleEntityExplode(ply, explodeRad, new ArrayList<>(), hitLoc,
                                                explodeDuration, explodeDelay);
                                });
                        break;
                    }
                    case "破碎方舟": {
                        if (currentIndex == 0) {
                            int charge = getDurability(weaponItem, 10);
                            if (charge > 0) {
                                Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 2, "远古剑气", false, 0);
                                projVel.normalize().multiply(2);

                                EntityHelper.spawnProjectile(ply, projVel, EntityHelper.getAttrMap(ply), "远古剑气");
                                charge --;
                                setDurability(weaponItem, 10, charge);
                            }
                        }
                        break;
                    }
                    case "环境之刃[天国之神威]":
                    case "真·环境之刃[兵匠之傲]": {
                        strikeRadius = 1;
                        break;
                    }
                    case "死神擢升": {
                        if (currentIndex == 0) {
                            HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                            projAttrMap.put("damage", projAttrMap.get("damage") * 0.125);
                            for (int i = 0; i < 4; i++) {
                                Vector projVel = MathHelper.vectorFromYawPitch_approx(
                                        plyYaw + Math.random() * 60 - 30,
                                        plyPitch + Math.random() * 60 - 30);
                                projVel.multiply(2.5);
                                EntityHelper.spawnProjectile(ply, projVel, projAttrMap,
                                        EntityHelper.DamageType.MELEE, "死神擢升镰刀");
                            }
                        }
                        break;
                    }
                    case "相位剑": {
                        // attack sprite is different from weapon sprite
                        ItemStack spriteItem = strikeLineInfo.particleInfo.spriteItem;
                        ItemMeta spriteItemMeta = spriteItem.getItemMeta();
                        spriteItemMeta.setDisplayName("相位剑剑刃");
                        spriteItem.setItemMeta(spriteItemMeta);
                        strikeLineInfo.particleInfo.setSpriteItem(spriteItem);
                        // this weapon's attack method resembles an agile spear
                        yawMin = plyYaw;
                        yawMax = plyYaw;
                        pitchMin = plyPitch;
                        pitchMax = plyPitch;
                        // projectile
                        if (currentIndex == 0) {
                            // deal 25% of weapon dmg (15% in calamity mod)
                            HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                            projAttrMap.put("damage", projAttrMap.get("damage") * 0.25);


                            Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 3, "热熔光刃", false, 0);
                            projVel.normalize().multiply(3);
                            EntityHelper.spawnProjectile(ply, projVel, projAttrMap,
                                    EntityHelper.DamageType.MELEE, "热熔光刃");
                        }
                        break;
                    }
                    case "宇宙暗流": {
                        // attack rotation
                        double swingPitch = -30;
                        if (swingAmount == 0) {
                            swingPitch += 360d * currentIndex / maxIndex;
                        }
                        pitchMin = swingPitch;
                        pitchMax = swingPitch;
                        // stab after first swing
                        if (swingAmount > 0 && currentIndex % 3 == 0) {
                            // sprite linger, reset damage CD
                            strikeLineInfo.particleInfo.setTicksLinger(5);
                            double strikeYaw = Math.random() * 360, strikePitch = Math.random() * 360;
                            damaged.clear();
                            // direction
                            Location targetLoc = getPlayerTargetLoc(ply, size * 0.5,
                                    new EntityHelper.AimHelperOptions().setAimMode(true), true);
                            targetLoc.subtract(
                                    MathHelper.vectorFromYawPitch_approx(strikeYaw, strikePitch)
                                            .multiply(size * 0.75));
                            strikeLineInfo.particleInfo.setRightOrthogonalDir(null);
                            GenericHelper.handleStrikeLine(ply, targetLoc, strikeYaw, strikePitch,
                                    size, strikeRadius, weaponType, color, damaged, attrMap, strikeLineInfo);
                            // set tick linger back to 1
                            strikeLineInfo.particleInfo.setTicksLinger(1);
                        }
                        break;
                    }
                    case "银河[凤凰之耀]": {
                        strikeRadius = 1;
                        // extra projectiles
                        boolean shouldShoot = false;
                        if (swingAmount > 15 && currentIndex == (maxIndex / 3))
                            shouldShoot = true;
                        else if (swingAmount > 25 && currentIndex == (maxIndex * 2 / 3))
                            shouldShoot = true;
                        else if (swingAmount > 35 && currentIndex == 0)
                            shouldShoot = true;
                        if (shouldShoot) {
                            HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                            projAttrMap.put("damage", 800d);
                            // projectile
                            Vector projVel = MathHelper.randomVector();
                            projVel.multiply(3);
                            EntityHelper.spawnProjectile(ply, projVel, projAttrMap,
                                    EntityHelper.DamageType.MELEE, "银河之雷");
                        }
                        // passive projectile
                        if (currentIndex == 0) {
                            HashMap<String, Double> passiveProjAttrMap = (HashMap<String, Double>) attrMap.clone();
                            passiveProjAttrMap.put("damage", 200d);
                            // projectile
                            Vector projVel = MathHelper.randomVector();
                            projVel.multiply(2.5);
                            EntityHelper.spawnProjectile(ply, projVel, passiveProjAttrMap,
                                    EntityHelper.DamageType.MELEE, "摩羯之祈星环");
                        }
                        break;
                    }
                    case "银河[白羊之怒]": {
                        strikeRadius = 1;
                        // on-hit projectiles
                        HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                        projAttrMap.put("damage", 800d);
                        strikeLineInfo.setDamagedFunction((hitIdx, hitEntity, hitLoc) -> {
                            Vector projVel = MathHelper.randomVector();
                            projVel.multiply(2.5);
                            EntityHelper.spawnProjectile(ply, hitLoc, projVel, projAttrMap,
                                    EntityHelper.DamageType.MELEE, "银河之雷");
                        });
                        // passive projectile
                        if (currentIndex == 0) {
                            HashMap<String, Double> passiveProjAttrMap = (HashMap<String, Double>) attrMap.clone();
                            passiveProjAttrMap.put("damage", 200d);
                            // projectile
                            Vector projVel = MathHelper.randomVector();
                            projVel.multiply(2.5);
                            EntityHelper.spawnProjectile(ply, projVel, passiveProjAttrMap,
                                    EntityHelper.DamageType.MELEE, "摩羯之祈星环");
                        }
                        break;
                    }
                    case "巨龙之怒": {
                        strikeRadius = 1;
                        // on-hit projectiles
                        HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                        projAttrMap.put("damage", projAttrMap.get("damage") * 0.15);
                        strikeLineInfo.setDamagedFunction((hitIdx, hitEntity, hitLoc) -> {
                            Vector projVel = MathHelper.randomVector();
                            projVel.multiply(2.5);
                            EntityHelper.spawnProjectile(ply, hitLoc, projVel, projAttrMap,
                                    EntityHelper.DamageType.MELEE, "巨龙之怒火球");
                        });
                        break;
                    }
                    case "屠杀": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                                if (hitEntity.isDead()) {
                                    PlayerHelper.heal(ply, 20);
                                }
                            }, 1);
                        });
                        break;
                    }
                    case "天神之剑": {
                        if (currentIndex == 0) {
                            for (int i = 0; i < 3; i++) {
                                Location spawnLoc = ply.getEyeLocation().add(
                                        Math.random() * 10 - 5, Math.random() * 10 - 5, Math.random() * 10 - 5);
                                Vector projVel = ply.getEyeLocation().subtract(spawnLoc).toVector();
                                projVel.multiply(0.04);
                                String projectileType = Math.random() < 0.5 ? "蓝色宙能聚爆" : "红色宙能聚爆";
                                EntityHelper.spawnProjectile(ply, spawnLoc, projVel, attrMap,
                                        EntityHelper.DamageType.MELEE, projectileType);
                            }
                        }
                        break;
                    }
                    case "硫火之刃": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            if (hitIdx == 1) {
                                for (int i = (int) (Math.random() * 2); i < 3; i++) {
                                    Vector projVel = MathHelper.randomVector();
                                    projVel.multiply(0.35);
                                    EntityHelper.spawnProjectile(ply, hitLoc, projVel,
                                            attrMap, EntityHelper.DamageType.MELEE, "硫磺火间歇泉");
                                }
                            }
                        });
                        break;
                    }
                    case "雄伟之护": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            if (EntityHelper.hasEffect(hitEntity, "碎甲"))
                                PlayerHelper.heal(ply, 6);
                            else
                                EntityHelper.applyEffect(hitEntity, "碎甲", 50);
                        });
                        break;
                    }
                    case "凋亡屠刀": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            EntityHelper.applyEffect(ply, "暴君之怒", 60);
                        });
                        break;
                    }
                    case "混乱之刃": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            double damage = ply.getMaxHealth() - ply.getHealth();
                            EntityHelper.handleDamage(ply, hitEntity, damage, EntityHelper.DamageReason.DIRECT_DAMAGE);
                        });
                        break;
                    }
                    case "彗星陨刃":
                    case "翡翠之潮":
                    case "月炎之锋": {
                        String projectileName;
                        double projectileSpeed;
                        int shootAmountMin = 2, shootAmountMax = 3;
                        switch (weaponType) {
                            case "翡翠之潮":
                                projectileName = "利维坦毒牙";
                                projectileSpeed = 2;
                                break;
                            case "彗星陨刃":
                                projectileName = "陨落流星";
                                projectileSpeed = 2.5;
                                shootAmountMax = 4;
                                break;
                            case "月炎之锋":
                                projectileName = "月之耀斑";
                                projectileSpeed = 2.25;
                                shootAmountMin = 1;
                                break;
                            default:
                                projectileName = "木箭";
                                projectileSpeed = 1;
                        }
                        int terminateIdx = shootAmountMax;
                        int initialRdmIdx = shootAmountMax - shootAmountMin + 1;
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            for (int i = (int) (Math.random() * initialRdmIdx); i < terminateIdx; i ++) {
                                Location spawnLoc;
                                Vector projVel;
                                spawnLoc = hitLoc.clone().add(
                                        Math.random() * 12 - 6,
                                        Math.random() * 8 + 16,
                                        Math.random() * 12 - 6);
                                projVel = MathHelper.getDirection(spawnLoc, hitLoc, projectileSpeed);
                                EntityHelper.spawnProjectile(ply, spawnLoc, projVel,
                                        attrMap, EntityHelper.DamageType.MELEE, projectileName);
                            }
                        });
                        break;
                    }
                    case "荆棘长刃": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            double thornYaw = Math.random() * 360;
                            double thornPitch = Math.random() * 360;
                            Vector offsetVec = MathHelper.vectorFromYawPitch_approx(thornYaw, thornPitch);
                            offsetVec.multiply(5);
                            GenericHelper.StrikeLineOptions thornStrikeOption = new GenericHelper.StrikeLineOptions()
                                    .setDamageCD(4)
                                    .setLingerTime(6)
                                    .setLingerDelay(5)
                                    .setThruWall(true);
                            GenericHelper.handleStrikeLightning(ply, hitLoc.subtract(offsetVec), thornYaw, thornPitch,
                                    12, 3,  0.5, 1, 4, "103|78|50",
                                    new ArrayList<>(), attrMap, thornStrikeOption);
                        });
                        break;
                    }
                    case "大守卫者": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            EntityHelper.handleEntityExplode(ply, 2.5, new ArrayList<>(), hitLoc);

                            if (EntityHelper.hasEffect(hitEntity, "碎甲"))
                                PlayerHelper.heal(ply, 8);
                            else
                                EntityHelper.applyEffect(hitEntity, "碎甲", 50);

                            if (hitEntity instanceof LivingEntity) {
                                LivingEntity livingHitEntity = (LivingEntity) hitEntity;
                                if (livingHitEntity.getHealth() * 2 < livingHitEntity.getMaxHealth()) {
                                    for (int projYaw = 0; projYaw <= 360; projYaw += 120) {
                                        Vector projVel = MathHelper.vectorFromYawPitch_approx(projYaw, -30);
                                        projVel.multiply(1.5);
                                        EntityHelper.spawnProjectile(ply, hitLoc, projVel, attrMap,
                                                EntityHelper.DamageType.MELEE, "彩虹弹幕");
                                    }
                                }
                            }
                        });
                        break;
                    }
                    case "狱火重剑": {
                        if (currentIndex == 0) {
                            for (int i = 0; i < 3; i ++) {
                                String projectileType;
                                double damageMulti;
                                if (i == 0) {
                                    projectileType = "大熔岩火球";
                                    damageMulti = 1;
                                }
                                else {
                                    projectileType = "小熔岩火球";
                                    damageMulti = 0.65;
                                }
                                HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                                projAttrMap.put("damage", projAttrMap.get("damage") * damageMulti);
                                Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 1.5, projectileType, false, 0);
                                projVel.add(MathHelper.randomVector().multiply( projVel.length() * 0.1 ) );
                                projVel.normalize().multiply(1.5);
                                EntityHelper.spawnProjectile(ply, projVel,
                                        projAttrMap, EntityHelper.DamageType.MELEE, projectileType);
                            }
                        }
                        break;
                    }
                    case "幻星刀": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            if (hitEntity instanceof LivingEntity) {
                                LivingEntity hitEntityLiving = (LivingEntity) hitEntity;
                                double victimHealthRatio = hitEntityLiving.getHealth() / hitEntityLiving.getMaxHealth();
                                double damage = attrMap.get("damage") * victimHealthRatio;
                                EntityHelper.handleDamage(ply, hitEntity, damage, EntityHelper.DamageReason.DIRECT_DAMAGE);
                            }
                        });
                        break;
                    }
                    case "崇高誓约之剑": {
                        if (currentIndex == 0) {
                            double projPitch = plyPitch;
                            projPitch -= 15;
                            for (int i = 0; i < 3; i ++) {
                                Vector projVel = MathHelper.vectorFromYawPitch_approx(plyYaw, projPitch);
                                projVel.multiply(1.5);
                                EntityHelper.spawnProjectile(ply,
                                        projVel, attrMap,
                                        EntityHelper.DamageType.MELEE, "禁忌镰刀");
                                projPitch += 15;
                            }
                        }
                        double critRate = (attrMap.getOrDefault("crit", 4d) +
                                attrMap.getOrDefault("critMelee", 0d) +
                                attrMap.getOrDefault("critTrueMelee", 0d)) / 100;
                        strikeLineInfo
                                .setDamagedFunction((hitIdx, hitEntity, hitLoc) -> {
                                    if (Math.random() < critRate)
                                        EntityHelper.handleEntityExplode(ply, 1.5, new ArrayList<>(), hitLoc,
                                                1, 5);
                                });
                        break;
                    }
                    case "破灭魔王剑": {
                        if (currentIndex == 0) {
                            double projPitch = plyPitch;
                            projPitch -= 15;
                            for (int i = 0; i < 3; i ++) {
                                Vector projVel = MathHelper.vectorFromYawPitch_approx(plyYaw, projPitch);
                                projVel.multiply(2.25);
                                EntityHelper.spawnProjectile(ply,
                                        projVel, attrMap,
                                        EntityHelper.DamageType.MELEE, "禁忌镰刀");
                                projPitch += 15;
                            }
                        }
                        double critRate = (attrMap.getOrDefault("crit", 4d) +
                                attrMap.getOrDefault("critMelee", 0d) +
                                attrMap.getOrDefault("critTrueMelee", 0d)) / 100;
                        strikeLineInfo
                                .setDamagedFunction((hitIdx, hitEntity, hitLoc) -> {
                                    if (Math.random() < critRate)
                                        EntityHelper.handleEntityExplode(ply, 1.5, new ArrayList<>(), hitLoc,
                                                1, 5);
                                });
                        break;
                    }
                    case "海爵剑": {
                        strikeLineInfo
                                .setDamagedFunction((hitIdx, hitEntity, hitLoc) -> {
                                    if (! ply.getScoreboardTags().contains("temp_sharknado")) {
                                        EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                                "temp_sharknado", 100);
                                        HashMap<String, Double> tornadoAttrMap = (HashMap<String, Double>) attrMap.clone();
                                        tornadoAttrMap.put("damage", tornadoAttrMap.get("damage") * 0.5);
                                        new PlayerTornado(
                                                ply, hitLoc.clone().subtract(0, 2, 0), new ArrayList<>(),
                                                0, 18, 45, 30,
                                                2, 0.9, "鲨鱼旋风", tornadoAttrMap);
                                    }
                                });
                        break;
                    }
                    case "熵阔剑": {
                        if (currentIndex == 0) {
                            for (int i = 0; i < 5; i ++) {
                                HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                                projAttrMap.put("damage", projAttrMap.get("damage") * (0.3 + Math.random() * 0.4) );
                                Vector projVel = MathHelper.vectorFromYawPitch_approx(
                                        plyYaw + Math.random() * 10 - 5, plyPitch + Math.random() * 10 - 5);
                                projVel.multiply(2.25);
                                EntityHelper.spawnProjectile(ply, projVel,
                                        projAttrMap, EntityHelper.DamageType.MELEE, "熵离子球");
                            }
                        }
                        break;
                    }
                    case "季节长剑": {
                        if (currentIndex == 0) {
                            Calendar calendar = Calendar.getInstance();
                            int hour = calendar.get(Calendar.HOUR_OF_DAY);
                            boolean isDay = hour > 8 && hour < 19;
                            HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                            projAttrMap.put("damage", projAttrMap.get("damage") * (0.5 + Math.random() * 0.3) );

                            String projType = isDay ? "白天剑气" : "夜晚剑气";
                            Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 3, projType, false, 0);
                            projVel.normalize().multiply(3);
                            EntityHelper.spawnProjectile(ply, projVel,
                                    projAttrMap, EntityHelper.DamageType.MELEE, projType);
                        }
                        break;
                    }
                    case "破坏重剑": {
                        if (currentIndex == 0) {
                            Location targetLoc = ply.getEyeLocation().add(
                                    getPlayerAimDir(ply, ply.getEyeLocation(), 3, "破坏重剑剑气", false, 0) );
                            for (int i = 1; i <= 8; i ++) {
                                Location spawnLoc = targetLoc.clone().add(
                                        Math.random() * 10 - 5, Math.random() * 10 + 15, Math.random() * 10 - 5);
                                Vector projVel = MathHelper.getDirection(spawnLoc, targetLoc, 3);
                                EntityHelper.spawnProjectile(ply, spawnLoc, projVel,
                                        attrMap, EntityHelper.DamageType.MELEE, "破坏重剑剑气");
                            }
                        }
                        break;
                    }
                    case "爆破剑": {
                        if (currentIndex == 0) {
                            for (int i = 1; i <= 8; i ++) {
                                Location spawnLoc;
                                Vector projVel;
                                spawnLoc = ply.getEyeLocation().add(0, i * 2, 0);
                                if (i <= 4) {
                                    Location targetLoc = ply.getEyeLocation().add(
                                            getPlayerAimDir(ply, ply.getEyeLocation(), 3, "爆破剑气", false, 0) );
                                    projVel = MathHelper.getDirection(spawnLoc, targetLoc, 3);
                                }
                                else {
                                    projVel = new Vector(Math.random() - 0.5, -1, Math.random() - 0.5);
                                    projVel.multiply(3);
                                }
                                EntityHelper.spawnProjectile(ply, spawnLoc, projVel,
                                        attrMap, EntityHelper.DamageType.MELEE, "爆破剑气");
                            }
                        }
                        break;
                    }
                    case "圣火之刃": {
                        strikeLineInfo.setDamagedFunction((hitIdx, hitEntity, hitLoc) -> {
                            HashMap<String, Double> projAttrMap = (HashMap<String, Double>) EntityHelper.getAttrMap(ply).clone();
                            projAttrMap.put("damage", projAttrMap.get("damage") * 0.3);
                            for (int i = 0; i < 8; i ++) {
                                Vector projVel = MathHelper.vectorFromYawPitch_approx(45 * i, 0);
                                projVel.multiply(1.5);
                                EntityHelper.spawnProjectile(ply, hitLoc, projVel, projAttrMap,
                                        EntityHelper.DamageType.MELEE,"神圣之火");
                            }
                        });
                        break;
                    }
                    case "星河之刃": {
                        if (currentIndex == 0) {
                            Location targetLoc = getPlayerTargetLoc(ply,
                                    new EntityHelper.AimHelperOptions().setAimMode(true).setTicksTotal(10), true);
                            for (int i = 0; i < 5; i ++) {
                                Location spawnLoc = targetLoc.clone().add(
                                        Math.random() * 8 - 4, Math.random() * 5 + 15, Math.random() * 8 - 4);
                                Location aimLoc = targetLoc.clone().add(
                                        Math.random() * 2 - 1, Math.random() * 2 - 1, Math.random() * 2 - 1);
                                Vector projVel = MathHelper.getDirection(spawnLoc, aimLoc, 2);
                                EntityHelper.spawnProjectile(ply, spawnLoc, projVel,
                                        attrMap, EntityHelper.DamageType.MELEE, "星河陨石");
                            }
                        }
                        break;
                    }
                    case "格拉克斯": {
                        strikeLineInfo.setDamagedFunction((hitIdx, hitEntity, hitLoc) ->
                                EntityHelper.applyEffect(ply, "格拉克斯之助", 200));
                        break;
                    }
                    case "猎命镰刀": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            if (hitIdx == 1)
                                PlayerHelper.heal(ply, 5);
                        });
                        break;
                    }
                    case "不洁巨剑": {
                        if (currentIndex == 0) {
                            String projType;
                            double rdm = Math.random();
                            if (rdm < 0.33333)
                                projType = "烈火不洁剑气";
                            else if (rdm < 0.66666)
                                projType = "至高不洁剑气";
                            else
                                projType = "日炎不洁剑气";

                            Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 2.25, projType, false, 0);
                            projVel.normalize().multiply(2.25);
                            EntityHelper.spawnProjectile(ply, projVel, EntityHelper.getAttrMap(ply), projType);
                        }
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            EntityHelper.applyEffect(ply, "暴君之怒", 100);
                        });
                        break;
                    }
                    case "泰拉阔剑": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            Vector projVel = MathHelper.randomVector();
                            projVel.multiply(8);
                            Location spawnLoc = hitLoc.add(hitEntity.getLocation()).multiply(0.5);
                            spawnLoc.subtract(projVel);
                            projVel.multiply(2);
                            EntityHelper.spawnProjectile(ply, spawnLoc, projVel, EntityHelper.getAttrMap(ply),
                                    EntityHelper.DamageType.MELEE, "泰拉斩切光束");
                        });
                        break;
                    }
                    case "血石斩切刃": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            if (hitEntity instanceof LivingEntity && ! hitEntity.getScoreboardTags().contains("isBOSS")) {
                                LivingEntity hitLivingEntity = (LivingEntity) hitEntity;
                                if (hitLivingEntity.getHealth() * 5 < hitLivingEntity.getMaxHealth()) {
                                    ArrayList<Entity> dmgExceptions = new ArrayList<>();
                                    dmgExceptions.add(hitEntity);
                                    EntityHelper.handleEntityExplode(ply, 2, dmgExceptions, hitLoc);
                                    EntityHelper.dropHeart(hitLoc);
                                }
                            }
                        });
                        break;
                    }
                    case "镜之刃": {
                        if (currentIndex == 0) {
                            for (int i = 0; i < 3; i ++) {
                                Vector projVel = MathHelper.vectorFromYawPitch_approx(
                                        plyYaw + Math.random() * 20 - 10, plyPitch + Math.random() * 20 - 10);
                                projVel.multiply(3);
                                EntityHelper.spawnProjectile(ply, projVel,
                                        attrMap, EntityHelper.DamageType.MELEE, "镜之刃镜子");
                            }
                        }

                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            HashMap<String, Double> victimAttrMap = EntityHelper.getAttrMap(hitEntity);
                            double extraDamage = Math.min(1000, victimAttrMap.getOrDefault("damage", 1d) );
                            EntityHelper.handleDamage(ply, hitEntity, extraDamage,
                                    EntityHelper.DamageReason.DIRECT_DAMAGE);
                        });
                        break;
                    }
                    case "狮心圣裁": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            EntityHelper.applyEffect(ply, "狮心圣裁能量外壳", 100);
                        });
                        break;
                    }
                    case "宙能波纹剑": {
                        if (currentIndex == 0) {
                            for (int i = 0; i < 3; i ++) {
                                Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 2, "宙魔之纹", false, 0);
                                projVel.add( MathHelper.randomVector().multiply(projVel.length() * 0.175) );
                                projVel.normalize().multiply(2.25);
                                EntityHelper.spawnProjectile(ply, projVel,
                                        attrMap, EntityHelper.DamageType.MELEE, "宙魔之纹");
                            }
                        }
                        break;
                    }
                    case "暴政": {
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            if (hitIdx == 1) {
                                Location shootLoc = hitLoc.add(
                                        Math.random() * 16 - 8,
                                        Math.random() * 10,
                                        Math.random() * 16 - 8);
                                Vector projVel = MathHelper.randomVector();
                                projVel.multiply(3);
                                EntityHelper.spawnProjectile(ply, shootLoc, projVel,
                                        attrMap, EntityHelper.DamageType.MELEE, "精华火焰");
                            }
                        });
                        break;
                    }
                    case "禅心剑": {
                        if (currentIndex == 0) {
                            Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 3, "爆炸禅心导弹", false, 0);
                            projVel.normalize().multiply(3);
                            for (int i = -1; i <= 1; i ++) {
                                Vector spawnLocOffset = MathHelper.vectorFromYawPitch_approx(
                                        plyYaw, plyPitch + i * 15);
                                Location spawnLoc = ply.getEyeLocation().add(spawnLocOffset);
                                EntityHelper.spawnProjectile(ply, spawnLoc, projVel,
                                        attrMap, EntityHelper.DamageType.MELEE,
                                        i == 0 ? "爆炸禅心导弹" : "分裂禅心导弹");
                            }
                        }
                        break;
                    }
                    case "盖尔大剑": {
                        if (currentIndex == 0) {
                            // extra projectiles
                            Vector projVel;
                            switch (swingAmount % 3) {
                                case 0:
                                    projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 3, "跟踪血骷髅", false, 0);
                                    projVel.normalize().multiply(3);
                                    for (int i = -1; i <= 1; i += 2) {
                                        Vector spawnLocOffset = MathHelper.vectorFromYawPitch_approx(
                                                plyYaw, plyPitch + i * 15);
                                        Location spawnLoc = ply.getEyeLocation().add(spawnLocOffset);
                                        EntityHelper.spawnProjectile(ply, spawnLoc, projVel,
                                                attrMap, EntityHelper.DamageType.MELEE, "跟踪血骷髅");
                                    }
                                    break;
                                case 1:
                                    projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 1.75, "巨大血骷髅", false, 0);
                                    projVel.normalize().multiply(1.75);
                                    Location spawnLoc = ply.getEyeLocation();
                                    EntityHelper.spawnProjectile(ply, spawnLoc, projVel,
                                            attrMap, EntityHelper.DamageType.MELEE, "巨大血骷髅");
                                    break;
                                case 2:
                                default:
                                    double dmg = attrMap.getOrDefault("damage", 100d);
                                    strikeLineInfo.setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                        EntityHelper.handleDamage(ply, hitEntity, dmg, EntityHelper.DamageReason.DIRECT_DAMAGE);
                                    });
                            }
                            // charge up
                            int charge = getDurability(weaponItem, 100);
                            if (charge++ >= 100) {
                                charge = 0;
                                for (int i = 0; i < 20; i ++) {
                                    projVel = MathHelper.vectorFromYawPitch_approx(
                                            Math.random() * 360, Math.random() * -90);
                                    projVel.multiply(2.5);
                                    EntityHelper.spawnProjectile(ply, projVel,
                                            attrMap, EntityHelper.DamageType.MELEE, "跟踪血骷髅");
                                }
                            }
                            setDurability(weaponItem, 100, charge);
                        }
                        break;
                    }
                    case "星流之刃": {
                        int currCharge = getDurability(weaponItem, 8);
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            Vector projVel = MathHelper.randomVector();
                            projVel.multiply(8);
                            Location spawnLoc = hitLoc.clone().add(hitEntity.getLocation()).multiply(0.5);
                            spawnLoc.subtract(projVel);
                            projVel.multiply(2);
                            EntityHelper.spawnProjectile(ply, spawnLoc, projVel, attrMap,
                                    EntityHelper.DamageType.MELEE, "星流斩切光束");
                            // explode on hit when fully charged
                            if (currCharge == 8) {
                                EntityHelper.spawnProjectile(ply, hitLoc, new Vector(), attrMap,
                                        EntityHelper.DamageType.MELEE, "星流能量爆炸");
                            }
                        });
                        // charge also increases the blade size
                        if (currCharge > 0)
                            size = 16;
                        // decrease charge on strike end
                        if (currentIndex == maxIndex)
                            setDurability(weaponItem, 8, currCharge - 1);
                        break;
                    }
                    case "无限大地": {
                        EntityHelper.AimHelperOptions aimHelper = new EntityHelper.AimHelperOptions()
                                .setAimMode(true).setTicksTotal(10);
                        strikeLineInfo.setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                            // heal
                            PlayerHelper.heal(ply, (int) (Math.random() * 50));
                            // spawn projectiles
                            Location aimLoc = EntityHelper.helperAimEntity(ply, hitEntity, aimHelper);
                            Location spawnLoc = aimLoc.clone().add(
                                    Math.random() * 12 - 6,
                                    Math.random() * 4 + 12,
                                    Math.random() * 12 - 6);
                            Vector projVel = aimLoc.clone().subtract(spawnLoc).toVector();
                            projVel.multiply(0.1);
                            EntityHelper.spawnProjectile(ply, spawnLoc, projVel, attrMap,
                                    EntityHelper.DamageType.MELEE, "三色大地流星");
                        });
                        break;
                    }
                }
                for (int i = indexStart; i < indexEnd; i ++) {
                    double progress = (double) i / loopTimes;
                    double actualYaw = yawMin + (yawMax - yawMin) * progress;
                    double actualPitch = pitchMin + (pitchMax - pitchMin) * progress;
                    Vector offsetDir = MathHelper.vectorFromYawPitch_approx(actualYaw, actualPitch);
                    // swords have a constant reach while whips have a changing attack reach over time
                    double strikeLength = interpolateType == 1 ?
                            size : size * MathHelper.xcos_degree( 300 * (progress - 0.5) );
                    Location startStrikeLoc = ply.getLocation().add(0, 1, 0).add(offsetDir);
                    // extra handling
                    switch (weaponType) {
                        case "剑锋之誓约": {
                            if ( i == (int) (loopTimes * 0.4) ||
                                    i == (int) (loopTimes * 0.5) ||
                                    i == (int) (loopTimes * 0.6) ||
                                    i == (int) (loopTimes * 0.7)) {
                                offsetDir.multiply(1.35);
                                EntityHelper.spawnProjectile(ply, offsetDir,
                                        EntityHelper.getAttrMap(ply), "血之镰刀");
                            }
                            break;
                        }
                        case "炼狱": {
                            if ( i == (int) (loopTimes * 0.15) ||
                                    i == (int) (loopTimes * 0.3) ||
                                    i == (int) (loopTimes * 0.45) ||
                                    i == (int) (loopTimes * 0.6) ||
                                    i == (int) (loopTimes * 0.75)) {
                                offsetDir.multiply(1.35);
                                HashMap<String, Double> attrMapProj = (HashMap<String, Double>) attrMap.clone();
                                attrMapProj.put("damage", attrMapProj.get("damage") * 0.4);
                                EntityHelper.spawnProjectile(ply, offsetDir,
                                        attrMapProj, "小火花");
                            }
                            break;
                        }
                        case "斩首者": {
                            if ( i == (int) (loopTimes * 0.15) ||
                                    i == (int) (loopTimes * 0.3) ||
                                    i == (int) (loopTimes * 0.45) ||
                                    i == (int) (loopTimes * 0.6) ||
                                    i == (int) (loopTimes * 0.75)) {
                                offsetDir.multiply(1.45);
                                HashMap<String, Double> attrMapProj = (HashMap<String, Double>) attrMap.clone();
                                attrMapProj.put("damage", attrMapProj.get("damage") * 0.35);
                                EntityHelper.spawnProjectile(ply, offsetDir,
                                        attrMapProj, "蓝焰火花");
                            }
                            break;
                        }
                        case "残缺环境刃[林妖之轻抚]":
                        case "环境之刃[林妖之轻抚]":
                        case "真·环境之刃[缚囚之悼]": {
                            // use particle for this one
                            strikeLineInfo.particleInfo = null;
                            strikeLineInfo.setLingerDelay(1);
                            strikeLength = 2.5 + 15d * progress;
                            Vector bladeOffsetDir = offsetDir.clone().normalize().multiply(strikeLength);
                            Location bladeStrikeInitLoc = startStrikeLoc.clone().add(bladeOffsetDir);
                            GenericHelper.StrikeLineOptions bladeStrikeOption = new GenericHelper.StrikeLineOptions()
                                    .setThruWall(false)
                                    .setLingerDelay(1);

                            // the blade pulls the player over if it is the final blow
                            if (i + 1 == loopTimes && ply.isSneaking()) {
                                Location finalStartStrikeLoc = startStrikeLoc;
                                bladeStrikeOption
                                        .setBlockHitFunction((hitLocation, movingObjectPosition) -> {
                                            // pulls the player
                                            ply.setFallDistance(0f);
                                            Vector plyVel = MathHelper.getDirection(finalStartStrikeLoc, hitLocation, 1.5);
                                            EntityHelper.setVelocity(ply, plyVel);
                                        });
                            }
                            // cache the crit rate, set it to 100, handle hit and reset to normal
                            HashMap<String, Double> plyAttrMap = EntityHelper.getAttrMap(ply);
                            double critRate = plyAttrMap.getOrDefault("crit", 4d);
                            plyAttrMap.put("crit", 100d);
                            String bladeColor = weaponType.equals("真·环境之刃[缚囚之悼]") ? "255|255|255" : "166|251|46";
                            GenericHelper.handleStrikeLine(ply, bladeStrikeInitLoc, actualYaw, actualPitch,
                                    2.5, strikeRadius, weaponType, bladeColor,
                                    damaged, attrMap, bladeStrikeOption);
                            plyAttrMap.put("crit", critRate);
                            break;
                        }
                        case "银河[白羊之怒]": {
                            // yoyo-like behaviour
                            Location targetLoc;
                            EntityHelper.AimHelperOptions aimOption = new EntityHelper.AimHelperOptions().setAimMode(true);
                            // only initialize target once to prevent dashing into multiple enemies with one swing
                            if (i == 0)
                                targetLoc = getPlayerTargetLoc(ply,
                                        strikeLength * 0.75,
                                        aimOption, true);
                            else
                                targetLoc = getPlayerCachedTargetLoc(ply, aimOption);
                            // rotation
                            actualYaw = plyYaw + 90;
                            actualPitch = 360d * i / loopTimes;
                            startStrikeLoc = targetLoc.subtract(
                                    MathHelper.vectorFromYawPitch_approx(actualYaw, actualPitch)
                                            .multiply(strikeLength / 2d));
                            strikeLineInfo.particleInfo.setRightOrthogonalDir(null);
                            break;
                        }
                        case "巨龙之怒": {
                            // strike direction; a swing is a full rotation
                            actualPitch = -45 + (360d * i / loopTimes);
                            break;
                        }
                        case "恣睢": {
                            // yoyo-like behaviour
                            Location targetLoc;
                            EntityHelper.AimHelperOptions aimOption = new EntityHelper.AimHelperOptions().setAimMode(true);
                            // its base damage is low, hence damage check should be very frequent
                            strikeLineInfo.setDamageCD(1);
                            // only initialize target once to prevent dashing into multiple enemies with one swing
                            if (i == 0)
                                targetLoc = getPlayerTargetLoc(ply,
                                        strikeLength * 0.75,
                                        aimOption, true);
                            else
                                targetLoc = getPlayerCachedTargetLoc(ply, aimOption);
                            // rotation
                            actualYaw = plyYaw + 90;
                            actualPitch = 360d * i / loopTimes;
                            startStrikeLoc = targetLoc.subtract(
                                    MathHelper.vectorFromYawPitch_approx(actualYaw, actualPitch)
                                            .multiply(strikeLength / 2d));
                            strikeLineInfo.particleInfo.setRightOrthogonalDir(null);
                            break;
                        }
                        case "远古方舟": {
                            int charge = getDurability(weaponItem, 10);
                            // shoot enhanced projectile
                            if (charge > 0) {
                                if (i == 0) {
                                    Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 2.5, "真·远古剑气", false, 0);
                                    projVel.normalize().multiply(2.5);
                                    EntityHelper.spawnProjectile(ply, projVel, EntityHelper.getAttrMap(ply), "真·远古剑气");
                                    charge--;
                                    setDurability(weaponItem, 10, charge);
                                }
                            }
                            // shoot three small projectiles every second swing
                            else if (swingAmount % 2 == 1) {
                                if (i == (int) (loopTimes * 0.4) ||
                                        i == (int) (loopTimes * 0.5) ||
                                        i == (int) (loopTimes * 0.65)) {
                                    offsetDir.multiply(1.75);
                                    HashMap<String, Double> attrMapPly = (HashMap<String, Double>) EntityHelper.getAttrMap(ply).clone();
                                    attrMapPly.put("damage", attrMapPly.getOrDefault("damage", 1d) * 0.35);
                                    EntityHelper.spawnProjectile(ply, offsetDir,
                                            attrMapPly, "远古之星");
                                }
                            }
                            break;
                        }
                        case "元素方舟":
                        case "鸿蒙方舟": {
                            boolean isArkOfElements = weaponType.equals("元素方舟");
                            double baseDmg = isArkOfElements ? 1200 : 5000;
                            double baseBladeSize = isArkOfElements ? 6 : 7.5;
                            double scissorsConnLen = 0.3333;
                            int attackPhase = swingAmount % 5;
                            // consume charge
                            int charge = getDurability(weaponItem, 16);
                            boolean isCharged = charge > 0;
                            if (isCharged && i + 1 == maxIndex) {
                                charge--;
                                setDurability(weaponItem, 16, charge);
                            }
                            // charges increase damage and size
                            strikeLength = baseBladeSize * (isCharged ? 1.3333d : 1d);
                            attrMap.put("damage", baseDmg * (isCharged ? 1.25d : 1d) );
                            // should not have excessive damage check
                            strikeLineInfo.setDamageCD(30);
                            // tweak strike info to render the first half of scissors
                            ItemStack scissorItem = strikeLineInfo.particleInfo.spriteItem;
                            if (strikeLineInfo.displayParticle) {
                                ItemMeta newMeta = scissorItem.getItemMeta();
                                newMeta.setDisplayName(weaponType + "1");
                                scissorItem.setItemMeta(newMeta);
                                strikeLineInfo.particleInfo.setSpriteItem(scissorItem);
                            }
                            // special attack mechanism for last swing
                            if (attackPhase == 4) {
                                Location startStrikeLocOriginal = startStrikeLoc.clone();
                                // this is the player's target location (equivalent to cursor pos. in Terraria)
                                Location targetLoc;
                                EntityHelper.AimHelperOptions aimOption = new EntityHelper.AimHelperOptions().setAimMode(true);
                                // only initialize target once to prevent scissors dashing into multiple enemies with one swing
                                if (i == 0)
                                    targetLoc = getPlayerTargetLoc(ply,
                                            strikeLength * 0.75,
                                            aimOption, true);
                                else
                                    targetLoc = getPlayerCachedTargetLoc(ply, aimOption);
                                Vector targetLocDir = targetLoc.clone().subtract(startStrikeLoc).toVector();
                                // spin and reach out for enemy
                                if (progress < 0.55) {
                                    double spinProgress = progress / 0.55;
                                    // damage-handling piece of scissors
                                    // find the middle position of scissors
                                    Location targetCenterLoc = startStrikeLoc.clone().add( targetLocDir.clone().multiply(spinProgress) );
                                    // get rotation
                                    actualPitch = MathHelper.getVectorPitch(targetLocDir) - 55 + (415 * spinProgress);
                                    // setup startStrikeLoc
                                    Vector startLocOffset = MathHelper.vectorFromYawPitch_approx(actualYaw, actualPitch);
                                    startLocOffset.multiply(strikeLength / 2d);
                                    startStrikeLoc = targetCenterLoc.clone().subtract(startLocOffset);


                                    // render additional scissor part if empowered
                                    if (isCharged && i == indexStart) {
                                        // strike
                                        double bladePitch = actualPitch + 30;
                                        Location bladeLoc = getScissorBladeLoc(startStrikeLoc,
                                                MathHelper.vectorFromYawPitch_approx(actualYaw, actualPitch),
                                                MathHelper.vectorFromYawPitch_approx(actualYaw, bladePitch),
                                                strikeLength, scissorsConnLen);
                                        GenericHelper.handleStrikeLine(ply, bladeLoc,
                                                actualYaw, bladePitch,
                                                strikeLength, strikeRadius, weaponType, color, damaged, attrMap, strikeLineInfo);
                                    }
                                }
                                // open scissors
                                else if (progress < 0.8) {
                                    double openProgress = (progress - 0.55) / 0.25;
                                    // damage-handling piece of scissors
                                    // get rotation
                                    double targetLocDirPitch = MathHelper.getVectorPitch(targetLocDir);
                                    actualPitch = targetLocDirPitch - (45 * openProgress);
                                    // setup startStrikeLoc
                                    Vector startLocOffset = MathHelper.vectorFromYawPitch_approx(actualYaw, actualPitch);
                                    startLocOffset.multiply(strikeLength / 2d);
                                    startStrikeLoc = targetLoc.clone().subtract(startLocOffset);


                                    // render additional scissor part
                                    if (i == indexStart) {
                                        // charged: scissors is already in place
                                        // get center
                                        Vector targetCenterDir = startStrikeLoc.clone().subtract(startStrikeLocOriginal).toVector();
                                        Location targetCenterLoc = isCharged ? startStrikeLoc :
                                                startStrikeLocOriginal.clone().add( targetCenterDir.clone().multiply(openProgress) );
                                        // set direction and orientation
                                        double scissorPartPitch = targetLocDirPitch + (45 * openProgress);
                                        Location bladeLoc = getScissorBladeLoc(targetCenterLoc,
                                                MathHelper.vectorFromYawPitch_approx(actualYaw, actualPitch),
                                                MathHelper.vectorFromYawPitch_approx(actualYaw, scissorPartPitch),
                                                strikeLength, scissorsConnLen);
                                        // damage
                                        GenericHelper.handleStrikeLine(ply, bladeLoc,
                                                actualYaw, scissorPartPitch,
                                                strikeLength, strikeRadius, weaponType, color, damaged, attrMap, strikeLineInfo);
                                    }
                                }
                                // cut
                                else {
                                    // reset damage CD and increase damage
                                    if ( (i - 1d) / loopTimes < 0.8 ) {
                                        damaged.clear();
                                    }
                                    attrMap.put("damage", baseDmg * (isCharged ? 2.5d : 1.75d) );
                                    // find cut progress; splay cutting sound effect on finish
                                    double cutProgress = Math.min( (progress - 0.8) / 0.125, 1);
                                    if (progress < 0.925 && (i + 1d) / loopTimes >= 0.925) {
                                        ply.getWorld().playSound(ply.getLocation(), SOUND_ARK_SCISSOR_CUT, SoundCategory.PLAYERS, 5f, 1f);
                                    }
                                    // damage-handling piece of scissors
                                    double targetLocDirPitch = MathHelper.getVectorPitch(targetLocDir);
                                    // get rotation
                                    actualPitch = targetLocDirPitch - (45 * (1 - cutProgress));
                                    // setup startStrikeLoc
                                    Vector startLocOffset = MathHelper.vectorFromYawPitch_approx(actualYaw, actualPitch);
                                    startLocOffset.multiply(strikeLength / 2d);
                                    startStrikeLoc = targetLoc.clone().subtract(startLocOffset);


                                    // render additional scissor part
                                    if (i == indexStart) {
                                        // set direction and orientation
                                        double scissorPartPitch = targetLocDirPitch + (45 * (1 - cutProgress));
                                        Location bladeLoc = getScissorBladeLoc(startStrikeLoc,
                                                MathHelper.vectorFromYawPitch_approx(actualYaw, actualPitch),
                                                MathHelper.vectorFromYawPitch_approx(actualYaw, scissorPartPitch),
                                                strikeLength, scissorsConnLen);
                                        // damage
                                        GenericHelper.handleStrikeLine(ply, bladeLoc,
                                                actualYaw, scissorPartPitch,
                                                strikeLength, strikeRadius, weaponType, color, damaged, attrMap, strikeLineInfo);
                                    }
                                }
                            }
                            // for simpler upward/downward swings
                            else {
                                boolean isDownwardSwing = attackPhase == 1 || attackPhase == 3;
                                // the blade is rotated by 180 degrees while upward swing
                                if (strikeLineInfo.displayParticle)
                                    strikeLineInfo.particleInfo.setRightOrthogonalDir(MathHelper.vectorFromYawPitch_approx(
                                            actualYaw + (isDownwardSwing ? 90 : -90), 0));
                                // ark of the cosmos should swing additional 360 degrees during downward swings
                                if (!isArkOfElements && isDownwardSwing) {
                                    actualPitch += 360d * i / loopTimes;
                                }
                                // downward swings should spawn projectiles
                                if (isDownwardSwing && i == 0) {
                                    String projType = isArkOfElements ? "元素日炎针" : "撕裂之针";
                                    Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 3, projType, false, 0);
                                    projVel.normalize().multiply(3);
                                    EntityHelper.spawnProjectile(ply, projVel, attrMap, projType);
                                    HashMap<String, Double> attrMapStar = (HashMap<String, Double>) attrMap.clone();
                                    attrMapStar.put("damage", attrMapStar.get("damage") * 0.35);
                                    for (int idxStar = 0; idxStar < 4; idxStar++) {
                                        Vector starProjVel;
                                        // element shoots projectile fwd, while cosmos spans their projectile around the circumference
                                        if (isArkOfElements)
                                            starProjVel = MathHelper.vectorFromYawPitch_approx(
                                                    actualYaw + Math.random() * 10 - 5, plyPitch + Math.random() * 10 - 5);
                                        else
                                            starProjVel = MathHelper.vectorFromYawPitch_approx(actualYaw, Math.random() * 360d);
                                        starProjVel.multiply(isArkOfElements ? 2 : 2.5);
                                        EntityHelper.spawnProjectile(ply, starProjVel, attrMapStar, isArkOfElements ? "元素之星" : "永恒之雷");
                                    }
                                }
                                // render additional scissor part if empowered
                                if (isCharged && i == indexStart) {
                                    // strike
                                    double bladePitch = actualPitch + (isDownwardSwing ? 30 : -30);
                                    Location bladeLoc = getScissorBladeLoc(startStrikeLoc,
                                            MathHelper.vectorFromYawPitch_approx(actualYaw, actualPitch),
                                            MathHelper.vectorFromYawPitch_approx(actualYaw, bladePitch),
                                            strikeLength, scissorsConnLen);
                                    GenericHelper.handleStrikeLine(ply, bladeLoc,
                                            actualYaw, bladePitch,
                                            strikeLength, strikeRadius, weaponType, color, damaged, attrMap, strikeLineInfo);
                                }
                            }
                            // reset strike info to render the other half of scissors (in default mechanism below)
                            if (strikeLineInfo.displayParticle) {
                                strikeLineInfo = strikeLineInfo.clone();
                                scissorItem = strikeLineInfo.particleInfo.spriteItem;
                                ItemMeta newMeta = scissorItem.getItemMeta();
                                newMeta.setDisplayName(weaponType + "2");
                                scissorItem.setItemMeta(newMeta);
                                strikeLineInfo.particleInfo.setSpriteItem(scissorItem);
                            }
                            break;
                        }
                        case "泰拉阔剑": {
                            if (progress < 0.5) {
                                yawMin = plyYaw;
                                yawMax = plyYaw;
                                pitchMin = plyPitch;
                                pitchMax = plyPitch;

                                double temp = 1 - 2 * progress;
                                temp *= temp;
                                actualPitch = pitchMin - 120 + 45 * temp * temp;
                            }
                            else {
                                double temp = 4 * progress - 3;
                                actualPitch = pitchMin + temp * 120;
                            }
                            // shoot projectile
                            if (i == (int) (loopTimes * 0.5)) {
                                Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), 4.25, "泰拉能量矢", false, 0);
                                projVel.normalize().multiply(4.25);
                                EntityHelper.spawnProjectile(ply, projVel, attrMap, "泰拉能量矢");
                            }
                            break;
                        }
                        case "星流之刃": {
                            actualPitch = pitchMin - 120;
                            if (progress < 0.5) {
                                yawMin = plyYaw;
                                yawMax = plyYaw;
                                pitchMin = plyPitch;
                                pitchMax = plyPitch;

                                strikeLength *= progress + 0.5;
                            }
                            else {
                                double temp = 2 * progress - 1;
                                actualPitch += temp * 300;

                                strikeLength *= 1.5 - progress;
                            }
                            // shoot projectile
                            int weaponCharge = getDurability(weaponItem, 8);
                            // the weapon spawns a projectile as long as the current frame is at most shoot threshold away from halfway
                            // in another word, the weapon shoots (threshold * 2 + 1) projectiles, each with 1 frame difference in time
                            int shootThreshold;
                            // 6 7 8
                            if (weaponCharge >= 6)
                                shootThreshold = 3;
                            // 4 5
                            else if (weaponCharge >= 4)
                                shootThreshold = 2;
                            // 1 2 3
                            else if (weaponCharge > 0)
                                shootThreshold = 1;
                            else
                                shootThreshold = 0;
                            if (i == indexStart && Math.abs(currentIndex - (int) (maxIndex * 0.5)) <= shootThreshold) {
                                HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                                projAttrMap.put("damage", projAttrMap.getOrDefault("damage", 100d) * 0.5);
                                Vector projVel = MathHelper.vectorFromYawPitch_approx(
                                        yawMin + Math.random() * 30 - 15, pitchMin + Math.random() * 30 - 15);
                                projVel.multiply(3);
                                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                        ply, projVel, projAttrMap, "星流能量矢");
                                String[] trailColorCandidates = {
                                        "234|205|134", // orange
                                        "125|255|255", // light blue
                                        "189|255|179", // light green
                                };
                                shootInfo.properties.put("trailColor",
                                        trailColorCandidates[ (int) (Math.random() * trailColorCandidates.length) ]);
                                EntityHelper.spawnProjectile(shootInfo);
                            }
                            break;
                        }
                    }
                    // strike
                    GenericHelper.handleStrikeLine(ply, startStrikeLoc, actualYaw, actualPitch,
                            strikeLength, strikeRadius, weaponType, color, damaged, attrMap, strikeLineInfo);
                    // for strike after first, do not display additional particle effect
                    strikeLineInfo.displayParticle = false;
                }
            }
        }
        if (currentIndex < maxIndex) {
            Vector finalLookDir = lookDir;
            Collection<Entity> finalDamaged = damaged;
            double finalYawMin = yawMin;
            double finalYawMax = yawMax;
            double finalPitchMin = pitchMin;
            double finalPitchMax = pitchMax;
            int finalCurrentIndex = currentIndex;
            double finalSize = size;
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleMeleeSwing(ply, attrMap, finalLookDir, finalDamaged, weaponSection,
                            finalYawMin, finalYawMax, finalPitchMin, finalPitchMax,
                            weaponType, weaponItem, finalSize, dirFixed, interpolateType,
                            finalCurrentIndex + 1, maxIndex, swingAmount), 1);
        }
    }
    // melee helper functions below
    protected static Vector getPlayerMeleeDir(Player ply, double size) {
        EntityHelper.AimHelperOptions rotationAimHelper = new EntityHelper.AimHelperOptions()
                .setAimMode(true)
                .setTicksTotal(0);
        // get targeted location
        Location targetLoc = getPlayerTargetLoc(ply, size,
                Setting.getOptionDouble(ply, Setting.Options.AIM_HELPER_RADIUS),
                0, rotationAimHelper, true);
        return MathHelper.getDirection(ply.getEyeLocation(), targetLoc, 1);
    }
    // stab and swing. NOT NECESSARILY MELEE DAMAGE!
    protected static boolean playerUseMelee(Player ply, String itemType, ItemStack weaponItem,
                                          ConfigurationSection weaponSection, HashMap<String, Double> attrMap, int swingAmount, boolean stabOrSwing) {
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        double size = weaponSection.getDouble("size", 3d);
        size *= attrMap.getOrDefault("meleeReachMulti", 1d);
//        double yaw = plyNMS.yaw, pitch = plyNMS.pitch;
//        Vector lookDir = MathHelper.vectorFromYawPitch_approx(yaw, pitch);
        Vector lookDir = getPlayerMeleeDir(ply, size);
        double yaw = MathHelper.getVectorYaw(lookDir), pitch = MathHelper.getVectorPitch(lookDir);

        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMeleeMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        // shoot projectile
        ConfigurationSection projectileInfo = weaponSection.getConfigurationSection("projectileInfo");
        if (projectileInfo != null) {
            HashMap<String, Double> attrMapProjectile = (HashMap<String, Double>) attrMap.clone();
            int shootInterval = (int) Math.floor(projectileInfo.getInt("interval", 1) * useSpeed);
            if (shootInterval < 1)
                shootInterval = 1;
            int shootAmount = 1;
            if ((swingAmount + 1) % shootInterval == 0) {
                double projectileSpeed = projectileInfo.getDouble("velocity", 1d);
                String projectileType = projectileInfo.getString("name", "");
                if (itemType.equals("绚辉圣剑")) {
                    double rdm = Math.random();
                    if (rdm < 0.25)
                        projectileType = "红色绚辉圣剑光束";
                    else if (rdm < 0.5)
                        projectileType = "绿色绚辉圣剑光束";
                    else if (rdm < 0.75)
                        projectileType = "黄色绚辉圣剑光束";
                    else
                        projectileType = "蓝色绚辉圣剑光束";
                }
                for (int i = 0; i < shootAmount; i ++) {
                    Projectile spawnedProjectile;
                    if (stabOrSwing) {
                        Vector offsetV = MathHelper.vectorFromYawPitch_approx(yaw, pitch);
                        offsetV.multiply(size);
                        Location shootLoc = ply.getEyeLocation().add(offsetV);
                        Vector projVel = getPlayerAimDir(ply, shootLoc, projectileSpeed, projectileType, false, 0);
                        projVel.normalize().multiply( projectileSpeed );
                        spawnedProjectile = EntityHelper.spawnProjectile(ply, shootLoc,
                                projVel, attrMapProjectile, EntityHelper.DamageType.MELEE, projectileType);
                    } else {
                        Vector projVel;
                        switch (itemType) {
                            case "风之刃":
                            case "风暴管束者":
                                projVel = MathHelper.vectorFromYawPitch_approx(plyNMS.yaw, plyNMS.pitch);
                                projVel.multiply( projectileSpeed );
                                break;
                            default:
                                projVel = getPlayerAimDir(ply, ply.getEyeLocation(), projectileSpeed, projectileType, false, 0);
                                projVel.normalize().multiply( projectileSpeed );
                        }
                        spawnedProjectile = EntityHelper.spawnProjectile(ply, ply.getEyeLocation(),
                                projVel, attrMapProjectile, EntityHelper.DamageType.MELEE, projectileType);
                    }
                    if (itemType.equals("钨钢螺丝刀")) {
                        spawnedProjectile.setVelocity(new Vector(0, 0.3, 0));
                        spawnedProjectile.addScoreboardTag("isWulfrumScrew");
                    }
                }
            }
        }

        // cool down, shoot direction and preparation for swing call
        switch (itemType) {
            case "元素方舟":
            case "鸿蒙方舟": {
                if (swingAmount % 5 == 4)
                    useTimeMulti *= 2.5;
                break;
            }
        }
        int coolDown = applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
        boolean dirFixed = weaponSection.getBoolean("dirFixed", true);
        int interpolateType = stabOrSwing ? 0 : 1;
        double yawMin = yaw, yawMax = yaw;
        double pitchMin, pitchMax;
        if (stabOrSwing) {
            pitchMin = pitch;
            pitchMax = pitch;
        }
        else {
            pitchMin = -110;
            pitchMax = 60;
        }
        switch (itemType) {
            case "残缺环境刃[拥怀之凛冽]":
            case "环境之刃[拥怀之凛冽]":
            case "真·环境之刃[拥怀之凛冽]": {
                switch (swingAmount % 3) {
                    case 0:
                        pitchMin = pitch - 50;
                        pitchMax = pitch + 50;
                        break;
                    case 1:
                        pitchMin = pitch + 50;
                        pitchMax = pitch - 50;
                        attrMap.put("damage", attrMap.getOrDefault("damage", 1d) * 1.14);
                        break;
                    case 2:
                        pitchMin = pitch;
                        pitchMax = pitch;
                        attrMap.put("damage", attrMap.getOrDefault("damage", 1d) * 1.4);
                        break;
                }
                coolDown /= 3;
                break;
            }
            case "残缺环境刃[林妖之轻抚]":
            case "环境之刃[林妖之轻抚]": {
                pitchMin = pitch - 90;
                pitchMax = pitch;
                coolDown /= 3;
                break;
            }
            case "真·环境之刃[缚囚之悼]": {
                if (swingAmount % 2 == 0) {
                    pitchMin = pitch - 90;
                    pitchMax = pitch + 45;
                }
                else {
                    pitchMin = pitch + 90;
                    pitchMax = pitch - 45;
                    attrMap.put("damage", attrMap.getOrDefault("damage", 1d) * 1.35);
                }
                break;
            }
            case "环境之刃[天国之神威]":
            case "真·环境之刃[兵匠之傲]":
            case "银河[凤凰之耀]": {
                double progress = Math.min(swingAmount, 35) / 35d;
                double spinAmount = 40 + progress * 135;
                size += progress * size;
                MetadataValue value = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_BIOME_BLADE_SPIN_PITCH);
                pitchMin = (value == null || swingAmount == 0) ? -180d : value.asDouble();
                pitchMax = pitchMin + spinAmount;
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_BIOME_BLADE_SPIN_PITCH, pitchMax);
                break;
            }
            case "远古方舟": {
                if (swingAmount % 2 == 0) {
                    pitchMin = pitch - 55;
                    pitchMax = pitch + 55;
                }
                else {
                    pitchMin = pitch + 55;
                    pitchMax = pitch - 55;
                }
                break;
            }
            case "元素方舟":
            case "鸿蒙方舟": {
                int swingPhase = swingAmount % 5;
                if (swingPhase == 1 || swingPhase == 3) {
                    pitchMin = pitch - 55;
                    pitchMax = pitch + 55;
                }
                else if (swingPhase == 4) {
                    pitchMin = pitch - 55;
                    pitchMax = pitch - 55;
                }
                else {
                    pitchMin = pitch + 55;
                    pitchMax = pitch - 55;
                }
                break;
            }
            case "泰拉阔剑":
            case "星流之刃": {
                pitchMin = pitch;
                pitchMax = pitch;
                break;
            }
        }
        handleMeleeSwing(ply, attrMap, lookDir, new HashSet<>(), weaponSection,
                yawMin, yawMax, pitchMin, pitchMax, itemType, weaponItem, size,
                dirFixed, interpolateType, 0, coolDown, swingAmount);
        return true;
    }
    protected static boolean playerUseWhip(Player ply, String itemType, ItemStack weaponItem,
                                          ConfigurationSection weaponSection, HashMap<String, Double> attrMap, int swingAmount) {
        double size = weaponSection.getDouble("size", 3d);
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMeleeMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        int coolDown = applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
        // note that whips are not applicable to aim helper: the player moves as the whip is being used!
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        double yaw = plyNMS.yaw, pitch = plyNMS.pitch;
        Vector lookDir = MathHelper.vectorFromYawPitch_approx(yaw, pitch);
        lookDir.normalize();
        size *= attrMap.getOrDefault("meleeReachMulti", 1d);
        // how many rays does this swing produce
        int meleeSwingAmount = 1;
        if (itemType.equals("晨星链刃"))
            meleeSwingAmount = 2;
        for (int swingIdx = 0; swingIdx < meleeSwingAmount; swingIdx ++) {
            // get swing offset
            double yawOffset, pitchOffset;
            switch (itemType) {
                case "日耀喷发剑":
                case "晨星链刃":
                    yawOffset = Math.random() * 180 - 90;
                    pitchOffset = Math.random() * 100 - 50;
                    break;
                default:
                    yawOffset = Math.random() * 30 - 15;
                    pitchOffset = 45;
            }
            handleMeleeSwing(ply, attrMap, lookDir, new HashSet<>(), weaponSection,
                    yaw - yawOffset, yaw + yawOffset, pitch - pitchOffset, pitch + pitchOffset,
                    itemType, weaponItem, size,
                    true, 2, 0, coolDown, swingAmount);
        }
        return true;
    }
    protected static boolean playerUseBoomerang(Player ply, String itemType, String weaponType,
                                                boolean autoSwing, HashMap<String, Double> attrMap, ConfigurationSection weaponSection) {
        // use the weapon
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        double projectileSpeed = weaponSection.getDouble("velocity", 5d);
        double distance = weaponSection.getDouble("distance", 10d);
        // note that the gravity of boomerangs are turned off in the boomerang class, so DO NOT use aim helper, it will cause issue.
        EntityHelper.AimHelperOptions aimHelperOptions = new EntityHelper.AimHelperOptions()
                .setAccelerationMode(Setting.getOptionBool(ply, Setting.Options.AIM_HELPER_ACCELERATION))
                .setAimMode(false)
                .setProjectileSpeed(projectileSpeed)
                .setProjectileGravity(0);
        // get targeted location
        Location targetLoc = getPlayerTargetLoc(ply,
                aimHelperOptions, true);
        Vector fireDir = targetLoc.subtract(ply.getEyeLocation()).toVector();
        fireDir.normalize();
        fireDir.multiply(projectileSpeed);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(ply, fireDir, attrMap, itemType);
        // spawn projectile
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMeleeMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        double useTime = attrMap.getOrDefault("useTime", 20d) * useTimeMulti;
        Boomerang entity = new Boomerang(shootInfo, distance, useTime);
        plyNMS.getWorld().addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // play sound
        playerUseItemSound(ply, weaponType, itemType, autoSwing);
        return true;
    }
    protected static boolean playerUseYoyo(Player ply, String itemType, String weaponType,
                                           boolean autoSwing, HashMap<String, Double> attrMap, ConfigurationSection weaponSection) {
        // use the weapon
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        Vector facingDir = MathHelper.vectorFromYawPitch_approx(plyNMS.yaw, plyNMS.pitch);
        double projectileSpeed = weaponSection.getDouble("velocity", 1.5d);
        double reach = weaponSection.getDouble("reach", 10d);
        int duration = weaponSection.getInt("duration", 100);
        facingDir.multiply(projectileSpeed);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(ply, facingDir, attrMap, itemType);
        // spawn projectile
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMeleeMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        double useTime = attrMap.getOrDefault("useTime", 20d) * useTimeMulti;
        double recoilPoolMultiplier = attrMap.getOrDefault("recoilPoolMultiplier", 0.7);
        Yoyo entity = new Yoyo(shootInfo, reach, useTime, recoilPoolMultiplier, duration);
        plyNMS.getWorld().addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // play sound
        playerUseItemSound(ply, weaponType, itemType, autoSwing);
        return true;
    }
    protected static boolean playerUseFlail(Player ply, String itemType, String weaponType,
                                            boolean autoSwing, HashMap<String, Double> attrMap, ConfigurationSection weaponSection) {
        // use the weapon
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        double projectileSpeed = weaponSection.getDouble("velocity", 5d);
        double reach = weaponSection.getDouble("reach", 10d);
        Vector fireDir = getPlayerAimDir(ply, ply.getEyeLocation(), projectileSpeed, itemType, false, 0);
        fireDir.normalize();
        fireDir.multiply(projectileSpeed);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(ply, fireDir, attrMap, itemType);
        // spawn projectile
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMeleeMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        double useTime = attrMap.getOrDefault("useTime", 20d) * useTimeMulti;
        Flail entity = new Flail(shootInfo, reach, useTime);
        plyNMS.getWorld().addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // play sound
        playerUseItemSound(ply, weaponType, itemType, autoSwing);
        return true;
    }
    // ranged helper functions below
    protected static double calculateProjectileSpeed(HashMap<String, Double> attrMap) {
        double projectileSpeed = attrMap.getOrDefault("projectileSpeed", 1d);
        projectileSpeed *= attrMap.getOrDefault("projectileSpeedMulti", 1d);
        if (attrMap.equals("BOW"))
            projectileSpeed *= attrMap.getOrDefault("projectileSpeedArrowMulti", 1d);
        // in Terraria, projectile speed is the amount of pixels moved for each tick, a block is 16 pixels wide
        // however, Terraria has 60 ticks/sec while Minecraft has 20 ticks/sec
        projectileSpeed = projectileSpeed / 5.3333;
        return projectileSpeed;
    }
    protected static void handleRangedFire(Player ply, HashMap<String, Double> attrMapOriginal, ConfigurationSection weaponSection,
                                         ItemStack weaponItem, int fireIndex, int swingAmount,
                                         String itemType, String weaponType, String ammoTypeInitial,
                                         boolean isLoadingWeapon, boolean autoSwing) {
        int fireRoundMax = weaponSection.getInt("fireRounds", 1);
        int fireAmount = isLoadingWeapon ? swingAmount : weaponSection.getInt("shots", 1);
        double spread = weaponSection.getDouble("offSet", -1d);
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        EntityHelper.DamageType damageType = EntityHelper.getDamageType(ply);
        double projectileSpeed = calculateProjectileSpeed(attrMapOriginal);
        // account for arrow attribute.
        String ammoType = ammoTypeInitial;
        HashMap<String, Double> attrMap = (HashMap<String, Double>) attrMapOriginal.clone();
        List<String> ammoConversion = weaponSection.getStringList("ammoConversion." + ammoType);
        if (ammoConversion.isEmpty())
            ammoConversion = weaponSection.getStringList("ammoConversion.ALL");
        // helper aim
        Vector facingDir = getPlayerAimDir(ply, ply.getEyeLocation(), projectileSpeed, ammoType, false, 0);
        facingDir.normalize();
        double facingDirYaw = MathHelper.getVectorYaw(facingDir), facingDirPitch = MathHelper.getVectorPitch(facingDir);


        // tweaks before firing, such as extra projectiles from weapons
        // note that in this section, the attributes from arrow used are not accounted for.
        switch (itemType) {
            // forward extra projectile
            case "星璇机枪":
            case "玛瑙爆破枪":
            case "玛瑙链炮":
            case "奥妮克希亚":
            case "凝胶弓":
            case "暗之回响":
            case "恒吹雪":
            case "蘑菇狙击枪":
            case "鬼火弓": {
                Location fireLoc = ply.getEyeLocation();
                Vector fireVelocity = facingDir.clone();
                HashMap<String, Double> attrMapExtraProjectile = (HashMap<String, Double>) attrMap.clone();
                boolean shouldFire = true;
                String extraProjectileType = "";
                int extraProjectileAmount = 1;
                switch (itemType) {
                    case "玛瑙爆破枪":
                    case "玛瑙链炮":
                    case "奥妮克希亚": {
                        extraProjectileType = "玛瑙能量";
                        break;
                    }
                    case "星璇机枪": {
                        if (swingAmount % 5 == 4) {
                            extraProjectileType = "星璇导弹";
                            attrMapExtraProjectile.put("damage", 140d);
                        } else {
                            shouldFire = false;
                        }
                        break;
                    }
                    case "凝胶弓": {
                        extraProjectileType = "凝胶束";
                        attrMapExtraProjectile.put("damage", attrMapExtraProjectile.get("damage") * 0.5);
                        extraProjectileAmount = 2;
                        break;
                    }
                    case "暗之回响": {
                        extraProjectileType = "水晶镖";
                        attrMapExtraProjectile.put("damage", attrMapExtraProjectile.get("damage") * 0.75);
                        break;
                    }
                    case "恒吹雪": {
                        extraProjectileType = "冰坠箭";
                        break;
                    }
                    case "蘑菇狙击枪": {
                        extraProjectileType = "追踪蘑菇";
                        attrMapExtraProjectile.put("damage", attrMapExtraProjectile.get("damage") * 0.65);
                        break;
                    }
                    case "鬼火弓": {
                        extraProjectileType = "恶魔焰火球";
                        break;
                    }
                }
                // setup projectile velocity
                if (shouldFire) {
                    fireVelocity.multiply(projectileSpeed);
                    for (int i = 0; i < extraProjectileAmount; i ++) {
                        EntityHelper.spawnProjectile(ply, fireLoc, fireVelocity, attrMapExtraProjectile,
                                EntityHelper.getDamageType(ply), extraProjectileType);
                    }
                }
                break;
            }
            // multiple fire round, ammo conversion tweak for extra fire round
            case "烈风": {
                Location fireLoc = ply.getEyeLocation();
                HashMap<String, Double> attrMapExtraProjectile = (HashMap<String, Double>) attrMap.clone();
                attrMapExtraProjectile.put("damage", attrMapExtraProjectile.get("damage") * 0.5);

                for (int i = -1; i <= 1; i ++) {
                    Vector fireVelocity = MathHelper.vectorFromYawPitch_approx(facingDirYaw, facingDirPitch + i * 10);
                    EntityHelper.spawnProjectile(ply, fireLoc, fireVelocity, attrMapExtraProjectile,
                            EntityHelper.getDamageType(ply), "天蓝羽毛");
                }
                break;
            }
            case "洲陆巨弓": {
                if (fireIndex == 2) {
                    ammoConversion = new ArrayList<>(2);
                    ammoConversion.add("诅咒箭");
                    ammoConversion.add("狱炎箭");
                    fireAmount = 2;
                }
                break;
            }
            case "荆棘藤": {
                if (fireIndex == 2) {
                    ammoConversion = new ArrayList<>(2);
                    ammoConversion.add("叶绿箭");
                    ammoConversion.add("毒液箭");
                    fireAmount = 2;
                }
                break;
            }
            case "劲弩": {
                fireAmount = fireIndex;
                break;
            }
            case "锋叶十字弩": {
                if (fireIndex == 2) {
                    // make its size > 1, so it converts the ammo in the loop
                    ammoConversion = new ArrayList<>(2);
                    ammoConversion.add("树叶");
                    ammoConversion.add("树叶");
                    fireAmount = 2;
                }
                break;
            }
            case "创世纪": {
                double conversionRatio = (swingAmount - 20) / 30d;
                // it converts merely into a plasma bolt if it is not powerful enough yet
                if (Math.random() > conversionRatio) {
                    ammoConversion = new ArrayList<>(1);
                    ammoConversion.add("创世之光");
                }
                break;
            }
            case "赤陨霸龙弓": {
                ammoConversion = new ArrayList<>(2);
                if (swingAmount <= 40) {
                    ammoConversion.add("炎龙崩啸");
                }
                else {
                    ammoConversion.add("充能炎龙崩啸");
                    if (swingAmount >= 50) {
                        ammoConversion.add("追踪炎龙崩啸");
                    }
                }
                break;
            }
            case "巨舰鲨": {
                if (swingAmount % 2 == 1) {
                    ammoConversion = new ArrayList<>(1);
                    ammoConversion.add("巨舰水流");
                }
                break;
            }
            case "海龙": {
                if (swingAmount % 2 == 1) {
                    ammoConversion = new ArrayList<>(1);
                    ammoConversion.add(swingAmount % 18 == 1 ? "海龙追踪火箭" : "巨舰水流");
                }
                break;
            }
            case "巨龙之息": {
                if (fireIndex == 2) {
                    ammoConversion = new ArrayList<>(1);
                    ammoConversion.add("龙息弹");
                }
                break;
            }
            case "巨龙之息_RIGHT_CLICK": {
                if (Math.random() < 0.5) {
                    ammoConversion = new ArrayList<>(2);
                    ammoConversion.add("龙息弹");
                    ammoConversion.add("龙息弹");
                }
                break;
            }
            case "太虚星龙炮": {
                if (swingAmount % 2 == 1) {
                    ammoConversion = new ArrayList<>(1);
                    ammoConversion.add("海龙追踪火箭");
                }
                break;
            }
            // other
            case "屠夫": {
                spread = Math.min(4 + swingAmount, 40);
                break;
            }
            case "精金粒子加速器": {
                ammoType = swingAmount % 2 == 0 ? "粉色粒子光束" : "蓝色粒子光束";
                break;
            }
            case "九头蛇": {
                int amountHead = Math.min( swingAmount / 5, 3);
                fireAmount = 1 + amountHead;
                break;
            }
            case "珍珠之神": {
                switch (swingAmount % 7) {
                    case 1:
                    case 2:
                        fireAmount = 3;
                        break;
                    case 3:
                    case 4:
                        fireAmount = 5;
                        break;
                    case 5:
                    case 6:
                        fireAmount = 7;
                        break;
                }
                break;
            }
            case "电话会议":
            case "散播者": {
                Vec3D locNMS = MathHelper.toNMSVector(ply.getLocation().toVector());
                Set<HitEntityInfo> hitCandidates = HitEntityInfo.getEntitiesHit(
                        plyNMS.world,
                        locNMS,
                        locNMS,
                        48,
                        (net.minecraft.server.v1_12_R1.Entity toCheck) ->
                                toCheck instanceof EntityLiving &&
                                        EntityHelper.checkCanDamage(ply, toCheck.getBukkitEntity(), true));
                if (! hitCandidates.isEmpty()) {
                    Iterator<HitEntityInfo> hitCandidateIter = hitCandidates.iterator();
                    for (int i = 0; i < 12; i++) {
                        if (!hitCandidateIter.hasNext()) hitCandidateIter = hitCandidates.iterator();
                        Entity target = hitCandidateIter.next().getHitEntity().getBukkitEntity();
                        Location shootLoc = target.getLocation().add(
                                Math.random() * 16 - 8,
                                Math.random() * 5 + 20,
                                Math.random() * 16 - 8);
                        Vector projVel = MathHelper.getDirection(shootLoc,
                                ((LivingEntity) target).getEyeLocation(), projectileSpeed);
                        EntityHelper.spawnProjectile(ply, shootLoc, projVel, attrMap, damageType, ammoType);
                    }
                }
                break;
            }
            case "天堂之风": {
                int maxCharge = 15;
                int currCharge = getDurability(weaponItem, maxCharge);
                double chargeRatio = (double) currCharge / maxCharge;
                // charges increases damage
                // the quadratic style makes extra damage obvious only when it is almost fully charged
                attrMap.put("damage", attrMapOriginal.get("damage") * (1 + chargeRatio * chargeRatio * 3));
                // first shot of each fire round increases the charge
                if (fireIndex == 1)
                    currCharge ++;
                // if fully charged, convert into empowered bolts
                if (currCharge >= maxCharge) {
                    ammoConversion = new ArrayList<>(1);
                    ammoConversion.add("天堂充能星流晶体");
                    // on last shot of charged attack, remove all charges
                    if (fireIndex == fireRoundMax)
                        currCharge = 0;
                }
                // update durability
                setDurability(weaponItem, maxCharge, currCharge);
                break;
            }
            case "诘责": {
                // shots are fired with a delay instead of launched all at once
                fireRoundMax = swingAmount;
                fireAmount = 1;
                break;
            }
            case "血祸沸炉": {
                // applies a damaging debuff to the user
                EntityHelper.applyEffect(ply, "血液沸腾", 20);
                break;
            }
        }
        // if the ammo could get converted into multiple possible projectiles, handle them separately in the loop instead.
        // ammo attribute and its contribution to projectile speed is in this section. DO NOT change condition to == 1
        if  (ammoConversion.size() <= 1) {
            // ammo conversion if applicable
            if  (ammoConversion.size() == 1)
                ammoType = ammoConversion.get(0);
            ConfigurationSection ammoAttributeSection = TerrariaHelper.itemConfig.getConfigurationSection(ammoType + ".attributes");
            // if the converted ammo does not have attributes that overrides the original, default to the original
            if (ammoAttributeSection == null)
                ammoAttributeSection = TerrariaHelper.itemConfig.getConfigurationSection(ammoTypeInitial + ".attributes");
            if (ammoAttributeSection != null) {
                EntityHelper.tweakAllAttributes(attrMap, ammoAttributeSection, true);
                // update projectile speed
                projectileSpeed = calculateProjectileSpeed(attrMap);
            }
            // calculate aim direction again, different ammo has different properties.
            facingDir = getPlayerAimDir(ply, ply.getEyeLocation(), projectileSpeed, ammoType, false, 0);
            facingDir.normalize();
            facingDirYaw = MathHelper.getVectorYaw(facingDir);
            facingDirPitch = MathHelper.getVectorPitch(facingDir);
        }
        // spawn projectiles in the loop body
        for (int i = 0; i < fireAmount; i ++) {
            // account for arrow attribute.
            // if the ammo could get converted into multiple possible projectiles, handle them separately here in the loop.
            if  (ammoConversion.size() > 1) {
                attrMap = (HashMap<String, Double>) attrMapOriginal.clone();
                ammoType = ammoConversion.get((int) (Math.random() * ammoConversion.size()));
                ConfigurationSection ammoAttributeSection =
                        TerrariaHelper.itemConfig.getConfigurationSection(ammoType + ".attributes");
                // if the converted ammo does not have attributes that overrides the original, default to the original
                if (ammoAttributeSection == null)
                    ammoAttributeSection = TerrariaHelper.itemConfig.getConfigurationSection(ammoTypeInitial + ".attributes");
                if (ammoAttributeSection != null) {
                    EntityHelper.tweakAllAttributes(attrMap, ammoAttributeSection, true);
                    // update projectile speed
                    projectileSpeed = calculateProjectileSpeed(attrMap);
                }
                // calculate aim direction again, different ammo has different properties.
                facingDir = getPlayerAimDir(ply, ply.getEyeLocation(), projectileSpeed, ammoType, false, 0);
                facingDir.normalize();
                facingDirYaw = MathHelper.getVectorYaw(facingDir);
                facingDirPitch = MathHelper.getVectorPitch(facingDir);
            }
            Location fireLoc = ply.getEyeLocation();
            Vector fireVelocity = facingDir.clone();
            // bullet spread
            if (spread > 0d) {
                fireVelocity.multiply(spread);
                fireVelocity.add(MathHelper.randomVector());
                fireVelocity.normalize();
            }
            // handle special weapons (pre-firing)
            switch (itemType) {
                case "海啸弓":
                case "黑翼蝙蝠弓":
                case "荆棘藤":
                case "季风":
                case "啸流":
                case "赤陨霸龙弓_RIGHT_CLICK": {
                    fireLoc.add(MathHelper.vectorFromYawPitch_approx(facingDirYaw,
                                    facingDirPitch + 12.5 * (i - fireAmount / 2))
                            .multiply(1.5));
                    break;
                }
                case "珍珠之神":
                case "黄金之鹰":
                case "无穷":
                case "无穷_RIGHT_CLICK":
                case "宇宙之源":
                case "巨龙之息":
                case "巨龙之息_RIGHT_CLICK": {
                    double pitchOffset, shootLocOffsetLen;
                    switch (itemType) {
                        case "珍珠之神":
                            pitchOffset = 7.5;
                            shootLocOffsetLen = 1.5;
                            break;
                        case "黄金之鹰":
                            pitchOffset = 1;
                            shootLocOffsetLen = 1.25;
                            break;
                        case "无穷":
                        case "无穷_RIGHT_CLICK":
                            double progress = (double) fireIndex / fireRoundMax;
                            if (swingAmount % 2 == 1)
                                progress = 1 - progress;
                            pitchOffset = 2 * progress;
                            shootLocOffsetLen = 2;
                            // skip the middle bullet
                            if (i == 1)
                                i ++;
                            break;
                        case "宇宙之源":
                            pitchOffset = 0.5;
                            shootLocOffsetLen = 1.25;
                            break;
                        case "巨龙之息":
                            pitchOffset = 0.5;
                            shootLocOffsetLen = 1.5;
                            break;
                        case "巨龙之息_RIGHT_CLICK":
                            pitchOffset = 1.5;
                            shootLocOffsetLen = 1.5;
                            // randomize the ammo conversion after each shot
                            ammoConversion = new ArrayList<>(2);
                            if (Math.random() < 0.5) {
                                ammoConversion.add("龙息弹");
                                ammoConversion.add("龙息弹");
                            }
                            // convert it back to original, otherwise it will not be updated
                            else {
                                ammoConversion.add(ammoTypeInitial);
                                ammoConversion.add(ammoTypeInitial);
                            }
                            break;
                        default:
                            pitchOffset = 1;
                            shootLocOffsetLen = 1;
                    }
                    Vector shootLocOffsetDir = MathHelper.vectorFromYawPitch_approx(facingDirYaw,
                            facingDirPitch + pitchOffset * (i - fireAmount / 2));
                    fireVelocity = shootLocOffsetDir.clone();
                    fireLoc.add(shootLocOffsetDir.multiply(shootLocOffsetLen));
                    break;
                }
                case "湮灭千星":
                case "代达罗斯风暴弓":
                case "风暴":
                case "血陨": {
                    Vector offset = new Vector(Math.random() * 30 - 15, 20 + Math.random() * 20, Math.random() * 30 - 15);
                    fireVelocity = offset.clone().multiply(-1).normalize();
                    Location destination = getPlayerTargetLoc(ply,
                            new EntityHelper.AimHelperOptions()
                                    .setAimMode(true)
                                    .setRandomOffsetRadius(5)
                                    .setTicksTotal(offset.length() / projectileSpeed), true);
                    fireLoc = destination.add(offset);
                    break;
                }
                case "天堂之风": {
                    double pitchOffset = -20 * MathHelper.xsin_degree( 360d * (fireIndex - 1d) / fireRoundMax );
                    fireVelocity = MathHelper.vectorFromYawPitch_approx(facingDirYaw, facingDirPitch + pitchOffset);
                    break;
                }
                // should not shoot for some reason
                case "异象纳米枪": {
                    if (fireIndex <= 5)
                        fireVelocity = null;
                    break;
                }
            }
            // do not shoot if velocity is not set
            if (fireVelocity == null)
                continue;
            // set up projectile velocity
            fireVelocity.multiply(projectileSpeed);
            Projectile firedProjectile = EntityHelper.spawnProjectile(ply, fireLoc, fireVelocity, attrMap,
                    damageType, ammoType);
            // handle special weapons (post-firing)
            switch (itemType) {
                case "幻象弓":
                case "幻魔": {
                    firedProjectile.addScoreboardTag("isVortex");
                    break;
                }
                case "地狱降临": {
                    firedProjectile.addScoreboardTag("isHellborn");
                    break;
                }
                case "精金粒子加速器": {
                    firedProjectile.addScoreboardTag("isAPA");
                    break;
                }
            }
        }
        // if this is a delayed shot, play item swing sound
        playerUseItemSound(ply, weaponType, itemType, autoSwing);
        // extra delayed shots
        if (fireIndex < fireRoundMax) {
            int fireRoundDelay = weaponSection.getInt("fireRoundsDelay", 20);
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleRangedFire(ply, attrMapOriginal, weaponSection, weaponItem,
                            fireIndex + 1, swingAmount,
                            itemType, weaponType, ammoTypeInitial, isLoadingWeapon, autoSwing)
                    , fireRoundDelay);
        }
    }
    public static String consumePlayerAmmo(Player ply, Predicate<ItemStack> ammoPredicate, double consumptionRate) {
        ItemStack ammo = PlayerHelper.getFirstItem(ply, ammoPredicate, true);
        if (ammo == null) return null;
        String ammoName = ItemHelper.splitItemName(ammo)[1];
        if (Math.random() < consumptionRate) ammo.setAmount(ammo.getAmount() - 1);
        return ammoName;
    }
    protected static boolean playerUseRanged(Player ply, String itemType, int swingAmount, ItemStack weaponItem,
                                             String weaponType, boolean isLoadingWeapon, boolean autoSwing,
                                             ConfigurationSection weaponSection, HashMap<String, Double> attrMap) {
        Predicate<ItemStack> ammoPredicate = null;
        String ammoType = null;
        double consumptionRate = attrMap.getOrDefault("ammoConsumptionRate", 1d);
        // setup basic variables according to damage type
        switch (weaponType) {
            case "BOW": {
                ammoPredicate = (itemStack) -> itemStack.getType() == Material.ARROW;
                consumptionRate *= attrMap.getOrDefault("arrowConsumptionRate", 1d);
                break;
            }
            case "GUN": {
                ammoPredicate = (itemStack) -> itemStack.getType() == Material.SLIME_BALL;
                break;
            }
            case "ROCKET": {
                ammoPredicate = (itemStack) -> itemStack.getType() == Material.BLAZE_POWDER;
                break;
            }
            case "SPECIAL_AMMO": {
                String ammunitionType = weaponSection.getString("ammo", "_火枪子弹");
                if (ammunitionType.startsWith("_")) {
                    consumptionRate = 0d;
                    ammoType = ammunitionType.substring(1);
                    ammoPredicate = (itemStack) -> true;
                } else {
                    ammoPredicate = (itemStack) -> ItemHelper.splitItemName(itemStack)[1].equals(ammunitionType);
                }
                break;
            }
        }
        // for special_ammo that does not require any ammunition, ammoType should not be null here
        // if ammo is not set, find the first ammo that is suitable
        if (ammoType == null) {
            ammoType = consumePlayerAmmo(ply, ammoPredicate, consumptionRate);
        }
        // if no suitable ammo exists
        if (ammoType == null) return false;
        // otherwise, use the weapon.
        handleRangedFire(ply, attrMap, weaponSection, weaponItem,1, swingAmount,
                itemType, weaponType, ammoType, isLoadingWeapon, autoSwing);
        // apply CD
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedRangedMulti", 1d);
        // special weapon handling
        switch (itemType) {
            case "屠夫": {
                useSpeed *= Math.min(1 + (swingAmount / 6d), 6);
                break;
            }
            case "九头蛇": {
                int amountHead = Math.min( swingAmount / 5, 3);
                switch (amountHead) {
                    case 1:
                        useSpeed *= 0.9;
                        break;
                    case 2:
                        useSpeed *= 0.75;
                        break;
                    case 3:
                        useSpeed *= 0.64;
                        break;
                }
                break;
            }
            case "赤陨霸龙弓": {
                useSpeed *= 1 + (Math.min(swingAmount, 50) ) / 15d;
                break;
            }
            case "瘟疫": {
                useSpeed *= 1 + Math.min(swingAmount, 30) / 15d;
                break;
            }
        }
        double useTimeMulti = 1 / useSpeed;
        applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
        // armor set
        switch (PlayerHelper.getArmorSet(ply)) {
            case "渊泉远程套装": {
                String coolDownTag = "temp_hydroThermicFireball";
                if (! ply.getScoreboardTags().contains(coolDownTag)) {
                    // cool down
                    EntityHelper.handleEntityTemporaryScoreboardTag(ply, coolDownTag, 10);
                    // damage setup
                    String projType = "大熔岩火球";
                    double projSpd = 2;

                    Vector aimDir = getPlayerAimDir(ply, ply.getEyeLocation(), projSpd, projType, false, 0);
                    HashMap<String, Double> fireballAttrMap = (HashMap<String, Double>) attrMap.clone();
                    double fireballDmg = Math.min(125, fireballAttrMap.getOrDefault("damage", 100d) * 0.25);
                    fireballAttrMap.put("damage", fireballDmg);
                    // projectile
                    aimDir.normalize().multiply(projSpd);
                    EntityHelper.spawnProjectile(ply, aimDir,
                            fireballAttrMap, EntityHelper.getDamageType(ply), projType);
                }
                break;
            }
            case "弑神者远程套装":
            case "金源远程套装": {
                String coolDownTag = "temp_godSlayerRanged";
                if (! ply.getScoreboardTags().contains(coolDownTag)) {
                    // cool down
                    EntityHelper.handleEntityTemporaryScoreboardTag(ply, coolDownTag, 50);
                    // damage setup
                    String projType = "弑神榴霰弹";
                    double projSpd = 3;

                    Vector aimDir = getPlayerAimDir(ply, ply.getEyeLocation(), projSpd, projType, false, 0);
                    HashMap<String, Double> fireballAttrMap = (HashMap<String, Double>) attrMap.clone();
                    double fireballDmg = Math.min(1600, fireballAttrMap.getOrDefault("damage", 100d) * 1.25);
                    fireballAttrMap.put("damage", fireballDmg);
                    // projectile
                    aimDir.normalize().multiply(projSpd);
                    EntityHelper.spawnProjectile(ply, aimDir,
                            fireballAttrMap, EntityHelper.getDamageType(ply), projType);
                }
                break;
            }
        }
        return true;
    }
    protected static boolean playerUseThrowingProjectile(Player ply, String itemType, String weaponType,
                                                         boolean autoSwing, HashMap<String, Double> attrMap, ItemStack tool) {
        double consumptionRate = attrMap.getOrDefault("ammoConsumptionRate", 1d);
        // remove one of the player's tool
        if (Math.random() < consumptionRate) {
            tool.setAmount(tool.getAmount() - 1);
        }
        // use the weapon
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        double projectileSpeed = attrMap.getOrDefault("projectileSpeed", 1d);
        projectileSpeed *= attrMap.getOrDefault("projectileSpeedMulti", 1d);
        Vector facingDir = getPlayerAimDir(ply, ply.getEyeLocation(), projectileSpeed, itemType, false, 0);
        facingDir.normalize().multiply(projectileSpeed);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(ply, facingDir, attrMap, itemType);
        EntityHelper.spawnProjectile(shootInfo);
        // play sound
        playerUseItemSound(ply, weaponType, itemType, autoSwing);
        // apply CD
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedRangedMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
        return true;
    }
    // magic helper functions below
    protected static boolean consumeMana(Player ply, int mana) {
        if (mana <= 0) return true;
        if (ply.getLevel() < mana) {
            // consume mana potion if applicable
            Collection<String> accessories = PlayerHelper.getAccessories(ply);
            if (    accessories.contains("魔力花") ||
                    accessories.contains("磁花") ||
                    accessories.contains("空灵护符")) {
                playerQuickUsePotion(ply, QuickBuffType.MANA);
            }
        }
        int currMana = ply.getLevel();
        if (currMana >= mana) {
            currMana -= mana;
            ply.setLevel(currMana);
            int maxMana = EntityHelper.getAttrMap(ply).getOrDefault("maxMana", 20d).intValue();
            int regenDelay = (int) Math.ceil(0.3 * (
                    (1 - ((double) currMana / maxMana)) * 240 + 45   ));
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_DELAY, regenDelay);
            return true;
        }
        return false;
    }
    protected static void handleMagicProjectileFire(Player ply, HashMap<String, Double> attrMap, ConfigurationSection weaponSection,
                                                  int swingAmount, int fireIndex, String itemType, String weaponType, boolean autoSwing) {
        int fireRoundMax = weaponSection.getInt("fireRounds", 1);
        int fireAmount = weaponSection.getInt("shots", 1);
        double spread = weaponSection.getDouble("offSet", -1d);
        String projectileName = weaponSection.getString("projectileName", "小火花");
        double projectileSpeed = attrMap.getOrDefault("projectileSpeed", 1d);
        projectileSpeed *= attrMap.getOrDefault("projectileSpeedMulti", 1d);
        // the reasoning for this number is shown in ranged projectile section
        projectileSpeed /= 5.3333;
        Vector facingDir = getPlayerAimDir(ply, ply.getEyeLocation(), projectileSpeed, projectileName, false, 0);
        facingDir.normalize();
        // handle special weapons
        switch (itemType) {
            case "暗晶风暴": {
                if (Math.random() < 0.5)
                    fireAmount = 2;
                break;
            }
            case "酸蚀毒蝰": {
                if (fireIndex == 2) {
                    fireAmount = 5;
                    spread = 30;
                }
                break;
            }
            case "归元漩涡_RIGHT_CLICK": {
                switch (swingAmount % 10) {
                    case 0:
                    case 1:
                    case 2:
                        fireAmount = 0;
                        break;
                    case 9:
                        projectileName = "归元漩涡";
                        attrMap.put("damage", attrMap.getOrDefault("damage", 10d) * 2);
                        break;
                }
                break;
            }
            case "异端僭越": {
                // no projectile fired after first swing
                if (swingAmount != 0)
                    fireAmount = 0;
                break;
            }
        }
        for (int i = 0; i < fireAmount; i ++) {
            Location fireLoc = ply.getEyeLocation();
            Vector fireVelocity = facingDir.clone();
            // bullet spread
            if (spread > 0d) {
                fireVelocity.multiply(spread);
                fireVelocity.add(MathHelper.randomVector());
                fireVelocity.normalize();
            }
            // handle special weapons (pre-firing)
            switch (itemType) {
                case "月之耀斑":
                case "暴雪法杖":
                case "冰坠法杖":
                case "天空之羽":
                case "飞龙之歌":
                case "星龙破空杖":
                case "维苏威阿斯":
                case "幻星法杖":
                case "行星法杖":
                case "死亡冰雹":
                case "星火凤凰雨": {
                    Vector offset = new Vector(Math.random() * 30 - 15, 20 + Math.random() * 20, Math.random() * 30 - 15);
                    fireVelocity = offset.clone().multiply(-1).normalize();
                    EntityHelper.AimHelperOptions options = new EntityHelper.AimHelperOptions()
                            .setAimMode(true)
                            .setTicksTotal(offset.length() / projectileSpeed);
                    switch (itemType) {
                        case "月之耀斑":
                            options.setRandomOffsetRadius(1);
                            break;
                        case "暴雪法杖":
                            options.setRandomOffsetRadius(2.5);
                            break;
                        case "星火凤凰雨":
                            options.setRandomOffsetRadius(2);
                            break;
                        case "星龙破空杖":
                            options.setRandomOffsetRadius(3);
                            if (Math.random() < 0.35) {
                                projectileName = "破空暗夜飞龙";
                                attrMap.put("damageMagicMulti", 3.75);
                            }
                            else {
                                projectileName = "破空星辰";
                                attrMap.put("damageMagicMulti", 1.5);
                            }
                            break;
                        case "死亡冰雹":
                            options.setRandomOffsetRadius(1);
                            offset = new Vector(Math.random() * 20 - 10, 16, Math.random() * 20 - 10);
                            fireVelocity = offset.clone().multiply(-1).normalize();
                            options.setTicksTotal(1);
                            break;
                        default:
                            options.setRandomOffsetRadius(0.5);
                            break;
                    }
                    Location destination = getPlayerTargetLoc(ply, options, true);
                    fireLoc = destination.add(offset);
                    break;
                }
                case "深渊女神之复仇":
                case "神圣天罚": {
                    Vector offset = new Vector(Math.random() * 30 - 15, -20 - Math.random() * 20, Math.random() * 30 - 15);
                    fireVelocity = offset.clone().multiply(-1).normalize();
                    EntityHelper.AimHelperOptions options = new EntityHelper.AimHelperOptions()
                            .setAimMode(true)
                            .setTicksTotal(offset.length() / projectileSpeed);
                    switch (itemType) {
                        case "深渊女神之复仇":
                            options.setRandomOffsetRadius(3);
                            break;
                        case "神圣天罚":
                            options.setRandomOffsetRadius(2);
                            break;
                        default:
                            options.setRandomOffsetRadius(0.5);
                            break;
                    }
                    Location destination = getPlayerTargetLoc(ply, options, true);
                    fireLoc = destination.add(offset);
                    break;
                }
                case "星云烈焰": {
                    if (Math.random() < 0.2) {
                        projectileName = "星云烈焰炮弹";
                        fireVelocity.multiply(1.25);
                        attrMap.put("damageMulti", attrMap.getOrDefault("damageMulti", 1d) + 2);
                    }
                    break;
                }
                case "菌杖":
                case "命运神启": {
                    fireVelocity = MathHelper.randomVector();
                    fireLoc = ply.getEyeLocation().add(
                            Math.random() * 5 - 2.5,
                            Math.random() * 5 - 2.5,
                            Math.random() * 5 - 2.5);
                    break;
                }
                case "裂天剑":
                case "狱炎裂空":
                case "终结裂空戟":
                case "狂野复诵":
                case "光之舞": {
                    fireLoc = ply.getEyeLocation().add(
                            Math.random() * 4 - 2,
                            Math.random() * 4 - 2,
                            Math.random() * 4 - 2);
                    fireVelocity = getPlayerAimDir(ply, fireLoc, projectileSpeed, projectileName,
                            false, 0);
                    if (itemType.equals("光之舞") && i == 0) {
                        // charge up a powerful attack
                        ItemStack weaponItem = ply.getInventory().getItemInMainHand();
                        int charge = getDurability(weaponItem, 90);
                        if (charge++ >= 90) {
                            charge = 0;
                            HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                            projAttrMap.put("damage", 233333d);
                            projAttrMap.put("damageMulti", 1d);
                            projAttrMap.put("crit", 100d);
                            EntityHelper.spawnProjectile(ply, ply.getEyeLocation().add(0, 15, 0), new Vector(),
                                    projAttrMap, EntityHelper.DamageType.MAGIC, "光之舞闪光");
                        }
                        setDurability(weaponItem, 90, charge);
                    }
                    fireVelocity.normalize();
                    break;
                }
                case "血涌": {
                    EntityHelper.AimHelperOptions aimHelper = new EntityHelper.AimHelperOptions()
                            .setAimMode(true)
                            .setTicksTotal(10);
                    Location aimLoc = getPlayerTargetLoc(ply, aimHelper, true);
                    aimLoc.add(Math.random() * 8 - 4, projectileSpeed * -10, Math.random() * 8 - 4);
                    fireLoc = aimLoc;
                    fireVelocity = new Vector(0, 1, 0);
                    break;
                }
                case "槲叶暴风": {
                    if (fireIndex == 2)
                        projectileName = "树叶";
                    break;
                }
                case "酸蚀毒蝰": {
                    if (fireIndex == 2) {
                        projectileName = "毒蝰之牙";
                    }
                    break;
                }
                case "极点光伏":
                case "虚空漩涡": {
                    EntityHelper.AimHelperOptions aimHelperOptions = new EntityHelper.AimHelperOptions()
                            .setAimMode(true)
                            .setTicksTotal(0);
                    if (i == 0)
                        fireLoc = getPlayerTargetLoc(ply, aimHelperOptions, true);
                    else
                        fireLoc = getPlayerCachedTargetLoc(ply, aimHelperOptions);
                    fireVelocity = MathHelper.vectorFromYawPitch_approx(i * 360d / fireAmount, 0);
                    break;
                }
                case "离子冲击波":
                case "凋亡射线": {
                    double maxMana = PlayerHelper.getMaxMana(ply);
                    double manaRatio = ply.getLevel() / maxMana;
                    attrMap.put("damage", attrMap.get("damage") * (0.2 + 1.4 * manaRatio));
                    break;
                }
                case "激光加特林": {
                    if (swingAmount < 25)
                        projectileName = null;
                    break;
                }
                case "遗迹圣物":
                case "原核之怒":
                case "黑洞边缘": {
                    fireVelocity = MathHelper.vectorFromYawPitch_approx(
                            MathHelper.getVectorYaw(facingDir) + 360d * i / fireAmount, 0);
                    break;
                }
                case "沸腾之火": {
                    // fires two darts that are slower and a major projectile unaffected by random offset
                    if (i > 0) {
                        projectileName = "硫火飞弹";
                        projectileSpeed /= 2;
                    }
                    else {
                        fireVelocity = facingDir.clone();
                    }
                    break;
                }
                case "殷红鞭笞": {
                    // fires two darts that are slower and a major projectile unaffected by random offset
                    switch (i) {
                        case 0:
                            projectileName = "大殷红利刃";
                            break;
                        case 1:
                            projectileName = "中殷红利刃";
                            projectileSpeed *= 0.85;
                            break;
                        case 2:
                            projectileName = "小殷红利刃";
                            projectileSpeed *= 0.7;
                            break;
                    }
                    break;
                }
                case "圣神光辉": {
                    // randomly fire the two possible projectiles
                    if (Math.random() < 0.1) {
                        projectileName = "圣神爆破光球";
                    }
                    break;
                }
                case "天穹流星": {
                    fireVelocity = MathHelper.vectorFromYawPitch_approx(Math.random() * 360, -70 - Math.random() * 20);
                    break;
                }
                case "异端": {
                    fireVelocity = MathHelper.vectorFromYawPitch_approx(Math.random() * 360, -80 - Math.random() * 10);
                    // has a chance to not spawn any projectile, which decays as more swings are made
                    double noProjectileChance = 1 - (swingAmount / 25d);
                    if (Math.random() < noProjectileChance)
                        projectileName = null;
                    // randomly fire 1 out of the 3 possible rare projectiles
                    else {
                        switch ((int) (Math.random() * 10)) {
                            case 0:
                                projectileName = "异端镀金灵魂";
                                break;
                            case 1:
                                projectileName = "异端迷失灵魂";
                                break;
                            case 2:
                                projectileName = "异端复仇灵魂";
                                break;
                        }
                    }
                    break;
                }
            }
            if (projectileName != null) {
                // setup projectile velocity
                fireVelocity.multiply(projectileSpeed);
                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                        ply, fireLoc, fireVelocity, attrMap,
                        EntityHelper.getDamageType(ply), projectileName);
                switch (itemType) {
                    case "水晶风暴": {
                        shootInfo.properties.put("gravity", 0d);
                        shootInfo.properties.put("blockHitAction", "bounce");
                        shootInfo.properties.put("bounce", 3);
                        shootInfo.properties.put("liveTime", 60);
                        shootInfo.properties.put("speedMultiPerTick", 0.98);
                        break;
                    }
                    case "蒸海硫火": {
                        shootInfo.properties.put("gravity", 0.05d);
                        shootInfo.properties.put("blockHitAction", "bounce");
                        shootInfo.properties.put("bounce", 3);
                        break;
                    }
                    case "凛冬之怒": {
                        if (fireIndex > 0)
                            shootInfo.properties.put("gravity", 0.05d);
                        break;
                    }
                }
                Projectile firedProjectile = EntityHelper.spawnProjectile(shootInfo);
            }
        }
        // if this is a delayed shot, play item swing sound
        playerUseItemSound(ply, weaponType, itemType, autoSwing);
        // extra delayed shots
        if (fireIndex < fireRoundMax) {
            int fireRoundDelay = weaponSection.getInt("fireRoundsDelay", 20);
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleMagicProjectileFire(ply, attrMap, weaponSection, swingAmount,
                            fireIndex + 1, itemType, weaponType, autoSwing)
                    , fireRoundDelay);
        }
    }
    protected static void handleMagicSpecialFire(Player ply, HashMap<String, Double> attrMap, ConfigurationSection weaponSection,
                                               int fireIndex, String itemType, String weaponType, Location targetedLocation,
                                               boolean autoSwing, int swingAmount, Collection<Entity> damageCD) {
        int fireAmount = weaponSection.getInt("fireRounds", 1),
                fireDelay = weaponSection.getInt("fireRoundsDelay", 1);
        switch (itemType) {
            default:
            {
                Collection<Entity> damageExceptions;
                if (damageCD == null) damageExceptions = new HashSet<>();
                else damageExceptions = damageCD;
                GenericHelper.StrikeLineOptions strikeInfo = new GenericHelper.StrikeLineOptions();
                Vector fireDir = targetedLocation.clone().subtract(ply.getEyeLocation()).toVector().normalize();
                double yaw = MathHelper.getVectorYaw(fireDir),
                        pitch = MathHelper.getVectorPitch(fireDir);
                Location startLoc = ply.getEyeLocation().add(fireDir).add(fireDir);
                double length = 8, width = 0.25;
                String particleColor = "255|255|0";
                // handle strike line properties
                switch (itemType) {
                    case "爆裂藤蔓": {
                        length = 24;
                        particleColor = "103|78|50";
                        width = 0.5;
                        strikeInfo
                                .setDamageCD(4)
                                .setLingerTime(6)
                                .setLingerDelay(5)
                                .setThruWall(true)
                                .setVanillaParticle(false);
                        GenericHelper.handleStrikeLightning(ply, startLoc, yaw, pitch, length, 4,  width, 1, 3, particleColor,
                                damageExceptions, attrMap, strikeInfo);
                        // prevent redundant strike
                        strikeInfo = null;
                        break;
                    }
                    case "高温射线枪": {
                        length = 48;
                        particleColor = "255|225|0";
                        strikeInfo
                                .setThruWall(false)
                                .setMaxTargetHit(1);
                        break;
                    }
                    case "暗影束法杖": {
                        length = 64;
                        particleColor = "255|125|255";
                        strikeInfo
                                .setBounceWhenHitBlock(true);
                        break;
                    }
                    case "影流法杖": {
                        length = 100;
                        particleColor = "255|125|255";
                        strikeInfo
                                .setBounceWhenHitBlock(true)
                                .setMaxTargetHit(6)
                                .setDecayCoef(1.25);
                        break;
                    }
                    case "终极棱镜":
                    case "寂虚之光":
                    case "亚利姆水晶": {
                        // do not use "helper aim" results, as diverged beam can not hit in that way.
                        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
                        yaw = plyNMS.yaw;
                        pitch = plyNMS.pitch;

                        double convergeProgress = swingAmount / 25d;
                        length = 40 + 24 * Math.min(convergeProgress, 1);
                        // particle color
                        String[] particleColors = new String[0];
                        if (convergeProgress < 1) {
                            switch (itemType) {
                                case "终极棱镜":
                                    particleColors = new String[]{"255|0|0", "255|165|0", "255|255|0", "0|128|0",
                                            "0|0|255", "75|0|130", "238|130|238"};
                                    fireAmount = 7;
                                    break;
                                case "寂虚之光":
                                    particleColor = "0|0|0";
                                    width = 1;
                                    break;
                                case "亚利姆水晶":
                                default:
                                    particleColors = new String[]{"232|143|90", "116|50|46"};
                                    fireAmount = 6;
                            }
                            // so that enemies do not get damaged for an unreasonable amount of times
                            damageCD = damageExceptions;
                        }
                        else {
                            double convergenceDamageFactor;
                            switch (itemType) {
                                case "终极棱镜":
                                    particleColor = "255|255|255";
                                    convergenceDamageFactor = 3;
                                    width = 1;
                                    break;
                                case "寂虚之光":
                                    // after completely converges (or, "diverge" in this case)
                                    if (convergeProgress > 2) {
                                        particleColors = new String[]{"255|0|0", "255|165|0", "255|255|0", "0|128|0",
                                                "0|0|255", "75|0|130", "238|130|238"};
                                        convergenceDamageFactor = 3;
                                    }
                                    // first phase of divergence
                                    else {
                                        particleColors = new String[]{"255|255|255"};
                                        convergenceDamageFactor = 2;
                                    }
                                    fireAmount = 7;
                                    convergeProgress = 0;
                                    break;
                                case "亚利姆水晶":
                                default:
                                    particleColor = "255|255|175";
                                    convergenceDamageFactor = 3;
                                    width = 1;
                            }
                            // converged ray deals more damage
                            if (fireIndex == 1)
                                attrMap.put("damage", attrMap.getOrDefault("damage", 10d) * convergenceDamageFactor);
                        }
                        // strike line additional information
                        strikeInfo
                                .setThruWall(false);
                        // tweak shooting direction, if applicable
                        if (fireAmount > 1) {
                            particleColor = particleColors[fireIndex % particleColors.length];
                            double angle = (360 * (double) fireIndex / fireAmount) - (swingAmount * 20);
                            double divergenceMulti = 20 * (1 - Math.min(1, convergeProgress) );
                            if (itemType.equals("寂虚之光") )
                                divergenceMulti /= 4;
                            yaw += MathHelper.xsin_degree(angle) * divergenceMulti;
                            pitch += MathHelper.xcos_degree(angle) * divergenceMulti;
                        }
                        break;
                    }
                    case "永夜射线": {
                        length = 24;
                        particleColor = "0|0|175";
                        strikeInfo
                                .setThruWall(false)
                                .setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                    if (hitEntity instanceof LivingEntity && hitIndex == 1) {
                                        EntityHelper.DamageType damageType = EntityHelper.getDamageType(ply);
                                        for (int i = 0; i < 4; i ++) {
                                            Location explodeLoc = hitLoc.clone()
                                                    .add(Math.random() * 8 - 4, Math.random() * 8 - 4, Math.random() * 8 - 4);
                                            EntityHelper.spawnProjectile(ply, explodeLoc, MathHelper.randomVector(), attrMap,
                                                    damageType, "暗影射线分支");
                                        }
                                    }
                                });
                        break;
                    }
                    case "女武神权杖": {
                        length = 32;
                        particleColor = "255|125|255";
                        strikeInfo
                                .setThruWall(false)
                                .setDamageCD(3)
                                .setLingerTime(5)
                                .setLingerDelay(4);
                        break;
                    }
                    case "泰拉射线": {
                        length = 48;
                        particleColor = "100|255|150";
                        strikeInfo
                                .setDamageCD(3)
                                .setLingerTime(5)
                                .setLingerDelay(4)
                                .setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                    if (hitEntity instanceof LivingEntity && hitIndex == 1) {
                                        EntityHelper.DamageType damageType = EntityHelper.getDamageType(ply);
                                        for (int i = 0; i < 3; i ++) {
                                            Location explodeLoc = hitLoc.clone()
                                                    .add(Math.random() * 10 - 5, Math.random() * 10 - 5, Math.random() * 10 - 5);
                                            EntityHelper.spawnProjectile(ply, explodeLoc, MathHelper.randomVector(), attrMap,
                                                    damageType, "自然能量");
                                        }
                                    }
                                });
                        break;
                    }
                    case "亚特兰蒂斯": {
                        length = 24;
                        width = 1.25;
                        particleColor = "119|145|197";
                        strikeInfo
                                .setDamageCD(4)
                                .setLingerTime(5)
                                .setLingerDelay(5)
                                .setVanillaParticle(false);
                        // particle must not block the vision
                        startLoc.add(fireDir);
                        GenericHelper.handleStrikeLightning(ply, startLoc, yaw, pitch, length,
                                3,  width, 0, 1, particleColor,
                                damageExceptions, attrMap, strikeInfo);
                        // prevent redundant strike
                        strikeInfo = null;
                        break;
                    }
                    case "拉扎尔射线": {
                        length = 48;
                        particleColor = "255|225|0";
                        yaw += Math.random() * 10 - 5;
                        pitch += Math.random() * 10 - 5;
                        strikeInfo
                                .setThruWall(false)
                                .setMaxTargetHit(1)
                                .setDamagedFunction((hitIndex, hitEntity, hitLoc) ->
                                    EntityHelper.handleEntityExplode(ply, 1, new ArrayList<>(), hitLoc));
                        break;
                    }
                    case "元素射线": {
                        length = 56;
                        fireAmount = 4;
                        startLoc.add(fireDir.clone().multiply(2));
                        startLoc.add(Math.random() * 4 - 2, Math.random() * 4 - 2, Math.random() * 4 - 2);
                        fireDir = targetedLocation.clone().subtract(startLoc).toVector();
                        yaw = MathHelper.getVectorYaw(fireDir) + Math.random() * 10 - 5;
                        pitch = MathHelper.getVectorPitch(fireDir) + Math.random() * 10 - 5;
                        switch (fireIndex) {
                            // solar
                            case 1: {
                                particleColor = "255|100|50";
                                strikeInfo
                                        .setDamageCD(10)
                                        .setLingerTime(4)
                                        .setLingerDelay(4)
                                        .setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                            if (hitEntity instanceof LivingEntity && hitIndex == 1) {
                                                EntityHelper.handleEntityExplode(ply, 0.5, damageExceptions, hitLoc);
                                            }
                                            EntityHelper.applyEffect(hitEntity, "元素谐鸣", 40);
                                        });
                                break;
                            }
                            // stardust
                            case 2: {
                                particleColor = "25|200|225";
                                strikeInfo
                                        .setDamageCD(10)
                                        .setLingerTime(4)
                                        .setLingerDelay(4)
                                        .setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                            if (hitEntity instanceof LivingEntity && Math.random() < (0.5d / (hitIndex + 1))) {
                                                EntityHelper.DamageType damageType = EntityHelper.getDamageType(ply);
                                                for (int i = 0; i < 4; i ++) {
                                                    Location explodeLoc = hitLoc.clone()
                                                            .add(Math.random() * 10 - 5, Math.random() * 10 - 5, Math.random() * 10 - 5);
                                                    EntityHelper.spawnProjectile(ply, explodeLoc, MathHelper.randomVector(), attrMap,
                                                            damageType, "星尘流星");
                                                }
                                            }
                                            EntityHelper.applyEffect(hitEntity, "元素谐鸣", 40);
                                        });
                                break;
                            }
                            // vortex
                            case 3: {
                                // this thunder is accurate.
                                yaw = MathHelper.getVectorYaw(fireDir);
                                pitch = MathHelper.getVectorPitch(fireDir);
                                particleColor = "125|255|225";
                                strikeInfo
                                        .setMaxTargetHit(1)
                                        .setLingerDelay(15)
                                        .setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                            EntityHelper.applyEffect(hitEntity, "带电", 100);
                                            EntityHelper.applyEffect(hitEntity, "元素谐鸣", 40);
                                        });
                                GenericHelper.handleStrikeLightning(ply, startLoc, yaw, pitch, length, 8,  width, 2, 2, particleColor,
                                        damageExceptions, attrMap, strikeInfo);
                                // prevent redundant strike
                                strikeInfo = null;
                                break;
                            }
                            // nebula
                            case 4: {
                                fireDelay = 5;
                                width *= 2;
                                particleColor = "225|150|225";
                                strikeInfo
                                        .setDamageCD(10)
                                        .setLingerTime(4)
                                        .setLingerDelay(4)
                                        .setDamagedFunction((hitIndex, hitEntity, hitLoc) ->
                                                EntityHelper.applyEffect(hitEntity, "元素谐鸣", 40));
                                yaw -= (fireIndex - 6) * 2;
                                break;
                            }
                            default:
                                return;
                        }
                        break;
                    }
                    case "耀界之光": {
                        length = 80;
                        particleColor = "RAINBOW";
                        startLoc.add(fireDir.clone().multiply(2));
                        startLoc.add(Math.random() * 4 - 2, Math.random() * 4 - 2, Math.random() * 4 - 2);
                        fireDir = targetedLocation.clone().subtract(startLoc).toVector();
                        yaw = MathHelper.getVectorYaw(fireDir) + Math.random() * 10 - 5;
                        pitch = MathHelper.getVectorPitch(fireDir) + Math.random() * 10 - 5;
                        Consumer<Location> onHitFunction = (Location location) -> {
                            // bouncing ray
                            if (Math.random() < 0.5) {
                                double extraRayYaw = Math.random() * 360, extraRayPitch = 60 + Math.random() * 30;
                                double extraRayLength = 40;
                                Location shootLoc = location.clone().subtract(
                                        MathHelper.vectorFromYawPitch_approx(extraRayYaw, extraRayPitch).multiply(16));
                                GenericHelper.StrikeLineOptions strikeLineOptions = new GenericHelper.StrikeLineOptions()
                                        .setThruWall(false)
                                        .setBounceWhenHitBlock(true)
                                        .setParticleIntensityMulti(0.2)
                                        .setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                            EntityHelper.applyEffect(hitEntity, "超位崩解", 100);
                                        });
                                GenericHelper.handleStrikeLine(ply, shootLoc, extraRayYaw, extraRayPitch, extraRayLength,
                                        0.5, itemType, "RAINBOW",
                                        new HashSet<>(), attrMap, strikeLineOptions);
                            }
                            // 8 homing projectiles
                            else {
                                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                        ply, location, new Vector(), attrMap, EntityHelper.DamageType.MAGIC, "耀界之光能量体");
                                for (int i = 0; i < 8; i ++) {
                                    shootInfo.velocity = MathHelper.randomVector().multiply(2);
                                    EntityHelper.spawnProjectile(shootInfo);
                                }
                            }
                        };
                        strikeInfo
                                .setThruWall(false)
                                .setMaxTargetHit(1)
                                .setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                    EntityHelper.applyEffect(hitEntity, "超位崩解", 100);
                                    onHitFunction.accept(hitLoc);
                                })
                                .setBlockHitFunction((Location hitLoc, MovingObjectPosition mop) -> {
                                    onHitFunction.accept(hitLoc);
                                });
                        break;
                    }
                    case "心泵血杖": {
                        length = 128;
                        particleColor = "109|26|27";
                        strikeInfo
                                .setThruWall(false)
                                .setBounceWhenHitBlock(true)
                                .setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                    if (hitEntity instanceof LivingEntity) {
                                        PlayerHelper.heal(ply, 2);
                                    }
                                })
                                // resets damage CD on block hit
                                .setBlockHitFunction((Location hitLoc, MovingObjectPosition mop) -> {
                                    damageExceptions.clear();
                                });
                        break;
                    }
                    case "净化激光炮": {
                        length = 64;
                        fireAmount = 3;
                        particleColor = "255|255|151";
                        strikeInfo
                                .setMaxTargetHit(2)
                                .setThruWall(false)
                                .setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                    EntityHelper.applyEffect(hitEntity, "神圣之火", 60);
                                });
                        yaw += Math.random() * 2 - 1;
                        pitch += Math.random() * 2 - 1;
                        break;
                    }
                    case "怨戾": {
                        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
                        yaw = plyNMS.yaw;
                        pitch = plyNMS.pitch;
                        length = 64;
                        width = 0.5;
                        particleColor = "248|89|118";
                        strikeInfo
                                .setThruWall(false)
                                .setParticleIntensityMulti(0.5)
                                .setBlockHitFunction((Location hitLoc, MovingObjectPosition hitInfo) -> {
                                    // spawn lava 10 times per second, or if no lava is close to the hit loc
                                    String lavaName = "怨戾熔岩";
                                    boolean spawnLava = swingAmount % 2 == 0;
                                    if (!spawnLava) {
                                        TreeSet<HitEntityInfo> lavaNearby = HitEntityInfo.getEntitiesHit(
                                                hitLoc.getWorld(), hitLoc.toVector(), hitLoc.toVector(),
                                                0.1, (net.minecraft.server.v1_12_R1.Entity e) ->
                                                        e.getBukkitEntity().getName().equals(lavaName));
                                        if (lavaNearby.size() == 0)
                                            spawnLava = true;
                                    }
                                    if (spawnLava) {
                                        EntityHelper.spawnProjectile(ply, hitLoc, new Vector(),
                                                attrMap, EntityHelper.DamageType.MAGIC, lavaName)
                                                .addScoreboardTag("ignoreCanDamageCheck");
                                    }
                                    // has a chance to spawn a hand
                                    {
                                        if (Math.random() < 0.1) {
                                            Location spawnLoc = hitLoc.clone().add(0, 2, 0);
                                            EntityHelper.spawnProjectile(ply, spawnLoc, new Vector(),
                                                    attrMap, EntityHelper.DamageType.MAGIC, "怨戾之手")
                                                    .addScoreboardTag("ignoreCanDamageCheck");
                                        }
                                    }
                                });
                        break;
                    }
                    case "诡触之书":
                    case "命运之手":
                    case "先兆元素": {
                        double randomOffset = 0;
                        switch (itemType) {
                            case "诡触之书":
                                length = 16;
                                particleColor = "243|144|157";
                                randomOffset = 12;
                                fireAmount = 3;
                                fireDelay = 3;
                                strikeInfo
                                        .setMaxTargetHit(1)
                                        .setThruWall(false)
                                        .setParticleIntensityMulti(0.5);
                                break;
                            case "命运之手":
                                length = 24;
                                particleColor = "131|55|183";
                                randomOffset = 10;
                                fireAmount = 4;
                                fireDelay = 2;
                                strikeInfo
                                        .setMaxTargetHit(1)
                                        .setThruWall(false)
                                        .setParticleIntensityMulti(0.35);
                                break;
                            case "先兆元素":
                                length = 32;
                                particleColor = "RAINBOW";
                                randomOffset = 9;
                                fireAmount = 5;
                                fireDelay = 1;
                                strikeInfo
                                        .setMaxTargetHit(1)
                                        .setThruWall(true)
                                        .setParticleIntensityMulti(0.2)
                                        .setDamagedFunction((hitIndex, hitEntity, hitLoc) -> {
                                            EntityHelper.applyEffect(hitEntity, "元素谐鸣", 150);
                                        });
                                break;
                        }
                        yaw += Math.random() * (randomOffset * 2) - randomOffset;
                        pitch += Math.random() * (randomOffset * 2) - randomOffset;
                        break;
                    }
                }
                if (strikeInfo != null)
                    GenericHelper.handleStrikeLine(ply, startLoc, yaw, pitch, length, width, itemType, particleColor,
                            damageExceptions, attrMap, strikeInfo);
                break;
            }
        }
        // extra delayed shots
        if (fireIndex < fireAmount) {
            Collection<Entity> finalDamageCD = damageCD;
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleMagicSpecialFire(ply, attrMap, weaponSection, fireIndex + 1,
                            itemType, weaponType, targetedLocation, autoSwing, swingAmount, finalDamageCD)
                    , fireDelay);
        }
    }
    protected static boolean playerUseMagic(Player ply, String itemType, int swingAmount, ItemStack weaponItem,
                                            String weaponType, boolean autoSwing,
                                            ConfigurationSection weaponSection, HashMap<String, Double> attrMap) {
        int manaConsumption = (int) Math.round(attrMap.getOrDefault("manaUse", 10d) *
                attrMap.getOrDefault("manaUseMulti", 1d));
        // mana consumption tweak
        switch (itemType) {
            case "太空枪":
                if (EntityHelper.getMetadata(ply, EntityHelper.MetadataName.ARMOR_SET).asString().equals("流星套装"))
                    manaConsumption = 0;
                break;
            case "终极棱镜":
                if (swingAmount >= 25)
                    manaConsumption *= 2;
                else if (swingAmount >= 15)
                    manaConsumption *= 1.5;
                break;
        }
        if (!consumeMana(ply, manaConsumption)) return false;
        // post mana consumption
        if (itemType.equals("归元漩涡_RIGHT_CLICK")) {
            int charge = getDurability(weaponItem, 9);
            charge = (charge + 1) % 10;
            setDurability(weaponItem, 9, charge);
            // offset the swing amount to align with the durability display
            swingAmount = charge + 9;
        }
        if (weaponType.equals("MAGIC_PROJECTILE")) {
            handleMagicProjectileFire(ply, attrMap, weaponSection, swingAmount, 1, itemType, weaponType, autoSwing);
        } else {
            Location targetedLocation = getPlayerTargetLoc(ply,
                    new EntityHelper.AimHelperOptions()
                            .setAimMode(true), true);
            handleMagicSpecialFire(ply, attrMap, weaponSection, 1, itemType, weaponType,
                    targetedLocation, autoSwing, swingAmount, null);
        }
        // apply CD
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMagicMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
        // armor set
        String armorSet = PlayerHelper.getArmorSet(ply);
        switch (armorSet) {
            case "龙蒿魔法套装": {
                if (swingAmount % 8 == 7) {
                    // damage setup
                    String projType = "树叶";
                    double projSpd = 3;

                    Vector aimDir = getPlayerAimDir(ply, ply.getEyeLocation(), projSpd, projType, false, 0);
                    HashMap<String, Double> fireballAttrMap = (HashMap<String, Double>) attrMap.clone();
                    double fireballDmg = fireballAttrMap.getOrDefault("damage", 100d) * 0.5;
                    fireballAttrMap.put("damage", fireballDmg);
                    // projectile
                    double centerYaw = MathHelper.getVectorYaw(aimDir);
                    double centerPitch = MathHelper.getVectorPitch(aimDir);
                    for (int i = 0; i < 8; i ++) {
                        Vector projVel = MathHelper.vectorFromYawPitch_approx(
                                centerYaw + Math.random() * 10 - 5, centerPitch + Math.random() * 10 - 5);
                        projVel.multiply(projSpd);
                        EntityHelper.spawnProjectile(ply, projVel,
                                fireballAttrMap, EntityHelper.getDamageType(ply), projType);
                    }
                }
                break;
            }
            case "血炎魔法套装": {
                String coolDownTag = "temp_bloodFlareMagic";
                if (! ply.getScoreboardTags().contains(coolDownTag)) {
                    // cool down
                    EntityHelper.handleEntityTemporaryScoreboardTag(ply, coolDownTag, 25);
                    // damage setup
                    String projType = "血炎魔法射弹";
                    double projSpd = 2;

                    HashMap<String, Double> fireballAttrMap = (HashMap<String, Double>) attrMap.clone();
                    double fireballDmg = Math.min( fireballAttrMap.getOrDefault("damage", 100d) * 1.1, 4000 );
                    fireballAttrMap.put("damage", fireballDmg);
                    // projectile
                    Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), projSpd, projType, false, 0);
                    MathHelper.setVectorLength(projVel, projSpd);
                    EntityHelper.spawnProjectile(ply, projVel,
                            fireballAttrMap, EntityHelper.getDamageType(ply), projType);
                }
                break;
            }
            case "金源魔法套装": {
                // leaf, from tarragon
                if (swingAmount % 8 == 7) {
                    // damage setup
                    String projType = "树叶";
                    double projSpd = 3;

                    Vector aimDir = getPlayerAimDir(ply, ply.getEyeLocation(), projSpd, projType, false, 0);
                    HashMap<String, Double> fireballAttrMap = (HashMap<String, Double>) attrMap.clone();
                    double fireballDmg = fireballAttrMap.getOrDefault("damage", 100d) * 0.5;
                    fireballAttrMap.put("damage", fireballDmg);
                    // projectile
                    double centerYaw = MathHelper.getVectorYaw(aimDir);
                    double centerPitch = MathHelper.getVectorPitch(aimDir);
                    for (int i = 0; i < 8; i ++) {
                        Vector projVel = MathHelper.vectorFromYawPitch_approx(
                                centerYaw + Math.random() * 10 - 5, centerPitch + Math.random() * 10 - 5);
                        projVel.multiply(projSpd);
                        EntityHelper.spawnProjectile(ply, projVel,
                                fireballAttrMap, EntityHelper.getDamageType(ply), projType);
                    }
                }
                // blood orb, from blood flare
                String coolDownTag = "temp_bloodFlareMagic";
                if (! ply.getScoreboardTags().contains(coolDownTag)) {
                    // cool down
                    EntityHelper.handleEntityTemporaryScoreboardTag(ply, coolDownTag, 25);
                    // damage setup
                    String projType = "血炎魔法射弹";
                    double projSpd = 2;

                    HashMap<String, Double> fireballAttrMap = (HashMap<String, Double>) attrMap.clone();
                    double fireballDmg = Math.min( fireballAttrMap.getOrDefault("damage", 100d) * 1.1, 4000 );
                    fireballAttrMap.put("damage", fireballDmg);
                    // projectile
                    Vector projVel = getPlayerAimDir(ply, ply.getEyeLocation(), projSpd, projType, false, 0);
                    MathHelper.setVectorLength(projVel, projSpd);
                    EntityHelper.spawnProjectile(ply, projVel,
                            fireballAttrMap, EntityHelper.getDamageType(ply), projType);
                }
                break;
            }
        }
        return true;
    }
    // summoning helper functions below
    public static boolean spawnSentryMinion(Player ply, String type, HashMap<String, Double> attrMap, int slotsConsumed,
                                            boolean sentryOrMinion, boolean hasContactDamage, boolean noDuplication,
                                            ItemStack originalStaff) {
        ArrayList<Entity> minionList;
        EntityHelper.MetadataName indexNextMetadataKey;
        int minionLimit, indexNext;
        // initialize minion limit and minion list
        {
            if (sentryOrMinion) {
                minionList = (ArrayList<Entity>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_SENTRY_LIST).value();
                minionLimit = attrMap.getOrDefault("sentryLimit", 1d).intValue();
                indexNextMetadataKey = EntityHelper.MetadataName.PLAYER_NEXT_SENTRY_INDEX;
            } else {
                minionList = (ArrayList<Entity>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_MINION_LIST).value();
                minionLimit = attrMap.getOrDefault("minionLimit", 1d).intValue();
                indexNextMetadataKey = EntityHelper.MetadataName.PLAYER_NEXT_MINION_INDEX;
            }
            indexNext = EntityHelper.getMetadata(ply, indexNextMetadataKey).asInt();
            // prevent bug brought by shrink in minion limit
            if (indexNext >= minionLimit)
                indexNext = 0;
        }
        // validate if the minion can be summoned
        {
            // for minions with no duplication allowed, abort the summoning attempt if a minion exists already.
            if (noDuplication) {
                for (Entity toCheck : minionList) {
                    if (GenericHelper.trimText(toCheck.getName()).equals(type) && !toCheck.isDead()) {
                        toCheck.addScoreboardTag("reSummoned");
                        return false;
                    }
                }
            }
            // check if the minion limit satisfies the requirement
            if (minionLimit < slotsConsumed) return false;
        }
        // summon the minion
        // minionSlot is used to check if a new minion has been spawned to replace the original.
        // minionSlotMax is used to check if the max minion slot of the player shrinks.
        int minionSlot = indexNext, minionSlotMax = Math.min(indexNext + slotsConsumed, minionLimit) - 1;
        Entity minionEntity;
        switch (type) {
            case "颠茄之灵":
            case "小骷髅":
            case "蚀骨之龙":
            case "缠怨鬼碟":
            case "代达罗斯守卫":
            case "矮人":
            case "葱茏之锋":
                MinionHusk huskMinion = new MinionHusk(ply, minionSlot, minionSlotMax, sentryOrMinion, hasContactDamage, type, attrMap, originalStaff);
                minionEntity = huskMinion.getBukkitEntity();
                break;
            case "蜘蛛":
            case "寄居蟹":
            case "灵魂吞噬者宝宝":
                MinionCaveSpider caveSpiderMinion = new MinionCaveSpider(ply, minionSlot, minionSlotMax, sentryOrMinion, hasContactDamage, type, attrMap, originalStaff);
                minionEntity = caveSpiderMinion.getBukkitEntity();
                break;
            default:
                MinionSlime slimeMinion = new MinionSlime(ply, minionSlot, minionSlotMax, sentryOrMinion, hasContactDamage, type, attrMap, originalStaff);
                minionEntity = slimeMinion.getBukkitEntity();
        }
        // set the slots
        for (int i = 0; i < slotsConsumed; i ++) {
            if (indexNext < minionList.size())
                minionList.set(indexNext, minionEntity);
            else
                minionList.add(minionEntity);
            indexNext = (indexNext + 1) % minionLimit;
        }
        EntityHelper.setMetadata(ply, indexNextMetadataKey, indexNext);
        return true;
    }
    protected static boolean playerUseSummon(Player ply, String itemType, int swingAmount, String weaponType,
                                           boolean autoSwing, ItemStack originalStaff,
                                           ConfigurationSection weaponSection, HashMap<String, Double> attrMap) {
        int manaConsumption = (int) Math.round(attrMap.getOrDefault("manaUse", 10d) *
                attrMap.getOrDefault("manaUseMulti", 1d));
        if (!consumeMana(ply, manaConsumption)) return false;
        boolean sentryOrMinion = weaponType.equals("SENTRY");
        boolean hasContactDamage = weaponSection.getString(
                "damageType", EntityHelper.DamageType.MELEE.internalName).equals(EntityHelper.DamageType.MELEE.internalName);
        boolean noDuplication = weaponSection.getBoolean("noDuplication", false);
        int slotsConsumed = weaponSection.getInt("slotsRequired", 1);
        String minionName = weaponSection.getString("minionName");
        if (minionName == null) return false;
        // apply CD, even if the summon attempt fails.
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMagicMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
        // if the summoning attempt failed, for example, minion is already present
        return spawnSentryMinion(ply, minionName, attrMap, slotsConsumed, sentryOrMinion, hasContactDamage, noDuplication, originalStaff);
    }
    // other helper functions for item using
    public static void playerUseItemSound(Entity ply, String weaponCategory, String weaponItemType, boolean autoSwing) {
        String itemUseSound;
        float volume = 3f, pitch = 1f;
        switch (weaponCategory) {
            case "BOW":
                itemUseSound = SOUND_BOW_SHOOT;
                break;
            case "GUN":
                if (autoSwing) {
                    itemUseSound = SOUND_GUN_FIRE;
                } else {
                    itemUseSound = SOUND_GUN_FIRE_LOUD;
                    pitch = 1.2f;
                }
                volume = 5f;
                break;
            default:
                itemUseSound = SOUND_GENERIC_SWING;
        }
        itemUseSound = TerrariaHelper.weaponConfig.getString(weaponItemType + ".useSound", itemUseSound);
        ply.getWorld().playSound(ply.getLocation(), itemUseSound, SoundCategory.PLAYERS, volume, pitch);
    }
    public static boolean playerBiomeBladeResonate(Player ply, String itemType, String prefix) {
        if (ply.isSneaking())
           switch (itemType) {
            case "残缺环境刃[澄澈之纯净]":
            case "残缺环境刃[拥怀之凛冽]":
            case "残缺环境刃[伟岸之枯涸]":
            case "残缺环境刃[反抗之衰朽]":
            case "残缺环境刃[林妖之轻抚]": {
                switch (WorldHelper.BiomeType.getBiome(ply.getLocation(), false)) {
                    case TUNDRA:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 残缺环境刃[拥怀之凛冽]", false));
                        break;
                    case DESERT:
                    case UNDERWORLD:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 残缺环境刃[伟岸之枯涸]", false));
                        break;
                    case CORRUPTION:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 残缺环境刃[反抗之衰朽]", false));
                        break;
                    case JUNGLE:
                    case OCEAN:
                    case SULPHUROUS_OCEAN:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 残缺环境刃[林妖之轻抚]", false));
                        break;
                    case NORMAL:
                    default:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 残缺环境刃[澄澈之纯净]", false));
                }
                applyCD(ply, 20);
                return true;
            }
            case "环境之刃[澄澈之纯净]":
            case "环境之刃[拥怀之凛冽]":
            case "环境之刃[伟岸之枯涸]":
            case "环境之刃[反抗之衰朽]":
            case "环境之刃[林妖之轻抚]":
            case "环境之刃[天国之神威]":
            case "环境之刃[嫌恶之永存]":
            case "环境之刃[溺死之亡姿]": {
                switch (WorldHelper.BiomeType.getBiome(ply.getLocation(), false)) {
                    case TUNDRA:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 环境之刃[拥怀之凛冽]", false));
                        break;
                    case DESERT:
                    case UNDERWORLD:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 环境之刃[伟岸之枯涸]", false));
                        break;
                    case CORRUPTION:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 环境之刃[反抗之衰朽]", false));
                        break;
                    case JUNGLE:
                    case OCEAN:
                    case SULPHUROUS_OCEAN:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 环境之刃[林妖之轻抚]", false));
                        break;
                    case HALLOW:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 环境之刃[天国之神威]", false));
                        break;
                    case ASTRAL_INFECTION:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 环境之刃[嫌恶之永存]", false));
                        break;
                    case SUNKEN_SEA:
                    case ABYSS:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 环境之刃[溺死之亡姿]", false));
                        break;
                    case NORMAL:
                    default:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 环境之刃[澄澈之纯净]", false));
                }
                applyCD(ply, 20);
                return true;
            }
            case "真·环境之刃[兵匠之傲]":
            case "真·环境之刃[血腾之愤]":
            case "真·环境之刃[缚囚之悼]":
            case "真·环境之刃[变幻之潮]": {
                switch (WorldHelper.BiomeType.getBiome(ply.getLocation(), false)) {
                    case DESERT:
                    case UNDERWORLD:
                    case CORRUPTION:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 真·环境之刃[血腾之愤]", false));
                        break;
                    case TUNDRA:
                    case JUNGLE:
                    case OCEAN:
                    case SULPHUROUS_OCEAN:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 真·环境之刃[缚囚之悼]", false));
                        break;
                    case ASTRAL_INFECTION:
                    case SUNKEN_SEA:
                    case ABYSS:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 真·环境之刃[变幻之潮]", false));
                        break;
                    case NORMAL:
                    default:
                        ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                                prefix + "的 真·环境之刃[兵匠之傲]", false));
                }
                applyCD(ply, 20);
                return true;
            }
            case "银河[凤凰之耀]": {
                ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                            prefix + "的 银河[白羊之怒]", false));
                applyCD(ply, 20);
                return true;
            }
            case "银河[白羊之怒]": {
                ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                            prefix + "的 银河[北斗之眸]", false));
                applyCD(ply, 20);
                return true;
            }
            case "银河[北斗之眸]": {
                ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                            prefix + "的 银河[仙女之跃]", false));
                applyCD(ply, 20);
                return true;
            }
            case "银河[仙女之跃]": {
                ply.getInventory().setItemInMainHand(ItemHelper.getItemFromDescription(
                            prefix + "的 银河[凤凰之耀]", false));
                applyCD(ply, 20);
                return true;
            }
            default:
                return false;
        }
        return false;
    }
    // note that the "durability" stored in items are actually damage value.
    public static int getDurability(ItemStack weaponItem, int maxDurability) {
        short maxVanillaDurability = weaponItem.getType().getMaxDurability();
        if (maxVanillaDurability <= 1)
            return maxDurability;
        int currVanillaDurability = maxVanillaDurability - weaponItem.getDurability();
        if (currVanillaDurability == maxVanillaDurability) return 0;
        double durabilityRatio = (double) currVanillaDurability / maxVanillaDurability;
        return (int) Math.round(maxDurability * durabilityRatio);
    }
    public static void setDurability(ItemStack weaponItem, int maxDurability, int currentDurability) {
        // tweak current durability to prevent out of bound bugs
        if (currentDurability < 0)
            currentDurability = 0;
        else if (currentDurability > maxDurability)
            currentDurability = maxDurability;
        // prevent bug from items with no durability
        short maxVanillaDurability = weaponItem.getType().getMaxDurability();
        if (maxVanillaDurability <= 1)
            return;
        // default values
        if (currentDurability == 0)
            weaponItem.setDurability((short) 0);
        else if (currentDurability == maxDurability)
            weaponItem.setDurability((short) 1);
        // calculate durability
        else {
            double durabilityRatio = (double) currentDurability / maxDurability;
            // prevent breaking the item
            int currVanillaDurability = (int) Math.max(maxVanillaDurability * durabilityRatio, 1);
            weaponItem.setDurability((short) (maxVanillaDurability - currVanillaDurability));
        }
    }
    // note that use time CD handling are in individual helper functions.
    // also, attrMap has been already cloned in this function prior to calling individual helper function.
    public static void playerUseItem(Player ply) {
        // cursed players can not use any item
        if (EntityHelper.hasEffect(ply, "诅咒")) {
            ply.removeScoreboardTag("temp_autoSwing");
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_ITEM_SWING_AMOUNT, 0);
            return;
        }
        // if the player is not logged in or is dead
        if (!PlayerHelper.isProperlyPlaying(ply)) return;
        // if tool changed, reload attributes
        Set<String> scoreboardTags = ply.getScoreboardTags();
        if (scoreboardTags.contains("toolChanged"))
            PlayerHelper.setupAttribute(ply);
        // variable setup
        HashMap<String, Double> attrMap = (HashMap<String, Double>) EntityHelper.getAttrMap(ply).clone();
        boolean isRightClick = scoreboardTags.contains("isSecondaryAttack");
        // other items that require item type info
        ItemStack mainHandItem = ply.getInventory().getItemInMainHand();
        String[] splitItemName = ItemHelper.splitItemName(mainHandItem);
        String itemPrefix = splitItemName[0];
        String itemName = splitItemName[1];
        // fishing rod
        if (!isRightClick && attrMap.getOrDefault("fishingPower", -1d) > 0) {
            playerSwingFishingRod(ply, attrMap, itemName);
            return;
        }
        // if itemName == "", some bug may occur. Also, vanilla items are not useful at all.
        if (itemName.length() > 0) {
            if (isRightClick) {
                // to summon an invasion event/platform event
                if (playerUseEventSummon(ply, itemName, mainHandItem)) return;
                // to release a critter
                if (playerUseCritter(ply, itemName, mainHandItem)) return;
                // void bag, piggy bank, musical instruments etc.
                if (playerUseMiscellaneous(ply, itemName)) return;
                // potion and other consumable consumption
                if (playerUsePotion(ply, itemName, mainHandItem, QuickBuffType.NONE)) return;
                // biome blade resonate
                if (playerBiomeBladeResonate(ply, itemName, itemPrefix)) return;
            }
            // weapon
            itemName += (isRightClick ? "_RIGHT_CLICK" : "");
            ConfigurationSection weaponSection = TerrariaHelper.weaponConfig.getConfigurationSection(itemName);
            if (weaponSection != null) {
                // handle loading
                boolean autoSwing = weaponSection.getBoolean("autoSwing", false);
                boolean isLoading = false;
                int maxLoad = weaponSection.getInt("maxLoad", 0);
                int swingAmount = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_ITEM_SWING_AMOUNT).asInt();
                if (maxLoad > 0) {
                    swingAmount = Math.min(swingAmount, maxLoad);
                    // this is not first loading attempt
                    if (scoreboardTags.contains("temp_autoSwing")) {
                        // still loading
                        if (scoreboardTags.contains("temp_isLoadingWeapon")) {
                            isLoading = true;
                        }
                        // finished loading
                        else {
                            handleFinishLoading(ply, mainHandItem);
                            autoSwing = false;
                        }
                    }
                    // start loading, as the auto swing scoreboard tag is added later.
                    else {
                        ply.addScoreboardTag("temp_isLoadingWeapon");
                        isLoading = true;
                    }
                }
                if (isLoading) {
                    swingAmount = Math.min(swingAmount + 1, maxLoad);
                    ply.addScoreboardTag("temp_autoSwing");
                    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_ITEM_SWING_AMOUNT, swingAmount);
                    displayLoadingProgress(ply, mainHandItem, swingAmount, maxLoad);
                    double loadSpeedMulti = weaponSection.getDouble("loadTimeMulti", 0.5d);
                    applyCD(ply, attrMap.getOrDefault("useTime", 10d)
                            * attrMap.getOrDefault("useTimeMulti", 1d) * loadSpeedMulti);
                    return;
                }
                // update attribute
                if (weaponSection.contains("attributes")) {
                    ConfigurationSection attributeSection = weaponSection.getConfigurationSection("attributes");
                    for (String attributeType : attributeSection.getKeys(false))
                        attrMap.put(attributeType, attributeSection.getDouble(attributeType, 1d));
                }
                // use weapon
                String weaponType = weaponSection.getString("type", "");
                // prevent accidental glitch that creates endless item use cool down
                if (attrMap.getOrDefault("useCD", 0d) < 0.01) PlayerHelper.setupAttribute(ply);
                boolean success = false;
                switch (weaponType) {
                    case "STAB":
                    case "SWING":
                        success = playerUseMelee(ply, itemName, mainHandItem, weaponSection, attrMap, swingAmount, weaponType.equals("STAB"));
                        break;
                    case "WHIP":
                        success = playerUseWhip(ply, itemName, mainHandItem, weaponSection, attrMap, swingAmount);
                        break;
                    case "BOW":
                    case "GUN":
                    case "ROCKET":
                    case "SPECIAL_AMMO":
                        success = playerUseRanged(ply, itemName, swingAmount, mainHandItem,
                                weaponType, maxLoad > 0, autoSwing, weaponSection, attrMap);
                        break;
                    case "MAGIC_PROJECTILE":
                    case "MAGIC_SPECIAL":
                        success = playerUseMagic(ply, itemName, swingAmount, mainHandItem, weaponType,
                                autoSwing, weaponSection, attrMap);
                        break;
                    case "SUMMON":
                    case "SENTRY":
                        success = playerUseSummon(ply, itemName, swingAmount, weaponType,
                                autoSwing, mainHandItem, weaponSection, attrMap);
                        break;
                    case "THROW_PROJECTILE":
                        success = playerUseThrowingProjectile(ply, itemName, weaponType,
                                autoSwing, attrMap, mainHandItem);
                        break;
                    case "BOOMERANG":
                        success = playerUseBoomerang(ply, itemName, weaponType,
                                autoSwing, attrMap, weaponSection);
                        break;
                    case "YOYO":
                        success = playerUseYoyo(ply, itemName, weaponType,
                                autoSwing, attrMap, weaponSection);
                        break;
                    case "FLAIL":
                        success = playerUseFlail(ply, itemName, weaponType,
                                autoSwing, attrMap, weaponSection);
                        break;
                }
                if (success) {
                    if (autoSwing) {
                        ply.addScoreboardTag("temp_autoSwing");
                        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_ITEM_SWING_AMOUNT, swingAmount + 1);
                    }
                    // play item use sound
                    playerUseItemSound(ply, weaponType, itemName, autoSwing);
                } else {
                    // prevent bug, if the item is not being used successfully, cancel auto swing
                    // this mainly happens when mana has depleted or ammo runs out
                    ply.removeScoreboardTag("temp_autoSwing");
                }
            }
            // pickaxe
            if (attrMap.getOrDefault("powerPickaxe", 0d) > 1) {
                playerSwingPickaxe(ply, attrMap, isRightClick);
            }
        }
    }
    // this function is called when the player right-clicks an item in inventory.
    public static boolean playerOpenCrate(Player ply, ItemStack item) {
        ConfigurationSection config = TerrariaHelper.crateConfig;
        String itemType = ItemHelper.splitItemName(item)[1];
        if (itemType.equals("专家模式福袋")) {
            config = config.getConfigurationSection(itemType);
            itemType = GenericHelper.trimText(item.getItemMeta().getLore().get(0));
        }
        // if the item is not set, to prevent bug.
        if (itemType.equals("")) return false;
        config = config.getConfigurationSection(itemType);
        if (config != null) {
            item.setAmount(item.getAmount() - 1);
            Collection<String> nodes = config.getKeys(false);
            // give items in each section to the player
            for (String dropSectionIndex : nodes) {
                ConfigurationSection dropSection = config.getConfigurationSection(dropSectionIndex);
                String progressRequired = dropSection.getString("progressRequired", "");
                if (!PlayerHelper.hasDefeated(ply, progressRequired))
                    continue;
                List<String> items = dropSection.getStringList("items");
                boolean shouldGiveAllItems = dropSection.getBoolean("giveAllItems", true);
                if (shouldGiveAllItems) {
                    for (String itemDescToGive : items) {
                        PlayerHelper.giveItem(ply, ItemHelper.getItemFromDescription(itemDescToGive), true);
                    }
                } else {
                    int amountToGive = dropSection.getInt("amountToGive", 1);
                    for (int i = 0; i < amountToGive; i++) {
                        if (items.isEmpty()) break;
                        String itemDescToGive = items.remove((int) (Math.random() * items.size()));
                        PlayerHelper.giveItem(ply, ItemHelper.getItemFromDescription(itemDescToGive), true);
                    }
                }
            }
            return true;
        }
        return false;
    }
}
