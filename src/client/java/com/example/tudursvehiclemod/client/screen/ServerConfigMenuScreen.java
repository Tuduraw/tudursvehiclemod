package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.VehicleModServerConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Edits VehicleModServerConfig from inside the game, the same way ConfigMenuScreen already does for the separate, purely client-side VehicleModConfig - mirrors that class's own layout/pagination/tooltip pattern closely, for a consistent feel between the two.
 *
 * <p>Deliberately only reachable/functional while {@link MinecraftClient#getServer()} is non-null - true exactly when this client is hosting the world being played (singleplayer, or a LAN game opened to others), since VehicleModServerConfig is loaded and lives entirely inside that integrated server's own process/classloader, which is genuinely the SAME process as this client in that specific case (unlike a dedicated server, a genuinely separate process this client has no direct access to at all, reachable only over the network - which this project has no existing config-sync payload for at all). DebugMenuScreen's own button that opens this screen is disabled with an explanatory tooltip whenever that condition doesn't hold, rather than this screen silently doing nothing (or worse, editing a config instance nobody's own dedicated server will ever actually read) if somehow reached anyway. */
public class ServerConfigMenuScreen extends Screen {

	private static final int BUTTON_WIDTH = 260;
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW_SPACING = 24;
	/** Matches ConfigMenuScreen's own per-page count, for a consistent feel between the two screens. */
	private static final int ENTRIES_PER_PAGE = 7;

	/** One row's own label, tooltip, and what pressing it does - same pattern as ConfigMenuScreen's own identical record. */
	private record ConfigEntry(Text label, String tooltipKey, Runnable action) {}

	private final int page;

	public ServerConfigMenuScreen() {
		this(0);
	}

	public ServerConfigMenuScreen(int page) {
		super(Text.translatable("gui.tudursvehiclemod.server_config_menu"));
		this.page = page;
	}

	@Override
	protected void init() {
		VehicleModServerConfig config = VehicleModServerConfig.get();
		List<ConfigEntry> entries = tudursvehiclemod$buildEntries(config);
		int pageCount = Math.max(1, (entries.size() + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE);
		int currentPage = Math.max(0, Math.min(this.page, pageCount - 1));

		int centerX = this.width / 2;
		int y = Math.max(32, this.height / 2 - (ROW_SPACING * (ENTRIES_PER_PAGE / 2 + 1)));

		// ---- Pagination controls - same layout as ConfigMenuScreen's own identical block. ----
		if (pageCount > 1) {
			int navWidth = 60;
			int gap = 4;

			this.addDrawableChild(ButtonWidget.builder(
					Text.literal("<"),
					button -> {
						if (this.client != null) {
							this.client.setScreen(new ServerConfigMenuScreen(currentPage > 0 ? currentPage - 1 : pageCount - 1));
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
							this.client.setScreen(new ServerConfigMenuScreen(currentPage < pageCount - 1 ? currentPage + 1 : 0));
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

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		// A config change here takes effect the instant the underlying VehicleModServerConfig instance is mutated (every reader calls VehicleModServerConfig#get() fresh, never caching it locally beyond a single tick's own use), so a standing reminder that no restart is needed - unlike VehicleModConfig's own translucencyMode/experimentalTriangleRendering, which explicitly DO need one (see ConfigMenuScreen's own doc).
		context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("gui.tudursvehiclemod.server_config_menu.no_restart_notice"),
				this.width / 2, 18, 0xFFAAAAAA);
	}

	/** Every server-side setting this menu edits, in a fixed order - see ConfigMenuScreen's own tudursvehiclemod$buildEntries() for why this is a flat list rather than laid out per-page directly (identical reasoning applies here). */
	private List<ConfigEntry> tudursvehiclemod$buildEntries(VehicleModServerConfig config) {
		List<ConfigEntry> entries = new ArrayList<>();

		entries.add(new ConfigEntry(
				tudursvehiclemod$toggleLabel("gui.tudursvehiclemod.server_config.parallel_hit_detection", config.parallelHitDetectionAcrossVehicles),
				"gui.tudursvehiclemod.server_config.parallel_hit_detection.tooltip",
				() -> config.parallelHitDetectionAcrossVehicles = !config.parallelHitDetectionAcrossVehicles));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.server_config.parallel_hit_detection_minimum", config.parallelHitDetectionMinimumVehicles),
				"gui.tudursvehiclemod.server_config.parallel_hit_detection_minimum.tooltip",
				// 1 through 20 in steps of 1 - the field's own doc frames this as "how many vehicles before parallel is worth it", so the practically relevant range is small and fine-grained throughout, not coarser at the high end the way some other settings here are.
				() -> config.parallelHitDetectionMinimumVehicles = config.parallelHitDetectionMinimumVehicles >= 20
						? 1
						: config.parallelHitDetectionMinimumVehicles + 1));

		entries.add(new ConfigEntry(
				tudursvehiclemod$toggleLabel("gui.tudursvehiclemod.server_config.destroyed_despawn_enabled", config.destroyedVehicleDespawnEnabled),
				"gui.tudursvehiclemod.server_config.destroyed_despawn_enabled.tooltip",
				() -> config.destroyedVehicleDespawnEnabled = !config.destroyedVehicleDespawnEnabled));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.server_config.destroyed_despawn_seconds", config.destroyedVehicleDespawnSeconds),
				"gui.tudursvehiclemod.server_config.destroyed_despawn_seconds.tooltip",
				// 30 seconds through 1800 (30 minutes) - fine steps (30s) up to 5 minutes (the default), coarser (5 minute) steps beyond it, since the exact value matters far less once it's already "quite a while".
				() -> {
					int next = config.destroyedVehicleDespawnSeconds < 300
							? config.destroyedVehicleDespawnSeconds + 30
							: config.destroyedVehicleDespawnSeconds + 300;
					config.destroyedVehicleDespawnSeconds = next > 1800 ? 30 : next;
				}));

		entries.add(new ConfigEntry(
				Text.translatable("gui.tudursvehiclemod.server_config.carrier_runway_width",
						String.format(java.util.Locale.ROOT, "%.1f", config.carrierRunwayExpectedWidth)),
				"gui.tudursvehiclemod.server_config.carrier_runway_width.tooltip",
				// 10 through 60 in steps of 5 - comfortably spans a small runway to a very large one, without an impractically long cycle to reach either end.
				() -> {
					double next = config.carrierRunwayExpectedWidth + 5.0;
					config.carrierRunwayExpectedWidth = next > 60.0 ? 10.0 : next;
				}));

		entries.add(new ConfigEntry(
				tudursvehiclemod$projectileChunkLimitLabel(config),
				"gui.tudursvehiclemod.server_config.projectile_chunk_limit.tooltip",
				// 0 (disabled) then 50 through 1000 in steps of 50 - 0 is called out as its own distinct, meaningful step (not merely "the bottom of a numeric range") since it disables the whole feature outright, per that field's own doc.
				() -> {
					int current = config.projectileForcedChunkLimit;
					if (current <= 0) {
						config.projectileForcedChunkLimit = 50;
					} else if (current >= 1000) {
						config.projectileForcedChunkLimit = 0;
					} else {
						config.projectileForcedChunkLimit = current + 50;
					}
				}));

		return entries;
	}

	/** Adds one row and returns the next row's own Y - identical pattern to ConfigMenuScreen's own tudursvehiclemod$addRow(), except this saves VehicleModServerConfig instead. */
	private int tudursvehiclemod$addRow(int centerX, int y, ConfigEntry entry) {
		int thisPage = this.page;
		ButtonWidget button = ButtonWidget.builder(entry.label(), pressed -> {
			entry.action().run();
			VehicleModServerConfig.get().save();
			if (this.client != null) {
				this.client.setScreen(new ServerConfigMenuScreen(thisPage));
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

	/** Shows "Disabled" specifically at 0, rather than just the raw number - per that field's own doc, 0 is a distinct, meaningful state (the whole feature turned off), not merely the low end of a numeric range. */
	private static Text tudursvehiclemod$projectileChunkLimitLabel(VehicleModServerConfig config) {
		if (config.projectileForcedChunkLimit <= 0) {
			return Text.translatable("gui.tudursvehiclemod.server_config.projectile_chunk_limit.disabled");
		}
		return Text.translatable("gui.tudursvehiclemod.server_config.projectile_chunk_limit", config.projectileForcedChunkLimit);
	}
}
