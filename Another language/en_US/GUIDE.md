# Tudur's Vehicle Mod - Manual

Separate from `README.md` (the developer-facing architecture reference),
this is a concise summary of what the mod does, how to build an addon
pack, and how to convert an MCHeli addon. For implementation detail and
the list of unsupported features, see `README.md`.

> **Important**: Before building an addon pack, read `GUIDELINES.md`
> regarding the handling of models and data that other people hold the
> rights to. Converting an MCHeli addon pack is also subject to
> restrictions.

## 1. What this mod can do

- **Vehicles**: five movement models are supported - helicopter, car,
  ship, submarine, and fixed-wing aircraft. You add a new vehicle just by
  adding a JSON definition plus an OBJ model (no code changes needed).
- **Weapons**: machine guns, rockets, bombs, torpedoes, and guided
  missiles (anti-ground/anti-air/anti-tank). Ammo count (magazine),
  heat-based (overheat), or both together are all supported.
- **Health/destruction**: per-vehicle HP, armor factor, a destruction
  effect (charring plus black smoke, damage to occupants), and heavy
  damage from a steep-angle block collision or a high-speed entity
  collision.
- **Fuel**: per-vehicle fuel tank and consumption rate (throttle-
  dependent, with an easing curve). Running out pins throttle to 0
  (piloting itself remains possible).
- **Resupply**: from the vehicle menu you can open while riding, you can
  resupply ammo (consuming items), refuel (by inserting a fuel can), and
  use the vehicle's own inventory (when configured).
- **Fuel cans and the refiner**: craft a fuel can from iron ingots and a
  refiner from a piston plus 8 furnaces, convert fuel items into fuel and
  into a can, and supply a vehicle from that can. A creative-only
  infinite fuel can is also provided.
- **HUD**: MCHeli-format HUD scripts (`assets/.../hud/*.txt`) are parsed
  and drawn as-is. Variables for health, fuel, ammo, heat, and more are
  supported.
- **Carrier**: a runway can be set on any vehicle, regardless of type,
  and a dedicated weapon can launch carrier aircraft that fly a patrol/
  attack route automatically and then return and land. See the "Carrier
  system" section of `README.md` for details.
- **UAV / autonomous drone / CAS**: with a registration stick plus a
  dedicated block, a vehicle can be operated as an unmanned remotely-
  piloted aircraft (UAV) or as autonomous AI flight (TargetDrone), and
  close air support (CAS) that automatically scrambles a support aircraft
  from a weapon is also supported. See the "UAV / autonomous drone /
  close air support (CAS)" section of `README.md` for details.

## 2. How to build an addon pack (a new vehicle)

Adding a new vehicle needs no code changes. Prepare the following.

1. **Model**: `assets/<namespace>/models/obj/<name>.obj` (OBJ format;
   triangles, quads, and n-gons are all accepted)
2. **Texture**: `assets/<namespace>/textures/vehicle/<name>.png`
3. **Data definition**: `data/<namespace>/vehicles/<name>.json`
   - `entity_type`: the movement model (one of `tudursvehiclemod:car` /
     `helicopter` / `ship` / `submarine` / `aircraft`)
   - `model` / `texture`: references to the files from 1 and 2 above
   - Assorted parameters - speed, hitbox, seats, weapons, health, fuel,
     and so on
   - (Optional) `weapons` for armament, `spawn_item` for a spawner item,
     `hud` to specify a HUD
4. `/reload` applies it in-game. No restart needed.

**For the full list of every configurable field, see the pack-author
documentation below** (this section is only an overview):

- `Readme_Addon.md` — where an addon pack goes, its directory structure,
  and naming rules
- `Readme_Vehicle.md` / `Readme_Vehicle_<Type>.md` — every vehicle field
  (common fields plus per-type fields)
- `Readme_Weapon.md` / `Readme_Weapon_<Type>.md` — every weapon field
  (common fields plus per-type fields)
- `Readme_HUD.txt` — the HUD script format

Also see the existing `data/tudursvehiclemod/vehicles/*.json` samples.

## 3. How to convert an MCHeli addon

A tool for automatically converting an existing MCHeli addon pack into
this mod's own format is bundled. The command-line and GUI versions run
the same conversion, so the result is identical.

> **Important**: Whether an MCHeli addon pack may be converted and used
> at all depends on that pack's own author's terms. See `GUIDELINES.md`
> before converting anything.

### GUI version

```bash
python3 tools/mcheli_convert_gui.py
```

A simple window opens with folder pickers and a progress log. Point
"source" at the MCHeli addon pack folder, "destination" at an output
folder, and press convert.

### Command-line version

```bash
python3 tools/mcheli_convert.py <MCHeli addon path> <output path> [--namespace NAMESPACE] [--no-auto-detect]
```

- `--namespace`: defaults to `mcheliport`
- `--no-auto-detect`: disables automatic detection of the vehicle kind
  (helicopter/aircraft/car/ship/submarine), always converting strictly
  according to the original folder name (helicopters/planes/tanks/
  vehicles). Auto-detection works from the presence of `AddRotor` /
  `AddCrawlerTrack`, and from the `Float` / `Gravity` values inside the
  `planes` folder to tell a ship, submarine, or seaplane apart - use this
  option if it produces an unintended result.

### Common behavior

- Reads vehicle config files (`.txt`), models (`.obj` / `.mqo`),
  textures, weapon configs, HUD scripts, and sounds, and converts them
  into this mod's own format (JSON plus assets).
- Weapon values such as damage, projectile speed, and ammo count are not
  baked in at conversion time - the original weapon config file (`.txt`)
  is still read directly afterwards. Balance adjustments therefore just
  need an edit to that `.txt` plus a `/reload`; no re-conversion.
- Some features are currently unsupported. See the "Unsupported features"
  section of `README.md` for details.
- Place the converted output folder into `resourcepacks` or the
  corresponding location, the same as any ordinary resource/data pack.

## 4. Notes

- This mod itself bundles no MCHeli assets whatsoever (no models,
  textures, sounds, or vehicle/weapon configs). The conversion tool only
  reads an addon pack that the user has obtained separately - and only
  where that pack's own terms permit it (see `GUIDELINES.md`).
- Verified on: Minecraft 1.21.11 / Fabric
