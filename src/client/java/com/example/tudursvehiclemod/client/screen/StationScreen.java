package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.screen.StationScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** No custom GUI background PNG asset of its own - same reasoning as FuelRefinerScreen's own doc (a simple beveled-rectangle slot background instead of cropping a vanilla texture). Slot 0 the binding stick - see block.StationBlockEntity's own doc. */
public class StationScreen extends HandledScreen<StationScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;

	public StationScreen(StationScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 166;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;
		context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL_COLOR);

		// This screen's own slot (binding stick) - see StationScreenHandler's own constructor for these exact coordinates.
		SlotGrid.drawSlot(context, x + 80, y + 35);

		// Player's own inventory grid (3 rows + hotbar) - see StationScreenHandler's own constructor for these exact coordinates.
		SlotGrid.drawPlayerInventoryGrid(context, x, y + 84, y + 142);
	}
}
