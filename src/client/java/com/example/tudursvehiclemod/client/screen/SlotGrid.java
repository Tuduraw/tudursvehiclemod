package com.example.tudursvehiclemod.client.screen;

import net.minecraft.client.gui.DrawContext;

/** Draws a simple beveled-rectangle slot background (dark top-left edge,
 * light bottom-right edge, mid-gray fill) matching vanilla's own recessed
 * slot look, without depending on cropping any actual vanilla texture file -
 * see FuelRefinerScreen's own doc for why. Both custom screens in this
 * project share this, plus the same 8+col*18 / row*18 coordinate convention
 * for the player's own inventory grid, so it's centralized here rather than
 * duplicated. */
final class SlotGrid {

	private static final int SLOT_FILL = 0xFF8B8B8B;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;

	private SlotGrid() {
	}

	/** Draws one 18x18 slot background at slotX,slotY - the SAME coordinate
	 * a Slot object itself uses (matching where HandledScreen will actually
	 * render an item sitting in it), not an already-offset "outline"
	 * coordinate - this method applies its own -1 inset internally. */
	static void drawSlot(DrawContext context, int slotX, int slotY) {
		int x = slotX - 1;
		int y = slotY - 1;
		context.fill(x, y, x + 18, y + 18, SLOT_FILL);
		// Shadow: top edge + left edge.
		context.fill(x, y, x + 18, y + 1, SLOT_SHADOW);
		context.fill(x, y, x + 1, y + 18, SLOT_SHADOW);
		// Highlight: bottom edge + right edge.
		context.fill(x, y + 17, x + 18, y + 18, SLOT_HIGHLIGHT);
		context.fill(x + 17, y, x + 18, y + 18, SLOT_HIGHLIGHT);
	}

	/** Draws the standard 3-row main inventory grid + hotbar row, at the
	 * SAME "8 + col*18" X offset (relative to panelX) and row spacing both
	 * screen handlers in this project already use for their own player
	 * inventory slots - mainInvTopY/hotbarY are absolute screen
	 * coordinates (i.e. already include the panel's own y offset), matching
	 * each Slot's own y coordinate for row 0. */
	static void drawPlayerInventoryGrid(DrawContext context, int panelX, int mainInvTopY, int hotbarY) {
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawSlot(context, panelX + 8 + col * 18, mainInvTopY + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			drawSlot(context, panelX + 8 + col * 18, hotbarY);
		}
	}
}
