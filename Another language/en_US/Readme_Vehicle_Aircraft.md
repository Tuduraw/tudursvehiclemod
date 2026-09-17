# Readme_Vehicle_Aircraft.md - Fields specific to fixed-wing aircraft (`entity_type: aircraft`)

In addition to the common fields in `Readme_Vehicle.md`, these fields
only have meaning for a vehicle whose `entity_type` is set to
`"tudursvehiclemod:aircraft"`.

---

## Flight performance

### `stall_speed`
- **Format**: float (default: `0.3`)
- **Description**: A fraction of the effective top speed. While airborne,
  if actual speed drops below this fraction, the aircraft enters a stall
  (gradually losing altitude). It recovers from the stall once speed
  climbs back up to (this value + 0.1).
- **Example**:
  ```json
  "stall_speed": 0.35
  ```

### `float_capable`
- **Format**: boolean (default: `false`)
- **Description**: When `true`, treated as a seaplane (flying boat),
  able to land on, taxi across, and take off from the water surface. An
  ordinary aircraft (the `false` default) that lands on water becomes
  uncontrollable and gradually starts sinking (since it was never
  intended to touch down on water at all).
- **Example**:
  ```json
  "float_capable": true
  ```

---

## Wing folding (variable-sweep wing)

Used together with `toggle_parts`'s own `trigger: "wing_fold"`. A
dedicated key toggles folding/unfolding (a separate action from the H-key
hatch open/close described in `Readme_Vehicle.md`).

### `wing_sweep` (inside a `toggle_parts` element)
- **Format**: object (optional)
- **Description**: Only set on a part with `trigger: "wing_fold"`. If
  omitted, it behaves as a simple stowed wing ("can only fold while
  stationary, can't move while folding").
  - `variable_sweep_wing` (default `false`): when `true`, becomes a
    variable-sweep wing whose sweep angle can be changed with a dedicated
    key even while airborne (no speed restriction at all)
  - `sweep_wing_speed` (default `0.0`): when `variable_sweep_wing: true`,
    this value is used as the top speed cap instead of `max_speed` while
    folded/folding (a greater sweep angle allowing higher speed matches
    a real variable-sweep wing's own behavior)
- **Example** (a carrier aircraft's own stowed wing, can't fold while
  airborne):
  ```json
  {
    "part": "$wing_l",
    "pivot_x": -1.0, "pivot_y": 0.0, "pivot_z": 0.0,
    "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0,
    "max_angle": 100.0,
    "trigger": "wing_fold",
    "speed": 3.0
  }
  ```
- **Example** (a variable-sweep wing, sweep angle changeable even while
  airborne):
  ```json
  {
    "part": "$wing_l",
    "trigger": "wing_fold",
    "wing_sweep": { "variable_sweep_wing": true, "sweep_wing_speed": 2.4 }
  }
  ```

---

## Landing gear

Used together with `toggle_parts`'s own `trigger: "landing_gear"`
(opens/closes following the retraction state), or
`trigger: "landing_gear_hatch"` (a cover that only opens while the gear
itself is in motion, corresponding to `AddPartLGHatch`). A dedicated
control key toggles deploy/retract. Ground detection and landing aren't
possible while the gear is retracted.

- **Example**:
  ```json
  { "part": "$gear_nose", "pivot_x": 0.0, "pivot_y": 0.0, "pivot_z": 2.0, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": 90.0, "trigger": "landing_gear", "speed": 4.0 },
  { "part": "$gear_hatch_nose", "trigger": "landing_gear_hatch", "max_angle": 90.0, "speed": 8.0 }
  ```

---

## Unmanned (UAV) / autonomous drone

These flags are fields inside the `GroundVehicleParts` group, not
documented in `Readme_Vehicle.md` itself (the naming suggests "for
ground vehicles", but they can actually be used with every type,
including aircraft).

### `is_uav`
- **Format**: boolean (default: `false`)
- **Description**: When `true`, this vehicle is treated as an unmanned
  aerial vehicle (UAV). Its ordinary seat/cockpit meaning is lost, and it
  becomes dedicated to remote operation via a dedicated Station block
  instead. See the "Unmanned (UAV) / Autonomous Drones / Close Air
  Support (CAS)" section of `README.md` for details.
- **Example**:
  ```json
  "is_uav": true
  ```

### `is_target_drone`
- **Format**: boolean (default: `false`)
- **Description**: When `true`, ordinary mounting is disabled, and it's
  treated as dedicated to being a target drone (autonomous AI flight).
  Control from a Drone Center block itself is available for any vehicle
  regardless of this flag.
- **Example**:
  ```json
  "is_target_drone": true
  ```

---

## Use as a Close Air Support (CAS) aircraft or Carrier launch aircraft

`AircraftEntity` is also used as the "support aircraft" or "carrier
aircraft" that gets automatically spawned when a `Type = CAS` or
`Type = CARRIER` weapon is set on another vehicle's own `weapons`. While
flying under these modes, a dedicated autonomous flight-path AI controls
the vehicle instead of ordinary mounting/piloting. See
`Readme_Weapon_Cas.md` for the weapon's own settings.

The carrier aircraft's own vehicle JSON has no CAS/Carrier-specific
settings of its own at all (the launch route, landing conditions, etc.
are all defined entirely on the weapon config file side).

---

## Notes

- A fixed-wing aircraft doesn't use `wheel_parts` (see
  `Readme_Vehicle_Car.md`)
- A carrier's own runway (`runways`) is set on the vehicle JSON of the
  **vehicle doing the launching/recovering**, not the carrier aircraft
  itself. This applies to any vehicle, not just ships. See
  `Readme_Vehicle.md`'s "12. Carrier deck / runway" for details
