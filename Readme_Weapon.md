# Readme_Weapon.md - 武器設定ファイル(共通項目)

対象ファイル: `assets/<namespace>/weapons/<weapon_name>.txt`

MCヘリ形式の`Key = Value`テキスト形式です(JSONではありません)。キー名は大文字・
小文字を区別しません。行頭が`;`の行、および`=`を含まない行はコメント/無視されます。
値の途中に`;`がある場合、それ以降は同一行内コメントとして無視されます。

機体側のJSON定義(`weapons`配列)からは、`weapon_name`でこのファイル名(拡張子なし)
を参照します。ファイル自体はいつでも編集・`/reload`で即座に反映可能です
(機体を再召喚する必要はありません)。

本ドキュメントには、`Type`の値に関わらず全ての武器タイプに共通する設定項目を
記載しています。特定タイプにのみ意味を持つ項目は、対応する
`Readme_Weapon_○○.md`を参照してください。

- `Readme_Weapon_Gun.md` — 直射武器(`MachineGun1`/`MachineGun2`/`Rocket`)
- `Readme_Weapon_Bomb.md` — 投下兵器(`Bomb`/`Depth`)
- `Readme_Weapon_Torpedo.md` — 魚雷(`Torpedo`)
- `Readme_Weapon_Missile.md` — 誘導兵器(`ASMissile`/`MkRocket`/`AAMissile`/
  `ATMissile`/`Missile`/`ASWeapon`/`TVMissile`)
- `Readme_Weapon_Special.md` — 特殊武器(`Dispenser`/`Smoke`/`Dummy`/`TargetingPod`)
- `Readme_Weapon_Cas.md` — 支援機発艦武器(`CAS`/`Carrier`)

各項目の見出しの下に、実際の記載例を`例:`として付けています。

---

## 目次

1. 基本情報
2. ダメージ
3. 装弾数・リロード・補給
4. 弾道・貫通・命中精度
5. 爆発・炎上・信管
6. サウンド
7. 見た目(弾体・軌跡・マズルフラッシュ・薬莢)
8. 熱量式(オーバーヒート)
9. 照準・カメラ
10. その他

---

## 1. 基本情報

### `DisplayName`
- **書式**: 文字列(半角英数字と記号のみ、全角不可)
- **説明**: メニュー等で表示される武器名。
- **例**: `DisplayName = M134 Minigun`

### `Type`
- **書式**: 文字列
- **説明**: 武器の挙動タイプ。必須項目です。使用可能な値と詳細は各
  `Readme_Weapon_○○.md`を参照してください:
  `MachineGun1`/`MachineGun2`/`Rocket`(`Readme_Weapon_Gun.md`)、
  `Bomb`/`Depth`(`Readme_Weapon_Bomb.md`)、`Torpedo`(`Readme_Weapon_Torpedo.md`)、
  `ASMissile`/`MkRocket`/`AAMissile`/`ATMissile`/`Missile`/`ASWeapon`/`TVMissile`
  (`Readme_Weapon_Missile.md`)、
  `Dispenser`/`Smoke`/`Dummy`/`TargetingPod`(`Readme_Weapon_Special.md`)、
  `CAS`/`Carrier`(`Readme_Weapon_Cas.md`)。
  未対応・不明な値は全て「その他」として扱われ、実弾を発射する挙動には
  なりません。
- **例**: `Type = MachineGun1`

### `Group`
- **書式**: 文字列(Optional)
- **説明**: 同じグループ名(大文字小文字を区別した完全一致)の武器同士は、
  発射待ち時間(`Delay`)とリロード時間(`ReloadTime`)を共有します。いずれか
  1つを使用すると、同じグループの他の武器も同時にクールダウン・リロード
  状態になります(弾数自体は各武器で独立)。武器Aを撃ってすぐ武器B(同じ砲の
  別弾種)に切り替えて即座撃つ、という挙動を防ぐための項目です。
- **例**: `Group = MainGun`

---

## 2. ダメージ

### `Power`
- **書式**: 数値
- **説明**: 基礎ダメージ量。
- **例**: `Power = 8`

### `DamageFactor`
- **書式**: `対象種別, 倍率`(複数行指定可)
- **説明**: 対象種別ごとのダメージ倍率。対象種別は`player`/`heli`(または
  `helicopter`)/`plane`/`tank`/`vehicle`のいずれか。複数行書くとそれぞれ
  反映されます。
