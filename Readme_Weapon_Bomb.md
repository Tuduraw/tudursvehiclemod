# Readme_Weapon_Bomb.md - 投下兵器(`Bomb`/`Depth`)特有の項目

`Readme_Weapon.md`の共通項目に加え、これらのタイプでのみ意味を持つ項目・
制約です。

---

## `Bomb`

真下に投下する爆弾です。以下の制約・挙動があります:

- **発射制限**: 機体の姿勢がほぼ水平(ピッチ・ロールいずれも±15度以内)で
  なければ使用できません
- 発射後は機体の速度を引き継いだ状態で開始し、`Gravity`に従って落下します
  (固定初速は使用しません)
- 水面に接触した瞬間に爆発します(`Explosion`/`ExplosionInWater`のいずれかが
  設定されていれば)

**例**:
```
DisplayName = Mk82 500lb Bomb
Type = Bomb
Power = 40
Gravity = -0.05
Explosion = 4
ExplosionBlock = 4
Round = 4
ReloadTime = 200
```

### `Destruct`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`にすると、使用と同時に発射元の機体自身が自爆します。この
  機体が無人機(UAV)ヘリコプターの場合のみ効果があります。
- **例**: `Destruct = true`

---

## `Depth`

`Bomb`と全く同じ挙動・発射制限(姿勢制限含む)ですが、**水面での爆発が
発生しません**。水中を沈み続け、実際に固体ブロック・エンティティに命中した
場合(または他の信管設定)にのみ爆発します。`Bomb`の水面貫通版として使用
してください。MCヘリ原作には存在しない、本プロジェクト独自のType名です。

**例**(対潜爆弾):
```
DisplayName = Depth Charge
Type = Depth
Power = 30
Gravity = -0.03
Explosion = 3
ExplosionInWater = 3
Round = 8
```

---

## クラスター(子弾散布)

`Bomblet`/`BombletSTime`/`BombletDiff`/`ModelBomblet`(`Readme_Weapon_Gun.md`
参照)を組み合わせることで、クラスター爆弾として使用できます。

**例**:
```
Type = Bomb
Bomblet = 25
BombletSTime = 5
BombletDiff = 0.7
ModelBomblet = cbc
```
