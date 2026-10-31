package com.example.tudursvehiclemod.client.sound;

import com.example.tudursvehiclemod.asset.AddonPaths;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Finds every .ogg sound under assets/&lt;namespace&gt;/sound(s)/ (resource packs, mod jars) and under
 * tudursvehiclemod-addons/&lt;addon&gt;/assets/&lt;namespace&gt;/sound(s)/, keyed by file name (lowercase, no extension).
 *
 * A reload only indexes the files. Each sound is decoded and uploaded to OpenAL the first time
 * getBuffer() asks for it: the vanilla sound index alone holds 2000+ files, and decoding all of them on
 * the render thread at every resource reload took many seconds. When two files share a name, addon
 * sounds win over other namespaces and the "minecraft" namespace comes last, so a vanilla sound such as
 * hit1 can no longer shadow an addon sound of the same name.
 *
 * Every OpenAL call is guarded: without an audio device (the sound engine failed to start), getBuffer()
 * returns null and the resource reload itself still succeeds.
 */
public class AddonSoundLoader implements SimpleSynchronousResourceReloadListener {

	/** Where a sound's bytes come from, read only when the sound is first needed. */
	private interface SoundSource {
		byte[] read() throws IOException;
	}

	private static Map<String, SoundSource> index = Map.of();
	private static final Map<String, Integer> loadedBuffers = new HashMap<>();
	/** Names that failed to decode or upload, so they are not retried every frame. */
	private static final java.util.Set<String> failed = new java.util.HashSet<>();

	/** Returns the OpenAL buffer id for the named sound (lowercase, no extension), or null if there is no such sound or it can't be played. */
	public static synchronized Integer getBuffer(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		Integer buffer = loadedBuffers.get(key);
		if (buffer != null || failed.contains(key)) {
			return buffer;
		}
		SoundSource source = index.get(key);
		if (source == null) {
			return null;
		}
		try {
			buffer = decodeAndUpload(source.read(), key);
		} catch (IOException e) {
			System.err.println("[tudursvehiclemod] Failed to read addon sound " + key + ": " + e.getMessage());
		}
		if (buffer == null) {
			failed.add(key);
		} else {
			loadedBuffers.put(key, buffer);
		}
		return buffer;
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new AddonSoundLoader());
	}

	@Override
	public Identifier getFabricId() {
		return Identifier.of("tudursvehiclemod", "addon_sound_loader");
	}

	@Override
	public void reload(ResourceManager manager) {
		synchronized (AddonSoundLoader.class) {
			// Buffers from the previous reload are deleted; they are decoded again on demand.
			for (int bufferId : loadedBuffers.values()) {
				try {
					AL10.alDeleteBuffers(bufferId);
				} catch (Throwable ignored) {
					// No OpenAL context (sound engine down) - nothing to free.
				}
			}
			loadedBuffers.clear();
			failed.clear();

			// Insertion order = priority: the first file seen for a name wins.
			Map<String, SoundSource> found = new java.util.LinkedHashMap<>();
			indexLooseAddons(found);
			indexResourceManager(manager, found, false);
			indexResourceManager(manager, found, true);
			index = Map.copyOf(found);
		}
		System.out.println("[tudursvehiclemod] Addon sound reload: indexed " + index.size() + " sound(s) (decoded on first use)");
	}

	/** vanilla=false indexes every namespace except "minecraft"; vanilla=true only "minecraft". */
	private static void indexResourceManager(ResourceManager manager, Map<String, SoundSource> found, boolean vanilla) {
		for (String folder : new String[] {"sound", "sounds"}) {
			for (Map.Entry<Identifier, Resource> entry :
					manager.findResources(folder, id -> id.getPath().endsWith(".ogg")).entrySet()) {
				Identifier id = entry.getKey();
				if (Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) != vanilla) {
					continue;
				}
				Resource resource = entry.getValue();
				found.putIfAbsent(keyFromFileName(id.getPath()), () -> {
					try (InputStream stream = resource.getInputStream()) {
						return stream.readAllBytes();
					}
				});
			}
		}
	}

	/** Scans tudursvehiclemod-addons/&lt;addon&gt;/assets/&lt;namespace&gt;/sound(s)/ *.ogg directly with plain file I/O. */
	private static void indexLooseAddons(Map<String, SoundSource> found) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path assetsDir = addonDir.resolve("assets");
			for (Path namespaceDir : AddonPaths.listSubdirectories(assetsDir)) {
				for (String folderName : new String[] {"sound", "sounds"}) {
					Path soundDir = namespaceDir.resolve(folderName);
					if (!Files.isDirectory(soundDir)) {
						continue;
					}
					try (var files = Files.walk(soundDir)) {
						for (Path oggFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".ogg"))::iterator) {
							found.putIfAbsent(keyFromFileName(oggFile.getFileName().toString()), () -> Files.readAllBytes(oggFile));
						}
					} catch (IOException e) {
						System.err.println("[tudursvehiclemod] Failed to scan " + soundDir + ": " + e.getMessage());
					}
				}
			}
		}
	}

	/** Decodes raw OGG bytes via STB Vorbis (single-shot, whole-file decode) into a new OpenAL buffer; null if it can't. */
	private static Integer decodeAndUpload(byte[] oggBytes, String key) {
		ByteBuffer nativeOgg = BufferUtils.createByteBuffer(oggBytes.length);
		nativeOgg.put(oggBytes);
		nativeOgg.flip();

		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer channelsBuf = stack.mallocInt(1);
			IntBuffer sampleRateBuf = stack.mallocInt(1);
			ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(nativeOgg, channelsBuf, sampleRateBuf);
			if (pcm == null) {
				System.err.println("[tudursvehiclemod] Could not decode addon sound " + key + " - skipping");
				return null;
			}
			try {
				int format = channelsBuf.get(0) == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
				int bufferId = AL10.alGenBuffers();
				AL10.alBufferData(bufferId, format, pcm, sampleRateBuf.get(0));
				return bufferId;
			} catch (Throwable t) {
				// No OpenAL context: the sound engine failed to start (no audio device).
				System.err.println("[tudursvehiclemod] Can't upload addon sound " + key + " (OpenAL unavailable): " + t);
				return null;
			} finally {
				org.lwjgl.system.libc.LibCStdlib.free(pcm);
			}
		}
	}

	private static String keyFromFileName(String pathOrFileName) {
		String fileName = pathOrFileName.contains("/")
				? pathOrFileName.substring(pathOrFileName.lastIndexOf('/') + 1)
				: pathOrFileName;
		return fileName.substring(0, fileName.length() - ".ogg".length()).toLowerCase(Locale.ROOT);
	}
}
