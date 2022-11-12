package terraria.util;

import com.earth2me.essentials.api.Economy;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.IChatBaseComponent;
import net.minecraft.server.v1_12_R1.PacketPlayOutTitle;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.libs.jline.internal.Nullable;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
import java.util.logging.Level;

public class PlayerHelper {
    // constants
    private static YmlHelper.YmlSection soundConfig = YmlHelper.getFile("plugins/Data/sounds.yml");
    private static YmlHelper.YmlSection settingConfig = YmlHelper.getFile("plugins/Data/setting.yml");
    private static HashMap<String, Double> defaultPlayerAttrMap = new HashMap<>(60);
    private static HashSet<String> defaultPlayerEffectInflict = new HashSet<>(8);
    static {
        // init default player attribute map
        defaultPlayerAttrMap.put("armorPenetration", 0d);
        defaultPlayerAttrMap.put("ammoConsumptionRate", 1d);
        defaultPlayerAttrMap.put("arrowConsumptionRate", 1d);
        defaultPlayerAttrMap.put("bounce", 0d);
        defaultPlayerAttrMap.put("crit", 0d);
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
        defaultPlayerAttrMap.put("minionLimit", 1d);
        defaultPlayerAttrMap.put("mobLimit", 15d);
        defaultPlayerAttrMap.put("mobSpawnRate", 0.25d);
        defaultPlayerAttrMap.put("mobSpawnRateMulti", 1d);
        defaultPlayerAttrMap.put("penetration", 0d);
        defaultPlayerAttrMap.put("powerPickaxe", 0d);
        defaultPlayerAttrMap.put("projectileSpeed", 0d);
        defaultPlayerAttrMap.put("projectileSpeedMulti", 1d);
        defaultPlayerAttrMap.put("projectileSpeedArrowMulti", 1d);
        defaultPlayerAttrMap.put("regen", 0d);
        defaultPlayerAttrMap.put("regenMulti", 1d);
        defaultPlayerAttrMap.put("sentryLimit", 1d);
        defaultPlayerAttrMap.put("speed", 0.2d);
        defaultPlayerAttrMap.put("speedMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMagicMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMeleeMulti", 1d);
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
    public static void setMoney(Player ply, double amount) {
        try {
            Economy.setMoney(ply.getUniqueId(), BigDecimal.valueOf(amount));
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] setMoney ", e);
        }
    }
    public static double getMoney(Player ply) {
        try {
            return Economy.getMoneyExact(ply.getUniqueId()).doubleValue();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] getMoney ", e);
            return 0d;
        }
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
    // TODO
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
        }, 4, 0);
    }
    public static void threadRegen() {
        boolean debugMessage = false;
        int delay = 4;
        double perTickMulti = (double)delay / 20;
        // every 4 ticks (1/5 second)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
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
                            // minion index, sentry index etc get to reset
                            initPlayerStats(ply);
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
                        // health regen
                        {
                            // init variables
                            double healthRegenTime = EntityHelper.getMetadata(ply, "regenTime").asDouble();
                            double effectiveRegenTime;
                            if (healthRegenTime <= 300) effectiveRegenTime = 0;
                            else if (healthRegenTime <= 800) effectiveRegenTime = (healthRegenTime - 300) / 100;
                            else effectiveRegenTime = 5 + ((healthRegenTime - 800) / 200);
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
                            ply.setHealth(Math.min(ply.getHealth() + regenAmount, maxHealth));
                            healthRegenTime += delay;
                            EntityHelper.setMetadata(ply, "regenTime", Math.min(healthRegenTime, 1200));
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
                                double manaRegenRate = ((maxMana * (moved ? 1d / 8 : 1d / 5)) + 1 + manaRegenBonus) * (manaRatio * 0.8 + 0.2) * 1.15;
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
        }, delay, 0);
    }
    public static void threadAttribute() {
        int delay = 10;
        // every 10 ticks (1/2 second)
        // note that players would have their stats updated
        // when they use their weapon after switching hot bar slot or closes an inventory.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                setupAttribute(ply);
            }
        }, delay, 0);
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
        ply.removeScoreboardTag("isLoadingWeapon");
        ply.removeScoreboardTag("autoSwing");
        ply.removeScoreboardTag("useCD");
        EntityHelper.setMetadata(ply, "swingAmount", 0);
        EntityHelper.setMetadata(ply, "nextMinionIndex", 0);
        EntityHelper.setMetadata(ply, "nextSentryIndex", 0);
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
        ply.removeScoreboardTag("thrusting");
        EntityHelper.setMetadata(ply, "grapplingHookItem", "");
        EntityHelper.setMetadata(ply, "thrust", 0);
        EntityHelper.setMetadata(ply, "thrustProgress", 0);
        // other variable
        EntityHelper.setMetadata(ply, "team", "red");
        EntityHelper.setMetadata(ply, "armorSet", "");
        // bgm and background
        EntityHelper.setMetadata(ply, "lastBackground", "");
        EntityHelper.setMetadata(ply, "lastBGM", "normal");
        EntityHelper.setMetadata(ply, "lastBGMTime", 0L);
        // regeneration
        EntityHelper.setMetadata(ply, "lastLocation", ply.getLocation());
        EntityHelper.setMetadata(ply, "regenTime", 0d);
        EntityHelper.setMetadata(ply, "manaRegenDelay", 0d);
        EntityHelper.setMetadata(ply, "manaRegenCounter", 0d);
        // inventories
        loadInventories(ply);
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
    // TODO
    public static void setupAttribute(Player player) {
        try {

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] setupAttribute ", e);
        }
    }
    public static void loadInventories(Player ply) {
        HashMap<String, Inventory> inventories = new HashMap<>();
        YmlHelper.YmlSection plyFile = YmlHelper.getFile("plugins/PlayerData/" + ply.getName() + ".yml");
        // storage inventories (piggy bank, void bag)
        List<String> otherInvs = settingConfig.getStringList("settings.playerInventories");
        for (String invName : otherInvs) {
            String title = settingConfig.getString("settings.playerInventoryTitles." + invName, "");
            List<String> contents = plyFile.getStringList("inventory." + invName);
            if (contents == null) contents = new ArrayList<>(1);
            Inventory inv = Bukkit.createInventory(ply, 54, title);
            int slot = 0;
            for (String itemInfo : contents) {
                inv.setItem(slot, ItemHelper.getItemFromDescription(itemInfo, false));
                slot ++;
            }
            inventories.put(invName, inv);
        }
        EntityHelper.setMetadata(ply, "inventories", inventories);
    }
    // TODO
    public static void saveInventories(Player ply) {
        HashMap<String, Inventory> inventories = (HashMap<String, Inventory>) EntityHelper.getMetadata(ply, "inventories").value();
        YmlHelper.YmlSection plyFile = YmlHelper.getFile("plugins/PlayerData/" + ply.getName() + ".yml");
        for (String invType : inventories.keySet()) {
            Inventory currInv = inventories.get(invType);
            ArrayList<String> result = new ArrayList<>(54);
            for (int i = 0; i < 54; i ++) {
                result.add(ItemHelper.getItemDescription(currInv.getItem(i)));
            }
            plyFile.set("inventory." + invType, result);
        }
    }
    @Nullable
    public static Inventory getInventory(Player ply, String key) {
        return ((HashMap<String, Inventory>) EntityHelper.getMetadata(ply, "inventories").value())
                .get(key);
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
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Player Helper] handleGrapplingHook ", e);
        }
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
        boolean hasVoidBag = hasVoidBag(ply);
        Inventory voidBagInv = getInventory(ply, "voidBag");
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem == null || currItem.getType() == Material.AIR) return true;
        }
        // void bag
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
    public static int addItemToGenericInventory(ItemStack item, Inventory inventory) {
        if (item == null) return 0;
        // returns how many remaining
        HashMap<Integer, ItemStack> remainingItemMap = inventory.addItem(item);
        if (remainingItemMap.isEmpty()) return 0;
        else return remainingItemMap.get(0).getAmount();
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
            amountRemaining = addItemToGenericInventory(item, ply.getInventory());
            if (amountRemaining > 0) {
                // put the item in the player's void bag, if the player has a void bag in the inventory
                if (hasVoidBag(ply)) {
                    Inventory voidBagInv = getInventory(ply, "voidBag");
                    if (voidBagInv != null)
                        amountRemaining = addItemToGenericInventory(item, voidBagInv);
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
    // TODO: hologram
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
    public static Location getSpawnLocation(Player ply) {
        if (ply.getBedSpawnLocation() != null && !ply.getScoreboardTags().contains("bedCancelled"))
            return ply.getBedSpawnLocation();
        return Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE).getSpawnLocation();
    }
    public static void sendActionBar(Player player, String message) {
        IChatBaseComponent actionBar = IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + message + "\"}");
        PacketPlayOutTitle packet = new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.ACTIONBAR, actionBar);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    private static void spectreProjectileTick(Vector currDir, LivingEntity target, int idx, double velocity, Player dPly, Location loc, double num, boolean healingOrDamage, String color) {
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
            if (idx == 6) velocity = 4;
            double distSqr = loc.distanceSquared(targetLoc);
            if (distSqr < velocity * velocity) {
                // if the projectile reaches its target
                currDir = targetLoc.clone().subtract(loc).toVector();
                velocity = Math.sqrt(distSqr);
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
                    velocity += 0.25;
                }
            }
            // hits its target
            if (distSqr < 0) {
                if (healingOrDamage) {
                    heal((Player) target, num);
                } else {
                    EntityHelper.handleDamage(target, dPly, num, "spectre");
                }
                return;
            }
        } else
            velocity += 0.1;
        MathHelper.setVectorLength(currDir, velocity);
        GenericHelper.handleParticleLine(currDir, velocity, 0.01, loc, color);
        loc.add(currDir);
        Vector finalCurrDir = currDir;
        double finalVelocity = velocity;
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () ->
                spectreProjectileTick(finalCurrDir, target, idx + 1, finalVelocity, dPly, loc, num, healingOrDamage, color),
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
            // get the valid target around loc that is closest to the player
            double targetDistSqr = 1e9;
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 24, 24, 24)) {
                if (EntityHelper.checkCanDamage(dPly, e)) {
                    double distSqr = e.getLocation().distanceSquared(dPly.getLocation());
                    if (distSqr < targetDistSqr) {
                        target = e;
                        targetDistSqr = distSqr;
                    }
                }
            }
        }
        if (target != null) {
            spectreProjectileTick(MathHelper.randomVector(), (LivingEntity) target, 0, 0.5, dPly, loc, num, healingOrDamage, color);
        }
    }
    public static void playerSpectreArmor(Player dPly, Entity v, double dmg) {
        if (!v.getScoreboardTags().contains("isMonster")) return;
        String armorSet = EntityHelper.getMetadata(dPly, "armorSet").asString();
        switch (armorSet) {
            case "星云套装": {
                if (dPly.getScoreboardTags().contains("tempNebulaCD")) break;
                ItemStack nebulaItem = new ItemStack(Material.FLINT_AND_STEEL);
                double rdm = Math.random();
                if (rdm < 1d/9)
                    nebulaItem.getItemMeta().setDisplayName("§c生命强化焰");
                else if (rdm < 2d/9)
                    nebulaItem.getItemMeta().setDisplayName("§d伤害强化焰");
                else if (rdm < 1d/3)
                    nebulaItem.getItemMeta().setDisplayName("§9魔力强化焰");
                else break;
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
                        createSpectreProjectile(dPly, v.getLocation().add(0, 1.5d, 0), (int) Math.ceil(projectilePower), true, "255|255|255");
                        // 80 health/second
                        coolDownTicks = (int) (projectilePower / 4);
                    } else {
                        double projectilePower = (int) Math.ceil(dmg * 0.5);
                        createSpectreProjectile(dPly, v.getLocation().add(0, 1.5d, 0), (int) Math.ceil(projectilePower), false, "255|255|255");
                        // 800 dmg/second
                        coolDownTicks = (int) (projectilePower / 40);
                    }
                    Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                        if (dPly.isOnline()) dPly.removeScoreboardTag("tempSpectreCD");
                    }, coolDownTicks);
                }
                break;
            }
        }
    }
}
