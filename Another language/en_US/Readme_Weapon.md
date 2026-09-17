# Readme_Weapon.md - Weapon config files (common fields)

Target file: `assets/<namespace>/weapons/<weapon_name>.txt`

An MC Heli-style `Key = Value` text format (not JSON). Key names are
case-insensitive. A line starting with `;`, and a line containing no
`=`, are treated as a comment/ignored. If `;` appears partway through a
value, everything after it on that same line is ignored as an inline
comment.

The vehicle's own JSON definition (the `weapons` array) references this
file's own name (without extension) via `weapon_name`. The file itself
can be edited at any time and takes effect immediately via `/reload` (no
need to respawn the vehicle).

This document covers the fields common to every weapon type, regardless
of `Type`'s own value. For fields that only have meaning for a specific
type, see the corresponding `Readme_Weapon_<Type>.md`.

- `Readme_Weapon_Gun.md` — direct-fire weapons (`MachineGun1` /
  `MachineGun2` / `Rocket`)
- `Readme_Weapon_Bomb.md` — drop weapons (`Bomb` / `Depth`)
- `Readme_Weapon_Torpedo.md` — torpedoes (`Torpedo`)
- `Readme_Weapon_Missile.md` — guided weapons (`ASMissile` / `MkRocket` /
  `AAMissile` / `ATMissile` / `Missile` / `ASWeapon` / `TVMissile`)
- `Readme_Weapon_Special.md` — special weapons (`Dispenser` / `Smoke` /
  `Dummy` / `TargetingPod`)
- `Readme_Weapon_Cas.md` — support-aircraft launch weapons (`CAS` /
  `Carrier`)

Under each field's own heading, an actual example entry is given as
`Example:`.

---

## Contents

1. Basic info
2. Damage
3. Ammo capacity/reload/resupply
4. Ballistics/penetration/accuracy
5. Explosion/fire/fuse
6. Sound
7. Appearance (projectile/trail/muzzle flash/shell casing)
8. Heat system (overheating)
9. Aiming/camera
10. Other

---

## 1. Basic info

### `DisplayName`
- **Format**: string (ASCII letters/digits/symbols only, no full-width
  characters)
- **Description**: The weapon name shown in menus etc.
- **Example**: `DisplayName = M134 Minigun`

### `Type`
- **Format**: string
- **Description**: The weapon's own behavior type. Required. See each
  `Readme_Weapon_<Type>.md` for the available values and details:
  `MachineGun1` / `MachineGun2` / `Rocket` (`Readme_Weapon_Gun.md`),
  `Bomb` / `Depth` (`Readme_Weapon_Bomb.md`), `Torpedo`
  (`Readme_Weapon_Torpedo.md`), `ASMissile` / `MkRocket` / `AAMissile` /
  `ATMissile` / `Missile` / `ASWeapon` / `TVMissile`
  (`Readme_Weapon_Missile.md`), `Dispenser` / `Smoke` / `Dummy` /
  `TargetingPod` (`Readme_Weapon_Special.md`), `CAS` / `Carrier`
  (`Readme_Weapon_Cas.md`). Any unsupported/unrecognized value is
  treated as "other" and never fires a real projectile.
- **Example**: `Type = MachineGun1`

### `Group`
- **Format**: string (optional)
- **Description**: Weapons sharing the same group name (an exact,
  case-sensitive match) share both firing delay (`Delay`) and reload
  time (`ReloadTime`). Using any one of them puts every other weapon in
  the same group into cooldown/reload at the same time too (ammo count
  itself is still independent per weapon). This field exists to prevent
  firing weapon A, immediately switching to weapon B (a different round
  type on the same gun), and firing it right away too.
- **Example**: `Group = MainGun`

---

## 2. Damage

### `Power`
- **Format**: number
- **Description**: Base damage amount.
- **Example**: `Power = 8`

### `DamageFactor`
- **Format**: `target category, multiplier` (multiple lines allowed)
- **Description**: A damage multiplier per target category. The target
  category is one of `player` / `heli` (or `helicopter`) / `plane` /
  `tank` / `vehicle`. Each line you write is applied.
