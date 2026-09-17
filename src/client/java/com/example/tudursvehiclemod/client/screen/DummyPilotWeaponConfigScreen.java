package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.network.DummyPilotWeaponConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/** The dummy pilot's own combat settings (weapon selection, attack altitudes, and hostile-mob search range), split out into this dedicated sub-screen reached via a "Weapon Settings" button on client.screen.DummyPilotConfigScreen - that screen's own layout was already at its practical display limit, leaving no room to also add a search-range field there.
 *
 * The weapon list is whatever the server sent (see network.DummyPilotWeaponConfigOpenPayload's own doc for why it's the server's list, not a client-side scan). Cycling through it shows "(未選択)" whenever no weapon is actually selected (weaponIndex -1, or the list being empty entirely - a vehicle with no weapons at all, or none currently bound).
 *
 * Every change is sent immediately (not on close), matching DummyPilotConfigScreen's own established behavior - so cycling the weapon or editing an altitude/the search range takes visible effect on the vehicle right away. The altitude and search-range fields all send on every keystroke, parsed defensively (an in-progress edit, e.g. a bare "-" or an empty field mid-retype, is not a valid number yet - falls back to this screen's own last-known-good value rather than sending garbage/zero the instant a keystroke briefly makes the field unparsable). */
public class DummyPilotWeaponConfigScreen extends Screen {

	private final int blockX;
	private final int blockY;
	private final int blockZ;

	/** The bound vehicle's own weapon list, server-sent. Empty if nothing is currently bound/loaded, or that vehicle genuinely has no weapons at all. */
	private final List<String> weaponDisplayNames;
	/** -1 means "not selected" - see block.DroneCenterBlockEntity's own dummyPilotWeaponIndex doc. */
	private int weaponIndex;
	private final String initialAttackStartAltitude;
	private final String initialAttackStopAltitude;
	private final String initialSearchRange;
	/** How much higher (blocks) than the target's own actual Y this dummy pilot's own aircraft dives toward - see entity.AircraftEntity's own CARRIER_LOCK_FALLING_WEAPON_DIVE_TARGET_Y_OFFSET doc for the full reasoning (0.0 here means "use this weapon's own type-based built-in default" rather than genuinely meaning zero offset). */
	private final String initialDiveTargetYOffset;

	private ButtonWidget weaponNameButton;
	private TextFieldWidget attackStartAltitudeField;
	private TextFieldWidget attackStopAltitudeField;
	private TextFieldWidget searchRangeField;
	private TextFieldWidget diveTargetYOffsetField;

	public DummyPilotWeaponConfigScreen(int blockX, int blockY, int blockZ, List<String> weaponDisplayNames, int weaponIndex,
			float attackStartAltitude, float attackStopAltitude, float searchRange, float diveTargetYOffset) {
		super(Text.translatable("gui.tudursvehiclemod.dummy_pilot_weapon_config"));
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
		this.weaponDisplayNames = List.copyOf(weaponDisplayNames);
		this.weaponIndex = weaponIndex;
		this.initialAttackStartAltitude = String.valueOf(attackStartAltitude);
		this.initialAttackStopAltitude = String.valueOf(attackStopAltitude);
		this.initialSearchRange = String.valueOf(searchRange);
		this.initialDiveTargetYOffset = String.valueOf(diveTargetYOffset);
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 2 - 90;
		int rowHeight = 24;

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.dummy_pilot_weapon_config.title_label"), button -> {})
				.dimensions(centerX - 100, startY, 200, 20).build());

