# Readme_Vehicle.md - Vehicle config files (common fields)

Target file: `data/<namespace>/vehicles/<name>.json` (JSON format)

This document covers the fields common to every vehicle type, regardless
of `entity_type`'s own value. For fields that only have meaning for a
specific type, see the corresponding `Readme_Vehicle_<Type>.md`.

- `Readme_Vehicle_Aircraft.md` — fixed-wing aircraft (`entity_type: aircraft`)
- `Readme_Vehicle_Helicopter.md` — helicopters (`entity_type: helicopter`)
- `Readme_Vehicle_Vtol.md` — VTOL aircraft (`entity_type: vtol`)
- `Readme_Vehicle_Car.md` — cars/tanks (`entity_type: car`)
- `Readme_Vehicle_Ship.md` — ships (`entity_type: ship`)
- `Readme_Vehicle_Submarine.md` — submarines (`entity_type: submarine`)
- `Readme_Vehicle_StaticEmplacement.md` — static emplacements
  (`entity_type: static_emplacement`)

The JSON as a whole uses `snake_case` key names rather than camelCase. A
field noted as `Optional` can be omitted, in which case the listed
default value is used.

Under each field's own heading, an actual example entry is given as
`Example:`.

---

## Contents

1. Basic info
2. Movement/handling
3. Seats
4. Weapons
5. Health/fuel/inventory
6. Appearance/hitbox
7. Continuously-spinning parts (spinning_parts)
8. Toggle parts (toggle_parts)
9. Weapon-linked parts (weapon_parts)
10. Searchlight/nav lights
11. Supply range
12. Spawner item

---

## 1. Basic info

### `entity_type`
- **Format**: string (Identifier)
- **Description**: Specifies this vehicle's own behavior model (the
  actual implementing class). Required. Available values:
  `tudursvehiclemod:aircraft` / `helicopter` / `vtol` / `car` / `ship` /
  `submarine` / `static_emplacement`. See each type's own dedicated
  document for details.
- **Example**:
  ```json
  "entity_type": "tudursvehiclemod:aircraft"
  ```

### `model`
- **Format**: string (Identifier)
- **Description**: The path to the 3D model file for display, in
  Identifier form. Required. OBJ format (triangle, quad, or n-gon faces
  are all fine). Conventionally placed at
  `assets/<namespace>/models/obj/<name>.obj`.
- **Example**:
  ```json
  "model": "tudursvehiclemod:models/obj/f16.obj"
  ```

### `texture`
- **Format**: string (Identifier)
- **Description**: The path to the texture image applied to the model
  above. Required. Conventionally placed at
  `assets/<namespace>/textures/vehicle/<name>.png`.
- **Example**:
  ```json
  "texture": "tudursvehiclemod:textures/vehicle/f16.png"
  ```

### `scale`
- **Format**: float (default: `1.0`)
- **Description**: The model's own display scale.
- **Example**:
  ```json
  "scale": 1.2
  ```

### `width` / `height`
- **Format**: float
- **Description**: Reference values representing this vehicle's own
  visual size. Required. Not normally used for the actual hitbox size
  (see `force_bounding_box` below).
- **Example**:
  ```json
  "width": 2.2,
  "height": 1.4
  ```

---

## 2. Movement/handling

### `max_speed`
- **Format**: float (default: `1.0`)
- **Description**: This vehicle's own top speed. Units are roughly
  blocks/tick.
- **Example**:
  ```json
  "max_speed": 1.8
  ```

### `acceleration`
- **Format**: float (default: `0.05`)
- **Description**: How closely acceleration follows throttle.
- **Example**:
  ```json
  "acceleration": 0.05
  ```

### `turn_speed`
- **Format**: float (default: `3.0`)
- **Description**: Turning performance. A larger value turns more
  tightly. Corresponds to MC Heli's own `MobilityYaw`. A negative value
  is automatically normalized to its absolute value (to prevent turn
  input from being reversed).
- **Example**:
  ```json
  "turn_speed": 3.5
  ```

### `step_height`
- **Format**: float (default: `1.0`)
- **Description**: The height of a step/ledge that can be automatically
  climbed.
- **Example**:
  ```json
  "step_height": 1.0
  ```

### `gravity`
- **Format**: float (default: `-0.04`)
- **Description**: The gravitational acceleration applied to this
  vehicle itself.
- **Example**:
  ```json
  "gravity": -0.04
  ```

### `on_ground_pitch`
- **Format**: float (default: `0.0`)
- **Description**: The pitch angle (degrees) it eases toward while on
  the ground.
- **Example**:
  ```json
  "on_ground_pitch": 0.0
  ```

### `reverse_throttle`
- **Format**: float (default: `0.0`)
- **Description**: The lowest throttle value at which reverse is
  possible (a negative value). For example, `-0.2` allows reversing up
  to 20%. `0` means reverse is not possible at all. A positive value is
  automatically normalized to negative.
- **Example**:
  ```json
  "reverse_throttle": -0.2
  ```

### `engine_sound_volume`
- **Format**: float (default: `1.0` or `3.0` depending on entity type -
  see below)
- **Description**: A volume multiplier for `engine_sound`. The same idea
  as `SoundVolume` in an MC Heli weapon config - `1.0` is ordinary
  maximum volume, and going above it extends the audible distance
  instead (an addition specific to this project; both the full-volume
  distance and the distance at which it fades to silence scale with
  this value).
