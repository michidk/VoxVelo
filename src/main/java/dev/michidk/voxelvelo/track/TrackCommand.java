package dev.michidk.voxelvelo.track;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;

/** Operator-only, incrementally applied world edits. One build per server. */
public final class TrackCommand {
    private static final Map<MinecraftServer, Deque<TrackBuild>> JOBS = new HashMap<>();
    private TrackCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            ArgumentBuilder<CommandSourceStack, ?> tail = Commands.argument("variant", IntegerArgumentType.integer(0))
                .executes(c -> start(c, 7));
            tail = Commands.argument("grade_percent", DoubleArgumentType.doubleArg(1, 15)).executes(c -> start(c, 6)).then(tail);
            tail = Commands.argument("width", IntegerArgumentType.integer(3, 11)).executes(c -> start(c, 5)).then(tail);
            tail = Commands.argument("bends", DoubleArgumentType.doubleArg(0, 1)).executes(c -> start(c, 4)).then(tail);
            tail = Commands.argument("checkpoints", IntegerArgumentType.integer(8, 24)).executes(c -> start(c, 3)).then(tail);
            tail = Commands.argument("material", BlockStateArgument.block(registry)).executes(c -> start(c, 2)).then(tail);
            tail = Commands.argument("length_km", DoubleArgumentType.doubleArg(0.25, 20)).executes(c -> start(c, 1)).then(tail);
            dispatcher.register(Commands.literal("voxelvelo").then(Commands.literal("track")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("create").executes(c -> start(c, 0)).then(tail))));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Deque<TrackBuild> queue = JOBS.get(server);
            if (queue == null) return;
            TrackBuild job = queue.getFirst();
            try {
                if (job.tick()) queue.removeFirst();
            } catch (RuntimeException e) {
                queue.removeFirst();
                job.fail(e.getMessage());
                dev.michidk.voxelvelo.VoxelVelo.LOGGER.error("Track build stopped", e);
            }
            if (queue.isEmpty()) JOBS.remove(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(JOBS::remove);
    }

    private static int start(CommandContext<CommandSourceStack> c, int arguments) {
        CommandSourceStack source = c.getSource();
        double km = arguments >= 1 ? DoubleArgumentType.getDouble(c, "length_km") : 10;
        BlockState material = arguments >= 2 ? BlockStateArgument.getBlock(c, "material").getState() : Blocks.SMOOTH_STONE.defaultBlockState();
        int checkpoints = arguments >= 3 ? IntegerArgumentType.getInteger(c, "checkpoints") : 12;
        double bends = arguments >= 4 ? DoubleArgumentType.getDouble(c, "bends") : 0.85;
        int width = arguments >= 5 ? IntegerArgumentType.getInteger(c, "width") : 6;
        double grade = arguments >= 6 ? DoubleArgumentType.getDouble(c, "grade_percent") / 100 : 0.08;
        int variant = arguments >= 7 ? IntegerArgumentType.getInteger(c, "variant") : 0;
        BlockPos origin = BlockPos.containing(source.getPosition());
        if (!material.isCollisionShapeFullBlock(source.getLevel(), origin) || !material.getFluidState().isEmpty()
            || material.getDestroySpeed(source.getLevel(), origin) < 0
            || material.hasBlockEntity() || material.getBlock() instanceof FallingBlock
            || material.is(Blocks.MAGMA_BLOCK) || material.is(Blocks.CACTUS)) {
            return error(source, "Choose a stable, full solid road block without fluids or a block entity.");
        }
        long seed = source.getLevel().getSeed() ^ ((long) origin.getX() * 341873128712L)
            ^ ((long) origin.getZ() * 132897987541L) ^ variant;
        TrackGeometry.Route route = TrackGeometry.generate(seed, origin.getX(), origin.getZ(), km * 1000, checkpoints, bends, width);
        TrackBuild job;
        try { job = new TrackBuild(source, route, material, grade); }
        catch (IllegalArgumentException e) { return error(source, e.getMessage()); }
        JOBS.computeIfAbsent(source.getServer(), ignored -> new ArrayDeque<>()).addLast(job);
        source.sendSuccess(() -> Component.literal("Planning " + km + " km track starting at " + origin.getX() + ", " + origin.getZ()
            + ". Construction will finish automatically."), true);
        return 1;
    }

    private static int error(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
