#!/usr/bin/env python3
"""
mcheli_convert.py - Converts MC Helicopter mod (Minecraft 1.7.10) addon packs into
a resource pack + data pack usable by this project's vehicle framework.

USAGE
    python3 mcheli_convert.py <mcheli_addon_folder> <output_folder> [--namespace mcheliport] [--no-auto-detect]

    <mcheli_addon_folder> is the folder containing "assets/mcheli/..." (i.e. the
    folder you get after unzipping the addon pack).

    <output_folder> will be created as ONE plain (uncompressed) folder mirroring:
        assets/<namespace>/models,textures/vehicle/<entity_type>/...
        data/<namespace>/vehicles/<entity_type>/...
    where <entity_type> is one of helicopter/car/ship/submarine/aircraft - per a
    direct request, output is auto-sorted by entity type rather than dumped into
    one flat folder, to make a large converted addon pack easier to browse/edit
    by hand afterward.

    --no-auto-detect disables the Float/Gravity/AddRotor/AddCrawlerTrack-based
    entity type detection (see detect_entity_type's own doc) - every vehicle is
    then assigned strictly by its source folder (helicopters/planes/tanks/vehicles),
    with no override even if a vehicle's own settings suggest otherwise.

    Command-line unfamiliar? Use mcheli_convert_gui.py instead - same conversion,
    a simple window with folder pickers and a progress log, no terminal needed.

    To install: copy the WHOLE <output_folder> into
        <game directory>/tudursvehiclemod-addons/<any name you like>/
    No zipping, no resource pack / datapack "enable" step needed - the mod scans
    that folder directly for vehicle definitions, models, and textures. Every
    converted vehicle is reachable in-game through the tiered spawner item system
    (see item.TieredVehicleSpawnerItem) - there's no per-vehicle recipe/item to
    generate anymore, so nothing here needs the traditional datapack route at all.

WHAT THIS DOES AND DOES NOT CONVERT
    MC Heli's format is vastly more feature-rich than this project's simple vehicle
    framework (turrets, hatches, landing gear, racks, armor, fuel, UAVs, multiple
    cameras, ...). This tool intentionally converts only the subset that maps onto
    VehicleDefinition:
      - entity_type   : from the source folder (helicopters/planes -> air,
                        tanks/vehicles -> ground)
      - model/texture : the vehicle's main <name>.obj + <name>.png only - NOT the
                        separate hatch/landing-gear/turret-part sub-models (this
                        project's renderer draws one static mesh, not an articulated
                        multi-part rig)
      - max_speed     : from Speed
      - width/height  : from the union of all Boundingbox entries (approximate -
                        MC Heli's own EntityWidth/EntityHeight is NOT the vehicle's
                        hitbox, it's the RIDDEN PLAYER's render size, so it is not
                        used here)
      - step_height   : from StepHeight (falls back to 1.0)
      - seats         : from AddSeat / AddGunnerSeat (position only; camera/turret
                        behaviour is dropped)
      - weapons       : from AddWeapon, cross-referenced against weapons/<name>.txt
                        for Power (damage) / Acceleration (velocity) / Delay
                        (cooldown) - ammo, explosions, guidance, sound etc. are ignored
      - spawn_item    : every vehicle defaults to spawn_item.tier = 1 (see the tiered
                        spawner item system - item.TieredVehicleSpawnerItem - which is
                        how players actually obtain converted vehicles); bump it by
                        hand per vehicle (1-5) to fit your own progression/balance.
                        display_name is taken from DisplayName if present
      - spinning_parts: from AddRotor / AddPartRotor / AddPartRotation, which each
                        specify a real pivot position + rotation axis (this project
                        uses those directly rather than guessing an axis from a
                        part's name, since e.g. a helicopter's tail rotor genuinely
                        spins about a different axis than its main rotor). Requires
                        the vehicle's .obj to actually name that geometry as its own
                        group/object named "rotor0"/"rotor1"/.../"rotation0"/... (MC
                        Heli's OWN convention is that a part's file-name suffix - or
                        its "$suffix" name inside the combined .obj - always matches
                        a specific AddPart*/AddRotor entry's FUNCTION, counted in
                        declaration order per type, not a free-form label; e.g. the
                        first AddPartWeapon in the file is always "weapon0"). MC
                        Heli's separate "<vehicle>_rotor0.obj"-style per-part files
                        are NOT merged in by this converter (see below)
      - weapon_parts  : from AddPartWeapon / AddPartTurretWeapon / AddPartRotWeapon,
                        which each name a real OBJ part that rotates to track
                        whoever is aiming the associated AddWeapon entry's own
                        seat (matched by weapon name, falling back to no
                        association ("none") if not found) - see
                        asset.WeaponPart. Shares one "weapon{N}" numbering
                        sequence across these three (plus AddPartWeaponMissile,
                        which becomes an asset.AmmoPart instead - see
                        build_weapon_parts()'s own doc), the same "one counter
                        per family" convention spinning_parts/toggle_parts
                        already use for their own directive families. AddPartTurretWeapon is treated
                        identically to AddPartWeapon (this project's simplified
                        version doesn't also shift the part's own position as a
                        real turret ring would); AddPartRotWeapon gets
                        aim-tracking too, but not its own "spins while firing"
                        behavior (for gatling-style weapons)

    NOT converted, even though this project's renderer COULD in principle support
    it with more work: AddPartWeaponChild (a sub-part riding along with its own
    parent AddPartWeapon/etc, sharing the same aim - still consumes a numbering
    slot so later parts number correctly, just isn't itself rendered/animated
    BY THIS FUNCTION specifically - the weapon-part equivalent IS implemented,
    by build_weapon_parts()). AddPartThrottle (throttle-linked motion - see
    build_toggle_parts()'s own "addpartthrottle" branch) and AddPartCamera
    (player-view-tracking parts - see build_camera_parts()) ARE now converted,
    both used to render as static geometry only.

    Skipped entirely (per the user's go-ahead to ignore anything not load-bearing
    for actual gameplay behaviour in this project): armor/damage model, fuel, HUD,
    racks, UAV/drone behaviour, wing/pylon folding, search lights, smoke/flares,
    multi-texture variants, and any vehicle whose main model is a .mqo file
    (Metasequoia format - this project's OBJ-only loader can't read it; re-export
    the model as OBJ first, e.g. via Blender, and re-run). Also, this converter only
    ever reads the vehicle's single main <name>.obj - MC Heli's separate per-part
    files ("<vehicle>_hatch0.obj", "<vehicle>_rotor0.obj", etc.) are never merged
    in; if you want any of that geometry to show up at all (even statically), you
    need to combine it into the main .obj yourself (e.g. in Blender) before
    converting, naming moving parts "rotor0"/"rotation0"/etc. as covered above.

    Every skip/limitation is written to convert_report.txt in the output folder.
"""
import json
import math
import re
import shutil
import sys
import tempfile
from pathlib import Path

import mqo_to_obj

CATEGORY_TO_ENTITY_TYPE = {
    "helicopters": ("tudursvehiclemod:helicopter", "air"),
    "planes": ("tudursvehiclemod:aircraft", "air"),
    "tanks": ("tudursvehiclemod:car", "ground"),
    # MC Heli's own community
    # documentation for authoring new content is explicit that "Vehicle
    # is something which cannot be moved, such as the Utibi, or a
    # turret" - this is NOT another movement category at all, unlike
    # every other folder here, so it maps onto its own dedicated,
    # movement-less entity type rather than being treated as (and given
    # car-style throttle/steering physics it was never meant to have)
    # another ground vehicle.
    "vehicles": ("tudursvehiclemod:static_emplacement", "static"),
}

# Used when a vehicle's own config file doesn't set its own HUD (see convert_one()'s own use of this).
DEFAULT_HUD_BY_ENTITY_TYPE = {
    "tudursvehiclemod:helicopter": "heli",
    "tudursvehiclemod:aircraft": "plane",
    "tudursvehiclemod:vtol": "heli",
    "tudursvehiclemod:car": "vehicle",
    "tudursvehiclemod:ship": "vehicle",
    "tudursvehiclemod:submarine": "sub",
    "tudursvehiclemod:static_emplacement": "vehicle",
}

DEFAULT_PROJECTILE_ITEM = "minecraft:iron_nugget"

# Reported by hand (comparing against MC Heli's own actual seat height).
SEAT_Y_CORRECTION = -0.5

# This project's damage model has nothing like MC Heli's per-vehicle MaxHp pools (which run into the hundreds/thousands for ships and tanks).


def detect_entity_type(entries: dict, category: str, disable_auto_detect: bool = False) -> tuple:
    """See CATEGORY_TO_ENTITY_TYPE for the plain folder-based mapping this
    overrides when disable_auto_detect is False (the default) - structural
    markers (AddRotor/AddCrawlerTrack) and, within "planes", the Float/Gravity
    heuristic below take priority over the source folder. Passing
    disable_auto_detect=True skips all of that and always uses the plain
    folder-based mapping instead.
    """
    if disable_auto_detect:
        return CATEGORY_TO_ENTITY_TYPE[category]

    if category == "planes":
        float_true = first(entries, "float", ["false"])[0].strip().lower() == "true"
        gravity = to_float(first(entries, "gravity", ["-0.04"])[0], -0.04)
        strong_gravity = abs(gravity) >= 0.08  # ships/subs ~-0.15 vs seaplanes/normal aircraft 0..~-0.05

        if float_true and strong_gravity:
            return "tudursvehiclemod:ship", "water"
        if not float_true and strong_gravity:
            return "tudursvehiclemod:submarine", "water"

        # MC Heli's own EnableVtol=true directive is
        # integrated into this project's own VtolEntity (see that class's
        # own doc) - judged SOLELY by this directive's own value, per a
        # direct clarification: MC Heli's own AddRotor/AddPartRotor
        # entries are written purely to make a "Blade"-type part actually
        # animate at all, REGARDLESS of whether the vehicle is genuinely a
        # VTOL aircraft, a plain helicopter, or anything else with blade
        # parts to move - their presence was never a meaningful signal for
        # VTOL status specifically, so no other condition is checked here
        # at all besides EnableVtol itself. Checked HERE, as the very LAST
        # step, only once ship/submarine have already been ruled out above,
        # applying only to whatever remains
        # classified as "aircraft" (whether the seaplane case just below,
        # or the plain aircraft fallback at the very end of this
        # function). Submarines in particular often set EnableVtol despite
        # not actually being a VTOL aircraft at all, so checking this any
        # earlier (before ship/submarine detection) would misclassify
        # them. This whole "planes"-category block runs BEFORE the generic
        # AddRotor check below too, purely so ship/submarine/vtol/seaplane
        # detection all get a chance to run first - not because AddRotor's
        # own presence has any bearing on VTOL status itself.
        if first(entries, "enablevtol", ["false"])[0].strip().lower() == "true":
            return "tudursvehiclemod:vtol", "air"

        if float_true:
            # Seaplane: flies like a normal aircraft, but also needs to be able to sit on and take off from water.
            return "tudursvehiclemod:aircraft", "air"

    if "addrotor" in entries:
        return "tudursvehiclemod:helicopter", "air"
    if "addcrawlertrack" in entries:
        return "tudursvehiclemod:car", "ground"

    return CATEGORY_TO_ENTITY_TYPE[category]


def parse_mcheli_file(path: Path) -> dict:
    """Parses a MC Heli key=value config file into {key: [ [token, token, ...], ... ]}.
    Every occurrence of a repeated key (AddSeat, AddWeapon, ...) is kept, in order.
    Lines starting with ';' (after stripping whitespace) are comments and ignored,
    per the addon author's own convention.

    Also stashes a "__ordered__" entry: every directive occurrence as (key, tokens)
    tuples in the file's ORIGINAL top-to-bottom order, regardless of key - the
    per-key grouping above loses this entirely (each key's own list is internally
    ordered, but there's no way to tell how DIFFERENT keys interleaved with each
    other from that alone). This matters because some directive FAMILIES share a
    single MC Heli file-naming sequence across multiple distinct directive types
    (e.g. AddPartLG/AddPartLGRev/AddPartSlideRotLG are all "<vehicle>_lg{N}.obj",
    numbered by where they fall in the file overall, not separately per exact
    directive type) - see build_toggle_parts()'s use of this for a real-world
    case where two interleaved directive types produced a genuine "$lg0" naming
    collision (two unrelated parts both trying to use the same name) using the
    simpler independent-per-key counting this function used to be relied on for.
    "__ordered__" is not a real MC Heli directive name, so entries.get("addxxx")
    calls elsewhere can never accidentally collide with it.
    """
    entries: dict[str, list[list[str]]] = {}
    ordered: list[tuple[str, list[str]]] = []
    text = path.read_text(encoding="utf-8-sig", errors="replace")
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith(";"):
            continue
        if "=" not in line:
            continue
        key, _, value = line.partition("=")
        # MC Heli itself is case-insensitive for directive names (e.g. "OnGroundPitch"/"Ongroundpitch"/"ONGROUNDPITCH" all mean the same thing to it).
        key = key.strip().lower()
        # Strip a trailing inline comment on the SAME line as the value (e.g. "OnGroundPitch = 13 ; flap angle").
        value = value.split(";", 1)[0]
        tokens = [t.strip() for t in value.split(",")]
        entries.setdefault(key, []).append(tokens)
        ordered.append((key, tokens))
    entries["__ordered__"] = ordered
    return entries


def first(entries: dict, key: str, default=None):
    if key in entries and entries[key]:
        return entries[key][0]
    return default


def to_float(token: str, default: float = 0.0) -> float:
    try:
        return float(token)
    except (TypeError, ValueError):
        return default


def to_bool(token: str) -> bool:
    return token.strip().lower() == "true"


def compute_bounding_size(entries: dict) -> tuple[float, float]:
    """Approximates overall width/height from the union of BoundingBox entries
    (BoundingBox is documented as a weapon-hit-detection box, not the movement
    hitbox, but it's the closest thing to an overall size MC Heli's format
    exposes, so it's used here as a best-effort stand-in)."""
    boxes = entries.get("boundingbox")
    if not boxes:
        return 1.0, 1.0
    max_half_width = 0.0
    max_top = 0.0
    for box in boxes:
        if len(box) < 5:
            continue
        cx, cy, cz, w, h = (to_float(box[0]), to_float(box[1]), to_float(box[2]),
                            to_float(box[3]), to_float(box[4]))
        max_half_width = max(max_half_width, abs(cx) + w / 2, abs(cz) + w / 2)
        max_top = max(max_top, cy + h / 2)
    width = round(max_half_width * 2, 2) or 1.0
    height = round(max_top, 2) or 1.0
    return width, height


def build_spinning_parts(entries: dict, warnings: list, available_part_names: set, y_offset: float = 0.0) -> list:
    """... (unchanged doc above) ...
    y_offset: see build_seats'/merge_obj_files' own doc - applied to every
    pivot_y so rotation centers stay correct relative to the (now-shifted) mesh.
    """
    parts = []
    rotor_index = 0

    def resolve_part_name(candidate_words: list, index: int, add_type: str) -> str:
        for word in candidate_words:
            candidate = f"${word}{index}"
            if candidate in available_part_names:
                return candidate
        fallback = f"${candidate_words[0]}{index}"
        tried = ", ".join(f"${w}{index}" for w in candidate_words)
        warnings.append(f"{add_type} #{index}: none of the candidate part names ({tried}) matched any "
                         f"actual part file found for this vehicle - defaulting to '{fallback}', which will "
                         f"almost certainly NOT animate (it doesn't match any real OBJ group). Check this "
                         f"vehicle's actual '<vehicle>_*.obj' part file names next to the main model and fix "
                         f"spinning_parts[].part by hand to match whichever one is really this rotor/blade.")
        return fallback

    # Real MC Heli OBJ files commonly name the rotor's
    # own group "$blade" rather than "$rotor" - "rotor" is tried first
    # (matching this project's own naming convention when generating new
    # models), falling back to "blade" for actual converted MC Heli models
    # that use that name instead, so AddRotor's own resolved part name
    # actually matches a real group in the model instead of defaulting to
    # a nonexistent "$rotorN" that would never animate at all.
    ROTOR_WORDS = ["rotor", "blade"]
    BLADE_WORDS = ["blade"]
    ROTATION_WORDS = ["rotation"]

    # RotorSpeed's own value is used directly, with no conversion formula applied at all.
    rotor_speed = to_float(first(entries, "rotorspeed", ["60"])[0], 60.0)

    for rotor in entries.get("addrotor", []):
        # AddRotor (helicopters) = bladeCount, angleBetweenBlades, posX, posY, posZ, axisX, axisY, axisZ, foldable
        if len(rotor) < 8:
            continue
        parts.append({
            "part": resolve_part_name(ROTOR_WORDS, rotor_index, "AddRotor"),
            "pivot_x": to_float(rotor[2]), "pivot_y": to_float(rotor[3]) + y_offset, "pivot_z": to_float(rotor[4]),
            "axis_x": to_float(rotor[5]), "axis_y": to_float(rotor[6]), "axis_z": to_float(rotor[7]),
            "speed": rotor_speed,
        })
        rotor_index += 1

    # AddBlade must immediately follow AddRotor or AddPartRotor in MC
    # Heli's own .txt format, but its OWN fields (not AddRotor's/
    # AddPartRotor's) are what actually matter here.
    # clarification: an AddBlade that immediately follows an AddPartRotor
    # specifically (rather than a plain AddRotor) is a CHILD of that
    # nacelle - see build_vtol_rotor_parts()'s own doc - and is handled
    # EXCLUSIVELY there instead (with its own vtol_rotor_parent link), so
    # it's explicitly skipped here to avoid registering it twice.
    #
    # A naming collision could occur: this uses each AddBlade
    # entry's own OVERALL position among ALL AddBlade entries in the
    # file (matching the original, pre-existing numbering convention -
    # see build_vtol_rotor_parts()'s own matching doc), NOT a counter
    # local to just the unlinked ones handled here, so an unlinked blade
    # can never end up sharing the same resolved "$bladeN" name as one
    # of build_vtol_rotor_parts()'s own linked ones.
    ordered = entries.get("__ordered__", [])
    addpartrotor_followed_addblade_positions = set()
    addblade_ordinal_by_position = {}
    addblade_ordinal_counter = 0
    for i, (key, _tokens) in enumerate(ordered):
        if key == "addblade":
            addblade_ordinal_by_position[i] = addblade_ordinal_counter
            addblade_ordinal_counter += 1
        if key == "addpartrotor" and i + 1 < len(ordered) and ordered[i + 1][0] == "addblade":
            addpartrotor_followed_addblade_positions.add(i + 1)
    for i, (key, blade) in enumerate(ordered):
        if key != "addblade":
            continue
        if i in addpartrotor_followed_addblade_positions:
            continue
        # AddBlade = bladeCount, angleBetweenBlades, posX, posY, posZ, axisX, axisY, axisZ
        if len(blade) < 8:
            continue
        parts.append({
            "part": resolve_part_name(BLADE_WORDS, addblade_ordinal_by_position[i], "AddBlade"),
            "pivot_x": to_float(blade[2]), "pivot_y": to_float(blade[3]) + y_offset, "pivot_z": to_float(blade[4]),
            "axis_x": to_float(blade[5]), "axis_y": to_float(blade[6]), "axis_z": to_float(blade[7]),
            "speed": rotor_speed,
        })

    for i, rot in enumerate(entries.get("addpartrotation", [])):
        # AddPartRotation = posX, posY, posZ, axisX, axisY, axisZ [, speed, alwaysRotate].
        if len(rot) < 6:
            continue
        parts.append({
            "part": resolve_part_name(ROTATION_WORDS, i, "AddPartRotation"),
            "pivot_x": to_float(rot[0]), "pivot_y": to_float(rot[1]) + y_offset, "pivot_z": to_float(rot[2]),
            "axis_x": to_float(rot[3]), "axis_y": to_float(rot[4]), "axis_z": to_float(rot[5]),
            # MC Heli's own speed unit for this field isn't confirmed (likely not a direct 1:1 with our degrees/tick).
            "speed": to_float(rot[6], 1.0) if len(rot) >= 7 else 1.0,
        })

    if parts:
        warnings.append(f"generated {len(parts)} spinning_parts (rotor/blade/rotation) - speeds are best-effort "
                         f"defaults or directly copied from the source file and may need manual tuning. This "
                         f"relies on the vehicle's original '<vehicle>_rotor0.obj'-style part files actually "
                         f"being found next to the main model and merged in as matching '$rotor0'/etc. groups "
                         f"(see find_part_files()/merge_obj_files()) - if a part file is missing, that part just "
                         f"won't be there to animate, though the rest of the vehicle still converts fine. Note: "
                         f"AddPartRotor (VTOL nacelle tilt) is NOT converted to a spinning part at all - see this "
                         f"function's own class doc for why.")

    return parts


