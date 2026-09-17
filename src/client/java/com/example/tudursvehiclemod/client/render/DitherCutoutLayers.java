package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.client.mixin.RenderLayerFactoryInvoker;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/** This mod's own translucent-looking vehicle parts (glass, etc.) render this way rather than as a true alpha-blended translucent layer: approximates partial alpha via per-pixel ordered/Bayer dithering (see shaders/core/entity_dither_cutout.fsh's own doc) instead of true blending - every surviving fragment is either fully drawn or fully discarded, never partially blended, so this uses a normal, depth-WRITING pipeline (same behavior as cutout) instead of a translucent one. Normal GPU depth testing (not draw order) resolves all occlusion correctly this way, exactly like any other opaque/cutout surface - including between different vehicles and between different parts of the SAME vehicle (rotor vs glass vs body). Memoized per texture Identifier (same convention as vanilla's own RenderLayers factory methods), so this applies automatically to any vehicle type's own texture without needing any per-entity-type registration at all. */
public final class DitherCutoutLayers {

	private DitherCutoutLayers() {
	}

	/** Whether translucency is handled by a VANILLA pipeline rather than this mod's own dithered one. True for both "translucent" and "dither_texture". Null-guarded because this class initialises lazily on first render, and there is no guarantee it could never be touched before the client config finishes loading - defaulting to the safe path in that case. */
	static final boolean USE_VANILLA_LAYER = !tudursvehiclemod$mode().equals("dither_shader");

	/** Whether the dither pattern is baked into textures at load, on a vanilla CUTOUT pipeline - see VehicleModConfig.translucencyMode's own doc. Read by AddonTextureLoader, which does the baking. */
	public static final boolean BAKE_DITHER_INTO_TEXTURES = tudursvehiclemod$mode().equals("dither_texture");

