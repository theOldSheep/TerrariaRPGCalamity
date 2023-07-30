package terraria.gameplay;

import net.minecraft.server.v1_12_R1.BossBattle;
import net.minecraft.server.v1_12_R1.BossBattleServer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.boss.event.celestialPillar.CelestialPillar;
import terraria.entity.boss.event.everscream.Everscream;
import terraria.entity.boss.event.headlessHorseman.HeadlessHorseman;
import terraria.entity.boss.event.iceQueen.IceQueen;
import terraria.entity.boss.event.mourningWood.MourningWood;
import terraria.entity.boss.event.pumpking.PumpkingHead;
import terraria.entity.boss.event.santaNK1.SantaNK1;
import terraria.util.*;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;

public class EventAndTime {
    // enums and mappings that maps general name back to enum value
    private static final HashMap<String, EventInfoMapKeys> infoKeyMapping = new HashMap<>();
    private static final HashMap<String, Events> eventNameMapping = new HashMap<>();
    private static final HashMap<String, QuestFish> questFishNameMapping = new HashMap<>();
    public enum EventInfoMapKeys {
        // pumpkin moon and frost moon
        EVENT_WAVE("tier"),
        // invasion progress
        INVADE_PROGRESS("invadeProgress"),
        INVADE_PROGRESS_MAX("invadeProgressMax"),
        INVADE_PROGRESS_FROM_FORMER_TIER("invadeProgressFromFormerTier"),
        // 1: true, 0: false
        IS_INVASION("isInvasion"),
        ;

        // fields
        final String keyName;
        // constructors
        EventInfoMapKeys(String keyName) {
            this.keyName = keyName;
            // test for collision
            if (infoKeyMapping.containsKey(keyName)) {
                TerrariaHelper.getInstance().getLogger().log(
                        Level.SEVERE, "Event Info Map Key Collision: " + keyName +
                                " between " + infoKeyMapping.get(keyName) + " and " + this);
            }
            infoKeyMapping.put(keyName, this);
        }

        @Override
        public String toString() {
            return keyName;
        }
    }
    public enum Events {
        BLOOD_MOON("血月"),
        FROST_MOON("冰霜月"),
        GOBLIN_ARMY("哥布林军团"),
        NONE(""),
        PUMPKIN_MOON("南瓜月"),
        SLIME_RAIN("史莱姆雨"),
        SOLAR_ECLIPSE("日食"),
        ;

        // fields
        final String eventName;
        // constructors
        Events(String evtName) {
            this.eventName = evtName;
            // test for collision
            if (eventNameMapping.containsKey(evtName)) {
                TerrariaHelper.getInstance().getLogger().log(
                        Level.SEVERE, "Event Info Map Key Collision: " + evtName +
                                " between " + eventNameMapping.get(evtName) + " and " + this);
            }
            eventNameMapping.put(evtName, this);
        }

        @Override
        public String toString() {
            return eventName;
        }
    }
    public enum QuestFish {
        ANGEL_FISH("天使鱼"),
        BAT_FISH("蝙蝠鱼"),
        SKELETON_FISH("骷髅鱼"),
        BUNNY_FISH("兔兔鱼"),
        CLOWN_FISH("小丑鱼"),
        MUD_FISH("泥鱼"),
        DIRT_FISH("土鱼"),
        CTHULHU_FISH("克苏鲁鱼"),
        GUIDE_VOODOO_FISH("向导巫毒鱼"),
        MUTANT_FLIN_FIN("突变弗林鱼"),
        ;

        // fields
        final String fishName;
        // constructors
        QuestFish(String fishName) {
            this.fishName = fishName;
            // test for collision
            if (questFishNameMapping.containsKey(fishName)) {
                TerrariaHelper.getInstance().getLogger().log(
                        Level.SEVERE, "Event Info Map Key Collision: " + fishName +
                                " between " + questFishNameMapping.get(fishName) + " and " + this);
            }
            questFishNameMapping.put(fishName, this);
        }