def build_track_roller_parts(entries: dict, warnings: list, available_part_names: set, y_offset: float = 0.0) -> list:
    """AddTrackRoller, no longer converted as a
    generic spinning_part at MC Heli's own flat TrackRollerRot rate (which
    bore no actual relationship to how fast the belt underneath it was
    really moving) - each roller instead gets its own dedicated entry here,
    position only. Its own rotations-per-block (how fast it should
    actually spin to stay in lock-step with the belt) is deliberately NOT
    computed here at all - see TrackRollerPart's own doc / ServerObjModelTrackRollerBounds's
    own doc for why that's instead derived once, server-side, from the
    roller's own actual model geometry and cached, rather than guessed at
    conversion time from directives that were never a reliable source for
    it in the first place.
    """
    TRACK_ROLLER_WORDS = ["track_roller", "trackroller", "roller"]
    parts = []

    def resolve_part_name(index: int) -> str:
        for word in TRACK_ROLLER_WORDS:
            candidate = f"${word}{index}"
            if candidate in available_part_names:
                return candidate
        fallback = f"${TRACK_ROLLER_WORDS[0]}{index}"
        tried = ", ".join(f"${w}{index}" for w in TRACK_ROLLER_WORDS)
        warnings.append(f"AddTrackRoller #{index}: none of the candidate part names ({tried}) matched any "
                         f"actual part file found for this vehicle - defaulting to '{fallback}', which will "
                         f"almost certainly NOT animate (it doesn't match any real OBJ group). Check this "
                         f"vehicle's actual '<vehicle>_*.obj' part file names next to the main model and fix "
                         f"track_roller_parts[].part by hand to match whichever one is really this roller.")
        return fallback

    for i, roller in enumerate(entries.get("addtrackroller", [])):
        # AddTrackRoller = posX, posY, posZ (position only - no axis of its own; negative X = right side, positive X = left side, per Readme_Aircraft.txt's own doc - the actual rotation axis is always the vehicle's own X axis, the same way a real roller/wheel naturally spins as the vehicle moves forward/backward).
        if len(roller) < 3:
            continue
        parts.append({
            "part": resolve_part_name(i),
            "pivot_x": to_float(roller[0]), "pivot_y": to_float(roller[1]) + y_offset, "pivot_z": to_float(roller[2]),
        })
    return parts


def build_vtol_rotor_parts(entries: dict, warnings: list, available_part_names: set, y_offset: float = 0.0) -> tuple[list, list]:
    """AddPartRotor, a DIFFERENT thing from
    AddRotor (HelicopterEntity's own continuously-spinning main/tail rotor
    blades - see ROTOR_WORDS's own doc). AddPartRotor instead tilts a named
    part (typically an engine nacelle) through a limited angle between
    VtolEntity's own two flight modes, driven by that vehicle's own
    mode-switch transition progress rather than spinning continuously -
    see VtolRotorPart's own doc.

    In MC Heli's own source format, an
    AddBlade entry that immediately follows an AddPartRotor entry (no
    other directive in between) is a CHILD of that specific rotor - its
    own continuous spin (same as any other AddRotor/AddBlade pairing -
    see build_spinning_parts()'s own doc) additionally inherits that
    parent nacelle's own tilt (see PartAnimation's own vtol_rotor_parent
    doc), rather than staying frozen at a fixed angle while the rest of
    the nacelle tilts out from under it. Walks entries["__ordered__"]
    directly (rather than just entries.get("addpartrotor", [])) so this
    direct (zero-gap) adjacency - MC Heli's own documented convention for
    this specific pairing - can actually be checked; a vehicle could
    plausibly have an AddPartRotor with no blade of its own at all (rare,
    but not invalid), so this deliberately does NOT fall back to "the
    most recent AddPartRotor seen so far regardless of what's in
    between".

    Returns (vtol_rotor_parts, linked_blade_spinning_parts) - the caller
    is expected to fold the second list into its own overall
    spinning_parts (alongside the plain, unlinked AddRotor/AddBlade
    entries build_spinning_parts() already produces).
    """
    VTOL_ROTOR_WORDS = ["vtol_rotor", "vtolrotor", "nacelle"]
    BLADE_WORDS = ["blade"]
    rotor_speed = to_float(first(entries, "rotorspeed", ["60"])[0], 60.0)
    parts = []
    blade_parts = []

    def resolve_rotor_name(index: int) -> str:
        for word in VTOL_ROTOR_WORDS:
            candidate = f"${word}{index}"
            if candidate in available_part_names:
                return candidate
        fallback = f"${VTOL_ROTOR_WORDS[0]}{index}"
        tried = ", ".join(f"${w}{index}" for w in VTOL_ROTOR_WORDS)
        warnings.append(f"AddPartRotor #{index}: none of the candidate part names ({tried}) matched any "
                         f"actual part file found for this vehicle - defaulting to '{fallback}', which will "
                         f"almost certainly NOT animate (it doesn't match any real OBJ group). Check this "
                         f"vehicle's actual '<vehicle>_*.obj' part file names next to the main model and fix "
                         f"vtol_rotor_parts[].part by hand to match whichever one is really this nacelle.")
        return fallback

    def resolve_blade_name(index: int) -> str:
        for word in BLADE_WORDS:
            candidate = f"${word}{index}"
            if candidate in available_part_names:
                return candidate
        fallback = f"${BLADE_WORDS[0]}{index}"
        warnings.append(f"AddBlade (following AddPartRotor #{index}): none of the candidate part names "
                         f"(${BLADE_WORDS[0]}{index}) matched any actual part file found for this vehicle - "
                         f"defaulting to '{fallback}', which will almost certainly NOT animate.")
        return fallback

    ordered = entries.get("__ordered__", [])
    # A naming collision could occur: linked (AddPartRotor's
    # own child) and unlinked (plain AddRotor's own, or standalone)
    # blades used to each start their own "$bladeN" numbering fresh from
    # 0, in whichever function happened to claim them - the ORIGINAL,
    # pre-existing convention numbered every AddBlade purely by its own
    # overall position among ALL AddBlade entries in the file, so this
    # precomputes that same overall ordinal up front (rather than a
    # fresh, function-local counter) to match it exactly, regardless of
    # which of the two functions actually ends up resolving that
    # particular blade's own part name.
    addblade_ordinal_by_position = {}
    addblade_ordinal_counter = 0
    for i, (key, _tokens) in enumerate(ordered):
        if key == "addblade":
            addblade_ordinal_by_position[i] = addblade_ordinal_counter
            addblade_ordinal_counter += 1

    rotor_index = 0
    for i, (key, tokens) in enumerate(ordered):
        if key != "addpartrotor":
            continue
        current_index = rotor_index
        rotor_index += 1
        # AddPartRotor = posX, posY, posZ, [axisX, axisY, axisZ] (position, plus an optional tilt axis - defaults to the vehicle's own X axis, same convention as AddRotor's own axis parameters, if omitted).
        if len(tokens) < 3:
            continue
        rotor_part_name = resolve_rotor_name(current_index)
        # The tilt direction came out backwards
        # compared to the original MC Heli vehicle: the previous version
        # of this function collapsed the axis into a single "x"/"y"/"z"
        # letter via a magnitude-only comparison (discarding sign
        # entirely, and even mis-selecting the axis outright whenever a
        # negative component was actually the largest-magnitude one,
        # since max() alone doesn't account for sign) - VtolRotorPart now
        # takes a full signed axis_x/axis_y/axis_z vector instead,
        # matching PartAnimation's own established convention, so the
        # source data's own sign (and therefore rotation direction)
        # survives the conversion intact.
        rotor_axis_x, rotor_axis_y, rotor_axis_z = 1.0, 0.0, 0.0
        if len(tokens) >= 6:
            rotor_axis_x, rotor_axis_y, rotor_axis_z = to_float(tokens[3]), to_float(tokens[4]), to_float(tokens[5])
        parts.append({
            "part": rotor_part_name,
            "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
            "axis_x": rotor_axis_x, "axis_y": rotor_axis_y, "axis_z": rotor_axis_z,
        })

        if i + 1 < len(ordered) and ordered[i + 1][0] == "addblade":
            blade_tokens = ordered[i + 1][1]
            # AddBlade = bladeCount, angleBetweenBlades, posX, posY, posZ, axisX, axisY, axisZ - see build_spinning_parts()'s own matching doc for this same 8-field layout.
            if len(blade_tokens) >= 8:
                blade_parts.append({
                    "part": resolve_blade_name(addblade_ordinal_by_position[i + 1]),
                    "pivot_x": to_float(blade_tokens[2]), "pivot_y": to_float(blade_tokens[3]) + y_offset, "pivot_z": to_float(blade_tokens[4]),
                    "axis_x": to_float(blade_tokens[5]), "axis_y": to_float(blade_tokens[6]), "axis_z": to_float(blade_tokens[7]),
                    "speed": rotor_speed,
                    "vtol_rotor_parent": rotor_part_name,
                })

    return parts, blade_parts


def build_toggle_parts(entries: dict, warnings: list, available_part_names: set, y_offset: float = 0.0) -> list:
    """AddPartHatch/AddPartSlideHatch and AddPartCanopy/AddPartSlideCanopy each
    ease between closed/open on the pilot's own toggle key (see
    key.tudursvehiclemod.hatch_toggle, default "H" - MC Heli itself uses "Z", but this
    project picked a key that doesn't collide with anything else it binds).
    AddPartLG/AddPartLGRev/AddPartSlideRotLG each ease automatically between
    retracted/deployed based on whether the vehicle is airborne or grounded (see
    AbstractVehicleEntity#landingGearProgress) - NOT a key toggle by default,
    despite MC Heli filing it under the same general "part" naming family as
    hatches/canopies, though key.tudursvehiclemod.gear_toggle can manually override the
    automatic state temporarily (see AbstractVehicleEntity#toggleLandingGear()).
    AddPartLGRev genuinely folds the OPPOSITE direction from AddPartLG (MC Heli's
    own documented behavior, not an implementation quirk) - see
    VehicleEntityRenderer, which inverts the progress specifically for
    trigger="landing_gear_reversed". AddPartSlideRotLG combines a slide AND a
    rotation at once (see VehicleEntityRenderer's "slide_rotate" mode).

    AddPartWeaponBay/AddPartSlideWeaponBay ease open (trigger="weapon_bay")
    whenever their own associated weapon is the currently selected one (see
    TogglePart's own "weapon_bay" trigger doc) - not literally "while firing",
    since MC Heli's own selection/firing model doesn't distinguish the two on
    the Java side of this project (selecting IS "arming" for HUD/ammo display
    purposes already).

    AddPartLGHatch ("opens only during the gear's own transition, not while
    fully deployed or retracted") IS implemented, via the dedicated
    landing_gear_hatch trigger - see its own doc in
    AbstractVehicleEntity's own updateToggleParts().

    AddPartThrottle eases (actually, follows directly with no independent
    easing at all - see AbstractVehicleEntity's own updateToggleParts() doc
    for its "throttle" trigger) proportionally to this vehicle's own current
    throttle fraction. Supports a simultaneous rotation+translation via the
    "slide_rotate" mode (the same one AddPartSlideRotLG already uses) when a
    non-zero moveX/Y/Z is present, "rotate"-only otherwise.

    Like build_spinning_parts(), part names are matched against
    available_part_names (the vehicle's REAL discovered part files) with an
    explicit warning on failure, rather than assumed outright - "hatch{N}"/
    "canopy{N}"/"lg{N}" are the documented MC Heli convention, but
    build_spinning_parts() already found one real-world vehicle where a
    documented naming convention didn't actually hold, so the same defensive
    matching is used here too rather than assuming this convention is airtight.
    """
    parts = []

    def resolve(word: str, index: int, add_type: str) -> str:
        candidate = f"${word}{index}"
        if candidate not in available_part_names:
            warnings.append(f"{add_type} #{index}: expected a part file for '{candidate}' but none was found "
                             f"for this vehicle - using '{candidate}' anyway, which will almost certainly NOT "
                             f"render/animate. Check this vehicle's actual '<vehicle>_*.obj' part file names and "
                             f"fix toggle_parts[].part by hand if it's actually named something else.")
        return candidate

    # Single pass over the file's ORIGINAL order (see parse_mcheli_file's own doc for why this matters).
    hatch_index = 0
    canopy_index = 0
    lg_index = 0
    light_hatch_index = 0
    wb_index = 0
    throttle_index = 0
    wing_index = -1
    pylon_index = 0
    # VariableSweepWing/SweepWingSpeed are vehicle-level (not per-AddPartWing)
    # directives in MC Heli's own format - read once, applied to every
    # wing/pylon part this vehicle has. See asset.WingSweepConfig's own doc.
    variable_sweep_wing = first(entries, "variablesweepwing", ["false"])[0].strip().lower() == "true"
    sweep_wing_speed = to_float(first(entries, "sweepwingspeed", ["0"])[0], 0.0)
    wing_sweep_config = {"variable_sweep_wing": variable_sweep_wing, "sweep_wing_speed": sweep_wing_speed}

    for key, tokens in entries.get("__ordered__", []):
        if key == "addparthatch":
            index = hatch_index
            hatch_index += 1
            # AddPartHatch = posX, posY, posZ, axisX, axisY, axisZ [, angle (0-180)].
            if len(tokens) >= 6:
                parts.append({
                    "part": resolve("hatch", index, "AddPartHatch"),
                    "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
                    "mode": "rotate",
                    "axis_x": to_float(tokens[3]), "axis_y": to_float(tokens[4]), "axis_z": to_float(tokens[5]),
                    "max_angle": to_float(tokens[6], 90.0) if len(tokens) >= 7 else 90.0,
                    "trigger": "key",
                    "speed": 0.6,
                })
        elif key == "addpartslidehatch":
            index = hatch_index
            hatch_index += 1
            # AddPartSlideHatch = moveX, moveY, moveZ
            if len(tokens) >= 3:
                parts.append({
                    "part": resolve("hatch", index, "AddPartSlideHatch"),
                    "mode": "slide",
                    "offset_x": to_float(tokens[0]), "offset_y": to_float(tokens[1]), "offset_z": to_float(tokens[2]),
                    "trigger": "key",
                    "speed": 0.6,
                })
        elif key == "addpartcanopy":
            index = canopy_index
            canopy_index += 1
            # AddPartCanopy = posX, posY, posZ, axisX, axisY, axisZ [, angle (0-180)].
            if len(tokens) >= 6:
                parts.append({
                    "part": resolve("canopy", index, "AddPartCanopy"),
                    "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
                    "mode": "rotate",
                    "axis_x": to_float(tokens[3]), "axis_y": to_float(tokens[4]), "axis_z": to_float(tokens[5]),
                    "max_angle": to_float(tokens[6], 90.0) if len(tokens) >= 7 else 90.0,
                    "trigger": "key",
                    "speed": 6.0,
                })
        elif key == "addpartslidecanopy":
            index = canopy_index
            canopy_index += 1
            # AddPartSlideCanopy = moveX, moveY, moveZ
            if len(tokens) >= 3:
                parts.append({
                    "part": resolve("canopy", index, "AddPartSlideCanopy"),
                    "mode": "slide",
                    "offset_x": to_float(tokens[0]), "offset_y": to_float(tokens[1]), "offset_z": to_float(tokens[2]),
                    "trigger": "key",
                    "speed": 6.0,
                })
        elif key == "addpartthrottle":
            index = throttle_index
            throttle_index += 1
            # AddPartThrottle = posX, posY, posZ, axisX, axisY, axisZ, angle (0-180) [, moveX, moveY, moveZ].
            if len(tokens) >= 7:
                has_translation = len(tokens) >= 10 and (
                        to_float(tokens[7]) != 0.0 or to_float(tokens[8]) != 0.0 or to_float(tokens[9]) != 0.0)
                entry = {
                    "part": resolve("throttle", index, "AddPartThrottle"),
                    "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
                    "axis_x": to_float(tokens[3]), "axis_y": to_float(tokens[4]), "axis_z": to_float(tokens[5]),
                    "max_angle": to_float(tokens[6], 90.0),
                    "trigger": "throttle",
                    "speed": 999.0,  # tudursvehiclemod$updateToggleParts()'s own "throttle" trigger follows the throttle fraction directly (no independent easing) - this is never actually read for it, but a huge value keeps this obviously-inert rather than looking like a real, meaningful tuning number.
                }
                if has_translation:
                    # "slide_rotate" - the same mode AddPartSlideRotLG already uses, combining a rotation around pivot_x/y/z FIRST, then a slide by offset_x/y/z - see AbstractVehicleEntity's own tudursvehiclemod$isInsideVehicleHitbox() and VehicleEntityRenderer's own matching "slide_rotate" branches.
                    entry["mode"] = "slide_rotate"
                    entry["offset_x"] = to_float(tokens[7])
                    entry["offset_y"] = to_float(tokens[8])
                    entry["offset_z"] = to_float(tokens[9])
                else:
                    entry["mode"] = "rotate"
                parts.append(entry)
        elif key == "addpartlg":
            index = lg_index
            lg_index += 1
            # AddPartLG = posX, posY, posZ, axisX, axisY, axisZ [, angle (0-180)] [, a second rotation stage.
            if len(tokens) >= 6:
                parts.append({
                    "part": resolve("lg", index, "AddPartLG"),
                    "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
                    "mode": "rotate",
                    "axis_x": to_float(tokens[3]), "axis_y": to_float(tokens[4]), "axis_z": to_float(tokens[5]),
                    "max_angle": to_float(tokens[6], 90.0) if len(tokens) >= 7 else 90.0,
                    "trigger": "landing_gear",
                    "speed": 3.0,
                })
        elif key == "addpartlghatch":
            index = lg_index
            lg_index += 1
            # AddPartLGHatch = posX, posY, posZ, axisX, axisY, axisZ [, angle (0-180)] [, a second rotation stage - same format as AddPartLG].
            # Per Readme_Aircraft.txt's own doc: "AddPartLGHatch ... ギア折りたたみ時 0 → 90 → 0" (opens partway through the gear's own transition, then closes again once it settles at either extreme) - the landing_gear_hatch trigger (see AbstractVehicleEntity's own updateToggleParts() doc) implements exactly this.
            if len(tokens) >= 6:
                parts.append({
                    "part": resolve("lg", index, "AddPartLGHatch"),
                    "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
                    "mode": "rotate",
                    "axis_x": to_float(tokens[3]), "axis_y": to_float(tokens[4]), "axis_z": to_float(tokens[5]),
                    "max_angle": to_float(tokens[6], 90.0) if len(tokens) >= 7 else 90.0,
                    "trigger": "landing_gear_hatch",
                    "speed": 6.0,
                })
        elif key == "addpartlighthatch":
            index = light_hatch_index
            light_hatch_index += 1
            # AddPartLightHatch = posX, posY, posZ, axisX, axisY, axisZ, angle (-1800 to 1800). This directive's own documented range: -1800~1800 is exactly 10x AddPartLG's own documented 0-180 (see that directive's own comment above) - the most sensible reading of that is that AddPartLightHatch's own angle token is in TENTHS of a degree (giving an effective -180 to 180 once divided by 10), not that this hatch genuinely needs ten times the rotation range of every other hatch/gear directive in this format.
            # A search light directive is required alongside this: opens only while any AddSearchLight/AddFixedSearchLight/AddSteeringSearchLight on this vehicle is switched on - see AbstractVehicleEntity's own updateToggleParts() doc for the "light_hatch" trigger this emits, which mirrors landing_gear_hatch's own "open only during an active transition" shape but keyed on the search light's own on/off state instead of a gear's own progress.
            if len(tokens) >= 6:
                parts.append({
                    "part": resolve("light_hatch", index, "AddPartLightHatch"),
                    "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
                    "mode": "rotate",
                    "axis_x": to_float(tokens[3]), "axis_y": to_float(tokens[4]), "axis_z": to_float(tokens[5]),
                    "max_angle": to_float(tokens[6], 90.0) / 10.0 if len(tokens) >= 7 else 90.0,
                    "trigger": "light_hatch",
                    "speed": 6.0,
                })
        elif key == "addpartlgrev":
            index = lg_index
            lg_index += 1
            # AddPartLGRev = posX, posY, posZ, axisX, axisY, axisZ [, angle (0-180)].
            if len(tokens) >= 6:
                parts.append({
                    "part": resolve("lg", index, "AddPartLGRev"),
                    "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
                    "mode": "rotate",
                    "axis_x": to_float(tokens[3]), "axis_y": to_float(tokens[4]), "axis_z": to_float(tokens[5]),
                    "max_angle": to_float(tokens[6], 90.0) if len(tokens) >= 7 else 90.0,
                    "trigger": "landing_gear_reversed",
                    "speed": 3.0,
                })
        elif key == "addpartsliderotlg":
            index = lg_index
            lg_index += 1
            # AddPartSlideRotLG = moveX, moveY, moveZ, posX, posY, posZ, axisX, axisY, axisZ [, angle (0-180)].
            if len(tokens) >= 9:
                parts.append({
                    "part": resolve("lg", index, "AddPartSlideRotLG"),
                    "mode": "slide_rotate",
                    "offset_x": to_float(tokens[0]), "offset_y": to_float(tokens[1]), "offset_z": to_float(tokens[2]),
                    "pivot_x": to_float(tokens[3]), "pivot_y": to_float(tokens[4]) + y_offset, "pivot_z": to_float(tokens[5]),
                    "axis_x": to_float(tokens[6]), "axis_y": to_float(tokens[7]), "axis_z": to_float(tokens[8]),
                    "max_angle": to_float(tokens[9], 90.0) if len(tokens) >= 10 else 90.0,
                    "trigger": "landing_gear",
                    "speed": 3.0,
                })
        elif key == "addpartweaponbay":
            index = wb_index
            wb_index += 1
            # AddPartWeaponBay = weapon_name, posX, posY, posZ, axisX, axisY, axisZ [, angle (0-180)].
            # weapon_name is taken as-is; the rare "name1 / name2" multi-weapon
            # syntax some other weapon-linked parts allow isn't specially
            # split here (MC Heli's own documented example only shows one).
            if len(tokens) >= 7:
                parts.append({
                    "part": resolve("wb", index, "AddPartWeaponBay"),
                    "pivot_x": to_float(tokens[1]), "pivot_y": to_float(tokens[2]) + y_offset, "pivot_z": to_float(tokens[3]),
                    "mode": "rotate",
                    "axis_x": to_float(tokens[4]), "axis_y": to_float(tokens[5]), "axis_z": to_float(tokens[6]),
                    "max_angle": to_float(tokens[7], 90.0) if len(tokens) >= 8 else 90.0,
                    "trigger": "weapon_bay",
                    "weapon_name": tokens[0].strip(),
                    "speed": 6.0,
                })
        elif key == "addpartslideweaponbay":
            index = wb_index
            wb_index += 1
            # AddPartSlideWeaponBay = weapon_name, moveX, moveY, moveZ
            if len(tokens) >= 4:
                parts.append({
                    "part": resolve("wb", index, "AddPartSlideWeaponBay"),
                    "mode": "slide",
                    "offset_x": to_float(tokens[1]), "offset_y": to_float(tokens[2]), "offset_z": to_float(tokens[3]),
                    "trigger": "weapon_bay",
                    "weapon_name": tokens[0].strip(),
                    "speed": 6.0,
                })
        elif key == "addpartwing":
            wing_index += 1
            pylon_index = 0
            # AddPartWing = posX, posY, posZ, axisX, axisY, axisZ [, angle (0-180)].
            if len(tokens) >= 6:
                parts.append({
                    "part": resolve("wing", wing_index, "AddPartWing"),
                    "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
                    "mode": "rotate",
                    "axis_x": to_float(tokens[3]), "axis_y": to_float(tokens[4]), "axis_z": to_float(tokens[5]),
                    "max_angle": to_float(tokens[6], 90.0) if len(tokens) >= 7 else 90.0,
                    "trigger": "wing_fold",
                    "speed": 3.0,
                    "wing_sweep": wing_sweep_config,
                })
        elif key == "addpartpylon":
            # Must follow its own AddPartWing (MC Heli's own documented
            # requirement) - folds together with it (same trigger/wing_sweep),
            # own pivot/axis/angle per MC Heli's own format.
            index = pylon_index
            pylon_index += 1
            # AddPartPylon = posX, posY, posZ, axisX, axisY, axisZ [, angle (0-180)].
            if len(tokens) >= 6 and wing_index >= 0:
                parts.append({
                    "part": resolve(f"wing{wing_index}_pylon", index, "AddPartPylon"),
                    "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
                    "mode": "rotate",
                    "axis_x": to_float(tokens[3]), "axis_y": to_float(tokens[4]), "axis_z": to_float(tokens[5]),
                    "max_angle": to_float(tokens[6], 90.0) if len(tokens) >= 7 else 90.0,
                    "trigger": "wing_fold",
                    "speed": 3.0,
                    "wing_sweep": wing_sweep_config,
                })

    if parts:
        warnings.append(f"generated {len(parts)} toggle_parts (hatch/canopy/landing gear/weapon bay) - hatch/canopy "
                         f"toggle with the 'H' key by default (key.tudursvehiclemod.hatch_toggle), landing gear "
                         f"follows takeoff/landing automatically (or manually with 'G', "
                         f"key.tudursvehiclemod.gear_toggle), weapon bays open while their own weapon is selected, "
                         f"AddPartLGHatch (if present) opens only during the gear's own transition.")

    return parts


