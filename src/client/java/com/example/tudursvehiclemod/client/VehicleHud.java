package com.example.tudursvehiclemod.client;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.client.hud.HudExecutionContext;
import com.example.tudursvehiclemod.client.hud.HudScript;
import com.example.tudursvehiclemod.client.hud.HudScriptLoader;
import com.example.tudursvehiclemod.client.hud.HudVariables;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

/** Per-vehicle HUD rendering. Two systems, tried in order: <ol> <li>A ported, MC-Heli-format HUD script (see the com.example.tudursvehiclemod.client.hud package). */
public class VehicleHud {

	private static final int BAR_WIDTH = 80;
	private static final int BAR_HEIGHT = 8;
	/** Minecraft runs at 20 ticks/second, so blocks-per-tick * this constant converts to blocks-per-hour; dividing that by 1000 (1kb = 1000 blocks, per direct spec).. */
	private static final double BLOCKS_PER_TICK_TO_KB_PER_HOUR = 72.0;

	public static void register() {
		HudElementRegistry.addLast(Identifier.of(VehicleMod.MOD_ID, "throttle_hud"), VehicleHud::render);
		HudScriptLoader.register();
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}
		if (!(com.example.tudursvehiclemod.client.VehicleModClient.tudursvehiclemod$getClientEffectiveVehicle(client.player)
				instanceof AbstractVehicleEntity vehicle)) {
			return;
		}
		// Whether this HUD should still actually
		// draw at all while in third-person view specifically - see
		// VehicleModConfig's own showHudInThirdPerson doc. First person
		// is entirely unaffected either way.
		if (!client.options.getPerspective().isFirstPerson() && !VehicleModClient.getConfig().showHudInThirdPerson) {
			return;
		}

		java.util.Optional<String> scriptName = vehicle.getDefinition().hud();
		Map<String, HudScript> scripts = HudScriptLoader.getScripts();
		boolean found = scriptName.isPresent() && scripts.containsKey(scriptName.get());

		if (found) {
			Map<String, Double> variables = HudVariables.build(client, client.player, vehicle);
			Map<String, String> stringVariables = HudVariables.buildStringVariables(client, client.player, vehicle);
			int screenWidth = client.getWindow().getScaledWidth();
			int screenHeight = client.getWindow().getScaledHeight();
			HudExecutionContext execCtx = new HudExecutionContext(context, client, variables, stringVariables,
					screenWidth / 2, screenHeight / 2, scripts);
			execCtx.run(scriptName.get());
			// If this vehicle's own HUD script (or anything it Calls, recursively) doesn't already handle DisplayMortarDistance itself, draws a default fallback line instead of showing nothing at all - an explicit script reference always takes priority (mainly so its own chosen position/styling isn't overridden).
			if (!tudursvehiclemod$scriptUsesMortarDistance(scriptName.get(), scripts, new java.util.HashSet<>())) {
				tudursvehiclemod$renderDefaultMortarDistance(context, client, variables, stringVariables, screenWidth / 2, screenHeight / 2);
			}
			return;
		}

