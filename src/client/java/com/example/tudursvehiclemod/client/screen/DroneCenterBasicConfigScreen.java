package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.network.DroneCenterBasicConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Per a further direct report that client.screen.DroneCenterConfigScreen still didn't fit at standard GUI scale even after the formation controls already moved to their own screen: the cruise speed/orbit altitude/turn radius rows moved to this dedicated screen too, opened via a button from that screen - same "詳細設定"-style pattern client.screen.DroneCenterFormationConfigScreen's own separation already established. Mirrors that original 3-row +/-/field layout exactly, unchanged from before this split. */
public class DroneCenterBasicConfigScreen extends Screen {

	private final int blockX;
	private final int blockY;
	private final int blockZ;

	private float speedFraction;
	private float orbitAltitude;
	private float radiusMultiplier;

	private static final float SPEED_STEP = 0.05f;
	private static final float ALTITUDE_STEP = 1.0f;
	private static final float RADIUS_STEP = 0.1f;
	private static final float MIN_SPEED_FRACTION = 0.05f;
	private static final float MAX_SPEED_FRACTION = 1.0f;
	private static final float MIN_ORBIT_ALTITUDE = 2.0f;
	private static final float MAX_ORBIT_ALTITUDE = 200.0f;
	private static final float MIN_RADIUS_MULTIPLIER = 0.1f;
	private static final float MAX_RADIUS_MULTIPLIER = 5.0f;

	/** Only 3 digits' worth of width - nothing here ever needs a 4th digit (percentages are clamped to 0-500, altitude to 0-200). */
	private static final int FIELD_WIDTH = 30;
	private static final int LABEL_WIDTH = 110;
	private static final int ROW_HEIGHT = 24;

	private TextFieldWidget speedField;
	private TextFieldWidget altitudeField;
	private TextFieldWidget radiusField;

	/** Set while a text field's own setChangedListener is applying a value FROM the +/- buttons (or initial population), so that listener doesn't immediately re-parse its own just-set text and send a redundant update. */
	private boolean populatingFieldText;

