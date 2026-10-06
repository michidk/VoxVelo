package dev.michidk.voxelvelo.bike;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import dev.michidk.voxelvelo.registry.ModEnchantments;
import dev.michidk.voxelvelo.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A rideable bicycle. It moves like a horse or a boat: the rider is the controlling passenger, the rider's own client
 * simulates the ride with {@link BikeMotion} and vanilla vehicle movement carries the position to the server and to
 * everyone else. The rider client also reports a {@link BikeRiderState} every tick, which the server syncs to the
 * other clients for animation and HUDs. A bike nobody rides is simulated by the server (it rolls to a stop).
 *
 * <p>The server stays in charge of consequences: crash and fall damage ({@link BikeWear}), hurting what the bike runs
 * into ({@link BikeEntityHits}), tire wear and the installed parts ({@link BikeParts}).
 *
 * <p>One entity class serves all {@link BikeType}s; the type only selects physics parameters, step height, seat
 * height, drop item and the model/texture on the client.
 */
public class BikeEntity extends VehicleEntity implements GeoEntity {
	public static final String ANIM_PEDAL = "animation.voxelvelo.bicycle.pedal";
	/** Name of the GeckoLib controller that turns the cranks. */
	public static final String DRIVE_CONTROLLER = "drive";
	private static final RawAnimation PEDAL = RawAnimation.begin().thenLoop(ANIM_PEDAL);

	/** Radius of the wheels as drawn (6.3 model units at the model scale), so the rolling speed matches the ground. */
	public static final float WHEEL_RADIUS = 6.3F * BikeRig.MODEL_SCALE / 16.0F;
	/** Server ticks without a rider report after which the rider's power counts as zero. */
	private static final int STATE_TIMEOUT_TICKS = 20;
	private static final int SUBMERGED_EJECT_TICKS = 40;
	/** Rider power above which the cranks visibly turn. */
	private static final double PEDALING_WATTS = 5.0;
	/** Moving further than this in one tick is a teleport, not riding, and wears no tire. */
	private static final double MAX_RIDE_STEP = 4.0;

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

	/** The rider's client, installed by the client entrypoint; null on a dedicated server. */
	private static @Nullable BikeDriver driver;

	private final BikeParts parts = new BikeParts(this);
	private final BikeWear wear = new BikeWear(this, this.parts);
	private final BikeEntityHits hits = new BikeEntityHits(this);
	private final BikeMotion motion = new BikeMotion(this);
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

	// State of the side that simulates the bike.
	private boolean simulating;
	private BikeControlState localInput = BikeControlState.NEUTRAL;
	private BikeSurface localSurface = BikeSurface.PATH;

	// Server-only state.
	private double reportedPower;
	private int lastReportTick = Integer.MIN_VALUE / 2;
	private double pendingImpact;
	private int submergedTicks;
	private @Nullable Vec3 lastServerPos;

	// Client-only animation state, derived from the bike's speed and height so every client animates identically.
	private final BikeSuspension suspension = new BikeSuspension();
	private float wheelAngle;
	private float wheelAngleO;

	public BikeEntity(EntityType<? extends BikeEntity> type, Level level) {
		super(type, level);
		this.parts.installStock(this.getBikeType());
	}

