package com.example.tudursvehiclemod.client.hud;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A single parsed HUD script (one.txt file's worth of commands). */
public record HudScript(List<HudCommand> commands, boolean referencesMortarDistance) {

	/** Parses one script file's full text into a command list. Malformed individual lines are logged and skipped rather than failing the whole file. */
	public static HudScript parse(Identifier sourceForLogging, String text) {
		String[] rawLines = text.split("\r\n|\r|\n");
		List<HudCommand> commands = new ArrayList<>();
		int i = 0;
		while (i < rawLines.length) {
			String line = stripComment(rawLines[i]).trim();
			i++;
			if (line.isEmpty()) {
				continue;
			}
			try {
				if (equalsIgnoreCase(directiveOf(line), "If")) {
					List<String> body = new ArrayList<>();
					while (i < rawLines.length) {
						String bodyLine = stripComment(rawLines[i]).trim();
						i++;
						if (equalsIgnoreCase(directiveOf(bodyLine), "EndIf")) {
							break;
						}
						if (!bodyLine.isEmpty()) {
							body.add(bodyLine);
						}
					}
					HudExpr condition = HudExpr.parse(valueOf(line));
					List<HudCommand> bodyCommands = new ArrayList<>();
					for (String bodyLine : body) {
						parseLine(sourceForLogging, bodyLine, bodyCommands);
					}
					commands.add(new HudCommand.IfCommand(condition, bodyCommands));
				} else {
					parseLine(sourceForLogging, line, commands);
				}
			} catch (RuntimeException e) {
				// Catches any RuntimeException, not just HudScriptException.
				System.err.println("[tudursvehiclemod] HUD script " + sourceForLogging + ": skipping bad line \""
						+ line + "\": " + e.getMessage());
			}
		}
		boolean referencesMortarDistance = text.toLowerCase(Locale.ROOT).contains("mortar_distance");
		return new HudScript(commands, referencesMortarDistance);
	}

	private static void parseLine(Identifier source, String line, List<HudCommand> out) {
		String directive = directiveOf(line);
		if (directive.isEmpty()) {
			return;
		}
		if (equalsIgnoreCase(directive, "Exit")) {
			out.add(new HudCommand.ExitCommand());
			return;
		}
		if (equalsIgnoreCase(directive, "Call")) {
			out.add(new HudCommand.CallCommand(valueOf(line).trim().toLowerCase(Locale.ROOT)));
			return;
		}
		if (equalsIgnoreCase(directive, "Color")) {
			out.add(new HudCommand.ColorCommand(parseExprList(valueOf(line))));
			return;
		}
		if (equalsIgnoreCase(directive, "DrawString") || equalsIgnoreCase(directive, "DrawCenteredString")) {
			out.add(parseDrawString(valueOf(line), equalsIgnoreCase(directive, "DrawCenteredString")));
			return;
		}
		if (equalsIgnoreCase(directive, "DrawTexture")) {
			out.add(parseDrawTexture(valueOf(line)));
			return;
		}
		if (equalsIgnoreCase(directive, "DrawRect")) {
			List<HudExpr> args = parseExprList(valueOf(line));
			out.add(new HudCommand.DrawRectCommand(args.get(0), args.get(1), args.get(2), args.get(3)));
			return;
		}
		if (equalsIgnoreCase(directive, "DrawLine")) {
			out.add(new HudCommand.DrawLineCommand(parseExprList(valueOf(line)), false, 0, 0));
			return;
		}
		if (equalsIgnoreCase(directive, "DrawLineStipple")) {
			List<HudExpr> all = parseExprList(valueOf(line));
			// First two params are the stipple pattern/factor.
			out.add(new HudCommand.DrawLineCommand(all.subList(Math.min(2, all.size()), all.size()), true, 0, 0));
			return;
		}
		if (equalsIgnoreCase(directive, "DrawGraduationYaw")) {
			List<HudExpr> args = parseExprList(valueOf(line));
			out.add(new HudCommand.DrawGraduationYawCommand(args.get(0), args.get(1), args.get(2)));
			return;
		}
		if (equalsIgnoreCase(directive, "DrawGraduationPitch1")) {
			List<HudExpr> args = parseExprList(valueOf(line));
			out.add(new HudCommand.DrawGraduationPitch1Command(args.get(0), args.get(1), args.get(2)));
			return;
		}
		if (equalsIgnoreCase(directive, "DrawGraduationPitch2")) {
			List<HudExpr> args = parseExprList(valueOf(line));
			out.add(new HudCommand.DrawGraduationPitch2Command(args.get(0), args.get(1), args.get(2), args.get(3)));
			return;
		}
		if (equalsIgnoreCase(directive, "DrawCameraRot")) {
			List<HudExpr> args = parseExprList(valueOf(line));
			out.add(new HudCommand.DrawCameraRotCommand(args.get(0), args.get(1)));
			return;
		}
		if (equalsIgnoreCase(directive, "DrawEntityRadar") || equalsIgnoreCase(directive, "DrawEnemyRadar")) {
			out.add(new HudCommand.UnsupportedCommand(directive));
			return;
		}
		throw new HudScriptException("Unknown directive \"" + directive + "\"");
	}

	private static HudCommand parseDrawString(String value, boolean centered) {
		List<String> parts = splitTopLevelCommaRespectingQuotes(value);
		if (parts.size() < 3) {
			throw new HudScriptException("DrawString needs at least x, y, format");
		}
		HudExpr x = HudExpr.parse(parts.get(0).trim());
		HudExpr y = HudExpr.parse(parts.get(1).trim());
		String format = unquote(parts.get(2).trim());
		List<HudExpr> data = new ArrayList<>();
		for (int i = 3; i < parts.size(); i++) {
			data.add(HudExpr.parse(parts.get(i).trim()));
		}
		return new HudCommand.DrawStringCommand(x, y, format, data, centered);
	}

	private static HudCommand parseDrawTexture(String value) {
		List<String> parts = splitTopLevelCommaRespectingQuotes(value);
		if (parts.size() < 9) {
			throw new HudScriptException("DrawTexture needs texture, x, y, w, h, u, v, uw, vh");
		}
		String texture = unquote(parts.get(0).trim()).toLowerCase(Locale.ROOT);
		HudExpr x = HudExpr.parse(parts.get(1).trim());
		HudExpr y = HudExpr.parse(parts.get(2).trim());
		HudExpr w = HudExpr.parse(parts.get(3).trim());
		HudExpr h = HudExpr.parse(parts.get(4).trim());
		HudExpr u = HudExpr.parse(parts.get(5).trim());
		HudExpr v = HudExpr.parse(parts.get(6).trim());
		HudExpr uw = HudExpr.parse(parts.get(7).trim());
		HudExpr vh = HudExpr.parse(parts.get(8).trim());
		HudExpr rotation = parts.size() > 9 ? HudExpr.parse(parts.get(9).trim()) : null;
		return new HudCommand.DrawTextureCommand(texture, x, y, w, h, u, v, uw, vh, rotation);
	}

	private static List<HudExpr> parseExprList(String value) {
		List<HudExpr> result = new ArrayList<>();
		for (String part : splitTopLevelCommaRespectingQuotes(value)) {
			if (!part.trim().isEmpty()) {
				result.add(HudExpr.parse(part.trim()));
			}
		}
		return result;
	}

	/** Returns the directive name (before the first '=', or the whole trimmed line for directives with no parameters like "Exit"/"EndIf"). */
	private static String directiveOf(String line) {
		int eq = line.indexOf('=');
		return (eq >= 0 ? line.substring(0, eq) : line).trim();
	}

	/** Returns everything after the first '=' on the line. */
	private static String valueOf(String line) {
		int eq = line.indexOf('=');
		return eq >= 0 ? line.substring(eq + 1) : "";
	}

	private static boolean equalsIgnoreCase(String a, String b) {
		return a.equalsIgnoreCase(b);
	}

	private static String unquote(String s) {
		if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
			return s.substring(1, s.length() - 1);
		}
		return s;
	}

	/** Strips a ';' comment, but only outside of a double-quoted string (a format string could plausibly contain one, however unlikely). */
	private static String stripComment(String line) {
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				inQuotes = !inQuotes;
			} else if (c == ';' && !inQuotes) {
				return line.substring(0, i);
			}
		}
		return line;
	}

	/** Splits on top-level commas only. */
	private static List<String> splitTopLevelCommaRespectingQuotes(String value) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '"') {
				inQuotes = !inQuotes;
				current.append(c);
			} else if (c == ',' && !inQuotes) {
				parts.add(current.toString());
				current.setLength(0);
			} else {
				current.append(c);
			}
		}
		if (!current.isEmpty() || !parts.isEmpty()) {
			parts.add(current.toString());
		}
		// Drop a trailing empty/whitespace-only part.
		if (!parts.isEmpty() && parts.get(parts.size() - 1).trim().isEmpty()) {
			parts.remove(parts.size() - 1);
		}
		return parts;
	}
}
