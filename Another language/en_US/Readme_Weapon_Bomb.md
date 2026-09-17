# Readme_Weapon_Bomb.md - Fields specific to drop weapons (`Bomb` / `Depth`)

In addition to the common fields in `Readme_Weapon.md`, these fields and
constraints only have meaning for these types.

---

## `Bomb`

A bomb dropped straight down. It has the following constraints and
behavior:

- **Firing restriction**: can only be used while the vehicle's own
  attitude is close to level (both pitch and roll within ±15 degrees)
- After release it starts out inheriting the vehicle's own velocity and
  falls under `Gravity` (no fixed initial velocity is used)
- Explodes the instant it touches the water surface (as long as either
  `Explosion` or `ExplosionInWater` is set)

**Example**:
```
DisplayName = Mk82 500lb Bomb
Type = Bomb
Power = 40
Gravity = -0.05
Explosion = 4
ExplosionBlock = 4
Round = 4
ReloadTime = 200
```

### `Destruct`
- **Format**: boolean (default: `false`)
- **Description**: When `true`, the firing vehicle itself self-destructs
  the instant it's used. Only has any effect if this vehicle is an
  unmanned (UAV) helicopter.
- **Example**: `Destruct = true`

---

## `Depth`

Behaves exactly like `Bomb`, with the exact same firing restrictions
(including the attitude limit), but **does not explode at the water
surface**. It keeps sinking underwater and only explodes once it actually
hits a solid block/entity (or per some other fuse setting). Use this as a
water-penetrating version of `Bomb`. This is a type name specific to this
project, not present in the original MC Heli.

**Example** (anti-submarine bomb):
```
DisplayName = Depth Charge
Type = Depth
Power = 30
Gravity = -0.03
Explosion = 3
ExplosionInWater = 3
Round = 8
```

---

## Cluster (submunition scatter)

Combining `Bomblet` / `BombletSTime` / `BombletDiff` / `ModelBomblet` (see
`Readme_Weapon_Gun.md`) lets you use this as a cluster bomb.

**Example**:
```
Type = Bomb
Bomblet = 25
BombletSTime = 5
BombletDiff = 0.7
ModelBomblet = cbc
```
