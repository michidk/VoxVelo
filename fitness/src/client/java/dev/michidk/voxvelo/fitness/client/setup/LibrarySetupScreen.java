package dev.michidk.voxvelo.fitness.client.setup;

import com.mojang.blaze3d.Blaze3D;
import dev.michidk.voxvelo.bikes.client.ui.SettingsList;
import dev.michidk.voxvelo.bikes.client.ui.SettingsScreen;
import dev.michidk.voxvelo.fitness.FitnessMod;
import java.nio.file.FileAlreadyExistsException;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Explains the missing library and offers an explicit, verified download with a restart. */
public final class LibrarySetupScreen extends SettingsScreen {
	private final String version;
	private final @Nullable String installed;
	private Component status;
	private boolean downloading;
	private boolean complete;

	public LibrarySetupScreen(@Nullable Screen parent, String version, @Nullable String installed) {
		super(parent, Component.translatable("voxvelo_fitness.setup.title"));
		this.version = version;
		this.installed = installed;
		this.status = Component.translatable("voxvelo_fitness.setup.ready");
	}

	@Override
	protected void addOptions() {
		this.rows.addText(() -> Component.translatable("voxvelo_fitness.setup.explanation", this.version), STATUS);
		this.rows.addText(() -> Component.translatable("voxvelo_fitness.setup.consent"), HINT);
		if (this.installed != null) {
			this.rows.addText(() -> Component.translatable("voxvelo_fitness.setup.wrong_version", this.installed), ERROR);
		}
		this.rows.addText(() -> this.status, this.complete ? STATUS : HINT);
		Button download = Button.builder(Component.translatable(this.complete ? "voxvelo_fitness.setup.installed" : "voxvelo_fitness.setup.download"), button -> this.download())
			.bounds(0, 0, SettingsList.WIDTH, 20).build();
		download.active = !this.downloading && !this.complete && this.installed == null;
		this.rows.addRow(download);
		this.rows.addRow(Button.builder(Component.translatable("voxvelo_fitness.setup.manual"), button -> Blaze3D.openUri(LibraryInstaller.releasePage(this.version)))
			.bounds(0, 0, SettingsList.WIDTH, 20).build());
		this.rows.addRow(Button.builder(Component.translatable("voxvelo_fitness.setup.folder"), button -> Blaze3D.openPath(FabricLoader.getInstance().getGameDir().resolve("mods")))
			.bounds(0, 0, SettingsList.WIDTH, 20).build());
		Button quit = Button.builder(Component.translatable("voxvelo_fitness.setup.quit"), button -> this.minecraft.stop())
			.bounds(0, 0, SettingsList.WIDTH, 20).build();
		quit.active = !this.downloading;
		this.rows.addRow(quit);
	}

	private void download() {
		if (this.downloading || this.complete || this.installed != null) return;
		this.downloading = true;
		this.status = Component.translatable("voxvelo_fitness.setup.downloading");
		this.rebuildWidgets();
		CompletableFuture.runAsync(() -> {
			Component result;
			boolean success;
			try {
				LibraryInstaller.install(FabricLoader.getInstance().getGameDir().resolve("mods"), this.version);
				result = Component.translatable("voxvelo_fitness.setup.restart");
				success = true;
			} catch (Exception e) {
				if (e instanceof InterruptedException) Thread.currentThread().interrupt();
				FitnessMod.LOGGER.warn("Fitness Library installation failed", e);
				result = e instanceof FileAlreadyExistsException
					? Component.translatable("voxvelo_fitness.setup.exists")
					: Component.translatable("voxvelo_fitness.setup.failed", e.getMessage() == null ? "Check your connection." : e.getMessage());
				success = false;
			}
			Component message = result;
			boolean installedSuccessfully = success;
			this.minecraft.execute(() -> {
				this.downloading = false;
				this.complete = installedSuccessfully;
				this.status = message;
				if (this.minecraft.gui.screen() == this) this.rebuildWidgets();
			});
		});
	}

	@Override
	protected void save() {}
}
