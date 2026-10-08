package dev.michidk.voxvelo.bikes.client.mixin;

import dev.michidk.voxvelo.bikes.client.pose.BikeRiderPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records, per rendered player, whether they sit on a bike and where the pedals are. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void voxvelo$extractBikePose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		state.setData(BikeRiderPose.KEY, BikeRiderPose.of(entity.getVehicle()));
	}
}
