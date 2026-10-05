package dev.michidk.voxelvelo.client.hud;

import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.network.chat.Component;

/** What an add-on can contribute to the riding HUD: better numbers than the bike's own, and extra lines. */
public interface HudExtras {
	/** Pedalling cadence in rpm, if the add-on has a measured one (a trainer); the bike's own estimate is used otherwise. */
	default OptionalDouble cadenceRpm() {
		return OptionalDouble.empty();
	}

	/** Slope of the ground as rise over run, if the add-on measures it more smoothly than the bike does. */
	default OptionalDouble gradient() {
		return OptionalDouble.empty();
	}

	/** Appends extra lines below the usual ones. */
	default void addLines(List<Component> lines) {
	}
}
