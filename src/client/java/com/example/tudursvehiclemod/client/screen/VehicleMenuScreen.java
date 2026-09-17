package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.asset.WeaponDefinition;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.screen.VehicleMenuScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/** No custom GUI background PNG asset of its own - see FuelRefinerScreen's
 * own doc for why slot backgrounds are drawn as simple beveled rectangles
 * (SlotGrid) rather than cropped from a vanilla texture.
 *
 * Resuppliable weapons are shown one at a time behind a row of tab buttons
 * (rather than a single stacked column of "Resupply <weapon>" buttons) - per
 * a direct request, since a vehicle with many weapons made the old layout
 * hard to scan. Each tab's own detail area shows the weapon's own name,
 * followed by the actual item icons + required counts (iron ingot/gunpowder/
 * redstone) it costs to resupply, then the resupply button itself. */
public class VehicleMenuScreen extends HandledScreen<VehicleMenuScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int BORDER_COLOR = 0xFF8B8B8B;
	private static final int TAB_WIDTH = 24;
	private static final int TAB_HEIGHT = 20;
	/** The side panels (both this and the mirrored repair one) rendered wide enough to run off-screen at standard GUI scale - narrowed from an earlier, wider value, and tudursvehiclemod$repairAreaX()/tudursvehiclemod$resupplyAreaX() additionally clamp the actual on-screen position so this can never be pushed past either edge of the screen even at unusually tight window sizes. */
	private static final int DETAIL_WIDTH = 90;

	/** Must match AbstractVehicleEntity's own REPAIR_TIER_IRON_COST exactly - iron ingots consumed per repair tier. */
	private static final int[] REPAIR_TIER_IRON_COST = {1, 5, 10};
	/** Must match AbstractVehicleEntity's own REPAIR_TIER_HEAL_PERCENT exactly - percentage of max health restored per tier. */
	private static final float[] REPAIR_TIER_HEAL_PERCENT = {5f, 25f, 50f};

	/** Index into resuppliableWeaponIndices (not a raw weapon index) of the currently shown tab. */
	private int selectedTab;
	/** Index into REPAIR_TIER_IRON_COST/REPAIR_TIER_HEAL_PERCENT of the currently shown repair tier. */
	private int selectedRepairTier;
	private final List<Integer> resuppliableWeaponIndices = new ArrayList<>();
	private final int cargoSize;

	/** AbstractVehicleEntity's own cargoPage field is server-only and never synced to the client at all (this design deliberately avoids reopening the whole screen on a page change - see VehicleMenuScreenHandler's own PagedCargoView doc - so there was never a natural point where the client would learn the server's own current page either). Tracked here instead, client-side, updated optimistically the instant a page button is clicked (rather than waiting for a round-trip confirmation from the server) - correct as long as the click itself succeeds, which it always does here since these buttons are only ever enabled within their own already-known valid range. */
	private int cargoPage;

	public VehicleMenuScreen(VehicleMenuScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		AbstractVehicleEntity vehicle = handler.tudursvehiclemod$getVehicle();
		this.cargoSize = vehicle != null ? vehicle.getDefinition().inventorySize() : 0;
		int cargoRows = Math.min(VehicleMenuScreenHandler.PAGE_ROWS, (cargoSize + 8) / 9);
		this.backgroundWidth = 200;
		this.backgroundHeight = 50 + cargoRows * 18 + 14 + 3 * 18 + 14 + 18;
		this.playerInventoryTitleY = this.backgroundHeight - 94;

		if (vehicle != null) {
			List<WeaponDefinition> weapons = vehicle.getDefinition().weapons();
			int viewerSeatIndex = this.client != null && this.client.player != null
					? vehicle.tudursvehiclemod$getAssignedSeatIndex(this.client.player) : 0;
			for (int i = 0; i < weapons.size(); i++) {
				if (!weapons.get(i).isResuppliable()) {
					continue;
				}
				// A non-pilot seat only sees/resupplies the weapon(s) ITS OWN seat can actually fire - the pilot (seat 0) keeps seeing every resuppliable weapon regardless of seat, matching the existing pilotUsable design elsewhere (the pilot may already fire an unoccupied gunner seat's own weapon).
				if (viewerSeatIndex == 0 || weapons.get(i).seatIndex() == viewerSeatIndex) {
					this.resuppliableWeaponIndices.add(i);
				}
			}
		}
	}

	/** Ideally sits DETAIL_WIDTH+4 to the left of the window, but never further left than a small screen-edge margin - see DETAIL_WIDTH's own doc for why this clamping exists. */
	private int tudursvehiclemod$repairAreaX() {
		int ideal = this.x - 4 - DETAIL_WIDTH;
		return Math.max(2, ideal);
	}

	/** Ideally sits 4 to the right of the window, but never far enough right to run past the opposite screen edge - see DETAIL_WIDTH's own doc for why this clamping exists. */
	private int tudursvehiclemod$resupplyAreaX() {
		int ideal = this.x + this.backgroundWidth + 4;
		return Math.min(this.width - DETAIL_WIDTH - 2, ideal);
	}

	@Override
	protected void init() {
		super.init();
		tudursvehiclemod$rebuildAllDetailAreas();
	}

	/** clearChildren() wipes EVERY dynamically-added widget on this screen, so both the weapon-resupply area (right of the window) and the repair area (left of the window - see tudursvehiclemod$rebuildRepairArea()'s own doc) have to be rebuilt together any time either one's own tab selection changes, or the other one's buttons would simply vanish. the debug menu button (see DebugMenuScreen's own doc) is ALSO re-added here every time, not just once from init() - clearChildren() wiped it out too on every subsequent call (e.g. switching the resupply tab's own selected weapon), and since nothing else ever re-added it, it permanently disappeared after the very first such switch. */
	private void tudursvehiclemod$rebuildAllDetailAreas() {
		this.clearChildren();
		tudursvehiclemod$rebuildDetailArea();
		tudursvehiclemod$rebuildRepairArea();

		// Debug menu access - see DebugMenuScreen's own doc.
		this.addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.tudursvehiclemod.debug_menu"),
				button -> {
					if (this.client != null) {
						this.client.setScreen(new com.example.tudursvehiclemod.client.screen.DebugMenuScreen());
					}
				}
		).dimensions(this.x + this.backgroundWidth - 46, this.y - 22, 46, 20).build());
	}

	/** 3 columns x 2 rows = 6 weapons visible at once - fits comfortably within DETAIL_WIDTH (3 * TAB_WIDTH = 72, well under 90). */
	private static final int GRID_COLS = 3;
	private static final int GRID_ROWS = 2;
	private static final int WEAPONS_PER_PAGE = GRID_COLS * GRID_ROWS;
	/** Small vertical gap between the 2 tab rows. */
	private static final int TAB_ROW_GAP = 2;
	/** Total height (px) the 2-row tab grid occupies, INCLUDING the page-nav row directly below it - drawWeaponDetail()'s own nameY uses this same constant so the weapon name/costs always start right after, regardless of how many rows/pages exist. */
	private static final int TAB_GRID_TOTAL_HEIGHT = GRID_ROWS * TAB_HEIGHT + (GRID_ROWS - 1) * TAB_ROW_GAP + TAB_ROW_GAP + TAB_HEIGHT;
	/** Current page (0-indexed) into resuppliableWeaponIndices, WEAPONS_PER_PAGE at a time - completely independent of which tab is selected (see this method's own doc for why that matters). */
	private int weaponPage;

	/** A vehicle with many resuppliable weapons could have its own tab row overflow the fixed DETAIL_WIDTH panel entirely (some tabs simply unreachable/off-screen), and that only 2 tabs fit visibly at once: shows up to WEAPONS_PER_PAGE (6) tabs at a time, arranged GRID_COLS x GRID_ROWS, with dedicated prev/next PAGE buttons directly below - completely independent of this.selectedTab, so switching pages never requires the selection to be at any particular tab first (an earlier version tied the visible window to keeping the selection in view, which fought against the page buttons unless the selection already happened to be at the edge). */
	private void tudursvehiclemod$rebuildDetailArea() {
		if (resuppliableWeaponIndices.isEmpty()) {
			return;
		}
		this.selectedTab = MathHelper.clamp(this.selectedTab, 0, resuppliableWeaponIndices.size() - 1);

		int areaX = tudursvehiclemod$resupplyAreaX();
		int tabY = this.y + 8;

		int totalPages = (resuppliableWeaponIndices.size() + WEAPONS_PER_PAGE - 1) / WEAPONS_PER_PAGE;
		this.weaponPage = MathHelper.clamp(this.weaponPage, 0, Math.max(0, totalPages - 1));
		int pageStart = this.weaponPage * WEAPONS_PER_PAGE;
		int pageEnd = Math.min(resuppliableWeaponIndices.size(), pageStart + WEAPONS_PER_PAGE);

		for (int tabIndex = pageStart; tabIndex < pageEnd; tabIndex++) {
			int weaponIndex = resuppliableWeaponIndices.get(tabIndex);
			WeaponDefinition weapon = this.getScreenHandler().tudursvehiclemod$getVehicle()
					.getDefinition().weapons().get(weaponIndex);
			int thisTab = tabIndex;
			Text label = Text.literal(Integer.toString(tabIndex + 1));
			int slotInPage = tabIndex - pageStart;
			int col = slotInPage % GRID_COLS;
			int row = slotInPage / GRID_COLS;
			ButtonWidget tabButton = ButtonWidget.builder(label, button -> {
				this.selectedTab = thisTab;
				tudursvehiclemod$rebuildAllDetailAreas();
			}).dimensions(areaX + col * TAB_WIDTH, tabY + row * (TAB_HEIGHT + TAB_ROW_GAP), TAB_WIDTH, TAB_HEIGHT).tooltip(
					net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(weapon.weaponName()))
			).build();
			tabButton.active = tabIndex != this.selectedTab;
			this.addDrawableChild(tabButton);
		}

		if (totalPages > 1) {
			int navY = tabY + GRID_ROWS * (TAB_HEIGHT + TAB_ROW_GAP);
			int currentPageForButton = this.weaponPage;
			ButtonWidget prevPage = ButtonWidget.builder(Text.literal("<"), button -> {
				this.weaponPage = Math.max(0, currentPageForButton - 1);
				tudursvehiclemod$rebuildAllDetailAreas();
			}).dimensions(areaX, navY, TAB_WIDTH, TAB_HEIGHT).build();
			prevPage.active = currentPageForButton > 0;
			this.addDrawableChild(prevPage);
			ButtonWidget nextPage = ButtonWidget.builder(Text.literal(">"), button -> {
				this.weaponPage = Math.min(totalPages - 1, currentPageForButton + 1);
				tudursvehiclemod$rebuildAllDetailAreas();
			}).dimensions(areaX + (GRID_COLS - 1) * TAB_WIDTH, navY, TAB_WIDTH, TAB_HEIGHT).build();
			nextPage.active = currentPageForButton < totalPages - 1;
			this.addDrawableChild(nextPage);
		}

		int weaponIndex = resuppliableWeaponIndices.get(this.selectedTab);
		WeaponDefinition weapon = this.getScreenHandler().tudursvehiclemod$getVehicle()
				.getDefinition().weapons().get(weaponIndex);

		int resupplyButtonY = tabY + TAB_GRID_TOTAL_HEIGHT + 56;
		this.addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.tudursvehiclemod.resupply", weapon.weaponName()),
				button -> {
					if (this.client != null && this.client.interactionManager != null) {
						this.client.interactionManager.clickButton(this.getScreenHandler().syncId, weaponIndex);
					}
				}
		).dimensions(areaX, resupplyButtonY, DETAIL_WIDTH, 20).build());
	}

	/** Repair panel - mirrors the weapon-resupply
	 * panel's own layout (tabs along the top, item cost + action button
	 * below) but on the OPPOSITE side of the window (left, outside the
	 * background, vs. resupply's right) so the two don't compete for the
	 * same space. Always shown (unlike the resupply panel, which hides
	 * itself if the vehicle has no resuppliable weapons at all) since
	 * every vehicle has health to repair. */
	private void tudursvehiclemod$rebuildRepairArea() {
		this.selectedRepairTier = MathHelper.clamp(this.selectedRepairTier, 0, REPAIR_TIER_IRON_COST.length - 1);

		int areaX = tudursvehiclemod$repairAreaX();
		int tabY = this.y + 8;

		for (int tierIndex = 0; tierIndex < REPAIR_TIER_IRON_COST.length; tierIndex++) {
			int thisTier = tierIndex;
			Text label = Text.literal(Integer.toString(tierIndex + 1));
			ButtonWidget tabButton = ButtonWidget.builder(label, button -> {
				this.selectedRepairTier = thisTier;
				tudursvehiclemod$rebuildAllDetailAreas();
			}).dimensions(areaX + tierIndex * TAB_WIDTH, tabY, TAB_WIDTH, TAB_HEIGHT).tooltip(
					net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable(
							"gui.tudursvehiclemod.repair_tier_tooltip",
							REPAIR_TIER_IRON_COST[tierIndex], Math.round(REPAIR_TIER_HEAL_PERCENT[tierIndex])))
			).build();
			tabButton.active = tierIndex != this.selectedRepairTier;
			this.addDrawableChild(tabButton);
		}

		AbstractVehicleEntity vehicle = this.getScreenHandler().tudursvehiclemod$getVehicle();
		int repairButtonY = tabY + TAB_HEIGHT + 56;
		ButtonWidget repairButton = ButtonWidget.builder(
				Text.translatable("gui.tudursvehiclemod.repair"),
				button -> {
					if (this.client != null && this.client.interactionManager != null) {
						this.client.interactionManager.clickButton(this.getScreenHandler().syncId,
								com.example.tudursvehiclemod.screen.VehicleMenuScreenHandler.REPAIR_BUTTON_ID_OFFSET + this.selectedRepairTier);
					}
				}
		).dimensions(areaX, repairButtonY, DETAIL_WIDTH, 20).build();
		repairButton.active = vehicle == null || vehicle.getHealth() < vehicle.getMaxHealth();
		this.addDrawableChild(repairButton);

		// Positioned side-by-side in the empty space directly to the right of the fuel gauge (which ends at x+50+60=x+110), at the same vertical level - rather than the cargo-grid row, which is one row shorter/taller depending on page and would shift the buttons around confusingly. Only shown at all once the vehicle's own inventorySize() actually spans more than one page - a small vehicle never sees these controls at all.
		int totalPages = (this.cargoSize + VehicleMenuScreenHandler.PAGE_SIZE - 1) / VehicleMenuScreenHandler.PAGE_SIZE;
		if (vehicle != null && totalPages > 1) {
			int pageButtonY = this.y + 24;
			ButtonWidget prevPageButton = ButtonWidget.builder(Text.literal("<"), button -> {
				if (this.client != null && this.client.interactionManager != null) {
					this.client.interactionManager.clickButton(this.getScreenHandler().syncId, VehicleMenuScreenHandler.PAGE_PREV_BUTTON_ID);
					this.cargoPage = Math.max(0, this.cargoPage - 1);
					tudursvehiclemod$rebuildAllDetailAreas();
				}
			}).dimensions(this.x + 116, pageButtonY, 16, 16).build();
			prevPageButton.active = this.cargoPage > 0;
			this.addDrawableChild(prevPageButton);

			ButtonWidget nextPageButton = ButtonWidget.builder(Text.literal(">"), button -> {
				if (this.client != null && this.client.interactionManager != null) {
					this.client.interactionManager.clickButton(this.getScreenHandler().syncId, VehicleMenuScreenHandler.PAGE_NEXT_BUTTON_ID);
					this.cargoPage = Math.min(totalPages - 1, this.cargoPage + 1);
					tudursvehiclemod$rebuildAllDetailAreas();
				}
			}).dimensions(this.x + 134, pageButtonY, 16, 16).build();
			nextPageButton.active = this.cargoPage < totalPages - 1;
			this.addDrawableChild(nextPageButton);
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;
		context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL_COLOR);
		tudursvehiclemod$fillBorder(context, x, y, this.backgroundWidth, this.backgroundHeight, BORDER_COLOR);

		AbstractVehicleEntity vehicle = this.getScreenHandler().tudursvehiclemod$getVehicle();
		// Fuel slot, and a small fuel gauge next to it - see VehicleMenuScreenHandler's own constructor for this exact coordinate.
		SlotGrid.drawSlot(context, x + 26, y + 24);
		if (vehicle != null) {
			int barX = x + 50;
			int barY = y + 28;
			int barWidth = 60;
			int barHeight = 8;
			context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF373737);
			float ratio = vehicle.tudursvehiclemod$isFuelless()
					? 1f
					: (vehicle.getMaxFuel() > 0 ? vehicle.getFuel() / vehicle.getMaxFuel() : 0f);
			int filled = Math.round(barWidth * MathHelper.clamp(ratio, 0f, 1f));
			if (filled > 0) {
				context.fill(barX, barY, barX + filled, barY + barHeight, 0xFFE0892C);
			}
		}

		// Cargo slots - matches VehicleMenuScreenHandler's own constructor exactly for the TOTAL slot count (a small vehicle shows exactly cargoSize slots, no pagination at all; a large one always creates a full PAGE_SIZE Slot objects). A partial last page (inventorySize not a clean multiple of PAGE_SIZE) still visually showed the full grid even though the extra cells couldn't actually hold anything (canInsert() rejects them) - only draws a cell's own background when it's genuinely within range for the CURRENT page (this.cargoPage, tracked client-side - see that field's own doc), leaving anything beyond the vehicle's own real inventorySize() undrawn entirely rather than looking like a normal, usable slot.
		int pageCount = cargoSize <= VehicleMenuScreenHandler.PAGE_SIZE ? cargoSize : VehicleMenuScreenHandler.PAGE_SIZE;
		int pageStart = this.cargoPage * VehicleMenuScreenHandler.PAGE_SIZE;
		for (int i = 0; i < pageCount; i++) {
			if (pageStart + i >= cargoSize) {
				continue;
			}
			int col = i % 9;
			int row = i / 9;
			SlotGrid.drawSlot(context, x + 8 + col * 18, y + 50 + row * 18);
		}

		// Player's own inventory grid - playerInvY computed the SAME way VehicleMenuScreenHandler's own constructor does (capped at PAGE_ROWS, based on the vehicle's own TOTAL cargoSize so this stays consistent across every page), so it lines up regardless of cargoSize.
		int cargoRows = Math.min(VehicleMenuScreenHandler.PAGE_ROWS, (cargoSize + 8) / 9);
		int playerInvY = y + 50 + cargoRows * 18 + 14;
		SlotGrid.drawPlayerInventoryGrid(context, x, playerInvY, playerInvY + 58);

		if (!resuppliableWeaponIndices.isEmpty()) {
			tudursvehiclemod$drawWeaponDetail(context);
		}
		tudursvehiclemod$drawRepairDetail(context);
	}

	private void tudursvehiclemod$drawWeaponDetail(DrawContext context) {
		int weaponIndex = resuppliableWeaponIndices.get(this.selectedTab);
		WeaponDefinition weapon = this.getScreenHandler().tudursvehiclemod$getVehicle()
				.getDefinition().weapons().get(weaponIndex);

		int areaX = tudursvehiclemod$resupplyAreaX();
		int nameY = this.y + 8 + TAB_GRID_TOTAL_HEIGHT + 8;
		context.drawText(this.textRenderer, Text.literal(weapon.weaponName()), areaX, nameY, 0xFFFFFF, false);

		// Item icons + required counts, directly below the weapon name -
		// only the resources this specific weapon actually costs (iron
		// ingot/gunpowder/redstone are the only three MC Heli itself allows
		// for a resupply cost - see WeaponDefinition's own resupply cost
		// accessors).
		int iconY = nameY + 12;
		int iconX = areaX;
		record ResupplyCost(ItemStack stack, int count) {
		}
		List<ResupplyCost> costs = new ArrayList<>();
		if (weapon.resupplyIronIngotCost() > 0) {
			costs.add(new ResupplyCost(new ItemStack(Items.IRON_INGOT), weapon.resupplyIronIngotCost()));
		}
		if (weapon.resupplyGunpowderCost() > 0) {
			costs.add(new ResupplyCost(new ItemStack(Items.GUNPOWDER), weapon.resupplyGunpowderCost()));
		}
		if (weapon.resupplyRedstoneCost() > 0) {
			costs.add(new ResupplyCost(new ItemStack(Items.REDSTONE), weapon.resupplyRedstoneCost()));
		}
		for (ResupplyCost cost : costs) {
			context.drawItem(cost.stack(), iconX, iconY);
			context.drawStackOverlay(this.textRenderer, cost.stack(), iconX, iconY, String.valueOf(cost.count()));
			iconX += 20;
		}
	}

	/** Repair panel's own detail area (see tudursvehiclemod$rebuildRepairArea()'s own doc) - a health bar for context, then the iron ingot icon + count for the currently selected tier, plus the percentage it restores. */
	private void tudursvehiclemod$drawRepairDetail(DrawContext context) {
		AbstractVehicleEntity vehicle = this.getScreenHandler().tudursvehiclemod$getVehicle();
		int areaX = tudursvehiclemod$repairAreaX();
		int tabY = this.y + 8;
		int nameY = tabY + TAB_HEIGHT + 8;

		context.drawText(this.textRenderer, Text.translatable("gui.tudursvehiclemod.repair"), areaX, nameY, 0xFFFFFF, false);

		int barX = areaX;
		int barY = nameY + 12;
		int barWidth = DETAIL_WIDTH;
		int barHeight = 8;
		context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF373737);
		if (vehicle != null) {
			float ratio = vehicle.getMaxHealth() > 0 ? vehicle.getHealth() / vehicle.getMaxHealth() : 0f;
			int filled = Math.round(barWidth * MathHelper.clamp(ratio, 0f, 1f));
			if (filled > 0) {
				context.fill(barX, barY, barX + filled, barY + barHeight, 0xFFCC3333);
			}
		}

		int iconY = barY + 14;
		ItemStack ironStack = new ItemStack(Items.IRON_INGOT);
		context.drawItem(ironStack, areaX, iconY);
		context.drawStackOverlay(this.textRenderer, ironStack, areaX, iconY,
				String.valueOf(REPAIR_TIER_IRON_COST[this.selectedRepairTier]));
		context.drawText(this.textRenderer,
				Text.translatable("gui.tudursvehiclemod.repair_percent", Math.round(REPAIR_TIER_HEAL_PERCENT[this.selectedRepairTier])),
				areaX + 20, iconY + 4, 0xFFFFFF, false);
	}

	/** drawBorder(int, int, int, int, int) was removed from DrawContext as of this project's own Minecraft version. */
	private static void tudursvehiclemod$fillBorder(DrawContext context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y, x + 1, y + height, color);
		context.fill(x + width - 1, y, x + width, y + height, color);
	}
}