- **例**:
  ```
  DamageFactor = tank, 2.0
  DamageFactor = player, 1.0
  ```

---

## 3. 装弾数・リロード・補給

### `Round`
- **書式**: 整数(既定値: `0`)
- **説明**: マガジン装弾数。`0`(または省略)は無制限(リロードサイクルなし)。
- **例**: `Round = 100`

### `ReloadTime`
- **書式**: 整数(既定値: `0`)
- **説明**: リロード完了までの待ち時間(tick単位)。
- **例**: `ReloadTime = 80`

### `Delay`
- **書式**: 数値(既定値: `0`)
- **説明**: 次の使用までの待ち時間(tick単位)。
- **例**: `Delay = 5`

### `MaxAmmo`
- **書式**: 整数(既定値: `0`)
- **説明**: この武器が保持できる予備弾数の総量(マガジンとは別の独立したプール)。
  `0`(または省略)は無制限で、自動リロードが際限なく続きます。上限がある場合、
  予備弾が尽きると自動リロードが止まります。
- **例**: `MaxAmmo = 40`

### `SuppliedNum`
- **書式**: 整数(既定値: `0`)
- **説明**: 1回の弾薬補給操作で追加される弾数(`MaxAmmo`が上限)。
- **例**: `SuppliedNum = 10`

### `Item`
- **書式**: `個数, アイテムID`(最大3行、`iron_ingot`/`gunpowder`/`redstone`の
  いずれかのみ指定可)
- **説明**: 1回の補給に必要なアイテムと個数。
- **例**(鉄インゴット3個・火薬4個・レッドストーン2個で10発補給):
  ```
  Item = 3, iron_ingot
  Item = 4, gunpowder
  Item = 2, redstone
  ```

---

## 4. 弾道・貫通・命中精度

### `Acceleration`
- **書式**: 数値
- **説明**: 弾速。`MachineGun1`/`MachineGun2`/`Rocket`のみ最大100.0まで指定可能、
  それ以外は最大4.0。
- **例**: `Acceleration = 4.0`

### `Gravity`
- **書式**: 数値
- **説明**: 弾頭の落下速度(絶対値が大きいほど早く落下)。
- **例**: `Gravity = -0.04`

### `Piercing`
- **書式**: 整数(既定値: `0`)
- **説明**: 着弾したブロックを追加で何個貫通できるか。`0`は貫通なし。
- **例**: `Piercing = 2`

### `Accuracy`
- **書式**: 数値(既定値: `0`)
- **説明**: 発射時に一度だけ適用される角度誤差(度)。無誘導武器
  (`MachineGun1`/`MachineGun2`/`Rocket`/`MkRocket`)のみ意味を持ちます。
  誘導兵器は発射後に軌道修正するため、この項目の影響を実質的に受けません。
- **例**: `Accuracy = 1`

### `BulletColor`
- **書式**: `A, R, G, B`(各0〜255)、または`0xAARRGGBB`形式
- **説明**: 空中飛行中の弾体・軌跡の色。既定は不透明の白(色付けなし)。
- **例**: `BulletColor = 255, 255, 255, 255`

### `BulletColorInWater`
- **書式**: `BulletColor`と同形式
- **説明**: 水中を移動中の弾体の色。`BulletColor`を上書きします。
- **例**: `BulletColorInWater = 255, 25, 25, 75`

---

## 5. 爆発・炎上・信管

### `Explosion`
- **書式**: 数値(既定値: `0`)
- **説明**: 着弾時の爆発威力(`0`=爆発なし、`1`=ガスト弾相当)。
- **例**: `Explosion = 2`

### `ExplosionInWater`
- **書式**: 数値(既定値: `Explosion`と同値)
- **説明**: 水中での着弾時の爆発威力。
- **例**: `ExplosionInWater = 0`

### `ExplosionBlock`
- **書式**: 数値
- **説明**: 着弾時のブロック破壊力。省略時・`0`はブロックを破壊しません。
- **例**: `ExplosionBlock = 2`

### `ExplosionAltitude`
- **書式**: 数値(既定値: `0`、無効)
- **説明**: この弾体自身の直下の地面からの高さがこの値以下になった時点で、
  何かに命中していなくても強制的に爆発します(空中炸裂)。
