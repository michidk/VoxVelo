package dev.michidk.voxelvelo.client.render;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.bike.BikeType;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Procedural bicycle models (no external model file), one layer per {@link BikeType}, so they can be
 * swapped for authored models later by replacing only this class and the textures.
 *
 * <p>Model space: 1 unit = 1/16 block, +Y is down, the front of the bike points to -Z, the origin is the
 * height of the wheel axles. Each texture is four flat colour bands (frame, tyre, trim, metal).
 */
public class BikeModel extends EntityModel<BikeRenderState> {
	private static final int TEX_W = 64;
	private static final int TEX_H = 96;
	private static final int BAND_FRAME = 0;
	private static final int BAND_TYRE = 24;
	private static final int BAND_TRIM = 48;
	private static final int BAND_METAL = 72;

	private static final float WHEEL_RADIUS = 6.0F;
	private static final float AXLE_Z = 8.0F;

	/** What makes the three bikes look different. */
	private record Spec(float tyreWidth, float tube, float barHeight, float barWidth, boolean dropBars, boolean suspension) {
		static Spec of(BikeType type) {
			return switch (type) {
				case ROAD -> new Spec(1.4F, 1.2F, 16.0F, 8.0F, true, false);
				case GRAVEL -> new Spec(2.2F, 1.5F, 17.0F, 11.0F, true, false);
				case MOUNTAIN -> new Spec(3.2F, 1.9F, 19.0F, 17.0F, false, true);
			};
		}
	}

	private final ModelPart rearWheel;
	private final ModelPart frontAssembly;
	private final ModelPart frontWheel;
	private final ModelPart crank;

	public BikeModel(ModelPart root) {
		super(root);
		this.rearWheel = root.getChild("rear_wheel");
		this.frontAssembly = root.getChild("front_assembly");
		this.frontWheel = this.frontAssembly.getChild("front_wheel");
		this.crank = root.getChild("crank");
	}

	public static ModelLayerLocation layer(BikeType type) {
		return new ModelLayerLocation(VoxelVelo.id("bicycle"), type.id);
	}

	public static LayerDefinition createLayer(BikeType type) {
		Spec spec = Spec.of(type);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		addWheel(root, "rear_wheel", PartPose.offset(0.0F, 0.0F, AXLE_Z), spec.tyreWidth);
		addFront(root, spec);
		addFrame(root, spec);
		addDrivetrain(root);

		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	private static void addFront(PartDefinition root, Spec spec) {
		PartDefinition front = root.addOrReplaceChild("front_assembly", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -AXLE_Z));
		addWheel(front, "front_wheel", PartPose.ZERO, spec.tyreWidth);

		float leg = spec.suspension ? 1.7F : 1.0F;
		front.addOrReplaceChild("fork_left", box(BAND_METAL, -3.0F - leg / 2.0F, -12.0F, -leg / 2.0F, leg, 12.0F, leg), PartPose.ZERO);
		front.addOrReplaceChild("fork_right", box(BAND_METAL, 3.0F - leg / 2.0F, -12.0F, -leg / 2.0F, leg, 12.0F, leg), PartPose.ZERO);
		front.addOrReplaceChild("fork_crown", box(BAND_METAL, -3.5F, -12.5F, -0.9F, 7.0F, 1.2F, 1.8F), PartPose.ZERO);
		if (spec.suspension) {
			// Dark stanchions above the lowers make the suspension fork readable.
			front.addOrReplaceChild("stanchion_left", box(BAND_TRIM, -3.0F - 0.6F, -16.0F, -0.6F, 1.2F, 3.8F, 1.2F), PartPose.ZERO);
			front.addOrReplaceChild("stanchion_right", box(BAND_TRIM, 3.0F - 0.6F, -16.0F, -0.6F, 1.2F, 3.8F, 1.2F), PartPose.ZERO);
		}

		float top = spec.barHeight;
		front.addOrReplaceChild("steerer", box(BAND_METAL, -0.75F, -(top - 1.0F), -0.75F, 1.5F, top - 13.0F, 1.5F), PartPose.ZERO);
		float half = spec.barWidth / 2.0F;
		front.addOrReplaceChild("handlebar", box(BAND_TRIM, -half, -top, 0.0F, spec.barWidth, 1.2F, 1.2F), PartPose.ZERO);
		front.addOrReplaceChild("grip_left", box(BAND_TRIM, -half - 1.0F, -top - 0.3F, -0.4F, 1.6F, 1.8F, 2.0F), PartPose.ZERO);
		front.addOrReplaceChild("grip_right", box(BAND_TRIM, half - 0.6F, -top - 0.3F, -0.4F, 1.6F, 1.8F, 2.0F), PartPose.ZERO);
		if (spec.dropBars) {
			// Forward hoods plus downward drops, flared outward on the gravel bike.
			float flare = spec.barWidth > 9.0F ? 1.0F : 0.0F;
			for (int side = -1; side <= 1; side += 2) {
				float x = side * (half + flare);
				String s = side < 0 ? "left" : "right";
				front.addOrReplaceChild("hood_" + s, box(BAND_TRIM, x - 0.6F, -top, -3.4F, 1.2F, 1.2F, 3.6F), PartPose.ZERO);
				front.addOrReplaceChild("drop_" + s, box(BAND_TRIM, x - 0.6F, -top, -3.4F, 1.2F, 5.0F, 1.2F), PartPose.ZERO);
			}
		}
	}

