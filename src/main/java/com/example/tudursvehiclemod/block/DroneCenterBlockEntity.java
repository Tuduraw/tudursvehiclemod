package com.example.tudursvehiclemod.block;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

/** Target drone control center (project-specific, not from MC Heli - per a
 * direct request, TargetDrone is a general capability any vehicle can be
 * flagged for - see asset.VehicleDefinition's own isTargetDrone doc -
 * rather than MC Heli's own dedicated-vehicles-only version).
 *
 * Deliberately reuses item.DroneControlStickItem's own vehicle-registration
 * mechanism (right-click a vehicle to register a stick to it, then insert
 * that same stick here) - the exact same binding UX as block.StationBlock's
 * own doc, just for autonomous AI control here instead of player remote
 * control there. A single stick works interchangeably with either block.
 *
 * Has 2 real inventory slots (see screen.DroneCenterScreenHandler)
 * - slot 0 the binding stick, slot 1 an optional item.DroneRouteBookItem
 * (see that class's own doc) - rather than the earlier sneak-click
 * insert/extract mechanic. This center's own "bound vehicle" is derived
 * directly from whatever registered stick currently sits in slot 0 (see
 * tudursvehiclemod$getBoundVehicleId()) rather than tracked as a separate
 * field that needed manual bookkeeping to stay in sync with the slot. */
