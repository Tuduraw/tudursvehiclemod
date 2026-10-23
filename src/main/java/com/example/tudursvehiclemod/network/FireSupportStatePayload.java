package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Server -> client: everything the Fire Support Center screen at pos shows (FireSupportCenterBlockEntity.tudursvehiclemod$encodeState()). Sent when the screen opens and then every few ticks while it stays open. */
public record FireSupportStatePayload(BlockPos pos, NbtCompound state) implements CustomPayload {

	public static final CustomPayload.Id<FireSupportStatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "fire_support_state"));

	public static final PacketCodec<RegistryByteBuf, FireSupportStatePayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, FireSupportStatePayload::pos,
			PacketCodecs.NBT_COMPOUND, FireSupportStatePayload::state,
			FireSupportStatePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
