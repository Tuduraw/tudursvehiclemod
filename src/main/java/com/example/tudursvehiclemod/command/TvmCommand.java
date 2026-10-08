package com.example.tudursvehiclemod.command;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

/** Bulk-removes vehicles within the executing player's own currently loaded world. "/tvm clean" targets only DESTROYED vehicles (AbstractVehicleEntity's own tudursvehiclemod$isDestroyed() - a wreck that would otherwise despawn on its own after VehicleModServerConfig's own destroyedVehicleDespawnSeconds anyway, per that field's own doc, so this simply does the exact same thing (Entity.discard()) immediately rather than waiting on the timer); "/tvm clean -a" instead targets EVERY entity belonging to this mod at all, destroyed or not - not just AbstractVehicleEntity and its own subclasses (aircraft/car/ship/submarine/VTOL/static emplacement), but also projectiles, Carrier runway platform tiles, and dummy pilots, identified generically by this mod's own registry namespace (VehicleMod.MOD_ID) rather than enumerating each entity type by hand, so a future new entity type is automatically covered without needing this command updated too. "/tvm clean -b" targets EVERY projectile entity specifically (VehicleProjectileEntity and its own subclasses, e.g. VehicleModelProjectileEntity for OBJ-modeled rockets/missiles) regardless of age/state - a quick way to clear an accumulated mass of in-flight bullets/rockets/missiles without touching any vehicle at all. "/tvm clean -d" and "/tvm clean -d --all" BOTH scan every generated chunk regardless of load state identically, and both hand off to command.WorldDataCleanupTask - see that class's own doc for the full multi-tick, region-file-scanning mechanism, its own periodic progress reporting, and VehicleModServerConfig's own worldDataCleanupChunksPerTick for controlling its speed. The presence/absence of "--all" is now purely about how each found vehicle is handled: "-d" alone RESETS it (a fresh replacement of the exact same vehicle, at the exact same position/rotation - every persisted field defaults, but the vehicle itself is preserved as a presence in the world), "-d --all" DISCARDS it outright (nothing spawned in its place). "/tvm unloadchunks" resets force-loaded chunks - see unloadChunks()'s own doc. */
public final class TvmCommand {

	private TvmCommand() {
	}

	/** /tvm unloadchunks is refused for this long (10 seconds) after the server starts - see unloadChunks(). */
	private static final int UNLOADCHUNKS_MIN_SERVER_TICKS = 200;

	// A box spanning the entire possible world border, matching VehicleMod's own established getEntitiesByClass() pattern (see that class's own resyncAllVehicleAmmo doc) - getEntitiesByClass only ever returns currently-loaded entities regardless of box size, so this naturally satisfies "within loaded range" without any separate chunk-loaded check of its own.
	private static final Box ENTIRE_WORLD = new Box(-30000000, -2048, -30000000, 30000000, 2048, 30000000);

