package com.example.tudursvehiclemod;

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
 * requester is typically "this" from whichever entity/block instance is asking - any Object works, since it's used purely as an identity key (never dereferenced). The outer map itself is a WeakHashMap keyed on ServerWorld, so an unloaded/removed world's own bookkeeping doesn't leak forever, though in practice this project only ever deals with long-lived worlds. */
public final class ChunkForceTracker {
	private static final Map<ServerWorld, Map<ChunkPos, Set<Object>>> REQUESTERS = new WeakHashMap<>();

	private ChunkForceTracker() {
	}

	/** Registers requester as needing pos force-loaded - actually force-loads it (with a synchronous getChunk() first, for a genuinely new/never-before-loaded chunk - see AircraftEntity's own earlier doc on why that matters) only if requester turns out to be the very first one currently asking for this exact chunk. Safe to call every tick for the same requester/pos pair - a cheap no-op after the first time, until release() is called for that same pair. */
	public static void request(ServerWorld world, ChunkPos pos, Object requester) {
		Set<Object> requesters = REQUESTERS.computeIfAbsent(world, w -> new HashMap<>())
				.computeIfAbsent(pos, p -> new HashSet<>());
		if (requesters.add(requester) && requesters.size() == 1) {
			world.getChunk(pos.x, pos.z);
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
		Map<ChunkPos, Set<Object>> worldMap = REQUESTERS.get(world);
		if (worldMap == null) {
			return;
		}
		Iterator<Map.Entry<ChunkPos, Set<Object>>> it = worldMap.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<ChunkPos, Set<Object>> entry = it.next();
			Set<Object> requesters = entry.getValue();
			if (requesters.remove(requester) && requesters.isEmpty()) {
				it.remove();
				ChunkPos pos = entry.getKey();
				world.setChunkForced(pos.x, pos.z, false);
			}
		}
	}
}
