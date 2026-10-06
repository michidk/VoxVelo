package dev.michidk.voxelvelo.bike;

import dev.michidk.voxelvelo.item.BikeHelmetItem;
import dev.michidk.voxelvelo.registry.ModItems;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Everything that wears a bike down or fixes it, server side: crashes, whole-bike durability, tire wear, and the
 * wrench (repair and dismantling). Settings come from {@link BikeServerConfig}.
 */
final class BikeWear {
	private static final double CRASH_SOUND_IMPACT = 4.0;
	private static final double CRASH_DAMAGE_IMPACT = 9.0;
	/** Hard landings start wearing the tires after this many blocks of fall. */
	private static final double FALL_TIRE_WEAR_BLOCKS = 3.0;
	/** Furthest a player may be from a bike to dismantle it, squared. */
	private static final double WRENCH_REACH_SQR = 64.0;

	private final BikeEntity bike;
	private final BikeParts parts;

	BikeWear(BikeEntity bike, BikeParts parts) {
		this.bike = bike;
		this.parts = parts;
	}

	/** Running into something that took {@code impact} m/s of speed: noise, then hurt rider, tires and frame. */
	void crash(double impact) {
		if (impact > CRASH_DAMAGE_IMPACT) {
			this.wearTires(BikeServerConfig.INSTANCE.collisionTireDamage * Math.min(2, impact / 12));
		}
		if (impact > CRASH_SOUND_IMPACT) {
			this.bike.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, (float) Math.min(1.0, impact / 12.0), 0.8F);
		}
		if (impact > CRASH_DAMAGE_IMPACT && this.bike.getRider() instanceof Player rider && this.bike.level() instanceof ServerLevel level) {
			float helmet = BikeHelmetItem.isWornBy(rider) ? BikeHelmetItem.CRASH_DAMAGE_FACTOR : 1.0F;
			rider.hurtServer(level, level.damageSources().flyIntoWall(), (float) ((impact - CRASH_DAMAGE_IMPACT) * 0.5) * helmet);
		}
		this.damage(BikeDurability.crashDamage(impact));
	}

	/** A landing after {@code fallDistance} blocks. */
	void land(double fallDistance) {
		if (fallDistance > FALL_TIRE_WEAR_BLOCKS) {
			this.wearTires(BikeServerConfig.INSTANCE.collisionTireDamage * Math.min(2, (fallDistance - FALL_TIRE_WEAR_BLOCKS) / 4));
		}
		this.damage(BikeDurability.fallDamage(fallDistance));
	}

	/** Riding {@code distance} blocks on {@code surface}. */
	void ride(double distance, BikeSurface surface) {
		BikeServerConfig config = BikeServerConfig.INSTANCE;
		this.wearTires(distance * config.tireDamageRate * (1 + surface.looseness * (config.roughTerrainTireDamageMultiplier - 1)));
	}

	private void wearTires(double amount) {
		if (BikeServerConfig.INSTANCE.enableTireDamage && amount > 0) {
			this.parts.wearTires(amount, this.bike.getRider());
		}
	}

	/** Whole-bike durability; a bike worn out completely falls apart into its parts. */
	private void damage(float amount) {
		if (amount <= 0.0F || !BikeServerConfig.INSTANCE.enableVehicleDamage || this.bike.isRemoved()) {
			return;
		}
		Player rider = this.bike.getRider();
		if (rider != null && rider.isCreative() && !BikeServerConfig.INSTANCE.vehicleDamageInCreativeMode) {
			return;
		}
		float damage = BikeDurability.clampDamage(this.bike.getVehicleDamage() + amount, this.bike.getBikeType());
		this.bike.setVehicleDamage(damage);
		this.bike.setHurtTime(10);
		this.bike.setDamage(10.0F);
		if (damage >= this.bike.getBikeType().maxDurability) {
			this.dropParts();
		}
	}

	void repairWithWrench(Player player) {
		if (!this.bike.getPassengers().isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.voxelvelo.repair_occupied"));
			return;
		}
		if (this.bike.getVehicleDamage() <= 0.0F) {
			player.sendOverlayMessage(Component.translatable("message.voxelvelo.already_repaired"));
			return;
		}
		this.bike.setVehicleDamage(0.0F);
		player.getItemInHand(InteractionHand.MAIN_HAND).hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		this.bike.playSound(SoundEvents.ANVIL_USE, 0.7F, 1.4F);
		player.sendOverlayMessage(Component.translatable("message.voxelvelo.repaired"));
	}

	void dismantleWithWrench(Player player, InteractionHand hand) {
		if (this.bike.level().isClientSide() || this.bike.isRemoved() || !player.getItemInHand(hand).is(ModItems.BIKE_WRENCH)
			|| this.bike.distanceToSqr(player) > WRENCH_REACH_SQR) {
			return;
		}
		if (!this.bike.getPassengers().isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.voxelvelo.dismantle_occupied"));
			return;
		}
		player.getItemInHand(hand).hurtAndBreak(1, player, hand);
		this.dropParts();
	}

	/** Removes the bike and drops its frame (in the bike's color) and every installed part. */
	private void dropParts() {
		if (!(this.bike.level() instanceof ServerLevel level) || this.bike.isRemoved()) {
			return;
		}
		ItemStack frame = new ItemStack(ModItems.frameFor(this.bike.getBikeType()));
		BikeComponents.color(frame, this.bike.getFrameColor());
		List<ItemStack> drops = this.parts.takeAll();
		drops.addFirst(frame);
		double x = this.bike.getX(), y = this.bike.getY() + 0.25, z = this.bike.getZ();
		this.bike.playSound(SoundEvents.ITEM_BREAK.value(), 1.0F, 0.8F);
		this.bike.discard(); // Remove and empty first, so another interaction cannot duplicate components.
		for (ItemStack drop : drops) {
			if (!drop.isEmpty()) {
				level.addFreshEntity(new ItemEntity(level, x, y, z, drop));
			}
		}
	}
}
