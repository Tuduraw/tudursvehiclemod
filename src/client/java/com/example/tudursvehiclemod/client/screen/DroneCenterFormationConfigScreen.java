package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.network.DroneCenterFormationConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** The formation config controls (formationSize/formationType/formationSpacing) overflowed client.screen.DroneCenterConfigScreen at standard GUI scale: those controls moved to this dedicated screen, opened via a button from that screen - matching the existing "詳細設定" pattern (one button leading to a whole separate sub-screen) rather than cramming more rows/buttons onto the main screen. Mirrors that screen's own +/-/field row layout closely for formationSize/formationSpacing; formationType uses a single cycle button spanning the full row instead (no meaningful "+/-" for an enum). Also hosts the button through to client.screen.DroneCenterFormationScreen (the wingman slot UI), consolidating both formation-related sub-screens behind one entry point on the main config screen. */
public class DroneCenterFormationConfigScreen extends Screen {

	private final int blockX;
	private final int blockY;
	private final int blockZ;

	private int formationSize;
	private com.example.tudursvehiclemod.asset.FormationType formationType;
	private double formationSpacing;
	private final double formationElementSpacing;

	private static final int FIELD_WIDTH = 30;
	private static final int LABEL_WIDTH = 110;
	private static final int ROW_HEIGHT = 24;

	private TextFieldWidget formationSizeField;
	private TextFieldWidget formationSpacingField;
	private ButtonWidget formationTypeButton;

	/** Set while a text field's own setChangedListener is applying a value FROM the +/- buttons (or initial population), so that listener doesn't immediately re-parse its own just-set text and send a redundant update - same convention DroneCenterConfigScreen's own identical fields already use. */
	private boolean populatingFieldText;

