package dev.michidk.voxelfitness.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Deterministic registration order; duplicate IDs fail rather than silently replacing integrations.
 */
public final class VehicleRegistry {
	private final Map<String, VehicleAdapter> adapters = new LinkedHashMap<>();

	public AutoCloseable register(VehicleAdapter adapter) {
		Objects.requireNonNull(adapter);
		String id = adapter.id();
		if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
			throw new IllegalArgumentException("Use a namespaced adapter ID");
		if (adapters.putIfAbsent(id, adapter) != null)
			throw new IllegalArgumentException("Duplicate fitness adapter: " + id);
		return () -> adapters.remove(id, adapter);
	}

	public VehicleAdapter select(Predicate<VehicleAdapter> supports) {
		for (VehicleAdapter adapter : adapters.values()) if (supports.test(adapter)) return adapter;
		return null;
	}
}
