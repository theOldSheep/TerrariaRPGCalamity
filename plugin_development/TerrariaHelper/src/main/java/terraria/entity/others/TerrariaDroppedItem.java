package terraria.entity.others;

import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.*;

import java.util.HashMap;
import java.util.Set;


public class TerrariaDroppedItem extends EntityItem {
    public static final YmlHelper.YmlSection itemConfig = YmlHelper.getFile(
            TerrariaHelper.Constants.DATA_FOLDER_DIR + "items.yml");
    // boosters live for 30 seconds, heart for 60 seconds, and generic for 300 seconds (5 minutes)
    static final int
            // 30 seconds
            BOOSTER_LIVE_TIME = 600,
            // 15 seconds
            HEART_LIVE_TIME = 300,
            // 1.5 minutes (90 seconds)
            GENERIC_LIVE_TIME = 1800;
    public int liveTime, baseRarity, lastTick, age;
    public String itemType;
    public org.bukkit.inventory.ItemStack bukkitItemStack;
    public boolean canBeMerged = true, hasNoGravity;
    boolean lastTickNoGravity = false;
    EntityPlayer pickedUpBy = null;
    public TerrariaDroppedItem(World world) {
        super(world);
        die();
    }
    public TerrariaDroppedItem(org.bukkit.Location loc, org.bukkit.inventory.ItemStack item) {
        super(((CraftWorld) loc.getWorld()).getHandle(),
                loc.getX(), loc.getY(), loc.getZ(),
                CraftItemStack.asNMSCopy(item));
        bukkitItemStack = item.clone();
        canBeMerged = canMerge();
        this.lastTick = MinecraftServer.currentTick - 1;
        pickupDelay = 20;
    }

