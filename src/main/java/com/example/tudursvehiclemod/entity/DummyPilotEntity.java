package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.asset.DummyPilotSkinRegistry;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.WeaponDefinition;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/** A Steve-shaped figure that sits in a drone-controlled vehicle's own pilot seat so the vehicle doesn't visibly fly/drive itself with an empty cockpit, with a selectable (and addon-extensible - see DummyPilotSkinRegistry) skin, and which automatically engages hostile mobs with whatever weapons that vehicle actually has.
 *
 * A LivingEntity rather than a plain Entity specifically so vanilla's own BipedEntityModel/PlayerEntityModel rendering path applies unchanged (that model hierarchy is built around LivingEntityRenderer) - this is a rendering-shape decision, not a "should behave like a mob" one. It has NO goals/brain/AI of vanilla's own kind at all: it never pathfinds, never moves under its own power, and never leaves its seat on its own. Everything it does is driven from tick() below while riding.
 *
 * Deliberately NOT damageable/killable (see damage()/isInvulnerableTo()): it is a cosmetic-plus-gunner stand-in wholly owned by the Drone Center that spawned it, and is removed when that center says so (see tudursvehiclemod$isDummyPilotEnabled()'s own dispatch in DroneCenterBlockEntity). Letting a stray arrow delete it would leave the vehicle mid-route with the empty cockpit this feature exists to avoid, and would need its own respawn bookkeeping to recover from. */
public class DummyPilotEntity extends LivingEntity {

	/** The skin id (see DummyPilotSkinRegistry for the id-not-path rationale) this particular dummy pilot renders with. A DataTracker field because rendering is client-side and the choice is made server-side (from the owning Drone Center's own config) - the exact same reasoning as every other render-affecting vehicle field in this project. */
	private static final TrackedData<String> SKIN_ID =
			DataTracker.registerData(DummyPilotEntity.class, TrackedDataHandlerRegistry.STRING);

	/** How far (blocks) this pilot looks for a hostile mob to engage - mirrored from the owning DroneCenterBlockEntity's own current setting, exactly like combatWeaponIndex/combatAttackStartAltitude/combatAttackStopAltitude below (see that field's own doc). Default (64.0) matches this field's own previous hardcoded value, before it became configurable - so a pilot that somehow ticks even once before its first settings push behaves exactly as it always did. */
	private double combatSearchRange = 64.0;
	/** How often (ticks) this pilot re-picks a target, rather than every tick - target selection scans an area for entities, which is the one genuinely non-trivial cost here, and a target one second stale is entirely fine for this purpose. */
	private static final int TARGET_REACQUIRE_INTERVAL_TICKS = 20;

	/** Server-side only - the mob currently being engaged, re-picked every TARGET_REACQUIRE_INTERVAL_TICKS. Plain field: nothing client-side needs it (this pilot's own posture doesn't change when it's shooting). */
	private LivingEntity currentTarget;

	/** Which weapon slot to engage hostile mobs with, and (for an aircraft specifically - see tudursvehiclemod$updateCombatAi()'s own doc) the Carrier-lock-pursuit attack-altitude thresholds to use, all mirrored from the owning DroneCenterBlockEntity's own current settings every tick (tudursvehiclemod$setCombatSettings(), called from that block's own tudursvehiclemod$updateDummyPilot() alongside the existing skin/name push) rather than captured once at spawn time - a player can change any of these live while the same drone keeps flying. weaponIndex defaults to -1 (not selected, per DroneCenterBlockEntity's own dummyPilotWeaponIndex doc) so a pilot that somehow ticks even once before its first settings push (should never actually happen in practice) fails safe into "no combat" rather than an arbitrary weapon slot. */
	private int combatWeaponIndex = -1;
	private double combatAttackStartAltitude = 200.0;
	private double combatAttackStopAltitude = 40.0;
	/** Mirrors DroneCenterBlockEntity's own dummyPilotDiveTargetYOffset the same way combatAttackStartAltitude/combatAttackStopAltitude already mirror that block's own attack-altitude settings - see AircraftEntity's own carrierLockDiveTargetYOffsetOverride doc for how this actually gets used. */
	private double combatDiveTargetYOffset = 0.0;