def to_argb(token: str, default: int = 0xFFFFFFFF) -> int:
    """Converts MC Heli's own AddSearchLight colour tokens (0x50FFFFFF-style hex ARGB literals, per Readme_Aircraft.txt's own "開始地点の色" / "終了地点の色" columns): parses a hex literal (with or without the 0x/# prefix MC Heli's own txt files use inconsistently) into a plain 32-bit ARGB int, masked to stay in range regardless of Python's own unbounded-int parsing. Falls back to opaque white on anything unparseable, matching to_float's own "never raise, just default" convention."""
    text = token.strip()
    if text.lower().startswith("0x"):
        text = text[2:]
    elif text.startswith("#"):
        text = text[1:]
    try:
        return int(text, 16) & 0xFFFFFFFF
    except ValueError:
        return default


def build_search_light_parts(entries: dict, warnings: list, available_part_names: set, y_offset: float = 0.0) -> list:
    """AddSearchLight/AddFixedSearchLight/AddSteeringSearchLight - see SearchLightPart's own doc (Java side) for exactly how the three differ (only in what drives the LIVE aim direction each frame).

    Per Readme_Aircraft.txt's own documented syntax, shared by all three:
    posX, posY, posZ, startColorHex, endColorHex, length, endRadius, yaw, pitch[, steerAngle (AddSteeringSearchLight only)].

    Matching every other part-naming convention here: part names use "$search_light{N}", counted across all three directives combined in file order (so a vehicle mixing AddSearchLight and AddFixedSearchLight gets $search_light0, $search_light1, ... continuously rather than each directive restarting its own count).
    """
    parts = []

    def resolve(index: int, add_type: str) -> str:
        candidate = f"$search_light{index}"
        if candidate in available_part_names:
            return candidate
        warnings.append(f"{add_type} #{index}: '{candidate}' doesn't match any actual part file found for this "
                         f"vehicle - a search light with no matching OBJ part still works (it has no mesh of its "
                         f"own to animate; only its light needs a pivot), so this is informational rather than a "
                         f"functional problem.")
        return candidate

    DIRECTIVES = [
        ("addsearchlight", "AddSearchLight", "PILOT_VIEW"),
        ("addfixedsearchlight", "AddFixedSearchLight", "FIXED"),
        ("addsteeringsearchlight", "AddSteeringSearchLight", "STEERING"),
    ]
    index = 0
    # Walks entries["__ordered__"] directly (see parse_mcheli_file's own doc), matching every other multi-directive
    # scan in this file, so $search_light{N} numbering follows the file's actual top-to-bottom order even when
    # AddSearchLight/AddFixedSearchLight/AddSteeringSearchLight are interleaved with each other.
    directive_keys = {key for key, _label, _mode in DIRECTIVES}
    for key, tokens in entries.get("__ordered__", []):
        if key not in directive_keys:
            continue
        label = next(lbl for k, lbl, _m in DIRECTIVES if k == key)
        mode = next(m for k, _l, m in DIRECTIVES if k == key)
        if len(tokens) < 7:
            warnings.append(f"{label} #{index}: expected at least 7 values (posX, posY, posZ, startColor, "
                             f"endColor, length, endRadius) - only {len(tokens)} given, skipping this entry.")
            index += 1
            continue
        entry = {
            "part": resolve(index, label),
            "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
            "start_color_argb": to_argb(tokens[3]),
            "end_color_argb": to_argb(tokens[4]),
            "length": to_float(tokens[5], 60.0),
            "end_radius": to_float(tokens[6], 20.0),
            "yaw": to_float(tokens[7], 0.0) if len(tokens) >= 8 else 0.0,
            "pitch": to_float(tokens[8], 0.0) if len(tokens) >= 9 else 0.0,
            "steer_angle": to_float(tokens[9], 0.0) if len(tokens) >= 10 else 0.0,
            "follow_mode": mode,
        }
        parts.append(entry)
        index += 1
    return parts


def build_wheel_parts(entries: dict, warnings: list, available_part_names: set, y_offset: float = 0.0) -> tuple:
    """AddPartWheel/AddPartSteeringWheel - see WheelPart's/SteeringWheelPart's
    own doc (Java side) for exactly how these get animated (spin+steer
    composition for AddPartWheel, steer-only for AddPartSteeringWheel).

    Part names use "$wheel{N}" / "$steering_wheel{N}"
    (with the underscore) - NOT any other naming this project might
    otherwise have guessed at, matching every other part-naming convention
    here (e.g. "$track_roller{N}").

    y_offset: see build_seats'/merge_obj_files' own doc - applied to every
    pivot_y (and steer_pivot_y, when explicitly given) so rotation centers
    stay correct relative to the (now-shifted) mesh.
    """
    wheel_parts = []
    steering_wheel_parts = []

    WHEEL_WORDS = ["wheel"]
    STEERING_WHEEL_WORDS = ["steering_wheel", "steeringwheel"]

    def resolve(candidate_words: list, index: int, add_type: str) -> str:
        for word in candidate_words:
            candidate = f"${word}{index}"
            if candidate in available_part_names:
                return candidate
        fallback = f"${candidate_words[0]}{index}"
        tried = ", ".join(f"${w}{index}" for w in candidate_words)
        warnings.append(f"{add_type} #{index}: none of the candidate part names ({tried}) matched any "
                         f"actual part file found for this vehicle - defaulting to '{fallback}', which will "
                         f"almost certainly NOT animate (it doesn't match any real OBJ group). Check this "
                         f"vehicle's actual '<vehicle>_*.obj' part file names next to the main model.")
        return fallback

    for i, wheel in enumerate(entries.get("addpartwheel", [])):
        # AddPartWheel = posX, posY, posZ [, steerAngle, steerAxisX, steerAxisY, steerAxisZ, steerPivotX, steerPivotY, steerPivotZ].
        # SteerAngle itself (not just the axis/pivot
        # group after it) can be omitted entirely for a wheel that never
        # steers (e.g. a rear wheel that stays facing forward through a
        # turn) - only X/Y/Z are actually required. Previously requiring
        # 4 tokens here silently dropped the WHOLE wheel entry whenever
        # steerAngle was left out, rather than just defaulting it to 0
        # (no steering) the way to_float()'s own default parameter
        # already handles for every OTHER omitted numeric field here.
        if len(wheel) < 3:
            continue
        entry = {
            "part": resolve(WHEEL_WORDS, i, "AddPartWheel"),
            "pivot_x": to_float(wheel[0]), "pivot_y": to_float(wheel[1]) + y_offset, "pivot_z": to_float(wheel[2]),
            "steer_angle": to_float(wheel[3], 0.0) if len(wheel) >= 4 else 0.0,
        }
        if len(wheel) >= 10:
            entry["steer_axis_x"] = to_float(wheel[4], 0.0)
            entry["steer_axis_y"] = to_float(wheel[5], 1.0)
            entry["steer_axis_z"] = to_float(wheel[6], 0.0)
            entry["steer_pivot_x"] = to_float(wheel[7])
            entry["steer_pivot_y"] = to_float(wheel[8]) + y_offset
            entry["steer_pivot_z"] = to_float(wheel[9])
        else:
            # Per Readme_Aircraft.txt's own doc: "回転軸を省略すると(0,1,0)が使用される" - and the steering pivot itself, when not separately given, is this same wheel's own main pivot (there's no OTHER sensible default - a steered wheel has to pivot around SOME point, and this wheel's own position is the only one given at all in that case).
            entry["steer_axis_x"] = 0.0
            entry["steer_axis_y"] = 1.0
            entry["steer_axis_z"] = 0.0
            entry["steer_pivot_x"] = entry["pivot_x"]
            entry["steer_pivot_y"] = entry["pivot_y"]
            entry["steer_pivot_z"] = entry["pivot_z"]
        wheel_parts.append(entry)

    for i, wheel in enumerate(entries.get("addpartsteeringwheel", [])):
        # AddPartSteeringWheel = posX, posY, posZ [, axisX, axisY, axisZ, maxAngle].
        if len(wheel) < 3:
            continue
        steering_wheel_parts.append({
            "part": resolve(STEERING_WHEEL_WORDS, i, "AddPartSteeringWheel"),
            "pivot_x": to_float(wheel[0]), "pivot_y": to_float(wheel[1]) + y_offset, "pivot_z": to_float(wheel[2]),
            "axis_x": to_float(wheel[3], 0.0) if len(wheel) >= 4 else 0.0,
            "axis_y": to_float(wheel[4], 0.0) if len(wheel) >= 5 else 0.0,
            "axis_z": to_float(wheel[5], 1.0) if len(wheel) >= 6 else 1.0,
            "max_angle": to_float(wheel[6], 130.0) if len(wheel) >= 7 else 130.0,
        })

    if wheel_parts or steering_wheel_parts:
        warnings.append(f"generated {len(wheel_parts)} wheel_parts and {len(steering_wheel_parts)} "
                         f"steering_wheel_parts - each wheel spins based on this vehicle's own current speed "
                         f"(PartWheelRot) and steers based on the current sideways input (up to its own "
                         f"steerAngle); the steering wheel model itself turns the same way, up to its own "
                         f"maxAngle. Relies on the vehicle's own actual '<vehicle>_wheel{{N}}.obj'/"
                         f"'<vehicle>_steering_wheel{{N}}.obj'-style part files actually being found and merged "
                         f"in as matching '$wheel{{N}}'/'$steering_wheel{{N}}' groups.")

    return wheel_parts, steering_wheel_parts


# See build_crawler_tracks' own use of this for the full reasoning: how far from centreline a
# zero-magnitude crawler track X is pushed, in whichever direction its own sign already pointed. 1.3 matches a real,
# working vehicle's own observed track X magnitude - a reasonable middle-of-the-road width for a track sitting
# off to one side, without needing to know this specific vehicle's own actual hull width.
CRAWLER_TRACK_ZERO_X_FALLBACK_MAGNITUDE = 1.3


def build_crawler_tracks(entries: dict, warnings: list, available_part_names: set, y_offset: float = 0.0) -> list:
    """AddCrawlerTrack - see CrawlerTrackPart's own doc (Java side) for
    exactly how the resulting path gets animated (differential-steering
    speed per track, repeated-link placement along the path).

    Per Readme_Aircraft.txt's own doc:
    "AddCrawlerTrack = 履帯の表裏逆転, 1つの履帯の間隔, 履帯のXの位置,
    履帯の回転ポイントY/Z, 履帯の回転ポイントY/Z, ..."
    Each path point after the first 3 params is a single "Y/Z" token (slash-
    separated, NOT its own comma-separated params like everything else here).

    Part name uses "$crawler_track{N}" (with the
    underscore), matching every other part-naming convention here.

    y_offset: see build_seats'/merge_obj_files' own doc - applied to every
    path point's own Y value, same as every other Y coordinate here, so the
    path stays correct relative to the (now-shifted) mesh.
    """
    tracks = []
    CRAWLER_TRACK_WORDS = ["crawler_track", "crawlertrack"]

    def resolve(index: int) -> str:
        for word in CRAWLER_TRACK_WORDS:
            candidate = f"${word}{index}"
            if candidate in available_part_names:
                return candidate
        fallback = f"${CRAWLER_TRACK_WORDS[0]}{index}"
        tried = ", ".join(f"${w}{index}" for w in CRAWLER_TRACK_WORDS)
        warnings.append(f"AddCrawlerTrack #{index}: none of the candidate part names ({tried}) matched any "
                         f"actual part file found for this vehicle - defaulting to '{fallback}', which will "
                         f"almost certainly NOT animate (it doesn't match any real OBJ group). Check this "
                         f"vehicle's actual '<vehicle>_*.obj' part file names next to the main model.")
        return fallback

    for i, track in enumerate(entries.get("addcrawlertrack", [])):
        # tokens[0]=flip, [1]=linkSpacing, [2]=x, [3:]=Y/Z path points (each its own "Y/Z" slash-separated token).
        if len(track) < 6:  # need at least flip, spacing, x, and 2 path points (4 tokens minimum for a real loop, but 6 total tokens including the 3 leading params).
            warnings.append(f"AddCrawlerTrack #{i}: fewer than 2 path points given - skipping, a track needs at "
                             f"least 2 points to form any kind of loop at all.")
            continue
        path = []
        for point_token in track[3:]:
            if "/" not in point_token:
                warnings.append(f"AddCrawlerTrack #{i}: path point '{point_token}' isn't in the expected "
                                 f"'Y/Z' (slash-separated) format - skipping this point.")
                continue
            y_str, z_str = point_token.split("/", 1)
            # THIS (the config file's own path
            # points) DOES still get y_offset, same as every other Y
            # coordinate in this converter - it's ONLY the OBJ mesh's own
            # raw vertex data (see merge_obj_files' own is_crawler_track
            # doc) that's excluded from it, since that geometry's own
            # placement comes entirely from THIS path (which already
            # accounts for the offset), not from the mesh's own Y values.
            path.append({"y": to_float(y_str) + y_offset, "z": to_float(z_str)})
        if len(path) < 2:
            continue
        x_value = to_float(track[2])
        # A converted vehicle's own crawler track animation didn't move during a neutral turn, traced back to this specific track's own X being exactly zero: this project's own differential-steering formula (see entity.AbstractVehicleEntity's own tudursvehiclemod$getCrawlerTrackSpeed() doc) multiplies the turning term by this X value, so X=0 always yields zero speed difference regardless of how sharply the vehicle turns - a track placed exactly on the centreline can never counter-rotate. Yet MC Heli's own source apparently distinguished LEFT/RIGHT via the SIGN of a zero value (0.0 vs -0.0, both zero in magnitude but opposite in IEEE-754 sign) - suggesting the original author intended two symmetric, opposite-side tracks and only wrote a zero-magnitude placeholder for each, relying on the sign alone to carry which side is which. math.copysign preserves that sign onto a real nonzero magnitude here, so the conversion keeps the author's own left/right intent while giving the differential-steering formula something to actually differ on.
        if x_value == 0.0:
            x_value = math.copysign(CRAWLER_TRACK_ZERO_X_FALLBACK_MAGNITUDE, x_value)
        tracks.append({
            "part": resolve(i),
            "flip": to_bool(track[0]),
            "link_spacing": to_float(track[1], 0.5),
            "x": x_value,
            "path": path,
        })

    if tracks:
        warnings.append(f"generated {len(tracks)} crawler_tracks - each track's own speed (and direction) is "
                         f"derived from this vehicle's own current forward speed and turn rate together (real "
                         f"differential/skid-steering physics), based on each track's own X position (negative "
                         f"= right, positive = left). Relies on the vehicle's own actual "
                         f"'<vehicle>_crawler_track{{N}}.obj'-style part file (a single track LINK, repeated "
                         f"around the path) actually being found and merged in as a matching "
                         f"'$crawler_track{{N}}' group.")

    return tracks


