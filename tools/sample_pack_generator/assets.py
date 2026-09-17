import os, sys, json
sys.path.insert(0, os.path.dirname(__file__))
from lib import make_skin, make_sound
from PIL import Image, ImageDraw

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f: f.write(text.strip() + "\n")

# ---------------- HUD ----------------
HUD_MAIN = """
; sample_hud.txt - main HUD script for every sample vehicle.
; Uses every drawing command + If/EndIf + Call so the syntax is all demonstrated in one place.
Color = 255, 60, 255, 90
DrawGraduationYaw = yaw, 0, -90
DrawGraduationPitch1 = pitch, -110, 0
DrawGraduationPitch2 = pitch, roll, 0, 0
DrawCameraRot = 0, 70

; --- left column: speed / altitude ---
DrawString = -150, -40, "SPD %.0f km/h", speed_kbh
DrawString = -150, -30, "ALT %.0f", altitude
DrawString = -150, -20, "SEA %.0f", sea_alt
DrawString = -150, -10, "THR %.2f", throttle

; --- right column: health / fuel ---
DrawString = 90, -40, "HP %.0f / %.0f", hp, max_hp
DrawRect = 90, -28, 50, 4
Color = 255, 255, 255, 255
DrawRect = 90, -28, hp_rto * 50, 4
Color = 255, 60, 255, 90
DrawString = 90, -20, "FUEL %.0f%%", fuel * 100
If = low_fuel == 1
  Color = 255, 255, 80, 40
  DrawCenteredString = 0, 40, "LOW FUEL", 
  Color = 255, 60, 255, 90
EndIf

; --- weapon block ---
DrawString = -150, 40, "%s", wpn_name
DrawString = -150, 50, "AMMO %d / %d", wpn_ammo, wpn_rm_ammo
If = reloading == 1
  DrawString = -150, 60, "RELOAD %d", reload_time
EndIf
If = is_heat_wpn == 1
  DrawString = -150, 60, "HEAT", 
  DrawRect = -110, 62, wpn_heat * 40, 4
EndIf
If = has_modes == 1
  DrawString = -150, 70, "MODE %d", wpn_mode
EndIf
If = has_mortar_distance == 1
  DrawString = -150, 80, "RNG %s", mortar_distance_str
EndIf
If = locked == 1
  Color = 255, 255, 60, 60
  DrawCenteredString = 0, -60, "LOCK", 
  Color = 255, 60, 255, 90
EndIf
If = lock_progress > 0
  DrawRect = -20, -50, lock_progress * 40, 3
EndIf

; --- flags ---
If = stalling == 1
  Color = 255, 255, 60, 60
  DrawCenteredString = 0, 30, "STALL", 
  Color = 255, 60, 255, 90
EndIf
If = gear_deployed == 1
  DrawString = 90, 40, "GEAR DOWN", 
EndIf
If = manual_mode == 1
  DrawString = 90, 50, "MANUAL", 
EndIf
If = free_look == 1
  DrawString = 90, 60, "FREELOOK", 
EndIf

; --- turret needle (rotating texture) + line indicator ---
DrawTexture = "sample_needle", -8, 92, 16, 16, 0, 0, 16, 16, gun_yaw
DrawLineStipple = 1, 2, -60, 110, 60, 110
DrawRect = gun_yaw - 1, 106, 2, 8
DrawString = 0, 114, "GUN %.0f / %.0f", gun_yaw, gun_pitch

Call = sample_compass
"""

HUD_COMPASS = """
; sample_compass.txt - sub-script called from sample_hud.txt.
; Draws a small position readout box at the bottom-left and exits.
Color = 255, 200, 200, 200
DrawLine = -160, 120, -100, 120, -100, 140, -160, 140, -160, 120
DrawString = -156, 124, "X %.0f", pos_x
DrawString = -156, 132, "Z %.0f", pos_z
If = time > 13000
  DrawString = -156, 144, "NIGHT", 
EndIf
Exit
DrawString = 0, 0, "never drawn (after Exit)", 
"""

def needle_texture(path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (10, 8), (8, 15), (6, 8)], fill=(255, 220, 60, 255))
    d.rectangle([7, 7, 8, 8], fill=(255, 80, 60, 255))
    img.save(path)

