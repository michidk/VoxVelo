package dev.michidk.voxvelo.bikes.bike;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import dev.michidk.voxvelo.bikes.registry.ModItems;
import dev.michidk.voxvelo.bikes.registry.ModSounds;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.player.Player;
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
 * <p>The server stays in charge of consequences ({@link BikeServerTick}): crash and fall damage ({@link BikeWear}),
 * hurting what the bike runs into ({@link BikeEntityHits}), tire wear and the installed parts ({@link BikeParts}).
 *
 * <p>One entity class serves all {@link BikeType}s; the type only selects physics parameters, step height, seat
 * height, drop item and the model/texture on the client.
 */
public class BikeEntity extends VehicleEntity implements GeoEntity {
	public static final String ANIM_PEDAL = "animation.voxvelo.bicycle.pedal";
	/** Name of the GeckoLib controller that turns the cranks. */
	public static final String DRIVE_CONTROLLER = "drive";
	private static final RawAnimation PEDAL = RawAnimation.begin().thenLoop(ANIM_PEDAL);

	/** Radius of the wheels as drawn (6.3 model units at the model scale), so the rolling speed matches the ground. */
	public static final float WHEEL_RADIUS = 6.3F * BikeRig.MODEL_SCALE / 16.0F;
	/** Rider power above which the cranks visibly turn. */
	private static final double PEDALING_WATTS = 5.0;
	/** Each gear above the lowest turns the wheel this much further per crank revolution. */
	private static final float GEAR_RATIO_STEP = 0.25F;

