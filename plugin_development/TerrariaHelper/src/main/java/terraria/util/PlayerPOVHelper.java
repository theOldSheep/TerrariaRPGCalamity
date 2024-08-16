package terraria.util;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import terraria.gameplay.Setting;

import java.util.HashMap;
import java.util.Map;



public class PlayerPOVHelper {
    private static final int INTERPOLATION_MILLIS = 2500;
    private static final String POV_TAG = "temp_thirdPerson";

    private static Map<Player, PlayerCameraState> playerStates = new HashMap<>();
    private static PlayerPOVHelper instance;

    public static boolean getPOVSwitch(Player ply) {
        return ply.getScoreboardTags().contains(POV_TAG);
    }
    public static void setPOVState(Player ply, boolean newState) {
        if (newState) {
            ply.addScoreboardTag(POV_TAG);
            moveCamera(ply, Setting.getOptionDouble(ply, Setting.Options.THIRD_PERSON_DIST), 1500);
        }
        else {
            ply.removeScoreboardTag(POV_TAG);
            moveCamera(ply, Setting.getOptionDouble(ply, Setting.Options.THIRD_PERSON_DIST), 0);
        }
    }
    public static void togglePOV(Player ply) {
        setPOVState(ply, !getPOVSwitch(ply));
    }

    public static void moveCamera(Player player, double distance, int sustainMillis) {
        PlayerCameraState state = playerStates.get(player);
        if (state == null) {
            state = new PlayerCameraState();
            playerStates.put(player, state);
        }
        state.targetDistance = distance;
        state.sustainMillis = sustainMillis;

        if (state.isSmoothingIn) {
            state.targetDistance = distance;
        }
        else if (state.isSustaining) {
            state.pendingUpdate = true;
        }
        // smoothing out / idle
        else {
            state.currentDistance = distance - 1;
            state.targetDistance = distance;
        }
    }

    public static void tick(long idx) {
        // sustain the camera location
        if (idx % 20 == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (getPOVSwitch(player)) {
                    moveCamera(player, Setting.getOptionDouble(player, Setting.Options.THIRD_PERSON_DIST), 1500);
                }
            }
        }
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<Player, PlayerCameraState> entry : playerStates.entrySet()) {
            Player player = entry.getKey();
            // track the duration
            PlayerCameraState state = entry.getValue();
            int interpolationDuration = Setting.getOptionInt(player, Setting.Options.THIRD_PERSON_INTERPOLATE);
            if (state.isSmoothingIn) {
                if (currentTime >= state.smoothInEndTime) {
                    state.isSmoothingIn = false;
                    if (state.targetDistance != state.currentDistance) {
                        // Smooth out the current distance
                        DragoncoreHelper.moveCamera(player, state.currentDistance, 0, 0, interpolationDuration);
                        state.isSmoothingOut = true;
                        state.smoothOutEndTime = currentTime + interpolationDuration;
                    } else {
                        // Start sustaining
                        DragoncoreHelper.moveCamera(player, state.targetDistance, 0, state.sustainMillis, interpolationDuration);
                        state.isSustaining = true;
                        state.sustainEndTime = currentTime + state.sustainMillis;
                    }
                }
            } else if (state.isSmoothingOut) {
                if (currentTime >= state.smoothOutEndTime) {
                    state.isSmoothingOut = false;
                    if (state.targetDistance != state.currentDistance) {
                        // Start the entire life cycle of the new distance
                        DragoncoreHelper.moveCamera(player, state.targetDistance, interpolationDuration, state.sustainMillis, interpolationDuration);
                        state.isSmoothingIn = true;
                        state.smoothInEndTime = currentTime + interpolationDuration;
                        state.currentDistance = state.targetDistance;
                    }
                }
            } else if (state.isSustaining) {
                if (currentTime >= state.sustainEndTime || state.targetDistance != state.currentDistance) {
                    // Smooth out the current distance
                    DragoncoreHelper.moveCamera(player, state.currentDistance, 0, 0, interpolationDuration);
                    state.isSmoothingOut = true;
                    state.smoothOutEndTime = currentTime + interpolationDuration;
                    state.isSustaining = false;
                } else if (state.pendingUpdate) {
                    // Update the sustain time
                    state.pendingUpdate = false;
                    state.sustainEndTime = currentTime + state.sustainMillis;
                    DragoncoreHelper.moveCamera(player, state.targetDistance, 0, state.sustainMillis, interpolationDuration);
                }
            } else {
                // Start the entire life cycle of the new distance
                if (state.targetDistance != state.currentDistance) {
                    DragoncoreHelper.moveCamera(player, state.targetDistance, interpolationDuration, state.sustainMillis, interpolationDuration);
                    state.isSmoothingIn = true;
                    state.smoothInEndTime = currentTime + interpolationDuration;
                    state.currentDistance = state.targetDistance;
                }
            }
        }
    }

    public static void resetCamera(Player player) {
        playerStates.remove(player);
        DragoncoreHelper.moveCamera(player, 0, 0, 0, 0);
    }

    private static class PlayerCameraState {
        double currentDistance;
        double targetDistance;
        int sustainMillis;
        boolean isSmoothingIn;
        boolean isSmoothingOut;
        boolean isSustaining;
        boolean pendingUpdate = false;
        long smoothInEndTime;
        long sustainEndTime;
        long smoothOutEndTime;
    }
}