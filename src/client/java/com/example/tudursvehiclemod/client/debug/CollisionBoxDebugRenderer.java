package com.example.tudursvehiclemod.client.debug;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/** A debug visualization of each vehicle's own
 * actual VANILLA collision box - i.e. exactly what this.getBoundingBox()
 * currently returns, the same box this.move() itself uses for ordinary
 * block collision (ground/seabed/wall contact, and so on) - as opposed to
 * HitDetectionMeshDebugRenderer's own visualization of the completely
 * separate, custom mesh-based DAMAGE hit-detection system (projectiles
 * hitting the vehicle). The two boxes/meshes serve entirely different
 * purposes and can legitimately differ (a vehicle can have a real-mesh
 * damage hitbox much larger or smaller than its own block-collision box,
 * or offset from it - see e.g. SubmarineEntity's own
 * calculateDefaultBoundingBox() override) - this class exists specifically
 * so that offset/positioning can be visually confirmed directly, rather
 * than only inferred from log values.
 *
 * Draws all 12 edges of the box as line gizmos (see client.hud.MortarMarkerRenderer's
 * own doc for why this API was chosen over particles - a real, non-debug-gated,
 * always-rendered official API, confirmed via direct bytecode inspection of the
 * actual game jar) - just red (0xFF3B30) instead of HitDetectionMeshDebugRenderer's
 * cyan/magenta, so the two debug overlays are never visually ambiguous with each
 * other when both happen to be on at once. */
public final class CollisionBoxDebugRenderer {

	private CollisionBoxDebugRenderer() {
	}

	/** Toggled from DebugMenuScreen's own button - see that class's own doc. */
	public static boolean enabled = false;

	/** A bright red, distinct from every other particle color this mod's own debug/gameplay effects already use (cyan/magenta hit-detection mesh, orange/tan explosion effects,..). Per Gizmo's own ARGB color convention (see MortarMarkerRenderer's own doc) - opaque. */
	private static final int DEBUG_BOX_COLOR = 0xFFFF3B30;
	/** Also visualizes each vehicle's own configured runway (see RunwayDefinition's own doc) alongside its collision box - a distinct bright green, so the two overlays are never confused for each other. */
	private static final int RUNWAY_COLOR = 0xFF34C759;
	/** Line thickness (screen pixels) for each edge gizmo. */
	private static final float LINE_WIDTH = 1.5f;

	/** Called once per client tick (see client.VehicleModClient's own hook for this) - a complete no-op whenever `enabled` is false, so this costs nothing at all for the overwhelming majority of players who never turn this on. Re-added every tick (cheap - just appends to a collection, no actual rendering work here) rather than throttled, since there's no expensive simulation behind this (unlike MortarMarkerRenderer's own raycast-stepping) - each vehicle's own current bounding box is already known outright. */
	public static void tick(MinecraftClient client) {
		if (!enabled || client.world == null) {
			return;
		}
		try (var scope = client.newGizmoScope()) {
			for (Entity entity : client.world.getEntities()) {
				if (!(entity instanceof AbstractVehicleEntity vehicle)) {
					continue;
				}
				tudursvehiclemod$drawBox(vehicle.getBoundingBox());
				vehicle.getDefinition().runways().forEach(runway -> tudursvehiclemod$drawRunway(vehicle, runway));
			}
		}
	}

	/** Draws the exact same rotating (ship-local, position/yaw-following) rectangle - at the runway's own configured surface height - that AbstractVehicleEntity's own tudursvehiclemod$updateCarrierRunwayPlatform() uses to place its own grid of Shulker platform entities, so the two can be visually cross-checked against each other directly. */
	private static void tudursvehiclemod$drawRunway(AbstractVehicleEntity vehicle, com.example.tudursvehiclemod.asset.RunwayDefinition runway) {
		double combinedYawRad = Math.toRadians(vehicle.getYaw());
		double forwardX = -Math.sin(combinedYawRad);
		double forwardZ = Math.cos(combinedYawRad);
		double rightX = forwardZ;
		double rightZ = -forwardX;
		double minZ = Math.min(runway.startZ(), runway.endZ());
		double maxZ = Math.max(runway.startZ(), runway.endZ());
		double halfWidth = runway.width() / 2.0;
		double surfaceWorldY = vehicle.getY() + runway.heightY();
		Vec3d corner1 = new Vec3d(vehicle.getX() + (runway.centerX() - halfWidth) * rightX + minZ * forwardX, surfaceWorldY, vehicle.getZ() + (runway.centerX() - halfWidth) * rightZ + minZ * forwardZ);
		Vec3d corner2 = new Vec3d(vehicle.getX() + (runway.centerX() + halfWidth) * rightX + minZ * forwardX, surfaceWorldY, vehicle.getZ() + (runway.centerX() + halfWidth) * rightZ + minZ * forwardZ);
		Vec3d corner3 = new Vec3d(vehicle.getX() + (runway.centerX() + halfWidth) * rightX + maxZ * forwardX, surfaceWorldY, vehicle.getZ() + (runway.centerX() + halfWidth) * rightZ + maxZ * forwardZ);
		Vec3d corner4 = new Vec3d(vehicle.getX() + (runway.centerX() - halfWidth) * rightX + maxZ * forwardX, surfaceWorldY, vehicle.getZ() + (runway.centerX() - halfWidth) * rightZ + maxZ * forwardZ);
		GizmoDrawing.line(corner1, corner2, RUNWAY_COLOR, LINE_WIDTH);
		GizmoDrawing.line(corner2, corner3, RUNWAY_COLOR, LINE_WIDTH);
		GizmoDrawing.line(corner3, corner4, RUNWAY_COLOR, LINE_WIDTH);
		GizmoDrawing.line(corner4, corner1, RUNWAY_COLOR, LINE_WIDTH);
		// A center cross, so the runway's own facing direction (which way is "start" vs "end") is visible at a glance, not just its own outline.
		Vec3d center = new Vec3d(vehicle.getX() + runway.centerX() * rightX + (minZ + maxZ) / 2.0 * forwardX, surfaceWorldY, vehicle.getZ() + runway.centerX() * rightZ + (minZ + maxZ) / 2.0 * forwardZ);
		Vec3d endMid = new Vec3d(vehicle.getX() + runway.centerX() * rightX + maxZ * forwardX, surfaceWorldY, vehicle.getZ() + runway.centerX() * rightZ + maxZ * forwardZ);
		GizmoDrawing.line(center, endMid, RUNWAY_COLOR, LINE_WIDTH);
	}

	private static void tudursvehiclemod$drawBox(Box box) {
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
			GizmoDrawing.line(corners[edge[0]], corners[edge[1]], DEBUG_BOX_COLOR, LINE_WIDTH);
		}
	}
}
