package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.network.DroneCenterConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Drone Center's own top-level config screen (, and "Drone Center基本設定を専用画面へ分離"/"Drone Center編隊設定を専用画面へ分離" for why this no longer hosts any numeric controls directly at all). Per two successive direct reports that this screen didn't fit at standard GUI scale: FIRST the formationSize/formationType/formationSpacing controls moved to client.screen.DroneCenterFormationConfigScreen, and once that STILL didn't fit, the cruise speed/orbit altitude/turn radius controls ALSO moved to client.screen.DroneCenterBasicConfigScreen. This screen is now purely a menu of buttons: two toggles (active/redstone control) plus five buttons leading to dedicated sub-screens (basic settings, detailed route settings, item slots, formation settings, dummy pilot) and a close button - matching the existing "詳細設定" pattern (one button leading to a whole dedicated sub-screen) throughout, rather than any inline row of controls. */
public class DroneCenterConfigScreen extends Screen {

	private final int blockX;
	private final int blockY;
	private final int blockZ;

	private float speedFraction;
	private float orbitAltitude;
	private float radiusMultiplier;
	private boolean active;
	private boolean redstoneControlEnabled;

	private int formationSize;
	private com.example.tudursvehiclemod.asset.FormationType formationType;
	private double formationSpacing;
	private double formationElementSpacing;

	private ButtonWidget activeToggleButton;
	private ButtonWidget redstoneControlToggleButton;

