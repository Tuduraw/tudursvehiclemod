package com.example.tudursvehiclemod.client.render;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.util.Identifier;

/** See VehicleProjectileRenderer's own doc for the two-step render-state pattern this fits into. */
public class VehicleProjectileRenderState extends EntityRenderState {
	public Identifier model;
	public Identifier texture;
	public float scale;
	public float yaw;
	public float pitch;
	public int light;
	/** Per Readme_Weapon.txt's own BulletColor/BulletColorInWater doc - packed ARGB, opaque white (no tint) by default. */
	public int tintColor = 0xFFFFFFFF;
}
