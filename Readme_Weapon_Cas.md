# Readme_Weapon_Cas.md - 支援機発艦武器(`CAS`/`Carrier`)特有の項目

対象タイプ: `CAS`(近接航空支援) / `Carrier`(空母艦載機発艦)

`Readme_Weapon.md`の共通項目のうち、この武器タイプに実際に意味を持つのは
`DisplayName`のみです(ダメージ・弾速等は、発艦する側の機体自身の武器設定で
別途定義されます)。これらのタイプは実弾を発射する代わりに、別の乗り物定義
(機体JSON)をその場でスポーンさせ、設定されたルートを自動飛行させます。
本プロジェクト独自の追加項目が中心です(MCヘリ原作の`CAS`/`Carrier`と直接の
対応関係はありません)。

---

## `CAS`(近接航空支援)

使用すると、搭乗者の照準が地面(または水面)と交わる点を目標地点として、
別途指定した支援機を目標地点に近いルート先頭でスポーンさせ、設定した経路を
自動飛行させながら攻撃させます。ルート終端まで到達すると自動的に消滅します
(帰投・着陸はしません)。

**例**:
```
DisplayName = Call Airstrike
Type = CAS
CasAircraft = a10_thunderbolt
CasWeaponIndex = 0
CasAccuracy = 3.0
CasTimeout = 60
CasStuckTimeout = 30
CasYawOffset = 0
CasWaypoint = 0,80,-150,80,false
CasWaypoint = 0,60,-40,60,true
CasWaypoint = 0,60,0,60,true
CasWaypoint = 0,60,40,60,true
CasWaypoint = 0,80,150,80,false
Round = 3
ReloadTime = 1200
```

### `CasAircraft`
- **書式**: 文字列(必須)
- **説明**: 発艦させる支援機の機体ファイル名(`data/<namespace>/vehicles/<名前>.json`、
  名前空間は不要)。
- **例**: `CasAircraft = a10_thunderbolt`

### `CasWeaponIndex`
- **書式**: 整数(既定値: `0`)
- **説明**: 支援機自身が攻撃時に使用する武器スロット番号(支援機自身の
  `weapons`配列の添字)。
- **例**: `CasWeaponIndex = 0`

### `CasAccuracy`
- **書式**: 数値(既定値: `0`)
- **説明**: 支援機自身の航法誤差(ルート自体のランダムなずれ幅)。武器自体の
  命中精度(`Accuracy`)とは別の項目です。
- **例**: `CasAccuracy = 3.0`

### `CasTimeout`
- **書式**: 数値、単位は秒(既定値: `60`)
- **説明**: 支援機の総飛行時間の上限。この時間が経過すると、ルートの進捗に
  関わらず強制的に消滅します。
- **例**: `CasTimeout = 60`

### `CasStuckTimeout`
- **書式**: 数値、単位は秒(既定値: `60`)
- **説明**: 次のウェイポイントへ進めないままこの時間が経過すると、進行状況に
  関わらず強制的に消滅します(`CasTimeout`とは独立した、進捗停止専用の
  タイムアウトです)。
- **例**: `CasStuckTimeout = 30`

### `CasYawOffset`
- **書式**: 数値(既定値: `0`)
- **説明**: ルート自体の回転基準(搭乗者の視線方向)に加える補正角度(度)。
- **例**: `CasYawOffset = 0`

### `CasTargetMode`
- **書式**: 文字列(既定値: `Ballistic`、大文字小文字区別なし)
- **説明**: 目標地点(ルート自体の相対座標の中心点)をどのように決定するかを
  選択します。
  - `Ballistic`(既定): 障害物を一切考慮しない、純粋に数学的な計算方式。
    武器自身の`Velocity`/`Gravity`(未設定時は既定値)と搭乗者の照準方向
    から、「発射高度へ戻ってくる地点」を弾道計算で求めます。照準の仰角が
    そのまま着弾距離を決める、間接照準兵器らしい挙動になります
  - `Raycast`: 照準方向へ直接レイキャストし、最初にブロックへ当たった
    地点を目標とします(何も当たらなければ固定の最大距離地点)
  - `Collision`: `Ballistic`と同じ弾道物理計算を使いつつ、シミュレーション
    の各区間で実際にブロックとの衝突を判定し、着弾地点(=実際にブロックへ
    衝突した地点)のみを目標とします。`Ballistic`のような「発射高度へ戻る
    地点」への早期打ち切りは行わず、地形が発射地点より低い場合など、必要
    であればより長く弾道をシミュレートし続けます
