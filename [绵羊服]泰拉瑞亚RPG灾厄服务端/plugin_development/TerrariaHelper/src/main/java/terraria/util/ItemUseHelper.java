package terraria.util;

import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import net.minecraft.server.v1_12_R1.PacketPlayOutSetCooldown;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.*;
import terraria.entity.others.TerrariaFishingHook;
import terraria.entity.minion.MinionCaveSpider;
import terraria.entity.minion.MinionHusk;
import terraria.entity.minion.MinionSlime;
import terraria.gameplay.EventAndTime;

import java.util.*;
import java.util.function.Predicate;

public class ItemUseHelper {
    public enum QuickBuffType {
        NONE, HEALTH, MANA, BUFF;
    }
    public static final String SOUND_GENERIC_SWING = "item.genericSwing", SOUND_BOW_SHOOT = "item.bowShoot",
            SOUND_GUN_FIRE = "item.gunfire", SOUND_GUN_FIRE_LOUD = "entity.generic.explode";
    protected static final double MELEE_STRIKE_RADIUS = 0.25;
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
        ply.playSound(ply.getEyeLocation(), "item.genericSwing", 1, 1);
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
        Vector facingDir = MathHelper.vectorFromYawPitch_quick(plyNMS.yaw, plyNMS.pitch);
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
        switch (itemName) {
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
        }
        if (successful) {
            itemStack.setAmount( itemStack.getAmount() - 1 );
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
        // potion can not be consumed when cursed.
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
                                successful = PlayerHelper.hasDefeated(ply, BossHelper.BossType.WALL_OF_FLESH.msgName) &&
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
                                            accessories.contains("神话护身符"))
                                        duration *= 0.75;
                                    EntityHelper.applyEffect(ply, "耐药性", duration);
                                    break;
                                }
                                // if the potion recovers mana
                                case "mana": {
                                    PlayerHelper.restoreMana(ply, potionPotency);
                                    EntityHelper.applyEffect(ply, "魔力疾病", 200);
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
            ply.getWorld().playSound(ply.getEyeLocation(), sound, 1, 1);
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
    protected static void displayLoadingProgress(Player ply, int currLoad, int maxLoad) {
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
    public static Location getPlayerTargetLoc(Player ply, double traceDist, double entityEnlargeRadius, EntityHelper.AimHelperOptions aimHelperInfo, boolean strictMode) {
        Location targetLoc = null;
        World plyWorld = ply.getWorld();
        EntityPlayer nmsPly = ((CraftPlayer) ply).getHandle();
        Vector lookDir = MathHelper.vectorFromYawPitch_quick(nmsPly.yaw, nmsPly.pitch);
        {
            Vector eyeLoc = ply.getEyeLocation().toVector();
            Vector endLoc = eyeLoc.clone().add(lookDir.clone().multiply(traceDist));
            // the block the player is looking ticksBeforeHookingFish, if near enough
            {
                MovingObjectPosition rayTraceResult = HitEntityInfo.rayTraceBlocks(
                        plyWorld,
                        eyeLoc.clone(),
                        endLoc);
                if (rayTraceResult != null) {
                    endLoc = MathHelper.toBukkitVector(rayTraceResult.pos);
                    targetLoc = endLoc.toLocation(plyWorld);
                }
            }
            // the enemy the player is looking ticksBeforeHookingFish, if applicable
            if (eyeLoc.distanceSquared(endLoc) > entityEnlargeRadius * entityEnlargeRadius * 4) {
                Set<HitEntityInfo> hits = HitEntityInfo.getEntitiesHit(
                        plyWorld,
                        eyeLoc.clone().add(lookDir.clone().multiply(entityEnlargeRadius)),
                        endLoc.clone().add(lookDir.clone().multiply(entityEnlargeRadius)),
                        entityEnlargeRadius,
                        (net.minecraft.server.v1_12_R1.Entity target) -> EntityHelper.checkCanDamage(ply, target.getBukkitEntity(), strictMode));
                if (hits.size() > 0) {
                    HitEntityInfo hitInfo = hits.iterator().next();
                    Entity hitEntity = hitInfo.getHitEntity().getBukkitEntity();
                    targetLoc = EntityHelper.helperAimEntity(ply, hitEntity, aimHelperInfo);
                }
            }
            // if the target location is still null, that is, no block/entity being hit
            if (targetLoc == null) {
                targetLoc = ply.getEyeLocation().add(lookDir.clone().multiply(traceDist));
            }
        }
        return targetLoc;
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
        Vector lookDir = MathHelper.vectorFromYawPitch_quick(nmsPly.yaw, nmsPly.pitch);
        Location targetLoc = getPlayerTargetLoc(ply, 96, 5,
                new EntityHelper.AimHelperOptions().setTicksOffset(8).setAimMode(true), true);
        Location centerLoc = targetLoc.clone().add(ply.getEyeLocation()).multiply(0.5);
        Vector reachVec = centerLoc.clone().subtract(ply.getEyeLocation()).toVector();
        Vector offsetVec = null;
        while (offsetVec == null || offsetVec.lengthSquared() < 1e-5) {
            offsetVec = MathHelper.randomVector();
            offsetVec.subtract(MathHelper.vectorProjection(reachVec, offsetVec));
        }
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
    // warning: this function modifies attrMap and damaged!
    protected static void handleMeleeSwing(Player ply, HashMap<String, Double> attrMap, Vector lookDir,
                                         Collection<Entity> damaged, ConfigurationSection weaponSection,
                                         double yawMin, double yawMax, double pitchMin, double pitchMax,
                                           String weaponType, ItemStack weaponItem, double size,
                                         boolean dirFixed, int interpolateType, int currentIndex, int maxIndex) {
        if (!PlayerHelper.isProperlyPlaying(ply)) return;
        if (!dirFixed) {
            EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
            // stab
            if (interpolateType == 0) {
                yawMin = plyNMS.yaw;
                yawMax = plyNMS.yaw;
                pitchMin = plyNMS.pitch;
                pitchMax = plyNMS.pitch;
                lookDir = MathHelper.vectorFromYawPitch_quick(yawMin, pitchMin);
            }
            // swing
            else {
                yawMin = plyNMS.yaw;
                yawMax = plyNMS.yaw;
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
                if (EntityHelper.getDamageType(ply) == EntityHelper.DamageType.MELEE)
                    strikeLineInfo.setParticleInfo(new GenericHelper.ParticleLineOptions()
                            .setParticleOrItem(false)
                            .setSpriteItem(weaponItem)
                            .setTicksLinger(1));
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
        // "stab"
        if (interpolateType == 0) {
            boolean shouldStrike;
            double strikeYaw = yawMin, strikePitch = pitchMin;
            double particleInterval = size;
            switch (weaponType) {
                case "星光": {
                    shouldStrike = currentIndex % 2 == 0;
                    if (shouldStrike) {
                        // prevent DPS loss due to damage invincibility frame
                        damaged = new ArrayList<>();
                        color = particleColors.get((int) (Math.random() * particleColors.size()));
                        if (currentIndex > 0) ply.getWorld().playSound(ply.getLocation(), SOUND_GENERIC_SWING, 1f, 1f);
                        strikeYaw += Math.random() * 30 - 15;
                        strikePitch += Math.random() * 30 - 15;
                    }
                    break;
                }
                case "钨钢螺丝刀_RIGHT_CLICK": {
                    shouldStrike = currentIndex == 0;
                    double finalStrikeYaw = strikeYaw;
                    double finalStrikePitch = strikePitch;
                    strikeLineInfo
                            .setDamagedFunction((hitIndex, entityHit, hitLoc) -> {
                                EntityHelper.getAttrMap(entityHit).put("damageMeleeMulti", 12.5);
                                Vector projVel = MathHelper.vectorFromYawPitch_quick(finalStrikeYaw, finalStrikePitch);
                                projVel.multiply(3);
                                entityHit.setVelocity(projVel);
                                hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1);
                            })
                            .setShouldDamageFunction((entity) -> entity.getScoreboardTags().contains("isWulfrumScrew"))
                            .setLingerDelay(5);
                    break;
                }
                default:
                    shouldStrike = currentIndex <= 5;
                    if (shouldStrike && particleColors.size() > 0) {
                        color = particleColors.get(0);
                    }
            }
            if (shouldStrike)
                GenericHelper.handleStrikeLine(ply, ply.getEyeLocation().add(lookDir), strikeYaw, strikePitch,
                        size, particleInterval, MELEE_STRIKE_RADIUS,
                        weaponType, color, damaged, attrMap, strikeLineInfo);
        }
        // "swing" or "whip"
        else {
            if (weaponType.equals("天顶剑")) {
                if (currentIndex % 4 == 0) {
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
                    case "金捕虫网":
                        strikeLineInfo
                                .setShouldDamageFunction( (e) ->
                                        e.getScoreboardTags().contains("isAnimal") && !(e.isDead()) )
                                .setDamagedFunction( (hitIdx, hitEntity, hitLoc) -> {
                                    ItemHelper.dropItem(hitLoc, hitEntity.getName());
                                    hitEntity.remove();
                                });
                        break;
                }
                for (int i = indexStart; i < indexEnd; i ++) {
                    double progress = (double) i / loopTimes;
                    double actualYaw = yawMin + (yawMax - yawMin) * progress;
                    double actualPitch = pitchMin + (pitchMax - pitchMin) * progress;
                    Vector offsetDir = MathHelper.vectorFromYawPitch_quick(actualYaw, actualPitch);
                    // swords have a constant reach while whips have a changing attack reach over time
                    double strikeLength = interpolateType == 1 ?
                            size : size * MathHelper.xcos_degree( 300 * (progress - 0.5) );
                    GenericHelper.handleStrikeLine(ply, ply.getEyeLocation().add(offsetDir), actualYaw, actualPitch,
                            strikeLength, MELEE_STRIKE_RADIUS, weaponType, color, damaged, attrMap, strikeLineInfo);
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
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleMeleeSwing(ply, attrMap, finalLookDir, finalDamaged, weaponSection,
                            finalYawMin, finalYawMax, finalPitchMin, finalPitchMax,
                            weaponType, weaponItem, size, dirFixed, interpolateType, currentIndex + 1, maxIndex), 1);
        }
    }
    // melee helper functions below
    // stab and swing. NOT NECESSARILY MELEE DAMAGE!
    protected static boolean playerUseMelee(Player ply, String itemType, ItemStack weaponItem,
                                          ConfigurationSection weaponSection, HashMap<String, Double> attrMap, int swingAmount, boolean stabOrSwing) {
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        double yaw = plyNMS.yaw, pitch = plyNMS.pitch;
        double size = weaponSection.getDouble("size", 3d);
        Vector lookDir = MathHelper.vectorFromYawPitch_quick(yaw, pitch);
        size *= attrMap.getOrDefault("meleeReachMulti", 1d);
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
                lookDir.multiply(projectileInfo.getDouble("velocity", 1d));
                String projectileType = projectileInfo.getString("name", "");
                for (int i = 0; i < shootAmount; i ++) {
                    Projectile spawnedProjectile;
                    if (stabOrSwing) {
                        Vector v = MathHelper.vectorFromYawPitch_quick(yaw, pitch);
                        v.multiply(size);
                        spawnedProjectile = EntityHelper.spawnProjectile(ply, ply.getEyeLocation().add(v),
                                lookDir, attrMapProjectile, EntityHelper.DamageType.MELEE, projectileType);
                    } else {
                        spawnedProjectile = EntityHelper.spawnProjectile(ply, ply.getEyeLocation(),
                                lookDir, attrMapProjectile, EntityHelper.DamageType.MELEE, projectileType);
                    }
                    if (itemType.equals("钨钢螺丝刀")) {
                        spawnedProjectile.setVelocity(new Vector(0, 0.3, 0));
                        spawnedProjectile.addScoreboardTag("isWulfrumScrew");
                    }
                }
            }
        }
        int coolDown = applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
        boolean dirFixed = weaponSection.getBoolean("dirFixed", true);
        if (stabOrSwing)
            handleMeleeSwing(ply, attrMap, lookDir, new HashSet<>(), weaponSection,
                    yaw, yaw, pitch, pitch, itemType, weaponItem, size,
                    dirFixed, 0, 0, coolDown);
        else
            handleMeleeSwing(ply, attrMap, lookDir, new HashSet<>(), weaponSection,
                    yaw, yaw, -110, 60, itemType, weaponItem, size,
                    dirFixed, 1, 0, coolDown);
        return true;
    }
    protected static boolean playerUseWhip(Player ply, String itemType, ItemStack weaponItem,
                                          ConfigurationSection weaponSection, HashMap<String, Double> attrMap, int swingAmount) {
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        double yaw = plyNMS.yaw, pitch = plyNMS.pitch;
        double size = weaponSection.getDouble("size", 3d);
        Vector lookDir = MathHelper.vectorFromYawPitch_quick(yaw, pitch);
        size *= attrMap.getOrDefault("meleeReachMulti", 1d);
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMeleeMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        int coolDown = applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
        // get swing offset
        double yawOffset, pitchOffset;
        switch (itemType) {
            case "日耀喷发剑":
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
                true, 2, 0, coolDown);
        return true;
    }
    protected static boolean playerUseBoomerang(Player ply, String itemType, String weaponType,
                                                boolean autoSwing, HashMap<String, Double> attrMap, ConfigurationSection weaponSection) {
        // use the weapon
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        Vector facingDir = MathHelper.vectorFromYawPitch_quick(plyNMS.yaw, plyNMS.pitch);
        double projectileSpeed = weaponSection.getDouble("velocity", 5d);
        double distance = weaponSection.getDouble("distance", 10d);
        facingDir.multiply(projectileSpeed);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(ply, facingDir, attrMap, itemType);
        // spawn projectile
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMeleeMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        double useTime = attrMap.getOrDefault("useTime", 20d) * useTimeMulti;
        TerrariaBoomerang entity = new TerrariaBoomerang(shootInfo, distance, useTime);
        plyNMS.getWorld().addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // play sound
        playerUseItemSound(ply, weaponType, autoSwing);
        return true;
    }
    protected static boolean playerUseYoyo(Player ply, String itemType, String weaponType,
                                           boolean autoSwing, HashMap<String, Double> attrMap, ConfigurationSection weaponSection) {
        // use the weapon
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        Vector facingDir = MathHelper.vectorFromYawPitch_quick(plyNMS.yaw, plyNMS.pitch);
        double projectileSpeed = weaponSection.getDouble("velocity", 0.5d);
        double reach = weaponSection.getDouble("reach", 10d);
        int duration = weaponSection.getInt("duration", 100);
        facingDir.multiply(projectileSpeed);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(ply, facingDir, attrMap, itemType);
        // spawn projectile
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMeleeMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        double useTime = attrMap.getOrDefault("useTime", 20d) * useTimeMulti;
        TerrariaYoyo entity = new TerrariaYoyo(shootInfo, reach, useTime, duration);
        plyNMS.getWorld().addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // play sound
        playerUseItemSound(ply, weaponType, autoSwing);
        return true;
    }
    protected static boolean playerUseFlail(Player ply, String itemType, String weaponType,
                                            boolean autoSwing, HashMap<String, Double> attrMap, ConfigurationSection weaponSection) {
        // use the weapon
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        Vector facingDir = MathHelper.vectorFromYawPitch_quick(plyNMS.yaw, plyNMS.pitch);
        double projectileSpeed = weaponSection.getDouble("velocity", 5d);
        double reach = weaponSection.getDouble("reach", 10d);
        facingDir.multiply(projectileSpeed);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(ply, facingDir, attrMap, itemType);
        // spawn projectile
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMeleeMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        double useTime = attrMap.getOrDefault("useTime", 20d) * useTimeMulti;
        TerrariaFlail entity = new TerrariaFlail(shootInfo, reach, useTime);
        plyNMS.getWorld().addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // play sound
        playerUseItemSound(ply, weaponType, autoSwing);
        return true;
    }
    // ranged helper functions below
    protected static void handleRangedFire(Player ply, HashMap<String, Double> attrMapOriginal, ConfigurationSection weaponSection,
                                         int fireIndex, int swingAmount,
                                         String itemType, String weaponType, String ammoTypeInitial,
                                         boolean isLoadingWeapon, boolean autoSwing) {
        int fireRoundMax = weaponSection.getInt("fireRounds", 1);
        int fireAmount = isLoadingWeapon ? swingAmount : weaponSection.getInt("shots", 1);
        double spread = weaponSection.getDouble("offSet", -1d);
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        Vector facingDir = MathHelper.vectorFromYawPitch_quick(plyNMS.yaw, plyNMS.pitch);
        // account for arrow attribute.
        String ammoType = ammoTypeInitial;
        HashMap<String, Double> attrMap = (HashMap<String, Double>) attrMapOriginal.clone();
        List<String> ammoConversion = weaponSection.getStringList("ammoConversion." + ammoType);
        // if the ammo could get converted into multiple possible projectiles, handle them separately in the loop instead.
        if  (ammoConversion.size() <= 1) {
            if (ammoConversion.size() == 1) ammoType = ammoConversion.get(0);
            ConfigurationSection ammoAttributeSection = TerrariaHelper.itemConfig.getConfigurationSection(ammoType + ".attributes");
            // if the converted ammo does not have attributes that overrides the original, default to the original
            if (ammoAttributeSection == null)
                ammoAttributeSection = TerrariaHelper.itemConfig.getConfigurationSection(ammoTypeInitial + ".attributes");
            if (ammoAttributeSection != null)
                EntityHelper.tweakAllAttributes(attrMap, ammoAttributeSection, true);
        }
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
                if (ammoAttributeSection != null)
                    EntityHelper.tweakAllAttributes(attrMap, ammoAttributeSection, true);
            }
            Location fireLoc = ply.getEyeLocation();
            Vector fireVelocity = facingDir.clone();
            double projectileSpeed = attrMap.getOrDefault("projectileSpeed", 1d);
            projectileSpeed *= attrMap.getOrDefault("projectileSpeedMulti", 1d);
            if (weaponType.equals("BOW"))
                projectileSpeed *= attrMap.getOrDefault("projectileSpeedArrowMulti", 1d);
            projectileSpeed = Math.sqrt(projectileSpeed) * 0.5;
            // bullet spread
            if (spread > 0d) {
                fireVelocity.multiply(spread);
                fireVelocity.add(MathHelper.randomVector());
                fireVelocity.normalize();
            }
            // handle special weapons (pre-firing)
            switch (itemType) {
                case "海啸弓": {
                    fireLoc.add(MathHelper.vectorFromYawPitch_quick(plyNMS.yaw,
                            plyNMS.pitch + 12.5 * (i - fireAmount / 2))
                            .multiply(1.5));
                    break;
                }
                case "湮灭千星":
                case "代达罗斯风暴弓": {
                    Vector offset = new Vector(Math.random() * 30 - 15, 20 + Math.random() * 20, Math.random() * 30 - 15);
                    fireVelocity = offset.clone().multiply(-1).normalize();
                    Location destination = getPlayerTargetLoc(ply, 64, 4,
                            new EntityHelper.AimHelperOptions()
                                    .setAimMode(true)
                                    .setRandomOffsetRadius(5)
                                    .setTicksOffset(offset.length() / projectileSpeed), true);
                    fireLoc = destination.add(offset);
                    break;
                }
            }
            // setup projectile velocity
            fireVelocity.multiply(projectileSpeed);
            Projectile firedProjectile = EntityHelper.spawnProjectile(ply, fireLoc, fireVelocity, attrMap,
                    EntityHelper.getDamageType(ply), ammoType);
            // handle special weapons (post-firing)
            switch (itemType) {
                case "幻象弓":
                case "幻界": {
                    firedProjectile.addScoreboardTag("isVortex");
                    break;
                }
            }
        }
        // extra projectiles from weapons such as onyx blaster and its upgrades
        switch (itemType) {
            // single extra projectile
            case "星璇机枪":
            case "玛瑙爆破枪":
            case "玛瑙链炮":
            case "奥妮克希亚": {
                Location fireLoc = ply.getEyeLocation();
                Vector fireVelocity = facingDir.clone();
                HashMap<String, Double> attrMapExtraProjectile = attrMap;
                double projectileSpeed = attrMap.getOrDefault("projectileSpeed", 1d);
                boolean shouldFire = true;
                String extraProjectileType = "";
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
                            attrMapExtraProjectile = (HashMap<String, Double>) attrMap.clone();
                            attrMapExtraProjectile.put("damage", 140d);
                        } else {
                            shouldFire = false;
                        }
                        break;
                    }
                }
                // setup projectile velocity
                if (shouldFire) {
                    fireVelocity.multiply(projectileSpeed);
                    EntityHelper.spawnProjectile(ply, fireLoc, fireVelocity, attrMapExtraProjectile,
                            EntityHelper.getDamageType(ply), extraProjectileType);
                }
                break;
            }
        }
        // if this is a delayed shot, play item swing sound
        playerUseItemSound(ply, weaponType, autoSwing);
        // extra delayed shots
        if (fireIndex < fireRoundMax) {
            int fireRoundDelay = weaponSection.getInt("fireRoundsDelay", 20);
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleRangedFire(ply, attrMapOriginal, weaponSection, fireIndex + 1, swingAmount,
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
    protected static boolean playerUseRanged(Player ply, String itemType, int swingAmount, String weaponType,
                                           boolean isLoadingWeapon, boolean autoSwing,
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
        handleRangedFire(ply, attrMap, weaponSection, 1, swingAmount,
                itemType, weaponType, ammoType, isLoadingWeapon, autoSwing);
        // apply CD
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedRangedMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
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
        Vector facingDir = MathHelper.vectorFromYawPitch_quick(plyNMS.yaw, plyNMS.pitch);
        double projectileSpeed = attrMap.getOrDefault("projectileSpeed", 1d);
        projectileSpeed *= attrMap.getOrDefault("projectileSpeedMulti", 1d);
        facingDir.multiply(projectileSpeed);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(ply, facingDir, attrMap, itemType);
        EntityHelper.spawnProjectile(shootInfo);
        // play sound
        playerUseItemSound(ply, weaponType, autoSwing);
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
                                                  int fireIndex, String itemType, String weaponType, boolean autoSwing) {
        int fireRoundMax = weaponSection.getInt("fireRounds", 1);
        int fireAmount = weaponSection.getInt("shots", 1);
        double spread = weaponSection.getDouble("offSet", -1d);
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        Vector facingDir = MathHelper.vectorFromYawPitch_quick(plyNMS.yaw, plyNMS.pitch);
        for (int i = 0; i < fireAmount; i ++) {
            String projectileName = weaponSection.getString("projectileName", "小火花");
            Location fireLoc = ply.getEyeLocation();
            Vector fireVelocity = facingDir.clone();
            double projectileSpeed = attrMap.getOrDefault("projectileSpeed", 1d);
            projectileSpeed *= attrMap.getOrDefault("projectileSpeedMulti", 1d);
            projectileSpeed /= 10;
            if (weaponType.equals("BOW"))
                projectileSpeed *= attrMap.getOrDefault("projectileSpeedArrowMulti", 1d);
            // bullet spread
            if (spread > 0d) {
                fireVelocity.multiply(spread);
                fireVelocity.add(MathHelper.randomVector());
                fireVelocity.normalize();
            }
            // handle special weapons (pre-firing)
            switch (itemType) {
                case "月之耀斑":
                case "暴雪法杖": {
                    Vector offset = new Vector(Math.random() * 30 - 15, 20 + Math.random() * 20, Math.random() * 30 - 15);
                    fireVelocity = offset.clone().multiply(-1).normalize();
                    EntityHelper.AimHelperOptions options = new EntityHelper.AimHelperOptions()
                            .setAimMode(true)
                            .setTicksOffset(offset.length() / projectileSpeed);
                    if (itemType.equals("暴雪法杖"))
                        options.setRandomOffsetRadius(3);
                    Location destination = getPlayerTargetLoc(ply, 64, 4, options, true);
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
            }
            // setup projectile velocity
            fireVelocity.multiply(projectileSpeed);
            Projectile firedProjectile = EntityHelper.spawnProjectile(ply, fireLoc, fireVelocity, attrMap,
                    EntityHelper.getDamageType(ply), projectileName);
        }
        // if this is a delayed shot, play item swing sound
        playerUseItemSound(ply, weaponType, autoSwing);
        // extra delayed shots
        if (fireIndex < fireRoundMax) {
            int fireRoundDelay = weaponSection.getInt("fireRoundsDelay", 20);
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleMagicProjectileFire(ply, attrMap, weaponSection, fireIndex + 1,
                            itemType, weaponType, autoSwing)
                    , fireRoundDelay);
        }
    }
    protected static void handleMagicSpecialFire(Player ply, HashMap<String, Double> attrMap, ConfigurationSection weaponSection,
                                               int fireIndex, String itemType, String weaponType, Location targetedLocation,
                                               boolean autoSwing, int swingAmount, Collection<Entity> damageCD) {
        int fireAmount = 1, fireDelay = 0;
        switch (itemType) {
            case "爆裂藤蔓":
            case "暗影束法杖":
            case "高温射线枪":
            case "终极棱镜":
            case "永夜射线":
            case "女武神权杖":
            case "泰拉射线":
            case "元素射线":
            {
                Collection<Entity> damageExceptions;
                if (damageCD == null) damageExceptions = new HashSet<>();
                else damageExceptions = damageCD;
                GenericHelper.StrikeLineOptions strikeInfo = new GenericHelper.StrikeLineOptions();
                Vector fireDir = targetedLocation.clone().subtract(ply.getEyeLocation()).toVector().normalize();
                double yaw = MathHelper.getVectorYaw(fireDir),
                        pitch = MathHelper.getVectorPitch(fireDir);
                Location startLoc = ply.getEyeLocation().add(fireDir).add(fireDir);
                double length = 8, width = 0.5;
                String particleColor = "255|255|0";
                // some weapons do not need smart targeting, they shoot exactly ticksBeforeHookingFish the cursor
                boolean useSmartTargeting = itemType.equals("元素射线");
                if (!useSmartTargeting) {
                    EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
                    yaw = plyNMS.yaw;
                    pitch = plyNMS.pitch;
                    fireDir = MathHelper.vectorFromYawPitch_quick(yaw, pitch);
                }
                // handle strike line properties
                switch (itemType) {
                    case "爆裂藤蔓": {
                        length = 24;
                        particleColor = "103|78|50";
                        strikeInfo
                                .setDamageCD(4)
                                .setLingerTime(6)
                                .setLingerDelay(5)
                                .setThruWall(true);
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
                    case "终极棱镜": {
                        double convergeProgress = Math.min((double) swingAmount / 25, 1);
                        length = 40 + 24 * convergeProgress;
                        // particle color
                        if (convergeProgress < 1) {
                            String[] particleColors = new String[]{"255|0|0", "255|165|0", "255|255|0", "0|128|0",
                                    "0|0|255", "75|0|130", "238|130|238"};
                            fireAmount = 7;
                            // so that enemies do not get damaged for an unreasonable amount of times
                            damageCD = damageExceptions;
                            particleColor = particleColors[fireIndex % particleColors.length];
                        } else {
                            particleColor = "255|255|255";
                            width = 1;
                            // converged ray deals 3x damage
                            attrMap.put("damage", attrMap.getOrDefault("damage", 125d) * 3);
                        }
                        // strike line additional information
                        strikeInfo
                                .setThruWall(false);
                        // tweak shooting direction
                        if (convergeProgress < 1) {
                            double angle = (360 * (double) fireIndex / fireAmount) - (swingAmount * 20);
                            yaw += MathHelper.xsin_degree(angle) * 20 * (1 - convergeProgress);
                            pitch += MathHelper.xcos_degree(angle) * 20 * (1 - convergeProgress);
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
                                            if (hitEntity instanceof LivingEntity) {
                                                EntityHelper.applyEffect(hitEntity, "带电", 100);
                                            }
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
                                width = 1;
                                particleColor = "225|150|225";
                                strikeInfo
                                        .setDamageCD(10)
                                        .setLingerTime(4)
                                        .setLingerDelay(4);
                                yaw -= (fireIndex - 6) * 2;
                                break;
                            }
                            default:
                                return;
                        }
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
    protected static boolean playerUseMagic(Player ply, String itemType, int swingAmount, String weaponType,
                                          boolean autoSwing,
                                          ConfigurationSection weaponSection, HashMap<String, Double> attrMap) {
        int manaConsumption = (int) Math.round(attrMap.getOrDefault("manaUse", 10d) *
                attrMap.getOrDefault("manaUseMulti", 1d));
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
        if (weaponType.equals("MAGIC_PROJECTILE")) {
            handleMagicProjectileFire(ply, attrMap, weaponSection, 1, itemType, weaponType, autoSwing);
        } else {
            Location targetedLocation = getPlayerTargetLoc(ply, 64, 4,
                    new EntityHelper.AimHelperOptions()
                            .setAimMode(true), true);
            handleMagicSpecialFire(ply, attrMap, weaponSection, 1, itemType, weaponType,
                    targetedLocation, autoSwing, swingAmount, null);
        }
        // apply CD
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMagicMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
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
                    if (GenericHelper.trimText(toCheck.getName()).equals(type) && !toCheck.isDead())
                        return false;
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
    public static void playerUseItemSound(Entity ply, String weaponType, boolean autoSwing) {
        String itemUseSound;
        float volume = 1f, pitch = 1f;
        switch (weaponType) {
            case "BOW":
                itemUseSound = SOUND_BOW_SHOOT;
                volume = 2f;
                break;
            case "GUN":
                if (autoSwing) {
                    itemUseSound = SOUND_GUN_FIRE;
                } else {
                    itemUseSound = SOUND_GUN_FIRE_LOUD;
                    pitch = 1.2f;
                }
                volume = 3f;
                break;
            default:
                itemUseSound = SOUND_GENERIC_SWING;
        }
        ply.getWorld().playSound(ply.getLocation(), itemUseSound, volume, pitch);
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
        // pickaxe
        if (attrMap.getOrDefault("powerPickaxe", 0d) > 1) {
            playerSwingPickaxe(ply, attrMap, isRightClick);
            return;
        }
        // other items that require item type info
        ItemStack mainHandItem = ply.getInventory().getItemInMainHand();
        String itemName = ItemHelper.splitItemName(mainHandItem)[1];
        // fishing rod
        if (!isRightClick && attrMap.getOrDefault("fishingPower", -1d) > 0) {
            playerSwingFishingRod(ply, attrMap, itemName);
            return;
        }
        // if itemName == "", some bug may occur. Also, vanilla items are not useful at all.
        if (itemName.length() > 0) {
            if (isRightClick) {
                // to summon an event
                if (playerUseEventSummon(ply, itemName, mainHandItem)) return;
                // to release a critter
                if (playerUseCritter(ply, itemName, mainHandItem)) return;
                // void bag, piggy bank, musical instruments etc.
                if (playerUseMiscellaneous(ply, itemName)) return;
                // potion and other consumable consumption
                if (playerUsePotion(ply, itemName, mainHandItem, QuickBuffType.NONE)) return;
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
                    if (scoreboardTags.contains("temp_autoSwing")) {
                        if (scoreboardTags.contains("temp_isLoadingWeapon")) {
                            // still loading
                            isLoading = true;
                        } else {
                            // finished loading
                            autoSwing = false;
                        }
                    } else {
                        // start loading, as the auto swing scoreboard tag is added later.
                        ply.addScoreboardTag("temp_isLoadingWeapon");
                        isLoading = true;
                    }
                }
                if (isLoading) {
                    swingAmount = Math.min(swingAmount + 1, maxLoad);
                    ply.addScoreboardTag("temp_autoSwing");
                    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_ITEM_SWING_AMOUNT, swingAmount);
                    displayLoadingProgress(ply, swingAmount, maxLoad);
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
                        success = playerUseRanged(ply, itemName, swingAmount, weaponType,
                                maxLoad > 0, autoSwing, weaponSection, attrMap);
                        break;
                    case "MAGIC_PROJECTILE":
                    case "MAGIC_SPECIAL":
                        success = playerUseMagic(ply, itemName, swingAmount, weaponType,
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
                    playerUseItemSound(ply, weaponType, autoSwing);
                } else {
                    // prevent bug, if the item is not being used successfully, cancel auto swing
                    // this mainly happens when mana has depleted or ammo runs out
                    ply.removeScoreboardTag("temp_autoSwing");
                }
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
