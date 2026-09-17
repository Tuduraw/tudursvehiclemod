package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.VehicleRegistry;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.item.TieredVehicleSpawnerItem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public class ModNetworking {

	public static void register() {
		PayloadTypeRegistry.playC2S().register(FireWeaponPayload.ID, FireWeaponPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SelectWeaponPayload.ID, SelectWeaponPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestSeatResyncPayload.ID, RequestSeatResyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WeaponFireSoundPayload.ID, WeaponFireSoundPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.example.tudursvehiclemod.network.SyncCruiseSpeedPayload.ID,
				com.example.tudursvehiclemod.network.SyncCruiseSpeedPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FreeLookPayload.ID, FreeLookPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DescendPayload.ID, DescendPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SelectVehiclePayload.ID, SelectVehiclePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AircraftOrientationInputPayload.ID, AircraftOrientationInputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(HatchTogglePayload.ID, HatchTogglePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GearTogglePayload.ID, GearTogglePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DroneCenterConfigUpdatePayload.ID,
				com.example.tudursvehiclemod.network.DroneCenterConfigUpdatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DummyPilotConfigRequestPayload.ID,
				com.example.tudursvehiclemod.network.DummyPilotConfigRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DummyPilotConfigUpdatePayload.ID,
				com.example.tudursvehiclemod.network.DummyPilotConfigUpdatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DummyPilotWeaponConfigRequestPayload.ID,
				com.example.tudursvehiclemod.network.DummyPilotWeaponConfigRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DummyPilotWeaponConfigUpdatePayload.ID,
				com.example.tudursvehiclemod.network.DummyPilotWeaponConfigUpdatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DroneCenterAutoDisableModeUpdatePayload.ID,
				com.example.tudursvehiclemod.network.DroneCenterAutoDisableModeUpdatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DroneWaypointsUpdatePayload.ID,
				com.example.tudursvehiclemod.network.DroneWaypointsUpdatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DroneWaypointsRequestPayload.ID,
				com.example.tudursvehiclemod.network.DroneWaypointsRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.GroundWaypointsUpdatePayload.ID,
				com.example.tudursvehiclemod.network.GroundWaypointsUpdatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.GroundWaypointsRequestPayload.ID,
				com.example.tudursvehiclemod.network.GroundWaypointsRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DroneRouteBookUpdatePayload.ID,
				com.example.tudursvehiclemod.network.DroneRouteBookUpdatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DroneStickRegisterPayload.ID,
				com.example.tudursvehiclemod.network.DroneStickRegisterPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.OpenBlockSlotsPayload.ID,
				com.example.tudursvehiclemod.network.OpenBlockSlotsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.OpenDroneCenterFormationPayload.ID,
				com.example.tudursvehiclemod.network.OpenDroneCenterFormationPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DroneCenterFormationConfigUpdatePayload.ID,
				com.example.tudursvehiclemod.network.DroneCenterFormationConfigUpdatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DroneCenterBasicConfigUpdatePayload.ID,
				com.example.tudursvehiclemod.network.DroneCenterBasicConfigUpdatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.StationToggleRemoteControlPayload.ID,
				com.example.tudursvehiclemod.network.StationToggleRemoteControlPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ThrottleInputPayload.ID, ThrottleInputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.PassengerPositionSyncPayload.ID,
				com.example.tudursvehiclemod.network.PassengerPositionSyncPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ManualModePayload.ID, ManualModePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(OpenVehicleMenuPayload.ID, OpenVehicleMenuPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.ToggleDroneRecordingPayload.ID,
				com.example.tudursvehiclemod.network.ToggleDroneRecordingPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.ToggleCarrierSeatPayload.ID,
				com.example.tudursvehiclemod.network.ToggleCarrierSeatPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.ToggleCarrierLockModePayload.ID,
				com.example.tudursvehiclemod.network.ToggleCarrierLockModePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DeployParachutePayload.ID,
				com.example.tudursvehiclemod.network.DeployParachutePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.EjectSeatPayload.ID,
				com.example.tudursvehiclemod.network.EjectSeatPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.ParachuteJumpPayload.ID,
				com.example.tudursvehiclemod.network.ParachuteJumpPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.MobDropTriggerPayload.ID,
				com.example.tudursvehiclemod.network.MobDropTriggerPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.DismountAllMobsPayload.ID,
				com.example.tudursvehiclemod.network.DismountAllMobsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.ForceDismountAllSeatsPayload.ID,
				com.example.tudursvehiclemod.network.ForceDismountAllSeatsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.LockCarrierTargetPayload.ID,
				com.example.tudursvehiclemod.network.LockCarrierTargetPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(
				com.example.tudursvehiclemod.network.ReleaseAllCarrierLocksPayload.ID,
				com.example.tudursvehiclemod.network.ReleaseAllCarrierLocksPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(WingFoldTogglePayload.ID, WingFoldTogglePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(VerticalLevelInputPayload.ID, VerticalLevelInputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SwitchSeatPayload.ID, SwitchSeatPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SwitchSeatPreviousPayload.ID, SwitchSeatPreviousPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ToggleWeaponModePayload.ID, ToggleWeaponModePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ToggleSearchLightPayload.ID, ToggleSearchLightPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DeployFlaresPayload.ID, DeployFlaresPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ToggleNavLightsPayload.ID, ToggleNavLightsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.example.tudursvehiclemod.network.BrakeInputPayload.ID,
				com.example.tudursvehiclemod.network.BrakeInputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(VtolModeTogglePayload.ID, VtolModeTogglePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TvMissileControlStartPayload.ID, TvMissileControlStartPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TvMissileControlEndPayload.ID, TvMissileControlEndPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(TvMissileInputPayload.ID, TvMissileInputPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RemoteControlStartPayload.ID, RemoteControlStartPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RemoteControlEndPayload.ID, RemoteControlEndPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RemoteControlVehicleTransformPayload.ID, RemoteControlVehicleTransformPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.example.tudursvehiclemod.network.DroneCenterConfigOpenPayload.ID,
				com.example.tudursvehiclemod.network.DroneCenterConfigOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.example.tudursvehiclemod.network.DummyPilotConfigOpenPayload.ID,
				com.example.tudursvehiclemod.network.DummyPilotConfigOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.example.tudursvehiclemod.network.DroneCenterAutoDisableModeOpenPayload.ID,
				com.example.tudursvehiclemod.network.DroneCenterAutoDisableModeOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.example.tudursvehiclemod.network.DummyPilotWeaponConfigOpenPayload.ID,
				com.example.tudursvehiclemod.network.DummyPilotWeaponConfigOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.example.tudursvehiclemod.network.DroneWaypointsOpenPayload.ID,
				com.example.tudursvehiclemod.network.DroneWaypointsOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.example.tudursvehiclemod.network.GroundWaypointsOpenPayload.ID,
				com.example.tudursvehiclemod.network.GroundWaypointsOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.example.tudursvehiclemod.network.DroneRouteBookOpenPayload.ID,
				com.example.tudursvehiclemod.network.DroneRouteBookOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.example.tudursvehiclemod.network.DroneStickNameOpenPayload.ID,
				com.example.tudursvehiclemod.network.DroneStickNameOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(
				com.example.tudursvehiclemod.network.StationMenuOpenPayload.ID,
				com.example.tudursvehiclemod.network.StationMenuOpenPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(HatchTogglePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$tryToggleHatch();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(WingFoldTogglePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tryToggleWingFold();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(GearTogglePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.toggleLandingGear();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneCenterConfigUpdatePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity
							&& context.player().getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
						blockEntity.tudursvehiclemod$setConfig(serverWorld, payload.speedFraction(), payload.orbitAltitude(), payload.radiusMultiplier());
						if (blockEntity.tudursvehiclemod$isRedstoneControlEnabled() != payload.redstoneControlEnabled()) {
							blockEntity.tudursvehiclemod$setRedstoneControlEnabled(serverWorld, payload.redstoneControlEnabled());
						}
						if (blockEntity.tudursvehiclemod$isActive() != payload.active()) {
							blockEntity.tudursvehiclemod$toggleActive(serverWorld);
						}
						blockEntity.tudursvehiclemod$decodeAndApplyFormationConfig(payload.formationConfig());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DummyPilotConfigRequestPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity) {
						ServerPlayNetworking.send(context.player(),
								new com.example.tudursvehiclemod.network.DummyPilotConfigOpenPayload(
										payload.x(), payload.y(), payload.z(),
										blockEntity.tudursvehiclemod$isDummyPilotEnabled(),
										blockEntity.tudursvehiclemod$getDummyPilotSkinId(),
										com.example.tudursvehiclemod.asset.DummyPilotSkinRegistry.tudursvehiclemod$getAvailableSkinIds(),
										blockEntity.tudursvehiclemod$isDummyPilotNameVisible(),
										blockEntity.tudursvehiclemod$getDummyPilotName()));
						// Per DroneCenterAutoDisableModeOpenPayload's own doc: sent as a separate payload since DummyPilotConfigOpenPayload above has no room left for a 13th field (before this screen's own weapon settings moved out to their own sub-screen/payload).
						ServerPlayNetworking.send(context.player(),
								new com.example.tudursvehiclemod.network.DroneCenterAutoDisableModeOpenPayload(
										payload.x(), payload.y(), payload.z(),
										blockEntity.tudursvehiclemod$getAutoDisableResumeMode()
												== com.example.tudursvehiclemod.block.DroneCenterBlockEntity.AutoDisableResumeMode.AUTO_RESUME));
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneCenterAutoDisableModeUpdatePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity) {
						blockEntity.tudursvehiclemod$setAutoDisableResumeMode(payload.autoResume()
								? com.example.tudursvehiclemod.block.DroneCenterBlockEntity.AutoDisableResumeMode.AUTO_RESUME
								: com.example.tudursvehiclemod.block.DroneCenterBlockEntity.AutoDisableResumeMode.MANUAL_REENABLE);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DummyPilotConfigUpdatePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity
							&& context.player().getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
						// skinId/name are resolved/capped (not trusted) inside setDummyPilotConfig - see DummyPilotConfigUpdatePayload's own doc.
						blockEntity.tudursvehiclemod$setDummyPilotConfig(serverWorld, payload.enabled(), payload.skinId(),
								payload.nameVisible(), payload.name());
					}
				}));

		// This request's own handler builds the weapon list the same way DummyPilotConfigRequestPayload's own handler used to (see network.DummyPilotWeaponConfigOpenPayload's own doc - the vehicle's own weapon list is server-side data the client can't look up on its own).
		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DummyPilotWeaponConfigRequestPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity) {
						com.example.tudursvehiclemod.entity.AbstractVehicleEntity boundVehicle =
								com.example.tudursvehiclemod.block.DroneCenterBlockEntity.tudursvehiclemod$findBoundVehicle(
										(net.minecraft.server.world.ServerWorld) context.player().getEntityWorld(),
										blockEntity.tudursvehiclemod$getBoundVehicleId());
						java.util.List<String> weaponDisplayNames = new java.util.ArrayList<>();
						if (boundVehicle != null) {
							for (com.example.tudursvehiclemod.asset.WeaponDefinition weapon : boundVehicle.getDefinition().weapons()) {
								weaponDisplayNames.add(com.example.tudursvehiclemod.asset.WeaponStatsLoader.get(weapon.weaponName()).displayName());
							}
						}
						ServerPlayNetworking.send(context.player(),
								new com.example.tudursvehiclemod.network.DummyPilotWeaponConfigOpenPayload(
										payload.x(), payload.y(), payload.z(),
										weaponDisplayNames,
										blockEntity.tudursvehiclemod$getDummyPilotWeaponIndex(),
										(float) blockEntity.tudursvehiclemod$getDummyPilotCasAttackStartAltitude(),
										(float) blockEntity.tudursvehiclemod$getDummyPilotCasAttackStopAltitude(),
										(float) blockEntity.tudursvehiclemod$getDummyPilotSearchRange(),
										(float) blockEntity.tudursvehiclemod$getDummyPilotDiveTargetYOffset()));
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DummyPilotWeaponConfigUpdatePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity
							&& context.player().getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
						// Per that same payload's own doc: weaponIndex is re-validated against the bound vehicle's own actual current weapon list, rather than trusted as-is.
						com.example.tudursvehiclemod.entity.AbstractVehicleEntity boundVehicle =
								com.example.tudursvehiclemod.block.DroneCenterBlockEntity.tudursvehiclemod$findBoundVehicle(
										serverWorld, blockEntity.tudursvehiclemod$getBoundVehicleId());
						int validatedWeaponIndex = (boundVehicle != null
								&& payload.weaponIndex() >= 0 && payload.weaponIndex() < boundVehicle.getDefinition().weapons().size())
								? payload.weaponIndex() : -1;
						blockEntity.tudursvehiclemod$setDummyPilotCombatConfig(validatedWeaponIndex,
								payload.attackStartAltitude(), payload.attackStopAltitude(), payload.searchRange(), payload.diveTargetYOffset());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneWaypointsUpdatePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity) {
						java.util.List<com.example.tudursvehiclemod.block.DroneWaypoint> waypoints = new java.util.ArrayList<>();
						if (!payload.encodedWaypoints().isEmpty()) {
							for (String part : payload.encodedWaypoints().split(";")) {
								com.example.tudursvehiclemod.block.DroneWaypoint decoded =
										com.example.tudursvehiclemod.block.DroneWaypoint.tudursvehiclemod$decode(part);
								if (decoded != null) {
									waypoints.add(decoded);
								}
							}
						}
						blockEntity.tudursvehiclemod$setWaypoints(waypoints);
						if (!payload.encodedHomePoint().isEmpty()) {
							com.example.tudursvehiclemod.block.DroneWaypoint decodedHome =
									com.example.tudursvehiclemod.block.DroneWaypoint.tudursvehiclemod$decode(payload.encodedHomePoint());
							if (decodedHome != null) {
								blockEntity.tudursvehiclemod$setHomePoint(decodedHome);
							}
						}
						// Empty means "no via-point configured" - clears it explicitly, rather than leaving a stale one behind, matching how a player would actually delete it from the screen.
						blockEntity.tudursvehiclemod$setReturnViaPoint(payload.encodedViaPoint().isEmpty()
								? null : com.example.tudursvehiclemod.block.DroneWaypoint.tudursvehiclemod$decode(payload.encodedViaPoint()));
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneWaypointsRequestPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity) {
						// Decides here, from the bound vehicle's own type, which of the two "open" payloads to answer with (see block.DroneCenterBlockEntity's own tudursvehiclemod$usesGroundRoute() doc). The client sends the SAME request payload either way - it doesn't need to know the vehicle's type at all, and the aircraft path below is byte-for-byte what it always was.
						com.example.tudursvehiclemod.entity.AbstractVehicleEntity bound =
								context.player().getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld
										? com.example.tudursvehiclemod.block.DroneCenterBlockEntity.tudursvehiclemod$findBoundVehicle(
												serverWorld, blockEntity.tudursvehiclemod$getBoundVehicleId())
										: null;
						if (com.example.tudursvehiclemod.block.DroneCenterBlockEntity.tudursvehiclemod$usesGroundRoute(bound)) {
							java.util.List<com.example.tudursvehiclemod.block.GroundWaypoint> groundWaypoints = blockEntity.tudursvehiclemod$getGroundWaypoints();
							StringBuilder groundSb = new StringBuilder();
							for (int i = 0; i < groundWaypoints.size(); i++) {
								if (i > 0) {
									groundSb.append(';');
								}
								groundSb.append(groundWaypoints.get(i).tudursvehiclemod$encode());
							}
							net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(context.player(),
									new com.example.tudursvehiclemod.network.GroundWaypointsOpenPayload(
											payload.x(), payload.y(), payload.z(), groundSb.toString()));
							return;
						}
						java.util.List<com.example.tudursvehiclemod.block.DroneWaypoint> waypoints = blockEntity.tudursvehiclemod$getWaypoints();
						StringBuilder sb = new StringBuilder();
						for (int i = 0; i < waypoints.size(); i++) {
							if (i > 0) {
								sb.append(';');
							}
							sb.append(waypoints.get(i).tudursvehiclemod$encode());
						}
						net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(context.player(),
								new com.example.tudursvehiclemod.network.DroneWaypointsOpenPayload(
										payload.x(), payload.y(), payload.z(), sb.toString(),
										blockEntity.tudursvehiclemod$getHomePoint().tudursvehiclemod$encode(),
										blockEntity.tudursvehiclemod$getReturnViaPoint() != null
												? blockEntity.tudursvehiclemod$getReturnViaPoint().tudursvehiclemod$encode() : ""));
					}
				}));

		// The ground counterpart of the DroneWaypointsUpdatePayload receiver just above. No Home Point handling - see GroundWaypointsOpenPayload's own doc for why a surface route has none.
		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.GroundWaypointsUpdatePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity) {
						java.util.List<com.example.tudursvehiclemod.block.GroundWaypoint> waypoints = new java.util.ArrayList<>();
						if (!payload.encodedWaypoints().isEmpty()) {
							for (String part : payload.encodedWaypoints().split(";")) {
								com.example.tudursvehiclemod.block.GroundWaypoint decoded =
										com.example.tudursvehiclemod.block.GroundWaypoint.tudursvehiclemod$decode(part);
								if (decoded != null) {
									waypoints.add(decoded);
								}
							}
						}
						blockEntity.tudursvehiclemod$setGroundWaypoints(waypoints);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneRouteBookUpdatePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.Hand hand = payload.mainHand() ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND;
					net.minecraft.item.ItemStack stack = context.player().getStackInHand(hand);
					if (stack.getItem() instanceof com.example.tudursvehiclemod.item.DroneRouteBookItem) {
						java.util.List<com.example.tudursvehiclemod.block.DroneRouteBookWaypoint> waypoints = new java.util.ArrayList<>();
						if (!payload.encodedWaypoints().isEmpty()) {
							for (String part : payload.encodedWaypoints().split(";")) {
								com.example.tudursvehiclemod.block.DroneRouteBookWaypoint decoded =
										com.example.tudursvehiclemod.block.DroneRouteBookWaypoint.tudursvehiclemod$decode(part);
								if (decoded != null) {
									waypoints.add(decoded);
								}
							}
						}
						com.example.tudursvehiclemod.item.DroneRouteBookItem.tudursvehiclemod$setWaypoints(stack, waypoints);
						int offsetX = 0, offsetY = 0, offsetZ = 0;
						if (!payload.encodedOffset().isEmpty()) {
							String[] offsetParts = payload.encodedOffset().split(",", -1);
							if (offsetParts.length == 3) {
								try {
									offsetX = Integer.parseInt(offsetParts[0].strip());
									offsetY = Integer.parseInt(offsetParts[1].strip());
									offsetZ = Integer.parseInt(offsetParts[2].strip());
								} catch (NumberFormatException ignored) {
								}
							}
						}
						com.example.tudursvehiclemod.item.DroneRouteBookItem.tudursvehiclemod$setOffset(stack, offsetX, offsetY, offsetZ);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.StationToggleRemoteControlPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (context.player().getEntityWorld() instanceof ServerWorld serverWorld) {
						net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
						com.example.tudursvehiclemod.block.StationBlock.tudursvehiclemod$handleToggleRemoteControl(serverWorld, context.player(), pos);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.OpenBlockSlotsPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					net.minecraft.block.entity.BlockEntity blockEntity = context.player().getEntityWorld().getBlockEntity(pos);
					if (blockEntity instanceof net.minecraft.screen.NamedScreenHandlerFactory factory
							&& (blockEntity instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity
							|| blockEntity instanceof com.example.tudursvehiclemod.block.StationBlockEntity)) {
						context.player().openHandledScreen(factory);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.OpenDroneCenterFormationPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity) {
						context.player().openHandledScreen(new net.minecraft.screen.NamedScreenHandlerFactory() {
							@Override
							public net.minecraft.text.Text getDisplayName() {
								return blockEntity.getDisplayName();
							}

							@Override
							public net.minecraft.screen.ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity player) {
								return new com.example.tudursvehiclemod.screen.DroneCenterFormationScreenHandler(syncId, playerInventory, blockEntity, pos);
							}
						});
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneCenterFormationConfigUpdatePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity) {
						blockEntity.tudursvehiclemod$decodeAndApplyFormationConfig(payload.formationConfig());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneCenterBasicConfigUpdatePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
					if (context.player().getEntityWorld().getBlockEntity(pos)
							instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity blockEntity
							&& context.player().getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
						blockEntity.tudursvehiclemod$setConfig(serverWorld, payload.speedFraction(), payload.orbitAltitude(), payload.radiusMultiplier());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DroneStickRegisterPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.util.Hand hand = payload.mainHand() ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND;
					net.minecraft.item.ItemStack stack = context.player().getStackInHand(hand);
					if (!(stack.getItem() instanceof com.example.tudursvehiclemod.item.DroneControlStickItem)) {
						return;
					}
					java.util.UUID vehicleId;
					try {
						vehicleId = java.util.UUID.fromString(payload.vehicleId());
					} catch (IllegalArgumentException e) {
						return;
					}
					if (!(context.player().getEntityWorld().getEntity(vehicleId) instanceof AbstractVehicleEntity vehicle)) {
						return;
					}
					com.example.tudursvehiclemod.item.DroneControlStickItem.setRegisteredVehicleId(stack, vehicle.getUuid());
					// Always overwrites the custom name with whatever the player just typed, unconditionally.
					String customName = payload.customName().strip();
					if (!customName.isEmpty()) {
						stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal(customName));
					}
					context.player().sendMessage(net.minecraft.text.Text.translatable(
							"item.tudursvehiclemod.drone_control_stick.registered_message", customName), true);
				}));

		ServerPlayNetworking.registerGlobalReceiver(VtolModeTogglePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (context.player().getVehicle() instanceof com.example.tudursvehiclemod.entity.VtolEntity vtol) {
						vtol.tudursvehiclemod$tryToggleVtolMode();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(ThrottleInputPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.setSyncedThrottleInput(payload.forwardInput());
						vehicle.setSyncedSidewaysInput(payload.sidewaysInput());
					}
				}));

		// Applies the client's own correctly-tracked position directly, bypassing vanilla's own apparently-unreliable-for-slow-vehicles passenger-position-sync entirely. Only ever applied while the player is actually confirmed riding a real AbstractVehicleEntity server-side - never trusts this payload's own coordinates otherwise, so a stale/late packet arriving after dismounting (or any other mismatch) can't move the player anywhere unexpected.
		ServerPlayNetworking.registerGlobalReceiver(com.example.tudursvehiclemod.network.PassengerPositionSyncPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (context.player().getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity) {
						context.player().setPosition(payload.x(), payload.y(), payload.z());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(VerticalLevelInputPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.setSyncedLevelAscendInput(payload.ascending());
						vehicle.setSyncedLevelDescendInput(payload.descending());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(com.example.tudursvehiclemod.network.BrakeInputPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.setSyncedBrakeInput(payload.braking());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(ManualModePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.setManualMode(payload.enabled());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(OpenVehicleMenuPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					// Gated on actually being mounted right now.
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						// This Mod menu (reload/
						// repair/etc. - see AbstractVehicleEntity's own
						// createMenu() doc) must be entirely unreachable
						// while the requesting player is remote-
						// controlling this exact vehicle right now (see
						// tudursvehiclemod$getRemoteControllerId()'s own
						// doc) - whether this vehicle is a UAV-dedicated
						// one or an ordinary, boardable one currently
						// just being remote-operated makes no difference
						// at all; an ACTUAL, ordinary rider (never
						// remote-controlling at all) still opens this
						// completely normally, unaffected.
						java.util.UUID remoteControllerId = vehicle.tudursvehiclemod$getRemoteControllerId();
						if (remoteControllerId != null && remoteControllerId.equals(context.player().getUuid())) {
							return;
						}
						context.player().openHandledScreen(vehicle);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.ToggleDroneRecordingPayload.ID, (payload, context) ->
				context.server().execute(() -> com.example.tudursvehiclemod.DroneRecordingManager.toggle(context.player())));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.ToggleCarrierSeatPayload.ID, (payload, context) ->
				context.server().execute(() -> com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$toggleCarrierSeat(context.player())));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.ToggleCarrierLockModePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$toggleCarrierLockMode();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DeployParachutePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					net.minecraft.server.network.ServerPlayerEntity deployPlayer = context.player();
					if (deployPlayer.getVehicle() instanceof com.example.tudursvehiclemod.entity.ParachuteEntity) {
						return;
					}
					if (!(deployPlayer.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).getItem()
							instanceof com.example.tudursvehiclemod.item.ParachuteItem)) {
						return;
					}
					if (!com.example.tudursvehiclemod.entity.ParachuteManager.tudursvehiclemod$isDeployable(deployPlayer)) {
						return;
					}
					if (deployPlayer.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld deployServerWorld) {
						int deployColor = net.minecraft.component.type.DyedColorComponent.getColor(
								deployPlayer.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST), 0xFFFFFF) | 0xFF000000;
						deployPlayer.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.item.ItemStack.EMPTY);
						com.example.tudursvehiclemod.entity.ParachuteEntity.tudursvehiclemod$spawnAndMount(
								deployPlayer, deployServerWorld, deployPlayer.getEntityPos(), deployColor, true);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.EjectSeatPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$tryEjectSeat(context.player());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.ParachuteJumpPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$tryParachuteJump(context.player());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.MobDropTriggerPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					org.slf4j.LoggerFactory.getLogger("VehicleMod/Entity").info(
							"[ParachuteDebug] MobDropTriggerPayload received from {}", context.player().getName().getString());
					Entity effectiveVehicle = com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player());
					if (effectiveVehicle instanceof AbstractVehicleEntity vehicle) {
						int seatIndex = vehicle.tudursvehiclemod$getAssignedSeatIndex(context.player());
						org.slf4j.LoggerFactory.getLogger("VehicleMod/Entity").info(
								"[ParachuteDebug] MobDropTriggerPayload: effective vehicle is {}, requester's own assigned seat index={} (must be 0 to proceed)",
								vehicle.getClass().getSimpleName(), seatIndex);
						if (seatIndex == 0) {
							vehicle.tudursvehiclemod$tryTriggerMobDrop();
						}
					} else {
						org.slf4j.LoggerFactory.getLogger("VehicleMod/Entity").info(
								"[ParachuteDebug] MobDropTriggerPayload: tudursvehiclemod$getEffectiveVehicle() returned {} (not an AbstractVehicleEntity at all)",
								effectiveVehicle == null ? "null" : effectiveVehicle.getClass().getSimpleName());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.DismountAllMobsPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle
							&& vehicle.tudursvehiclemod$getAssignedSeatIndex(context.player()) == 0) {
						vehicle.tudursvehiclemod$tryTriggerMobDrop();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.ForceDismountAllSeatsPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$tryForceDismountAllSeats(context.player());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.LockCarrierTargetPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$tryLockCarrierTarget(context.player());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(
				com.example.tudursvehiclemod.network.ReleaseAllCarrierLocksPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$releaseAllCarrierLocks();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(FireWeaponPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tryFireWeapon(payload.weaponIndex(), context.player());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(SelectWeaponPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$setSelectedWeaponIndex(payload.weaponIndex());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(FreeLookPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					// Only the controlling passenger's own client should be able to change this.
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.setFreeLook(payload.freeLook());
						// A previous version tried to snap the pilot's own view back onto the vehicle's heading here (via ServerPlayerEntity#teleport) the instant free-look ended, to..
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(DescendPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.setDescending(payload.descending());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(AircraftOrientationInputPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					// VtolEntity's own aircraft mode
					// needs this exact same handling as AircraftEntity -
					// see AbstractVehicleEntity's own
					// tudursvehiclemod$usesAircraftStyleOrientation() doc
					// for why this used to only ever check AircraftEntity
					// alone.
					var vehicle = context.player().getVehicle();
					if (vehicle instanceof com.example.tudursvehiclemod.entity.AircraftEntity aircraft) {
						aircraft.pendingYawInput += payload.yawDelta();
						aircraft.pendingPitchInput += payload.pitchDelta();
					} else if (vehicle instanceof com.example.tudursvehiclemod.entity.VtolEntity vtol) {
						vtol.pendingYawInput += payload.yawDelta();
						vtol.pendingPitchInput += payload.pitchDelta();
					} else if (vehicle instanceof com.example.tudursvehiclemod.entity.HelicopterEntity helicopter && helicopter.isManualMode()) {
						helicopter.pendingYawInput += payload.yawDelta();
						helicopter.pendingPitchInput += payload.pitchDelta();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(SelectVehiclePayload.ID, (payload, context) ->
				context.server().execute(() -> handleSelectVehicle(payload, context.player())));

		ServerPlayNetworking.registerGlobalReceiver(SwitchSeatPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$trySwitchSeat(context.player(), true);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(SwitchSeatPreviousPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$trySwitchSeat(context.player(), false);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(ToggleWeaponModePayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$tryToggleWeaponMode(vehicle.tudursvehiclemod$getSelectedWeaponIndex());
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(ToggleSearchLightPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$toggleSearchLight();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(DeployFlaresPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$deployFlares();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(ToggleNavLightsPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(context.player()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$toggleNavLights();
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(TvMissileInputPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (context.player().getEntityWorld() instanceof ServerWorld serverWorld) {
						var missile = com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity
								.tudursvehiclemod$getControlledMissile(serverWorld, context.player().getId());
						if (missile != null) {
							missile.pendingTvYawInput += payload.yawDelta();
							missile.pendingTvPitchInput += payload.pitchDelta();
						}
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(RequestSeatResyncPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					if (context.player().getEntityWorld() instanceof ServerWorld requestServerWorld
							&& requestServerWorld.getEntityById(payload.vehicleEntityId()) instanceof AbstractVehicleEntity vehicle) {
						vehicle.tudursvehiclemod$resyncSeatsForNewlyJoinedPlayer(context.player());
					}
				}));
	}

	/** Re-validates everything from scratch rather than trusting the client's screen contents: the stack in the claimed hand really is a TieredVehicleSpawnerItem, the.. */
	private static void handleSelectVehicle(SelectVehiclePayload payload, ServerPlayerEntity player) {
		Hand hand = payload.mainHand() ? Hand.MAIN_HAND : Hand.OFF_HAND;
		ItemStack stack = player.getStackInHand(hand);
		if (!(stack.getItem() instanceof TieredVehicleSpawnerItem spawner)) {
			return;
		}

		Optional<VehicleDefinition> maybeDef = VehicleRegistry.get(payload.vehicleId());
		if (maybeDef.isEmpty()) {
			return;
		}
		VehicleDefinition def = maybeDef.get();

		if (!def.entityType().equals(spawner.getCategory().entityTypeId())) {
			return;
		}
		if (def.tier() > spawner.getTier()) {
			return;
		}

		ServerWorld world = (ServerWorld) player.getEntityWorld();
		EntityType<?> entityType = Registries.ENTITY_TYPE.get(def.entityType());

		Vec3d eyePos = player.getEyePos();
		Vec3d spawnPos = eyePos.add(player.getRotationVec(1.0f).multiply(3.0));

		Entity entity = entityType.create(world, spawned -> {
			if (spawned instanceof AbstractVehicleEntity vehicle) {
				vehicle.setVehicleDefinitionId(payload.vehicleId());
			}
		}, net.minecraft.util.math.BlockPos.ofFloored(spawnPos), SpawnReason.SPAWN_ITEM_USE, false, false);

		if (entity == null) {
			return;
		}
		// Snapped to the nearest cardinal direction (multiple of 90 degrees) rather than the player's exact, arbitrary look angle.
		float spawnYaw = Math.round(player.getYaw() / 90f) * 90f;
		entity.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, spawnYaw, 0);
		world.spawnEntity(entity);

		TieredVehicleSpawnerItem.consumeOne(player, hand);
	}
}
