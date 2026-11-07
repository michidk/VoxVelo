package dev.michidk.voxvelo.bikes.network;
import dev.michidk.voxvelo.bikes.VoxVelo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** Requests safe recovery of the sender's ridden bike. */
public record UnstuckPayload() implements CustomPacketPayload {
public static final Type<UnstuckPayload> TYPE = new Type<>(VoxVelo.id("unstuck"));
public static final StreamCodec<RegistryFriendlyByteBuf, UnstuckPayload> CODEC = StreamCodec.unit(new UnstuckPayload());
@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
