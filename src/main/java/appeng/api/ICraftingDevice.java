package appeng.api;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import net.minecraft.inventory.InventoryCrafting;

public interface ICraftingDevice extends ICraftingMedium {
    @Override
    default boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        return false;
    }

    long push(ICraftingPatternDetails details, long max, IItemList<IAEItemStack> waiting);
}