	public DroneCenterFormationConfigScreen(int blockX, int blockY, int blockZ,
			int formationSize, com.example.tudursvehiclemod.asset.FormationType formationType,
			double formationSpacing, double formationElementSpacing) {
		super(Text.translatable("gui.tudursvehiclemod.drone_center_formation_config"));
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
		this.formationSize = formationSize;
		this.formationType = formationType;
		this.formationSpacing = formationSpacing;
		// Per DroneCenterConfigOpenPayload's own formationConfig doc: no in-game control exists for this value at all yet (matches this project's own CAS/Carrier equivalent, raw weapon.txt key only) - round-tripped unchanged whenever this screen sends any OTHER update.
		this.formationElementSpacing = formationElementSpacing;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 2 - 50;

		int labelX = centerX - LABEL_WIDTH - 6;
		int minusX = centerX - 4;
		int fieldX = minusX + 22;
		int plusX = fieldX + FIELD_WIDTH + 2;

		int sizeRowY = startY;
		int typeRowY = startY + ROW_HEIGHT;
		int spacingRowY = startY + ROW_HEIGHT * 2;
		int afterRowsY = startY + ROW_HEIGHT * 3 + 8;

		// FormationSize (total including the leader) - min 1 (no formation at all), no fixed upper bound (a large value is simply paginated by DroneCenterFormationScreen's own 45-slot pages).
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.formation_size_label"), button -> {}
		).dimensions(labelX, sizeRowY, LABEL_WIDTH, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("-"),
				button -> this.tudursvehiclemod$adjustFormationSize(-1)
		).dimensions(minusX, sizeRowY, 20, 20).build());
		this.formationSizeField = new TextFieldWidget(this.textRenderer, fieldX, sizeRowY, FIELD_WIDTH, 20,
				Text.translatable("gui.tudursvehiclemod.drone_center_config.formation_size_field"));
		this.formationSizeField.setMaxLength(3);
		this.formationSizeField.setChangedListener(this::tudursvehiclemod$onFormationSizeFieldChanged);
		this.addDrawableChild(this.formationSizeField);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+"),
				button -> this.tudursvehiclemod$adjustFormationSize(1)
		).dimensions(plusX, sizeRowY, 20, 20).build());

		// A single cycle button spanning the full row (no meaningful "+/-" for a FormationType enum) - clicking advances to the next value, wrapping back to the first past the last.
		this.formationTypeButton = ButtonWidget.builder(this.tudursvehiclemod$formationTypeText(), button -> {
					com.example.tudursvehiclemod.asset.FormationType[] values = com.example.tudursvehiclemod.asset.FormationType.values();
					this.formationType = values[(this.formationType.ordinal() + 1) % values.length];
					this.formationTypeButton.setMessage(this.tudursvehiclemod$formationTypeText());
					this.tudursvehiclemod$sendUpdate();
				}
		).dimensions(labelX, typeRowY, LABEL_WIDTH + 22 + FIELD_WIDTH + 22, 20).build();
		this.addDrawableChild(this.formationTypeButton);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.formation_spacing_label"), button -> {}
		).dimensions(labelX, spacingRowY, LABEL_WIDTH, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("-"),
				button -> this.tudursvehiclemod$adjustFormationSpacing(-1.0)
		).dimensions(minusX, spacingRowY, 20, 20).build());
		this.formationSpacingField = new TextFieldWidget(this.textRenderer, fieldX, spacingRowY, FIELD_WIDTH, 20,
				Text.translatable("gui.tudursvehiclemod.drone_center_config.formation_spacing_field"));
		this.formationSpacingField.setMaxLength(3);
		this.formationSpacingField.setChangedListener(this::tudursvehiclemod$onFormationSpacingFieldChanged);
		this.addDrawableChild(this.formationSpacingField);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+"),
				button -> this.tudursvehiclemod$adjustFormationSpacing(1.0)
		).dimensions(plusX, spacingRowY, 20, 20).build());

		// Opens screen.DroneCenterFormationScreenHandler's own wingman slot UI (reuses the exact same DroneControlStickItem binding mechanism the leader itself already uses - see block.DroneCenterBlockEntity's own FormationSlotsInventory doc).
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.drone_center_config.formation_slots"),
				button -> ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.OpenDroneCenterFormationPayload(
						this.blockX, this.blockY, this.blockZ))
		).dimensions(centerX - 100, afterRowsY, 200, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.close"),
				button -> this.close()
		).dimensions(centerX - 100, afterRowsY + 24, 200, 20).build());

		// Populates each field's own initial text without treating it as a user edit (see populatingFieldText's own doc).
		this.populatingFieldText = true;
		this.formationSizeField.setText(String.valueOf(this.formationSize));
		this.formationSpacingField.setText(String.valueOf(Math.round(this.formationSpacing)));
		this.populatingFieldText = false;
	}

	private void tudursvehiclemod$adjustFormationSize(int delta) {
		this.formationSize = Math.max(1, this.formationSize + delta);
		this.populatingFieldText = true;
		this.formationSizeField.setText(String.valueOf(this.formationSize));
		this.populatingFieldText = false;
		this.tudursvehiclemod$sendUpdate();
	}

	private void tudursvehiclemod$onFormationSizeFieldChanged(String text) {
		if (this.populatingFieldText) {
			return;
		}
		try {
			this.formationSize = Math.max(1, Integer.parseInt(text.strip()));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
			// Mid-edit (empty, just "-", etc.) - leave the last valid value in place rather than reverting/rejecting the keystroke.
		}
	}

	private void tudursvehiclemod$adjustFormationSpacing(double delta) {
		this.formationSpacing = Math.max(0.5, this.formationSpacing + delta);
		this.populatingFieldText = true;
		this.formationSpacingField.setText(String.valueOf(Math.round(this.formationSpacing)));
		this.populatingFieldText = false;
		this.tudursvehiclemod$sendUpdate();
	}

	private void tudursvehiclemod$onFormationSpacingFieldChanged(String text) {
		if (this.populatingFieldText) {
			return;
		}
		try {
			this.formationSpacing = Math.max(0.5, Double.parseDouble(text.strip()));
			this.tudursvehiclemod$sendUpdate();
		} catch (NumberFormatException ignored) {
		}
	}

	private Text tudursvehiclemod$formationTypeText() {
		String key = switch (this.formationType) {
			case LINE_ABREAST -> "gui.tudursvehiclemod.drone_center_config.formation_type.line_abreast";
			case LINE_ASTERN -> "gui.tudursvehiclemod.drone_center_config.formation_type.line_astern";
			case V_FORMATION -> "gui.tudursvehiclemod.drone_center_config.formation_type.v_formation";
			case DIAMOND -> "gui.tudursvehiclemod.drone_center_config.formation_type.diamond";
			case DELTA -> "gui.tudursvehiclemod.drone_center_config.formation_type.delta";
		};
		return Text.translatable("gui.tudursvehiclemod.drone_center_config.formation_type_label", Text.translatable(key));
	}

	private void tudursvehiclemod$sendUpdate() {
		String formationConfig = this.formationSize + "," + this.formationType.name() + "," + this.formationSpacing + "," + this.formationElementSpacing;
		ClientPlayNetworking.send(new DroneCenterFormationConfigUpdatePayload(this.blockX, this.blockY, this.blockZ, formationConfig));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
