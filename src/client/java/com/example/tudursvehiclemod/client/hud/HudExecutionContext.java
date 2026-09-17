package com.example.tudursvehiclemod.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Carries everything a HudCommand needs while a script is executing: resolved variables for this frame, the current draw target, and the "current color" state.. */
public class HudExecutionContext {

	public final DrawContext drawContext;
	public final MinecraftClient client;
	public final Map<String, Double> variables;
	/** String-valued variables (e.g. WPN_NAME). */
	public final Map<String, String> stringVariables;
	public final int centerX;
	public final int centerY;
	public int currentColor = 0xFFFFFFFF;

	private final Map<String, HudScript> allScripts;
	/** Scripts currently being executed higher up the Call chain. */
	private final Set<String> callStack = new HashSet<>();

	public HudExecutionContext(DrawContext drawContext, MinecraftClient client, Map<String, Double> variables,
			Map<String, String> stringVariables, int centerX, int centerY, Map<String, HudScript> allScripts) {
		this.drawContext = drawContext;
		this.client = client;
		this.variables = variables;
		this.stringVariables = stringVariables;
		this.centerX = centerX;
		this.centerY = centerY;
		this.allScripts = allScripts;
	}

	/** Executes the named script's top-level command list in place. */
	public void run(String scriptName) {
		callScript(scriptName);
	}

	void callScript(String scriptName) {
		String key = scriptName.toLowerCase(java.util.Locale.ROOT);
		if (!callStack.add(key)) {
			return; // already executing higher up this Call chain - skip silently
		}
		try {
			HudScript script = allScripts.get(key);
			if (script == null) {
				return; // referenced script not found/loaded - skip silently, don't crash the HUD
			}
			for (HudCommand command : script.commands()) {
				command.execute(this);
			}
		} catch (HudCommand.ExitSignal exit) {
			// Exit unwinds only to here.
		} finally {
			callStack.remove(key);
		}
	}
}
