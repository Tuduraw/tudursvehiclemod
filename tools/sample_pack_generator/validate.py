import os, re, json, glob, sys

root = sys.argv[1]; docs = sys.argv[2]
A = os.path.join(root, "assets", "tudur_sample")
errors = []

def obj_groups(path):
    return {l.split()[1] for l in open(path) if l.startswith(("o ", "g "))}

weapons = {os.path.basename(p)[:-4]: open(p, encoding="utf-8").read() for p in glob.glob(f"{A}/weapons/*.txt")}
sounds = {os.path.basename(p)[:-4].lower() for p in glob.glob(f"{A}/sound/*.ogg")}
models = {os.path.basename(p)[:-4] for p in glob.glob(f"{A}/models/obj/*.obj")}
huds = {os.path.basename(p)[:-4] for p in glob.glob(f"{A}/hud/*.txt")}
vehicle_files = {os.path.basename(p)[:-5] for p in glob.glob(f"{root}/data/tudur_sample/vehicles/*.json")}

used_vehicle_keys = set()
def walk(o):
    if isinstance(o, dict):
        for k, v in o.items(): used_vehicle_keys.add(k); walk(v)
    elif isinstance(o, list):
        for v in o: walk(v)

for vf in sorted(vehicle_files):
    v = json.load(open(f"{root}/data/tudur_sample/vehicles/{vf}.json", encoding="utf-8")); walk(v)
    mname = v["model"].split("/")[-1][:-4]
    groups = obj_groups(f"{A}/models/obj/{mname}.obj")
    refs = set(re.findall(r'"part": "(\$[a-z0-9_]+)"', json.dumps(v)))
    refs |= set(re.findall(r'"linked_part": "(\$[a-z0-9_]+)"', json.dumps(v)))
    refs |= set(re.findall(r'"vtol_rotor_parent": "(\$[a-z0-9_]+)"', json.dumps(v)))
    for r in refs:
        if r not in groups: errors.append(f"{vf}: part {r} not in {mname}.obj (has {sorted(groups)})")
    for w in re.findall(r'"weapon_name": "([a-z0-9_]+)"', json.dumps(v)):
        if w not in weapons: errors.append(f"{vf}: weapon {w} missing")
    if v.get("engine_sound") and v["engine_sound"].lower() not in sounds: errors.append(f"{vf}: engine_sound {v['engine_sound']} missing")
    if v.get("hud") and v["hud"] not in huds: errors.append(f"{vf}: hud {v['hud']} missing")

used_weapon_keys = set()
for wn, txt in weapons.items():
    for line in txt.splitlines():
        if "=" in line and not line.strip().startswith(";"):
            k, val = [s.strip() for s in line.split("=", 1)]; used_weapon_keys.add(k.lower())
            if k.lower() in ("modelbullet", "modelbomblet") and f"bullet_{val.lower()}" not in models: errors.append(f"{wn}: model bullet_{val} missing")
            if k.lower() == "setcartridge" and f"bullet_{val.split(',')[0].strip()}" not in models: errors.append(f"{wn}: cartridge model missing")
            if k.lower() == "sound" and val.lower() not in sounds: errors.append(f"{wn}: sound {val} missing")
            if k.lower() in ("casaircraft", "carrieraircraft") and val not in vehicle_files: errors.append(f"{wn}: aircraft {val} missing")

# HUD Call targets
for h in huds:
    for m in re.findall(r"Call\s*=\s*(\w+)", open(f"{A}/hud/{h}.txt").read()):
        if m not in huds: errors.append(f"hud {h}: Call {m} missing")

# ---- doc coverage ----
def doc_keys(pattern, files):
    keys = set()
    for f in files:
        for m in re.findall(pattern, open(f, encoding="utf-8").read()): keys.add(m)
    return keys
vdocs = glob.glob(f"{docs}/Readme_Vehicle*.md")
vkeys = doc_keys(r"^### `([a-z_0-9]+)`", vdocs) | doc_keys(r"^### `([a-z_0-9]+)` / `([a-z_0-9]+)`", vdocs)
# expand slash-joined headings
vkeys2 = set()
for f in vdocs:
    for line in open(f, encoding="utf-8"):
        if line.startswith("### "):
            for k in re.findall(r"`([a-z_0-9]+)`", line): vkeys2.add(k)
        for k in re.findall(r"^\s*- `([a-z_0-9]+)`", line): vkeys2.add(k)
vkeys = vkeys2
wkeys = set()
for f in glob.glob(f"{docs}/Readme_Weapon*.md"):
    for line in open(f, encoding="utf-8"):
        if line.startswith("### "):
            for k in re.findall(r"`([A-Za-z_0-9]+)`", line): wkeys.add(k.lower())

print("=== reference errors ===")
print("\n".join(errors) if errors else "none")
print("\n=== documented vehicle keys NOT used ===")
print(sorted(vkeys - used_vehicle_keys - {"runway", "x", "y", "z"}))
print("\n=== documented weapon keys NOT used ===")
print(sorted(wkeys - used_weapon_keys - {"type"}))
print("\nvehicle keys used:", len(used_vehicle_keys), " weapon keys used:", len(used_weapon_keys))
