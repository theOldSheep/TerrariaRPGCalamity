package terraria.util;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.HitEntityInfo;

import java.util.*;
import java.util.function.Predicate;

public class ItemUseHelper {
    private static final String SOUND_GENERIC_SWING = "item.genericSwing", SOUND_BOW_SHOOT = "item.bowShoot",
            SOUND_GUN_FIRE = "item.gunfire", SOUND_GUN_FIRE_LOUD = "entity.generic.explode";
    private static final double MELEE_STRIKE_RADIUS = 0.25;
    public static int applyCD(Player ply, double CD) {
        int coolDown = (int) CD;
        if (Math.random() < CD % 1) coolDown ++;
        return applyCD(ply, coolDown);
    }
    public static int applyCD(Player ply, int CD) {
        ply.addScoreboardTag("useCD");
        long lastCDApply = Calendar.getInstance().getTimeInMillis();
        EntityHelper.setMetadata(ply, "useCDInternal", lastCDApply);
        ItemStack tool = ply.getInventory().getItemInMainHand();
        // the CD <= 0: never stops on its own
        PacketPlayOutSetCooldown packet = new PacketPlayOutSetCooldown(CraftItemStack.asNMSCopy(tool).getItem(), CD <= 0 ? 1919810 : CD);
        ((CraftPlayer) ply).getHandle().playerConnection.sendPacket(packet);
        if (CD > 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                if (ply.isOnline() && EntityHelper.getMetadata(ply, "useCDInternal").asLong() == lastCDApply) {
                    if (!PlayerHelper.isProperlyPlaying(ply)) {
                        ply.removeScoreboardTag("autoSwing");
                    }
                    // handle next use
                    ply.removeScoreboardTag("useCD");
                    if (ply.getScoreboardTags().contains("autoSwing")) {
                        playerUseItem(ply);
                    }
                }
            }, CD);
        }
        return CD;
    }
    // util functions for use item
    private static void playerSwingPickaxe(Player ply, HashMap<String, Double> attrMap, boolean isRightClick) {
        ply.playSound(ply.getEyeLocation(), "item.genericSwing", 1, 1);
        double pickaxeReach = 4 + attrMap.getOrDefault("reachExtra", 0d);
        pickaxeReach *= attrMap.getOrDefault("meleeReachMulti", 1d);
        // mine block if applicable
        Block blk = ply.getTargetBlock(GameplayHelper.noMiningSet, (int) Math.round(pickaxeReach));
        if (blk != null) GameplayHelper.playerMineBlock(blk, ply);
        // left click swings until stopped
        if (!isRightClick)
            ply.addScoreboardTag("autoSwing");
        double useCD = attrMap.getOrDefault("useTime", 20d);
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) *
                attrMap.getOrDefault("useSpeedMeleeMulti", 1d) *
                attrMap.getOrDefault("useSpeedMiningMulti", 1d);
        useCD /= useSpeed;
        applyCD(ply, useCD);
    }
    private static boolean playerUseMiscellaneous(Player ply, String itemName) {
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
    private static boolean playerUsePotion(Player ply, String itemType, ItemStack potion) {
        return false;
    }
    // weapon use helper functions below
    private static void displayLoadingProgress(Player ply, int currLoad, int maxLoad) {
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
    private static Location getPlayerTargetLoc(Player ply, double traceDist, double entityEnlargeRadius, GenericHelper.AimHelperOptions aimHelperInfo, boolean strictMode) {
        Location targetLoc = null;
        World plyWorld = ply.getWorld();
        EntityPlayer nmsPly = ((CraftPlayer) ply).getHandle();
        Vector lookDir = MathHelper.vectorFromYawPitch_quick(nmsPly.yaw, nmsPly.pitch);
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
                    targetLoc = endLoc.toLocation(plyWorld);
                }
            }
            // the enemy the player is looking at, if applicable
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
                    targetLoc = GenericHelper.helperAimEntity(ply, hitEntity, aimHelperInfo);
                }
            }
            // if the target location is still null, that is, no block/entity being hit
            if (targetLoc == null) {
                targetLoc = ply.getEyeLocation().add(lookDir.clone().multiply(traceDist));
            }
        }
        return targetLoc;
    }
    // melee helper functions below
    private static void handleSingleZenithSwingAnimation(Player ply, HashMap<String, Double> attrMap,
                                                         Location centerLoc, Vector reachVector, Vector offsetVector,
                                                         Collection<Entity> exceptions, String color,
                                                         GenericHelper.StrikeLineOptions strikeLineInfo,
                                                         int index, int indexMax) {
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
                6, 0.5, "天顶剑", color, exceptions, attrMap, strikeLineInfo);
        int delayAmount = ((index + 1) * 16 / indexMax) - (index * 16 / indexMax);
        if (delayAmount > 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> handleSingleZenithSwingAnimation(ply, attrMap, centerLoc, reachVector, offsetVector, exceptions, color, strikeLineInfo, index + 1, indexMax), delayAmount);
        } else {
            handleSingleZenithSwingAnimation(ply, attrMap, centerLoc, reachVector, offsetVector, exceptions, color, strikeLineInfo, index + 1, indexMax);
        }
    }
    private static void handleSingleZenithStrike(Player ply, HashMap<String, Double> attrMap, List<String> colors, GenericHelper.StrikeLineOptions strikeLineInfo) {
        // setup vector info etc.
        EntityPlayer nmsPly = ((CraftPlayer) ply).getHandle();
        Vector lookDir = MathHelper.vectorFromYawPitch_quick(nmsPly.yaw, nmsPly.pitch);
        Location targetLoc = getPlayerTargetLoc(ply, 96, 5,
                new GenericHelper.AimHelperOptions().setTicksOffset(8).setAimMode(true), false);
        Location centerLoc = targetLoc.clone().add(ply.getEyeLocation()).multiply(0.5);
        Vector reachVec = centerLoc.clone().subtract(ply.getEyeLocation()).toVector();
        Vector offsetVec = null;
        while (offsetVec == null || offsetVec.lengthSquared() < 1e-5) {
            offsetVec = MathHelper.randomVector();
            offsetVec.subtract(MathHelper.vectorProjection(reachVec, offsetVec));
        }
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
        int loopAmount = (int) (reachLength * 4);
        String color = "255|0|0";
        if (colors != null && colors.size() > 0) color = colors.get((int) (Math.random() * colors.size()));
        handleSingleZenithSwingAnimation(ply, attrMap, centerLoc, reachVec, offsetVec, new HashSet<>(), color, strikeLineInfo, 0, loopAmount);
    }
    // warning: this function modifies attrMap and damaged!
    private static void handleMeleeSwing(Player ply, HashMap<String, Double> attrMap, Vector lookDir,
                                         Collection<Entity> damaged, ConfigurationSection weaponSection,
                                         double yaw, double pitch, String weaponType, double size,
                                         boolean dirFixed, boolean stabOrSwing, int currentIndex, int maxIndex) {
        if (!PlayerHelper.isProperlyPlaying(ply)) return;
        if (!dirFixed) {
            EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
            yaw = plyNMS.yaw;
            pitch = plyNMS.pitch;
            lookDir = MathHelper.vectorFromYawPitch_quick(yaw, pitch);
        }
        String color = "102|255|255";
        GenericHelper.StrikeLineOptions strikeLineInfo =
                new GenericHelper.StrikeLineOptions()
                        .setThruWall(false);
        if (stabOrSwing) {
            boolean shouldStrike;
            double strikeYaw = yaw, strikePitch = pitch;
            if (weaponType.equals("星光")) {
                shouldStrike = currentIndex % 2 == 0;
                if (shouldStrike) {
                    // prevent DPS loss due to damage invincibility frame
                    damaged = new ArrayList<>();
                    List<String> colorCandidates = weaponSection.getStringList("particleColor");
                    color = colorCandidates.get((int) (Math.random() * colorCandidates.size()));
                    if (currentIndex > 0) ply.getWorld().playSound(ply.getLocation(), SOUND_GENERIC_SWING, 1f, 1f);
                    strikeYaw += Math.random() * 30 - 15;
                    strikePitch += Math.random() * 30 - 15;
                }
            } else {
                shouldStrike = currentIndex == 0;
                if (shouldStrike) {
                    List<String> colors = weaponSection.getStringList("particleColor");
                    if (colors.size() > 0)
                        color = colors.get(0);
                }
            }
            if (shouldStrike)
                GenericHelper.handleStrikeLine(ply, ply.getEyeLocation().add(lookDir), strikeYaw, strikePitch, size, MELEE_STRIKE_RADIUS,
                        weaponType, color, damaged, attrMap, strikeLineInfo);
        } else {
            List<String> colors = weaponSection.getStringList("particleColor");
            if (weaponType.equals("天顶剑")) {
                if (currentIndex % 4 == 0) {
                    handleSingleZenithStrike(ply, (HashMap<String, Double>) attrMap.clone(), colors, strikeLineInfo);
                }
            } else {
                if (colors.size() > 0)
                    color = colors.get(0);
                int loopTimes = Math.max(maxIndex, 35);
                int indexStart = loopTimes * currentIndex / (maxIndex + 1);
                int indexEnd = loopTimes * (currentIndex + 1) / (maxIndex + 1);
                for (int i = indexStart; i < indexEnd; i ++) {
                    double actualPitch = ((170 * (double) i / loopTimes) - 110);
                    Vector offsetDir = MathHelper.vectorFromYawPitch_quick(yaw, actualPitch);
                    GenericHelper.handleStrikeLine(ply, ply.getEyeLocation().add(offsetDir), yaw, actualPitch,
                            size, MELEE_STRIKE_RADIUS, weaponType, color, damaged, attrMap, strikeLineInfo);
                }
            }
        }
        if (currentIndex < maxIndex) {
            double finalYaw = yaw;
            double finalPitch = pitch;
            Vector finalLookDir = lookDir;
            Collection<Entity> finalDamaged = damaged;
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleMeleeSwing(ply, attrMap, finalLookDir, finalDamaged, weaponSection, finalYaw, finalPitch,
                            weaponType, size, dirFixed, stabOrSwing, currentIndex + 1, maxIndex), 1);
        }
    }
    // stab and swing. NOT NECESSARILY MELEE DAMAGE!
    private static boolean playerUseMelee(Player ply, String itemType,
                                          ConfigurationSection weaponSection, HashMap<String, Double> attrMap, int swingAmount, boolean stabOrSwing) {
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        double yaw = plyNMS.yaw, pitch = plyNMS.pitch;
        double size = weaponSection.getDouble("size", 3d);
        Vector lookDir = MathHelper.vectorFromYawPitch_quick(yaw, pitch);
        size *= attrMap.getOrDefault("meleeReachMulti", 1d);
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) * attrMap.getOrDefault("useSpeedMeleeMulti", 1d);
        double useTimeMulti = 1 / useSpeed;
        ConfigurationSection projectileInfo = weaponSection.getConfigurationSection("projectileInfo");
        if (projectileInfo != null) {
            HashMap<String, Double> attrMapProjectile = (HashMap<String, Double>) attrMap.clone();
            int shootInterval = (int) Math.floor(projectileInfo.getInt("interval", 1) * useSpeed);
            int shootAmount = 1;
            if (swingAmount % shootInterval == 0) {
                lookDir.multiply(projectileInfo.getDouble("velocity", 1d));
                String projectileType = projectileInfo.getString("name", "");
                for (int i = 0; i < shootAmount; i ++) {
                    if (stabOrSwing) {
                        Vector v = MathHelper.vectorFromYawPitch_quick(yaw, pitch);
                        v.multiply(size);
                        EntityHelper.spawnProjectile(ply, ply.getEyeLocation().add(v),
                                lookDir, attrMapProjectile, "Melee", projectileType);
                    } else {
                        EntityHelper.spawnProjectile(ply, ply.getEyeLocation(),
                                lookDir, attrMapProjectile, "Melee", projectileType);
                    }
                }
            }
        }
        int coolDown = applyCD(ply, attrMap.getOrDefault("useTime", 20d) * useTimeMulti);
        boolean dirFixed = weaponSection.getBoolean("dirFixed", true);
        handleMeleeSwing(ply, attrMap, lookDir, new HashSet<>(), weaponSection, yaw, pitch, itemType, size,
                dirFixed, stabOrSwing, 0, coolDown);
        return true;
    }
    // ranged helper functions below
    private static void handleRangedFire(Player ply, HashMap<String, Double> attrMapOriginal, ConfigurationSection weaponSection,
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
        List<String> ammoConversion = weaponSection.getStringList("ammoConversion." + ammoTypeInitial);
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
                case "海啸弓": {
                    fireLoc.add(MathHelper.vectorFromYawPitch_quick(plyNMS.yaw,
                            plyNMS.pitch + 12.5 * (i - fireAmount / 2))
                            .multiply(1.5));
                    break;
                }
                case "湮灭千星":
                case "代达罗斯风暴弓": {
                    Vector offset = new Vector(Math.random() * 15 - 7.5, 20 + Math.random() * 10, Math.random() * 15 - 7.5);
                    fireVelocity = offset.clone().multiply(-1).normalize();
                    Location destination = getPlayerTargetLoc(ply, 64, 4,
                            new GenericHelper.AimHelperOptions()
                                    .setRandomOffsetRadius(1)
                                    .setTicksOffset(offset.length() / projectileSpeed), true);
                    fireLoc = destination.add(offset);
                    break;
                }
            }
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
    private static String consumePlayerAmmo(Player ply, Predicate<ItemStack> ammoPredicate, double consumptionRate) {
        // in the player's inventory
        Inventory plyInv = ply.getInventory();
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem != null && ammoPredicate.test(currItem)) {
                String ammoName = ItemHelper.splitItemName(currItem)[1];
                if (Math.random() < consumptionRate) currItem.setAmount(currItem.getAmount() - 1);
                return ammoName;
            }
        }
        // in the player's void bag
        if (PlayerHelper.hasVoidBag(ply)) {
            Inventory voidBagInv = PlayerHelper.getInventory(ply, "voidBag");
            for (ItemStack currItem : voidBagInv.getContents()) {
                if (currItem != null && ammoPredicate.test(currItem)) {
                    String ammoName = ItemHelper.splitItemName(currItem)[1];
                    if (Math.random() < consumptionRate) currItem.setAmount(currItem.getAmount() - 1);
                    return ammoName;
                }
            }
        }
        // ammunition not found
        return null;
    }
    private static boolean playerUseRanged(Player ply, String itemType, int swingAmount, String weaponType,
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
    private static void playerUseItemSound(Player ply, String weaponType, boolean autoSwing) {
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
    public static void playerUseItem(Player ply) {
        // cursed players can not use any item
        if (EntityHelper.hasEffect(ply, "诅咒")) {
            ply.removeScoreboardTag("autoSwing");
            EntityHelper.setMetadata(ply, "swingAmount", 0);
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
        // other items
        ItemStack mainHandItem = ply.getInventory().getItemInMainHand();
        String itemName = ItemHelper.splitItemName(mainHandItem)[1];
        // void bag, piggy bank, musical instruments etc.
        if (isRightClick && playerUseMiscellaneous(ply, itemName)) return;
        // potion and other consumable consumption
        if (isRightClick && playerUsePotion(ply, itemName, mainHandItem)) return;
        // weapon
        String weaponYMLPath = itemName + (isRightClick ? "_RIGHT_CLICK" : "");
        ConfigurationSection weaponSection = TerrariaHelper.weaponConfig.getConfigurationSection(weaponYMLPath);
        if (weaponSection != null) {
            // handle loading
            boolean autoSwing = weaponSection.getBoolean("autoSwing", false);
            boolean isLoading = false;
            int maxLoad = weaponSection.getInt("maxLoad", 0);
            int swingAmount = EntityHelper.getMetadata(ply, "swingAmount").asInt();
            if (maxLoad > 0) {
                swingAmount = Math.min(swingAmount, maxLoad);
                if (scoreboardTags.contains("autoSwing")) {
                    if (scoreboardTags.contains("isLoadingWeapon")) {
                        // still loading
                        isLoading = true;
                    } else {
                        // finished loading
                        autoSwing = false;
                    }
                } else {
                    // start loading, as the auto swing scoreboard tag is added later.
                    ply.addScoreboardTag("isLoadingWeapon");
                    isLoading = true;
                }
            }
            if (isLoading) {
                swingAmount = Math.min(swingAmount + 1, maxLoad);
                ply.addScoreboardTag("autoSwing");
                EntityHelper.setMetadata(ply, "swingAmount", swingAmount);
                displayLoadingProgress(ply, swingAmount, maxLoad);
                double loadSpeedMulti = weaponSection.getDouble("loadTimeMulti", 0.5d);
                applyCD(ply, attrMap.getOrDefault("useTime", 10d)
                        * attrMap.getOrDefault("useTimeMulti", 1d) * loadSpeedMulti);
                return;
            }
            // use weapon
            String weaponType = weaponSection.getString("type", "");
            // prevent accidental glitch that creates endless item use cool down
            if (attrMap.getOrDefault("useCD", 0d) < 0.01) PlayerHelper.setupAttribute(ply);
            boolean success = false;
            switch (weaponType) {
                case "STAB":
                case "SWING":
                    success = playerUseMelee(ply, itemName, weaponSection, attrMap, swingAmount, weaponType.equals("STAB"));
                    break;
                case "BOW":
                case "GUN":
                case "ROCKET":
                case "SPECIAL_AMMO":
                    success = playerUseRanged(ply, itemName, swingAmount, weaponType,
                            maxLoad > 0, autoSwing, weaponSection, attrMap);
                    break;
            }
            if (success) {
                if (autoSwing) {
                    ply.addScoreboardTag("autoSwing");
                    EntityHelper.setMetadata(ply, "swingAmount", swingAmount + 1);
                }
                // play item use sound
                playerUseItemSound(ply, weaponType, autoSwing);
            }
        }
    }
    public static void spawnSentryMinion(Player ply, String type, HashMap<String, Double> attrMap, boolean sentryOrMinion) {
        if (sentryOrMinion) {

        } else {

        }
    }
    private static void minionAI(Entity minion, Player owner, String nameMinion, int minionSlot) {

    }
}