- **On the default**: when not explicitly set, the default depends on
  `entity_type`.
  - `aircraft` / `helicopter` / `vtol` (flies at altitude), and
    `ship` / `submarine` (sheer physical scale): `3.0`
  - `car` / `static_emplacement`, and any `entity_type` an addon mod
    adds: `1.0`
- **Example**:
  ```json
  "engine_sound_volume": 3.0
  ```

### `pivot_turn_throttle`
- **Format**: float (default: `0.0`)
- **Description**: `Car` type only. The minimum throttle required to
  turn (a fraction of `max_speed`, 0-1). At `0` (default), pivot turning
  (turning in place) is possible even at zero throttle. See
  `Readme_Vehicle_Car.md` for details.
- **Example**:
  ```json
  "pivot_turn_throttle": 0.2
  ```

### `wheel_rotation_speed`
- **Format**: float (default: `40.0`)
- **Description**: `Car` type only. The tire rotation speed coefficient.
  See `Readme_Vehicle_Car.md` for details.
- **Example**:
  ```json
  "wheel_rotation_speed": 40.0
  ```

### `throttle_up_down`
- **Format**: float (optional)
- **Description**: A multiplier on how fast throttle changes in response
  to W/S input. `1.0` is each vehicle type's own default as-is, `2.0` is
  double speed, `0.5` is half speed. If omitted, each type's own default
  step value is used.
- **Example**:
  ```json
  "throttle_up_down": 1.5
  ```

### `weight_type`
- **Format**: string (`"tank"` / `"car"` / `"unknown"`, default:
  `"unknown"`)
- **Description**: The vehicle's own weight category. Same as MC Heli's
  own `WeightType`:
  - `tank`: takes no damage from colliding with a mob. Has high
    block-destruction power
  - `car`: takes damage itself from colliding with a mob. Has low
    block-destruction power
  - `unknown` (default): neither behavior occurs
- **Example**:
  ```json
  "weight_type": "tank"
  ```

### `throttle_switch_hold_ticks`
- **Format**: integer (optional)
- **Description**: How long (ticks) throttle pauses at 0% when crossing
  from positive to negative or vice versa. If omitted, each vehicle
  type's own default (40 ticks).
- **Example**:
  ```json
  "throttle_switch_hold_ticks": 40
  ```

### `dive_max_speed`
- **Format**: float (optional)
- **Description**: `Submarine` type only. Top speed while submerged
  (`max_speed` is only used while surfaced). See
  `Readme_Vehicle_Submarine.md` for details.
- **Example**:
  ```json
  "dive_max_speed": 0.6
  ```

---

## 3. Seats (`seats`)

- **Format**: array of objects (default: empty array)
- **Description**: The list of mountable seats. Index 0 of the array is
  always treated as the driver's seat (pilot seat).
- **Example** (1 pilot seat + 1 gun position):
  ```json
  "seats": [
    { "name": "pilot", "offset_x": 0.0, "offset_y": 0.5, "offset_z": 0.5, "driver": true },
    { "name": "gunner", "offset_x": 0.0, "offset_y": 0.8, "offset_z": -1.5 }
  ]
  ```

Fields for each seat object:

### `name`
- **Format**: string
- **Description**: The seat's own identifying name.
- **Example**: `"name": "pilot"`

### `offset_x` / `offset_y` / `offset_z`
- **Format**: float
- **Description**: The seat's own relative coordinate from the vehicle's
  own reference position. Required.
- **Example**: `"offset_x": 0.0, "offset_y": 0.5, "offset_z": 0.5`

