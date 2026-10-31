package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server -> client, on join and after /reload: a short hash of every vehicle definition and weapon config the server has loaded
 * (see asset.DefinitionFingerprint). The definitions themselves are not sent - the client compares this against its own copy of
 * the addon packs and warns when they differ (client.ClientDefinitions). A few dozen bytes per entry. */
public record DefinitionFingerprintPayload(Map<String, Long> vehicles, Map<String, Long> weapons) implements CustomPayload {

	public static final CustomPayload.Id<DefinitionFingerprintPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "definition_fingerprint"));

	public static final PacketCodec<RegistryByteBuf, DefinitionFingerprintPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				writeMap(buf, value.vehicles());
				writeMap(buf, value.weapons());
			},
			buf -> new DefinitionFingerprintPayload(readMap(buf), readMap(buf))
	);

	private static void writeMap(RegistryByteBuf buf, Map<String, Long> map) {
		buf.writeVarInt(map.size());
		map.forEach((key, hash) -> {
			buf.writeString(key);
			buf.writeLong(hash);
		});
	}

	private static Map<String, Long> readMap(RegistryByteBuf buf) {
		int size = buf.readVarInt();
		Map<String, Long> map = new LinkedHashMap<>();
		for (int i = 0; i < size; i++) {
			map.put(buf.readString(), buf.readLong());
		}
		return map;
	}

	/** What this server has loaded right now. */
	public static DefinitionFingerprintPayload current() {
		return new DefinitionFingerprintPayload(
				com.example.tudursvehiclemod.asset.DefinitionFingerprint.currentVehicles(),
				com.example.tudursvehiclemod.asset.DefinitionFingerprint.currentWeapons());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
