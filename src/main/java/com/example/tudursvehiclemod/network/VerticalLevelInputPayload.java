package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "my level-ascend/level-descend keys (arrow up/down) are
 * currently held/released" - see SubmarineEntity's own doc for what these do
 * (move vertically in world space while keeping the current pitch, unlike
 * Space/Ctrl which change pitch itself). */
public record VerticalLevelInputPayload(boolean ascending, boolean descending) implements CustomPayload {

	public static final CustomPayload.Id<VerticalLevelInputPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "vertical_level_input"));

	public static final PacketCodec<RegistryByteBuf, VerticalLevelInputPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, VerticalLevelInputPayload::ascending,
			PacketCodecs.BOOLEAN, VerticalLevelInputPayload::descending,
			VerticalLevelInputPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