    public double getPickUpDistance(EntityPlayer p) {
        return getPickUpDistance(p.getBukkitEntity());
    }
    public double getPickUpDistance(Player ply) {
        Set<String> accessories = PlayerHelper.getAccessories(ply);
        HashMap<String, Integer> potionEffects = EntityHelper.getEffectMap(ply);
        double reach = 2.75;
        switch (itemType) {
            case "铜币":
            case "银币":
            case "金币":
            case "铂金币":
                reach = 3;
                break;
            case "生命强化焰":
            case "伤害强化焰":
            case "魔力强化焰":
                reach = 22.5;
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
        if (potionEffects.containsKey("无尽空虚")) {
            reach += 12.5;
            reach *= 2;
        }
        else if (potionEffects.containsKey("磁铁")) {
            reach += 9;
        }
        return reach;
    }
    protected boolean canPickUp(Player ply) {
        // players that are not properly playing (waiting for revive etc.) should not be considered
        if (!PlayerHelper.isProperlyPlaying(ply))
            return false;
        // players in a different world would not be considered
        if (ply.getWorld() != bukkitEntity.getWorld())
            return false;
        // calculate distance
        double distSqr = ply.getLocation().distanceSquared(bukkitEntity.getLocation());
        // get pick up distance
        double pickUpDistSqr = getPickUpDistance(ply);
        pickUpDistSqr *= pickUpDistSqr;
        // players too far away should not be considered
        return distSqr < pickUpDistSqr;
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
    public boolean mergeWith(TerrariaDroppedItem itemToMerge) {
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
            } else if (itemstack1.getCount() + itemstack.getCount() > itemstack1.getMaxStackSize()) {
                return false;
            } else if (itemstack1.getCount() < itemstack.getCount()) {
                return itemToMerge.mergeWith(this);
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
    public void merge(boolean ignorePickupDelay) {
        if (pickupDelay > 0 && !ignorePickupDelay) return;
        if (!canBeMerged) return;
        // do not bother handle dropped items that are fully stacked
        if (bukkitItemStack.getAmount() < bukkitItemStack.getMaxStackSize()) {
            // only merge twice per second, to reduce lag
            if (ticksLived % 10 == 0)
                for (TerrariaDroppedItem toMerge : this.world.a(TerrariaDroppedItem.class, this.getBoundingBox().grow(3, 2, 3))) {
                    this.mergeWith(toMerge);
                }
        }
    }
    @Override
    public Entity b(int i) {
        Entity entity = super.b(i);
        if (!this.world.isClientSide && entity instanceof TerrariaDroppedItem)
            ((TerrariaDroppedItem)entity).merge(false);
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

        boolean currTickNoGravity = hasNoGravity;
        // find the player that could pick it up
        double greatestPickupAbility = 0;
        // if the item can be picked up, find the player that will pick it up.
        if (this.pickupDelay <= 0 && ticksLived % 5 == 0) {
            // validate if the pick-up target is valid
            if (pickedUpBy != null) {
                if (! canPickUp(pickedUpBy.getBukkitEntity()) )
                    pickedUpBy = null;
            }
            // update if not currently picked up
            if (pickedUpBy == null) {
                for (Player ply : Bukkit.getOnlinePlayers()) {
                    if (canPickUp(ply)) {
                        // calculate squared distance
                        double distSqr = ply.getLocation().distanceSquared(bukkitEntity.getLocation());
                        // calculate pick up ability ( how close it is to the item )
                        double pickUpDistSqr = getPickUpDistance(ply);
                        pickUpDistSqr *= pickUpDistSqr;
                        double pickupAbility = 1 - (distSqr / pickUpDistSqr);
                        if (greatestPickupAbility < pickupAbility) {
                            // player can not hold new item should not be considered
                            if (!PlayerHelper.canHoldAny(ply, bukkitItemStack)) continue;
                            greatestPickupAbility = distSqr;
                            pickedUpBy = ((CraftPlayer) ply).getHandle();
                        }
                    }
                }
            }
        }
        // modify velocity
        double d0 = this.motX;
        double d1 = this.motY;
        double d2 = this.motZ;
        Vector currVelocity = new Vector(this.motX, this.motY, this.motZ);
        if (pickedUpBy != null) {
            // when being picked up, item should be unaffected by gravity
            currTickNoGravity = true;

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
        }
        // no player is picking it up
        else {
            // if it has gravity: fall to the ground
            if (! hasNoGravity)
                currVelocity.add(new Vector(0, -0.04, 0));
            // below: do not use noclip from vanilla, it creates glitch when dropped item trapped in blocks
            this.noclip = false;
//            this.noclip = this.i(this.locX, (this.getBoundingBox().b + this.getBoundingBox().e) / 2.0, this.locZ);
        }
        // normalize speed
        if (currVelocity.lengthSquared() > 1.5)
            terraria.util.MathHelper.setVectorLength(currVelocity, 1.5);
        this.motX = currVelocity.getX();
        this.motY = currVelocity.getY();
        this.motZ = currVelocity.getZ();
        // handle gravity
        if (currTickNoGravity != lastTickNoGravity)
            setNoGravity(currTickNoGravity);

        // movement tick
        this.move(EnumMoveType.SELF, this.motX, this.motY, this.motZ);
        this.merge(false);

        // slow down the item slightly
        float f = 0.95F;
        if (pickedUpBy == null) {
            // if it is on ground and nobody would pick it up, slow down according to friction factor
            // if it has no gravity and nobody would pick it up, slow it down at a much faster rate
            if (this.onGround) {
                f *= this.world.getType(new BlockPosition(MathHelper.floor(this.locX), MathHelper.floor(this.getBoundingBox().b) - 1, MathHelper.floor(this.locZ))).getBlock().frictionFactor;
            }
            else if (hasNoGravity) f = 0.75F;
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
        // items in terraria shall never "burn" on fire
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
            case "星尘":
            case "妖精尘":
            case "诅咒焰":
            case "光明之魂":
            case "暗影之魂":
            case "飞翔之魂":
            case "力量之魂":
            case "视域之魂":
            case "恐惧之魂":
            case "日光精华":
            case "冰川精华":
            case "混乱精华":
            case "灵气":
            case "日耀碎片":
            case "星璇碎片":
            case "星云碎片":
            case "星尘碎片":
            case "冥思溶剂":
            case "浊火精华":
            case "灵质":
            case "暗离子体":
            case "扭曲虚空":
            case "毁灭之灵":
            case "恒温能量":
            case "梦魇魔能":
            case "日蚀之阴碎片":
            case "化神魂精":
            case "龙魂碎片":
            case "湮灭余烬":
                hasNoGravity = true;
                this.setNoGravity(true);
                break;
            default:
                hasNoGravity = false;
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
        baseRarity = ItemHelper.getItemRarityFromFullDescription(getItemStack().getName());
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
