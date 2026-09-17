# Readme_Vehicle_Car.md - Fields specific to cars/tanks (`entity_type: car`)

In addition to the common fields in `Readme_Vehicle.md`, these fields
only have meaning for a vehicle whose `entity_type` is set to
`"tudursvehiclemod:car"`. Use this type for any wheeled or tracked ground
vehicle.

---

## Steering/movement characteristics

### `pivot_turn_throttle`
- **Format**: float (default: `0.0`, see `Readme_Vehicle.md`)
- **Description**: At `0` (default), pivot turning (turning in place with
  zero throttle) is possible. Setting a value above `0` prevents turning
  until reaching this fraction of `max_speed`, and automatically
  accelerates up to that speed the moment there's a turn input instead
  (like a tank's own neutral-steer turn).
- **Example**:
  ```json
  "pivot_turn_throttle": 0.15
  ```

### `wheel_rotation_speed`
- **Format**: float (default: `40.0`)
- **Description**: The tire rotation speed coefficient for `wheel_parts`.
  A simple multiplier applied to current speed - no calculation based on
  actual wheel diameter is performed.
- **Example**:
  ```json
  "wheel_rotation_speed": 40.0
  ```

---

## `wheel_parts` (tires)

- **Format**: array of objects (default: empty array)
- **Description**: Corresponds to MC Heli's `AddPartWheel`. Represents
  the combination of spin rotation (rotating as the vehicle moves
  forward) and steering rotation (turning to follow steering input) -
  applied together (spin is applied on the inside, steering on the
  outside).
- **Example** (front 2 wheels steer, rear 2 wheels don't):
  ```json
  "wheel_parts": [
    { "part": "$wheel_fl", "pivot_x": -0.8, "pivot_y": 0.4, "pivot_z": 1.5, "steer_angle": 30.0 },
    { "part": "$wheel_fr", "pivot_x": 0.8, "pivot_y": 0.4, "pivot_z": 1.5, "steer_angle": 30.0 },
    { "part": "$wheel_rl", "pivot_x": -0.8, "pivot_y": 0.4, "pivot_z": -1.5 },
    { "part": "$wheel_rr", "pivot_x": 0.8, "pivot_y": 0.4, "pivot_z": -1.5 }
  ]
  ```

Fields for each element:

### `part`
- **Format**: string (required)
- **Description**: The target OBJ part's name.
- **Example**: `"part": "$wheel_fl"`

### `pivot_x` / `pivot_y` / `pivot_z`
- **Format**: float (required)
- **Description**: The tire's own position coordinate (spin rotation
  happens around this point, on the local X axis).
- **Example**: `"pivot_x": -0.8, "pivot_y": 0.4, "pivot_z": 1.5`

### `steer_angle`
- **Format**: float (default: `0.0`)
- **Description**: The maximum angle (degrees) this tire swings through
  in response to steering input. `0` means it doesn't steer at all
  (e.g. a rear wheel).
- **Example**: `"steer_angle": 30.0`

### `steer_axis_x` / `steer_axis_y` / `steer_axis_z`
- **Format**: float (default `0.0` / `1.0` / `0.0`)
- **Description**: The steering rotation axis. If omitted, the vertical
  axis (kingpin axis).
- **Example**: `"steer_axis_x": 0.0, "steer_axis_y": 1.0, "steer_axis_z": 0.0`

### `steer_pivot_x` / `steer_pivot_y` / `steer_pivot_z`
- **Format**: float (default `0.0` for each)
- **Description**: The steering rotation's own pivot coordinate. If
  omitted, the same as this tire's own `pivot_x/y/z`.
- **Example**: `"steer_pivot_x": -0.8, "steer_pivot_y": 0.4, "steer_pivot_z": 1.5`

---

## `steering_wheel_parts` (steering wheel)

- **Format**: array of objects (default: empty array)
- **Description**: Corresponds to MC Heli's `AddPartSteeringWheel`.
  Rotates the model for the steering wheel itself, visible inside the
  driver's seat, in response to steering input (independent from tire
  spin/steering - purely a single-axis rotation of the wheel itself).
  Each element:
  - `part` (required): the target OBJ part's name
  - `pivot_x` / `pivot_y` / `pivot_z` (required): the rotation pivot
    coordinate
  - `axis_x` / `axis_y` / `axis_z` (default `0.0` / `0.0` / `1.0`): the
    rotation axis
  - `max_angle` (default `130.0`): the rotation angle (degrees) at
    maximum steering
