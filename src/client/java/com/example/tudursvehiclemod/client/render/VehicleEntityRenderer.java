package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/** One renderer class handles every vehicle: it never hardcodes a mesh. */
public class VehicleEntityRenderer extends EntityRenderer<AbstractVehicleEntity, VehicleRenderState> {

	public VehicleEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(AbstractVehicleEntity entity, net.minecraft.client.render.Frustum frustum,
			double x, double y, double z) {
		Boolean override = RenderDistanceHelper.tudursvehiclemod$overrideShouldRender(
				entity.getX(), entity.getY(), entity.getZ(), x, y, z);
		if (override != null) {
			return override;
		}
		return super.shouldRender(entity, frustum, x, y, z);
	}

	@Override
	public VehicleRenderState createRenderState() {
		return new VehicleRenderState();
	}

	@Override
	public void updateRenderState(AbstractVehicleEntity entity, VehicleRenderState state, float tickProgress) {
		super.updateRenderState(entity, state, tickProgress);

		VehicleDefinition def = entity.getDefinition();
		state.model = def.model();
		state.texture = def.texture();
		state.scale = def.scale();
		state.yaw = entity.getYaw(tickProgress);
		// A sinking wreck's attitude comes straight from its own closed form rather than from the entity's rotation, which vanilla's own client-side interpolation writes to after this mod does - see AbstractVehicleEntity.tudursvehiclemod$getSinkingRenderPitch()'s own doc. NaN means "not sinking", in which case the normal rotation path applies exactly as before.
		float sinkingPitch = entity.tudursvehiclemod$getSinkingRenderPitch(tickProgress);
		state.pitch = Float.isNaN(sinkingPitch) ? entity.getPitch(tickProgress) : sinkingPitch;
		state.light = this.getLight(entity, tickProgress);
		state.animationPhase = entity.getAnimationPhase(tickProgress);
		// CarEntity showed visual judder despite fine
		// actual FPS - see AbstractVehicleEntity's own getRenderYOffset()
		// doc. Defaults to 0 for every OTHER vehicle type (harmless no-op).
		state.renderYOffset = entity.getRenderYOffset(tickProgress);
		float sinkingRoll = entity.tudursvehiclemod$getSinkingRenderRoll(tickProgress);
		state.roll = Float.isNaN(sinkingRoll) ? entity.getRoll(tickProgress) : sinkingRoll;
		// Sinking's own attitude is a closed-form yaw/pitch/roll triple (see the sinkingPitch/
		// sinkingRoll doc above), never a quaternion of its own - composing state's own already-
		// resolved three angles here (rather than calling entity.tudursvehiclemod$getBodyOrientation(float),
		// which for AircraftEntity/VtolEntity would ignore the sinking override entirely and return
		// their own still-live flight orientation instead) is what keeps a sinking aircraft settling
		// flat exactly as sinkingPitch/sinkingRoll intend, instead of hanging in whatever attitude it
		// was destroyed at.
		if (!Float.isNaN(sinkingPitch) || !Float.isNaN(sinkingRoll)) {
			state.bodyOrientation = new org.joml.Quaternionf()
					.rotationY((float) Math.toRadians(-state.yaw))
					.rotateX((float) Math.toRadians(state.pitch))
					.rotateZ((float) Math.toRadians(state.roll));
		} else {
			state.bodyOrientation = entity.tudursvehiclemod$getBodyOrientation(tickProgress);
		}
		state.customPartTransforms = entity.tudursvehiclemod$getCustomPartTransforms(tickProgress);
		state.spinningParts = def.spinningParts();
		if (!def.spinningParts().isEmpty()) {
			java.util.Map<String, Float> phase = new java.util.HashMap<>();
			for (var part : def.spinningParts()) {
				phase.put(part.part(), entity.getSpinningPartPhase(part.part(), tickProgress));
			}
			state.spinningPartsPhase = phase;
		} else {
			state.spinningPartsPhase = java.util.Map.of();
		}
		state.toggleParts = def.toggleParts();
		if (!def.toggleParts().isEmpty()) {
			java.util.Map<String, Float> progress = new java.util.HashMap<>();
			for (var part : def.toggleParts()) {
				progress.put(part.part(), entity.getTogglePartProgress(part.part(), tickProgress));
			}
			state.togglePartProgress = progress;
		} else {
			state.togglePartProgress = java.util.Map.of();
		}
		state.weaponParts = def.weaponParts();
		if (!def.weaponParts().isEmpty()) {
			java.util.Map<String, org.joml.Quaternionf> ownRotation = new java.util.HashMap<>();
			java.util.Map<String, org.joml.Quaternionf> parentRotation = new java.util.HashMap<>();
			java.util.Map<String, Float> recoilOffset = new java.util.HashMap<>();
			for (var part : def.weaponParts()) {
				org.joml.Quaternionf partOwnRotation = entity.tudursvehiclemod$getWeaponPartOwnRotation(part, tickProgress);
				// AddPartRotWeapon's own gatling-style
				// spin - see WeaponPart's own spinsWhileFiring doc): the spin
				// rotation (around this part's own local Z / barrel-forward
				// axis) is composed BEFORE (i.e. applied first to the raw
				// geometry) the existing yaw/pitch tracking rotation, so the
				// combined effect is "spin in place around its own current
				// facing direction, wherever that facing direction is
				// currently tracking towards" rather than spinning around
				// some fixed world axis unrelated to the barrel's own
				// current orientation.
				if (part.spinsWhileFiring()) {
					float spinDegrees = entity.tudursvehiclemod$getWeaponPartSpinPhase(part.part(), tickProgress);
					org.joml.Quaternionf spin = new org.joml.Quaternionf().rotateZ((float) Math.toRadians(spinDegrees));
					partOwnRotation = new org.joml.Quaternionf(partOwnRotation).mul(spin);
				}
				ownRotation.put(part.part(), partOwnRotation);
				parentRotation.put(part.part(), entity.tudursvehiclemod$getWeaponPartParentRotation(part, tickProgress));
				recoilOffset.put(part.part(), entity.tudursvehiclemod$getWeaponPartRecoilOffset(part.part(), tickProgress));
			}
			state.weaponPartOwnRotation = ownRotation;
			state.weaponPartParentRotation = parentRotation;
			state.weaponPartRecoilOffset = recoilOffset;
		} else {
			state.weaponPartOwnRotation = java.util.Map.of();
			state.weaponPartParentRotation = java.util.Map.of();
			state.weaponPartRecoilOffset = java.util.Map.of();
		}
		state.destroyed = entity.tudursvehiclemod$isDestroyed();
		state.tintColorOverride = entity instanceof com.example.tudursvehiclemod.entity.ParachuteEntity parachute
				? parachute.tudursvehiclemod$getTintColor() : -1;

		state.wheelParts = def.wheelParts();
		if (!def.wheelParts().isEmpty()) {
			java.util.Map<String, org.joml.Quaternionf> wheelRotation = new java.util.HashMap<>();
			org.joml.Quaternionf spin = entity.tudursvehiclemod$getWheelPartSpinRotation(tickProgress);
			for (var wheelPart : def.wheelParts()) {
				// Per WheelPart's own doc: spin applied FIRST (inner), steering SECOND (outer) - composing steer.mul(spin) applies spin's own rotation to the raw geometry first, then re-orients the whole already-spinning result by the steering rotation.
				org.joml.Quaternionf steer = entity.tudursvehiclemod$getWheelPartSteerRotation(wheelPart);
				wheelRotation.put(wheelPart.part(), new org.joml.Quaternionf(steer).mul(spin));
			}
			state.wheelPartRotation = wheelRotation;
		} else {
			state.wheelPartRotation = java.util.Map.of();
		}

		state.trackRollerParts = def.trackRollerParts();
		if (!def.trackRollerParts().isEmpty()) {
			java.util.Map<String, org.joml.Quaternionf> trackRollerRotation = new java.util.HashMap<>();
			for (var rollerPart : def.trackRollerParts()) {
				trackRollerRotation.put(rollerPart.part(),
						entity.tudursvehiclemod$getTrackRollerSpinRotation(rollerPart, tickProgress));
			}
			state.trackRollerPartRotation = trackRollerRotation;
		} else {
			state.trackRollerPartRotation = java.util.Map.of();
		}

		state.steeringWheelParts = def.steeringWheelParts();
		if (!def.steeringWheelParts().isEmpty()) {
			java.util.Map<String, org.joml.Quaternionf> steeringWheelRotation = new java.util.HashMap<>();
			for (var steeringWheelPart : def.steeringWheelParts()) {
				steeringWheelRotation.put(steeringWheelPart.part(), entity.tudursvehiclemod$getSteeringWheelRotation(steeringWheelPart, tickProgress));
			}
			state.steeringWheelPartRotation = steeringWheelRotation;
		} else {
			state.steeringWheelPartRotation = java.util.Map.of();
		}

		state.crawlerTracks = def.crawlerTracks();
		if (!def.crawlerTracks().isEmpty()) {
			// The map is REUSED rather than rebuilt. This runs once per visible vehicle per frame, and its keys are the definition's own track part names, which never change at runtime - so the only thing that actually differs frame to frame is the float each one maps to. Allocating a fresh HashMap (plus a boxed Float per entry) every frame for that was pure garbage. Cleared and refilled in place instead; the state object is per-entity and only ever touched from the render thread, so reuse is safe.
			java.util.Map<String, Float> crawlerPhase = state.crawlerTrackPhase instanceof java.util.HashMap<String, Float> reusable
					? reusable
					: new java.util.HashMap<>();
			crawlerPhase.clear();
			for (var track : def.crawlerTracks()) {
				crawlerPhase.put(track.part(), entity.tudursvehiclemod$getCrawlerTrackPhase(track.part(), tickProgress));
			}
			state.crawlerTrackPhase = crawlerPhase;
		} else {
			state.crawlerTrackPhase = java.util.Map.of();
		}

		// AddPartRotor - see VtolRotorPart's own doc. Empty/0 (a no-op
		// tilt) for every OTHER vehicle type, since getVtolTiltProgress()
		// only exists on VtolEntity itself.
		state.vtolRotorParts = def.vtolRotorParts();
		state.vtolTiltProgress = entity instanceof com.example.tudursvehiclemod.entity.VtolEntity vtol
				? vtol.getVtolTiltProgress() : 0f;

		if (!def.ammoParts().isEmpty()) {
			state.ammoParts = def.ammoParts();
			// Reused for the same reason as crawlerTrackPhase above - fixed keys, only the boolean changes per frame.
			java.util.Map<String, Boolean> visibility = state.ammoPartVisibility instanceof java.util.HashMap<String, Boolean> reusable
					? reusable
					: new java.util.HashMap<>();
			visibility.clear();
			for (var part : def.ammoParts()) {
				visibility.put(part.part(), entity.tudursvehiclemod$isAmmoPartVisible(part));
			}
			state.ammoPartVisibility = visibility;
		} else {
			state.ammoParts = java.util.List.of();
			state.ammoPartVisibility = java.util.Map.of();
		}

		// Reverted back to per-entity copying. This method's own tickProgress parameter confirms it runs once per RENDERED FRAME, not once per GAME TICK - the underlying wakeBowHistory/wakeSternHistory/wakeSideHistory deques themselves are only ever actually modified from this vehicle's own tick logic, never mid-frame - so re-copying them fresh on every extra frame would be wasted allocation. Skipped whenever this SAME entity's own current world tick matches the tick its own history was already copied for.
		long currentWorldTick = entity.getEntityWorld().getTime();
		if (state.wakeHistoryCopiedForTick != currentWorldTick) {
			// Copy, not a live reference, so render() works from a stable list.
			// Routed through tudursvehiclemod$snapshotWakeHistory() rather than a bare List.copyOf(). For an EMPTY source that returns the shared, immutable List.of() singleton instead of allocating anything at all - which both avoids a pointless per-tick allocation for the (common) no-wake case AND, more importantly, immediately drops this state's own reference to whatever previous, potentially large snapshot it was still holding. Without that, a vehicle whose trail had just fully aged out would keep its last non-empty snapshot alive here until the next tick happened to overwrite it.
			state.wakeBowHistory = tudursvehiclemod$snapshotWakeHistory(entity.tudursvehiclemod$getWakeBowHistory());
			state.wakeSternHistory = tudursvehiclemod$snapshotWakeHistory(entity.tudursvehiclemod$getWakeSternHistory());
			state.wakeSideHistory = tudursvehiclemod$snapshotWakeHistory(entity.tudursvehiclemod$getWakeSideHistory());
			state.wakeHistoryCopiedForTick = currentWorldTick;
		}
		state.wakeTrailDurationTicks = def.wakeTrailDurationTicks();
		state.currentWorldTick = currentWorldTick;
		state.wakeReversing = entity.tudursvehiclemod$isWakeReversing();
		state.wakeCurrentYaw = entity.getYaw();

		// Resolved once per frame here rather than inside render() itself, matching how weaponPartOwnRotation etc. are already handled - render() should only ever read already-computed state, not derive anything from the entity live.
		// The DRAWN cone is a separate path from SearchLightIllumination's own lighting, so it needs the same gate - see that class's own note for why this checks isDestroyed() rather than forcing the switch off on death.
		if (def.searchLightParts().isEmpty() || entity.tudursvehiclemod$isDestroyed() || !entity.tudursvehiclemod$isSearchLightOn()) {
			state.activeSearchLightBeams = java.util.List.of();
		} else {
			java.util.List<VehicleRenderState.LightBeam> beams = new java.util.ArrayList<>(def.searchLightParts().size());
			for (var light : def.searchLightParts()) {
				float[] worldAim = entity.tudursvehiclemod$getSearchLightWorldAim(light, tickProgress);
				beams.add(new VehicleRenderState.LightBeam(
						light.pivotX(), light.pivotY(), light.pivotZ(),
						worldAim[0], worldAim[1],
						light.startColorArgb(), light.endColorArgb(),
						light.length(), light.endRadius()));
			}
			state.activeSearchLightBeams = beams;
			// Now fully superseded by block overlays (confirmed unrelated to the still-needed beam cone guide).
			state.activeBlockOverlays = com.example.tudursvehiclemod.client.render.SearchLightIllumination.getBlockOverlays(entity);
		}
	}

