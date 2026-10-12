# Readme_Vehicle_Vtol.md - VTOL機(`entity_type: vtol`)特有の項目

`entity_type: "tudursvehiclemod:vtol"`は、固定翼機(`Aircraft`)を拡張したタイプ
です(`Readme_Vehicle_Aircraft.md`の項目は全てそのまま使用できます)。専用キーで
ヘリコプターモード(垂直離着陸・ホバリング)と固定翼機モード(通常飛行)を
切り替えられる機体向けです。

---

## `vtol_rotor_parts`

- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: MCヘリの`AddPartRotor`に相当。ヘリコプターモード⇔固定翼機モードの
  切り替えに合わせて傾動するローターナセル等のパーツです(ティルトローター機
  向け)。
- **例**(左右2基のティルトローター):
  ```json
  "vtol_rotor_parts": [
    { "part": "$rotor_nacelle_l", "pivot_x": -3.0, "pivot_y": 0.5, "pivot_z": 0.0, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0 },
    { "part": "$rotor_nacelle_r", "pivot_x": 3.0, "pivot_y": 0.5, "pivot_z": 0.0, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0 }
  ]
  ```

各要素の項目:

### `part`
- **書式**: 文字列(必須)
- **説明**: 対象のOBJパーツ名。
- **例**: `"part": "$rotor_nacelle_l"`

### `pivot_x` / `pivot_y` / `pivot_z`
- **書式**: 浮動小数点数(既定値いずれも`0.0`)
- **説明**: 傾動の中心座標。
- **例**: `"pivot_x": -3.0, "pivot_y": 0.5, "pivot_z": 0.0`

### `axis_x` / `axis_y` / `axis_z`
- **書式**: 浮動小数点数(既定値`1.0`/`0.0`/`0.0`)
- **説明**: 傾動軸ベクトル。符号も含めて正確に指定してください(傾動方向が
  逆転する原因になります)。
- **例**: `"axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0`

傾動範囲は固定で90度です(モード切り替えの進捗0〜1に対応)。固定翼機モードで
モデルの原型姿勢(回転なし)、ヘリコプターモードで90度回転した姿勢になります。

このパーツに従属して回転する常時回転パーツ(プロペラ・ブレード等)を追加したい
場合は、`Readme_Vehicle.md`の`spinning_parts`の`vtol_rotor_parent`にこの
`vtol_rotor_parts`の`part`名を指定してください。

- **例**(上記ナセルに従属するプロペラ):
  ```json
  "spinning_parts": [
    {
      "part": "$prop_l", "pivot_x": -3.5, "pivot_y": 0.5, "pivot_z": 0.0,
      "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "speed": 90.0,
      "vtol_rotor_parent": "$rotor_nacelle_l"
    }
  ]
  ```

---

## `vtol_hover_speed_fraction`

- **書式**: 浮動小数点数(既定値: `0.1`)
- **説明**: ヘリコプターモード中のWASD水平移動の最高速度を、`max_speed`(固定翼機
  モードでの最高速度と共通)に対する割合で指定します。既定の`0.1`は10%です。
  本プロジェクト独自の追加項目(MCヘリ非対応)で、ヘリコプターモードの水平移動が
  固定翼機モードと同じ速度になってしまうと過剰に速く感じるための調整項目です。
- **例**:
  ```json
  "vtol_hover_speed_fraction": 0.15
  ```

---

## モード切り替え

- 専用キーでヘリコプターモード⇔固定翼機モードを切り替えます。機体が水平に
  近い姿勢のときのみ切り替え開始が可能で、切り替え中は再度の切り替え操作は
  無視されます
- ヘリコプターモード中は、固定翼機の失速(`stall_speed`)は発生しません
- 降着装置(`toggle_parts`の`trigger: "landing_gear"`)は固定翼機と共通です
  (記載例は`Readme_Vehicle_Aircraft.md`参照)

---

## 補足

- `Readme_Vehicle_Aircraft.md`の全項目(`stall_speed`・`float_capable`・
  `wing_sweep`・`is_uav`・`is_target_drone`等)はVtolでもそのまま使用できます
- `wheel_parts`(`Readme_Vehicle_Car.md`参照)は使用しません
