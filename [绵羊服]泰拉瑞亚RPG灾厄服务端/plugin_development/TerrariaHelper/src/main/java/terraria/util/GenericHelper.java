package terraria.util;

import eos.moe.dragoncore.api.CoreAPI;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.HitEntityInfo;

import java.util.*;
import java.util.function.BiConsumer;

public class GenericHelper {
    static long nextHologramIndex = 0;
    public static class ParticleLineOptions {
        double length, width, stepsize;
        float alpha;
        int ticksLinger;
        String particleChar;
        List<String> particleColor;
        public ParticleLineOptions() {
            length = 1;
            width = 0.25;
            stepsize = width;
            ticksLinger = 5;
            particleChar = "█";
            particleColor = new ArrayList<>();
            particleColor.add("255|255|255");
            alpha = 0.5f;
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
            return this;
        }
    }
    public static class StrikeLineOptions {
        boolean bounceWhenHitBlock, thruWall;
        int damageCD, lingerTime, lingerDelay, maxTargetHit;
        double damage, decayCoef, whipBonusCrit, whipBonusDamage;
        ParticleLineOptions particleInfo;
        BiConsumer<Integer, Entity> damagedFunction;
        // internal variables
        int amountEntitiesHit;
        public StrikeLineOptions() {
            bounceWhenHitBlock = false;
            thruWall = true;

            damageCD = 10;
            lingerTime = 1;
            lingerDelay = 6;
            maxTargetHit = 999999;

            damage = 0d;
            decayCoef = 1d;
            whipBonusCrit = 0d;
            whipBonusDamage = 0d;

            particleInfo = null;

            damagedFunction = null;
            // internal values
            amountEntitiesHit = 0;
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

        public StrikeLineOptions setDamage(double damage) {
            this.damage = damage;
            return this;
        }
        public StrikeLineOptions setDecayCoef(double decayCoef) {
            this.damage = decayCoef;
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


        public StrikeLineOptions setParticleInfo(ParticleLineOptions particleInfo) {
            this.particleInfo = particleInfo;
            return this;
        }

        public StrikeLineOptions setDamagedFunction(BiConsumer<Integer, Entity> damagedFunction) {
            this.damagedFunction = damagedFunction;
            return this;
        }
    }

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
        list.add(victim);
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> list.remove(victim), cd);
    }
    public static int[] coinConversion(int amount, boolean copperOrRaw) {
        int amountCopper;
        if (copperOrRaw) amountCopper = amount;
        else amountCopper = amount / 100;
        return coinConversion(amountCopper);
    }
    public static int[] coinConversion(int copperAmount) {
        int copper = copperAmount;
        int[] result = new int[]{0, 0, 0, 0};
        if (copper >= 1000000) {
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
    public static void dropMoney(Location loc, int amount, boolean copperOrRaw) {
        int amountCopper;
        if (copperOrRaw) amountCopper = amount;
        else amountCopper = amount / 100;
        dropMoney(loc, amountCopper);
    }
    public static void dropMoney(Location loc, int amount) {
        int[] stackSize = coinConversion(amount);
        if (stackSize[0] > 0) loc.getWorld().dropItemNaturally(loc,
                ItemHelper.getItemFromDescription("铂金币:" + stackSize[0], false));
        if (stackSize[1] > 0) loc.getWorld().dropItemNaturally(loc,
                ItemHelper.getItemFromDescription("金币:" + stackSize[1], false));
        if (stackSize[2] > 0) loc.getWorld().dropItemNaturally(loc,
                ItemHelper.getItemFromDescription("银币:" + stackSize[2], false));
        if (stackSize[3] > 0) loc.getWorld().dropItemNaturally(loc,
                ItemHelper.getItemFromDescription("铜币:" + stackSize[3], false));
    }
    public static double getHorizontalDistance(Location locationA, Location locationB) {
        double distX = Math.abs(locationA.getX() - locationA.getX());
        double distZ = Math.abs(locationA.getZ() - locationA.getZ());
        return Math.max(distX, distZ);
    }
    public static void handleParticleLine(Vector vector, Location startLoc, ParticleLineOptions options) {
        // variables copied from options
        double length = options.length;
        double width = options.width;
        double stepsize = options.stepsize;
        int ticksLinger = options.ticksLinger;
        float alpha = options.alpha;
        String particleCharacter = options.particleChar;
        List<String> particleColor = options.particleColor;
        if (ticksLinger <= 0) return;

        Vector dVec = vector.clone().normalize();
        int loopTime = (int) Math.round(length / stepsize);
        dVec.multiply(length / loopTime);
        List<Color> allColors = new ArrayList<>();
        for (String currColor : particleColor) {
            String[] info = currColor.split("\\|");
            allColors.add(Color.fromRGB(Integer.parseInt(info[0]), Integer.parseInt(info[1]), Integer.parseInt(info[2])));
        }
        Location currLoc = startLoc.clone();
        for (int i = 0; i <= loopTime; i ++) {
            // tweak color
            double colorProgress = (double) i * allColors.size() / (loopTime + 1);
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
            // spawn "particles"
            String rCode = Integer.toHexString(rInt), gCode = Integer.toHexString(gInt), bCode = Integer.toHexString(bInt);
            if (rCode.length() == 1) rCode = "0" + rCode;
            if (gCode.length() == 1) gCode = "0" + gCode;
            if (bCode.length() == 1) bCode = "0" + bCode;
            String colorCode = rCode + gCode + bCode;
            displayHoloText(currLoc, "§#" + colorCode + particleCharacter, ticksLinger, (float) width, (float) width, alpha);
            // add vector to location
            currLoc.add(dVec);
        }
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
        BiConsumer<Integer, Entity> damagedFunction = advanced.damagedFunction;
        // get hit entities
        Set<HitEntityInfo> entityHitCandidate = HitEntityInfo.getEntitiesHit(
                wld, startLoc.toVector(), terminalLoc.toVector(), width, predication);
        for (HitEntityInfo info : entityHitCandidate) {
            Entity victim = info.getHitEntity().getBukkitEntity();
            EntityHelper.handleDamage(damager, victim, damage, "DirectDamage");
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
            // whip dmg/crit bonus
            if (whipBonusDamage > 0 && whipBonusCrit > 0) {
                EntityHelper.setMetadata(victim, "minionWhipBonusDamage",   whipBonusDamage);
                EntityHelper.setMetadata(victim, "minionWhipBonusCrit",     whipBonusCrit);
            }
            damageCoolDown(exceptions, victim, damageCD);
            advanced.amountEntitiesHit ++;
            // use damaged function
            if (damagedFunction != null)
                damagedFunction.accept(advanced.amountEntitiesHit, victim);
            // handle maxTargetHit
            if (advanced.amountEntitiesHit >= penetration)
                return MathHelper.toBukkitVector(info.getHitLocation().pos).toLocation(wld);
        }
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
    public static void handleStrikeLine(Entity damager, Location startLoc, double yaw, double pitch, double length, double width, String itemType, String color, Collection<Entity> exceptions, HashMap<String, Double> attrMap, StrikeLineOptions advanced) {
        if (length < 0) return;
        // setup variables
        boolean bounceWhenHitBlock = advanced.bounceWhenHitBlock,
                thruWall = advanced.thruWall,
                useAttrMapDamage = advanced.damage <= 0d;
        int     lingerTime = advanced.lingerTime,
                lingerDelay = advanced.lingerDelay;
        double  damage = useAttrMapDamage ? attrMap.getOrDefault("damage", 10d) : advanced.damage;
        ParticleLineOptions particleInfo = advanced.particleInfo;
        // default particle info if it does not exist
        if (particleInfo == null) {
            particleInfo = new ParticleLineOptions()
                    .setParticleColor(color)
                    .setAlpha(0.5f)
                    .setTicksLinger(lingerTime > 1 ? (lingerTime - 1) * lingerDelay : lingerDelay);
        }
        // find terminal location ( hit block etc. )
        World wld = startLoc.getWorld();
        Vector direction = MathHelper.vectorFromYawPitch_quick(yaw, pitch);
        direction.multiply(length);
        Location terminalLoc = startLoc.clone().add(direction);
        // if the projectile does not go through wall, find possible collision
        if (!thruWall) {
            MovingObjectPosition hitLoc = HitEntityInfo.rayTraceBlocks(wld,
                    startLoc.toVector(),
                    terminalLoc.toVector());
            if (hitLoc != null && hitLoc.pos != null) {
                terminalLoc = MathHelper.toBukkitVector(hitLoc.pos).toLocation(wld);
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
        // init predication for victim selection
        com.google.common.base.Predicate<? super net.minecraft.server.v1_12_R1.Entity> predication;
        if (itemType.contains("虫网")) {
            predication = (e) -> {
                if (!e.isAlive()) return false;
                Entity entity = e.getBukkitEntity();
                if (entity.getScoreboardTags().contains("isAnimal")) {
                    if (entity instanceof LivingEntity) {
                        LivingEntity entityParsed = (LivingEntity) entity;
                        return entityParsed.getHealth() > 0;
                    }
                }
                return false;
            };
        } else {
            predication = (e) -> {
                Entity entity = e.getBukkitEntity();
                if (EntityHelper.checkCanDamage(damager, entity, false)) {
                    return !exceptions.contains(entity);
                }
                return false;
            };
        }
        // damage hit entities
        Location newTerminalLoc = handleStrikeLineDamage(wld, startLoc, terminalLoc, width, predication, damager, damage,
                attrMap, itemType, exceptions, lingerTime, advanced);
        if (newTerminalLoc != null) {
            direction = newTerminalLoc.subtract(startLoc).toVector();
        }
        // display particle
        particleInfo
                .setLength(direction.length())
                .setWidth(width);
        handleParticleLine(direction, startLoc, particleInfo);
    }
    // helper function for each step of lightning
    private static void handleStrikeLightningStep(Entity damager, Location[] locations, int currIndex, double width, int delay, String color, Collection<Entity> exceptions, HashMap<String, Double> attrMap, StrikeLineOptions advanced) {
        // find the current direction
        Location startLoc = locations[currIndex];
        Vector currDir = locations[currIndex + 1].clone().subtract(startLoc).toVector();
        handleStrikeLine(damager, startLoc, MathHelper.getVectorYaw(currDir), MathHelper.getVectorPitch(currDir), currDir.length(),
        width, "LIGHTNING", color, exceptions, (HashMap<String, Double>) attrMap.clone(), advanced);
        // if the lightning reached penetration limit
        if (advanced.amountEntitiesHit >= advanced.maxTargetHit)
            return;
        if (currIndex + 2 < locations.length) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleStrikeLightningStep(damager, locations, currIndex + 1, width, delay, color, exceptions, attrMap, advanced),
                    delay);
        }
    }
    public static void handleStrikeLightning(Entity damager, Location startLoc, double yaw, double pitch, double length, double stepSize, double width, double offset, int delay, String color, Collection<Entity> exceptions, HashMap<String, Double> attrMap, StrikeLineOptions advanced) {
        // initialize the intermediate points
        int size = (int) Math.max(1, Math.round(length / stepSize)) + 1;
        Location[] locations = new Location[size];
        Vector dVec = MathHelper.vectorFromYawPitch_quick(yaw, pitch).multiply(length / (size - 1));
        for (int i = 0; i < size; i ++) {
            Location currLoc = startLoc.clone().add(dVec.clone().multiply(i));
            // start location and terminal location must be kept intact.
            if (i != 0 && i + 1 < size) {
                currLoc.add(MathHelper.randomVector().multiply(offset));
            }
            locations[i] = currLoc;
        }
        // handle the steps
        handleStrikeLightningStep(damager, locations, 0, width, delay, color, exceptions, attrMap, advanced);
    }
    public static void displayHoloText(Location displayLoc, String text, int ticksDisplay, float width, float height, float alpha) {
        String holoInd = "" + (nextHologramIndex++);
        // all players in radius of 64 blocks can see the hologram
        ArrayList<Player> playersSent = new ArrayList<>(100);
        for (Player p : displayLoc.getWorld().getPlayers())
            if (p.getLocation().distanceSquared(displayLoc) < 40000) playersSent.add(p);
        for (Player p : playersSent)
            CoreAPI.setPlayerWorldTexture(p, holoInd, displayLoc, 0, 0, 0, "[text]" + text, width, height, alpha, true, true);
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
            for (Player p : playersSent)
                CoreAPI.removePlayerWorldTexture(p, holoInd);
        }, ticksDisplay);
    }
    public static void displayHolo(Entity e, double dmg, boolean isCrit, String damageCause) {
        String colorCode;
        switch (damageCause) {
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
                colorCode = "b";
                break;
            case "Debuff_中毒":
            case "Debuff_剧毒":
                colorCode = "2";
                break;
            case "Debuff_破晓":
                colorCode = "4";
                break;
            default:
                colorCode = isCrit ? "c" : "6";
        }
        int ticksDisplay = 15;
        switch (damageCause) {
            case "Drowning":
                ticksDisplay = 10;
                break;
            case "Suffocation":
                ticksDisplay = 5;
                break;
            default:
                if (damageCause.startsWith("Debuff_")) {
                    ticksDisplay = 8;
                } else if (isCrit) ticksDisplay = 30;
        }
        // display the message
        String text = ChatColor.COLOR_CHAR + colorCode + (int) Math.round(dmg);
        Location displayLoc;
        if (e instanceof LivingEntity) displayLoc = ((LivingEntity) e).getEyeLocation().add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5);
        else displayLoc = e.getLocation().add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5);
        displayHoloText(displayLoc, text, ticksDisplay, 1f, 0.75f, 0.75f);
    }
    public static Map.Entry<Double, Double> interpolateDirection(double initialYaw, double initialPitch,
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
        return new AbstractMap.SimpleImmutableEntry<>(finalYaw, finalPitch);
    }
    public static Map.Entry<Double, Double> getDirectionInterpolateOffset(double initialYaw, double initialPitch,
                                                                          double targetYaw, double targetPitch, double progress) {
        double yawOffset = targetYaw - initialYaw;
        if (yawOffset < -180) yawOffset += 360;
        if (yawOffset > 180) yawOffset -= 360;
        double pitchOffset = targetPitch - initialPitch;
        // TODO: (?) swing over head or below foot
        return new AbstractMap.SimpleImmutableEntry<>(yawOffset * progress, pitchOffset * progress);
    }
}
