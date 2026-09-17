package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.screen.VehicleConverterScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** No custom GUI background PNG asset of its own - see client.screen.FuelRefinerScreen's own doc for why (slot backgrounds drawn as simple beveled rectangles via SlotGrid instead of a cropped vanilla texture). */
public class VehicleConverterScreen extends HandledScreen<VehicleConverterScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;

	public VehicleConverterScreen(VehicleConverterScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 176;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		int centerX = this.x + this.backgroundWidth / 2;
		// Per this screen's own drawBackground() layout: the page buttons sit either side of the slot itself (which VehicleConverterScreenHandler's own constructor fixes at x+80, y+35), with confirm below both.
		int pageButtonY = this.y + 34;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"),
						button -> this.tudursvehiclemod$clickButton(VehicleConverterScreenHandler.BUTTON_PREVIOUS_PAGE))
				.dimensions(centerX - 58, pageButtonY, 20, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"),
						button -> this.tudursvehiclemod$clickButton(VehicleConverterScreenHandler.BUTTON_NEXT_PAGE))
				.dimensions(centerX + 38, pageButtonY, 20, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.vehicle_converter.confirm"),
						button -> this.tudursvehiclemod$clickButton(VehicleConverterScreenHandler.BUTTON_CONFIRM))
				.dimensions(centerX - 50, this.y + 58, 100, 20).build());
	}

	/** Sends this button press to the server via the standard vanilla clickButton mechanism (same one an enchanting table/loom already use) - see VehicleConverterScreenHandler's own onButtonClick() doc for what each ID actually does. */
	private void tudursvehiclemod$clickButton(int buttonId) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.getScreenHandler().syncId, buttonId);
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;
		context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL_COLOR);

		// This screen's own single slot (base item input) - MUST match VehicleConverterScreenHandler's own Slot coordinate exactly (x+80, y+35): these two had drifted apart.
		SlotGrid.drawSlot(context, x + 80, y + 35);

		// Currently-selected target, above the slot/page-button row - covers addon-registered targets too (see item.VehicleConverterTargets' own doc).
		com.example.tudursvehiclemod.item.VehicleConverterTarget selected =
				com.example.tudursvehiclemod.item.VehicleConverterTargets.byIndex(
						this.getScreenHandler().getSelectedCategoryIndex());
		if (selected != null) {
			Text categoryText = Text.translatable(selected.translationKey());
			int textWidth = this.textRenderer.getWidth(categoryText);
			context.drawText(this.textRenderer, categoryText, x + this.backgroundWidth / 2 - textWidth / 2, y + 22, 0xFF404040, false);
		}

		// Player's own inventory grid (3 rows + hotbar) - see VehicleConverterScreenHandler's own constructor for these exact coordinates.
		SlotGrid.drawPlayerInventoryGrid(context, x, y + 84, y + 142);
	}
}
