# Tudur's Vehicle Mod (Fabric 1.21.11)

**Author**: Tuduraw
**License**: MIT License (see `LICENSE`)

> **On the use of generative AI**
> The code and documentation in this project were produced using
> Anthropic's generative AI, "Claude".

A template project that takes the "vehicle mod" idea - MCHelicopter,
Flan's Mod - and makes it extensible with **nothing but a JSON definition
plus an OBJ model**. It supports ships, aircraft, submarines, weapons,
spawner items, and a dedicated creative tab, and bundles a tool
(`tools/mcheli_convert.py`) for converting MCHeli addon packs into this
project's own format.

> **Important**: Before building an addon pack, read `GUIDELINES.md`
> regarding the handling of models and data that other people hold the
> rights to. Converting an MCHeli addon pack is also subject to
> restrictions.

> **Note on this translation**: this file is a condensed version of the
> Japanese `README.md`, keeping its structure and every key fact while
> compressing the longest implementation walkthroughs. For the fullest
> detail, see the Japanese original. The per-topic `Readme_*.md` files
> remain the authoritative reference for every configurable field, and
> those are translated in full.

This document is a developer-facing architecture reference. **For the
detailed list of every configurable field, aimed at addon pack authors**,
see:

- `GUIDELINES.md` — usage guidelines (rights handling, notes on depiction)
- `Readme_Addon.md` — where an addon pack goes, directory structure, naming
- `Readme_Addon_Mod.md` — how to build an addon mod (extending via code)
- `Readme_Vehicle.md` / `Readme_Vehicle_<Type>.md` — every vehicle field
- `Readme_Weapon.md` / `Readme_Weapon_<Type>.md` — every weapon field
- `Readme_HUD.md` — the HUD script format
- `Readme_cfg.md` — the list of config settings
- `GUIDE.md` — a brief summary of the above, plus how to use the MCHeli
  conversion tool

## Architecture

```
data/<namespace>/vehicles/*.json    ← vehicle "data" (readable on the server too: hitbox/speed/seats/weapons/which model)
assets/<namespace>/models/obj/*.obj ← the visual mesh (client only)
assets/<namespace>/textures/vehicle/*.png ← textures

VehicleDefinition (record, Codec)   ← the data object parsed from the JSON above
VehicleRegistry                     ← where currently-loaded VehicleDefinitions are looked up by id (rebuilt on /reload)
VehicleDefinitionReloadListener     ← rescans data/.../vehicles/*.json every time and rebuilds VehicleRegistry

MeshedEntity (interface)             ← the shared face of "something drawn from an OBJ mesh"
AbstractVehicleEntity implements it  ← shared handling: riding, seat placement, NBT sync, weapon firing
  ├ HelicopterEntity                 ← free flight (airborne)
  ├ CarEntity                        ← ground driving + steering
  ├ ShipEntity                       ← surface water (simplified buoyancy)
  ├ SubmarineEntity                  ← two modes, surface/submerged (keys only, not view-linked)
  └ AircraftEntity                   ← fixed-wing (quaternion-based attitude)

WeaponDefinition                     ← one weapon mounted on a vehicle (seat, damage, velocity, fire interval)
VehicleProjectileEntity              ← extends ThrownItemEntity; the projectile reuses vanilla thrown-item rendering
FireWeaponPayload / ModNetworking    ← the C2S packet carrying fire-key input to the server
ThrottleInputPayload                 ← explicitly syncs W/S/A/D input intent from client to server
                                        (PlayerEntity#forwardSpeed/sidewaysSpeed aren't trustworthy
                                        server-side, so vehicle control uses this throughout)

SpawnItemDefinition                  ← display name + tier (1-5) for the spawner item list
item.TieredVehicleSpawnerItem         ← 25 items (kind x tier), fixed icons (no resource pack needed)
client.VehicleSelectScreen           ← the selection screen opened on right-click (lists vehicles at or below that tier)
network.SelectVehiclePayload         ← sends the selection to the server, which re-validates kind/tier and spawns
ModItemGroups                        ← the dedicated creative tab

RunwayDefinition                     ← the runway rectangle declared on the carrying vehicle's own JSON
                                        (settable on any vehicle type)
CarrierAircraftConfig                ← config for one WeaponType.CARRIER weapon (carrier aircraft, launch/return
                                        route, catapult, ammo-conversion radius, etc.)
CarrierRunwayPlatformEntity          ← the invisible floor tiles that realize a runway (follow the carrier every tick)

item.DroneControlStickItem           ← a stick you right-click a vehicle with to register its UUID (UAV/TargetDrone)
block.StationBlockEntity             ← the UAV remote piloting station
block.DroneCenterBlockEntity         ← the autonomous AI flight control block for a TargetDrone
item.DroneRouteBookItem              ← an item that records waypoints while you fly the route yourself
CasStrikeConfig                      ← config for one WeaponType.CAS weapon (support aircraft, attack route)

ObjModel / ObjModelLoader (client)   ← parses .obj into triangle lists, cached across reloads ("o" and "g" both work)
VehicleEntityRenderer (client)       ← draws every vehicle from one class; supports translucent textures

tools/mcheli_convert.py              ← converts an MCHeli addon pack into this project's own format
```

### Why no new Item/EntityType per vehicle

Vanilla finalizes registries such as Item and EntityType **at mod init,
before any world/datapack is loaded**, so a new Item class can't be
registered dynamically for a vehicle a datapack added later. Hence:

- **One EntityType per "way it's controlled"** (helicopter, car, ship,
  submarine, aircraft). A vehicle that merely looks different is just a
  different `VehicleDefinition` on the same EntityType.
- **Only 25 items, "kind x tier"** (car T1-5, ship T1-5, ...). Which
  `VehicleDefinition` actually spawns is chosen by the player in the
  selection screen the item opens.

### Adding a new vehicle with no code

A vehicle that only looks different (handling like one of the existing
kinds is fine) needs zero code changes.

1. Export `.obj` from Blender etc. →
   `assets/tudursvehiclemod/models/obj/my_vehicle.obj`
2. Texture → `assets/tudursvehiclemod/textures/vehicle/my_vehicle.png`
3. Definition → `data/tudursvehiclemod/vehicles/my_vehicle.json` (copy an
   existing json and change the values). `entity_type` takes one of the
   existing kinds (`tudursvehiclemod:car`, `helicopter`, `ship`,
   `submarine`, `aircraft`)
4. (Optional) add `weapons` for armament, and `spawn_item` with a display
   name and tier to make it appear in that kind's own tiered spawner
   selection screen
5. (Optional) `is_uav` / `is_target_drone` disable ordinary riding, making
   the vehicle operable only as a UAV or autonomous drone
