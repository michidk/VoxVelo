package dev.michidk.voxvelo.bike;

import dev.michidk.voxvelo.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One invisible, independently targetable section of a bike. It is a real tracked entity so vanilla attacks and
 * interactions work without replacing Minecraft's ray casting; all gameplay is forwarded to the parent bike.
 */
public final class BikeHitboxPart extends Entity {
	private static final EntityDataAccessor<Integer> DATA_PARENT = SynchedEntityData.defineId(BikeHitboxPart.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PART = SynchedEntityData.defineId(BikeHitboxPart.class, EntityDataSerializers.INT);
	private static final int PARENT_GRACE_TICKS = 20;

	private @Nullable BikeEntity parent;
	private BikeHitboxLayout.Part part = BikeHitboxLayout.Part.FRAME;

	public BikeHitboxPart(EntityType<? extends BikeHitboxPart> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	BikeHitboxPart(BikeEntity parent, BikeHitboxLayout.Part part) {
		this(ModEntities.BICYCLE_PART, parent.level());
		this.parent = parent;
		this.part = part;
		this.entityData.set(DATA_PARENT, parent.getId());
		this.entityData.set(DATA_PART, part.ordinal());
		this.refreshDimensions();
		this.followParent();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_PARENT, 0);
		entityData.define(DATA_PART, BikeHitboxLayout.Part.FRAME.ordinal());
	}

	@Override
	public void tick() {
		super.tick();
		BikeEntity bike = this.parent();
		if (bike != null) {
			this.followParent();
		} else if (this.tickCount > PARENT_GRACE_TICKS) {
			this.discard();
		}
	}

	void followParent() {
		BikeEntity bike = this.parent();
		if (bike == null) {
			return;
		}
		Vec3 position = BikeHitboxLayout.position(bike.position(), bike.getYRot(), this.part);
		this.setPos(position);
		this.setYRot(bike.getYRot());
		this.setXRot(bike.getXRot());
	}

	private @Nullable BikeEntity parent() {
		if (this.parent != null && !this.parent.isRemoved()) {
			return this.parent;
		}
		Entity candidate = this.level().getEntity(this.entityData.get(DATA_PARENT));
		this.parent = candidate instanceof BikeEntity bike ? bike : null;
		return this.parent;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_PART.equals(key)) {
			BikeHitboxLayout.Part[] parts = BikeHitboxLayout.Part.values();
			int ordinal = this.entityData.get(DATA_PART);
			this.part = ordinal >= 0 && ordinal < parts.length ? parts[ordinal] : BikeHitboxLayout.Part.FRAME;
			this.refreshDimensions();
		}
		if (DATA_PARENT.equals(key)) {
			this.parent = null;
		}
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return EntityDimensions.fixed(this.part.width(), this.part.height());
	}

	@Override
	public boolean isPickable() {
		return this.parent() != null;
	}

	@Override
	public boolean is(Entity entity) {
		return super.is(entity) || entity == this.parent();
	}

	@Override
	public ItemStack getPickResult() {
		BikeEntity bike = this.parent();
		return bike == null ? ItemStack.EMPTY : bike.getPickResult();
	}

	@Override
	public Component getName() {
		BikeEntity bike = this.parent();
		return bike == null ? super.getName() : bike.getName();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		BikeEntity bike = this.parent();
		return bike != null && bike.hurtServer(level, source, amount);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		BikeEntity bike = this.parent();
		return bike == null ? InteractionResult.PASS : bike.interact(player, hand, location);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
