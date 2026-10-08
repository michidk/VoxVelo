package dev.michidk.voxvelo.bikes.client.ui;

import dev.michidk.voxvelo.bikes.bike.BikeServerConfig;
import dev.michidk.voxvelo.bikes.client.ClientContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The VoxVelo settings hub, opened with the "Open Bike Settings" key. It leads to one page per topic, grouped under
 * headings: the rider and the keyboard are built in, add-ons such as the fitness integration add their own pages and
 * status lines. In singleplayer it also holds the settings of the world.
 */
public class ConfigScreen extends SettingsScreen {
	private final ClientContext ctx = ClientContext.get();

	public ConfigScreen(@Nullable Screen parent) {
		super(parent, Component.translatable("voxvelo_bikes.config.title"));
	}

	@Override
	protected void addOptions() {
		for (var line : this.ctx.statusLines()) {
			this.rows.addText(line, STATUS);
		}

		Map<String, List<ClientContext.SettingsPage>> sections = new LinkedHashMap<>();
		sections.put("voxvelo_bikes.config.section.riding", List.of(
			new ClientContext.SettingsPage("voxvelo_bikes.config.rider", RiderScreen::new),
			new ClientContext.SettingsPage("voxvelo_bikes.config.keyboard", KeyboardScreen::new)));
		for (ClientContext.SettingsPage page : this.ctx.settingsPages()) {
			sections.computeIfAbsent(page.sectionKey(), key -> new ArrayList<>()).add(page);
		}
		sections.forEach((section, pages) -> {
			this.rows.addHeader(Component.translatable(section));
			// Two buttons per row; a lone last one takes the whole row.
			for (int i = 0; i < pages.size(); i += 2) {
				if (i == pages.size() - 1) {
					this.rows.addRow(this.page(0, SettingsList.WIDTH, pages.get(i)));
				} else {
					this.rows.addRow(this.page(0, SettingsList.HALF, pages.get(i)),
						this.page(SettingsList.WIDTH - SettingsList.HALF, SettingsList.HALF, pages.get(i + 1)));
				}
			}
		});

		if (this.minecraft.isLocalServer()) {
			this.rows.addHeader(Component.translatable("voxvelo_bikes.config.section.world"));
			this.rows.addRow(CycleButton.builder(ConfigScreen::tireWearValue, BikeServerConfig.get().enableTireDamage)
				.withValues(true, false)
				.withTooltip(value -> Tips.of("voxvelo_bikes.config.tire_damage"))
				.create(0, 0, SettingsList.WIDTH, 20, Component.translatable("voxvelo_bikes.config.tire_damage"),
					(button, value) -> BikeServerConfig.setTireDamageEnabled(value)));
		}
	}

	private static Component tireWearValue(boolean enabled) {
		return Component.translatable("voxvelo_bikes.config.tire_damage." + (enabled ? "survival" : "off"));
	}

	/** A button that opens another settings page, built when the button is pressed. */
	private AbstractWidget page(int x, int width, ClientContext.SettingsPage page) {
		Function<Screen, Screen> factory = page.factory();
		return Button.builder(Component.translatable(page.labelKey()), button -> this.minecraft.gui.setScreen(factory.apply(this)))
			.tooltip(Tips.of(page.labelKey())).bounds(x, 0, width, 20).build();
	}

	@Override
	protected void save() {
		this.ctx.config.save();
	}
}
