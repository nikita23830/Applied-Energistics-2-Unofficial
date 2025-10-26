/*
 * The MIT License (MIT) Copyright (c) 2013 AlgorithmX2 Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions: The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.storage;

import javax.annotation.Nonnull;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.IterationCounter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * AE's Equivalent to IInventory, used to reading contents, and manipulating contents of ME Inventories.
 * <p>
 * Implementations should COMPLETELY ignore stack size limits from an external view point, Meaning that you can inject
 * Integer.MAX_VALUE items and it should work as defined, or be able to extract Integer.MAX_VALUE and have it work as
 * defined, Translations to MC's max stack size are external to the AE API.
 * <p>
 * If you want to request a stack of an item, you should should determine that prior to requesting the stack from the
 * inventory.
 */
public interface IMEInventory<StackType extends IAEStack> {

    /**
     * Store new items, or simulate the addition of new items into the ME Inventory.
     *
     * @param input item to add.
     * @param type  action type
     * @param src   action source
     * @return returns the number of items not added.
     */
    StackType injectItems(StackType input, Actionable type, BaseActionSource src);

    default StackType injectItemsNotSave(StackType input, final Actionable mode, final BaseActionSource src) {
        return null;
    }

    default void _saveChanges() {}

    default List<StackType> injectMultiItems(IItemList<StackType> input, Actionable type, BaseActionSource src) {
        return new ArrayList<>();
    }

    /**
     * Extract the specified item from the ME Inventory
     *
     * @param request item to request ( with stack size. )
     * @param mode    simulate, or perform action?
     * @return returns the number of items extracted, null
     */
    StackType extractItems(StackType request, Actionable mode, BaseActionSource src);

    /**
     * Request a full report of all available items, storage.
     * 
     * @deprecated use/override {@link #getAvailableItems(IItemList<StackType>,int)} instead
     *
     * @param out the IItemList the results will be written too
     * @return returns same list that was passed in, is passed out
     */
    @Deprecated
    default IItemList<StackType> getAvailableItems(IItemList<StackType> out) {
        var ret = getAvailableItems(out, IterationCounter.incrementGlobalDepth());
        IterationCounter.decrementGlobalDepth();
        return ret;
    }

    /**
     * Request a full report of all available items, storage.
     *
     * @param out       the IItemList the results will be written too
     * @param iteration numeric id for this iteration, use {@link appeng.util.IterationCounter#fetchNewId()} to avoid
     *                  conflicts
     * @return returns same list that was passed in, is passed out
     */
    default IItemList<StackType> getAvailableItems(IItemList<StackType> out, int iteration) {
        IterationCounter.incrementGlobalDepthWith(iteration);
        var ret = getAvailableItems(out);
        IterationCounter.decrementGlobalDepth();
        return ret;
    }

    /**
     * Request a report of how many of a single item type are available in storage. It falls back to
     * {@link IMEInventory#getAvailableItems} if a more optimized implementation is not present.
     *
     * @deprecated use/override {@link #getAvailableItem(StackType,int)} instead
     *
     * @param request The item type to search for, it does not get modified
     * @return A new stack with the stack size set to the count of items in storage, or null if none are present
     */
    @SuppressWarnings("unchecked") // changing the generic StackType to be correct here is too much of a breaking API
    // change
    @Deprecated
    default StackType getAvailableItem(@Nonnull StackType request) {
        var ret = getAvailableItem(request, IterationCounter.incrementGlobalDepth());
        IterationCounter.decrementGlobalDepth();
        return ret;
    }

    /**
     * Request a report of how many of a single item type are available in storage. It falls back to
     * {@link IMEInventory#getAvailableItems} if a more optimized implementation is not present.
     *
     * @param request   The item type to search for, it does not get modified
     * @param iteration numeric id for this iteration, use {@link appeng.util.IterationCounter#fetchNewId()} to avoid
     *                  conflicts
     * @return A new stack with the stack size set to the count of items in storage, or null if none are present
     */
    @SuppressWarnings("unchecked") // changing the generic StackType to be correct here is too much of a breaking API
                                   // change
    default StackType getAvailableItem(@Nonnull StackType request, int iteration) {
        return getAvailableItems((IItemList<StackType>) getChannel().createList(), iteration).findPrecise(request);
    }

    /**
     * Request a full report of all available items that match a fuzzy filter, in order of priority (descending order).
     * Mostly relevant for extract-only inventories.
     *
     * @param out       the Collection the results will be written to
     * @param fuzzyItem the AEItemStack that will be passed to
     *                  {@link appeng.util.item.ItemList#findFuzzy(IAEItemStack filter, FuzzyMode fuzzy)} as the filter
     * @param fuzzyMode the FuzzyMode instance that will be passed to
     *                  {@link appeng.util.item.ItemList#findFuzzy(IAEItemStack filter, FuzzyMode fuzzy)}
     * @param iteration numeric id for this iteration, use {@link appeng.util.IterationCounter#fetchNewId()} to avoid
     *                  conflicts
     * @return returns a list of objects in the AE2 network sorted by storage priority
     */
    default Collection<StackType> getSortedFuzzyItems(Collection<StackType> out, StackType fuzzyItem,
                                                      FuzzyMode fuzzyMode, int iteration) {
        return out;
    }

    /**
     * @return the type of channel your handler should be part of
     */

    /**
     * @return the type of channel your handler should be part of
     */
    StorageChannel getChannel();
}