6. (Optional, settable on any vehicle type) `runway` gives that vehicle a
   carrier runway - a deck other vehicles can take off from, land on, and
   park on. More simply, it can also just be used as a deck to stand or
   ride on

Only when you want to **change the control logic itself** do you need a
new Java class extending `AbstractVehicleEntity` (implementing
`updateVehicleMovement`). This can also be added via an addon mod instead
of modifying the base mod itself.

### Adding weapons

`weapons` is a list, so one vehicle can carry several:

```json
"weapons": [
  {
    "seat_index": 1,
    "offset_x": 0.0, "offset_y": 0.5, "offset_z": 0.6,
    "projectile_item": "minecraft:iron_nugget",
    "damage": 4.0,
    "velocity": 3.0,
    "cooldown_ticks": 5
  }
]
```

- `seat_index`: only the passenger in that seat (index into `seats`,
  0-based) can fire this weapon
- `projectile_item`: an **existing item id** to fly as the projectile,
  drawn with vanilla thrown-item rendering
- The server re-validates "is this player really in that seat" and "is the
  cooldown over" before firing

### Spawner items (tiered)

Tier 1-5 spawner items exist out of the box for each **kind** of vehicle
(car, ship, submarine, aircraft, helicopter - 25 items total,
`ModItems.TIERED_SPAWNERS`). Icons are fixed per kind, bundled in the mod,
with no resource pack needed.

**Usage**: right-click holding the item to open the selection screen.
Picking a vehicle spawns it and consumes one item (not consumed in
creative). **Packing a vehicle away** (sneak + right-click while empty)
returns one matching spawner item; in creative it simply vanishes without
handing an item back, to prevent duplication.

- All the vehicle sets is `spawn_item.tier` (1-5, default 1) and
  `display_name`
- Tiers are distinguished visually by the icon's own border (T2 green
  brackets, T3 blue full frame, T4 purple frame + white dot, T5 thick gold
  frame + white dot)

### Control scheme (throttle plus view-following)

- **W/S = throttle**, cruise-control style: W/S move the throttle value,
  releasing holds it. Actual speed eases toward throttle x `max_speed`
  every tick
- **Car/ship**: view input never affects the vehicle at all (always
  equivalent to free look). A/D turns left/right
- **Aircraft**: the nose's own heading (yaw + pitch) follows the pilot's
  own view direction. A/D becomes a visual bank (tilt)
- **Helicopter**: the nose's own heading (yaw + pitch) follows the
  pilot's own view direction. Normally, W/A/S/D input moves it forward/
  back/left/right. In manual mode, the vehicle's own attitude is linked
  to view movement instead
- **Submarine**: view input never affects the vehicle at all (always
  equivalent to free look). A/D turns left/right. See the dedicated
  section below for dive controls and other detail
- **Packing a vehicle away**: sneak + right-click while nobody is aboard.
  If a mob is riding it, force-dismount it from the pilot seat first
  (hold the dedicated key)
- **HUD display**: with nothing configured, only a bar showing the
  throttle value and a bar showing remaining fuel are shown. Assign an
  empty HUD config file if you want to remove the display entirely

### Key bindings (defaults; all rebindable in Controls)

| Key | Function |
|---|---|
| Right click | Fire weapon |
| R | Switch between the weapons assigned to your seat |
| X | Switch the selected weapon's own mode (`ModeNum`) |
| Y | Switch seat (next) |
| T | Switch seat (previous) |
| Alt+Y | Switch between a carrier-launched aircraft and the launching carrier (the physical key is shared with Y; holding Alt - the free-look key - with it is interpreted as switching toward the launched aircraft) |
| Left Alt | Free look (view stops following the vehicle while held) |
| Left Ctrl | Descend (helicopter/VTOL). Not Left Shift, since sneak - vanilla's own Left Shift - is what dismounts |
| V | Switch a VTOL between helicopter and aircraft mode |
| H | Open/close hatches and canopies |
| G | Manually retract/extend landing gear |
| N | Fold/extend the main wings (a variable-sweep aircraft can also switch mid-flight) |
| C | Cycle between configured camera positions (`CameraPosition`) |
| PageUp/PageDown | Third-person zoom (aircraft) |
| Up/Down arrows | Ascend/descend a submarine without changing its attitude |
| K | Open the vehicle menu (fuel/ammo resupply, cargo, weapon tabs) |
| J | Start/stop waypoint recording while holding the Drone Route Book in the offhand |
| M (hold 1s) | Toggle manual mode (disables auto-levelling) |

### Creative tab

`ModItemGroups` registers a dedicated tab whose contents are enumerated
dynamically from `VehicleRegistry`, so it follows datapack additions,
removals, and `/reload` automatically.

### OBJ model constraints

`ObjModel`/`ObjModelLoader` handles only `v`/`vt`/`vn`/`f` (n-gons are fan
triangulated). `.mtl`/`usemtl` are ignored - one texture per vehicle, from
the JSON's own `texture` field. Group declarations for
`spinning_parts`/`toggle_parts` work with either `o` or `g`.

---

## Aircraft (AircraftEntity), current implementation

### Attitude management: quaternion-based

Rather than managing yaw/pitch/roll as separate Euler angles, attitude
is held in a single `Quaternionf orientation` field. Input is applied as
an "incremental rotation relative to the current attitude"
(`orientation.mul(delta).normalize()`), and Euler angles are only
extracted (`extractYawPitchRoll`) at the points that actually need a
yaw/pitch/roll number (camera, seats, spinning parts, etc.).

Combined with a dedicated view-input pipeline that never goes through
vanilla's own look controls at all (which clamp at ±90 degrees) -
`PlayerLookRateMixin` captures the mouse's own raw movement, sent to the
server via `AircraftOrientationInputPayload` - this makes **unrestricted
looping** possible.

### Flight physics

- **Grounded/surfaced**: only yaw follows the view; pitch and roll are
  held toward `OnGroundPitch` (below). Throttle input still
  accelerates/decelerates, but **deceleration is slower to respond than
  acceleration** (half the rate of an earlier setting; this doesn't
  affect acceleration or airborne behavior)
- **Normal flight (throttle 50% or above)**: both yaw and pitch follow
  the view, A/D rolls (a full 360-degree barrel roll is possible). Speed
  smoothly eases toward throttle x `max_speed`
- **Gliding (throttle under 50%, airborne)**: gravity strengthens in
  stages as speed drops (6x ordinary falling gravity once speed is fully
  lost)
- **Landing gear deployed**: top speed drops to 70% (representing drag)
- **Deceleration (in flight, when throttle is reduced)**: kicks in 1.5x
  faster than acceleration does (ground braking is configured separately,
  as above)

### Behavior with no pilot aboard

