package com.example.tudursvehiclemod;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Multiple AircraftEntity instances flying close together (a formation) each independently tracked and force-loaded their OWN overlapping 3x3 grid of chunks around themselves. Since their grids overlap heavily while flying in formation, one aircraft's own per-tick "unforce whatever chunk just fell out of MY OWN grid" logic had no way to know a DIFFERENT aircraft's own grid still relied on that same, shared chunk - it unforced it anyway, silently undoing the other aircraft's own force-loading.
 *
 * Fixes this by centralizing every force-load request behind simple reference counting, keyed per (world, chunk): a chunk is only ever ACTUALLY force-loaded when its own first requester asks for it, and only ever actually un-forced once its own LAST remaining requester releases it - any number of independent, overlapping requesters (multiple aircraft, a Drone Center, a Station, a Carrier mothership's own grid, etc.) can safely request/release the very same chunk without ever stepping on each other, regardless of ordering.
 *
 * requester is typically "this" from whichever entity/block instance is asking - any Object works, since it's used purely as an identity key. An owner that force-loads several independent things (a Drone Center's own chunk AND its vehicle's grid, say) uses a {@link Purpose} per thing, so releasing one never releases the other where they overlap. Every chunk this mod force-loads goes through here, which is what lets {@link #resetForcedChunks} tell this mod's live requests apart from leftovers. The outer map itself is a WeakHashMap keyed on ServerWorld, and is also cleared when the server stops. */
public final class ChunkForceTracker {
	private static final Map<ServerWorld, Map<ChunkPos, Set<Object>>> REQUESTERS = new WeakHashMap<>();

	private ChunkForceTracker() {
	}

	/** A requester identity for one of several independent things the same owner force-loads. Compared by value, so the same (owner, name) pair can be rebuilt wherever it's needed. The owner (an Entity or a BlockEntity) is what {@link #resetForcedChunks} checks to tell whether the request is still alive. */
	public record Purpose(Object owner, String name) {
	}

	/** Registers requester as needing pos force-loaded - actually force-loads it (with a synchronous getChunk() first, for a genuinely new/never-before-loaded chunk - see AircraftEntity's own earlier doc on why that matters) only if requester turns out to be the very first one currently asking for this exact chunk. Safe to call every tick for the same requester/pos pair - a cheap no-op after the first time, until release() is called for that same pair. */
	public static void request(ServerWorld world, ChunkPos pos, Object requester) {
		request(world, pos, requester, true);
	}

	/** request() without the synchronous getChunk() first - the forced ticket alone loads the chunk in the background. For a block entity's own chunk (already loaded; and called while that very chunk is still being loaded - from a block entity load - getChunk() waits for a load that can't finish until it returns, and the server hangs), and for a Station's/Drone Center's one-off look for its vehicle (which never needed the chunk loaded on the spot, and shouldn't stall a click or a tick loading it). */
	public static void requestWithoutLoading(ServerWorld world, ChunkPos pos, Object requester) {
		request(world, pos, requester, false);
	}

	private static void request(ServerWorld world, ChunkPos pos, Object requester, boolean loadFirst) {
		Set<Object> requesters = REQUESTERS.computeIfAbsent(world, w -> new HashMap<>())
				.computeIfAbsent(pos, p -> new HashSet<>());
		if (requesters.add(requester) && requesters.size() == 1) {
			if (loadFirst) {
				world.getChunk(pos.x, pos.z);
			}
			world.setChunkForced(pos.x, pos.z, true);
		}
	}

	/** Un-registers requester from pos - actually un-forces it only if requester turns out to have been the very last one still asking for this exact chunk. Safe to call even if requester never actually request()ed this exact pos at all (a harmless no-op), which happens routinely as a moving grid naturally releases chunks it no longer covers. */
	public static void release(ServerWorld world, ChunkPos pos, Object requester) {
		Map<ChunkPos, Set<Object>> worldMap = REQUESTERS.get(world);
		if (worldMap == null) {
			return;
		}
		Set<Object> requesters = worldMap.get(pos);
		if (requesters == null || !requesters.remove(requester)) {
			return;
		}
		if (requesters.isEmpty()) {
			worldMap.remove(pos);
			world.setChunkForced(pos.x, pos.z, false);
		}
	}

	/** Releases every chunk requester currently holds in world (an entity only ever needs one world's own bookkeeping released at a time, since it can't simultaneously exist in more than one) - for an entity/block that's being removed/discarded and needs to give up all its own outstanding requests at once, without needing to separately remember exactly which chunks those were. Actually calls setChunkForced(false) for every chunk this requester's own removal empties out. */
	public static void releaseAll(ServerWorld world, Object requester) {
		removeMatching(world, requester::equals, true);
	}

	/** releaseAll() for owner itself and every {@link Purpose} it owns. */
	public static void releaseAllOwnedBy(ServerWorld world, Object owner) {
		removeMatching(world, requester -> ownerOf(requester) == owner, true);
	}

	/** Drops owner's (and its Purposes') bookkeeping WITHOUT un-forcing anything - for an owner that's only being unloaded with its chunk (a server stop, say), not going away: the forced chunks are saved with the world, so it loads back in and requests them again. */
	public static void forgetAllOwnedBy(ServerWorld world, Object owner) {
		removeMatching(world, requester -> ownerOf(requester) == owner, false);
	}

	/** Called when the server stops, so a singleplayer world that's been left can't keep its ServerWorld (and every requester's entity) reachable into the next one. */
	public static void clear() {
		REQUESTERS.clear();
	}

	/** Outcome of {@link #resetForcedChunks}. */
	public record ResetResult(int released, int kept) {
	}

	/** /tvm unloadchunks: makes world's force-loaded chunks exactly the ones this mod still has a live requester for. Requests whose owner is gone (a removed entity or block entity) are dropped first; then every force-loaded chunk nobody is requesting is un-forced - leftovers from before removal cleanup was fixed, from earlier sessions (force-loading is saved with the world), and also any made with vanilla /forceload or by other mods. Every chunk a live requester still holds is kept, and re-forced if something removed it, so whatever is currently flying, patrolling or being controlled carries on without a gap. */
	public static ResetResult resetForcedChunks(ServerWorld world) {
		Map<ChunkPos, Set<Object>> worldMap = REQUESTERS.computeIfAbsent(world, w -> new HashMap<>());
		Iterator<Map.Entry<ChunkPos, Set<Object>>> it = worldMap.entrySet().iterator();
		while (it.hasNext()) {
			Set<Object> requesters = it.next().getValue();
			requesters.removeIf(requester -> !isAlive(requester));
			if (requesters.isEmpty()) {
				it.remove();
			}
		}
		LongSet forced = new LongOpenHashSet(world.getForcedChunks());
		int released = 0;
		for (long packed : forced) {
			ChunkPos pos = new ChunkPos(packed);
			if (!worldMap.containsKey(pos)) {
				world.setChunkForced(pos.x, pos.z, false);
				released++;
			}
		}
		for (ChunkPos pos : worldMap.keySet()) {
			if (!forced.contains(pos.toLong())) {
				// No getChunk() first (unlike request()): forcing is enough to have it loaded, and this runs from a command, where a synchronous load of a far-off chunk would only stall the server.
				world.setChunkForced(pos.x, pos.z, true);
			}
		}
		return new ResetResult(released, worldMap.size());
	}

	private static void removeMatching(ServerWorld world, java.util.function.Predicate<Object> matches, boolean unforce) {
		Map<ChunkPos, Set<Object>> worldMap = REQUESTERS.get(world);
		if (worldMap == null) {
			return;
		}
		Iterator<Map.Entry<ChunkPos, Set<Object>>> it = worldMap.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<ChunkPos, Set<Object>> entry = it.next();
			Set<Object> requesters = entry.getValue();
			if (requesters.removeIf(matches) && requesters.isEmpty()) {
				it.remove();
				if (unforce) {
					ChunkPos pos = entry.getKey();
					world.setChunkForced(pos.x, pos.z, false);
				}
			}
		}
	}

	private static Object ownerOf(Object requester) {
		return requester instanceof Purpose purpose ? purpose.owner() : requester;
	}

	/** An entity or block entity counts as alive until it's removed (unloaded included - a reload makes a new instance, which requests again). Any other kind of requester is kept, since there's nothing to check it against. */
	private static boolean isAlive(Object requester) {
		Object owner = ownerOf(requester);
		if (owner instanceof Entity entity) {
			return !entity.isRemoved();
		}
		if (owner instanceof BlockEntity blockEntity) {
			return !blockEntity.isRemoved();
		}
		return true;
	}
}
