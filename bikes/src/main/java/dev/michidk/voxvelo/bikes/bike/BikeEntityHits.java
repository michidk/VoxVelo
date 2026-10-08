package dev.michidk.voxvelo.bikes.bike;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Riding into a living entity (mob or player) shoves or hurts it depending on how fast the two close in, and costs
 * the bike speed in proportion to what it hit, see {@link BikeImpact}. Riders of other bikes are left to the solid
 * bike-on-bike collision instead, see {@link BikeEntity#canBeCollidedWith}.
 *
 * <p>Both sides run the same sweep: the side that simulates the bike takes the speed loss, and only the server hurts,
 * pushes and makes noise. Each side keeps its own hit cooldowns, so they agree on which contacts count.
 */
final class BikeEntityHits {
	private static final int HIT_COOLDOWN_TICKS = 10;

	/**
	 * @param speedLoss m/s the bike loses to everything it ran into
	 * @param crash     the part of it lost to damaging hits; stopping short like that hurts the rider like a wall
	 */
	record Result(double speedLoss, double crash) {
		static final Result NONE = new Result(0.0, 0.0);
	}

	private final BikeEntity bike;
	private final Map<UUID, Integer> recentHits = new HashMap<>();

	BikeEntityHits(BikeEntity bike) {
		this.bike = bike;
	}

	Result strike(double speed, double forwardX, double forwardZ, BikePhysics.Params params) {
		if (speed < BikeImpact.TOUCH_SPEED) {
			return Result.NONE;
		}
		int tick = this.bike.tickCount;
		this.recentHits.values().removeIf(last -> tick - last > HIT_COOLDOWN_TICKS * 2);
		ServerLevel server = this.bike.level() instanceof ServerLevel serverLevel ? serverLevel : null;

		// Sweep the whole distance covered this tick so a fast bike cannot skip over a creature.
		double reach = speed * BikeMotion.DT + 0.3;
		AABB area = this.bike.getBoundingBox().expandTowards(forwardX * reach, 0.0, forwardZ * reach).inflate(0.15, 0.0, 0.15);
		double rightX = -forwardZ;
		double rightZ = forwardX;
		double remaining = speed;
		double crash = 0.0;
		for (Entity target : this.bike.level().getEntities(this.bike, area, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
			&& !this.bike.isPassengerOfSameVehicle(e) && !(e.getVehicle() instanceof BikeEntity))) {
			double offsetX = target.getX() - this.bike.getX();
			double offsetZ = target.getZ() - this.bike.getZ();
			if (offsetX * forwardX + offsetZ * forwardZ <= 0.0) {
				continue;
			}
			Integer last = this.recentHits.get(target.getUUID());
			if (last != null && tick - last < HIT_COOLDOWN_TICKS) {
				continue;
			}
			LivingEntity living = (LivingEntity) target;
			Vec3 motion = living.getDeltaMovement();
			double along = (motion.x * forwardX + motion.z * forwardZ) / BikeMotion.DT;
			BikeImpact.Outcome outcome = BikeImpact.resolve(remaining, along, params.totalMass(), BikeImpact.massOf(target.getBbWidth(), target.getBbHeight()));
			if (outcome.kind() == BikeImpact.Kind.NONE) {
				continue;
			}
			if (outcome.kind() == BikeImpact.Kind.HIT) {
				this.recentHits.put(target.getUUID(), tick);
				crash += outcome.speedLoss();
			}
			if (server != null) {
				this.affect(server, living, outcome, remaining - along, offsetX * rightX + offsetZ * rightZ, forwardX, forwardZ);
			}
			remaining = Math.max(0.0, remaining - outcome.speedLoss());
		}
		return new Result(speed - remaining, crash);
	}

	/** Hurts or shoves the creature, sending it ahead and a little away from the bike's line. */
	private void affect(ServerLevel level, LivingEntity living, BikeImpact.Outcome outcome, double closing, double sideOffset,
		double forwardX, double forwardZ) {
		double side = Mth.clamp(sideOffset * 0.8, -0.5, 0.5);
		double dirX = forwardX - forwardZ * side;
		double dirZ = forwardZ + forwardX * side;
		double length = Math.hypot(dirX, dirZ);
		dirX /= length;
		dirZ /= length;

		boolean hurt = false;
		if (outcome.kind() == BikeImpact.Kind.HIT) {
			Player rider = this.bike.getRider();
			DamageSource source = rider != null ? level.damageSources().playerAttack(rider) : level.damageSources().generic();
			hurt = living.hurtServer(level, source, outcome.damage());
			if (hurt) {
				living.knockback(outcome.knockback(), -dirX, -dirZ, source, outcome.damage());
				level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(),
					6 + (int) outcome.damage(), 0.25, 0.2, 0.25, 0.2);
				this.bike.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.9F);
				this.bike.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, (float) Math.min(1.0, closing / 10.0), 1.0F);
			}
		}
		if (!hurt) {
			// A shove, or a hit that could not hurt (peaceful rules, no PvP, invulnerable): still push it out of the way.
			double strength = outcome.kind() == BikeImpact.Kind.SHOVE ? outcome.knockback() : 0.08 + 0.03 * closing;
			living.push(dirX * strength, 0.02, dirZ * strength);
			if (outcome.kind() == BikeImpact.Kind.HIT) {
				this.bike.playSound(SoundEvents.PLAYER_ATTACK_WEAK, 0.8F, 1.0F);
			}
		}
	}
}