def build_seats(entries: dict, y_offset: float = 0.0) -> list:
    """y_offset: see merge_obj_files' own doc - the SAME offset applied to the
    mesh itself must also be applied here, or seat positions would no longer
    line up with the (now-shifted) model geometry at all.

    SEAT_Y_CORRECTION: an additional, separate adjustment reported by hand
    (comparing against MC Heli's own actual seat height) - not derived from
    any documented MC Heli field, just a fixed correction based on that report.

    MC Heli assigns seat numbers by DECLARATION
    ORDER across BOTH AddSeat and AddGunnerSeat together, whichever order
    they actually appear in the file in - NOT all AddSeat first and then all
    AddGunnerSeat after (an earlier version of this function did exactly
    that, which silently assigned wrong seat numbers to every weapon/
    weapon_part whenever a vehicle's own file interleaved the two, e.g.
    several AddGunnerSeat lines followed by a SECOND AddSeat further down -
    everything after the first mismatch would end up off by however many
    seats were reordered). Uses __ordered__ (see parse_mcheli_file's own
    doc) rather than entries["addseat"]/entries["addgunnerseat"] separately,
    specifically to preserve their true relative order.

    The seat declared FIRST overall (regardless of which of the two
    directives it is) is always the pilot/driver - "1つ目がパイロットの座席"
    per Readme_Aircraft.txt's own AddSeat doc.

    The only real difference between the two directives (a direct
    clarification) is the VIEW POSITION: AddGunnerSeat can specify its own
    dedicated camera position (falling back to CameraPosition if it doesn't -
    see Readme_Aircraft.txt's own doc, "省略時は CameraPosition の位置になる"),
    while a plain AddSeat has no such per-seat camera override at all. The
    vehicle-level CameraPosition directive itself only ever actually applies
    to the pilot seat specifically (that's the whole reason AddGunnerSeat
    needs its OWN, separate camera fields at all - a gunner seat obviously
    can't share the pilot's own CameraPosition by default the same way).
    """
    seats = []

    # ALL CameraPosition directives are collected (not
    # just the first), matching MC Heli's own documented "複数設定すると、
    # Hキーでそれぞれ視点を変えられる" (setting several lets you cycle
    # between each viewpoint with a key - see AbstractVehicleEntity's own
    # tudursvehiclemod$cycleCameraPosition() doc for how this project
    # actually implements that cycling).
    camera_positions = []
    for key, tokens in entries.get("__ordered__", []):
        if key != "cameraposition" or len(tokens) < 3:
            continue
        camera_entry = {
            "x": to_float(tokens[0]), "y": to_float(tokens[1]) + y_offset, "z": to_float(tokens[2]),
        }
        if len(tokens) >= 4:
            camera_entry["force_camera"] = to_bool(tokens[3])
        if len(tokens) >= 6:
            camera_entry["fixed_yaw"] = to_float(tokens[4])
            camera_entry["fixed_pitch"] = to_float(tokens[5])
        camera_positions.append(camera_entry)

    enable_parachuting_global = first(entries, "enableparachuting", ["false"])[0].strip().lower() == "true"

    for key, tokens in entries.get("__ordered__", []):
        if key not in ("addseat", "addgunnerseat"):
            continue
        if len(tokens) < 3:
            continue
        index = len(seats)
        is_driver = index == 0
        entry = {
            "name": "driver" if is_driver else (f"gunner{index}" if key == "addgunnerseat" else f"seat{index}"),
            "offset_x": to_float(tokens[0]), "offset_y": to_float(tokens[1]) + y_offset + SEAT_Y_CORRECTION,
            "offset_z": to_float(tokens[2]),
            "driver": is_driver,
        }
        # Per EnableParachuting's own doc above: seat 3 onward (0-indexed: index >= 2).
        if enable_parachuting_global and index >= 2:
            entry["enable_parachuting"] = True
        if is_driver:
            # Only the pilot seat uses the vehicle-level CameraPosition(s), regardless of which directive declared it.
            if camera_positions:
                entry["camera_positions"] = camera_positions
        elif key == "addgunnerseat":
            if len(tokens) >= 6:
                gunner_camera = {"x": to_float(tokens[3]), "y": to_float(tokens[4]) + y_offset, "z": to_float(tokens[5])}
                entry["camera_positions"] = [gunner_camera]
            elif camera_positions:
                # "省略時は CameraPosition の位置になる" (if omitted, falls back to CameraPosition's own position(s)) - the FULL list, so a gunner without their own dedicated camera can still cycle through the same multiple pilot positions.
                entry["camera_positions"] = camera_positions
        # A non-pilot plain AddSeat has no camera override mechanism at all - per this function's own doc.
        seats.append(entry)

    if not seats:
        seats.append({"name": "driver", "offset_x": 0.0, "offset_y": 1.0 + y_offset + SEAT_Y_CORRECTION,
                       "offset_z": 0.0, "driver": True})
    return seats


def compute_addweapon_part_ownership(entries: dict) -> list:
    """Returns a list PARALLEL to entries["__ordered__"] (same length, same
    order) - for each position that's an AddWeapon/AddTurretWeapon entry,
    the part name (e.g. "$weapon2") of whichever AddPartWeapon/
    AddPartTurretWeapon/AddPartRotWeapon entry MOST RECENTLY preceded it
    with nothing else (other than more AddWeapon/AddTurretWeapon entries
    themselves) appearing in between - or None if no such part exists (no
    AddPartWeapon-family entry has appeared yet, or something else broke
    the chain). Every other position (not an AddWeapon-family entry at
    all) is always None too.

    This project's own conversion additionally
    supports linking a SPECIFIC muzzle (AddWeapon) to a SPECIFIC visual
    part (AddPartWeapon) - something MC Heli itself has no concept of at
    all - purely by relying on DECLARATION ORDER: "AddPartWeapon, then
    some AddWeapon lines, then the NEXT AddPartWeapon, then ITS OWN
    AddWeapon lines" is read as "each AddPartWeapon owns whichever
    AddWeapon lines directly follow it, until the next AddPartWeapon (or
    anything else) breaks that chain". This is DELIBERATELY conservative,
    Also: if a file's own AddWeapon/AddPartWeapon
    lines are NOT cleanly grouped this way - e.g. MC Heli's own more
    common convention of listing ALL AddWeapon lines together in one
    block, then ALL AddPartWeapon lines in a separate block afterward,
    which naturally means every AddWeapon line appears before ANY
    AddPartWeapon has been seen yet - NONE of them get linked here at
    all, falling back entirely to the existing weapon_name-based matching
    in AbstractVehicleEntity's own tryFireWeapon (see WeaponPart's own
    weaponName doc), exactly as requested rather than forcing a grouping
    the source file doesn't actually support.

    Uses the exact same "$weapon{N}" numbering scheme (a single shared
    counter across AddPartWeapon/AddPartTurretWeapon/AddPartRotWeapon/
    AddPartWeaponMissile, in that declaration order) as
    build_weapon_parts()'s own resolve() helper, so the part names
    produced here always line up with the ones actually written out
    there - see that function's own doc.
    """
    ordered = entries.get("__ordered__", [])
    result = [None] * len(ordered)
    weapon_index = 0
    current_part_name = None
    for i, (key, tokens) in enumerate(ordered):
        if key in ("addpartweapon", "addpartturretweapon", "addpartrotweapon", "addpartweaponmissile"):
            index = weapon_index
            weapon_index += 1
            if key == "addpartweaponmissile":
                current_part_name = None
                continue
            current_part_name = f"$weapon{index}"
        elif key in ("addweapon", "addturretweapon"):
            result[i] = current_part_name
        elif key == "addpartweaponchild":
            pass  # doesn't break the chain, and doesn't consume a weapon_index slot either - see build_weapon_parts' own doc
        else:
            current_part_name = None
    return result


def build_weapons(entries: dict, addon_root: Path, seat_count: int, namespace: str, y_offset: float = 0.0,
                   is_ground_weapon_category: bool = False):
    """y_offset: see build_seats' own doc / merge_obj_files'. Returns
    (weapons_list, name_to_seat_index) - the second is used by
    build_weapon_parts() to resolve AddPartWeapon/AddPartTurretWeapon's own
    weapon name reference(s) down to a seat index (see its own doc for why a
    seat index, not the weapon's own list index, is what a WeaponPart
    actually needs).

    Each weapon entry only stores WHICH weapon file to read stats from
    (weapon_name) - NOT damage/velocity/cooldown/gravity/sound/bullet_model
    values baked in at conversion time. Those are read LIVE from that
    weapon's own assets/<namespace>/weapons/<weapon_name>.txt file at runtime
    instead (see asset.WeaponStatsLoader and copy_weapon_assets(), which
    Copies that file into the output pack): editing
    the original MC Heli-format file directly for balance tweaks is more
    convenient than re-running this converter and regenerating every vehicle
    referencing that weapon each time.

    is_ground_weapon_category: MC Heli's own ground
    weapons (this project's "Vehicles" category - see
    CATEGORY_TO_ENTITY_TYPE's own comment) sometimes specify their own
    aim/rotation range via separate, vehicle-level "MinRotationYaw"/
    "MaxRotationYaw"/"MinRotationPitch"/"MaxRotationPitch" directives
    instead of AddWeapon's own trailing 5 fields (DefaultYaw/MinYaw/
    MaxYaw/MinPitch/MaxPitch) - when this AddWeapon line doesn't already
    have its own trailing range (this_aim_range is still None below) and
    this flag is set, those 4 directives are read as a fallback aim_range
    instead (default_yaw defaults to 0.0, since none of the four
    directives themselves specify one).
    """
    # Group by (weapon_name, seat_index).
    weapons = []
    # (weapon_name.lower()) -> (seat_index, pilot_usable, aim_range dict or None) - see build_weapon_parts()'s own doc for why pilot_usable/aim_range travel with this.
    name_to_seat = {}
    groups = {}  # (name, seat) -> list of offset dicts
    aim_ranges = {}  # (name, seat) -> aim_range dict or None
    pilot_usable_by_key = {}  # (name, seat) -> bool
    group_order = []
    ownership = compute_addweapon_part_ownership(entries)
    # Per is_ground_weapon_category's own doc - read once, applied to every AddWeapon in this file that doesn't already specify its own trailing range.
    ground_rotation_range = None
    if is_ground_weapon_category:
        min_yaw_raw = first(entries, "minrotationyaw", [None])[0]
        max_yaw_raw = first(entries, "maxrotationyaw", [None])[0]
        min_pitch_raw = first(entries, "minrotationpitch", [None])[0]
        max_pitch_raw = first(entries, "maxrotationpitch", [None])[0]
        if min_yaw_raw is not None or max_yaw_raw is not None or min_pitch_raw is not None or max_pitch_raw is not None:
            ground_rotation_range = {
                "default_yaw": 0.0,
                "min_yaw": to_float(min_yaw_raw, -180.0) if min_yaw_raw is not None else -180.0,
                "max_yaw": to_float(max_yaw_raw, 180.0) if max_yaw_raw is not None else 180.0,
                "min_pitch": to_float(min_pitch_raw, -90.0) if min_pitch_raw is not None else -90.0,
                "max_pitch": to_float(max_pitch_raw, 90.0) if max_pitch_raw is not None else 90.0,
            }
    for i, (key, w) in enumerate(entries.get("__ordered__", [])):
        if key not in ("addweapon", "addturretweapon"):
            continue
        if len(w) < 4:
            continue
        weapon_name = w[0].strip()
        x, y, z = to_float(w[1]), to_float(w[2]) + y_offset, to_float(w[3])
        # AddWeapon = name, x, y, z, yaw, pitch, pilotUsable, seat, DefaultYaw, MinYaw, MaxYaw, MinPitch, MaxPitch
        mount_yaw = to_float(w[4]) if len(w) >= 5 else 0.0
        mount_pitch = to_float(w[5]) if len(w) >= 6 else 0.0
        # Per Readme_Aircraft.txt's own doc: "true, N" -> seat N's occupant can
        # use it, falling back to the pilot when seat N is empty; "false, N" ->
        # ONLY seat N's occupant can use it, never the pilot; omitted entirely
        # defaults to "true, 1" (pilot-only, which is trivially always true
        # since seat 1 - index 0 - IS the pilot).
        pilot_usable = to_bool(w[6]) if len(w) >= 7 else True
        seat_index = 0
        if len(w) >= 8 and w[7].strip().lstrip("-").isdigit():
            seat_index = max(0, int(w[7].strip()) - 1)
        seat_index = min(seat_index, max(seat_count - 1, 0))
        # Aim range - present only if this specific AddWeapon line has all 5 trailing params. Computed here (not just below, where aim_ranges[key] is set) so it's available via name_to_seat even for build_weapon_parts()'s own use, which doesn't otherwise have access to a weapon's own stats.
        this_aim_range = None
        if len(w) >= 13:
            this_aim_range = {
                "default_yaw": to_float(w[8]), "min_yaw": to_float(w[9]), "max_yaw": to_float(w[10]),
                "min_pitch": to_float(w[11]), "max_pitch": to_float(w[12]),
            }
        elif ground_rotation_range is not None:
            this_aim_range = ground_rotation_range
        name_to_seat[weapon_name.lower()] = (seat_index, pilot_usable, this_aim_range)
        if not (addon_root / "assets" / "mcheli" / "weapons" / f"{weapon_name}.txt").exists():
            print(f"warning: weapon '{weapon_name}' has no matching weapons/{weapon_name}.txt - "
                  f"it will use fallback stats (see asset.WeaponStats.FALLBACK) until that file exists")
        key2 = (weapon_name.lower(), seat_index)
        if key2 not in groups:
            groups[key2] = []
            group_order.append(key2)
            pilot_usable_by_key[key2] = pilot_usable
            aim_ranges[key2] = this_aim_range
        offset_entry = {"x": x, "y": y, "z": z, "mount_yaw": mount_yaw, "mount_pitch": mount_pitch}
        # See compute_addweapon_part_ownership()'s own
        # doc - links this SPECIFIC muzzle to a SPECIFIC visual part when
        # the file's own declaration order supports it, rather than every
        # muzzle sharing whichever part merely happens to match by weapon
        # name (this offset's own weapon might have SEVERAL parts, e.g. a
        # dual/quad mount split across more than one AddPartWeapon).
        linked_part = ownership[i]
        if linked_part is not None:
            offset_entry["linked_part"] = linked_part
        groups[key2].append(offset_entry)

    # CameraRotationSpeed - applied uniformly to every weapon entry's own turret_rotation_speed at conversion time (detail: docs/IMPLEMENTATION_NOTES.md "武装旋回速度制限機能"). Absent means no limit.
    camera_rotation_speed_token = first(entries, "camerarotationspeed")
    camera_rotation_speed = to_float(camera_rotation_speed_token[0], 0.0) if camera_rotation_speed_token else None

    for weapon_name, seat_index in group_order:
        key2 = (weapon_name, seat_index)
        weapon_entry = {
            "seat_index": seat_index,
            "offsets": groups[key2],
            "projectile_item": DEFAULT_PROJECTILE_ITEM,
            "weapon_name": weapon_name,
        }
        # Only actually meaningful for a non-pilot (gunner) seat - the pilot's own seat (0) has no fallback to fall back TO, so this is omitted there to keep the JSON uncluttered.
        if seat_index != 0 and pilot_usable_by_key.get(key2, True):
            weapon_entry["pilot_usable"] = True
        if aim_ranges.get(key2):
            weapon_entry["aim_range"] = aim_ranges[key2]
        if camera_rotation_speed is not None:
            weapon_entry["turret_rotation_speed"] = camera_rotation_speed
        weapons.append(weapon_entry)
    return weapons, name_to_seat


