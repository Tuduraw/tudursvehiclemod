# Readme_Weapon.md - 武器設定ファイル(共通項目)

対象ファイル: `assets/<namespace>/weapons/<weapon_name>.txt`

MCヘリ互換の`Key = Value`テキスト形式です(JSONではありません)。キー名は大文字・小文字を区別しません。行頭が`;`の行、および`=`を含まない行はコメント/無視されます。値の途中に`;`がある場合、それ以降は同一行内コメントとして無視されます。

機体側のJSON定義(`weapons`配列)からは、`weapon_name`でこのファイル名(拡張子なし)を参照します。ファイル自体はいつでも編集・`/reload`で即座に反映可能です(機体を再召喚する必要はありません)。

特定タイプにのみ意味を持つ項目は、対応する`Readme_Weapon_○○.md`を参照してください。

- `Readme_Weapon_Gun.md` — 直射武器(`MachineGun`/`Rocket`)
- `Readme_Weapon_Bomb.md` — 投下兵器(`Bomb`/`Depth`)
- `Readme_Weapon_Torpedo.md` — 魚雷(`Torpedo`)
- `Readme_Weapon_Missile.md` — 誘導兵器(`ASMissile`/`MkRocket`/`AAMissile`/`ATMissile`/`Missile`/`ASWeapon`/`TVMissile`)
- `Readme_Weapon_Special.md` — 特殊武器(`Dispenser`/`Smoke`/`Dummy`/`TargetingPod`)
- `Readme_Weapon_Cas.md` — 支援機発艦武器(`CAS`/`Carrier`)

以下に、MCヘリと部分的または完全に非互換な項目のみ記載します。それ以外の項目についてはMCヘリ側のドキュメントを参照してください(詳細な作業は保留中)。

### `Type`
- **書式**: 文字列
- **説明**: 武器の挙動タイプ。必須項目です。MCヘリと異なる実装や独自の武器タイプも存在するため、使用可能な値と詳細は各`Readme_Weapon_○○.md`を参照してください:
  `MachineGun`/`Rocket`(`Readme_Weapon_Gun.md`)、`Bomb`/`Depth`(`Readme_Weapon_Bomb.md`)、`Torpedo`(`Readme_Weapon_Torpedo.md`)、`ASMissile`/`MkRocket`/`AAMissile`/`ATMissile`/`Missile`/`ASWeapon`/`TVMissile`(`Readme_Weapon_Missile.md`)、`Dispenser`/`Smoke`/`Dummy`/`TargetingPod`(`Readme_Weapon_Special.md`)、`CAS`/`Carrier`(`Readme_Weapon_Cas.md`)。
  未対応・不明な値は全て「その他」として扱われ、実弾を発射する挙動にはなりません。
- **例**: `Type = MachineGun`

### `DisplayMortarDistance`
- **書式**: 真偽値(既定値: `false`)
- **説明**: 着弾予測距離をHUD上に表示するか(`Bomb`/`Rocket`はHUD上の数値ではなく、地面への着弾予測地点を示す円形マーカー表示になります)。
- **`CasTargetMode`との併用**: `CasTargetMode`(既定値`Ballistic`、詳細は`Readme_Weapon_Cas.md`参照)を`Collision`に設定すると、`MachineGun`・`AS_MISSILE`・`MK_ROCKET`・`Bomb`・`Rocket`の各タイプでも、各タイプ本来の計算方式(既定)の代わりに、実際にブロックへ衝突する地点までの距離を計算して表示します。`Bomb`/`Rocket`の場合、既存の円形マーカー表示に加えて、この距離の数値も併せて表示されるようになります。
- **例**: `DisplayMortarDistance = true`

---
