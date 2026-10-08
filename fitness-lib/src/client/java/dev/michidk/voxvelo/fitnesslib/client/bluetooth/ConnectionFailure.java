package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

import net.minecraft.network.chat.Component;

/** A failure whose reason the player can read. The exception message is the English text for the log. */
public final class ConnectionFailure extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final transient Component reason;

	public ConnectionFailure(String logMessage, Component reason) {
		super(logMessage);
		this.reason = reason;
	}

	public Component reason() {
		return this.reason;
	}
}
