# Readme_Weapon_Missile.md - 誘導兵器特有の項目

対象タイプ: `ASMissile` / `MkRocket` / `AAMissile` / `ATMissile` / `Missile` /
`ASWeapon` / `TVMissile`

`Readme_Weapon.md`の共通項目に加え、これらのタイプでのみ意味を持つ項目です。

---

## 各タイプの違い

### `ASMissile` / `MkRocket`
発射した瞬間の、搭乗者の視線が地面(水中の場合は水底)と交わる点を目標として、発射後に徐々に向きを変えながら飛行します。特定のエンティティをロックオンする機構は使用しません。誘導中は重力の影響を受けません。`MkRocket`は`ASMissile`と全く同じ挙動です(MCヘリ側の分類は別ですが、実装上の違いはありません)。

**例**:
```
DisplayName = Marker Missile
Type = ASMissile
Power = 20
Acceleration = 2.5
Gravity = -0.02
Explosion = 2
Round = 1
```

### `AAMissile` / `ATMissile` / `Missile`
発射キーを押した瞬間、照準(視線の中心付近、設定角度以内)にいる対象へ自動的にロックオンし、そのまま追尾します(対象が動けば毎tick方向を再計算)。ロック完了までは発射キーを押しても発射されません(下記`LockTime`参照)。対象が死亡・消滅した場合は、その後は通常の重力に従って落下します。

- **`AAMissile`**: **現在空中にいる**対象のみロックオン可能
- **`ATMissile`**: **現在地上または水面上にいる**対象のみロックオン可能
- **`Missile`**(本プロジェクト独自、MCヘリ原作には存在しないType名):
  `AAMissile`/`ATMissile`と同じロックオン・誘導・発射制限の挙動をしますが、
  空中/地上/水中の対象種別による絞り込みは一切行いません

**例**(対空ミサイル):
```
DisplayName = Ghast Hunter
Type = AAMissile
Power = 60
Acceleration = 2.0
LockTime = 10
RigidityTime = 5
ProximityFuseDist = 3.0
Sight = MissileSight
Round = 8
```

「空中」「地上」の判定は対象の**現在の物理的な状態**に基づきます(機体の種別ではありません)。地面から一定高度以上離れており水に触れていない場合は「空中」、水中に潜っている(目の位置が水面下)場合は「水中」として区別され、いずれも`AAMissile`/`ATMissile`/`Missile`ではロックできません(水中の目標には`ASWeapon`を使用してください)。水面上に浮いている・立っている状態は「地上」扱いで`ATMissile`の対象になります。

### `ASWeapon`(本プロジェクト独自、MCヘリ原作には存在しないType名)
対潜ミサイルです。`ASMissile`同様、発射時の照準地点(水中の目標であれば、その水底付近の座標)を目標として誘導飛行します(ロックオンではありません)。目標までの距離が`DiveDistance`ブロック以内になるまでは、目標の水平座標へ向けて水面のやや上を飛行し続け、その距離まで近づいた時点で目標そのものへ向けて潜航を開始します。着水した瞬間からは、魚雷(`Torpedo`)と全く同じ水中巡航システムに切り替わります(`AccelerationInWater`/`VelocityInWater`/`TargetDepth`をそのまま流用、`Readme_Weapon_Torpedo.md`参照)。弾頭分離など実際の対潜兵器が持つ複雑な多段階動作は実装していません。

**例**:
```
DisplayName = RUM-139 VL-ASROC
Type = ASWeapon
Power = 55
Acceleration = 3.0
DiveDistance = 10.0
AccelerationInWater = 2.0
VelocityInWater = 0.3
TargetDepth = 3.0
Explosion = 6
ExplosionInWater = 6
Round = 4
```

### `TVMissile`
発射後、`ModeNum`未設定(またはModeNum=1)の場合は、通常の無誘導弾と同様に
発射方向へ直進しつつ、搭乗者が発射後もマウス操作でミサイル自身を直接操縦
できます(発射元の機体からの操縦は一時的に手放されます)。`ModeNum = 2`を
設定すると、通常の誘導弾モードに切り替わります(`ASMissile`同様、搭乗者操縦は
行わず発射時の照準地点へ誘導)。

**例**(搭乗者操縦式):
```
DisplayName = TV Missile
Type = TVMissile
Power = 20
Acceleration = 3.0
Explosion = 5
Round = 4
```

---

## ロックオン系(`AAMissile`/`ATMissile`/`Missile`)専用項目

### `LockTime`
- **書式**: 整数(既定値: `0`、即座にロック)
- **説明**: 同一対象を照準内に維持し続ける必要のあるtick数。この時間が経過
  するまでは発射キーを押しても発射されません(本プロジェクトでは、ロック
  完了前の無誘導発射は行いません)。
- **例**: `LockTime = 40`

### `RidableOnly`
- **書式**: 真偽値
- **説明**: MCヘリでは「機体に乗っている状態でのみロック可能」を意味しますが、本プロジェクトの武器システムはそもそも全て機体搭載専用(携行武器アイテムが存在しない)のため、値に関わらず実質的な効果はありません。互換性のためパース・保持のみ行っています。
- **例**: `RidableOnly = true`

### `ProximityFuseDist`
- **書式**: 数値(既定値: `0`、無効)
- **説明**: ロック済みの誘導弾が、実際に命中しなくても目標からこの距離(ブロック)以内に近づいた時点で起爆します。
- **例**: `ProximityFuseDist = 3.0`

### `RigidityTime`
- **書式**: 整数(既定値: `7`)
- **説明**: 発射から実際に誘導(進路修正)を開始するまでの時間(tick)。発射直後は真っ直ぐ飛び、発射母機から物理的に十分離れてから誘導を開始します。
- **例**: `RigidityTime = 7`

---

## `ASMissile`/`MkRocket`/`ASWeapon`共通項目

### `DiveDistance`
- **書式**: 数値(既定値: `10.0`)
- **説明**: `ASWeapon`専用。目標までの距離がこの値(ブロック)以下になった時点で潜航を開始します。
- **例**: `DiveDistance = 10.0`

---

## `ATMissile`専用: Top Attackモード

### `ModeNum`
- **書式**: 整数、`2`
- **説明**: `Type = ATMissile`に`ModeNum = 2`を設定すると、専用キーでTop Attackモード(通常誘導⇔目標上空への上昇後、急降下する誘導方式)を切り替えられます。
- **例**:
  ```
  Type = ATMissile
  ModeNum = 2
  LockTime = 60
  ```
