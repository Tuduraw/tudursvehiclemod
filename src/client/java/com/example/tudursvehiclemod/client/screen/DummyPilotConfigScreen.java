package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.client.render.DummyPilotSkins;
import com.example.tudursvehiclemod.network.DummyPilotConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** Toggles whether this center puts a dummy pilot in its vehicle's pilot seat, picks which skin that pilot uses, and controls whether its nametag shows at all and what it says.
 *
 * The skin list is whatever the server sent (see DummyPilotConfigOpenPayload's own doc for why it's the server's list, not a client-side scan). so an addon-supplied skin previews exactly as it will actually render rather than showing a generic placeholder.
 *
 * This screen's own layout was already at its practical display limit, so the dummy pilot's own combat settings (weapon selection, attack altitudes, hostile-mob search range) live in a dedicated sub-screen instead (client.screen.DummyPilotWeaponConfigScreen), reached via this screen's own "Weapon Settings" button - same request/reply round-trip pattern as this screen's own reachability from DroneCenterConfigScreen (the bound vehicle's own weapon list is server-side data the client can't look up on its own).
 *
 * Every change is sent immediately (not on close), matching DroneCenterConfigScreen's own established behavior on this block - so toggling an option, cycling skins, or editing the name/auto-resume-mode takes visible effect on the vehicle right away. The name field sends on every keystroke rather than only on close/defocus, for that same immediacy - a person typing a name generally wants to see it applied as they go, matching this screen's own established convention rather than introducing a different, delayed one just for this field. */
public class DummyPilotConfigScreen extends Screen {

	private final int blockX;
	private final int blockY;
	private final int blockZ;

	private boolean enabled;
	private String skinId;
	private final List<String> availableSkinIds;
	private int skinIndex;
	private boolean nameVisible;
	private final String initialName;

	/** Mirrors block.DroneCenterBlockEntity's own AutoDisableResumeMode - true for AUTO_RESUME ("モード2"), false for MANUAL_REENABLE ("モード1"). Starts false (this screen's own constructor has no way to know the real value yet - it arrives moments later via network.DroneCenterAutoDisableModeOpenPayload, a separate payload sent right after the one that opens this very screen - see tudursvehiclemod$setAutoResumeMode(), called by that payload's own client-side handler once it arrives). */
	private boolean autoResumeMode;
	private ButtonWidget autoResumeModeToggleButton;

	private ButtonWidget enabledToggleButton;
	private ButtonWidget skinNameButton;
	private ButtonWidget nameVisibleToggleButton;
	private TextFieldWidget nameField;

	/** Where the preview is drawn, filled in during init() so render() doesn't need to recompute the layout. */
	private int previewX;
	private int previewY;

	/** Size (px) the preview face is drawn at. A vanilla skin's own face is 8x8 texels, so this is a straight 6x magnification. */
	private static final int PREVIEW_SIZE = 48;

	public DummyPilotConfigScreen(int blockX, int blockY, int blockZ, boolean enabled, String skinId, List<String> availableSkinIds,
			boolean nameVisible, String name) {
		super(Text.translatable("gui.tudursvehiclemod.dummy_pilot_config"));
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
		this.enabled = enabled;
		this.availableSkinIds = availableSkinIds.isEmpty() ? List.of(skinId) : List.copyOf(availableSkinIds);
		this.skinId = skinId;
		this.skinIndex = Math.max(0, this.availableSkinIds.indexOf(skinId));
		this.nameVisible = nameVisible;
		this.initialName = name == null ? "" : name;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 2 - 108;
		int rowHeight = 24;

		this.enabledToggleButton = ButtonWidget.builder(this.tudursvehiclemod$enabledText(), button -> {
			this.enabled = !this.enabled;
			this.enabledToggleButton.setMessage(this.tudursvehiclemod$enabledText());
			this.tudursvehiclemod$sendUpdate();
		}).dimensions(centerX - 100, startY, 200, 20).build();
		this.addDrawableChild(this.enabledToggleButton);

		// Preview sits between the toggle and the skin selector - see render() for the actual drawing.
		this.previewX = centerX - PREVIEW_SIZE / 2;
		this.previewY = startY + rowHeight + 4;

		int selectorY = this.previewY + PREVIEW_SIZE + 8;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"),
				button -> this.tudursvehiclemod$cycleSkin(-1))
				.dimensions(centerX - 100, selectorY, 20, 20).build());
		this.skinNameButton = ButtonWidget.builder(Text.literal(this.skinId), button -> {})
				.dimensions(centerX - 76, selectorY, 152, 20).build();
		this.addDrawableChild(this.skinNameButton);
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"),
				button -> this.tudursvehiclemod$cycleSkin(1))
				.dimensions(centerX + 80, selectorY, 20, 20).build());

		// Nametag visibility toggle and an arbitrary custom name.
		int nameRowY = selectorY + rowHeight;
		this.nameVisibleToggleButton = ButtonWidget.builder(this.tudursvehiclemod$nameVisibleText(), button -> {
			this.nameVisible = !this.nameVisible;
			this.nameVisibleToggleButton.setMessage(this.tudursvehiclemod$nameVisibleText());
			this.tudursvehiclemod$sendUpdate();
		}).dimensions(centerX - 100, nameRowY, 200, 20).build();
		this.addDrawableChild(this.nameVisibleToggleButton);

		this.nameField = new TextFieldWidget(this.textRenderer, centerX - 100, nameRowY + rowHeight, 200, 20,
				Text.translatable("gui.tudursvehiclemod.dummy_pilot_config.name_field"));
		this.nameField.setMaxLength(64);
		this.nameField.setText(this.initialName);
		// Per this class's own doc: sends on every keystroke rather than only on defocus/close, matching this screen's own established "every change takes effect immediately" behavior for its other controls.
		this.nameField.setChangedListener(text -> this.tudursvehiclemod$sendUpdate());
		this.addDrawableChild(this.nameField);

		// Toggles between the two named modes for what happens after an automatic low-fuel/low-ammo disable.
		int autoResumeRowY = nameRowY + rowHeight * 2 + 8;
		this.autoResumeModeToggleButton = ButtonWidget.builder(this.tudursvehiclemod$autoResumeModeText(), button -> {
			this.autoResumeMode = !this.autoResumeMode;
			this.autoResumeModeToggleButton.setMessage(this.tudursvehiclemod$autoResumeModeText());
			ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.DroneCenterAutoDisableModeUpdatePayload(
					this.blockX, this.blockY, this.blockZ, this.autoResumeMode));
		}).dimensions(centerX - 100, autoResumeRowY, 200, 20).build();
		this.addDrawableChild(this.autoResumeModeToggleButton);

		// Per this class's own doc: weapon/altitude/search-range settings moved to their own dedicated sub-screen, reached here - same request/reply pattern DroneCenterConfigScreen's own button for THIS screen already uses.
		int weaponSettingsRowY = autoResumeRowY + rowHeight;
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.dummy_pilot_config.weapon_settings"),
				button -> ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.DummyPilotWeaponConfigRequestPayload(
						this.blockX, this.blockY, this.blockZ))
		).dimensions(centerX - 100, weaponSettingsRowY, 200, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursvehiclemod.close"),
				button -> this.close())
				.dimensions(centerX - 100, weaponSettingsRowY + rowHeight + 8, 200, 20).build());
	}

	/** Per this class's own doc: draws the currently-selected skin's own face, cut straight out of the real texture.
	 *
	 * The face is at texels (8,8)-(16,16) of a 64x64 vanilla skin, and the "hat" overlay layer at (40,8)-(48,16) is drawn over it - exactly how vanilla composites a head everywhere else, so a skin that puts detail in the overlay layer (very common) doesn't preview as a blank/odd face. */
	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		Identifier texture = DummyPilotSkins.tudursvehiclemod$getTexture(this.skinId);
		// 10-arg overload: (x, y, u, v, drawW, drawH, regionW, regionH, texW, texH) - the one that magnifies an 8x8 source region up to PREVIEW_SIZE. The 9-arg form used elsewhere in this project draws 1:1 and can't.
		context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture,
				this.previewX, this.previewY, 8.0f, 8.0f, PREVIEW_SIZE, PREVIEW_SIZE, 8, 8, 64, 64);
		context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture,
				this.previewX, this.previewY, 40.0f, 8.0f, PREVIEW_SIZE, PREVIEW_SIZE, 8, 8, 64, 64);
	}

	private void tudursvehiclemod$cycleSkin(int direction) {
		if (this.availableSkinIds.isEmpty()) {
			return;
		}
		this.skinIndex = Math.floorMod(this.skinIndex + direction, this.availableSkinIds.size());
		this.skinId = this.availableSkinIds.get(this.skinIndex);
		this.skinNameButton.setMessage(Text.literal(this.skinId));
		this.tudursvehiclemod$sendUpdate();
	}

	private Text tudursvehiclemod$enabledText() {
		return Text.translatable(this.enabled
				? "gui.tudursvehiclemod.dummy_pilot_config.enabled"
				: "gui.tudursvehiclemod.dummy_pilot_config.disabled");
	}

	private Text tudursvehiclemod$nameVisibleText() {
		return Text.translatable(this.nameVisible
				? "gui.tudursvehiclemod.dummy_pilot_config.name_visible"
				: "gui.tudursvehiclemod.dummy_pilot_config.name_hidden");
	}

	private Text tudursvehiclemod$autoResumeModeText() {
		return Text.translatable(this.autoResumeMode
				? "gui.tudursvehiclemod.dummy_pilot_config.auto_resume_mode_2"
				: "gui.tudursvehiclemod.dummy_pilot_config.auto_resume_mode_1");
	}

	/** Called by network.DroneCenterAutoDisableModeOpenPayload's own client-side handler once that separate payload arrives (see autoResumeMode's own doc for why this screen's own constructor can't already know the real value). Updates the button's own label if it's already been built (init() may not have run yet the very first time this fires, in principle, though channel ordering makes that exceedingly unlikely in practice - guarded anyway rather than relying on that timing). */
	public void tudursvehiclemod$setAutoResumeMode(boolean autoResumeMode) {
		this.autoResumeMode = autoResumeMode;
		if (this.autoResumeModeToggleButton != null) {
			this.autoResumeModeToggleButton.setMessage(this.tudursvehiclemod$autoResumeModeText());
		}
	}

	/** See this class's own doc for why every change is sent immediately rather than on close. */
	private void tudursvehiclemod$sendUpdate() {
		// nameField is null on the very first call chain (button callbacks can't fire before init() finishes, but tudursvehiclemod$sendUpdate() itself has no other early caller) - guarded anyway since setChangedListener's own callback could in principle run during construction of the widget itself on some platforms.
		String name = this.nameField != null ? this.nameField.getText() : this.initialName;
		ClientPlayNetworking.send(new DummyPilotConfigUpdatePayload(
				this.blockX, this.blockY, this.blockZ, this.enabled, this.skinId, this.nameVisible, name));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