	public DroneCenterConfigScreen(int blockX, int blockY, int blockZ,
			float speedFraction, float orbitAltitude, float radiusMultiplier, boolean active,
			boolean redstoneControlEnabled, String formationConfig) {
		super(Text.translatable("gui.tudursvehiclemod.drone_center_config"));
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
		this.speedFraction = speedFraction;
		this.orbitAltitude = orbitAltitude;
		this.radiusMultiplier = radiusMultiplier;
		this.active = active;
		this.redstoneControlEnabled = redstoneControlEnabled;
		// Per network.DroneCenterConfigOpenPayload's own formationConfig doc: "formationSize,formationType,formationSpacing,formationElementSpacing" - malformed input (a mismatched client/server version) falls back to no-formation defaults rather than crashing this screen's own construction.
		int parsedSize = 1;
		com.example.tudursvehiclemod.asset.FormationType parsedType = com.example.tudursvehiclemod.asset.FormationType.LINE_ABREAST;
		double parsedSpacing = 8.0;
		double parsedElementSpacing = -1.0;
		String[] parts = formationConfig.split(",", -1);
		if (parts.length == 4) {
			try {
				parsedSize = Math.max(1, Integer.parseInt(parts[0]));
				parsedType = com.example.tudursvehiclemod.asset.FormationType.valueOf(parts[1]);
				parsedSpacing = Double.parseDouble(parts[2]);
				parsedElementSpacing = Double.parseDouble(parts[3]);
			} catch (IllegalArgumentException ignored) {
			}
		}
		this.formationSize = parsedSize;
		this.formationType = parsedType;
		this.formationSpacing = parsedSpacing;
		this.formationElementSpacing = parsedElementSpacing;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		// Per this class's own doc: no inline rows of controls remain at all now - every value lives on its own dedicated sub-screen instead, so this is purely a stack of buttons, 24px apart.
		int startY = this.height / 2 - 90;

		this.activeToggleButton = ButtonWidget.builder(this.tudursvehiclemod$activeText(), button -> {
					this.active = !this.active;
					this.activeToggleButton.setMessage(this.tudursvehiclemod$activeText());
					this.tudursvehiclemod$sendUpdate();
				}
		).dimensions(centerX - 100, startY, 200, 20).build();
		this.activeToggleButton.active = !this.redstoneControlEnabled;
		this.addDrawableChild(this.activeToggleButton);

		// While enabled, the manual toggle above is grayed out (its own effect would just be immediately overridden by the next redstone signal change anyway) and this center's own active state instead tracks whatever redstone power it's currently receiving.
		this.redstoneControlToggleButton = ButtonWidget.builder(this.tudursvehiclemod$redstoneControlText(), button -> {
					this.redstoneControlEnabled = !this.redstoneControlEnabled;
					this.redstoneControlToggleButton.setMessage(this.tudursvehiclemod$redstoneControlText());
					this.activeToggleButton.active = !this.redstoneControlEnabled;
					this.tudursvehiclemod$sendUpdate();
				}
		).dimensions(centerX - 100, startY + 24, 200, 20).build();
		this.addDrawableChild(this.redstoneControlToggleButton);

		// Per a further direct report that this screen still didn't fit at standard GUI scale even after the formation controls already moved out: opens client.screen.DroneCenterBasicConfigScreen, hosting the cruise speed/orbit altitude/turn radius controls that used to live inline here.
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.basic_settings"),
				button -> this.client.setScreen(new com.example.tudursvehiclemod.client.screen.DroneCenterBasicConfigScreen(
						this.blockX, this.blockY, this.blockZ, this.speedFraction, this.orbitAltitude, this.radiusMultiplier))
		).dimensions(centerX - 100, startY + 48, 200, 20).build());

		// Opens the waypoint route editor - requests the current route from the server first (this screen's own client-side state doesn't otherwise know it), which responds with DroneWaypointsOpenPayload to actually open the screen.
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.detailed_settings"),
				button -> ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.DroneWaypointsRequestPayload(
						this.blockX, this.blockY, this.blockZ))
		).dimensions(centerX - 100, startY + 76, 200, 20).build());

		// This button is now the only way to reach it.
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.item_slots"),
				button -> ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.OpenBlockSlotsPayload(
						this.blockX, this.blockY, this.blockZ))
		).dimensions(centerX - 100, startY + 100, 200, 20).build());

		// Opens client.screen.DroneCenterFormationConfigScreen instead of the slot UI directly - that screen hosts the formationSize/formationType/formationSpacing controls (moved off this screen entirely) plus its own button through to the slot UI, matching the existing "詳細設定" pattern (one button leading to a whole dedicated sub-screen) rather than adding more rows/buttons here.
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.formation_settings"),
				button -> this.client.setScreen(new com.example.tudursvehiclemod.client.screen.DroneCenterFormationConfigScreen(
						this.blockX, this.blockY, this.blockZ, this.formationSize, this.formationType, this.formationSpacing, this.formationElementSpacing))
		).dimensions(centerX - 100, startY + 124, 200, 20).build());

		// Since the selectable skin list is a server-side scan (see DummyPilotConfigRequestPayload's own doc).
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.dummy_pilot"),
				button -> ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.DummyPilotConfigRequestPayload(
						this.blockX, this.blockY, this.blockZ))
		).dimensions(centerX - 100, startY + 148, 200, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.close"),
				button -> this.close()
		).dimensions(centerX - 100, startY + 172, 200, 20).build());
	}

	private Text tudursvehiclemod$activeText() {
		return Text.translatable("gui.tudursvehiclemod.drone_center_config.active",
				Text.translatable(this.active ? "options.on" : "options.off"));
	}

	private Text tudursvehiclemod$redstoneControlText() {
		return Text.translatable("gui.tudursvehiclemod.drone_center_config.redstone_control",
				Text.translatable(this.redstoneControlEnabled ? "options.on" : "options.off"));
	}

	/** Encodes this screen's own current formation settings the same way block.DroneCenterBlockEntity's own tudursvehiclemod$encodeFormationConfig() does server-side - kept as a local mirror rather than a shared static helper, since this screen's own fields aren't actually backed by a DroneCenterBlockEntity instance at all (client-side, no block entity reference here). Per two successive direct reports that overflowed the screen at standard GUI scale, neither the formation controls nor the basic speed/altitude/radius controls are editable on THIS screen at all anymore (moved to their own dedicated screens, each opened via its own dedicated button) - both are simply round-tripped unchanged here whenever the active/redstoneControlEnabled toggles change. */
	private void tudursvehiclemod$sendUpdate() {
		String formationConfig = this.formationSize + "," + this.formationType.name() + "," + this.formationSpacing + "," + this.formationElementSpacing;
		ClientPlayNetworking.send(new DroneCenterConfigUpdatePayload(
				this.blockX, this.blockY, this.blockZ,
				this.speedFraction, this.orbitAltitude, this.radiusMultiplier, this.active, this.redstoneControlEnabled,
				formationConfig));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
