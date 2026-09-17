package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: toggle manual mode (disables the automatic level-flight assist) on the aircraft the sender is piloting. Held state, sent only on change. */
public record ManualModePayload(boolean enabled) implements CustomPayload {

	public static final CustomPayload.Id<ManualModePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "manual_mode"));

	public static final PacketCodec<RegistryByteBuf, ManualModePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, ManualModePayload::enabled,
			ManualModePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
