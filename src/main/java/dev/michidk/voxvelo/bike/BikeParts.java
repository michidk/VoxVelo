package dev.michidk.voxvelo.bike;

import dev.michidk.voxvelo.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

/**
 * The parts installed on a bike, one per {@link BikeComponents} slot. They are private entity state, not an
 * inventory: they change only when a bike is built from an item, dismantled, or its tires wear. Clients only see the
 * summary the bike syncs, see {@link BikeEntity#publishParts}.
 */
final class BikeParts {
	/** The rear tire wears a little faster than the front one, it carries more of the rider. */
	private static final double REAR_TIRE_WEAR = 1.05;

	private final BikeEntity bike;
	private final NonNullList<ItemStack> stacks = NonNullList.withSize(BikeComponents.COUNT, ItemStack.EMPTY);

	BikeParts(BikeEntity bike) {
		this.bike = bike;
	}

	ItemContainerContents contents() {
		return ItemContainerContents.fromItems(this.stacks);
	}

	/** The parts a new bike of this type comes with. */
	void installStock(BikeType type) {
		for (int slot = 0; slot < BikeComponents.COUNT; slot++) {
			this.stacks.set(slot, BikeComponents.stock(type, slot));
		}
		this.sync();
	}

	/** Takes over the parts stored in a bike item; anything that does not fit its slot is left out. */
	void load(ItemContainerContents contents, BikeType type) {
		NonNullList<ItemStack> items = NonNullList.withSize(BikeComponents.COUNT, ItemStack.EMPTY);
		contents.copyInto(items);
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack part = BikeComponents.accepts(slot, items.get(slot)) ? items.get(slot).copy() : ItemStack.EMPTY;
			// Bikes from before treads existed ran on the tires of their own type.
			BikeComponents.stampTread(part, TireTread.nativeFor(type));
			this.stacks.set(slot, part);
		}
		this.sync();
	}

	/** Removes every part, for dropping them. Emptying first means no later interaction can duplicate them. */
	List<ItemStack> takeAll() {
		List<ItemStack> taken = new ArrayList<>();
		for (int slot = 0; slot < BikeComponents.COUNT; slot++) {
			taken.add(this.stacks.get(slot));
			this.stacks.set(slot, ItemStack.EMPTY);
		}
		return taken;
	}

	/** Wears both tires by {@code amount} (share of a fresh tire); a burst pops audibly and the rider is told. */
	void wearTires(double amount, @Nullable Player rider) {
		for (int slot : new int[] {BikeComponents.FRONT, BikeComponents.REAR}) {
			ItemStack wheel = this.stacks.get(slot);
			double before = BikeComponents.tire(wheel);
			if (wheel.isEmpty() || before <= 0) {
				continue;
			}
			double after = Math.max(0, before - amount * (slot == BikeComponents.REAR ? REAR_TIRE_WEAR : 1.0));
			BikeComponents.tire(wheel, after);
			if (after == 0) {
				this.bike.playSound(ModSounds.BIKE_TIRE_BURST, 1.0F, 1.6F);
				this.bike.playSound(ModSounds.BIKE_TIRE_HISS, 0.8F, 1.4F);
				if (rider != null) {
					rider.sendSystemMessage(Component.translatable(slot == BikeComponents.FRONT ? "message.voxvelo.front_burst" : "message.voxvelo.rear_burst"));
				}
			}
		}
		this.sync();
	}

	/** Publishes which parts are installed, the tire condition and the handling setup to every client. */
	void sync() {
		if (this.bike.level().isClientSide()) {
			return;
		}
		int mask = 0;
		for (int slot = 0; slot < BikeComponents.COUNT; slot++) {
			if (!this.stacks.get(slot).isEmpty()) {
				mask |= BikeComponents.bit(slot);
			}
		}
		int tires = BikeComponents.packTires(percent(this.stacks.get(BikeComponents.FRONT)), percent(this.stacks.get(BikeComponents.REAR)));
		this.bike.publishParts(mask, tires, BikeComponents.setup(this.bike.getBikeType(), this.stacks));
	}

	private static int percent(ItemStack wheel) {
		return (int) Math.ceil(BikeComponents.tire(wheel) * 100);
	}
}
