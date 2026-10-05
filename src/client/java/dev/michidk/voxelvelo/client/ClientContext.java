package dev.michidk.voxelvelo.client;

import dev.michidk.voxelvelo.client.config.VoxelVeloConfig;
import dev.michidk.voxelvelo.client.hud.HudExtras;
import dev.michidk.voxelvelo.client.input.BikeInputManager;
import dev.michidk.voxelvelo.client.input.KeyboardBikeInput;
import dev.michidk.voxelvelo.network.RiderSettingsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** The client-side services of the mod, created once at startup. Nothing here is ever loaded on a server. */
public final class ClientContext {
	private static ClientContext instance;

	/** A page of the settings hub added by an add-on. */
	public record SettingsPage(String labelKey, Function<Screen, Screen> factory) {
	}

	public final VoxelVeloConfig config;
	public final BikeInputManager inputManager;

	private final List<SettingsPage> settingsPages = new ArrayList<>();
	private final List<Supplier<Component>> statusLines = new ArrayList<>();
	private final List<HudExtras> hudExtras = new ArrayList<>();
	private final List<Consumer<LocalPlayer>> mountListeners = new ArrayList<>();

	private ClientContext() {
		this.config = VoxelVeloConfig.load();
		this.inputManager = new BikeInputManager(new KeyboardBikeInput(this.config));
	}

	/** Pages an add-on adds to the settings hub, after the core ones. */
	public List<SettingsPage> settingsPages() {
		return this.settingsPages;
	}

	/** Lines an add-on shows at the top of the settings hub, e.g. connection status. */
	public List<Supplier<Component>> statusLines() {
		return this.statusLines;
	}

	public List<HudExtras> hudExtras() {
		return this.hudExtras;
	}

	/** Listeners told when the local player gets on a bike. */
	public List<Consumer<LocalPlayer>> mountListeners() {
		return this.mountListeners;
	}

	/** Tells the server this player rider mass and speed limit. Harmless if the server does not know the packet. */
	public void sendRiderSettings() {
		if (ClientPlayNetworking.canSend(RiderSettingsPayload.TYPE)) {
			ClientPlayNetworking.send(RiderSettingsPayload.of(this.config.riderMassKg, this.config.speedLimitKmh));
		}
	}

	public static ClientContext init() {
		instance = new ClientContext();
		return instance;
	}

	public static ClientContext get() {
		return instance;
	}
}
