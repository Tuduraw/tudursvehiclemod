package com.example.tudursvehiclemod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;

/** One small, invisible piece of a vehicle's own multi-box "attack hitbox" -
 * Several of these together (one per retained,
 * non-decorative connected mesh component - see
 * com.example.tudursvehiclemod.asset.ServerObjModelHitboxes's own doc)
 * approximate a vehicle's own actual shape, rather than the vehicle's own
 * single, crude EntityDimensions-derived box swallowing its entire extent
 * regardless of shape.
 *
 * Deliberately NOT a full physics entity: no block collision (noClip is set
 * true in the constructor), no movement of its own at all - its own
 * position/rotation is instead set EXTERNALLY, every tick, by its owning
 * AbstractVehicleEntity (see that class's own
 * tudursvehiclemod$updateHitboxParts() doc), transforming this hitbox's own
 * fixed model-local offset through the vehicle's own current scale/
 * rotation/world position. This lets ordinary vanilla hit-detection
 * (arrows, tridents, any other projectile's own entity-vs-entity collision
 * check, which is entirely independent of block collision) treat each of
 * these exactly like a normal, small, solid-shaped target - it's only ever
 * the DAMAGE that gets redirected (via this class's own damage() override)
 * to the owning vehicle, never actually applied to this entity itself. */
public class VehicleHitboxEntity extends Entity {

	private AbstractVehicleEntity owner;
	private float boxWidth = 1.0f;
	private float boxHeight = 1.0f;
	/** This hitbox's own fixed offset from the owning vehicle's own origin, in the vehicle's own LOCAL (unrotated) space, already scaled by def.scale() - see AbstractVehicleEntity's own tudursvehiclemod$updateHitboxParts() doc for how this gets transformed into a world position every tick. */
	private float localOffsetX;
	private float localOffsetY;
	private float localOffsetZ;

	public VehicleHitboxEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setInvisible(true);
	}

	/** Set once, right after spawning - see AbstractVehicleEntity's own tudursvehiclemod$updateHitboxParts() doc. */
	public void tudursvehiclemod$setOwner(AbstractVehicleEntity owner) {
		this.owner = owner;
	}

	public AbstractVehicleEntity tudursvehiclemod$getOwner() {
		return this.owner;
	}

	/** Set once, right after spawning, from this hitbox's own ServerObjModelHitboxes.Box (already scaled by the vehicle's own def.scale()) - see AbstractVehicleEntity's own tudursvehiclemod$updateHitboxParts() doc. */
	public void tudursvehiclemod$setBoxSize(float width, float height) {
		this.boxWidth = Math.max(0.1f, width);
		this.boxHeight = Math.max(0.1f, height);
		this.calculateDimensions();
	}

	@Override
	public EntityDimensions getDimensions(EntityPose pose) {
		return EntityDimensions.changing(this.boxWidth, this.boxHeight);
	}

	/** Set once, right after spawning - see this field's own doc. */
	public void tudursvehiclemod$setLocalOffset(double x, double y, double z) {
		this.localOffsetX = (float) x;
		this.localOffsetY = (float) y;
		this.localOffsetZ = (float) z;
	}

	/** A FRESH Vector3f each call (since callers typically mutate it in place via a rotation transform) - never the same instance twice. */
	public org.joml.Vector3f tudursvehiclemod$getLocalOffset() {
		return new org.joml.Vector3f(this.localOffsetX, this.localOffsetY, this.localOffsetZ);
	}

	/** Always hittable as long as the owning vehicle itself still legitimately exists and isn't already destroyed - matches the owning vehicle's own canHit() semantics. */
	@Override
	public boolean canHit() {
		return this.owner != null && !this.owner.isRemoved()
				&& !this.owner.tudursvehiclemod$isDestroyed() && !this.isRemoved();
	}

	@Override
	public boolean isAttackable() {
		return this.canHit();
	}

	/** Redirects ALL damage to the owning vehicle - this entity itself never actually takes or tracks damage/health of its own. */
	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (this.owner == null || this.owner.isRemoved()) {
			return false;
		}
		return this.owner.damage(world, source, amount);
	}

	/** No movement/physics of its own at all - position is set externally every tick by the owning vehicle. Still runs the base Entity tick (age counter, etc.) so vanilla bookkeeping stays sane. */
	@Override
	public void tick() {
		this.baseTick();
		if (this.owner == null || this.owner.isRemoved() || this.owner.tudursvehiclemod$isDestroyed()) {
			this.discard();
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		// No synced state of its own - position/rotation sync alone (handled automatically by vanilla's own entity tracking) is all a client needs to render/predict against this correctly, and there's nothing else about it worth syncing.
	}

	@Override
	protected void readCustomData(ReadView view) {
		// No persistent state of its own - if the world reloads, the owning vehicle's own tick() will simply spawn a fresh set of these again (see AbstractVehicleEntity's own tudursvehiclemod$updateHitboxParts() doc), rather than this entity itself needing to survive a save/load cycle.
	}

	@Override
	protected void writeCustomData(WriteView view) {
	}
}
