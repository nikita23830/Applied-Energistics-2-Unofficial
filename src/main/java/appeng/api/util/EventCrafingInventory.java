package appeng.api.util;

import appeng.container.ContainerNull;
import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventCrafingInventory extends InventoryCrafting {
    public static final ExecutorService executors = Executors.newFixedThreadPool(20);

    public EventCrafingInventory(Container container, int x, int y) {
        super(container, x, y);
    }

    public EventCrafingInventory(InventoryCrafting inv) {
        super(new ContainerNull(), inv.getSizeInventory(), 1);
        for (int x = 0; x < inv.getSizeInventory(); x++) {
            setInventorySlotContents(x, inv.getStackInSlot(x) == null ? null : inv.getStackInSlot(x).copy());
        }
    }

    public EventCrafingInventory(IInventory inv) {
        super(new ContainerNull(), inv.getSizeInventory(), 1);
        for (int x = 0; x < inv.getSizeInventory(); x++) {
            ItemStack pre = inv == null ? null : inv.getStackInSlot(x);
            setInventorySlotContents(x, pre == null ? null : pre.copy());
        }
    }
}
