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
8. 制限事項

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
| 独自の武器を追加 | `assets/<namespace>/weapons/<n>.txt`をjarに同梱 |

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
    public static final MyHovercraftTarget TARGET = new MyHovercraftTarget();

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
    VehicleConverterTargets.register(new HovercraftTarget());
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

書式は前提MODが読み込むMCHeli形式そのままです(`Readme_Weapon.txt`を
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

## 8. 制限事項

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
