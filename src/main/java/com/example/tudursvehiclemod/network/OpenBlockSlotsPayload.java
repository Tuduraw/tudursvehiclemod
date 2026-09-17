package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "open the item slot container UI for the block at this position" - sent from a button inside block.DroneCenterBlockEntity's own config screen or block.StationBlockEntity's own menu screen (see each block's own doc). the server re-validates that the block at this position still actually is one of this project's own slot-bearing block entities before opening anything. */
public record OpenBlockSlotsPayload(int x, int y, int z) implements CustomPayload {

	public static final CustomPayload.Id<OpenBlockSlotsPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "open_block_slots"));

	public static final PacketCodec<RegistryByteBuf, OpenBlockSlotsPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, OpenBlockSlotsPayload::x,
			PacketCodecs.INTEGER, OpenBlockSlotsPayload::y,
			PacketCodecs.INTEGER, OpenBlockSlotsPayload::z,
			OpenBlockSlotsPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
