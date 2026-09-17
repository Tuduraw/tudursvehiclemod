package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.block.GroundWaypoint;
import com.example.tudursvehiclemod.network.GroundWaypointsUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** The Drone Center's own detailed route settings screen shown when the bound vehicle is a Car/Ship/Submarine (see block.DroneCenterBlockEntity's own tudursvehiclemod$usesGroundRoute() doc for exactly how that's decided, entirely server-side - the client just opens whichever screen it's told to). DroneWaypointsScreen (the aircraft one) is left completely untouched by this feature.
 *
 * Deliberately a NARROWER screen than the aircraft version, matching GroundWaypoint's own smaller field set: X/Z position, cruise speed, and dwell time. There is no Y field (a surface vehicle's own height follows the terrain/waterline - see that record's own doc), no roll angle or maneuverability multipliers (a surface vehicle's own banking/turn behavior comes from its own steering physics, not from route data), and no Home Point page (that exists purely for the aircraft landing autopilot - a surface vehicle simply stops instead).
 *
 * Every other convention is copied verbatim from the aircraft screen, since that pattern is already proven here: one waypoint at a time with prev/next navigation rather than a scrollable list, position settable either by typing or by standing somewhere and pressing "Record Here", and every add/remove/edit immediately resending the FULL current route (see GroundWaypointsUpdatePayload's own doc). */
public class GroundWaypointsScreen extends Screen {

	private final int blockX;
	/** Kept purely so update payloads can address the correct block - a ground WAYPOINT itself has no Y at all (see GroundWaypoint's own doc), but the Drone Center block being edited obviously still does. */
	private final int blockY;
	private final int blockZ;

	/** Mutable working copy - edited freely here, sent to the server on every change (see this class's own doc). */
	private final List<GroundWaypoint> waypoints;
	/** 0.waypoints.size()-1. Unlike the aircraft screen there is no -1 "Home Point" page here (see this class's own doc), so an empty route simply shows blank fields until one is added. */
	private int currentIndex;

	private static final int FIELD_WIDTH = 30;
	private static final int LABEL_WIDTH = 90;
	private static final int ROW_HEIGHT = 24;

	private TextFieldWidget xField;
	private TextFieldWidget zField;
	private TextFieldWidget speedField;
	private TextFieldWidget waitField;
	private ButtonWidget pageIndicatorButton;

	/** Set while a text field's own setChangedListener is applying a value from navigation/record/add rather than an actual keystroke, so that listener doesn't immediately re-parse its own just-set text and send a redundant update. Same purpose as the aircraft screen's own field of this name. */
	private boolean populatingFieldText;

	public GroundWaypointsScreen(int blockX, int blockY, int blockZ, String encodedWaypoints) {
		super(Text.translatable("gui.tudursvehiclemod.ground_waypoints"));
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
		this.waypoints = new ArrayList<>();
		if (!encodedWaypoints.isEmpty()) {
			for (String part : encodedWaypoints.split(";")) {
				GroundWaypoint decoded = GroundWaypoint.tudursvehiclemod$decode(part);
				if (decoded != null) {
					this.waypoints.add(decoded);
				}
			}
		}
		this.currentIndex = 0;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 2 - 100;

		int labelX = centerX - LABEL_WIDTH - 6;
		int fieldX = centerX - 4;
		int afterFieldX = fieldX + FIELD_WIDTH + 4;

		int xRowY = startY;
		int zRowY = startY + ROW_HEIGHT;
		int speedRowY = startY + ROW_HEIGHT * 2;
		int waitRowY = startY + ROW_HEIGHT * 3;
		int navRowY = startY + ROW_HEIGHT * 4 + 6;
		int actionRowY = navRowY + 24;
		int closeRowY = actionRowY + 48;

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.ground_waypoints.x_label"), button -> {}
		).dimensions(labelX, xRowY, LABEL_WIDTH, 20).build());
		this.xField = new TextFieldWidget(this.textRenderer, fieldX, xRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.ground_waypoints.x_field"));
		this.xField.setMaxLength(6);
		this.xField.setChangedListener(text -> this.tudursvehiclemod$onCoordinateFieldChanged(text, true));
		this.addDrawableChild(this.xField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.ground_waypoints.z_label"), button -> {}
		).dimensions(labelX, zRowY, LABEL_WIDTH, 20).build());
		this.zField = new TextFieldWidget(this.textRenderer, fieldX, zRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.ground_waypoints.z_field"));
		this.zField.setMaxLength(6);
		this.zField.setChangedListener(text -> this.tudursvehiclemod$onCoordinateFieldChanged(text, false));
		this.addDrawableChild(this.zField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.ground_waypoints.speed_label"), button -> {}
		).dimensions(labelX, speedRowY, LABEL_WIDTH, 20).build());
		this.speedField = new TextFieldWidget(this.textRenderer, fieldX, speedRowY, FIELD_WIDTH, 20,
				Text.translatable("gui.tudursvehiclemod.ground_waypoints.speed_field"));
		this.speedField.setMaxLength(3);
		this.speedField.setChangedListener(this::tudursvehiclemod$onSpeedFieldChanged);
		this.addDrawableChild(this.speedField);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("%"), button -> {}
		).dimensions(afterFieldX, speedRowY, 20, 20).build());

		// How long this vehicle sits stationary at this waypoint before moving on - see GroundWaypoint's own waitTicks doc. 0 means roll straight through without stopping.
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.ground_waypoints.wait_label"), button -> {}
		).dimensions(labelX, waitRowY, LABEL_WIDTH, 20).build());
		this.waitField = new TextFieldWidget(this.textRenderer, fieldX, waitRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.ground_waypoints.wait_field"));
		this.waitField.setMaxLength(6);
		this.waitField.setChangedListener(this::tudursvehiclemod$onWaitFieldChanged);
		this.addDrawableChild(this.waitField);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"),
				button -> this.tudursvehiclemod$navigate(-1)
		).dimensions(centerX - 100, navRowY, 40, 20).build());
		this.pageIndicatorButton = ButtonWidget.builder(this.tudursvehiclemod$pageIndicatorText(), button -> {}
		).dimensions(centerX - 56, navRowY, 112, 20).build();
		this.addDrawableChild(this.pageIndicatorButton);
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"),
				button -> this.tudursvehiclemod$navigate(1)
		).dimensions(centerX + 60, navRowY, 40, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.ground_waypoints.record"),
				button -> this.tudursvehiclemod$recordHere()
		).dimensions(centerX - 100, actionRowY, 200, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.ground_waypoints.add"),
				button -> this.tudursvehiclemod$addWaypoint()
		).dimensions(centerX - 100, actionRowY + 24, 96, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.ground_waypoints.remove"),
				button -> this.tudursvehiclemod$removeWaypoint()
		).dimensions(centerX + 4, actionRowY + 24, 96, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.close"),
				button -> this.close()
		).dimensions(centerX - 100, closeRowY, 200, 20).build());

		this.tudursvehiclemod$refreshFields();
	}

	/** Wraps around both ends of the route. A no-op for an empty route (nothing to navigate between). */
	private void tudursvehiclemod$navigate(int delta) {
		if (this.waypoints.isEmpty()) {
			return;
		}
		int size = this.waypoints.size();
		this.currentIndex = ((this.currentIndex + delta) % size + size) % size;
		this.tudursvehiclemod$refreshFields();
	}

	/** Captures the player's own X/Z relative to the Drone Center's own block position (matching GroundWaypoint's own "relative to center" convention). Y is deliberately not captured at all - see that record's own doc. Keeps whatever speed/dwell settings the waypoint already had. Adds a first waypoint automatically if the route is currently empty, so "Record Here" works as the natural way to start a route from scratch. */
	private void tudursvehiclemod$recordHere() {
		if (this.client == null || this.client.player == null) {
			return;
		}
		int relX = (int) Math.floor(this.client.player.getX()) - this.blockX;
		int relZ = (int) Math.floor(this.client.player.getZ()) - this.blockZ;
		if (this.waypoints.isEmpty()) {
			this.waypoints.add(GroundWaypoint.createDefault());
			this.currentIndex = 0;
		}
		GroundWaypoint current = this.waypoints.get(this.currentIndex);
		this.waypoints.set(this.currentIndex, new GroundWaypoint(relX, relZ, current.speedFraction(), current.waitTicks()));
		this.tudursvehiclemod$refreshFields();
		this.tudursvehiclemod$sendUpdate();
	}

	private void tudursvehiclemod$addWaypoint() {
		this.waypoints.add(GroundWaypoint.createDefault());
		this.currentIndex = this.waypoints.size() - 1;
		this.tudursvehiclemod$refreshFields();
		this.tudursvehiclemod$sendUpdate();
	}

	private void tudursvehiclemod$removeWaypoint() {
		if (this.waypoints.isEmpty()) {
			return;
		}
		this.waypoints.remove(this.currentIndex);
		if (this.currentIndex >= this.waypoints.size()) {
			this.currentIndex = Math.max(0, this.waypoints.size() - 1);
		}
		this.tudursvehiclemod$refreshFields();
		this.tudursvehiclemod$sendUpdate();
	}

	/** Repopulates every field from the currently-selected waypoint - without treating it as a user edit (see populatingFieldText's own doc). */
	private void tudursvehiclemod$refreshFields() {
		this.populatingFieldText = true;
		GroundWaypoint current = this.waypoints.isEmpty() ? null : this.waypoints.get(this.currentIndex);
		if (current == null) {
			this.xField.setText("");
			this.zField.setText("");
			this.speedField.setText("");
			this.waitField.setText("");
		} else {
			this.xField.setText(String.valueOf(current.relX()));
			this.zField.setText(String.valueOf(current.relZ()));
			this.speedField.setText(String.valueOf(Math.round(current.speedFraction() * 100)));
			this.waitField.setText(String.valueOf(current.waitTicks()));
		}
		this.populatingFieldText = false;
		this.pageIndicatorButton.setMessage(this.tudursvehiclemod$pageIndicatorText());
	}

	private Text tudursvehiclemod$pageIndicatorText() {
		return this.waypoints.isEmpty()
				? Text.translatable("gui.tudursvehiclemod.ground_waypoints.none")
				: Text.translatable("gui.tudursvehiclemod.ground_waypoints.page", this.currentIndex + 1, this.waypoints.size());
	}

	/** Parses the field's own current text as an integer relative coordinate - silently ignores anything unparseable (mid-edit) rather than rejecting/reverting it. */
	private void tudursvehiclemod$onCoordinateFieldChanged(String text, boolean isX) {
		if (this.populatingFieldText || this.waypoints.isEmpty()) {
			return;
		}
		try {
			int parsed = Integer.parseInt(text.strip());
			GroundWaypoint current = this.waypoints.get(this.currentIndex);
			this.waypoints.set(this.currentIndex, new GroundWaypoint(
					isX ? parsed : current.relX(),
					isX ? current.relZ() : parsed,
					current.speedFraction(), current.waitTicks()));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	/** Entered as a whole-number PERCENTAGE of this vehicle's own max speed (matching the aircraft screen's own convention), stored as the 0-1 fraction GroundWaypoint itself carries. */
	private void tudursvehiclemod$onSpeedFieldChanged(String text) {
		if (this.populatingFieldText || this.waypoints.isEmpty()) {
			return;
		}
		try {
			int percent = Integer.parseInt(text.strip());
			float fraction = net.minecraft.util.math.MathHelper.clamp(percent / 100f, 0f, 1f);
			GroundWaypoint current = this.waypoints.get(this.currentIndex);
			this.waypoints.set(this.currentIndex, new GroundWaypoint(current.relX(), current.relZ(), fraction, current.waitTicks()));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	/** Per GroundWaypoint's own waitTicks doc: entered directly in ticks (20 per second), 0 meaning "don't stop here at all". Negatives are clamped away rather than rejected. */
	private void tudursvehiclemod$onWaitFieldChanged(String text) {
		if (this.populatingFieldText || this.waypoints.isEmpty()) {
			return;
		}
		try {
			int ticks = Math.max(0, Integer.parseInt(text.strip()));
			GroundWaypoint current = this.waypoints.get(this.currentIndex);
			this.waypoints.set(this.currentIndex, new GroundWaypoint(current.relX(), current.relZ(), current.speedFraction(), ticks));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	/** Sends the FULL current route (see this class's own doc for why resending everything is fine). */
	private void tudursvehiclemod$sendUpdate() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < this.waypoints.size(); i++) {
			if (i > 0) {
				sb.append(';');
			}
			sb.append(this.waypoints.get(i).tudursvehiclemod$encode());
		}
		ClientPlayNetworking.send(new GroundWaypointsUpdatePayload(this.blockX, this.blockY, this.blockZ, sb.toString()));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