def build_weapon_parts(entries: dict, weapon_name_to_seat: dict, warnings: list, available_part_names: set,
                        is_ground_weapon_category: bool = False, y_offset: float = 0.0) -> list:
    """y_offset: see build_seats'/merge_obj_files' own doc - the SAME
    offset applied to the actual model geometry (every vertex, including
    every weapon part's own sub-mesh - see merge_obj_files' own doc) and
    to every OTHER pivot/offset elsewhere in this converter (seats,
    AddWeapon's own y, spinning parts, toggle parts) must ALSO be added
    to every pivot_y computed here, for exactly the same reason - per a
    direct report/confirmation: this was the ONE place in the whole
    converter that had been missed, leaving a weapon part's own rotation
    PIVOT sitting y_offset (0.30 for a typical non-water-referenced
    vehicle) below where the actual (correctly-shifted) model geometry
    it's supposed to rotate around actually ended up, throwing the
    visual center of rotation off by that same amount - most obviously
    seen on a MULTI-PART turret (a parent body plus a child barrel, each
    with a wrong pivot of their own compounding together) but not
    actually specific to that case at all; ANY WeaponPart's own pivot
    from this function was equally affected, ground-weapon-style
    AddPart/AddChildPart and aircraft-style AddPartWeapon/
    AddPartWeaponChild alike.

    AddPartWeapon/AddPartTurretWeapon/AddPartRotWeapon each rotate a named
    part to track whoever is aiming their associated weapon's seat - see
    asset.WeaponPart's own doc. AddPartTurretWeapon is treated identically to
    AddPartWeapon here (this project's simplified version doesn't also shift
    the part's own position as a real turret ring would - see WeaponPart's own
    doc). AddPartRotWeapon's own "spins continuously while firing" behavior
    (for gatling-style weapons - see WeaponPart's own spinsWhileFiring doc)
    IS implemented, same as AddPart/AddChildPart's own part_type=1 (see the
    "addpart"/"addchildpart" branches below).

    is_ground_weapon_category: MC
    Heli's own ground weapons (this project's "Vehicles" category - see
    CATEGORY_TO_ENTITY_TYPE's own comment) use their OWN dedicated
    "AddPart"/"AddChildPart" directives (see Readme_Aircraft.txt's own
    "■地上兵器設定ファイル" section, provided directly) rather than
    AddPartWeapon/AddPartWeaponChild - same overall shape (a part that
    rotates to track the operator's own aim, optionally with a child
    riding along), just with a slightly different parameter layout of its
    own (no explicit weapon-name field - see the "addpart"/"addchildpart"
    branches below for the actual parsing, and how the associated weapon
    is matched by FILE ORDER instead) and its OWN "$partN"/"$partN_M"
    model file naming (normalized to "$weaponN"/"$weaponN_M" elsewhere -
    see normalize_weapon_part_names()'s own doc). Only when a ground
    weapon has NEITHER an AddPart NOR any AddPartWeapon-family directive
    at all does this fall back to generating an implicit part around the
    model's own origin (0,0,0) instead - see the bottom of this
    function's own body - as a last-resort safety net so that case still
    tracks aim at all rather than not rotating whatsoever.

    NOW implemented: AddPartWeaponChild (a sub-part riding along with its
    parent AddPartWeapon/AddPartTurretWeapon/AddPartRotWeapon - see
    Readme_Aircraft.txt's own doc). Per that doc, a child directive must be
    written directly on the line right after its parent (tracked here via
    current_parent, which resets to None on any OTHER directive in between -
    consecutive AddPartWeaponChild lines all attach to the SAME parent,
    matching "multiple can be added" in the readme), and inherits its
    parent's own seat_index/hide_for_gunner ("連動武器名とガンナー時非表示は
    親パーツと同じになる" - the associated weapon name and gunner-hide
    status are the same as the parent part) while using its OWN yaw_follow/
    pitch_follow/pivot/recoil_distance from its own config line - i.e. it
    tracks whatever its parent is aiming at, but can have entirely different
    rotation behavior of its own on top of that. Does NOT consume a shared
    "weapon{N}" numbering slot (see weapon_index below) - it uses its own
    "weapon{N}_{M}" sub-numbering tied to its parent's own index instead,
    per the readme's own documented model file naming convention
    ("機体名_weapon?_0.obj", ? = parent part's number).

    AddPartWeaponMissile becomes an AmmoPart (see that class's own doc) rather
    than a WeaponPart - it doesn't track aim at all, just visibility, hidden
    once its own weapon's remaining ammo drops to this part's own slot index
    or below. Several AddPartWeaponMissile lines for the SAME weapon_name get
    sequential slot indices (0, 1, 2, ...) in the order they appear, so they
    disappear one at a time as that weapon is fired - a
    description of the intended behavior.

    Shares one "weapon{N}" numbering sequence across ALL of AddPartWeapon/
    AddPartTurretWeapon/AddPartRotWeapon/AddPartWeaponMissile (not AddPartWeaponChild,
    which uses its OWN "weapon{N}_{M}" sub-numbering under its parent) - same
    "one counter per family, shared across that family's own directive types"
    approach as build_toggle_parts() uses for hatch/canopy/lg, since MC Heli's
    own model file naming convention numbers them together
    ("<vehicle>_weapon?.obj", see this module's own top-level doc).

    Returns (weapon_parts, ammo_parts).
    """
    parts = []
    ammo_parts = []

    def resolve(index: int, add_type: str) -> str:
        candidate = f"$weapon{index}"
        if candidate not in available_part_names:
            warnings.append(f"{add_type} #{index}: expected a part file for '{candidate}' but none was found "
                             f"for this vehicle - using '{candidate}' anyway, which will almost certainly NOT "
                             f"render/animate. Check this vehicle's actual '<vehicle>_*.obj' part file names and "
                             f"fix weapon_parts[].part by hand if it's actually named something else.")
        return candidate

    def resolve_seat(raw_name_field: str) -> tuple:
        # "none" (no association) or "name1 / name2 / ..." (shared seat across ammo variants on the same mount.
        if raw_name_field.strip().lower() == "none":
            return -1, False, None, None
        for candidate in raw_name_field.split("/"):
            candidate_name = candidate.strip().lower()
            resolved = weapon_name_to_seat.get(candidate_name)
            if resolved is not None:
                seat_index, pilot_usable, aim_range = resolved
                return seat_index, pilot_usable, aim_range, candidate_name
        return -1, False, None, None

    weapon_index = 0
    ammo_slot_by_weapon = {}
    # See this function's own AddPartWeaponChild doc for why this is tracked and reset the way it is.
    current_parent = None
    child_counter_by_parent = {}
    # MC Heli's own ground weapons have no
    # per-weapon distinction at all - "割り当てられた武器を共通の動きの
    # なかで動作させるだけ" (they just operate the ONE assigned weapon
    # within shared/common motion), since a ground weapon's own fixed
    # armament only ever has "turret"/"barrel"-style structural elements
    # to implement, never several independently-tracking weapons sharing
    # one file. An earlier version of this matched AddPart to AddWeapon
    # by FILE ORDER instead (the Nth AddPart to the Nth AddWeapon) - which
    # breaks the instant a ground weapon has MORE structural AddPart
    # entries than actual weapons (e.g. a separate yaw-only base AND a
    # Yaw+pitch body, both serving the SAME single gun)
    # report/confirmation, that mismatch left every AddPart past the
    # first entirely unlinked from any weapon at all (no weapon_name, no
    # aim_range), silently breaking both the actual firing/muzzle
    # position tracking AND this part's own aim-range clamping for any
    # such vehicle. Every AddPart/AddChildPart-created part in this
    # file now shares the SAME single ground weapon instead (the first
    # one - see GROUND_WEAPON_NAME's own doc for the rare-multi-weapon
    # case) regardless of which AddPart line it came from.
    GROUND_WEAPON_NAME = next(iter(weapon_name_to_seat.keys()), None)
    # No explicit recoil DISTANCE is ever given for AddPart's own Param4=2
    # ("recoils when the weapon fires") - just that boolean-ish flag - so
    # this is a reasonable, small default kick rather than a value read
    # from the file itself (there isn't one to read).
    GROUND_WEAPON_RECOIL_DISTANCE_DEFAULT = 0.1
    for key, tokens in entries.get("__ordered__", []):
        if key == "addpart":
            # AddPart = show_in_first_person, yaw_follow, pitch_follow, part_type (0=none, 1=rotates when firing, 2=recoils when firing), pivotX, pivotY, pivotZ
            index = weapon_index
            weapon_index += 1
            current_parent = None
            if len(tokens) >= 7:
                show_in_first_person = to_bool(tokens[0])
                yaw_follow = to_bool(tokens[1])
                pitch_follow = to_bool(tokens[2])
                part_type = to_float(tokens[3], 0.0)
                pivot_x, pivot_y, pivot_z = to_float(tokens[4]), to_float(tokens[5]) + y_offset, to_float(tokens[6])
                recoil_distance = GROUND_WEAPON_RECOIL_DISTANCE_DEFAULT if part_type == 2.0 else 0.0
                # See GROUND_WEAPON_NAME's own doc above for why this is
                # the SAME single weapon for every AddPart in this file,
                # not looked up per-index.
                weapon_name = GROUND_WEAPON_NAME
                seat_index, pilot_usable, aim_range = (
                    weapon_name_to_seat[weapon_name] if weapon_name is not None else (0, True, None))
                part_entry = {
                    "part": resolve(index, "AddPart"),
                    "seat_index": seat_index,
                    # Readme_Aircraft.txt's own Param1 doc: "true=表示, false=非表示" (true=shown, false=hidden) during first-person view - the OPPOSITE sense from hide_for_gunner (true = actually hidden), hence the negation.
                    "hide_for_gunner": not show_in_first_person,
                    "yaw_follow": yaw_follow,
                    "pitch_follow": pitch_follow,
                    "pivot_x": pivot_x, "pivot_y": pivot_y, "pivot_z": pivot_z,
                    "recoil_distance": recoil_distance,
                }
                # Gatling-style continuous spin while
                # the weapon is actively being fired - same final effect as
                # AddPartRotWeapon (see that directive's own doc), just
                # reached via AddPart's own Param4=1 instead of a whole
                # separate directive.
                if part_type == 1.0:
                    part_entry["spins_while_firing"] = True
                if seat_index != 0 and pilot_usable:
                    part_entry["pilot_fallback"] = True
                if aim_range is not None:
                    part_entry["aim_range"] = aim_range
                if weapon_name is not None:
                    part_entry["weapon_name"] = weapon_name
                parts.append(part_entry)
                current_parent = {"seat_index": seat_index, "hide_for_gunner": not show_in_first_person,
                                   "pilot_usable": pilot_usable, "parent_index": index,
                                   "yaw_follow": yaw_follow, "pitch_follow": pitch_follow,
                                   "pivot_x": pivot_x, "pivot_y": pivot_y, "pivot_z": pivot_z,
                                   "aim_range": aim_range, "weapon_name": weapon_name}
        elif key == "addchildpart":
            # AddChildPart - same parameter shape as AddPart, must directly follow its own parent AddPart line (per Readme_Aircraft.txt's own doc) - same overall relationship as AddPartWeaponChild has to AddPartWeapon (see that branch's own doc for the two-stage parent/own rotation split this feeds into).
            if current_parent is None:
                warnings.append("AddChildPart found with no AddPart on the immediately preceding line - skipped, "
                                 "per Readme_Aircraft.txt's own doc that it must directly follow one")
                continue
            if len(tokens) < 7:
                warnings.append(f"AddChildPart has too few fields ({len(tokens)}, need at least 7) - skipped")
                continue
            parent_index = current_parent["parent_index"]
            child_index = child_counter_by_parent.get(parent_index, 0)
            child_counter_by_parent[parent_index] = child_index + 1
            child_part_name = f"$weapon{parent_index}_{child_index}"
            if child_part_name not in available_part_names:
                warnings.append(f"AddChildPart #{child_index} of weapon{parent_index}: expected a part file for "
                                 f"'{child_part_name}' but none was found for this vehicle - using it anyway, "
                                 f"which will almost certainly NOT render/animate.")
            child_show_in_first_person = to_bool(tokens[0])
            child_yaw_follow = to_bool(tokens[1])
            child_pitch_follow = to_bool(tokens[2])
            child_part_type = to_float(tokens[3], 0.0)
            child_pivot_x, child_pivot_y, child_pivot_z = to_float(tokens[4]), to_float(tokens[5]) + y_offset, to_float(tokens[6])
            child_recoil_distance = GROUND_WEAPON_RECOIL_DISTANCE_DEFAULT if child_part_type == 2.0 else 0.0
            child_entry = {
                "part": child_part_name,
                "seat_index": current_parent["seat_index"],
                "hide_for_gunner": not child_show_in_first_person,
                "yaw_follow": child_yaw_follow,
                "pitch_follow": child_pitch_follow,
                "pivot_x": child_pivot_x, "pivot_y": child_pivot_y, "pivot_z": child_pivot_z,
                "recoil_distance": child_recoil_distance,
                "child_info": {
                    "parent_yaw_follow": current_parent["yaw_follow"],
                    "parent_pitch_follow": current_parent["pitch_follow"],
                    "parent_pivot_x": current_parent["pivot_x"],
                    "parent_pivot_y": current_parent["pivot_y"],
                    "parent_pivot_z": current_parent["pivot_z"],
                },
            }
            # Example (the Phalanx CIWS's own
            # "本体-砲身" AddChildPart, part_type=1) - see the "addpart"
            # branch's own doc for why this is the same final effect as
            # AddPartRotWeapon.
            if child_part_type == 1.0:
                child_entry["spins_while_firing"] = True
            if current_parent["seat_index"] != 0 and current_parent["pilot_usable"]:
                child_entry["pilot_fallback"] = True
            if current_parent.get("aim_range") is not None:
                child_entry["aim_range"] = current_parent["aim_range"]
            if current_parent.get("weapon_name") is not None:
                child_entry["weapon_name"] = current_parent["weapon_name"]
            parts.append(child_entry)
            # current_parent deliberately NOT reset here - consecutive AddChildPart lines all attach to the same parent, same as AddPartWeaponChild.
        elif key in ("addpartweapon", "addpartturretweapon", "addpartrotweapon", "addpartweaponmissile"):
            index = weapon_index
            weapon_index += 1
            add_type = {"addpartweapon": "AddPartWeapon", "addpartturretweapon": "AddPartTurretWeapon",
                        "addpartrotweapon": "AddPartRotWeapon", "addpartweaponmissile": "AddPartWeaponMissile"}[key]
            if key == "addpartweaponmissile":
                current_parent = None
                # AddPartWeaponMissile = weapon_name, gunner_hide?, yaw_follow, pitch_follow, pivotX, pivotY, pivotZ
                if len(tokens) >= 1:
                    weapon_name = tokens[0].strip().lower()
                    slot_index = ammo_slot_by_weapon.get(weapon_name, 0)
                    ammo_slot_by_weapon[weapon_name] = slot_index + 1
                    ammo_parts.append({
                        "part": resolve(index, add_type),
                        "weapon_name": weapon_name,
                        "slot_index": slot_index,
                    })
                continue
            # AddPartWeapon/AddPartTurretWeapon = weaponName(s), hideForGunner, yawFollow, pitchFollow, pivotX, pivotY, pivotZ, [recoilDistance] AddPartRotWeapon =...
            current_parent = None
            if len(tokens) >= 7:
                seat_index, pilot_usable, aim_range, matched_weapon_name = resolve_seat(tokens[0])
                hide_for_gunner = to_bool(tokens[1])
                parent_yaw_follow = to_bool(tokens[2])
                parent_pitch_follow = to_bool(tokens[3])
                parent_pivot_x, parent_pivot_y, parent_pivot_z = to_float(tokens[4]), to_float(tokens[5]) + y_offset, to_float(tokens[6])
                part_entry = {
                    "part": resolve(index, add_type),
                    "seat_index": seat_index,
                    "hide_for_gunner": hide_for_gunner,
                    "yaw_follow": parent_yaw_follow,
                    "pitch_follow": parent_pitch_follow,
                    "pivot_x": parent_pivot_x, "pivot_y": parent_pivot_y, "pivot_z": parent_pivot_z,
                    "recoil_distance": to_float(tokens[7], 0.0) if key != "addpartrotweapon" and len(tokens) >= 8 else 0.0,
                }
                # AddPartRotWeapon is otherwise
                # identical to AddPartWeapon (same $weaponN model naming,
                # same position/pivot handling - already grouped together
                # above) - the one actual difference ("ガトリング用で、武器
                # 使用中は回転する" - for gatling guns, spins while the
                # weapon is in use) is captured here as a simple flag; the
                # actual spin behavior/speed lives entirely on the Java
                # side (see WeaponStats's own rotationSpeedPerSecond doc).
                if key == "addpartrotweapon":
                    part_entry["spins_while_firing"] = True
                # Only meaningful for a non-pilot seat - see build_weapons()'s own doc.
                if seat_index != 0 and pilot_usable:
                    part_entry["pilot_fallback"] = True
                # This part's own tracked yaw/pitch is
                # clamped around this weapon's own DefaultYaw (see
                # WeaponAimRange's own doc) exactly like the actual firing
                # direction already was, rather than the visual part being
                # free to swing further than the weapon can actually aim.
                if aim_range is not None:
                    part_entry["aim_range"] = aim_range
                # Lets AbstractVehicleEntity's own
                # tryFireWeapon find the specific part tracking THIS
                # specific weapon (unambiguous even if several weapons
                # share one seat), to adjust the actual firing/muzzle
                # position to follow this part's own current rotation.
                if matched_weapon_name is not None:
                    part_entry["weapon_name"] = matched_weapon_name
                parts.append(part_entry)
                current_parent = {"seat_index": seat_index, "hide_for_gunner": hide_for_gunner,
                                   "pilot_usable": pilot_usable, "parent_index": index,
                                   "yaw_follow": parent_yaw_follow, "pitch_follow": parent_pitch_follow,
                                   "pivot_x": parent_pivot_x, "pivot_y": parent_pivot_y, "pivot_z": parent_pivot_z,
                                   "aim_range": aim_range, "weapon_name": matched_weapon_name}
        elif key == "addpartweaponchild":
            # AddPartWeaponChild = yawFollow, pitchFollow, pivotX, pivotY, pivotZ, recoilDistance
            if current_parent is None:
                warnings.append("AddPartWeaponChild found with no AddPartWeapon/AddPartTurretWeapon/"
                                 "AddPartRotWeapon on the immediately preceding line - skipped, per "
                                 "Readme_Aircraft.txt's own doc that it must directly follow one")
                continue
            if len(tokens) < 5:
                warnings.append(f"AddPartWeaponChild has too few fields ({len(tokens)}, need at least 5) - skipped")
                continue
            parent_index = current_parent["parent_index"]
            child_index = child_counter_by_parent.get(parent_index, 0)
            child_counter_by_parent[parent_index] = child_index + 1
            child_part_name = f"$weapon{parent_index}_{child_index}"
            if child_part_name not in available_part_names:
                warnings.append(f"AddPartWeaponChild #{child_index} of weapon{parent_index}: expected a part "
                                 f"file for '{child_part_name}' but none was found for this vehicle - using it "
                                 f"anyway, which will almost certainly NOT render/animate.")
            child_entry = {
                "part": child_part_name,
                # Inherited from the parent, per Readme_Aircraft.txt's own doc ("連動武器名とガンナー時非表示は親パーツと同じになる").
                "seat_index": current_parent["seat_index"],
                "hide_for_gunner": current_parent["hide_for_gunner"],
                "yaw_follow": to_bool(tokens[0]),
                "pitch_follow": to_bool(tokens[1]),
                "pivot_x": to_float(tokens[2]), "pivot_y": to_float(tokens[3]) + y_offset, "pivot_z": to_float(tokens[4]),
                "recoil_distance": to_float(tokens[5], 0.0) if len(tokens) >= 6 else 0.0,
                # A child UNCONDITIONALLY follows
                # whatever rotation its own parent undergoes (around the
                # PARENT's own pivot, not the child's) - its own yaw_follow/
                # pitch_follow above only control WHETHER the child ALSO gets
                # its own additional, independent rotation around its OWN
                # pivot on top of that, not whether it follows the parent at
                # all. See VehicleEntityRenderer's own doc for how this is
                # actually applied as a two-stage transform. Nested under
                # child_info (matching WeaponPart.ChildInfo's own JSON
                # schema - see that record's own doc for why this is now a
                # nested object rather than several flat top-level fields).
                "child_info": {
                    "parent_yaw_follow": current_parent["yaw_follow"],
                    "parent_pitch_follow": current_parent["pitch_follow"],
                    "parent_pivot_x": current_parent["pivot_x"],
                    "parent_pivot_y": current_parent["pivot_y"],
                    "parent_pivot_z": current_parent["pivot_z"],
                },
            }
            if current_parent["seat_index"] != 0 and current_parent["pilot_usable"]:
                child_entry["pilot_fallback"] = True
            # Same weapon as the parent, so the same aim-range clamp applies.
            if current_parent.get("aim_range") is not None:
                child_entry["aim_range"] = current_parent["aim_range"]
            if current_parent.get("weapon_name") is not None:
                child_entry["weapon_name"] = current_parent["weapon_name"]
            parts.append(child_entry)
            # current_parent deliberately NOT reset here - consecutive AddPartWeaponChild lines all attach to the same parent (see this function's own doc).
        else:
            current_parent = None

    if is_ground_weapon_category:
        # MC Heli's own ground weapons (this
        # project's "Vehicles" category, converted onto
        # entity.StaticEmplacementEntity - see CATEGORY_TO_ENTITY_TYPE's
        # own comment) rotate to track aim around the model's own ORIGIN
        # (0,0,0) instead of having an explicit AddPartWeapon/
        # AddPartTurretWeapon/AddPartRotWeapon directive of their own at
        # all - unlike every other category, where a weapon with no such
        # directive simply never rotates/tracks aim visually (its OWN
        # muzzle position still fires correctly, it just doesn't visibly
        # swing to follow). Any weapon actually registered via AddWeapon
        # (weapon_name_to_seat) that doesn't already have a WeaponPart
        # tracking it (from an explicit directive above) gets one
        # generated here instead, using the origin as its own pivot,
        # continuing the SAME shared "weapon{N}" numbering sequence as
        # every explicit AddPartWeapon-family entry above (so its own
        # part still resolves against whatever "$weaponN" this vehicle's
        # own $part-renamed geometry actually provides - see
        # normalize_weapon_part_names()'s own doc).
        already_tracked_weapon_names = {p.get("weapon_name") for p in parts if p.get("weapon_name") is not None}
        for weapon_name, (seat_index, pilot_usable, aim_range) in weapon_name_to_seat.items():
            if weapon_name in already_tracked_weapon_names:
                continue
            index = weapon_index
            weapon_index += 1
            implicit_entry = {
                "part": resolve(index, "AddWeapon (implicit ground-weapon origin pivot)"),
                "seat_index": seat_index,
                "hide_for_gunner": False,
                "yaw_follow": True,
                "pitch_follow": True,
                "pivot_x": 0.0, "pivot_y": y_offset, "pivot_z": 0.0,
                "recoil_distance": 0.0,
                "weapon_name": weapon_name,
            }
            if seat_index != 0 and pilot_usable:
                implicit_entry["pilot_fallback"] = True
            if aim_range is not None:
                implicit_entry["aim_range"] = aim_range
            parts.append(implicit_entry)

    return parts, ammo_parts


def build_camera_parts(entries: dict, warnings: list, available_part_names: set, y_offset: float = 0.0) -> list:
    """AddPartCamera = posX, posY, posZ, yawFollow, pitchFollow - "常にプレイヤーの
    方向を向くパーツを追加する。2番席にモブが居る場合、2番席のモブの方向を向く。"
    (a part that always faces the active player's own direction - the pilot's,
    unless seat 2 is occupied, in which case seat 2's own occupant instead).

    Reuses asset.WeaponPart directly rather than a new dedicated record - a
    WeaponPart with neither weapon_name nor aim_range present already just
    tracks seat_index's own aim direction with no weapon-specific clamping/
    ownership at all (see AbstractVehicleEntity's own getWeaponAimYaw/Pitch,
    which already handle a null aim_range as their normal, supported case -
    used for exactly this kind of weapon-less tracking elsewhere already).
    seat_index=1 (0-indexed "seat 2") with pilot_fallback=True reproduces MC
    Heli's own documented behavior exactly, since that's already precisely
    what WeaponPart's own pilot_fallback does for a gunner seat: track seat 2
    if occupied, otherwise fall back to the pilot.

    Part naming is its own dedicated "$camera{N}" family (a
    instruction), NOT sharing build_weapon_parts()'s own "weapon{N}" counter -
    this reflects AddPartCamera being an entirely separate MC Heli directive
    family from AddWeapon/AddPartWeapon, with its own independent numbering.
    """
    parts = []

    def resolve(index: int) -> str:
        candidate = f"$camera{index}"
        if candidate not in available_part_names:
            warnings.append(f"AddPartCamera #{index}: expected a part file for '{candidate}' but none was found "
                             f"for this vehicle - using '{candidate}' anyway, which will almost certainly NOT "
                             f"render/animate. Check this vehicle's actual '<vehicle>_*.obj' part file names and "
                             f"fix weapon_parts[].part by hand if it's actually named something else.")
        return candidate

    camera_index = 0
    for key, tokens in entries.get("__ordered__", []):
        if key != "addpartcamera":
            continue
        index = camera_index
        camera_index += 1
        if len(tokens) < 5:
            continue
        parts.append({
            "part": resolve(index),
            "seat_index": 1,
            "pilot_fallback": True,
            "yaw_follow": tokens[3].strip().lower() == "true",
            "pitch_follow": tokens[4].strip().lower() == "true",
            "pivot_x": to_float(tokens[0]), "pivot_y": to_float(tokens[1]) + y_offset, "pivot_z": to_float(tokens[2]),
        })
    return parts


