package dev.michidk.voxelvelo.bike;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import dev.michidk.voxelvelo.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
	public static final float WHEEL_RADIUS = 0.375F;
	private static final double SEAT_OFFSET = -0.12;
	private static final int SUBMERGED_EJECT_TICKS = 40;
	private static final double DT = 1.0 / 20.0;
	/** Largest drop the bike stays glued to the ground over (down slopes, stairs). */
	private static final double GROUND_SNAP = 0.6;
	/** Rising more than this in one tick counts as hopping a ledge and costs speed. */
	private static final double LEDGE_PENALTY_HEIGHT = 0.55;
	private static final double CRASH_SOUND_IMPACT = 4.0;
	private static final double CRASH_DAMAGE_IMPACT = 9.0;
	/** Below this speed a bike only nudges entities out of the way; above it, it hurts them. */
	private static final double HIT_DAMAGE_SPEED = 3.0;
	private static final int HIT_COOLDOWN_TICKS = 10;

	private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_STEERING = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_BRAKE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_GRADIENT = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_GEAR = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_PEDALING = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_SURFACE = SynchedEntityData.defineId(BikeEntity.class, EntityDataSerializers.INT);

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

	public BikeEntity(EntityType<? extends BikeEntity> type, Level level) {
		super(type, level);
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
		return this.control;
	}

	private void serverTick() {
		BikeControlState input = this.effectiveControl();
		BikePhysics.Params params = this.getBikeType().params();

		if (this.isEyeInFluid(FluidTags.WATER)) {
			if (++this.submergedTicks >= SUBMERGED_EJECT_TICKS) {
				this.ejectPassengers();
			}
		} else {
			this.submergedTicks = 0;
		}

		this.steering = BikePhysics.smoothSteering(this.steering, input.steering());

		BlockState below = this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.2, this.getZ()));
		BikeSurface surface = BikeSurface.classify(below, this.isInWater());
		this.speed = BikePhysics.stepSpeed(this.speed, DT, input.propulsion(), input.brake(), surface, this.gradient, params);

		double yawRate = BikePhysics.yawRate(this.speed, this.steering, params);
		this.setYRot(this.getYRot() + (float) Math.toDegrees(yawRate * DT));
		this.setXRot(0.0F);

		double yawRad = Math.toRadians(this.getYRot());
		double forwardX = -Math.sin(yawRad);
		double forwardZ = Math.cos(yawRad);
		this.hitEntitiesAhead(forwardX, forwardZ);
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
			this.speed *= 0.7;
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
		if (this.onGround() && moved > 0.02) {
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
		if (impact > CRASH_SOUND_IMPACT) {
			this.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, (float) Math.min(1.0, impact / 12.0), 0.8F);
		}
		if (impact > CRASH_DAMAGE_IMPACT && this.getRider() instanceof Player rider && this.level() instanceof ServerLevel serverLevel) {
			rider.hurtServer(serverLevel, serverLevel.damageSources().flyIntoWall(), (float) ((impact - CRASH_DAMAGE_IMPACT) * 0.5));
		}
	}

	/**
	 * Riding into a living entity (mob or player) hurts it and knocks it forward, costing the bike speed.
	 * Solid vehicles such as other bikes are handled by normal collision instead, see {@link #canBeCollidedWith}.
	 */
	private void hitEntitiesAhead(double forwardX, double forwardZ) {
		if (this.speed < 0.5 || !(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		this.recentHits.values().removeIf(tick -> this.tickCount - tick > HIT_COOLDOWN_TICKS * 2);

		AABB area = this.getBoundingBox().inflate(0.1, 0.0, 0.1).move(forwardX * 0.4, 0.0, forwardZ * 0.4);
		for (Entity target : serverLevel.getEntities(this, area, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !this.isPassengerOfSameVehicle(e))) {
			if ((target.getX() - this.getX()) * forwardX + (target.getZ() - this.getZ()) * forwardZ <= 0.0) {
				continue;
			}
			LivingEntity living = (LivingEntity) target;
			if (this.speed < HIT_DAMAGE_SPEED) {
				living.push(forwardX * 0.08, 0.0, forwardZ * 0.08);
				continue;
			}
			Integer last = this.recentHits.get(target.getUUID());
			if (last != null && this.tickCount - last < HIT_COOLDOWN_TICKS) {
				continue;
			}
			this.recentHits.put(target.getUUID(), this.tickCount);

			Player rider = this.getRider();
			DamageSource source = rider != null ? serverLevel.damageSources().playerAttack(rider) : serverLevel.damageSources().generic();
			float damage = (float) ((this.speed - 2.5) * 0.6);
			living.hurtServer(serverLevel, source, damage);
			living.knockback(Math.min(1.5, 0.3 + this.speed * 0.07), -forwardX, -forwardZ, source, damage);
			this.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 1.0F);
			this.speed *= 0.6;
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

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
		if (onGround) {
			this.resetFallDistance();
		}
	}

	@Override
	protected Item getDropItem() {
		return ModItems.forType(this.getBikeType());
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(ModItems.forType(this.getBikeType()));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("bike_type", this.getBikeType().id);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setBikeType(BikeType.byId(input.getStringOr("bike_type", BikeType.GRAVEL.id)));
	}

	// ---------------------------------------------------------------- riding

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		InteractionResult result = super.interact(player, hand, location);
		if (result != InteractionResult.PASS) {
			return result;
		}
		if (player.isSecondaryUseActive() || !this.canAddPassenger(player)) {
			return InteractionResult.PASS;
		}
		if (!this.level().isClientSide() && !player.startRiding(this)) {
			return InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().isEmpty() && !this.isEyeInFluid(FluidTags.WATER);
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return null;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0.0, this.getBikeType().seatHeight, SEAT_OFFSET).yRot(-this.getYRot() * (float) (Math.PI / 180.0));
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
