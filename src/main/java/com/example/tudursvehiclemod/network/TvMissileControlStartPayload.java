package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "you're now steering entityId (a TV-guided missile you just fired) directly - start rendering your own camera from its own position/orientation instead" - see entity.projectile.VehicleProjectileEntity's own tudursvehiclemod$setTvControlled() doc, and client.mixin.CameraMixin's own doc for where this actually gets consumed. Paired with TvMissileControlEndPayload for the reverse. */
public record TvMissileControlStartPayload(int entityId) implements CustomPayload {

	public static final CustomPayload.Id<TvMissileControlStartPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "tv_missile_control_start"));

	public static final PacketCodec<RegistryByteBuf, TvMissileControlStartPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, TvMissileControlStartPayload::entityId,
			TvMissileControlStartPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
