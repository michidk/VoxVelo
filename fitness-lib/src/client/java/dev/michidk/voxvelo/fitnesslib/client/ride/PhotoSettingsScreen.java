package dev.michidk.voxvelo.fitnesslib.client.ride;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.ui.SettingsList;
import dev.michidk.voxvelo.fitnesslib.client.ui.SettingsScreen;
import dev.michidk.voxvelo.fitnesslib.client.ui.Tips;
import dev.michidk.voxvelo.fitnesslib.client.ui.ValueSlider;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Settings for automatic photos during active fitness riding. */
public final class PhotoSettingsScreen extends SettingsScreen {
	private static final String KEY = "voxvelo_fitness_lib.photos.";
	private final dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig config = FitnessRuntime.get().config;
	public PhotoSettingsScreen(Screen parent) { super(parent, Component.translatable(KEY + "title")); }
	@Override
	protected void addOptions() {
		int width = SettingsList.WIDTH;
		this.rows.addText(() -> Component.translatable(KEY + "hint"), HINT);
		this.rows.addRow(CycleButton.onOffBuilder(this.config.ridePhotosEnabled)
			.withTooltip(value -> Tips.of(KEY + "enabled"))
			.create(0, 0, width, 20, Component.translatable(KEY + "enabled"), (button, value) -> this.config.ridePhotosEnabled = value));
		this.rows.addRow(new ValueSlider(0, 0, width, 30, 3600, 30, this.config.ridePhotoIntervalSeconds,
			value -> Component.translatable(KEY + "interval", value.intValue()), value -> this.config.ridePhotoIntervalSeconds = (int) value).tip(KEY + "interval"));
		this.rows.addRow(new ValueSlider(0, 0, width, 0, 1000, 10, this.config.ridePhotoMinWatts,
			value -> Component.translatable(KEY + "watts", value.intValue()), value -> this.config.ridePhotoMinWatts = (int) value).tip(KEY + "watts"));
		this.rows.addRow(new ValueSlider(0, 0, width, 1, 50, 1, this.config.ridePhotoMinSpeedKmh,
			value -> Component.translatable(KEY + "speed", value.intValue()), value -> this.config.ridePhotoMinSpeedKmh = value).tip(KEY + "speed"));
		this.rows.addRow(CycleButton.<Integer>builder(value -> Component.translatable(KEY + "view." + value), this.config.ridePhotoPerspective)
			.withValues(0, 1, 2, 3, 4)
			.withTooltip(value -> Tips.of(KEY + "view"))
			.create(0, 0, width, 20, Component.translatable(KEY + "view"), (button, value) -> this.config.ridePhotoPerspective = value));
		this.rows.addText(() -> Component.translatable(KEY + "view_warning"), HINT);
		this.rows.addRow(CycleButton.onOffBuilder(this.config.ridePhotoHideHud)
			.withTooltip(value -> Tips.of(KEY + "hide_hud"))
			.create(0, 0, width, 20, Component.translatable(KEY + "hide_hud"), (button, value) -> this.config.ridePhotoHideHud = value));
		this.rows.addRow(Button.builder(Component.translatable(KEY + "folder"), button -> {
			try {
				var directory = RideExport.ridesDirectory().resolve("photos");
				java.nio.file.Files.createDirectories(directory);
				com.mojang.blaze3d.Blaze3D.openPath(directory);
			} catch (java.io.IOException failure) { FitnessRuntime.LOGGER.warn("Could not open photo folder", failure); }
		}).bounds(0, 0, width, 20).build());
	}
	@Override
	protected void save() { this.config.save(); }
}
