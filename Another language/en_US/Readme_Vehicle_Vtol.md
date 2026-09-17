# Readme_Vehicle_Vtol.md - Fields specific to VTOL aircraft (`entity_type: vtol`)

`entity_type: "tudursvehiclemod:vtol"` is a type that extends the
fixed-wing aircraft type (`Aircraft`) - every field from
`Readme_Vehicle_Aircraft.md` can be used as-is. It's intended for a
vehicle that can switch between helicopter mode (vertical takeoff/landing
and hovering) and fixed-wing mode (ordinary flight) via a dedicated key.

---

## `vtol_rotor_parts`

- **Format**: array of objects (default: empty array)
- **Description**: Corresponds to MC Heli's `AddPartRotor`. Parts such as
  rotor nacelles that tilt in sync with switching between helicopter mode
  and fixed-wing mode (for tiltrotor aircraft).
- **Example** (two tiltrotors, left and right):
  ```json
  "vtol_rotor_parts": [
    { "part": "$rotor_nacelle_l", "pivot_x": -3.0, "pivot_y": 0.5, "pivot_z": 0.0, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0 },
    { "part": "$rotor_nacelle_r", "pivot_x": 3.0, "pivot_y": 0.5, "pivot_z": 0.0, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0 }
  ]
  ```

Fields for each element:

### `part`
- **Format**: string (required)
- **Description**: The target OBJ part's name.
- **Example**: `"part": "$rotor_nacelle_l"`

### `pivot_x` / `pivot_y` / `pivot_z`
- **Format**: float (default `0.0` for each)
- **Description**: The pivot coordinate for the tilting motion.
- **Example**: `"pivot_x": -3.0, "pivot_y": 0.5, "pivot_z": 0.0`

### `axis_x` / `axis_y` / `axis_z`
- **Format**: float (default `1.0` / `0.0` / `0.0`)
- **Description**: The tilt axis vector. Specify this precisely, sign
  included (getting it wrong causes the tilt direction to be reversed).
- **Example**: `"axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0`

The tilt range is fixed at 90 degrees (corresponding to mode-switch
progress from 0 to 1). In fixed-wing mode the part sits at the model's
own original pose (no rotation); in helicopter mode it's rotated 90
degrees.

If you want an always-spinning part (a propeller/blade, etc.) that
rotates dependent on this part, set that part's own `vtol_rotor_parent`
(in `spinning_parts`, see `Readme_Vehicle.md`) to the `part` name used
here in `vtol_rotor_parts`.

- **Example** (a propeller dependent on the nacelle above):
  ```json
  "spinning_parts": [
    {
      "part": "$prop_l", "pivot_x": -3.5, "pivot_y": 0.5, "pivot_z": 0.0,
      "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "speed": 90.0,
      "vtol_rotor_parent": "$rotor_nacelle_l"
    }
  ]
  ```

---

## `vtol_hover_speed_fraction`

- **Format**: float (default: `0.1`)
- **Description**: The top speed for WASD horizontal movement while in
  helicopter mode, given as a fraction of `max_speed` (the same top speed
  used for fixed-wing mode). The default `0.1` is 10%. This is an
  addition specific to this project (not supported by MC Heli) - a
  tuning knob for when horizontal movement in helicopter mode ending up
  at the same speed as fixed-wing mode would feel excessively fast.
- **Example**:
  ```json
  "vtol_hover_speed_fraction": 0.15
  ```

---

## Mode switching

- A dedicated key switches between helicopter mode and fixed-wing mode.
  The switch can only begin while the vehicle's own attitude is close to
  level, and any further switch input is ignored while a switch is
  already in progress
- Fixed-wing stall (`stall_speed`) never happens while in helicopter mode
- Landing gear (`toggle_parts` with `trigger: "landing_gear"`) is shared
  with fixed-wing aircraft (see `Readme_Vehicle_Aircraft.md` for example
  entries)

---

## Notes

- Every field from `Readme_Vehicle_Aircraft.md` (`stall_speed`,
  `float_capable`, `wing_sweep`, `is_uav`, `is_target_drone`, etc.) can
  be used on a VTOL exactly as-is
- `wheel_parts` (see `Readme_Vehicle_Car.md`) is not used
