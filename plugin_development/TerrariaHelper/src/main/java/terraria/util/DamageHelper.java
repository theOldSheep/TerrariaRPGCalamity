package terraria.util;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.gameplay.EventAndTime;
import terraria.gameplay.Setting;

import java.util.*;
import java.util.logging.Level;

public class DamageHelper {
    public static final HashMap<String, DamageType> DAMAGE_TYPE_INTERNAL_NAME_MAPPING = new HashMap<>(30);

    public enum DamageReason {
        BLOCK_EXPLOSION(false, DamageType.BLOCK_EXPLOSION),
        BOSS_ANGRY(true, null),
        CONTACT_DAMAGE(true, DamageType.MELEE),
        DEBUFF(false, DamageType.DEBUFF),
        DROWNING(false, DamageType.DROWNING),
        FALL(false, DamageType.FALL),
        LAVA(false, DamageType.LAVA),
        PROJECTILE(true, null),
        EXPLOSION(true, null),
        FEAR(true, null),
        THORN(true, DamageType.THORN),
        SPECTRE(true, DamageType.SPECTRE),
        STRIKE(true, null),
        SUFFOCATION(false, DamageType.SUFFOCATION);
        // fields
        final boolean isDirectDamage;
        final DamageType damageType;
        // constructors
        DamageReason(boolean isDirectDamage, DamageType damageType) {
            this.isDirectDamage = isDirectDamage;
            this.damageType = damageType;
        }
        // getters
        boolean isDirectDamage() {
            return isDirectDamage;
        }
        DamageType getDamageType() {
            return damageType;
        }
    }

    public enum DamageType {
        MELEE("Melee"),
        TRUE_MELEE("TrueMelee"),
        ARROW("Arrow"),
        BULLET("Bullet"),
        ROCKET("Rocket"),
        ROGUE("Rogue"),
        MAGIC("Magic"),
        SPECTRE("Spectre"),
        SUMMON("Summon"),
        BLOCK_EXPLOSION("BlockExplosion"),
        DEBUFF("Debuff"),
        DROWNING("Drowning"),
        FALL("Fall"),
        LAVA("Lava"),
        NEGATIVE_REGEN("NegativeRegen"),
        SUFFOCATION("Suffocation"),
        THORN("Thorn");
        // field
        String internalName;
        // constructor
        DamageType(String internalName) {
            this.internalName = internalName;
            DAMAGE_TYPE_INTERNAL_NAME_MAPPING.put(internalName, this);
        }
        // getter
        @Override
        public String toString() {
            return internalName;
        }
    }

    public static DamageType getDamageType(Metadatable entity) {
        try {
            return (DamageType) EntityHelper.getMetadata(entity, EntityHelper.MetadataName.DAMAGE_TYPE).value();
        } catch (Exception e) {
            return DamageType.MELEE;
        }
    }

    public static void setDamageType(Metadatable entity, DamageType damageType) {
        if (damageType == null) EntityHelper.setMetadata(entity, EntityHelper.MetadataName.DAMAGE_TYPE, DamageType.MELEE);
        else EntityHelper.setMetadata(entity, EntityHelper.MetadataName.DAMAGE_TYPE, damageType);
    }

