# Readme_Vehicle_Submarine.md - Fields specific to submarines (`entity_type: submarine`)

In addition to the common fields in `Readme_Vehicle.md`, these fields only
have meaning for a vehicle whose `entity_type` is set to
`"tudursvehiclemod:submarine"`.

---

## `dive_max_speed`

- **Format**: float (optional)
- **Description**: Top speed while submerged. `max_speed` only applies
  **while surfaced**; while submerged, this value is used as the speed
  cap instead. If omitted, `max_speed / 3` is used automatically.
- **Example**:
  ```json
  "max_speed": 1.2,
  "dive_max_speed": 0.5
  ```

---

## Surfacing / diving (H key)

For a submarine, the same H key that opens/closes a hatch via the common
`toggle_parts` field also doubles as the surface/dive toggle (hatch
state = surfaced state - a special-cased meaning distinct from what
"hatch" means for every other vehicle type).

- **Surfaced** (hatch open): weapons can be used normally. The vehicle is
  surfaced immediately after spawning
- **Submerged** (hatch closed): weapon use is frozen. However, a
  `Type = Torpedo` weapon, and any individual weapon file that sets
  `UsableWhileDiving = true`, can still be used while submerged (see
  `Readme_Weapon.md`)
- The physical transition from surfaced to submerged (actually starting
  to sink) is delayed until the hatch part's own `toggle_parts` animation
  finishes (or a fixed 2 seconds if there's no hatch part at all). The
  transition from submerged to surfaced takes effect immediately

As a quirk specific to this model, the `toggle_parts` for the
hatch/conning-tower part may use the **opposite** angle convention from
every other vehicle's own established rule (`angle=0` as the fully-open
pose, with the rotated angle as the closed pose). Adjust the sign of
`max_angle` etc. to match your actual model.

- **Example** (conning tower hatch):
  ```json
  "toggle_parts": [
    { "part": "$conning_tower_hatch", "pivot_x": 0.0, "pivot_y": 1.5, "pivot_z": 0.0, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": 90.0, "trigger": "key", "speed": 6.0 }
  ]
  ```

---

## Underwater engagement (torpedoes)

A submarine's own weapon (`Type = Torpedo`) can be used even while
submerged. For the torpedo's own underwater behavior (target depth,
underwater speed), see `Readme_Weapon_Torpedo.md`.

---

## Notes

- `wheel_parts` (see `Readme_Vehicle_Car.md`), `vtol_rotor_parts` (see
  `Readme_Vehicle_Vtol.md`), and `runways` (see `Readme_Vehicle_Ship.md`)
  are not used
- Like ships, a wake trail is generated while surfaced and underway (see
  `wake_trail_spread_distance` / `wake_trail_duration_ticks` in
  `Readme_Vehicle_Ship.md`)
