# Readme_Vehicle_Helicopter.md - Fields specific to helicopters (`entity_type: helicopter`)

In addition to the common fields in `Readme_Vehicle.md`, these are fields
and behaviors that only have meaning for a vehicle whose `entity_type` is
set to `"tudursvehiclemod:helicopter"`.

---

## `float_capable`

- **Format**: boolean (default: `false`, see `Readme_Vehicle.md`)
- **Description**: Also applies to helicopters. When `true`, landing on
  water doesn't sink naturally - the vehicle stabilizes floating near the
  waterline (a buoyancy spring). Unlike a fixed-wing aircraft, a
  helicopter normally keeps sinking under gravity the moment it touches
  water, so this setting is required for operation on water.
- **Example**:
  ```json
  "float_capable": true
  ```

---

## Manual mode (manual attitude control)

A flight mode that can be toggled with a dedicated key. In normal mode,
attitude while airborne is determined automatically from the pilot's own
view direction (yaw) and W/S input (pitch tilt), but switching to manual
mode makes mouse movement directly control yaw/pitch, with A/D input
handling roll (continuous rotation with inertia) instead. While on the
ground, behavior stays the same (fixed attitude) regardless of the mode.

There is no dedicated JSON setting to enable/disable this mode at all (it
is toggled purely by the key itself).

---

## Notes

- Helicopters don't use `wheel_parts` (see `Readme_Vehicle_Car.md`) or
  `runways` (see `Readme_Vehicle_Ship.md`)
- Landing gear (`toggle_parts` with `trigger: "landing_gear"`) can be used
  the same way as on a fixed-wing aircraft - see `Readme_Vehicle_Aircraft.md`
  for example entries
