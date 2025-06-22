package terraria.event.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.metadata.MetadataValue;
import terraria.util.MetadataHelper;

public class PlayerChatListener implements Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerChat(PlayerChatEvent e) {
        String msg = e.getMessage();
        Player ply = e.getPlayer();
        // wormhole potion handling
        {
            String[] parts = msg.split(" ");
            if (parts.length == 2) {
                switch (parts[0]) {
                    case "传送到": {
                        // no teleport if wormhole potion is not consumed
                        if (!ply.getScoreboardTags().contains("wormHolePotionUsed"))
                            return;
                        e.setCancelled(true);
                        // target player is not online
                        Player targetPly = Bukkit.getServer().getPlayerExact(parts[1]);
                        if (targetPly == null) {
                            ply.sendMessage("§c传送目标玩家不在线。");
                        }
                        // target player is the original player
                        else if (targetPly == ply) {
                            ply.sendMessage("§c您无法向自己发送传送请求。");
                        }
                        // target player is online
                        else {
                            MetadataHelper.setMetadata(ply, MetadataHelper.MetadataName.PLAYER_TELEPORT_TARGET, parts[1]);
                            ply.sendMessage("§a传送请求已发送！");
                            targetPly.sendMessage(String.format(
                                    "§a玩家 %1$s 请求传送到你的位置！", ply.getName()));
                            targetPly.sendMessage(String.format(
                                    "§a您可以输入\"接受传送 %1$s\"来同意本次传送。", ply.getName()));
                        }
                        break;
                    }
                    case "接受传送": {
                        e.setCancelled(true);

                        Player teleportAcceptedPly = Bukkit.getServer().getPlayerExact(parts[1]);
                        // if the player is found
                        if (teleportAcceptedPly != null) {
                            MetadataValue teleportRequestTarget = MetadataHelper.getMetadata(teleportAcceptedPly,
                                    MetadataHelper.MetadataName.PLAYER_TELEPORT_TARGET);
                            // successful teleport
                            if (teleportRequestTarget != null && teleportRequestTarget.asString().equals(ply.getName())) {
                                ply.sendMessage(String.format(
                                        "§a已同意玩家 %1$s 的传送请求！正在尝试将对方传送至您的位置……", teleportAcceptedPly.getName() ));
                                teleportAcceptedPly.sendMessage(String.format(
                                        "§a玩家 %1$s 已同意您的传送请求！正在尝试将您传送至对方的位置……", ply.getName() ));
                                teleportAcceptedPly.removeScoreboardTag("wormHolePotionUsed");
                                teleportAcceptedPly.teleport(ply);
                                // remove teleport cache and wormhole potion scoreboard tag
                                MetadataHelper.setMetadata(teleportAcceptedPly,
                                        MetadataHelper.MetadataName.PLAYER_TELEPORT_TARGET, null);
                                teleportAcceptedPly.removeScoreboardTag("wormHolePotionUsed");
                            }
                            // no request present
                            else {
                                ply.sendMessage(String.format(
                                        "§a玩家 %1$s 暂未对您发送传送请求或已更改传送目标！", teleportAcceptedPly.getName() ));
                            }
                        }
                        else {
                            ply.sendMessage(String.format(
                                    "§a玩家 %1$s 并不在线！", parts[1] ));
                        }
                        break;
                    }
                }
            }
        }
    }
}
