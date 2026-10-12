# Readme_Addon_Mod.md - アドオンMODの作り方

このMODを**前提MOD**として、その仕組みを流用しながら独自の乗り物・挙動を
追加する「アドオンMOD」を作るための資料です。

> **重要**: アドオンMODに同梱するモデル・テクスチャ・音声について、他者が
> 権利を有するデータの取り扱いは`GUIDELINES.md`を必ず確認してください。

`Readme_Addon.md`が扱うのは**アセットのみのアドオンパック**(JSONとOBJ
モデルだけで乗り物を追加する方法)です。こちらはそれとは別に、**Javaの
コードを書いて機能そのものを拡張する**場合を扱います。

---

## 目次

1. アドオンMODでできること
2. プロジェクトの準備
3. 新しい乗り物の種類を追加する
4. 新しい自律動作を実装する
5. ダミーパイロットの攻撃対象を変更する
6. ティア別スポーンアイテムを利用する
7. 武器ファイルを同梱する
8. 車両以外から武器を発射する
9. HUDに変数を追加する
10. 削除時の後始末とチャンクの強制ロード
11. 制限事項

---

## 1. アドオンMODでできること

以下はいずれも実際に拡張可能な形になっています。

| やりたいこと | 方法 |
|---|---|
| 新しい乗り物の種類を追加 | `AbstractVehicleEntity`を継承し、`ModEntityTypes.registerAddonVehicleType()`で登録 |
| 既存の乗り物の挙動を変更 | 既存クラス(`AircraftEntity`等)を継承し、`updateVehicleMovement()`をオーバーライド |
| 新しい自律動作を実装 | `tudursvehiclemod$updateDroneAutopilot()`等をオーバーライド |
| ダミーパイロットの攻撃対象を変更 | `DummyPilotEntity`を継承し、`tudursvehiclemod$targetClass()`/`tudursvehiclemod$isValidTarget()`をオーバーライド |
| ベースアイテムからの変換に対応 | `VehicleConverterTarget`を実装し、`VehicleConverterTargets.register()`で登録(任意) |
| スポーンアイテムと車両選択画面を使う | `TieredVehicleSpawnerItem`に自分の`VehicleConverterTarget`を渡す |
| 独自の武器を追加 | `assets/<namespace>/weapons/<weapon_name>.txt`をjarに同梱 |
| 車両以外(携帯装備など)から武器を発射 | `WeaponProjectileFactory`で弾体を生成し、`WeaponTargeting`で照準・ロックオンを行う |
| HUDスクリプトに独自の変数を追加 | `HudVariableProvider.EVENT`にリスナーを登録する |

モデル表示・メッシュ命中判定・耐久値/破壊処理・座席・武装・HUD・半透明
描画といった共通部分は、いずれの場合も`AbstractVehicleEntity`から
そのまま引き継がれます。

---

## 2. プロジェクトの準備

### 前提MODをローカルへ公開する

このMODのディレクトリで以下を実行すると、ローカルのMavenリポジトリへ
インストールされます。

```
./gradlew publishToMavenLocal
```

### アドオン側の`build.gradle`

```gradle
repositories {
    mavenLocal()
}

dependencies {
    minecraft "com.mojang:minecraft:1.21.11"
    mappings "net.fabricmc:yarn:1.21.11+build.4:v2"
    modImplementation "net.fabricmc:fabric-loader:0.18.2"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.139.4+1.21.11"

    // 前提MOD本体
    modImplementation "com.example.tudursvehiclemod:tudursvehiclemod:1.0.0"
}
```

Yarnマッピングのバージョンは前提MODと一致させてください。異なると
同じクラスに別名が付き、参照できなくなります。

### アドオン側の`fabric.mod.json`

前提MODを`depends`に記載します。これにより読み込み順が保証され、
前提MODが無い環境では明確なエラーになります。

```json
{
  "depends": {
    "fabricloader": ">=0.18.0",
    "fabric-api": "*",
    "minecraft": "1.21.11",
    "java": ">=21",
    "tudursvehiclemod": ">=1.0.0"
  }
}
```

