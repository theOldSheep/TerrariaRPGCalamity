package terraria.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import terraria.entity.minion.MinionHelper;
import terraria.util.PlayerHelper;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.BiFunction;

public class Setting {
    public static HashMap<UUID, PendingKeyBind> PENDING_KEY_BIND = new HashMap();
    public static class PendingKeyBind {
        UUID uid;
        Location loc;
        Options option;
        public PendingKeyBind(Player ply, Options option) {
            this(ply.getUniqueId(), option, ply.getLocation());
        }
        public PendingKeyBind(UUID uid, Options option, Location loc) {
            this.option = option;
            this.loc = loc;
            this.uid = uid;
            PENDING_KEY_BIND.put(uid, this);
        }
        public boolean attemptBind(String newKey) {
            Player ply = Bukkit.getPlayer(uid);
            boolean success = false;
            if (ply != null) {
                if (ply.getLocation().getDirection().equals(loc.getDirection())) {
                    setOptionValue(ply, option, newKey);
                    success = true;
                }
            }
            PENDING_KEY_BIND.remove(uid);
            return success;
        }
    }

    public enum Options {
        // 3rd person
        THIRD_PERSON_DIST("泰拉瑞亚第三人称视角距离；取值范围<min>~<max>，默认值为<def>", 8d, 64d, 24d),
        THIRD_PERSON_INTERPOLATE("泰拉瑞亚第三人称视角切换动画时长（单位：毫秒）；取值范围<min>~<max>，默认值为<def>", 0, 2000, 500),
        THIRD_PERSON_HOTKEY("泰拉瑞亚第三人称视角切换按键，默认为[<def>]", "K"),
        // visual / client accessibility
        DEBUFF_PARTICLE_TOGGLE("是否在受到Debuff的实体身上显示粒子，默认值为<def>", true),
        PARTICLE_DENSITY_MULTI("普通粒子效果密度；取值范围<min>~<max>，默认值为<def>", 0.1d, 3d, 1d),
        UI_SIZE("UI（背包，快捷栏，生命值，魔力值等）大小；取值范围<min>~<max>，默认值为<def>", 0.25d, 1.5d, 1d),
        // gameplay
        AIM_HELPER_ACCELERATION("辅助瞄准功能是否考虑敌人加速度，默认值为<def>", true),
        AIM_HELPER_DISTANCE("辅助瞄准功能索敌最大距离（单位：格）；取值范围<min>~<max>，默认值为<def>", 32d, 96d, 96d),
        AIM_HELPER_RADIUS("辅助瞄准功能索敌最大范围（单位：格）；取值范围<min>~<max>，默认值为<def>", 0d, 15d, 5d),
        DEFAULT_AUTO_SWING("对于未注明是否自动挥舞的武器是否开启自动挥舞，默认值为<def>", true),
        DPS_DURATION("DPS计算时长；设置为较长时间可能会使计算更精准。取值范围<min>~<max>，默认值为<def>", 1, 10, 3),
        MINION_RETARGET_THRESHOLD("仆从因护主重新锁敌的最小距离差（单位：格）；取值范围<min>~<max>，默认值为<def>", 0d, 16d, 5d),
        MINION_AGGRO_RADIUS("仆从最大索敌范围（单位：格）；取值范围<min>~<max>，默认值为<def>", 24d, MinionHelper.MAX_DIST_BEFORE_TELEPORT, MinionHelper.DEFAULT_TARGET_DIST),
        VOID_BAG_PICKUP("虚空袋是否自动收纳掉落物，默认值为<def>", true),
        // controls
        CONTROL_W("前进按键，默认为[<def>]", "W"),
        CONTROL_A("向左移动按键，默认为[<def>]", "A"),
        CONTROL_S("后退按键，默认为[<def>]", "S"),
        CONTROL_D("向右移动按键，默认为[<def>]", "D"),
        CONTROL_SPACE("跳跃/飞行按键，默认为[<def>]", "SPACE"),
        CONTROL_MOUNT("坐骑开关按键，默认为[<def>]", "R"),
        CONTROL_HOOK("发射钩爪按键，默认为[<def>]", "F"),
        CONTROL_INSIGNIA("进升证章按键，默认为[<def>]", "X"),
        CONTROL_SWITCHABLE("切换类饰品按键，默认为[<def>]", "C"),
        CONTROL_ARMOR_SET("护甲套装主动效果按键，默认为[<def>]", "V"),
        CONTROL_BUFF("一键使用增益药水按键，默认为[<def>]", "B"),
        CONTROL_HEAL("一键使用治疗药水按键，默认为[<def>]", "H"),
        CONTROL_MANA("一键使用魔力药水按键，默认为[<def>]", "J"),
        CONTROL_MOUNT_DESCEND("飞行类坐骑下降按键，默认为[<def>]", "LSHIFT"),;

