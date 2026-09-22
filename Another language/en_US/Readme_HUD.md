# Readme_HUD.md - HUD script files

Target file: `assets/<namespace>/hud/<name>.txt` (a custom text format,
following the same syntax as MC Heli's own original HUD scripts)

Use this text script to define a custom HUD (aiming reticle,
instruments, turret indicator, etc.) drawn on screen while riding a
vehicle. One `.txt` file corresponds to one "script", and the vehicle's
own configuration specifies which HUD script to use (for the
HUD-related fields on the vehicle config file side, see
`Readme_Vehicle.md`).

---

## Contents

1. Basic file structure
2. List of drawing commands
3. List of variables
4. Example: turret direction indicator

---

## 1. Basic file structure

### Basic syntax

Each line takes the form `Directive = argument1, argument2, ...`
(following MC Heli's own original HUD script syntax exactly). A
directive that takes no arguments (`Exit`, `EndIf`) is written on its own
with no `=`.

```
DrawString = 0, 0, "Speed: %.0f", speed
```

- Leading whitespace on a line is ignored.
- Anything after `;` is ignored as a comment (except inside a
  double-quoted string).
- Blank lines are ignored.
- An invalid line doesn't cause the whole file to fail - only that one
  line is skipped (a warning is logged).

### Conditionals (`If` / `EndIf`)

```
If = hp_rto < 0.3
  Color = 255, 255, 0, 0
  DrawString = 0, -20, "WARNING"
EndIf
```

The condition (the right-hand side of `If =`) is written using the
expression syntax below. Any non-zero value is treated as true.

- Everything from `If` to the next `EndIf` is one block. If the `EndIf` is
  missing, **everything to the end of the file becomes part of that `If`**.
- Nested `If` (an `If` inside an `If`) is not supported. The inner `If` line
  is ignored, and the inner `EndIf` **closes the outer `If`** (the lines after
  it run regardless of the condition). To combine conditions, use `&&` /
  `||` in a single condition instead (e.g. `If = locked && wpn_ammo > 0`).

### Calling a sub-script (`Call` / `Exit`)

```
Call = compass_tape
```

`Call = script name` (no extension or path, lowercase) calls another
script file inside the same `hud` folder. `Exit` stops execution of the
current script (or the one called via `Call`, if that's where it
appears) at that point.

If the called script can't be found, nothing happens (it isn't an error).
A `Call` to a script that is already running further up the same call
chain (including itself) is ignored.

### Expression syntax

The following operators are available (lowest precedence first;
parentheses `( )` change the order).

| Operator | Meaning |
|---|---|
| `cond ? a : b` | Ternary (a if cond is non-zero, otherwise b) |
| `\|\|` | Logical OR (1 if either side is non-zero, otherwise 0) |
| `&&` | Logical AND (1 if both sides are non-zero, otherwise 0) |
| `==` `!=` | Equal / not equal (1 if true, 0 if false) |
| `>` `<` `>=` `<=` | Comparison (1 if true, 0 if false) |
| `+` `-` | Addition / subtraction |
| `*` `/` | Multiplication / division (division by zero gives 0) |
| `-` `!` | Negation / logical NOT (unary; `!` gives 1 for 0, 0 otherwise) |

- Numbers can be decimal (`12`, `0.5`) or hexadecimal (`#FF00FF` or
  `0xFF00FF`) - handy for passing a packed ARGB value to `Color`
  (e.g. `Color = #FF00FF00`).
- Variable names are case-insensitive. An unknown variable name is **not an
  error - it simply evaluates to 0**, so watch for typos.
- Function calls such as trigonometric functions are not supported (for
  drawing that involves rotation, use `DrawTexture`'s own rotation
  parameter, described below, instead).

Examples:
```
DrawString = 0, 0, "%03d", yaw < 0 ? yaw + 360 : yaw
If = lock_progress > 0 && !locked
```

Variable names are listed in "3. List of variables" below.

---

## 2. List of drawing commands

Every coordinate is relative to the screen's own center
(`center_x, center_y`) as the origin.

### `Color`
- **Format**: `Color = packed ARGB integer` or `Color = a, r, g, b`
- **Description**: Sets the color used by every drawing command that
  follows.

### `DrawString` / `DrawCenteredString`
- **Format**: `DrawString = x, y, "format string", value1, value2, ...`
- **Description**: Draws text using a printf-style format string.
  Supports `%d` (integer), `%.Nf` (decimal), and `%s` (string - passing a
  bound string variable's own name directly substitutes its value; see
  `wpn_name` etc. below). `DrawCenteredString` draws it centered
  horizontally.

### `DrawTexture`
- **Format**: `DrawTexture = "texture name", x, y, w, h, u, v, uw, vh, rotation angle (degrees, optional)`
- **Description**: Crops a `uw×vh` pixel rectangle with `(u,v)` as its
  top-left corner from `assets/<namespace>/textures/gui/<texture name>.png`
  (or an already-loaded texture from another addon), and draws it at
  `(x,y)` as its own top-left corner, sized `w×h`. If you specify the
  final rotation angle (degrees, optional), it rotates clockwise around
  the center of the drawn area. This can be used directly, without any
  trigonometric functions, for drawing a rotating needle/indicator.
- **Example**: `DrawTexture = "needle", -8, -8, 16, 16, 0, 0, 16, 16, gun_yaw`

### `DrawRect`
- **Format**: `DrawRect = x, y, w, h`
- **Description**: Draws a filled rectangle. Passing a negative value for
  w or h lets you use it as a bar that grows in that direction (for a
  remaining-amount display, etc.).

### `DrawLine` / `DrawLineStipple`
- **Format**: `DrawLine = x1, y1, x2, y2, x3, y3, ...`
- **Description**: Draws line segments connecting each successive pair
  of points (3 or more points forms a connected polyline).
  `DrawLineStipple` additionally takes 2 leading arguments (dash pattern,
  spacing), but the dashed appearance itself isn't implemented - it's
  drawn as an ordinary solid line.

### `DrawGraduationYaw`
- **Format**: `DrawGraduationYaw = center angle, x, y`
- **Description**: Draws a compass tape (heading scale) running across
  the top of the screen.

### `DrawGraduationPitch1`
- **Format**: `DrawGraduationPitch1 = center angle, x, y`
- **Description**: Draws a pitch scale (ladder-style) along the side of
  the screen.

### `DrawGraduationPitch2`
- **Format**: `DrawGraduationPitch2 = pitch angle, roll angle, x, y`
- **Description**: Draws a simplified artificial horizon (a horizon line
  supporting both pitch and roll) in the center of the screen.

### `DrawCameraRot`
- **Format**: `DrawCameraRot = x, y`
- **Description**: Draws a small marker showing how far the player's own
  view has diverged from the vehicle body's own heading
  (`plyr_yaw - yaw`).

### `DrawEntityRadar` / `DrawEnemyRadar`
- **Description**: Not implemented. Using these does not cause an error -
  they simply draw nothing at all.

---

## 3. List of variables

### Screen/basic info
| Variable | Meaning |
|---|---|
| `center_x` / `center_y` | The screen's own center, in pixel coordinates |
| `width` / `height` | The screen's own full size, in pixels |
| `yaw` / `pitch` / `roll` | The vehicle body's own current heading (degrees) |
| `plyr_yaw` / `plyr_pitch` | The occupant's own view direction (degrees, world-absolute) |

### Altitude/position/speed
| Variable | Meaning |
|---|---|
| `altitude` | Height above the ground |
| `sea_alt` | Height above sea level (the world's own SeaLevel) |
| `pos_x` / `pos_y` / `pos_z` | The vehicle's own world coordinates |
| `motion_x` / `motion_y` / `motion_z` | The vehicle's own current velocity vector (per axis) |
| `speed` | The vehicle's own current speed (blocks/tick) |
| `speed_kbh` | The same converted to km/h |

### Health/fuel
| Variable | Meaning |
|---|---|
| `hp` / `max_hp` | Current health / max health |
| `hp_rto` / `hp_per` | Health as a fraction (0-1) / percentage (0-100) |
| `fuel` | Fuel as a fraction (0-1) |
| `low_fuel` | Alternates between 1 and 0 at a blinking interval when fuel is low |

### Control input
| Variable | Meaning |
|---|---|
| `throttle` | The current throttle value |
| `stick_x` / `stick_y` | The stick-equivalent input value (roughly -1 to 1) |
| `free_look` | Whether currently in free-look mode (0/1) |
| `manual_mode` | Whether currently in manual mode (0/1; only meaningful for Aircraft/VTOL) |
| `stalling` | Whether currently stalling (0/1; only meaningful for Aircraft/VTOL) |
| `gear_deployed` | Whether the landing gear is deployed (0/1) |

### Weapons (about the currently-selected weapon)
| Variable | Meaning |
|---|---|
| `wpn_name` | The selected weapon's own display name (a string variable, for use with `DrawString`'s own `%s`) |
| `wpn_ammo` / `wpn_rm_ammo` | Current round count / magazine size |
| `reloading` / `reload_time` | Whether currently reloading (0/1) / remaining ticks (also includes fire-rate delay) |
| `wpn_heat` / `is_heat_wpn` | Heat as a fraction (0-1) / whether this is a heat-managed weapon (0/1) |
| `lock_progress` / `locked` / `lock` | Lock-on progress (0-1) / whether locked (0/1) / same value as `lock_progress` |
| `wpn_mode` / `has_modes` | The current weapon mode number / whether it has multiple modes (0/1) |
| `sight_type` | The sight type (an internal enum value) |
| `mortar_distance` / `mortar_distance_str` / `has_mortar_distance` | Mortar-style range (numeric/string) / whether this is a range-display-capable weapon (0/1) |
| `gun_yaw` / `gun_pitch` | See "4. Example" below |

### Other
| Variable | Meaning |
|---|---|
| `time` | The world's own time of day (0-23999) |

The following exist for compatibility but are always a fixed value in
this implementation (not implemented): `dsp_mt_dist`, `mt_dist`,
`have_radar`, `radar_rot`, `vtol_stat`, `cam_mode`, `cam_zoom`,
`auto_pilot`, `have_flare`, `can_flare`, `inventory`, `hovering`,
`is_uav`, `uav_fs`, `gunner_mode`, `test_mode`.

---

## 4. Example: turret direction indicator

### `gun_yaw` / `gun_pitch`

These are variables representing the direction the currently-selected
weapon (the same weapon `wpn_ammo` etc. refer to, i.e. the client's own
currently-selected weapon) **is actually currently pointing**. They're
relative angles (degrees), measured against the vehicle body's own
heading (`yaw`) - `gun_yaw` of `0` points straight ahead of the vehicle,
negative points left, and positive points right.

Unlike `plyr_yaw` (the occupant's own view), this value directly reflects
**the direction a round would actually be fired in**, accounting for any
turn-rate limit from `turret_rotation_speed` (see chapter 4 of
`Readme_Vehicle.md`). For a weapon with a turn-rate limit, `gun_yaw`
only changes at that limited rate even if the occupant moves their own
view quickly. For a weapon with no turn-rate limit (no
`turret_rotation_speed` set), it follows the occupant's own view
instantly. When you switch weapons, the newly-selected weapon's own
settings (whether it has a limit at all, and its value) take effect
immediately.

While a weapon with no `turret_rotation_speed` set is selected, this
always matches the occupant's own view direction exactly. While a fixed
weapon with no `aim_range` (aiming range) set at all is selected, both
`gun_yaw` and `gun_pitch` return `0` (since such a weapon has no concept
of "direction" at all).

### Example 1: a rotating needle texture

```
; A turret indicator needle that rotates, around center (0,0), to match
; the selected weapon's own actual direction.
; Prepare the texture at 16x16px, with the needle's own base positioned
; at the center of the image.
DrawTexture = "turret_needle", -8, -8, 16, 16, 0, 0, 16, 16, gun_yaw
```

### Example 2: a simplified indicator using line segments

If you don't want to prepare a texture, you can also display this as a
simplified horizontal bar, using the same idea as `DrawLine` and
`DrawCameraRot` (converting an angle into a pixel offset).

```
; A compass bar spanning ±60 degrees, centered on the vehicle's own
; front (0 degrees), at the bottom-center of the screen.
; Shows a white marker at the angle the selected weapon is currently pointing.
Color = 255, 255, 255, 255
DrawLine = -60, 100, 60, 100
DrawRect = gun_yaw - 1, 96, 2, 8
```

### Example 3: numeric display

```
DrawString = 0, 110, "GUN: %.0f", gun_yaw
```
