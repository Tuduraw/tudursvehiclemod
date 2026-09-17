# Readme_Vehicle.md - 機体設定ファイル(共通項目)

対象ファイル: `data/<namespace>/vehicles/<名前>.json`(JSON形式)

このドキュメントには、全ての機体タイプ(`entity_type`の値に関わらず)に共通する
設定項目を記載しています。特定タイプにのみ意味を持つ項目は、対応する
`Readme_Vehicle_○○.md`を参照してください。

- `Readme_Vehicle_Aircraft.md` — 固定翼機(`entity_type: aircraft`)
- `Readme_Vehicle_Helicopter.md` — ヘリコプター(`entity_type: helicopter`)
- `Readme_Vehicle_Vtol.md` — VTOL機(`entity_type: vtol`)
- `Readme_Vehicle_Car.md` — 車両・戦車(`entity_type: car`)
- `Readme_Vehicle_Ship.md` — 艦船(`entity_type: ship`)
- `Readme_Vehicle_Submarine.md` — 潜水艦(`entity_type: submarine`)
- `Readme_Vehicle_StaticEmplacement.md` — 据置型兵装(`entity_type: static_emplacement`)

JSON全体はキャメルケースではなく`snake_case`のキー名を使用します。`Optional`と
記載されている項目は省略可能で、省略時は記載のデフォルト値が使われます。

各項目の見出しの下に、実際の記載例を`例:`として付けています。

---

## 目次

1. 基本情報
2. 移動・操作性
3. 座席
4. 武装
5. 耐久・燃料・インベントリ
6. 見た目・当たり判定
7. 継続回転パーツ(spinning_parts)
8. 開閉パーツ(toggle_parts)
9. 武器連動パーツ(weapon_parts)
10. サーチライト・航法灯
11. 補給範囲
12. スポーンアイテム
13. パラシュート降下

---

## 1. 基本情報

### `entity_type`
- **書式**: 文字列(Identifier)
- **説明**: この機体の動作モデル(挙動の実体クラス)を指定します。必須項目です。
  使用可能な値: `tudursvehiclemod:aircraft` / `helicopter` / `vtol` / `car` /
  `ship` / `submarine` / `static_emplacement`。各タイプの詳細は個別ドキュメントを
  参照してください。
- **例**:
  ```json
  "entity_type": "tudursvehiclemod:aircraft"
  ```

### `model`
- **書式**: 文字列(Identifier)
- **説明**: 表示用の3DモデルファイルへのパスをIdentifier形式で指定します。必須項目です。
  OBJ形式(三角形・四角形・多角形いずれも可)。慣例として
  `assets/<namespace>/models/obj/<名前>.obj`に配置します。
- **例**:
  ```json
  "model": "tudursvehiclemod:models/obj/f16.obj"
  ```

### `texture`
- **書式**: 文字列(Identifier)
- **説明**: 上記モデルに適用するテクスチャ画像へのパス。必須項目です。慣例として
  `assets/<namespace>/textures/vehicle/<名前>.png`に配置します。
- **例**:
  ```json
  "texture": "tudursvehiclemod:textures/vehicle/f16.png"
  ```

### `scale`
- **書式**: 浮動小数点数(既定値: `1.0`)
- **説明**: モデルの表示倍率。
- **例**:
  ```json
  "scale": 1.2
  ```

### `width` / `height`
- **書式**: 浮動小数点数
- **説明**: この機体自身の見た目上のサイズを表す参考値です。必須項目です。実際の
  当たり判定サイズには通常使われません(後述の`force_bounding_box`参照)。
- **例**:
  ```json
  "width": 2.2,
  "height": 1.4
  ```

---

## 2. 移動・操作性

### `max_speed`
- **書式**: 浮動小数点数(既定値: `1.0`)
- **説明**: この機体の最高速度。単位はブロック/tick相当。
- **例**:
  ```json
  "max_speed": 1.8
  ```

### `acceleration`
- **書式**: 浮動小数点数(既定値: `0.05`)
- **説明**: スロットルに対する加速の追従度。
- **例**:
  ```json
  "acceleration": 0.05
  ```

### `turn_speed`
- **書式**: 浮動小数点数(既定値: `3.0`)
- **説明**: 旋回性能。大きいほど小回りが利きます。MCヘリの`MobilityYaw`に相当。
  負の値を指定しても自動的に絶対値へ正規化されます(旋回入力の反転を防止するため)。
- **例**:
  ```json
  "turn_speed": 3.5
  ```

### `step_height`
- **書式**: 浮動小数点数(既定値: `1.0`)
- **説明**: 自動で乗り越えられる段差の高さ。
- **例**:
  ```json
  "step_height": 1.0
  ```

### `gravity`
- **書式**: 浮動小数点数(既定値: `-0.04`)
- **説明**: この機体自身にかかる重力加速度。
- **例**:
  ```json
  "gravity": -0.04
  ```

### `on_ground_pitch`
- **書式**: 浮動小数点数(既定値: `0.0`)
- **説明**: 接地時にイージングして向かうピッチ角(度)。
- **例**:
  ```json
  "on_ground_pitch": 0.0
  ```

### `reverse_throttle`
- **書式**: 浮動小数点数(既定値: `0.0`)
- **説明**: 後進可能な最低スロットル値(負の値)。例: `-0.2`で後進20%まで。`0`は
  後進不可を意味します。正の値を指定しても自動的に負へ正規化されます。
- **例**:
  ```json
  "reverse_throttle": -0.2
  ```