- **例**: `ExplosionAltitude = 10`

### `Flaming`
- **書式**: 真偽値(既定値: `false`)
- **説明**: 着弾時に炎を撒くかどうか。`Explosion > 0`の場合のみ有効。
- **例**: `Flaming = true`

### `FAE`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `true`にすると燃料気化爆弾になり、`ExplosionBlock`の設定に関わらず
  一切ブロックを破壊しなくなります。
- **例**: `FAE = true`

### `DelayFuse`
- **書式**: 整数(既定値: 未設定、命中即消滅)
- **説明**: 着弾から実際に消滅するまでの遅延(tick)。`Explosion`/
  `ExplosionInWater`が設定されている場合、消滅時に爆発します。`Bound`
  (跳ね返り)と併用しないと、着弾直後に消滅してしまうためほぼ意味を
  なしません。
- **例**: `DelayFuse = 30`

### `TimeFuse`
- **書式**: 整数(既定値: 未設定)
- **説明**: 発射から(命中の有無に関わらず)消滅するまでの時間(tick)。
  `DelayFuse`とは独立した、単純な生存時間カウントダウンです。
- **例**: `TimeFuse = 30`

### `Bound`
- **書式**: 数値(既定値: `0`、跳ね返りなし)
- **説明**: 着弾時の跳ね返りの強さ。
- **例**: `Bound = 0.4`

---

## 6. サウンド

### `Sound`
- **書式**: 文字列(Optional)
- **説明**: 使用音声のファイル名(拡張子不要)。省略時は`武器名_snd`が使われます。
- **例**: `Sound = rocket_snd`

### `SoundVolume`
- **書式**: 数値(既定値: `1.0`)
- **説明**: 音量。Minecraftの仕様上`1.0`が最大音量で、それを超えると聞こえる
  距離が伸びます。
- **例**: `SoundVolume = 3`

### `SoundPitch`
- **書式**: 数値、0.0〜1.0(既定値: `1.0`)
- **説明**: 音の高さ。
- **例**: `SoundPitch = 1.0`

### `SoundPitchRandom`
- **書式**: 数値、0.0〜1.0(既定値: `0.0`)
- **説明**: 音の高さのランダムな変動幅。
- **例**: `SoundPitchRandom = 0.1`

### `SoundDelay`
- **書式**: 整数(既定値: `0`)
- **説明**: 連続発射時、次の音を鳴らすまでの最低間隔(tick)。連射武器で音声が
  過剰に重なるのを防ぎます。
- **例**: `SoundDelay = 1`

---

## 7. 見た目(弾体・軌跡・マズルフラッシュ・薬莢)

### `ModelBullet`
- **書式**: 文字列(Optional)
- **説明**: 弾体の3Dモデル名。`models/obj/bullets/<名前>.obj`と
  `textures/vehicle/bullet_<名前>.png`が使用されます。未設定時はバニラの
  アイテム表示になります。
- **例**: `ModelBullet = bullet`

### `TrajectoryParticle`
- **書式**: 文字列(Optional)
- **説明**: 飛行中に発生させる軌跡エフェクト。`none`/`explode`/`flame`/
  `hugeexplosion`/`largeexplode`/`largesmoke`/`smoke`のいずれか。省略時は
  軌跡なし。
- **例**: `TrajectoryParticle = flame`

### `TrajectoryParticleStartTick`
- **書式**: 整数(既定値: `0`)
- **説明**: 発射から`TrajectoryParticle`が出始めるまでの遅延(tick)。
- **例**: `TrajectoryParticleStartTick = 10`

### `DisableSmoke`
- **書式**: 真偽値(既定値: `false`)
- **説明**: `TrajectoryParticle`が煙系の見た目(`smoke`/`largesmoke`/`explode`/
  `largeexplode`/`hugeexplosion`)の場合に限り、それを無効化します
  (`flame`は影響を受けません)。
- **例**: `DisableSmoke = true`

### `AddMuzzleFlash`
- **書式**: `発射元からの距離, サイズ, 表示時間, A, R, G, B`
- **説明**: 発射時のマズルフラッシュ。
- **例**: `AddMuzzleFlash = 0.5, 0.20, 1, 150, 254, 219, 184`

