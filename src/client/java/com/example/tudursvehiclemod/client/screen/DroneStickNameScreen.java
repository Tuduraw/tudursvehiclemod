package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.network.DroneStickRegisterPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** item.DroneControlStickItem's own naming screen - opened client-side the instant the player right-clicks a vehicle while holding this item (see entity.AbstractVehicleEntity's own interact() doc for where that dispatch happens), letting the player type any name they want for the stick rather than it always being auto-named after the vehicle. This happens EVERY time the stick is (re-)registered, not just the first time: this screen always opens, always pre-filled with the vehicle's own display name as a convenient starting point (the player can freely edit or replace it), and confirming always sends the full registration + chosen name back to the server together (see DroneStickRegisterPayload's own doc) - there's no longer a "silently keep whatever name it already has" path at all. Cancel (Escape, or the Cancel button) closes without registering anything, leaving the stick exactly as it was. */
public class DroneStickNameScreen extends Screen {

	private final boolean mainHand;
	private final String vehicleId;
	private final String suggestedName;

	private TextFieldWidget nameField;

	public DroneStickNameScreen(boolean mainHand, String vehicleId, String suggestedName) {
		super(Text.translatable("gui.tudursvehiclemod.drone_stick_name"));
		this.mainHand = mainHand;
		this.vehicleId = vehicleId;
		this.suggestedName = suggestedName;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int centerY = this.height / 2;

		this.nameField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY - 30, 200, 20,
				Text.translatable("gui.tudursvehiclemod.drone_stick_name.field"));
		this.nameField.setMaxLength(64);
		this.nameField.setText(this.suggestedName);
		this.addDrawableChild(this.nameField);
		this.setInitialFocus(this.nameField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_stick_name.confirm"),
				button -> this.tudursvehiclemod$confirm()
		).dimensions(centerX - 100, centerY, 96, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.cancel"),
				button -> this.close()
		).dimensions(centerX + 4, centerY, 96, 20).build());
	}

	/** Falls back to the suggested (vehicle's own display) name if the player clears the field entirely, rather than sending an empty custom name. */
	private void tudursvehiclemod$confirm() {
		String typed = this.nameField.getText().strip();
		String finalName = typed.isEmpty() ? this.suggestedName : typed;
		ClientPlayNetworking.send(new DroneStickRegisterPayload(this.mainHand, this.vehicleId, finalName));
		this.close();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
