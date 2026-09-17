package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.block.DroneCenterBlockEntity;
import com.example.tudursvehiclemod.block.DroneWaypoint;
import com.example.tudursvehiclemod.network.DroneWaypointsUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Drone Center's own detailed (patrol route) settings screen (sections, esp. "詳細設定(巡回飛行ルート)実装" and "HomePoint実装") - opened from DroneCenterConfigScreen's own "Detailed Settings.." button. Edits one waypoint at a time (prev/next), each with position + cruise speed + roll + maneuverability corrections, settable by typing coordinates or "Record Here". Every edit resends the full route to the server. An empty route means the vehicle keeps its original circular orbit. Also edits the special "Home Point" (index -1) the landing autopilot targets, and ("経由地のUIについて、ホームポイント設定画面のさらに一つ前に追加したいので") the special "Via Point" (index -2, one page before the Home Point) an en-route waypoint the landing autopilot flies through FIRST if configured - see entity.AircraftEntity's own tudursvehiclemod$updateDroneLandingRouteAutopilot() doc for the full two-leg routing. Unlike the Home Point (always present, never null), the Via Point is genuinely optional - null means "not configured at all", shown as empty fields exactly like an empty regular-waypoint list, and "Record Here"/typing a coordinate both create one from DroneWaypoint's own default first if it doesn't exist yet, while "Remove" clears it back to null (rather than being a no-op, unlike on the Home Point page). */
public class DroneWaypointsScreen extends Screen {

	private final int blockX;
	private final int blockY;
	private final int blockZ;

	/** Mutable working copy - edited freely here, only actually sent to the server on every change (see this class's own doc). */
	private final List<DroneWaypoint> waypoints;
	/** Mutable working copy of the Home Point (see this class's own doc) - always non-null. */
	private DroneWaypoint homePoint;
	/** Mutable working copy of the Via Point (see this class's own doc) - null means "not configured", unlike homePoint's own always-non-null contract. */
	private DroneWaypoint viaPoint;
	/** -2 means "currently viewing/editing the Via Point"; -1 means "currently viewing/editing the Home Point"; 0.waypoints.size()-1 means a regular waypoint - see this class's own doc. */
	private int currentIndex;

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

