package dev.michidk.voxvelo.bikes.client.mixin;

import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.bikes.bike.BikeHitboxPart;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sends a normal server attack for creative bike removal without playing a hand swing. */
@Mixin(Minecraft.class)
public abstract class CreativeBikeAttackMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void voxvelo$removeCreativeBike(CallbackInfoReturnable<Boolean> result) {
		Minecraft client = (Minecraft) (Object) this;
		if (client.player == null || client.gameMode == null || !client.player.isCreative()
			|| client.player.isHandsBusy() || !(client.hitResult instanceof EntityHitResult hit)) return;
		if (!(hit.getEntity() instanceof BikeEntity) && !(hit.getEntity() instanceof BikeHitboxPart)) return;
		if (!client.player.isWithinEntityInteractionRange(hit.getEntity(), 0.0)) return;
		client.gameMode.attack(client.player, hit.getEntity());
		result.setReturnValue(false);
	}
}
