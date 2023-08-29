package terraria.entity.others;

import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.*;

import java.util.HashMap;
import java.util.Set;


public class TerrariaItem extends EntityItem {
    public static final YmlHelper.YmlSection itemConfig = YmlHelper.getFile(
            TerrariaHelper.Constants.DATA_FOLDER_DIR + "items.yml");
    // boosters live for 30 seconds, heart for 60 seconds, and generic for 300 seconds (5 minutes)
    static final int
            BOOSTER_LIVE_TIME = 600,
            HEART_LIVE_TIME = 1200,
            GENERIC_LIVE_TIME = 3000;
    static final double maxPickupRadius = 27.5;
    public int liveTime, baseRarity, lastTick, age;
    public String itemType;
    public org.bukkit.inventory.ItemStack bukkitItemStack;
    public boolean canBeMerged;
    EntityPlayer pickedUpBy = null;
    public TerrariaItem (World world) {
        super(world);
        die();
    }
    public TerrariaItem (org.bukkit.Location loc, org.bukkit.inventory.ItemStack item) {
        super(((CraftWorld) loc.getWorld()).getHandle(),
                loc.getX(), loc.getY(), loc.getZ(),
                CraftItemStack.asNMSCopy(item));
        bukkitItemStack = item.clone();
        canBeMerged = canMerge();
        this.lastTick = MinecraftServer.currentTick - 1;
        pickupDelay = 20;
    }