- **Example**:
  ```
  DamageFactor = tank, 2.0
  DamageFactor = player, 1.0
  ```

---

## 3. Ammo capacity/reload/resupply

### `Round`
- **Format**: integer (default: `0`)
- **Description**: Magazine capacity. `0` (or omitted) means unlimited
  (no reload cycle).
- **Example**: `Round = 100`

### `ReloadTime`
- **Format**: integer (default: `0`)
- **Description**: How long (ticks) until reloading is complete.
- **Example**: `ReloadTime = 80`

### `Delay`
- **Format**: number (default: `0`)
- **Description**: How long (ticks) until it can be used again.
- **Example**: `Delay = 5`

### `MaxAmmo`
- **Format**: integer (default: `0`)
- **Description**: The total reserve ammo this weapon can hold (an
  independent pool separate from the magazine). `0` (or omitted) means
  unlimited, and automatic reloading continues without limit. When
  there's a cap, automatic reloading stops once reserve ammo runs out.
- **Example**: `MaxAmmo = 40`

### `SuppliedNum`
- **Format**: integer (default: `0`)
- **Description**: How many rounds are added per single resupply action
  (capped at `MaxAmmo`).
- **Example**: `SuppliedNum = 10`

### `Item`
- **Format**: `count, item id` (up to 3 lines; only `iron_ingot`,
  `gunpowder`, or `redstone` can be specified)
- **Description**: The items and quantities required for a single
  resupply.
- **Example** (3 iron ingots, 4 gunpowder, and 2 redstone resupplies 10
  rounds):
  ```
  Item = 3, iron_ingot
  Item = 4, gunpowder
  Item = 2, redstone
  ```

---

## 4. Ballistics/penetration/accuracy

### `Acceleration`
- **Format**: number
- **Description**: Projectile speed. Only `MachineGun1` / `MachineGun2` /
  `Rocket` can go as high as 100.0; every other type caps at 4.0.
- **Example**: `Acceleration = 4.0`

### `Gravity`
- **Format**: number
- **Description**: The warhead's own fall speed (a larger absolute value
  falls faster).
- **Example**: `Gravity = -0.04`

### `Piercing`
- **Format**: integer (default: `0`)
- **Description**: How many additional blocks it can penetrate after
  impact. `0` means no penetration.
- **Example**: `Piercing = 2`

### `Accuracy`
- **Format**: number (default: `0`)
- **Description**: An angular error (degrees) applied once, at the
  moment of firing. Only has meaning for an unguided weapon
  (`MachineGun1` / `MachineGun2` / `Rocket` / `MkRocket`). A guided
  weapon corrects its own course after firing, so this field effectively
  has no impact on it.
- **Example**: `Accuracy = 1`

### `BulletColor`
- **Format**: `A, R, G, B` (each 0-255), or `0xAARRGGBB` form
- **Description**: The color of the projectile/trail while flying
  through the air. Default is opaque white (no tint).
- **Example**: `BulletColor = 255, 255, 255, 255`

### `BulletColorInWater`
- **Format**: same format as `BulletColor`
- **Description**: The projectile's own color while moving underwater.
  Overrides `BulletColor`.
- **Example**: `BulletColorInWater = 255, 25, 25, 75`

---

## 5. Explosion/fire/fuse

### `Explosion`
- **Format**: number (default: `0`)
- **Description**: Explosion power on impact (`0` = no explosion, `1` =
  roughly a ghast fireball).
- **Example**: `Explosion = 2`

### `ExplosionInWater`
- **Format**: number (default: the same value as `Explosion`)
- **Description**: Explosion power on an underwater impact.
- **Example**: `ExplosionInWater = 0`

### `ExplosionBlock`
- **Format**: number
- **Description**: Block-destruction power on impact. If omitted or `0`,
  it destroys no blocks at all.
- **Example**: `ExplosionBlock = 2`

### `ExplosionAltitude`
- **Format**: number (default: `0`, disabled)
- **Description**: Once the height above the ground directly below this
  projectile drops to this value or below, it forcibly explodes, even
  without hitting anything (an airburst).
- **Example**: `ExplosionAltitude = 10`

