# Readme_Vehicle_StaticEmplacement.md - 据置型兵装(`entity_type: static_emplacement`)特有の項目

`Readme_Vehicle.md`の共通項目のうち、移動に関する項目(`max_speed`・
`acceleration`・`turn_speed`・`step_height`・`reverse_throttle`・
`pivot_turn_throttle`・`wheel_rotation_speed`・`throttle_up_down`・
`weight_type`・`throttle_switch_hold_ticks`等)は一切使用されません。この
タイプはMCヘリの`Vehicles`カテゴリ(固定砲台等)に相当し、自走・操舵・スロットル
操作の能力を一切持ちません。

---

## 特徴

- スロットル・操舵は無効です。搭乗しても機体自体は移動しません
- 重力の影響は他タイプと同様に受けます(設置面がなくなれば落下します)
- 座席(`seats`)による搭乗、`weapon_parts`による砲塔追従(搭乗者の視点に合わせた
  向きの変更)、`toggle_parts`によるハッチ・カバーの開閉、当たり判定・耐久・
  撃破処理は、他の全機体タイプと共通して利用できます
- 固定砲台やタレット等、「その場で銃座として機能するが移動はしない」構造物の
  表現に使用します

---

## 使用しない項目

- `wheel_parts`・`steering_wheel_parts`・`crawler_tracks`・`track_roller_parts`
  (`Readme_Vehicle_Car.md`参照)
- `vtol_rotor_parts`(`Readme_Vehicle_Vtol.md`参照)
- `runways`(`Readme_Vehicle_Ship.md`参照)
- `stall_speed`・`float_capable`・`wing_sweep`(`Readme_Vehicle_Aircraft.md`参照)
- `dive_max_speed`(`Readme_Vehicle_Submarine.md`参照)
