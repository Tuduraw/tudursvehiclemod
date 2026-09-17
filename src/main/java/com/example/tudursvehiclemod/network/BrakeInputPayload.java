package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "my brake key (Space, by default the vanilla jump key -
 * unused by any vehicle here otherwise) is currently held/released" - per a
 * direct request, CarEntity-specific: applies a decelerating force
 * independent of the current throttle value (see CarEntity's own
 * tudursvehiclemod$applyBrake() doc). Same "boolean, sent only on change"
 * pattern as VerticalLevelInputPayload, for the same reason
 * (PlayerEntity#input isn't reliably readable server-side for a riding
 * entity, so this needs its own explicit sync). */
public record BrakeInputPayload(boolean braking) implements CustomPayload {

	public static final CustomPayload.Id<BrakeInputPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "brake_input"));

	public static final PacketCodec<RegistryByteBuf, BrakeInputPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, BrakeInputPayload::braking,
			BrakeInputPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
