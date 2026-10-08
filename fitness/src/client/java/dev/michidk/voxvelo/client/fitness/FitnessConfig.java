package dev.michidk.voxvelo.client.fitness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.michidk.voxelfitness.client.FitnessRuntime;
import dev.michidk.voxvelo.client.ftms.TrainerLimits;
import dev.michidk.voxvelo.client.obc.ObcMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/** Settings of the fitness integration, stored as JSON in config/voxvelo-fitness.json. */
public final class FitnessConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** The version written by this build; files without one are older than version numbers. */
	private static final int CURRENT_VERSION = 1;

	/**
	 * What counts as road unless the player changes it: smooth stone and its slab (what the track command builds when it
	 * is given no material, slabs on its half-block steps), plus stone and gravel for roads made by hand.
	 */
	public static final List<String> DEFAULT_ROAD_BLOCKS = List.of(
		"minecraft:smooth_stone", "minecraft:smooth_stone_slab", "minecraft:stone", "minecraft:gravel");
	/** The default before the track's own blocks were added; a list still equal to it was never edited. */
	private static final List<String> OLD_DEFAULT_ROAD_BLOCKS = List.of("minecraft:stone", "minecraft:gravel");

	/** Layout version of the file, so later builds know which migrations it still needs. */
	public int configVersion = CURRENT_VERSION;

	/** Stable Bluetooth id and display name of the trainer to reconnect to. Empty when none is chosen. */
	public String preferredTrainerId = "";
	public String preferredTrainerName = "";
	public boolean autoConnectTrainer = true;
	/** A separate Cycling Power Service sensor whose watts take precedence over trainer-reported watts. */
	public String preferredPowerMeterId = "";
	public String preferredPowerMeterName = "";
	public boolean autoConnectPowerMeter = true;
	/** A heart rate sensor (chest strap, armband, watch) whose bpm takes precedence over the one the trainer relays. */
	public String preferredHeartRateId = "";
	public String preferredHeartRateName = "";
	public boolean autoConnectHeartRate = true;

	/** Which OpenBikeControl transports are used. */
	public ObcMode obcMode = ObcMode.BOTH;
	public String obcPreferredKey = "";
	public String obcPreferredName = "";
	public boolean obcAutoConnect = true;

	/** Send terrain gradient and surface to the trainer as resistance. Intensity 1.0 matches the game physics. */
	public boolean trainerResistanceEnabled = true;
	public double trainerResistanceIntensity = TrainerLimits.DEFAULT_INTENSITY;
	public boolean virtualShiftingEnabled = true;
	/** Physical chainring teeth divided by rear cog teeth; keep the real bike in this gear. */
	public double trainerGearRatio = TrainerLimits.DEFAULT_GEAR_RATIO;
	public double trainerWheelCircumferenceM = TrainerLimits.DEFAULT_WHEEL_CIRCUMFERENCE_M;

	/** Show other riders watts and heart rate on screen, and whether this player shares theirs with the others. */
	public boolean statsOverlayEnabled = true;
	public boolean shareWatts = true;
	public boolean shareHeartRate = true;

	/** The riding panel with power, cadence, speed, grade and gear. */
	public boolean hudEnabled = true;
	public boolean heartRateHudEnabled = true;
	public boolean junctionHudEnabled = true;

	/** Steer the bike along the road by itself. It takes over the steering whenever a road is found ahead. */
	public boolean roadFollowEnabled = false;
	/** Blocks that count as road for the road-follow steering source: block ids, or tags written as #namespace:tag. */
	public List<String> roadBlocks = new ArrayList<>(DEFAULT_ROAD_BLOCKS);
	/** Widest road the follower will centre on, in blocks each side of the bike. Wider areas count as open ground. */
	public int roadMaxHalfWidth = 8;

	/** Ask left or right at junctions while road following, and the least time between two questions. */
	public boolean junctionPrompt = true;
	public int junctionCooldownSeconds = 30;

	/** Blocks each way (behind and ahead) over which the slope for the trainer and HUD is measured. */
	public int slopeWindowBlocks = 10;

	/**
	 * Whether exported FIT files carry GPS positions, and where on the globe a ride starts. The default origin is open
	 * ocean in the mid Atlantic, so a ride is clearly virtual and never lands on somebody's real street.
	 */
	public boolean rideFitPosition = true;
	public double rideFitOriginLat = 30.0;
	public double rideFitOriginLon = -40.0;

	public boolean hasPreferredTrainer() {
		return !this.preferredTrainerId.isEmpty();
	}

	public boolean hasPreferredPowerMeter() {
		return !this.preferredPowerMeterId.isEmpty();
	}

	public boolean hasPreferredHeartRate() {
		return !this.preferredHeartRateId.isEmpty();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("voxvelo-fitness.json");
	}

	/**
	 * Loads the settings. Before the fitness integration had its own file these lived in voxvelo-client.json, so
	 * without a fitness file the old one is read instead (unknown fields, such as the core ones, are ignored).
	 * An unreadable fitness file is kept as voxvelo-fitness.json.bak before the defaults replace it.
	 */
	public static FitnessConfig load() {
		Path file = file();
		boolean own = Files.isRegularFile(file);
		Path source = own ? file : FabricLoader.getInstance().getConfigDir().resolve("voxvelo-client.json");
		if (Files.isRegularFile(source)) {
			try {
				JsonObject json = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
				FitnessConfig loaded = GSON.fromJson(json, FitnessConfig.class);
				if (loaded != null) {
					// The core file numbers its own versions; fitness settings found there predate fitness versions.
					int version = own && json.has("configVersion") ? json.get("configVersion").getAsInt() : 0;
					loaded.migrate(json, version);
					loaded.sanitize();
					return loaded;
				}
			} catch (Exception e) {
				FitnessRuntime.LOGGER.warn("Could not read {}, using defaults", source, e);
				if (own) {
					backup(file);
				}
			}
		}
		return new FitnessConfig();
	}

	/** Keeps an unreadable file next to the defaults that are about to replace it, so hand edits are not lost. */
	private static void backup(Path file) {
		Path backup = file.resolveSibling(file.getFileName() + ".bak");
		try {
			Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
			FitnessRuntime.LOGGER.warn("Kept the unreadable settings as {}", backup);
		} catch (IOException e) {
			FitnessRuntime.LOGGER.warn("Could not keep a copy of {}", file, e);
		}
	}

	/** Brings a file written by an older build up to date. Each step runs for files older than the version it came with. */
	private void migrate(JsonObject json, int version) {
		if (version < 1) {
			// Road following used to be picked as the steering source; a file that still says so keeps it on.
			JsonElement oldSteering = json.get("steeringSource");
			if (!json.has("roadFollowEnabled") && oldSteering != null && oldSteering.isJsonPrimitive()
					&& "roadfollow".equals(oldSteering.getAsString())) {
				this.roadFollowEnabled = true;
			}
			// A list still equal to the old default was never edited, so it gets the new default.
			if (sanitizeBlocks(this.roadBlocks).equals(OLD_DEFAULT_ROAD_BLOCKS)) {
				this.roadBlocks = new ArrayList<>(DEFAULT_ROAD_BLOCKS);
			}
		}
		this.configVersion = CURRENT_VERSION;
	}

	/** Keeps hand-edited or older files within sane ranges. */
	private void sanitize() {
		this.preferredTrainerId = this.preferredTrainerId == null ? "" : this.preferredTrainerId;
		this.preferredTrainerName = this.preferredTrainerName == null ? "" : this.preferredTrainerName;
		this.preferredPowerMeterId = this.preferredPowerMeterId == null ? "" : this.preferredPowerMeterId;
		this.preferredPowerMeterName = this.preferredPowerMeterName == null ? "" : this.preferredPowerMeterName;
		this.preferredHeartRateId = this.preferredHeartRateId == null ? "" : this.preferredHeartRateId;
		this.preferredHeartRateName = this.preferredHeartRateName == null ? "" : this.preferredHeartRateName;
		// Unknown values read as null.
		this.obcMode = this.obcMode == null ? ObcMode.BOTH : this.obcMode;
		this.obcPreferredKey = this.obcPreferredKey == null ? "" : this.obcPreferredKey;
		this.obcPreferredName = this.obcPreferredName == null ? "" : this.obcPreferredName;
		this.trainerResistanceIntensity = clamp(this.trainerResistanceIntensity,
			TrainerLimits.MIN_INTENSITY, TrainerLimits.MAX_INTENSITY, TrainerLimits.DEFAULT_INTENSITY);
		this.trainerGearRatio = clamp(this.trainerGearRatio,
			TrainerLimits.MIN_GEAR_RATIO, TrainerLimits.MAX_GEAR_RATIO, TrainerLimits.DEFAULT_GEAR_RATIO);
		this.trainerWheelCircumferenceM = clamp(this.trainerWheelCircumferenceM,
			TrainerLimits.MIN_WHEEL_CIRCUMFERENCE_M, TrainerLimits.MAX_WHEEL_CIRCUMFERENCE_M, TrainerLimits.DEFAULT_WHEEL_CIRCUMFERENCE_M);
		this.roadBlocks = sanitizeBlocks(this.roadBlocks);
		this.roadMaxHalfWidth = Math.max(2, Math.min(16, this.roadMaxHalfWidth));
		this.slopeWindowBlocks = Math.max(10, Math.min(40, this.slopeWindowBlocks));
		this.junctionCooldownSeconds = Math.max(5, Math.min(120, this.junctionCooldownSeconds));
		this.rideFitOriginLat = clamp(this.rideFitOriginLat, -80.0, 80.0, 30.0);
		this.rideFitOriginLon = clamp(this.rideFitOriginLon, -180.0, 180.0, -40.0);
	}

	/** Trims, lower-cases, drops blanks and duplicates, and caps the list so a hand-edited file cannot get huge. */
	public static List<String> sanitizeBlocks(List<String> blocks) {
		LinkedHashSet<String> clean = new LinkedHashSet<>();
		if (blocks != null) {
			for (String block : blocks) {
				if (block != null && !block.isBlank() && clean.size() < 64) {
					clean.add(block.trim().toLowerCase(Locale.ROOT));
				}
			}
		}
		return new ArrayList<>(clean);
	}

	private static double clamp(double value, double min, double max, double fallback) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
	}

	public void save() {
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(this));
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			FitnessRuntime.LOGGER.warn("Could not save {}", file, e);
		}
	}
}
