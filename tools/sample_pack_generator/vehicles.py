import json, os

NS = "tudur_sample"
def mdl(n): return f"{NS}:models/obj/{n}.obj"
def tex(n): return f"{NS}:textures/vehicle/{n}.png"

def seat(name, x, y, z, driver=False, **kw):
    d = {"name": name, "offset_x": x, "offset_y": y, "offset_z": z, "driver": driver}
    d.update(kw); return d

def cam(x, y, z, **kw):
    d = {"x": x, "y": y, "z": z}; d.update(kw); return d

VEHICLES = {}

# ===================================================================
# Helicopter
# ===================================================================
VEHICLES["sample_helicopter"] = {
    "entity_type": "tudursvehiclemod:helicopter",
    "model": mdl("sample_helicopter"), "texture": tex("sample_helicopter"),
    "display_name": "Sample Attack Helicopter",
    "scale": 1.0, "width": 2.2, "height": 2.6,
    "max_speed": 1.1, "acceleration": 0.05, "turn_speed": 3.2, "step_height": 1.0,
    "gravity": -0.04, "on_ground_pitch": 0.0, "reverse_throttle": 0.0,
    "engine_sound_volume": 3.0, "throttle_up_down": 1.2, "throttle_switch_hold_ticks": 40,
    "float_capable": True,
    "max_health": 120.0, "armor_damage_factor": 0.9, "armor_min_damage": 1.0, "armor_max_damage": 50.0,
    "damage_factor": 0.5, "max_fuel": 700.0, "fuel_consumption": 0.6, "inventory_size": 9,
    "submerged_damage_height": 0.5,
    "hud": "sample_hud", "engine_sound": "smp_engine_rotor",
    "passenger_display": {"hide_entity": False, "entity_width": 0.9, "entity_height": 0.9},
    "force_bounding_box": True, "regeneration": False, "default_freelook": False,
    "enable_ejection_seat": False, "flare_type": 1,
    "seats": [
        seat("pilot", 0.0, 1.3, 2.2, True, camera_positions=[cam(0.0, 1.7, 2.6), cam(0.0, 5.0, -10.0, force_camera=True)]),
        seat("gunner", 0.0, 1.3, 1.2, camera_positions=[cam(0.0, 0.5, 3.6, force_camera=True, fixed_pitch=-10.0)]),
        seat("passenger_l", -0.5, 1.2, -0.2, enable_parachuting=True),
        seat("passenger_r", 0.5, 1.2, -0.2, enable_parachuting=True),
    ],
    "weapons": [
        {"seat_index": 1, "weapon_name": "smp_turret_gun", "projectile_item": "minecraft:iron_nugget",
         "pilot_usable": True, "turret_rotation_speed": 90.0,
         "offsets": [{"x": 0.0, "y": 0.35, "z": 4.4, "linked_part": "$chin_turret"}],
         "aim_range": {"default_yaw": 0.0, "min_yaw": -110.0, "max_yaw": 110.0, "min_pitch": -60.0, "max_pitch": 15.0}},
        {"seat_index": 0, "weapon_name": "smp_rockets", "projectile_item": "minecraft:iron_ingot",
         "offsets": [{"x": -2.0, "y": 1.0, "z": 0.9, "mount_yaw": 0.0, "mount_pitch": 2.0},
                     {"x": 2.0, "y": 1.0, "z": 0.9, "mount_yaw": 0.0, "mount_pitch": 2.0}]},
        {"seat_index": 0, "weapon_name": "smp_at_missile", "projectile_item": "minecraft:iron_ingot",
         "offsets": [{"x": -1.3, "y": 0.85, "z": 1.0, "linked_part": "$missile_l1"},
                     {"x": 1.3, "y": 0.85, "z": 1.0, "linked_part": "$missile_r1"},
                     {"x": -1.6, "y": 0.85, "z": 1.0, "linked_part": "$missile_l2"},
                     {"x": 1.6, "y": 0.85, "z": 1.0, "linked_part": "$missile_r2"}]},
    ],
    "ammo_parts": [
        {"part": "$missile_l1", "weapon_name": "smp_at_missile", "slot_index": 3, "display_penalty": "3, 2"},
        {"part": "$missile_r1", "weapon_name": "smp_at_missile", "slot_index": 2, "display_penalty": "3, 2"},
        {"part": "$missile_l2", "weapon_name": "smp_at_missile", "slot_index": 1, "display_penalty": "3, 2"},
        {"part": "$missile_r2", "weapon_name": "smp_at_missile", "slot_index": 0, "display_penalty": "3, 2"},
    ],
    "spinning_parts": [
        {"part": "$main_rotor", "pivot_x": 0.0, "pivot_y": 2.5, "pivot_z": 0.5, "axis_x": 0.0, "axis_y": 1.0, "axis_z": 0.0, "speed": 55.0},
        {"part": "$tail_rotor", "pivot_x": 0.35, "pivot_y": 2.0, "pivot_z": -6.3, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "speed": 80.0},
    ],
    "toggle_parts": [
        {"part": "$canopy", "pivot_x": 0.0, "pivot_y": 1.6, "pivot_z": 1.2, "mode": "rotate",
         "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": -70.0, "trigger": "key", "speed": 6.0},
        {"part": "$hatch_door_l", "pivot_x": -0.85, "pivot_y": 1.0, "pivot_z": -0.3, "mode": "slide",
         "offset_x": 0.0, "offset_y": 0.0, "offset_z": -1.1, "trigger": "key", "speed": 4.0},
        {"part": "$light_hatch", "pivot_x": 0.0, "pivot_y": 0.1, "pivot_z": 1.3, "mode": "rotate",
         "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": 90.0, "trigger": "light_hatch", "speed": 8.0},
    ],
    "weapon_parts": [
        {"part": "$chin_turret", "seat_index": 1, "weapon_name": "smp_turret_gun", "pilot_fallback": True,
         "hide_for_gunner": False, "yaw_follow": True, "pitch_follow": True,
         "pivot_x": 0.0, "pivot_y": 0.35, "pivot_z": 3.0, "recoil_distance": 0.1,
         "aim_range": {"min_yaw": -110.0, "max_yaw": 110.0, "min_pitch": -60.0, "max_pitch": 15.0}},
    ],
    "search_light_parts": [
        {"part": "$search_light", "pivot_x": 0.0, "pivot_y": 0.3, "pivot_z": 1.5,
         "start_color_argb": -1, "end_color_argb": 0, "length": 40.0, "end_radius": 8.0,
         "yaw": 0.0, "pitch": -20.0, "steer_angle": 0.0, "follow_mode": "PILOT_VIEW"},
    ],
    "nav_light_parts": [
        {"part": "$nav_light_l", "pivot_x": -2.3, "pivot_y": 1.3, "pivot_z": 0.2, "color_argb": -65536},
        {"part": "$nav_light_r", "pivot_x": 2.3, "pivot_y": 1.3, "pivot_z": 0.2, "color_argb": -16711936},
    ],
    "spawn_item": {"display_name": "Sample Attack Helicopter", "tier": 3},
}

