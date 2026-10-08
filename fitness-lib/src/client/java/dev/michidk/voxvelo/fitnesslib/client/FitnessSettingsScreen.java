package dev.michidk.voxvelo.fitnesslib.client;

import dev.michidk.voxvelo.fitnesslib.api.SettingsPage;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessStatus;
import dev.michidk.voxvelo.fitnesslib.client.ride.RideScreen;
import dev.michidk.voxvelo.fitnesslib.client.ui.BluetoothScreen;
import dev.michidk.voxvelo.fitnesslib.client.ui.ObcScreen;
import dev.michidk.voxvelo.fitnesslib.client.ui.SettingsList;
import dev.michidk.voxvelo.fitnesslib.client.ui.SettingsScreen;
import dev.michidk.voxvelo.fitnesslib.client.ui.Tips;
import dev.michidk.voxvelo.fitnesslib.client.ui.TrainerSettingsScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The fitness settings hub: the state of every connection, then one button per settings page. Vehicle mods add their
 * own pages through {@link dev.michidk.voxvelo.fitnesslib.api.Fitness#addSettingsPage}.
 */
public final class FitnessSettingsScreen extends SettingsScreen {
	/** Every page in order: the built-in ones, then those vehicle mods added. */
	public static final List<SettingsPage> PAGES = new ArrayList<>(List.of(
		new SettingsPage("voxvelo_fitness_lib.config.bluetooth_page", BluetoothScreen::new),
		new SettingsPage("voxvelo_fitness_lib.bluetooth.trainer_settings", TrainerSettingsScreen::new),
		new SettingsPage("voxvelo_fitness_lib.config.obc", ObcScreen::new),
		new SettingsPage("voxvelo_fitness_lib.config.ride_recording", RideScreen::new)));

	private final FitnessRuntime ctx = FitnessRuntime.get();

	public FitnessSettingsScreen(@Nullable Screen parent) {
		super(parent, Component.translatable("voxvelo_fitness_lib.settings.title"));
	}

	@Override
	protected void addOptions() {
		this.rows.addText(() -> FitnessStatus.trainerStatus(this.ctx), STATUS);
		this.rows.addText(() -> FitnessStatus.powerMeterStatus(this.ctx), STATUS);
		this.rows.addText(() -> FitnessStatus.heartRateStatus(this.ctx), STATUS);
		this.rows.addText(() -> FitnessStatus.telemetryLine(this.ctx), STATUS);
		this.rows.addText(() -> FitnessStatus.obcStatus(this.ctx), STATUS);
		// Two buttons per row; a lone last one takes the whole row.
		for (int i = 0; i < PAGES.size(); i += 2) {
			if (i == PAGES.size() - 1) {
				this.rows.addRow(this.page(0, SettingsList.WIDTH, PAGES.get(i)));
			} else {
				this.rows.addRow(this.page(0, SettingsList.HALF, PAGES.get(i)),
					this.page(SettingsList.WIDTH - SettingsList.HALF, SettingsList.HALF, PAGES.get(i + 1)));
			}
		}
	}

	/** A button that opens another settings page, built when the button is pressed. */
	private AbstractWidget page(int x, int width, SettingsPage page) {
		Function<Screen, Screen> factory = page.factory();
		return Button.builder(Component.translatable(page.labelKey()), button -> this.minecraft.gui.setScreen(factory.apply(this)))
			.tooltip(Tips.of(page.labelKey())).bounds(x, 0, width, 20).build();
	}

	@Override
	protected void save() {
		this.ctx.config.save();
	}
}
