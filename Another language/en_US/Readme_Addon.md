# Readme_Addon.md - Placing addon packs

A summary of the directory structure, required files, and naming rules
for an addon pack that adds new vehicles/weapons to this mod. No code
changes or rebuild are needed at all.

> **Important**: regarding models, textures, and sounds you include in a
> pack, read `GUIDELINES.md` on handling data that other people hold the
> rights to.

---

## 1. Where to place it

An addon pack can be placed using either of the following methods.

### Method A: dedicated addon folder (recommended)

Place it, as a plain (uncompressed) folder structure, at:

```
<game directory>/tudursvehiclemod-addons/<any addon name>/
```

`<any addon name>` can be anything you like (if you have several addons
coexisting, give each its own separately-named subfolder). This folder is
created automatically the first time the mod starts.

### Method B: an ordinary resource pack/data pack

The same internal structure as above can also be placed as an ordinary
resource pack/data pack, under Minecraft's own standard `resourcepacks`
folder (for the `assets/` side) or a world's own `datapacks`/
`resourcepacks` folder (for the `data/` side). In this case, you need to
enable it through Minecraft's own resource pack activation step.

**With the dedicated addon folder (Method A), no activation step is
needed at all - it's loaded automatically the moment you place it in the
folder.**

---

## 2. Directory structure

```
tudursvehiclemod-addons/
└── <addon name>/
    ├── data/
    │   └── <namespace>/
    │       └── vehicles/
    │           └── <vehicle name>.json      … vehicle definition (JSON)
    └── assets/
        └── <namespace>/
            ├── weapons/
            │   └── <weapon name>.txt        … weapon config (key=value format)
            ├── models/
            │   └── obj/
            │       └── <vehicle name>.obj    … 3D model (conventional path)
            ├── textures/
            │   ├── vehicle/
            │   │   └── <vehicle name>.png    … vehicle texture (conventional path)
            │   └── gui/
            │       └── <image name>.png      … HUD texture
            ├── hud/
            │   └── <HUD name>.txt            … HUD script
            ├── sound/ or sounds/
            │   └── <sound name>.ogg          … sound effects/engine sound
            └── lang/
                └── <language code>.json      … translations (optional, standard vanilla format)
```

`<namespace>` can be any namespace name (ASCII letters/digits/underscore
only, conventionally lowercase - this mod itself uses
`tudursvehiclemod`). Multiple addons can share the same namespace, but
if file names collide, whichever loads later overwrites the other, so
using a dedicated namespace for your own addon is recommended.

---

## 3. What each file does

### Vehicle definition (`data/<namespace>/vehicles/*.json`)

One JSON file per vehicle. The file name (without extension) directly
becomes this vehicle's own identifier (`<namespace>:<file name>`, or
`<namespace>:<subfolder>/<file name>` if you use a subfolder). For field
details, see `Readme_Vehicle.md` / `Readme_Vehicle_<Type>.md`.

### Weapon config (`assets/<namespace>/weapons/*.txt`)

One text file per weapon. The file name (without extension,
case-insensitive) is what gets referenced from `weapon_name` in a
vehicle JSON's own `weapons` array. For field details, see
`Readme_Weapon.md` / `Readme_Weapon_<Type>.md`.

### Model (`assets/<namespace>/models/...`)

OBJ format (triangle, quad, or n-gon faces are all fine). The path can be
freely specified in the vehicle JSON's own `model` field, but the
conventional location is `models/obj/<name>.obj`.

### Texture (`assets/<namespace>/textures/...`)

PNG format. The path can be freely specified in the vehicle JSON's own
`texture` field, or in a weapon's own `ModelBullet`-related settings, but
the conventional location is `textures/vehicle/<name>.png`. When a
weapon's own `ModelBullet` is set, its corresponding texture is
automatically looked up at
`textures/vehicle/bullet_<the ModelBullet value>.png` (see
`Readme_Weapon.md`).

### Sound (`assets/<namespace>/sound/` or `sounds/`)

OGG format (`.ogg`). **Both the singular `sound` and plural `sounds`
folder names are recognized** (subfolders are fine too). The file name
(without extension, case-insensitive) is what gets referenced from a
vehicle JSON's own `engine_sound`, or a weapon's own `Sound` field.
**Vanilla's own `sounds.json` is not needed** (this mod uses its own
independent sound-loading mechanism - simply placing a `.ogg` file makes
it immediately referenceable by file name alone).

### HUD script (`assets/<namespace>/hud/*.txt`)

An MC Heli-format HUD drawing script. The file name (without extension)
is what gets referenced from a vehicle JSON's own `hud` field. See the
included `Readme_HUD.txt` for the exact syntax.

### HUD texture (`assets/<namespace>/textures/gui/*.png`)

An image referenced by file name (without extension) from a HUD script's
own `DrawTexture` command. 256×256 is recommended.

### Translations (`assets/<namespace>/lang/*.json`)

Minecraft's own standard resource-pack language file format. This mod has
no keys of its own that are specifically required (a vehicle's/weapon's
own display name is written directly in the `display_name` /
`DisplayName` field of the JSON/txt file itself instead).

---

## 4. Summary of naming rules

| Item | Case sensitivity | Extension handling |
|---|---|---|
| Vehicle file name (`vehicles/*.json`) | Case-sensitive (used as part of the identifier) | Not used when referencing |
| Weapon file name (`weapons/*.txt`) | **Case-insensitive** | Not used when referencing |
| Sound file name (`sound(s)/*.ogg`) | **Case-insensitive** | Not used when referencing |
| HUD script file name (`hud/*.txt`) | Case-sensitive | Not used when referencing |
| Namespace (`<namespace>`) | Conventionally lowercase | — |

Every key inside a vehicle JSON (`entity_type`, `model`, etc.) is always
`snake_case`; every key inside a weapon config file (`DisplayName`,
`Type`, etc.) is always case-insensitive (conventionally written in
`PascalCase`, following MC Heli's own convention).

---

## 5. Reloading

- Running `/reload` in-game reloads every vehicle, weapon, HUD, model,
  texture, and sound at once. No game or world restart is needed
- While riding a vehicle, you can also reload the weapons or HUD
  individually from the vehicle menu (R key) → MOD Option → Development
- An already-spawned vehicle entity may not reflect a change to the
  vehicle definition itself (position, appearance, etc.) unless
  respawned. A numeric adjustment in a weapon config file
  (`weapons/*.txt`) takes effect immediately even while still riding the
  existing vehicle

---

## 6. Converting from an existing MC Heli addon

If you already have an addon pack for MC Heli, the included conversion
tool (`tools/mcheli_convert.py` or `tools/mcheli_convert_gui.py`) can
automatically convert it to the directory structure above. See the "How
to convert an MC Heli addon" section of `GUIDE.md` for details. Once
converted, you can use the output folder as-is by placing it under
`tudursvehiclemod-addons/`.

> **Important**: an MC Heli addon pack may not be used with this mod
> unless its own author has explicitly permitted use outside MC Heli. The
> conversion working technically and the conversion being permitted are
> two separate questions. See `GUIDELINES.md`.
