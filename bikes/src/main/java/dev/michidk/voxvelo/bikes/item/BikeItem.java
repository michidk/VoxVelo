package dev.michidk.voxvelo.bikes.item;

import dev.michidk.voxvelo.bikes.bike.BikeComponents;
import dev.michidk.voxvelo.bikes.bike.BikeDurability;
import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.bikes.bike.BikeType;
import dev.michidk.voxvelo.bikes.registry.ModEntities;
import dev.michidk.voxvelo.bikes.registry.ModSounds;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Places a {@link BikeEntity} of a fixed {@link BikeType} where the player is looking, like a boat or minecart. */
public class BikeItem extends Item {
	private final BikeType bikeType;

	public BikeItem(BikeType bikeType, Item.Properties properties) {
		super(properties);
		this.bikeType = bikeType;
	}

	public BikeType bikeType() {
		return this.bikeType;
	}

	// Item tooltips are still added by overriding this hook, which Mojang marks deprecated.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines,
		TooltipFlag flag) {
		super.appendHoverText(stack, context, display, lines, flag);
		lines.accept(Component.translatable("tooltip.voxvelo_bikes.bike_use"));
		float damage = BikeComponents.vehicleDamage(stack);
		if (damage > 0.0F) {
			lines.accept(Component.translatable("tooltip.voxvelo_bikes.bike_condition", BikeDurability.conditionPercent(damage, this.bikeType)));
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}

		BikeEntity bike = ModEntities.BICYCLE.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (bike == null) {
			return InteractionResult.FAIL;
		}
		Vec3 location = hit.getLocation();
		bike.setBikeType(this.bikeType);
		bike.loadFromItem(stack);
		bike.setInitialPos(location.x, location.y, location.z);
		bike.setYRot(player.getYRot());
		if (level instanceof ServerLevel serverLevel) {
			EntityType.<BikeEntity>createDefaultStackConfig(serverLevel, stack, player).apply(bike);
		}
		if (!level.noCollision(bike, bike.getBoundingBox())) {
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide()) {
			if (!level.addFreshEntity(bike)) return InteractionResult.FAIL;
			level.gameEvent(player, GameEvent.ENTITY_PLACE, location);
			bike.playSound(ModSounds.BIKE_PLACE, 0.8F, 1.0F);
			stack.consume(1, player);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS;
	}
}
