# Readme_Addon_Mod.md - Building an addon mod

A guide to extending this mod with code (an addon mod), as opposed to an
addon pack (data only, `Readme_Addon.md`).

> **Important**: regarding models, textures, and sounds you bundle in an
> addon mod, read `GUIDELINES.md` on handling data that other people hold
> the rights to.

Everything here goes through what this mod deliberately exposes - no
mixins into it are needed.

---

## Contents

1. What an addon mod can do
2. Setting up the project
3. Adding a new kind of vehicle
4. Implementing new autonomous behavior
5. Changing a dummy pilot's targets
6. Using the tiered spawner items
7. Bundling weapon files
8. Firing weapons from outside a vehicle
9. Limitations

---

## 1. What an addon mod can do

All of the following are genuinely extensible.

| What you want | How |
|---|---|
| Add a new kind of vehicle | Extend `AbstractVehicleEntity` and register with `ModEntityTypes.registerAddonVehicleType()` |
| Change an existing vehicle's behavior | Extend the existing class (`AircraftEntity` etc.) and override `updateVehicleMovement()` |
| Implement new autonomous behavior | Override `tudursvehiclemod$updateDroneAutopilot()` and similar |
| Change a dummy pilot's targets | Extend `DummyPilotEntity` and override `tudursvehiclemod$targetClass()` / `tudursvehiclemod$isValidTarget()` |
| Support conversion from the base item | Implement `VehicleConverterTarget` and register with `VehicleConverterTargets.register()` (optional) |
| Use the spawner items and vehicle selection screen | Pass your own `VehicleConverterTarget` to `TieredVehicleSpawnerItem` |
| Add your own weapons | Bundle `assets/<namespace>/weapons/<name>.txt` in your jar |
| Fire weapons from outside a vehicle (handheld equipment, etc.) | Create projectiles with `WeaponProjectileFactory` and aim or lock on with `WeaponTargeting` |

---

## 2. Setting up the project

### Publish the base mod locally

Run this in this mod's own directory to install it into your local Maven
repository.

```
./gradlew publishToMavenLocal
```

### The addon's own `build.gradle`

```gradle
repositories {
    mavenLocal()
}

dependencies {
    minecraft "com.mojang:minecraft:1.21.11"
    mappings "net.fabricmc:yarn:1.21.11+build.4:v2"
    modImplementation "net.fabricmc:fabric-loader:0.18.2"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.139.4+1.21.11"

    // The base mod itself
    modImplementation "com.example.tudursvehiclemod:tudursvehiclemod:1.0.0"
}
```

Match the Yarn mapping version to the base mod's own. A mismatch gives
the same class a different name, and references stop resolving.

### The addon's own `fabric.mod.json`

List the base mod under `depends`. This guarantees load order and
produces a clear error in an environment without it.

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

## 3. Adding a new kind of vehicle

### The entity class

Extend `AbstractVehicleEntity` (or a closer existing class). There's only
one abstract method, `updateVehicleMovement()`.

```java
public class HovercraftEntity extends AbstractVehicleEntity {

    public HovercraftEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    protected void updateVehicleMovement(VehicleDefinition def) {
        // Per-tick movement. Called on both client and server.
        // def supplies the configured values - max_speed, turn_speed, etc.
    }
}
```

### Registering (common side)

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

Use the addon's own namespace for the registration id (`myaddon` above).

### Registering (client side)

The existing `VehicleEntityRenderer` can be used directly for rendering,
so OBJ models, textures, translucency handling, part animation, and the
rest of the display side all work exactly as they do for the base mod.

```java
public class MyAddonClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MyAddon.HOVERCRAFT, VehicleEntityRenderer::new);
    }
}
```

### Referencing it from a vehicle JSON

Put the addon's own id in `entity_type`. The base mod resolves this
dynamically from the registry, so no extra registration is needed.

```json
{
  "entity_type": "myaddon:hovercraft",
  "model": "myaddon:models/obj/hovercraft.obj",
  "texture": "myaddon:textures/vehicle/hovercraft.png"
}
```

The tiered spawner items and the vehicle selection screen work with an
addon's own vehicles too (see "6. Using the tiered spawner items"). If
you'd rather have your own acquisition route, you can equally spawn them
from your own item or command.