### About the dismount position
Regardless of this seat's own `offset_y`, **the dismount position is
always the vehicle's own current Y coordinate + 1.0 block**. This
prevents an occupant in a low seat from ending up embedded in the
vehicle or the ground, and an occupant in a high seat from taking a
needless fall. The X/Z coordinates still come from this seat's own
`offset_x`/`offset_z` (rotated to match the vehicle's own heading), as
before.

This correction is applied uniformly whether or not the vehicle is
airborne (it's a relative addition based on the vehicle's own current
position, so dismounting from a vehicle flying at, say, 100m altitude
simply places the passenger at 101m, which falls just like an ordinary
fall and causes no practical issue).

### `dismount_x` / `dismount_y` / `dismount_z`
- **Format**: float (optional, unset by default)
- **Description**: Overrides this seat's own dismount position
  individually. Uses the same coordinate space as `offset_x/y/z` (vehicle-
  relative, rotated to match the vehicle's own heading). Each axis can be
  set independently; any axis left unset falls back to its own default
  behavior (X/Z from `offset_x/z`, Y from the fixed vehicle-relative +1.0
  height described above).
- **Example**: `"dismount_x": 0.0, "dismount_y": 1.0, "dismount_z": 2.5`

### `driver`
- **Format**: boolean (default: `false`)
- **Description**: Whether this seat can pilot the vehicle.
- **Example**: `"driver": true`

### `enable_parachuting`
- **Format**: boolean (default: `false`)
- **Description**: Whether this seat's own occupant (excluding the pilot
  seat) can parachute-jump with the parachute-jump key binding. See
  "13. Parachuting" for details.
- **Example**: `"enable_parachuting": true`

### `camera_positions`
- **Format**: array of objects (default: empty array)
- **Description**: The list of view positions usable from this seat.
  With several set, the H key cycles between views (corresponds to MC
  Heli's own `CameraPosition`). Each element:
  - `x` / `y` / `z` (required): the view coordinate
  - `force_camera` (default `false`): always uses this camera view
    (never switches to first-person)
  - `fixed_yaw` / `fixed_pitch` (optional): a fixed view angle
- **Example**:
  ```json
  "camera_positions": [
    { "x": 0.0, "y": 0.6, "z": 0.2 },
    { "x": 0.0, "y": 3.0, "z": -6.0, "force_camera": true }
  ]
  ```

---

## 4. Weapons (`weapons`)

- **Format**: array of objects (default: empty array)
- **Description**: The list of weapon mounts equipped on this vehicle.
  The actual damage/projectile speed/etc. values are defined on the
  `.txt` file referenced by `weapon_name` (see `Readme_Weapon.md`) -
  only the mount position and aim range are set here.
- **Example**:
  ```json
  "weapons": [
    {
      "seat_index": 0,
      "offsets": [ { "x": 0.6, "y": 0.2, "z": 3.0 } ],
      "projectile_item": "minecraft:iron_nugget",
      "weapon_name": "m61_vulcan"
    }
  ]
  ```

### `seat_index`
- **Format**: integer
- **Description**: The index of the seat that can use this weapon (an
  index into the `seats` array; 0 is the pilot seat).
- **Example**: `"seat_index": 0`

### `offsets`
- **Format**: array of objects (required, at least 1)
- **Description**: The list of firing positions (setting several lets
  you represent a multi-barrel gun, etc.). Each element:
  - `x` / `y` / `z` (default `0.0`): the firing position coordinate,
    relative to the vehicle
  - `mount_yaw` / `mount_pitch` (default `0.0`): the fixed direction
    (when `aim_range` isn't set)
  - `linked_part` (optional, string): links this firing position to a
    specific `weapon_parts` name (used for a multi-barrel gun, to
    distinguish which part corresponds to which muzzle)
- **Example** (a twin mount, left and right):
  ```json
  "offsets": [
    { "x": -0.8, "y": 0.3, "z": 2.5, "linked_part": "$gun_left" },
    { "x": 0.8, "y": 0.3, "z": 2.5, "linked_part": "$gun_right" }
  ]
  ```

### `aim_range`
- **Format**: object (optional)
- **Description**: When set, this weapon follows the occupant's own view
  direction instead of a fixed direction (clamped to the `min`/`max`
  range). Corresponds to the trailing parameters of MC Heli's own
  `AddWeapon`.
  - `default_yaw` (default `0.0`)
  - `min_yaw`/`max_yaw` (default `-180.0`/`180.0`)
  - `min_pitch`/`max_pitch` (default `-90.0`/`90.0`)
- **Example**:
  ```json
  "aim_range": { "default_yaw": 0.0, "min_yaw": -170.0, "max_yaw": 170.0, "min_pitch": -10.0, "max_pitch": 60.0 }
  ```

### `projectile_item`
- **Format**: string (Identifier)
- **Description**: The item id used for the projectile's own item
  display (a fallback when no model is set).
- **Example**: `"projectile_item": "minecraft:iron_nugget"`

### `weapon_name`
- **Format**: string
- **Description**: The name (without extension) of the corresponding
  weapon config file
  (`assets/<namespace>/weapons/<weapon_name>.txt`). Required. Every
  actual performance value - damage, projectile speed, weapon type, etc.
  - is defined entirely on that file's own side (see
  `Readme_Weapon.md`).
- **Example**: `"weapon_name": "m61_vulcan"`

### `pilot_usable`
- **Format**: boolean (default: `false`)
- **Description**: When `true`, if the `seat_index` seat is unoccupied,
  the pilot seat's own occupant (seat 0) can use this weapon instead.
  Only has meaning when `seat_index` is anything other than seat 0.
- **Example**: `"pilot_usable": true`

### `turret_rotation_speed`
- **Format**: float (optional, degrees/second)
- **Description**: A turn-rate cap specific to this weapon mount. If
  omitted, it follows the occupant's own view instantly. If set, any
  tracking on `weapon_parts` (see chapter 9) is limited to this rate, and
  this weapon can't fire until it's finished turning.
- **Example**:
  ```json
  "turret_rotation_speed": 25.0
  ```

---

## 5. Health/fuel/inventory

### `max_health`
- **Format**: float (default: `100.0`)
- **Description**: Max HP.
- **Example**: `"max_health": 150.0`

### `armor_damage_factor`
- **Format**: float (default: `1.0`)
- **Description**: A multiplier on damage taken (an armor factor).
- **Example**: `"armor_damage_factor": 0.5`

### `armor_min_damage`
- **Format**: float (default: `0.0`)
- **Description**: If damage after the armor factor is applied is below
  this value, that damage is fully negated.
- **Example**: `"armor_min_damage": 2.0`

### `armor_max_damage`
- **Format**: float (default: effectively unlimited)
- **Description**: If damage after the armor factor is applied exceeds
  this value, it's clamped down to this value.
- **Example**: `"armor_max_damage": 40.0`

### `damage_factor`
- **Format**: float (default: `1.0`)
- **Description**: A multiplier on damage taken by the riding player.
- **Example**: `"damage_factor": 0.3`

### `max_fuel`
- **Format**: float (default: `600.0`)
- **Description**: Maximum fuel amount.
- **Example**: `"max_fuel": 800.0`

### `fuel_consumption`
- **Format**: float (default: `0.5`)
- **Description**: Fuel consumption per second (varies with throttle).
  Running out of fuel forces throttle to 0 (piloting itself remains
  possible). **A negative value means this vehicle uses no fuel at
  all** - fuel never depletes, throttle is never restricted for running
  out, and the fuel gauge's low-fuel warning never fires; the vehicle
  always behaves as if fully fuelled (regardless of its own actual
  internal fuel value or `max_fuel` setting). Intended for a vehicle
  where the concept of fuel doesn't fit at all, such as a human-pedalled
  bicycle.
- **Example**: `"fuel_consumption": 0.5`
- **Example (no fuel needed)**: `"fuel_consumption": -1.0`

### `inventory_size`
- **Format**: integer (default: `0`)
- **Description**: How many slots this vehicle's own persistent
  inventory has. `0` means no inventory at all.
- **Example**: `"inventory_size": 9`

### `ammo_parts`
- **Format**: array of objects (default: empty array)
- **Description**: Corresponds to MC Heli's own `AddPartWeaponMissile`.
  Externally-visible projectile parts (missiles, bombs, etc.) that
  become hidden once a specific weapon's own remaining ammo drops to a
  certain level or below. Each element:
  - `part` (required): the target OBJ part's name
  - `weapon_name` (required): the target weapon's own `weapon_name`
  - `slot_index` (default `0`): hidden once remaining ammo drops to this
    value or below (setting several with 0, 1, 2, ... lets you produce
    an effect where they disappear one at a time, in order)
  - `display_penalty` (optional): while this part is in its displayed
    state (as long as remaining ammo is above `slot_index`), reduces the
    vehicle's own speed and maneuverability respectively. Specified as a
    string of the form `"speed reduction(%), maneuverability reduction(%)"`
    (for example, `"10, 5"` for a 10% speed reduction and 5% maneuverability
    reduction). If omitted, no effect. With several parts configured, the
    values of every currently-displayed part are added together (not
    multiplied) - since each part can have its own
    individual value, different effects can apply depending on weapon
    type or mount position
- **Example** (2 loaded missiles disappear one at a time as ammo
  decreases, each affecting speed/maneuverability while still loaded):
  ```json
  "ammo_parts": [
    { "part": "$missile_1", "weapon_name": "agm65", "slot_index": 1, "display_penalty": "5, 3" },
    { "part": "$missile_2", "weapon_name": "agm65", "slot_index": 0, "display_penalty": "5, 3" }
  ]
  ```

### `submerged_damage_height`
- **Format**: float (default: `0.0`)
- **Description**: Submerging up to this many blocks below the water
  surface takes no damage per tick. `0` (default) means damage occurs
  the instant any part is submerged at all.
- **Example**: `"submerged_damage_height": 1.5`

### `fuel_supply_range` / `ammo_supply_range` / `health_supply_range`
- **Format**: float (default `0.0` for each)
- **Description**: MC Heli's own `FuelSupplyRange`/`AmmoSupplyRange`, and
  a health-value equivalent added specifically by this project.
  Continuously supplies fuel/ammo/health to **other** vehicles within
  this radius (blocks). The supplying vehicle doesn't consume or lose
  any itself. Never supplies to itself. `0` (default) means no supply
  feature at all.
  Only stationary vehicles are supplied. Ammo refills the magazine first,
  then the reserve up to the weapon's `MaxAmmo` (total capacity, magazine
  included).
- **Example**:
  ```json
  "fuel_supply_range": 20.0,
  "ammo_supply_range": 20.0,
  "health_supply_range": 20.0
  ```

---

## 6. Appearance/hitbox

### `passenger_display`
- **Format**: object (optional)
- **Description**: Settings for how passengers look. The following fields go
  **inside this object**.
  - `hide_entity` (default `false`): when `true`, completely hides the riding
    player's own model (the feature itself is still fully functional - only
    the visible appearance disappears).
  - `entity_width` / `entity_height` (default `1.0` for each): the display
    scale of the riding player's own model.
