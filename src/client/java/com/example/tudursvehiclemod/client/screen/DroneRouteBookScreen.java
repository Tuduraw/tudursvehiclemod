package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.block.DroneRouteBookWaypoint;
import com.example.tudursvehiclemod.item.DroneRouteBookItem;
import com.example.tudursvehiclemod.network.DroneRouteBookUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** item.DroneRouteBookItem's own editor screen - opened by right-clicking the held book, usable freely anywhere in the world (no Drone Center needed at all) specifically so "Record Here" actually has room to walk around and record different spots; doing this from the Drone Center's own config screen left barely any room to move. Same one-waypoint-at-a-time editing pattern as DroneWaypointsScreen, but with ABSOLUTE world coordinates (see DroneRouteBookWaypoint's own doc for why) - every add/remove/edit/record immediately sends the full current route back to the server to update this same held stack (see DroneRouteBookUpdatePayload's own doc). */
public class DroneRouteBookScreen extends Screen {

	private final boolean mainHand;
	private final List<DroneRouteBookWaypoint> waypoints;
	/** -1 means the special "offset page" (see this class's own doc for OFFSET_PAGE_INDEX) is currently showing instead of any actual waypoint. */
	private int currentIndex;
	/** Edited via the same x/y/z fields as a waypoint, but only while on the dedicated offset page (currentIndex == OFFSET_PAGE_INDEX) - added to every waypoint's own absolute position once this book is actually read by a Drone Center. */
	private int offsetX;
	private int offsetY;
	private int offsetZ;
	private static final int OFFSET_PAGE_INDEX = -1;

	private static final int FIELD_WIDTH = 30;
	private static final int LABEL_WIDTH = 90;
	private static final int ROW_HEIGHT = 24;

	private TextFieldWidget xField;
	private TextFieldWidget yField;
	private TextFieldWidget zField;
	private TextFieldWidget speedField;
	private TextFieldWidget rollField;
	private TextFieldWidget rollManeuverabilityField;
	private TextFieldWidget turnManeuverabilityField;
	private ButtonWidget pageIndicatorButton;

	/** Set while a text field's own setChangedListener is applying a value from navigation/record/add rather than an actual keystroke, so that listener doesn't immediately re-parse its own just-set text and send a redundant update. */
	private boolean populatingFieldText;

