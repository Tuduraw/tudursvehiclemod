package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** A named OBJ part (from "o"/"g" lines, same convention as PartAnimation/ TogglePart) that rotates about its own pivot to track whoever is currently aiming an.. */
public record WeaponPart(
		String part,
		// Which seat's occupant this part tracks.
		int seatIndex,
		boolean hideForGunner,
		boolean yawFollow,
		boolean pitchFollow,
		double pivotX,
		double pivotY,
		double pivotZ,
		// How far this part slides back along its own local -Z axis while recoiling after a shot.
		float recoilDistance,
		// pilot_fallback - see WeaponDefinition's own pilotUsable doc; when seatIndex (a gunner seat) has no occupant, this part tracks the PILOT (seat 0) instead of just sitting idle at 0/0.
		boolean pilotFallback,
		// child_info - present only for an AddPartWeaponChild (see ChildInfo's own doc) - bundled into one nested record (rather than several separate top-level fields, as an earlier version of this had) specifically to stay under RecordCodecBuilder's own 16-field limit once aimRange below was added.
		Optional<ChildInfo> childInfo,
		// aim_range - present only if the specific weapon this part is bound to has its own AddWeapon "DefaultYaw, MinYaw, MaxYaw, MinPitch, MaxPitch" trailing parameters (see WeaponAimRange's own doc) - when present, this part's own tracked yaw/pitch (see AbstractVehicleEntity's own getWeaponAimYaw/Pitch doc) is clamped around DefaultYaw, matching the actual firing direction's own clamping exactly rather than the part visually swinging further than the weapon can actually aim.
		Optional<WeaponAimRange> aimRange,
		// weapon_name - the specific AddWeapon this part is bound to (its own first field, "連動する武器名" - see Readme_Aircraft.txt's own AddPartWeapon doc), lowercased. Present so a specific WeaponDefinition can be matched to the exact WeaponPart tracking IT specifically (by weaponName equality) rather than just by seatIndex, which alone could be ambiguous if more than one weapon shares a seat. Used by AbstractVehicleEntity's own tryFireWeapon to adjust the actual firing/muzzle position to follow this part's own current rotation - see that method's own doc.
		Optional<String> weaponName,
		// Spins_while_firing - true only for a part created from AddPartRotWeapon (otherwise identical to AddPartWeapon) - continuously spins around its own local Z axis (barrel-forward) while the weapon it's bound to is actively being fired, gatling-style. Speed comes from the WEAPON's own stats (see WeaponStats's own rotationSpeedPerSecond doc), not from this part - a part on its own has no inherent spin speed.
		boolean spinsWhileFiring
) {
	/** An AddPartWeaponChild (see Readme_Aircraft.txt's own doc) - a child
	 * part UNCONDITIONALLY inherits whatever rotation its own parent
	 * undergoes (around the PARENT's own pivot here), regardless of the
	 * owning WeaponPart's own yawFollow/pitchFollow above - those only
	 * control whether the child ALSO gets its own additional, independent
	 * rotation around ITS OWN pivot (WeaponPart.pivotX/Y/Z) on top of
	 * that. See VehicleEntityRenderer's own doc for how this two-stage
	 * transform is actually applied. */
	public record ChildInfo(
			boolean parentYawFollow,
			boolean parentPitchFollow,
			double parentPivotX,
			double parentPivotY,
			double parentPivotZ
	) {
		public static final Codec<ChildInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("parent_yaw_follow", false).forGetter(ChildInfo::parentYawFollow),
				Codec.BOOL.optionalFieldOf("parent_pitch_follow", false).forGetter(ChildInfo::parentPitchFollow),
				Codec.DOUBLE.optionalFieldOf("parent_pivot_x", 0.0).forGetter(ChildInfo::parentPivotX),
				Codec.DOUBLE.optionalFieldOf("parent_pivot_y", 0.0).forGetter(ChildInfo::parentPivotY),
				Codec.DOUBLE.optionalFieldOf("parent_pivot_z", 0.0).forGetter(ChildInfo::parentPivotZ)
		).apply(instance, ChildInfo::new));
	}

	public static final Codec<WeaponPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(WeaponPart::part),
			Codec.INT.optionalFieldOf("seat_index", -1).forGetter(WeaponPart::seatIndex),
			Codec.BOOL.optionalFieldOf("hide_for_gunner", false).forGetter(WeaponPart::hideForGunner),
			Codec.BOOL.optionalFieldOf("yaw_follow", true).forGetter(WeaponPart::yawFollow),
			Codec.BOOL.optionalFieldOf("pitch_follow", true).forGetter(WeaponPart::pitchFollow),
			Codec.DOUBLE.optionalFieldOf("pivot_x", 0.0).forGetter(WeaponPart::pivotX),
			Codec.DOUBLE.optionalFieldOf("pivot_y", 0.0).forGetter(WeaponPart::pivotY),
			Codec.DOUBLE.optionalFieldOf("pivot_z", 0.0).forGetter(WeaponPart::pivotZ),
			Codec.FLOAT.optionalFieldOf("recoil_distance", 0.0f).forGetter(WeaponPart::recoilDistance),
			Codec.BOOL.optionalFieldOf("pilot_fallback", false).forGetter(WeaponPart::pilotFallback),
			ChildInfo.CODEC.optionalFieldOf("child_info").forGetter(WeaponPart::childInfo),
			WeaponAimRange.CODEC.optionalFieldOf("aim_range").forGetter(WeaponPart::aimRange),
			Codec.STRING.optionalFieldOf("weapon_name").forGetter(WeaponPart::weaponName),
			Codec.BOOL.optionalFieldOf("spins_while_firing", false).forGetter(WeaponPart::spinsWhileFiring)
	).apply(instance, WeaponPart::new));
}
