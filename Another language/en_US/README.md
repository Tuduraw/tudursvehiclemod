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
   park on

Only when you want to **change the control logic itself** do you need a
new Java class extending `AbstractVehicleEntity` (implementing
`updateVehicleMovement`).

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
- **Car/ship**: A/D steer directly
- **Aircraft/helicopter**: the nose (yaw + pitch) follows the pilot's own
  view direction; A/D become a visual bank
- **Submarine**: view input never affects the vehicle at all (always
  equivalent to free-look)
- **Packing away**: sneak + right-click while nobody is aboard
- **HUD**: a throttle bar and the actual speed (in **kb/h**, blocks
  travelled per hour with 1 kb = 1,000 blocks) are always shown

### Key bindings (defaults; all rebindable in Controls)

| Key | Function |
|---|---|
| Right click | Fire weapon |
| R | Switch between the weapons assigned to your seat |
| X | Switch the selected weapon's own mode (`ModeNum`) |
| Y | Switch seat (next) |
| T | Switch seat (previous) |
| Alt+Y | Switch between a carrier-launched aircraft and the launching carrier |
| Left Alt | Free look (view stops following the vehicle while held) |
| Left Shift | Descend (helicopter/VTOL; sneak is taken by dismounting) |
| V | Switch a VTOL between helicopter and aircraft mode |
| H | Open/close hatches and canopies |
| G | Manually retract/extend landing gear |
| N | Fold/extend the main wings |
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

## Aircraft (AircraftEntity)

Attitude is managed as a **quaternion** rather than Euler angles, so there
is no gimbal lock and inverted flight behaves correctly.

Key behaviors:

- **Flight physics**: lift/drag/gravity are applied from the current
  attitude and speed, so the aircraft sinks as it slows
- **Unmanned**: with no pilot, throttle eases back toward 0
- **`OnGroundPitch`**: a distinct resting attitude on the ground (a
  taildragger sits nose-up, etc.)
- **Stall**: below a threshold speed the nose drops and control authority
  falls away
- **Dive acceleration / climb deceleration**: speed rises in a dive and
  falls in a climb, on top of the throttle value
- **Manual mode** (hold M): disables auto-levelling
- **Speed display**, **HUD**, **landing gear**, **hatch/canopy**,
  **third-person player model linkage**, and **camera** each have their
  own handling - see the Japanese `README.md` for the full detail

---

## Submarine (SubmarineEntity)

A fully key-driven vehicle where mouse input never affects the vehicle;
the view is always free (as with aircraft free-look).

- **Surface/submerged toggle**: H (shared with the hatch/canopy key).
  Submerging closes hatches, surfacing opens them; it spawns on the
  surface with hatches open
- **Surface mode**: handles exactly like a ship, with two differences -
  it banks *into* a turn (its center of mass is below the waterline,
  unlike a ship's), and buoyancy is a continuous spring toward just below
  the searched water surface rather than a ship's own three-step discrete
  gravity switch (the discrete version oscillated near the surface)
- **Submerged mode**: W/S throttle, A/D yaw, Space nose-up, Left Shift
  nose-down, Up/Down arrows ascend/descend without changing attitude.
  Pitch auto-levels after 5 seconds of no input (disable with manual
  mode). Roll always returns to 0 underwater
- While submerged, weapon part aiming freezes and firing is disabled.
  Only torpedoes (`Type=Torpedo`) work, unless a weapon config sets
  `UsableWhileDiving = true`
- **Throttle limits**: forward to 100%, reverse to -20%, and reversing
  direction requires 2 seconds at 0%
- While underwater, nothing but input moves the vehicle vertically
  (gravity and buoyancy are fully disabled). Ordinary gravity returns only
  once fully out of the water

---

## UAV / autonomous drone / close air support (CAS)

Three independent mechanisms for operating a vehicle without a player
aboard.

- **UAV**: register the vehicle's UUID with the Drone Control Stick,
  insert that stick into a Station block, and pilot it remotely from the
  station
- **TargetDrone**: autonomous AI flight managed by the Drone Center block
  - circular orbits or waypoint patrol, with automatic landing at a Home
  Point. Waypoints can be recorded by flying the route with the Drone
  Route Book
- **CAS** (`WeaponType.CAS`): a weapon that automatically scrambles a
  support aircraft, which flies an attack route near a distant target

---

## Carrier system

Lets a vehicle function as a carrier, managing launch, automatic flight,
and return of carrier aircraft. **This isn't limited to any particular
vehicle type** - the implementation has no `entity_type` dependency at
all, so a fictional "flying aircraft carrier" works just as well.

### Runway (`runway`/`runways`)

**A runway and the `Type = CARRIER` weapon aren't required by each
other** - either works alone. A runway with no weapon is simply a deck;
a weapon with no runway recovers its aircraft by position check instead.
Combining both, so a launched aircraft actually lands on the runway, is
the originally-intended basic form of a carrier.

A `runway` object (one) or `runways` array (several decks) on the
carrying vehicle's own JSON defines a rectangular runway:

```json
"runway": {
  "width": 30.0,
  "center_x": 0.0,
  "height_y": 12.0,
  "start_z": -105.0,
  "end_z": 90.0
}
```

- All coordinates are relative to the carrier's own entity position and
  rotate with its own heading
- Realized as a set of `CarrierRunwayPlatformEntity` (invisible floor
  tiles) covering the area, so landing gear, stall detection, and
  collision all behave as if real blocks were there. Tile size is derived
  from the `carrierRunwayExpectedWidth` server setting (default 30.0)
- A vehicle with no runway defined never uses this feature at all
- Hatch-linked optional fields (`hatch_gated`, `hatch_offset_x/y/z`,
  `hatch_move_speed`) let a runway exist only while a hatch is open, or
  ease its position as the hatch opens - for a landing craft's bow ramp or
  a carrier's elevator deck. See `Readme_Vehicle.md` for the full field
  list

### The carrier-aircraft-launching weapon (`WeaponType.CARRIER`)

Setting a weapon's type to `CARRIER` in its config file (`.txt`) makes it
spawn a carrier aircraft (another vehicle definition) instead of firing a
projectile, and fly it along a configured route. **This works regardless
of whether a runway is defined.**

Main behaviors: launch along `CarrierLaunchWaypoint`, return along
`CarrierLandingWaypoint`, and automatic conversion back into ammo (+1)
once the aircraft is inside `CarrierLandingToAmmoRadius` or a
`CarrierRecoveryPoint` zone. That check is purely position/velocity based
and does not require touching a runway tile. See `Readme_Weapon_Cas.md`
for every field.

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

- **Weapons**: one-shot playback at the weapon's own world position per
  shot. `sound_delay_ticks` is the interval at which sound actually
  plays, managed separately from the fire rate
- **Vehicles**: `engine_sound` loops continuously, with volume and pitch
  varying with throttle
- Files live at `assets/<namespace>/sound(s)/<name>.ogg` (both folder
  spellings are searched), including inside loose folders such as
  `tudursvehiclemod-addons`
- Internally this bypasses Minecraft's own sound system (no `sounds.json`
  registration): OGG files are decoded directly and uploaded to OpenAL
  buffers, because files in a loose folder aren't visible to
  `ResourceManager`
