package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "trigger a mob-drop sequence on my current vehicle" - supports MC Heli's own MobDropOption (see asset.MobDropOption's own doc), sent when the pilot of a vehicle whose own VehicleDefinition has a MobDropOption configured presses the assigned key (see client.VehicleModClient's own key-handling). No payload data needed - the server resolves "this player's own current vehicle" itself (never trusts a client-supplied target) and re-validates that this player is actually the pilot (seat 0) and a MobDropOption is actually configured, via entity.AbstractVehicleEntity's own tudursvehiclemod$tryTriggerMobDrop(). */
public record MobDropTriggerPayload() implements CustomPayload {

	public static final CustomPayload.Id<MobDropTriggerPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "mob_drop_trigger"));

	public static final PacketCodec<RegistryByteBuf, MobDropTriggerPayload> CODEC =
			PacketCodec.unit(new MobDropTriggerPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
