package com.example.tudursvehiclemod.client.hud;

/** Thrown while parsing a HUD script file or an expression within one. */
public class HudScriptException extends RuntimeException {
	public HudScriptException(String message) {
		super(message);
	}

	public HudScriptException(String message, Throwable cause) {
		super(message, cause);
	}
}