        @Override
        public String toString() {
            return fishName;
        }
        public static QuestFish randomFish() {
            return values()[(int) (Math.random() * values().length) ];
        }
    }
    // time, starts at 8:15 AM when server starts.
    public static final int TIME_CHANGE_PER_ITERATION = 2;
    public static long currentTime = 2250;
    // event
    public static Events currentEvent = Events.NONE, reservedEvent = Events.NONE;
    public static int reservedEventCountdown = 0, NPCRespawnCountdown = 1000;
    public static boolean NPCInitialized = false;
    public static HashMap<EventInfoMapKeys, Double> eventInfo;
    public static BossBattleServer eventProgressBar = null;
    public static int[] eventBossAmount = {0, 0, 0}, eventBossAmountLimit = {0, 0, 0};
    // quest fish
    public static QuestFish questFish = QuestFish.randomFish();
    public static HashSet<String> questFishSubmitted = new HashSet<>();
    // celestial pillars
    public static HashMap<CelestialPillar.PillarTypes, CelestialPillar> pillars = new HashMap<>(8);
    // fallen stars
    static final HashMap<String, Double> attrMapFallenStar;
    static {
        attrMapFallenStar = new HashMap<>();
        attrMapFallenStar.put("damage", 2500d);
        attrMapFallenStar.put("knockback", 2d);
    }
    public static ArrayList<Entity> fallenStars = new ArrayList<>();




