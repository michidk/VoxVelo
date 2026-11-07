package dev.michidk.voxvelo.bikes.bike;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Recovers a stationary ridden bike only while solid blocks obstruct its heading. */
final class BikeUnstuck {
private final BikeEntity bike;
private Vec3 anchor;
private int ticks;
private int lastManualTick = -100;
private boolean manual;
private boolean attempted;
private int lastPoweredTick = -100;
private int lastBlockedTick = -100;
void request() {
if (this.bike.getRider() != null && this.bike.tickCount - this.lastManualTick >= 40) {
this.lastManualTick = this.bike.tickCount; this.manual = true;
}
}
BikeUnstuck(BikeEntity bike) { this.bike = bike; }
void tick() {
Vec3 position = this.bike.position();
double yaw = Math.toRadians(this.bike.getYRot());
Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
AABB ahead = this.bike.getBoundingBox().deflate(0.05).move(forward.scale(0.3));
double step = BikeServerConfig.get().stepHeightBlocks;
if (powered(this.bike.getAppliedPower())) this.lastPoweredTick = this.bike.tickCount;
boolean wall = impassable(BikeServerConfig.get().automaticUnstuck,
this.bike.level().getBlockCollisions(this.bike, ahead).iterator().hasNext(),
this.bike.level().getBlockCollisions(this.bike, ahead.move(0, step + 0.05, 0)).iterator().hasNext());
if (wall) this.lastBlockedTick = this.bike.tickCount;
boolean blocked = wall || this.ticks > 0 && this.bike.tickCount - this.lastBlockedTick <= 10;
boolean effort = powered(this.bike.getAppliedPower()) || this.ticks > 0 && this.bike.tickCount - this.lastPoweredTick <= 20;
double movedSquared = this.anchor == null ? Double.POSITIVE_INFINITY : Math.pow(position.x - this.anchor.x, 2) + Math.pow(position.z - this.anchor.z, 2);
if (resets(this.bike.getRider() != null, this.bike.onGround(), blocked && effort, movedSquared)) {
this.anchor = position;
this.ticks = 0;
this.attempted = false;
} else if (!this.attempted) { this.ticks++; }
if (this.manual || !this.attempted && this.ticks >= 100) {
this.manual = false;
Vec3 behind = position.subtract(forward.scale(5));
Vec3 target = this.safe(behind) ? behind : null;
// Prefer the nearby floor, including slabs and downhill roads, before searching the surface above.
for (int dy = 2; dy >= -4 && target == null; dy--) {
var floor = net.minecraft.core.BlockPos.containing(behind.x, behind.y + dy, behind.z);
double height = this.bike.level().getBlockFloorHeight(floor);
if (!Double.isFinite(height) || height < 0 || height > 1) continue;
Vec3 candidate = new Vec3(behind.x, floor.getY() + height, behind.z);
if (this.safe(candidate)) target = candidate;
}
if (target == null) {
	for (int radius = 0; radius <= 3 && target == null; radius++) {
	for (int dx = -radius; dx <= radius && target == null; dx++) for (int dz = -radius; dz <= radius && target == null; dz++) {
	if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
	int x = (int) Math.floor(behind.x) + dx, z = (int) Math.floor(behind.z) + dz;
	if (!this.bike.level().getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
	int y = this.bike.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	Vec3 candidate = new Vec3(x + 0.5, y, z + 0.5);
	if (this.safe(candidate)) target = candidate;
	}
	}
}
if (target != null) {
	this.bike.teleportTo((net.minecraft.server.level.ServerLevel) this.bike.level(), target.x, target.y, target.z, java.util.Set.of(), this.bike.getYRot(), this.bike.getXRot(), false);
	this.bike.setDeltaMovement(Vec3.ZERO);
} else if (this.bike.getRider() != null) {
this.bike.getRider().sendOverlayMessage(net.minecraft.network.chat.Component.translatable("voxvelo_bikes.unstuck.no_space"));
}
this.attempted = true;
this.anchor = this.bike.position();
this.ticks = 0;
}
this.bike.getEntityData().set(BikeEntity.DATA_UNSTUCK, this.attempted || this.ticks == 0 ? 0 : (100 - this.ticks + 19) / 20);
}
static boolean powered(double watts) { return Double.isFinite(watts) && watts > 0; }
static boolean impassable(boolean enabled, boolean blocked, boolean blockedAboveStep) {
return enabled && blocked && blockedAboveStep;
}
static boolean resets(boolean ridden, boolean grounded, boolean blocked, double movedSquared) {
return !ridden || !grounded || !blocked || movedSquared > 0.25;
}
private boolean safe(Vec3 target) {
var level = this.bike.level();
if (!level.getChunkSource().hasChunk(((int) Math.floor(target.x)) >> 4, ((int) Math.floor(target.z)) >> 4) || target.y < level.getMinY() || target.y + 3 >= level.getMaxY()) return false;
AABB box = this.bike.getBoundingBox().move(target.subtract(this.bike.position()));
AABB clearance = new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + 3, box.maxZ);
return level.getWorldBorder().isWithinBounds(clearance) && level.noCollision(this.bike, clearance)
&& !level.containsAnyLiquid(clearance) && level.getBlockCollisions(this.bike, box.move(0, -0.15, 0)).iterator().hasNext();
}
}
