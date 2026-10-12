# Readme_Vehicle_Car.md - 車両・戦車(`entity_type: car`)特有の項目

`Readme_Vehicle.md`の共通項目に加え、`entity_type: "tudursvehiclemod:car"`を
指定した機体でのみ意味を持つ項目です。車輪式・履帯式のいずれの地上車両にも
このタイプを使用します。

---

## 操舵・移動特性

### `pivot_turn_throttle`
- **書式**: 浮動小数点数(既定値: `0.0`、`Readme_Vehicle.md`参照)
- **説明**: `0`(既定)では、スロットル0のままその場旋回(超信地旋回)が可能です。
  `0`より大きい値を設定すると、`max_speed`に対するこの割合の速度に達するまで
  旋回できなくなり(戦車のような信地旋回)、旋回入力があると自動的にこの速度
  まで加速します。
- **例**:
  ```json
  "pivot_turn_throttle": 0.15
  ```

### `wheel_rotation_speed`
- **書式**: 浮動小数点数(既定値: `40.0`)
- **説明**: `wheel_parts`のタイヤ回転速度係数。現在の速度に乗算されるだけの
  単純な倍率で、実タイヤ径に基づく計算は行いません。
- **例**:
  ```json
  "wheel_rotation_speed": 40.0
  ```

---

## `wheel_parts`(タイヤ)

- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: MCヘリの`AddPartWheel`に相当。スピン回転(進行に応じた回転)と、
  操舵回転(ハンドル操作に応じた向きの変化)の2つを合成して表現します
  (スピンが内側、操舵が外側に適用されます)。
- **例**(前輪2つが操舵、後輪2つは非操舵):
  ```json
  "wheel_parts": [
    { "part": "$wheel_fl", "pivot_x": -0.8, "pivot_y": 0.4, "pivot_z": 1.5, "steer_angle": 30.0 },
    { "part": "$wheel_fr", "pivot_x": 0.8, "pivot_y": 0.4, "pivot_z": 1.5, "steer_angle": 30.0 },
    { "part": "$wheel_rl", "pivot_x": -0.8, "pivot_y": 0.4, "pivot_z": -1.5 },
    { "part": "$wheel_rr", "pivot_x": 0.8, "pivot_y": 0.4, "pivot_z": -1.5 }
  ]
  ```

各要素の項目:

### `part`
- **書式**: 文字列(必須)
- **説明**: 対象のOBJパーツ名。
- **例**: `"part": "$wheel_fl"`

### `pivot_x` / `pivot_y` / `pivot_z`
- **書式**: 浮動小数点数(必須)
- **説明**: タイヤの位置座標(スピン回転はこの座標を中心にローカルX軸で行われます)。
- **例**: `"pivot_x": -0.8, "pivot_y": 0.4, "pivot_z": 1.5`

### `steer_angle`
- **書式**: 浮動小数点数(既定値: `0.0`)
- **説明**: このタイヤが操舵入力に応じて振れる最大角度(度)。`0`なら操舵しません
  (後輪等)。
- **例**: `"steer_angle": 30.0`

### `steer_axis_x` / `steer_axis_y` / `steer_axis_z`
- **書式**: 浮動小数点数(既定値`0.0`/`1.0`/`0.0`)
- **説明**: 操舵回転軸。省略時は垂直軸(キングピン軸)。
- **例**: `"steer_axis_x": 0.0, "steer_axis_y": 1.0, "steer_axis_z": 0.0`

### `steer_pivot_x` / `steer_pivot_y` / `steer_pivot_z`
- **書式**: 浮動小数点数(既定値いずれも`0.0`)
- **説明**: 操舵回転の中心座標。省略時はこのタイヤ自身の`pivot_x/y/z`と同じ。
- **例**: `"steer_pivot_x": -0.8, "steer_pivot_y": 0.4, "steer_pivot_z": 1.5`

---

## `steering_wheel_parts`(ハンドル)

- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: MCヘリの`AddPartSteeringWheel`に相当。運転席内で見えるハンドル自体の
  モデルを、操舵入力に応じて回転させます(タイヤのスピン・操舵とは独立、
  ハンドル自体の1軸回転のみ)。各要素:
  - `part`(必須): 対象のOBJパーツ名
  - `pivot_x`/`pivot_y`/`pivot_z`(必須): 回転中心座標
  - `axis_x`/`axis_y`/`axis_z`(既定値`0.0`/`0.0`/`1.0`): 回転軸
  - `max_angle`(既定値`130.0`): 最大操舵時の回転角度(度)