### `engine_sound_volume`
- **書式**: 浮動小数点数(既定値: `3.0`)
- **説明**: `engine_sound`の音量倍率。MCヘリ武器設定の`SoundVolume`と同じ考え方で、
  `1.0`が通常の最大音量、それを超えると聞こえる距離が伸びます(本プロジェクト
  独自の追加項目)。
- **例**:
  ```json
  "engine_sound_volume": 3.0
  ```

### `pivot_turn_throttle`
- **書式**: 浮動小数点数(既定値: `0.0`)
- **説明**: `Car`タイプ専用。旋回に必要な最低スロットル(`max_speed`に対する割合、
  0〜1)。`0`(既定)ならスロットル0でもその場旋回(超信地旋回)可能。詳細は
  `Readme_Vehicle_Car.md`参照。
- **例**:
  ```json
  "pivot_turn_throttle": 0.2
  ```

### `wheel_rotation_speed`
- **書式**: 浮動小数点数(既定値: `40.0`)
- **説明**: `Car`タイプ専用。タイヤの回転速度係数。詳細は`Readme_Vehicle_Car.md`参照。
- **例**:
  ```json
  "wheel_rotation_speed": 40.0
  ```

### `throttle_up_down`
- **書式**: 浮動小数点数(Optional)
- **説明**: W/S入力に対するスロットル変化速度の倍率。`1.0`で各機体タイプの既定値
  そのまま、`2.0`で2倍速、`0.5`で半分の速さになります。省略時は各タイプの
  既定ステップ値を使用します。
- **例**:
  ```json
  "throttle_up_down": 1.5
  ```

### `weight_type`
- **書式**: 文字列(`"tank"` / `"car"` / `"unknown"`、既定値: `"unknown"`)
- **説明**: 機体の重量タイプ。MCヘリの`WeightType`と同じ:
  - `tank`: モブに衝突してもダメージを受けない。ブロック破壊力が高い
  - `car`: モブに衝突すると自分もダメージを受ける。ブロック破壊力が低い
  - `unknown`(既定): どちらの挙動も発生しません
- **例**:
  ```json
  "weight_type": "tank"
  ```

### `throttle_switch_hold_ticks`
- **書式**: 整数(Optional)
- **説明**: スロットルが正負を跨ぐ際、0%で一時停止する時間(tick単位)。省略時は
  各機体タイプの既定値(40tick)。
- **例**:
  ```json
  "throttle_switch_hold_ticks": 40
  ```

### `dive_max_speed`
- **書式**: 浮動小数点数(Optional)
- **説明**: `Submarine`タイプ専用。潜航中の最高速度(`max_speed`は水上航行時のみ
  使用)。詳細は`Readme_Vehicle_Submarine.md`参照。
- **例**:
  ```json
  "dive_max_speed": 0.6
  ```

---

## 3. 座席(`seats`)

- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: 搭乗可能な座席の一覧。配列の0番目が必ず操縦席(パイロット席)として
  扱われます。
- **例**(操縦席1つ+銃座1つ):
  ```json
  "seats": [
    { "name": "pilot", "offset_x": 0.0, "offset_y": 0.5, "offset_z": 0.5, "driver": true },
    { "name": "gunner", "offset_x": 0.0, "offset_y": 0.8, "offset_z": -1.5 }
  ]
  ```

各座席オブジェクトの項目:

### `name`
- **書式**: 文字列
- **説明**: 座席の識別名。
- **例**: `"name": "pilot"`

### `offset_x` / `offset_y` / `offset_z`
- **書式**: 浮動小数点数
- **説明**: 機体基準位置からの座席の相対座標。必須項目です。
- **例**: `"offset_x": 0.0, "offset_y": 0.5, "offset_z": 0.5`

### 降車位置について
座席の`offset_y`にかかわらず、**降車位置は機体の現在のY座標+1.0
ブロックの高さ**になります。低い座席の搭乗者が機体や地面に埋まったり、
高い座席の搭乗者が不要に落下したりすることを防ぐための仕様です。X・Z
座標は従来どおり座席の`offset_x`/`offset_z`(機体の向きに応じて回転)が
そのまま使われます。

この補正は機体が空中にあるかどうかに関わらず一律に適用されます(機体の
現在位置を基準にした相対的な加算のため、例えば高度100mを飛行中の機体
から降車する場合、降車位置は高度101mとなり、通常の落下と同様に扱われ
実用上の問題はありません)。

### `dismount_x` / `dismount_y` / `dismount_z`
- **書式**: 浮動小数点数(任意項目、既定は未設定)
- **説明**: この座席の降車位置を個別に上書きします。`offset_x/y/z`と同じ
  座標系(機体基準・機体の向きに応じて回転)です。軸ごとに独立して設定
  でき、指定しなかった軸は既定の挙動(X・Zは`offset_x/z`、Yは上記の
  機体基準+1.0の固定高さ)になります。
- **例**: `"dismount_x": 0.0, "dismount_y": 1.0, "dismount_z": 2.5`

### `driver`
- **書式**: 真偽値(既定値: `false`)
- **説明**: この座席が操縦可能かどうか。
- **例**: `"driver": true`

### `enable_parachuting`
- **書式**: 真偽値(既定値: `false`)
- **説明**: この座席の搭乗者(パイロット席を除く)がパラシュートジャンプ用の
  割り当てキーでパラシュート降下できるかどうか。詳細は
  「13. パラシュート降下」を参照してください。
- **例**: `"enable_parachuting": true`

