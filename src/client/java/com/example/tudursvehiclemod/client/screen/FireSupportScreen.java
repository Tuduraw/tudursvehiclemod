package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.block.FireSupportCenterBlockEntity;
import com.example.tudursvehiclemod.network.FireSupportActionPayload;
import com.example.tudursvehiclemod.screen.FireSupportScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Fire Support Center screen (block.FireSupportCenterBlockEntity): left, one row per unit - its stick slot, a weapon button (auto or one weapon; click to cycle) and the unit's name and status - with the route-book slot below; right, the target and the switches. Everything shown comes from the server's state (FireSupportScreenHandler.tudursvehiclemod$getState()); every button sends one action back. No background texture - the same flat panel and drawn slots as the Drone Center's screen. */
public class FireSupportScreen extends HandledScreen<FireSupportScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int TEXT_COLOR = 0xFF404040;
	private static final int DIM_COLOR = 0xFF707070;
	private static final int OK_COLOR = 0xFF1E7F1E;
	private static final int WARN_COLOR = 0xFFB02020;
	private static final int RIGHT_X = 236;
	private static final int RIGHT_WIDTH = 116;

	private final ButtonWidget[] weaponButtons = new ButtonWidget[FireSupportCenterBlockEntity.UNIT_SLOTS];
	private ButtonWidget modeButton;
	private TextFieldWidget relXField;
	private TextFieldWidget relYField;
	private TextFieldWidget relZField;
	private ButtonWidget applyButton;
	private ButtonWidget previousWaypointButton;
	private ButtonWidget nextWaypointButton;
	private ButtonWidget arcButton;
	private ButtonWidget redstoneButton;
	private ButtonWidget remoteButton;
	private ButtonWidget activeButton;
	/** The relative coordinates last shown from the server - the fields are refilled only when these change, so typing isn't overwritten by the periodic state updates. */
	private int[] shownRelative;

	public FireSupportScreen(FireSupportScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 360;
		this.backgroundHeight = 250;
		this.playerInventoryTitleX = FireSupportScreenHandler.INVENTORY_X;
		this.playerInventoryTitleY = FireSupportScreenHandler.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		int left = this.x;
		int top = this.y;
		for (int unit = 0; unit < FireSupportCenterBlockEntity.UNIT_SLOTS; unit++) {
			int slotUnit = unit;
			this.weaponButtons[unit] = this.addDrawableChild(ButtonWidget.builder(Text.empty(),
							button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_CYCLE_WEAPON, slotUnit, 0, 0))
					.dimensions(left + 28, top + FireSupportScreenHandler.UNIT_SLOT_Y - 1 + unit * FireSupportScreenHandler.UNIT_SLOT_SPACING, 92, 18).build());
		}
		int rx = left + RIGHT_X;
		this.modeButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_CYCLE_MODE, 0, 0, 0)).dimensions(rx, top + 16, RIGHT_WIDTH, 18).build());
		this.relXField = this.tudursvehiclemod$coordinateField(rx, top + 38, "X");
		this.relYField = this.tudursvehiclemod$coordinateField(rx + 40, top + 38, "Y");
		this.relZField = this.tudursvehiclemod$coordinateField(rx + 80, top + 38, "Z");
		this.applyButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.fire_support.apply"),
				button -> this.tudursvehiclemod$applyRelative()).dimensions(rx, top + 58, RIGHT_WIDTH, 18).build());
		this.previousWaypointButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("<"),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_STEP_WAYPOINT, -1, 0, 0)).dimensions(rx, top + 38, 20, 18).build());
		this.nextWaypointButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(">"),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_STEP_WAYPOINT, 1, 0, 0)).dimensions(rx + RIGHT_WIDTH - 20, top + 38, 20, 18).build());
		this.arcButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_TOGGLE_ARC, 0, 0, 0)).dimensions(rx, top + 102, RIGHT_WIDTH, 18).build());
		this.redstoneButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_TOGGLE_REDSTONE, 0, 0, 0)).dimensions(rx, top + 122, RIGHT_WIDTH, 18).build());
		this.remoteButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_TOGGLE_REMOTE, 0, 0, 0)).dimensions(rx, top + 142, RIGHT_WIDTH, 18).build());
		this.activeButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_TOGGLE_ACTIVE, 0, 0, 0)).dimensions(rx, top + 164, RIGHT_WIDTH, 20).build());
		this.shownRelative = null;
		this.tudursvehiclemod$refreshWidgets();
	}

	private TextFieldWidget tudursvehiclemod$coordinateField(int x, int y, String axis) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, 36, 16, Text.literal(axis));
		field.setMaxLength(6);
		field.setTextPredicate(text -> text.isEmpty() || text.equals("-") || text.matches("-?\\d{1,5}"));
		field.setPlaceholder(Text.literal(axis).formatted(net.minecraft.util.Formatting.DARK_GRAY));
		return this.addDrawableChild(field);
	}

	private void tudursvehiclemod$applyRelative() {
		try {
			int x = Integer.parseInt(this.relXField.getText().trim());
			int y = Integer.parseInt(this.relYField.getText().trim());
			int z = Integer.parseInt(this.relZField.getText().trim());
			this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_SET_RELATIVE, x, y, z);
		} catch (NumberFormatException ignored) {
			// An incomplete field ("" or "-"): nothing to send yet.
		}
	}

	private void tudursvehiclemod$send(int action, int a, int b, int c) {
		BlockPos pos = this.handler.tudursvehiclemod$getBlockPos();
		if (pos != null) {
			ClientPlayNetworking.send(new FireSupportActionPayload(pos, action, a, b, c));
		}
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		this.tudursvehiclemod$refreshWidgets();
	}

	/** Brings every button label and the visible target controls in line with the latest state. */
	private void tudursvehiclemod$refreshWidgets() {
		NbtCompound state = this.handler.tudursvehiclemod$getState();
		NbtList units = state.getListOrEmpty("Units");
		for (int unit = 0; unit < this.weaponButtons.length; unit++) {
			NbtCompound entry = unit < units.size() ? units.getCompoundOrEmpty(unit) : new NbtCompound();
			boolean linked = !entry.getString("Name", "").isEmpty();
			String weapon = entry.getString("Weapon", "");
			this.weaponButtons[unit].visible = linked;
			this.weaponButtons[unit].setMessage(weapon.isEmpty()
					? Text.translatable("gui.tudursvehiclemod.fire_support.weapon_auto")
					: Text.literal(this.tudursvehiclemod$trim(weapon, 84)));
		}
		String mode = state.getString("Mode", FireSupportCenterBlockEntity.TargetMode.RELATIVE.name());
		this.modeButton.setMessage(Text.translatable("gui.tudursvehiclemod.fire_support.mode." + mode.toLowerCase(java.util.Locale.ROOT)));
		boolean relative = FireSupportCenterBlockEntity.TargetMode.RELATIVE.name().equals(mode);
		boolean waypoint = FireSupportCenterBlockEntity.TargetMode.WAYPOINT.name().equals(mode);
		this.relXField.visible = relative;
		this.relYField.visible = relative;
		this.relZField.visible = relative;
		this.applyButton.visible = relative;
		this.previousWaypointButton.visible = waypoint;
		this.nextWaypointButton.visible = waypoint;
		int[] serverRelative = {state.getInt("RelX", 0), state.getInt("RelY", 0), state.getInt("RelZ", 64)};
		if (state.contains("RelX") && (this.shownRelative == null || !java.util.Arrays.equals(this.shownRelative, serverRelative))) {
			this.shownRelative = serverRelative;
			this.relXField.setText(Integer.toString(serverRelative[0]));
			this.relYField.setText(Integer.toString(serverRelative[1]));
			this.relZField.setText(Integer.toString(serverRelative[2]));
		}
		this.arcButton.setMessage(Text.translatable(state.getBoolean("HighArc", false)
				? "gui.tudursvehiclemod.fire_support.arc_high" : "gui.tudursvehiclemod.fire_support.arc_low"));
		this.redstoneButton.setMessage(Text.translatable(state.getBoolean("Redstone", false)
				? "gui.tudursvehiclemod.fire_support.redstone_on" : "gui.tudursvehiclemod.fire_support.redstone_off"));
		this.remoteButton.setMessage(Text.translatable(state.getBoolean("Remote", false)
				? "gui.tudursvehiclemod.fire_support.remote_on" : "gui.tudursvehiclemod.fire_support.remote_off"));
		this.activeButton.setMessage(Text.translatable(state.getBoolean("Active", false)
				? "gui.tudursvehiclemod.fire_support.active_on" : "gui.tudursvehiclemod.fire_support.active_off"));
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int left = this.x;
		int top = this.y;
		context.fill(left, top, left + this.backgroundWidth, top + this.backgroundHeight, PANEL_COLOR);
		context.fill(left + RIGHT_X - 4, top + 4, left + RIGHT_X - 3, top + this.backgroundHeight - 4, 0xFF9A9A9A);
		for (int unit = 0; unit < FireSupportCenterBlockEntity.UNIT_SLOTS; unit++) {
			SlotGrid.drawSlot(context, left + FireSupportScreenHandler.UNIT_SLOT_X,
					top + FireSupportScreenHandler.UNIT_SLOT_Y + unit * FireSupportScreenHandler.UNIT_SLOT_SPACING);
		}
		SlotGrid.drawSlot(context, left + FireSupportScreenHandler.BOOK_SLOT_X, top + FireSupportScreenHandler.BOOK_SLOT_Y);
		SlotGrid.drawPlayerInventoryGrid(context, left, top + FireSupportScreenHandler.INVENTORY_Y, top + FireSupportScreenHandler.HOTBAR_Y);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		super.drawForeground(context, mouseX, mouseY);
		NbtCompound state = this.handler.tudursvehiclemod$getState();
		NbtList units = state.getListOrEmpty("Units");
		for (int unit = 0; unit < FireSupportCenterBlockEntity.UNIT_SLOTS; unit++) {
			NbtCompound entry = unit < units.size() ? units.getCompoundOrEmpty(unit) : new NbtCompound();
			int rowY = FireSupportScreenHandler.UNIT_SLOT_Y + unit * FireSupportScreenHandler.UNIT_SLOT_SPACING;
			String name = entry.getString("Name", "");
			if (name.isEmpty()) {
				context.drawText(this.textRenderer, Text.translatable("gui.tudursvehiclemod.fire_support.unit_empty"), 28, rowY + 4, DIM_COLOR, false);
				continue;
			}
			FireSupportCenterBlockEntity.UnitStatus status = this.tudursvehiclemod$status(entry.getString("Status", ""));
			context.drawText(this.textRenderer, Text.literal(this.tudursvehiclemod$trim(name, 106)), 124, rowY - 1, TEXT_COLOR, false);
			int color = status.canFire() ? OK_COLOR : status.isWarning() ? WARN_COLOR : DIM_COLOR;
			context.drawText(this.textRenderer, Text.translatable(status.translationKey()), 124, rowY + 8, color, false);
		}
		context.drawText(this.textRenderer, Text.translatable("gui.tudursvehiclemod.fire_support.book"),
				FireSupportScreenHandler.BOOK_SLOT_X + 20, FireSupportScreenHandler.BOOK_SLOT_Y + 4, TEXT_COLOR, false);

		context.drawText(this.textRenderer, Text.translatable("gui.tudursvehiclemod.fire_support.target_header"), RIGHT_X, 6, TEXT_COLOR, false);
		String mode = state.getString("Mode", "");
		if (FireSupportCenterBlockEntity.TargetMode.WAYPOINT.name().equals(mode)) {
			int count = state.getInt("WaypointCount", 0);
			Text label = count == 0 ? Text.translatable("gui.tudursvehiclemod.fire_support.no_waypoints")
					: Text.translatable("gui.tudursvehiclemod.fire_support.waypoint", state.getInt("WaypointIndex", 0) + 1, count);
			context.drawCenteredTextWithShadow(this.textRenderer, label, RIGHT_X + RIGHT_WIDTH / 2, 43, 0xFFFFFFFF);
		} else if (FireSupportCenterBlockEntity.TargetMode.DESIGNATOR.name().equals(mode)) {
			context.drawWrappedText(this.textRenderer, Text.translatable("gui.tudursvehiclemod.fire_support.designator_hint"), RIGHT_X, 40, RIGHT_WIDTH, DIM_COLOR, false);
		}
		int[] target = state.getIntArray("Target").orElse(null);
		if (target != null && target.length == 3) {
			context.drawText(this.textRenderer, Text.translatable("gui.tudursvehiclemod.fire_support.target", target[0], target[1], target[2]), RIGHT_X, 80, TEXT_COLOR, false);
			context.drawText(this.textRenderer, Text.translatable("gui.tudursvehiclemod.fire_support.distance", state.getInt("TargetDistance", 0)), RIGHT_X, 90, TEXT_COLOR, false);
		} else {
			context.drawText(this.textRenderer, Text.translatable("gui.tudursvehiclemod.fire_support.no_target"), RIGHT_X, 80, WARN_COLOR, false);
		}

		// The last notice, then the units it named.
		String messageKey = state.getString("MessageKey", "");
		if (!messageKey.isEmpty()) {
			int y = 190;
			for (OrderedText line : this.textRenderer.wrapLines(Text.translatable(messageKey), RIGHT_WIDTH)) {
				if (y > this.backgroundHeight - 10) {
					break;
				}
				context.drawText(this.textRenderer, line, RIGHT_X, y, TEXT_COLOR, false);
				y += 10;
			}
			NbtList args = state.getListOrEmpty("MessageArgs");
			for (int i = 0; i < args.size() && y <= this.backgroundHeight - 10; i++) {
				Text detail = FireSupportCenterBlockEntity.tudursvehiclemod$problemText(args.getString(i, ""));
				for (OrderedText line : this.textRenderer.wrapLines(detail, RIGHT_WIDTH)) {
					if (y > this.backgroundHeight - 10) {
						break;
					}
					context.drawText(this.textRenderer, line, RIGHT_X, y, WARN_COLOR, false);
					y += 10;
				}
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	private FireSupportCenterBlockEntity.UnitStatus tudursvehiclemod$status(String name) {
		try {
			return FireSupportCenterBlockEntity.UnitStatus.valueOf(name);
		} catch (IllegalArgumentException e) {
			return FireSupportCenterBlockEntity.UnitStatus.EMPTY;
		}
	}

	private String tudursvehiclemod$trim(String text, int width) {
		return this.textRenderer.getWidth(text) <= width ? text : this.textRenderer.trimToWidth(text, width - this.textRenderer.getWidth("...")) + "...";
	}

	/** Keeps typing in a coordinate field from triggering the inventory key (closing the screen on "E"). */
	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
		for (TextFieldWidget field : List.of(this.relXField, this.relYField, this.relZField)) {
			if (field.isFocused() && field.visible) {
				if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
					return super.keyPressed(input);
				}
				if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
					this.tudursvehiclemod$applyRelative();
					return true;
				}
				return field.keyPressed(input) || true;
			}
		}
		return super.keyPressed(input);
	}
}
