package com.example.tudursvehiclemod;

import com.example.tudursvehiclemod.asset.VehicleDefinitionReloadListener;
import com.example.tudursvehiclemod.item.ModItemGroups;
import com.example.tudursvehiclemod.item.ModItems;
import com.example.tudursvehiclemod.network.ModNetworking;
import com.example.tudursvehiclemod.registry.ModEntityTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VehicleMod implements ModInitializer {

	public static final String MOD_ID = "tudursvehiclemod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static net.minecraft.server.MinecraftServer server;

	@Override
	public void onInitialize() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);

		// WEAPON_AMMO_SYNC (the client-visible synced string) is only ever rewritten on specific events (fire, reload-tick completion,..), never automatically just because the weapon *definitions* themselves got reloaded. Forces every currently-loaded vehicle to re-sync its own (unchanged, still-correct) ammo state to the client right here instead, so the HUD/AmmoPart visibility catch up immediately rather than waiting on the next such event.
		Runnable resyncAllVehicleAmmo = () -> {
			if (server == null) {
				return;
			}
			// A box spanning the entire possible world border, rather than an unverified "iterate every entity" API - getEntitiesByClass is already used elsewhere in this project (see AbstractVehicleEntity's own hit-detection code) and is known to work.
			net.minecraft.util.math.Box entireWorld = new net.minecraft.util.math.Box(
					-30000000, -2048, -30000000, 30000000, 2048, 30000000);
			for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
				for (com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle
						: world.getEntitiesByClass(com.example.tudursvehiclemod.entity.AbstractVehicleEntity.class, entireWorld, e -> true)) {
					vehicle.tudursvehiclemod$syncWeaponAmmo();
				}
			}
		};
		// This is
		// loaded EAGERLY here (which also writes out a default file if
		// none exists yet) rather than lazily on first use - its only
		// other caller is the hit-detection path, which never runs at all
		// until someone actually fires a weapon near a vehicle, so on a
		// fresh install the file could easily never get created.
		VehicleModServerConfig.get();
		LOGGER.info("Server config loaded from {} (parallelHitDetectionAcrossVehicles={})",
				VehicleModServerConfig.getConfigPath(),
				VehicleModServerConfig.get().parallelHitDetectionAcrossVehicles);

		ModEntityTypes.register();
		// A LivingEntity subclass requires its own default attributes registered here, or it crashes the first time one is actually spawned.
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
				ModEntityTypes.CARRIER_RUNWAY_PLATFORM, com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity.createAttributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
				ModEntityTypes.CARRIER_RUNWAY_BORDER_PLATFORM, com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity.createAttributes());
		// CARRIER_RUNWAY_PLATFORM_SMALLER_VARIANTS (the 7 additional, smaller discrete tile sizes used for narrower runways - see that field's own doc) was registered as an EntityType via ModEntityTypes.register() same as the two above, but never had its own default attributes registered here at all - every one of those 7 entity types has been crashing on every single spawn attempt since they were introduced.
		for (net.minecraft.entity.EntityType<?> smallerVariant : ModEntityTypes.CARRIER_RUNWAY_PLATFORM_SMALLER_VARIANTS) {
			@SuppressWarnings("unchecked")
			net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity> typedVariant =
					(net.minecraft.entity.EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity>) smallerVariant;
			net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
					typedVariant, com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity.createAttributes());
		}
		// Another LivingEntity subclass, so it needs its own default attributes here too, for exactly the reason noted above.
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
				ModEntityTypes.DUMMY_PILOT, com.example.tudursvehiclemod.entity.DummyPilotEntity.createAttributes());
		com.example.tudursvehiclemod.registry.ModParticleTypes.register();		ModItems.register();
		// Built-in converter targets, registered before any addon's own initializer can add to the list - see item.VehicleConverterTargets' own ordering doc.
		com.example.tudursvehiclemod.item.VehicleConverterTargets.registerBuiltIn();
		com.example.tudursvehiclemod.item.ModComponents.register();
		com.example.tudursvehiclemod.registry.ModBlocks.register();
		com.example.tudursvehiclemod.registry.ModBlockEntities.register();
		com.example.tudursvehiclemod.registry.ModScreenHandlers.register();
		ModItemGroups.register();
		ModNetworking.register();
		com.example.tudursvehiclemod.block.DroneCenterBlock.tudursvehiclemod$registerUseBlockCallback();
		com.example.tudursvehiclemod.block.StationBlock.tudursvehiclemod$registerUseBlockCallback();
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$registerUseItemCallback();
		com.example.tudursvehiclemod.command.TvmCommand.register();

		// Vehicle *data* (hitbox, speed, seats, which model/texture to use) lives under data/<namespace>/vehicles/*.json so it's available on the dedicated server too..
		ResourceManagerHelper.get(ResourceType.SERVER_DATA)
				.registerReloadListener(new VehicleDefinitionReloadListener());

		// Weapon *stats* (damage, velocity, cooldown, gravity, sound, bullet model) are read live from each weapon's own assets/<namespace>/ weapons/<name>.txt file..
		ResourceManagerHelper.get(ResourceType.SERVER_DATA)
				.registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
					@Override
					public net.minecraft.util.Identifier getFabricId() {
						return net.minecraft.util.Identifier.of(MOD_ID, "weapon_stats");
					}

					@Override
					public void reload(net.minecraft.resource.ResourceManager manager) {
						com.example.tudursvehiclemod.asset.WeaponStatsLoader.reload(manager);
						// Per DummyPilotSkinRegistry's own reload() doc: re-scans the addon folders for dummy pilot skins, so a newly-dropped-in file is selectable without restarting.
						com.example.tudursvehiclemod.asset.DummyPilotSkinRegistry.reload();
						if (server != null) {
							server.execute(resyncAllVehicleAmmo);
						}
					}
				});

		// Drains and runs whatever hit-detection
		// work was batched during this tick as ONE parallel pass (see
		// entity.HitDetectionCoordinator's own doc). A complete no-op
		// unless VehicleModServerConfig's own
		// parallelHitDetectionAcrossVehicles is actually enabled, since
		// nothing ever gets submitted otherwise - so this costs nothing
		// at all on a default-configured server.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server ->
				com.example.tudursvehiclemod.entity.HitDetectionCoordinator.tudursvehiclemod$processPending());
		// A candidate's own physics (thrust, gravity, drag, etc, running as part of its own tick) could still fight/alter the velocity set from WITHIN the mothership's own tick, since the two run in some unpredictable relative order within the same game tick. Running this LAST (after every entity in the world has already fully ticked) avoids that entirely - see AbstractVehicleEntity's own carrierActiveMotherships/carryRunwayDeckEntities() doc.
		//
		// Together with a specific hypothesis ('s own note on this) that this matches the SAME class of bug already fixed for the sinking wreck's own pitch/roll: this mod's own position write happening AFTER vanilla's own per-tick network sync has already captured/sent that tick's "pre-carry" position means the carry adjustment is only reflected in the FOLLOWING tick's own sync - an uneven update cadence client-side interpolation can read as a spike. Moved from END_SERVER_TICK to END_WORLD_TICK: still runs after every entity in this world has fully ticked (preserving the original reason for running late, above), but earlier in the overall tick sequence - closer to (or before, depending on vanilla's own internal ordering between chunk/entity-tracker sync and Fabric's own end-of-tick hooks) this SAME tick's own network sync, rather than guaranteed to run after it. A minimal, easily-reversible change within the existing approach rather than a new render path - if the spike persists, that would indicate this specific ordering theory wasn't the (or the whole) cause.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(world -> {
			java.util.Iterator<com.example.tudursvehiclemod.entity.AbstractVehicleEntity> motherships =
					com.example.tudursvehiclemod.entity.AbstractVehicleEntity.CARRIER_ACTIVE_MOTHERSHIPS.iterator();
			while (motherships.hasNext()) {
				com.example.tudursvehiclemod.entity.AbstractVehicleEntity mothership = motherships.next();
				if (mothership.isRemoved()) {
					motherships.remove();
					continue;
				}
				if (mothership.getEntityWorld() != world) {
					// END_WORLD_TICK fires once per loaded world, so with more than one loaded (e.g. the Nether alongside the Overworld), a mothership registered in a DIFFERENT world than the one this particular callback invocation is for must be skipped this time - it will get its own turn when ITS OWN world's own END_WORLD_TICK fires.
					continue;
				}
				mothership.tudursvehiclemod$carryRunwayDeckEntities();
			}
		});
		// Ticks every currently-recording player once per server tick.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
				com.example.tudursvehiclemod.DroneRecordingManager::onServerTick);
		// With periodic progress reporting and a configurable scan speed - see command.WorldDataCleanupTask's own doc.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
				com.example.tudursvehiclemod.command.WorldDataCleanupTask::onServerTick);
		// Creates every explosion queued this tick by a vehicle's own destruction, on a fresh stack each tick rather than recursively within whichever explosion triggered it.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server ->
				com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$processPendingExplosions());
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			com.example.tudursvehiclemod.entity.HitDetectionCoordinator.tudursvehiclemod$clear();
			// Cleared here too, so leaving a world can never carry entity instances from that session forward into the next one within the same JVM. Without this, a singleplayer player who exits and re-enters worlds repeatedly would accumulate every previous session's own motherships indefinitely - the same strong-reference leak as the per-entity case, just at world granularity instead.
			com.example.tudursvehiclemod.entity.AbstractVehicleEntity.CARRIER_ACTIVE_MOTHERSHIPS.clear();
		});

		// Chunk force-loading "didn't seem to
		// work at all" for block.StationBlockEntity: onPlaced() (where
		// this used to ONLY ever be established) is a PLACEMENT-only
		// hook - it never actually runs again for a station block that
		// instead gets loaded back in from disk (e.g. right after a
		// server restart, or the chunk it's in simply being loaded
		// normally later on) - meaning that station's own permanent
		// chunk ticket was silently never actually requested at all in
		// either of those (extremely common) cases, breaking this
		// entire feature outright well before restart-recovery
		// (StationBlockEntity's own tick() doc) ever even became
		// relevant. BLOCK_ENTITY_LOAD fires whenever this block entity
		// is genuinely loaded into memory for ANY reason at all,
		// placement included - re-requesting the exact same ticket
		// there instead (a harmless, idempotent re-request if it was
		// somehow already active) actually covers every real case.
		// A very large world's own loading
		// stalling out entirely after this feature was added: calling
		// world.setChunkForced() SYNCHRONOUSLY, directly from within
		// this load event's own callback, risked re-entering the chunk-
		// loading system while it's potentially still in the middle of
		// loading/processing THIS EXACT chunk itself (or others) - on a
		// world with many stations scattered across a large area, this
		// could also mean a large burst of MANY simultaneous forced
		// chunk loads/generations firing all at once during startup,
		// compounding the same risk further. Deferred to
		// server.execute() instead - runs at the START of the next
		// server tick, a normal, safe point already used throughout
		// vanilla/Fabric for exactly this kind of "don't act reentrant
		// during a sensitive callback" case - rather than inline, right
		// here, while still potentially deep inside chunk-loading itself.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
			if (blockEntity instanceof com.example.tudursvehiclemod.block.StationBlockEntity station) {
				world.getServer().execute(() -> station.tudursvehiclemod$updateChunkForceLoading(true));
			}
		});

		LOGGER.info("Vehicle framework initialized");
	}
}
