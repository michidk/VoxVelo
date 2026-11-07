package dev.michidk.voxvelo.fitness.client.mixin;
import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.fitness.client.fitness.FitnessContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Aligns the local rider through LocalPlayer's view yaw override. */
@Mixin(LocalPlayer.class)
public abstract class BikeCameraYawMixin {
@Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
private void voxvelo$lookAhead(float partialTick, CallbackInfoReturnable<Float> result) {
Minecraft client = Minecraft.getInstance();
if ((Object) this != client.player || !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voxvelo_fitness_lib")) return;
FitnessContext context = FitnessContext.get();
if (context == null || !context.config.bikeCameraFollow) return;
BikeEntity bike = BikeEntity.riddenBy(client.player);
if (bike != null) result.setReturnValue(bike.getViewYRot(partialTick));
}
}