        public final String desc, defaultVal;
        public final boolean isKeyBind;
        public final BiFunction<String, Player, String> acceptor;
        Options(String description, double min, double max, double defaultVal) {
            this.isKeyBind = false;
            this.defaultVal = String.valueOf(defaultVal);
            description = description.replace("<min>", String.valueOf(min) );
            description = description.replace("<max>", String.valueOf(max) );
            description = description.replace("<def>", String.valueOf(defaultVal) );
            desc = description;

            acceptor = (str, ply) -> {
                try {
                    double parsed = Double.parseDouble(str);
                    if (parsed < min) {
                        parsed = min;
                        ply.sendMessage(String.format("设置值小于最小值(%.2f)。已默认为最小值。", min));
                    }
                    else if (parsed > max) {
                        parsed = max;
                        ply.sendMessage(String.format("设置值大于最大值(%.2f)。已默认为最大值。", max));
                    }
                    else {
                        ply.sendMessage("设置成功。");
                    }
                    return String.valueOf(parsed);
                }
                catch (Exception ignored) {
                    ply.sendMessage(String.format("无效的设置值；已设为默认值(%.2f)。", defaultVal));
                    return String.valueOf(defaultVal);
                }
            };
        }
        Options(String description, int min, int max, int defaultVal) {
            this.isKeyBind = false;
            this.defaultVal = String.valueOf(defaultVal);
            description = description.replace("<min>", String.valueOf(min) );
            description = description.replace("<max>", String.valueOf(max) );
            description = description.replace("<def>", String.valueOf(defaultVal) );
            desc = description;

            acceptor = (str, ply) -> {
                try {
                    int parsed = Integer.parseInt(str);
                    if (parsed < min) {
                        parsed = min;
                        ply.sendMessage(String.format("设置值小于最小值(%d)。已默认为最小值。", min));
                    }
                    else if (parsed > max) {
                        parsed = max;
                        ply.sendMessage(String.format("设置值大于最大值(%d)。已默认为最大值。", max));
                    }
                    else {
                        ply.sendMessage("设置成功。");
                    }
                    return String.valueOf(parsed);
                }
                catch (Exception ignored) {
                    ply.sendMessage(String.format("无效的设置值；已设为默认值(%d)。", defaultVal));
                    return String.valueOf(defaultVal);
                }
            };
        }
        Options(String description, boolean defaultVal) {
            this.isKeyBind = false;
            this.defaultVal = String.valueOf(defaultVal);
            description = description.replace("<def>", String.valueOf(defaultVal) );
            desc = description;

            acceptor = (str, ply) -> {
                boolean value;
                switch (str) {
                    case "t":
                    case "true":
                    case "y":
                    case "yes":
                    case "是":
                    case "开":
                    case "开启":
                    case "启用":
                    case "允许":
                        value = true;
                        ply.sendMessage("设置成功。");
                        break;
                    case "f":
                    case "false":
                    case "n":
                    case "no":
                    case "否":
                    case "关":
                    case "关闭":
                    case "禁用":
                    case "禁止":
                        value = false;
                        ply.sendMessage("设置成功。");
                        break;
                    default:
                        ply.sendMessage(String.format("无效的设置值；已设为默认值(%b)。", defaultVal));
                        value = defaultVal;
                }
                return String.valueOf(value);
            };
        }
        Options(String description, String defaultVal) {
            this.isKeyBind = true;
            this.defaultVal = defaultVal;
            description = description.replace("<def>", String.valueOf(defaultVal) );
            desc = description;

            acceptor = (str, ply) -> {
                ply.sendMessage(String.format("成功将按键 [%s] 绑定为 [%s]。", this, str));
                return str;
            };
        }
    }

    private static String getOptionPath(Options option) {
        return "options." + option.toString();
    }

    public static double getOptionDouble(Player ply, Options option) {
        String optionVal = PlayerHelper.getPlayerDataFile(ply).getString(getOptionPath(option), option.defaultVal);
        try {
            return Double.parseDouble(optionVal);
        }
        catch (Exception ignored) {
            return Double.parseDouble(option.defaultVal);
        }
    }
    public static int getOptionInt(Player ply, Options option) {
        String optionVal = PlayerHelper.getPlayerDataFile(ply).getString(getOptionPath(option), option.defaultVal);
        try {
            return Integer.parseInt(optionVal);
        }
        catch (Exception ignored) {
            return Integer.parseInt(option.defaultVal);
        }
    }
    public static boolean getOptionBool(Player ply, Options option) {
        String optionVal = PlayerHelper.getPlayerDataFile(ply).getString(getOptionPath(option), option.defaultVal);
        try {
            return Boolean.parseBoolean(optionVal);
        }
        catch (Exception ignored) {
            return Boolean.parseBoolean(option.defaultVal);
        }
    }
    public static String getOptionString(Player ply, Options option) {
        return PlayerHelper.getPlayerDataFile(ply).getString(getOptionPath(option), option.defaultVal);
    }

    public static void setOptionValue(Player ply, Options option, String value) {
        PlayerHelper.getPlayerDataFile(ply).set(
                getOptionPath(option), option.acceptor.apply(value, ply));
    }
}
