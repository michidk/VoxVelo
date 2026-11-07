package dev.michidk.voxvelo.bikes.client.network;
import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.bikes.network.UnstuckPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
/** Shared keyboard and controller entrypoint for manual bike recovery. */
public final class ManualUnstuck {
public static void request() {
var client = Minecraft.getInstance();
if (client.gui.screen() == null && BikeEntity.riddenBy(client.player) != null && ClientPlayNetworking.canSend(UnstuckPayload.TYPE))
ClientPlayNetworking.send(new UnstuckPayload());
}
}