def merge_obj_files(main_path: Path, part_files: list, output_path: Path, y_offset: float = 0.0):
    """Concatenates MC Heli's separate '<vehicle>_<part>.obj' files into one combined
    OBJ, which is what this project's per-vehicle single-.obj loader needs to see
    them at all. Each part's geometry is wrapped in its own "o <part>" AND
    "g <part>" pair (both are written for the same group - some tools, including
    at least some versions of Metasequoia4's own OBJ importer, only key off "g"
    for separating objects and effectively ignore "o" entirely, showing everything
    as one combined object even though the file is otherwise perfectly correctly
    structured; writing both covers readers that only look at either one). The
    group name (including its "$" prefix, e.g. "$weapon0") is decided by the
    caller (see find_part_files()), and vertex/uv/normal indices in each part's
    "f" lines are re-numbered to account for everything already written before it
    (OBJ indices are global across the whole file, 1-based, and don't reset
    per-object).
    part_files: list of (Path, group_name) tuples, in the order to append them.
    y_offset: added to every vertex's Y coordinate (both the main model and every
    part file) - MC Heli's own ground-based vehicle models are authored such that
    Y=-0.30 in the .obj/config files lines up with Minecraft's own ground level,
    not Y=0, so this project's own renderer (which assumes the model's own Y=0 is
    the entity's own feet/origin) needs every such model shifted by +0.30 to
    compensate. Ships, submarines and seaplanes are positioned relative to the water
    surface instead and don't need this - see convert_one()'s is_water_surface_referenced.
    """
    v_count = vt_count = vn_count = 0

    def copy_renumbered(path: Path, out, group_name: str | None):
        nonlocal v_count, vt_count, vn_count
        local_v = local_vt = local_vn = 0
        if group_name is not None:
            out.write(f"o {group_name}\n")
            out.write(f"g {group_name}\n")

        def is_crawler_track_name(name: str) -> bool:
            lowered = name.lower()
            return lowered.startswith("$crawler_track") or lowered.startswith("$crawlertrack")

        # AddCrawlerTrack's own single link model is
        # excluded from this same +y_offset shift applied to every other
        # part - its own placement comes entirely from the config file's
        # own path Y/Z points (which DO still get y_offset - see
        # build_crawler_tracks()'s own doc), not from the model's own raw
        # vertex Y values, so shifting those TOO would move the link away
        # from where the path (which already accounts for the offset)
        # actually places it.
        #
        # This used to be computed ONCE, from the
        # OUTER group_name parameter only - correct for a "$crawler_track"
        # part living in its own SEPARATE file (part_files' own entries,
        # each with their own group_name), but wrong for one living
        # INSIDE the main file itself as one of potentially several "g"
        # groups (group_name=None the whole time here) - e.g. an
        # .mqo-sourced main model, which bundles every part as its own
        # named Object within ONE file (see convert_mqo_to_obj's own
        # doc), or any hand-authored .obj that already has multiple
        # named groups rather than separate per-part files. Tracked as
        # a MUTABLE, per-line-scanned value instead, updated every time
        # an "o"/"g" line is actually seen (which, for the main file,
        # happens as this loop scans its own internal groups one by
        # one) - correctly excluding "$crawler_track" vertices no matter
        # which of the two ways this vehicle's own files are organized.
        current_group_is_crawler_track = is_crawler_track_name(group_name) if group_name is not None else False
        text = path.read_text(encoding="utf-8", errors="replace")
        for raw_line in text.splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            tag = line.split(None, 1)[0]
            if tag == "v":
                local_v += 1
                effective_y_offset = 0.0 if current_group_is_crawler_track else y_offset
                if effective_y_offset != 0.0:
                    coords = line.split()[1:]
                    if len(coords) >= 3:
                        try:
                            x, y, z = float(coords[0]), float(coords[1]), float(coords[2])
                            rest = coords[3:]  # some exporters add a 4th (w) or color columns
                            out.write("v " + " ".join([f"{x:.6f}", f"{y + effective_y_offset:.6f}", f"{z:.6f}", *rest]) + "\n")
                        except ValueError:
                            out.write(raw_line + "\n")
                    else:
                        out.write(raw_line + "\n")
                else:
                    out.write(raw_line + "\n")
            elif tag == "vt":
                local_vt += 1
                out.write(raw_line + "\n")
            elif tag == "vn":
                local_vn += 1
                out.write(raw_line + "\n")
            elif tag == "f":
                tokens = line.split()[1:]
                new_tokens = []
                for tok in tokens:
                    idx = tok.split("/")
                    vi = str(int(idx[0]) + v_count) if idx[0] else ""
                    vti = str(int(idx[1]) + vt_count) if len(idx) > 1 and idx[1] else ""
                    vni = str(int(idx[2]) + vn_count) if len(idx) > 2 and idx[2] else ""
                    new_tokens.append(f"{vi}/{vti}/{vni}")
                out.write("f " + " ".join(new_tokens) + "\n")
            elif tag in ("o", "g"):
                # Only the main file keeps its own o/g lines (its body may legitimately have named sub-groups already).
                if group_name is None:
                    # An .mqo-sourced main model can
                    # have its own weapon-tracking part as an INTERNAL
                    # named object ("$partN"/"$partN_M" for a child part -
                    # MC Heli's own "Vehicles" category convention - see
                    # normalize_weapon_part_names()'s own doc) rather than
                    # as a separate "<vehicle>_partN" file - that function
                    # only ever renames entries in the part_files LIST, so
                    # an internal "$partN" object slipped through
                    # untouched, still not matching build_weapon_parts()'s
                    # own "$weaponN"/"$weaponN_M" expectation. Applying the
                    # exact same substitution here too (rather than only in
                    # normalize_weapon_part_names()) covers this case as
                    # well, directly on the actual "o"/"g" line as it's
                    # copied into the combined model.
                    renamed_line = re.sub(r"^(o|g)\s+\$part(\d+)(_\d+)?\s*$", r"\1 $weapon\2\3", line)
                    out.write(renamed_line + "\n")
                    new_group_name = renamed_line.split(None, 1)[1].strip() if len(renamed_line.split(None, 1)) > 1 else ""
                    current_group_is_crawler_track = is_crawler_track_name(new_group_name)
            # usemtl/mtllib/s intentionally dropped.

        v_count += local_v
        vt_count += local_vt
        vn_count += local_vn

    with open(output_path, "w", encoding="utf-8") as out:
        copy_renumbered(main_path, out, group_name=None)
        for part_path, group_name in part_files:
            copy_renumbered(part_path, out, group_name=group_name)


_MQO_CONVERSION_CACHE_DIR = Path(tempfile.gettempdir()) / "tudursvehiclemod_mqo_cache"
# An empty placeholder body for a vehicle that has no main body model of its own - see convert_one()'s own doc for exactly when this gets used.
_EMPTY_MODEL_CACHE_DIR = Path(tempfile.gettempdir()) / "tudursvehiclemod_empty_body_cache"


def resolve_or_convert_model(models_dir: Path, stem: str, report: list) -> Path | None:
    """Returns a Path to stem's own .obj file - either the real one directly
    in models_dir, or (if that doesn't exist but a same-named .mqo does) a
    freshly-converted .obj cached in a temp directory (see mqo_to_obj's own
    module doc for what this conversion does and doesn't handle). Returns
    None if neither format exists at all.

    The cache is keyed by the source .mqo's own path and modification time,
    so re-running the converter after only editing OTHER files doesn't
    reconvert every .mqo again - but editing the .mqo itself (a newer mtime)
    does trigger a fresh conversion, same as if the cache didn't exist at
    all.
    """
    obj_path = models_dir / f"{stem}.obj"
    if obj_path.exists():
        return obj_path

    mqo_path = models_dir / f"{stem}.mqo"
    if not mqo_path.exists():
        return None

    _MQO_CONVERSION_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    # Cache key includes both the mqo's own mtime AND this converter script's own mtime, so a stale cached conversion doesn't keep getting reused after the CONVERTER...
    mqo_module_mtime = int(Path(mqo_to_obj.__file__).stat().st_mtime)
    cache_key = f"{stem}_{int(mqo_path.stat().st_mtime)}_{mqo_module_mtime}"
    cached_obj = _MQO_CONVERSION_CACHE_DIR / f"{cache_key}.obj"
    if not cached_obj.exists():
        object_count = mqo_to_obj.convert_mqo_to_obj(mqo_path, cached_obj)
        report.append(f"Converted {mqo_path.name} (Metasequoia) to OBJ: {object_count} object(s) "
                      f"- see tools/mqo_to_obj.py's own doc for format support/limitations")
    return cached_obj


def extract_own_group_names(obj_path: Path) -> set:
    """Returns every "o"/"g" name already present INSIDE obj_path itself -
    for the main model, which (when converted from a multi-Object .mqo, or
    already a multi-group .obj to begin with) can already contain named
    sub-parts like "$rotor0" WITHOUT those being separate "<vehicle>_rotor0"
    part files at all - see find_part_files' own doc for that other case.

    Needed because build_spinning_parts()/build_toggle_parts()/
    build_weapon_parts() all validate their own animation-config entries
    against available_part_names, which previously only ever came from
    find_part_files() (separate part files) - so a sub-part that only ever
    existed as a named Object INSIDE the main model's own single file was
    invisible to that validation even though the geometry itself renders
    correctly grouped, silently dropping any animation/weapon-part config
    that referenced it - exactly that mismatch was observed.
    """
    names = set()
    text = obj_path.read_text(encoding="utf-8", errors="replace")
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("o ") or line.startswith("g "):
            name = line.split(None, 1)[1].strip() if len(line.split(None, 1)) > 1 else ""
            if name:
                names.add(name)
    return names


def find_part_files(models_dir: Path, vehicle_stem: str, report: list) -> list:
    """Finds every '<vehicle_stem>_<suffix>.obj' (or '<vehicle_stem>_<suffix>.mqo',
    transparently converted - see resolve_or_convert_model) next to the main
    model and returns [(path, group_name)], sorted for determinism.
    "<suffix>" (e.g. "weapon0", "rotor1") becomes that part's OBJ group name -
    PREFIXED with "$" (e.g. "$rotor1"), matching MC Heli's own actual naming
    convention for a part's object/group name inside the combined .obj (see
    ObjModel's own class doc) - this project's animation-matching code
    (VehicleEntityRenderer) looks for group names exactly as declared in each
    vehicle's spinning_parts JSON, which build_spinning_parts() below now
    also generates with the same "$" prefix, so the two stay consistent."""
    prefix = f"{vehicle_stem}_"
    stems_found = set()
    for candidate in models_dir.glob(f"{prefix}*.obj"):
        stems_found.add(candidate.stem)
    for candidate in models_dir.glob(f"{prefix}*.mqo"):
        stems_found.add(candidate.stem)  # may already be present from the .obj glob above; a set dedupes that
    parts = []
    for stem in sorted(stems_found):
        suffix = stem[len(prefix):]
        if not suffix:
            continue
        resolved = resolve_or_convert_model(models_dir, stem, report)
        if resolved is not None:
            parts.append((resolved, f"${suffix}"))
    return parts


# Earlier versions of this guessed an unclaimed
# part file's own identity from hint words (gun/turret/barrel/...), which
# risked wrongly grabbing some OTHER, unrelated part (e.g. a piece of the
# body itself, or an accessory) that just happened to not match any other
# Known role either. there's no need to guess
# at all: MC Heli's own "Vehicles" category simply names its own weapon-
# tracking part files "$partN" where every other category names them
# "$weaponN" - a plain, direct, one-to-one substitution, not a fuzzy
# heuristic.
def normalize_weapon_part_names(part_files: list) -> list:
    """MC Heli's own "Vehicles" category
    (a stationary turret/emplacement - see entity.StaticEmplacementEntity's
    own Java doc, and CATEGORY_TO_ENTITY_TYPE's own comment) simply calls
    its own weapon-tracking part files "$partN" (and, for an
    AddPartWeaponChild's own sub-part, "$partN_M") where every other
    category (helicopters/planes/tanks) calls them "$weaponN"/"$weaponN_M"
    - build_weapon_parts()'s own resolve()/child-part naming only ever
    looks for the latter, so a "Vehicles"-category file's own
    "$part0"/"$part1"/"$part0_0"/... never matched anything at all. This
    renames any "$partN" or "$partN_M" part file straight to its own
    "$weaponN"/"$weaponN_M" equivalent - a direct, unambiguous
    substitution (not a guess), applied uniformly regardless of category
    (harmless everywhere else, since no other category's own convention
    ever produces a "$partN"-named file in the first place).
    """
    renamed = []
    for path, name in part_files:
        match = re.fullmatch(r"\$part(\d+)(_\d+)?", name)
        if match:
            renamed.append((path, f"$weapon{match.group(1)}{match.group(2) or ''}"))
        else:
            renamed.append((path, name))
    return renamed


def normalize_vtol_rotor_part_names(part_files: list, entity_type: str) -> list:
    """MC Heli's own source files name a VTOL's
    AddPartRotor nacelle part files the exact same way AddRotor's own
    helicopter blade parts are named - "$rotorN" (see ROTOR_WORDS's own
    doc for that side) - there's no separate naming convention for the two
    at the source. Renamed to "$vtol_rotorN" here instead (matching
    build_vtol_rotor_parts()'s own VTOL_ROTOR_WORDS candidate list) - but
    ONLY when entity_type is actually tudursvehiclemod:vtol, so an ordinary
    helicopter's own "$rotorN" part files (which really do need to keep
    that exact name, for AddRotor's own ROTOR_WORDS resolution to still
    find them) are never touched by this at all.
    """
    if entity_type != "tudursvehiclemod:vtol":
        return part_files
    renamed = []
    for path, name in part_files:
        match = re.fullmatch(r"\$rotor(\d+)", name)
        if match:
            renamed.append((path, f"$vtol_rotor{match.group(1)}"))
        else:
            renamed.append((path, name))
    return renamed