### `camera_positions`
- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: この座席で使用可能な視点位置のリスト。複数設定するとHキーで視点を
  切り替えられます(MCヘリの`CameraPosition`に相当)。各要素:
  - `x` / `y` / `z`(必須): 視点座標
  - `force_camera`(既定値`false`): 常にカメラ視点になる(一人称に切り替わらない)
  - `fixed_yaw` / `fixed_pitch`(Optional): 固定視点角度
- **例**:
  ```json
  "camera_positions": [
    { "x": 0.0, "y": 0.6, "z": 0.2 },
    { "x": 0.0, "y": 3.0, "z": -6.0, "force_camera": true }
  ]
  ```

---

## 4. 武装(`weapons`)

- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: この機体に搭載する武器マウントの一覧。実際のダメージ・弾速等の数値は
  `weapon_name`で参照する`.txt`ファイル(`Readme_Weapon.md`参照)側で定義され、
  ここでは搭載位置・照準範囲のみを設定します。
- **例**:
  ```json
  "weapons": [
    {
      "seat_index": 0,
      "offsets": [ { "x": 0.6, "y": 0.2, "z": 3.0 } ],
      "projectile_item": "minecraft:iron_nugget",
      "weapon_name": "m61_vulcan"
    }
  ]
  ```

### `seat_index`
- **書式**: 整数
- **説明**: この武器を使用できる座席のインデックス(`seats`配列の添字、0が操縦席)。
- **例**: `"seat_index": 0`

### `offsets`
- **書式**: オブジェクトの配列(必須、1つ以上)
- **説明**: 発射位置の一覧(複数設定すると連装砲等を表現可能)。各要素:
  - `x` / `y` / `z`(既定値`0.0`): 機体基準の発射位置座標
  - `mount_yaw` / `mount_pitch`(既定値`0.0`): 固定の向き(`aim_range`未設定時)
  - `linked_part`(Optional、文字列): この発射位置を特定の`weapon_parts`名に
    紐付ける(複数連装砲でどのパーツがどの発射口か区別する場合に使用)
- **例**(左右2連装):
  ```json
  "offsets": [
    { "x": -0.8, "y": 0.3, "z": 2.5, "linked_part": "$gun_left" },
    { "x": 0.8, "y": 0.3, "z": 2.5, "linked_part": "$gun_right" }
  ]
  ```

### `aim_range`
- **書式**: オブジェクト(Optional)
- **説明**: 設定すると、この武器は固定方向ではなく搭乗者の視点を追従します
  (`min`/`max`の範囲でクランプ)。MCヘリの`AddWeapon`末尾パラメータに相当。
  - `default_yaw`(既定値`0.0`)
  - `min_yaw`/`max_yaw`(既定値`-180.0`/`180.0`)
  - `min_pitch`/`max_pitch`(既定値`-90.0`/`90.0`)
- **例**:
  ```json
  "aim_range": { "default_yaw": 0.0, "min_yaw": -170.0, "max_yaw": 170.0, "min_pitch": -10.0, "max_pitch": 60.0 }
  ```

### `projectile_item`
- **書式**: 文字列(Identifier)
- **説明**: 弾体のアイテム表示(モデル未設定時のフォールバック)に使うアイテムID。
- **例**: `"projectile_item": "minecraft:iron_nugget"`

### `weapon_name`
- **書式**: 文字列
- **説明**: 対応する武器設定ファイル(`assets/<namespace>/weapons/<weapon_name>.txt`)
  の名前(拡張子なし)。必須項目です。ダメージ・弾速・武器タイプ等、実際の性能は
  全てこのファイル側で定義されます(`Readme_Weapon.md`参照)。
- **例**: `"weapon_name": "m61_vulcan"`

### `pilot_usable`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`の場合、`seat_index`の座席が無人のとき、操縦席(座席0)の
  搭乗者がこの武器を代わりに使用できます。`seat_index`が座席0以外の場合のみ
  意味を持ちます。
- **例**: `"pilot_usable": true`

### `turret_rotation_speed`
- **書式**: 浮動小数点数(Optional、度/秒)
- **説明**: この武器マウント固有の旋回速度上限。省略時は搭乗者の視点へ即座に
  追従します。設定した場合、`weapon_parts`(9章参照)の追従がこの速度に
  制限され、旋回が完了するまでこの武器は発砲できません。
- **例**:
  ```json
  "turret_rotation_speed": 25.0
  ```

---

## 5. 耐久・燃料・インベントリ

### `max_health`
- **書式**: 浮動小数点数(既定値: `100.0`)
- **説明**: 最大HP。
- **例**: `"max_health": 150.0`

### `armor_damage_factor`
- **書式**: 浮動小数点数(既定値: `1.0`)
- **説明**: 被ダメージへの倍率(装甲係数)。
- **例**: `"armor_damage_factor": 0.5`

### `armor_min_damage`
- **書式**: 浮動小数点数(既定値: `0.0`)
- **説明**: 装甲係数適用後のダメージがこの値未満の場合、そのダメージは完全に
  無効化されます。
- **例**: `"armor_min_damage": 2.0`

### `armor_max_damage`
- **書式**: 浮動小数点数(既定値: 実質無制限)
- **説明**: 装甲係数適用後のダメージがこの値を超える場合、この値まで切り下げられます。
- **例**: `"armor_max_damage": 40.0`

### `damage_factor`
- **書式**: 浮動小数点数(既定値: `1.0`)
- **説明**: 搭乗中のプレイヤーが受けるダメージへの倍率。
- **例**: `"damage_factor": 0.3`

### `max_fuel`
- **書式**: 浮動小数点数(既定値: `600.0`)
- **説明**: 最大燃料量。
- **例**: `"max_fuel": 800.0`

