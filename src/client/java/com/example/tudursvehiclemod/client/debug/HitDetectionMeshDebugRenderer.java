package com.example.tudursvehiclemod.client.debug;

import com.example.tudursvehiclemod.asset.AmmoPart;
import com.example.tudursvehiclemod.asset.PartAnimation;
import com.example.tudursvehiclemod.asset.ServerObjModelHitboxes;
import com.example.tudursvehiclemod.asset.TogglePart;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.VtolRotorPart;
import com.example.tudursvehiclemod.asset.WeaponPart;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.VtolEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A debug visualization of the EXACT geometry
 * ServerObjModelHitboxes.isInside() actually tests hits against - i.e.
 * hits against - i.e. AFTER blade exclusion (see that class's own doc) -
 * rather than the model's own full, original geometry.
 *
 * This used to just draw ServerObjModelHitboxes.
 * getMesh()'s own flat, all-groups-combined mesh at each vehicle's own
 * REST pose, which meant independently-animated parts (PartAnimation/
 * TogglePart/WeaponPart) never visibly moved here even though the actual
 * hit detection itself DOES account for their own current transform (see
 * AbstractVehicleEntity's own tudursvehiclemod$isWorldPointNearVehicleSurface()
 * doc) - this now draws the SAME split: this vehicle's own static body
 * (every group EXCEPT the animated ones - getMeshExcludingGroups())
 * transformed by the vehicle's own overall position/rotation/scale ONLY,
 * PLUS each animated part's own triangles (getGroupedTriangles()),
 * transformed by that part's own CURRENT state (phase/progress/rotation)
 * FIRST and then by the vehicle's own overall transform - the exact same
 * two-stage approach client.render.VehicleEntityRenderer's own render()
 * uses to actually DRAW the model, just reproduced here for hit-detection
 * geometry instead of the model's own visual geometry.
 *
 * Deliberately reuses ServerObjModelHitboxes/AbstractVehicleEntity's own
 * public accessors directly from client-side code rather than requesting
 * anything from the server over the network - none of this is actually
 * server-specific despite the "Server" in ServerObjModelHitboxes's own
 * name (that prefix reflects this mod's own package layout, not an actual
 * server/client distinction), and the model files themselves are
 * identical mod resources on both sides anyway.
 *
 * Draws each retained triangle's own 3 edges as line gizmos (see
 * client.hud.MortarMarkerRenderer's own doc for why this API was chosen
 * over particles - a real, non-debug-gated, always-rendered official API,
 * confirmed via direct bytecode inspection of the actual game jar) rather
 * than dust particles - one line gizmo per edge is far lighter than a
 * whole run of particles spaced along it, and was hitting Minecraft's own
 * particle-count cap on large meshes (see MAX_TRIANGLES_PER_DRAW's own
 * doc) in a way a gizmo line simply doesn't. */
public final class HitDetectionMeshDebugRenderer {

	private HitDetectionMeshDebugRenderer() {
	}

	/** Toggled from DebugMenuScreen's own button - see that class's own doc. */
	public static boolean enabled = false;

	/** Only actually redraws every this-many client ticks - the per-triangle transform math (pivot rotations, animated-part state, etc.) below is the actual cost here, not the drawing itself, so this throttle still matters even with lightweight gizmo lines. */
	private static final int REDRAW_INTERVAL_TICKS = 5;
	/** A bright, distinct cyan (0x00E5FF) for this vehicle's own static body - deliberately different from any other particle color this mod already uses elsewhere (explosion flash/smoke/water splash), so this debug overlay is never ambiguous with an actual gameplay effect. Per Gizmo's own ARGB color convention (see MortarMarkerRenderer's own doc) - opaque. */
	private static final int DEBUG_MESH_COLOR = 0xFF00E5FF;
	/** A distinct magenta (0xFF00E5) for independently-animated parts specifically, so it's visually obvious at a glance which triangles are the static body vs. a part that moves on its own. Opaque ARGB, same as DEBUG_MESH_COLOR's own doc. */
	private static final int DEBUG_ANIMATED_PART_COLOR = 0xFFFF00E5;
	/** Line thickness (screen pixels) for each triangle-edge gizmo. */
	private static final float LINE_WIDTH = 1.0f;

	private static int tickCounter;
	/** The expensive per-triangle transform work still only reruns every Nth tick, but its RESULT (this cache of already-transformed line endpoints) is what actually gets re-added as gizmos every single tick in between, so the display never goes dark while waiting for the next recompute. */
	private record CachedLine(Vec3d start, Vec3d end, int color) {
	}
	private static final List<CachedLine> tudursvehiclemod$cachedLines = new java.util.ArrayList<>();