	@Override
	public void render(VehicleRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
		matrices.push();
		// Drawn before vehicle rotation is applied - wake history is world-space, not local geometry.
		if (!state.wakeBowHistory.isEmpty() || !state.wakeSternHistory.isEmpty() || !state.wakeSideHistory.isEmpty()) {
			tudursvehiclemod$renderWakeRibbon(state, matrices, queue);
		}
		if (!state.activeSearchLightBeams.isEmpty()) {
			tudursvehiclemod$renderSearchLightBeams(state, matrices, queue);
		}
		if (!state.activeBlockOverlays.isEmpty()) {
			tudursvehiclemod$renderBlockOverlays(state, matrices, queue);
		}
		// CarEntity's own visual judder - see
		// AbstractVehicleEntity's own getRenderYOffset() doc. Applied in
		// WORLD space (before any of this vehicle's own rotation below),
		// since "up" should mean the same thing regardless of the
		// vehicle's own current yaw/pitch/roll.
		matrices.translate(0.0, state.renderYOffset, 0.0);
		// Model space forward is +Z (MC Heli's own convention, and what this project's OBJ pipeline
		// assumes). One combined rotation rather than three separate yaw/pitch/roll multiplies -
		// see VehicleRenderState's own bodyOrientation doc, and AbstractVehicleEntity's own
		// tudursvehiclemod$getBodyOrientation(float) doc, for why: for Car/Ship/Submarine/Helicopter
		// this is built from exactly the same three angles the old code multiplied in directly
		// (identical rendered result), but for AircraftEntity/VtolEntity it is that class's own
		// already-interpolated native quaternion, with no yaw/pitch/roll decomposition - and
		// therefore no gimbal-lock-adjacent jitter - in between.
		matrices.multiply(state.bodyOrientation);
		matrices.scale(state.scale, state.scale, state.scale);

		ObjModelLoader.get(state.model).ifPresent(model -> {
			// Uses a dithered-discard cutout layer instead of a true alpha-blended translucent one: approximates partial alpha via per-pixel ordered dithering, so every fragment is either fully drawn or fully discarded, letting this use a normal, depth-WRITING pipeline (correct occlusion between vehicles/parts, exactly like any opaque surface) while still avoiding permanently occluding water/other translucent content behind it (discarded fragments never write depth at all, same as any other cutout hole).
			RenderLayer layer = com.example.tudursvehiclemod.client.render.DitherCutoutLayers.entityDitherCutout(state.texture);
			int light = state.light;
			// Charred-black tint for a destroyed vehicle (see VehicleRenderState.destroyed's own doc).
			int tintColor = state.destroyed ? 0xFF2A2A2A : state.tintColorOverride != -1 ? state.tintColorOverride : 0xFFFFFFFF;

			// Named OBJ parts (from "o"/"g" lines). The
			// display itself is heavy on high-poly models: this Set is
			// derived purely from the definition's own part lists, which
			// never change at runtime, so it's built once per definition
			// and reused rather than reallocated every single frame (it
			// also has to be a stable, equal-valued key for ObjModel's own
			// excludingCache to actually hit - see that field's own doc).
			java.util.Set<String> animatedGroups = tudursvehiclemod$getAnimatedGroupNames(state);
			// Custom-transform groups (see AbstractVehicleEntity's own
			// tudursvehiclemod$getCustomPartTransforms() doc) are excluded from the static mesh too.
			// Merged into a fresh set rather than the per-definition cache, since they come from the
			// entity at render time; an equal-valued set still hits ObjModel's own excludingCache.
			if (!state.customPartTransforms.isEmpty()) {
				java.util.Set<String> merged = new java.util.HashSet<>(animatedGroups);
				merged.addAll(state.customPartTransforms.keySet());
				animatedGroups = merged;
			}

			renderTriangles(queue, matrices, layer, model.getTrianglesExcluding(animatedGroups), light, tintColor);

			for (var part : state.spinningParts) {
				ObjModel.Triangles partTriangles = model.getGroup(part.part());
				if (partTriangles.isEmpty()) {
					continue;
				}
				matrices.push();
				// A blade that's a child of a
				// VtolRotorPart (see PartAnimation's own vtolRotorParent
				// doc) ADDITIONALLY inherits that parent nacelle's own
				// current tilt - applied FIRST (outer transform), same
				// parent-then-own order as WeaponPart's own childInfo
				// handling further below.
				if (part.vtolRotorParent().isPresent()) {
					for (var parentRotor : state.vtolRotorParts) {
						if (parentRotor.part().equals(part.vtolRotorParent().get())) {
							matrices.translate(parentRotor.pivotX(), parentRotor.pivotY(), parentRotor.pivotZ());
							matrices.multiply(tudursvehiclemod$safeAxisAngleDeg(
									parentRotor.axisX(), parentRotor.axisY(), parentRotor.axisZ(),
									com.example.tudursvehiclemod.asset.VtolRotorPart.resolveAngleDegrees(state.vtolTiltProgress)));
							matrices.translate(-parentRotor.pivotX(), -parentRotor.pivotY(), -parentRotor.pivotZ());
							break;
						}
					}
				}
				matrices.translate(part.pivotX(), part.pivotY(), part.pivotZ());
				matrices.multiply(tudursvehiclemod$safeAxisAngleDeg(
						part.axisX(), part.axisY(), part.axisZ(),
						state.spinningPartsPhase.getOrDefault(part.part(), 0f)));
				matrices.translate(-part.pivotX(), -part.pivotY(), -part.pivotZ());
				renderTriangles(queue, matrices, layer, partTriangles, light, tintColor);
				matrices.pop();
			}

			// Hatches/canopies/landing gear.
			for (var part : state.toggleParts) {
				ObjModel.Triangles partTriangles = model.getGroup(part.part());
				if (partTriangles.isEmpty()) {
					continue;
				}
				float progress = state.togglePartProgress.getOrDefault(part.part(), 0f);
				if ("landing_gear_reversed".equals(part.trigger())) {
					progress = 1f - progress;
				}
				matrices.push();
				if ("slide".equals(part.mode())) {
					matrices.translate(part.offsetX() * progress, part.offsetY() * progress, part.offsetZ() * progress);
				} else if ("slide_rotate".equals(part.mode())) {
					// Rotate around the pivot FIRST, then slide.
					matrices.translate(part.pivotX(), part.pivotY(), part.pivotZ());
					matrices.multiply(tudursvehiclemod$safeAxisAngleDeg(
							part.axisX(), part.axisY(), part.axisZ(), part.maxAngle() * progress));
					matrices.translate(-part.pivotX(), -part.pivotY(), -part.pivotZ());
					matrices.translate(part.offsetX() * progress, part.offsetY() * progress, part.offsetZ() * progress);
				} else {
					matrices.translate(part.pivotX(), part.pivotY(), part.pivotZ());
					matrices.multiply(tudursvehiclemod$safeAxisAngleDeg(
							part.axisX(), part.axisY(), part.axisZ(), part.maxAngle() * progress));
					matrices.translate(-part.pivotX(), -part.pivotY(), -part.pivotZ());
				}
				renderTriangles(queue, matrices, layer, partTriangles, light, tintColor);
				matrices.pop();
			}

			// Weapon-tracking parts (turret barrels, gun mantlets,..). A
			// child part (see WeaponPart's own doc) gets a TWO-STAGE
			// transform: first, it unconditionally inherits whatever
			// rotation its own parent undergoes, around the PARENT's own
			// pivot (so it correctly orbits along with the parent's own
			// rotation rather than just spinning in place around its own,
			// different, pivot) - then its own yaw_follow/pitch_follow-
			// gated rotation applies ON TOP of that, around its OWN pivot,
			// representing whatever additional independent motion it has
			// beyond just following the parent. A non-child part only ever
			// gets the second stage.
			for (var part : state.weaponParts) {
				ObjModel.Triangles partTriangles = model.getGroup(part.part());
				if (partTriangles.isEmpty()) {
					continue;
				}
				matrices.push();
				if (part.childInfo().isPresent()) {
					com.example.tudursvehiclemod.asset.WeaponPart.ChildInfo childInfo = part.childInfo().get();
					org.joml.Quaternionf parentRotation = state.weaponPartParentRotation.getOrDefault(part.part(), new org.joml.Quaternionf());
					matrices.translate(childInfo.parentPivotX(), childInfo.parentPivotY(), childInfo.parentPivotZ());
					matrices.multiply(parentRotation);
					matrices.translate(-childInfo.parentPivotX(), -childInfo.parentPivotY(), -childInfo.parentPivotZ());
				}
				org.joml.Quaternionf ownRotation = state.weaponPartOwnRotation.getOrDefault(part.part(), new org.joml.Quaternionf());
				matrices.translate(part.pivotX(), part.pivotY(), part.pivotZ());
				matrices.multiply(ownRotation);
				// AddPart/AddChildPart's own part_type=2
				// - "駐退距離"/recoil distance): translates BACKWARD along
				// this part's own current local Z axis (the same barrel-
				// forward axis the spin above rotates around), so this
				// happens in the part's own POST-rotation frame - the part
				// kicks back along whatever direction it's CURRENTLY aimed
				// at, the same way a real gun barrel's own recoil tracks
				// its current elevation/traverse rather than some fixed
				// model-space direction.
				float recoilOffset = state.weaponPartRecoilOffset.getOrDefault(part.part(), 0f);
				if (recoilOffset != 0f) {
					matrices.translate(0.0, 0.0, -recoilOffset);
				}
				matrices.translate(-part.pivotX(), -part.pivotY(), -part.pivotZ());
				renderTriangles(queue, matrices, layer, partTriangles, light, tintColor);
				matrices.pop();
			}

			// Custom-transform groups: each matrix already holds the part's whole chain (see
			// AbstractVehicleEntity's own tudursvehiclemod$getCustomPartTransforms() doc), so it is
			// applied as-is. The normal matrix gets the matching inverse-transpose so lighting stays
			// correct under the part's own rotation.
			for (var entry : state.customPartTransforms.entrySet()) {
				ObjModel.Triangles partTriangles = model.getGroup(entry.getKey());
				if (partTriangles.isEmpty()) {
					continue;
				}
				matrices.push();
				matrices.peek().getPositionMatrix().mul(entry.getValue());
				matrices.peek().getNormalMatrix().mul(entry.getValue().normal(new org.joml.Matrix3f()));
				renderTriangles(queue, matrices, layer, partTriangles, light, tintColor);
				matrices.pop();
			}

			// AddPartWheel (see WheelPart's own doc) - spin+steer, composed at its own pivot.
			for (var wheelPart : state.wheelParts) {
				ObjModel.Triangles partTriangles = model.getGroup(wheelPart.part());
				if (partTriangles.isEmpty()) {
					continue;
				}
				matrices.push();
				org.joml.Quaternionf rotation = state.wheelPartRotation.getOrDefault(wheelPart.part(), new org.joml.Quaternionf());
				matrices.translate(wheelPart.pivotX(), wheelPart.pivotY(), wheelPart.pivotZ());
				matrices.multiply(rotation);
				matrices.translate(-wheelPart.pivotX(), -wheelPart.pivotY(), -wheelPart.pivotZ());
				renderTriangles(queue, matrices, layer, partTriangles, light, tintColor);
				matrices.pop();
			}

			// AddTrackRoller (see TrackRollerPart's own doc) - belt-synced spin only, at its own pivot.
			for (var rollerPart : state.trackRollerParts) {
				ObjModel.Triangles partTriangles = model.getGroup(rollerPart.part());
				if (partTriangles.isEmpty()) {
					continue;
				}
				matrices.push();
				org.joml.Quaternionf rotation = state.trackRollerPartRotation.getOrDefault(rollerPart.part(), new org.joml.Quaternionf());
				matrices.translate(rollerPart.pivotX(), rollerPart.pivotY(), rollerPart.pivotZ());
				matrices.multiply(rotation);
				matrices.translate(-rollerPart.pivotX(), -rollerPart.pivotY(), -rollerPart.pivotZ());
				renderTriangles(queue, matrices, layer, partTriangles, light, tintColor);
				matrices.pop();
			}

			// AddPartSteeringWheel (see SteeringWheelPart's own doc) - steer only, at its own pivot.
			for (var steeringWheelPart : state.steeringWheelParts) {
				ObjModel.Triangles partTriangles = model.getGroup(steeringWheelPart.part());
				if (partTriangles.isEmpty()) {
					continue;
				}
				matrices.push();
				org.joml.Quaternionf rotation = state.steeringWheelPartRotation.getOrDefault(steeringWheelPart.part(), new org.joml.Quaternionf());
				matrices.translate(steeringWheelPart.pivotX(), steeringWheelPart.pivotY(), steeringWheelPart.pivotZ());
				matrices.multiply(rotation);
				matrices.translate(-steeringWheelPart.pivotX(), -steeringWheelPart.pivotY(), -steeringWheelPart.pivotZ());
				renderTriangles(queue, matrices, layer, partTriangles, light, tintColor);
				matrices.pop();
			}

			// AddCrawlerTrack (see CrawlerTrackPart's own doc) - a single link model repeated around a closed-loop path.
			for (var track : state.crawlerTracks) {
				ObjModel.Triangles partTriangles = model.getGroup(track.part());
				if (partTriangles.isEmpty()) {
					continue;
				}
				float phase = state.crawlerTrackPhase.getOrDefault(track.part(), 0f);
				for (var placement : tudursvehiclemod$getCrawlerTrackPlacements(track, phase)) {
					matrices.push();
					// track.x() is ONLY ever used to
					// decide which side this track is on for its own
					// speed/direction (see AbstractVehicleEntity's own
					// tudursvehiclemod$getCrawlerTrackSpeed() doc) - the
					// model's own geometry ALREADY sits at the correct X
					// position (that's how the original model was
					// authored), so translating by track.x() here on TOP
					// of that double-applies the offset. Only Y/Z (the
					// path position itself) is ever actually translated.
					matrices.translate(0.0, placement.y(), placement.z());
					// A 90-degree orientation
					// mismatch: CRAWLER_TRACK_ANGLE_CORRECTION_DEGREES
					// corrects for this link model's own default "long
					// axis" not actually being +Y (this project's own
					// original assumption) - see that constant's own doc.
					float correctedAngle = placement.angleDegrees() + CRAWLER_TRACK_ANGLE_CORRECTION_DEGREES;
					matrices.multiply(new org.joml.Quaternionf().rotateX((float) Math.toRadians(correctedAngle)));
					// A true mirror (negative scale
					// on the model's own local X - the track's own width/
					// thickness direction, where a real track link's
					// "outward tread face" vs "inward smooth face"
					// distinction actually lives) rather than a rotation-
					// based visual approximation, for reliability - a
					// scale-based mirror genuinely reverses chirality
					// regardless of whether the model happens to be
					// symmetric around any particular rotation axis. See
					// tudursvehiclemod$getCrawlerTrackPlacements()'s own
					// matching, non-negated track.flip() check for the
					// path's own winding order, which flip=true reverses
					// TOGETHER with this.
					if (track.flip()) {
						matrices.scale(-1f, 1f, 1f);
					}
					renderTriangles(queue, matrices, layer, partTriangles, light, tintColor);
					matrices.pop();
				}
			}

			// AddPartRotor (see VtolRotorPart's own doc) - tilts between
			// this vehicle's own two flight modes, driven by
			// VtolRotorPart.resolveAngleDegrees(state.vtolTiltProgress).
			for (var rotor : state.vtolRotorParts) {
				ObjModel.Triangles partTriangles = model.getGroup(rotor.part());
				if (partTriangles.isEmpty()) {
					continue;
				}
				matrices.push();
				matrices.translate(rotor.pivotX(), rotor.pivotY(), rotor.pivotZ());
				matrices.multiply(tudursvehiclemod$safeAxisAngleDeg(
						rotor.axisX(), rotor.axisY(), rotor.axisZ(),
						com.example.tudursvehiclemod.asset.VtolRotorPart.resolveAngleDegrees(state.vtolTiltProgress)));
				matrices.translate(-rotor.pivotX(), -rotor.pivotY(), -rotor.pivotZ());
				renderTriangles(queue, matrices, layer, partTriangles, light, tintColor);
				matrices.pop();
			}

			// Ammo-count-gated ordnance (missiles/bombs that disappear as they're expended).
			// Ammo parts carry no transform of their own - only a visibility check - so they all share one matrix and can go through a SINGLE draw command instead of one each. Collected and submitted right here, at the exact point they were previously drawn one by one, so the order everything reaches the buffer in is completely unchanged - which matters because the translucent mode resolves overlap by draw order rather than by depth.
			List<ObjModel.Triangles> ammoBatch = new java.util.ArrayList<>();
			for (var part : state.ammoParts) {
				if (!state.ammoPartVisibility.getOrDefault(part.part(), true)) {
					continue;
				}
				ObjModel.Triangles partTriangles = model.getGroup(part.part());
				if (partTriangles.isEmpty()) {
					continue;
				}
				ammoBatch.add(partTriangles);
			}
			if (!ammoBatch.isEmpty()) {
				renderTriangles(queue, matrices, layer, ammoBatch, light, tintColor, true);
			}
		});

		matrices.pop();
		super.render(state, matrices, queue, cameraState);
	}

	/** Safe substitute for `new Quaternionf().fromAxisAngleDeg(x, y, z, angle)`. */
	private static org.joml.Quaternionf tudursvehiclemod$safeAxisAngleDeg(float x, float y, float z, float angleDeg) {
		if (x == 0f && y == 0f && z == 0f) {
			return new org.joml.Quaternionf();
		}
		return new org.joml.Quaternionf().fromAxisAngleDeg(x, y, z, angleDeg);
	}

	/** See the call site's own comment - built once per model, reused every frame afterwards. Keyed by model Identifier, since every vehicle sharing a model necessarily shares the same part lists too. Plain HashMap is safe: only ever touched from the render thread. */
	private static final java.util.Map<net.minecraft.util.Identifier, java.util.Set<String>> ANIMATED_GROUPS_CACHE = new java.util.HashMap<>();

