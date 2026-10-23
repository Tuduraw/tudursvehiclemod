package com.example.tudursvehiclemod.item;

import com.example.tudursvehiclemod.VehicleModServerConfig;
import com.example.tudursvehiclemod.block.FireSupportCenterBlock;
import com.example.tudursvehiclemod.block.FireSupportCenterBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

/** Remote handset for a Fire Support Center (block.FireSupportCenterBlockEntity).
 *
 * <ul>
 * <li>Sneak + right-click the center: links this designator to it.</li>
 * <li>Right-click: marks the block you are looking at (up to VehicleModServerConfig.fireSupportDesignatorRange) as the target - taken when the center is set to designator targeting.</li>
 * <li>Sneak + right-click anywhere else: turns support on or off.</li>
 * </ul>
 *
 * <p>Works only while the center has remote control enabled - only then are it and its vehicles kept loaded (see the center's doc) - and only from the center's own dimension. Every outcome is reported in the actionbar. */
public class FireSupportDesignatorItem extends Item {

	public FireSupportDesignatorItem(Settings settings) {
		super(settings);
	}

	public static GlobalPos tudursvehiclemod$getLink(ItemStack stack) {
		return stack.get(ModComponents.FIRE_SUPPORT_LINK);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		if (player == null || !player.isSneaking() || !(world.getBlockState(context.getBlockPos()).getBlock() instanceof FireSupportCenterBlock)) {
			return ActionResult.PASS;
		}
		if (world instanceof ServerWorld && player instanceof ServerPlayerEntity serverPlayer) {
			context.getStack().set(ModComponents.FIRE_SUPPORT_LINK, GlobalPos.create(world.getRegistryKey(), context.getBlockPos().toImmutable()));
			serverPlayer.sendMessage(Text.translatable("message.tudursvehiclemod.fire_support.linked",
					context.getBlockPos().getX(), context.getBlockPos().getY(), context.getBlockPos().getZ()), true);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player)) {
			return ActionResult.SUCCESS;
		}
		FireSupportCenterBlockEntity center = tudursvehiclemod$findCenter(serverWorld, player, user.getStackInHand(hand));
		if (center == null) {
			return ActionResult.FAIL;
		}
		if (player.isSneaking()) {
			center.tudursvehiclemod$setActive(serverWorld, !center.tudursvehiclemod$isActive(), player);
			return ActionResult.SUCCESS;
		}
		if (center.tudursvehiclemod$getTargetMode() != FireSupportCenterBlockEntity.TargetMode.DESIGNATOR) {
			player.sendMessage(Text.translatable("message.tudursvehiclemod.fire_support.not_designator_mode"), true);
			return ActionResult.FAIL;
		}
		HitResult hit = player.raycast(VehicleModServerConfig.get().fireSupportDesignatorRange, 1.0f, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			player.sendMessage(Text.translatable("message.tudursvehiclemod.fire_support.no_block"), true);
			return ActionResult.FAIL;
		}
		BlockPos target = blockHit.getBlockPos();
		boolean wasActive = center.tudursvehiclemod$isActive();
		center.tudursvehiclemod$setDesignatedTarget(serverWorld, target, player);
		// A center that just went off over this target has already said why.
		if (!wasActive || center.tudursvehiclemod$isActive()) {
			player.sendMessage(Text.translatable("message.tudursvehiclemod.fire_support.designated",
					target.getX(), target.getY(), target.getZ(), (int) Math.round(Math.sqrt(target.getSquaredDistance(center.getPos())))), true);
		}
		return ActionResult.SUCCESS;
	}

	/** The linked center, if it can be reached right now; otherwise tells the player why not and returns null. */
	private static FireSupportCenterBlockEntity tudursvehiclemod$findCenter(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
		GlobalPos link = tudursvehiclemod$getLink(stack);
		if (link == null) {
			player.sendMessage(Text.translatable("message.tudursvehiclemod.fire_support.not_linked"), true);
			return null;
		}
		if (!link.dimension().equals(world.getRegistryKey())) {
			player.sendMessage(Text.translatable("message.tudursvehiclemod.fire_support.other_dimension"), true);
			return null;
		}
		// Not loaded means remote control is off (it keeps the center loaded) - don't load it here.
		if (!world.isChunkLoaded(link.pos()) || !(world.getBlockEntity(link.pos()) instanceof FireSupportCenterBlockEntity center)) {
			player.sendMessage(Text.translatable("message.tudursvehiclemod.fire_support.center_unreachable"), true);
			return null;
		}
		if (!center.tudursvehiclemod$isRemoteControlEnabled()) {
			player.sendMessage(Text.translatable("message.tudursvehiclemod.fire_support.remote_disabled"), true);
			return null;
		}
		return center;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, net.minecraft.component.type.TooltipDisplayComponent displayComponent,
			java.util.function.Consumer<Text> textConsumer, net.minecraft.item.tooltip.TooltipType type) {
		GlobalPos link = tudursvehiclemod$getLink(stack);
		if (link != null) {
			textConsumer.accept(Text.translatable("item.tudursvehiclemod.fire_support_designator.linked",
					link.pos().getX(), link.pos().getY(), link.pos().getZ()).formatted(Formatting.GRAY));
		} else {
			textConsumer.accept(Text.translatable("item.tudursvehiclemod.fire_support_designator.unlinked").formatted(Formatting.DARK_GRAY));
		}
		textConsumer.accept(Text.translatable("item.tudursvehiclemod.fire_support_designator.usage").formatted(Formatting.DARK_GRAY));
	}
}
