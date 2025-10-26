package appeng.api;

import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraftforge.common.util.ForgeDirection;

public interface ICraftingEMachine extends ICraftingMachine {
    @Override
    default boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table, ForgeDirection dir) {
        return false;
    }

    long push(ICraftingPatternDetails details, long max, IItemList<IAEItemStack> waiting);
}
