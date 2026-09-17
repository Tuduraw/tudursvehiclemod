package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "control of whatever TV-guided missile you were steering has just ended (a hit, or it strayed out of range/an unloaded chunk - see entity.projectile.VehicleProjectileEntity's own tudursvehiclemod$releaseTvControl() doc) - revert your own camera back to normal". Paired with TvMissileControlStartPayload for the initial hand-off. */
public record TvMissileControlEndPayload() implements CustomPayload {

	public static final CustomPayload.Id<TvMissileControlEndPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "tv_missile_control_end"));

	public static final PacketCodec<RegistryByteBuf, TvMissileControlEndPayload> CODEC =
			PacketCodec.unit(new TvMissileControlEndPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
