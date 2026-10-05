package dev.michidk.voxelvelo.registry;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.inventory.BikeMaintenanceMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    public static final MenuType<BikeMaintenanceMenu> MAINTENANCE = Registry.register(
            BuiltInRegistries.MENU, VoxelVelo.id("bike_maintenance"),
            new MenuType<>(BikeMaintenanceMenu::new, FeatureFlags.DEFAULT_FLAGS));
    private ModMenus() {}
    public static void register() {}
}
