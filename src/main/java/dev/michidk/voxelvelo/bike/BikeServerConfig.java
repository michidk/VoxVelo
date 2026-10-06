package dev.michidk.voxelvelo.bike;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.michidk.voxelvelo.VoxelVelo;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-owned settings, read once at startup. Roughly 10 km of smooth riding per tire. */
public final class BikeServerConfig {
    public static BikeServerConfig INSTANCE = new BikeServerConfig();
    public boolean enableVehicleDamage = true;
    public boolean vehicleDamageInCreativeMode = false;
    public boolean enableTireDamage = true;
    public double tireDamageRate = 0.0001;
    public double roughTerrainTireDamageMultiplier = 1.75;
    public double collisionTireDamage = 0.08;
    /** Tallest ledge in blocks that a bike rolls up without stopping (0 = none, 1 = a full block). */
    public double stepHeightBlocks = 1.0;

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("voxelvelo-server.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                BikeServerConfig config = gson.fromJson(Files.readString(path), BikeServerConfig.class);
                if (config != null) INSTANCE = config;
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, gson.toJson(INSTANCE));
            }
            INSTANCE.tireDamageRate = safe(INSTANCE.tireDamageRate, 0.0001, 1);
            INSTANCE.roughTerrainTireDamageMultiplier = safe(INSTANCE.roughTerrainTireDamageMultiplier, 1.75, 100);
            INSTANCE.collisionTireDamage = safe(INSTANCE.collisionTireDamage, 0.08, 1);
            INSTANCE.stepHeightBlocks = safe(INSTANCE.stepHeightBlocks, 1.0, 4);
        } catch (Exception e) {
            INSTANCE = new BikeServerConfig();
            VoxelVelo.LOGGER.warn("Cannot read bicycle server configuration; using defaults", e);
        }
    }

    private static double safe(double value, double fallback, double max) {
        return Double.isFinite(value) ? Math.clamp(value, 0, max) : fallback;
    }
}