- Goes through **exactly the same physics** as when piloted (the same
  grounded/normal-flight/gliding logic) - there is no separate,
  dedicated unmanned-only code path
- Throttle eases down a little every tick (0.01/tick, roughly 5 seconds
  from full throttle to 0)
- Attitude eases toward level flight at all times with no grace period
  (holding the current heading, pitch 0, roll 0)
- The client never recomputes position/velocity on its own - it simply
  calls `move()` using the server's own synced velocity as-is. Letting
  client and server each compute physics independently causes the two to
  drift apart, producing a visible "correction stutter" every time they
  resync

### `OnGroundPitch` (ground attitude)

While grounded/surfaced and **sufficiently slowed down (20% of top speed
or below)**, pitch eases toward a configured angle (degrees) - this
applies whether piloted or not. Immediately after landing, while still
rolling out with speed remaining, the attitude at the moment of touchdown
is held as-is, and the transition to ground attitude only begins once
fully slowed. The target yaw for this transition is fixed once, at the
instant the transition begins (recomputing it every tick would drift
unintentionally due to how SLERP - spherical linear interpolation - works,
making it unstable).

Conversely, while accelerating for takeoff and reaching **20% of top
speed or above** (the same threshold as ground attitude), the aircraft
eases toward level (pitch and roll both 0) instead of continuing to roll
along the runway in its own ground attitude (often nose-up, for a
taildragger, say) - readying its attitude for takeoff.

Ground acceleration is deliberately half the normal rate, so this
attitude control has time to complete before takeoff.

**On attitude syncing**: yaw/pitch/roll are synced explicitly through
their own dedicated sync fields (DataTracker) rather than relying on
vanilla's own standard sync mechanism (`super.setYaw`/`getYaw`, etc.).
Investigation found that vanilla's own standard sync apparently
optimizes by **not sending rotation updates while position hasn't
changed** - which was the cause of a bug where a grounded, stationary
aircraft's pitch appeared frozen at its landing attitude. The DataTracker
mechanism already used to sync throttle, gear state, and the like syncs
independently of position changes, avoiding this problem.

### Stall

While airborne (not grounded), dropping below 20% of top speed triggers a
stall, forcing the nose straight down - overriding both pilot input and
the unmanned level-flight correction. It clears once speed recovers to
30% of top speed (a 20%-trigger / 30%-clear hysteresis, preventing rapid
flip-flopping right around a single threshold).

### Dive acceleration / climb deceleration

- Climb (nose up) and dive (nose down) are handled as a single continuous
  formula, fixing a bug where the branch switched at exactly 0 degrees of
  pitch (a speed drop around a 45-degree dive, and climb deceleration not
  taking effect in some cases)
- **Dive**: the speed target rises continuously with nose-down angle (1.5x
  top speed at a vertical dive)
- **Climb**: the speed target falls continuously with nose-up angle (0 at
  a vertical climb)
- **Takeoff-ready / gliding determination**: based on what percentage of
  top speed the actual current speed is

### Manual mode

A trigger key (default M, hold for 1 second to toggle) fully disables the
automatic level-flight correction (auto-leveling after 5 seconds of no
input). Shown as a status indicator on the HUD.

### Speed display

The HUD shows actual speed in kb/h (1 kb = 1,000 blocks).

### HUD display

By default, this is a simple HUD showing only the throttle bar, speed
(kb/h), and a FREE LOOK/MANUAL MODE indicator.

**MC Heli-compatible HUD scripts are supported.** A text-format HUD
script following MC Heli's own HUD config file spec can be loaded and
drawn as-is (or nearly as-is) with no code changes.

- Location: `assets/<namespace>/hud/<name>.txt` (read from any namespace
  - a resource pack or another mod's own namespace - not just this mod's
  own)
- Textures it references: place these at
  `assets/tudursvehiclemod/textures/gui/<name>.png` (256x256 recommended)
- Adding `"hud": "<script name>"` to a vehicle's own JSON definition uses
  that script (the same per-vehicle HUD-file approach MC Heli itself
  uses). Falls back to the simple HUD if unset or the script isn't found
  (`aircraft.json` defaults to `"hud": "plane"`, `helicopter.json` to
  `"hud": "heli"`)
- Textures are searched by name (without extension) across every loaded
  namespace, not just the script's own (so, for example, ported data left
  under the `mcheli` namespace still works)
- Supported: `Call`/`Exit`/`If`...`EndIf` (no nesting, matching the
  original)/`Color`/`DrawString`/`DrawCenteredString`/`DrawTexture`/
  `DrawRect`/`DrawLine`/`DrawLineStipple` (dashed lines are simplified to
  solid)/`DrawGraduationYaw`/`DrawGraduationPitch1`/
  `DrawGraduationPitch2`/`DrawCameraRot`. Expressions (arithmetic,
  comparison, logical, ternary, hex literals) and variable references are
  also supported
- Unsupported: `DrawEntityRadar`/`DrawEnemyRadar` (this mod has no radar
  equipment concept, so these are parsed but draw nothing)
- Variables: nearly every variable from the original's own list
  (`yaw`/`pitch`/`roll`/`throttle`/`pos_x`, etc.) is supported. Anything
  with no equivalent concept here (radar, etc.) returns a safe default.
  Custom additions of this mod's own: `manual_mode` (in manual mode?),
  `stalling` (stalling?), `gear_deployed` (gear deployed?), `speed`
  (actual speed, blocks/tick), `speed_kbh` (same, kb/h)
- `stick_x`/`stick_y`: for aircraft, roll input (A/D, a held state, so
  stable) and mouse pitch input (not a held state - consumed every tick,
  so the value is instantaneous). Every other vehicle uses its own
  sideways/forward input instead
- Textures have their actual PNG dimensions read to compute correct UV
  coordinates
- Reloaded on a resource reload (F3+T, etc.)

### Landing gear

- **Automatic deployment**: deploys automatically the instant it lands.
  Never automatically retracts (manual only)
- **Manual retract/deploy**: G key (default,
  `key.tudursvehiclemod.gear_toggle`). The next actual landing overrides
  this with automatic deployment again
- Top speed drops while deployed (as above)

### Hatches/canopies

Supports toggle parts opened/closed with the H key (default,
`key.tudursvehiclemod.hatch_toggle`), both rotating and sliding types. A
single key press switches both the hatch and canopy to the same state
(open/closed) together.

- Immediately after spawning, the hatch starts closed (the canopy starts
  open)
- While riding, the hatch keeps whatever state it was in before boarding
  (it doesn't open/close automatically). The canopy automatically retracts
  (closes) on boarding

### Third-person player-model linkage

The riding player's own model (your own third-person appearance, or how
an occupant appears to other players) follows the vehicle's own attitude
completely, **as if it were one of the vehicle's own parts** (the whole
body matches the vehicle's yaw/pitch/roll).

