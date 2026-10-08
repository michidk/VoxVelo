package dev.michidk.voxvelo.bike;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Where the rider sits on a bike, how far they can look around from it, and where they step off. */
final class BikeSeat {
	/** Furthest the rider can turn from the bike's heading, degrees: over the shoulder, not all the way round. */
	private static final float MAX_LOOK_YAW = 105.0F;

	private final BikeEntity bike;

	BikeSeat(BikeEntity bike) {
		this.bike = bike;
	}

	/** The saddle relative to the bike, lifted or lowered by {@code suspensionOffset} blocks. */
	Vec3 attachmentPoint(double suspensionOffset) {
		BikeRig rig = this.bike.getBikeType().rig;
		return new Vec3(0.0, rig.seatHeight() + suspensionOffset, rig.seatOffset()).yRot(-this.bike.getYRot() * Mth.DEG_TO_RAD);
	}

	/** Faces the rider's body along the bike and keeps their look within reach; returns the yaw change. */
	float clampRotation(Entity passenger) {
		passenger.setYBodyRot(this.bike.getYRot());
		float delta = Mth.wrapDegrees(passenger.getYRot() - this.bike.getYRot());
		float clamped = Mth.clamp(delta, -MAX_LOOK_YAW, MAX_LOOK_YAW);
		passenger.setYRot(passenger.getYRot() + clamped - delta);
		return clamped - delta;
	}

	/** A safe spot beside the bike, trying the right side first; null if neither side has room. */
	@Nullable Vec3 dismountLocation(LivingEntity passenger) {
		double distance = (this.bike.getBbWidth() + passenger.getBbWidth()) * 0.5 + 0.2;
		double yaw = Math.toRadians(this.bike.getYRot());
		double sideX = Math.cos(yaw) * distance;
		double sideZ = Math.sin(yaw) * distance;
		Vec3 target = this.findDismountLocation(passenger, this.bike.getX() + sideX, this.bike.getZ() + sideZ);
		return target != null ? target : this.findDismountLocation(passenger, this.bike.getX() - sideX, this.bike.getZ() - sideZ);
	}

	/** Finds a safe standing position at one side of the bike, checking the bike's level and the block below it. */
	private @Nullable Vec3 findDismountLocation(LivingEntity passenger, double targetX, double targetZ) {
		BlockPos targetPos = BlockPos.containing(targetX, this.bike.getBoundingBox().maxY, targetZ);
		BlockPos belowPos = targetPos.below();
		if (this.bike.level().isWaterAt(belowPos)) {
			return null;
		}
		List<Vec3> targets = new ArrayList<>();
		double targetFloor = this.bike.level().getBlockFloorHeight(targetPos);
		if (DismountHelper.isBlockFloorValid(targetFloor)) {
			targets.add(new Vec3(targetX, targetPos.getY() + targetFloor, targetZ));
		}
		double belowFloor = this.bike.level().getBlockFloorHeight(belowPos);
		if (DismountHelper.isBlockFloorValid(belowFloor)) {
			targets.add(new Vec3(targetX, belowPos.getY() + belowFloor, targetZ));
		}
		for (Pose pose : passenger.getDismountPoses()) {
			for (Vec3 target : targets) {
				if (DismountHelper.canDismountTo(this.bike.level(), target, passenger, pose)) {
					passenger.setPose(pose);
					return target;
				}
			}
		}
		return null;
	}
}
