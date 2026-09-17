package com.example.tudursvehiclemod.registry;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.entity.AircraftEntity;
import com.example.tudursvehiclemod.entity.CarEntity;
import com.example.tudursvehiclemod.entity.HelicopterEntity;
import com.example.tudursvehiclemod.entity.ShipEntity;
import com.example.tudursvehiclemod.entity.StaticEmplacementEntity;
import com.example.tudursvehiclemod.entity.SubmarineEntity;
import com.example.tudursvehiclemod.entity.VtolEntity;
import com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** Registers one EntityType per *movement model* (helicopter, car, boat-like,..), not one per visual vehicle. */
public class ModEntityTypes {

	public static EntityType<HelicopterEntity> HELICOPTER;
	public static EntityType<CarEntity> CAR;
	public static EntityType<ShipEntity> SHIP;
	public static EntityType<SubmarineEntity> SUBMARINE;
	public static EntityType<AircraftEntity> AIRCRAFT;
	public static EntityType<VtolEntity> VTOL;
	public static EntityType<StaticEmplacementEntity> STATIC_EMPLACEMENT;
	/** Replaces the previous status-effect-based parachute mechanism with a real ridden entity - see entity.ParachuteEntity's own doc. */
	public static EntityType<com.example.tudursvehiclemod.entity.ParachuteEntity> PARACHUTE;
	public static EntityType<VehicleProjectileEntity> VEHICLE_PROJECTILE;
	public static EntityType<com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity> VEHICLE_MODEL_PROJECTILE;
	public static EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity> CARRIER_RUNWAY_PLATFORM;
	public static EntityType<com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity> CARRIER_RUNWAY_BORDER_PLATFORM;
	/** "Dummy pilot" option on the Drone Center - see DummyPilotEntity's own doc. Sized to match a vanilla player (0.6 x 1.8), since it renders with that same model and sits in seats laid out for players. */
	public static EntityType<com.example.tudursvehiclemod.entity.DummyPilotEntity> DUMMY_PILOT;

	/** Since EntityType dimensions are fixed once at registration (LivingEntity's own getDimensions() is confirmed final - see CarrierRunwayPlatformEntity's own doc), no single entity TYPE can adapt to a runway it hasn't been told about yet. Registering several DISCRETE size variants instead lets tudursvehiclemod$selectCarrierRunwayTileType() (AbstractVehicleEntity) choose, per runway, whichever registered size best fits THAT runway's own width - a coarser approximation of "size follows width" than truly continuous scaling, but a close one in practice.
	 *
	 * All share the exact same CarrierRunwayPlatformEntity class and (per CarrierRunwayPlatformEntityRenderer's own doc) the exact same no-op renderer - registering more variants carries no rendering cost or behavioral difference beyond the EntityType's own registered size, so there's no real downside to having several.
	 *
	 * The ORIGINAL "carrier_runway_platform" id is kept exactly as it always was (still the largest size, still CARRIER_RUNWAY_PLATFORM itself) rather than renamed into this array - Minecraft embeds an entity's own registered type id in its saved NBT, so renaming it would leave any tile already persisted in an existing world unresolvable on reload. Each SMALLER size gets its own new, additional id instead; nothing about an existing save changes at all.
	 *
	 * 6.0 was the smallest before this addition - the confirmed-safe size from the border tile's own history (see CARRIER_RUNWAY_BORDER_PLATFORM's own doc for the edge-slip-through issue a LARGER size once caused). 1.0 has been added below that as a genuinely smaller option - unlike 6.0, this specific size has no prior real-world testing history in this project; it fits an extremely narrow runway more precisely, at the cost of needing many more tiles to cover the same length (a long, narrow runway selecting this size could require several hundred tiles - see tudursvehiclemod$selectCarrierRunwayTileType()'s own doc for the safeguard against that). Each step up from there is roughly 1.3x the previous, reaching up to the pre-existing default (30.0 config -> ~14.0 tile) and the original "carrier_runway_platform" id's own historical 28.0-ish upper range. */
	public static final float[] CARRIER_RUNWAY_TILE_SIZES = {1.0f, 6.0f, 8.0f, 11.0f, 14.0f, 18.0f, 23.0f};
	public static final EntityType<?>[] CARRIER_RUNWAY_PLATFORM_SMALLER_VARIANTS = new EntityType<?>[CARRIER_RUNWAY_TILE_SIZES.length];