	private static java.util.Set<String> tudursvehiclemod$getAnimatedGroupNames(VehicleRenderState state) {
		return ANIMATED_GROUPS_CACHE.computeIfAbsent(state.model, unused -> {
			java.util.Set<String> names = new java.util.HashSet<>();
			for (var part : state.spinningParts) {
				names.add(part.part());
			}
			for (var part : state.toggleParts) {
				names.add(part.part());
			}
			for (var part : state.weaponParts) {
				names.add(part.part());
			}
			for (var part : state.ammoParts) {
				names.add(part.part());
			}
			for (var part : state.wheelParts) {
				names.add(part.part());
			}
			for (var part : state.steeringWheelParts) {
				names.add(part.part());
			}
			for (var part : state.crawlerTracks) {
				names.add(part.part());
			}
			for (var part : state.trackRollerParts) {
				names.add(part.part());
			}
			for (var part : state.vtolRotorParts) {
				names.add(part.part());
			}
			return names;
		});
	}

	/** One placement (position + rotation) of a CrawlerTrackPart's own single link model, along its own path. */
	/** The crawler track link model's own
	 * orientation was 90 degrees off: this project's own original
	 * assumption (see tudursvehiclemod$getCrawlerTrackPlacements()'s own
	 * doc) was that a track-link model's own default "long axis" points
	 * along local +Y - apparently this specific model (and, likely, MC
	 * Heli-sourced crawler track links in general) actually default to a
	 * DIFFERENT axis instead, 90 degrees away from that assumption
	 * (-90f). A SEPARATE follow-up report found the link still displaying
	 * upside-down (vertically flipped) after that first fix - an
	 * additional +180f on top of the same -90f correction fixes that. If
	 * this ever needs to go the OTHER way for some other model, adjust
	 * the sign/value here. */
	private static final float CRAWLER_TRACK_ANGLE_CORRECTION_DEGREES = 90f;

	private record CrawlerTrackPlacement(double y, double z, float angleDegrees) {
	}

