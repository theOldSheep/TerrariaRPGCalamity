package terraria.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import terraria.TerrariaHelper;
import terraria.util.BossHelper;

import java.util.ArrayList;
import java.util.List;

public class RestartCommandExecutor implements CommandExecutor, TabCompleter {
    private int countdown = -1;
    public static final int COUNTDOWN_MAX = 20, COUNTDOWN_ITV = 5;
    public static String COMMAND = "terrariaRestart";

    private void printWarning() {
        Bukkit.broadcastMessage(ChatColor.RED +
                "服务器将在 " + countdown + " 秒后重启！若要取消重启，请输入 /"
                + COMMAND + " terminate");
    }
    private void tick() {
        if (countdown > 0) {
            countdown --;
            if (countdown <= 0) {
                Bukkit.getServer().shutdown();
            }
            else if (countdown % COUNTDOWN_ITV == 0) {
                printWarning();
            }
        }
    }
    public RestartCommandExecutor() {
        super();
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), this::tick, 0, 20);
    }
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        boolean startOrTerminate = true;
        if (args.length > 0) {
            String optionRaw = args[0];
            if (optionRaw.equalsIgnoreCase("terminate")) {
                startOrTerminate = false;
            }
        }
        if (startOrTerminate) {
            if (countdown < 0 && BossHelper.bossMap.isEmpty()) {
                countdown = COUNTDOWN_MAX;
                printWarning();
            }
        }
        else {
            if (countdown > 0) {
                countdown = -1;
                Bukkit.broadcastMessage(ChatColor.AQUA + "服务器重启尝试已终止。");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        ArrayList<String> result = new ArrayList<>();
        result.add("plan");
        result.add("terminate");
        return result;
    }
}
