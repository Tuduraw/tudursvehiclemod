package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.screen.FuelRefinerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** No custom GUI background PNG asset of its own. Slot backgrounds are drawn
 * as a simple beveled rectangle (dark top-left, light bottom-right, matching
 * vanilla's own recessed-slot look) rather than cropped from a vanilla
 * texture - an earlier attempt at cropping generic_54.png's own slot sprite
 * rendered as a broken, split-looking square (the exact source coordinates
 * for that sprite in this project's own Minecraft version turned out not to
 * be reliable to guess at), so this avoids that risk entirely while still
 * looking like a normal inventory slot. */
public class FuelRefinerScreen extends HandledScreen<FuelRefinerScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int PROGRESS_BAR_BG = 0xFF373737;
	private static final int PROGRESS_BAR_FILL = 0xFFE0892C;

	public FuelRefinerScreen(FuelRefinerScreenHandler handler, PlayerInventory inventory, Text title) {
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

		// This screen's own slots (fuel item, fuel can) - see FuelRefinerScreenHandler's own constructor for these exact coordinates.
		SlotGrid.drawSlot(context, x + 62, y + 24);
		SlotGrid.drawSlot(context, x + 62, y + 52);

		// Progress bar for stored burn time, between the two slots.
		int barX = x + 90;
		int barY = y + 30;
		int barWidth = 40;
		int barHeight = 8;
		context.fill(barX, barY, barX + barWidth, barY + barHeight, PROGRESS_BAR_BG);
		int filled = Math.round(barWidth * this.getScreenHandler().getStoredBurnTimeRatio());
		if (filled > 0) {
			context.fill(barX, barY, barX + filled, barY + barHeight, PROGRESS_BAR_FILL);
		}

		// Player's own inventory grid (3 rows + hotbar) - see FuelRefinerScreenHandler's own constructor for these exact coordinates.
		SlotGrid.drawPlayerInventoryGrid(context, x, y + 84, y + 142);
	}
}
