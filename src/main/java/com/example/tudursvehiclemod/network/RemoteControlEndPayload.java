package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "remote control of whatever vehicle you were controlling has ended - revert your own camera/input back to normal". Paired with RemoteControlStartPayload for the initial hand-off. */
public record RemoteControlEndPayload() implements CustomPayload {

	public static final CustomPayload.Id<RemoteControlEndPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "remote_control_end"));

	public static final PacketCodec<RegistryByteBuf, RemoteControlEndPayload> CODEC =
			PacketCodec.unit(new RemoteControlEndPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
