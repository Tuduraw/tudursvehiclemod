# Readme_Vehicle_Submarine.md - 潜水艦(`entity_type: submarine`)特有の項目

`Readme_Vehicle.md`の共通項目に加え、`entity_type: "tudursvehiclemod:submarine"`
を指定した機体でのみ意味を持つ項目です。

---

## `dive_max_speed`

- **書式**: 浮動小数点数(Optional)
- **説明**: 潜航中の最高速度。`max_speed`は**水上航行時のみ**に使われる値になり、
  潜航中はこちらが上限速度として使われます。省略時は`max_speed / 3`が自動的に
  使われます。
- **例**:
  ```json
  "max_speed": 1.2,
  "dive_max_speed": 0.5
  ```

---

## 浮上・潜航(Hキー)

潜水艦は、共通項目の`toggle_parts`によるハッチ開閉と同じHキーが「浮上/潜航」の
切り替えを兼ねます(ハッチ状態=浮上状態、という特殊な扱いです。他の機体タイプの
「ハッチ」とは意味が異なります)。

- **浮上状態**(ハッチ開): 通常通り武器を使用できます。召喚直後は浮上状態です
- **潜航状態**(ハッチ閉): 武器の使用が凍結されます。ただし`Type = Torpedo`の
  武器、および個別武器ファイルで`UsableWhileDiving = true`を指定した武器は
  潜航中でも使用可能です(`Readme_Weapon.md`参照)
- 浮上→潜航の物理的な切り替え(実際に沈み始める)は、ハッチ部位の`toggle_parts`
  アニメーションが完了するまで(またはハッチパーツが存在しない場合は固定2秒)
  遅延します。潜航→浮上の切り替えは即座に反映されます

このモデル特有の注意点として、ハッチ・コニングタワー部位の`toggle_parts`は、
他の全ての機体の慣例とは**逆方向**の角度規則(`angle=0`が全開姿勢、回転後の
角度が閉鎖姿勢)を使用する場合があります。実際のモデルに合わせて`max_angle`の
符号等を調整してください。

- **例**(コニングタワーハッチ):
  ```json
  "toggle_parts": [
    { "part": "$conning_tower_hatch", "pivot_x": 0.0, "pivot_y": 1.5, "pivot_z": 0.0, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": 90.0, "trigger": "key", "speed": 6.0 }
  ]
  ```

---

## 水中巡航(魚雷)

潜水艦自身の武器(`Type = Torpedo`)は、潜航中でも使用できます。魚雷自体の
水中挙動(目標深度・水中速度)については`Readme_Weapon_Torpedo.md`を参照して
ください。

---

## 補足

- `wheel_parts`(`Readme_Vehicle_Car.md`参照)・`vtol_rotor_parts`
  (`Readme_Vehicle_Vtol.md`参照)・`runways`(`Readme_Vehicle_Ship.md`参照)は
  使用しません
- 艦船と同様、水上航行時は航跡(`wake_trail_spread_distance`/
  `wake_trail_duration_ticks`、`Readme_Vehicle_Ship.md`参照)が生成されます