    public double getPickUpDistance(EntityPlayer p) {
        Player ply = p.getBukkitEntity();
        Set<String> accessories = PlayerHelper.getAccessories(ply);
        HashMap<String, Integer> potionEffects = EntityHelper.getEffectMap(ply);
        double reach = 3;
        switch (itemType) {
            case "铜币":
            case "银币":
            case "金币":
            case "铂金币":
                if (accessories.contains("金戒指") || accessories.contains("钱币戒指") || accessories.contains("贪婪戒指"))
                    reach += 21.875;
                break;
            case "生命强化焰":
            case "伤害强化焰":
            case "魔力强化焰":
                reach += 17.5;
                break;
            case "心":
                if (potionEffects.containsKey("拾心"))
                    reach += 15.625;
                break;
            case "星":
                if (accessories.contains("天界磁石") ||
                        accessories.contains("天界徽章") ||
                        accessories.contains("天界手铐") ||
                        accessories.contains("磁花") ||
                        accessories.contains("空灵护符") ||
                        accessories.contains("灾厄符章"))
                    reach += 18.75;
                break;
        }
        return reach;
    }
    public boolean canMerge() {
        switch (itemType) {
            case "生命强化焰":
            case "伤害强化焰":
            case "魔力强化焰":
            case "心":
            case "星":
                return false;
            default:
                return true;
        }
    }
    // merging
    public boolean mergeWith(TerrariaItem itemToMerge) {
        // function modified from EntityItem.class
        if (itemToMerge == this) {
            return false;
        } else if (itemToMerge.isAlive() && this.isAlive()) {
            ItemStack itemstack = this.getItemStack();
            ItemStack itemstack1 = itemToMerge.getItemStack();

            if (itemstack1.getItem() != itemstack.getItem()) {
                return false;
            } else if (itemstack1.hasTag() ^ itemstack.hasTag()) {
                return false;
            } else if (itemstack1.hasTag() && !itemstack1.getTag().equals(itemstack.getTag())) {
                return false;
            } else if (itemstack1.getItem() == null) {
                return false;
            } else if (itemstack1.getItem().k() && itemstack1.getData() != itemstack.getData()) {
                return false;
            } else if (itemstack1.getCount() < itemstack.getCount()) {
                return itemToMerge.mergeWith(this);
            } else if (itemstack1.getCount() + itemstack.getCount() > itemstack1.getMaxStackSize()) {
                return false;
            } else if (CraftEventFactory.callItemMergeEvent(this, itemToMerge).isCancelled()) {
                return false;
            } else {
                itemstack1.add(itemstack.getCount());
                itemToMerge.pickupDelay = Math.max(itemToMerge.pickupDelay, this.pickupDelay);
                itemToMerge.age = Math.min(itemToMerge.age, this.age);
                itemToMerge.setItemStack(itemstack1);
                this.die();
                return true;
            }
        } else {
            return false;
        }
    }
    public void merge() {
        if (pickupDelay > 0) return;
        if (!canBeMerged) return;
        for (TerrariaItem toMerge : this.world.a(TerrariaItem.class, this.getBoundingBox().grow(3, 2, 3))) {
            this.mergeWith(toMerge);
        }
    }
    @Override
    public Entity b(int i) {
        Entity entity = super.b(i);
        if (!this.world.isClientSide && entity instanceof TerrariaItem)
            ((TerrariaItem)entity).merge();
        return entity;
    }
    // generic ticking
    @Override
    public void B_() {
        this.world.methodProfiler.a("entityBaseTick");
        // pickup delay and live time
        int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
        this.pickupDelay -= elapsedTicks;
        if (this.pickupDelay < 0) this.pickupDelay = 0;
        this.age += elapsedTicks;
        if (this.age > liveTime) {
            if (CraftEventFactory.callItemDespawnEvent(this).isCancelled())
                this.age = 0;
            else
                this.die();
        }
        this.lastTick = MinecraftServer.currentTick;

        // find the player that could pick it up
        double greatestPickupAbility = 0;
        // if the item can be picked up, find the player that will pick it up.
        if (this.pickupDelay <= 0 && ticksLived % 5 == 0) {
            pickedUpBy = null;
            for (EntityPlayer p : this.world.a(EntityPlayer.class, this.getBoundingBox().grow(maxPickupRadius, maxPickupRadius, maxPickupRadius))) {
                if (p.playerInteractManager.getGameMode() != EnumGamemode.SURVIVAL) continue;
                if (p.getScoreboardTags().contains("unauthorized")) continue;
                double distX = p.locX - this.locX;
                double distY = p.locY - this.locY;
                double distZ = p.locZ - this.locZ;
                double distSqr = distX * distX + distY * distY + distZ * distZ;
                double pickUpDistSqr = getPickUpDistance(p);
                pickUpDistSqr *= pickUpDistSqr;
                if (distSqr > pickUpDistSqr) continue;
                double pickupAbility = 1 - (distSqr / pickUpDistSqr);
                if (greatestPickupAbility < pickupAbility) {
                    // player can not hold new item
                    if (!PlayerHelper.canHoldAny(p.getBukkitEntity(), bukkitItemStack)) continue;
                    greatestPickupAbility = distSqr;
                    pickedUpBy = p;
                }
            }
        }
        // modify velocity
        double d0 = this.motX;
        double d1 = this.motY;
        double d2 = this.motZ;
        Vector currVelocity = new Vector(this.motX, this.motY, this.motZ);
        if (pickedUpBy != null) {
            double speed = currVelocity.length() + 0.1;
            this.noclip = true;
            Vector acceleration = new Vector(pickedUpBy.locX - this.locX, pickedUpBy.locY - this.locY, pickedUpBy.locZ - this.locZ);
            double accelerationLength = acceleration.length();
            // this item is very close to the player
            if (accelerationLength < speed) currVelocity = acceleration;
            else {
                if (currVelocity.lengthSquared() > 0)
                    terraria.util.MathHelper.setVectorLengthSquared(currVelocity, accelerationLength);
                currVelocity.add(acceleration);
                double newSpeed = currVelocity.length();
                if (newSpeed > speed && newSpeed > 0) currVelocity.multiply(speed / newSpeed);
            }
        } else {
            // no player is picking it up
            // if it has gravity: fall to the ground
            if (!this.isNoGravity())
                currVelocity.add(new Vector(0, -0.03999999910593033, 0));
            // below: do not use noclip from vanilla, creating glitch when dropped item trapped in blocks
//            this.noclip = this.i(this.locX, (this.getBoundingBox().b + this.getBoundingBox().e) / 2.0, this.locZ);
            this.noclip = false;
        }
        if (currVelocity.lengthSquared() > 1) currVelocity.normalize();
        this.motX = currVelocity.getX();
        this.motY = currVelocity.getY();
        this.motZ = currVelocity.getZ();

        this.move(EnumMoveType.SELF, this.motX, this.motY, this.motZ);
        this.merge();

        // slow down the item slightly
        float f = 0.95F;
        if (pickedUpBy == null) {
            // if it is on ground and nobody would pick it up, slow down according to friction factor
            // if it has no gravity and nobody would pick it up, slow it down ticksBeforeHookingFish a much faster rate
            if (this.onGround) {
                f *= this.world.getType(new BlockPosition(MathHelper.floor(this.locX), MathHelper.floor(this.getBoundingBox().b) - 1, MathHelper.floor(this.locZ))).getBlock().frictionFactor;
            }
            else if (this.isNoGravity()) f = 0.75F;
        }
        this.motX *= f;
        this.motY *= f;
        this.motZ *= f;
        // nobody is picking it up: stays on the ground
        if (this.onGround && pickedUpBy == null) {
            this.motY *= -0.5;
        }
        // tick water
        this.aq();
        // mark speed change
        double d3 = this.motX - d0;
        double d4 = this.motY - d1;
        double d5 = this.motZ - d2;
        double d6 = d3 * d3 + d4 * d4 + d5 * d5;
        if (d6 > 1e-7) {
            this.impulse = true;
        }

        // modified base tick mechanism from Entity class
        if (this.j > 0) {
            --this.j;
        }

        this.I = this.J;
        this.lastX = this.locX;
        this.lastY = this.locY;
        this.lastZ = this.locZ;
        this.lastPitch = this.pitch;
        this.lastYaw = this.yaw;
        // items in terraria shall never burn
        this.extinguish();
        this.setFlag(0, false);

        if (this.au())
            this.burnFromLava();

        if (this.locY < -64.0) {
            this.ac();
        }

        this.justCreated = false;
        // timing
        this.world.methodProfiler.b();
    }


