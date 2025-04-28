package terraria.util;

import eos.moe.dragoncore.api.CoreAPI;
import net.minecraft.server.v1_12_R1.*;
import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.TriConsumer;
import org.bukkit.*;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.HitEntityInfo;
import terraria.gameplay.Setting;

import java.util.*;
import java.util.function.Predicate;

public class GenericHelper {
    static long nextWorldTextureIndex = 0;
    // 80 * 80 = 6400
    static final double PARTICLE_DISPLAY_RADIUS_SQR = 80 * 80;
    public static final double GLOBAL_PARTICLE_DENSITY = TerrariaHelper.optimizationConfig.getDouble("optimization.globalParticleDensity", 1d);
    public static class ParticleLineOptions {
        boolean particleOrItem;
        boolean vanillaParticle;
        ItemStack spriteItem;
        Vector rightOrthogonalDir;
        double length, width, stepsize, intensityMulti;
        float alpha;
        int ticksLinger;
        // Not using vanilla - if char is single char, it is the char rendered; otherwise it is snowstorm particle name.
        String particleChar;
        ArrayList<String> particleColor;
        ArrayList<Color> particleColorObjects;
        public ParticleLineOptions() {
            particleOrItem = true;
            vanillaParticle = true;
            spriteItem = null;
            rightOrthogonalDir = null;
            length = 1;
            width = 0.25;
            stepsize = width;
            intensityMulti = 1;
            ticksLinger = 5;
            particleChar = "█";
            particleColor = new ArrayList<>();
            setParticleColor("255|255|255");
            alpha = 0.5f;
        }
        public ParticleLineOptions clone() {
            ParticleLineOptions result = new ParticleLineOptions();
            result.setParticleOrItem(particleOrItem)
                    .setVanillaParticle(vanillaParticle)
                    .setSpriteItem(spriteItem.clone())
                    .setRightOrthogonalDir(rightOrthogonalDir.clone())
                    .setLength(length)
                    .setWidth(width, false)
                    .setStepsize(stepsize)
                    .setIntensityMulti(intensityMulti)
                    .setTicksLinger(ticksLinger)
                    .setAlpha(alpha)
                    .setParticleChar(particleChar)
                    .setParticleColor((ArrayList<String>) particleColor.clone());
            return result;
        }
        public ParticleLineOptions setParticleOrItem(boolean particleOrItem) {
            this.particleOrItem = particleOrItem;
            return this;
        }
        public ParticleLineOptions setVanillaParticle(boolean vanillaParticle) {
            this.vanillaParticle = vanillaParticle;
            return this;
        }
        public ParticleLineOptions setSpriteItem(ItemStack spriteItem) {
            this.spriteItem = spriteItem;
            return this;
        }
        public ParticleLineOptions setRightOrthogonalDir(Vector rightOrthogonalDir) {
            if (rightOrthogonalDir == null) {
                this.rightOrthogonalDir = null;
                return this;
            }
            if (rightOrthogonalDir.lengthSquared() < 1e-9)
                return this;
            this.rightOrthogonalDir = rightOrthogonalDir;
            this.rightOrthogonalDir.normalize();
            return this;
        }
        public ParticleLineOptions setLength(double length) {
            this.length = length;
            return this;
        }
        public ParticleLineOptions setWidth(double width) {
            return setWidth(width, true);
        }
        public ParticleLineOptions setWidth(double width, boolean changeStepSize) {
            this.width = width;
            if (changeStepSize) this.stepsize = width;
            return this;
        }
        public ParticleLineOptions setStepsize(double stepsize) {
            this.stepsize = stepsize;
            return this;
        }
        public ParticleLineOptions setIntensityMulti(double intensityMulti) {
            this.intensityMulti = intensityMulti;
            return this;
        }
        public ParticleLineOptions setTicksLinger(int ticksLinger) {
            this.ticksLinger = ticksLinger;
            return this;
        }
        public ParticleLineOptions setAlpha(float alpha) {
            this.alpha = alpha;
            return this;
        }
        public ParticleLineOptions setParticleChar(String particleChar) {
            this.particleChar = particleChar;
            return this;
        }
        public ParticleLineOptions setParticleColor(String... particleColor) {
            this.particleColor.clear();
            this.particleColor.addAll(Arrays.asList(particleColor));
            this.particleColorObjects = getColorListFromStrings(this.particleColor);
            return this;
        }
        public ParticleLineOptions setParticleColor(List<String> particleColor) {
            this.particleColor.clear();
            this.particleColor.addAll(particleColor);
            this.particleColorObjects = getColorListFromStrings(this.particleColor);
            return this;
        }
        public ArrayList<Color> getParticleColorObjects() {
            return particleColorObjects;
        }
    }
    public static class StrikeLineOptions {
        boolean displayParticle, bounceWhenHitBlock, thruWall, vanillaParticle;
        int damageCD, lingerTime, lingerDelay, maxTargetHit;
        double damage, decayCoef, particleIntensityMulti, whipBonusCrit, whipBonusDamage;
        ParticleLineOptions particleInfo;
        BiConsumer<Location, MovingObjectPosition> blockHitFunction;
        TriConsumer<Integer, Entity, Location> damagedFunction;
        Predicate<Entity> shouldDamageFunction;
        // internal variables
        int amountEntitiesHit;
        public StrikeLineOptions() {
            displayParticle = true;
            bounceWhenHitBlock = false;
            thruWall = true;
            vanillaParticle = true;

            damageCD = 10;
            lingerTime = 1;
            lingerDelay = 6;
            maxTargetHit = 999999;

            damage = 0d;
            decayCoef = 1d;
            particleIntensityMulti = 1d;
            whipBonusCrit = 0d;
            whipBonusDamage = 0d;

            particleInfo = null;

            blockHitFunction = null;
            damagedFunction = null;
            shouldDamageFunction = null;
            // internal values
            amountEntitiesHit = 0;
        }
        public StrikeLineOptions clone() {
            StrikeLineOptions result = new StrikeLineOptions();
            result
                    // booleans
                    .setDisplayParticle(displayParticle)
                    .setBounceWhenHitBlock(bounceWhenHitBlock)
                    .setThruWall(thruWall)
                    .setVanillaParticle(vanillaParticle)
                    // ints
                    .setDamageCD(damageCD)
                    .setLingerTime(lingerTime)
                    .setLingerDelay(lingerDelay)
                    .setMaxTargetHit(maxTargetHit)
                    // doubles
                    .setDamage(damage)
                    .setDecayCoef(decayCoef)
                    .setParticleIntensityMulti(particleIntensityMulti)
                    .setWhipBonusCrit(whipBonusCrit)
                    .setWhipBonusDamage(whipBonusDamage)
                    // objects
                    .setParticleInfo(particleInfo.clone())
                    .setBlockHitFunction(blockHitFunction)
                    .setDamagedFunction(damagedFunction)
                    .setShouldDamageFunction(shouldDamageFunction);
            return result;
        }
        // boolean setters
        public StrikeLineOptions setDisplayParticle(boolean displayParticle) {
            this.displayParticle = displayParticle;
            return this;
        }
        public StrikeLineOptions setBounceWhenHitBlock(boolean shouldBounce) {
            this.bounceWhenHitBlock = shouldBounce;
            if (shouldBounce) this.thruWall = false;
            return this;
        }
        public StrikeLineOptions setThruWall(boolean thruWall) {
            this.thruWall = thruWall;
            return this;
        }
        public StrikeLineOptions setVanillaParticle(boolean vanillaParticle) {
            this.vanillaParticle = vanillaParticle;
            return this;
        }
        // int setters
        public StrikeLineOptions setDamageCD(int damageCD) {
            this.damageCD = damageCD;
            return this;
        }
        public StrikeLineOptions setLingerTime(int lingerTime) {
            this.lingerTime = lingerTime;
            return this;
        }
        public StrikeLineOptions setLingerDelay(int lingerDelay) {
            this.lingerDelay = lingerDelay;
            return this;
        }
        public StrikeLineOptions setMaxTargetHit(int maxTargetHit) {
            this.maxTargetHit = maxTargetHit;
            return this;
        }
        // double setters
        public StrikeLineOptions setDamage(double damage) {
            this.damage = damage;
            return this;
        }
        public StrikeLineOptions setDecayCoef(double decayCoef) {
            this.decayCoef = decayCoef;
            return this;
        }
        public StrikeLineOptions setParticleIntensityMulti(double particleIntensityMulti) {
            this.particleIntensityMulti = particleIntensityMulti;
            return this;
        }
        public StrikeLineOptions setWhipBonusCrit(double whipBonusCrit) {
            this.whipBonusCrit = whipBonusCrit;
            return this;
        }
        public StrikeLineOptions setWhipBonusDamage(double whipBonusDamage) {
            this.whipBonusDamage = whipBonusDamage;
            return this;
        }

