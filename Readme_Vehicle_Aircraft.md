# Readme_Vehicle_Aircraft.md - 固定翼機(`entity_type: aircraft`)特有の項目

`Readme_Vehicle.md`の共通項目に加え、`entity_type: "tudursvehiclemod:aircraft"`を
指定した機体でのみ意味を持つ項目です。

---

## 飛行性能

### `stall_speed`
- **書式**: 浮動小数点数(既定値: `0.3`)
- **説明**: 実効最高速度に対する割合。飛行中、実際の速度がこの割合を下回ると
  失速状態になります(徐々に高度を失う)。速度が(この値+0.1)まで回復すると
  失速状態を脱します。
- **例**:
  ```json
  "stall_speed": 0.35
  ```

### `float_capable`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`の場合、水上機(飛行艇)として扱われ、水面への着水・水面での
  滑走・水面からの離水が可能になります。`false`(既定)の通常機が水面に着水すると
  操縦不能になり、緩やかに沈み始めます(本来水面着水を想定していないため)。
- **例**:
  ```json
  "float_capable": true
  ```

---

## 主翼折りたたみ(可変後退翼)

`toggle_parts`の`trigger: "wing_fold"`と組み合わせて使用します。専用キーで
折りたたみ・展開を切り替えます(`Readme_Vehicle.md`のH キーによるハッチ開閉とは
別の操作です)。

### `wing_sweep`(`toggle_parts`要素内)
- **書式**: オブジェクト(Optional)
- **説明**: `trigger: "wing_fold"`のパーツにのみ設定します。省略時は単純な
  「静止中のみ折りたたみ可能、折りたたみ中は移動不可」の格納翼として動作します。
  - `variable_sweep_wing`(既定値`false`): `true`にすると、飛行中でも専用キーで
    後退角を変更できる可変後退翼になります(速度による制限はありません)
  - `sweep_wing_speed`(既定値`0.0`): `variable_sweep_wing: true`の場合、
    折りたたみ・折りたたみ中はこの値が`max_speed`の代わりに最高速度上限として
    使われます(後退角を増すほど高速、が実機の可変後退翼の挙動です)
- **例**(艦載機の格納翼、飛行中は折りたためない):
  ```json
  {
    "part": "$wing_l",
    "pivot_x": -1.0, "pivot_y": 0.0, "pivot_z": 0.0,
    "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0,
    "max_angle": 100.0,
    "trigger": "wing_fold",
    "speed": 3.0
  }
  ```
- **例**(可変後退翼、飛行中も後退角を変更可能):
  ```json
  {
    "part": "$wing_l",
    "trigger": "wing_fold",
    "wing_sweep": { "variable_sweep_wing": true, "sweep_wing_speed": 2.4 }
  }
  ```

---

## 降着装置(ランディングギア)

`toggle_parts`の`trigger: "landing_gear"`(格納状態に追従して開閉)、または
`trigger: "landing_gear_hatch"`(格納装置が動作中の間だけ開くカバー、
`AddPartLGHatch`相当)と組み合わせて使用します。専用の操作キーで展開・格納を
切り替えます。降着装置が格納された状態では地上判定・着陸ができません。

- **例**:
  ```json
  { "part": "$gear_nose", "pivot_x": 0.0, "pivot_y": 0.0, "pivot_z": 2.0, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": 90.0, "trigger": "landing_gear", "speed": 4.0 },
  { "part": "$gear_hatch_nose", "trigger": "landing_gear_hatch", "max_angle": 90.0, "speed": 8.0 }
  ```

---

## 無人機(UAV)・自律ドローン

これらのフラグは`Readme_Vehicle.md`未記載の、`GroundVehicleParts`グループ内の
項目です(命名上は「地上車両向け」ですが、実際には航空機を含む全タイプで
使用可能です)。

### `is_uav`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`の場合、この機体は無人機(UAV)として扱われます。通常の
  搭乗・操縦席の意味が失われ、専用のステーションブロック経由での遠隔操縦
  専用になります。詳細は`README.md`の「無人機(UAV)・自律ドローン・近接航空
  支援(CAS)」セクションを参照してください。
- **例**:
  ```json
  "is_uav": true
  ```

### `is_target_drone`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`の場合、通常の搭乗を無効化し、標的ドローン(自律AI飛行)
  専用として扱います。ドローンセンターブロックからの制御自体は、このフラグに
  関わらずどの機体でも可能です。
- **例**:
  ```json
  "is_target_drone": true
  ```

---

## 近接航空支援(CAS)・空母(Carrier)発艦機として使われる場合

`AircraftEntity`は、他の機体の`weapons`に`Type = CAS`または`Type = CARRIER`の
武器を設定した際、その武器から自動的にスポーンされる「支援機」「艦載機」としても
使用されます。これらのモードで飛行中は、通常の搭乗・操縦とは別の自律航路飛行AIが
機体を制御します。武器側の設定については`Readme_Weapon_Cas.md`を参照してください。

空母から発艦する艦載機自身の機体JSONには、CAS/Carrier専用の設定項目はありません
(発艦ルート・着艦条件等は全て武器設定ファイル側で定義されます)。

---

## 補足

- 固定翼機は`wheel_parts`(`Readme_Vehicle_Car.md`参照)を使用しません
- 空母の滑走路(`runways`)は、艦載機自身ではなく**発艦・着艦させる側の乗り物**の
  機体JSONに設定します。艦船に限らずどの乗り物にも設定できます。詳細は
  `Readme_Vehicle.md`の「12. 空母(Carrier)甲板・滑走路」を参照してください
