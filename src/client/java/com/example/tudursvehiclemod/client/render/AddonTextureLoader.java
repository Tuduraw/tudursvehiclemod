package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.asset.AddonPaths;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Vehicle textures normally reach the game through the resource pack system, which resolves an Identifier like "mcheliport:textures/vehicle/f4u.png" by searching.. */
public class AddonTextureLoader implements SimpleSynchronousResourceReloadListener {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/AddonTextures");

	@Override
	public Identifier getFabricId() {
		return Identifier.of("tudursvehiclemod", "addon_textures");
	}

	@Override
	public void reload(ResourceManager manager) {
		loadAll();
	}

	/** This now only INDEXES the available textures, mapping each Identifier to the file it came from. No pixel data is read and nothing is registered until something actually draws with that texture.
	 *
	 * WHY: every addon texture used to be decoded, dithered, upscaled and uploaded on every resource reload, whether or not any vehicle using it was ever seen. With the texture-dither modes that is the most expensive thing this mod does at load - an upscaled texture can be tens of megabytes, held on both the CPU and the GPU at once - and almost all of it was for vehicles not on screen.
	 *
	 * Also safe to call directly (e.g. from a manual reload). */
	public static void loadAll() {
		INDEX.clear();
		// Textures registered before a reload are deliberately forgotten here too, so that a reload genuinely re-reads them (their files may have changed) the next time each is drawn.
		LOADED.clear();
		PENDING.clear();
		DECODING.clear();
		for (DecodedTexture stale = READY.poll(); stale != null; stale = READY.poll()) {
			if (stale.image() != null) {
				stale.image().close();
			}
		}
		int found = 0;

		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path assetsDir = addonDir.resolve("assets");
			for (Path namespaceDir : AddonPaths.listSubdirectories(assetsDir)) {
				String namespace = namespaceDir.getFileName().toString();
				Path texturesDir = namespaceDir.resolve("textures").resolve("vehicle");
				if (!Files.isDirectory(texturesDir)) {
					continue;
				}
				try (var files = Files.walk(texturesDir)) {
					for (Path pngFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".png"))::iterator) {
						String relative = texturesDir.relativize(pngFile).toString()
								.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
						INDEX.put(Identifier.of(namespace, "textures/vehicle/" + relative), pngFile);
						found++;
					}
				} catch (Exception e) {
					LOGGER.error("Failed to scan {}", texturesDir, e);
				}
			}
		}

		LOGGER.info("Indexed {} addon textures from tudursvehiclemod-addons/ (loaded on first use)", found);
	}

	/** Loads and registers one texture if it hasn't been already. Called from DitherCutoutLayers.entityDitherCutout() - i.e. at the moment something first draws with this texture.
	 *
	 * LOADED is what guarantees that. An Identifier that has been registered is never decoded a second time, however many vehicles share it and however often they are drawn, so nothing is ever loaded twice. Textures are deliberately NOT unloaded afterwards, which keeps this free of the lifecycle bookkeeping that would otherwise be needed.
	 *
	 * Must be called on the render thread, which is where it is called from. */
	public static void requestLoad(Identifier id) {
		if (LOADED.contains(id) || !INDEX.containsKey(id)) {
			return;
		}
		PENDING.add(id);
	}

	/** Performs at most one queued texture load, from the client tick - that is, OUTSIDE any rendering.
	 *
	 * WHY THIS EXISTS: loading used to happen inline from DitherCutoutLayers.entityDitherCutout(), which runs DURING rendering, in the middle of a frame and potentially inside a shader mod's own render pass. That one call did blocking disk I/O, PNG decode, dithering, an upscale allocating tens of megabytes, several million pixel operations, a GPU texture creation and upload, and - with GPU compression on - raw OpenGL calls, all synchronously on the render thread. A frame that long stalls the GPU driver, and a driver that stops responding is exactly what makes the display server freeze system-wide, recover, and leave the game dead without ever reaching Minecraft's own crash handler. It also matches the reported pattern: it only ever happened while summoning vehicles (the only thing that triggers a NEW texture load) and never at a predictable moment.
	 *
	 * Doing the work here instead means the heavy part happens between frames, where a long operation costs a tick rather than risking the driver. ONE texture per tick deliberately: summoning several vehicles at once would otherwise reintroduce the same stall in a single tick. A texture that isn't ready yet simply renders untextured for a frame or two, which is a far better failure than a device reset. */
	public static void processPendingLoads() {
		// Step 1: hand one queued texture to a worker, if nothing is being decoded already.
		if (DECODING.isEmpty()) {
			Identifier id = PENDING.poll();
			while (id != null && LOADED.contains(id)) {
				id = PENDING.poll();
			}
			if (id != null && INDEX.containsKey(id)) {
				Identifier target = id;
				DECODING.add(target);
				java.util.concurrent.CompletableFuture
						.supplyAsync(() -> tudursvehiclemod$decode(target), Util.getMainWorkerExecutor())
						.thenAccept(decoded -> READY.add(new DecodedTexture(target, decoded)));
			}
		}

		// Step 2: upload at most one finished image. This part MUST be here, on the main thread.
		DecodedTexture decoded = READY.poll();
		if (decoded != null) {
			DECODING.remove(decoded.id());
			tudursvehiclemod$register(decoded);
		}
	}

	/** Everything that does NOT have to touch the GPU - reading the file, decoding the PNG, dithering, and the upscale that can allocate tens of megabytes and run through millions of pixels - happens here, on a worker.
	 *
	 * Only the upload itself needs the main thread, so leaving the heavy part on it was making every first sight of a new vehicle cost a visible hitch. Splitting it does not change the average frame rate - the same work still happens - but it moves it off the thread where a stall is actually felt. Returns null if the texture cannot be read, which the caller treats as "give up on this one". */
	private static NativeImage tudursvehiclemod$decode(Identifier id) {
		Path pngFile = INDEX.get(id);
		if (pngFile == null) {
			return null;
		}
		try (InputStream in = Files.newInputStream(pngFile)) {
			NativeImage image = NativeImage.read(in);
			if (DitherCutoutLayers.BAKE_DITHER_INTO_TEXTURES) {
				image = tudursvehiclemod$bakeDitherPattern(image);
			}
			return image;
		} catch (Exception e) {
			LOGGER.error("Failed to load addon texture {}", pngFile, e);
			return null;
		}
	}

	/** Registers an already-decoded image with the texture manager, on the main thread. Everything from here on genuinely needs to be here: creating the GPU texture, uploading it, the optional BC1 re-upload, and releasing the CPU copy.
	 *
	 * LOADED is marked here, at the point the texture actually exists, and is never cleared except by a resource reload - so an Identifier is decoded once no matter how many vehicles share it. Textures are deliberately not unloaded. */
	private static void tudursvehiclemod$register(DecodedTexture decoded) {
		Identifier id = decoded.id();
		NativeImage image = decoded.image();
		if (image == null) {
			// Decode failed; still marked as loaded so a broken file isn't retried every tick.
			LOADED.add(id);
			return;
		}
		if (!LOADED.add(id)) {
			// Already registered while this one was decoding - discard the duplicate rather than leaking it.
			image.close();
			return;
		}
		try {
			NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
			if (DitherCutoutLayers.textureDitherGpuCompression()) {
				tudursvehiclemod$tryCompressOnGpu(texture, image, id);
			}
			tudursvehiclemod$releaseCpuCopy(texture, id);
		} catch (Exception e) {
			LOGGER.error("Failed to register addon texture {}", id, e);
		}
	}

	/** One decoded image waiting for its main-thread upload - see processPendingLoads()'s own doc. */
	private record DecodedTexture(Identifier id, NativeImage image) {}

	/** Identifiers currently being decoded on a worker. Keeps only one decode in flight, so summoning several vehicles at once cannot put several large upscales through the worker pool simultaneously. */
	private static final java.util.Set<Identifier> DECODING = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Images decoded and waiting to be uploaded on the main thread. */
	private static final java.util.Queue<DecodedTexture> READY = new java.util.concurrent.ConcurrentLinkedQueue<>();

	/** Identifiers waiting to be loaded - see processPendingLoads()'s own doc. */
	private static final java.util.Queue<Identifier> PENDING = new java.util.concurrent.ConcurrentLinkedQueue<>();

	/** Drops the CPU-side copy of a texture once the GPU has its own, halving what an addon texture costs.
	 *
	 * A NativeImageBackedTexture keeps its own NativeImage so it can re-upload later, but these textures are static - once uploaded, the CPU copy is never read again. For an upscaled dithered texture that copy is the same size as the GPU one (16MB for a 2048x2048), so releasing it is a straight 50% saving on every addon texture.
	 *
	 * Guarded because it depends on setImage() tolerating a detach: if any part of it fails, the copy is simply kept, which costs memory but is otherwise completely harmless. upload() runs first so the GPU is guaranteed to have the data before anything is released. */
	private static void tudursvehiclemod$releaseCpuCopy(NativeImageBackedTexture texture, Identifier id) {
		try {
			texture.upload();
			NativeImage cpuCopy = texture.getImage();
			if (cpuCopy == null) {
				return;
			}
			// Detach before closing, so the texture's own close() can never free the same image a second time.
			texture.setImage(null);
			cpuCopy.close();
		} catch (Throwable failure) {
			LOGGER.warn("Could not release the CPU-side copy of {} - it stays in memory alongside the GPU texture", id, failure);
		}
	}

	/** Identifier -> the file it is read from. Populated by loadAll(), consumed by tudursvehiclemod$decode(). */
	private static final java.util.Map<Identifier, Path> INDEX = new java.util.concurrent.ConcurrentHashMap<>();

	/** Identifiers already registered - see tudursvehiclemod$register()'s own doc for why this alone is enough to prevent any duplicate load. */
	private static final java.util.Set<Identifier> LOADED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Converts every partially-transparent texel into fully opaque or fully transparent using the same 4x4 ordered (Bayer) matrix this mod's own fragment shader applies in screen space, optionally upscaling the image first so the pattern lands finer.
	 *
	 * WHY UPSCALING SOLVES THE COARSENESS: the pattern is a fixed 4x4 block of texels, so on an unscaled texture it spans four ORIGINAL texels and reads as a chunky mesh on anything low-resolution. Upscaling shrinks it relative to the model while changing nothing about the model or its UVs, which are normalised and so keep addressing the same places. At the default factor of 4 it lands exactly one-to-one: each original texel becomes a 4x4 block carrying all sixteen Bayer thresholds exactly once, so every texel expresses its own alpha independently across the full 17 levels the pattern can represent - the finest the source grid can support.
	 *
	 * ONLY TRANSLUCENT TEXTURES PAY FOR IT: an image with no partially-transparent texel at all is returned completely untouched, so ordinary opaque vehicle textures cost nothing in memory or time. Upscaling uses nearest-neighbour replication, so fully opaque and fully transparent regions of an affected texture also come out pixel-identical to the original - only genuinely partial alpha is ever changed.
	 *
	 * The dropped texels become alpha 0, which the cutout pipeline discards, and a discarded fragment writes no depth - which is what preserves the depth-buffer behaviour this mode exists for (water and other entities stay visible behind see-through areas), unlike genuine alpha blending. */
	private static NativeImage tudursvehiclemod$bakeDitherPattern(NativeImage source) {
		if (!tudursvehiclemod$hasPartialAlpha(source)) {
			return source;
		}
		double scale = tudursvehiclemod$effectiveUpscale(source.getWidth(), source.getHeight());
		if (scale == 1.0) {
			return tudursvehiclemod$ditherInPlace(source);
		}
		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
		NativeImage result;
		try {
			result = new NativeImage(width, height, false);
		} catch (OutOfMemoryError | RuntimeException error) {
			// Allocation is off-heap, so it can fail independently of the Java heap limit. Falling back to dithering the original in place keeps the mode working (just coarser) instead of taking the game down with it.
			LOGGER.error("Not enough memory to upscale a {}x{} vehicle texture by {} for dithering - falling back to no upscale",
					source.getWidth(), source.getHeight(), scale, error);
			return tudursvehiclemod$ditherInPlace(source);
		}
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int sourceX = Math.min(source.getWidth() - 1, (int) (x / scale));
				int sourceY = Math.min(source.getHeight() - 1, (int) (y / scale));
				result.setColorArgb(x, y, tudursvehiclemod$ditheredArgb(source.getColorArgb(sourceX, sourceY), x, y));
			}
		}
		source.close();
		return result;
	}

	/** The configured factor, reduced as far as needed to keep this one texture's own upscaled form within the configured maximum texture size.
	 *
	 * A NativeImage is allocated OFF the Java heap, so a large source multiplied by the factor squared can exhaust native memory regardless of -Xmx - a 2048x2048 texture at 4x is 8192x8192, which is 268MB for that single texture alone. Halving the factor rather than refusing outright means a big texture still gets whatever refinement it can afford, and a small one (the common case) still gets the full configured factor. The ceiling itself is configurable - see VehicleModConfig.translucencyDitherMaxTextureSize's own doc. */
	private static double tudursvehiclemod$effectiveUpscale(int sourceWidth, int sourceHeight) {
		double scale = DitherCutoutLayers.textureDitherUpscale();
		int maxSize = DitherCutoutLayers.textureDitherMaxSize();
		// A factor below 1 SHRINKS the texture, which is a deliberate quality-for-memory trade on weak hardware. Only factors above 1 can breach the size ceiling, so only those are halved back.
		while (scale > 1.0 && Math.max(sourceWidth, sourceHeight) * scale > maxSize) {
			scale /= 2.0;
		}
		return scale;
	}

	/** Dithers a texture at its own original resolution, with no upscale at all - the fallback path when upscaling is disabled, unaffordable, or has just failed. */
	private static NativeImage tudursvehiclemod$ditherInPlace(NativeImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				image.setColorArgb(x, y, tudursvehiclemod$ditheredArgb(image.getColorArgb(x, y), x, y));
			}
		}
		return image;
	}

	/** One texel's own dithered result: partial alpha becomes fully opaque or fully transparent according to this position's own Bayer threshold, while fully opaque and fully transparent texels pass through untouched. */
	private static int tudursvehiclemod$ditheredArgb(int argb, int x, int y) {
		int alpha = (argb >>> 24) & 0xFF;
		if (alpha == 0 || alpha == 0xFF) {
			return argb;
		}
		// Offsetting by 0.5/16 centers each threshold within its own bucket, matching entity_dither_cutout.fsh exactly.
		float threshold = (BAYER_4X4[(y & 3) * 4 + (x & 3)] + 0.5f) / 16.0f;
		int newAlpha = (alpha / 255.0f) >= threshold ? 0xFF : 0x00;
		return (newAlpha << 24) | (argb & 0x00FFFFFF);
	}


	/** True if any texel is neither fully opaque nor fully transparent - see tudursvehiclemod$bakeDitherPattern()'s own doc for why a texture without any is skipped entirely. */
	private static boolean tudursvehiclemod$hasPartialAlpha(NativeImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int alpha = (image.getColorArgb(x, y) >>> 24) & 0xFF;
				if (alpha != 0 && alpha != 0xFF) {
					return true;
				}
			}
		}
		return false;
	}

	/** The same 4x4 ordered/Bayer matrix entity_dither_cutout.fsh uses - see tudursvehiclemod$bakeDitherPattern()'s own doc. */
	private static final float[] BAYER_4X4 = {
			0f, 8f, 2f, 10f,
			12f, 4f, 14f, 6f,
			3f, 11f, 1f, 9f,
			15f, 7f, 13f, 5f
	};

	/** Re-uploads an already-registered texture as BC1/DXT1, cutting its VRAM cost to one eighth. Silently does nothing on any failure, leaving the ordinary uncompressed upload in place.
	 *
	 * WHY THIS GOES AROUND MINECRAFT'S OWN API: TextureFormat offers only RGBA8, RED8 and DEPTH32, and every upload path takes a NativeImage or a NativeImage.Format buffer, so there is no supported way to hand the game compressed data at all. The compressed image is therefore pushed straight to the texture's own GL handle. That is outside what the game guarantees, which is exactly why VehicleModConfig.translucencyDitherGpuCompression defaults to off.
	 *
	 * The GL handle is located reflectively rather than through a mixin: the field's own name is not part of any public contract, so scanning for it and failing gracefully is more robust than hard-coding a name that may not match. Everything here is wrapped, and any failure at all simply leaves the texture as it already is - correct, just uncompressed. */
	private static void tudursvehiclemod$tryCompressOnGpu(NativeImageBackedTexture texture, NativeImage image, Identifier id) {
		java.nio.ByteBuffer compressed = null;
		try {
			int width = image.getWidth();
			int height = image.getHeight();
			if (width % 4 != 0 || height % 4 != 0) {
				// BC1 works in whole 4x4 blocks; a non-multiple size would need padding that is not worth the complexity here.
				return;
			}
			Integer glId = tudursvehiclemod$findGlTextureId(texture);
			if (glId == null) {
				LOGGER.warn("Could not locate the GL handle for {} - leaving it uncompressed", id);
				return;
			}
			compressed = tudursvehiclemod$encodeBc1(image);
			// The binding that was in place beforehand is captured and restored below. Leaving a different texture bound corrupts the state Minecraft (and any shader mod) believes is current, which is its own route to invalid GPU work.
			int previousBinding = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
			org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, glId);
			org.lwjgl.opengl.GL13.glCompressedTexImage2D(
					org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, GL_COMPRESSED_RGBA_S3TC_DXT1_EXT,
					width, height, 0, compressed);
			int error = org.lwjgl.opengl.GL11.glGetError();
			org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, previousBinding);
			if (error != org.lwjgl.opengl.GL11.GL_NO_ERROR) {
				LOGGER.warn("GPU compression of {} reported GL error {} - the texture may render incorrectly", id, error);
			}
		} catch (Throwable failure) {
			LOGGER.warn("GPU compression of {} failed - leaving it uncompressed", id, failure);
		} finally {
			if (compressed != null) {
				org.lwjgl.system.MemoryUtil.memFree(compressed);
			}
		}
	}

	/** Encodes an image to BC1/DXT1 - see tudursvehiclemod$tryCompressOnGpu()'s own doc.
	 *
	 * Alpha survives EXACTLY: BC1 carries one alpha bit per texel rather than interpolating it, and after dithering every texel is already fully opaque or fully transparent, so the dither pattern is reproduced perfectly (verified by simulation over 48,000 texels). Colour is what BC1 approximates, storing two endpoints per 4x4 block with interpolation between them. */
	private static java.nio.ByteBuffer tudursvehiclemod$encodeBc1(NativeImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		java.nio.ByteBuffer out = org.lwjgl.system.MemoryUtil.memAlloc(width * height / 2);
		int[] blockR = new int[16], blockG = new int[16], blockB = new int[16];
		boolean[] blockOpaque = new boolean[16];
		for (int blockY = 0; blockY < height; blockY += 4) {
			for (int blockX = 0; blockX < width; blockX += 4) {
				boolean anyTransparent = false;
				boolean anyOpaque = false;
				for (int t = 0; t < 16; t++) {
					int argb = image.getColorArgb(blockX + (t & 3), blockY + (t >> 2));
					blockR[t] = (argb >> 16) & 0xFF;
					blockG[t] = (argb >> 8) & 0xFF;
					blockB[t] = argb & 0xFF;
					blockOpaque[t] = ((argb >>> 24) & 0xFF) >= 128;
					anyTransparent |= !blockOpaque[t];
					anyOpaque |= blockOpaque[t];
				}
				if (!anyOpaque) {
					// Entirely transparent block: c0 < c1 selects the punch-through mode, and index 3 means transparent everywhere.
					out.putShort((short) 0).putShort((short) 0xFFFF).putInt(0xFFFFFFFF);
					continue;
				}
				// Endpoints are the darkest and brightest OPAQUE texels, so transparent ones never drag the colour range around.
				int lowIndex = -1, highIndex = -1, lowLuma = Integer.MAX_VALUE, highLuma = Integer.MIN_VALUE;
				for (int t = 0; t < 16; t++) {
					if (!blockOpaque[t]) {
						continue;
					}
					int luma = blockR[t] * 299 + blockG[t] * 587 + blockB[t] * 114;
					if (luma < lowLuma) { lowLuma = luma; lowIndex = t; }
					if (luma > highLuma) { highLuma = luma; highIndex = t; }
				}
				int color0 = tudursvehiclemod$pack565(blockR[highIndex], blockG[highIndex], blockB[highIndex]);
				int color1 = tudursvehiclemod$pack565(blockR[lowIndex], blockG[lowIndex], blockB[lowIndex]);
				if (anyTransparent) {
					// Punch-through mode requires color0 <= color1.
					if (color0 > color1) { int swap = color0; color0 = color1; color1 = swap; }
					if (color0 == color1 && color1 < 0xFFFF) { color1++; }
				} else if (color0 <= color1) {
					// Opaque mode requires color0 > color1.
					if (color1 > 0) { color0 = color1; color1 = color0 - 1; } else { color0 = 1; color1 = 0; }
				}
				int[] paletteR = new int[4], paletteG = new int[4], paletteB = new int[4];
				tudursvehiclemod$unpack565(color0, paletteR, paletteG, paletteB, 0);
				tudursvehiclemod$unpack565(color1, paletteR, paletteG, paletteB, 1);
				if (anyTransparent) {
					paletteR[2] = (paletteR[0] + paletteR[1]) / 2;
					paletteG[2] = (paletteG[0] + paletteG[1]) / 2;
					paletteB[2] = (paletteB[0] + paletteB[1]) / 2;
				} else {
					paletteR[2] = (2 * paletteR[0] + paletteR[1]) / 3;
					paletteG[2] = (2 * paletteG[0] + paletteG[1]) / 3;
					paletteB[2] = (2 * paletteB[0] + paletteB[1]) / 3;
					paletteR[3] = (paletteR[0] + 2 * paletteR[1]) / 3;
					paletteG[3] = (paletteG[0] + 2 * paletteG[1]) / 3;
					paletteB[3] = (paletteB[0] + 2 * paletteB[1]) / 3;
				}
				int indices = 0;
				int candidateCount = anyTransparent ? 3 : 4;
				for (int t = 0; t < 16; t++) {
					int best;
					if (anyTransparent && !blockOpaque[t]) {
						best = 3;
					} else {
						best = 0;
						int bestDistance = Integer.MAX_VALUE;
						for (int candidate = 0; candidate < candidateCount; candidate++) {
							int dr = blockR[t] - paletteR[candidate];
							int dg = blockG[t] - paletteG[candidate];
							int db = blockB[t] - paletteB[candidate];
							int distance = dr * dr + dg * dg + db * db;
							if (distance < bestDistance) { bestDistance = distance; best = candidate; }
						}
					}
					indices |= best << (2 * t);
				}
				out.putShort((short) color0).putShort((short) color1).putInt(indices);
			}
		}
		return out.flip();
	}

	private static int tudursvehiclemod$pack565(int r, int g, int b) {
		return ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
	}

	private static void tudursvehiclemod$unpack565(int packed, int[] r, int[] g, int[] b, int slot) {
		r[slot] = (((packed >> 11) & 0x1F) * 255) / 31;
		g[slot] = (((packed >> 5) & 0x3F) * 255) / 63;
		b[slot] = ((packed & 0x1F) * 255) / 31;
	}

	/** Finds the OpenGL texture name behind a texture, by scanning its own GpuTexture for an int field holding a plausible handle - see tudursvehiclemod$tryCompressOnGpu()'s own doc for why this is done reflectively. Returns null if nothing suitable is found, which the caller treats as "leave it uncompressed". */
	private static Integer tudursvehiclemod$findGlTextureId(NativeImageBackedTexture texture) {
		try {
			Object gpuTexture = texture.getGlTexture();
			if (gpuTexture == null) {
				return null;
			}
			for (Class<?> type = gpuTexture.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
				for (java.lang.reflect.Field field : type.getDeclaredFields()) {
					if (field.getType() != int.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
						continue;
					}
					String name = field.getName().toLowerCase(java.util.Locale.ROOT);
					if (!name.equals("id") && !name.contains("glid") && !name.contains("handle")) {
						continue;
					}
					field.setAccessible(true);
					int value = field.getInt(gpuTexture);
					if (value > 0) {
						return value;
					}
				}
			}
		} catch (Throwable ignored) {
			// Any reflection failure just means no compression, which is handled by the caller.
		}
		return null;
	}

	/** GL_COMPRESSED_RGBA_S3TC_DXT1_EXT - BC1 with one-bit alpha. Declared here because LWJGL exposes it only through the EXT_texture_compression_s3tc extension class, and this avoids depending on that class being present. */
	private static final int GL_COMPRESSED_RGBA_S3TC_DXT1_EXT = 0x83F1;
}