public class DroneCenterBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, Inventory {

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);

	/** How many total aircraft (including the leader, this center's own bound vehicle in slot 0) fly together. 1 (the default) means no formation at all - existing saves/behavior are completely unchanged. */
	private int formationSize = 1;
	/** See formationSize's own doc. LINE_ABREAST by default, matching CasStrikeConfig's own equivalent default. */
	private com.example.tudursvehiclemod.asset.FormationType formationType = com.example.tudursvehiclemod.asset.FormationType.LINE_ABREAST;
	/** See formationSize's own doc. */
	private double formationSpacing = 8.0;
	/** See formationSize's own doc. Negative means "not explicitly configured" - falls back to AbstractVehicleEntity's own auto-derived value, same convention as CasStrikeConfig's own elementSpacing. */
	private double formationElementSpacing = -1.0;

	/** One DroneControlStickItem per wingman slot (formationSize - 1 of them - the leader itself is slot 0 of the main 2-slot inventory above, not part of this one at all), each stick's own registered vehicle (see DroneControlStickItem's own doc) designating exactly which vehicle flies at that SPECIFIC formation position. Slot INDEX directly determines formation position (formation index = slot index + 1, since index 0 of the OVERALL formation is always the leader) - a gap (empty slot) simply means no aircraft occupies that specific position; slots are never compacted/shifted to fill gaps. Resized (never destructively - existing stick stacks beyond a shrunk size are simply dropped, matching AbstractVehicleEntity's own cargo-inventory resize convention) whenever formationSize changes. */
	private DefaultedList<ItemStack> formationSlots = DefaultedList.of();

	/** A very large formationSize could overflow the slot screen off the bottom the same way a large vehicle inventorySize() once did: which page of formation slots (screen.DroneCenterFormationScreenHandler's own PAGE_SIZE=45 slots, mirroring VehicleMenuScreenHandler's own identical page size) is currently shown. Not persisted - purely a transient UI concern, resets to page 0 on reload. */
	private int formationSlotPage;

	/** Wraps formationSlots as a standalone Inventory, separate from this whole class's own Inventory implementation (which is exclusively the main 2-slot bound-vehicle/route-book inventory) - needed since a BlockEntity can only implement Inventory once, but this needs its own independent size()/getStack()/setStack() backed by a DIFFERENT list. Mirrors screen.VehicleMenuScreenHandler's own PagedCargoView pattern closely (paginated, dynamically sized). */
	public final class FormationSlotsInventory implements Inventory {
		@Override
		public int size() {
			return DroneCenterBlockEntity.this.formationSlots.size();
		}

		@Override
		public boolean isEmpty() {
			for (ItemStack stack : DroneCenterBlockEntity.this.formationSlots) {
				if (!stack.isEmpty()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public ItemStack getStack(int slot) {
			return slot >= 0 && slot < DroneCenterBlockEntity.this.formationSlots.size()
					? DroneCenterBlockEntity.this.formationSlots.get(slot) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeStack(int slot, int amount) {
			if (slot < 0 || slot >= DroneCenterBlockEntity.this.formationSlots.size()) {
				return ItemStack.EMPTY;
			}
			ItemStack result = net.minecraft.inventory.Inventories.splitStack(DroneCenterBlockEntity.this.formationSlots, slot, amount);
			if (!result.isEmpty()) {
				DroneCenterBlockEntity.this.markDirty();
			}
			return result;
		}

		@Override
		public ItemStack removeStack(int slot) {
			if (slot < 0 || slot >= DroneCenterBlockEntity.this.formationSlots.size()) {
				return ItemStack.EMPTY;
			}
			return net.minecraft.inventory.Inventories.removeStack(DroneCenterBlockEntity.this.formationSlots, slot);
		}

		@Override
		public void setStack(int slot, ItemStack stack) {
			if (slot >= 0 && slot < DroneCenterBlockEntity.this.formationSlots.size()) {
				DroneCenterBlockEntity.this.formationSlots.set(slot, stack);
				DroneCenterBlockEntity.this.markDirty();
			}
		}

		@Override
		public void markDirty() {
			DroneCenterBlockEntity.this.markDirty();
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return DroneCenterBlockEntity.this.getWorld() != null
					&& DroneCenterBlockEntity.this.getWorld().getBlockEntity(DroneCenterBlockEntity.this.getPos()) == DroneCenterBlockEntity.this
					&& player.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(DroneCenterBlockEntity.this.getPos())) <= 64.0;
		}

		@Override
		public void clear() {
			for (int i = 0; i < DroneCenterBlockEntity.this.formationSlots.size(); i++) {
				this.removeStack(i);
			}
		}
	}



	/** Bound vehicle's last-known chunk, persisted to disk - same reasoning as StationBlockEntity's own lastKnownVehicleChunk. */
	private ChunkPos lastKnownVehicleChunk;

	/** The 3x3 grid of chunks centered on wherever the bound vehicle is CURRENTLY flying, force-loaded for as long as this center is active - recomputed and moves with the vehicle each tick (unlike lastKnownVehicleChunk above, which is only a persisted "last seen at" hint for re-finding a vehicle that's gone missing entirely). Not persisted - re-established the next time tick() finds the vehicle after a reload, same as any other in-memory-only tracking state here. Uses the SAME 3x3-grid approach (not just the single current chunk) already applied to CAS aircraft - a single-chunk approach was confirmed to have a real vulnerability at chunk-transition boundaries there, and this vehicle-following logic uses the exact same single-chunk pattern, so the same fix applies here too. */
	private java.util.Set<ChunkPos> currentlyForcedChunks = java.util.Set.of();

	/** Whether this center's own bound vehicle should currently be actively flying its autonomous drone routine (see entity.AbstractVehicleEntity's own tudursvehiclemod$isDroneActive() doc) - toggled from the config screen, independent of whether the vehicle is actually currently loaded/found. stays true (so tick() keeps managing/chunk-forcing the vehicle normally) even after the user has asked to deactivate, for as long as the vehicle is still landing - tudursvehiclemod$isActive() (the externally-visible state, e.g. what the config screen shows) is NOT simply this field; see that method's own doc. */
	private boolean active;

	/** True from the moment the user asks to deactivate a still-airborne vehicle, until that vehicle's own tudursvehiclemod$isDroneLandingInProgress() reports it has actually finished landing - tick() polls this to know when to finally flip active to false for real (see this class's own doc on why active itself stays true throughout). Always false for an already-grounded vehicle (nothing to land) or once landing completes. */
	private boolean pendingDeactivation;

	/** This center's own configured autopilot settings, pushed through to the linked vehicle whenever (re-)activated/found - see AbstractVehicleEntity's own tudursvehiclemod$setDroneLink() 4-arg overload for what each of these actually configures. Defaults match that method's own defaults. */
	private float speedFraction = 0.5f;
	private double orbitAltitude = 15.0;
	private float radiusMultiplier = 1.0f;

	/** Drone Center "dummy pilot" option - whether this center spawns a DummyPilotEntity into its bound vehicle's own pilot seat while active. Off by default, so existing saves and existing routes behave exactly as before. */
	/** Whether this center's own active state is currently driven by redstone signal presence (see tudursvehiclemod$onRedstonePowerChanged()) rather than the existing manual toggle button (tudursvehiclemod$toggleActive()). Off by default, so existing saves and the existing manual-only behavior are completely unaffected until a player explicitly opts in via the config screen. The two modes are mutually exclusive at any given moment (whichever one is currently selected is the only thing that can change active), but switching modes doesn't retroactively undo whatever active value the OTHER mode last left behind - switching to redstone mode only takes effect the next time this block's own redstone input actually changes (or immediately, if tudursvehiclemod$setRedstoneControlEnabled() finds the current power state already differs from active - see that method's own doc). */
	private boolean redstoneControlEnabled;

	/** Whether an automatic low-fuel/low-ammo disable (see tudursvehiclemod$updateAutoDisable()) simply stays off until a player manually re-enables it (MANUAL_REENABLE, the default), or automatically resumes control once fuel/ammo are actually resupplied (AUTO_RESUME). Meaningless whenever redstoneControlEnabled is true: redstone-driven control never auto-disables at all, flying until the redstone signal itself changes, exactly as it already did before this whole feature existed. */
	public enum AutoDisableResumeMode {
		MANUAL_REENABLE, AUTO_RESUME
	}

	private AutoDisableResumeMode autoDisableResumeMode = AutoDisableResumeMode.MANUAL_REENABLE;
	/** Per AutoDisableResumeMode's own AUTO_RESUME doc: true from the moment tudursvehiclemod$updateAutoDisable() actually triggers an automatic disable, until either a player manually re-enables (MANUAL_REENABLE - simply cleared then, no further effect) or - only under AUTO_RESUME - fuel/ammo are confirmed resupplied and control resumes on its own. False (the ordinary, pre-this-feature state) whenever this center is inactive for any OTHER reason (a player's own manual toggle, redstone driving it low, no vehicle bound at all) - only an actual auto-disable event sets this, so AUTO_RESUME's own "resume once resupplied" check never fires for a center a player deliberately turned off themselves. */
	private boolean autoDisabledPendingResupply;

	private boolean dummyPilotEnabled;
	/** Which skin this center's own pilot uses - a plain id, see DummyPilotSkinRegistry for why not a path. */
	private String dummyPilotSkinId = com.example.tudursvehiclemod.asset.DummyPilotSkinRegistry.DEFAULT_SKIN_ID;
	/** Nametag visibility toggle. Off by default - existing saves/newly-created centers get the previous no-visible-nametag behavior unless the player explicitly turns it on. */
	private boolean dummyPilotNameVisible;
	/** An arbitrary custom name. Empty means no custom name at all (this pilot's default translated entity name is used wherever a name is needed, e.g. death messages if that were ever relevant), regardless of dummyPilotNameVisible. */
	private String dummyPilotName = "";
	/** The pilot currently spawned by this center, if any. Plain (non-persisted) field - on world reload this is simply re-resolved/re-spawned by tudursvehiclemod$updateDummyPilot() on the next tick, rather than trying to persist an entity reference. */
	private com.example.tudursvehiclemod.entity.DummyPilotEntity activeDummyPilot;

	/** Which of the bound vehicle's own weapon slots this center's own dummy pilot uses to engage hostile mobs - -1 (the default) means "not selected": an invalid/nonexistent index (< 0, or >= however many weapons the currently-bound vehicle actually has) disables hostile-mob combat entirely rather than falling back to some other weapon. Unlike CAS's own per-strike weaponIndex (fixed once at launch time, on a short-lived autonomous aircraft), this is a live, editable per-center setting a player can change at any time while the same drone keeps flying, so entity.DummyPilotEntity's own combat AI re-validates it fresh every reacquire cycle instead of capturing it once. */
	private int dummyPilotWeaponIndex = -1;
	/** Overrides entity.AircraftEntity's own tudursvehiclemod$updateCarrierLockPursuit() attack-altitude thresholds (normally read from the locked weapon's own CasAttackStartAltitude/CasAttackStopAltitude file settings) for this center's own dummy-pilot-driven aircraft attacks specifically - defaults match those same weapon-file defaults exactly ("デフォルト値はCAS/Carrierと同一"), so a freshly-placed center behaves identically to what CAS/Carrier already does until a player explicitly changes it. Meaningless for a non-aircraft vehicle (a ground/surface vehicle's own combat AI never dives/climbs at all - see DummyPilotEntity's own doc), but still stored/editable regardless, exactly like several other aircraft-only settings this same block already has (orbitAltitude, radiusMultiplier) that a ground-vehicle-bound center simply never reads. */
	private double dummyPilotCasAttackStartAltitude = 200.0;
	private double dummyPilotCasAttackStopAltitude = 40.0;
	/** How far (blocks) this center's own dummy pilot looks for a hostile mob to engage - see entity.DummyPilotEntity's own combatSearchRange doc. Default (64.0) matches that class's own previous hardcoded TARGET_SEARCH_RANGE constant exactly, so a freshly-placed center (or any save predating this feature) behaves identically until a player explicitly changes it. */
	private double dummyPilotSearchRange = 64.0;
	/** Overrides entity.AircraftEntity's own tudursvehiclemod$updateCarrierLockPursuit() dive-target Y offset for this center's own dummy-pilot-driven aircraft attacks - default 0.0 (matching this field's own natural Java default, and the direct request's own "デフォルト値を0") means "not yet explicitly configured", in which case that method itself falls back to a built-in default depending on the locked weapon's own actual type (see AircraftEntity's own CARRIER_LOCK_FALLING_WEAPON_DIVE_TARGET_Y_OFFSET doc: +20 for a falling weapon - BOMB/DEPTH - attacking a ground/water target, 0 - i.e. unchanged - for every other case) rather than this field's own raw value being used directly at 0. A player who explicitly sets ANY value here (including deliberately re-entering 0 for a falling weapon, an accepted rare edge case this simple convention doesn't distinguish from "never touched") always has that value used exactly as given, for any weapon type. */
	private double dummyPilotDiveTargetYOffset = 0.0;

	/** This center's own GROUND route (see GroundWaypoint's own doc), used instead of the aircraft `waypoints` list above whenever the bound vehicle isn't an aircraft (see tudursvehiclemod$usesGroundRoute()'s own doc for exactly how that's decided). Deliberately a SEPARATE list rather than a reinterpretation of the aircraft one: the two record types genuinely differ (no relY, plus waitTicks), and keeping both means a center whose bound vehicle changes type doesn't silently destroy or misread the route already authored for the other.
	 *
	 * Per a further direct request, ALSO doubles as the waitTicks backing store when a route book (slot 1) is inserted (see tudursvehiclemod$getGroundWaypoints()'s own doc) - the book format itself has no field for it at all, so it's tracked here independently, matched to the book's own waypoints purely by INDEX. */
	private final java.util.List<GroundWaypoint> groundWaypoints = new java.util.ArrayList<>();

	/** This center's own current ground route, relative to its own block position - either directly from the internal list above, or (if a route book is inserted in slot 1) converted from that book's own ABSOLUTE X/Z/speed, same "record freely anywhere, convert only once inserted" reasoning as tudursvehiclemod$getWaypoints()'s own doc for the aircraft route. The book's own Y and roll/maneuverability fields are simply ignored - GroundWaypoint has no use for them (see that record's own doc). waitTicks (which the book format has no field for at all) comes from this center's own internal groundWaypoints list instead, matched by index - 0 for any waypoint index the book has that the internal list doesn't (a newly-recorded stop not yet given its own wait time), so wait time defaults to 0 and is set separately from recording position. */
	public java.util.List<GroundWaypoint> tudursvehiclemod$getGroundWaypoints() {
		ItemStack book = this.items.get(1);
		if (book.getItem() instanceof com.example.tudursvehiclemod.item.DroneRouteBookItem) {
			java.util.List<DroneRouteBookWaypoint> absolute = com.example.tudursvehiclemod.item.DroneRouteBookItem.tudursvehiclemod$getWaypoints(book);
			java.util.List<GroundWaypoint> relative = new java.util.ArrayList<>();
			for (int i = 0; i < absolute.size(); i++) {
				DroneRouteBookWaypoint wp = absolute.get(i);
				int waitTicks = i < this.groundWaypoints.size() ? this.groundWaypoints.get(i).waitTicks() : 0;
				relative.add(new GroundWaypoint(wp.x() - this.getPos().getX(), wp.z() - this.getPos().getZ(), wp.speedFraction(), waitTicks));
			}
			return relative;
		}
		return java.util.List.copyOf(this.groundWaypoints);
	}

	/** Replaces this center's own entire ground route at once - same "resend everything" convention as tudursvehiclemod$setWaypoints()'s own doc. Writes through to whichever source tudursvehiclemod$getGroundWaypoints() would currently read from, same convention as that method's aircraft counterpart: with a route book present, each waypoint's own X/Z/speed is written into the book (Y taken from whatever that same index's own previous absolute Y already was, or this center's own Y for a brand new index - GroundWaypoint has no Y of its own to supply one, see that record's own doc), while EVERY waypoint's own waitTicks is additionally persisted into this center's own internal list regardless (so a later read can still recover it by index, per tudursvehiclemod$getGroundWaypoints()'s own doc) - without a book, the internal list alone is both the position/speed AND the waitTicks source, unchanged from before. */
	public void tudursvehiclemod$setGroundWaypoints(java.util.List<GroundWaypoint> newWaypoints) {
		ItemStack book = this.items.get(1);
		if (book.getItem() instanceof com.example.tudursvehiclemod.item.DroneRouteBookItem) {
			java.util.List<DroneRouteBookWaypoint> previousAbsolute = com.example.tudursvehiclemod.item.DroneRouteBookItem.tudursvehiclemod$getWaypoints(book);
			java.util.List<DroneRouteBookWaypoint> newAbsolute = new java.util.ArrayList<>();
			for (int i = 0; i < newWaypoints.size(); i++) {
				GroundWaypoint wp = newWaypoints.get(i);
				int y = i < previousAbsolute.size() ? previousAbsolute.get(i).y() : this.getPos().getY();
				newAbsolute.add(new DroneRouteBookWaypoint(wp.relX() + this.getPos().getX(), y, wp.relZ() + this.getPos().getZ(),
						wp.speedFraction(), 0f, 1.0f, 1.0f));
			}
			com.example.tudursvehiclemod.item.DroneRouteBookItem.tudursvehiclemod$setWaypoints(book, newAbsolute);
		}
		this.groundWaypoints.clear();
		this.groundWaypoints.addAll(newWaypoints);
		this.markDirty();
	}

	/** Whether this center's own currently-bound vehicle should follow the GROUND route (GroundWaypoint) instead of the aircraft one (DroneWaypoint). Decided purely from the bound vehicle's own type - an AircraftEntity (which includes VtolEntity, its own subclass) and a HelicopterEntity both fly, and keep the original aircraft autopilot entirely unchanged; a Car/Ship/Submarine moves across a surface and uses the ground autopilot instead. StaticEmplacementEntity is deliberately excluded (per that same direct request) - it cannot move at all, so neither route type means anything for it. A center with no vehicle currently bound/loaded reports false (the original aircraft behavior), so nothing changes until a surface vehicle is actually bound. */
	public static boolean tudursvehiclemod$usesGroundRoute(com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
		if (vehicle == null) {
			return false;
		}
		return vehicle instanceof com.example.tudursvehiclemod.entity.CarEntity
				|| vehicle instanceof com.example.tudursvehiclemod.entity.ShipEntity
				|| vehicle instanceof com.example.tudursvehiclemod.entity.SubmarineEntity;
	}

	/** A non-empty list here takes over from the circular-orbit autopilot entirely (see AircraftEntity's own tudursvehiclemod$updateDroneAutopilot() doc for how the two modes are chosen between) - an empty list (the default) preserves the original circular-orbit behavior unchanged. Only actually used when slot 1 (see this class's own doc) is empty - a DroneRouteBookItem there takes over instead (see tudursvehiclemod$getWaypoints()'s own doc). Per a further direct request, no longer capped at any fixed count - the previous MAX_WAYPOINTS limit has been abolished entirely. */
	private final java.util.List<DroneWaypoint> waypoints = new java.util.ArrayList<>();

	/** This center's own current patrol route, relative to its own block position - either directly from the internal list above, or (if a route book is inserted in slot 1) converted from that book's own ABSOLUTE coordinates. Recording waypoints is usable freely anywhere without the Drone Center's own config screen open (which left barely any room to walk around) - a route book records absolute positions independently, then this conversion happens only once actually inserted here. Callers (the autopilot, the config screen) don't need to know or care which source is actually backing this - see tudursvehiclemod$setWaypoints()'s own doc for the same reasoning on the write side. */
	public java.util.List<DroneWaypoint> tudursvehiclemod$getWaypoints() {
		ItemStack book = this.items.get(1);
		if (book.getItem() instanceof com.example.tudursvehiclemod.item.DroneRouteBookItem) {
			java.util.List<DroneRouteBookWaypoint> absolute = com.example.tudursvehiclemod.item.DroneRouteBookItem.tudursvehiclemod$getWaypoints(book);
			net.minecraft.util.math.Vec3i offset = com.example.tudursvehiclemod.item.DroneRouteBookItem.tudursvehiclemod$getOffset(book);
			java.util.List<DroneWaypoint> relative = new java.util.ArrayList<>();
			for (DroneRouteBookWaypoint wp : absolute) {
				relative.add(new DroneWaypoint(wp.x() + offset.getX() - this.getPos().getX(), wp.y() + offset.getY() - this.getPos().getY(),
						wp.z() + offset.getZ() - this.getPos().getZ(), wp.speedFraction(), wp.rollAngle(),
						wp.rollManeuverabilityMultiplier(), wp.turnManeuverabilityMultiplier()));
			}
			return relative;
		}
		return java.util.List.copyOf(this.waypoints);
	}

	/** Replaces the entire route at once (same "resend everything, rather than track individual deltas" reasoning as tudursvehiclemod$setConfig()'s own doc). Writes through to whichever source tudursvehiclemod$getWaypoints() would currently read from (the inserted route book's own absolute coordinates if one is present, this center's own internal relative list otherwise) - so editing from the config screen stays consistent regardless of which one is actually backing the route at the time. */
	public void tudursvehiclemod$setWaypoints(java.util.List<DroneWaypoint> newWaypoints) {
		java.util.List<DroneWaypoint> capped = newWaypoints;
		ItemStack book = this.items.get(1);
		if (book.getItem() instanceof com.example.tudursvehiclemod.item.DroneRouteBookItem) {
			java.util.List<DroneRouteBookWaypoint> absolute = new java.util.ArrayList<>();
			for (DroneWaypoint wp : capped) {
				absolute.add(new DroneRouteBookWaypoint(wp.relX() + this.getPos().getX(), wp.relY() + this.getPos().getY(),
						wp.relZ() + this.getPos().getZ(), wp.speedFraction(), wp.rollAngle(),
						wp.rollManeuverabilityMultiplier(), wp.turnManeuverabilityMultiplier()));
			}
			com.example.tudursvehiclemod.item.DroneRouteBookItem.tudursvehiclemod$setWaypoints(book, absolute);
		} else {
			this.waypoints.clear();
			this.waypoints.addAll(capped);
		}
		this.markDirty();
	}

	/** "Home Point" - a special waypoint the landing autopilot targets (see AircraftEntity's own tudursvehiclemod$updateDroneLandingRouteAutopilot() doc), editable the same way any other waypoint is. Defaults to wherever the bound vehicle actually was at the moment its stick was inserted into slot 0 (see tudursvehiclemod$setStack()'s own doc for exactly when/how that capture happens) - null only in the brief window before any stick has ever been successfully bound here at all, in which case tudursvehiclemod$getHomePoint() falls back to DroneWaypoint's own createDefault(). */
	private DroneWaypoint homePoint;

	/** This center's own current Home Point (see homePoint's own doc) - never null, falling back to DroneWaypoint.createDefault() if nothing has been captured/set yet. */
	public DroneWaypoint tudursvehiclemod$getHomePoint() {
		return this.homePoint != null ? this.homePoint : DroneWaypoint.createDefault();
	}

	/** Manually overrides the Home Point (e.g. from the detailed settings screen editing it like any other waypoint) - overwrites whatever was captured automatically at binding time. */
	public void tudursvehiclemod$setHomePoint(DroneWaypoint homePoint) {
		this.homePoint = homePoint;
		this.markDirty();
	}

	/** An additional en-route waypoint entity.AircraftEntity's own updateDroneWaypointAutopilot()-driven landing route (see that method's own doc, and its dispatch site's own doc, for the full two-leg routing) flies to FIRST, before continuing on to the Home Point itself. Unlike homePoint (which always has a usable default, DroneWaypoint.createDefault()), null here specifically means "no via-point configured at all" - the landing route then goes straight to the Home Point exactly as it always did before this feature existed, with no behavior change whatsoever for a center that never sets one. */
	private DroneWaypoint returnViaPoint;

	/** This center's own current return via-point (see returnViaPoint's own doc) - null if none has ever been configured, unlike tudursvehiclemod$getHomePoint()'s own always-non-null contract. */
	public DroneWaypoint tudursvehiclemod$getReturnViaPoint() {
		return this.returnViaPoint;
	}

	/** Sets/clears (pass null to clear) this center's own return via-point - same "editable the same way any other waypoint is" convention as tudursvehiclemod$setHomePoint(). */
	public void tudursvehiclemod$setReturnViaPoint(DroneWaypoint returnViaPoint) {
		this.returnViaPoint = returnViaPoint;
		this.markDirty();
	}

	public float tudursvehiclemod$getSpeedFraction() {
		return this.speedFraction;
	}

	public double tudursvehiclemod$getOrbitAltitude() {
		return this.orbitAltitude;
	}

	public float tudursvehiclemod$getRadiusMultiplier() {
		return this.radiusMultiplier;
	}

	/** See AutoDisableResumeMode's own doc. */
	public AutoDisableResumeMode tudursvehiclemod$getAutoDisableResumeMode() {
		return this.autoDisableResumeMode;
	}

	/** See AutoDisableResumeMode's own doc. Does not itself retroactively resume/re-disable anything - a mode switched to AUTO_RESUME while autoDisabledPendingResupply is already set simply starts being checked from the very next tick onward, same as tudursvehiclemod$updateAutoResume()'s own normal per-tick polling. */
	public void tudursvehiclemod$setAutoDisableResumeMode(AutoDisableResumeMode mode) {
		this.autoDisableResumeMode = mode;
		this.markDirty();
	}

	/** See dummyPilotEnabled's own doc. */
	public boolean tudursvehiclemod$isDummyPilotEnabled() {
		return this.dummyPilotEnabled;
	}

	/** See dummyPilotSkinId's own doc. Always resolves through the registry, so a center whose selected skin's addon has since been removed reports the default rather than a dangling id. */
	public String tudursvehiclemod$getDummyPilotSkinId() {
		return com.example.tudursvehiclemod.asset.DummyPilotSkinRegistry.tudursvehiclemod$resolveOrDefault(this.dummyPilotSkinId);
	}

	/** See dummyPilotNameVisible's own doc. */
	public boolean tudursvehiclemod$isDummyPilotNameVisible() {
		return this.dummyPilotNameVisible;
	}

	/** See dummyPilotName's own doc. */
	public String tudursvehiclemod$getDummyPilotName() {
		return this.dummyPilotName;
	}

	/** See dummyPilotWeaponIndex's own doc. */
	public int tudursvehiclemod$getDummyPilotWeaponIndex() {
		return this.dummyPilotWeaponIndex;
	}

	/** The currently-selected weapon's own DisplayName (its own weapon file's own DisplayName setting, e.g. "M134 Minigun" - see Readme_Weapon.txt's own doc for that field), for the config screen to actually show a player rather than just a bare index number. Empty ("") whenever dummyPilotWeaponIndex is out of range for the given vehicle's own actual weapon list (including the default -1, and any index a vehicle definition change has since made stale) - per dummyPilotWeaponIndex's own doc, this is exactly the same "not selected" condition that also disables hostile-mob combat entirely, so the screen and the actual AI always agree on what counts as "no weapon chosen". vehicle may be null (nothing currently bound/loaded) - also reports empty in that case, same as an out-of-range index. */
	public String tudursvehiclemod$getDummyPilotWeaponDisplayName(com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
		if (vehicle == null) {
			return "";
		}
		java.util.List<com.example.tudursvehiclemod.asset.WeaponDefinition> weapons = vehicle.getDefinition().weapons();
		if (this.dummyPilotWeaponIndex < 0 || this.dummyPilotWeaponIndex >= weapons.size()) {
			return "";
		}
		return com.example.tudursvehiclemod.asset.WeaponStatsLoader.get(weapons.get(this.dummyPilotWeaponIndex).weaponName()).displayName();
	}

	/** See dummyPilotCasAttackStartAltitude's own doc. */
	public double tudursvehiclemod$getDummyPilotCasAttackStartAltitude() {
		return this.dummyPilotCasAttackStartAltitude;
	}

	/** See dummyPilotCasAttackStopAltitude's own doc. */
	public double tudursvehiclemod$getDummyPilotCasAttackStopAltitude() {
		return this.dummyPilotCasAttackStopAltitude;
	}

	/** See dummyPilotSearchRange's own doc. */
	public double tudursvehiclemod$getDummyPilotSearchRange() {
		return this.dummyPilotSearchRange;
	}

	/** See dummyPilotDiveTargetYOffset's own doc. */
	public double tudursvehiclemod$getDummyPilotDiveTargetYOffset() {
		return this.dummyPilotDiveTargetYOffset;
	}

	/** Sets this center's own dummy-pilot combat settings (weapon selection, attack altitudes, search range) - a separate setter from tudursvehiclemod$setDummyPilotConfig() (look/name settings), matching this project's own established convention of one focused setter per distinct group of concerns on this same block entity (compare tudursvehiclemod$setWaypoints()/tudursvehiclemod$setHomePoint(), also separate from each other despite both being route-related). Applies immediately to an already-spawned pilot (same as tudursvehiclemod$setDummyPilotConfig() already does for look/name) - entity.DummyPilotEntity's own combatWeaponIndex/attack-altitude/search-range fields are plain fields pushed via tudursvehiclemod$setCombatSettings(), not read fresh from this block each cycle, so a live change needs this same explicit re-push to actually take effect without a deactivate/reactivate cycle. */
	public void tudursvehiclemod$setDummyPilotCombatConfig(int weaponIndex, double attackStartAltitude, double attackStopAltitude, double searchRange, double diveTargetYOffset) {
		this.dummyPilotWeaponIndex = weaponIndex;
		this.dummyPilotCasAttackStartAltitude = attackStartAltitude;
		this.dummyPilotCasAttackStopAltitude = attackStopAltitude;
		this.dummyPilotSearchRange = searchRange;
		this.dummyPilotDiveTargetYOffset = diveTargetYOffset;
		this.markDirty();
		if (this.activeDummyPilot != null && this.activeDummyPilot.isAlive() && !this.activeDummyPilot.isRemoved()) {
			this.activeDummyPilot.tudursvehiclemod$setCombatSettings(weaponIndex, attackStartAltitude, attackStopAltitude, searchRange, diveTargetYOffset);
		}
	}

	/** Sets this center's own dummy pilot options, applying them immediately (spawning/removing/re-skinning/re-naming the pilot as needed) rather than only on the next activation. */
	public void tudursvehiclemod$setDummyPilotConfig(ServerWorld world, boolean enabled, String skinId, boolean nameVisible, String name) {
		this.dummyPilotEnabled = enabled;
		this.dummyPilotSkinId = com.example.tudursvehiclemod.asset.DummyPilotSkinRegistry.tudursvehiclemod$resolveOrDefault(skinId);
		this.dummyPilotNameVisible = nameVisible;
		// Per dummyPilotName's own doc: capped to a sane length here rather than trusting the network payload's own length unconditionally - a hostile/broken client sending an enormous string shouldn't get to persist one into this block's own NBT.
		String stripped = name == null ? "" : name.strip();
		this.dummyPilotName = stripped.substring(0, Math.min(stripped.length(), 64));
		this.markDirty();
		this.tudursvehiclemod$updateDummyPilot(world);
	}

	/** Brings the actual spawned pilot into line with this center's own current settings - spawning one when it should have one and doesn't, removing it when it shouldn't, and keeping its skin/name current. Called every tick from tick() (cheap: almost always just a couple of reference checks) so it also recovers automatically after a world reload, after the vehicle is re-found following a chunk unload, and after the pilot is lost for any reason.
	 *
	 * Whoever currently occupies seat 0 is forcibly dismounted first - the dummy pilot always wins that seat while enabled. */
	private void tudursvehiclemod$updateDummyPilot(ServerWorld world) {
		boolean shouldHavePilot = this.dummyPilotEnabled && this.active && !this.redstoneControlEnabled;
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle =
				tudursvehiclemod$findBoundVehicle(world, this.tudursvehiclemod$getBoundVehicleId());
		if (vehicle == null || vehicle.tudursvehiclemod$isDestroyed()) {
			shouldHavePilot = false;
		}

		if (!shouldHavePilot) {
			this.tudursvehiclemod$removeDummyPilot();
			// Per this method's own doc: activeDummyPilot alone can't be trusted to still reference every real pilot that might exist - directly checking the vehicle's own actual seat 0 occupant catches one this block entity has otherwise lost track of (e.g. after a reload), rather than leaving it stranded in the world forever.
			if (vehicle != null && vehicle.tudursvehiclemod$getSeatOccupant(0)
					instanceof com.example.tudursvehiclemod.entity.DummyPilotEntity strandedPilot) {
				strandedPilot.stopRiding();
				strandedPilot.discard();
			}
			return;
		}
		// Still alive and still aboard the right vehicle - just keep its skin/name/combat settings current (so a change takes effect without a deactivate/reactivate cycle) and leave it be.
		if (this.activeDummyPilot != null && this.activeDummyPilot.isAlive() && !this.activeDummyPilot.isRemoved()
				&& this.activeDummyPilot.getVehicle() == vehicle) {
			this.activeDummyPilot.tudursvehiclemod$setSkinId(this.tudursvehiclemod$getDummyPilotSkinId());
			this.activeDummyPilot.tudursvehiclemod$setNameSettings(this.dummyPilotNameVisible, this.dummyPilotName);
			this.activeDummyPilot.tudursvehiclemod$setCombatSettings(this.dummyPilotWeaponIndex,
					this.dummyPilotCasAttackStartAltitude, this.dummyPilotCasAttackStopAltitude, this.dummyPilotSearchRange, this.dummyPilotDiveTargetYOffset);
			return;
		}
		this.tudursvehiclemod$removeDummyPilot();

		// Per this method's own doc: the dummy pilot takes seat 0 unconditionally, evicting any current occupant.
		net.minecraft.entity.Entity currentOccupant = vehicle.tudursvehiclemod$getSeatOccupant(0);
		if (currentOccupant != null) {
			currentOccupant.stopRiding();
		}

		com.example.tudursvehiclemod.entity.DummyPilotEntity pilot =
				new com.example.tudursvehiclemod.entity.DummyPilotEntity(
						com.example.tudursvehiclemod.registry.ModEntityTypes.DUMMY_PILOT, world);
		pilot.refreshPositionAndAngles(vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYaw(), 0f);
		pilot.tudursvehiclemod$setSkinId(this.tudursvehiclemod$getDummyPilotSkinId());
		pilot.tudursvehiclemod$setNameSettings(this.dummyPilotNameVisible, this.dummyPilotName);
		pilot.tudursvehiclemod$setCombatSettings(this.dummyPilotWeaponIndex,
				this.dummyPilotCasAttackStartAltitude, this.dummyPilotCasAttackStopAltitude, this.dummyPilotSearchRange, this.dummyPilotDiveTargetYOffset);
		if (!world.spawnEntity(pilot)) {
			return;
		}
		if (!vehicle.tudursvehiclemod$mountToSeat(pilot, 0)) {
			// Couldn't actually take the seat (see that method's own doc for when it refuses) - don't leave an orphaned pilot standing in the world.
			pilot.discard();
			return;
		}
		this.activeDummyPilot = pilot;
	}

	/** Removes this center's own currently-spawned dummy pilot, if any. Safe to call repeatedly / when there is none. */
	private void tudursvehiclemod$removeDummyPilot() {
		if (this.activeDummyPilot != null) {
			this.activeDummyPilot.stopRiding();
			this.activeDummyPilot.discard();
			this.activeDummyPilot = null;
		}
	}

	/** Updates this center's own configured settings, and immediately re-applies them to the currently-linked vehicle (if any is currently loaded/found) so a change takes effect right away rather than only on the next activation. */
	public void tudursvehiclemod$setConfig(ServerWorld world, float speedFraction, double orbitAltitude, float radiusMultiplier) {
		this.speedFraction = speedFraction;
		this.orbitAltitude = orbitAltitude;
		this.radiusMultiplier = radiusMultiplier;
		this.markDirty();
		if (this.active && tudursvehiclemod$findBoundVehicle(world, this.tudursvehiclemod$getBoundVehicleId())
				instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
			vehicle.tudursvehiclemod$setDroneLink(this.getPos(), speedFraction, orbitAltitude, radiusMultiplier);
		}
	}

	public DroneCenterBlockEntity(BlockPos pos, net.minecraft.block.BlockState state) {
		super(com.example.tudursvehiclemod.registry.ModBlockEntities.DRONE_CENTER, pos, state);
	}

	/** Derived directly from whatever registered stick currently sits in slot 0 (see this class's own doc) - null if slot 0 is empty, holds an unregistered stick, or holds something else entirely. */
	public UUID tudursvehiclemod$getBoundVehicleId() {
		ItemStack stick = this.items.get(0);
		return stick.getItem() instanceof com.example.tudursvehiclemod.item.DroneControlStickItem
				? com.example.tudursvehiclemod.item.DroneControlStickItem.getRegisteredVehicleId(stick) : null;
	}

	/** Resolved the exact same way tudursvehiclemod$getBoundVehicleId() resolves the leader - see FormationSlotsInventory's own doc for the slot-index-to-formation-position convention. Null if slotIndex is out of range, that slot is empty, holds an unregistered stick, or holds something else entirely. */
	public UUID tudursvehiclemod$getFormationSlotVehicleId(int slotIndex) {
		if (slotIndex < 0 || slotIndex >= this.formationSlots.size()) {
			return null;
		}
		ItemStack stick = this.formationSlots.get(slotIndex);
		return stick.getItem() instanceof com.example.tudursvehiclemod.item.DroneControlStickItem
				? com.example.tudursvehiclemod.item.DroneControlStickItem.getRegisteredVehicleId(stick) : null;
	}

	public int tudursvehiclemod$getFormationSize() {
		return this.formationSize;
	}

	public com.example.tudursvehiclemod.asset.FormationType tudursvehiclemod$getFormationType() {
		return this.formationType;
	}

	public double tudursvehiclemod$getFormationSpacing() {
		return this.formationSpacing;
	}

	public double tudursvehiclemod$getFormationElementSpacing() {
		return this.formationElementSpacing;
	}

	/** Applies every formation setting from the config screen at once (see client.screen.DroneCenterConfigScreen's own formation controls). Resizes formationSlots to match the new formationSize immediately (max(0, formationSize - 1) wingman slots - the leader itself, slot 0 of the MAIN inventory, is never part of this list at all) - existing stick stacks are preserved when growing, silently dropped beyond the new size when shrinking (same convention AbstractVehicleEntity's own cargo-inventory resize already uses). */
	public void tudursvehiclemod$setFormationConfig(int formationSize, com.example.tudursvehiclemod.asset.FormationType formationType,
			double formationSpacing, double formationElementSpacing) {
		this.formationSize = Math.max(1, formationSize);
		this.formationType = formationType;
		this.formationSpacing = Math.max(0.5, formationSpacing);
		this.formationElementSpacing = formationElementSpacing;
		int newSlotCount = Math.max(0, this.formationSize - 1);
		if (newSlotCount != this.formationSlots.size()) {
			DefaultedList<ItemStack> resized = DefaultedList.ofSize(newSlotCount, ItemStack.EMPTY);
			for (int i = 0; i < Math.min(newSlotCount, this.formationSlots.size()); i++) {
				resized.set(i, this.formationSlots.get(i));
			}
			this.formationSlots = resized;
		}
		this.markDirty();
	}

	/** Encodes this center's own current formation settings as "formationSize,formationType,formationSpacing,formationElementSpacing" - see network.DroneCenterConfigOpenPayload's own formationConfig doc for why this compact single-string format exists at all (avoiding PacketCodec.tuple's own 12-field arity limit). */
	public String tudursvehiclemod$encodeFormationConfig() {
		return this.formationSize + "," + this.formationType.name() + "," + this.formationSpacing + "," + this.formationElementSpacing;
	}

	/** Decodes the format tudursvehiclemod$encodeFormationConfig() produces and applies it via tudursvehiclemod$setFormationConfig() (see that method's own doc for the resize behavior this triggers). Malformed input is silently ignored entirely (no partial application) - defensive against a mismatched client/server version sending an unexpected format. */
	public void tudursvehiclemod$decodeAndApplyFormationConfig(String encoded) {
		String[] parts = encoded.split(",", -1);
		if (parts.length != 4) {
			return;
		}
		try {
			int decodedSize = Integer.parseInt(parts[0]);
			com.example.tudursvehiclemod.asset.FormationType decodedType = com.example.tudursvehiclemod.asset.FormationType.valueOf(parts[1]);
			double decodedSpacing = Double.parseDouble(parts[2]);
			double decodedElementSpacing = Double.parseDouble(parts[3]);
			this.tudursvehiclemod$setFormationConfig(decodedSize, decodedType, decodedSpacing, decodedElementSpacing);
		} catch (IllegalArgumentException ignored) {
		}
	}



	private final FormationSlotsInventory formationSlotsInventory = new FormationSlotsInventory();

	/** The standalone Inventory wrapper for formationSlots - see FormationSlotsInventory's own doc for why this is separate from this whole class's own Inventory implementation. */
	public FormationSlotsInventory tudursvehiclemod$getFormationSlotsInventory() {
		return this.formationSlotsInventory;
	}

	public int tudursvehiclemod$getFormationSlotPage() {
		return this.formationSlotPage;
	}

	public void tudursvehiclemod$setFormationSlotPage(int page) {
		this.formationSlotPage = page;
	}

	/** The wingman vehicles this center is CURRENTLY formation-following (as of last tick) - tracked purely so a vehicle that's no longer assigned to any slot (stick removed, slot swapped to a different vehicle, formationSize shrunk past it) can be explicitly released (tudursvehiclemod$clearDroneFormationFollow()) rather than continuing to chase a stale offset forever. Not persisted - re-established fresh from formationSlots the moment this block entity next ticks after a reload, same "in-memory-only" convention several other pieces of transient Drone Center state already use. */
	private final java.util.Set<UUID> currentlyFollowingWingmen = new java.util.HashSet<>();

	/** Called every tick this center is active with a found leader (see tick()'s own doc) - resolves each formation slot's own registered vehicle (tudursvehiclemod$getFormationSlotVehicleId()) and assigns it a rigid formation-follow offset via AircraftEntity's own tudursvehiclemod$setDroneFormationFollow(), using the exact same tudursvehiclemod$computeFormationOffsets() math CAS/Carrier's own formation feature already established. Slot index directly determines formation position (formation index = slot index + 1, since index 0 of the overall formation is always the leader itself) - an empty slot simply means no wingman occupies that specific position, never compacted/shifted to fill gaps. Only AircraftEntity vehicles are actually followed (formation-follow's own flight model, tudursvehiclemod$updateDroneFormationFollow(), is AircraftEntity-specific) - a stick registered to some other vehicle type is silently ignored. */
	private void tudursvehiclemod$updateFormationWingmen(ServerWorld serverWorld, com.example.tudursvehiclemod.entity.AbstractVehicleEntity leader) {
		java.util.Set<UUID> stillFollowing = new java.util.HashSet<>();
		if (this.formationSize > 1 && !this.formationSlots.isEmpty()) {
			java.util.List<double[]> offsets = com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$computeFormationOffsets(
					this.formationSize, this.formationType, this.formationSpacing, this.formationElementSpacing);
			for (int slotIndex = 0; slotIndex < this.formationSlots.size(); slotIndex++) {
				UUID wingmanId = this.tudursvehiclemod$getFormationSlotVehicleId(slotIndex);
				if (wingmanId == null) {
					continue;
				}
				int formationIndex = slotIndex + 1;
				if (formationIndex >= offsets.size()) {
					continue;
				}
				if (serverWorld.getEntity(wingmanId) instanceof com.example.tudursvehiclemod.entity.AircraftEntity wingman && !wingman.isRemoved()) {
					double[] offset = offsets.get(formationIndex);
					wingman.tudursvehiclemod$setDroneFormationFollow(leader.getUuid(), offset[0], offset[1]);
					stillFollowing.add(wingmanId);
				}
			}
		}
		for (UUID previousId : this.currentlyFollowingWingmen) {
			if (!stillFollowing.contains(previousId) && serverWorld.getEntity(previousId) instanceof com.example.tudursvehiclemod.entity.AircraftEntity previousWingman) {
				previousWingman.tudursvehiclemod$clearDroneFormationFollow();
			}
		}
		this.currentlyFollowingWingmen.clear();
		this.currentlyFollowingWingmen.addAll(stillFollowing);
	}

	/** Externally-visible active state (e.g. what the config screen's own toggle button shows) - reflects the user's own last request immediately (shows "off" the instant they ask, even if the vehicle itself is still landing internally) rather than the internal active field, which deliberately lags behind until landing actually finishes (see that field's own doc). */
	public boolean tudursvehiclemod$isActive() {
		return this.active && !this.pendingDeactivation;
	}

	/** See redstoneControlEnabled's own doc. */
	public boolean tudursvehiclemod$isRedstoneControlEnabled() {
		return this.redstoneControlEnabled;
	}

	/** Switches between manual (existing toggleActive() button) and redstone-driven control. Per this whole feature's own doc: entering redstone mode immediately syncs active to the block's own CURRENT redstone power state (rather than waiting for the next power change, which might not come for a long time if the wire was already in its "new" state before this was turned on) - reuses tudursvehiclemod$onRedstonePowerChanged() itself for that, so the two code paths can never disagree about what "powered" should mean. Leaving redstone mode does NOT change active on its own - whatever state redstone last left it in simply becomes the new manual starting point, exactly as if the player had set it that way with the toggle button themselves. */
	public void tudursvehiclemod$setRedstoneControlEnabled(ServerWorld world, boolean enabled) {
		this.redstoneControlEnabled = enabled;
		this.markDirty();
		if (enabled) {
			this.tudursvehiclemod$onRedstonePowerChanged(world, world.isReceivingRedstonePower(this.getPos()));
		}
	}

	/** Called from DroneCenterBlock's own neighborUpdate()/onBlockAdded() whenever this block's own received redstone power might have changed. A no-op entirely unless redstoneControlEnabled is currently true (see that field's own doc - the manual toggle button remains the only thing that can change active otherwise). Drives active DIRECTLY to match `powered` (via the same tudursvehiclemod$toggleActive() the manual button itself uses, so landing-in-progress/dummy-pilot/chunk-forcing all stay identical between the two control modes) - but only if it doesn't already match, since toggleActive() is a TOGGLE (calling it when already in the desired state would flip it the WRONG way). */
	public void tudursvehiclemod$onRedstonePowerChanged(ServerWorld world, boolean powered) {
		if (!this.redstoneControlEnabled) {
			return;
		}
		if (this.tudursvehiclemod$isActive() != powered) {
			this.tudursvehiclemod$toggleActive(world);
		}
	}

	/** Toggles active on/off for this center's own bound vehicle. Immediately links/unlinks the vehicle to this center's own position if it's currently loaded/found; otherwise the link is applied the next time tick() finds it (same on-demand chunk-loading approach as StationBlockEntity's own pending-control-attempt system, just simpler here since there's no player waiting on an immediate result). */
	public boolean tudursvehiclemod$toggleActive(ServerWorld world) {
		if (this.tudursvehiclemod$getBoundVehicleId() == null) {
			return false;
		}
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle =
				tudursvehiclemod$findBoundVehicle(world, this.tudursvehiclemod$getBoundVehicleId());
		if (this.tudursvehiclemod$isActive()) {
			// Deactivating.
			if (vehicle instanceof com.example.tudursvehiclemod.entity.AircraftEntity aircraft && !aircraft.isOnGround()) {
				// Still airborne - lands gently near this center instead of simply losing control. active itself deliberately stays true (tick() keeps managing/chunk-forcing normally) until the vehicle reports landing complete; tudursvehiclemod$isActive() already reflects "off" to the user right away regardless.
				this.pendingDeactivation = true;
				this.markDirty();
				aircraft.tudursvehiclemod$requestDroneLanding(this.getPos(), this.tudursvehiclemod$getHomePoint(), this.tudursvehiclemod$getReturnViaPoint());
				return false;
			}
			// Already grounded (or vehicle not found/not an aircraft) - nothing to land, deactivates immediately same as before this feature existed.
			this.active = false;
			this.pendingDeactivation = false;
			this.markDirty();
			this.tudursvehiclemod$releaseCurrentlyForcedChunk(world);
			if (vehicle != null) {
				vehicle.tudursvehiclemod$setDroneLink(null, this.speedFraction, this.orbitAltitude, this.radiusMultiplier);
			}
			return false;
		}
		// Activating.
		this.active = true;
		this.pendingDeactivation = false;
		this.markDirty();
		if (vehicle != null && !vehicle.tudursvehiclemod$isDestroyed()) {
			this.tudursvehiclemod$rememberVehicleChunk(world, vehicle);
			vehicle.tudursvehiclemod$setDroneLink(this.getPos(), this.speedFraction, this.orbitAltitude, this.radiusMultiplier);
			// Per tudursvehiclemod$resetGroundWaypointProgress()'s own doc: a surface vehicle starts its own ground route from the first waypoint each time it's activated, rather than resuming from wherever a previous run happened to stop. A no-op for an aircraft (which never advances that state at all).
			vehicle.tudursvehiclemod$resetGroundWaypointProgress();
		}
		return true;
	}

	/** Checked once per tick from tick() while this center is active (and NOT under redstone control - see this whole feature's own doc on AutoDisableResumeMode for why). Two independent trigger conditions, either one sufficient on its own: (1) fuel below 10% of this vehicle's own current max (AbstractVehicleEntity's own getFuel()/getMaxFuel() - the SAME max a drop-tank-equipped vehicle's own bonus capacity already contributes to, so a vehicle that's genuinely still well-fueled relative to its own actual current capacity isn't penalized for a drop tank's own remaining ammo happening to be lower than some fixed absolute number would be), and (2) - only when dummyPilotEnabled AND dummyPilotWeaponIndex is actually a valid selection (see that field's own doc; an unselected/invalid index has no weapon to run out of ammo on, so this half of the check simply never applies) - that weapon's own current magazine (AbstractVehicleEntity#getWeaponAmmoState()'s own first value) AND reserve ammo (AbstractVehicleEntity#tudursvehiclemod$getWeaponReserveAmmo() - a direct report confirmed getWeaponAmmoState()'s own SECOND value is reload ticks remaining, not reserve ammo at all, which had no public accessor whatsoever before that report) both being at or below zero, i.e. no rounds left anywhere at all, not merely "empty until the next reload" (magazine == -1 - a weapon with no ammo tracking/concept of running out at all - never triggers this half either). Triggering either condition calls tudursvehiclemod$toggleActive() (this center is confirmed active whenever this method itself is even called, so that call is guaranteed to deactivate, landing gently first if still airborne - exactly the same mechanism a manual deactivation already uses) and marks autoDisabledPendingResupply, for AutoDisableResumeMode.AUTO_RESUME's own later use. Returns whether a disable was actually triggered this call, so tick() can skip its own normal drone-link re-apply for the same tick a disable just fired. */
	private boolean tudursvehiclemod$updateAutoDisable(ServerWorld world, com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
		// A deactivation/landing already in progress must never be re-triggered - toggleActive()'s own airborne-deactivation branch deliberately keeps active=true for the ENTIRE landing sequence (see that method's own doc), so without this guard, this whole check would re-fire every single tick for as long as the underlying low-fuel/out-of-ammo condition remained true (which it typically does throughout the whole landing, since nothing resupplies mid-flight), each time re-invoking tudursvehiclemod$requestDroneLanding() and resetting the landing route's own navigation progress right back to its start - preventing the aircraft from ever actually completing (or even properly progressing through) its own return route.
		if (this.pendingDeactivation) {
			return false;
		}
		boolean lowFuel = !vehicle.tudursvehiclemod$isFuelless()
				&& vehicle.getMaxFuel() > 0f && vehicle.getFuel() / vehicle.getMaxFuel() < 0.10f;
		boolean outOfAmmo = false;
		if (this.dummyPilotEnabled && this.dummyPilotWeaponIndex >= 0
				&& this.dummyPilotWeaponIndex < vehicle.getDefinition().weapons().size()) {
			int magazineAmmo = vehicle.getWeaponAmmoState(this.dummyPilotWeaponIndex)[0];
			int reserveAmmo = vehicle.tudursvehiclemod$getWeaponReserveAmmo(this.dummyPilotWeaponIndex);
			outOfAmmo = magazineAmmo != -1 && magazineAmmo <= 0 && reserveAmmo <= 0;
		}
		if (!lowFuel && !outOfAmmo) {
			return false;
		}
		this.autoDisabledPendingResupply = true;
		this.markDirty();
		this.tudursvehiclemod$toggleActive(world);
		return true;
	}

	/** Per AutoDisableResumeMode.AUTO_RESUME's own doc: checked once per tick from tick() while this center is inactive with autoDisabledPendingResupply still set and AUTO_RESUME actually selected (redstone-controlled centers never reach this at all - see this whole feature's own doc). resupply is now confirmed only once the vehicle is genuinely FULLY topped off, not merely above whatever threshold originally triggered the disable - fuel at its own current max, AND (whichever ammo condition actually applied - if dummy-pilot combat wasn't even part of why this triggered, only fuel is checked here) the selected weapon's own ammo at full capacity: for a weapon with a separate reserve pool, magazine+reserve COMBINED at WeaponDefinition#maxAmmo() (that value is the total across both pools together, per tudursvehiclemod$tryResupplyAmmo()'s own reserveCap computation - NOT the reserve's own separate maximum, a mistake an earlier version of this check made, which could never actually succeed once the magazine was also full); for a weapon with no separate reserve (getWeaponReserveAmmo() < 0), simply the magazine at its own full magazineSize(). Does nothing at all (silently) if the vehicle can no longer be found (unloaded/destroyed/unbound since the disable) - resuming has nothing to resume onto in that case; the flag simply stays set for a later tick once the vehicle becomes findable again, exactly like tick()'s own existing on-demand vehicle-finding elsewhere on this same block. */
	private void tudursvehiclemod$updateAutoResume(ServerWorld world) {
		if (this.autoDisableResumeMode != AutoDisableResumeMode.AUTO_RESUME) {
			return;
		}
		UUID boundVehicleId = this.tudursvehiclemod$getBoundVehicleId();
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle =
				boundVehicleId == null ? null : tudursvehiclemod$findBoundVehicle(world, boundVehicleId);
		if (vehicle == null || vehicle.tudursvehiclemod$isDestroyed()) {
			return;
		}
		boolean fuelOk = vehicle.tudursvehiclemod$isFuelless()
				|| vehicle.getMaxFuel() <= 0f || vehicle.getFuel() >= vehicle.getMaxFuel();
		boolean ammoOk = true;
		if (this.dummyPilotEnabled && this.dummyPilotWeaponIndex >= 0
				&& this.dummyPilotWeaponIndex < vehicle.getDefinition().weapons().size()) {
			com.example.tudursvehiclemod.asset.WeaponDefinition weapon = vehicle.getDefinition().weapons().get(this.dummyPilotWeaponIndex);
			int magazineAmmo = vehicle.getWeaponAmmoState(this.dummyPilotWeaponIndex)[0];
			int reserveAmmo = vehicle.tudursvehiclemod$getWeaponReserveAmmo(this.dummyPilotWeaponIndex);
			// maxAmmo() is the COMBINED total across magazine+reserve together (matching tudursvehiclemod$tryResupplyAmmo()'s own atCapacity check - reserveCap there is explicitly maxAmmo() MINUS the current magazine ammo), not the reserve pool's own independent maximum. Comparing reserveAmmo alone against maxAmmo() could never succeed once the magazine was also full (the reserve's own real cap is maxAmmo() - magazineSize()), meaning this whole check could never actually pass no matter how much the weapon was resupplied.
			ammoOk = magazineAmmo == -1 || reserveAmmo < 0
					? magazineAmmo == -1 || magazineAmmo >= weapon.magazineSize()
					: magazineAmmo + reserveAmmo >= weapon.maxAmmo();
		}
		if (!fuelOk || !ammoOk) {
			return;
		}
		this.autoDisabledPendingResupply = false;
		this.markDirty();
		this.tudursvehiclemod$toggleActive(world);
	}

	private void tudursvehiclemod$releaseCurrentlyForcedChunk(ServerWorld world) {
		for (ChunkPos chunk : this.currentlyForcedChunks) {
			world.setChunkForced(chunk.x, chunk.z, false);
		}
		this.currentlyForcedChunks = java.util.Set.of();
	}

	/** The 3x3 grid of chunks centered on centerChunk - same helper shape as entity.AircraftEntity's own tudursvehiclemod$computeChunkGrid(). */
	private static java.util.Set<ChunkPos> tudursvehiclemod$computeChunkGrid(ChunkPos centerChunk) {
		java.util.Set<ChunkPos> grid = new java.util.HashSet<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				grid.add(new ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
			}
		}
		return grid;
	}

	private void tudursvehiclemod$rememberVehicleChunk(ServerWorld world, com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
		ChunkPos current = new ChunkPos(vehicle.getBlockPos());
		if (!current.equals(this.lastKnownVehicleChunk)) {
			this.lastKnownVehicleChunk = current;
			this.markDirty();
		}
		java.util.Set<ChunkPos> newGrid = tudursvehiclemod$computeChunkGrid(current);
		if (!newGrid.equals(this.currentlyForcedChunks)) {
			for (ChunkPos chunk : this.currentlyForcedChunks) {
				if (!newGrid.contains(chunk)) {
					world.setChunkForced(chunk.x, chunk.z, false);
				}
			}
			for (ChunkPos chunk : newGrid) {
				if (!this.currentlyForcedChunks.contains(chunk)) {
					// setChunkForced() alone (without this getChunk() call first) left a real gap for a genuinely NEW, never-before-loaded chunk crossed during flight.
					world.getChunk(chunk.x, chunk.z);
					world.setChunkForced(chunk.x, chunk.z, true);
				}
			}
			this.currentlyForcedChunks = newGrid;
		}
	}

	/** Force-loads the bound vehicle's own last-known chunk for a few ticks so an active-but-not-currently-loaded vehicle actually gets a chance to be found and linked - same reasoning as StationBlockEntity's own pending-control-attempt chunk loading, simplified (no player waiting on the outcome here). */
	private int forceLoadTicksRemaining;
	private static final int FORCE_LOAD_WINDOW_TICKS = 100;

	public static void tick(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.BlockState state, DroneCenterBlockEntity blockEntity) {
		UUID boundVehicleId = blockEntity.tudursvehiclemod$getBoundVehicleId();
		if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
			return;
		}
		// Per tudursvehiclemod$updateDummyPilot()'s own doc: deliberately ahead of the active/bound early return below, so a pilot spawned while active is actually REMOVED again once this center goes inactive or loses its binding - that cleanup can't happen from inside a branch that only runs while still active.
		blockEntity.tudursvehiclemod$updateDummyPilot(serverWorld);
		// Redstone-driven control never auto-disables at all - checked first here so it applies before the general active/bound early return below reaches a center that's currently inactive purely because of an EARLIER auto-disable (this check has nothing to do with THAT case at all, only with an ACTIVE, redstone-driven center that would otherwise be subject to auto-disable like any manually-controlled one).
		if (!blockEntity.active && blockEntity.autoDisabledPendingResupply && !blockEntity.redstoneControlEnabled) {
			blockEntity.tudursvehiclemod$updateAutoResume(serverWorld);
		}
		if (!blockEntity.active || boundVehicleId == null) {
			return;
		}
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle =
				tudursvehiclemod$findBoundVehicle(serverWorld, boundVehicleId);
		if (vehicle != null && vehicle.tudursvehiclemod$isDestroyed()) {
			// A destroyed vehicle's own onDestroyed() already released its side of the link directly - this also deactivates the center itself, so it stops trying to re-link the same (still-existing-for-a-while, but no longer flyable) vehicle every subsequent tick.
			blockEntity.active = false;
			blockEntity.pendingDeactivation = false;
			blockEntity.markDirty();
			blockEntity.tudursvehiclemod$releaseCurrentlyForcedChunk(serverWorld);
			return;
		}
		// While waiting on that, checks whether the vehicle's own landing sequence has actually finished (it clears its own state and releases the link itself upon touchdown) - checked BEFORE the normal "re-apply every tick" logic below, so a just-landed vehicle doesn't get its drone link immediately re-enabled again.
		if (blockEntity.pendingDeactivation && vehicle instanceof com.example.tudursvehiclemod.entity.AircraftEntity aircraft
				&& !aircraft.tudursvehiclemod$isDroneLandingInProgress()) {
			blockEntity.active = false;
			blockEntity.pendingDeactivation = false;
			blockEntity.markDirty();
			blockEntity.tudursvehiclemod$releaseCurrentlyForcedChunk(serverWorld);
			return;
		}
		if (vehicle != null) {
			// Checked before the normal drone-link re-apply below, so a just-auto-disabled vehicle doesn't get its link immediately re-established again this same tick. Never applies under redstone control - see this whole feature's own doc on AutoDisableResumeMode.
			if (!blockEntity.redstoneControlEnabled && blockEntity.tudursvehiclemod$updateAutoDisable(serverWorld, vehicle)) {
				return;
			}
			blockEntity.tudursvehiclemod$rememberVehicleChunk(serverWorld, vehicle);
			if (blockEntity.forceLoadTicksRemaining > 0) {
				serverWorld.setChunkForced(blockEntity.lastKnownVehicleChunk.x, blockEntity.lastKnownVehicleChunk.z, false);
				blockEntity.forceLoadTicksRemaining = 0;
			}
			// Re-applied every tick while active (not just once on toggleActive()) so a vehicle that only just now loaded back in (e.g. after being unloaded while still flagged active) still picks the link back up on its own.
			vehicle.tudursvehiclemod$setDroneLink(pos, blockEntity.speedFraction, blockEntity.orbitAltitude, blockEntity.radiusMultiplier);
			blockEntity.tudursvehiclemod$updateFormationWingmen(serverWorld, vehicle);
			return;
		}
		if (blockEntity.lastKnownVehicleChunk == null) {
			return;
		}
		if (blockEntity.forceLoadTicksRemaining <= 0) {
			serverWorld.setChunkForced(blockEntity.lastKnownVehicleChunk.x, blockEntity.lastKnownVehicleChunk.z, true);
			blockEntity.forceLoadTicksRemaining = FORCE_LOAD_WINDOW_TICKS;
		} else {
			blockEntity.forceLoadTicksRemaining--;
			if (blockEntity.forceLoadTicksRemaining == 0) {
				serverWorld.setChunkForced(blockEntity.lastKnownVehicleChunk.x, blockEntity.lastKnownVehicleChunk.z, false);
			}
		}
	}

	public static com.example.tudursvehiclemod.entity.AbstractVehicleEntity tudursvehiclemod$findBoundVehicle(ServerWorld world, UUID vehicleId) {
		if (vehicleId == null) {
			return null;
		}
		Entity entity = world.getEntity(vehicleId);
		return entity instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle ? vehicle : null;
	}

	/** Called when this block is removed - releases any still-forced chunk, since tick() won't run again to do it. */
	public void tudursvehiclemod$releaseForcedChunk(ServerWorld world) {
		if (this.forceLoadTicksRemaining > 0 && this.lastKnownVehicleChunk != null) {
			world.setChunkForced(this.lastKnownVehicleChunk.x, this.lastKnownVehicleChunk.z, false);
		}
		this.forceLoadTicksRemaining = 0;
		this.tudursvehiclemod$releaseCurrentlyForcedChunk(world);
	}

	/** This center's own chunk stays loaded permanently for as long as the block entity exists, same as StationBlockEntity's own equivalent. */
	public void tudursvehiclemod$updateChunkForceLoading(boolean forced) {
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			ChunkPos ownChunk = new ChunkPos(this.getPos());
			serverWorld.setChunkForced(ownChunk.x, ownChunk.z, forced);
		}
	}

	// --- NamedScreenHandlerFactory / Inventory (see this class's own doc) ---

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.tudursvehiclemod.drone_center");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new com.example.tudursvehiclemod.screen.DroneCenterScreenHandler(syncId, playerInventory, this, this.getPos());
	}

	@Override
	public int size() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		return this.items.get(0).isEmpty() && this.items.get(1).isEmpty();
	}

	@Override
	public ItemStack getStack(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack result = net.minecraft.inventory.Inventories.splitStack(this.items, slot, amount);
		if (!result.isEmpty()) {
			this.markDirty();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return net.minecraft.inventory.Inventories.removeStack(this.items, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		// Captures the bound vehicle's own position at the exact moment a NEW (different) valid stick is inserted into slot 0 - checked BEFORE actually storing the new stack, since tudursvehiclemod$getBoundVehicleId() needs to compare against whatever was there previously.
		if (slot == 0) {
			UUID previousBoundId = this.tudursvehiclemod$getBoundVehicleId();
			this.items.set(slot, stack);
			UUID newBoundId = this.tudursvehiclemod$getBoundVehicleId();
			if (newBoundId != null && !newBoundId.equals(previousBoundId) && this.getWorld() instanceof ServerWorld serverWorld
					&& tudursvehiclemod$findBoundVehicle(serverWorld, newBoundId) instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
				this.homePoint = new DroneWaypoint(
						(int) Math.floor(vehicle.getX()) - this.getPos().getX(),
						(int) Math.floor(vehicle.getY()) - this.getPos().getY(),
						(int) Math.floor(vehicle.getZ()) - this.getPos().getZ(),
						0.5f, 0f, 1.0f, 1.0f);
			}
		} else if (slot == 1) {
			// Before replacing whatever's in this slot, if it was a valid route book, copies its own current waypoints into this center's own internal list first - so removing/swapping the book that had been the active source of truth doesn't silently lose whatever was actually edited into it.
			ItemStack previousBook = this.items.get(1);
			if (previousBook.getItem() instanceof com.example.tudursvehiclemod.item.DroneRouteBookItem) {
				this.waypoints.clear();
				for (DroneRouteBookWaypoint wp : com.example.tudursvehiclemod.item.DroneRouteBookItem.tudursvehiclemod$getWaypoints(previousBook)) {
					this.waypoints.add(new DroneWaypoint(wp.x() - this.getPos().getX(), wp.y() - this.getPos().getY(),
							wp.z() - this.getPos().getZ(), wp.speedFraction(), wp.rollAngle(),
							wp.rollManeuverabilityMultiplier(), wp.turnManeuverabilityMultiplier()));
				}
			}
			this.items.set(slot, stack);
		} else {
			this.items.set(slot, stack);
		}
		if (stack.getCount() > this.getMaxCountPerStack()) {
			stack.setCount(this.getMaxCountPerStack());
		}
		this.markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return this.getWorld() != null && this.getWorld().getBlockEntity(this.getPos()) == this
				&& player.squaredDistanceTo(this.getPos().getX() + 0.5, this.getPos().getY() + 0.5, this.getPos().getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clear() {
		this.items.clear();
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		Inventories.readData(view, this.items);
		int lastChunkX = view.getInt("LastKnownVehicleChunkX", Integer.MIN_VALUE);
		int lastChunkZ = view.getInt("LastKnownVehicleChunkZ", Integer.MIN_VALUE);
		this.lastKnownVehicleChunk = lastChunkX == Integer.MIN_VALUE ? null : new ChunkPos(lastChunkX, lastChunkZ);
		this.active = view.getBoolean("Active", false);
		// Per redstoneControlEnabled's own doc: absent in any save predating this feature, so the default here preserves existing (manual-only) behavior exactly.
		this.redstoneControlEnabled = view.getBoolean("RedstoneControlEnabled", false);
		this.speedFraction = view.getFloat("SpeedFraction", 0.5f);
		this.orbitAltitude = view.getFloat("OrbitAltitude", 15.0f);
		this.radiusMultiplier = view.getFloat("RadiusMultiplier", 1.0f);
		// Per dummyPilotEnabled's own doc: absent in any save predating this feature, so the defaults here preserve existing behavior exactly.
		this.dummyPilotEnabled = view.getBoolean("DummyPilotEnabled", false);
		this.dummyPilotSkinId = view.getString("DummyPilotSkinId",
				com.example.tudursvehiclemod.asset.DummyPilotSkinRegistry.DEFAULT_SKIN_ID);
		this.dummyPilotNameVisible = view.getBoolean("DummyPilotNameVisible", false);
		this.dummyPilotName = view.getString("DummyPilotName", "");
		// Per dummyPilotWeaponIndex's own doc: -1 (not selected) for any save predating this feature.
		this.dummyPilotWeaponIndex = view.getInt("DummyPilotWeaponIndex", -1);
		// Per AutoDisableResumeMode's own doc: MANUAL_REENABLE (ordinal 0) for any save predating this feature.
		int resumeModeOrdinal = view.getInt("AutoDisableResumeMode", AutoDisableResumeMode.MANUAL_REENABLE.ordinal());
		AutoDisableResumeMode[] resumeModes = AutoDisableResumeMode.values();
		this.autoDisableResumeMode = resumeModeOrdinal >= 0 && resumeModeOrdinal < resumeModes.length
				? resumeModes[resumeModeOrdinal] : AutoDisableResumeMode.MANUAL_REENABLE;
		this.autoDisabledPendingResupply = view.getBoolean("AutoDisabledPendingResupply", false);
		this.dummyPilotCasAttackStartAltitude = view.getFloat("DummyPilotCasAttackStartAltitude", 200.0f);
		this.dummyPilotCasAttackStopAltitude = view.getFloat("DummyPilotCasAttackStopAltitude", 40.0f);
		this.dummyPilotSearchRange = view.getFloat("DummyPilotSearchRange", 64.0f);
		this.dummyPilotDiveTargetYOffset = view.getFloat("DummyPilotDiveTargetYOffset", 0.0f);
		this.waypoints.clear();
		String waypointsEncoded = view.getString("Waypoints", "");
		if (!waypointsEncoded.isEmpty()) {
			for (String part : waypointsEncoded.split(";")) {
				DroneWaypoint decoded = DroneWaypoint.tudursvehiclemod$decode(part);
				if (decoded != null) {
					this.waypoints.add(decoded);
				}
			}
		}
		// Per groundWaypoints' own doc: persisted under its own separate key, entirely independent of the aircraft route above - an older save with no such key simply loads an empty ground route, exactly as if none had been authored yet.
		this.groundWaypoints.clear();
		String groundWaypointsEncoded = view.getString("GroundWaypoints", "");
		if (!groundWaypointsEncoded.isEmpty()) {
			for (String part : groundWaypointsEncoded.split(";")) {
				GroundWaypoint decoded = GroundWaypoint.tudursvehiclemod$decode(part);
				if (decoded != null) {
					this.groundWaypoints.add(decoded);
				}
			}
		}
		String homePointEncoded = view.getString("HomePoint", "");
		this.homePoint = homePointEncoded.isEmpty() ? null : DroneWaypoint.tudursvehiclemod$decode(homePointEncoded);
		String returnViaPointEncoded = view.getString("ReturnViaPoint", "");
		this.returnViaPoint = returnViaPointEncoded.isEmpty() ? null : DroneWaypoint.tudursvehiclemod$decode(returnViaPointEncoded);
		// Per formationSize's own doc: absent in any save predating this feature, so the defaults here preserve existing (no-formation) behavior exactly.
		this.formationSize = Math.max(1, view.getInt("FormationSize", 1));
		String formationTypeName = view.getString("FormationType", com.example.tudursvehiclemod.asset.FormationType.LINE_ABREAST.name());
		com.example.tudursvehiclemod.asset.FormationType parsedFormationType;
		try {
			parsedFormationType = com.example.tudursvehiclemod.asset.FormationType.valueOf(formationTypeName);
		} catch (IllegalArgumentException e) {
			parsedFormationType = com.example.tudursvehiclemod.asset.FormationType.LINE_ABREAST;
		}
		this.formationType = parsedFormationType;
		this.formationSpacing = view.getDouble("FormationSpacing", 8.0);
		this.formationElementSpacing = view.getDouble("FormationElementSpacing", -1.0);
		this.formationSlots = DefaultedList.ofSize(Math.max(0, this.formationSize - 1), ItemStack.EMPTY);
		view.getOptionalReadView("FormationSlots").ifPresent(slotsView -> Inventories.readData(slotsView, this.formationSlots));
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		Inventories.writeData(view, this.items, true);
		if (this.lastKnownVehicleChunk != null) {
			view.putInt("LastKnownVehicleChunkX", this.lastKnownVehicleChunk.x);
			view.putInt("LastKnownVehicleChunkZ", this.lastKnownVehicleChunk.z);
		}
		view.putBoolean("Active", this.active);
		view.putBoolean("RedstoneControlEnabled", this.redstoneControlEnabled);
		view.putFloat("SpeedFraction", this.speedFraction);
		view.putFloat("OrbitAltitude", (float) this.orbitAltitude);
		view.putFloat("RadiusMultiplier", this.radiusMultiplier);
		view.putBoolean("DummyPilotEnabled", this.dummyPilotEnabled);
		view.putString("DummyPilotSkinId", this.dummyPilotSkinId);
		view.putBoolean("DummyPilotNameVisible", this.dummyPilotNameVisible);
		view.putString("DummyPilotName", this.dummyPilotName);
		view.putInt("DummyPilotWeaponIndex", this.dummyPilotWeaponIndex);
		view.putInt("AutoDisableResumeMode", this.autoDisableResumeMode.ordinal());
		view.putBoolean("AutoDisabledPendingResupply", this.autoDisabledPendingResupply);
		view.putFloat("DummyPilotCasAttackStartAltitude", (float) this.dummyPilotCasAttackStartAltitude);
		view.putFloat("DummyPilotCasAttackStopAltitude", (float) this.dummyPilotCasAttackStopAltitude);
		view.putFloat("DummyPilotSearchRange", (float) this.dummyPilotSearchRange);
		view.putFloat("DummyPilotDiveTargetYOffset", (float) this.dummyPilotDiveTargetYOffset);
		if (!this.waypoints.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < this.waypoints.size(); i++) {
				if (i > 0) {
					sb.append(';');
				}
				sb.append(this.waypoints.get(i).tudursvehiclemod$encode());
			}
			view.putString("Waypoints", sb.toString());
		}
		if (!this.groundWaypoints.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < this.groundWaypoints.size(); i++) {
				if (i > 0) {
					sb.append(';');
				}
				sb.append(this.groundWaypoints.get(i).tudursvehiclemod$encode());
			}
			view.putString("GroundWaypoints", sb.toString());
		}
		if (this.homePoint != null) {
			view.putString("HomePoint", this.homePoint.tudursvehiclemod$encode());
		}
		if (this.returnViaPoint != null) {
			view.putString("ReturnViaPoint", this.returnViaPoint.tudursvehiclemod$encode());
		}
		view.putInt("FormationSize", this.formationSize);
		view.putString("FormationType", this.formationType.name());
		view.putDouble("FormationSpacing", this.formationSpacing);
		view.putDouble("FormationElementSpacing", this.formationElementSpacing);
		if (!this.formationSlots.isEmpty()) {
			Inventories.writeData(view.get("FormationSlots"), this.formationSlots, true);
		}
	}
}