- **Example**:
  ```json
  "passenger_display": { "hide_entity": false, "entity_width": 0.9, "entity_height": 0.9 }
  ```
- **Note**: Earlier documentation and the MC Heli conversion tool wrote these
  fields directly at the top level of the vehicle JSON. For compatibility,
  top-level `hide_entity` / `entity_width` / `entity_height` are moved into
  `passenger_display` automatically on load (if both exist, the value inside
  `passenger_display` wins). Write them inside `passenger_display` for new
  vehicles.

### `force_bounding_box`
- **Format**: boolean (default: `false`)
- **Description**: When `false` (default), the hitbox uses this mod's
  own standard fixed size. When `true`, this file's own `width`/`height`
  are used as the actual hitbox size instead.
- **Example**: `"force_bounding_box": true`

### `float_capable`
- **Format**: boolean (default: `false`)
- **Description**: `Aircraft` (and `Vtol`)/`Helicopter` types only.
  Whether landing on/floating on water is possible. See
  `Readme_Vehicle_Aircraft.md` / `Readme_Vehicle_Helicopter.md` for
  details.
- **Example**: `"float_capable": true`

### `stall_speed`
- **Format**: float (default: `0.3`)
- **Description**: `Aircraft` type only. The speed fraction at which a
  stall occurs. See `Readme_Vehicle_Aircraft.md` for details.
- **Example**: `"stall_speed": 0.3`

