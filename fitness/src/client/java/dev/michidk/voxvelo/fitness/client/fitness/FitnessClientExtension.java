package dev.michidk.voxvelo.fitness.client.fitness;

import dev.michidk.voxvelo.bikes.client.ClientContext;
import dev.michidk.voxvelo.bikes.client.ext.VoxVeloClientExtension;
import dev.michidk.voxvelo.fitness.client.setup.LibrarySetupScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Starts the adapter only when its matching library is installed. */
public final class FitnessClientExtension implements VoxVeloClientExtension {
	private VoxVeloClientExtension delegate;
	private boolean prompted;
	private String version;
	private String installed;

	@Override
	public void init(ClientContext core) {
		FabricLoader loader = FabricLoader.getInstance();
		this.version = loader.getModContainer("voxvelo_fitness").orElseThrow().getMetadata().getVersion().getFriendlyString();
		this.installed = loader.getModContainer("voxvelo_fitness_lib")
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
		if (this.version.equals(this.installed)) {
			this.delegate = new AvailableFitnessClientExtension();
			this.delegate.init(core);
		} else {
			core.settingsPages().add(new ClientContext.SettingsPage("voxvelo_fitness.setup.title",
				parent -> new LibrarySetupScreen(parent, this.version, this.installed)));
		}
	}

	@Override
	public void tick(Minecraft client) {
		if (this.delegate != null) {
			this.delegate.tick(client);
		} else if (!this.prompted && client.gui.screen() instanceof TitleScreen title) {
			this.prompted = true;
			client.gui.setScreen(new LibrarySetupScreen(title, this.version, this.installed));
		}
	}

	@Override
	public void onJoin() {
		if (this.delegate != null) this.delegate.onJoin();
	}

	@Override
	public void onStopping() {
		if (this.delegate != null) this.delegate.onStopping();
	}
}
