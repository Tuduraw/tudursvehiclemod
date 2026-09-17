# Readme_Weapon_Torpedo.md - 魚雷(`Torpedo`)特有の項目

`Readme_Weapon.md`の共通項目に加え、`Type = Torpedo`でのみ意味を持つ項目です。

---

## 発射制限

- 機体の姿勢がほぼ水平(ピッチ・ロールいずれも±15度以内)、かつ低高度
  (直下の地面/水面までの距離で判定)でなければ使用できません
- 潜水艦(`Readme_Vehicle_Submarine.md`参照)は、潜航中(ハッチ閉)でも
  この武器タイプのみ使用できます

---

## 挙動

発射直後は機体の速度を引き継ぎ、`Gravity`に従って落下します。**着水した瞬間から
専用の水中巡航フェーズに切り替わり**、以降は`Gravity`の影響を受けなくなります。

**例**:
```
DisplayName = Mk46 Torpedo
Type = Torpedo
Power = 60
Gravity = -0.03
AccelerationInWater = 2.5
VelocityInWater = 0.3
TargetDepth = 3.0
GuidedTorpedo = true
Explosion = 6
ExplosionInWater = 6
Round = 2
```

## `AccelerationInWater`
- **書式**: 数値(既定値: `4.0`、最大4.0)
- **説明**: 水中巡航時の目標速度。着水直後からこの速度まで徐々に加速します。
- **例**: `AccelerationInWater = 2.5`

## `VelocityInWater`
- **書式**: 数値(既定値: `0.5`)
- **説明**: 水中での速度変化の応答速度(tickごとにこの割合で目標速度へ近づく
  イージング係数)。
- **例**: `VelocityInWater = 0.3`

## `TargetDepth`
- **書式**: 数値(既定値: `2.0`)
- **説明**: 本プロジェクト独自の追加項目(MCヘリ非対応)。水面からこのブロック数
  だけ下を目標巡航深度とします(`水面Y - TargetDepth`)。
- **例**: `TargetDepth = 3.0`

## `GuidedTorpedo`
- **書式**: 真偽値(既定値: `true`)
- **説明**: `true`(既定)の場合、着水時に指定されたブロック(発射時の照準地点)へ
  向けて誘導します(水平方向は固定、着水時のピッチを目標深度に向けて徐々に
  補正)。`false`の場合、無誘導魚雷になり、着水した位置からまっすぐ進みます
  (`AccelerationInWater`/`VelocityInWater`による速度制御は引き続き適用され、
  進路の補正のみ行われません)。
- **例**: `GuidedTorpedo = false`

## `GravityInWater`
- **書式**: 数値(既定値: 通常の`Gravity`と同値)
- **説明**: 水中に入るまでの落下速度とは別の、水没後専用の落下速度
  (`Readme_Weapon.md`の共通項目としても使用できますが、魚雷では特に意味を
  持ちます)。
- **例**: `GravityInWater = 0.0`
