package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.asset.ServerObjModelHitboxes;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Their hitboxes are deliberately larger than the actual visible mesh (used for block-collision purposes - see AbstractVehicleEntity's own doc on hitbox sizing), so vanilla's own outline/glow effect renders around that oversized, invisible geometry rather than the model actually drawn on screen, and ends up hidden behind it instead of visibly outlining it.
 *
 * <p>Draws a mesh-accurate gizmo-line outline instead, for any AbstractVehicleEntity currently flagged HIGHLIGHT_ACTIVE (see that field's own doc - set/cleared server-side, synced, by whichever targeting system - missile lock-on preview, Carrier lock-candidate preview, TargetingPod spotting - currently wants this vehicle highlighted).
 *
 * <p>a plain non-vehicle entity (player, mob) no longer uses setGlowing() at all either - see HIGHLIGHTED_ENTITY_IDS's own doc for the synced list (living on the TRACKING vehicle, not the target) this reads instead, drawing a simple bounding-box wireframe (tudursvehiclemod$drawBoundingBox()) rather than a mesh outline for these - no OBJ mesh exists for a vanilla entity, and its hitbox already roughly matches its visual model anyway, so a plain box is entirely sufficient.
 *
 * <p>Reuses client.debug.HitDetectionMeshDebugRenderer's own mesh-transform math (pivot rotation, scale, translation - see that class's own doc for the derivation), but simplified to this vehicle's own static, REST-POSE mesh only (ServerObjModelHitboxes.getMesh() - the full, unsplit mesh, not split by independently-animated groups) - unlike that debug tool, whose whole purpose is pixel-accurate hit-detection geometry (animated parts included), a highlight only needs to make it visually obvious WHICH vehicle is currently targeted, for which the rest-pose silhouette is entirely sufficient. */
public final class TargetHighlightRenderer {

	private TargetHighlightRenderer() {
	}

	/** A bright, distinct yellow-green (0xAAFF00), deliberately different from HitDetectionMeshDebugRenderer's own cyan/magenta (a genuinely separate, non-debug, always-on feature) and from MortarMarkerRenderer's own orange marker color. Opaque ARGB, same convention as those two files' own color constants. */
	private static final int HIGHLIGHT_COLOR = 0xFFAAFF00;
	/** Line thickness (screen pixels) for each triangle-edge gizmo - matches HitDetectionMeshDebugRenderer's own LINE_WIDTH. */
	private static final float LINE_WIDTH = 1.5f;
	/** Only actually recomputes the mesh transform every this-many client ticks - matches HitDetectionMeshDebugRenderer's own REDRAW_INTERVAL_TICKS/reasoning (the per-triangle transform math is the actual cost, not the drawing itself; gizmos still need to be re-added every tick regardless - see the cache below). */
	private static final int REDRAW_INTERVAL_TICKS = 5;

	private record CachedLine(Vec3d start, Vec3d end) {
	}

	private static int tickCounter;
	private static final List<CachedLine> tudursvehiclemod$cachedLines = new ArrayList<>();

	/** Called once per client tick (see client.VehicleModClient's own hook for this) - iterates every currently-loaded AbstractVehicleEntity twice: once as a potential HIGHLIGHT_ACTIVE TARGET (drawing its own mesh outline), once as a potential HIGHLIGHTED_ENTITY_IDS TRACKER (drawing a bounding-box wireframe for each non-vehicle entity it lists) - see HIGHLIGHT_ACTIVE/HIGHLIGHTED_ENTITY_IDS's own doc for why targets and trackers are handled so differently. Typically zero or a handful of entities involved in practice (this whole feature is about "the few things currently being targeted"), so this stays cheap even unthrottled at the iteration level; the actual per-triangle transform work is what's throttled below. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			return;
		}
		tickCounter++;
		if (tickCounter % REDRAW_INTERVAL_TICKS == 0) {
			tudursvehiclemod$cachedLines.clear();
			for (Entity entity : client.world.getEntities()) {
				if (entity instanceof AbstractVehicleEntity vehicle) {
					if (vehicle.tudursvehiclemod$isHighlighted()) {
						tudursvehiclemod$drawVehicle(vehicle);
					}
					for (Integer highlightedId : vehicle.tudursvehiclemod$getHighlightedEntityIds()) {
						Entity highlighted = client.world.getEntityById(highlightedId);
						if (highlighted != null) {
							tudursvehiclemod$drawBoundingBox(highlighted);
						}
					}
				}
			}
		}
		if (tudursvehiclemod$cachedLines.isEmpty()) {
			return;
		}
		try (var scope = client.newGizmoScope()) {
			for (CachedLine line : tudursvehiclemod$cachedLines) {
				GizmoDrawing.line(line.start(), line.end(), HIGHLIGHT_COLOR, LINE_WIDTH);
			}
		}
	}

	/** Non-vehicle entities (players, mobs - anything reached via HIGHLIGHTED_ENTITY_IDS rather than HIGHLIGHT_ACTIVE) also get a gizmo-line highlight instead of setGlowing(): a plain 12-edge box outline from the entity's own current bounding box - no OBJ mesh exists for these (they use vanilla's own entity models), and their hitbox already roughly matches their visual model anyway (unlike this mod's own vehicles - see this whole class's own doc), so a simple box is entirely sufficient here without needing anything mesh-accurate. */
	private static void tudursvehiclemod$drawBoundingBox(Entity entity) {
		net.minecraft.util.math.Box box = entity.getBoundingBox();
		Vec3d[] corners = {
				new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.minZ),
				new Vec3d(box.maxX, box.minY, box.maxZ), new Vec3d(box.minX, box.minY, box.maxZ),
				new Vec3d(box.minX, box.maxY, box.minZ), new Vec3d(box.maxX, box.maxY, box.minZ),
				new Vec3d(box.maxX, box.maxY, box.maxZ), new Vec3d(box.minX, box.maxY, box.maxZ),
		};
		// Bottom face, top face, then the 4 vertical edges connecting them.
		for (int i = 0; i < 4; i++) {
			tudursvehiclemod$cachedLines.add(new CachedLine(corners[i], corners[(i + 1) % 4]));
			tudursvehiclemod$cachedLines.add(new CachedLine(corners[4 + i], corners[4 + (i + 1) % 4]));
			tudursvehiclemod$cachedLines.add(new CachedLine(corners[i], corners[4 + i]));
		}
	}


	/** This renderer was very heavy - missing the triangle-count sampling client.debug.HitDetectionMeshDebugRenderer's own equivalent (MAX_TRIANGLES_PER_DRAW) already has. A clarification that a MUCH coarser outline than that debug tool's own is entirely acceptable here (this is a "which vehicle is this" highlight, not pixel-accurate hit-detection geometry) - deliberately far smaller than that tool's own 20000. */
	private static final int MAX_TRIANGLES_PER_DRAW = 400;

	private static void tudursvehiclemod$drawVehicle(AbstractVehicleEntity vehicle) {
		VehicleDefinition def = vehicle.getDefinition();
		Optional<ServerObjModelHitboxes.Mesh> meshOpt = ServerObjModelHitboxes.getMesh(def.model());
		if (meshOpt.isEmpty()) {
			return;
		}
		Quaternionf bodyRotation = vehicle.tudursvehiclemod$getCurrentRotationForRendering();
		float scale = def.scale();
		double vehicleX = vehicle.getX();
		double vehicleY = vehicle.getY();
		double vehicleZ = vehicle.getZ();
		for (float[] tri : tudursvehiclemod$sampleTriangles(meshOpt.get().triangles())) {
			Vec3d a = tudursvehiclemod$toWorld(tri[0], tri[1], tri[2], bodyRotation, scale, vehicleX, vehicleY, vehicleZ);
			Vec3d b = tudursvehiclemod$toWorld(tri[3], tri[4], tri[5], bodyRotation, scale, vehicleX, vehicleY, vehicleZ);
			Vec3d c = tudursvehiclemod$toWorld(tri[6], tri[7], tri[8], bodyRotation, scale, vehicleX, vehicleY, vehicleZ);
			tudursvehiclemod$cachedLines.add(new CachedLine(a, b));
			tudursvehiclemod$cachedLines.add(new CachedLine(b, c));
			tudursvehiclemod$cachedLines.add(new CachedLine(c, a));
		}
	}

	/** Matches client.debug.HitDetectionMeshDebugRenderer's own tudursvehiclemod$sampleTriangles() exactly (evenly-strided subsampling, not a truncated prefix, so the coarser outline still covers the whole model rather than just one end of its own triangle list) - see MAX_TRIANGLES_PER_DRAW's own doc for why the actual cap value differs. */
	private static List<float[]> tudursvehiclemod$sampleTriangles(List<float[]> triangles) {
		if (triangles.size() <= MAX_TRIANGLES_PER_DRAW) {
			return triangles;
		}
		List<float[]> sampled = new ArrayList<>(MAX_TRIANGLES_PER_DRAW);
		float stride = (float) triangles.size() / MAX_TRIANGLES_PER_DRAW;
		for (int i = 0; i < MAX_TRIANGLES_PER_DRAW; i++) {
			sampled.add(triangles.get((int) (i * stride)));
		}
		return sampled;
	}

	/** Same scale-then-rotate-then-translate order as HitDetectionMeshDebugRenderer's own identical helper. */
	private static Vec3d tudursvehiclemod$toWorld(float localX, float localY, float localZ,
			Quaternionf rotation, float scale, double vehicleX, double vehicleY, double vehicleZ) {
		Vector3f local = new Vector3f(localX * scale, localY * scale, localZ * scale);
		rotation.transform(local);
		return new Vec3d(vehicleX + local.x, vehicleY + local.y, vehicleZ + local.z);
	}
}