	/** Called from DroneCenterBlockEntity's own tudursvehiclemod$updateDummyPilot(), alongside the existing skin/name push - see combatWeaponIndex's own doc. */
	public void tudursvehiclemod$setCombatSettings(int weaponIndex, double attackStartAltitude, double attackStopAltitude, double searchRange, double diveTargetYOffset) {
		this.combatWeaponIndex = weaponIndex;
		this.combatAttackStartAltitude = attackStartAltitude;
		this.combatAttackStopAltitude = attackStopAltitude;
		this.combatSearchRange = searchRange;
		this.combatDiveTargetYOffset = diveTargetYOffset;
	}

	/** How close (degrees, full 3D angle - not just horizontal bearing, since an aircraft's own fixed guns need pitch alignment too) the target must be to a fixed (non-turret) weapon's own actual firing direction to count as "will hit" rather than merely "roughly ahead". Deliberately tight - see this field's own call site for why the old, much wider cone was replaced. */
	private static final float FIXED_WEAPON_HIT_TOLERANCE_DEGREES = 4.0f;

	public DummyPilotEntity(EntityType<? extends LivingEntity> type, World world) {
		super(type, world);
	}

	/** LivingEntity requires attributes to be registered even for something that never moves or takes damage - a minimal set, since nothing here is actually consulted for this entity's own behavior. */
	public static DefaultAttributeContainer.Builder createAttributes() {
		return LivingEntity.createLivingAttributes()
				.add(EntityAttributes.MAX_HEALTH, 20.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(SKIN_ID, DummyPilotSkinRegistry.DEFAULT_SKIN_ID);
	}

	/** See SKIN_ID's own doc. Resolved through the registry so an id whose addon has since been removed renders as the default rather than as a missing texture. */
	public String tudursvehiclemod$getSkinId() {
		return DummyPilotSkinRegistry.tudursvehiclemod$resolveOrDefault(this.dataTracker.get(SKIN_ID));
	}

	/** Called by DroneCenterBlockEntity when spawning this pilot, and again whenever that center's own skin selection changes while this pilot already exists (so a change takes effect without needing to deactivate/reactivate the drone). */
	public void tudursvehiclemod$setSkinId(String skinId) {
		this.dataTracker.set(SKIN_ID, DummyPilotSkinRegistry.tudursvehiclemod$resolveOrDefault(skinId));
	}

	/** Applies both via vanilla's own setCustomName()/setCustomNameVisible() - name empty/blank means "no custom name" (this pilot then has none at all, same as it had before this feature existed), regardless of the visibility flag. */
	public void tudursvehiclemod$setNameSettings(boolean nameVisible, String name) {
		this.setCustomName(name != null && !name.isBlank() ? net.minecraft.text.Text.literal(name) : null);
		this.setCustomNameVisible(nameVisible);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getEntityWorld().isClient()) {
			return;
		}
		// Per this class's own doc: this pilot exists only to occupy a seat. Losing its vehicle (the vehicle was destroyed, or something dismounted it) means it has no reason to exist and no way to get back - the owning Drone Center will spawn a fresh one if it still wants a pilot.
		if (!(this.getVehicle() instanceof AbstractVehicleEntity vehicle)) {
			this.discard();
			return;
		}
		if (vehicle.tudursvehiclemod$isDestroyed()) {
			this.discard();
			return;
		}
		this.tudursvehiclemod$updateCombatAi(vehicle);
	}

