package terraria.gameplay;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SettingCommandExecutor implements CommandExecutor, TabCompleter {
    public static final String COMMAND = "settings";
    public static final int DESCRIPTIONS_PER_PAGE = 6;

    public static void describeSettingOptions(Player ply, String page) {
        try {
            describeSettingOptions(ply, Integer.parseInt(page));
        }
        catch (Exception ignored) {
            describeSettingOptions(ply, 1);
        }
    }
    public static void describeSettingOptions(Player ply, int page) {
        Setting.Options[] options = Setting.Options.values();
        int pagesTotal = (int) Math.ceil((double) options.length / DESCRIPTIONS_PER_PAGE);
        if (page < 1)
            page = 1;
        else if (page > pagesTotal)
            page = pagesTotal;

        ply.sendMessage(ChatColor.RED + String.format("=-=-=-=-=-=-=-=-=-=第 %d/%d 页=-=-=-=-=-=-=-=-=-=", page, pagesTotal));
        for (int idx = (page - 1) * DESCRIPTIONS_PER_PAGE; idx < page * DESCRIPTIONS_PER_PAGE && idx < options.length; idx ++) {
            Setting.Options option = options[idx];
            ply.sendMessage(ChatColor.DARK_AQUA + "[" + option.toString() + "] " + ChatColor.RESET + option.desc);
        }
        ply.sendMessage(ChatColor.LIGHT_PURPLE + String.format("请输入 [/%s ? 页码] 查看其他页", COMMAND));
        ply.sendMessage(ChatColor.LIGHT_PURPLE + String.format("请输入 [/%s 选项 值] 设定选项值", COMMAND));
    }

    private static Setting.Options getOptionFromString(String option) {
        try {
            return Setting.Options.valueOf(option);
        }
        catch (Exception ignored) {
            return null;
        }
    }
    private static void warnNonExistingOption(Player ply, String option) {
        ply.sendMessage(String.format("选项 “%s” 并不存在。请尝试利用Tab补全功能或/%s ? 方法获得选项名称。", option, COMMAND) );
    }
    public static void setOptionValue(Player ply, String option, String value) {
        Setting.Options optionParsed = getOptionFromString(option);
        if (optionParsed != null) {
            Setting.setOptionValue(ply, optionParsed, value);
        }
        else {
            warnNonExistingOption(ply, option);
        }
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if (commandSender instanceof Player) {
            Player player = (Player) commandSender;
            // the option is specified
            if (args.length >= 1) {
                String optionRaw = args[0];
                switch (optionRaw) {
                    // the user is expecting some hint
                    case "?":
                    case "？":
                        describeSettingOptions(player, args.length == 1 ? "1" : args[1]);
                        break;
                    // actual manipulation with the option
                    default: {
                        Setting.Options option = getOptionFromString(optionRaw);
                        // warn about non-existing option
                        if (option == null) {
                            warnNonExistingOption(player, optionRaw);
                        }
                        else {
                            // key-bind options; delay the binding mechanism to some key press.
                            if (option.isKeyBind) {
                                new Setting.PendingKeyBind(player, option);
                                player.sendMessage(ChatColor.GOLD + "请按下您想要绑定的按键。您可以转动视角来取消按键绑定。");
                                player.sendMessage(ChatColor.GOLD + String.format(
                                        "目前按键设置 [%s] 的绑定按键为： [%s]", option, Setting.getOptionString(player, option)) );
                            }
                            else {
                                // explain the option if the user did not specify the new value
                                if (args.length == 1) {
                                    player.sendMessage(ChatColor.GREEN + String.format("选项 “%s”：", optionRaw));
                                    player.sendMessage(ChatColor.GREEN + option.desc);
                                }
                                // setting the value otherwise
                                else {
                                    Setting.setOptionValue(player, option, args[1]);
                                }
                            }
                        }
                    }
                }
            }
            else {
                describeSettingOptions(player, 1);
            }
            return true;
        }

        return false;
    }


    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] args) {
        if (s.equals(COMMAND)) {
            // option name fill-in
            if (args.length <= 1) {
                ArrayList<String> candidates = new ArrayList<>();
                String startingRestriction;
                switch (args.length) {
                    case 0:
                        startingRestriction = "";
                        break;
                    case 1:
                        startingRestriction = args[0].toLowerCase();
                        break;
                    default:
                        return null;
                }
                for (Setting.Options option : Setting.Options.values()) {
                    if (option.toString().toLowerCase().startsWith(startingRestriction))
                        candidates.add(option.toString());
                }
                return candidates;
            }
            // default value fill-in
            else if (args.length == 2) {
                Setting.Options option = getOptionFromString(args[0]);
                if (option != null) {
                    ArrayList<String> defaultValue = new ArrayList<>();
                    defaultValue.add(option.defaultVal);
                    return defaultValue;
                }
            }
        }
        return null;
    }
}
