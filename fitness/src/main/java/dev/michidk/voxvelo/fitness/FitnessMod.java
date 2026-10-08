package dev.michidk.voxvelo.fitness;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Identifiers owned by the bicycle fitness integration. */
public final class FitnessMod {
	public static final String MOD_ID = "voxvelo_fitness";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private FitnessMod() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
