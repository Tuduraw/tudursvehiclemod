# Readme_Weapon_Bomb.md - Fields specific to drop weapons (`Bomb` / `Depth`)

In addition to the common fields in `Readme_Weapon.md`, these fields and
constraints only have meaning for these types.

---

## `Bomb`

A bomb dropped straight down. It has the following constraints and
behavior:

- After release it starts out inheriting the vehicle's own velocity and
  falls under `Gravity`.
- Explodes the instant it touches the water surface. To have it sink
  underwater instead, use Depth (below).

**Example**:
```
DisplayName = 500lb Bomb
Type = Bomb
Power = 100
Gravity = -0.05
Explosion = 8
ExplosionBlock = 8
Round = 2
ReloadTime = 800
```

### `Destruct`
- **Format**: boolean (default: `false`)
- **Description**: When `true`, the firing vehicle itself self-destructs
  the instant it's used. Only has any effect if this vehicle is an
  unmanned (UAV) helicopter.
- **Example**: `Destruct = true`

---

## `Depth`

Behaves exactly like `Bomb`, but **does not explode at the water
surface**. It keeps sinking underwater and only explodes once it actually
hits a solid block/entity (or per some other fuse setting). Use this as a
water-penetrating version of `Bomb`. This is a type name specific to this
mod, not present in MC Heli.

**Example** (anti-submarine bomb):
```
DisplayName = Depth Charge
Type = Depth
Power = 30
Gravity = -0.03
Explosion = 3
ExplosionInWater = 8
Round = 8
```

---

## Cluster (submunition scatter)

Combining `Bomblet` / `BombletSTime` / `BombletDiff` / `ModelBomblet` (see
MC Heli's own documentation) lets you use this as a cluster bomb.

**Example**:
```
Type = Bomb
Bomblet = 30
BombletSTime = 6
BombletDiff = 0.8
ModelBomblet = samplebomblet
```