	/** Called once per client tick (see client.VehicleModClient's own hook for this) - a complete no-op whenever `enabled` is false, so this costs nothing at all for the overwhelming majority of players who never turn this on. */
	public static void tick(MinecraftClient client) {
		if (!enabled || client.world == null) {
			return;
		}
		tickCounter++;
		if (tickCounter % REDRAW_INTERVAL_TICKS == 0) {
			tudursvehiclemod$cachedLines.clear();
			for (Entity entity : client.world.getEntities()) {
				if (!(entity instanceof AbstractVehicleEntity vehicle)) {
					continue;
				}
				tudursvehiclemod$drawVehicle(vehicle, DEBUG_MESH_COLOR, DEBUG_ANIMATED_PART_COLOR);
			}
		}
		try (var scope = client.newGizmoScope()) {
			for (CachedLine line : tudursvehiclemod$cachedLines) {
				GizmoDrawing.line(line.start(), line.end(), line.color(), LINE_WIDTH);
			}
		}
	}

	private static void tudursvehiclemod$drawVehicle(AbstractVehicleEntity vehicle, int bodyColor, int animatedPartColor) {
		VehicleDefinition def = vehicle.getDefinition();
		Quaternionf bodyRotation = vehicle.tudursvehiclemod$getCurrentRotationForRendering();
		float scale = def.scale();
		double vehicleX = vehicle.getX();
		double vehicleY = vehicle.getY();
		double vehicleZ = vehicle.getZ();

		Set<String> animatedNames = AbstractVehicleEntity.tudursvehiclemod$getAnimatedPartNames(def);

		// Static body - every group except the independently-animated ones, transformed only by this vehicle's own overall position/rotation/scale.
		Optional<ServerObjModelHitboxes.Mesh> staticMeshOpt = ServerObjModelHitboxes.getMeshExcludingGroups(def.model(), animatedNames);
		staticMeshOpt.ifPresent(mesh -> {
			for (float[] tri : tudursvehiclemod$sampleTriangles(mesh.triangles())) {
				tudursvehiclemod$drawTriangle(tri, bodyRotation, scale, vehicleX, vehicleY, vehicleZ, bodyColor);
			}
		});

		if (animatedNames.isEmpty()) {
			return;
		}
		Optional<Map<String, List<float[]>>> groupedOpt = ServerObjModelHitboxes.getGroupedTriangles(def.model());
		if (groupedOpt.isEmpty()) {
			return;
		}
		Map<String, List<float[]>> grouped = groupedOpt.get();

		for (PartAnimation part : def.spinningParts()) {
			List<float[]> partTriangles = grouped.get(part.part());
			if (partTriangles == null || partTriangles.isEmpty()) {
				continue;
			}
			float phaseDeg = vehicle.getSpinningPartPhase(part.part());
			Quaternionf partRotation = tudursvehiclemod$safeAxisAngleDeg(part.axisX(), part.axisY(), part.axisZ(), phaseDeg);
			for (float[] tri : tudursvehiclemod$sampleTriangles(partTriangles)) {
				float[] transformed = tudursvehiclemod$applyPivotRotation(tri, part.pivotX(), part.pivotY(), part.pivotZ(), partRotation);
				// A blade that's a child of a
				// VtolRotorPart (see PartAnimation's own vtolRotorParent
				// doc) ADDITIONALLY inherits that parent nacelle's own
				// current tilt - applied AFTER its own spin here, since
				// this loop already computed transformed relative to the
				// part's own pivot; the parent's own pivot rotation
				// wraps that result exactly like WeaponPart's own
				// childInfo handling further below.
				if (part.vtolRotorParent().isPresent() && vehicle instanceof VtolEntity vtol) {
					for (VtolRotorPart parentRotor : def.vtolRotorParts()) {
						if (parentRotor.part().equals(part.vtolRotorParent().get())) {
							Quaternionf parentRotation = tudursvehiclemod$safeAxisAngleDeg(
									parentRotor.axisX(), parentRotor.axisY(), parentRotor.axisZ(),
									VtolRotorPart.resolveAngleDegrees(vtol.getVtolTiltProgress()));
							transformed = tudursvehiclemod$applyPivotRotation(transformed,
									parentRotor.pivotX(), parentRotor.pivotY(), parentRotor.pivotZ(), parentRotation);
							break;
						}
					}
				}
				tudursvehiclemod$drawTriangle(transformed, bodyRotation, scale, vehicleX, vehicleY, vehicleZ, animatedPartColor);
			}
		}

		for (TogglePart part : def.toggleParts()) {
			List<float[]> partTriangles = grouped.get(part.part());
			if (partTriangles == null || partTriangles.isEmpty()) {
				continue;
			}
			float progress = vehicle.getTogglePartProgress(part.part());
			if ("landing_gear_reversed".equals(part.trigger())) {
				progress = 1f - progress;
			}
			for (float[] tri : tudursvehiclemod$sampleTriangles(partTriangles)) {
				float[] transformed;
				if ("slide".equals(part.mode())) {
					transformed = tudursvehiclemod$applyOffset(tri, part.offsetX() * progress, part.offsetY() * progress, part.offsetZ() * progress);
				} else if ("slide_rotate".equals(part.mode())) {
					Quaternionf partRotation = tudursvehiclemod$safeAxisAngleDeg(part.axisX(), part.axisY(), part.axisZ(), part.maxAngle() * progress);
					float[] rotated = tudursvehiclemod$applyPivotRotation(tri, part.pivotX(), part.pivotY(), part.pivotZ(), partRotation);
					transformed = tudursvehiclemod$applyOffset(rotated, part.offsetX() * progress, part.offsetY() * progress, part.offsetZ() * progress);
				} else {
					Quaternionf partRotation = tudursvehiclemod$safeAxisAngleDeg(part.axisX(), part.axisY(), part.axisZ(), part.maxAngle() * progress);
					transformed = tudursvehiclemod$applyPivotRotation(tri, part.pivotX(), part.pivotY(), part.pivotZ(), partRotation);
				}
				tudursvehiclemod$drawTriangle(transformed, bodyRotation, scale, vehicleX, vehicleY, vehicleZ, animatedPartColor);
			}
		}

		for (AmmoPart part : def.ammoParts()) {
			if (!vehicle.tudursvehiclemod$isAmmoPartVisible(part)) {
				continue;
			}
			List<float[]> partTriangles = grouped.get(part.part());
			if (partTriangles == null || partTriangles.isEmpty()) {
				continue;
			}
			for (float[] tri : tudursvehiclemod$sampleTriangles(partTriangles)) {
				tudursvehiclemod$drawTriangle(tri, bodyRotation, scale, vehicleX, vehicleY, vehicleZ, animatedPartColor);
			}
		}

		for (WeaponPart part : def.weaponParts()) {
			List<float[]> partTriangles = grouped.get(part.part());
			if (partTriangles == null || partTriangles.isEmpty()) {
				continue;
			}
			Quaternionf ownRotation = vehicle.tudursvehiclemod$getWeaponPartOwnRotation(part, 1.0f);
			for (float[] tri : tudursvehiclemod$sampleTriangles(partTriangles)) {
				float[] transformed = tri;
				if (part.childInfo().isPresent()) {
					WeaponPart.ChildInfo childInfo = part.childInfo().get();
					Quaternionf parentRotation = vehicle.tudursvehiclemod$getWeaponPartParentRotation(part, 1.0f);
					transformed = tudursvehiclemod$applyPivotRotation(transformed,
							(float) childInfo.parentPivotX(), (float) childInfo.parentPivotY(), (float) childInfo.parentPivotZ(), parentRotation);
				}
				transformed = tudursvehiclemod$applyPivotRotation(transformed,
						(float) part.pivotX(), (float) part.pivotY(), (float) part.pivotZ(), ownRotation);
				tudursvehiclemod$drawTriangle(transformed, bodyRotation, scale, vehicleX, vehicleY, vehicleZ, animatedPartColor);
			}
		}

		// AddPartRotor - see VtolRotorPart's own doc. Only ever actually
		// populated for a VtolEntity (an empty list for every other
		// vehicle type), so tudursvehiclemod$getVtolTiltProgress() is
		// only ever read once THIS vehicle is confirmed to be one.
		if (vehicle instanceof VtolEntity vtol) {
			float tiltProgress = vtol.getVtolTiltProgress();
			for (VtolRotorPart part : def.vtolRotorParts()) {
				List<float[]> partTriangles = grouped.get(part.part());
				if (partTriangles == null || partTriangles.isEmpty()) {
					continue;
				}
				Quaternionf rotation = tudursvehiclemod$safeAxisAngleDeg(
						part.axisX(), part.axisY(), part.axisZ(),
						VtolRotorPart.resolveAngleDegrees(tiltProgress));
				for (float[] tri : tudursvehiclemod$sampleTriangles(partTriangles)) {
					float[] transformed = tudursvehiclemod$applyPivotRotation(tri,
							part.pivotX(), part.pivotY(), part.pivotZ(), rotation);
					tudursvehiclemod$drawTriangle(transformed, bodyRotation, scale, vehicleX, vehicleY, vehicleZ, animatedPartColor);
				}
			}
		}
	}