### `hud`
- **Format**: string (optional)
- **Description**: The HUD script name this vehicle uses
  (`assets/<namespace>/hud/<name>.txt`, specified without extension). If
  omitted, no HUD is shown at all.
- **Example**: `"hud": "f16_hud"`

### `engine_sound`
- **Format**: string (optional)
- **Description**: The sound name played as the engine sound (matched
  against `assets/<namespace>/sounds/<name>.ogg` in any namespace).
- **Example**: `"engine_sound": "jet_engine"`

---

## 7. Continuously-spinning parts (`spinning_parts`)

- **Format**: array of objects (default: empty array)
- **Description**: Continuously spins a named group inside the OBJ model
  (an `o`/`g` line) at a constant speed, around its own pivot/axis
  (propellers, rotors, etc.).
- **Example** (a main rotor):
  ```json
  "spinning_parts": [
    { "part": "$main_rotor", "pivot_x": 0.0, "pivot_y": 1.5, "pivot_z": 0.0, "axis_x": 0.0, "axis_y": 1.0, "axis_z": 0.0, "speed": 60.0 }
  ]
  ```

Fields for each element:

### `part`
- **Format**: string (required)
- **Description**: The target OBJ part's name.
- **Example**: `"part": "$main_rotor"`

### `pivot_x` / `pivot_y` / `pivot_z`
- **Format**: float (default `0.0` for each)
- **Description**: The rotation pivot coordinate.
- **Example**: `"pivot_x": 0.0, "pivot_y": 1.5, "pivot_z": 0.0`

### `axis_x` / `axis_y` / `axis_z`
- **Format**: float (default `0.0` / `1.0` / `0.0`)
- **Description**: The rotation axis vector.
- **Example**: `"axis_x": 0.0, "axis_y": 1.0, "axis_z": 0.0`

### `speed`
- **Format**: float (default: `15.0`)
- **Description**: The rotation angle (degrees) per tick.
- **Example**: `"speed": 60.0`

### `vtol_rotor_parent`
- **Format**: string (optional)
- **Description**: `Vtol` type only. Specifying the rotor nacelle this
  blade depends on (the `part` name from `vtol_rotor_parts`) makes it
  also follow that nacelle's own tilting, on top of always spinning. See
  `Readme_Vehicle_Vtol.md` for details.
- **Example**: `"vtol_rotor_parent": "$vtol_rotor0"`

---

## 8. Toggle parts (`toggle_parts`)

- **Format**: array of objects (default: empty array)
- **Description**: Eases a named group inside the OBJ model between two
  states (closed=0/open=1) (hatches, canopies, landing gear covers,
  etc.).
- **Example** (a manually-opened canopy):
  ```json
  "toggle_parts": [
    { "part": "$canopy", "pivot_x": 0.0, "pivot_y": 1.0, "pivot_z": -0.5, "mode": "rotate", "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": 80.0, "trigger": "key", "speed": 6.0 }
  ]
  ```

Fields for each element:

### `part`
- **Format**: string (required)
- **Description**: The target OBJ part's name.
- **Example**: `"part": "$canopy"`

### `pivot_x` / `pivot_y` / `pivot_z`
- **Format**: float (default `0.0` for each)
- **Description**: The reference coordinate for rotation/sliding.
- **Example**: `"pivot_x": 0.0, "pivot_y": 1.0, "pivot_z": -0.5`

### `mode`
- **Format**: string (`"rotate"` / `"slide"`, default: `"rotate"`)
- **Description**: `rotate` uses `axis_x/y/z` + `max_angle` for rotation;
  `slide` uses `offset_x/y/z` for a sliding motion.
- **Example**: `"mode": "slide"`

### `axis_x` / `axis_y` / `axis_z`
- **Format**: float (default `0.0` / `1.0` / `0.0`)
- **Description**: The rotation axis for `mode: rotate`.
- **Example**: `"axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0`

### `max_angle`
- **Format**: float (default: `90.0`)
- **Description**: For `mode: rotate`, the rotation angle (degrees) at
  the fully-open state.
- **Example**: `"max_angle": 80.0`

### `offset_x` / `offset_y` / `offset_z`
- **Format**: float (default `0.0` for each)
- **Description**: For `mode: slide`, how far it moves at the
  fully-open state.
- **Example** (a sliding elevator-deck hatch that descends 8 blocks):
  `"offset_y": -8.0`