	public DroneWaypointsScreen(int blockX, int blockY, int blockZ, String encodedWaypoints, String encodedHomePoint, String encodedViaPoint) {
		super(Text.translatable("gui.tudursvehiclemod.drone_waypoints"));
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
		this.waypoints = new ArrayList<>();
		if (!encodedWaypoints.isEmpty()) {
			for (String part : encodedWaypoints.split(";")) {
				DroneWaypoint decoded = DroneWaypoint.tudursvehiclemod$decode(part);
				if (decoded != null) {
					this.waypoints.add(decoded);
				}
			}
		}
		DroneWaypoint decodedHome = encodedHomePoint.isEmpty() ? null : DroneWaypoint.tudursvehiclemod$decode(encodedHomePoint);
		this.homePoint = decodedHome != null ? decodedHome : DroneWaypoint.createDefault();
		this.viaPoint = encodedViaPoint.isEmpty() ? null : DroneWaypoint.tudursvehiclemod$decode(encodedViaPoint);
		this.currentIndex = -1;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 2 - 134;

		int labelX = centerX - LABEL_WIDTH - 6;
		int minusEquivX = centerX - 4;
		int fieldX = minusEquivX;
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
		this.xField.setMaxLength(6);
		this.xField.setChangedListener(this::tudursvehiclemod$onXFieldChanged);
		this.addDrawableChild(this.xField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.z_label"), button -> {}
		).dimensions(labelX, zRowY, LABEL_WIDTH, 20).build());
		this.zField = new TextFieldWidget(this.textRenderer, fieldX, zRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.drone_waypoints.z_field"));
		this.zField.setMaxLength(6);
		this.zField.setChangedListener(this::tudursvehiclemod$onZFieldChanged);
		this.addDrawableChild(this.zField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.y_label"), button -> {}
		).dimensions(labelX, yRowY, LABEL_WIDTH, 20).build());
		this.yField = new TextFieldWidget(this.textRenderer, fieldX, yRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.drone_waypoints.y_field"));
		this.yField.setMaxLength(6);
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

		// How quickly this waypoint's own roll transition happens - see DroneWaypoint's own rollManeuverabilityMultiplier doc.
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_waypoints.roll_maneuverability_label"), button -> {}
		).dimensions(labelX, rollManeuverabilityRowY, LABEL_WIDTH, 20).build());
		this.rollManeuverabilityField = new TextFieldWidget(this.textRenderer, fieldX, rollManeuverabilityRowY, FIELD_WIDTH * 2, 20,
				Text.translatable("gui.tudursvehiclemod.drone_waypoints.roll_maneuverability_field"));
		this.rollManeuverabilityField.setMaxLength(5);
		this.rollManeuverabilityField.setChangedListener(this::tudursvehiclemod$onRollManeuverabilityFieldChanged);
		this.addDrawableChild(this.rollManeuverabilityField);

		// How sharply this vehicle can actually turn (yaw) approaching this specific waypoint - see DroneWaypoint's own turnManeuverabilityMultiplier doc.
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

	/** Per this class's own doc: -2 is the Via Point, -1 is the Home Point, 0.size-1 are regular waypoints - wraps around both ends (the Via Point and Home Point each count as one extra "page" alongside however many regular waypoints exist). */
	private void tudursvehiclemod$navigate(int delta) {
		int totalPages = this.waypoints.size() + 2;
		int currentPage = this.currentIndex + 2;
		int newPage = ((currentPage + delta) % totalPages + totalPages) % totalPages;
		this.currentIndex = newPage - 2;
		this.tudursvehiclemod$refreshFields();
	}

	/** Per this class's own doc: the Via Point specifically starts out null (unlike the Home Point, which always has SOME value) - this is the single shared place a fresh default gets created for it the first time it's actually needed (recording a position here, or typing directly into a field while still null - see tudursvehiclemod$onCoordinateFieldChanged() and friends). Returns the resulting (possibly newly-created) value, and updates this.viaPoint itself. */
	private DroneWaypoint tudursvehiclemod$getOrCreateViaPoint() {
		if (this.viaPoint == null) {
			this.viaPoint = DroneWaypoint.createDefault();
		}
		return this.viaPoint;
	}

	/** Records the player's own current position as a waypoint (or the Home Point/Via Point) - captures this vehicle's own client, relative to the Drone Center's own block position (matching DroneWaypoint's own "relative to center" convention). Keeps whatever speed/roll/maneuverability settings the point already had (a freshly-created Via Point uses DroneWaypoint's own defaults for those, per tudursvehiclemod$getOrCreateViaPoint()'s own doc). */
	private void tudursvehiclemod$recordHere() {
		if (this.client == null || this.client.player == null) {
			return;
		}
		int relX = (int) Math.floor(this.client.player.getX()) - this.blockX;
		int relY = (int) Math.floor(this.client.player.getY()) - this.blockY;
		int relZ = (int) Math.floor(this.client.player.getZ()) - this.blockZ;
		if (this.currentIndex == -1) {
			this.homePoint = new DroneWaypoint(relX, relY, relZ, this.homePoint.speedFraction(), this.homePoint.rollAngle(),
					this.homePoint.rollManeuverabilityMultiplier(), this.homePoint.turnManeuverabilityMultiplier());
		} else if (this.currentIndex == -2) {
			DroneWaypoint current = this.tudursvehiclemod$getOrCreateViaPoint();
			this.viaPoint = new DroneWaypoint(relX, relY, relZ, current.speedFraction(), current.rollAngle(),
					current.rollManeuverabilityMultiplier(), current.turnManeuverabilityMultiplier());
		} else {
			if (this.waypoints.isEmpty()) {
				this.tudursvehiclemod$addWaypoint();
			}
			DroneWaypoint current = this.waypoints.get(this.currentIndex);
			this.waypoints.set(this.currentIndex, new DroneWaypoint(relX, relY, relZ, current.speedFraction(), current.rollAngle(),
					current.rollManeuverabilityMultiplier(), current.turnManeuverabilityMultiplier()));
		}
		this.tudursvehiclemod$refreshFields();
		this.tudursvehiclemod$sendUpdate();
	}

	private void tudursvehiclemod$addWaypoint() {
		this.waypoints.add(DroneWaypoint.createDefault());
		this.currentIndex = this.waypoints.size() - 1;
		this.tudursvehiclemod$refreshFields();
		this.tudursvehiclemod$sendUpdate();
	}

	/** No-op while viewing the Home Point (see this class's own doc) - it can be re-recorded/re-edited, but never removed entirely (there's always exactly one). unlike the Home Point, this genuinely CLEARS the Via Point back to null (it's optional, unlike the Home Point) and navigates back to the Home Point page - a no-op if it was already null (nothing to clear). */
	private void tudursvehiclemod$removeWaypoint() {
		if (this.currentIndex == -1) {
			return;
		}
		if (this.currentIndex == -2) {
			if (this.viaPoint == null) {
				return;
			}
			this.viaPoint = null;
			this.currentIndex = -1;
			this.tudursvehiclemod$refreshFields();
			this.tudursvehiclemod$sendUpdate();
			return;
		}
		if (this.waypoints.isEmpty()) {
			return;
		}
		this.waypoints.remove(this.currentIndex);
		if (this.currentIndex >= this.waypoints.size()) {
			this.currentIndex = this.waypoints.isEmpty() ? -1 : this.waypoints.size() - 1;
		}
		this.tudursvehiclemod$refreshFields();
		this.tudursvehiclemod$sendUpdate();
	}

	/** Repopulates every field from whichever point is currently selected (the Via Point, the Home Point, or a regular waypoint) - without treating it as a user edit (see populatingFieldText's own doc). A null Via Point (not yet configured) shows as empty fields, exactly like an empty regular-waypoint list. */
	private void tudursvehiclemod$refreshFields() {
		this.populatingFieldText = true;
		DroneWaypoint current = this.currentIndex == -1 ? this.homePoint
				: this.currentIndex == -2 ? this.viaPoint
				: (this.waypoints.isEmpty() ? null : this.waypoints.get(this.currentIndex));
		if (current == null) {
			this.xField.setText("");
			this.zField.setText("");
			this.yField.setText("");
			this.speedField.setText("");
			this.rollField.setText("");
			this.rollManeuverabilityField.setText("");
			this.turnManeuverabilityField.setText("");
		} else {
			this.xField.setText(String.valueOf(current.relX()));
			this.zField.setText(String.valueOf(current.relZ()));
			this.yField.setText(String.valueOf(current.relY()));
			this.speedField.setText(String.valueOf(Math.round(current.speedFraction() * 100)));
			this.rollField.setText(String.valueOf(Math.round(current.rollAngle())));
			this.rollManeuverabilityField.setText(String.valueOf(current.rollManeuverabilityMultiplier()));
			this.turnManeuverabilityField.setText(String.valueOf(current.turnManeuverabilityMultiplier()));
		}
		this.populatingFieldText = false;
		this.pageIndicatorButton.setMessage(this.tudursvehiclemod$pageIndicatorText());
	}

	private Text tudursvehiclemod$pageIndicatorText() {
		if (this.currentIndex == -1) {
			return Text.translatable("gui.tudursvehiclemod.drone_waypoints.home_point");
		}
		if (this.currentIndex == -2) {
			return this.viaPoint == null
					? Text.translatable("gui.tudursvehiclemod.drone_waypoints.via_point_unset")
					: Text.translatable("gui.tudursvehiclemod.drone_waypoints.via_point");
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

	/** Per this class's own doc: resolves whichever point is currently selected for an edit - the Home Point (always present), the Via Point (created fresh from DroneWaypoint's own default the first time an edit actually happens while it's still null - see tudursvehiclemod$getOrCreateViaPoint()'s own doc), or a regular waypoint (null if the list is empty, matching every field handler's own existing "nothing to edit yet" guard for that case). */
	private DroneWaypoint tudursvehiclemod$currentForEdit() {
		if (this.currentIndex == -1) {
			return this.homePoint;
		}
		if (this.currentIndex == -2) {
			return this.tudursvehiclemod$getOrCreateViaPoint();
		}
		return this.waypoints.isEmpty() ? null : this.waypoints.get(this.currentIndex);
	}

	/** Per this class's own doc: applies an edited point back to wherever it came from (Home Point field, Via Point field, or the regular-waypoint list at currentIndex). */
	private void tudursvehiclemod$applyEdit(DroneWaypoint updated) {
		if (this.currentIndex == -1) {
			this.homePoint = updated;
		} else if (this.currentIndex == -2) {
			this.viaPoint = updated;
		} else {
			this.waypoints.set(this.currentIndex, updated);
		}
	}

	/** Parses the field's own current text as an integer relative coordinate - silently ignores anything unparseable (mid-edit) rather than rejecting/reverting it. Applies to whichever point (Home Point, Via Point, or a regular waypoint) is currently selected. */
	private void tudursvehiclemod$onCoordinateFieldChanged(String text, boolean isX, boolean isY, boolean isZ) {
		if (this.populatingFieldText) {
			return;
		}
		if (this.currentIndex >= 0 && this.waypoints.isEmpty()) {
			return;
		}
		try {
			int parsed = Integer.parseInt(text.strip());
			DroneWaypoint current = this.tudursvehiclemod$currentForEdit();
			DroneWaypoint updated = new DroneWaypoint(
					isX ? parsed : current.relX(),
					isY ? parsed : current.relY(),
					isZ ? parsed : current.relZ(),
					current.speedFraction(), current.rollAngle(),
					current.rollManeuverabilityMultiplier(), current.turnManeuverabilityMultiplier());
			this.tudursvehiclemod$applyEdit(updated);
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	private void tudursvehiclemod$onSpeedFieldChanged(String text) {
		if (this.populatingFieldText) {
			return;
		}
		if (this.currentIndex >= 0 && this.waypoints.isEmpty()) {
			return;
		}
		try {
			float parsedPercent = Float.parseFloat(text.strip());
			float clamped = Math.max(0.05f, Math.min(1.0f, parsedPercent / 100f));
			DroneWaypoint current = this.tudursvehiclemod$currentForEdit();
			DroneWaypoint updated = new DroneWaypoint(current.relX(), current.relY(), current.relZ(), clamped, current.rollAngle(),
					current.rollManeuverabilityMultiplier(), current.turnManeuverabilityMultiplier());
			this.tudursvehiclemod$applyEdit(updated);
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	/** Parses the field's own current text as a roll angle in degrees - no clamp at all (aerobatic maneuvers like loops need well beyond a simple +/-90 bank range). */
	private void tudursvehiclemod$onRollFieldChanged(String text) {
		if (this.populatingFieldText) {
			return;
		}
		if (this.currentIndex >= 0 && this.waypoints.isEmpty()) {
			return;
		}
		try {
			float parsed = Float.parseFloat(text.strip());
			float clamped = parsed;
			DroneWaypoint current = this.tudursvehiclemod$currentForEdit();
			DroneWaypoint updated = new DroneWaypoint(current.relX(), current.relY(), current.relZ(), current.speedFraction(), clamped,
					current.rollManeuverabilityMultiplier(), current.turnManeuverabilityMultiplier());
			this.tudursvehiclemod$applyEdit(updated);
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	/** Parses the field's own current text as this waypoint's own roll transition speed multiplier (1.0 = normal) - clamped to a reasonable 0.1-5.0 range so a stray 0 or negative value can't produce a degenerate transition. */
	private void tudursvehiclemod$onRollManeuverabilityFieldChanged(String text) {
		if (this.populatingFieldText) {
			return;
		}
		if (this.currentIndex >= 0 && this.waypoints.isEmpty()) {
			return;
		}
		try {
			float parsed = Float.parseFloat(text.strip());
			float clamped = Math.max(0.1f, Math.min(5.0f, parsed));
			DroneWaypoint current = this.tudursvehiclemod$currentForEdit();
			DroneWaypoint updated = new DroneWaypoint(current.relX(), current.relY(), current.relZ(), current.speedFraction(),
					current.rollAngle(), clamped, current.turnManeuverabilityMultiplier());
			this.tudursvehiclemod$applyEdit(updated);
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	/** Parses the field's own current text as this waypoint's own turn (yaw) rate multiplier (1.0 = normal) - clamped to a reasonable 0.1-5.0 range, same reasoning as the roll maneuverability field's own equivalent. */
	private void tudursvehiclemod$onTurnManeuverabilityFieldChanged(String text) {
		if (this.populatingFieldText) {
			return;
		}
		if (this.currentIndex >= 0 && this.waypoints.isEmpty()) {
			return;
		}
		try {
			float parsed = Float.parseFloat(text.strip());
			float clamped = Math.max(0.1f, Math.min(5.0f, parsed));
			DroneWaypoint current = this.tudursvehiclemod$currentForEdit();
			DroneWaypoint updated = new DroneWaypoint(current.relX(), current.relY(), current.relZ(), current.speedFraction(),
					current.rollAngle(), current.rollManeuverabilityMultiplier(), clamped);
			this.tudursvehiclemod$applyEdit(updated);
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
		ClientPlayNetworking.send(new DroneWaypointsUpdatePayload(this.blockX, this.blockY, this.blockZ, sb.toString(), this.homePoint.tudursvehiclemod$encode(),
				this.viaPoint != null ? this.viaPoint.tudursvehiclemod$encode() : ""));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
