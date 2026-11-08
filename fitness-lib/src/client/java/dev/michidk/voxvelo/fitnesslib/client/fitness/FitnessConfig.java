package dev.michidk.voxvelo.fitnesslib.client.fitness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothDevice;
import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerLimits;
import dev.michidk.voxvelo.fitnesslib.client.ftms.VirtualShifting;
import dev.michidk.voxvelo.fitnesslib.client.obc.ObcMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/** Settings of the fitness integration, stored as JSON in config/voxvelo-fitness-lib.json. */
public final class FitnessConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * What counts as road unless the player changes it: smooth stone and its slab (what the track command builds when it
	 * is given no material, slabs on its half-block steps), plus stone and gravel for roads made by hand.
	 */
	public static final List<String> DEFAULT_ROAD_BLOCKS = List.of(
		"minecraft:smooth_stone", "minecraft:smooth_stone_slab", "minecraft:stone", "minecraft:gravel");

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
	/** Saved physical devices, including devices not currently selected for a capability. */
	public List<BluetoothDevice> bluetoothDevices = new ArrayList<>();
	public boolean autoConnectBluetooth = true;

	/** Which OpenBikeControl transports are used. */
	public ObcMode obcMode = ObcMode.BOTH;
	public String obcPreferredKey = "";
	public String obcPreferredName = "";
	public boolean obcAutoConnect = true;

	/** Send terrain gradient and surface to the trainer as resistance. Intensity 1.0 matches the game physics. */
	public boolean trainerResistanceEnabled = true;
	public double trainerResistanceIntensity = TrainerLimits.DEFAULT_INTENSITY;
	/** Seconds the grade sent to the trainer takes to follow the terrain, so a single block step is not felt. */
	public double trainerGradeSmoothingSeconds = TrainerLimits.DEFAULT_GRADE_SMOOTHING_S;
	public boolean virtualShiftingEnabled = true;
	public VirtualShifting.GearRange virtualGearRange = VirtualShifting.GearRange.COMPACT;
	/** Virtual wheel circumference used with measured cadence to calculate road speed. */
	public double trainerWheelCircumferenceM = TrainerLimits.DEFAULT_WHEEL_CIRCUMFERENCE_M;

	/** Show other riders watts and heart rate on screen, and whether this player shares theirs with the others. */
	public boolean statsOverlayEnabled = true;
	public boolean shareWatts = true;
	public boolean shareHeartRate = true;

	/** The riding panel with power, cadence, speed, grade and gear. */
	public boolean hudEnabled = true;
	public boolean heartRateHudEnabled = true;
	public boolean steeringHudEnabled = false;
	public boolean trainerDebugHudEnabled = false;
	public HudVisibility ridingHudVisibility = HudVisibility.RIDING;
	public HudVisibility heartHudVisibility = HudVisibility.RIDING;
	public HudVisibility steeringHudVisibility = HudVisibility.RIDING;
	public HudVisibility junctionHudVisibility = HudVisibility.RIDING;
	/** Multiplier for OpenBikeControl steering, with 1 preserving the controller's angle. */
	public double steeringSensitivity = 1.0;
	public double steeringSmoothing = 1.0;
	public double steeringDeadzoneDegrees = 5.0;
	public boolean junctionHudEnabled = true;

	/** Steer the bike along the road by itself. It takes over the steering whenever a road is found ahead. */
	public boolean roadFollowEnabled = false;
	/** Keep the bicycle camera level and facing along the bike when the Fitness adapter is installed. */
	public boolean bikeCameraFollow = true;
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
	/** The bicycle adapter may start a recording on mounting; manual recording remains the default. */
	public boolean rideAutoRecordOnMount = false;
	/** Automatic photos count active riding time, with measured power and movement required. */
	public boolean ridePhotosEnabled = true;
	public int ridePhotoIntervalSeconds = 300;
	public int ridePhotoMinWatts = 50;
	public double ridePhotoMinSpeedKmh = 3;
	public boolean ridePhotoHideHud = true;
	/** 0 keeps the current view; 1 rear, 2 front, 3 alternates rear and front. */
	public int ridePhotoPerspective = 4;
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
		return FabricLoader.getInstance().getConfigDir().resolve("voxvelo-fitness-lib.json");
	}

	/** Loads library settings; unreadable files are backed up before defaults replace them. */
	public static FitnessConfig load() {
		Path file = file();
		if (Files.isRegularFile(file)) {
			try {
				JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				FitnessConfig loaded = GSON.fromJson(json, FitnessConfig.class);
				if (loaded != null) {
					if (!json.has("autoConnectBluetooth")) {
						loaded.autoConnectBluetooth = loaded.autoConnectTrainer && loaded.autoConnectPowerMeter && loaded.autoConnectHeartRate;
					}
					if (!json.has("ridingHudVisibility") && !loaded.hudEnabled) loaded.ridingHudVisibility = HudVisibility.NEVER;
if (!json.has("heartHudVisibility") && !loaded.heartRateHudEnabled) loaded.heartHudVisibility = HudVisibility.NEVER;
if (!json.has("junctionHudVisibility") && !loaded.junctionHudEnabled) loaded.junctionHudVisibility = HudVisibility.NEVER;
loaded.sanitize();
					return loaded;
				}
			} catch (Exception e) {
				FitnessRuntime.LOGGER.warn("Could not read {}, using defaults", file, e);
				backup(file);
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

	/** Keeps hand-edited or older files within sane ranges. */
	private void sanitize() {
		this.steeringDeadzoneDegrees = clamp(this.steeringDeadzoneDegrees, 0, 30, 5);
		this.steeringSmoothing = clamp(this.steeringSmoothing, 0.05, 1, 1);
		if (this.ridingHudVisibility == null) this.ridingHudVisibility = HudVisibility.RIDING;
		if (this.heartHudVisibility == null) this.heartHudVisibility = HudVisibility.RIDING;
		if (this.steeringHudVisibility == null) this.steeringHudVisibility = HudVisibility.RIDING;
		if (this.junctionHudVisibility == null) this.junctionHudVisibility = HudVisibility.RIDING;
		this.ridePhotoIntervalSeconds = Math.max(30, Math.min(3600, this.ridePhotoIntervalSeconds));
		this.ridePhotoMinWatts = Math.max(0, Math.min(1000, this.ridePhotoMinWatts));
		this.ridePhotoMinSpeedKmh = clamp(this.ridePhotoMinSpeedKmh, 1, 50, 3);
		this.ridePhotoPerspective = Math.max(0, Math.min(4, this.ridePhotoPerspective));
		if (this.bluetoothDevices == null) this.bluetoothDevices = new ArrayList<>();
		this.bluetoothDevices.removeIf(device -> device == null || device.id() == null || device.id().isBlank() || device.advertisedServices() == null);
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
		this.trainerGradeSmoothingSeconds = clamp(this.trainerGradeSmoothingSeconds,
			TrainerLimits.MIN_GRADE_SMOOTHING_S, TrainerLimits.MAX_GRADE_SMOOTHING_S, TrainerLimits.DEFAULT_GRADE_SMOOTHING_S);
		if (this.virtualGearRange == null) this.virtualGearRange = VirtualShifting.GearRange.COMPACT;
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