### `Flaming`
- **Format**: boolean (default: `false`)
- **Description**: Whether it scatters fire on impact. Only has effect
  when `Explosion > 0`.
- **Example**: `Flaming = true`

### `FAE`
- **Format**: boolean (default: `false`)
- **Description**: When `true`, becomes a fuel-air explosive, and never
  destroys any blocks at all regardless of the `ExplosionBlock` setting.
- **Example**: `FAE = true`

### `DelayFuse`
- **Format**: integer (default: unset, despawns the instant it hits
  something)
- **Description**: The delay (ticks) from impact until it actually
  despawns. If `Explosion`/`ExplosionInWater` is set, it explodes when it
  despawns. Without combining this with `Bound` (bouncing), it
  effectively means almost nothing, since it would despawn right after
  impact anyway.
- **Example**: `DelayFuse = 30`

### `TimeFuse`
- **Format**: integer (default: unset)
- **Description**: How long (ticks) from firing until it despawns
  (regardless of whether it hit anything). A simple lifetime countdown,
  independent from `DelayFuse`.
- **Example**: `TimeFuse = 30`

### `Bound`
- **Format**: number (default: `0`, no bouncing)
- **Description**: How strongly it bounces on impact.
- **Example**: `Bound = 0.4`

---

## 6. Sound

### `Sound`
- **Format**: string (optional)
- **Description**: The file name (no extension needed) of the sound
  played on use. If omitted, `<weapon name>_snd` is used.
- **Example**: `Sound = rocket_snd`

### `SoundVolume`
- **Format**: number (default: `1.0`)
- **Description**: Volume. Under Minecraft's own rules, `1.0` is the
  maximum volume, and going above it extends the audible distance
  instead.
- **Example**: `SoundVolume = 3`

### `SoundPitch`
- **Format**: number, 0.0-1.0 (default: `1.0`)
- **Description**: The sound's own pitch.
- **Example**: `SoundPitch = 1.0`

### `SoundPitchRandom`
- **Format**: number, 0.0-1.0 (default: `0.0`)
- **Description**: How much random variation is applied to pitch.
- **Example**: `SoundPitchRandom = 0.1`

### `SoundDelay`
- **Format**: integer (default: `0`)
- **Description**: The minimum interval (ticks) between successive
  sounds during rapid fire. Prevents the sound from overlapping
  excessively on a rapid-fire weapon.
- **Example**: `SoundDelay = 1`

---

## 7. Appearance (projectile/trail/muzzle flash/shell casing)

### `ModelBullet`
- **Format**: string (optional)
- **Description**: The projectile's own 3D model name.
  `models/obj/bullets/<name>.obj` and
  `textures/vehicle/bullet_<name>.png` are used. If unset, it appears as
  a vanilla item instead.
- **Example**: `ModelBullet = bullet`

### `TrajectoryParticle`
- **Format**: string (optional)
- **Description**: A trail effect produced while in flight. One of
  `none` / `explode` / `flame` / `hugeexplosion` / `largeexplode` /
  `largesmoke` / `smoke`. If omitted, no trail at all.
- **Example**: `TrajectoryParticle = flame`

### `TrajectoryParticleStartTick`
- **Format**: integer (default: `0`)
- **Description**: The delay (ticks) from firing until
  `TrajectoryParticle` actually starts appearing.
- **Example**: `TrajectoryParticleStartTick = 10`

### `DisableSmoke`
- **Format**: boolean (default: `false`)
- **Description**: Only when `TrajectoryParticle` is one of the
  smoke-like appearances (`smoke` / `largesmoke` / `explode` /
  `largeexplode` / `hugeexplosion`), this disables it (`flame` is
  unaffected).
- **Example**: `DisableSmoke = true`

### `AddMuzzleFlash`
- **Format**: `distance from firing point, size, display time, A, R, G, B`
- **Description**: The muzzle flash shown when firing.
- **Example**: `AddMuzzleFlash = 0.5, 0.20, 1, 150, 254, 219, 184`