    public static void damageCD(Collection<Entity> dmgCdList, Entity entityToAdd, int cdTick) {
        dmgCdList.add(entityToAdd);
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                () -> dmgCdList.remove(entityToAdd), cdTick);
    }

    private static void sendDeathMessage(Entity d, Entity v, DamageType damageType, DamageReason damageReason, String debuffType) {
        String dm = "";
        // special death message cases
        if (v instanceof Player) {
            Player plyV = (Player) v;
            String plyTool = ItemHelper.splitItemName(plyV.getInventory().getItemInMainHand() )[1];
            switch (plyTool) {
                case "雷姆的复仇":
                    dm = "<victim>……是谁？";
                    break;
                case "绯红恶魔":
                    if (damageReason.isDirectDamage)
                        dm = "<victim> 忘记了抱头蹲防";
                    break;
            }
            if ( PlayerHelper.getAccessories(plyV).contains("空灵护符") && Math.random() < 0.1 ) {
                String msg = "§4<victim>死了    <victim>死了".replaceAll("<victim>", v.getName());
                for (int i = 0; i < 3; i ++) {
                    Bukkit.broadcastMessage(msg);
                }
                return;
            }
        }
        if (dm.length() == 0) {
            // general msg
            String killer = null;
            if (d != null) {
                killer = d.getCustomName();
            }

            String deathMessageConfigDir = "deathMessages." + damageType;
            switch (damageType) {
                case DEBUFF:
                    deathMessageConfigDir = "deathMessages.Debuff_" + debuffType;
                    break;
                case NEGATIVE_REGEN: {
                    MetadataValue mdv = EntityHelper.getMetadata(v, EntityHelper.MetadataName.PLAYER_NEG_REGEN_CAUSE);
                    if (mdv != null) {
                        HashMap<String, Double> negRegenCause = (HashMap<String, Double>) mdv.value();
                        String randomizedCause = MathHelper.selectWeighedRandom(negRegenCause);
                        deathMessageConfigDir = "deathMessages.NegRegen_" + randomizedCause;
                    }
                    break;
                }
            }

            List<String> deathMessages;
            if (TerrariaHelper.settingConfig.contains(deathMessageConfigDir)) {
                deathMessages = TerrariaHelper.settingConfig.getStringList(deathMessageConfigDir);
            } else {
                deathMessages = TerrariaHelper.settingConfig.getStringList("deathMessages.Generic");
            }
            dm = deathMessages.get((int) (Math.random() * deathMessages.size()));
            if (killer != null && d != v) {
                dm += "，凶手是" + killer;
            }
        }
        dm = dm.replaceAll("<victim>", v.getName());
        Bukkit.broadcastMessage("§4" + dm);
    }

    public static boolean entityDamageEvent(Entity damager, Entity damageSource, LivingEntity victim, LivingEntity damageTaker,
                                            double dmg, double originalDmg,
                                            DamageType damageType, DamageReason damageReason) {
        if (damager == null) return true;
        String nameV = GenericHelper.trimText(victim.getName());
        Entity minion = damager;
        // validate if the damage is direct (or more specifically, contact damage)
        boolean isContactDmg = false, isDirectDmg = false;
        switch (damageType) {
            case MELEE:
            case TRUE_MELEE:
                isContactDmg = true;
            case ARROW:
            case BULLET:
            case MAGIC:
            case SPECTRE:
            case ROCKET:
            case SUMMON:
                isDirectDmg = true;
        }
        // special minion behaviour
        if (minion instanceof Projectile) {
            ProjectileSource projSrc = ((Projectile) minion).getShooter();
            if (projSrc instanceof Entity) minion = (Entity) projSrc;
        }
        if (minion.getScoreboardTags().contains("isMinion")) {
            if (victim.getScoreboardTags().contains("暗黑收割")) {
                victim.removeScoreboardTag("暗黑收割");
                // prevent doubled damage on initial target due to the explosion
                ArrayList<Entity> damageExceptions = new ArrayList<>();
                damageExceptions.add(victim);
                EntityHelper.handleEntityExplode(damager, 2, damageExceptions, victim.getEyeLocation());
            }
        }
        // special victim behaviour
        switch (nameV) {
            case "史莱姆王": {
                if (damageSource instanceof Player) {
                    // 100 slimes to spawn in total
                    int amountRemaining = (int) (100 * victim.getHealth() / victim.getMaxHealth());
                    int amountRemainingAfterDmg = (int) (100 * (victim.getHealth() - dmg) / victim.getMaxHealth());
                    int amountSpawn = amountRemaining - amountRemainingAfterDmg;
                    for (int i = 0; i < amountSpawn; i ++)
                        MonsterHelper.spawnMob(
                                Math.random() < 0.2 ? "尖刺史莱姆" : "史莱姆",
                                EntityHelper.getRandomPosInEntity(victim, new EntityHelper.RandomPosInBBInfo().setBBShrinkYTop(1d)),
                                (Player) damageSource);
                }
                break;
            }
            case "毁灭者": {
                if (damageSource instanceof Player) {
                    if (victim.getScoreboardTags().contains("hasProbe") && Math.random() < 0.25) {
                        victim.setCustomName("毁灭者" + ChatColor.COLOR_CHAR + "4");
                        victim.removeScoreboardTag("hasProbe");
                        MonsterHelper.spawnMob("探测怪", victim.getLocation(), (Player) damageSource);
                    }
                }
                break;
            }
            case "石巨人头": {
                if (dmg >= victim.getHealth()) {
                    victim.addScoreboardTag("noDamage");
                    ArrayList<LivingEntity> bossParts = BossHelper.bossMap.get(BossHelper.BossType.GOLEM.msgName);
                    if (bossParts != null) {
                        bossParts.get(0).removeScoreboardTag("noDamage");
                        return false;
                    }
                }
                break;
            }
            case "蜥蜴人": {
                double healthThreshold = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() / 2;
                if (victim.getHealth() > healthThreshold) {
                    if ((victim.getHealth() - dmg) <= healthThreshold) {
                        HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(victim);
                        attrMap.put("damageMulti", 1.5d);
                        attrMap.put("defenceMulti", 1.5d);
                        attrMap.put("knockbackResistance", 1d);
                    }
                }
                break;
            }
            case "胡桃夹士": {
                double healthThreshold = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() / 2;
                if (victim.getHealth() > healthThreshold) {
                    if ((victim.getHealth() - dmg) <= healthThreshold) {
                        HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(victim);
                        attrMap.put("damageMulti", 1.25d);
                        attrMap.put("defenceMulti", 1.5d);
                        attrMap.put("knockbackResistance", 0.91d);
                    }
                }
                break;
            }
        }

        // thorn effect
        HashMap<String, Integer> victimEffects = EntityHelper.getEffectMap(victim);
        if (victimEffects.containsKey("荆棘") && isContactDmg) {
            handleDamage(damageTaker, damager, Math.min(Math.max(dmg / 3, 25), 500), DamageReason.THORN);
        }
        if (victim.getScoreboardTags().contains("destroyOnDamage")) {
            victim.remove();
            return false;
        }

        // player being damaged
        if (victim instanceof Player) {
            Player vPly = (Player) victim;
            // prevent damage and damage handling if in invulnerability
            if (EntityHelper.hasEffect(vPly, "始源林海无敌") )
                return false;
            // health regen time reset
            EntityHelper.setMetadata(victim, EntityHelper.MetadataName.REGEN_TIME, 0);
            // special damager
            switch (damager.getName()) {
                case "水螺旋": {
                    damager.remove();
                    return false;
                }
                case "吮脑怪": {
                    victim.addPassenger(damager);
                    break;
                }
            }
            HashSet<String> accessories = PlayerHelper.getAccessories(victim);
            HashMap<String, Double> victimAttrMap = AttributeHelper.getAttrMap(victim);
            // defence-damage style damage reduction POST damage calculation
            if (isDirectDmg && originalDmg > 50) {
                boolean alternative = accessories.contains("血炎晶核") || accessories.contains("血神圣杯");
                double def = victimAttrMap.getOrDefault("defence", 0d);
                // alternative: lose more defence but regenerate health while recovering
                if (alternative) {
                    int duration = (int) Math.min(def, Math.max(dmg, def * 0.5) );
                    EntityHelper.applyEffect(victim, "血炎防御损毁", duration);
                }
                // "vanilla" behavior of defence-damage
                else {
                    int duration = (int) ( Math.min(def, originalDmg * 0.05 + def * 0.15 ) );
                    EntityHelper.applyEffect(victim, "防御损毁", duration);
                    EntityHelper.applyEffect(victim, "防御修补冷却", 100);
                }
            }
            // accessories
            for (String accessory : accessories) {
                switch (accessory) {
                    // mana recovery on damage
                    case "魔法手铐":
                    case "天界手铐": {
                        int recovery = (int) Math.max(1, Math.floor(dmg / 4));
                        PlayerHelper.restoreMana(vPly, recovery);
                        break;
                    }
                    case "血神圣杯": {
                        if (isDirectDmg && dmg >= 100d) {
                            double recovery = Math.min(vPly.getMaxHealth() - vPly.getHealth(), dmg * 0.95);
                            PlayerHelper.heal(vPly, recovery);
                            EntityHelper.applyEffect(victim, "血神之凋零", (int) Math.ceil(recovery / 10));
                        }
                        break;
                    }
                    // "revive" (heal by factor of 1.75, minimum of 200)
                    case "星云之核": {
                        if (dmg >= vPly.getHealth() && (! victimEffects.containsKey("星云之核冷却")) ) {
                            // cool down for 180 seconds (3600 ticks)
                            EntityHelper.applyEffect(vPly, "星云之核冷却", 3600);
                            // heal for the damage amount
                            PlayerHelper.heal(vPly, Math.max(dmg * 1.75, 200) );
                        }
                        break;
                    }
                }
            }
            // armor sets
            String victimPlayerArmorSet = PlayerHelper.getArmorSet(vPly);
            switch (victimPlayerArmorSet) {
                case "掠夺者坦克套装": {
                    if (isDirectDmg && Math.random() < 0.25)
                        EntityHelper.applyEffect(damageTaker, "掠夺者之怒", 60);
                    if (isContactDmg) {
                        handleDamage(damageTaker, damager, Math.min(Math.max(dmg, 100), 500), DamageReason.THORN);
                    }
                    break;
                }
                case "耀斑套装": {
                    if (isContactDmg) {
                        handleDamage(damageTaker, damager, Math.min(Math.max(dmg * 1.5, 300), 1000), DamageReason.THORN);
                    }
                    break;
                }
                case "龙蒿近战套装": {
                    if (isDirectDmg && Math.random() < 0.25)
                        EntityHelper.applyEffect(damageTaker, "生命涌流", 80);
                    break;
                }
                case "弑神者近战套装": {
                    if (isContactDmg) {
                        handleDamage(damageTaker, damager, Math.min(Math.max(dmg * 2.5, 500), 2500), DamageReason.THORN);
                    }
                    break;
                }
                case "金源近战套装": {
                    if (isDirectDmg && Math.random() < 0.25)
                        EntityHelper.applyEffect(damageTaker, "生命涌流", 80);
                    if (isContactDmg) {
                        handleDamage(damageTaker, damager, Math.min(Math.max(dmg * 2.5, 500), 2500), DamageReason.THORN);
                    }
                    break;
                }
                case "始源林海魔法套装":
                case "始源林海召唤套装":
                case "金源魔法套装":
                case "金源召唤套装": {
                    if (dmg >= vPly.getHealth() && (! victimEffects.containsKey("始源林海无敌冷却")) ) {
                        EntityHelper.applyEffect(vPly, "始源林海无敌", 160);
                        return false;
                    }
                    break;
                }
                case "天钻套装": {
                    if (isDirectDmg) {
                        int[] removeIndexOrder = {0, 4, 5};
                        switch (getDamageType(vPly)) {
                            // ranged
                            case ARROW:
                            case BULLET:
                            case ROCKET:
                                removeIndexOrder[0] = 1;
                                break;
                            // magic
                            case MAGIC:
                                removeIndexOrder[0] = 2;
                                break;
                            // summon
                            case SUMMON:
                                removeIndexOrder[0] = 3;
                                break;
                        }
                        // remove ONE gem, is possible.
                        for (int removeIdx : removeIndexOrder) {
                            String gemToRemove = PlayerHelper.GEM_TECH_GEMS[removeIdx];
                            if (EntityHelper.hasEffect(vPly, gemToRemove)) {
                                EntityHelper.applyEffect(vPly, gemToRemove + "冷却", 600);
                                break;
                            }
                        }
                    }
                    break;
                }
            }
            // special item
            ItemStack plyTool = vPly.getEquipment().getItemInMainHand();
            switch (ItemHelper.splitItemName(plyTool)[1]) {
                case "赤陨霸龙弓": {
                    if (isDirectDmg)
                        EntityHelper.setMetadata(vPly, EntityHelper.MetadataName.PLAYER_ITEM_SWING_AMOUNT, 0);
                    break;
                }
            }
            // buff
            if (isDirectDmg) {
                if (EntityHelper.hasEffect(vPly, "弑神者冲刺"))
                    Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                            () -> EntityHelper.applyEffect(vPly, "神弑者之停息", 600), 5);
            }
        }
        // player damage other entity
        if (damageSource instanceof Player) {
            Player dPly = (Player) damageSource;
            String armorSet = PlayerHelper.getArmorSet(dPly);

            // life steal
            String lifeStealTempCD = "temp_lifeStealCD";
            // debuff and self-damage shall not trigger life steal
            if (damager != victim && ! dPly.getScoreboardTags().contains(lifeStealTempCD)) {
                // cool down
                EntityHelper.handleEntityTemporaryScoreboardTag(dPly, lifeStealTempCD, 1);

                HashMap<String, Double> plyAttrMap = AttributeHelper.getAttrMap(dPly);
                double lifeStealFactor = plyAttrMap.getOrDefault("lifeSteal", 0d);
                if (lifeStealFactor > 1e-9) {
                    double healAmount = dmg * lifeStealFactor;
                    healAmount = Math.min(healAmount, dPly.getMaxHealth() * 0.05);
                    PlayerHelper.heal(dPly, healAmount);
                }
            }

            // general armor set properties (damage-type restricted armor set properties are handled below)
            if (isDirectDmg) {
                // blood flare armor mechanisms
                switch (armorSet) {
                    case "血炎近战套装":
                    case "血炎远程套装":
                    case "血炎盗贼套装":
                    case "血炎魔法套装":
                    case "血炎召唤套装":
                    case "金源近战套装":
                    case "金源远程套装":
                    case "金源盗贼套装":
                    case "金源魔法套装":
                    case "金源召唤套装": {
                        String coolDownTag = "temp_bloodFlareHeart";
                        if (!dPly.getScoreboardTags().contains(coolDownTag)) {
                            // cool down (5 seconds)
                            EntityHelper.handleEntityTemporaryScoreboardTag(dPly, coolDownTag, 100);
                            // drop a heart
                            dropHeart(victim.getLocation());
                        }
                        break;
                    }
                }
                // silva armor healing
                switch (armorSet) {
                    case "始源林海魔法套装":
                    case "始源林海召唤套装":
                    case "金源魔法套装":
                    case "金源召唤套装": {
                        // healing orb
                        String coolDownTagHealingOrb = "temp_silvaHealing";
                        if (!dPly.getScoreboardTags().contains(coolDownTagHealingOrb)) {
                            // cool down (1 second)
                            EntityHelper.handleEntityTemporaryScoreboardTag(dPly, coolDownTagHealingOrb, 20);
                            PlayerHelper.createSpectreProjectile(dPly, victim.getLocation().add(0, 1.5d, 0),
                                    10, true, "96|169|92");
                        }
                        break;
                    }
                }
            }
            // damage type-based armor set properties
            switch (damageType) {
                // magic damage
                case MAGIC: {
                    if (! (damageReason == DamageReason.SPECTRE)) {
                        PlayerHelper.playerMagicArmorSet(dPly, victim, dmg);
                    }
                    break;
                }
                case MELEE:
                case TRUE_MELEE: {
                    switch (armorSet) {
                        case "渊泉近战套装": {
                            String coolDownTag = "temp_hydroThermicFireball";
                            if (!dPly.getScoreboardTags().contains(coolDownTag)) {
                                // cool down
                                EntityHelper.handleEntityTemporaryScoreboardTag(dPly, coolDownTag, 30);
                                // damage setup
                                HashMap<String, Double> fireballAttrMap = (HashMap<String, Double>) AttributeHelper.getAttrMap(damager).clone();
                                double fireballDmg = Math.min(90, fireballAttrMap.getOrDefault("damage", 100d) * 0.15);
                                fireballAttrMap.put("damage", fireballDmg);

                                String projType = "小熔岩火球";
                                double projSpd = 1.75;
                                org.bukkit.util.Vector aimDir = ItemUseHelper.getPlayerAimDir(dPly, dPly.getEyeLocation(),
                                        projSpd, projType, false, 0);
                                double aimYaw = MathHelper.getVectorYaw(aimDir);
                                double aimPitch = MathHelper.getVectorPitch(aimDir);
                                // projectiles
                                for (int i = 0; i < 3; i++) {
                                    Vector projVel = MathHelper.vectorFromYawPitch_approx(
                                            aimYaw + Math.random() * 10 - 5, aimPitch + Math.random() * 10 - 5);
                                    projVel.multiply(projSpd);
                                    EntityHelper.spawnProjectile(dPly, projVel,
                                            fireballAttrMap, DamageType.MELEE, projType);
                                }
                            }
                            break;
                        }
                        case "血炎近战套装":
                        case "金源近战套装": {
                            if (damageType == DamageType.TRUE_MELEE)
                                EntityHelper.applyEffect(dPly, "鲜血狂怒", 100);
                            break;
                        }
                    }
                    break;
                }
            }
        }
        return true;
    }

    public static void dropHeart(Location loc) {
        ItemStack heart = new ItemStack(Material.CLAY_BALL);
        ItemMeta meta = heart.getItemMeta();
        meta.setDisplayName("§c§l心");
        heart.setItemMeta(meta);
        ItemHelper.dropItem(loc, heart, false);
    }

    public static void handleDeath(Entity v, Entity dPly, Entity d, DamageType damageType, DamageReason damageReason, String debuffType) {
        if (v instanceof Player) {
            Player vPly = (Player) v;
            // prevent spectator getting in a wall & prevent speed remaining after revive
            EntityMovementHelper.setVelocity(vPly, new Vector());
            // respawn time, default to 5 seconds and increases if boss is alive
            int respawnTime = 5;
            for (ArrayList<LivingEntity> bossList : BossHelper.bossMap.values()) {
                HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targets =
                        (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>)
                                EntityHelper.getMetadata(bossList.get(0), EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
                // if the current boss has the player as target, 15 seconds respawn time for each player, up to 1 minute
                if (targets.containsKey(vPly.getUniqueId())) {
                    // note: max would take the longest respawn time across all active bosses
                    respawnTime = Math.max(respawnTime, Math.min(targets.size() * 15, 60));
                }
            }
            // drop money
            long moneyDrop = (long) Math.floor(PlayerHelper.getMoney(vPly) / 100);
            moneyDrop = (long) Math.ceil(moneyDrop * 0.75);
            if (PlayerHelper.hasPiggyBank(vPly)) {
                moneyDrop = Math.min(moneyDrop, 1104);
            }
            moneyDrop *= 100;
            PlayerHelper.setMoney(vPly, PlayerHelper.getMoney(vPly) - moneyDrop);
            GenericHelper.dropMoney(vPly.getEyeLocation(), moneyDrop, false);
            // death message
            String moneyMsg = "";
            if (moneyDrop > 0) {
                moneyMsg = "§c§l掉了";
                long[] moneyConverted = GenericHelper.coinConversion(moneyDrop, false);
                if (moneyConverted[0] > 0)
                    moneyMsg += "§c§l " + moneyConverted[0] + "§f§l 铂";
                if (moneyConverted[1] > 0)
                    moneyMsg += "§c§l " + moneyConverted[1] + "§e§l 金";
                if (moneyConverted[2] > 0)
                    moneyMsg += "§c§l " + moneyConverted[2] + "§7§l 银";
                if (moneyConverted[3] > 0)
                    moneyMsg += "§c§l " + moneyConverted[3] + "§c§l 铜";
            }
            String deathTitle = "你死了！";
            if ( ItemHelper.splitItemName( vPly.getInventory().getItemInMainHand() )[1].equals("雷姆的复仇") )
                deathTitle = "感谢款待！";
            vPly.sendTitle("§c§l" + deathTitle, moneyMsg, 0, respawnTime * 20, 0);
            sendDeathMessage(d, v, damageType, damageReason, debuffType);
            // remove vanilla potion effects except for mining fatigue
            // terraria potion effects are removed in their threads
            for (PotionEffect effect : vPly.getActivePotionEffects()) {
                if (effect.getType() != PotionEffectType.SLOW_DIGGING)
                    vPly.removePotionEffect(effect.getType());
            }
            // initialize respawn countdown and other features
            vPly.closeInventory();
            double maxHealth = vPly.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double respawnHealth = Math.max(400, maxHealth / 2);
            // make sure the new health do not exceed maximum health
            vPly.setHealth(Math.min(respawnHealth, maxHealth) );
            EntityHelper.setMetadata(vPly, EntityHelper.MetadataName.RESPAWN_COUNTDOWN, respawnTime * 20);
            vPly.setGameMode(GameMode.SPECTATOR);
            vPly.setFlySpeed(0);
            vPly.setFallDistance(0);
            // further respawn ticking mechanism in regen thread
        } else {
            // when an entity is killed by a player
            Set<String> vScoreboardTags = v.getScoreboardTags();
            if (dPly instanceof Player) {
                Player dPlayer = (Player) dPly;
                String victimName = GenericHelper.trimText(v.getName());
                // special enemy death handling
                switch (victimName) {
                    // the Hive Mind
                    case "腐化囊": {
                        BossHelper.spawnBoss((Player) dPly, BossHelper.BossType.THE_HIVE_MIND);
                        break;
                    }
                    // anahita and leviathan spawns after killing ???
                    case "???": {
                        BossHelper.spawnBoss((Player) dPly, BossHelper.BossType.LEVIATHAN_AND_ANAHITA);
                        break;
                    }
                    // empress of light
                    case "七彩草蛉": {
                        BossHelper.spawnBoss((Player) dPly, BossHelper.BossType.EMPRESS_OF_LIGHT);
                        break;
                    }
                    // lunatic cultist spawns after killing the mob in the dungeon
                    case "拜月教教徒": {
                        BossHelper.spawnBoss((Player) dPly, BossHelper.BossType.LUNATIC_CULTIST, v.getLocation());
                        break;
                    }
                    // drop a lot of hearts
                    case "礼物宝箱怪": {
                        // randomly drop 4-8 hearts
                        for (int i = (int) (Math.random() * 5); i < 8; i ++)
                            dropHeart(v.getLocation());
                        break;
                    }
                }
                // drop stars
                switch (damageType) {
                    case MAGIC:
                    case SPECTRE:
                        if (v.getScoreboardTags().contains("isMonster")) {
                            for (int i = 0; i < 3; i ++)
                                if (Math.random() < 0.1) {
                                    ItemStack star = new ItemStack(Material.CLAY_BALL);
                                    ItemMeta meta = star.getItemMeta();
                                    meta.setDisplayName("§9§l星");
                                    star.setItemMeta(meta);
                                    ItemHelper.dropItem(v.getLocation(), star, false);
                                }
                        }
                }
                // drop heart
                if (dPlayer.getHealth() < dPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() &&
                        vScoreboardTags.contains("isMonster") && Math.random() < 0.2) {
                    dropHeart(v.getLocation());
                }
            }
            // set health/directly remove
            if (v instanceof LivingEntity) ((LivingEntity) v).setHealth(0);
            else v.remove();
            // NPC should also get death message
            if (vScoreboardTags.contains("isNPC"))
                sendDeathMessage(d, v, damageType, damageReason, debuffType);
            // monster generic drop, event etc.
            else if (vScoreboardTags.contains("isMonster")) {
                LivingEntity vLiving = (LivingEntity) v;
                MetadataValue spawnEvt = EntityHelper.getMetadata(v, EntityHelper.MetadataName.SPAWN_IN_EVENT);
                // event monster
                if (spawnEvt != null && spawnEvt.value() == EventAndTime.currentEvent ) {
                    HashMap<EventAndTime.EventInfoMapKeys, Double> eventInfo = EventAndTime.eventInfo;
                    if (eventInfo.getOrDefault(EventAndTime.EventInfoMapKeys.IS_INVASION, 1d) > 0) {
                        MetadataValue progress = EntityHelper.getMetadata(v, EntityHelper.MetadataName.KILL_CONTRIBUTE_EVENT_PROGRESS);
                        if (progress != null) {
                            double invadeProgress = eventInfo.getOrDefault(EventAndTime.EventInfoMapKeys.INVADE_PROGRESS, 0d);
                            eventInfo.put(EventAndTime.EventInfoMapKeys.INVADE_PROGRESS,
                                    invadeProgress + progress.asDouble());
                        }
                    }
                }
                // generic death drop etc.
                MetadataValue parentTypeMetadata = EntityHelper.getMetadata(v, EntityHelper.MetadataName.MONSTER_PARENT_TYPE);
                MetadataValue bossTypeMetadata = EntityHelper.getMetadata(v, EntityHelper.MetadataName.BOSS_TYPE);
                String parentType = "";
                if (bossTypeMetadata != null)
                    parentType = bossTypeMetadata.value().toString();
                if (parentTypeMetadata != null)
                    parentType = parentTypeMetadata.asString();

                switch (parentType) {
                    // lava slimes leave lava at death
                    case "史莱姆": {
                        if (v.getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD)) {
                            Location deathLoc = vLiving.getEyeLocation();
                            Block deathBlock = deathLoc.getBlock();
                            WorldHelper.createTemporaryLava(deathBlock);
                        }
                        break;
                    }
                    // the hungry flies towards the player after breaking free
                    case "饿鬼Attached": {
                        if (dPly instanceof Player) {
                            MonsterHelper.spawnMob("饿鬼", v.getLocation(), (Player) dPly);
                            v.remove();
                        }
                        break;
                    }
                }
                // special, player-progression specific drops
                if (dPly instanceof Player) {
                    Player dPlayer = (Player) dPly;
                    // dungeon souls; not applicable for boss parts
                    if (! vScoreboardTags.contains("isBOSS") &&
                            WorldHelper.BiomeType.getBiome(dPlayer) == WorldHelper.BiomeType.DUNGEON) {
                        switch (parentType) {
                            case "地牢幽魂":
                            case "幻魂":
                                break;
                            default:
                                if (PlayerHelper.hasDefeated(dPlayer, BossHelper.BossType.PLANTERA) &&
                                        Math.random() < 0.175)
                                    MonsterHelper.spawnMob("地牢幽魂", v.getLocation(), dPlayer);
                                else if (PlayerHelper.hasDefeated(dPlayer, BossHelper.BossType.MOON_LORD) &&
                                        Math.random() < 0.25)
                                    MonsterHelper.spawnMob("幻魂", v.getLocation(), dPlayer);
                        }
                    }
                    // souls and essences
                    if (PlayerHelper.hasDefeated(dPlayer, BossHelper.BossType.WALL_OF_FLESH)) {
                        if (!v.getScoreboardTags().contains("isBOSS")) {
                            Location deathLoc = v.getLocation();
                            WorldHelper.BiomeType biome = WorldHelper.BiomeType.getBiome(deathLoc);
                            switch (biome) {
                                case HALLOW: // hallow
                                case CORRUPTION: // corruption
                                {
                                    WorldHelper.HeightLayer height = WorldHelper.HeightLayer.getHeightLayer(deathLoc);
                                    if (height != WorldHelper.HeightLayer.CAVERN)
                                        return;
                                    double dropChance = (255 - v.getLocation().getY()) / 125;
                                    if (Math.random() < dropChance)
                                        ItemHelper.dropItem(vLiving.getEyeLocation(),
                                                biome == WorldHelper.BiomeType.HALLOW ? "光明之魂" : "暗影之魂", false);
                                    break;
                                }
                                case TUNDRA: // tundra
                                {
                                    double dropChance = 0.25;
                                    if (Math.random() < dropChance)
                                        ItemHelper.dropItem(vLiving.getEyeLocation(),
                                                "冰川精华", false);
                                    break;
                                }
                                case SPACE: // space
                                {
                                    double dropChance = 0.25;
                                    if (Math.random() < dropChance)
                                        ItemHelper.dropItem(vLiving.getEyeLocation(),
                                                "日光精华", false);
                                    break;
                                }
                                case BRIMSTONE_CRAG: // brimstone crag
                                {
                                    double dropChance = 0.25;
                                    if (Math.random() < dropChance)
                                        ItemHelper.dropItem(vLiving.getEyeLocation(),
                                                "混乱精华", false);
                                    break;
                                }
                            }
                        }
                    }
                    // post-DoG essences
                    if (spawnEvt != null && PlayerHelper.hasDefeated(dPlayer, BossHelper.BossType.THE_DEVOURER_OF_GODS)) {
                        String itemType = null;
                        int dropAmountMin = 1, dropAmountMax = 1;
                        double dropChance = 0d;
                        EventAndTime.Events evt = (EventAndTime.Events) spawnEvt.value();
                        switch (evt) {
                            case PUMPKIN_MOON:
                                itemType = "梦魇魔能";
                                switch (parentType) {
                                    case "树精":
                                        dropChance = 0.5d;
                                        break;
                                    case "地狱犬":
                                    case "胡闹鬼":
                                        dropChance = 0.5d;
                                        dropAmountMax = 2;
                                        break;
                                    case "无头骑士":
                                        dropChance = 1d;
                                        dropAmountMin = 3;
                                        dropAmountMax = 5;
                                        break;
                                    case "哀木":
                                        dropChance = 1d;
                                        dropAmountMin = 5;
                                        dropAmountMax = 10;
                                        break;
                                    case "南瓜王":
                                        dropChance = 1d;
                                        dropAmountMin = 10;
                                        dropAmountMax = 20;
                                        break;
                                }
                                break;
                            case FROST_MOON:
                                itemType = "恒温能量";
                                switch (parentType) {
                                    case "胡桃夹士":
                                    case "精灵直升机":
                                    case "雪花怪":
                                        dropChance = 0.5d;
                                        break;
                                    case "坎卜斯":
                                    case "雪兽":
                                    case "礼物宝箱怪":
                                        dropChance = 0.5d;
                                        dropAmountMax = 2;
                                        break;
                                    case "常绿尖叫怪":
                                        dropChance = 1d;
                                        dropAmountMin = 3;
                                        dropAmountMax = 5;
                                        break;
                                    case "圣诞坦克":
                                        dropChance = 1d;
                                        dropAmountMin = 5;
                                        dropAmountMax = 10;
                                        break;
                                    case "冰雪女王":
                                        dropChance = 1d;
                                        dropAmountMin = 10;
                                        dropAmountMax = 20;
                                        break;
                                }
                                break;
                            case SOLAR_ECLIPSE:
                                itemType = "日蚀之阴碎片";
                                switch (parentType) {
                                    case "水月怪":
                                    case "弗里茨":
                                    case "沼泽怪":
                                    case "科学怪人":
                                        dropChance = 0.1d;
                                        break;
                                    case "死神":
                                    case "吸血鬼":
                                    case "攀爬魔":
                                    case "致命球":
                                        dropChance = 0.5d;
                                        break;
                                    case "眼怪":
                                        dropChance = 1d;
                                        dropAmountMax = 2;
                                        break;
                                    case "蛾怪":
                                        dropChance = 1d;
                                        dropAmountMin = 20;
                                        dropAmountMax = 30;
                                        break;
                                }
                                break;
                        }
                        // drop
                        if (itemType != null && Math.random() < dropChance) {
                            int dropAmountFinal = dropAmountMin + (int) (Math.random() * (dropAmountMax - dropAmountMin + 1));
                            ItemHelper.dropItem(vLiving.getEyeLocation(),
                                    itemType + ":" + dropAmountFinal, false);
                        }
                    }

                }
            }
        }
    }

    public static boolean checkCanDamage(Entity entity, Entity target) {
        return checkCanDamage(entity, target, true);
    }

    public static boolean checkCanDamage(Entity entity, Entity target, boolean strict) {
        // dead target
        if (target.isDead()) return false;
        // store scoreboard tags as it is requested frequently below
        Entity damageTaker = getDamageTaker(target);
        Set<String> targetScoreboardTags = target.getScoreboardTags();
        Set<String> damageTakerScoreboardTags = damageTaker.getScoreboardTags();
        Set<String> entityScoreboardTags = entity.getScoreboardTags();
        Entity damageSource = getDamageSource(entity);
        if (damageTakerScoreboardTags.contains("isPillar")) {
            MetadataValue temp = EntityHelper.getMetadata(damageTaker, EntityHelper.MetadataName.CELESTIAL_PILLAR_SHIELD);
            if (temp != null && temp.asInt() > 0) return false;
        }
        if (!(damageTaker instanceof LivingEntity)) {
            if (damageTaker instanceof Projectile) {
                if (AttributeHelper.getAttrMap(damageTaker).containsKey("health")) {
                    ProjectileSource src = ((Projectile) damageTaker).getShooter();
                    if (src instanceof Entity) {
                        damageTaker = (Entity) src;
                        // can damage shooter (no strict checking needed):
                        // if strict mode return false(so homing weapons do not home to enemy projectiles)
                        // if not strict mode return true(so enemy can be damaged)
                        if (checkCanDamage(entity, damageTaker, false)) return !strict;
                    }
                }
            }
            return false;
        }
        // only players not fighting any boss can damage armor stand
        if (damageTaker instanceof ArmorStand) {
            return damageSource instanceof Player && ! PlayerHelper.isTargetedByBOSS((Player) damageSource);
        }
        // invulnerable target
        if (damageTaker.isInvulnerable()) return false;
        if (damageTakerScoreboardTags.contains("noDamage") || targetScoreboardTags.contains("noDamage")) return false;
        // fallen star etc. can damage players and NPC etc. without further check
        if (entityScoreboardTags.contains("ignoreCanDamageCheck")) {
            // for pvp damage, it still requires the later validations.
            if (! (damageSource instanceof Player && target instanceof Player && damageSource != target) )
                return true;
        }
        // can not attack oneself
        if (damageSource == damageTaker) return false;
        // check details about damage source and victim
        entityScoreboardTags = damageSource.getScoreboardTags();
        if (damageSource instanceof Player) {
            if (!PlayerHelper.isProperlyPlaying((Player) damageSource)) return false;
            // only both players are in pvp mode and the check is not strict
            // so homing weapons and minions will not target other players
            if (damageTaker instanceof Player)
                return (!strict) && (damageTakerScoreboardTags.contains("PVP") && entityScoreboardTags.contains("PVP"));
            else {
                if (targetScoreboardTags.contains("isMonster")) return true;
                // homing weapons and minions should not willingly attack critters and NPCs (with voodoo doll)
                if (strict) return false;
                HashSet<String> accessories = PlayerHelper.getAccessories(damageSource);
                if (damageTakerScoreboardTags.contains("isNPC"))
                    return accessories.contains(damageTaker.getName() + "巫毒娃娃");
                if (damageTakerScoreboardTags.contains("isAnimal"))
                    return (!PlayerHelper.hasCritterGuide((Player) damageSource));
                // entities that are not animal, NPC or monster can be damaged but are not targeted actively
                return true;
            }
        }
        // non-player attacks player
        else if (damageTaker instanceof Player) {
            HashSet<String> accessories = PlayerHelper.getAccessories(damageTaker);
            Player damageTakerPly = (Player) damageTaker;
            if (PlayerHelper.isProperlyPlaying(damageTakerPly)) {
                // handle special parent type (slime damage neglected by royal gel)
                MetadataValue temp = EntityHelper.getMetadata(damageSource, EntityHelper.MetadataName.MONSTER_PARENT_TYPE);
                if (temp != null) {
                    String parentType = temp.asString();
                    if (parentType.equals("史莱姆") && accessories.contains("皇家凝胶"))
                        return false;
                }
                // NPCs do not attack players
                return !entityScoreboardTags.contains("isNPC");
            }
            // players that are logged out are not supposed to get hurt.
            else {
                return false;
            }
        }
        // non-player attacks non-player
        else {
            // monster attack target: npc and critters can be damaged by accident but not targeted on purpose
            if (entityScoreboardTags.contains("isMonster")) {
                if (damageTakerScoreboardTags.contains("isNPC") || damageTakerScoreboardTags.contains("isAnimal"))
                    return !strict;
                return false;
            }
            // NPC/minion -> monster: true
            if (targetScoreboardTags.contains("isMonster"))
                return entityScoreboardTags.contains("isNPC") || entityScoreboardTags.contains("isMinion");
            return false;
        }
    }

    public static Entity getDamageSource(Entity damager) {
        Entity source = damager;
        if (source instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) source).getShooter();
            if (shooter instanceof Entity) source = (Entity) shooter;
        }
        MetadataValue damageSourceMetadata = EntityHelper.getMetadata(source, EntityHelper.MetadataName.DAMAGE_SOURCE);
        if (damageSourceMetadata != null)
            source = (Entity) damageSourceMetadata.value();
        return source;
    }

    public static Entity getDamageTaker(Entity victim) {
        Entity taker = victim;
        MetadataValue mdv = EntityHelper.getMetadata(victim, EntityHelper.MetadataName.DAMAGE_TAKER);
        if (mdv != null)
            taker = (Entity) mdv.value();
        return taker;
    }

    public static String getInvulnerabilityTickName(DamageType damageType) {
        return "tempDamageCD_" + damageType;
    }

    private static void playDamageSound(Location playLoc, String sound, float volume) {
        HashMap<String, Long> soundPlayed;
        double audibleDistSqr = volume * 16;
        audibleDistSqr *= audibleDistSqr;
        for (Player ply : playLoc.getWorld().getPlayers()) {
            if (ply.getLocation().distanceSquared(playLoc) > audibleDistSqr)
                continue;
            // get last played time
            MetadataValue mdv = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_DAMAGE_SOUND_MEMO);
            if (mdv == null) {
                soundPlayed = new HashMap<>();
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_DAMAGE_SOUND_MEMO, soundPlayed);
            }
            else {
                soundPlayed = (HashMap<String, Long>) mdv.value();
            }
            // do not play again within 100 ms
            if (soundPlayed.getOrDefault(sound, 0L) + 100 > System.currentTimeMillis())
                continue;
            // play the sound
            ply.playSound(playLoc, sound, SoundCategory.HOSTILE, volume, 1f);
            soundPlayed.put(sound, System.currentTimeMillis());
        }
    }

    // tracks the DPS and display the damage info to the player
    private static void trackDPS(Player src, String victimName, double health, double maxHealth, double dmg, boolean beginOrEnd) {
        // update variables
        int hits = EntityHelper.getMetadata(src, EntityHelper.MetadataName.DPS_HITS).asInt();
        double dmgTotal = EntityHelper.getMetadata(src, EntityHelper.MetadataName.DPS_DMG_TOTAL).asDouble();
        int recordSign = beginOrEnd ? 1 : -1;
        hits += recordSign;
        dmgTotal += dmg * recordSign;
        EntityHelper.setMetadata(src, EntityHelper.MetadataName.DPS_HITS, hits);
        EntityHelper.setMetadata(src, EntityHelper.MetadataName.DPS_DMG_TOTAL, dmgTotal);
        // send message etc.
        if (beginOrEnd) {
            int secInterval = Setting.getOptionInt(src, Setting.Options.DPS_DURATION);
            double dps = dmgTotal / secInterval;
            PlayerHelper.sendActionBar(src,
                    String.format("§r%s §6[§a%.0f§6/§a%.0f§6] §b(-%.0f) §6[§a%d秒内%d次共%.0f§d|§a%.1f§dDPS§6]",
                            victimName, health, maxHealth, dmg, secInterval, hits, dmgTotal, dps));
            // plan to remove the info
            Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(),
                    () -> trackDPS(src, victimName, health, maxHealth, dmg, false), secInterval * 20);
        }
    }

    public static void handleDamage(Entity damager, Entity victim, double damage, DamageReason damageReason) {
        handleDamage(damager, victim, damage, damageReason, null);
    }

    public static void handleDamage(Entity damager, Entity victim, double damage, DamageReason damageReason, String debuffType) {
        // damaging a mount counts as directly damaging its owner.
        if (victim.getScoreboardTags().contains("isMount")) {
            victim = getDamageTaker(victim);
        }

        HashMap<String, Double> victimAttrMap = AttributeHelper.getAttrMap(victim);
        Entity damageSource = getDamageSource(damager);
        // projectile etc
        if (!(victim instanceof LivingEntity)) {
            if (victimAttrMap.containsKey("health")) {
                double health = victimAttrMap.get("health") - damage;
                double maxHealth = victimAttrMap.getOrDefault("healthMax", health);
                victimAttrMap.put("health", health);
                if (health < 0) {
                    victim.remove();
                    health = 0;
                }
                if (damageSource instanceof Player)
                    trackDPS((Player) damageSource, victim.getName(), health, maxHealth, damage, true);
            }
            return;
        }

        // living entities
        LivingEntity victimLivingEntity = (LivingEntity) victim;
        LivingEntity damageTaker = (LivingEntity) getDamageTaker(victim);
        if (damageTaker.getHealth() <= 0)
            return;
        Set<String> damageTakerTags = damageTaker.getScoreboardTags();
        Set<String> victimTags = victim.getScoreboardTags();

        // no damage scenarios
        boolean canDamage = true;
        if (damageTakerTags.contains("isMinion")) canDamage = false;
        else if (damageTakerTags.contains("noDamage") || victimTags.contains("noDamage")) canDamage = false;
        else if (victim.isInvulnerable()) canDamage = false;
        else if (victim instanceof Player && ((Player) victim).getGameMode() != GameMode.SURVIVAL) canDamage = false;
        else if (victimLivingEntity.getHealth() <= 0) canDamage = false;
        // check minion damage
        boolean isMinionDmg = false;
        boolean isDirectAttackDamage = damageReason.isDirectDamage();
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity && ((Entity) shooter).getScoreboardTags().contains("isMinion"))
                isMinionDmg = true;
        } else if (damager.getScoreboardTags().contains("isMinion")) isMinionDmg = true;
        // setup properties such as invulnerability tick and defence
        int damageInvulnerabilityTicks;
        double defence = victimAttrMap.getOrDefault("defence", 0d) * victimAttrMap.getOrDefault("defenceMulti", 1d);
        if (damageTakerTags.contains("isBOSS")) {
            if (isDirectAttackDamage) {
                MetadataValue bossTargets = EntityHelper.getMetadata(victim, EntityHelper.MetadataName.BOSS_TARGET_MAP);
                boolean canProperlyDamage = false;
                if (damageSource instanceof Player) {
                    if ( bossTargets == null ||
                            (((HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) (bossTargets.value()))
                                    .containsKey(damageSource.getUniqueId())) )
                        canProperlyDamage = true;
                }
                // damage source of a boss is not a target
                if (!canProperlyDamage)
                    defence = 999999999;
            } else {
                // if the damage source of a boss is neither direct attack, thorn nor debuff (that is, lava etc.)
                // the damage is ignored.
                if (! ((damageReason == DamageReason.DEBUFF) || (damageReason == DamageReason.THORN)))
                    canDamage = false;
            }
        }
        if (!canDamage) return;

        // setup damage type and damager attribute
        HashMap<String, Double> damagerAttrMap = AttributeHelper.getAttrMap(damager);
        DamageType damageType = damageReason.getDamageType();
        if (isDirectAttackDamage) {
            // if no mandatory damage type for the damage reason, default to the attacker(can be a projectile) damage type
            if (damageType == null)
                damageType = getDamageType(damager);
            if (damageReason == DamageReason.STRIKE && damageType == DamageType.MELEE)
                damageType = DamageType.TRUE_MELEE;
        }
        // if the victim has invincibility frame on this damage type (usually player)
        String damageInvincibilityFrameName = getInvulnerabilityTickName(damageType);
        if (victimTags.contains(damageInvincibilityFrameName)) return;


        HashSet<String> damagerAccessories = PlayerHelper.getAccessories(damageSource);
        HashSet<String> victimAccessories = PlayerHelper.getAccessories(victim);
        // inflict debuff
        if (isDirectAttackDamage) {
            List<String> buffInflict = new ArrayList<>();
            // projectile buff inflict
            if (damager instanceof Projectile) {
                buffInflict.addAll(TerrariaHelper.projectileConfig.getStringList(damager.getName() + ".buffInflict"));
            }
            // monster/minion direct damage buff inflict
            else if (! (damager instanceof Player)) {
                buffInflict.addAll(TerrariaHelper.entityConfig.getStringList(
                        GenericHelper.trimText(damager.getName()) + ".buffInflict"));
            }
            // player buff inflict
            if (damageSource instanceof Player) {
                // player minion buff inflict
                HashMap<String, ArrayList<String>> buffInflictMap = PlayerHelper.getPlayerEffectInflict(damageSource);
                buffInflict.addAll(buffInflictMap.getOrDefault("buffInflict", new ArrayList<>(0)));
                switch (damageType) {
                    case ARROW:
                    case BULLET:
                    case ROCKET:
                        buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictRanged", new ArrayList<>(0)));
                        break;
                    case TRUE_MELEE:
                        buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictMelee", new ArrayList<>(0)));
                        buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictTrueMelee", new ArrayList<>(0)));
                        break;
                    // Melee, Magic, Summon, Rogue
                    default:
                        // whip
                        if (damageType == DamageType.SUMMON && damageReason == DamageReason.STRIKE)
                            buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictMelee", new ArrayList<>(0)));
                        buffInflict.addAll(buffInflictMap.getOrDefault("buffInflict" + damageType, new ArrayList<>(0)));
                }
            }
            // apply (de)buff(s) to victim
            for (String buff : buffInflict) {
                String[] buffInfo = buff.split("\\|");
                double chance;
                try {
                    chance = Double.parseDouble(buffInfo[2]);
                    if (Math.random() < chance)
                        EntityHelper.applyEffect(victim, buffInfo[0], Integer.parseInt(buffInfo[1]));
                } catch (Exception ignored) {
                }
            }
        }

        // further setup damage info (fixed amount etc.); damage setup for special damage types
        damageInvulnerabilityTicks = victimAttrMap.getOrDefault("invulnerabilityTick", 0d).intValue();
        double dmg = damage;
        double knockback = 0d, critRate = -1e9;
        boolean damageFixed = false;
        switch (damageReason) {
            case FEAR:
            case BOSS_ANGRY:
                dmg = 114514;
                damageFixed = true;
                break;
            case FALL:
                double fallDist = victim.getFallDistance();
                dmg = (fallDist - 12.5) * 20;
                if (dmg < 0) return;
                if (EntityHelper.hasEffect(victim, "钙质")) return;
                break;
            case BLOCK_EXPLOSION:
                knockback = 5;
                dmg = 1000;
                break;
            case LAVA:
                if (victim.getLocation().getBlock().getBiome() == Biome.SAVANNA)
                    EntityHelper.applyEffect(victim, "硫磺火", 150);
                else
                    EntityHelper.applyEffect(victim, "燃烧", 150);
                // mainly prevents excessive damage dealt to enemies
                damageInvulnerabilityTicks = Math.max(damageInvulnerabilityTicks, 30);
                break;
            case DROWNING:
                damageFixed = true;
                damageInvulnerabilityTicks = 0;
                break;
            case SUFFOCATION:
                damageInvulnerabilityTicks = 0;
                dmg = 10;
                damageFixed = true;
                break;
            case THORN:
                damageInvulnerabilityTicks = 0;
                damageFixed = true;
                break;
            default:
                if (damageReason == DamageReason.DEBUFF) {
                    damageInvulnerabilityTicks = 0;
                    damageFixed = true;
                } else if (isDirectAttackDamage) {
                    // special minion whip etc
                    if (isMinionDmg) {
                        if (victimTags.contains("鞭炮")) {
                            victim.removeScoreboardTag("鞭炮");
                            dmg *= 2.75;
                            victim.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, victim.getLocation(), 1);
                            victim.getWorld().playSound(victim.getLocation(), "entity.generic.explode", SoundCategory.HOSTILE,1f, 1f);
                        }
                    }
                    knockback = damagerAttrMap.getOrDefault("knockback", 0d);
                    knockback *= damagerAttrMap.getOrDefault("knockbackMulti", 1d);
                    // no percentage dmg for spectre; critical strike only.
                    boolean canGetPercentageBonus = !(damageReason == DamageReason.SPECTRE);
                    if (canGetPercentageBonus)
                        dmg *= damagerAttrMap.getOrDefault("damageMulti", 1d);
                    critRate = damagerAttrMap.getOrDefault("crit", 0d);
                    switch (damageType) {
                        case TRUE_MELEE:
                            if (canGetPercentageBonus)
                                dmg *= damagerAttrMap.getOrDefault("damageTrueMeleeMulti", 1d);
                            critRate += damagerAttrMap.getOrDefault("critTrueMelee", 0d);
                        case MELEE:
                            if (canGetPercentageBonus)
                                dmg *= damagerAttrMap.getOrDefault("damageMeleeMulti", 1d);
                            critRate += damagerAttrMap.getOrDefault("critMelee", 0d);
                            knockback *= damagerAttrMap.getOrDefault("knockbackMeleeMulti", 1d);
                            break;
                        case ARROW:
                        case BULLET:
                        case ROCKET:
                            if (canGetPercentageBonus) {
                                dmg *= damagerAttrMap.getOrDefault("damage" + damageType + "Multi", 1d);
                                dmg *= damagerAttrMap.getOrDefault("damageRangedMulti", 1d);
                            }
                            critRate += damagerAttrMap.getOrDefault("critRanged", 0d);
                            break;
                        case ROGUE:
                            if (canGetPercentageBonus) {
                                dmg *= damagerAttrMap.getOrDefault("damageRogueMulti", 1d);
                            }
                            critRate += damagerAttrMap.getOrDefault("critRogue", 0d);
                            break;
                        case MAGIC:
                        case SPECTRE:
                            critRate += damagerAttrMap.getOrDefault("critMagic", 0d);
                        case SUMMON:
                            if (canGetPercentageBonus)
                                dmg *= damagerAttrMap.getOrDefault("damage" + damageType + "Multi", 1d);
                            break;
                        default:
                            TerrariaHelper.LOGGER.log(Level.SEVERE, "Unhandled damage type: " + damageType);
                            return;
                    }
                } else {
                    TerrariaHelper.LOGGER.log(Level.SEVERE, "Unhandled damage reason: " + damageReason);
                    return;
                }
        }

        // tweak damage, including minion whip bonus, accessories such as paladin shield, random damage floating and crit
        boolean crit = false;
        if (!damageFixed) {
            double damageTakenMulti = victimAttrMap.getOrDefault("damageTakenMulti", 1d);
            switch (damageType) {
                case MELEE:
                case TRUE_MELEE:
                    damageTakenMulti *= victimAttrMap.getOrDefault("damageContactTakenMulti", 1d);
                    if (EntityHelper.hasEffect(victim, "血肉图腾"))
                        EntityHelper.applyEffect(victim, "血肉图腾", 0);
            }
            // extra tweak on damage when victim is not a player
            if (!(victim instanceof Player)) {
                // minion damage effects and whips
                if (isMinionDmg) {
                    MetadataValue temp;
                    temp = EntityHelper.getMetadata(victim, EntityHelper.MetadataName.MINION_WHIP_BONUS_DAMAGE);
                    double dmgBonus = temp != null ? temp.asDouble() : 0;
                    dmg += dmgBonus;
                    temp = EntityHelper.getMetadata(victim, EntityHelper.MetadataName.MINION_WHIP_BONUS_CRIT);
                    critRate += temp != null ? temp.asDouble() : 0;
                    // on-hit effects from accessory
                    if (damagerAccessories.contains("幻魂神物")) {
                        double rdm = Math.random();
                        if (rdm < 0.3333)
                            EntityHelper.applyEffect(damageSource, "幻魂还生", 20);
                        else if (rdm < 0.6666)
                            EntityHelper.applyEffect(damageSource, "幻魂坚盾", 20);
                        else
                            EntityHelper.applyEffect(damageSource, "幻魂之力", 20);
                    }
                    else if (damagerAccessories.contains("神圣符文")) {
                        double rdm = Math.random();
                        if (rdm < 0.3333)
                            EntityHelper.applyEffect(damageSource, "神圣之辉", 20);
                        else if (rdm < 0.6666)
                            EntityHelper.applyEffect(damageSource, "神圣之佑", 20);
                        else
                            EntityHelper.applyEffect(damageSource, "神圣之力", 20);
                    }
                    else if (damagerAccessories.contains("灵魂浮雕")) {
                        double rdm = Math.random();
                        if (rdm < 0.3333)
                            EntityHelper.applyEffect(damageSource, "灵魂恢复", 20);
                        else if (rdm < 0.6666)
                            EntityHelper.applyEffect(damageSource, "灵魂防御", 20);
                        else
                            EntityHelper.applyEffect(damageSource, "灵魂力量", 20);
                    }
                }
                // special enemy damage tweak
                switch (GenericHelper.trimText( victim.getName() ) ) {
                    case "猪鲨公爵":
                    case "硫海遗爵":
                    case "利维坦":
                        // the damager is the flail
                        if (damager.getName().equals("雷姆的复仇"))
                            dmg *= 8;
                        break;

                }
                // accessories
                if (damageSource instanceof Player) {
                    Player damageSourcePly = (Player) damageSource;
                    for (String plyAcc : damagerAccessories) {
                        switch (plyAcc) {
                            case "魔能过载仪":
                            case "魔能熔毁仪": {
                                if (getDamageType(damageSourcePly) == DamageType.MAGIC) {
                                    int mana = damageSourcePly.getLevel();
                                    double consumption;
                                    double manaToDamageRate;
                                    double consumptionRatio;
                                    if (plyAcc.equals("魔能过载仪")) {
                                        consumption = 2;
                                        manaToDamageRate = 50;
                                        consumptionRatio = 1;
                                    }
                                    else {
                                        consumption = (int) Math.max(mana * 0.035, 5);
                                        manaToDamageRate = 25;
                                        double effectDuration = EntityHelper.getEffectMap(damageSourcePly).getOrDefault("魔力熔蚀", 0);
                                        // at 20 second = 400 ticks, mana use reduced by roughly 100%
                                        consumptionRatio = 1 - Math.pow(effectDuration / 400, 0.25);
                                    }
                                    if (ItemUseHelper.consumeMana(damageSourcePly, MathHelper.randomRound(consumption * consumptionRatio) )) {
                                        dmg += consumption * manaToDamageRate;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                // crit to non-player victims
                if (Math.random() * 100 < critRate) {
                    crit = true;
                    dmg *= 1 + (damagerAttrMap.getOrDefault("critDamage", 1d) / 100);
                }
            }
            // extra tweak on damage when victim is a player
            else {
                // crit to player pre-defence due to blood pact
                if (victimAccessories.contains("血契") && Math.random() < 0.25) {
                    crit = true;
                    dmg *= 1.75;
                }

                // paladin shield, only applies to player victims
                if (! EntityHelper.hasEffect(victim, "圣骑士护盾")) {
                    String team = EntityHelper.getMetadata(victim, EntityHelper.MetadataName.PLAYER_TEAM).asString();
                    // works with players within 96 blocks
                    double dist = 9216;
                    Entity shieldPly = null;
                    for (Player ply : victim.getWorld().getPlayers()) {
                        if (!PlayerHelper.isProperlyPlaying(ply)) continue;
                        if (!EntityHelper.hasEffect(ply, "圣骑士护盾")) continue;
                        String currTeam = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_TEAM).asString();
                        if (!(currTeam.equals(team))) continue;
                        double currDist = ply.getLocation().distanceSquared(victim.getLocation());
                        if (currDist >= dist) continue;
                        dist = currDist;
                        shieldPly = ply;
                    }
                    if (shieldPly != null) {
                        handleDamage(damager, shieldPly, (dmg * 0.05), DamageReason.THORN);
                        dmg *= 0.85;
                    }
                }
            }
            dmg *= damageTakenMulti;
            defence = Math.max(defence - damagerAttrMap.getOrDefault("armorPenetration", 0d), 0);
            dmg -= defence;
            // interrupt barrier regen - 10 seconds when damaged while interrupted
            if (EntityHelper.hasEffect(victim, "保护矩阵充能")) {
                EntityHelper.applyEffect(victim, "保护矩阵充能", 200);
            }
            // damage barrier damage reduction & interruption
            if (EntityHelper.hasEffect(victim, "保护矩阵")) {
                // 20 ticks = 10 dmg
                int damageShield = EntityHelper.getEffectMap(victim).get("保护矩阵") / 2;
                int damageBlock = (int) Math.min(Math.ceil(dmg), damageShield);
                dmg -= damageBlock;
                EntityHelper.applyEffect(victim, "保护矩阵", (damageShield - damageBlock) * 2);
                // interrupt barrier regen - 8 seconds when damaged with barrier
                EntityHelper.applyEffect(victim, "保护矩阵充能", 160);
            }
            if (EntityHelper.hasEffect(victim, "狮心圣裁能量外壳")) {
                EntityHelper.applyEffect(victim, "狮心圣裁能量外壳冷却", 900);
            }
            // random damage offset
            dmg *= Math.random() * 0.3 + 0.85;

            // boss damage reduction
            if (damageTakerTags.contains("isBOSS")) {
                double dynamicDR = 1;
                MetadataValue temp = EntityHelper.getMetadata(victim, EntityHelper.MetadataName.DYNAMIC_DAMAGE_REDUCTION);
                if (temp != null) dynamicDR = temp.asDouble();
                BossHelper.BossType type = (BossHelper.BossType) EntityHelper.getMetadata(victim, EntityHelper.MetadataName.BOSS_TYPE).value();
                if (damageSource instanceof Player &&  ! PlayerHelper.hasDefeated((Player) damageSource, type) )
                    dmg *= dynamicDR;
            }
            // NPC damage reduction ( for non-fixed damage, the damage is decreased by a factor of 4, and is upper capped at 50
            else if (damageTakerTags.contains("isNPC")) {
                dmg = Math.min(dmg / 4d, 50);
            }
        }
        // round damage
        dmg = Math.round(dmg);
        if (dmg < 1.5) dmg = crit ? 2 : 1;
        // for some entities that locks health at a specific value
        MetadataValue healthLockMetadata = EntityHelper.getMetadata(damageTaker, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT);
        if (healthLockMetadata != null) {
            double healthLock = healthLockMetadata.asDouble();
            if (damageTaker.getHealth() > healthLock) {
                dmg = Math.min(damageTaker.getHealth() - (healthLock + 1e-5), dmg);
            }
            else {
                dmg = 0;
            }
        }

        // call damage event
        if (! entityDamageEvent(damager, damageSource, victimLivingEntity, damageTaker, dmg, damage, damageType, damageReason))
            return;

        // damage/kill
        if (victim instanceof ArmorStand) {
            // players can break the armor stand by damaging it with a pickaxe
            if (damageSource instanceof Player && AttributeHelper.getAttrMap(damageSource).getOrDefault("powerPickaxe", 0d) > 0d) {
                // check for break permission
                if (GameplayHelper.isBreakable(victim.getLocation().getBlock().getRelative(BlockFace.UP), (Player) damageSource)) {
                    LivingEntity victimLivingE = (LivingEntity) victim;
                    victimLivingE.remove();
                    // drop the armor stand itself and its equipment
                    ItemHelper.dropItem(victimLivingE.getEyeLocation(), "盔甲架");
                    for (ItemStack item : victimLivingE.getEquipment().getArmorContents() ) {
                        if (item == null || item.getType() == Material.AIR)
                            continue;
                        ItemHelper.dropItem(victimLivingE.getEyeLocation(), item);
                    }
                }
            }
        }
        else {
            float soundVolume = 3f;
            // register damage dealt; bosses have louder damage sound
            if (damageTakerTags.contains("isBOSS") && damageSource instanceof Player) {
                soundVolume = 8f;
                MetadataValue temp = EntityHelper.getMetadata(damageTaker, EntityHelper.MetadataName.BOSS_TARGET_MAP);
                if (temp != null) {
                    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targets =
                            (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) temp.value();
                    Player ply = (Player) damageSource;
                    UUID plyID = ply.getUniqueId();
                    if (targets.containsKey(plyID))
                        targets.get(plyID).addDamageDealt(dmg);
                }
            }
            String sound = null;
            // kills the target
            if (damageTaker.getHealth() <= dmg) {
                handleDeath(damageTaker, damageSource, damager, damageType, damageReason, debuffType);
                if (victimTags.contains("isMechanic")) sound = "entity.generic.explode";
                else sound = "entity." + damageTaker.getType() + ".death";
                sound = TerrariaHelper.entityConfig.getString(GenericHelper.trimText(damageTaker.getName()) + ".soundDamaged", sound);
                sound = TerrariaHelper.entityConfig.getString(GenericHelper.trimText(damageTaker.getName()) + ".soundKilled", sound);
            }
            // hurts the target
            else {
                damageTaker.setHealth(Math.max(0, damageTaker.getHealth() - dmg));
                if (!(damageType == DamageType.DEBUFF)) {
                    // if damage cause is not debuff, play hurt sound
                    if (victimTags.contains("isMechanic")) sound = "entity.irongolem.hurt";
                    else sound = "entity." + damageTaker.getType() + ".hurt";
                    sound = TerrariaHelper.entityConfig.getString(GenericHelper.trimText(damageTaker.getName()) + ".soundDamaged", sound);
                }
            }
            // fall damage sound
            if (damageReason == DamageReason.FALL) {
                if (dmg < 200)
                    sound = "entity.generic.fallSmall";
                else
                    sound = "entity.generic.fallBig";
            }
            // play sound
            if (sound != null)
                playDamageSound(victim.getLocation(), sound, soundVolume);
            // knockback
            if (knockback > 0) {
                Vector vec = victim.getLocation().subtract(damager.getLocation()).toVector();
//                double kbForce = knockback / 20;
                double sqrtKB = Math.sqrt(knockback);
                double kbForce = sqrtKB / 8;
                MathHelper.setVectorLength(vec, kbForce);
                // for non-downward knockback, amplify the upward component and push the victim off ground
                if (victim.isOnGround() && vec.getY() > -1e-3)
                    vec.setY(vec.getY() + Math.min(sqrtKB / 4, 1));
                EntityHelper.knockback(victim, vec, false);
            }
        }
        // remove the not damaged marker scoreboard tag
        if (isDirectAttackDamage)
            victim.removeScoreboardTag("notDamaged");

        // display damage
        boolean displayDmg = true;
        switch (damageReason) {
            case SUFFOCATION:
            case DROWNING:
                displayDmg = false;
        }
        if (displayDmg) {
            String hologramInfo;
            if (damageType == DamageType.DEBUFF)
                hologramInfo = "Debuff_" + debuffType;
            else
                hologramInfo = damageType.toString();
            GenericHelper.displayHolo(victim, dmg, crit, hologramInfo);
        }

        // track DPS for the damager player
        if (damageSource instanceof Player && damageTaker != damageSource) {
            String vName = victim.getName();
            double maxHealth = damageTaker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            trackDPS((Player) damageSource, vName, damageTaker.getHealth(), maxHealth, dmg, true);
        }

        // handle invincibility ticks
        EntityHelper.handleEntityTemporaryScoreboardTag(damageTaker, damageInvincibilityFrameName, damageInvulnerabilityTicks);
    }


}
