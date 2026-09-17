package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "register the vehicle with this UUID to the drone control stick I'm holding in this hand, and give it this custom name" - sent once the player confirms the name typed into item.DroneControlStickItem's own naming screen (see that class's own doc), which the client opens immediately (no server round-trip needed first) the moment the player right-clicks a vehicle while holding this item, since the vehicle's own UUID and a suggested default name are both already available client-side at that point. vehicleId is sent as its String form (no existing payload in this project sends a raw UUID, so this follows the same "keep it a simple, already-supported codec type" convention used elsewhere). Re-registering ALWAYS re-prompts and re-applies a name (rather than only naming a stick the very first time it's ever registered, as before), the server re-validates everything from scratch rather than trusting anything about the client's own screen state. */
public record DroneStickRegisterPayload(boolean mainHand, String vehicleId, String customName) implements CustomPayload {

	public static final CustomPayload.Id<DroneStickRegisterPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_stick_register"));

	public static final PacketCodec<RegistryByteBuf, DroneStickRegisterPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, DroneStickRegisterPayload::mainHand,
			PacketCodecs.STRING, DroneStickRegisterPayload::vehicleId,
			PacketCodecs.STRING, DroneStickRegisterPayload::customName,
			DroneStickRegisterPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
