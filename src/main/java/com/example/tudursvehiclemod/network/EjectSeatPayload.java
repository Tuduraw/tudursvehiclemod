package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "eject from my current vehicle seat" - supports MC Heli's own EnableEjectionSeat, sent when the player is riding a vehicle whose own VehicleDefinition has enableEjectionSeat set and presses Alt+Space (see client.VehicleModClient's own key-handling for the exact client-side trigger). No payload data needed - the server resolves "this player's own current vehicle" itself (never trusts a client-supplied target) and re-validates enableEjectionSeat before actually acting, via entity.AbstractVehicleEntity's own tudursvehiclemod$tryEjectSeat(). */
public record EjectSeatPayload() implements CustomPayload {

	public static final CustomPayload.Id<EjectSeatPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "eject_seat"));

	public static final PacketCodec<RegistryByteBuf, EjectSeatPayload> CODEC =
			PacketCodec.unit(new EjectSeatPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