# ---------------- skins ----------------
SKINS = {
    # olive flight suit, white helmet with dark visor
    "smp_flight_suit": dict(shirt=(84, 96, 56), pants=(84, 96, 56), boots=(40, 40, 40), helmet=(230, 230, 230), visor=(40, 50, 70), belt=(60, 60, 60)),
    # grey coverall, black beret-like cap
    "smp_tank_crew": dict(shirt=(95, 95, 100), pants=(80, 80, 85), boots=(30, 30, 30), helmet=(35, 35, 40), hair=(50, 35, 25), belt=(120, 90, 50)),
    # white sailor uniform, navy cap
    "smp_sailor": dict(shirt=(235, 235, 240), pants=(30, 40, 80), boots=(20, 20, 20), helmet=(240, 240, 245), belt=(30, 40, 80)),
    # dark blue submariner sweater, no helmet
    "smp_submariner": dict(shirt=(35, 45, 80), pants=(30, 35, 55), boots=(25, 25, 25), hair=(40, 30, 20), belt=(60, 60, 60)),
    # camo gunner, steel helmet
    "smp_gunner": dict(shirt=(70, 85, 55), pants=(60, 70, 45), boots=(35, 35, 30), helmet=(70, 80, 60), belt=(50, 45, 35), sleeves=(85, 100, 65)),
    # orange hi-vis drone operator, cap
    "smp_operator": dict(shirt=(240, 130, 30), pants=(50, 50, 55), boots=(30, 30, 30), helmet=(60, 60, 65), belt=(40, 40, 40)),
}

# ---------------- lang ----------------
LANG_EN = {
    "_comment": "Sample pack translations. Vehicle/weapon display names come from the JSON/txt files themselves.",
    "tudur_sample.pack.name": "Tudur's Vehicle Mod - Sample Pack"
}
LANG_JA = {
    "_comment": "サンプルパックの翻訳ファイル。乗り物・武器の表示名はJSON/txt側のdisplay_nameで定義されています。",
    "tudur_sample.pack.name": "Tudur's Vehicle Mod - サンプルパック"
}