### `fuel_consumption`
- **書式**: 浮動小数点数(既定値: `0.5`)
- **説明**: 秒あたりの燃料消費量(スロットルに応じて変動)。燃料切れになると
  スロットルが強制的に0になります(操縦自体は可能)。
  **負の値を指定すると、この乗り物は燃料を一切使用しません。**
  燃料の減少・燃料切れによる出力制限・燃料計の低燃料警告のいずれも
  発生せず、常に満タン扱いとして動作します(内部の燃料値や`max_fuel`
  の設定に関わらず)。人力で走る自転車のように、燃料という概念自体が
  そぐわない乗り物向けの設定です。
- **例**: `"fuel_consumption": 0.5`
- **例(燃料不要)**: `"fuel_consumption": -1.0`

### `inventory_size`
- **書式**: 整数(既定値: `0`)
- **説明**: この機体が保持する永続インベントリのスロット数。`0`ならインベントリ
  なし。
- **例**: `"inventory_size": 9`

### `ammo_parts`
- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: MCヘリの`AddPartWeaponMissile`に相当。特定武器の残弾数が一定以下に
  なった際に非表示になる、外部から見える弾体パーツ(ミサイル・爆弾等)。各要素:
  - `part`(必須): 対象のOBJパーツ名
  - `weapon_name`(必須): 対象武器の`weapon_name`
  - `slot_index`(既定値`0`): 残弾数がこの値以下になった時点で非表示化(0,1,2...と
    複数指定すると1発ずつ順番に消えていく演出が可能)
  - `display_penalty`(Optional): このパーツが表示状態(残弾がslot_indexを
    上回っている間)にある間、機体の速度・運動性能をそれぞれ低下させる。
    `"速度低下(%), 運動性能低下(%)"`という形式の文字列で指定する
    (例: `"10, 5"`で速度10%減・運動性能5%減)。省略時は影響なし。
    複数のパーツを設定した場合、表示中の全パーツの値が加算される
    (乗算ではない)。パーツごとに個別の値を設定できるため、武器の種類や
    搭載位置に応じて異なる影響を与えられる
- **例**(2発搭載しているミサイルが、残弾に応じて1発ずつ消え、搭載中は
  それぞれ速度・運動性能へ影響する):
  ```json
  "ammo_parts": [
    { "part": "$missile_1", "weapon_name": "agm65", "slot_index": 1, "display_penalty": "5, 3" },
    { "part": "$missile_2", "weapon_name": "agm65", "slot_index": 0, "display_penalty": "5, 3" }
  ]
  ```

### `submerged_damage_height`
- **書式**: 浮動小数点数(既定値: `0.0`)
- **説明**: 水面下このブロック数までなら潜っても毎tickダメージを受けません。
  `0`(既定)は少しでも水没すると即座にダメージが発生することを意味します。
- **例**: `"submerged_damage_height": 1.5`

### `fuel_supply_range` / `ammo_supply_range` / `health_supply_range`
- **書式**: 浮動小数点数(既定値いずれも`0.0`)
- **説明**: MCヘリの`FuelSupplyRange`/`AmmoSupplyRange`、および本プロジェクト
  独自追加の耐久値版。この半径(ブロック)内にいる**他の**機体へ、燃料・弾薬・
  耐久値を継続的に供給します。供給する側は消費・減少しません。自機自身への
  供給は行われません。`0`(既定)は補給機能なし。
- **例**:
  ```json
  "fuel_supply_range": 20.0,
  "ammo_supply_range": 20.0,
  "health_supply_range": 20.0
  ```

---

## 6. 見た目・当たり判定

### `hide_entity`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`の場合、搭乗中のプレイヤーモデル自体を完全に非表示にします
  (機能自体は維持されたまま見た目のみ消えます)。
- **例**: `"hide_entity": true`

### `entity_width` / `entity_height`
- **書式**: 浮動小数点数(既定値いずれも`1.0`)
- **説明**: 搭乗中のプレイヤーモデルの表示スケール。
- **例**: `"entity_width": 0.9, "entity_height": 0.9`

### `force_bounding_box`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `false`(既定)の場合、当たり判定は本MOD標準の固定サイズが使われます。
  `true`にすると、代わりにこのファイル自身の`width`/`height`が実際の当たり判定
  サイズとして使われます。
- **例**: `"force_bounding_box": true`

### `float_capable`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `Aircraft`(および`Vtol`)・`Helicopter`タイプ専用。水上への着水・
  浮遊が可能かどうか。詳細は`Readme_Vehicle_Aircraft.md`・
  `Readme_Vehicle_Helicopter.md`参照。
- **例**: `"float_capable": true`

### `stall_speed`
- **書式**: 浮動小数点数(既定値: `0.3`)
- **説明**: `Aircraft`タイプ専用。失速が発生する速度の割合。詳細は
  `Readme_Vehicle_Aircraft.md`参照。
- **例**: `"stall_speed": 0.3`

### `hud`
- **書式**: 文字列(Optional)
- **説明**: この機体が使用するHUDスクリプト名(`assets/<namespace>/hud/<名前>.txt`、
  拡張子なしで指定)。省略時はHUD表示なし。
- **例**: `"hud": "f16_hud"`

### `engine_sound`
- **書式**: 文字列(Optional)
- **説明**: エンジン音として再生するサウンド名(任意の名前空間の
  `assets/<namespace>/sounds/<名前>.ogg`に一致するもの)。
- **例**: `"engine_sound": "jet_engine"`

---

## 7. 継続回転パーツ(`spinning_parts`)

- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: OBJモデル内の名前付きグループ(`o`/`g`行)を、自身のピボット・軸を
  中心に常時一定速度で回転させます(プロペラ・ローター等)。
- **例**(メインローター):
  ```json
  "spinning_parts": [
    { "part": "$main_rotor", "pivot_x": 0.0, "pivot_y": 1.5, "pivot_z": 0.0, "axis_x": 0.0, "axis_y": 1.0, "axis_z": 0.0, "speed": 60.0 }
  ]
  ```

各要素の項目:

### `part`
- **書式**: 文字列(必須)
- **説明**: 対象のOBJパーツ名。
- **例**: `"part": "$main_rotor"`

### `pivot_x` / `pivot_y` / `pivot_z`
- **書式**: 浮動小数点数(既定値いずれも`0.0`)
- **説明**: 回転中心座標。
- **例**: `"pivot_x": 0.0, "pivot_y": 1.5, "pivot_z": 0.0`

### `axis_x` / `axis_y` / `axis_z`
- **書式**: 浮動小数点数(既定値`0.0`/`1.0`/`0.0`)
- **説明**: 回転軸ベクトル。
- **例**: `"axis_x": 0.0, "axis_y": 1.0, "axis_z": 0.0`

### `speed`
- **書式**: 浮動小数点数(既定値: `15.0`)
- **説明**: 1tickあたりの回転角度(度)。
- **例**: `"speed": 60.0`

### `vtol_rotor_parent`
- **書式**: 文字列(Optional)
- **説明**: `Vtol`タイプ専用。このブレードが従属するローターナセル(`vtol_rotor_parts`
  の`part`名)を指定すると、常時回転に加えてそのナセルの傾動も追従します。
  詳細は`Readme_Vehicle_Vtol.md`参照。
- **例**: `"vtol_rotor_parent": "$vtol_rotor0"`

---

## 8. 開閉パーツ(`toggle_parts`)

- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: OBJモデル内の名前付きグループを、2つの状態(閉=0/開=1)間でイージング
  させます(ハッチ・キャノピー・降着装置カバー等)。
- **例**(手動開閉するキャノピー):
  ```json
  "toggle_parts": [
    { "part": "$canopy", "pivot_x": 0.0, "pivot_y": 1.0, "pivot_z": -0.5, "mode": "rotate", "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": 80.0, "trigger": "key", "speed": 6.0 }
  ]
  ```

各要素の項目:

### `part`
- **書式**: 文字列(必須)
- **説明**: 対象のOBJパーツ名。
- **例**: `"part": "$canopy"`

### `pivot_x` / `pivot_y` / `pivot_z`
- **書式**: 浮動小数点数(既定値いずれも`0.0`)
- **説明**: 回転・スライドの基準座標。
- **例**: `"pivot_x": 0.0, "pivot_y": 1.0, "pivot_z": -0.5`

### `mode`
- **書式**: 文字列(`"rotate"` / `"slide"`、既定値: `"rotate"`)
- **説明**: `rotate`は`axis_x/y/z`+`max_angle`による回転、`slide`は
  `offset_x/y/z`によるスライド移動。
- **例**: `"mode": "slide"`

### `axis_x` / `axis_y` / `axis_z`
- **書式**: 浮動小数点数(既定値`0.0`/`1.0`/`0.0`)
- **説明**: `mode: rotate`時の回転軸。
- **例**: `"axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0`

### `max_angle`
- **書式**: 浮動小数点数(既定値: `90.0`)
- **説明**: `mode: rotate`時、全開状態での回転角度(度)。
- **例**: `"max_angle": 80.0`

### `offset_x` / `offset_y` / `offset_z`
- **書式**: 浮動小数点数(既定値いずれも`0.0`)
- **説明**: `mode: slide`時、全開状態での移動量。
- **例**(エレベーター甲板が8ブロック下降するスライド式ハッチ): `"offset_y": -8.0`

### `trigger`
- **書式**: 文字列(既定値: `"key"`)
- **説明**: このパーツの開閉状態を何が決定するか:
  - `key`: Hキーによる手動開閉。パーツ名が`$hatch`で始まる場合は設定`speed`の
    2倍速、`$canopy`で始まる場合は0.1倍速で動作します(本プロジェクト独自の
    命名規則による補正)
  - `landing_gear`: 降着装置の展開・格納状態に追従(`Aircraft`/`Vtol`専用)
  - `landing_gear_hatch`: 降着装置が動作中の間だけ開く(`AddPartLGHatch`相当)
  - `weapon_bay`: `weapon_name`で指定した武器が選択されている間だけ開く
  - `wing_fold`: 主翼折りたたみ専用キーで開閉(`Aircraft`専用、`wing_sweep`参照)
  - `light_hatch`: サーチライト点灯中だけ開く
- **例**: `"trigger": "landing_gear"`

### `speed`
- **書式**: 浮動小数点数(既定値: `6.0`)
- **説明**: 1tickあたりの開閉進捗の変化速度。
- **例**: `"speed": 6.0`

### `weapon_name`
- **書式**: 文字列(Optional)
- **説明**: `trigger: weapon_bay`の場合のみ意味を持ちます。対象武器の`weapon_name`。
- **例**:
  ```json
  { "part": "$weapon_bay_door", "trigger": "weapon_bay", "weapon_name": "agm65" }
  ```

### `wing_sweep`
- **書式**: オブジェクト(Optional)
- **説明**: `trigger: wing_fold`の場合のみ意味を持ちます。詳細は
  `Readme_Vehicle_Aircraft.md`参照。