	public static void register() {
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("tvm")
				.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.then(CommandManager.literal("clean")
						.executes(ctx -> cleanDestroyedVehicles(ctx.getSource()))
						.then(CommandManager.literal("-a")
								.executes(ctx -> cleanAllModEntities(ctx.getSource())))
						.then(CommandManager.literal("-b")
								.executes(ctx -> cleanAllProjectiles(ctx.getSource())))
						.then(CommandManager.literal("-d")
								.executes(ctx -> resetAllVehicleData(ctx.getSource()))
								.then(CommandManager.literal("--all")
										.executes(ctx -> cleanAllPersistedVehicleData(ctx.getSource())))))
				.then(CommandManager.literal("unloadchunks")
						.executes(ctx -> unloadChunks(ctx.getSource()))));
	}

	/** "/tvm unloadchunks": resets every dimension's force-loaded chunks to exactly the ones this mod is still using - see ChunkForceTracker.resetForcedChunks()'s own doc. Releases the leftovers (from before removal cleanup was fixed, from earlier sessions - force-loading is saved with the world - and any made with vanilla /forceload or by other mods); keeps whatever an aircraft in flight, an active Drone Center, a Station or a projectile still needs, so they carry on uninterrupted. Everything else is then loaded around players as usual. */
	private static int unloadChunks(ServerCommandSource source) {
		// Right after the world loads, an aircraft, a Drone Center or a mothership may not have asked for its chunks again yet (force-loading is saved with the world, but its bookkeeping isn't, and entities load a little after their chunks) - its chunks would look like leftovers and be released, leaving it stranded in an unloaded chunk.
		if (source.getServer().getTicks() < UNLOADCHUNKS_MIN_SERVER_TICKS) {
			source.sendError(Text.literal("[tvm] The world has only just loaded - wait a few seconds so everything that force-loads chunks has asked for them again, then run this again."));
			return 0;
		}
		int released = 0;
		int kept = 0;
		StringBuilder perWorld = new StringBuilder();
		for (ServerWorld world : source.getServer().getWorlds()) {
			com.example.tudursvehiclemod.ChunkForceTracker.ResetResult result =
					com.example.tudursvehiclemod.ChunkForceTracker.resetForcedChunks(world);
			released += result.released();
			kept += result.kept();
			if (result.released() > 0 || result.kept() > 0) {
				perWorld.append("\n  ").append(world.getRegistryKey().getValue())
						.append(": released ").append(result.released()).append(", kept ").append(result.kept());
			}
		}
		int releasedCount = released;
		int keptCount = kept;
		String details = perWorld.toString();
		source.sendFeedback(() -> Text.literal("[tvm] Released " + releasedCount + " force-loaded chunk(s); kept "
				+ keptCount + " still in use by this mod's aircraft, Drone Centers, Stations or projectiles." + details), true);
		return releasedCount;
	}

	private static int cleanDestroyedVehicles(ServerCommandSource source) {
		ServerWorld world = source.getWorld();
		int removed = 0;
		for (AbstractVehicleEntity vehicle : world.getEntitiesByClass(AbstractVehicleEntity.class, ENTIRE_WORLD, e -> true)) {
			if (vehicle.tudursvehiclemod$isDestroyed()) {
				vehicle.discard();
				removed++;
			}
		}
		int removedCount = removed;
		source.sendFeedback(() -> Text.literal("[tvm] Removed " + removedCount + " destroyed vehicle(s)."), true);
		return removedCount;
	}

	private static int cleanAllModEntities(ServerCommandSource source) {
		ServerWorld world = source.getWorld();
		int removed = 0;
		for (Entity entity : world.getEntitiesByClass(Entity.class, ENTIRE_WORLD, TvmCommand::isModEntity)) {
			entity.discard();
			removed++;
		}
		int removedCount = removed;
		source.sendFeedback(() -> Text.literal("[tvm] Removed " + removedCount + " entity/entities belonging to this mod."), true);
		return removedCount;
	}

	// Bulk-removes every projectile entity (VehicleProjectileEntity and its own subclasses, e.g. VehicleModelProjectileEntity - getEntitiesByClass() naturally includes subclasses too) regardless of age/state, with no effect on any vehicle at all.
	private static int cleanAllProjectiles(ServerCommandSource source) {
		ServerWorld world = source.getWorld();
		int removed = 0;
		for (com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity projectile
				: world.getEntitiesByClass(com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity.class, ENTIRE_WORLD, e -> true)) {
			projectile.discard();
			removed++;
		}
		int removedCount = removed;
		source.sendFeedback(() -> Text.literal("[tvm] Removed " + removedCount + " projectile(s)."), true);
		return removedCount;
	}

	// The only difference now is that each found vehicle is RESET (replaced with a fresh instance of itself) rather than removed outright. See command.WorldDataCleanupTask's own doc for the full mechanism.
	private static int resetAllVehicleData(ServerCommandSource source) {
		WorldDataCleanupTask.startReset(source);
		return 0;
	}

	// Regardless of whether it's currently loaded, with periodic progress reporting and a configurable scan speed - see command.WorldDataCleanupTask's own doc for the full mechanism.
	private static int cleanAllPersistedVehicleData(ServerCommandSource source) {
		WorldDataCleanupTask.startDiscard(source);
		return 0;
	}

	// Identifies ANY entity registered by this mod - checked by registry namespace rather than a hand-enumerated list of classes/EntityTypes, so a future new entity type (a new vehicle category, a new projectile,..) is automatically covered here without this command needing to be updated too.
	private static boolean isModEntity(Entity entity) {
		net.minecraft.util.Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
		return id != null && VehicleMod.MOD_ID.equals(id.getNamespace());
	}
}
