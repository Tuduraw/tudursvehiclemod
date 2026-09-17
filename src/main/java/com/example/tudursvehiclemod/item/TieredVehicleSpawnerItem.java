package com.example.tudursvehiclemod.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** One of these exists per (converter target, tier 1-5) pair.
 *
 * Typed against VehicleConverterTarget rather than the VehicleCategory enum directly, so an ADDON
 * MOD can reuse this item - and the vehicle selection screen it opens - for its own vehicle kinds.
 * VehicleCategory implements that interface, so every built-in item is constructed exactly as before. */
public class TieredVehicleSpawnerItem extends Item {

	/** Set by VehicleModClient at client startup. */
	public static VehicleSelectScreenOpener screenOpener;

	private final VehicleConverterTarget category;
	private final int tier;

	public TieredVehicleSpawnerItem(Settings settings, VehicleConverterTarget category, int tier) {
		super(settings);
		this.category = category;
		this.tier = tier;
	}

	public VehicleConverterTarget getCategory() {
		return category;
	}

	public int getTier() {
		return tier;
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (world.isClient() && screenOpener != null) {
			screenOpener.open(category, tier);
		}
		return ActionResult.SUCCESS;
	}

	/** Called (server-side) once the player has actually picked a vehicle from the screen. */
	public static void consumeOne(PlayerEntity player, Hand hand) {
		if (!player.isCreative()) {
			ItemStack stack = player.getStackInHand(hand);
			stack.decrement(1);
		}
	}
}
