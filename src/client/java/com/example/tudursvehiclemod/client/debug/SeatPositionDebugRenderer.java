package com.example.tudursvehiclemod.client.debug;

import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/** A way to visually verify seat placement (particularly after tudursvehiclemod$tryMountNearestSeat()'s own proximity-based mounting -), toggled from DebugMenuScreen's own button (same convention as CollisionBoxDebugRenderer/HitDetectionMeshDebugRenderer - a static enabled flag, no server round-trip at all, so this costs nothing for a player who never turns it on): draws a small cube (see CUBE_SIZE's own doc for why a point gizmo was replaced with this) centered on every seat's own current world position (AbstractVehicleEntity's own tudursvehiclemod$getSeatWorldPos()) for every loaded vehicle - green for an empty seat, red for an occupied one, so both a seat's own exact position AND its current occupancy are visible at a glance. Purely a visual aid; changes nothing about actual seat behavior. */
public final class SeatPositionDebugRenderer {

	private SeatPositionDebugRenderer() {
	}

	/** Toggled from DebugMenuScreen's own button - see that class's own doc. */
	public static boolean enabled = false;

	/** Distinct from CollisionBoxDebugRenderer's own red (used here for an OCCUPIED seat, so the two overlays stay visually consistent) and its own green runway color (used here for an EMPTY seat) - both opaque, per Gizmo's own ARGB color convention. */
	private static final int EMPTY_SEAT_COLOR = 0xFF34C759;
	private static final int OCCUPIED_SEAT_COLOR = 0xFFFF3B30;
	/** Line thickness (screen pixels) for each cube edge gizmo - same as CollisionBoxDebugRenderer's own LINE_WIDTH. */
	private static final float LINE_WIDTH = 1.5f;
	/** A 0.5-block cube CENTERED on the seat's own position instead (0.25 blocks each direction from center) - large enough to be clearly visible without being mistaken for the vehicle's own actual geometry. */
	private static final double CUBE_HALF_SIZE = 0.25;

	/** Called once per client tick (see client.VehicleModClient's own hook for this) - a complete no-op whenever `enabled` is false. */
	public static void tick(MinecraftClient client) {
		if (!enabled || client.world == null) {
			return;
		}
		try (var scope = client.newGizmoScope()) {
			for (Entity entity : client.world.getEntities()) {
				if (!(entity instanceof AbstractVehicleEntity vehicle)) {
					continue;
				}
				VehicleDefinition def = vehicle.getDefinition();
				for (int i = 0; i < def.seats().size(); i++) {
					Vec3d seatWorldPos = vehicle.tudursvehiclemod$getSeatWorldPos(i);
					int color = vehicle.tudursvehiclemod$getSeatOccupant(i) != null ? OCCUPIED_SEAT_COLOR : EMPTY_SEAT_COLOR;
					Box cube = new Box(
							seatWorldPos.x - CUBE_HALF_SIZE, seatWorldPos.y - CUBE_HALF_SIZE, seatWorldPos.z - CUBE_HALF_SIZE,
							seatWorldPos.x + CUBE_HALF_SIZE, seatWorldPos.y + CUBE_HALF_SIZE, seatWorldPos.z + CUBE_HALF_SIZE);
					tudursvehiclemod$drawBox(cube, color);
				}
			}
		}
	}

	/** Draws all 12 edges of the box as line gizmos - same approach as CollisionBoxDebugRenderer's own tudursvehiclemod$drawBox(), just with a caller-supplied color instead of a single fixed one (this class needs two different colors, unlike that one). */
	private static void tudursvehiclemod$drawBox(Box box, int color) {
		double minX = box.minX, minY = box.minY, minZ = box.minZ;
		double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;

		Vec3d[] corners = {
				new Vec3d(minX, minY, minZ), new Vec3d(maxX, minY, minZ),
				new Vec3d(maxX, minY, maxZ), new Vec3d(minX, minY, maxZ),
				new Vec3d(minX, maxY, minZ), new Vec3d(maxX, maxY, minZ),
				new Vec3d(maxX, maxY, maxZ), new Vec3d(minX, maxY, maxZ),
		};
		// Bottom face, top face, then the 4 vertical edges connecting them.
		int[][] edges = {
				{0, 1}, {1, 2}, {2, 3}, {3, 0},
				{4, 5}, {5, 6}, {6, 7}, {7, 4},
				{0, 4}, {1, 5}, {2, 6}, {3, 7},
		};
		for (int[] edge : edges) {
			GizmoDrawing.line(corners[edge[0]], corners[edge[1]], color, LINE_WIDTH);
		}
	}
}
