STAR_COUNT = 35
HEART_ROWS = 2
HEART_COLS = 10
HEART_TOTAL = HEART_ROWS * HEART_COLS

STAR_BG = '''star{i}_background_texture:
  visible: "(方法.到整数(方法.取变量('terraria_max_mana')) > (20 * {i})) ? 'true' : 'false'"
  x: "界面变量.star_x"
  y: "界面变量.star_size + 界面变量.star_stack_offset * {i}"
  width: "界面变量.star_size"
  height: "界面变量.star_size"
  texture: "'hud/star_container.png'"
  tip: "界面变量.mana_tooltip"
'''

HEART_BG = '''heart{i}_background_texture:
  visible: "(界面变量.last_heart >= {ii}) ? 'true' : 'false'"
  x: "界面变量.health_x + 界面变量.health_size * {c} - (界面变量.last_heart == {ii} ? 界面变量.health_offset_large : 0)"
  y: "界面变量.health_y + 界面变量.health_size * {r} - (界面变量.last_heart == {ii} ? 界面变量.health_offset_large : 0)"
  width: "(界面变量.last_heart == {ii} ? 界面变量.health_size_large : 界面变量.health_size)"
  height: "方法.取组件值('heart{i}_background_texture','width')"
  texture: "界面变量.last_heart == {ii} ? 'hud/heart_container_last.png' : 'hud/heart_container.png'"
  tip: "界面变量.health_tooltip"
'''


STAR_OVERLAY = '''star{i}_texture:
  x: "界面变量.star_x - (方法.取组件值('star{i}_texture','width') / 2)"
  y: "界面变量.star_y + (界面变量.star_stack_offset * {i}) - (方法.取组件值('star{i}_texture','width') / 2)"
  width: "界面变量.star_size * 方法.min(方法.max((方法.玩家等级() / 20) - {i}, 0), 1)"
  height: "方法.取组件值('star{i}_texture','width')"
  texture: "(方法.到整数(方法.取变量('terraria_mana_tier')) < 11) ? 'hud/star1.png' : (方法.合并文本('hud/star', 方法.到整数(方法.取变量('terraria_mana_tier') - 9), '.png'))"
'''

HEART_OVERLAY = '''heart{i}_texture:
  visible: "(界面变量.health_tier >= {ii}) ? 'true' : 'false'"
  x: "界面变量.health_x + (界面变量.health_size * {c}) - (方法.取组件值('heart{i}_texture','width') / 2)"
  y: "界面变量.health_y + (界面变量.health_size * {r}) - (方法.取组件值('heart{i}_texture','width') / 2)"
  width: "界面变量.health_size * 方法.min(方法.max((界面变量.playerHealth / 方法.玩家最大血量()) * 方法.min(方法.取变量('terraria_health_tier'), 20) - {i}, 0), 1)"
  height: "方法.取组件值('heart{i}_texture','width')"
  texture: "(界面变量.health_tier <= {threshold}) ? 'hud/heart1.png' : ((界面变量.health_tier <= {threshold2}) ? 'hud/heart2.png' : 方法.合并文本('hud/heart', 方法.到整数(方法.取变量('terraria_health_tier') - ({threshold2} - 2)), '.png'))"
'''

BARRIER_OVERLAY = '''barrier{i}_texture:
  visible: "(界面变量.health_tier >= {ii}) ? 'true' : 'false'"
  x: "界面变量.health_x + (界面变量.health_size * {c}) - (界面变量.health_size / 2)"
  y: "界面变量.health_y + (界面变量.health_size * {r}) - (界面变量.health_size / 2)"
  textureWidth: "界面变量.health_size"
  textureHeight: "界面变量.health_size"
  width: "界面变量.health_size * 方法.min(方法.max((方法.取变量('terraria_energy_shield') / 方法.玩家最大血量()) * 方法.min(方法.取变量('terraria_health_tier'), 20) - {i}, 0), 1)"
  height: "界面变量.health_size"
  texture: "'hud/heart_shielding.png'"
  alpha: "界面变量.energy_shielding_alpha"
'''


with open('background.txt', 'w') as bg:
    bg.write("# star\n")
    for i in range(STAR_COUNT):
        bg.write(STAR_BG.format(i=i))
    bg.write("\n# heart\n")
    for r in range(HEART_ROWS):
        for c in range(HEART_COLS):
            i = r * HEART_COLS + c
            bg.write(HEART_BG.format(i=i, ii=i+1, r=r, c=c))

with open('overlay.txt', 'w') as ovl:
    ovl.write("# star\n")
    for i in range(STAR_COUNT):
        ovl.write(STAR_OVERLAY.format(i=i))
    ovl.write("\n# heart\n")
    for r in range(HEART_ROWS):
        for c in range(HEART_COLS):
            i = r * HEART_COLS + c
            ovl.write(HEART_OVERLAY.format(i=i, ii=i+1, r=r, c=c, threshold=HEART_TOTAL+i, threshold2=HEART_TOTAL*2))
    ovl.write("\n# barrier\n")
    for r in range(HEART_ROWS):
        for c in range(HEART_COLS):
            i = r * HEART_COLS + c
            ovl.write(BARRIER_OVERLAY.format(i=i, ii=i+1, r=r, c=c))