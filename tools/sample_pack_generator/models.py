import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from lib import ObjBuilder

OUT_OBJ = None
OUT_TEX = None
OUT_OBJ_REF = None  # "o"-declared reference copy, kept out of the loadable addon - see lib.ObjBuilder.write()'s own doc

def save(b):
    b.write(os.path.join(OUT_OBJ, b.name + ".obj"), declare="g")
    if OUT_OBJ_REF:
        b.write(os.path.join(OUT_OBJ_REF, b.name + ".obj"), declare="o")
    b.write_texture(os.path.join(OUT_TEX, b.name + ".png"))
    print("model:", b.name, len(b.faces)//6, "boxes")

GLASS = (120, 190, 230, 110)

# =====================================================================
# 1. Helicopter  (attack helicopter with chin turret, pods, missiles)
# =====================================================================
def helicopter():
    b = ObjBuilder("sample_helicopter")
    hull = b.color("hull", (70, 88, 60, 255)); dark = b.color("dark", (40, 45, 40, 255))
    rotor = b.color("rotor", (30, 30, 30, 255)); glass = b.color("glass", GLASS)
    red = b.color("red", (220, 40, 40, 255)); green = b.color("green", (40, 220, 60, 255))
    white = b.color("white", (240, 240, 240, 255)); missile = b.color("missile", (200, 200, 205, 255))
    # fuselage
    b.box("hull", 0, 1.0, 0.8, 1.6, 1.3, 4.0, hull)
    b.box("hull", 0, 0.9, 3.2, 1.2, 1.0, 1.4, hull)          # nose
    b.box("hull", 0, 1.9, -0.2, 1.0, 0.6, 2.0, dark)          # engine hump
    b.box("hull", 0, 1.4, -4.2, 0.5, 0.5, 4.5, hull)          # tail boom
    b.box("hull", 0, 2.2, -6.2, 0.15, 1.6, 0.9, hull)         # tail fin
    b.box("hull", 0, 1.5, -6.0, 1.8, 0.12, 0.6, hull)         # horizontal stabilizer
    # skids
    for x in (-0.9, 0.9):
        b.box("hull", x, 0.1, 0.8, 0.12, 0.12, 3.6, dark)
        b.box("hull", x, 0.45, 1.8, 0.1, 0.7, 0.1, dark); b.box("hull", x, 0.45, -0.2, 0.1, 0.7, 0.1, dark)
    # stub wings + rocket pods + missiles
    b.box("hull", 0, 1.2, 0.2, 4.6, 0.15, 0.7, hull)
    for x in (-2.0, 2.0):
        b.box("hull", x, 1.0, 0.2, 0.5, 0.5, 1.4, dark)       # rocket pod
    b.box("$missile_l1", -1.3, 0.85, 0.2, 0.22, 0.22, 1.6, missile)
    b.box("$missile_l2", -1.6, 0.85, 0.2, 0.22, 0.22, 1.6, missile)
    b.box("$missile_r1", 1.3, 0.85, 0.2, 0.22, 0.22, 1.6, missile)
    b.box("$missile_r2", 1.6, 0.85, 0.2, 0.22, 0.22, 1.6, missile)
    # canopy (rotates up around rear edge)
    b.box("$canopy", 0, 1.85, 2.2, 1.2, 0.5, 2.0, glass)
    # side door (slides backwards)
    b.box("$hatch_door_l", -0.85, 1.0, -0.3, 0.06, 1.0, 1.2, dark)
    # main rotor: hub + 4 blades
    b.box("$main_rotor", 0, 2.45, 0.5, 0.4, 0.3, 0.4, rotor)
    b.box("$main_rotor", 0, 2.5, 0.5, 8.0, 0.06, 0.35, rotor)
    b.box("$main_rotor", 0, 2.5, 0.5, 0.35, 0.06, 8.0, rotor)
    # tail rotor (rotates around X at the tail)
    b.box("$tail_rotor", 0.35, 2.0, -6.3, 0.06, 2.0, 0.25, rotor)
    b.box("$tail_rotor", 0.35, 2.0, -6.3, 0.06, 0.25, 2.0, rotor)
    # chin turret gun (weapon_parts, follows pilot view)
    b.box("$chin_turret", 0, 0.35, 3.0, 0.6, 0.5, 0.6, dark)
    b.box("$chin_turret", 0, 0.35, 3.8, 0.15, 0.15, 1.2, rotor)
    # searchlight + its hatch cover
    b.box("$search_light", 0, 0.3, 1.5, 0.4, 0.3, 0.4, white)
    b.box("$light_hatch", 0, 0.1, 1.5, 0.45, 0.06, 0.45, dark)
    # nav lights
    b.box("$nav_light_l", -2.3, 1.3, 0.2, 0.12, 0.12, 0.12, red)
    b.box("$nav_light_r", 2.3, 1.3, 0.2, 0.12, 0.12, 0.12, green)
    save(b)

# =====================================================================
# 2. Fixed-wing aircraft (swing-wing fighter; also used by CAS/Carrier)
# =====================================================================
def aircraft():
    b = ObjBuilder("sample_aircraft")
    hull = b.color("hull", (110, 120, 130, 255)); dark = b.color("dark", (45, 50, 55, 255))
    glass = b.color("glass", GLASS); red = b.color("red", (220, 40, 40, 255))
    green = b.color("green", (40, 220, 60, 255)); missile = b.color("missile", (220, 220, 225, 255))
    tank = b.color("tank", (150, 150, 120, 255)); gear = b.color("gear", (60, 60, 60, 255))
    # fuselage
    b.box("hull", 0, 1.0, 0.0, 1.4, 1.2, 9.0, hull)
    b.box("hull", 0, 0.9, 5.2, 0.9, 0.9, 1.6, hull)        # nose cone
    b.box("hull", 0, 0.9, -5.2, 1.4, 1.0, 1.6, dark)       # exhaust
    b.box("hull", 0, 2.2, -3.5, 0.15, 1.6, 2.0, hull)      # vertical tail
    b.box("hull", 0, 1.1, -4.0, 4.0, 0.12, 1.4, hull)      # tailplane
    b.box("hull", 0, 1.0, 1.0, 3.2, 0.5, 3.0, hull)        # wing root / glove
    # swing wings (pivot near the glove, rotate about Y)
    b.box("$wing_l", -3.4, 1.0, 0.2, 4.4, 0.12, 1.8, hull)
    b.box("$wing_r",  3.4, 1.0, 0.2, 4.4, 0.12, 1.8, hull)
    # canopy
    b.box("$canopy", 0, 1.85, 2.4, 1.0, 0.55, 2.2, glass)
    # gun muzzle (left nose)
    b.box("hull", -0.6, 0.9, 4.0, 0.15, 0.15, 1.0, dark)
    # missiles on wing glove pylons (ammo_parts)
    b.box("$aam_l", -1.8, 0.65, 0.8, 0.18, 0.18, 2.2, missile)
    b.box("$aam_r",  1.8, 0.65, 0.8, 0.18, 0.18, 2.2, missile)
    # centreline drop tank (ammo_parts for DropTank)
    b.box("$drop_tank", 0, 0.3, 0.0, 0.5, 0.5, 3.4, tank)
    # weapon bay doors under fuselage (open while bay weapon selected)
    b.box("$bay_door_l", -0.35, 0.38, -1.5, 0.7, 0.06, 2.0, hull)
    b.box("$bay_door_r",  0.35, 0.38, -1.5, 0.7, 0.06, 2.0, hull)
    # landing gear: nose + mains, each with a hatch cover
    b.box("$gear_nose", 0, 0.15, 3.5, 0.12, 0.8, 0.12, gear)
    b.box("$gear_nose", 0, -0.2, 3.5, 0.3, 0.3, 0.12, dark)
    b.box("$gear_hatch_nose", 0.2, 0.4, 3.5, 0.3, 0.06, 1.0, hull)
    for x,n in ((-1.0,"l"),(1.0,"r")):
        b.box(f"$gear_main_{n}", x, 0.15, -0.5, 0.12, 0.8, 0.12, gear)
        b.box(f"$gear_main_{n}", x, -0.2, -0.5, 0.35, 0.35, 0.14, dark)
        b.box(f"$gear_hatch_main_{n}", x*1.25, 0.4, -0.5, 0.45, 0.06, 1.2, hull)
    # nav lights on wingtips of the glove
    b.box("$nav_light_l", -1.7, 1.0, 1.0, 0.12, 0.12, 0.12, red)
    b.box("$nav_light_r",  1.7, 1.0, 1.0, 0.12, 0.12, 0.12, green)
    save(b)

# =====================================================================
# 3. Small UAV target drone
# =====================================================================
def uav():
    b = ObjBuilder("sample_uav")
    hull = b.color("hull", (200, 200, 200, 255)); dark = b.color("dark", (60, 60, 60, 255))
    prop = b.color("prop", (30, 30, 30, 255)); orange = b.color("orange", (240, 130, 30, 255))
    b.box("hull", 0, 0.5, 0, 0.8, 0.4, 0.8, hull)
    b.box("hull", 0, 0.3, 0, 0.5, 0.2, 0.5, orange)                 # payload / camera pod
    for x,z,n in ((-0.9,0.9,"fl"),(0.9,0.9,"fr"),(-0.9,-0.9,"rl"),(0.9,-0.9,"rr")):
        b.box("hull", x/2, 0.55, z/2, 0.15, 0.1, 1.2 if abs(z)>0 else 0.15, dark, rot_y=45 if x*z>0 else -45)
        b.box(f"$rotor_{n}", x, 0.7, z, 1.0, 0.04, 0.12, prop)
        b.box(f"$rotor_{n}", x, 0.7, z, 0.12, 0.04, 1.0, prop)
        b.box("hull", x, 0.6, z, 0.15, 0.15, 0.15, dark)
    b.box("$hatch_bay", 0, 0.18, 0, 0.5, 0.05, 0.5, dark)
    save(b)

# =====================================================================
# 4. VTOL (tiltrotor transport with bomb bay)
# =====================================================================
def vtol():
    b = ObjBuilder("sample_vtol")
    hull = b.color("hull", (100, 105, 95, 255)); dark = b.color("dark", (40, 42, 40, 255))
    glass = b.color("glass", GLASS); prop = b.color("prop", (30, 30, 30, 255))
    bomb = b.color("bomb", (60, 90, 60, 255)); red = b.color("red", (220,40,40,255)); green = b.color("green", (40,220,60,255))
    b.box("hull", 0, 1.4, 0.0, 1.8, 1.6, 7.0, hull)
    b.box("hull", 0, 1.3, 4.0, 1.4, 1.2, 1.4, hull)
    b.box("$canopy", 0, 2.1, 3.2, 1.2, 0.5, 1.6, glass)
    b.box("hull", 0, 2.6, -3.0, 0.15, 1.4, 1.5, hull)
    b.box("hull", 0, 3.2, -3.0, 3.0, 0.12, 0.8, hull)
    b.box("hull", 0, 2.1, 0.5, 8.0, 0.2, 1.4, hull)                   # wing
    # tilting nacelles at the wingtips (pivot at wingtip, rotate about X)
    for x,n in ((-4.2,"l"),(4.2,"r")):
        b.box(f"$rotor_nacelle_{n}", x, 2.1, 0.5, 0.7, 0.7, 1.8, dark)
        b.box(f"$prop_{n}", x, 2.1, 1.5, 3.2, 0.3, 0.06, prop)
        b.box(f"$prop_{n}", x, 2.1, 1.5, 0.3, 3.2, 0.06, prop)
    # rear ramp hatch (rotates down)
    b.box("$hatch_ramp", 0, 0.9, -3.6, 1.6, 1.0, 0.1, dark)
    # bomb bay doors + bombs
    b.box("$bay_door_l", -0.45, 0.62, 0.0, 0.9, 0.06, 2.6, hull)
    b.box("$bay_door_r",  0.45, 0.62, 0.0, 0.9, 0.06, 2.6, hull)
    b.box("$bomb_1", -0.3, 0.95, 0.4, 0.3, 0.3, 1.3, bomb)
    b.box("$bomb_2",  0.3, 0.95, 0.4, 0.3, 0.3, 1.3, bomb)
    # gear (fixed skids)
    for x in (-0.7, 0.7):
        b.box("hull", x, 0.3, 0.0, 0.2, 0.6, 0.3, dark); b.box("hull", x, 0.3, 2.8, 0.2, 0.6, 0.3, dark)
    b.box("$nav_light_l", -4.5, 2.5, 0.5, 0.12,0.12,0.12, red); b.box("$nav_light_r", 4.5, 2.5, 0.5, 0.12,0.12,0.12, green)
    save(b)

# =====================================================================
# 5. Half-track (front wheels steer, rear crawler tracks, turret)
# =====================================================================
def halftrack():
    b = ObjBuilder("sample_halftrack")
    hull = b.color("hull", (95, 100, 70, 255)); dark = b.color("dark", (35, 35, 30, 255))
    tire = b.color("tire", (25, 25, 25, 255)); track = b.color("track", (50, 50, 50, 255))
    glass = b.color("glass", GLASS); white = b.color("white", (240,240,240,255))
    b.box("hull", 0, 1.0, 0.5, 2.4, 0.9, 6.0, hull)              # body
    b.box("hull", 0, 1.7, 2.4, 2.2, 0.6, 1.6, hull)              # cab
    b.box("$canopy", 0, 1.75, 3.25, 2.0, 0.5, 0.08, glass)       # windshield (folds forward)
    b.box("hull", 0, 0.7, 3.8, 2.0, 0.9, 1.0, dark)              # bonnet/engine
    b.box("$hatch_rear", 0, 1.3, -2.5, 2.2, 0.6, 0.08, dark)     # tailgate
    # steering wheel inside cab
    b.box("$steering_wheel", 0.6, 1.6, 2.9, 0.5, 0.5, 0.05, dark)
    # front wheels (steer around Y)
    for x,n in ((-1.35,"l"),(1.35,"r")):
        b.box(f"$wheel_f{n}", x, 0.5, 3.4, 0.4, 1.0, 1.0, tire)
    # rear crawler track guide + rollers
    for x,n in ((-1.35,"l"),(1.35,"r")):
        b.box("hull", x, 0.55, -0.8, 0.2, 0.3, 3.4, dark)          # track frame
        for z,i in ((0.4,1),(-0.8,2),(-2.0,3)):
            b.box(f"$roller{i}_{n}", x, 0.4, z, 0.45, 0.6, 0.6, track)
    b.box("$track_link", 0, 0.12, 0.4, 0.5, 0.15, 0.35, track)   # single link; the mod instances it along each crawler path
    # turret ring + gun (weapon_parts with child barrel)
    b.box("$turret", 0, 1.65, -0.8, 1.2, 0.5, 1.2, hull)
    b.box("$turret_gun", 0, 1.75, 0.2, 0.18, 0.18, 1.8, dark)
    # searchlight (steering-follow) on the bonnet
    b.box("$search_light", 0.7, 1.3, 3.9, 0.3, 0.3, 0.3, white)
    save(b)

# =====================================================================
# 6. Ship (small carrier with elevator + ASW launcher)
# =====================================================================
def ship():
    b = ObjBuilder("sample_ship")
    hull = b.color("hull", (95, 100, 110, 255)); deck = b.color("deck", (70, 70, 75, 255))
    dark = b.color("dark", (40, 40, 45, 255)); glass = b.color("glass", GLASS)
    white = b.color("white", (240,240,240,255)); red = b.color("red", (220,40,40,255)); green = b.color("green", (40,220,60,255))
    L = 60.0
    b.box("hull", 0, 2.0, 0, 12.0, 4.0, L, hull)                  # main hull
    b.box("hull", 0, 0.8, 32.0, 6.0, 2.4, 6.0, hull)              # bow
    b.box("hull", 0, 4.3, 2.0, 16.0, 0.6, 56.0, deck)             # flight deck (runway top ~ y 4.6)
    b.box("hull", 6.5, 7.0, -6.0, 2.5, 5.0, 8.0, hull)            # island (starboard = -X? island on left/+X side)
    b.box("hull", 6.5, 10.0, -6.0, 0.4, 3.0, 0.4, dark)           # mast
    b.box("$canopy", 6.5, 8.6, -2.2, 2.4, 0.9, 0.1, glass)        # bridge windows (front of island)
    b.box("$radar", 6.5, 11.6, -6.0, 2.4, 0.15, 0.4, dark)        # rotating radar
    # elevator platform (slides down 6 blocks with the hatch)
    b.box("$hatch_elevator", -4.0, 4.55, -18.0, 5.0, 0.2, 6.0, dark)
    # ASW missile launcher box (weapon_parts turret, seat 1)
    b.box("$asw_launcher", 4.5, 5.2, 24.0, 1.6, 1.0, 2.0, dark)
    b.box("$asw_launcher", 4.5, 5.9, 24.5, 1.4, 0.4, 1.0, dark)
    # CIWS turret on bow (seat 1, MachineGun2)
    b.box("$ciws", -4.5, 5.0, 24.0, 1.0, 0.9, 1.0, white)
    b.box("$ciws_gun", -4.5, 5.2, 25.0, 0.2, 0.2, 1.4, dark)
    # depth charge rack at the stern
    b.box("hull", -4.0, 5.0, -27.0, 1.5, 0.5, 2.5, dark)
    b.box("$dc_1", -4.0, 5.5, -26.0, 0.6, 0.6, 0.6, dark); b.box("$dc_2", -4.0, 5.5, -27.0, 0.6, 0.6, 0.6, dark)
    b.box("$search_light", 6.5, 9.2, -3.0, 0.5, 0.5, 0.5, white)
    b.box("$nav_light_l", 8.0, 5.0, 26.0, 0.2,0.2,0.2, red); b.box("$nav_light_r", -8.0, 5.0, 26.0, 0.2,0.2,0.2, green)
    save(b)

# =====================================================================
# 7. Submarine
# =====================================================================
def submarine():
    b = ObjBuilder("sample_submarine")
    hull = b.color("hull", (35, 40, 50, 255)); dark = b.color("dark", (20, 22, 28, 255))
    prop = b.color("prop", (150, 110, 60, 255)); white = b.color("white", (240,240,240,255))
    b.box("hull", 0, 1.5, 0, 3.0, 3.0, 24.0, hull)
    b.box("hull", 0, 1.5, 13.5, 2.2, 2.2, 3.0, hull)
    b.box("hull", 0, 1.5, -13.0, 2.0, 2.0, 2.0, hull)
    b.box("hull", 0, 4.0, 2.0, 1.4, 2.0, 4.0, dark)              # conning tower (sail)
    b.box("hull", 0, 4.6, 2.0, 4.0, 0.15, 1.2, dark)             # sail planes
    b.box("$hatch_top", 0, 5.1, 2.5, 0.8, 0.1, 0.8, dark)        # tower hatch (surfaced = open)
    b.box("$periscope", 0, 5.8, 1.5, 0.15, 1.6, 0.15, dark)
    b.box("hull", 0, 1.5, -12.0, 0.15, 4.0, 1.5, hull)           # rudder
    b.box("hull", 0, 1.5, -12.0, 4.0, 0.15, 1.5, hull)           # stern planes
    b.box("$screw", 0, 1.5, -14.3, 1.8, 0.1, 0.4, prop); b.box("$screw", 0, 1.5, -14.3, 0.1, 1.8, 0.4, prop)
    b.box("$torpedo_l", -0.6, 1.2, 12.0, 0.4, 0.4, 3.0, white); b.box("$torpedo_r", 0.6, 1.2, 12.0, 0.4, 0.4, 3.0, white)
    save(b)

# =====================================================================
# 8. Static emplacement (twin AA gun + CAS radio)
# =====================================================================
def emplacement():
    b = ObjBuilder("sample_emplacement")
    base = b.color("base", (120, 110, 90, 255)); dark = b.color("dark", (45, 45, 45, 255))
    steel = b.color("steel", (150, 150, 155, 255)); white = b.color("white", (240,240,240,255))
    b.box("hull", 0, 0.3, 0, 3.0, 0.6, 3.0, base)                # concrete base
    b.box("hull", 0, 0.9, 0, 1.4, 0.6, 1.4, dark)                # pedestal
    b.box("$turret", 0, 1.6, 0, 1.6, 0.8, 1.6, steel)            # rotating mount (yaw)
    b.box("$turret_shield", 0, 2.0, 0.5, 2.0, 1.0, 0.1, steel)
    b.box("$gun_cradle", 0, 1.8, 0.6, 1.2, 0.5, 0.8, dark)       # pitch part (child)
    b.box("$gun_cradle", -0.35, 1.85, 1.8, 0.16, 0.16, 2.4, dark)
    b.box("$gun_cradle",  0.35, 1.85, 1.8, 0.16, 0.16, 2.4, dark)
    b.box("$radio_mast", 1.2, 1.8, -1.2, 0.1, 2.4, 0.1, dark)    # CAS radio
    b.box("$hatch_ammo", -1.2, 0.7, -1.2, 0.6, 0.2, 0.6, dark)   # ammo box lid
    b.box("$search_light", 1.0, 1.1, 1.2, 0.4, 0.4, 0.4, white)
    save(b)

# ---------- bullet models ----------
def bullets():
    for name, col, dims in (("rocket", (200,60,40,255), (0.15,0.15,0.9)),
                            ("missile", (220,220,225,255), (0.18,0.18,1.6)),
                            ("bomb", (60,90,60,255), (0.3,0.3,1.2)),
                            ("torpedo", (240,240,240,255), (0.35,0.35,2.6)),
                            ("shell", (230,190,60,255), (0.1,0.1,0.35)),
                            ("cartridge", (200,170,50,255), (0.06,0.06,0.18))):
        b = ObjBuilder("bullet_" + name)
        c = b.color("body", col); fin = b.color("fin", (60,60,60,255))
        b.box("body", 0,0,0, *dims, c)
        b.box("body", 0,0,-dims[2]*0.4, dims[0]*2.2, 0.03, dims[2]*0.25, fin)
        b.box("body", 0,0,-dims[2]*0.4, 0.03, dims[1]*2.2, dims[2]*0.25, fin)
        save(b)

def build(out_obj, out_tex, out_obj_ref=None):
    global OUT_OBJ, OUT_TEX, OUT_OBJ_REF
    OUT_OBJ, OUT_TEX, OUT_OBJ_REF = out_obj, out_tex, out_obj_ref
    os.makedirs(out_obj, exist_ok=True); os.makedirs(out_tex, exist_ok=True)
    if out_obj_ref: os.makedirs(out_obj_ref, exist_ok=True)
    helicopter(); aircraft(); uav(); vtol(); halftrack(); ship(); submarine(); emplacement(); bullets()

if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
