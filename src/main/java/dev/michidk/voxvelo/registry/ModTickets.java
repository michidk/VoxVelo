package dev.michidk.voxvelo.registry;

import dev.michidk.voxvelo.VoxVelo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

/** Chunk ticket types. Track builds hold their chunks until they release them, never by timeout. */
public final class ModTickets {
	/** The chunks a track build is surveying or building in. */
	public static final TicketType TRACK_BUILD = register("track_build");
	/** The chunks a tree the track cuts through reaches into. */
	public static final TicketType TRACK_TREE_CLEANUP = register("track_tree_cleanup");

	private ModTickets() {
	}

	private static TicketType register(String name) {
		return Registry.register(BuiltInRegistries.TICKET_TYPE, VoxVelo.id(name),
			new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
	}

	public static void register() {
		// Loads the class, which registers the ticket types above before the registries freeze.
	}
}