	public DroneCenterBasicConfigScreen(int blockX, int blockY, int blockZ,
			float speedFraction, float orbitAltitude, float radiusMultiplier) {
		super(Text.translatable("gui.tudursvehiclemod.drone_center_basic_config"));
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
		this.speedFraction = speedFraction;
		this.orbitAltitude = orbitAltitude;
		this.radiusMultiplier = radiusMultiplier;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 2 - 60;

		int labelX = centerX - LABEL_WIDTH - 6;
		int minusX = centerX - 4;
		int fieldX = minusX + 22;
		int plusX = fieldX + FIELD_WIDTH + 2;
		int percentX = plusX + 22;

		int speedRowY = startY;
		int altitudeRowY = startY + ROW_HEIGHT;
		int radiusRowY = startY + ROW_HEIGHT * 2;
		int afterRowsY = startY + ROW_HEIGHT * 3 + 8;

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.speed_label"), button -> {}
		).dimensions(labelX, speedRowY, LABEL_WIDTH, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.speed_minus"),
				button -> this.tudursvehiclemod$adjustSpeed(-SPEED_STEP)
		).dimensions(minusX, speedRowY, 20, 20).build());
		this.speedField = new TextFieldWidget(this.textRenderer, fieldX, speedRowY, FIELD_WIDTH, 20,
				Text.translatable("gui.tudursvehiclemod.drone_center_config.speed_field"));
		this.speedField.setMaxLength(3);
		this.speedField.setChangedListener(this::tudursvehiclemod$onSpeedFieldChanged);
		this.addDrawableChild(this.speedField);
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.speed_plus"),
				button -> this.tudursvehiclemod$adjustSpeed(SPEED_STEP)
		).dimensions(plusX, speedRowY, 20, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("%"), button -> {}
		).dimensions(percentX, speedRowY, 20, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.altitude_label"), button -> {}
		).dimensions(labelX, altitudeRowY, LABEL_WIDTH, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.altitude_minus"),
				button -> this.tudursvehiclemod$adjustAltitude(-ALTITUDE_STEP)
		).dimensions(minusX, altitudeRowY, 20, 20).build());
		this.altitudeField = new TextFieldWidget(this.textRenderer, fieldX, altitudeRowY, FIELD_WIDTH, 20,
				Text.translatable("gui.tudursvehiclemod.drone_center_config.altitude_field"));
		this.altitudeField.setMaxLength(3);
		this.altitudeField.setChangedListener(this::tudursvehiclemod$onAltitudeFieldChanged);
		this.addDrawableChild(this.altitudeField);
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.altitude_plus"),
				button -> this.tudursvehiclemod$adjustAltitude(ALTITUDE_STEP)
		).dimensions(plusX, altitudeRowY, 20, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.radius_label"), button -> {}
		).dimensions(labelX, radiusRowY, LABEL_WIDTH, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.radius_minus"),
				button -> this.tudursvehiclemod$adjustRadius(-RADIUS_STEP)
		).dimensions(minusX, radiusRowY, 20, 20).build());
		this.radiusField = new TextFieldWidget(this.textRenderer, fieldX, radiusRowY, FIELD_WIDTH, 20,
				Text.translatable("gui.tudursvehiclemod.drone_center_config.radius_field"));
		this.radiusField.setMaxLength(3);
		this.radiusField.setChangedListener(this::tudursvehiclemod$onRadiusFieldChanged);
		this.addDrawableChild(this.radiusField);
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.radius_plus"),
				button -> this.tudursvehiclemod$adjustRadius(RADIUS_STEP)
		).dimensions(plusX, radiusRowY, 20, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("%"), button -> {}
		).dimensions(percentX, radiusRowY, 20, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.close"),
				button -> this.close()
		).dimensions(centerX - 100, afterRowsY, 200, 20).build());

		// Populates each field's own initial text without treating it as a user edit (see populatingFieldText's own doc).
		this.populatingFieldText = true;
		this.speedField.setText(String.valueOf(Math.round(this.speedFraction * 100)));
		this.altitudeField.setText(String.valueOf(Math.round(this.orbitAltitude)));
		this.radiusField.setText(String.valueOf(Math.round(this.radiusMultiplier * 100)));
		this.populatingFieldText = false;
	}

	private void tudursvehiclemod$adjustSpeed(float delta) {
		this.speedFraction = Math.max(MIN_SPEED_FRACTION, Math.min(MAX_SPEED_FRACTION, this.speedFraction + delta));
		this.populatingFieldText = true;
		this.speedField.setText(String.valueOf(Math.round(this.speedFraction * 100)));
		this.populatingFieldText = false;
		this.tudursvehiclemod$sendUpdate();
	}

	private void tudursvehiclemod$adjustAltitude(float delta) {
		this.orbitAltitude = Math.max(MIN_ORBIT_ALTITUDE, Math.min(MAX_ORBIT_ALTITUDE, this.orbitAltitude + delta));
		this.populatingFieldText = true;
		this.altitudeField.setText(String.valueOf(Math.round(this.orbitAltitude)));
		this.populatingFieldText = false;
		this.tudursvehiclemod$sendUpdate();
	}

	private void tudursvehiclemod$adjustRadius(float delta) {
		this.radiusMultiplier = Math.max(MIN_RADIUS_MULTIPLIER, Math.min(MAX_RADIUS_MULTIPLIER, this.radiusMultiplier + delta));
		this.populatingFieldText = true;
		this.radiusField.setText(String.valueOf(Math.round(this.radiusMultiplier * 100)));
		this.populatingFieldText = false;
		this.tudursvehiclemod$sendUpdate();
	}

	/** Parses the field's own current text as a percentage (e.g. "50" -> 0.5) - silently ignores anything unparseable (an empty field, a stray "-", mid-edit) rather than rejecting/reverting it, since the player may still be in the middle of typing a full number. */
	private void tudursvehiclemod$onSpeedFieldChanged(String text) {
		if (this.populatingFieldText) {
			return;
		}
		try {
			float parsedPercent = Float.parseFloat(text.strip());
			this.speedFraction = Math.max(MIN_SPEED_FRACTION, Math.min(MAX_SPEED_FRACTION, parsedPercent / 100f));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
			// Mid-edit (empty, just "-", etc.) - leave the last valid value in place rather than reverting/rejecting the keystroke.
		}
	}

	private void tudursvehiclemod$onAltitudeFieldChanged(String text) {
		if (this.populatingFieldText) {
			return;
		}
		try {
			float parsed = Float.parseFloat(text.strip());
			this.orbitAltitude = Math.max(MIN_ORBIT_ALTITUDE, Math.min(MAX_ORBIT_ALTITUDE, parsed));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	private void tudursvehiclemod$onRadiusFieldChanged(String text) {
		if (this.populatingFieldText) {
			return;
		}
		try {
			float parsedPercent = Float.parseFloat(text.strip());
			this.radiusMultiplier = Math.max(MIN_RADIUS_MULTIPLIER, Math.min(MAX_RADIUS_MULTIPLIER, parsedPercent / 100f));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	private void tudursvehiclemod$sendUpdate() {
		ClientPlayNetworking.send(new DroneCenterBasicConfigUpdatePayload(
				this.blockX, this.blockY, this.blockZ, this.speedFraction, this.orbitAltitude, this.radiusMultiplier));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