### `trigger`
- **Format**: string (default: `"key"`)
- **Description**: What determines this part's own open/close state:
  - `key`: manual open/close via the H key. If the part's own name
    starts with `$hatch`, it moves at 2x the configured `speed`; if it
    starts with `$canopy`, at 0.1x speed (a correction based on this
    project's own naming convention)
  - `landing_gear`: follows the landing gear's own deploy/retract state
    (`Aircraft`/`Vtol` only)
  - `landing_gear_hatch`: opens only while the landing gear is in motion
    (corresponds to `AddPartLGHatch`)
  - `weapon_bay`: opens only while the weapon specified in `weapon_name`
    is selected
  - `wing_fold`: opens/closes with the dedicated wing-fold key
    (`Aircraft` only, see `wing_sweep`)
  - `light_hatch`: opens only while the searchlight is lit
- **Example**: `"trigger": "landing_gear"`

### `speed`
- **Format**: float (default: `6.0`)
- **Description**: How fast open/close progress changes per tick.
- **Example**: `"speed": 6.0`

### `weapon_name`
- **Format**: string (optional)
- **Description**: Only has meaning for `trigger: weapon_bay`. The
  target weapon's own `weapon_name`.
- **Example**:
  ```json
  { "part": "$weapon_bay_door", "trigger": "weapon_bay", "weapon_name": "agm65" }
  ```

### `wing_sweep`
- **Format**: object (optional)
- **Description**: Only has meaning for `trigger: wing_fold`. See
  `Readme_Vehicle_Aircraft.md` for details.
- **Example**:
  ```json
  { "part": "$wing_l", "trigger": "wing_fold", "wing_sweep": { "variable_sweep_wing": true, "sweep_wing_speed": 2.5 } }
  ```

At spawn time, a hatch (including `$hatch`-family parts and any part
with no `trigger` set) spawns closed, and keeps whatever state it was in
before mounting once mounted. A canopy (`$canopy`-family parts) spawns
open, and automatically retracts once mounted. A manual toggle (the H
key) brings both into the same state together.

### Runway hatch linkage (the hatch fields on `runways`)

A runway (`runways`, see "12. Carrier deck / runway") has its own
independent hatch-linked fields (`hatch_gated` / `hatch_offset_x/y/z` /
`hatch_move_speed`) that don't depend on any particular `toggle_parts`
name. If you want it to sync with a visible hatch, match the numbers up
yourself on the addon pack side.

---

## 9. Weapon-linked parts (`weapon_parts`)

- **Format**: array of objects (default: empty array)
- **Description**: Rotates a named group inside the OBJ model to track
  the corresponding weapon's own aim direction (the occupant's own view)
  (turrets, gun positions, etc.).