	/** A large vehicle's own main body mesh looked sparse/incomplete in this debug view (while smaller part groups rendered fully) - a re-investigation found this was a leftover artifact from BEFORE this renderer switched from dust particles to gizmo lines: the original 300-triangle cap existed specifically to stay under Minecraft's own particle-count cap, but gizmo lines don't hit that same limit at all (see this file's own doc above), so that original reason no longer applies - the cap was simply never raised back up after the switch, silently under-sampling any mesh above a few hundred triangles ever since. Raised substantially; still bounded (rather than removed outright) purely as a sane ceiling against something truly pathological (a many-tens-of-thousands-of-triangles model) rather than for the particle-cap reason this constant originally existed for. */
	private static final int MAX_TRIANGLES_PER_DRAW = 20000;

	private static List<float[]> tudursvehiclemod$sampleTriangles(List<float[]> triangles) {
		if (triangles.size() <= MAX_TRIANGLES_PER_DRAW) {
			return triangles;
		}
		List<float[]> sampled = new java.util.ArrayList<>(MAX_TRIANGLES_PER_DRAW);
		float stride = (float) triangles.size() / MAX_TRIANGLES_PER_DRAW;
		for (int i = 0; i < MAX_TRIANGLES_PER_DRAW; i++) {
			sampled.add(triangles.get((int) (i * stride)));
		}
		return sampled;
	}