- **例**:
  ```json
  { "part": "$wing_l", "trigger": "wing_fold", "wing_sweep": { "variable_sweep_wing": true, "sweep_wing_speed": 2.5 } }
  ```

召喚時、ハッチ(`$hatch`系・trigger未指定のパーツ含む)は閉じた状態でスポーンし、
搭乗時は搭乗前の状態を維持します。キャノピー(`$canopy`系)は召喚時に開いた状態で
スポーンし、搭乗時に自動で格納されます。手動トグル(Hキー)は両方を同じ状態に
揃えます。

### ハッチと連動する滑走路(`runways`のハッチ項目)

滑走路(`runways`、「12. 空母(Carrier)甲板・滑走路」参照)には、特定の
`toggle_parts`名には依存しない独立したハッチ連動項目(`hatch_gated`/
`hatch_offset_x/y/z`/`hatch_move_speed`)があります。見た目のハッチと連動
させたい場合は、数値をaddonパック側で個別に一致させてください。

---

## 9. 武器連動パーツ(`weapon_parts`)

- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: OBJモデル内の名前付きグループを、対応する武器の照準方向(搭乗者の
  視点)へ追従回転させます(砲塔・銃座等)。
- **例**(座席1の視点に追従する砲塔):
  ```json
  "weapon_parts": [
    { "part": "$turret", "seat_index": 1, "pivot_x": 0.0, "pivot_y": 1.0, "pivot_z": 0.0, "weapon_name": "cannon" }
  ]
  ```

各要素の項目:

### `part`
- **書式**: 文字列(必須)
- **説明**: 対象のOBJパーツ名。
- **例**: `"part": "$turret"`

### `seat_index`
- **書式**: 整数(既定値: `-1`)
- **説明**: このパーツが追従する座席のインデックス。
- **例**: `"seat_index": 1`

### `hide_for_gunner`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`の場合、この座席の搭乗者自身の視点からはこのパーツを非表示にします。
- **例**: `"hide_for_gunner": true`

### `yaw_follow` / `pitch_follow`
- **書式**: 真偽値(既定値いずれも`true`)
- **説明**: それぞれヨー・ピッチ方向の追従を有効にするか。
- **例**: `"yaw_follow": true, "pitch_follow": false`

### `pivot_x` / `pivot_y` / `pivot_z`
- **書式**: 浮動小数点数(既定値いずれも`0.0`)
- **説明**: 回転中心座標。
- **例**: `"pivot_x": 0.0, "pivot_y": 1.0, "pivot_z": 0.0`

### `recoil_distance`
- **書式**: 浮動小数点数(既定値: `0.0`)
- **説明**: 発射時、このパーツ自身のローカルZ軸負方向へ後退する距離(反動演出)。
- **例**: `"recoil_distance": 0.15`

### `pilot_fallback`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`の場合、`seat_index`の座席が無人のとき操縦席の視点を代わりに
  追従します。
- **例**: `"pilot_fallback": true`

### `child_info`
- **書式**: オブジェクト(Optional)
- **説明**: `AddPartWeaponChild`(親パーツの回転を無条件で継承する子パーツ)を
  表現する場合に設定します。
  - `parent_yaw_follow` / `parent_pitch_follow`(既定値いずれも`false`)
  - `parent_pivot_x` / `parent_pivot_y` / `parent_pivot_z`(既定値いずれも`0.0`):
    親パーツ自身のピボット
- **例**:
  ```json
  "child_info": { "parent_yaw_follow": true, "parent_pitch_follow": true, "parent_pivot_x": 0.0, "parent_pivot_y": 1.0, "parent_pivot_z": 0.0 }
  ```

### `aim_range`
- **書式**: オブジェクト(Optional、`weapons`の`aim_range`と同一形式)
- **説明**: このパーツが紐付く武器自身の照準範囲。設定すると、パーツの見た目の
  可動範囲も実際の発射方向のクランプと一致します。
- **例**:
  ```json
  "aim_range": { "min_yaw": -170.0, "max_yaw": 170.0, "min_pitch": -10.0, "max_pitch": 60.0 }
  ```

### `weapon_name`
- **書式**: 文字列(Optional)
- **説明**: このパーツが特定のどの武器に紐付くか(`seat_index`だけでは同一座席の
  複数武器を区別できないため)。
- **例**: `"weapon_name": "cannon"`

### `spins_while_firing`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`の場合(`AddPartRotWeapon`相当)、対応武器の発射中、このパーツ
  自身のローカルZ軸(銃身前方)を中心に連続回転します(ガトリング演出)。
  速度は対応武器側の`RotationSpeed`設定に従います。
- **例**: `"spins_while_firing": true`

> **補足**: 対応する武器の`turret_rotation_speed`(4章参照)を設定すると、
> 本章のパーツの追従速度に上限をかけられます。

---

## 10. サーチライト・航法灯

### `search_light_parts`
- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: MCヘリの`AddSearchLight`/`AddFixedSearchLight`/`AddSteeringSearchLight`
  に相当する、円錐状のサーチライト。各要素:
  - `part`(必須): 対象のOBJパーツ名
  - `pivot_x`/`pivot_y`/`pivot_z`(必須): 発光位置
  - `start_color_argb`/`end_color_argb`(必須、整数ARGB): 光源側・末端側の色
  - `length`/`end_radius`(必須): 光の長さと末端の半径
  - `yaw`/`pitch`(既定値いずれも`0.0`): 基準の向き
  - `steer_angle`(既定値`0.0`): `follow_mode: STEERING`時の最大振れ角
  - `follow_mode`(必須、`PILOT_VIEW`/`FIXED`/`STEERING`): 向きの決定方式
    - `PILOT_VIEW`: 現在操作中の搭乗者の視点に追従(`AddSearchLight`相当)
    - `FIXED`: 機体基準で固定(`AddFixedSearchLight`相当)
    - `STEERING`: 操舵角に追従(`AddSteeringSearchLight`相当)
