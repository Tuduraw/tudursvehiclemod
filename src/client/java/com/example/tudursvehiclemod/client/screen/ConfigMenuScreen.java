package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.client.VehicleModClient;
import com.example.tudursvehiclemod.client.VehicleModConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Edits VehicleModConfig from inside the game, so switching between the translucency modes for shader-pack use no longer means editing the config file by hand every time.
 *
 * EVERY change here is saved immediately, so nothing is lost if the game is closed without any further action.
 *
 * HOW EACH SETTING APPLIES - three tiers, because they genuinely differ and pretending otherwise would mislead:
 * - Immediately: settings read fresh every frame (HUD, culling, camera distance).
 * - On resource reload: the texture-dither settings, since they are applied while textures are being read. The debug menu's own reload button triggers exactly that, so it is one click away.
 * - Only after a restart: translucencyMode and experimentalTriangleRendering, because the render pipeline and its layers are constructed once at class initialisation and reused for the rest of the session.
 *
 * Per the same request, anything in the last tier says so at the START of its own tooltip rather than burying it at the end.
 *
 * Entries are built once as a flat list and sliced into fixed-size pages, rather than laid out individually - adding a ninth setting in the future means appending one more entry to that list, nothing about the pagination itself needs to change. */
public class ConfigMenuScreen extends Screen {

	private static final int BUTTON_WIDTH = 260;
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW_SPACING = 24;
	/** The navigation row occupies one slot's worth of vertical space, so seven settings plus it fills the same height eight settings did. */
	private static final int ENTRIES_PER_PAGE = 7;

	/** One row's own label, tooltip, and what pressing it does. Building the full list once and paginating it is what lets a future setting just be appended here - see this class's own doc. */
	private record ConfigEntry(Text label, String tooltipKey, Runnable action) {}

	private final int page;

	public ConfigMenuScreen() {
		this(0);
	}

	public ConfigMenuScreen(int page) {
		super(Text.translatable("gui.tudursvehiclemod.config_menu"));
		this.page = page;
	}

