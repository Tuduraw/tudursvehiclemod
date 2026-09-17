# Readme_Vehicle_Ship.md - Fields specific to ships (`entity_type: ship`)

In addition to the common fields in `Readme_Vehicle.md`, these fields
only have meaning for a vehicle whose `entity_type` is set to
`"tudursvehiclemod:ship"`. Used for vessels that travel on the water
surface.

---

## Wake trail

### `wake_trail_spread_distance`
- **Format**: float (optional)
- **Description**: Manually specifies how wide the wake trail (bow wave /
  wake) spreads. If omitted, it's auto-detected from the hull shape
  (measured beam width at the waterline).
- **Example**:
  ```json
  "wake_trail_spread_distance": 8.0
  ```

### `wake_trail_duration_ticks`
- **Format**: integer (default: `300`)
- **Description**: How long (ticks) a single point of the wake trail
  stays visible before fading.
- **Example**:
  ```json
  "wake_trail_duration_ticks": 300
  ```

---

## Notes

- `wheel_parts` (see `Readme_Vehicle_Car.md`) and `vtol_rotor_parts` (see
  `Readme_Vehicle_Vtol.md`) are not used
- The submarine-specific diving feature (`dive_max_speed`, etc.) is not
  used. For a submarine, use `entity_type: submarine` instead (see
  `Readme_Vehicle_Submarine.md`)