- **例**:
  ```json
  "steering_wheel_parts": [
    { "part": "$steering_wheel", "pivot_x": 0.4, "pivot_y": 0.9, "pivot_z": 1.0, "max_angle": 130.0 }
  ]
  ```

---

## `crawler_tracks`(キャタピラ)

- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: MCヘリの`AddCrawlerTrack`に相当。1つの履帯コマモデルを、閉じたループ状の
  経路上に連続配置して表現します。
- **例**(左右2本の履帯):
  ```json
  "crawler_tracks": [
    {
      "part": "$track_link", "flip": false, "link_spacing": 0.5, "x": -1.2,
      "path": [ { "y": 0.0, "z": 2.0 }, { "y": 0.6, "z": 2.0 }, { "y": 0.6, "z": -2.0 }, { "y": 0.0, "z": -2.0 } ]
    },
    {
      "part": "$track_link", "flip": true, "link_spacing": 0.5, "x": 1.2,
      "path": [ { "y": 0.0, "z": 2.0 }, { "y": 0.6, "z": 2.0 }, { "y": 0.6, "z": -2.0 }, { "y": 0.0, "z": -2.0 } ]
    }
  ]
  ```

各要素の項目:

### `part`
- **書式**: 文字列(必須)
- **説明**: 履帯コマのOBJパーツ名(1コマ分のモデル)。
- **例**: `"part": "$track_link"`

### `flip`
- **書式**: 真偽値(既定値: `false`)
- **説明**: 履帯の表裏を反転させます。
- **例**: `"flip": true`

### `link_spacing`
- **書式**: 浮動小数点数(既定値: `0.5`)
- **説明**: 履帯コマ同士の間隔。
- **例**: `"link_spacing": 0.5`

### `x`
- **書式**: 浮動小数点数(必須)
- **説明**: この履帯の左右位置。負の値=右側、正の値=左側という規則で、旋回時に
  左右の履帯が逆方向(その場旋回時)・異なる速度(通常旋回時)で動く差動操舵の
  計算に使われます。
- **例**: `"x": -1.2`

### `path`
- **書式**: `{y, z}`オブジェクトの配列(必須)
- **説明**: 履帯コマが辿る閉じたループ経路上のY/Z座標点列。
- **例**:
  ```json
  "path": [ { "y": 0.0, "z": 2.0 }, { "y": 0.6, "z": 2.0 }, { "y": 0.6, "z": -2.0 }, { "y": 0.0, "z": -2.0 } ]
  ```

---

## `track_roller_parts`(誘導輪・転輪)

- **書式**: オブジェクトの配列(既定値: 空配列)
- **説明**: MCヘリの`AddTrackRoller`に相当。履帯の上を転がる小さな転輪です。
  対応する`crawler_tracks`(X座標の符号で自動的にマッチング)の実際の移動速度に
  完全に同期して自転します(独立した固定速度での回転ではありません)。
- **例**:
  ```json
  "track_roller_parts": [
    { "part": "$roller1_l", "pivot_x": -1.2, "pivot_y": 0.3, "pivot_z": 1.5 },
    { "part": "$roller2_l", "pivot_x": -1.2, "pivot_y": 0.3, "pivot_z": 0.0 }
  ]
  ```

各要素の項目:
- `part`(必須): 対象のOBJパーツ名。**例**: `"part": "$roller1_l"`
- `pivot_x`/`pivot_y`/`pivot_z`(必須): 位置座標(ローカルX軸で自転)。
  **例**: `"pivot_x": -1.2, "pivot_y": 0.3, "pivot_z": 1.5`
- `rotations_per_block`(Optional): 履帯1ブロック分の移動あたりの回転数
  (転輪の円周から算出される値)。省略時はモデル形状から自動計算されたキャッシュ
  値を使用します。**例**: `"rotations_per_block": 0.8`

---

## 補足

- `runways`(`Readme_Vehicle_Ship.md`参照)・`vtol_rotor_parts`
  (`Readme_Vehicle_Vtol.md`参照)は使用しません
- 降着装置・失速の概念はありません(`stall_speed`は無視されます)
