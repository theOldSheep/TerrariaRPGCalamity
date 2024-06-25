package terraria.util;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;



public class PlayerPOVHelper {
    private static final int INTERPOLATION_MILLIS = 2500;

    private Map<Player, PlayerCameraState> playerStates;
    private static PlayerPOVHelper instance;
    private Map<Player, Integer> interpolationDurations;


    private PlayerPOVHelper() {
        this.playerStates = new HashMap<>();
        interpolationDurations = new HashMap<>();
    }

    public static PlayerPOVHelper getInstance() {
        if (instance == null) {
            instance = new PlayerPOVHelper();
        }
        return instance;
    }
    public void setInterpolationDuration(Player player, int duration) {
        interpolationDurations.put(player, duration);
    }

    private int getInterpolationDuration(Player player) {
        return interpolationDurations.getOrDefault(player, INTERPOLATION_MILLIS);
    }

    public void moveCamera(Player player, double distance, int sustainMillis) {
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

    public void tick() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<Player, PlayerCameraState> entry : playerStates.entrySet()) {
            Player player = entry.getKey();
            PlayerCameraState state = entry.getValue();
            int interpolationDuration = getInterpolationDuration(player);
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

    public void resetCamera(Player player) {
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