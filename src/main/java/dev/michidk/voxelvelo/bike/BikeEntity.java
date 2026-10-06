package dev.michidk.voxelvelo.bike;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import dev.michidk.voxelvelo.item.BikeHelmetItem;
import dev.michidk.voxelvelo.registry.ModEnchantments;
import dev.michidk.voxelvelo.registry.ModItems;
import dev.michidk.voxelvelo.terrain.LevelGround;
import dev.michidk.voxelvelo.terrain.TerrainSlope;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A real, server-authoritative bicycle. Only the server runs {@link BikePhysics}; clients receive
 * position, rotation and the small amount of visual state below through normal entity sync.
 *
 * <p>The rider is deliberately <em>not</em> the vanilla controlling passenger: that would make the
 * rider client authoritative for the vehicle position. Instead the rider client sends
 * {@link BikeControlState} intentions and the server moves the bike.
 *
 * <p>One entity class serves all {@link BikeType}s; the type only selects physics parameters, step
 * height, seat height, drop item and the model/texture on the client.
 */
public class BikeEntity extends VehicleEntity implements GeoEntity {
	public static final String ANIM_PEDAL = "animation.voxelvelo.bicycle.pedal";
	private static final RawAnimation PEDAL = RawAnimation.begin().thenLoop(ANIM_PEDAL);

	/** Server ticks without a control packet after which the rider input is considered gone. */
	public static final int CONTROL_TIMEOUT_TICKS = 15;
	/** Radius of the wheels as drawn (6.3 model units at the model scale), so the rolling speed matches the ground. */
	public static final float WHEEL_RADIUS = 6.3F * BikeRig.MODEL_SCALE / 16.0F;
	private static final int SUBMERGED_EJECT_TICKS = 40;
	private static final double DT = 1.0 / 20.0;
	/** Largest drop the bike stays glued to the ground over (down slopes, stairs). */
	private static final double GROUND_SNAP = 1.0;
	/** Blocks each way along the heading over which the terrain slope that drives gravity is measured. */
	private static final int SLOPE_PROBE_BLOCKS = 4;
	/** Share of the way to the measured slope the bike gradient moves per tick, so single steps do not jerk it. */
	private static final double SLOPE_SMOOTHING = 0.2;
	/** Rising more than this in one tick counts as hopping a ledge and costs speed. */
	private static final double LEDGE_PENALTY_HEIGHT = 0.55;
	private static final double CRASH_SOUND_IMPACT = 4.0;
	private static final double CRASH_DAMAGE_IMPACT = 9.0;
	private static final int HIT_COOLDOWN_TICKS = 10;

	private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_STEERING = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_BRAKE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_GRADIENT = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_GEAR = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_PEDALING = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_SURFACE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PARTS = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TIRES = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
    /** Installed treads and handlebar kind, see {@link BikeComponents#setup}; clients need it for handling and looks. */
    private static final EntityDataAccessor<Integer> DATA_SETUP = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_VEHICLE_DAMAGE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SPEED_BOOST = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
    /** Installed parts are private entity state, not an inventory. They change only when an item is placed or dismantled. */
    private final NonNullList<ItemStack> components = NonNullList.withSize(BikeComponents.COUNT, ItemStack.EMPTY);

	// Server-only simulation state.
	private BikeControlState control = BikeControlState.NEUTRAL;
	private int lastControlTick = Integer.MIN_VALUE / 2;
	private int lastSequence = -1;
	private double speed;
	private double steering;
	private double gradient;
	private int submergedTicks;
	private final Map<UUID, Integer> recentHits = new HashMap<>();

	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

	// Client-only animation state, derived from synced data so every client animates identically.
	private float wheelAngle;
	private float wheelAngleO;
	// Client-only suspension spring: the body lags behind sudden vertical movement (steps, landings) and rings out.
	private double suspensionOffset;
	private double suspensionOffsetO;
	private double suspensionVelocity;
	private double lastY;
	private double lastVerticalSpeed;
	private boolean hasLastY;

	public BikeEntity(EntityType<? extends BikeEntity> type, Level level) {
		super(type, level);
        initializeComponents();
	}

