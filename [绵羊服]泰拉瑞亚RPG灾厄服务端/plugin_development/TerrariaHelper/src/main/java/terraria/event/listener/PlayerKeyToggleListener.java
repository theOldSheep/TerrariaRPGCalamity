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
        HashSet<String> allKeysPressed = (HashSet<String>) EntityHelper.getMetadata(ply,
                EntityHelper.MetadataName.PLAYER_KEYS_PRESSED).value();
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
        HashSet<String> allKeysPressed = (HashSet<String>) EntityHelper.getMetadata(ply,
                EntityHelper.MetadataName.PLAYER_KEYS_PRESSED).value();
        String keyPressed = e.getKey();
        if (! PlayerHelper.isProperlyPlaying(ply))
            return;
        // prevent excessive handling when the player is pressing a single key for a prolonged time
        if (allKeysPressed.contains(keyPressed))
            return;
        allKeysPressed.add(keyPressed);
        int removeAllGrapplingHooks = 0;
        switch (keyPressed) {
            case "W":
            case "A":
            case "S":
            case "D":
                long lastChargeTime = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_KEY_PRESSED_MS).asLong();
                long currTimeInMS = Calendar.getInstance().getTimeInMillis();
                String lastChargeDir = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_DIRECTION).asString();
                if (currTimeInMS - lastChargeTime < 200 && lastChargeDir.equals(keyPressed)) {
                    // handle player charge
                    double chargeYaw = PlayerHelper.getPlayerMoveYaw(ply, keyPressed);
                    PlayerHelper.handleDash(ply, chargeYaw, 0);
                }
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_KEY_PRESSED_MS, currTimeInMS);
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_DIRECTION, keyPressed);
                break;
            case "SPACE":
                ply.addScoreboardTag("temp_thrusting");
                // only remove hooks already in blocks
                removeAllGrapplingHooks = 1;
                break;
            case "R":
                PlayerHelper.handleMount(ply);
                // remove all hooks
                removeAllGrapplingHooks = 2;
                break;
            case "F":
                PlayerHelper.handleGrapplingHook(ply);
                // dismount on using hook
                if (PlayerHelper.getMount(ply) != null) {
                    PlayerHelper.handleMount(ply);
                }
                break;
            case "V":
                PlayerHelper.handleArmorSetActiveEffect(ply);
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

        // remove all hooks that are in place
        if (removeAllGrapplingHooks > 0)
            for (Entity hook : (Collection<Entity>) EntityHelper.getMetadata(ply,
                    EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOKS).value())
                if (removeAllGrapplingHooks != 1 || hook.getVelocity().lengthSquared() < 1e-5)
                    hook.remove();
    }
}
