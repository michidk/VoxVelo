package dev.michidk.voxvelo.bike;

import net.minecraft.world.entity.Entity;

/** Server-owned lifecycle for the tracked entities that make up a bike's rotating hitbox. */
final class BikeHitboxes {
	private final BikeEntity bike;
	private final BikeHitboxPart[] parts = new BikeHitboxPart[BikeHitboxLayout.Part.values().length];

	BikeHitboxes(BikeEntity bike) {
		this.bike = bike;
	}

	void tick() {
		if (this.bike.level().isClientSide()) {
			return;
		}
		for (BikeHitboxLayout.Part layout : BikeHitboxLayout.Part.values()) {
			BikeHitboxPart part = this.parts[layout.ordinal()];
			if (part == null || part.isRemoved()) {
				part = new BikeHitboxPart(this.bike, layout);
				this.parts[layout.ordinal()] = part;
				this.bike.level().addFreshEntity(part);
			} else {
				part.followParent();
			}
		}
	}

	void remove(Entity.RemovalReason reason) {
		for (BikeHitboxPart part : this.parts) {
			if (part != null && !part.isRemoved()) {
				part.remove(reason);
			}
		}
	}
}
