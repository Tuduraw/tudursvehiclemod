package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: raw (pre-vanilla-clamp) mouse yaw/pitch deltas accumulated since the last tick, while directly steering a TV-guided missile (see entity.projectile.VehicleProjectileEntity's own tudursvehiclemod$updateTvMissileControl() doc) - same convention as AircraftOrientationInputPayload's own doc. */
public record TvMissileInputPayload(float yawDelta, float pitchDelta) implements CustomPayload {

	public static final CustomPayload.Id<TvMissileInputPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "tv_missile_input"));

	public static final PacketCodec<RegistryByteBuf, TvMissileInputPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, TvMissileInputPayload::yawDelta,
			PacketCodecs.FLOAT, TvMissileInputPayload::pitchDelta,
			TvMissileInputPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