		renderBuiltinFallback(context, client, vehicle);
		Map<String, Double> fallbackVariables = HudVariables.build(client, client.player, vehicle);
		Map<String, String> fallbackStringVariables = HudVariables.buildStringVariables(client, client.player, vehicle);
		int fallbackScreenWidth = client.getWindow().getScaledWidth();
		int fallbackScreenHeight = client.getWindow().getScaledHeight();
		tudursvehiclemod$renderDefaultMortarDistance(context, client, fallbackVariables, fallbackStringVariables,
				fallbackScreenWidth / 2, fallbackScreenHeight / 2);
	}

	/** Original, simple built-in HUD. */
	private static void renderBuiltinFallback(DrawContext context, MinecraftClient client,
			AbstractVehicleEntity vehicle) {
		float throttle = vehicle.getThrottle();

		int screenWidth = client.getWindow().getScaledWidth();
		int screenHeight = client.getWindow().getScaledHeight();

		int x = screenWidth / 2 - BAR_WIDTH / 2;
		int y = screenHeight - 55;

		// Background + center tick (0 throttle).
		context.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0x80000000);
		int mid = x + BAR_WIDTH / 2;

		int filled = Math.round(Math.abs(throttle) * (BAR_WIDTH / 2f));
		if (throttle >= 0) {
			context.fill(mid, y, mid + filled, y + BAR_HEIGHT, 0xFF55DD55);
		} else {
			context.fill(mid - filled, y, mid, y + BAR_HEIGHT, 0xFFDD5555);
		}
		context.fill(mid, y - 1, mid + 1, y + BAR_HEIGHT + 1, 0xFFFFFFFF);

		Text label = Text.translatable("hud.tudursvehiclemod.throttle", Math.round(throttle * 100));
		int labelWidth = client.textRenderer.getWidth(label);
		context.drawText(client.textRenderer, label, screenWidth / 2 - labelWidth / 2, y - 12, 0xFFFFFF, true);

		// This built-in fallback HUD (used whenever
		// a vehicle has no custom HUD script of its own assigned at all
		// - see this class's own doc for the OTHER, fully scriptable
		// MC-Heli-format HUD system, which already supports fuel/
		// low_fuel as ordinary script variables) never actually showed
		// fuel at all, only throttle - a second bar, directly below the
		// throttle one, in the exact same visual style.
		int fuelY = y + BAR_HEIGHT + 6;
		float maxFuel = Math.max(1f, vehicle.getMaxFuel());
		float fuelFraction = vehicle.tudursvehiclemod$isFuelless()
				? 1f
				: net.minecraft.util.math.MathHelper.clamp(vehicle.getFuel() / maxFuel, 0f, 1f);
		context.fill(x - 1, fuelY - 1, x + BAR_WIDTH + 1, fuelY + BAR_HEIGHT + 1, 0x80000000);
		int fuelFilled = Math.round(fuelFraction * BAR_WIDTH);
		// Per Readme_HUD.txt's own doc: low_fuel blinks (alternates
		// between "fuel present"/"fuel low" every so often) once fuel
		// actually runs low - this fallback HUD's own fuel bar simply
		// turns red outright instead of blinking (simpler, and no less
		// noticeable), rather than reimplementing that exact same
		// blink timing/threshold logic a second time completely
		// separately from HudVariables' own already-existing copy of it.
		int fuelColor = fuelFraction <= 0.2f ? 0xFFDD5555 : 0xFF55AADD;
		context.fill(x, fuelY, x + fuelFilled, fuelY + BAR_HEIGHT, fuelColor);

		Text fuelLabel = Text.translatable("hud.tudursvehiclemod.fuel", Math.round(fuelFraction * 100));
		int fuelLabelWidth = client.textRenderer.getWidth(fuelLabel);
		context.drawText(client.textRenderer, fuelLabel, screenWidth / 2 - fuelLabelWidth / 2, fuelY + BAR_HEIGHT + 2, 0xFFFFFF, true);

		// getCruiseSpeed() (airspeed), not getVelocity().length() (total physical velocity magnitude).
		double speedKbPerHour = vehicle.getCruiseSpeed() * BLOCKS_PER_TICK_TO_KB_PER_HOUR;
		Text speedLabel = Text.of(String.format("%.1f kb/h", speedKbPerHour));
		context.drawText(client.textRenderer, speedLabel, 4, 4, 0xFFFFFF, true);

		if (vehicle.isFreeLook()) {
			Text freeLook = Text.translatable("hud.tudursvehiclemod.free_look");
			int freeLookWidth = client.textRenderer.getWidth(freeLook);
			context.drawText(client.textRenderer, freeLook, screenWidth / 2 - freeLookWidth / 2,
					y + BAR_HEIGHT + 4, 0xFFFF55, true);
		}

		if (vehicle instanceof com.example.tudursvehiclemod.entity.AircraftEntity aircraft && aircraft.isManualMode()) {
			Text manualMode = Text.translatable("hud.tudursvehiclemod.manual_mode");
			int manualModeWidth = client.textRenderer.getWidth(manualMode);
			context.drawText(client.textRenderer, manualMode, screenWidth / 2 - manualModeWidth / 2,
					y + BAR_HEIGHT + 16, 0xFF5555, true);
		}
	}

	/** True if scriptName (or anything it Calls, recursively) already references mortar_distance/has_mortar_distance - HudScript's own referencesMortarDistance() already covers the whole file's own raw text (including inside If bodies), so this only needs to additionally walk Call chains into OTHER script files. Cycle-safe via visited. */
	private static boolean tudursvehiclemod$scriptUsesMortarDistance(String scriptName, Map<String, HudScript> scripts,
			java.util.Set<String> visited) {
		String key = scriptName.toLowerCase(java.util.Locale.ROOT);
		if (!visited.add(key)) {
			return false;
		}
		HudScript script = scripts.get(key);
		if (script == null) {
			return false;
		}
		if (script.referencesMortarDistance()) {
			return true;
		}
		for (com.example.tudursvehiclemod.client.hud.HudCommand command : script.commands()) {
			if (tudursvehiclemod$commandCallsScriptUsingMortarDistance(command, scripts, visited)) {
				return true;
			}
		}
		return false;
	}

	private static boolean tudursvehiclemod$commandCallsScriptUsingMortarDistance(
			com.example.tudursvehiclemod.client.hud.HudCommand command, Map<String, HudScript> scripts, java.util.Set<String> visited) {
		if (command instanceof com.example.tudursvehiclemod.client.hud.HudCommand.CallCommand call) {
			return tudursvehiclemod$scriptUsesMortarDistance(call.scriptName(), scripts, visited);
		}
		if (command instanceof com.example.tudursvehiclemod.client.hud.HudCommand.IfCommand ifCommand) {
			for (com.example.tudursvehiclemod.client.hud.HudCommand sub : ifCommand.body()) {
				if (tudursvehiclemod$commandCallsScriptUsingMortarDistance(sub, scripts, visited)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The exact default shown whenever no HUD script (or anything it Calls) already handles DisplayMortarDistance itself - "Range: ---b" while not currently computable, matching mortar_distance_str's own doc, rather than hiding the line entirely. */
	private static void tudursvehiclemod$renderDefaultMortarDistance(DrawContext context, MinecraftClient client,
			Map<String, Double> variables, Map<String, String> stringVariables, int centerX, int centerY) {
		if (variables.getOrDefault("has_mortar_distance", 0.0) != 1.0) {
			return;
		}
		String text = "Range: " + stringVariables.getOrDefault("mortar_distance_str", "---b");
		context.drawText(client.textRenderer, text, centerX + 10, centerY + 10, 0xFFFFFFFF, true);
	}
}
