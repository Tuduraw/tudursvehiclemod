package com.example.tudursvehiclemod.entity.projectile;

import com.example.tudursvehiclemod.registry.ModEntityTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Same firing/hit/damage behavior as VehicleProjectileEntity (extended directly, rather than duplicated). */
public class VehicleModelProjectileEntity extends VehicleProjectileEntity {

	protected static final TrackedData<String> BULLET_MODEL =
			DataTracker.registerData(VehicleModelProjectileEntity.class, TrackedDataHandlerRegistry.STRING);
	protected static final TrackedData<String> BULLET_TEXTURE =
			DataTracker.registerData(VehicleModelProjectileEntity.class, TrackedDataHandlerRegistry.STRING);
	protected static final TrackedData<Float> BULLET_SCALE =
			DataTracker.registerData(VehicleModelProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT);

	/** Required by EntityType deserialization (e.g. */
	public VehicleModelProjectileEntity(EntityType<? extends VehicleProjectileEntity> type, World world) {
		super(type, world);
	}

	public VehicleModelProjectileEntity(World world, LivingEntity owner, ItemStack stack, float damage, float gravity,
			float explosionPower, boolean explosionDestroysBlocks, boolean flaming,
			Identifier bulletModel, Identifier bulletTexture, float bulletScale) {
		super(ModEntityTypes.VEHICLE_MODEL_PROJECTILE, world, owner, stack, damage, gravity,
				explosionPower, explosionDestroysBlocks, flaming);
		this.dataTracker.set(BULLET_MODEL, bulletModel.toString());
		this.dataTracker.set(BULLET_TEXTURE, bulletTexture.toString());
		this.dataTracker.set(BULLET_SCALE, bulletScale);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(BULLET_MODEL, "");
		builder.add(BULLET_TEXTURE, "");
		builder.add(BULLET_SCALE, 1.0f);
	}

	/** Null if not yet synced (e.g. the very first tick or two right after spawning, before the client has received this entity's own DataTracker state). */
	public Identifier getBulletModel() {
		String raw = this.dataTracker.get(BULLET_MODEL);
		return raw.isEmpty() ? null : Identifier.of(raw);
	}

	public Identifier getBulletTexture() {
		String raw = this.dataTracker.get(BULLET_TEXTURE);
		return raw.isEmpty() ? null : Identifier.of(raw);
	}

	public float getBulletScale() {
		return this.dataTracker.get(BULLET_SCALE);
	}
}