- Weapon fire sounds are sent from the server to every player tracking
  that vehicle via a dedicated packet

### Weapon stats (loaded at runtime)

A weapon's damage, velocity, fire interval, gravity, sound, and projectile
model are **not baked into the vehicle JSON** - they're read directly from
`assets/<namespace>/weapons/<weapon_name>.txt` (MCHeli format, unchanged)
on every shot (`asset.WeaponStatsLoader`), the same way HUD scripts work.
Balance changes need only an edit plus `/reload`.

Weapon files can also be **bundled inside an addon mod's own jar** at
`assets/<namespace>/weapons/<name>.txt`. A loose file of the same name in
the addons folder takes priority, preserving that folder's role as an
override.

### Weapon types, aiming, and explosions (reproducing MCHeli behavior)

- **MachineGun1/MachineGun2**: merged into `MachineGun` at conversion
  time. Whether it follows the view or stays fixed is decided **not by
  `Type`** but by whether `AddWeapon` specifies
  `MinYaw`/`MaxYaw`/`MinPitch`/`MaxPitch`. This rule is shared across
  weapon types
- **Rocket**: same ballistics as MachineGun; differs mainly in explosion
  and incendiary handling on hit
- **Bomb**: can only be released when the vehicle's own pitch and roll are
  within a set angle of level. It inherits the vehicle's own current
  velocity and then falls per the weapon file's own gravity
- **Torpedo**: like a bomb, requires level attitude and low altitude.
  Gravity switches off the instant it enters water
- **Explosions**: `Explosion` (power, 0 for none), `ExplosionBlock`
  (>0 destroys blocks), and `Flaming` (ignites on hit; ineffective when
  `Explosion` is 0) are all supported

---

## Wake trail effect

A surface-water wake drawn behind ships and other surface vehicles, with
spread width auto-detected from the hull's own beam at the waterline
unless `wake_trail_spread_distance` overrides it.

---

## MCHeli conversion tool (`tools/mcheli_convert.py`)

Converts an existing MCHeli addon pack into this project's own format.
A GUI version (`tools/mcheli_convert_gui.py`) runs the same conversion.

> **Important**: whether a given MCHeli addon pack may be converted and
> used at all depends on that pack's own author's terms. See
> `GUIDELINES.md`.

Supported: vehicle configs (`.txt`), models (`.obj`/`.mqo`), textures,
weapon configs, HUD scripts, and sounds. Weapon values are not baked in -
the original `.txt` is still read at runtime after conversion.

For the list of unsupported features, see the Japanese `README.md`.

**This mod bundles no MCHeli assets whatsoever.** The tool only reads a
pack the user has obtained separately.

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

The Minecraft, Fabric Loader, and Yarn mapping versions in
`gradle.properties` must match between this mod and any addon mod built
against it. Mismatched Yarn mappings in particular give the same class or
method a different name, so the addon compiles but fails to resolve at
runtime.
