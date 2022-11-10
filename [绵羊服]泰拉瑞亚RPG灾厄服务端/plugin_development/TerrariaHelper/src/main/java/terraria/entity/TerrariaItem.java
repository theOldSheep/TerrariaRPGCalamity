package terraria.entity;

import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.util.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;


public class TerrariaItem extends EntityItem {
    static final int
            BOOSTER_LIVE_TIME = 600,
            HEART_LIVE_TIME = 1200,
            GENERIC_LIVE_TIME = 6000;
    static final double maxPickupRadius = 27.5;
    public int liveTime, baseRarity, lastTick, age;
    public String itemType;
    public static final YmlHelper.YmlSection itemConfig = YmlHelper.getFile("plugins/Data/items.yml");;
    public TerrariaItem (org.bukkit.Location loc, org.bukkit.inventory.ItemStack item) {
        super(((CraftWorld) loc.getWorld()).getHandle(),
                loc.getX(), loc.getY(), loc.getZ(),
                CraftItemStack.asNMSCopy(item));
        this.lastTick = MinecraftServer.currentTick - 1;
        pickupDelay = 20;
    }

    public double getPickUpDistance(EntityPlayer p) {
        Player ply = p.getBukkitEntity();
        Set<String> accessories = PlayerHelper.getAccessories(ply);
        HashMap<String, Integer> potionEffects = EntityHelper.getEffectMap(ply);
        double reach = 5;
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
                reach += 6.25;
                break;
            case "心":
                if (potionEffects.containsKey("拾心"))
                    reach += 15.625;
                break;
            case "星":
                if (accessories.contains("天界磁石") || accessories.contains("天界徽章") || accessories.contains("天界手铐") || accessories.contains("磁花"))
                    reach += 18.75;
                break;
        }
        return reach;
    }
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
        EntityPlayer nearest = null;
        // if the item can be picked up, find the player that will pick it up.
        if (this.pickupDelay <= 0) {
            Iterator<EntityPlayer> iterator = this.world.a(EntityPlayer.class, this.getBoundingBox().grow(maxPickupRadius, maxPickupRadius, maxPickupRadius)).iterator();

            while(iterator.hasNext()) {
                EntityPlayer p = iterator.next();
                if (p.playerInteractManager.getGameMode() != EnumGamemode.SURVIVAL) continue;
                if (p.getScoreboardTags().contains("unauthorized")) continue;
                double distX = p.locX - this.locX;
                double distY = p.locY - this.locY;
                double distZ = p.locZ - this.locZ;
                double distSqr = distX * distX + distY * distY + distZ * distZ;
                double pickUpDist = getPickUpDistance(p);
                double pickupAbility = 1 - (distSqr / (pickUpDist * pickUpDist));
                if (greatestPickupAbility < pickupAbility) {
                    greatestPickupAbility = distSqr;
                    nearest = p;
                }
            }
        }
        // modify velocity
        double d0 = this.motX;
        double d1 = this.motY;
        double d2 = this.motZ;
        Vector currVelocity = new Vector(this.motX, this.motY, this.motZ);
        if (nearest != null) {
            double speed = currVelocity.length() + 0.1;
            this.noclip = true;
            Vector acceleration = new Vector(nearest.locX - this.locX, nearest.locY - this.locY, nearest.locZ - this.locZ);
            terraria.util.MathHelper.setVectorLengthSquared(
                    currVelocity, acceleration.length());
            currVelocity.add(acceleration);
            double newSpeed = currVelocity.length();
            if (newSpeed > speed) currVelocity.multiply(speed / newSpeed);
        } else {
            // no player is picking it up
            // if it has gravity: fall to the ground
            if (!this.isNoGravity())
                currVelocity.add(new Vector(0, -0.03999999910593033, 0));
            this.noclip = this.i(this.locX, (this.getBoundingBox().b + this.getBoundingBox().e) / 2.0, this.locZ);
        }
        if (currVelocity.lengthSquared() > 1) currVelocity.normalize();
        this.motX = currVelocity.getX();
        this.motY = currVelocity.getY();
        this.motZ = currVelocity.getZ();

        this.move(EnumMoveType.SELF, this.motX, this.motY, this.motZ);

        // slow down the item slightly
        float f = 0.95F;
        if (nearest == null) {
            // if it is on ground and nobody would pick it up, slow down according to friction factor
            // if it has no gravity and nobody would pick it up, slow it down at a much faster rate
            if (this.onGround) {
                f *= this.world.getType(new BlockPosition(MathHelper.floor(this.locX), MathHelper.floor(this.getBoundingBox().b) - 1, MathHelper.floor(this.locZ))).getBlock().frictionFactor;
            }
            else if (this.isNoGravity()) f = 0.75F;
        }
        this.motX *= f;
        this.motY *= f;
        this.motZ *= f;
        // nobody is picking it up: stays on the ground
        if (this.onGround && nearest == null) {
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
        // those items should be glowing
        switch (itemInfo[1]) {
            case "坠星":
            case "落星":
            case "坠落之星":
                glowing = true;
        }
        // those items should have no gravity
        switch (itemInfo[1]) {
            case "生命强化焰":
            case "伤害强化焰":
            case "魔力强化焰":
            case "光明之魂":
            case "暗影之魂":
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
        baseRarity = itemConfig.getInt(GenericHelper.trimText(getItemStack().getName()) + ".rarity", 0);
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
            // TODO: spawn wall of flesh
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
        int remaining = PlayerHelper.addItemToPlayerInventory(CraftItemStack.asBukkitCopy(itemstack), (Player) entityhuman.getBukkitEntity());
        if (remaining > 0)
            itemstack.setCount(remaining);
        else {
            this.die();
            itemstack.setCount(0);
        }
    }
}
