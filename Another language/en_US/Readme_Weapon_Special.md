# Readme_Weapon_Special.md - Fields specific to special weapons

Applies to: `Dispenser` / `Smoke` / `Dummy` / `TargetingPod` / `DropTank`

In addition to the common fields in `Readme_Weapon.md`, these fields only
have meaning for these types. Other than `DropTank`, none of these
actually fire a real projectile.

---

## `Dummy`

An unusable weapon. Used purely to display text in the weapon slot (for
example, to pad out the look of a weapon bay). It has no additional
fields of its own. Using it never produces a firing sound, a projectile,
or a cooldown at all.

**Example**:
```
DisplayName = ---
Type = Dummy
```

---

## `Dispenser`

Applies the same effect as using a configured Minecraft item, targeted at
the impact point (the visible flight path to the actual impact point is
still shown).

**Example** (a fire-extinguishing water bucket effect):
```
DisplayName = Fire Extinguisher
Type = Dispenser
DispenseItem = water_bucket
DispenseRange = 4
Acceleration = 2.0
Gravity = -0.03
Round = 5
```

### `DispenseItem`
- **Format**: string (an item id, optional)
- **Description**: The item to apply at the impact point. For example,
  setting `flint_and_steel` produces the same effect as using flint and
  steel at the impact point (ignition). Whether there's any effect at all
  depends on the item. As an exception, `water_bucket` doesn't place
  water - it instead extinguishes fire/lava near the impact point.
- **Example**: `DispenseItem = flint_and_steel`

### `DispenseRange`
- **Format**: number (default: `4.0`)
- **Description**: The range (blocks) over which `DispenseItem`'s own
  effect applies.
- **Example**: `DispenseRange = 4`

---

## `Smoke`

A weapon that simply produces a cluster of particles, with a configured
color, size, and lifetime, at the firing position's own current location
(for a contrail effect, etc.).

**Example**:
```
DisplayName = Smoke White
Type = Smoke
SmokeColor = 230, 255, 255, 255
SmokeSize = 2.0
SmokeMaxAge = 500
Delay = 1
```

### `SmokeColor`
- **Format**: `A, R, G, B` (each 0-255), or `0xAARRGGBB` form (default: a
  semi-transparent tan)
- **Description**: The smoke's own color.
- **Example**: `SmokeColor = 230, 200, 20, 80`

### `SmokeSize`
- **Format**: number (default: `2.0`)
- **Description**: The smoke particle's own size.
- **Example**: `SmokeSize = 2.0`

### `SmokeMaxAge`
- **Format**: integer (default: `500`)
- **Description**: How long (ticks) the smoke stays visible.
- **Example**: `SmokeMaxAge = 500`

---

## `TargetingPod`

Displays a spot marker on a mob/player, or a mark on a block (deals no
actual damage at all).

**Example**:
```
DisplayName = Targeting Pod
Type = TargetingPod
Target = planes/helicopters/vehicles
Length = 100
Radius = 45
MarkTime = 10
```

### `Target`
- **Format**: multiple values separated by `/` are allowed (e.g.
  `monsters/others`)
- **Description**: What kind of target can be spotted. Choose from:
  - `planes` / `helicopters` / `vehicles`: this mod's own various
    vehicles
  - `players`: other players
  - `monsters`: hostile mobs
  - `others`: other mobs
  - `block`: switches to a block-marking mode instead (every other
    value is ignored entirely - this stops being an entity-spotting
    feature at all)
- **Example**: `Target = planes/helicopters/vehicles`

### `Length`
- **Format**: number (default: `100.0`)
- **Description**: The distance (blocks) over which spotting is
  possible.
- **Example**: `Length = 100`

### `Radius`
- **Format**: number (default: `45.0`)
- **Description**: The spotting cone's own radius angle (degrees).
- **Example**: `Radius = 45`

### `MarkTime`
- **Format**: number, in seconds (default: `10.0`)
- **Description**: How long the spot marker stays visible.
- **Example**: `MarkTime = 10`

---

## `DropTank`

A weapon type this project extends with of its own (not an original MC
Heli `Type`). Represents an external drop tank (fuel tank). While
equipped, it temporarily increases the vehicle's own max fuel based on
its own remaining ammo count. Using it (dropping it) decreases remaining
ammo by 1, reducing the max-fuel increase along with it. If current fuel
is above the reduced max fuel afterward, the excess simply disappears (it
is not refunded or restored).

If a `BulletModel` (projectile model) is set, it falls with exactly the
same behavior as `Bomb` (inheriting the vehicle's own current velocity,
droppable regardless of the vehicle's own attitude). What actually
happens at the point of impact (whether it explodes, etc.) depends on the
other settings, the same as any other weapon.

### `FuelPerAmmo`
- **Format**: number (default: `0.0`)
- **Description**: How much max fuel increases per remaining round. The
  actual increase is `FuelPerAmmo × current remaining ammo`, recalculated
  automatically every time remaining ammo decreases (is dropped). If
  omitted (the default), this has no effect on max fuel at all.
- **Example**: `FuelPerAmmo = 50.0`

**Example**:
```
DisplayName = Drop Tank
Type = DropTank
MagazineNum = 2
FuelPerAmmo = 50.0
BulletModel = drop_tank
```
