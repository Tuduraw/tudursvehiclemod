package com.example.tudursvehiclemod.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Developer/debug-only actions (not on the normal player-facing menu): re-running /reload, toggling the hit-detection mesh visualizer. */
public class DebugMenuScreen extends Screen {

	public DebugMenuScreen() {
		super(Text.translatable("gui.tudursvehiclemod.debug_menu"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 2 - 36 - 48;

		this.addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.tudursvehiclemod.reload_addons"),
				button -> {
					if (this.client != null && this.client.player != null) {
						this.client.player.networkHandler.sendChatCommand("reload");
					}
					this.close();
				}
		).dimensions(centerX - 100, startY, 200, 20).build());

		this.addDrawableChild(ButtonWidget.builder(
				tudursvehiclemod$hitboxMeshButtonText(),
				button -> {
					com.example.tudursvehiclemod.client.debug.HitDetectionMeshDebugRenderer.enabled =
							!com.example.tudursvehiclemod.client.debug.HitDetectionMeshDebugRenderer.enabled;
					button.setMessage(tudursvehiclemod$hitboxMeshButtonText());
				}
		).dimensions(centerX - 100, startY + 24, 200, 20).build());

		this.addDrawableChild(ButtonWidget.builder(
				tudursvehiclemod$collisionBoxButtonText(),
				button -> {
					com.example.tudursvehiclemod.client.debug.CollisionBoxDebugRenderer.enabled =
							!com.example.tudursvehiclemod.client.debug.CollisionBoxDebugRenderer.enabled;
					button.setMessage(tudursvehiclemod$collisionBoxButtonText());
				}
		).dimensions(centerX - 100, startY + 48, 200, 20).build());

		// Same convention as the two toggle buttons just above.
		this.addDrawableChild(ButtonWidget.builder(
				tudursvehiclemod$seatPositionsButtonText(),
				button -> {
					com.example.tudursvehiclemod.client.debug.SeatPositionDebugRenderer.enabled =
							!com.example.tudursvehiclemod.client.debug.SeatPositionDebugRenderer.enabled;
					button.setMessage(tudursvehiclemod$seatPositionsButtonText());
				}
		).dimensions(centerX - 100, startY + 72, 200, 20).build());

		// Whether client.VehicleHud still draws at
		// all while in third-person view (first person is unaffected
		// either way - see VehicleModConfig's own showHudInThirdPerson
		// doc).
		this.addDrawableChild(ButtonWidget.builder(
				tudursvehiclemod$thirdPersonHudButtonText(),
				button -> {
					com.example.tudursvehiclemod.client.VehicleModConfig config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
					config.showHudInThirdPerson = !config.showHudInThirdPerson;
					config.save();
					button.setMessage(tudursvehiclemod$thirdPersonHudButtonText());
				}
		).dimensions(centerX - 100, startY + 96, 200, 20).build());

		// Opens ConfigMenuScreen, which edits VehicleModConfig in place - see that class's own doc for how each setting applies.
		this.addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.tudursvehiclemod.config_menu"),
				button -> {
					if (this.client != null) {
						this.client.setScreen(new ConfigMenuScreen());
					}
				}
		).dimensions(centerX - 100, startY + 120, 200, 20).build());

		// Disabled with an explanatory tooltip whenever this client isn't hosting the world being played (client.getServer() == null - true for a genuinely remote dedicated server connection), rather than opening a screen that would edit a config instance nobody's own dedicated server will ever actually read.
		boolean canEditServerConfig = this.client != null && this.client.getServer() != null;
		ButtonWidget serverConfigButton = ButtonWidget.builder(
				Text.translatable("gui.tudursvehiclemod.server_config_menu"),
				button -> {
					if (this.client != null) {
						this.client.setScreen(new ServerConfigMenuScreen());
					}
				}
		).dimensions(centerX - 100, startY + 144, 200, 20).build();
		serverConfigButton.active = canEditServerConfig;
		if (!canEditServerConfig) {
			serverConfigButton.setTooltip(Tooltip.of(Text.translatable("gui.tudursvehiclemod.server_config_menu.unavailable_tooltip")));
		}
		this.addDrawableChild(serverConfigButton);

		this.addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.tudursvehiclemod.close"),
				button -> this.close()
		).dimensions(centerX - 100, startY + 168, 200, 20).build());
	}

	/** Label reflects the toggle's own current on/off state, same convention as a vanilla options-screen toggle button. */
	private static Text tudursvehiclemod$thirdPersonHudButtonText() {
		boolean enabled = com.example.tudursvehiclemod.client.VehicleModClient.getConfig().showHudInThirdPerson;
		return Text.translatable("gui.tudursvehiclemod.toggle_third_person_hud",
				Text.translatable(enabled ? "options.on" : "options.off"));
	}

	/** Label reflects the toggle's own current on/off state, same convention as a vanilla options-screen toggle button. */
	private static Text tudursvehiclemod$hitboxMeshButtonText() {
		boolean enabled = com.example.tudursvehiclemod.client.debug.HitDetectionMeshDebugRenderer.enabled;
		return Text.translatable("gui.tudursvehiclemod.toggle_hitbox_mesh",
				Text.translatable(enabled ? "options.on" : "options.off"));
	}

	/** Label reflects the toggle's own current on/off state, same convention as a vanilla options-screen toggle button. */
	private static Text tudursvehiclemod$collisionBoxButtonText() {
		boolean enabled = com.example.tudursvehiclemod.client.debug.CollisionBoxDebugRenderer.enabled;
		return Text.translatable("gui.tudursvehiclemod.toggle_collision_box",
				Text.translatable(enabled ? "options.on" : "options.off"));
	}

	/** Label reflects the toggle's own current on/off state, same convention as a vanilla options-screen toggle button. */
	private static Text tudursvehiclemod$seatPositionsButtonText() {
		boolean enabled = com.example.tudursvehiclemod.client.debug.SeatPositionDebugRenderer.enabled;
		return Text.translatable("gui.tudursvehiclemod.toggle_seat_positions",
				Text.translatable(enabled ? "options.on" : "options.off"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
