package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "this Drone Center's own current auto-disable resume mode is X" - sent alongside DummyPilotConfigOpenPayload when that screen opens (a separate payload rather than an extra field on that one, which has no room left - PacketCodec.tuple()'s own maximum of 12 field pairs is already fully used there).
 *
 * AutoResume mirrors block.DroneCenterBlockEntity's own AutoDisableResumeMode - true for AUTO_RESUME ("モード2"), false for MANUAL_REENABLE ("モード1", the default). */
public record DroneCenterAutoDisableModeOpenPayload(int x, int y, int z, boolean autoResume) implements CustomPayload {

	public static final CustomPayload.Id<DroneCenterAutoDisableModeOpenPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_center_auto_disable_mode_open"));

	public static final PacketCodec<RegistryByteBuf, DroneCenterAutoDisableModeOpenPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DroneCenterAutoDisableModeOpenPayload::x,
			PacketCodecs.VAR_INT, DroneCenterAutoDisableModeOpenPayload::y,
			PacketCodecs.VAR_INT, DroneCenterAutoDisableModeOpenPayload::z,
			PacketCodecs.BOOLEAN, DroneCenterAutoDisableModeOpenPayload::autoResume,
			DroneCenterAutoDisableModeOpenPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
