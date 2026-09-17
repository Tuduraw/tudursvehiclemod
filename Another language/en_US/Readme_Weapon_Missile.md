# Readme_Weapon_Missile.md - Fields specific to guided weapons

Applies to: `ASMissile` / `MkRocket` / `AAMissile` / `ATMissile` /
`Missile` / `ASWeapon` / `TVMissile`

In addition to the common fields in `Readme_Weapon.md`, these fields only
have meaning for these types.

---

## Differences between the types

### `ASMissile` / `MkRocket`
Targets the point where the pilot's own line of sight meets the ground
(or, underwater, the seabed) at the exact instant of firing, and flies
gradually turning toward it after launch. Doesn't use any lock-on
mechanism against a specific entity. Unaffected by gravity while guiding.
`MkRocket` behaves exactly the same as `ASMissile` (MC Heli categorizes
them separately, but there's no implementation difference here).

**Example**:
```
DisplayName = AGM-65 Maverick
Type = ASMissile
Power = 50
Acceleration = 3.5
Gravity = -0.02
Explosion = 5
Round = 2
```

### `AAMissile` / `ATMissile` / `Missile`
The instant the fire key is pressed, automatically locks onto a target
currently within the aim reticle (near the center of view, within a
configured angle) and homes on it from then on (recalculating direction
every tick if the target moves). Pressing the fire key before the lock
completes does not fire it at all (see `LockTime` below). If the target
dies or disappears, it falls under ordinary gravity from then on.

- **`AAMissile`**: can only lock onto a target that is **currently
  airborne**
- **`ATMissile`**: can only lock onto a target that is **currently on the
  ground or on the water surface**
- **`Missile`** (specific to this project, no such type name exists in
  the original MC Heli): behaves identically to `AAMissile` / `ATMissile`
  for lock-on, guidance, and firing restrictions, but applies no
  filtering at all by airborne/ground/underwater target category

**Example** (air-to-air missile):
```
DisplayName = AIM-9 Sidewinder
Type = AAMissile
Power = 45
Acceleration = 3.5
LockTime = 40
RigidityTime = 7
ProximityFuseDist = 3.0
Sight = MissileSight
Round = 2
```

Whether a target counts as "airborne" or "on the ground" is based on the
target's own **current physical state** (not the vehicle's own type). A
target sufficiently far above the ground and not touching water counts
as "airborne"; one submerged in water (its eye position below the water
surface) counts as "underwater" - neither can be locked by `AAMissile` /
`ATMissile` / `Missile` (use `ASWeapon` for an underwater target).
Floating or standing on the water surface counts as "on the ground" and
is a valid target for `ATMissile`.

### `ASWeapon` (specific to this project, no such type name exists in the original MC Heli)
An anti-submarine missile. Like `ASMissile`, it flies guided toward the
aim point at the moment of firing (for an underwater target, the
coordinate near that target's own seabed) - this is not a lock-on. Until
the distance to the target is within `DiveDistance` blocks, it keeps
flying toward the target's own horizontal coordinate just above the
water surface; once it closes to within that distance, it begins diving
toward the target itself. The instant it hits the water, it switches to
exactly the same underwater cruise system a torpedo (`Torpedo`) uses
(reusing `AccelerationInWater` / `VelocityInWater` / `TargetDepth`
as-is - see `Readme_Weapon_Torpedo.md`). The complex multi-stage behavior
a real anti-submarine weapon has (warhead separation, etc.) is not
implemented.

**Example**:
```
DisplayName = RUM-139 VL-ASROC
Type = ASWeapon
Power = 55
Acceleration = 3.0
DiveDistance = 10.0
AccelerationInWater = 2.0
VelocityInWater = 0.3
TargetDepth = 3.0
Explosion = 6
ExplosionInWater = 6
Round = 4
```

### `TVMissile`
After firing, if `ModeNum` isn't set (or is set to 1), it flies straight
in the firing direction like an ordinary unguided round, but the pilot
can directly steer the missile itself with mouse movement even after
firing (control of the launching vehicle is temporarily given up while
doing so). Setting `ModeNum = 2` switches it to an ordinary guided-round
mode instead (like `ASMissile`, guided toward the aim point at the
moment of firing, with no pilot steering).

**Example** (pilot-steered):
```
DisplayName = AGM-114 Hellfire TV
Type = TVMissile
Power = 50
Acceleration = 3.0
Explosion = 5
Round = 4
```

---

## Lock-on family (`AAMissile` / `ATMissile` / `Missile`)-only fields

### `LockTime`
- **Format**: integer (default: `0`, locks instantly)
- **Description**: How many ticks the same target must stay within the
  aim reticle before the lock is complete. Pressing the fire key before
  this time has elapsed does not fire at all (this project never fires
  an unguided shot before the lock completes).
- **Example**: `LockTime = 40`

### `RidableOnly`
- **Format**: boolean
- **Description**: In the original MC Heli this means "can only lock
  while riding a vehicle", but since this project's own weapon system is
  entirely vehicle-mounted to begin with (there is no such thing as a
  hand-held weapon item), this has no practical effect regardless of its
  value. It's only parsed and retained for compatibility.
- **Example**: `RidableOnly = true`

### `ProximityFuseDist`
- **Format**: number (default: `0`, disabled)
- **Description**: A locked guided round detonates once it closes to
  within this distance (blocks) of the target, even without an actual
  direct hit.
- **Example**: `ProximityFuseDist = 3.0`

### `RigidityTime`
- **Format**: integer (default: `7`)
- **Description**: How long (ticks) after firing before actual guidance
  (course correction) actually begins. It flies straight right after
  launch, and only starts guiding once it's physically far enough from
  the launching vehicle.
- **Example**: `RigidityTime = 7`

---

## `ASMissile` / `MkRocket` / `ASWeapon` common field

### `DiveDistance`
- **Format**: number (default: `10.0`)
- **Description**: `ASWeapon`-only. Begins diving once the distance to
  the target drops to this value (blocks) or below.
- **Example**: `DiveDistance = 10.0`

---

## `ATMissile`-only: Top Attack mode

### `ModeNum`
- **Format**: integer, `2`
- **Description**: Setting `ModeNum = 2` on `Type = ATMissile` lets a
  dedicated key switch between ordinary guidance and Top Attack mode (a
  guidance profile that climbs above the target before diving steeply).
- **Example**:
  ```
  Type = ATMissile
  ModeNum = 2
  LockTime = 60
  ```