        // object setters
        public StrikeLineOptions setParticleInfo(ParticleLineOptions particleInfo) {
            this.particleInfo = particleInfo;
            return this;
        }

        public StrikeLineOptions setBlockHitFunction(BiConsumer<Location, MovingObjectPosition> blockHitFunction) {
            this.blockHitFunction = blockHitFunction;
            return this;
        }
        public StrikeLineOptions setDamagedFunction(TriConsumer<Integer, Entity, Location> damagedFunction) {
            this.damagedFunction = damagedFunction;
            return this;
        }
        public StrikeLineOptions setShouldDamageFunction(Predicate<Entity> shouldDamageFunction) {
            this.shouldDamageFunction = shouldDamageFunction;
            return this;
        }
    }
    public static String[] defaultMoneySuffixes = {" §r■铂 ", " §e■金 ", " §7■银 ", " §c■铜 "};

    public static String trimText(String textToTrim) {
        if (textToTrim == null) return "";
        try {
            StringBuilder result = new StringBuilder();
            int isColor = -1;
            for (char c : textToTrim.toCharArray()) {
                if (c == '§') isColor = 1;
                else if (isColor == 0 && c == '#') isColor = 6;
                else if (isColor < 0) result.append(c);
                isColor --;
            }
            return result.toString();
        } catch (Exception e) {
            return textToTrim;
        }
    }
    public static <T> void damageCoolDown(Collection<T> list, T victim, int cd) {
        if (!list.contains(victim)) {
            list.add(victim);
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> list.remove(victim), cd);
        }
    }
    public static long[] coinConversion(long amount, boolean copperOrRaw) {
        long amountCopper;
        if (copperOrRaw) amountCopper = amount;
        else amountCopper = amount / 100;
        return coinConversion(amountCopper);
    }
    public static long[] coinConversion(long copperAmount) {
        long copper = copperAmount;
        long[] result = new long[]{0, 0, 0, 0};
        if (copper >= 1000000L) {
            result[0] = copper / 1000000;
            copper = copper % 1000000;
        }
        if (copper >= 10000) {
            result[1] = copper / 10000;
            copper = copper % 10000;
        }
        if (copper >= 100) {
            result[2] = copper / 100;
            copper = copper % 100;
        }
        result[3] = copper;
        return result;
    }
    public static String getCoinDisplay(long[] coins) {
        return getCoinDisplay(coins, defaultMoneySuffixes);
    }
    public static String getCoinDisplay(long[] coins, String[] formatStr) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < 4; index++) {
            if (coins[index] > 0) {
                result.append(formatStr[index].replace(
                        "■", coins[index] + ""));
            }
        }
        return result.toString();
    }
    public static void dropMoney(Location loc, long amount, boolean copperOrRaw) {
        long amountCopper;
        if (copperOrRaw) amountCopper = amount;
        else amountCopper = amount / 100;
        dropMoney(loc, amountCopper);
    }
    public static void dropMoney(Location loc, long amount) {
        long[] stackSize = coinConversion(amount);
        if (stackSize[0] > 0)
            ItemHelper.dropItem(loc, "铂金币:" + stackSize[0],
                    false, false, false);
        if (stackSize[1] > 0)
            ItemHelper.dropItem(loc, "金币:" + stackSize[1],
                    false, false, false);
        if (stackSize[2] > 0)
            ItemHelper.dropItem(loc, "银币:" + stackSize[2],
                    false, false, false);
        if (stackSize[3] > 0)
            ItemHelper.dropItem(loc, "铜币:" + stackSize[3],
                    false, false, false);
    }
    public static double getHorizontalDistance(Location locationA, Location locationB) {
        double distX = Math.abs(locationA.getX() - locationB.getX());
        double distZ = Math.abs(locationA.getZ() - locationB.getZ());
        return Math.max(distX, distZ);
    }
    public static void handleParticleLine(Vector vector, Location startLoc, ParticleLineOptions options) {
        if (options.particleOrItem)
            handleParticleLine_particle(vector, startLoc, options);
        else
            handleParticleLine_item(vector, startLoc, options);
    }
    public static void handleParticleLine_item(Vector vector, Location startLoc, ParticleLineOptions options) {
        displayHoloItem(startLoc, options.spriteItem, options.ticksLinger, (float) options.length, vector, options.rightOrthogonalDir);
    }
    // color helper functions
    public static String getStringFromColor(Color color) {
        return color.getRed() + "|" + color.getGreen() + "|" + color.getBlue();
    }
    // Parse color from R|G|B
    public static Color getColorFromString(String colorString) {
        String[] info = colorString.split("\\|");
        return Color.fromRGB(Integer.parseInt(info[0]), Integer.parseInt(info[1]), Integer.parseInt(info[2]));
    }
    // Parse color list from a list of strings, used in particle display
    public static ArrayList<Color> getColorListFromStrings(List<String> colorStrings) {
        ArrayList<Color> colors = new ArrayList<>();
        if (colorStrings.get(0).equals("RAINBOW"))
            return colors;
        for (String currColor : colorStrings) {
            colors.add( getColorFromString(currColor) );
        }
        return colors;
    }
    // Get the interpolation color of color pivots
    public static Color getInterpolateColor(double progress, List<Color> allColors) {
        double colorProgress = progress * allColors.size();
        int colorIndex = (int) colorProgress;
        Color c1 = allColors.get(colorIndex);
        int rInt, gInt, bInt;
        if (allColors.size() > 1) {
            Color c2 = allColors.get((colorIndex + 1) % allColors.size());
            double multi2 = colorProgress % 1;
            double multi1 = 1 - multi2;
            rInt = (int) ((c1.getRed() * multi1) + (c2.getRed() * multi2));
            gInt = (int) ((c1.getGreen() * multi1) + (c2.getGreen() * multi2));
            bInt = (int) ((c1.getBlue() * multi1) + (c2.getBlue() * multi2));
        } else {
            rInt = c1.getRed();
            gInt = c1.getGreen();
            bInt = c1.getBlue();
        }
        return Color.fromRGB(rInt, gInt, bInt);
    }
    // Rainbow colors
    public static Color getRainbowColor(long singleColorDuration) {
        long seed = Calendar.getInstance().getTimeInMillis();
        long internal = seed % singleColorDuration, phase = ((seed - internal) / singleColorDuration) % 3;
        int interpolate = (int) (255 * internal / singleColorDuration), interpolateOther = 255 - interpolate;
        int r = 0, g = 0, b = 0;
        switch ((int) phase) {
            case 0:
                r = interpolateOther;
                g = interpolate;
                break;
            case 1:
                g = interpolateOther;
                b = interpolate;
                break;
            case 2:
                b = interpolateOther;
                r = interpolate;
                break;
        }
        return Color.fromRGB(r, g, b);
    }
    // Display particle line
    public static void handleParticleLine_particle(Vector vector, Location startLoc, ParticleLineOptions options) {
        String particleCharacter = options.particleChar;
        if (particleCharacter.length() > 1 && (! particleCharacter.startsWith("§")) ) {
            handleParticleLine_snowstorm(vector, startLoc, options);
            return;
        }
        // variables copied from options
        double length = options.length;
        double width = options.width;
        double stepsize = options.stepsize;
        int ticksLinger = options.ticksLinger;
        float alpha = options.alpha;
        if (ticksLinger <= 0) return;

        Vector dVec = vector.clone().normalize();
        int loopTime = (int) Math.round(length / stepsize);
        dVec.multiply(length / loopTime);
        List<Color> allColors = options.particleColorObjects;
        Location currLoc = startLoc.clone();
        for (int i = 0; i <= loopTime; i ++) {
            // tweak color
            Color currentColor = options.particleColor.get(0).equals("RAINBOW") ?
                    getRainbowColor(2500) : getInterpolateColor((double) i / (loopTime + 1), allColors);
            // spawn "particles"
            if (options.vanillaParticle) {
                double particleEstDist = (stepsize + width) / 2;
                // basically, max(estimated dist, estimated dist^2) * intensityMulti
                double particleAmountRaw = options.intensityMulti *
                        (particleEstDist < 1 ? particleEstDist : particleEstDist * particleEstDist );
                double rVal = (currentColor.getRed() / 255d) - 1;
                double gVal = (currentColor.getGreen() / 255d);
                double bVal = (currentColor.getBlue() / 255d);
                // spawn particles for each player
                for (Player ply : startLoc.getWorld().getPlayers()) {
                    int particleAmount = MathHelper.randomRound(
                            particleAmountRaw * GLOBAL_PARTICLE_DENSITY *
                                    Setting.getOptionDouble(ply, Setting.Options.PARTICLE_DENSITY_MULTI));
                    for (int spawnIdx = 0; spawnIdx < particleAmount; spawnIdx ++) {
                        Location currParticleLoc = currLoc.clone().add(
                                Math.random() * width * 2 - width,
                                Math.random() * width * 2 - width,
                                Math.random() * width * 2 - width);
                        ply.spawnParticle(Particle.REDSTONE, currParticleLoc, 0, rVal, gVal, bVal);
                    }
                }
            }
            else {
                String rCode = Integer.toHexString(currentColor.getRed()),
                        gCode = Integer.toHexString(currentColor.getGreen()),
                        bCode = Integer.toHexString(currentColor.getBlue());
                if (rCode.length() == 1) rCode = "0" + rCode;
                if (gCode.length() == 1) gCode = "0" + gCode;
                if (bCode.length() == 1) bCode = "0" + bCode;
                String colorCode = rCode + gCode + bCode;
                displayHoloText(currLoc, "§#" + colorCode + particleCharacter, ticksLinger, (float) width, (float) width, alpha);
            }
            // add vector to location
            currLoc.add(dVec);
        }
    }
    // Handle snowstorm particle
    public static void handleParticleLine_snowstorm(Vector vector, Location startLoc, ParticleLineOptions options) {
        // variables copied from options
        double length = options.length;
        double width = options.width;
        double stepsize = options.stepsize;
        int ticksLinger = options.ticksLinger;
        String particleName = options.particleChar;

        DragoncoreHelper.DragonCoreParticleInfo particleInfo = new DragoncoreHelper.DragonCoreParticleInfo(particleName, startLoc);

        double rotateY = -(float) MathHelper.getVectorYaw(vector) + 90;
        double rotateZ = (float) MathHelper.getVectorPitch(vector);
        particleInfo.setRotationalInfo(String.format("0,%f,%f", rotateY, rotateZ));

        DragoncoreHelper.displayBlizzardParticle(particleInfo, ticksLinger);
    }

    // helper function for handle strike line. This only handles damage.
    private static Location handleStrikeLineDamage(World wld, Location startLoc, Location terminalLoc, double width,
                                               com.google.common.base.Predicate<? super net.minecraft.server.v1_12_R1.Entity> predication,
                                               Entity damager, double damage,
                                               HashMap<String, Double> attrMap, String itemType,
                                               Collection<Entity> exceptions, int lingerTime,
                                               StrikeLineOptions advanced) {
        if (lingerTime <= 0) return null;
        // setup variables
        boolean useAttrMapDamage = advanced.damage <= 0d;
        int     damageCD = advanced.damageCD,
                lingerDelay = advanced.lingerDelay,
                penetration = advanced.maxTargetHit;
        double  decayCoef = advanced.decayCoef,
                whipBonusCrit = advanced.whipBonusCrit,
                whipBonusDamage = advanced.whipBonusDamage;
        TriConsumer<Integer, Entity, Location> damagedFunction = advanced.damagedFunction;
        // get hit entities
        Set<HitEntityInfo> entityHitCandidate = HitEntityInfo.getEntitiesHit(
                wld, startLoc.toVector(), terminalLoc.toVector(), width, predication);
        for (HitEntityInfo info : entityHitCandidate) {
            Entity victim = info.getHitEntity().getBukkitEntity();
            DamageHelper.handleDamage(damager, victim, damage, DamageHelper.DamageReason.STRIKE);
            // damage decay
            damage *= decayCoef;
            if (useAttrMapDamage)
                attrMap.put("damage", damage);
            // whip marking
            switch (itemType) {
                case "鞭炮":
                case "暗黑收割":
                    victim.addScoreboardTag(itemType);
                    break;
            }
            // whip dmg/crit bonus and prioritized focus
            if (whipBonusDamage > 1e-5 || whipBonusCrit > 1e-5) {
                EntityHelper.setMetadata(victim,  EntityHelper.MetadataName.MINION_WHIP_BONUS_DAMAGE,  whipBonusDamage);
                EntityHelper.setMetadata(victim,  EntityHelper.MetadataName.MINION_WHIP_BONUS_CRIT,    whipBonusCrit);
                EntityHelper.setMetadata(damager, EntityHelper.MetadataName.PLAYER_MINION_WHIP_FOCUS,  victim);
            }
            damageCoolDown(exceptions, victim, damageCD);
            advanced.amountEntitiesHit ++;
            // use damaged function
            if (damagedFunction != null)
                damagedFunction.accept(advanced.amountEntitiesHit, victim,
                        MathHelper.toBukkitVector(info.getHitLocation().pos).toLocation(victim.getWorld()) );
            // handle maxTargetHit
            if (advanced.amountEntitiesHit >= penetration)
                return MathHelper.toBukkitVector(info.getHitLocation().pos).toLocation(wld);
        }
        // destroy vegetation
        WorldHelper.attemptDestroyVegetation(startLoc, terminalLoc);
        // schedule lingering
        if (lingerTime > 1) {
            double finalDamage = damage;
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleStrikeLineDamage(wld, startLoc, terminalLoc, width, predication, damager, finalDamage,
                            attrMap, itemType, exceptions, lingerTime - 1, advanced),
                    lingerDelay);
        }
        return null;
    }
    // warning: this function modifies attrMap and exceptions!
    public static void handleStrikeLine(Entity damager, Location startLoc, double yaw, double pitch, double length, double width,
                                        String itemType, String color, Collection<Entity> exceptions,
                                        HashMap<String, Double> attrMap, StrikeLineOptions advanced) {
        handleStrikeLine(damager, startLoc, yaw, pitch, length, width, width,
                itemType, color, exceptions, attrMap, advanced);
    }
    public static void handleStrikeLine(Entity damager, Location startLoc,
                                        double yaw, double pitch, double length, double width, double particleInterval,
                                        String itemType, String color, Collection<Entity> exceptions,
                                        HashMap<String, Double> attrMap, StrikeLineOptions advanced) {
        if (length < 0) return;
        // setup variables
        boolean bounceWhenHitBlock = advanced.bounceWhenHitBlock,
                thruWall = advanced.thruWall,
                useAttrMapDamage = advanced.damage < 1e-5;
        int     lingerTime = advanced.lingerTime,
                lingerDelay = advanced.lingerDelay;
        double  damage = useAttrMapDamage ? attrMap.getOrDefault("damage", 10d) : advanced.damage;
        ParticleLineOptions particleInfo = advanced.particleInfo;
        // default particle info if it does not exist
        if (particleInfo == null) {
            particleInfo = new ParticleLineOptions()
                    .setParticleColor(color)
                    .setAlpha(0.5f)
                    .setIntensityMulti(advanced.particleIntensityMulti)
                    .setVanillaParticle(advanced.vanillaParticle)
                    .setTicksLinger(lingerTime > 1 ? (lingerTime - 1) * lingerDelay : lingerDelay);
        }
        // find terminal location ( hit block etc. )
        World wld = startLoc.getWorld();
        Vector direction = MathHelper.vectorFromYawPitch_approx(yaw, pitch);
        direction.multiply(length);
        Location terminalLoc = startLoc.clone().add(direction);
        // if the projectile does not go through wall or have a block hit action, find possible collision
        if (!thruWall || advanced.blockHitFunction != null) {
            MovingObjectPosition hitLoc = HitEntityInfo.rayTraceBlocks(wld,
                    startLoc.toVector(),
                    terminalLoc.toVector());
            // if a block has been hit
            if (hitLoc != null && hitLoc.pos != null) {
                Location blockHitLoc = MathHelper.toBukkitVector(hitLoc.pos).toLocation(wld);
                if (advanced.blockHitFunction != null)
                    advanced.blockHitFunction.accept(blockHitLoc.clone(), hitLoc);
                // if the weapon do not go thru wall
                if (!thruWall) {
                    terminalLoc = blockHitLoc;
                    direction = terminalLoc.toVector().subtract(startLoc.toVector());
                    double distToWall = direction.length();
                    // bounce when hit block
                    if (bounceWhenHitBlock) {
                        // make sure that the new strike line does not get stuck in wall
                        Vector dirToHitLoc = direction.clone();
                        if (distToWall > 0.01) {
                            dirToHitLoc.multiply((distToWall - 0.01) / distToWall);
                            terminalLoc = startLoc.clone().add(dirToHitLoc);
                            distToWall -= 0.01;
                        }
                        double newStrikeLength = length - Math.max(distToWall, 5);
                        switch (hitLoc.direction) {
                            case EAST:
                            case WEST:
                                dirToHitLoc.setX(dirToHitLoc.getX() * -1);
                                break;
                            case UP:
                            case DOWN:
                                dirToHitLoc.setY(dirToHitLoc.getY() * -1);
                                break;
                            case SOUTH:
                            case NORTH:
                                dirToHitLoc.setZ(dirToHitLoc.getZ() * -1);
                                break;
                        }
                        Location newStartLoc = terminalLoc;
                        Bukkit.getScheduler().runTask(TerrariaHelper.getInstance(),
                                () -> handleStrikeLine(damager, newStartLoc,
                                        MathHelper.getVectorYaw(dirToHitLoc), MathHelper.getVectorPitch(dirToHitLoc),
                                        newStrikeLength, width, itemType,
                                        color, exceptions, attrMap, advanced));
                    }
                }
            }
        }
        // init predication for victim selection
        com.google.common.base.Predicate<? super net.minecraft.server.v1_12_R1.Entity> predication;
        if (advanced.shouldDamageFunction != null)
            predication = (e) -> advanced.shouldDamageFunction.test(e.getBukkitEntity());
        else
            predication = (e) -> {
                Entity entity = e.getBukkitEntity();
                if (DamageHelper.checkCanDamage(damager, entity, false)) {
                    return !exceptions.contains(entity);
                }
                return false;
            };
        // damage hit entities
        Location newTerminalLoc = handleStrikeLineDamage(wld, startLoc, terminalLoc, width, predication, damager, damage,
                attrMap, itemType, exceptions, lingerTime, advanced);
        if (newTerminalLoc != null) {
            direction = newTerminalLoc.subtract(startLoc).toVector();
        }
        // display particle
        particleInfo
                // melee weapon sprite size etc. should not depend on block that is in its way!
                .setLength( particleInfo.particleOrItem ? direction.length() : length )
                .setWidth(width * 2)
                .setStepsize(particleInterval);
        if (advanced.displayParticle)
            handleParticleLine(direction, startLoc, particleInfo);
    }
    // helper function for each step of lightning
    private static void handleStrikeLightningStep(Entity damager, Location[] locations, int currIndex,
                                                  double width, double particleInterval, int delay, String color,
                                                  Collection<Entity> exceptions, HashMap<String, Double> attrMap, StrikeLineOptions advanced) {
        // find the current direction
        Location startLoc = locations[currIndex];
        Vector currDir = locations[currIndex + 1].clone().subtract(startLoc).toVector();
        handleStrikeLine(damager, startLoc, MathHelper.getVectorYaw(currDir), MathHelper.getVectorPitch(currDir), currDir.length(),
        width, particleInterval, "LIGHTNING", color, exceptions, (HashMap<String, Double>) attrMap.clone(), advanced);
        // if the lightning reached penetration limit
        if (advanced.amountEntitiesHit >= advanced.maxTargetHit)
            return;
        if (currIndex + 2 < locations.length) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleStrikeLightningStep(damager, locations, currIndex + 1, width, particleInterval, delay, color, exceptions, attrMap, advanced),
                    delay);
        }
    }
    public static void handleStrikeLightning(Entity damager, Location startLoc,
                                             double yaw, double pitch, double length, double stepSize, double width,
                                             double offset, int delay, String color,
                                             Collection<Entity> exceptions, HashMap<String, Double> attrMap,
                                             StrikeLineOptions advanced) {
        handleStrikeLightning(damager, startLoc, yaw, pitch, length, stepSize, width, width, offset, delay, color, exceptions, attrMap, advanced);
    }
    public static void handleStrikeLightning(Entity damager, Location startLoc,
                                             double yaw, double pitch, double length, double stepSize, double width, double particleStepSize,
                                             double offset, int delay, String color,
                                             Collection<Entity> exceptions, HashMap<String, Double> attrMap,
                                             StrikeLineOptions advanced) {
        // initialize the intermediate points
        int size = (int) Math.max(1, Math.round(length / stepSize)) + 1;
        Location[] locations = new Location[size];
        Vector dVec = MathHelper.vectorFromYawPitch_approx(yaw, pitch).multiply(length / (size - 1));
        for (int i = 0; i < size; i ++) {
            Location currLoc = startLoc.clone().add(dVec.clone().multiply(i));
            // start location and terminal location must be kept intact.
            if (i != 0 && i + 1 < size) {
                currLoc.add(MathHelper.randomVector().multiply(offset));
            }
            locations[i] = currLoc;
        }
        // handle the steps
        handleStrikeLightningStep(damager, locations, 0, width, particleStepSize,
                delay, color, exceptions, attrMap, advanced);
    }
    public static void displayHoloText(Location displayLoc, String text, int ticksDisplay, float width, float height, float alpha) {
        String holoInd = "" + (nextWorldTextureIndex++);
        // all players in radius of 64 blocks can see the hologram
        ArrayList<Player> playersSent = new ArrayList<>(10);
        for (Player p : displayLoc.getWorld().getPlayers())
            if (p.getLocation().distanceSquared(displayLoc) < PARTICLE_DISPLAY_RADIUS_SQR) playersSent.add(p);
        for (Player p : playersSent)
            CoreAPI.setPlayerWorldTexture(p, holoInd, displayLoc, 0, 0, 0, "[text]" + text, width, height, alpha, true, true);
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
            for (Player p : playersSent)
                CoreAPI.removePlayerWorldTexture(p, holoInd);
        }, ticksDisplay);
    }
    private static double[] calculateRotation(Vector displayDir, Vector rightOrthonormal) {
        double[] result = {0, 0, 0};
        rightOrthonormal.normalize();
        // eliminate edge case
        if (Math.abs(rightOrthonormal.getZ()) < 0.0001) {
            rightOrthonormal.setZ(0.0001);
            rightOrthonormal.normalize();
        }
        // calculate the principle roots of x, y rotation
        result[0] = Math.asin(rightOrthonormal.getY()) * MathHelper.RAD_TO_DEG;
        result[1] = Math.atan(rightOrthonormal.getX() / rightOrthonormal.getZ()) * MathHelper.RAD_TO_DEG;
        // now, consider all roots
        double sinX, sinY, cosX, cosY;
        sinX = rightOrthonormal.getY();
        cosX = MathHelper.xcos_degree(result[0]);
        sinY = MathHelper.xsin_degree(result[1]);
        cosY = MathHelper.xcos_degree(result[1]);
        // determine the combination of actual x and y.
        // x is flipped about the y-axis, y is added by 180 deg.
        boolean xFlipped = false, yFlipped = false;
        {
            double bestError = 999999;
            boolean[] flipState = {true, false};
            for (boolean flipX : flipState) {
                for (boolean flipY : flipState) {
                    double currError = 0;
                    int signMulti = -1;
                    if (flipX) signMulti *= -1;
                    if (flipY) signMulti *= -1;
                    currError += Math.abs(rightOrthonormal.getZ() -
                            signMulti * cosX * cosY);
                    currError += Math.abs(rightOrthonormal.getX() -
                            signMulti * cosX * sinY);
                    if (currError < bestError) {
                        bestError = currError;
                        xFlipped = flipX;
                        yFlipped = flipY;
                    }
                }
            }
        }
        // after determining x and y, handle the aftermath
        {
            if (!xFlipped) {
                cosX *= -1;
                result[0] = 180 - result[0];
            }
            if (!yFlipped) {
                sinY *= -1;
                cosY *= -1;
                result[1] += 180;
            }
        }
        // calculate the z-rotation
        {
            // default sword direction, (-1, 1, 0)
            // (-1, 1, 0) after z, x, y rotation -> direction
            // as rotations are linear transformation,
            // direction after y, x, z rotation -> (-1, 1, 0)
            Vector original = new Vector(-1, 1, 0);
            Vector zRotTargetVec = displayDir.clone();
            // inverse rotation: rotate by -x, -y
            // sin(-x) = -sin(x), cos(-x) = cos(x)
            zRotTargetVec = MathHelper.rotateY(zRotTargetVec, -sinY, cosY);
            zRotTargetVec = MathHelper.rotateX(zRotTargetVec, -sinX, cosX);
            result[2] = MathHelper.getAngleRadian(zRotTargetVec, original) * MathHelper.RAD_TO_DEG;
            double sinZ = MathHelper.xsin_degree(result[2]);
            double cosZ = MathHelper.xcos_degree(result[2]);
            // result: if the z is correct, the inverse we get (-)
            // inv: if the z should be flipped, the inverse we get (+)
            Vector zRotResult = MathHelper.rotateZ(zRotTargetVec, -sinZ, cosZ);
            Vector zRotResultInv = MathHelper.rotateZ(zRotTargetVec, sinZ, cosZ);
            if (zRotResult.dot(original) < zRotResultInv.dot(original)) {
                result[2] = result[2] * -1;
            }
        }
        // return the answer
        return result;
    }
    public static String displayNonDirectionalHoloItem(Location displayLoc, ItemStack item, int ticksDisplay, float size) {
        String holoInd = "" + (nextWorldTextureIndex++);
        displayNonDirectionalHoloItem(displayLoc, item, ticksDisplay, size, holoInd);
        return holoInd;
    }
    public static void displayNonDirectionalHoloItem(Location displayLoc, ItemStack item, int ticksDisplay, float size, String holoInd) {
        // all players in radius of 64 blocks can see the hologram
        ArrayList<Player> playersSent = new ArrayList<>(10);
        for (Player p : displayLoc.getWorld().getPlayers())
            if (p.getLocation().distanceSquared(displayLoc) < PARTICLE_DISPLAY_RADIUS_SQR) playersSent.add(p);
        // send packets
        for (Player p : playersSent)
            CoreAPI.setPlayerWorldTextureItem(p, holoInd, displayLoc,
                    0, 0, 0, item, size * 2, true);
        if (ticksDisplay >= 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                for (Player p : playersSent)
                    CoreAPI.removePlayerWorldTexture(p, holoInd);
            }, ticksDisplay);
        }
    }
    public static void displayHoloItem(Location displayLoc, ItemStack item, int ticksDisplay, float size, Vector displayDir, Vector rightOrthogonalDirection) {
        String holoInd = "" + (nextWorldTextureIndex++);
        // all players in radius of 64 blocks can see the hologram
        ArrayList<Player> playersSent = new ArrayList<>(10);
        for (Player p : displayLoc.getWorld().getPlayers())
            if (p.getLocation().distanceSquared(displayLoc) < PARTICLE_DISPLAY_RADIUS_SQR) playersSent.add(p);
        // init rotation
        float rotateX, rotateY, rotateZ;
        // if no special rotation, use pre-calculated default.
        if (rightOrthogonalDirection == null) {
            rotateX = 0;
            rotateY = -(float) MathHelper.getVectorYaw(displayDir) + 90;
            rotateZ = (float) MathHelper.getVectorPitch(displayDir) + 45;
        }
        // if the sprite is required to rotate in place, calculate it carefully.
        else {
            double[] result = calculateRotation(displayDir, rightOrthogonalDirection);
            rotateX = (float) result[0];
            rotateY = (float) result[1];
            rotateZ = (float) result[2];
        }
        // generate actual sprite location
        Vector actualDisplayLocOffset = displayDir.clone();
        actualDisplayLocOffset.multiply(size / 2 / actualDisplayLocOffset.length() );
        Location displayLocActual = displayLoc.clone();
        displayLocActual.add(actualDisplayLocOffset);
        // send packets
        for (Player p : playersSent)
            CoreAPI.setPlayerWorldTextureItem(p, holoInd, displayLocActual,
                    rotateX, rotateY, rotateZ, item, size * 2, false);
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
            for (Player p : playersSent)
                CoreAPI.removePlayerWorldTexture(p, holoInd);
        }, ticksDisplay);
    }
    public static void displayHolo(Entity e, double dmg, boolean isCrit, String hologramInfo) {
        String colorCode;
        switch (hologramInfo) {
            case "回血":
                colorCode = "a";
                break;
            case "回蓝":
                colorCode = "9";
                break;
            case "Debuff_咒火":
            case "Debuff_神圣之火":
                colorCode = "e";
                break;
            case "Debuff_霜火":
            case "Debuff_带电":
            case "Debuff_幻星感染":
            case "Debuff_夜魇":
                colorCode = "b";
                break;
            case "Debuff_硫磺火":
            case "Debuff_孱弱巫咒":
                colorCode = "7";
                break;
            case "Debuff_中毒":
            case "Debuff_剧毒":
            case "Debuff_瘟疫":
            case "Debuff_硫磺海剧毒":
                colorCode = "2";
                break;
            case "Debuff_破晓":
            case "Debuff_血液沸腾":
            case "Debuff_龙焰":
            case "Debuff_解离":
            case "Debuff_血神之凋零":
                colorCode = "4";
                break;
            case "Debuff_暗影焰":
            case "Debuff_弑神怒焰":
                colorCode = "d";
                break;
            case "Debuff_元素谐鸣": {
                int rdm = (int) (Math.random() * 4);
                // a random color from the four pillars
                switch (rdm) {
                    case 0:
                        colorCode = "#EF00FF";
                        break;
                    case 1:
                        colorCode = "#FF8000";
                        break;
                    case 2:
                        colorCode = "#00CC92";
                        break;
                    case 3:
                    default:
                        colorCode = "#00B4FF";
                        break;
                }
                break;
            }
            case "Debuff_超位崩解": {
                int rdm = (int) (Math.random() * 3);
                // a random color from red, light blue and light green
                switch (rdm) {
                    case 0:
                        colorCode = "#F27049";
                        break;
                    case 1:
                        colorCode = "#A6F069";
                        break;
                    case 2:
                    default:
                        colorCode = "#69F0DC";
                        break;
                }
                break;
            }
            default:
                colorCode = isCrit ? "c" : "6";
        }
        int ticksDisplay = 15;
        switch (hologramInfo) {
            case "Drowning":
                ticksDisplay = 10;
                break;
            case "Suffocation":
                ticksDisplay = 5;
                break;
            default:
                if (hologramInfo.startsWith("Debuff_")) {
                    ticksDisplay = 8;
                } else if (isCrit) ticksDisplay = 30;
        }
        // display the message
        String text = ChatColor.COLOR_CHAR + colorCode + (int) Math.round(dmg);
        Location displayLoc = EntityHelper.getRandomPosInEntity(e,
                new EntityHelper.RandomPosInBBInfo()
                        .setSideEdgesOnly(true)
                        .setBBShrinkYBottom(1d));
        displayHoloText(displayLoc, text, ticksDisplay, 1f, 0.75f, 0.75f);
    }
    public static double[] interpolateDirection(double initialYaw, double initialPitch,
                                                                 double offsetYaw, double offsetPitch) {
        double finalYaw = initialYaw + offsetYaw;
        double finalPitch = initialPitch + offsetPitch;
        // regularize not number / infinity
        if (Double.isNaN(finalYaw) || finalYaw == Double.NEGATIVE_INFINITY || finalYaw == Double.POSITIVE_INFINITY)
            finalYaw = 0;
        if (Double.isNaN(finalPitch) || finalPitch == Double.NEGATIVE_INFINITY || finalPitch == Double.POSITIVE_INFINITY)
            finalPitch = 0;
        // regulate pitch
        if (finalPitch > 90) {
            // 90 - (finalPitch - 90)
            finalPitch = 180 - finalPitch;
            finalYaw += 180;
        }
        if (finalPitch < -90) {
            // -90 + (-90 - finalPitch)
            finalPitch = -180 - finalPitch;
            finalYaw += 180;
        }
        // regulate yaw
        while (finalYaw > 180) finalYaw -= 360;
        while (finalYaw < -180) finalYaw += 360;
        return new double[] { finalYaw, finalPitch };
    }
    public static double[] getDirectionInterpolateOffset(double initialYaw, double initialPitch,
                                                                          double targetYaw, double targetPitch, double progress) {
        double yawOffset = targetYaw - initialYaw;
        if (yawOffset < -180) yawOffset += 360;
        if (yawOffset > 180) yawOffset -= 360;
        double pitchOffset = targetPitch - initialPitch;
        // TODO: (?) swing over head or below foot
        return new double[] { yawOffset * progress, pitchOffset * progress };
    }
}
