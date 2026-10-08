package dev.michidk.voxvelo.bikes.track;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.michidk.voxvelo.bikes.VoxVelo;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Operator-only, incrementally applied world edits. One build per server. */
public final class TrackCommand {
	// The optional arguments, in the order they are typed; any tail of them may be left off.
	private static final String LENGTH = "length_km";
	private static final String MATERIAL = "material";
	private static final String CHECKPOINTS = "checkpoints";
	private static final String BENDS = "bends";
	private static final String WIDTH = "width";
	private static final String GRADE = "grade_percent";
	private static final String VARIANT = "variant";

	private static final SimpleCommandExceptionType BUSY =
		new SimpleCommandExceptionType(Component.translatable("commands.voxvelo_bikes.track.busy"));
	private static final SimpleCommandExceptionType IDLE =
		new SimpleCommandExceptionType(Component.translatable("commands.voxvelo_bikes.track.idle"));
	private static final SimpleCommandExceptionType BAD_MATERIAL =
		new SimpleCommandExceptionType(Component.translatable("commands.voxvelo_bikes.track.bad_material"));

	private static final Map<MinecraftServer, TrackBuild> JOBS = new HashMap<>();

	private TrackCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			ArgumentBuilder<CommandSourceStack, ?> tail = Commands.argument(VARIANT, IntegerArgumentType.integer(0))
				.executes(TrackCommand::start);
			tail = Commands.argument(GRADE, DoubleArgumentType.doubleArg(1, 15)).executes(TrackCommand::start).then(tail);
			tail = Commands.argument(WIDTH, IntegerArgumentType.integer(3, 11)).executes(TrackCommand::start).then(tail);
			tail = Commands.argument(BENDS, DoubleArgumentType.doubleArg(0, 1)).executes(TrackCommand::start).then(tail);
			tail = Commands.argument(CHECKPOINTS, IntegerArgumentType.integer(8, 24)).executes(TrackCommand::start).then(tail);
			tail = Commands.argument(MATERIAL, BlockStateArgument.block(registry)).executes(TrackCommand::start).then(tail);
			tail = Commands.argument(LENGTH, DoubleArgumentType.doubleArg(0.25, 20)).executes(TrackCommand::start).then(tail);
			dispatcher.register(Commands.literal("voxvelo_bikes").then(Commands.literal("track")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(TrackCommand::start)
				.then(Commands.literal("status").executes(c -> status(c.getSource())))
				.then(Commands.literal("cancel").executes(c -> cancel(c.getSource())))
				.then(tail)));
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			TrackBuild job = JOBS.get(server);
			if (job == null) return;
			try {
				if (job.tick()) JOBS.remove(server);
			} catch (RuntimeException e) {
				JOBS.remove(server);
				job.fail(Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName()));
				VoxVelo.LOGGER.error("Track build stopped", e);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TrackBuild job = JOBS.remove(server);
			if (job != null) job.cancel();
		});
	}

	private static int start(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		CommandSourceStack source = c.getSource();
		if (JOBS.containsKey(source.getServer())) {
			throw BUSY.create();
		}
		double km = optional(c, LENGTH, Double.class, 10.0);
		BlockState material = has(c, MATERIAL) ? c.getArgument(MATERIAL, BlockInput.class).getState() : Blocks.SMOOTH_STONE.defaultBlockState();
		int checkpoints = optional(c, CHECKPOINTS, Integer.class, 12);
		double bends = optional(c, BENDS, Double.class, 0.85);
		int width = optional(c, WIDTH, Integer.class, 6);
		double grade = optional(c, GRADE, Double.class, 8.0) / 100;
		int variant = optional(c, VARIANT, Integer.class, 0);
		BlockPos origin = BlockPos.containing(source.getPosition());
		if (!material.isCollisionShapeFullBlock(source.getLevel(), origin) || !material.getFluidState().isEmpty()
			|| material.getDestroySpeed(source.getLevel(), origin) < 0
			|| material.hasBlockEntity() || material.getBlock() instanceof FallingBlock
			|| material.is(Blocks.MAGMA_BLOCK) || material.is(Blocks.CACTUS)) {
			throw BAD_MATERIAL.create();
		}
		long seed = source.getLevel().getSeed() ^ ((long) origin.getX() * 341873128712L)
			^ ((long) origin.getZ() * 132897987541L) ^ variant;
		TrackGeometry.Route route = TrackGeometry.generate(seed, origin.getX(), origin.getZ(), km * 1000, checkpoints, bends, width);
		JOBS.put(source.getServer(), new TrackBuild(source, route, material, grade));
		source.sendSuccess(() -> Component.translatable("commands.voxvelo_bikes.track.planning", km, origin.getX(), origin.getZ()), true);
		return 1;
	}

	/** Whether the command was typed with this argument; later optional arguments are absent if an earlier one is. */
	private static boolean has(CommandContext<CommandSourceStack> c, String argument) {
		return c.getNodes().stream().anyMatch(node -> node.getNode().getName().equals(argument));
	}

	private static <T> T optional(CommandContext<CommandSourceStack> c, String argument, Class<T> type, T fallback) {
		return has(c, argument) ? c.getArgument(argument, type) : fallback;
	}

	private static int status(CommandSourceStack source) {
		TrackBuild job = JOBS.get(source.getServer());
		source.sendSuccess(() -> job == null ? Component.translatable("commands.voxvelo_bikes.track.idle") : job.status(), false);
		return 1;
	}

	private static int cancel(CommandSourceStack source) throws CommandSyntaxException {
		TrackBuild job = JOBS.remove(source.getServer());
		if (job == null) {
			throw IDLE.create();
		}
		job.cancel();
		source.sendSuccess(() -> Component.translatable("commands.voxvelo_bikes.track.cancelled"), true);
		return 1;
	}
}
