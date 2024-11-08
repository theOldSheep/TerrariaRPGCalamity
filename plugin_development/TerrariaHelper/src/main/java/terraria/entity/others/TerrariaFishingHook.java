/*
most of the code in this class are from net.minecraft.server package
 */
package terraria.entity.others;

import net.minecraft.server.v1_12_R1.*;
import net.minecraft.server.v1_12_R1.MathHelper;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Fish;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.gameplay.EventAndTime;
import terraria.util.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class TerrariaFishingHook extends EntityFishingHook {
    // static variables and functions
    static final HashMap<String, Double> baitsAndPower;
    static {
        baitsAndPower = new HashMap<>();
        ConfigurationSection baitSection = TerrariaHelper.fishingConfig.getConfigurationSection("baitsAndPower");
        Collection<String> validBaits = baitSection.getKeys(false);
        for (String bait : validBaits) {
            baitsAndPower.put(bait, baitSection.getDouble(bait));
        }
    }
    public static org.bukkit.inventory.ItemStack getBait(Player player, org.bukkit.entity.Entity hook) {
        return PlayerHelper.getFirstItem(player,
                (item) -> {
                    String itemType = ItemHelper.splitItemName(item)[1];
                    if (! baitsAndPower.containsKey(itemType))
                        return false;
                    if (itemType.equals("松露虫") &&
                            WorldHelper.BiomeType.getBiome(hook.getLocation()) != WorldHelper.BiomeType.OCEAN)
                        return false;
                    if (itemType.equals("血蠕虫") &&
                            WorldHelper.BiomeType.getBiome(hook.getLocation()) != WorldHelper.BiomeType.SULPHUROUS_OCEAN)
                        return false;
                    return true;
                },
                true);
    }
    public static double getBaitPower(org.bukkit.inventory.ItemStack bait) {
        String itemType = ItemHelper.splitItemName(bait)[1];
        return baitsAndPower.getOrDefault(itemType, -1d);
    }
    // other variables and methods
    protected long startUseTime;
    protected int hookedTimeRemaining, waitingTimeRemaining;
    protected float fishingPower, reelingInSpeed = 1;
    protected boolean isInGround, isInLava = false, lavaProof;
    protected Player ownerPly;
    protected FishingState state;
    protected org.bukkit.entity.Item hookedItem = null;

    public TerrariaFishingHook(World world, EntityHuman entityhuman) {
        this(world, entityhuman, false, 5f);
    }
    public TerrariaFishingHook(World world, EntityHuman entityhuman, boolean lavaProof, float fishingPower) {
        super(world, entityhuman);
        // if the player is not online
        if (entityhuman.dead) {
            die();
            return;
        }

        this.startUseTime = EntityHelper.getMetadata(entityhuman.getBukkitEntity(),
                EntityHelper.MetadataName.PLAYER_INTERNAL_ITEM_START_USE_CD).asLong();

        this.hookedTimeRemaining = 0;
        this.waitingTimeRemaining = 0;

        this.fishingPower = fishingPower;

        this.isInGround = false;
        this.lavaProof = lavaProof;

        this.ownerPly = (Player) owner.getBukkitEntity();

        this.state = FishingState.FLYING;
    }

    protected org.bukkit.inventory.ItemStack getBait() {
        return getBait(ownerPly, bukkitEntity);
    }
    protected double getBaitPower() {
        return getBaitPower(getBait());
    }

    // make the hook only effective against items. Not really used.
    @Override
    protected boolean a(Entity entity) {
        return entity instanceof EntityItem;
    }
    // never burn in lava
    @Override
    public boolean au() {
        return false;
    }

    // helper functions and the ticking itself
    protected EnumParticle getSplashParticle(Block block) {
        if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
            this.isInLava = false;
            return EnumParticle.WATER_SPLASH;
        }
        if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) {
            this.isInLava = true;
            return EnumParticle.LAVA;
        }
        return null;
    }
    protected Sound getSplashSound(Block block) {
        return Sound.ENTITY_BOBBER_SPLASH;
    }
    protected void displayHookedEffects() {
        // pull the hook downwards
        this.motY -= 0.25;
        velocityChanged = true;
        // particle
        WorldServer worldserver = (WorldServer)this.world;
        Block block = worldserver.getType(new BlockPosition((int)locX, (int)this.getBoundingBox().b, (int)locZ)).getBlock();
        EnumParticle particle = getSplashParticle(block);
        if (particle != null) {
            worldserver.a(particle, locX, locY, locZ, 3 + this.random.nextInt(3), 0.25, 0.0, 0.25, 0.0, new int[0]);
        }
        Sound sound = getSplashSound(block);
        if (sound != null) {
            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), sound, org.bukkit.SoundCategory.BLOCKS,1, 1);
        }
    }
    private boolean checkValid() {
        // if the player clicked again and started reeling in
        long startUseTimeCurrent = EntityHelper.getMetadata(ownerPly,
                EntityHelper.MetadataName.PLAYER_INTERNAL_ITEM_START_USE_CD).asLong();
        if (startUseTimeCurrent != startUseTime) {
            tryCatchingFish();
            return true;
        }
        ItemStack itemstack = this.owner.getItemInMainHand();
        boolean isHoldingFishingRod = itemstack.getItem() == Items.FISHING_ROD;
        // if the owner is in the world (online & world not changed),
        // properly playing,
        // holding a fishing rod and is
        // within 64 blocks radius
        if (!this.owner.dead && PlayerHelper.isProperlyPlaying(ownerPly)
                && isHoldingFishingRod && this.h(this.owner) <= 1024.0) {
            return true;
        } else {
            this.die();
            return false;
        }
    }
    protected boolean isInProperLiquid(IBlockData iblockdata) {
        return  (iblockdata.getMaterial() == Material.WATER ||
                (iblockdata.getMaterial() == Material.LAVA && this.lavaProof));
    }
    // fishing result helpers
    protected FishingResultRarity getFishingResultRarity(double fishingPower) {
        FishingResultRarity result = FishingResultRarity.PLENTIFUL;
        if (Math.random() < Math.min(fishingPower / 4500, 1d/6))
            result = FishingResultRarity.EXTREMELY_RARE;
        else if (Math.random() < Math.min(fishingPower / 2250, 1d/5))
            result = FishingResultRarity.VERY_RARE;
        else if (Math.random() < Math.min(fishingPower / 1050, 1d/4))
            result = FishingResultRarity.RARE;
        else if (Math.random() < Math.min(fishingPower / 300, 1d/3))
            result = FishingResultRarity.UNCOMMON;
        else if (Math.random() < Math.min(fishingPower / 150, 1d/2))
            result = FishingResultRarity.COMMON;
        return result;
    }
    protected String catchCrateItem(Player ply, boolean inHardMode, String biome, FishingResultRarity rarity) {
        String result;
        switch (rarity) {
            case EXTREMELY_RARE:
            case VERY_RARE:
                result = PlayerHelper.hasDefeated(ply, "机械二王") ? "钛金匣" : "金匣";
                break;
            // try getting the biome-specific crate, returns iron/mythril crate if none exists
            case RARE: {
                result = PlayerHelper.hasDefeated(ply, "机械一王") ? "秘银匣" : "铁匣";
                result = TerrariaHelper.fishingConfig.getString("biomeCrates." + biome, result);
                break;
            }
            case UNCOMMON:
                result = PlayerHelper.hasDefeated(ply, "机械一王") ? "秘银匣" : "铁匣";
                break;
            default:
                result = inHardMode ? "珍珠木匣" : "木匣";
        }
        return result;
    }
    protected String catchFishItem(double fishingPower, String biome, String height, FishingResultRarity rarity) {
        String result = "";
        if (rarity == null) return result;
        ConfigurationSection allFishSection = TerrariaHelper.fishingConfig.getConfigurationSection("fish." + rarity);
        Collection<String> allFish = allFishSection.getKeys(false);
        // setup all valid fish types
        List<String> allFishValid = new ArrayList<>(allFish.size());
        for (String fish : allFish) {
            ConfigurationSection currFishSection = allFishSection.getConfigurationSection(fish);
            // biome exception requirement
            {
                Collection<String> biomeExceptions = currFishSection.getStringList("biomeException");
                if (biomeExceptions.size() > 0 && biomeExceptions.contains(biome))
                    continue;
            }
            // biome requirement
            {
                Collection<String> availableBiomes = currFishSection.getStringList("biome");
                if (availableBiomes.size() > 0 && !availableBiomes.contains(biome))
                    continue;
            }
            // height layer requirement
            {
                Collection<String> availableHeights = currFishSection.getStringList("height");
                if (availableHeights.size() > 0 && !availableHeights.contains(height))
                    continue;
            }
            // fishing power requirement
            {
                double fishingPowerRequired = currFishSection.getDouble("fishingPower", -1);
                if (fishingPower < fishingPowerRequired)
                    continue;
            }
            // game progress requirement
            {
                String progress = currFishSection.getString("progressRequired");
                if (progress != null && !PlayerHelper.hasDefeated(ownerPly, progress))
                    continue;
            }
            // then, the fish is valid.
            allFishValid.add(fish);
        }
        // if valid fish exists, return a random one
        if (allFishValid.size() > 0) {
            result = allFishValid.get((int) (Math.random() * allFishValid.size()));
        }
        // otherwise, go down a rarity level
        else {
            return catchFishItem(fishingPower, biome, height, rarity.getLowerRarity());
        }
        return result;
    }
    protected String getFishingResult(Player ply, double fishingPower) {
        FishingResultRarity rarity = getFishingResultRarity(fishingPower);
        boolean inHardMode = PlayerHelper.hasDefeated(ply, BossHelper.BossType.WALL_OF_FLESH);
        Location hookLoc = bukkitEntity.getLocation();
        WorldHelper.BiomeType biome = WorldHelper.BiomeType.getBiome(hookLoc, false);
        WorldHelper.HeightLayer height = WorldHelper.HeightLayer.getHeightLayer(hookLoc);
        // in lava, the biome is set to underworld if it is not in the brimstone crag.
        if (isInLava) {
            if (biome != WorldHelper.BiomeType.BRIMSTONE_CRAG)
                biome = WorldHelper.BiomeType.UNDERWORLD;
        }
        else {
            // regularize some biomes
            // those biomes will only appear in hardmode but the player is in pre-hardmode
            switch (biome) {
                case HALLOW:
                case ASTRAL_INFECTION: {
                    if (!inHardMode)
                        biome = WorldHelper.BiomeType.NORMAL;
                    break;
                }
            }
        }
        // crates
        double crateChance = EntityHelper.hasEffect(ply, "宝匣") ? 0.125 : 0.075;
        if (Math.random() < crateChance) {
            return catchCrateItem(ply, inHardMode, biome.toString(), rarity);
        }
        // quest fish
        if (TerrariaHelper.fishingConfig.getStringList("questFish." + EventAndTime.questFish).contains(biome + ":" + height) &&
                Math.random() < 0.05) {
            return EventAndTime.questFish.toString();
        }
        // normal fish/weapon etc.
        return catchFishItem(fishingPower, biome.toString(), height.toString(), rarity);
    }
    protected void tryCatchingFish() {
        if (state == FishingState.REELING_IN)
            return;
        if (this.hookedTimeRemaining > 0) {
            org.bukkit.inventory.ItemStack bait = getBait();
            if (bait != null) {
                // duke fishron
                boolean shouldConsumeBait = false;
                switch (ItemHelper.splitItemName(bait)[1]) {
                    case "松露虫":
                        if ( BossHelper.spawnBoss(ownerPly.getPlayer(), BossHelper.BossType.DUKE_FISHRON, bukkitEntity.getLocation()) )
                            shouldConsumeBait = true;
                        break;
                    case "血蠕虫":
                        if ( BossHelper.spawnBoss(ownerPly.getPlayer(), BossHelper.BossType.THE_OLD_DUKE, bukkitEntity.getLocation()) )
                            shouldConsumeBait = true;
                        break;
                    default:
                        double baitPower = getBaitPower(bait);
                        if (Math.random() < 1 / (1 + baitPower / 6))
                            shouldConsumeBait = true;
                        this.hookedItem = ItemHelper.dropItem(getBukkitEntity().getLocation(),
                                getFishingResult(ownerPly, fishingPower + baitPower), true, false);
                }
                if (shouldConsumeBait)
                    bait.setAmount(bait.getAmount() - 1);
            }
        }
        this.state = FishingState.REELING_IN;
        this.noclip = true;
    }
    // ticking
    private void basicFishingTicking() {
        WorldServer worldserver = (WorldServer)this.world;
        int waitingTimeDecrement = 1;

        // ambient particles
        {
            float particleAngle;
            float particleOffset;
            double particleX;
            double particleY;
            double particleZ;
            Block block;
            // spawn particle
            if (this.random.nextFloat() < 0.15) {
                particleAngle = MathHelper.a(this.random, 0.0F, 360.0F) * 0.017453292F;
                particleOffset = MathHelper.a(this.random, 25.0F, 60.0F);
                particleX = this.locX + (double)(MathHelper.sin(particleAngle) * particleOffset * 0.1F);
                particleY = ((float)MathHelper.floor(this.getBoundingBox().b) + 1.0F);
                particleZ = this.locZ + (double)(MathHelper.cos(particleAngle) * particleOffset * 0.1F);
                block = worldserver.getType(new BlockPosition((int)particleX, (int)particleY - 1, (int)particleZ)).getBlock();
                EnumParticle particle = getSplashParticle(block);
                if (particle != null) {
                    worldserver.a(particle, particleX, particleY, particleZ, 2 + this.random.nextInt(2), 0.10000000149011612, 0.0, 0.10000000149011612, 0.0, new int[0]);
                }
            }
        }
        // fish is being hooked
        if (this.hookedTimeRemaining > 0) {
            --this.hookedTimeRemaining;
            if (this.hookedTimeRemaining <= 0) {
                this.waitingTimeRemaining = 0;
                // fire failed to catch fish event
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player)this.owner.getBukkitEntity(), null, (Fish)this.getBukkitEntity(), PlayerFishEvent.State.FAILED_ATTEMPT);
                this.world.getServer().getPluginManager().callEvent(playerFishEvent);
                // if the fish flees, pull the bob down again etc.
                displayHookedEffects();
            }
        }
        // fish is waiting to be hooked
        else {
            if (this.waitingTimeRemaining > 0) {
                this.waitingTimeRemaining -= waitingTimeDecrement;
                // fish bites the hook
                if (this.waitingTimeRemaining <= 0) {
                    double baitPower = getBaitPower();
                    if (baitPower >= 0d) {
                        this.hookedTimeRemaining = 15;
                        displayHookedEffects();
                    }
                }
            }
            // initialize waiting time
            else {
                double baitPower = getBaitPower();
                if (baitPower < 0d) {
                    PlayerHelper.sendActionBar(ownerPly, "§7你可能需要一些鱼饵才能钓鱼。");
                    this.waitingTimeRemaining = 100;
                } else {
                    this.waitingTimeRemaining = (int) Math.ceil((1 + Math.random() * 0.3 - 0.15) *
                            300 / (1.5 + (baitPower + this.fishingPower) * 0.05));
                }
            }
        }

    }
    @Override
    public void B_() {
        // basic ticking
        this.Y();
        // fishing rod tick
        double speedOffset;
        if (this.owner == null) {
            this.die();
            return;
        }
        if (this.checkValid()) {
            // if the player is starting to reel in
            if (state != FishingState.REELING_IN && !ownerPly.getScoreboardTags().contains("temp_autoSwing"))
                tryCatchingFish();
            float fluidHeight = 0.0F;
            BlockPosition blockposition = new BlockPosition(this);
            IBlockData iblockdata = this.world.getType(blockposition);
            if (isInProperLiquid(iblockdata)) {
                fluidHeight = BlockFluids.g(iblockdata, this.world, blockposition);
            }
            // gravity
            if (!isInProperLiquid(iblockdata)) {
                this.motY -= 0.05;
            }
            // other velocity ticking
            switch (this.state) {
                case FLYING: {
                    setNoGravity(false);
                    if (fluidHeight > 0.0F) {
                        this.motX *= 0.2;
                        this.motY *= 0.15;
                        this.motZ *= 0.2;
                        this.state = FishingState.IN_WATER;
                        return;
                    }


                    // make sure the hook does not move around in blocks
                    if (this.isInGround || this.onGround) {
                        this.motX = 0.0;
                        this.motY = 0.0;
                        this.motZ = 0.0;
                    }
                    break;
                }
                case IN_WATER: {
                    setNoGravity(true);
                    this.motX *= 0.75;
                    this.motZ *= 0.75;
                    // fluid Y (target y) - the hook y in next tick
//                    Bukkit.broadcastMessage("Fld ht: " + fluidHeight + ">>>" + ((double)blockposition.getY() + (double)fluidHeight));
                    speedOffset = ((double)blockposition.getY() + (double)fluidHeight) - (this.locY + this.motY);
                    // accelerate the hook
                    this.motY += speedOffset * 0.5;
                    // if in water, handle fishing CD etc.
                    if (fluidHeight > 0.0F) {
                        this.basicFishingTicking();
                    }
                    break;
                }
                case REELING_IN: {
                    setNoGravity(true);
                    Vector vel = ownerPly.getEyeLocation().subtract(getBukkitEntity().getLocation()).toVector();
                    if (vel.lengthSquared() < reelingInSpeed * reelingInSpeed) {
                        die();
                        if (hookedItem != null) {
                            hookedItem.setPickupDelay(0);
                        }
                        return;
                    } else {
                        double distance = vel.length();
                        vel.multiply(Math.max(distance / 8, reelingInSpeed)  / distance);
                        if (hookedItem != null) {
                            hookedItem.teleport(getBukkitEntity());
                            hookedItem.setPickupDelay(10);
                            hookedItem.setVelocity(vel);
                        }
                        motX = vel.getX();
                        motY = vel.getY();
                        motZ = vel.getZ();
                        reelingInSpeed += 0.05;
                    }
                    break;
                }
            }

            this.move(EnumMoveType.SELF, this.motX, this.motY, this.motZ);
            this.regularizeDirection();
            speedOffset = 0.92;
            this.motX *= speedOffset;
            this.motY *= speedOffset;
            this.motZ *= speedOffset;
            this.setPosition(this.locX, this.locY, this.locZ);

            // try to prevent client glitch
            this.positionChanged = true;
            this.velocityChanged = true;
            this.impulse = true;
//            PlayerHelper.sendActionBar(ownerPly, this.waitingTimeRemaining + ", " + this.hookedTimeRemaining);
//            Bukkit.broadcastMessage(this.waitingTimeRemaining + ", " + this.hookedTimeRemaining);
//            Bukkit.broadcastMessage(this.motX + ", " + this.motY + ", " + this.motZ);
//            Bukkit.broadcastMessage(this.locX + ", " + this.locY + ", " + this.locZ);
//            Bukkit.broadcastMessage(this.state.toString());
        }
    }
    private void regularizeDirection() {
        float horSpd = MathHelper.sqrt(this.motX * this.motX + this.motZ * this.motZ);
        this.yaw = (float)(MathHelper.c(this.motX, this.motZ) * 57.2957763671875);

        for(this.pitch = (float)(MathHelper.c(this.motY, horSpd) * 57.2957763671875); this.pitch - this.lastPitch < -180.0F; this.lastPitch -= 360.0F) {
        }

        while(this.pitch - this.lastPitch >= 180.0F) {
            this.lastPitch += 360.0F;
        }

        while(this.yaw - this.lastYaw < -180.0F) {
            this.lastYaw -= 360.0F;
        }

        while(this.yaw - this.lastYaw >= 180.0F) {
            this.lastYaw += 360.0F;
        }

        this.pitch = this.lastPitch + (this.pitch - this.lastPitch) * 0.2F;
        this.yaw = this.lastYaw + (this.yaw - this.lastYaw) * 0.2F;
    }
    public enum FishingState {
        FLYING, IN_WATER, REELING_IN;
    }
    public enum FishingResultRarity {
        PLENTIFUL, COMMON, UNCOMMON, RARE, VERY_RARE, EXTREMELY_RARE;
        public FishingResultRarity getLowerRarity() {
            return getLowerRarity(this);
        }
        public static FishingResultRarity getLowerRarity(FishingResultRarity rarity) {
            switch (rarity) {
                case EXTREMELY_RARE:
                    return VERY_RARE;
                case VERY_RARE:
                    return RARE;
                case RARE:
                    return UNCOMMON;
                case UNCOMMON:
                    return COMMON;
                case COMMON:
                    return PLENTIFUL;
                default:
                    return null;
            }
        }
    }
}
