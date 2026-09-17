# Readme_cfg.md - Config settings

Files covered:
- `config/tudursvehiclemod.json` (client settings)
- `config/tudursvehiclemod-server.json` (server settings)

Both are JSON, and are generated automatically with default values on
first launch if they don't exist. Changing a setting from the in-game
settings screen saves to the file immediately, so there's normally no
need to edit these files by hand.

## Opening the settings in-game

While riding any vehicle, press the "open vehicle menu" key, then press
the "Debug" button inside that menu to open the debug menu. From there
you'll find entries for "Settings" (client settings) and "Server
settings".

"Server settings" can only be operated on a world you host yourself
(singleplayer, or a LAN game you opened). The settings of a remote
dedicated server can't be changed directly from this screen.

---

## Contents

1. About when a change takes effect
2. Client settings (`tudursvehiclemod.json`)
3. Server settings (`tudursvehiclemod-server.json`)
4. Settings that don't appear on the settings screen

---

## 1. About when a change takes effect

When a change takes effect varies by setting.

- **Immediate**: a setting re-read every frame or every tick. The effect
  changes the moment you press the button.
- **On reload**: a setting whose effect changes at a resource reload
  (which the debug menu's own reload button can trigger immediately).
  These are the ones tied to texture loading.
- **Restart required**: a setting tied to machinery built only once per
  game session. After changing it, nothing visibly changes until you
  restart the game.

Each entry below states which of the three it is.

---

## 2. Client settings (`tudursvehiclemod.json`)

### Translucency mode (`translucencyMode`) - restart required

How a vehicle's own transparent parts (windows, optics, etc.) are drawn.
Default is `dither_texture`.

| Value | Display name | Notes |
|---|---|---|
| `dither_shader` | Dither (shader) | Dithering via this mod's own shader. Can't be combined with a shader pack (the pack replaces the drawing program with its own). |
| `dither_texture` (default) | Dither (texture) | Bakes the dither pattern into the texture at load time. Works with shader packs, and handles depth ordering against water and entities correctly. |
| `translucent` | Alpha blending | Straightforward alpha blending using vanilla's own translucency pipeline. Works with shader packs, but being draw-order dependent, water can end up hidden behind a transparent part. |

### Searchlight (`searchLightMode`) - lighting immediate, shape may need a restart

Switches both the searchlight's own lighting method and its visible shape
with one setting. Default is `beam`.

| Value | Display name | Notes |
|---|---|---|
| `beam` (default) | Beam | A closed translucent cone (MC Heli's own original shape). Lights entities only. |
| `dynamic_light` | Dynamic light (experimental) | Looks the same as Beam, but terrain is actually re-lit too (an experimental feature involving chunk rebuilds). |
| `blades` | Blades | Same lighting as Beam, drawn as several blades extending around the circumference. Displays correctly under any translucency mode. |
| `bands` | Bands | Same lighting as Beam, drawn as rings arranged along its length. |
| `dashes` | Dashes | Blades further cut into shorter segments, giving a dashed appearance. |

### Searchlight brightness (`searchLightBrightness`) - immediate

- Range: 0 - 6 (default: 3.0, in steps of 0.5; steps of 1.0 above 3.0)
- A multiplier on how brightly a lit searchlight illuminates blocks and
  entities. It scales the intensity configured on each light rather than
  replacing it. At 0, lighting is disabled and only the visible beam
  remains.

### Block overlay brightness (`searchLightBlockBrightness`) - immediate

- Range: 0 - 3 (default: 1.0, in steps of 0.5)
- How strongly a searchlight tints terrain. Independent of searchlight
  brightness (which controls entity lighting). At 0, only the terrain
  drawing is disabled; entity lighting remains.

### Block overlay sample count (`searchLightBlockOverlaySampleCount`) - immediate

- Range: 50 - 400 (default: 200, in steps of 50)
- How many samples a searchlight fires per tick to determine which parts
  of the terrain are lit. A fixed value, unrelated to the light's own
  shape or range. Fewer is lighter, and the small gaps at adjoining faces
  that coarse sampling produces are filled in by the diffusion pass.

### Block overlay update interval (`searchLightBlockOverlayIntervalTicks`) - immediate

- Range: 1 - 10 ticks (default: 4)
- How often the terrain drawing is fully recalculated. This is the part
  of searchlight processing that consumes the most temporary memory, so
  raising the value reduces memory use proportionally - at the cost of
  the drawing lagging slightly behind a quickly-moving beam. The lighting
  itself is always updated every tick regardless of this setting.

### Cone display distance (`searchLightConeDisplayDistance`) - immediate

- Range: 0.1 - 1.0 (default: 1.0, in steps of 0.1)
- How far the visible shape (cone/blades/bands/dashes) reaches, as a
  fraction of the light's own length. Affects only what is drawn; the
  lighting and block overlay ranges don't change.

### Chunk rebuild interval (`searchLightChunkRebuildIntervalTicks`) - immediate

- Range: 1 - 40 ticks (default: 10)
- Used only when the searchlight mode is "Dynamic light". How often
  terrain re-lighting is requested as the beam moves. Lower is smoother
  but heavier.

### Ring count multiplier (`searchLightRingCountMultiplier`) - immediate

- The settings screen cycles between 0.5, 1.0, 2.0, and 3.0 (default: 1.0)
- A multiplier on how many rings a blades/bands/dashes beam generates
  along its length, applied on top of the automatic size-based
  adjustment. There's no upper bound in the code, so edit the file
  directly to go beyond the values the screen cycles through.

### Blade count multiplier (`searchLightBladeCountMultiplier`) - immediate

- The settings screen cycles between 0.5, 1.0, 2.0, and 3.0 (default: 1.0)
- A multiplier on how many blades a blades/dashes beam generates around
  its circumference. As with the ring multiplier, there's no upper bound
  and the file can be edited directly for higher values.

### Dither upscale (`translucencyDitherUpscale`) - on reload

- Possible values: 0.25 / 0.5 / 1 / 2 / 4 / 8 (default: 4.0)
- The texture upscale factor applied before dithering. Above 1 gives a
  finer pattern in exchange for memory; below 1 downscales to save
  memory. Only meaningful when the translucency mode is "Dither
  (texture)".

### GPU texture compression, BC1 (`translucencyDitherGpuCompression`) - on reload

- Default: `false`
- Keeps the dithered texture in BC1, cutting VRAM use to 1/8. Alpha is
  preserved exactly, while color detail becomes approximate. It operates
  outside Minecraft's own texture API, so it falls back to the
  conventional path automatically on failure. Only meaningful when the
  translucency mode is "Dither (texture)".

### Dither max texture size (`translucencyDitherMaxTextureSize`) - on reload

- Range: 512 - 8192 px (default: 2048, cycling by doubling)
- An upper bound on the upscaled texture size, which caps the dither
  upscale factor. Only meaningful when the translucency mode is "Dither
  (texture)".

### Experimental triangle rendering (`experimentalTriangleRendering`) - restart required

- Default: `false`
- An experimental drawing path that submits 3 vertices per triangle
  rather than 4. Only meaningful when the translucency mode is "Dither
  (shader)".

### Match chunk view distance (`matchChunkViewDistanceForEntityRender`) - immediate

- Default: `true`
- Makes this mod's own entities (vehicles, projectiles) use the game's
  own render distance (chunk view distance) rather than the shorter
  entity render distance.

### Disable render culling (`disableEntityRenderCulling`) - immediate

- Default: `true`
- Skips this renderer's own distance and frustum checks, always drawing a
  vehicle as long as it's loaded.

### Third-person HUD (`showHudInThirdPerson`) - immediate

- Default: `true`
- Whether the vehicle HUD is drawn in third person too. In first person
  it's always drawn regardless of this setting.

### Max aircraft camera distance (`maxAircraftCameraDistance`) - immediate

- Range: 20 - 160 blocks (default: 80, in steps of 20)
- How far the third-person camera can zoom out while flying.

---

## 3. Server settings (`tudursvehiclemod-server.json`)

Changes made on the "Server settings" screen take effect immediately,
with no restart.

### Parallel hit detection (`parallelHitDetectionAcrossVehicles`) - immediate

- Default: `false`
- Batches this mod's own hit detection for every vehicle into a single
  parallel pass once per tick, instead of running it inline inside each
  vehicle's own tick. Turn this on for a world with many high-polygon
  vehicles where hit detection is clearly the bottleneck.

### Minimum vehicles for parallel hit detection (`parallelHitDetectionMinimumVehicles`) - immediate

- Range: 1 - 20 (default: 3)
- How many vehicles must need hit detection in a tick before the parallel
  pass above is worth using. Only meaningful when parallel hit detection
  is on.

### Destroyed vehicle despawn (`destroyedVehicleDespawnEnabled`) - immediate

- Default: `true`
- Makes a destroyed vehicle despawn automatically after the timer below,
  instead of remaining in the world as wreckage forever. Has no effect
  whatsoever on a vehicle that's still drivable.

### Time before a destroyed vehicle despawns (`destroyedVehicleDespawnSeconds`) - immediate

- Range: 30 - 1800 seconds (default: 300 = 5 minutes)
- How long a destroyed vehicle stays in the world before despawning. Only
  meaningful when destroyed vehicle despawn is on.

### Carrier runway expected width (`carrierRunwayExpectedWidth`) - immediate (newly generated runway tiles only)

- Range: 10 - 60 blocks (default: 30.0)
- The assumed width used to size a carrier runway's own support tiles at
  mod load time, before the actual runway width from a vehicle file is
  known. If your actual runway width differs from the default, set this
  to match. Existing tiles keep the size they were already fixed at, so a
  change affects only newly generated tiles.

### Projectile forced-chunk limit (`projectileForcedChunkLimit`) - immediate

- Possible values: disabled (0) / 50 - 1000 (default: 300)
- A server-wide cap on the total number of chunks that in-flight
  projectiles force-load, which is what lets a projectile flying far from
  any player keep going until impact. Past the cap, the
  oldest-summoned projectile is released first. Disabled (0) means
  projectiles never force-load chunks at all. This doesn't affect
  CAS/Carrier aircraft or other always-loaded features (those are tuned
  on their own weapon/vehicle files).

---

## 4. Settings that don't appear on the settings screen

These exist in the config files but aren't shown on the in-game settings
screen, so changing them requires editing the file directly.

### `searchLightBlockOverlayRange` (client settings)

- Default: `50.0` (blocks)
- How far from a searchlight terrain is tinted with its color. The effect
  fades gradually toward this distance (it never cuts off abruptly). If
  the light's own length is shorter than this, it's truncated at that
  length. Cost scales with the cube of this block count, making it the
  single most performance-relevant setting in this whole feature. Lower
  it on weaker hardware, raise it if you have headroom.

### `worldDataCleanupChunksPerTick` (server settings)

- Default: `-1` (no count limit)
- An upper bound on how many candidate chunks `/tvm clean -d` (the
  command that fully removes vehicles across the world, including every
  unloaded chunk) examines per tick. At -1 (the default) no count-based
  limit is applied - which does **not** mean the work finishes in one
  tick. The processing time itself is always separately limited, and
  progress is logged periodically. Setting a positive value (50, say)
  adds a chunk-count cap on top of that time limit, smoothing the
  per-tick load at the cost of taking longer to finish overall.