---

## 3. 新しい乗り物の種類を追加する

### エンティティクラス

`AbstractVehicleEntity`(またはより近い既存クラス)を継承します。
抽象メソッドは`updateVehicleMovement()`の1つだけです。

```java
public class HovercraftEntity extends AbstractVehicleEntity {

    public HovercraftEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    protected void updateVehicleMovement(VehicleDefinition def) {
        // 毎tickの移動処理。クライアント・サーバー双方で呼ばれます。
        // def から max_speed / turn_speed 等の設定値を取得できます。
    }
}
```

### 登録(共通側)

```java
public class MyAddon implements ModInitializer {

    public static EntityType<HovercraftEntity> HOVERCRAFT;

    @Override
    public void onInitialize() {
        HOVERCRAFT = ModEntityTypes.registerAddonVehicleType(
                Identifier.of("myaddon", "hovercraft"),
                HovercraftEntity::new,
                2.0f, 1.5f);
    }
}
```

登録IDはアドオン自身の名前空間(上記なら`myaddon`)にしてください。

### 登録(クライアント側)

描画は既存の`VehicleEntityRenderer`をそのまま使えます。これにより、
OBJモデル・テクスチャ・半透明処理・パーツアニメーションなどの表示
まわりは前提MODと同じ仕組みで動作します。

```java
public class MyAddonClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MyAddon.HOVERCRAFT, VehicleEntityRenderer::new);
    }
}
```

### 乗り物JSONからの参照

`entity_type`にアドオン側のIDを指定します。前提MODはこの値をレジストリ
から動的に解決するため、追加の登録作業は不要です。

```json
{
  "entity_type": "myaddon:hovercraft",
  "model": "myaddon:models/obj/hovercraft.obj",
  "texture": "myaddon:textures/vehicle/hovercraft.png"
}
```

なお、ティア別スポーンアイテムと車両選択画面は、アドオンの乗り物でも
そのまま利用できます(「6. ティア別スポーンアイテムを利用する」参照)。
独自の入手方法にしたい場合は、アドオン自身が用意したアイテムや
コマンドでスポーンさせることもできます。

---

## 4. 新しい自律動作を実装する

Drone機能(ドローンセンターによる自律飛行)のフックは`protected`に
なっているため、継承してオーバーライドできます。

```java
public class PatrolAircraftEntity extends AircraftEntity {

    public PatrolAircraftEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    /** ドローンセンターに紐付けられ、有効化されている間、毎tick呼ばれます。 */
    @Override
    protected void tudursvehiclemod$updateDroneAutopilot(VehicleDefinition def) {
        // super を呼べば既定のウェイポイント追従をそのまま使えます。
        // 呼ばずに独自の飛行ロジックを書くこともできます。
        super.tudursvehiclemod$updateDroneAutopilot(def);
    }
}
```

地上車両向けのウェイポイント追従は
`tudursvehiclemod$updateGroundWaypointAutopilot()`が同様に`protected`
です。

ドローンセンター側の設定値(巡航速度・旋回半径・周回高度・ウェイポイント
一覧など)は`DroneCenterBlockEntity`から取得できるため、それらを読んだ
うえで独自の判断を加える、といった実装も可能です。

---

## 5. ダミーパイロットの攻撃対象を変更する

`DummyPilotEntity`は、既定では**敵対mob(`HostileEntity`)のうち、
射程内で視線が通る最も近い個体**を狙います。この判定は2つのフックに
分かれており、それぞれ独立して差し替えられます。

| メソッド | 役割 | 既定値 |
|---|---|---|
| `tudursvehiclemod$targetClass()` | 探索するエンティティの種類 | `HostileEntity.class` |
| `tudursvehiclemod$isValidTarget()` | 個々の候補を狙う価値があるか | 生存していて削除済みでないこと |

視線判定と「最も近いものを選ぶ」処理は、これらを差し替えても共通処理
として維持されます。

### 例: プレイヤーも攻撃対象に含める

