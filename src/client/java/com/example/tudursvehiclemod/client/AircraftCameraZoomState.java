package com.example.tudursvehiclemod.client;

import net.minecraft.util.math.MathHelper;

/** Current third-person camera distance for aircraft specifically. */
public final class AircraftCameraZoomState {

	/** Matches CameraMixin's old fixed THIRD_PERSON_DISTANCE. */
	public static final float MIN_DISTANCE = 4f;
	public static final float STEP = 4f;

	public static float currentDistance = MIN_DISTANCE;

	private AircraftCameraZoomState() {
	}

	/** Positive steps zoom out (further away), negative steps zoom in. */
	public static void adjust(int steps, float maxDistance) {
		currentDistance = MathHelper.clamp(currentDistance + steps * STEP, MIN_DISTANCE, maxDistance);
	}
}