	public static void register() {
		HELICOPTER = register("helicopter", HelicopterEntity::new, 2.0f, 2.0f, 1);
		CAR = register("car", CarEntity::new, 1.6f, 1.2f, 1);
		SHIP = register("ship", ShipEntity::new, 2.0f, 1.5f, 1);
		SUBMARINE = register("submarine", SubmarineEntity::new, 2.0f, 1.5f, 1);
		AIRCRAFT = register("aircraft", AircraftEntity::new, 2.2f, 1.4f, 1);
		VTOL = register("vtol", VtolEntity::new, 2.2f, 1.4f, 1);
		STATIC_EMPLACEMENT = register("static_emplacement", StaticEmplacementEntity::new, 1.5f, 1.5f, 1);
		// See entity.ParachuteEntity's own doc.
		PARACHUTE = register("parachute", com.example.tudursvehiclemod.entity.ParachuteEntity::new, 0.6f, 0.6f, 1);
		DUMMY_PILOT = register("dummy_pilot", com.example.tudursvehiclemod.entity.DummyPilotEntity::new, 0.6f, 1.8f, 1);
		// Torpedoes (and other VehicleProjectileEntity
		// instances) moving choppily/stutteringly on the client: the default
		// trackingTickInterval (unset here otherwise, so it falls back to
		// EntityType.Builder's own default of several ticks between
		// position syncs) is fine for a projectile on an ordinary,
		// unchanging ballistic arc, but a torpedo's own velocity changes
		// dramatically tick-to-tick during its FALLING->SETTLING->CRUISING
		// phases (see VehicleProjectileEntity's own tudursvehiclemod$
		// updateUnderwaterCruise() doc) - syncing that only every few
		// ticks made the client's own interpolation visibly jump between
		// sparse, very different samples instead of smoothly following
		// the actual path. Syncing every tick instead fixes this.
		VEHICLE_PROJECTILE = register("vehicle_projectile", VehicleProjectileEntity::new, 0.25f, 0.25f, 1);
		VEHICLE_MODEL_PROJECTILE = register("vehicle_model_projectile",
				com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity::new, 0.25f, 0.25f, 1);
		// A flat, wide tile - 1.0 wide (matching the spacing this platform grid is tiled at) x 0.5 tall (thin, floor-like, rather than a tall box) since this only ever needs to offer a top SURFACE to stand on, not occupy meaningful vertical space itself.
		// Explicitly syncs every tick, same as VEHICLE_PROJECTILE above (for an unrelated reason) - a long default interval before this entity's own very first tracking update reaches a newly-in-range client is one plausible explanation for exactly this "invisible until something else forces a resync" pattern.
		// DIRECT test confirming push-based collision genuinely requires a LivingEntity (not just isPushable()=true on a bare Entity) - see CarrierRunwayPlatformEntity's own doc for the full reasoning: back to a LivingEntity-based design, which in turn means a FIXED size (LivingEntity's own getDimensions()/calculateBoundingBox() are both confirmed final, no per-instance sizing is possible at all) - tiled in a 2-D grid (both across a runway's own width AND along its own length) by AbstractVehicleEntity's own runway-tiling logic, rather than one row of custom-width tiles.
		// Tile size derived from VehicleModServerConfig's own carrierRunwayExpectedWidth (3 tiles across width, with overlap) rather than hardcoded.
		float carrierRunwayTileSize = (float) (com.example.tudursvehiclemod.VehicleModServerConfig.get().carrierRunwayExpectedWidth / 3.0 * 1.4);
		// trackingTickInterval raised from 1 to 4 to reduce per-tick network sync load for a Carrier with many runway tiles.
		int carrierRunwayTilePlatformTrackingInterval = 4;
		CARRIER_RUNWAY_PLATFORM = register("carrier_runway_platform",
				com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity::new, carrierRunwayTileSize, 1.0f, carrierRunwayTilePlatformTrackingInterval);
		// Per CARRIER_RUNWAY_TILE_SIZES' own doc: additional, smaller size variants under their own NEW ids - the original "carrier_runway_platform" id above is untouched, so an existing save's own already-persisted tiles resolve exactly as they always did.
		for (int i = 0; i < CARRIER_RUNWAY_TILE_SIZES.length; i++) {
			CARRIER_RUNWAY_PLATFORM_SMALLER_VARIANTS[i] = register("carrier_runway_platform_small_" + i,
					com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity::new, CARRIER_RUNWAY_TILE_SIZES[i], 1.0f, carrierRunwayTilePlatformTrackingInterval);
		}
		// Only ever a 1-2 block range, and traced to the large interior tile size above (14.0-ish) apparently exceeding some entity-lookup broad-phase assumption - fixed at 6.0 (the known-safe size confirmed used before the tile-size-increase that introduced this) rather than derived from config, since this is specifically meant to stay small/safe regardless of the interior tile's own size. Same CarrierRunwayPlatformEntity class (its own logic doesn't depend on which EntityType constructed it), spawned only along the runway's own outer edge as a thin border strip - see AbstractVehicleEntity's own tudursvehiclemod$updateCarrierRunwayBorderPlatform() doc.
		CARRIER_RUNWAY_BORDER_PLATFORM = register("carrier_runway_border_platform",
				com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity::new, 6.0f, 1.0f, carrierRunwayTilePlatformTrackingInterval);
	}

