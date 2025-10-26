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

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin logic layer that can be swapped with different IMEInventory implementations, used to handle features related to
 * storage, that are Separate from the storage medium itself.
 *
 * @param <StackType>
 */
public interface IMEInventoryHandler<StackType extends IAEStack> extends IMEInventory<StackType> {

    /**
     * determine if items can be injected/extracted.
     *
     * @return the access
     */
    AccessRestriction getAccess();

    /**
     * determine if a particular item is prioritized for this inventory handler, if it is, then it will be added to this
     * inventory prior to any non-prioritized inventories.
     *
     * @param input - item that might be added
     * @return if its prioritized
     */
    boolean isPrioritized(StackType input);

    default boolean isPrioritized(IItemList<StackType> input) {
        return false;
    }

    /**
     * determine if an item can be accepted and stored.
     *
     * @param input - item that might be added
     * @return if the item can be added
     */
    boolean canAccept(StackType input);

    default boolean canAccept(IItemList<StackType> input) {
        return false;
    }

    /**
     * determine what the priority of the inventory is.
     *
     * @return the priority, zero is default, positive and negative are supported.
     */
    int getPriority();

    /**
     * pass back value for blinkCell.
     *
     * @return the slot index for the cell that this represents in the storage unit, the method on the
     *         {@link ICellContainer} will be called with this value, only trust the return value of this method if you
     *         are the implementer of this.
     */
    int getSlot();

    /**
     * AE Uses a two pass placement system, the first pass checks contents and tries to find a place where the item
     * belongs, however in some cases you can save processor time, or require that the second, or first pass is simply
     * ignored, this allows you to do that.
     *
     * @param i - pass number ( 1 or 2 )
     * @return true, if this inventory is valid for this pass.
     */
    boolean validForPass(int i);

    /**
     * Gets whether an inventory is "Sticky" i.e. only it and other sticky storages that have partitions with certain
     * items are allowed to be put into sticky storages.
     * 
     * @return
     */
    default boolean getSticky() {
        return false;
    }

    /**
     * Gets whether an inventory stores items for auto crafting. For example the
     * {@link appeng.me.cache.CraftingGridCache}
     *
     * @return true if this inventory stores items for auto crafting
     */
    default boolean isAutoCraftingInventory() {
        return false;
    }

    default List<StackType> injectMultiItems(IItemList<StackType> input, Actionable mode, BaseActionSource src) {
        return new ArrayList<>();
    }

    default void _saveChanges() {

    }

}
