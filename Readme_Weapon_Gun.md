# Readme_Weapon_Gun.md - 直射武器(`MachineGun1`/`MachineGun2`/`Rocket`)特有の項目

`Readme_Weapon.md`の共通項目に加え、これらのタイプでのみ意味を持つ項目です。
これらはいずれも誘導を持たない直進武器で、発射時の向きのまま(`Accuracy`で
誤差を加えた上で)重力に従って飛び続けます。

---

## 各タイプの違い

- **`MachineGun1`**: 機体固定方向(照準にAim_rangeを設定しない限り、常に発射位置
  自身の`mount_yaw`/`mount_pitch`の向き)に発射
- **`MachineGun2`**: 搭乗者の視点に向きを合わせて発射(通常`weapons`側で
  `aim_range`を設定して使用します)
- **`Rocket`**: `MachineGun1`同様、無誘導・機体固定方向

**例**(機関銃):
```
DisplayName = M134 Minigun
Type = MachineGun1
Power = 8
Acceleration = 4.0
Round = 100
ReloadTime = 80
Delay = 1
HeatCount = 20
MaxHeatCount = 150
```

`Acceleration`(弾速)は、この3タイプのみ最大100.0まで指定可能です
(他の武器タイプは最大4.0)。

---

## `MachineGun2`専用: `ModeNum`によるHE弾切り替え

- **書式**: 整数、`2`
- **説明**: `Type = MachineGun2`に`ModeNum = 2`を設定すると、専用キーで通常弾
  ⇔HE弾(`Explosion`が設定されている場合に爆発する弾)を切り替えられます。
  `Explosion`が`0`の場合は無効です。
- **例**:
  ```
  Type = MachineGun2
  ModeNum = 2
  Explosion = 1
  ```

---

## `Rocket`専用: `ModeNum`によるHEIAP弾切り替え

- **書式**: 整数、`2`
- **説明**: `Type = Rocket`に`ModeNum = 2`を設定すると、専用キーで通常弾
  ⇔HEIAP弾(空中で複数の子弾を撒く弾)を切り替えられます。子弾については
  `Bomblet`系の項目(下記)を参照してください。
- **例**:
  ```
  Type = Rocket
  ModeNum = 2
  Bomblet = 25
  BombletSTime = 5
  BombletDiff = 0.7
  ```

---

## 熱量式との組み合わせ

`MachineGun1`はガトリング等、連射武器として`HeatCount`/`MaxHeatCount`
(`Readme_Weapon.md`参照)と組み合わせることが多い武器タイプです。連射に
伴う視覚パーツの連続回転を表現したい場合は、機体側`weapon_parts`の
`spins_while_firing`と組み合わせてください(`Readme_Vehicle.md`参照)。

---

## クラスター(子弾散布)

`Rocket`(`ModeNum = 2`のHEIAP弾)や、通常の`Bomb`(`Readme_Weapon_Bomb.md`参照)
と組み合わせて使用できる項目です。

### `Bomblet`
- **書式**: 整数(既定値: `0`、無効)
- **説明**: 使用後に展開する子弾の数。
- **例**: `Bomblet = 25`

### `BombletSTime`
- **書式**: 整数(既定値: `0`)
- **説明**: 子弾が展開するまでの時間(tick)。
- **例**: `BombletSTime = 5`

### `BombletDiff`
- **書式**: 数値(既定値: `0.7`)
- **説明**: 子弾の拡散率。
- **例**: `BombletDiff = 0.7`

### `ModelBomblet`
- **書式**: 文字列(Optional)
- **説明**: 子弾自身の3Dモデル名(親弾の`ModelBullet`とは独立、省略時は親弾と
  同じ見た目)。
- **例**: `ModelBomblet = cbc`
