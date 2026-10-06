package dev.michidk.voxelvelo.item;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.bike.BikeComponents;
import dev.michidk.voxelvelo.bike.BikeDurability;
import dev.michidk.voxelvelo.bike.BikeType;
import dev.michidk.voxelvelo.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> lines,
            net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(net.minecraft.network.chat.Component.translatable("tooltip.voxelvelo.bike_use"));
        float damage = BikeComponents.vehicleDamage(stack);
        if (damage > 0.0F) lines.accept(net.minecraft.network.chat.Component.translatable(
                "tooltip.voxelvelo.bike_condition", BikeDurability.conditionPercent(damage, bikeType)));
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
			stack.consume(1, player);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS;
	}
}
