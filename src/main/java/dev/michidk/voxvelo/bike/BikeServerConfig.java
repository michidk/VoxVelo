package dev.michidk.voxvelo.bike;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.michidk.voxvelo.VoxVelo;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Server-owned settings, read once at startup. Roughly 10 km of smooth riding per tire. */
public final class BikeServerConfig {
	private static BikeServerConfig instance = new BikeServerConfig();

	public boolean enableVehicleDamage = true;
	public boolean vehicleDamageInCreativeMode = false;
	public boolean enableTireDamage = true;
	public double tireDamageRate = 0.0001;
	public double roughTerrainTireDamageMultiplier = 1.75;
	public double collisionTireDamage = 0.08;
	/** Tallest ledge in blocks that a bike rolls up without stopping (0 = none, 1 = a full block). */
	public double stepHeightBlocks = 1.0;

	/** The settings in effect; the defaults until {@link #load} has run. */
	public static BikeServerConfig get() {
		return instance;
	}

	/** Tire wear is either a survival mechanic or disabled; creative riders never consume tires. */
	boolean wearsTires(boolean creativeMode) {
		return this.enableTireDamage && !creativeMode;
	}

	/** Changes the live integrated-server setting and persists it for the next world. */
	public static void setTireDamageEnabled(boolean enabled) {
		instance.enableTireDamage = enabled;
		save();
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("voxvelo-server.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			BikeServerConfig config = null;
			if (Files.exists(path)) {
				config = gson.fromJson(Files.readString(path), BikeServerConfig.class);
			} else {
				Files.createDirectories(path.getParent());
				Files.writeString(path, gson.toJson(new BikeServerConfig()));
			}
			instance = config != null ? config.sanitized() : new BikeServerConfig();
		} catch (Exception e) {
			instance = new BikeServerConfig();
			VoxVelo.LOGGER.warn("Cannot read bicycle server configuration; using defaults", e);
		}
	}

	private static void save() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("voxvelo-server.json");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(instance));
		} catch (Exception e) {
			VoxVelo.LOGGER.warn("Cannot save bicycle server configuration", e);
		}
	}

	/** Forces hand-edited numbers into their ranges; anything that is not a number falls back to its default. */
	private BikeServerConfig sanitized() {
		BikeServerConfig defaults = new BikeServerConfig();
		this.tireDamageRate = safe(this.tireDamageRate, defaults.tireDamageRate, 1);
		this.roughTerrainTireDamageMultiplier = safe(this.roughTerrainTireDamageMultiplier, defaults.roughTerrainTireDamageMultiplier, 100);
		this.collisionTireDamage = safe(this.collisionTireDamage, defaults.collisionTireDamage, 1);
		this.stepHeightBlocks = safe(this.stepHeightBlocks, defaults.stepHeightBlocks, 4);
		return this;
	}

	private static double safe(double value, double fallback, double max) {
		return Double.isFinite(value) ? Math.clamp(value, 0, max) : fallback;
	}
}
