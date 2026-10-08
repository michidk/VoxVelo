package dev.michidk.voxvelo.bikes.client.input;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;

/**
 * One origin of rider controls (keyboard, FTMS trainer, OpenBikeControl...). A source only reports
 * what it actually knows; {@link BikeInputManager} decides per control channel which source wins.
 */
public interface BikeInputSource {
	String id();

	/** Whether this source can drive the given control at all (used to filter configuration choices). */
	boolean supports(ControlChannel channel);

	/** Short name for the UI. */
	String displayName();

	/** Whether this source is currently usable (e.g. a trainer is connected). The keyboard is always active. */
	boolean isActive();

	/** Called once per client tick while the local player is riding a bike. */
	void tick(Minecraft client);

	/** Called when the player stops riding; drop any latched state. */
	void reset();

	/** Rider power in watts, if this source provides propulsion. */
	OptionalDouble propulsion();

	/** Normalized steering -1..+1, if this source provides steering. */
	OptionalDouble steering();

	/** Normalized brake 0..1, if this source provides braking. */
	OptionalDouble brake();

	/** Net number of gear changes (+ up, - down) since the last call; consumes them. */
	int consumeGearShift();

	/** An absolute gear (1-based) requested by the source, if any; consumes it. */
	default OptionalInt consumeGearSet() {
		return OptionalInt.empty();
	}
}
