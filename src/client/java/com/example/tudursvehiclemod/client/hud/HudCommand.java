package com.example.tudursvehiclemod.client.hud;

import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A single executable step of a parsed HUD script. */
public interface HudCommand {

	void execute(HudExecutionContext ctx);

	/** Thrown by ExitCommand to unwind back to the nearest Call (or the top-level render call, if not inside one). */
	class ExitSignal extends RuntimeException {
		static final ExitSignal INSTANCE = new ExitSignal();

		private ExitSignal() {
			super(null, null, false, false); // no stack trace - this is pure control flow, not an error
		}
	}

	record ExitCommand() implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			throw ExitSignal.INSTANCE;
		}
	}

	record CallCommand(String scriptName) implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			ctx.callScript(scriptName);
		}
	}

	record IfCommand(HudExpr condition, List<HudCommand> body) implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			if (condition.evaluate(ctx.variables) != 0.0) {
				for (HudCommand cmd : body) {
					cmd.execute(ctx);
				}
			}
		}
	}

	record ColorCommand(List<HudExpr> args) implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			if (args.size() == 1) {
				ctx.currentColor = (int) (long) args.get(0).evaluate(ctx.variables);
			} else if (args.size() >= 4) {
				int a = (int) args.get(0).evaluate(ctx.variables) & 0xFF;
				int r = (int) args.get(1).evaluate(ctx.variables) & 0xFF;
				int g = (int) args.get(2).evaluate(ctx.variables) & 0xFF;
				int b = (int) args.get(3).evaluate(ctx.variables) & 0xFF;
				ctx.currentColor = (a << 24) | (r << 16) | (g << 8) | b;
			}
		}
	}

	record DrawStringCommand(HudExpr x, HudExpr y, String format, List<HudExpr> data, boolean centered)
			implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			String text = HudFormat.format(format, data, ctx.variables, ctx.stringVariables);
			int px = ctx.centerX + (int) Math.round(x.evaluate(ctx.variables));
			int py = ctx.centerY + (int) Math.round(y.evaluate(ctx.variables));
			if (centered) {
				int width = ctx.client.textRenderer.getWidth(text);
				px -= width / 2;
			}
			ctx.drawContext.drawText(ctx.client.textRenderer, text, px, py, ctx.currentColor, true);
		}
	}

	record DrawTextureCommand(String texture, HudExpr x, HudExpr y, HudExpr w, HudExpr h,
			HudExpr u, HudExpr v, HudExpr uw, HudExpr vh, HudExpr rotationDegrees) implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			int px = ctx.centerX + (int) Math.round(x.evaluate(ctx.variables));
			int py = ctx.centerY + (int) Math.round(y.evaluate(ctx.variables));
			float drawWidth = (float) w.evaluate(ctx.variables);
			float drawHeight = (float) h.evaluate(ctx.variables);
			int su = (int) Math.round(u.evaluate(ctx.variables));
			int sv = (int) Math.round(v.evaluate(ctx.variables));
			int srcWidth = (int) Math.round(uw.evaluate(ctx.variables));
			int srcHeight = (int) Math.round(vh.evaluate(ctx.variables));
			if (srcWidth == 0 || srcHeight == 0) {
				return;
			}
			// Resolved across whichever namespace the texture was actually found under during the last resource reload..
			HudScriptLoader.TextureInfo textureInfo = HudScriptLoader.resolveTexture(texture);
			float rotation = rotationDegrees != null ? (float) rotationDegrees.evaluate(ctx.variables) : 0f;
			// Draw at native source-region size, then scale/rotate via the matrix stack to reach the requested on-screen width/height.
			ctx.drawContext.getMatrices().pushMatrix();
			ctx.drawContext.getMatrices().translate(px + drawWidth / 2f, py + drawHeight / 2f);
			if (rotation != 0f) {
				ctx.drawContext.getMatrices().rotate((float) Math.toRadians(rotation));
			}
			ctx.drawContext.getMatrices().scale(drawWidth / srcWidth, drawHeight / srcHeight);
			ctx.drawContext.getMatrices().translate(-srcWidth / 2f, -srcHeight / 2f);
			// RenderPipelines.GUI_TEXTURED, NOT RenderLayer::getGuiTextured.
			ctx.drawContext.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, textureInfo.id(),
					0, 0, su, sv, srcWidth, srcHeight, textureInfo.width(), textureInfo.height());
			ctx.drawContext.getMatrices().popMatrix();
		}
	}

	record DrawRectCommand(HudExpr x, HudExpr y, HudExpr w, HudExpr h) implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			int px = ctx.centerX + (int) Math.round(x.evaluate(ctx.variables));
			int py = ctx.centerY + (int) Math.round(y.evaluate(ctx.variables));
			int width = (int) Math.round(w.evaluate(ctx.variables));
			int height = (int) Math.round(h.evaluate(ctx.variables));
			// Negative width/height is valid in the source format (e.g. a bar that grows left/up).
			int x1 = Math.min(px, px + width);
			int x2 = Math.max(px, px + width);
			int y1 = Math.min(py, py + height);
			int y2 = Math.max(py, py + height);
			ctx.drawContext.fill(x1, y1, x2, y2, ctx.currentColor);
		}
	}

	record DrawLineCommand(List<HudExpr> points, boolean stippled, int stipplePattern, int stippleFactor)
			implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			// Stipple pattern isn't rendered specially (DrawContext has no built-in line-stipple support).
			for (int i = 0; i + 3 < points.size(); i += 2) {
				int x1 = ctx.centerX + (int) Math.round(points.get(i).evaluate(ctx.variables));
				int y1 = ctx.centerY + (int) Math.round(points.get(i + 1).evaluate(ctx.variables));
				int x2 = ctx.centerX + (int) Math.round(points.get(i + 2).evaluate(ctx.variables));
				int y2 = ctx.centerY + (int) Math.round(points.get(i + 3).evaluate(ctx.variables));
				drawLine(ctx.drawContext, x1, y1, x2, y2, ctx.currentColor);
			}
		}

		static void drawLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
			// DrawContext has no direct line-drawing primitive.
			if (Math.abs(x2 - x1) >= Math.abs(y2 - y1)) {
				int xa = Math.min(x1, x2);
				int xb = Math.max(x1, x2);
				for (int x = xa; x <= xb; x++) {
					float t = xb == xa ? 0 : (float) (x - x1) / (x2 - x1);
					int y = Math.round(y1 + (y2 - y1) * t);
					ctx.fill(x, y, x + 1, y + 1, color);
				}
			} else {
				int ya = Math.min(y1, y2);
				int yb = Math.max(y1, y2);
				for (int y = ya; y <= yb; y++) {
					float t = yb == ya ? 0 : (float) (y - y1) / (y2 - y1);
					int x = Math.round(x1 + (x2 - x1) * t);
					ctx.fill(x, y, x + 1, y + 1, color);
				}
			}
		}
	}

	/** Simple compass tape across the top of the screen. */
	record DrawGraduationYawCommand(HudExpr angle, HudExpr x, HudExpr y) implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			double centerAngle = angle.evaluate(ctx.variables);
			int px = ctx.centerX + (int) Math.round(x.evaluate(ctx.variables));
			int py = ctx.centerY + (int) Math.round(y.evaluate(ctx.variables));
			for (int deg = -60; deg <= 60; deg += 10) {
				double shown = wrapDegrees(centerAngle + deg);
				int tickX = px + deg * 2;
				int tickHeight = (Math.round(shown) % 30 == 0) ? 6 : 3;
				ctx.drawContext.fill(tickX, py, tickX + 1, py + tickHeight, ctx.currentColor);
				if (Math.round(shown) % 30 == 0) {
					String label = String.valueOf(Math.round(shown));
					int labelWidth = ctx.client.textRenderer.getWidth(label);
					ctx.drawContext.drawText(ctx.client.textRenderer, label, tickX - labelWidth / 2, py + 8,
							ctx.currentColor, true);
				}
			}
		}

		static double wrapDegrees(double deg) {
			double d = deg % 360.0;
			if (d >= 180.0) {
				d -= 360.0;
			} else if (d < -180.0) {
				d += 360.0;
			}
			return d;
		}
	}

	/** Simple pitch ladder on the side of the screen. */
	record DrawGraduationPitch1Command(HudExpr angle, HudExpr x, HudExpr y) implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			double centerAngle = angle.evaluate(ctx.variables);
			int px = ctx.centerX + (int) Math.round(x.evaluate(ctx.variables));
			int py = ctx.centerY + (int) Math.round(y.evaluate(ctx.variables));
			for (int deg = -40; deg <= 40; deg += 10) {
				double shown = centerAngle + deg;
				int tickY = py - deg * 2;
				int tickWidth = (deg % 30 == 0) ? 10 : 5;
				ctx.drawContext.fill(px - tickWidth, tickY, px, tickY + 1, ctx.currentColor);
				if (deg % 30 == 0) {
					String label = String.valueOf(Math.round(shown));
					ctx.drawContext.drawText(ctx.client.textRenderer, label, px + 4, tickY - 4,
							ctx.currentColor, true);
				}
			}
		}
	}

	/** Simplified artificial-horizon-style pitch+roll ladder in the center of the screen. */
	record DrawGraduationPitch2Command(HudExpr pitchAngle, HudExpr rollAngle, HudExpr x, HudExpr y)
			implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			double pitch = pitchAngle.evaluate(ctx.variables);
			double roll = Math.toRadians(rollAngle.evaluate(ctx.variables));
			int px = ctx.centerX + (int) Math.round(x.evaluate(ctx.variables));
			int py = ctx.centerY + (int) Math.round(y.evaluate(ctx.variables)) - (int) Math.round(pitch * 2);
			int halfLen = 40;
			double dx = Math.cos(roll) * halfLen;
			double dy = Math.sin(roll) * halfLen;
			DrawLineCommand.drawLine(ctx.drawContext, (int) (px - dx), (int) (py - dy),
					(int) (px + dx), (int) (py + dy), ctx.currentColor);
		}
	}

	/** Small marker showing how far the player's own view has turned away from the vehicle's own heading. */
	record DrawCameraRotCommand(HudExpr x, HudExpr y) implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			double yaw = ctx.variables.getOrDefault("yaw", 0.0);
			double plyrYaw = ctx.variables.getOrDefault("plyr_yaw", 0.0);
			double delta = DrawGraduationYawCommand.wrapDegrees(plyrYaw - yaw);
			int px = ctx.centerX + (int) Math.round(x.evaluate(ctx.variables)) + (int) Math.round(delta);
			int py = ctx.centerY + (int) Math.round(y.evaluate(ctx.variables));
			ctx.drawContext.fill(px - 1, py - 1, px + 1, py + 1, ctx.currentColor);
		}
	}

	/** Entity/enemy radar isn't implemented. */
	record UnsupportedCommand(String name) implements HudCommand {
		public void execute(HudExecutionContext ctx) {
			// Intentional no-op.
		}
	}

	/** Formats MC Heli-style printf strings against a list of (already-parsed) data expressions, converting each to whatever type its own format specifier expects.. */
	final class HudFormat {
		private static final Pattern SPECIFIER = Pattern.compile("%[-+0#, ]*[0-9]*(\\.[0-9]+)?([a-zA-Z%])");

		private HudFormat() {
		}

		static String format(String fmt, List<HudExpr> data, java.util.Map<String, Double> variables,
				java.util.Map<String, String> stringVariables) {
			Matcher m = SPECIFIER.matcher(fmt);
			StringBuilder out = new StringBuilder();
			int dataIndex = 0;
			int lastEnd = 0;
			while (m.find()) {
				out.append(fmt, lastEnd, m.start());
				lastEnd = m.end();
				char conv = m.group(2).charAt(0);
				String specifier = m.group();
				if (conv == '%') {
					out.append('%');
					continue;
				}
				HudExpr dataExpr = dataIndex < data.size() ? data.get(dataIndex) : null;
				dataIndex++;
				// %s with a data arg that's just a bare variable name matching a STRING variable (e.g. WPN_NAME).
				if (conv == 's' && dataExpr != null) {
					String asIdentifier = dataExpr.toString().trim().toLowerCase(java.util.Locale.ROOT);
					String stringValue = stringVariables.get(asIdentifier);
					if (stringValue != null) {
						out.append(String.format(specifier, stringValue));
						continue;
					}
				}
				double value = dataExpr != null ? dataExpr.evaluate(variables) : 0.0;
				switch (conv) {
					case 'd' -> out.append(String.format(specifier, Math.round(value)));
					case 's' -> {
						String asString = (value == Math.floor(value) && !Double.isInfinite(value))
								? String.valueOf((long) value)
								: String.valueOf(value);
						out.append(String.format(specifier, asString));
					}
					default -> out.append(String.format(specifier, value)); // f, e, g, etc.
				}
			}
			out.append(fmt.substring(lastEnd));
			return out.toString();
		}
	}
}