- **例**: `CasTargetMode = Collision`

### `CasAttackStartAltitude` / `CasAttackStopAltitude`
- **書式**: 数値、単位はブロック(既定値: `CasAttackStartAltitude = 200`、
  `CasAttackStopAltitude = 40`)
- **説明**: Carrierの僚機への目標ロック機能(`/tvm`のロックモード)で、
  地表・水上の目標を攻撃する際の高度しきい値です。単純な「目標地点からの
  一定オフセット高度」だけでは、その高度へ到達するまでの飛行経路自体が
  安全である保証がないため、2段階のヒステリシス方式を採用しています。
  - `CasAttackStartAltitude`: 攻撃(目標への旋回・接近・発射)を開始・
    再開する高度。目標に対してこの高さ以上まで実際に上昇して初めて、
    攻撃動作へ移行します
  - `CasAttackStopAltitude`: この高さを下回ると、攻撃を中断して
    `CasAttackStartAltitude`まで機首上げして上昇します。
    `CasAttackStartAltitude`まで戻るまでは(`CasAttackStopAltitude`
    を上回っただけでは)攻撃を再開しません(境界付近での頻繁な切り替えを
    防ぐためです)
  - この2つの値は、僚機がロック時に使用する武器スロット(隊長機が
    ロック時点で選択していた武装)自身の設定が使われます
- **例**: `CasAttackStartAltitude = 200` / `CasAttackStopAltitude = 40`

### `CasWaypoint`
- **書式**: `相対X, 相対Y, 相対Z, 速度%, 攻撃するか`(複数行指定可)
- **説明**: 支援機が辿るルートのウェイポイント。座標はマークした目標地点
  からの相対値です。速度%は支援機自身の最高速度に対する割合(0〜100)。
  攻撃フラグ`true`/`false`(または`1`/`0`)が`true`の区間では、そのレグを
  飛行中に`CasWeaponIndex`の武器で自動攻撃します。ルートは通常、
  攻撃フラグ`false`の進入・離脱区間と、`true`の攻撃run区間で構成します。
- **例**(進入→攻撃run→離脱):
  ```
  CasWaypoint = 0,80,-150,80,false
  CasWaypoint = 0,60,-40,60,true
  CasWaypoint = 0,60,40,60,true
  CasWaypoint = 0,80,150,80,false
  ```

### `CasFormationSize`
- **書式**: 整数(既定値: `1`)
- **説明**: このプロジェクト独自の拡張項目です。1発の発射で、単機ではなく
  この機数分の支援機を編隊で出撃させます。1機目(リーダー機)は常に
  設定通りのルートを辿り、2機目以降は`CasFormationType`で指定した隊形に
  従い、リーダー機と全く同じルートをそのまま平行移動した経路を飛びます
  (隊形内の相対位置を保ったまま、全機が一斉に同じルートを飛行します)。
  既定値`1`(省略時)は編隊を組まない、従来通りの単機出撃です。
- **例**: `CasFormationSize = 4`

### `CasFormationType`
- **書式**: 文字列(既定値: `LineAbreast`)
- **説明**: `CasFormationSize`が2以上の場合の隊形。
  - `LineAbreast`: 横一列。リーダー機を基準に左右交互に並びます
    (機数が偶数の場合、片側へ1機多くなり左右非対称になります)
  - `LineAstern`: 縦一列。リーダー機の真後ろへ等間隔に並びます
  - `VFormation`: V字。リーダー機を先頭に、左右交互かつ後方へ段階的に
    下がりながら並びます(`LineAbreast`と同様、偶数機では左右非対称)
  - `Diamond`: 4機1組のダイヤ隊形(先頭・右・左・後尾)を構成します。
    5機以上の場合、ダイヤの塊(エレメント)自体を`VFormation`と同じ
    考え方でさらに大きく展開し、全体としてコンバットボックスを形成します
  - `Delta`: 3機1組のデルタ(小規模なV字)隊形を構成します。4機以上の
    場合は`Diamond`と同様、デルタのエレメント自体をさらに展開します
- **例**: `CasFormationType = VFormation`

### `CasFormationSpacing`
- **書式**: 数値、単位はブロック(既定値: `8.0`)
- **説明**: 編隊内の機体間隔。`Diamond`/`Delta`では、エレメント内(小さな
  隊形内)の機体間隔として使われます。エレメント同士の間隔は
  `CasFormationElementSpacing`で別途指定します。
