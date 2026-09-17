package com.example.tudursvehiclemod.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-only config (these settings. */
public final class VehicleModConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("tudursvehiclemod.json");

	/** Furthest the aircraft third-person camera can zoom out, in blocks. */
	public float maxAircraftCameraDistance = 80f;

	/** If true, this mod's own entities (vehicles, projectiles) use the current chunk/world view distance (Video Settings' own render distance slider, in blocks) as.. */
	public boolean matchChunkViewDistanceForEntityRender = true;

	/** If true, skips ALL of this renderer's own distance/frustum culling checks entirely for this mod's own entities.. */
	public boolean disableEntityRenderCulling = true;

	/** Whether VehicleHud still draws in third-person view (default true, matching pre-existing behavior); first person is unaffected either way. */
	public boolean showHudInThirdPerson = true;

	/** Switches this mod's own vehicle rendering between two vertex-submission modes.
	 *
	 * FALSE (the default) is the ORIGINAL behaviour, from before any of the degenerate-quad work: the render pipeline inherits its own vertex format and QUADS draw mode from vanilla's own ENTITY_SNIPPET untouched, and every triangle is padded to four vertices by repeating its own third one. This wastes 25% of all vertex submissions, but it is the configuration that was known to work.
	 *
	 * TRUE enables the experimental path: the pipeline declares POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL with TRIANGLES explicitly, and only three vertices per triangle are submitted.
	 *
	 * WHY THIS DEFAULTS OFF: with the experimental path enabled, SEUS PTGI rendered vehicles oversized, entirely black, and skewed. That specific combination is the signature of vertex attributes being read with a different layout than they were written with - a shader reading positions at the wrong stride/offset produces exactly that scale and skew distortion, and colour that no longer lands where the shader looks for it reads as black. The likely cause is this pipeline explicitly overriding the vertex format it had previously inherited: a shader pack that recognises the pipeline by its own registered location (which is exactly why DitherCutoutLayers registers it - see that class's own doc) can bind its own replacement shader expecting vanilla's own entity layout, which no longer matches what is declared here. Reverting to inheritance restores the layout that shader packs actually expect.
	 *
	 * Read once when the render pipeline is built, so changing it requires a game restart. */
	public boolean experimentalTriangleRendering = false;

	/** Which of three translucency implementations this mod uses. Recognised values, case-insensitive:
	 *
	 * "dither_shader" - this mod's own custom RenderPipeline with its dithered-discard fragment shader. Screen-space 4x4 ordered dither on a depth-writing cutout pipeline, so occlusion is resolved by the depth buffer and water and other entities stay visible behind see-through areas. NOT compatible with shader packs, and that is structural rather than a bug: a pack replaces entity rendering with its own program entirely, so a custom fragment shader supplied by this mod is never executed, and there is no mechanism for a pack to recognise a mod's own pipeline. Only a stock vanilla pipeline makes packs work, which is what the other two modes use.
	 *
	 * "translucent" - vanilla's own entity translucent pipeline: genuine alpha blending, as this mod rendered before dithering was introduced. Shader-pack compatible, but translucent surfaces resolve by draw ORDER rather than depth, so the water surface can disappear behind them and some entities can fail to draw.
	 *
	 * "dither_texture" (the DEFAULT) - vanilla's own entity cutout pipeline, with the dither pattern baked into the TEXTURE at load instead of applied by a shader. Shader-pack compatible AND keeps the depth-buffer behaviour (dropped texels become fully transparent, a cutout discards them, and a discarded fragment writes no depth - so water and entities behind remain visible). The pattern lives in texture space rather than screen space, which is what translucencyDitherUpscale exists to compensate for.
	 *
	 * Read once when the render layer is built, so changing it requires a game restart. */
	public String translucencyMode = "dither_texture";

	/** Later reshaped, since having this alongside a SEPARATE searchLightIlluminationMode setting was confusing ("BeamとDynamicLightの時点でゴチャついてる感がある..どれをどう設定したら何が有効化するのかがイマイチわかりません" - it's already muddled between Beam and DynamicLight, and it's unclear what setting does what), then extended after a direct correction that further approaches should stand ALONGSIDE "beam"/"dynamic_light" as their own peers rather than as a shape choice layered under "beam" ("Beam、DynamicLightと"並ぶ形で"方針の違う要素を実装していきたい..という意味合いであり、"形状をその場で比較"という意図はありません"): the ONE setting controlling how a lit search light is both illuminated AND drawn. Recognised values, case-insensitive:
	 *
	 * "beam" (the DEFAULT) - a closed translucent cone (MC Heli's own shape), entity lighting only (see EntityRendererLightMixin's own doc), no chunk rebuilding at all.
	 *
	 * "dynamic_light" - the same cone, but block/terrain lighting is also active via real chunk rebuilds (see searchLightChunkRebuildIntervalTicks's own doc for the throttle this needs), so terrain genuinely relights itself the way MC Heli's own search light does rather than only entities doing so. EXPERIMENTAL and opt-in - a moving, wide-range light means continuous, ongoing rebuild work rather than a one-off, which is real, sustained cost for the chunk builder.
	 *
	 * "blades" - same illumination as "beam" (entity-only, no chunk rebuild), but the cone is drawn as a handful of wide longitudinal light shafts rather than a closed surface - see tudursvehiclemod$renderBladeCone()'s own doc for why a closed cone cannot fade under "dither_texture" translucency (its fade is alpha-based, which that mode binarises at load) while shafts read correctly in every translucency mode (their fade is geometric coverage instead).
	 *
	 * "bands" - same illumination as "beam" again, but the cone is drawn as a handful of wide transverse rings along its own length instead of lengthwise shafts - the same coverage-based, translucency-mode-independent fade as "blades", oriented the other way.
	 *
	 * "Dashes" - "blades" with EACH shaft additionally cut into short segments along its own length, so it reads as a dashed line rather than a continuous strip. Same illumination as "beam".
	 *
	 * Read once per frame for the illumination side, so that half takes effect immediately with no restart; the visible cone/shaft/band geometry itself is still read once when a beam is rendered (matching translucencyMode's own read timing), so changing THIS setting can require a restart to see the shape itself change, even though the illumination behaviour updates immediately either way. */
	public String searchLightMode = "beam";

	/** Multiplies how brightly a lit search light illuminates blocks and entities.
	 *
	 * WHY A MULTIPLIER RATHER THAN A DIRECT LEVEL: the base intensity comes from each light's own authored start-colour alpha, which MC Heli uses as the beam's own strength - so a vehicle whose author made one light dimmer than another keeps that relationship. This scales all of them together instead of overriding what the addon specified.
	 *
	 * The default of 3.0 is the requested tripling of the original behaviour. A typical authored alpha is 0x50 (about 0.31 of full), so 1.0 produced only a faint glow - which is what prompted this. The resulting block-light level is still clamped to vanilla's own 0-15, so pushing this very high saturates rather than overflowing; 0 disables the illumination entirely while leaving the visible beam alone.
	 *
	 * Applies regardless of searchLightMode - every mode uses it as the same starting intensity for whichever illumination it applies. Read fresh on every lighting query, so changes take effect immediately with no restart or reload. */
	public double searchLightBrightness = 3.0;

	/** How many ticks apart chunk-rebuild requests are issued while searchLightMode is "dynamic_light" - has no effect in any other mode, none of which ever request a chunk rebuild. Requesting one every single tick for a beam with any real range would keep the chunk builder permanently behind, which is exactly the performance risk "dynamic_light" is opt-in for. Higher values trade smoothness (how quickly lit/unlit terrain catches up to a moving beam) for a lighter, more sustainable load; 10 ticks (twice a second) is a starting point for judging whether the cost is tolerable at all, not a tuned final value. */
	public int searchLightChunkRebuildIntervalTicks = 10;

	/** Multiplies the ring count tudursvehiclemod$scaledRingCount() computes for "blades"/"bands"/"dashes" - see that method's own doc for the base (unmultiplied) scaling this applies on top of. 1.0 leaves that base scaling untouched; 0.5 halves it, 2.0 doubles it, and so on with no ceiling, per the direct instruction that none be imposed. Values at or below 0 collapse to that method's own minimum-ring floor rather than producing zero or negative rings. Read fresh every time a beam is rendered, so changes take effect on the very next frame with no restart. */
	public double searchLightRingCountMultiplier = 1.0;

	/** Multiplies the blade count tudursvehiclemod$scaledBladeCount() computes. Same defaults, same no-ceiling policy, same immediate effect. */
	public double searchLightBladeCountMultiplier = 1.0;

	/** How far from the light, in blocks, terrain is re-rendered with the search light's own colour over it - see SearchLightIllumination's own block-overlay collection for how this is applied.
	 *
	 * The effect fades out towards this distance rather than stopping abruptly. A light whose own length is shorter than this stops at its own length instead, since lighting past where the beam itself ends would make no sense.
	 *
	 * This is the main performance knob for the whole feature: the number of blocks re-rendered grows with the CUBE of this value in the worst case, so doubling it is far more than twice the cost. Lower it on weaker hardware; raise it if the falloff is visibly cutting off closer than wanted and there is headroom to spare. Read fresh every tick, so changes take effect immediately. */
	public double searchLightBlockOverlayRange = 50.0;

	/** Multiplies how strongly the block overlay tints terrain, on top of - not instead of - searchLightBrightness.
	 *
	 * WHY SEPARATE: the two are visually unrelated effects. Entity lighting goes through the vanilla lightmap, where a value is a 0-15 light level competing with the world's own lighting; a block overlay is a translucent colour laid over a surface, where the same number means an alpha. A setting that looked right for one was never going to look right for the other, which is what prompted this.
	 *
	 * 1.0 leaves the block overlay at whatever searchLightBrightness alone produces. Values above brighten it, below dim it, and 0 turns the block overlay off entirely while leaving entity lighting untouched. Read fresh every tick, so changes take effect immediately. */
	public double searchLightBlockBrightness = 1.0;

	/** How many samples the block-overlay raycast fires per light per tick, as a FIXED count entirely independent of that light's own length/endRadius/range - see SearchLightIllumination's own Vogel-spiral sampling doc for why this is architecturally safe against the shape a light happens to have, unlike the earlier spacing-based scheme it replaces (whose own ray count derived from radiusAtRange = tan(halfAngle)*range, a value with no bound as half-angle approaches 90 degrees - the actual cause a review traced a severe freeze/crash report back to).
	 *
	 * THIS IS THE MAIN COST CONTROL for the block overlay - it is now a literal count, not a density, so its own cost is exactly proportional to this number regardless of anything else about the light. 200 is comfortably more than the target (deciding whether light reaches a block-scale surface, not fine visual detail) actually needs, per the direct observation that block-scale precision requires far fewer samples than the earlier scheme used. Diffusion (see OVERLAY_DIFFUSION_STRENGTH's own doc) bridges gaps a sparse sample might miss between two adjacent lit surfaces, so this can be lowered further without ray-count-driven gaps reappearing the way the very first sparse attempt suffered from. Read fresh every tick. */
	public int searchLightBlockOverlaySampleCount = 200;

	/** How many ticks pass between full recomputes of the block overlay - see SearchLightIllumination's own recomputeOverlays doc. The overlay collection is by far that system's own largest allocator, and at 1 it runs at the full 20Hz tick rate.
	 *
	 * Raising this reduces that allocation proportionally (4 means a quarter as much) at the cost of the overlay following a MOVING beam a little less closely - it lags by up to this many ticks. Terrain itself never moves, so a stationary or slowly-sweeping light looks identical at any value here; a fast-slewing one is where a higher value becomes noticeable. Illumination itself (what the lighting mixins query) is never throttled by this and always updates every tick. */
	public int searchLightBlockOverlayIntervalTicks = 4;

	/** Multiplies the length AND end radius the cone/blades/bands/dashes shape is drawn at, together, so the cone's own half-angle (and therefore its proportions) stays the same as it shrinks rather than flattening or narrowing.
	 *
	 * 1.0 (the maximum) draws the full length the light itself was authored with; lower values draw a proportionally shorter, smaller cone. This is purely how far the VISIBLE shape reaches - it has no effect on illumination range, block overlay range, or anything else the light actually does, only on how much of its own already-existing reach is drawn. Clamped to [0, 1] wherever it is read, so a value outside that range in the file has no further effect. Read fresh every frame, so changes apply immediately with no restart. */
	public double searchLightConeDisplayDistance = 1.0;

	/** How many times larger a texture is made before the dither pattern is baked into it, for "dither_texture" mode only.
	 *
	 * WHY THIS FIXES THE COARSENESS: the dither pattern is a fixed 4x4 block of texels. On an unscaled texture that block spans four of the ORIGINAL texels, so a low-resolution texture shows a visibly chunky mesh. Upscaling first shrinks the pattern relative to the model without touching the model or its UVs at all (UVs are normalised, so they keep pointing at the same places). At 4 the pattern lands exactly one-to-one: each original texel becomes a 4x4 block carrying all sixteen Bayer thresholds exactly once, so every texel independently expresses its own alpha to the full 17 levels the pattern can represent, at the finest granularity the original texture's own grid allows. Verified by simulation: average alpha matches the source within one step at every level.
	 *
	 * COST: only textures that actually contain partially-transparent texels are upscaled at all - fully opaque ones are left completely alone, so most vehicle textures cost nothing. An affected texture grows by this factor SQUARED in memory (a 256x256 RGBA texture is 256KB at 1, 4MB at 4, 16MB at 8). Nearest-neighbour replication is used, so fully opaque and fully transparent areas come out pixel-identical to the original.
	 *
	 * Values below 2 disable upscaling entirely. Going far above 4 buys little: the pattern is already finer than the source texture's own texels, while memory keeps growing quadratically and a very fine pattern starts to shimmer at distance under nearest-neighbour sampling. */
	public double translucencyDitherUpscale = 4.0;

	/** Stores dithered vehicle textures on the GPU as BC1/DXT1 instead of uncompressed RGBA8, cutting their VRAM cost to one eighth.
	 *
	 * WHY IT SUITS THIS PARTICULAR CASE: BC1's alpha is one bit per texel, kept exactly rather than interpolated - and after dithering every texel is already fully opaque or fully transparent, so the dither pattern survives compression untouched. What BC1 does approximate is COLOUR, which it stores as two endpoints per 4x4 block with 2-bit interpolation between them, so flat or gently shaded regions compress cleanly while sharp multi-coloured detail within a single block loses precision.
	 *
	 * DEFAULTS OFF because it reaches past Minecraft's own rendering abstraction: Minecraft exposes only RGBA8, RED8 and DEPTH32, with no way to hand it compressed data, so this uploads through a raw OpenGL call against the texture's own handle. That is outside what the game guarantees, and how it interacts with shader packs is not predictable. Every part of it is guarded - any failure falls back to the ordinary uncompressed upload - but it is opt-in for exactly that reason.
	 *
	 * Only affects "dither_texture" mode, since it relies on alpha already being binary. Applies on resource reload. */
	public boolean translucencyDitherGpuCompression = false;

	/** Ceiling on the LARGER side of an upscaled texture, in pixels, for "dither_texture" mode. A texture whose upscale would exceed this has its own factor halved until it fits, so this trades memory against how fine the pattern can get.
	 *
	 * Defaults to 2048, which is 16MB of off-heap RGBA per affected texture. That default exists because an unbounded upscale caused startup failures: a NativeImage lives OUTSIDE the Java heap, so it can exhaust native memory regardless of -Xmx, and the cost grows with the SQUARE of the factor - a 1024x1024 source at 4x is 4096x4096, or 64MB for that one texture alone, which an addon pack with several translucent textures multiplies immediately.
	 *
	 * Raising this is reasonable when a pack has only a few translucent textures, or when running with plenty of memory; 4096 quadruples the budget to 64MB apiece. Note that graphics hardware also imposes its own maximum texture size (commonly 16384, but lower on older hardware), and exceeding it fails at upload rather than allocation - so a value in the low thousands stays safely inside both limits. Allocation failure is caught either way and falls back to dithering without any upscale, so a too-high value degrades quality rather than crashing. */
	public int translucencyDitherMaxTextureSize = 2048;

	public static VehicleModConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				VehicleModConfig loaded = GSON.fromJson(reader, VehicleModConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException | RuntimeException ignored) {
				// Malformed or unreadable.
			}
		}
		VehicleModConfig defaults = new VehicleModConfig();
		defaults.save();
		return defaults;
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException ignored) {
			// Not being able to write the config is not worth crashing over.
		}
	}
}