**The head's own yaw (left/right) is locked to the vehicle's own heading
unless free-looking. Pitch (up/down) still behaves as vanilla always has
- moving independently based on the player's own view direction.**

Implemented via three mixins into `LivingEntityRenderer`
(`AircraftRidingRenderState` interface, `LivingEntityRenderStateMixin`,
and `LivingEntityRendererMixin`). The `bodyYaw` argument of
`setupTransforms` (vanilla's own dedicated method for applying body-yaw
rotation to the matrix stack) is itself substituted with the vehicle's
own yaw, with an additional pitch/roll rotation multiplied in right after.

### Camera

- Normally: the camera fully follows the vehicle's own heading (a cockpit
  view, including its own tilt)
- While free-looking: the player's own view input is reflected directly,
  unaffected by the vehicle's own heading (in third person, this becomes
  vanilla's own current camera rotation, pulled back by the zoom
  distance)
- Third-person zoom: PgUp/PgDn (default), in 4-block steps, with terrain
  collision
- Third-person's own face-on view (pressing F5 twice, vanilla's own
  second-stage third person) is also supported

---

## Submarine (SubmarineEntity), current implementation

A fully key-driven vehicle where mouse input never affects the vehicle at
all. The view is always free to look around (the same as an aircraft's
own free look).

### Surface/dive toggle

Toggled with the H key (default, shared with the hatch/canopy key). This
state is shared with the open/closed state of hatch-type toggle parts
(`toggle_parts` with `trigger: "key"`) - diving closes the hatch, and
surfacing opens it. Spawns in surface mode (hatch open).

### Surface mode

Handles exactly like a ship (ShipEntity) - throttle plus direct A/D yaw
turning - with one difference:

- **Bank while turning**: opposite to a ship's own. A ship banks outward
  (its own center of mass sits above the waterline, so centrifugal force
  banks it out), while a submarine banks inward (its own center of mass
  sits below the waterline)

While submerged, aiming for weapon parts such as `AddPartWeapon` stops
following (their orientation freezes), and firing weapons at all becomes
impossible. Only torpedoes (`Type=Torpedo`) remain usable while
submerged, unless a weapon's own config file sets
`UsableWhileDiving = true`, which allows other weapon types to be used
while submerged too. While an unusable weapon is selected, its own
`weapon_bay`-triggered weapon-bay part also never opens (even if
selected).

### Dive mode

- **W/S**: throttle (shared with surface mode, with the limits below)
- **A/D**: heading change (shared with surface mode, direct yaw turning)
- **Space**: nose up (pitch toward surfacing)
- **Left Ctrl** (default, shared with the descend key): nose down (pitch
  toward sinking)
- **Up arrow**: ascend without changing attitude
- **Down arrow**: descend without changing attitude

