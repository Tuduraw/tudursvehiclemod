package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "force-dismount every non-pilot passenger on my current vehicle, regardless of any seat's own enableParachuting setting" - later revised to a long-press trigger instead of Ctrl+key ("強制降車について、ctrlとの組み合わせではなくパラシュートキーを長押しして動作するように変更しようと思います。本来対象とならない座席に対する処理のため、同一キーで問題ありません"): sent when the pilot holds the parachute-jump key past PARACHUTE_FORCE_DISMOUNT_HOLD_TICKS (see client.VehicleModClient's own key-handling). Distinct from DismountAllMobsPayload (mobs only, per-seat parachute-or-plain outcome depending on each seat's own enableParachuting, sent on a short press of the same key) - this affects EVERY non-pilot passenger (mob or player alike) with a plain forced dismount and no parachute grant at all, unconditionally. No payload data needed - the server resolves "this player's own current vehicle" itself (never trusts a client-supplied target) and re-validates that this player is actually the pilot (seat 0) before acting, via entity.AbstractVehicleEntity's own tudursvehiclemod$tryForceDismountAllSeats(). */
public record ForceDismountAllSeatsPayload() implements CustomPayload {

	public static final CustomPayload.Id<ForceDismountAllSeatsPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "force_dismount_all_seats"));

	public static final PacketCodec<RegistryByteBuf, ForceDismountAllSeatsPayload> CODEC =
			PacketCodec.unit(new ForceDismountAllSeatsPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
