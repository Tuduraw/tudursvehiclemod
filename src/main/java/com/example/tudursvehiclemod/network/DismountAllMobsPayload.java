package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: a short press of the parachute-jump key while piloting - starts the staggered Mob Drop sequence (entity.AbstractVehicleEntity's own tudursvehiclemod$tryTriggerMobDrop()), which only ever affects seats with enableParachuting set, one mob at a time at that vehicle's own configured interval. Originally dismounted every mob passenger at once regardless of each seat's own enableParachuting setting, but this wrongly swept up non-enabled seats too and dropped everyone simultaneously rather than staggered ("), this now routes to the Mob Drop sequence instead - see client.VehicleModClient's own key-handling for the exact client-side trigger (a SHORT press specifically; holding past PARACHUTE_FORCE_DISMOUNT_HOLD_TICKS sends ForceDismountAllSeatsPayload instead, which still affects every seat unconditionally). No payload data needed - the server resolves "this player's own current vehicle" itself (never trusts a client-supplied target) and re-validates that this player is actually the pilot (seat 0) before acting. */
public record DismountAllMobsPayload() implements CustomPayload {

	public static final CustomPayload.Id<DismountAllMobsPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "dismount_all_mobs"));

	public static final PacketCodec<RegistryByteBuf, DismountAllMobsPayload> CODEC =
			PacketCodec.unit(new DismountAllMobsPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
