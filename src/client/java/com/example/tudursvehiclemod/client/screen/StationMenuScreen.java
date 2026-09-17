package com.example.tudursvehiclemod.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** block.StationBlock's own menu screen - opened on every right-click of that block (sneaking or not, identically - see that class's own doc for why sneaking could no longer be relied on to reach the item slot UI at all). Offers a "Toggle Remote Control" button (running the exact logic that used to fire immediately on right-click) and an "Item Slots" button (opens the container UI - see network.OpenBlockSlotsPayload's own doc), consolidating both into one reachable screen instead. */
public class StationMenuScreen extends Screen {

	private final int blockX;
	private final int blockY;
	private final int blockZ;

	public StationMenuScreen(int blockX, int blockY, int blockZ) {
		super(Text.translatable("gui.tudursvehiclemod.station_menu"));
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int centerY = this.height / 2;

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.station_menu.toggle_remote_control"),
				button -> {
					ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.StationToggleRemoteControlPayload(
							this.blockX, this.blockY, this.blockZ));
					this.close();
				}
		).dimensions(centerX - 100, centerY - 34, 200, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.station_menu.item_slots"),
				button -> ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.OpenBlockSlotsPayload(
						this.blockX, this.blockY, this.blockZ))
		).dimensions(centerX - 100, centerY - 10, 200, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.close"),
				button -> this.close()
		).dimensions(centerX - 100, centerY + 14, 200, 20).build());
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
