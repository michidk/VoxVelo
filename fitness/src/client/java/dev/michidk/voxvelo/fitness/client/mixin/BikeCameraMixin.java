package dev.michidk.voxvelo.fitness.client.mixin;

import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.fitness.client.fitness.FitnessContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps only the local rider's view aligned with the interpolated bike heading. */
@Mixin(Entity.class)
public abstract class BikeCameraMixin {
	@Unique private @Nullable BikeEntity voxvelo$cameraBike() {
		Minecraft client = Minecraft.getInstance();
		if ((Object) this != client.player || !FabricLoader.getInstance().isModLoaded("voxvelo_fitness_lib")) return null;
		FitnessContext context = FitnessContext.get();
		return context != null && context.config.bikeCameraFollow && client.player != null
			&& client.player.getVehicle() instanceof BikeEntity bike ? bike : null;
	}

	@Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
	private void voxvelo$levelView(float partialTick, CallbackInfoReturnable<Float> result) {
		if (this.voxvelo$cameraBike() != null) result.setReturnValue(0.0F);
	}

	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void voxvelo$ignoreMouseLook(double horizontal, double vertical, CallbackInfo callback) {
		if (this.voxvelo$cameraBike() != null) callback.cancel();
	}
}
