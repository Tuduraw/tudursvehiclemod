package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/** The shared, per-frame snapshot of every active search light cone, plus the query the lighting mixins ask for a given world position.
 *
 * WHY THIS SHAPE: the two mixins that need it (one for block/terrain lighting, one for entity lighting) both boil down to the same question - "how bright is this one point, according to this mod's own lights?" - so both call {@link #getLightLevelAt(Vec3d)} and take the brighter of that and vanilla's own value. Keeping the actual cone maths here rather than in either mixin means the two can never disagree about what a beam illuminates, and a mixin stays a thin adapter over it.
 *
 * WHY IT'S A CONE RATHER THAN A POINT LIGHT: this is the specific thing that made existing dynamic-lighting mods unsuitable as a base - they place spherical point lights, whereas a search light is directional by definition. {@link #getLightLevelAt(Vec3d)} therefore tests membership in an actual cone (angle from the beam axis, distance along it), not just proximity to the emitter.
 *
 * All state here is CLIENT-side and rebuilt each frame from already-synced vehicle state, so nothing new crosses the network for this. */
public final class SearchLightIllumination {

	private SearchLightIllumination() {}

	/** One active beam, reduced to just what {@link #getLightLevelAt(Vec3d)} needs - see this class's own doc. */
	private record ActiveCone(Vec3d origin, Vec3d direction, double length, double halfAngleCos, float brightness) {}


	/** Rebuilt wholesale every client tick - see tudursvehiclemod$refresh()'s own doc. Read from the render thread by the lighting mixins; never mutated in place, always replaced, so a mixin reading it mid-rebuild sees a complete older list rather than a half-built one. */
	private static volatile List<ActiveCone> activeCones = List.of();

	/** A search light's own origin is lit via the same real (chunk-rebuilt) lighting DynamicLight itself uses, regardless of which searchLightMode is otherwise active - the lit area is a single point, so the cost is negligible. Reused for the separate nav-light feature this file's own doc describes below: a plain spherical point light with none of ActiveCone's own directionality - just a position, a falloff radius, and a brightness. */
	private record ActivePointLight(Vec3d position, double radius, float brightness) {}

	/** Per the same requests as ActivePointLight's own doc - see tudursvehiclemod$refresh()'s own doc for how each of these is populated. */
	private static volatile List<ActivePointLight> activePointLights = List.of();