As with aircraft, pitch automatically returns to level (0 degrees) after
a fixed period (5 seconds) of no input. Manual mode (hold M, shared with
aircraft) disables this auto-leveling. Roll always returns to 0 while
submerged (there's no banking underwater).

Reaching the surface while still nose-up returns the nose to level more
gently than an aircraft's own stall, with a somewhat stronger downward
force applied during that transition (to prevent it from continuing to
surface indefinitely).

While underwater, nothing but input (throttle, pitch, the arrow keys)
moves it vertically at all - gravity and buoyancy are both completely
disabled. Ordinary gravity only applies once fully out of the water
(running aground, say).

### Throttle limits

Forward is normally capped at 100%, reverse at -20%. Plus and minus can't
be switched between directly (making it easier to settle at exactly 0%
throttle) - it waits at 0% before beginning to accelerate in the opposite
direction.

### Behavior with no pilot aboard

Goes through exactly the same handling as when piloted, except that
throttle naturally eases back toward 0% (there is no separate, dedicated
unmanned-only code path).

### Camera and player display

View (yaw/pitch) always reflects the player's own input directly
(equivalent to free look), but roll (tilt) alone comes from the vehicle
itself and is reflected in both the camera and the third-person player
display (sharing the same mechanism as an aircraft's own free look).

---

## UAV / autonomous drone / close air support (CAS)

Three independent mechanisms for operating a vehicle without a player
riding it directly.

### UAV (unmanned aerial vehicle, remote piloting station)

Setting `"is_uav": true` in a vehicle's own JSON definition disables
ordinary riding (boarding a seat directly) for that vehicle. Instead, it
is piloted remotely through the following steps:

1. Right-clicking the target vehicle while holding a **Drone Control
   Stick** (the registration stick) registers that vehicle's own UUID to
   the stick (one stick always holds only the single most recently
   registered vehicle - registering again overwrites it)
2. Inserting a registered stick into a **UAV Station** block links that
   station to that vehicle
3. Right-clicking the station rides the player onto that vehicle as a
   remote pilot - handling exactly like ordinary riding in every respect,
   including view input and firing weapons
4. If the vehicle is outside the loaded range, the station temporarily
   force-loads the chunk at its own last known coordinates (up to 5
   seconds) while it searches for the vehicle

### TargetDrone (autonomous AI flight)

Setting `"is_target_drone": true` in a vehicle's own JSON definition
likewise disables ordinary riding. It uses the exact same registration
stick mechanism as a UAV, and is likewise linked by inserting the stick
into a **Drone Center** block - but while the Drone Center has it
enabled, the vehicle flies under autonomous AI with no player input at
all.

- **Orbit mode (default)**: with no waypoints configured, it orbits above
  the Drone Center at a set altitude. The turn radius is derived
  naturally from the vehicle's own actual performance (turn speed,
  cruise speed) - it isn't a fixed value
- **Waypoint patrol mode**: enter several waypoints directly from the
  Drone Center's own settings screen, or insert a **Drone Route Book**
  (an item), and it patrols that route
- **Drone Route Book**: an item for recording a route by actually flying
  it - hold it in the offhand and press J to start/stop recording
  waypoints while flying the vehicle yourself
- **Home Point**: the landing target. Defaults to the vehicle's own
  position at the moment the stick was linked, and can be edited just
  like any other waypoint
- Can be enabled/disabled from the settings screen. Disabling it mid-
  flight doesn't strand it where it is - it safely lands near the Home
  Point first, then stops
- The Drone Center also permanently force-loads a 3x3 chunk area centered
  on the vehicle's own current position, so it never drifts out of the
  loaded range

### Close air support (CAS, `WeaponType.CAS`)

Setting a weapon's type to `CAS` in its own config file (`.txt`), on a
carrier vehicle, ground vehicle, or similar, makes that weapon spawn a
support aircraft (another vehicle definition) on the spot instead of
firing a projectile, flying it along a configured waypoint route while
attacking automatically.

- On firing, the point where the crosshair's own aim intersects the
  ground is marked as the route's own reference coordinate
- The configured support aircraft spawns at the route's own first
  waypoint and flies the route automatically
- While passing through a waypoint with `attack=true` set, it fires
  automatically using the specified weapon slot
- It despawns on reaching the last waypoint (unlike a carrier's own
  carrier aircraft, it doesn't return and land)
- Has two safety mechanisms: a lifetime timeout, and a timeout for
  stalling with no progress for a set period

These mechanisms share a common foundation (waypoint patrol, attack
detection, chunk force-loading) with the Carrier system described next.

---

## Carrier system

Lets a vehicle function as a carrier, managing launch, automatic flight,
and return of carrier aircraft. **This isn't limited to any particular
vehicle type** - the implementation has no `entity_type` dependency at
all, so a fictional "flying aircraft carrier" works just as well.

### Runway (`runway`/`runways`)

**A runway (`runways`) and the carrier-aircraft-launching weapon
(`Type = CARRIER`, described below) aren't required by each other** -
either works alone. You can set a runway purely as a deck with no weapon
at all, or conversely set only a `Type = CARRIER` weapon with no runway
defined (assuming the launched vehicle is recovered by some other means).
Combining both, so a launched carrier aircraft actually lands on this
runway and gets recovered there, is of course also possible - that
combination is the originally-intended basic form of a carrier (see
"Main behaviors" below for details).

A `runway` object (one) or `runways` array (several decks) on the
carrying vehicle's own JSON defines a rectangular runway on its deck:

```json
"runway": {
  "width": 30.0,
  "center_x": 0.0,
  "height_y": 12.0,
  "start_z": -105.0,
  "end_z": 90.0
}
```

- All coordinates are relative to the carrying vehicle's own entity
  position, and rotate together with its own current heading (yaw). This
  defines a rectangular runway centered on `center_x`, `width` blocks
  wide, spanning `start_z` to `end_z` (either order), at `height_y`
  blocks above the vehicle's own position
- Realized as a set of `CarrierRunwayPlatformEntity` (invisible floor
  tiles) covering the whole area, so a carrier aircraft's own landing
  gear, stall detection, and collision are all treated as if actual
  blocks were present there. Tile size is derived automatically from the
  `carrierRunwayExpectedWidth` server setting (default 30.0)
- A vehicle with no runway defined never uses this feature at all
  (behaves as an ordinary vehicle)
- Use the `runways` array if you want several independent runways - for
  multiple decks, an elevator, etc.:
  ```json
  "runways": [
    { "width": 30.0, "center_x": 0.0, "height_y": 12.0, "start_z": -105.0, "end_z": 90.0 },
    { "width": 5.0, "center_x": 0.0, "height_y": 2.0, "start_z": -3.0, "end_z": 3.0, "hatch_offset_y": -8.0 }
  ]
  ```

#### Hatch-linked runway (optional fields)

Each element of `runway`/`runways` can add the following optional
fields, independent of any particular `toggle_parts` name. If you want it
to sync with a visible hatch animation, match the numbers on both sides
yourself on the addon pack's own side - there's no automatic linkage.

- `hatch_gated` (bool, default `false`): when `true`, this runway (and
  its own tiles) only exists while a hatch is open. Intended for a
  rotating-type hatch (a landing craft's own bow ramp, say - one whose
  partial-open progress can't be tracked). A simple on/off toggle, with
  no partial-open state considered at all
- `hatch_offset_x`/`hatch_offset_y`/`hatch_offset_z` (double, default
  `0.0`): when non-zero, this runway's own effective position eases
  toward being offset by this amount as the hatch opens (and eases back
  to its original position as it closes). Intended for a sliding-type
  hatch that moves up/down/forward/back (a carrier's own elevator, say)
- `hatch_move_speed` (double, default `1.0`): the easing speed
  (progress/second) for the offset above. This automatically gets the
  same scaling correction (runs at 2x the configured speed) that a
  `toggle_parts` entry with a `"$hatch"` prefix already gets - if you
  want it to move at the same speed as the visible hatch, give it the
  same number as that matching `toggle_parts` entry's own `speed`

Specifying both `hatch_gated` and `hatch_offset_*` at once is technically
possible.

### The carrier-aircraft-launching weapon (`WeaponType.CARRIER`)

Setting a weapon's type to `CARRIER`, in the config file (`.txt`) for a
weapon mounted on the carrier, makes that weapon spawn a carrier aircraft
(another vehicle definition) on the spot instead of firing a projectile,
and fly it along a configured route. **This works regardless of whether
a runway (`runways`) is defined** - a returning carrier aircraft is
recovered by the position check described by `CarrierLandingToAmmoRadius`
below, plus a check for having slowed down enough, not by whether it has
actually touched down on a runway tile.

Main fields:

- `CarrierAircraft`: the vehicle file name of the carrier aircraft to
  launch
- `CarrierWaypoint` (repeatable): the ordinary patrol/attack route (same
  format as the CAS weapon: relative X, Y, Z, speed %, whether to attack)
- `CarrierLaunchWaypoint` (repeatable): a dedicated takeoff route flown
  first, immediately after launch. Format is
  `relative X, Y, Z, speed %, gear, bay, speed override (km/h)` (the
  gear/bay/speed-override columns are optional, `-` meaning "no change").
  **Speed override** is for a catapult effect: the instant this waypoint
  is reached, the vehicle's own horizontal speed is instantly overwritten
  to this value (km/h) - heading is preserved, and normal speed control
  takes over afterward. The gear/bay columns are forced open/close
  triggers for landing gear/weapon bay, but currently only actually take
  effect at waypoint index 0
- `CarrierLandingWaypoint` (repeatable, at least one required): the
  return-flight landing approach route. Same format as
  `CarrierLaunchWaypoint` (the speed-override column can be set too, but
  is unused on the landing side). The final point becomes the reference
  point for final approach
- `CarrierLandingToAmmoRadius` (default 15.0): a radius centered on the
  AddWeapon position. A carrier aircraft (crewed or not) inside this
  range is automatically converted into one round of ammo. This recovery
  zone is always active, regardless of whether a runway is defined
- `CarrierRecoveryPoint` (repeatable, optional): format
  `relative X, Y, Z, radius` - defines any number of additional recovery
  zones on top of the AddWeapon-position zone above. Coordinates are
  relative to the AddWeapon position (following the carrier's own
  heading). Useful for a large carrier's own multiple landing spots, for
  example

### Main behaviors

- **Launch**: spawns at the firing position, flies the
  `CarrierLaunchWaypoint` route first, then joins the ordinary
  `CarrierWaypoint` route
- **Switching seats**: the player who launched it can switch, with
  Alt+Y (default), between riding the launching carrier and the launched
  carrier aircraft
- **Return/landing**: on reaching the last point of the `CarrierWaypoint`
  route, instead of despawning it moves into an automatic landing
  sequence via the `CarrierLandingWaypoint` route
- **Conversion to ammo**: a carrier aircraft that has returned near the
  AddWeapon position via the `CarrierLandingWaypoint` route, or that is
  inside a `CarrierLandingToAmmoRadius`/`CarrierRecoveryPoint` recovery
  zone, is automatically converted into this weapon's own ammo (+1
  round). This check is purely position/velocity based and does not
  require actually touching a runway tile (it works exactly the same way
  for a carrying vehicle with no runway defined at all)

### Following the mothership

Any entity present on the runway (the runway tiles themselves, carrier
aircraft, other vehicles, players, etc.) follows the carrier's own
movement and turning every tick. A player follows via a difference-based
position update through `requestTeleport()`; every other entity simply
has the carrier's own movement since the previous tick added directly to
its own position. For an unmanned vehicle, the client never recomputes
its own physics (velocity) independently - it only updates position based
on the velocity the server has already computed and synced. Each of
client and server redoing physics independently would cause the values
to drift slightly apart, getting corrected on every resync in a way that
looks like a stutter or a small teleport. Note that this particular
stutter isn't fully solved yet, and a small amount still occurs (a fix is
pending).

---

## Fuel, resupply, and the vehicle menu

- **Fuel**: `max_fuel` (default 600) and `fuel_consumption` (default 0.5
  per second, with a throttle-dependent easing curve). Running out pins
  throttle to 0; piloting still works. **A negative `fuel_consumption`
  means the vehicle uses no fuel at all** - nothing depletes, no warning
  fires, and it is always treated as full
- **Vehicle menu (K)**: opens while riding, with fuel resupply (insert a
  fuel can), ammo resupply (consuming matching items), and cargo (for a
  vehicle with `inventory_size` set; 45 slots per page, paging beyond
  that)
- **Fuel cans and the refiner**: a fuel can from iron ingots, a refiner
  from a piston plus 8 furnaces. The refiner converts fuel items into
  fuel in a can; the can supplies a vehicle. A creative-only infinite can
  also exists

---

## Sound system

A custom sound loading/playback system matching MCHeli's own
`Sound`/`SoundVolume`/`SoundPitch`/`SoundPitchRandom`/`SoundDelay`
settings, for both vehicles and weapons.

- **Weapons**: `WeaponDefinition`'s own `sound` (defaults to
  `<weapon name>_snd` if unset), `sound_volume`, `sound_pitch`,
  `sound_pitch_random`, and `sound_delay_ticks`. Played once, at the
  weapon's own world position, per shot. `sound_delay_ticks` is the
  "actual interval sound plays at" for rapid-fire weapons, managed
  separately from the fire rate (`cooldown_ticks`)
- **Vehicles**: `VehicleDefinition`'s own `engine_sound`. Loops
  continuously as an engine sound, with volume and pitch varying with
  throttle (idle to full)
- Files live at `assets/<namespace>/sound(s)/<name>.ogg` (both the
  `sound` and `sounds` folder spellings are searched, across every
  namespace), including inside loose folders such as
  `tudursvehiclemod-addons` (`client.sound.AddonSoundLoader` scans these
  directly, the same mechanism the HUD system uses)
- Internally this bypasses Minecraft's own sound system entirely (no
  `sounds.json` resource-pack registration): OGG files are decoded
  directly and uploaded to OpenAL buffers, with playback managed
  independently (since files in a loose folder aren't visible to
  `ResourceManager`)
