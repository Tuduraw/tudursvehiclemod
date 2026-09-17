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

/** Loads every.ogg sound under assets/&lt;namespace&gt;/sound/*.ogg AND assets/&lt;namespace&gt;/sounds/*.ogg.. */
public class AddonSoundLoader implements SimpleSynchronousResourceReloadListener {

	private static Map<String, Integer> loadedBuffers = Map.of();

	/** Returns the OpenAL buffer id for the named sound (lowercase, no extension), or null if it wasn't found during the last resource reload. */
	public static Integer getBuffer(String name) {
		return loadedBuffers.get(name.toLowerCase(Locale.ROOT));
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
		// Any buffers from a PREVIOUS reload need deleting first.
		for (int bufferId : loadedBuffers.values()) {
			AL10.alDeleteBuffers(bufferId);
		}

		Map<String, Integer> buffers = new HashMap<>();
		loadFromResourceManager(manager, "sound", buffers);
		loadFromResourceManager(manager, "sounds", buffers);
		loadFromLooseAddons(buffers);
		loadedBuffers = Map.copyOf(buffers);
		System.out.println("[tudursvehiclemod] Addon sound reload: found " + loadedBuffers.size()
				+ " sound(s): " + loadedBuffers.keySet());
	}

	private static void loadFromResourceManager(ResourceManager manager, String folder, Map<String, Integer> buffers) {
		for (Map.Entry<Identifier, Resource> entry :
				manager.findResources(folder, id -> id.getPath().endsWith(".ogg")).entrySet()) {
			Identifier id = entry.getKey();
			String key = keyFromFileName(id.getPath());
			try (InputStream stream = entry.getValue().getInputStream()) {
				decodeAndUpload(key, stream.readAllBytes(), buffers, id.toString());
			} catch (IOException e) {
				System.err.println("[tudursvehiclemod] Failed to read addon sound " + id + ": " + e.getMessage());
			}
		}
	}

	/** Scans tudursvehiclemod-addons/&lt;addon&gt;/assets/&lt;namespace&gt;/sound(s)/ *.ogg directly with plain file I/O. */
	private static void loadFromLooseAddons(Map<String, Integer> buffers) {
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
							String key = keyFromFileName(oggFile.getFileName().toString());
							try {
								decodeAndUpload(key, Files.readAllBytes(oggFile), buffers, oggFile.toString());
							} catch (IOException e) {
								System.err.println("[tudursvehiclemod] Failed to read loose addon sound " + oggFile
										+ ": " + e.getMessage());
							}
						}
					} catch (IOException e) {
						System.err.println("[tudursvehiclemod] Failed to scan " + soundDir + ": " + e.getMessage());
					}
				}
			}
		}
	}

	/** Decodes raw OGG bytes via STB Vorbis (single-shot, whole-file decode. */
	private static void decodeAndUpload(String key, byte[] oggBytes, Map<String, Integer> buffers, String sourceDescription) {
		if (buffers.containsKey(key)) {
			return;
		}
		ByteBuffer nativeOgg = BufferUtils.createByteBuffer(oggBytes.length);
		nativeOgg.put(oggBytes);
		nativeOgg.flip();

		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer channelsBuf = stack.mallocInt(1);
			IntBuffer sampleRateBuf = stack.mallocInt(1);
			ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(nativeOgg, channelsBuf, sampleRateBuf);
			if (pcm == null) {
				System.err.println("[tudursvehiclemod] Could not decode addon sound " + sourceDescription + " - skipping");
				return;
			}
			int channels = channelsBuf.get(0);
			int sampleRate = sampleRateBuf.get(0);
			int format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;

			int bufferId = AL10.alGenBuffers();
			AL10.alBufferData(bufferId, format, pcm, sampleRate);
			org.lwjgl.system.libc.LibCStdlib.free(pcm);
			buffers.put(key, bufferId);
		}
	}

	private static String keyFromFileName(String pathOrFileName) {
		String fileName = pathOrFileName.contains("/")
				? pathOrFileName.substring(pathOrFileName.lastIndexOf('/') + 1)
				: pathOrFileName;
		return fileName.substring(0, fileName.length() - ".ogg".length()).toLowerCase(Locale.ROOT);
	}
}