	/** Upscale factor applied before baking - see VehicleModConfig.translucencyDitherUpscale's own doc. 1 when texture baking isn't in use at all.
	 *
	 * Read fresh on every call rather than captured once, so changing it takes effect on the next resource reload instead of needing the game restarted. Textures are only re-dithered when they are re-read, which is exactly what a reload does. */
	public static double textureDitherUpscale() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		// Values below 1 are allowed and shrink the texture instead, trading pattern quality for memory on weaker hardware. Floored well above zero so a stray value can't collapse a texture to nothing.
		return BAKE_DITHER_INTO_TEXTURES && config != null ? Math.max(0.05, config.translucencyDitherUpscale) : 1.0;
	}

	/** Whether to upload dithered textures as BC1 rather than RGBA8. */
	public static boolean textureDitherGpuCompression() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		return BAKE_DITHER_INTO_TEXTURES && config != null && config.translucencyDitherGpuCompression;
	}

	/** Ceiling on the larger side of an upscaled texture - see VehicleModConfig.translucencyDitherMaxTextureSize's own doc. Floored at 16 so a nonsensical value can't disable upscaling entirely by accident. Read fresh for the same reason as textureDitherUpscale() above. */
	public static int textureDitherMaxSize() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		return BAKE_DITHER_INTO_TEXTURES && config != null ? Math.max(16, config.translucencyDitherMaxTextureSize) : 16;
	}

	/** This mod's own configured translucency mode, normalised, falling back to the default for an unset config or an unrecognised value rather than failing. */
	private static String tudursvehiclemod$mode() {
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		if (config == null || config.translucencyMode == null) {
			// Matches VehicleModConfig.translucencyMode's own default.
			return "dither_texture";
		}
		String mode = config.translucencyMode.trim().toLowerCase(java.util.Locale.ROOT);
		return switch (mode) {
			case "translucent", "dither_texture" -> mode;
			case "dither_shader" -> "dither_shader";
			default -> "dither_texture";
		};
	}

	/** Every mode now actually culls (see the pipeline changes above - "dither_texture" moved from ENTITY_CUTOUT_NO_CULL to ENTITY_CUTOUT, "dither_shader" from withCull(false) to withCull(true)), so the beam/blades/bands/dashes rendering (which reads this exact flag to decide whether it needs to double its own quad geometry - 8 vertices instead of 4 - since the camera sits inside the cone where back-face culling would otherwise hide half of it) now needs that doubling in every mode, not only "translucent" as before this change. */
	public static final boolean CURRENT_MODE_CULLS = true;

	/** Shader-pack compatibility is already broken, with SEUS PTGI rendering vehicles oversized, entirely black and skewed; and the follow-up request to make the change opt-in: whether the experimental TRIANGLES path is active, read ONCE here when this pipeline is built.
	 *
	 * Defaults to false, which is the original pre-change behaviour. Reverting to false did NOT fix the shader-pack breakage, so this is confirmed not to have been its cause and is kept purely as a performance experiment.
	 *
	 * Forced off whenever a vanilla pipeline is in use: such a pipeline carries its own draw mode, which this mod does not get to choose, so vertex submission must fall back to the four-vertex form there regardless. */
	static final boolean EXPERIMENTAL_TRIANGLES = !USE_VANILLA_LAYER
			&& com.example.tudursvehiclemod.client.VehicleModClient.getConfig() != null
			&& com.example.tudursvehiclemod.client.VehicleModClient.getConfig().experimentalTriangleRendering;

	/** Same ENTITY_SNIPPET-based configuration as vanilla's own cutout-style entity pipelines, but with the fragment shader swapped for this mod's own dithered-discard one - see this class's own doc for why. Registered with vanilla's own RenderPipelines.register() (not just built standalone) for the same reason vanilla itself always does this - shader-pack mods (Iris/OptiFine or similar) that recognize/remap pipelines by their own registered location need this to actually be visible to them.
	 *
	 * The vertex format and draw mode are deliberately INHERITED from ENTITY_SNIPPET untouched unless EXPERIMENTAL_TRIANGLES is on - see that field's own doc. Overriding them is what the experimental path does, and is the prime suspect for the reported SEUS PTGI breakage: a shader pack that recognises this pipeline by its registered location can bind its own replacement shader expecting vanilla's own entity vertex layout, so declaring a different one here leaves the two disagreeing about how to read each vertex. */
	private static final RenderPipeline PIPELINE = RenderPipelines.register(tudursvehiclemod$buildPipeline());

	private static RenderPipeline tudursvehiclemod$buildPipeline() {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
				.withLocation(Identifier.of(VehicleMod.MOD_ID, "pipeline/entity_dither_cutout"))
				.withFragmentShader(Identifier.of(VehicleMod.MOD_ID, "core/entity_dither_cutout"))
				.withShaderDefine("PER_FACE_LIGHTING")
				.withSampler("Sampler1")
				// Real culling enabled - see CURRENT_MODE_CULLS's own doc for the matching change to the beam/blade/band/dash decorative geometry this was originally disabled for, which now doubles its own geometry here too instead of relying on this pipeline drawing both faces for it.
				.withCull(true);
		if (EXPERIMENTAL_TRIANGLES) {
			builder = builder.withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
					VertexFormat.DrawMode.TRIANGLES);
		}
		return builder.build();
	}

	/** Same convention as vanilla's own RenderLayers factory fields - built once per distinct texture, reused afterwards, rather than reconstructing a whole RenderLayer (and its underlying RenderSetup) every single frame.
	 *
	 * Picks the pipeline for the configured mode - this mod's own dithered one, vanilla's translucent one for genuine alpha blending, or vanilla's cutout one when the dither pattern has instead been baked into the textures themselves. Everything else about the layer - texture binding, lightmap, overlay, outline mode - stays identical across all three, so the pipeline is the only thing that differs. */
	private static final Function<Identifier, RenderLayer> ENTITY_DITHER_CUTOUT = Util.memoize(texture -> {
		RenderPipeline pipeline;
		String layerName;
		if (BAKE_DITHER_INTO_TEXTURES) {
			// Real culling enabled - ENTITY_CUTOUT (not the _NO_CULL variant) - see CURRENT_MODE_CULLS's own doc for the matching change to the beam/blade/band/dash decorative geometry.
			pipeline = RenderPipelines.ENTITY_CUTOUT;
			layerName = "tudursvehiclemod_entity_baked_dither_cutout";
		} else if (USE_VANILLA_LAYER) {
			pipeline = RenderPipelines.ENTITY_TRANSLUCENT;
			layerName = "tudursvehiclemod_entity_translucent";
		} else {
			pipeline = PIPELINE;
			layerName = "tudursvehiclemod_entity_dither_cutout";
		}
		RenderSetup renderSetup = RenderSetup.builder(pipeline)
				.texture("Sampler0", texture)
				.useLightmap()
				.useOverlay()
				.outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
				.build();
		return RenderLayerFactoryInvoker.tudursvehiclemod$of(layerName, renderSetup);
	});

	/** This is the moment something first draws with a given texture, so it is where that texture is QUEUED for loading if it has not been already.
	 *
	 * Deliberately called on every invocation rather than inside the memoized factory below: the memo survives a resource reload, so putting it there would mean a reloaded texture never got re-registered. requestLoad() only ever does a couple of lookups - the actual loading happens later, from the client tick, because doing it here (mid-frame, inside rendering) caused system-wide GPU driver stalls. See AddonTextureLoader.processPendingLoads() own doc. */
	public static RenderLayer entityDitherCutout(Identifier texture) {
		AddonTextureLoader.requestLoad(texture);
		return ENTITY_DITHER_CUTOUT.apply(texture);
	}

	/** Unlike the model and the beam, these draw thin, sparse overlays rather than large continuous surfaces, so the sorting risk against water and entities that motivated translucencyMode's own dither options in the first place was judged not to apply here. Fixed to translucent blending unconditionally - no mode branch at all.
	 *
	 * Deliberately a VANILLA pipeline. A custom fragment shader could mask the overlay by a block's own texture alpha, but doing so cost shader-pack compatibility outright - shader packs replace pipelines they recognise, and a mod-specific one is not among them - which was judged too high a price for a partial gain (it worked for leaves but not for plants, and washed lit blocks out into flat colour). */
	private static final Function<Identifier, RenderLayer> ILLUMINATION_TRANSLUCENT = Util.memoize(texture -> {
		RenderSetup renderSetup = RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
				.texture("Sampler0", texture)
				.useLightmap()
				.useOverlay()
				.outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
				.build();
		return RenderLayerFactoryInvoker.tudursvehiclemod$of("tudursvehiclemod_illumination_translucent", renderSetup);
	});

	/** The illumination system's own fixed-translucent layer. Same requestLoad() handling as entityDitherCutout() above, for the same reason. */
	public static RenderLayer illuminationTranslucent(Identifier texture) {
		AddonTextureLoader.requestLoad(texture);
		return ILLUMINATION_TRANSLUCENT.apply(texture);
	}

}