	@Override
	protected void init() {
		VehicleModConfig config = VehicleModClient.getConfig();
		if (config == null) {
			return;
		}
		List<ConfigEntry> entries = tudursvehiclemod$buildEntries(config);
		int pageCount = Math.max(1, (entries.size() + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE);
		int currentPage = Math.max(0, Math.min(this.page, pageCount - 1));

		int centerX = this.width / 2;
		int y = Math.max(32, this.height / 2 - (ROW_SPACING * (ENTRIES_PER_PAGE / 2 + 1)));

		// ---- Pagination controls, drawn ABOVE the settings, so the page indicator stays in one place instead of shifting down whenever a page holds fewer rows than the others. Only shown when there is more than one page, so a short settings list looks exactly as it did before this feature existed. ----
		if (pageCount > 1) {
			int navWidth = 60;
			int gap = 4;

			this.addDrawableChild(ButtonWidget.builder(
					Text.literal("<"),
					button -> {
						if (this.client != null) {
							this.client.setScreen(new ConfigMenuScreen(currentPage > 0 ? currentPage - 1 : pageCount - 1));
						}
					}
			).dimensions(centerX - BUTTON_WIDTH / 2, y, navWidth, BUTTON_HEIGHT).build());

			ButtonWidget indicator = ButtonWidget.builder(
					Text.translatable("gui.tudursvehiclemod.config.page", currentPage + 1, pageCount),
					button -> {}
			).dimensions(centerX - BUTTON_WIDTH / 2 + navWidth + gap, y, BUTTON_WIDTH - navWidth * 2 - gap * 2, BUTTON_HEIGHT).build();
			indicator.active = false;
			this.addDrawableChild(indicator);

			this.addDrawableChild(ButtonWidget.builder(
					Text.literal(">"),
					button -> {
						if (this.client != null) {
							this.client.setScreen(new ConfigMenuScreen(currentPage < pageCount - 1 ? currentPage + 1 : 0));
						}
					}
			).dimensions(centerX + BUTTON_WIDTH / 2 - navWidth, y, navWidth, BUTTON_HEIGHT).build());

			y += ROW_SPACING;
		}

		int start = currentPage * ENTRIES_PER_PAGE;
		int end = Math.min(entries.size(), start + ENTRIES_PER_PAGE);
		for (int i = start; i < end; i++) {
			y = tudursvehiclemod$addRow(centerX, y, entries.get(i));
		}

		this.addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.tudursvehiclemod.close"),
				button -> this.close()
		).dimensions(centerX - BUTTON_WIDTH / 2, y + 4, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	/** Every setting this menu edits, in a fixed order - see this class's own doc for why this is a flat list rather than laid out per-page directly. Each entry's own action rebuilds the screen on the CURRENT page afterwards (see tudursvehiclemod$addRow()'s own doc), so a button's own label is always freshly derived from config rather than tracked separately. */
	private List<ConfigEntry> tudursvehiclemod$buildEntries(VehicleModConfig config) {
		List<ConfigEntry> entries = new ArrayList<>();

		// ---- Translucency (the settings this menu mainly exists for) ----
		entries.add(new ConfigEntry(
				tudursvehiclemod$translucencyModeLabel(config),
				"gui.tudursvehiclemod.config.translucency_mode.tooltip",
				() -> config.translucencyMode = tudursvehiclemod$nextTranslucencyMode(config.translucencyMode)));

		// ---- Search light (see VehicleModConfig.searchLightMode's own doc) ----
		entries.add(new ConfigEntry(
				tudursvehiclemod$searchLightModeLabel(config),
				"gui.tudursvehiclemod.config.search_light_mode.tooltip",
				() -> config.searchLightMode = tudursvehiclemod$nextSearchLightMode(config.searchLightMode)));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.config.search_light_brightness",
						String.format(java.util.Locale.ROOT, "%.1f", config.searchLightBrightness)),
				"gui.tudursvehiclemod.config.search_light_brightness.tooltip",
				// 0 (off) through 6, in halves up to 3 and then whole steps - fine control around the default, coarser above it where each step matters less because the result saturates against vanilla's own 0-15 cap anyway.
				() -> {
					double next = config.searchLightBrightness < 3.0
							? config.searchLightBrightness + 0.5
							: config.searchLightBrightness + 1.0;
					config.searchLightBrightness = next > 6.0 ? 0.0 : next;
				}));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.config.search_light_block_brightness",
						String.format(java.util.Locale.ROOT, "%.1f", config.searchLightBlockBrightness)),
				"gui.tudursvehiclemod.config.search_light_block_brightness.tooltip",
				// 0 (off) through 3.0 in halves - the block overlay saturates visually well before the entity-side setting does, so it needs neither the same range nor the same step size.
				() -> {
					double next = Math.round((config.searchLightBlockBrightness + 0.5) * 10.0) / 10.0;
					config.searchLightBlockBrightness = next > 3.0 ? 0.0 : next;
				}));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.config.search_light_overlay_sample_count",
						String.valueOf(config.searchLightBlockOverlaySampleCount)),
				"gui.tudursvehiclemod.config.search_light_overlay_sample_count.tooltip",
				// 50 through 400 in steps of 50 - comfortably spans "very cheap, coarser coverage" to "well beyond what block-scale detection actually needs", per the direct observation that this task needs far fewer samples than a visually-fine-grained one would.
				() -> {
					int next = config.searchLightBlockOverlaySampleCount + 50;
					config.searchLightBlockOverlaySampleCount = next > 400 ? 50 : next;
				}));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.config.search_light_overlay_interval",
						String.valueOf(config.searchLightBlockOverlayIntervalTicks)),
				"gui.tudursvehiclemod.config.search_light_overlay_interval.tooltip",
				// 1 through 10 ticks - 1 is the original every-tick behaviour, 10 is half a second of lag on a moving beam, beyond which the overlay starts visibly trailing rather than merely updating less often.
				() -> {
					int next = config.searchLightBlockOverlayIntervalTicks + 1;
					config.searchLightBlockOverlayIntervalTicks = next > 10 ? 1 : next;
				}));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.config.search_light_cone_display_distance",
						String.format(java.util.Locale.ROOT, "%.1f", config.searchLightConeDisplayDistance)),
				"gui.tudursvehiclemod.config.search_light_cone_display_distance.tooltip",
				// 0.1 through 1.0 in steps of 0.1, wrapping back to 0.1 rather than 0 - a value of exactly 0 would draw nothing at all, which isn't a useful step to land on repeatedly while cycling.
				() -> {
					double next = Math.round((config.searchLightConeDisplayDistance + 0.1) * 10.0) / 10.0;
					config.searchLightConeDisplayDistance = next > 1.0 ? 0.1 : next;
				}));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.config.search_light_chunk_rebuild_interval", config.searchLightChunkRebuildIntervalTicks),
				"gui.tudursvehiclemod.config.search_light_chunk_rebuild_interval.tooltip",
				// 1 through 40 ticks (2 seconds) in steps of 1 up to 10, then coarser above it - fine control where it matters most (near the default), without an impractically long cycle to reach the high end.
				() -> {
					int next = config.searchLightChunkRebuildIntervalTicks < 10
							? config.searchLightChunkRebuildIntervalTicks + 1
							: config.searchLightChunkRebuildIntervalTicks + 5;
					config.searchLightChunkRebuildIntervalTicks = next > 40 ? 1 : next;
				}));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.config.search_light_ring_count_multiplier",
						String.format(java.util.Locale.ROOT, "%.1f", config.searchLightRingCountMultiplier)),
				"gui.tudursvehiclemod.config.search_light_ring_count_multiplier.tooltip",
				// The FIELD is never clamped upward anywhere this is read (see VehicleModEntityRenderer.tudursvehiclemod$scaledRingCount()'s own doc), so a value beyond this button's own cycle is always reachable by editing the config file directly. The button itself cycles through a bounded, practical set of common values and wraps back to the low end - an unbounded "always increase, never decrease" button would have no way back down without that file edit, which is worse UX than the small inconvenience of the file edit for the rare person who wants to go past this cycle's own top.
				() -> config.searchLightRingCountMultiplier = tudursvehiclemod$nextCountMultiplier(config.searchLightRingCountMultiplier)));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.config.search_light_blade_count_multiplier",
						String.format(java.util.Locale.ROOT, "%.1f", config.searchLightBladeCountMultiplier)),
				"gui.tudursvehiclemod.config.search_light_blade_count_multiplier.tooltip",
				// Same bounded convenience cycle as searchLightRingCountMultiplier's own entry, for the same reason.
				() -> config.searchLightBladeCountMultiplier = tudursvehiclemod$nextCountMultiplier(config.searchLightBladeCountMultiplier)));

		entries.add(new ConfigEntry(
				tudursvehiclemod$upscaleLabel(config),
				"gui.tudursvehiclemod.config.dither_upscale.tooltip",
				// Powers of two only, since the size cap halves the factor and a non-power would not reduce cleanly. the sequence extends BELOW 1 for weaker hardware, where shrinking the texture trades pattern quality for memory.
				() -> config.translucencyDitherUpscale = config.translucencyDitherUpscale >= 8.0
						? 0.25
						: config.translucencyDitherUpscale * 2.0));

		entries.add(new ConfigEntry(
				tudursvehiclemod$toggleLabel("gui.tudursvehiclemod.config.gpu_compression", config.translucencyDitherGpuCompression),
				"gui.tudursvehiclemod.config.gpu_compression.tooltip",
				() -> config.translucencyDitherGpuCompression = !config.translucencyDitherGpuCompression));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.config.dither_max_size", config.translucencyDitherMaxTextureSize),
				"gui.tudursvehiclemod.config.dither_max_size.tooltip",
				() -> config.translucencyDitherMaxTextureSize = config.translucencyDitherMaxTextureSize >= 8192
						? 512
						: Math.max(512, config.translucencyDitherMaxTextureSize) * 2));

		// ---- Rendering ----
		entries.add(new ConfigEntry(
				tudursvehiclemod$toggleLabel("gui.tudursvehiclemod.config.experimental_triangles", config.experimentalTriangleRendering),
				"gui.tudursvehiclemod.config.experimental_triangles.tooltip",
				() -> config.experimentalTriangleRendering = !config.experimentalTriangleRendering));

		entries.add(new ConfigEntry(
				tudursvehiclemod$toggleLabel("gui.tudursvehiclemod.config.match_view_distance", config.matchChunkViewDistanceForEntityRender),
				"gui.tudursvehiclemod.config.match_view_distance.tooltip",
				() -> config.matchChunkViewDistanceForEntityRender = !config.matchChunkViewDistanceForEntityRender));

		entries.add(new ConfigEntry(
				tudursvehiclemod$toggleLabel("gui.tudursvehiclemod.config.disable_culling", config.disableEntityRenderCulling),
				"gui.tudursvehiclemod.config.disable_culling.tooltip",
				() -> config.disableEntityRenderCulling = !config.disableEntityRenderCulling));

		entries.add(new ConfigEntry(
				tudursvehiclemod$toggleLabel("gui.tudursvehiclemod.config.third_person_hud", config.showHudInThirdPerson),
				"gui.tudursvehiclemod.config.third_person_hud.tooltip",
				() -> config.showHudInThirdPerson = !config.showHudInThirdPerson));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.config.camera_distance", (int) config.maxAircraftCameraDistance),
				"gui.tudursvehiclemod.config.camera_distance.tooltip",
				() -> config.maxAircraftCameraDistance = config.maxAircraftCameraDistance >= 160f
						? 20f
						: config.maxAircraftCameraDistance + 20f));

		return entries;
	}

	/** Adds one row and returns the next row's own Y. Every button carries a hover tooltip, per this class's own doc.
	 *
	 * Pressing a row's own button runs its action, saves, and then rebuilds this SAME screen on the SAME page (rather than updating the button's own label in place, as the pre-pagination version did) - config always drives the label, so there is nothing to keep in sync separately, and this stays correct regardless of how entries end up distributed across pages. */
	private int tudursvehiclemod$addRow(int centerX, int y, ConfigEntry entry) {
		VehicleModConfig config = VehicleModClient.getConfig();
		int thisPage = this.page;
		ButtonWidget button = ButtonWidget.builder(entry.label(), pressed -> {
			entry.action().run();
			if (config != null) {
				config.save();
			}
			if (this.client != null) {
				this.client.setScreen(new ConfigMenuScreen(thisPage));
			}
		}).dimensions(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.of(Text.translatable(entry.tooltipKey())))
				.build();
		this.addDrawableChild(button);
		return y + ROW_SPACING;
	}

	private static Text tudursvehiclemod$toggleLabel(String labelKey, boolean enabled) {
		return Text.translatable(labelKey, Text.translatable(enabled ? "options.on" : "options.off"));
	}

	/** Cycles through the three modes in a fixed order, so repeated presses walk them predictably. */
	private static String tudursvehiclemod$nextTranslucencyMode(String current) {
		String mode = current == null ? "" : current.trim().toLowerCase(java.util.Locale.ROOT);
		return switch (mode) {
			case "dither_shader" -> "dither_texture";
			case "dither_texture" -> "translucent";
			default -> "dither_shader";
		};
	}

	/** Label showing the mode's own translated name, so the button reads meaningfully rather than showing a raw config value. */
	private static Text tudursvehiclemod$translucencyModeLabel(VehicleModConfig config) {
		String mode = config.translucencyMode == null ? "" : config.translucencyMode.trim().toLowerCase(java.util.Locale.ROOT);
		String nameKey = switch (mode) {
			case "dither_texture" -> "gui.tudursvehiclemod.config.translucency_mode.dither_texture";
			case "translucent" -> "gui.tudursvehiclemod.config.translucency_mode.translucent";
			default -> "gui.tudursvehiclemod.config.translucency_mode.dither_shader";
		};
		return Text.translatable("gui.tudursvehiclemod.config.translucency_mode", Text.translatable(nameKey));
	}

	/** Cycles beam -> dynamic_light -> blades -> bands -> beam, matching tudursvehiclemod$nextTranslucencyMode()'s own fixed-order cycling pattern. */
	private static String tudursvehiclemod$nextSearchLightMode(String current) {
		String mode = current == null ? "" : current.trim().toLowerCase(java.util.Locale.ROOT);
		return switch (mode) {
			case "beam" -> "dynamic_light";
			case "dynamic_light" -> "blades";
			case "blades" -> "bands";
			case "bands" -> "dashes";
			default -> "beam";
		};
	}

	/** Label showing the search light mode's own translated name - see tudursvehiclemod$translucencyModeLabel()'s own doc for the same pattern applied to that setting. */
	private static Text tudursvehiclemod$searchLightModeLabel(VehicleModConfig config) {
		String mode = config.searchLightMode == null ? "" : config.searchLightMode.trim().toLowerCase(java.util.Locale.ROOT);
		String nameKey = switch (mode) {
			case "dynamic_light" -> "gui.tudursvehiclemod.config.search_light_mode.dynamic_light";
			case "blades" -> "gui.tudursvehiclemod.config.search_light_mode.blades";
			case "bands" -> "gui.tudursvehiclemod.config.search_light_mode.bands";
			case "dashes" -> "gui.tudursvehiclemod.config.search_light_mode.dashes";
			default -> "gui.tudursvehiclemod.config.search_light_mode.beam";
		};
		return Text.translatable("gui.tudursvehiclemod.config.search_light_mode", Text.translatable(nameKey));
	}

	/** Shows the factor as a plain number, so values below 1 (which shrink the texture) read naturally rather than as "0.25x" rounded to zero. */
	/** No ceiling is imposed on searchLightRingCountMultiplier/searchLightBladeCountMultiplier themselves - see either entry's own doc for why this button still wraps despite that (a practical, bounded set of common values for quick browsing; the field itself stays reachable at any value via the config file). 0.5 through 3.0 covers "half" through "triple" without an impractically long cycle to click through. */
	private static double tudursvehiclemod$nextCountMultiplier(double current) {
		if (current < 1.0) {
			return Math.round((current + 0.5) * 10.0) / 10.0;
		}
		double next = current + 1.0;
		return next > 3.0 ? 0.5 : next;
	}

	private static Text tudursvehiclemod$upscaleLabel(VehicleModConfig config) {
		double value = config.translucencyDitherUpscale;
		String shown = value >= 1.0
				? String.valueOf((int) Math.round(value))
				: String.valueOf(value);
		return Text.translatable("gui.tudursvehiclemod.config.dither_upscale", shown);
	}
}