	private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	static final EntityDataAccessor<Float> DATA_STEERING = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	static final EntityDataAccessor<Float> DATA_BRAKE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	static final EntityDataAccessor<Float> DATA_GRADIENT = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	static final EntityDataAccessor<Integer> DATA_GEAR = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Boolean> DATA_PEDALING = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.BOOLEAN);
	static final EntityDataAccessor<Integer> DATA_SURFACE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PARTS = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TIRES = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	/** Installed treads and handlebar kind, see {@link BikeComponents#setup}; clients need it for handling and looks. */
	private static final EntityDataAccessor<Integer> DATA_SETUP = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_VEHICLE_DAMAGE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	/** How tall a ledge the bike rolls up, set by the server config and synced so the rider's client steps the same way. */
	static final EntityDataAccessor<Float> DATA_STEP_HEIGHT = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_SPEED_BOOST = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);

	/** The rider's client, installed by the client entrypoint; null on a dedicated server. */
	private static @Nullable BikeDriver driver;

	private final BikeParts parts = new BikeParts(this);
	private final BikeWear wear = new BikeWear(this, this.parts);
	private final BikeEntityHits hits = new BikeEntityHits(this);
	private final BikeMotion motion = new BikeMotion(this);
	private final BikeServerTick server = new BikeServerTick(this, this.wear, this.hits);
	private final BikeSeat seat = new BikeSeat(this);
	private final BikeHitboxes hitboxes = new BikeHitboxes(this);
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

	// State of the side that simulates the bike.
	private boolean simulating;
	private BikeControlState localInput = BikeControlState.NEUTRAL;
	private BikeSurface localSurface = BikeSurface.PATH;

	// Client-only animation state, derived from the bike's speed and height so every client animates identically.
	private final BikeSuspension suspension = new BikeSuspension();
	private float wheelAngle;
	private float wheelAngleO;
	/** Gear heard last, to click on a shift; -1 until the first tick. */
	private int heardGear = -1;

	public BikeEntity(EntityType<? extends BikeEntity> type, Level level) {
		super(type, level);
		this.parts.installStock(this.getBikeType());
	}

	public static void setDriver(BikeDriver clientDriver) {
		driver = clientDriver;
	}

	/** The bike {@code player} rides as its rider (not as a passenger behind someone else), or null. */
	public static @Nullable BikeEntity riddenBy(@Nullable Player player) {
		return player != null && player.getVehicle() instanceof BikeEntity bike && bike.getRider() == player ? bike : null;
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
		entityData.define(DATA_STEP_HEIGHT, 1.0F);
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

	/** Takes over the color, parts, condition and enchantment of the bike item it was placed from. */
	public void loadFromItem(ItemStack stack) {
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents != null) {
			this.parts.load(contents, this.getBikeType());
		}
		this.restore(BikeComponents.color(stack), BikeComponents.vehicleDamage(stack),
			BikeComponents.speedBoostLevel(stack, this.level().registryAccess()));
	}

	public ItemStack toItem() {
		return BikeComponents.bikeItem(this.getBikeType(), this.getFrameColor(), this.parts.contents(), this.getVehicleDamage(),
			this.getSpeedBoostLevel(), this.level().registryAccess());
	}

	/** Sets what an item or a save carries besides type and parts, each forced into its range. */
	private void restore(int color, float vehicleDamage, int speedBoost) {
		this.entityData.set(DATA_COLOR, color & 0xFFFFFF);
		this.entityData.set(DATA_VEHICLE_DAMAGE, BikeDurability.clampDamage(vehicleDamage, this.getBikeType()));
		this.entityData.set(DATA_SPEED_BOOST, BikeSpeedBoost.clampLevel(speedBoost));
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
			this.server.tick(!this.simulating);
		}
		this.hitboxes.tick();
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
		// Nobody drives an unridden bike, nor one the rider's client has not taken over yet: it coasts.
		BikeControlState input = this.drivable(driven != null ? driven : new BikeControlState(0.0, 0.0, 0.0, this.localInput.gear()));
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
			this.server.publishMotion(this.motion.speed(), this.motion.steering(), input.brake(), input.gear(), false,
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

	/** Called on the server with the rider client's report of the bike it simulates. Values are validated there. */
	public void applyRiderState(BikeRiderState report) {
		if (!this.level().isClientSide() && !this.simulating) {
			this.server.applyRiderState(report);
		}
	}

	/** Watts the rider currently puts into the bike (zero without a rider or when their reports stopped). */
	public double getAppliedPower() {
		if (this.getRider() == null) {
			return 0.0;
		}
		return this.simulatesForRider() ? this.localInput.propulsion() : this.server.reportedPower();
	}

	public @Nullable Player getRider() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	private void clientTick() {
		this.wheelAngleO = this.wheelAngle;
		this.wheelAngle += this.getSpeed() * (float) BikeMotion.DT / WHEEL_RADIUS;
		this.suspension.tick(this.getY(), this.getGradient(), this.getBikeType().suspension);
		// Shifts click locally: at once for the rider, from the synced gear for everyone else.
		int gear = this.getGear();
		if (this.heardGear >= 0 && gear != this.heardGear && this.getRider() != null) {
			this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), ModSounds.BIKE_GEAR_SHIFT, this.getSoundSource(), 0.4F,
				gear > this.heardGear ? 1.8F : 1.5F, false);
		}
		this.heardGear = gear;
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
		return wheelRevs / (1.0F + this.getGear() * GEAR_RATIO_STEP);
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
		return this.entityData.get(DATA_STEP_HEIGHT);
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
		return false;
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		this.hitboxes.remove(reason);
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
		input.read("components", ItemContainerContents.CODEC).ifPresent(contents -> this.parts.load(contents, this.getBikeType()));
		this.restore(input.getIntOr("frame_color", BikeComponents.DEFAULT_COLOR), input.getFloatOr("vehicle_damage", 0.0F),
			input.getIntOr("speed_boost", 0));
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
				this.playSound(ModSounds.BIKE_PICKUP, 0.5F, 1.4F);
				this.discard();
				if (!player.getInventory().add(stack)) {
					player.spawnAtLocation(level, stack);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (!this.canRide()) {
			if (!this.level().isClientSide()) {
				this.playSound(ModSounds.BIKE_DENY, 0.6F, 1.2F);
				player.sendOverlayMessage(Component.translatable("message.voxvelo_bikes.missing_parts"));
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
				player.sendOverlayMessage(Component.translatable("message.voxvelo_bikes.no_pedals"));
			}
			if (!player.startRiding(this)) {
				return InteractionResult.PASS;
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if (!this.level().isClientSide()) {
			this.playSound(ModSounds.BIKE_MOUNT, 0.6F, 1.2F);
		}
	}

	/** Dismounting clicks too, but not when the bike or rider is unloaded or removed. */
	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!this.level().isClientSide() && !this.isRemoved() && !passenger.isRemoved()) {
			this.playSound(ModSounds.BIKE_DISMOUNT, 0.6F, 1.0F);
		}
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
		return this.seat.attachmentPoint(this.level().isClientSide() ? this.suspension.offset() : 0.0);
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		this.seat.clampRotation(passenger);
	}

	@Override
	public void onPassengerTurned(Entity passenger) {
		float delta = this.seat.clampRotation(passenger);
		passenger.yRotO += delta;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		Vec3 target = this.seat.dismountLocation(passenger);
		return target != null ? target : super.getDismountLocationForPassenger(passenger);
	}
}
