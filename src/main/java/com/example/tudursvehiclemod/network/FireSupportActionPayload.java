package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client -> server: one action on the Fire Support Center at pos, from its screen. action is one of FireSupportCenterBlockEntity's ACTION_* codes; a, b, c its arguments (ACTION_SET_RELATIVE: x, y, z; ACTION_STEP_WAYPOINT: a = direction; ACTION_CYCLE_WEAPON: a = unit slot; the rest take none). */
public record FireSupportActionPayload(BlockPos pos, int action, int a, int b, int c) implements CustomPayload {

	public static final CustomPayload.Id<FireSupportActionPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "fire_support_action"));

	public static final PacketCodec<RegistryByteBuf, FireSupportActionPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, FireSupportActionPayload::pos,
			PacketCodecs.VAR_INT, FireSupportActionPayload::action,
			PacketCodecs.VAR_INT, FireSupportActionPayload::a,
			PacketCodecs.VAR_INT, FireSupportActionPayload::b,
			PacketCodecs.VAR_INT, FireSupportActionPayload::c,
			FireSupportActionPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