- **例**:
  ```json
  "search_light_parts": [
    {
      "part": "$search_light",
      "pivot_x": 0.0, "pivot_y": 0.5, "pivot_z": 1.0,
      "start_color_argb": -1, "end_color_argb": 0,
      "length": 30.0, "end_radius": 6.0,
      "follow_mode": "PILOT_VIEW"
    }
  ]
  ```

### `nav_light_parts`
- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: 本プロジェクト独自追加。円錐状の広がりや操作を持たない、単純な定点光源
  (航海灯・航空灯向け)。機体が存在する限り常時点灯します(サーチライトと異なり
  オン/オフの切り替えはありません)。各要素:
  - `part`(必須): 対象のOBJパーツ名
  - `pivot_x`/`pivot_y`/`pivot_z`(必須): 発光位置
  - `color_argb`(必須、整数ARGB): 光の色。アルファ値が輝度を表すため、`0`にすると
    モデル上にパーツは存在するが発光しない状態にできます
- **例**(左翼端の赤色航空灯):
  ```json
  "nav_light_parts": [
    { "part": "$nav_light_l", "pivot_x": -5.0, "pivot_y": 0.0, "pivot_z": 0.0, "color_argb": -65536 }
  ]
  ```

---

## 11. スポーンアイテム(`spawn_item`)

- **書式**: オブジェクト(Optional)
- **説明**: 階層式スポナーアイテムでの表示・分類設定。
- **例**:
  ```json
  "spawn_item": { "display_name": "F-16 Fighting Falcon", "tier": 3 }
  ```

### `display_name`
- **書式**: 文字列(Optional)
- **説明**: 表示名。省略時は機体IDから自動生成されます。
- **例**: `"display_name": "F-16 Fighting Falcon"`

### `tier`
- **書式**: 整数(既定値: `1`、範囲1〜5)
- **説明**: このスポナーアイテムがこの機体を召喚可能になる階層。
- **例**: `"tier": 3`

---

---

## 12. 空母(Carrier)甲板・滑走路(`runways`)

**`entity_type`を問わず、あらゆる乗り物に設定できます。** 艦船に限定した機能では
なく、実装上も特定の乗り物タイプへの依存は一切ありません。平面に乗って立つ・
着陸するための当たり判定という意味では、車・航空機・ヘリコプター・固定砲座など
どの乗り物にも活用できます。例えば「空中空母」のような架空の航空機に滑走路を
持たせることも、この項目だけで実現できます。

**滑走路(`runways`)と艦載機発艦武器(`weapons`の`Type = CARRIER`、後述)は互いに
必須の関係ではなく、それぞれ単独でも利用できます。** 武器を持たず甲板としてのみ
滑走路を設定する(他の乗り物やプレイヤーが乗り降りできる足場として使う)ことも、
逆に`Type = CARRIER`の武器だけを設定し滑走路は定義しない(発艦した機体を
`CarrierLandingToAmmoRadius`・`CarrierRecoveryPoint`等、何らかの別の手段で回収
する前提とする)ことも、どちらも成立します。両方を組み合わせて「発艦させた
艦載機が実際にこの滑走路へ着艦して回収される」という一連の動作にすることも
もちろん可能で、これは空母の基本形として元々意図されている組み合わせです
(詳細は本セクション末尾を参照)。

- **書式**: `runway`(単一オブジェクト、旧式)または`runways`(オブジェクトの配列、
  複数甲板対応)
- **説明**: この機体自身の任意の位置に、他の乗り物が離着陸・停泊するための滑走路を
  定義します。この機体自身のJSONに直接設定します。実体としては見えない支持タイル
  (`CarrierRunwayPlatformEntity`)の集合が範囲全体に敷き詰められ、他の乗り物の
  降着装置・失速判定・当たり判定を、実際にブロックが存在するかのように扱います。
  複数の甲板・エレベーター等、独立した滑走路を複数持たせたい場合は`runways`配列を
  使用してください(両方同時に指定した場合、両方とも有効になります)。

- **例**(単一の甲板を持つ滑走路):
  ```json
  "runways": [
    { "width": 30.0, "center_x": 0.0, "height_y": 12.0, "start_z": -105.0, "end_z": 90.0 }
  ]
  ```

各要素の項目:

### `width`
- **書式**: 浮動小数点数(必須)
- **説明**: 滑走路の幅(ブロック)。
- **例**: `"width": 30.0`

### `center_x`
- **書式**: 浮動小数点数(既定値: `0.0`)
- **説明**: 滑走路の中心となる、機体基準からの左右方向オフセット。
- **例**: `"center_x": 0.0`

### `height_y`
- **書式**: 浮動小数点数(既定値: `0.0`)
- **説明**: 機体基準位置からの高さ。滑走路面はこの高さに位置します。
- **例**: `"height_y": 12.0`

### `start_z` / `end_z`
- **書式**: 浮動小数点数(必須)
- **説明**: 滑走路の前後方向の範囲(順不同)。
- **例**: `"start_z": -105.0, "end_z": 90.0`

座標は全てこの機体自身のエンティティ位置からの相対値で、機体の現在の向き
(ヨー)に合わせて回転します。実際のタイルサイズはサーバー設定
`carrierRunwayExpectedWidth`から自動算出されます。

