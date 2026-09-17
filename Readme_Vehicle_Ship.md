# Readme_Vehicle_Ship.md - 艦船(`entity_type: ship`)特有の項目

`Readme_Vehicle.md`の共通項目に加え、`entity_type: "tudursvehiclemod:ship"`を
指定した機体でのみ意味を持つ項目です。水面を航行する船舶に使用します。

---

## 航跡(ウェイク)

### `wake_trail_spread_distance`
- **書式**: 浮動小数点数(Optional)
- **説明**: 航跡(船首波・航跡)の広がり幅を手動で指定します。省略時は船体形状
  (喫水線での実測ビーム幅)から自動検出されます。
- **例**:
  ```json
  "wake_trail_spread_distance": 8.0
  ```

### `wake_trail_duration_ticks`
- **書式**: 整数(既定値: `300`)
- **説明**: 航跡の1点が消えるまでの表示時間(tick)。
- **例**:
  ```json
  "wake_trail_duration_ticks": 300
  ```

---

## 補足

- `wheel_parts`(`Readme_Vehicle_Car.md`参照)・`vtol_rotor_parts`
  (`Readme_Vehicle_Vtol.md`参照)は使用しません
- 潜水艦特有の潜航機能(`dive_max_speed`等)は使用しません。潜水艦は別途
  `entity_type: submarine`を使用してください(`Readme_Vehicle_Submarine.md`参照)