- Weapon fire sounds are sent from the server to every player tracking
  that vehicle via a dedicated packet, and each client plays its own copy

### Weapon stats (loaded at runtime)

A weapon's damage, velocity, fire interval, gravity, sound, and
projectile model are not baked into the vehicle's own JSON - they're
**read directly, on every single shot**, from
`assets/<namespace>/weapons/<weapon_name>.txt` (MC Heli format,
unchanged) (`asset.WeaponStatsLoader`). This works the same way HUD
scripts do, scanning a loose folder such as `tudursvehiclemod-addons`
directly (since the ordinary server resource manager, which only sees
`data/`, can't see anything under `assets/`).

- Balance changes take effect just by editing the `.txt` file directly
  and running `/reload` (no restart needed)
- Falls back to a default (`WeaponStats.FALLBACK`) if the file can't be
  found
- `VehicleDefinition`'s own `weapons[]` records only `weapon_name` (which
  weapon file to read)
- `DisplayName` (the display name), `Round` (magazine size - 0 or unset
  means unlimited), and `ReloadTime` (reload time, in ticks) are also
  read. A weapon with a magazine loses one round per shot and
  automatically starts reloading once it hits 0 (tracked and synced
  server-side)

Weapon files can also be **bundled inside an addon mod's own jar** at
`assets/<namespace>/weapons/<name>.txt`. A loose file of the same name in
the addons folder takes priority, preserving that folder's role as an
override.

### Weapon switching and the fire key

- The fire key defaults to right-click
  (`key.tudursvehiclemod.fire_weapon`)
- The weapon-switch key (default `R`, `key.tudursvehiclemod.weapon_switch`)
  cycles the selection among the weapons assigned to your own current
  seat (like every other key binding, this can be changed from Controls)
- Weapon selection is a client-only concept - the server independently
  re-validates the weapon index sent along with each fire request
- Several `AddWeapon` entries referencing the same weapon file are
  **merged into a single logical weapon**, per MC Heli's own spec, cycling
  through their own firing positions (`offsets`) in declared order on
  each shot (for a twin- or multi-barrel gun, say) - they are never
  treated as separately selectable weapons

### HUD weapon information display

`WPN_NAME` (weapon name), `WPN_AMMO` (current magazine count),
`WPN_RM_AMMO` (magazine capacity), and `reloading` (currently reloading?)
are all supported, showing information for the currently selected
weapon.

A string-typed variable like `WPN_NAME` is implemented through a
dedicated mechanism (`HudExecutionContext.stringVariables`), since this
expression language otherwise only supports numeric (`double`) values -
it's specially resolved as a string only when referenced with the `%s`
format specifier AND its name matches a known string-variable identifier
exactly.

### Weapon types, aiming, and explosions (reproducing MC Heli behavior)

Implements behavior close to MC Heli's own `Type` directive:

