package dev.michidk.voxvelo.client.mixin;

import dev.michidk.voxvelo.client.pose.BikeRiderPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the bike riding pose after vanilla has posed the limbs. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends HumanoidRenderState> {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void voxvelo$poseBikeRider(T state, CallbackInfo ci) {
		BikeRiderPose pose = state.getData(BikeRiderPose.KEY);
		if (pose != null) {
			HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;
			pose.apply(self.body, self.head, self.rightArm, self.leftArm, self.rightLeg, self.leftLeg);
		}
	}
}
