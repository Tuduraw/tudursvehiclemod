package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "lock whatever is currently under my crosshair" for a Carrier formation's own lead aircraft, while in lock mode - see entity.AbstractVehicleEntity's own tudursvehiclemod$tryLockCarrierTarget() doc for the full server-side logic (candidate search, wingman assignment). Sent by the fire key INSTEAD of FireWeaponPayload while CARRIER_LOCK_MODE_ACTIVE is true (see client.VehicleModClient's own fire-key routing) - locking is instantaneous (no hold-to-lock duration, unlike AAMissile/ATMissile's own guided-lock mechanic), so a single press either locks or does nothing (no valid candidate under the crosshair) immediately. No payload data needed - the weapon a locked wingman actually attacks with is the LEAD's own currently-selected weapon (tudursvehiclemod$getSelectedWeaponIndex(), already server-authoritative and synced), not something the client needs to additionally specify here. */
public record LockCarrierTargetPayload() implements CustomPayload {

	public static final CustomPayload.Id<LockCarrierTargetPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "lock_carrier_target"));

	public static final PacketCodec<RegistryByteBuf, LockCarrierTargetPayload> CODEC =
			PacketCodec.unit(new LockCarrierTargetPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
