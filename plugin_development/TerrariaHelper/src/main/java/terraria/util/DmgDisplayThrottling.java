package terraria.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import terraria.TerrariaHelper;

import java.util.Comparator;
import java.util.function.Consumer;

/**
 * A task that is throttled is created once and used since then.
 * It is initialized with:
 * - a logic to utilize the most "relevant" data
 * - the delay for which the throttling is executed after the first acceptance since idle
 * - the relevance comparator
 * </br>
 * furthermore, if delay is non-positive the throttling is not applied.
 */
public class DmgDisplayThrottling<T> {
    private static final Logger log = LoggerFactory.getLogger(DmgDisplayThrottling.class);
    T extra = null;
    Consumer<T> logic;
    int ticksDelay;
    Comparator<T> relevanceComparator;
    boolean idle = true;
    public DmgDisplayThrottling(Consumer<T> logic, int ticksDelay, Comparator<T> relevanceComparator) {
        this.logic = logic;
        this.ticksDelay = ticksDelay;
        this.relevanceComparator = relevanceComparator;
    }
    public boolean accept(T newObj) {
        // delay <= 0 - equivalent to no throttling
        if (ticksDelay <= 0) {
            logic.accept(newObj);
            return true;
        }
        boolean accepted = false;
        // schedule the execution if needed
        if (idle) {
            idle = false;
            Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(), () -> {
                try {
                    logic.accept(extra);
                } finally {
                    idle = true;
                    extra = null;
                }
            }, ticksDelay);
        }
        // update if needed
        if (extra == null) {
            accepted = true;
        }
        else if (relevanceComparator.compare(newObj, extra) > 0) {
            accepted = true;
        }
        if (accepted) {
            extra = newObj;
        }
        return accepted;
    }

    // use scenario - damage hologram
    public static final int DMG_HOLOGRAM_INTERVAL = TerrariaHelper.optimizationConfig.getInt("optimization.dmgHologramInterval", 1);

    public static class DmgHologramContext {
        public double dmg;
        public boolean crit;
        public String ctx;
        public DmgHologramContext(double dmg, boolean crit, String ctx) {
            this.dmg = dmg;
            this.crit = crit;
            this.ctx = ctx;
        }
        public double getDmg() {
            return dmg;
        }
    }

    // initialization helper functions
    public static DmgDisplayThrottling<DmgHologramContext> getDmgHoloThrottle(Entity victim) {
        MetadataValue mdv = EntityHelper.getMetadata(victim, EntityHelper.MetadataName.THROTTLE_DMG_HOLOGRAM);
        DmgDisplayThrottling<DmgHologramContext> result;
        if (mdv != null) {
            result = (DmgDisplayThrottling<DmgHologramContext>) mdv.value();
        }
        else {
            result = new DmgDisplayThrottling<>(
                    (data) -> GenericHelper.displayHolo(victim, data.dmg, data.crit, data.ctx),
                    DMG_HOLOGRAM_INTERVAL,
                    Comparator.comparingDouble(DmgHologramContext::getDmg));
            EntityHelper.setMetadata(victim, EntityHelper.MetadataName.THROTTLE_DMG_HOLOGRAM, result);
        }
        return result;
    }

    // use scenario - DPS display
    public static final int DPS_DISPLAY_INTERVAL = TerrariaHelper.optimizationConfig.getInt("optimization.dpsDisplayInterval", 1);

    public static class DpsDisplayContext {
        public String msg;
        private long timestamp;
        public DpsDisplayContext(String msg) {
            this.msg = msg;
            this.timestamp = System.currentTimeMillis();
        }
        public long getTimestamp() {
            return this.timestamp;
        }
    }

    // initialization helper functions
    public static DmgDisplayThrottling<DpsDisplayContext> getDpsDisplayThrottle(Player ply) {
        MetadataValue mdv = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.THROTTLE_DPS_ACTION_BAR);
        DmgDisplayThrottling<DpsDisplayContext> result;
        if (mdv != null) {
            result = (DmgDisplayThrottling<DpsDisplayContext>) mdv.value();
        }
        else {
            result = new DmgDisplayThrottling<>(
                    (data) -> PlayerHelper.sendActionBar(ply, data.msg),
                    DPS_DISPLAY_INTERVAL,
                    Comparator.comparingDouble(DpsDisplayContext::getTimestamp));
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.THROTTLE_DPS_ACTION_BAR, result);
        }
        return result;
    }
}