README = """
# サンプルパック (tudur_sample)

Tudur's Vehicle Mod の全乗り物タイプ・全武器タイプを網羅した、動作するサンプル
アドオンパックです。`tudursvehiclemod-addons/` 配下にこのフォルダごと配置し、
`/reload` で読み込まれます。モデルは箱を組み合わせたローポリ、テクスチャは
単色スウォッチ(キャノピー部分のみ半透明)です。細部の調整はご自由にどうぞ。

## 乗り物 (data/tudur_sample/vehicles/)

| ファイル | タイプ | 主な実装要素 |
|---|---|---|
| sample_helicopter | helicopter | メイン/テールローター回転、チンターレット(weapon_parts+pilot_fallback+recoil)、ロケット(HEIAP子弾)、対戦車ミサイル(トップアタック、ammo_parts+display_penalty)、スライド式ドア、サーチライト+light_hatch、航法灯、パラシュート降下席、float_capable |
| sample_aircraft | aircraft | 機銃(熱量式+薬莢)、対空ミサイル、ウェポンベイ(weapon_bay扉+ASMissile)、増槽(DropTank+display_penalty)、スモーク、降着装置3脚+扉(landing_gear/landing_gear_hatch)、キャノピー、可変翼(wing_sweep)、射出座席、失速。CAS/Carrierの発艦機としても使用 |
| sample_uav | helicopter | is_uav/is_target_drone、クアッドローター、自爆(Destruct)、weapon_bayハッチ |
| sample_vtol | vtol | ティルトローター(vtol_rotor_parts+vtol_rotor_parent)、爆弾(DelayFuse+Bound)、FAE爆弾、汎用ミサイル(Missile)、MkRocket、後部ランプ、mob_drop_option、降下兵席 |
| sample_halftrack | car | 前輪操舵(wheel_parts)+後部履帯(crawler_tracks)+転輪(track_roller_parts)+ハンドル(steering_wheel_parts)、砲塔(親子weapon_parts)、消火器(Dispenser)、ターゲティングポッド、操舵追従サーチライト、pivot_turn_throttle |
| sample_ship | ship | 滑走路3本(通常/エレベーター連動hatch_offset/hatch_gated)、Carrier発艦(編隊・カタパルト・帰投・回収)、対潜ロケット(ASWeapon)、CIWS(spins_while_firing)、爆雷(Depth)、回転レーダー、補給範囲、航跡 |
| sample_submarine | submarine | 潜航(dive_max_speed)、魚雷(UsableWhileDiving)、ハッチ/潜望鏡、スクリュー |
| sample_emplacement | static_emplacement | 高射砲(空中炸裂+子弾+距離表示)、TVミサイル、CAS呼び出し(3機編隊)、ダミー武器、regeneration |

## 武器 (assets/tudur_sample/weapons/) — 全Type網羅

MachineGun1 (smp_minigun / smp_flak), MachineGun2 (smp_ciws / smp_turret_gun), Rocket (smp_rockets),
Bomb (smp_bomb / smp_fae_bomb / smp_kamikaze), Depth (smp_depth_charge), Torpedo (smp_torpedo),
ASMissile (smp_as_missile), MkRocket (smp_mk_rocket), AAMissile (smp_aa_missile), ATMissile (smp_at_missile),
Missile (smp_missile), ASWeapon (smp_asw), TVMissile (smp_tv_missile), Dispenser (smp_dispenser),
Smoke (smp_smoke), Dummy (smp_dummy), TargetingPod (smp_targeting_pod), DropTank (smp_drop_tank),
CAS (smp_cas), Carrier (smp_carrier)

## その他

- `assets/tudur_sample/hud/sample_hud.txt` … 全描画コマンド・If/Call/Exitを使ったHUD(+`sample_compass.txt`)
- `assets/tudur_sample/textures/gui/sample_needle.png` … HUDの回転針テクスチャ
- `assets/tudur_sample/sound/*.ogg` … 合成音のエンジン音/発射音(smp_engine_jet/rotor/car/boat, smp_gun, smp_launch)
- `assets/tudur_sample/models/obj/bullet_*.obj` … 弾体・薬莢モデル(rocket/missile/bomb/torpedo/shell/cartridge)
- `textures/dummy_pilot/*.png` … テーマ別ダミーパイロットスキン(flight_suit / tank_crew / sailor / submariner / gunner / operator)

## 生成について

全アセットは `tools/sample_pack_generator/` のPythonスクリプトで生成しています
(`python3 build_all.py <出力先>`)。モデルの寸法・色・武器の数値を変えたい場合は、
スクリプトを編集して再生成するか、出力されたファイルを直接編集してください。
"""

def build(root):
    A = os.path.join(root, "assets", "tudur_sample")
    write(os.path.join(A, "hud", "sample_hud.txt"), HUD_MAIN)
    write(os.path.join(A, "hud", "sample_compass.txt"), HUD_COMPASS)
    os.makedirs(os.path.join(A, "textures", "gui"), exist_ok=True)
    needle_texture(os.path.join(A, "textures", "gui", "sample_needle.png"))
    snd = os.path.join(A, "sound"); os.makedirs(snd, exist_ok=True)
    for name, kind, sec in (("smp_engine_jet", "engine", 2.0), ("smp_engine_rotor", "rotor", 2.0),
                            ("smp_engine_car", "engine", 2.0), ("smp_engine_boat", "boat", 2.0),
                            ("smp_gun", "gun", 0.25), ("smp_launch", "launch", 0.8)):
        make_sound(os.path.join(snd, name + ".ogg"), kind, sec)
    os.makedirs(os.path.join(A, "lang"), exist_ok=True)
    for fn, d in (("en_us.json", LANG_EN), ("ja_jp.json", LANG_JA)):
        with open(os.path.join(A, "lang", fn), "w", encoding="utf-8") as f: json.dump(d, f, indent=2, ensure_ascii=False); f.write("\n")
    sk = os.path.join(root, "textures", "dummy_pilot"); os.makedirs(sk, exist_ok=True)
    for name, kw in SKINS.items(): make_skin(os.path.join(sk, name + ".png"), **kw)
    write(os.path.join(root, "README_SamplePack.md"), README)
    print("hud/sounds/skins/lang/readme: ok")

if __name__ == "__main__":
    build(sys.argv[1])