- **Example** (a turret that tracks seat 1's own view):
  ```json
  "weapon_parts": [
    { "part": "$turret", "seat_index": 1, "pivot_x": 0.0, "pivot_y": 1.0, "pivot_z": 0.0, "weapon_name": "cannon" }
  ]
  ```

Fields for each element:

### `part`
- **Format**: string (required)
- **Description**: The target OBJ part's name.
- **Example**: `"part": "$turret"`

### `seat_index`
- **Format**: integer (default: `-1`)
- **Description**: The index of the seat this part tracks.
- **Example**: `"seat_index": 1`

### `hide_for_gunner`
- **Format**: boolean (default: `false`)
- **Description**: When `true`, hides this part from that seat's own
  occupant's own view specifically.
- **Example**: `"hide_for_gunner": true`

### `yaw_follow` / `pitch_follow`
- **Format**: boolean (default `true` for each)
- **Description**: Whether yaw tracking and pitch tracking are each
  enabled.
- **Example**: `"yaw_follow": true, "pitch_follow": false`

### `pivot_x` / `pivot_y` / `pivot_z`
- **Format**: float (default `0.0` for each)
- **Description**: The rotation pivot coordinate.
- **Example**: `"pivot_x": 0.0, "pivot_y": 1.0, "pivot_z": 0.0`

### `recoil_distance`
- **Format**: float (default: `0.0`)
- **Description**: On firing, how far this part itself pulls back along
  its own local negative Z axis (a recoil effect).
- **Example**: `"recoil_distance": 0.15`

### `pilot_fallback`
- **Format**: boolean (default: `false`)
- **Description**: When `true`, if the `seat_index` seat is unoccupied,
  it tracks the pilot seat's own view instead.
- **Example**: `"pilot_fallback": true`

### `child_info`
- **Format**: object (optional)
- **Description**: Set this when representing `AddPartWeaponChild` (a
  child part that unconditionally inherits its parent part's own
  rotation).
  - `parent_yaw_follow` / `parent_pitch_follow` (default `false` for
    each)
  - `parent_pivot_x` / `parent_pivot_y` / `parent_pivot_z` (default
    `0.0` for each): the parent part's own pivot
- **Example**:
  ```json
  "child_info": { "parent_yaw_follow": true, "parent_pitch_follow": true, "parent_pivot_x": 0.0, "parent_pivot_y": 1.0, "parent_pivot_z": 0.0 }
  ```

### `aim_range`
- **Format**: object (optional, the same format as `weapons`'s own
  `aim_range`)
- **Description**: The aim range for the weapon this part is linked to.
  When set, the part's own visible range of motion also matches the
  actual firing-direction clamp.
- **Example**:
  ```json
  "aim_range": { "min_yaw": -170.0, "max_yaw": 170.0, "min_pitch": -10.0, "max_pitch": 60.0 }
  ```

### `weapon_name`
- **Format**: string (optional)
- **Description**: Which specific weapon this part is linked to (since
  `seat_index` alone can't distinguish between multiple weapons on the
  same seat).
- **Example**: `"weapon_name": "cannon"`

### `spins_while_firing`
- **Format**: boolean (default: `false`)
- **Description**: When `true` (corresponds to `AddPartRotWeapon`), this
  part itself continuously rotates around its own local Z axis (forward
  along the barrel) while the corresponding weapon is firing (a Gatling
  gun effect). Speed follows the corresponding weapon's own
  `RotationSpeed` setting.
- **Example**: `"spins_while_firing": true`

> **Note**: Setting the corresponding weapon's own
> `turret_rotation_speed` (see chapter 4) caps the tracking speed of the
> parts in this chapter too.

---

## 10. Searchlight/nav lights

### `search_light_parts`
- **Format**: array of objects (default: empty array)
- **Description**: A cone-shaped searchlight, corresponding to MC Heli's
  own `AddSearchLight` / `AddFixedSearchLight` / `AddSteeringSearchLight`.
  Each element:
  - `part` (required): the target OBJ part's name
  - `pivot_x`/`pivot_y`/`pivot_z` (required): the light source position
  - `start_color_argb`/`end_color_argb` (required, integer ARGB): the
    color at the source end and the far end
  - `length`/`end_radius` (required): the light's own length and the
    far-end radius
  - `yaw`/`pitch` (default `0.0` for each): the reference direction
  - `steer_angle` (default `0.0`): the maximum swing angle for
    `follow_mode: STEERING`
  - `follow_mode` (required, `PILOT_VIEW`/`FIXED`/`STEERING`): how the
    direction is determined
    - `PILOT_VIEW`: follows the view of whichever occupant is currently
      operating it (corresponds to `AddSearchLight`)
    - `FIXED`: fixed relative to the vehicle (corresponds to
      `AddFixedSearchLight`)
    - `STEERING`: follows the steering angle (corresponds to
      `AddSteeringSearchLight`)
- **Example**:
  ```json
  "search_light_parts": [
    {
      "part": "$search_light",
      "pivot_x": 0.0, "pivot_y": 0.5, "pivot_z": 1.0,
      "start_color_argb": -1, "end_color_argb": 0,
      "length": 30.0, "end_radius": 6.0,
      "follow_mode": "PILOT_VIEW"
    }
  ]
  ```

### `nav_light_parts`
- **Format**: array of objects (default: empty array)
- **Description**: An addition specific to this project. A simple
  fixed-point light source (for navigation lights) with no cone-shaped
  spread or steering at all. Lit at all times as long as the vehicle
  exists (unlike a searchlight, there's no on/off toggle). Each element:
  - `part` (required): the target OBJ part's name
  - `pivot_x`/`pivot_y`/`pivot_z` (required): the light source position
  - `color_argb` (required, integer ARGB): the light's own color. Since
    the alpha value represents brightness, setting it to `0` lets you
    keep the part present in the model without it actually being lit
- **Example** (a red nav light at the left wingtip):
  ```json
  "nav_light_parts": [
    { "part": "$nav_light_l", "pivot_x": -5.0, "pivot_y": 0.0, "pivot_z": 0.0, "color_argb": -65536 }
  ]
  ```

---

## 11. Spawner item (`spawn_item`)

- **Format**: object (optional)
- **Description**: Display/categorization settings for the tiered
  spawner item.
- **Example**:
  ```json
  "spawn_item": { "display_name": "F-16 Fighting Falcon", "tier": 3 }
  ```

### `display_name`
- **Format**: string (optional)
- **Description**: The display name. If omitted, generated automatically
  from the vehicle's own id.
- **Example**: `"display_name": "F-16 Fighting Falcon"`

### `tier`
- **Format**: integer (default: `1`, range 1-5)
- **Description**: The tier at which the spawner item for this vehicle
  becomes available.
- **Example**: `"tier": 3`

---

## 12. Carrier deck / runway (`runways`)

**Can be set on any vehicle, regardless of `entity_type`.** This isn't a
feature limited to ships - the implementation has no dependency on any
specific vehicle type at all. In the sense of "collision that lets
something stand on a flat surface", it's usable by any vehicle - a car,
an aircraft, a helicopter, a static emplacement, anything. For example,
giving a fictional "flying aircraft carrier" a runway is achievable with
this field alone.

**A runway (`runways`) and the carrier-aircraft-launching weapon
(`Type = CARRIER` under `weapons`, described below) aren't required by
each other - each works fine on its own.** You can define a runway with
no weapon at all, purely as a deck (a footing that other vehicles or
players can stand/land on), or conversely set a `Type = CARRIER` weapon
with no runway defined at all (assuming the launched aircraft is
recovered some other way, e.g. `CarrierLandingToAmmoRadius` /
`CarrierRecoveryPoint`). Combining both, so a launched carrier aircraft
actually lands on this runway and gets recovered there, is of course
also possible - that combination is the originally-intended basic form
of a carrier (see the end of this section for details).

- **Format**: `runway` (a single object, legacy) or `runways` (an array
  of objects, supports multiple decks)
- **Description**: Defines a runway anywhere on this vehicle's own body,
  for other vehicles to take off from/land on/park on. Set directly on
  this vehicle's own JSON. Under the hood, an invisible set of support
  tiles (`CarrierRunwayPlatformEntity`) is laid across the whole area,
  and another vehicle's own landing gear, stall detection, and collision
  are all treated as if actual blocks were present there. If you want
  several independent runways - multiple decks, an elevator, etc. - use
  the `runways` array (if both `runway` and `runways` are set at once,
  both take effect).

- **Example** (a single deck):
  ```json
  "runways": [
    { "width": 30.0, "center_x": 0.0, "height_y": 12.0, "start_z": -105.0, "end_z": 90.0 }
  ]
  ```

Fields for each element:

### `width`
- **Format**: float (required)
- **Description**: The runway's own width (blocks).
- **Example**: `"width": 30.0`

### `center_x`
- **Format**: float (default: `0.0`)
- **Description**: The left/right offset from the vehicle's own reference
  point to the runway's own center.
- **Example**: `"center_x": 0.0`

### `height_y`
- **Format**: float (default: `0.0`)
- **Description**: Height above the vehicle's own reference position. The
  runway surface sits at this height.
- **Example**: `"height_y": 12.0`

### `start_z` / `end_z`
- **Format**: float (required)
- **Description**: The runway's own front-to-back extent (order doesn't
  matter).
- **Example**: `"start_z": -105.0, "end_z": 90.0`

All coordinates are relative to this vehicle's own entity position, and
rotate together with the vehicle's own current heading (yaw). The actual
tile size is computed automatically from the server setting
`carrierRunwayExpectedWidth`.

### Hatch-linked runway (optional fields)

The following optional fields can be added without depending on any
particular `toggle_parts` name at all (if you want it to sync with a
visible hatch, match the numbers up yourself on the addon pack side).

- `hatch_gated` (default `false`): when `true`, this runway (and its own
  tiles) only exists while a hatch is open. A simple on/off toggle
  intended for a rotating-type hatch (a landing craft's own bow ramp,
  say) - it doesn't account for any partial-open state in between
  - **Example** (a landing craft's own bow ramp, only present while the
    hatch is fully open):
    ```json
    { "width": 3.0, "center_x": 0.0, "height_y": -0.5, "start_z": 4.0, "end_z": 10.0, "hatch_gated": true }
    ```
- `hatch_offset_x` / `hatch_offset_y` / `hatch_offset_z` (default `0.0`
  for each): when non-zero, this runway's own effective position eases
  toward being offset by this amount as the hatch opens (and eases back
  once it closes). Intended for a sliding-type hatch that moves
  up/down/forward/back (a carrier's own elevator deck, say)
  - **Example** (an elevator that descends 8 blocks from hangar height
    down to deck level):
    ```json
    { "width": 5.0, "center_x": 0.0, "height_y": 2.0, "start_z": -3.0, "end_z": 3.0, "hatch_offset_y": -8.0, "hatch_move_speed": 2.0 }
    ```
- `hatch_move_speed` (default `1.0`): the easing speed (progress/second)
  for the offset above. This automatically gets the same scaling
  correction (runs at 2x the configured speed) that a `toggle_parts`
  entry with a `"$hatch"` prefix already gets - if you want it to move at
  the same speed as the visible hatch, give it the same number as that
  matching `toggle_parts` entry's own `speed`
  - **Example**: `"hatch_move_speed": 2.0`

Specifying both `hatch_gated` and `hatch_offset_*` at once is technically
possible, but normally only one or the other is meant to be used.

An example combining multiple decks/elevators:
```json
"runways": [
  { "width": 30.0, "center_x": 0.0, "height_y": 12.0, "start_z": -105.0, "end_z": 90.0 },
  { "width": 5.0, "center_x": 0.0, "height_y": 2.0, "start_z": -3.0, "end_z": 3.0, "hatch_offset_y": -8.0, "hatch_move_speed": 2.0 }
]
```

### Combining with the carrier-aircraft-launching weapon (`Type = CARRIER`)

A runway (`runways`) works fine with no carrier-aircraft-launching weapon
at all, and a `Type = CARRIER` weapon likewise works fine with no runway
defined (a launched aircraft is then recovered by some other means, e.g.
`CarrierLandingToAmmoRadius`).

That said, **having a launched aircraft actually land on this runway and
get recovered there is the originally-intended basic form of a full
carrier.** See `Readme_Weapon_Cas.md` for the weapon's own settings. When
used this way, the runway (`runways`) acts as the physical footing/
support for a landed or stationary aircraft, and carries it along as
this vehicle moves, turns, or (with a hatch-linked runway) rises and
falls.

---

## 13. Parachuting

The fields related to parachuting are the per-seat `enable_parachuting`
(see "3. Seats") plus two vehicle-level fields, `enable_ejection_seat`
and `mob_drop_option`. All three are optional; leaving them unset has no
effect at all on an existing vehicle's own behavior.

### `enable_ejection_seat`
- **Format**: boolean (default: `false`)
- **Description**: Whether the pilot can hold the Space key to eject
  every occupant of a seat with `enable_parachuting` set (including the
  pilot themself) all at once. An occupant of a seat with
  `enable_parachuting` enabled is given a parachute; an occupant of a
  seat where it's disabled is simply force-dismounted.
- **Example**: `"enable_ejection_seat": true`

### `mob_drop_option`
- **Format**: object (optional)
- **Description**: A feature where a short press of the assigned
  parachute-jump key drops the occupants (mobs) of every seat with
  `enable_parachuting` set (excluding the pilot seat, in ascending seat
  order) one at a time, parachuting each in turn at the interval given by
  `interval_ticks`. If left unset, that key press does nothing. Holding
  the same key instead targets every seat, exactly like
  `enable_ejection_seat` - each occupant either gets a parachute or is
  force-dismounted depending on whether `enable_parachuting` is enabled
  for their own seat. Fields:
  - `rel_x` / `rel_y` / `rel_z` (required): the drop position's own
    relative coordinate from the vehicle's own reference position
    (rotates to match the vehicle's own current heading).
  - `interval_ticks` (required): the interval before dropping the next
    mob (in ticks, 1/20 second each).
- **Example**:
  ```json
  "mob_drop_option": { "rel_x": 0.0, "rel_y": -0.5, "rel_z": 0.0, "interval_ticks": 20 }
  ```

---

That's every common field this mod interprets on a vehicle JSON. For a
field that only has meaning for a specific type (`wheel_parts`,
`vtol_rotor_parts`, etc.), see the corresponding
`Readme_Vehicle_<Type>.md`.