---

## 4. Implementing new autonomous behavior

The hooks for the drone feature (autonomous flight via the Drone Center)
are `protected`, so you can extend and override them.

```java
public class PatrolAircraftEntity extends AircraftEntity {

    public PatrolAircraftEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    /** Called every tick while linked to a Drone Center and enabled. */
    @Override
    protected void tudursvehiclemod$updateDroneAutopilot(VehicleDefinition def) {
        // Call super to keep the default waypoint following as-is,
        // or skip it and write your own flight logic.
        super.tudursvehiclemod$updateDroneAutopilot(def);
    }
}
```

Waypoint following for ground vehicles is likewise `protected`, as
`tudursvehiclemod$updateGroundWaypointAutopilot()`.

The Drone Center's own settings (cruise speed, turn radius, orbit
altitude, the waypoint list, and so on) are readable from
`DroneCenterBlockEntity`, so you can also read those and layer your own
decisions on top.

---

## 5. Changing a dummy pilot's targets

By default `DummyPilotEntity` targets **the nearest hostile mob
(`HostileEntity`) within range and in line of sight**. That decision is
split across two hooks, each independently replaceable.

| Method | Role | Default |
|---|---|---|
| `tudursvehiclemod$targetClass()` | Which entity type to search for | `HostileEntity.class` |
| `tudursvehiclemod$isValidTarget()` | Whether an individual candidate is worth targeting | Alive and not removed |

The line-of-sight check and the "pick the nearest" logic remain shared
even when you replace these.

### Example: include players as targets

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
        // Hostile mobs plus players (excluding creative and spectator)
        if (candidate instanceof PlayerEntity player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return candidate instanceof HostileEntity;
    }
}
```

### Example: target one specific mob

```java
@Override
protected Class<? extends LivingEntity> tudursvehiclemod$targetClass() {
    return ZombieEntity.class;
}
```

The attack behavior itself (attack start/stop altitude, search range,
which weapon is used, the descent target Y offset) carries over from
whatever is configured on the Drone Center's own "dummy pilot settings"
screen.

---

## 6. Using the tiered spawner items

The base item (`vehicle_base_tN`) and converter block mechanism is
available to an addon too, **by registration**.

Registering adds the addon's own vehicles to the converter block's page
cycling, letting them be converted from the existing base items. It also
enables packing a placed vehicle back up from the vehicle menu.

**Registration is optional.** If you'd rather have a costlier,
independent acquisition route for balance reasons, simply don't register
and nothing about your vehicle appears in the converter at all. Not
registering doesn't affect how the vehicle itself behaves.

### The spawner item works as-is

`TieredVehicleSpawnerItem` takes a `VehicleConverterTarget`, so you can
pass your addon's own target to it directly.

Right-clicking then opens **the base mod's own vehicle selection
screen**, listing your addon's vehicles at or below that tier, with
search. You don't have to write a selection screen or any networking.

Registering the item itself is the addon's own job. Its settings, model,
and whether it has a recipe are all up to you.

```java
public class MyAddonSpawners {

    public static final Item[] HOVERCRAFT_SPAWNERS = new Item[5];