### ハッチ連動runway(任意項目)

特定の`toggle_parts`名には一切依存しない、以下の任意項目を追加できます
(見た目のハッチと連動させたい場合は数値をaddonパック側で個別に一致させてください)。

- `hatch_gated`(既定値`false`): `true`の場合、この滑走路(とそのタイル)は
  ハッチが開いている間だけ存在します。回転タイプのハッチ(揚陸艇の船首道板等)
  向けの単純なon/off切り替えで、開閉の途中経過は考慮しません
  - **例**(揚陸艇の船首道板、ハッチ全開時のみ道板が実在する):
    ```json
    { "width": 3.0, "center_x": 0.0, "height_y": -0.5, "start_z": 4.0, "end_z": 10.0, "hatch_gated": true }
    ```
- `hatch_offset_x`/`hatch_offset_y`/`hatch_offset_z`(既定値いずれも`0.0`):
  非ゼロの場合、ハッチが開くにつれてこの滑走路の実効位置がこの値ぶんまで
  イージングしながらオフセットされます(閉じると元に戻ります)。上下・前後・
  左右の平行移動タイプのハッチ(空母のエレベーター甲板等)向け
  - **例**(格納庫の高さから甲板レベルまで8ブロック下降するエレベーター):
    ```json
    { "width": 5.0, "center_x": 0.0, "height_y": 2.0, "start_z": -3.0, "end_z": 3.0, "hatch_offset_y": -8.0, "hatch_move_speed": 2.0 }
    ```
- `hatch_move_speed`(既定値`1.0`): 上記オフセットのイージング速度(進捗/秒)。
  `toggle_parts`の`"$hatch"`プレフィックス付きパーツと同じ倍率補正(設定speedの
  2倍で動作)が自動的にかかるため、見た目のハッチと同じ速度にしたい場合は、
  対応する`toggle_parts`の`speed`と同じ数値を指定してください
  - **例**: `"hatch_move_speed": 2.0`

`hatch_gated`と`hatch_offset_*`は同時に指定することも技術的には可能ですが、
通常はどちらか一方のみの使用を想定しています。

複数の甲板・エレベーターを組み合わせた例:
```json
"runways": [
  { "width": 30.0, "center_x": 0.0, "height_y": 12.0, "start_z": -105.0, "end_z": 90.0 },
  { "width": 5.0, "center_x": 0.0, "height_y": 2.0, "start_z": -3.0, "end_z": 3.0, "hatch_offset_y": -8.0, "hatch_move_speed": 2.0 }
]
```

### 艦載機発艦武器(`Type = CARRIER`)との組み合わせ

滑走路(`runways`)は艦載機発艦武器がなくても単独で機能し、逆に`Type = CARRIER`の
武器も滑走路なしで単独で機能します(発艦した機体の回収は`CarrierLandingToAmmoRadius`
等、滑走路に依存しない手段で行われます)。

とはいえ、**「艦載機発艦武器で発艦させた機体が、この滑走路へ実際に着艦して回収
される」という一連の流れは、空母の基本形として元々意図されている組み合わせ**
です。武器側の設定(発艦ルート・着艦ルート等)は`Readme_Weapon_Cas.md`を参照して
ください。この組み合わせで使う場合、滑走路(`runways`)は発艦・着艦する乗り物の
物理的な足場・支持として機能し、この機体の移動・旋回・(ハッチ連動時は)昇降にも
追随させます。

---

## 13. パラシュート降下

パラシュート降下に関連する項目は、座席ごとの`enable_parachuting`
(「3. 座席」を参照)と、機体レベルの`enable_ejection_seat`・
`mob_drop_option`の3つです。いずれもOptionalで、指定しない場合は
既存の機体の挙動に一切影響しません。

### `enable_ejection_seat`
- **書式**: 真偽値(既定値: `false`)
- **説明**: パイロットがSpaceキーを長押しすることで、`enable_parachuting`
  が有効な座席の搭乗者(パイロット自身を含む)を一斉に脱出させられる
  ようにするかどうか。`enable_parachuting`が有効な座席の搭乗者には
  パラシュートが付与され、無効な座席の搭乗者は単純に強制降車します。
- **例**: `"enable_ejection_seat": true`

### `mob_drop_option`
- **書式**: オブジェクト(Optional)
- **説明**: パイロットがパラシュートジャンプ用の割り当てキーを短く
  押すことで、`enable_parachuting`が有効な座席(パイロット席を除く、
  番号の若い順)の搭乗者(mob)を、`interval_ticks`で指定した間隔で
  1体ずつ順番にパラシュート降下させる機能。設定しない場合、このキー
  操作は何も行いません。同じキーを長押しした場合は、`enable_ejection_seat`
  と同様に全座席が対象になり、`enable_parachuting`が有効かどうかに
  応じてパラシュート付与または強制降車が行われます。各要素:
  - `rel_x` / `rel_y` / `rel_z`(必須): 機体の基準位置からの、投下位置の
    相対座標(機体の現在の向きに合わせて回転します)。
  - `interval_ticks`(必須): 次のmobを投下するまでの間隔(tick数、
    1/20秒単位)。
- **例**:
  ```json
  "mob_drop_option": { "rel_x": 0.0, "rel_y": -0.5, "rel_z": 0.0, "interval_ticks": 20 }
  ```

---

これで本MODが解釈する機体JSONの共通項目は全てです。特定タイプのみで意味を持つ
項目(`wheel_parts`・`vtol_rotor_parts`等)については、各
`Readme_Vehicle_○○.md`を参照してください。
