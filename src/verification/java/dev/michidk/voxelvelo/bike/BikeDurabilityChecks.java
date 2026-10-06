package dev.michidk.voxelvelo.bike;

/** Standalone checks for crash/fall damage, type durability, and condition reporting. */
public final class BikeDurabilityChecks {
    public static void main(String[] args) {
        require(BikeType.ROAD.maxDurability < BikeType.GRAVEL.maxDurability
                && BikeType.GRAVEL.maxDurability < BikeType.MOUNTAIN.maxDurability,
                "gravel and mountain bikes are progressively more durable than race bikes");
        require(BikeDurability.crashDamage(4.0) == 0.0F, "minor impacts do not damage the bike");
        require(BikeDurability.crashDamage(10.0) == 12.0F, "hard impacts scale from lost speed");
        require(BikeDurability.fallDamage(3.0) == 0.0F, "short falls do not damage the bike");
        require(BikeDurability.fallDamage(8.0) == 15.0F, "long falls damage the bike");
        require(BikeDurability.conditionPercent(30.0F, BikeType.ROAD) == 50, "condition uses type durability");
        require(BikeDurability.clampDamage(Float.NaN, BikeType.ROAD) == 0.0F, "invalid saved damage is sanitized");
        require(BikeDurability.clampDamage(500.0F, BikeType.MOUNTAIN) == 100.0F, "saved damage is capped");
        System.out.println("Bike durability checks passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