def convert_one(txt_path: Path, category: str, addon_root: Path, out_root: Path,
                 namespace: str, report: list, disable_auto_detect: bool = False,
                 lang_entries: dict[str, dict[str, str]] | None = None) -> bool:
    name = txt_path.stem.lower()
    name = re.sub(r"[^a-z0-9_.-]", "_", name)

    models_src_dir = addon_root / "assets" / "mcheli" / "models" / category
    model_src = resolve_or_convert_model(models_src_dir, txt_path.stem, report)
    # Discovered BEFORE build_spinning_parts() so it can match rotor/rotation
    # entries against whatever part files actually exist on disk, rather
    # than assuming a... (moved earlier than before, so a missing main
    # model can still check for these before deciding whether to skip -
    # see the model_src is None branch below).
    part_files = find_part_files(models_src_dir, txt_path.stem, report)
    if model_src is None:
        # Some vehicles genuinely have no main body
        # mesh of their own at all - just separate, purely functional part
        # files (e.g. a rig that works entirely via its own named parts,
        # with nothing that needs its own dedicated body geometry). Rather
        # than skipping the whole vehicle outright just because
        # "<vehicle_name>.obj"/".mqo" itself doesn't exist, an empty
        # placeholder body is used instead whenever at least one part file
        # DOES exist, so the .txt config still gets converted and those
        # part files still get merged in normally.
        if part_files:
            _EMPTY_MODEL_CACHE_DIR.mkdir(parents=True, exist_ok=True)
            empty_model_path = _EMPTY_MODEL_CACHE_DIR / f"{txt_path.stem}_empty.obj"
            if not empty_model_path.exists():
                empty_model_path.write_text(
                    "# Empty placeholder body - this vehicle has no main body model of its "
                    "own (no matching <name>.obj/.mqo file), only separate part files.\n",
                    encoding="utf-8")
            model_src = empty_model_path
            report.append(f"{category}/{txt_path.name}: no main body model found, but {len(part_files)} "
                          f"part file(s) exist - converting with an empty placeholder body instead of "
                          f"skipping this vehicle entirely")
        else:
            report.append(f"[SKIPPED] {category}/{txt_path.name}: no matching .obj or .mqo model found "
                           f"(neither a main body nor any part files)")
            return False

    texture_src = addon_root / "assets" / "mcheli" / "textures" / category / f"{txt_path.stem}.png"
    if not texture_src.exists():
        report.append(f"[SKIPPED] {category}/{txt_path.name}: no matching .png texture found")
        return False

    entries = parse_mcheli_file(txt_path)
    warnings = []
    entity_type, _phys_type = detect_entity_type(entries, category, disable_auto_detect)
    if not disable_auto_detect and CATEGORY_TO_ENTITY_TYPE[category][0] != entity_type:
        warnings.append(f"folder suggested {CATEGORY_TO_ENTITY_TYPE[category][0]} but this vehicle's own "
                         f"settings (Float/AddRotor/AddCrawlerTrack) indicate {entity_type} - using that instead")

    # Ships already excluded the model Y-offset, but Submarine (and likely seaplanes) had been missed - no offset is needed at the water surface:
    # SUBMARINE was missing from this list. The +0.30 shift exists only because MC Heli authors GROUND vehicles with Y=-0.30
    # as their own ground line; anything positioned against the water surface instead is already correct at Y=0 and must not
    # be shifted. A submarine is exactly that - detect_entity_type() classifies it as water-based (see its own "water" physics
    # tag), so it belongs here alongside ships for the same reason they do.
    #
    # Seaplanes were already covered, but only incidentally: they stay classified as "aircraft" and are caught by the Float=true
    # test below. Spelled out explicitly here so the intent survives - detect_entity_type() sends Float=true vehicles to SHIP
    # only when their own gravity is also strong (ships/subs ~-0.15), so a seaplane's own weak gravity keeps it an aircraft
    # while still being water-referenced.
    is_water_surface_referenced = (entity_type == "tudursvehiclemod:ship"
                                    or entity_type == "tudursvehiclemod:submarine"
                                    or (entity_type == "tudursvehiclemod:aircraft"
                                        and first(entries, "float", ["false"])[0].strip().lower() == "true"))
    ground_y_offset = 0.0 if is_water_surface_referenced else 0.30

    # part_files was already discovered earlier (before the model_src is
    # None check above, so a missing main model can still check for these
    # first) - reused here rather than calling find_part_files() a second
    # time.
    # MC Heli's own "Vehicles" category simply
    # calls its own weapon-tracking part files "$partN" in place of every
    # other category's own "$weaponN" - a direct substitution, not a guess
    # - see that function's own doc. Runs for every category (a harmless
    # no-op elsewhere, since no other category's own convention ever
    # produces a "$partN"-named file to begin with).
    part_files = normalize_weapon_part_names(part_files)
    # MC Heli's own VTOL source files name their
    # AddPartRotor nacelle part files "$rotorN" - the exact same convention
    # AddRotor's own helicopter blade parts use - so this rename is scoped
    # to entity_type == vtol specifically, and is a no-op for every other
    # vehicle type (including an ordinary helicopter, whose own "$rotorN"
    # needs to keep that exact name) - see that function's own doc.
    part_files = normalize_vtol_rotor_part_names(part_files, entity_type)
    available_part_names = {group_name for _, group_name in part_files}
    available_part_names |= extract_own_group_names(model_src)

    display_name = first(entries, "displayname", [txt_path.stem])[0]
    # MC Heli's own per-locale directive, one line per locale - e.g. "AddDisplayName = ja_JP, AH-6 キラーエッグ". Collected here (every occurrence, not just the first, since a vehicle can define several); actually written out to this project's own lang files further down in this function, once the vehicle's final id (namespace + name) is known - see that write-out's own doc for the localization convention this feeds (client.VehicleDisplayNames' own "vehicle.<namespace>.<path>" key).
    add_display_names: list[tuple[str, str]] = []
    for tokens in entries.get("adddisplayname", []):
        if len(tokens) < 2:
            continue
        # MC Heli's own locale token uses its OWN casing convention (e.g. "ja_JP"), which does not match Minecraft's own lang file naming (lowercase, e.g. "ja_jp") - normalized here rather than trusting the addon author to have used Minecraft's own casing.
        locale = tokens[0].strip().lower()
        # The name itself may itself contain a comma (rare, but MC Heli's own comma-separated format has no escaping mechanism for this) - rejoined from every token after the locale rather than assuming exactly two tokens.
        name_value = ",".join(tokens[1:]).strip()
        if locale and name_value:
            add_display_names.append((locale, name_value))
    speed = to_float(first(entries, "speed", ["0.5"])[0], 0.5)
    step_height = to_float(first(entries, "stepheight", ["1.0"])[0], 1.0)
    # Pitch (degrees) to ease towards while grounded.
    on_ground_pitch = to_float(first(entries, "ongroundpitch", ["0"])[0], 0.0)
    # This vehicle's own "HUD = <name>" directive, if it set one.
    hud_directive = first(entries, "hud", [None])[0]
    hud = hud_directive.strip().lower() if hud_directive else DEFAULT_HUD_BY_ENTITY_TYPE.get(entity_type)
    # This vehicle's own "Sound = <name>" directive (engine sound, looped continuously and scaled with throttle.
    engine_sound_directive = first(entries, "sound", [None])[0]
    engine_sound = engine_sound_directive.strip().lower() if engine_sound_directive else None
    # MaxHP/ArmorDamageFactor/ArmorMinDamage/DamageFactor.
    max_health = to_float(first(entries, "maxhp", ["100"])[0], 100.0)
    armor_damage_factor = to_float(first(entries, "armordamagefactor", ["1.0"])[0], 1.0)
    armor_min_damage = to_float(first(entries, "armormindamage", ["0"])[0], 0.0)
    # ArmorMaxDamage - omitted from the output JSON entirely if this vehicle's own config doesn't set it (rather than writing something like float...
    armor_max_damage_raw = first(entries, "armormaxdamage", [None])[0]
    armor_max_damage = to_float(armor_max_damage_raw, 0.0) if armor_max_damage_raw is not None else None
    damage_factor = to_float(first(entries, "damagefactor", ["1.0"])[0], 1.0)
    # InventorySize - see asset.VehicleExtras' own doc.
    inventory_size = max(0, int(to_float(first(entries, "inventorysize", ["0"])[0], 0.0)))
    # MaxFuel/FuelConsumption.
    max_fuel_raw = first(entries, "maxfuel", [None])[0]
    max_fuel = to_float(max_fuel_raw, 0.0) if max_fuel_raw is not None else None
    fuel_consumption_raw = first(entries, "fuelconsumption", [None])[0]
    fuel_consumption = to_float(fuel_consumption_raw, 0.0) if fuel_consumption_raw is not None else None
    # SubmergedDamageHeight - how far below the water surface (blocks) this vehicle can go before taking damage each second - omitted (0, MC Heli's own pre-feature default) unless this vehicle's own config sets it.
    submerged_damage_height_raw = first(entries, "submergeddamageheight", [None])[0]
    if submerged_damage_height_raw is not None:
        submerged_damage_height = to_float(submerged_damage_height_raw, 0.0)
    elif entity_type == "tudursvehiclemod:submarine":
        # A submarine that takes damage immediately upon diving (0, the raw MC Heli default) would be broken - default to effectively unlimited unless explicitly configured otherwise.
        submerged_damage_height = 1000.0
    else:
        submerged_damage_height = None
    width, height = compute_bounding_size(entries)
    if width > 10 or height > 10:
        warnings.append(f"estimated size ({width}x{height}) is unusually large - BoundingBox-derived size is "
                         f"only approximate, review/scale it down manually if this vehicle looks oversized")
    seats = build_seats(entries, ground_y_offset)
    weapons, weapon_name_to_seat = build_weapons(entries, addon_root, len(seats), namespace, ground_y_offset,
                                                  is_ground_weapon_category=(category == "vehicles"))
    weapon_parts, ammo_parts = build_weapon_parts(entries, weapon_name_to_seat, warnings, available_part_names,
                                                   is_ground_weapon_category=(category == "vehicles"),
                                                   y_offset=ground_y_offset)
    weapon_parts = weapon_parts + build_camera_parts(entries, warnings, available_part_names, ground_y_offset)
    spinning_parts = build_spinning_parts(entries, warnings, available_part_names, ground_y_offset)
    toggle_parts = build_toggle_parts(entries, warnings, available_part_names, ground_y_offset)
    wheel_parts, steering_wheel_parts = build_wheel_parts(entries, warnings, available_part_names, ground_y_offset)
    crawler_tracks = build_crawler_tracks(entries, warnings, available_part_names, ground_y_offset)
    track_roller_parts = build_track_roller_parts(entries, warnings, available_part_names, ground_y_offset)
    vtol_rotor_parts, vtol_rotor_blade_parts = build_vtol_rotor_parts(entries, warnings, available_part_names, ground_y_offset)
    search_light_parts = build_search_light_parts(entries, warnings, available_part_names, ground_y_offset)
    # A blade AddBlade entry immediately
    # following an AddPartRotor (see build_vtol_rotor_parts()'s own doc)
    # is folded into the SAME overall spinning_parts list as every other
    # continuously-spinning part (a plain, unlinked AddRotor/AddBlade
    # pairing included) - PartAnimation's own optional vtol_rotor_parent
    # is what actually distinguishes "this one also inherits a nacelle's
    # own tilt" from an ordinary spinning part, not which list it lives in.
    spinning_parts = spinning_parts + vtol_rotor_blade_parts

    # Handling: aircraft's turn_speed (yaw) stays at the slower, already-tuned value (it governs how fast the view-following heading can turn.
    is_aircraft = entity_type == "tudursvehiclemod:aircraft"
    TURN_SPEED_DEFAULT = 0.3 if is_aircraft else 3.0
    SPEED_MULTIPLIER = 3.0 if is_aircraft else 1.0

    # MobilityYaw - per Readme_Aircraft.txt's own doc ("機体の横方向の回転量、
    # 大きいほど機動性が良い" - the vehicle's own horizontal rotation amount,
    # larger = better maneuverability), read directly here as this vehicle's
    # own turn_speed rather than always using the hardcoded default above -
    # this wasn't being read/applied at all. Falls back
    # to MobilityYawOnGround (documented as ground-only, but a reasonable
    # single fallback for a vehicle that only specifies that variant) if
    # MobilityYaw itself is absent, then to the existing hardcoded default
    # if NEITHER is present at all (an older/incomplete source file).
    mobility_yaw_raw = first(entries, "mobilityyaw", [None])[0]
    if mobility_yaw_raw is None:
        mobility_yaw_raw = first(entries, "mobilityyawonground", [None])[0]
    turn_speed = to_float(mobility_yaw_raw, TURN_SPEED_DEFAULT) if mobility_yaw_raw is not None else TURN_SPEED_DEFAULT
    # Reverse steering felt inverted on SOME vehicles but not others: MobilityYaw is documented as
    # "larger = better maneuverability", so a NEGATIVE value is meaningless as a turn rate - but nothing rejected one, and it
    # flowed straight through to turnSpeed, where it silently mirrors every steering input for that vehicle. That matches the
    # reported pattern exactly (per-vehicle, not per-type). Taking the magnitude keeps the intended maneuverability while
    # removing the accidental direction flip; a zero stays zero, which legitimately means "cannot turn".
    if turn_speed < 0:
        turn_speed = abs(turn_speed)

    # ReverseThrottle: a new, project-specific
    # directive (not from MC Heli's own format at all) letting a vehicle's
    # own config file explicitly set its own reverse-throttle fraction
    # (e.g. -0.2 = 20% reverse speed), overriding the vehicle-TYPE-specific
    # Default below when present. Defaults, all 
    # submarine keeps its own pre-existing -0.2 (unchanged from before this
    # feature existed); ship reuses that SAME NUMBER (not shared config,
    # just the same value); aircraft -0.05 (5% - a fixed-wing plane can't
    # really "back up" outside of thrust reversers); car -0.5 (50%).
    #
    # Helicopter used to default to 0.0 (no reverse
    # at all) - unlike a fixed-wing aircraft, a real helicopter CAN
    # genuinely fly backward (tail-first) at a meaningful fraction of its
    # own forward speed, so S doing nothing at all was reported as a bug,
    # not the intended behavior. -0.3 (30%) instead - noticeably more
    # capable than aircraft's own very limited reverse, but still slower
    # than flying forward, matching how backward flight actually behaves.
    REVERSE_THROTTLE_DEFAULTS = {
        "tudursvehiclemod:submarine": -0.2,
        "tudursvehiclemod:ship": -0.2,
        "tudursvehiclemod:aircraft": -0.05,
        "tudursvehiclemod:helicopter": -0.3,
        "tudursvehiclemod:car": -0.5,
    }
    reverse_throttle_raw = first(entries, "reversethrottle", [None])[0]
    reverse_throttle = (to_float(reverse_throttle_raw, 0.0) if reverse_throttle_raw is not None
                        else REVERSE_THROTTLE_DEFAULTS.get(entity_type, 0.0))
    # Some Cars travelled the wrong way in reverse: reverse_throttle is used as the LOWER bound on throttle, so it must be negative for "reverse" to mean anything - a positive value makes the vehicle drive FORWARD when asked to reverse. MC Heli's own ReverseThrottle is a magnitude with no meaningful sign, and addons write it either way, which is why this appeared on only some vehicles. Guarded here at conversion time exactly as turn_speed's own sign is above; VehicleDefinition's own compact constructor guards it again on load, which is what also repairs already-converted files.
    if reverse_throttle > 0:
        reverse_throttle = -reverse_throttle

    # PivotTurnThrottle - CarEntity-specific (see MovementStats's own doc /
    # AbstractVehicleEntity's own PivotTurnThrottle-gated steering doc for
    # Where this is actually used). MC Heli itself
    # apparently gets a meaningful effect out of very small values (e.g.
    # 0.01) that wouldn't translate well to this project's own
    # implementation, so any NONZERO value below 0.1 is clamped UP to 0.1
    # here - 0 itself is left untouched (it has its own separate meaning:
    # "超信地旋回", pivot freely at any speed including a dead stop, not
    # "a very small required speed").
    #
    # Applied to every vehicle type now (not just
    # car), with its own default (used whenever the source file doesn't set
    # its own PivotTurnThrottle at all) per vehicle type below - car and
    # helicopter keep 0.0 ("超信地旋回"/free pivoting, unchanged from
    # before this applied to every vehicle type at all); aircraft (0.05)
    # and ship/submarine (0.3) get their own explicit, different-from-a-
    # single-shared-default values, since the speed scale involved varies a
    # lot between vehicle types - a
    # ship/submarine genuinely needs relatively more of its own max speed
    # before its own rudder turns it effectively than a plane does before
    # its own control surfaces become effective.
    PIVOT_TURN_THROTTLE_DEFAULTS = {
        "tudursvehiclemod:car": 0.0,
        "tudursvehiclemod:helicopter": 0.0,
        "tudursvehiclemod:aircraft": 0.05,
        "tudursvehiclemod:ship": 0.3,
        "tudursvehiclemod:submarine": 0.3,
    }
    pivot_turn_throttle_raw = first(entries, "pivotturnthrottle", [None])[0]
    if pivot_turn_throttle_raw is not None:
        pivot_turn_throttle_value = to_float(pivot_turn_throttle_raw, 0.0)
        pivot_turn_throttle = 0.1 if 0.0 < pivot_turn_throttle_value < 0.1 else pivot_turn_throttle_value
    else:
        pivot_turn_throttle = PIVOT_TURN_THROTTLE_DEFAULTS.get(entity_type, 0.1)

    # PartWheelRot - see MovementStats's own doc / WheelPart's own doc - a single, vehicle-level value (not per-wheel), same "direct value, no conversion" convention as RotorSpeed/TrackRollerRot.
    wheel_rotation_speed = to_float(first(entries, "partwheelrot", ["40"])[0], 40.0)

    # ThrottleUpDown - MC Heli's own directive controlling throttle
    # sensitivity while holding W/S (see MovementStats's own doc /
    # AbstractVehicleEntity's own updateThrottle() doc for exactly how this
    # gets used - treated as a MULTIPLIER on this project's own existing
    # default step, not an absolute value, since MC Heli's own scale for
    # this directive differs substantially). Only actually included in the
    # output at all if the source file sets its own value - omitted
    # entirely otherwise, so 1.0 ("unchanged") applies exactly as it always
    # did before this directive existed.
    throttle_up_down_raw = first(entries, "throttleupdown", [None])[0]
    throttle_up_down = to_float(throttle_up_down_raw, 1.0) if throttle_up_down_raw is not None else None
    # MC Heli's own Car-equivalent vehicles set
    # ThrottleUpDown to a very high value on MC Heli's own scale -
    # CarEntity specifically (no other vehicle type here) divides this by
    # 10 at conversion time to bring it back in line with this project's
    # own scale for the same directive.
    if throttle_up_down is not None and entity_type == "tudursvehiclemod:car":
        throttle_up_down /= 10.0

    # WeightType - see WeightType.java's own doc for the full ported
    # directive text. MC Heli's own values ("Tank"/"Car"/anything else) map
    # case-insensitively onto this project's own tank/car/unknown; "Unknown"
    # (also the default when the source file doesn't set this at all)
    # deliberately behaves like neither, matching the source directive's
    # own documented undefined behavior for anything other than Tank/Car.
    weight_type_raw = first(entries, "weighttype", ["unknown"])[0].strip().lower()
    weight_type = weight_type_raw if weight_type_raw in ("tank", "car") else "unknown"

    # StallSpeed: a new, project-specific directive
    # (not from MC Heli's own format) letting a vehicle's own config file
    # override AircraftEntity's own stall-engage fraction (0.3, the
    # pre-existing hardcoded value, remains the default) - meaningless for
    # non-aircraft vehicle types, which simply never read this field at all.
    stall_speed_raw = first(entries, "stallspeed", [None])[0]
    stall_speed = to_float(stall_speed_raw, 0.3) if stall_speed_raw is not None else 0.3

    # HideEntity/EntityWidth/EntityHeight - per Readme_Aircraft.txt's own doc:
    # HideEntity hides every mounted passenger's own rendering entirely;
    # EntityWidth/EntityHeight scale a mounted passenger's own rendered size
    # (1.0 = unscaled - MC Heli's own doc lists 0.9 as one example vehicle's
    # own choice, not the actual fallback when the directive is omitted
    # entirely, so 1.0 is used here instead when absent).
    hide_entity = to_bool(first(entries, "hideentity", ["false"])[0])
    entity_width = to_float(first(entries, "entitywidth", [None])[0], 1.0) if first(entries, "entitywidth", [None])[0] is not None else 1.0
    entity_height = to_float(first(entries, "entityheight", [None])[0], 1.0) if first(entries, "entityheight", [None])[0] is not None else 1.0

    # Float: originally only meaningful for aircraft
    # (a seaplane/flying boat that can land/take off from water and taxi
    # on it, vs. an ordinary aircraft that instead loses control and sinks
    # on water contact - see AircraftEntity's own doc). This project has
    # since added shared float support to every vehicle type (Car/
    # Helicopter/StaticEmplacement now read and use this same value too,
    # via AbstractVehicleEntity's own shared tudursvehiclemod$applySurfaceFloatSpring()
    # - each settles/drives on the water surface instead of sinking/
    # getting stuck when float-capable), so this is meaningful across the
    # board now rather than merely harmless-when-unread for those types.
    # Converted unconditionally for every vehicle type regardless of
    # category, same as before. MC Heli's own default when this directive
    # is omitted entirely is false (not float-capable).
    float_capable = to_bool(first(entries, "float", ["false"])[0])

    # IsUAV: marks this vehicle as an unmanned,
    # remotely-piloted-only helicopter, flown exclusively through this
    # project's own station block/dedicated stick system rather than an
    # actual pilot seat - gates Readme_Weapon.txt's own Destruct directive
    # (only actually has any effect for a UAV helicopter, per that
    # directive's own doc). Not an MC Heli directive this project's own
    # converter previously recognized at all - added here specifically so
    # a UAV-flagged vehicle can still be ported through this same pipeline,
    # Vehicle PORTING itself stay supported even
    # though the actual station/stick equipment is this project's own,
    # unrelated design (see that request's own doc elsewhere). false (the
    # default, key absent) is an ordinary, crewed vehicle.
    is_uav = to_bool(first(entries, "isuav", ["false"])[0])

    # VtolHoverSpeedFraction: how fast VTOL mode's
    # own WASD-driven horizontal movement is, as a fraction of this
    # vehicle's own maxSpeed (the same top speed aircraft mode itself
    # flies at) - a project-specific directive (not an MC Heli one at
    # all, same reasoning as IsUAV just above) so each vehicle's own VTOL-
    # mode feel can be tuned individually when porting it through this
    # same pipeline. 0.1 (10% of maxSpeed) is this project's own default
    # whenever this key is entirely absent, matching a direct request's
    # own explicit default.
    vtol_hover_speed_fraction = to_float(first(entries, "vtolhoverspeedfraction", ["0.1"])[0], 0.1)

    # Auto-sorts converted output into a subfolder
    # named after the vehicle's own entity_type (helicopter/car/ship/
    # submarine/aircraft), rather than dumping every vehicle's own model/
    # texture/definition file into one flat folder regardless of type -
    # this makes a large addon pack much easier to browse/edit by hand
    # afterward. entity_type is always "<namespace>:<type>" (see
    # CATEGORY_TO_ENTITY_TYPE/detect_entity_type above), so the part after
    # the colon IS the type folder name directly.
    type_folder = entity_type.split(":", 1)[1]

    definition = {
        "entity_type": entity_type,
        "model": f"{namespace}:models/obj/{type_folder}/{name}.obj",
        "texture": f"{namespace}:textures/vehicle/{type_folder}/{name}.png",
        "scale": 1.0,
        "width": width,
        "height": height,
        "max_speed": round(min(max(speed, 0.05), 2.0) * SPEED_MULTIPLIER, 3),
        "acceleration": 0.05,
        "turn_speed": turn_speed,
        "reverse_throttle": reverse_throttle,
        "pivot_turn_throttle": pivot_turn_throttle,
        "wheel_rotation_speed": wheel_rotation_speed,
        "stall_speed": stall_speed,
        "weight_type": weight_type,
        # HideEntity/EntityWidth/EntityHeight live inside passenger_display - the mod's codec
        # only reads them there (older output wrote them at the top level, where they were
        # ignored; the mod now also migrates that old form on load for existing packs).
        "passenger_display": {
            "hide_entity": hide_entity,
            "entity_width": entity_width,
            "entity_height": entity_height,
        },
        "float_capable": float_capable,
        "is_uav": is_uav,
        "vtol_hover_speed_fraction": vtol_hover_speed_fraction,
        "step_height": step_height,
        "on_ground_pitch": on_ground_pitch,
        "seats": seats,
        "max_health": max_health,
        "armor_damage_factor": armor_damage_factor,
        "armor_min_damage": armor_min_damage,
        "damage_factor": damage_factor,
    }
    if armor_max_damage is not None:
        definition["armor_max_damage"] = armor_max_damage
    if throttle_up_down is not None:
        definition["throttle_up_down"] = throttle_up_down
    if inventory_size > 0:
        definition["inventory_size"] = inventory_size
    if max_fuel is not None:
        definition["max_fuel"] = max_fuel
    if fuel_consumption is not None:
        definition["fuel_consumption"] = fuel_consumption
    if submerged_damage_height is not None:
        definition["submerged_damage_height"] = submerged_damage_height
    if weapons:
        definition["weapons"] = weapons
    if spinning_parts:
        definition["spinning_parts"] = spinning_parts
    if toggle_parts:
        definition["toggle_parts"] = toggle_parts
    if weapon_parts:
        definition["weapon_parts"] = weapon_parts
    if ammo_parts:
        definition["ammo_parts"] = ammo_parts
    if wheel_parts:
        definition["wheel_parts"] = wheel_parts
    if steering_wheel_parts:
        definition["steering_wheel_parts"] = steering_wheel_parts
    if crawler_tracks:
        definition["crawler_tracks"] = crawler_tracks
    if track_roller_parts:
        definition["track_roller_parts"] = track_roller_parts
    if vtol_rotor_parts:
        definition["vtol_rotor_parts"] = vtol_rotor_parts
    if search_light_parts:
        definition["search_light_parts"] = search_light_parts
    if hud:
        definition["hud"] = hud
    if engine_sound:
        definition["engine_sound"] = engine_sound

    # Supports MC Heli's own supply-range settings: FuelSupplyRange/AmmoSupplyRange convert directly, since this project's own equivalents share their exact semantics (a radius, in blocks, within which OTHER vehicles are topped up at no cost to the supplier - see VehicleDefinition's own fuelSupplyRange doc). Only emitted when the source file actually sets them, so an ordinary vehicle's own converted json is completely unchanged by this.
    #
    # health_supply_range has NO MC Heli counterpart - it is this project's own addition, requested alongside these two - so nothing here ever emits it; a pack author adds it by hand if they want it.
    fuel_supply_range = to_float(first(entries, "fuelsupplyrange", ["0"])[0], 0.0)
    if fuel_supply_range > 0:
        definition["fuel_supply_range"] = fuel_supply_range
    ammo_supply_range = to_float(first(entries, "ammosupplyrange", ["0"])[0], 0.0)
    if ammo_supply_range > 0:
        definition["ammo_supply_range"] = ammo_supply_range

    # Supports MC Heli's own EnableEjectionSeat - only emitted when the source file actually sets it, same convention as the supply ranges above.
    if first(entries, "enableejectionseat", ["false"])[0].strip().lower() == "true":
        definition["enable_ejection_seat"] = True

    # Supports MC Heli's own MobDropOption - see asset.MobDropOption's own doc; interval_ticks needs no unit conversion at all (1/20 second is already exactly one tick).
    mob_drop_tokens = first(entries, "mobdropoption", None)
    if mob_drop_tokens is not None and len(mob_drop_tokens) >= 4:
        definition["mob_drop_option"] = {
            "rel_x": to_float(mob_drop_tokens[0]),
            "rel_y": to_float(mob_drop_tokens[1]),
            "rel_z": to_float(mob_drop_tokens[2]),
            "interval_ticks": int(to_float(mob_drop_tokens[3])),
        }

    # Every converted vehicle defaults to tier 1 (immediately available from the very first tiered spawner item.
    definition["spawn_item"] = {"display_name": display_name, "tier": 1}

    # --- write outputs ---
    models_dir = out_root / "assets" / namespace / "models" / "obj" / type_folder
    textures_dir = out_root / "assets" / namespace / "textures" / "vehicle" / type_folder
    vehicles_dir = out_root / "data" / namespace / "vehicles" / type_folder
    for d in (models_dir, textures_dir, vehicles_dir):
        d.mkdir(parents=True, exist_ok=True)

    merge_obj_files(model_src, part_files, models_dir / f"{name}.obj", y_offset=ground_y_offset)
    if entity_type == "tudursvehiclemod:vtol":
        # Normalize_vtol_rotor_part_names() above only
        # ever renamed a SEPARATE "<vehicle>_rotorN.obj" part file's own
        # group name - it never touched a "$rotorN" group defined directly
        # INSIDE the main model file itself (merge_obj_files() copies the
        # main model's own internal "o"/"g" declarations through
        # unchanged, group_name=None - see that function's own doc), which
        # is apparently where at least some VTOL source models actually
        # define this. Renamed here instead as a direct text substitution
        # on the already-merged output file, catching either case -
        # scoped to entity_type == vtol only, so an ordinary helicopter's
        # own "$rotorN" (wherever it's defined) is never touched by this.
        merged_path = models_dir / f"{name}.obj"
        merged_text = merged_path.read_text(encoding="utf-8")
        renamed_text = re.sub(r"^([og]) \$rotor(\d+)\b", r"\1 $vtol_rotor\2", merged_text, flags=re.MULTILINE)
        if renamed_text != merged_text:
            merged_path.write_text(renamed_text, encoding="utf-8")
    if part_files:
        merged_names = ", ".join(suffix for _, suffix in part_files)
        warnings.append(f"merged {len(part_files)} separate part file(s) into the main .obj as their own "
                         f"groups: {merged_names} (only groups matching spinning_parts above actually "
                         f"animate - everything else, including weapon/hatch/canopy/lg parts, is merged in "
                         f"but renders as static geometry, part of the main body)")
    shutil.copy(texture_src, textures_dir / f"{name}.png")
    (vehicles_dir / f"{name}.json").write_text(
        json.dumps(definition, indent=2, ensure_ascii=False), encoding="utf-8")

    # Per the same direct request as add_display_names' own doc above: feeds this vehicle's own per-locale names into the shared structure written out once, for every vehicle, at the end of run_conversion() - see write_lang_files()'s own doc for the actual file format.
    if lang_entries is not None:
        for locale, name_value in add_display_names:
            # The pack-side lang file had no effect ("パック側のlangファイルが機能しないようです。英語表記のままになっています"): the actual registered vehicleId's own PATH includes type_folder as a leading path segment (see entity.VehicleDefinitionReloadListener's own "vehiclesDir.relativize(jsonFile)" - the relative path from vehicles/ to the json file, which is "<type_folder>/<name>", not just "<name>", since the json itself is written to vehicles/<type_folder>/<name>.json). The key generated here previously omitted type_folder entirely and therefore never matched what client.VehicleDisplayNames actually builds and looks up - it silently fell through to the untranslated fallback every time, which is exactly the symptom reported.
            lang_entries.setdefault(locale, {})[f"vehicle.{namespace}.{type_folder}/{name}"] = name_value

    status = "OK" if not warnings else "OK (with warnings)"
    report.append(f"[{status}] {category}/{txt_path.name} -> {namespace}:{type_folder}/{name}"
                  + ("".join(f"\n           - {w}" for w in warnings)))
    return True


