# Readme_Weapon_Torpedo.md - Fields specific to torpedoes (`Torpedo`)

In addition to the common fields in `Readme_Weapon.md`, these fields only
have meaning for `Type = Torpedo`.

---

## Firing restrictions

- Can only be used while the vehicle's own attitude is close to level
  (both pitch and roll within ±15 degrees) and at low altitude (the
  distance to the ground/water surface directly below must be no more
  than `TorpedoMaxAltitude`, 40 blocks by default)
- A submarine (see `Readme_Vehicle_Submarine.md`) can use this weapon
  type even while submerged (hatch closed)

---

## Behavior

Right after firing, it inherits the vehicle's own velocity and falls
under `Gravity`. **The instant it hits the water, it switches to a
dedicated underwater cruise phase**, and from then on is no longer
affected by `Gravity`. Underwater it levels out gradually from the angle
it entered the water at and runs at `TargetDepth` (the same for every
torpedo, whatever `GuidedTorpedo` is set to).

**Example**:
```
DisplayName = Type.93 Torpedo
Type = Torpedo
Power = 200
Gravity = -0.03
AccelerationInWater = 2.5
VelocityInWater = 0.3
TargetDepth = 3.0
GuidedTorpedo = true
Explosion = 6
ExplosionInWater = 12
Round = 3
```

## `AccelerationInWater`
- **Format**: number (default: `4.0`)
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
- **Format**: boolean (default: `false`)
- **Description**: Whether it is steered horizontally underwater. Keeping
  to `TargetDepth` happens either way.
  - `false` (default): an unguided torpedo. It runs straight on in the
    direction it was travelling when it entered the water.
  - `true`: a guided torpedo. The kind of guidance is switched with lock
    mode (the **B key** by default - the same key as commanding
    wingmen):
    - Lock mode off: it heads for the aim point at the moment of firing
      (the point under the crosshair). Once within 3 blocks of that
      point it runs straight on.
    - Lock mode on: while this weapon is selected, the entity nearest
      the crosshair (within 15 degrees of it, up to 1000 blocks away) is
      highlighted. When fired, the torpedo chases that entity (like a
      missile's lock-on). It cannot be fired with nothing highlighted.
  - How fast it turns is set by `TurnRate` (degrees per tick). At 0 or
    less, it turns at 2 degrees per tick.
  - Either way, once it has passed its target (the target is behind it at
    close range), it carries straight on rather than turning back - so it
    never ends up circling the target.
  - Fired by an AI (a wingman, a Carrier aircraft or a dummy pilot), it
    chases the target that AI is attacking (lock mode plays no part).
- **Example**: `GuidedTorpedo = true`

## `TorpedoMaxAltitude`
- **Format**: number, in blocks (default: `40`)
- **Description**: The highest altitude a torpedo can be fired (dropped)
  from, measured from the ground or water surface directly below the
  vehicle. It cannot be fired from any higher. This used to be fixed at
  15 blocks.
- **Example**: `TorpedoMaxAltitude = 30`

## `GravityInWater`
- **Format**: number (default: the same value as the ordinary `Gravity`)
- **Description**: A separate fall speed used specifically after
  submersion, distinct from the fall speed before entering the water
  (this can also be used as a common field per `Readme_Weapon.md`, but it
  carries particular significance for a torpedo).
- **Example**: `GravityInWater = 0.0`
