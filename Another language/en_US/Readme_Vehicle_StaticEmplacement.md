# Readme_Vehicle_StaticEmplacement.md - Fields specific to static emplacements (`entity_type: static_emplacement`)

Of the common fields in `Readme_Vehicle.md`, every movement-related field
(`max_speed`, `acceleration`, `turn_speed`, `step_height`,
`reverse_throttle`, `pivot_turn_throttle`, `wheel_rotation_speed`,
`throttle_up_down`, `weight_type`, `throttle_switch_hold_ticks`, etc.) is
never used at all. This type corresponds to MC Heli's `Vehicles` category
(fixed gun emplacements, etc.) - it has no ability to self-propel, steer,
or use throttle whatsoever.

---

## Characteristics

- Throttle and steering are disabled entirely. The vehicle itself never
  moves even while occupied
- Still affected by gravity the same as every other type (it falls if the
  surface it's sitting on disappears)
- Mounting via seats (`seats`), turret tracking via `weapon_parts` (turning
  to match the occupant's own view direction), hatch/cover opening via
  `toggle_parts`, and hitbox/health/destruction handling are all shared in
  common with every other vehicle type
- Used to represent structures such as fixed gun emplacements or turrets -
  something that functions as a gun position in place, but never actually
  moves

---

## Fields never used

- `wheel_parts` / `steering_wheel_parts` / `crawler_tracks` /
  `track_roller_parts` (see `Readme_Vehicle_Car.md`)
- `vtol_rotor_parts` (see `Readme_Vehicle_Vtol.md`)
- `runways` (see `Readme_Vehicle_Ship.md`)
- `stall_speed` / `float_capable` / `wing_sweep` (see
  `Readme_Vehicle_Aircraft.md`)
- `dive_max_speed` (see `Readme_Vehicle_Submarine.md`)