```java
public class HostilePilotEntity extends DummyPilotEntity {

    public HostilePilotEntity(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected Class<? extends LivingEntity> tudursvehiclemod$targetClass() {
        return LivingEntity.class;
    }

    @Override
    protected boolean tudursvehiclemod$isValidTarget(LivingEntity candidate) {
        if (!super.tudursvehiclemod$isValidTarget(candidate)) {
            return false;
        }
        // 敵対mobに加えてプレイヤーも対象にする(クリエイティブ・観戦者は除く)
        if (candidate instanceof PlayerEntity player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return candidate instanceof HostileEntity;
    }
}
```

### 例: 特定のmobだけを狙う

```java
@Override
protected Class<? extends LivingEntity> tudursvehiclemod$targetClass() {
    return ZombieEntity.class;
}
```

攻撃時の挙動(攻撃開始/終了高度・索敵範囲・使用武装・降下目標Y
オフセット)は、ドローンセンターの「ダミーパイロット設定」画面から
設定される値がそのまま引き継がれます。

---

## 6. ティア別スポーンアイテムを利用する

ベースアイテム(`vehicle_base_tN`)と変換ブロックの仕組みは、アドオン
からも**登録制**で利用できます。

登録すると、変換ブロックのページ送りにアドオンの乗り物が追加され、
既存のベースアイテムから変換できるようになります。また、乗り物メニュー
からの回収(パックアップ)にも対応します。

**登録は任意です。** バランス調整の都合などで、より高コストな独自の
入手方法を用意したい場合は、登録しなければ変換対象には一切現れません。
その場合でも乗り物そのものの動作には影響しません。

### スポーンアイテムはそのまま使える

`TieredVehicleSpawnerItem`は`VehicleConverterTarget`を受け取るため、
アドオンの変換対象をそのまま渡せます。

右クリック時には**前提MODの車両選択画面がそのまま開き**、その
ティア以下のアドオン車両が一覧・検索できます。選択画面もネットワーク
処理も、アドオン側に書く必要はありません。

アイテムの登録自体はアドオンが行います。設定・モデル・レシピの有無は
すべてアドオン側の裁量です。

```java
public class MyAddonSpawners {

    public static final Item[] HOVERCRAFT_SPAWNERS = new Item[5];

    /** 選択画面のフィルタにも使われるため、アイテムと同じ対象を渡します。 */
    public static final HovercraftTarget TARGET = new HovercraftTarget();

    public static void register() {
        for (int tier = 1; tier <= 5; tier++) {
            Identifier id = Identifier.of("myaddon", "hovercraft_spawner_t" + tier);
            RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
            HOVERCRAFT_SPAWNERS[tier - 1] = Registry.register(Registries.ITEM, key,
                    new TieredVehicleSpawnerItem(
                            new Item.Settings().registryKey(key).maxCount(1), TARGET, tier));
        }
    }
}
```

選択画面に並ぶのは、`entity_type`が対象の`entityTypeId()`と一致し、
かつ`spawn_item.tier`がアイテムのティア以下の乗り物です。

独自の入手方法にしたい場合は、この仕組みを使わず自前のアイテムを
用意しても構いません。

### 変換対象として登録する

`VehicleConverterTarget`を実装し、`VehicleConverterTargets.register()`
へ渡します。

```java
public record HovercraftTarget() implements VehicleConverterTarget {

    @Override
    public Identifier entityTypeId() {
        return Identifier.of("myaddon", "hovercraft");
    }

    @Override
    public String translationKey() {
        return "item.myaddon.category.hovercraft";
    }

    @Override
    public Identifier id() {
        return Identifier.of("myaddon", "hovercraft");
    }

    @Override
    public Item[] tieredSpawnerItems() {
        return MyAddonSpawners.HOVERCRAFT_SPAWNERS;
    }
}
```

```java
@Override
public void onInitialize() {
    MyAddonSpawners.register();
    VehicleConverterTargets.register(MyAddonSpawners.TARGET);
}
```

`translationKey()`が指す翻訳は、アドオン自身の言語ファイルに用意して
ください。