- **例**: `CasFormationSpacing = 10.0`

### `CasFormationElementSpacing`
- **書式**: 数値、単位はブロック(既定値: 省略時は自動計算)
- **説明**: `Diamond`/`Delta`限定。エレメント(小隊形)同士の中心間隔。
  省略した場合は、`CasFormationSpacing`のエレメント内機数×1.5倍が
  自動的に使われます(従来通りの挙動)。`Diamond`/`Delta`以外の隊形
  では意味を持ちません。
- **例**: `CasFormationElementSpacing = 20.0`

---

## `Carrier`(空母艦載機発艦)

`CAS`と似た仕組みですが、発艦させる機体自身の`AddWeapon`マウント位置
(=母艦自身)からその場で発艦する点が異なります(`CAS`は離れた目標地点付近で
スポーン)。搭乗者はAlt+Yキーで発艦した機体と母艦の間を行き来できます。ルート
終端到達後は消滅せず、帰投・着艦シーケンスに入ります。滑走路自体は母艦側の
機体JSON(`runways`、`Readme_Vehicle.md`の「12. 空母(Carrier)甲板・滑走路」参照)
で定義します。母艦は艦船に限らず、`entity_type`を問わずどの乗り物にも設定できます。

**この武器と滑走路(`runways`)は互いに必須ではありません。** 滑走路を定義して
いない母艦でもこの武器は問題なく機能します(帰投した艦載機の回収は位置判定のみで
行われ、滑走路タイルへの着地は必須ではありません)。逆に、この武器を設定せず
滑走路のみを甲板として使うことも可能です。両方を組み合わせて発艦した艦載機を
実際に滑走路へ着艦させる形が、空母の基本形として元々意図されている組み合わせ
です。

**例**:
```
DisplayName = Launch F-14
Type = Carrier
CarrierAircraft = f14_tomcat
CarrierWeaponIndex = 0
CarrierAccuracy = 3.0
CarrierTimeout = 300
CarrierStuckTimeout = 30
CarrierYawOffset = 0
CarrierLaunchWaypoint = 0,10,60,100,1,-,250
CarrierWaypoint = 0,80,300,80,false
CarrierWaypoint = 0,80,600,80,true
CarrierLandingWaypoint = 0,60,-200,40,1,1,-
CarrierLandingWaypoint = 0,20,-60,20,-,-,-
CarrierLandingToAmmoRadius = 15.0
```

### `CarrierAircraft` / `CarrierWeaponIndex` / `CarrierAccuracy` /
### `CarrierTimeout` / `CarrierStuckTimeout` / `CarrierYawOffset`
- **書式・説明**: `CAS`の同名項目(`CasAircraft`等)と全く同じ意味です。
- **例**:
  ```
  CarrierAircraft = f14_tomcat
  CarrierWeaponIndex = 0
  CarrierAccuracy = 3.0
  CarrierTimeout = 300
  CarrierStuckTimeout = 30
  CarrierYawOffset = 0
  ```

### `CasTargetMode`
- **書式・説明**: `CAS`側と全く同じ項目です。`Carrier`武器の場合も、
  キー名は`CarrierTargetMode`ではなく**`CasTargetMode`のまま**指定します
  (この項目のみ、CAS/Carrierで共通の単一キーとして扱われます)。
- **例**: `CasTargetMode = Collision`

### `CasAttackStartAltitude` / `CasAttackStopAltitude`
- **書式・説明**: `CAS`側と全く同じ項目です。`CasTargetMode`と同様、
  `Carrier`武器の場合もキー名は`CasAttackStartAltitude`/
  `CasAttackStopAltitude`のままです。
- **例**: `CasAttackStartAltitude = 200` / `CasAttackStopAltitude = 40`

### `CarrierLandingYawOffset`
- **書式**: 数値(既定値: `0`)
- **説明**: `CarrierYawOffset`に加え、着艦進入方向にのみ追加で適用される補正角度。
- **例**: `CarrierLandingYawOffset = 0`

### `CarrierTargetYawOffset`
- **書式**: 数値(既定値: `0`)
- **説明**: `CarrierYawOffset`に加え、ルート自体の回転にのみ追加で適用される
  補正角度。
- **例**: `CarrierTargetYawOffset = 0`

### `CarrierWaypoint`
- **書式**: `CasWaypoint`と同じ形式
- **説明**: 通常の哨戒・攻撃ルート。
- **例**:
  ```
  CarrierWaypoint = 0,80,300,80,false
  CarrierWaypoint = 0,80,600,80,true
  ```