	/** Rebuilds the snapshot of every search light currently lit, from every vehicle the client has loaded. Called once per client tick rather than per lighting query, because a single frame asks getLightLevelAt() for a very large number of positions (every rendered block face and entity) and re-deriving each beam's own world aim inside that loop would be pointless repeated work.
	 *
	 * A vehicle whose own search light is off, or which defines none, contributes nothing - so a world with no lit search light in it leaves this empty and every query below returns immediately. */
	public static void tudursvehiclemod$refresh() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			activeCones = List.of();
			activePointLights = List.of();
			blockOverlays = java.util.Map.of();
			return;
		}
		List<ActiveCone> cones = null;
		List<ActivePointLight> pointLights = null;
		java.util.Map<AbstractVehicleEntity, List<BlockOverlay>> overlaysByVehicle = null;
		List<BlockOverlay> overlays = null;
		// Only actually built when searchLightMode is "dynamic_light" - see VehicleModConfig.searchLightMode's own doc for the now-unified single-axis design (this used to be a separate searchLightIlluminationMode field; "beam" mode never touches chunk rebuilding at all now).
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		boolean chunkRebuildMode = config != null && "dynamic_light".equals(config.searchLightMode);
		// The block-overlay collection below is by far this system's own largest allocator - at its own configured ceiling it churns through well over a hundred kilobytes of short-lived boxed entries per light per pass, and it was running at the full 20Hz tick rate. Recomputed on an interval instead; on skipped ticks the PREVIOUS result simply stays in place (blockOverlays is left untouched at the end), which is visually almost indistinguishable because the overlay follows terrain that isn't moving.
		//
		// Deliberately throttles ONLY the overlay collection. The cone/point-light snapshots below are rebuilt every tick regardless: they are a handful of small records, they drive the ACTUAL lighting the mixins query, and delaying them would make a moving beam's own illumination visibly lag its own drawn shape.
		int overlayInterval = Math.max(1, config == null ? 1 : config.searchLightBlockOverlayIntervalTicks);
		boolean recomputeOverlays = (tudursvehiclemod$overlayIntervalCounter++ % overlayInterval) == 0;
		java.util.Set<net.minecraft.util.math.ChunkPos> touchedChunks = chunkRebuildMode ? new java.util.HashSet<>() : null;
		// Its own single chunk is scheduled unconditionally, separately from touchedChunks above (which stays gated to "dynamic_light" for the cone's own much larger sweep). One extra chunk per lit search light, regardless of mode, is exactly the negligible cost the direct request anticipated.
		java.util.Set<net.minecraft.util.math.ChunkPos> originChunks = new java.util.HashSet<>();
		for (var entity : client.world.getEntities()) {
			// A destroyed vehicle's own entity lingers for a while before despawning (see AbstractVehicleEntity's own tudursvehiclemod$isDestroyed() doc), and this check previously asked only whether the light's own switch was on - which it still is, since nothing turns it off on destruction. Checked here rather than by forcing the switch off on death, so a vehicle that is somehow repaired/restored resumes with its own light in whatever state the player actually left it.
			if (!(entity instanceof AbstractVehicleEntity vehicle) || vehicle.tudursvehiclemod$isDestroyed()
					|| !vehicle.tudursvehiclemod$isSearchLightOn()) {
				continue;
			}
			var def = vehicle.getDefinition();
			for (var light : def.searchLightParts()) {
				float[] aim = vehicle.tudursvehiclemod$getSearchLightWorldAim(light, 1.0f);
				// Same +Z-forward convention the beam geometry itself uses (see VehicleEntityRenderer's own cone builder), converted here into a world-space direction vector.
				float yawRad = (float) Math.toRadians(aim[0]);
				float pitchRad = (float) Math.toRadians(aim[1]);
				double dirX = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
				double dirY = -MathHelper.sin(pitchRad);
				double dirZ = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
				Vec3d origin = new Vec3d(
						vehicle.getX() + light.pivotX(),
						vehicle.getY() + light.pivotY(),
						vehicle.getZ() + light.pivotZ());
				Vec3d direction = new Vec3d(dirX, dirY, dirZ).normalize();
				// endRadius over length is the cone's own tangent, so this is its own half-angle - taken as a cosine here because getLightLevelAt() compares against a dot product, which avoids an acos() per queried position.
				double halfAngle = Math.atan2(light.endRadius(), Math.max(0.0001f, light.length()));
				// The authored start colour's own alpha is what MC Heli uses for a beam's own intensity, so it drives brightness here too rather than inventing a separate knob - scaled by VehicleModConfig.searchLightBrightness (see that field's own doc). a typical authored alpha is 0x50, i.e. 0.31, so the raw value alone never came close to a full-strength light - which is exactly what that multiplier exists to correct.
				float brightness = ((light.startColorArgb() >>> 24) & 0xFF) / 255f * tudursvehiclemod$brightnessMultiplier();
				if (cones == null) {
					cones = new ArrayList<>();
				}
				// light.length() is an addon-authored value with no upper bound of its own (see SearchLightPart's own float field, unvalidated), so it is capped here ONCE and shared - both the actual illumination range (ActiveCone's own length, which getLightLevelAt() checks distance against) and the chunk-rebuild tracking below use this rather than each independently trusting the raw value.
				double effectiveLength = Math.min(SEARCH_LIGHT_MAX_OVERLAY_RADIUS_AT_RANGE, light.length());
				cones.add(new ActiveCone(origin, direction,
						effectiveLength, Math.cos(halfAngle), brightness));

				// See this method's own doc on originChunks for why this fires regardless of searchLightMode.
				if (pointLights == null) {
					pointLights = new ArrayList<>();
				}
				pointLights.add(new ActivePointLight(origin, SEARCH_LIGHT_ORIGIN_POINT_LIGHT_RADIUS, brightness));
				originChunks.add(new net.minecraft.util.math.ChunkPos(net.minecraft.util.math.BlockPos.ofFloored(origin)));

				// Tracks which chunks this ONE beam's own axis actually passes through, up to wherever it hit (or its own full length, if it hit nothing) - see tudursvehiclemod$applyChunkRebuild()'s own doc for how this feeds the actual rebuild requests. Steps along the ray rather than sweeping the whole cone volume, at a fraction of the cost - a beam lights along that same axis, so the chunks it crosses are the ones that matter regardless of the cone's own width.
				//
				// The raycast itself is now inside this check rather than run unconditionally: it used to also feed the ground disc, which has since been removed entirely (the cone display replaces it), so in "beam" mode there is nothing left that needs to know where the beam lands, and paying for a raycast per light per tick there would be pure waste.
				if (touchedChunks != null) {
					// Uses effectiveLength (capped, shared with ActiveCone's own construction above) rather than the raw light.length(), which would otherwise serve unbounded as BOTH this raycast's own target distance and the chunk-collection loop's own iteration bound below.
					Vec3d rayEnd = origin.add(direction.multiply(effectiveLength));
					RaycastContext context = new RaycastContext(origin, rayEnd,
							RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, net.minecraft.block.ShapeContext.absent());
					BlockHitResult hit = client.world.raycast(context);
					double reach = hit.getType() == HitResult.Type.BLOCK ? origin.distanceTo(hit.getPos()) : effectiveLength;
					for (double travelled = 0; travelled <= reach; travelled += 8.0) {
						Vec3d samplePos = origin.add(direction.multiply(travelled));
						touchedChunks.add(new net.minecraft.util.math.ChunkPos(net.minecraft.util.math.BlockPos.ofFloored(samplePos)));
					}
				}

				// Called once per light, right here, since origin/direction/halfAngle are already at hand.
				if (!chunkRebuildMode && recomputeOverlays) {
					double overlayRange = Math.min(tudursvehiclemod$blockOverlayRange(), effectiveLength);
					// Applied ON TOP of the shared brightness above, so the two effects can be balanced independently - see VehicleModConfig.searchLightBlockBrightness's own doc for why they need to be.
					float overlayBrightness = brightness * tudursvehiclemod$blockBrightnessMultiplier();
					overlays = tudursvehiclemod$collectGroundSurfaceOverlays(client, origin, direction, Math.cos(halfAngle),
							overlayRange, light.startColorArgb() & 0xFFFFFF, overlayBrightness, overlays);
				}
			}
			if (overlays != null && !overlays.isEmpty()) {
				if (overlaysByVehicle == null) {
					overlaysByVehicle = new java.util.HashMap<>();
				}
				overlaysByVehicle.put(vehicle, overlays);
				overlays = null;
			}
		}
		// Separate lighting feature - navigation/aviation lights ("航海灯や航空灯などを表現するための機能") - later corrected to require an on/off switch after all ("灯火は消せる必要があります。特にこのmodは軍用機や軍艦も含むので、それらが戦闘中にも常時光っているわけにはいきません..駐機中などの消灯させたいシチュエーションは存在します"): every vehicle's own nav lights contribute point lights, gated on tudursvehiclemod$areNavLightsOn() (one switch for the whole set, same shape as the search light's own), merged into the SAME activePointLights list and the SAME always-on real-lighting chunk set as the search light origin, since both are simple point lights sharing one lighting mechanism - see NavLightPart's own doc.
		for (var entity : client.world.getEntities()) {
			// Same destroyed-vehicle gate as the search light loop above, for the identical reason - see that loop's own note.
			if (!(entity instanceof AbstractVehicleEntity vehicle) || vehicle.tudursvehiclemod$isDestroyed()
					|| !vehicle.tudursvehiclemod$areNavLightsOn()) {
				continue;
			}
			for (var navLight : vehicle.getDefinition().navLightParts()) {
				Vec3d position = new Vec3d(
						vehicle.getX() + navLight.pivotX(),
						vehicle.getY() + navLight.pivotY(),
						vehicle.getZ() + navLight.pivotZ());
				float brightness = ((navLight.colorArgb() >>> 24) & 0xFF) / 255f * tudursvehiclemod$brightnessMultiplier();
				if (pointLights == null) {
					pointLights = new ArrayList<>();
				}
				pointLights.add(new ActivePointLight(position, SEARCH_LIGHT_ORIGIN_POINT_LIGHT_RADIUS, brightness));
				originChunks.add(new net.minecraft.util.math.ChunkPos(net.minecraft.util.math.BlockPos.ofFloored(position)));
			}
		}
		activeCones = cones == null ? List.of() : cones;
		activePointLights = pointLights == null ? List.of() : pointLights;
		// Only replaced on a recompute tick - see the recomputeOverlays doc above. On a skipped tick the previous map stays in place, which is the entire point: clearing it here would make the overlay flicker on and off at the interval rate instead of simply updating less often.
		if (recomputeOverlays) {
			blockOverlays = overlaysByVehicle == null ? java.util.Map.of() : overlaysByVehicle;
		} else if (!blockOverlays.isEmpty()) {
			// Per a report that instability returned after these changes: this map is keyed by the ENTITY, so simply leaving it untouched between recomputes would keep a strong reference to any vehicle removed in the meantime - holding a dead entity (and everything it in turn references) alive for up to the whole interval. Pruned every tick regardless of the throttle, which preserves what the throttle is actually for (not re-DERIVING the overlays) without that side effect.
			//
			// Copied rather than pruned in place: the map assigned above is a plain HashMap, but the empty fallback is Map.of(), which is immutable and would throw on any mutation - and the isEmpty() check above does not distinguish the two, since a HashMap can be empty too. Copying sidesteps that entirely, and only ever happens when there is actually something to prune.
			java.util.Map<AbstractVehicleEntity, List<BlockOverlay>> pruned = new java.util.HashMap<>(blockOverlays);
			if (pruned.keySet().removeIf(vehicle -> !vehicle.isAlive())) {
				blockOverlays = pruned;
			}
		}

		// Per the same direct request as originChunks' own doc: scheduled unconditionally, separately from the "dynamic_light"-gated touchedChunks/applyChunkRebuild pairing above - a search light's own origin point (and now nav lights too) always uses real lighting regardless of searchLightMode, so it needs its own always-on rebuild scheduling rather than piggybacking on the cone's own mode-gated one.
		tudursvehiclemod$applyOriginChunkRebuild(originChunks);

		if (chunkRebuildMode && config != null) {
			tudursvehiclemod$applyChunkRebuild(touchedChunks, config.searchLightChunkRebuildIntervalTicks);
		}
	}

	/** The actual "dynamic_light" mode work - see VehicleModConfig.searchLightMode's own "dynamic_light" doc for the overall rationale (this is the one mode that reproduces MC Heli's own real per-block lighting, at the acknowledged cost of continuous rebuild work).
	 *
	 * Throttled by tickCounter, matching MortarMarkerRenderer's own established pattern for the same reason: requesting a rebuild every single tick would keep the chunk builder permanently behind for any beam with real range.
	 *
	 * Rebuilds BOTH this tick's own touched chunks AND whatever the beam(s) touched LAST time but not this time - that second set is what actually lets an area a beam has swept past return to its own true vanilla darkness rather than staying stuck bright (the concern a direct report raised about the earlier, ruled-out always-on chunk-rebuild approach: "明るさや暗さが周囲と一致しないまま残存してしまう不具合"). Re-rebuilding a chunk the mixins no longer claim anything brighter in naturally recomputes it back down. */
	private static void tudursvehiclemod$applyChunkRebuild(java.util.Set<net.minecraft.util.math.ChunkPos> touchedChunks, int intervalTicks) {
		tudursvehiclemod$chunkRebuildTickCounter++;
		if (tudursvehiclemod$chunkRebuildTickCounter % Math.max(1, intervalTicks) != 0) {
			return;
		}
		java.util.Set<net.minecraft.util.math.ChunkPos> toRebuild = new java.util.HashSet<>(touchedChunks);
		toRebuild.addAll(tudursvehiclemod$previouslyTouchedChunks);
		var world = MinecraftClient.getInstance().world;
		if (world == null) {
			tudursvehiclemod$previouslyTouchedChunks = touchedChunks;
			return;
		}
		for (net.minecraft.util.math.ChunkPos chunkPos : toRebuild) {
			// A single representative column per chunk is enough: scheduleBlockRenders() marks the whole containing render-chunk column dirty, it does not rebuild only the one block given - see WorldRenderer's own doc for that method.
			world.scheduleBlockRenders(chunkPos.getStartX(), world.getBottomY(), chunkPos.getStartZ());
		}
		tudursvehiclemod$previouslyTouchedChunks = touchedChunks;
	}

	/** Counts ticks for the block-overlay recompute interval - see tudursvehiclemod$refresh()'s own recomputeOverlays doc. */
	private static int tudursvehiclemod$overlayIntervalCounter = 0;

	/** Per-call throttle for tudursvehiclemod$applyChunkRebuild() - see that method's own doc. */
	private static int tudursvehiclemod$chunkRebuildTickCounter = 0;

	/** A search light's own origin (and now nav lights too) always use DynamicLight's own real lighting regardless of searchLightMode, since the lit area is a single point: the same un-light-what-was-left-behind pattern tudursvehiclemod$applyChunkRebuild() uses, but UNCONDITIONAL - no "dynamic_light" gate, and no interval throttle, since a handful of single-point light sources is cheap enough to rebuild every tick without needing to be throttled the way the cone's own much larger sweep does. */
	private static void tudursvehiclemod$applyOriginChunkRebuild(java.util.Set<net.minecraft.util.math.ChunkPos> touchedChunks) {
		java.util.Set<net.minecraft.util.math.ChunkPos> toRebuild = new java.util.HashSet<>(touchedChunks);
		toRebuild.addAll(tudursvehiclemod$previouslyTouchedOriginChunks);
		var world = MinecraftClient.getInstance().world;
		if (world == null) {
			tudursvehiclemod$previouslyTouchedOriginChunks = touchedChunks;
			return;
		}
		for (net.minecraft.util.math.ChunkPos chunkPos : toRebuild) {
			world.scheduleBlockRenders(chunkPos.getStartX(), world.getBottomY(), chunkPos.getStartZ());
		}
		tudursvehiclemod$previouslyTouchedOriginChunks = touchedChunks;
	}

	/** What tudursvehiclemod$applyOriginChunkRebuild() touched on its own last actual run - see that method's own doc for why this is what lets a chunk an origin/nav point light has left un-light itself. Starts empty, matching tudursvehiclemod$previouslyTouchedChunks's own doc for the identical reason. */
	private static java.util.Set<net.minecraft.util.math.ChunkPos> tudursvehiclemod$previouslyTouchedOriginChunks = java.util.Set.of();

	/** A search light's own origin (and nav lights) are lit as simple point lights - see ActivePointLight's own doc: how far a point light's own falloff reaches. A single block's own worth of light spilling into its immediate surroundings is what "a small fixture on the vehicle's own body glows" actually looks like - wide enough to read as a genuine light source, narrow enough to stay a point rather than growing its own cone-sized footprint. */
	private static final double SEARCH_LIGHT_ORIGIN_POINT_LIGHT_RADIUS = 6.0;

	/** What tudursvehiclemod$applyChunkRebuild() touched on its own last actual run - see that method's own doc for why this is what lets a chunk a beam has left un-light itself. Starts empty, which is correct: there is nothing to un-light before any beam has ever run in chunk_rebuild mode. */
	private static java.util.Set<net.minecraft.util.math.ChunkPos> tudursvehiclemod$previouslyTouchedChunks = java.util.Set.of();


	/** One block to draw a coloured overlay onto, with the intensity already resolved for its own distance.
	 *
	 * WHY RE-RENDERING THE BLOCK BEATS A FLAT PATCH: the overlay is drawn from the block's OWN model, so a grass cross stays a cross, a fence stays a fence, and a slab stays a slab - there is no separate shape to mismatch. This is also what vanilla itself does for block-breaking cracks, so it is an established approach rather than a novel one. */
	public record BlockOverlay(net.minecraft.util.math.BlockPos pos, int colorArgb) {}

	/** This tick's own block overlays, keyed by the vehicle that owns them - same per-vehicle shape (and same reason) as the light splats above. */
	private static volatile java.util.Map<AbstractVehicleEntity, List<BlockOverlay>> blockOverlays = java.util.Map.of();

	/** This tick's own overlays for one vehicle. */
	public static List<BlockOverlay> getBlockOverlays(AbstractVehicleEntity vehicle) {
		return blockOverlays.getOrDefault(vehicle, List.of());
	}

	/** Collects overlays by RAYCASTING through the cone.
	 *
	 * WHY RAYCASTING IS THE RIGHT ANSWER HERE: "the surface the light can actually see, and nothing behind an obstruction" is the definition of a raycast. A heightmap answers a different question entirely - "the topmost block in this column" - which is why leaves overhead hid the ground beneath them and why a beam aimed into a cave found the surface above it instead. No heightmap variant can fix that, because the column-topmost assumption is the problem, not which blocks it counts.
	 *
	 * An earlier version of this feature did use raycasts and was replaced BECAUSE it left gaps - but the fault there was the ray COUNT (a sparse polar grid of ~121 rays, sized for placing a handful of flat patches), not the technique. Sampling densely enough that neighbouring rays land within about a block of each other at the far end closes those gaps, and the cost stays bounded because it scales with the configured range rather than the light's own full length.
	 *
	 * Duplicate hits are collapsed through a set, so a block struck by many rays is still overlaid exactly once - the alpha therefore comes purely from distance falloff, with no accidental density weighting. */
	private static List<BlockOverlay> tudursvehiclemod$collectGroundSurfaceOverlays(MinecraftClient client, Vec3d origin, Vec3d direction,
			double halfAngleCos, double range, int rgb, float brightness, List<BlockOverlay> into) {
		List<BlockOverlay> result = into;
		// Where THIS light's own contributions begin: with several search lights on one vehicle, `into` already holds the previous lights' overlays, and diffusing those again on every subsequent light would let diffused light spread another block outward each time. Diffusion below is restricted to the range starting here.
		int ownFirstIndex = result == null ? 0 : result.size();
		// The cone's own half-angle, recovered from the cosine the caller already computed, so the sampling disc below can be sized to the beam's actual spread at the range being covered.
		double halfAngle = Math.acos(Math.min(1.0, Math.max(-1.0, halfAngleCos)));
		// A defensive bound, NOT the fix for the hang that was being investigated when it was added - that turned out to be the block-overlay COUNT feeding per-frame renderDamage() calls (see the diffusion pass's own SEARCH_LIGHT_MAX_TOTAL_OVERLAYS doc), and capping this changed nothing about the reported symptom. It is kept because the underlying concern is real on its own terms: radiusAtRange is unbounded whenever a light's own endRadius/length ratio is extreme (verified numerically that length=1000, endRadius=100,000,000 drives it past 5,000,000 blocks), and it scales where each sample actually AIMS, so without this an individual ray could be cast toward an absurdly distant coordinate.
		double radiusAtRange = Math.min(SEARCH_LIGHT_MAX_OVERLAY_RADIUS_AT_RANGE, Math.tan(halfAngle) * range);
		// Sample count is now a FIXED number, entirely independent of the light's own shape. radiusAtRange only SCALES where each of that fixed number of samples lands - it can never make the loop itself run more times, which is what actually removes the vulnerability rather than merely capping its result after the fact.
		//
		// Vogel's method (the same even-disk-coverage technique used for sunflower seed heads and lens-free camera sampling): point i of N sits at radius R*sqrt(i/N) and angle i*goldenAngle. The sqrt keeps points evenly spaced by AREA (rather than bunching near the centre, which a linear radius step would do), and the golden angle (~137.5 degrees) is irrational relative to a full turn, so no two points ever fall on the same radial line no matter how many are generated - both properties standard, well-established reasons this exact method is the go-to for "evenly cover a disk with a fixed point count," not something invented for this case.
		int sampleCount = tudursvehiclemod$overlaySampleCount();
		java.util.Set<Long> seen = new java.util.HashSet<>(Math.max(16, sampleCount * 2));
		Vec3d upHint = Math.abs(direction.y) > 0.99 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
		Vec3d tangentU = direction.crossProduct(upHint).normalize();
		Vec3d tangentV = direction.crossProduct(tangentU).normalize();
		Vec3d rangeEnd = origin.add(direction.multiply(range));
		for (int i = 0; i < sampleCount; i++) {
			double sampleRadius = radiusAtRange * Math.sqrt(i / (double) sampleCount);
			double angle = i * SEARCH_LIGHT_GOLDEN_ANGLE_RADIANS;
			Vec3d target = rangeEnd
					.add(tangentU.multiply(Math.cos(angle) * sampleRadius))
					.add(tangentV.multiply(Math.sin(angle) * sampleRadius));
			// FluidHandling.ANY, so the ray is blocked by the FIRST fluid it meets, exactly like a real light hitting a water surface. It therefore never reaches the seabed - which is what "すべて水面への表示としてほしい" needed - and never reaches kelp or anything else beneath the surface either, which is what made the earlier walk-based detour to fix kelp unnecessary in the first place.
			RaycastContext context = new RaycastContext(origin, target,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, net.minecraft.block.ShapeContext.absent());
			BlockHitResult hit = client.world.raycast(context);
			if (hit.getType() != HitResult.Type.BLOCK) {
				continue;
			}
			net.minecraft.util.math.BlockPos pos = hit.getBlockPos();
			long posKey = pos.asLong();
			if (!seen.add(posKey)) {
				continue;
			}
			net.minecraft.block.BlockState hitState = client.world.getBlockState(pos);
			// A fluid-blocked hit is accepted directly, without the opaque/full-cube test below - water is never opaque nor a full cube by that test, which would otherwise exclude it. The renderer's own fluid branch (see VehicleEntityRenderer's own doc) already draws this at the fluid's own real surface height via FluidState.getHeight() rather than needing that precision here.
			boolean isWaterHit = !client.world.getFluidState(pos).isEmpty();
			if (!isWaterHit) {
				// Only a fully opaque complete cube is overlaid, since such a block has no transparent texels and no model gaps, making a solid overlay across its whole shape always correct. Asks about the PROPERTY rather than enumerating block types, so modded and future blocks are handled without a list to maintain.
				if (hitState.isAir() || !hitState.isOpaque() || !hitState.isFullCube(client.world, pos)) {
					continue;
				}
			}
			double distance = origin.distanceTo(hit.getPos());
			if (distance > range || distance < 1.0e-4) {
				continue;
			}
			// Fades to nothing at the configured range, so the effect ends gradually rather than at a visible hard edge - per the direct instruction that it weaken gradually.
			float falloff = (float) (1.0 - distance / range);
			int alpha = (int) Math.round(255 * Math.min(1.0f, brightness * falloff));
			if (alpha <= 0) {
				continue;
			}
			if (result == null) {
				// Sized from sampleCount rather than left at ArrayList's own default capacity, for the same reallocation reason "seen" above uses it - most rays that reach this point do end up producing an overlay, so sampleCount is a reasonable upper estimate rather than a wild guess.
				result = new ArrayList<>(sampleCount);
			}
			// Same shared-accumulator ceiling the diffusion pass applies - see its own note. This list is threaded through every search light on this vehicle via `into`, so direct hits alone could otherwise push it past the cap before diffusion even runs.
			if (result.size() >= SEARCH_LIGHT_MAX_TOTAL_OVERLAYS) {
				break;
			}
			result.add(new BlockOverlay(pos, (alpha << 24) | rgb));
		}
		// Gaps a sparse sample might miss between two actually-adjacent lit surfaces get bridged by diffusion's own reach, rather than requiring every visible surface to be hit by an explicit ray.
		return tudursvehiclemod$diffuseOverlays(client, result, ownFirstIndex, rgb);
	}

	/** Adds a dimmed overlay to blocks neighbouring an already-lit one.
	 *
	 * Works from a snapshot of the directly-lit set, so diffusion never feeds itself - light spreads exactly one block past what the beam actually reached, never propagating onward from its own output.
	 *
	 * A neighbour only qualifies if it has at least one face open to air, since an overlay on a fully buried block would never be seen anyway and costs a lookup to draw. */
	private static List<BlockOverlay> tudursvehiclemod$diffuseOverlays(MinecraftClient client, List<BlockOverlay> direct, int ownFirstIndex, int rgb) {
		if (direct == null || direct.size() <= ownFirstIndex) {
			return direct;
		}
		// Every position already overlaid - including earlier lights' - counts as "already lit" so diffusion never doubles up on one, but only THIS light's own entries (from ownFirstIndex on) are used as sources to spread FROM. See the call site's own note on why that distinction matters.
		//
		// Keyed by BlockPos.asLong() rather than by BlockPos itself: the position is only ever needed for identity here, and hashing a primitive long avoids hashing a full object several thousand times per tick per light. Both collections are pre-sized from the known input count so they do not rehash repeatedly while filling.
		//
		// Deliberately plain java.util rather than fastutil's own primitive collections: fastutil ships with the game, but it has no usage precedent anywhere in this project, and reaching for an unverified library here would repeat a mistake this project has already paid for repeatedly.
		// Per a memory-pressure review: entries that came from `direct` are stored NEGATED, so the final loop below can tell them apart from diffusion-added ones without a second set. Previously a whole separate HashSet<Long> was rebuilt from `direct` afterwards purely to answer "was this already present?" - information already known right here, at the moment each entry goes in. At this system's own configured ceiling that set was ~1200 boxed Long entries allocated and discarded every tick per light, for something derivable at zero cost.
		//
		// Negation is safe as the marker because every alpha stored here is strictly positive (a zero or negative spread alpha is rejected before ever reaching this map - see the spreadAlpha check in the fill below), so no genuine value can ever collide with the marker's own sign.
		java.util.Map<Long, Integer> best = new java.util.HashMap<>(Math.max(16, direct.size() * 4));
		for (BlockOverlay overlay : direct) {
			int existingAlpha = (overlay.colorArgb() >>> 24) & 0xFF;
			best.merge(overlay.pos().asLong(), -existingAlpha, Math::min);
		}
		// A proper multi-step BFS flood-fill outward from every directly-lit block, weakening by OVERLAY_DIFFUSION_STRENGTH with each additional step and blocked by opaque obstacles - the same shape vanilla's own block light itself takes (a level that decreases by a fixed amount per block and does not cross solid blocks), rather than the single fixed-radius neighbour spread this replaces.
		//
		// WHY THIS ALSO LETS THE FIXED SAMPLE COUNT BE LOWER: this is what the direct correction about ray count ("必要なレイ数はかなり少ないもの..拡散..障害の影響を受けつつ品質もある程度確保できる") relies on - gaps a sparse, fixed-count sample leaves between two actually-adjacent lit surfaces get bridged by diffusion's own multi-step reach, rather than requiring every visible surface to be hit by an explicit ray.
		java.util.ArrayDeque<Long> frontier = new java.util.ArrayDeque<>();
		for (int i = ownFirstIndex; i < direct.size(); i++) {
			frontier.add(direct.get(i).pos().asLong());
		}
		java.util.Set<Long> ineligible = null;
		net.minecraft.util.math.BlockPos.Mutable neighbour = new net.minecraft.util.math.BlockPos.Mutable();
		// This flood fill caps its own STEP DEPTH (SEARCH_LIGHT_DIFFUSION_MAX_STEPS) but never capped the resulting COUNT. Each step expands 6-ways from every newly-lit block, so the total grows geometrically - a few hundred directly-lit blocks readily becomes several THOUSAND after four steps. That matters enormously here rather than being merely wasteful, because every resulting overlay costs a full blockRenderManager.renderDamage() call in VehicleEntityRenderer's own overlay rendering EVERY FRAME, and that call regenerates the block's entire model each time. Approaching the light is exactly what pushes more nearby blocks into range, which matches the reported trigger precisely.
		outer:
		for (int step = 0; step < SEARCH_LIGHT_DIFFUSION_MAX_STEPS && !frontier.isEmpty(); step++) {
			java.util.ArrayDeque<Long> nextFrontier = new java.util.ArrayDeque<>();
			for (long currentKey : frontier) {
				// Absolute value: an entry that came from `direct` is stored negated as a marker (see best's own doc above), but its BRIGHTNESS is the magnitude either way.
				int currentAlpha = Math.abs(best.getOrDefault(currentKey, 0));
				int spreadAlpha = (int) Math.round(currentAlpha * OVERLAY_DIFFUSION_STRENGTH);
				if (spreadAlpha <= 0) {
					continue;
				}
				net.minecraft.util.math.BlockPos current = net.minecraft.util.math.BlockPos.fromLong(currentKey);
				for (net.minecraft.util.math.Direction side : ALL_DIRECTIONS) {
					if (best.size() >= SEARCH_LIGHT_MAX_TOTAL_OVERLAYS) {
						// Stops the whole fill outright rather than just this one step: once the ceiling is reached, every further neighbour would be rejected anyway, so continuing to walk the remaining frontier would be pure wasted work.
						break outer;
					}
					neighbour.set(current, side);
					long key = neighbour.asLong();
					// Checked BEFORE any world lookup: a neighbour already recorded at least this bright cannot be improved, and the block-state and exposure tests below are by far the expensive part of this loop. Several sources bordering one neighbour is the common case, not the exception - especially now that the flood fill can reach the same block from multiple directions across several steps. Compared by magnitude for the same reason currentAlpha above is - the sign is a marker, not part of the brightness.
					Integer existing = best.get(key);
					if (existing != null && Math.abs(existing) >= spreadAlpha) {
						continue;
					}
					// A neighbour already known to be ineligible (from an earlier, differently-bright source) stays ineligible - see this method's own doc on why brightness is irrelevant to this particular check.
					if (ineligible != null && ineligible.contains(key)) {
						continue;
					}
					net.minecraft.block.BlockState neighbourState = client.world.getBlockState(neighbour);
					if (neighbourState.isAir() || !neighbourState.isOpaque() || !neighbourState.isFullCube(client.world, neighbour)
							|| !tudursvehiclemod$hasExposedFace(client, neighbour)) {
						if (ineligible == null) {
							ineligible = new java.util.HashSet<>();
						}
						ineligible.add(key);
						continue;
					}
					// Preserves the negative marker when overwriting an entry that came from `direct` (see best's own doc above): this block is already in the result list, so brightening it must not turn it into a "diffusion-added" entry, which the final loop would then append a SECOND time. Verified against the original set-based implementation across randomized trials - dropping the sign here produced exactly that duplication.
					best.put(key, existing != null && existing < 0 ? -spreadAlpha : spreadAlpha);
					// A block that just received light for the first time (existing == null) becomes part of the NEXT step's own frontier, so the flood fill continues outward from it - but a block already lit at least this brightly by an earlier, closer source does not re-propagate from here, since doing so would let light effectively cross an obstacle diagonally by hopping around it through a brighter neighbour rather than travelling its own direct path.
					if (existing == null) {
						nextFrontier.add(key);
					}
				}
			}
			frontier = nextFrontier;
		}
		List<BlockOverlay> result = direct;
		for (var entry : best.entrySet()) {
			// Per a report that instability returned after the overlay changes: the cap on `best` above bounds ONE diffusion pass, but `result` is the SHARED accumulator threaded through every search light on this vehicle (see the `into` parameter's own use at the call site) - so a vehicle with several lights previously accumulated up to that ceiling once PER LIGHT, and each subsequent light then rebuilt its own `best` from that ever-larger list. Checked here against the shared list's own size so the total actually handed to VehicleRenderState (and re-processed every recompute) is bounded regardless of how many lights a vehicle carries.
			if (result.size() >= SEARCH_LIGHT_MAX_TOTAL_OVERLAYS) {
				break;
			}
			// A negative value marks an entry that came from `direct` and is therefore already in the result list - see best's own doc above for why the sign carries this instead of a separate set.
			if (entry.getValue() < 0) {
				continue;
			}
			result.add(new BlockOverlay(net.minecraft.util.math.BlockPos.fromLong(entry.getKey()), (entry.getValue() << 24) | rgb));
		}
		return result;
	}

	/** Whether this block has at least one face open to air - see tudursvehiclemod$diffuseOverlays()'s own doc for why that matters. Uses one reusable Mutable rather than allocating six BlockPos per call, since this runs once per candidate neighbour. */
	private static boolean tudursvehiclemod$hasExposedFace(MinecraftClient client, net.minecraft.util.math.BlockPos pos) {
		net.minecraft.util.math.BlockPos.Mutable scratch = new net.minecraft.util.math.BlockPos.Mutable();
		for (net.minecraft.util.math.Direction side : ALL_DIRECTIONS) {
			scratch.set(pos, side);
			if (client.world.getBlockState(scratch).isAir()) {
				return true;
			}
		}
		return false;
	}

	/** Direction.values() cached once. That method CLONES its backing array on every call to keep it immutable, and the two loops that use it sit inside per-hit loops - at a typical range that came to several thousand throwaway six-element arrays per tick per light, purely to iterate six constants. Read-only here, so sharing one array is safe. */
	private static final net.minecraft.util.math.Direction[] ALL_DIRECTIONS = net.minecraft.util.math.Direction.values();

	/** How much of a directly-lit block's own alpha carries onto its neighbours - see tudursvehiclemod$diffuseOverlays()'s own doc. Low enough that diffused light reads as a soft shadow edge rather than as more lit surface. */
	private static final double OVERLAY_DIFFUSION_STRENGTH = 0.35;

	/** Diffusion resembles vanilla's own light propagation - see tudursvehiclemod$diffuseOverlays()'s own doc for the full reasoning: how many blocks a flood fill is allowed to travel outward from a directly-lit surface. At OVERLAY_DIFFUSION_STRENGTH's own 0.35 per step, alpha falls under 1% of its own starting value by the 4th step regardless of this ceiling, so raising it further would cost real work for no visible gain - this exists mainly to guarantee the fill terminates promptly even in a dense, fully-enclosed stone structure where every candidate initially looks eligible. */
	private static final int SEARCH_LIGHT_DIFFUSION_MAX_STEPS = 4;

	/** Per the diagnostic result narrowing the reported hang to the block-overlay path - see the diffusion loop's own doc: a hard ceiling on how many overlays one light can produce in total, capping the flood fill's own geometric growth. This bounds the REAL cost, which is not the fill itself but the per-frame blockRenderManager.renderDamage() call each resulting overlay triggers in VehicleEntityRenderer's own overlay rendering - that call regenerates a block's entire model, so a few thousand of them per frame is exactly the kind of sustained render-thread load that matches the reported symptom. 1200 comfortably covers a normally-lit area at the default sample count while keeping that per-frame cost bounded. */
	private static final int SEARCH_LIGHT_MAX_TOTAL_OVERLAYS = 1200;

	/** Small number rather than derived from a light's own (potentially unbounded) geometry ("必要なレイ数はかなり少ないものと推定します"): the configured sample count, read fresh so a config change takes effect on the next tick. Floored at a small positive number so a misconfigured value can't reach zero and silently disable the block overlay entirely. */
	private static int tudursvehiclemod$overlaySampleCount() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		return config == null ? 200 : Math.max(8, config.searchLightBlockOverlaySampleCount);
	}

	/** The golden angle in radians (~137.5 degrees, 2*pi divided by the golden ratio squared) - see tudursvehiclemod$collectGroundSurfaceOverlays()'s own doc on Vogel's method for why this specific angle, rather than any other, is what gives even disk coverage with no radial repetition. */
	private static final double SEARCH_LIGHT_GOLDEN_ANGLE_RADIANS = Math.PI * (3.0 - Math.sqrt(5.0));

	/** A defensive ceiling on how far from the beam axis any sampling/tracking distance may reach - see radiusAtRange's own doc at its computation site for why this is a precaution rather than the fix for any observed problem. 2000 blocks comfortably exceeds the reference light's own endRadius (140) by more than an order of magnitude, so no reasonably-authored search light ever reaches it. Also reused as the ceiling on effectiveLength (a light's own authored length), which is unvalidated at the asset level for the same reason. */
	private static final double SEARCH_LIGHT_MAX_OVERLAY_RADIUS_AT_RANGE = 2000.0;

	/** The configured block-side brightness multiplier - see VehicleModConfig.searchLightBlockBrightness's own doc. Read fresh so config changes take effect on the next tick. */
	private static float tudursvehiclemod$blockBrightnessMultiplier() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		return config == null ? 1.0f : (float) Math.max(0.0, config.searchLightBlockBrightness);
	}

	/** The configured block-overlay range - see VehicleModConfig.searchLightBlockOverlayRange's own doc. Read fresh so config changes take effect on the next tick. */
	private static double tudursvehiclemod$blockOverlayRange() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		return config == null ? 50.0 : Math.max(0.0, config.searchLightBlockOverlayRange);
	}

	/** The block-light level (0-15) this mod's own search lights contribute at one world position, or 0 if none reach it.
	 *
	 * Called from BOTH lighting mixins for every position they light, so the early-out on an empty list matters - a world with no lit search light pays only that one volatile read. Within a cone the value falls off with distance along the beam, so an illuminated surface fades out towards the beam's own end rather than cutting off at a hard edge.
	 *
	 * Deliberately returns the MAXIMUM across overlapping cones rather than summing them: block light in Minecraft is a 0-15 level, not an additive intensity, and vanilla itself resolves two nearby light sources by taking the brighter. Summing would let two weak beams crossing produce a brighter spot than either could alone, which no vanilla light does. */
	public static int getLightLevelAt(Vec3d pos) {
		List<ActiveCone> cones = activeCones;
		List<ActivePointLight> pointLights = activePointLights;
		if (cones.isEmpty() && pointLights.isEmpty()) {
			return 0;
		}
		int best = 0;
		for (ActivePointLight pointLight : pointLights) {
			double distance = pos.distanceTo(pointLight.position());
			if (distance > pointLight.radius()) {
				continue;
			}
			double falloff = 1.0 - (distance / pointLight.radius());
			int level = MathHelper.clamp((int) Math.round(MAX_SEARCH_LIGHT_LEVEL * falloff * pointLight.brightness()), 0, MAX_SEARCH_LIGHT_LEVEL);
			best = Math.max(best, level);
		}
		for (ActiveCone cone : cones) {
			Vec3d toPos = pos.subtract(cone.origin());
			double distance = toPos.length();
			if (distance > cone.length() || distance < 1.0e-4) {
				continue;
			}
			// Inside the cone iff the angle between the beam axis and this position is within the half-angle - compared as cosines (see tudursvehiclemod$refresh()'s own note) so no trig is needed per position.
			double alignment = toPos.multiply(1.0 / distance).dotProduct(cone.direction());
			if (alignment < cone.halfAngleCos()) {
				continue;
			}
			double falloff = 1.0 - (distance / cone.length());
			// Clamped because the configured multiplier can push this past vanilla's own 0-15 block-light range, which the lightmap has no way to represent.
			int level = MathHelper.clamp((int) Math.round(MAX_SEARCH_LIGHT_LEVEL * falloff * cone.brightness()), 0, MAX_SEARCH_LIGHT_LEVEL);
			if (level > best) {
				best = level;
			}
		}
		return best;
	}

	/** Brightest block-light level a search light can produce, matching vanilla's own maximum (a glowstone block / the top of the 0-15 block light scale) so a fully lit surface reads exactly as brightly as one under any vanilla light. */
	private static final int MAX_SEARCH_LIGHT_LEVEL = 15;

	/** The configured brightness multiplier, read fresh so the config menu's own changes take effect without a restart. Null-guarded to the same default the config itself declares, for the case where this is somehow reached before the client config has finished loading. */
	private static float tudursvehiclemod$brightnessMultiplier() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		return config == null ? 3.0f : (float) Math.max(0.0, config.searchLightBrightness);
	}
}