### 注意点

- 登録は**MOD初期化時のみ**行ってください。変換ブロックは現在のページを
  一覧内の**位置(インデックス)**として保存するため、途中で登録内容が
  変わると保存済みの選択がずれます。
- 組み込みの7カテゴリは前提MOD自身が最初に登録するため、アドオンの
  登録順に関わらず、既存のページ位置は変わりません。
- 同じ`id()`で二重に登録した場合、2回目以降は無視されます。

---

## 7. 武器ファイルを同梱する

アドオンが独自の武器を持たせたい場合、武器設定ファイルを**アドオンの
jarに同梱**できます。

```
src/main/resources/assets/<namespace>/weapons/<name>.txt
```

書式は前提MODが読み込むMCHeli形式そのままです(`Readme_Weapon.md`を
参照)。乗り物JSONの`weapons`から、**拡張子なしのファイル名**で参照
します。

```json
"weapons": [
  {
    "seat_index": 1,
    "weapon_name": "sidecar_mg",
    "projectile_item": "minecraft:iron_nugget",
    "offsets": [
      { "x": 0.72, "y": 1.06, "z": 0.80, "mount_yaw": 0.0, "mount_pitch": 0.0 }
    ]
  }
]
```

外部の`tudursvehiclemod-addons/`フォルダに同名のファイルがある場合は、
**そちらが優先されます**。利用者による上書き調整の余地を残すためです。

### 発射音

`Sound`ディレクティブは**名前空間・拡張子なしのファイル名**を指定
します。音声も同様に同梱できます。

```
src/main/resources/assets/<namespace>/sounds/<name>.ogg
```

指定した音が見つからない場合、**無音で発射されます**(エラーには
なりません)。

---

## 8. 車両以外から武器を発射する

携帯装備のように、**車両に載っていない発射者**から武器ファイルの弾を
撃つための機能です。車両の武装と同じ処理を共有しているため、武器
ファイルの設定はそのまま同じ意味になります。

### 弾体を生成する

`WeaponProjectileFactory.create()`は、武器ファイルの設定から弾体を
生成・設定します。**スポーンはしません。**

```java
WeaponStats stats = WeaponStatsLoader.get("my_launcher");
VehicleProjectileEntity projectile = WeaponProjectileFactory.create(
        world, player, new ItemStack(Items.IRON_NUGGET), stats, mode);

projectile.setPosition(muzzlePos.x, muzzlePos.y, muzzlePos.z);
Vec3d velocity = WeaponTargeting.applyAccuracySpread(
        player.getRotationVec(1.0f).multiply(stats.velocity()),
        stats.accuracyDegrees(), world.random);
projectile.setVelocity(velocity);

world.spawnEntity(projectile);
projectile.tudursvehiclemod$forceLoadSpawnChunk();
```

`create()`が設定するのは、武器ファイルだけで決まる項目です。

- 威力、爆発(ブロック破壊・炎上・水中)、各種信管、跳弾、貫通、燃料気化
- 弾のモデル・色・軌跡パーティクル、Dispenserの散布アイテム
- 子弾(Bomblet)
- `ModeNum`による切り替え(MachineGunのモード1=榴弾、Rocketのモード0=子弾なし)。
  `mode`には選択中のモード番号を渡します(モードのない武器は0)

発射位置・速度・向きと誘導は、呼び出し側で設定します。誘導の設定は
車両と同じ公開メソッドを使います。

| 武器の種類 | 設定方法 |
|---|---|
| ASMissile / MkRocket | `setGuidanceTargetPos(WeaponTargeting.raycastGroundPoint(world, player), stats.turnRateDegreesPerTick())` |
| AAMissile / ATMissile / Missile | `setMissileGuidanceTuning(...)`の後、`setGuidanceTargetEntity(target.getId(), ...)`。ATのトップアタックは`setTopAttack(true)` |
| TVMissile | `setTvControlled(player)`(下記参照) |

車両の武装と異なり、`tudursvehiclemod$setFiringVehicle()`は呼びません。
弾薬・リロード・熱・クールダウンの管理も呼び出し側で行ってください。