    @Override
    protected void burn(int i) {}
    @Override
    public void setItemStack(ItemStack itemstack) {
        super.setItemStack(itemstack);

        String[] itemInfo = ItemHelper.splitItemName(getItemStack().getName());
        itemType = itemInfo[1];
        // those items should have no gravity
        switch (itemInfo[1]) {
            case "生命强化焰":
            case "伤害强化焰":
            case "魔力强化焰":
            case "光明之魂":
            case "暗影之魂":
            case "飞翔之魂":
            case "日光精华":
            case "冰川精华":
            case "混乱精华":
            case "日耀碎片":
            case "星璇碎片":
            case "星云碎片":
            case "星尘碎片":
            case "冥思溶剂":
                this.setNoGravity(true);
        }
        // setup live time
        switch (itemInfo[1]) {
            case "生命强化焰":
            case "伤害强化焰":
            case "魔力强化焰":
                liveTime = BOOSTER_LIVE_TIME;
                break;
            case "心":
            case "星":
                liveTime = HEART_LIVE_TIME;
                break;
            default:
                liveTime = GENERIC_LIVE_TIME;
        }
        baseRarity = itemConfig.getInt(ItemHelper.splitItemName(getItemStack().getName())[1] + ".rarity", 0);
        this.fireProof = baseRarity > 0;
        bukkitItemStack = CraftItemStack.asBukkitCopy(itemstack);
    }

    @Override
    public boolean damageEntity(DamageSource damagesource, float f) {
        if (damagesource.equals(DamageSource.CACTUS)) return false;
        if (baseRarity > 0) {
            if (damagesource.equals(DamageSource.FIRE)) return false;
            if (damagesource.equals(DamageSource.BURN)) return false;
            if (damagesource.equals(DamageSource.LAVA)) return false;
        }
        String itemName = ItemHelper.splitItemName(getItemStack().getName())[1];
        if (itemName.equals("向导巫毒娃娃")) {
            BossHelper.spawnBoss(null, BossHelper.BossType.WALL_OF_FLESH, bukkitEntity.getLocation());
        }
        this.a(SoundEffects.bR, 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
        die();
        return false;
    }
    // pickup
    @Override
    public void d(EntityHuman entityhuman) {
        if (pickupDelay > 0) return;
        ItemStack itemstack = this.getItemStack();
        int remaining = PlayerHelper.giveItem((Player) entityhuman.getBukkitEntity(), bukkitItemStack, false);
        // call the event to update item tag amount
        PlayerPickupItemEvent playerEvent = new PlayerPickupItemEvent((Player)entityhuman.getBukkitEntity(), (org.bukkit.entity.Item)this.getBukkitEntity(), remaining);
        this.world.getServer().getPluginManager().callEvent(playerEvent);

        if (remaining > 0)
            itemstack.setCount(remaining);
        else {
            this.die();
            itemstack.setCount(0);
        }
    }
}
