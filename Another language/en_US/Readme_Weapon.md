# Readme_Weapon.md - Weapon config files (common fields)

Target file: `assets/<namespace>/weapons/<weapon_name>.txt`

An MC Heli-compatible `Key = Value` text format (not JSON). Key names
are case-insensitive. A line starting with `;`, and a line containing no
`=`, are treated as a comment/ignored. If `;` appears partway through a
value, everything after it on that same line is ignored as an inline
comment.

The vehicle's own JSON definition (the `weapons` array) references this
file's own name (without extension) via `weapon_name`. The file itself
can be edited at any time and takes effect immediately via `/reload` (no
need to respawn the vehicle).

For fields that only have meaning for a specific type, see the
corresponding `Readme_Weapon_<Type>.md`.

- `Readme_Weapon_Bomb.md` — drop weapons (`Bomb` / `Depth`)
- `Readme_Weapon_Torpedo.md` — torpedoes (`Torpedo`)
- `Readme_Weapon_Missile.md` — guided weapons (`ASMissile` / `MkRocket` /
  `AAMissile` / `ATMissile` / `Missile` / `ASWeapon` / `TVMissile`)
- `Readme_Weapon_Special.md` — special weapons (`Dispenser` / `Smoke` /
  `Dummy` / `TargetingPod`)
- `Readme_Weapon_Cas.md` — support-aircraft launch weapons (`CAS` /
  `Carrier`)

Only fields that are partially or fully incompatible with MC Heli are
listed below. For every other field, see MC Heli's own documentation
(fully documenting those here is still pending).

### `Type`
- **Format**: string
- **Description**: The weapon's own behavior type. Required. Some
  implementations differ from MC Heli, and some weapon types are unique
  to this mod, so see each `Readme_Weapon_<Type>.md` for the accepted
  values and details:
  `MachineGun`/`Rocket` (`Readme_Weapon_Gun.md`), `Bomb`/`Depth`
  (`Readme_Weapon_Bomb.md`), `Torpedo` (`Readme_Weapon_Torpedo.md`),
  `ASMissile`/`MkRocket`/`AAMissile`/`ATMissile`/`Missile`/`ASWeapon`/`TVMissile`
  (`Readme_Weapon_Missile.md`), `Dispenser`/`Smoke`/`Dummy`/`TargetingPod`
  (`Readme_Weapon_Special.md`), `CAS`/`Carrier` (`Readme_Weapon_Cas.md`).
  Any unsupported or unrecognized value is treated as "other" and never
  fires a live round.
- **Example**: `Type = MachineGun`

### `DisplayMortarDistance`
- **Format**: boolean (default: `false`)
- **Description**: Whether to show the predicted impact distance on the
  HUD (for `Bomb`/`Rocket`, instead of a HUD number this becomes a
  circular marker on the ground showing the predicted impact point).
- **Combined with `CasTargetMode`**: setting `CasTargetMode` (default
  `Ballistic`, see `Readme_Weapon_Cas.md` for details) to `Collision`
  makes `MachineGun`, `AS_MISSILE`, `MK_ROCKET`, `Bomb`, and `Rocket` all
  compute and display the distance to the point where the round would
  actually strike a block, instead of each type's own normal (default)
  calculation. For `Bomb`/`Rocket`, this distance value is shown
  alongside the existing circular marker rather than replacing it.
- **Example**: `DisplayMortarDistance = true`

---