### 照準・ロックオン

`WeaponTargeting`は、車両の武装が使っているものと同じ処理です。

| メソッド | 内容 |
|---|---|
| `findLockOnTarget(world, shooter, weaponType, lockRange, excluded)` | 視線から15度以内・`LockRange`以内で最も視線に近い対象。AAは空中、ATは地上・水上の対象のみ |
| `classifyTargetPosition(entity)` | 対象が空中・地上(水上)・水中のどれか |
| `raycastGroundPoint(world, shooter)` | 視線の先128ブロック以内の着弾点 |
| `applyAccuracySpread(velocity, accuracyDegrees, random)` | `Accuracy`による弾のばらつき |

ロックオンに必要な時間(`LockTime`、`LockTimePerBlock`)の計測と表示は、
呼び出し側で行ってください。

### TVミサイル

車両以外から発射したTVミサイルも、プレイヤーが操縦できます。操縦は、
プレイヤーが死亡・ログアウト・ディメンション移動したとき、または何かに
搭乗したときに終了します。射程・チャンク読み込みによる終了条件は
車両の場合と同じです。

### 弾の命中判定

`VehicleProjectileEntity`は、車両のモデル形状に対する命中判定と、
設定どおりのダメージ・爆風がそのまま適用されます。フレアによる誘導
妨害の対象にもなります。

### OBJモデルを描画する(クライアント)

アイテムの描画などでOBJモデルを使う場合は、以下を組み合わせます。

```java
ObjModel model = ObjModelLoader.get(Identifier.of("myaddon", "models/obj/my_launcher.obj")).orElse(null);
RenderLayer layer = DitherCutoutLayers.entityDitherCutout(Identifier.of("myaddon", "textures/item/my_launcher.png"));
VehicleEntityRenderer.renderTriangles(queue, matrices, layer, model.getTriangles(), light, overlay, 0xFFFFFFFF);
```

`DitherCutoutLayers`のレイヤーを使うと、Config(半透明の描画方式・
三角形描画)の設定とモデルの形式が常に一致します。

---

## 9. HUDに変数を追加する

アドオンMODは、`client.hud.HudVariableProvider.EVENT`(Fabric APIのイベント)に
リスナーを登録すると、HUDスクリプトで使える変数を追加できます。数値の変数は
式の中で、文字列の変数は`DrawString`の`%s`で、組み込みの変数と同じように使えます。

```java
// クライアント側のエントリポイント(ClientModInitializer)で登録する
HudVariableProvider.EVENT.register(new HudVariableProvider() {
    @Override
    public void provideNumeric(MinecraftClient client, PlayerEntity player,
            AbstractVehicleEntity vehicle, Map<String, Double> vars) {
        if (vehicle instanceof MyRobotEntity robot) {
            vars.put("myaddon_arm_angle", (double) robot.getArmAngle());
        }
    }

    @Override
    public void provideString(MinecraftClient client, PlayerEntity player,
            AbstractVehicleEntity vehicle, Map<String, String> vars) {
        vars.put("myaddon_mode", "ATTACK");
    }
});
```

HUDスクリプト側では、組み込みの変数と同じように書けます。

```
DrawString = -100, 80, "ARM %3.0f", myaddon_arm_angle
DrawString = -100, 92, "%s", myaddon_mode
```

- `provideNumeric`・`provideString`は、どちらも既定で何もしないメソッドです。
  必要な方だけを実装してください
- 組み込みの変数をすべて入れ終えた後に呼ばれます。渡されたmapには組み込みの
  変数が入っているため、読み取って計算に使うこともできます
- HUDが描画されるたび(毎フレーム)、クライアント側で呼ばれます。重い処理は
  避けてください
- **変数名**: 組み込みの変数(`speed`・`hp`など)と同じ名前を書くと、組み込みの値を
  上書きします。調整のために意図的に上書きすることもできますが、誤って上書きすると、
  その変数を使うすべてのHUDの表示が変わります。独自の変数には、`myaddon_`のような
  接頭辞を付けてください。また、HUDスクリプトの変数名は小文字で照合されるため、
  小文字の名前で入れてください