	private static void addFrame(PartDefinition root, Spec spec) {
		float t = spec.tube;
		// Frame in the YZ plane: bottom bracket, seat cluster and head tube positions.
		float bbY = 0.0F, bbZ = 1.0F;
		float seatY = -13.0F, seatZ = 3.0F;
		float headTopY = -13.0F, headBottomY = -11.0F, headZ = -8.0F;
		tube(root, "chain_stay", BAND_FRAME, t * 0.8F, bbY, bbZ, 0.0F, AXLE_Z);
		tube(root, "seat_stay", BAND_FRAME, t * 0.8F, seatY, seatZ, 0.0F, AXLE_Z);
		tube(root, "seat_tube", BAND_FRAME, t, bbY, bbZ, seatY, seatZ);
		tube(root, "top_tube", BAND_FRAME, t, seatY, seatZ, headTopY, headZ);
		tube(root, "down_tube", BAND_FRAME, t * 1.15F, bbY, bbZ, headBottomY, headZ);
		tube(root, "head_tube", BAND_FRAME, t * 1.1F, headBottomY, headZ, -15.0F, headZ);
		if (spec.suspension) {
			tube(root, "rear_shock", BAND_TRIM, 1.5F, -9.0F, 2.2F, -2.0F, 5.0F);
		}
		root.addOrReplaceChild("seat_post", box(BAND_METAL, -0.5F, -16.0F, 2.5F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
		root.addOrReplaceChild("seat", box(BAND_TRIM, -2.0F, -17.0F, 0.5F, 4.0F, 1.5F, 7.0F), PartPose.ZERO);
	}

	private static void addDrivetrain(PartDefinition root) {
		PartDefinition crankPart = root.addOrReplaceChild("crank", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 1.0F));
		crankPart.addOrReplaceChild("crank_axle", box(BAND_METAL, -3.0F, -0.75F, -0.75F, 6.0F, 1.5F, 1.5F), PartPose.ZERO);
		crankPart.addOrReplaceChild("arm_right", box(BAND_METAL, 2.5F, -0.5F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.ZERO);
		crankPart.addOrReplaceChild("arm_left", box(BAND_METAL, -3.5F, -5.5F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.ZERO);
		crankPart.addOrReplaceChild("pedal_right", box(BAND_TRIM, 3.0F, 5.0F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.ZERO);
		crankPart.addOrReplaceChild("pedal_left", box(BAND_TRIM, -6.0F, -6.0F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.ZERO);
	}

	private static CubeListBuilder box(int band, float x, float y, float z, float w, float h, float d) {
		return CubeListBuilder.create().texOffs(0, band).addBox(x, y, z, w, h, d);
	}

	/** Wheel centered on its own origin: tyre ring of 8 segments, 4 spokes and a hub. */
	private static void addWheel(PartDefinition parent, String name, PartPose pose, float tyreWidth) {
		PartDefinition wheel = parent.addOrReplaceChild(name, CubeListBuilder.create(), pose);
		wheel.addOrReplaceChild("hub", box(BAND_METAL, -1.5F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F), PartPose.ZERO);
		for (int i = 0; i < 8; i++) {
			wheel.addOrReplaceChild(
				"tyre_" + i,
				box(BAND_TYRE, -tyreWidth / 2.0F, -WHEEL_RADIUS, -2.4F, tyreWidth, 1.3F, 4.8F),
				PartPose.rotation(i * (float) (Math.PI / 4.0), 0.0F, 0.0F)
			);
		}
		for (int i = 0; i < 4; i++) {
			wheel.addOrReplaceChild(
				"spoke_" + i,
				box(BAND_METAL, -0.25F, -(WHEEL_RADIUS - 1.0F), -0.25F, 0.5F, 2.0F * (WHEEL_RADIUS - 1.0F), 0.5F),
				PartPose.rotation(i * (float) (Math.PI / 4.0), 0.0F, 0.0F)
			);
		}
	}

	/** A square tube of the given thickness between two points in the centre YZ plane (x = 0). */
	private static void tube(PartDefinition parent, String name, int band, float thickness, float y1, float z1, float y2, float z2) {
		float dy = y2 - y1;
		float dz = z2 - z1;
		float length = (float) Math.hypot(dy, dz);
		float angle = (float) Math.atan2(-dy, dz);
		parent.addOrReplaceChild(
			name,
			box(band, -thickness / 2.0F, -thickness / 2.0F, -length / 2.0F, thickness, thickness, length),
			PartPose.offsetAndRotation(0.0F, (y1 + y2) / 2.0F, (z1 + z2) / 2.0F, angle, 0.0F, 0.0F)
		);
	}

	@Override
	public void setupAnim(BikeRenderState state) {
		super.setupAnim(state);
		this.rearWheel.xRot = state.wheelAngle;
		this.frontWheel.xRot = state.wheelAngle;
		this.frontAssembly.yRot = state.steering * state.maxSteerAngle;
		this.crank.xRot = state.crankAngle;
	}
}
