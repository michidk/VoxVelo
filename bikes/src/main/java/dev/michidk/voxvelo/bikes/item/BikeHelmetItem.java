package dev.michidk.voxvelo.bikes.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import dev.michidk.voxvelo.bikes.VoxVelo;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * A dyeable cycling helmet: light head armor, like a leather cap, that also takes the edge off bike crashes. It is
 * drawn by GeckoLib with its own model (see the client's BikeHelmetRenderer), not by the vanilla armor layers.
 */
public class BikeHelmetItem extends Item implements GeoItem {
	/** Share of the damage a crash on the bike still does to a rider wearing a helmet. */
	public static final float CRASH_DAMAGE_FACTOR = 0.5F;

	public static final ArmorMaterial MATERIAL = new ArmorMaterial(8,
			Stream.of(ArmorType.values()).collect(Collectors.toMap(Function.identity(), type -> type == ArmorType.HELMET ? 1 : 0)),
			15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.WOOL,
			ResourceKey.create(EquipmentAssets.ROOT_ID, VoxVelo.id("bike_helmet")));

	/**
	 * The GeckoLib render provider, set by the client entrypoint. Typed as Object because GeckoLib's provider type
	 * refers to client-only classes this common code cannot see; GeckoLib only asks for it on the client.
	 */
	public static Object renderProvider;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public BikeHelmetItem(Properties properties) {
		super(properties.humanoidArmor(MATERIAL, ArmorType.HELMET));
	}

	public static boolean isWornBy(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BikeHelmetItem;
	}

	// Item tooltips are still added by overriding this hook, which Mojang marks deprecated.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines,
		TooltipFlag flag) {
		super.appendHoverText(stack, context, display, lines, flag);
		lines.accept(Component.translatable("tooltip.voxvelo_bikes.bike_helmet"));
	}

	@Override
	public Object getRenderProvider() {
		return renderProvider;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// Static model, nothing to animate.
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