- 複数のアドオンが登録した場合は、登録順に呼ばれます。同じ名前を入れた場合は、
  後から登録したものが優先されます
- リスナーが例外を投げた場合、その例外はログに記録され(同じリスナーにつき1回)、
  HUDの描画は続行されます。後続のリスナーも実行されます

---

## 10. 削除時の後始末とチャンクの強制ロード

### ドローンセンターの強制ロードを止める

`DroneCenterBlockEntity`は、既定で次のチャンクを強制ロードします。

- 自身のあるチャンク
- 紐付けた機体の周囲3×3チャンク
- 機体が読み込まれていないときに、機体を探すためのチャンク

継承したブロックエンティティでこれらの強制ロードを行わせたくない場合
(プレイヤーが近くにいるときだけ動く敵拠点など)は、
`tudursvehiclemod$forceLoadsChunks()`をオーバーライドして`false`を返します。
強制ロード以外の動作(機体の管理・ウェイポイント・編隊など)は変わりません。

```java
public class EnemyBaseBlockEntity extends DroneCenterBlockEntity {
    // コンストラクタなどは省略

    @Override
    protected boolean tudursvehiclemod$forceLoadsChunks() {
        return false;
    }
}
```

- 戻り値は、そのブロックエンティティが存在する間ずっと同じにしてください。
  途中で`false`に切り替えても、それまでに要求した強制ロードはすぐには解除されません
- アドオン独自のチケット(`ChunkTicketType`)で読み込みを維持するのは自由です。
  `/tvm unloadchunks`が解除するのは強制ロード(`/forceload`と同じ種類)だけです

### 内部の呼び出しにMixinを当てない

前提MODのメソッドの中にある個々の呼び出し(`ServerWorld.setChunkForced()`など)を
`@WrapOperation`・`@Redirect`などで書き換えると、前提MOD側の実装が変わったときに
注入先が見つからず、**起動時にクラッシュ**します(Mixin設定で`"defaultRequire": 1`と
している場合。Fabricのテンプレートの既定です)。これはコンパイル時には検出できません。

実際に、ドローンセンターの強制ロードは`ChunkForceTracker`経由に変更されたため、
`DroneCenterBlockEntity`の中から`setChunkForced()`の呼び出しはなくなっています。
`protected`・`public`のフックで足りない場合は、Mixinで対処する前に、前提MOD側に
フックを追加することを検討してください。

### 独自に強制ロードする場合

アドオンの機能でチャンクを強制ロードする場合は、`ServerWorld.setChunkForced()`を
直接呼ばずに`ChunkForceTracker`を使ってください。

```java
// 強制ロードを要求する(チャンクを読み込んでから強制ロードする)
ChunkForceTracker.request(serverWorld, chunkPos, this);
// 要求を取り下げる
ChunkForceTracker.release(serverWorld, chunkPos, this);
// thisが要求したものを、Purposeを含めてすべて取り下げる
ChunkForceTracker.releaseAllOwnedBy(serverWorld, this);
```

- 要求は要求者(第3引数)ごとに数えられ、すべての要求者が取り下げたときに
  強制ロードが解除されます。`setChunkForced(…, false)`を直接呼ぶと、ほかの機能
  (近くを飛ぶ機体やドローンセンターなど)が使っているチャンクまで解除してしまいます
- `/tvm unloadchunks`は、`ChunkForceTracker`経由で要求されている強制ロードだけを
  残します。`setChunkForced()`を直接呼んで強制ロードしたチャンクは、このコマンドで
  解除されます
- 1つのオブジェクトが用途の異なる強制ロードを別々に管理する場合は、
  `new ChunkForceTracker.Purpose(this, "用途名")`を要求者にします。用途ごとに
  取り下げられ、重なったチャンクで一方の取り下げがもう一方を解除することは
  ありません