# ===================================================================
# Fixed-wing aircraft (also the CAS / Carrier aircraft)
# ===================================================================
def gear(part, px, py, pz, angle=90.0):
    return {"part": part, "pivot_x": px, "pivot_y": py, "pivot_z": pz, "mode": "rotate",
            "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": angle, "trigger": "landing_gear", "speed": 4.0}
def gear_hatch(part, px, py, pz, axis_z=0.0):
    return {"part": part, "pivot_x": px, "pivot_y": py, "pivot_z": pz, "mode": "rotate",
            "axis_x": 0.0 if axis_z else 1.0, "axis_y": 0.0, "axis_z": axis_z, "max_angle": 90.0,
            "trigger": "landing_gear_hatch", "speed": 8.0}

VEHICLES["sample_aircraft"] = {
    "entity_type": "tudursvehiclemod:aircraft",
    "model": mdl("sample_aircraft"), "texture": tex("sample_aircraft"),
    "display_name": "Sample Swing-wing Fighter",
    "scale": 1.0, "width": 2.4, "height": 2.4,
    "max_speed": 2.4, "acceleration": 0.06, "turn_speed": 2.8, "step_height": 0.6,
    "gravity": -0.04, "on_ground_pitch": 0.0, "reverse_throttle": 0.0,
    "engine_sound_volume": 4.0, "throttle_up_down": 1.0, "throttle_switch_hold_ticks": 40,
    "stall_speed": 0.32, "float_capable": False,
    "max_health": 100.0, "armor_damage_factor": 1.0, "armor_min_damage": 0.0, "armor_max_damage": 60.0,
    "damage_factor": 0.4, "max_fuel": 900.0, "fuel_consumption": 0.9, "inventory_size": 0,
    "hud": "sample_hud", "engine_sound": "smp_engine_jet",
    "passenger_display": {"hide_entity": True, "entity_width": 1.0, "entity_height": 1.0},
    "force_bounding_box": False, "regeneration": False, "default_freelook": False,
    "enable_ejection_seat": True, "flare_type": 2,
    "seats": [
        seat("pilot", 0.0, 1.5, 2.4, True, camera_positions=[cam(0.0, 1.9, 2.8), cam(0.0, 4.0, -14.0, force_camera=True)]),
    ],
    "weapons": [
        {"seat_index": 0, "weapon_name": "smp_minigun", "projectile_item": "minecraft:iron_nugget",
         "offsets": [{"x": -0.6, "y": 0.9, "z": 4.6, "mount_yaw": 0.0, "mount_pitch": 0.0}]},
        {"seat_index": 0, "weapon_name": "smp_aa_missile", "projectile_item": "minecraft:iron_ingot",
         "offsets": [{"x": -1.8, "y": 0.65, "z": 1.9, "linked_part": "$aam_l"},
                     {"x": 1.8, "y": 0.65, "z": 1.9, "linked_part": "$aam_r"}]},
        {"seat_index": 0, "weapon_name": "smp_as_missile", "projectile_item": "minecraft:iron_ingot",
         "offsets": [{"x": 0.0, "y": 0.5, "z": -1.5}]},
        {"seat_index": 0, "weapon_name": "smp_drop_tank", "projectile_item": "minecraft:bucket",
         "offsets": [{"x": 0.0, "y": 0.3, "z": 0.0, "linked_part": "$drop_tank"}]},
        {"seat_index": 0, "weapon_name": "smp_smoke", "projectile_item": "minecraft:white_dye",
         "offsets": [{"x": -5.5, "y": 1.0, "z": -0.6, "mount_yaw": 0.0, "mount_pitch": 0.0},
                     {"x": 5.5, "y": 1.0, "z": -0.6, "mount_yaw": 0.0, "mount_pitch": 0.0}]},
    ],
    "ammo_parts": [
        {"part": "$aam_l", "weapon_name": "smp_aa_missile", "slot_index": 1, "display_penalty": "4, 3"},
        {"part": "$aam_r", "weapon_name": "smp_aa_missile", "slot_index": 0, "display_penalty": "4, 3"},
        {"part": "$drop_tank", "weapon_name": "smp_drop_tank", "slot_index": 0, "display_penalty": "12, 8"},
    ],
    "toggle_parts": [
        {"part": "$canopy", "pivot_x": 0.0, "pivot_y": 1.6, "pivot_z": 1.3, "mode": "rotate",
         "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": -60.0, "trigger": "key", "speed": 6.0},
        {"part": "$wing_l", "pivot_x": -1.4, "pivot_y": 1.0, "pivot_z": 1.0, "mode": "rotate",
         "axis_x": 0.0, "axis_y": 1.0, "axis_z": 0.0, "max_angle": 45.0, "trigger": "wing_fold", "speed": 3.0,
         "wing_sweep": {"variable_sweep_wing": True, "sweep_wing_speed": 2.4}},
        {"part": "$wing_r", "pivot_x": 1.4, "pivot_y": 1.0, "pivot_z": 1.0, "mode": "rotate",
         "axis_x": 0.0, "axis_y": 1.0, "axis_z": 0.0, "max_angle": -45.0, "trigger": "wing_fold", "speed": 3.0,
         "wing_sweep": {"variable_sweep_wing": True, "sweep_wing_speed": 2.4}},
        gear("$gear_nose", 0.0, 0.4, 3.5, -90.0),
        gear("$gear_main_l", -1.0, 0.4, -0.5, 90.0),
        gear("$gear_main_r", 1.0, 0.4, -0.5, 90.0),
        gear_hatch("$gear_hatch_nose", 0.35, 0.4, 3.5, axis_z=1.0),
        gear_hatch("$gear_hatch_main_l", -1.5, 0.4, -0.5, axis_z=1.0),
        gear_hatch("$gear_hatch_main_r", 1.5, 0.4, -0.5, axis_z=1.0),
        {"part": "$bay_door_l", "pivot_x": -0.7, "pivot_y": 0.4, "pivot_z": -1.5, "mode": "rotate",
         "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0, "max_angle": -100.0, "trigger": "weapon_bay", "weapon_name": "smp_as_missile", "speed": 5.0},
        {"part": "$bay_door_r", "pivot_x": 0.7, "pivot_y": 0.4, "pivot_z": -1.5, "mode": "rotate",
         "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0, "max_angle": 100.0, "trigger": "weapon_bay", "weapon_name": "smp_as_missile", "speed": 5.0},
    ],
    "nav_light_parts": [
        {"part": "$nav_light_l", "pivot_x": -1.7, "pivot_y": 1.0, "pivot_z": 1.0, "color_argb": -65536},
        {"part": "$nav_light_r", "pivot_x": 1.7, "pivot_y": 1.0, "pivot_z": 1.0, "color_argb": -16711936},
    ],
    "spawn_item": {"display_name": "Sample Swing-wing Fighter", "tier": 4},
}

# ===================================================================
# UAV quadcopter (helicopter type, unmanned)
# ===================================================================
VEHICLES["sample_uav"] = {
    "entity_type": "tudursvehiclemod:helicopter",
    "model": mdl("sample_uav"), "texture": tex("sample_uav"),
    "display_name": "Sample Quadcopter UAV",
    "scale": 1.0, "width": 1.2, "height": 0.9,
    "max_speed": 0.9, "acceleration": 0.08, "turn_speed": 5.0, "gravity": -0.04,
    "max_health": 20.0, "max_fuel": 300.0, "fuel_consumption": 0.3,
    "is_uav": True, "is_target_drone": True,
    "hud": "sample_hud", "engine_sound": "smp_engine_rotor", "engine_sound_volume": 1.0,
    "passenger_display": {"hide_entity": True, "entity_width": 0.5, "entity_height": 0.5},
    "seats": [seat("operator", 0.0, 0.6, 0.0, True)],
    "weapons": [
        {"seat_index": 0, "weapon_name": "smp_kamikaze", "projectile_item": "minecraft:tnt",
         "offsets": [{"x": 0.0, "y": 0.2, "z": 0.0}]},
    ],
    "spinning_parts": [
        {"part": f"$rotor_{n}", "pivot_x": x, "pivot_y": 0.7, "pivot_z": z, "axis_x": 0.0, "axis_y": 1.0, "axis_z": 0.0, "speed": 70.0 if n in ("fl","rr") else -70.0}
        for x, z, n in ((-0.9, 0.9, "fl"), (0.9, 0.9, "fr"), (-0.9, -0.9, "rl"), (0.9, -0.9, "rr"))
    ],
    "toggle_parts": [
        {"part": "$hatch_bay", "pivot_x": 0.0, "pivot_y": 0.18, "pivot_z": -0.25, "mode": "rotate",
         "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": 90.0, "trigger": "weapon_bay", "weapon_name": "smp_kamikaze", "speed": 8.0},
    ],
    "spawn_item": {"display_name": "Sample Quadcopter UAV", "tier": 1},
}

# ===================================================================
# VTOL tiltrotor transport
# ===================================================================
VEHICLES["sample_vtol"] = {
    "entity_type": "tudursvehiclemod:vtol",
    "model": mdl("sample_vtol"), "texture": tex("sample_vtol"),
    "display_name": "Sample Tiltrotor",
    "scale": 1.0, "width": 2.8, "height": 3.4,
    "max_speed": 1.8, "acceleration": 0.05, "turn_speed": 2.6, "step_height": 1.0,
    "gravity": -0.04, "stall_speed": 0.3, "float_capable": True, "vtol_hover_speed_fraction": 0.15,
    "engine_sound_volume": 3.0, "throttle_up_down": 1.0,
    "max_health": 140.0, "armor_damage_factor": 0.8, "damage_factor": 0.5,
    "max_fuel": 800.0, "fuel_consumption": 0.7, "inventory_size": 18,
    "hud": "sample_hud", "engine_sound": "smp_engine_rotor",
    "mob_drop_option": {"rel_x": 0.0, "rel_y": -0.5, "rel_z": -3.8, "interval_ticks": 15},
    "seats": [
        seat("pilot", 0.0, 1.6, 3.2, True, camera_positions=[cam(0.0, 2.0, 3.6), cam(0.0, 5.0, -12.0, force_camera=True)]),
        seat("copilot", 0.0, 1.6, 2.4),
        seat("trooper_1", -0.6, 1.2, 0.0, enable_parachuting=True),
        seat("trooper_2", 0.6, 1.2, 0.0, enable_parachuting=True),
        seat("trooper_3", -0.6, 1.2, -1.4, enable_parachuting=True),
        seat("trooper_4", 0.6, 1.2, -1.4, enable_parachuting=True),
    ],
    "weapons": [
        {"seat_index": 0, "weapon_name": "smp_bomb", "projectile_item": "minecraft:iron_block",
         "offsets": [{"x": -0.3, "y": 0.9, "z": 0.4, "linked_part": "$bomb_1"}, {"x": 0.3, "y": 0.9, "z": 0.4, "linked_part": "$bomb_2"}]},
        {"seat_index": 0, "weapon_name": "smp_fae_bomb", "projectile_item": "minecraft:iron_block",
         "offsets": [{"x": 0.0, "y": 0.8, "z": -0.8}]},
        {"seat_index": 0, "weapon_name": "smp_missile", "projectile_item": "minecraft:iron_ingot",
         "offsets": [{"x": -3.0, "y": 1.8, "z": 0.5}, {"x": 3.0, "y": 1.8, "z": 0.5}]},
        {"seat_index": 1, "weapon_name": "smp_mk_rocket", "projectile_item": "minecraft:iron_ingot", "pilot_usable": True,
         "offsets": [{"x": -2.0, "y": 1.8, "z": 0.6}, {"x": 2.0, "y": 1.8, "z": 0.6}]},
    ],
    "ammo_parts": [
        {"part": "$bomb_1", "weapon_name": "smp_bomb", "slot_index": 1, "display_penalty": "6, 4"},
        {"part": "$bomb_2", "weapon_name": "smp_bomb", "slot_index": 0, "display_penalty": "6, 4"},
    ],
    "vtol_rotor_parts": [
        {"part": "$rotor_nacelle_l", "pivot_x": -4.2, "pivot_y": 2.1, "pivot_z": 0.5, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0},
        {"part": "$rotor_nacelle_r", "pivot_x": 4.2, "pivot_y": 2.1, "pivot_z": 0.5, "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0},
    ],
    "spinning_parts": [
        {"part": "$prop_l", "pivot_x": -4.2, "pivot_y": 2.1, "pivot_z": 1.5, "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0, "speed": 60.0, "vtol_rotor_parent": "$rotor_nacelle_l"},
        {"part": "$prop_r", "pivot_x": 4.2, "pivot_y": 2.1, "pivot_z": 1.5, "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0, "speed": -60.0, "vtol_rotor_parent": "$rotor_nacelle_r"},
    ],
    "toggle_parts": [
        {"part": "$canopy", "pivot_x": 0.0, "pivot_y": 1.85, "pivot_z": 2.4, "mode": "rotate", "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": -55.0, "trigger": "key", "speed": 6.0},
        {"part": "$hatch_ramp", "pivot_x": 0.0, "pivot_y": 0.4, "pivot_z": -3.6, "mode": "rotate", "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": -80.0, "trigger": "key", "speed": 3.0},
        {"part": "$bay_door_l", "pivot_x": -0.9, "pivot_y": 0.62, "pivot_z": 0.0, "mode": "rotate", "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0, "max_angle": -100.0, "trigger": "weapon_bay", "weapon_name": "smp_bomb", "speed": 5.0},
        {"part": "$bay_door_r", "pivot_x": 0.9, "pivot_y": 0.62, "pivot_z": 0.0, "mode": "rotate", "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0, "max_angle": 100.0, "trigger": "weapon_bay", "weapon_name": "smp_bomb", "speed": 5.0},
    ],
    "nav_light_parts": [
        {"part": "$nav_light_l", "pivot_x": -4.5, "pivot_y": 2.5, "pivot_z": 0.5, "color_argb": -65536},
        {"part": "$nav_light_r", "pivot_x": 4.5, "pivot_y": 2.5, "pivot_z": 0.5, "color_argb": -16711936},
    ],
    "spawn_item": {"display_name": "Sample Tiltrotor", "tier": 4},
}

# ===================================================================
# Half-track
# ===================================================================
track_path = [{"y": 0.1, "z": 0.9}, {"y": 0.75, "z": 0.9}, {"y": 0.75, "z": -2.5}, {"y": 0.1, "z": -2.5}]
VEHICLES["sample_halftrack"] = {
    "entity_type": "tudursvehiclemod:car",
    "model": mdl("sample_halftrack"), "texture": tex("sample_halftrack"),
    "display_name": "Sample Half-track",
    "scale": 1.0, "width": 2.6, "height": 2.2,
    "max_speed": 0.75, "acceleration": 0.04, "turn_speed": 2.5, "step_height": 1.1,
    "gravity": -0.04, "on_ground_pitch": 0.0, "reverse_throttle": -0.3,
    "pivot_turn_throttle": 0.1, "wheel_rotation_speed": 40.0, "weight_type": "car",
    "engine_sound_volume": 2.0, "throttle_up_down": 1.5, "throttle_switch_hold_ticks": 20,
    "max_health": 160.0, "armor_damage_factor": 0.7, "armor_min_damage": 2.0, "armor_max_damage": 40.0,
    "damage_factor": 0.3, "max_fuel": 500.0, "fuel_consumption": 0.4, "inventory_size": 27,
    "submerged_damage_height": 1.0,
    "hud": "sample_hud", "engine_sound": "smp_engine_car",
    "passenger_display": {"hide_entity": False, "entity_width": 1.0, "entity_height": 1.0},
    "seats": [
        seat("driver", 0.6, 1.3, 2.7, True, camera_positions=[cam(0.6, 1.7, 2.9), cam(0.0, 4.0, -8.0, force_camera=True)]),
        seat("gunner", 0.0, 1.6, -0.8, camera_positions=[cam(0.0, 2.4, -0.8, force_camera=True)]),
        seat("commander", -0.6, 1.3, 2.7),
        seat("trooper_1", -0.7, 1.2, 0.6, enable_parachuting=False),
        seat("trooper_2", 0.7, 1.2, 0.6),
    ],
    "weapons": [
        {"seat_index": 1, "weapon_name": "smp_turret_gun", "projectile_item": "minecraft:iron_nugget",
         "pilot_usable": True, "turret_rotation_speed": 60.0,
         "offsets": [{"x": 0.0, "y": 1.75, "z": 1.2, "linked_part": "$turret_gun"}],
         "aim_range": {"default_yaw": 0.0, "min_yaw": -180.0, "max_yaw": 180.0, "min_pitch": -15.0, "max_pitch": 70.0}},
        {"seat_index": 0, "weapon_name": "smp_dispenser", "projectile_item": "minecraft:water_bucket",
         "offsets": [{"x": 0.0, "y": 1.5, "z": 4.3, "mount_pitch": 15.0}]},
        {"seat_index": 2, "weapon_name": "smp_targeting_pod", "projectile_item": "minecraft:spyglass", "pilot_usable": True,
         "offsets": [{"x": -0.6, "y": 1.9, "z": 2.7}],
         "aim_range": {"min_yaw": -90.0, "max_yaw": 90.0, "min_pitch": -20.0, "max_pitch": 60.0}},
    ],
    "wheel_parts": [
        {"part": "$wheel_fl", "pivot_x": -1.35, "pivot_y": 0.5, "pivot_z": 3.4, "steer_angle": 30.0,
         "steer_axis_x": 0.0, "steer_axis_y": 1.0, "steer_axis_z": 0.0, "steer_pivot_x": -1.35, "steer_pivot_y": 0.5, "steer_pivot_z": 3.4},
        {"part": "$wheel_fr", "pivot_x": 1.35, "pivot_y": 0.5, "pivot_z": 3.4, "steer_angle": 30.0},
    ],
    "steering_wheel_parts": [
        {"part": "$steering_wheel", "pivot_x": 0.6, "pivot_y": 1.6, "pivot_z": 2.9, "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0, "max_angle": 130.0},
    ],
    "crawler_tracks": [
        {"part": "$track_link", "flip": False, "link_spacing": 0.45, "x": 1.35, "path": track_path},
        {"part": "$track_link", "flip": True, "link_spacing": 0.45, "x": -1.35, "path": track_path},
    ],
    "track_roller_parts": [
        {"part": f"$roller{i}_{n}", "pivot_x": x, "pivot_y": 0.4, "pivot_z": z, "rotations_per_block": 0.55}
        for x, n in ((-1.35, "l"), (1.35, "r")) for z, i in ((0.4, 1), (-0.8, 2), (-2.0, 3))
    ],
    "weapon_parts": [
        {"part": "$turret", "seat_index": 1, "weapon_name": "smp_turret_gun", "pilot_fallback": True,
         "yaw_follow": True, "pitch_follow": False, "pivot_x": 0.0, "pivot_y": 1.65, "pivot_z": -0.8},
        {"part": "$turret_gun", "seat_index": 1, "weapon_name": "smp_turret_gun", "pilot_fallback": True,
         "yaw_follow": False, "pitch_follow": True, "hide_for_gunner": True,
         "pivot_x": 0.0, "pivot_y": 1.75, "pivot_z": -0.6, "recoil_distance": 0.2,
         "child_info": {"parent_yaw_follow": True, "parent_pitch_follow": False, "parent_pivot_x": 0.0, "parent_pivot_y": 1.65, "parent_pivot_z": -0.8},
         "aim_range": {"min_yaw": -180.0, "max_yaw": 180.0, "min_pitch": -15.0, "max_pitch": 70.0}},
    ],
    "toggle_parts": [
        {"part": "$canopy", "pivot_x": 0.0, "pivot_y": 1.5, "pivot_z": 3.25, "mode": "rotate", "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": 80.0, "trigger": "key", "speed": 6.0},
        {"part": "$hatch_rear", "pivot_x": 0.0, "pivot_y": 1.0, "pivot_z": -2.5, "mode": "rotate", "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": -90.0, "trigger": "key", "speed": 4.0},
    ],
    "search_light_parts": [
        {"part": "$search_light", "pivot_x": 0.7, "pivot_y": 1.3, "pivot_z": 3.9,
         "start_color_argb": -1, "end_color_argb": 0, "length": 25.0, "end_radius": 5.0,
         "yaw": 0.0, "pitch": -5.0, "steer_angle": 30.0, "follow_mode": "STEERING"},
    ],
    "spawn_item": {"display_name": "Sample Half-track", "tier": 2},
}

# ===================================================================
# Ship (light carrier)
# ===================================================================
VEHICLES["sample_ship"] = {
    "entity_type": "tudursvehiclemod:ship",
    "model": mdl("sample_ship"), "texture": tex("sample_ship"),
    "display_name": "Sample Light Carrier",
    "scale": 1.0, "width": 16.0, "height": 12.0,
    "max_speed": 0.9, "acceleration": 0.01, "turn_speed": 0.5, "step_height": 0.0,
    "gravity": -0.04, "reverse_throttle": -0.25, "weight_type": "tank",
    "engine_sound_volume": 3.0, "throttle_up_down": 0.5, "throttle_switch_hold_ticks": 60,
    "max_health": 2000.0, "armor_damage_factor": 0.5, "armor_min_damage": 5.0, "armor_max_damage": 200.0,
    "damage_factor": 0.2, "max_fuel": 5000.0, "fuel_consumption": 1.5, "inventory_size": 54,
    "submerged_damage_height": 3.0,
    "fuel_supply_range": 30.0, "ammo_supply_range": 30.0, "health_supply_range": 30.0,
    "wake_trail_spread_distance": 14.0, "wake_trail_duration_ticks": 400,
    "hud": "sample_hud", "engine_sound": "smp_engine_boat",
    "runways": [
        {"width": 15.0, "center_x": 0.0, "height_y": 4.6, "start_z": -30.0, "end_z": 30.0},
        {"width": 5.0, "center_x": -4.0, "height_y": 4.65, "start_z": -21.0, "end_z": -15.0,
         "hatch_offset_x": 0.0, "hatch_offset_y": -6.0, "hatch_offset_z": 0.0, "hatch_move_speed": 2.0},
        {"width": 3.0, "center_x": 0.0, "height_y": 2.0, "start_z": 34.0, "end_z": 38.0, "hatch_gated": True},
    ],
    "seats": [
        seat("captain", 6.5, 8.8, -3.0, True, camera_positions=[cam(6.5, 9.2, -2.6), cam(0.0, 30.0, -60.0, force_camera=True, fixed_pitch=25.0)]),
        seat("asw_operator", 4.5, 6.2, 23.0, camera_positions=[cam(4.5, 7.0, 22.0, force_camera=True)]),
        seat("ciws_gunner", -4.5, 6.0, 23.0),
    ],
    "weapons": [
        {"seat_index": 0, "weapon_name": "smp_carrier", "projectile_item": "minecraft:feather",
         "offsets": [{"x": 0.0, "y": 4.7, "z": -12.0, "mount_yaw": 0.0, "mount_pitch": 0.0}]},
        {"seat_index": 1, "weapon_name": "smp_asw", "projectile_item": "minecraft:iron_ingot", "pilot_usable": True, "turret_rotation_speed": 40.0,
         "offsets": [{"x": 4.5, "y": 6.2, "z": 25.6, "linked_part": "$asw_launcher"}],
         "aim_range": {"default_yaw": 0.0, "min_yaw": -150.0, "max_yaw": 150.0, "min_pitch": 5.0, "max_pitch": 60.0}},
        {"seat_index": 2, "weapon_name": "smp_ciws", "projectile_item": "minecraft:iron_nugget", "pilot_usable": True, "turret_rotation_speed": 120.0,
         "offsets": [{"x": -4.5, "y": 5.2, "z": 25.7, "linked_part": "$ciws_gun"}],
         "aim_range": {"default_yaw": 0.0, "min_yaw": -180.0, "max_yaw": 180.0, "min_pitch": -10.0, "max_pitch": 85.0}},
        {"seat_index": 0, "weapon_name": "smp_depth_charge", "projectile_item": "minecraft:iron_block",
         "offsets": [{"x": -4.0, "y": 5.5, "z": -28.5, "mount_yaw": 180.0, "linked_part": "$dc_1"},
                     {"x": -4.0, "y": 5.5, "z": -28.5, "mount_yaw": 180.0, "linked_part": "$dc_2"}]},
    ],
    "ammo_parts": [
        {"part": "$dc_1", "weapon_name": "smp_depth_charge", "slot_index": 1},
        {"part": "$dc_2", "weapon_name": "smp_depth_charge", "slot_index": 0},
    ],
    "spinning_parts": [
        {"part": "$radar", "pivot_x": 6.5, "pivot_y": 11.6, "pivot_z": -6.0, "axis_x": 0.0, "axis_y": 1.0, "axis_z": 0.0, "speed": 4.0},
    ],
    "toggle_parts": [
        {"part": "$hatch_elevator", "pivot_x": -4.0, "pivot_y": 4.55, "pivot_z": -18.0, "mode": "slide",
         "offset_x": 0.0, "offset_y": -6.0, "offset_z": 0.0, "trigger": "key", "speed": 2.0},
        {"part": "$canopy", "pivot_x": 6.5, "pivot_y": 8.6, "pivot_z": -2.2, "mode": "slide",
         "offset_x": 0.0, "offset_y": 0.0, "offset_z": 0.0, "trigger": "key", "speed": 1.0},
    ],
    "weapon_parts": [
        {"part": "$asw_launcher", "seat_index": 1, "weapon_name": "smp_asw", "pilot_fallback": True,
         "yaw_follow": True, "pitch_follow": True, "pivot_x": 4.5, "pivot_y": 5.2, "pivot_z": 24.0,
         "aim_range": {"min_yaw": -150.0, "max_yaw": 150.0, "min_pitch": 5.0, "max_pitch": 60.0}},
        {"part": "$ciws", "seat_index": 2, "weapon_name": "smp_ciws", "pilot_fallback": True,
         "yaw_follow": True, "pitch_follow": False, "pivot_x": -4.5, "pivot_y": 5.0, "pivot_z": 24.0},
        {"part": "$ciws_gun", "seat_index": 2, "weapon_name": "smp_ciws", "pilot_fallback": True,
         "yaw_follow": False, "pitch_follow": True, "spins_while_firing": True,
         "pivot_x": -4.5, "pivot_y": 5.2, "pivot_z": 24.4,
         "child_info": {"parent_yaw_follow": True, "parent_pitch_follow": False, "parent_pivot_x": -4.5, "parent_pivot_y": 5.0, "parent_pivot_z": 24.0}},
    ],
    "search_light_parts": [
        {"part": "$search_light", "pivot_x": 6.5, "pivot_y": 9.2, "pivot_z": -3.0,
         "start_color_argb": -1, "end_color_argb": 0, "length": 80.0, "end_radius": 12.0,
         "yaw": 0.0, "pitch": -10.0, "follow_mode": "FIXED"},
    ],
    "nav_light_parts": [
        {"part": "$nav_light_l", "pivot_x": 8.0, "pivot_y": 5.0, "pivot_z": 26.0, "color_argb": -65536},
        {"part": "$nav_light_r", "pivot_x": -8.0, "pivot_y": 5.0, "pivot_z": 26.0, "color_argb": -16711936},
    ],
    "spawn_item": {"display_name": "Sample Light Carrier", "tier": 5},
}

# ===================================================================
# Submarine
# ===================================================================
VEHICLES["sample_submarine"] = {
    "entity_type": "tudursvehiclemod:submarine",
    "model": mdl("sample_submarine"), "texture": tex("sample_submarine"),
    "display_name": "Sample Attack Submarine",
    "scale": 1.0, "width": 4.0, "height": 6.0,
    "max_speed": 0.8, "dive_max_speed": 0.45, "acceleration": 0.02, "turn_speed": 0.9,
    "gravity": -0.04, "reverse_throttle": -0.3, "weight_type": "tank",
    "engine_sound_volume": 1.5, "throttle_switch_hold_ticks": 40,
    "max_health": 600.0, "armor_damage_factor": 0.6, "damage_factor": 0.2,
    "max_fuel": 3000.0, "fuel_consumption": 0.8, "inventory_size": 18,
    "submerged_damage_height": 60.0,
    "hud": "sample_hud", "engine_sound": "smp_engine_boat",
    "passenger_display": {"hide_entity": True, "entity_width": 1.0, "entity_height": 1.0},
    "seats": [
        seat("captain", 0.0, 3.5, 2.0, True, camera_positions=[cam(0.0, 7.6, 1.5, force_camera=True), cam(0.0, 12.0, -30.0, force_camera=True, fixed_yaw=0.0, fixed_pitch=15.0)]),
        seat("crew", 0.0, 2.0, -2.0),
    ],
    "weapons": [
        {"seat_index": 0, "weapon_name": "smp_torpedo", "projectile_item": "minecraft:iron_ingot",
         "offsets": [{"x": -0.6, "y": 1.2, "z": 14.0, "linked_part": "$torpedo_l"}, {"x": 0.6, "y": 1.2, "z": 14.0, "linked_part": "$torpedo_r"}]},
    ],
    "ammo_parts": [
        {"part": "$torpedo_l", "weapon_name": "smp_torpedo", "slot_index": 1},
        {"part": "$torpedo_r", "weapon_name": "smp_torpedo", "slot_index": 0},
    ],
    "spinning_parts": [
        {"part": "$screw", "pivot_x": 0.0, "pivot_y": 1.5, "pivot_z": -14.3, "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0, "speed": 20.0},
    ],
    "toggle_parts": [
        {"part": "$hatch_top", "pivot_x": 0.0, "pivot_y": 5.1, "pivot_z": 2.9, "mode": "rotate", "axis_x": 1.0, "axis_y": 0.0, "axis_z": 0.0, "max_angle": -100.0, "trigger": "key", "speed": 6.0},
        {"part": "$periscope", "pivot_x": 0.0, "pivot_y": 5.8, "pivot_z": 1.5, "mode": "slide", "offset_x": 0.0, "offset_y": -1.4, "offset_z": 0.0, "trigger": "key", "speed": 3.0},
    ],
    "spawn_item": {"display_name": "Sample Attack Submarine", "tier": 4},
}

# ===================================================================
# Static emplacement
# ===================================================================
VEHICLES["sample_emplacement"] = {
    "entity_type": "tudursvehiclemod:static_emplacement",
    "model": mdl("sample_emplacement"), "texture": tex("sample_emplacement"),
    "display_name": "Sample Flak Emplacement",
    "scale": 1.0, "width": 3.0, "height": 3.0,
    "gravity": -0.04,
    "max_health": 300.0, "armor_damage_factor": 0.6, "armor_min_damage": 3.0,
    "damage_factor": 0.3, "max_fuel": 100.0, "fuel_consumption": 0.0, "inventory_size": 9,
    "regeneration": True, "default_freelook": True,
    "hud": "sample_hud",
    "seats": [
        seat("gunner", 0.0, 2.0, -0.4, True, camera_positions=[cam(0.0, 2.6, -0.2, force_camera=True), cam(0.0, 5.0, -6.0, force_camera=True)]),
        seat("loader", 0.9, 1.2, -1.2),
    ],
    "weapons": [
        {"seat_index": 0, "weapon_name": "smp_flak", "projectile_item": "minecraft:iron_nugget", "turret_rotation_speed": 50.0,
         "offsets": [{"x": -0.35, "y": 1.85, "z": 3.0, "linked_part": "$gun_cradle"}, {"x": 0.35, "y": 1.85, "z": 3.0, "linked_part": "$gun_cradle"}],
         "aim_range": {"default_yaw": 0.0, "min_yaw": -180.0, "max_yaw": 180.0, "min_pitch": -5.0, "max_pitch": 85.0}},
        {"seat_index": 0, "weapon_name": "smp_tv_missile", "projectile_item": "minecraft:iron_ingot",
         "offsets": [{"x": 0.0, "y": 2.4, "z": 0.0, "mount_pitch": 20.0}]},
        {"seat_index": 1, "weapon_name": "smp_cas", "projectile_item": "minecraft:paper", "pilot_usable": True,
         "offsets": [{"x": 1.2, "y": 3.0, "z": -1.2}]},
        {"seat_index": 0, "weapon_name": "smp_dummy", "projectile_item": "minecraft:stone",
         "offsets": [{"x": 0.0, "y": 1.0, "z": 0.0}]},
    ],
    "weapon_parts": [
        {"part": "$turret", "seat_index": 0, "weapon_name": "smp_flak", "yaw_follow": True, "pitch_follow": False,
         "pivot_x": 0.0, "pivot_y": 1.6, "pivot_z": 0.0},
        {"part": "$turret_shield", "seat_index": 0, "weapon_name": "smp_flak", "yaw_follow": True, "pitch_follow": False,
         "pivot_x": 0.0, "pivot_y": 1.6, "pivot_z": 0.0},
        {"part": "$gun_cradle", "seat_index": 0, "weapon_name": "smp_flak", "yaw_follow": False, "pitch_follow": True,
         "pivot_x": 0.0, "pivot_y": 1.8, "pivot_z": 0.6, "recoil_distance": 0.25,
         "child_info": {"parent_yaw_follow": True, "parent_pitch_follow": False, "parent_pivot_x": 0.0, "parent_pivot_y": 1.6, "parent_pivot_z": 0.0},
         "aim_range": {"min_yaw": -180.0, "max_yaw": 180.0, "min_pitch": -5.0, "max_pitch": 85.0}},
    ],
    "toggle_parts": [
        {"part": "$hatch_ammo", "pivot_x": -1.5, "pivot_y": 0.8, "pivot_z": -1.2, "mode": "rotate", "axis_x": 0.0, "axis_y": 0.0, "axis_z": 1.0, "max_angle": -110.0, "trigger": "key", "speed": 5.0},
    ],
    "search_light_parts": [
        {"part": "$search_light", "pivot_x": 1.0, "pivot_y": 1.1, "pivot_z": 1.2,
         "start_color_argb": -1, "end_color_argb": 0, "length": 60.0, "end_radius": 10.0,
         "yaw": 0.0, "pitch": 20.0, "follow_mode": "PILOT_VIEW"},
    ],
    "spawn_item": {"display_name": "Sample Flak Emplacement", "tier": 2},
}

def build(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for n, v in VEHICLES.items():
        with open(os.path.join(out_dir, n + ".json"), "w", encoding="utf-8") as f:
            json.dump(v, f, indent=2, ensure_ascii=False); f.write("\n")
    print("vehicles:", len(VEHICLES))

if __name__ == "__main__":
    import sys; build(sys.argv[1])
