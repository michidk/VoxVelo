package dev.michidk.voxvelo.fitnesslib.client.mixin;
import dev.michidk.voxvelo.fitnesslib.client.ride.RidePhotos;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Positions photo frames beside the rider without changing the player's rotation or position. */
@Mixin(Camera.class)
public abstract class RidePhotoCameraMixin {
@Shadow protected abstract void setPosition(Vec3 position);
@Shadow protected abstract void setRotation(float yaw, float pitch);
@Inject(method = "update", at = @At("RETURN"))
private void voxvelo$photoCamera(DeltaTracker delta, CallbackInfo ci) {
if (!RidePhotos.detachedPhoto()) return;
var client = Minecraft.getInstance();
if (client.player == null || client.level == null) return;
var vehicle = client.player.getVehicle();
if (vehicle == null) return;
Vec3 focus = vehicle.position().add(0, 1.1, 0);
double yaw = Math.toRadians(vehicle.getYRot() + (RidePhotos.shotSequence() % 2 == 0 ? 55 : -55));
Vec3 desired = focus.add(Math.sin(yaw) * 5, 2, -Math.cos(yaw) * 5);
var hit = client.level.clip(new ClipContext(focus, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
Vec3 offset = hit.getLocation().subtract(focus);
double length = offset.length();
Vec3 position = focus.add(offset.scale(length > 0.3 ? (length - 0.3) / length : 0));
Vec3 look = focus.subtract(position);
this.setPosition(position);
this.setRotation((float) Math.toDegrees(Math.atan2(-look.x, look.z)), (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))));
}
}