	public DroneRouteBookScreen(boolean mainHand, ItemStack stack) {
		super(Text.translatable("gui.tudursvehiclemod.drone_route_book"));
		this.mainHand = mainHand;
		this.waypoints = new ArrayList<>(DroneRouteBookItem.tudursvehiclemod$getWaypoints(stack));
		net.minecraft.util.math.Vec3i offset = DroneRouteBookItem.tudursvehiclemod$getOffset(stack);
		this.offsetX = offset.getX();
		this.offsetY = offset.getY();
		this.offsetZ = offset.getZ();
		this.currentIndex = OFFSET_PAGE_INDEX;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 2 - 134;

		int labelX = centerX - LABEL_WIDTH - 6;
		int fieldX = centerX - 4;
		int afterFieldX = fieldX + FIELD_WIDTH + 4;

		int xRowY = startY;
		int zRowY = startY + ROW_HEIGHT;
		int yRowY = startY + ROW_HEIGHT * 2;
		int speedRowY = startY + ROW_HEIGHT * 3;
		int rollRowY = startY + ROW_HEIGHT * 4;
		int rollManeuverabilityRowY = startY + ROW_HEIGHT * 5;
		int turnManeuverabilityRowY = startY + ROW_HEIGHT * 6;
		int navRowY = startY + ROW_HEIGHT * 7 + 6;
		int actionRowY = navRowY + 24;
		int closeRowY = actionRowY + 48;

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.x_label"), button -> {}
		).dimensions(labelX, xRowY, LABEL_WIDTH, 20).build());
		this.xField = new TextFieldWidget(this.textRenderer, fieldX, xRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.drone_waypoints.x_field"));
		this.xField.setMaxLength(8);
		this.xField.setChangedListener(this::tudursvehiclemod$onXFieldChanged);
		this.addDrawableChild(this.xField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.z_label"), button -> {}
		).dimensions(labelX, zRowY, LABEL_WIDTH, 20).build());
		this.zField = new TextFieldWidget(this.textRenderer, fieldX, zRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.drone_waypoints.z_field"));
		this.zField.setMaxLength(8);
		this.zField.setChangedListener(this::tudursvehiclemod$onZFieldChanged);
		this.addDrawableChild(this.zField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.y_label"), button -> {}
		).dimensions(labelX, yRowY, LABEL_WIDTH, 20).build());
		this.yField = new TextFieldWidget(this.textRenderer, fieldX, yRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.drone_waypoints.y_field"));
		this.yField.setMaxLength(8);
		this.yField.setChangedListener(this::tudursvehiclemod$onYFieldChanged);
		this.addDrawableChild(this.yField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.speed_label"), button -> {}
		).dimensions(labelX, speedRowY, LABEL_WIDTH, 20).build());
		this.speedField = new TextFieldWidget(this.textRenderer, fieldX, speedRowY, FIELD_WIDTH, 20,
				Text.translatable("gui.tudursvehiclemod.drone_waypoints.speed_field"));
		this.speedField.setMaxLength(3);
		this.speedField.setChangedListener(this::tudursvehiclemod$onSpeedFieldChanged);
		this.addDrawableChild(this.speedField);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("%"), button -> {}
		).dimensions(afterFieldX, speedRowY, 20, 20).build());

		// Each waypoint's own target roll (bank) angle, which the vehicle rolls toward as it actually arrives - see entity.AircraftEntity's own tudursvehiclemod$updateDroneWaypointAutopilot() doc.
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.roll_label"), button -> {}
		).dimensions(labelX, rollRowY, LABEL_WIDTH, 20).build());
		this.rollField = new TextFieldWidget(this.textRenderer, fieldX, rollRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.drone_waypoints.roll_field"));
		this.rollField.setMaxLength(4);
		this.rollField.setChangedListener(this::tudursvehiclemod$onRollFieldChanged);
		this.addDrawableChild(this.rollField);

		// How quickly this waypoint's own roll transition happens - see DroneRouteBookWaypoint's own rollManeuverabilityMultiplier doc.
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.roll_maneuverability_label"), button -> {}
		).dimensions(labelX, rollManeuverabilityRowY, LABEL_WIDTH, 20).build());
		this.rollManeuverabilityField = new TextFieldWidget(this.textRenderer, fieldX, rollManeuverabilityRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.drone_waypoints.roll_maneuverability_field"));
		this.rollManeuverabilityField.setMaxLength(5);
		this.rollManeuverabilityField.setChangedListener(this::tudursvehiclemod$onRollManeuverabilityFieldChanged);
		this.addDrawableChild(this.rollManeuverabilityField);

		// How sharply this vehicle can actually turn (yaw) approaching this specific waypoint - see DroneRouteBookWaypoint's own turnManeuverabilityMultiplier doc.
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.turn_maneuverability_label"), button -> {}
		).dimensions(labelX, turnManeuverabilityRowY, LABEL_WIDTH, 20).build());
		this.turnManeuverabilityField = new TextFieldWidget(this.textRenderer, fieldX, turnManeuverabilityRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.drone_waypoints.turn_maneuverability_field"));
		this.turnManeuverabilityField.setMaxLength(5);
		this.turnManeuverabilityField.setChangedListener(this::tudursvehiclemod$onTurnManeuverabilityFieldChanged);
		this.addDrawableChild(this.turnManeuverabilityField);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> this.tudursvehiclemod$navigate(-1)
		).dimensions(centerX - 100, navRowY, 20, 20).build());
		this.pageIndicatorButton = ButtonWidget.builder(this.tudursvehiclemod$pageIndicatorText(), button -> {}
		).dimensions(centerX - 76, navRowY, 152, 20).build();
		this.addDrawableChild(this.pageIndicatorButton);
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> this.tudursvehiclemod$navigate(1)
		).dimensions(centerX + 80, navRowY, 20, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.record"),
				button -> this.tudursvehiclemod$recordHere()
		).dimensions(centerX - 100, actionRowY, 96, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.add"),
				button -> this.tudursvehiclemod$addWaypoint()
		).dimensions(centerX + 4, actionRowY, 96, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.remove"),
				button -> this.tudursvehiclemod$removeWaypoint()
		).dimensions(centerX - 100, actionRowY + 24, 200, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.close"),
				button -> this.close()
		).dimensions(centerX - 100, closeRowY, 200, 20).build());

		this.tudursvehiclemod$refreshFields();
	}

	private void tudursvehiclemod$navigate(int delta) {
		// Total pages = 1 (offset) + however many waypoints exist. Cycles through OFFSET_PAGE_INDEX (-1), 0, 1,.., size-1, back to -1.
		int totalPages = this.waypoints.size() + 1;
		int currentPageNumber = this.currentIndex + 1; // 0 = offset page, 1..size = waypoints
		currentPageNumber = ((currentPageNumber + delta) % totalPages + totalPages) % totalPages;
		this.currentIndex = currentPageNumber - 1;
		this.tudursvehiclemod$refreshFields();
	}

	/** Recording is usable freely anywhere (not just near a Drone Center) - captures the player's own current ABSOLUTE position directly. Keeps whatever speed/roll/maneuverability settings the waypoint already had. */
	private void tudursvehiclemod$recordHere() {
		if (this.client == null || this.client.player == null || this.currentIndex == OFFSET_PAGE_INDEX) {
			return;
		}
		if (this.waypoints.isEmpty()) {
			this.tudursvehiclemod$addWaypoint();
		}
		int x = (int) Math.floor(this.client.player.getX());
		int y = (int) Math.floor(this.client.player.getY());
		int z = (int) Math.floor(this.client.player.getZ());
		DroneRouteBookWaypoint current = this.waypoints.get(this.currentIndex);
		this.waypoints.set(this.currentIndex, new DroneRouteBookWaypoint(x, y, z, current.speedFraction(), current.rollAngle(),
				current.rollManeuverabilityMultiplier(), current.turnManeuverabilityMultiplier()));
		this.tudursvehiclemod$refreshFields();
		this.tudursvehiclemod$sendUpdate();
	}

	private void tudursvehiclemod$addWaypoint() {
		int x = 0;
		int y = 64;
		int z = 0;
		if (this.client != null && this.client.player != null) {
			x = (int) Math.floor(this.client.player.getX());
			y = (int) Math.floor(this.client.player.getY());
			z = (int) Math.floor(this.client.player.getZ());
		}
		this.waypoints.add(DroneRouteBookWaypoint.createDefault(x, y, z));
		this.currentIndex = this.waypoints.size() - 1;
		this.tudursvehiclemod$refreshFields();
		this.tudursvehiclemod$sendUpdate();
	}

	private void tudursvehiclemod$removeWaypoint() {
		if (this.waypoints.isEmpty() || this.currentIndex == OFFSET_PAGE_INDEX) {
			return;
		}
		this.waypoints.remove(this.currentIndex);
		if (this.currentIndex >= this.waypoints.size()) {
			this.currentIndex = Math.max(0, this.waypoints.size() - 1);
		}
		this.tudursvehiclemod$refreshFields();
		this.tudursvehiclemod$sendUpdate();
	}

	/** Repopulates every field from the currently-selected waypoint (or blanks them if the route is empty) - without treating it as a user edit (see populatingFieldText's own doc). */
	private void tudursvehiclemod$refreshFields() {
		this.populatingFieldText = true;
		if (this.currentIndex == OFFSET_PAGE_INDEX) {
			this.xField.setText(String.valueOf(this.offsetX));
			this.zField.setText(String.valueOf(this.offsetZ));
			this.yField.setText(String.valueOf(this.offsetY));
			this.speedField.setText("");
			this.rollField.setText("");
			this.rollManeuverabilityField.setText("");
			this.turnManeuverabilityField.setText("");
			this.speedField.setEditable(false);
			this.rollField.setEditable(false);
			this.rollManeuverabilityField.setEditable(false);
			this.turnManeuverabilityField.setEditable(false);
		} else if (this.waypoints.isEmpty()) {
			this.xField.setText("");
			this.zField.setText("");
			this.yField.setText("");
			this.speedField.setText("");
			this.rollField.setText("");
			this.rollManeuverabilityField.setText("");
			this.turnManeuverabilityField.setText("");
		} else {
			this.speedField.setEditable(true);
			this.rollField.setEditable(true);
			this.rollManeuverabilityField.setEditable(true);
			this.turnManeuverabilityField.setEditable(true);
			DroneRouteBookWaypoint current = this.waypoints.get(this.currentIndex);
			this.xField.setText(String.valueOf(current.x()));
			this.zField.setText(String.valueOf(current.z()));
			this.yField.setText(String.valueOf(current.y()));
			this.speedField.setText(String.valueOf(Math.round(current.speedFraction() * 100)));
			this.rollField.setText(String.valueOf(Math.round(current.rollAngle())));
			this.rollManeuverabilityField.setText(String.valueOf(current.rollManeuverabilityMultiplier()));
			this.turnManeuverabilityField.setText(String.valueOf(current.turnManeuverabilityMultiplier()));
		}
		this.populatingFieldText = false;
		this.pageIndicatorButton.setMessage(this.tudursvehiclemod$pageIndicatorText());
	}

	private Text tudursvehiclemod$pageIndicatorText() {
		if (this.currentIndex == OFFSET_PAGE_INDEX) {
			return Text.translatable("gui.tudursvehiclemod.drone_waypoints.offset_page");
		}
		return this.waypoints.isEmpty()
				? Text.translatable("gui.tudursvehiclemod.drone_waypoints.none")
				: Text.translatable("gui.tudursvehiclemod.drone_waypoints.page", this.currentIndex + 1, this.waypoints.size());
	}

	private void tudursvehiclemod$onXFieldChanged(String text) {
		this.tudursvehiclemod$onCoordinateFieldChanged(text, true, false, false);
	}

	private void tudursvehiclemod$onZFieldChanged(String text) {
		this.tudursvehiclemod$onCoordinateFieldChanged(text, false, false, true);
	}

	private void tudursvehiclemod$onYFieldChanged(String text) {
		this.tudursvehiclemod$onCoordinateFieldChanged(text, false, true, false);
	}

	/** Parses the field's own current text as an integer absolute coordinate - silently ignores anything unparseable (mid-edit) rather than rejecting/reverting it. */
	private void tudursvehiclemod$onCoordinateFieldChanged(String text, boolean isX, boolean isY, boolean isZ) {
		if (this.populatingFieldText) {
			return;
		}
		try {
			int parsed = Integer.parseInt(text.strip());
			if (this.currentIndex == OFFSET_PAGE_INDEX) {
				if (isX) {
					this.offsetX = parsed;
				} else if (isY) {
					this.offsetY = parsed;
				} else {
					this.offsetZ = parsed;
				}
				this.tudursvehiclemod$sendUpdate();
				return;
			}
			if (this.waypoints.isEmpty()) {
				return;
			}
			DroneRouteBookWaypoint current = this.waypoints.get(this.currentIndex);
			DroneRouteBookWaypoint updated = new DroneRouteBookWaypoint(
					isX ? parsed : current.x(),
					isY ? parsed : current.y(),
					isZ ? parsed : current.z(),
					current.speedFraction(), current.rollAngle(),
					current.rollManeuverabilityMultiplier(), current.turnManeuverabilityMultiplier());
			this.waypoints.set(this.currentIndex, updated);
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	private void tudursvehiclemod$onSpeedFieldChanged(String text) {
		if (this.populatingFieldText || this.waypoints.isEmpty()) {
			return;
		}
		try {
			float parsedPercent = Float.parseFloat(text.strip());
			float clamped = Math.max(0.05f, Math.min(1.0f, parsedPercent / 100f));
			DroneRouteBookWaypoint current = this.waypoints.get(this.currentIndex);
			this.waypoints.set(this.currentIndex, new DroneRouteBookWaypoint(current.x(), current.y(), current.z(), clamped,
					current.rollAngle(), current.rollManeuverabilityMultiplier(), current.turnManeuverabilityMultiplier()));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	/** Parses the field's own current text as a roll angle in degrees - no clamp at all, same reasoning as DroneWaypointsScreen's own equivalent. */
	private void tudursvehiclemod$onRollFieldChanged(String text) {
		if (this.populatingFieldText || this.waypoints.isEmpty()) {
			return;
		}
		try {
			float parsed = Float.parseFloat(text.strip());
			float clamped = parsed;
			DroneRouteBookWaypoint current = this.waypoints.get(this.currentIndex);
			this.waypoints.set(this.currentIndex, new DroneRouteBookWaypoint(current.x(), current.y(), current.z(), current.speedFraction(),
					clamped, current.rollManeuverabilityMultiplier(), current.turnManeuverabilityMultiplier()));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	/** Parses the field's own current text as this waypoint's own roll transition speed multiplier (1.0 = normal) - clamped to a reasonable 0.1-5.0 range, same reasoning as DroneWaypointsScreen's own equivalent. */
	private void tudursvehiclemod$onRollManeuverabilityFieldChanged(String text) {
		if (this.populatingFieldText || this.waypoints.isEmpty()) {
			return;
		}
		try {
			float parsed = Float.parseFloat(text.strip());
			float clamped = Math.max(0.1f, Math.min(5.0f, parsed));
			DroneRouteBookWaypoint current = this.waypoints.get(this.currentIndex);
			this.waypoints.set(this.currentIndex, new DroneRouteBookWaypoint(current.x(), current.y(), current.z(), current.speedFraction(),
					current.rollAngle(), clamped, current.turnManeuverabilityMultiplier()));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	/** Parses the field's own current text as this waypoint's own turn (yaw) rate multiplier (1.0 = normal) - clamped to a reasonable 0.1-5.0 range, same reasoning as DroneWaypointsScreen's own equivalent. */
	private void tudursvehiclemod$onTurnManeuverabilityFieldChanged(String text) {
		if (this.populatingFieldText || this.waypoints.isEmpty()) {
			return;
		}
		try {
			float parsed = Float.parseFloat(text.strip());
			float clamped = Math.max(0.1f, Math.min(5.0f, parsed));
			DroneRouteBookWaypoint current = this.waypoints.get(this.currentIndex);
			this.waypoints.set(this.currentIndex, new DroneRouteBookWaypoint(current.x(), current.y(), current.z(), current.speedFraction(),
					current.rollAngle(), current.rollManeuverabilityMultiplier(), clamped));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	private void tudursvehiclemod$sendUpdate() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < this.waypoints.size(); i++) {
			if (i > 0) {
				sb.append(';');
			}
			sb.append(this.waypoints.get(i).tudursvehiclemod$encode());
		}
		String encodedOffset = (this.offsetX == 0 && this.offsetY == 0 && this.offsetZ == 0)
				? "" : this.offsetX + "," + this.offsetY + "," + this.offsetZ;
		ClientPlayNetworking.send(new DroneRouteBookUpdatePayload(this.mainHand, sb.toString(), encodedOffset));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
