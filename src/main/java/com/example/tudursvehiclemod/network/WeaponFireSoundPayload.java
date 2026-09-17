package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "play this weapon's fire sound at this position". */
public record WeaponFireSoundPayload(
		String soundName, double x, double y, double z, float volume, float pitch, float pitchRandom
) implements CustomPayload {

	public static final CustomPayload.Id<WeaponFireSoundPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "weapon_fire_sound"));

	// Manual encode/decode rather than PacketCodec.tuple().
	public static final PacketCodec<RegistryByteBuf, WeaponFireSoundPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeString(value.soundName());
				buf.writeDouble(value.x());
				buf.writeDouble(value.y());
				buf.writeDouble(value.z());
				buf.writeFloat(value.volume());
				buf.writeFloat(value.pitch());
				buf.writeFloat(value.pitchRandom());
			},
			buf -> new WeaponFireSoundPayload(
					buf.readString(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readFloat(), buf.readFloat(), buf.readFloat())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
