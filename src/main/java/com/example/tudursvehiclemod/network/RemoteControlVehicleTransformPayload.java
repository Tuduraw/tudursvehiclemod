package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client, sent every tick to the remote controller only, for
 * as long as remote control is active (see
 * entity.AbstractVehicleEntity's own tudursvehiclemod$updateRemoteControlSync()
 * doc): the controlled vehicle's own current position/rotation, synced
 * directly rather than relying on the controlling player's own client
 * actually having this (very possibly distant, well outside vanilla's
 * own normal entity-tracking range) vehicle loaded/tracked as a real
 * client-side Entity at all - see client.RemoteControlState's own doc
 * for how this gets consumed directly by the camera instead. */
public record RemoteControlVehicleTransformPayload(
		double x, double y, double z, float yaw, float pitch, float roll
) implements CustomPayload {

	public static final CustomPayload.Id<RemoteControlVehicleTransformPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "remote_control_vehicle_transform"));

	public static final PacketCodec<RegistryByteBuf, RemoteControlVehicleTransformPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.DOUBLE, RemoteControlVehicleTransformPayload::x,
			PacketCodecs.DOUBLE, RemoteControlVehicleTransformPayload::y,
			PacketCodecs.DOUBLE, RemoteControlVehicleTransformPayload::z,
			PacketCodecs.FLOAT, RemoteControlVehicleTransformPayload::yaw,
			PacketCodecs.FLOAT, RemoteControlVehicleTransformPayload::pitch,
			PacketCodecs.FLOAT, RemoteControlVehicleTransformPayload::roll,
			RemoteControlVehicleTransformPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