    /** Also used as the selection screen's own filter, so pass the same target. */
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

What appears in the selection screen is every vehicle whose
`entity_type` matches the target's own `entityTypeId()` and whose
`spawn_item.tier` is at or below the item's own tier.

### Registering as a converter target

Implement `VehicleConverterTarget` and pass it to
`VehicleConverterTargets.register()`.

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

Provide the translation that `translationKey()` points at in the addon's
own language file.

### Notes

- Register **only at mod init**. The converter block stores its current
  page as a **position (index)** into the list, so a list that changes
  later shifts every stored selection.
- The seven built-in categories are registered first by the base mod
  itself, so existing page positions never move regardless of addon
  registration order.
- Registering the same `id()` twice ignores the second and later
  registrations.

---

## 7. Bundling weapon files

An addon that wants its own weapons can **bundle the weapon config file
inside its own jar**.

```
src/main/resources/assets/<namespace>/weapons/<name>.txt
```

The format is the same MC Heli directive format the base mod reads (see
`Readme_Weapon.md`). A vehicle JSON's `weapons` refers to it by **file
name, without the extension**.

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

A file of the same name in the external `tudursvehiclemod-addons/` folder
**takes priority**, preserving the user's ability to override and tune.

### Firing sound

The `Sound` directive takes a **bare file name, without namespace or
extension**. Sounds can be bundled the same way.

```
src/main/resources/assets/<namespace>/sounds/<name>.ogg
```

If the named sound isn't found, the weapon **fires silently** - it isn't
an error.

---

## 8. Firing weapons from outside a vehicle

These APIs let a shooter that is **not on a vehicle**, such as handheld
equipment, fire a weapon file's projectile. They share their code with
vehicle weapons, so every weapon file setting means the same thing.

### Creating the projectile

`WeaponProjectileFactory.create()` creates a projectile and configures it
from the weapon file. **It does not spawn it.**

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

`create()` sets everything that the weapon file alone decides:

- Damage, explosion (block damage, fire, in water), every fuse type, bounce,
  piercing and fuel-air explosive
- Bullet model, colour and trail particle, and the Dispenser item
- Bomblets
- `ModeNum` switching (MachineGun mode 1 = HE round, Rocket mode 0 = no
  bomblets). Pass the selected mode index as `mode` (0 for a weapon without
  modes)

The caller sets the spawn position, velocity, direction and guidance.
Guidance uses the same public methods as vehicle weapons:

| Weapon type | How to set it up |
|---|---|
| ASMissile / MkRocket | `setGuidanceTargetPos(WeaponTargeting.raycastGroundPoint(world, player), stats.turnRateDegreesPerTick())` |
| AAMissile / ATMissile / Missile | `setMissileGuidanceTuning(...)`, then `setGuidanceTargetEntity(target.getId(), ...)`. Use `setTopAttack(true)` for AT top attack |
| TVMissile | `setTvControlled(player)` (see below) |

Unlike vehicle weapons, do not call `tudursvehiclemod$setFiringVehicle()`.
The caller also manages ammo, reloading, heat and cooldown.

### Aiming and lock-on

`WeaponTargeting` holds the same logic vehicle weapons use.

| Method | What it does |
|---|---|
| `findLockOnTarget(world, shooter, weaponType, lockRange, excluded)` | The target closest to the line of sight, within 15 degrees and `LockRange`. AA only locks airborne targets; AT only locks targets on the ground or water surface |
| `classifyTargetPosition(entity)` | Whether a target is airborne, on the ground/water surface, or underwater |
| `raycastGroundPoint(world, shooter)` | The point hit along the line of sight, within 128 blocks |
| `applyAccuracySpread(velocity, accuracyDegrees, random)` | Projectile spread from `Accuracy` |

The caller measures and displays the lock time (`LockTime`, `LockTimePerBlock`).

### TV missiles

A player can also steer a TV missile fired from outside a vehicle. Control
ends when the player dies, logs out, changes dimension, or mounts anything.
The range and chunk-loading limits are the same as for vehicles.

### Hit detection

A `VehicleProjectileEntity` hits vehicles against their model shape and
applies its configured damage and blast, as usual. Flares can also defeat
its guidance.

### Drawing OBJ models (client)

To draw an OBJ model, for example as an item, combine the following:

```java
ObjModel model = ObjModelLoader.get(Identifier.of("myaddon", "models/obj/my_launcher.obj")).orElse(null);
RenderLayer layer = DitherCutoutLayers.entityDitherCutout(Identifier.of("myaddon", "textures/item/my_launcher.png"));
VehicleEntityRenderer.renderTriangles(queue, matrices, layer, model.getTriangles(), light, overlay, 0xFFFFFFFF);
```

A layer from `DitherCutoutLayers` always matches both the Config settings
(translucency mode, triangle rendering) and the model's format.

---

## 9. Limitations

### Version matching

The base mod and the addon must use matching Minecraft, Fabric Loader,
and Yarn mapping versions. Mismatched Yarn mappings in particular give
the same class and method a different name, so the addon compiles but
fails to resolve at runtime.

### Depending on internals

Methods beginning with `tudursvehiclemod$` are this mod's own additions.
The `protected` and `public` ones are exposed deliberately, but their
signatures may change in a future update. Declaring an explicit version
on the addon's own side is recommended.
