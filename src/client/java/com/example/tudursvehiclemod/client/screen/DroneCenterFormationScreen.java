package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.screen.DroneCenterFormationScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** The wingman-slot screen, mirroring VehicleMenuScreen's own pagination pattern (SlotGrid-drawn cells, prev/next page buttons) but much simpler - no repair/resupply tabs, just the slot grid itself plus a "N機編成" label showing the currently-synced formation size. No custom GUI background PNG of its own, same reasoning as VehicleMenuScreen/FuelRefinerScreen (see those classes' own doc). */
public class DroneCenterFormationScreen extends HandledScreen<DroneCenterFormationScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int BORDER_COLOR = 0xFF8B8B8B;

	public DroneCenterFormationScreen(DroneCenterFormationScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 18 + DroneCenterFormationScreenHandler.PAGE_ROWS * 18 + 14 + 3 * 18 + 14 + 18;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		int slotCount = this.getScreenHandler().tudursvehiclemod$getFormationSize() - 1;
		int totalPages = slotCount <= 0 ? 1
				: (slotCount + DroneCenterFormationScreenHandler.PAGE_SIZE - 1) / DroneCenterFormationScreenHandler.PAGE_SIZE;
		if (totalPages > 1) {
			int currentPage = this.getScreenHandler().tudursvehiclemod$getCurrentPage();
			int navY = this.y + 4;
			ButtonWidget prevPage = ButtonWidget.builder(Text.literal("<"), button -> {
				if (this.client != null && this.client.interactionManager != null) {
					this.client.interactionManager.clickButton(this.getScreenHandler().syncId, DroneCenterFormationScreenHandler.PAGE_PREV_BUTTON_ID);
				}
			}).dimensions(this.x + this.backgroundWidth - 40, navY, 16, 16).build();
			prevPage.active = currentPage > 0;
			this.addDrawableChild(prevPage);
			ButtonWidget nextPage = ButtonWidget.builder(Text.literal(">"), button -> {
				if (this.client != null && this.client.interactionManager != null) {
					this.client.interactionManager.clickButton(this.getScreenHandler().syncId, DroneCenterFormationScreenHandler.PAGE_NEXT_BUTTON_ID);
				}
			}).dimensions(this.x + this.backgroundWidth - 20, navY, 16, 16).build();
			nextPage.active = currentPage < totalPages - 1;
			this.addDrawableChild(nextPage);
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;
		context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL_COLOR);
		tudursvehiclemod$fillBorder(context, x, y, this.backgroundWidth, this.backgroundHeight, BORDER_COLOR);

		int formationSize = this.getScreenHandler().tudursvehiclemod$getFormationSize();
		context.drawText(this.textRenderer, Text.translatable("gui.tudursvehiclemod.drone_center_formation.size", formationSize),
				x + 8, y + 6, 0xFFFFFF, false);

		int slotCount = Math.max(0, formationSize - 1);
		int pageStart = this.getScreenHandler().tudursvehiclemod$getCurrentPage() * DroneCenterFormationScreenHandler.PAGE_SIZE;
		int visibleThisPage = Math.min(DroneCenterFormationScreenHandler.PAGE_SIZE, Math.max(0, slotCount - pageStart));
		for (int i = 0; i < visibleThisPage; i++) {
			int col = i % 9;
			int row = i / 9;
			SlotGrid.drawSlot(context, x + 8 + col * 18, y + 18 + row * 18);
		}

		int playerInvY = y + 18 + DroneCenterFormationScreenHandler.PAGE_ROWS * 18 + 14;
		SlotGrid.drawPlayerInventoryGrid(context, x, playerInvY, playerInvY + 58);
	}

	/** drawBorder(int, int, int, int, int) was removed from DrawContext as of this project's own Minecraft version. */
	private static void tudursvehiclemod$fillBorder(DrawContext context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y, x + 1, y + height, color);
		context.fill(x + width - 1, y, x + width, y + height, color);
	}
}