- **Example**:
  ```json
  "steering_wheel_parts": [
    { "part": "$steering_wheel", "pivot_x": 0.4, "pivot_y": 0.9, "pivot_z": 1.0, "max_angle": 130.0 }
  ]
  ```

---

## `crawler_tracks` (tank tracks)

- **Format**: array of objects (default: empty array)
- **Description**: Corresponds to MC Heli's `AddCrawlerTrack`. Represents
  a track by placing a single track-link model repeatedly along a closed
  loop path.
- **Example** (two tracks, left and right):
  ```json
  "crawler_tracks": [
    {
      "part": "$track_link", "flip": false, "link_spacing": 0.5, "x": -1.2,
      "path": [ { "y": 0.0, "z": 2.0 }, { "y": 0.6, "z": 2.0 }, { "y": 0.6, "z": -2.0 }, { "y": 0.0, "z": -2.0 } ]
    },
    {
      "part": "$track_link", "flip": true, "link_spacing": 0.5, "x": 1.2,
      "path": [ { "y": 0.0, "z": 2.0 }, { "y": 0.6, "z": 2.0 }, { "y": 0.6, "z": -2.0 }, { "y": 0.0, "z": -2.0 } ]
    }
  ]
  ```

Fields for each element:

### `part`
- **Format**: string (required)
- **Description**: The track-link's own OBJ part name (the model for a
  single link).
- **Example**: `"part": "$track_link"`

### `flip`
- **Format**: boolean (default: `false`)
- **Description**: Flips the track's own front/back face.
- **Example**: `"flip": true`

### `link_spacing`
- **Format**: float (default: `0.5`)
- **Description**: The spacing between track links.
- **Example**: `"link_spacing": 0.5`

### `x`
- **Format**: float (required)
- **Description**: This track's own left/right position. By convention,
  a negative value means the right side and a positive value means the
  left side - this is used in the differential-steering calculation that
  makes the left and right tracks move in opposite directions (while
  pivot-turning) or at different speeds (while turning normally).
- **Example**: `"x": -1.2`

### `path`
- **Format**: array of `{y, z}` objects (required)
- **Description**: The sequence of Y/Z coordinate points along the closed
  loop path that the track links follow.
- **Example**:
  ```json
  "path": [ { "y": 0.0, "z": 2.0 }, { "y": 0.6, "z": 2.0 }, { "y": 0.6, "z": -2.0 }, { "y": 0.0, "z": -2.0 } ]
  ```

---

## `track_roller_parts` (idler/road wheels)

- **Format**: array of objects (default: empty array)
- **Description**: Corresponds to MC Heli's `AddTrackRoller`. Small
  wheels that roll on top of the track. They spin fully in sync with the
  actual movement speed of the matching `crawler_tracks` entry (matched
  automatically by the sign of the X coordinate) - not spinning at some
  independent, fixed rate.
- **Example**:
  ```json
  "track_roller_parts": [
    { "part": "$roller1_l", "pivot_x": -1.2, "pivot_y": 0.3, "pivot_z": 1.5 },
    { "part": "$roller2_l", "pivot_x": -1.2, "pivot_y": 0.3, "pivot_z": 0.0 }
  ]
  ```

Fields for each element:
- `part` (required): the target OBJ part's name. **Example**:
  `"part": "$roller1_l"`
- `pivot_x` / `pivot_y` / `pivot_z` (required): the position coordinate
  (spins on the local X axis). **Example**:
  `"pivot_x": -1.2, "pivot_y": 0.3, "pivot_z": 1.5`
- `rotations_per_block` (optional): how many rotations occur per block
  of track movement (a value derived from the roller's own
  circumference). If omitted, an automatically-computed cached value
  derived from the model's own shape is used. **Example**:
  `"rotations_per_block": 0.8`

---

## Notes

- `runways` (see `Readme_Vehicle_Ship.md`) and `vtol_rotor_parts` (see
  `Readme_Vehicle_Vtol.md`) are not used
- There's no landing gear or stall concept at all (`stall_speed` is
  ignored)
