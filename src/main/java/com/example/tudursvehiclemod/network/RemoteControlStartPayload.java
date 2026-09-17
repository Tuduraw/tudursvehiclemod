package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "you're now remote-controlling entityId (a UAV vehicle, via a bound block.StationBlockEntity) - start showing its own pilot view, and redirect your own input to it" - see entity.AbstractVehicleEntity's own tudursvehiclemod$tryEnterRemoteControl() doc, and client.RemoteControlState's own doc for where this actually gets consumed. Paired with RemoteControlEndPayload for the reverse. */
public record RemoteControlStartPayload(int entityId) implements CustomPayload {

	public static final CustomPayload.Id<RemoteControlStartPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "remote_control_start"));

	public static final PacketCodec<RegistryByteBuf, RemoteControlStartPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, RemoteControlStartPayload::entityId,
			RemoteControlStartPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
