package com.example.tudursvehiclemod.mixin;

import com.example.tudursvehiclemod.entity.SubmarineEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's own onVehicleMove() handler treats
 * ANY entity a player is the controlling passenger of as a client-authoritative
 * vehicle (same mechanism as boats/horses/pigs) - it takes the position the
 * CLIENT reports in the packet and directly overwrites the vehicle's own
 * server-side position with it, every time the client sends one.
 *
 * An EARLIER version of this mixin applied that protection to EVERY
 * AbstractVehicleEntity, on the theory that any vehicle computing its own
 * movement entirely server-side would have the same problem. A direct
 * comparison against an old project backup from before this mixin
 * existed at all: aircraft (and presumably car/ship/helicopter) worked
 * correctly with NO such protection whatsoever, and their own movement-
 * application code is unchanged since then - so vanilla's default
 * behavior was never actually a problem for their own smooth, continuous
 * flight/driving/sailing physics; a client's own naive dead-reckoning
 * isn't far enough off from that kind of steady, mostly-inertial motion
 * to cause a visible issue. It was SubmarineEntity's own hold-key
 * vertical ascend/descend mechanic specifically - sudden, discontinuous
 * vertical movement a naive client-side prediction doesn't anticipate at
 * all - that actually caused the original problem this mixin was written
 * for, but scoping the fix to ALL vehicles instead of just submarines
 * ended up breaking something else (see the second half of this doc).
 *
 * That's also why this is now scoped to SubmarineEntity specifically
 * rather than AbstractVehicleEntity generally: there were reports of
 * aircraft appearing to fly smoothly then suddenly "crash" at a
 * completely different position than where they visually appeared to be,
 * a further investigation found that vanilla's own player-riding-a-
 * vehicle networking ALREADY assumes the riding player's own client is
 * authoritative for that vehicle's position, and specifically does NOT
 * send that same player normal entity-tracking position updates for the
 * vehicle they're riding - the ONLY mechanism that keeps the rider's
 * client informed of the server's real position for a vehicle is
 * onVehicleMove()'s own internal "if the client's reported movement fails
 * a sanity/speed check, send a corrective resync back" safety net.
 * Applying this mixin's protection to aircraft (which never needed it)
 * broke that safety net for them too, meaning the piloting player's own
 * client had no way left to ever learn the server's real, physics-
 * authoritative position for their aircraft specifically - it would just
 * keep locally believing its own increasingly wrong guess, completely
 * unaware the server had already diverged (through a stall, a collision,
 * anything), until some unrelated full resync eventually snapped the view
 * to the truth all at once, looking exactly like a sudden, unexplained
 * crash somewhere else. Scoping this back down to just submarines - the
 * one vehicle type that actually needs it - restores every other vehicle
 * to its original, working, mixin-free behavior.
 *
 * For submarines, this still lets onVehicleMove() run to completion as
 * normal - including whatever internal resync-the-client logic it decides
 * to run - and only forcibly restores the vehicle's own true PRE-packet
 * position/rotation afterward, rather than cancelling the method outright
 * (which would ALSO have silently disabled that same safety net for
 * submarines specifically).
 *
 * A PILOTED vehicle stopped following a
 * carrier's own runway deck the instant a passenger boarded (confirmed via
 * diagnostic logging that the vehicle's own updateVehicleMovement() left
 * its position completely untouched every tick, yet the position still
 * reverted to the exact value it had at boarding time between one tick's
 * carry and the next): the SAME root cause documented above for
 * submarines' sudden vertical movement applies equally to any vehicle
 * being carried by a runway deck - the carry's own server-side
 * displacement is likewise invisible to the riding player's own client-
 * side dead-reckoning, so onVehicleMove()'s overwrite-with-the-client's-
 * stale-position behavior silently undoes the carry every single tick.
 * Extended to cover any AbstractVehicleEntity currently flagged as
 * carried-this-tick (see RUNWAY_CARRY_ACTIVE's own doc) rather than
 * AbstractVehicleEntity generally, specifically to avoid reintroducing
 * the aircraft divergence-resync regression described above for the
 * general (non-carried) case.
 *
 * Even with the above fix, a piloted carried
 * vehicle still stayed visually frozen for the riding player the entire
 * time they remained mounted - only snapping to its true (correctly
 * carried) position at the moment of dismount, when a normal,
 * unsuppressed entity sync finally occurred: restoring the server's true
 * position alone corrects the SERVER's own authoritative state, but does
 * nothing to inform the RIDER's client of it. Relying on onVehicleMove()'s
 * own internal "resync if the client's reported movement fails a sanity
 * check" safety net (as the submarine case above does, by letting the
 * method run to completion) turned out not to be reliable here - a
 * carrier's own gradual, small-per-tick displacement apparently never
 * crosses whatever threshold that internal check uses, unlike a
 * submarine's sudden vertical movement.
 *
 * Rapid forward/backward jitter began
 * for the carried case as soon as the mixin's protection was extended to
 * runway-carried vehicles at all - present even BEFORE any explicit
 * resync packet was ever added, i.e. purely from letting onVehicleMove()
 * run to completion (as the submarine case does) and restoring position
 * afterward: whatever onVehicleMove() does internally while processing
 * the client's report - which, for a carried vehicle, is ALWAYS
 * stale/wrong relative to the carry the client has no way to know about -
 * was itself a source of jitter, independent of whatever position was
 * ultimately left standing afterward. For the carried case specifically,
 * onVehicleMove() is cancelled OUTRIGHT at HEAD rather than left to run
 * and having its result corrected afterward - vanilla never touches the
 * vehicle's position/rotation for this case at all, and an explicit
 * VehicleMoveS2CPacket carrying the vehicle's own current (already
 * correct, server-authoritative) position/rotation is sent immediately in
 * the same injection to keep the rider's client informed, without relying
 * on vanilla's own internal safety net. The submarine case is
 * intentionally left exactly as it was (captured at HEAD, method still
 * runs to completion, restored at RETURN via setPosition()+setYaw()+
 * setPitch() - NOT refreshPositionAndAngles(), which also resets
 * prevX/Y/Z and so would eliminate render interpolation for every OTHER
 * client observing the submarine if called every tick) since that
 * behavior was already confirmed working before this rework and isn't
 * known to share the carried-vehicle's own problem.
 *
 * Even after the above refinements (outright
 * cancellation, position-only resync, no explicit velocity packet), a
 * fine jitter was STILL observed for the runway-carried case, and that it
 * persisted even with this mixin's own runway-carried protection removed
 * entirely (implicating something else, older, upstream of this mixin
 * altogether - see IMPLEMENTATION_NOTES.md's own "パーツ回転" history):
 * this mixin's own runway-carried extension was re-applied essentially
 * unchanged from its last-known-working (following confirmed) state, on
 * the working theory that the jitter's true source lies elsewhere and
 * this mixin's own carried-vehicle protection remains necessary regardless
 * for the "doesn't follow at all while mounted" issue it was written to
 * fix. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class VehicleMoveIgnoreMixin {

	@Shadow
	public ServerPlayerEntity player;

	@Unique
	private SubmarineEntity tudursvehiclemod$capturedVehicle;
	@Unique
	private double tudursvehiclemod$preX, tudursvehiclemod$preY, tudursvehiclemod$preZ;
	@Unique
	private float tudursvehiclemod$preYaw, tudursvehiclemod$prePitch;

	@Inject(method = "onVehicleMove", at = @At("HEAD"), cancellable = true)
	private void tudursvehiclemod$interceptVehicleMove(VehicleMoveC2SPacket packet, CallbackInfo ci) {
		Entity vehicle = this.player.getVehicle();
		if (vehicle instanceof SubmarineEntity submarine) {
			this.tudursvehiclemod$capturedVehicle = submarine;
			this.tudursvehiclemod$preX = vehicle.getX();
			this.tudursvehiclemod$preY = vehicle.getY();
			this.tudursvehiclemod$preZ = vehicle.getZ();
			this.tudursvehiclemod$preYaw = vehicle.getYaw();
			this.tudursvehiclemod$prePitch = vehicle.getPitch();
			return;
		}
		if (vehicle instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity abstractVehicle
				&& abstractVehicle.tudursvehiclemod$isCarriedByRunwayLastTick()) {
			ci.cancel();
			this.player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket(
					vehicle.getEntityPos(), vehicle.getYaw(), vehicle.getPitch()));
		}
	}

	@Inject(method = "onVehicleMove", at = @At("RETURN"))
	private void tudursvehiclemod$restoreServerPosition(VehicleMoveC2SPacket packet, CallbackInfo ci) {
		SubmarineEntity vehicle = this.tudursvehiclemod$capturedVehicle;
		this.tudursvehiclemod$capturedVehicle = null;
		if (vehicle == null) {
			return;
		}
		vehicle.setPosition(this.tudursvehiclemod$preX, this.tudursvehiclemod$preY, this.tudursvehiclemod$preZ);
		vehicle.setYaw(this.tudursvehiclemod$preYaw);
		vehicle.setPitch(this.tudursvehiclemod$prePitch);
	}
}
