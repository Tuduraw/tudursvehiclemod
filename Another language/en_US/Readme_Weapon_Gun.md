# Readme_Weapon_Gun.md - Fields specific to direct-fire weapons (`MachineGun1` / `MachineGun2` / `Rocket`)

In addition to the common fields in `Readme_Weapon.md`, these fields only
have meaning for these types. All of these are unguided, straight-line
weapons - they keep traveling in the direction they were fired (with
error added per `Accuracy`) while falling under gravity.

---

## Differences between the types

- **`MachineGun1`**: fires in a direction fixed to the vehicle (unless
  `Aim_range` is set on the mount, it always uses the firing position's
  own `mount_yaw` / `mount_pitch` direction)
- **`MachineGun2`**: fires aimed at the occupant's own view direction
  (typically used together with an `aim_range` set on the `weapons`
  entry itself)
- **`Rocket`**: like `MachineGun1`, unguided and fixed to the vehicle's
  own direction

**Example** (machine gun):
```
DisplayName = M134 Minigun
Type = MachineGun1
Power = 8
Acceleration = 4.0
Round = 100
ReloadTime = 80
Delay = 1
HeatCount = 20
MaxHeatCount = 150
```

`Acceleration` (projectile speed) can be set as high as 100.0 for only
these 3 types (every other weapon type caps at 4.0).

---

## `MachineGun2`-only: switching HE rounds via `ModeNum`

- **Format**: integer, `2`
- **Description**: Setting `ModeNum = 2` on `Type = MachineGun2` lets a
  dedicated key switch between normal rounds and HE rounds (rounds that
  explode, when `Explosion` is set). Has no effect if `Explosion` is `0`.
- **Example**:
  ```
  Type = MachineGun2
  ModeNum = 2
  Explosion = 1
  ```

---

## `Rocket`-only: switching HEIAP rounds via `ModeNum`

- **Format**: integer, `2`
- **Description**: Setting `ModeNum = 2` on `Type = Rocket` lets a
  dedicated key switch between normal rounds and HEIAP rounds (rounds
  that scatter multiple submunitions in the air). See the `Bomblet`
  family of fields below for the submunitions themselves.
- **Example**:
  ```
  Type = Rocket
  ModeNum = 2
  Bomblet = 25
  BombletSTime = 5
  BombletDiff = 0.7
  ```

---

## Combining with the heat-based system

`MachineGun1` is the weapon type most often combined with
`HeatCount` / `MaxHeatCount` (see `Readme_Weapon.md`) for a rapid-fire
weapon like a Gatling gun. If you want to represent a visual part
continuously spinning while firing, combine this with `spins_while_firing`
on the vehicle's own `weapon_parts` entry (see `Readme_Vehicle.md`).

---

## Cluster (submunition scatter)

A set of fields that can be used together with `Rocket` (the HEIAP round
under `ModeNum = 2`) or an ordinary `Bomb` (see `Readme_Weapon_Bomb.md`).

### `Bomblet`
- **Format**: integer (default: `0`, disabled)
- **Description**: How many submunitions deploy after use.
- **Example**: `Bomblet = 25`

### `BombletSTime`
- **Format**: integer (default: `0`)
- **Description**: How long (ticks) until the submunitions deploy.
- **Example**: `BombletSTime = 5`

### `BombletDiff`
- **Format**: number (default: `0.7`)
- **Description**: The submunitions' own spread rate.
- **Example**: `BombletDiff = 0.7`

### `ModelBomblet`
- **Format**: string (optional)
- **Description**: The submunition's own 3D model name (independent from
  the parent round's own `ModelBullet` - if omitted, it looks the same as
  the parent round).
- **Example**: `ModelBomblet = cbc`
