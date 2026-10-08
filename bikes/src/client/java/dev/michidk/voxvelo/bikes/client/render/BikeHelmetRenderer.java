package dev.michidk.voxvelo.bikes.client.render;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.michidk.voxvelo.bikes.VoxVelo;
import dev.michidk.voxvelo.bikes.item.BikeHelmetItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Draws the bike helmet on the wearer's head. GeckoLib follows the vanilla head (turning, sneaking, the bike pose) by
 * moving the model's {@code armorHead} bone, which sits at the biped head pivot (Y 24, the neck).
 *
 * <p>Only the shell takes the dye color; trim, straps and buckle keep their own colors.
 */
public final class BikeHelmetRenderer extends GeoArmorRenderer<BikeHelmetItem, HumanoidRenderState> {
	private static final Identifier TEXTURE = VoxVelo.id("textures/armor/bike_helmet.png");
	private static final DataTicket<Integer> SHELL_COLOR = DataTicket.create("voxvelo_helmet_color", new TypeToken<>() {});

	private BikeHelmetRenderer() {
		super(new Model());
		withRenderLayer(new ShellLayer(this));
	}

	/** Hands the renderer to the helmet item; GeckoLib asks for it the first time a helmet is drawn. */
	public static void register() {
		BikeHelmetItem.renderProvider = new GeoRenderProvider() {
			private BikeHelmetRenderer renderer;

			@Override
			public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
				if (renderer == null) renderer = new BikeHelmetRenderer();
				return renderer;
			}
		};
	}

	private static final class Model extends GeoModel<BikeHelmetItem> {
		@Override public Identifier getModelResource(GeoRenderState state) { return VoxVelo.id("armor/bike_helmet"); }
		@Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
		@Override public Identifier getAnimationResource(BikeHelmetItem helmet) { return VoxVelo.id("armor/bike_helmet"); }
	}

	/** Draws {@code helmet_shell} in the dye color (white, so untinted, by default); GeckoLib hides it in the normal pass. */
	private static final class ShellLayer extends CustomBoneTextureGeoLayer<BikeHelmetItem, RenderData, HumanoidRenderState> {
		ShellLayer(BikeHelmetRenderer renderer) {
			super(renderer, "helmet_shell", TEXTURE);
		}

		@Override
		public void addRenderData(BikeHelmetItem helmet, RenderData data, HumanoidRenderState state, float partialTick) {
			DyedItemColor dye = data.itemStack().get(DataComponents.DYED_COLOR);
			state.addGeckolibData(SHELL_COLOR, dye == null ? 0xFFFFFF : dye.rgb());
		}

		@Override
		protected void renderBone(RenderPassInfo<HumanoidRenderState> info, GeoBone bone, SubmitNodeCollector collector) {
			var renderType = getRenderType(info.renderState(), TEXTURE);
			if (renderType == null) return;
			int color = 0xFF000000 | info.getOrDefaultGeckolibData(SHELL_COLOR, 0xFFFFFF);
			int light = info.packedLight(), overlay = info.packedOverlay();
			collector.submitCustomGeometry(info.poseStack(), renderType, (pose, vertices) -> {
				PoseStack stack = new PoseStack();
				stack.last().set(pose);
				bone.translateAwayFromPivotPoint(stack);
				// The shell has no child bones: trim, straps and buckle are its siblings.
				BoneGeometry.draw(this, bone, stack, vertices, light, overlay, color);
			});
		}
	}
}
