package com.example.tudursvehiclemod.client;

/** Accumulates raw (pre-vanilla-clamp) mouse yaw/pitch movement while piloting an aircraft, in "degrees" (using an approximate conversion. */
public final class AircraftOrientationInputState {

	public static float accumulatedYawDelta;
	public static float accumulatedPitchDelta;

	private AircraftOrientationInputState() {
	}
}