	public static void setDriver(BikeDriver clientDriver) {
		driver = clientDriver;
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
		entityData.define(DATA_PARTS, BikeComponents.ALL_PARTS_MASK);
		entityData.define(DATA_TIRES, BikeComponents.packTires(100, 100));
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

	// ---------------------------------------------------------------- type, parts and condition

	public BikeType getBikeType() {
		return BikeType.byOrdinal(this.entityData.get(DATA_TYPE));
	}

	public void setBikeType(BikeType type) {
		this.entityData.set(DATA_TYPE, type.ordinal());
		this.entityData.set(DATA_VEHICLE_DAMAGE, 0.0F);
		this.parts.installStock(type);
	}

	public int getFrameColor() {
		return this.entityData.get(DATA_COLOR);
	}

	public int getComponentMask() {
		return this.entityData.get(DATA_PARTS);
	}

	public boolean hasPart(int slot) {
		return (this.getComponentMask() & BikeComponents.bit(slot)) != 0;
	}

	public int tirePercent(int slot) {
		return BikeComponents.tirePercent(this.entityData.get(DATA_TIRES), slot);
	}

	public int getSetup() {
		return this.entityData.get(DATA_SETUP);
	}

	public float getVehicleDamage() {
		return this.entityData.get(DATA_VEHICLE_DAMAGE);
	}

	void setVehicleDamage(float damage) {
		this.entityData.set(DATA_VEHICLE_DAMAGE, damage);
	}

	public int getSpeedBoostLevel() {
		return this.entityData.get(DATA_SPEED_BOOST);
	}

	public int getConditionPercent() {
		return BikeDurability.conditionPercent(this.getVehicleDamage(), this.getBikeType());
	}

	/** Physics parameters of the frame with the installed tires and handlebars. */
	public BikePhysics.Params params() {
		return BikeComponents.params(this.getBikeType(), this.getSetup());
	}

	public BikeRig rig() {
		return BikeComponents.rig(this.getBikeType(), this.getSetup());
	}

	public boolean canRide() {
		return (this.getComponentMask() & BikeComponents.RIDEABLE_MASK) == BikeComponents.RIDEABLE_MASK;
	}

	/** Called by {@link BikeParts} on the server when the installed parts change. */
	void publishParts(int mask, int tires, int setup) {
		this.entityData.set(DATA_PARTS, mask);
		this.entityData.set(DATA_TIRES, tires);
		this.entityData.set(DATA_SETUP, setup);
		if ((mask & BikeComponents.RIDEABLE_MASK) != BikeComponents.RIDEABLE_MASK) {
			this.ejectPassengers();
		}
	}

	public void loadFromItem(ItemStack stack) {
		this.entityData.set(DATA_COLOR, BikeComponents.color(stack));
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents != null) {
			this.parts.load(contents, this.getBikeType());
		}
		this.entityData.set(DATA_VEHICLE_DAMAGE, BikeDurability.clampDamage(BikeComponents.vehicleDamage(stack), this.getBikeType()));
		var speedBoost = this.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModEnchantments.SPEED_BOOST);
		this.entityData.set(DATA_SPEED_BOOST, BikeSpeedBoost.clampLevel(stack.getEnchantments().getLevel(speedBoost)));
	}

	public ItemStack toItem() {
		ItemStack stack = new ItemStack(ModItems.forType(this.getBikeType()));
		BikeComponents.color(stack, this.getFrameColor());
		stack.set(DataComponents.CONTAINER, this.parts.contents());
		BikeComponents.vehicleDamage(stack, this.getVehicleDamage());
		if (this.getSpeedBoostLevel() > 0) {
			var speedBoost = this.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModEnchantments.SPEED_BOOST);
			stack.enchant(speedBoost, this.getSpeedBoostLevel());
		}
		return stack;
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

		if (this.isLocalInstanceAuthoritative()) {
			this.simulate();
		} else {
			this.simulating = false;
			if (this.level().isClientSide()) {
				this.setDeltaMovement(Vec3.ZERO);
			}
		}

		if (this.level().isClientSide()) {
			this.clientTick();
		} else {
			this.serverTick();
		}
	}

	/** One tick of the ride on the side in charge: the rider's client, or the server for a parked bike. */
	private void simulate() {
		boolean client = this.level().isClientSide();
		if (!this.simulating) {
			// Taking the bike over from the other side: continue where it left off.
			this.simulating = true;
			this.motion.seed(this.syncedSpeed(), this.entityData.get(DATA_STEERING), this.entityData.get(DATA_GRADIENT));
			this.localInput = new BikeControlState(0.0, 0.0, 0.0, this.entityData.get(DATA_GEAR));
			this.localSurface = BikeSurface.byOrdinal(this.entityData.get(DATA_SURFACE));
		}

		BikeControlState driven = client && driver != null ? driver.input(this) : null;
		BikeControlState input;
		if (driven != null) {
			input = driven;
		} else if (client) {
			input = new BikeControlState(0.0, 0.0, 0.0, this.localInput.gear()); // Not taken over yet: coast.
		} else {
			input = new BikeControlState(0.0, 0.0, 1.0, this.localInput.gear()); // Parked: the brakes hold it.
		}
		input = this.drivable(input);
		RiderSettings.Settings rider = client && driver != null ? driver.riderSettings() : RiderSettings.Settings.DEFAULT;

		BikeMotion.Step step = this.motion.step(input, RiderSettings.apply(this.params(), rider), rider, this.hits);
		this.localInput = input;
		this.localSurface = step.surface();

		if (client) {
			// Hurting what the bike ran into is the server's business; the rider client only reports wall crashes.
			if (driver != null) {
				driver.report(this, new BikeRiderState(this.motion.speed(), this.motion.steering(), input.brake(), input.gear(),
					this.isPedaling(), input.propulsion(), step.surface().ordinal(), this.motion.gradient(), step.wallImpact()));
			}
		} else {
			this.wear.crash(step.wallImpact());
			this.wear.crash(step.hitCrash());
			this.publishMotion(this.motion.speed(), this.motion.steering(), input.brake(), input.gear(), false,
				this.motion.gradient(), step.surface());
		}
	}

	/** Without the riding parts the bike only brakes; without pedals the rider cannot drive it. */
	private BikeControlState drivable(BikeControlState input) {
		if (!this.canRide()) {
			return new BikeControlState(0.0, 0.0, 1.0, input.gear());
		}
		return this.hasPart(BikeComponents.PEDALS) ? input : new BikeControlState(0.0, input.steering(), input.brake(), input.gear());
	}

	private void serverTick() {
		Player rider = this.getRider();
		if (rider != null && !this.canRide()) {
			this.ejectPassengers();
		}
		if (this.isEyeInFluid(FluidTags.WATER)) {
			if (++this.submergedTicks >= SUBMERGED_EJECT_TICKS) {
				this.ejectPassengers();
			}
		} else {
			this.submergedTicks = 0;
		}

		if (!this.simulating) {
			// The rider's client moved the bike; the server deals with what it ran into and how hard it crashed.
			double yawRad = Math.toRadians(this.getYRot());
			BikePhysics.Params params = rider != null ? RiderSettings.apply(this.params(), RiderSettings.get(rider.getUUID())) : this.params();
			this.wear.crash(this.hits.strike(this.getSpeed(), -Math.sin(yawRad), Math.cos(yawRad), params).crash());
			this.wear.crash(this.pendingImpact);
		}
		this.pendingImpact = 0.0;

		// Tires wear by the distance actually covered, whichever side moved the bike.
		Vec3 position = this.position();
		if (this.lastServerPos != null && rider != null && this.onGround()) {
			double moved = Math.hypot(position.x - this.lastServerPos.x, position.z - this.lastServerPos.z);
			if (moved < MAX_RIDE_STEP) {
				this.wear.ride(moved, this.getSurface());
			}
		}
		this.lastServerPos = position;
	}

	/** Called on the server with the rider client's report of the bike it simulates. Values are validated here. */
	public void applyRiderState(BikeRiderState report) {
		if (this.level().isClientSide() || this.simulating) {
			return;
		}
		double maxSpeed = this.params().maxSpeed() * BikeSpeedBoost.multiplier(this.getSpeedBoostLevel());
		BikeRiderState state = report.sanitized(maxSpeed);
		boolean drives = this.canRide() && this.hasPart(BikeComponents.PEDALS);
		this.publishMotion(state.speed(), state.steering(), state.brake(), state.gear(), drives && state.pedaling(),
			state.gradient(), BikeSurface.byOrdinal(state.surface()));
		this.reportedPower = drives ? state.power() : 0.0;
		this.lastReportTick = this.tickCount;
		this.pendingImpact = Math.max(this.pendingImpact, state.impact());
	}

	private void publishMotion(double speed, double steering, double brake, int gear, boolean pedaling, double gradient, BikeSurface surface) {
		this.entityData.set(DATA_SPEED, quantize(speed, 10.0));
		this.entityData.set(DATA_STEERING, quantize(steering, 16.0));
		this.entityData.set(DATA_BRAKE, quantize(brake, 8.0));
		this.entityData.set(DATA_GRADIENT, quantize(gradient, 100.0));
		this.entityData.set(DATA_GEAR, gear);
		this.entityData.set(DATA_PEDALING, pedaling);
		this.entityData.set(DATA_SURFACE, surface.ordinal());
	}

	private static float quantize(double value, double steps) {
		return (float) (Math.round(value * steps) / steps);
	}

	/** Watts the rider currently puts into the bike (zero without a rider or when their reports stopped). */
	public double getAppliedPower() {
		if (this.getRider() == null) {
			return 0.0;
		}
		if (this.simulatesForRider()) {
			return this.localInput.propulsion();
		}
		return this.tickCount - this.lastReportTick > STATE_TIMEOUT_TICKS ? 0.0 : this.reportedPower;
	}

	public @Nullable Player getRider() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	private void clientTick() {
		this.wheelAngleO = this.wheelAngle;
		this.wheelAngle += this.getSpeed() * (float) BikeMotion.DT / WHEEL_RADIUS;
		this.suspension.tick(this.getY(), this.getGradient(), this.getBikeType().suspension);
	}

	/** Vertical offset of the body from the suspension, in blocks (client only; negative is compressed). */
	public float getSuspensionOffset(float partialTick) {
		return this.suspension.offset(partialTick);
	}

	// ---------------------------------------------------------------- ride state for rendering, HUD and stats

	/** Whether this is the rider's own client simulating the ride, so the freshest values are the local ones. */
	private boolean simulatesForRider() {
		return this.simulating && this.level().isClientSide();
	}

	private float syncedSpeed() {
		return this.entityData.get(DATA_SPEED);
	}

	/** Forward speed in m/s. */
	public float getSpeed() {
		return this.simulatesForRider() ? (float) this.motion.speed() : this.syncedSpeed();
	}

	/** Front wheel steering, -1 (left) .. +1 (right). */
	public float getSteering() {
		return this.simulatesForRider() ? (float) this.motion.steering() : this.entityData.get(DATA_STEERING);
	}

	public float getBrake() {
		return this.simulatesForRider() ? (float) this.localInput.brake() : this.entityData.get(DATA_BRAKE);
	}

	/** Gradient along the direction of travel as rise/run (0.05 = 5 %). */
	public float getGradient() {
		return this.simulatesForRider() ? (float) this.motion.gradient() : this.entityData.get(DATA_GRADIENT);
	}

	public int getGear() {
		return this.simulatesForRider() ? this.localInput.gear() : this.entityData.get(DATA_GEAR);
	}

	/** Terrain under the bike; used for trainer resistance feedback and tire wear. */
	public BikeSurface getSurface() {
		return this.simulatesForRider() ? this.localSurface : BikeSurface.byOrdinal(this.entityData.get(DATA_SURFACE));
	}

	public boolean isPedaling() {
		return this.simulatesForRider()
			? this.localInput.propulsion() > PEDALING_WATTS && this.getRider() != null
			: this.entityData.get(DATA_PEDALING);
	}

	public float getWheelAngle(float partialTick) {
		return Mth.lerp(partialTick, this.wheelAngleO, this.wheelAngle);
	}

	/** Crank revolutions per second implied by the wheel speed and the selected gear. */
	public float getCadenceRevsPerSecond() {
		float wheelRevs = this.getSpeed() / (2.0F * (float) Math.PI * WHEEL_RADIUS);
		return wheelRevs / (1.0F + this.getGear() * 0.25F);
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// The pedal loop is one crank revolution per second, so the controller speed is the cadence in rev/s.
		// When not pedaling the animation is paused, leaving the crank where it stopped (the bike coasts).
		controllers.add(new AnimationController<BikeEntity>(DRIVE_CONTROLLER, 2, test -> {
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

	/** Lets {@link BikeMotion} apply what the blocks the bike moved through do to it. */
	void applyBlockEffects() {
		this.applyEffectsFromBlocks();
	}

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
	 * suspension the type has the more it takes. The landing also damages the bicycle itself. The server sees the
	 * fall even while the rider's client moves the bike, since vanilla replays the vehicle's movement there.
	 */
	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		if (this.level().isClientSide()) {
			return false;
		}
		BikeType type = this.getBikeType();
		this.wear.land(fallDistance);
		double effective = Math.max(0.0, fallDistance - type.fallAbsorbBlocks());
		this.propagateFallToPassengers(effective, damageModifier * type.fallDamageMultiplier(), damageSource);
		return false;
	}

	/** Left-clicking an unoccupied bicycle with the wrench repairs its whole-vehicle durability. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof Player player && player.getMainHandItem().is(ModItems.BIKE_WRENCH)) {
			this.wear.repairWithWrench(player);
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
		return this.toItem();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("bike_type", this.getBikeType().id);
		output.putInt("frame_color", this.getFrameColor());
		output.putFloat("vehicle_damage", this.getVehicleDamage());
		output.putInt("speed_boost", this.getSpeedBoostLevel());
		output.store("components", ItemContainerContents.CODEC, this.parts.contents());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setBikeType(BikeType.byId(input.getStringOr("bike_type", BikeType.GRAVEL.id)));
		this.entityData.set(DATA_COLOR, input.getIntOr("frame_color", BikeComponents.DEFAULT_COLOR) & 0xFFFFFF);
		input.read("components", ItemContainerContents.CODEC).ifPresent(contents -> this.parts.load(contents, this.getBikeType()));
		this.entityData.set(DATA_VEHICLE_DAMAGE, BikeDurability.clampDamage(input.getFloatOr("vehicle_damage", 0.0F), this.getBikeType()));
		this.entityData.set(DATA_SPEED_BOOST, BikeSpeedBoost.clampLevel(input.getIntOr("speed_boost", 0)));
	}

	// ---------------------------------------------------------------- riding

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.getItemInHand(hand).is(ModItems.BIKE_WRENCH)) {
			if (!this.level().isClientSide()) {
				this.wear.dismantleWithWrench(player, hand);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty()) {
			if (this.level() instanceof ServerLevel level && this.getPassengers().isEmpty() && !this.isRemoved()) {
				ItemStack stack = this.toItem();
				this.discard();
				if (!player.getInventory().add(stack)) {
					player.spawnAtLocation(level, stack);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (!this.canRide()) {
			if (!this.level().isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.voxelvelo.missing_parts"));
			}
			return InteractionResult.FAIL;
		}
		InteractionResult result = super.interact(player, hand, location);
		if (result != InteractionResult.PASS) {
			return result;
		}
		if (player.isSecondaryUseActive() || !this.canAddPassenger(player)) {
			return InteractionResult.PASS;
		}
		if (!this.level().isClientSide()) {
			if (!this.hasPart(BikeComponents.PEDALS)) {
				player.sendOverlayMessage(Component.translatable("message.voxelvelo.no_pedals"));
			}
			if (!player.startRiding(this)) {
				return InteractionResult.PASS;
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.canRide() && this.getPassengers().isEmpty() && !this.isEyeInFluid(FluidTags.WATER);
	}

	/** The rider controls the bike like a horse: their own client simulates it. */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.getRider();
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		double seatHeight = this.getBikeType().rig.seatHeight() + (this.level().isClientSide() ? this.suspension.offset() : 0.0);
		return new Vec3(0.0, seatHeight, this.getBikeType().rig.seatOffset()).yRot(-this.getYRot() * (float) (Math.PI / 180.0));
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
