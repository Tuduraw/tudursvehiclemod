# Readme_Vehicle_Helicopter.md - ヘリコプター(`entity_type: helicopter`)特有の項目

`Readme_Vehicle.md`の共通項目に加え、`entity_type: "tudursvehiclemod:helicopter"`
を指定した機体でのみ意味を持つ項目・挙動です。

---

## `float_capable`

- **書式**: 真偽値(既定値: `false`、`Readme_Vehicle.md`参照)
- **説明**: ヘリコプターにも適用されます。`true`の場合、水面へ着水すると自然に
  沈まず、水面のライン付近に浮いた状態で安定します(浮力ばね)。ヘリコプターは
  固定翼機と異なり、通常時は水面に接触すると重力に従って沈み続けるため、水上での
  運用にはこの設定が必要です。
- **例**:
  ```json
  "float_capable": true
  ```

---

## マニュアルモード(手動姿勢制御)

専用キーで切り替え可能な操縦モードです。通常モードでは、飛行中の姿勢は
搭乗者の視点(ヨー)とW/S入力(ピッチ傾き)から自動的に決まりますが、
マニュアルモードに切り替えると、マウス操作がヨー・ピッチを直接操作し、
A/D入力がロール(慣性つきの連続回転)を担当するようになります。地上にいる間は
モードに関わらず通常の挙動(固定姿勢)のままです。

このモードの有効/無効を切り替える専用の設定項目はJSON側にはありません
(キー操作のみで切り替わります)。

---

## 補足

- ヘリコプターは`wheel_parts`(`Readme_Vehicle_Car.md`参照)・`runways`
  (`Readme_Vehicle_Ship.md`参照)を使用しません
- 降着装置(`toggle_parts`の`trigger: "landing_gear"`)は固定翼機と同様に使用でき、
  記載例は`Readme_Vehicle_Aircraft.md`を参照してください