### `AddMuzzleFlashSmoke`
- **書式**: `発射元からの距離, 表示数, サイズ, 範囲, 表示時間, A, R, G, B`
- **説明**: 発射時のマズルスモーク。
- **例**: `AddMuzzleFlashSmoke = 2.2, 1, 5.0, 2.0, 15, 180, 250, 245, 240`

### `SetCartridge`
- **書式**: `モデル名, 飛ばす強さ, Yaw, Pitch, 表示倍率, 重力, 跳ね返り`
- **説明**: 発射時に排出する空薬莢の演出。ダメージ等には一切影響しません。
- **例**: `SetCartridge = cartridge, 0.0, 0, 0, 2.00, -0.04, 0.40`

---

## 8. 熱量式(オーバーヒート)

### `HeatCount`
- **書式**: 数値(既定値: `0`)
- **説明**: 1回の使用で上昇する熱量。設定すると、`Round`/`ReloadTime`による
  通常のマガジン方式の代わりに熱量式(ガトリング等)になります。
- **例**: `HeatCount = 20`

### `MaxHeatCount`
- **書式**: 数値(既定値: `0`)
- **説明**: 熱量上限。到達すると冷却するまで使用不可になります。
- **例**: `MaxHeatCount = 150`

---

## 9. 照準・カメラ

### `Sight`
- **書式**: `MoveSight` / `None` / `MissileSight`(既定値: `MoveSight`)
- **説明**: 選択中に表示される照準の種類。`MissileSight`は`AAMissile`/
  `ATMissile`/`Missile`/`ASWeapon`でロックオン表示に使われます。
- **例**: `Sight = MissileSight`

### `Zoom`
- **書式**: 数値(`,`区切りで複数指定可)
- **説明**: 携行兵器専用のスコープ倍率。複数指定するとZキーで切り替え可能。
  現状、携行武器システム自体は未実装のため実質的な効果はありません。
- **例**: `Zoom = 4.2, 9.2`

### `FixCameraPitch`
- **書式**: 真偽値(既定値: `false`)
- **説明**: この武器選択中、搭乗者の視点ピッチを常に水平(0度)に固定します。
- **例**: `FixCameraPitch = true`

### `CameraRotationSpeedPitch`
- **書式**: 数値(既定値: `1.0`)
- **説明**: この武器選択中の視点ピッチ回転速度の倍率。値を小さくすると、より
  細かく照準を調整できます。
- **例**: `CameraRotationSpeedPitch = 0.3`

---

## 10. その他

### `ModeNum`
- **書式**: 整数、`1`または`2`(既定値: `1`)
- **説明**: 選択可能なモード数。武器タイプごとに意味が異なります
  (各`Readme_Weapon_○○.md`参照)。
- **例**: `ModeNum = 2`

### `DisplayMortarDistance`
- **書式**: 真偽値(既定値: `false`)
- **説明**: 着弾予測距離をHUD上に表示するか(`Bomb`/`Rocket`はHUD上の
  数値ではなく、地面への着弾予測地点を示す円形マーカー表示になります)。
- **`CasTargetMode`との併用**: `CasTargetMode`(既定値`Ballistic`、詳細は
  `Readme_Weapon_Cas.md`参照)を`Collision`に設定すると、`MachineGun`・
  `AS_MISSILE`・`MK_ROCKET`・`Bomb`・`Rocket`の各タイプでも、各タイプ
  本来の計算方式(既定)の代わりに、実際にブロックへ衝突する地点までの
  距離を計算して表示します。`Bomb`/`Rocket`の場合、既存の円形マーカー
  表示に加えて、この距離の数値も併せて表示されるようになります。
- **例**: `DisplayMortarDistance = true`

### `Recoil`
- **書式**: 数値(既定値: `0`、無効)
- **説明**: 使用時の機体の揺れ(反動)の強さ。
- **例**: `Recoil = 1.1`

### `RecoilBufCount`
- **書式**: `駐退カウント, 後退中のカウント倍率`(既定値: `40, 5`)
- **説明**: 反動アニメーションの時間調整。駐退カウントを増やすほど全体の時間が
  延び、後退中のカウント倍率を増やすほど後退フェーズのみ速くなります。
- **例**: `RecoilBufCount = 40, 5`

---

これで全武器タイプに共通する設定項目は全てです。特定タイプのみで意味を持つ
項目(`AccelerationInWater`・`LockTime`・`CasAircraft`等)については、各
`Readme_Weapon_○○.md`を参照してください。