def write_lang_files(out_root: Path, namespace: str, lang_entries: dict[str, dict[str, str]], report: list):
    """writes every locale's own accumulated vehicle-name entries (see convert_one()'s own add_display_names/lang_entries doc) to assets/<namespace>/lang/<locale>.json - the exact convention client.VehicleDisplayNames already reads from (its own "vehicle.<namespace>.<path>" key), so a converted addon's names are localized automatically with no further wiring needed on the mod side at all.

    MERGES rather than overwrites: an existing lang file at that path (from a previous conversion run over the same addon folder, or a translator's own manual additions/corrections) keeps every key this run doesn't itself touch - only the specific vehicle.<namespace>.<path> keys THIS run produced are added or updated, exactly mirroring how the mod's own asset files are re-generated per vehicle without disturbing anything else in the output tree.
    """
    if not lang_entries:
        return
    lang_dir = out_root / "assets" / namespace / "lang"
    lang_dir.mkdir(parents=True, exist_ok=True)
    for locale, new_keys in lang_entries.items():
        lang_path = lang_dir / f"{locale}.json"
        existing: dict[str, str] = {}
        if lang_path.exists():
            try:
                existing = json.loads(lang_path.read_text(encoding="utf-8"))
            except (json.JSONDecodeError, OSError):
                # An unreadable existing file is left alone rather than silently discarded - the new keys are still written into a merged copy, but a malformed file is reported so it can be looked at by hand instead of quietly losing whatever it held.
                report.append(f"WARNING: {lang_path} could not be parsed as JSON - its own existing "
                               f"content may not have been preserved correctly. Check it by hand.")
        existing.update(new_keys)
        lang_path.write_text(
            json.dumps(existing, indent=2, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
    total = sum(len(v) for v in lang_entries.values())
    report.append(f"AddDisplayName: wrote {total} localized name(s) across {len(lang_entries)} "
                  f"locale(s) into assets/{namespace}/lang/ (read automatically by "
                  f"client.VehicleDisplayNames - see that class's own doc)")


def write_pack_mcmeta(out_root: Path):
    """A pack.mcmeta isn't needed at all for the loose tudursvehiclemod-addons/ folder
    scan this project does itself - it's included only so the SAME output folder
    can also be dropped into a world's datapacks folder as a real datapack if
    you'd rather install it that way (functionally equivalent for vehicle
    definitions; only textures/models specifically require the loose-folder scan
    or a real resource pack, see the README)."""
    meta = {"pack": {"pack_format": 48, "description": "MC Helicopter addon, converted"}}
    out_root.mkdir(parents=True, exist_ok=True)
    (out_root / "pack.mcmeta").write_text(json.dumps(meta, indent=2), encoding="utf-8")


EXTENSION_STATUS_MARKER = ";=== tudursvehiclemod extension status (auto-appended by mcheli_convert.py) ==="

# Appended to common_pilot.txt (if present) after copying HUD scripts.
EXTENSION_STATUS_BLOCK = f"""
{EXTENSION_STATUS_MARKER}
If = manual_mode==1
\tColor = 0xFFFF5555
\tDrawCenteredString = 0, -80, "MANUAL MODE"
EndIf
If = stalling==1
\tColor = 0xFFFF5555
\tDrawCenteredString = 0, -25, "STALL"
EndIf
If = gear_deployed==1
\tColor = 0xFF28d448
\tDrawCenteredString = 0, 40, "GEAR DOWN"
EndIf
Color = 0xFFFFFFFF
DrawCenteredString = 0, 60, "%.1f kb/h", speed_kbh
"""


def append_extension_status_hud(hud_dst: Path, report: list):
    """Prepends EXTENSION_STATUS_BLOCK to the very START of EVERY copied HUD
    script - per direct feedback that appending at the end still didn't show
    anything, even after an earlier fix that tried to insert just before a
    detected trailing "Exit" line (an Exit directive stops the script's
    execution at that point, so content placed after one never runs at all).

    Prepending at the very start instead is simpler and more robust than
    trying to detect the "right" Exit to insert before: a HUD script can have
    an Exit INSIDE an If block (a conditional early-exit), and blindly using
    the LAST "Exit" line found anywhere in the file risked picking one of
    those instead of a genuine trailing one - which would have nested this
    project's own content inside that unrelated If block, subtly breaking
    both. Since HUD scripts execute top-to-bottom, content at the very
    beginning is guaranteed to run before any Exit anywhere later in the
    file, unconditionally, without needing to identify anything about the
    rest of the file's own structure at all.

    Idempotent per file: checks for EXTENSION_STATUS_MARKER first, so
    re-running the converter against the same output folder doesn't keep
    piling up duplicate copies within any single file. Since copy_hud_assets()
    re-copies the pack's own original file over this output on every run
    before this function sees it, that original never actually has the
    marker to begin with - this check mainly guards against re-running this
    function twice within the same conversion, not across separate runs.
    """
    txt_paths = sorted(hud_dst.glob("*.txt"))
    if not txt_paths:
        report.append(f"HUD extension status: no HUD scripts found at {hud_dst} - skipped")
        return

    appended = []
    already_present = []
    for txt_path in txt_paths:
        existing = txt_path.read_text(encoding="utf-8-sig", errors="replace")
        if EXTENSION_STATUS_MARKER in existing:
            already_present.append(txt_path.name)
            continue
        txt_path.write_text(EXTENSION_STATUS_BLOCK + existing, encoding="utf-8")
        appended.append(txt_path.name)

    if appended:
        report.append(f"HUD extension status: prepended manual_mode/stalling/gear_deployed/speed_kbh "
                      f"display to: {', '.join(appended)}")
    if already_present:
        report.append(f"HUD extension status: already present in: {', '.join(already_present)} - left unchanged")


def copy_hud_assets(addon_root: Path, out_root: Path, report: list):
    """Copies the addon's own HUD scripts (assets/mcheli/hud/*.txt) and their
    referenced textures (assets/mcheli/textures/gui/*.png) into the output
    pack, unchanged and still under the "mcheli" namespace - this project's own
    HUD system (see client.hud.HudScriptLoader) discovers scripts/textures
    across EVERY loaded namespace, not just this converter's own output
    namespace, so there's no need to move or rename anything here for it to
    be found. Every vehicle's own generated JSON already points at a HUD
    script BY NAME (see convert_one()'s own "hud" field) - copying the actual
    script/texture files here is what makes that name resolve to something
    real once the output pack is loaded.
    """
    hud_src = addon_root / "assets" / "mcheli" / "hud"
    gui_textures_src = addon_root / "assets" / "mcheli" / "textures" / "gui"

    copied_scripts = 0
    if hud_src.exists():
        hud_dst = out_root / "assets" / "mcheli" / "hud"
        hud_dst.mkdir(parents=True, exist_ok=True)
        for txt_path in sorted(hud_src.glob("*.txt")):
            shutil.copy(txt_path, hud_dst / txt_path.name)
            copied_scripts += 1
        append_extension_status_hud(hud_dst, report)

    copied_textures = 0
    if gui_textures_src.exists():
        gui_textures_dst = out_root / "assets" / "mcheli" / "textures" / "gui"
        gui_textures_dst.mkdir(parents=True, exist_ok=True)
        for png_path in sorted(gui_textures_src.glob("*.png")):
            shutil.copy(png_path, gui_textures_dst / png_path.name)
            copied_textures += 1

    if copied_scripts or copied_textures:
        report.append(f"HUD assets: copied {copied_scripts} script(s) from {hud_src} and "
                      f"{copied_textures} texture(s) from {gui_textures_src}, under the "
                      f"\"mcheli\" namespace (unchanged)")
    else:
        report.append(f"HUD assets: none found at {hud_src} / {gui_textures_src} - vehicles "
                      f"referencing a HUD script will fall back to this mod's built-in HUD "
                      f"unless you place the script/texture files there yourself and re-run")


def copy_sound_assets(addon_root: Path, out_root: Path, report: list):
    """Copies the addon's own sound files (assets/mcheli/sound/*.ogg AND
    assets/mcheli/sounds/*.ogg - MC Heli's own addon packs have used both
    folder names across different versions/examples) into the output pack,
    unchanged and still under the "mcheli" namespace - this project's own
    sound system (see client.sound.AddonSoundLoader) discovers sounds across
    EVERY loaded namespace, not just this converter's own output namespace,
    so there's no need to move or rename anything here for it to be found.
    Every weapon/vehicle's own generated JSON already points at a sound BY
    NAME (see build_weapons()'s own "sound" field and convert_one()'s own
    "engine_sound" field) - copying the actual .ogg files here is what makes
    that name resolve to something real once the output pack is loaded.
    """
    copied_total = 0
    sources_checked = []
    for folder_name in ("sound", "sounds"):
        src = addon_root / "assets" / "mcheli" / folder_name
        sources_checked.append(str(src))
        if not src.exists():
            continue
        dst = out_root / "assets" / "mcheli" / folder_name
        dst.mkdir(parents=True, exist_ok=True)
        for ogg_path in sorted(src.glob("*.ogg")):
            shutil.copy(ogg_path, dst / ogg_path.name)
            copied_total += 1

    if copied_total:
        report.append(f"Sound assets: copied {copied_total} sound(s), under the \"mcheli\" "
                      f"namespace (unchanged)")
    else:
        report.append(f"Sound assets: none found at {' / '.join(sources_checked)} - vehicles/"
                      f"weapons referencing a sound will be silent unless you place the .ogg "
                      f"files there yourself and re-run")


def copy_bullet_assets(addon_root: Path, out_root: Path, report: list):
    """Copies the addon's own bullet models (assets/mcheli/models/bullets/*.obj)
    and textures (assets/mcheli/textures/bullets/*.png) into this project's
    OWN model/texture folder structure (assets/mcheli/models/obj/ and
    assets/mcheli/textures/vehicle/ respectively), renamed with a "bullet_"
    prefix.

    Uses the "mcheli" namespace specifically - NOT the vehicle output
    namespace (--namespace) - because that's the SAME namespace
    copy_weapon_assets() leaves the weapon .txt files under, and
    asset.WeaponStatsLoader (which reads those .txt files live at runtime,
    see WeaponDefinition's own doc) builds each bullet_model/bullet_texture
    Identifier using THAT file's own namespace, not the vehicle's. Using the
    vehicle output namespace here instead - an earlier version did - meant
    the Identifier WeaponStatsLoader actually looked up
    ("mcheli:models/obj/bullet_X.obj") never matched where these files
    actually ended up ("<output_namespace>:models/obj/bullet_X.obj"),
    silently failing to find the model/texture and falling back to plain
    thrown-item rendering instead - exactly that
    ("the bullet model doesn't show").

    This project's own ObjModelLoader/AddonTextureLoader only scan those two
    fixed paths (models/obj, textures/vehicle) - not a separate
    models/bullets or textures/bullets path - so relocating here (rather than
    teaching those loaders a second path to scan) is what makes a weapon's own
    bullet_model/bullet_texture resolve to something real once the output
    pack is loaded. The "bullet_" prefix avoids colliding with an actual
    vehicle model of the same base name (e.g. a vehicle AND its own bullet
    both happening to be named "b150mm").
    """
    models_src = addon_root / "assets" / "mcheli" / "models" / "bullets"
    textures_src = addon_root / "assets" / "mcheli" / "textures" / "bullets"

    copied_models = 0
    if models_src.exists():
        models_dst = out_root / "assets" / "mcheli" / "models" / "obj"
        models_dst.mkdir(parents=True, exist_ok=True)
        for obj_path in sorted(models_src.glob("*.obj")):
            shutil.copy(obj_path, models_dst / f"bullet_{obj_path.name}".lower())
            copied_models += 1

    copied_textures = 0
    if textures_src.exists():
        textures_dst = out_root / "assets" / "mcheli" / "textures" / "vehicle"
        textures_dst.mkdir(parents=True, exist_ok=True)
        for png_path in sorted(textures_src.glob("*.png")):
            shutil.copy(png_path, textures_dst / f"bullet_{png_path.name}".lower())
            copied_textures += 1

    if copied_models or copied_textures:
        report.append(f"Bullet assets: copied {copied_models} model(s) from {models_src} and "
                      f"{copied_textures} texture(s) from {textures_src}, renamed with a "
                      f"\"bullet_\" prefix under the \"mcheli\" namespace (matching where "
                      f"the weapon .txt files themselves stay)")
    else:
        report.append(f"Bullet assets: none found at {models_src} / {textures_src} - weapons "
                      f"referencing a ModelBullet will fall back to the plain thrown-item "
                      f"rendering unless you place the model/texture files there yourself and "
                      f"re-run")


def _inject_round_from_max_ammo(text: str) -> str:
    """If this weapon file's own text sets MaxAmmo but not Round at all,
    appends "Round = <MaxAmmo value>" to it - MC Heli's MaxAmmo directive is
    a DIFFERENT ammo system (a total pool manually resupplied via Item
    directives, rather than a magazine that auto-reloads - see
    Readme_Weapon.txt's own documented example), which this project doesn't
    model as its own separate system. Earlier, WeaponStatsLoader special-cased
    "no Round but MaxAmmo present" on the JAVA side instead, treating MaxAmmo
    as the magazine size there - but ammo displayed
    correctly at first but never actually decremented on firing. Injecting
    a real, ordinary "Round = <n>" line here instead means the Java side
    never needs to know MaxAmmo was ever involved at all - the weapon looks
    exactly like any other Round-based one, which is already confirmed
    working correctly end-to-end.

    Also injects the weapon's own Delay value (or 0 if Delay itself isn't
    set either, matching WeaponStatsLoader's own default) as ReloadTime,
    UNLESS the file already sets one itself - a plain Round injection with no
    ReloadTime at all would otherwise get this project's own "ReloadTime
    omitted = instant reload" default, letting a MaxAmmo weapon instantly
    refill to full the moment it empties, which doesn't match MaxAmmo's own
    intended "manual resupply only" design (see Readme_Weapon.txt's own
    documented example - resupplying costs specific items, not nothing at
    all). An arbitrary huge placeholder number here
    (the original approach) is impractical to actually use/tune, so Delay's
    own value is used instead."""
    has_round = False
    has_reload_time = False
    max_ammo_value = None
    delay_value = "0"
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if line.startswith(";") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip().lower()
        if key == "round":
            has_round = True
        elif key == "reloadtime":
            has_reload_time = True
        elif key == "maxammo":
            # Defensively strips a same-line trailing comment (";...") rather than assuming every weapon file puts comments on their own separate line.
            raw_value = value.partition(";")[0]
            max_ammo_value = raw_value.strip()
        elif key == "delay":
            raw_value = value.partition(";")[0]
            delay_value = raw_value.strip()
    if has_round or max_ammo_value is None:
        return text
    separator = "" if text.endswith("\n") else "\n"
    addition = f"Round = {max_ammo_value}\n"
    if not has_reload_time:
        addition += f"ReloadTime = {delay_value}\n"
    return f"{text}{separator}{addition}"


def copy_weapon_assets(addon_root: Path, out_root: Path, report: list):
    """Copies the addon's own weapon config files (assets/mcheli/weapons/*.txt)
    into the output pack, unchanged and still under the "mcheli" namespace.

    This step is now ESSENTIAL, not just for-reference: as of a later revision,
    this project's own asset.WeaponStatsLoader reads these files LIVE, every
    time a weapon fires, rather than build_weapons() baking their
    damage/velocity/cooldown/sound/gravity/bullet_model values into each
    vehicle's own generated weapons[] JSON at conversion time - a
    request that editing the original file directly for balance tweaks is more
    convenient than re-running this converter every time. Skipping this step
    means every weapon in the output pack falls back to generic default stats
    (see asset.WeaponStats.FALLBACK) until these files are actually present.
    """
    src = addon_root / "assets" / "mcheli" / "weapons"
    if not src.exists():
        report.append(f"Weapon config files: none found at {src}")
        return

    dst = out_root / "assets" / "mcheli" / "weapons"
    dst.mkdir(parents=True, exist_ok=True)
    copied = 0
    max_ammo_injected = 0
    for txt_path in sorted(src.glob("*.txt")):
        # Same encoding fallback as elsewhere in this converter (e.g. mqo_to_obj.py).
        try:
            raw_text = txt_path.read_text(encoding="utf-8-sig")
        except UnicodeDecodeError:
            raw_text = txt_path.read_bytes().decode("cp932", errors="replace")
        patched_text = _inject_round_from_max_ammo(raw_text)
        if patched_text != raw_text:
            max_ammo_injected += 1
        (dst / txt_path.name).write_text(patched_text, encoding="utf-8")
        copied += 1
    report.append(f"Weapon config files: copied {copied} file(s) from {src} - read LIVE at "
                  f"runtime (see asset.WeaponStatsLoader), so editing them directly after "
                  f"conversion (e.g. for balance tweaks) takes effect on the next /reload, "
                  f"no re-conversion needed")
    if max_ammo_injected:
        report.append(f"Weapon config files: {max_ammo_injected} file(s) had MaxAmmo but no Round - "
                       f"added a matching \"Round = <MaxAmmo>\" line so this project's own ammo "
                       f"tracking treats them as an ordinary magazine-based weapon (MaxAmmo's own "
                       f"manual-resupply-via-items behavior isn't modeled separately - see "
                       f"_inject_round_from_max_ammo's own doc)")


def run_conversion(addon_root: Path, out_root: Path, namespace: str = "mcheliport",
                    disable_auto_detect: bool = False, progress_callback=None) -> tuple:
    """Runs the full conversion and returns (converted_count, skipped_count, report_lines).
    progress_callback, if given, is called as progress_callback(message: str) for each
    major step and each vehicle file processed - used by the GUI (see mcheli_convert_gui.py)
    to show live progress; the CLI's own main() just prints instead.
    """
    def notify(message: str):
        if progress_callback is not None:
            progress_callback(message)

    if not (addon_root / "assets" / "mcheli").exists():
        raise ValueError(f"{addon_root} does not contain assets/mcheli - is this the right folder?")

    write_pack_mcmeta(out_root)

    report = [f"MC Helicopter addon conversion report", f"source: {addon_root}", f"namespace: {namespace}", ""]
    converted = 0
    skipped = 0
    # Per convert_one()'s own add_display_names/lang_entries doc: shared across every vehicle converted in this run, keyed by locale then by the vehicle's own "vehicle.<namespace>.<path>" key - written out once, after the loop below, rather than per vehicle.
    lang_entries: dict[str, dict[str, str]] = {}

    notify("HUDファイルをコピーしています...")
    copy_hud_assets(addon_root, out_root, report)
    notify("サウンドファイルをコピーしています...")
    copy_sound_assets(addon_root, out_root, report)
    notify("弾丸モデルをコピーしています...")
    copy_bullet_assets(addon_root, out_root, report)
    notify("武器設定ファイルをコピーしています...")
    copy_weapon_assets(addon_root, out_root, report)

    for category in CATEGORY_TO_ENTITY_TYPE:
        folder = addon_root / "assets" / "mcheli" / category
        if not folder.exists():
            continue
        for txt_path in sorted(folder.glob("*.txt")):
            notify(f"変換中: {category}/{txt_path.name}")
            ok = convert_one(txt_path, category, addon_root, out_root, namespace, report, disable_auto_detect, lang_entries)
            if ok:
                converted += 1
            else:
                skipped += 1

    write_lang_files(out_root, namespace, lang_entries, report)

    report.insert(3, f"converted: {converted}   skipped: {skipped}")
    report.insert(4, "")
    (out_root / "convert_report.txt").write_text("\n".join(report), encoding="utf-8")

    notify(f"完了: 変換 {converted}件、スキップ {skipped}件")
    return converted, skipped, report


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)

    addon_root = Path(sys.argv[1]).resolve()
    out_root = Path(sys.argv[2]).resolve()
    namespace = "mcheliport"
    if "--namespace" in sys.argv:
        namespace = sys.argv[sys.argv.index("--namespace") + 1]
    disable_auto_detect = "--no-auto-detect" in sys.argv

    try:
        converted, skipped, _ = run_conversion(addon_root, out_root, namespace, disable_auto_detect)
    except ValueError as e:
        print(f"error: {e}")
        sys.exit(1)

    print(f"Converted {converted} vehicle(s), skipped {skipped}. See convert_report.txt for details.")


if __name__ == "__main__":
    main()