- **MachineGun1/MachineGun2**: merged into `MachineGun` at conversion
  time. Whether it follows the view (MachineGun2) or stays fixed
  (MachineGun1) is decided **not by `Type`** but by whether `AddWeapon`
  specifies `MinYaw`/`MaxYaw`/`MinPitch`/`MaxPitch` (if given, it follows
  the player's own view within that range; if not, it uses `AddWeapon`'s
  own fixed yaw/pitch). This rule is shared across weapon types,
  regardless of type
- **Rocket**: same ballistics as MachineGun (straight-line, gravity per
  the weapon file). Mainly differs in explosion/incendiary handling on
  hit
- **Bomb**: on release, falls starting from the vehicle's own current
  velocity, then follows the weapon file's own gravity from there (no
  fixed initial velocity is used)
- **Explosions/incendiary**: supports the weapon file's own `Explosion`
  (power, 0 for none), `ExplosionBlock` (destroys blocks if above 0), and
  `Flaming` (ignites on hit; ineffective if `Explosion` is 0)
- **Torpedo**: can only be launched with level attitude at low altitude
  (low altitude judged by distance to the ground/water surface directly
  below). Starts by inheriting the vehicle's own velocity and falls per
  gravity, but gravity switches off the instant it touches water, after
  which it continues straight ahead at that same velocity (unguided)
- **ASMissile**: targets the point where the player's own line of sight
  meets the ground at the instant of firing (or, if nothing is there, the
  point at the line of sight's own maximum search distance), gradually
  turning toward it in flight (unaffected by gravity while guiding)
- **AAMissile/ATMissile**: the instant the fire key is pressed, it
  automatically locks onto whatever target is within the sight (near the
  center of view, within a configured angle) and tracks it (recalculating
  direction every tick if the target moves; continuing straight ahead if
  the target dies or disappears). AAMissile can only lock onto a target
  that is **currently airborne**, and ATMissile only onto one that is
  **currently on the ground or on the water's surface** (judged by the
  target's own current physical state, not its vehicle type - "airborne"
  means sufficiently far above the ground and not touching water,
  "underwater" means submerged (eye position below the water's surface);
  neither AAMissile nor ATMissile can lock either of those. Floating or
  standing on the water's surface still counts as "grounded" and remains
  a valid ATMissile target). Pressing the fire key does nothing until
  locking (keeping the same target within the sight for the duration set
  by `LockTime`) completes
- **Missile** (unique to this project, not an MC Heli type name): the
  same lock-on/guidance/fire-restriction behavior as AAMissile/ATMissile,
  but with no filtering by airborne/grounded/underwater target type at
  all
- **ASWeapon** (unique to this project, not an MC Heli type name - given
  a different name since `ASMissile` was already taken): an anti-
  submarine missile. Rather than AAMissile/ATMissile-style lock-on, it
  guides toward the aim point at the moment of firing, the same way
  ASMissile does (for an underwater target, this becomes a point near
  the sea floor beneath it). It keeps flying just above the water's
  surface toward the target's own horizontal coordinate until within a
  new field, `DiveDistance` (default 10.0) blocks of the target, at which
  point it begins diving toward the target itself. The instant it touches
  water, it switches to exactly the same underwater cruise system a
  torpedo (Torpedo) uses (reusing `AccelerationInWater`/`VelocityInWater`/
  `TargetDepth` as-is). Note that this doesn't implement the more complex,
  multi-stage behavior a real anti-submarine weapon has, such as warhead
  separation
- Parameters shared across every guided missile (turn speed, search
  range, lock-on angle) are currently fixed values - per-weapon-file
  tuning isn't supported
- MC Heli's own `MkRocket`/`Dispenser`/`Smoke`/`Dummy`/`TargetingPod` are
  also implemented. `CAS` (close air support - a weapon type that
  automatically scrambles and attacks with a support aircraft) is
  implemented as a separate mechanism incompatible with MC Heli
  (`WeaponType.CAS`) - see the "UAV / autonomous drone / close air
  support (CAS)" section for details

### Bullet model

When a weapon's own `bullet_model`/`bullet_texture`/`bullet_scale` are
set (auto-converted from MC Heli's own `ModelBullet`), the projectile is
drawn as an actual OBJ model rather than vanilla's own item display (item
display is used as before when these are unset). Model/texture/scale are
synced from server to client via `DataTracker` (an ordinary Java field
set only server-side would never reach the client-side entity spawned
from the spawn packet).

---

## Wake trail effect

While a ship, a surfaced submarine, a seaplane (an aircraft with
`Float = true`), or a car (while submerged within its own
`SubmersibleDamageHeight` range) moves across the water's surface, a
cluster of small square tiles is drawn at the bow (splitting into two
diverging bands) and the stern. This happens automatically for any
qualifying vehicle simply moving across water - no extra configuration
is needed.

**As a stand-in for an actual particle system**, this reuses the same
lightweight polygon-drawing machinery already used to draw the vehicle's
own body model, rather than real particles, so it looks natural
regardless of the ship's own scale.

The bow's two diverging tile bands spread outward in a straight line, at
a **fixed angle** from the hull's own heading at creation time (modeled
on the spread angle of the Kelvin wake a real ship produces), at a speed
proportional to the ship's own speed; once they reach their own maximum
width they stop moving and remain in place until they disappear. A
faster ship reaches that maximum width sooner, while a slower one
spreads more gradually or fades out before ever reaching it - so a
slower ship's own wake ends up looking comparatively narrow. The stern's
own tiles don't move sideways at all, remaining exactly where they were
recorded.

While turning, the inner tiles (on the side toward the turn's own
center) automatically get both a longer reach distance and a wider
spread angle, scaled to the current turn radius (a tighter radius
spreads further and at a steeper angle). This compensates for the inner
wake otherwise looking geometrically sparse during a turn - the same
compression a real ship's own wake shows in a turn.

Rather than a fixed offset, the creation position is computed dynamically
by slicing each model's own hit-detection mesh at the water's surface
height and finding the bow/stern intersection from the model's own
actual shape - so no per-model configuration is needed.

The following two fields are adjustable in the vehicle config file (both
optional):

- `wake_trail_spread_distance`: the maximum spread width (in blocks) the
  bow's own wake eventually reaches. **If omitted, this is measured
  automatically from the hull's own hit-detection mesh at the waterline**
  (so it scales naturally with no manual tuning, from a small boat up to
  a vessel over 200 blocks long). Only override this if the
  automatically measured value doesn't match your intent.
- `wake_trail_duration_ticks` (default `300`, 15 seconds): how long each
  wake point (tile), bow or stern alike, stays visible before
  disappearing.

---

## MCHeli conversion tool (`tools/mcheli_convert.py`)

Converts an MC Heli addon pack (`.txt` config plus `.obj` model) into
this project's own `VehicleDefinition` JSON format.