	public void setInitialPos(double x, double y, double z) {
		this.setPos(x, y, z);
		this.xo = x;
		this.yo = y;
		this.zo = z;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_COLOR, BikeComponents.DEFAULT_COLOR);
        entityData.define(DATA_PARTS, 31);
        entityData.define(DATA_TIRES, 100 | (100 << 8));
        entityData.define(DATA_SETUP, BikeComponents.stockSetup(BikeType.GRAVEL));
        entityData.define(DATA_VEHICLE_DAMAGE, 0.0F);
        entityData.define(DATA_SPEED_BOOST, 0);
        entityData.define(DATA_TYPE, BikeType.GRAVEL.ordinal());
		entityData.define(DATA_SPEED, 0.0F);
		entityData.define(DATA_STEERING, 0.0F);
		entityData.define(DATA_BRAKE, 0.0F);
		entityData.define(DATA_GRADIENT, 0.0F);
		entityData.define(DATA_GEAR, BikeControlState.DEFAULT_GEAR);
		entityData.define(DATA_PEDALING, false);
		entityData.define(DATA_SURFACE, BikeSurface.PATH.ordinal());
	}

	public BikeType getBikeType() {
		return BikeType.byOrdinal(this.entityData.get(DATA_TYPE));
	}

	public void setBikeType(BikeType type) {
		this.entityData.set(DATA_TYPE, type.ordinal());
        this.entityData.set(DATA_VEHICLE_DAMAGE, 0.0F);
        initializeComponents();
	}

    public int getFrameColor() { return entityData.get(DATA_COLOR); }
    public int getComponentMask() { return entityData.get(DATA_PARTS); }
    public boolean hasPart(int slot) { return (getComponentMask() & (1 << slot)) != 0; }
    public int tirePercent(int slot) { return (entityData.get(DATA_TIRES) >> (slot * 8)) & 255; }
    public int getSetup() { return entityData.get(DATA_SETUP); }
    public float getVehicleDamage() { return entityData.get(DATA_VEHICLE_DAMAGE); }
    public int getSpeedBoostLevel() { return entityData.get(DATA_SPEED_BOOST); }
    public int getConditionPercent() { return BikeDurability.conditionPercent(getVehicleDamage(), getBikeType()); }
    /** Physics parameters of the frame with the installed tires and handlebars. */
    public BikePhysics.Params params() { return BikeComponents.params(getBikeType(), getSetup()); }
    public BikeRig rig() { return BikeComponents.rig(getBikeType(), getSetup()); }
    public boolean canRide() { return (getComponentMask() & 15) == 15; }

    private void initializeComponents() {
        for (int i = 0; i < BikeComponents.COUNT; i++) components.set(i, BikeComponents.stock(getBikeType(), i));
        syncComponents();
    }

    public void syncComponents() {
        if (level().isClientSide()) return;
        int mask = 0;
        for (int i = 0; i < BikeComponents.COUNT; i++) if (!components.get(i).isEmpty()) mask |= 1 << i;
        entityData.set(DATA_PARTS, mask);
        entityData.set(DATA_TIRES, (int) Math.ceil(BikeComponents.tire(components.get(0)) * 100)
                | ((int) Math.ceil(BikeComponents.tire(components.get(1)) * 100) << 8));
        entityData.set(DATA_SETUP, BikeComponents.setup(getBikeType(), components));
        if ((mask & 15) != 15) ejectPassengers();
    }

    public void loadFromItem(ItemStack stack) {
        entityData.set(DATA_COLOR, BikeComponents.color(stack));
        var contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            NonNullList<ItemStack> items = NonNullList.withSize(BikeComponents.COUNT, ItemStack.EMPTY);
            contents.copyInto(items);
            for (int i = 0; i < items.size(); i++) {
                ItemStack part = BikeComponents.accepts(i, items.get(i)) ? items.get(i).copy() : ItemStack.EMPTY;
                // Bikes from before treads existed ran on the tires of their own type.
                BikeComponents.stampTread(part, TireTread.nativeFor(getBikeType()));
                components.set(i, part);
            }
        }
        entityData.set(DATA_VEHICLE_DAMAGE, BikeDurability.clampDamage(BikeComponents.vehicleDamage(stack), getBikeType()));
        var speedBoost = level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.SPEED_BOOST);
        entityData.set(DATA_SPEED_BOOST, BikeSpeedBoost.clampLevel(stack.getEnchantments().getLevel(speedBoost)));
        syncComponents();
    }

    public ItemStack toItem() {
        ItemStack stack = new ItemStack(ModItems.forType(getBikeType()));
        BikeComponents.color(stack, getFrameColor());
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(components));
        BikeComponents.vehicleDamage(stack, getVehicleDamage());
        if (getSpeedBoostLevel() > 0) {
            var speedBoost = level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(ModEnchantments.SPEED_BOOST);
            stack.enchant(speedBoost, getSpeedBoostLevel());
        }
        return stack;
    }

    private void useWrench(Player player, InteractionHand hand) {
        player.getItemInHand(hand).hurtAndBreak(1, player, hand);
    }

    private void destroyWithWrench(Player player, InteractionHand hand) {
        if (level().isClientSide() || isRemoved() || !player.getItemInHand(hand).is(ModItems.BIKE_WRENCH)
                || distanceToSqr(player) > 64) return;
        if (!getPassengers().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.voxelvelo.dismantle_occupied"));
            return;
        }
        useWrench(player, hand);
        dropInstalledParts();
    }

    private void dropInstalledParts() {
        if (!(level() instanceof ServerLevel serverLevel) || isRemoved()) return;
        ItemStack frame = new ItemStack(ModItems.frameFor(getBikeType()));
        BikeComponents.color(frame, getFrameColor());
        List<ItemStack> drops = new ArrayList<>();
        drops.add(frame);
        for (int i = 0; i < BikeComponents.COUNT; i++) {
            drops.add(components.get(i).copy());
            components.set(i, ItemStack.EMPTY);
        }
        double x = getX(), y = getY() + 0.25, z = getZ();
        playSound(SoundEvents.ITEM_BREAK.value(), 1.0F, 0.8F);
        discard(); // Remove and empty first, so another interaction cannot duplicate components.
        for (ItemStack drop : drops) if (!drop.isEmpty()) {
            serverLevel.addFreshEntity(new ItemEntity(serverLevel, x, y, z, drop));
        }
    }

    private void repairWithWrench(Player player) {
        if (!getPassengers().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.voxelvelo.repair_occupied"));
            return;
        }
        if (getVehicleDamage() <= 0.0F) {
            player.sendOverlayMessage(Component.translatable("message.voxelvelo.already_repaired"));
            return;
        }
        entityData.set(DATA_VEHICLE_DAMAGE, 0.0F);
        useWrench(player, InteractionHand.MAIN_HAND);
        playSound(SoundEvents.ANVIL_USE, 0.7F, 1.4F);
        player.sendOverlayMessage(Component.translatable("message.voxelvelo.repaired"));
    }

    private void damageVehicle(float amount) {
        if (amount <= 0.0F || !BikeServerConfig.INSTANCE.enableVehicleDamage || isRemoved()) return;
        Player rider = getRider();
        if (rider != null && rider.isCreative() && !BikeServerConfig.INSTANCE.vehicleDamageInCreativeMode) return;
        float damage = BikeDurability.clampDamage(getVehicleDamage() + amount, getBikeType());
        entityData.set(DATA_VEHICLE_DAMAGE, damage);
        setHurtTime(10);
        setDamage(10.0F);
        if (damage >= getBikeType().maxDurability) dropInstalledParts();
    }

    private void wearTires(double amount) {
        if (!BikeServerConfig.INSTANCE.enableTireDamage || amount <= 0) return;
        for (int slot = 0; slot < 2; slot++) {
            ItemStack wheel = components.get(slot);
            double before = BikeComponents.tire(wheel);
            if (wheel.isEmpty() || before <= 0) continue;
            double after = Math.max(0, before - amount * (slot == 0 ? 1.0 : 1.05));
            BikeComponents.tire(wheel, after);
            if (after == 0 && getRider() != null) getRider().sendSystemMessage(
                    Component.translatable(slot == 0 ? "message.voxelvelo.front_burst" : "message.voxelvelo.rear_burst"));
        }
        syncComponents();
    }

	// ---------------------------------------------------------------- simulation

	@Override
	public void tick() {
		if (this.getHurtTime() > 0) {
			this.setHurtTime(this.getHurtTime() - 1);
		}
		if (this.getDamage() > 0.0F) {
			this.setDamage(this.getDamage() - 1.0F);
		}

		super.tick();

		if (this.level().isClientSide()) {
			this.clientTick();
		} else {
			this.serverTick();
		}
	}

	/** Watts the server is currently applying for the rider (zero without a rider or when their input timed out). */
	public double getAppliedPower() {
		if (this.getRider() == null || this.tickCount - this.lastControlTick > CONTROL_TIMEOUT_TICKS) {
			return 0.0;
		}
		return hasPart(BikeComponents.PEDALS) && canRide() ? this.control.propulsion() : 0.0;
	}

	/** Called on the server when the rider client sends a control packet. Values are validated here. */
	public void applyRiderControl(int sequence, BikeControlState state) {
		if (this.level().isClientSide()) {
			return;
		}
		if (this.lastSequence >= 0) {
			int diff = (sequence - this.lastSequence) & 0xFFFF;
			if (diff == 0 || diff >= 0x8000) {
				return;
			}
		}
		this.lastSequence = sequence & 0xFFFF;
		this.control = state.sanitized();
		this.lastControlTick = this.tickCount;
	}

	public @Nullable Player getRider() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	private BikeControlState effectiveControl() {
		if (this.getRider() == null) {
			this.control = BikeControlState.NEUTRAL;
			this.lastSequence = -1;
			return new BikeControlState(0.0, 0.0, 1.0, this.control.gear());
		}
		if (this.tickCount - this.lastControlTick > CONTROL_TIMEOUT_TICKS) {
			return new BikeControlState(0.0, 0.0, 0.0, this.control.gear());
		}
		return new BikeControlState(hasPart(BikeComponents.PEDALS) ? this.control.propulsion() : 0, this.control.steering(), this.control.brake(), this.control.gear());
	}

	private void serverTick() {
		BikeControlState input = this.effectiveControl();
		Player rider = this.getRider();
        if (!canRide()) { ejectPassengers(); input = new BikeControlState(0, 0, 1, input.gear()); }
		BikePhysics.Params enchantedParams = BikeSpeedBoost.apply(this.params(), getSpeedBoostLevel());
		BikePhysics.Params params = rider == null
			? enchantedParams
			: RiderSettings.apply(enchantedParams, RiderSettings.get(rider.getUUID()));

		if (this.isEyeInFluid(FluidTags.WATER)) {
			if (++this.submergedTicks >= SUBMERGED_EJECT_TICKS) {
				this.ejectPassengers();
			}
		} else {
			this.submergedTicks = 0;
		}

		this.steering = BikePhysics.smoothSteering(this.steering, input.steering());

		// Gravity follows the slope under the wheels. It is read from the terrain along the heading, so it also works
		// at a standstill, over stairs and when rolling downhill, instead of being inferred from how far the bike moved.
		boolean probed = false;
		if (this.onGround()) {
			double headingYaw = Math.toRadians(this.getYRot());
			OptionalDouble probe = TerrainSlope.measureWindow(new LevelGround(this.level()), this.getX(), this.getZ(),
				-Math.sin(headingYaw), Math.cos(headingYaw), this.getY(), SLOPE_PROBE_BLOCKS);
			if (probe.isPresent()) {
				this.gradient += (Mth.clamp(probe.getAsDouble(), -0.6, 0.6) - this.gradient) * SLOPE_SMOOTHING;
				probed = true;
			}
		}

		BlockState below = this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.2, this.getZ()));
		BikeSurface surface = BikeSurface.classify(below, this.isInWater());
		this.speed = BikePhysics.stepSpeed(this.speed, DT, input.propulsion(), input.brake(), surface, this.gradient, params);
        int burst = (hasPart(0) && tirePercent(0) == 0 ? 1 : 0) + (hasPart(1) && tirePercent(1) == 0 ? 1 : 0);
        if (burst > 0) this.speed = Math.min(3.0 / burst, this.speed * Math.pow(0.985, burst));
        if (!hasPart(0) || !hasPart(1)) this.speed = 0;

		double yawRate = BikePhysics.yawRate(this.speed, this.steering, params);
		this.setYRot(this.getYRot() + (float) Math.toDegrees(yawRate * DT));
		this.setXRot(0.0F);

		double yawRad = Math.toRadians(this.getYRot());
		double forwardX = -Math.sin(yawRad);
		double forwardZ = Math.cos(yawRad);
		this.hitEntitiesAhead(forwardX, forwardZ, params);
		double blocksPerTick = this.speed * DT;

		double verticalSpeed = this.isInWater()
			? (this.getDeltaMovement().y - 0.01) * 0.8
			: (this.getDeltaMovement().y - this.getGravity()) * 0.98;
		this.setDeltaMovement(forwardX * blocksPerTick, verticalSpeed, forwardZ * blocksPerTick);

		double startX = this.getX();
		double startY = this.getY();
		double startZ = this.getZ();
		boolean wasOnGround = this.onGround();
		double speedBeforeMove = this.speed;
		this.move(MoverType.SELF, this.getDeltaMovement());

		// Collisions: keep the speed component that survived, and make hard hits audible and painful.
		if (this.horizontalCollision) {
			Vec3 after = this.getDeltaMovement();
			double surviving = Math.max(0.0, (after.x * forwardX + after.z * forwardZ) / DT);
			this.handleImpact(speedBeforeMove - surviving);
			this.speed = surviving;
		}

		// Hopping up a tall ledge (mountain bike) is not free.
		if (this.onGround() && this.getY() - startY > LEDGE_PENALTY_HEIGHT) {
			this.speed *= this.getBikeType().bumpSpeedFactor();
		}

		// Stay glued to the ground over down slopes and stairs instead of launching off them.
		if (wasOnGround && !this.onGround() && this.getDeltaMovement().y <= 0.0 && !this.isInWater()) {
			this.move(MoverType.SELF, new Vec3(0.0, -GROUND_SNAP, 0.0));
			if (this.onGround()) {
				this.setDeltaMovement(this.getDeltaMovement().x, 0.0, this.getDeltaMovement().z);
			}
		}
		this.applyEffectsFromBlocks();

		// Gradient comes from how the bike actually moved through the terrain, smoothed so single
		// stair steps do not cause spikes.
		double moved = Math.hypot(this.getX() - startX, this.getZ() - startZ);
        if (rider != null && onGround()) wearTires(moved * BikeServerConfig.INSTANCE.tireDamageRate
                * (1 + surface.looseness * (BikeServerConfig.INSTANCE.roughTerrainTireDamageMultiplier - 1)));
		if (probed) {
			// The terrain probe above already set the gradient.
		} else if (this.onGround() && moved > 0.02) {
			double rawGradient = Mth.clamp((this.getY() - startY) / moved, -0.6, 0.6);
			this.gradient += (rawGradient - this.gradient) * 0.25;
		} else if (this.onGround()) {
			this.gradient *= 0.8;
		}

		this.entityData.set(DATA_SPEED, quantize(this.speed, 10.0));
		this.entityData.set(DATA_STEERING, quantize(this.steering, 16.0));
		this.entityData.set(DATA_BRAKE, quantize(input.brake(), 8.0));
		this.entityData.set(DATA_GRADIENT, quantize(this.gradient, 100.0));
		this.entityData.set(DATA_GEAR, input.gear());
		this.entityData.set(DATA_PEDALING, input.propulsion() > 5.0 && this.getRider() != null);
		this.entityData.set(DATA_SURFACE, surface.ordinal());
	}

	private void handleImpact(double impact) {
        if (impact > CRASH_DAMAGE_IMPACT) wearTires(BikeServerConfig.INSTANCE.collisionTireDamage * Math.min(2, impact / 12));
		if (impact > CRASH_SOUND_IMPACT) {
			this.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, (float) Math.min(1.0, impact / 12.0), 0.8F);
		}
		if (impact > CRASH_DAMAGE_IMPACT && this.getRider() instanceof Player rider && this.level() instanceof ServerLevel serverLevel) {
			float helmet = BikeHelmetItem.isWornBy(rider) ? BikeHelmetItem.CRASH_DAMAGE_FACTOR : 1.0F;
			rider.hurtServer(serverLevel, serverLevel.damageSources().flyIntoWall(), (float) ((impact - CRASH_DAMAGE_IMPACT) * 0.5) * helmet);
		}
		damageVehicle(BikeDurability.crashDamage(impact));
	}

	/**
	 * Riding into a living entity (mob or player) shoves or hurts it depending on how fast the two close in, and
	 * costs the bike speed in proportion to what it hit, see {@link BikeImpact}. Riders of other bikes are left to
	 * the solid bike-on-bike collision instead, see {@link #canBeCollidedWith}.
	 */
	private void hitEntitiesAhead(double forwardX, double forwardZ, BikePhysics.Params params) {
		if (this.speed < BikeImpact.TOUCH_SPEED || !(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		this.recentHits.values().removeIf(tick -> this.tickCount - tick > HIT_COOLDOWN_TICKS * 2);

		// Sweep the whole distance covered this tick so a fast bike cannot skip over a creature.
		double reach = this.speed * DT + 0.3;
		AABB area = this.getBoundingBox().expandTowards(forwardX * reach, 0.0, forwardZ * reach).inflate(0.15, 0.0, 0.15);
		double rightX = -forwardZ;
		double rightZ = forwardX;
		double lostInHits = 0.0;
		for (Entity target : serverLevel.getEntities(this, area, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
			&& !this.isPassengerOfSameVehicle(e) && !(e.getVehicle() instanceof BikeEntity))) {
			double offsetX = target.getX() - this.getX();
			double offsetZ = target.getZ() - this.getZ();
			if (offsetX * forwardX + offsetZ * forwardZ <= 0.0) {
				continue;
			}
			Integer last = this.recentHits.get(target.getUUID());
			if (last != null && this.tickCount - last < HIT_COOLDOWN_TICKS) {
				continue;
			}
			LivingEntity living = (LivingEntity) target;
			Vec3 motion = living.getDeltaMovement();
			double along = (motion.x * forwardX + motion.z * forwardZ) / DT;
			BikeImpact.Outcome outcome = BikeImpact.resolve(this.speed, along, params.totalMass(), BikeImpact.massOf(target.getBbWidth(), target.getBbHeight()));
			if (outcome.kind() == BikeImpact.Kind.NONE) {
				continue;
			}

			// Send it ahead and a little away from the bike's line, so a creature at the edge is swept aside.
			double side = Mth.clamp((offsetX * rightX + offsetZ * rightZ) * 0.8, -0.5, 0.5);
			double dirX = forwardX + rightX * side;
			double dirZ = forwardZ + rightZ * side;
			double length = Math.hypot(dirX, dirZ);
			dirX /= length;
			dirZ /= length;

			double closing = this.speed - along;
			boolean hurt = false;
			if (outcome.kind() == BikeImpact.Kind.HIT) {
				this.recentHits.put(target.getUUID(), this.tickCount);
				Player rider = this.getRider();
				DamageSource source = rider != null ? serverLevel.damageSources().playerAttack(rider) : serverLevel.damageSources().generic();
				hurt = living.hurtServer(serverLevel, source, outcome.damage());
				if (hurt) {
					living.knockback(outcome.knockback(), -dirX, -dirZ, source, outcome.damage());
					serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
						6 + (int) outcome.damage(), 0.25, 0.2, 0.25, 0.2);
					this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.9F);
					this.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, (float) Math.min(1.0, closing / 10.0), 1.0F);
				}
			}
			if (!hurt) {
				// A shove, or a hit that could not hurt (peaceful rules, no PvP, invulnerable): still push it out of the way.
				double strength = outcome.kind() == BikeImpact.Kind.SHOVE ? outcome.knockback() : 0.08 + 0.03 * closing;
				living.push(dirX * strength, 0.02, dirZ * strength);
				if (outcome.kind() == BikeImpact.Kind.HIT) {
					this.playSound(SoundEvents.PLAYER_ATTACK_WEAK, 0.8F, 1.0F);
				}
			}
			if (outcome.kind() == BikeImpact.Kind.HIT) {
				lostInHits += outcome.speedLoss();
			}
			this.speed = Math.max(0.0, this.speed - outcome.speedLoss());
		}
		if (lostInHits > 0.0) {
			// Running into something heavy stops the bike short, and that hurts the rider like hitting a wall.
			this.handleImpact(lostInHits);
		}
	}

	private static float quantize(double value, double steps) {
		return (float) (Math.round(value * steps) / steps);
	}

	private void clientTick() {
		this.setDeltaMovement(Vec3.ZERO);

		this.wheelAngleO = this.wheelAngle;
		float metersPerTick = this.getSpeed() * (float) DT;
		this.wheelAngle += metersPerTick / WHEEL_RADIUS;
		this.updateSuspension();
	}

	/**
	 * A damped spring driven by the vertical acceleration of the bike. Climbing a step or landing makes the body sag
	 * and then rebound; a stiff road bike barely moves and settles at once, a mountain bike sags further and rings
	 * for longer. Climbing also settles the body a little lower. Rendering only, the server never sees it.
	 */
	private void updateSuspension() {
		this.suspensionOffsetO = this.suspensionOffset;
		double y = this.getY();
		double verticalSpeed = this.hasLastY ? Mth.clamp(y - this.lastY, -1.0, 1.0) : 0.0;
		double lift = verticalSpeed - this.lastVerticalSpeed;
		this.lastY = y;
		this.lastVerticalSpeed = verticalSpeed;
		this.hasLastY = true;

		float softness = this.getBikeType().suspension;
		double omega = Mth.lerp(softness, 0.75F, 0.42F);
		double damping = Mth.lerp(softness, 0.8F, 0.2F);
		double travel = 0.04 + 0.16 * softness;
		double rest = -Math.max(0.0, this.getGradient()) * softness * 0.12;
		this.suspensionVelocity += -omega * omega * (this.suspensionOffset - rest) - 2.0 * damping * omega * this.suspensionVelocity
			- lift * (0.25 + 0.75 * softness);
		this.suspensionOffset += this.suspensionVelocity;
		if (Math.abs(this.suspensionOffset) > travel) {
			this.suspensionOffset = Math.signum(this.suspensionOffset) * travel;
			this.suspensionVelocity *= -0.3;
		}
	}

	/** Vertical offset of the body from the suspension, in blocks (client only; negative is compressed). */
	public float getSuspensionOffset(float partialTick) {
		return (float) Mth.lerp(partialTick, this.suspensionOffsetO, this.suspensionOffset);
	}

	// ---------------------------------------------------------------- synced state for rendering / HUD

	/** Forward speed in m/s. */
	public float getSpeed() {
		return this.entityData.get(DATA_SPEED);
	}

	/** Front wheel steering, -1 (left) .. +1 (right). */
	public float getSteering() {
		return this.entityData.get(DATA_STEERING);
	}

	public float getBrake() {
		return this.entityData.get(DATA_BRAKE);
	}

	/** Gradient along the direction of travel as rise/run (0.05 = 5 %). */
	public float getGradient() {
		return this.entityData.get(DATA_GRADIENT);
	}

	public int getGear() {
		return this.entityData.get(DATA_GEAR);
	}

	/** Terrain under the bike as last measured by the server; used for trainer resistance feedback. */
	public BikeSurface getSurface() {
		BikeSurface[] all = BikeSurface.values();
		int index = this.entityData.get(DATA_SURFACE);
		return index >= 0 && index < all.length ? all[index] : BikeSurface.PATH;
	}

	public boolean isPedaling() {
		return this.entityData.get(DATA_PEDALING);
	}

	public float getWheelAngle(float partialTick) {
		return Mth.lerp(partialTick, this.wheelAngleO, this.wheelAngle);
	}

	// ---------------------------------------------------------------- GeckoLib

	/** Crank revolutions per second implied by the wheel speed and the selected gear. */
	public float getCadenceRevsPerSecond() {
		float wheelRevs = this.getSpeed() / (2.0F * (float) Math.PI * WHEEL_RADIUS);
		return wheelRevs / (1.0F + this.getGear() * 0.25F);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// The pedal loop is one crank revolution per second, so the controller speed is the cadence in rev/s.
		// When not pedaling the animation is paused, leaving the crank where it stopped (the bike coasts).
		controllers.add(new AnimationController<BikeEntity>("drive", 2, test -> {
			BikeEntity bike = test.animatable();
			if (!bike.isPedaling()) {
				return PlayState.PAUSE;
			}
			test.setControllerSpeed(Math.max(0.2F, bike.getCadenceRevsPerSecond()));
			return test.setAndContinue(PEDAL);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.geoCache;
	}

	// ---------------------------------------------------------------- entity plumbing

	@Override
	protected double getDefaultGravity() {
		return 0.08;
	}

	@Override
	public float maxUpStep() {
		return this.getBikeType().stepHeight;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 2);
	}

	@Override
	protected Entity.MovementEmission getMovementEmission() {
		return Entity.MovementEmission.EVENTS;
	}

	/** Other vehicles (including bikes) collide with a bike; players and mobs walk through and are hit instead. */
	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return other instanceof VehicleEntity;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	/**
	 * A landing is passed on to the rider, softened by the bike: it always takes some of the blow, and the more
	 * suspension the type has the more it takes. The landing also damages the bicycle itself.
	 */
	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        if (!level().isClientSide() && fallDistance > 3)
            wearTires(BikeServerConfig.INSTANCE.collisionTireDamage * Math.min(2, (fallDistance - 3) / 4));
		BikeType type = this.getBikeType();
		double effective = Math.max(0.0, fallDistance - type.fallAbsorbBlocks());
		this.propagateFallToPassengers(effective, damageModifier * type.fallDamageMultiplier(), damageSource);
		damageVehicle(BikeDurability.fallDamage(fallDistance));
		return false;
	}

    /** Left-clicking an unoccupied bicycle with the wrench repairs its whole-vehicle durability. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player && player.getMainHandItem().is(ModItems.BIKE_WRENCH)) {
            repairWithWrench(player);
            return true;
        }
        return false;
    }

	@Override
	protected Item getDropItem() {
		return ModItems.forType(this.getBikeType());
	}

	@Override
	public ItemStack getPickResult() {
		return toItem();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("bike_type", this.getBikeType().id);
        output.putInt("frame_color", getFrameColor());
        output.putFloat("vehicle_damage", getVehicleDamage());
        output.putInt("speed_boost", getSpeedBoostLevel());
        output.store("components", ItemContainerContents.CODEC, ItemContainerContents.fromItems(components));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setBikeType(BikeType.byId(input.getStringOr("bike_type", BikeType.GRAVEL.id)));
        entityData.set(DATA_COLOR, input.getIntOr("frame_color", BikeComponents.DEFAULT_COLOR) & 0xFFFFFF);
        input.read("components", ItemContainerContents.CODEC).ifPresent(contents -> {
            ItemStack stack = new ItemStack(ModItems.forType(getBikeType()));
            BikeComponents.color(stack, getFrameColor());
            stack.set(DataComponents.CONTAINER, contents);
            loadFromItem(stack);
        });
        entityData.set(DATA_VEHICLE_DAMAGE, BikeDurability.clampDamage(
                input.getFloatOr("vehicle_damage", getVehicleDamage()), getBikeType()));
        entityData.set(DATA_SPEED_BOOST, BikeSpeedBoost.clampLevel(input.getIntOr("speed_boost", 0)));
	}

	// ---------------------------------------------------------------- riding

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.getItemInHand(hand).is(ModItems.BIKE_WRENCH)) {
            if (!level().isClientSide()) destroyWithWrench(player, hand);
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty()) {
            if (!level().isClientSide() && getPassengers().isEmpty() && !isRemoved()) {
                ItemStack stack = toItem();
                discard();
                if (!player.getInventory().add(stack)) player.spawnAtLocation((ServerLevel) level(), stack);
            }
            return InteractionResult.SUCCESS;
        }
        if (!canRide()) {
            if (!level().isClientSide()) player.sendOverlayMessage(Component.translatable("message.voxelvelo.missing_parts"));
            return InteractionResult.FAIL;
        }
		InteractionResult result = super.interact(player, hand, location);
		if (result != InteractionResult.PASS) {
			return result;
		}
		if (player.isSecondaryUseActive() || !this.canAddPassenger(player)) {
			return InteractionResult.PASS;
		}
		if (!this.level().isClientSide() && !hasPart(BikeComponents.PEDALS))
            player.sendOverlayMessage(Component.translatable("message.voxelvelo.no_pedals"));
        if (!this.level().isClientSide() && !player.startRiding(this)) {
			return InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return canRide() && this.getPassengers().isEmpty() && !this.isEyeInFluid(FluidTags.WATER);
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return null;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0.0, this.getBikeType().rig.seatHeight() + (this.level().isClientSide() ? this.suspensionOffset : 0.0), this.getBikeType().rig.seatOffset()).yRot(-this.getYRot() * (float) (Math.PI / 180.0));
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		this.clampRotation(passenger);
	}

	@Override
	public void onPassengerTurned(Entity passenger) {
		float delta = this.clampRotation(passenger);
		passenger.yRotO += delta;
	}

	private float clampRotation(Entity passenger) {
		passenger.setYBodyRot(this.getYRot());
		float delta = Mth.wrapDegrees(passenger.getYRot() - this.getYRot());
		float clamped = Mth.clamp(delta, -105.0F, 105.0F);
		passenger.setYRot(passenger.getYRot() + clamped - delta);
		return clamped - delta;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		Vec3 direction = getCollisionHorizontalEscapeVector(this.getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(), passenger.getYRot());
		double targetX = this.getX() + direction.x;
		double targetZ = this.getZ() + direction.z;
		BlockPos targetPos = BlockPos.containing(targetX, this.getBoundingBox().maxY, targetZ);
		BlockPos belowPos = targetPos.below();
		if (!this.level().isWaterAt(belowPos)) {
			List<Vec3> targets = new ArrayList<>();
			double targetFloor = this.level().getBlockFloorHeight(targetPos);
			if (DismountHelper.isBlockFloorValid(targetFloor)) {
				targets.add(new Vec3(targetX, targetPos.getY() + targetFloor, targetZ));
			}
			double belowFloor = this.level().getBlockFloorHeight(belowPos);
			if (DismountHelper.isBlockFloorValid(belowFloor)) {
				targets.add(new Vec3(targetX, belowPos.getY() + belowFloor, targetZ));
			}
			for (Pose pose : passenger.getDismountPoses()) {
				for (Vec3 target : targets) {
					if (DismountHelper.canDismountTo(this.level(), target, passenger, pose)) {
						passenger.setPose(pose);
						return target;
					}
				}
			}
		}
		return super.getDismountLocationForPassenger(passenger);
	}
}