	/** Same forward transform client.render.VehicleEntityRenderer's own render() applies to a part's own triangles: translate to pivot, rotate, translate back - i.e. rotation*(point - pivot) + pivot for each of the triangle's own 3 corners. */
	private static float[] tudursvehiclemod$applyPivotRotation(float[] tri, float pivotX, float pivotY, float pivotZ, Quaternionf rotation) {
		float[] result = new float[9];
		for (int i = 0; i < 3; i++) {
			Vector3f corner = new Vector3f(tri[i * 3] - pivotX, tri[i * 3 + 1] - pivotY, tri[i * 3 + 2] - pivotZ);
			rotation.transform(corner);
			result[i * 3] = corner.x + pivotX;
			result[i * 3 + 1] = corner.y + pivotY;
			result[i * 3 + 2] = corner.z + pivotZ;
		}
		return result;
	}

	private static float[] tudursvehiclemod$applyOffset(float[] tri, float offsetX, float offsetY, float offsetZ) {
		float[] result = new float[9];
		for (int i = 0; i < 3; i++) {
			result[i * 3] = tri[i * 3] + offsetX;
			result[i * 3 + 1] = tri[i * 3 + 1] + offsetY;
			result[i * 3 + 2] = tri[i * 3 + 2] + offsetZ;
		}
		return result;
	}

	/** Safe substitute for `new Quaternionf().fromAxisAngleDeg(x, y, z, angle)` - same guard as client.render.VehicleEntityRenderer's own tudursvehiclemod$safeAxisAngleDeg() (a zero-length axis vector produces a degenerate/NaN quaternion otherwise). */
	private static Quaternionf tudursvehiclemod$safeAxisAngleDeg(float x, float y, float z, float angleDeg) {
		if (x == 0f && y == 0f && z == 0f) {
			return new Quaternionf();
		}
		return new Quaternionf().fromAxisAngleDeg(x, y, z, angleDeg);
	}

	private static void tudursvehiclemod$drawTriangle(float[] tri, Quaternionf bodyRotation, float scale,
			double vehicleX, double vehicleY, double vehicleZ, int color) {
		Vec3d a = tudursvehiclemod$toWorld(tri[0], tri[1], tri[2], bodyRotation, scale, vehicleX, vehicleY, vehicleZ);
		Vec3d b = tudursvehiclemod$toWorld(tri[3], tri[4], tri[5], bodyRotation, scale, vehicleX, vehicleY, vehicleZ);
		Vec3d c = tudursvehiclemod$toWorld(tri[6], tri[7], tri[8], bodyRotation, scale, vehicleX, vehicleY, vehicleZ);
		tudursvehiclemod$cachedLines.add(new CachedLine(a, b, color));
		tudursvehiclemod$cachedLines.add(new CachedLine(b, c, color));
		tudursvehiclemod$cachedLines.add(new CachedLine(c, a, color));
	}

	/** Same scale-then-rotate-then-translate order as AbstractVehicleEntity's own removePassenger() seat-position transform (see that method's own doc), just forward (model-local to world) rather than that method's own case, which happens to also go local-to-world for a seat offset specifically. */
	private static Vec3d tudursvehiclemod$toWorld(float localX, float localY, float localZ,
			Quaternionf rotation, float scale, double vehicleX, double vehicleY, double vehicleZ) {
		Vector3f local = new Vector3f(localX * scale, localY * scale, localZ * scale);
		rotation.transform(local);
		return new Vec3d(vehicleX + local.x, vehicleY + local.y, vehicleZ + local.z);
	}
}
