package com.example.tudursvehiclemod.asset;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Loads assets/&lt;namespace&gt;/weapons/&lt;name&gt;.txt files directly from the loose tudursvehiclemod-addons/&lt;addon&gt;/ folder structure (see AddonPaths) into.. */
public final class WeaponStatsLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/WeaponStats");
	/** Lock-on time scales with a target's own distance BY DEFAULT (farther takes longer), even for a weapon file that doesn't configure this explicitly at all: at this rate, a typical long-range engagement (~100 blocks) adds a modest, clearly-noticeable ~1.5 extra seconds on top of the weapon's own base LockTime - meaningful enough to matter tactically without making a genuinely long-range lock impractically slow. */
	private static final float LOCK_TIME_PER_BLOCK_DEFAULT = 0.3f;
	/** Matches AbstractVehicleEntity's own original hardcoded MISSILE_TURN_RATE_DEGREES_PER_TICK value exactly, per TurnRate's own doc. */
	private static final float MISSILE_TURN_RATE_DEGREES_PER_TICK_DEFAULT = 3.0f;

	private static Map<String, WeaponStats> loaded = Map.of();

	private WeaponStatsLoader() {
	}

	private static final java.util.Set<String> loggedFallbacks = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Falls back to WeaponStats.FALLBACK if name isn't currently loaded (missing file, failed to parse, or not yet reloaded). */
	public static WeaponStats get(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		WeaponStats stats = loaded.get(key);
		if (stats == null) {
			if (loggedFallbacks.add(key)) {
				LOGGER.warn("[tudursvehiclemod] Weapon '{}' has no loaded stats - using WeaponStats.FALLBACK", key);
			}
			return WeaponStats.FALLBACK;
		}
		return stats;
	}

	/** Resource-pack/mod-jar weapons first, then the loose tudursvehiclemod-addons/ folder.
	 *
	 * That order means a loose file still OVERRIDES a bundled one of the same name, which is the
	 * behaviour the addons folder has always had - dropping a tweaked copy in there wins.
	 *
	 * The ResourceManager half is what lets an ADDON MOD ship its own weapon inside its jar, at
	 * assets/&lt;namespace&gt;/weapons/&lt;name&gt;.txt. An addon can't write into the external folder, so
	 * without this it had no way to supply a weapon of its own at all. */
	public static void reload(net.minecraft.resource.ResourceManager manager) {
		Map<String, WeaponStats> result = new HashMap<>();
		int fromResourcePacks = 0;

		if (manager != null) {
			for (Map.Entry<Identifier, net.minecraft.resource.Resource> entry :
					manager.findResources("weapons", id -> id.getPath().endsWith(".txt")).entrySet()) {
				Identifier fileId = entry.getKey();
				String fileName = fileId.getPath().substring(fileId.getPath().lastIndexOf('/') + 1);
				String key = fileName.substring(0, fileName.length() - ".txt".length()).toLowerCase(Locale.ROOT);
				try (var stream = entry.getValue().getInputStream()) {
					// Same lenient decoding the loose-file path uses - these files are commonly
					// Shift-JIS in the wild, and a bundled one may well be a copy of such a file.
					String content = decodeLeniently(stream.readAllBytes());
					result.put(key, parseContent(content, fileId.getNamespace(), key));
					fromResourcePacks++;
				} catch (Exception e) {
					LOGGER.error("Failed to read bundled weapon config {}", fileId, e);
				}
			}
		}

		Path addonsRoot = AddonPaths.getAddonsRoot();
		var addonDirs = AddonPaths.listSubdirectories(addonsRoot);
		for (Path addonDir : addonDirs) {
			Path assetsDir = addonDir.resolve("assets");
			for (Path namespaceDir : AddonPaths.listSubdirectories(assetsDir)) {
				String namespace = namespaceDir.getFileName().toString();
				Path weaponsDir = namespaceDir.resolve("weapons");
				if (!Files.isDirectory(weaponsDir)) {
					continue;
				}
				try (var files = Files.walk(weaponsDir)) {
					for (Path txtFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".txt"))::iterator) {
						String fileName = txtFile.getFileName().toString();
						String key = fileName.substring(0, fileName.length() - ".txt".length()).toLowerCase(Locale.ROOT);
						try {
							WeaponStats stats = parse(txtFile, namespace, key);
							result.put(key, stats);
						} catch (IOException e) {
							LOGGER.error("Failed to read weapon config {}", txtFile, e);
						}
					}
				} catch (IOException e) {
					LOGGER.error("Failed to scan {}", weaponsDir, e);
				}
			}
		}
		loaded = Map.copyOf(result);
		loggedFallbacks.clear();
		LOGGER.info("Loaded {} weapon config(s) ({} bundled, {} addon pack(s) found)",
				loaded.size(), fromResourcePacks, addonDirs.size());
	}

	/** Kept for any caller that has no ResourceManager to hand - scans the loose addons folder only. */
	public static void reload() {
		reload(null);
	}

	/** Tries UTF-8 first (the common case, and what the rest of this format assumes for its own directive names/values); falls back to Shift-JIS (Windows-31J/CP932).. */
	private static String readTextLeniently(Path path) throws IOException {
		return decodeLeniently(Files.readAllBytes(path));
	}

	/** See readTextLeniently()'s own doc - the decoding half, shared with the resource-pack path. */
	private static String decodeLeniently(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
		} catch (java.nio.charset.CharacterCodingException e) {
			return new String(bytes, Charset.forName("windows-31j"));
		}
	}

	private static WeaponStats parse(Path path, String namespace, String weaponName) throws IOException {
		return parseContent(readTextLeniently(path), namespace, weaponName);
	}

	/** The parser proper, working from already-read text so the same format can come from a loose
	 * file in the addons folder OR from a resource pack / mod jar - see reload(ResourceManager). */
	private static WeaponStats parseContent(String content, String namespace, String weaponName) {
		Map<String, String> entries = new HashMap<>();
		// "Item = <count>, <name>" can appear up to 3 times (once each for iron_ingot/gunpowder/redstone.
		java.util.List<String> itemLines = new java.util.ArrayList<>();
		// "CasWaypoint = relX,relY,relZ,speedPercent,attack" - see CasWaypoint's own parsing doc, one weapon file can have any number of these (a full route).
		java.util.List<String> casWaypointLines = new java.util.ArrayList<>();
		// "CarrierWaypoint = relX,relY,relZ,speedPercent,attack" - same format as CasWaypoint's own (see CarrierAircraftConfig's own doc), one weapon file can have any number of these.
		java.util.List<String> carrierWaypointLines = new java.util.ArrayList<>();
		// "CarrierLaunchWaypoint = relX,relY,relZ,speedPercent,gear,bay,speedBoostKmh" - a separate dedicated liftoff route flown FIRST (see CarrierAircraftConfig's own launchWaypoints doc). this line format has no "attack" column at all (unlike CasWaypoint/CarrierWaypoint) - launch waypoints never attack, see CarrierAircraftConfig's own doc.
		java.util.List<String> carrierLaunchWaypointLines = new java.util.ArrayList<>();
		// "CarrierLandingWaypoint = relX,relY,relZ,speedPercent,gear,bay,speedBoostKmh" - same format as CarrierLaunchWaypoint's own, a separate dedicated final-approach route flown before the existing single-point approach (see CarrierAircraftConfig's own landingWaypoints doc).
		java.util.List<String> carrierLandingWaypointLines = new java.util.ArrayList<>();
		// "CarrierRecoveryPoint = relX,relY,relZ,radius" - see CarrierAircraftConfig's own extraRecoveryPoints doc, one weapon file can have any number of these (additional recovery zones on top of the always-active AddWeapon-centered one).
		java.util.List<String> carrierRecoveryPointLines = new java.util.ArrayList<>();
		if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
			content = content.substring(1); // strip UTF-8 BOM (utf-8-sig), same as the Python converter reads with
		}
		for (String rawLine : content.split("\r\n|\r|\n")) {
			String line = rawLine.strip();
			if (line.isEmpty() || line.startsWith(";") || !line.contains("=")) {
				continue;
			}
			int eq = line.indexOf('=');
			String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
			// Strips a same-line trailing comment (";..") from the value.
			String rawValue = line.substring(eq + 1);
			int commentStart = rawValue.indexOf(';');
			String value = (commentStart >= 0 ? rawValue.substring(0, commentStart) : rawValue).strip();
			if (key.equals("item")) {
				itemLines.add(value);
			} else if (key.equals("caswaypoint")) {
				casWaypointLines.add(value);
			} else if (key.equals("carrierwaypoint")) {
				carrierWaypointLines.add(value);
			} else if (key.equals("carrierlaunchwaypoint")) {
				carrierLaunchWaypointLines.add(value);
			} else if (key.equals("carrierlandingwaypoint")) {
				carrierLandingWaypointLines.add(value);
			} else if (key.equals("carrierrecoverypoint")) {
				carrierRecoveryPointLines.add(value);
			} else {
				entries.put(key, value);
			}
		}

		float power = toFloat(entries.get("power"), 0.0f);
		float acceleration = toFloat(entries.get("acceleration"), 0.0f);
		float damage = power;
		// No minimum or maximum clamp - an upper clamp (MAX_REASONABLE_VELOCITY) previously silently flattened any weapon's own Acceleration setting above 6.0 down to exactly 6.0, confirmed (via log evidence) to be erasing a real, intended muzzle-velocity difference between two weapons whose own Acceleration settings (7.0 and 16) both exceeded it.
		float velocity = Math.max(acceleration, 0.0f);
		int cooldownTicks = Math.max(0, (int) toFloat(entries.get("delay"), 0.0f));
		float gravity = Math.abs(toFloat(entries.get("gravity"), 0.0f));
		// GravityInWater - per this record's own doc, defaults to matching
		// plain gravity itself when the key is absent, rather than 0.
		float gravityInWater = Math.abs(toFloat(entries.get("gravityinwater"), gravity));

		// AccelerationInWater/VelocityInWater - torpedo-only (see WeaponType.TORPEDO), used by VehicleProjectileEntity's own underwater cruise logic. Defaults match Readme_Weapon.txt's own documented examples. No upper clamp - per the same direct request as the main velocity field above.
		float accelerationInWater = Math.max(toFloat(entries.get("accelerationinwater"), 4.0f), 0.0f);
		float velocityInWater = Math.max(0.0f, toFloat(entries.get("velocityinwater"), 0.5f));

		// Sound = <name> (extension-less, defaults to "<weaponName>_snd" if not set.
		String soundValue = entries.getOrDefault("sound", weaponName + "_snd").strip().toLowerCase(Locale.ROOT);
		Optional<String> sound = Optional.of(soundValue);
		float soundVolume = toFloat(entries.get("soundvolume"), 1.0f);
		float soundPitch = toFloat(entries.get("soundpitch"), 1.0f);
		float soundPitchRandom = toFloat(entries.get("soundpitchrandom"), 0.0f);
		int soundDelayTicks = Math.max(0, (int) toFloat(entries.get("sounddelay"), 0.0f));

		// ModelBullet = <name> -> models/bullets/<name>.obj + textures/bullets/ <name>.png (see Readme_Weapon.txt).
		String modelBullet = entries.get("modelbullet");
		Optional<Identifier> bulletModel = Optional.empty();
		Optional<Identifier> bulletTexture = Optional.empty();
		if (modelBullet != null) {
			String bulletName = modelBullet.strip().toLowerCase(Locale.ROOT);
			bulletModel = Optional.of(Identifier.of(namespace, "models/obj/bullet_" + bulletName + ".obj"));
			bulletTexture = Optional.of(Identifier.of(namespace, "textures/vehicle/bullet_" + bulletName + ".png"));
		}

		// DisplayName - falls back to the weapon's own file name (capitalized) if not set, rather than leaving it blank on a HUD.
		String displayName = entries.getOrDefault("displayname", capitalize(weaponName));
		// Round = <n> - magazine capacity, 0 (or omitted) = unlimited/no reload cycle at all - see this record's own doc.
		int magazineSize = Math.max(0, (int) toFloat(entries.get("round"), 0.0f));
		// ReloadTime = <ticks>.
		int reloadTicks = Math.max(0, (int) toFloat(entries.get("reloadtime"), 0.0f));
		// MaxAmmo = <n> - total reserve, 0 (or omitted) = unlimited reserve - see this record's own doc.
		int maxAmmo = Math.max(0, (int) toFloat(entries.get("maxammo"), 0.0f));

		// Type = MachineGun1/MachineGun2/Rocket/Bomb/..
		WeaponType weaponType = parseWeaponType(entries.get("type"));
		float explosionPower = toFloat(entries.get("explosion"), 0.0f);
		float explosionPowerInWater = toFloat(entries.get("explosioninwater"), explosionPower);
		// ExplosionBlock - per Readme_Weapon.txt's own doc, this is its OWN
		// independent block-destruction power (not just an on/off flag) -
		// An earlier version of this only checked
		// whether it was greater than 0 and otherwise ignored the actual
		// number entirely, always using the normal Explosion/
		// ExplosionInWater power for the destruction radius regardless of
		// what ExplosionBlock itself said. -1 here means "not set at all
		// in the file" - see VehicleProjectileEntity's own explodeIfConfigured
		// doc for how that's resolved at detonation time (defaults to
		// destroying blocks anyway, using whichever of explosionPower/
		// explosionPowerInWater actually applies, rather than silently
		// having no block destruction just because this line was omitted).
		// An explicitly-set value (even 0, to deliberately disable it) is
		// always used exactly as given.
		String explosionBlockRaw = entries.get("explosionblock");
		float explosionBlockPower = explosionBlockRaw == null ? -1f : toFloat(explosionBlockRaw, 0.0f);
		boolean explosionDestroysBlocks = explosionBlockRaw == null || explosionBlockPower > 0f;
		boolean flaming = "true".equalsIgnoreCase(entries.getOrDefault("flaming", "false").strip());

		// ExplosionAltitude - see this record's own doc. 0 (absent) disables it entirely.
		float explosionAltitude = Math.max(0.0f, toFloat(entries.get("explosionaltitude"), 0.0f));

		// DelayFuse/TimeFuse - see this record's own -1-sentinel doc (0 is a real, explicit "no delay", distinct from the key being absent at all).
		String delayFuseRaw = entries.get("delayfuse");
		int delayFuseTicks = delayFuseRaw == null ? -1 : Math.max(0, (int) toFloat(delayFuseRaw, 0.0f));
		String timeFuseRaw = entries.get("timefuse");
		int timeFuseTicks = timeFuseRaw == null ? -1 : Math.max(0, (int) toFloat(timeFuseRaw, 0.0f));

		// Bound - see this record's own doc.
		float bounceStrength = Math.max(0.0f, toFloat(entries.get("bound"), 0.0f));

		// GuidedTorpedo - Type=Torpedo only, defaults to true (this project's own original, always-guided behavior).
		boolean guidedTorpedo = !"false".equalsIgnoreCase(entries.getOrDefault("guidedtorpedo", "true").strip());

		// Piercing - see this record's own doc.
		int piercingCount = Math.max(0, (int) toFloat(entries.get("piercing"), 0.0f));

		// Accuracy - see this record's own doc.
		float accuracyDegrees = Math.max(0.0f, toFloat(entries.get("accuracy"), 0.0f));

		// FAE - see this record's own doc.
		boolean fuelAirExplosive = "true".equalsIgnoreCase(entries.getOrDefault("fae", "false").strip());

		// BulletColor/BulletColorInWater = Alpha, Red, Green, Blue (0-255 each) - same parsing convention as SmokeColor further below. Opaque white is the default (this project's own original, uncolored bullet trail before these fields existed).
		int bulletColor = parseArgbColor(entries.get("bulletcolor"), 0xFFFFFFFF);
		int bulletColorInWater = parseArgbColor(entries.get("bulletcolorinwater"), bulletColor);

		// Sight = MoveSight/None/MissileSight - see this record's own doc.
		com.example.tudursvehiclemod.asset.SightType sight = parseSightType(entries.get("sight"));

		// LockTime - see this record's own doc.
		int lockTimeTicks = Math.max(0, (int) toFloat(entries.get("locktime"), 0.0f));

		// LockRange - see this record's own doc. 0 (the parsed default whenever the key is absent) means unlimited.
		double lockRange = Math.max(0.0, toFloat(entries.get("lockrange"), 0.0f));

		// LockTimePerBlock - see this record's own doc. Defaults to a non-zero value (distance-scaled lock time is the intended default behavior), not 0.
		double lockTimePerBlock = Math.max(0.0, toFloat(entries.get("locktimeperblock"), LOCK_TIME_PER_BLOCK_DEFAULT));

		// TurnRate - see this record's own doc. Defaults to 3.0 (degrees/tick), this project's own original hardcoded value, whenever the key is absent.
		float turnRateDegreesPerTick = Math.max(0.0f, toFloat(entries.get("turnrate"), MISSILE_TURN_RATE_DEGREES_PER_TICK_DEFAULT));

		// CasTargetMode - see this record's own doc / CasTargetMode's own enum doc. Defaults to BALLISTIC (this project's own current behavior) whenever the key is absent or unrecognized.
		com.example.tudursvehiclemod.asset.CasTargetMode casTargetMode = switch (entries.getOrDefault("castargetmode", "ballistic").strip().toLowerCase(java.util.Locale.ROOT)) {
			case "raycast" -> com.example.tudursvehiclemod.asset.CasTargetMode.RAYCAST;
			case "collision" -> com.example.tudursvehiclemod.asset.CasTargetMode.COLLISION;
			default -> com.example.tudursvehiclemod.asset.CasTargetMode.BALLISTIC;
		};

		// CasAttackStartAltitude/CasAttackStopAltitude - see this record's own doc for the full reasoning. Defaults to 100.0/40.0 whenever the key is absent, matching this project's own current behavior.
		float casAttackStartAltitude = toFloat(entries.get("casattackstartaltitude"), 200.0f);
		float casAttackStopAltitude = toFloat(entries.get("casattackstopaltitude"), 40.0f);

		// RidableOnly - see this record's own doc (informational only in this project - every weapon here is already vehicle-mounted).
		boolean ridableOnly = !"false".equalsIgnoreCase(entries.getOrDefault("ridableonly", "true").strip());

		// ProximityFuseDist - see this record's own doc.
		float proximityFuseDist = Math.max(0.0f, toFloat(entries.get("proximityfusedist"), 0.0f));

		// RigidityTime - Readme_Weapon.txt's own documented default is 7 when this key is absent entirely.
		int rigidityTimeTicks = Math.max(0, (int) toFloat(entries.get("rigiditytime"), 7.0f));

		// Group - see this record's own doc. Case-sensitive, exact-match grouping, per Readme_Weapon.txt's own example.
		Optional<String> group = Optional.ofNullable(entries.get("group")).map(String::strip).filter(s -> !s.isEmpty());

		// ModeNum - see this record's own doc. Readme_Weapon.txt's own documented valid range is 1 or 2; anything else typo'd in the file just clamps into that same range rather than producing some invalid third mode count.
		int modeNum = Math.max(1, Math.min(2, (int) toFloat(entries.get("modenum"), 1.0f)));

		// TrajectoryParticle/TrajectoryParticleStartTick - see this record's own doc.
		Optional<String> trajectoryParticle = Optional.ofNullable(entries.get("trajectoryparticle"))
				.map(s -> s.strip().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty() && !s.equals("none"));
		int trajectoryParticleStartTick = Math.max(0, (int) toFloat(entries.get("trajectoryparticlestarttick"), 0.0f));

		// DisableSmoke - see this record's own doc.
		boolean disableSmoke = "true".equalsIgnoreCase(entries.getOrDefault("disablesmoke", "false").strip());

		// AddMuzzleFlash = distance, size, displayTicks, A, R, G, B - see MuzzleFlashConfig's own doc.
		Optional<com.example.tudursvehiclemod.asset.MuzzleFlashConfig> muzzleFlash = Optional.empty();
		if (entries.containsKey("addmuzzleflash")) {
			String[] parts = entries.get("addmuzzleflash").split(",");
			float distance = parts.length > 0 ? toFloat(parts[0], 0.5f) : 0.5f;
			float size = parts.length > 1 ? toFloat(parts[1], 0.2f) : 0.2f;
			int displayTicks = parts.length > 2 ? (int) toFloat(parts[2], 1.0f) : 1;
			int alpha = parts.length > 3 ? (int) toFloat(parts[3], 150f) : 150;
			int red = parts.length > 4 ? (int) toFloat(parts[4], 254f) : 254;
			int green = parts.length > 5 ? (int) toFloat(parts[5], 219f) : 219;
			int blue = parts.length > 6 ? (int) toFloat(parts[6], 184f) : 184;
			int color = (clampByte(alpha) << 24) | (clampByte(red) << 16) | (clampByte(green) << 8) | clampByte(blue);
			muzzleFlash = Optional.of(new com.example.tudursvehiclemod.asset.MuzzleFlashConfig(distance, size, Math.max(1, displayTicks), color));
		}

		// AddMuzzleFlashSmoke = distance, count, size, range, displayTicks, A, R, G, B - see MuzzleFlashSmokeConfig's own doc.
		Optional<com.example.tudursvehiclemod.asset.MuzzleFlashSmokeConfig> muzzleFlashSmoke = Optional.empty();
		if (entries.containsKey("addmuzzleflashsmoke")) {
			String[] parts = entries.get("addmuzzleflashsmoke").split(",");
			float distance = parts.length > 0 ? toFloat(parts[0], 2.2f) : 2.2f;
			int count = parts.length > 1 ? Math.max(1, (int) toFloat(parts[1], 1.0f)) : 1;
			float size = parts.length > 2 ? toFloat(parts[2], 5.0f) : 5.0f;
			float range = parts.length > 3 ? toFloat(parts[3], 2.0f) : 2.0f;
			int displayTicks = parts.length > 4 ? (int) toFloat(parts[4], 15.0f) : 15;
			int alpha = parts.length > 5 ? (int) toFloat(parts[5], 180f) : 180;
			int red = parts.length > 6 ? (int) toFloat(parts[6], 250f) : 250;
			int green = parts.length > 7 ? (int) toFloat(parts[7], 245f) : 245;
			int blue = parts.length > 8 ? (int) toFloat(parts[8], 240f) : 240;
			int color = (clampByte(alpha) << 24) | (clampByte(red) << 16) | (clampByte(green) << 8) | clampByte(blue);
			muzzleFlashSmoke = Optional.of(new com.example.tudursvehiclemod.asset.MuzzleFlashSmokeConfig(
					distance, count, size, range, Math.max(1, displayTicks), color));
		}

		// SetCartridge = modelName, acceleration, yaw, pitch, modelScale, gravity, bound - see CartridgeConfig's own doc.
		Optional<com.example.tudursvehiclemod.asset.CartridgeConfig> cartridge = Optional.empty();
		if (entries.containsKey("setcartridge")) {
			String[] parts = entries.get("setcartridge").split(",");
			String modelName = parts.length > 0 ? parts[0].strip().toLowerCase(Locale.ROOT) : "cartridge";
			float cartAcceleration = parts.length > 1 ? toFloat(parts[1], 0.0f) : 0.0f;
			float cartYaw = parts.length > 2 ? toFloat(parts[2], 0.0f) : 0.0f;
			float cartPitch = parts.length > 3 ? toFloat(parts[3], 0.0f) : 0.0f;
			float cartScale = parts.length > 4 ? toFloat(parts[4], 1.0f) : 1.0f;
			// An ejected cartridge was accelerating
			// upward indefinitely instead of falling: MC Heli's own
			// documented example for this exact field uses a NEGATIVE
			// value (-0.04, "downward" expressed as negative) - same
			// convention as the main weapon's own plain Gravity directive
			// (see this file's own gravity parsing just above, which
			// ALREADY wraps its own raw value in Math.abs() for exactly
			// this reason) - but this project's own gravity field expects
			// a positive MAGNITUDE instead (Entity's own applyGravity()
			// subtracts it from velocity.y directly, so a raw negative
			// value fed straight through, unlike the main Gravity field,
			// ADDS to velocity.y every tick instead of subtracting -
			// continuous upward acceleration, exactly the reported
			// symptom). Wrapped in the same Math.abs() here now, to match.
			float cartGravity = Math.abs(parts.length > 5 ? toFloat(parts[5], -0.04f) : -0.04f);
			float cartBounce = parts.length > 6 ? toFloat(parts[6], 0.4f) : 0.4f;
			if (!modelName.isEmpty()) {
				cartridge = Optional.of(new com.example.tudursvehiclemod.asset.CartridgeConfig(
						modelName, cartAcceleration, cartYaw, cartPitch, cartScale, cartGravity, cartBounce));
			}
		}

		// Recoil - see this record's own doc.
		float recoil = Math.max(0.0f, toFloat(entries.get("recoil"), 0.0f));

		// RecoilBufCount = recoilDurationTicks, recessionRateMultiplier - see this record's own doc.
		int recoilDurationTicks = 40;
		int recoilRecessionRateMultiplier = 5;
		if (entries.containsKey("recoilbufcount")) {
			String[] parts = entries.get("recoilbufcount").split(",");
			recoilDurationTicks = Math.max(1, parts.length > 0 ? (int) toFloat(parts[0], 40.0f) : 40);
			recoilRecessionRateMultiplier = Math.max(1, parts.length > 1 ? (int) toFloat(parts[1], 5.0f) : 5);
		}

		// Destruct - see this record's own doc.
		boolean destruct = "true".equalsIgnoreCase(entries.getOrDefault("destruct", "false").strip());
		// CameraRotationSpeedPitch - see WeaponStats's own doc - 1.0 (unchanged) if the key is absent.
		float cameraRotationSpeedPitch = toFloat(entries.get("camerarotationspeedpitch"), 1.0f);
		// FixCameraPitch - see WeaponStats's own doc.
		boolean fixCameraPitch = "true".equalsIgnoreCase(entries.getOrDefault("fixcamerapitch", "false").strip());
		// DisplayMortarDistance - see WeaponStats's own doc.
		boolean displayMortarDistance = "true".equalsIgnoreCase(entries.getOrDefault("displaymortardistance", "false").strip());
		// UsableWhileDiving - this project's own new addition, letting a weapon of any Type (not just the hardcoded Torpedo default - see SubmarineEntity's own tudursvehiclemod$canFireWeapons(Optional) doc) be usable while a submarine hull is submerged/diving. false (the default, key absent) preserves the original Torpedo-only behavior for every other weapon type.
		boolean usableWhileDiving = "true".equalsIgnoreCase(entries.getOrDefault("usablewhilediving", "false").strip());
		// FuelPerAmmo - see WeaponStats' own doc. 0.0f (the default, key absent) means no fuel bonus at all.
		float fuelPerAmmo = toFloat(entries.get("fuelperammo"), 0.0f);
		// HeatCount/MaxHeatCount.
		float heatPerShot = toFloat(entries.get("heatcount"), 0.0f);
		float maxHeat = toFloat(entries.get("maxheatcount"), 0.0f);

		// SuppliedNum/Item.
		int suppliedNum = Math.max(0, (int) toFloat(entries.get("suppliednum"), 0.0f));
		int resupplyIronIngotCost = 0;
		int resupplyGunpowderCost = 0;
		int resupplyRedstoneCost = 0;
		for (String itemLine : itemLines) {
			String[] parts = itemLine.split(",", 2);
			if (parts.length != 2) {
				continue;
			}
			int count = Math.max(0, (int) toFloat(parts[0].strip(), 0.0f));
			String itemName = parts[1].strip().toLowerCase(Locale.ROOT);
			switch (itemName) {
				case "iron_ingot" -> resupplyIronIngotCost = count;
				case "gunpowder" -> resupplyGunpowderCost = count;
				case "redstone" -> resupplyRedstoneCost = count;
				default -> { /* MC Heli itself only allows these 3 - anything else is ignored */ }
			}
		}

		// Bomblet/BombletSTime/BombletDiff/ModelBomblet - see Readme_Weapon.txt's own doc ("使用後、子弾が展開する数。クラスター爆弾などに使用" - how many child submunitions deploy after use, for cluster bombs and similar).
		int bombletCount = Math.max(0, (int) toFloat(entries.get("bomblet"), 0.0f));
		int bombletDeployTicks = Math.max(0, (int) toFloat(entries.get("bombletstime"), 0.0f));
		float bombletSpreadRate = Math.max(0.0f, toFloat(entries.get("bombletdiff"), 0.7f));
		String modelBomblet = entries.get("modelbomblet");
		Optional<Identifier> bombletModel = Optional.empty();
		Optional<Identifier> bombletTexture = Optional.empty();
		if (modelBomblet != null) {
			String bombletModelName = modelBomblet.strip().toLowerCase(Locale.ROOT);
			bombletModel = Optional.of(Identifier.of(namespace, "models/obj/bullet_" + bombletModelName + ".obj"));
			bombletTexture = Optional.of(Identifier.of(namespace, "textures/vehicle/bullet_" + bombletModelName + ".png"));
		}

		// TargetDepth - not an MC Heli-native directive, this project's own new addition for a torpedo's own target cruise depth (how far below the water surface it aims for) - 2.0 default ("水面Y-2").
		float targetDepthOffset = toFloat(entries.get("targetdepth"), 2.0f);
		// DiveDistance - Type=ASWeapon only, see WeaponStats's own doc - not an MC Heli-native directive, this project's own new addition. 10.0 default.
		float diveDistance = toFloat(entries.get("divedistance"), 10.0f);
		// RotationSpeed - see WeaponStats's own doc - 15.0 (rotations/second) default, since MC Heli's own Readme doesn't document a concrete number for AddPartRotWeapon's own spin rate at all. was 30.0, confirmed mathematically correct (30 rotations/sec, not a double-converted value) but visually excessive - 30 full rotations/second is 1.5 rotations EVERY SINGLE GAME TICK, which at typical render framerates (well under 30x the tick rate) aliases into a chaotic-looking blur rather than a smooth spin, regardless of the underlying math being "correct" - 15.0 is a calmer default while still reading as a fast-spinning gatling barrel.
		float rotationSpeedPerSecond = toFloat(entries.get("rotationspeed"), 15.0f);

		// DispenseItem/DispenseRange - Type=Dispenser only - see WeaponStats's own doc.
		Optional<String> dispenseItem = entries.containsKey("dispenseitem")
				? Optional.of(entries.get("dispenseitem").strip().toLowerCase(Locale.ROOT))
				: Optional.empty();
		float dispenseRange = Math.max(0.0f, toFloat(entries.get("dispenserange"), 4.0f));

		// SmokeColor = Alpha, Red, Green, Blue (0-255 each) - Type=Smoke only. Packed into 0xAARRGGBB; any component missing/unparseable falls back to that channel's own share of the same default (230, 200, 20, 80) WeaponStats's own doc documents.
		int smokeColor = 0xE6C81450;
		if (entries.containsKey("smokecolor")) {
			String[] smokeColorParts = entries.get("smokecolor").split(",");
			int alpha = smokeColorParts.length > 0 ? (int) toFloat(smokeColorParts[0], 230f) : 230;
			int red = smokeColorParts.length > 1 ? (int) toFloat(smokeColorParts[1], 200f) : 200;
			int green = smokeColorParts.length > 2 ? (int) toFloat(smokeColorParts[2], 20f) : 20;
			int blue = smokeColorParts.length > 3 ? (int) toFloat(smokeColorParts[3], 80f) : 80;
			smokeColor = ((clampByte(alpha)) << 24) | (clampByte(red) << 16) | (clampByte(green) << 8) | clampByte(blue);
		}
		float smokeSize = Math.max(0.0f, toFloat(entries.get("smokesize"), 2.0f));
		int smokeMaxAge = Math.max(1, (int) toFloat(entries.get("smokemaxage"), 500f));

		// Target = monsters/others/.. - Type=TargetingPod only - see WeaponStats's own doc.
		java.util.Set<String> targetingPodTargets = java.util.Set.of();
		if (entries.containsKey("target")) {
			java.util.Set<String> parsedTargets = new java.util.HashSet<>();
			for (String token : entries.get("target").split("/")) {
				String trimmed = token.strip().toLowerCase(Locale.ROOT);
				if (!trimmed.isEmpty()) {
					parsedTargets.add(trimmed);
				}
			}
			targetingPodTargets = java.util.Set.copyOf(parsedTargets);
		}
		float targetingPodLength = Math.max(0.0f, toFloat(entries.get("length"), 100.0f));
		float targetingPodRadius = Math.max(0.0f, toFloat(entries.get("radius"), 45.0f));
		float targetingPodMarkTimeSeconds = Math.max(0.0f, toFloat(entries.get("marktime"), 10.0f));

		// CasAircraft/CasWeaponIndex/CasAccuracy/repeated CasWaypoint - see CasStrikeConfig's own doc. Only actually built if CasAircraft itself is present (an otherwise-unconfigured CAS weapon just does nothing when fired, same "unconfigured falls back to inert" convention as every other Type-specific config block here).
		Optional<CasStrikeConfig> casStrike = Optional.empty();
		String casAircraftValue = entries.get("casaircraft");
		if (casAircraftValue != null && !casAircraftValue.isBlank()) {
			String aircraftFileName = casAircraftValue.strip();
			int casWeaponIndex = Math.max(0, (int) toFloat(entries.get("casweaponindex"), 0.0f));
			// This is the spawned aircraft's OWN navigation imprecision (blocks of random deviation applied to its own route - see AbstractVehicleEntity's own tudursvehiclemod$fireCasStrike() doc), NOT weapon accuracy at all (that's the aircraft's own weaponIndex weapon's own separate, standard Accuracy field, already applied automatically since auto-fire reuses tryFireWeapon() directly).
			float casAccuracy = Math.max(0f, toFloat(entries.get("casaccuracy"), 0f));
			// CasTimeout - a hard safety-net despawn timer, in SECONDS (60 = 1 minute default) - converted to ticks here.
			int casTimeoutTicks = Math.max(1, net.minecraft.util.math.MathHelper.ceil(toFloat(entries.get("castimeout"), 60.0f) * 20f));
			// CasStuckTimeout - despawns if this aircraft hasn't advanced to a new waypoint in this many seconds (60 = 1 minute default) - converted to ticks here.
			int casStuckTimeoutTicks = Math.max(1, net.minecraft.util.math.MathHelper.ceil(toFloat(entries.get("casstucktimeout"), 60.0f) * 20f));
			// CasYawOffset - an additional, manually-tunable correction (degrees) the user can dial in directly by trial and error, without needing the exact root cause pinned down first.
			float casYawOffset = toFloat(entries.get("casyawoffset"), 0.0f);
			// CasFormationSize/CasFormationType/CasFormationSpacing - see CasStrikeConfig's own doc. 1 (the default, keys absent) means no formation at all.
			int casFormationSize = Math.max(1, (int) toFloat(entries.get("casformationsize"), 1.0f));
			FormationType casFormationType = parseFormationType(entries.get("casformationtype"));
			double casFormationSpacing = Math.max(0.5, toFloat(entries.get("casformationspacing"), 8.0f));
			// CasFormationElementSpacing - see CasStrikeConfig's own doc. Absent (the default) is represented as -1.0, meaning "auto-derive" - never itself a valid spacing value, since Math.max(0.5,..) is applied whenever the raw key IS actually present.
			double casElementSpacing = entries.containsKey("casformationelementspacing")
					? Math.max(0.5, toFloat(entries.get("casformationelementspacing"), 0f)) : -1.0;
			java.util.List<CasWaypoint> casWaypoints = new java.util.ArrayList<>();
			for (String waypointLine : casWaypointLines) {
				CasWaypoint parsed = CasWaypoint.tudursvehiclemod$parse(waypointLine);
				if (parsed != null) {
					casWaypoints.add(parsed);
				} else {
					LOGGER.warn("[tudursvehiclemod] Weapon '{}' has a malformed CasWaypoint line: '{}'", weaponName, waypointLine);
				}
			}
			if (!casWaypoints.isEmpty()) {
				casStrike = Optional.of(new CasStrikeConfig(aircraftFileName, casWeaponIndex, casAccuracy, casTimeoutTicks, casStuckTimeoutTicks, casYawOffset, casWaypoints,
						casFormationSize, casFormationType, casFormationSpacing, casElementSpacing));
			} else {
				LOGGER.warn("[tudursvehiclemod] Weapon '{}' sets CasAircraft but has no valid CasWaypoint lines - CAS strike left unconfigured", weaponName);
			}
		}

		// CarrierAircraft/CarrierWeaponIndex/CarrierAccuracy/repeated CarrierWaypoint - see CarrierAircraftConfig's own doc. Same parsing shape as CasAircraft's own, just under a "Carrier" prefix.
		Optional<CarrierAircraftConfig> carrierAircraft = Optional.empty();
		String carrierAircraftValue = entries.get("carrieraircraft");
		if (carrierAircraftValue != null && !carrierAircraftValue.isBlank()) {
			String aircraftFileName = carrierAircraftValue.strip();
			int carrierWeaponIndex = Math.max(0, (int) toFloat(entries.get("carrierweaponindex"), 0.0f));
			float carrierAccuracy = Math.max(0f, toFloat(entries.get("carrieraccuracy"), 0f));
			int carrierTimeoutTicks = Math.max(1, net.minecraft.util.math.MathHelper.ceil(toFloat(entries.get("carriertimeout"), 60.0f) * 20f));
			int carrierStuckTimeoutTicks = Math.max(1, net.minecraft.util.math.MathHelper.ceil(toFloat(entries.get("carrierstucktimeout"), 60.0f) * 20f));
			float carrierYawOffset = toFloat(entries.get("carrieryawoffset"), 0.0f);
			java.util.List<CasWaypoint> carrierWaypoints = new java.util.ArrayList<>();
			for (String waypointLine : carrierWaypointLines) {
				CasWaypoint parsed = CasWaypoint.tudursvehiclemod$parse(waypointLine);
				if (parsed != null) {
					carrierWaypoints.add(parsed);
				} else {
					LOGGER.warn("[tudursvehiclemod] Weapon '{}' has a malformed CarrierWaypoint line: '{}'", weaponName, waypointLine);
				}
			}
			java.util.List<CarrierLaunchWaypoint> carrierLaunchWaypoints = new java.util.ArrayList<>();
			for (String waypointLine : carrierLaunchWaypointLines) {
				CarrierLaunchWaypoint parsed = CarrierLaunchWaypoint.tudursvehiclemod$parse(waypointLine);
				if (parsed != null) {
					carrierLaunchWaypoints.add(parsed);
				} else {
					LOGGER.warn("[tudursvehiclemod] Weapon '{}' has a malformed CarrierLaunchWaypoint line: '{}'", weaponName, waypointLine);
				}
			}
			java.util.List<CarrierLaunchWaypoint> carrierLandingWaypoints = new java.util.ArrayList<>();
			for (String waypointLine : carrierLandingWaypointLines) {
				CarrierLaunchWaypoint parsed = CarrierLaunchWaypoint.tudursvehiclemod$parse(waypointLine);
				if (parsed != null) {
					carrierLandingWaypoints.add(parsed);
				} else {
					LOGGER.warn("[tudursvehiclemod] Weapon '{}' has a malformed CarrierLandingWaypoint line: '{}'", weaponName, waypointLine);
				}
			}
			if (!carrierWaypoints.isEmpty() && !carrierLandingWaypoints.isEmpty()) {
				float carrierLandingYawOffset = toFloat(entries.get("carrierlandingyawoffset"), 0.0f);
				float carrierTargetYawOffset = toFloat(entries.get("carriertargetyawoffset"), 0.0f);
				double carrierLandingToAmmoRadius = toFloat(entries.get("carrierlandingtoammoradius"), 15.0f);
				// CarrierFormationSize/CarrierFormationType/CarrierFormationSpacing - see CarrierAircraftConfig's own doc. 1 (the default, keys absent) means no formation at all.
				int carrierFormationSize = Math.max(1, (int) toFloat(entries.get("carrierformationsize"), 1.0f));
				FormationType carrierFormationType = parseFormationType(entries.get("carrierformationtype"));
				double carrierFormationSpacing = Math.max(0.5, toFloat(entries.get("carrierformationspacing"), 8.0f));
				// CarrierFormationElementSpacing - see CarrierAircraftConfig's own doc. Absent (the default) is represented as -1.0, meaning "auto-derive" - mirrors CAS's own equivalent field exactly.
				double carrierElementSpacing = entries.containsKey("carrierformationelementspacing")
						? Math.max(0.5, toFloat(entries.get("carrierformationelementspacing"), 0f)) : -1.0;
				java.util.List<CarrierAircraftConfig.CarrierRecoveryPointConfig> carrierRecoveryPoints = new java.util.ArrayList<>();
				for (String recoveryPointLine : carrierRecoveryPointLines) {
					CarrierAircraftConfig.CarrierRecoveryPointConfig parsed = CarrierAircraftConfig.CarrierRecoveryPointConfig.tudursvehiclemod$parse(recoveryPointLine);
					if (parsed != null) {
						carrierRecoveryPoints.add(parsed);
					} else {
						LOGGER.warn("[tudursvehiclemod] Weapon '{}' has a malformed CarrierRecoveryPoint line: '{}'", weaponName, recoveryPointLine);
					}
				}
				carrierAircraft = Optional.of(new CarrierAircraftConfig(aircraftFileName, carrierWeaponIndex, carrierAccuracy, carrierTimeoutTicks, carrierStuckTimeoutTicks, carrierYawOffset, carrierLandingYawOffset, carrierTargetYawOffset, carrierLaunchWaypoints, carrierWaypoints, carrierLandingToAmmoRadius, carrierRecoveryPoints, carrierLandingWaypoints,
						carrierFormationSize, carrierFormationType, carrierFormationSpacing, carrierElementSpacing));
			} else if (carrierWaypoints.isEmpty()) {
				LOGGER.warn("[tudursvehiclemod] Weapon '{}' sets CarrierAircraft but has no valid CarrierWaypoint lines - Carrier launch left unconfigured", weaponName);
			} else {
				LOGGER.warn("[tudursvehiclemod] Weapon '{}' sets CarrierAircraft but has no valid CarrierLandingWaypoint lines (at least one is required) - Carrier launch left unconfigured", weaponName);
			}
		}

		return new WeaponStats(damage, velocity, cooldownTicks, gravity, sound, soundVolume, soundPitch,
				soundPitchRandom, soundDelayTicks, bulletModel, bulletTexture, 1.0f,
				displayName, magazineSize, reloadTicks, maxAmmo, weaponType, explosionPower, explosionDestroysBlocks, flaming,
				heatPerShot, maxHeat, suppliedNum, resupplyIronIngotCost, resupplyGunpowderCost, resupplyRedstoneCost,
				accelerationInWater, velocityInWater, explosionPowerInWater, explosionBlockPower,
				bombletCount, bombletDeployTicks, bombletSpreadRate, bombletModel, bombletTexture, targetDepthOffset, diveDistance,
				rotationSpeedPerSecond, dispenseItem, dispenseRange, smokeColor, smokeSize, smokeMaxAge,
				targetingPodTargets, targetingPodLength, targetingPodRadius, targetingPodMarkTimeSeconds,
				explosionAltitude, delayFuseTicks, timeFuseTicks, bounceStrength, gravityInWater, guidedTorpedo,
				piercingCount, accuracyDegrees, fuelAirExplosive, bulletColor, bulletColorInWater,
				sight, lockTimeTicks, lockRange, lockTimePerBlock, turnRateDegreesPerTick, casTargetMode, casAttackStartAltitude, casAttackStopAltitude, ridableOnly, proximityFuseDist, rigidityTimeTicks, group, modeNum,
				trajectoryParticle, trajectoryParticleStartTick, disableSmoke, muzzleFlash, muzzleFlashSmoke, cartridge,
				recoil, recoilDurationTicks, recoilRecessionRateMultiplier, destruct,
				cameraRotationSpeedPitch, fixCameraPitch, displayMortarDistance, casStrike, carrierAircraft, usableWhileDiving,
				fuelPerAmmo);
	}

	/** Clamps a 0-255 color channel value read as a plain float/int - out-of-range input (e.g. a typo'd 300) is clamped rather than silently wrapping/overflowing into a completely different color via a raw (byte) cast. */
	private static int clampByte(int value) {
		return Math.max(0, Math.min(255, value));
	}

	/** Parses an "Alpha, Red, Green, Blue" (0-255 each) comma-separated
	 * directive value into a single packed ARGB int - same convention
	 * SmokeColor's own inline parsing already used, extracted here so
	 * BulletColor/BulletColorInWater (and any future color directive)
	 * can share it instead of duplicating the same four-way split/clamp
	 * logic again. rawValue is the raw entries.get(..) result (null if
	 * the key was absent from the file at all), fallback is used
	 * wholesale in that case. */
	private static int parseArgbColor(String rawValue, int fallback) {
		if (rawValue == null) {
			return fallback;
		}
		String[] parts = rawValue.split(",");
		int alpha = parts.length > 0 ? (int) toFloat(parts[0], 255f) : 255;
		int red = parts.length > 1 ? (int) toFloat(parts[1], 255f) : 255;
		int green = parts.length > 2 ? (int) toFloat(parts[2], 255f) : 255;
		int blue = parts.length > 3 ? (int) toFloat(parts[3], 255f) : 255;
		return (clampByte(alpha) << 24) | (clampByte(red) << 16) | (clampByte(green) << 8) | clampByte(blue);
	}

	/** Maps MC Heli's own Type directive (see Readme_Weapon.txt's own documented list) down to WeaponType. */
	/** Maps a CasFormationType/CarrierFormationType directive down to FormationType - see that enum's own doc. LINE_ABREAST (the default) if absent/unrecognized. */
	private static com.example.tudursvehiclemod.asset.FormationType parseFormationType(String rawType) {
		if (rawType == null) {
			return com.example.tudursvehiclemod.asset.FormationType.LINE_ABREAST;
		}
		return switch (rawType.strip().toLowerCase(Locale.ROOT)) {
			case "lineabreast" -> com.example.tudursvehiclemod.asset.FormationType.LINE_ABREAST;
			case "lineastern" -> com.example.tudursvehiclemod.asset.FormationType.LINE_ASTERN;
			case "vformation" -> com.example.tudursvehiclemod.asset.FormationType.V_FORMATION;
			case "diamond" -> com.example.tudursvehiclemod.asset.FormationType.DIAMOND;
			case "delta" -> com.example.tudursvehiclemod.asset.FormationType.DELTA;
			default -> com.example.tudursvehiclemod.asset.FormationType.LINE_ABREAST;
		};
	}

	private static WeaponType parseWeaponType(String rawType) {
		if (rawType == null) {
			return WeaponType.OTHER;
		}
		return switch (rawType.strip().toLowerCase(Locale.ROOT)) {
			case "machinegun1", "machinegun2" -> WeaponType.MACHINE_GUN;
			case "rocket" -> WeaponType.ROCKET;
			case "bomb" -> WeaponType.BOMB;
			// A new, project-specific type (not from MC Heli's own format) - behaves like BOMB except it doesn't explode on water contact (see WeaponType.DEPTH's own doc).
			case "depth" -> WeaponType.DEPTH;
			case "torpedo" -> WeaponType.TORPEDO;
			case "asmissile" -> WeaponType.AS_MISSILE;
			// A new, project-specific Type (not from MC Heli's own documented list) - see WeaponType.AS_WEAPON's own doc.
			case "asweapon" -> WeaponType.AS_WEAPON;
			case "aamissile" -> WeaponType.AA_MISSILE;
			case "atmissile" -> WeaponType.AT_MISSILE;
			// A new, project-specific Type (not from MC Heli's own documented list) - see WeaponType.MISSILE's own doc.
			case "missile" -> WeaponType.MISSILE;
			case "tvmissile" -> WeaponType.TV_MISSILE;
			case "mkrocket" -> WeaponType.MK_ROCKET;
			case "dispenser" -> WeaponType.DISPENSER;
			case "smoke" -> WeaponType.SMOKE;
			case "dummy" -> WeaponType.DUMMY;
			case "targetingpod" -> WeaponType.TARGETING_POD;
			case "cas" -> WeaponType.CAS;
			case "carrier" -> WeaponType.CARRIER;
			// A new, project-specific Type (not from MC Heli's own documented list) - see WeaponType.DROP_TANK's own doc.
			case "droptank" -> WeaponType.DROP_TANK;
			default -> WeaponType.OTHER;
		};
	}

	/** Maps MC Heli's own Sight directive down to SightType - see that enum's own doc. */
	private static com.example.tudursvehiclemod.asset.SightType parseSightType(String rawSight) {
		if (rawSight == null) {
			return com.example.tudursvehiclemod.asset.SightType.MOVE_SIGHT;
		}
		return switch (rawSight.strip().toLowerCase(Locale.ROOT)) {
			case "none" -> com.example.tudursvehiclemod.asset.SightType.NONE;
			case "missilesight" -> com.example.tudursvehiclemod.asset.SightType.MISSILE_SIGHT;
			default -> com.example.tudursvehiclemod.asset.SightType.MOVE_SIGHT;
		};
	}

	private static String capitalize(String name) {
		if (name.isEmpty()) {
			return name;
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static float toFloat(String value, float fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