### `CarrierLaunchWaypoint`
- **書式**: `相対X, 相対Y, 相対Z, 速度%[, gear[, bay[, speedBoostKmh]]]`(複数行
  指定可)
- **説明**: 発艦直後に最初に飛行する専用の離陸ルート(`CarrierWaypoint`より
  先に実行されます)。攻撃フラグはありません。末尾3列は省略可能:
  - `gear`(`0`/`1`/`-`、既定値`-`=変更なし): このウェイポイント到達の瞬間、
    降着装置の状態を強制的に設定(格納/展開)
  - `bay`(同上): 武器ベイの開閉状態を強制的に設定
  - `speedBoostKmh`(数値または`-`、既定値`-`=無効): このウェイポイント到達の
    瞬間、現在の水平方向を保ったまま速度をこのkm/h値へ瞬間的に変更します
    (カタパルト射出の演出)
  - 現状、`gear`/`bay`は最初のウェイポイント(0番目、発艦直後)と着艦ルートの
    最終ウェイポイントでのみ実際に反映されます(中間のウェイポイントでは
    無視されます)
- **例**(発艦直後にギアを格納、カタパルトで250km/hまで加速):
  ```
  CarrierLaunchWaypoint = 0,10,60,100,1,-,250
  ```

### `CarrierLandingWaypoint`
- **書式**: `CarrierLaunchWaypoint`と同じ形式(必須、最低1つ)
- **説明**: 帰投・着艦ルート。母艦の現在位置・向きに対する相対座標で、
  毎tick再計算されます(着艦には時間がかかり、その間も母艦が移動している
  可能性があるため)。最後のウェイポイントが、最終進入(母艦のAddWeapon位置
  そのものへの直線進入)の基準点になります。
- **例**(進入時にギア展開・ベイ開放、最終進入では変更なし):
  ```
  CarrierLandingWaypoint = 0,60,-200,40,1,1,-
  CarrierLandingWaypoint = 0,20,-60,20,-,-,-
  ```

### `CarrierLandingToAmmoRadius`
- **書式**: 数値(既定値: `15.0`)
- **説明**: この武器自身のAddWeapon位置からこの半径(ブロック)以内に、
  対応する機体ファイルの搭乗機体が入ると(速度に関わらず)、自動的に
  弾薬+1に変換されます(`MaxAmmo`に空きがある場合)。この武器から実際に
  発艦させた機体に限らず、同じ機体ファイルの任意のプレイヤー操縦機が対象です。
- **例**: `CarrierLandingToAmmoRadius = 15.0`

### `CarrierRecoveryPoint`
- **書式**: `相対X, 相対Y, 相対Z, 半径`(複数行指定可)
- **説明**: `CarrierLandingToAmmoRadius`の範囲に加え、追加の回収ゾーンを
  設定できます(例: 甲板とは別のエレベーター等)。各行が独立した座標・半径を
  持ちます。省略時は既定の1ゾーンのみが有効です。
- **例**(サイドエレベーター付近を追加の回収ゾーンに):
  ```
  CarrierRecoveryPoint = 15,0,-30,10.0
  ```

### `CarrierFormationSize` / `CarrierFormationType` / `CarrierFormationSpacing` / `CarrierFormationElementSpacing`
- **書式・説明**: `CAS`側の`CasFormationSize`/`CasFormationType`/
  `CasFormationSpacing`/`CasFormationElementSpacing`と全く同じ形式・
  意味です。
- **Carrier特有の注意点(発艦シーケンス)**: 編隊機は同一の発艦位置
  (この武器自身の`AddWeapon`マウント位置)・同一の向きから、
  約1秒(20tick)間隔で1機ずつ発艦します(同時発艦による機体同士の
  衝突を避けるため)。各機は発艦専用ルート(`CarrierLaunchWaypoint`)を
  終えた時点で、編隊全体の発艦が完了していなければ、母艦周辺を上空で
  周回しながら待機します。編隊の最後の1機が発艦した瞬間、待機中だった
  機体を含む編隊全体が一斉に通常の巡航・攻撃ルート(`CarrierWaypoint`)
  への飛行を開始します。編隊のオフセット自体は、発艦専用ルートには
  適用されず、通常ルートにのみ適用されます。座席切り替え(Alt+Y)は、
  編隊中最後に発艦した機体を対象とします。
- **例**: `CarrierFormationSize = 3` / `CarrierFormationType = Delta`
