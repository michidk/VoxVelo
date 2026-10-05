package dev.michidk.voxelvelo.inventory;

import dev.michidk.voxelvelo.bike.BikeComponents;
import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.bike.TireTread;
import dev.michidk.voxelvelo.registry.ModItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Validated part slots and vanilla container synchronization; all edits execute on the server. */
public final class BikeMaintenanceMenu extends AbstractContainerMenu {
    private final BikeEntity bike;
    private final SimpleContainerData status = new SimpleContainerData(5);

    public BikeMaintenanceMenu(int id, Inventory inventory) { this(id, inventory, null); }

    public BikeMaintenanceMenu(int id, Inventory inventory, BikeEntity bike) {
        super(dev.michidk.voxelvelo.registry.ModMenus.MAINTENANCE, id);
        this.bike = bike;
        var parts = bike == null ? new SimpleContainer(BikeComponents.COUNT) : bike.components();
        for (int i = 0; i < BikeComponents.COUNT; i++) {
            final int component = i;
            addSlot(new Slot(parts, i, 16 + i * 31, 35) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return stillValid(inventory.player) && BikeComponents.fits(component, stack);
                }
                @Override public boolean mayPickup(Player player) { return stillValid(player); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        addStandardInventorySlots(inventory, 8, 146);
        addDataSlots(status);
        updateInfo();
    }

    private void updateInfo() {
        if (bike == null) return;
        status.set(0, bike.getComponentMask());
        status.set(1, bike.tirePercent(0));
        status.set(2, bike.tirePercent(1));
        status.set(3, bike.getBikeType().ordinal());
        status.set(4, bike.getSetup());
    }

    public int frontCondition() { return status.get(1); }
    public int rearCondition() { return status.get(2); }
    /** Installed treads and bars, see {@link BikeComponents#setup}. */
    public int setup() { return status.get(4); }
    public String conditionKey() {
        if ((status.get(0) & 15) != 15) return "menu.voxelvelo.missing";
        if ((status.get(0) & 16) == 0) return "menu.voxelvelo.coasting";
        if (status.get(1) == 0 || status.get(2) == 0) return "menu.voxelvelo.burst_short";
        return "menu.voxelvelo.good";
    }

    @Override public void broadcastChanges() {
        updateInfo();
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        return bike == null || bike.isAlive() && bike.isMechanic(player) && bike.getPassengers().isEmpty()
                && bike.distanceToSqr(player) <= 64 && bike.hasWrench(player);
    }

    @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (bike == null || !stillValid(player)) return;
        TireTread tread = ModItems.treadOf(getCarried());
        if (slot >= 0 && slot < 2 && input == ContainerInput.PICKUP && tread != null) {
            // Fitting a tire replaces the old one, worn or not: repairs it, or changes the tread.
            ItemStack wheel = bike.components().getItem(slot);
            if (!wheel.isEmpty() && (BikeComponents.tire(wheel) < 1 || BikeComponents.tread(wheel) != tread)) {
                BikeComponents.tire(wheel, 1);
                BikeComponents.tread(wheel, tread);
                getCarried().shrink(1);
                bike.components().setChanged();
                bike.useWrench(player);
                updateInfo();
                broadcastChanges();
            }
            return;
        }
        var before = net.minecraft.world.item.component.ItemContainerContents.fromItems(bike.components().getItems());
        super.clicked(slot, button, input, player);
        bike.syncComponents();
        if (!before.equals(net.minecraft.world.item.component.ItemContainerContents.fromItems(bike.components().getItems()))) {
            bike.useWrench(player);
            updateInfo();
        }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (bike == null || !stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 5) {
            if (!moveItemStackTo(stack, 5, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < 5; i++) if (slots.get(i).mayPlace(stack) && !slots.get(i).hasItem()) {
                moved = moveItemStackTo(stack, i, i + 1, false);
                break;
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player); // Returns the cursor stack, never the installed components.
        if (bike != null) bike.releaseMechanic(player);
    }
}
