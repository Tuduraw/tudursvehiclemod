package com.example.tudursvehiclemod.client;

import com.example.tudursvehiclemod.client.render.AddonTextureLoader;
import com.example.tudursvehiclemod.client.render.ObjModelLoader;
import com.example.tudursvehiclemod.client.render.VehicleEntityRenderer;
import com.example.tudursvehiclemod.entity.AircraftEntity;
import com.example.tudursvehiclemod.item.TieredVehicleSpawnerItem;
import com.example.tudursvehiclemod.network.AircraftOrientationInputPayload;
import com.example.tudursvehiclemod.network.DescendPayload;
import com.example.tudursvehiclemod.network.FireWeaponPayload;
import com.example.tudursvehiclemod.network.FreeLookPayload;
import com.example.tudursvehiclemod.network.GearTogglePayload;
import com.example.tudursvehiclemod.network.HatchTogglePayload;
import com.example.tudursvehiclemod.network.ThrottleInputPayload;
import com.example.tudursvehiclemod.network.ManualModePayload;
import com.example.tudursvehiclemod.registry.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class VehicleModClient implements ClientModInitializer {

	private static KeyBinding fireWeaponKey;
	private static KeyBinding weaponSwitchKey;
	private static KeyBinding seatSwitchKey;
	private static KeyBinding seatSwitchPreviousKey;
	private static KeyBinding carrierLockToggleKey;
	private static KeyBinding searchLightToggleKey;
	private static KeyBinding freeLookKey;
	private static KeyBinding descendKey;
	private static KeyBinding levelAscendKey;
	private static KeyBinding levelDescendKey;
	private static KeyBinding zoomInKey;
	private static KeyBinding zoomOutKey;
	private static KeyBinding hatchToggleKey;
	/** MC Heli's own multi-CameraPosition doc - see AbstractVehicleEntity's own tudursvehiclemod$cycleCameraPosition() doc for why this needs its own key rather than reusing 'H', which MC Heli itself does but which this project had already assigned to hatch/canopy toggle. */
	private static KeyBinding cameraSwitchKey;
	private static KeyBinding gearToggleKey;
	private static KeyBinding droneRecordingToggleKey;
	private static KeyBinding vtolModeToggleKey;
	/** Dispenses this vehicle's own flares/countermeasures. */
	private static KeyBinding deployFlaresKey;
	private static KeyBinding manualModeKey;
	private static KeyBinding openVehicleMenuKey;
	/** Supports MC Heli's own MobDropOption - triggers a mob-drop sequence on the vehicle this player is piloting, if one is configured at all (see asset.MobDropOption's own doc). */
	private static KeyBinding mobDropTriggerKey;
	/** Used for BOTH on-foot parachute deployment and seat-based parachute-jump (enableParachuting) - previously both hardcoded to reuse vanilla's own client.options.jumpKey directly, which worked but wasn't independently rebindable from ordinary Jump. Default O, distinct from ejectSeatKey's own hold-based use of Jump (unchanged, per that separate direct request - "射出座席の割り当てについて、Space長押しとしてください"). */
	/** On-foot manual parachute deployment by an equipped player - its own dedicated, independently rebindable key binding (default Space, matching this feature's own original convenience before it had a dedicated binding at all), distinct from parachuteJumpKey below (seat-based enableParachuting). */
	private static KeyBinding parachuteDeployKey;
	/** Per parachuteDeployKey's own doc: seat-based parachute-jump (enableParachuting) - its own separate key binding (default O), independent of parachuteDeployKey above. */
	private static KeyBinding parachuteJumpKey;
	private static boolean lastFreeLookState = false;
	private static boolean lastDescendState = false;
	private static boolean lastLevelAscendState = false;
	private static boolean lastLevelDescendState = false;
	private static boolean lastBrakeState = false;
	private static float lastThrottleInputState = 0f;
	private static float lastSidewaysInputState = 0f;
	private static int manualModeHeldTicks = 0;
	private static boolean manualModeToggledThisHold = false;
	/** How many consecutive ticks manualModeKey must be held before it toggles manual mode. */
	private static final int MANUAL_MODE_HOLD_TICKS = 20;
	private static int ejectHeldTicks = 0;
	private static boolean ejectTriggeredThisHold = false;
	/** How many consecutive ticks Space must be held (while riding a vehicle with enableEjectionSeat) before it triggers an eject - same value/pattern as MANUAL_MODE_HOLD_TICKS's own established "hold to trigger" convention. */
	private static final int EJECT_HOLD_TICKS = 20;
	private static int parachuteJumpHeldTicks = 0;
	private static boolean parachuteForceDismountTriggeredThisHold = false;
	/** Same value/pattern as EJECT_HOLD_TICKS's own established "hold to trigger" convention. */
	private static final int PARACHUTE_FORCE_DISMOUNT_HOLD_TICKS = 20;
	private static VehicleModConfig config;

	/** Used by this mod's own entity renderers (VehicleEntityRenderer, VehicleProjectileRenderer) to check the render-distance/culling settings. */
	public static VehicleModConfig getConfig() {
		return config;
	}
	/** Which of the LOCAL player's own available weapons (i.e. */
	private static int selectedWeaponIndex = 0;
	/** The vehicle entity (by id) the local player was riding as of last tick. */
	private static Integer previouslyMountedVehicleId = null;

	/** Read by client.hud.HudVariables to show the currently-selected weapon's own name/ammo/reload status. */
	public static int getSelectedWeaponIndex() {
		return selectedWeaponIndex;
	}

	/** Client-side counterpart to AbstractVehicleEntity's server-only getEffectiveVehicle() - resolves the vehicle the player is controlling (actual mount, or remote-controlled via a station), or null. */
	public static com.example.tudursvehiclemod.entity.AbstractVehicleEntity tudursvehiclemod$getClientEffectiveVehicle(
			net.minecraft.entity.player.PlayerEntity player) {
		if (player.getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity mounted) {
			return mounted;
		}
		Integer remoteControlledId = com.example.tudursvehiclemod.client.RemoteControlState.controlledEntityId;
		if (remoteControlledId != null && player.getEntityWorld().getEntityById(remoteControlledId)
				instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity remoteVehicle) {
			return remoteVehicle;
		}
		return null;
	}

	/** Weapon indices bound to the given player's own seat, plus pilot-fallback-eligible ones. Public/PlayerEntity-typed so HudVariables can reuse it. */
	public static java.util.List<Integer> tudursvehiclemod$ownSeatWeaponIndices(
			net.minecraft.entity.player.PlayerEntity player) {
		java.util.List<Integer> result = new java.util.ArrayList<>();
		if (!(tudursvehiclemod$getClientEffectiveVehicle(player) instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle)) {
			return result;
		}
		int seatIndex = vehicle.tudursvehiclemod$getAssignedSeatIndex(player);
		var weapons = vehicle.getDefinition().weapons();
		for (int i = 0; i < weapons.size(); i++) {
			var weapon = weapons.get(i);
			if (weapon.seatIndex() == seatIndex) {
				result.add(i);
			} else if (seatIndex == 0 && weapon.pilotUsable()
					&& vehicle.tudursvehiclemod$getSeatOccupant(weapon.seatIndex()) == null) {
				// This used to only ever offer
				// weapons bound to the player's own EXACT seat at all -
				// entity.AbstractVehicleEntity's own tryFireWeapon()
				// already correctly lets the pilot fire a gunner-seat
				// weapon whenever that seat is empty (Readme_Aircraft.txt's
				// own documented "true, N" pilotUsable+seat behavior),
				// but with no way for the pilot to actually SELECT such
				// a weapon in the first place (this client-side list is
				// what weapon-cycling/firing keys actually choose from),
				// that server-side permission was never reachable
				// through ordinary play at all. Included here now,
				// matching that same eligibility check exactly.
				result.add(i);
			}
		}
		return result;
	}


	@Override
	public void onInitializeClient() {
		config = VehicleModConfig.load();

		// Lets the common (main-sourceSet) TieredVehicleSpawnerItem open this client-only screen without a compile-time dependency on client code.
		TieredVehicleSpawnerItem.screenOpener = (category, tier) ->
				MinecraftClient.getInstance().setScreen(new VehicleSelectScreen(category, tier));

		// Same reasoning as TieredVehicleSpawnerItem.screenOpener just above, for AbstractVehicleEntity's own onSpawnPacket() - see that field's own doc.
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$clientRequestSeatResyncCallback = vehicleEntityId ->
				ClientPlayNetworking.send(
						new com.example.tudursvehiclemod.network.RequestSeatResyncPayload(vehicleEntityId));

		// Client-only: parses assets/<namespace>/models/obj/*.obj on every resource reload.
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
				.registerReloadListener(new ObjModelLoader());
		// Loads PNGs dropped straight into tudursvehiclemod-addons/ (see AddonPaths) and registers them with the TextureManager directly, since loose files outside any..
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
				.registerReloadListener(new AddonTextureLoader());
		AddonTextureLoader.loadAll();

		// One renderer class serves every OBJ-based vehicle.
		EntityRendererRegistry.register(ModEntityTypes.HELICOPTER, VehicleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.CAR, VehicleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.SHIP, VehicleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.SUBMARINE, VehicleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.AIRCRAFT, VehicleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.VTOL, VehicleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.STATIC_EMPLACEMENT, VehicleEntityRenderer::new);
		// See entity.ParachuteEntity's own doc.
		EntityRendererRegistry.register(ModEntityTypes.PARACHUTE, VehicleEntityRenderer::new);
		// See DummyPilotEntityRenderer's own doc. The model layer must be registered before the renderer is constructed, since that's what resolves it via context.getPart().
		com.example.tudursvehiclemod.client.render.DummyPilotModelLayers.register();
		EntityRendererRegistry.register(ModEntityTypes.DUMMY_PILOT,
				com.example.tudursvehiclemod.client.render.DummyPilotEntityRenderer::new);
		// Every registered EntityType needs a client-side renderer, even one with genuinely nothing to draw (this entity is always invisible) - omitting this entirely crashed the client the instant it tried to render one.
		EntityRendererRegistry.register(ModEntityTypes.CARRIER_RUNWAY_PLATFORM,
				com.example.tudursvehiclemod.client.render.CarrierRunwayPlatformEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.CARRIER_RUNWAY_BORDER_PLATFORM,
				com.example.tudursvehiclemod.client.render.CarrierRunwayPlatformEntityRenderer::new);
		// CARRIER_RUNWAY_PLATFORM_SMALLER_VARIANTS (the 7 additional, smaller discrete tile sizes - see that field's own doc) never had a client-side renderer registered either, same as the attribute registration gap above - this only surfaced as a crash once tiles of these types could actually spawn and reach the client for the first time.
		for (net.minecraft.entity.EntityType<?> smallerVariant : ModEntityTypes.CARRIER_RUNWAY_PLATFORM_SMALLER_VARIANTS) {
			@SuppressWarnings("unchecked")
			net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity> typedVariant =
					(net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity>) smallerVariant;
			EntityRendererRegistry.register(typedVariant,
					com.example.tudursvehiclemod.client.render.CarrierRunwayPlatformEntityRenderer::new);
		}

		// Weapon projectiles piggyback on vanilla's "flying item" renderer (same one snowballs/eggs use).
		EntityRendererRegistry.register(ModEntityTypes.VEHICLE_PROJECTILE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.VEHICLE_MODEL_PROJECTILE,
				com.example.tudursvehiclemod.client.render.VehicleProjectileRenderer::new);

		// This mod's own custom explosion/smoke/water-splash visual effects, separate from vanilla's own built-in particles.
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(
				com.example.tudursvehiclemod.registry.ModParticleTypes.EXPLOSION_FLASH,
				com.example.tudursvehiclemod.client.particle.ExplosionFlashParticle.Factory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(
				com.example.tudursvehiclemod.registry.ModParticleTypes.SMOKE_PUFF,
				com.example.tudursvehiclemod.client.particle.SmokePuffParticle.Factory::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(
				com.example.tudursvehiclemod.registry.ModParticleTypes.WATER_SPLASH,
				com.example.tudursvehiclemod.client.particle.WaterSplashParticle.Factory::new);

		net.minecraft.client.gui.screen.ingame.HandledScreens.register(
				com.example.tudursvehiclemod.registry.ModScreenHandlers.FUEL_REFINER,
				com.example.tudursvehiclemod.client.screen.FuelRefinerScreen::new);
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(
				com.example.tudursvehiclemod.registry.ModScreenHandlers.VEHICLE_MENU,
				com.example.tudursvehiclemod.client.screen.VehicleMenuScreen::new);
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(
				com.example.tudursvehiclemod.registry.ModScreenHandlers.DRONE_CENTER,
				com.example.tudursvehiclemod.client.screen.DroneCenterScreen::new);
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(
				com.example.tudursvehiclemod.registry.ModScreenHandlers.DRONE_CENTER_FORMATION,
				com.example.tudursvehiclemod.client.screen.DroneCenterFormationScreen::new);
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(
				com.example.tudursvehiclemod.registry.ModScreenHandlers.STATION,
				com.example.tudursvehiclemod.client.screen.StationScreen::new);
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(
				com.example.tudursvehiclemod.registry.ModScreenHandlers.VEHICLE_CONVERTER,
				com.example.tudursvehiclemod.client.screen.VehicleConverterScreen::new);

		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("tudursvehiclemod", "vehicle"));

		// Defaults to right-click (mouse), per direct request.
		fireWeaponKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.fire_weapon",
				InputUtil.Type.MOUSE,
				GLFW.GLFW_MOUSE_BUTTON_RIGHT,
				category
		));

		// Cycles selectedWeaponIndex through whichever of this vehicle's own weapons are bound to the seat the player is actually sitting in..
		weaponSwitchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.weapon_switch",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				category
		));

		// Moves the player to the next available seat on whatever vehicle they're currently riding (see AbstractVehicleEntity's own tudursvehiclemod$trySwitchSeat() doc) - rebindable like every other KeyBinding registered this way. Moved off V (MC Heli's own convention) to Y, since V is used by vtolModeToggleKey below instead.
		seatSwitchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.seat_switch",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_Y,
				category
		));

		// The PREVIOUS-direction counterpart to seatSwitchKey above (see SwitchSeatPreviousPayload's own doc) - T was unused by any other binding here.
		seatSwitchPreviousKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.seat_switch_previous",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_U,
				category
		));

		// Toggles lock mode on the Carrier formation lead aircraft currently piloted (see network.ToggleCarrierLockModePayload's own doc) - the Alt-held combo instead releases every wingman's own current lock (network.ReleaseAllCarrierLocksPayload), matching seatSwitchKey's own identical Alt-combo pattern below. B was unused by any other binding here.
		carrierLockToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.carrier_lock_toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_B,
				category
		));

		// Per Readme_Weapon.txt's own ModeNum doc: toggles the currently-selected weapon's own mode (see ToggleWeaponModePayload's own doc) - X matches MC Heli's own documented convention for this.
		// Weapon_mode_toggle no longer has its own registered KeyBinding at all. It is resolved purely from weaponSwitchKey's own current binding plus whether Alt is held at press time - see the tick handler below - so there is genuinely only ONE rebindable key entry governing both, and moving it moves both actions together.

		// Shares search_light_toggle's own physical key (L), disambiguated by Alt the same way weapon_mode_toggle above now shares weapon_switch's own R.
		searchLightToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.search_light_toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_L,
				category
		));

		// Per the same correction as weapon_mode_toggle's own doc above: no separate KeyBinding of its own - resolved from searchLightToggleKey's own binding plus Alt state at press time.

		// Toggles a VTOL between helicopter and aircraft mode (see VtolEntity's own tudursvehiclemod$tryToggleVtolMode() doc) - only meaningful while piloting a VtolEntity, checked at send time below.
		vtolModeToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.vtol_mode_toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_V,
				category
		));

		// Dispenses this vehicle's own flares/countermeasures - see AbstractVehicleEntity's own tudursvehiclemod$deployFlares() doc.
		deployFlaresKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.deploy_flares",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_N,
				category
		));

		freeLookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.free_look",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_LEFT_ALT,
				category
		));

		// Defaults to Left Ctrl, NOT Left Shift: Left Shift is vanilla's own sneak
		// binding, which dismounts, so binding descend there makes the two fight
		// each other for anyone on default vanilla controls.
		descendKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.descend",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_LEFT_CONTROL,
				category
		));

		// SubmarineEntity's own "ascend/descend without changing pitch" (see its own doc) - arrow keys are unbound by vanilla by default.
		levelAscendKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.level_ascend",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UP,
				category
		));
		levelDescendKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.level_descend",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_DOWN,
				category
		));

		// Aircraft third-person camera zoom.
		zoomOutKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.camera_zoom_out",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_PAGE_UP,
				category
		));
		zoomInKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.camera_zoom_in",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_PAGE_DOWN,
				category
		));

		// Toggles hatch/canopy-type TogglePart parts (see TogglePart, and AbstractVehicleEntity#toggleHatch()).
		hatchToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.hatch_toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				category
		));

		// Cycles this vehicle's own multiple CameraPosition entries (see AbstractVehicleEntity#tudursvehiclemod$cycleCameraPosition()) - MC Heli itself reuses 'H' for this, but this project had already assigned that to hatch/canopy toggle above, so this gets its own key instead.
		cameraSwitchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.camera_switch",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_C,
				category
		));

		// Toggles wing fold state (see AbstractVehicleEntity#tryToggleWingFold(), AddPartWing/VariableSweepWing). Per the same genuine-unification correction as weapon_mode_toggle's own doc: no separate KeyBinding of its own - resolved from hatchToggleKey's own binding plus Alt state at press time.

		// Manually flips landing gear (see AbstractVehicleEntity#toggleLandingGear).
		gearToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.gear_toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				category
		));

		// Toggles drone route recording mode while riding a vehicle with a DroneRouteBookItem in the offhand - see network.ToggleDroneRecordingPayload's own doc.
		droneRecordingToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.drone_recording_toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_J,
				category
		));

		// No separate KeyBinding of its own any more. Resolved from seatSwitchKey's own current binding plus whether Alt is held at press time - see the tick handler below, and network.ToggleCarrierSeatPayload's own doc for what this actually does.

		// Opens the vehicle's own menu (fuel can slot, cargo, ammo resupply).
		openVehicleMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.open_vehicle_menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				category
		));

		// Hold (not tap) to toggle.
		manualModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.manual_mode",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				category
		));

		// Supports MC Heli's own MobDropOption.
		mobDropTriggerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.mob_drop_trigger",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				category
		));

		// Independently rebindable keys ("機体操作でのパラシュート展開と、装備しているプレイヤーによる手動での展開のキーは別々に設定可能としてください。後者については変更前同様に規定をSpaceとしてください") - the on-foot one defaults to Space, matching this feature's own original convenience.
		parachuteDeployKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.parachute_deploy",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_SPACE,
				category
		));
		parachuteJumpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursvehiclemod.parachute_jump",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_O,
				category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// ParachuteDeployKey, a dedicated, independently rebindable key binding (default Space, matching this feature's own original convenience) - deploys an equipped parachute while genuinely falling. Not riding any vehicle at all is deliberately part of this check - a parachute is an on-foot feature, and client.player.getVehicle() == null also means this can never fire spuriously while, say, riding out of an aircraft's own seat mid-ejection.
			if (client.player != null && client.player.getVehicle() == null
					&& parachuteDeployKey.wasPressed()
					&& client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).getItem() instanceof com.example.tudursvehiclemod.item.ParachuteItem
					&& com.example.tudursvehiclemod.entity.ParachuteManager.tudursvehiclemod$isDeployable(client.player)) {
				ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.DeployParachutePayload());
			}

			// Reassigned to holding Space - same "hold for EJECT_HOLD_TICKS, trigger once" pattern manualModeKey's own established convention already uses.
			boolean onEjectionSeatVehicle = client.player != null
					&& client.player.getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity ejectVehicle
					&& ejectVehicle.getDefinition().enableEjectionSeat();
			if (onEjectionSeatVehicle && client.options.jumpKey.isPressed()) {
				ejectHeldTicks++;
				if (ejectHeldTicks == EJECT_HOLD_TICKS && !ejectTriggeredThisHold) {
					ejectTriggeredThisHold = true;
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.EjectSeatPayload());
				}
			} else {
				ejectHeldTicks = 0;
				ejectTriggeredThisHold = false;
			}

			// The pilot (seat 0) pressing parachuteJumpKey alone now triggers a mass dismount of every mob passenger, with each one's own outcome (parachute vs. plain dismount) decided per-seat server-side - see AbstractVehicleEntity's own updated tudursvehiclemod$tryDismountAllMobs() doc. Per a further direct request for a separate, unconditional variant ("パラシュート対応座席がある機体での非対応の座席の降車について、検討する必要があります。ctrlキーと組み合わせた場合にパイロット以外のすべての座席に対して強制降車させるようにできますか"): holding sneak (Ctrl by default) while pressing the same key sends ForceDismountAllSeatsPayload instead - every non-pilot passenger, mob or player alike, forced off with no parachute at all, regardless of any seat's own enableParachuting.
			boolean parachuteJumpKeyPressed = parachuteJumpKey.wasPressed();
			boolean pilotOnVehicle = client.player != null
					&& client.player.getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity pilotedVehicle
					&& pilotedVehicle.tudursvehiclemod$getAssignedSeatIndex(client.player) == 0;
			if (pilotOnVehicle && parachuteJumpKey.isPressed()) {
				parachuteJumpHeldTicks++;
				if (parachuteJumpHeldTicks == PARACHUTE_FORCE_DISMOUNT_HOLD_TICKS && !parachuteForceDismountTriggeredThisHold) {
					parachuteForceDismountTriggeredThisHold = true;
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.ForceDismountAllSeatsPayload());
				}
			} else {
				if (parachuteJumpHeldTicks > 0 && !parachuteForceDismountTriggeredThisHold) {
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.DismountAllMobsPayload());
				}
				parachuteJumpHeldTicks = 0;
				parachuteForceDismountTriggeredThisHold = false;
			}

			// ParachuteJumpKey, distinct from parachuteDeployKey's own on-foot check above (which explicitly requires NOT riding anything at all). No longer needs to guard against eject-seat's own Space-hold detection - the two use entirely different physical keys, so there's nothing ambiguous to resolve.
			if (client.player != null && client.player.getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity ridingVehicle) {
				int seatIndex = ridingVehicle.tudursvehiclemod$getAssignedSeatIndex(client.player);
				if (seatIndex >= 0 && seatIndex < ridingVehicle.getDefinition().seats().size()) {
					com.example.tudursvehiclemod.asset.SeatDefinition seat = ridingVehicle.getDefinition().seats().get(seatIndex);
					if (!seat.driver() && seat.enableParachuting() && parachuteJumpKeyPressed) {
						ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.ParachuteJumpPayload());
					}
				}
			}

			// Supports MC Heli's own MobDropOption feature.
			if (client.player != null && client.player.getVehicle() != null && mobDropTriggerKey.wasPressed()) {
				ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.MobDropTriggerPayload());
			}

			// Addon textures are loaded HERE, between frames, rather than inline during rendering - see AddonTextureLoader.processPendingLoads()'s own doc for why doing it mid-frame stalled the GPU driver.
			com.example.tudursvehiclemod.client.render.AddonTextureLoader.processPendingLoads();
			// Rebuilds the snapshot the two lighting mixins query - see SearchLightIllumination.tudursvehiclemod$refresh()'s own doc for why this is per-tick rather than per-query.
			com.example.tudursvehiclemod.client.render.SearchLightIllumination.tudursvehiclemod$refresh();
			com.example.tudursvehiclemod.client.sound.VehicleEngineSoundManager.tick(client);
			com.example.tudursvehiclemod.client.sound.WeaponFireSoundManager.tick();
			com.example.tudursvehiclemod.client.debug.HitDetectionMeshDebugRenderer.tick(client);
			com.example.tudursvehiclemod.client.debug.CollisionBoxDebugRenderer.tick(client);
			com.example.tudursvehiclemod.client.debug.SeatPositionDebugRenderer.tick(client);
			com.example.tudursvehiclemod.client.hud.MortarMarkerRenderer.tick(client);
			com.example.tudursvehiclemod.client.render.TargetHighlightRenderer.tick(client);

			// Sends this mod's own dedicated, always-sent position-sync payload every tick while genuinely riding an AbstractVehicleEntity (client.player.getVehicle() specifically - the actual riding check, not tudursvehiclemod$getClientEffectiveVehicle()'s own broader "or remote-controlling" one, since this is about a real passenger's own position specifically). Bypasses vanilla's own apparently-unreliable-for-slow-vehicles VehicleMoveC2SPacket mechanism entirely.
			if (client.player != null && client.player.getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity) {
				ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.PassengerPositionSyncPayload(
						client.player.getX(), client.player.getY(), client.player.getZ()));
			}

			// Auto-correct selectedWeaponIndex to a weapon actually bound to the player's own current seat, every tick.
			int weaponIndexBeforeThisTick = selectedWeaponIndex;
			if (client.player != null) {
				com.example.tudursvehiclemod.entity.AbstractVehicleEntity effectiveVehicleForWeaponReset =
					tudursvehiclemod$getClientEffectiveVehicle(client.player);
			Integer currentVehicleId = effectiveVehicleForWeaponReset != null ? effectiveVehicleForWeaponReset.getId() : null;
				if (!java.util.Objects.equals(currentVehicleId, previouslyMountedVehicleId) && currentVehicleId != null) {
					// Freshly mounted (including remounting the same vehicle type again after dismounting).
					selectedWeaponIndex = 0;
				}
				previouslyMountedVehicleId = currentVehicleId;

				var ownWeapons = tudursvehiclemod$ownSeatWeaponIndices(client.player);
				if (!ownWeapons.isEmpty() && !ownWeapons.contains(selectedWeaponIndex)) {
					selectedWeaponIndex = ownWeapons.get(0);
				}

				// FixCameraPitch: forces view pitch to exactly 0 every tick while the currently selected weapon has this flag set - the mouse-move-only mixin injection (PlayerLookRateMixin) alone wouldn't correct an already-nonzero pitch from before this weapon was selected.
				java.util.List<com.example.tudursvehiclemod.asset.WeaponDefinition> weaponsForPitchLock =
						effectiveVehicleForWeaponReset != null ? effectiveVehicleForWeaponReset.getDefinition().weapons() : java.util.List.of();
				if (selectedWeaponIndex >= 0 && selectedWeaponIndex < weaponsForPitchLock.size()
						&& ownWeapons.contains(selectedWeaponIndex)
						&& weaponsForPitchLock.get(selectedWeaponIndex).fixCameraPitch()) {
					client.player.setPitch(0f);
				}
			}

			// The server re-validates seat/cooldown, so this is just "tell it which mount I meant".
			com.example.tudursvehiclemod.entity.AbstractVehicleEntity effectiveVehicleForFire =
					client.player != null ? tudursvehiclemod$getClientEffectiveVehicle(client.player) : null;
			if (effectiveVehicleForFire != null && effectiveVehicleForFire.tudursvehiclemod$isCarrierLockModeActive()) {
				// Lock mode replaces the fire key's own normal action entirely - locking is instantaneous, so wasPressed() (once per press) rather than isPressed() (every tick while held, matching normal fire's own auto-fire-while-held behavior).
				if (fireWeaponKey.wasPressed()) {
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.LockCarrierTargetPayload());
				}
			} else if (fireWeaponKey.isPressed()) {
				if (effectiveVehicleForFire != null) {
					ClientPlayNetworking.send(new FireWeaponPayload(selectedWeaponIndex));
				}
			}

			// WeaponSwitchKey's own wasPressed() is read exactly once, then branched on Alt state - weapon_mode_toggle has no KeyBinding of its own at all any more, so rebinding weaponSwitchKey via Minecraft's own Controls screen moves both actions together.
			if (weaponSwitchKey.wasPressed()) {
				if (freeLookKey.isPressed()) {
					if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
						ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.ToggleWeaponModePayload());
					}
				} else {
					if (client.player != null) {
						var ownWeapons = tudursvehiclemod$ownSeatWeaponIndices(client.player);
						if (!ownWeapons.isEmpty()) {
							int currentPosition = ownWeapons.indexOf(selectedWeaponIndex);
							int nextPosition = (currentPosition + 1) % ownWeapons.size();
							selectedWeaponIndex = ownWeapons.get(nextPosition);
						}
					}
				}
			}

			// Per the same genuine-unification correction: seatSwitchKey's own wasPressed() is read exactly once, then branched on Alt state - carrier_seat_switch has no KeyBinding of its own any more. See network.ToggleCarrierSeatPayload's own doc for what the Alt-held branch actually does.
			if (seatSwitchKey.wasPressed()) {
				if (freeLookKey.isPressed()) {
					if (client.player != null) {
						ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.ToggleCarrierSeatPayload());
					}
				} else {
					if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
						ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.SwitchSeatPayload());
					}
				}
			}

			if (seatSwitchPreviousKey.wasPressed()) {
				if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.SwitchSeatPreviousPayload());
				}
			}

			// CarrierLockToggleKey's own wasPressed() is read exactly once, then branched on Alt state, matching seatSwitchKey's own identical pattern above.
			if (carrierLockToggleKey.wasPressed()) {
				if (freeLookKey.isPressed()) {
					if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
						ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.ReleaseAllCarrierLocksPayload());
					}
				} else {
					if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
						ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.ToggleCarrierLockModePayload());
					}
				}
			}

			// Per the same genuine-unification correction: searchLightToggleKey's own wasPressed() is read exactly once, then branched on Alt state - nav_lights_toggle has no KeyBinding of its own any more.
			if (searchLightToggleKey.wasPressed()) {
				if (freeLookKey.isPressed()) {
					if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
						ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.ToggleNavLightsPayload());
					}
				} else {
					if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
						ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.ToggleSearchLightPayload());
					}
				}
			}

			// Synced separately from firing (see network.SelectWeaponPayload's own
			// doc) so AddPartWeaponBay-style parts can open the moment a weapon is
			// selected, not just once it's actually fired.
			if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null
					&& selectedWeaponIndex != weaponIndexBeforeThisTick) {
				ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.SelectWeaponPayload(selectedWeaponIndex));
			}

			// Not gated to "riding an aircraft".
			while (zoomOutKey.wasPressed()) {
				AircraftCameraZoomState.adjust(1, config.maxAircraftCameraDistance);
			}
			while (zoomInKey.wasPressed()) {
				AircraftCameraZoomState.adjust(-1, config.maxAircraftCameraDistance);
			}

			// Per the same genuine-unification correction as weapon_switch/weapon_mode_toggle's own doc: hatchToggleKey's own wasPressed() is read exactly once, then branched on Alt state - wing_fold_toggle has no KeyBinding of its own any more.
			if (hatchToggleKey.wasPressed()) {
				if (freeLookKey.isPressed()) {
					if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
						ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.WingFoldTogglePayload());
					}
				} else {
					if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
						ClientPlayNetworking.send(new HatchTogglePayload());
					}
				}
			}

			// Purely client-side (see AbstractVehicleEntity's
			// own tudursvehiclemod$cycleCameraPosition() doc for why) - directly
			// cycles the LOCAL player's own copy of the vehicle entity, no
			// server round-trip at all.
			if (cameraSwitchKey.wasPressed()) {
				if (client.player != null
						&& tudursvehiclemod$getClientEffectiveVehicle(client.player) instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
					int seatIndex = vehicle.tudursvehiclemod$getAssignedSeatIndex(client.player);
					vehicle.tudursvehiclemod$cycleCameraPosition(seatIndex);
				}
			}

			if (openVehicleMenuKey.wasPressed()) {
				if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.OpenVehicleMenuPayload());
				}
			}

			if (gearToggleKey.wasPressed()) {
				if (client.player != null
						&& tudursvehiclemod$getClientEffectiveVehicle(client.player) instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
					// CRITICAL: also toggle directly on the client's own local copy, not just send it to the server.
					vehicle.toggleLandingGear();
					ClientPlayNetworking.send(new GearTogglePayload());
				}
			}

			// Toggles drone route recording mode - actual validation (offhand book present, currently riding a vehicle) happens server-side (see network.ToggleDroneRecordingPayload's own doc); this just sends the request.
			if (droneRecordingToggleKey.wasPressed()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.ToggleDroneRecordingPayload());
				}
			}

			// Unlike the simpler gear toggle above,
			// tryToggleVtolMode() has non-trivial gating (a level-attitude
			// check) and drives a multi-tick transition state - left
			// entirely server-authoritative rather than also predicted
			// locally, to avoid the client's own guess ever disagreeing
			// with whether the level check actually passed.
			if (vtolModeToggleKey.wasPressed()) {
				if (client.player != null
						&& tudursvehiclemod$getClientEffectiveVehicle(client.player) instanceof com.example.tudursvehiclemod.entity.VtolEntity) {
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.VtolModeTogglePayload());
				}
			}

			if (deployFlaresKey.wasPressed()) {
				if (client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null) {
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.DeployFlaresPayload());
				}
			}

			// Manual mode: hold (not tap) manualModeKey for MANUAL_MODE_HOLD_TICKS to toggle.
			if (manualModeKey.isPressed()) {
				manualModeHeldTicks++;
				if (manualModeHeldTicks == MANUAL_MODE_HOLD_TICKS && !manualModeToggledThisHold) {
					manualModeToggledThisHold = true;
					if (client.player != null
							&& tudursvehiclemod$getClientEffectiveVehicle(client.player) instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity manualVehicle) {
						boolean newManualMode = !manualVehicle.isManualMode();
						manualVehicle.setManualMode(newManualMode);
						ClientPlayNetworking.send(new ManualModePayload(newManualMode));
					}
				}
			} else {
				manualModeHeldTicks = 0;
				manualModeToggledThisHold = false;
			}

			boolean mounted = client.player != null && tudursvehiclemod$getClientEffectiveVehicle(client.player) != null;

			// Free-look and descend are held states, not one-shot presses.
			if (mounted) {
				boolean currentFreeLook = freeLookKey.isPressed();
				// This reset made any such aim snap to vehicle-forward the instant Alt was pressed or released, regardless of whether the vehicle actually supports free-look. The actual free-look side effects (the reset below, and sending FreeLookPayload) are now restricted to FreeCameraVehicle - the existing marker interface (AircraftEntity, SubmarineEntity) tudursvehiclemod$setFreeLook() already checks server-side for this exact reason (see that method's own doc) - so a vehicle that was never meant to use free-look now sees no free-look-related processing AT ALL when Alt is pressed, leaving whatever it does with player look/aim completely undisturbed.
				//
				// lastFreeLookState itself is still resynced UNCONDITIONALLY here (regardless of vehicle type), so it never goes stale while mounted in a non-free-look vehicle - without this, releasing Alt specifically while inside such a vehicle would leave lastFreeLookState stuck at whatever it was before, triggering a spurious one-off reset the next time a free-look-capable vehicle was entered.
				//
				// freeLookKey.isPressed() itself is read UNCONDITIONALLY above, outside any gate, and stays exactly as it already was for the R/L/H combo checks elsewhere in this same tick handler - KeyBinding.isPressed() is a raw physical-key read that was never tied to which vehicle is mounted to begin with, so restricting the free-look-SPECIFIC side effects below to FreeCameraVehicle changes nothing about Alt detection for anything else.
				if (currentFreeLook != lastFreeLookState) {
					lastFreeLookState = currentFreeLook;
					if (tudursvehiclemod$getClientEffectiveVehicle(client.player) instanceof com.example.tudursvehiclemod.entity.FreeCameraVehicle
							&& tudursvehiclemod$getClientEffectiveVehicle(client.player)
									instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
						// Resets the player's own yaw/pitch to match the vehicle's current facing, both when
						// free-look STARTS (so it begins from the current facing rather than resuming wherever
						// a previous session left off) and when it ENDS (so the view returns to its pre-free-look
						// position instead of the vehicle snapping to match wherever the player was looking at
						// release - player.yaw/pitch otherwise just sit frozen outside free-look, since
						// PlayerLookRateMixin routes mouse input elsewhere during that time).
						client.player.setYaw(vehicle.getYaw());
						client.player.setPitch(vehicle.getPitch());
						client.player.setHeadYaw(vehicle.getYaw());
						client.player.setBodyYaw(vehicle.getYaw());
						ClientPlayNetworking.send(new FreeLookPayload(currentFreeLook));
					}
				}

				boolean currentDescend = descendKey.isPressed();
				if (currentDescend != lastDescendState) {
					lastDescendState = currentDescend;
					ClientPlayNetworking.send(new DescendPayload(currentDescend));
				}

				boolean currentLevelAscend = levelAscendKey.isPressed();
				boolean currentLevelDescend = levelDescendKey.isPressed();
				// CRITICAL: also set directly on the client's own local copy
				// (same reason as syncedThrottleInput/syncedSidewaysInput
				// just below) - without this, the client's own local
				// prediction of updateVehicleMovement() never sees these
				// inputs at all (they're plain fields, not synced
				// TrackedData, and were previously only ever set from the
				// server-side network handler), so diving/ascending applied
				// correctly on the server but never visually moved the
				// entity on screen.
				if (tudursvehiclemod$getClientEffectiveVehicle(client.player)
						instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity levelVehicle) {
					levelVehicle.setSyncedLevelAscendInput(currentLevelAscend);
					levelVehicle.setSyncedLevelDescendInput(currentLevelDescend);
				}
				if (currentLevelAscend != lastLevelAscendState || currentLevelDescend != lastLevelDescendState) {
					lastLevelAscendState = currentLevelAscend;
					lastLevelDescendState = currentLevelDescend;
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.VerticalLevelInputPayload(
							currentLevelAscend, currentLevelDescend));
				}

				// See ThrottleInputPayload's own doc for why this exists: pilot.forwardSpeed/sidewaysSpeed aren't reliably readable on the server, so the client's own..
				// Throttle would climb toward
				// 100% then visibly decay back down on its own during
				// remote control specifically: client.player.forwardSpeed/
				// sidewaysSpeed are themselves derived from vanilla's own
				// PLAYER MOVEMENT pipeline, which entity.AbstractVehicleEntity's
				// own tudursvehiclemod$updateRemoteControllerFreeze()
				// (re-anchoring this same player's own position/velocity
				// back to the station every single server tick, for as
				// long as remote control is active) appears to disrupt/
				// reset - computed directly from the raw movement KEY
				// STATE instead for the remote-control case specifically
				// (vanilla's own exact forward/sideways sign convention:
				// forward key +1, back key -1; left key +1, right key -1),
				// entirely bypassing that disrupted field.
				float currentThrottleInput;
				float currentSidewaysInput;
				if (client.player.getVehicle() == null && com.example.tudursvehiclemod.client.RemoteControlState.controlledEntityId != null) {
					currentThrottleInput = (client.options.forwardKey.isPressed() ? 1f : 0f) - (client.options.backKey.isPressed() ? 1f : 0f);
					currentSidewaysInput = (client.options.leftKey.isPressed() ? 1f : 0f) - (client.options.rightKey.isPressed() ? 1f : 0f);
				} else {
					currentThrottleInput = client.player.forwardSpeed;
					currentSidewaysInput = client.player.sidewaysSpeed;
				}
				if (tudursvehiclemod$getClientEffectiveVehicle(client.player) instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity throttleVehicle) {
					throttleVehicle.setSyncedThrottleInput(currentThrottleInput);
					throttleVehicle.setSyncedSidewaysInput(currentSidewaysInput);
				}
				if (currentThrottleInput != lastThrottleInputState || currentSidewaysInput != lastSidewaysInputState) {
					lastThrottleInputState = currentThrottleInput;
					lastSidewaysInputState = currentSidewaysInput;
					ClientPlayNetworking.send(new ThrottleInputPayload(currentThrottleInput, currentSidewaysInput));
				}

				// CarEntity-specific brake, bound to
				// vanilla's own jump key (Space by default) - unused by any
				// vehicle here otherwise (unlike helicopters, which use a
				// dedicated descend key instead - see README's own doc).
				boolean currentBrake = client.options.jumpKey.isPressed();
				if (tudursvehiclemod$getClientEffectiveVehicle(client.player) instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity brakeVehicle) {
					brakeVehicle.setSyncedBrakeInput(currentBrake);
				}
				if (currentBrake != lastBrakeState) {
					lastBrakeState = currentBrake;
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.BrakeInputPayload(currentBrake));
				}

				// Unlike free-look/descend (booleans, sent only on change), this is a small continuous value.
				// VtolEntity's own aircraft mode uses
				// this exact same pendingYawInput/pendingPitchInput
				// mechanism as AircraftEntity (see AbstractVehicleEntity's
				// own tudursvehiclemod$usesAircraftStyleOrientation()
				// doc for why this used to only ever check AircraftEntity
				// alone) - VtolEntity just isn't an AircraftEntity
				// subclass, so it needs its own explicit branch here since
				// the two don't share a common field to write through.
				var riddenVehicle = tudursvehiclemod$getClientEffectiveVehicle(client.player);
				if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$usesAircraftStyleOrientation(riddenVehicle)) {
					float yawDelta = AircraftOrientationInputState.accumulatedYawDelta;
					float pitchDelta = AircraftOrientationInputState.accumulatedPitchDelta;

					// CRITICAL: also apply this directly to the CLIENT'S OWN local copy of the vehicle, not just send it to the server.
					if (riddenVehicle instanceof AircraftEntity aircraft) {
						aircraft.pendingYawInput += yawDelta;
						aircraft.pendingPitchInput += pitchDelta;
					} else if (riddenVehicle instanceof com.example.tudursvehiclemod.entity.VtolEntity vtol) {
						vtol.pendingYawInput += yawDelta;
						vtol.pendingPitchInput += pitchDelta;
					} else if (riddenVehicle instanceof com.example.tudursvehiclemod.entity.HelicopterEntity helicopter) {
						helicopter.pendingYawInput += yawDelta;
						helicopter.pendingPitchInput += pitchDelta;
					}

					ClientPlayNetworking.send(new AircraftOrientationInputPayload(yawDelta, pitchDelta));
					AircraftOrientationInputState.accumulatedYawDelta = 0f;
					AircraftOrientationInputState.accumulatedPitchDelta = 0f;
				}

				// Per Readme_Weapon.txt's own TVMissile doc - see client.TvMissileControlState's own doc for why this is a completely separate check from the vehicle-orientation one just above (steering a missile has nothing to do with whatever vehicle the player still happens to be seated in).
				if (com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId != null) {
					// Then snaps back up"
					// desync pattern: a likely cause was network.TvMissileControlStartPayload's
					// own receiver running before this missile's own spawn
					// packet had actually reached/been processed by this
					// client yet, meaning tudursvehiclemod$initTvClientTracking()
					// silently never got called at all (see that
					// receiver's own diagnostic logging) - this missile's
					// own client-side copy would then just fall completely
					// unpredicted between server sync packets forever
					// after, while the server's own copy (which DID get
					// initialized, unconditionally, in tryFireWeapon())
					// kept steering level throughout. Retried here, every
					// tick, as a fallback - the instant the entity actually
					// becomes available AND isn't already initialized, it
					// gets initialized right then instead of never at all.
					if (client.world != null && client.world.getEntityById(
							com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId)
							instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity trackedMissile
							&& !trackedMissile.tudursvehiclemod$isTvSteeringActive()) {
						trackedMissile.tudursvehiclemod$initTvClientTracking();
					}
					float tvYawDelta = com.example.tudursvehiclemod.client.TvMissileControlState.accumulatedYawDelta;
					float tvPitchDelta = com.example.tudursvehiclemod.client.TvMissileControlState.accumulatedPitchDelta;
					if (tvYawDelta != 0f || tvPitchDelta != 0f) {
						// CRITICAL: also apply this directly to the CLIENT'S
						// OWN local copy of the missile, not just send it to
						// the server - see entity.projectile.VehicleProjectileEntity's
						// own tvSteeringActive doc for why skipping this
						// (relying purely on the server's own eventual sync
						// packets instead) is exactly what caused the
						// violent view-shake this is fixing.
						if (client.world != null && client.world.getEntityById(
								com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId)
								instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity missile) {
							missile.pendingTvYawInput += tvYawDelta;
							missile.pendingTvPitchInput += tvPitchDelta;
						}
						ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.TvMissileInputPayload(tvYawDelta, tvPitchDelta));
						com.example.tudursvehiclemod.client.TvMissileControlState.accumulatedYawDelta = 0f;
						com.example.tudursvehiclemod.client.TvMissileControlState.accumulatedPitchDelta = 0f;
					}
				}
			} else {
				// Dismounted while a key was held.
				lastFreeLookState = false;
				lastDescendState = false;
				AircraftOrientationInputState.accumulatedYawDelta = 0f;
				AircraftOrientationInputState.accumulatedPitchDelta = 0f;
			}
		});

		VehicleHud.register();
		com.example.tudursvehiclemod.client.sound.AddonSoundLoader.register();
		com.example.tudursvehiclemod.client.AddonLangLoader.register();

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.WeaponFireSoundPayload.ID, (payload, context) ->
						context.client().execute(() -> com.example.tudursvehiclemod.client.sound.WeaponFireSoundManager.play(
								payload.soundName(), new net.minecraft.util.math.Vec3d(payload.x(), payload.y(), payload.z()),
								payload.volume(), payload.pitch(), payload.pitchRandom())));

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.SyncCruiseSpeedPayload.ID, (payload, context) ->
						context.client().execute(() -> {
							if (context.client().world != null
									&& context.client().world.getEntityById(payload.entityId()) instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
								vehicle.tudursvehiclemod$syncCruiseSpeedAndThrottle(payload.cruiseSpeed(), payload.throttle());
							}
						}));

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneCenterConfigOpenPayload.ID, (payload, context) ->
						context.client().execute(() -> context.client().setScreen(
								new com.example.tudursvehiclemod.client.screen.DroneCenterConfigScreen(
										payload.x(), payload.y(), payload.z(),
										payload.speedFraction(), payload.orbitAltitude(), payload.radiusMultiplier(), payload.active(),
										payload.redstoneControlEnabled(), payload.formationConfig()))));

		// Carrying the server's own available skin list (see that payload's own doc).
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DummyPilotConfigOpenPayload.ID, (payload, context) ->
						context.client().execute(() -> context.client().setScreen(
								new com.example.tudursvehiclemod.client.screen.DummyPilotConfigScreen(
										payload.x(), payload.y(), payload.z(),
										payload.enabled(), payload.skinId(), payload.availableSkinIds(),
										payload.nameVisible(), payload.name()))));

		// Always sent right after DummyPilotConfigOpenPayload above from the same server-side handler, so the screen is guaranteed to already be open (channel ordering) by the time this arrives.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneCenterAutoDisableModeOpenPayload.ID, (payload, context) ->
						context.client().execute(() -> {
							if (context.client().currentScreen instanceof com.example.tudursvehiclemod.client.screen.DummyPilotConfigScreen screen) {
								screen.tudursvehiclemod$setAutoResumeMode(payload.autoResume());
							}
						}));

		// The reply to DummyPilotWeaponConfigRequestPayload, sent when the player presses that button on DummyPilotConfigScreen.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DummyPilotWeaponConfigOpenPayload.ID, (payload, context) ->
						context.client().execute(() -> context.client().setScreen(
								new com.example.tudursvehiclemod.client.screen.DummyPilotWeaponConfigScreen(
										payload.x(), payload.y(), payload.z(),
										payload.weaponDisplayNames(), payload.weaponIndex(),
										payload.attackStartAltitude(), payload.attackStopAltitude(), payload.searchRange(),
										payload.diveTargetYOffset()))));

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneWaypointsOpenPayload.ID, (payload, context) ->
						context.client().execute(() -> context.client().setScreen(
								new com.example.tudursvehiclemod.client.screen.DroneWaypointsScreen(
										payload.x(), payload.y(), payload.z(), payload.encodedWaypoints(), payload.encodedHomePoint(),
										payload.encodedViaPoint()))));

		// The server decides which of the two screens to open (see network.ModNetworking's own DroneWaypointsRequestPayload receiver) - this client just opens whichever payload actually arrives, so the aircraft receiver above stays completely unchanged.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.GroundWaypointsOpenPayload.ID, (payload, context) ->
						context.client().execute(() -> context.client().setScreen(
								new com.example.tudursvehiclemod.client.screen.GroundWaypointsScreen(
										payload.x(), payload.y(), payload.z(), payload.encodedWaypoints()))));

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneRouteBookOpenPayload.ID, (payload, context) ->
						context.client().execute(() -> {
							net.minecraft.util.Hand hand = payload.mainHand() ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND;
							if (context.player() != null) {
								context.client().setScreen(new com.example.tudursvehiclemod.client.screen.DroneRouteBookScreen(
										payload.mainHand(), context.player().getStackInHand(hand)));
							}
						}));

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneStickNameOpenPayload.ID, (payload, context) ->
					context.client().execute(() -> {
						// Per the same per-vehicle localization mechanism as client.VehicleDisplayNames' own doc: payload.suggestedName() was resolved SERVER-side, where I18n is unavailable, so it is always the raw addon-authored (or id-derived) name regardless of the player's own locale. Re-resolved here client-side instead, using whatever VehicleDefinition is locally known for this vehicle's own id - falls back to the server's own suggestion unchanged if that lookup fails for any reason (an unrecognised id, or none loaded), so this can only improve on the existing behaviour, never regress it.
						String suggestedName = payload.suggestedName();
						net.minecraft.util.Identifier vehicleId = net.minecraft.util.Identifier.tryParse(payload.vehicleId());
						if (vehicleId != null) {
							var def = com.example.tudursvehiclemod.asset.VehicleRegistry.get(vehicleId);
							if (def.isPresent()) {
								suggestedName = com.example.tudursvehiclemod.client.VehicleDisplayNames.resolve(vehicleId, def.get());
							}
						}
						context.client().setScreen(
								new com.example.tudursvehiclemod.client.screen.DroneStickNameScreen(
										payload.mainHand(), payload.vehicleId(), suggestedName));
					}));

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.StationMenuOpenPayload.ID, (payload, context) ->
					context.client().execute(() -> context.client().setScreen(
							new com.example.tudursvehiclemod.client.screen.StationMenuScreen(
									payload.x(), payload.y(), payload.z()))));

		// Per Readme_Weapon.txt's own TVMissile doc - see client.TvMissileControlState's own doc.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.TvMissileControlStartPayload.ID, (payload, context) ->
						context.client().execute(() -> {
							com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId = payload.entityId();
							com.example.tudursvehiclemod.client.TvMissileControlState.accumulatedYawDelta = 0f;
							com.example.tudursvehiclemod.client.TvMissileControlState.accumulatedPitchDelta = 0f;
							// This missile's own
							// view shook violently: initializes THIS
							// client's own local copy of the missile's own
							// steering state too (see entity.projectile.VehicleProjectileEntity's
							// own tvSteeringActive doc for why this is
							// essential - without it, the client never
							// predicts this missile's own steering locally
							// at all, relying purely on however often sync
							// packets happen to arrive instead).
							if (context.client().world != null
									&& context.client().world.getEntityById(payload.entityId())
									instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity missile) {
								missile.tudursvehiclemod$initTvClientTracking();
							} else {
								// TEMPORARY diagnostic: if this ever logs,
								// it means this missile's own spawn packet
								// hadn't actually reached/been processed by
								// this client yet at the exact moment this
								// payload arrived - tudursvehiclemod$initTvClientTracking()
								// never got called at all as a result, so
								// this missile would never actually get
								// predicted locally, relying purely on
								// however often position-sync packets
								// happen to arrive instead (a likely
								// explanation for a "falls, then snaps back
								// up" pattern - the server's own copy
								// steers level throughout, the client's own
								// copy just falls, unpredicted, between
								// syncs).
							}
						}));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.TvMissileControlEndPayload.ID, (payload, context) ->
						context.client().execute(() -> {
							// Per tudursvehiclemod$initTvClientTracking()'s
							// own doc: stops this SAME entity's own local
							// prediction too, resolved BEFORE clearing
							// controlledEntityId below (the very value this
							// lookup itself depends on).
							Integer controlledId = com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId;
							if (controlledId != null && context.client().world != null
									&& context.client().world.getEntityById(controlledId)
									instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity missile) {
								missile.tudursvehiclemod$clearTvClientTracking();
							}
							com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId = null;
							com.example.tudursvehiclemod.client.TvMissileControlState.accumulatedYawDelta = 0f;
							com.example.tudursvehiclemod.client.TvMissileControlState.accumulatedPitchDelta = 0f;
						}));

		// See client.RemoteControlState's own doc.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.RemoteControlStartPayload.ID, (payload, context) ->
						context.client().execute(() ->
								com.example.tudursvehiclemod.client.RemoteControlState.controlledEntityId = payload.entityId()));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.RemoteControlEndPayload.ID, (payload, context) ->
						context.client().execute(() -> {
							com.example.tudursvehiclemod.client.RemoteControlState.controlledEntityId = null;
							com.example.tudursvehiclemod.client.RemoteControlState.transformX = Double.NaN;
						}));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.RemoteControlVehicleTransformPayload.ID, (payload, context) ->
						context.client().execute(() -> {
							com.example.tudursvehiclemod.client.RemoteControlState.transformX = payload.x();
							com.example.tudursvehiclemod.client.RemoteControlState.transformY = payload.y();
							com.example.tudursvehiclemod.client.RemoteControlState.transformZ = payload.z();
							com.example.tudursvehiclemod.client.RemoteControlState.transformYaw = payload.yaw();
							com.example.tudursvehiclemod.client.RemoteControlState.transformPitch = payload.pitch();
							com.example.tudursvehiclemod.client.RemoteControlState.transformRoll = payload.roll();
					}));
	}
}