		// Per this class's own doc: weapon selection (a cycle button, same "< label >" pattern DummyPilotConfigScreen's own skin selector already uses).
		int weaponRowY = startY + rowHeight;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"),
				button -> this.tudursvehiclemod$cycleWeapon(-1))
				.dimensions(centerX - 100, weaponRowY, 20, 20).build());
		this.weaponNameButton = ButtonWidget.builder(this.tudursvehiclemod$weaponButtonText(), button -> {})
				.dimensions(centerX - 76, weaponRowY, 152, 20).build();
		this.addDrawableChild(this.weaponNameButton);
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"),
				button -> this.tudursvehiclemod$cycleWeapon(1))
				.dimensions(centerX + 80, weaponRowY, 20, 20).build());

		int altitudeRowY = weaponRowY + rowHeight;
		this.attackStartAltitudeField = new TextFieldWidget(this.textRenderer, centerX - 100, altitudeRowY, 96, 20,
				Text.translatable("gui.tudursvehiclemod.dummy_pilot_weapon_config.attack_start_altitude_field"));
		this.attackStartAltitudeField.setMaxLength(16);
		this.attackStartAltitudeField.setText(this.initialAttackStartAltitude);
		this.attackStartAltitudeField.setChangedListener(text -> this.tudursvehiclemod$sendUpdate());
		this.addDrawableChild(this.attackStartAltitudeField);

		this.attackStopAltitudeField = new TextFieldWidget(this.textRenderer, centerX + 4, altitudeRowY, 96, 20,
				Text.translatable("gui.tudursvehiclemod.dummy_pilot_weapon_config.attack_stop_altitude_field"));
		this.attackStopAltitudeField.setMaxLength(16);
		this.attackStopAltitudeField.setText(this.initialAttackStopAltitude);
		this.attackStopAltitudeField.setChangedListener(text -> this.tudursvehiclemod$sendUpdate());
		this.addDrawableChild(this.attackStopAltitudeField);

		// How far (blocks) the dummy pilot looks for a hostile mob to engage.
		int searchRangeRowY = altitudeRowY + rowHeight;
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.dummy_pilot_weapon_config.search_range_label"), button -> {})
				.dimensions(centerX - 100, searchRangeRowY, 96, 20).build());
		this.searchRangeField = new TextFieldWidget(this.textRenderer, centerX + 4, searchRangeRowY, 96, 20,
				Text.translatable("gui.tudursvehiclemod.dummy_pilot_weapon_config.search_range_field"));
		this.searchRangeField.setMaxLength(16);
		this.searchRangeField.setText(this.initialSearchRange);
		this.searchRangeField.setChangedListener(text -> this.tudursvehiclemod$sendUpdate());
		this.addDrawableChild(this.searchRangeField);

		// How much higher than the target's own actual Y this dummy pilot's own aircraft dives toward (0 means "use this weapon's own type-based built-in default" - see entity.AircraftEntity's own CARRIER_LOCK_FALLING_WEAPON_DIVE_TARGET_Y_OFFSET doc).
		int diveTargetYOffsetRowY = searchRangeRowY + rowHeight;
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.dummy_pilot_weapon_config.dive_target_y_offset_label"), button -> {})
				.dimensions(centerX - 100, diveTargetYOffsetRowY, 96, 20).build());
		this.diveTargetYOffsetField = new TextFieldWidget(this.textRenderer, centerX + 4, diveTargetYOffsetRowY, 96, 20,
				Text.translatable("gui.tudursvehiclemod.dummy_pilot_weapon_config.dive_target_y_offset_field"));
		this.diveTargetYOffsetField.setMaxLength(16);
		this.diveTargetYOffsetField.setText(this.initialDiveTargetYOffset);
		this.diveTargetYOffsetField.setChangedListener(text -> this.tudursvehiclemod$sendUpdate());
		this.addDrawableChild(this.diveTargetYOffsetField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.close"),
				button -> this.close())
				.dimensions(centerX - 100, diveTargetYOffsetRowY + rowHeight + 8, 200, 20).build());
	}

	/** Cycles weaponIndex through -1 (not selected) plus every actual index in weaponDisplayNames - "not selected" is always reachable this way, never skipped over, regardless of which weapon happens to be currently selected. A no-op if weaponDisplayNames is empty (nothing bound/loaded, or a weapon-less vehicle) - weaponIndex simply stays at whatever it already was (-1, in every such case, per block.DroneCenterBlockEntity's own doc). */
	private void tudursvehiclemod$cycleWeapon(int direction) {
		if (this.weaponDisplayNames.isEmpty()) {
			return;
		}
		// Treats the range as [-1, 0, 1,.., size-1] (size+1 total positions), cycling through that whole range.
		int currentPosition = this.weaponIndex + 1;
		int nextPosition = Math.floorMod(currentPosition + direction, this.weaponDisplayNames.size() + 1);
		this.weaponIndex = nextPosition - 1;
		this.weaponNameButton.setMessage(this.tudursvehiclemod$weaponButtonText());
		this.tudursvehiclemod$sendUpdate();
	}

	/** Shows a translated "not selected" label whenever weaponIndex is -1 (or otherwise out of range for the current weaponDisplayNames - can happen if the bound vehicle's own definition changed since this screen's own data was fetched), rather than the weapon's own display name. */
	private Text tudursvehiclemod$weaponButtonText() {
		if (this.weaponIndex < 0 || this.weaponIndex >= this.weaponDisplayNames.size()) {
			return Text.translatable("gui.tudursvehiclemod.dummy_pilot_weapon_config.weapon_unselected");
		}
		return Text.literal(this.weaponDisplayNames.get(this.weaponIndex));
	}

	/** See this class's own doc for why every change is sent immediately rather than on close, and why the numeric fields are parsed defensively. */
	private void tudursvehiclemod$sendUpdate() {
		float attackStartAltitude = this.tudursvehiclemod$parseFloatOr(this.attackStartAltitudeField, this.initialAttackStartAltitude);
		float attackStopAltitude = this.tudursvehiclemod$parseFloatOr(this.attackStopAltitudeField, this.initialAttackStopAltitude);
		float searchRange = this.tudursvehiclemod$parseFloatOr(this.searchRangeField, this.initialSearchRange);
		float diveTargetYOffset = this.tudursvehiclemod$parseFloatOr(this.diveTargetYOffsetField, this.initialDiveTargetYOffset);
		ClientPlayNetworking.send(new DummyPilotWeaponConfigUpdatePayload(
				this.blockX, this.blockY, this.blockZ, this.weaponIndex, attackStartAltitude, attackStopAltitude, searchRange, diveTargetYOffset));
	}

	private float tudursvehiclemod$parseFloatOr(TextFieldWidget field, String fallback) {
		String text = field != null ? field.getText() : fallback;
		try {
			return Float.parseFloat(text);
		} catch (NumberFormatException e) {
			try {
				return Float.parseFloat(fallback);
			} catch (NumberFormatException e2) {
				return 0.0f;
			}
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