	/** Computes where copies of
	 * this track's own single link model should actually sit, spaced
	 * linkSpacing apart around its own closed-loop path (the path's own
	 * LAST point connects back to its FIRST, forming a loop - a track
	 * has no actual "start"/"end", it's an endless belt). "flip"
	 * reverses the path's own winding order first (per Readme_Aircraft.txt's
	 * own doc: "履帯の回転ポイントを..時計回りに設定した時はfalse、その逆の
	 * 場合はtrue" - i.e. whichever winding the path was actually authored
	 * in), so a positive phase consistently means "the belt is moving in
	 * this vehicle's own forward direction" regardless of which way the
	 * artist happened to list the points in.
	 *
	 * Each placement's own rotation aligns the link model's own default
	 * "up" (local +Y) direction with the path's own local tangent at that
	 * point (computed from the CURRENT segment's own start->end
	 * direction) - a reasonable default for how a track-link model is
	 * normally authored; if a specific model's own links end up rotated
	 * 90/180 degrees from where they should be, that's this convention
	 * not matching that particular model's own default orientation, not
	 * a placement-math bug. */
	private static List<CrawlerTrackPlacement> tudursvehiclemod$getCrawlerTrackPlacements(
			com.example.tudursvehiclemod.asset.CrawlerTrackPart track, float phase) {
		java.util.List<com.example.tudursvehiclemod.asset.CrawlerTrackPart.PathPoint> path = track.path();
		int n = path.size();
		if (n < 2) {
			return List.of();
		}
		java.util.List<com.example.tudursvehiclemod.asset.CrawlerTrackPart.PathPoint> orderedPath;
		// Flip=true reverses BOTH the path's
		// own winding order (this) AND the visual display (the matching
		// non-negated track.flip() check in the render loop that actually
		// calls this method) TOGETHER - flip=false leaves both exactly as
		// authored in the source file.
		if (track.flip()) {
			orderedPath = new java.util.ArrayList<>(path);
			java.util.Collections.reverse(orderedPath);
		} else {
			orderedPath = path;
		}

		double[] cumulative = new double[n + 1];
		for (int i = 0; i < n; i++) {
			var a = orderedPath.get(i);
			var b = orderedPath.get((i + 1) % n);
			double segLength = Math.hypot(b.y() - a.y(), b.z() - a.z());
			cumulative[i + 1] = cumulative[i] + segLength;
		}
		double totalLength = cumulative[n];
		if (totalLength < 1.0E-6) {
			return List.of();
		}

		float spacing = Math.max(0.05f, track.linkSpacing());
		int count = Math.max(1, Math.round((float) (totalLength / spacing)));
		List<CrawlerTrackPlacement> placements = new java.util.ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			double raw = i * spacing + phase;
			double distance = ((raw % totalLength) + totalLength) % totalLength;
			int segIndex = 0;
			while (segIndex < n - 1 && cumulative[segIndex + 1] < distance) {
				segIndex++;
			}
			var a = orderedPath.get(segIndex);
			var b = orderedPath.get((segIndex + 1) % n);
			double segStart = cumulative[segIndex];
			double segLength = cumulative[segIndex + 1] - segStart;
			double t = segLength > 1.0E-9 ? (distance - segStart) / segLength : 0.0;
			double y = a.y() + (b.y() - a.y()) * t;
			double z = a.z() + (b.z() - a.z()) * t;
			float angleDegrees = (float) Math.toDegrees(Math.atan2(b.z() - a.z(), b.y() - a.y()));
			placements.add(new CrawlerTrackPlacement(y, z, angleDegrees));
		}
		return placements;
	}

	/** Computes each triangle's own face normal for geometry that is built fresh every frame, and so cannot be packed ahead of time (the wake bands are the only such caller - see renderTriangles()'s own List overload).
	 *
	 * Model geometry does NOT come through here at all any more. Each ObjModel.Triangles now carries its own normals, computed once when it is packed, which replaced the static IdentityHashMap that used to cache them globally - see ObjModel.Triangles' own doc. That removal is deliberate: a static map keyed on per-frame geometry is exactly the shape of leak this codebase has hit before, and the shape it can no longer take now that nothing retains normals beyond the geometry they belong to.
	 *
	 * WHY A COMPUTED FACE NORMAL RATHER THAN EACH VERTEX'S OWN AUTHORED ONE (per an earlier direct report of one side of double-sided translucent faces appearing incorrectly shaded/dark): a wrong or inconsistent "vn" on a model's own back-facing copy of a surface - a common authoring mistake for geometry drawn double-sided to fake two-sided rendering under a culling layer - would otherwise shade as if lit from behind, regardless of which way the triangle actually faces on screen. A normal derived from the triangle's own three positions is always consistent with its own winding, so that cannot happen. */
	private static float[] tudursvehiclemod$computeFaceNormals(List<ObjModel.Vertex> tris) {
		int triangleCount = tris.size() / 3;
		float[] normals = new float[triangleCount * 3];
		for (int t = 0; t < triangleCount; t++) {
			int i = t * 3;
			// Written STRAIGHT into the output array rather than through a method returning its own float[3]. This runs over every triangle of every wake band on every frame - a large vessel's trail is hundreds of tiles at eighteen vertices each - so the old form allocated a short-lived three-float array per triangle per frame purely to copy it out again. That is the same needless per-frame allocation already removed from the model path (see ObjModel.Triangles' own doc); this is the last place it survived.
			ObjModel.Vertex a = tris.get(i);
			ObjModel.Vertex b = tris.get(i + 1);
			ObjModel.Vertex c = tris.get(i + 2);
			float ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z();
			float vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
			float nx = uy * vz - uz * vy;
			float ny = uz * vx - ux * vz;
			float nz = ux * vy - uy * vx;
			float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (length < 1.0e-6f) {
				// Degenerate triangle - falls back to the first vertex's own authored normal, exactly as before.
				normals[t * 3] = a.nx();
				normals[t * 3 + 1] = a.ny();
				normals[t * 3 + 2] = a.nz();
			} else {
				normals[t * 3] = nx / length;
				normals[t * 3 + 1] = ny / length;
				normals[t * 3 + 2] = nz / length;
			}
		}
		return normals;
	}

	/** Size of a single wake tile (blocks). See IMPLEMENTATION_NOTES.md "Wake Trail" section for design history. */
	private static final float WAKE_BOW_TILE_SIZE = 1.0f;
	private static final float WAKE_STERN_TILE_SIZE = 1.6f;
	/** Smaller than the main bow/stern tiles, for a subtler "just looks about right" turbulent band along the hull's own sides (see AbstractVehicleEntity's own tudursvehiclemod$updateWakeTrail() doc for how these points are generated). */
	private static final float WAKE_SIDE_TILE_SIZE = 0.7f;
	/** A tile's own texture/tint stays at this fixed alpha (0-255) for its ENTIRE life, right up until it is removed at its own despawn tick - it does not fade with age. An earlier per-age-band fade required genuine alpha blending to look right; run through this mod's own cutout-style render layer (used here for the same water-sorting reasons the vehicle's own translucent parts use it - true alpha blending sorts poorly against water) a fading alpha doesn't fade at all, it just disappears outright once below the pipeline's own cutout threshold. The sinking motion (see tudursvehiclemod$renderWakeTiles()'s own sink-phase doc) is what carries the "fading away" effect instead - a tile visually recedes by sinking, not by becoming transparent. */
	private static final int WAKE_TILE_ALPHA = 160;

	/** Computes a wake tile's lateral offset distance. Two additive terms: (1) forward-speed-based divergence at a fixed wake angle (Kelvin-wake-inspired), and (2) for the inner side only, a rotational term from this point's own end's distance from the vehicle's rotation origin (rigid-body kinematics: lever arm x turn rate), scaled by WAKE_INNER_ROTATION_STRENGTH for visibility. Grows without bound for as long as the tile exists - that continued outward growth IS the Kelvin wake's own natural look, so nothing here caps it; the caller decides sink/despawn timing separately (anchored to maxAgeTicks, not to how far this has travelled - see that call site's own doc). See IMPLEMENTATION_NOTES.md "Wake Trail" section for full design history and rationale. */
	private static double tudursvehiclemod$computeWakeOffsetDistance(long elapsedTicks, float referenceSpeed, double angleRad, boolean isInner, float leverArm, float innerBoostSigned) {
		double rawDistance = elapsedTicks * referenceSpeed * Math.tan(angleRad);
		if (isInner) {
			double turnRateRadPerTick = Math.toRadians(Math.abs(innerBoostSigned));
			rawDistance += elapsedTicks * leverArm * turnRateRadPerTick * WAKE_INNER_ROTATION_STRENGTH;
		}
		return rawDistance;
	}

	/** The fraction of a wake tile's own effective max age (maxAgeTicks, i.e. the vehicle's own configured wake_trail_duration_ticks) spent BEFORE sinking begins - shared by bow, stern, and side alike (the sink itself starts at this point and grows without bound afterward - see WAKE_SINK_RATE_PER_TICK's own doc - rather than being confined to a fixed remaining fraction of the tile's own life). Time-anchored rather than distance-anchored so the full configured duration is always honored regardless of this vehicle's own speed - a purely distance-based trigger (an earlier version, for bow specifically) despawns a tile sooner the FASTER its own vehicle moves, which is backwards, and for most realistic speeds triggered well under wakeTrailDurationTicks. */
	private static final double WAKE_SINK_AGE_FRACTION = 0.15;

	/** The maximum magnitude (degrees, either direction) the stern chase's own linear turn-rate extrapolation is allowed to reach, regardless of how large gapDelayTicks x innerBoostSigned itself computes to. 180 degrees (half a full rotation) is already an extreme amount of sustained turning for this extrapolation's own short gapDelayTicks window to assume plausible - capping there avoids the worst-case wrap-past-a-full-rotation estimates a sharp, slow turn could otherwise produce. */
	private static final float WAKE_STERN_CHASE_MAX_EXTRAPOLATION_DEG = 180f;

	/** How far (world-space blocks) a stern/side/bow tile sinks per TICK once it's entered its own sink phase (see tudursvehiclemod$renderWakeTiles()'s own doc for how that start point and this rate combine into an unbounded, ongoing sink) - a genuine per-tick rate, not a value confined to any fixed window. */
	private static final double WAKE_SINK_RATE_PER_TICK = 0.025;

	/** How many ticks after entering its own sink phase a BOW or STERN tile takes to fully despawn (shared by both - see tudursvehiclemod$renderWakeTiles()'s own doc for how this combines with WAKE_SINK_RATE_PER_TICK). 200 ticks = 10 seconds, applied identically whether this vehicle is currently moving or stopped. Kept within WAKE_SINK_AGE_FRACTION's own remaining share of a typical wakeTrailDurationTicks (roughly 300 ticks by default) so the sink animation still has room to complete before this vehicle's own normal age-based pruning removes it regardless - a vehicle configured with a notably shorter wakeTrailDurationTicks than the default may need this value revisited. */
	private static final long WAKE_MAIN_SINK_DESPAWN_TICKS = 200;

	/** Per a further direct request ("側面帯について、沈み始めてから1秒ほどで消滅するようにしたい" - the side band should disappear about 1 second after it starts sinking): how many ticks after entering its own sink phase a SIDE-BAND tile takes to fully despawn - see tudursvehiclemod$renderWakeTiles()'s own doc. 20 ticks = 1 second exactly, as requested. */
	private static final long WAKE_SIDE_SINK_DESPAWN_TICKS = 20;

	/** Rotates a local (x,z) offset by a yaw-only rotation (degrees) into a world-space direction, deduplicating the same inline cos/sin pattern that used to appear 4 separate times within tudursvehiclemod$renderWakeTiles() alone (the stern chase's own chase/creation offsets, plus the main tile's own perpX/Z and backwardX/Z basis vectors). Matches this project's own established local-to-world convention (compare AbstractVehicleEntity's own tudursvehiclemod$updateWakeTrail() perpPlusX/Z derivation) - yaw=0 pointing toward +Z local/world.
	 *
	 * This used to RETURN a freshly allocated double[]{worldX, worldZ}. tudursvehiclemod$renderWakeTiles() calls it up to FOUR times per tile per frame (perp, backward, plus the stern chase's own chaseOffset and creationOffset), and that method runs every rendered frame across every live tile - so at 60fps with a few thousand tiles this alone was on the order of a million short-lived arrays per second, and the turn-specific chase path is exactly what pushes it from two calls per tile to four. Writes into a caller-supplied two-element scratch array now instead, so the whole render pass reuses a handful of arrays rather than allocating per tile. */
	private static void tudursvehiclemod$rotateYawOnly(double localX, double localZ, double yawDegrees, double[] out) {
		double yawRad = Math.toRadians(yawDegrees);
		double cos = Math.cos(yawRad);
		double sin = Math.sin(yawRad);
		out[0] = localX * -cos + localZ * -sin;
		out[1] = localX * -sin + localZ * cos;
	}

	/** Wake divergence angle (degrees). Exaggerated well past the real Kelvin angle (~19.5 deg) for visibility. */
	private static final double WAKE_ANGLE_DEG = 30.0;

	/** Ratio of backward (toward-stern) drift to lateral drift, for the inner side only. Without this, a tile's own trajectory is purely perpendicular to its own creation yaw and never has any toward-stern character regardless of distance or elapsed time - per report, this was the actual issue, not timing. 0.7 gives a diagonal sweep (not purely sideways, not purely backward). */
	private static final double WAKE_INNER_BACKWARD_RATIO = 0.7;

	/** Multiplier on the inner side's rotational term. A tile reaches its own target distance at elapsedTicks = target / (leverArm x turnRate x this value) - raising this makes tiles reach their target SOONER (closer to the leading end, since less ship-travel-time has passed), lowering it makes them take LONGER (further toward the trailing end before despawning). Lowered from an earlier 2.0 per report ("手前に寄りすぎる" - the visible spread leans too far toward the leading end) - the earlier value reached the (also-enlarged) cap too quickly. */
	private static final double WAKE_INNER_ROTATION_STRENGTH = 1.0;

	/** Draws wakeBowHistory/wakeSternHistory as independent, unconnected tiles (not a connected ribbon) - side is 0 for stern or +/-1 for the bow's two diverging tile fields. Each point's own spreadDistance() (fixed at creation) determines its own target reach distance. A young bow tile (elapsedTicks < WAKE_BOW_MOUND_AGE_TICKS) is drawn as a raised pyramid instead of a flat quad, height fading to 0 with age - combined with the existing lateral divergence, this alone produces the "multiple pyramids spreading like Kelvin waves" look with no separate mound data/generation needed. "船首(Kelvin波・盛り上がり)"). */
	private static void tudursvehiclemod$renderWakeTiles(List<AbstractVehicleEntity.WakeHistoryPoint> history, double side, float tileSize,
			double originX, double originY, double originZ, MatrixStack matrices, OrderedRenderCommandQueue queue,
			RenderLayer layer, int light, long maxAgeTicks, long currentWorldTick, boolean applySternChase, float currentYaw, long sinkDespawnTicks) {
		if (history.isEmpty()) {
			return;
		}
		List<ObjModel.Vertex> tris = new java.util.ArrayList<>();
		float halfSize = tileSize * 0.5f;
		// Four scratch arrays allocated ONCE per call to this method, reused across every tile in the loop below - replacing what used to be up to four freshly allocated double[] per tile per frame. Safe to share this way because each is fully consumed immediately after the rotate call that fills it, before the next one runs.
		double[] perpScratch = new double[2];
		double[] backwardScratch = new double[2];
		double[] chaseScratch = new double[2];
		double[] creationScratch = new double[2];
		for (AbstractVehicleEntity.WakeHistoryPoint p : history) {
			long elapsedTicks = currentWorldTick - p.tick();
			double lateralOffset = 0.0;
			double backwardOffset = 0.0;
			double sinkDepth = 0.0;
			double effectiveX = p.x();
			double effectiveZ = p.z();
			float edgeOffsetThisSide = side > 0 ? p.edgeOffsetPlus() : p.edgeOffsetMinus();
			if (side != 0.0) {
				boolean isInner = (p.innerBoostSigned() > 0 && side > 0) || (p.innerBoostSigned() < 0 && side < 0);
				double angleRad = Math.toRadians(WAKE_ANGLE_DEG);
				// distance keeps growing unbounded for this tile's entire life - that continued outward
				// growth IS the Kelvin-wake spreading look, not a bug.
				double distance = tudursvehiclemod$computeWakeOffsetDistance(elapsedTicks, p.referenceSpeed(), angleRad, isInner, p.leverArm(), p.innerBoostSigned());
				// Immediate baseline offset from this end's own actual edge position (see WakeHistoryPoint's own edgeOffsetPlus/edgeOffsetMinus doc) - added on top of, not replacing, the usual growth. 0 for stern tiles and for a genuinely pointed bow. Uses this side's own actual value (not a shared/averaged magnitude - see that same doc for why that used to cause an asymmetric overshoot on one side for a wide bow).
				lateralOffset = (distance + edgeOffsetThisSide) * side;
				if (isInner) {
					// Inner tiles also drift toward the stern, proportional to their own lateral offset - without this, a tile's own trajectory is purely sideways and the wake shape has no toward-stern character at all, regardless of distance or elapsed time.
					backwardOffset = distance * WAKE_INNER_BACKWARD_RATIO;
				}
			} else {
				// Per a further direct request (see WakeHistoryPoint's own sternGapDelayTicks doc for the fuller rationale): a STERN-band point's own sink-start timing (see the shared computation below) is delayed by this estimate of how long this hull's own actual stern tip takes to physically reach this same world position after this point's own creation. 0 for the side band (and for bow, entirely outside this branch), so this has no effect on either.
				//
				// This point's own local (pre-rotation) offset is re-rotated by an "effective yaw" (see below) instead of using the fixed world position recorded at creation - tracking a turn instead of assuming this hull travelled in a straight line, anchored at THIS point's own creation-time vehicle position (reconstructed by subtracting this point's own local offset, rotated by ITS OWN creation yaw, back out of its own recorded p.x()/p.z()) so it stays anchored where this specific point actually was rather than collapsing every same-age-band point onto the vehicle's own current position.
				//
				// Freezes at an ESTIMATE of this vehicle's own yaw at the moment of detachment (extrapolated from this point's own creation yaw plus its own turn rate at that same moment, held constant across the gapDelayTicks window) once past gapDelayTicks, so a later, unrelated turn can no longer disturb an already-detached point.
				if (applySternChase) {
					long gapDelayTicks = (long) p.sternGapDelayTicks();
					double localX = p.sternLocalOffsetX();
					double localZ = p.sternLocalOffsetZ();
					float effectiveYawDeg;
					if (elapsedTicks < gapDelayTicks) {
						effectiveYawDeg = currentYaw;
					} else {
						float extrapolatedTurn = p.innerBoostSigned() * gapDelayTicks;
						extrapolatedTurn = net.minecraft.util.math.MathHelper.clamp(extrapolatedTurn, -WAKE_STERN_CHASE_MAX_EXTRAPOLATION_DEG, WAKE_STERN_CHASE_MAX_EXTRAPOLATION_DEG);
						effectiveYawDeg = p.yaw() + extrapolatedTurn;
					}
					tudursvehiclemod$rotateYawOnly(localX, localZ, effectiveYawDeg, chaseScratch);
					tudursvehiclemod$rotateYawOnly(localX, localZ, p.yaw(), creationScratch);
					double creationVehicleX = p.x() - creationScratch[0];
					double creationVehicleZ = p.z() - creationScratch[1];
					effectiveX = creationVehicleX + chaseScratch[0];
					effectiveZ = creationVehicleZ + chaseScratch[1];
				}
			}
			// Sink-then-despawn timing, UNIFIED across bow/stern/side: this tile enters its own sink
			// phase once its own age passes the last WAKE_SINK_AGE_FRACTION of maxAgeTicks (offset by
			// this point's own sternGapDelayTicks() - 0 for bow and side, a real value only for a
			// genuine stern point), then sinks at a constant WAKE_SINK_RATE_PER_TICK (unbounded, no
			// fixed depth cap), and finally despawns once past sinkDespawnTicks - a value the caller
			// passes in per call (bow and stern share the same longer grace period, the side band its
			// own short, specific ~1s one - see tudursvehiclemod$renderWakeRibbon()'s own doc for the
			// actual values each gets).
			long gapDelayTicksForSink = (long) p.sternGapDelayTicks();
			long sinkStartTick = (long) ((1.0 - WAKE_SINK_AGE_FRACTION) * maxAgeTicks) + gapDelayTicksForSink;
			long ticksSinceSinkStart = Math.max(0L, elapsedTicks - sinkStartTick);
			if (ticksSinceSinkStart >= sinkDespawnTicks) {
				continue;
			}
			sinkDepth = ticksSinceSinkStart * WAKE_SINK_RATE_PER_TICK;
			tudursvehiclemod$rotateYawOnly(1.0, 0.0, p.yaw(), perpScratch);
			double perpX = perpScratch[0];
			double perpZ = perpScratch[1];
			tudursvehiclemod$rotateYawOnly(0.0, 1.0, p.yaw(), backwardScratch);
			double backwardX = -backwardScratch[0];
			double backwardZ = -backwardScratch[1];
			float tileX = (float) (effectiveX + perpX * lateralOffset + backwardX * backwardOffset - originX);
			float tileY = (float) (p.y() - originY - sinkDepth);
			float tileZ = (float) (effectiveZ + perpZ * lateralOffset + backwardZ * backwardOffset - originZ);
			float ax = tileX - halfSize, az = tileZ - halfSize;
			float bx = tileX + halfSize, bz = tileZ - halfSize;
			float cx = tileX + halfSize, cz = tileZ + halfSize;
			float dx = tileX - halfSize, dz = tileZ + halfSize;
			// Per a further direct report ("左右非対称な場合はわざわざ平均とせず、左右それぞれの角度を使用する" - use each side's own actual angle for an asymmetric hull, instead of averaging): picks p.moundSpreadAnglePlusDeg()/moundTiltPlusX/Z for side>0, or the Minus equivalents for side<0 - see AbstractVehicleEntity's own tudursvehiclemod$updateWakeTrail() doc for exactly how Plus/Minus was assigned to left/right at creation time. A symmetric hull naturally ends up with the same values on both sides anyway, so this changes nothing there.
			float moundSpreadAngleDeg = side > 0 ? p.moundSpreadAnglePlusDeg() : p.moundSpreadAngleMinusDeg();
			float moundTiltThisSideX = side > 0 ? p.moundTiltPlusX() : p.moundTiltMinusX();
			float moundTiltThisSideZ = side > 0 ? p.moundTiltPlusZ() : p.moundTiltMinusZ();
			float moundHeight = 0f;
			float apexOffsetX = 0f;
			float apexOffsetZ = 0f;
			float moundTileX = tileX;
			float moundTileZ = tileZ;
			float moundHalfSize = halfSize;
			if (side != 0.0 && elapsedTicks < WAKE_BOW_MOUND_AGE_TICKS) {
				double moundAngleRad = Math.toRadians(moundSpreadAngleDeg);
				// This end's own actual left/right corner offset (see WakeHistoryPoint's own edgeOffsetPlus/edgeOffsetMinus doc) was missing here entirely, unlike the flat tile's own lateralOffset just below (which DOES include it). Without it, BOTH side=+1 and side=-1 mounds started at the exact same center position (elapsedTicks=0 gives 0 growth), for a wide bow only truly separating once they'd aged partway through the mound phase - now they start at the bow's own actual left/right corners immediately, just like the flat Kelvin-wake tiles already do. Uses this side's own actual value now, not a shared/averaged magnitude.
				double moundLateralOffset = (elapsedTicks * p.referenceSpeed() * Math.tan(moundAngleRad) + edgeOffsetThisSide) * side;
				moundTileX = (float) (p.x() + perpX * moundLateralOffset - originX);
				moundTileZ = (float) (p.z() + perpZ * moundLateralOffset - originZ);
				float moundAgeFraction = 1f - (float) elapsedTicks / (float) WAKE_BOW_MOUND_AGE_TICKS;
				// SizeScale now comes from p.hullLength() (this cluster's own overall bow-to-stern length), not p.spreadDistance() (beam/width) as before - a longer vessel's own mound is now bigger overall. Applied as a RATIO to BOTH the base footprint (moundHalfSize, separate from the flat tiles' own shared halfSize) AND the height ceiling/growth below - multiplicatively alongside the existing speed-driven height term, not replacing it, so a long vessel moving fast still gets a taller mound than a long vessel moving slowly, and vice versa for a short vessel.
				float sizeScale = 1f + p.hullLength() * (float) WAKE_BOW_MOUND_SIZE_PER_HULL_LENGTH;
				moundHalfSize = halfSize * sizeScale;
				float maxHeight = (float) (WAKE_BOW_MOUND_MAX_HEIGHT * sizeScale);
				float fullHeight = Math.min(p.referenceSpeed() * (float) WAKE_BOW_MOUND_HEIGHT_PER_SPEED * sizeScale, maxHeight);
				moundHeight = fullHeight * moundAgeFraction;
				apexOffsetX = moundTiltThisSideX * moundHeight * (float) WAKE_BOW_MOUND_TILT_SCALE;
				apexOffsetZ = moundTiltThisSideZ * moundHeight * (float) WAKE_BOW_MOUND_TILT_SCALE;
			}
			if (moundHeight > 0f) {
				float mx0 = moundTileX - moundHalfSize, mz0 = moundTileZ - moundHalfSize;
				float mbx = moundTileX + moundHalfSize, mbz = moundTileZ - moundHalfSize;
				float mcx = moundTileX + moundHalfSize, mcz = moundTileZ + moundHalfSize;
				float mdx = moundTileX - moundHalfSize, mdz = moundTileZ + moundHalfSize;
				float apexX = moundTileX + apexOffsetX;
				float apexY = tileY + moundHeight;
				float apexZ = moundTileZ + apexOffsetZ;
				// Winding (corner, apex, nextCorner) for every one of the 4 sides - verified by hand to give an outward-and-upward-facing normal via the cross product below, unlike the flat tiles' own single hardcoded (0,1,0) (which a sloped pyramid face can't just reuse).
				// Each face submitted BOTH ways (corner/apex swapped, reversing the winding) so the
				// mound is visible from any angle - from directly below (a submarine looking up
				// through the surface), not just from above/outside.
				tudursvehiclemod$addMoundFace(tris, mx0, tileY, mz0, apexX, apexY, apexZ, mbx, tileY, mbz);
				tudursvehiclemod$addMoundFace(tris, mbx, tileY, mbz, apexX, apexY, apexZ, mx0, tileY, mz0);
				tudursvehiclemod$addMoundFace(tris, mbx, tileY, mbz, apexX, apexY, apexZ, mcx, tileY, mcz);
				tudursvehiclemod$addMoundFace(tris, mcx, tileY, mcz, apexX, apexY, apexZ, mbx, tileY, mbz);
				tudursvehiclemod$addMoundFace(tris, mcx, tileY, mcz, apexX, apexY, apexZ, mdx, tileY, mdz);
				tudursvehiclemod$addMoundFace(tris, mdx, tileY, mdz, apexX, apexY, apexZ, mcx, tileY, mcz);
				tudursvehiclemod$addMoundFace(tris, mdx, tileY, mdz, apexX, apexY, apexZ, mx0, tileY, mz0);
				tudursvehiclemod$addMoundFace(tris, mx0, tileY, mz0, apexX, apexY, apexZ, mdx, tileY, mdz);
			} else {
				// The vertex WINDING ORDER, not any per-vertex normal value, is what a GPU's own
				// face-culling test actually uses - a normal is shading data for the fragment shader,
				// completely separate from culling. The order (a,b,c / a,c,d) produces a
				// downward-facing triangle under the same cross-product convention
				// tudursvehiclemod$addMoundFace() itself uses (verified: (0,-1,0)), which is back-facing
				// as viewed from above and gets discarded by this pipeline's own culling (see
				// DitherCutoutLayers' own doc) before texture/tint alpha even matters - independent of
				// whatever normal was declared. The reversed order (a,c,b / a,d,c) is front-facing from
				// above, which also happens to make the geometric normal (0,1,0).
				//
				// Submitted BOTH ways (front-facing from above, AND its own mirror-image winding with
				// the normal flipped) rather than only one direction - a single-sided tile is still
				// invisible from BELOW the water surface (a submarine looking up, say). Doubling the
				// geometry this way is a per-draw, render-state-free way to guarantee visibility from any
				// angle, without touching this pipeline's own shared cull setting (which the vehicle
				// body's own geometry, sharing the same pipeline object, still needs left alone).
				tris.add(new ObjModel.Vertex(ax, tileY, az, 0f, 0f, 0f, 1f, 0f));
				tris.add(new ObjModel.Vertex(cx, tileY, cz, 1f, 1f, 0f, 1f, 0f));
				tris.add(new ObjModel.Vertex(bx, tileY, bz, 1f, 0f, 0f, 1f, 0f));
				tris.add(new ObjModel.Vertex(ax, tileY, az, 0f, 0f, 0f, 1f, 0f));
				tris.add(new ObjModel.Vertex(dx, tileY, dz, 0f, 1f, 0f, 1f, 0f));
				tris.add(new ObjModel.Vertex(cx, tileY, cz, 1f, 1f, 0f, 1f, 0f));
				tris.add(new ObjModel.Vertex(ax, tileY, az, 0f, 0f, 0f, -1f, 0f));
				tris.add(new ObjModel.Vertex(bx, tileY, bz, 1f, 0f, 0f, -1f, 0f));
				tris.add(new ObjModel.Vertex(cx, tileY, cz, 1f, 1f, 0f, -1f, 0f));
				tris.add(new ObjModel.Vertex(ax, tileY, az, 0f, 0f, 0f, -1f, 0f));
				tris.add(new ObjModel.Vertex(cx, tileY, cz, 1f, 1f, 0f, -1f, 0f));
				tris.add(new ObjModel.Vertex(dx, tileY, dz, 0f, 1f, 0f, -1f, 0f));
			}
		}
		if (tris.isEmpty()) {
			return;
		}
		int tintColor = (WAKE_TILE_ALPHA << 24) | 0xFFFFFF;
		// This list is rebuilt from scratch on every single frame, which is exactly the geometry the List overload of renderTriangles() exists for - it computes normals on the spot and retains nothing past the frame. There is no longer any global normal cache for per-frame geometry to leak into at all (see ObjModel.Triangles' own doc), which is what previously pinned the heap at its ceiling by permanently retaining every band of every frame.
		renderTriangles(queue, matrices, layer, tris, light, tintColor);
	}

	/** List.copyOf() equivalent, except an EMPTY source returns the shared, immutable List.of() singleton rather than allocating a new (albeit tiny) list every single tick. The point isn't the saved allocation so much as making the no-wake case reliably drop whatever previous, potentially large snapshot this render state was still holding onto. */
	private static List<AbstractVehicleEntity.WakeHistoryPoint> tudursvehiclemod$snapshotWakeHistory(java.util.Deque<AbstractVehicleEntity.WakeHistoryPoint> source) {
		return source.isEmpty() ? List.of() : List.copyOf(source);
	}

	/** Draws state.wakeBowHistory/wakeSternHistory as independent, fading tiles. Called before this vehicle's rotation is applied to matrices, since history points are world-space (not local model geometry). When state.wakeReversing is true, the stern leads (gets the diverging pattern) and the bow trails (gets the static pattern), matching which end is actually cutting through the water first. Like Kelvin waves, multiple pyramids are generated on each side - see tudursvehiclemod$renderWakeTiles()'s own doc for how the bow mound is now rendered as part of this same diverging-tile pass, not a separate one. */
	private static void tudursvehiclemod$renderWakeRibbon(VehicleRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
		net.minecraft.util.Identifier foamTexture = net.minecraft.util.Identifier.of(com.example.tudursvehiclemod.VehicleMod.MOD_ID, "textures/misc/wake_ribbon_white.png");
		RenderLayer layer = DitherCutoutLayers.entityDitherCutout(foamTexture);
		List<AbstractVehicleEntity.WakeHistoryPoint> leadingHistory = state.wakeReversing ? state.wakeSternHistory : state.wakeBowHistory;
		List<AbstractVehicleEntity.WakeHistoryPoint> trailingHistory = state.wakeReversing ? state.wakeBowHistory : state.wakeSternHistory;
		// bow and stern now share the same sink-then-despawn timing (WAKE_MAIN_SINK_DESPAWN_TICKS) -
		// see tudursvehiclemod$renderWakeTiles()'s own doc for the unified computation this feeds.
		tudursvehiclemod$renderWakeTiles(leadingHistory, 1.0, WAKE_BOW_TILE_SIZE,
				state.x, state.y, state.z, matrices, queue, layer, state.light, state.wakeTrailDurationTicks, state.currentWorldTick, false, state.wakeCurrentYaw, WAKE_MAIN_SINK_DESPAWN_TICKS);
		tudursvehiclemod$renderWakeTiles(leadingHistory, -1.0, WAKE_BOW_TILE_SIZE,
				state.x, state.y, state.z, matrices, queue, layer, state.light, state.wakeTrailDurationTicks, state.currentWorldTick, false, state.wakeCurrentYaw, WAKE_MAIN_SINK_DESPAWN_TICKS);
		tudursvehiclemod$renderWakeTiles(trailingHistory, 0.0, WAKE_STERN_TILE_SIZE,
				state.x, state.y, state.z, matrices, queue, layer, state.light, state.wakeTrailDurationTicks, state.currentWorldTick, true, state.wakeCurrentYaw, WAKE_MAIN_SINK_DESPAWN_TICKS);
		// Rendered exactly like stern tiles (side 0.0, no lateral growth, ages and sinks the same way) - see AbstractVehicleEntity's own tudursvehiclemod$updateWakeTrail() doc for how these points are generated. Uses its own, smaller WAKE_SIDE_TILE_SIZE for a subtler look than the main bow/stern tiles. Per a further direct request ("側面帯について、沈み始めてから1秒ほどで消滅するようにしたい" - see WAKE_SIDE_SINK_DESPAWN_TICKS's own doc for the fuller rationale): the side band's own, short, specific sink-despawn window. applySternChase is false - the side band never chases a turn, per the design principle's own sole exception being the stern specifically.
		tudursvehiclemod$renderWakeTiles(state.wakeSideHistory, 0.0, WAKE_SIDE_TILE_SIZE,
				state.x, state.y, state.z, matrices, queue, layer, state.light, state.wakeTrailDurationTicks, state.currentWorldTick, false, state.wakeCurrentYaw, WAKE_SIDE_SINK_DESPAWN_TICKS);
	}




	/** Emits one triangular face (3 vertices, v0->v1->v2) with a face normal computed via cross product of its own two edges (v1-v0, v2-v0), normalized - unlike the flat wake tiles (a constant (0,1,0) normal is fine for something perfectly horizontal), a mound's own sloped sides each need their own actual normal for the lighting to look reasonably correct. Falls back to straight up if the three points are degenerate (a zero-length cross product - shouldn't happen for a mound with any actual height, but avoids a division by zero if height is ever exactly 0). */
	private static void tudursvehiclemod$addMoundFace(List<ObjModel.Vertex> tris, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2) {
		float ex1 = x1 - x0, ey1 = y1 - y0, ez1 = z1 - z0;
		float ex2 = x2 - x0, ey2 = y2 - y0, ez2 = z2 - z0;
		float nx = ey1 * ez2 - ez1 * ey2;
		float ny = ez1 * ex2 - ex1 * ez2;
		float nz = ex1 * ey2 - ey1 * ex2;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len > 1.0e-6f) {
			nx /= len;
			ny /= len;
			nz /= len;
		} else {
			nx = 0f;
			ny = 1f;
			nz = 0f;
		}
		tris.add(new ObjModel.Vertex(x0, y0, z0, 0f, 0f, nx, ny, nz));
		tris.add(new ObjModel.Vertex(x1, y1, z1, 0.5f, 1f, nx, ny, nz));
		tris.add(new ObjModel.Vertex(x2, y2, z2, 1f, 0f, nx, ny, nz));
	}

	/** How much a mound's own height (world-space blocks) grows per unit of referenceSpeed (blocks/tick) - see tudursvehiclemod$renderWakeTiles()'s own doc. Tripled from an earlier 2.0 alongside WAKE_BOW_MOUND_MAX_HEIGHT below (raising just this alone would only make a mound reach the SAME old ceiling sooner, not actually grow any taller at high speed - both need to scale together for the peak itself to triple). */
	private static final double WAKE_BOW_MOUND_HEIGHT_PER_SPEED = 6.0;

	/** Ceiling on a mound's own height regardless of speed - see tudursvehiclemod$renderWakeTiles()'s own doc. This value is BEFORE sizeScale is applied - the actual per-mound ceiling scales up further for a larger hull. Tripled from an earlier 0.8 alongside WAKE_BOW_MOUND_HEIGHT_PER_SPEED above - see that constant's own doc for why both needed to change together. */
	private static final double WAKE_BOW_MOUND_MAX_HEIGHT = 2.4;

	/** How much p.hullLength() (this cluster's own overall bow-to-stern length) grows a mound's own sizeScale multiplier. Switched from the earlier p.spreadDistance() (beam/width)-based version - since a typical hull's own length is several times its own beam, this coefficient is correspondingly smaller (0.006 vs the earlier 0.02) to land on a similar overall scale for a typical vessel, while still meaningfully differentiating a genuinely long hull from a short one. */
	private static final double WAKE_BOW_MOUND_SIZE_PER_HULL_LENGTH = 0.006;

	/** How far (as a multiple of a mound's own height) its own apex shifts horizontally toward the current side's own moundTiltPlusX/Z or moundTiltMinusX/Z - see tudursvehiclemod$renderWakeTiles()'s own doc. */
	private static final double WAKE_BOW_MOUND_TILT_SCALE = 1.5;

	/** How many ticks a young bow tile's own mound height takes to fade from its own full peak down to 0 (at which point it's indistinguishable from an ordinary flat tile) - see tudursvehiclemod$renderWakeTiles()'s own doc for the fuller "mound reuses the same diverging tile" rationale. 15 ticks (0.75s) gives a quick pile-up-and-settle rather than a lasting raised shape. */
	private static final long WAKE_BOW_MOUND_AGE_TICKS = 15;

	/** Flat-array conversion, following a reported CPU single-thread rendering bottleneck with the GPU nearly idle: the model-geometry submission path, reading vertices straight out of one contiguous float[] (see ObjModel.Triangles' own doc for why that layout was the problem worth fixing).
	 *
	 * This walks the array linearly, so the prefetcher can keep up, and there is no per-vertex object, no bounds-checked List access and no pointer chase anywhere in the loop. Face normals come precomputed with the geometry rather than from any global cache. */
	static void renderTriangles(OrderedRenderCommandQueue queue, MatrixStack matrices, RenderLayer layer,
										 ObjModel.Triangles tris, int light, int tintColor) {
		if (tris.isEmpty()) {
			return;
		}
		float[] data = tris.data();
		float[] faceNormals = tris.faceNormals();
		int vertexCount = tris.vertexCount();
		// Unpacked ONCE here rather than re-unpacked for every single vertex.
		int colorA = (tintColor >> 24) & 0xFF;
		int colorR = (tintColor >> 16) & 0xFF;
		int colorG = (tintColor >> 8) & 0xFF;
		int colorB = tintColor & 0xFF;
		// The geometry already carries whatever shape the pipeline expects - four vertices per face on a QUADS pipeline (a quad as authored, or a triangle with its own last vertex repeated), three on a TRIANGLES one - so this loop just walks it. The padding branch that used to live here is gone: repeating a vertex is now done once when the model is packed, not on every frame of every vehicle. See ObjModel.Triangles.pack()'s own doc.
		int verticesPerFace = tris.verticesPerFace();
		queue.submitCustom(matrices, layer, (matrixEntry, vertexConsumer) -> {
			Matrix4f pose = matrixEntry.getPositionMatrix();

			for (int vertex = 0; vertex < vertexCount; vertex++) {
				int base = vertex * ObjModel.Triangles.FLOATS_PER_VERTEX;
				int normalBase = (vertex / verticesPerFace) * 3;
				vertexConsumer.vertex(pose, data[base], data[base + 1], data[base + 2])
						.color(colorR, colorG, colorB, colorA)
						.texture(data[base + 3], data[base + 4])
						.overlay(OverlayTexture.DEFAULT_UV)
						.light(light)
						.normal(matrixEntry, faceNormals[normalBase], faceNormals[normalBase + 1], faceNormals[normalBase + 2]);
			}
		});
	}

	/** Submits SEVERAL geometry chunks through ONE draw command, for the specific case where they all share the exact same transform.
	 *
	 * WHY THE SCOPE IS SO NARROW: nearly every part this renderer draws has its own matrix - a rotation about its own pivot, a recoil slide, a track flip - and those genuinely cannot share a submission, because the transform is applied when the command is submitted, not per vertex. The only set that does NOT is the ammo parts, which are drawn with no transform of their own at all, just a visibility check. Those, and only those, are batched here.
	 *
	 * DRAW ORDER IS DELIBERATELY UNCHANGED. The static body shares the ammo parts' matrix too and could technically have joined them, but doing so would have moved its submission from first to last - and the translucent mode resolves overlapping surfaces by draw ORDER rather than by depth, so that would have altered how vehicles actually look in that mode. The batch is submitted at the exact point the ammo parts were previously drawn one by one, and each part keeps its own original position within it, so the sequence reaching the buffer is byte-for-byte what it was. Every other part type is left exactly as it was. */
	static void renderTriangles(OrderedRenderCommandQueue queue, MatrixStack matrices, RenderLayer layer,
										 List<ObjModel.Triangles> parts, int light, int tintColor, boolean batched) {
		int totalVertices = 0;
		for (ObjModel.Triangles part : parts) {
			totalVertices += part.vertexCount();
		}
		if (totalVertices == 0) {
			return;
		}
		int colorA = (tintColor >> 24) & 0xFF;
		int colorR = (tintColor >> 16) & 0xFF;
		int colorG = (tintColor >> 8) & 0xFF;
		int colorB = tintColor & 0xFF;
		queue.submitCustom(matrices, layer, (matrixEntry, vertexConsumer) -> {
			Matrix4f pose = matrixEntry.getPositionMatrix();
			for (ObjModel.Triangles part : parts) {
				float[] data = part.data();
				float[] faceNormals = part.faceNormals();
				int vertexCount = part.vertexCount();
				int verticesPerFace = part.verticesPerFace();
				for (int vertex = 0; vertex < vertexCount; vertex++) {
					int base = vertex * ObjModel.Triangles.FLOATS_PER_VERTEX;
					int normalBase = (vertex / verticesPerFace) * 3;
					vertexConsumer.vertex(pose, data[base], data[base + 1], data[base + 2])
							.color(colorR, colorG, colorB, colorA)
							.texture(data[base + 3], data[base + 4])
							.overlay(OverlayTexture.DEFAULT_UV)
							.light(light)
							.normal(matrixEntry, faceNormals[normalBase], faceNormals[normalBase + 1], faceNormals[normalBase + 2]);
				}
			}
		});
	}

	/** The per-frame-geometry path: the wake renderer builds brand new bands every frame, so this takes an ordinary Vertex list and computes its own normals on the spot.
	 *
	 * Model geometry deliberately does NOT come through here - it uses the ObjModel.Triangles overload above. This split also removed the old cacheFaceNormals flag entirely: that flag existed only to keep per-frame wake geometry out of a static IdentityHashMap of normals (which it had previously been leaking into, permanently retaining every band of every frame). With normals now owned by the packed geometry itself, no such global cache exists to leak into, so nothing here needs to opt out of anything. */
	static void renderTriangles(OrderedRenderCommandQueue queue, MatrixStack matrices, RenderLayer layer,
										 List<ObjModel.Vertex> tris, int light, int tintColor) {
		if (tris.isEmpty()) {
			return;
		}
		float[] faceNormals = tudursvehiclemod$computeFaceNormals(tris);
		int colorA = (tintColor >> 24) & 0xFF;
		int colorR = (tintColor >> 16) & 0xFF;
		int colorG = (tintColor >> 8) & 0xFF;
		int colorB = tintColor & 0xFF;
		queue.submitCustom(matrices, layer, (matrixEntry, vertexConsumer) -> {
			Matrix4f pose = matrixEntry.getPositionMatrix();

			for (int i = 0; i + 2 < tris.size(); i += 3) {
				int normalBase = (i / 3) * 3;
				float nx = faceNormals[normalBase];
				float ny = faceNormals[normalBase + 1];
				float nz = faceNormals[normalBase + 2];

				emitVertex(vertexConsumer, pose, matrixEntry, tris.get(i), light, colorR, colorG, colorB, colorA, nx, ny, nz);
				emitVertex(vertexConsumer, pose, matrixEntry, tris.get(i + 1), light, colorR, colorG, colorB, colorA, nx, ny, nz);
				emitVertex(vertexConsumer, pose, matrixEntry, tris.get(i + 2), light, colorR, colorG, colorB, colorA, nx, ny, nz);
				// Same flag as the model path above - this shares the very same pipeline, so it must match whichever draw mode that pipeline was built with.
				if (!DitherCutoutLayers.EXPERIMENTAL_TRIANGLES) {
					emitVertex(vertexConsumer, pose, matrixEntry, tris.get(i + 2), light, colorR, colorG, colorB, colorA, nx, ny, nz);
				}
			}
		});
	}

	private static void emitVertex(net.minecraft.client.render.VertexConsumer vertexConsumer, Matrix4f pose,
									MatrixStack.Entry matrixEntry, ObjModel.Vertex v, int light,
									int r, int g, int b, int a, float nx, float ny, float nz) {
		vertexConsumer.vertex(pose, v.x(), v.y(), v.z())
				.color(r, g, b, a)
				.texture(v.u(), v.v())
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(light)
				.normal(matrixEntry, nx, ny, nz);
	}

	/** Draws every currently-active search light beam as a translucent cone - a single apex at the light's own pivot, fading and widening out to endRadius at length, with colour interpolated from startColorArgb to endColorArgb along that same length. Drawn before this vehicle's own body rotation is applied (see render()'s own call site) - worldYaw/worldPitch in VehicleRenderState.LightBeam are already absolute world-space angles, not relative to the vehicle's own current facing. Positioned via matrices.translate(pivot only) rather than pivot+state.x/y/z: matrices at this point is already translated to this entity's own render origin (verified against tudursvehiclemod$renderWakeTiles(), which explicitly subtracts originX/Y/Z from world coordinates to land back in this same local frame) - pivotX/Y/Z is already entity-relative, so adding state.x/y/z on top would double-count the entity's own position.
	 *
	 * Uses its own dedicated per-vertex-colour path rather than the model/wake renderTriangles() overloads above, neither of which can vary colour across a single draw call at all (both take one flat tintColor for the whole mesh) - a gradient cone specifically needs per-vertex colour, which only this method's own inline vertex emission provides. */
	private static void tudursvehiclemod$renderSearchLightBeams(VehicleRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
		// The cone is now the ONLY visual representation this mod draws for a search light, in both modes. The ground disc it replaced was removed because it depended on the same lightmap mixins that only take effect on a chunk rebuild - which made "beam" mode quietly chunk-rebuild-dependent, exactly what that mode is supposed to avoid. The cone is ordinary translucent geometry submitted every frame, so it has no such dependency at all.
		tudursvehiclemod$renderSearchLightBeamCones(state, matrices, queue);
	}

	/** The actual beam-cone rendering - see tudursvehiclemod$renderSearchLightBeams()'s own doc for why this no longer needs its own mode branch. */
	private static void tudursvehiclemod$renderSearchLightBeamCones(VehicleRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
		// The fixed ENTITY_TRANSLUCENT layer it used before sorted badly against entities and the water surface, because genuine alpha blending does not write depth the way the model's own dithered/cutout layers do.
		//
		// The texture is an ALPHA RAMP (row y has alpha y), not a solid colour, and each vertex's own V coordinate is its desired alpha - see tudursvehiclemod$emitBeamVertex()'s own doc. That indirection is what makes following the mode possible at all: both dither pipelines test the TEXTURE's own alpha and ignore vertex alpha entirely, so a beam carrying its fade in vertex colour alone would come out fully opaque in those modes. Sampling the ramp puts the same fade where every pipeline actually looks for it.
		net.minecraft.util.Identifier beamTexture = net.minecraft.util.Identifier.of(com.example.tudursvehiclemod.VehicleMod.MOD_ID, "textures/misc/beam_alpha_ramp.png");
		RenderLayer layer = DitherCutoutLayers.entityDitherCutout(beamTexture);
		for (var beam : state.activeSearchLightBeams) {
			matrices.push();
			matrices.translate(beam.pivotX(), beam.pivotY(), beam.pivotZ());
			// Model space forward is +Z (see render()'s own comment on the same convention) - the cone is built pointing along local +Z below, so aiming it is just this same yaw/pitch pair applied here instead.
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-beam.worldYaw()));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(beam.worldPitch()));
			// Drawn at FULL BRIGHT rather than at state.light. A beam is a light SOURCE - it emits rather than receives - so feeding it the surrounding lightmap value meant that at night, exactly when a search light is actually used, the ambient level is near zero and the per-vertex colour was multiplied down to nothing. Matches ExplosionFlashParticle's own established use of the same constant for the same reason (a self-lit effect that must not be dimmed by its surroundings).
			tudursvehiclemod$renderOneSearchLightCone(matrices, queue, layer, SEARCH_LIGHT_BEAM_FULL_BRIGHT, beam);
			matrices.pop();
		}
	}

	/** The actual cone geometry for one beam, built fresh each frame as a simple apex-plus-ring fan (SEARCH_LIGHT_CONE_SEGMENTS triangles) pointing along local +Z. No face-normal lighting is applied (an ordered/flat normal would look wrong on a light beam that isn't a real lit surface to begin with) - every vertex uses local +Z as its own normal instead, which is close enough for a translucent glow that isn't meant to read as solid geometry. */
	/** In that mode the cone is drawn as longitudinal LIGHT SHAFTS - thin blades running along the beam - rather than as a closed surface.
	 *
	 * WHY THE SOLID CONE CANNOT WORK THERE: that mode bakes the dither into the texture, binarising each texel's own alpha through a 4x4 Bayer matrix at load time. Every beam vertex samples one single column of the ramp, so the whole cone lands on one side of that binarisation together and comes out uniformly opaque - which is exactly what was reported. Sampling more of the ramp instead would put a 4-texel-wide pattern across a 450-block cone, which is the "chunky mesh at this scale" problem the report anticipated.
	 *
	 * WHY BLADES INSTEAD: translucency here is expressed as COVERAGE rather than alpha - the blades together occupy the same fraction of the cone's own circumference that the intended alpha would have been, so gaps between them let the world show through. Because it is geometry rather than a texture pattern, it stays the same apparent density at any distance, never moires, and is unaffected by mipmapping. It also happens to read as light shafts, which suits a search light better than a flat translucent cone does.
	 *
	 * The blades NARROW along the beam's own length, since the intended alpha itself fades from start to end - so the beam naturally thins out towards its far end rather than stopping abruptly. */
	private static void tudursvehiclemod$renderBladeCone(net.minecraft.client.render.VertexConsumer vertexConsumer, Matrix4f pose,
			MatrixStack.Entry matrixEntry, int light, float length, float endRadius,
			int startR, int startG, int startB, int startA, int endR, int endG, int endB, int endA, boolean transverse, boolean dashed) {
		// "bands" is the same construction with the two axes swapped: coverage is taken out of the beam's own LENGTH (each band occupying part of its slice's depth) instead of out of its circumference, and each band is a full ring rather than a lengthwise strip. Everything else - how coverage encodes alpha, the colour gradient, the double winding - is identical, which is why one method builds both.
		//
		// "Dashed" applies BOTH narrowings at once on top of "blades" (angular AND longitudinal), so each remaining patch is a short segment rather than a full-length strip - literal dashes running along the beam.
		// Computed from this specific light's own length/endRadius rather than fixed constants, so a longer or wider beam gets proportionally more rings/blades instead of stretching the same fixed count thinner. "bands" own angularCount (SEARCH_LIGHT_CONE_SEGMENTS) is deliberately left alone - it draws the FULL circumference every ring (no angular gaps to space out), so it is a smoothness setting rather than a density one and is unrelated to this request.
		int angularCount = transverse ? SEARCH_LIGHT_CONE_SEGMENTS : tudursvehiclemod$scaledBladeCount(endRadius);
		int lengthCount = transverse ? tudursvehiclemod$scaledRingCount(length) : (dashed ? tudursvehiclemod$scaledRingCount(length) : SEARCH_LIGHT_CONE_RINGS);
		// Per an optimisation review of blades/bands/dashes, which double-sided every quad (8 vertices) on the assumption the camera sits inside the cone where back-face culling would otherwise hide half of it: this is a plain field read, not a per-frame computation - see DitherCutoutLayers.CURRENT_MODE_CULLS's own doc for why a static final is correct here (translucencyMode itself requires a restart to change).
		boolean needsDoubleSided = DitherCutoutLayers.CURRENT_MODE_CULLS;
		// FullShare depends only on angularCount, which is fixed before either loop below even starts - it does not vary with blade OR ring - yet it used to be recalculated once per (blade, ring) pair (up to lengthCount times more often than necessary for a single blade, and angularCount times more than that across the whole nest). Hoisted here so it is computed exactly once regardless of how many blades or rings this light ends up with.
		float fullShare = (float) (Math.PI / angularCount);
		for (int blade = 0; blade < angularCount; blade++) {
			// centreAngle depends only on blade, not on ring - hoisted out of the ring loop below so it is computed once per blade instead of once per (blade, ring) pair. A single float multiply is a small saving on its own, but per the same direct request, addressed regardless of size.
			float centreAngle = (float) (2 * Math.PI * blade / angularCount);
			for (int ring = 0; ring < lengthCount; ring++) {
				// The rotating even-selection scheme still looked wrong ("回転オフセットも悪目立ちします..斜め方向に空白区間が生まれる"). Every blade at every ring is drawn again, exactly as "blades" itself already does.
				float rawNear = ring / (float) lengthCount;
				float sliceNear = dashed ? (float) Math.pow(rawNear, SEARCH_LIGHT_DASH_DENSITY_EXPONENT) : rawNear;
				float sliceFar = dashed ? (float) Math.pow((ring + 1) / (float) lengthCount, SEARCH_LIGHT_DASH_DENSITY_EXPONENT) : (ring + 1) / (float) lengthCount;
				float nearT = sliceNear;
				float farT = sliceFar;
				int nearR = (int) net.minecraft.util.math.MathHelper.lerp(nearT, startR, endR);
				int nearG = (int) net.minecraft.util.math.MathHelper.lerp(nearT, startG, endG);
				int nearB = (int) net.minecraft.util.math.MathHelper.lerp(nearT, startB, endB);
				int farR = (int) net.minecraft.util.math.MathHelper.lerp(farT, startR, endR);
				int farG = (int) net.minecraft.util.math.MathHelper.lerp(farT, startG, endG);
				int farB = (int) net.minecraft.util.math.MathHelper.lerp(farT, startB, endB);
				// Coverage IS the alpha, in whichever axis this style takes it from - see this method's own doc. Uses the original per-position interpolation rather than a fixed value, since a uniform value still didn't actually look uniform.
				float nearCoverage = net.minecraft.util.math.MathHelper.lerp(nearT, startA, endA) / 255f;
				float farCoverage = net.minecraft.util.math.MathHelper.lerp(farT, startA, endA) / 255f;
				if (transverse || dashed) {
					// The camera sits near the beam's own apex (the vehicle), so a FIXED real-world length appears larger the closer it is to that apex and smaller the farther away - the opposite of what a uniform look needs. Scaling by rawNear (the UNWARPED distance along the beam - see this loop's own doc for why it's kept separate from the now-warped sliceNear) mirrors how the radial WIDTH below already scales with radius (endRadius * t) for the same reason, which is why width was never reported as a problem here.
					//
					// "Dashed" uses its own, doubled fraction rather than sharing "bands"'s own value, since only dashes were asked to change here.
					float lengthFraction = dashed ? SEARCH_LIGHT_DASHED_LENGTH_FRACTION : SEARCH_LIGHT_DASH_LENGTH_FRACTION;
					float distanceScale = dashed ? rawNear : sliceNear;
					farT = sliceNear + (sliceFar - sliceNear) * lengthFraction * distanceScale;
				}
				// Dashes, like the plain solid cone, naturally get visually larger toward the far end since width scales with radius (endRadius*t) - which by itself is what previously made pieces look big near the tip. This shrink counteracts that growth everywhere, strengthening continuously with distance rather than switching on only near the tip.
				//
				// WHY THIS SPECIFIC CURVE: width is radius(t) times coverage(t) times this shrink - since radius rises from zero, width MUST rise from zero too near the very base (there is nothing to shrink yet), so a small rise near the base is unavoidable. What this curve does is make the shrink strong enough, early enough, that width's own peak lands close to that unavoidable base rise (around rawNear~0.1, verified numerically) rather than sitting prominently mid-beam - reading as a natural taper from the root rather than a bulge in the middle.
				float dashWidthShrink = dashed ? 1f / (1f + SEARCH_LIGHT_DASH_WIDTH_SHRINK_STRENGTH * rawNear) : 1f;
				float rawFarForShrink = (ring + 1) / (float) lengthCount;
				float dashWidthShrinkFar = dashed ? 1f / (1f + SEARCH_LIGHT_DASH_WIDTH_SHRINK_STRENGTH * rawFarForShrink) : 1f;
				float nearHalfWidth = transverse ? fullShare : (float) (Math.PI * nearCoverage * dashWidthShrink / angularCount);
				float farHalfWidth = transverse ? fullShare : (float) (Math.PI * farCoverage * dashWidthShrinkFar / angularCount);
				float nearZ = length * nearT;
				float farZ = length * farT;
				float nearRadius = endRadius * nearT;
				float farRadius = endRadius * farT;
				float nearLeft = centreAngle - nearHalfWidth;
				float nearRight = centreAngle + nearHalfWidth;
				float farLeft = centreAngle - farHalfWidth;
				float farRight = centreAngle + farHalfWidth;
				// Sampled at the ramp's own fully-opaque row: a blade must never be discarded by the cutout test, because its own see-through-ness is the GAP beside it, not any alpha of its own.
				// Front face, then the same quad reversed - the camera sits at the cone's own apex, so without both windings every blade would be back-facing and culled (the same reason the solid cone below is double-sided).
				tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, (float) Math.cos(nearLeft) * nearRadius, (float) Math.sin(nearLeft) * nearRadius, nearZ, nearR, nearG, nearB, SEARCH_LIGHT_RAMP_OPAQUE_V);
				tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, (float) Math.cos(farLeft) * farRadius, (float) Math.sin(farLeft) * farRadius, farZ, farR, farG, farB, SEARCH_LIGHT_RAMP_OPAQUE_V);
				tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, (float) Math.cos(farRight) * farRadius, (float) Math.sin(farRight) * farRadius, farZ, farR, farG, farB, SEARCH_LIGHT_RAMP_OPAQUE_V);
				tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, (float) Math.cos(nearRight) * nearRadius, (float) Math.sin(nearRight) * nearRadius, nearZ, nearR, nearG, nearB, SEARCH_LIGHT_RAMP_OPAQUE_V);

				if (needsDoubleSided) {
					tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, (float) Math.cos(nearRight) * nearRadius, (float) Math.sin(nearRight) * nearRadius, nearZ, nearR, nearG, nearB, SEARCH_LIGHT_RAMP_OPAQUE_V);
					tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, (float) Math.cos(farRight) * farRadius, (float) Math.sin(farRight) * farRadius, farZ, farR, farG, farB, SEARCH_LIGHT_RAMP_OPAQUE_V);
					tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, (float) Math.cos(farLeft) * farRadius, (float) Math.sin(farLeft) * farRadius, farZ, farR, farG, farB, SEARCH_LIGHT_RAMP_OPAQUE_V);
					tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, (float) Math.cos(nearLeft) * nearRadius, (float) Math.sin(nearLeft) * nearRadius, nearZ, nearR, nearG, nearB, SEARCH_LIGHT_RAMP_OPAQUE_V);
				}
			}
		}
	}

	/** This client's own configured translucency mode, normalised - see VehicleModConfig.translucencyMode's own doc. Read fresh from config each call, deliberately NOT via DitherCutoutLayers.BAKE_DITHER_INTO_TEXTURES (a static final resolved once at class load) - that field was the exact cause of an earlier bug where a translucencyMode change silently required a restart to take effect on beam shape. Falls back to "dither_texture" (the actual default) for an unset config. */
	private static String tudursvehiclemod$translucencyModeSetting() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		if (config == null || config.translucencyMode == null) {
			return "dither_texture";
		}
		return config.translucencyMode.trim().toLowerCase(java.util.Locale.ROOT);
	}

	/** The configured cone display-distance multiplier - see VehicleModConfig.searchLightConeDisplayDistance's own doc. Clamped to [0, 1] here regardless of what the config file holds, since a value above the stated maximum of 1.0 would make the cone reach further than the light's own actual range, and a negative one is nonsensical. Read fresh every frame for immediate effect. */
	private static float tudursvehiclemod$coneDisplayDistanceMultiplier() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		if (config == null) {
			return 1.0f;
		}
		return (float) Math.max(0.0, Math.min(1.0, config.searchLightConeDisplayDistance));
	}

	/** This client's own configured search light mode, normalised - see VehicleModConfig.searchLightMode's own doc. Falls back to the default for an unset config or an unrecognised value rather than drawing nothing. */
	private static String tudursvehiclemod$searchLightMode() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		if (config == null || config.searchLightMode == null) {
			return "beam";
		}
		return config.searchLightMode.trim().toLowerCase(java.util.Locale.ROOT);
	}

	/** The target real-world spacing between consecutive blade positions around the far end's own circumference. Derived from this project's own reference light (endRadius 140, which previously used a fixed 24 blades: circumference 2*pi*140 / 24 ≈ 36.65) so that light's own look is unchanged by this generalisation - only OTHER configurations now scale proportionally rather than reusing that same fixed count regardless of their own size. */
	private static final float SEARCH_LIGHT_REFERENCE_BLADE_SPACING = 36.65f;

	/** The target real-world spacing between consecutive rings along the beam's own length. Derived the same way from this project's own reference light (length 450, which previously used a fixed 52 rings: 450 / 52 ≈ 8.65). */
	private static final float SEARCH_LIGHT_REFERENCE_RING_SPACING = 8.65f;

	/** Floor under tudursvehiclemod$scaledBladeCount()'s own result, so a very small endRadius still reads as a beam rather than degenerating to a handful of disconnected slivers. */
	private static final int SEARCH_LIGHT_MIN_BLADE_COUNT = 6;

	/** Per a review triggered by a SECOND direct report of the freeze/crash symptom recurring, this time while adjusting dashes-related density config (the ring/blade count multipliers) - a strong hint that the PREVIOUS ceiling (200) was itself still far too permissive: at 200 blades combined with 600 rings, dashes alone could reach 960,000 vertices - roughly 33MB of vertex data rebuilt every single frame - reachable simply by raising VehicleModConfig.searchLightBladeCountMultiplier (explicitly asked to have no ceiling on its own configured VALUE), with no pathological light shape required at all, unlike the block-overlay ray-count issue a previous review fixed. Lowered to 48, which still comfortably exceeds anything a reasonably authored or reasonably-multiplied light would visually benefit from, while keeping worst-case vertex count for "dashes" (this times SEARCH_LIGHT_MAX_RING_COUNT) in the low hundreds of thousands rather than approaching a million. */
	private static final int SEARCH_LIGHT_MAX_BLADE_COUNT = 48;

	/** Floor under tudursvehiclemod$scaledRingCount()'s own result, for the same reason SEARCH_LIGHT_MIN_BLADE_COUNT exists. */
	private static final int SEARCH_LIGHT_MIN_RING_COUNT = 4;

	/** Per the same review as SEARCH_LIGHT_MAX_BLADE_COUNT's own doc: lowered from 600 to 250 for the same reason - the combination with the (now also lowered) blade cap is what actually determines worst-case vertex count for "dashes", and 600 alone still left that combination far too large. */
	private static final int SEARCH_LIGHT_MAX_RING_COUNT = 250;

	/** How many blade positions a light with this specific endRadius gets around its own far-end circumference - see SEARCH_LIGHT_REFERENCE_BLADE_SPACING's own doc. Circumference-based (2*pi*endRadius) rather than raw endRadius, since it is the actual distance around the far end - the space blades are competing for - that determines how many fit at a consistent spacing. Also scaled by VehicleModConfig.searchLightBladeCountMultiplier (see that field's own doc), read fresh here so a config change takes effect on the very next frame. */
	private static int tudursvehiclemod$scaledBladeCount(float endRadius) {
		float circumference = (float) (2 * Math.PI * Math.max(0.01f, endRadius));
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		double multiplier = config == null ? 1.0 : Math.max(0.0, config.searchLightBladeCountMultiplier);
		int raw = Math.round((float) (circumference / SEARCH_LIGHT_REFERENCE_BLADE_SPACING * multiplier));
		// Per a review triggered by a direct report of freezing and crashing at moderate distance within a search light's own range, with no clear cause: a hard SAFETY ceiling on the resulting count, distinct from the multiplier's own value (which was separately asked to have no ceiling - that request was about the CONFIGURED VALUE, not about the geometry it can produce). Without this, a light with an unusually large endRadius produces a proportionally large circumference and therefore an unbounded blade count, with nothing to stop it.
		return Math.min(SEARCH_LIGHT_MAX_BLADE_COUNT, Math.max(SEARCH_LIGHT_MIN_BLADE_COUNT, raw));
	}

	/** How many rings a light with this specific length gets along its own beam - see SEARCH_LIGHT_REFERENCE_RING_SPACING's own doc. The base spacing is divided by 3 here (tripling the generated ring count) rather than changing SEARCH_LIGHT_REFERENCE_RING_SPACING itself, keeping that constant's own doc (the reference light's derivation) accurate and this multiplication visible at the point it actually happens. Also scaled by VehicleModConfig.searchLightRingCountMultiplier on top (see that field's own doc), read fresh here for the same immediate-effect reason tudursvehiclemod$scaledBladeCount() reads its own multiplier fresh. */
	private static int tudursvehiclemod$scaledRingCount(float length) {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		double multiplier = config == null ? 1.0 : Math.max(0.0, config.searchLightRingCountMultiplier);
		float tripledSpacing = SEARCH_LIGHT_REFERENCE_RING_SPACING / 3f;
		int raw = Math.round((float) (Math.max(0.01f, length) / tripledSpacing * multiplier));
		// Per the same safety-ceiling review as tudursvehiclemod$scaledBladeCount()'s own doc.
		return Math.min(SEARCH_LIGHT_MAX_RING_COUNT, Math.max(SEARCH_LIGHT_MIN_RING_COUNT, raw));
	}

	/** The fixed fraction of each length-axis slice that is dash/band rather than gap, replacing the previous alpha-derived one. 0.2 approximates that previous scheme's own AVERAGE coverage across a typical beam (roughly 0.19 for a start/end alpha of 0x50/0x10), per the direct instruction to keep overall density about where it already was ("密度自体は現状とあまり変わらない程度に間引いてください") while removing the taper that made it uneven. */
	/** Short near the vehicle - see tudursvehiclemod$renderBladeCone()'s own "transverse || dashed" branch for the full reasoning: the fraction of a length-axis slice that is dash/band, at the FAR end (the value this reaches once scaled by distance along the beam - see that same branch's own multiplication by sliceNear). 0.2 is simply the far-end look that was already reported as fine and left unchanged; only the near end changes here. */
	private static final float SEARCH_LIGHT_DASH_LENGTH_FRACTION = 0.2f;

	/** "Dashed"'s own far-end length fraction, twice SEARCH_LIGHT_DASH_LENGTH_FRACTION (which "bands" keeps using unchanged, since only dashes were asked to change here). */
	private static final float SEARCH_LIGHT_DASHED_LENGTH_FRACTION = SEARCH_LIGHT_DASH_LENGTH_FRACTION * 2f;

	/** How strongly the repeat period is compressed near the vehicle. 2 (a plain square-law warp) packs periods in noticeably tighter near the start while leaving the far end's own already-liked spacing close to what it was. */
	private static final float SEARCH_LIGHT_DASH_DENSITY_EXPONENT = 2f;

	/** The ramp texture's own LAST TEXEL CENTRE (not the boundary itself) - see tudursvehiclemod$renderBladeCone()'s own doc. this used to be exactly 1.0f, the coordinate right at the texture's own edge rather than inside any texel. The solid cone (confirmed working) never samples that exact boundary - its own V values are interpolated strictly between 0 and 1 - so blades sampling precisely at the edge, and nothing else in this beam's own rendering doing so, points at the boundary itself as the difference. Without CLAMP addressing (never explicitly configured for this texture), V=1.0 wraps to row 0 (alpha 0) rather than staying on the last opaque row, which would read as "nothing rendered" regardless of geometry width - matching the report exactly. 255.5/256 lands squarely inside the last row's own texel instead. */
	private static final float SEARCH_LIGHT_RAMP_OPAQUE_V = 255.5f / 256f;

	/** Draws each affected block AGAIN, using that block's own model, through a vertex consumer that replaces the colour with the light's own.
	 *
	 * WHY THIS FIXES WHAT PATCHES COULDN'T: the overlay geometry IS the block's geometry, so a grass cross stays a cross and a fence stays a fence - there is no separate shape that can mismatch, which is exactly what made flat patches read as "a big plane laid over things". Vanilla does the same thing for block-breaking cracks, so re-rendering a block model as an overlay is an established approach rather than a novel one.
	 *
	 * Drawn at full bright so the overlay itself isn't dimmed by the very darkness it is meant to relieve, and slightly scaled up about the block's own centre so it sits just outside the original geometry instead of z-fighting with it. */
	private static void tudursvehiclemod$renderBlockOverlays(VehicleRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		// Back to a uniformly solid texture. Binding the block atlas only makes sense alongside a shader that takes alpha from the texture and colour from the vertex, and that shader cost shader-pack compatibility - see DitherCutoutLayers.ILLUMINATION_TRANSLUCENT's own doc. A solid texture keeps every block behaving identically, which is what fixed the earlier "arbitrary per-block-type" failure and must not regress.
		net.minecraft.util.Identifier texture = net.minecraft.util.Identifier.of(com.example.tudursvehiclemod.VehicleMod.MOD_ID, "textures/misc/illumination_solid_white.png");
		RenderLayer layer = DitherCutoutLayers.illuminationTranslucent(texture);
		var blockRenderManager = client.getBlockRenderManager();
		// Per an optimisation pass: ONE submitCustom for every overlay this vehicle has, not one per block. Each call is a separate draw submission, so at a typical range this was on the order of a thousand of them per vehicle per FRAME purely to draw the same layer over and over. Batching them costs nothing visually - they all share one layer and one texture, which is exactly the condition for merging - and the per-block transform that used to come from the enclosing MatrixStack is applied inside instead (block models get it via their own stack, the water quad by offsetting its own coordinates).
		queue.submitCustom(matrices, layer, (matrixEntry, vertexConsumer) -> {
			Matrix4f pose = matrixEntry.getPositionMatrix();
			// Defence in depth alongside SearchLightIllumination's own SEARCH_LIGHT_MAX_TOTAL_OVERLAYS. That cap bounds how many overlays are PRODUCED; this one bounds how many are actually DRAWN in a single frame, so even if the produced count ever exceeds expectations - a later change, or a config pushed past what was anticipated - the per-frame cost here stays bounded. This is the expensive end: renderDamage() below regenerates a block's whole model per call, and an unbounded number of those per frame was the confirmed cause of a reported render-thread hang.
			int drawn = 0;
			for (var overlay : state.activeBlockOverlays) {
				if (drawn >= BLOCK_OVERLAY_MAX_DRAWN_PER_FRAME) {
					break;
				}
				var pos = overlay.pos();
				var blockState = client.world.getBlockState(pos);
				var fluidState = client.world.getFluidState(pos);
				if (blockState.isAir() && fluidState.isEmpty()) {
					continue;
				}
				drawn++;
				int a = (overlay.colorArgb() >>> 24) & 0xFF;
				int r = (overlay.colorArgb() >> 16) & 0xFF;
				int g = (overlay.colorArgb() >> 8) & 0xFF;
				int b = overlay.colorArgb() & 0xFF;
				float blockX = (float) (pos.getX() - state.x);
				float blockY = (float) (pos.getY() - state.y);
				float blockZ = (float) (pos.getZ() - state.z);
				if (!blockState.isAir()) {
					// The block's own transform now lives on the stack handed to renderDamage() rather than on the shared outer one, which is what makes a single batched submission possible at all. Grown very slightly about the block's own centre so the overlay sits a hair outside the original surface rather than exactly coincident with it (which would z-fight).
					MatrixStack blockMatrices = new MatrixStack();
					blockMatrices.translate(blockX, blockY, blockZ);
					blockMatrices.translate(0.5, 0.5, 0.5);
					blockMatrices.scale(BLOCK_OVERLAY_SCALE, BLOCK_OVERLAY_SCALE, BLOCK_OVERLAY_SCALE);
					blockMatrices.translate(-0.5, -0.5, -0.5);
					// Every vertex the block model emits is rewritten to the light's own colour and full brightness - see tudursvehiclemod$tintedConsumer()'s own doc.
					blockRenderManager.renderDamage(blockState, pos, client.world, blockMatrices,
							tudursvehiclemod$tintedConsumer(vertexConsumer, matrixEntry, r, g, b, a));
				}
				// renderFluid() is deliberately not used. Which coordinate convention it emits in was never established - two different assumptions produced "displaced" and "invisible" respectively - and a fluid surface is FLAT, so a quad IS its correct shape. Drawn directly in the frame the block-model branch has already demonstrated positions correctly.
				if (!fluidState.isEmpty()) {
					// getHeight() is the fluid's own surface height within its block, 0-1, so a full source block sits near 1.0 and a flowing one lower - the quad follows that rather than assuming a full block.
					float surfaceY = blockY + fluidState.getHeight() - (float) WATER_OVERLAY_DEPTH_OFFSET;
					for (int corner = 0; corner < 4; corner++) {
						// Walks the quad's own perimeter over this block's 1x1 footprint: (0,0), (0,1), (1,1), (1,0).
						float cx = blockX + ((corner == 0 || corner == 1) ? 0f : 1f);
						float cz = blockZ + ((corner == 0 || corner == 3) ? 0f : 1f);
						vertexConsumer.vertex(pose, cx, surfaceY, cz)
								.color(r, g, b, a)
								.texture(SEARCH_LIGHT_BEAM_U, SEARCH_LIGHT_BEAM_U)
								.overlay(OverlayTexture.DEFAULT_UV)
								.light(SEARCH_LIGHT_BEAM_FULL_BRIGHT)
								.normal(matrixEntry, 0f, 1f, 0f);
					}
				}
			}
		});
	}

	/** How far below the fluid's own true surface its overlay is drawn - see the fluid branch's own doc above for why. Small enough not to look detached from the water it belongs to, large enough to clear the display-layer issue that put it in the wrong place before. */
	private static final double WATER_OVERLAY_DEPTH_OFFSET = 0.1;

	/** Per the diagnostic result narrowing the reported hang to the block-overlay path - see tudursvehiclemod$renderBlockOverlays()'s own doc: a per-frame ceiling on how many overlays are actually drawn, since each one costs a full blockRenderManager.renderDamage() (which regenerates that block's entire model). Deliberately set slightly above SearchLightIllumination's own SEARCH_LIGHT_MAX_TOTAL_OVERLAYS so that in normal operation this never actually engages - it exists purely so this expensive end can never be handed an unbounded list, whatever happens upstream. */
	private static final int BLOCK_OVERLAY_MAX_DRAWN_PER_FRAME = 1500;

	/** Wraps a VertexConsumer so every vertex a block model writes through it comes out in one fixed colour, at full brightness, sampling the beam's own ramp texture for its alpha - see tudursvehiclemod$renderBlockOverlays()'s own doc.
	 *
	 * The block model supplies POSITION and shape; everything about appearance is overridden here. Position is transformed by the supplied matrix entry because the model renders into its own fresh MatrixStack (it has no idea about this entity's own transform), so the two have to be combined at this point rather than earlier. */
	private static net.minecraft.client.render.VertexConsumer tudursvehiclemod$tintedConsumer(
			net.minecraft.client.render.VertexConsumer delegate, MatrixStack.Entry matrixEntry,
			int r, int g, int b, int a) {
		Matrix4f pose = matrixEntry.getPositionMatrix();
		// Leaving transparent parts (a plant's cross-shaped texture, glass, etc) untouched ("あくまでブロックの不透過テクスチャ部分のみを対象とし、透過部分に対しては影響を与えないようにすることは可能ですか"): earlier this wrapper discarded the block model's own UV entirely and substituted a fixed one, which meant the fragment shader's own texture(Sampler0, texCoord0) sampled the SAME single texel for every vertex regardless of where on the block's own texture that vertex actually was - there was no way for a transparent part of the texture to ever be sampled, so nothing could suppress the overlay there. Passing the model's own REAL UV through instead lets that same shader-level sampling do the actual work: a transparent texel's own near-zero alpha multiplies through exactly the way its colour does, suppressing the overlay there natively - no separate transparency check has to be written, because the vanilla-standard "colour = texture * vertexColor" multiply this pipeline already does is precisely that check.
		//
		// Buffers each element as it's set rather than emitting immediately on vertex(x,y,z): a VertexConsumer's own contract is that position often arrives first with the rest following before the implicit "next vertex" boundary, so submitting on vertex() (as this used to) meant every OTHER call - color, texture, overlay, light, normal - arrived too late to matter and was silently discarded. Buffering until whichever call happens to arrive last for this vertex, then flushing once all pending fields have real values, uses the model's own real texture coordinates instead of ignoring them.
		return new net.minecraft.client.render.VertexConsumer() {
			private float pendingX, pendingY, pendingZ;
			private float pendingU, pendingV;
			private boolean havePosition;
			private boolean haveTexture;

			private void flushIfReady() {
				if (havePosition && haveTexture) {
					delegate.vertex(pose, pendingX, pendingY, pendingZ)
							.color(r, g, b, a)
							.texture(pendingU, pendingV)
							.overlay(OverlayTexture.DEFAULT_UV)
							.light(SEARCH_LIGHT_BEAM_FULL_BRIGHT)
							.normal(matrixEntry, 0f, 1f, 0f);
					havePosition = false;
					haveTexture = false;
				}
			}

			@Override
			public net.minecraft.client.render.VertexConsumer vertex(float x, float y, float z) {
				pendingX = x;
				pendingY = y;
				pendingZ = z;
				havePosition = true;
				flushIfReady();
				return this;
			}

			@Override
			public net.minecraft.client.render.VertexConsumer texture(float u, float v) {
				pendingU = u;
				pendingV = v;
				haveTexture = true;
				flushIfReady();
				return this;
			}

			// Colour, overlay, light and normal are still fully overridden above (full bright, the light's own tint) - only the texture COORDINATE is taken from the model, not its lighting or vertex colour.
			@Override
			public net.minecraft.client.render.VertexConsumer color(int red, int green, int blue, int alpha) {
				return this;
			}

			@Override
			public net.minecraft.client.render.VertexConsumer color(int argb) {
				return this;
			}

			@Override
			public net.minecraft.client.render.VertexConsumer overlay(int u, int v) {
				return this;
			}

			@Override
			public net.minecraft.client.render.VertexConsumer light(int u, int v) {
				return this;
			}

			@Override
			public net.minecraft.client.render.VertexConsumer normal(float x, float y, float z) {
				return this;
			}

			@Override
			public net.minecraft.client.render.VertexConsumer lineWidth(float width) {
				delegate.lineWidth(width);
				return this;
			}
		};
	}

	/** The largest stride reached at the very tip (rawNear=1). 6 means the tip keeps roughly 1 in 7 of the parity-surviving blades (about 1 in 14 of the light's own full angular resolution) - sparse enough to read as fading away, without dropping to so few that the remaining dashes look like isolated specks rather than a tapering beam. */
	/** The floor on how much of the full blade count survives at the very tip (rawNear=1). Kept just above zero rather than allowed to reach it, so the tip fades to a faint trickle of dashes rather than a hard, sudden absence. */
	/** How strongly width is pulled down, growing with distance. Verified numerically against representative authored alpha values (0x50 near, 0x10 far): at 100, width's own peak lands around rawNear~0.1 (close to the unavoidable near-zero base) and decreases for the rest of the beam, rather than sitting prominently mid-beam as lower values did. */
	private static final float SEARCH_LIGHT_DASH_WIDTH_SHRINK_STRENGTH = 100f;

	/** How much of the authored alpha is subtracted at the very tip (t=1), scaling linearly from 0 at the vehicle itself. 0.6 leaves a faint residual glow at the tip rather than a hard cutoff to fully zero, while still reading as a clear fade rather than a subtle one - since it stacks with whatever the author's own end alpha already was, a low authored end alpha combined with this can still reach zero well before the actual tip.
	 *
	 * Only used by the closed "beam"/"dynamic_light" cone (see that shape's own call site). "blades"/"bands"/"dashes" express their own translucency as geometric coverage rather than alpha - a ramp-texture row fixed at fully opaque, by design (see SEARCH_LIGHT_RAMP_OPAQUE_V's own doc) - so multiplying an alpha value onto them would do nothing observable there; giving them an equivalent fade would mean shrinking their own coverage fraction towards the tip instead, which is a large enough change to warrant its own separate pass rather than folding it in here. */
	private static final float SEARCH_LIGHT_FAR_END_FALLOFF = 0.6f;

	/** How much a block overlay is grown about the block's own centre - see tudursvehiclemod$renderBlockOverlays()'s own doc. Just enough to clear the original surface without the overlay visibly outgrowing the block. */
	private static final float BLOCK_OVERLAY_SCALE = 1.002f;

	private static void tudursvehiclemod$renderOneSearchLightCone(MatrixStack matrices, OrderedRenderCommandQueue queue,
			RenderLayer layer, int light, VehicleRenderState.LightBeam beam) {
		int startA = (beam.startColorArgb() >>> 24) & 0xFF;
		int startR = (beam.startColorArgb() >> 16) & 0xFF;
		int startG = (beam.startColorArgb() >> 8) & 0xFF;
		int startB = beam.startColorArgb() & 0xFF;
		int endA = (beam.endColorArgb() >>> 24) & 0xFF;
		int endR = (beam.endColorArgb() >> 16) & 0xFF;
		int endG = (beam.endColorArgb() >> 8) & 0xFF;
		int endB = beam.endColorArgb() & 0xFF;
		// Applied once, here, at the single point every shape branch below reads length/endRadius from - so beam/blades/bands/dashes all shrink together with no separate handling needed per shape. endRadius is scaled by the SAME factor as length, keeping the cone's own half-angle (and therefore its visual proportions) unchanged - shortening it produces a smaller, similar cone rather than a flatter or narrower one.
		float displayDistance = tudursvehiclemod$coneDisplayDistanceMultiplier();
		// A defensive bound, like SearchLightIllumination's own SEARCH_LIGHT_MAX_OVERLAY_RADIUS_AT_RANGE (see that constant's own doc): length/endRadius are addon-authored and unvalidated at the asset level, and feed directly into this cone's own vertex coordinates (cos(angle)*radius etc), so an extreme value risks submitting absurdly large or non-finite vertex data to the GPU. Clamped to the same generous 2000-block ceiling, well beyond anything a reasonably-authored light needs.
		float length = Math.min(2000f, beam.length() * displayDistance);
		float endRadius = Math.min(2000f, beam.endRadius() * displayDistance);
		queue.submitCustom(matrices, layer, (matrixEntry, vertexConsumer) -> {
			Matrix4f pose = matrixEntry.getPositionMatrix();
			// Per an optimisation review of this cone, which double-sided every quad (8 vertices) on the assumption the camera sits inside it where back-face culling would otherwise hide half of it: this is a plain field read - see DitherCutoutLayers.CURRENT_MODE_CULLS's own doc for why a static final is correct here (translucencyMode itself requires a restart to change).
			boolean needsDoubleSided = DitherCutoutLayers.CURRENT_MODE_CULLS;
			// The shape comes straight from searchLightMode - see that field's own doc.
			//
			// NOTE for "beam"/"dynamic_light": their cone's translucency comes from alpha, which "dither_texture" translucency mode binarises at load time, so it renders solid there. "blades"/"bands" express translucency as geometric coverage instead and therefore look correct in every translucency mode.
			String mode = tudursvehiclemod$searchLightMode();
			if ("blades".equals(mode)) {
				tudursvehiclemod$renderBladeCone(vertexConsumer, pose, matrixEntry, light, length, endRadius,
						startR, startG, startB, startA, endR, endG, endB, endA, false, false);
				return;
			}
			if ("bands".equals(mode)) {
				tudursvehiclemod$renderBladeCone(vertexConsumer, pose, matrixEntry, light, length, endRadius,
						startR, startG, startB, startA, endR, endG, endB, endA, true, false);
				return;
			}
			if ("dashes".equals(mode)) {
				tudursvehiclemod$renderBladeCone(vertexConsumer, pose, matrixEntry, light, length, endRadius,
						startR, startG, startB, startA, endR, endG, endB, endA, false, true);
				return;
			}
			// "Beam" specifically (not "dynamic_light") auto-falls-back to "dashes" here when translucencyMode is "dither_texture", since a solid cone renders fully opaque under that mode (its fade is alpha-based, which that mode binarises at load) - so the out-of-the-box combination now looks right without requiring the person to also change searchLightMode away from its own default. Explicitly choosing "blades"/"bands"/"dashes" via searchLightMode - handled by the three branches above - always wins over this; this only fires when NONE of them were chosen and the mode fell through as "beam" (or an unrecognised value, defaulting to "beam"'s own behaviour) toward the solid cone below.
			if ("beam".equals(mode) && "dither_texture".equals(tudursvehiclemod$translucencyModeSetting())) {
				tudursvehiclemod$renderBladeCone(vertexConsumer, pose, matrixEntry, light, length, endRadius,
						startR, startG, startB, startA, endR, endG, endB, endA, false, true);
				return;
			}
			for (int ring = 0; ring < SEARCH_LIGHT_CONE_RINGS; ring++) {
				// The beam is now subdivided ALONG its own length too, not just around its circumference. A single apex-to-ring jump forced the entire start-to-end colour change to be interpolated across one enormous triangle each, which is what made the gradient read as flat bands rather than a fade.
				float nearT = ring / (float) SEARCH_LIGHT_CONE_RINGS;
				float farT = (ring + 1) / (float) SEARCH_LIGHT_CONE_RINGS;
				float nearZ = length * nearT;
				float farZ = length * farT;
				float nearRadius = endRadius * nearT;
				float farRadius = endRadius * farT;
				int nearR = (int) net.minecraft.util.math.MathHelper.lerp(nearT, startR, endR);
				int nearG = (int) net.minecraft.util.math.MathHelper.lerp(nearT, startG, endG);
				int nearB = (int) net.minecraft.util.math.MathHelper.lerp(nearT, startB, endB);
				int farR = (int) net.minecraft.util.math.MathHelper.lerp(farT, startR, endR);
				int farG = (int) net.minecraft.util.math.MathHelper.lerp(farT, startG, endG);
				int farB = (int) net.minecraft.util.math.MathHelper.lerp(farT, startB, endB);
				// A SECOND, purely position-based falloff multiplied onto whatever the authored start/end alpha already produces. Applied here specifically - the closed "beam"/"dynamic_light" cone - since alpha reaches the screen directly for this shape (see SEARCH_LIGHT_FAR_END_FALLOFF's own doc for why "blades"/"bands"/"dashes" are a different case). The authored gradient (startColorArgb -> endColorArgb) still runs independently; this only pulls the very end of it down further, toward zero, rather than replacing it.
				float nearFalloff = 1f - SEARCH_LIGHT_FAR_END_FALLOFF * nearT;
				float farFalloff = 1f - SEARCH_LIGHT_FAR_END_FALLOFF * farT;
				int nearA = (int) (net.minecraft.util.math.MathHelper.lerp(nearT, startA, endA) * nearFalloff);
				int farA = (int) (net.minecraft.util.math.MathHelper.lerp(farT, startA, endA) * farFalloff);
				for (int segment = 0; segment < SEARCH_LIGHT_CONE_SEGMENTS; segment++) {
					float angleA = (float) (2 * Math.PI * segment / SEARCH_LIGHT_CONE_SEGMENTS);
					float angleB = (float) (2 * Math.PI * (segment + 1) / SEARCH_LIGHT_CONE_SEGMENTS);
					float cosA = (float) Math.cos(angleA);
					float sinA = (float) Math.sin(angleA);
					float cosB = (float) Math.cos(angleB);
					float sinB = (float) Math.sin(angleB);
					// Every vertex samples the SAME single texel rather than stretching the whole texture across each wedge. The texture is a plain white square used only to satisfy the render layer - all actual colour comes from the per-vertex colour above - so spreading its UVs across the geometry contributed nothing but that visible radial seam pattern, which the dithered layers then made worse by giving the stretched texels a mesh of their own.
					tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, cosA * nearRadius, sinA * nearRadius, nearZ, nearR, nearG, nearB, nearA / 255f);
					tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, cosA * farRadius, sinA * farRadius, farZ, farR, farG, farB, farA / 255f);
					tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, cosB * farRadius, sinB * farRadius, farZ, farR, farG, farB, farA / 255f);
					// Always four vertices for this half: the QUADS draw mode this beam's own layer inherits (see the layer's own doc) needs the fourth corner to complete a genuine quad regardless of anything else.
					tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, cosB * nearRadius, sinB * nearRadius, nearZ, nearR, nearG, nearB, nearA / 255f);

					// Every mode now culls back faces (see DitherCutoutLayers.CURRENT_MODE_CULLS's own doc), and this cone's own apex sits at the vehicle itself - so the camera is essentially always inside it, making every outward-facing quad back-facing. This reversed-winding half restores the cone's own always-visible-from-inside appearance under every mode now, not only "translucent" as before that change.
					if (needsDoubleSided) {
						tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, cosB * nearRadius, sinB * nearRadius, nearZ, nearR, nearG, nearB, nearA / 255f);
						tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, cosB * farRadius, sinB * farRadius, farZ, farR, farG, farB, farA / 255f);
						tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, cosA * farRadius, sinA * farRadius, farZ, farR, farG, farB, farA / 255f);
						tudursvehiclemod$emitBeamVertex(vertexConsumer, pose, matrixEntry, light, cosA * nearRadius, sinA * nearRadius, nearZ, nearR, nearG, nearB, nearA / 255f);
					}
				}
			}
		});
	}

	/** One search light beam vertex. this vertex's own ALPHA is carried as the V texture coordinate into the ramp texture (see tudursvehiclemod$renderSearchLightBeamCones()'s own doc), rather than as vertex colour alpha, because the two dither pipelines only ever test texture alpha. The vertex colour therefore carries RGB only and is left fully opaque - putting the alpha in both places would apply the fade twice over in ENTITY_TRANSLUCENT mode, which is the one mode that does honour vertex alpha.
	 *
	 * U stays fixed at the ramp's own centre column: the ramp varies only vertically, and spreading U across the geometry is exactly what produced the radial "shuttlecock" seam pattern reported earlier. */
	private static void tudursvehiclemod$emitBeamVertex(net.minecraft.client.render.VertexConsumer vertexConsumer, Matrix4f pose,
			MatrixStack.Entry matrixEntry, int light, float x, float y, float z, int r, int g, int b, float rampV) {
		vertexConsumer.vertex(pose, x, y, z)
				.color(r, g, b, 255)
				.texture(SEARCH_LIGHT_BEAM_U, rampV)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(light)
				.normal(matrixEntry, 0f, 0f, 1f);
	}

	/** Segments AROUND the beam's own circumference - see tudursvehiclemod$renderOneSearchLightCone()'s own doc. */
	private static final int SEARCH_LIGHT_CONE_SEGMENTS = 16;

	/** Bands ALONG the beam's own length - see tudursvehiclemod$renderOneSearchLightCone()'s own doc for why subdividing this way was needed at all. 8 is enough for the start-to-end colour fade to read as a gradient rather than as flat bands, without multiplying the vertex count unreasonably (8 x 16 wedges). */
	private static final int SEARCH_LIGHT_CONE_RINGS = 8;

	/** The ramp texture's own centre column - the only U every beam vertex uses, since the ramp varies vertically only. See tudursvehiclemod$emitBeamVertex()'s own doc for why V is the interesting coordinate here instead. */
	private static final float SEARCH_LIGHT_BEAM_U = 0.5f;

	/** The lightmap coordinate a search light beam is drawn at - maximum block AND sky light, so the surrounding darkness never dims it. A beam emits light rather than receiving it, and at night (the only time a search light is actually used) the ambient lightmap is near zero, which multiplied the beam's own per-vertex colour down to nothing. Same constant and same reasoning as ExplosionFlashParticle's own FULL_BRIGHT. */
	private static final int SEARCH_LIGHT_BEAM_FULL_BRIGHT = 0xF000F0;
}
