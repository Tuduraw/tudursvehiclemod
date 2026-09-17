# Readme_Weapon_Special.md - 特殊武器特有の項目

対象タイプ: `Dispenser` / `Smoke` / `Dummy` / `TargetingPod` / `DropTank`

`Readme_Weapon.md`の共通項目に加え、これらのタイプでのみ意味を持つ項目です。
`DropTank`以外は実弾を発射する挙動ではありません。

---

## `Dummy`

使用不可能な武器です。武器欄に文字を表示するためだけに使用します(例:
武器ベイと組み合わせた見た目の水増し等)。専用の追加設定項目はありません。
使用しても発射音・弾体・クールダウンのいずれも一切発生しません。

**例**:
```
DisplayName = ---
Type = Dummy
```

---

## `Dispenser`

着弾地点に対して、設定したMinecraftのアイテムを使用したのと同じ効果を
発生させます(実際に着弾する見た目の弾道は表示されます)。

**例**(消火用の水入りバケツ効果):
```
DisplayName = Fire Extinguisher
Type = Dispenser
DispenseItem = water_bucket
DispenseRange = 4
Acceleration = 2.0
Gravity = -0.03
Round = 5
```

### `DispenseItem`
- **書式**: 文字列(アイテムID、Optional)
- **説明**: 着弾地点に対して使用するアイテム。例: `flint_and_steel`を指定すると
  着弾地点に火打ち石を使用したのと同じ効果(着火)が発生します。効果の有無は
  アイテムによって異なります。例外として`water_bucket`は水を設置するのでは
  なく、着弾地点付近の火・溶岩を消火する効果になります。
- **例**: `DispenseItem = flint_and_steel`

### `DispenseRange`
- **書式**: 数値(既定値: `4.0`)
- **説明**: `DispenseItem`の効果が適用される範囲(ブロック)。
- **例**: `DispenseRange = 4`

---

## `Smoke`

発射元の現在位置に、設定した色・サイズ・持続時間のパーティクルの塊を
発生させるだけの武器です(飛行機雲の演出等)。

**例**:
```
DisplayName = Smoke White
Type = Smoke
SmokeColor = 230, 255, 255, 255
SmokeSize = 2.0
SmokeMaxAge = 500
Delay = 1
```

### `SmokeColor`
- **書式**: `A, R, G, B`(各0〜255)、または`0xAARRGGBB`形式(既定値: 半透明の
  黄褐色)
- **説明**: スモークの色。
- **例**: `SmokeColor = 230, 200, 20, 80`

### `SmokeSize`
- **書式**: 数値(既定値: `2.0`)
- **説明**: スモークの粒子サイズ。
- **例**: `SmokeSize = 2.0`

### `SmokeMaxAge`
- **書式**: 整数(既定値: `500`)
- **説明**: スモークの表示時間(tick)。
- **例**: `SmokeMaxAge = 500`

---

## `TargetingPod`

モブ・プレイヤーへのスポット表示、またはブロックへのマーク表示を行います
(実際のダメージは発生しません)。

**例**:
```
DisplayName = Targeting Pod
Type = TargetingPod
Target = planes/helicopters/vehicles
Length = 100
Radius = 45
MarkTime = 10
```

### `Target`
- **書式**: `/`区切りの複数指定可(例: `monsters/others`)
- **説明**: スポット対象の種類。以下から選択:
  - `planes` / `helicopters` / `vehicles`: 本MODの各種機体
  - `players`: 他のプレイヤー
  - `monsters`: モンスター
  - `others`: その他のモブ
  - `block`: ブロックをマークするモードに切り替わります(他の指定は
    全て無効化されます。エンティティのスポット機能ではなくなります)
- **例**: `Target = planes/helicopters/vehicles`

### `Length`
- **書式**: 数値(既定値: `100.0`)
- **説明**: スポット可能な距離(ブロック)。
- **例**: `Length = 100`

### `Radius`
- **書式**: 数値(既定値: `45.0`)
- **説明**: スポット可能な範囲の半径角度(度)。
- **例**: `Radius = 45`

### `MarkTime`
- **書式**: 数値、単位は秒(既定値: `10.0`)
- **説明**: スポット表示の持続時間。
- **例**: `MarkTime = 10`

---

## `DropTank`

このプロジェクト独自の拡張武器タイプです(MCヘリ本来のTypeではありません)。
外付けの増槽(燃料タンク)を表現します。装備している間、残弾数に応じて
機体の最大燃料を一時的に増加させます。使用(投下)すると残弾が1減り、
それに伴い最大燃料の増加分も減少します。減少後の最大燃料を現在の燃料が
上回っている場合、超過分は消滅します(補給・回復されません)。

`BulletModel`(弾丸モデル)を設定した場合、`Bomb`と全く同じ挙動
(機体の現在の速度を引き継いだまま、自機の姿勢に関わらず投下可能)で
落下します。落下地点への実際の作用(爆発の有無等)は、他の武器と同様に
その他の設定項目に依存します。

### `FuelPerAmmo`
- **書式**: 数値(既定値: `0.0`)
- **説明**: 残弾1発ごとに増加する最大燃料の量。実際の増加分は
  `FuelPerAmmo × 現在の残弾数`で、残弾が減る(投下する)たびに
  自動的に再計算されます。省略時(既定)は最大燃料に一切影響しません。
- **例**: `FuelPerAmmo = 50.0`

**例**:
```
DisplayName = 増槽
Type = DropTank
MagazineNum = 2
FuelPerAmmo = 50.0
BulletModel = drop_tank
```