### `AddMuzzleFlashSmoke`
- **Format**: `distance from firing point, count, size, spread, display time, A, R, G, B`
- **Description**: The muzzle smoke shown when firing.
- **Example**: `AddMuzzleFlashSmoke = 2.2, 1, 5.0, 2.0, 15, 180, 250, 245, 240`

### `SetCartridge`
- **Format**: `model name, ejection strength, Yaw, Pitch, display scale, gravity, bounce`
- **Description**: The ejected empty shell casing effect shown when
  firing. Has no effect at all on damage, etc.
- **Example**: `SetCartridge = cartridge, 0.0, 0, 0, 2.00, -0.04, 0.40`

---

## 8. Heat system (overheating)

### `HeatCount`
- **Format**: number (default: `0`)
- **Description**: How much heat increases per use. Setting this switches
  from the ordinary magazine system (`Round`/`ReloadTime`) to a
  heat-based system instead (a Gatling gun, etc.).
- **Example**: `HeatCount = 20`

### `MaxHeatCount`
- **Format**: number (default: `0`)
- **Description**: The heat cap. Reaching it makes the weapon unusable
  until it cools down.
- **Example**: `MaxHeatCount = 150`

---

## 9. Aiming/camera

### `Sight`
- **Format**: `MoveSight` / `None` / `MissileSight` (default: `MoveSight`)
- **Description**: The kind of reticle shown while this weapon is
  selected. `MissileSight` is used for the lock-on display on
  `AAMissile` / `ATMissile` / `Missile` / `ASWeapon`.
- **Example**: `Sight = MissileSight`

### `Zoom`
- **Format**: numbers (multiple values allowed, separated by `,`)
- **Description**: The scope zoom level for a hand-held weapon. With
  multiple values, the Z key cycles between them. Since the hand-held
  weapon system itself isn't implemented at all currently, this
  effectively has no effect.
- **Example**: `Zoom = 4.2, 9.2`

### `FixCameraPitch`
- **Format**: boolean (default: `false`)
- **Description**: While this weapon is selected, keeps the occupant's
  own view pitch fixed level (0 degrees) at all times.
- **Example**: `FixCameraPitch = true`

### `CameraRotationSpeedPitch`
- **Format**: number (default: `1.0`)
- **Description**: A multiplier on view pitch rotation speed while this
  weapon is selected. A smaller value allows finer aim adjustment.
- **Example**: `CameraRotationSpeedPitch = 0.3`

---

## 10. Other

### `ModeNum`
- **Format**: integer, `1` or `2` (default: `1`)
- **Description**: How many selectable modes there are. The meaning
  differs per weapon type (see each `Readme_Weapon_<Type>.md`).
- **Example**: `ModeNum = 2`

### `DisplayMortarDistance`
- **Format**: boolean (default: `false`)
- **Description**: Whether to display the predicted impact distance on
  the HUD (`Bomb`/`Rocket` show a circular marker at the predicted
  ground impact point, rather than a number on the HUD).
- **Combining with `CasTargetMode`**: setting `CasTargetMode` (default
  `Ballistic`, see `Readme_Weapon_Cas.md` for details) to `Collision`
  makes `MachineGun`, `AS_MISSILE`, `MK_ROCKET`, `Bomb`, and `Rocket` all
  compute and display the actual distance to a block-collision point
  instead of that type's own default calculation method. For
  `Bomb`/`Rocket`, this numeric distance is shown alongside the existing
  circular marker, rather than replacing it.
- **Example**: `DisplayMortarDistance = true`

### `Recoil`
- **Format**: number (default: `0`, disabled)
- **Description**: How strongly the vehicle shakes (recoils) on use.
- **Example**: `Recoil = 1.1`

### `RecoilBufCount`
- **Format**: `recoil count, count multiplier while retracting` (default:
  `40, 5`)
- **Description**: Timing adjustment for the recoil animation. A larger
  recoil count extends the overall duration; a larger count multiplier
  while retracting only speeds up the retraction phase.
- **Example**: `RecoilBufCount = 40, 5`

---

That's every field common to every weapon type. For a field that only
has meaning for a specific type (`AccelerationInWater`, `LockTime`,
`CasAircraft`, etc.), see the corresponding `Readme_Weapon_<Type>.md`.
