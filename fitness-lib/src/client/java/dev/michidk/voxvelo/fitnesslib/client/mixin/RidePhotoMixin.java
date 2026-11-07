package dev.michidk.voxvelo.fitnesslib.client.mixin;

import dev.michidk.voxvelo.fitnesslib.client.ride.RidePhotos;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prepares the camera before extraction and captures after the frame has rendered. */
@Mixin(GameRenderer.class)
public abstract class RidePhotoMixin {
	@Inject(method = "extract", at = @At("HEAD"))
	private void voxvelo$preparePhoto(CallbackInfo ci) { RidePhotos.prepare(); }
	@Inject(method = "extract", at = @At("RETURN"))
private void voxvelo$restoreView(CallbackInfo ci) { RidePhotos.restoreExtractedView(); }
@Inject(method = "render", at = @At("RETURN"))
	private void voxvelo$capturePhoto(CallbackInfo ci) { RidePhotos.capture(); }
}
