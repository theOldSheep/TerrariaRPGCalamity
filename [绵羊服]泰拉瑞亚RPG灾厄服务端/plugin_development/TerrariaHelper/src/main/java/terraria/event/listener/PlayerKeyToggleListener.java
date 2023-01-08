package terraria.event.listener;

import eos.moe.dragoncore.api.event.KeyPressEvent;
import eos.moe.dragoncore.api.event.KeyReleaseEvent;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.omg.CORBA.TypeCodePackage.BadKind;
import terraria.util.EntityHelper;
import terraria.util.ItemUseHelper;
import terraria.util.PlayerHelper;

import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;

public class PlayerKeyToggleListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyReleaseEvent(KeyReleaseEvent e) {
        Player ply = e.getPlayer();
        HashSet<String> allKeysPressed = (HashSet<String>) EntityHelper.getMetadata(ply, "keysPressed").value();
        String keyReleased = e.getKey();
        allKeysPressed.remove(keyReleased);
        // space bar
        switch (keyReleased) {
            case "SPACE":
            case "ALL":
                ply.removeScoreboardTag("temp_thrusting");
                break;
        }
        if (keyReleased.equals("ALL")) {
            allKeysPressed.clear();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyPressEvent(KeyPressEvent e) {
        Player ply = e.getPlayer();
        HashSet<String> allKeysPressed = (HashSet<String>) EntityHelper.getMetadata(ply, "keysPressed").value();
        String keyPressed = e.getKey();
        // prevent excessive handling when the player is pressing a single key for a prolonged time
        if (allKeysPressed.contains(keyPressed))
            return;
        switch (keyPressed) {
            case "W":
            case "A":
            case "S":
            case "D":
                long lastChargeTime = EntityHelper.getMetadata(ply, "chargeDirLastPressed").asLong();
                long currTimeInMS = Calendar.getInstance().getTimeInMillis();
                String lastChargeDir = EntityHelper.getMetadata(ply, "chargeDir").asString();
                if (currTimeInMS - lastChargeTime < 400 && lastChargeDir.equals(keyPressed)) {
                    // handle player charge
                    double chargeYaw = ((CraftPlayer) ply).getHandle().yaw;
                    switch (keyPressed) {
                        case "A":
                            chargeYaw -= 90;
                            break;
                        case "S":
                            chargeYaw += 180;
                            break;
                        case "D":
                            chargeYaw += 90;
                            break;
                    }
                }
                EntityHelper.setMetadata(ply, "chargeDirLastPressed", currTimeInMS);
                EntityHelper.setMetadata(ply, "chargeDir", keyPressed);
                break;
            case "SPACE":
                ply.addScoreboardTag("temp_thrusting");
                // remove all hooks
                for (Entity hook : (Collection<Entity>) EntityHelper.getMetadata(ply, "hooks").value())
                    hook.remove();
                break;
            case "R":
                PlayerHelper.handleGrapplingHook(ply);
                break;
            case "B":
                ItemUseHelper.playerQuickUsePotion(ply, ItemUseHelper.QuickBuffType.BUFF);
                break;
            case "H":
                ItemUseHelper.playerQuickUsePotion(ply, ItemUseHelper.QuickBuffType.HEALTH);
                break;
            case "J":
                ItemUseHelper.playerQuickUsePotion(ply, ItemUseHelper.QuickBuffType.MANA);
                break;
        }
    }
}
