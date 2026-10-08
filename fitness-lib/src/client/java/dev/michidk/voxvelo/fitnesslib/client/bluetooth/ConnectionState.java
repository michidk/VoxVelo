package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

/** Where the connection to a device stands. The same for every kind of device: trainers, sensors and controllers. */
public enum ConnectionState {
	DISCONNECTED,
	CONNECTING,
	CONNECTED
}
