package dev.michidk.voxvelo.fitnesslib.client;

import dev.michidk.voxvelo.fitnesslib.api.VehicleAdapter;

/**
 * Tracks vehicle identity independently of telemetry values. Changing identity always releases the
 * old owner.
 */
public final class VehicleBinding {
	private Object identity;
	private VehicleAdapter adapter;
	private final Runnable onRelease;

	public VehicleBinding(Runnable onRelease) {
		this.onRelease = onRelease;
	}

	public VehicleAdapter adapter() {
		return adapter;
	}

	public Object identity() {
		return identity;
	}

	public void select(Object nextIdentity, VehicleAdapter next) {
		if (identity == nextIdentity && adapter == next) return;
		clear();
		if (next != null) {
			identity = nextIdentity;
			adapter = next;
		}
	}

	public void clear() {
		VehicleAdapter previous = adapter;
		identity = null;
		adapter = null;
		try {
			if (previous != null) previous.deactivate();
		} finally {
			onRelease.run();
		}
	}
}
