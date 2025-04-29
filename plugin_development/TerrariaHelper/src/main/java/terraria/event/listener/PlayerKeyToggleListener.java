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
import terraria.gameplay.Setting;
import terraria.util.EntityHelper;
import terraria.util.ItemUseHelper;
import terraria.util.PlayerHelper;
import terraria.util.PlayerPOVHelper;

import java.util.*;

public class PlayerKeyToggleListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyReleaseEvent(KeyReleaseEvent e) {
        Player ply = e.getPlayer();
        HashSet<String> allKeysPressed = PlayerHelper.getPlayerKeyPressed(ply);
        String keyReleased = e.getKey();
        allKeysPressed.remove(keyReleased);
        // space bar
        if (keyReleased.equals("ALL") || keyReleased.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_SPACE))) {
            ply.removeScoreboardTag("temp_thrusting");
        }
        if (keyReleased.equals("ALL")) {
            allKeysPressed.clear();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyPressEvent(KeyPressEvent e) {
        Player ply = e.getPlayer();
        HashSet<String> allKeysPressed = PlayerHelper.getPlayerKeyPressed(ply);
        String keyPressed = e.getKey();
        if (! PlayerHelper.isProperlyPlaying(ply))
            return;
        // prevent excessive handling when the player is pressing a single key for a prolonged time
        if (allKeysPressed.contains(keyPressed))
            return;
        allKeysPressed.add(keyPressed);
        int removeAllGrapplingHooks = 0;
        // check for key binding
        Setting.PendingKeyBind keyBind = Setting.PENDING_KEY_BIND.get(ply.getUniqueId());
        if (keyBind != null && keyBind.attemptBind(keyPressed)) {
            return;
        }

        // WASD double-click dash
        if (Setting.getOptionBool(ply, Setting.Options.ENABLE_DOUBLE_CLICK_DASH) && (
                keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_W)) ||
                keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_A)) ||
                keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_S)) ||
                keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_D))) ) {
            long lastChargeTime = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_KEY_PRESSED_MS).asLong();
            long currTimeInMS = Calendar.getInstance().getTimeInMillis();
            String lastChargeDir = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_DIRECTION).asString();
            if (currTimeInMS - lastChargeTime < 200 && lastChargeDir.equals(keyPressed)) {
                // try to dash in the double-tapped direction
                Collection<String> cgDir = new ArrayList<>();
                cgDir.add(keyPressed);
                double chargeYaw = PlayerHelper.getPlayerMoveYaw(ply, cgDir);
                PlayerHelper.handleDash(ply, chargeYaw, 0);
            }
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_KEY_PRESSED_MS, currTimeInMS);
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_DIRECTION, keyPressed);
        }
        // Hotkey dash
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_DASH)) ) {
            double chargeYaw = PlayerHelper.getPlayerMoveYaw(ply);
            // default to forward if not moving
            if (chargeYaw > 1e5) chargeYaw = ply.getLocation().getYaw();
            PlayerHelper.handleDash(ply, chargeYaw, 0);
        }
        // SPACE
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_SPACE))) {
            ply.addScoreboardTag("temp_thrusting");
            // only remove hooks already in blocks
            removeAllGrapplingHooks = 1;
        }
        // MOUNT
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_MOUNT))) {
            PlayerHelper.handleMount(ply);
            // remove all hooks
            removeAllGrapplingHooks = 2;
        }
        // HOOK
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_HOOK))) {
            PlayerHelper.handleGrapplingHook(ply);
            // dismount on using hook
            if (PlayerHelper.getMount(ply) != null) {
                PlayerHelper.handleMount(ply);
            }
        }
        // Insignia
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_INSIGNIA))) {
            PlayerHelper.handleInsignia(ply);
        }
        // Switchable
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_SWITCHABLE))) {
            PlayerHelper.handleToggleSwitchable(ply);
        }
        // Armor Set
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_ARMOR_SET))) {
            PlayerHelper.handleArmorSetActiveEffect(ply);
        }
        // Buff up
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_BUFF))) {
            ItemUseHelper.playerQuickUsePotion(ply, ItemUseHelper.QuickBuffType.BUFF);
        }
        // Quick Heal
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_HEAL))) {
            ItemUseHelper.playerQuickUsePotion(ply, ItemUseHelper.QuickBuffType.HEALTH);
        }
        // Quick Mana
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.CONTROL_MANA))) {
            ItemUseHelper.playerQuickUsePotion(ply, ItemUseHelper.QuickBuffType.MANA);
        }
        // third person toggle
        if (keyPressed.equals(Setting.getOptionString(ply, Setting.Options.THIRD_PERSON_HOTKEY))) {
            PlayerPOVHelper.togglePOV(ply);
        }

        // remove all hooks that are in place, if needed
        if (removeAllGrapplingHooks > 0) {
            for (Entity hook : (Collection<Entity>) EntityHelper.getMetadata(ply,
                    EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOKS).value())
                if (removeAllGrapplingHooks != 1 || hook.getVelocity().lengthSquared() < 1e-5)
                    hook.remove();
        }
    }
}
