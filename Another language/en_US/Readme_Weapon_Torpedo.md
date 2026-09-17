# Readme_Weapon_Torpedo.md - Fields specific to torpedoes (`Torpedo`)

In addition to the common fields in `Readme_Weapon.md`, these fields only
have meaning for `Type = Torpedo`.

---

## Firing restrictions

- Can only be used while the vehicle's own attitude is close to level
  (both pitch and roll within ±15 degrees) and at low altitude (judged by
  distance to the ground/water surface directly below)
- A submarine (see `Readme_Vehicle_Submarine.md`) can use this weapon
  type even while submerged (hatch closed)

---

## Behavior

Right after firing, it inherits the vehicle's own velocity and falls
under `Gravity`. **The instant it hits the water, it switches to a
dedicated underwater cruise phase**, and from then on is no longer
affected by `Gravity`.

**Example**:
```
DisplayName = Mk46 Torpedo
Type = Torpedo
Power = 60
Gravity = -0.03
AccelerationInWater = 2.5
VelocityInWater = 0.3
TargetDepth = 3.0
GuidedTorpedo = true
Explosion = 6
ExplosionInWater = 6
Round = 2
```

## `AccelerationInWater`
- **Format**: number (default: `4.0`, max 4.0)
- **Description**: The target speed while cruising underwater. Gradually
  accelerates toward this speed starting right after hitting the water.
- **Example**: `AccelerationInWater = 2.5`

## `VelocityInWater`
- **Format**: number (default: `0.5`)
- **Description**: How responsively speed changes underwater (an easing
  factor - each tick, speed moves this fraction of the way toward the
  target speed).
- **Example**: `VelocityInWater = 0.3`

## `TargetDepth`
- **Format**: number (default: `2.0`)
- **Description**: An addition specific to this project (not supported
  by MC Heli). This many blocks below the water surface becomes the
  target cruising depth (`water surface Y - TargetDepth`).
- **Example**: `TargetDepth = 3.0`

## `GuidedTorpedo`
- **Format**: boolean (default: `true`)
- **Description**: When `true` (default), homes toward the block that was
  specified at the moment it hit the water (the aim point at the time of
  firing) - horizontal direction is fixed, while pitch at the moment of
  water entry is gradually corrected toward the target depth. When
  `false`, it becomes an unguided torpedo that travels straight from
  wherever it entered the water (speed control via
  `AccelerationInWater` / `VelocityInWater` still applies - only the
  course correction itself is skipped).
- **Example**: `GuidedTorpedo = false`

## `GravityInWater`
- **Format**: number (default: the same value as the ordinary `Gravity`)
- **Description**: A separate fall speed used specifically after
  submersion, distinct from the fall speed before entering the water
  (this can also be used as a common field per `Readme_Weapon.md`, but it
  carries particular significance for a torpedo).
- **Example**: `GravityInWater = 0.0`
