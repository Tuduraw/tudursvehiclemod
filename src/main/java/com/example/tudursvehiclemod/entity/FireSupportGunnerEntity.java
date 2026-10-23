package com.example.tudursvehiclemod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Arm;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The unseen crew a Fire Support Center (block.FireSupportCenterBlockEntity) puts into a linked vehicle's gunner seat to lay its guns.
 *
 * <p>A vehicle's turrets follow whoever sits in the weapon's seat - its yaw and pitch (AbstractVehicleEntity.getWeaponAimYaw()/getWeaponAimPitch()) - so laying a gun on a target point means seating someone there and turning them to face the firing solution. This entity is that someone: the center turns it every tick, and the turret (turret_rotation_speed, aim limits, settling before it fires - all as for a player gunner) and the client's view of the turret (an occupant's rotation is synced like any entity's) follow on their own.
 *
 * <p>Its own class, not a DummyPilotEntity: a dummy pilot is taken to BE the pilot wherever it sits (AbstractVehicleEntity.tudursvehiclemod$getSeatOccupant(0) returns any DummyPilotEntity aboard), which a gunner in seat 3 must not be.
 *
 * <p>Invisible, can't be hurt, doesn't move or collide. It leaves on its own (discards itself) as soon as its center stops renewing it - OWNER_REFRESH_TIMEOUT_TICKS without tudursvehiclemod$refresh() - so a gunner can never outlive the center that placed it: the center deactivated, broken, unloaded, or the vehicle unlinked, whatever the path. Its owner is kept in a command tag (saved with the entity by vanilla), so one loaded back from a save is still recognized by the center that owns it. */
public class FireSupportGunnerEntity extends LivingEntity {

	/** Prefix of the command tag naming the owning center: "tudursvehiclemod_fire_support:x,y,z". */
	private static final String OWNER_TAG_PREFIX = "tudursvehiclemod_fire_support:";

	/** How long a gunner stays without its center renewing it (tudursvehiclemod$refresh()). Longer than a tick or two so a center that skips a tick doesn't make it blink out of the seat, short enough that a stale one is gone before anyone could want the seat. */
	private static final int OWNER_REFRESH_TIMEOUT_TICKS = 40;

	/** Ticks since the center last renewed this gunner. Not saved: one loaded from disk gets the full timeout to be claimed again. */
	private int ticksSinceRefresh;

	public FireSupportGunnerEntity(EntityType<? extends LivingEntity> type, World world) {
		super(type, world);
		this.setInvisible(true);
		this.setNoGravity(true);
		this.setSilent(true);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return LivingEntity.createLivingAttributes()
				.add(EntityAttributes.MAX_HEALTH, 20.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.0);
	}

	/** Marks this gunner as belonging to the center at ownerPos (replacing any earlier owner). */
	public void tudursvehiclemod$setOwner(BlockPos ownerPos) {
		for (String tag : java.util.List.copyOf(this.getCommandTags())) {
			if (tag.startsWith(OWNER_TAG_PREFIX)) {
				this.removeCommandTag(tag);
			}
		}
		this.addCommandTag(OWNER_TAG_PREFIX + ownerPos.getX() + "," + ownerPos.getY() + "," + ownerPos.getZ());
	}

	/** Whether this gunner belongs to the center at ownerPos. */
	public boolean tudursvehiclemod$isOwnedBy(BlockPos ownerPos) {
		return this.getCommandTags().contains(OWNER_TAG_PREFIX + ownerPos.getX() + "," + ownerPos.getY() + "," + ownerPos.getZ());
	}

	/** Called by the owning center every tick it still wants this gunner in its seat. */
	public void tudursvehiclemod$refresh() {
		this.ticksSinceRefresh = 0;
	}

	/** Faces the given direction - the yaw/pitch the vehicle's weapon tracking reads (see this class's doc). */
	public void tudursvehiclemod$lookAt(float yaw, float pitch) {
		this.setYaw(yaw);
		this.setHeadYaw(yaw);
		this.setBodyYaw(yaw);
		this.setPitch(pitch);
		this.lastYaw = yaw;
		this.lastPitch = pitch;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getEntityWorld().isClient()) {
			return;
		}
		if (!(this.getVehicle() instanceof AbstractVehicleEntity vehicle) || vehicle.tudursvehiclemod$isDestroyed()
				|| ++this.ticksSinceRefresh > OWNER_REFRESH_TIMEOUT_TICKS) {
			this.stopRiding();
			this.discard();
		}
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void pushAwayFrom(net.minecraft.entity.Entity entity) {
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean canMoveVoluntarily() {
		return false;
	}

	@Override
	public net.minecraft.item.ItemStack getEquippedStack(net.minecraft.entity.EquipmentSlot slot) {
		return net.minecraft.item.ItemStack.EMPTY;
	}

	@Override
	public void equipStack(net.minecraft.entity.EquipmentSlot slot, net.minecraft.item.ItemStack stack) {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