> **Important**: whether a given MCHeli addon pack may be converted and
> used at all depends on that pack's own author's terms. See
> `GUIDELINES.md`.

### Main supported features

- Automatic vehicle-type detection (distinguishes helicopter/car/ship/
  submarine/seaplane from the presence of `AddRotor`/`AddCrawlerTrack`
  and the strength of `Float`+`Gravity`)
- Merges part files (`<vehicle name>_<part name>.obj`) into the main
  `.obj`, emitted as BOTH `o` and `g` group declarations (since some 3D
  tools only recognize `g`)
- **Spinning parts** (`spinning_parts`): `AddRotor` (a helicopter's own
  rotor) / `AddBlade` (a propeller's own actual spinning part - not to be
  confused with `AddPartRotor`, which is a VTOL's own rotor-housing tilt,
  a different thing from continuous spinning) / `AddPartRotation`.
  `RotorSpeed` (defaults to 60 if unset) is interpreted as "revolutions
  per second at full throttle," and spin speed actually varies with
  throttle
- **Toggle parts** (`toggle_parts`): hatches/canopies (`AddPartHatch`/
  `AddPartSlideHatch`/`AddPartCanopy`/`AddPartSlideCanopy`, opened/closed
  with H), landing gear (`AddPartLG`/`AddPartLGRev`/`AddPartSlideRotLG`,
  automatic deployment plus manual retraction). Several directive kinds
  within the same naming family (e.g. a mix of `AddPartLG` and
  `AddPartSlideRotLG`) are numbered based on their own actual order of
  appearance in the file, so naming never collides
- **Y-coordinate offset**: for anything other than ships/seaplanes, since
  an OBJ Y coordinate of -0.30 corresponds to Minecraft's own ground
  level, +0.30 is added during conversion (applied to the mesh, seats,
  weapons, and every other part's own pivot coordinates; not applied to a
  sliding part's own travel distance)
- **Seat position correction**: an additional adjustment (-0.5) to close
  a visual gap with MC Heli's own placement
- **CameraPosition**: carries the riding camera position over from MC
  Heli's own `CameraPosition`/`AddGunnerSeat` camera coordinates
  (switchable with a dedicated key if several CameraPosition entries are
  configured)
- **`OnGroundPitch`**: read case-insensitively (since MC Heli itself is
  case-insensitive, every field name in the config file is normalized to
  lowercase for comparison)
- **HUD**: reads the vehicle config file's own `HUD = <name>`, carrying it
  into the generated JSON's own `"hud"` (falls back to a default HUD per
  vehicle type if unset). Automatically copies
  `assets/mcheli/hud/*.txt`/`assets/mcheli/textures/gui/*.png` into the
  output pack, and appends display support for
  `manual_mode`/`stalling`/`gear_deployed`/`speed_kbh` to **every** HUD
  file it copies (since each vehicle only ever uses one HUD - either its
  own or its type's own default - duplicate display isn't really a
  practical problem; re-running the conversion doesn't append duplicates
  into the same file either). Place files manually or edit the script
  yourself if you'd rather it not append this
- **Sound**: reads the vehicle's own `Sound = <name>` (engine sound) and a
  weapon's own `Sound`/`SoundVolume`/`SoundPitch`/`SoundPitchRandom`/
  `SoundDelay` (firing sound; `Sound` defaults to `<weapon name>_snd` if
  unset), carrying them into the generated JSON. Automatically copies
  `assets/mcheli/sound/*.ogg`/`assets/mcheli/sounds/*.ogg` into the
  output pack
- **Weapons**: only carries seat, offset, and `weapon_name` (the weapon
  file name) from `AddWeapon` into the JSON - actual values such as
  damage, velocity, fire interval, gravity, sound, and bullet model are
  **never baked in**. Copies `assets/mcheli/weapons/*.txt` into the
  output pack, and **reads that file directly at runtime** (as described
  above). This means balance tuning after conversion is just a matter of
  editing the original `.txt` file directly (`/reload` applies it) - no
  re-conversion needed. Falls back to a default if the file can't be
  found (`WeaponStats.FALLBACK`)
- **Bullet model**: reads a weapon's own `ModelBullet = <name>`, relocating
  `assets/mcheli/models/bullets/*.obj`/`assets/mcheli/textures/bullets/*.png`
  into `models/obj/`/`textures/vehicle/` with a `bullet_` prefix (since
  this project's own model/texture loading only scans fixed paths)

### Unsupported features

- `AddRepellingHook` (a hook that drops mobs from a helicopter at a set
  interval)

Also, the following are MC Heli's own features that this mod's own
design deliberately treats as **incompatible**, rather than supporting:

- `AddRack`/`RideRack` (a "rack" feature for mounting one vehicle on
  another) and `ExclusionSeat` (mutual exclusion between a seat and a
  rack) - this mod substitutes its own runway (`runways`) feature for
  carrying and following another vehicle
- `AddParticleSplash`/`EnableSeaSurfaceParticle` (splash particles while
  moving on the water) - this mod substitutes its own wake-trail
  generation feature
- Terrain-following tilt settings (`OnGroundPitchFactor`/
  `OnGroundRollFactor`/`SetWheelPos`) - the terrain-following tilt
  feature itself IS implemented, but with a different calculation method
  from MC Heli's own, so these specific settings themselves have no
  compatibility
- Gunner mode (`EnableGunnerMode`) and `ConcurrentGunnerMode` - this mod
  doesn't implement gunner mode itself (the pilot borrowing the second
  seat's own view/weapon), so these are treated as incompatible

Note: `CameraRotationSpeed` (a camera rotation-speed limit for something
like a tank turret) is fully supported, both in conversion and as a
substitute feature.

Note: `DefaultFreelook` (free look from the moment of boarding) is
implemented. However, as a design choice unique to this mod, **its own
behavior is reversed from ordinary free look**: a vehicle with
`default_freelook` enabled starts in free-look mode by default, and only
returns to the normal fixed view while the release key is held.

---

## Building

```bash
./gradlew build
```

The jar lands in `build/libs/`.

To make the mod available to an addon mod, publish it locally first:

```bash
./gradlew publishToMavenLocal
```

---

## On versions (important)

- Minecraft 1.21.11 is **the last version Fabric updates Yarn mappings
  for** (a move to Mojang's own official mappings is recommended from
  here on)
- The `yarn_mappings`/`fabric_version`/`loader_version` values in
  `gradle.properties` are current as of writing. Check
  https://fabricmc.net/develop/ for the latest build numbers before
  building.

The Minecraft, Fabric Loader, and Yarn mapping versions in
`gradle.properties` must also match between this mod and any addon mod
built against it. Mismatched Yarn mappings in particular give the same
class or method a different name, so the addon compiles but fails to
resolve at runtime.
