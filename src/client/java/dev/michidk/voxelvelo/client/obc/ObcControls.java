package dev.michidk.voxelvelo.client.obc;

import java.util.Arrays;
import java.util.List;

/**
 * Turns OpenBikeControl button states into the normalized controls the bike understands. Written by
 * transport threads, read by the game thread, so every method is synchronized.
 *
 * <ul>
 *   <li>Steer Left / Steer Right (0x18/0x19): pressed (1) is full lock, 2..255 is analog strength.</li>
 *   <li>Brake (0x1A): 1 is full, 2..201 is (value - 1) percent, clamped to 100 %.</li>
 *   <li>Shift Up/Down (0x01/0x02): one step per press; Gear Set (0x03): 1-based gear = value - 1.</li>
 * </ul>
 * Every other button id is ignored.
 */
public final class ObcControls {
	private final int[] lastState = new int[256];
	private double left;
	private double right;
	private double brake;
	private int pendingShifts;
	private int gearSet;

	public synchronized void apply(List<ObcMessages.ButtonState> buttons) {
		for (ObcMessages.ButtonState button : buttons) {
			this.apply(button.id(), button.state());
		}
	}

	public synchronized void apply(int id, int state) {
		int previous = this.lastState[id];
		this.lastState[id] = state;
		switch (id) {
			case Obc.BUTTON_STEER_LEFT -> this.left = steerMagnitude(state);
			case Obc.BUTTON_STEER_RIGHT -> this.right = steerMagnitude(state);
			case Obc.BUTTON_BRAKE -> this.brake = brakeStrength(state);
			case Obc.BUTTON_SHIFT_UP -> {
				if (previous == 0 && state != 0) {
					this.pendingShifts++;
				}
			}
			case Obc.BUTTON_SHIFT_DOWN -> {
				if (previous == 0 && state != 0) {
					this.pendingShifts--;
				}
			}
			case Obc.BUTTON_GEAR_SET -> {
				// 0 and 1 mean "no change"; 2.. is gear + 1.
				if (state >= 2) {
					this.gearSet = state - 1;
				}
			}
			default -> {
				// Unknown or unsupported action: ignored on purpose.
			}
		}
	}

	/** Called when the device goes away: nothing may stay pressed. */
	public synchronized void releaseAll() {
		Arrays.fill(this.lastState, 0);
		this.left = 0.0;
		this.right = 0.0;
		this.brake = 0.0;
		this.pendingShifts = 0;
		this.gearSet = 0;
	}

	/** -1 (left) .. +1 (right). */
	public synchronized double steering() {
		return Math.max(-1.0, Math.min(1.0, this.right - this.left));
	}

	public synchronized double brake() {
		return this.brake;
	}

	public synchronized int consumeShifts() {
		int shifts = this.pendingShifts;
		this.pendingShifts = 0;
		return shifts;
	}

	/** An absolute gear requested by the device, or 0 if none. Consumes it. */
	public synchronized int consumeGearSet() {
		int gear = this.gearSet;
		this.gearSet = 0;
		return gear;
	}

	private static double steerMagnitude(int state) {
		if (state <= 0) {
			return 0.0;
		}
		return state == 1 ? 1.0 : Math.min(1.0, (state - 1) / 254.0);
	}

	private static double brakeStrength(int state) {
		if (state <= 0) {
			return 0.0;
		}
		if (state == 1 || state > 0xC9) {
			return 1.0;
		}
		return Math.min(1.0, (state - 1) / 100.0);
	}
}
