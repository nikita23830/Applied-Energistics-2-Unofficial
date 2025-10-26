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

package appeng.api.networking.crafting;

import javax.annotation.Nullable;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.CraftCancelListener;
import appeng.api.util.CraftCompleteListener;
import appeng.api.util.CraftUpdateListener;
import appeng.api.config.CraftingAllow;

public interface ICraftingCPU extends IBaseMonitor<IAEItemStack> {

    /**
     * @return true if the CPU currently has a job.
     */
    boolean isBusy();

    /**
     * @return the action source for the CPU.
     */
    BaseActionSource getActionSource();

    /**
     * @return the available storage in bytes
     */
    long getAvailableStorage();

    /**
     * @return the storage used by an active crafting job in bytes
     */
    default long getUsedStorage() {
        return 0;
    }

    /**
     * @return the number of co-processors in the CPU.
     */
    int getCoProcessors();

    /**
     * @return an empty string or the name of the cpu.
     */
    String getName();

    /**
     * @return final output of the current crafting operation, or null if not crafting
     */
    @Nullable
    default IAEItemStack getFinalOutput() {
        return null;
    }

    /**
     * @return remaining count of items (or other units of processing) for the current crafting job
     */
    default long getRemainingItemCount() {
        return 0;
    }

    /**
     * @return total count of items (or other units of processing) for the current crafting job
     */
    default long getStartItemCount() {
        return 0;
    }

    /**
     * When player name is follow this crafting will remove it from follow list </br>
     * Otherwise will add it into follow list
     * 
     * @param name
     */
    public default void togglePlayerFollowStatus(String name) {}

    /**
     * @param craftCompleteListener a callback that is called when task is complete
     */
    default void addOnCompleteListener(CraftCompleteListener craftCompleteListener) {}

    /**
     * @param onCancelListener a callback that is called when task is canceled
     */
    default void addOnCancelListener(CraftCancelListener onCancelListener) {}

    /**
     * called when craft executes, passing number of tasks executed to Listener
     * 
     * @param onCraftingStatusUpdate
     */
    default void addOnCraftingUpdateListener(CraftUpdateListener onCraftingStatusUpdate) {}

    public default CraftingAllow getCraftingAllowMode() {
        return CraftingAllow.ALLOW_ALL;
    }

    public default void changeCraftingAllowMode(CraftingAllow mode) {}

    /**
     * get this crafting elapsed time
     *
     * @return
     */
    public default long getElapsedTime() {
        return 0l;
    }
}
