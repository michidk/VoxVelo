package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

import java.util.concurrent.TimeoutException;

/**
 * A device search ran out of time before the device showed up. Kept apart from other timeouts, such as a connected
 * device that stops answering, which mean something else to the player.
 */
public final class DeviceNotFoundException extends TimeoutException {
	private static final long serialVersionUID = 1L;

	public DeviceNotFoundException() {
		super("Device not found");
	}
}