	private static <T extends net.minecraft.entity.Entity> EntityType<T> register(
			String path, EntityType.EntityFactory<T> factory, float width, float height) {
		return register(path, factory, width, height, null);
	}

	/** Registers a vehicle entity type for an ADDON mod, under that addon's own namespace, with the same tracking setup every built-in vehicle type here uses.
	 *
	 * An addon's own AbstractVehicleEntity subclass is spawned through exactly the same path a built-in one is: a vehicle JSON's own "entity_type" is resolved against the entity registry at spawn time, so any type registered here works without this class needing to know about it.
	 *
	 * Call this from the addon's own ModInitializer, and register a renderer for it client-side (VehicleEntityRenderer::new handles any AbstractVehicleEntity subclass). Note that a category-scoped spawner ITEM is only provided for the built-in categories (see item.VehicleCategory) - an addon type is spawned via its own item, command, or whatever mechanism the addon itself provides. */
	public static <T extends net.minecraft.entity.Entity> EntityType<T> registerAddonVehicleType(
			Identifier id, EntityType.EntityFactory<T> factory, float width, float height) {
		return registerAt(id, factory, width, height, null);
	}

	private static <T extends net.minecraft.entity.Entity> EntityType<T> register(
			String path, EntityType.EntityFactory<T> factory, float width, float height, Integer trackingTickInterval) {
		return registerAt(Identifier.of(VehicleMod.MOD_ID, path), factory, width, height, trackingTickInterval);
	}

	private static <T extends net.minecraft.entity.Entity> EntityType<T> registerAt(
			Identifier id, EntityType.EntityFactory<T> factory, float width, float height, Integer trackingTickInterval) {

		RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);

		EntityType.Builder<T> builder = EntityType.Builder.create(factory, SpawnGroup.MISC)
				.dimensions(width, height)
				// 32 chunks - matches the maximum possible vanilla view distance, so these entities stay tracked (known to the client at all, over the network) out to however..
				.maxTrackingRange(32);
		if (trackingTickInterval != null) {
			builder.trackingTickInterval(trackingTickInterval);
		}
		EntityType<T> type = builder.build(key);

		return Registry.register(Registries.ENTITY_TYPE, key, type);
	}
}