    /*
     *
     *    FUNCTIONS BELOW
     *
     */
    public static void threadTimeAndEvent() {
        // every 3 ticks (~1/7 second)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            World[] worldsToHandle = {
                    Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE),
                    Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_CAVERN),
                    Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD) };
            World surfaceWorld = worldsToHandle[0];
            // time mechanic
            if (surfaceWorld != null)
                currentTime = surfaceWorld.getTime();
            currentTime += TIME_CHANGE_PER_ITERATION;
            for (World currWorld : worldsToHandle)
                if (currWorld != null) {
                    currWorld.setTime(currentTime);
                    WorldHelper.worldRandomTick(currWorld);
                }
            // end events when appropriate
            handleEventTermination();
            // tick event
            tickEvent();
            // other surface world mechanism
            if (surfaceWorld != null) {
                // spawn NPCs
                if (!NPCInitialized) {
                    for (NPCHelper.NPCType npcType : NPCHelper.NPCType.values()) {
                        try {
                            NPCHelper.spawnNPC(npcType);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    NPCInitialized = true;
                }
                // fallen stars
                tickFallenStars(surfaceWorld);
                // NPC respawn
                if (WorldHelper.isDayTime(surfaceWorld)) {
                    if (--NPCRespawnCountdown < 0) {
                        for (NPCHelper.NPCType npcType : NPCHelper.NPCType.values()) {
                            // if NPC is alive, move on
                            if (NPCHelper.NPCMap.containsKey(npcType) && !NPCHelper.NPCMap.get(npcType).isDead())
                                continue;
                            // otherwise, revive the NPC
                            try {
                                NPCHelper.spawnNPC(npcType);
                                Bukkit.broadcastMessage("§#327dff" + npcType + " 已到达！");
                                NPCRespawnCountdown = (int) (1000 + Math.random() * 1000);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            // only revive one NPC at a time
                            break;
                        }
                    }
                }
                // NPC respawn counter resets at night
                else {
                    NPCRespawnCountdown = 1800;
                }
            }
        }, 0, 3);
    }
    public static void destroyAllFallenStars() {
        if (fallenStars.size() > 0) {
            for (Entity entity : fallenStars) {
                entity.removeScoreboardTag("isFallenStar");
                entity.remove();
            }
            fallenStars.clear();
        }
    }
    protected static void tickFallenStars(World surfaceWorld) {
        // clear stars at day
        if (WorldHelper.isDayTime( surfaceWorld ) ) {
            destroyAllFallenStars();
        }
        // randomly spawn star at night
        else {
            EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(null,
                    null, null,
                    attrMapFallenStar, EntityHelper.DamageType.ARROW, "坠星");
            for (Chunk chunk : surfaceWorld.getLoadedChunks()) {
                if (Math.random() < 0.0001) {
                    double spawnX = (chunk.getX() << 4) + Math.random() * 16;
                    double spawnZ = (chunk.getZ() << 4) + Math.random() * 16;
                    Location spawnLoc = new Location(surfaceWorld, spawnX, 200 + Math.random() * 150, spawnZ);
                    Vector velocity = new Vector(Math.random() - 0.5, -3, Math.random() - 0.5);
                    shootInfo.shootLoc = spawnLoc;
                    shootInfo.velocity = velocity;
                    Projectile spawnedFallenStar = EntityHelper.spawnProjectile(shootInfo);
                    spawnedFallenStar.addScoreboardTag("isFallenStar");
                    spawnedFallenStar.addScoreboardTag("ignoreCanDamageCheck");
                    fallenStars.add(spawnedFallenStar);
                }
            }
        }
    }
    protected static void handleEventTermination() {
        // for events that are specific to daytime or nighttime
        switch (currentEvent) {
            // the ones that disappear at day
            case BLOOD_MOON:
            case FROST_MOON:
            case PUMPKIN_MOON: {
                if (WorldHelper.isDayTime(currentTime))
                    endEvent();
                break;
            }
            // the ones that disappear at night
            case SOLAR_ECLIPSE:
            case SLIME_RAIN: {
                if (!WorldHelper.isDayTime(currentTime))
                    endEvent();
                break;
            }
        }
        // invasion events "wave" handling(frost moon etc.) and termination
        switch (currentEvent) {
            case GOBLIN_ARMY: {
                if (eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS) >=
                        eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS_MAX)) {
                    endEvent();
                }
                break;
            }
            case FROST_MOON:
            case PUMPKIN_MOON: {
                if (eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS) >=
                        eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS_MAX)) {
                    eventNextWave();
                }
                break;
            }
        }
    }
    protected static void tickEvent() {
        // update progress bar
        if (eventProgressBar != null) {
            double progressCurr = eventInfo.getOrDefault(EventInfoMapKeys.INVADE_PROGRESS, 0d);
            double progressMax = eventInfo.getOrDefault(EventInfoMapKeys.INVADE_PROGRESS_MAX, 1d);
            double progressRatio;
            if (progressMax > 999999 || progressMax < 1e-5)
                progressRatio = 1d;
            else {
                progressRatio = progressCurr / progressMax;
                progressRatio = Math.min(progressRatio, 1d);
                progressRatio = Math.max(progressRatio, 0d);
            }
            eventProgressBar.setProgress((float) progressRatio);
        }
        // tick event and new event if none exists
        switch (currentEvent) {
            // no event is currently in place.
            case NONE: {
                // initialize some events at day/night
                switch ((int) (currentTime % 24000)) {
                    case 13500:
                        initNightEvent();
                        break;
                    case 22500:
                        initDayEvent();
                        break;
                }
                // handle reserved events
                prepareReservedEvent();
                break;
            }
            // frost/pumpkin moon, spawn boss
            case FROST_MOON:
            case PUMPKIN_MOON: {
                handleWaveEventBossSpawn();
                break;
            }
            // slime king for slime rain
            case SLIME_RAIN: {
                double progress = eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS);
                double progressMax = eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS_MAX);
                // spawn king slime
                if (! BossHelper.bossMap.containsKey(BossHelper.BossType.KING_SLIME.msgName) &&
                        progress >= progressMax) {
                    ArrayList<Player> availablePlayers = new ArrayList<>();
                    for (Player ply : Bukkit.getOnlinePlayers()) {
                        if (PlayerHelper.isProperlyPlaying(ply) &&
                                ply.getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE))
                            availablePlayers.add(ply);
                    }
                    if (availablePlayers.size() > 0)
                        BossHelper.spawnBoss(availablePlayers.get((int) (Math.random() * availablePlayers.size())),
                                BossHelper.BossType.KING_SLIME);
                }
                break;
            }
        }
    }

    protected static void prepareReservedEvent() {
        if (reservedEvent == Events.NONE)
            return;
        reservedEventCountdown -= TIME_CHANGE_PER_ITERATION;
        // if the event should happen
        if (reservedEventCountdown <= 0) {
            currentEvent = reservedEvent;
            reservedEvent = Events.NONE;
            // status message
            String statusMsg = null;
            switch (currentEvent) {
                case SLIME_RAIN:
                    statusMsg = "§d§l史莱姆正在从天而降！";
                    break;
                case GOBLIN_ARMY:
                    statusMsg = "§d§l哥布林军团来了！";
                    break;
            }
            if (statusMsg != null)
                Bukkit.broadcastMessage(statusMsg);
            // make progress bar visible after the event starts properly.
            if (eventProgressBar != null)
                eventProgressBar.setVisible(true);
        }
    }
    protected static void initDayEvent() {
        // reset fishing
        questFish = QuestFish.randomFish();
        questFishSubmitted = new HashSet<>();
        // slime rain
        if (Math.random() < 0.05) {
            initializeEvent(Events.SLIME_RAIN);
        }
        // goblin army
        {
            double goblinArmyChance = 0.035;
            // if there is one player that has defeated eye of cthulhu but not goblin army, boost chance to 1/3.
            for (Player ply : Bukkit.getOnlinePlayers()) {
                if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.EYE_OF_CTHULHU.msgName) &&
                        !PlayerHelper.hasDefeated(ply, Events.GOBLIN_ARMY.eventName)) {
                    goblinArmyChance = 0.33;
                    break;
                }
            }
            if (Math.random() < goblinArmyChance) {
                initializeEvent(Events.GOBLIN_ARMY);
            }
        }
        // solar eclipse
        if (Math.random() < 0.05) {
            initializeEvent(Events.SOLAR_ECLIPSE);
        }
    }
    protected static void initNightEvent() {
        // blood moon
        if (Math.random() < 0.05) {
            initializeEvent(Events.BLOOD_MOON);
        }
    }
    // for pumpkin moon and frost moon only
    protected static void eventNextWave() {
        // save next tier
        int currTier = eventInfo.get(EventInfoMapKeys.EVENT_WAVE).intValue();
        currTier ++;
        eventInfo.put(EventInfoMapKeys.EVENT_WAVE, (double) currTier);
        // info message for next wave enemies
        String msg = TerrariaHelper.settingConfig.getString("events." + currentEvent + ".messages." + currTier);
        Bukkit.broadcastMessage(msg);
        // save the current tier progress into the total and subtract it from invade progress
        double currentProgress = eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS);
        double currentProgressMax = eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS_MAX);
        double lastFormerProgress = eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS_FROM_FORMER_TIER);
        eventInfo.put(EventInfoMapKeys.INVADE_PROGRESS_FROM_FORMER_TIER, currentProgressMax + lastFormerProgress);
        eventInfo.put(EventInfoMapKeys.INVADE_PROGRESS, currentProgress - currentProgressMax);
        // get progress amount for next tier
        double nextTierProgressMax = TerrariaHelper.settingConfig.getInt(
                "events." + currentEvent + ".progress." + currTier, 99999999);
        eventInfo.put(EventInfoMapKeys.INVADE_PROGRESS_MAX, nextTierProgressMax);
        // boss limits
        List<Integer> bossAmountLimitList = TerrariaHelper.settingConfig.getIntegerList(
                "events." + currentEvent + ".bossMax." + currTier);
        eventBossAmountLimit = new int[] {
                bossAmountLimitList.get(0),
                bossAmountLimitList.get(1),
                bossAmountLimitList.get(2)};
    }
    protected static void handleWaveEventBossSpawn() {
        switch (currentEvent) {
            case FROST_MOON:
            case PUMPKIN_MOON: {
                if (currentTime % 60 != 0)
                    return;
                // determine the boss that can still be spawned
                ArrayList<Integer> bossIndexCandidate = new ArrayList<>(4);
                for (int i = 0; i < 3; i ++) {
                    if (eventBossAmount[i] < eventBossAmountLimit[i])
                        bossIndexCandidate.add(i);
                }
                if (bossIndexCandidate.isEmpty())
                    return;
                // get a random boss to spawn
                int bossSpawnIndex = bossIndexCandidate.get( (int) (Math.random() * bossIndexCandidate.size()) );
                // get a random player as target
                Player targetPly = null;
                {
                    ArrayList<Player> candidateTargets = new ArrayList<>();
                    for (Player ply : Bukkit.getOnlinePlayers()) {
                        if (! PlayerHelper.isProperlyPlaying(ply))
                            continue;
                        if (! ply.getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE))
                            continue;
                        if (! PlayerHelper.hasDefeated(ply, BossHelper.BossType.PLANTERA.msgName))
                            continue;
                        switch (WorldHelper.HeightLayer.getHeightLayer( ply.getLocation() ) ) {
                            case SURFACE:
                            case SPACE:
                                candidateTargets.add(ply);
                        }
                    }
                    // no available player, cancel spawning attempt
                    if (candidateTargets.isEmpty())
                        return;
                    targetPly = candidateTargets.get( (int) (Math.random() * candidateTargets.size()) );
                }
                switch (bossSpawnIndex) {
                    // first boss
                    case 0: {
                        if (currentEvent == Events.FROST_MOON) {
                            new Everscream(targetPly);
                        }
                        else {
                            new MourningWood(targetPly);
                        }
                        break;
                    }
                    // second boss
                    case 1: {
                        if (currentEvent == Events.FROST_MOON) {
                            new SantaNK1(targetPly);
                        }
                        else {
                            new PumpkingHead(targetPly);
                        }
                        break;
                    }
                    // third boss
                    case 2: {
                        if (currentEvent == Events.FROST_MOON) {
                            new IceQueen(targetPly);
                        }
                        else {
                            new HeadlessHorseman(targetPly);
                        }
                        break;
                    }
                }

                break;
            }
        }
    }
    public static double getWaveEventBossDropRate() {
        switch (currentEvent) {
            case FROST_MOON:
            case PUMPKIN_MOON:
                double wave = eventInfo.get(EventInfoMapKeys.EVENT_WAVE);
                return 1 / Math.max( (20 - wave) / 2, 1);
        }
        return 1d;
    }
    // initialize and terminate event
    public static boolean initializeEvent(Events newEvent) {
        if (currentEvent != Events.NONE || reservedEvent != Events.NONE)
            return false;
        // event info
        eventInfo = new HashMap<>(15);
        switch (newEvent) {
            case GOBLIN_ARMY: {
                int playerAmount = Bukkit.getOnlinePlayers().size();
                eventInfo.put(EventInfoMapKeys.INVADE_PROGRESS, 0d);
                eventInfo.put(EventInfoMapKeys.INVADE_PROGRESS_MAX, (60d + playerAmount * 20) );
                eventInfo.put(EventInfoMapKeys.IS_INVASION, 1d);
                break;
            }
            case SLIME_RAIN: {
                eventInfo.put(EventInfoMapKeys.INVADE_PROGRESS, 0d);
                eventInfo.put(EventInfoMapKeys.INVADE_PROGRESS_MAX, 150d);
                eventInfo.put(EventInfoMapKeys.IS_INVASION, 1d);
                break;
            }
            case FROST_MOON:
            case PUMPKIN_MOON: {
                eventBossAmount = new int[] {0, 0, 0};
                eventBossAmountLimit = new int[] {0, 0, 0};
                eventInfo.put(EventInfoMapKeys.INVADE_PROGRESS, 1e-5);
                eventInfo.put(EventInfoMapKeys.INVADE_PROGRESS_MAX, 0d);
                eventInfo.put(EventInfoMapKeys.INVADE_PROGRESS_FROM_FORMER_TIER, 0d);
                eventInfo.put(EventInfoMapKeys.IS_INVASION, 1d);
                eventInfo.put(EventInfoMapKeys.EVENT_WAVE, 0d);
                break;
            }
        }
        // status message
        String msg = null;
        switch (newEvent) {
            case GOBLIN_ARMY: {
                msg = "§d§l一支哥布林军团正在逼近...";
                break;
            }
            case SOLAR_ECLIPSE: {
                msg = "§a§l正在发生日食！";
                break;
            }
            case BLOOD_MOON: {
                msg = "§d§l血月正在升起...";
                break;
            }
            case FROST_MOON:
            case PUMPKIN_MOON: {
                msg = "§a§l" + newEvent + "正在升起！";
                break;
            }
        }
        if (msg != null)
            Bukkit.broadcastMessage(msg);
        // set event or reserve event
        switch (newEvent) {
            case GOBLIN_ARMY:
                // between 4:30 and 12:00
                reservedEventCountdown = (int) (Math.random() * 7500);
                reservedEvent = newEvent;
                break;
            case SLIME_RAIN:
                // between 7:00 and 9:00
                reservedEventCountdown = 2500 + (int) (Math.random() * 2000);
                reservedEvent = newEvent;
                break;
            default:
                currentEvent = newEvent;
        }
        // event progress bar if applicable
        if (eventInfo.containsKey(EventInfoMapKeys.INVADE_PROGRESS)) {
            eventProgressBar = new BossBattleServer(
                    CraftChatMessage.fromString(newEvent.eventName, true)[0],
                    BossBattle.BarColor.WHITE, BossBattle.BarStyle.PROGRESS);
            eventProgressBar.setProgress(0f);
            // make it instantly visible only if the event happens instantly
            eventProgressBar.setVisible(currentEvent != Events.NONE);
            // add all players to the progress bar
            for (Player ply : Bukkit.getOnlinePlayers()) {
                eventProgressBar.addPlayer( ((CraftPlayer) ply).getHandle() );
            }
        }
        // for wave-style events, use the helper function to initialize first wave
        switch (currentEvent) {
            case PUMPKIN_MOON:
            case FROST_MOON:
                eventNextWave();
        }
        return true;
    }
    public static void endEvent() {
        // save event defeated progress
        for (Player ply : Bukkit.getOnlinePlayers())
            PlayerHelper.getPlayerDataFile(ply).set("bossDefeated." + currentEvent, true);
        // status message
        String statusMsg = null;
        switch (currentEvent) {
            case GOBLIN_ARMY: {
                statusMsg = "§d§l" + currentEvent + "被击退了！";
                break;
            }
            case SLIME_RAIN: {
                statusMsg = "§a§l史莱姆不再从天而降.";
                break;
            }
            case FROST_MOON:
            case PUMPKIN_MOON: {
                int score = eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS).intValue();
                score += eventInfo.get(EventInfoMapKeys.INVADE_PROGRESS_FROM_FORMER_TIER).intValue();
                statusMsg = "§d§l" + currentEvent + "过去了！（得分：" + score + ")！";
                break;
            }
        }
        if (statusMsg != null)
            Bukkit.broadcastMessage(statusMsg);
        // update event variable
        currentEvent = Events.NONE;
        // hide boss bar
        if (eventProgressBar != null) {
            eventProgressBar.setVisible(false);
            eventProgressBar = null;
        }
    }
}
