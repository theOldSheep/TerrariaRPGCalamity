package terraria.util;

import net.minecraft.server.v1_12_R1.EntityPlayer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.gameplay.Event;

import java.util.*;
import java.util.logging.Level;

public class PlayerHelper {
    // constants
    private static YmlHelper.YmlSection soundConfig = YmlHelper.getFile("plugins/Data/sounds.yml");
    private static HashMap<String, Double> defaultPlayerAttrMap = new HashMap<>(60);
    private static HashSet<String> defaultPlayerEffectInflict = new HashSet<>(8);
    static {
        // init default player attribute map
        defaultPlayerAttrMap.put("armorPenetration", 0d);
        defaultPlayerAttrMap.put("ammoConsumptionRate", 1d);
        defaultPlayerAttrMap.put("arrowConsumptionRate", 1d);
        defaultPlayerAttrMap.put("bounce", 0d);
        defaultPlayerAttrMap.put("crit", 0d);
        defaultPlayerAttrMap.put("critRanged", 0d);
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
        defaultPlayerAttrMap.put("defence", 0d);
        defaultPlayerAttrMap.put("defenceMulti", 1d);
        defaultPlayerAttrMap.put("healthMulti", 1d);
        defaultPlayerAttrMap.put("invulnerabilityTick", 10d);
        defaultPlayerAttrMap.put("knockback", 0d);
        defaultPlayerAttrMap.put("knockbackResistance", 0d);
        defaultPlayerAttrMap.put("knockbackMeleeMulti", 1d);
        defaultPlayerAttrMap.put("knockbackMulti", 1d);
        defaultPlayerAttrMap.put("manaRegen", 4d);
        defaultPlayerAttrMap.put("manaRegenMulti", 1d);
        defaultPlayerAttrMap.put("manaUse", 0d);
        defaultPlayerAttrMap.put("manaUseMulti", 1d);
        defaultPlayerAttrMap.put("maxHealth", 200d);
        defaultPlayerAttrMap.put("maxHealthMulti", 1d);
        defaultPlayerAttrMap.put("meleeReachMulti", 1d);
        defaultPlayerAttrMap.put("minionLimit", 1d);
        defaultPlayerAttrMap.put("mobLimit", 15d);
        defaultPlayerAttrMap.put("mobSpawnRate", 0.25d);
        defaultPlayerAttrMap.put("mobSpawnRateMulti", 1d);
        defaultPlayerAttrMap.put("penetration", 0d);
        defaultPlayerAttrMap.put("powerPickaxe", 0d);
        defaultPlayerAttrMap.put("projectileSpeed", 0d);
        defaultPlayerAttrMap.put("projectileSpeedMulti", 1d);
        defaultPlayerAttrMap.put("projectileSpeedArrowMulti", 1d);
        defaultPlayerAttrMap.put("regen", 2d);
        defaultPlayerAttrMap.put("regenMulti", 1d);
        defaultPlayerAttrMap.put("sentryLimit", 1d);
        defaultPlayerAttrMap.put("speed", 0.2d);
        defaultPlayerAttrMap.put("speedMulti", 1d);
        defaultPlayerAttrMap.put("useTime", 0d);
        defaultPlayerAttrMap.put("useTimeMagicMulti", 1d);
        defaultPlayerAttrMap.put("useTimeMeleeMulti", 1d);
        defaultPlayerAttrMap.put("useTimeMulti", 1d);
        defaultPlayerAttrMap.put("useTimeRangedMulti", 1d);
        // init default player buff inflict map
        defaultPlayerEffectInflict.add("buffInflict");
        defaultPlayerEffectInflict.add("buffInflictMagic");
        defaultPlayerEffectInflict.add("buffInflictMelee");
        defaultPlayerEffectInflict.add("buffInflictRanged");
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
            return null;
        }
    }
    public static HashMap<String, Double> getDefaultPlayerAttributes() {
        return (HashMap<String, Double>) defaultPlayerAttrMap.clone();
    }
    // threads
    public static void threadGrapplingHook() {
        // every 3 ticks (~1/7 second)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
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
                                GenericHelper.handleParticleLine(dVec, dVec.length(), 0, 1, 1,
                                        ply.getEyeLocation(), EntityHelper.getMetadata(hook, "color").asString());
                            }
                        }
                        for (Entity hook : hooksToRemove) hooks.remove(hook);
                        if (hookedAmount >= 1) {
                            ply.setGravity(false);
                            EntityHelper.setMetadata(ply, "thrust", 0);
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
        }, 3, 0);
    }
    public static void threadArmorAccessory() {
        // every 5 ticks (1/4 second)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                try {
                    // validate the current player
                    if (PlayerHelper.isProperlyPlaying(ply)) {
                        // handle armor sets
                        switch (EntityHelper.getMetadata(ply, "armorSet").asString()) {
                            case "星尘套装":
                                // TODO: spawn a stardust guardian
                                break;
                            case "叶绿魔法套装":
                            case "叶绿射手套装":
                            case "叶绿战士套装":
                                // chlorophyte

                                break;
                        }
                        // handle accessories
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] threadArmorAccessory ", e);
                }
            }
        }, 5, 0);
    }
    public static void threadThrustRegen() {
        // every 4 ticks (1/5 second)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                try {
                    // validate the current player
                    if (PlayerHelper.isProperlyPlaying(ply)) {
                        if (ply.getWorld().getBlockAt(ply.getLocation().subtract(0, 0.5, 0)).getType().isSolid()) {
                            EntityHelper.setMetadata(ply, "thrust", 0);
                            EntityHelper.setMetadata(ply, "thrustProgress", 0);
                        }
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] threadThrustRegen ", e);
                }
            }
        }, 4, 0);
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
        }, 10, 0);
    }
    public static void threadBGM() {
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
                                current = soundConfig.getString("boss." + bossName, "");
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
                        boolean isDayTime = ! (MathHelper.isBetween(plyWorld.getTime(), 13500, 22500));
                        if (current.equals("")) {
                            String biomeType = WorldHelper.getBiome(ply.getLocation());
                            switch (WorldHelper.getHeightLayer(ply.getLocation())) {
                                case "space":
                                    current = isDayTime ? "space_night" : "space";
                                    break;
                                case "underground":
                                case "cavern":
                                    switch (biomeType) {
                                        case "normal":
                                        case "corruption":
                                        case "hallow":
                                        case "desert":
                                        case "ocean":
                                        case "tundra":
                                        case "jungle":
                                        case "astral_infection":
                                            current = biomeType + "_underground";
                                            break;
                                        default:
                                            current = biomeType;
                                    }
                                    break;
                                default:
                                    switch (biomeType) {
                                        case "dungeon":
                                        case "temple":
                                        case "underworld":
                                        case "brimstone_crag":
                                        case "corruption":
                                        case "astral_infection":
                                        case "desert":
                                        case "sunken_sea":
                                        case "sulphurous_ocean":
                                        case "abyss":
                                            // always the same bgm
                                            current = biomeType;
                                            break;
                                        case "hallow":
                                            current = isDayTime ? "hallow" : "normal_night";
                                            break;
                                        default:
                                            current = isDayTime ? biomeType : biomeType + "_night";
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
                    long musicDuration = soundConfig.getLong("lengths." + current, 0L);
                    ply.sendMessage(current + ", " + (musicDuration + lastTime - currentTime) + " ms left.");
                    boolean shouldPlayMusic = false;
                    if (musicDuration + lastTime < currentTime) {
                        // full song finished playing
                        shouldPlayMusic = true;
                        // if the current one playing is the full version, next one shall be reduced version
                        if (current.endsWith("_full")) current = current.replace("_full", "");
                    } else if (!last.equals(current)) shouldPlayMusic = true;
                    if (shouldPlayMusic) {
                        ply.sendMessage(current + ", replayed!");
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
        }, 4, 0);
    }
    // others
    public static void initPlayerStats(Player ply) {
        EntityHelper.initEntityMetadata(ply);
        ply.setFoodLevel(0);
        ply.setGravity(true);
        // crafting variables
        EntityHelper.setMetadata(ply, "craftingStation", "CLOSED");
        EntityHelper.setMetadata(ply, "recipeNumber", -1);
        // weapon use variables
        EntityHelper.setMetadata(ply, "isLoadingWeapon", false);
        EntityHelper.setMetadata(ply, "autoSwing", false);
        EntityHelper.setMetadata(ply, "swingAmount", 0);
        EntityHelper.setMetadata(ply, "nextMinionIndex", 0);
        EntityHelper.setMetadata(ply, "nextSentryIndex", 0);
        EntityHelper.setMetadata(ply, "useCD", false);
        // object variables
        EntityHelper.setMetadata(ply, "minions", new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, "sentries", new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, "accessory", new HashSet<String>());
        EntityHelper.setMetadata(ply, "effects", new HashMap<String, Integer>());
        EntityHelper.setMetadata(ply, "attrMap", PlayerHelper.getDefaultPlayerAttributes());
        EntityHelper.setMetadata(ply, "effectInflict", PlayerHelper.getDefaultPlayerEffectInflict());
        EntityHelper.setMetadata(ply, "hooks", new ArrayList<Entity>());
        // mob spawning variable
        EntityHelper.setMetadata(ply, "mobAmount", 0);
        // movement variable
        EntityHelper.setMetadata(ply, "grapplingHookItem", "");
        EntityHelper.setMetadata(ply, "thrusting", false);
        EntityHelper.setMetadata(ply, "thrust", 0);
        EntityHelper.setMetadata(ply, "thrustProgress", 0);
        // other variable
        EntityHelper.setMetadata(ply, "team", "red");
        EntityHelper.setMetadata(ply, "armorSet", "");
        EntityHelper.setMetadata(ply, "lastBackground", "");
        EntityHelper.setMetadata(ply, "lastBGM", "normal");
        EntityHelper.setMetadata(ply, "lastBGMTime", 0L);
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
    public static boolean hasDefeated(Player player, String progressToCheck) {
        YmlHelper.YmlSection fileSection = YmlHelper.getFile("plugins/PlayerData/" + player.getName() + ".yml");
        return fileSection.getBoolean("bossDefeated" + progressToCheck, false);
    }
    public static boolean isProperlyPlaying(Player player) {
        if (!player.isOnline()) return false;
        if (player.getGameMode() != GameMode.SURVIVAL) return false;
        return !player.getScoreboardTags().contains("unauthorized");
    }
    public static HashSet<String> getAccessories(Entity entity) {
        try {
            return (HashSet<String>) EntityHelper.getMetadata(entity, "accessory").value();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Entity Helper] getAccessories", e);
        }
        return new HashSet<>();
    }
    public static void handleGrapplingHook(Player ply) {
        try {
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
            Vector velocity = GenericHelper.vectorFromYawPitch_quick(yaw, pitch);
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
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] handleGrapplingHook ", e);
        }
    }

}