- 要求の記録はメモリ上にだけあり、ワールドには保存されません(強制ロードの状態は
  ワールドに保存されます)。ワールドを読み込み直したときは、要求し直してください。
  前提MODのドローンセンター・Stationは、ブロックエンティティの読み込み時に
  要求し直しています
- 要求者がエンティティかブロックエンティティ(または、それらを所有者とする
  `Purpose`)であれば、`/tvm unloadchunks`の実行時に、削除済みの要求者の要求は
  破棄されます。それ以外のオブジェクトを要求者にした場合は、必ず自分で
  取り下げてください
- ブロックエンティティの読み込み中に、そのブロックエンティティ自身のチャンクを
  要求する場合は、`requestWithoutLoading()`を使ってください。`request()`は
  チャンクを同期的に読み込むため、読み込み中のチャンクに対して呼ぶとサーバーが
  停止します(デッドロック)

### エンティティの後始末

継承した乗り物などで削除時の後始末を追加する場合は、`onRemove(Entity.RemovalReason)`を
オーバーライドし、必ず`super.onRemove(reason)`を呼んでください。

```java
@Override
public void onRemove(Entity.RemovalReason reason) {
    super.onRemove(reason);
    if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            // チャンクと一緒に保存されるだけ(後で読み込み直される)
            ChunkForceTracker.forgetAllOwnedBy(serverWorld, this);
        } else {
            ChunkForceTracker.releaseAllOwnedBy(serverWorld, this);
        }
    }
}
```

- `onRemove`は、すべての削除経路(`discard()`・`kill()`・チャンクのアンロード・
  ディメンション移動など)で、サーバーとクライアントの両方で呼ばれます
- `onRemoved()`はクライアント側でしか呼ばれません。`remove(RemovalReason)`は
  `discard()`・`kill()`を経由したときにしか呼ばれず、チャンクのアンロードや
  ディメンション移動では呼ばれません。サーバー側の後始末をこれらに書くと、
  実行されない場合があります
- `UNLOADED_TO_CHUNK`(サーバー停止時を含む)では、エンティティはワールドに保存され、
  後で読み込み直されます。このときは強制ロードを解除せず、記録だけを破棄します
  (強制ロードの状態はワールドに保存されるため、読み込み直したときにそのまま
  使えます)

### ブロックエンティティの後始末

ブロックが壊されたときの後始末は、ブロックの`onStateReplaced()`ではなく、
ブロックエンティティの`onBlockReplaced(BlockPos, BlockState)`に書いてください。
`onStateReplaced()`が呼ばれる時点では、ブロックエンティティはすでにワールドから
取り除かれており、`world.getBlockEntity(pos)`は`null`を返します。

```java
@Override
public void onBlockReplaced(BlockPos pos, BlockState oldState) {
    if (this.getWorld() instanceof ServerWorld serverWorld) {
        ChunkForceTracker.releaseAllOwnedBy(serverWorld, this);
    }
    super.onBlockReplaced(pos, oldState); // インベントリの中身を落とす
}
```

- `DroneCenterBlockEntity`を継承する場合は、`super.onBlockReplaced()`で前提MOD側の
  後始末(ダミーパイロットの削除・機体の紐付け解除・編隊の解除・強制ロードの解除など)が
  行われます
- `/setblock`(`destroy`指定なし)・`/fill`・`/clone`で置き換えた場合は、バニラの
  仕様で`onBlockReplaced()`が呼ばれません。このとき残った強制ロードは
  `/tvm unloadchunks`で解除できます

---

## 11. 制限事項

### バージョンの一致

前提MODとアドオンは、Minecraft・Fabric Loader・Yarnマッピングの
バージョンを揃える必要があります。特にYarnマッピングが異なると、
同じクラス・メソッドに別の名前が付くため、コンパイルは通っても実行時に
解決できなくなります。

### 内部実装への依存

`tudursvehiclemod$`で始まるメソッドはこのMOD独自の追加分です。
`protected`・`public`のものは意図的に公開していますが、将来の更新で
シグネチャが変わる可能性はあります。アドオン側でバージョンを明示的に
指定しておくことを推奨します。