	/** CombatWeaponIndex out of range (< 0, or >= however many weapons this vehicle's own definition actually has - see that field's own doc) disables hostile-mob combat completely - no target is even searched for in that case.
	 *
	 * Reuses AbstractVehicleEntity's own tryFireWeapon() with shooter=null for the non-aircraft case - the exact same entry point CAS auto-fire already uses, which by design skips seat validation and guided-lock requirements while keeping ammo, magazine, cooldown and reload handling completely intact. */
	private void tudursvehiclemod$updateCombatAi(AbstractVehicleEntity vehicle) {
		VehicleDefinition def = vehicle.getDefinition();
		List<WeaponDefinition> weapons = def.weapons();
		if (this.combatWeaponIndex < 0 || this.combatWeaponIndex >= weapons.size()) {
			this.currentTarget = null;
			return;
		}

		if (this.age % TARGET_REACQUIRE_INTERVAL_TICKS == 0) {
			this.currentTarget = this.tudursvehiclemod$findTarget(vehicle);
		}
		LivingEntity target = this.currentTarget;
		if (target == null || !target.isAlive() || target.isRemoved()) {
			this.currentTarget = null;
			return;
		}

		if (vehicle instanceof AircraftEntity aircraft) {
			// Per this method's own doc: hands off entirely to the same mechanism an actual player-initiated Carrier lock uses - AircraftEntity's own tick dispatch takes over this aircraft's own flight/attack behavior for as long as the lock stays active, with no further involvement from this pilot at all beyond re-confirming the target every reacquire cycle.
			aircraft.tudursvehiclemod$updateDroneCombatLock(target.getUuid(), this.combatWeaponIndex,
					this.combatAttackStartAltitude, this.combatAttackStopAltitude, this.combatDiveTargetYOffset);
			return;
		}

		WeaponDefinition weapon = weapons.get(this.combatWeaponIndex);
		Vec3d shooterOrigin = vehicle.tudursvehiclemod$getWeaponMuzzleWorldPos(weapon);
		Vec3d toTarget = target.getEntityPos().add(0.0, target.getStandingEyeHeight(), 0.0).subtract(shooterOrigin);
		if (toTarget.lengthSquared() < 1.0e-6) {
			return;
		}
		float desiredYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
		float desiredPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, toTarget.horizontalLength()));
		// Per this method's own doc: this pilot's own yaw/pitch IS what AbstractVehicleEntity's own getWeaponAimYaw()/getWeaponAimPitch() read for whichever WeaponParts track seat 0 - turning to actually look at the target is therefore not just cosmetic here (contrast the earlier, look-only version of this method): it is what drives every turret-tracked weapon's own visual AND actual firing rotation, with no separate turret-driving code needed in this class at all.
		this.setYaw(desiredYaw);
		this.setHeadYaw(desiredYaw);
		this.setBodyYaw(desiredYaw);
		this.setPitch(desiredPitch);

		if (this.tudursvehiclemod$canActuallyHit(vehicle, def, weapon, desiredYaw, desiredPitch, target)) {
			// Rate limiting is entirely tryFireWeapon()'s own (cooldown/magazine/reload), exactly as for a player holding the fire key - see this method's own doc.
			vehicle.tryFireWeapon(this.combatWeaponIndex, null);
		}
	}

	/** See tudursvehiclemod$updateCombatAi()'s own doc for the two cases this splits into. */
	private boolean tudursvehiclemod$canActuallyHit(AbstractVehicleEntity vehicle, VehicleDefinition def, WeaponDefinition weapon,
			float desiredYaw, float desiredPitch, LivingEntity target) {
		java.util.Optional<com.example.tudursvehiclemod.asset.WeaponPart> trackingPart = def.weaponParts().stream()
				.filter(part -> part.weaponName().isPresent() && part.weaponName().get().equalsIgnoreCase(weapon.weaponName()))
				.filter(part -> part.yawFollow() || part.pitchFollow())
				.findFirst();
		if (trackingPart.isPresent()) {
			// Turret-tracked: this pilot is already turned to face the target exactly (see the call site) - only remaining question is whether THIS weapon's own configured aim range can actually swing that far, rather than just clamping short and firing anyway regardless of where that leaves the barrel pointed.
			float relativeYaw = MathHelper.wrapDegrees(desiredYaw - vehicle.getYaw());
			return weapon.aimRange()
					.map(range -> vehicle.tudursvehiclemod$isAimWithinRange(relativeYaw, desiredPitch, range))
					.orElse(true);
		}
		// Fixed: no part tracks this weapon at all, so it fires along wherever it's actually mounted relative to the vehicle body regardless of this pilot's own facing - only counts as a hit if the target genuinely lies within a tight tolerance of the vehicle's own current actual direction.
		Vec3d vehicleForward = vehicle.getRotationVector();
		Vec3d toTarget = target.getEntityPos().add(0.0, target.getStandingEyeHeight(), 0.0).subtract(vehicle.getEntityPos());
		if (toTarget.lengthSquared() < 1.0e-6) {
			return false;
		}
		double cosAngle = vehicleForward.normalize().dotProduct(toTarget.normalize());
		double angleDegrees = Math.toDegrees(Math.acos(MathHelper.clamp(cosAngle, -1.0, 1.0)));
		return angleDegrees <= FIXED_WEAPON_HIT_TOLERANCE_DEGREES;
	}

	/** Which entity class this pilot searches for. Defaults to HostileEntity (hostile mobs specifically - not players, not other vehicles).
	 *
	 * An ADDON subclass can widen this (e.g. LivingEntity.class to consider players too, or its own faction interface) and pair it with tudursvehiclemod$isValidTarget() below to express whatever targeting rule it wants. */
	protected Class<? extends LivingEntity> tudursvehiclemod$targetClass() {
		return HostileEntity.class;
	}

	/** Whether one candidate found by tudursvehiclemod$targetClass() is actually worth attacking. Defaults to "alive and not removed" - line of sight is checked separately by the search itself, for every candidate regardless of this.
	 *
	 * An ADDON subclass overrides this to add its own conditions (a specific mob type, a team/faction check, excluding a particular player, and so on) without having to reimplement the search. */
	protected boolean tudursvehiclemod$isValidTarget(LivingEntity candidate) {
		return candidate.isAlive() && !candidate.isRemoved();
	}

	/** The nearest candidate within range that this pilot can actually see - see tudursvehiclemod$targetClass()/tudursvehiclemod$isValidTarget() for the two hooks an addon overrides to change WHAT counts as a target, rather than replacing this search outright. */
	protected LivingEntity tudursvehiclemod$findTarget(AbstractVehicleEntity vehicle) {
		return this.tudursvehiclemod$findNearestOfClass(vehicle, this.tudursvehiclemod$targetClass());
	}

	/** Exists purely so the candidate class binds to ONE concrete type variable for the getEntitiesByClass() call - passing a Class<? extends LivingEntity> directly would make its own T a wildcard capture, which neither the predicate nor the result assignment can be checked against. */
	private <T extends LivingEntity> LivingEntity tudursvehiclemod$findNearestOfClass(AbstractVehicleEntity vehicle, Class<T> targetClass) {
		Box searchBox = vehicle.getBoundingBox().expand(this.combatSearchRange);
		LivingEntity nearest = null;
		double nearestDistanceSquared = Double.MAX_VALUE;
		for (T candidate : this.getEntityWorld().getEntitiesByClass(targetClass, searchBox,
				this::tudursvehiclemod$isValidTarget)) {
			double distanceSquared = candidate.squaredDistanceTo(vehicle);
			if (distanceSquared >= nearestDistanceSquared) {
				continue;
			}
			if (!this.canSee(candidate)) {
				continue;
			}
			nearest = candidate;
			nearestDistanceSquared = distanceSquared;
		}
		return nearest;
	}

	// --- Everything below: this entity is a seat occupant, not a participant in the world. ---

	/** See this class's own doc for why this pilot can't be damaged at all. */
	@Override
	public boolean damage(net.minecraft.server.world.ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(net.minecraft.server.world.ServerWorld world, DamageSource source) {
		return true;
	}

	/** Never pushed around by anything - it's strapped into a seat. */
	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void pushAwayFrom(Entity entity) {
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	/** Riding position is entirely the vehicle's own seat placement (AbstractVehicleEntity handles every passenger the same way) - no gravity, no independent movement, ever. */
	@Override
	public boolean hasNoGravity() {
		return true;
	}

	@Override
	public boolean canMoveVoluntarily() {
		return false;
	}

	/** No inventory or equipment of any kind - it renders as a plain figure and its "weapons" are the vehicle's own. getEquippedStack() below already covers every slot, armor included - there is no separate armor-items method on LivingEntity in this version. */
	@Override
	public ItemStack getEquippedStack(EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void equipStack(EquipmentSlot slot, ItemStack stack) {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}

	@Override
	protected void writeCustomData(net.minecraft.storage.WriteView view) {
		super.writeCustomData(view);
		view.putString("SkinId", this.dataTracker.get(SKIN_ID));
	}

	@Override
	protected void readCustomData(net.minecraft.storage.ReadView view) {
		super.readCustomData(view);
		this.tudursvehiclemod$setSkinId(view.getString("SkinId", DummyPilotSkinRegistry.DEFAULT_SKIN_ID));
	}
}
