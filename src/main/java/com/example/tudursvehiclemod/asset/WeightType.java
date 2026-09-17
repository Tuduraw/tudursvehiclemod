package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

/** Ported from MC Heli's own WeightType directive:
 * <pre>
 * WeightType = Tank
 *; Tank or Car or Unknown
 *; 機体の重量タイプ
 *; Tank : モブにぶつかっても自分にはダメージ無し, 破壊するブロックが多い
 *; Car : モブにぶつかると自分もダメージを受ける, 破壊するブロックが少ない
 *; Tank でも Car でも無い場合の動作は未定義
 * </pre>
 * See AbstractVehicleEntity's own tudursvehiclemod$handleWeightTypeMobCollision()
 * and tudursvehiclemod$handleWeightTypeBlockBreaking() docs for exactly how
 * each type's own behavior is currently implemented. UNKNOWN (also the
 * default when a vehicle's own definition doesn't set this at all)
 * deliberately behaves like neither - matching the source directive's own
 * "動作は未定義" (undefined behavior) for anything other than Tank/Car. */
public enum WeightType implements StringIdentifiable {
	TANK("tank"),
	CAR("car"),
	UNKNOWN("unknown");

	public static final Codec<WeightType> CODEC = StringIdentifiable.createCodec(WeightType::values);

	private final String id;

	WeightType(String id) {
		this.id = id;
	}

	@Override
	public String asString() {
		return id;
	}
}
