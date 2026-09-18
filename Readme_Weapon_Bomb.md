# Readme_Weapon_Bomb.md - 投下兵器(`Bomb`/`Depth`)特有の項目

`Readme_Weapon.md`の共通項目に加え、これらのタイプでのみ意味を持つ項目・
制約です。

---

## `Bomb`

真下に投下する爆弾です。以下の制約・挙動があります:

- 発射後は機体の速度を引き継いだ状態で開始し、`Gravity`に従って落下します。
- 水面に接触した瞬間に爆発します。水中に沈降させたい場合はDepth(後述)を使用してください。

**例**:
```
DisplayName = 500lb Bomb
Type = Bomb
Power = 100
Gravity = -0.05
Explosion = 8
ExplosionBlock = 8
Round = 2
ReloadTime = 800
```

### `Destruct`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`にすると、使用と同時に発射元の機体自身が自爆します。この機体が無人機(UAV)ヘリコプターの場合のみ効果があります。
- **例**: `Destruct = true`

---

## `Depth`

`Bomb`と全く同じ挙動・発射制限(姿勢制限含む)ですが、**水面での爆発が発生しません**。水中を沈み続け、実際に固体ブロック・エンティティに命中した場合(または他の信管設定)にのみ爆発します。`Bomb`の水面貫通版として使用してください。MCヘリには存在しない、本Mod独自のType名です。

**例**(対潜爆弾):
```
DisplayName = Depth Charge
Type = Depth
Power = 30
Gravity = -0.03
Explosion = 3
ExplosionInWater = 8
Round = 8
```

---

## クラスター(子弾散布)

`Bomblet`/`BombletSTime`/`BombletDiff`/`ModelBomblet`(`Readme_Weapon_Gun.md`
参照)を組み合わせることで、クラスター爆弾として使用できます。

**例**:
```
Type = Bomb
Bomblet = 30
BombletSTime = 6
BombletDiff = 0.8
ModelBomblet = samplebomblet
```
