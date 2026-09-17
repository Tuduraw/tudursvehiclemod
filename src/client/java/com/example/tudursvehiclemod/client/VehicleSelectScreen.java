package com.example.tudursvehiclemod.client;

import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.VehicleRegistry;
import com.example.tudursvehiclemod.item.VehicleConverterTarget;
import com.example.tudursvehiclemod.network.SelectVehiclePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Opened by TieredVehicleSpawnerItem (via VehicleSelectScreenOpener) when the pilot right-clicks one of the 25 tiered spawner items.
 *
 * Sorted by display name by default (rather than tier-only, which used to
 * leave same-tier vehicles in whatever order VehicleRegistry's own backing
 * map happened to iterate in - not a stable order across restarts, per a
 * direct report that this made vehicles hard to find) - see SortMode's own
 * doc for the other available orderings, and the search box for filtering
 * by name directly. */
public class VehicleSelectScreen extends Screen {

	private static final int ROWS_PER_PAGE = 7;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_WIDTH = 220;
	private static final int LIST_TOP_OFFSET = 56;

	private enum SortMode {
		NAME("screen.tudursvehiclemod.sort_name"),
		TIER("screen.tudursvehiclemod.sort_tier");

		private final String translationKey;

		SortMode(String translationKey) {
			this.translationKey = translationKey;
		}

		Text label() {
			return Text.translatable(translationKey);
		}

		SortMode next() {
			return this == NAME ? TIER : NAME;
		}
	}

	private final VehicleConverterTarget category;
	private final int tier;
	private final List<Map.Entry<Identifier, VehicleDefinition>> allInCategory = new ArrayList<>();
	private final List<Map.Entry<Identifier, VehicleDefinition>> matches = new ArrayList<>();
	private SortMode sortMode = SortMode.NAME;
	private TextFieldWidget searchBox;
	private int page;

	public VehicleSelectScreen(VehicleConverterTarget category, int tier) {
		super(Text.translatable(category.translationKey()).append(Text.literal(" (" + tier + ")")));
		this.category = category;
		this.tier = tier;
	}

	@Override
	protected void init() {
		allInCategory.clear();
		for (Map.Entry<Identifier, VehicleDefinition> entry : VehicleRegistry.getAll().entrySet()) {
			VehicleDefinition def = entry.getValue();
			if (def.entityType().equals(category.entityTypeId()) && def.tier() <= tier) {
				allInCategory.add(entry);
			}
		}

		String previousSearch = this.searchBox != null ? this.searchBox.getText() : "";
		this.searchBox = new TextFieldWidget(this.textRenderer, this.width / 2 - BUTTON_WIDTH / 2, 40, BUTTON_WIDTH, 20,
				Text.translatable("screen.tudursvehiclemod.search"));
		this.searchBox.setPlaceholder(Text.translatable("screen.tudursvehiclemod.search"));
		this.searchBox.setText(previousSearch);
		this.searchBox.setChangedListener(text -> {
			page = 0;
			applyFilterAndSort();
		});
		this.addDrawableChild(this.searchBox);
		this.setInitialFocus(this.searchBox);

		applyFilterAndSort();
	}

	private void applyFilterAndSort() {
		String query = this.searchBox.getText().strip().toLowerCase(Locale.ROOT);
		matches.clear();
		for (Map.Entry<Identifier, VehicleDefinition> entry : allInCategory) {
			String name = VehicleDisplayNames.resolve(entry.getKey(), entry.getValue());
			if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)) {
				matches.add(entry);
			}
		}

		Comparator<Map.Entry<Identifier, VehicleDefinition>> comparator = switch (sortMode) {
			case NAME -> Comparator.comparing(e -> VehicleDisplayNames.resolve(e.getKey(), e.getValue()), String.CASE_INSENSITIVE_ORDER);
			case TIER -> Comparator.<Map.Entry<Identifier, VehicleDefinition>>comparingInt(e -> e.getValue().tier())
					.thenComparing(e -> VehicleDisplayNames.resolve(e.getKey(), e.getValue()), String.CASE_INSENSITIVE_ORDER);
		};
		matches.sort(comparator);

		rebuildButtons();
	}

	private void rebuildButtons() {
		this.clearChildren();
		this.addDrawableChild(this.searchBox);

		int totalPages = Math.max(1, (matches.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
		page = Math.max(0, Math.min(page, totalPages - 1));

		int x = this.width / 2 - BUTTON_WIDTH / 2;
		int startY = LIST_TOP_OFFSET;

		// Sort toggle button, to the right of the search box's own row.
		this.addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.tudursvehiclemod.sort_by", sortMode.label()),
				button -> {
					sortMode = sortMode.next();
					applyFilterAndSort();
				}
		).dimensions(x, 16, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		int from = page * ROWS_PER_PAGE;
		int to = Math.min(matches.size(), from + ROWS_PER_PAGE);

		for (int i = from; i < to; i++) {
			Map.Entry<Identifier, VehicleDefinition> entry = matches.get(i);
			Identifier vehicleId = entry.getKey();
			VehicleDefinition def = entry.getValue();
			int y = startY + (i - from) * BUTTON_HEIGHT;

			String label = VehicleDisplayNames.resolve(vehicleId, def) + " (T" + def.tier() + ")";
			this.addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
				ClientPlayNetworking.send(new SelectVehiclePayload(vehicleId, true));
				this.close();
			}).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}

		if (totalPages > 1) {
			int navY = startY + ROWS_PER_PAGE * BUTTON_HEIGHT + 8;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> {
				page--;
				rebuildButtons();
			}).dimensions(x, navY, 40, BUTTON_HEIGHT).build());

			this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> {
				page++;
				rebuildButtons();
			}).dimensions(x + BUTTON_WIDTH - 40, navY, 40, BUTTON_HEIGHT).build());
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 4, 0xFFFFFF);

		if (matches.isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("screen.tudursvehiclemod.no_vehicles_available"),
					this.width / 2, this.height / 2, 0xFFAAAA);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
