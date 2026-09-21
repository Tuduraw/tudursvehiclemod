package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.asset.SeatDefinition;
import com.example.tudursvehiclemod.asset.TogglePart;
import com.example.tudursvehiclemod.asset.WingSweepConfig;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.VehicleRegistry;
import com.example.tudursvehiclemod.asset.AmmoPart;
import com.example.tudursvehiclemod.asset.WeaponDefinition;
import com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import java.util.List;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared behaviour for every vehicle entity: which VehicleDefinition it uses, seat placement, mounting, and NBT/tracked-data plumbing. */
public abstract class AbstractVehicleEntity extends Entity implements MeshedEntity, net.minecraft.inventory.Inventory,
		net.minecraft.screen.NamedScreenHandlerFactory {

	private static final TrackedData<String> VEHICLE_ID =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<Float> THROTTLE =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Synced via DataTracker (not a plain field) so tree-felling state reaches the client's own independent physics copy too. */
	private static final TrackedData<Integer> TREE_COLLISION_BRAKE_TICKS =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Whether a carrier's own runway deck applied a carry displacement to this vehicle THIS tick - used by VehicleMoveIgnoreMixin (see that class's own doc: letting it detect and protect only currently-carried vehicles, rather than piloted vehicles in general). Deliberately a PLAIN, non-synced field rather than a DataTracker one - VehicleMoveIgnoreMixin is @Mixin(ServerPlayNetworkHandler.class), a server-only class, so its own client-side copy was never actually needed for that purpose at all. This project has repeatedly tried and reverted a velocity-zeroing hook driven by this same flag (most recently: reintroduced alongside candidate.setVelocity() as a render-smoothing hint, then reverted again after a direct report of large position disruption for both the candidate and the mothership itself) - not currently in use for that purpose. Set true by carryRunwayDeckEntities() (server-side only, same as everything else in that method) whenever this vehicle is actually carried a given tick, cleared the tick after carrying genuinely stops (see carrierLastCarriedCandidates' own reconciliation). */
	private boolean tudursvehiclemod$runwayCarryActive;
	/** LastTickX/lastTickZ/lastYaw (used by tudursvehiclemod$getActualForwardSpeed()/tudursvehiclemod$getCrawlerTrackSpeed() for wheel-spin/crawler-track animation) are plain, non-synced fields tracked INDEPENDENTLY by each side, and that whole calculation "runs on both client and server" - carryRunwayDeckEntities()'s own SERVER-side compensation (advancing those fields to the post-carry position/yaw) only ever corrects the SERVER's own copy, never the CLIENT's, so the client's own independent measurement still picks up the carry's own displacement as genuine forward speed. This flag, unlike tudursvehiclemod$runwayCarryActive above, IS synced - when true, tick() advances THIS side's own lastTickX/lastTickZ/lastYaw to its own current position/yaw (see the tick()-level hook below), which is safe to do on the client too since those fields are purely internal bookkeeping for this one calculation and have zero effect on the client's own rendered position/motion smoothness (unlike velocity, which vanilla's own interpolation relies on and which this mechanism deliberately never touches at all). */
	private static final TrackedData<Boolean> RUNWAY_CARRY_WHEEL_SYNC =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> FREE_LOOK =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> DESCEND =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> HATCH_OPEN =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Hatch-type TogglePart parts (see updateToggleParts()'s own "$hatch"/"$canopy" prefix doc) no longer share HATCH_OPEN's own auto-open-on-spawn/auto-close-on-mount behavior, while canopy-type parts keep that exact behavior unchanged: a separate, dedicated flag for canopy-type parts specifically. Default true, matching HATCH_OPEN's own PREVIOUS default - canopy's own spawn-state behavior is unaffected by this whole change. See isCanopyOpen()/setCanopyOpen() below. Player-triggered toggleHatch() still moves both this and HATCH_OPEN together (see that method's own doc) - only the SPAWN/MOUNT auto-behaviors actually diverge between the two now. */
	private static final TrackedData<Boolean> CANOPY_OPEN =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** See isWingFoldOpen()'s own doc. */
	private static final TrackedData<Boolean> WING_FOLD_OPEN =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** toggleLandingGear() is only ever called either from a server-side network packet handler (ModNetworking.java, receiving the pilot's own key-press) or that SAME pilot's own client optimistically predicting locally (VehicleModClient.java) - neither path ever notified any OTHER client at all, since this was a plain (non-networked) field before. A drone has no pilot to "optimistically predict" on any client at all, so this gap became 100% visible instead of just theoretical. Converted to a proper DataTracker field so gear state (like wing_fold/hatch already do) reaches every observing client, not just an actively-predicting pilot. */
	private static final TrackedData<Boolean> GEAR_DEPLOYED =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** CarrierWeaponBayForcedOpen used to be a plain (server-only) field, never reaching any observing client at all (including a Carrier aircraft's own autonomous flight, which has no pilot to locally predict this on any client). Two booleans represent the 3-state Optional&lt;Boolean&gt; this used to be (active + value), same idea as GEAR_DEPLOYED's own conversion. */
	private static final TrackedData<Boolean> CARRIER_WEAPON_BAY_OVERRIDE_ACTIVE =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> CARRIER_WEAPON_BAY_OVERRIDE_VALUE =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Manual mode: disables a vehicle's own "no input for N ticks" level-flight/level-attitude assist entirely (see AircraftEntity's and SubmarineEntity's own auto-level logic). Moved here from AircraftEntity so SubmarineEntity can share the exact same state/key/payload. */
	private static final TrackedData<Boolean> MANUAL_MODE =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Bit N = weapon N had a fire attempt within WEAPON_FIRING_GRACE_TICKS - needed client-side for AddPartRotWeapon's spin animation. Supports up to 32 weapon slots. */
	private static final TrackedData<Integer> FIRING_WEAPON_BITMASK =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Bit N = weapon N had a REAL shot fire this exact tick (cleared every tick) - distinct from FIRING_WEAPON_BITMASK's own multi-tick grace window. */
	private static final TrackedData<Integer> ACTUAL_FIRE_BITMASK =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** This vehicle's own current recoil-shake magnitude - snaps to weapon.recoil() on fire, eases back via RECOIL_RECOVERY_RATE (separate from RecoilBufCount, which is for WeaponPart animation instead -). */
	private static final TrackedData<Float> RECOIL_CURRENT_MAGNITUDE =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** The firing weapon's own aim yaw at the instant recoil was captured, so the shake kicks away from wherever the weapon was actually pointing. */
	private static final TrackedData<Float> RECOIL_AIM_YAW =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Current remote controller's UUID (empty = none), synced so LivingEntityRendererMixin can force-hide that one passenger regardless of HideEntity. */
	private static final TrackedData<String> REMOTE_CONTROLLER_UUID =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.STRING);
	/** How much of this vehicle's pitch is currently attributable to recoil shake - tracked so each tick applies just the change since last tick. */
	private float lastAppliedRecoilPitchOffset = 0f;
	/** Same as lastAppliedRecoilPitchOffset, for roll instead - RECOIL_AIM_YAW splits a single shake magnitude into both. */
	private float lastAppliedRecoilRollOffset = 0f;
	/** Encodes every weapon's current (ammo remaining, reload ticks remaining) as "ammo0:reload0,ammo1:reload1,.." (index matching VehicleDefinition#weapons()). */
	private static final TrackedData<String> WEAPON_AMMO_SYNC =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.STRING);
	/** Per-weapon missile lock progress, synced for HudVariables. */
	private static final TrackedData<String> MISSILE_LOCK_SYNC =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.STRING);
	/** The distance-aware REQUIRED tick count (see tudursvehiclemod$missileLockRequiredTicks's own doc) synced alongside MISSILE_LOCK_SYNC's own progress, same "progress0,progress1,.." format - so HudVariables' own lock_progress display can compute the correct fraction against the weapon's own actual required time for THIS lock attempt, not its flat, fixed lockTimeTicks() alone. */
	private static final TrackedData<String> MISSILE_LOCK_REQUIRED_SYNC =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.STRING);
	/** Current health, out of def.maxHealth(). */
	private static final TrackedData<Float> HEALTH =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Current internal fuel, out of getMaxFuel(). */
	private static final TrackedData<Float> FUEL =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Selected weapon index, synced so AddPartWeaponBay's weapon_bay trigger opens for every nearby player, not just the selector. -1 = none selected. */
	private static final TrackedData<Integer> SELECTED_WEAPON_INDEX =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** True while this vehicle (a Carrier formation's own lead aircraft, player-piloted) is in "lock mode" - while active, the fire key locks a target for a wingman to attack instead of firing this aircraft's own weapon (see tudursvehiclemod$tryLockCarrierTarget()'s own doc). Toggled by ToggleCarrierLockModePayload (see that payload's own doc for the Alt-held "release all" alternate action). */
	private static final TrackedData<Boolean> CARRIER_LOCK_MODE_ACTIVE =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Entity.setGlowing() is insufficient for this mod's own vehicle entities (see this field's own full doc above the class) - a synced "highlight this vehicle" flag driving client.render.TargetHighlightRenderer's own mesh-accurate outline instead. */
	private static final TrackedData<Boolean> HIGHLIGHT_ACTIVE =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Non-vehicle entities also avoid setGlowing() in favor of a mesh-like (bounding-box wireframe) display, driven by client.render.TargetHighlightRenderer - a synced, comma-separated list (matching MISSILE_LOCK_SYNC's own encoding convention) of every non-vehicle entity ID THIS vehicle currently wants highlighted. Lives on the TRACKING vehicle (not the target) - see this field's own full doc above for why that's a deliberate, meaningful difference from HIGHLIGHT_ACTIVE. */
	private static final TrackedData<String> HIGHLIGHTED_ENTITY_IDS =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.STRING);
	/** Server-only working set backing HIGHLIGHTED_ENTITY_IDS's own synced encoding - see that field's own doc. */
	private final java.util.Set<Integer> tudursvehiclemod$highlightedNonVehicleEntityIds = new java.util.HashSet<>();
	/** See tudursvehiclemod$onDestroyed()'s own doc. */
	private static final TrackedData<Boolean> DESTROYED =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** Whether this vehicle's own search light(s) - every SearchLightPart in its own definition, there being no per-light on/off in MC Heli's own format - are currently switched on. One flag covers all of a vehicle's own lights at once, matching every other MC Heli vehicle-wide toggle here (weapon mode, hatch) rather than needing a whole array for something that was never independently controllable per-light in the source format to begin with. */
	private static final TrackedData<Boolean> SEARCH_LIGHT_ON =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** One flag covering every NavLightPart in the definition at once, the same "one switch for the whole set" shape SEARCH_LIGHT_ON already uses - a real aircraft's own nav-light switch works the same way (one switch, not one per bulb). Defaults to on, matching how nav/aviation lights are normally left on during ordinary operation and only switched off for the specific situations (combat, parked, etc.) the direct requirement named. */
	private static final TrackedData<Boolean> NAV_LIGHTS_ON =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** The yaw/pitch a PILOT_VIEW light was aiming at the moment its own tracked occupant was last present, held here so tudursvehiclemod$getSearchLightWorldAim()'s own no-occupant fallback can return THIS instead of collapsing to bodyYaw/basePitch (which is the FIXED-mode fallback, and was never meant to also serve PILOT_VIEW's own no-rider case). Synced (needs to match on every client, not just derived locally) and NaN by default, meaning "never had an occupant yet" - see that same fallback's own doc for how the NaN case is handled. */
	private static final TrackedData<Float> SEARCH_LIGHT_LAST_YAW =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> SEARCH_LIGHT_LAST_PITCH =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);

	/** Whether this wreck has been given a sinking attitude yet. 0 = not sinking; 1 = sinking, with its own targets held in SINK_TARGET_PITCH / SINK_TARGET_ROLL.
	 *
	 * This replaced a fixed set of six numbered patterns. Drawing the two target angles freely instead produces the same recognisable outcomes - going down by the bow or stern, listing to either side, rolling over - as points on a continuum rather than as six discrete cases, so no two wrecks settle quite alike.
	 *
	 * Synced rather than kept as a plain field because the client runs its own independent physics copy - randomly drawn values can't be re-derived there, so both sides would otherwise tilt the same wreck different ways. */
	private static final TrackedData<Integer> SINK_PATTERN =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/** The pitch this wreck is easing towards, in degrees. Drawn once when sinking begins. */
	private static final TrackedData<Float> SINK_TARGET_PITCH =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);

	/** The roll this wreck is easing towards, in degrees - see SINK_TARGET_PITCH's own doc. The wider range is what lets a wreck roll fully over rather than merely list. */
	private static final TrackedData<Float> SINK_TARGET_ROLL =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);

	private static final TrackedData<Integer> SINK_TICKS =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.INTEGER);

	protected VehicleDefinition definition;

	/** Ticks remaining before each weapon (by index into VehicleDefinition#weapons) can fire again. */
	private int[] weaponCooldowns = new int[0];
	/** Separate from weaponCooldowns (the weapon's own fire rate). */
	private int[] weaponSoundCooldowns = new int[0];
	/** Current ammo remaining in each weapon's magazine (index matching VehicleDefinition#weapons()). */
	private int[] weaponAmmo = new int[0];
	/** Ticks remaining until each weapon's reload finishes (0 = not currently reloading). */
	private int[] weaponReloadTicksRemaining = new int[0];
	/** Total reserve remaining per weapon, separate from weaponAmmo's own magazine. -1 = unlimited (maxAmmo 0/unset); see tudursvehiclemod$ensureWeaponAmmoArraysSized() for initialization. */
	private int[] weaponReserveAmmo = new int[0];
	/** How many currently-in-flight aircraft were launched from each weapon slot and haven't landed (or been otherwise lost) yet - counted toward that slot's own MaxAmmo cap so resupply doesn't let magazine+reserve+in-flight exceed it (preventing wasted item consumption, or an unintended net increase in total loaded ammo, once an in-flight aircraft eventually lands and adds its own round back). Incremented at launch (tudursvehiclemod$fireCarrierLaunch()), decremented on a SUCCESSFUL landing (tudursvehiclemod$replenishWeaponAmmo()) - deliberately NOT decremented if an aircraft is lost some other way (destroyed, timed out) without ever landing, since that round is genuinely gone for good, not still "out there" waiting to come back. */
	/** Tracks the UUID of every currently-in-flight Carrier aircraft launched from each weapon slot (keyed by weaponIndex) - added at launch time, removed on a SUCCESSFUL landing. Unlike a simple increment/decrement counter, this is verified/pruned against which of these UUIDs are ACTUALLY still alive right at resupply-check time (see tudursvehiclemod$tryResupplyWeapon()'s own doc) - self-correcting even if an aircraft was lost some other way (timeout, destroyed) that never explicitly decremented anything, rather than silently drifting out of sync with reality forever. */
	private final java.util.Map<Integer, java.util.Set<java.util.UUID>> carrierInFlightAircraft = new java.util.HashMap<>();
	/** ModeNum: selectable mode per weapon (0 = default, 1 = second, X toggles). Always 0 if hasModes() is false. */
	private int[] weaponMode = new int[0];
	/** Accumulated barrel heat, per weapon index. */
	private float[] weaponHeat = new float[0];
	/** Which of a weapon's own offsets() the NEXT shot from that weapon index uses. */
	private int[] weaponFiringPositionIndex = new int[0];
	/** This vehicle's own per-SEAT-INDEX occupant as of last tick (not raw passenger-list position - see tudursvehiclemod$updateSeatMountGrace()'s own doc for why that distinction matters). Index i holds whoever occupied seat i last tick, or null if that seat was empty. */
	private Entity[] tudursvehiclemod$previousSeatOccupants = new Entity[0];
	/** Ticks remaining, per seat index, during which firing is locked out. */
	private int[] seatMountGraceTicks = new int[0];
	/** Entity IDs this vehicle set glowing last tick as AAMissile/ATMissile lock-on-preview targets (see tudursvehiclemod$updateMissileLockOnIndicators's own doc). */
	private java.util.Set<Integer> tudursvehiclemod$previousLockOnTargetIds = java.util.Set.of();
	/** LockTime: consecutive ticks each AAMissile/ATMissile has held the same target under its crosshair; resets on target change. Reaching lockTimeTicks launches guided; earlier launches unguided. */
	private final java.util.Map<Integer, Integer> tudursvehiclemod$missileLockProgressTicks = new java.util.HashMap<>();
	/** Which entity ID tudursvehiclemod$missileLockProgressTicks's own count is currently tracking, per weaponIndex - compared each tick to detect the target changing (which resets progress back to 0). */
	private final java.util.Map<Integer, Integer> tudursvehiclemod$missileLockTargetId = new java.util.HashMap<>();
	/** The ACTUAL required tick count for THIS weapon's own current lock attempt, recomputed every tick in tudursvehiclemod$updateMissileLockOnIndicators() from the target's own live distance (weapon.lockTimeTicks() + weapon.lockTimePerBlock() * currentDistance) - read by the lock-completion check elsewhere instead of the weapon's own flat, fixed lockTimeTicks() alone. Recomputed (not fixed once at lock-start) so a target closing distance mid-lock genuinely locks faster, and one opening distance genuinely takes longer, matching a real proportional/continuous tracking sensor rather than a one-shot distance snapshot. */
	private final java.util.Map<Integer, Integer> tudursvehiclemod$missileLockRequiredTicks = new java.util.HashMap<>();
	/** This vehicle's own persistent cargo inventory (see VehicleExtras' own inventorySize doc). */
	private DefaultedList<ItemStack> vehicleInventory = DefaultedList.of();

	/** Purely cosmetic bank/tilt angle from A/D input. */
	protected float roll;
	protected float prevRoll;

	/** Internal "how fast am I currently cruising" state used by approachThrottledVelocity. */
	protected float cruiseSpeed;

	/** This vehicle's own tracked "airspeed". */
	public float getCruiseSpeed() {
		return this.cruiseSpeed;
	}

	/** CruiseSpeed is a plain, non-DataTracker field - it's NEVER automatically synced from server to client at all (unlike position/velocity, which vanilla's own entity tracking handles). The CLIENT's own copy of a Carrier-launched aircraft never actually ran its own autonomous-flight tick logic at all while it was flying unmanned and out of that player's own render distance, so its own local cruiseSpeed just sat at its default (0) the whole time - only catching up gradually once normal piloted-vehicle physics started running client-side too. This setter exists so network.SyncCruiseSpeedPayload's own client-side handler can push the server's own already-correct value directly onto the client's own copy the instant a Carrier seat switch succeeds, rather than waiting for it to organically catch up. Also sets throttle via setThrottleDirect() (protected, so this public wrapper is needed for the client-side network handler in a different package to reach it at all). */
	public void tudursvehiclemod$syncCruiseSpeedAndThrottle(float cruiseSpeed, float throttle) {
		this.cruiseSpeed = cruiseSpeed;
		this.setThrottleDirect(throttle);
	}

	/** 0 = fully deployed/extended, 1 = fully retracted/folded (this is the opposite of what the names might suggest. */
	private float landingGearProgress = 0f;
	private float prevLandingGearProgress = 0f;

	/** Per-TogglePart current/previous progress (0=closed, 1=open), keyed by TogglePart#part(). */
	private final java.util.Map<String, Float> togglePartProgress = new java.util.HashMap<>();
	private final java.util.Map<String, Float> prevTogglePartProgress = new java.util.HashMap<>();

	/** Per RunwayDefinition's own hatchOffsetX/Y/Z doc: per-runway current/previous hatch-driven progress (0=closed/base position, 1=fully open/offset), keyed by that runway's own index within def.runways() - see updateRunwayHatchProgress() for how this eases towards isHatchOpen() each tick. Deliberately keyed by index rather than by any TogglePart name, since RunwayDefinition's own hatch fields are intentionally decoupled from named TogglePart parts entirely (see that record's own doc). */
	private final java.util.Map<Integer, Float> runwayHatchProgress = new java.util.HashMap<>();
	private final java.util.Map<Integer, Float> prevRunwayHatchProgress = new java.util.HashMap<>();

	/** Per-PartAnimation accumulated spin angle (degrees, unbounded. */
	private final java.util.Map<String, Float> spinningPartsPhase = new java.util.HashMap<>();
	private final java.util.Map<String, Float> prevSpinningPartsPhase = new java.util.HashMap<>();

	/** Which tick each weaponIndex last had a fire attempt recorded - used by tudursvehiclemod$isWeaponCurrentlyFiring() for AddPartRotWeapon's gatling-style spin. */
	private final java.util.Map<Integer, Integer> lastFireAttemptTick = new java.util.HashMap<>();
	/** Same as lastFireAttemptTick but only for REAL shots, with a shorter grace window for recoil purposes. */
	private final java.util.Map<Integer, Integer> lastActualFireTick = new java.util.HashMap<>();
	/** Per-WeaponPart accumulated spin angle (degrees, unbounded) - same convention as spinningPartsPhase above, just driven by tudursvehiclemod$isWeaponCurrentlyFiring() instead of throttle. */
	private final java.util.Map<String, Float> weaponPartSpinPhase = new java.util.HashMap<>();
	private final java.util.Map<String, Float> prevWeaponPartSpinPhase = new java.util.HashMap<>();
	/** AddPart/AddChildPart's part_type=2 (recoil distance) animation - kicks to recoilDistance on fire, eases back to 0 each tick. */
	private final java.util.Map<String, Float> weaponPartRecoilOffset = new java.util.HashMap<>();
	private final java.util.Map<String, Float> prevWeaponPartRecoilOffset = new java.util.HashMap<>();
	/** Tick each recoil kick started, so decay rate can reflect that weapon's own RecoilBufCount instead of a fixed hardcoded rate. */
	private final java.util.Map<String, Integer> weaponPartRecoilStartTick = new java.util.HashMap<>();
	/** AddPartWheel/PartWheelRot: single shared spin phase for every WheelPart, driven by actual ground speed (not throttle -). */
	private float wheelSpinPhase;
	private float prevWheelSpinPhase;
	/** Position at the end of the previous tick, for tudursvehiclemod$getActualForwardSpeed()'s own position-delta speed calculation. Plain (non-synced) field - each side (client/server) tracks its own copy. */
	private double lastTickX;
	private double lastTickZ;
	private boolean hasLastTickPosition;
	/** AddCrawlerTrack: per-track accumulated belt distance, each accumulating independently by its own differential-steering speed (so left/right sides end up at different phases while turning). */
	private final java.util.Map<String, Float> crawlerTrackPhase = new java.util.HashMap<>();
	private final java.util.Map<String, Float> prevCrawlerTrackPhase = new java.util.HashMap<>();

	protected AbstractVehicleEntity(EntityType<?> type, World world) {
		super(type, world);
		// Every vehicle type computes its OWN gravity/fall behavior entirely by hand (see each subclass's own updateVehicleMovement(), which calls setVelocity() directly).
		this.setNoGravity(true);
	}

	/** The definition id this entity uses if nothing else was set (e.g. */
	protected abstract Identifier defaultDefinitionId();

	/** Subclasses implement their own control/physics model here. */
	protected abstract void updateVehicleMovement(VehicleDefinition definition);

	/** A single float of purely cosmetic animation state (rotor spin angle, wheel spin angle, propeller RPM,..) that the client-side renderer reads every frame via.. */
	@Override
	public float getAnimationPhase(float tickDelta) {
		return 0f;
	}

	@Override
	public Identifier getModelId() {
		return getDefinition().model();
	}

	@Override
	public Identifier getTextureId() {
		return getDefinition().texture();
	}

	@Override
	public float getScale() {
		return getDefinition().scale();
	}

	/** Purely cosmetic Y offset, default 0 - CarEntity overrides this to smooth its own step-up logic's instant setPosition() jump. */
	public float getRenderYOffset(float tickDelta) {
		return 0f;
	}

	/** Current (non-interpolated) tick value of getRenderYOffset(), for updatePassengerPosition() which runs once per tick with no tickDelta - same current-vs-interpolated split as getSeatRotationCurrent(). */
	public float getRenderYOffsetCurrent() {
		return 0f;
	}

	/** Purely cosmetic bank/tilt angle (see updateRoll()). */
	@Override
	public float getRoll(float tickDelta) {
		return this.prevRoll + (this.roll - this.prevRoll) * tickDelta;
	}

	/** Non-interpolated counterpart of getRoll(float). */
	public float getRoll() {
		return this.roll;
	}

	/** The actual, tick-accurate roll value (not render-interpolated). */
	public float getCurrentRoll() {
		return this.roll;
	}

	/** This vehicle's own current full 3D body orientation (yaw, pitch, AND roll) as a quaternion. */
	public Quaternionf tudursvehiclemod$getBodyOrientation() {
		return new Quaternionf()
				.rotationY((float) Math.toRadians(-this.getYaw()))
				.rotateX((float) Math.toRadians(this.getPitch()))
				.rotateZ((float) Math.toRadians(this.getRoll()));
	}

	/** Free-form, per-OBJ-group transforms this vehicle wants applied when drawn - a general escape
	 * hatch for animation the fixed part types (weapon/toggle/spinning/wheel parts) can't express,
	 * such as multi-joint limbs where a shin must follow a thigh which must follow a hip, to any depth.
	 *
	 * <p>Keyed by OBJ group name. Each matrix is in MODEL space (model units, before this vehicle's
	 * own {@code scale}), applied on top of the body transform exactly where the renderer draws every
	 * other animated part, and must already contain the part's WHOLE chain (e.g. for a shin:
	 * hip rotation about the hip pivot, then knee rotation about the knee pivot) - the renderer applies
	 * it as-is and knows nothing about joints, pivots or parents. A group listed here is removed from
	 * the static mesh and drawn only with its transform. Groups also claimed by another part list
	 * (a weapon part, toggle part, ...) should not be listed here.
	 *
	 * <p>Default: none - every existing vehicle type renders exactly as before. Client-side render
	 * data only; nothing here affects collision, physics or networking. */
	public java.util.Map<String, org.joml.Matrix4f> tudursvehiclemod$getCustomPartTransforms(float tickDelta) {
		return java.util.Map.of();
	}

	/** World-space translation of this vehicle's own BODY FRAME relative to its entity position - for
	 * a vehicle whose body is drawn rotated about some pivot other than its own model origin (e.g. an
	 * addon leaning a tall body about its middle), which a rotation alone
	 * (tudursvehiclemod$getBodyOrientation()) can't express. Applied everywhere a body-local point is
	 * placed in the world: the rendered position (VehicleEntityRenderer.getPositionOffset()), seat
	 * positions (updatePassengerPosition()), the rider's eye/camera position (getRotatedEyePos()),
	 * and weapon spawn positions (tudursvehiclemod$computeWeaponSpawnPos()) - so the model, riders
	 * and muzzles all stay together.
	 *
	 * <p>Default: zero - every existing vehicle type is unaffected. Purely positional; never moves
	 * the entity itself, its hitbox, or anything physics-related. */
	public Vec3d tudursvehiclemod$getBodyFrameOffset(float tickDelta) {
		return Vec3d.ZERO;
	}

	/** Render-interpolated counterpart of tudursvehiclemod$getBodyOrientation() - used ONLY by
	 * VehicleEntityRenderer for the vehicle's own mesh transform, alongside (not instead of) the
	 * no-arg version above, which every other caller (flare direction, mount direction,
	 * MortarMarkerRenderer's own ballistic aim) keeps using unchanged.
	 *
	 * <p>Default implementation composes from getYaw(tickDelta)/getPitch(tickDelta)/getRoll(tickDelta)
	 * - IDENTICAL rendered result to what VehicleEntityRenderer used to build inline from those same
	 * three calls, for every vehicle type that doesn't override this (Car, Ship, Submarine,
	 * Helicopter): their own attitude has no representation OTHER than yaw/pitch/roll, so composing
	 * a quaternion from them and immediately handing it to the renderer loses nothing next to
	 * building the rotation matrix directly, and changes no rendered pixel.
	 *
	 * <p>AircraftEntity/VtolEntity override this to return their own already-interpolated
	 * {@code orientation} quaternion directly (see either class's own override) - for those two,
	 * going through this default composition would recreate exactly the gimbal-lock-adjacent
	 * jitter their own getYaw()/getPitch()/getRoll() overrides already have to accept as the cost
	 * of exposing a quaternion-native attitude through a yaw/pitch/roll-shaped API in the first
	 * place; this overload exists specifically so the renderer never has to pay that cost. */
	public Quaternionf tudursvehiclemod$getBodyOrientation(float tickDelta) {
		return new Quaternionf()
				.rotationY((float) Math.toRadians(-this.getYaw(tickDelta)))
				.rotateX((float) Math.toRadians(this.getPitch(tickDelta)))
				.rotateZ((float) Math.toRadians(this.getRoll(tickDelta)));
	}

	/** For vehicles whose pitch can go beyond what a raw player view angle could ever represent (currently: AircraftEntity, for full loops. */
	public float getExcessPitchForCamera(float tickDelta) {
		return 0f;
	}

	/** Max degrees/tick the PILOT'S OWN view is allowed to turn (yaw) while riding this vehicle, or a negative number if this vehicle doesn't link its heading to the.. */
	public float getYawFollowRateDegrees() {
		return -1f;
	}

	/** Same as getYawFollowRateDegrees(), but for pitch. */
	public float getPitchFollowRateDegrees() {
		return -1f;
	}

	/** Holds throttle at exactly 0% for this many ticks when crossing forward/reverse, shared by Ship/Aircraft (originally SubmarineEntity's own behavior -). Only matters if reverseThrottle allows reverse at all. */
	private static final int THROTTLE_SWITCH_HOLD_TICKS = 40;
	private int throttleSwitchHoldTicksRemaining;

	/** Tracks the previous tick's own EFFECTIVE throttle sign (post-hold) for tudursvehiclemod$applyThrottleSwitchHold()'s own use. */
	private float lastEffectiveThrottleSign;

	/** Tracks the sign of the last NON-ZERO throttle value reached (unchanged while briefly at 0) - decides whether a hold is warranted. */
	private float lastNonZeroThrottleSign;

	/** Applies the same 0%-hold behavior as updateThrottle(), but for vehicles (Car, Helicopter) using raw unramped throttle - rawInput should already be the clamped final value the caller was about to use. */
	protected float tudursvehiclemod$applyThrottleSwitchHold(float rawInput, float min) {
		int holdTicks = this.getDefinition().throttleSwitchHoldTicks().orElse(THROTTLE_SWITCH_HOLD_TICKS);
		if (this.throttleSwitchHoldTicksRemaining > 0) {
			this.throttleSwitchHoldTicksRemaining--;
			if (this.throttleSwitchHoldTicksRemaining == 0) {
				this.lastEffectiveThrottleSign = 0f;
			}
			return 0f;
		}
		if (min < 0f && ((this.lastEffectiveThrottleSign > 0 && rawInput < 0)
				|| (this.lastEffectiveThrottleSign < 0 && rawInput > 0))) {
			this.throttleSwitchHoldTicksRemaining = holdTicks;
			this.lastEffectiveThrottleSign = 0f;
			return 0f;
		}
		if (rawInput != 0f) {
			this.lastEffectiveThrottleSign = rawInput;
		}
		return rawInput;
	}

	/** Throttle-style speed control shared by every vehicle: holding W ramps the throttle up, holding S ramps it down, and releasing both HOLDS the current value.. */
	protected float updateThrottle(PlayerEntity pilot, float step, float min, float max) {
		if (this.throttleSwitchHoldTicksRemaining > 0) {
			this.throttleSwitchHoldTicksRemaining--;
			this.dataTracker.set(THROTTLE, 0f);
			if (this.throttleSwitchHoldTicksRemaining == 0) {
				// Once the hold actually finishes,
				// forget the direction that triggered it - otherwise the
				// very next ramp (in whichever direction the player is
				// still holding) would immediately compare against that
				// SAME old sign again and re-trigger another hold right
				// away, looping forever instead of ever actually letting
				// the throttle move again.
				this.lastNonZeroThrottleSign = 0f;
			}
			return 0f;
		}
		float current = this.dataTracker.get(THROTTLE);
		float previous = current;
		boolean outOfFuel = this.tudursvehiclemod$isOutOfFuel();
		boolean wingLocked = this.tudursvehiclemod$isWingLockedFolded();
		// A destroyed vehicle's own throttle behaves exactly like being out of fuel - locked at 0%, never raisable again, regardless of actual fuel level.
		boolean destroyed = this.tudursvehiclemod$isDestroyed();
		// Uses this.getSyncedThrottleInput() (see ThrottleInputPayload's own doc, and that method's own doc for why NOT the raw syncedThrottleInput field directly - the getter is what actually resolves to the ground autopilot's own synced value on every client, not just this same server instance) rather than pilot.forwardSpeed directly.
		float throttleInput = this.getSyncedThrottleInput();
		if (outOfFuel || wingLocked || destroyed) {
			// Engine stopped, throttle locked at 0.
			current = 0f;
		} else if (throttleInput > 0) {
			current = Math.min(max, current + step);
		} else if (throttleInput < 0) {
			current = Math.max(min, current - step);
		}
		// The hold should only ever trigger for a
		// GENUINE sign reversal - i.e. the ramped value actually reaching
		// a non-zero value on the OPPOSITE side from whichever non-zero
		// value it last actually held (see lastNonZeroThrottleSign's own
		// doc) - not merely because the opposite-direction key happened
		// to be held the exact instant the ramp touched 0 (e.g. holding S
		// down to precisely 0%, then immediately switching back to W
		// without the throttle ever having actually gone negative, should
		// NOT trigger a hold at all). Deliberately does nothing while
		// current is exactly 0 - lastNonZeroThrottleSign only ever
		// updates once a genuinely non-zero value is reached, so it
		// keeps remembering whichever direction was last actually in
		// effect across that gap.
		if (min < 0f && current != 0f && !this.tudursvehiclemod$isFollowingGroundRoute()) {
			float currentSign = Math.signum(current);
			if (this.lastNonZeroThrottleSign != 0f && currentSign != this.lastNonZeroThrottleSign) {
				current = 0f;
				this.throttleSwitchHoldTicksRemaining = this.getDefinition().throttleSwitchHoldTicks().orElse(THROTTLE_SWITCH_HOLD_TICKS);
			} else {
				this.lastNonZeroThrottleSign = currentSign;
			}
		}
		this.dataTracker.set(THROTTLE, current);
		return current;
	}

	/** See ThrottleInputPayload's own doc for why this exists instead of just reading PlayerEntity#forwardSpeed directly. */
	private float syncedThrottleInput;
	/** See ThrottleInputPayload's own doc. */
	private float syncedSidewaysInput;

	public void setSyncedThrottleInput(float value) {
		this.syncedThrottleInput = value;
	}

	public void setSyncedSidewaysInput(float value) {
		this.syncedSidewaysInput = value;
	}

	/** See ThrottleInputPayload's own doc for why this exists instead of just reading PlayerEntity#forwardSpeed directly. A ground-route-following vehicle previously moved smoothly only for a riding-but-not-driving passenger's own client - see tudursvehiclemod$applyGroundWaypointAutopilotInputs()'s own doc for the full mechanism: while this vehicle's own GROUND_ROUTE_ACTIVE flag (synced) is true, returns the synced GROUND_AUTOPILOT_THROTTLE_INPUT instead of this plain field - every client now reads the SAME, correct, autopilot-driven value this way, rather than each client's own copy of this plain field (which, for an autopilot-driven vehicle, no packet from any actual controlling player ever populates at all). Falls back to this plain field otherwise, unchanged - an actual player-piloted vehicle's own input responsiveness is completely unaffected, since GROUND_ROUTE_ACTIVE is never true for one. */
	public float getSyncedThrottleInput() {
		return this.dataTracker.get(GROUND_ROUTE_ACTIVE) ? this.dataTracker.get(GROUND_AUTOPILOT_THROTTLE_INPUT) : this.syncedThrottleInput;
	}

	/** See getSyncedThrottleInput()'s own doc - same reasoning, same mechanism, for steering instead of throttle. */
	public float getSyncedSidewaysInput() {
		return this.dataTracker.get(GROUND_ROUTE_ACTIVE) ? this.dataTracker.get(GROUND_AUTOPILOT_STEER_INPUT) : this.syncedSidewaysInput;
	}

	/** See VerticalLevelInputPayload's own doc (SubmarineEntity's own arrow-up/down "ascend/descend without changing pitch"). */
	private boolean syncedLevelAscendInput;
	private boolean syncedLevelDescendInput;

	public void setSyncedLevelAscendInput(boolean value) {
		this.syncedLevelAscendInput = value;
	}

	public void setSyncedLevelDescendInput(boolean value) {
		this.syncedLevelDescendInput = value;
	}

	public boolean getSyncedLevelAscendInput() {
		return this.syncedLevelAscendInput;
	}

	public boolean getSyncedLevelDescendInput() {
		return this.syncedLevelDescendInput;
	}

	/** See BrakeInputPayload's own doc (CarEntity-specific brake key, default Space). */
	private boolean syncedBrakeInput;

	public void setSyncedBrakeInput(boolean value) {
		this.syncedBrakeInput = value;
	}

	public boolean getSyncedBrakeInput() {
		return this.syncedBrakeInput;
	}

	public float getThrottle() {
		return this.dataTracker.get(THROTTLE);
	}

	/** Bypasses the ramping in updateThrottle() and sets the tracked throttle value (used for the HUD) directly. */
	protected void setThrottleDirect(float value) {
		this.dataTracker.set(THROTTLE,
				(this.tudursvehiclemod$isOutOfFuel() || this.tudursvehiclemod$isWingLockedFolded()) ? 0f : value);
	}

	/** A throttle-like value that ramps toward +1/-1 while the corresponding input is held, but automatically eases back toward 0 the instant NEITHER input is held. */
	protected static float updateReturnToCenterThrottle(float current, boolean increase, boolean decrease,
														  float step, float returnStep) {
		if (increase && !decrease) {
			return Math.min(1f, current + step);
		}
		if (decrease && !increase) {
			return Math.max(-1f, current - step);
		}
		if (current > 0f) {
			return Math.max(0f, current - returnStep);
		}
		if (current < 0f) {
			return Math.min(0f, current + returnStep);
		}
		return 0f;
	}

	/** Ground vehicles only: gently tilts the vehicle's pitch to lean into whatever slope/step it's currently climbing or descending, instead of staying perfectly.. */
	protected double lastGroundTiltY = Double.NaN;

	/** Ground vehicles only: a gentle visual pitch lean that follows however much this tick's actual Y position changed (climbing/descending a natural slope, e.g. */
	protected void updateGroundTilt(float maxTiltDegrees, float smoothing) {
		if (Double.isNaN(this.lastGroundTiltY)) {
			this.lastGroundTiltY = this.getY();
		}
		double dy = this.getY() - this.lastGroundTiltY;
		this.lastGroundTiltY = this.getY();

		double horizontalSpeed = this.getVelocity().horizontalLength();
		float targetTilt = 0f;
		if (horizontalSpeed > 0.02) {
			double slopeRadians = Math.atan2(dy, Math.max(horizontalSpeed, 0.05));
			targetTilt = MathHelper.clamp((float) -Math.toDegrees(slopeRadians), -maxTiltDegrees, maxTiltDegrees);
		}

		this.setPitch(this.getPitch() + (targetTilt - this.getPitch()) * smoothing);
	}

	/** Only adjusts throttle - callers must also check isPivotTurnRestricted() and skip/zero their own yaw update while it returns true. */
	protected boolean isPivotTurnRestricted(VehicleDefinition def, double turningInput) {
		if (turningInput == 0.0) {
			return false;
		}
		// ALL vehicles should be completely unable to turn while out of fuel, regardless of pivot_turn_throttle's own configuration (including its own default of 0, which would otherwise mean "free to pivot-turn at any speed" even with a dead engine).
		if (this.tudursvehiclemod$isOutOfFuel()) {
			return true;
		}
		if (def.pivotTurnThrottle() <= 0f) {
			return false;
		}
		double pivotTurnThreshold = def.pivotTurnThrottle() * this.tudursvehiclemod$getEffectiveMaxSpeed();
		return Math.abs(this.cruiseSpeed) < pivotTurnThreshold;
	}

	/** Implements pivot_turn_throttle generically for any vehicle sharing cruiseSpeed/getThrottle(). */
	protected float applyPivotTurnThrottleRestriction(VehicleDefinition def, float throttleOutput,
													   double turningInput, float throttleStep) {
		if (def.pivotTurnThrottle() <= 0f || turningInput == 0.0) {
			return throttleOutput;
		}
		// setThrottleDirect() below already zeroes the tracked/displayed throttle while out of fuel, but this method's own RETURN value (what the caller actually uses to drive movement) wasn't gated the same way - letting a fuel-empty vehicle creep slightly while turning left/right despite the HUD correctly showing 0 throttle the whole time.
		if (this.tudursvehiclemod$isOutOfFuel()) {
			return throttleOutput;
		}
		double currentSpeedAbs = Math.abs(this.cruiseSpeed);
		double pivotTurnThreshold = def.pivotTurnThrottle() * this.tudursvehiclemod$getEffectiveMaxSpeed();
		if (currentSpeedAbs >= pivotTurnThreshold) {
			return throttleOutput;
		}
		float rawInputSign = Math.signum((float) this.getSyncedThrottleInput());
		float direction = rawInputSign != 0f ? rawInputSign : (this.cruiseSpeed < 0f ? -1f : 1f);
		// Caps at def.reverseThrottle() (which may be less in magnitude than -1.0) rather than always ramping toward full -1.0 reverse - otherwise a vehicle whose reverseThrottle cap can never actually reach pivotTurnThreshold would keep ramping throttle past its own configured reverse limit indefinitely.
		float requiredThrottle = direction > 0 ? 1.0f : def.reverseThrottle();
		float currentPersisted = this.getThrottle();
		float rampedTowardRequired = requiredThrottle > currentPersisted
				? Math.min(requiredThrottle, currentPersisted + throttleStep)
				: Math.max(requiredThrottle, currentPersisted - throttleStep);
		float effectiveThrottleInput = direction > 0
				? Math.max(throttleOutput, rampedTowardRequired)
				: Math.min(throttleOutput, rampedTowardRequired);
		this.setThrottleDirect(effectiveThrottleInput);
		return effectiveThrottleInput;
	}

	/** Eases the vehicle's SPEED toward throttle * maxSpeed, while always pointing exactly along the current heading (yaw, or yaw+pitch if includePitch). */
	protected Vec3d approachThrottledVelocity(VehicleDefinition def, float throttle, boolean includePitch) {
		return approachThrottledVelocity(def, throttle, includePitch, this.tudursvehiclemod$getEffectiveMaxSpeed());
	}

	/** Same as the 3-arg overload, but with an explicit maxSpeed override - lets SubmarineEntity use a separate dive max speed rather than always sharing surfaced-mode maxSpeed. */
	protected Vec3d approachThrottledVelocity(VehicleDefinition def, float throttle, boolean includePitch, float maxSpeedOverride) {
		// getRotationVec() is vanilla's own, guaranteed-correct yaw/pitch -> direction conversion (the same one used for aiming/throwing).
		Vec3d heading = includePitch
				? this.getRotationVec(1.0f)
				: new Vec3d(-Math.sin(Math.toRadians(this.getYaw())), 0, Math.cos(Math.toRadians(this.getYaw())));
		return approachThrottledVelocity(def, throttle, heading, maxSpeedOverride);
	}

	/** Same as the 3-arg overload above, but with the heading vector supplied directly instead of derived from this.getYaw()/getPitch(). */
	protected Vec3d approachThrottledVelocity(VehicleDefinition def, float throttle, Vec3d heading) {
		return approachThrottledVelocity(def, throttle, heading, this.tudursvehiclemod$getEffectiveMaxSpeed());
	}

	/** All vehicles slow slightly while turning - 0.15 means full sideways input caps target speed at 85%; partial input scales proportionally. */
	protected static final float TURN_SPEED_PENALTY_FACTOR = 0.15f;

	/** Eased version of |sidewaysInput| for the turn-speed penalty, avoiding small repeating judder from raw network-synced tick-to-tick noise (same easing style as CarEntity's turnRate). */
	private float easedTurnIntensity;

	/** Same as the Vec3d overload above, but with an explicit maxSpeed override - see the boolean-includePitch overload's own doc for why this exists. */
	protected Vec3d approachThrottledVelocity(VehicleDefinition def, float throttle, Vec3d heading, float maxSpeedOverride) {
		float targetSpeed = throttle * maxSpeedOverride;
		// A slight negative correction to the target
		// speed itself while turning, rather than touching cruiseSpeed or
		// throttle directly - shared by every vehicle type through this one
		// method, so it applies uniformly without each vehicle's own
		// updateVehicleMovement() needing its own separate handling.
		float rawTurnFraction = MathHelper.clamp(Math.abs((float) this.getSyncedSidewaysInput()), 0f, 1f);
		this.easedTurnIntensity += (rawTurnFraction - this.easedTurnIntensity) * 0.4f;
		targetSpeed *= (1f - TURN_SPEED_PENALTY_FACTOR * this.easedTurnIntensity);
		float newCruiseSpeed = this.tudursvehiclemod$blendCruiseSpeedToward(def, this.cruiseSpeed, targetSpeed, throttle, maxSpeedOverride);
		return heading.multiply(newCruiseSpeed);
	}

	/** Unifies this with autopilot code (CAS/Carrier waypoints, Carrier landing approach, generic Drone Center orbit - AircraftEntity's own tudursvehiclemod$rampAutopilotThrottle() and its own three call sites) that was independently duplicating this exact formula: blends this.cruiseSpeed toward targetSpeed using def.acceleration() as the blend rate (the same "how quickly does this vehicle's own speed actually respond" behavior piloted flight already uses via approachThrottledVelocity(), which now calls this same shared method instead of inlining the blend itself). throttle/maxSpeedOverride are passed through only for the existing diagnostic jump-detection log below, not used in the blend computation itself (targetSpeed alone already reflects any turn-penalty or other adjustment the caller applied on top of throttle*maxSpeedOverride). Returns the resulting cruiseSpeed (also already stored in this.cruiseSpeed). */
	protected float tudursvehiclemod$blendCruiseSpeedToward(VehicleDefinition def, float currentSpeed, float targetSpeed, float throttle, float maxSpeedOverride) {
		float blend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
		float newCruiseSpeed = currentSpeed + (targetSpeed - currentSpeed) * blend;
		// Absent for an entity that had never moved at all - the exact same known pattern already fixed separately in CarEntity's and AircraftEntity's own bespoke ground-throttle code, applied here instead to the shared method itself so every current and future caller of approachThrottledVelocity() benefits uniformly: the exponential blend above never mathematically reaches exactly targetSpeed, leaving a persistent, ever-shrinking-but-never-zero residual that this.cruiseSpeed (a plain, non-networked field computed independently on both client and server) can very gradually diverge on over many ticks - snaps cleanly to the target once close enough, rather than approaching it asymptotically forever. Only an entity that has genuinely been decelerating (not one that was always at rest, whose cruiseSpeed has no residual to diverge on) is ever affected.
		if (Math.abs(targetSpeed - newCruiseSpeed) < 1.0e-4f) {
			newCruiseSpeed = targetSpeed;
		}
		this.cruiseSpeed = newCruiseSpeed;

		return newCruiseSpeed;
	}

	/** Aircraft/helicopter/submarine: makes the vehicle's yaw/pitch follow wherever the pilot is looking, UNLESS free-look is active (see setFreeLook()), in which.. */
	/** Aircraft/helicopter/submarine: makes the vehicle's yaw/pitch follow wherever the pilot is looking, UNLESS free-look is active (see setFreeLook()). */
	protected void followPilotView(LivingEntity pilot, float maxPitch) {
		if (this.isFreeLook()) {
			return;
		}
		this.setYaw(pilot.getYaw());
		this.setPitch(MathHelper.clamp(pilot.getPitch(), -maxPitch, maxPitch));
	}

	/** Ground variant of followPilotView(): only yaw follows the pilot (for turning while taxiing/parked), pitch eases back to level instead of following the pilot's.. */
	protected void followPilotViewGrounded(LivingEntity pilot, float maxDegreesPerTick) {
		if (this.isFreeLook()) {
			return;
		}
		this.setYaw(stepTowardAngle(this.getYaw(), pilot.getYaw(), maxDegreesPerTick));
		this.setPitch(stepTowardAngle(this.getPitch(), 0f, maxDegreesPerTick));
	}

	/** Moves `current` towards `target` by at most maxDelta degrees, the short way around the circle (e.g. */
	protected static float stepTowardAngle(float current, float target, float maxDelta) {
		float delta = MathHelper.wrapDegrees(target - current);
		delta = MathHelper.clamp(delta, -maxDelta, maxDelta);
		return current + delta;
	}

	/** Whether the controlling pilot currently has free-look active (see FreeLookPayload). Reversed for a defaultFreelook-enabled vehicle (see AuxiliaryParts's own doc for the full semantics): the raw held-key state (FREE_LOOK, false by default/true while held) is XORed against getDefinition().defaultFreelook(), so such a vehicle instead starts in free-look with no key held at all, and holding the key switches to a FIXED view. */
	public boolean isFreeLook() {
		boolean rawFreeLook = this.dataTracker.get(FREE_LOOK);
		return this.getDefinition().defaultFreelook() != rawFreeLook;
	}

	/** No-op for vehicles that don't implement FreeCameraVehicle (- toggling had no visual effect but was exploitable for an instant view snap). */
	public void setFreeLook(boolean freeLook) {
		if (!(this instanceof FreeCameraVehicle)) {
			return;
		}
		this.dataTracker.set(FREE_LOOK, freeLook);
	}

	/** Any non-pilot passenger is always effectively free-look, since isFreeLook() only reflects the pilot's own choice. */
	public boolean tudursvehiclemod$isEffectiveFreeLook(Entity viewer) {
		return viewer != this.getControllingPassenger() || this.isFreeLook();
	}

	/** Whether the controlling pilot currently holds the dedicated "descend" key (default Left Control. */
	public boolean isDescending() {
		return this.dataTracker.get(DESCEND);
	}

	/** Reads raw network jump input directly, not player.isJumping() - that field stays false while mounted on this MC version's newer input pipeline. Client-side local prediction still uses isJumping(), which works fine there. */
	protected static boolean tudursvehiclemod$isPilotJumping(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			return serverPlayer.getPlayerInput().jump();
		}
		return player.isJumping();
	}

	/** Set from ModNetworking's DescendPayload handler. */
	public void setDescending(boolean descending) {
		this.dataTracker.set(DESCEND, descending);
	}

	/** Whether hatch-type TogglePart parts (trigger="key", "$hatch" name prefix - and everything else not "$canopy") are currently open - see CANOPY_OPEN's own doc for why canopy-type parts no longer share this flag. Also doubles as SubmarineEntity's own "surfaced" signal there (unrelated to this split - that class's own constructor explicitly sets this true regardless of HATCH_OPEN's own default, so this change doesn't affect it). */
	public boolean isHatchOpen() {
		return this.dataTracker.get(HATCH_OPEN);
	}

	/** Set from ModNetworking's HatchTogglePayload handler. */
	public void setHatchOpen(boolean open) {
		this.dataTracker.set(HATCH_OPEN, open);
	}

	/** Per CANOPY_OPEN's own doc: whether canopy-type TogglePart parts ("$canopy" name prefix) are currently open. */
	public boolean isCanopyOpen() {
		return this.dataTracker.get(CANOPY_OPEN);
	}

	/** Per CANOPY_OPEN's own doc. */
	public void setCanopyOpen(boolean open) {
		this.dataTracker.set(CANOPY_OPEN, open);
	}

	/** Still moves BOTH hatch and canopy to the same new state together (rather than only HATCH_OPEN) - preserves the player's existing single-button experience for manually opening/closing everything, even though the SPAWN/MOUNT auto-behaviors for the two have since diverged (see CANOPY_OPEN's own doc). */
	public void toggleHatch() {
		boolean newState = !isHatchOpen();
		setHatchOpen(newState);
		setCanopyOpen(newState);
	}

	/** True by default (always accepts the toggle) - overridden by
	 * SubmarineEntity to gate surfacing on being at/above the water and
	 * level attitude. Returns whether the toggle was actually accepted. */
	public boolean tudursvehiclemod$tryToggleHatch() {
		toggleHatch();
		return true;
	}

	/** See MANUAL_MODE's own doc. */
	public boolean isManualMode() {
		return this.dataTracker.get(MANUAL_MODE);
	}

	public void setManualMode(boolean value) {
		this.dataTracker.set(MANUAL_MODE, value);
	}

	/** True = wings extended/usable, false = folded (the default, per a
	 * direct request - spawns folded rather than extended). See
	 * tryToggleWingFold()'s own doc for the gating rules around changing
	 * this. */
	public boolean isWingFoldOpen() {
		return this.dataTracker.get(WING_FOLD_OPEN);
	}

	private void setWingFoldOpen(boolean open) {
		this.dataTracker.set(WING_FOLD_OPEN, open);
	}

	/** First WingSweepConfig found among this vehicle's own "wing_fold"
	 * TogglePart entries, or an all-default (non-variable) one if it has
	 * none - used as this vehicle's single, shared wing-fold behavior even
	 * if (unusually) several wing parts somehow disagree. */
	private WingSweepConfig tudursvehiclemod$getWingSweepConfig() {
		for (TogglePart part : this.getDefinition().toggleParts()) {
			if ("wing_fold".equals(part.trigger()) && part.wingSweep().isPresent()) {
				return part.wingSweep().get();
			}
		}
		return new WingSweepConfig(false, 0f);
	}

	/** A non-variable-sweep wing can only fold/unfold while stationary; variable-sweep can sweep at any speed in flight. Returns false if rejected. */
	public boolean tryToggleWingFold() {
		WingSweepConfig config = this.tudursvehiclemod$getWingSweepConfig();
		if (!config.variableSweepWing() && this.getVelocity().horizontalLength() > 0.05) {
			return false;
		}
		setWingFoldOpen(!isWingFoldOpen());
		return true;
	}

	/** Sets WING_FOLD_OPEN directly, bypassing tryToggleWingFold()'s own speed gate entirely - for updateDroneAutopilot()'s own "no pilot to manually deploy" automatic case only, where the gate could otherwise permanently reject the wing open forever (wing-fold fix). A genuine player-initiated toggle still goes through tryToggleWingFold() and its own gate unchanged. */
	public void tudursvehiclemod$forceWingFoldOpen(boolean open) {
		setWingFoldOpen(open);
	}

	/** Effective max speed accounting for a variable-sweep wing's SweepWingSpeed cap while folded, AND every currently-visible AmmoPart's own display_penalty speed component. Falls back to def.maxSpeed() (with only the ammo-part multiplier applied) otherwise. */
	public float tudursvehiclemod$getEffectiveMaxSpeed() {
		float defaultMaxSpeed = this.getDefinition().maxSpeed();
		WingSweepConfig config = this.tudursvehiclemod$getWingSweepConfig();
		float wingAdjustedMaxSpeed = defaultMaxSpeed;
		if (config.variableSweepWing() && this.tudursvehiclemod$isWingFolded()) {
			wingAdjustedMaxSpeed = config.sweepWingSpeed() > 0f ? config.sweepWingSpeed() : defaultMaxSpeed;
		}
		return wingAdjustedMaxSpeed * this.tudursvehiclemod$getAmmoPartSpeedMultiplier();
	}

	/** Effective turn/maneuverability rate (this vehicle's own def.turnSpeed(), the base value every subclass' own turn/roll/pitch rate is ultimately derived from) accounting for every currently-visible AmmoPart's own display_penalty maneuverability component. */
	public float tudursvehiclemod$getEffectiveTurnSpeed() {
		return this.getDefinition().turnSpeed() * this.tudursvehiclemod$getAmmoPartManeuverabilityMultiplier();
	}

	/** Multiplier (0.1, never negative) from every currently-visible AmmoPart's own display_penalty speed component (see that field's own doc), stacked ADDITIVELY (two 10% penalties combine to a 20% total reduction, not a compounded ~19%) - simpler and more predictable for an addon author combining several parts than a multiplicative stack would be. 1.0 (no effect at all) when no currently-visible part configures this. */
	public float tudursvehiclemod$getAmmoPartSpeedMultiplier() {
		float totalPenaltyPercent = 0f;
		for (com.example.tudursvehiclemod.asset.AmmoPart part : this.getDefinition().ammoParts()) {
			if (this.tudursvehiclemod$isAmmoPartVisible(part)) {
				totalPenaltyPercent += part.speedPenaltyPercent();
			}
		}
		return Math.max(0f, 1f - totalPenaltyPercent / 100f);
	}

	/** Same as tudursvehiclemod$getAmmoPartSpeedMultiplier() above, but for each AmmoPart's own maneuverability penalty component instead. */
	public float tudursvehiclemod$getAmmoPartManeuverabilityMultiplier() {
		float totalPenaltyPercent = 0f;
		for (com.example.tudursvehiclemod.asset.AmmoPart part : this.getDefinition().ammoParts()) {
			if (this.tudursvehiclemod$isAmmoPartVisible(part)) {
				totalPenaltyPercent += part.maneuverabilityPenaltyPercent();
			}
		}
		return Math.max(0f, 1f - totalPenaltyPercent / 100f);
	}

	/** True once this vehicle's own wing_fold part(s) are (mostly) folded -
	 * see isWingFoldOpen()'s own doc for the 0/1 convention. False for any
	 * vehicle with no "wing_fold" parts at all. */
	public boolean tudursvehiclemod$isWingFolded() {
		boolean hasWingFold = this.getDefinition().toggleParts().stream()
				.anyMatch(part -> "wing_fold".equals(part.trigger()));
		if (!hasWingFold) {
			return false;
		}
		float foldProgress = this.getTogglePartProgress(this.tudursvehiclemod$getFirstWingFoldPartName());
		// A normal folding wing has progress 0 = extended/open, 1 = folded (MC Heli's own genuine AddPartWing convention); a variable-sweep wing is the OPPOSITE, progress 0 = folded/swept, 1 = extended.
		boolean variableSweep = this.tudursvehiclemod$getWingSweepConfig().variableSweepWing();
		return variableSweep ? foldProgress < 0.5f : foldProgress > 0.5f;
	}

	/** True once fully extended (not just "mostly" -). Locks at fold start, releases only once fully unfolded. True if no "wing_fold" parts at all. */
	public boolean tudursvehiclemod$isWingFullyExtended() {
		boolean hasWingFold = this.getDefinition().toggleParts().stream()
				.anyMatch(part -> "wing_fold".equals(part.trigger()));
		if (!hasWingFold) {
			return true;
		}
		float foldProgress = this.getTogglePartProgress(this.tudursvehiclemod$getFirstWingFoldPartName());
		// Per the same direction split as isWingFolded() above: a normal folding wing's own fully-extended end is progress 0; a variable-sweep wing's own fully-extended end is progress 1 instead.
		boolean variableSweep = this.tudursvehiclemod$getWingSweepConfig().variableSweepWing();
		boolean atFullyExtendedProgress = variableSweep ? foldProgress >= 0.99f : foldProgress <= 0.01f;
		return this.isWingFoldOpen() && atFullyExtendedProgress;
	}

	/** Non-variable-sweep wing that isn't fully extended - gates throttle to 0 directly, rather than touching max speed. */
	public boolean tudursvehiclemod$isWingLockedFolded() {
		return !this.tudursvehiclemod$getWingSweepConfig().variableSweepWing()
				&& !this.tudursvehiclemod$isWingFullyExtended();
	}

	private String tudursvehiclemod$getFirstWingFoldPartName() {
		for (TogglePart part : this.getDefinition().toggleParts()) {
			if ("wing_fold".equals(part.trigger())) {
				return part.part();
			}
		}
		return "";
	}

	/** Cosmetic bank/tilt from A/D input that springs back to level when released. */
	protected void updateRoll(PlayerEntity pilot, float maxRollDegrees, float smoothing) {
		this.prevRoll = this.roll;
		// Uses this.syncedSidewaysInput rather than pilot.sidewaysSpeed directly.
		float target = -this.syncedSidewaysInput * maxRollDegrees;
		this.roll += (target - this.roll) * smoothing;
	}

	/** Full 360-degree roll for aircraft: holding A/D keeps rotating at a constant rate (a proper barrel roll) instead of springing to a capped bank angle; releasing.. */
	protected void updateContinuousRoll(PlayerEntity pilot, float degreesPerTick, float autoLevelSmoothing) {
		this.prevRoll = this.roll;
		// Uses this.syncedSidewaysInput rather than pilot.sidewaysSpeed directly.
		if (this.syncedSidewaysInput != 0) {
			this.roll = MathHelper.wrapDegrees(this.roll - this.syncedSidewaysInput * degreesPerTick);
		} else {
			float nearest90 = Math.round(this.roll / 90f) * 90f;
			float delta = MathHelper.wrapDegrees(nearest90 - this.roll);
			this.roll = MathHelper.wrapDegrees(this.roll + delta * autoLevelSmoothing);
		}
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(VEHICLE_ID, defaultDefinitionId().toString());
		builder.add(THROTTLE, 0.0f);
		builder.add(TREE_COLLISION_BRAKE_TICKS, 0);
		builder.add(RUNWAY_CARRY_WHEEL_SYNC, false);
		builder.add(FREE_LOOK, false);
		builder.add(DESCEND, false);
		// A freshly-spawned vehicle's own hatch now starts closed. SubmarineEntity's own constructor explicitly overrides this back to true immediately after construction (see that constructor's own doc) - unaffected by this change.
		builder.add(HATCH_OPEN, false);
		// Per CANOPY_OPEN's own doc: unaffected by the above - canopy's own spawn-state behavior is unchanged.
		builder.add(CANOPY_OPEN, true);
		builder.add(WING_FOLD_OPEN, false);
		builder.add(GEAR_DEPLOYED, true);
		builder.add(CARRIER_WEAPON_BAY_OVERRIDE_ACTIVE, false);
		builder.add(CARRIER_WEAPON_BAY_OVERRIDE_VALUE, false);
		builder.add(DRONE_ACTIVE, false);
		builder.add(GROUND_ROUTE_ACTIVE, false);
		builder.add(GROUND_AUTOPILOT_THROTTLE_INPUT, 0f);
		builder.add(GROUND_AUTOPILOT_STEER_INPUT, 0f);
		builder.add(MANUAL_MODE, false);
		builder.add(FIRING_WEAPON_BITMASK, 0);
		builder.add(ACTUAL_FIRE_BITMASK, 0);
		builder.add(RECOIL_CURRENT_MAGNITUDE, 0.0f);
		builder.add(RECOIL_AIM_YAW, 0.0f);
		builder.add(REMOTE_CONTROLLER_UUID, "");
		builder.add(WEAPON_AMMO_SYNC, "");
		builder.add(MISSILE_LOCK_SYNC, "");
		builder.add(MISSILE_LOCK_REQUIRED_SYNC, "");
		// Sentinel (not yet initialized).
		builder.add(HEALTH, -1.0f);
		builder.add(DESTROYED, false);
		builder.add(SEARCH_LIGHT_ON, false);
		builder.add(NAV_LIGHTS_ON, true);
		builder.add(SEARCH_LIGHT_LAST_YAW, Float.NaN);
		builder.add(SEARCH_LIGHT_LAST_PITCH, Float.NaN);
		builder.add(SINK_PATTERN, 0);
		builder.add(SINK_TARGET_PITCH, 0f);
		builder.add(SINK_TARGET_ROLL, 0f);
		builder.add(SINK_TICKS, 0);
		builder.add(FUEL, -1.0f);
		builder.add(SELECTED_WEAPON_INDEX, -1);
		builder.add(CARRIER_LOCK_MODE_ACTIVE, false);
		builder.add(HIGHLIGHT_ACTIVE, false);
		builder.add(HIGHLIGHTED_ENTITY_IDS, "");
		builder.add(SEAT_ASSIGNMENTS, "");
	}

	/** Hitbox refresh hooked here (not setVehicleDefinitionId(), server-only) so it fires consistently on both client and server. */
	@Override
	public void onTrackedDataSet(net.minecraft.entity.data.TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (data == VEHICLE_ID) {
			this.definition = null; // force re-resolve, and re-apply the hitbox below
			refreshDimensionsFromDefinition();
		}
	}

	/** Which JSON-defined vehicle this specific entity instance currently represents. */
	public void setVehicleDefinitionId(Identifier id) {
		this.dataTracker.set(VEHICLE_ID, id.toString());
		// Definition re-resolution and hitbox refresh happen uniformly via onTrackedDataSet() above.
		// Full health for a freshly-assigned vehicle type.
		if (this.dataTracker.get(HEALTH) < 0f) {
			this.dataTracker.set(HEALTH, this.getDefinition().maxHealth());
		}
		// Starts empty, not full.
		if (this.dataTracker.get(FUEL) < 0f) {
			this.dataTracker.set(FUEL, 0f);
		}
	}

	/** "/tvm clean -d" resets ALL persisted vehicle data by spawning a fresh replacement entity (see command.TvmCommand's own doc) - needed to tell that fresh entity which vehicle definition it should represent, since only setVehicleDefinitionId() existed before this. Null if VEHICLE_ID's own raw string is somehow malformed (should never actually happen for a live, functioning vehicle). */
	public Identifier getVehicleDefinitionId() {
		String raw = this.dataTracker.get(VEHICLE_ID);
		return Identifier.tryParse(raw);
	}

	/** Current health, out of getMaxHealth(). */
	public float getHealth() {
		return this.dataTracker.get(HEALTH);
	}

	public float getMaxHealth() {
		return this.getDefinition().maxHealth();
	}

	/** True while this wreck is playing a sinking animation. Every vehicle type's own buoyancy checks this and stands down, since a hull spring pulling towards the surface would otherwise fight the sinking and win.
	 *
	 * Deliberately not restricted to ships and submarines: any destroyed vehicle that happens to be on water sinks. */
	public boolean tudursvehiclemod$isSinking() {
		return this.dataTracker.get(SINK_PATTERN) != 0;
	}

	/** Picks this wreck's own sinking pattern, once, when a destroyed vehicle is found to be on water. Server-only - the result reaches the client through SINK_PATTERN, which is exactly why that is a tracked field.
	 *
	 * Drawn RANDOMLY here, at the moment of destruction, rather than derived from this vehicle's own UUID. Deriving it from the UUID meant the pattern was effectively fixed the instant the vehicle was summoned and carried around for its whole life for something that only ever matters once - and, more visibly, meant a given vehicle ALWAYS sank the same way, which is what made repeats look non-random. The guard above still ensures it is drawn only once per wreck, so nothing re-rolls mid-animation. */
	private void tudursvehiclemod$beginSinkingIfOnWater() {
		if (this.dataTracker.get(SINK_PATTERN) != 0) {
			return;
		}
		if (!this.tudursvehiclemod$hasWaterNearby()) {
			return;
		}
		// The two target angles are drawn HERE, once, and everything the wreck does from now on follows from them - there is no longer a numbered pattern to look up.
		this.dataTracker.set(SINK_TARGET_PITCH, tudursvehiclemod$drawCenterBiasedAngle(SINK_MAX_PITCH_DEGREES));
		this.dataTracker.set(SINK_TARGET_ROLL, tudursvehiclemod$drawCenterBiasedAngle(SINK_MAX_ROLL_DEGREES));
		this.dataTracker.set(SINK_PATTERN, 1);
		this.dataTracker.set(SINK_TICKS, 0);
	}

	/** Draws one target angle in [-maxDegrees, maxDegrees], biased so values near zero are more likely than values near the extremes.
	 *
	 * Squares a uniform draw while keeping its own sign: for u uniform on [-1, 1], sign(u)*u^2 has a density that is HIGHEST at 0 and falls off towards ±1 (the density of x=u^2 for a uniform u is 1/(2*sqrt(x)), which diverges at 0 and is smallest at 1) - the opposite of the uniform draw this replaced, where every magnitude up to the maximum was equally likely (which is what put a full roll-over at roughly the same odds as a barely-perceptible list). Verified by simulation: the share landing past 90 degrees dropped from about half to under a third.
	 *
	 * Draws from this.getEntityWorld().getRandom() - the WORLD's own random source - rather than this.random (the per-entity one, inherited from vanilla's own Entity). This was changed after a run of tests all landing on the same sign in a row: this.random's own seeding is vanilla's, not something this mod controls or has verified, and classic LCG-family generators (several of vanilla's own Random implementations are exactly this family) are documented to correlate their EARLY outputs when seeded from nearby values - which repeatedly spawning near-identical test targets in a short session could plausibly produce, if that seed is tied to anything sequential like entity IDs or a coarse clock. The world's own random is continuously advanced by everything else happening in the world, not just this one entity's own creation, so it carries none of that risk regardless of how the per-entity one turns out to be seeded. */
	private float tudursvehiclemod$drawCenterBiasedAngle(float maxDegrees) {
		float raw = this.getEntityWorld().getRandom().nextFloat() * 2f - 1f;
		return raw * Math.abs(raw) * maxDegrees;
	}

	/** Applies this tick's own sinking motion - a steady descent plus whatever list and trim this wreck's own pattern calls for.
	 *
	 * Runs on BOTH sides. The server advances SINK_TICKS and the client reads it, so the two agree on how far through the animation this wreck is even if the client only came into view partway through. Attitude is eased towards the pattern's own targets rather than snapped, so a wreck settles into its list the way a flooding hull actually would.
	 *
	 * The descent deliberately does not accelerate: a wreck that fell like a stone would be gone before the despawn timer (VehicleModServerConfig's own destroyedVehicleDespawnSeconds) had a chance to show any of this. */
	private void tudursvehiclemod$applySinkingMotion() {
		int pattern = this.dataTracker.get(SINK_PATTERN);
		if (pattern == 0) {
			return;
		}
		if (!this.getEntityWorld().isClient()) {
			this.dataTracker.set(SINK_TICKS, this.dataTracker.get(SINK_TICKS) + 1);
		}
		int sinkTicks = this.dataTracker.get(SINK_TICKS);

		// Every value below is now derived PURELY from sinkTicks, with no dependence on the previous tick's own attitude.
		//
		// WHY THAT WAS THE BUG: this used to ease incrementally - "move the CURRENT pitch a fraction of the way towards the target". That form only produces the intended curve if a side sees every single tick, starting from the same value. It does not survive being run on two sides at once: pitch is a vanilla-synced rotation, so the server pushes its own value to the client every tick and the client interpolates towards it, while this method simultaneously recomputed pitch locally from whatever value the client happened to hold at that instant. The two fought - the client's own easing pulled away from the synced value, the next packet yanked it back - which is exactly the "rolls back, then jumps" behaviour reported. Roll had the same fragility for a different reason: it is an unsynced field, so a client joining late, or missing ticks, started its accumulation from a different place and simply stayed wrong.
		//
		// A closed form removes the whole class of problem: given the same sinkTicks (which IS synced) both sides compute the identical angle no matter what either of them held a moment ago, so the vanilla rotation sync can only ever confirm what the client already calculated instead of contradicting it.
		float targetPitch = this.dataTracker.get(SINK_TARGET_PITCH);
		float targetRoll = this.dataTracker.get(SINK_TARGET_ROLL);

		// PrevRoll was being written as "whatever roll happened to hold a moment ago", which is only correct if this method runs EXACTLY once per tick. It doesn't always - the log shows frames where prevRoll equals roll exactly, which happens when this runs a second time within the same tick: the first pass sets prevRoll to the old value and roll to the new one, then the second pass overwrites prevRoll with that same new roll (sinkTicks hasn't advanced, so roll recomputes identically). prevRoll == roll leaves getRoll(tickDelta) with nothing to interpolate across, so the model sits still for that tick and then jumps on the next one - exactly the wobble reported, and exactly as intermittent as the double-tick itself.
		//
		// Deriving the PREVIOUS attitude from sinkTicks - 1, the same closed way the current one comes from sinkTicks, makes this method idempotent: running it once, twice or ten times in a tick produces identical values every time, because nothing is read back from the object's own prior state. That removes the whole failure mode rather than chasing down why the extra call happens.
		this.prevRoll = tudursvehiclemod$sinkingRollAt(sinkTicks - 1, targetRoll);
		this.roll = tudursvehiclemod$sinkingRollAt(sinkTicks, targetRoll);

		float pitch = tudursvehiclemod$sinkingPitchAt(sinkTicks, targetPitch);
		float previousPitch = tudursvehiclemod$sinkingPitchAt(sinkTicks - 1, targetPitch);
		this.tudursvehiclemod$setSinkingPitch(pitch, previousPitch);

		// A flat rate, with no build-up at all. The gravity-then-terminal-speed form this replaced only actually accelerated for its own first few ticks before hitting the cap anyway, so the ramp-in it was meant to provide was never really visible - a single constant is both what was asked for and simpler to reason about.
		Vec3d velocity = this.getVelocity();
		this.setVelocity(velocity.x * SINK_HORIZONTAL_DAMPING, SINK_DESCENT_SPEED, velocity.z * SINK_HORIZONTAL_DAMPING);
	}

	/** The render-time attitude of a sinking wreck, computed directly from its own synced sinkTicks instead of being read back off the entity's rotation fields.
	 *
	 * WHY THIS EXISTS AT ALL: setting pitch on the entity means competing with vanilla's own client-side rotation interpolation. The server's pitch reaches the client quantised to a single byte (1.40625-degree steps) as a lerp TARGET, and vanilla steps the entity's own pitch towards that target over several ticks - after this mod has already written its own value for the tick. When one of those steps lands past the smooth curve, that frame renders a slightly larger angle than the curve ever calls for, and the next frame snaps back. That is a single-frame overshoot in an otherwise continuous motion, which is exactly what was reported, and no amount of correctness on the entity side fixes it because the overwrite happens afterwards.
	 *
	 * Bypassing the entity's rotation entirely removes the contest rather than trying to win it: both values here are pure functions of sinkTicks (see tudursvehiclemod$applySinkingMotion()'s own doc), so the client can derive exactly what the server has without any rotation packet being involved. Interpolated across sinkTicks-1 to sinkTicks by tickDelta, giving smooth sub-tick motion from the same closed form.
	 *
	 * Returns NaN when this vehicle isn't sinking, so callers fall back to the normal rotation path - see VehicleEntityRenderer's own use. */
	public float tudursvehiclemod$getSinkingRenderPitch(float tickDelta) {
		if (!this.tudursvehiclemod$isSinking()) {
			return Float.NaN;
		}
		int sinkTicks = this.dataTracker.get(SINK_TICKS);
		float targetPitch = this.dataTracker.get(SINK_TARGET_PITCH);
		return MathHelper.lerp(tickDelta,
				tudursvehiclemod$sinkingPitchAt(sinkTicks - 1, targetPitch),
				tudursvehiclemod$sinkingPitchAt(sinkTicks, targetPitch));
	}

	/** Roll counterpart of tudursvehiclemod$getSinkingRenderPitch() - see that method's own doc. Roll isn't synced by vanilla at all, so it was never overwritten the way pitch was, but deriving it the same way keeps both axes on one consistent source and makes the render path independent of prevRoll's own bookkeeping. */
	public float tudursvehiclemod$getSinkingRenderRoll(float tickDelta) {
		if (!this.tudursvehiclemod$isSinking()) {
			return Float.NaN;
		}
		int sinkTicks = this.dataTracker.get(SINK_TICKS);
		float targetRoll = this.dataTracker.get(SINK_TARGET_ROLL);
		return MathHelper.lerp(tickDelta,
				tudursvehiclemod$sinkingRollAt(sinkTicks - 1, targetRoll),
				tudursvehiclemod$sinkingRollAt(sinkTicks, targetRoll));
	}

	/** This wreck's own roll at a given tick of its own sinking - a pure function of that tick, with no dependence on anything the object currently holds (see tudursvehiclemod$applySinkingMotion()'s own doc for why that matters). */
	private static float tudursvehiclemod$sinkingRollAt(int sinkTicks, float targetRoll) {
		return targetRoll * tudursvehiclemod$sinkingEaseAt(sinkTicks);
	}

	/** This wreck's own pitch at a given tick of its own sinking - eases out towards its target, then decays back to level after the ramp and its own delay. Pure function of the tick, as above. */
	private static float tudursvehiclemod$sinkingPitchAt(int sinkTicks, float targetPitch) {
		int ticksSinceLevellingBegan = sinkTicks - SINK_ATTITUDE_RAMP_TICKS - SINK_PITCH_LEVEL_DELAY_TICKS;
		if (ticksSinceLevellingBegan > 0) {
			return targetPitch * (float) Math.pow(1f - SINK_PITCH_LEVEL_SMOOTHING, ticksSinceLevellingBegan);
		}
		return targetPitch * tudursvehiclemod$sinkingEaseAt(sinkTicks);
	}

	/** Ease-out cubic over the attitude ramp - quick to lean at first, slowing as it settles. Clamped at both ends so a tick index of -1 (used for the PREVIOUS value at the very first tick) stays at 0 rather than going negative. */
	private static float tudursvehiclemod$sinkingEaseAt(int sinkTicks) {
		float progress = MathHelper.clamp(sinkTicks / (float) SINK_ATTITUDE_RAMP_TICKS, 0f, 1f);
		float remaining = 1f - progress;
		return 1f - remaining * remaining * remaining;
	}

	/** Applies a sinking wreck's own pitch AND the previous-tick pitch its render interpolation reads, so nothing is left for vanilla's rotation interpolation to smooth towards.
	 *
	 * Both values are supplied by the caller as closed forms of the tick index rather than being carried over from the last call, which is what makes the whole path idempotent - see tudursvehiclemod$applySinkingMotion()'s own doc. On the client this deliberately sets the PREVIOUS pitch alongside the current one, so getPitch(tickProgress) interpolates between two values this mod computed rather than towards the coarse, byte-rounded figure that arrived over the network. Correct here only because the attitude is a pure function of the synced sinkTicks and therefore identical on both sides. */
	private void tudursvehiclemod$setSinkingPitch(float pitch, float previousPitch) {
		this.setPitch(pitch);
		if (this.getEntityWorld().isClient()) {
			this.lastPitch = previousPitch;
		}
	}

	/** Per the same reasoning every vehicle type's own water detection already uses: scans a short vertical range around this vehicle for water, rather than relying on isTouchingWater().
	 *
	 * isTouchingWater() tests this entity's own bounding box against water, which is NOT reliable for a floating hull - a vehicle sitting on the surface can easily have its own box entirely above the waterline and read as dry. That is exactly why AircraftEntity/ShipEntity/SubmarineEntity each scan instead (see their own tudursvehiclemod$findWaterSurfaceY() docs), and it is the prime suspect for the sinking animation never starting at all. */
	private boolean tudursvehiclemod$hasWaterNearby() {
		// Deliberately mirrors ShipEntity's own findWaterSurfaceY() scan exactly - same BlockPos.ofFloored() + add() form, same getFluidState().isIn(FluidTags.WATER) test - rather than a separately-invented equivalent, so this stays consistent with the water detection every vehicle type already relies on.
		net.minecraft.util.math.BlockPos basePos =
				net.minecraft.util.math.BlockPos.ofFloored(this.getX(), this.getY(), this.getZ());
		for (int dy = SINK_WATER_SCAN_DOWN; dy <= SINK_WATER_SCAN_UP; dy++) {
			net.minecraft.util.math.BlockPos checkPos = basePos.add(0, dy, 0);
			if (this.getEntityWorld().getFluidState(checkPos).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** Lowest offset tudursvehiclemod$hasWaterNearby() scans, relative to this vehicle's own Y. */
	private static final int SINK_WATER_SCAN_DOWN = -3;

	/** Highest offset tudursvehiclemod$hasWaterNearby() scans - a hull can float with its own origin above the surface. */
	private static final int SINK_WATER_SCAN_UP = 2;

	/** Largest magnitude the drawn target pitch can take, in degrees. */
	private static final float SINK_MAX_PITCH_DEGREES = 90f;

	/** Largest magnitude the drawn target roll can take, in degrees - wide enough for a wreck to roll completely over. */
	private static final float SINK_MAX_ROLL_DEGREES = 180f;

	/** How fast a wreck descends, in blocks per tick - a flat rate, applied every tick with no acceleration. Matches the speed the previous gravity-plus-terminal-speed form actually settled at, so the descent looks the same minus its own brief initial ramp-in. */
	private static final double SINK_DESCENT_SPEED = -0.00375;

	/** How much of its own remaining horizontal motion a wreck keeps each tick, so it coasts to a stop rather than sliding along under the surface. */
	private static final double SINK_HORIZONTAL_DAMPING = 0.94;

	/** Ticks over which a wreck progresses to its own pattern's full attitude - see tudursvehiclemod$applySinkingMotion()'s own doc for why this spans the descent rather than just its start.
	 *
	 * Lengthened tenfold, from 400 ticks (20 seconds) to 4000 (200 seconds). Stretching the ramp is what divides the per-tick change by ten - the curve's own shape and its final angle are untouched, it simply takes ten times as long to get there. */
	private static final int SINK_ATTITUDE_RAMP_TICKS = 4000;

	/** Ticks to wait after the attitude ramp finishes before pitch starts easing towards level at all - see tudursvehiclemod$applySinkingMotion()'s own doc. 100 ticks = 5 seconds. */
	private static final int SINK_PITCH_LEVEL_DELAY_TICKS = 100;

	/** Per-tick easing factor pitch uses once it starts levelling - see tudursvehiclemod$applySinkingMotion()'s own doc. Applied as a closed-form power of sinkTicks rather than once per tick (see that doc). Deliberately small so the levelling reads as unmistakably slow, before the exponential's own natural slowdown near zero is even accounted for. */
	private static final float SINK_PITCH_LEVEL_SMOOTHING = 0.01f;

	/** True once this vehicle's health has reached 0. */
	public boolean tudursvehiclemod$isDestroyed() {
		return this.dataTracker.get(DESTROYED);
	}

	/** Server-only destroyed-ticks counter for tick()'s own despawn timer - not saved to NBT (a fresh timer after a restart is fine). */
	private int ticksSinceDestroyed;

	/** Current internal fuel, out of getMaxFuel(). */
	public float getFuel() {
		return this.dataTracker.get(FUEL);
	}

	/** Synced selected weapon index (see SELECTED_WEAPON_INDEX's own doc), or
	 * -1 if none selected yet. */
	public int tudursvehiclemod$getSelectedWeaponIndex() {
		return this.dataTracker.get(SELECTED_WEAPON_INDEX);
	}

	public void tudursvehiclemod$setSelectedWeaponIndex(int weaponIndex) {
		this.dataTracker.set(SELECTED_WEAPON_INDEX, weaponIndex);
	}

	/** See CARRIER_LOCK_MODE_ACTIVE's own doc. */
	public boolean tudursvehiclemod$isCarrierLockModeActive() {
		return this.dataTracker.get(CARRIER_LOCK_MODE_ACTIVE);
	}

	/** Flips CARRIER_LOCK_MODE_ACTIVE - see that field's own doc. */
	public void tudursvehiclemod$toggleCarrierLockMode() {
		this.dataTracker.set(CARRIER_LOCK_MODE_ACTIVE, !this.dataTracker.get(CARRIER_LOCK_MODE_ACTIVE));
	}

	/** See HIGHLIGHT_ACTIVE's own doc. */
	public boolean tudursvehiclemod$isHighlighted() {
		return this.dataTracker.get(HIGHLIGHT_ACTIVE);
	}

	/** See HIGHLIGHT_ACTIVE's own doc - called fresh every tick by whoever is doing the targeting (never left set without also being actively re-asserted or explicitly cleared), so nothing can ever get permanently "stuck" highlighted the way setGlowing() reportedly could. */
	public void tudursvehiclemod$setHighlighted(boolean highlighted) {
		if (this.dataTracker.get(HIGHLIGHT_ACTIVE) != highlighted) {
			this.dataTracker.set(HIGHLIGHT_ACTIVE, highlighted);
		}
	}

	/** Parses HIGHLIGHTED_ENTITY_IDS's own synced "id,id,.." encoding - read by client.render.TargetHighlightRenderer, once per currently-loaded AbstractVehicleEntity, to know which non-vehicle entities THIS one currently wants highlighted (see that field's own doc). Empty set for the common case (this vehicle isn't currently tracking any non-vehicle target at all). */
	public java.util.Set<Integer> tudursvehiclemod$getHighlightedEntityIds() {
		String encoded = this.dataTracker.get(HIGHLIGHTED_ENTITY_IDS);
		if (encoded.isEmpty()) {
			return java.util.Set.of();
		}
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (String part : encoded.split(",")) {
			try {
				ids.add(Integer.parseInt(part));
			} catch (NumberFormatException ignored) {
				// Defensive - shouldn't happen given this is entirely this mod's own encoding, but a malformed value here shouldn't crash rendering.
			}
		}
		return ids;
	}

	/** Attempts to lock whatever is currently under shooter's own crosshair, assigning it to a wingman to attack - see AircraftEntity's own override for the real logic (which needs that class's own formation registries). No-op base-class default for any non-aircraft vehicle, where this feature doesn't apply at all. */
	public void tudursvehiclemod$tryLockCarrierTarget(net.minecraft.server.network.ServerPlayerEntity shooter) {
	}

	/** Releases every wingman currently following this vehicle from whatever target they're each individually tracking, returning them all to ordinary formation-follow - see AircraftEntity's own override for the real logic. No-op base-class default for any non-aircraft vehicle. */
	public void tudursvehiclemod$releaseAllCarrierLocks() {
	}

	/** This vehicle's own current max fuel - def.maxFuel() plus a bonus from every currently-equipped WeaponType.DROP_TANK weapon's own remaining ammo (see WeaponType.DROP_TANK's own doc): each such weapon contributes its own remaining ammo count times its own fuelPerAmmo() (WeaponStats' own new field). Firing a drop tank (reducing its own remaining ammo by 1, same as any other weapon) reduces this bonus accordingly - tudursvehiclemod$clampFuelToMaxFuel() is called right after ammo consumption in tryFireWeapon() so any current fuel now above this new, lower max is clamped down immediately rather than lingering until the next unrelated fuel update. */
	public float getMaxFuel() {
		float maxFuel = this.getDefinition().maxFuel();
		List<com.example.tudursvehiclemod.asset.WeaponDefinition> weapons = this.getDefinition().weapons();
		for (int i = 0; i < weapons.size(); i++) {
			com.example.tudursvehiclemod.asset.WeaponDefinition weapon = weapons.get(i);
			if (weapon.weaponType() != com.example.tudursvehiclemod.asset.WeaponType.DROP_TANK || weapon.fuelPerAmmo() <= 0f) {
				continue;
			}
			int magazineSize = weapon.magazineSize();
			int[] ammoState = this.getWeaponAmmoState(i);
			int ammoRemaining = ammoState[0] >= 0 ? ammoState[0] : magazineSize;
			maxFuel += Math.max(0, ammoRemaining) * weapon.fuelPerAmmo();
		}
		return maxFuel;
	}

	/** Clamps this vehicle's own current fuel down to getMaxFuel() if it's now above it: firing a WeaponType.DROP_TANK weapon (reducing max fuel - see getMaxFuel()'s own doc) discards any excess fuel rather than leaving it above the new, lower max. A no-op (never raises fuel) whenever current fuel is already at or below the current max. */
	public void tudursvehiclemod$clampFuelToMaxFuel() {
		float maxFuel = this.getMaxFuel();
		if (this.getFuel() > maxFuel) {
			this.dataTracker.set(FUEL, maxFuel);
		}
	}

	/** True for a vehicle configured with a NEGATIVE fuel_consumption (VehicleExtras' own doc) -
	 * one that doesn't use fuel at all, a human-pedalled bicycle being the motivating case. Every
	 * fuel-aware consumer (isOutOfFuel(), the consumption tick, and every fuel gauge/low-fuel
	 * warning across the HUD, vehicle menu, and Drone Center) checks this FIRST and treats such a
	 * vehicle as permanently, fully fuelled - regardless of its own actual internal fuel value or
	 * max_fuel setting - rather than each trying to fake a "full" numeric reading of its own,
	 * which would misbehave for the edge case of a vehicle whose own max_fuel is also 0. */
	public boolean tudursvehiclemod$isFuelless() {
		return this.getDefinition().fuelConsumptionPerSecond() < 0f;
	}

	/** True once fuel has run out. reports false while isFollowingGroundRoute() is true, regardless of actual fuel level - matching the same established precedent as a drone-linked aircraft's own entirely fuel-independent flight (see AircraftEntity's own getSpinningPartSpeedMultiplier() doc). Fixed HERE, at the source, rather than at each of this method's own many scattered call sites (an earlier attempt patched two of those individually and still missed others - CarEntity's own turnRate gate, isPivotTurnRestricted(), applyPivotTurnThrottleRestriction() - since nothing enumerates all of them in one place) - every consumer, present or future, now automatically gets correct behavior. This vehicle's own actual fuel GAUGE/consumption (getFuel()/tudursvehiclemod$updateFuelConsumption()) still work completely normally throughout - only the various movement-blocking CONSEQUENCES of being empty are bypassed while a route is actively driving. A fuelless vehicle (tudursvehiclemod$isFuelless()) is never out of fuel either, for the same reason - fuel simply isn't part of how it operates. */
	public boolean tudursvehiclemod$isOutOfFuel() {
		return !this.tudursvehiclemod$isFuelless()
				&& this.getFuel() <= 0f && !this.tudursvehiclemod$isFollowingGroundRoute();
	}

	/** Adds amount (clamped to [0, getMaxFuel()]) to this vehicle's own internal fuel. */
	public void tudursvehiclemod$addFuel(float amount) {
		if (amount <= 0f) {
			return;
		}
		float newFuel = MathHelper.clamp(this.getFuel() + amount, 0f, this.getMaxFuel());
		this.dataTracker.set(FUEL, newFuel);
	}

	/** This vehicle's own persistent cargo inventory (see VehicleExtras' own inventorySize doc), resized to match the CURRENT definition's own inventorySize() if it.. */
	public DefaultedList<ItemStack> tudursvehiclemod$getInventory() {
		int size = 1 + this.getDefinition().inventorySize();
		if (this.vehicleInventory.size() != size) {
			DefaultedList<ItemStack> resized = DefaultedList.ofSize(size, ItemStack.EMPTY);
			for (int i = 0; i < Math.min(size, this.vehicleInventory.size()); i++) {
				resized.set(i, this.vehicleInventory.get(i));
			}
			this.vehicleInventory = resized;
		}
		return this.vehicleInventory;
	}

	/** Always 0 - see tudursvehiclemod$getInventory()'s own doc. */
	public int tudursvehiclemod$getFuelCanSlotIndex() {
		return 0;
	}

	// --- net.minecraft.inventory.Inventory.

	@Override
	public int size() {
		return this.tudursvehiclemod$getInventory().size();
	}

	@Override
	public boolean isEmpty() {
		return this.tudursvehiclemod$getInventory().stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		return this.tudursvehiclemod$getInventory().get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		return net.minecraft.inventory.Inventories.splitStack(this.tudursvehiclemod$getInventory(), slot, amount);
	}

	@Override
	public ItemStack removeStack(int slot) {
		return net.minecraft.inventory.Inventories.removeStack(this.tudursvehiclemod$getInventory(), slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		this.tudursvehiclemod$getInventory().set(slot, stack);
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		if (slot == this.tudursvehiclemod$getFuelCanSlotIndex()) {
			return stack.getItem() instanceof com.example.tudursvehiclemod.item.FuelCanItem;
		}
		return true;
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		// The vehicle menu can only be opened while mounted at all (see the C2S open-request handling), so by the time this is even asked, riding is..
		return player.getVehicle() == this;
	}

	@Override
	public void clear() {
		this.tudursvehiclemod$getInventory().clear();
	}

	@Override
	public void markDirty() {
		// No persistence side-effect needed here (unlike a block entity, which uses this to know it needs re-saving).
	}

	@Override
	public net.minecraft.text.Text getDisplayName() {
		return net.minecraft.text.Text.translatable("gui.tudursvehiclemod.vehicle_menu");
	}

	@Override
	public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new com.example.tudursvehiclemod.screen.VehicleMenuScreenHandler(syncId, playerInventory, this);
	}

	/** A vehicle with a very large inventorySize() (e.g. 180) overflowed the cargo screen off the bottom of the display, since every cargo slot was being shown at once with no limit at all: which page of cargo slots (VehicleMenuScreenHandler's own PAGE_SIZE=45 slots per page, a 5x9 grid matching the creative inventory's own layout) is currently shown. Deliberately per-VEHICLE rather than per-player (the simplest option, and in practice only ever one person opens a given vehicle's own cargo at a time) - not persisted, resets to page 0 on reload, purely a transient UI concern. */
	private int cargoPage;

	public int tudursvehiclemod$getCargoPage() {
		return this.cargoPage;
	}

	public void tudursvehiclemod$setCargoPage(int page) {
		this.cargoPage = Math.max(0, page);
	}

	/** Drains fuel every tick, scaled by throttle. */
	private void tudursvehiclemod$updateFuelConsumption() {
		if (this.tudursvehiclemod$isFuelless() || this.getFuel() <= 0f) {
			return;
		}
		float throttleFraction = Math.abs(this.getThrottle());
		if (throttleFraction <= 0f) {
			return;
		}
		float consumptionFraction = tudursvehiclemod$fuelConsumptionFraction(throttleFraction);
		float maxConsumptionPerTick = this.getDefinition().fuelConsumptionPerSecond() / 20f;
		float consumption = consumptionFraction * maxConsumptionPerTick;
		if (consumption <= 0f) {
			return;
		}
		float newFuel = Math.max(0f, this.getFuel() - consumption);
		this.dataTracker.set(FUEL, newFuel);
	}

	/** Maps a 0.0-1.0 throttle fraction to a 0.0-1.0 fraction of this vehicle's own full-throttle fuel consumption rate. */
	private static float tudursvehiclemod$fuelConsumptionFraction(float throttleFraction) {
		float t = MathHelper.clamp(throttleFraction, 0f, 1f);
		if (t <= THROTTLE_RAMP_THRESHOLD) {
			return (t / THROTTLE_RAMP_THRESHOLD) * THROTTLE_RAMP_THRESHOLD_CONSUMPTION_FRACTION;
		}
		float steepPortion = (t - THROTTLE_RAMP_THRESHOLD) / (1f - THROTTLE_RAMP_THRESHOLD);
		return THROTTLE_RAMP_THRESHOLD_CONSUMPTION_FRACTION
				+ steepPortion * (1f - THROTTLE_RAMP_THRESHOLD_CONSUMPTION_FRACTION);
	}

	/** Drains a FuelCanItem sitting in this vehicle's own fuel slot (see tudursvehiclemod$getFuelCanSlotIndex()) into its internal fuel pool, a bit at a time every tick. */
	private void tudursvehiclemod$updateFuelCanSlot() {
		if (this.getEntityWorld().isClient()) {
			return;
		}
		if (this.getFuel() >= this.getMaxFuel()) {
			return;
		}
		DefaultedList<ItemStack> inventory = this.tudursvehiclemod$getInventory();
		ItemStack canStack = inventory.get(this.tudursvehiclemod$getFuelCanSlotIndex());
		if (canStack.isEmpty() || !(canStack.getItem() instanceof com.example.tudursvehiclemod.item.FuelCanItem)) {
			return;
		}
		int canFuel = com.example.tudursvehiclemod.item.FuelCanItem.getFuel(canStack);
		if (canFuel <= 0) {
			return;
		}
		int transfer = Math.min(canFuel, VEHICLE_FUEL_TRANSFER_PER_TICK);
		transfer = (int) Math.min(transfer, this.getMaxFuel() - this.getFuel());
		if (transfer <= 0) {
			return;
		}
		com.example.tudursvehiclemod.item.FuelCanItem.setFuel(canStack, canFuel - transfer);
		this.tudursvehiclemod$addFuel(transfer);
	}

	public VehicleDefinition getDefinition() {
		if (this.definition == null) {
			Identifier id = Identifier.tryParse(this.dataTracker.get(VEHICLE_ID));
			this.definition = VehicleRegistry.get(id).orElseGet(() -> fallbackDefinition(id));
		}
		return this.definition;
	}

	/** Gates boarding as a rider only, not the stick/station remote-control system itself (works for any vehicle regardless of this value); also gates Destruct. */
	public boolean tudursvehiclemod$isUav() {
		return this.getDefinition().isUav();
	}

	/** Same gating as tudursvehiclemod$isUav() (see that method's own doc), but for the Drone Center system instead of Station. */
	public boolean tudursvehiclemod$isTargetDrone() {
		return this.getDefinition().isTargetDrone();
	}

	/** Which player (if any) is remote-controlling this vehicle - never an actual passenger (position/visibility/damage stay at the station -). */
	private java.util.UUID remoteControllerId;
	/** Which StationBlockEntity (by BlockPos) is remote-controlling this vehicle, so tryExitRemoteControl() can tell that station control ended too. */
	private net.minecraft.util.math.BlockPos remoteControlStationPos;
	/** The remote controller's own isInvulnerable() state from before tryEnterRemoteControl() forced it true - restored exactly rather than always flipping to false. */
	private boolean remoteControllerWasInvulnerable;

	/** Which DroneCenterBlockEntity (by BlockPos), if any, this vehicle is currently linked to for autonomous target-drone flight - see that class's own doc, and tudursvehiclemod$isDroneActive()'s own doc for what this actually gates. Null = not linked (ordinary vehicle, or a linked-but-since-unlinked one). */
	private net.minecraft.util.math.BlockPos droneCenterPos;
	/** The speed this vehicle's own autopilot cruises at, as a fraction of maxSpeed() ("50% throttle" by default) - a mutable field (not a hardcoded constant) specifically so a later Drone Center configuration screen can tune it per vehicle. */
	private float droneSpeedFraction = 0.5f;
	/** Fixed height (blocks) above the linked Drone Center's own Y this vehicle's own autopilot climbs to and orbits at - same "mutable for a future config screen" reasoning as droneSpeedFraction's own doc. */
	private double droneOrbitAltitude = 15.0;
	/** The turn radius is adjustable as a PERCENTAGE RATIO relative to the naturally-computed default (see AircraftEntity's own tudursvehiclemod$updateDroneAutopilot() doc for that calculation) rather than an absolute value - 1.0 = the natural default, 0.5 = half as tight, 2.0 = twice as wide. */
	private float droneRadiusMultiplier = 1.0f;

	/** DroneCenterPos itself is a plain (non-networked) field (only the actual autopilot math running server-side needs it), so any check based on it directly - like tudursvehiclemod$isDroneActive() - always evaluated as "not drone-active" on any OBSERVING client, since that client's own copy of droneCenterPos was never populated at all. Exactly the same category of bug GEAR_DEPLOYED had before its own fix (see that field's own doc). This flag is what tudursvehiclemod$isDroneActive() actually reads now, kept in sync with droneCenterPos's own null-ness whenever tudursvehiclemod$setDroneLink() is called. */
	private static final TrackedData<Boolean> DRONE_ACTIVE =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** A ground-route-following vehicle previously moved smoothly only for whichever client happened to also be riding it (as a passenger, without even controlling it) - see tudursvehiclemod$isFollowingGroundRoute()'s own doc for the full mechanism this fixes: exactly the same category of bug DRONE_ACTIVE itself already needed fixing for (see that field's own doc) - the underlying followingGroundRoute field is plain/server-only, so any client-side check based on it directly always read false on every client except (by IMPLICIT coincidence, not by its own working correctly) whichever one was locally simulating the true state some other way. This is what tudursvehiclemod$isFollowingGroundRoute() actually reads now. */
	private static final TrackedData<Boolean> GROUND_ROUTE_ACTIVE =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Per tudursvehiclemod$isFollowingGroundRoute()'s own doc: the ground autopilot's own computed throttle/steering inputs, synced so every client (not just a controlling player, who never actually exists for an autopilot-driven vehicle at all) can read the SAME values this vehicle is actually being driven by. Written alongside (not instead of) the plain syncedThrottleInput/syncedSidewaysInput fields - see tudursvehiclemod$getSyncedThrottleInput()/-SidewaysInput()'s own doc for how the two are actually reconciled. */
	private static final TrackedData<Float> GROUND_AUTOPILOT_THROTTLE_INPUT =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> GROUND_AUTOPILOT_STEER_INPUT =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);


	/** Called by DroneCenterBlockEntity (a different package) when its own bound vehicle should start/stop autonomous flight - centerPos null unlinks. Defaults speedFraction/orbitAltitude/radiusMultiplier to their own usual defaults (see those fields' own doc) - use the 4-arg overload once a config screen actually needs to supply its own values. */
	public void tudursvehiclemod$setDroneLink(net.minecraft.util.math.BlockPos centerPos) {
		this.tudursvehiclemod$setDroneLink(centerPos, this.droneSpeedFraction, this.droneOrbitAltitude, this.droneRadiusMultiplier);
	}

	/** Same as the 1-arg overload's own doc, but also configures the autopilot's own speed fraction / orbit altitude / radius multiplier at the same time - centerPos null still unlinks (the other arguments are then ignored). */
	public void tudursvehiclemod$setDroneLink(net.minecraft.util.math.BlockPos centerPos, float speedFraction, double orbitAltitude, float radiusMultiplier) {
		this.droneCenterPos = centerPos;
		this.dataTracker.set(DRONE_ACTIVE, centerPos != null);
		if (centerPos != null) {
			this.droneSpeedFraction = speedFraction;
			this.droneOrbitAltitude = orbitAltitude;
			this.droneRadiusMultiplier = radiusMultiplier;
		}
	}

	/** This vehicle's own currently-linked Drone Center position, or null if not linked - see droneCenterPos's own doc. */
	public net.minecraft.util.math.BlockPos tudursvehiclemod$getDroneCenterPos() {
		return this.droneCenterPos;
	}

	/** This vehicle's own currently-configured autopilot cruise speed, as a fraction of maxSpeed() - see droneSpeedFraction's own doc. */
	public float tudursvehiclemod$getDroneSpeedFraction() {
		return this.droneSpeedFraction;
	}

	/** This vehicle's own currently-configured autopilot orbit altitude (blocks above the linked Drone Center) - see droneOrbitAltitude's own doc. */
	public double tudursvehiclemod$getDroneOrbitAltitude() {
		return this.droneOrbitAltitude;
	}

	/** This vehicle's own currently-configured autopilot turn radius multiplier - see droneRadiusMultiplier's own doc. */
	public float tudursvehiclemod$getDroneRadiusMultiplier() {
		return this.droneRadiusMultiplier;
	}

	/** True while this vehicle should be actively flying its own autonomous target-drone routine - gates the autopilot logic in each vehicle type's own updateVehicleMovement(), the same way "am I currently piloted" already gates the manned-vs-unmanned split there. Does NOT require isTargetDrone() itself (see that method's own doc - any vehicle can be Drone Center-linked, same as remote control via Station doesn't require isUav()). */
	public boolean tudursvehiclemod$isDroneActive() {
		return this.dataTracker.get(DRONE_ACTIVE);
	}

	/** Autonomous-route system aimed at surface-moving vehicles (Car/Ship/Submarine) rather than aircraft: which GroundWaypoint in the linked Drone Center's own ground route this vehicle is currently heading toward. Plain server-side field, same convention as AircraftEntity's own droneWaypointIndex. */
	private int groundWaypointIndex;
	/** Per GroundWaypoint's own waitTicks doc: ticks still remaining of the current waypoint's own stationary dwell, or 0 when not currently waiting. While above 0 this vehicle holds zero throttle/steering input (so it sits still, keeping whatever heading it arrived on - the heading is preserved rather than reset), counting down until it resumes toward the next waypoint. */
	private int groundWaypointWaitTicksRemaining;
	/** True while completing an in-place turn to face the current target waypoint, before driving toward it - see tudursvehiclemod$updateGroundWaypointAutopilot()'s own doc for the state machine. Only cleared by actually arriving at the waypoint (no mid-leg realign). Persisted - see that section for why a plain field caused post-reload drift. */
	private boolean groundAutopilotTurning = true;
	/** The committed steering direction (+1/-1) for the CURRENT turn, decided once and reused for its whole duration rather than recomputed each tick - prevents oscillation near a 180°-behind target. 0f = not yet committed. */
	private float groundAutopilotTurnDirection = 0f;

	/** How close (blocks, HORIZONTAL only - see GroundWaypoint's own doc for why Y is deliberately ignored entirely) counts as having arrived at a ground-route waypoint. Generous enough that a vehicle with a wide turning circle isn't left orbiting a point it can never quite touch. */
	private static final double GROUND_WAYPOINT_ARRIVAL_RADIUS = 3.0;
	/** Yaw error (degrees) at or below which an in-place turn (groundAutopilotTurning) counts as complete, switching this vehicle over to driving straight at the target. Deliberately tight - the whole point of this state is to reach a genuinely well-aligned heading BEFORE committing to a straight run, so a wide tolerance here would defeat that. */
	private static final float GROUND_AUTOPILOT_TURN_COMPLETE_DEGREES = 5.0f;

	/** Steers this vehicle along the linked Drone Center's own ground route (see GroundWaypoint's own doc) using a simple, robust TURN-THEN-DRIVE state machine appropriate for a tight-turning-radius vehicle, instead of continuously blending steering and throttle together from a proportional yaw-error controller every tick (the previous approach - reliably produced a persistent left-right weave, from a proportional controller riding on top of this vehicle's own already-eased turnRate response, AND kept top speed perpetually capped by treating ANY nonzero residual heading error, even ordinary noise that never fully settles to exactly zero, as a reason to hold throttle back).
	 *
	 * Each leg between waypoints is treated as a straight line (which suits an aircraft's own wide turning circle but not a car/ship's own tight one): while groundAutopilotTurning is true, this vehicle applies ZERO throttle and FULL-LOCK steering (bang-bang, not proportional - there is no gain here left to tune wrong) until its own heading toward the current target is within GROUND_AUTOPILOT_TURN_COMPLETE_DEGREES, at which point it switches to driving mode. While driving, steering is held at EXACTLY ZERO (a real straight run, not a continuously-corrected one) and throttle is this waypoint's own FULL configured speedFraction (never scaled down by heading error at all, fixing the reported "always far slower than actual top speed" symptom). This vehicle NEVER re-enters turning mid-leg no matter how much heading drift accumulates - only arriving at the current waypoint (or the route being reset) turns it back to true - see groundAutopilotTurning's own doc for the full reasoning and for why that field is now persisted.
	 *
	 * Arrival is judged on HORIZONTAL distance only (GroundWaypoint has no Y at all - see that record's own doc). On arrival, groundAutopilotTurning is set true again (so this vehicle always re-orients toward its own NEXT target before departing), and a waypoint with waitTicks > 0 additionally holds this vehicle fully stationary (zero inputs, heading preserved exactly as arrived) for that many ticks first. The route loops back to the first waypoint after the last.
	 *
	 * Still drives this vehicle's own STEERING/THROTTLE INPUTS (setSyncedSidewaysInput()/setSyncedThrottleInput()) rather than touching velocity/yaw directly - that part of the original design was never the problem (see this method's own git history for the fuller reasoning, still valid: it's what keeps CarEntity's own wheel-spin/crawler-track/steering-wheel animation, pivot-turn gating, ShipEntity's/SubmarineEntity's own turn-induced roll, engine sound, and wake trails all working with no per-vehicle-type special-casing at all). Only the ALGORITHM deciding what those inputs should be, each tick, has been replaced. */
	/** setSyncedThrottleInput()/updateThrottle() only reads the SIGN of the input (>0 ramps toward max, <0 ramps toward min, 0 holds), never its magnitude - see updateThrottle()'s own doc. This bang-bang-with-tolerance helper lets that existing sign-only ramp converge onto and then hold at an exact target fraction anyway: +1 while genuinely below it, -1 while genuinely above it, 0 (hold) once within a small tolerance band - the same simple deadband-around-a-setpoint pattern as a domestic thermostat. Shared between the ground autopilot's own driving state and its own pivot-turn-throttle-required turning state (see tudursvehiclemod$updateGroundWaypointAutopilot()'s own doc for both). */
	private float tudursvehiclemod$bangBangThrottleInput(float targetThrottleFraction) {
		float targetThrottle = MathHelper.clamp(targetThrottleFraction, 0f, 1f);
		float currentThrottle = this.getThrottle();
		float throttleTolerance = 0.02f;
		if (currentThrottle < targetThrottle - throttleTolerance) {
			return 1f;
		} else if (currentThrottle > targetThrottle + throttleTolerance) {
			return -1f;
		}
		return 0f;
	}

	protected void tudursvehiclemod$updateGroundWaypointAutopilot(VehicleDefinition def, net.minecraft.util.math.BlockPos center,
			java.util.List<com.example.tudursvehiclemod.block.GroundWaypoint> waypoints) {
		if (waypoints.isEmpty()) {
			// A zero INPUT only ever HOLDS this vehicle's own currently-ramped throttle value (this project's own cruise-control convention - see updateThrottle()'s own doc), it does not decelerate it. An empty route should leave this vehicle genuinely stationary, not coasting forever at whatever speed it last had - forced directly here rather than relying on input alone.
			this.setThrottleDirect(0f);
			this.setSyncedThrottleInput(0f);
			this.setSyncedSidewaysInput(0f);
			return;
		}
		// Per GroundWaypoint's own waitTicks doc: sitting still at a waypoint - see this method's own doc for why setThrottleDirect(0f) (an actual, immediate force-stop) is used here rather than relying on a merely-zero INPUT, which this project's own cruise-control throttle convention would otherwise just interpret as "hold whatever speed I already had".
		if (this.groundWaypointWaitTicksRemaining > 0) {
			this.groundWaypointWaitTicksRemaining--;
			this.setThrottleDirect(0f);
			this.setSyncedThrottleInput(0f);
			this.setSyncedSidewaysInput(0f);
			return;
		}
		this.groundWaypointIndex = MathHelper.clamp(this.groundWaypointIndex, 0, waypoints.size() - 1);
		com.example.tudursvehiclemod.block.GroundWaypoint waypoint = waypoints.get(this.groundWaypointIndex);
		double targetX = center.getX() + 0.5 + waypoint.relX();
		double targetZ = center.getZ() + 0.5 + waypoint.relZ();
		double dx = targetX - this.getX();
		double dz = targetZ - this.getZ();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		if (horizontalDistance <= GROUND_WAYPOINT_ARRIVAL_RADIUS) {
			// The route loops back to the first waypoint after the last, rather than despawning/stopping. groundAutopilotTurning resets true here so this vehicle always re-orients toward its own NEXT target before departing - see that field's own doc. setThrottleDirect(0f) here for the same "genuinely stop, don't just hold" reasoning as the waitTicks branch above.
			this.groundWaypointIndex = (this.groundWaypointIndex + 1) % waypoints.size();
			this.groundWaypointWaitTicksRemaining = Math.max(0, waypoint.waitTicks());
			this.groundAutopilotTurning = true;
			this.groundAutopilotTurnDirection = 0f;
			this.setThrottleDirect(0f);
			this.setSyncedThrottleInput(0f);
			this.setSyncedSidewaysInput(0f);
			return;
		}

		float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float yawError = MathHelper.wrapDegrees(desiredYaw - this.getYaw());
		float absYawError = Math.abs(yawError);

		if (this.groundAutopilotTurning && absYawError <= GROUND_AUTOPILOT_TURN_COMPLETE_DEGREES) {
			this.groundAutopilotTurning = false;
			this.groundAutopilotTurnDirection = 0f;
		}
		// Per groundAutopilotTurnDirection's own doc: commits a fresh direction exactly once per turn, the first tick turning is found active with no commitment yet outstanding (sentinel 0f) - covers all three of that state's own trigger points (arrival, realign just above, and a freshly-(re)activated route) from one place, rather than duplicating this same logic at each of them. specific report (consistently turning right when left was needed, and vice versa, taking the long way around every time) - NEGATED here relative to yawError's own sign: whatever the earlier reasoning for the un-negated version was, it was simply backward relative to how CarEntity's own steering actually responds to a positive/negative sidewaysInput.
		if (this.groundAutopilotTurning && this.groundAutopilotTurnDirection == 0f) {
			float committedSign = -Math.signum(yawError);
			this.groundAutopilotTurnDirection = committedSign != 0f ? committedSign : 1f;
		}

		float steerInput;
		float throttleInput;
		if (this.groundAutopilotTurning) {
			// Replicates a real player's own A/D input during the turn - steering alone, with throttle management deliberately disabled entirely (not force-stopped, not targeting the waypoint's own speedFraction, just left at 0/uncontrolled) so whatever throttle fluctuation this vehicle's own existing physics produces from that steering input takes priority instead (including CarEntity's own pivotTurnRestricted-triggered automatic throttle boost for a vehicle that needs speed to turn at all - that mechanism reacts to the steering input directly, independent of whatever this method itself feeds as throttle input, so no separate branching on pivot_turn_throttle is needed here at all). Full-lock steering, no proportional gain to tune wrong (see this method's own doc). Uses the COMMITTED groundAutopilotTurnDirection (see that field's own doc), locked for the whole turn rather than recomputed tick to tick.
			steerInput = this.groundAutopilotTurnDirection;
			throttleInput = 0f;
		} else {
			// Driving dead straight - see this method's own doc for why steering is held at exactly zero here (not continuously corrected).
			steerInput = 0f;
			throttleInput = tudursvehiclemod$bangBangThrottleInput(waypoint.speedFraction());
		}

		this.setSyncedThrottleInput(throttleInput);
		this.setSyncedSidewaysInput(steerInput);
	}

	/** Resets this vehicle's own ground-route progress back to the start - called whenever a ground route is (re-)activated, so a newly-started route always begins from its own first waypoint rather than wherever a previous run happened to leave off. Also resets groundAutopilotTurning to true (see that field's own doc) - a freshly-(re)activated route always turns to face its own first waypoint before departing, rather than potentially assuming it's already correctly aligned. */
	public void tudursvehiclemod$resetGroundWaypointProgress() {
		this.groundWaypointIndex = 0;
		this.groundWaypointWaitTicksRemaining = 0;
		this.groundAutopilotTurning = true;
		this.groundAutopilotTurnDirection = 0f;
	}

	/** See tudursvehiclemod$isFollowingGroundRoute()'s own doc - this is now just a thin wrapper over the synced GROUND_ROUTE_ACTIVE flag, kept as its own method for readability at call sites. */
	public boolean tudursvehiclemod$isFollowingGroundRoute() {
		return this.dataTracker.get(GROUND_ROUTE_ACTIVE);
	}

	/** The single hook each surface vehicle type (Car/Ship/Submarine) calls at the very top of its own updateVehicleMovement(), resolves the linked Drone Center's own ground route and, if there is one, sets this tick's own synced steering/throttle inputs from it. Returns true if it actually did so - the caller then simply carries on into its own ordinary physics with those inputs already in place, exactly as if a player had pressed those keys. Returns false in every other case (not drone-active, no linked center, center not loaded, empty ground route), leaving inputs completely untouched so nothing changes for a vehicle that isn't running a ground route at all.
	 *
	 * SERVER-ONLY (guarded by the isClient() check below). Also mirrors this tick's computed values into the synced GROUND_AUTOPILOT_THROTTLE_INPUT/-STEER_INPUT/GROUND_ROUTE_ACTIVE fields (see tudursvehiclemod$getSyncedThrottleInput()/-SidewaysInput()'s own doc for how a client prefers these over the plain, otherwise-stale copy) - fixes a client-side jitter for an autopilot-driven vehicle, since the plain syncedThrottleInput/syncedSidewaysInput fields were never actually networked. */
	protected boolean tudursvehiclemod$applyGroundWaypointAutopilotInputs(VehicleDefinition def) {
		if (this.getEntityWorld().isClient() || !this.tudursvehiclemod$isDroneActive()) {
			this.dataTracker.set(GROUND_ROUTE_ACTIVE, false);
			return false;
		}
		net.minecraft.util.math.BlockPos center = this.tudursvehiclemod$getDroneCenterPos();
		if (center == null) {
			this.dataTracker.set(GROUND_ROUTE_ACTIVE, false);
			return false;
		}
		if (!(this.getEntityWorld().getBlockEntity(center) instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity droneCenter)) {
			this.dataTracker.set(GROUND_ROUTE_ACTIVE, false);
			return false;
		}
		java.util.List<com.example.tudursvehiclemod.block.GroundWaypoint> route = droneCenter.tudursvehiclemod$getGroundWaypoints();
		if (route.isEmpty()) {
			this.dataTracker.set(GROUND_ROUTE_ACTIVE, false);
			return false;
		}
		this.dataTracker.set(GROUND_ROUTE_ACTIVE, true);
		this.tudursvehiclemod$updateGroundWaypointAutopilot(def, center, route);
		this.dataTracker.set(GROUND_AUTOPILOT_THROTTLE_INPUT, this.syncedThrottleInput);
		this.dataTracker.set(GROUND_AUTOPILOT_STEER_INPUT, this.syncedSidewaysInput);
		return true;
	}

	/** The client-side engine sound manager reads tudursvehiclemod$getDroneSpeedFraction() as its own "throttle" input whenever isDroneActive() is true, but that field only applies to the GENERIC Drone Center orbital-patrol autopilot - it's never touched at all by AircraftEntity's own separate CAS/Carrier waypoint-following autopilot, which drives its own speed from each individual DroneWaypoint's own speedFraction instead. False by default (only AircraftEntity overrides this to true while actually following such a route) - lets the sound manager tell the two drone-style autopilot modes apart and read the correct speed source for each, rather than reading a stale/unrelated value the whole time a CAS/Carrier route is being flown. */
	public boolean tudursvehiclemod$isUsingRampedAutopilotThrottle() {
		return false;
	}

	/** See tudursvehiclemod$isUsingRampedAutopilotThrottle()'s own doc - the CAS/Carrier waypoint route's own current speed, as a fraction of maxSpeed() (same meaning as tudursvehiclemod$getDroneSpeedFraction()'s own doc). Meaningless (always 0) unless tudursvehiclemod$isUsingRampedAutopilotThrottle() is true. */
	public float tudursvehiclemod$getCasWaypointSpeedFraction() {
		return 0f;
	}

	/** playerId -> entity ID of whichever vehicle they're remote-controlling (mirrors VehicleProjectileEntity's ACTIVE_TV_CONTROL -). */
	private static final java.util.Map<java.util.UUID, Integer> ACTIVE_REMOTE_CONTROL = new java.util.concurrent.ConcurrentHashMap<>();

	/** Set once by VehicleModClient's own client-side initializer to actually send network.RequestSeatResyncPayload - kept as a plain java.util.function.IntConsumer here (never a client-only Fabric API type) specifically so this common (src/main) class never directly references anything that wouldn't exist on a genuine dedicated server. Left null there, where onSpawnPacket() is never actually invoked client-side anyway. */
	public static java.util.function.IntConsumer tudursvehiclemod$clientRequestSeatResyncCallback;

	/** Resolves the vehicle a player is actually controlling right now - real mount, or remote-controlled via a station. Input-handling code routes through this instead of checking player.getVehicle() directly. */
	public static AbstractVehicleEntity tudursvehiclemod$getEffectiveVehicle(net.minecraft.entity.player.PlayerEntity player) {
		if (player.getVehicle() instanceof AbstractVehicleEntity mounted) {
			return mounted;
		}
		Integer vehicleEntityId = ACTIVE_REMOTE_CONTROL.get(player.getUuid());
		if (vehicleEntityId == null) {
			return null;
		}
		if (player.getEntityWorld().getEntityById(vehicleEntityId) instanceof AbstractVehicleEntity vehicle) {
			return vehicle;
		}
		return null;
	}

	/** This vehicle's own current remote controller, or null if nobody's remote-controlling it right now - see remoteControllerId's own doc. Public so block.StationBlockEntity (a different package) can check it. */
	public java.util.UUID tudursvehiclemod$getRemoteControllerId() {
		return this.remoteControllerId;
	}

	/** True if passenger is this vehicle's own remote controller (a real passenger that should never be rendered) - works client-side too, reading the synced string directly. */
	public boolean tudursvehiclemod$isRemoteControlHiddenPilot(Entity passenger) {
		String uuidString = this.dataTracker.get(REMOTE_CONTROLLER_UUID);
		return !uuidString.isEmpty() && uuidString.equals(passenger.getUuidAsString());
	}

	/** Actually mounts player onto this vehicle (real mounting, not a virtual camera/input redirect -). Fails if already remote-controlled, or player is riding/controlling anything else. */
	public boolean tudursvehiclemod$tryEnterRemoteControl(net.minecraft.server.network.ServerPlayerEntity player, net.minecraft.util.math.BlockPos stationPos) {
		if (this.remoteControllerId != null || player.getVehicle() != null || this.tudursvehiclemod$isDestroyed()) {
			return false;
		}
		if (tudursvehiclemod$getEffectiveVehicle(player) != null) {
			// Already remote-controlling some OTHER vehicle right now.
			return false;
		}
		this.remoteControllerId = player.getUuid();
		this.remoteControlStationPos = stationPos;
		ACTIVE_REMOTE_CONTROL.put(player.getUuid(), this.getId());
		this.dataTracker.set(REMOTE_CONTROLLER_UUID, player.getUuid().toString());
		this.remoteControllerWasInvulnerable = player.isInvulnerable();
		player.setInvulnerable(true);
		if (this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
			player.teleport(serverWorld, this.getX(), this.getY(), this.getZ(),
					java.util.Set.of(), this.getYaw(), this.getPitch(), false);
		}
		// This vehicle's own remote controller must
		// ALWAYS end up in the pilot seat (seat 0) specifically -
		// tudursvehiclemod$assignFirstAvailableSeat() (this vehicle's
		// own general-purpose "find whichever seat is actually still
		// empty" logic, meant for an ordinary passenger boarding any
		// available seat) isn't guaranteed to pick seat 0 specifically
		// at all on a vehicle with more than one seat defined - only
		// the PILOT seat should ever actually fly/steer this vehicle,
		// so mounting/assigning seat 0 directly here, unconditionally,
		// is required rather than reusing that other search at all.
		player.startRiding(this);
		this.tudursvehiclemod$setAssignedSeat(player, 0);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
				new com.example.tudursvehiclemod.network.RemoteControlStartPayload(this.getId()));
		return true;
	}

	/** Releases remote control from the outside (station's "end control", or this vehicle destroyed/removed) - safe no-op if nobody's controlling. Dismounts via stopRiding(); actual cleanup happens in removePassenger() as a side effect. */
	public void tudursvehiclemod$tryExitRemoteControl() {
		if (this.remoteControllerId == null) {
			return;
		}
		if (this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld
				&& serverWorld.getServer().getPlayerManager().getPlayer(this.remoteControllerId) instanceof net.minecraft.server.network.ServerPlayerEntity player
				&& player.getVehicle() == this) {
			player.stopRiding();
		} else {
			// The controller isn't actually mounted here anymore for some
			// reason (already dismounted through some other path, or
			// disconnected) - clean up this vehicle's own state directly
			// instead, since removePassenger() won't ever fire to do it.
			ACTIVE_REMOTE_CONTROL.remove(this.remoteControllerId, this.getId());
			this.dataTracker.set(REMOTE_CONTROLLER_UUID, "");
			this.remoteControllerId = null;
			this.remoteControlStationPos = null;
		}
	}

	/** This vehicle's own remote-control station position, mainly so a destroyed/broken station can find and release it (- the camera itself doesn't anchor here). Null if not remote-controlled. Public for client.mixin.CameraMixin. */
	public net.minecraft.util.math.BlockPos tudursvehiclemod$getRemoteControlStationPos() {
		return this.remoteControlStationPos;
	}

	private VehicleDefinition fallbackDefinition(Identifier id) {
		VehicleMod_LoggerHolder.LOGGER.warn(
				"No vehicle definition '{}' is loaded (missing data/{}/vehicles/{}.json?) - using a 1x1 placeholder",
				id, id == null ? "?" : id.getNamespace(), id == null ? "?" : id.getPath());
		return new VehicleDefinition(
				Identifier.of("minecraft", "missing"),
				Identifier.of("minecraft", "missing"), Identifier.of("minecraft", "missing"),
				1.0f, 1.0f, 1.0f, 0.4f, 0.05f, 3.0f, 1.0f, -0.04f, 0.0f, 0.0f, java.util.Optional.empty(), 1.0f, 0.0f, 40.0f,
				java.util.Optional.empty(), com.example.tudursvehiclemod.asset.WeightType.UNKNOWN,
				java.util.Optional.empty(),
				java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
				java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.List.of(),
				100.0f, 1.0f, 0.0f, Float.MAX_VALUE, 1.0f, 600.0f, 0.5f, 0, java.util.List.of(), 0.0f, true, 0.3f,
				false, 1.0f, 1.0f, false, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), false, false, 0.1f, java.util.List.of(),
				// wake_trail_spread_distance/wake_trail_duration_ticks - same defaults as VehicleDefinition.CODEC's own (see GroundVehicleParts's own doc) - this placeholder has no water/wake relevance at all, but still needs SOME value for every one of this record's own fields.
				java.util.Optional.empty(), 300,
				// flare_type - a 1x1 placeholder has no flare capability at all.
				0,
				// search_light_parts/nav_light_parts - see SearchLightPart's/NavLightPart's own doc. A 1x1 placeholder has no lights of its own.
				java.util.List.of(), java.util.List.of(),
				// fuel/ammo/health supply ranges - 0 disables each. A 1x1 placeholder supplies nothing.
				0f, 0f, 0f,
				// regeneration/default_freelook - a 1x1 placeholder has neither.
				false, false,
				// enable_ejection_seat/mob_drop_option - a 1x1 placeholder has neither.
				false, java.util.Optional.empty());
	}

	/** force_bounding_box: false/absent = mod-standard box; true = hand-specified size. Never derived from model geometry, and separate from the custom hit-detection system. */
	@Override
	public EntityDimensions getDimensions(net.minecraft.entity.EntityPose pose) {
		VehicleDefinition def = getDefinition();
		float rawWidth;
		float rawHeight;
		if (def.forceBoundingBox()) {
			rawWidth = def.width();
			rawHeight = def.height();
		} else {
			// Same fixed, small values as CarEntity's own getDimensions() override.
			rawWidth = 1.6f;
			rawHeight = 1.0f;
		}
		float width = Math.max(0.1f, rawWidth * def.scale());
		float height = Math.max(0.1f, rawHeight * def.scale());
		return EntityDimensions.changing(width, height);
	}

	/** Re-applies the entity's hitbox from the (possibly just-changed) definition. */
	protected void refreshDimensionsFromDefinition() {
		this.calculateDimensions();
	}

	/** Tracks whether there was a controlling passenger last tick, to detect the exact tick someone (re)takes the controls. */
	private boolean hadController;

	/** See removePassenger()'s own doc for why this correction is deferred to tick() rather than applied immediately. Null when nothing is pending. */
	private Entity pendingDismountPassenger;
	private Vec3d pendingDismountPosition;

	/** Guarantees tryExitRemoteControl() always runs whenever this vehicle goes away at all (any removal path, not just combat destruction) - a remote controller should never be left stuck controlling a vehicle that no longer exists.
	 *
	 * This vehicle's own wake history is cleared HERE rather than in tudursvehiclemod$onRemoved(). This method fires on EVERY removal path - crucially including RemovalReason.UNLOADED_TO_CHUNK and UNLOADED_WITH_PLAYER (a vehicle simply going out of load range), which is exactly the "機体非読み込み時" case the request asks about, and which onRemoved() does NOT reliably cover. That distinction matters especially here because wake history is CLIENT-side only (see wakeBowHistory's own doc), while onRemoved()'s own other cleanup work is all explicitly ServerWorld-gated.
	 *
	 * Repeated spawn/sail/remove cycles without rejoining the world grew memory steadily, with GC reclaiming little of it: CARRIER_ACTIVE_MOTHERSHIPS is de-registered here too, for exactly the same reason, and this is the far more serious of the two. That static Set holds STRONG REFERENCES TO ENTITY INSTANCES THEMSELVES (not UUIDs), and any runway-equipped vehicle re-adds itself to it EVERY SINGLE TICK - while removal only ever happened in onRemoved(). Any vehicle that left by a path onRemoved() doesn't cover therefore stayed in that Set permanently, keeping the whole entity - and transitively everything reachable from it, its wake deques included - alive for the entire remaining JVM session, completely immune to GC. That is precisely the reported "GC runs but memory doesn't come back" signature, and precisely why it accumulates per spawn/remove cycle rather than resetting until the world is reloaded. */
	@Override
	public void remove(net.minecraft.entity.Entity.RemovalReason reason) {
		this.tudursvehiclemod$tryExitRemoteControl();
		CARRIER_ACTIVE_MOTHERSHIPS.remove(this);
		this.wakeBowHistory.clear();
		this.wakeSternHistory.clear();
		this.wakeSideHistory.clear();
		super.remove(reason);
	}

	@Override
	public void tick() {
		super.tick();

		if (!this.getEntityWorld().isClient() && this.age <= 5) {
			// Per a recurrence report (a single sync at age<=1 apparently wasn't sufficient - likely too early, syncing stale/still-empty ammo data before whatever actually establishes the Carrier weapon's own real initial magazine value had a chance to run): repeats this sync over this vehicle's own first several ticks instead of just once, robust regardless of exactly when that real initialization happens to occur relative to first tick.
			this.tudursvehiclemod$syncWeaponAmmo();
		}
		if (!this.getEntityWorld().isClient()) {
			this.tudursvehiclemod$updateCarrierMothershipForcedChunks();
			if (this.getEntityWorld() instanceof ServerWorld carrierLaunchServerWorld) {
				this.tudursvehiclemod$updateCarrierLaunchSequences(carrierLaunchServerWorld);
			}
			this.tudursvehiclemod$updateCarrierLandingToAmmo();
			this.tudursvehiclemod$updateSupplyToNearbyVehicles();
			this.tudursvehiclemod$updatePassengerRegeneration();
			this.tudursvehiclemod$updateMobDropSequence();
			// Keeps SEARCH_LIGHT_LAST_YAW/PITCH current every tick an occupant is actually present, so tudursvehiclemod$getSearchLightWorldAim()'s own no-occupant fallback has a real last-aimed direction to return instead of collapsing to level/forward. Gated on searchLightParts().isEmpty() so a vehicle with no search lights at all pays nothing for this; NOT further gated on isSearchLightOn(), because the light can be switched on again after remounting and should resume from wherever it was actually last aimed, not from whatever it happened to read while off.
			if (!this.getDefinition().searchLightParts().isEmpty()) {
				Entity searchLightOccupant = this.tudursvehiclemod$resolveWeaponTrackingOccupant(0, true);
				if (searchLightOccupant != null) {
					this.dataTracker.set(SEARCH_LIGHT_LAST_YAW, searchLightOccupant.getYaw());
					this.dataTracker.set(SEARCH_LIGHT_LAST_PITCH, searchLightOccupant.getPitch());
				}
			}
			// Per tudursvehiclemod$carryRunwayDeckEntities()'s own doc: no longer called directly here - self-registers into a static set instead, actually invoked from a global END_WORLD_TICK callback (see VehicleMod's own onInitialize()) so it runs AFTER every entity in the world has already completed its own tick this cycle.
			if (!this.getDefinition().runways().isEmpty()) {
				CARRIER_ACTIVE_MOTHERSHIPS.add(this);
			}
		}

		// A destroyed vehicle (see
		// tudursvehiclemod$isDestroyed()'s own doc) despawns on its own
		// after VehicleModServerConfig's own destroyedVehicleDespawnSeconds
		// - see that config field's own doc. Deliberately only counts
		// ticks while ACTUALLY destroyed - an intact vehicle's own age
		// never contributes towards this at all, however long it sits
		// around unused.
		if (!this.getEntityWorld().isClient() && this.tudursvehiclemod$isDestroyed()) {
			var serverConfig = com.example.tudursvehiclemod.VehicleModServerConfig.get();
			if (serverConfig.destroyedVehicleDespawnEnabled) {
				this.ticksSinceDestroyed++;
				if (this.ticksSinceDestroyed >= serverConfig.destroyedVehicleDespawnSeconds * 20) {
					this.discard();
					return;
				}
			}
		}

		// Per removePassenger()'s own doc: applied here, at the START of
		// this vehicle's own next tick, so it runs strictly after any
		// same-tick vanilla dismount-positioning logic (which may run
		// somewhere after removePassenger() itself already returned) has
		// had a full tick to finish, rather than racing against it.
		if (this.pendingDismountPassenger != null && !this.getEntityWorld().isClient()) {
			Entity target = this.pendingDismountPassenger;
			Vec3d pos = this.pendingDismountPosition;
			this.pendingDismountPassenger = null;
			this.pendingDismountPosition = null;
			if (!target.isRemoved() && target.getVehicle() != this) {
				if (target instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
					serverPlayer.networkHandler.requestTeleport(pos.x, pos.y, pos.z, serverPlayer.getYaw(), serverPlayer.getPitch());
				} else {
					target.setPosition(pos.x, pos.y, pos.z);
				}
			}
		}

		if (!this.getEntityWorld().isClient()) {
			this.tudursvehiclemod$repairSeatAssignmentsIfNeeded();
		}

		boolean hasController = getControllingPassenger() != null;
		if (hasController && !this.hadController) {
			onPilotMounted();
		}
		this.hadController = hasController;

		if (!this.getEntityWorld().isClient()) {
			tudursvehiclemod$clearExpiredFiringBits();
			tudursvehiclemod$clearExpiredActualFireBits();
		}

		tudursvehiclemod$updateCustomHitDetection(getDefinition());

		// Even though that code only ever RAN server-side, the resulting zeroed velocity was still the server's own AUTHORITATIVE value, broadcast to every observing client via Minecraft's own normal entity-velocity sync (separate from position sync) - so observing clients still received "velocity=0" while the entity's own position kept changing via periodic position sync, reproducing the same interpolation mismatch/jitter through a different path than the original client-side DataTracker sync did. Removed entirely: RUNWAY_CARRY_WHEEL_SYNC (see its own doc, just below) directly fixes the actual root cause of the wheel-spin/crawler-track bug (independently-tracked lastTickX/lastTickZ/lastYaw, unrelated to velocity), so this mechanism no longer serves any purpose at all.
		if (this.dataTracker.get(RUNWAY_CARRY_WHEEL_SYNC)) {
			this.lastTickX = this.getX();
			this.lastTickZ = this.getZ();
			this.lastYaw = this.getYaw();
		}
		// Reverted back out entirely. tudursvehiclemod$updateWheelSpinPhase()/tudursvehiclemod$updateCrawlerTrackPhase() now suppress their own animation accumulation directly while carried and unpiloted (see their own doc), independent of velocity entirely, so this hook no longer serves the purpose it was reintroduced for either.
		// A destroyed wreck sitting on water sinks instead of running its normal physics. Chosen and advanced BEFORE updateVehicleMovement() so every vehicle type's own buoyancy sees tudursvehiclemod$isSinking() already true this tick and stands down - see that method's own doc.
		if (!this.getEntityWorld().isClient() && this.tudursvehiclemod$isDestroyed()) {
			this.tudursvehiclemod$beginSinkingIfOnWater();
		}
		if (this.tudursvehiclemod$isSinking()) {
			this.tudursvehiclemod$applySinkingMotion();
		}
		this.lastRenderX = this.getX();
		this.lastRenderY = this.getY();
		this.lastRenderZ = this.getZ();
		updateVehicleMovement(getDefinition());
		// This runway's own hatch progress (see updateRunwayHatchProgress()'s own doc) needs to be up to date for THIS tick before tudursvehiclemod$updateCarrierRunwayPlatform() below reads it to place tiles - moved ahead of it (was previously called much later, alongside updateToggleParts()/updateSpinningParts()) specifically so tile placement never reads a one-tick-stale progress value. Runs unconditionally (both sides) regardless of this ordering change - see that method's own doc for why running it here changes nothing else about its own behavior.
		updateRunwayHatchProgress();
		if (!this.getEntityWorld().isClient()) {
			this.tudursvehiclemod$updateCarrierRunwayPlatform();
		}
		if (!this.getEntityWorld().isClient()) {
			tudursvehiclemod$handleWeightTypeMobCollision(getDefinition());
			tudursvehiclemod$handleWeightTypeBlockBreaking(getDefinition());
		}
		updateLandingGear();
		updateToggleParts();
		updateSpinningParts();
		tudursvehiclemod$updateWeaponPartSpin(getDefinition());
		tudursvehiclemod$updateWeaponPartRecoil();
		tudursvehiclemod$updateWeaponPartAimSpeed(getDefinition());
		tudursvehiclemod$updateCrawlerTrackPhase();
		tudursvehiclemod$updateWheelSpinPhase();
		tudursvehiclemod$updateSmoothedSteeringWheelFraction();
		boolean cooldownChanged = false;
		for (int i = 0; i < this.weaponCooldowns.length; i++) {
			if (this.weaponCooldowns[i] > 0) {
				this.weaponCooldowns[i]--;
				cooldownChanged = true;
			}
		}
		if (cooldownChanged && !this.getEntityWorld().isClient()) {
			// The HUD's own "reloading" display
			// should ALSO change while a weapon's own fire-rate delay
			// (MC Heli's own "Delay" directive - this project's own
			// cooldownTicks) is counting down, not just during an actual
			// magazine reload - see tudursvehiclemod$syncWeaponAmmo()'s
			// own doc for where this cooldown value actually gets synced
			// alongside ammo/reload/heat.
			this.tudursvehiclemod$syncWeaponAmmo();
		}
		for (int i = 0; i < this.weaponSoundCooldowns.length; i++) {
			if (this.weaponSoundCooldowns[i] > 0) {
				this.weaponSoundCooldowns[i]--;
			}
		}
		this.updateWeaponReloads();
		this.tudursvehiclemod$updateWeaponHeat();
		this.tudursvehiclemod$updateFuelConsumption();
		this.tudursvehiclemod$updateFuelCanSlot();
		this.tudursvehiclemod$updateSeatMountGrace();
		this.tudursvehiclemod$updateMissileLockOnIndicators();
		this.tudursvehiclemod$updateDamageSmoke();
		this.tudursvehiclemod$updateRecoilShakePhysics();
		// This vehicle now ACTUALLY mounts
		// its own remote controller (see tudursvehiclemod$tryEnterRemoteControl()'s
		// own doc) - vanilla's own passenger-position system already
		// keeps them correctly positioned/synced automatically, exactly
		// like any other passenger, so tudursvehiclemod$updateRemoteControllerFreeze()/
		// tudursvehiclemod$updateRemoteControlSync() (this vehicle's own
		// PREVIOUS, "virtual"-control-era workarounds for a controller
		// that was deliberately NOT actually mounted at all) would now
		// only ever fight against that same normal mounting instead -
		// removed entirely rather than left as harmless dead calls.
		// Block collision (crop trampling etc.) is now handled internally by move()/baseTick() in 1.21.11.
	}

	private static final float LANDING_GEAR_SPEED = 3f;
	/** Maximum pitch/roll (degrees) a vehicle can be at and still use a WeaponType.TORPEDO weapon (Bomb/Depth are droppable at any attitude - this no longer applies to them). */
	private static final float BOMB_MAX_TILT_DEGREES = 15.0f;
	/** How long (ticks) firing stays locked out after a seat's occupant changes. */
	private static final int MOUNT_FIRE_LOCK_TICKS = 40;
	/** Fraction of a heat-based weapon's own maxHeat that cools off per tick. */
	private static final float WEAPON_HEAT_COOLDOWN_FRACTION_PER_TICK = 0.01f;
	/** Fuel consumed per tick at 100% throttle. */
	/** Throttle fraction (0.0-1.0) below which fuel consumption ramps up gently rather than linearly. */
	private static final float THROTTLE_RAMP_THRESHOLD = 0.7f;
	/** Fraction of full-throttle fuel consumption reached AT THROTTLE_RAMP_THRESHOLD itself (rather than THROTTLE_RAMP_THRESHOLD's own value). */
	private static final float THROTTLE_RAMP_THRESHOLD_CONSUMPTION_FRACTION = 0.5f;
	/** How much fuel transfers per tick from a can sitting in the vehicle's own fuel slot into its internal pool. */
	private static final int VEHICLE_FUEL_TRANSFER_PER_TICK = 20;
	/** Maximum height (blocks) above the nearest solid ground/water surface below the vehicle for a Torpedo weapon to be usable. */
	private static final double TORPEDO_MAX_ALTITUDE_ABOVE_SURFACE = 15.0;
	/** How far (blocks) to search for the ground/water surface below the vehicle when checking Torpedo's own altitude limit. */
	private static final double TORPEDO_SURFACE_SEARCH_DEPTH = 64.0;
	/** How far (blocks) a raycast for ASMissile's own ground-point target, or AAMissile/ATMissile's own crosshair lock-on, searches. */
	private static final double MISSILE_TARGET_SEARCH_RANGE = 128.0;
	/** Lock-on range defaults to unlimited when a weapon's own LockRange isn't configured at all - see tudursvehiclemod$findLockOnTarget()'s own doc for why a genuinely finite ceiling is still needed even so (Box.expand()/getOtherEntities() both need one to build a search volume from at all). Chosen generously large enough that no real gameplay scenario would ever actually hit it. */
	private static final double MISSILE_LOCK_UNLIMITED_RANGE_CEILING = 4096.0;
	/** How far off-center (degrees, from the shooter's own view direction) an entity can be and still count as "under the crosshair" for AAMissile/ATMissile's own.. */
	// 5 degrees was an impractically narrow cone to hold continuously on a moving target (see tudursvehiclemod$updateMissileLockOnIndicators()'s own doc for why ANY target swap resets lock progress to 0) long enough to accumulate a weapon's own configured LockTime. Widened to a more forgiving 15.
	private static final double MISSILE_LOCK_ON_CONE_DEGREES = 15.0;
	/** Per tudursvehiclemod$classifyTargetPosition()'s own doc: minimum measured altitude (blocks) above the nearest solid block below before a candidate counts as genuinely airborne for AA_MISSILE/AT_MISSILE's own ground/air filtering, rather than merely a brief moment of not touching ground. */
	private static final double AIRBORNE_TARGET_MIN_ALTITUDE = 3.0;

	/** Last tick's grounded/floating state, to detect the exact tick it genuinely CHANGES (not just "is currently true/false"). */
	private boolean wasGroundedForGear = true;

	/** Directly sets GEAR_DEPLOYED, the same DataTracker flag updateLandingGear() itself reads as its own target - landingGearProgress eases toward this normally afterward, no different from any other cause of the gear moving. */
	public void tudursvehiclemod$setGearDeployed(boolean deployed) {
		this.dataTracker.set(GEAR_DEPLOYED, deployed);
	}

	/** When active, overrides updateToggleParts()'s own normal "is this part's own weapon currently selected" weapon_bay target with this fixed value instead - see that method's own doc for exactly where this is checked. Inactive (the default) falls back to the normal weapon-selection-driven behavior. Backed by CARRIER_WEAPON_BAY_OVERRIDE_ACTIVE/_VALUE (see that field's own doc for why this needs to be a proper synced DataTracker pair, not a plain field). */
	public void tudursvehiclemod$setWeaponBayForcedOpen(java.util.Optional<Boolean> forcedOpen) {
		this.dataTracker.set(CARRIER_WEAPON_BAY_OVERRIDE_ACTIVE, forcedOpen.isPresent());
		this.dataTracker.set(CARRIER_WEAPON_BAY_OVERRIDE_VALUE, forcedOpen.orElse(false));
	}

	/** Eases landingGearProgress towards its current target. */
	private void updateLandingGear() {
		this.prevLandingGearProgress = this.landingGearProgress;
		if (this instanceof SubmarineEntity) {
			// Landing gear has no real use case for a submarine - per a
			// direct request, disabled unconditionally (not just "unless a
			// landing_gear part is configured") as a precaution.
			return;
		}
		boolean grounded = this.isOnGround() || this.isTouchingWater();
		if (grounded && !this.wasGroundedForGear) {
			// Just landed - auto-deploy, same as MC Heli's own automatic behavior.
			this.dataTracker.set(GEAR_DEPLOYED, true);
			this.tudursvehiclemod$onJustLanded();
		}
		this.wasGroundedForGear = grounded;
		float target = this.dataTracker.get(GEAR_DEPLOYED) ? 0f : 1f;
		float step = LANDING_GEAR_SPEED / 90f; // LANDING_GEAR_SPEED is degrees-equivalent; progress is 0..1
		if (this.landingGearProgress < target) {
			this.landingGearProgress = Math.min(target, this.landingGearProgress + step);
		} else if (this.landingGearProgress > target) {
			this.landingGearProgress = Math.max(target, this.landingGearProgress - step);
		}
	}

	/** Manually flips the gear's own target (deployed &lt;-&gt; retracted), independent of the automatic behavior. */
	public void toggleLandingGear() {
		this.dataTracker.set(GEAR_DEPLOYED, !this.dataTracker.get(GEAR_DEPLOYED));
	}

	/** Called exactly once at the moment this vehicle touches down (see updateLandingGear()'s own doc for the exact "just landed" transition this fires on) - empty by default. Overridden by AircraftEntity for its own roll-angle-based landing attitude damage/destruction rules. */
	protected void tudursvehiclemod$onJustLanded() {
	}

	/** Raw target state (not the eased animation progress. */
	public boolean isGearDeployed() {
		return this.dataTracker.get(GEAR_DEPLOYED);
	}

	/** False by default; only AircraftEntity/HelicopterEntity override to true - other vehicle types (car/ship/submarine) have no landing gear concept at all. */
	public boolean tudursvehiclemod$supportsLandingGearDisplay() {
		return false;
	}

	/** Non-interpolated landing gear progress (0=deployed, 1=retracted. */
	public float getLandingGearProgress() {
		return this.landingGearProgress;
	}

	/** Render-interpolated counterpart of getLandingGearProgress(). */
	public float getLandingGearProgress(float tickDelta) {
		return this.prevLandingGearProgress + (this.landingGearProgress - this.prevLandingGearProgress) * tickDelta;
	}

	/** Eases every TogglePart declared on this vehicle's definition towards its current target (0=closed, 1=open), at that part's own configured speed. */
	private void updateToggleParts() {
		for (TogglePart part : getDefinition().toggleParts()) {
			float previous = this.togglePartProgress.getOrDefault(part.part(), 0f);
			this.prevTogglePartProgress.put(part.part(), previous);
			// Per CANOPY_OPEN's own doc: moved up from further below (was previously computed only for the speedMultiplier selection) so the target-computation branches below can also select isCanopyOpen() vs isHatchOpen() by this same prefix.
			String partNameLower = part.part().toLowerCase(java.util.Locale.ROOT);
			boolean isCanopyPart = partNameLower.startsWith("$canopy");

			float target;
			if ("landing_gear".equals(part.trigger()) || "landing_gear_reversed".equals(part.trigger())) {
				target = this.landingGearProgress;
				// Follows the gear's own progress directly rather than easing independently towards it.
				this.togglePartProgress.put(part.part(), target);
				continue;
			} else if ("throttle".equals(part.trigger())) {
				// Follows this vehicle's own current throttle fraction directly (clamped to 0-1 - MC Heli's own format has no reverse-throttle concept for this specific part), the same "follow directly" pattern landing_gear's own branch above already uses, since the throttle value itself already changes gradually via this project's own acceleration/deceleration ramping.
				target = MathHelper.clamp(this.getThrottle(), 0f, 1f);
				this.togglePartProgress.put(part.part(), target);
				continue;
			} else if ("weapon_bay".equals(part.trigger())) {
				// Previously opened purely on "is this bay's own weapon the currently SELECTED one", regardless of whether that weapon can actually be used right now at all (e.g. a submarine with a non-Torpedo, non-UsableWhileDiving weapon selected while diving - see SubmarineEntity's own tudursvehiclemod$canFireWeapons(Optional) doc). A bay opening for a weapon that's frozen/unusable in the current context doesn't make sense - now also requires tudursvehiclemod$canFireWeapons() to actually agree the selected weapon is usable. A no-op for every non-Submarine vehicle (canFireWeapons(Optional) defaults to delegating straight to the plain, always-true base check), so this only actually changes behavior for a diving submarine.
				target = this.dataTracker.get(CARRIER_WEAPON_BAY_OVERRIDE_ACTIVE)
						? (this.dataTracker.get(CARRIER_WEAPON_BAY_OVERRIDE_VALUE) ? 1f : 0f)
						: (tudursvehiclemod$isWeaponBayWeaponSelected(part)
								&& this.tudursvehiclemod$canFireWeapons(tudursvehiclemod$resolveWeaponByName(part.weaponName()))
								? 1f : 0f);
			} else if ("light_hatch".equals(part.trigger())) {
				// Per Readme_Aircraft.txt's own doc for AddPartLightHatch ("サーチライトがONの間だけ開くパーツを追加する" - adds a part that opens only while the search light is on): unlike landing_gear_hatch's own "only during an active transition" shape, this simply follows the light's own on/off state directly - MC Heli's own format has no separate "opening"/"closing" phase for this one, just open-while-on.
				target = this.tudursvehiclemod$isSearchLightOn() ? 1f : 0f;
			} else if ("landing_gear_hatch".equals(part.trigger())) {
				// AddPartLGHatch - a hatch that opens only while the
				// gear is actively transitioning: open only while the gear itself is
				// actively mid-transition (neither fully retracted nor
				// fully deployed) - closed the instant it settles at
				// either extreme. A small epsilon (rather than a strict
				// 0/1 comparison) avoids this flickering open for a
				// single tick right as landingGearProgress asymptotically
				// approaches but never mathematically reaches its own
				// target.
				float epsilon = 0.01f;
				target = (this.landingGearProgress > epsilon && this.landingGearProgress < 1f - epsilon) ? 1f : 0f;
			} else if ("wing_fold".equals(part.trigger())) {
				// A normal folding wing (variableSweepWing() false) keeps MC Heli's own genuine AddPartWing convention (angle=0/progress 0 = extended/unfolded, progress 1 = folded) - confirmed already correct for that vehicle type. A variable-sweep wing (variableSweepWing() true) needs the OPPOSITE direction instead (progress 0 = folded/swept, progress 1 = extended) to display correctly for that vehicle type - resolved entirely here in code (branching on WingSweepConfig's own already-parsed variableSweepWing() flag) rather than requiring either vehicle's own model/AddPartWing data to be edited.
				boolean variableSweep = this.tudursvehiclemod$getWingSweepConfig().variableSweepWing();
				target = variableSweep ? (isWingFoldOpen() ? 1f : 0f) : (isWingFoldOpen() ? 0f : 1f);
			} else if (this instanceof SubmarineEntity) {
				// Right after spawning (isHatchOpen()
				// defaults to true - surfaced), this hull's own hatch part
				// visibly eased itself shut with zero player input at all,
				// and toggling to dive mode (isHatchOpen() becoming false)
				// made it visibly pop back OPEN instead - exactly backwards.
				// This hull's own hatch/conning-tower part turns out to use
				// the OPPOSITE angle convention from every other vehicle's
				// own hatch/canopy part here (angle=0/progress=0 as ITS OWN
				// open pose, the rotated angle=1 as ITS OWN closed one) -
				// the exact same kind of per-part-type quirk wing_fold's
				// own case above already accounts for, just for this one
				// hull's own hatch specifically rather than every vehicle's.
				target = (isCanopyPart ? isCanopyOpen() : isHatchOpen()) ? 0f : 1f;
			} else {
				// isHatchOpen()==true (the flag's own
				// name) was mapping to target=0 (MC Heli's own documented
				// CLOSED/base pose) and false (this flag's own default,
				// before anything ever explicitly sets it) was mapping to
				// target=1 (the OPEN/rotated pose) - genuinely backwards
				// from what the flag's own name says, and the direct
				// cause of hatches/canopies appearing to "open by
				// themselves" by default on every vehicle that has one,
				// with no explicit toggle ever having happened at all.
				// Corrected to the straightforward mapping instead: open
				// (true) -> the open pose (1), closed (false, the
				// default) -> the closed pose (0).
				target = (isCanopyPart ? isCanopyOpen() : isHatchOpen()) ? 1f : 0f;
			}

			// Canopy-specific parts (identified by the
			// "$canopy" name prefix - this project's own converter's
			// established, reliable naming convention for AddPartCanopy/
			// AddPartSlideCanopy specifically, distinct from "$hatch" for
			// AddPartHatch/AddPartSlideHatch, even though both currently
			// share the same "key" trigger) move at 1/10th their own
			// configured speed - everything else (hatches, landing gear,
			// weapon bays, wing fold) is unaffected. Per a further direct
			// request, hatch-specific parts (the "$hatch" name prefix)
			// instead move at DOUBLE their own configured speed.
			float speedMultiplier;
			if (isCanopyPart) {
				speedMultiplier = 0.1f;
			} else if (partNameLower.startsWith("$hatch")) {
				speedMultiplier = 2.0f;
			} else {
				speedMultiplier = 1.0f;
			}
			float maxStep = "slide".equals(part.mode())
					? part.speed() * speedMultiplier / 20f // speed is blocks/sec-ish; step per tick
					: part.speed() * speedMultiplier / 90f; // speed is degrees/tick-equivalent; progress is 0..1 over max_angle
			float current = previous;
			if (current < target) {
				current = Math.min(target, current + maxStep);
			} else if (current > target) {
				current = Math.max(target, current - maxStep);
			}
			this.togglePartProgress.put(part.part(), current);
		}
	}

	/** Per RunwayDefinition's own hatchOffsetX/Y/Z doc: eases every runway's own hatchProgress towards isHatchOpen() ? 1 : 0, at that runway's own configured hatchMoveSpeed (progress-per-second, same convention as TogglePart's own speed field - see updateToggleParts()'s own maxStep doc for the identical /20f-per-tick derivation). Runs unconditionally on both client and server (same as updateToggleParts()) - deterministic and driven purely by isHatchOpen() (an ordinary synced DataTracker boolean), so no separate network sync is needed for this progress value itself, exactly like every other toggle-driven animation in this project. A runway with all-zero hatchOffsetX/Y/Z (the default - no hatch tracking at all) still gets a harmless, unused progress value computed here; tudursvehiclemod$computeCarrierRunwayTileWorldPosClientSide()/RunwayDefinition's own tudursvehiclemod$withHatchProgress() both no-op for that case regardless. */
	private void updateRunwayHatchProgress() {
		java.util.List<com.example.tudursvehiclemod.asset.RunwayDefinition> runways = getDefinition().runways();
		for (int runwayIndex = 0; runwayIndex < runways.size(); runwayIndex++) {
			com.example.tudursvehiclemod.asset.RunwayDefinition runway = runways.get(runwayIndex);
			float previous = this.runwayHatchProgress.getOrDefault(runwayIndex, 0f);
			this.prevRunwayHatchProgress.put(runwayIndex, previous);
			float target = this.isHatchOpen() ? 1f : 0f;
			// This project's own hatch-type TogglePart convention (the "$hatch" name prefix - see updateToggleParts()'s own speedMultiplier doc) moves at DOUBLE its own configured speed value - applied here unconditionally too (rather than reading any specific TogglePart's own name, which RunwayDefinition's own hatch fields are deliberately decoupled from - see that record's own doc) per direct confirmation this is only ever combined with hatch-type parts in practice, never canopy-type. Without this, entering the same numeric speed value on both produced a visibly slower runway than its own matching hatch.
			float maxStep = (float) runway.hatchMoveSpeed() * 2f / 20f;
			float current = previous;
			if (current < target) {
				current = Math.min(target, current + maxStep);
			} else if (current > target) {
				current = Math.max(target, current - maxStep);
			}
			this.runwayHatchProgress.put(runwayIndex, current);
		}
	}

	/** Non-interpolated hatch-driven progress (0=closed/base position, 1=fully open/offset) for a runway, by its own index within getDefinition().runways() - see RunwayDefinition's own hatchOffsetX/Y/Z doc. 0 for an out-of-range index (defensive - shouldn't normally happen). */
	public float tudursvehiclemod$getRunwayHatchProgress(int runwayIndex) {
		return this.runwayHatchProgress.getOrDefault(runwayIndex, 0f);
	}


	private boolean tudursvehiclemod$isWeaponBayWeaponSelected(TogglePart part) {
		int selectedIndex = this.tudursvehiclemod$getSelectedWeaponIndex();
		java.util.List<WeaponDefinition> weapons = this.getDefinition().weapons();
		if (selectedIndex < 0 || selectedIndex >= weapons.size()) {
			return false;
		}
		return part.weaponName().map(name -> name.equals(weapons.get(selectedIndex).weaponName())).orElse(false);
	}

	/** Non-interpolated progress (0=closed, 1=open) for a named TogglePart, or 0 if this vehicle has no such part. */
	public float getTogglePartProgress(String partName) {
		return this.togglePartProgress.getOrDefault(partName, 0f);
	}

	/** Render-interpolated counterpart of getTogglePartProgress(String). */
	public float getTogglePartProgress(String partName, float tickDelta) {
		float previous = this.prevTogglePartProgress.getOrDefault(partName, 0f);
		float current = this.togglePartProgress.getOrDefault(partName, 0f);
		return previous + (current - previous) * tickDelta;
	}

	/** Accumulates each declared PartAnimation's spin phase by degreesPerTick()*getSpinningPartSpeedMultiplier() every tick, instead of spinning at a fixed rate regardless of throttle.. */
	private void updateSpinningParts() {
		float speedMultiplier = getSpinningPartSpeedMultiplier();
		for (com.example.tudursvehiclemod.asset.PartAnimation part : getDefinition().spinningParts()) {
			float previous = this.spinningPartsPhase.getOrDefault(part.part(), 0f);
			this.prevSpinningPartsPhase.put(part.part(), previous);
			this.spinningPartsPhase.put(part.part(), previous + part.degreesPerTick() * speedMultiplier);
		}
	}

	/** The actual mechanism driving every spinning_part's visible rotation - defaults to getThrottle(). See HelicopterEntity's own override for why its rotor needs something different. */
	protected float getSpinningPartSpeedMultiplier() {
		return getThrottle();
	}

	/** Public wrapper for getSpinningPartSpeedMultiplier() - see that method's own doc. Needed by client.sound.VehicleEngineSoundManager (a different package) to correct engine sound for a vehicle whose rotor speed doesn't move in lockstep with throttle. */
	public float tudursvehiclemod$getSpinningPartSpeedMultiplier() {
		return getSpinningPartSpeedMultiplier();
	}

	/** Non-interpolated accumulated spin phase (degrees) for a named PartAnimation, or 0 if this vehicle has no such part. */
	public float getSpinningPartPhase(String partName) {
		return this.spinningPartsPhase.getOrDefault(partName, 0f);
	}

	/** Render-interpolated counterpart of getSpinningPartPhase(String). */
	public float getSpinningPartPhase(String partName, float tickDelta) {
		float previous = this.prevSpinningPartsPhase.getOrDefault(partName, 0f);
		float current = this.spinningPartsPhase.getOrDefault(partName, 0f);
		return previous + (current - previous) * tickDelta;
	}

	/** How many ticks after tryFireWeapon's own last recorded fire attempt (see that method's own doc) a spinning part linked to that weapon keeps spinning - a small grace window (rather than requiring a fire attempt on THIS exact tick) covers the fire key's own client-to-server network latency, so the barrel doesn't visibly stutter/stop for a tick or two between an actual server-side fire attempt and the next one arriving. */
	private static final int WEAPON_FIRING_GRACE_TICKS = 3;

	/** True if weaponIndex currently has its bit set in the synced FIRING_WEAPON_BITMASK - see that field's own doc for why this is synced (rather than reading the plain, server-only lastFireAttemptTick map directly) and tudursvehiclemod$clearExpiredFiringBits()'s own doc for how/when that bit actually gets cleared again. */
	public boolean tudursvehiclemod$isWeaponCurrentlyFiring(int weaponIndex) {
		if (weaponIndex < 0 || weaponIndex >= 32) {
			return false;
		}
		return (this.dataTracker.get(FIRING_WEAPON_BITMASK) & (1 << weaponIndex)) != 0;
	}

	/** Server-only: clears FIRING_WEAPON_BITMASK bits whose lastFireAttemptTick aged past WEAPON_FIRING_GRACE_TICKS, then syncs the result down to clients. */
	private void tudursvehiclemod$clearExpiredFiringBits() {
		int bitmask = this.dataTracker.get(FIRING_WEAPON_BITMASK);
		if (bitmask == 0) {
			return;
		}
		int updated = bitmask;
		for (java.util.Map.Entry<Integer, Integer> entry : this.lastFireAttemptTick.entrySet()) {
			int weaponIndex = entry.getKey();
			if (weaponIndex >= 32) {
				continue;
			}
			if ((updated & (1 << weaponIndex)) != 0 && this.age - entry.getValue() > WEAPON_FIRING_GRACE_TICKS) {
				updated &= ~(1 << weaponIndex);
			}
		}
		if (updated != bitmask) {
			this.dataTracker.set(FIRING_WEAPON_BITMASK, updated);
		}
	}

	/** Much shorter than WEAPON_FIRING_GRACE_TICKS - just long enough to reliably sync to the client while still letting a cannon-style weapon's next shot produce its own distinct recoil rising edge. */
	private static final int ACTUAL_FIRE_GRACE_TICKS = 1;

	/** Same idea as tudursvehiclemod$clearExpiredFiringBits() above, just for ACTUAL_FIRE_BITMASK/lastActualFireTick/ACTUAL_FIRE_GRACE_TICKS instead. */
	private void tudursvehiclemod$clearExpiredActualFireBits() {
		int bitmask = this.dataTracker.get(ACTUAL_FIRE_BITMASK);
		if (bitmask == 0) {
			return;
		}
		int updated = bitmask;
		for (java.util.Map.Entry<Integer, Integer> entry : this.lastActualFireTick.entrySet()) {
			int weaponIndex = entry.getKey();
			if (weaponIndex >= 32) {
				continue;
			}
			if ((updated & (1 << weaponIndex)) != 0 && this.age - entry.getValue() > ACTUAL_FIRE_GRACE_TICKS) {
				updated &= ~(1 << weaponIndex);
			}
		}
		if (updated != bitmask) {
			this.dataTracker.set(ACTUAL_FIRE_BITMASK, updated);
		}
	}

	/** This vehicle's own weapon index whose own weaponName() matches weaponName (case-insensitive), or -1 if none does. */
	private int tudursvehiclemod$findWeaponIndexByName(VehicleDefinition def, String weaponName) {
		String target = weaponName.toLowerCase(java.util.Locale.ROOT);
		java.util.List<WeaponDefinition> weapons = def.weapons();
		for (int i = 0; i < weapons.size(); i++) {
			if (weapons.get(i).weaponName().toLowerCase(java.util.Locale.ROOT).equals(target)) {
				return i;
			}
		}
		return -1;
	}

	/** Accumulates a spin-linked WeaponPart's phase by its weapon's rotationSpeedPerSecond only while actually firing. */
	private void tudursvehiclemod$updateWeaponPartSpin(VehicleDefinition def) {
		for (com.example.tudursvehiclemod.asset.WeaponPart part : def.weaponParts()) {
			if (!part.spinsWhileFiring()) {
				continue;
			}
			float previous = this.weaponPartSpinPhase.getOrDefault(part.part(), 0f);
			this.prevWeaponPartSpinPhase.put(part.part(), previous);
			float delta = 0f;
			if (part.weaponName().isPresent()) {
				int weaponIndex = tudursvehiclemod$findWeaponIndexByName(def, part.weaponName().get());
				if (weaponIndex >= 0 && this.tudursvehiclemod$isWeaponCurrentlyFiring(weaponIndex)) {
					float rotationSpeedPerSecond = def.weapons().get(weaponIndex).rotationSpeedPerSecond();
					delta = rotationSpeedPerSecond * 360f / 20f;
				}
			}
			this.weaponPartSpinPhase.put(part.part(), previous + delta);
		}
	}

	/** Render-interpolated accumulated spin phase (degrees) for a named WeaponPart's own gatling-style spin (see tudursvehiclemod$updateWeaponPartSpin()'s own doc), or 0 if this vehicle has no such spinning part. */
	public float tudursvehiclemod$getWeaponPartSpinPhase(String partName, float tickDelta) {
		float previous = this.prevWeaponPartSpinPhase.getOrDefault(partName, 0f);
		float current = this.weaponPartSpinPhase.getOrDefault(partName, 0f);
		return previous + (current - previous) * tickDelta;
	}

	/** Holds the last-tracked direction once the tracked occupant leaves, rather than snapping to 0, keyed by (seatIndex, pilotFallback). Stores the RAW pre-clamp relative yaw/pitch. */
	private final java.util.Map<Integer, Float> lastTrackedRelativeYawBySeat = new java.util.HashMap<>();
	private final java.util.Map<Integer, Float> lastTrackedPitchBySeat = new java.util.HashMap<>();

	/** This part's own CURRENT tracked yaw/pitch, easing toward the instantaneous target at a limited rate (turret_rotation_speed -). Keyed by WeaponPart.part(). Plain/non-synced, computed deterministically on both sides each tick (same reasoning as togglePartProgress). Left unpopulated for any part whose weapon mount doesn't configure this - callers then fall back to the pre-existing instant-tracking computation. */
	private final java.util.Map<String, Float> weaponPartCurrentYaw = new java.util.HashMap<>();
	private final java.util.Map<String, Float> weaponPartCurrentPitch = new java.util.HashMap<>();

	/** Same as weaponPartCurrentYaw/-Pitch above, but for a child part's PARENT stage (ChildInfo's own parentYawFollow/parentPitchFollow), which is a separate rotation from the part's own stage. */
	private final java.util.Map<String, Float> weaponPartParentCurrentYaw = new java.util.HashMap<>();
	private final java.util.Map<String, Float> weaponPartParentCurrentPitch = new java.util.HashMap<>();

	/** {yaw, pitch} target - same computation as tudursvehiclemod$getPartStageRotation()'s own "manned" branch, extracted so both that method, updateWeaponPartAimSpeed(), and tryFireWeapon() agree on the same number. */
	private float[] tudursvehiclemod$computeWeaponPartTarget(int seatIndex, boolean pilotFallback,
			com.example.tudursvehiclemod.asset.WeaponAimRange aimRange, boolean yawFollow, boolean pitchFollow, float tickProgress) {
		double defaultYaw = aimRange != null ? aimRange.defaultYaw() : 0.0;
		float rawYaw = this.getWeaponAimYaw(seatIndex, pilotFallback, null, tickProgress);
		float rawPitch = this.getWeaponAimPitch(seatIndex, pilotFallback, null, tickProgress);
		float targetYaw = (float) defaultYaw;
		if (yawFollow) {
			targetYaw = aimRange != null ? (float) tudursvehiclemod$clampYawToAimRange(rawYaw, aimRange) : rawYaw;
		}
		float targetPitch = 0f;
		if (pitchFollow) {
			targetPitch = aimRange != null ? (float) tudursvehiclemod$clampPitchToAimRange(rawPitch, aimRange) : rawPitch;
		}
		return new float[]{targetYaw, targetPitch};
	}

	/** Eases every weapon-tracking WeaponPart's own current yaw/pitch (own and parent stage) toward its instantaneous target, at a rate from its OWN associated weapon mount's turretRotationSpeed(). A part whose weapon mount doesn't configure this is skipped entirely (cache left unpopulated - callers fall back to instant tracking). Runs on both client/server, like updateToggleParts(). An unmanned part (see tudursvehiclemod$getPartStageRotation()'s own doc) is skipped per-part, freezing its current value. */
	private void tudursvehiclemod$updateWeaponPartAimSpeed(VehicleDefinition def) {
		for (com.example.tudursvehiclemod.asset.WeaponPart part : def.weaponParts()) {
			boolean hasOwnStage = part.yawFollow() || part.pitchFollow();
			boolean hasParentStage = part.childInfo().isPresent()
					&& (part.childInfo().get().parentYawFollow() || part.childInfo().get().parentPitchFollow());
			if (!hasOwnStage && !hasParentStage) {
				continue;
			}
			java.util.Optional<com.example.tudursvehiclemod.asset.WeaponDefinition> weapon =
					tudursvehiclemod$resolveWeaponByName(part.weaponName());
			java.util.Optional<Float> turretRotationSpeed = weapon.flatMap(com.example.tudursvehiclemod.asset.WeaponDefinition::turretRotationSpeed);
			if (turretRotationSpeed.isEmpty()) {
				continue;
			}
			Entity trackingOccupant = this.tudursvehiclemod$resolveWeaponTrackingOccupant(part.seatIndex(), part.pilotFallback());
			boolean effectivelyTracking = trackingOccupant != null
					&& !(this instanceof FreeCameraVehicle freeCameraVehicle
							&& !freeCameraVehicle.tudursvehiclemod$isEffectiveFreeLook(trackingOccupant));
			if (!(this.tudursvehiclemod$canFireWeapons(weapon) && effectivelyTracking)) {
				continue;
			}
			float maxStep = turretRotationSpeed.get() / 20f;
			com.example.tudursvehiclemod.asset.WeaponAimRange aimRange = part.aimRange().orElse(null);
			// This weapon's own neutral/default orientation (aimRange's own defaultYaw, pitch 0) - used as the DEFAULT below whenever this specific part has no cached value AT ALL yet, rather than the current target itself (which previously made the very first tracking attempt start already "at" the target, reporting settled with zero actual rotation - unlike every SUBSEQUENT boarding, where the cache already holds a stale value from whoever tracked it before and genuinely needs to rotate away from it).
			float defaultYawForFirstTracking = aimRange != null ? (float) aimRange.defaultYaw() : 0f;
			if (hasOwnStage) {
				float[] target = tudursvehiclemod$computeWeaponPartTarget(part.seatIndex(), part.pilotFallback(),
						aimRange, part.yawFollow(), part.pitchFollow(), 1.0f);
				// Each axis's own cache is only updated when THIS part actually follows that specific axis - hasOwnStage being true from EITHER yawFollow OR pitchFollow alone previously updated BOTH unconditionally, even for a part (e.g. a yaw-only turret base, whose own pitchFollow is false) that never actually tracks the other axis at all. computeWeaponPartTarget() returns 0 for an axis a part doesn't follow, which - if written here regardless - would wrongly populate that axis's own cache with a value drifting toward 0, later misread by getEffectiveWeaponAim() (via this same part, since findTrackingPart() can select ANY part that follows at least one axis) as this whole weapon's own "effective" aim on that axis, completely unrelated to wherever the player is actually aiming.
				if (part.yawFollow()) {
					float currentYaw = this.weaponPartCurrentYaw.getOrDefault(part.part(), defaultYawForFirstTracking);
					this.weaponPartCurrentYaw.put(part.part(), tudursvehiclemod$stepTowardsYaw(currentYaw, target[0], maxStep, aimRange));
				}
				if (part.pitchFollow()) {
					float currentPitch = this.weaponPartCurrentPitch.getOrDefault(part.part(), 0f);
					this.weaponPartCurrentPitch.put(part.part(), tudursvehiclemod$stepTowardsAngle(currentPitch, target[1], maxStep));
				}
			}
			if (hasParentStage) {
				com.example.tudursvehiclemod.asset.WeaponPart.ChildInfo childInfo = part.childInfo().get();
				float[] parentTarget = tudursvehiclemod$computeWeaponPartTarget(part.seatIndex(), part.pilotFallback(),
						aimRange, childInfo.parentYawFollow(), childInfo.parentPitchFollow(), 1.0f);
				// Per the same direct bug report/fix as hasOwnStage's own identical fix above: each axis's own parent-stage cache is only updated when THIS part's own childInfo actually follows that specific axis.
				if (childInfo.parentYawFollow()) {
					float parentCurrentYaw = this.weaponPartParentCurrentYaw.getOrDefault(part.part(), defaultYawForFirstTracking);
					this.weaponPartParentCurrentYaw.put(part.part(), tudursvehiclemod$stepTowardsYaw(parentCurrentYaw, parentTarget[0], maxStep, aimRange));
				}
				if (childInfo.parentPitchFollow()) {
					float parentCurrentPitch = this.weaponPartParentCurrentPitch.getOrDefault(part.part(), 0f);
					this.weaponPartParentCurrentPitch.put(part.part(), tudursvehiclemod$stepTowardsAngle(parentCurrentPitch, parentTarget[1], maxStep));
				}
			}
		}
	}

	/** Moves current towards target by at most maxStep degrees this tick, taking the shortest way around the ±180 wrap (MathHelper.wrapDegrees on the raw difference) rather than always increasing/decreasing numerically - the same shortest-path reasoning tudursvehiclemod$getWeaponAimYaw() itself already relies on via MathHelper.wrapDegrees for the SAME reason. */
	private static float tudursvehiclemod$stepTowardsAngle(float current, float target, float maxStep) {
		float diff = MathHelper.wrapDegrees(target - current);
		if (Math.abs(diff) <= maxStep) {
			return current + diff;
		}
		return current + Math.copySign(maxStep, diff);
	}

	/** Yaw-specific variant of tudursvehiclemod$stepTowardsAngle() - see this whole fix's own doc (the direct call site's own comment) for why a GENUINELY constrained part (its own aim range's yaw arc narrower than a full 360° circle) must NOT use the wrapped shortest-path direction: current and target are both already clamped within [minYaw, maxYaw] by tudursvehiclemod$computeWeaponPartTarget(), so stepping by the plain, unwrapped difference between them can never leave that range - correctly forcing the long way around whenever the mathematically-shorter wrapped path would have crossed this weapon's own restricted zone. An unconstrained part - aimRange == null, OR present but with its own min_yaw/max_yaw left at their own defaults (-180/180, i.e. NOT actually restricted at all, a very common configuration for a weapon that just needs default_yaw for tracking) - delegates straight to the original wrapped, shortest-path behavior, unchanged. */
	private static float tudursvehiclemod$stepTowardsYaw(float current, float target, float maxStep,
			com.example.tudursvehiclemod.asset.WeaponAimRange aimRange) {
		if (aimRange == null || aimRange.maxYaw() - aimRange.minYaw() >= 360.0) {
			return tudursvehiclemod$stepTowardsAngle(current, target, maxStep);
		}
		float diff = target - current;
		if (Math.abs(diff) <= maxStep) {
			return target;
		}
		return current + Math.copySign(maxStep, diff);
	}

	/** Whether this part has caught up to target (0.5° tolerance). True if not cached at all (turret_rotation_speed unconfigured, or part unmanned). */
	private boolean tudursvehiclemod$isWeaponPartSettled(com.example.tudursvehiclemod.asset.WeaponPart part, float[] target) {
		Float currentYaw = this.weaponPartCurrentYaw.get(part.part());
		Float currentPitch = this.weaponPartCurrentPitch.get(part.part());
		if (currentYaw == null || currentPitch == null) {
			return true;
		}
		float settleToleranceDegrees = 0.5f;
		return Math.abs(MathHelper.wrapDegrees(target[0] - currentYaw)) <= settleToleranceDegrees
				&& Math.abs(target[1] - currentPitch) <= settleToleranceDegrees;
	}

	/** The WeaponPart (if any) tracking this weapon by name - shared by tudursvehiclemod$isTurretUnsettled() and tryFireWeapon()'s own aim-direction branch. */
	private java.util.Optional<com.example.tudursvehiclemod.asset.WeaponPart> tudursvehiclemod$findTrackingPart(
			com.example.tudursvehiclemod.asset.WeaponDefinition weapon, VehicleDefinition def) {
		return def.weaponParts().stream()
				.filter(part -> part.weaponName().isPresent() && part.weaponName().get().equalsIgnoreCase(weapon.weaponName()))
				.filter(part -> part.yawFollow() || part.pitchFollow())
				.findFirst();
	}

	/** This weapon's own EFFECTIVE current aim direction - {yaw, pitch}, both absolute (DefaultYaw-relative-then-added-back, matching every other absolute yaw in this method). The SAME cached (speed-limited, if turret_rotation_speed configured) value tryFireWeapon() itself actually fires at right now, or the raw instant shooter-view direction otherwise - see tudursvehiclemod$updateWeaponPartAimSpeed()'s own doc. For HUD/external display purposes: reflects wherever this weapon's own tracking part(s) ACTUALLY currently point, not necessarily wherever the shooter is looking. {0, 0} for a weapon with no aimRange at all (a fixed mount - no meaningful "current aim" concept to report).
	 *
	 * Yaw and pitch are resolved INDEPENDENTLY of each other (searching every part associated with this weapon, own stage then parent stage, for a cached value on that SPECIFIC axis) - a split-axis turret (a yaw-only part plus a separate pitch-only part, no single part/stage tracking both at once) still gets correctly speed-limited on both axes this way, each from whichever part actually tracks it, rather than requiring one single part to supply both before using either (which previously meant no cached value was ever used at all for this exact configuration, silently disabling speed limiting on BOTH axes even once each part's own cache itself held the correct value for its own axis). */
	public double[] tudursvehiclemod$getEffectiveWeaponAim(com.example.tudursvehiclemod.asset.WeaponDefinition weapon) {
		if (weapon.aimRange().isEmpty()) {
			return new double[]{0.0, 0.0};
		}
		com.example.tudursvehiclemod.asset.WeaponAimRange range = weapon.aimRange().get();
		VehicleDefinition def = this.getDefinition();
		Float cachedYaw = null;
		Float cachedPitch = null;
		for (com.example.tudursvehiclemod.asset.WeaponPart part : def.weaponParts()) {
			if (part.weaponName().isEmpty() || !part.weaponName().get().equalsIgnoreCase(weapon.weaponName())) {
				continue;
			}
			if (cachedYaw == null && part.yawFollow()) {
				cachedYaw = this.weaponPartCurrentYaw.get(part.part());
			}
			if (cachedPitch == null && part.pitchFollow()) {
				cachedPitch = this.weaponPartCurrentPitch.get(part.part());
			}
			if (part.childInfo().isPresent()) {
				com.example.tudursvehiclemod.asset.WeaponPart.ChildInfo childInfo = part.childInfo().get();
				if (cachedYaw == null && childInfo.parentYawFollow()) {
					cachedYaw = this.weaponPartParentCurrentYaw.get(part.part());
				}
				if (cachedPitch == null && childInfo.parentPitchFollow()) {
					cachedPitch = this.weaponPartParentCurrentPitch.get(part.part());
				}
			}
			if (cachedYaw != null && cachedPitch != null) {
				break;
			}
		}
		double yaw = cachedYaw != null ? cachedYaw : range.defaultYaw() + this.getWeaponAimYaw(weapon.seatIndex(), weapon.pilotUsable(), range, 1.0f);
		double pitch = cachedPitch != null ? cachedPitch : this.getWeaponAimPitch(weapon.seatIndex(), weapon.pilotUsable(), range, 1.0f);
		return new double[]{yaw, pitch};
	}

	/** Whether this weapon's own tracking part is still mid-rotation toward its target - gates firing in tryFireWeapon(), before any ammo/cooldown/heat consumption. False (never blocks) when this weapon has no aimRange, the shooter is a non-free-looking pilot, or the part isn't currently cached (turret_rotation_speed unconfigured). */
	private boolean tudursvehiclemod$isTurretUnsettled(com.example.tudursvehiclemod.asset.WeaponDefinition weapon,
			VehicleDefinition def, ServerPlayerEntity shooter) {
		if (weapon.aimRange().isEmpty()) {
			return false;
		}
		com.example.tudursvehiclemod.asset.WeaponAimRange range = weapon.aimRange().get();
		boolean pilotSteeringViaFreeLook = shooter != null && this instanceof FreeCameraVehicle freeCameraVehicle
				&& !freeCameraVehicle.tudursvehiclemod$isEffectiveFreeLook(shooter);
		if (pilotSteeringViaFreeLook) {
			return false;
		}
		java.util.Optional<com.example.tudursvehiclemod.asset.WeaponPart> trackingPart = tudursvehiclemod$findTrackingPart(weapon, def);
		if (trackingPart.isEmpty() || !this.weaponPartCurrentYaw.containsKey(trackingPart.get().part())
				|| !this.weaponPartCurrentPitch.containsKey(trackingPart.get().part())) {
			return false;
		}
		float[] target = tudursvehiclemod$computeWeaponPartTarget(trackingPart.get().seatIndex(), trackingPart.get().pilotFallback(),
				range, trackingPart.get().yawFollow(), trackingPart.get().pitchFollow(), 1.0f);
		return !tudursvehiclemod$isWeaponPartSettled(trackingPart.get(), target);
	}

	private int tudursvehiclemod$weaponTrackingCacheKey(int seatIndex, boolean pilotFallback) {
		return seatIndex * 2 + (pilotFallback ? 1 : 0);
	}

	/** Relative yaw a WeaponPart should track - falls back to the pilot (seat 0) if seatIndex's seat is empty and pilotFallback is true. Doesn't itself check canFireWeapons() - the caller already does. */
	public float getWeaponAimYaw(int seatIndex, boolean pilotFallback, com.example.tudursvehiclemod.asset.WeaponAimRange aimRange, float tickProgress) {
		Entity occupant = tudursvehiclemod$resolveWeaponTrackingOccupant(seatIndex, pilotFallback);
		int cacheKey = tudursvehiclemod$weaponTrackingCacheKey(seatIndex, pilotFallback);
		float relative;
		if (occupant == null) {
			relative = this.lastTrackedRelativeYawBySeat.getOrDefault(cacheKey, 0f);
		} else {
			relative = net.minecraft.util.math.MathHelper.wrapDegrees(occupant.getYaw(tickProgress) - this.getYaw(tickProgress));
			this.lastTrackedRelativeYawBySeat.put(cacheKey, relative);
		}
		if (aimRange == null) {
			return relative;
		}
		return (float) tudursvehiclemod$clampedYawOffsetFromDefault(relative, aimRange);
	}

	/** Pitch a WeaponPart tied to seatIndex should rotate to - see getWeaponAimYaw(int, boolean, WeaponAimRange, float)'s own doc for pilotFallback/aimRange/why there's no canFireWeapons() check here. Unlike yaw, pitch has no equivalent "DefaultPitch" concept in MC Heli's own format at all (MinPitch/MaxPitch are already relative to the model's own neutral, unrotated pitch), so no offset subtraction is needed here - just the plain clamp. */
	public float getWeaponAimPitch(int seatIndex, boolean pilotFallback, com.example.tudursvehiclemod.asset.WeaponAimRange aimRange, float tickProgress) {
		Entity occupant = tudursvehiclemod$resolveWeaponTrackingOccupant(seatIndex, pilotFallback);
		int cacheKey = tudursvehiclemod$weaponTrackingCacheKey(seatIndex, pilotFallback);
		float pitch;
		if (occupant == null) {
			pitch = this.lastTrackedPitchBySeat.getOrDefault(cacheKey, 0f);
		} else {
			pitch = occupant.getPitch(tickProgress);
			this.lastTrackedPitchBySeat.put(cacheKey, pitch);
		}
		if (aimRange == null) {
			return pitch;
		}
		return (float) tudursvehiclemod$clampPitchToAimRange(pitch, aimRange);
	}

	/** target * rest^-1 via proper quaternion composition, avoiding a gimbal-lock-like axis mix-up the naive rotateY(offset).rotateX(pitch) approach suffered near DefaultYaw=±90°. */
	private static Quaternionf tudursvehiclemod$computePartRotation(double defaultYaw, float absoluteYaw, float pitch) {
		Quaternionf target = new Quaternionf()
				.rotateY((float) Math.toRadians(-absoluteYaw))
				.rotateX((float) Math.toRadians(pitch));
		Quaternionf restInverse = new Quaternionf().rotateY((float) Math.toRadians(defaultYaw));
		return target.mul(restInverse, new Quaternionf());
	}

	/** Returns just THIS part's own stage of a possible parent/child two-stage composition - the caller applies the parent's stage separately. yawFollow/pitchFollow false = stays at rest. */
	private Quaternionf tudursvehiclemod$getPartStageRotation(int seatIndex, boolean pilotFallback,
			com.example.tudursvehiclemod.asset.WeaponAimRange aimRange, boolean yawFollow, boolean pitchFollow,
			float tickProgress, java.util.Optional<com.example.tudursvehiclemod.asset.WeaponDefinition> weapon) {
		double defaultYaw = aimRange != null ? aimRange.defaultYaw() : 0.0;
		// An unmanned weapon (no occupant currently
		// tracking it, or weapons disabled entirely - see
		// tudursvehiclemod$canFireWeapons()'s own doc) rests facing its
		// own DefaultYaw with pitch=0, regardless of yawFollow/pitchFollow
		// - rather than running the occupant-less sentinel yaw (0)
		// getWeaponAimYaw() itself returns through the same clamp an
		// actual occupant's own aim would use below, which isn't
		// necessarily DefaultYaw at all (DefaultYaw can be any angle,
		// e.g. 180 for a rear-facing mount) and could leave the part
		// facing some other, arbitrary direction within its own clamp
		// range instead.
		Entity trackingOccupant = this.tudursvehiclemod$resolveWeaponTrackingOccupant(seatIndex, pilotFallback);
		// Per a further direct request: for a vehicle with a toggleable
		// free-look mode (Aircraft/Submarine - see FreeCameraVehicle's own
		// doc), the PILOT specifically is only treated as actually
		// "aiming" while free-look is genuinely active. While NOT
		// free-looking, the pilot's own view directly drives the
		// vehicle's own steering instead (see followPilotView()/
		// followPilotViewGrounded()'s own doc) - so an occupant.getYaw()-
		// relative weapon tracking calculation would be reacting to
		// ordinary steering motion, not any independent aim, visibly
		// swinging the weapon around unintentionally. tudursvehiclemod$
		// isEffectiveFreeLook() already encodes exactly this distinction
		// (always true for a non-pilot seat, which never steers the
		// vehicle to begin with - only the PILOT's own case is
		// conditional on isFreeLook()). Vehicles with no free-look
		// concept at all (not a FreeCameraVehicle) are unaffected - this
		// only ever narrows things further for a vehicle that already
		// implements that interface.
		boolean effectivelyTracking = trackingOccupant != null
				&& !(this instanceof FreeCameraVehicle freeCameraVehicle
						&& !freeCameraVehicle.tudursvehiclemod$isEffectiveFreeLook(trackingOccupant));
		boolean manned = this.tudursvehiclemod$canFireWeapons(weapon) && effectivelyTracking;
		if (!manned) {
			// A dismounted pilot's own last-aimed
			// direction wasn't actually being held after all: this
			// EARLIER short-circuit (for every "not manned" reason, not
			// just a genuinely absent occupant) always snapped straight
			// to DefaultYaw/0 pitch here, before getWeaponAimYaw()/
			// getWeaponAimPitch() (which DO hold the last tracked
			// direction - see their own doc) ever even got called at
			// all. Specifically for trackingOccupant == null (a
			// genuinely dismounted/empty seat, as opposed to weapons
			// being disabled entirely, or a non-free-look pilot whose
			// view is driving steering instead) this now defers to
			// those same cached values instead, so the two are actually
			// consistent with each other.
			if (trackingOccupant == null) {
				int cacheKey = tudursvehiclemod$weaponTrackingCacheKey(seatIndex, pilotFallback);
				// A freshly-spawned vehicle's own
				// weapon parts were ignoring their own configured
				// DefaultYaw entirely: a seat that has NEVER actually been
				// tracked at all (no cache entry yet - as opposed to
				// having been tracked before and then vacated, which SHOULD
				// hold the last-aimed direction per this whole branch's own
				// doc) has nothing meaningful to "freeze" at all yet.
				// getWeaponAimYaw()'s own no-cache fallback is 0f - which
				// means "aim exactly along this vehicle's own forward
				// direction", NOT "DefaultYaw" - those are only the same
				// angle when DefaultYaw itself happens to be 0. Fed through
				// the SAME absolute-angle clamp used below regardless, that
				// 0f was getting clamped to whatever edge of
				// [DefaultYaw+MinYaw, DefaultYaw+MaxYaw] happened to be
				// closest to 0 - visibly NOT DefaultYaw at all for any
				// weapon whose own DefaultYaw isn't 0. Checked here via
				// containsKey() specifically (rather than changing
				// getWeaponAimYaw()'s own no-cache fallback itself, which
				// other callers - genuinely relative-yaw ones - still
				// legitimately want as 0) so a truly never-tracked seat
				// rests at DefaultYaw/0 pitch directly instead.
				boolean neverTracked = !this.lastTrackedRelativeYawBySeat.containsKey(cacheKey)
						&& !this.lastTrackedPitchBySeat.containsKey(cacheKey);
				if (neverTracked) {
					return tudursvehiclemod$computePartRotation(defaultYaw, (float) defaultYaw, 0f);
				}
				float frozenYaw = (float) defaultYaw;
				if (yawFollow) {
					float rawFrozenYaw = this.getWeaponAimYaw(seatIndex, pilotFallback, null, tickProgress);
					frozenYaw = aimRange != null
							? (float) tudursvehiclemod$clampYawToAimRange(rawFrozenYaw, aimRange)
							: rawFrozenYaw;
				}
				float frozenPitch = 0f;
				if (pitchFollow) {
					float rawFrozenPitch = this.getWeaponAimPitch(seatIndex, pilotFallback, null, tickProgress);
					frozenPitch = aimRange != null
							? (float) tudursvehiclemod$clampPitchToAimRange(rawFrozenPitch, aimRange)
							: rawFrozenPitch;
				}
				return tudursvehiclemod$computePartRotation(defaultYaw, frozenYaw, frozenPitch);
			}
			return tudursvehiclemod$computePartRotation(defaultYaw, (float) defaultYaw, 0f);
		}
		float rawYaw = this.getWeaponAimYaw(seatIndex, pilotFallback, null, tickProgress);
		float rawPitch = this.getWeaponAimPitch(seatIndex, pilotFallback, null, tickProgress);
		float absoluteYaw = (float) defaultYaw;
		if (yawFollow) {
			absoluteYaw = aimRange != null
					? (float) tudursvehiclemod$clampYawToAimRange(rawYaw, aimRange)
					: rawYaw;
		}
		float pitch = 0f;
		if (pitchFollow) {
			pitch = aimRange != null ? (float) tudursvehiclemod$clampPitchToAimRange(rawPitch, aimRange) : rawPitch;
		}
		return tudursvehiclemod$computePartRotation(defaultYaw, absoluteYaw, pitch);
	}

	/** Public convenience wrapper for tudursvehiclemod$getPartStageRotation() - this part's OWN stage rotation, using its own seatIndex/pilotFallback/aimRange/yawFollow/pitchFollow directly. */
	public Quaternionf tudursvehiclemod$getWeaponPartOwnRotation(com.example.tudursvehiclemod.asset.WeaponPart part, float tickProgress) {
		Float currentYaw = this.weaponPartCurrentYaw.get(part.part());
		Float currentPitch = this.weaponPartCurrentPitch.get(part.part());
		boolean yawNeedsFallback = part.yawFollow() && currentYaw == null;
		boolean pitchNeedsFallback = part.pitchFollow() && currentPitch == null;
		if (!yawNeedsFallback && !pitchNeedsFallback) {
			double defaultYaw = part.aimRange().map(com.example.tudursvehiclemod.asset.WeaponAimRange::defaultYaw).orElse(0.0);
			float finalYaw = currentYaw != null ? currentYaw : (float) defaultYaw;
			float finalPitch = currentPitch != null ? currentPitch : 0f;
			return tudursvehiclemod$computePartRotation(defaultYaw, finalYaw, finalPitch);
		}
		return tudursvehiclemod$getPartStageRotation(part.seatIndex(), part.pilotFallback(), part.aimRange().orElse(null),
				part.yawFollow(), part.pitchFollow(), tickProgress, tudursvehiclemod$resolveWeaponByName(part.weaponName()));
	}

	/** Public convenience wrapper for tudursvehiclemod$getPartStageRotation() - the PARENT's own stage rotation for a child part (identity if part isn't actually a child - see WeaponPart.ChildInfo's own doc). */
	public Quaternionf tudursvehiclemod$getWeaponPartParentRotation(com.example.tudursvehiclemod.asset.WeaponPart part, float tickProgress) {
		if (part.childInfo().isEmpty()) {
			return new Quaternionf();
		}
		com.example.tudursvehiclemod.asset.WeaponPart.ChildInfo childInfo = part.childInfo().get();
		Float currentYaw = this.weaponPartParentCurrentYaw.get(part.part());
		Float currentPitch = this.weaponPartParentCurrentPitch.get(part.part());
		boolean yawNeedsFallback = childInfo.parentYawFollow() && currentYaw == null;
		boolean pitchNeedsFallback = childInfo.parentPitchFollow() && currentPitch == null;
		if (!yawNeedsFallback && !pitchNeedsFallback) {
			double defaultYaw = part.aimRange().map(com.example.tudursvehiclemod.asset.WeaponAimRange::defaultYaw).orElse(0.0);
			float finalYaw = currentYaw != null ? currentYaw : (float) defaultYaw;
			float finalPitch = currentPitch != null ? currentPitch : 0f;
			return tudursvehiclemod$computePartRotation(defaultYaw, finalYaw, finalPitch);
		}
		return tudursvehiclemod$getPartStageRotation(part.seatIndex(), part.pilotFallback(), part.aimRange().orElse(null),
				childInfo.parentYawFollow(), childInfo.parentPitchFollow(), tickProgress,
				tudursvehiclemod$resolveWeaponByName(part.weaponName()));
	}

	/** OFFSET from DefaultYaw for rotating a weapon part's own already-oriented model, NOT the absolute angle. tryFireWeapon needs the absolute angle instead - see clampYawToAimRange(). */
	private static double tudursvehiclemod$clampedYawOffsetFromDefault(double rawYaw, com.example.tudursvehiclemod.asset.WeaponAimRange aimRange) {
		float relativeToDefault = net.minecraft.util.math.MathHelper.wrapDegrees((float) (rawYaw - aimRange.defaultYaw()));
		return net.minecraft.util.math.MathHelper.clamp(relativeToDefault, aimRange.minYaw(), aimRange.maxYaw());
	}

	/** Whether rawYaw (vehicle-relative, same convention getWeaponAimYaw's own "relative" already uses) and rawPitch (absolute, same convention getWeaponAimPitch already uses) both fall within this weapon's own configured aim range without needing to be clamped at all - i.e. the weapon's own turret could actually reach this exact direction, not just swing as far as it mechanically can toward it. Reuses this class's own existing clamp helpers directly (rather than a second implementation of the same math) so this can never silently disagree with what the weapon actually does when it fires. */
	public boolean tudursvehiclemod$isAimWithinRange(float rawYaw, float rawPitch, com.example.tudursvehiclemod.asset.WeaponAimRange aimRange) {
		double clampedYawOffset = tudursvehiclemod$clampedYawOffsetFromDefault(rawYaw, aimRange);
		double rawYawOffset = net.minecraft.util.math.MathHelper.wrapDegrees((float) (rawYaw - aimRange.defaultYaw()));
		double clampedPitch = tudursvehiclemod$clampPitchToAimRange(rawPitch, aimRange);
		return Math.abs(clampedYawOffset - rawYawOffset) < 0.01 && Math.abs(clampedPitch - rawPitch) < 0.01;
	}

	/** Absolute angle version, for a real firing direction vector (- a plain linear clamp breaks near DefaultYaw=±180°, since atan2 only returns [-180,180]). */
	private static double tudursvehiclemod$clampYawToAimRange(double rawYaw, com.example.tudursvehiclemod.asset.WeaponAimRange aimRange) {
		return aimRange.defaultYaw() + tudursvehiclemod$clampedYawOffsetFromDefault(rawYaw, aimRange);
	}

	/** Defensively normalizes min/max order first, in case a weapon's own data has them inverted. */
	private static double tudursvehiclemod$clampPitchToAimRange(double rawPitch, com.example.tudursvehiclemod.asset.WeaponAimRange aimRange) {
		double lower = Math.min(aimRange.minPitch(), aimRange.maxPitch());
		double upper = Math.max(aimRange.minPitch(), aimRange.maxPitch());
		return net.minecraft.util.math.MathHelper.clamp(rawPitch, lower, upper);
	}

	/** Applies the same rotation weaponPart visually undergoes to a static vehicle-local offset (e.g. an AddWeapon muzzle position), so the actual firing position follows the part's current rotation. */
	private Vec3d tudursvehiclemod$applyWeaponPartRotation(Vec3d offset, com.example.tudursvehiclemod.asset.WeaponPart part) {
		Vector3f vec = new Vector3f((float) offset.x, (float) offset.y, (float) offset.z);
		com.example.tudursvehiclemod.asset.WeaponAimRange aimRange = part.aimRange().orElse(null);
		java.util.Optional<com.example.tudursvehiclemod.asset.WeaponDefinition> weapon = tudursvehiclemod$resolveWeaponByName(part.weaponName());
		// Own stage first (see VehicleEntityRenderer's own two-stage transform doc for why: matrix-stack composition means the own/child stage is what actually applies to the geometry FIRST, in the original unrotated frame, with the parent stage applied second/outermost on top of that result).
		Quaternionf ownRotation = tudursvehiclemod$getPartStageRotation(
				part.seatIndex(), part.pilotFallback(), aimRange, part.yawFollow(), part.pitchFollow(), 1.0f, weapon);
		vec.sub((float) part.pivotX(), (float) part.pivotY(), (float) part.pivotZ());
		ownRotation.transform(vec);
		vec.add((float) part.pivotX(), (float) part.pivotY(), (float) part.pivotZ());
		if (part.childInfo().isPresent()) {
			com.example.tudursvehiclemod.asset.WeaponPart.ChildInfo childInfo = part.childInfo().get();
			Quaternionf parentRotation = tudursvehiclemod$getPartStageRotation(
					part.seatIndex(), part.pilotFallback(), aimRange, childInfo.parentYawFollow(), childInfo.parentPitchFollow(), 1.0f, weapon);
			vec.sub((float) childInfo.parentPivotX(), (float) childInfo.parentPivotY(), (float) childInfo.parentPivotZ());
			parentRotation.transform(vec);
			vec.add((float) childInfo.parentPivotX(), (float) childInfo.parentPivotY(), (float) childInfo.parentPivotZ());
		}
		return new Vec3d(vec.x, vec.y, vec.z);
	}

	/** seatIndex's own occupant if present; otherwise, if pilotFallback, the pilot's (seat 0) own occupant instead (still possibly null if the pilot seat is ALSO empty); otherwise null. */
	private Entity tudursvehiclemod$resolveWeaponTrackingOccupant(int seatIndex, boolean pilotFallback) {
		Entity occupant = this.tudursvehiclemod$getSeatOccupant(seatIndex);
		if (occupant != null) {
			return occupant;
		}
		return pilotFallback ? this.tudursvehiclemod$getSeatOccupant(0) : null;
	}

	/** Per-passenger seat assignments, keyed by passenger UUID rather than getPassengerList() position (- UUID-keyed as of the fix for cross-reload seat drift, superseding that doc's own original position-based rationale). Encoded as comma-separated "uuid:seatIndex" pairs. Synced via DataTracker, not a server-only Map, so every client can resolve seat occupancy/positions for itself without a separate sync channel. */
	private static final TrackedData<String> SEAT_ASSIGNMENTS =
			DataTracker.registerData(AbstractVehicleEntity.class, TrackedDataHandlerRegistry.STRING);

	/** Systematically excludes runway tiles (see CarrierRunwayPlatformEntity's own doc for why they ride this vehicle at all - purely for the same client-side, no-independent-network-sync positioning benefit real seat occupants get, nothing to do with the seat system itself) from every seat/passenger-related computation here: the actual passenger list, filtered down to genuine (non-tile) occupants only. Every seat-index/capacity/occupant-lookup method below uses this instead of the raw getPassengerList(), so tiles riding alongside real passengers never shift seat-index-by-position mappings, never count toward seat capacity, and never appear as a seat "occupant" to any of this vehicle's own seat-dependent systems. */
	public java.util.List<Entity> tudursvehiclemod$getRealPassengerList() {
		java.util.List<Entity> all = this.getPassengerList();
		boolean anyTile = false;
		for (Entity e : all) {
			if (e instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity) {
				anyTile = true;
				break;
			}
		}
		if (!anyTile) {
			return all;
		}
		java.util.List<Entity> filtered = new java.util.ArrayList<>(all.size());
		for (Entity e : all) {
			if (!(e instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity)) {
				filtered.add(e);
			}
		}
		return filtered;
	}

	/** The seat index actually assigned to this passenger right now, or -1 if they're not a recognized passenger. */
	public int tudursvehiclemod$getAssignedSeatIndex(Entity passenger) {
		// The remote-controlling player
		// (see remoteControllerId's own doc) is never actually a
		// passenger of this vehicle at all (never in getPassengerList()
		// at all), but every seat-dependent system here (camera eye
		// position, weapon aim tracking,..) still needs to resolve
		// them to a real seat, matching "operates exactly like actually
		// being aboard and piloting it" - resolved to seat 0 (the pilot
		// seat) specifically, since a UAV vehicle is only ever remote-
		// controlled as a whole, not through any one particular gunner
		// seat.
		if (passenger.getUuid().equals(this.remoteControllerId)) {
			return 0;
		}
		if (this.tudursvehiclemod$getRealPassengerList().indexOf(passenger) < 0) {
			return -1;
		}
		Integer seatIndex = tudursvehiclemod$decodeSeatAssignments().get(passenger.getUuid());
		return seatIndex != null ? seatIndex : -1;
	}

	/** Whoever is currently assigned to seatIndex, or null if that seat is empty (or seatIndex itself is invalid). */
	public Entity tudursvehiclemod$getSeatOccupant(int seatIndex) {
		if (seatIndex < 0) {
			return null;
		}
		// A dummy pilot always and only represents seat 0 - checked directly, bypassing SEAT_ASSIGNMENTS (unreliable for this case -).
		if (seatIndex == 0) {
			for (Entity passenger : this.tudursvehiclemod$getRealPassengerList()) {
				if (passenger instanceof com.example.tudursvehiclemod.entity.DummyPilotEntity dummyPilot) {
					return dummyPilot;
				}
			}
		}
		for (Entity passenger : this.tudursvehiclemod$getRealPassengerList()) {
			if (this.tudursvehiclemod$getAssignedSeatIndex(passenger) == seatIndex) {
				return passenger;
			}
		}
		// Per tudursvehiclemod$getAssignedSeatIndex()'s own doc: the
		// remote-controlling player (see remoteControllerId's own doc)
		// is never actually in getPassengerList() at all, so the search
		// above alone would never find them - weapon aiming/tracking and
		// every other seat-occupant-dependent system here still needs
		// to treat them as seat 0's own occupant, matching "operates
		// exactly like actually being aboard and piloting it".
		if (seatIndex == 0 && this.remoteControllerId != null
				&& this.getEntityWorld().getPlayerByUuid(this.remoteControllerId) instanceof Entity remoteController) {
			return remoteController;
		}
		return null;
	}

	private java.util.Map<java.util.UUID, Integer> tudursvehiclemod$decodeSeatAssignments() {
		java.util.Map<java.util.UUID, Integer> result = new java.util.LinkedHashMap<>();
		String encoded = this.dataTracker.get(SEAT_ASSIGNMENTS);
		if (encoded.isEmpty()) {
			return result;
		}
		for (String entry : encoded.split(",")) {
			int colonIndex = entry.indexOf(':');
			if (colonIndex < 0) {
				continue;
			}
			try {
				java.util.UUID uuid = java.util.UUID.fromString(entry.substring(0, colonIndex).trim());
				int seatIndex = Integer.parseInt(entry.substring(colonIndex + 1).trim());
				result.put(uuid, seatIndex);
			} catch (IllegalArgumentException ignored) {
				// Skip a malformed entry rather than letting it corrupt the whole decode.
			}
		}
		return result;
	}

	private static String tudursvehiclemod$encodeSeatAssignments(java.util.Map<java.util.UUID, Integer> assignments) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (java.util.Map.Entry<java.util.UUID, Integer> entry : assignments.entrySet()) {
			if (!first) {
				sb.append(',');
			}
			sb.append(entry.getKey()).append(':').append(entry.getValue());
			first = false;
		}
		return sb.toString();
	}

	/** Inconsistent seat-occupancy behavior (a pilot able to switch into an already-mob-occupied seat; an already-mounted mob appearing to overlap a different seat's own position) that this project's own code review couldn't pin down through static reading of tudursvehiclemod$trySwitchSeat()/tudursvehiclemod$getSeatOccupant() alone, both of which look correct in isolation: the most plausible remaining explanation is that SEAT_ASSIGNMENTS data on an already-tested vehicle got corrupted by one of the several seat-related bugs already fixed earlier in this same session (over-capacity mounting, missing seatIndex bounds checks in tudursvehiclemod$mountToSeat(), etc.) before those fixes were actually in place - and that corrupted data (an out-of-range seat index, or two DIFFERENT passengers somehow sharing the SAME seat index) persisted across saves even after the CODE itself was fixed, since nothing ever re-validates already-saved SEAT_ASSIGNMENTS data on its own. Called once per tick (see tick() below) - cheap, and only actually rewrites SEAT_ASSIGNMENTS at all if a real inconsistency was found and fixed. An out-of-range index gets reassigned to the first genuinely free valid index; if two positions ever share the same seat index, only the first (by position) keeps it, and any later duplicate also gets reassigned to the first free index - self-healing automatically, without needing a manual reset of this vehicle's own saved data. */
	private void tudursvehiclemod$repairSeatAssignmentsIfNeeded() {
		java.util.List<Entity> passengers = this.tudursvehiclemod$getRealPassengerList();
		if (passengers.isEmpty()) {
			return;
		}
		int seatCount = this.getDefinition().seats().size();
		java.util.Map<java.util.UUID, Integer> assignments = tudursvehiclemod$decodeSeatAssignments();
		boolean[] seatTaken = new boolean[seatCount];
		boolean changed = false;
		for (Entity passenger : passengers) {
			Integer seatIndex = assignments.get(passenger.getUuid());
			if (seatIndex != null && seatIndex >= 0 && seatIndex < seatCount && !seatTaken[seatIndex]) {
				seatTaken[seatIndex] = true;
				continue;
			}
			int replacement = -1;
			for (int candidate = 0; candidate < seatCount; candidate++) {
				if (!seatTaken[candidate]) {
					replacement = candidate;
					break;
				}
			}
			assignments.put(passenger.getUuid(), replacement);
			if (replacement >= 0) {
				seatTaken[replacement] = true;
			}
			changed = true;
		}
		// Drops any entry whose UUID no longer belongs to a current passenger, so this doesn't grow forever across many mount/dismount cycles.
		int sizeBeforePrune = assignments.size();
		assignments.keySet().removeIf(uuid -> passengers.stream().noneMatch(p -> p.getUuid().equals(uuid)));
		if (assignments.size() != sizeBeforePrune) {
			changed = true;
		}
		if (changed) {
			this.dataTracker.set(SEAT_ASSIGNMENTS, tudursvehiclemod$encodeSeatAssignments(assignments));
		}
	}

	/** Assigns seatIndex to passenger - MUST be called AFTER passenger has already mounted (startRiding()/already in getPassengerList()), since this locates them by their current list position. */
	private void tudursvehiclemod$setAssignedSeat(Entity passenger, int seatIndex) {
		if (!this.tudursvehiclemod$getRealPassengerList().contains(passenger)) {
			return;
		}
		java.util.Map<java.util.UUID, Integer> assignments = tudursvehiclemod$decodeSeatAssignments();
		assignments.put(passenger.getUuid(), seatIndex);
		this.dataTracker.set(SEAT_ASSIGNMENTS, tudursvehiclemod$encodeSeatAssignments(assignments));
	}

	/** Public combination of startRiding() + tudursvehiclemod$setAssignedSeat() - for external callers (currently only the Carrier seat-switch logic, see tudursvehiclemod$toggleCarrierSeat()'s own doc) that need to mount a passenger into a SPECIFIC seat index directly, rather than the "first available seat" tudursvehiclemod$assignFirstAvailableSeat() picks. Does nothing (silently) if seatIndex is already occupied by someone else, seatIndex itself is outside this vehicle's own actual defined seat range, or this vehicle's own total passenger count is already at its own total seat count - callers should check tudursvehiclemod$getSeatOccupant() first if that matters to them. Mobs could mount beyond this vehicle's own actual seat count once canAddPassenger() (see that method's own doc) no longer capped total passengers at all: this method itself never validated seatIndex range or overall capacity before, relying entirely on vanilla's own (now-removed) single-passenger restriction as an unintentional safety net - both are validated directly here now, centralizing the real capacity constraint in this one shared mounting method rather than trusting every individual caller to separately get it right. Returns whether the mount actually succeeded (false for any of the above reasons, OR if startRiding() itself failed for any other reason, e.g. vanilla's own dimension/distance checks) - callers should NOT proceed with follow-up state changes that assume a passenger is now actually present when this returns false. Uses startRiding(this, true, true) (force=true) rather than the plain 1-arg overload - vanilla's Entity.removePassenger() unconditionally sets ridingCooldown=60 on EVERY dismount (a general anti-abuse cooldown never set by this project itself), and canStartRiding()'s own default implementation refuses ANY mount for a full 3 seconds afterward - which the plain overload's own canStartRiding()/canAddPassenger() checks would silently hit for a passenger dismounted moments earlier (exactly this method's own most common use case: eject seat, mob drop, parachute jump, auto-seating). force=true skips both checks entirely, which is fine here since this method's own capacity/seat-index/occupancy checks just above already enforce everything canAddPassenger() would have anyway - this also means a sneaking or just-dismounted passenger can now always be force-mounted through this one shared method (every caller, including ordinary player boarding via tudursvehiclemod$assignFirstAvailableSeat()), which is the intended, more permissive behavior rather than a side effect to guard against. */
	public boolean tudursvehiclemod$mountToSeat(Entity passenger, int seatIndex) {
		int seatCount = this.getDefinition().seats().size();
		if (seatIndex < 0 || seatIndex >= seatCount) {
			return false;
		}
		if (this.tudursvehiclemod$getRealPassengerList().size() >= seatCount) {
			return false;
		}
		if (this.tudursvehiclemod$getSeatOccupant(seatIndex) != null) {
			return false;
		}
		if (!passenger.startRiding(this, true, true)) {
			return false;
		}
		this.tudursvehiclemod$setAssignedSeat(passenger, seatIndex);
		return true;
	}

	/** Called right when a passenger actually mounts (see interact()'s/interactAt()'s own docs) - finds the lowest-indexed unoccupied seat and mounts them into it via tudursvehiclemod$mountToSeat() (see that method's own doc for the capacity/occupancy checks this now inherits automatically). Does nothing at all if every seat is already occupied - this method's own caller already guards against that case in practice, but no longer relies solely on that external guard being correct. */
	private void tudursvehiclemod$assignFirstAvailableSeat(Entity passenger) {
		int seatCount = Math.max(1, this.getDefinition().seats().size());
		for (int i = 0; i < seatCount; i++) {
			if (this.tudursvehiclemod$getSeatOccupant(i) == null) {
				this.tudursvehiclemod$mountToSeat(passenger, i);
				return;
			}
		}
	}

	/** A player could collide with (and take flyIntoWall() crash damage from) the vehicle they just dismounted mid-flight from - see tudursvehiclemod$isRecentlyDismounted()'s own doc for the full mechanism. Keyed by passenger UUID, value is the this.age tick the grace period expires at. */
	private final java.util.Map<java.util.UUID, Integer> recentlyDismountedExpiryTick = new java.util.HashMap<>();
	private static final int RECENTLY_DISMOUNTED_GRACE_TICKS = 20;

	/** How far above this vehicle's own current Y a passenger dismounts, when their own seat has no
	 * dismount_y override - see removePassenger()'s own seat-position computation. */
	private static final double DISMOUNT_HEIGHT_ABOVE_VEHICLE = 1.0;

	/** Tracks the FULL ordered roster of every aircraft launched by a Carrier weapon fired from a given seat index on THIS vehicle (the "mothership"), in launch order (index 0 = the formation's own lead) - set by tudursvehiclemod$fireCarrierLaunch()/tudursvehiclemod$updateCarrierLaunchSequences() at launch time, read by tudursvehiclemod$toggleCarrierSeat() (via tudursvehiclemod$findCurrentCarrierLeader(), which walks this list in order and returns the first entry that's still alive) to find which aircraft to switch a mothership-seated player INTO. This means a destroyed or otherwise-gone LEADER automatically falls through to the next aircraft in launch order, rather than seat-switching simply going inert once the SPECIFIC aircraft it used to point at is gone. Persisted (see tudursvehiclemod$encodeCarrierLinkedAircraftBySeat()/-readCarrierLinkedAircraftBySeat()) so the roster survives a world/chunk reload - a launched aircraft that's already gone (route completed, timed out, destroyed) is simply skipped over when walking the list, never removed from it outright (removing entries would require its own separate cleanup pass for no real benefit, since a gone UUID is already cheap to skip).
	 */
	private final java.util.Map<Integer, java.util.List<java.util.UUID>> carrierLinkedAircraftBySeat = new java.util.HashMap<>();
	/** Bundles the interior tile list/missing-ticks map and two one-shot logging flags into one holder, so a runway's own tracking state stays entirely self-contained rather than needing parallel arrays threaded through every runway-related method. See runwayTileStates' own doc for how one of these is obtained per runway. */
	private static final class RunwayTileState {
		final java.util.List<java.util.UUID> interior = new java.util.ArrayList<>();
		final java.util.Map<Integer, Integer> interiorMissingTicks = new java.util.HashMap<>();
		boolean loggedFound;
		boolean loggedPlatformTiles;
	}

	/** Per RunwayTileState's own doc: one entry per this vehicle's own def.runways() index, created lazily (see tudursvehiclemod$getOrCreateRunwayTileState()) the first time each runway is actually processed, rather than all at once - a vehicle whose runway list is still being populated during loading, or briefly inconsistent for any other reason, never needs every index to already exist up front. */
	private final java.util.List<RunwayTileState> runwayTileStates = new java.util.ArrayList<>();

	/** Grows runwayTileStates on demand so index `runwayIndex` is always valid to read/write afterwards - see that field's own doc for why lazy rather than pre-sized. */
	private RunwayTileState tudursvehiclemod$getOrCreateRunwayTileState(int runwayIndex) {
		while (this.runwayTileStates.size() <= runwayIndex) {
			this.runwayTileStates.add(new RunwayTileState());
		}
		return this.runwayTileStates.get(runwayIndex);
	}

	/** Purely from the mothership's own already-synced/interpolated position/yaw (see CarrierRunwayPlatformEntity's own client-side tick() for the full reasoning) - a standalone reimplementation of the exact same grid-layout formula tudursvehiclemod$updateCarrierRunwayPlatform()'s own positionFunc lambda already uses for the server's own authoritative placement, kept here as a separate, self-contained static method (rather than refactoring the server's own already-verified hot path to call it) specifically to avoid risking any behavioral change to that already-tested code. If this project's own tile-layout formula is ever changed, BOTH copies need updating together. Returns null if tileIndex is out of range for the tile count this runway/tileSize combination actually produces (columns*rows) - e.g. a stale tile index left over from before a runway's own configured size last changed. */
	public static net.minecraft.util.math.Vec3d tudursvehiclemod$computeCarrierRunwayTileWorldPosClientSide(
			AbstractVehicleEntity vehicle, com.example.tudursvehiclemod.asset.RunwayDefinition runway, float tileSize, int tileIndex) {
		double minZ = Math.min(runway.startZ(), runway.endZ());
		double maxZ = Math.max(runway.startZ(), runway.endZ());
		double length = maxZ - minZ;
		double halfWidth = runway.width() / 2.0;
		double spacing = tileSize * 0.7;
		int columns = Math.max(1, (int) Math.ceil(runway.width() / spacing));
		int rows = Math.max(1, (int) Math.ceil(length / spacing));
		if (tileIndex < 0 || tileIndex >= columns * rows) {
			return null;
		}
		int col = tileIndex % columns;
		int row = tileIndex / columns;
		double tileLocalX = columns == 1 ? runway.centerX() : runway.centerX() - halfWidth + tileSize / 2.0 + col * ((runway.width() - tileSize) / Math.max(1, columns - 1));
		double tileLocalZ = rows == 1 ? (minZ + maxZ) / 2.0 : minZ + tileSize / 2.0 + row * ((length - tileSize) / Math.max(1, rows - 1));
		double combinedYawRad = Math.toRadians(vehicle.getYaw());
		double forwardX = -Math.sin(combinedYawRad);
		double forwardZ = Math.cos(combinedYawRad);
		double rightX = forwardZ;
		double rightZ = -forwardX;
		double surfaceWorldY = vehicle.getY() + runway.heightY();
		double worldX = vehicle.getX() + tileLocalX * rightX + tileLocalZ * forwardX;
		double worldZ = vehicle.getZ() + tileLocalX * rightZ + tileLocalZ * forwardZ;
		return new net.minecraft.util.math.Vec3d(worldX, surfaceWorldY, worldZ);
	}

	/** Runway tile collision became unstable specifically in a real (Overworld) world under load, but not in Superflat - and that the mothership's own runway itself worked fine before the landing/recovery-related changes, implicating something newer (this self-heal feature, added since): tileIndex -> how many CONSECUTIVE ticks that tile has been confirmed missing (its own expected chunk loaded, but the entity itself absent) - see tudursvehiclemod$updateCarrierRunwayPlatform()'s own self-heal doc for why a single confirmed-missing tick alone isn't trusted; a chunk that JUST finished loading may have its own blocks ready before its own entity-tracking has fully caught up, which could otherwise read as a false-positive "genuinely gone" on that first tick even though the original tile is still actually there and about to reappear. */
	private static final int CARRIER_RUNWAY_MISSING_DEBOUNCE_TICKS = 40;
	/** Jittery/jerky carried-entity motion - see tudursvehiclemod$carryRunwayDeckEntities()'s own doc for the full reasoning: the maximum magnitude (blocks) the acceleration-compensation term there can contribute in any single axis, regardless of how large a single-tick delta anomaly (e.g. the Y-coordinate settle-and-snap) happens to be. 0.1 blocks/tick^2 comfortably covers a genuinely accelerating ship's own typical acceleration while suppressing amplification of a much smaller (typically ~0.01 block) but still-discontinuous single-tick anomaly. */
	private static final double CARRIER_ACCEL_COMPENSATION_MAX_BLOCKS = 0.1;
	/** Runway tiles were unstable specifically right after a fresh mothership spawn - see the initial-spawn loop's own doc in tudursvehiclemod$updateCarrierRunwayPlatform() for the full reasoning: how many new tiles are spawned per tick during that initial burst, instead of the entire remaining backlog at once. */
	private static final int CARRIER_RUNWAY_TILES_SPAWNED_PER_TICK = 4;
	/** Motionless, in mid-air after this vehicle (and its own runway) was removed while still actively carrying it: which candidates this vehicle carried on its own most recent tick - consulted in onRemoved() to explicitly release each one (see tudursvehiclemod$releaseCarriedCandidate()'s own doc) rather than leaving them in whatever forced state (hasLifted=false, etc.) they were left in, with no guarantee their own physics would ever naturally re-evaluate that state correctly once this vehicle itself is simply gone. Replaced wholesale each tick (see tudursvehiclemod$carryRunwayDeckEntities()'s own doc), not incrementally updated.
	 */
	private java.util.Set<java.util.UUID> carrierLastCarriedCandidates = java.util.Set.of();
	/** Per tudursvehiclemod$carryRunwayDeckEntities()'s own doc: every currently-loaded vehicle that has a runway configured, self-registered (see this class's own tick()) and self-unregistered (see onRemoved()) - consulted by a global END_WORLD_TICK callback (VehicleMod's own onInitialize()) to actually invoke carrying on each, after every entity in the world has already finished its own tick this cycle. */
	public static final java.util.Set<AbstractVehicleEntity> CARRIER_ACTIVE_MOTHERSHIPS = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** 10 km/h = 2.7778 m/s = 0.13889 blocks/tick (1 block ~= 1m, 20 ticks/s) - squared here since it's compared against Vec3d.lengthSquared() (avoids an actual sqrt every check). Used by tudursvehiclemod$updateCarrierLandingToAmmo()'s own in-flight-exclusion check, and kept comfortably above AircraftEntity's own CARRIER_LANDING_FINAL_MIN_SPEED (its own glide-mode speed floor) so a released, decelerating aircraft reliably ends up BELOW this threshold once it's actually coasted down to that floor. */
	private static final double CARRIER_ESSENTIALLY_LANDED_SPEED_SQ = 0.019290;
	/** Entities standing on the runway (not seated as an actual passenger) move together with this vehicle as it moves/turns: this vehicle's own position/yaw as of the END of the previous tick, compared against the current tick's own END position/yaw to compute exactly how far/how much this vehicle itself moved/turned - see tudursvehiclemod$carryRunwayDeckEntities()'s own doc for how that delta is actually applied (via velocity, not a direct position override - see that method's own doc for why). NaN sentinel marks the very first tick, when there's no meaningful previous position to compare against yet. */
	private double carrierPrevTickX = Double.NaN;
	private double carrierPrevTickY;
	private double carrierPrevTickZ;
	private float carrierPrevTickYaw;
	/** Carried entities drifted specifically DURING acceleration - this vehicle's own delta (movement this tick) FROM THE PREVIOUS invocation of tudursvehiclemod$carryRunwayDeckEntities(), needed to estimate acceleration (this tick's own delta minus this) and predict roughly one tick ahead - see that method's own doc for why: the velocity this method sets doesn't actually get consumed until the candidate's OWN next physics tick, by which point this vehicle (if accelerating) has already moved further than what a target based on ITS CURRENT position alone would account for, causing a systematic, acceleration-proportional undershoot. NaN sentinel marks that no previous delta is available yet (first couple of ticks). */
	private double carrierPrevDeltaX = Double.NaN;
	private double carrierPrevDeltaY;
	private double carrierPrevDeltaZ;

	/** Force-loads a 3x3 grid of chunks around this mothership's own current position for as long as it has at least one Carrier-launched aircraft still active (a linked entry in carrierLinkedAircraftBySeat whose own aircraft is still alive) - same grid-based approach already used for CAS/Carrier aircraft themselves (see entity.AircraftEntity's own casForcedChunks doc), for the exact same "single-chunk boundary/timing issue" robustness reasons. Released the instant no more active links remain. */
	private java.util.Set<net.minecraft.util.math.ChunkPos> carrierMothershipForcedChunks = java.util.Set.of();

	/** True while `entity` is still within RECENTLY_DISMOUNTED_GRACE_TICKS of having dismounted THIS specific vehicle - see recentlyDismountedExpiryTick's own doc for why this exists. Callers (currently only AircraftEntity/VtolEntity's own tudursvehiclemod$checkEntityCrashDamage()) should exclude a recently-dismounted entity from crash-damage candidacy the same way an actual current passenger already is. */
	protected boolean tudursvehiclemod$isRecentlyDismounted(Entity entity) {
		Integer expiry = this.recentlyDismountedExpiryTick.get(entity.getUuid());
		if (expiry == null) {
			return false;
		}
		if (this.age >= expiry) {
			this.recentlyDismountedExpiryTick.remove(entity.getUuid());
			return false;
		}
		return true;
	}

	/** Auto-seating mobs always failed to actually mount (despite correctly finding empty seats and eligible mobs) once the vehicle already had ANY other passenger: this class extends Entity directly, so vanilla's own default canAddPassenger(Entity) - "this.getPassengerList().isEmpty()", i.e. only ONE passenger total, ever - was still silently rejecting every mount attempt past the first. This project's own tudursvehiclemod$mountToSeat()/tudursvehiclemod$getSeatOccupant() already fully enforce the REAL constraint (a given seat index can't be double-occupied) before ever calling startRiding() at all, making vanilla's own single-passenger-total restriction entirely redundant - and actively harmful for a multi-seat vehicle design like this one's own. */
	@Override
	public boolean canAddPassenger(Entity passenger) {
		return true;
	}

	/** This class extends Entity directly, so vanilla's own default handleFallDamage() - which unconditionally forwards THIS VEHICLE'S OWN accumulated fallDistance to every passenger the instant it lands - was still active. A player-piloted vehicle rarely accumulates much fallDistance (the pilot controls descent), so this went unnoticed; an unmanned vehicle genuinely falling (or descending uncontrolled) accumulates a large fallDistance that gets forwarded in full on touchdown. Lives in this shared base class, so it applies identically to every vehicle type here (Helicopter, VTOL, etc.), not just one - ordinary landing was never meant to hurt whoever's aboard; this vehicle's own separate durability/damage system remains the intended way it takes damage itself. */
	@Override
	public boolean handleFallDamage(double fallDistance, float damagePerDistance, DamageSource damageSource) {
		return false;
	}

	/** Sends packets directly to just this one player's own network handler (not sendToNearbyPlayers(), which would reach every already-connected player regardless of whether they need this at all) - see each action's own doc below for why it's needed. Called from ModNetworking's own RequestSeatResyncPayload receiver, in response to a specific player's client explicitly asking for this (see onSpawnPacket()'s own override just below for where that request comes from). Silently does nothing if this vehicle has no real passengers at all. */
	public void tudursvehiclemod$resyncSeatsForNewlyJoinedPlayer(net.minecraft.server.network.ServerPlayerEntity player) {
		if (this.getEntityWorld().isClient()) {
			return;
		}
		java.util.List<Entity> resyncPassengers = this.tudursvehiclemod$getRealPassengerList();
		if (resyncPassengers.isEmpty()) {
			return;
		}
		this.dataTracker.set(SEAT_ASSIGNMENTS, this.dataTracker.get(SEAT_ASSIGNMENTS), true);
		for (Entity resyncPassenger : resyncPassengers) {
			if (resyncPassenger instanceof net.minecraft.entity.mob.MobEntity) {
				net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket resyncSpawnPacket =
						new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(
								resyncPassenger.getId(), resyncPassenger.getUuid(),
								resyncPassenger.getX(), resyncPassenger.getY(), resyncPassenger.getZ(),
								resyncPassenger.getPitch(), resyncPassenger.getYaw(),
								resyncPassenger.getType(), 0, resyncPassenger.getVelocity(), resyncPassenger.getHeadYaw());
				player.networkHandler.sendPacket(resyncSpawnPacket);
			}
		}
		player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket(this));
	}

	/** Overrides Entity's own onSpawnPacket() - confirmed from its own decompiled source to be the genuine client-side callback that fires exactly once per actual client-side spawn of this entity, covering an ordinary chunk reload identically to a fresh world join (both trigger a fresh spawn packet, so both trigger this the same way). Routes through tudursvehiclemod$clientRequestSeatResyncCallback (see that field's own doc for why this indirection exists) rather than calling ClientPlayNetworking directly from this common class. This is the precise timing the request asked for: no guessing at server-side readiness, no periodic polling, no per-tick cost at all - exactly one small request, exactly when the client itself knows it needs one. */
	@Override
	public void onSpawnPacket(net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket packet) {
		super.onSpawnPacket(packet);
		if (this.getEntityWorld().isClient() && tudursvehiclemod$clientRequestSeatResyncCallback != null) {
			tudursvehiclemod$clientRequestSeatResyncCallback.accept(this.getId());
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		if (passenger instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity) {
			super.removePassenger(passenger);
			return;
		}
		// These synced input fields are only ever written by an incoming network packet FROM the controlling player - nothing ever reset them back to neutral once that player actually dismounts, so whatever they were last holding (e.g. still pressing A/D at the exact moment of dismounting) could persist as a stale, nonzero value indefinitely into the unmanned state. Checked BEFORE super.removePassenger() actually performs the removal, since getControllingPassenger() needs passenger to still be in the list to correctly identify them as the (about to be former) pilot. Excludes a DummyPilotEntity specifically - see this method's own doc for why zeroing here on its removal is actively wrong rather than merely unneeded.
		if (passenger.equals(this.getControllingPassenger()) && !(passenger instanceof com.example.tudursvehiclemod.entity.DummyPilotEntity)) {
			this.setSyncedSidewaysInput(0f);
			this.syncedThrottleInput = 0f;
			this.setSyncedBrakeInput(false);
		}
		// Records a short grace period (see recentlyDismountedExpiryTick's own doc) during which THIS vehicle's own crash-damage check won't treat them as a collision candidate, and gives them this vehicle's own current velocity (matching e.g. a dropped Bomb inheriting the firing vehicle's own momentum) so they naturally separate from it instead of staying stationary while a fast-moving vehicle continues through their old position. Only meaningful for an actual player/living entity, not e.g. a remote-control station teleport target.
		if (!this.getEntityWorld().isClient() && passenger instanceof LivingEntity) {
			this.recentlyDismountedExpiryTick.put(passenger.getUuid(), this.age + RECENTLY_DISMOUNTED_GRACE_TICKS);
			passenger.setVelocity(this.getVelocity());
			passenger.velocityDirty = true;
		}
		// BEFORE calling super (which is what actually performs the removal) so getPassengerList() still includes `passenger` here, letting this correctly locate + drop just their own entry rather than everyone's.
		int position = this.tudursvehiclemod$getRealPassengerList().indexOf(passenger);

		// Dismounting should place the passenger at
		// their own SEAT's actual physical position - NOT wherever their
		// own view/camera happened to be (e.g. a gunner seat's own
		// separate CameraPosition/AddGunnerSeat camera offset, which can
		// be some distance from the seat itself). Computed here (BEFORE
		// super.removePassenger()) since tudursvehiclemod$getAssignedSeatIndex
		// needs the passenger to still be in getPassengerList() to work at
		// All - but actually REPOSITIONING them here
		// (before super.removePassenger()) had no visible effect, since
		// vanilla's own dismount-position logic runs INSIDE
		// super.removePassenger() itself and recomputes/overwrites the
		// position regardless of wherever the passenger already was -
		// so the actual repositioning now happens AFTER super.removePassenger()
		// instead, overriding whatever vanilla just computed.
		int seatIndex = this.tudursvehiclemod$getAssignedSeatIndex(passenger);
		VehicleDefinition def = this.getDefinition();
		Vec3d seatWorldPos = null;
		// If this vehicle's own remote
		// controller (see remoteControllerId's own doc) is the one
		// actually dismounting right now - for ANY reason (the station's
		// own "end control" interaction calling tudursvehiclemod$tryExitRemoteControl()
		// directly, this vehicle being destroyed, or even an unexpected
		// vanilla-triggered dismount) - they're redirected back to their
		// own station's position instead of this vehicle's own seat
		// position, and this vehicle's own remote-control state is
		// cleaned up right here (NOT via a recursive tudursvehiclemod$tryExitRemoteControl()
		// call, which would call stopRiding() again while already in the
		// middle of processing one dismount).
		boolean wasRemoteController = passenger.getUuid().equals(this.remoteControllerId);
		if (wasRemoteController && !this.getEntityWorld().isClient()) {
			net.minecraft.util.math.BlockPos station = this.remoteControlStationPos;
			if (station != null) {
				seatWorldPos = new Vec3d(station.getX() + 0.5, station.getY() + 1.0, station.getZ() + 0.5);
				// A distant vehicle could never
				// actually be found/controlled again after flying off
				// and being left there - see block.StationBlockEntity's
				// own tudursvehiclemod$updateLastKnownVehicleChunk() doc
				// for why this specific call, right here, is the fix:
				// records this vehicle's own ACTUAL final resting
				// position (right now, before it's potentially left
				// unattended for a long time) as that station's own
				// remembered chunk to try loading next time, rather than
				// leaving it stuck at wherever this vehicle merely
				// happened to be the last time control STARTED.
				if (this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld dismountWorld
						&& dismountWorld.getBlockEntity(station) instanceof com.example.tudursvehiclemod.block.StationBlockEntity stationEntity) {
					stationEntity.tudursvehiclemod$updateLastKnownVehicleChunk(new net.minecraft.util.math.ChunkPos(this.getBlockPos()));
				}
			}
			ACTIVE_REMOTE_CONTROL.remove(this.remoteControllerId, this.getId());
			this.dataTracker.set(REMOTE_CONTROLLER_UUID, "");
			if (passenger instanceof net.minecraft.server.network.ServerPlayerEntity remotePlayer) {
				remotePlayer.setInvulnerable(this.remoteControllerWasInvulnerable);
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(remotePlayer, new com.example.tudursvehiclemod.network.RemoteControlEndPayload());
			}
			this.remoteControllerId = null;
			this.remoteControlStationPos = null;
		} else if (!this.getEntityWorld().isClient() && seatIndex >= 0 && seatIndex < def.seats().size()) {
			SeatDefinition seat = def.seats().get(seatIndex);
			// X/Z: this seat's own dismount_x/dismount_z override if set, else its ordinary offset_x/offset_z - unchanged from before this feature existed.
			double localX = seat.dismountOffsetX().orElse(seat.offsetX());
			double localZ = seat.dismountOffsetZ().orElse(seat.offsetZ());
			Vector3f local = new Vector3f(
					(float) (localX * def.scale()),
					(float) (seat.offsetY() * def.scale()),
					(float) (localZ * def.scale()));
			this.getSeatRotationCurrent().transform(local);
			double worldY;
			if (seat.dismountOffsetY().isPresent()) {
				// An explicit override always wins, rotated the same way offset_y itself would be.
				Vector3f yOnly = new Vector3f(0f, (float) (seat.dismountOffsetY().get() * def.scale()), 0f);
				this.getSeatRotationCurrent().transform(yOnly);
				worldY = this.getY() + yOnly.y;
			} else {
				// Dismounts at a fixed height above this vehicle's own current Y, regardless of this
				// seat's own offset_y - a low seat no longer lands the passenger embedded in the
				// vehicle/ground below, and a high seat no longer drops them from a needless fall.
				// Deliberately NOT rotated by this vehicle's own current attitude (unlike offset_y
				// itself) - a plain vertical addition in world space, so a banked/rolled vehicle
				// doesn't push the dismount point sideways. Applied unconditionally, on the ground
				// or in the air: this is relative to the vehicle's own position either way, so a
				// vehicle flying at Y=100 simply dismounts at Y=101 - no different in kind from a
				// mid-flight ejection already placing the passenger at the vehicle's own altitude.
				worldY = this.getY() + DISMOUNT_HEIGHT_ABOVE_VEHICLE;
			}
			seatWorldPos = new Vec3d(this.getX() + local.x, worldY, this.getZ() + local.z);
		}

		super.removePassenger(passenger);

		if (seatWorldPos != null) {
			// Even an immediate requestTeleport()
			// call here still got overwritten again - strongly suggesting
			// vanilla's own dismount-related positioning logic runs
			// somewhere AFTER removePassenger() itself returns (e.g. back
			// up in stopRiding()'s own caller chain, or a subsequent
			// packet-handling step later in the SAME tick) - re-applying
			// on top of whatever correction was attempted here. Deferring
			// to this vehicle's own NEXT tick() call (see the pending
			// fields' own doc, and tick()'s own handling of them) instead
			// guarantees this correction is applied strictly after any and
			// all same-tick vanilla positioning work has already finished,
			// rather than racing against it.
			this.pendingDismountPassenger = passenger;
			this.pendingDismountPosition = seatWorldPos;
		}

		if (position < 0) {
			return;
		}
		java.util.Map<java.util.UUID, Integer> remainingAssignments = tudursvehiclemod$decodeSeatAssignments();
		if (remainingAssignments.remove(passenger.getUuid()) != null) {
			this.dataTracker.set(SEAT_ASSIGNMENTS, tudursvehiclemod$encodeSeatAssignments(remainingAssignments));
		}
	}

	/** Moves passenger forward/backward to the next available seat, wrapping around and skipping occupied ones. No-op if only one seat, unrecognized passenger, or all others occupied. */
	public void tudursvehiclemod$trySwitchSeat(Entity passenger, boolean forward) {
		int seatCount = Math.max(1, this.getDefinition().seats().size());
		if (seatCount <= 1) {
			return;
		}
		int current = this.tudursvehiclemod$getAssignedSeatIndex(passenger);
		if (current < 0) {
			return;
		}
		for (int offset = 1; offset < seatCount; offset++) {
			int step = forward ? offset : -offset;
			int candidate = ((current + step) % seatCount + seatCount) % seatCount;
			if (this.tudursvehiclemod$getSeatOccupant(candidate) == null) {
				this.tudursvehiclemod$setAssignedSeat(passenger, candidate);
				return;
			}
		}
	}

	/** Whether a carrier's own runway deck applied a carry displacement to this vehicle THIS tick - see RUNWAY_CARRY_ACTIVE's own doc. Exposed publicly for VehicleMoveIgnoreMixin. */
	public boolean tudursvehiclemod$isCarriedByRunwayLastTick() {
		return this.tudursvehiclemod$runwayCarryActive;
	}

	/** Called the instant a pilot (re)takes the controls, including re-boarding mid-air after being abandoned. */
	protected void onPilotMounted() {
		// Carrier-launched aircraft and dummy pilots both already have an established, autonomous cruiseSpeed - resetting it here would wrongly interrupt it.
		if (!(this instanceof AircraftEntity aircraft && aircraft.tudursvehiclemod$isCarrierPlayerControlled())
				&& !(this.getControllingPassenger() instanceof com.example.tudursvehiclemod.entity.DummyPilotEntity)) {
			this.cruiseSpeed = 0f;
		}
		this.setFreeLook(false);
		this.setDescending(false);
		// The hatch now simply stays at whatever state it was in before boarding. Canopy's own auto-close-on-mount is unchanged (still applies to every vehicle type, including SubmarineEntity - CANOPY_OPEN has no "surfaced" dual meaning there, unlike HATCH_OPEN, so the previous SubmarineEntity exception doesn't apply here at all).
		this.setCanopyOpen(false);
		this.tudursvehiclemod$autoSeatNearbyMobs();
	}

	/** How far (blocks) to search for nearby mobs to auto-seat - not derived from any documented MC Heli value, a reasonable guess. */
	private static final double AUTO_SEAT_MOB_RADIUS = 10.0;

	/** The largest width this feature allows - comfortably above an ordinary player/zombie/villager's own 0.6 and a cow/pig/sheep's own 0.9, but well below a horse's own ~1.4 (excluded) or larger. */
	private static final double AUTO_SEAT_MOB_MAX_WIDTH = 1.0;
	/** Per AUTO_SEAT_MOB_MAX_WIDTH's own doc: the largest height this feature allows - comfortably above an ordinary player/zombie/villager's own 1.8-1.95, but well below an enderman's own 2.9 or an iron golem's own 2.7 (both excluded).*/
	private static final double AUTO_SEAT_MOB_MAX_HEIGHT = 2.0;

	/** Called from onPilotMounted() above - fills every currently-empty NON-pilot seat (ascending index) with the nearest eligible mob (nearest-first, so the closest mobs get seated when there are more candidates than empty seats). Eligible means a live, currently-unmounted MobEntity within AUTO_SEAT_MOB_RADIUS blocks whose own CURRENT width/height (see AUTO_SEAT_MOB_MAX_WIDTH/HEIGHT's own doc for the exact thresholds and reasoning - a general size rule rather than an explicit species/boss list, so this naturally excludes the ender dragon/wither/ravagers/iron golems/hoglins/etc. without needing to name any of them, and extends to any mod-added mob too) both fall at or under the configured maximums; per the direct request, otherwise no distinction at all between hostile and passive mobs. Does nothing at all if this vehicle has only a pilot seat (nothing to fill) or every other seat is already occupied.*/
	private void tudursvehiclemod$autoSeatNearbyMobs() {
		if (this.getEntityWorld().isClient()) {
			return;
		}
		VehicleDefinition def = this.getDefinition();
		java.util.List<Integer> emptySeatIndices = new java.util.ArrayList<>();
		for (int seatIndex = 1; seatIndex < def.seats().size(); seatIndex++) {
			if (this.tudursvehiclemod$getSeatOccupant(seatIndex) == null) {
				emptySeatIndices.add(seatIndex);
			}
		}
		if (emptySeatIndices.isEmpty()) {
			return;
		}
		net.minecraft.util.math.Box searchBox = this.getBoundingBox().expand(AUTO_SEAT_MOB_RADIUS);
		java.util.List<net.minecraft.entity.mob.MobEntity> nearbyMobs = this.getEntityWorld().getEntitiesByClass(
				net.minecraft.entity.mob.MobEntity.class, searchBox,
				mob -> mob.isAlive() && mob.getVehicle() == null
						&& mob.getWidth() <= AUTO_SEAT_MOB_MAX_WIDTH
						&& mob.getHeight() <= AUTO_SEAT_MOB_MAX_HEIGHT);
		nearbyMobs.sort(java.util.Comparator.comparingDouble(this::squaredDistanceTo));
		int mobIndex = 0;
		for (int seatIndex : emptySeatIndices) {
			if (mobIndex >= nearbyMobs.size()) {
				break;
			}
			net.minecraft.entity.mob.MobEntity candidate = nearbyMobs.get(mobIndex);
			boolean mounted = this.tudursvehiclemod$mountToSeat(candidate, seatIndex);
			if (mounted) {
				mobIndex++;
			}
		}
	}

	/** How close a right-click has to land to a specific seat's own position to mount there directly - not derived from any documented MC Heli value, a reasonable guess. */
	private static final double SEAT_MOUNT_RADIUS = 1.0;

	/** This seat's own current world-space position - same formula updatePassengerPosition() already uses for actually positioning a mounted passenger each tick (including its own getRenderYOffsetCurrent() term - see that method's own doc for why that's needed, e.g. CarEntity's own step-up smoothing). */
	public Vec3d tudursvehiclemod$getSeatWorldPos(int seatIndex) {
		VehicleDefinition def = this.getDefinition();
		SeatDefinition seat = def.seats().get(seatIndex);
		org.joml.Vector3f local = new org.joml.Vector3f(
				(float) (seat.offsetX() * def.scale()),
				(float) (seat.offsetY() * def.scale()),
				(float) (seat.offsetZ() * def.scale()));
		this.getSeatRotationCurrent().transform(local);
		double seatY = this.getY() + this.getRenderYOffsetCurrent() + local.y;
		return new Vec3d(this.getX() + local.x, seatY, this.getZ() + local.z);
	}

	/** Launches this passenger straight up off this vehicle (a fixed, generous upward velocity - not configurable, MC Heli's own doc gives no launch-strength value to convert), immediately dismounts them, and - only if they actually have a parachute equipped in their chest slot - marks them for automatic deployment the instant they start falling back down (see entity.ParachuteManager's own tudursvehiclemod$markPendingAutoDeploy() doc for why this can't deploy immediately, mid-launch). No parachute equipped: falls exactly like an ordinary passenger dismounting mid-flight, per the direct instruction that this is an acceptable outcome. Silently does nothing if this vehicle's own definition doesn't have enableEjectionSeat set at all, or if this entity isn't actually a passenger of this vehicle (not a recognized seat). */
	public void tudursvehiclemod$tryEjectSeat(Entity passenger) {
		if (!this.getDefinition().enableEjectionSeat()) {
			return;
		}
		if (this.tudursvehiclemod$getAssignedSeatIndex(passenger) < 0) {
			return;
		}
		Vec3d launchOrigin = passenger.getEntityPos();
		passenger.stopRiding();
		// stopRiding() just above (via removePassenger()) unconditionally scheduled a deferred dismount-position correction for THIS SAME passenger, fired on this vehicle's own NEXT tick() call - which would teleport them straight back to their old seat position moments later, completely undoing the explosion's own velocity impulse below. Canceled here (only if it still actually targets this passenger) since the ejection itself already fully handles repositioning/launching - that later correction would otherwise defeat the whole feature.
		if (this.pendingDismountPassenger == passenger) {
			this.pendingDismountPassenger = null;
			this.pendingDismountPosition = null;
		}
		passenger.setPosition(launchOrigin.x, launchOrigin.y, launchOrigin.z);
		// Reverted to a direct, manually-computed vertical velocity instead (no distance/exposure/line-of-sight computation involved at all, unlike an explosion's own knockback formula, which may have been reduced or blocked by this vehicle's own hitbox still very close at the moment of ejection). Horizontal velocity is zeroed (rather than inheriting whatever this vehicle's own removePassenger() just set it to) for a clean, unmistakable vertical toss regardless of this vehicle's own current speed.
		Vec3d launchVelocity = new Vec3d(0.0, EJECTION_SEAT_LAUNCH_VELOCITY, 0.0);
		passenger.setVelocity(launchVelocity);
		passenger.velocityDirty = true;
		// ServerPlayerEntity.setVelocity()/velocityDirty above only reaches every OTHER client tracking this entity, not this SAME player's own client (a player doesn't track itself the way observers do) - matching vanilla's own explicit workaround for player-affecting knockback/explosions, sent directly here too so the ejected player actually sees their own launch. That earlier attempt's own failure is now understood to have actually been the deferred-dismount-correction bug fixed above, rather than this packet being insufficient on its own - but sending it regardless costs nothing and remains correct practice.
		if (passenger instanceof ServerPlayerEntity ejectedPlayer) {
			ejectedPlayer.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(ejectedPlayer.getId(), launchVelocity));
		}
		if (passenger instanceof net.minecraft.entity.LivingEntity livingEjected
				&& this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld ejectServerWorld) {
			ItemStack ejectChestStack = livingEjected.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
			boolean ejectHasRealParachute = ejectChestStack.getItem() instanceof com.example.tudursvehiclemod.item.ParachuteItem;
			int ejectColor = ejectHasRealParachute
					? net.minecraft.component.type.DyedColorComponent.getColor(ejectChestStack, 0xFFFFFF) | 0xFF000000
					: 0xFFFFFFFF;
			if (ejectHasRealParachute) {
				livingEjected.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
			}
			com.example.tudursvehiclemod.entity.ParachuteEntity.tudursvehiclemod$spawnAndMount(
					livingEjected, ejectServerWorld, livingEjected.getEntityPos(), ejectColor, ejectHasRealParachute);
		}
	}

	/** Per tudursvehiclemod$tryEjectSeat()'s own doc: how hard an ejection launches its occupant upward - not derived from any documented MC Heli value (that feature's own doc gives no launch-strength number to convert), a reasonable guess giving a clean, visible toss clear of the vehicle before gravity takes back over. */
	private static final double EJECTION_SEAT_LAUNCH_VELOCITY = 1.5;

	/** If this passenger's own currently-assigned seat has SeatDefinition's own enableParachuting set (and it is NOT the pilot seat, enforced here regardless of how that flag happens to be set - the pilot flying the vehicle should never be able to bail out this way), dismounts them and marks them for automatic deployment once they start falling, exactly like tudursvehiclemod$tryEjectSeat() above - EXCEPT per a further direct request ("パラシュート降下についてはパラシュートを装備していなくても一時的にパラシュートの効果および表示状態を付与するような実装にできますか", later extended to tudursvehiclemod$dropNextMobDropPassenger() too per "Mob投下にも適用してください。Mob投下はEnableParachutingと併用するもののため、実質的に同一です"), a passenger with no parachute of their own equipped is temporarily granted one (see entity.ParachuteManager's own tudursvehiclemod$grantVirtualParachuteAndMarkPending() doc) rather than simply falling with nothing - unlike tudursvehiclemod$tryEjectSeat() above, which remains unchanged (per that request's own specific scope: only enableParachuting-driven drops, whether via this method or the mob-drop sequence, grant a virtual one). Silently does nothing if this entity isn't a recognized passenger, is the pilot, or their own seat doesn't have this enabled at all. */
	public void tudursvehiclemod$tryParachuteJump(Entity passenger) {
		int seatIndex = this.tudursvehiclemod$getAssignedSeatIndex(passenger);
		if (seatIndex < 0 || seatIndex >= this.getDefinition().seats().size()) {
			return;
		}
		SeatDefinition seat = this.getDefinition().seats().get(seatIndex);
		if (seat.driver() || !seat.enableParachuting()) {
			return;
		}
		Vec3d jumpOrigin = passenger.getEntityPos();
		passenger.stopRiding();
		passenger.setPosition(jumpOrigin.x, jumpOrigin.y, jumpOrigin.z);
		if (this.pendingDismountPassenger == passenger) {
			this.pendingDismountPassenger = null;
			this.pendingDismountPosition = null;
		}
		if (passenger instanceof net.minecraft.entity.LivingEntity livingPassenger
				&& this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld jumpServerWorld) {
			ItemStack jumpChestStack = livingPassenger.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
			boolean jumpHasRealParachute = jumpChestStack.getItem() instanceof com.example.tudursvehiclemod.item.ParachuteItem;
			int jumpColor = jumpHasRealParachute
					? net.minecraft.component.type.DyedColorComponent.getColor(jumpChestStack, 0xFFFFFF) | 0xFF000000
					: 0xFFFFFFFF;
			if (jumpHasRealParachute) {
				livingPassenger.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
			}
			com.example.tudursvehiclemod.entity.ParachuteEntity.tudursvehiclemod$spawnAndMount(
					livingPassenger, jumpServerWorld, jumpOrigin, jumpColor, jumpHasRealParachute);
		}
	}

	/** A separate, unconditional force-dismount action, triggered by a long press of the parachute key: called when the pilot holds the parachute-jump key past PARACHUTE_FORCE_DISMOUNT_HOLD_TICKS (see client.VehicleModClient's own key-handling) - unlike tudursvehiclemod$tryTriggerMobDrop() (what a SHORT press of the same key triggers instead - only ever touches enableParachuting-enabled seats, one mob at a time at the configured interval, "長押ししていないにもかかわらずパラシュート降下が有効ではない座席まで降車させられており..また、一定間隔での降下は起こらず、一斉に落下します"), this dismounts EVERY non-pilot passenger at once - mob OR player alike - with a plain forced dismount and no parachute grant at all, regardless of any seat's own enableParachuting setting. Silently does nothing if requester isn't actually this vehicle's own current pilot (seat 0), or there are no other passengers to dismount at all. */
	public void tudursvehiclemod$tryForceDismountAllSeats(Entity requester) {
		if (this.tudursvehiclemod$getAssignedSeatIndex(requester) != 0) {
			return;
		}
		for (Entity passenger : new java.util.ArrayList<>(this.tudursvehiclemod$getRealPassengerList())) {
			if (passenger.equals(requester)) {
				continue;
			}
			passenger.stopRiding();
		}
	}

	/** Tick count remaining until the NEXT passenger in a currently-in-progress drop sequence is actually dropped; -1 means no sequence is currently running. Not persisted across a reload - an in-progress drop sequence simply stops if the vehicle/world happens to unload mid-sequence, an accepted edge case matching how uncommon and short-lived this state actually is. */
	private int mobDropTicksRemaining = -1;

	/** Per asset.MobDropOption's own doc: called when the pilot presses the assigned mob-drop key. Starts a fresh drop sequence (see mobDropTicksRemaining's own doc) if one isn't already running and this vehicle's own definition actually has a MobDropOption configured at all - the very FIRST eligible passenger (ascending seat index, SeatDefinition's own enableParachuting set, excluding the pilot seat itself) is dropped immediately, with tudursvehiclemod$updateMobDropSequence() (called every tick from tick() below) handling every subsequent one at the configured interval. Silently does nothing if a sequence is already in progress, there's no MobDropOption at all, or there are no eligible passengers to drop in the first place. */
	public void tudursvehiclemod$tryTriggerMobDrop() {
		if (this.mobDropTicksRemaining >= 0) {
			return;
		}
		java.util.Optional<com.example.tudursvehiclemod.asset.MobDropOption> mobDropOption = this.getDefinition().mobDropOption();
		if (mobDropOption.isEmpty()) {
			return;
		}
		if (this.tudursvehiclemod$dropNextMobDropPassenger(mobDropOption.get())) {
			this.mobDropTicksRemaining = mobDropOption.get().intervalTicks();
		}
	}

	/** Per mobDropTicksRemaining's own doc: called every tick (see tick() below) while a drop sequence is actually in progress. Counts down to 0, then drops the next eligible passenger (same ordering as tudursvehiclemod$tryTriggerMobDrop()'s own doc) and restarts the countdown - or ends the sequence entirely (mobDropTicksRemaining back to -1) once no eligible passengers remain at all. */
	private void tudursvehiclemod$updateMobDropSequence() {
		if (this.mobDropTicksRemaining < 0) {
			return;
		}
		if (this.mobDropTicksRemaining > 0) {
			this.mobDropTicksRemaining--;
			return;
		}
		java.util.Optional<com.example.tudursvehiclemod.asset.MobDropOption> mobDropOption = this.getDefinition().mobDropOption();
		if (mobDropOption.isEmpty() || !this.tudursvehiclemod$dropNextMobDropPassenger(mobDropOption.get())) {
			this.mobDropTicksRemaining = -1;
			return;
		}
		this.mobDropTicksRemaining = mobDropOption.get().intervalTicks();
	}

	/** Per asset.MobDropOption's own doc: finds the first eligible passenger (ascending seat index, SeatDefinition's own enableParachuting set, excluding the pilot seat) and drops them - teleported to the configured drop position (relX/relY/relZ, scaled and rotated with this vehicle's own current heading, exactly like tudursvehiclemod$getSeatWorldPos()'s own established pattern) before being dismounted. while THIS VEHICLE is itself airborne (not onGround), the dropped passenger gets a parachute forced on immediately and unconditionally (entity.ParachuteManager's own tudursvehiclemod$forceDeployParachute() - see that method's own doc for exactly how this differs from tudursvehiclemod$tryParachuteJump()'s own "wait for it to genuinely start falling" behavior above), granting a temporary virtual one if none is equipped. While this vehicle is itself grounded, no parachute is granted at all. Returns whether an eligible passenger was actually found and dropped at all - callers use this to know whether to keep the sequence running. */
	private boolean tudursvehiclemod$dropNextMobDropPassenger(com.example.tudursvehiclemod.asset.MobDropOption mobDropOption) {
		VehicleDefinition def = this.getDefinition();
		Entity toDrop = null;
		for (int seatIndex = 0; seatIndex < def.seats().size(); seatIndex++) {
			SeatDefinition seat = def.seats().get(seatIndex);
			if (seat.driver() || !seat.enableParachuting()) {
				continue;
			}
			Entity occupant = this.tudursvehiclemod$getSeatOccupant(seatIndex);
			if (occupant != null) {
				toDrop = occupant;
				break;
			}
		}
		if (toDrop == null) {
			return false;
		}
		org.joml.Vector3f local = new org.joml.Vector3f(
				(float) (mobDropOption.relX() * def.scale()),
				(float) (mobDropOption.relY() * def.scale()),
				(float) (mobDropOption.relZ() * def.scale()));
		this.getSeatRotationCurrent().transform(local);
		double dropX = this.getX() + local.x;
		double dropY = this.getY() + this.getRenderYOffsetCurrent() + local.y;
		double dropZ = this.getZ() + local.z;
		toDrop.stopRiding();
		toDrop.setPosition(dropX, dropY, dropZ);
		toDrop.setOnGround(false);
		if (this.pendingDismountPassenger == toDrop) {
			this.pendingDismountPassenger = null;
			this.pendingDismountPosition = null;
		}
		if (!this.isOnGround() && toDrop instanceof net.minecraft.entity.LivingEntity livingToDrop
				&& this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld dropServerWorld) {
			ItemStack dropChestStack = livingToDrop.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
			boolean dropHasRealParachute = dropChestStack.getItem() instanceof com.example.tudursvehiclemod.item.ParachuteItem;
			int dropColor = dropHasRealParachute
					? net.minecraft.component.type.DyedColorComponent.getColor(dropChestStack, 0xFFFFFF) | 0xFF000000
					: 0xFFFFFFFF;
			if (dropHasRealParachute) {
				livingToDrop.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
			}
			com.example.tudursvehiclemod.entity.ParachuteEntity.tudursvehiclemod$spawnAndMount(
					livingToDrop, dropServerWorld, new Vec3d(dropX, dropY, dropZ), dropColor, dropHasRealParachute);
		}
		return true;
	}

	/** This weapon's own actual muzzle position in world space (its first configured offset, scaled and rotated with this vehicle's current heading) - an aim calculation needs this rather than a generic vehicle-body reference point. Falls back to this vehicle's own current position if the weapon has no offsets configured at all (shouldn't normally happen). */
	public Vec3d tudursvehiclemod$getWeaponMuzzleWorldPos(com.example.tudursvehiclemod.asset.WeaponDefinition weapon) {
		if (weapon.offsets().isEmpty()) {
			return this.getEntityPos();
		}
		VehicleDefinition def = this.getDefinition();
		com.example.tudursvehiclemod.asset.WeaponOffset offset = weapon.offsets().get(0);
		org.joml.Vector3f local = new org.joml.Vector3f(
				(float) (offset.x() * def.scale()),
				(float) (offset.y() * def.scale()),
				(float) (offset.z() * def.scale()));
		this.getSeatRotationCurrent().transform(local);
		return new Vec3d(this.getX() + local.x, this.getY() + this.getRenderYOffsetCurrent() + local.y, this.getZ() + local.z);
	}

	/** Mounts player into whichever of this vehicle's own seats is currently closest to them, PROVIDED that seat is within SEAT_MOUNT_RADIUS - horizontally only (XZ), since the seat's own Y coordinate is irrelevant to this specific check, unlike interactAt()'s own hitPos-based search just below (which stays 3D and unchanged - see this whole feature's own doc for why the two coexist). Called from a proximity scan (see VehicleMod's own UseItemCallback registration), entirely independent of this entity's own interaction hitbox/raycast - the fix this whole mechanism exists for in the first place.
	 *
	 * Two-tier search, since densely-packed seating (small vehicles especially) could leave a player unable to mount at all: first finds the single nearest seat REGARDLESS of occupancy, within SEAT_MOUNT_RADIUS - this establishes whether the player is genuinely standing at a seating position on THIS vehicle at all. If that nearest seat is empty, mounts it directly (unchanged from before). If it's already occupied, falls back to the nearest EMPTY seat instead, this time with NO radius limit at all - once a player's own position is confirmed to genuinely correspond to one of this vehicle's own seats, "that exact seat is full" should assign the next available one rather than silently failing, exactly the way vanilla boarding a multi-seat vehicle already behaves.
	 *
	 * Returns whether a mount actually happened. */
	public boolean tudursvehiclemod$tryMountNearestSeat(PlayerEntity player) {
		if (player.isSneaking() || this.getEntityWorld().isClient() || this.tudursvehiclemod$isDestroyed()
				|| this.tudursvehiclemod$isUav() || this.tudursvehiclemod$isTargetDrone() || player.getVehicle() != null) {
			return false;
		}
		VehicleDefinition def = this.getDefinition();
		if (this.tudursvehiclemod$getRealPassengerList().size() >= Math.max(1, def.seats().size())) {
			return false;
		}
		int nearestOverallSeat = -1;
		double nearestOverallDistanceSq = SEAT_MOUNT_RADIUS * SEAT_MOUNT_RADIUS;
		for (int i = 0; i < def.seats().size(); i++) {
			Vec3d seatWorldPos = this.tudursvehiclemod$getSeatWorldPos(i);
			double dx = player.getX() - seatWorldPos.x;
			double dz = player.getZ() - seatWorldPos.z;
			double distanceSq = dx * dx + dz * dz;
			if (distanceSq < nearestOverallDistanceSq) {
				nearestOverallDistanceSq = distanceSq;
				nearestOverallSeat = i;
			}
		}
		if (nearestOverallSeat < 0) {
			return false;
		}
		int targetSeat = nearestOverallSeat;
		if (this.tudursvehiclemod$getSeatOccupant(targetSeat) != null) {
			targetSeat = -1;
			double nearestEmptyDistanceSq = Double.MAX_VALUE;
			for (int i = 0; i < def.seats().size(); i++) {
				if (this.tudursvehiclemod$getSeatOccupant(i) != null) {
					continue;
				}
				Vec3d seatWorldPos = this.tudursvehiclemod$getSeatWorldPos(i);
				double dx = player.getX() - seatWorldPos.x;
				double dz = player.getZ() - seatWorldPos.z;
				double distanceSq = dx * dx + dz * dz;
				if (distanceSq < nearestEmptyDistanceSq) {
					nearestEmptyDistanceSq = distanceSq;
					targetSeat = i;
				}
			}
		}
		if (targetSeat < 0) {
			return false;
		}
		player.startRiding(this);
		this.tudursvehiclemod$setAssignedSeat(player, targetSeat);
		return true;
	}

	/** Per this whole feature's own doc (see tudursvehiclemod$tryMountNearestSeat()): registers a UseItemCallback (fires on a right-click that hits neither a block nor an entity - exactly the case a remote seat's own small interaction hitbox can't be reached by raycast at all) that scans nearby vehicles for the closest seat within mount range and boards it directly. Called once from VehicleMod's own onInitialize(), same convention as DroneCenterBlock/StationBlock's own registerUseBlockCallback(). A generous search box around the player (not just around each vehicle's own entity position) is needed since a large vehicle's own seats can sit many blocks from its own origin. */
	public static void tudursvehiclemod$registerUseItemCallback() {
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClient() || hand != net.minecraft.util.Hand.MAIN_HAND) {
				return net.minecraft.util.ActionResult.PASS;
			}
			double searchRadius = 24.0;
			net.minecraft.util.math.Box searchBox = player.getBoundingBox().expand(searchRadius);
			for (AbstractVehicleEntity vehicle : world.getEntitiesByClass(AbstractVehicleEntity.class, searchBox, e -> true)) {
				if (vehicle.tudursvehiclemod$tryMountNearestSeat(player)) {
					return net.minecraft.util.ActionResult.SUCCESS;
				}
			}
			return net.minecraft.util.ActionResult.PASS;
		});
	}

	/** Right-clicking near a specific seat mounts directly into it (matching MC Heli's own per-seat hit-detection box), falling back to interact()'s own "first available seat" otherwise. */
	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		if (player.isSneaking() || this.getEntityWorld().isClient() || this.tudursvehiclemod$isDestroyed()) {
			return ActionResult.PASS;
		}
		// Registering a stick must never also mount the player, on ANY vehicle type.
		if (player.getStackInHand(hand).getItem() instanceof com.example.tudursvehiclemod.item.DroneControlStickItem) {
			return ActionResult.PASS;
		}
		// A UAV-flagged vehicle can never be boarded as a rider at all.
		if (this.tudursvehiclemod$isUav()) {
			return ActionResult.PASS;
		}
		// Same gating as isUav() just above, but for a dedicated target-drone vehicle (see tudursvehiclemod$isTargetDrone()'s own doc) - controlled exclusively through a Drone Center block instead.
		if (this.tudursvehiclemod$isTargetDrone()) {
			return ActionResult.PASS;
		}
		if (player.getVehicle() == this) {
			// Already riding THIS vehicle - never re-process a mount attempt for them (see this method's own doc for why this matters).
			return ActionResult.PASS;
		}
		// A player already riding some OTHER vehicle
		// (or any other entity at all - a boat, a horse,..) could still
		// board this one entirely, e.g. just by brushing past it while
		// piloting their own - nothing here ever checked whether they
		// were mounted on anything else at all. Boarding is blocked
		// outright while already riding anything; dismounting first is
		// required before boarding a different vehicle.
		if (player.getVehicle() != null) {
			return ActionResult.PASS;
		}
		VehicleDefinition def = this.getDefinition();
		if (this.tudursvehiclemod$getRealPassengerList().size() >= Math.max(1, def.seats().size())) {
			return ActionResult.PASS;
		}

		// If the pilot seat is empty, mounting ALWAYS
		// puts the player there, regardless of where on the vehicle they
		// actually clicked - a right-click intended for the pilot seat
		// shouldn't ever accidentally land in some other, unrelated seat
		// just because that seat happened to be geometrically closer to
		// the exact click position. Only once the pilot seat is already
		// occupied does the proximity-based search below (for a specific
		// OTHER seat) actually apply.
		if (this.tudursvehiclemod$getSeatOccupant(0) == null) {
			player.startRiding(this);
			this.tudursvehiclemod$setAssignedSeat(player, 0);
			return ActionResult.SUCCESS;
		}

		// hitPos is relative to this entity's own position, but NOT adjusted for its current yaw/pitch/roll - undo that rotation so it can be compared directly against each seat's own LOCAL (unrotated) offset.
		org.joml.Quaternionf inverseRotation = this.getSeatRotationCurrent().conjugate();
		org.joml.Vector3f localHit = new org.joml.Vector3f((float) hitPos.x, (float) hitPos.y, (float) hitPos.z);
		inverseRotation.transform(localHit);

		int nearestSeat = -1;
		double nearestDistanceSq = SEAT_MOUNT_RADIUS * SEAT_MOUNT_RADIUS;
		for (int i = 1; i < def.seats().size(); i++) {
			if (this.tudursvehiclemod$getSeatOccupant(i) != null) {
				continue;
			}
			SeatDefinition seat = def.seats().get(i);
			double dx = localHit.x - seat.offsetX() * def.scale();
			double dy = localHit.y - seat.offsetY() * def.scale();
			double dz = localHit.z - seat.offsetZ() * def.scale();
			double distanceSq = dx * dx + dy * dy + dz * dz;
			if (distanceSq < nearestDistanceSq) {
				nearestDistanceSq = distanceSq;
				nearestSeat = i;
			}
		}
		if (nearestSeat < 0) {
			return ActionResult.PASS;
		}
		player.startRiding(this);
		this.tudursvehiclemod$setAssignedSeat(player, nearestSeat);
		return ActionResult.SUCCESS;
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		// Plain right-click mounts the vehicle, so packing it away needs some other signal to not be ambiguous with that.
		if (player.isSneaking()) {
			return tryPackUp(player);
		}
		// Registering a vehicle to
		// item.DroneControlStickItem (right-clicking it while holding
		// that item - see that class's own doc) works on ANY vehicle at
		// all, regardless of its own isUav() value - an ordinary,
		// boardable vehicle can still ALSO be remote-controlled through
		// this project's own station block system, in addition to being
		// ridden normally; isUav() only ever gates whether ORDINARY
		// boarding itself is blocked (see the check just below this one),
		// not whether station-based remote control is available at all.
		// Handled directly here, rather than through that item's own
		// useOnEntity() (which is never actually called at all for a
		// plain Entity subclass like this one, only for LivingEntity
		// ones), and checked BEFORE the UAV-boarding-block/mounting
		// logic below, so registering always works regardless of that
		// block.
		ItemStack heldStack = player.getStackInHand(hand);
		if (heldStack.getItem() instanceof com.example.tudursvehiclemod.item.DroneControlStickItem) {
			if (!this.getEntityWorld().isClient() && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
				String suggestedName = this.getDefinition().displayName(Identifier.tryParse(this.dataTracker.get(VEHICLE_ID)));
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
						new com.example.tudursvehiclemod.network.DroneStickNameOpenPayload(hand == Hand.MAIN_HAND, this.getUuidAsString(), suggestedName));
			}
			return ActionResult.SUCCESS;
		}
		// isUav()==true blocks ordinary boarding entirely (station/stick-operated only); isUav()==false keeps boarding available AND registerable.
		if (this.tudursvehiclemod$isUav()) {
			// Opens this project's Mod menu instead of nothing - blocked while anyone is already remote-controlling this vehicle.
			if (this.remoteControllerId == null && !this.getEntityWorld().isClient()
					&& player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
				serverPlayer.openHandledScreen(this);
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		}
		// Same gating as isUav() just above, but no menu-on-right-click (a target drone is controlled exclusively through its Drone Center block instead - see tudursvehiclemod$isTargetDrone()'s own doc).
		if (this.tudursvehiclemod$isTargetDrone()) {
			return ActionResult.PASS;
		}
		if (player.getVehicle() == this) {
			// Already riding THIS vehicle - see interactAt()'s own doc for why this guard matters.
			return ActionResult.PASS;
		}
		// See interactAt()'s own matching doc - boarding is blocked outright while already riding anything else at all.
		if (player.getVehicle() != null) {
			return ActionResult.PASS;
		}

		if (!this.getEntityWorld().isClient() && !this.tudursvehiclemod$isDestroyed()
				&& this.tudursvehiclemod$getRealPassengerList().size() < Math.max(1, getDefinition().seats().size())) {
			this.tudursvehiclemod$assignFirstAvailableSeat(player);
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	/** Sneak + right-click turns the vehicle back into an item, provided nobody is currently riding it. */
	private ActionResult tryPackUp(PlayerEntity player) {
		if (this.getEntityWorld().isClient()) {
			// Let the client predict a successful interaction (swing animation etc.); the server performs the actual pack-up and is the source of truth.
			return ActionResult.SUCCESS;
		}
		if (this.getPassengerList().stream().anyMatch(p -> !(p instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity))) {
			player.sendMessage(Text.translatable("message.tudursvehiclemod.occupied"), true);
			return ActionResult.FAIL;
		}

		// Creative players never spent an item to place this vehicle (spawner items in the creative tab aren't consumed on use either), so handing one back here would..
		if (!player.isCreative()) {
			VehicleDefinition def = getDefinition();
			com.example.tudursvehiclemod.item.VehicleConverterTargets.byEntityTypeId(def.entityType()).ifPresent(target -> {
				net.minecraft.item.Item[] tiers = target.tieredSpawnerItems();
				if (tiers == null || tiers.length == 0) {
					return;
				}
				int tier = Math.max(1, Math.min(tiers.length, def.tier()));
				net.minecraft.item.Item item = tiers[tier - 1];
				if (item == null) {
					return;
				}
				ItemStack spawner = new ItemStack(item);
				if (!player.getInventory().insertStack(spawner)) {
					player.dropItem(spawner, false);
				}
			});
		}

		this.getEntityWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.BLOCK_ANVIL_USE, SoundCategory.NEUTRAL, 0.5f, 1.2f);

		this.discard();
		return ActionResult.SUCCESS;
	}

	/** Always whoever's assigned seat 0 (the pilot seat), not vanilla's getFirstPassenger() (raw mount order) - the two diverge once seat-switching decouples assignment from mount order. */
	@Override
	public LivingEntity getControllingPassenger() {
		Entity pilot = this.tudursvehiclemod$getSeatOccupant(0);
		return pilot instanceof LivingEntity living ? living : null;
	}

	/** Reverted to vanilla's original value - returning false broke mounting entirely (vanilla's own targeting raycast uses this for mounting too, not just combat) without actually fixing the "arrows hit anywhere on the vehicle" issue this was meant to address. */
	@Override
	public boolean canHit() {
		return !this.isRemoved();
	}

	/** Implements MC Heli's own MaxHP/ArmorDamageFactor/ArmorMinDamage/ ArmorMaxDamage directives (see VehicleExtras' own doc for what each means). */
	/** Bypasses armor reduction entirely, unlike damage(). Use for mechanics that must GUARANTEE destruction. */
	public void tudursvehiclemod$forceDestroy(ServerWorld world) {
		if (this.isRemoved() || this.tudursvehiclemod$isDestroyed()) {
			return;
		}
		this.dataTracker.set(HEALTH, 0f);
		this.tudursvehiclemod$onDestroyed(world);
	}

	/** Applies amount directly to health, bypassing armor reduction - for gradual damage-over-time mechanics armor shouldn't be able to deflect. */
	public void tudursvehiclemod$applyRawDamage(ServerWorld world, float amount) {
		if (this.isRemoved() || this.tudursvehiclemod$isDestroyed()) {
			return;
		}
		float newHealth = this.getHealth() - amount;
		this.dataTracker.set(HEALTH, Math.max(0f, newHealth));
		if (newHealth <= 0f) {
			this.tudursvehiclemod$onDestroyed(world);
		}
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (this.isRemoved() || this.tudursvehiclemod$isDestroyed()) {
			return false;
		}
		VehicleDefinition def = this.getDefinition();
		float reduced = amount * def.armorDamageFactor();
		if (reduced < def.armorMinDamage()) {
			return false; // below the minimum threshold - no effect at all
		}
		// ArmorMaxDamage - caps the reduced damage, rounding it DOWN to this value rather than letting a single huge hit apply in full - per MC Heli's own documented..
		reduced = Math.min(reduced, def.armorMaxDamage());
		float newHealth = this.getHealth() - reduced;
		this.dataTracker.set(HEALTH, Math.max(0f, newHealth));
		if (newHealth <= 0f) {
			this.tudursvehiclemod$onDestroyed(world);
		}
		return true;
	}

	/** One explosion queued by tudursvehiclemod$onDestroyed(), to be actually created at the START of the next server tick - see PENDING_EXPLOSIONS' own doc for why this is deferred rather than immediate. Captures x/y/z directly (rather than re-reading them from the source entity later, which might already be discarded/repositioned by the time this runs) - source itself is only used for attribution (explosion kill-credit), which vanilla tolerates fine even for an already-discarded entity. */
	private record PendingExplosion(ServerWorld world, Entity source, double x, double y, double z, float power) {
	}

	/** world.createExplosion() applies damage to every nearby entity SYNCHRONOUSLY, inline within the SAME call stack that triggered it - if that damage happens to kill ANOTHER nearby vehicle, that vehicle's own tudursvehiclemod$onDestroyed() (and its own explosion) previously ran recursively right there, chaining through however many vehicles happen to be clustered close enough (a dense CAS/Carrier formation, or several aircraft queued/orbiting near the same mothership point - exactly the scenario introduced by this project's own recent formation-flying feature) until the stack overflowed. Queued explosions are actually created by tudursvehiclemod$processPendingExplosions(), called once per server tick from a global END_SERVER_TICK hook (see VehicleMod's own onInitialize()) - each tick's own processing runs on a fresh, shallow stack, so a chain that used to recurse arbitrarily deep within one tick now simply spreads out over a few additional (imperceptible) ticks instead, however long the cluster actually is. A plain ArrayList is safe here despite being mutated by chained destructions during its own processing - tudursvehiclemod$processPendingExplosions() below swaps in a fresh empty list before iterating the old one, so anything queued mid-iteration lands in the NEW list for the following tick rather than colliding with the iteration in progress. */
	private static final java.util.List<PendingExplosion> PENDING_EXPLOSIONS = new java.util.ArrayList<>();

	/** Called once per server tick from VehicleMod's own onInitialize() - see PENDING_EXPLOSIONS' own doc. A no-op whenever nothing is queued. */
	public static void tudursvehiclemod$processPendingExplosions() {
		if (PENDING_EXPLOSIONS.isEmpty()) {
			return;
		}
		java.util.List<PendingExplosion> toProcess = new java.util.ArrayList<>(PENDING_EXPLOSIONS);
		PENDING_EXPLOSIONS.clear();
		for (PendingExplosion pending : toProcess) {
			pending.world().createExplosion(pending.source(), pending.x(), pending.y(), pending.z(), pending.power(), false,
					net.minecraft.world.World.ExplosionSourceType.MOB);
		}
	}

	/** Explodes once and ejects every passenger when this vehicle's own health reaches 0, but. */
	private void tudursvehiclemod$onDestroyed(ServerWorld world) {
		float explosionPower = MathHelper.clamp(
				(this.getDefinition().width() + this.getDefinition().height()) * 0.5f, 1.0f, 4.0f);
		// Per PENDING_EXPLOSIONS' own doc: deferred to next tick rather than created immediately, to break a recursive chain-reaction stack overflow through several clustered vehicles.
		PENDING_EXPLOSIONS.add(new PendingExplosion(world, this, this.getX(), this.getY(), this.getZ(), explosionPower));
		// A vehicle WITHOUT enableEjectionSeat now simply leaves every remaining passenger mounted on the wreck, undamaged, until they choose to dismount themselves - no forced dismount (this.removeAllPassengers() is skipped for this case entirely) and no damage at all. A vehicle WITH enableEjectionSeat instead ejects every remaining passenger via tudursvehiclemod$tryEjectSeat() (see that method's own doc for the launch-and-auto-parachute mechanism), which already dealt no damage of its own to begin with. EXCEPT a player actively piloting this aircraft under Carrier control (see AircraftEntity's own carrierPlayerControlled doc), returned safely to their own recorded mothership seat instead, checked FIRST regardless of which of the two paths above would otherwise apply - an already-established safe return takes priority over either. Falls through to the normal handling below if the return itself doesn't succeed (mothership gone, seat already occupied, mount failed for any other reason) - a player left with no vehicle at all is worse than one who's merely ejected/left mounted like usual. Snapshotted into a fresh list first (rather than iterating tudursvehiclemod$getRealPassengerList() directly) since stopRiding() (this method's own eject/mothership-return paths, or the player themselves later) structurally modifies riding state, which could otherwise corrupt iteration if that method's own underlying list ever turned out to be a live view rather than a true copy.
		boolean hasEjectionSeat = this.getDefinition().enableEjectionSeat();
		for (Entity passenger : new java.util.ArrayList<>(this.tudursvehiclemod$getRealPassengerList())) {
			if (passenger instanceof ServerPlayerEntity player
					&& this instanceof AircraftEntity aircraft
					&& aircraft.tudursvehiclemod$isCarrierPlayerControlled()
					&& this.tudursvehiclemod$tryReturnCarrierPilotToMothership(world, aircraft, player)) {
				continue;
			}
			if (hasEjectionSeat) {
				this.tudursvehiclemod$tryEjectSeat(passenger);
			}
		}
		// Per this method's own doc: deliberately no removeAllPassengers() call here anymore at all. A hasEjectionSeat vehicle's own tudursvehiclemod$tryEjectSeat() calls above already dismount each passenger they actually eject - nothing left to remove. A vehicle without an ejection seat leaves every remaining passenger mounted on the wreck, undamaged, until they choose to dismount themselves (per this method's own doc).
		this.dataTracker.set(DESTROYED, true);
		// A destroyed vehicle can no
		// longer be remote-controlled at all - ends any active session
		// through this SAME mechanism a manually-broken/removed
		// block.StationBlockEntity's own binding already uses (see that
		// class's own onStateReplaced() doc).
		this.tudursvehiclemod$tryExitRemoteControl();
		// A destroyed vehicle should no longer be flying its own autonomous target-drone routine - releases the Drone Center link the same way remote control is already released just above.
		this.tudursvehiclemod$setDroneLink(null);
	}

	/** Rather than ejected into open air and damaged like any other passenger - called from tudursvehiclemod$onDestroyed() before the normal damage/eject loop reaches them. Mirrors tudursvehiclemod$toggleCarrierSeat()'s own "returning to mothership" branch (same pre-check-before-stopRiding() order). Returns whether the return actually succeeded - false (mothership gone, seat already occupied by someone else, or the mount itself failing for any other reason) means the caller should fall back to the normal damage/eject handling instead, since a player left with no vehicle at all is worse than one merely ejected and damaged as usual. */
	private boolean tudursvehiclemod$tryReturnCarrierPilotToMothership(ServerWorld world, AircraftEntity aircraft, ServerPlayerEntity player) {
		java.util.UUID mothershipUuid = aircraft.tudursvehiclemod$getCarrierMothershipUuid();
		if (mothershipUuid == null) {
			return false;
		}
		if (!(world.getEntity(mothershipUuid) instanceof AbstractVehicleEntity mothership) || mothership.isRemoved()) {
			return false;
		}
		int seatIndex = aircraft.tudursvehiclemod$getCarrierMothershipSeatIndex();
		if (mothership.tudursvehiclemod$getSeatOccupant(seatIndex) != null) {
			return false;
		}
		player.stopRiding();
		boolean mounted = mothership.tudursvehiclemod$mountToSeat(player, seatIndex);
		if (mounted) {
			aircraft.tudursvehiclemod$setCarrierPlayerControlled(false);
		}
		return mounted;
	}

	/** Custom smoke (see registry.ModParticleTypes/client.particle.SmokePuffParticle's own doc) from every named part whose name contains "blade" (e.g. MC Heli's own "$blade0"/"$blade1" rotor blades) - ALL of them, if a vehicle has more than one. a vehicle with no such blade-named part at all still gets damage smoke, just from its own center instead, rather than getting none at all. */
	/** A real physical kick on this vehicle's actual pitch/roll, not a cosmetic render-time effect - runs on both client and server, applying the delta since last tick after all other pitch-setting logic. */
	private void tudursvehiclemod$updateRecoilShakePhysics() {
		// This method previously ran completely unconditionally, applying its own additive pitch/roll deltas every tick with no awareness of tudursvehiclemod$applySinkingMotion() at all. A vehicle that was still firing right up to the moment of its own destruction keeps a non-zero RECOIL_CURRENT_MAGNITUDE decaying for some time afterwards, so this kept adding its own kicks on top of the sinking easing - explaining both symptoms at once: the extra deltas stacked onto the sinking motion's own per-tick change (faster than the simulation, which assumed sinking was the only thing touching pitch/roll that tick), and any still-in-flight recoil event landed as a discrete jump superimposed on the otherwise-smooth curve. Stepping aside once sinking begins hands pitch/roll over to applySinkingMotion() with no contest; whatever offset recoil had already applied simply becomes part of the wreck's own starting attitude, which is harmless (and arguably fitting - a ship still shuddering from its last shot as it goes under).
		if (this.tudursvehiclemod$isSinking()) {
			return;
		}
		float currentMagnitude = this.dataTracker.get(RECOIL_CURRENT_MAGNITUDE);
		if (currentMagnitude <= 0f && this.lastAppliedRecoilPitchOffset == 0f && this.lastAppliedRecoilRollOffset == 0f) {
			return;
		}
		// Splits a single shake magnitude into a
		// pitch component and a ROLL component, based on RECOIL_AIM_YAW
		// (the firing weapon's own actual aim direction relative to this
		// vehicle, captured once at tudursvehiclemod$startRecoilShake()'s
		// own call time - see that field's own doc). A weapon firing
		// straight ahead (aimYaw=0) kicks purely as a nose-up PITCH; one
		// firing exactly sideways (aimYaw=±90) kicks purely as a ROLL
		// tilt away from that side instead, with anything in between
		// blending smoothly.
		float aimYawRad = (float) Math.toRadians(this.dataTracker.get(RECOIL_AIM_YAW));
		float targetPitchMagnitude = currentMagnitude * MathHelper.cos(aimYawRad);
		float targetRollMagnitude = currentMagnitude * MathHelper.sin(aimYawRad);
		float pitchDelta = targetPitchMagnitude - this.lastAppliedRecoilPitchOffset;
		float rollDelta = targetRollMagnitude - this.lastAppliedRecoilRollOffset;
		if (pitchDelta != 0f) {
			// Kicks the nose UP (pitch decreases in this project's own
			// convention, matching every other "looking up" case
			// elsewhere) as the shake magnitude rises, easing back down
			// again as it falls - see RECOIL_CURRENT_MAGNITUDE's own doc
			// for the instant-kick-then-decay shape this actually follows.
			this.setPitch(this.getPitch() - pitchDelta);
		}
		if (rollDelta != 0f) {
			// this.roll is a plain, unsynced field (see its own doc -
			// each side computes it independently from input, needing no
			// network sync at all), so it's added to directly here rather
			// than going through a setter - same sign relationship as the
			// pitch kick just above (a weapon firing to its own right
			// rolls this vehicle back towards its own left in reaction).
			this.roll -= rollDelta;
		}
		this.lastAppliedRecoilPitchOffset = targetPitchMagnitude;
		this.lastAppliedRecoilRollOffset = targetRollMagnitude;
		// Uses
		// VEHICLE_RECOIL_SHAKE_DECAY_RATE (its own, separate rate - see
		// that constant's own doc) instead of RECOIL_RECOVERY_RATE, which
		// is weaponPartRecoilOffset's own, unrelated concern.
		float eased = currentMagnitude * (1f - VEHICLE_RECOIL_SHAKE_DECAY_RATE);
		this.dataTracker.set(RECOIL_CURRENT_MAGNITUDE, eased < 0.001f ? 0f : eased);
	}

	private void tudursvehiclemod$updateDamageSmoke() {
		if (!(this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
			return;
		}
		VehicleDefinition def = this.getDefinition();
		boolean shouldSmoke = this.tudursvehiclemod$isDestroyed() || this.getHealth() < def.maxHealth() * 0.5f;
		if (!shouldSmoke) {
			return;
		}
		Quaternionf bodyOrientation = this.tudursvehiclemod$getBodyOrientation();
		boolean anyBladeFound = false;
		for (com.example.tudursvehiclemod.asset.PartAnimation part : def.spinningParts()) {
			if (!part.part().toLowerCase(java.util.Locale.ROOT).contains("blade")) {
				continue;
			}
			anyBladeFound = true;
			Vector3f local = new Vector3f(part.pivotX(), part.pivotY(), part.pivotZ());
			// A blade that's a child of a VtolRotorPart additionally inherits that parent nacelle's own current tilt, same forward transform as VehicleEntityRenderer's own render() - so the smoke follows the blade's actual current position (e.g. in VTOL helicopter mode) rather than its static rest-pose pivot.
			if (part.vtolRotorParent().isPresent() && this instanceof VtolEntity vtol) {
				for (com.example.tudursvehiclemod.asset.VtolRotorPart parentRotor : def.vtolRotorParts()) {
					if (parentRotor.part().equals(part.vtolRotorParent().get())) {
						local.sub(parentRotor.pivotX(), parentRotor.pivotY(), parentRotor.pivotZ());
						tudursvehiclemod$safeAxisAngleDeg(parentRotor.axisX(), parentRotor.axisY(), parentRotor.axisZ(),
								com.example.tudursvehiclemod.asset.VtolRotorPart.resolveAngleDegrees(vtol.getVtolTiltProgress())).transform(local);
						local.add(parentRotor.pivotX(), parentRotor.pivotY(), parentRotor.pivotZ());
						break;
					}
				}
			}
			local.mul(def.scale());
			bodyOrientation.transform(local);
			double x = this.getX() + local.x;
			double y = this.getY() + local.y;
			double z = this.getZ() + local.z;
			serverWorld.spawnParticles(com.example.tudursvehiclemod.registry.ModParticleTypes.SMOKE_PUFF,
					true, true, x, y, z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		if (!anyBladeFound) {
			serverWorld.spawnParticles(com.example.tudursvehiclemod.registry.ModParticleTypes.SMOKE_PUFF,
					true, true, this.getX(), this.getY() + this.getHeight() * 0.5, this.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	/** Vehicles are heavy. */
	@Override
	public boolean isPushable() {
		return false;
	}

	/** Called server-side (see FireWeaponPayload's receiver in VehicleMod) when a passenger presses the fire key. */
	/** True by default - overridden by SubmarineEntity to freeze weapons
	 * (both firing and aim-tracking parts) while submerged.
	 * Default behaviour is unrestricted firing. */
	public boolean tudursvehiclemod$canFireWeapons() {
		return true;
	}

	/** Weapon-aware overload - lets a subclass (SubmarineEntity) allow a specific weapon through where the plain check would say no (e.g. Torpedo while diving, or any weapon with usableWhileDiving() set - see WeaponStats's own doc). Takes the full WeaponDefinition (not just its WeaponType) so an override can consult a per-weapon flag, not only its shared Type. Defaults to delegating to the plain check. */
	public boolean tudursvehiclemod$canFireWeapons(java.util.Optional<com.example.tudursvehiclemod.asset.WeaponDefinition> weapon) {
		return this.tudursvehiclemod$canFireWeapons();
	}

	/** Resolves a WeaponPart's own optional weapon name back to its full WeaponDefinition, for canFireWeapons(Optional)'s use during aim-tracking (which only has the part, not the WeaponDefinition directly). */
	private java.util.Optional<com.example.tudursvehiclemod.asset.WeaponDefinition> tudursvehiclemod$resolveWeaponByName(java.util.Optional<String> weaponName) {
		if (weaponName.isEmpty()) {
			return java.util.Optional.empty();
		}
		String target = weaponName.get().toLowerCase(java.util.Locale.ROOT);
		for (WeaponDefinition weapon : this.getDefinition().weapons()) {
			if (weapon.weaponName().toLowerCase(java.util.Locale.ROOT).equals(target)) {
				return java.util.Optional.of(weapon);
			}
		}
		return java.util.Optional.empty();
	}

	/** Bundles the actual spawn position with the specific WeaponOffset used - callers needing that offset's mountYaw/mountPitch (for aim direction) must reuse this rather than re-deriving it separately, which would incorrectly re-advance weaponFiringPositionIndex a second time. */
	record WeaponSpawnInfo(Vec3d pos, com.example.tudursvehiclemod.asset.WeaponOffset offset, java.util.Optional<String> associatedPartName) {
	}

	/** This weapon's own muzzle/spawn position - cycles through weapon.offsets() and follows any associated WeaponPart, extracted out so no-projectile weapon types (Dispenser/Smoke/TargetingPod) can reuse the same muzzle-tracking without a real VehicleProjectileEntity. */
	public WeaponSpawnInfo tudursvehiclemod$computeWeaponSpawnPos(VehicleDefinition def, WeaponDefinition weapon, int weaponIndex) {
		java.util.List<com.example.tudursvehiclemod.asset.WeaponOffset> offsets = weapon.offsets();
		int positionIndex = weaponIndex < this.weaponFiringPositionIndex.length
				? this.weaponFiringPositionIndex[weaponIndex] % offsets.size()
				: 0;
		com.example.tudursvehiclemod.asset.WeaponOffset currentOffset = offsets.get(positionIndex);
		if (weaponIndex < this.weaponFiringPositionIndex.length) {
			this.weaponFiringPositionIndex[weaponIndex] = (positionIndex + 1) % offsets.size();
		}

		Vec3d weaponOffset = new Vec3d(currentOffset.x(), currentOffset.y(), currentOffset.z());
		// If one or more AddPartWeapon entries declared
		// a part tracking THIS specific weapon (matched by name), the
		// muzzle position follows that part's own current rotation, rather
		// than staying fixed relative to the vehicle body the way a
		// weapon with no associated visual part naturally does.
		//
		// When a weapon has MULTIPLE firing positions
		// (several AddWeapon lines sharing one name) AND multiple matching
		// parts (several AddPartWeapon lines naming that same weapon - e.g.
		// a twin-cannon turret with two separate barrel models), each
		// specific muzzle can be linked to a SPECIFIC part rather than
		// every muzzle just following whichever part happened to be found
		// first - see WeaponOffset's own linkedPart doc and
		// mcheli_convert.py's own compute_addweapon_part_ownership() doc
		// for exactly how the converter decides whether a given source
		// file's own declaration order actually supports this at all.
		// currentOffset.linkedPart() takes priority when present (an exact
		// part name to match); otherwise this falls back to matching by
		// weapon name alone - the ORIGINAL, simpler behavior, for any file
		// whose own AddWeapon/AddPartWeapon declaration order didn't
		// cleanly support linking specific muzzles to specific parts. When
		// falling back this way, a CHILD part (see WeaponPart.ChildInfo's
		// own doc) is preferred over its own parent if both match the same
		// weapon name (which they always do - a child
		// inherits its parent's own weapon_name during conversion): a
		// child's own applyWeaponPartRotation already composes BOTH its
		// parent's rotation AND its own additional rotation on top, so
		// it's strictly more complete than the parent alone - matching the
		// parent specifically would silently ignore any actual pitch a
		// child handles whenever the parent itself has pitch_follow=false
		// (a common pattern: the turret base only yaws, a child sub-part
		// like the barrel handles pitch), exactly matching a direct report
		// of pitch never affecting the fired position.
		java.util.List<com.example.tudursvehiclemod.asset.WeaponPart> matchingParts = def.weaponParts().stream()
				.filter(p -> p.weaponName().isPresent() && p.weaponName().get().equalsIgnoreCase(weapon.weaponName()))
				.toList();
		// The fallback path below (used whenever this specific offset has no explicit linkedPart) resolved the part with findFirst(), which by definition always returns the SAME part no matter which muzzle position is currently active - so the muzzle cycled correctly while its associated part never moved past the first one. Fixed by indexing matchingParts with the SAME positionIndex the muzzle itself is cycling through, wrapped by the part count, so a weapon with N muzzles and M parts walks both in step (the common N==M turret case maps each muzzle to its own part exactly; unequal counts still distribute sensibly rather than collapsing onto one).
		//
		// The child-part preference the previous fallback applied is kept, but scoped correctly: it now picks among the parts at THIS index rather than globally. Concretely, child parts are preferred as a GROUP when any exist (a child's own applyWeaponPartRotation composes both its parent's rotation and its own, so it is strictly more complete - see the explanation above), and the index is applied within whichever group is used. currentOffset.linkedPart() still takes priority over all of this when present, unchanged.
		java.util.List<com.example.tudursvehiclemod.asset.WeaponPart> childParts = matchingParts.stream()
				.filter(p -> p.childInfo().isPresent())
				.toList();
		java.util.List<com.example.tudursvehiclemod.asset.WeaponPart> cycleParts = childParts.isEmpty() ? matchingParts : childParts;
		java.util.Optional<com.example.tudursvehiclemod.asset.WeaponPart> associatedPart;
		// LinkedPart takes priority, but ONLY when it actually distinguishes the muzzles. mcheli_convert.py assigns it from declaration order - each AddPartWeapon owns whichever AddWeapon lines directly follow it (see its own compute_addweapon_part_ownership() doc) - so a source file laid out as one AddPartWeapon followed by ALL of that weapon's AddWeapon lines gives every single muzzle the SAME linkedPart. That is valid data, and the linked path then honours it exactly as told: every shot resolves to one part, which looks identical to the cycling being broken. Re-converting the same addon on a differently-arranged setup is exactly the kind of thing that flips a file between the two layouts.
		//
		// So: linkedPart is used when the weapon's own offsets name more than one distinct part, which is the case it exists to serve. When they all name the same one while several parts actually match this weapon, the linking carries no per-muzzle information at all, and cycling by positionIndex is what the report describes as correct. A weapon with genuinely one part is unaffected either way.
		boolean linkingDistinguishesMuzzles = offsets.stream()
				.map(com.example.tudursvehiclemod.asset.WeaponOffset::linkedPart)
				.filter(java.util.Optional::isPresent)
				.map(java.util.Optional::get)
				.distinct()
				.count() > 1;
		if (currentOffset.linkedPart().isPresent() && (linkingDistinguishesMuzzles || cycleParts.size() <= 1)) {
			associatedPart = def.weaponParts().stream().filter(p -> p.part().equals(currentOffset.linkedPart().get())).findFirst();
		} else if (cycleParts.isEmpty()) {
			associatedPart = java.util.Optional.empty();
		} else {
			associatedPart = java.util.Optional.of(cycleParts.get(positionIndex % cycleParts.size()));
		}
		if (associatedPart.isPresent()) {
			weaponOffset = tudursvehiclemod$applyWeaponPartRotation(weaponOffset, associatedPart.get());
		}
		// A submarine's own torpedo launch
		// position didn't move at all to account for the vehicle's own
		// current PITCH (only yaw affected it) - unlike aircraft, which
		// need this same full-orientation transform to correctly place a
		// weapon mounted away from the body's own center once pitched up
		// or down. rotateY() only ever rotates around the vertical axis
		// (yaw), completely ignoring bodyOrientation's own pitch/roll
		// components - using the full quaternion transform here (the
		// SAME bodyOrientation already computed below for the actual
		// firing DIRECTION) makes the mount POSITION follow the vehicle's
		// own full current attitude too, not just its yaw.
		Quaternionf bodyOrientation = this.tudursvehiclemod$getBodyOrientation();
		Vector3f mountOffsetVec = new Vector3f((float) weaponOffset.x, (float) weaponOffset.y, (float) weaponOffset.z);
		bodyOrientation.transform(mountOffsetVec);
		Vec3d mountOffset = new Vec3d(mountOffsetVec.x, mountOffsetVec.y, mountOffsetVec.z);
		return new WeaponSpawnInfo(this.getEntityPos().add(this.tudursvehiclemod$getBodyFrameOffset(1.0f)).add(mountOffset),
				currentOffset, associatedPart.map(com.example.tudursvehiclemod.asset.WeaponPart::part));
	}

	/** Converts a ship-local (localX, localY, localZ) offset - relative to addWeaponPos, in the SAME rotated reference frame the landing approach point already uses (this mothership's own current yaw + the weapon's own mount_yaw + yawOffsetDegrees) - into a world position. Shared by every Carrier feature that needs a "point relative to the ship, that moves/rotates with it" other than the route itself (which uses a separate, target-point-derived basis - see CarrierAircraftConfig's own doc for why these are deliberately different). */
	public Vec3d tudursvehiclemod$carrierLocalToWorld(Vec3d addWeaponPos, double localX, double localY, double localZ,
			com.example.tudursvehiclemod.asset.WeaponOffset mountOffset, float yawOffsetDegrees) {
		double combinedYawRad = Math.toRadians(this.getYaw() + mountOffset.mountYaw() + yawOffsetDegrees);
		double forwardX = -Math.sin(combinedYawRad);
		double forwardZ = Math.cos(combinedYawRad);
		double rightX = forwardZ;
		double rightZ = -forwardX;
		double worldX = addWeaponPos.x + localX * rightX + localZ * forwardX;
		double worldY = addWeaponPos.y + localY;
		double worldZ = addWeaponPos.z + localX * rightZ + localZ * forwardZ;
		return new Vec3d(worldX, worldY, worldZ);
	}

	/** Kicks the associated part's recoil offset to full recoilDistance on fire (if any, and if > 0) - updateWeaponPartRecoil() eases it back each tick. No-op if no associated part, or recoilDistance is 0. */
	/** ACTUAL_FIRE_BITMASK value as of the end of last tick, compared against the current value to detect a rising edge (a weapon that JUST fired this tick, not merely still within an earlier grace window). */
	private int previousFiringBitmaskForRecoil = 0;

	/** Plain exponential decay rate for weaponPartRecoilOffset's own easing. */
	private static final float RECOIL_RECOVERY_RATE = 0.3f;
	/** A separate, doubled-decay-time rate for this vehicle's own shake, distinct from RECOIL_RECOVERY_RATE. */
	private static final float VEHICLE_RECOIL_SHAKE_DECAY_RATE = 1f - (float) Math.sqrt(1f - RECOIL_RECOVERY_RATE);

	/** Snaps RECOIL_CURRENT_MAGNITUDE to the weapon's own Recoil strength and captures aim yaw for the pitch/roll split - called from tryFireWeapon() only on an actual shot. */
	private void tudursvehiclemod$startRecoilShake(WeaponDefinition weapon) {
		this.dataTracker.set(RECOIL_CURRENT_MAGNITUDE, weapon.recoil());
		float aimYaw = this.getWeaponAimYaw(weapon.seatIndex(), false, weapon.aimRange().orElse(null), 1.0f);
		this.dataTracker.set(RECOIL_AIM_YAW, aimYaw);
	}

	private void tudursvehiclemod$updateWeaponPartRecoil() {
		VehicleDefinition def = this.getDefinition();
		int currentBitmask = this.dataTracker.get(ACTUAL_FIRE_BITMASK);
		int risingEdge = currentBitmask & ~this.previousFiringBitmaskForRecoil;
		this.previousFiringBitmaskForRecoil = currentBitmask;
		if (risingEdge != 0) {
			java.util.List<WeaponDefinition> weapons = def.weapons();
			for (int weaponIndex = 0; weaponIndex < weapons.size() && weaponIndex < 32; weaponIndex++) {
				if ((risingEdge & (1 << weaponIndex)) == 0) {
					continue;
				}
				String weaponName = weapons.get(weaponIndex).weaponName();
				for (com.example.tudursvehiclemod.asset.WeaponPart part : def.weaponParts()) {
					if (part.recoilDistance() > 0f && part.weaponName().isPresent()
							&& part.weaponName().get().equalsIgnoreCase(weaponName)) {
						this.weaponPartRecoilOffset.put(part.part(), part.recoilDistance());
						this.weaponPartRecoilStartTick.put(part.part(), this.age);
					}
				}
			}
		}

		if (this.weaponPartRecoilOffset.isEmpty()) {
			return;
		}
		for (var entry : this.weaponPartRecoilOffset.entrySet()) {
			String partName = entry.getKey();
			this.prevWeaponPartRecoilOffset.put(partName, entry.getValue());
			float fullDistance = -1f;
			int durationTicks = 40;
			int recedeTicks = 5;
			for (com.example.tudursvehiclemod.asset.WeaponPart candidatePart : def.weaponParts()) {
				if (candidatePart.part().equals(partName) && candidatePart.recoilDistance() > 0f) {
					fullDistance = candidatePart.recoilDistance();
					if (candidatePart.weaponName().isPresent()) {
						for (WeaponDefinition weapon : def.weapons()) {
							if (weapon.weaponName().equalsIgnoreCase(candidatePart.weaponName().get())) {
								durationTicks = Math.max(1, weapon.recoilDurationTicks());
								// RecoilRecessionRateMultiplier
								// is NOT itself a tick count - the recede
								// phase's own actual duration (in ticks)
								// is durationTicks DIVIDED BY this value
								// (e.g. RecoilBufCount = 40, 5 means the
								// recede animation takes 40/5 = 8 ticks).
								int recessionMultiplier = Math.max(1, weapon.recoilRecessionRateMultiplier());
								recedeTicks = Math.max(1, Math.min(durationTicks, durationTicks / recessionMultiplier));
								break;
							}
						}
					}
					break;
				}
			}
			if (fullDistance <= 0f) {
				// This part's own recoilDistance somehow couldn't be found
				// at all anymore (shouldn't normally happen) - just let
				// this offset sit as-is rather than risk dividing by an
				// unknown/zero distance below.
				continue;
			}
			int startTick = this.weaponPartRecoilStartTick.getOrDefault(partName, this.age);
			int elapsed = this.age - startTick;
			// Per Readme_Weapon.txt's own RecoilBufCount doc, and a
			// clarification: both of RecoilBufCount's own values are
			// literal tick counts (not a rate/multiplier at all, despite
			// this directive's own documented Japanese name for the
			// second one) - durationTicks is the WHOLE animation's own
			// total length; recedeTicks is specifically how many of those
			// ticks the initial kick-out (0 -> fullDistance) itself takes,
			// with whatever's left over (durationTicks - recedeTicks)
			// spent easing back down from fullDistance to 0 again.
			float newOffset;
			if (elapsed < recedeTicks) {
				newOffset = fullDistance * MathHelper.clamp(elapsed / (float) recedeTicks, 0f, 1f);
			} else {
				int recoverTicks = Math.max(1, durationTicks - recedeTicks);
				float recoverProgress = (elapsed - recedeTicks) / (float) recoverTicks;
				newOffset = fullDistance * (1f - MathHelper.clamp(recoverProgress, 0f, 1f));
			}
			entry.setValue(newOffset < 0.001f && elapsed >= durationTicks ? 0f : newOffset);
		}
	}

	/** Render-interpolated current recoil offset (blocks) for a named WeaponPart's own part_type=2 recoil kick (see weaponPartRecoilOffset's own doc), or 0 if this vehicle has no such recoiling part / it hasn't fired recently. */
	public float tudursvehiclemod$getWeaponPartRecoilOffset(String partName, float tickDelta) {
		float previous = this.prevWeaponPartRecoilOffset.getOrDefault(partName, 0f);
		float current = this.weaponPartRecoilOffset.getOrDefault(partName, 0f);
		return previous + (current - previous) * tickDelta;
	}

	/** Car takes damage colliding with a living entity (crash); Tank doesn't; Unknown behaves like neither (ported from MC Heli's WeightType directive - undefined for anything but Tank/Car). */
	private void tudursvehiclemod$handleWeightTypeMobCollision(VehicleDefinition def) {
		if (def.weightType() != com.example.tudursvehiclemod.asset.WeightType.CAR) {
			return;
		}
		double speed = this.getVelocity().horizontalLength();
		// Only a "crash" worth damaging over at a meaningful speed; barely nudging a mob while idling shouldn't hurt the vehicle.
		if (speed < 0.05) {
			return;
		}
		List<LivingEntity> hitEntities = this.getEntityWorld().getEntitiesByClass(
				LivingEntity.class, this.getBoundingBox(),
				entity -> entity != (Entity) this && !this.hasPassenger(entity) && entity.isAlive());
		if (hitEntities.isEmpty()) {
			return;
		}
		ServerWorld world = (ServerWorld) this.getEntityWorld();
		float damageAmount = (float) MathHelper.clamp(speed * 10.0, 1.0, 10.0);
		this.damage(world, world.getDamageSources().generic(), damageAmount);
	}

	/** Tank breaks logs/leaves (any type) in its path; Car breaks nothing (ported from MC Heli's WeightType directive). */
	private void tudursvehiclemod$handleWeightTypeBlockBreaking(VehicleDefinition def) {
		if (def.weightType() != com.example.tudursvehiclemod.asset.WeightType.TANK) {
			return;
		}
		ServerWorld world = (ServerWorld) this.getEntityWorld();
		// Blocks the vehicle's own hitbox was directly
		// touching/resting on (e.g. leaves directly underneath while
		// riding on top of them) weren't reliably being caught by the
		// exact, unexpanded bounding box - expanded by a small margin in
		// every direction (including downward) so anything actually in
		// contact is always included.
		net.minecraft.util.math.Box box = this.getBoundingBox().expand(0.1);
		net.minecraft.util.math.BlockPos min = net.minecraft.util.math.BlockPos.ofFloored(box.minX, box.minY, box.minZ);
		net.minecraft.util.math.BlockPos max = net.minecraft.util.math.BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);
		java.util.Set<net.minecraft.util.math.BlockPos> alreadyFelled = new java.util.HashSet<>();
		for (net.minecraft.util.math.BlockPos pos : net.minecraft.util.math.BlockPos.iterate(min, max)) {
			net.minecraft.util.math.BlockPos immutablePos = pos.toImmutable();
			if (alreadyFelled.contains(immutablePos)) {
				continue;
			}
			net.minecraft.block.BlockState state = world.getBlockState(immutablePos);
			if (state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) {
				// tudursvehiclemod$tryFellTree()
				// itself now always fully resolves any tree it doesn't
				// reject outright (see its own doc) - either felling it,
				// destroying it in its entirety if it's bigger than an
				// ordinary tree but still within its own safety cap, or
				// (for a genuine big tree, or the rare structure beyond
				// even the safety cap) leaving it standing entirely
				// untouched. A null result means the latter.
				java.util.Set<net.minecraft.util.math.BlockPos> resolvedTreeBlocks =
						tudursvehiclemod$tryFellTree(world, immutablePos);
				if (resolvedTreeBlocks != null) {
					alreadyFelled.addAll(resolvedTreeBlocks);
				}
			} else if (state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
				// This vehicle kept getting caught on
				// low bushes/short trees - leaves ENCOUNTERED DIRECTLY
				// (never logs - those are still only ever handled via
				// tudursvehiclemod$tryFellTree() above) are destroyed
				// outright here. Also clears every OTHER leaves block in
				// a column extending 3 blocks straight up from this one -
				// a short bush/tree's own canopy commonly overhangs
				// slightly above where the vehicle's own hitbox actually
				// reaches, and clearing only the exact blocks directly
				// inside the hitbox left just enough of that overhang
				// behind to keep snagging on it.
				for (int upStep = 0; upStep <= 3; upStep++) {
					net.minecraft.util.math.BlockPos columnPos = immutablePos.up(upStep);
					if (world.getBlockState(columnPos).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
						world.breakBlock(columnPos, false, this);
					}
				}
			}
		}
	}


	/** Fells or destroys a recognized tree the vehicle's hitbox overlaps, rather than just deleting the overlapping block. Returns the full set of handled positions, or null if the connected structure exceeds the safety cap. */
	private java.util.Set<net.minecraft.util.math.BlockPos> tudursvehiclemod$tryFellTree(
			ServerWorld world, net.minecraft.util.math.BlockPos startLog) {
		final int MAX_TREE_LOGS = 24;
		final int MAX_TREE_LEAVES = 100;
		// A tree exceeding the above (ordinary-tree)
		// thresholds is no longer abandoned immediately - it's instead
		// fully identified (up to these considerably larger safety caps)
		// so it can be destroyed in its entirety rather than partially.
		final int MAX_SEARCH_LOGS = 96;
		final int MAX_SEARCH_LEAVES = 500;
		final int MAX_SEARCH_RADIUS = 16;

		java.util.Set<net.minecraft.util.math.BlockPos> logs = new java.util.LinkedHashSet<>();
		java.util.Set<net.minecraft.util.math.BlockPos> leaves = new java.util.LinkedHashSet<>();
		java.util.ArrayDeque<net.minecraft.util.math.BlockPos> queue = new java.util.ArrayDeque<>();
		java.util.Set<net.minecraft.util.math.BlockPos> visited = new java.util.HashSet<>();
		queue.add(startLog);
		visited.add(startLog);

		while (!queue.isEmpty()) {
			net.minecraft.util.math.BlockPos current = queue.poll();
			net.minecraft.block.BlockState currentState = world.getBlockState(current);
			if (currentState.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) {
				logs.add(current);
			}
			if (logs.size() > MAX_SEARCH_LOGS) {
				return null;
			}
			// 6-directional (orthogonal-only)
			// adjacency cut trees like Acacia off at the very first
			// diagonal branch - Acacia's own trunk characteristically
			// grows diagonally, and large oak variants commonly have
			// similar angled branch clusters, neither of which ever
			// directly touch along a shared face the way a plain
			// vertical trunk does. Every neighbor in the full 3x3x3 cube
			// around this block (26 neighbors, not just the 6 orthogonal
			// ones) is checked instead, so a log diagonally touching
			// another log (sharing at least a single edge or corner) is
			// still recognized as the same connected tree.
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						net.minecraft.util.math.BlockPos neighbor = current.add(dx, dy, dz);
						if (!visited.add(neighbor)) {
							continue;
						}
						if (Math.abs(neighbor.getX() - startLog.getX()) > MAX_SEARCH_RADIUS
								|| Math.abs(neighbor.getY() - startLog.getY()) > MAX_SEARCH_RADIUS
								|| Math.abs(neighbor.getZ() - startLog.getZ()) > MAX_SEARCH_RADIUS) {
							continue;
						}
						if (world.getBlockState(neighbor).isIn(net.minecraft.registry.tag.BlockTags.LOGS)) {
							queue.add(neighbor);
						}
					}
				}
			}
		}

		if (logs.isEmpty()) {
			return null;
		}

		// An earlier version checked whichever log the
		// vehicle's own hitbox happened to touch first for "2+ directly
		// adjacent logs", which could misfire on an ordinary single-trunk
		// tree's own branch cluster (large oak/acacia commonly have 2+
		// logs bunched together partway up, even though the trunk's own
		// actual base is only a single log wide) - incorrectly treating a
		// perfectly ordinary tree as an unfellable "big tree". Checked
		// here instead, against this tree's own TRUE base (the lowest log
		// or logs actually found by the full flood-fill above) - a
		// genuine 2x2+ "big tree" trunk (e.g. dark oak) always has 2+ logs
		// this way at its own base; an ordinary tree's occasional branch
		// cluster higher up the trunk never does.
		int baseY = logs.stream().mapToInt(net.minecraft.util.math.BlockPos::getY).min().orElseThrow();
		java.util.List<net.minecraft.util.math.BlockPos> baseLogs = logs.stream()
				.filter(p -> p.getY() == baseY).toList();
		boolean isBigTree = false;
		outer:
		for (net.minecraft.util.math.BlockPos baseLog : baseLogs) {
			int adjacentAtBase = 0;
			for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
				if (baseLogs.contains(baseLog.offset(direction))) {
					adjacentAtBase++;
				}
			}
			if (adjacentAtBase >= 2) {
				isBigTree = true;
				break outer;
			}
		}
		if (isBigTree) {
			// Big trees are left standing entirely
			// untouched - no felling, no destruction at all.
			return null;
		}

		// Leaves only ever explored when directly
		// touching an already-found log (an earlier attempt) missed most
		// of a real tree's own canopy outright - actual Minecraft trees
		// commonly have leaf blocks 2+ blocks out from the trunk with no
		// log directly adjacent to them at all. A separate bounding-box
		// scan around the trunk's own actual extent (expanded by a
		// couple of blocks in every direction) instead reliably catches
		// the whole canopy regardless of exactly how far out individual
		// leaf blocks happen to sit, still bounded by MAX_SEARCH_LEAVES.
		int trunkMinX = logs.stream().mapToInt(net.minecraft.util.math.BlockPos::getX).min().orElseThrow();
		int trunkMaxX = logs.stream().mapToInt(net.minecraft.util.math.BlockPos::getX).max().orElseThrow();
		int trunkMinY = logs.stream().mapToInt(net.minecraft.util.math.BlockPos::getY).min().orElseThrow();
		int trunkMaxY = logs.stream().mapToInt(net.minecraft.util.math.BlockPos::getY).max().orElseThrow();
		int trunkMinZ = logs.stream().mapToInt(net.minecraft.util.math.BlockPos::getZ).min().orElseThrow();
		int trunkMaxZ = logs.stream().mapToInt(net.minecraft.util.math.BlockPos::getZ).max().orElseThrow();
		final int LEAF_SCAN_MARGIN = 3;
		net.minecraft.util.math.BlockPos leafScanMin = new net.minecraft.util.math.BlockPos(
				trunkMinX - LEAF_SCAN_MARGIN, trunkMinY - LEAF_SCAN_MARGIN, trunkMinZ - LEAF_SCAN_MARGIN);
		net.minecraft.util.math.BlockPos leafScanMax = new net.minecraft.util.math.BlockPos(
				trunkMaxX + LEAF_SCAN_MARGIN, trunkMaxY + LEAF_SCAN_MARGIN, trunkMaxZ + LEAF_SCAN_MARGIN);
		for (net.minecraft.util.math.BlockPos pos : net.minecraft.util.math.BlockPos.iterate(leafScanMin, leafScanMax)) {
			if (world.getBlockState(pos).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
				leaves.add(pos.toImmutable());
				if (leaves.size() > MAX_SEARCH_LEAVES) {
					return null;
				}
			}
		}

		java.util.Set<net.minecraft.util.math.BlockPos> allTreeBlocks = new java.util.HashSet<>(logs);
		allTreeBlocks.addAll(leaves);

		if (logs.size() > MAX_TREE_LOGS || leaves.size() > MAX_TREE_LEAVES) {
			// Bigger than an ordinary tree (but
			// still within the generous safety cap above, or this method
			// would already have returned null well before reaching here)
			// - rather than leaving most of it standing (this vehicle's
			// own hitbox is intentionally small, so a fallback that only
			// cleared whatever single block it happened to touch left the
			// vast majority of an oversized tree standing regardless),
			// the entire thing - every log and every leaf found - is
			// simply destroyed outright instead of felled.
			for (net.minecraft.util.math.BlockPos pos : allTreeBlocks) {
				world.breakBlock(pos, false, this);
			}
			// The same 3-second braking-equivalent
			// deceleration applies here too, not just an actual fell -
			// see treeCollisionBrakeTicksRemaining's own doc.
			this.setTreeCollisionBrakeTicksRemaining(30);
			return allTreeBlocks;
		}

		int minY = trunkMinY;
		int baseX = logs.stream().filter(p -> p.getY() == minY).mapToInt(net.minecraft.util.math.BlockPos::getX).findFirst().orElseThrow();
		int baseZ = logs.stream().filter(p -> p.getY() == minY).mapToInt(net.minecraft.util.math.BlockPos::getZ).findFirst().orElseThrow();

		// Trying to precisely determine "the"
		// correct fall direction from this vehicle's own current
		// velocity wasn't working out reliably (velocity can be small,
		// noisy, or poorly aligned with either horizontal axis right at
		// The moment of impact) - exact accuracy
		// isn't actually needed here at all. This vehicle's own current
		// YAW (a far more stable signal - it's however the vehicle is
		// actually facing, or was last facing, entirely independent of
		// its own instantaneous velocity) is snapped to the nearest of
		// the 4 cardinal directions instead, that direction's own
		// opposite (which would drop the trunk back onto/into the
		// vehicle itself) is excluded, and one of the remaining 3
		// directions is picked uniformly at random.
		// Direction.fromRotation(float) doesn't exist
		// in this project's own Yarn mappings - computed manually instead,
		// using the same yaw-to-direction-index formula vanilla itself
		// uses internally in equivalent places.
		int facingIndex = MathHelper.floor(this.getYaw() / 90.0 + 0.5) & 3;
		net.minecraft.util.math.Direction facing = switch (facingIndex) {
			case 0 -> net.minecraft.util.math.Direction.SOUTH;
			case 1 -> net.minecraft.util.math.Direction.WEST;
			case 2 -> net.minecraft.util.math.Direction.NORTH;
			default -> net.minecraft.util.math.Direction.EAST;
		};
		net.minecraft.util.math.Direction excluded = facing.getOpposite();
		java.util.List<net.minecraft.util.math.Direction> candidates = new java.util.ArrayList<>();
		for (net.minecraft.util.math.Direction candidate : new net.minecraft.util.math.Direction[]{
				net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.SOUTH,
				net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.WEST}) {
			if (candidate != excluded) {
				candidates.add(candidate);
			}
		}
		net.minecraft.util.math.Direction fallDirection = candidates.get(this.getRandom().nextInt(candidates.size()));
		int fallStepX = fallDirection.getOffsetX();
		int fallStepZ = fallDirection.getOffsetZ();
		net.minecraft.state.property.Property<net.minecraft.util.math.Direction.Axis> axisProperty =
				net.minecraft.state.property.Properties.AXIS;
		net.minecraft.util.math.Direction.Axis fallAxis = fallDirection.getAxis();

		net.minecraft.util.math.BlockPos basePos = new net.minecraft.util.math.BlockPos(baseX, minY, baseZ);
		java.util.List<net.minecraft.util.math.BlockPos> orderedLogs = new java.util.ArrayList<>(logs);
		orderedLogs.sort(java.util.Comparator.comparingInt(net.minecraft.util.math.BlockPos::getY));

		// Applies a braking-equivalent deceleration
		// for the next 1.5 seconds (30 ticks) - see treeCollisionBrakeTicksRemaining's
		// own doc - for an actual fell here; the full-destroy branch above
		// sets this same counter itself, so that both
		// outcomes should trigger it. Only a genuine big tree (or the rare
		// structure beyond even the safety cap), which return before ever
		// reaching either of these points, don't.
		this.setTreeCollisionBrakeTicksRemaining(30);

		for (net.minecraft.util.math.BlockPos leafPos : leaves) {
			world.removeBlock(leafPos, false);
		}

		int step = 0;
		for (net.minecraft.util.math.BlockPos logPos : orderedLogs) {
			net.minecraft.block.BlockState originalState = world.getBlockState(logPos);
			world.removeBlock(logPos, false);
			if (logPos.equals(basePos)) {
				// The base itself stays exactly where it was, still
				// upright - only the trunk ABOVE it actually falls over,
				// same as a real felled tree still has its own stump
				// left behind.
				world.setBlockState(logPos, originalState, net.minecraft.block.Block.NOTIFY_ALL);
				continue;
			}
			step++;
			net.minecraft.util.math.BlockPos newPos = basePos.add(fallStepX * step, 0, fallStepZ * step);
			if (world.getBlockState(newPos).isAir()) {
				net.minecraft.block.BlockState fallenState = originalState.contains(axisProperty)
						? originalState.with(axisProperty, fallAxis)
						: originalState;
				world.setBlockState(newPos, fallenState, net.minecraft.block.Block.NOTIFY_ALL);
			}
			// If newPos isn't clear air (e.g. more terrain in the way),
			// that particular trunk section is simply lost rather than
			// forced through existing terrain - same "don't overwrite
			// what's actually there" caution the step-up logic itself
			// already follows elsewhere in this class.
		}

		java.util.Set<net.minecraft.util.math.BlockPos> handled = new java.util.HashSet<>(logs);
		handled.addAll(leaves);
		return handled;
	}


	/** Reliable, actual signed forward speed (positive = forward relative to this vehicle's own CURRENT facing) based on genuine position change since last tick - deliberately NOT this.getVelocity(), which can already read back as zero here even on a tick this vehicle genuinely moved (see lastTickX/lastTickZ's own doc for why vanilla's own post-move() velocity-zeroing can fool that).
	 * <p>MUST be called AFTER this tick's own move() has already been applied (an earlier call site that called this BEFORE its own move() always got back ~0, every single tick, regardless of actual speed - this.getX()/getZ() at that point still held this tick's own PRE-move position, identical to lastTickX/lastTickZ, which themselves hold the position from the END of last tick i.e. the START of this one - so dx/dz was always ~0 too). tudursvehiclemod$updateWheelSpinPhase() itself already gets this right "for free", simply by virtue of running later still, after the whole of updateVehicleMovement() (including this tick's own move()) has already returned.
	 * <p>Reads (but, unlike tudursvehiclemod$updateWheelSpinPhase()'s own call site, does NOT advance) lastTickX/lastTickZ/hasLastTickPosition - those themselves stay correctly stale (last tick's own position) for the whole of THIS tick regardless of exactly when within it this is called, since only tudursvehiclemod$updateWheelSpinPhase() itself (which always runs after updateVehicleMovement() returns) ever advances them - it's specifically this.getX()/getZ() (THIS vehicle's own current position, which move() is what actually changes) that this MUST be called after, not those fields. */
	/** Whether this vehicle's own Y is currently frozen at tudursvehiclemod$lockedSurfaceTargetY, per tudursvehiclemod$applySurfaceFloatSpring()'s own doc. */
	private boolean tudursvehiclemod$surfaceYLocked;
	/** The Y this vehicle's own position is currently frozen at, while tudursvehiclemod$surfaceYLocked is true - meaningless otherwise. NaN until first locked. */
	private double tudursvehiclemod$lockedSurfaceTargetY = Double.NaN;
	/** How far the water-surface target must move from its own last LOCKED value before this vehicle treats it as a genuine surface change (resuming normal spring physics) rather than noise to ignore outright - the underlying assumption is that water surface height simply doesn't change by less than a full block under normal circumstances (individual wave ripples/flowing-water surface variation isn't something this mod needs to visually track at all), with this set slightly BELOW that full block (0.5, not 1.0) as a safety margin against the actual detected value not landing exactly on a whole-block boundary. */
	protected static final double SURFACE_CHANGE_LOCK_THRESHOLD = 0.5;
	/** How close (position) and how slow (velocity) this vehicle's own approach to a water-surface target must be before tudursvehiclemod$applySurfaceFloatSpring() snaps/locks it outright rather than continuing to spring toward it - shared default for any vehicle type using that method, matching the value this project has already tuned identically for Ship/Submarine's own surfaced mode. */
	protected static final double SURFACE_SETTLE_POSITION_THRESHOLD = 0.01;
	protected static final double SURFACE_SETTLE_VELOCITY_THRESHOLD = 0.005;
	/** Deliberately gentle - a strong spring causes visible bobbing (per Ship/Submarine/Aircraft's own identical prior tuning, now shared here since every existing float-capable vehicle type used exactly this same value). */
	protected static final double SURFACE_SPRING_STRENGTH = 0.02;
	/** Applied every tick regardless of the spring correction above, so any residual vertical velocity actually settles instead of ringing forever. */
	protected static final double SURFACE_VERTICAL_DAMPING = 0.8;

	/** Shared water-surface buoyancy spring for any floating vehicle type (Ship/Submarine surfaced mode, Aircraft/VTOL water landing, etc.) - computes vertical velocity toward targetY via a damped spring, but locks onto a stable target and skips the computation entirely (holding Y fixed) once settled, only resuming normal spring physics once the target has moved by SURFACE_CHANGE_LOCK_THRESHOLD. Callers must call tudursvehiclemod$resetSurfaceFloatLock() whenever their own water search comes back empty, so a later return to water doesn't compare against a stale locked value. */
	protected double tudursvehiclemod$applySurfaceFloatSpring(double targetY, double currentVelY,
			double springStrength, double verticalDamping, double settlePositionThreshold, double settleVelocityThreshold) {
		if (this.tudursvehiclemod$surfaceYLocked && Math.abs(targetY - this.tudursvehiclemod$lockedSurfaceTargetY) < SURFACE_CHANGE_LOCK_THRESHOLD) {
			if (Math.abs(this.getY() - this.tudursvehiclemod$lockedSurfaceTargetY) > 1.0e-9) {
				this.setPosition(this.getX(), this.tudursvehiclemod$lockedSurfaceTargetY, this.getZ());
			}
			return 0.0;
		}
		this.tudursvehiclemod$surfaceYLocked = false;
		double yError = targetY - this.getY();
		if (Math.abs(yError) < settlePositionThreshold && Math.abs(currentVelY) < settleVelocityThreshold) {
			if (Math.abs(this.getY() - targetY) > 1.0e-9) {
				this.setPosition(this.getX(), targetY, this.getZ());
			}
			this.tudursvehiclemod$surfaceYLocked = true;
			this.tudursvehiclemod$lockedSurfaceTargetY = targetY;
			return 0.0;
		}
		return (currentVelY + yError * springStrength) * verticalDamping;
	}

	/** Per tudursvehiclemod$applySurfaceFloatSpring()'s own doc: callers must call this whenever their own water-surface search finds nothing at all this tick (not near water), so a later return to water starts fresh instead of comparing against a stale, unrelated locked value. */
	protected void tudursvehiclemod$resetSurfaceFloatLock() {
		this.tudursvehiclemod$surfaceYLocked = false;
	}

	/** Shared default: scans a small vertical range around this vehicle's own position for the topmost water block - returns empty if no water is found nearby at all. Ship/Submarine/Aircraft each already declare their own more specialized version of this same method (silently overriding this default, with no behavior change) - this shared default exists for any OTHER vehicle type (Helicopter/Car/StaticEmplacement's own new float support, and any future type) that doesn't need anything more specialized. Callers should skip surface-spring logic entirely and fall back to plain gravity/normal physics when this is empty. */
	protected java.util.OptionalDouble tudursvehiclemod$findWaterSurfaceY() {
		net.minecraft.util.math.BlockPos basePos = net.minecraft.util.math.BlockPos.ofFloored(this.getX(), this.getY(), this.getZ());
		double surfaceY = this.getY();
		boolean foundWater = false;
		for (int dy = -2; dy <= 3; dy++) {
			net.minecraft.util.math.BlockPos checkPos = basePos.add(0, dy, 0);
			if (this.getEntityWorld().getFluidState(checkPos).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
				surfaceY = checkPos.getY() + 1.0;
				foundWater = true;
			}
		}
		return foundWater ? java.util.OptionalDouble.of(surfaceY) : java.util.OptionalDouble.empty();
	}

	protected float tudursvehiclemod$getActualForwardSpeed() {
		double dx = this.getX() - this.lastTickX;
		double dz = this.getZ() - this.lastTickZ;
		double forwardX = -Math.sin(Math.toRadians(this.getYaw()));
		double forwardZ = Math.cos(Math.toRadians(this.getYaw()));
		return this.hasLastTickPosition ? (float) (dx * forwardX + dz * forwardZ) : 0f;
	}

	/** Signed forward speed (positive = forward) from an explicit velocity vector, using this vehicle's current yaw - for callers that need a signed value from a velocity Vec3d before move() has run this tick (tudursvehiclemod$getActualForwardSpeed() requires move() to have already happened). Used by Ship/Submarine's own wake-trail reversing detection. */
	protected float tudursvehiclemod$getSignedForwardSpeed(Vec3d velocity) {
		double forwardX = -Math.sin(Math.toRadians(this.getYaw()));
		double forwardZ = Math.cos(Math.toRadians(this.getYaw()));
		return (float) (velocity.x * forwardX + velocity.z * forwardZ);
	}

	/** Accumulates wheelSpinPhase by horizontal ground speed × wheel_rotation_speed each tick (speed-based, not throttle-based, matching MC Heli's PartWheelRot doc and CarEntity's own wheelRotation formula). Runs on both client and server. */
	private void tudursvehiclemod$updateWheelSpinPhase() {
		this.prevWheelSpinPhase = this.wheelSpinPhase;
		if (this.dataTracker.get(RUNWAY_CARRY_WHEEL_SYNC) && this.getControllingPassenger() == null) {
			this.lastTickX = this.getX();
			this.lastTickZ = this.getZ();
			this.hasLastTickPosition = true;
			return;
		}
		// This vehicle's own forward-facing unit
		// vector dotted with the actual displacement gives a SIGNED
		// speed instead of Math.hypot's own unsigned magnitude - negative
		// while genuinely moving backward relative to this vehicle's own
		// current facing, spinning the wheel the other way accordingly.
		float speed = tudursvehiclemod$getActualForwardSpeed();
		this.wheelSpinPhase += speed * this.getDefinition().wheelRotationSpeed();
		this.lastTickX = this.getX();
		this.lastTickZ = this.getZ();
		this.hasLastTickPosition = true;
	}

	/** Derived from differential/skid-steering physics (v - x·ω per side) - both pivot-turn and ordinary turn fall out of the same formula (both terms are directly measured motion rather than simulated/commanded state). */
	private double tudursvehiclemod$getCrawlerTrackSpeed(com.example.tudursvehiclemod.asset.CrawlerTrackPart track, float actualForwardSpeed, float angularVelocityRadians) {
		// The belt's own visual movement direction was
		// reversed compared to MC Heli for ordinary forward motion - the
		// whole expression is negated here (rather than just one term) so
		// the relative left/right differential-steering relationship
		// (see this method's own class-level doc) stays intact; only
		// which way "forward" actually moves the belt changes.
		//
		// Left/right was completely (not
		// partially) reversed - confirmed once a pivot-turn vehicle made
		// this glaringly obvious (a full stop on the wrong side, rather
		// than just a somewhat-off relative speed difference during an
		// ordinary partial turn) - the angular term's own sign relative
		// to track.x() is flipped here (track.x() * -angularVelocityRadians,
		// equivalently -track.x() * angularVelocityRadians) to match.
		double speed = -(actualForwardSpeed - track.x() * -angularVelocityRadians);
		// With pivotTurnThrottle > 0 (this vehicle's
		// own definition says it performs genuine tank-style "信地旋回" -
		// pivoting on one stationary track - rather than "超信地旋回" -
		// spinning both tracks in opposite directions), the raw
		// differential-steering formula above could still make the INNER
		// track's own speed cross into the opposite sign from the
		// vehicle's overall direction of travel while turning sharply
		// enough - visually counter-rotating that track backward while
		// the outer one moves forward, which is "部分的超信地旋回" (a
		// PARTIAL neutral-turn), not the genuine single-track-stopped
		// pivot this vehicle is actually supposed to perform. Clamped to
		// never cross past 0 relative to what this SAME return value
		// would already be with no turning at all (-actualForwardSpeed,
		// given the negation above) - comparing against raw, non-negated
		// actualForwardSpeed directly here instead (an earlier version of
		// this fix) clamped this to 0 on EVERY ordinary straight-line tick,
		// since ordinary forward motion (actualForwardSpeed > 0) always
		// maps to a NEGATIVE "speed" by this method's own established
		// convention - only escaping unnoticed during a sharp, low-speed
		// turn, where the angular term could push speed positive on its
		// own regardless of the clamp. The inner track can still slow
		// all the way down to a full stop, just never reverse past that
		// point.
		if (this.getDefinition().pivotTurnThrottle() > 0f) {
			double speedWithNoTurning = -actualForwardSpeed;
			// Swapping these branches (an earlier
			// attempt) broke ordinary straight-line motion entirely -
			// with no turning at all (angularVelocityRadians == 0),
			// EVERY track's own raw speed equals speedWithNoTurning
			// exactly, and the swapped branches clamped that shared,
			// non-turning value itself to 0. This form is the only one
			// that leaves straight-line motion alone (a track's own
			// speed only ever gets clamped when it actually crosses to
			// the OPPOSITE sign from speedWithNoTurning, which can only
			// happen from the angular term's own contribution during an
			// actual turn) - the underlying "which side is inner"
			// determination itself comes entirely from the differential-
			// steering formula above (this clamp is agnostic to it,
			// simply catching whichever specific track's own value
			// crosses the shared threshold).
			speed = speedWithNoTurning >= 0 ? Math.max(0, speed) : Math.min(0, speed);
		}
		return speed;
	}

	/** Accumulates every CrawlerTrackPart's own "belt
	 * distance travelled" by its own tudursvehiclemod$getCrawlerTrackSpeed()
	 * each tick - see crawlerTrackPhase's own doc.
	 *
	 * Uses directly MEASURED motion (getActualForwardSpeed()/yaw delta), not commanded state, and MUST run BEFORE tudursvehiclemod$updateWheelSpinPhase() in the same tick (shares lastTickX/lastTickZ/lastYaw). Advances lastYaw itself, immediately after use. */
	private void tudursvehiclemod$updateCrawlerTrackPhase() {
		boolean suppressedByRunwayCarry = this.dataTracker.get(RUNWAY_CARRY_WHEEL_SYNC) && this.getControllingPassenger() == null;
		float actualForwardSpeed = tudursvehiclemod$getActualForwardSpeed();
		float angularVelocityRadians = (float) Math.toRadians(MathHelper.wrapDegrees(this.getYaw() - this.lastYaw));
		this.lastYaw = this.getYaw();
		for (com.example.tudursvehiclemod.asset.CrawlerTrackPart track : this.getDefinition().crawlerTracks()) {
			float previous = this.crawlerTrackPhase.getOrDefault(track.part(), 0f);
			this.prevCrawlerTrackPhase.put(track.part(), previous);
			if (suppressedByRunwayCarry) {
				continue;
			}
			double speed = tudursvehiclemod$getCrawlerTrackSpeed(track, actualForwardSpeed, angularVelocityRadians);
			this.crawlerTrackPhase.put(track.part(), previous + (float) speed);
		}
	}

	/** Render-interpolated current belt phase (blocks travelled, unbounded) for a named CrawlerTrackPart - see crawlerTrackPhase's own doc. */
	public float tudursvehiclemod$getCrawlerTrackPhase(String partName, float tickDelta) {
		float previous = this.prevCrawlerTrackPhase.getOrDefault(partName, 0f);
		float current = this.crawlerTrackPhase.getOrDefault(partName, 0f);
		return previous + (current - previous) * tickDelta;
	}

	/** Caches each TrackRollerPart's own resolved rotationsPerBlock (override or geometry-computed), so it's only resolved once per part per entity instance rather than every render frame. */
	private final java.util.Map<String, Float> trackRollerRotationsPerBlock = new java.util.HashMap<>();

	/** How many more ticks to apply brake-equivalent deceleration after felling a tree (set to 30/1.5s in tryFellTree()) - whichever ground vehicle actually implements braking (CarEntity) applies it via its own ordinary brake mechanism. Backed by TREE_COLLISION_BRAKE_TICKS's DataTracker field. */
	public int getTreeCollisionBrakeTicksRemaining() {
		return this.dataTracker.get(TREE_COLLISION_BRAKE_TICKS);
	}

	public void setTreeCollisionBrakeTicksRemaining(int ticks) {
		this.dataTracker.set(TREE_COLLISION_BRAKE_TICKS, ticks);
	}

	/** A track roller spins in lock-step with whichever CrawlerTrackPart sits on its own side, driven by that track's own belt phase (- so the two can never visually drift apart). Identity if no rotationsPerBlock or no track on that side. */
	public Quaternionf tudursvehiclemod$getTrackRollerSpinRotation(
			com.example.tudursvehiclemod.asset.TrackRollerPart roller, float tickDelta) {
		Float rotationsPerBlock = this.trackRollerRotationsPerBlock.computeIfAbsent(roller.part(), name -> {
			if (roller.rotationsPerBlock().isPresent()) {
				return roller.rotationsPerBlock().get();
			}
			Float computed = com.example.tudursvehiclemod.asset.ServerObjModelTrackRollerBounds
					.getRotationsPerBlock(this.getDefinition().model()).get(name);
			return computed != null ? computed : 0f;
		});
		if (rotationsPerBlock == 0f) {
			return new Quaternionf();
		}
		com.example.tudursvehiclemod.asset.CrawlerTrackPart matchedTrack = null;
		double bestXDelta = Double.MAX_VALUE;
		boolean rollerOnLeft = roller.pivotX() > 0;
		for (com.example.tudursvehiclemod.asset.CrawlerTrackPart track : this.getDefinition().crawlerTracks()) {
			boolean trackOnLeft = track.x() > 0;
			if (trackOnLeft != rollerOnLeft) {
				continue;
			}
			double xDelta = Math.abs(Math.abs(track.x()) - Math.abs(roller.pivotX()));
			if (xDelta < bestXDelta) {
				bestXDelta = xDelta;
				matchedTrack = track;
			}
		}
		if (matchedTrack == null) {
			return new Quaternionf();
		}
		float beltPhase = tudursvehiclemod$getCrawlerTrackPhase(matchedTrack.part(), tickDelta);
		// The sync (speed) was already correct, but
		// the rotational direction itself was backward - a roller riding
		// under/against a moving belt needs to spin in the OPPOSITE
		// rotational sense from simply using the belt's own phase value
		// directly (the same way a wheel rolling over the ground spins
		// opposite to the ground's own apparent motion underneath it,
		// not the same way).
		float degrees = -beltPhase * rotationsPerBlock * 360f;
		return new Quaternionf().rotateX((float) Math.toRadians(degrees));
	}

	/** This vehicle's own current steering angle (degrees) - proportional to the current sideways (A/D) input, matching WheelPart's own steerAngle at full deflection. Shared by every WheelPart/SteeringWheelPart on this vehicle (MC Heli has no notion of independently-steerable wheel groups - every steered wheel/steering wheel model turns together). */
	private float tudursvehiclemod$getSteeringFraction() {
		return (float) this.getSyncedSidewaysInput();
	}

	/** Eased separately from getSteeringFraction() (which stays instant, still driving WheelPart directly) - only the in-cabin steering wheel prop visibly benefits from easing. Tracks a fraction (-1.1), not an angle. */
	private float smoothedSteeringFraction;
	private float prevSmoothedSteeringFraction;

	/** How much of the remaining gap to the raw steering fraction closes per tick - 0.15 reaches most of full lock within a few ticks. */
	private static final float STEERING_WHEEL_SMOOTHING = 0.15f;

	private void tudursvehiclemod$updateSmoothedSteeringWheelFraction() {
		this.prevSmoothedSteeringFraction = this.smoothedSteeringFraction;
		float target = this.tudursvehiclemod$getSteeringFraction();
		this.smoothedSteeringFraction += (target - this.smoothedSteeringFraction) * STEERING_WHEEL_SMOOTHING;
	}

	/** WheelPart's own continuous spin rotation (local X axis), applied BEFORE the steering rotation. */
	public Quaternionf tudursvehiclemod$getWheelPartSpinRotation(float tickDelta) {
		float phase = this.prevWheelSpinPhase + (this.wheelSpinPhase - this.prevWheelSpinPhase) * tickDelta;
		return new Quaternionf().rotateX((float) Math.toRadians(phase));
	}

	/** WheelPart's own steering rotation (steerAxis, default vertical king-pin) - the OUTER transform around the continuous spin above. */
	public Quaternionf tudursvehiclemod$getWheelPartSteerRotation(com.example.tudursvehiclemod.asset.WheelPart wheelPart) {
		float angleDeg = this.tudursvehiclemod$getSteeringFraction() * wheelPart.steerAngle();
		return new Quaternionf().rotateAxis((float) Math.toRadians(angleDeg),
				(float) wheelPart.steerAxisX(), (float) wheelPart.steerAxisY(), (float) wheelPart.steerAxisZ());
	}

	/** SteeringWheelPart's own rotation, proportional to the smoothed/interpolated steering fraction up to maxAngle - unlike WheelPart, this IS the entire rotation (no separate spin stage). */
	public Quaternionf tudursvehiclemod$getSteeringWheelRotation(com.example.tudursvehiclemod.asset.SteeringWheelPart steeringWheelPart, float tickDelta) {
		float fraction = this.prevSmoothedSteeringFraction + (this.smoothedSteeringFraction - this.prevSmoothedSteeringFraction) * tickDelta;
		float angleDeg = fraction * steeringWheelPart.maxAngle();
		return new Quaternionf().rotateAxis((float) Math.toRadians(angleDeg),
				(float) steeringWheelPart.axisX(), (float) steeringWheelPart.axisY(), (float) steeringWheelPart.axisZ());
	}

	/** Toggles weaponIndex between its two modes (0<->1) if hasModes() is true, else a no-op - implemented for MachineGun2/Rocket/ATMissile/TVMissile. */
	public void tudursvehiclemod$tryToggleWeaponMode(int weaponIndex) {
		VehicleDefinition def = getDefinition();
		if (weaponIndex < 0 || weaponIndex >= def.weapons().size()) {
			return;
		}
		WeaponDefinition weapon = def.weapons().get(weaponIndex);
		if (!weapon.hasModes()) {
			return;
		}
		this.tudursvehiclemod$ensureWeaponAmmoArraysSized(def);
		this.weaponMode[weaponIndex] = this.weaponMode[weaponIndex] == 0 ? 1 : 0;
		this.tudursvehiclemod$syncWeaponAmmo();
	}

	/** This light's own CURRENT world-space aim, as {yaw, pitch} in degrees, for the given FollowMode. Called from both server and client - a pure function of already-synced state (seat occupants' own rotation, this vehicle's own body yaw, and the synced steering fraction), so both sides land on the same answer without needing anything new synced across the network purely for this.
	 *
	 * baseYaw/basePitch (this light's own AddSearchLight-authored rest orientation, relative to the vehicle's own body) is always the starting point; PILOT_VIEW and STEERING then add an offset on top of it, while FIXED uses it completely unchanged. */
	public float[] tudursvehiclemod$getSearchLightWorldAim(com.example.tudursvehiclemod.asset.SearchLightPart light, float tickProgress) {
		float bodyYaw = this.getYaw(tickProgress);
		switch (light.followMode()) {
			case FIXED -> {
				return new float[]{bodyYaw + light.baseYaw(), light.basePitch()};
			}
			case STEERING -> {
				// smoothedSteeringFraction is already the same eased -1.1 value the in-cabin steering wheel prop itself turns with (see that field's own doc) - reusing it here keeps a steering-linked light's own motion visually consistent with the wheel, rather than snapping to the raw, unsmoothed input.
				float steerOffset = this.smoothedSteeringFraction * light.steerAngle();
				return new float[]{bodyYaw + light.baseYaw() + steerOffset, light.basePitch()};
			}
			default -> {
				// PILOT_VIEW: follows whichever occupant is actually tracking it, the same resolution weapon parts use (pilot fallback, since a search light - unlike most weapons - has no dedicated gunner seat of its own in MC Heli's own format).
				Entity occupant = this.tudursvehiclemod$resolveWeaponTrackingOccupant(0, true);
				if (occupant != null) {
					// getYaw(float)/getPitch(float) are base Entity methods (see this same class's own occupant.getYaw(tickProgress) usage elsewhere), not LivingEntity-specific, so no instanceof check is needed here.
					return new float[]{occupant.getYaw(tickProgress), occupant.getPitch(tickProgress)};
				}
				// No occupant means SEARCH_LIGHT_LAST_YAW/PITCH (kept current every tick by tick() itself, above) holds wherever this light was actually last aimed - returning THAT instead of bodyYaw/basePitch (the FIXED-mode fallback, which was wrongly shared with PILOT_VIEW's own no-rider case before this fix, collapsing pitch to whatever basePitch happens to be - usually zero). NaN means no occupant has EVER been tracked yet (a vehicle whose search light has never had anyone aim it) - only then does the FIXED-style fallback apply, since there is genuinely no "last aim" to fall back to.
				float lastYaw = this.dataTracker.get(SEARCH_LIGHT_LAST_YAW);
				float lastPitch = this.dataTracker.get(SEARCH_LIGHT_LAST_PITCH);
				if (Float.isNaN(lastYaw) || Float.isNaN(lastPitch)) {
					return new float[]{bodyYaw + light.baseYaw(), light.basePitch()};
				}
				return new float[]{lastYaw, lastPitch};
			}
		}
	}

	/** Flips this vehicle's own search light(s) on/off - see SEARCH_LIGHT_ON's own doc for why this is one flag covering every SearchLightPart in the definition at once, matching tryToggleWeaponMode()'s own vehicle-wide shape immediately above rather than per-weapon indexing. A no-op for a vehicle with no search lights defined at all, so the network round trip this is called from costs nothing extra for those. */
	public void tudursvehiclemod$toggleSearchLight() {
		if (getDefinition().searchLightParts().isEmpty()) {
			return;
		}
		this.dataTracker.set(SEARCH_LIGHT_ON, !this.dataTracker.get(SEARCH_LIGHT_ON));
	}

	/** Whether this vehicle's own search light(s) are currently on - read by both AddPartLightHatch's own "light_hatch" toggle-part trigger (see updateToggleParts()'s own doc) and the client-side beam renderer. */
	public boolean tudursvehiclemod$isSearchLightOn() {
		return this.dataTracker.get(SEARCH_LIGHT_ON);
	}

	/** Finds every missile currently guiding on THIS vehicle within FLARE_SEARCH_RADIUS, disables their guidance (see VehicleProjectileEntity's own tudursvehiclemod$disableGuidanceFromFlare() doc - each then flies unguided for a few seconds before self-destructing, same as a genuinely lost target), and spawns a flareType-differentiated visual effect. A no-op for a vehicle with flareType==0 (no flare capability defined at all), same convention as tudursvehiclemod$toggleSearchLight()'s own empty-searchLightParts no-op - so the network round trip this is called from costs nothing extra for those. Server-side only (missile guidance itself, and the entities being searched for, are both server-authoritative). */
	private static final double FLARE_SEARCH_RADIUS = 256.0;

	public void tudursvehiclemod$deployFlares() {
		int flareType = this.getDefinition().flareType();
		if (flareType <= 0 || !(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		net.minecraft.util.math.Box searchBox = this.getBoundingBox().expand(FLARE_SEARCH_RADIUS);
		for (Entity candidate : serverWorld.getOtherEntities(this, searchBox,
				e -> e instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity)) {
			com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity missile =
					(com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity) candidate;
			if (missile.tudursvehiclemod$isGuidingTowardEntity(this.getId())) {
				missile.tudursvehiclemod$disableGuidanceFromFlare();
			}
		}
		tudursvehiclemod$spawnFlareParticles(serverWorld, flareType);
	}

	/** The purely visual side of tudursvehiclemod$deployFlares() - per that method's own doc, FlareType's own 1-10 values only ever change THIS (guidance disabling itself is identical regardless of which value is configured). Directions are built from this vehicle's own current body orientation (tudursvehiclemod$getBodyOrientation()), so "lateral"/"frontal"/"downward" are always relative to however this vehicle is actually facing right now, not a fixed world-space direction. */
	private void tudursvehiclemod$spawnFlareParticles(ServerWorld serverWorld, int flareType) {
		org.joml.Quaternionf bodyOrientation = this.tudursvehiclemod$getBodyOrientation();
		Vec3d center = this.getEntityPos().add(0.0, this.getHeight() * 0.5, 0.0);
		switch (flareType) {
			case 2 -> {
				// Large-aircraft flare - same forward/rear scatter as the plain flare (case 1, the default branch below) but a noticeably bigger burst, per FlareType's own doc distinguishing "大型機向け" (for large aircraft) from the plain "有り" (present) case.
				tudursvehiclemod$spawnFlareBurst(serverWorld, center, bodyOrientation, new Vector3f(0, 0, 1), 24);
				tudursvehiclemod$spawnFlareBurst(serverWorld, center, bodyOrientation, new Vector3f(0, 0, -1), 24);
			}
			case 3 -> {
				// Lateral - scattered to both sides.
				tudursvehiclemod$spawnFlareBurst(serverWorld, center, bodyOrientation, new Vector3f(1, 0, 0), 12);
				tudursvehiclemod$spawnFlareBurst(serverWorld, center, bodyOrientation, new Vector3f(-1, 0, 0), 12);
			}
			case 4 ->
				// Frontal - scattered forward only.
					tudursvehiclemod$spawnFlareBurst(serverWorld, center, bodyOrientation, new Vector3f(0, 0, -1), 16);
			case 5 ->
				// Downward - scattered below.
					tudursvehiclemod$spawnFlareBurst(serverWorld, center, bodyOrientation, new Vector3f(0, -1, 0), 16);
			case 10 -> {
				// Tank smoke discharger - a dense, lingering smoke screen rather than bright flares at all, spawned all around rather than in any one particular direction.
				for (int i = 0; i < 20; i++) {
					double angle = serverWorld.random.nextDouble() * Math.PI * 2.0;
					double dist = 1.5 + serverWorld.random.nextDouble() * 2.0;
					serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, true, true,
							center.x + Math.cos(angle) * dist, this.getEntityPos().y + 0.3, center.z + Math.sin(angle) * dist,
							1, 0.1, 0.05, 0.1, 0.01);
				}
			}
			default ->
				// Plain flare (FlareType=1, or any other unlisted 1-10 value) - scattered to the rear, the classic countermeasure-dispenser look.
					tudursvehiclemod$spawnFlareBurst(serverWorld, center, bodyOrientation, new Vector3f(0, 0, 1), 16);
		}
	}

	/** Spawns `count` FLAME particles, each with an independently randomized velocity within a cone around localDirection (transformed by bodyOrientation into world space) - the shared burst logic behind every flareType branch above except the smoke-discharger case (10), which looks nothing like a directional flare burst at all. */
	private void tudursvehiclemod$spawnFlareBurst(ServerWorld serverWorld, Vec3d center, org.joml.Quaternionf bodyOrientation, Vector3f localDirection, int count) {
		for (int i = 0; i < count; i++) {
			Vector3f spread = new Vector3f(
					localDirection.x + (serverWorld.random.nextFloat() - 0.5f) * 0.8f,
					localDirection.y + (serverWorld.random.nextFloat() - 0.5f) * 0.8f,
					localDirection.z + (serverWorld.random.nextFloat() - 0.5f) * 0.8f);
			bodyOrientation.transform(spread);
			double speed = 0.3 + serverWorld.random.nextDouble() * 0.3;
			serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, true, true,
					center.x, center.y, center.z, 1,
					spread.x * speed, spread.y * speed, spread.z * speed, 0.05);
		}
	}

	/** Flips this vehicle's own nav light(s) on/off, the same one-flag-for-the-whole-set shape tudursvehiclemod$toggleSearchLight() uses. A no-op for a vehicle with no nav lights defined at all, for the same reason that method is. */
	public void tudursvehiclemod$toggleNavLights() {
		if (getDefinition().navLightParts().isEmpty()) {
			return;
		}
		this.dataTracker.set(NAV_LIGHTS_ON, !this.dataTracker.get(NAV_LIGHTS_ON));
	}

	/** Whether this vehicle's own nav light(s) are currently on, read by SearchLightIllumination's own point-light collection. */
	public boolean tudursvehiclemod$areNavLightsOn() {
		return this.dataTracker.get(NAV_LIGHTS_ON);
	}

	public boolean tryFireWeapon(int weaponIndex, ServerPlayerEntity shooter) {
		VehicleDefinition def = getDefinition();
		if (weaponIndex < 0 || weaponIndex >= def.weapons().size()) {
			return false;
		}
		WeaponDefinition weapon = def.weapons().get(weaponIndex);
		if (!this.tudursvehiclemod$canFireWeapons(java.util.Optional.of(weapon))) {
			return false;
		}
		// A CAS/Carrier formation's own ACTUAL size (clamped to available ammo - see the ammo-consumption block below for exactly how) - defaults to 1 (no formation) for every other weapon type, and is only ever set otherwise for CAS/CARRIER.
		int actualFormationSize = 1;
		// Type=Dummy is an entirely empty
		// weapon slot - selectable in the weapon list (e.g. alongside a
		// WeaponBay for purely cosmetic/display tuning) but firing it
		// does nothing at all - no projectile, no sound, no cooldown, not
		// even a fire-attempt/spin-tracking record (there's no
		// AddPartRotWeapon-style spin that would make sense on a weapon
		// that never actually fires anything).
		if (weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.DUMMY) {
			return false;
		}
		// AddPartRotWeapon's own gatling-style spin -
		// see WeaponPart's own spinsWhileFiring doc): recorded here,
		// BEFORE the cooldown gate below, so a spinning barrel keeps
		// spinning continuously for as long as the fire key is actually
		// held (the client sends this payload every tick while held - see
		// VehicleModClient's own fireWeaponKey.isPressed() doc), rather
		// than only during the instant a shot actually fires and briefly
		// stopping between rounds whenever the weapon's own cooldown
		// blocks the next shot.
		this.lastFireAttemptTick.put(weaponIndex, this.age);
		if (weaponIndex < 32) {
			this.dataTracker.set(FIRING_WEAPON_BITMASK, this.dataTracker.get(FIRING_WEAPON_BITMASK) | (1 << weaponIndex));
		}
		if (this.weaponCooldowns.length != def.weapons().size()) {
			this.weaponCooldowns = new int[def.weapons().size()];
		}
		if (this.weaponCooldowns[weaponIndex] > 0) {
			return false;
		}

		if (this.weaponSoundCooldowns.length != def.weapons().size()) {
			this.weaponSoundCooldowns = new int[def.weapons().size()];
		}

		// Shooter is null for that call specifically (there's no player firing an autonomous strike aircraft's own weapon) - seat validation makes no sense at all without an actual occupant to check, so it's skipped entirely (always allowed) in that case.
		if (shooter != null) {
			int seatIndex = this.tudursvehiclemod$getAssignedSeatIndex(shooter);
			if (seatIndex != weapon.seatIndex()) {
				// Per Readme_Aircraft.txt's own doc for AddWeapon's own pilotUsable+seat pair ("true, N" -> seat N's occupant can use it, falling back to the pilot when seat N is empty): the pilot may still fire a gunner-seat weapon if no one is actually in that gunner seat right now.
				boolean pilotFallbackEligible = seatIndex == 0 && weapon.pilotUsable()
						&& this.tudursvehiclemod$getSeatOccupant(weapon.seatIndex()) == null;
				if (!pilotFallbackEligible) {
					return false; // shooter isn't sitting in the seat this weapon is bound to
				}
			}
			// Still within the post-mount fire lockout.
			if (seatIndex < this.seatMountGraceTicks.length && this.seatMountGraceTicks[seatIndex] > 0) {
				return false;
			}
		}

		// Only Torpedo requires a near-level attitude to fire - Bomb/Depth are droppable at any attitude (including a steep dive), matching Readme_Weapon.txt's own documented restriction (which names Torpedo specifically, not Bomb).
		if (weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.TORPEDO) {
			float pitchTilt = Math.abs(this.getPitch());
			float rollTilt = this instanceof AircraftEntity aircraft ? Math.abs(aircraft.getRoll()) : 0f;
			if (pitchTilt > BOMB_MAX_TILT_DEGREES || rollTilt > BOMB_MAX_TILT_DEGREES) {
				return false;
			}
		}
		if (weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.TORPEDO
				&& !this.tudursvehiclemod$isNearSurfaceForTorpedo()) {
			return false;
		}
		// Previously, firing an AAMissile/ATMissile before lock completed still launched an unguided shot (matching Readme_Weapon.txt's own documented "you can pull the trigger before tone, you just don't get a guided shot" behavior) - now blocks firing entirely instead, so pressing the fire key does nothing at all until this weapon's own lock actually completes. Only applies to player-initiated fire (shooter != null) - CAS auto-fire (shooter == null) can never lock a target at all (see tudursvehiclemod$updateMissileLockOnIndicators()'s own shooter-seat requirement), so leaving this unrestricted there would make the weapon entirely unusable for CAS; that path already fires unguided by design (see tryFireWeapon()'s own AA_MISSILE/AT_MISSILE/MISSILE case doc), unaffected by this change. shares this exact same lock-required restriction.
		if (shooter != null && (weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.AA_MISSILE
				|| weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.AT_MISSILE
				|| weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.MISSILE)) {
			Integer lockProgress = this.tudursvehiclemod$missileLockProgressTicks.get(weaponIndex);
			int requiredTicks = this.tudursvehiclemod$missileLockRequiredTicks.getOrDefault(weaponIndex, weapon.lockTimeTicks());
			boolean lockComplete = lockProgress != null && lockProgress >= requiredTicks;
			if (!lockComplete) {
				return false;
			}
		}

		// Refuses to fire at all (before any ammo/cooldown/heat is consumed below) while this weapon's own tracking part hasn't yet settled onto its current target - see tudursvehiclemod$isTurretUnsettled()'s own doc.
		if (this.tudursvehiclemod$isTurretUnsettled(weapon, def, shooter)) {
			return false;
		}

		this.tudursvehiclemod$ensureWeaponAmmoArraysSized(def);
		int magazineSize = weapon.magazineSize();
		// Heat (HeatCount/MaxHeatCount) and magazine (Round/MaxAmmo/ ReloadTime) are two INDEPENDENT systems in MC Heli's own format.
		if (weapon.isHeatBased() && this.weaponHeat[weaponIndex] >= weapon.maxHeat()) {
			return false; // overheated
		}
		if (magazineSize > 0 && this.weaponReloadTicksRemaining[weaponIndex] > 0) {
			return false; // still reloading
		}
		// A magazine that's already empty AND
		// not currently mid-reload (this only happens once the reserve -
		// see WeaponDefinition's own maxAmmo() doc - has run out and an
		// earlier reload completed without being able to refill
		// anything at all) is a genuine, permanent "out of ammo" state
		// until resupplied - blocked here outright, rather than letting
		// this fall through and spuriously decrement an already-empty
		// magazine below.
		if (magazineSize > 0 && this.weaponAmmo[weaponIndex] <= 0) {
			return false;
		}

		// Recoil (part_type=2) needs to trigger only on
		// an ACTUAL shot, not merely a fire ATTEMPT blocked by this
		// weapon's own cooldown/Delay, reload, heat, or ammo state - unlike
		// AddPartRotWeapon's own continuous gatling-style spin
		// (FIRING_WEAPON_BITMASK, which intentionally DOES stay "on"
		// through a blocked attempt, so the barrel keeps spinning
		// smoothly between rounds rather than stuttering), a cannon-style
		// weapon with real spacing between shots should only ever recoil
		// once PER shot that actually fires. This used to be set right
		// after only the COOLDOWN check above, before the reload/heat/
		// ammo checks further above had even run yet - meaning firing
		// during an active RELOAD still triggered recoil even though the
		// shot itself was blocked and never actually happened. Moved here,
		// after every single early-return guard in this whole method,
		// so it's only ever set for a shot that's genuinely about to
		// fire. See tudursvehiclemod$updateWeaponPartRecoil()'s own doc
		// for how this gets consumed.
		if (weaponIndex < 32) {
			this.dataTracker.set(ACTUAL_FIRE_BITMASK, this.dataTracker.get(ACTUAL_FIRE_BITMASK) | (1 << weaponIndex));
			this.lastActualFireTick.put(weaponIndex, this.age);
		}
		if (weapon.hasRecoil()) {
			this.tudursvehiclemod$startRecoilShake(weapon);
		}

		boolean ammoStateChanged = false;
		if (weapon.isHeatBased()) {
			// Cooldown happens continuously in updateWeaponHeat(), not here.
			this.weaponHeat[weaponIndex] += weapon.heatPerShot();
			ammoStateChanged = true;
		}
		// A CAS/Carrier formation's own ACTUAL size - this weapon's own configured formation size, clamped down to whatever ammo is actually available (a weapon with unlimited ammo, magazineSize<=0, always gets its own full configured size - there's no "not enough ammo" concern there at all). Computed BEFORE the magazine branch below, since it needs to know configuredFormationSize regardless of whether this weapon even has a magazine.
		if (weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.CAS
				|| weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.CARRIER) {
			int configuredFormationSize = weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.CAS
					? weapon.casStrike().map(com.example.tudursvehiclemod.asset.CasStrikeConfig::formationSize).orElse(1)
					: weapon.carrierAircraft().map(com.example.tudursvehiclemod.asset.CarrierAircraftConfig::formationSize).orElse(1);
			actualFormationSize = magazineSize > 0
					? Math.max(1, Math.min(configuredFormationSize, this.weaponAmmo[weaponIndex]))
					: configuredFormationSize;
		}
		if (magazineSize > 0) {
			this.weaponAmmo[weaponIndex]--;
			if (weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.DROP_TANK) {
				this.tudursvehiclemod$clampFuelToMaxFuel();
			}
			// A CAS/Carrier formation consumes ammo equal to actualFormationSize (computed just above) - one round was already spent by the decrement just above this comment (representing the lead aircraft), so only (actualFormationSize - 1) MORE is taken here, for the remaining wingmen. Consumed here, before the reload-triggering check just below, so a formation that empties the magazine reloads immediately rather than only on the next shot.
			if (actualFormationSize > 1) {
				this.weaponAmmo[weaponIndex] = Math.max(0, this.weaponAmmo[weaponIndex] - (actualFormationSize - 1));
			}
			if (this.weaponAmmo[weaponIndex] <= 0) {
				this.weaponAmmo[weaponIndex] = 0;
				if (weapon.reloadTicks() > 0) {
					this.weaponReloadTicksRemaining[weaponIndex] = weapon.reloadTicks();
					tudursvehiclemod$applyGroupReload(def, weapon, weaponIndex);
				} else {
					// Instant reload (ReloadTime = 0, explicitly valid
					// per MC Heli's own documented format) - per the same
					// direct clarification as updateWeaponReloads()'s own
					// doc, this ALSO has to respect a genuinely limited
					// reserve instead of unconditionally refilling to
					// magazineSize outright.
					if (this.weaponReserveAmmo[weaponIndex] < 0) {
						this.weaponAmmo[weaponIndex] = magazineSize;
					} else {
						int actuallyAdded = Math.min(magazineSize, this.weaponReserveAmmo[weaponIndex]);
						this.weaponAmmo[weaponIndex] = actuallyAdded;
						this.weaponReserveAmmo[weaponIndex] -= actuallyAdded;
					}
				}
			}
			ammoStateChanged = true;
		}
		if (ammoStateChanged) {
			this.tudursvehiclemod$syncWeaponAmmo();
		}

		// Dispenser now uses a REAL projectile (see
		// VehicleProjectileEntity's own tudursvehiclemod$dispenseIfConfigured()
		// doc) instead of being a no-projectile special case - so it
		// automatically gets Bomblet-based wide-area scatter, cooldown,
		// magazine, and sound, the exact same way every OTHER weapon type
		// already does, rather than needing its own separate,
		// hand-simulated implementation of each of those. Smoke/
		// TargetingPod still have no projectile/bullet model at all - each
		// does its own thing entirely (see WeaponType's own doc for
		// exactly what) and then returns here, still having consumed
		// ammo/cooldown/heat above and still playing this weapon's own
		// sound below, same as any ordinary shot - just never actually
		// spawning a VehicleProjectileEntity.
		if (weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.SMOKE
				|| weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.TARGETING_POD
				|| weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.CAS
				|| weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.CARRIER) {
			// This line was missing entirely, despite
			// this method's own doc comment already (incorrectly) claiming
			// cooldown was handled - meaning these 2 weapon types could
			// actually be "fired" every single tick the fire key was held,
			// completely ignoring their own configured Delay/cooldownTicks.
			this.weaponCooldowns[weaponIndex] = weapon.cooldownTicks();
			tudursvehiclemod$applyGroupCooldown(def, weapon, weaponIndex);
			WeaponSpawnInfo noProjectileSpawnInfo = tudursvehiclemod$computeWeaponSpawnPos(def, weapon, weaponIndex);
			Vec3d noProjectileSpawnPos = noProjectileSpawnInfo.pos();
			if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
				switch (weapon.weaponType()) {
					case SMOKE -> tudursvehiclemod$fireSmoke(weapon, serverWorld, noProjectileSpawnPos);
					case TARGETING_POD -> {
						if (shooter != null) {
							tudursvehiclemod$fireTargetingPod(weapon, shooter);
						}
					}
					// Ignoring obstacles entirely - see tudursvehiclemod$computeBallisticTargetPoint()'s own doc.
					case CAS -> {
						if (shooter != null) {
							Vec3d shooterViewDir = shooter.getRotationVec(1.0f);
							tudursvehiclemod$fireCasStrike(weapon, serverWorld, tudursvehiclemod$computeCasCarrierTargetPoint(shooter, weapon), shooterViewDir.x, shooterViewDir.z, actualFormationSize, shooter, weaponIndex);
						}
					}
					// Unlike CAS, spawns at THIS weapon's own AddWeapon mount position (noProjectileSpawnPos, already computed above) rather than a distant marked point - the ballistic target point still serves as the route's own relative-coordinate center, exactly like CAS's own target point does. See tudursvehiclemod$fireCarrierLaunch()'s own doc.
					case CARRIER -> {
						if (shooter != null) {
							Vec3d shooterViewDir = shooter.getRotationVec(1.0f);
							tudursvehiclemod$fireCarrierLaunch(weapon, serverWorld, noProjectileSpawnPos, tudursvehiclemod$computeCasCarrierTargetPoint(shooter, weapon), shooterViewDir.x, shooterViewDir.z, shooter, weaponIndex, actualFormationSize);
						}
					}
					default -> {
					}
				}
			}
			tudursvehiclemod$playWeaponFireSound(weapon, weaponIndex, noProjectileSpawnPos, shooter);
			return true;
		}

		net.minecraft.item.Item projectileItem = Registries.ITEM.get(weapon.projectileItem());
		// Per Readme_Weapon.txt's own ModeNum doc: for a MachineGun-type
		// weapon with a second mode configured, mode 0 (the default)
		// fires a plain, non-explosive round regardless of this
		// weapon's own configured Explosion - mode 1 is the "HE round"
		// toggle, actually applying it. A weapon with only one mode
		// (hasModes() false) always uses its own configured Explosion
		// directly, exactly as before ModeNum existed at all.
		this.tudursvehiclemod$ensureWeaponAmmoArraysSized(def);
		boolean isMachineGunSecondMode = weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.MACHINE_GUN
				&& weapon.hasModes() && this.weaponMode[weaponIndex] == 1;
		boolean isMachineGunWithModes = weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.MACHINE_GUN
				&& weapon.hasModes();
		float effectiveExplosionPower = (isMachineGunWithModes && !isMachineGunSecondMode) ? 0f : weapon.explosionPower();
		boolean effectiveExplosionDestroysBlocks = (isMachineGunWithModes && !isMachineGunSecondMode) ? false : weapon.explosionDestroysBlocks();
		boolean effectiveFlaming = (isMachineGunWithModes && !isMachineGunSecondMode) ? false : weapon.flaming();
		VehicleProjectileEntity projectile;
		if (weapon.bulletModel().isPresent() && weapon.bulletTexture().isPresent()) {
			projectile = new com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity(
					this.getEntityWorld(), shooter, projectileItem.getDefaultStack(), weapon.damage(), weapon.gravity(),
					effectiveExplosionPower, effectiveExplosionDestroysBlocks, effectiveFlaming,
					weapon.bulletModel().get(), weapon.bulletTexture().get(), weapon.bulletScale());
		} else {
			projectile = new VehicleProjectileEntity(
					this.getEntityWorld(), shooter, projectileItem.getDefaultStack(), weapon.damage(), weapon.gravity(),
					effectiveExplosionPower, effectiveExplosionDestroysBlocks, effectiveFlaming);
		}
		projectile.tudursvehiclemod$setExplosionPowerInWater(isMachineGunWithModes && !isMachineGunSecondMode ? 0f : weapon.explosionPowerInWater());
		projectile.tudursvehiclemod$setExplosionBlockPower(weapon.explosionBlockPower());
		projectile.tudursvehiclemod$setExplosionAltitude(weapon.explosionAltitude());
		projectile.tudursvehiclemod$setFuseTicks(weapon.delayFuseTicks(), weapon.timeFuseTicks());
		projectile.tudursvehiclemod$setBounceStrength(weapon.bounceStrength());
		projectile.tudursvehiclemod$setGravityInWater(weapon.gravityInWater());
		projectile.tudursvehiclemod$setGuidedTorpedo(weapon.guidedTorpedo());
		projectile.tudursvehiclemod$setPiercingCount(weapon.piercingCount());
		projectile.tudursvehiclemod$setFuelAirExplosive(weapon.fuelAirExplosive());
		projectile.tudursvehiclemod$setTrajectoryParticle(weapon.trajectoryParticle(), weapon.trajectoryParticleStartTick(), weapon.disableSmoke());
		projectile.tudursvehiclemod$setBulletColors(weapon.bulletColor(), weapon.bulletColorInWater());
		projectile.tudursvehiclemod$setFiringVehicle(this);
		projectile.tudursvehiclemod$setExplodeOnWaterContact(
				weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.BOMB
						|| weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.DROP_TANK);
		projectile.tudursvehiclemod$setSplashScale(weapon.bulletScale());
		// Dispenser is now a REAL projectile (see
		// VehicleProjectileEntity's own tudursvehiclemod$dispenseIfConfigured()
		// doc) - set unconditionally here, same as every other per-shot
		// property above (empty/default for every OTHER weapon type,
		// which never actually checks this at all).
		projectile.tudursvehiclemod$setDispenseItem(weapon.dispenseItem(), weapon.dispenseRange());
		// Per Readme_Weapon.txt's own ModeNum doc: for a Rocket-type
		// weapon with a second mode configured, mode 1 is the "HEIAP
		// round" toggle - scatters this weapon's own configured Bomblet
		// submunitions in the air, reusing the exact same mechanism as a
		// weapon that always has Bomblet configured (see
		// tudursvehiclemod$updateBombletDeployment()'s own doc) - mode 0
		// (the default) is a plain, non-scattering rocket instead, even
		// if Bomblet happens to be configured, same "explicit toggle
		// gates whether an otherwise-always-configured effect actually
		// applies" idea as MachineGun2's own HE-round toggle above. A
		// Rocket with only one mode (hasModes() false) always scatters
		// its own configured Bomblet directly, exactly as before ModeNum
		// existed at all.
		boolean isRocketFirstMode = weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.ROCKET
				&& weapon.hasModes() && this.weaponMode[weaponIndex] == 0;
		if (weapon.hasBomblets() && !isRocketFirstMode) {
			projectile.tudursvehiclemod$setBomblets(weapon.bombletCount(), weapon.bombletDeployTicks(),
					weapon.bombletSpreadRate(), weapon.bombletModel(), weapon.bombletTexture());
		}

		// Cycle through this weapon's own firing positions, one per shot (wrapping back to the first after the last).
		WeaponSpawnInfo spawnInfo = tudursvehiclemod$computeWeaponSpawnPos(def, weapon, weaponIndex);
		Vec3d spawnPos = spawnInfo.pos();
		com.example.tudursvehiclemod.asset.WeaponOffset currentOffset = spawnInfo.offset();
		Quaternionf bodyOrientation = this.tudursvehiclemod$getBodyOrientation();
		projectile.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);

		Vec3d finalVelocity;
		if (weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.BOMB
				|| weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.DEPTH
				|| weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.DROP_TANK) {
			// "機体の速度を引き継いだうえで落下" - a bomb has no propulsion of its own; it's just dropped, inheriting the firing vehicle's own current momentum before gravity takes over.
			finalVelocity = this.getVelocity();
		} else if (weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.TORPEDO) {
			// A torpedo fired from a STATIONARY submarine
			// launched at completely the wrong angle - it was sharing the
			// BOMB branch above, so with near-zero vehicle velocity it had
			// essentially no propulsion of its own at all and just fell
			// straight down under gravity, ignoring its own configured
			// mount angle entirely. Unlike a dropped bomb, a torpedo IS
			// self-propelled at launch (see WeaponStats's own
			// accelerationInWater doc for its SEPARATE underwater-cruise
			// speed, which is different from this initial launch speed) -
			// so it needs its own configured direction and weapon.velocity()
			// applied, same as the plain mounted-direction branch below,
			// with the firing vehicle's own velocity still added on top for
			// realistic momentum carry-through.
			Vec3d localDir = Vec3d.fromPolar((float) currentOffset.mountPitch(), (float) currentOffset.mountYaw());
			Vector3f worldDir = new Vector3f((float) localDir.x, (float) localDir.y, (float) localDir.z);
			bodyOrientation.transform(worldDir);
			Vec3d aim = new Vec3d(worldDir.x, worldDir.y, worldDir.z);
			finalVelocity = aim.multiply(weapon.velocity()).add(this.getVelocity());
		} else if (weapon.aimRange().isPresent()) {
			// Tracks wherever the shooter is currently looking, clamped to this weapon's own aim range - UNLESS the shooter is the pilot of a vehicle with a toggleable free-look mode (Aircraft/Submarine) while NOT actually free-looking, in which case the shooter's own view directly drives the vehicle's own steering instead (see followPilotView()/followPilotViewGrounded()'s own doc) rather than reflecting any independent aim - firing straight along this weapon's own DefaultYaw/pitch=0 instead in that case, same idea as tudursvehiclemod$getPartStageRotation()'s own doc for the visual weapon part.
			com.example.tudursvehiclemod.asset.WeaponAimRange range = weapon.aimRange().get();
			boolean pilotSteeringViaFreeLook = shooter != null && this instanceof FreeCameraVehicle freeCameraVehicle
					&& !freeCameraVehicle.tudursvehiclemod$isEffectiveFreeLook(shooter);
			double clampedYaw;
			double clampedPitch;
			if (pilotSteeringViaFreeLook) {
				clampedYaw = range.defaultYaw();
				clampedPitch = 0.0;
			} else {
				// Settling was already confirmed by tudursvehiclemod$isTurretUnsettled() earlier in this same call (returned false if not) - reuses the exact same effective-aim computation the HUD's own turret indicator does, so the two can never disagree about where this weapon is actually pointing.
				double[] effectiveAim = this.tudursvehiclemod$getEffectiveWeaponAim(weapon);
				clampedYaw = effectiveAim[0];
				clampedPitch = effectiveAim[1];
			}
			Vec3d clampedLocalDir = Vec3d.fromPolar((float) clampedPitch, (float) clampedYaw);
			Vector3f worldDir = new Vector3f((float) clampedLocalDir.x, (float) clampedLocalDir.y, (float) clampedLocalDir.z);
			bodyOrientation.transform(worldDir);
			Vec3d aim = new Vec3d(worldDir.x, worldDir.y, worldDir.z);
			finalVelocity = aim.multiply(weapon.velocity()).add(this.getVelocity());
			// Car-mounted weapons exploded almost
			// directly below the vehicle regardless of aim, with yaw only
			// seeming to shift the muzzle's own spawn position and pitch
			// having no visible effect at all - logged once per (vehicle
		} else {
			// No aim range at all.
			Vec3d localDir = Vec3d.fromPolar((float) currentOffset.mountPitch(), (float) currentOffset.mountYaw());
			Vector3f worldDir = new Vector3f((float) localDir.x, (float) localDir.y, (float) localDir.z);
			bodyOrientation.transform(worldDir);
			Vec3d aim = new Vec3d(worldDir.x, worldDir.y, worldDir.z);
			finalVelocity = aim.multiply(weapon.velocity()).add(this.getVelocity());
		}
		// Per Readme_Weapon.txt's own Accuracy doc: a small random angular
		// error applied ONCE, right here at the moment of firing, after
		// every branch above has already settled on its own intended
		// aim direction - rather than duplicating this in each branch
		// individually, applying it uniformly to whatever finalVelocity
		// actually ended up being covers all of them (bomb/torpedo/
		// aim-range-tracking/fixed-direction alike) with a single check.
		// 0 (the default, absent from the file) applies no spread at
		// all - the original, perfectly-accurate behavior before this
		// field existed.
		if (weapon.accuracyDegrees() > 0f) {
			finalVelocity = tudursvehiclemod$applyAccuracySpread(finalVelocity, weapon.accuracyDegrees());
		}
		projectile.setVelocity(finalVelocity);
		// Explicitly set the projectile's own yaw/pitch to match its
		// actual flight direction, rather than leaving them at their
		// freshly-constructed default of 0/0. The symptom was a
		// projectile's own displayed model persistently drifting away
		// from its actual travel direction: setAngles() (rather than
		// separate setYaw()/setPitch() calls) ALSO snaps this
		// projectile's own lastYaw/lastPitch (the render-interpolation
		// baseline getYaw(tickProgress)/getPitch(tickProgress) actually
		// lerp from - see VehicleProjectileEntity's own tick() doc for
		// the full explanation) to match immediately, rather than
		// leaving THOSE at their own separate 0/0 default - avoiding
		// even a brief interpolation artifact on this projectile's very
		// first rendered frame(s), before its own first tick() has run
		// even once.
		double speed = finalVelocity.length();
		if (speed > 1.0E-6) {
			float initialPitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(finalVelocity.y / speed, -1.0, 1.0)));
			float initialYaw = (float) Math.toDegrees(Math.atan2(-finalVelocity.x, finalVelocity.z));
			projectile.setAngles(initialYaw, initialPitch);
		}

		// Guidance/special-behavior setup.
		switch (weapon.weaponType()) {
			case TORPEDO -> projectile.tudursvehiclemod$setUnderwaterCruiseCapable(
					weapon.accelerationInWater(), weapon.velocityInWater(), weapon.targetDepthOffset());
			// MkRocket behaves exactly like AS_MISSILE (homes toward the ground point marked under the shooter's own crosshair at fire time) - MC Heli documents these as separate Type names, but the actual guidance is identical. Per a further direct clarification, guided types don't need to work for CAS auto-fire (shooter=null there) - skips applying guidance (unguided/straight flight) rather than NPEing on a null shooter.
			case AS_MISSILE, MK_ROCKET -> {
				if (shooter != null) {
					projectile.tudursvehiclemod$setGuidanceTargetPos(
							tudursvehiclemod$raycastGroundPoint(shooter), weapon.turnRateDegreesPerTick());
				}
			}
			// Per WeaponType.AS_WEAPON's own doc: same fixed-point homing as AS_MISSILE/MK_ROCKET above, plus this weapon's own DiveDistance/AccelerationInWater/VelocityInWater/TargetDepth (the exact same underwater fields TORPEDO uses, reusing the existing configuration rather than adding a separate set) so the projectile dives into torpedo-style underwater cruise once close enough to the target - see VehicleProjectileEntity's own tudursvehiclemod$updateGuidance() doc for exactly how the two phases connect.
			case AS_WEAPON -> {
				if (shooter != null) {
					projectile.tudursvehiclemod$setGuidanceTargetPos(
							tudursvehiclemod$raycastGroundPoint(shooter), weapon.turnRateDegreesPerTick());
					projectile.tudursvehiclemod$setAntiSubmarineDive(
							weapon.diveDistance(), weapon.accelerationInWater(), weapon.velocityInWater(), weapon.targetDepthOffset());
				}
			}
			case AA_MISSILE, AT_MISSILE, MISSILE -> {
				projectile.tudursvehiclemod$setMissileGuidanceTuning(weapon.rigidityTimeTicks(), weapon.proximityFuseDist());
				// Per Readme_Weapon.txt's own ModeNum doc: for an ATMissile with a second mode configured, mode 1 is the "TAモード" (Top Attack) toggle - climbs above the target before diving steeply, rather than heading straight at it. Mode 0 (or a weapon with only one mode) flies a normal, direct intercept course.
				boolean isTopAttackMode = weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.AT_MISSILE
						&& weapon.hasModes() && this.weaponMode[weaponIndex] == 1;
				projectile.tudursvehiclemod$setTopAttack(isTopAttackMode);
				// Firing is now blocked entirely upstream (see this method's own new lock-completion check near its own top) unless this weapon's own lock has already completed, so shooter != null here already implies that. Re-finds the target (rather than reusing whatever was locked when tudursvehiclemod$updateMissileLockOnIndicators() last ran, up to a tick stale) purely for freshness - null here would only happen in the rare case the shooter's own crosshair left the target in the same tick fire was pressed, in which case this simply fires unguided rather than blocking the shot after ammo/cooldown have already been consumed.
				if (shooter != null) {
					Entity lockOnTarget = tudursvehiclemod$findLockOnTarget(shooter, weapon);
					if (lockOnTarget != null) {
						projectile.tudursvehiclemod$setGuidanceTargetEntity(lockOnTarget.getId(), weapon.turnRateDegreesPerTick());
					}
				}
			}
			// Per Readme_Weapon.txt's own TVMissile doc: mode 0 (the
			// default) fires exactly like an ordinary unguided shot along
			// this weapon's own aim direction, with the shooter steering
			// it directly afterward (see VehicleProjectileEntity's own
			// tudursvehiclemod$setTvControlled() doc). A TVMissile with a
			// second mode configured switches to mode 1 ("通常誘導弾" -
			// a normal guided munition, NOT missile-view-controlled)
			// instead - homes to the ground point marked under the
			// shooter's own crosshair at fire time, exactly like
			// AS_MISSILE/MK_ROCKET's own guidance, with no player control
			// Handed off at all. guided types don't need to work for CAS auto-fire (shooter=null there) - skips entirely in that case (fires unguided/straight).
			case TV_MISSILE -> {
				if (shooter != null) {
					boolean isNormalGuidedMode = weapon.hasModes() && this.weaponMode[weaponIndex] == 1;
					if (isNormalGuidedMode) {
						projectile.tudursvehiclemod$setGuidanceTargetPos(
								tudursvehiclemod$raycastGroundPoint(shooter), weapon.turnRateDegreesPerTick());
					} else {
						projectile.tudursvehiclemod$setTvControlled(shooter);
					}
				}
			}
			default -> {
				// MACHINE_GUN/ROCKET/BOMB/OTHER.
			}
		}

		this.getEntityWorld().spawnEntity(projectile);
		projectile.tudursvehiclemod$forceLoadSpawnChunk();
		this.weaponCooldowns[weaponIndex] = weapon.cooldownTicks();
		tudursvehiclemod$applyGroupCooldown(def, weapon, weaponIndex);

		tudursvehiclemod$playWeaponFireSound(weapon, weaponIndex, spawnPos, shooter);
		tudursvehiclemod$spawnMuzzleEffects(weapon, spawnPos, finalVelocity);

		// Destruct (Bomb type, UAV helicopter only): self-destructs after the bomb drops, reusing onDestroyed()'s existing explosion/ejection logic.
		if (weapon.destruct() && weapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.BOMB
				&& this.tudursvehiclemod$isUav() && !this.tudursvehiclemod$isDestroyed()
				&& this.getEntityWorld() instanceof ServerWorld destructWorld) {
			this.tudursvehiclemod$onDestroyed(destructWorld);
		}

		return true;
	}

	/** Spawns AddMuzzleFlash/AddMuzzleFlashSmoke/SetCartridge one-time firing effects, positioned distanceFromMuzzle ahead of spawnPos along finalVelocity - each independent, no-op if not configured. Server-side only. */
	private void tudursvehiclemod$spawnMuzzleEffects(WeaponDefinition weapon, Vec3d spawnPos, Vec3d finalVelocity) {
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		double speed = finalVelocity.length();
		Vec3d forward = speed > 1.0E-6 ? finalVelocity.multiply(1.0 / speed) : new Vec3d(0, 0, 1);

		weapon.muzzleFlash().ifPresent(flash -> {
			Vec3d pos = spawnPos.add(forward.multiply(flash.distanceFromMuzzle()));
			int rgb = flash.argbColor() & 0xFFFFFF;
			net.minecraft.particle.DustParticleEffect effect = new net.minecraft.particle.DustParticleEffect(rgb, flash.size());
			int count = Math.max(1, flash.displayTicks());
			serverWorld.spawnParticles(effect, true, true, pos.x, pos.y, pos.z, count, 0.0, 0.0, 0.0, 0.0);
		});

		weapon.muzzleFlashSmoke().ifPresent(smoke -> {
			Vec3d pos = spawnPos.add(forward.multiply(smoke.distanceFromMuzzle()));
			int rgb = smoke.argbColor() & 0xFFFFFF;
			net.minecraft.particle.DustParticleEffect effect = new net.minecraft.particle.DustParticleEffect(rgb, smoke.size() * 0.2f);
			serverWorld.spawnParticles(effect, true, true, pos.x, pos.y, pos.z, smoke.count(),
					smoke.spreadRange() * 0.1, smoke.spreadRange() * 0.1, smoke.spreadRange() * 0.1, 0.02);
		});

		weapon.cartridge().ifPresent(cart -> {
			// Per this field's own doc: purely cosmetic, non-damaging -
			// reuses VehicleProjectileEntity purely as a convenient
			// physics-driven cosmetic model carrier (0 damage, no
			// explosion configured at all), rather than introducing a
			// whole separate entity class just for this.
			net.minecraft.item.Item cartridgeItem = Registries.ITEM.get(weapon.projectileItem());
			// Ejection wasn't actually happening
			// at all: this used to build the model/texture identifiers
			// under THIS MOD's own namespace, at models/bullets/ and
			// textures/bullets/ respectively - neither of which is where
			// tools/mcheli_convert.py's own copy_bullet_assets() actually
			// places a converted addon's own bullet models/textures at
			// all (see that function's own doc) - it copies them into
			// models/obj/ and textures/vehicle/ instead, under the
			// SOURCE ADDON'S OWN namespace (not necessarily this mod's
			// own), and with a "bullet_" filename prefix - EXACTLY the
			// same convention WeaponStatsLoader's own ModelBullet/
			// ModelBomblet resolution already uses correctly. Every
			// cartridge model/texture path was therefore pointing at a
			// location nothing was ever actually copied to - reusing
			// weapon.projectileItem()'s own namespace (the SAME addon
			// this cartridge's own model was converted alongside) fixes
			// this the same way those two fields already do it right.
			// Confirmed by this exact bug's own debug
			// log output: weapon.projectileItem() is a vanilla PLACEHOLDER
			// item (this project's own thrown-entity ItemStack, used
			// purely for the entity's own internal bookkeeping - not this
			// project's own custom asset content at all), so its own
			// namespace is just "minecraft", nowhere close to wherever
			// this weapon's own actual converted assets live. This
			// weapon's own bulletModel (the MAIN projectile's own model,
			// which DOES already render correctly) is guaranteed to carry
			// the weapon file's own real namespace instead (see
			// WeaponStatsLoader's own ModelBullet resolution, which builds
			// it from that exact namespace) - reused here directly rather
			// than repeating the same wrong assumption a second time.
			String namespace = weapon.bulletModel().map(Identifier::getNamespace)
					.orElseGet(() -> weapon.bulletTexture().map(Identifier::getNamespace)
							.orElse(weapon.projectileItem().getNamespace()));
			Identifier cartridgeModel = Identifier.of(namespace, "models/obj/bullet_" + cart.modelName() + ".obj");
			Identifier cartridgeTexture = Identifier.of(namespace, "textures/vehicle/bullet_" + cart.modelName() + ".png");
			VehicleProjectileEntity cartridgeEntity = new com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity(
					this.getEntityWorld(), null, cartridgeItem.getDefaultStack(), 0f, cart.gravity(), 0f, false, false,
					cartridgeModel, cartridgeTexture, cart.modelScale());
			cartridgeEntity.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
			// Ejection still wasn't visible at
			// all even after the model/texture path fix: this was
			// missing entirely - without it, tudursvehiclemod$wasFiredByThisVehicle()'s
			// own self-collision exclusion (see that method's own doc)
			// never actually recognized this cartridge as having come
			// from this vehicle at all (getFiringVehicle() stayed null),
			// meaning this cartridge could immediately collide with and
			// get stopped/discarded by this SAME vehicle's own hitbox
			// the instant it spawned - it's created essentially inside
			// or right at the muzzle, part of this vehicle's own body,
			// and (unlike a fast-moving bullet, which usually clears
			// that same hitbox within a single tick regardless) a
			// cartridge with Acceleration=0 barely moves away from that
			// spawn point at all initially, giving it every opportunity
			// to register a hit against this vehicle's own mesh before
			// ever becoming visible.
			cartridgeEntity.tudursvehiclemod$setFiringVehicle(this);
			cartridgeEntity.tudursvehiclemod$setBounceStrength(cart.bounce());
			cartridgeEntity.tudursvehiclemod$setFuseTicks(20, -1);
			// Per Readme_Weapon.txt's own SetCartridge doc: Yaw/Pitch are
			// measured relative to the WEAPON's own firing direction
			// (sideways/up-down offsets from it), not absolute world
			// angles - built here by rotating forward by those two
			// offsets. Yaw specifically needs to be
			// SUBTRACTED here, not added - this project's own yaw
			// convention increases clockwise (towards the shooter's own
			// right, same convention used throughout this class), but
			// Readme_Weapon.txt's own documented convention for THIS
			// directive specifically is the opposite (positive = left,
			// negative = right) - adding cart.yawDegrees() directly
			// would eject towards the right for a positive value,
			// exactly backwards from spec.
			float baseYaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
			float basePitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(forward.y, -1.0, 1.0)));
			float cartYaw = baseYaw - cart.yawDegrees();
			float cartPitch = basePitch + cart.pitchDegrees();
			double cartYawRad = Math.toRadians(cartYaw);
			double cartPitchRad = Math.toRadians(cartPitch);
			Vec3d cartDir = new Vec3d(-Math.sin(cartYawRad) * Math.cos(cartPitchRad), -Math.sin(cartPitchRad),
					Math.cos(cartYawRad) * Math.cos(cartPitchRad));
			cartridgeEntity.setVelocity(cartDir.multiply(cart.acceleration()));
			this.getEntityWorld().spawnEntity(cartridgeEntity);
		});
	}

	/** Accuracy: tilts velocity by a random angle within a cone of half-angle accuracyDegrees, preserving speed (same approach as applyBombletSpread(), but accuracyDegrees is already a plain degree value, not a 0.1 fraction). */
	private Vec3d tudursvehiclemod$applyAccuracySpread(Vec3d velocity, float accuracyDegrees) {
		double speed = velocity.length();
		if (speed < 1.0E-6 || accuracyDegrees <= 0f) {
			return velocity;
		}
		Vector3f dir = new Vector3f((float) (velocity.x / speed), (float) (velocity.y / speed), (float) (velocity.z / speed));
		Vector3f arbitrary = Math.abs(dir.x) < 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		Vector3f perpendicular = new Vector3f(dir).cross(arbitrary).normalize();
		var random = this.getEntityWorld().random;
		float spinAngleRad = (float) Math.toRadians(random.nextFloat() * 360f);
		Quaternionf spin = new Quaternionf().rotationAxis(spinAngleRad, dir.x, dir.y, dir.z);
		Vector3f tiltAxis = spin.transform(new Vector3f(perpendicular));
		float tiltAngleRad = (float) Math.toRadians(random.nextFloat() * accuracyDegrees);
		Quaternionf tilt = new Quaternionf().rotationAxis(tiltAngleRad, tiltAxis.x, tiltAxis.y, tiltAxis.z);
		Vector3f result = tilt.transform(new Vector3f(dir));
		return new Vec3d(result.x, result.y, result.z).multiply(speed);
	}

	/** Group: applies firedWeapon's own fire-rate cooldown to every other weapon sharing its exact group name (each on its OWN cooldownTicks) - prevents firing weapon A, switching to weapon B (same gun, different round), and firing instantly again. Ammo counts are untouched. */
	private void tudursvehiclemod$applyGroupCooldown(VehicleDefinition def, WeaponDefinition firedWeapon, int firedWeaponIndex) {
		if (firedWeapon.group().isEmpty()) {
			return;
		}
		String groupName = firedWeapon.group().get();
		for (int otherIndex = 0; otherIndex < def.weapons().size(); otherIndex++) {
			if (otherIndex == firedWeaponIndex) {
				continue;
			}
			WeaponDefinition other = def.weapons().get(otherIndex);
			if (other.group().isPresent() && other.group().get().equals(groupName)) {
				this.weaponCooldowns[otherIndex] = other.cooldownTicks();
			}
		}
	}

	/** Per Readme_Weapon.txt's own Group doc: same idea as tudursvehiclemod$applyGroupCooldown()'s own doc, but for the reload timer instead - starts every OTHER same-group weapon's own reload countdown (at ITS OWN configured reloadTicks) too, the instant firedWeapon's own magazine empties out and starts reloading. A no-op entirely if firedWeapon isn't actually in any group at all, or the OTHER weapon's own ReloadTime is 0 (instant reload has nothing to actually start counting down at all). */
	private void tudursvehiclemod$applyGroupReload(VehicleDefinition def, WeaponDefinition firedWeapon, int firedWeaponIndex) {
		if (firedWeapon.group().isEmpty()) {
			return;
		}
		String groupName = firedWeapon.group().get();
		for (int otherIndex = 0; otherIndex < def.weapons().size(); otherIndex++) {
			if (otherIndex == firedWeaponIndex) {
				continue;
			}
			WeaponDefinition other = def.weapons().get(otherIndex);
			if (other.group().isPresent() && other.group().get().equals(groupName) && other.reloadTicks() > 0) {
				this.weaponReloadTicksRemaining[otherIndex] = other.reloadTicks();
			}
		}
	}

	/** Sends this weapon's own configured fire sound (see WeaponDefinition's own sound/soundVolume/soundPitch/soundPitchRandom doc), respecting its own soundDelayTicks cooldown - extracted so the no-projectile weapon types (Dispenser/Smoke/TargetingPod) can play their own sound the exact same way an ordinary shot already does. */
	private void tudursvehiclemod$playWeaponFireSound(WeaponDefinition weapon, int weaponIndex, Vec3d spawnPos, ServerPlayerEntity shooter) {
		if (weapon.sound().isEmpty() || this.weaponSoundCooldowns[weaponIndex] > 0) {
			return;
		}
		this.weaponSoundCooldowns[weaponIndex] = weapon.soundDelayTicks();
		com.example.tudursvehiclemod.network.WeaponFireSoundPayload soundPayload =
				new com.example.tudursvehiclemod.network.WeaponFireSoundPayload(
						weapon.sound().get(), spawnPos.x, spawnPos.y, spawnPos.z,
						weapon.soundVolume(), weapon.soundPitch(), weapon.soundPitchRandom());
		for (net.minecraft.server.network.ServerPlayerEntity tracking :
				net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(this)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(tracking, soundPayload);
		}
		// The shooter themselves might not be "tracking" their own mount in the usual sense (they're riding it, not observing it from outside). that concern simply doesn't apply without an actual shooter, so this whole extra send is skipped entirely in that case.
		if (shooter != null && !net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(this).contains(shooter)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(shooter, soundPayload);
		}
	}

	/** How often (ticks) tudursvehiclemod$fireSmoke() spawns another burst of particles, for as long as its own smokeMaxAge lasts - same "waves" idea as VehicleProjectileEntity's own tudursvehiclemod$scheduleGroundSmokeWaves(). */
	private static final int SMOKE_WAVE_INTERVAL_TICKS = 4;

	/** Type=Smoke: repeating dust-particle bursts at the muzzle every SMOKE_WAVE_INTERVAL_TICKS for smokeMaxAge, manually triggered (not tied to an explosion). Purely cosmetic - no projectile/damage/collision. */
	private void tudursvehiclemod$fireSmoke(WeaponDefinition weapon, ServerWorld world, Vec3d spawnPos) {
		// smokeColor() is packed 0xAARRGGBB, but DustParticleEffect wants plain RGB - alpha byte masked off here.
		int rgb = weapon.smokeColor() & 0xFFFFFF;
		float size = weapon.smokeSize();
		net.minecraft.particle.DustParticleEffect effect = new net.minecraft.particle.DustParticleEffect(rgb, size);
		int totalTicks = Math.max(1, weapon.smokeMaxAge());
		int totalWaves = Math.max(1, totalTicks / SMOKE_WAVE_INTERVAL_TICKS);
		int[] tickCount = {0};
		int[] wavesSpawned = {0};
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (wavesSpawned[0] >= totalWaves) {
				return;
			}
			tickCount[0]++;
			if (tickCount[0] % SMOKE_WAVE_INTERVAL_TICKS == 0) {
				world.spawnParticles(effect, true, true, spawnPos.x, spawnPos.y, spawnPos.z, 3, 0.15, 0.15, 0.15, 0.01);
				wavesSpawned[0]++;
			}
		});
	}

	/** MarkTime is in SECONDS, unlike almost every other MC Heli duration. */
	private static final int TICKS_PER_SECOND = 20;

	/** Type=TargetingPod: highlights whatever's in the shooter's spotting cone (front of view, not vehicle facing) - "block" mode marks with a particle outline, entity mode uses vanilla's Glowing (closest built-in equivalent to MC Heli's "highlighted through terrain"). No projectile. */
	private void tudursvehiclemod$fireTargetingPod(WeaponDefinition weapon, ServerPlayerEntity shooter) {
		java.util.Set<String> targets = weapon.targetingPodTargets();
		int markTimeTicks = MathHelper.ceil(weapon.targetingPodMarkTimeSeconds() * TICKS_PER_SECOND);
		if (targets.contains("block")) {
			net.minecraft.util.hit.HitResult hit = shooter.raycast(weapon.targetingPodLength(), 1.0f, false);
			if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
					&& this.getEntityWorld() instanceof ServerWorld serverWorld) {
				net.minecraft.util.math.BlockPos hitPos = ((net.minecraft.util.hit.BlockHitResult) hit).getBlockPos();
				tudursvehiclemod$scheduleBlockMarker(serverWorld, hitPos, markTimeTicks);
			}
			return;
		}
		Vec3d eyePos = shooter.getEyePos();
		Vec3d viewDir = shooter.getRotationVec(1.0f);
		float cosRadius = (float) Math.cos(Math.toRadians(weapon.targetingPodRadius()));
		double lengthSq = (double) weapon.targetingPodLength() * weapon.targetingPodLength();
		net.minecraft.util.math.Box searchBox = new net.minecraft.util.math.Box(eyePos, eyePos)
				.expand(weapon.targetingPodLength());
		for (Entity candidate : this.getEntityWorld().getOtherEntities(this, searchBox, entity -> true)) {
			if (!tudursvehiclemod$matchesTargetingPodCategory(candidate, targets)) {
				continue;
			}
			Vec3d toCandidate = candidate.getEntityPos().subtract(eyePos);
			double distSq = toCandidate.lengthSquared();
			if (distSq > lengthSq || distSq < 1.0E-6) {
				continue;
			}
			double cosAngle = toCandidate.normalize().dotProduct(viewDir);
			if (cosAngle < cosRadius) {
				continue;
			}
			// Entity.setGlowing() (universal - works on
			// ANY entity, vehicles included) instead of the LivingEntity-
			// only StatusEffectInstance/Glowing approach this used to
			// have, which could never actually spot a plane/helicopter/
			// vehicle target at all (none of those extend LivingEntity).
			// Per a further direct report that setGlowing() itself is
			// insufficient for this mod's own vehicle entities (see
			// HIGHLIGHT_ACTIVE's own doc) - dispatches through
			// tudursvehiclemod$setEntityHighlighted() instead, same as
			// every other lock-on-style highlight in this project now
			// does. Still a plain persistent flag rather than a timed
			// effect, so this schedules turning it back off after
			// markTimeTicks itself, same "scheduled task" idea as
			// tudursvehiclemod$scheduleBlockMarker()'s own particle timer.
			tudursvehiclemod$setEntityHighlighted(candidate, true);
			tudursvehiclemod$scheduleGlowOff(candidate.getId(), markTimeTicks);
		}
	}

	/** Spawns an entirely separate support aircraft to carry out the actual strike, rather than firing any projectile from THIS vehicle at all - targetPos (already marked via the same crosshair raycast AS_MISSILE itself uses) is treated as the route's own "center", the same relative-coordinate convention block.DroneCenterBlockEntity's own waypoints already use, so the SAME weapon file's own route works correctly regardless of where it's actually called in from. Does nothing at all (no-op, not an error) if this weapon's own CasAircraft/CasWaypoint configuration is missing or empty (see WeaponStatsLoader's own parsing doc) - matches every other Type-specific "unconfigured falls back to inert" convention this project already has elsewhere. CAS takes the shooter's own facing direction into account: every waypoint's own relX/relZ is rotated by the shooter's own actual horizontal view direction (see tudursvehiclemod$rotateCasOffset()'s own doc) before use, so the configured route's own "forward" (+relZ) direction always means "whichever way the player was actually looking at fire time", not a fixed world direction. Spawns the aircraft at the FIRST (rotated) waypoint's own position facing along its own first leg, hands it the full (rotated, then accuracy-perturbed) route, and activates its drone autopilot immediately - it starts flying and auto-firing at attack=true waypoints the moment it's spawned. */
	/** Rotates a (relX, relZ) horizontal offset into world space, using the shooter's own ACTUAL horizontal view direction (forwardX, forwardZ - already normalized by the caller) directly, rather than re-deriving a direction from a yaw angle via this project's own sin/cos convention (eliminates any possible sign/convention mismatch between the two). relZ maps onto the given forward direction, relX onto its own 90-degree-right companion (rightX, rightZ) = (forwardZ, -forwardX) - so the route's own configured "forward" (+relZ) always means "whichever way the player was actually looking at fire time". Returns {rotatedX, rotatedZ}; relY (vertical) is untouched by a horizontal rotation and isn't part of this at all. */
	protected static double[] tudursvehiclemod$rotateCasOffset(double relX, double relZ, double forwardX, double forwardZ) {
		double horizontalLength = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
		if (horizontalLength < 1.0e-6) {
			// Defensive: looking straight up/down leaves no meaningful horizontal facing at all - falls back to "no rotation" (same as vanilla south, +Z) rather than dividing by ~0.
			return new double[]{relX, relZ};
		}
		double normalizedForwardX = forwardX / horizontalLength;
		double normalizedForwardZ = forwardZ / horizontalLength;
		double rightX = normalizedForwardZ;
		double rightZ = -normalizedForwardX;
		double rotatedX = relX * rightX + relZ * normalizedForwardX;
		double rotatedZ = relX * rightZ + relZ * normalizedForwardZ;
		return new double[]{rotatedX, rotatedZ};
	}

	/** Default spacing (blocks) between aircraft/elements when a weapon's own CasFormationSpacing/CarrierFormationSpacing is left unconfigured. */
	private static final double DEFAULT_FORMATION_SPACING = 8.0;

	/** How many ticks apart each aircraft in a staggered Carrier formation launch spawns - see carrierPendingLaunches' own doc. */
	private static final int CARRIER_LAUNCH_INTERVAL_TICKS = 20;

	/** Everything about ONE Carrier launch that stays IDENTICAL across every aircraft in its own formation - bundled here purely to keep tudursvehiclemod$spawnOneCarrierAircraft()'s own parameter list manageable, split out from the per-aircraft formLateral/formLongitudinal/waitRequired values that DO vary. shooterUuid/mothershipSeatIndex are -1/null when this weapon was fired without a shooter (e.g. by an AI/dummy pilot) - resolved fresh each time from the UUID rather than holding a ServerPlayerEntity reference directly, since a player could disconnect mid-sequence. */
	private record CarrierLaunchContext(
			com.example.tudursvehiclemod.asset.CarrierAircraftConfig config,
			net.minecraft.util.Identifier vehicleId,
			VehicleDefinition def,
			net.minecraft.entity.EntityType<?> entityType,
			net.minecraft.util.math.BlockPos targetBlockPos,
			double spawnX, double spawnY, double spawnZ,
			float spawnYaw,
			double mothershipLaunchForwardX, double mothershipLaunchForwardZ,
			double finalForwardX, double finalForwardZ,
			java.util.UUID shooterUuid,
			int mothershipSeatIndex,
			int mothershipWeaponIndex,
			java.util.List<int[]> sharedAccuracyPerturbations) {
	}

	/** One staggered Carrier formation launch currently in progress on this vehicle (as the mothership), keyed by mothershipWeaponIndex in carrierPendingLaunches. Ticked down by tudursvehiclemod$updateCarrierLaunchSequences(), called once per server tick from tick() itself; a no-op whenever carrierPendingLaunches is empty, so this costs nothing for a ship that never fires a formation Carrier launch. */
	private static final class CarrierLaunchSequence {
		final CarrierLaunchContext context;
		final java.util.List<double[]> formationOffsets;
		int nextIndex;
		int ticksUntilNextLaunch = CARRIER_LAUNCH_INTERVAL_TICKS;
		final java.util.List<java.util.UUID> launchedAircraft = new java.util.ArrayList<>();
		/** The lead aircraft's own UUID, known once it's actually spawned (see tudursvehiclemod$fireCarrierLaunch()'s own doc) - every wingman spawned across this sequence's own remaining ticks is told to follow THIS aircraft specifically. Null only in the pathological case where the lead itself somehow failed to spawn at all. */
		final java.util.UUID leadAircraftUuid;

		CarrierLaunchSequence(CarrierLaunchContext context, java.util.List<double[]> formationOffsets, int nextIndex, java.util.UUID leadAircraftUuid) {
			this.context = context;
			this.formationOffsets = formationOffsets;
			this.nextIndex = nextIndex;
			this.leadAircraftUuid = leadAircraftUuid;
		}
	}

	/** See CarrierLaunchSequence's own doc. */
	private final java.util.Map<Integer, CarrierLaunchSequence> carrierPendingLaunches = new java.util.HashMap<>();

	/** {lateralOffset, longitudinalOffset} for every aircraft index (0=lead, always {0,0}) in a formation of `count` aircraft of the given `type`, spaced `spacing` blocks apart - added to EVERY route waypoint (see tudursvehiclemod$rotateCasOffset()'s own relX/relZ convention, which this matches: lateral=right, longitudinal=forward, so a negative longitudinal offset means "behind" the lead) for a rigid formation that flies the configured route together, just staggered. count<=1 always returns a single {0,0} entry (no formation at all). elementSpacing is only meaningful for DIAMOND/DELTA (see tudursvehiclemod$computeElementFormationOffsets()'s own doc) - a negative value there means "not explicitly configured", falling back to that method's own auto-derived default. */
	public static java.util.List<double[]> tudursvehiclemod$computeFormationOffsets(int count, com.example.tudursvehiclemod.asset.FormationType type, double spacing, double elementSpacing) {
		java.util.List<double[]> offsets = new java.util.ArrayList<>();
		if (count <= 1) {
			offsets.add(new double[]{0.0, 0.0});
			return offsets;
		}
		switch (type) {
			case LINE_ABREAST -> {
				for (int i = 0; i < count; i++) {
					offsets.add(new double[]{tudursvehiclemod$alternatingSideOffset(i) * spacing, 0.0});
				}
			}
			case LINE_ASTERN -> {
				for (int i = 0; i < count; i++) {
					offsets.add(new double[]{0.0, -i * spacing});
				}
			}
			case V_FORMATION -> {
				for (int i = 0; i < count; i++) {
					double side = tudursvehiclemod$alternatingSideOffset(i);
					offsets.add(new double[]{side * spacing, -Math.abs(side) * spacing});
				}
			}
			case DIAMOND -> offsets.addAll(tudursvehiclemod$computeElementFormationOffsets(count, 4, spacing, elementSpacing));
			case DELTA -> offsets.addAll(tudursvehiclemod$computeElementFormationOffsets(count, 3, spacing, elementSpacing));
		}
		return offsets;
	}

	/** For aircraft index i (0=lead), which side of the formation's own centerline it sits on and how far out - 0 for the lead itself, then alternating +1,-1,+2,-2,.. This is exactly what leaves an EVEN total count left-right asymmetric : the lead is always fixed at 0 (it must stay on the route), so one side unavoidably ends up with one more position than the other. */
	private static int tudursvehiclemod$alternatingSideOffset(int i) {
		if (i == 0) {
			return 0;
		}
		int pairIndex = (i + 1) / 2;
		return (i % 2 == 1) ? pairIndex : -pairIndex;
	}

	/** DIAMOND/DELTA: splits `count` aircraft into elements of `elementSize` (the last element may have fewer, if count isn't an exact multiple), each flying its own small local shape (tudursvehiclemod$computeElementLocalOffset()) - the elements' own centroids are then arranged into a larger echelon using the SAME alternating-side, stepped-back logic V_FORMATION itself uses one level up (a "combat box" - the overall arrangement forms one even with multiple small diamond/delta sub-groups). elementSpacing (blocks between element CENTROIDS, distinct from spacing between aircraft within the same element) is configurable - a negative value (the default, unconfigured) falls back to elementSize*1.5x the base spacing instead, the original auto-derived value from before this became independently configurable. */
	private static java.util.List<double[]> tudursvehiclemod$computeElementFormationOffsets(int count, int elementSize, double spacing, double elementSpacing) {
		java.util.List<double[]> offsets = new java.util.ArrayList<>();
		double actualElementSpacing = elementSpacing >= 0.0 ? elementSpacing : spacing * elementSize * 1.5;
		for (int i = 0; i < count; i++) {
			int elementIndex = i / elementSize;
			int positionInElement = i % elementSize;
			double side = tudursvehiclemod$alternatingSideOffset(elementIndex);
			double elementLateral = side * actualElementSpacing;
			double elementLongitudinal = -Math.abs(side) * actualElementSpacing;
			double[] local = tudursvehiclemod$computeElementLocalOffset(positionInElement, elementSize);
			offsets.add(new double[]{elementLateral + local[0] * spacing, elementLongitudinal + local[1] * spacing});
		}
		return offsets;
	}

	/** Local (unscaled - caller multiplies by spacing) offset for one aircraft within a single diamond (elementSize 4: front/right-wing/left-wing/tail) or delta/vic (elementSize 3: front/right-wing/left-wing) element - position 0 is always that element's own lead (front point). */
	private static double[] tudursvehiclemod$computeElementLocalOffset(int positionInElement, int elementSize) {
		if (elementSize >= 4) {
			return switch (positionInElement) {
				case 0 -> new double[]{0.0, 0.0};
				case 1 -> new double[]{1.0, -0.5};
				case 2 -> new double[]{-1.0, -0.5};
				default -> new double[]{0.0, -1.5};
			};
		}
		return switch (positionInElement) {
			case 0 -> new double[]{0.0, 0.0};
			case 1 -> new double[]{1.0, -1.0};
			default -> new double[]{-1.0, -1.0};
		};
	}

	/** Once the lead aircraft (formationIndex 0) is actually spawned, links it to shooter's own current seat on THIS vehicle exactly the way tudursvehiclemod$fireCarrierLaunch() already does for a Carrier launch (tudursvehiclemod$setCarrierMothership() on the aircraft itself, carrierLinkedAircraftBySeat/tudursvehiclemod$updateCarrierMothershipForcedChunks() on this vehicle) - entity.AbstractVehicleEntity's own tudursvehiclemod$toggleCarrierSeat() is entirely agnostic to HOW an aircraft came to be linked, so populating these same fields is the whole fix; no changes needed there at all. Landing-specific parameters (mothership weapon-slot ammo replenishment, yaw offsets, landing waypoints) are passed as inert defaults - a CAS aircraft never returns to "land" on the vehicle that fired it at all, it simply discards itself once its own route completes, so none of that ever actually gets read for a CAS-spawned aircraft. */
	private void tudursvehiclemod$fireCasStrike(WeaponDefinition weapon, ServerWorld serverWorld, Vec3d targetPos, double shooterForwardX, double shooterForwardZ, int actualFormationSize, ServerPlayerEntity shooter, int weaponIndex) {
		org.slf4j.Logger logger = VehicleMod_LoggerHolder.LOGGER;
		java.util.Optional<com.example.tudursvehiclemod.asset.CasStrikeConfig> maybeConfig = weapon.casStrike();
		if (maybeConfig.isEmpty()) {
			logger.warn("[CAS] Weapon '{}' has Type=CAS but no valid CasAircraft/CasWaypoint configuration was parsed from its own .txt file - see WeaponStatsLoader's own log output at reload time for the specific parsing warning.", weapon.weaponName());
			return;
		}
		com.example.tudursvehiclemod.asset.CasStrikeConfig config = maybeConfig.get();
		// Applies the user-tunable CasYawOffset correction (see CasStrikeConfig's own doc) on top of the shooter's own actual forward direction, before it's used for any waypoint rotation below. Uses new final variables rather than reassigning the method parameters, since those get captured by a lambda further below (which requires effectively-final locals).
		double effectiveForwardX = shooterForwardX;
		double effectiveForwardZ = shooterForwardZ;
		if (config.yawOffsetDegrees() != 0f) {
			double offsetRad = Math.toRadians(config.yawOffsetDegrees());
			double cos = Math.cos(offsetRad);
			double sin = Math.sin(offsetRad);
			effectiveForwardX = shooterForwardX * cos - shooterForwardZ * sin;
			effectiveForwardZ = shooterForwardX * sin + shooterForwardZ * cos;
		}
		final double finalForwardX = effectiveForwardX;
		final double finalForwardZ = effectiveForwardZ;
		if (config.waypoints().isEmpty()) {
			// Defensive: WeaponStatsLoader's own parsing already refuses to build a CasStrikeConfig with an empty waypoint list at all, so this should be unreachable in practice.
			logger.warn("[CAS] Weapon '{}' has a CasStrikeConfig with zero waypoints.", weapon.weaponName());
			return;
		}
		java.util.Optional<net.minecraft.util.Identifier> maybeId = com.example.tudursvehiclemod.asset.VehicleRegistry.getIdByFileName(config.aircraftFileName());
		java.util.Optional<VehicleDefinition> maybeDef = com.example.tudursvehiclemod.asset.VehicleRegistry.getByFileName(config.aircraftFileName());
		if (maybeId.isEmpty() || maybeDef.isEmpty()) {
			logger.warn("[CAS] Weapon '{}' names CasAircraft='{}', but no currently-loaded vehicle file has that name - currently known vehicle file names: {}",
					weapon.weaponName(), config.aircraftFileName(),
					com.example.tudursvehiclemod.asset.VehicleRegistry.getAll().keySet());
			return;
		}
		net.minecraft.util.Identifier vehicleId = maybeId.get();
		VehicleDefinition def = maybeDef.get();
		net.minecraft.entity.EntityType<?> entityType = net.minecraft.registry.Registries.ENTITY_TYPE.get(def.entityType());

		net.minecraft.util.math.BlockPos targetBlockPos = net.minecraft.util.math.BlockPos.ofFloored(targetPos);
		com.example.tudursvehiclemod.asset.CasWaypoint firstWaypoint = config.waypoints().get(0);

		// Faces along the route's own first leg (waypoint 0 -> waypoint 1) when there's a second waypoint to aim toward, rather than an arbitrary default yaw - a real strike aircraft would already be lined up on its own approach course the instant it appears. Computed ONCE, shared by every aircraft in the formation - a per-aircraft formation offset is CONSTANT across every waypoint of that aircraft's own route, so it cancels out of this delta entirely and every aircraft ends up facing the exact same direction, matching a real formation departing in parallel.
		float spawnYaw = 0f;
		if (config.waypoints().size() > 1) {
			com.example.tudursvehiclemod.asset.CasWaypoint secondWaypoint = config.waypoints().get(1);
			double[] rotatedDelta = tudursvehiclemod$rotateCasOffset(
					secondWaypoint.relX() - firstWaypoint.relX(), secondWaypoint.relZ() - firstWaypoint.relZ(), finalForwardX, finalForwardZ);
			double dx = rotatedDelta[0];
			double dz = rotatedDelta[1];
			if (Math.abs(dx) > 1.0e-4 || Math.abs(dz) > 1.0e-4) {
				spawnYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			}
		}
		float finalSpawnYaw = spawnYaw;

		// Spawns config.formationSize() aircraft instead of just one, each flying the exact same configured route, offset by tudursvehiclemod$computeFormationOffsets() (index 0 is always the lead - a zero offset, so its own spawn position/route match the pre-existing single-aircraft behavior exactly).
		java.util.List<double[]> formationOffsets = tudursvehiclemod$computeFormationOffsets(
				actualFormationSize, config.formationType(), config.formationSpacing(), config.elementSpacing());
		// One shared perturbation per waypoint, generated ONCE here rather than independently inside each aircraft's own entity-create callback below - every aircraft in the formation now perturbs identically relative to the ideal route, keeping the formation's own relative spacing exactly as configured throughout.
		java.util.List<int[]> sharedAccuracyPerturbations = new java.util.ArrayList<>();
		for (int i = 0; i < config.waypoints().size(); i++) {
			if (i > 0 && config.accuracy() > 0f) {
				float navigationAccuracy = config.accuracy();
				sharedAccuracyPerturbations.add(new int[]{
						Math.round((this.random.nextFloat() * 2f - 1f) * navigationAccuracy),
						Math.round((this.random.nextFloat() * 2f - 1f) * navigationAccuracy),
						Math.round((this.random.nextFloat() * 2f - 1f) * navigationAccuracy)});
			} else {
				sharedAccuracyPerturbations.add(new int[]{0, 0, 0});
			}
		}
		// The lead aircraft (index 0) is always spawned FIRST, so its UUID is known before any wingman needs it.
		java.util.UUID leadAircraftUuid = null;
		for (int formationIndex = 0; formationIndex < formationOffsets.size(); formationIndex++) {
			double[] formationOffset = formationOffsets.get(formationIndex);
			double formLateral = formationOffset[0];
			double formLongitudinal = formationOffset[1];
			boolean isWingman = formationIndex > 0;

			// Per this whole feature's own doc: spawn POSITION still uses the formation offset (a real formation departs side-by-side, not stacked at one point) - only the ROUTE ITSELF (used from here on purely for attack-flag lookup and timeout/length bookkeeping, never for actual navigation once a wingman starts following its own leader) no longer bakes the offset in.
			double[] firstRotated = tudursvehiclemod$rotateCasOffset(
					firstWaypoint.relX() + formLateral, firstWaypoint.relZ() + formLongitudinal, finalForwardX, finalForwardZ);
			double spawnX = targetBlockPos.getX() + 0.5 + firstRotated[0];
			double spawnY = targetBlockPos.getY() + firstWaypoint.relY();
			double spawnZ = targetBlockPos.getZ() + 0.5 + firstRotated[1];

			Entity spawned = entityType.create(serverWorld, entity -> {
				if (entity instanceof AbstractVehicleEntity vehicle) {
					vehicle.setVehicleDefinitionId(vehicleId);
					vehicle.tudursvehiclemod$fillAllWeaponAmmoAndFuel();
				}
				if (entity instanceof AircraftEntity aircraft) {
					java.util.List<com.example.tudursvehiclemod.block.DroneWaypoint> route = new java.util.ArrayList<>();
					java.util.List<Boolean> attackFlags = new java.util.ArrayList<>();
					for (int i = 0; i < config.waypoints().size(); i++) {
						com.example.tudursvehiclemod.asset.CasWaypoint casWaypoint = config.waypoints().get(i);
						double[] rotated = tudursvehiclemod$rotateCasOffset(
								casWaypoint.relX(), casWaypoint.relZ(), finalForwardX, finalForwardZ);
						com.example.tudursvehiclemod.block.DroneWaypoint droneWaypoint = new com.example.tudursvehiclemod.block.DroneWaypoint(
								(int) Math.round(rotated[0]), casWaypoint.relY(), (int) Math.round(rotated[1]),
								casWaypoint.speedFraction(), 0f, 1.0f, 1.0f);
						// Perturbs every waypoint EXCEPT the first. Uses sharedAccuracyPerturbations (computed once, above, per waypoint) - shared by the whole formation, since only the LEAD aircraft actually navigates via this route at all now (every wingman follows the lead directly instead - see this whole feature's own doc), so there's nothing left to keep "in sync" here beyond the lead's own single copy, but the shared perturbation is kept regardless for consistency/simplicity (a wingman's own route copy is otherwise unused for navigation, only attack-flag lookup, which perturbation doesn't affect).
						int[] perturbation = sharedAccuracyPerturbations.get(i);
						if (perturbation[0] != 0 || perturbation[1] != 0 || perturbation[2] != 0) {
							droneWaypoint = new com.example.tudursvehiclemod.block.DroneWaypoint(
									droneWaypoint.relX() + perturbation[0], droneWaypoint.relY() + perturbation[1], droneWaypoint.relZ() + perturbation[2],
									droneWaypoint.speedFraction(), droneWaypoint.rollAngle(),
									droneWaypoint.rollManeuverabilityMultiplier(), droneWaypoint.turnManeuverabilityMultiplier());
						}
						route.add(droneWaypoint);
						attackFlags.add(casWaypoint.attack());
					}
					aircraft.tudursvehiclemod$setCasWaypointRoute(route, attackFlags, config.weaponIndex());
					aircraft.tudursvehiclemod$setCasTimeoutTicks(config.timeoutTicks());
					aircraft.tudursvehiclemod$setCasStuckTimeoutTicks(config.stuckTimeoutTicks());
					aircraft.tudursvehiclemod$setDroneLink(targetBlockPos);
				}
			}, net.minecraft.util.math.BlockPos.ofFloored(spawnX, spawnY, spawnZ), net.minecraft.entity.SpawnReason.TRIGGERED, false, false);

			if (spawned == null) {
				logger.warn("[CAS] Vehicle '{}' (file '{}', entity type '{}') was found, but EntityType.create() returned null - could not spawn the strike aircraft at all.",
						vehicleId, config.aircraftFileName(), def.entityType());
				continue;
			}
			if (!(spawned instanceof AircraftEntity)) {
				logger.warn("[CAS] Vehicle '{}' (file '{}') is not an AircraftEntity (actual class: {}) - it will spawn, but its own drone route/waypoints were never actually assigned, since only AircraftEntity supports that.",
						vehicleId, config.aircraftFileName(), spawned.getClass().getSimpleName());
			}
			spawned.refreshPositionAndAngles(spawnX, spawnY, spawnZ, finalSpawnYaw, 0f);
			if (spawned instanceof AircraftEntity spawnedAircraft) {
				spawnedAircraft.tudursvehiclemod$forceLoadSpawnChunk();
				spawnedAircraft.tudursvehiclemod$setCarrierFormationIndex(formationIndex);
				if (!isWingman) {
					leadAircraftUuid = spawnedAircraft.getUuid();
					spawnedAircraft.tudursvehiclemod$registerFormationMember(leadAircraftUuid, true);
					if (shooter != null) {
						int shooterSeatIndex = this.tudursvehiclemod$getAssignedSeatIndex(shooter);
						spawnedAircraft.tudursvehiclemod$setCarrierMothership(this.getUuid(), shooterSeatIndex, weaponIndex, 0f, 0f, java.util.List.of());
						this.carrierLinkedAircraftBySeat.put(shooterSeatIndex, new java.util.ArrayList<>(java.util.List.of(leadAircraftUuid)));
						this.tudursvehiclemod$updateCarrierMothershipForcedChunks();
					}
				} else if (leadAircraftUuid != null) {
					// A CAS wingman starts following its own lead aircraft from the very first tick (CAS has no launch/landing phase to exclude at all) - see AircraftEntity's own tudursvehiclemod$updateDroneFormationFollow() doc for the actual follow behavior.
					spawnedAircraft.tudursvehiclemod$setDroneFormationFollow(leadAircraftUuid, formLateral, formLongitudinal);
					spawnedAircraft.tudursvehiclemod$registerFormationMember(leadAircraftUuid, false);
				}
			}
			serverWorld.spawnEntity(spawned);
		}
	}

	/** STAGE 1 ONLY - always fully autonomous, identical to CAS's own in-flight behavior once airborne (reuses AircraftEntity's own casWaypointOverride/casAttackFlags/casForcedChunks/casTimeoutTicksRemaining/casStuckTimeoutTicks fields and tudursvehiclemod$updateDroneWaypointAutopilot()/tudursvehiclemod$updateCasAutoFire() methods as-is - there is no Carrier-specific autopilot at all in this stage). The one meaningful difference from tudursvehiclemod$fireCasStrike(): spawnPos is given directly (this weapon's own AddWeapon mount position, already computed by the caller) rather than derived from the route's own first waypoint - a carrier aircraft launches FROM the firing vehicle itself, not at a distant point. The marked ground point (targetPos) still serves as the route's own relative-coordinate center exactly like CAS's own target point does, so the SAME weapon file's own route format/rotation/accuracy-perturbation logic works completely unchanged. Faces toward the route's own first waypoint (rather than CAS's own "waypoint 0 -> waypoint 1" facing, since here the aircraft actually has to fly TO waypoint 0 first, unlike CAS where it spawns AT waypoint 0's own position already). */
	/** Per carrierMothershipForcedChunks's own doc: releases any chunks this vehicle itself was force-loading as a Carrier mothership, whenever IT is removed from the world for any reason - otherwise those chunks would stay permanently requested with nothing left to ever release them. Uses ChunkForceTracker.releaseAll() (see that class's own doc, and AircraftEntity's own equivalent migration) rather than un-forcing directly - a shared chunk an orbiting/launching aircraft still needs stays correctly force-loaded even after this mothership releases its own claim on it. */
	@Override
	public void onRemoved() {
		if (this.getEntityWorld() instanceof ServerWorld serverWorldForLockCleanup) {
			for (Integer previousId : this.tudursvehiclemod$previousLockOnTargetIds) {
				Entity previous = serverWorldForLockCleanup.getEntityById(previousId);
				if (previous != null) {
					tudursvehiclemod$setEntityHighlighted(previous, false);
				}
			}
		}
		this.tudursvehiclemod$previousLockOnTargetIds = java.util.Set.of();
		if (!this.carrierMothershipForcedChunks.isEmpty() && this.getEntityWorld() instanceof ServerWorld serverWorld) {
			com.example.tudursvehiclemod.ChunkForceTracker.releaseAll(serverWorld, this);
			this.carrierMothershipForcedChunks = java.util.Set.of();
		}
		if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
			for (RunwayTileState state : this.runwayTileStates) {
				tudursvehiclemod$discardAndClearRunwayTiles(serverWorld, state.interior);
			}
		}
		CARRIER_ACTIVE_MOTHERSHIPS.remove(this);
		// Motionless, in mid-air after this vehicle (and its own runway) disappeared while still actively carrying it: explicitly releases whatever this vehicle was carrying on its own most recent tick, rather than leaving each one in whatever forced state (hasLifted=false, etc.) it was left in, with no guarantee its own physics would naturally re-evaluate that state correctly once this vehicle itself is simply gone.
		if (!this.carrierLastCarriedCandidates.isEmpty() && this.getEntityWorld() instanceof ServerWorld serverWorld) {
			for (java.util.UUID candidateUuid : this.carrierLastCarriedCandidates) {
				Entity candidate = serverWorld.getEntity(candidateUuid);
				if (candidate instanceof AircraftEntity aircraftCandidate) {
					aircraftCandidate.tudursvehiclemod$releaseFromCarrier();
				}
			}
		}
		super.onRemoved();
	}

	/** Avoids duplicating onRemoved()'s own discard-and-clear loop four times (once per border side) - discards every entity in tiles (if still present) and clears the list. No-op if already empty. */
	private static void tudursvehiclemod$discardAndClearRunwayTiles(ServerWorld serverWorld, java.util.List<java.util.UUID> tiles) {
		if (tiles.isEmpty()) {
			return;
		}
		for (java.util.UUID platformUuid : tiles) {
			Entity platformEntity = serverWorld.getEntity(platformUuid);
			if (platformEntity != null) {
				platformEntity.discard();
			}
		}
		tiles.clear();
	}

	/** See carrierMothershipForcedChunks's own doc - called once per tick (server-side only) from tick() itself. A no-op (returns immediately) for the overwhelming majority of vehicles that have never fired a Carrier weapon at all, so this costs nothing for anything else. */
	/** For each of this vehicle's own Carrier weapons, looks for a player-piloted aircraft matching that weapon's own configured aircraftFileName, sitting essentially stationary, within ANY of that weapon's own recovery zones (the original AddWeapon-centered zone, PLUS any configured extraRecoveryPoints - see CarrierAircraftConfig's own doc) - and if that weapon's own ammo capacity (magazine + reserve + in-flight, same accounting tryResupplyWeapon() itself uses) has room for one more, converts it: despawns the aircraft, adds +1 directly to that weapon's own magazine (same tudursvehiclemod$replenishWeaponAmmo() a genuine return-to-base landing itself uses), and relocates its own pilot to the lowest-numbered currently-empty seat on THIS vehicle - or simply dismounts them (leaving them standing where the aircraft was) if every seat is already occupied. At most one conversion per weapon per tick, regardless of how many of its own zones have a qualifying candidate. */
	/** DIRECT test confirming push-based collision genuinely requires a LivingEntity (not just isPushable()=true on a bare Entity, which was mistakenly assumed confirmed working from an EARLIER test that turned out to have actually been against a LivingEntity-based Shulker-style attempt, not the later bare-Entity redesign) - see CarrierRunwayPlatformEntity's own doc for the full reasoning: tiles this runway with a 2-D GRID of FIXED-size CarrierRunwayPlatformEntity instances (both across the runway's own width and along its own length - see TILE_SIZE's own doc), rather than one row of custom-sized tiles (impossible on a LivingEntity subclass at all, since both of Entity's own size-overriding mechanisms are confirmed final there). No-op if this vehicle has no runway configured. */
	// The constant that used to live here (matching registry.ModEntityTypes's own single CARRIER_RUNWAY_PLATFORM registration) has been replaced by tudursvehiclemod$selectCarrierRunwayTileType() below, choosing per-runway from several registered discrete sizes instead of always reading one shared value.

	/** Runway tile size follows a SPECIFIC runway's own configured width, with 1.0 available as an even smaller option - see ModEntityTypes' own CARRIER_RUNWAY_TILE_SIZES doc for why this can only choose from a small set of pre-registered discrete sizes rather than truly continuous scaling, and for the tile-count tradeoff a very small size carries on a long runway: returns the LARGEST registered variant (checking the original "carrier_runway_platform" id's own size too, alongside the newer smaller-variant array) whose own width still fits within runwayWidth, so the 3-column formula's own boundary-anchoring property holds. Falls back to the SMALLEST registered variant if even that doesn't fit (a runway narrower than every registered size) - the columns==1 special case in the caller then bounds the resulting overhang to that smallest variant's own unavoidable minimum. */
	/** That fix only ever capped the BORDER tile at a confirmed-safe 6.0, never the interior tiles (the base CARRIER_RUNWAY_PLATFORM, or CARRIER_RUNWAY_PLATFORM_SMALLER_VARIANTS' own larger entries), which could still select and use an oversized, unsafe tile. tudursvehiclemod$selectCarrierRunwayTileType() below now filters its own candidate pool to this same safe maximum before picking the largest fit - necessarily increasing tile count for wider runways compared to before (reversing part of an earlier deliberate tile-count-reduction effort), but correctness (not falling through the world) takes priority over that optimization. */
	private static final float SAFE_MAX_RUNWAY_TILE_SIZE = 6.0f;

	private static net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity> tudursvehiclemod$selectCarrierRunwayTileType(double runwayWidth) {
		@SuppressWarnings("unchecked")
		net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity> best =
				com.example.tudursvehiclemod.registry.ModEntityTypes.CARRIER_RUNWAY_PLATFORM;
		float bestFittingSize = -1f;
		if (best.getWidth() <= SAFE_MAX_RUNWAY_TILE_SIZE && best.getWidth() <= runwayWidth) {
			bestFittingSize = best.getWidth();
		} else {
			best = null;
		}
		net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity> smallest = null;
		for (net.minecraft.entity.EntityType<?> rawVariant : com.example.tudursvehiclemod.registry.ModEntityTypes.CARRIER_RUNWAY_PLATFORM_SMALLER_VARIANTS) {
			@SuppressWarnings("unchecked")
			net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity> variant =
					(net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity>) rawVariant;
			if (variant.getWidth() > SAFE_MAX_RUNWAY_TILE_SIZE) {
				continue;
			}
			if (smallest == null || variant.getWidth() < smallest.getWidth()) {
				smallest = variant;
			}
			if (variant.getWidth() <= runwayWidth && variant.getWidth() > bestFittingSize) {
				best = variant;
				bestFittingSize = variant.getWidth();
			}
		}
		if (smallest == null) {
			// Every registered variant (interior AND smaller-variant array) exceeded SAFE_MAX_RUNWAY_TILE_SIZE - shouldn't normally happen given CARRIER_RUNWAY_TILE_SIZES' own smallest entries, but falls back to the border tile's own confirmed-safe size directly rather than ever returning null.
			return com.example.tudursvehiclemod.registry.ModEntityTypes.CARRIER_RUNWAY_BORDER_PLATFORM;
		}
		// Nothing safe actually fits within this runway's own width at all - falls back to the smallest safe variant, per this method's own doc.
		return bestFittingSize < 0f ? smallest : best;
	}

	private void tudursvehiclemod$updateCarrierRunwayPlatform() {
		VehicleDefinition def = this.getDefinition();
		if (def.runways().isEmpty()) {
			return;
		}
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		// Rather than continuing to be maintained/repositioned indefinitely while it sinks (previously only actually cleaned up via onRemoved() once the mothership itself is finally fully removed, much later).
		if (this.tudursvehiclemod$isDestroyed()) {
			for (RunwayTileState state : this.runwayTileStates) {
				tudursvehiclemod$discardAndClearRunwayTiles(serverWorld, state.interior);
			}
			return;
		}
		// Processes each in turn, using that runway's own dedicated RunwayTileState (see that class's own doc) rather than one shared set of tracking collections - everything below is otherwise identical to the original single-runway behaviour, just repeated once per runway.
		for (int runwayIndex = 0; runwayIndex < def.runways().size(); runwayIndex++) {
			com.example.tudursvehiclemod.asset.RunwayDefinition baseRunway = def.runways().get(runwayIndex);
			RunwayTileState state = this.tudursvehiclemod$getOrCreateRunwayTileState(runwayIndex);
			// Per RunwayDefinition's own hatchGated doc (rotation-type hatch, e.g. a landing craft's own bow ramp): this runway simply doesn't exist at all while its own hatch is closed - no partial/mid-swing tracking of any kind. Discards any tiles left over from before it last closed, then skips this runway entirely for the rest of this tick.
			if (baseRunway.hatchGated() && !this.isHatchOpen()) {
				tudursvehiclemod$discardAndClearRunwayTiles(serverWorld, state.interior);
				continue;
			}
			// Per RunwayDefinition's own hatchOffsetX/Y/Z doc (slide-type hatch, e.g. a carrier's own elevator deck): bakes this tick's own hatch progress into a NEW, offset-adjusted RunwayDefinition - a no-op whenever this runway has no hatch offset configured at all (see that method's own doc). Kept as a SEPARATE final variable (rather than reassigning baseRunway) since positionFunc below is a lambda, and lambdas can only capture effectively-final locals - every computation below reads centerX()/heightY()/startZ()/endZ() from THIS variable, so nothing further needs to change to pick up the offset.
			final com.example.tudursvehiclemod.asset.RunwayDefinition runway = baseRunway.tudursvehiclemod$withHatchProgress(this.tudursvehiclemod$getRunwayHatchProgress(runwayIndex));
			if (!state.loggedFound) {
				state.loggedFound = true;
				org.slf4j.LoggerFactory.getLogger("VehicleMod/Carrier").info(
						"[Carrier] Runway #{} found on entity id={}: width={}, centerX={}, heightY={}, startZ={}, endZ={}, vehicle pos=({},{},{})",
						runwayIndex, this.getId(), runway.width(), runway.centerX(), runway.heightY(), runway.startZ(), runway.endZ(),
						this.getX(), this.getY(), this.getZ());
			}

			double minZ = Math.min(runway.startZ(), runway.endZ());
			double maxZ = Math.max(runway.startZ(), runway.endZ());
			double length = maxZ - minZ;
			double halfWidth = runway.width() / 2.0;
			// Picks whichever registered discrete size (see ModEntityTypes' own CARRIER_RUNWAY_TILE_SIZES doc) best fits THIS runway, rather than always using one single global size - restores the 3-column formula's own boundary-anchoring property (see its own doc below) for any runway at least as wide as the smallest registered variant, instead of only ones wider than whatever the server-wide "expected width" config happens to be set to.
			net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity> tileType = tudursvehiclemod$selectCarrierRunwayTileType(runway.width());
			float tileSize = tileType.getWidth();
			// Fixed-size, axis-aligned tiles positioned along the ship's own rotated local grid don't perfectly tile edge-to-edge as the rotation changes; spacing tile CENTERS closer together than the tile's own actual size (0.7x) creates genuine overlap to close those gaps, at the cost of more tiles for the same runway area.
			double carrierRunwayTileSpacing = tileSize * 0.7;
			// The 3-column placement formula anchors its own OUTER tile edges to the runway's own configured half-width ONLY when the tile size fits within the runway's own full width - once the tile size exceeds the runway's own width, (width - tileSize) goes negative and the outer tiles' own FAR edges are no longer anchored to anything, growing unbounded with tile size instead. Falls back to a SINGLE centered tile in that case - now only actually needed for a runway narrower than even the SMALLEST registered variant (see tudursvehiclemod$selectCarrierRunwayTileType()'s own doc), since per-runway size selection above already avoids this for anything wider than that.
			int columns = Math.max(1, (int) Math.ceil(runway.width() / carrierRunwayTileSpacing));
			int rows = Math.max(1, (int) Math.ceil(length / carrierRunwayTileSpacing));
			int tileCount = columns * rows;

			double combinedYawRad = Math.toRadians(this.getYaw());
			double forwardX = -Math.sin(combinedYawRad);
			double forwardZ = Math.cos(combinedYawRad);
			double rightX = forwardZ;
			double rightZ = -forwardX;
			double surfaceWorldY = this.getY() + runway.heightY();

			if (!state.loggedPlatformTiles) {
				state.loggedPlatformTiles = true;
				org.slf4j.LoggerFactory.getLogger("VehicleMod/Carrier").info(
						"[Carrier] Runway #{} platform tiles: entity id={}, columns={}, rows={}, tileCount={}", runwayIndex, this.getId(), columns, rows, tileCount);
			}

			this.tudursvehiclemod$updateCarrierRunwayTileSet(
					serverWorld, tileType, tileSize, tileCount, runwayIndex,
					i -> {
						int col = i % columns;
						int row = i / columns;
						// Evenly spaced tile centers across the runway's own width/length - a single column/row sits at the midpoint; more than one spans edge to edge with equal gaps, so adjacent tiles' own edges meet/slightly overlap rather than leaving gaps, regardless of how evenly the selected tile size actually divides the runway's own actual width/length.
						double tileLocalX = columns == 1 ? runway.centerX() : runway.centerX() - halfWidth + tileSize / 2.0 + col * ((runway.width() - tileSize) / Math.max(1, columns - 1));
						double tileLocalZ = rows == 1 ? (minZ + maxZ) / 2.0 : minZ + tileSize / 2.0 + row * ((length - tileSize) / Math.max(1, rows - 1));
						return new double[] {this.getX() + tileLocalX * rightX + tileLocalZ * forwardX, this.getY(), this.getZ() + tileLocalX * rightZ + tileLocalZ * forwardZ};
					},
					surfaceWorldY, state.interior, state.interiorMissingTicks, CARRIER_RUNWAY_TILES_SPAWNED_PER_TICK);
		}
	}


	/** Spawns up to spawnBudget new tiles for any index in [trackingList.size(), tileCount) whose own target chunk is currently loaded, then walks every already-tracked index, self-healing (same CARRIER_RUNWAY_MISSING_DEBOUNCE_TICKS-debounced logic as before) any that's gone missing or repositioning/zeroing velocity for any still alive. Returns how many new tiles were actually spawned this call. */
	private int tudursvehiclemod$updateCarrierRunwayTileSet(ServerWorld serverWorld,
			net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity> entityType, float tileSize, int tileCount, int runwayIndex,
			java.util.function.IntFunction<double[]> positionFunc, double surfaceWorldY,
			java.util.List<java.util.UUID> trackingList, java.util.Map<Integer, Integer> missingTicksMap, int spawnBudget) {
		int tilesSpawnedThisTick = 0;
		// This vehicle's own coordinates (typically thousands of blocks from world origin) mean a fresh entity's own DEFAULT position (0,0,0) is essentially guaranteed to fall inside a completely unrelated, unloaded chunk - constructing it there, THEN spawning it, THEN only repositioning it correctly afterward may have left it briefly un-tracked or otherwise mishandled before ever reaching its own real position at all. Every new tile's own correct position is set BEFORE it's ever added to the world at all, so it never exists anywhere else first.
		// Spawns at most spawnBudget NEW tiles per tick instead of the full remaining backlog all at once, spreading the load across several ticks rather than potentially dozens of entities all within a single one, right when the surrounding chunks may not have fully settled yet.
		for (int i = trackingList.size(); i < tileCount && tilesSpawnedThisTick < spawnBudget; i++) {
			double[] pos = positionFunc.apply(i);
			double worldX = pos[0];
			double worldZ = pos[2];
			// Skips (without counting against the per-tick budget) a tile whose own target chunk isn't loaded yet, retrying it on a later tick instead of ever spawning into a chunk that may still be generating.
			net.minecraft.util.math.ChunkPos targetChunk = new net.minecraft.util.math.ChunkPos(net.minecraft.util.math.BlockPos.ofFloored(worldX, surfaceWorldY, worldZ));
			if (!serverWorld.isChunkLoaded(targetChunk.toLong())) {
				continue;
			}
			try {
				com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity platform =
						new com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity(entityType, serverWorld);
				platform.refreshPositionAndAngles(worldX, surfaceWorldY - entityType.getHeight(), worldZ, 0f, 0f);
				platform.setInvisible(true);
				platform.setInvulnerable(true);
				platform.setSilent(true);
				platform.setNoGravity(true);
				// This entity is a MobEntity descendant again (see CarrierRunwayPlatformEntity's own doc) and therefore subject to MobEntity's own natural distance-based despawning unless explicitly opted out - this call was correctly present in an earlier bare-Entity version of this design (where it didn't even exist, being MobEntity-specific) but was never re-added after switching back to a MobEntity-based one.
				platform.setPersistent();
				platform.tudursvehiclemod$setMothership(this.getUuid());
				// This tile's own identity (which mothership, which runway, which grid index) needs to be known client-side too, since the position FORMULA itself is already client-computable (VehicleDefinition/RunwayDefinition are ordinary client-visible data) but WHICH runway/index this specific tile instance represents is not, without this.
				platform.tudursvehiclemod$setTileIdentity(this.getId(), runwayIndex, i);
				boolean spawned = serverWorld.spawnEntity(platform);
				org.slf4j.LoggerFactory.getLogger("VehicleMod/Carrier").info(
						"[Carrier] Runway platform tile spawn attempt: entity id={}, entityType={}, tileIndex={}, spawned={}, platformUuid={}, platformId={}, platformPos=({},{},{}), platformRemoved={}",
						this.getId(), entityType, i, spawned, platform.getUuid(), platform.getId(),
						platform.getX(), platform.getY(), platform.getZ(), platform.isRemoved());
				if (!spawned) {
					break;
				}
				trackingList.add(platform.getUuid());
				tilesSpawnedThisTick++;
			} catch (Exception e) {
				org.slf4j.LoggerFactory.getLogger("VehicleMod/Carrier").error(
						"[Carrier] Runway platform tile spawn threw an exception: entity id={}, entityType={}", this.getId(), entityType, e);
				break;
			}
		}

		for (int i = 0; i < tileCount && i < trackingList.size(); i++) {
			Entity platformEntity = serverWorld.getEntity(trackingList.get(i));
			double[] pos = positionFunc.apply(i);
			double worldX = pos[0];
			double worldZ = pos[2];
			if (!(platformEntity instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity platform) || platform.isRemoved()) {
				// Only actually regenerates once a tile has been CONFIRMED missing (its own expected chunk loaded, entity still absent) for CARRIER_RUNWAY_MISSING_DEBOUNCE_TICKS consecutive ticks in a row, not on the very first observation - filters out a transient false-positive (a chunk that just finished loading may have its own blocks ready before its own entity-tracking has fully caught up).
				net.minecraft.util.math.ChunkPos expectedChunk = new net.minecraft.util.math.ChunkPos(net.minecraft.util.math.BlockPos.ofFloored(worldX, surfaceWorldY, worldZ));
				if (!serverWorld.isChunkLoaded(expectedChunk.toLong())) {
					// Own chunk isn't even loaded - genuinely can't tell if this tile still exists or not (very likely does, just untracked right now). Resets any partial debounce progress rather than letting it accumulate across an unrelated gap.
					missingTicksMap.remove(i);
					continue;
				}
				int missingTicks = missingTicksMap.getOrDefault(i, 0) + 1;
				if (missingTicks < CARRIER_RUNWAY_MISSING_DEBOUNCE_TICKS) {
					missingTicksMap.put(i, missingTicks);
					continue;
				}
				missingTicksMap.remove(i);
				try {
					com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity healedPlatform =
							new com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity(entityType, serverWorld);
					healedPlatform.refreshPositionAndAngles(worldX, surfaceWorldY - entityType.getHeight(), worldZ, 0f, 0f);
					healedPlatform.setInvisible(true);
					healedPlatform.setInvulnerable(true);
					healedPlatform.setSilent(true);
					healedPlatform.setNoGravity(true);
					healedPlatform.setPersistent();
					healedPlatform.tudursvehiclemod$setMothership(this.getUuid());
					healedPlatform.tudursvehiclemod$setTileIdentity(this.getId(), runwayIndex, i);
					if (serverWorld.spawnEntity(healedPlatform)) {
						org.slf4j.LoggerFactory.getLogger("VehicleMod/Carrier").info(
								"[Carrier] Runway platform tile self-healed (confirmed missing for {} consecutive ticks, own chunk loaded throughout): entity id={}, entityType={}, tileIndex={}, newPlatformUuid={}",
								missingTicks, this.getId(), entityType, i, healedPlatform.getUuid());
						trackingList.set(i, healedPlatform.getUuid());
					}
				} catch (Exception e) {
					org.slf4j.LoggerFactory.getLogger("VehicleMod/Carrier").error(
							"[Carrier] Runway platform tile self-heal threw an exception: entity id={}, entityType={}, tileIndex={}", this.getId(), entityType, i, e);
				}
				continue;
			}
			missingTicksMap.remove(i);
			platform.setPosition(worldX, surfaceWorldY - entityType.getHeight(), worldZ);
			platform.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
		}
		return tilesSpawnedThisTick;
	}

	/** Sets velocity to compensate for this vehicle's own per-tick translation/rotation (plus acceleration prediction, since the velocity set here isn't consumed until the candidate's own NEXT physics tick) for every entity standing on the runway. No persistent per-entity state or damping - recomputed fresh each tick from actual current position. No-op if this vehicle has no runway or hasn't moved/turned this tick. */
	public void tudursvehiclemod$carryRunwayDeckEntities() {
		VehicleDefinition def = this.getDefinition();
		if (def.runways().isEmpty()) {
			return;
		}
		double currentX = this.getX();
		double currentY = this.getY();
		double currentZ = this.getZ();
		float currentYaw = this.getYaw();
		if (Double.isNaN(this.carrierPrevTickX)) {
			// First tick - nothing to compare against yet.
			this.carrierPrevTickX = currentX;
			this.carrierPrevTickY = currentY;
			this.carrierPrevTickZ = currentZ;
			this.carrierPrevTickYaw = currentYaw;
			return;
		}
		double deltaX = currentX - this.carrierPrevTickX;
		double deltaY = currentY - this.carrierPrevTickY;
		double deltaZ = currentZ - this.carrierPrevTickZ;
		float deltaYaw = net.minecraft.util.math.MathHelper.wrapDegrees(currentYaw - this.carrierPrevTickYaw);
		// A sudden, implausibly large single-tick jump (e.g. from a seat-mount/camera-related position or rotation snap when boarding) would otherwise be misread as genuine movement and carried as a violent displacement onto anything on the runway. A real Carrier's own top speed is nowhere near this - treats it as a spurious jump instead, resetting tracking to the new state without carrying anything this tick (same as the very first tick, with no meaningful previous position to compare against).
		if (Math.abs(deltaX) > 5.0 || Math.abs(deltaY) > 5.0 || Math.abs(deltaZ) > 5.0 || Math.abs(deltaYaw) > 30.0) {
			return;
		}
		boolean hasMovement = !(Math.abs(deltaX) < 1.0E-5 && Math.abs(deltaY) < 1.0E-5 && Math.abs(deltaZ) < 1.0E-5 && Math.abs(deltaYaw) < 1.0E-5);
		if (!hasMovement) {
			this.carrierPrevDeltaX = 0.0;
			this.carrierPrevDeltaY = 0.0;
			this.carrierPrevDeltaZ = 0.0;
		}

		// This vehicle's own previous orientation is the same for every runway (it describes THIS ship, not any one deck) - hoisted out of the per-runway loop below rather than recomputed once per runway.
		double prevYawRad = Math.toRadians(this.carrierPrevTickYaw);
		double prevForwardX = -Math.sin(prevYawRad);
		double prevForwardZ = Math.cos(prevYawRad);
		double prevRightX = prevForwardZ;
		double prevRightZ = -prevForwardX;

		// The velocity this method sets doesn't actually get consumed until the candidate's OWN next physics tick - by which point an accelerating ship has already moved further than this tick's own raw delta alone would account for. Estimates this vehicle's own acceleration (this tick's own delta minus the previous tick's own) and adds it to this tick's own delta, predicting roughly one tick ahead.
		double accelX = Double.isNaN(this.carrierPrevDeltaX) ? 0.0 : deltaX - this.carrierPrevDeltaX;
		double accelY = Double.isNaN(this.carrierPrevDeltaX) ? 0.0 : deltaY - this.carrierPrevDeltaY;
		double accelZ = Double.isNaN(this.carrierPrevDeltaX) ? 0.0 : deltaZ - this.carrierPrevDeltaZ;
		// This derivative-based term is meant to compensate for SUSTAINED, gradual acceleration - but a single-tick discontinuous position jump (e.g. the Y-coordinate settle-and-snap behavior, which intentionally snaps cleanly to its own exact target rather than approaching it forever) produces one large, one-off delta spike, which this term would otherwise extrapolate into an even larger predicted correction, momentarily jolting anything being carried at that exact instant. Clamped to CARRIER_ACCEL_COMPENSATION_MAX_BLOCKS in each axis so a genuine, gradual acceleration is still compensated normally, while a single-tick anomaly can't be amplified into a visible jolt.
		accelX = net.minecraft.util.math.MathHelper.clamp(accelX, -CARRIER_ACCEL_COMPENSATION_MAX_BLOCKS, CARRIER_ACCEL_COMPENSATION_MAX_BLOCKS);
		accelY = net.minecraft.util.math.MathHelper.clamp(accelY, -CARRIER_ACCEL_COMPENSATION_MAX_BLOCKS, CARRIER_ACCEL_COMPENSATION_MAX_BLOCKS);
		accelZ = net.minecraft.util.math.MathHelper.clamp(accelZ, -CARRIER_ACCEL_COMPENSATION_MAX_BLOCKS, CARRIER_ACCEL_COMPENSATION_MAX_BLOCKS);
		double predictedDeltaX = deltaX + accelX;
		double predictedDeltaY = deltaY + accelY;
		double predictedDeltaZ = deltaZ + accelZ;
		this.carrierPrevDeltaX = deltaX;
		this.carrierPrevDeltaY = deltaY;
		this.carrierPrevDeltaZ = deltaZ;

		double deltaYawRad = Math.toRadians(deltaYaw);
		double cos = Math.cos(deltaYawRad);
		double sin = Math.sin(deltaYawRad);
		// Shared across every runway's own loop iteration below, so an entity already carried by one deck isn't mistakenly re-processed against another (which would double-apply its own displacement) - and so the launch-linked-aircraft section further down (unchanged, still runs once after every runway) sees the FULL set carried this tick, regardless of which specific runway carried each one.
		java.util.Set<java.util.UUID> carriedThisTick = new java.util.HashSet<>();

		for (int carryRunwayIndex = 0; carryRunwayIndex < def.runways().size(); carryRunwayIndex++) {
			com.example.tudursvehiclemod.asset.RunwayDefinition runway = def.runways().get(carryRunwayIndex);
			// Per RunwayDefinition's own hatchGated/hatchOffsetX/Y/Z doc: this loop tests candidates against the runway's own geometry as of the END OF THE PREVIOUS TICK (see carrierPrevTickX/Y/Z/Yaw's own doc, used throughout this method for exactly that reason) - so the hatch state consulted here is likewise the PREVIOUS tick's own value (prevRunwayHatchProgress/the same isHatchOpen() flag, which doesn't itself carry tick-to-tick history but changes rarely enough that using "current" for the gate check is harmless) rather than this tick's, for consistency with everything else this method already compares against that same reference point. A hatch-gated runway that was closed as of last tick carried nothing standing on it then either, so skipping it here is exactly correct - not an approximation.
			if (runway.hatchGated() && !this.isHatchOpen()) {
				continue;
			}
			runway = runway.tudursvehiclemod$withHatchProgress(this.prevRunwayHatchProgress.getOrDefault(carryRunwayIndex, 0f));
			double minZ = Math.min(runway.startZ(), runway.endZ());
			double maxZ = Math.max(runway.startZ(), runway.endZ());
			double halfWidth = runway.width() / 2.0;
			double prevSurfaceY = this.carrierPrevTickY + runway.heightY();
			// This runway's OWN tiles already move correctly every tick (surfaceWorldY is recomputed fresh from this tick's own hatch progress every time - see tudursvehiclemod$updateCarrierRunwayPlatform()), but a candidate CARRIED here was only ever displaced by the mothership's own hull movement (deltaX/Y/Z below) - completely missing this runway's own ADDITIONAL movement whenever its hatch progress itself changes tick to tick (an elevator descending while the ship's own hull stays put moves its own deck surface without the ship itself moving at all). The tile fell out from under the candidate every tick this went unaccounted for, and the candidate free-fell the remaining distance once atSurfaceHeight's own tolerance was exceeded. Computed once per runway (not per candidate, since it doesn't depend on which candidate) as this runway's own hatch-progress-driven world-space displacement between the previous tick and this one, using the same prevForwardX/Z, prevRightX/Z local-to-world conversion already used for tile placement elsewhere - added into both carryY/rawCarryY (and, for a runway with hatchOffsetX/Z configured too, carryX/Z and rawCarryX/Z) below, on top of (not instead of) the mothership's own hull-movement contribution.
			float runwayProgressDelta = this.tudursvehiclemod$getRunwayHatchProgress(carryRunwayIndex) - this.prevRunwayHatchProgress.getOrDefault(carryRunwayIndex, 0f);
			double runwayHatchDeltaWorldX = runwayProgressDelta * runway.hatchOffsetX() * prevRightX + runwayProgressDelta * runway.hatchOffsetZ() * prevForwardX;
			double runwayHatchDeltaWorldY = runwayProgressDelta * runway.hatchOffsetY();
			double runwayHatchDeltaWorldZ = runwayProgressDelta * runway.hatchOffsetX() * prevRightZ + runwayProgressDelta * runway.hatchOffsetZ() * prevForwardZ;
			// Confirmed via the supplied [ElevatorCarryDiag]/[ElevatorCarryDiag2] logs that the candidate's own actual Y quietly lost ~0.05 blocks/tick to its own gravity/physics EVERY tick, in BOTH directions - while descending, that loss happened to point the same direction as the elevator's own movement and so went unnoticed (the relative rawCarryY delta below still landed it close enough to prevSurfaceY each tick), but while ascending it directly opposed the elevator's own upward movement, so the candidate's own net climb per tick was only ~0.16 of the elevator's own full ~0.21 - a gap that grew by that same ~0.05/tick, tick after tick, until it finally exceeded atSurfaceHeight's own 1.2-block tolerance and the candidate fell out of tracking entirely, then free-fell the remaining height. This tick's own exact target surface Y (using this.getY(), captured as currentY at the very top of this method, plus runway's own PREVIOUS-progress heightY() plus this tick's own hatchProgress-driven Y delta already computed above - together exactly this runway's own CURRENT tick surface height) - used below to correct rawCarryY as an ABSOLUTE snap-to-surface rather than a relative delta, which cannot accumulate drift from gravity or any other stray per-tick nudge regardless of direction, since it's recomputed fresh from the candidate's own actual current position every single tick.
			double currentSurfaceY = currentY + runway.heightY() + runwayHatchDeltaWorldY;

			double searchRadius = Math.max(halfWidth, Math.max(Math.abs(minZ), Math.abs(maxZ))) + 2.0;
			net.minecraft.util.math.Box searchBox = new net.minecraft.util.math.Box(
					this.carrierPrevTickX - searchRadius, prevSurfaceY - 1.0, this.carrierPrevTickZ - searchRadius,
				this.carrierPrevTickX + searchRadius, prevSurfaceY + 2.5, this.carrierPrevTickZ + searchRadius);
		// AbstractVehicleEntity extends Entity directly, NOT LivingEntity - searching only LivingEntity meant a parked vehicle could never be found as a candidate in the first place. Same (LivingEntity OR AbstractVehicleEntity) filter checkEntityCrashDamage() already uses elsewhere in this file.
		// Previously also excluded an AircraftEntity under "active Carrier control" via tudursvehiclemod$isUnderActiveCarrierControl() - that method had been permanently hardcoded to return false (for the full history of why), making this clause a permanent no-op with zero effect on the actual candidate set. Removed entirely, along with that now-unused method, rather than leaving inert dead code in place.
		java.util.List<Entity> candidates = this.getEntityWorld().getOtherEntities(this, searchBox,
				e -> (e instanceof LivingEntity || e instanceof AbstractVehicleEntity)
						&& !(e instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity) && e.getVehicle() == null);
			for (Entity candidate : candidates) {
				if (carriedThisTick.contains(candidate.getUuid())) {
					// An entity already carried by an EARLIER runway this same tick (e.g. standing near the boundary between two decks) is not re-processed against a LATER one - its own displacement for this tick has already been fully applied.
					continue;
				}
				double relX = candidate.getX() - this.carrierPrevTickX;
				double relZ = candidate.getZ() - this.carrierPrevTickZ;
				double localX = relX * prevRightX + relZ * prevRightZ;
				double localZ = relX * prevForwardX + relZ * prevForwardZ;
				boolean withinFootprint = localX >= runway.centerX() - halfWidth - 2.0 && localX <= runway.centerX() + halfWidth + 2.0
						&& localZ >= minZ - 2.0 && localZ <= maxZ + 2.0;
				boolean atSurfaceHeight = Math.abs(candidate.getY() - prevSurfaceY) < 1.2;
				if (!withinFootprint || !atSurfaceHeight) {
					continue;
				}
				carriedThisTick.add(candidate.getUuid());
				if (candidate instanceof AircraftEntity aircraftCandidate) {
					// Unconditional, regardless of this candidate's own current speed - "on the runway, below flight-supporting speed" (the atSurfaceHeight/withinFootprint match above already implies this) is always treated as landed while matched here, exactly as intended.
					aircraftCandidate.tudursvehiclemod$suppressStallWhileCarried();
				}
				if (!hasMovement && Math.abs(runwayHatchDeltaWorldX) < 1.0E-5 && Math.abs(runwayHatchDeltaWorldY) < 1.0E-5 && Math.abs(runwayHatchDeltaWorldZ) < 1.0E-5) {
					// Calling move()/setVelocity() every tick even with near-zero values (while the ship itself is stationary) was fighting the runway tile's own natural collision/push-support resolution for control of this candidate's own position. Nothing left to actually displace here (carryX/Y/Z would all be ~0 anyway) - skips the calls below entirely, leaving the tile's own natural collision entirely undisturbed. A vehicle riding an elevator-type runway could be destroyed - see runwayHatchDeltaWorldX/Y/Z's own doc: this runway's OWN hatch-driven movement must still count as movement here even when the ship's own hull (hasMovement) doesn't, or an elevator operated while the mothership sits still would skip carrying entirely.
					continue;
				}
				// The tangential displacement this candidate's own offset from the ship's pivot sweeps through this tick's own rotation, PLUS the ship's own predicted (acceleration-compensated) translation - genuinely needed only for the setVelocity() path further below (a mob's own value is consumed on ITS OWN next physics tick, one tick after this one).
				double rotatedRelX = relX * cos - relZ * sin;
				double rotatedRelZ = relX * sin + relZ * cos;
				double tangentialX = rotatedRelX - relX;
				double tangentialZ = rotatedRelZ - relZ;
				double carryX = predictedDeltaX + tangentialX + runwayHatchDeltaWorldX;
				double carryY = predictedDeltaY + runwayHatchDeltaWorldY;
				double carryZ = predictedDeltaZ + tangentialZ + runwayHatchDeltaWorldZ;
				// The player-teleport and vehicle-move() paths both apply their own displacement IMMEDIATELY, this same tick - unlike setVelocity() above, they never wait for a "next tick" at all, so the acceleration-compensated prediction's own premise doesn't hold for them, and using it anyway amplifies (then inverts, then amplifies again) any natural tick-to-tick variation in the ship's own raw delta into a visible oscillation. Raw (non-predicted) displacement used for those two paths instead.
				double rawCarryX = deltaX + tangentialX + runwayHatchDeltaWorldX;
				double rawCarryY = currentSurfaceY - candidate.getY();
				double rawCarryZ = deltaZ + tangentialZ + runwayHatchDeltaWorldZ;
				if (candidate instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
					// Ordinary player walking is driven directly by their own client-side input/position packets, not the server-side velocity field the way a mob/vehicle's own movement is - velocity alone was never going to reliably carry a player at all. requestTeleport() with this same incremental delta is what actually forces/syncs a player's own state reliably, for both position and rotation together.
					serverPlayer.networkHandler.requestTeleport(
							candidate.getX() + rawCarryX, candidate.getY() + rawCarryY, candidate.getZ() + rawCarryZ,
							candidate.getYaw() + deltaYaw, candidate.getPitch());
				} else if (candidate instanceof AbstractVehicleEntity parkedVehicle) {
					// Applied immediately, on top of whatever (typically minimal, for a normally-parked vehicle) movement the candidate's own physics already produced this tick - no dependency on the candidate's own tick() ever consuming a velocity value through its own code paths at all.
					candidate.move(net.minecraft.entity.MovementType.SELF, new net.minecraft.util.math.Vec3d(rawCarryX, rawCarryY, rawCarryZ));
					// That version's own fix for the SAME "jerky old-position-vs-new-position alternation" symptom being reported now also set candidate.setVelocity() here, but CRITICALLY also set candidate.velocityDirty = true immediately after - a step this project's own earlier reintroduction attempt (this session) omitted, which may explain why it appeared to have no positive effect. That attempt was reverted after a direct report of large position disruption, but that regression coincided with a SEPARATELY-added early-tick velocity-zeroing hook (since removed entirely) - the interaction between that hook and setVelocity(), not setVelocity() itself, was the more likely cause. Safe to retry now: the early-zero hook is gone, and wheel-spin/crawler-track animation suppression (tudursvehiclemod$updateWheelSpinPhase()/tudursvehiclemod$updateCrawlerTrackPhase(), see their own doc) is fully decoupled from velocity, so this value is never consumed as self-propelled momentum by anything - it exists purely so entity-tracking sync actually includes a velocity update for observing/riding clients to smooth/extrapolate with, which plain move() alone does not guarantee.
					candidate.setVelocity(rawCarryX, rawCarryY, rawCarryZ);
					candidate.velocityDirty = true;
					// Tudursvehiclemod$getActualForwardSpeed() (which drives wheel spin - see that method's own doc) measures WORLD-SPACE displacement since this candidate's own lastTickX/Z were last recorded, in its own tick() (which runs BEFORE this carry, since carryRunwayDeckEntities() is invoked via END_WORLD_TICK - see VehicleMod's own onInitialize() doc). Left uncorrected, the carry displacement just applied above becomes indistinguishable from genuine self-propelled movement the NEXT time the candidate's own tick() measures its own speed. Advancing lastTickX/Z to the POST-carry position here means that measurement's own baseline already accounts for the carry, so only movement BEYOND it - i.e., the car's own actual throttle-driven motion, whether stationary on the runway or moving under its own power - registers as speed, exactly matching what "the car's own relative position on the runway didn't change" should produce: zero.
					parkedVehicle.lastTickX = candidate.getX();
					parkedVehicle.lastTickZ = candidate.getZ();
					parkedVehicle.hasLastTickPosition = true;
					parkedVehicle.tudursvehiclemod$runwayCarryActive = true;
					parkedVehicle.dataTracker.set(RUNWAY_CARRY_WHEEL_SYNC, true);
				} else {
					// A plain LivingEntity (mob, not a vehicle) - unaffected by the cruiseSpeed issue above (mobs have no such field), so the normal velocity-based approach still applies cleanly here.
					candidate.setVelocity(carryX, carryY, carryZ);
					candidate.velocityDirty = true;
				}
				if (Math.abs(deltaYaw) > 1.0E-5) {
					if (candidate instanceof LivingEntity livingCandidate) {
						livingCandidate.setYaw(livingCandidate.getYaw() + deltaYaw);
						livingCandidate.setHeadYaw(livingCandidate.getHeadYaw() + deltaYaw);
					} else {
						// A parked vehicle (AbstractVehicleEntity) - no separate "head" concept, just its own body yaw.
						candidate.setYaw(candidate.getYaw() + deltaYaw);
						if (candidate instanceof AbstractVehicleEntity parkedVehicleForYaw) {
							// Advances this candidate's own lastYaw to the POST-carry value here, so the next tick's own yaw-delta measurement (tudursvehiclemod$getCrawlerTrackSpeed()'s own use of lastYaw) already accounts for this turn - the same reasoning as lastTickX/lastTickZ's own correction above, for rotation instead of position.
							parkedVehicleForYaw.lastYaw = candidate.getYaw();
						}
						if (candidate instanceof AircraftEntity aircraftCandidate2) {
							// SetYaw() alone has no effect on this class's own actual rendered attitude (a separate Quaternionf) at all.
							aircraftCandidate2.tudursvehiclemod$rotateOrientationForCarry(deltaYaw);
						}
					}
				}
			}
		}

		if (!this.carrierLinkedAircraftBySeat.isEmpty() && hasMovement) {
			for (java.util.UUID linkedUuid : this.carrierLinkedAircraftBySeat.values().stream().flatMap(java.util.List::stream).toList()) {
				if (carriedThisTick.contains(linkedUuid)) {
					continue;
				}
				Entity linkedEntity = this.getEntityWorld().getEntity(linkedUuid);
				if (!(linkedEntity instanceof AircraftEntity launchingAircraft) || !launchingAircraft.tudursvehiclemod$isLaunchingFromCarrier()) {
					continue;
				}
				double relX = launchingAircraft.getX() - this.carrierPrevTickX;
				double relZ = launchingAircraft.getZ() - this.carrierPrevTickZ;
				double rotatedRelX = relX * cos - relZ * sin;
				double rotatedRelZ = relX * sin + relZ * cos;
				double carryX = deltaX + (rotatedRelX - relX);
				double carryY = deltaY;
				double carryZ = deltaZ + (rotatedRelZ - relZ);
				launchingAircraft.move(net.minecraft.entity.MovementType.SELF, new net.minecraft.util.math.Vec3d(carryX, carryY, carryZ));
				if (Math.abs(deltaYaw) > 1.0E-5) {
					launchingAircraft.setYaw(launchingAircraft.getYaw() + deltaYaw);
					launchingAircraft.tudursvehiclemod$rotateOrientationForCarry(deltaYaw);
				}
				carriedThisTick.add(linkedUuid);
			}
		}

		// Clears RUNWAY_CARRY_ACTIVE for anything that WAS carried last tick but isn't this tick (carrying genuinely stopped) - without this, the flag would stay stuck true forever the first time a vehicle is ever carried.
		for (java.util.UUID previouslyCarriedUuid : this.carrierLastCarriedCandidates) {
			if (carriedThisTick.contains(previouslyCarriedUuid)) {
				continue;
			}
			Entity previouslyCarried = this.getEntityWorld().getEntity(previouslyCarriedUuid);
			if (previouslyCarried instanceof AbstractVehicleEntity previouslyCarriedVehicle) {
				previouslyCarriedVehicle.tudursvehiclemod$runwayCarryActive = false;
				previouslyCarriedVehicle.dataTracker.set(RUNWAY_CARRY_WHEEL_SYNC, false);
			}
		}

		this.carrierPrevTickX = currentX;
		this.carrierPrevTickY = currentY;
		this.carrierPrevTickZ = currentZ;
		this.carrierPrevTickYaw = currentYaw;
		this.carrierLastCarriedCandidates = carriedThisTick;
	}


	/** Carrier landing-to-ammo conversion (see CarrierAircraftConfig's own landingToAmmoRadius doc), extended for each of this vehicle's own Carrier weapons, builds a list of recovery zones - zone 0 is always the original AddWeapon-centered one (radius landingToAmmoRadius), followed by one more zone per configured extraRecoveryPoints entry (each its own ship-local point converted to a world position via tudursvehiclemod$carrierLocalToWorld(), with its own independent radius) - then looks for a player-piloted aircraft matching that weapon's own configured aircraftFileName, sitting essentially stationary, within ANY one of those zones. If that weapon's own ammo capacity has room for one more, converts the first qualifying candidate found (checked zone by zone, in the same order as the zone list itself): despawns the aircraft, adds +1 directly to that weapon's own magazine, and relocates its own pilot to the lowest-numbered currently-empty seat on THIS vehicle - or simply dismounts them if every seat is already occupied. At most one conversion per weapon per tick, regardless of how many zones/candidates would otherwise qualify. */
	private void tudursvehiclemod$updateCarrierLandingToAmmo() {
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		VehicleDefinition def = this.getDefinition();
		for (int weaponIndex = 0; weaponIndex < def.weapons().size(); weaponIndex++) {
			WeaponDefinition weapon = def.weapons().get(weaponIndex);
			if (weapon.weaponType() != com.example.tudursvehiclemod.asset.WeaponType.CARRIER) {
				continue;
			}
			java.util.Optional<com.example.tudursvehiclemod.asset.CarrierAircraftConfig> maybeConfig = weapon.carrierAircraft();
			if (maybeConfig.isEmpty()) {
				continue;
			}
			com.example.tudursvehiclemod.asset.CarrierAircraftConfig config = maybeConfig.get();
			java.util.Optional<VehicleDefinition> maybeAircraftDef = com.example.tudursvehiclemod.asset.VehicleRegistry.getByFileName(config.aircraftFileName());
			if (maybeAircraftDef.isEmpty()) {
				continue;
			}
			this.tudursvehiclemod$ensureWeaponAmmoArraysSized(def);
			Vec3d addWeaponPos = this.tudursvehiclemod$computeWeaponSpawnPos(def, weapon, weaponIndex).pos();
			// Per CarrierAircraftConfig's own extraRecoveryPoints doc: zone 0 is always the original, unconditional AddWeapon-centered zone (landingToAmmoRadius) - every configured extraRecoveryPoints entry is an ADDITIONAL zone on top, converted from its own ship-local offset into a world position via the same rotated reference frame the landing approach point already uses. Both lists stay index-aligned with each other throughout this method.
			com.example.tudursvehiclemod.asset.WeaponOffset mountOffset = weapon.offsets().isEmpty()
					? new com.example.tudursvehiclemod.asset.WeaponOffset(0, 0, 0, 0, 0, java.util.Optional.empty())
					: weapon.offsets().get(0);
			java.util.List<Vec3d> recoveryZoneCenters = new java.util.ArrayList<>();
			java.util.List<Double> recoveryZoneRadii = new java.util.ArrayList<>();
			recoveryZoneCenters.add(addWeaponPos);
			recoveryZoneRadii.add(config.landingToAmmoRadius());
			for (com.example.tudursvehiclemod.asset.CarrierAircraftConfig.CarrierRecoveryPointConfig extraPoint : config.extraRecoveryPoints()) {
				recoveryZoneCenters.add(this.tudursvehiclemod$carrierLocalToWorld(addWeaponPos, extraPoint.x(), extraPoint.y(), extraPoint.z(), mountOffset, config.yawOffsetDegrees()));
				recoveryZoneRadii.add(extraPoint.radius());
			}
			java.util.Set<java.util.UUID> inFlightSet = this.carrierInFlightAircraft.get(weaponIndex);
			// A player-piloted aircraft trying to land was being counted against its own weapon's own capacity here, via carrierInFlightAircraft still listing it as "still out there" - blocking its own recovery. The autonomous return-to-base path never hits this at all (it recovers unconditionally on reaching AddWeapon, without ever checking capacity first), explaining why only manned landings were affected. Excludes any tracked in-flight aircraft that's ITSELF essentially landed already (within ANY recovery zone's own radius of that zone's own center, sufficiently slowed) from this count - treated as "already home", not "still out there".
			int inFlight = 0;
			if (inFlightSet != null) {
				for (java.util.UUID inFlightUuid : inFlightSet) {
					Entity inFlightEntity = serverWorld.getEntity(inFlightUuid);
					boolean essentiallyLanded = false;
					if (inFlightEntity instanceof AircraftEntity inFlightAircraft
							&& inFlightAircraft.age >= AircraftEntity.SPAWN_GRACE_TICKS
							&& inFlightAircraft.getVelocity().lengthSquared() < CARRIER_ESSENTIALLY_LANDED_SPEED_SQ) {
						for (int zoneIndex = 0; zoneIndex < recoveryZoneCenters.size(); zoneIndex++) {
							if (inFlightAircraft.getEntityPos().distanceTo(recoveryZoneCenters.get(zoneIndex)) < recoveryZoneRadii.get(zoneIndex)) {
								essentiallyLanded = true;
								break;
							}
						}
					}
					if (!essentiallyLanded) {
						inFlight++;
					}
				}
			}
			boolean atCapacity = this.weaponReserveAmmo[weaponIndex] < 0
					? this.weaponAmmo[weaponIndex] >= weapon.magazineSize()
					: this.weaponAmmo[weaponIndex] + this.weaponReserveAmmo[weaponIndex] + inFlight >= weapon.maxAmmo();
			if (atCapacity) {
				continue;
			}
			AircraftEntity recoveredAircraft = null;
			for (int zoneIndex = 0; zoneIndex < recoveryZoneCenters.size() && recoveredAircraft == null; zoneIndex++) {
				Vec3d zoneCenter = recoveryZoneCenters.get(zoneIndex);
				double zoneRadius = recoveryZoneRadii.get(zoneIndex);
				for (Entity nearby : serverWorld.getEntitiesByClass(AircraftEntity.class,
						new net.minecraft.util.math.Box(zoneCenter.subtract(zoneRadius, zoneRadius, zoneRadius), zoneCenter.add(zoneRadius, zoneRadius, zoneRadius)),
						// No longer requires a passenger at all - an UNMANNED, glide-released Carrier aircraft (see AircraftEntity's own tudursvehiclemod$updateCarrierReturnToBase() doc: it turns off drone control after its own last landing waypoint and relies on THIS exact mechanism for its own recovery) would otherwise never actually be recovered here, since it has no passenger by definition.
						// Per a bug this review surfaced: without the previous passenger requirement, a freshly-launched (unmanned) aircraft - still near AddWeapon and likely still slow in its own first few ticks after spawning - could satisfy both the radius and speed conditions immediately, getting swept right back into ammo before it ever actually got a chance to fly its own route at all. Excludes any candidate still within its own spawn-grace window (the same one already used elsewhere to stabilize a freshly-spawned aircraft).
						// Each zone's own Box already scopes this search to a cube around that zone's own center/radius (unchanged from the original AddWeapon-only behavior, not a true sphere check - see this method's own doc) - no further per-zone distance check needed here beyond the Box itself.
						e -> e instanceof AircraftEntity aircraftCandidate && aircraftCandidate.age >= AircraftEntity.SPAWN_GRACE_TICKS
								&& aircraftCandidate.getDefinition().equals(maybeAircraftDef.get())
								&& aircraftCandidate.getVelocity().lengthSquared() < CARRIER_ESSENTIALLY_LANDED_SPEED_SQ)) {
					recoveredAircraft = (AircraftEntity) nearby;
					break;
				}
			}
			if (recoveredAircraft != null) {
				Entity pilot = recoveredAircraft.getPassengerList().isEmpty() ? null : recoveredAircraft.getPassengerList().get(0);
				recoveredAircraft.discard();
				this.tudursvehiclemod$replenishWeaponAmmo(weaponIndex, 1, recoveredAircraft.getUuid());
				if (pilot != null) {
					for (int seatIndex = 0; seatIndex < def.seats().size(); seatIndex++) {
						if (this.tudursvehiclemod$mountToSeat(pilot, seatIndex)) {
							break;
						}
					}
				}
			}
		}
	}


	/** Keeping any launched aircraft's own chunk loaded even when the mothership itself has fallen out of any player's own loaded range - see carrierMothershipForcedChunks's own doc. */
	private void tudursvehiclemod$updateCarrierMothershipForcedChunks() {
		if (this.carrierLinkedAircraftBySeat.isEmpty() && this.carrierMothershipForcedChunks.isEmpty()) {
			return;
		}
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		boolean hasActiveLink = false;
		for (java.util.UUID aircraftUuid : this.carrierLinkedAircraftBySeat.values().stream().flatMap(java.util.List::stream).toList()) {
			Entity linkedAircraft = serverWorld.getEntity(aircraftUuid);
			if (linkedAircraft != null && !linkedAircraft.isRemoved()) {
				hasActiveLink = true;
				break;
			}
		}
		if (!hasActiveLink) {
			com.example.tudursvehiclemod.ChunkForceTracker.releaseAll(serverWorld, this);
			this.carrierMothershipForcedChunks = java.util.Set.of();
			return;
		}
		net.minecraft.util.math.ChunkPos centerChunk = new net.minecraft.util.math.ChunkPos(this.getBlockPos());
		java.util.Set<net.minecraft.util.math.ChunkPos> newGrid = new java.util.HashSet<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				newGrid.add(new net.minecraft.util.math.ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
			}
		}
		if (!newGrid.equals(this.carrierMothershipForcedChunks)) {
			for (net.minecraft.util.math.ChunkPos chunk : this.carrierMothershipForcedChunks) {
				if (!newGrid.contains(chunk)) {
					com.example.tudursvehiclemod.ChunkForceTracker.release(serverWorld, chunk, this);
				}
			}
			for (net.minecraft.util.math.ChunkPos chunk : newGrid) {
				if (!this.carrierMothershipForcedChunks.contains(chunk)) {
					com.example.tudursvehiclemod.ChunkForceTracker.request(serverWorld, chunk, this);
				}
			}
			this.carrierMothershipForcedChunks = newGrid;
		}
	}

	/** An AircraftEntity they're actively piloting under Carrier control switches them back to their own recorded mothership seat (see AircraftEntity's own carrierMothershipUuid/carrierMothershipSeatIndex doc); any other seat with a linked aircraft (see carrierLinkedAircraftBySeat's own doc) switches them INTO that aircraft's own pilot seat instead. Silently does nothing (no error, no message) if the target vehicle/seat is no longer valid (aircraft already gone, mothership already gone, target seat already occupied by someone else) - same "unconfigured/unavailable falls back to inert" convention as this project's other opportunistic, not-strictly-validated actions. */
	public static void tudursvehiclemod$toggleCarrierSeat(ServerPlayerEntity player) {
		AbstractVehicleEntity currentVehicle = tudursvehiclemod$getEffectiveVehicle(player);
		if (currentVehicle == null || !(player.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		if (currentVehicle instanceof AircraftEntity aircraft && aircraft.tudursvehiclemod$isCarrierPlayerControlled()) {
			// Currently piloting a Carrier-launched aircraft - switch back to the mothership seat it was launched from.
			java.util.UUID mothershipUuid = aircraft.tudursvehiclemod$getCarrierMothershipUuid();
			if (mothershipUuid == null) {
				return;
			}
			Entity mothershipEntity = serverWorld.getEntity(mothershipUuid);
			if (!(mothershipEntity instanceof AbstractVehicleEntity mothership) || mothershipEntity.isRemoved()) {
				return;
			}
			int seatIndex = aircraft.tudursvehiclemod$getCarrierMothershipSeatIndex();
			if (mothership.tudursvehiclemod$getSeatOccupant(seatIndex) != null) {
				return;
			}
			player.stopRiding();
			if (mothership.tudursvehiclemod$mountToSeat(player, seatIndex)) {
				aircraft.tudursvehiclemod$setCarrierPlayerControlled(false);
			}
			// If the mount above failed for any reason, carrierPlayerControlled is deliberately left AS-IS (still true) rather than being cleared - the player is now unmounted from the aircraft (stopRiding() already ran), but the aircraft itself still correctly reports itself as "was under player control", so a follow-up switch attempt isn't left looking at an inconsistent, already-corrupted state.
			return;
		}
		// Currently in the mothership (or some other vehicle entirely) - check whether their CURRENT seat has a linked aircraft to switch INTO.
		int seatIndex = currentVehicle.tudursvehiclemod$getAssignedSeatIndex(player);
		AircraftEntity aircraft = currentVehicle.tudursvehiclemod$findCurrentCarrierLeader(serverWorld, seatIndex);
		if (aircraft == null) {
			return;
		}
		if (aircraft.tudursvehiclemod$getSeatOccupant(0) != null) {
			return;
		}
		player.stopRiding();
		boolean mounted = aircraft.tudursvehiclemod$mountToSeat(player, 0);
		if (!mounted) {
			VehicleMod_LoggerHolder.LOGGER.warn("[Carrier] Seat switch to aircraft id={} FAILED (mountToSeat returned false) - player left unmounted, no state changed on the aircraft itself.", aircraft.getId());
			return;
		}
		aircraft.tudursvehiclemod$setCarrierPlayerControlled(true);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
				new com.example.tudursvehiclemod.network.SyncCruiseSpeedPayload(aircraft.getId(), aircraft.getCruiseSpeed(), aircraft.getThrottle()));
	}

	/** Walks carrierLinkedAircraftBySeat's own roster for seatIndex in launch order (index 0 = the formation's own original lead), returning the first entry that's still alive (not removed, not destroyed) - a destroyed or otherwise-gone leader is transparently skipped over, automatically falling through to the next aircraft in line. Returns null if the seat has no roster at all, or every aircraft in it is gone. */
	private AircraftEntity tudursvehiclemod$findCurrentCarrierLeader(ServerWorld serverWorld, int seatIndex) {
		java.util.List<java.util.UUID> roster = this.carrierLinkedAircraftBySeat.get(seatIndex);
		if (roster == null) {
			return null;
		}
		for (java.util.UUID aircraftUuid : roster) {
			Entity aircraftEntity = serverWorld.getEntity(aircraftUuid);
			if (aircraftEntity instanceof AircraftEntity aircraft && !aircraftEntity.isRemoved() && !aircraft.tudursvehiclemod$isDestroyed()) {
				return aircraft;
			}
		}
		return null;
	}

	private void tudursvehiclemod$fireCarrierLaunch(WeaponDefinition weapon, ServerWorld serverWorld, Vec3d spawnPos, Vec3d targetPos, double shooterForwardX, double shooterForwardZ, ServerPlayerEntity shooter, int mothershipWeaponIndex, int actualFormationSize) {
		org.slf4j.Logger logger = VehicleMod_LoggerHolder.LOGGER;
		java.util.Optional<com.example.tudursvehiclemod.asset.CarrierAircraftConfig> maybeConfig = weapon.carrierAircraft();
		if (maybeConfig.isEmpty()) {
			logger.warn("[Carrier] Weapon '{}' has Type=Carrier but no valid CarrierAircraft/CarrierWaypoint configuration was parsed from its own .txt file - see WeaponStatsLoader's own log output at reload time for the specific parsing warning.", weapon.weaponName());
			return;
		}
		com.example.tudursvehiclemod.asset.CarrierAircraftConfig config = maybeConfig.get();
		// Per the same reasoning as tudursvehiclemod$fireCasStrike()'s own doc: applies the user-tunable CarrierYawOffset correction on top of the shooter's own actual forward direction, before it's used for any waypoint rotation below.
		double effectiveForwardX = shooterForwardX;
		double effectiveForwardZ = shooterForwardZ;
		if (config.yawOffsetDegrees() != 0f) {
			double offsetRad = Math.toRadians(config.yawOffsetDegrees());
			double cos = Math.cos(offsetRad);
			double sin = Math.sin(offsetRad);
			effectiveForwardX = shooterForwardX * cos - shooterForwardZ * sin;
			effectiveForwardZ = shooterForwardX * sin + shooterForwardZ * cos;
		}
		// CarrierTargetYawOffset is an ADDITIONAL, more granular correction applied ONLY to the waypoint route's own rotation - stacks on top of CarrierYawOffset (which also broadly affects the landing approach direction elsewhere) for cases needing independent route fine-tuning.
		if (config.targetYawOffsetDegrees() != 0f) {
			double targetOffsetRad = Math.toRadians(config.targetYawOffsetDegrees());
			double targetCos = Math.cos(targetOffsetRad);
			double targetSin = Math.sin(targetOffsetRad);
			double rotatedX = effectiveForwardX * targetCos - effectiveForwardZ * targetSin;
			double rotatedZ = effectiveForwardX * targetSin + effectiveForwardZ * targetCos;
			effectiveForwardX = rotatedX;
			effectiveForwardZ = rotatedZ;
		}
		final double finalForwardX = effectiveForwardX;
		final double finalForwardZ = effectiveForwardZ;
		if (config.waypoints().isEmpty()) {
			// Defensive: WeaponStatsLoader's own parsing already refuses to build a CarrierAircraftConfig with an empty waypoint list at all, so this should be unreachable in practice.
			logger.warn("[Carrier] Weapon '{}' has a CarrierAircraftConfig with zero waypoints.", weapon.weaponName());
			return;
		}
		java.util.Optional<net.minecraft.util.Identifier> maybeId = com.example.tudursvehiclemod.asset.VehicleRegistry.getIdByFileName(config.aircraftFileName());
		java.util.Optional<VehicleDefinition> maybeDef = com.example.tudursvehiclemod.asset.VehicleRegistry.getByFileName(config.aircraftFileName());
		if (maybeId.isEmpty() || maybeDef.isEmpty()) {
			logger.warn("[Carrier] Weapon '{}' names CarrierAircraft='{}', but no currently-loaded vehicle file has that name - currently known vehicle file names: {}",
					weapon.weaponName(), config.aircraftFileName(),
					com.example.tudursvehiclemod.asset.VehicleRegistry.getAll().keySet());
			return;
		}
		net.minecraft.util.Identifier vehicleId = maybeId.get();
		VehicleDefinition def = maybeDef.get();
		net.minecraft.entity.EntityType<?> entityType = net.minecraft.registry.Registries.ENTITY_TYPE.get(def.entityType());

		net.minecraft.util.math.BlockPos targetBlockPos = net.minecraft.util.math.BlockPos.ofFloored(targetPos);
		double spawnX = spawnPos.x;
		double spawnY = spawnPos.y;
		double spawnZ = spawnPos.z;

		// Faces the mothership's own AddWeapon "DefaultYaw" (its own configured mount_yaw/mount_pitch, combined with the mothership's own current body orientation) - NOT any direction derived from the route/marked target at all, since a carrier aircraft launches FROM the mothership and should depart facing the way that mount is actually aimed.
		com.example.tudursvehiclemod.asset.WeaponOffset mountOffset = weapon.offsets().isEmpty()
				? new com.example.tudursvehiclemod.asset.WeaponOffset(0, 0, 0, 0, 0, java.util.Optional.empty())
				: weapon.offsets().get(0);
		Vec3d localMountDir = Vec3d.fromPolar((float) mountOffset.mountPitch(), (float) mountOffset.mountYaw());
		Vector3f worldMountDir = new Vector3f((float) localMountDir.x, (float) localMountDir.y, (float) localMountDir.z);
		this.tudursvehiclemod$getBodyOrientation().transform(worldMountDir);
		double mothershipForwardHorizontalLength = Math.sqrt(worldMountDir.x * worldMountDir.x + worldMountDir.z * worldMountDir.z);
		// Derives spawnYaw and the launch waypoint route's own rotation basis (mothershipLaunchForwardX/Z, used inside the entity-create callback below) from these SAME normalized values, rather than each independently re-deriving its own version from worldMountDir - guarantees the two can never subtly disagree, by construction.
		double mothershipLaunchForwardX = mothershipForwardHorizontalLength > 1.0e-4 ? worldMountDir.x / mothershipForwardHorizontalLength : 0.0;
		double mothershipLaunchForwardZ = mothershipForwardHorizontalLength > 1.0e-4 ? worldMountDir.z / mothershipForwardHorizontalLength : 1.0;
		float spawnYaw = mothershipForwardHorizontalLength > 1.0e-4
				? (float) Math.toDegrees(Math.atan2(-mothershipLaunchForwardX, mothershipLaunchForwardZ))
				: this.getYaw();

		float finalSpawnYaw = spawnYaw;

		// Launches actualFormationSize aircraft instead of just one, all from the SAME mount position/facing (a carrier launches sequentially from one spot, not several) - the formation offset applies only to config.waypoints() (the patrol/strike route) below, NOT launchWaypoints (the scripted liftoff path stays identical for every aircraft, so they all clear the deck the same way before diverging into formation). Index 0 is always the lead (a zero offset - unmodified route, matching the original single-aircraft behavior exactly).
		java.util.List<double[]> formationOffsets = tudursvehiclemod$computeFormationOffsets(
				actualFormationSize, config.formationType(), config.formationSpacing(), config.elementSpacing());
		// One shared perturbation per waypoint, generated ONCE here for the WHOLE staggered launch sequence, rather than independently inside each aircraft's own entity-create callback (tudursvehiclemod$spawnOneCarrierAircraft()) - every aircraft in the formation, including ones spawned several ticks later, now perturbs identically relative to the ideal route.
		java.util.List<int[]> sharedAccuracyPerturbations = new java.util.ArrayList<>();
		for (int i = 0; i < config.waypoints().size(); i++) {
			if (config.accuracy() > 0f) {
				float navigationAccuracy = config.accuracy();
				sharedAccuracyPerturbations.add(new int[]{
						Math.round((this.random.nextFloat() * 2f - 1f) * navigationAccuracy),
						Math.round((this.random.nextFloat() * 2f - 1f) * navigationAccuracy),
						Math.round((this.random.nextFloat() * 2f - 1f) * navigationAccuracy)});
			} else {
				sharedAccuracyPerturbations.add(new int[]{0, 0, 0});
			}
		}
		CarrierLaunchContext context = new CarrierLaunchContext(config, vehicleId, def, entityType, targetBlockPos,
				spawnX, spawnY, spawnZ, finalSpawnYaw, mothershipLaunchForwardX, mothershipLaunchForwardZ,
				finalForwardX, finalForwardZ, shooter != null ? shooter.getUuid() : null,
				shooter != null ? this.tudursvehiclemod$getAssignedSeatIndex(shooter) : -1, mothershipWeaponIndex,
				sharedAccuracyPerturbations);

		// The lead aircraft (index 0) always spawns immediately - matches the original single-aircraft behavior exactly when there's no formation at all (formationOffsets.size() == 1).
		double[] leadOffset = formationOffsets.get(0);
		boolean leadMustWait = formationOffsets.size() > 1;
		AircraftEntity leadAircraft = tudursvehiclemod$spawnOneCarrierAircraft(serverWorld, context, leadOffset[0], leadOffset[1], leadMustWait, 0, null);

		if (formationOffsets.size() <= 1) {
			// No formation - seat tracking happens immediately, exactly as before this whole feature existed.
			if (shooter != null && leadAircraft != null) {
				this.carrierLinkedAircraftBySeat.put(context.mothershipSeatIndex(), new java.util.ArrayList<>(java.util.List.of(leadAircraft.getUuid())));
				this.tudursvehiclemod$updateCarrierMothershipForcedChunks();
			}
			return;
		}
		// Queues the REMAINING wingmen (index 1 onward), spawned one at a time by tudursvehiclemod$updateCarrierLaunchSequences() every CARRIER_LAUNCH_INTERVAL_TICKS.
		CarrierLaunchSequence sequence = new CarrierLaunchSequence(context, formationOffsets, 1, leadAircraft != null ? leadAircraft.getUuid() : null);
		if (leadAircraft != null) {
			sequence.launchedAircraft.add(leadAircraft.getUuid());
		}
		this.carrierPendingLaunches.put(mothershipWeaponIndex, sequence);
	}

	/** Spawns exactly one Carrier aircraft - shared by tudursvehiclemod$fireCarrierLaunch() (the lead, spawned immediately) and tudursvehiclemod$updateCarrierLaunchSequences() (every wingman after it, spawned one at a time). formLateral/formLongitudinal is this specific aircraft's own formation offset (see tudursvehiclemod$computeFormationOffsets()'s own doc); waitRequired marks it as needing to orbit the mothership once its own launch waypoints finish, until the rest of the formation has also launched (see AircraftEntity's own carrierFormationPending doc) - false for the lead when there's no formation at all, and false for whichever aircraft turns out to be the LAST one spawned (nothing left to wait for). Returns null (after logging a warning) if the spawn itself failed or wasn't actually an AircraftEntity. */
	private AircraftEntity tudursvehiclemod$spawnOneCarrierAircraft(ServerWorld serverWorld, CarrierLaunchContext ctx,
			double formLateral, double formLongitudinal, boolean waitRequired, int formationIndex, java.util.UUID leadAircraftUuid) {
		org.slf4j.Logger logger = VehicleMod_LoggerHolder.LOGGER;
		com.example.tudursvehiclemod.asset.CarrierAircraftConfig config = ctx.config();
		net.minecraft.util.math.BlockPos targetBlockPos = ctx.targetBlockPos();
		double spawnX = ctx.spawnX();
		double spawnY = ctx.spawnY();
		double spawnZ = ctx.spawnZ();
		double mothershipLaunchForwardX = ctx.mothershipLaunchForwardX();
		double mothershipLaunchForwardZ = ctx.mothershipLaunchForwardZ();
		double finalForwardX = ctx.finalForwardX();
		double finalForwardZ = ctx.finalForwardZ();

		Entity spawned = ctx.entityType().create(serverWorld, entity -> {
			if (entity instanceof AbstractVehicleEntity vehicle) {
				vehicle.setVehicleDefinitionId(ctx.vehicleId());
				// Fills EVERY weapon slot (not just the one auto-fire uses) plus fuel to full - a player may take over piloting this aircraft at any time, with no chance to have prepared it themselves first.
				vehicle.tudursvehiclemod$fillAllWeaponAmmoAndFuel();
				// WEAPON_AMMO_SYNC only re-sends on specific events (firing, reload completion) - directly setting ammo like fillAllWeaponAmmoAndFuel() just did doesn't trigger that on its own, so the HUD would otherwise show 0/0 until the first shot is fired. Forces a sync immediately instead.
				vehicle.tudursvehiclemod$syncWeaponAmmo();
			}
			if (entity instanceof AircraftEntity aircraft) {
				java.util.List<com.example.tudursvehiclemod.block.DroneWaypoint> route = new java.util.ArrayList<>();
				java.util.List<Boolean> attackFlags = new java.util.ArrayList<>();
				java.util.List<java.util.Optional<Boolean>> gearFlags = new java.util.ArrayList<>();
				java.util.List<java.util.Optional<Boolean>> bayFlags = new java.util.ArrayList<>();
				// Per CarrierLaunchWaypoint's own speedBoostKmh doc: parallel to gearFlags/bayFlags (same index, same length, same Optional.empty()-for-CarrierWaypoint-entries convention below) - only launchWaypoints ever carry a real value here, since config.waypoints() entries (CasWaypoint) have no such field at all.
				java.util.List<java.util.Optional<Float>> speedBoostFlags = new java.util.ArrayList<>();
				// A separate, dedicated liftoff route (see CarrierAircraftConfig's own launchWaypoints doc) flown first, prepended ahead of the normal patrol/strike route - no accuracy perturbation (a scripted, precise departure path) and never attacks. Deliberately NOT offset by the formation (see this whole feature's own doc) - every aircraft in the formation flies this identical liftoff path.
				// Launch waypoints are positioned relative to the mothership's own AddWeapon position/orientation - reuses mothershipLaunchForwardX/Z (the SAME values spawnYaw was itself derived from) rather than the target-point-derived rotation basis the normal route uses below, since a scripted liftoff path is naturally anchored to the ship's own deck, not to some far-away marked point.
				for (com.example.tudursvehiclemod.asset.CarrierLaunchWaypoint launchWaypoint : config.launchWaypoints()) {
					double[] launchRotated = tudursvehiclemod$rotateCasOffset(launchWaypoint.relX(), launchWaypoint.relZ(), mothershipLaunchForwardX, mothershipLaunchForwardZ);
					double launchWorldX = spawnX + launchRotated[0];
					double launchWorldY = spawnY + launchWaypoint.relY();
					double launchWorldZ = spawnZ + launchRotated[1];
					int storedRelX = (int) Math.round(launchWorldX - (targetBlockPos.getX() + 0.5));
					int storedRelY = (int) Math.round(launchWorldY - targetBlockPos.getY());
					int storedRelZ = (int) Math.round(launchWorldZ - (targetBlockPos.getZ() + 0.5));
					route.add(new com.example.tudursvehiclemod.block.DroneWaypoint(
							storedRelX, storedRelY, storedRelZ,
							launchWaypoint.speedFraction(), 0f, 1.0f, 1.0f));
					attackFlags.add(false);
					gearFlags.add(launchWaypoint.gearState());
					bayFlags.add(launchWaypoint.bayState());
					speedBoostFlags.add(launchWaypoint.speedBoostKmh());
				}
				for (int i = 0; i < config.waypoints().size(); i++) {
					com.example.tudursvehiclemod.asset.CasWaypoint carrierWaypoint = config.waypoints().get(i);
					double[] rotated = tudursvehiclemod$rotateCasOffset(
							carrierWaypoint.relX(), carrierWaypoint.relZ(), finalForwardX, finalForwardZ);
					com.example.tudursvehiclemod.block.DroneWaypoint droneWaypoint = new com.example.tudursvehiclemod.block.DroneWaypoint(
							(int) Math.round(rotated[0]), carrierWaypoint.relY(), (int) Math.round(rotated[1]),
							carrierWaypoint.speedFraction(), 0f, 1.0f, 1.0f);
					// Per the same reasoning as CAS's own accuracy perturbation: applied to every waypoint here (including the first) since, unlike CAS, this aircraft's own spawn position is fixed at the AddWeapon mount point regardless of the route - perturbing waypoint 0 doesn't create any spawn-position mismatch the way it would for CAS. Uses ctx.sharedAccuracyPerturbations() (computed once for the whole staggered launch sequence) rather than an independent random value per aircraft - see this whole feature's own doc for why that mattered for formation flying specifically.
					int[] perturbation = ctx.sharedAccuracyPerturbations().get(i);
					if (perturbation[0] != 0 || perturbation[1] != 0 || perturbation[2] != 0) {
						droneWaypoint = new com.example.tudursvehiclemod.block.DroneWaypoint(
								droneWaypoint.relX() + perturbation[0], droneWaypoint.relY() + perturbation[1], droneWaypoint.relZ() + perturbation[2],
								droneWaypoint.speedFraction(), droneWaypoint.rollAngle(),
								droneWaypoint.rollManeuverabilityMultiplier(), droneWaypoint.turnManeuverabilityMultiplier());
					}
					route.add(droneWaypoint);
					attackFlags.add(carrierWaypoint.attack());
					gearFlags.add(java.util.Optional.empty());
					bayFlags.add(java.util.Optional.empty());
					speedBoostFlags.add(java.util.Optional.empty());
				}
				// Stage 1's own autonomous flight uses the exact same CAS fields/autopilot/auto-fire on AircraftEntity - no separate Carrier-specific autopilot at all. Stage 2 adds player-controllable piloting (see carrierMothershipUuid/carrierMothershipSeatIndex/carrierPlayerControlled's own doc) on top of that same autonomous behavior, rather than replacing it.
				aircraft.tudursvehiclemod$setCasWaypointRoute(route, attackFlags, config.weaponIndex());
				aircraft.tudursvehiclemod$setCarrierGearBayFlags(gearFlags, bayFlags);
				// Per CarrierLaunchWaypoint's own speedBoostKmh doc: mothershipLaunchForwardX/Z (the SAME basis finalSpawnYaw was itself derived from) is the direction waypoint 0's own boost (if any) is applied along, since this callback runs before refreshPositionAndAngles() below actually sets this aircraft's own getYaw() - see tudursvehiclemod$setCarrierSpeedBoosts()'s own doc for why a direction can't just be read off the aircraft itself yet at this point.
				aircraft.tudursvehiclemod$setCarrierSpeedBoosts(speedBoostFlags, mothershipLaunchForwardX, mothershipLaunchForwardZ);
				aircraft.tudursvehiclemod$setCarrierLaunchWaypointCount(config.launchWaypoints().size());
				aircraft.tudursvehiclemod$setCasTimeoutTicks(config.timeoutTicks());
				aircraft.tudursvehiclemod$setCasStuckTimeoutTicks(config.stuckTimeoutTicks());
				aircraft.tudursvehiclemod$setDroneLink(targetBlockPos);
				// Always set, regardless of whether a player fired the shot - needed purely so this aircraft can find the mothership's own current position to orbit around while awaiting the rest of its own formation.
				aircraft.tudursvehiclemod$setCarrierLaunchMothership(this.getUuid());
				aircraft.tudursvehiclemod$setCarrierFormationIndex(formationIndex);
				aircraft.tudursvehiclemod$setCarrierFormationPending(waitRequired);
				if (leadAircraftUuid != null) {
					aircraft.tudursvehiclemod$setDroneFormationFollow(leadAircraftUuid, formLateral, formLongitudinal);
					aircraft.tudursvehiclemod$registerFormationMember(leadAircraftUuid, false);
				} else {
					aircraft.tudursvehiclemod$registerFormationMember(aircraft.getUuid(), true);
				}
				if (ctx.shooterUuid() != null) {
					aircraft.tudursvehiclemod$setCarrierMothership(this.getUuid(), ctx.mothershipSeatIndex(), ctx.mothershipWeaponIndex(), config.yawOffsetDegrees(), config.landingYawOffsetDegrees(), config.landingWaypoints());
				}
			}
		}, net.minecraft.util.math.BlockPos.ofFloored(spawnX, spawnY, spawnZ), net.minecraft.entity.SpawnReason.TRIGGERED, false, false);

		if (spawned == null) {
			logger.warn("[Carrier] Vehicle '{}' (file '{}', entity type '{}') was found, but EntityType.create() returned null - could not launch the carrier aircraft at all.",
					ctx.vehicleId(), config.aircraftFileName(), ctx.def().entityType());
			return null;
		}
		if (!(spawned instanceof AircraftEntity spawnedAircraft)) {
			logger.warn("[Carrier] Vehicle '{}' (file '{}') is not an AircraftEntity (actual class: {}) - it will spawn, but its own drone route/waypoints were never actually assigned, since only AircraftEntity supports that.",
					ctx.vehicleId(), config.aircraftFileName(), spawned.getClass().getSimpleName());
			serverWorld.spawnEntity(spawned);
			return null;
		}
		spawned.refreshPositionAndAngles(spawnX, spawnY, spawnZ, ctx.spawnYaw(), 0f);
		spawnedAircraft.tudursvehiclemod$forceLoadSpawnChunk();
		this.tudursvehiclemod$incrementCarrierInFlightCount(ctx.mothershipWeaponIndex(), spawnedAircraft.getUuid());
		serverWorld.spawnEntity(spawned);
		return spawnedAircraft;
	}

	/** Called once per server tick from tick() itself - a no-op (immediate return) whenever carrierPendingLaunches is empty, so this costs nothing for a ship that never fires a formation Carrier launch. Ticks down each in-progress sequence's own timer; once it reaches 0, spawns the NEXT wingman (tudursvehiclemod$spawnOneCarrierAircraft()) and resets the timer. Once the LAST wingman has been spawned, signals every aircraft in the formation (tudursvehiclemod$clearCarrierFormationPending() - see AircraftEntity's own doc) that the whole group is now airborne, applies the deferred seat-tracking update (targeting whichever aircraft turned out to be launched last, same simplification the pre-staggered version already used), and removes the completed sequence. */
	private void tudursvehiclemod$updateCarrierLaunchSequences(ServerWorld serverWorld) {
		if (this.carrierPendingLaunches.isEmpty()) {
			return;
		}
		java.util.Iterator<java.util.Map.Entry<Integer, CarrierLaunchSequence>> it = this.carrierPendingLaunches.entrySet().iterator();
		while (it.hasNext()) {
			CarrierLaunchSequence sequence = it.next().getValue();
			sequence.ticksUntilNextLaunch--;
			if (sequence.ticksUntilNextLaunch > 0) {
				continue;
			}
			double[] offset = sequence.formationOffsets.get(sequence.nextIndex);
			boolean isLast = sequence.nextIndex == sequence.formationOffsets.size() - 1;
			AircraftEntity spawned = tudursvehiclemod$spawnOneCarrierAircraft(serverWorld, sequence.context, offset[0], offset[1], !isLast, sequence.nextIndex, sequence.leadAircraftUuid);
			if (spawned != null) {
				sequence.launchedAircraft.add(spawned.getUuid());
			}
			sequence.nextIndex++;
			sequence.ticksUntilNextLaunch = CARRIER_LAUNCH_INTERVAL_TICKS;
			if (sequence.nextIndex >= sequence.formationOffsets.size()) {
				for (java.util.UUID uuid : sequence.launchedAircraft) {
					if (serverWorld.getEntity(uuid) instanceof AircraftEntity aircraft) {
						aircraft.tudursvehiclemod$clearCarrierFormationPending();
					}
				}
				if (sequence.context.shooterUuid() != null) {
					this.carrierLinkedAircraftBySeat.put(sequence.context.mothershipSeatIndex(), new java.util.ArrayList<>(sequence.launchedAircraft));
					this.tudursvehiclemod$updateCarrierMothershipForcedChunks();
				}
				it.remove();
			}
		}
	}

	/** A freshly-spawned vehicle's own weaponAmmo/weaponReserveAmmo intentionally start at 0 (see tudursvehiclemod$ensureWeaponAmmoArraysSized()'s own doc - avoids an "ammo exceeds MaxAmmo" bug for a NORMAL, player-spawned vehicle, which is expected to reload/resupply itself before use) - but an autonomous CAS strike aircraft has no player around to ever do that at all, so it would otherwise never be able to fire. Fills weaponIndex's own magazine (Round, to magazineSize) and reserve (the REMAINDER of MaxAmmo after the magazine - see this method's own fix history for why not MaxAmmo itself) fully at spawn instead - called once, right after the CAS aircraft is created, before it's added to the world. */
	public void tudursvehiclemod$fillCasWeaponAmmo(int weaponIndex) {
		VehicleDefinition def = this.getDefinition();
		if (weaponIndex < 0 || weaponIndex >= def.weapons().size()) {
			return;
		}
		this.tudursvehiclemod$ensureWeaponAmmoArraysSized(def);
		WeaponDefinition weapon = def.weapons().get(weaponIndex);
		if (weapon.magazineSize() > 0) {
			this.weaponAmmo[weaponIndex] = weapon.magazineSize();
		}
		// MaxAmmo represents the TOTAL capacity (magazine + reserve combined), exactly like tryResupplyWeapon()'s own reserveCap calculation (weapon.maxAmmo() - this.weaponAmmo[weaponIndex]) already treats it - this was instead setting the reserve to the FULL MaxAmmo value on top of an ALSO-full magazine. Subtracts the magazine here too, for the same correct total.
		if (weapon.maxAmmo() > 0) {
			this.weaponReserveAmmo[weaponIndex] = Math.max(0, weapon.maxAmmo() - this.weaponAmmo[weaponIndex]);
		}
	}

	/** A player switching into a Carrier-launched aircraft could find it with no fuel and no ammo at all: both intentionally start at 0 for a NORMALLY player-spawned vehicle (see this class's own docs/IMPLEMENTATION_NOTES.md "弾薬・燃料の初期化"), on the assumption the player prepares it themselves (refuels, resupplies) before ever actually using it - but a Carrier/CAS-launched aircraft gives the player no such opportunity at all, since it spawns and starts flying autonomously before they can board it. Fills EVERY weapon slot's own magazine+reserve (not just the one weaponIndex tudursvehiclemod$fillCasWeaponAmmo() fills for CAS's own single auto-fire weapon - a player taking over piloting may want to use any of this aircraft's own weapons, not just that one) and sets fuel to this vehicle's own configured maximum. Called once by the Carrier launch logic, right after this entity is created, before it's added to the world. tudursvehiclemod$fireCasStrike() now calls this too, for every CAS aircraft it spawns (lead and wingmen alike, exactly matching how tudursvehiclemod$spawnOneCarrierAircraft() already calls this unconditionally) - a wingman never actually checks its own fuel at all while flying autonomously (its own throttle is set directly, bypassing tudursvehiclemod$setThrottleDirect()'s own fuel gate entirely), so filling it too is harmless, and keeps CAS's own spawn logic uniform rather than branching on formation position. */
	public void tudursvehiclemod$fillAllWeaponAmmoAndFuel() {
		VehicleDefinition def = this.getDefinition();
		for (int i = 0; i < def.weapons().size(); i++) {
			this.tudursvehiclemod$fillCasWeaponAmmo(i);
		}
		this.dataTracker.set(FUEL, this.getMaxFuel());
	}

	/** Adds amount directly to weaponIndex's own magazine (NOT reserve -), capped at that weapon's own configured magazineSize, then force-syncs the ammo display (same reasoning as tudursvehiclemod$fillAllWeaponAmmoAndFuel()'s own doc - directly setting ammo like this doesn't trigger WEAPON_AMMO_SYNC on its own). Also removes aircraftUuid from carrierInFlightAircraft's own set for that slot (this round is no longer "out there") - see that field's own doc for why a UUID set is used instead of a plain counter. Silently does nothing if weaponIndex is out of range. */
	public void tudursvehiclemod$replenishWeaponAmmo(int weaponIndex, int amount, java.util.UUID aircraftUuid) {
		VehicleDefinition def = this.getDefinition();
		if (weaponIndex < 0 || weaponIndex >= def.weapons().size()) {
			return;
		}
		this.tudursvehiclemod$ensureWeaponAmmoArraysSized(def);
		WeaponDefinition weapon = def.weapons().get(weaponIndex);
		if (weapon.magazineSize() > 0) {
			this.weaponAmmo[weaponIndex] = Math.min(weapon.magazineSize(), this.weaponAmmo[weaponIndex] + amount);
		}
		java.util.Set<java.util.UUID> inFlightSet = this.carrierInFlightAircraft.get(weaponIndex);
		if (inFlightSet != null) {
			inFlightSet.remove(aircraftUuid);
		}
		this.tudursvehiclemod$syncWeaponAmmo();
	}

	/** Per carrierInFlightAircraft's own doc: called once at launch time (tudursvehiclemod$fireCarrierLaunch()) to mark aircraftUuid as one more round "out there" for weaponIndex. */
	public void tudursvehiclemod$incrementCarrierInFlightCount(int weaponIndex, java.util.UUID aircraftUuid) {
		VehicleDefinition def = this.getDefinition();
		if (weaponIndex < 0 || weaponIndex >= def.weapons().size()) {
			return;
		}
		this.carrierInFlightAircraft.computeIfAbsent(weaponIndex, k -> new java.util.HashSet<>()).add(aircraftUuid);
	}

	/** Encodes carrierInFlightAircraft as "weaponIndex:uuid,uuid,..;weaponIndex:uuid,.." - see that field's own doc. */
	private String tudursvehiclemod$encodeCarrierInFlightAircraft() {
		StringBuilder sb = new StringBuilder();
		boolean firstGroup = true;
		for (java.util.Map.Entry<Integer, java.util.Set<java.util.UUID>> entry : this.carrierInFlightAircraft.entrySet()) {
			if (entry.getValue().isEmpty()) {
				continue;
			}
			if (!firstGroup) {
				sb.append(';');
			}
			firstGroup = false;
			sb.append(entry.getKey()).append(':');
			boolean firstUuid = true;
			for (java.util.UUID uuid : entry.getValue()) {
				if (!firstUuid) {
					sb.append(',');
				}
				firstUuid = false;
				sb.append(uuid);
			}
		}
		return sb.toString();
	}

	/** Decodes the format tudursvehiclemod$encodeCarrierInFlightAircraft() produces, populating carrierInFlightAircraft. Malformed entries are silently skipped. */
	private void tudursvehiclemod$readCarrierInFlightAircraft(String encoded) {
		this.carrierInFlightAircraft.clear();
		if (encoded.isEmpty()) {
			return;
		}
		for (String group : encoded.split(";", -1)) {
			int colonIndex = group.indexOf(':');
			if (colonIndex < 0) {
				continue;
			}
			try {
				int weaponIndex = Integer.parseInt(group.substring(0, colonIndex));
				java.util.Set<java.util.UUID> uuids = new java.util.HashSet<>();
				for (String uuidString : group.substring(colonIndex + 1).split(",", -1)) {
					if (!uuidString.isEmpty()) {
						uuids.add(java.util.UUID.fromString(uuidString));
					}
				}
				if (!uuids.isEmpty()) {
					this.carrierInFlightAircraft.put(weaponIndex, uuids);
				}
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	/** Encodes carrierLinkedAircraftBySeat as "seatIndex:uuid1,uuid2,uuid3;seatIndex:uuid1;.." (comma-separated roster within each seat's own entry, in launch order) - see that field's own doc. */
	private String tudursvehiclemod$encodeCarrierLinkedAircraftBySeat() {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (java.util.Map.Entry<Integer, java.util.List<java.util.UUID>> entry : this.carrierLinkedAircraftBySeat.entrySet()) {
			if (!first) {
				sb.append(';');
			}
			first = false;
			sb.append(entry.getKey()).append(':');
			for (int i = 0; i < entry.getValue().size(); i++) {
				if (i > 0) {
					sb.append(',');
				}
				sb.append(entry.getValue().get(i));
			}
		}
		return sb.toString();
	}

	/** Decodes the format tudursvehiclemod$encodeCarrierLinkedAircraftBySeat() produces, populating carrierLinkedAircraftBySeat. Malformed entries/UUIDs are silently skipped - a seat entry with zero valid UUIDs left after skipping isn't added at all. */
	private void tudursvehiclemod$readCarrierLinkedAircraftBySeat(String encoded) {
		this.carrierLinkedAircraftBySeat.clear();
		if (encoded.isEmpty()) {
			return;
		}
		for (String entry : encoded.split(";", -1)) {
			int colonIndex = entry.indexOf(':');
			if (colonIndex < 0) {
				continue;
			}
			try {
				int seatIndex = Integer.parseInt(entry.substring(0, colonIndex));
				java.util.List<java.util.UUID> roster = new java.util.ArrayList<>();
				String rosterPart = entry.substring(colonIndex + 1);
				if (!rosterPart.isEmpty()) {
					for (String uuidString : rosterPart.split(",", -1)) {
						try {
							roster.add(java.util.UUID.fromString(uuidString));
						} catch (IllegalArgumentException ignored) {
						}
					}
				}
				if (!roster.isEmpty()) {
					this.carrierLinkedAircraftBySeat.put(seatIndex, roster);
				}
			} catch (NumberFormatException ignored) {
			}
		}
	}

	/** Stage 2 of WeaponType.CAS: fires weaponIndex (this aircraft's own weapon slot) automatically while attackActive is true - called every tick from AircraftEntity's own CAS waypoint dispatch, which determines attackActive from the current waypoint's own attack flag (see that class's own casAttackFlags doc). this simply calls tryFireWeapon() itself with shooter=null, reusing that method's own weapon-position (this aircraft's own configured mount/offset, not any target-aiming computation), ammo/magazine, cooldown, and reload handling completely unchanged - rather than a separate, simplified reimplementation. Guided weapon types (AS_MISSILE/MK_ROCKET/AA_MISSILE/AT_MISSILE/MISSILE/TV_MISSILE) simply fire unguided/straight when shooter is null (guided ordnance isn't expected to work for a CAS aircraft at all) - see tryFireWeapon()'s own now-nullable-shooter handling for exactly where each of those is skipped. Calling this every tick while attackActive is naturally rate-limited by tryFireWeapon()'s own cooldown/magazine/reload checks, the exact same way a player holding down the fire key already works. */
	protected void tudursvehiclemod$updateCasAutoFire(boolean attackActive, int weaponIndex) {
		if (attackActive) {
			this.tryFireWeapon(weaponIndex, null);
		}
	}

	/** Turns the highlight back off for entityId after durationTicks - see tudursvehiclemod$fireTargetingPod()'s own doc for why this is needed at all (neither setGlowing() nor HIGHLIGHT_ACTIVE is a timed effect on its own). Looks the entity back up by ID each tick rather than holding a direct reference, since it may have been unloaded/removed/changed dimension in the meantime. */
	private void tudursvehiclemod$scheduleGlowOff(int entityId, int durationTicks) {
		int[] ticksRemaining = {Math.max(1, durationTicks)};
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ticksRemaining[0] <= 0) {
				return;
			}
			ticksRemaining[0]--;
			if (ticksRemaining[0] == 0 && this.getEntityWorld() instanceof ServerWorld serverWorld) {
				Entity target = serverWorld.getEntityById(entityId);
				if (target != null) {
					tudursvehiclemod$setEntityHighlighted(target, false);
				}
			}
		});
	}

	/** See tudursvehiclemod$fireTargetingPod()'s own doc for which category names are recognized here - matches MC Heli's own documented Target values (planes/helicopters/vehicles/players/monsters/others). Takes a plain Entity (not LivingEntity) since a spottable AircraftEntity/AbstractVehicleEntity is not a LivingEntity at all. */
	private boolean tudursvehiclemod$matchesTargetingPodCategory(Entity candidate, java.util.Set<String> categories) {
		if (candidate instanceof net.minecraft.entity.player.PlayerEntity) {
			return categories.contains("players");
		}
		if (candidate instanceof AircraftEntity) {
			return categories.contains("planes") || categories.contains("helicopters");
		}
		if (candidate instanceof AbstractVehicleEntity) {
			return categories.contains("vehicles");
		}
		if (candidate instanceof net.minecraft.entity.mob.Monster) {
			return categories.contains("monsters");
		}
		if (candidate instanceof net.minecraft.entity.LivingEntity) {
			return categories.contains("others");
		}
		return false;
	}

	/** Spawns a lingering particle outline at blockPos for durationTicks - see tudursvehiclemod$fireTargetingPod()'s own doc for the block-marking mode this serves (no real entity exists to apply a status effect to, so this is the closest equivalent). */
	private void tudursvehiclemod$scheduleBlockMarker(ServerWorld world, net.minecraft.util.math.BlockPos blockPos, int durationTicks) {
		net.minecraft.particle.DustParticleEffect effect = new net.minecraft.particle.DustParticleEffect(0xFFFF5555, 1.5f);
		double cx = blockPos.getX() + 0.5, cy = blockPos.getY() + 1.05, cz = blockPos.getZ() + 0.5;
		int totalWaves = Math.max(1, durationTicks / SMOKE_WAVE_INTERVAL_TICKS);
		int[] tickCount = {0};
		int[] wavesSpawned = {0};
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (wavesSpawned[0] >= totalWaves) {
				return;
			}
			tickCount[0]++;
			if (tickCount[0] % SMOKE_WAVE_INTERVAL_TICKS == 0) {
				world.spawnParticles(effect, true, true, cx, cy, cz, 4, 0.3, 0.05, 0.3, 0.0);
				wavesSpawned[0]++;
			}
		});
	}

	/** Lazily (re)sizes weaponAmmo/weaponReloadTicksRemaining to match the current definition's weapon count, initializing any newly-added slot's ammo to a full.. */
	/** Torpedo's own low-altitude requirement. */
	private boolean tudursvehiclemod$isNearSurfaceForTorpedo() {
		Vec3d start = this.getEntityPos();
		Vec3d end = start.add(0, -TORPEDO_SURFACE_SEARCH_DEPTH, 0);
		net.minecraft.world.RaycastContext context = new net.minecraft.world.RaycastContext(start, end,
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.SOURCE_ONLY, this);
		net.minecraft.util.hit.BlockHitResult hit = this.getEntityWorld().raycast(context);
		if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
			return false;
		}
		return start.y - hit.getPos().y <= TORPEDO_MAX_ALTITUDE_ABOVE_SURFACE;
	}

	/** CAS/Carrier's own default ballistic stats (see tudursvehiclemod$computeBallisticTargetPoint()'s own doc) land at approximately 1000 blocks when fired at a 45-degree elevation - both chosen as integers, confirmed numerically against THIS project's own drag-aware trajectory physics (0.99x/tick air resistance - see client.hud.MortarMarkerRenderer's own PROJECTILE_AIR_DRAG_PER_TICK doc for why that specific value matters here), not the simpler drag-free "v^2/g" formula alone (velocity=197/gravity=32 gives ~839 blocks drag-free at 45 degrees, but ~1000 with this project's own real, drag-aware physics - the pair actually used here). */
	private static final float CAS_CARRIER_DEFAULT_VELOCITY = 197f;
	private static final float CAS_CARRIER_DEFAULT_GRAVITY = 32f;
	/** Vanilla's own ThrownItemEntity air drag (see that class's own applyDrag()) - matches client.hud.MortarMarkerRenderer's own identical constant, kept as a separate definition here since src/main (this file, shared client+server) can't reference a src/client-only class at all (that source set doesn't exist on a dedicated server). */
	private static final double CAS_CARRIER_BALLISTIC_AIR_DRAG_PER_TICK = 0.99;
	/** This weapon TYPE's own appropriate raycast range - unlike tudursvehiclemod$raycastGroundPoint() (AS_MISSILE's own use, MISSILE_TARGET_SEARCH_RANGE = 128 blocks, tuned for a much shorter-range weapon), a CAS/Carrier strike is meant to reach roughly as far as BALLISTIC's own default stats already do (see CAS_CARRIER_DEFAULT_VELOCITY/GRAVITY's own doc, ~1000 blocks at a 45-degree aim) - a mere 128-block raycast range meant RAYCAST mode simply couldn't reach anywhere near that far regardless of aim, which is almost certainly why it "wasn't working well" in practice. */
	private static final double CAS_CARRIER_RAYCAST_RANGE = 1000.0;
	/** COLLISION mode uses ONLY the actual block collision to determine the target (never substituting BALLISTIC's own same-height-return point) - generous enough ticks for the simulated trajectory to keep falling well below launch height (e.g. firing from a cliff down into a valley) until it actually hits terrain, rather than an arbitrary early cutoff. 3000 ticks (150 simulated seconds) comfortably covers any realistic fall even from near the build height limit down to the world's own bottom. */
	private static final int CAS_CARRIER_COLLISION_MAX_TICKS = 3000;

	/** CAS/Carrier's own "target point" (the route's own relative-coordinate center - see tudursvehiclemod$fireCasStrike()'s own doc) is computed via the same ballistic trajectory calculation a bullet's own impact point uses, rather than tudursvehiclemod$computeCasCarrierRaycastPoint()'s own crosshair-raycast-to-first-block approach - the shooter's own aimed elevation now genuinely determines how FAR the strike lands, matching a real indirect-fire weapon's own behavior, rather than always landing exactly on whatever's directly under the crosshair regardless of aim angle. Obstacles are NOT considered at all here (unlike tudursvehiclemod$computeCasCarrierCollisionPoint(), CasTargetMode.COLLISION's own dedicated calculation) - this is a pure mathematical "same-height-return" point (matching the SAME semantics client.hud.MortarMarkerRenderer's own tudursvehiclemod$computeCasCarrierDistance() already established for the CAS/Carrier HUD number), computed purely from the weapon's own velocity/gravity and the shooter's own current aim, with no raycast at all. Falls back to CAS_CARRIER_DEFAULT_VELOCITY/CAS_CARRIER_DEFAULT_GRAVITY whenever this weapon's own Velocity/Gravity aren't actually configured (both default to 0.0f when absent - see WeaponStatsLoader's own parsing doc), so a CAS/Carrier weapon file that never specified ballistic stats at all (since neither actually needed them before this feature existed) still gets a sensible, non-degenerate target point rather than landing at the shooter's own feet. */
	private Vec3d tudursvehiclemod$computeBallisticTargetPoint(net.minecraft.server.network.ServerPlayerEntity shooter, WeaponDefinition weapon) {
		float velocity = weapon.velocity() > 0f ? weapon.velocity() : CAS_CARRIER_DEFAULT_VELOCITY;
		float gravity = weapon.gravity() > 0f ? weapon.gravity() : CAS_CARRIER_DEFAULT_GRAVITY;
		Vec3d start = shooter.getEyePos();
		Vec3d aimDir = shooter.getRotationVec(1.0f);
		double horizontalSpeed = Math.sqrt(aimDir.x * aimDir.x + aimDir.z * aimDir.z) * velocity;
		double verticalSpeed = aimDir.y * velocity;
		if (verticalSpeed <= 0.0 || gravity <= 0f) {
			// Aiming level or downward (or a pathologically zero/negative gravity, impossible via the fallback above but defensive regardless) - no meaningful "returns to launch height" point exists at all; falls back to the raw aim direction at a fixed, arbitrary reasonable distance instead, matching the general shape of the old raycast fallback (max-range point) for a shot that would never arc back down.
			return start.add(aimDir.multiply(CAS_CARRIER_DEFAULT_VELOCITY * 20.0));
		}
		double stepHorizontalVelocity = horizontalSpeed;
		double stepVerticalVelocity = verticalSpeed;
		double horizontalDistance = 0.0;
		double heightAboveLaunch = 0.0;
		double dragFreeEstimateTicks = 2.0 * verticalSpeed / gravity;
		int tickCap = (int) Math.max(1200.0, dragFreeEstimateTicks * 3.0);
		for (int tick = 0; tick < tickCap; tick++) {
			stepHorizontalVelocity *= CAS_CARRIER_BALLISTIC_AIR_DRAG_PER_TICK;
			stepVerticalVelocity = stepVerticalVelocity * CAS_CARRIER_BALLISTIC_AIR_DRAG_PER_TICK - gravity;
			double nextHeightAboveLaunch = heightAboveLaunch + stepVerticalVelocity;
			if (nextHeightAboveLaunch <= 0.0) {
				double fraction = heightAboveLaunch / (heightAboveLaunch - nextHeightAboveLaunch);
				horizontalDistance += stepHorizontalVelocity * fraction;
				break;
			}
			horizontalDistance += stepHorizontalVelocity;
			heightAboveLaunch = nextHeightAboveLaunch;
		}
		double horizontalAimLength = Math.sqrt(aimDir.x * aimDir.x + aimDir.z * aimDir.z);
		if (horizontalAimLength < 1.0e-4) {
			return start;
		}
		Vec3d horizontalAimDir = new Vec3d(aimDir.x / horizontalAimLength, 0.0, aimDir.z / horizontalAimLength);
		return start.add(horizontalAimDir.multiply(horizontalDistance));
	}

	/** The same trajectory physics tudursvehiclemod$computeBallisticTargetPoint() itself uses, but the target is determined SOLELY by an actual block collision - the simulation keeps running (raycasting between each consecutive simulated position, well past the height BALLISTIC itself would already stop at if needed) until something is actually hit, or CAS_CARRIER_COLLISION_MAX_TICKS/falling below the world's own bottom is reached (both extremely unlikely in practice - would require firing out over open air/void for an absurdly long unobstructed fall), in which case wherever the simulation currently stands is used as a last-resort fallback rather than leaving the target undefined. */
	private Vec3d tudursvehiclemod$computeCasCarrierCollisionPoint(net.minecraft.server.network.ServerPlayerEntity shooter, WeaponDefinition weapon) {
		float velocity = weapon.velocity() > 0f ? weapon.velocity() : CAS_CARRIER_DEFAULT_VELOCITY;
		float gravity = weapon.gravity() > 0f ? weapon.gravity() : CAS_CARRIER_DEFAULT_GRAVITY;
		Vec3d start = shooter.getEyePos();
		Vec3d aimDir = shooter.getRotationVec(1.0f);
		if (gravity <= 0f) {
			// A pathologically zero/negative gravity (impossible via the fallback above but defensive regardless) - no meaningful arc at all; falls back to a straight-line raycast along the aim direction instead, matching the general shape of the other modes' own fallback for this same degenerate case.
			Vec3d fallbackEnd = start.add(aimDir.multiply(CAS_CARRIER_DEFAULT_VELOCITY * 20.0));
			net.minecraft.util.hit.BlockHitResult fallbackHit = tudursvehiclemod$raycastBlocksBetween(start, fallbackEnd, shooter);
			return fallbackHit != null ? fallbackHit.getPos() : fallbackEnd;
		}
		double horizontalAimLength = Math.sqrt(aimDir.x * aimDir.x + aimDir.z * aimDir.z);
		Vec3d horizontalAimDir = horizontalAimLength < 1.0e-4 ? Vec3d.ZERO
				: new Vec3d(aimDir.x / horizontalAimLength, 0.0, aimDir.z / horizontalAimLength);
		double stepHorizontalVelocity = horizontalAimLength * velocity;
		double stepVerticalVelocity = aimDir.y * velocity;
		double horizontalDistance = 0.0;
		double heightAboveLaunch = 0.0;
		Vec3d previousPos = start;
		double worldBottomMargin = this.getEntityWorld().getBottomY() - 8;
		for (int tick = 0; tick < CAS_CARRIER_COLLISION_MAX_TICKS; tick++) {
			stepHorizontalVelocity *= CAS_CARRIER_BALLISTIC_AIR_DRAG_PER_TICK;
			stepVerticalVelocity = stepVerticalVelocity * CAS_CARRIER_BALLISTIC_AIR_DRAG_PER_TICK - gravity;
			horizontalDistance += stepHorizontalVelocity;
			heightAboveLaunch += stepVerticalVelocity;
			Vec3d nextPos = start.add(horizontalAimDir.multiply(horizontalDistance)).add(0.0, heightAboveLaunch, 0.0);
			net.minecraft.util.hit.BlockHitResult stepHit = tudursvehiclemod$raycastBlocksBetween(previousPos, nextPos, shooter);
			if (stepHit != null) {
				return stepHit.getPos();
			}
			if (nextPos.y < worldBottomMargin) {
				// Fell below the world - no terrain will ever be hit at all; stops simulating here rather than looping needlessly, using wherever it currently stands as a last-resort fallback.
				return nextPos;
			}
			previousPos = nextPos;
		}
		// Exhausted the tick cap without ever hitting anything - extremely unlikely in practice, but a defensive fallback regardless.
		return previousPos;
	}

	/** Shared block-collision check for tudursvehiclemod$computeCasCarrierCollisionPoint()'s own per-step raycast - a single simulated-trajectory-step raycast between two consecutive positions, returning the hit (or null if that step passed through open air/fluid entirely). */
	protected net.minecraft.util.hit.BlockHitResult tudursvehiclemod$raycastBlocksBetween(Vec3d from, Vec3d to, net.minecraft.entity.Entity excludeEntity) {
		net.minecraft.world.RaycastContext context = new net.minecraft.world.RaycastContext(from, to,
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, excludeEntity);
		net.minecraft.util.hit.BlockHitResult hit = this.getEntityWorld().raycast(context);
		return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? null : hit;
	}

	/** CAS/Carrier's own target-point calculation method is selectable per weapon (CasTargetMode - see that enum's own doc for what each value means) rather than always using the current BALLISTIC default: the single dispatcher every actual CAS/Carrier fire path now calls, instead of any of the three underlying calculation methods directly. */
	private Vec3d tudursvehiclemod$computeCasCarrierTargetPoint(net.minecraft.server.network.ServerPlayerEntity shooter, WeaponDefinition weapon) {
		return switch (weapon.casTargetMode()) {
			case RAYCAST -> tudursvehiclemod$computeCasCarrierRaycastPoint(shooter);
			case COLLISION -> tudursvehiclemod$computeCasCarrierCollisionPoint(shooter, weapon);
			case BALLISTIC -> tudursvehiclemod$computeBallisticTargetPoint(shooter, weapon);
		};
	}

	/** CAS/Carrier's own CasTargetMode.RAYCAST implementation - see CAS_CARRIER_RAYCAST_RANGE's own doc for why this is a SEPARATE method/range from tudursvehiclemod$raycastGroundPoint() (AS_MISSILE's own, much shorter-range use case) rather than reusing it directly. */
	private Vec3d tudursvehiclemod$computeCasCarrierRaycastPoint(net.minecraft.server.network.ServerPlayerEntity shooter) {
		Vec3d start = shooter.getEyePos();
		Vec3d viewDir = shooter.getRotationVec(1.0f);
		Vec3d end = start.add(viewDir.multiply(CAS_CARRIER_RAYCAST_RANGE));
		net.minecraft.util.hit.BlockHitResult hit = tudursvehiclemod$raycastBlocksBetween(start, end, shooter);
		return hit != null ? hit.getPos() : end;
	}

	/** ASMissile's own target-point acquisition. */
	private Vec3d tudursvehiclemod$raycastGroundPoint(net.minecraft.server.network.ServerPlayerEntity shooter) {
		Vec3d start = shooter.getEyePos();
		Vec3d viewDir = shooter.getRotationVec(1.0f);
		Vec3d end = start.add(viewDir.multiply(MISSILE_TARGET_SEARCH_RANGE));
		net.minecraft.world.RaycastContext context = new net.minecraft.world.RaycastContext(start, end,
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, shooter);
		net.minecraft.util.hit.BlockHitResult hit = this.getEntityWorld().raycast(context);
		return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? end : hit.getPos();
	}

	/** AAMissile/ATMissile's own auto-lock-on-fire. */
	/** Continuously previews AAMissile/ATMissile's own auto-lock-on-fire (see tudursvehiclemod$findLockOnTarget's own doc) by setting whichever entity WOULD be locked right.. */
	private void tudursvehiclemod$updateMissileLockOnIndicators() {
		if (!(this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
			return;
		}
		VehicleDefinition def = this.getDefinition();
		java.util.Set<Integer> currentTargetIds = new java.util.HashSet<>();
		for (int weaponIndex = 0; weaponIndex < def.weapons().size(); weaponIndex++) {
			WeaponDefinition weapon = def.weapons().get(weaponIndex);
			if (weapon.weaponType() != com.example.tudursvehiclemod.asset.WeaponType.AA_MISSILE
					&& weapon.weaponType() != com.example.tudursvehiclemod.asset.WeaponType.AT_MISSILE
					&& weapon.weaponType() != com.example.tudursvehiclemod.asset.WeaponType.MISSILE) {
				continue;
			}
			if (!(this.tudursvehiclemod$getSeatOccupant(weapon.seatIndex()) instanceof net.minecraft.server.network.ServerPlayerEntity shooter)) {
				// No seated gunner at all right now - this weapon's own lock progress resets, same as losing the target entirely.
				this.tudursvehiclemod$missileLockProgressTicks.remove(weaponIndex);
				this.tudursvehiclemod$missileLockTargetId.remove(weaponIndex);
				this.tudursvehiclemod$missileLockRequiredTicks.remove(weaponIndex);
				continue;
			}
			Entity target = tudursvehiclemod$findLockOnTarget(shooter, weapon);
			if (target != null) {
				currentTargetIds.add(target.getId());
				tudursvehiclemod$setEntityHighlighted(target, true);
				// See missileLockRequiredTicks's own doc for why this is recomputed every tick rather than snapshotted once at lock-start.
				double currentDistance = target.getEntityPos().distanceTo(shooter.getEyePos());
				int requiredTicks = (int) Math.round(weapon.lockTimeTicks() + weapon.lockTimePerBlock() * currentDistance);
				this.tudursvehiclemod$missileLockRequiredTicks.put(weaponIndex, requiredTicks);
				// Per Readme_Weapon.txt's own LockTime doc: counts up while
				// the SAME target stays continuously under this weapon's
				// own crosshair, resetting back to 0 the instant a
				// DIFFERENT target takes over (or there's no target at
				// all - see the else branch below).
				Integer previousTargetId = this.tudursvehiclemod$missileLockTargetId.get(weaponIndex);
				if (previousTargetId != null && previousTargetId == target.getId()) {
					this.tudursvehiclemod$missileLockProgressTicks.merge(weaponIndex, 1, Integer::sum);
				} else {
					this.tudursvehiclemod$missileLockTargetId.put(weaponIndex, target.getId());
					this.tudursvehiclemod$missileLockProgressTicks.put(weaponIndex, 0);
				}
			} else {
				this.tudursvehiclemod$missileLockProgressTicks.remove(weaponIndex);
				this.tudursvehiclemod$missileLockTargetId.remove(weaponIndex);
				this.tudursvehiclemod$missileLockRequiredTicks.remove(weaponIndex);
			}
		}
		for (Integer previousId : this.tudursvehiclemod$previousLockOnTargetIds) {
			if (!currentTargetIds.contains(previousId)) {
				Entity previous = serverWorld.getEntityById(previousId);
				if (previous != null) {
					tudursvehiclemod$setEntityHighlighted(previous, false);
				}
			}
		}
		this.tudursvehiclemod$previousLockOnTargetIds = currentTargetIds;
		this.tudursvehiclemod$syncMissileLockProgress();
	}

	/** Entity.setGlowing() is insufficient for this mod's own vehicle entities (see HIGHLIGHT_ACTIVE's own doc), and non-vehicle entities also avoid setGlowing() (see HIGHLIGHTED_ENTITY_IDS's own doc) - shared dispatch every lock-on-style highlight call site (missile lock-on preview, Carrier target-lock candidate preview, TargetingPod spotting) now goes through: an AbstractVehicleEntity target gets the mesh-accurate tudursvehiclemod$setHighlighted() (set ON THE TARGET ITSELF); anything else is added to/removed from THIS vehicle's own HIGHLIGHTED_ENTITY_IDS list instead (this vehicle being "the tracker" - see that field's own doc for why tracking it there, rather than on the target, is the deliberate, meaningful choice for non-vehicle entities). Now an instance method (previously static) since it needs "this" as the tracker for the non-vehicle case. */
	public void tudursvehiclemod$setEntityHighlighted(Entity entity, boolean highlighted) {
		if (entity instanceof AbstractVehicleEntity vehicleEntity) {
			vehicleEntity.tudursvehiclemod$setHighlighted(highlighted);
			return;
		}
		boolean changed = highlighted
				? this.tudursvehiclemod$highlightedNonVehicleEntityIds.add(entity.getId())
				: this.tudursvehiclemod$highlightedNonVehicleEntityIds.remove(entity.getId());
		if (changed) {
			this.dataTracker.set(HIGHLIGHTED_ENTITY_IDS, this.tudursvehiclemod$highlightedNonVehicleEntityIds.stream()
					.map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
		}
	}

	/** Encodes tudursvehiclemod$missileLockProgressTicks into MISSILE_LOCK_SYNC's own "progress0,progress1,.." format, and tudursvehiclemod$missileLockRequiredTicks into MISSILE_LOCK_REQUIRED_SYNC's own identical format (see that field's own doc). */
	private void tudursvehiclemod$syncMissileLockProgress() {
		VehicleDefinition def = this.getDefinition();
		StringBuilder progressSb = new StringBuilder();
		StringBuilder requiredSb = new StringBuilder();
		for (int weaponIndex = 0; weaponIndex < def.weapons().size(); weaponIndex++) {
			if (weaponIndex > 0) {
				progressSb.append(',');
				requiredSb.append(',');
			}
			progressSb.append(this.tudursvehiclemod$missileLockProgressTicks.getOrDefault(weaponIndex, 0));
			requiredSb.append(this.tudursvehiclemod$missileLockRequiredTicks.getOrDefault(weaponIndex, def.weapons().get(weaponIndex).lockTimeTicks()));
		}
		this.dataTracker.set(MISSILE_LOCK_SYNC, progressSb.toString());
		this.dataTracker.set(MISSILE_LOCK_REQUIRED_SYNC, requiredSb.toString());
	}

	/** Current lock progress (ticks - see tudursvehiclemod$missileLockProgressTicks's own doc) for weaponIndex, read from MISSILE_LOCK_SYNC. Public so client.hud.HudVariables can show it. 0 for every weapon type that isn't actually AAMissile/ATMissile at all, or if nothing's currently being tracked. */
	public int tudursvehiclemod$getMissileLockProgress(int weaponIndex) {
		String raw = this.dataTracker.get(MISSILE_LOCK_SYNC);
		if (raw.isEmpty()) {
			return 0;
		}
		String[] parts = raw.split(",");
		if (weaponIndex < 0 || weaponIndex >= parts.length) {
			return 0;
		}
		try {
			return Integer.parseInt(parts[weaponIndex]);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** The distance-aware REQUIRED tick count (see tudursvehiclemod$missileLockRequiredTicks's own doc) for weaponIndex, read from MISSILE_LOCK_REQUIRED_SYNC. Public so client.hud.HudVariables can compute a correct lock_progress fraction against it. Falls back to weapon.lockTimeTicks() itself (the caller's own responsibility to pass it, since this class alone can't resolve a WeaponDefinition from an index without one) if nothing's synced yet at all. */
	public int tudursvehiclemod$getMissileLockRequiredTicks(int weaponIndex, int fallback) {
		String raw = this.dataTracker.get(MISSILE_LOCK_REQUIRED_SYNC);
		if (raw.isEmpty()) {
			return fallback;
		}
		String[] parts = raw.split(",");
		if (weaponIndex < 0 || weaponIndex >= parts.length) {
			return fallback;
		}
		try {
			return Integer.parseInt(parts[weaponIndex]);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private net.minecraft.entity.Entity tudursvehiclemod$findLockOnTarget(net.minecraft.server.network.ServerPlayerEntity shooter, WeaponDefinition weapon) {
		com.example.tudursvehiclemod.asset.WeaponType weaponType = weapon.weaponType();
		// 0 (weapon.lockRange()'s own parsed default whenever that key is absent) means unlimited, using MISSILE_LOCK_UNLIMITED_RANGE_CEILING instead - a genuinely finite value is still needed to build a search box/AABB at all, and no real gameplay scenario needs locking something literally thousands of blocks away regardless of what "unlimited" is meant to convey.
		double lockRange = weapon.lockRange() > 0.0 ? weapon.lockRange() : MISSILE_LOCK_UNLIMITED_RANGE_CEILING;
		Vec3d eyePos = shooter.getEyePos();
		Vec3d viewDir = shooter.getRotationVec(1.0f);
		double minDot = Math.cos(Math.toRadians(MISSILE_LOCK_ON_CONE_DEGREES));
		net.minecraft.util.math.Box searchBox = shooter.getBoundingBox().expand(lockRange);
		net.minecraft.entity.Entity best = null;
		double bestDot = minDot;
		// This project's own vehicles (AbstractVehicleEntity) extend Entity directly, NOT LivingEntity - the original LivingEntity-only filter meant every aircraft/helicopter/tank in the game was silently invisible to this search, leaving only players and mobs lockable. isDestroyed() (rather than LivingEntity's own isAlive()) is the correct "still a valid target" check for a vehicle - a destroyed hull sitting there sinking/burning shouldn't be lockable.
		// CarrierRunwayPlatformEntity (a runway's own invisible support tile) extends PathAwareEntity - a genuine LivingEntity/MobEntity - so it was unintentionally matching the plain LivingEntity branch below and showing up as a lockable target, despite being an implementation detail with no meaningful existence as an actual target. Excluded explicitly.
		for (net.minecraft.entity.Entity candidate : this.getEntityWorld().getOtherEntities(shooter, searchBox,
				e -> (e instanceof net.minecraft.entity.LivingEntity living && living.isAlive()
						|| e instanceof AbstractVehicleEntity vehicleCandidate && !vehicleCandidate.tudursvehiclemod$isDestroyed())
						&& !(e instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity)
						&& e != this && e != shooter.getVehicle())) {
			// Per Readme_Weapon.txt's own documented distinction ("AAMissile 空中にいるモブを追跡するミサイル" / "ATMissile 地上にいるモブを追跡するミサイル" - based on the TARGET's own current physical state, not its entity/vehicle type): a single shared classification (tudursvehiclemod$classifyTargetPosition()) determines whether a candidate is currently AIRBORNE, on the SURFACE (ground, or floating/standing on top of water), or SUBMERGED (underwater) - AA_MISSILE requires AIRBORNE, AT_MISSILE requires SURFACE; SUBMERGED is excluded from both (a submerged target is its own distinct category now, reserved for WeaponType.ASWeapon - see that enum's own doc). MISSILE (this project's own type, see that enum's own doc) and everything else stays unrestricted.
			TargetPosition candidatePosition = null;
			if (weaponType == com.example.tudursvehiclemod.asset.WeaponType.AA_MISSILE
					|| weaponType == com.example.tudursvehiclemod.asset.WeaponType.AT_MISSILE) {
				candidatePosition = tudursvehiclemod$classifyTargetPosition(candidate);
				boolean wantsAirborne = weaponType == com.example.tudursvehiclemod.asset.WeaponType.AA_MISSILE;
				TargetPosition required = wantsAirborne ? TargetPosition.AIRBORNE : TargetPosition.SURFACE;
				if (candidatePosition != required) {
					continue;
				}
			}
			Vec3d toCandidate = candidate.getEntityPos().subtract(eyePos);
			double distance = toCandidate.length();
			if (distance < 1.0 || distance > lockRange) {
				continue;
			}
			double dot = toCandidate.normalize().dotProduct(viewDir);
			if (dot > bestDot) {
				bestDot = dot;
				best = candidate;
			}
		}
		return best;
	}

	/** Per Readme_Weapon.txt's own AAMissile/ATMissile doc, and a direct request to also distinguish a SUBMERGED target as its own third category (excluded from both AA_MISSILE and AT_MISSILE, reserved for WeaponType.ASWeapon): AIRBORNE (in open air, not resting on solid ground/floating and not in/on water), SURFACE (on the ground, or touching/floating on TOP of water without being submerged), or SUBMERGED (actually underwater - see Entity's own isSubmergedInWater(), true once the eye position itself is below the water surface). Airborne classification deliberately NOT based purely on Entity's own isOnGround() flag - this project's own documentation already establishes that flag as unreliable specifically on this mod's own entity-based runway tiles (a vehicle resting there could misreport it), so it also requires a meaningful measured altitude (raycast straight down to the nearest solid block, same approach client.hud.HudVariables' own altitudeAboveGround() already uses for the HUD's own "altitude" variable) above AIRBORNE_TARGET_MIN_ALTITUDE before treating something as genuinely airborne rather than merely momentarily not touching ground (a brief hop, or a physics engine quirk). */
	protected enum TargetPosition { AIRBORNE, SURFACE, SUBMERGED }

	protected TargetPosition tudursvehiclemod$classifyTargetPosition(Entity candidate) {
		if (candidate.isSubmergedInWater()) {
			return TargetPosition.SUBMERGED;
		}
		if (candidate.isTouchingWater() || candidate.isOnGround()) {
			return TargetPosition.SURFACE;
		}
		Vec3d start = candidate.getEntityPos();
		Vec3d end = start.add(0, -256, 0);
		net.minecraft.util.hit.HitResult hit = candidate.getEntityWorld().raycast(new net.minecraft.world.RaycastContext(
				start, end, net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, candidate));
		double altitude = hit instanceof net.minecraft.util.hit.BlockHitResult blockHit && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
				? Math.max(0.0, start.y - blockHit.getPos().y) : 256.0;
		return altitude > AIRBORNE_TARGET_MIN_ALTITUDE ? TargetPosition.AIRBORNE : TargetPosition.SURFACE;
	}

	private void tudursvehiclemod$ensureWeaponAmmoArraysSized(VehicleDefinition def) {
		int count = def.weapons().size();
		if (this.weaponAmmo.length == count) {
			return;
		}
		int[] newAmmo = new int[count];
		int[] newReload = new int[count];
		int[] newReserve = new int[count];
		int[] newFiringPosition = new int[count];
		float[] newHeat = new float[count];
		int[] newMode = new int[count];
		for (int i = 0; i < count; i++) {
			// Spawns empty (0), not full magazine+reserve (a full-magazine+full-reserve start caused an "ammo exceeds MaxAmmo" bug).
			newAmmo[i] = i < this.weaponAmmo.length ? this.weaponAmmo[i] : 0;
			newReload[i] = i < this.weaponReloadTicksRemaining.length ? this.weaponReloadTicksRemaining[i] : 0;
			int maxAmmo = def.weapons().get(i).maxAmmo();
			newReserve[i] = i < this.weaponReserveAmmo.length ? this.weaponReserveAmmo[i] : (maxAmmo > 0 ? 0 : -1);
			newFiringPosition[i] = i < this.weaponFiringPositionIndex.length ? this.weaponFiringPositionIndex[i] : 0;
			newHeat[i] = i < this.weaponHeat.length ? this.weaponHeat[i] : 0f;
			newMode[i] = i < this.weaponMode.length ? this.weaponMode[i] : 0;
		}
		this.weaponAmmo = newAmmo;
		this.weaponReloadTicksRemaining = newReload;
		this.weaponReserveAmmo = newReserve;
		this.weaponFiringPositionIndex = newFiringPosition;
		this.weaponHeat = newHeat;
		this.weaponMode = newMode;
	}

	/** Detects a SEAT's occupant changing (including empty -> occupied, by actual seat index - not raw passenger-list position, which can diverge from it once seat reassignment is involved), resetting that seat's own fire lockout (seatMountGraceTicks) back to MOUNT_FIRE_LOCK_TICKS whenever it does. */
	private void tudursvehiclemod$updateSeatMountGrace() {
		int seatCount = this.getDefinition().seats().size();
		if (this.seatMountGraceTicks.length < seatCount) {
			this.seatMountGraceTicks = java.util.Arrays.copyOf(this.seatMountGraceTicks, seatCount);
		}
		if (this.tudursvehiclemod$previousSeatOccupants.length < seatCount) {
			this.tudursvehiclemod$previousSeatOccupants = java.util.Arrays.copyOf(this.tudursvehiclemod$previousSeatOccupants, seatCount);
		}
		for (int seatIndex = 0; seatIndex < seatCount; seatIndex++) {
			Entity current = this.tudursvehiclemod$getSeatOccupant(seatIndex);
			Entity previous = this.tudursvehiclemod$previousSeatOccupants[seatIndex];
			if (current != previous) {
				this.seatMountGraceTicks[seatIndex] = MOUNT_FIRE_LOCK_TICKS;
				this.tudursvehiclemod$previousSeatOccupants[seatIndex] = current;
			} else if (this.seatMountGraceTicks[seatIndex] > 0) {
				this.seatMountGraceTicks[seatIndex]--;
			}
		}
	}

	/** Cools every heat-based weapon (see WeaponStats.isHeatBased()'s own doc) down over time, continuously. */
	private void tudursvehiclemod$updateWeaponHeat() {
		VehicleDefinition def = getDefinition();
		if (def.weapons().isEmpty()) {
			return;
		}
		this.tudursvehiclemod$ensureWeaponAmmoArraysSized(def);
		boolean changed = false;
		for (int i = 0; i < this.weaponHeat.length; i++) {
			if (this.weaponHeat[i] <= 0f) {
				continue;
			}
			WeaponDefinition weapon = def.weapons().get(i);
			if (!weapon.isHeatBased()) {
				continue;
			}
			float cooldownPerTick = weapon.maxHeat() * WEAPON_HEAT_COOLDOWN_FRACTION_PER_TICK;
			this.weaponHeat[i] = Math.max(0f, this.weaponHeat[i] - cooldownPerTick);
			changed = true;
		}
		if (changed) {
			this.tudursvehiclemod$syncWeaponAmmo();
		}
	}

	private void updateWeaponReloads() {
		VehicleDefinition def = getDefinition();
		if (def.weapons().isEmpty()) {
			return;
		}
		// For a UAV, reload only progresses while actually piloted (remote control = real mounting, so getControllingPassenger() reflects this) - not while unmanned/idle.
		if (this.tudursvehiclemod$isUav() && !(getControllingPassenger() instanceof PlayerEntity)) {
			return;
		}
		boolean changed = this.weaponAmmo.length != def.weapons().size();
		this.tudursvehiclemod$ensureWeaponAmmoArraysSized(def);
		for (int i = 0; i < this.weaponReloadTicksRemaining.length; i++) {
			if (this.weaponReloadTicksRemaining[i] > 0) {
				this.weaponReloadTicksRemaining[i]--;
				changed = true;
				if (this.weaponReloadTicksRemaining[i] == 0 && i < def.weapons().size()) {
					int magazineSize = def.weapons().get(i).magazineSize();
					// ReloadTime governs
					// refilling the magazine (Round) FROM the total
					// reserve (MaxAmmo) - a genuinely limited reserve
					// (weaponReserveAmmo[i] >= 0; -1 means unlimited, see
					// that field's own doc) caps how much this reload can
					// actually add, and is drawn down by that same
					// amount. Once the reserve reaches 0, this simply
					// adds nothing at all - a true "out of ammo" state
					// (the magazine stays wherever it already was,
					// usually 0) rather than refilling forever.
					if (this.weaponReserveAmmo[i] < 0) {
						this.weaponAmmo[i] = magazineSize;
					} else {
						int wanted = magazineSize - this.weaponAmmo[i];
						int actuallyAdded = Math.min(wanted, this.weaponReserveAmmo[i]);
						this.weaponAmmo[i] += actuallyAdded;
						this.weaponReserveAmmo[i] -= actuallyAdded;
					}
				}
			}
		}
		if (changed) {
			this.tudursvehiclemod$syncWeaponAmmo();
		}
	}

	/** Encodes weaponAmmo/weaponReloadTicksRemaining/weaponHeat/weaponCooldowns into WEAPON_AMMO_SYNC's own "ammo0:reload0:heat0:cooldown0,ammo1:reload1:heat1:cooldown1,.." format. */
	public void tudursvehiclemod$syncWeaponAmmo() {
		if (this.getEntityWorld().isClient()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < this.weaponAmmo.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			int heat = i < this.weaponHeat.length ? Math.round(this.weaponHeat[i]) : 0;
			int cooldown = i < this.weaponCooldowns.length ? this.weaponCooldowns[i] : 0;
			int mode = i < this.weaponMode.length ? this.weaponMode[i] : 0;
			sb.append(this.weaponAmmo[i]).append(':').append(this.weaponReloadTicksRemaining[i])
					.append(':').append(heat).append(':').append(cooldown).append(':').append(mode);
		}
		this.dataTracker.set(WEAPON_AMMO_SYNC, sb.toString());
	}

	/** Attempts one resupply action for weaponIndex. */
	/** Iron ingot cost per tier, matching REPAIR_TIER_HEAL_PERCENT's own indices - see tudursvehiclemod$tryRepair()'s own doc. */
	private static final int[] REPAIR_TIER_IRON_COST = {1, 5, 10};
	/** Percentage of this vehicle's own max health restored per tier, matching REPAIR_TIER_IRON_COST's own indices. */
	private static final float[] REPAIR_TIER_HEAL_PERCENT = {5f, 25f, 50f};

	/** Restores a fixed % of max health for a fixed iron-ingot cost (tier 0=1 ingot/5%, tier 1=5/25%, tier 2=10/50% - see REPAIR_TIER_IRON_COST/REPAIR_TIER_HEAL_PERCENT). No-op if invalid tier, already full, or not enough ingots. */
	public boolean tudursvehiclemod$tryRepair(net.minecraft.entity.player.PlayerEntity player, int tier) {
		if (tier < 0 || tier >= REPAIR_TIER_IRON_COST.length) {
			return false;
		}
		if (this.getHealth() >= this.getMaxHealth()) {
			return false; // already full
		}
		int ironCost = REPAIR_TIER_IRON_COST[tier];
		net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();
		if (inventory.count(net.minecraft.item.Items.IRON_INGOT) < ironCost) {
			return false;
		}
		tudursvehiclemod$consumeFromInventory(inventory, net.minecraft.item.Items.IRON_INGOT, ironCost);
		float healAmount = this.getMaxHealth() * (REPAIR_TIER_HEAL_PERCENT[tier] / 100f);
		float newHealth = Math.min(this.getMaxHealth(), this.getHealth() + healAmount);
		this.dataTracker.set(HEALTH, newHealth);
		return true;
	}

	/** Resupply only works below this real velocity squared - small enough to count as "stopped" despite floating-point noise. */
	private static final double STATIONARY_RESUPPLY_VELOCITY_THRESHOLD_SQUARED = 0.02 * 0.02;

	/** Tops up every OTHER vehicle within each configured radius. Per MC Heli's own documented semantics, supplying costs this vehicle nothing at all, and a vehicle never supplies ITSELF ("他の機体に補給しても自機の燃料は減らない。ただし、自機には補給できない") - both are reproduced exactly. Per a further direct request ("補給の対象となる機体は範囲内かつ静止している(手動で弾薬の装填が可能な)機体のみとできますか"): a candidate recipient must also be stationary - the exact same condition (STATIONARY_RESUPPLY_VELOCITY_THRESHOLD_SQUARED) the player-initiated, item-consuming resupply action already requires - a moving vehicle receives none of the three supply types at all, regardless of how close it is.
	 *
	 * Throttled to once per second rather than running every tick: the effect is inherently gradual (a per-second top-up), so re-scanning nearby entities at 20Hz would be twenty times the work for no observable difference. Early-outs entirely when this vehicle has no supply range configured at all, so the overwhelming majority of vehicles pay only that one check.
	 *
	 * Each radius is independent - a tanker might refuel at 25 blocks while rearming only at 35, or do one and not the other - matching how MC Heli itself treats them as two unrelated settings rather than one combined "supply" concept. */
	private void tudursvehiclemod$updateSupplyToNearbyVehicles() {
		VehicleDefinition def = this.getDefinition();
		float fuelRange = def.fuelSupplyRange();
		float ammoRange = def.ammoSupplyRange();
		float healthRange = def.healthSupplyRange();
		if (fuelRange <= 0f && ammoRange <= 0f && healthRange <= 0f) {
			return;
		}
		if (this.age % SUPPLY_INTERVAL_TICKS != 0) {
			return;
		}
		// One search at the widest configured radius, then per-vehicle distance checks against each individual range below - rather than up to three separate world searches, which would repeat the same broad-phase work.
		double widest = Math.max(fuelRange, Math.max(ammoRange, healthRange));
		var candidates = this.getEntityWorld().getEntitiesByClass(AbstractVehicleEntity.class,
				this.getBoundingBox().expand(widest), other -> other != this && other.isAlive());
		for (AbstractVehicleEntity other : candidates) {
			// A vehicle still moving skips every kind of supply below entirely, regardless of range.
			if (other.getVelocity().horizontalLengthSquared() > STATIONARY_RESUPPLY_VELOCITY_THRESHOLD_SQUARED) {
				continue;
			}
			double distanceSquared = this.squaredDistanceTo(other);
			if (fuelRange > 0f && distanceSquared <= fuelRange * fuelRange) {
				// Costs this vehicle nothing - see this method's own doc. addFuel() clamps to the recipient's own maxFuel, so an already-full vehicle simply stays full.
				other.tudursvehiclemod$addFuel(other.getMaxFuel() * SUPPLY_FUEL_FRACTION_PER_INTERVAL);
			}
			if (ammoRange > 0f && distanceSquared <= ammoRange * ammoRange) {
				other.tudursvehiclemod$receiveAmmoSupply();
			}
			if (healthRange > 0f && distanceSquared <= healthRange * healthRange) {
				other.tudursvehiclemod$receiveHealthSupply();
			}
		}
	}

	/** How often tudursvehiclemod$updatePassengerRegeneration() actually heals - matches SUPPLY_INTERVAL_TICKS's own once-per-second pacing (a gradual heal-over-time, not an instant top-up). */
	private static final int REGENERATION_INTERVAL_TICKS = 20;

	/** How much of a passenger's own maximum health one regeneration interval restores - matches SUPPLY_HEALTH_FRACTION_PER_INTERVAL's own pacing (roughly fifty seconds to fully heal from empty). */
	private static final float REGENERATION_HEALTH_FRACTION_PER_INTERVAL = 0.02f;

	/** Heals every non-pilot passenger (seat index >= 1 - the pilot, seat 0, is deliberately excluded, matching MC Heli's own documented scope) a fraction of their own max health once per REGENERATION_INTERVAL_TICKS, via a direct tudursvehiclemod$getRealPassengerList()-filtered LivingEntity#heal() call (this project's own approach A) rather than applying vanilla's own REGENERATION status effect - avoiding that effect's own particle/HUD-icon presentation, which MC Heli's own feature never had either. No-op entirely if this vehicle's own definition doesn't have regeneration set, so the overwhelming majority of vehicles pay only that one check. Works for both player and non-player LivingEntity passengers alike, matching MC Heli's own "モブ" wording (which in that context just means "whoever's occupying the seat", not specifically a non-player). */
	private void tudursvehiclemod$updatePassengerRegeneration() {
		if (!this.getDefinition().regeneration()) {
			return;
		}
		if (this.age % REGENERATION_INTERVAL_TICKS != 0) {
			return;
		}
		for (Entity passenger : this.tudursvehiclemod$getRealPassengerList()) {
			if (this.tudursvehiclemod$getAssignedSeatIndex(passenger) == 0) {
				continue; // Pilot excluded, per MC Heli's own documented scope.
			}
			if (passenger instanceof LivingEntity livingPassenger && livingPassenger.getHealth() < livingPassenger.getMaxHealth()) {
				livingPassenger.heal(livingPassenger.getMaxHealth() * REGENERATION_HEALTH_FRACTION_PER_INTERVAL);
			}
		}
	}

	/** How often tudursvehiclemod$updateSupplyToNearbyVehicles() actually runs - see that method's own doc for why it isn't every tick. */
	private static final int SUPPLY_INTERVAL_TICKS = 20;

	/** How much of a recipient's own maximum fuel one supply interval restores. At this rate a fully empty vehicle refills over roughly twenty seconds - fast enough to be useful while parked beside a tanker, slow enough that it reads as refuelling rather than an instant reset. */
	private static final float SUPPLY_FUEL_FRACTION_PER_INTERVAL = 0.05f;

	/** How much of a recipient's own maximum health one supply interval restores - see SUPPLY_FUEL_FRACTION_PER_INTERVAL's own doc for the same pacing reasoning. Deliberately slower than fuel: repair should feel more costly in time than simply topping up a tank. */
	private static final float SUPPLY_HEALTH_FRACTION_PER_INTERVAL = 0.02f;

	/** Per the same request as tudursvehiclemod$updateSupplyToNearbyVehicles()'s own doc: refills THIS vehicle's own weapon magazines from its own reserve-free supply, called on the RECIPIENT by a nearby supplier. Reuses tudursvehiclemod$replenishWeaponAmmo() so magazine capping and the ammo-display sync behave identically to a Carrier landing's own resupply; the UUID argument is irrelevant here (nothing is in flight), so a throwaway one is passed. */
	private void tudursvehiclemod$receiveAmmoSupply() {
		VehicleDefinition def = this.getDefinition();
		for (int weaponIndex = 0; weaponIndex < def.weapons().size(); weaponIndex++) {
			WeaponDefinition weapon = def.weapons().get(weaponIndex);
			int magazineSize = weapon.magazineSize();
			if (magazineSize <= 0) {
				continue;
			}
			this.tudursvehiclemod$ensureWeaponAmmoArraysSized(def);
			if (this.weaponAmmo[weaponIndex] >= magazineSize) {
				continue;
			}
			// A fraction of the magazine per interval rather than a flat count, so a large-magazine weapon doesn't take proportionally forever compared to a small one - floored at 1 so even a tiny magazine still makes progress.
			int amount = Math.max(1, Math.round(magazineSize * SUPPLY_AMMO_FRACTION_PER_INTERVAL));
			this.tudursvehiclemod$replenishWeaponAmmo(weaponIndex, amount, java.util.UUID.randomUUID());
		}
	}

	/** How much of a recipient weapon's own magazine one supply interval restores - see tudursvehiclemod$receiveAmmoSupply()'s own doc for why this is a fraction rather than a flat round count. */
	private static final float SUPPLY_AMMO_FRACTION_PER_INTERVAL = 0.1f;

	/** Per the same request as tudursvehiclemod$updateSupplyToNearbyVehicles()'s own doc: restores part of THIS vehicle's own health, called on the RECIPIENT by a nearby supplier. Sets HEALTH directly, exactly as the existing iron-ingot repair path does (see tudursvehiclemod$tryRepair()'s own use of the same field), so both routes to healing behave identically. */
	private void tudursvehiclemod$receiveHealthSupply() {
		float maxHealth = this.getMaxHealth();
		float current = this.getHealth();
		if (current >= maxHealth) {
			return;
		}
		this.dataTracker.set(HEALTH, Math.min(maxHealth, current + maxHealth * SUPPLY_HEALTH_FRACTION_PER_INTERVAL));
	}



	public boolean tudursvehiclemod$tryResupplyWeapon(net.minecraft.entity.player.PlayerEntity player, int weaponIndex) {
		VehicleDefinition def = this.getDefinition();
		if (weaponIndex < 0 || weaponIndex >= def.weapons().size()) {
			return false;
		}
		WeaponDefinition weapon = def.weapons().get(weaponIndex);
		if (!weapon.isResuppliable()) {
			return false;
		}
		// A non-pilot seat can only resupply the weapon(s) its own seat can actually fire - the pilot (seat 0) keeps being able to resupply any weapon regardless of seat, matching the existing pilotUsable design elsewhere.
		int resupplierSeatIndex = this.tudursvehiclemod$getAssignedSeatIndex(player);
		if (resupplierSeatIndex != 0 && weapon.seatIndex() != resupplierSeatIndex) {
			return false;
		}
		this.tudursvehiclemod$ensureWeaponAmmoArraysSized(def);
		// Rather than trusting a simple counter that could drift out of sync (a timeout/destruction despawn never explicitly decrements anything), actively checks each tracked UUID against whether it's ACTUALLY still alive right now, pruning any that aren't - the resulting (verified) set size is the true in-flight count.
		java.util.Set<java.util.UUID> inFlightSet = this.carrierInFlightAircraft.get(weaponIndex);
		int inFlight = 0;
		if (inFlightSet != null && !inFlightSet.isEmpty() && this.getEntityWorld() instanceof ServerWorld serverWorldForVerify) {
			java.util.Iterator<java.util.UUID> inFlightIterator = inFlightSet.iterator();
			while (inFlightIterator.hasNext()) {
				java.util.UUID candidateUuid = inFlightIterator.next();
				Entity candidateEntity = serverWorldForVerify.getEntity(candidateUuid);
				if (candidateEntity == null || candidateEntity.isRemoved()
						|| (candidateEntity instanceof AbstractVehicleEntity candidateVehicle && candidateVehicle.tudursvehiclemod$isDestroyed())) {
					inFlightIterator.remove();
				} else {
					inFlight++;
				}
			}
		}
		// The previous "magazineFull && reserveFull" condition never actually blocked anything once the magazine was empty (e.g. right after firing/launching), regardless of reserveFull's own value, since BOTH had to be true. Checks total capacity used (magazine + reserve + in-flight) against MaxAmmo directly instead - correctly blocks in every case, not just when the magazine happens to already be full.
		boolean atCapacity = this.weaponReserveAmmo[weaponIndex] < 0
				? this.weaponAmmo[weaponIndex] >= weapon.magazineSize()
				: this.weaponAmmo[weaponIndex] + this.weaponReserveAmmo[weaponIndex] + inFlight >= weapon.maxAmmo();
		if (atCapacity) {
			return false; // already full
		}
		// Only HORIZONTAL velocity matters for "stationary" - GROUNDED_STICK_VELOCITY's own small vertical nudge while landed shouldn't disqualify it.
		if (this.getVelocity().horizontalLengthSquared() > STATIONARY_RESUPPLY_VELOCITY_THRESHOLD_SQUARED) {
			return false;
		}
		net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();
		int ironCost = weapon.resupplyIronIngotCost();
		int gunpowderCost = weapon.resupplyGunpowderCost();
		int redstoneCost = weapon.resupplyRedstoneCost();
		// In creative the item cost is waived entirely - both the can-you-afford-it check and the deduction itself. Deliberately scoped to exactly that: the capacity check, the stationary requirement and every other condition above are untouched, so a creative player still cannot resupply a full weapon or do it while moving.
		boolean creative = player.isCreative();
		if (!creative && ironCost > 0 && inventory.count(net.minecraft.item.Items.IRON_INGOT) < ironCost) {
			return false;
		}
		if (!creative && gunpowderCost > 0 && inventory.count(net.minecraft.item.Items.GUNPOWDER) < gunpowderCost) {
			return false;
		}
		if (!creative && redstoneCost > 0 && inventory.count(net.minecraft.item.Items.REDSTONE) < redstoneCost) {
			return false;
		}
		if (!creative && ironCost > 0) {
			tudursvehiclemod$consumeFromInventory(inventory, net.minecraft.item.Items.IRON_INGOT, ironCost);
		}
		if (!creative && gunpowderCost > 0) {
			tudursvehiclemod$consumeFromInventory(inventory, net.minecraft.item.Items.GUNPOWDER, gunpowderCost);
		}
		if (!creative && redstoneCost > 0) {
			tudursvehiclemod$consumeFromInventory(inventory, net.minecraft.item.Items.REDSTONE, redstoneCost);
		}
		// Restocks the reserve by suppliedNum, then tops the magazine back up from that reserve - one action doing both.
		if (this.weaponReserveAmmo[weaponIndex] >= 0) {
			// Reserve cap is maxAmmo minus current magazine ammo, not maxAmmo alone (- otherwise repeated resupply could exceed maxAmmo's combined total).
			int reserveCap = Math.max(0, weapon.maxAmmo() - this.weaponAmmo[weaponIndex]);
			this.weaponReserveAmmo[weaponIndex] = Math.min(reserveCap, this.weaponReserveAmmo[weaponIndex] + weapon.suppliedNum());
			int wanted = weapon.magazineSize() - this.weaponAmmo[weaponIndex];
			int actuallyAdded = Math.min(wanted, this.weaponReserveAmmo[weaponIndex]);
			this.weaponAmmo[weaponIndex] += actuallyAdded;
			this.weaponReserveAmmo[weaponIndex] -= actuallyAdded;
		} else {
			this.weaponAmmo[weaponIndex] = Math.min(weapon.magazineSize(), this.weaponAmmo[weaponIndex] + weapon.suppliedNum());
		}
		// Resupplying also clears any in-progress reload timer, same as an instant reload would.
		this.weaponReloadTicksRemaining[weaponIndex] = 0;
		this.tudursvehiclemod$syncWeaponAmmo();
		return true;
	}

	/** Removes count total of item from inventory, one slot at a time. */
	private static void tudursvehiclemod$consumeFromInventory(net.minecraft.inventory.Inventory inventory,
			net.minecraft.item.Item item, int count) {
		int remaining = count;
		for (int i = 0; i < inventory.size() && remaining > 0; i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isEmpty() || !stack.isOf(item)) {
				continue;
			}
			int take = Math.min(remaining, stack.getCount());
			inventory.removeStack(i, take);
			remaining -= take;
		}
	}

	/** Parses WEAPON_AMMO_SYNC's own "ammo0:reload0:heat0,ammo1:reload1:heat1,.." format. */
	/** True if this AmmoPart should currently be rendered - its weapon has more than slotIndex rounds remaining (unlimited-magazine weapons always show every part). */
	public boolean tudursvehiclemod$isAmmoPartVisible(AmmoPart part) {
		// Often wing-mounted ordnance, which would clash with a folded wing - hidden the instant folding starts, shown again only once fully extended.
		if (!this.tudursvehiclemod$isWingFullyExtended()) {
			return false;
		}
		List<WeaponDefinition> weapons = this.getDefinition().weapons();
		for (int i = 0; i < weapons.size(); i++) {
			if (weapons.get(i).weaponName().equals(part.weaponName())) {
				int magazineSize = weapons.get(i).magazineSize();
				if (magazineSize <= 0) {
					return true;
				}
				int[] ammoState = this.getWeaponAmmoState(i);
				int ammoRemaining = ammoState[0] >= 0 ? ammoState[0] : magazineSize;
				return ammoRemaining > part.slotIndex();
			}
		}
		return true; // weapon not found - fail open rather than hiding the part
	}

	public int[] getWeaponAmmoState(int weaponIndex) {
		String raw = this.dataTracker.get(WEAPON_AMMO_SYNC);
		if (raw.isEmpty()) {
			return new int[] {-1, 0};
		}
		String[] entries = raw.split(",");
		if (weaponIndex < 0 || weaponIndex >= entries.length) {
			return new int[] {-1, 0};
		}
		String[] parts = entries[weaponIndex].split(":");
		if (parts.length < 2) {
			return new int[] {-1, 0};
		}
		try {
			return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
		} catch (NumberFormatException e) {
			return new int[] {-1, 0};
		}
	}

	/** WeaponReserveAmmo itself (unlike weaponAmmo, the live magazine count already exposed via getWeaponAmmoState()) had no public accessor at all before this - 0 for an out-of-range weaponIndex, matching that same method's own "nothing to report" convention. */
	public int tudursvehiclemod$getWeaponReserveAmmo(int weaponIndex) {
		if (weaponIndex < 0 || weaponIndex >= this.weaponReserveAmmo.length) {
			return 0;
		}
		return this.weaponReserveAmmo[weaponIndex];
	}

	/** Current accumulated heat for weaponIndex (see WeaponStats.isHeatBased()'s own doc). */
	public float getWeaponHeat(int weaponIndex) {
		String raw = this.dataTracker.get(WEAPON_AMMO_SYNC);
		if (raw.isEmpty()) {
			return 0f;
		}
		String[] entries = raw.split(",");
		if (weaponIndex < 0 || weaponIndex >= entries.length) {
			return 0f;
		}
		String[] parts = entries[weaponIndex].split(":");
		if (parts.length < 3) {
			return 0f;
		}
		try {
			return Float.parseFloat(parts[2]);
		} catch (NumberFormatException e) {
			return 0f;
		}
	}

	/** Current fire-rate delay remaining for weaponIndex (MC Heli's "Delay" - cooldownTicks here), in ticks (0 = free to fire). Exposed so the HUD can reflect this like it does for reload. */
	public int getWeaponCooldown(int weaponIndex) {
		String raw = this.dataTracker.get(WEAPON_AMMO_SYNC);
		if (raw.isEmpty()) {
			return 0;
		}
		String[] entries = raw.split(",");
		if (weaponIndex < 0 || weaponIndex >= entries.length) {
			return 0;
		}
		String[] parts = entries[weaponIndex].split(":");
		if (parts.length < 4) {
			return 0;
		}
		try {
			return Integer.parseInt(parts[3]);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Current selected mode (0 or 1 - see WeaponDefinition's own hasModes()/tudursvehiclemod$tryToggleWeaponMode() doc) for weaponIndex, read from WEAPON_AMMO_SYNC. Public so client.hud.HudVariables can show it. */
	public int getWeaponMode(int weaponIndex) {
		String raw = this.dataTracker.get(WEAPON_AMMO_SYNC);
		if (raw.isEmpty()) {
			return 0;
		}
		String[] entries = raw.split(",");
		if (weaponIndex < 0 || weaponIndex >= entries.length) {
			return 0;
		}
		String[] parts = entries[weaponIndex].split(":");
		if (parts.length < 5) {
			return 0;
		}
		try {
			return Integer.parseInt(parts[4]);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Places each passenger at the seat with the same index as their mount order. */
	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		VehicleDefinition def = getDefinition();
		int index = this.tudursvehiclemod$getAssignedSeatIndex(passenger);
		if (index >= 0 && index < def.seats().size()) {
			SeatDefinition seat = def.seats().get(index);
			return new Vec3d(seat.offsetX(), seat.offsetY(), seat.offsetZ()).multiply(def.scale());
		}
		return super.getPassengerAttachmentPos(passenger, dimensions, scaleFactor);
	}

	/** The rotation used to orient a passenger's seat offset, current (non- interpolated) tick values. */
	protected org.joml.Quaternionf getSeatRotationCurrent() {
		return new org.joml.Quaternionf()
				.rotateY((float) Math.toRadians(-this.getYaw()))
				.rotateX((float) Math.toRadians(this.getPitch()))
				.rotateZ((float) Math.toRadians(this.getRoll()));
	}

	/** Pitch+roll only, split out from getSeatRotationCurrent()'s own same convention (that method itself applies roll first, then pitch, then yaw, when transforming a vector - this is exactly its own first two steps). Used to pre-rotate mesh vertices before the (unchanged) horizontal-plane waterline slice - see ServerObjModelHitboxes's own findWaterlineExtremes() doc and IMPLEMENTATION_NOTES.md "Wake Trail" section for the fuller rationale. Yaw is deliberately excluded here: a pure rotation around the world-vertical axis never changes any vertex's own height at all, so it's irrelevant to which vertices end up "at the waterline" - only pitch and roll actually tilt the hull away from level. */
	protected org.joml.Quaternionf tudursvehiclemod$getPitchRollRotation() {
		return new org.joml.Quaternionf()
				.rotateX((float) Math.toRadians(this.getPitch()))
				.rotateZ((float) Math.toRadians(this.getRoll()));
	}

	/** The yaw-only remainder of getSeatRotationCurrent() - applied to a point that has ALREADY had tudursvehiclemod$getPitchRollRotation() applied to it (during waterline slicing), completing the exact same overall rotation getSeatRotationCurrent() itself would have produced, just split into two separately-timed steps instead of one. */
	protected org.joml.Quaternionf tudursvehiclemod$getYawOnlyRotation() {
		return new org.joml.Quaternionf().rotateY((float) Math.toRadians(-this.getYaw()));
	}

	/** Public wrapper around getSeatRotationCurrent(), for client.debug.HitDetectionMeshDebugRenderer to transform hit-detection mesh triangles into world space (the reverse of isPointNearMeshSurface()'s own transform). */
	public org.joml.Quaternionf tudursvehiclemod$getCurrentRotationForRendering() {
		return this.getSeatRotationCurrent();
	}

	/** Public version of the same geometry-based surface test updateCustomHitDetection() uses for projectiles - reused by AircraftEntity's own vehicle-vs-vehicle collision. */
	public boolean tudursvehiclemod$isPointNearMeshSurface(Vec3d worldPos) {
		VehicleDefinition def = this.getDefinition();
		org.joml.Quaternionf inverseRotation = this.getSeatRotationCurrent().conjugate();
		float scale = def.scale();
		return tudursvehiclemod$isWorldPointNearVehicleSurface(
				worldPos.x, worldPos.y, worldPos.z, inverseRotation, scale, def);
	}

	/** The resolved static body mesh, cached per definition. tudursvehiclemod$isWorldPointNearVehicleSurface() previously called asset.ServerObjModelHitboxes's own getMeshExcludingGroups() directly on EVERY point test, and while that method caches the mesh itself, reaching that cache still required building its key first - modelId concatenated with the excluded group names sorted and joined via a Stream pipeline and a string reduce. A string reduce allocates a new string at every step, so that cost grows quadratically with part count, and it was paid on every single point of every projectile check. Resolving the mesh once per definition skips that entire lookup on the hot path. Same IdentityHashMap reasoning as ANIMATED_PART_NAMES_CACHE above. */
	private static final java.util.Map<VehicleDefinition, java.util.Optional<com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.Mesh>> STATIC_BODY_MESH_CACHE =
			java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

	/** This definition's own static body mesh (animated parts excluded), resolved once and reused. */
	public static java.util.Optional<com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.Mesh> tudursvehiclemod$getStaticBodyMesh(VehicleDefinition def) {
		java.util.Optional<com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.Mesh> cached = STATIC_BODY_MESH_CACHE.get(def);
		if (cached != null) {
			return cached;
		}
		java.util.Optional<com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.Mesh> resolved =
				com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getMeshExcludingGroups(
						def.model(), tudursvehiclemod$getAnimatedPartNames(def));
		STATIC_BODY_MESH_CACHE.put(def, resolved);
		return resolved;
	}

	/** Tests against both the static body AND each independently-animated part's own CURRENT transform (not its rest pose). Returns true if any check succeeds. */
	private boolean tudursvehiclemod$isWorldPointNearVehicleSurface(double worldX, double worldY, double worldZ,
			org.joml.Quaternionf inverseRotation, float scale, VehicleDefinition def) {
		org.joml.Vector3f bodyLocal = new org.joml.Vector3f(
				(float) (worldX - this.getX()), (float) (worldY - this.getY()), (float) (worldZ - this.getZ()));
		inverseRotation.transform(bodyLocal);
		if (scale > 0f) {
			bodyLocal.div(scale);
		}
		var staticMeshOpt = tudursvehiclemod$getStaticBodyMesh(def);
		if (staticMeshOpt.isPresent()
				&& com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.isInside(staticMeshOpt.get(), bodyLocal.x, bodyLocal.y, bodyLocal.z)) {
			return true;
		}
		return tudursvehiclemod$isNearAnimatedParts(bodyLocal, def);
	}

	/** Ticks between wake history samples. See IMPLEMENTATION_NOTES.md "Wake Trail" section for design history. */
	private static final int WAKE_TRAIL_INTERVAL_TICKS = 4;
	/** Minimum speed (blocks/tick) to record a new wake point. */
	private static final double WAKE_TRAIL_MIN_SPEED = 0.05;
	/** How much this vehicle's own lastKnownHullLength grows maxAgeTicks's own scale multiplier, on top of def.wakeTrailDurationTicks()'s own base value. 0.02 gives a modest increase for a small boat (a 10-block hull: 1.2x) and a substantial one for a large vessel (a 100-block hull: 3x) - roughly the same order of magnitude client.render.VehicleEntityRenderer's own WAKE_BOW_MOUND_SIZE_PER_HULL_LENGTH already uses for a similarly length-scaled visual effect. */
	private static final float WAKE_MAX_AGE_LENGTH_SCALE_FRACTION = 0.02f;
	/** Per a further direct request (see tudursvehiclemod$updateWakeTrail()'s own doc for the fuller rationale): the fixed (not hull-size-scaled) portion of the bow/stern prune buffer, mirroring client.render.VehicleEntityRenderer's own (now-unified) WAKE_MAIN_SINK_DESPAWN_TICKS (a separate class - these two need to be kept in sync manually if either changes) - the sink-then-despawn duration itself is a fixed visual-effect length, not something that grows with a bigger hull the way the gap-delay portion of the stern's own buffer does. Shared by both bow and stern: a bow point's own sink-start timing needs no separate gap-delay term added on top (a bow tile has no per-point creation-to-physical-bow-arrival delay the way a stern point's own does), so for bow the fixed despawn-window portion alone is what this buffer amounts to. */
	private static final long WAKE_MAIN_SINK_DESPAWN_MIRROR_TICKS = 200;
	/** The low-pass filter's own responsiveness - each tick, smoothedTurnRate moves this fraction of the way from its own current value toward the raw, this-instant turn rate.
	 *
	 * Turn-tracking responsiveness for a steady turn was fine, but changes at a turn's start/end were not followed closely enough: the original 0.2 (settling to within ~5% of a step change in ~15 ticks/0.75s) over-corrected - smoothing a genuine, fast-changing turn rate over 15 ticks meant several consecutive wake points near a turn's own start/end all captured a noticeably STALE value, lagging behind what this vehicle was actually doing by that point. Raised to 0.5 (settles in ~6 ticks/0.3s, roughly one-and-a-half WAKE_TRAIL_INTERVAL_TICKS generation intervals) - still smooths away the original single-tick spike/jump between two adjacent generations, but tracks a genuinely changing turn rate closely enough that consecutive points stay visually connected through a turn's own start and end, not just its steady middle. */
	private static final float WAKE_TURN_RATE_SMOOTHING_FACTOR = 0.5f;
	/** Offset below the water surface for the waterline slice used to find bow/stern points. */
	private static final double WAKE_TRAIL_SURFACE_OFFSET = 0.125;

	private int wakeTrailCooldown;
	/** True while this vehicle is moving in reverse (actualSpeed negative) - see IMPLEMENTATION_NOTES.md "Wake Trail" section. Used to swap which end (bow/stern) gets the diverging wake pattern vs the static trailing one. */
	private boolean wakeReversing;

	public boolean tudursvehiclemod$isWakeReversing() {
		return this.wakeReversing;
	}

	/** This vehicle's own most recently MEASURED hull length (world-space blocks, see clusterHullLength's own local computation) - the LARGEST across every cluster this same tick, for a multi-hull vessel. Carries over from tick to tick (not reset when this vehicle stops moving, or between the occasional ticks new points aren't actually generated) since the underlying hull shape itself doesn't change tick to tick - used to scale maxAgeTicks itself with this vehicle's own actual size, rather than every vehicle sharing one flat, unscaled default duration regardless of how large or small it actually is.
	 */
	private float lastKnownHullLength;
	/** This vehicle's own most recently MEASURED stern-gap distance (world-space blocks) - the LARGEST across every cluster this same tick, for a multi-hull vessel. */
	private float lastKnownSternGapDistance;
	/** Low-pass-filtered version of this vehicle's own per-tick turn rate, updated every tick regardless of whether a wake point is generated this tick - smooths out the otherwise-abrupt jump a turn starting/stopping right at a WAKE_TRAIL_INTERVAL_TICKS sample boundary would produce. */
	private float smoothedTurnRate;

	/** One wake-trail sample. Client-side only. 
	 * yaw/innerBoostSigned/referenceSpeed: heading, turn rate (sign = inner side), and speed reference at creation.
	 * leverArm: distance from this vehicle's model origin to this point's own end (bow or stern), for extra lateral swing during rotation.
	 * edgeOffsetPlus/edgeOffsetMinus: baseline offset from center along the perpendicular growth direction, split by side - lets a wide/flat bow's wake lines start from its actual corners. 0 for stern points.
	 * spreadDistance: base target reaching distance, fixed at creation (previously read live, which could make an existing tile's target shrink out from under it).
	 * moundTiltPlusX/Z/moundSpreadAnglePlusDeg (and Minus variants): bow flare direction/angle, kept separate per side for an asymmetric hull. Bow points only.
	 * hullLength: overall length at creation, scales the young-bow-tile mound size. Bow points only.
	 * sternGapDelayTicks: estimated ticks for the actual stern to reach this position, computed once at creation from creation-time speed (not referenceSpeed, which ratchets and would destabilize this estimate over the point's own lifetime). Stern points only.
	 * sternLocalOffsetX/Z: local (pre-rotation) offset at creation, re-rotated by the renderer while still within sternGapDelayTicks so a stern-band tile tracks a turn correctly before detaching and freezing in place. Stern points only. */
	public record WakeHistoryPoint(double x, double y, double z, long tick, float yaw, float innerBoostSigned, float referenceSpeed, float leverArm, float edgeOffsetPlus, float edgeOffsetMinus, float spreadDistance,
			float moundTiltPlusX, float moundTiltPlusZ, float moundSpreadAnglePlusDeg,
			float moundTiltMinusX, float moundTiltMinusZ, float moundSpreadAngleMinusDeg, float hullLength, float sternGapDelayTicks,
			float sternLocalOffsetX, float sternLocalOffsetZ) {
	}

	/** The capacity a freshly-created (or freshly-reset) wake deque starts at. Deliberately small - a vehicle that never touches water keeps only this much, and one that does simply grows from here as normal. */
	private static final int WAKE_HISTORY_INITIAL_CAPACITY = 16;

	/** Reverted back to a per-instance field - see IMPLEMENTATION_NOTES.md "Wake Trail" section for the fuller history of this reversion. Client-side-only history of this vehicle's own recent bow/stern waterline points (see tudursvehiclemod$updateWakeTrail()'s own doc) - oldest-first, read by client.render.VehicleEntityRenderer (via VehicleRenderState's own snapshot copy, taken once per frame in updateRenderState()) to draw a fading ribbon trail. The server-side copy of this same entity instance never has anything pushed into it at all (updateWakeTrail() itself returns immediately server-side), so these simply stay permanently empty there - harmless, not worth guarding against separately. Non-final so tudursvehiclemod$resetWakeHistoryCapacity() can genuinely release grown backing arrays - see that method's own doc. */
	private java.util.Deque<WakeHistoryPoint> wakeBowHistory = new java.util.ArrayDeque<>(WAKE_HISTORY_INITIAL_CAPACITY);
	private java.util.Deque<WakeHistoryPoint> wakeSternHistory = new java.util.ArrayDeque<>(WAKE_HISTORY_INITIAL_CAPACITY);
	/** Points sampled along BOTH sides of the hull, between bow and stern - see tudursvehiclemod$updateWakeTrail()'s own doc for exactly how these are generated. Rendered identically to wakeSternHistory (static, ages, sinks - see client.render.VehicleEntityRenderer's own tudursvehiclemod$renderWakeRibbon() doc), just with its own tile size for a subtler look. */
	private java.util.Deque<WakeHistoryPoint> wakeSideHistory = new java.util.ArrayDeque<>(WAKE_HISTORY_INITIAL_CAPACITY);

	/** Replaces all three wake deques with fresh, minimally-sized instances, genuinely releasing whatever backing arrays they had grown to. java.util.ArrayDeque only ever GROWS its own internal array - clear() and removeFirst() both leave that capacity fully allocated - so this outright replacement is the only way to actually return it. Called only when all three are already empty, so nothing is ever discarded here; this is purely about the (potentially large) empty arrays underneath. This is also exactly why those three fields can't be final. */
	private void tudursvehiclemod$resetWakeHistoryCapacity() {
		this.wakeBowHistory = new java.util.ArrayDeque<>(WAKE_HISTORY_INITIAL_CAPACITY);
		this.wakeSternHistory = new java.util.ArrayDeque<>(WAKE_HISTORY_INITIAL_CAPACITY);
		this.wakeSideHistory = new java.util.ArrayDeque<>(WAKE_HISTORY_INITIAL_CAPACITY);
	}

	public java.util.Deque<WakeHistoryPoint> tudursvehiclemod$getWakeBowHistory() {
		return this.wakeBowHistory;
	}

	public java.util.Deque<WakeHistoryPoint> tudursvehiclemod$getWakeSternHistory() {
		return this.wakeSternHistory;
	}

	public java.util.Deque<WakeHistoryPoint> tudursvehiclemod$getWakeSideHistory() {
		return this.wakeSideHistory;
	}

	/** Confirmed root cause - see tudursvehiclemod$updateWakeTrail()'s own doc for the fuller rationale. This is the pruning/smoothing/ratcheting portion of that same method, extracted so it can be called UNCONDITIONALLY - independent of whatever gates a specific vehicle type's own point-GENERATION logic (most commonly, whether water-surface detection currently succeeds at all). Returns whether this vehicle is currently considered stopped (|actualSpeed| < WAKE_TRAIL_MIN_SPEED) - callers that go on to attempt point generation can reuse this instead of recomputing it themselves. */
	protected boolean tudursvehiclemod$pruneWakeTrailOnly(double actualSpeed) {
		if (!this.getEntityWorld().isClient()) {
			return true;
		}
		// Updated EVERY tick, unconditionally, regardless of whether a new wake point is about to be generated this same tick (or this vehicle is even currently moving/past its own cooldown at all) - so the smoothing itself has already been building up continuously by the time any actual point capture happens, rather than starting from scratch only on the occasional tick a point is recorded.
		float rawTurnRate = MathHelper.wrapDegrees(this.getYaw() - this.lastYaw);
		this.smoothedTurnRate += (rawTurnRate - this.smoothedTurnRate) * WAKE_TURN_RATE_SMOOTHING_FACTOR;
		// A vehicle with nothing recorded in any of its own three histories at all (never been near water, or already fully aged out and pruned) has nothing whatsoever for the remaining steps below to actually do - pruning an empty deque, or ratcheting speeds across zero points, is real but wasted work, repeated every single tick for the (likely common) case of a vehicle that's never touched water in the first place. Skipped here once that's true, rather than after computing maxAgeTicks/the prune buffer only to find there was nothing to apply either to.
		if (this.wakeBowHistory.isEmpty() && this.wakeSternHistory.isEmpty() && this.wakeSideHistory.isEmpty()) {
			return Math.abs(actualSpeed) < WAKE_TRAIL_MIN_SPEED;
		}
		VehicleDefinition def = this.getDefinition();
		long now = this.getEntityWorld().getTime();
		// def.wakeTrailDurationTicks() (default 300) is now treated as a BASE duration, scaled up by this vehicle's own actual measured length (see lastKnownHullLength's own doc) - a config-file override still works exactly as before (it's still the base being scaled, not replaced), but every vehicle no longer shares one flat, unscaled default duration regardless of its own actual size.
		float maxAgeLengthScale = 1f + this.lastKnownHullLength * WAKE_MAX_AGE_LENGTH_SCALE_FRACTION;
		long maxAgeTicks = (long) (def.wakeTrailDurationTicks() * maxAgeLengthScale);
		// Pruned with an EXTRA buffer beyond the normal maxAgeTicks, mirroring the stern's own buffer
		// just below - a bow tile's own sink-then-despawn window (see client.render.VehicleEntityRenderer's
		// own tudursvehiclemod$renderWakeTiles() doc) starts at a FRACTION of maxAgeTicks and runs for a
		// further fixed window past that, so its total on-screen life extends past maxAgeTicks itself.
		// Without this buffer, this same prune call was deleting the underlying point before that window
		// ever got a chance to play out, so the tile just vanished (already-pruned) instead of visibly
		// sinking first - the bow-side counterpart of the exact bug already fixed for the stern below.
		tudursvehiclemod$pruneWakeHistory(this.wakeBowHistory, now, maxAgeTicks + WAKE_MAIN_SINK_DESPAWN_MIRROR_TICKS);
		// Pruned with a generous EXTRA buffer beyond the normal maxAgeTicks - a stern point's own gap-based sink-start delay (see client.render.VehicleEntityRenderer's own tudursvehiclemod$renderWakeTiles() doc) can push its own sink-then-despawn window well past maxAgeTicks itself; without this buffer, this same prune call was removing the underlying data before that window ever got a chance to actually play out, so the tile just vanished (already-pruned) instead of visibly sinking first.
		// Per a further direct request (see lastKnownSternGapDistance's own doc for the fuller rationale): this buffer is derived from this vehicle's own actual measured stern-gap distance (worst case: divided by WAKE_TRAIL_MIN_SPEED, this vehicle's own lowest possible creation speed) plus a fixed portion mirroring client.render.VehicleEntityRenderer's own WAKE_MAIN_SINK_DESPAWN_TICKS (a separate class - these two need to be kept in sync manually if either changes; the sink-despawn duration itself is a fixed visual-effect length, not something that scales with hull size the way the gap-delay portion does).
		float worstCaseSternGapDelayTicks = this.lastKnownSternGapDistance / (float) WAKE_TRAIL_MIN_SPEED;
		long sternPruneBufferTicks = (long) worstCaseSternGapDelayTicks + WAKE_MAIN_SINK_DESPAWN_MIRROR_TICKS;
		tudursvehiclemod$pruneWakeHistory(this.wakeSternHistory, now, maxAgeTicks + sternPruneBufferTicks);
		tudursvehiclemod$pruneWakeHistory(this.wakeSideHistory, now, maxAgeTicks);
		// java.util.ArrayDeque NEVER shrinks its own internal backing array - it only ever grows (doubling on overflow), and neither removeFirst() nor clear() releases any of that capacity. A large vessel that generated, say, 700 side-band points once would keep a 1024-element Object[] alive per deque for as long as this entity exists, even with every single point long since aged out and the deque reporting size()==0. Since the three deques only ever ALL become empty together at a genuine "this vehicle currently has no wake at all" boundary (it stopped, left the water, etc. - not something that happens every tick), replacing them outright with fresh, minimally-sized instances right at that moment is cheap in practice while genuinely returning the grown arrays to the GC. tudursvehiclemod$resetWakeHistoryCapacity()'s own doc covers why these can't simply be final anymore.
		if (this.wakeBowHistory.isEmpty() && this.wakeSternHistory.isEmpty() && this.wakeSideHistory.isEmpty()) {
			tudursvehiclemod$resetWakeHistoryCapacity();
			return Math.abs(actualSpeed) < WAKE_TRAIL_MIN_SPEED;
		}
		boolean isStopped = Math.abs(actualSpeed) < WAKE_TRAIL_MIN_SPEED;
		// Ratchets existing points' referenceSpeed upward on acceleration; runs every tick regardless of current speed.
		float ratchetSpeed = Math.abs((float) actualSpeed);
		tudursvehiclemod$ratchetWakeSpeed(this.wakeBowHistory, ratchetSpeed);
		tudursvehiclemod$ratchetWakeSpeed(this.wakeSternHistory, ratchetSpeed);
		tudursvehiclemod$ratchetWakeSpeed(this.wakeSideHistory, ratchetSpeed);
		return isStopped;
	}

	/** Records wake history samples (client-side only; pruning is split out into tudursvehiclemod$pruneWakeTrailOnly() so cleanup keeps running even when surface detection fails). Callers: ShipEntity/SubmarineEntity/CarEntity/AircraftEntity's updateVehicleMovement(). actualSpeed's sign determines wakeReversing (negative = reversing) but not whether a point is recorded (only magnitude gates that). */
	protected void tudursvehiclemod$updateWakeTrail(double surfaceY, double actualSpeed) {
		boolean isStopped = tudursvehiclemod$pruneWakeTrailOnly(actualSpeed);
		if (!this.getEntityWorld().isClient()) {
			return;
		}
		if (isStopped) {
			this.wakeTrailCooldown = 0;
			return;
		}
		this.wakeReversing = actualSpeed < 0;
		if (this.wakeTrailCooldown > 0) {
			this.wakeTrailCooldown--;
			return;
		}
		this.wakeTrailCooldown = WAKE_TRAIL_INTERVAL_TICKS;
		VehicleDefinition def = this.getDefinition();
		long now = this.getEntityWorld().getTime();
		float scale = def.scale();
		if (scale <= 0f) {
			return;
		}
		float localY = (float) ((surfaceY - WAKE_TRAIL_SURFACE_OFFSET - this.getY()) / scale);
		var staticMeshOpt = tudursvehiclemod$getStaticBodyMesh(def);
		if (staticMeshOpt.isEmpty()) {
			return;
		}
		// Pitch/roll is applied to the mesh's own vertices BEFORE slicing (see tudursvehiclemod$getPitchRollRotation()'s own doc), so which cross-section counts as "at the waterline" correctly follows the vehicle's own current tilt instead of always assuming level.
		org.joml.Quaternionf pitchRollRotation = this.tudursvehiclemod$getPitchRollRotation();
		// One entry per detected hull section (see ServerObjModelHitboxes's own findWaterlineExtremes() doc for how sections are clustered) - a single-hull vessel still gets exactly one entry here, unchanged.
		var extremesList = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.findWaterlineExtremes(staticMeshOpt.get(), localY, pitchRollRotation);
		if (extremesList.isEmpty()) {
			return;
		}
		// The extremesList points above already had pitchRollRotation applied during slicing, so only the yaw remainder is applied here to complete the world orientation - applying the full rotation again would double-count pitch/roll.
		org.joml.Quaternionf rotation = this.tudursvehiclemod$getYawOnlyRotation();
		float currentYaw = this.getYaw();
		// Uses the already-smoothed value (updated every tick since this method's own start, unconditionally - see that same field's own doc) instead of a raw, this-instant-only wrapDegrees(currentYaw - lastYaw), so a turn that's just starting or just ending contributes a gradually ramping value across several consecutive wake points instead of one abrupt jump between two adjacent ones.
		float innerBoostSigned = this.smoothedTurnRate;
		float creationSpeed = Math.abs((float) actualSpeed);
		// Computed once, shared across every cluster below.
		List<com.example.tudursvehiclemod.asset.PartAnimation> bladeParts = tudursvehiclemod$getSternBladeParts(def);
		for (var extremes : extremesList) {
			Vec3d bowWorld = tudursvehiclemod$wakeLocalToWorld(extremes.bowX(), extremes.bowY(), extremes.bowZ(), rotation, scale);
			// This cluster's own distance from the model origin (rotation center), used for the rotational-swing correction. See WakeHistoryPoint doc and IMPLEMENTATION_NOTES.md.
			float bowLeverArm = Math.abs(extremes.bowZ()) * scale;
			float maxWidthSternZ = (extremes.maxWidthSternLeftZ() + extremes.maxWidthSternRightZ()) * 0.5f;
			float sternLeverArm = Math.abs(maxWidthSternZ) * scale;
			// The distance itself is computed the same way as clusterHullLength below (a Z-difference at creation, scaled), then converted to a tick estimate using creationSpeed (this vehicle's own speed at THIS SAME moment, not a later, ratcheted value - see that same field's own doc for why this distinction matters). creationSpeed is guaranteed >= WAKE_TRAIL_MIN_SPEED already (generation itself is gated on that), so the max() below is just a defensive guard, not something that should ever actually engage.
			float sternGapDistance = Math.abs(maxWidthSternZ - extremes.sternZ()) * scale;
			float sternGapDelayTicks = sternGapDistance / Math.max(creationSpeed, 0.01f);
			// This cluster's own bow edge world positions - used below for the per-side edge offset computation.
			Vec3d bowLeftWorld = tudursvehiclemod$wakeLocalToWorld(extremes.bowLeftX(), extremes.bowLeftY(), extremes.bowLeftZ(), rotation, scale);
			Vec3d bowRightWorld = tudursvehiclemod$wakeLocalToWorld(extremes.bowRightX(), extremes.bowRightY(), extremes.bowRightZ(), rotation, scale);
			// This cluster's own target reaching distance, resolved ONCE here at creation (explicit config always wins, otherwise this cluster's own actual measured width) rather than re-resolved live at render time from this vehicle's own currently-changing width.
			float clusterSpreadDistance = def.wakeTrailSpreadDistance().orElse(extremes.width() * scale);
			// Rather than a separate, short-lived mound history, this now reuses this SAME bow tile record directly - client.render.VehicleEntityRenderer's own tudursvehiclemod$renderWakeTiles() renders a young bow tile as a raised pyramid (fading to flat as it ages), so the mound naturally inherits the exact same left/right divergence every bow tile already has, with no separate data or generation logic needed at all.
			// Use each side's own actual angle for an asymmetric hull, instead of averaging: flareLeft/flareRight (this cluster's own local-left/local-right flare) need to be matched to the renderer's own side=+1/side=-1 convention - not assumed, since that mapping depends on this project's own rotation conventions. Determined here via dot product against the SAME perpendicular direction client.render.VehicleEntityRenderer's own tudursvehiclemod$renderWakeTiles() uses for side=+1 (perpX=-cos(yaw), perpZ=-sin(yaw)) - whichever of left/right aligns with that direction is "Plus" (side>0), the other is "Minus" (side<0).
			float[] flareLeft = tudursvehiclemod$computeBowFlareDirection(extremes.bowLeftX(), extremes.bowZ(), extremes.maxWidthBowLeftX(), extremes.maxWidthBowLeftZ(), rotation);
			float[] flareRight = tudursvehiclemod$computeBowFlareDirection(extremes.bowRightX(), extremes.bowZ(), extremes.maxWidthBowRightX(), extremes.maxWidthBowRightZ(), rotation);
			double bowYawRad = Math.toRadians(currentYaw);
			double perpPlusX = -Math.cos(bowYawRad);
			double perpPlusZ = -Math.sin(bowYawRad);
			double leftDotPlus = flareLeft[0] * perpPlusX + flareLeft[1] * perpPlusZ;
			float[] flarePlus = leftDotPlus >= 0 ? flareLeft : flareRight;
			float[] flareMinus = leftDotPlus >= 0 ? flareRight : flareLeft;
			// The earlier symmetric edgeHalfWidth (half the world-space distance between bowLeftWorld and bowRightWorld) was applied as the SAME magnitude to both sides around bowWorld's own center - but bowWorld itself (the mesh's own single detected bow-tip point) isn't guaranteed to sit exactly at the true midpoint between bowLeftWorld and bowRightWorld (same root cause as the earlier flare-angle asymmetry bug), so applying one shared, averaged half-width symmetrically around an off-center point overshoots on whichever side bowWorld itself already leans toward. Fixed the same way as that earlier bug: project each ACTUAL edge's own offset from bowWorld onto the perpPlus direction, and use each side's own actual value directly - no shared/averaged magnitude at all anymore.
			double leftEdgeOffsetPlus = (bowLeftWorld.x - bowWorld.x) * perpPlusX + (bowLeftWorld.z - bowWorld.z) * perpPlusZ;
			double rightEdgeOffsetPlus = (bowRightWorld.x - bowWorld.x) * perpPlusX + (bowRightWorld.z - bowWorld.z) * perpPlusZ;
			float edgeOffsetPlus = (float) Math.max(leftEdgeOffsetPlus, rightEdgeOffsetPlus);
			float edgeOffsetMinus = (float) Math.abs(Math.min(leftEdgeOffsetPlus, rightEdgeOffsetPlus));
			// This cluster's own overall length, bow to stern.
			float clusterHullLength = Math.abs(extremes.bowZ() - extremes.sternZ()) * scale;
			// Refreshed every time this cluster's own hull is actually measured - takes the LARGEST across every cluster this same tick, for a multi-hull vessel, so a smaller secondary hull section never shrinks the cached value a larger primary one already established.
			this.lastKnownHullLength = Math.max(this.lastKnownHullLength, clusterHullLength);
			this.lastKnownSternGapDistance = Math.max(this.lastKnownSternGapDistance, sternGapDistance);
			this.wakeBowHistory.addLast(new WakeHistoryPoint(bowWorld.x, surfaceY, bowWorld.z, now, currentYaw, innerBoostSigned, creationSpeed, bowLeverArm, edgeOffsetPlus, edgeOffsetMinus, clusterSpreadDistance,
					flarePlus[0], flarePlus[1], flarePlus[2], flareMinus[0], flareMinus[1], flareMinus[2], clusterHullLength, 0f, 0f, 0f));
			// WAKE_STERN_BAND_POINT_COUNT points spread evenly across the max-width run's own actual left-to-right span at its own stern-side end.
			for (float[] localPos : tudursvehiclemod$computeSternBandLocalPositions(extremes)) {
				Vec3d bandWorld = tudursvehiclemod$wakeLocalToWorld(localPos[0], localPos[1], localPos[2], rotation, scale);
				this.wakeSternHistory.addLast(new WakeHistoryPoint(bandWorld.x, surfaceY, bandWorld.z, now, currentYaw, innerBoostSigned, creationSpeed, sternLeverArm, 0f, 0f, clusterSpreadDistance, 0f, 0f, 0f, 0f, 0f, 0f, 0f, sternGapDelayTicks,
						localPos[0] * scale, localPos[2] * scale));
			}
			// Only for a genuinely single-hull vessel (extremesList.size()==1) - a multi-hull vessel's own blade-to-hull assignment isn't attempted here, per the same request's own "if it gets complex, simple banding alone is fine" allowance.
			if (extremesList.size() == 1) {
				for (com.example.tudursvehiclemod.asset.PartAnimation blade : bladeParts) {
					// Only blades genuinely closer to the max-width terminus (this cluster's own actual stern-wash origin now) than to this cluster's own bow - excludes unrelated spinning parts elsewhere on this vehicle (e.g. a helicopter's own main rotor) from ending up as spurious stern-wash origins.
					if (Math.abs(blade.pivotZ() - maxWidthSternZ) > Math.abs(blade.pivotZ() - extremes.bowZ())) {
						continue;
					}
					Vec3d bladeWorld = tudursvehiclemod$wakeLocalToWorld(blade.pivotX(), extremes.sternY(), maxWidthSternZ, rotation, scale);
					this.wakeSternHistory.addLast(new WakeHistoryPoint(bladeWorld.x, surfaceY, bladeWorld.z, now, currentYaw, innerBoostSigned, creationSpeed, sternLeverArm, 0f, 0f, clusterSpreadDistance, 0f, 0f, 0f, 0f, 0f, 0f, 0f, sternGapDelayTicks,
							blade.pivotX() * scale, maxWidthSternZ * scale));
				}
			}
			// See tudursvehiclemod$computeSideBandLocalPositions()'s own doc for the fix (a piecewise path through the max-width run's own two actual ends instead of a single straight line).
			for (float[] localPos : tudursvehiclemod$computeSideBandLocalPositions(extremes)) {
				Vec3d sideWorld = tudursvehiclemod$wakeLocalToWorld(localPos[0], localPos[1], localPos[2], rotation, scale);
				this.wakeSideHistory.addLast(new WakeHistoryPoint(sideWorld.x, surfaceY, sideWorld.z, now, currentYaw, innerBoostSigned, creationSpeed, 0f, 0f, 0f, clusterSpreadDistance, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f));
			}
		}
	}

	/** This project's own interpretation of that 3-point construction is the right triangle formed by this end's own bow-edge reference point, the max-width run's own bow-side start on the SAME side, and that same max-width point's own projection onto a line through the bow-edge reference point parallel to the centerline - equivalently, the angle at the bow-edge reference point between the forward axis and the line to the max-width point. A genuinely pointed bow has a small angle here (the hull barely widens near the tip at all, so the max-width point sits far away along the centerline), while a blunt/flared bow has a wide angle (the hull reaches near its own full beam very close to the tip).
	 *
	 * The actual bug was simpler than double application - dx used to be computed relative to the SAME single, shared bowLocalX (the mesh-detected bow tip's own X) for both sides, and a mesh's own single "largest Z" vertex isn't guaranteed to sit exactly on X=0 even for a visually symmetric hull (ordinary triangulation can put it a little off-center) - that small offset then applied ASYMMETRICALLY to dx on each side (adding on one side, subtracting on the other), inflating one side's own angle while shrinking the other's.
	 *
	 * Strictly using X=0 causes a different inconsistency for a hull with a wide bow, so each side's own bow reference point's own X is used instead.
	 *
	 * Returns {directionX, directionZ, angleDegrees}: direction is a NORMALIZED world-space (x,z) vector (yaw already applied via rotation, matching every other position this same loop already converts to world space) - client.render.VehicleEntityRenderer's own tudursvehiclemod$renderWakeTiles() uses this to tilt a young bow tile's own mound apex toward that same direction, scaled by its own height. angleDegrees is this SAME triangle's own angle (0 to ~90 degrees) - per a further direct request ("決め打ちではモデルごとの違いに追随できません" - a fixed hardcoded value can't follow differences between models), that same renderer method now uses THIS, not a fixed constant, as the mound's own lateral-spread angle - a hull-specific value instead of one guessed number for every vehicle. Direction is (0,0) and angle is 0 for a genuinely pointed bow where this side's own bow-edge point and the max-width point coincide (or are too close together to give a meaningful direction) - atan2 itself handles that degenerate case gracefully (0 opposite / 0 adjacent both collapse to angle 0) without needing a special-cased fallback the way the direction's own normalization does. */
	private static float[] tudursvehiclemod$computeBowFlareDirection(float bowLocalX, float bowLocalZ, float maxWidthLocalX, float maxWidthLocalZ, org.joml.Quaternionf rotation) {
		float dx = maxWidthLocalX - bowLocalX;
		float dz = maxWidthLocalZ - bowLocalZ;
		float angleDeg = (float) Math.toDegrees(Math.atan2(Math.abs(dx), Math.abs(dz)));
		float len = (float) Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-4f) {
			return new float[]{0f, 0f, angleDeg};
		}
		org.joml.Vector3f vec = new org.joml.Vector3f(dx / len, 0f, dz / len);
		rotation.transform(vec);
		return new float[]{vec.x, vec.z, angleDeg};
	}

	/** Each side gets its own TWO-segment path now instead of one straight line - stern-side max-width terminus to bow-side max-width terminus (the actual near-constant-beam run, wherever it sits along this hull's own length), then that same bow-side terminus on to the bow's own actual edge corner - rather than a single straight line from an approximate stern corner all the way to the bow edge, which cuts straight through the hull's own middle for any vessel whose own widest section isn't right at the stern. */
	/** Generated exactly at the hull's own width, these points were mostly hidden inside the hull and barely visible: every one of these points previously sat EXACTLY on the hull's own actual surface boundary - indistinguishable from (or occluded by) the hull's own model geometry right there. margin (see WAKE_SIDE_BAND_OUTWARD_MARGIN_FRACTION's own doc) pushes every point outward, away from this cluster's own centerline, by a small amount scaled to this hull's own actual width - just enough to clear the hull's own surface and become visible alongside it. */
	private static List<float[]> tudursvehiclemod$computeSideBandLocalPositions(com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.BowSternPoint extremes) {
		List<float[]> positions = new java.util.ArrayList<>(WAKE_SIDE_BAND_POINT_COUNT * 4);
		float margin = extremes.width() * WAKE_SIDE_BAND_OUTWARD_MARGIN_FRACTION;
		tudursvehiclemod$addSideBandSegment(positions, extremes.maxWidthSternLeftX(), extremes.maxWidthSternLeftY(), extremes.maxWidthSternLeftZ(),
				extremes.maxWidthBowLeftX(), extremes.maxWidthBowLeftY(), extremes.maxWidthBowLeftZ(), -margin);
		tudursvehiclemod$addSideBandSegment(positions, extremes.maxWidthBowLeftX(), extremes.maxWidthBowLeftY(), extremes.maxWidthBowLeftZ(),
				extremes.bowLeftX(), extremes.bowLeftY(), extremes.bowLeftZ(), -margin);
		tudursvehiclemod$addSideBandSegment(positions, extremes.maxWidthSternRightX(), extremes.maxWidthSternRightY(), extremes.maxWidthSternRightZ(),
				extremes.maxWidthBowRightX(), extremes.maxWidthBowRightY(), extremes.maxWidthBowRightZ(), margin);
		tudursvehiclemod$addSideBandSegment(positions, extremes.maxWidthBowRightX(), extremes.maxWidthBowRightY(), extremes.maxWidthBowRightZ(),
				extremes.bowRightX(), extremes.bowRightY(), extremes.bowRightZ(), margin);
		return positions;
	}

	/** Linearly interpolates WAKE_SIDE_BAND_POINT_COUNT points (t=0 at the first point through t=1 at the second, both endpoints included) along one straight segment of tudursvehiclemod$computeSideBandLocalPositions()'s own now-piecewise path, appending them to positions. xMargin is added to every interpolated point's own local X (negative pushes left/outward on the left side, positive pushes right/outward on the right side) - see that method's own doc for why this margin exists at all. */
	private static void tudursvehiclemod$addSideBandSegment(List<float[]> positions, float x0, float y0, float z0, float x1, float y1, float z1, float xMargin) {
		for (int i = 0; i < WAKE_SIDE_BAND_POINT_COUNT; i++) {
			float t = WAKE_SIDE_BAND_POINT_COUNT == 1 ? 0.5f : (float) i / (WAKE_SIDE_BAND_POINT_COUNT - 1);
			positions.add(new float[]{x0 + t * (x1 - x0) + xMargin, y0 + t * (y1 - y0), z0 + t * (z1 - z0)});
		}
	}

	/** How far outward (as a fraction of this cluster's own overall width) side-band points are pushed past the hull's own actual surface. 0.08 (8% of beam) is enough to clear the hull's own model geometry on most hull shapes without the band reading as obviously detached from the hull. */
	private static final float WAKE_SIDE_BAND_OUTWARD_MARGIN_FRACTION = 0.08f;

	/** How many points tudursvehiclemod$addSideBandSegment() spreads along EACH of the (now 2, per side) segments tudursvehiclemod$computeSideBandLocalPositions() builds. */
	private static final int WAKE_SIDE_BAND_POINT_COUNT = 3;

	/** Every def.spinningParts() entry whose own part name contains "blade" (case-insensitive) - the same convention tudursvehiclemod$updateDamageSmoke() already uses to identify propeller/rotor blades on this vehicle. */
	private static List<com.example.tudursvehiclemod.asset.PartAnimation> tudursvehiclemod$getSternBladeParts(VehicleDefinition def) {
		List<com.example.tudursvehiclemod.asset.PartAnimation> result = new java.util.ArrayList<>();
		for (com.example.tudursvehiclemod.asset.PartAnimation part : def.spinningParts()) {
			if (part.part().toLowerCase(java.util.Locale.ROOT).contains("blade")) {
				result.add(part);
			}
		}
		return result;
	}

	/** WAKE_STERN_BAND_POINT_COUNT local (x,y,z) positions spread evenly across the max-width run's own actual left-to-right span at its own stern-side end (ServerObjModelHitboxes's own BowSternPoint.maxWidthSternLeft/Right - see that field's own doc), replacing the earlier synthetic sternX +- half-width guess at the stern tip itself. */
	private static List<float[]> tudursvehiclemod$computeSternBandLocalPositions(com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.BowSternPoint extremes) {
		List<float[]> positions = new java.util.ArrayList<>(WAKE_STERN_BAND_POINT_COUNT);
		for (int i = 0; i < WAKE_STERN_BAND_POINT_COUNT; i++) {
			float t = WAKE_STERN_BAND_POINT_COUNT == 1 ? 0.5f : (float) i / (WAKE_STERN_BAND_POINT_COUNT - 1);
			float x = extremes.maxWidthSternLeftX() + t * (extremes.maxWidthSternRightX() - extremes.maxWidthSternLeftX());
			float y = extremes.maxWidthSternLeftY() + t * (extremes.maxWidthSternRightY() - extremes.maxWidthSternLeftY());
			float z = extremes.maxWidthSternLeftZ() + t * (extremes.maxWidthSternRightZ() - extremes.maxWidthSternLeftZ());
			positions.add(new float[]{x, y, z});
		}
		return positions;
	}

	/** How many points tudursvehiclemod$computeSternBandLocalPositions() spreads across a single cluster's own max-width run. */
	private static final int WAKE_STERN_BAND_POINT_COUNT = 4;

	/** A cheap read-only pre-scan gates the expensive part. The rebuild loop below has to poll and re-add EVERY point (java.util.Deque offers no in-place replacement), and each updated point allocates a whole new 21-field record - so for the common case where this vehicle isn't currently exceeding any stored referenceSpeed at all (steady cruising, decelerating, stopped), that entire pass is pure waste. Iterating first to check whether ANY point actually needs raising costs one pass with zero allocation, and skips the rebuild entirely whenever nothing does. */
	private void tudursvehiclemod$ratchetWakeSpeed(java.util.Deque<WakeHistoryPoint> history, float currentSpeed) {
		boolean anyNeedsRaising = false;
		for (WakeHistoryPoint p : history) {
			if (currentSpeed > p.referenceSpeed()) {
				anyNeedsRaising = true;
				break;
			}
		}
		if (!anyNeedsRaising) {
			return;
		}
		int size = history.size();
		for (int i = 0; i < size; i++) {
			WakeHistoryPoint p = history.pollFirst();
			if (currentSpeed > p.referenceSpeed()) {
				p = new WakeHistoryPoint(p.x(), p.y(), p.z(), p.tick(), p.yaw(), p.innerBoostSigned(), currentSpeed, p.leverArm(), p.edgeOffsetPlus(), p.edgeOffsetMinus(), p.spreadDistance(), p.moundTiltPlusX(), p.moundTiltPlusZ(), p.moundSpreadAnglePlusDeg(), p.moundTiltMinusX(), p.moundTiltMinusZ(), p.moundSpreadAngleMinusDeg(), p.hullLength(), p.sternGapDelayTicks(), p.sternLocalOffsetX(), p.sternLocalOffsetZ());
			}
			history.addLast(p);
		}
	}

	/** Drops every history point older than maxAgeTicks (this vehicle's own def.wakeTrailDurationTicks() - see that field's own doc) from the FRONT of history (oldest-first, so once one point is found young enough to keep, every point after it is guaranteed young enough too - no need to scan the rest). Runs unconditionally at the top of tudursvehiclemod$updateWakeTrail() every tick, regardless of whether a NEW point is about to be added this same tick - a stopped vehicle's own existing trail still needs to age out on its own even while nothing new is being recorded. */
	private static void tudursvehiclemod$pruneWakeHistory(java.util.Deque<WakeHistoryPoint> history, long now, long maxAgeTicks) {
		while (!history.isEmpty() && now - history.peekFirst().tick() > maxAgeTicks) {
			history.removeFirst();
		}
	}

	/** Model-local -> world-space (scale, then rotate, then translate by this vehicle's own current position) - the same forward direction isWorldPointNearVehicleSurface()'s own reverse (world-to-local) transform undoes, reused here for tudursvehiclemod$updateWakeTrail()'s own bow/stern points specifically. */
	private Vec3d tudursvehiclemod$wakeLocalToWorld(float localX, float localY, float localZ, org.joml.Quaternionf rotation, float scale) {
		org.joml.Vector3f vec = new org.joml.Vector3f(localX, localY, localZ);
		vec.mul(scale);
		rotation.transform(vec);
		return new Vec3d(this.getX() + vec.x, this.getY() + vec.y, this.getZ() + vec.z);
	}

	/** Every named part this vehicle's own definition treats as independently animated (a PartAnimation/TogglePart/WeaponPart) - see tudursvehiclemod$isWorldPointNearVehicleSurface()'s own doc for why these need pulling out of the static body mesh. Cached per definition - see this method's own implementation doc below for why the earlier "recompute every call, it's cheap" reasoning turned out not to hold on the hit-detection hot path. */
	/** True for AircraftEntity, VtolEntity currently in/transitioning to aircraft mode, or HelicopterEntity while in manual mode (see that class's own tudursvehiclemod$updateManualModeMovement() doc) - the single shared check all mouse-look call sites now use. */
	public static boolean tudursvehiclemod$usesAircraftStyleOrientation(Entity vehicle) {
		// VtolEntity checked FIRST (and separately from plain AircraftEntity) since it's now a subclass of AircraftEntity - checking the generic type first would incorrectly match it even in helicopter mode.
		if (vehicle instanceof com.example.tudursvehiclemod.entity.VtolEntity vtol) {
			return !vtol.isHelicopterMode();
		}
		if (vehicle instanceof com.example.tudursvehiclemod.entity.HelicopterEntity helicopter) {
			return helicopter.isManualMode();
		}
		return vehicle instanceof AircraftEntity;
	}

	/** Cached per definition. This method rebuilt a fresh HashSet and walked all five of a definition's own part lists on EVERY call, and it is called once per hit-detection point test (see tudursvehiclemod$isWorldPointNearVehicleSurface()) - i.e. many times per projectile, per tick, per vehicle. A definition's own part lists never change at runtime, so all of that was recomputing a constant. Worse, the returned set is then handed straight to asset.ServerObjModelHitboxes's own getMeshExcludingGroups(), which sorts it and concatenates it into a cache-key string - so every one of those redundant rebuilds also drove a redundant sort and string build downstream. Keyed on the definition instance itself via IdentityHashMap: definitions are loaded once and reused for the session, so identity is both correct and avoids hashing the whole definition. */
	private static final java.util.Map<VehicleDefinition, java.util.Set<String>> ANIMATED_PART_NAMES_CACHE =
			java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

	public static java.util.Set<String> tudursvehiclemod$getAnimatedPartNames(VehicleDefinition def) {
		java.util.Set<String> cached = ANIMATED_PART_NAMES_CACHE.get(def);
		if (cached != null) {
			return cached;
		}
		java.util.Set<String> names = new java.util.HashSet<>();
		for (com.example.tudursvehiclemod.asset.PartAnimation part : def.spinningParts()) {
			names.add(part.part());
		}
		for (com.example.tudursvehiclemod.asset.TogglePart part : def.toggleParts()) {
			names.add(part.part());
		}
		for (com.example.tudursvehiclemod.asset.WeaponPart part : def.weaponParts()) {
			names.add(part.part());
		}
		for (com.example.tudursvehiclemod.asset.VtolRotorPart part : def.vtolRotorParts()) {
			names.add(part.part());
		}
		for (com.example.tudursvehiclemod.asset.AmmoPart part : def.ammoParts()) {
			names.add(part.part());
		}
		// Unmodifiable so a caller can never mutate the shared, cached instance out from under every other caller.
		java.util.Set<String> immutable = java.util.Collections.unmodifiableSet(names);
		ANIMATED_PART_NAMES_CACHE.put(def, immutable);
		return immutable;
	}

	/** Checks bodyLocal against every independently-animated part, undoing each part's own current transform first (inverse of VehicleEntityRenderer's render()). Returns true if any part's check succeeds. */
	private boolean tudursvehiclemod$isNearAnimatedParts(org.joml.Vector3f bodyLocal, VehicleDefinition def) {
		for (com.example.tudursvehiclemod.asset.PartAnimation part : def.spinningParts()) {
			var partMeshOpt = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getPartMesh(def.model(), part.part());
			if (partMeshOpt.isEmpty()) {
				continue;
			}
			org.joml.Vector3f partLocal = new org.joml.Vector3f(bodyLocal);
			// A blade that's a child of a
			// VtolRotorPart (see PartAnimation's own vtolRotorParent
			// doc) ADDITIONALLY inherits that parent nacelle's own
			// current tilt, applied LAST in the forward (renderer)
			// direction - so its own inverse is undone FIRST here,
			// before this part's own spin is undone below (see
			// client.render.VehicleEntityRenderer's own render() for
			// the exact matching forward order).
			if (part.vtolRotorParent().isPresent() && this instanceof VtolEntity vtol) {
				for (com.example.tudursvehiclemod.asset.VtolRotorPart parentRotor : def.vtolRotorParts()) {
					if (parentRotor.part().equals(part.vtolRotorParent().get())) {
						partLocal.sub(parentRotor.pivotX(), parentRotor.pivotY(), parentRotor.pivotZ());
						tudursvehiclemod$safeAxisAngleDeg(parentRotor.axisX(), parentRotor.axisY(), parentRotor.axisZ(),
								com.example.tudursvehiclemod.asset.VtolRotorPart.resolveAngleDegrees(vtol.getVtolTiltProgress())).conjugate().transform(partLocal);
						partLocal.add(parentRotor.pivotX(), parentRotor.pivotY(), parentRotor.pivotZ());
						break;
					}
				}
			}
			// Forward (renderer): translate to pivot, rotate by +phase around axis, translate back. Inverse (here): translate to pivot, rotate by -phase (conjugate), translate back.
			float phaseDeg = this.getSpinningPartPhase(part.part());
			partLocal.sub(part.pivotX(), part.pivotY(), part.pivotZ());
			tudursvehiclemod$safeAxisAngleDeg(part.axisX(), part.axisY(), part.axisZ(), phaseDeg).conjugate().transform(partLocal);
			partLocal.add(part.pivotX(), part.pivotY(), part.pivotZ());
			if (com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.isInside(partMeshOpt.get(), partLocal.x, partLocal.y, partLocal.z)) {
				return true;
			}
		}

		for (com.example.tudursvehiclemod.asset.TogglePart part : def.toggleParts()) {
			var partMeshOpt = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getPartMesh(def.model(), part.part());
			if (partMeshOpt.isEmpty()) {
				continue;
			}
			float progress = this.getTogglePartProgress(part.part());
			if ("landing_gear_reversed".equals(part.trigger())) {
				progress = 1f - progress;
			}
			org.joml.Vector3f partLocal = new org.joml.Vector3f(bodyLocal);
			if ("slide".equals(part.mode())) {
				// Forward: translate by +offset*progress. Inverse: translate by -offset*progress.
				partLocal.sub(part.offsetX() * progress, part.offsetY() * progress, part.offsetZ() * progress);
			} else if ("slide_rotate".equals(part.mode())) {
				// Forward: rotate around pivot FIRST, then slide. Inverse: undo the slide FIRST, then undo the rotation.
				partLocal.sub(part.offsetX() * progress, part.offsetY() * progress, part.offsetZ() * progress);
				partLocal.sub(part.pivotX(), part.pivotY(), part.pivotZ());
				tudursvehiclemod$safeAxisAngleDeg(part.axisX(), part.axisY(), part.axisZ(), part.maxAngle() * progress).conjugate().transform(partLocal);
				partLocal.add(part.pivotX(), part.pivotY(), part.pivotZ());
			} else {
				partLocal.sub(part.pivotX(), part.pivotY(), part.pivotZ());
				tudursvehiclemod$safeAxisAngleDeg(part.axisX(), part.axisY(), part.axisZ(), part.maxAngle() * progress).conjugate().transform(partLocal);
				partLocal.add(part.pivotX(), part.pivotY(), part.pivotZ());
			}
			if (com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.isInside(partMeshOpt.get(), partLocal.x, partLocal.y, partLocal.z)) {
				return true;
			}
		}

		for (com.example.tudursvehiclemod.asset.WeaponPart part : def.weaponParts()) {
			var partMeshOpt = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getPartMesh(def.model(), part.part());
			if (partMeshOpt.isEmpty()) {
				continue;
			}
			org.joml.Vector3f partLocal = new org.joml.Vector3f(bodyLocal);
			// Forward (renderer): parent-rotation stage around parent's own pivot FIRST (if a child), then own-rotation stage around this part's own pivot. Inverse: undo own-rotation stage first, then undo parent-rotation stage.
			org.joml.Quaternionf ownRotation = this.tudursvehiclemod$getWeaponPartOwnRotation(part, 1.0f);
			partLocal.sub((float) part.pivotX(), (float) part.pivotY(), (float) part.pivotZ());
			ownRotation.conjugate().transform(partLocal);
			partLocal.add((float) part.pivotX(), (float) part.pivotY(), (float) part.pivotZ());
			if (part.childInfo().isPresent()) {
				com.example.tudursvehiclemod.asset.WeaponPart.ChildInfo childInfo = part.childInfo().get();
				org.joml.Quaternionf parentRotation = this.tudursvehiclemod$getWeaponPartParentRotation(part, 1.0f);
				partLocal.sub((float) childInfo.parentPivotX(), (float) childInfo.parentPivotY(), (float) childInfo.parentPivotZ());
				parentRotation.conjugate().transform(partLocal);
				partLocal.add((float) childInfo.parentPivotX(), (float) childInfo.parentPivotY(), (float) childInfo.parentPivotZ());
			}
			if (com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.isInside(partMeshOpt.get(), partLocal.x, partLocal.y, partLocal.z)) {
				return true;
			}
		}

		// AddPartRotor - see VtolRotorPart's own doc. Only this vehicle's
		// own VtolEntity subclass ever actually has any of these (an
		// empty list for every other vehicle type, so this loop is a
		// no-op there), so tudursvehiclemod$getVtolTiltProgress() is only
		// ever read once THIS is confirmed to be a VtolEntity in the
		// first place.
		if (this instanceof VtolEntity vtol) {
			float tiltProgress = vtol.getVtolTiltProgress();
			for (com.example.tudursvehiclemod.asset.VtolRotorPart part : def.vtolRotorParts()) {
				var partMeshOpt = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getPartMesh(def.model(), part.part());
				if (partMeshOpt.isEmpty()) {
					continue;
				}
				// Forward (renderer): translate to pivot, rotate by +VtolRotorPart.resolveAngleDegrees(progress) around axis, translate back. Inverse (here): translate to pivot, rotate by the conjugate, translate back.
				org.joml.Vector3f partLocal = new org.joml.Vector3f(bodyLocal);
				partLocal.sub(part.pivotX(), part.pivotY(), part.pivotZ());
				tudursvehiclemod$safeAxisAngleDeg(part.axisX(), part.axisY(), part.axisZ(),
						com.example.tudursvehiclemod.asset.VtolRotorPart.resolveAngleDegrees(tiltProgress)).conjugate().transform(partLocal);
				partLocal.add(part.pivotX(), part.pivotY(), part.pivotZ());
				if (com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.isInside(partMeshOpt.get(), partLocal.x, partLocal.y, partLocal.z)) {
					return true;
				}
			}
		}

		// Checked here instead, gated on that SAME visibility check the renderer itself already uses, so a hidden part's own geometry stops contributing to hit detection the instant it stops being drawn - no transform to undo (these don't animate/move, only appear/disappear).
		for (com.example.tudursvehiclemod.asset.AmmoPart part : def.ammoParts()) {
			if (!this.tudursvehiclemod$isAmmoPartVisible(part)) {
				continue;
			}
			var partMeshOpt = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getPartMesh(def.model(), part.part());
			if (partMeshOpt.isEmpty()) {
				continue;
			}
			if (com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.isInside(partMeshOpt.get(), bodyLocal.x, bodyLocal.y, bodyLocal.z)) {
				return true;
			}
		}

		return false;
	}

	/** Safe substitute for `new Quaternionf().fromAxisAngleDeg(x, y, z, angle)` - same guard as client.render.VehicleEntityRenderer's own tudursvehiclemod$safeAxisAngleDeg() (a zero-length axis vector produces a degenerate/NaN quaternion otherwise). */
	private static org.joml.Quaternionf tudursvehiclemod$safeAxisAngleDeg(float x, float y, float z, float angleDeg) {
		if (x == 0f && y == 0f && z == 0f) {
			return new org.joml.Quaternionf();
		}
		return new org.joml.Quaternionf().fromAxisAngleDeg(x, y, z, angleDeg);
	}

	/** Checks nearby projectiles' current position against this vehicle's real model geometry, applying damage directly and discarding the projectile if it's actually inside. Only projectile-like entities - ordinary mob/player collision is unaffected. */
	private void tudursvehiclemod$updateCustomHitDetection(VehicleDefinition def) {
		if (!(this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
			return;
		}
		// Skips EVERYTHING
		// below (including the mesh lookup and, more importantly, the
		// per-vehicle world query for nearby projectiles) the instant
		// there isn't a single VehicleProjectileEntity anywhere in any
		// loaded world at all - see
		// VehicleProjectileEntity.tudursvehiclemod$anyActiveAnywhere()'s
		// own doc. This is overwhelmingly the common case (most of the
		// time, across most vehicles, nobody's actually firing anything
		// at any given moment) and this check itself is essentially free.
		if (!com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity.tudursvehiclemod$anyActiveAnywhere()) {
			return;
		}
		var wholeMeshOpt = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getMesh(def.model());
		if (wholeMeshOpt.isEmpty()) {
			return;
		}
		// This used to derive the search
		// radius from this.getBoundingBox() - now (per a separate, direct
		// request) always a small, fixed (1.6, 1.0) box regardless of the
		// vehicle's own real visual size (see getDimensions()'s own doc),
		// which made this search box drastically too small for any large
		// vehicle - a 200+ block ship's own hull, far from wherever this
		// entity's own position/origin happens to sit, could never even
		// be found as a candidate at all, while a weapon part positioned
		// close to that same origin still could, exactly matching a
		// direct report of hits registering on parts but never the main
		// body specifically for large vehicles. Derives the radius from
		// the model's own actual mesh bounding box (its full diagonal, so
		// this stays correct regardless of the vehicle's current
		// rotation) instead - a generous, correctly-sized sphere around
		// this vehicle's own position.
		var wholeMesh = wholeMeshOpt.get();
		float scale = def.scale();
		double meshDiagonal = Math.sqrt(
				Math.pow(wholeMesh.maxX() - wholeMesh.minX(), 2)
						+ Math.pow(wholeMesh.maxY() - wholeMesh.minY(), 2)
						+ Math.pow(wholeMesh.maxZ() - wholeMesh.minZ(), 2))
				* scale;
		net.minecraft.util.math.Box searchBox = new net.minecraft.util.math.Box(
				this.getX(), this.getY(), this.getZ(), this.getX(), this.getY(), this.getZ())
				.expand(meshDiagonal);
		List<net.minecraft.entity.projectile.ProjectileEntity> candidates = this.getEntityWorld().getEntitiesByClass(
				net.minecraft.entity.projectile.ProjectileEntity.class, searchBox,
				projectile -> !projectile.isRemoved() && !tudursvehiclemod$wasFiredByThisVehicle(projectile));
		if (candidates.isEmpty()) {
			return;
		}

		// When the opt-in cross-vehicle parallel
		// path is enabled (see VehicleModServerConfig's own
		// parallelHitDetectionAcrossVehicles doc), this vehicle's own
		// candidates are handed to HitDetectionCoordinator instead of
		// being checked inline right here - see that class's own doc for
		// why batching every vehicle's own check into ONE parallel pass
		// at end of tick can distribute meaningfully better than each
		// vehicle independently parallelizing over its own (usually just
		// one or two) candidates. Candidate GATHERING deliberately stays
		// here, on the server thread, since world entity queries are not
		// safe to run off-thread - only the pure geometry math is
		// actually deferred.
		if (com.example.tudursvehiclemod.VehicleModServerConfig.get().parallelHitDetectionAcrossVehicles) {
			HitDetectionCoordinator.tudursvehiclemod$submit(this, serverWorld, candidates);
			return;
		}

		org.joml.Quaternionf inverseRotation = this.getSeatRotationCurrent().conjugate();
		// Sequential rather than parallel: candidate sets here are typically 0-3 projectiles, where
		// candidate sets here are typically 0-3 projectiles, where
		// parallelStream()'s own fork/join dispatch overhead exceeds
		// whatever it could possibly save - a plain sequential stream is
		// both simpler and actually cheaper at this scale.
		List<net.minecraft.entity.projectile.ProjectileEntity> hits = candidates.stream()
				.filter(projectile -> tudursvehiclemod$checkProjectileHit(projectile, inverseRotation, scale, def))
				.collect(java.util.stream.Collectors.toList());
		tudursvehiclemod$applyProjectileHits(serverWorld, hits);
	}

	/** The actual side effects (damage + discard) for a resolved set of hits - extracted so HitDetectionCoordinator (see that class's own doc) can apply its own batch's results through the exact same path, rather than duplicating this logic. ALWAYS called on the server thread, never from inside a parallel phase. */
	void tudursvehiclemod$applyProjectileHits(net.minecraft.server.world.ServerWorld serverWorld,
			List<net.minecraft.entity.projectile.ProjectileEntity> hits) {
		for (net.minecraft.entity.projectile.ProjectileEntity projectile : hits) {
			float damageAmount = tudursvehiclemod$estimateProjectileDamage(projectile);
			net.minecraft.entity.damage.DamageSource damageSource = tudursvehiclemod$estimateProjectileDamageSource(projectile, serverWorld);
			this.damage(serverWorld, damageSource, damageAmount);
			// A hit registered through this custom
			// mesh-based path went straight to a plain discard() with no
			// explosion/effects at all, unlike vanilla's own onEntityHit/
			// onBlockHit (both of which already trigger this weapon's own
			// explosion) - this gives a vehicle hit that exact same
			// treatment for this mod's own projectiles specifically.
			if (projectile instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity vehicleProjectile) {
				vehicleProjectile.tudursvehiclemod$explodeAndDiscardForVehicleHit();
			} else {
				projectile.discard();
			}
		}
	}

	/** Pure, read-only geometry check for one candidate, resolving this vehicle's own current rotation/scale/definition itself rather than taking them as parameters - so HitDetectionCoordinator's own parallel phase (see that class's own doc) can call this directly without having to capture and carry that state around per submission. Safe to call from a worker thread: touches only immutable mesh data plus this entity's own already-settled per-tick state. */
	boolean tudursvehiclemod$checkProjectileHitForCoordinator(net.minecraft.entity.projectile.ProjectileEntity projectile) {
		VehicleDefinition def = this.getDefinition();
		return tudursvehiclemod$checkProjectileHit(projectile, this.getSeatRotationCurrent().conjugate(), def.scale(), def);
	}

	/** How many points to sample along a fast projectile's own recent movement segment (see this method's own doc) - deliberately fine-grained (every ~0.25 blocks of travel) since a rocket/bomb's own much higher per-tick speed compared to a machine-gun bullet was, tunneling clean through the thin surface-distance threshold checking only its own single current-tick position. */
	private static final float HIT_CHECK_SAMPLE_SPACING = 0.25f;

	/** Samples several points along the segment from this tick's start position to current (not just the current position alone), so a fast-moving projectile can't skip over the thin detection shell between ticks. */
	private boolean tudursvehiclemod$checkProjectileHit(net.minecraft.entity.projectile.ProjectileEntity projectile,
			org.joml.Quaternionf inverseRotation, float scale, VehicleDefinition def) {
		Vec3d currentPos = projectile.getEntityPos();
		Vec3d velocity = projectile.getVelocity();
		Vec3d previousPos = currentPos.subtract(velocity);
		double segmentLength = velocity.length();
		int steps = Math.max(1, (int) Math.ceil(segmentLength / HIT_CHECK_SAMPLE_SPACING));
		for (int step = 0; step <= steps; step++) {
			double t = (double) step / steps;
			double sampleX = previousPos.x + (currentPos.x - previousPos.x) * t;
			double sampleY = previousPos.y + (currentPos.y - previousPos.y) * t;
			double sampleZ = previousPos.z + (currentPos.z - previousPos.z) * t;
			if (tudursvehiclemod$isWorldPointNearVehicleSurface(sampleX, sampleY, sampleZ, inverseRotation, scale, def)) {
				return true;
			}
		}
		return false;
	}

	/** Excludes this vehicle's own fire from self-damage - getOwner() alone is always the shooting player, never the vehicle, so this checks the recorded firing vehicle (VehicleProjectileEntity) or falls back to current-passenger status otherwise. */
	private boolean tudursvehiclemod$wasFiredByThisVehicle(net.minecraft.entity.projectile.ProjectileEntity projectile) {
		if (projectile instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity vehicleProjectile) {
			return vehicleProjectile.tudursvehiclemod$getFiringVehicle() == this;
		}
		Entity owner = projectile.getOwner();
		return owner != null && owner.getVehicle() == this;
	}

	/** VehicleProjectileEntity exposes its own base damage directly; other projectile types (vanilla arrows/tridents, whose damage accessor isn't reliably available across mappings) fall back to a velocity-scaled estimate. */
	private static float tudursvehiclemod$estimateProjectileDamage(net.minecraft.entity.projectile.ProjectileEntity projectile) {
		if (projectile instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity vehicleProjectile) {
			return vehicleProjectile.tudursvehiclemod$getDamage();
		}
		double speed = projectile.getVelocity().length();
		return (float) net.minecraft.util.math.MathHelper.clamp(speed * 6.0, 1.0, 10.0);
	}

	/** Best-effort DamageSource for a projectile that just hit this
	 * vehicle's own custom hit detection - a generic "thrown" source,
	 * attributed to the projectile's own owner where one exists. */
	private static net.minecraft.entity.damage.DamageSource tudursvehiclemod$estimateProjectileDamageSource(
			net.minecraft.entity.projectile.ProjectileEntity projectile, net.minecraft.server.world.ServerWorld serverWorld) {
		net.minecraft.entity.Entity owner = projectile.getOwner();
		return serverWorld.getDamageSources().thrown(projectile, owner);
	}

	/** Render-interpolated counterpart of getSeatRotationCurrent(). */
	protected org.joml.Quaternionf getSeatRotationInterpolated(float tickDelta) {
		return new org.joml.Quaternionf()
				.rotateY((float) Math.toRadians(-this.getYaw(tickDelta)))
				.rotateX((float) Math.toRadians(this.getPitch(tickDelta)))
				.rotateZ((float) Math.toRadians(this.getRoll(tickDelta)));
	}

	@Override
	public void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
		if (!this.hasPassenger(passenger)) {
			return;
		}
		VehicleDefinition def = getDefinition();
		int index = this.tudursvehiclemod$getAssignedSeatIndex(passenger);
		if (index < 0 || index >= def.seats().size()) {
			super.updatePassengerPosition(passenger, positionUpdater);
			return;
		}

		SeatDefinition seat = def.seats().get(index);
		org.joml.Vector3f local = new org.joml.Vector3f(
				(float) (seat.offsetX() * def.scale()),
				(float) (seat.offsetY() * def.scale()),
				(float) (seat.offsetZ() * def.scale()));

		getSeatRotationCurrent().transform(local);

		// The seat/passenger position felt like it
		// tracked the vehicle body "in steps" rather than smoothly during
		// step-up: this was using the raw, instantly-jumping this.getY()
		// while the camera (getRotatedEyePos()) and visual model both
		// already ease over getRenderYOffset() - adding that same offset's
		// own CURRENT (non-interpolated, tick-based - see that method's
		// own doc) value here keeps the passenger's own actual position in
		// sync with what they're actually seeing, rather than snapping
		// ahead of it.
		Vec3d bodyFrameOffset = this.tudursvehiclemod$getBodyFrameOffset(1.0f);
		double passengerY = this.getY() + this.getRenderYOffsetCurrent();
		double targetX = this.getX() + local.x + bodyFrameOffset.x;
		double targetY = passengerY + local.y + bodyFrameOffset.y;
		double targetZ = this.getZ() + local.z + bodyFrameOffset.z;
		positionUpdater.accept(passenger, targetX, targetY, targetZ);
	}

	/** MC Heli's CameraPosition: which cameraPositions() entry is active for the assigned seat. Unsynced/client-only field - only ever read from client-side camera code (CameraMixin) for the local player's own view. */
	private int cameraPositionIndex = 0;

	/** Cycles cameraPositionIndex forward (wrapping), via the client's own camera-switch key (a new key, separate from hatch/canopy's 'H'). No-op if fewer than 2 camera positions are configured. */
	public void tudursvehiclemod$cycleCameraPosition(int seatIndex) {
		java.util.List<SeatDefinition> seats = this.getDefinition().seats();
		if (seatIndex < 0 || seatIndex >= seats.size()) {
			return;
		}
		int count = seats.get(seatIndex).cameraPositions().size();
		if (count < 2) {
			return;
		}
		this.cameraPositionIndex = (this.cameraPositionIndex + 1) % count;
	}

	/** Where `passenger`'s EYE should be, given the vehicle's full (render- interpolated) yaw/pitch/roll. */
	public Vec3d getRotatedEyePos(Entity passenger, float tickDelta) {
		VehicleDefinition def = getDefinition();
		int index = this.tudursvehiclemod$getAssignedSeatIndex(passenger);
		if (index < 0 || index >= def.seats().size()) {
			return passenger.getEyePos();
		}

		SeatDefinition seat = def.seats().get(index);

		org.joml.Vector3f local;
		if (!seat.cameraPositions().isEmpty()) {
			// An explicit camera position was set (MC Heli's own CameraPosition/
			// AddGunnerSeat camera fields) - cycles through every declared
			// entry via tudursvehiclemod$cycleCameraPosition() (see that
			// method's own doc for why 'H' itself couldn't be reused here).
			com.example.tudursvehiclemod.asset.CameraPositionEntry cameraPosition =
					seat.cameraPositions().get(this.cameraPositionIndex % seat.cameraPositions().size());
			local = new org.joml.Vector3f(
					(float) (cameraPosition.x() * def.scale()),
					(float) (cameraPosition.y() * def.scale()),
					(float) (cameraPosition.z() * def.scale()));
		} else {
			float eyeHeight = passenger.getStandingEyeHeight();
			local = new org.joml.Vector3f(
					(float) (seat.offsetX() * def.scale()),
					(float) (seat.offsetY() * def.scale() + eyeHeight),
					(float) (seat.offsetZ() * def.scale()));
		}

		org.joml.Quaternionf rotation = getSeatRotationInterpolated(tickDelta);
		rotation.transform(local);

		// This camera position needs the SAME getRenderYOffset() smoothing
		// this camera position needs the SAME getRenderYOffset() smoothing
		// the vehicle's own renderer applies (see that method's own doc) -
		// otherwise the vehicle's own visual position eases smoothly while
		// the camera (which determines how the WORLD appears to the rider)
		// still jumps instantly with the raw logical Y, making the two
		// visibly diverge from each other instead of matching.
		float renderYOffset = this.getRenderYOffset(tickDelta);
		Vec3d vehiclePos = new Vec3d(
				MathHelper.lerp(tickDelta, this.lastRenderX, this.getX()),
				MathHelper.lerp(tickDelta, this.lastRenderY, this.getY()) + renderYOffset,
				MathHelper.lerp(tickDelta, this.lastRenderZ, this.getZ()))
				.add(this.tudursvehiclemod$getBodyFrameOffset(tickDelta));
		return new Vec3d(vehiclePos.x + local.x, vehiclePos.y + local.y, vehiclePos.z + local.z);
	}

	@Override
	protected void readCustomData(ReadView view) {
		// getString(key, default) returns the value directly (not Optional) since 1.21.11's ReadView always wants a fallback rather than forcing a contains() check first.
		this.dataTracker.set(VEHICLE_ID, view.getString("VehicleDefinition", defaultDefinitionId().toString()));
		this.dataTracker.set(HEALTH, view.getFloat("Health", this.getDefinition().maxHealth()));
		this.dataTracker.set(DESTROYED, view.getBoolean("Destroyed", false));
		this.dataTracker.set(SEARCH_LIGHT_ON, view.getBoolean("SearchLightOn", false));
		this.dataTracker.set(NAV_LIGHTS_ON, view.getBoolean("NavLightsOn", true));
		// This was never actually persisted at all, so it silently reset to its own default (empty string, decoding to every seat unassigned/-1) on any entity reload.
		this.dataTracker.set(SEAT_ASSIGNMENTS, view.getString("SeatAssignments", ""));
		// See writeCustomData()'s own identical note for the full reasoning.
		this.dataTracker.set(THROTTLE, view.getFloat("Throttle", 0f));
		this.cruiseSpeed = view.getFloat("CruiseSpeed", 0f);
		// Once drawn, a wreck's own sinking attitude is saved with it. Deliberately saved only from the moment it is DRAWN, not from spawn - a healthy vehicle has no sinking data to carry, which was the whole point of moving the draw to the moment of destruction in the first place. A vehicle that never sank writes nothing and reads back the 0 default.
		this.dataTracker.set(SINK_PATTERN, view.getInt("SinkPattern", 0));
		this.dataTracker.set(SINK_TICKS, view.getInt("SinkTicks", 0));
		this.dataTracker.set(SINK_TARGET_PITCH, view.getFloat("SinkTargetPitch", 0f));
		this.dataTracker.set(SINK_TARGET_ROLL, view.getFloat("SinkTargetRoll", 0f));
		this.dataTracker.set(FUEL, view.getFloat("Fuel", this.getMaxFuel()));
		DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1 + this.getDefinition().inventorySize(), ItemStack.EMPTY);
		Inventories.readData(view, inventory);
		this.vehicleInventory = inventory;
		// Weapon ammo state, persisted as comma-separated strings - this used to never be saved/loaded at all.
		this.weaponAmmo = tudursvehiclemod$parseIntArray(view.getString("WeaponAmmo", ""));
		this.weaponReserveAmmo = tudursvehiclemod$parseIntArray(view.getString("WeaponReserveAmmo", ""));
		this.weaponReloadTicksRemaining = tudursvehiclemod$parseIntArray(view.getString("WeaponReloadTicksRemaining", ""));
		// Per groundAutopilotTurning's own doc: persisted so a reload can never spontaneously restart a turn (or lose its place in the route) that wasn't actually happening at save time. Defaults match this vehicle's own pre-persistence field initializers exactly, so an older save (or a vehicle that has never run a ground route at all) behaves identically to before this fix.
		this.groundWaypointIndex = view.getInt("GroundWaypointIndex", 0);
		this.groundWaypointWaitTicksRemaining = view.getInt("GroundWaypointWaitTicksRemaining", 0);
		this.groundAutopilotTurning = view.getBoolean("GroundAutopilotTurning", true);
		this.groundAutopilotTurnDirection = view.getFloat("GroundAutopilotTurnDirection", 0f);
		this.weaponHeat = tudursvehiclemod$parseFloatArray(view.getString("WeaponHeat", ""));
		this.weaponMode = tudursvehiclemod$parseIntArray(view.getString("WeaponMode", ""));
		// Unlike every other per-weapon array above, this one was never actually persisted at all - the field simply kept its declaration-time new int[0] through a reload, so tudursvehiclemod$ensureWeaponAmmoArraysSized()'s own "carry the old value across a resize" logic had nothing to carry, and every weapon's own cycling silently reset to position 0. Restored the same way the others are.
		this.weaponFiringPositionIndex = tudursvehiclemod$parseIntArray(view.getString("WeaponFiringPositionIndex", ""));
		// This array was never persisted at all, so a mothership chunk unload-then-reload cycle (or a server restart) silently reset it to empty/zero, "forgetting" any aircraft still out there even though the aircraft itself (which DOES persist its own state) was genuinely still in flight.
		this.tudursvehiclemod$readCarrierInFlightAircraft(view.getString("CarrierInFlightAircraft", ""));
		this.tudursvehiclemod$readCarrierLinkedAircraftBySeat(view.getString("CarrierLinkedAircraftBySeat", ""));
		// The raw arrays above are correctly restored from NBT (e.g. on server restart/world reload/a chunk unload-then-reload cycle), but WEAPON_AMMO_SYNC itself (the client-visible DataTracker string these arrays get encoded INTO) is a separate field that was never explicitly rebuilt here - it stayed at its default, empty initDataTracker() value regardless of what was just loaded, so the HUD/AmmoPart visibility read this as "not yet synced" (0/0, or falling back to a full magazine) until some OTHER event (firing, a reload tick finishing, resupplying) happened to call this and catch it up.
		this.tudursvehiclemod$syncWeaponAmmo();
		int droneX = view.getInt("DroneCenterX", Integer.MIN_VALUE);
		if (droneX != Integer.MIN_VALUE) {
			this.droneCenterPos = new net.minecraft.util.math.BlockPos(droneX,
					view.getInt("DroneCenterY", 0), view.getInt("DroneCenterZ", 0));
			this.droneSpeedFraction = view.getFloat("DroneSpeedFraction", 0.5f);
			this.droneOrbitAltitude = view.getFloat("DroneOrbitAltitude", 15.0f);
			this.droneRadiusMultiplier = view.getFloat("DroneRadiusMultiplier", 1.0f);
			this.dataTracker.set(DRONE_ACTIVE, true);
		}
		// These deploy/fold TARGET states previously reset to their own hardcoded defaults on every world reload (server restart, chunk unload-then-reload) instead of staying wherever the player last left them.
		this.dataTracker.set(GEAR_DEPLOYED, view.getBoolean("GearDeployed", true));
		this.dataTracker.set(WING_FOLD_OPEN, view.getBoolean("WingFoldOpen", false));
		this.dataTracker.set(HATCH_OPEN, view.getBoolean("HatchOpen", true));
		// Per CANOPY_OPEN's own doc: same fallback default as HatchOpen's own (true) for a save predating this split - such a save's own "HatchOpen" value already represented both hatch and canopy together, so both should start from that same value on first load after the split, then diverge independently from there on.
		this.dataTracker.set(CANOPY_OPEN, view.getBoolean("CanopyOpen", view.getBoolean("HatchOpen", true)));
		// Restores this vehicle's own previously-spawned tile UUIDs, so tudursvehiclemod$updateCarrierRunwayPlatform() can find and simply reposition/resize them again instead of creating an entirely new batch.
		//
		// Index 0 keeps the ORIGINAL, unsuffixed key names (so a save from before this change loads unchanged, with no migration needed at all), while index 1 and beyond use a "..Runway{N}" suffix. Walked until a completely absent index is hit, rather than reading this vehicle's own runways().size() - the actual VehicleDefinition isn't necessarily resolved yet at this point in loading, and an absent key naturally means "nothing more was ever saved here" regardless.
		for (int runwayIndex = 0; ; runwayIndex++) {
			String suffix = runwayIndex == 0 ? "" : "Runway" + runwayIndex;
			String interiorKey = "CarrierRunwayPlatformEntities" + suffix;
			String interiorIds = view.getString(interiorKey, "");
			// BorderLeftIds is still read here purely to preserve this loop's own existing continuation check (an old save with only border data but no interior data at some index should still be recognized as "something was saved here") - no longer decoded/stored anywhere, since border tiles aren't separately tracked at all anymore.
			String borderLeftIds = view.getString("CarrierRunwayBorderPlatformEntitiesLeft" + suffix, "");
			if (interiorIds.isEmpty() && borderLeftIds.isEmpty()) {
				break;
			}
			RunwayTileState state = this.tudursvehiclemod$getOrCreateRunwayTileState(runwayIndex);
			tudursvehiclemod$decodeRunwayTileIds(interiorIds, state.interior);
		}
	}

	/** Appends every UUID in encoded (same comma-separated format tudursvehiclemod$encodeRunwayTileIds() below produces) onto target. No-op if encoded is empty. */
	private static void tudursvehiclemod$decodeRunwayTileIds(String encoded, java.util.List<java.util.UUID> target) {
		if (encoded.isEmpty()) {
			return;
		}
		for (String idString : encoded.split(",")) {
			if (!idString.isEmpty()) {
				target.add(java.util.UUID.fromString(idString));
			}
		}
	}

	@Override
	protected void writeCustomData(WriteView view) {
		view.putString("VehicleDefinition", this.dataTracker.get(VEHICLE_ID));
		view.putFloat("Health", this.dataTracker.get(HEALTH));
		view.putBoolean("Destroyed", this.dataTracker.get(DESTROYED));
		view.putBoolean("SearchLightOn", this.dataTracker.get(SEARCH_LIGHT_ON));
		view.putBoolean("NavLightsOn", this.dataTracker.get(NAV_LIGHTS_ON));
		// See readCustomData()'s own identical note for why this is needed at all.
		view.putString("SeatAssignments", this.dataTracker.get(SEAT_ASSIGNMENTS));
		// A reload silently resets a moving vehicle back to a dead stop server-side (THROTTLE/cruiseSpeed both defaulting to 0), even though its own last-saved velocity/position looked like it should still be under way.
		view.putFloat("Throttle", this.dataTracker.get(THROTTLE));
		view.putFloat("CruiseSpeed", this.cruiseSpeed);
		// Only written once a wreck actually has a sinking attitude - see readCustomData()'s own note for why a healthy vehicle deliberately stores nothing.
		if (this.dataTracker.get(SINK_PATTERN) != 0) {
			view.putInt("SinkPattern", this.dataTracker.get(SINK_PATTERN));
			view.putInt("SinkTicks", this.dataTracker.get(SINK_TICKS));
			view.putFloat("SinkTargetPitch", this.dataTracker.get(SINK_TARGET_PITCH));
			view.putFloat("SinkTargetRoll", this.dataTracker.get(SINK_TARGET_ROLL));
		}
		view.putFloat("Fuel", this.dataTracker.get(FUEL));
		Inventories.writeData(view, this.tudursvehiclemod$getInventory(), true);
		// Per readCustomData()'s own doc for why these are persisted at all.
		view.putString("WeaponAmmo", tudursvehiclemod$encodeIntArray(this.weaponAmmo));
		view.putString("WeaponReserveAmmo", tudursvehiclemod$encodeIntArray(this.weaponReserveAmmo));
		view.putString("WeaponReloadTicksRemaining", tudursvehiclemod$encodeIntArray(this.weaponReloadTicksRemaining));
		view.putInt("GroundWaypointIndex", this.groundWaypointIndex);
		view.putInt("GroundWaypointWaitTicksRemaining", this.groundWaypointWaitTicksRemaining);
		view.putBoolean("GroundAutopilotTurning", this.groundAutopilotTurning);
		view.putFloat("GroundAutopilotTurnDirection", this.groundAutopilotTurnDirection);
		view.putString("WeaponHeat", tudursvehiclemod$encodeFloatArray(this.weaponHeat));
		view.putString("WeaponMode", tudursvehiclemod$encodeIntArray(this.weaponMode));
		view.putString("WeaponFiringPositionIndex", tudursvehiclemod$encodeIntArray(this.weaponFiringPositionIndex));
		view.putString("CarrierInFlightAircraft", this.tudursvehiclemod$encodeCarrierInFlightAircraft());
		view.putString("CarrierLinkedAircraftBySeat", this.tudursvehiclemod$encodeCarrierLinkedAircraftBySeat());
		if (this.droneCenterPos != null) {
			view.putInt("DroneCenterX", this.droneCenterPos.getX());
			view.putInt("DroneCenterY", this.droneCenterPos.getY());
			view.putInt("DroneCenterZ", this.droneCenterPos.getZ());
			view.putFloat("DroneSpeedFraction", this.droneSpeedFraction);
			view.putFloat("DroneOrbitAltitude", (float) this.droneOrbitAltitude);
			view.putFloat("DroneRadiusMultiplier", this.droneRadiusMultiplier);
		}
		view.putBoolean("GearDeployed", this.dataTracker.get(GEAR_DEPLOYED));
		view.putBoolean("WingFoldOpen", this.dataTracker.get(WING_FOLD_OPEN));
		view.putBoolean("HatchOpen", this.dataTracker.get(HATCH_OPEN));
		view.putBoolean("CanopyOpen", this.dataTracker.get(CANOPY_OPEN));
		// Per readCustomData()'s own doc for the "index 0 stays unsuffixed" backward-compatibility scheme.
		for (int runwayIndex = 0; runwayIndex < this.runwayTileStates.size(); runwayIndex++) {
			RunwayTileState state = this.runwayTileStates.get(runwayIndex);
			String suffix = runwayIndex == 0 ? "" : "Runway" + runwayIndex;
			tudursvehiclemod$encodeRunwayTileIds(state.interior, s -> view.putString("CarrierRunwayPlatformEntities" + suffix, s));
		}
	}

	/** Joins ids as a comma-separated string (same format tudursvehiclemod$decodeRunwayTileIds() above expects) and hands it to writer - a no-op (writer never called) if ids is empty, so nothing is written at all rather than an empty string. */
	private static void tudursvehiclemod$encodeRunwayTileIds(java.util.List<java.util.UUID> ids, java.util.function.Consumer<String> writer) {
		if (ids.isEmpty()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ids.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(ids.get(i));
		}
		writer.accept(sb.toString());
	}

	private static String tudursvehiclemod$encodeIntArray(int[] array) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < array.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(array[i]);
		}
		return sb.toString();
	}

	private static String tudursvehiclemod$encodeFloatArray(float[] array) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < array.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(array[i]);
		}
		return sb.toString();
	}

	private static int[] tudursvehiclemod$parseIntArray(String encoded) {
		if (encoded.isEmpty()) {
			return new int[0];
		}
		String[] parts = encoded.split(",");
		int[] result = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try {
				result[i] = Integer.parseInt(parts[i].trim());
			} catch (NumberFormatException e) {
				result[i] = 0;
			}
		}
		return result;
	}

	private static float[] tudursvehiclemod$parseFloatArray(String encoded) {
		if (encoded.isEmpty()) {
			return new float[0];
		}
		String[] parts = encoded.split(",");
		float[] result = new float[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try {
				result[i] = Float.parseFloat(parts[i].trim());
			} catch (NumberFormatException e) {
				result[i] = 0f;
			}
		}
		return result;
	}

	@Override
	public Packet<net.minecraft.network.listener.ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
		return new EntitySpawnS2CPacket(this, entityTrackerEntry);
	}

	/** Tiny indirection so this file doesn't need a circular import on VehicleMod. */
	private static final class VehicleMod_LoggerHolder {
		static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("VehicleMod/Entity");
	}
}
