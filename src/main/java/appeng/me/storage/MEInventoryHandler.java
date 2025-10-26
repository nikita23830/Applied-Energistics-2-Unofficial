/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.prioitylist.DefaultPriorityList;
import appeng.util.prioitylist.IPartitionList;

public class MEInventoryHandler<T extends IAEStack<T>> implements IMEInventoryHandler<T> {

    private final IMEInventoryHandler<T> internal;
    private int myPriority;
    private IncludeExclude myWhitelist;
    private AccessRestriction myAccess;
    private IPartitionList<T> myPartitionList;
    private IPartitionList<T> myExtractPartitionList;

    private AccessRestriction cachedAccessRestriction;
    protected boolean hasReadAccess;
    protected boolean hasWriteAccess;
    protected boolean isSticky;
    protected boolean isExtractFilterActive;

    public MEInventoryHandler(final IMEInventory<T> i, final StorageChannel channel) {
        if (i instanceof IMEInventoryHandler) {
            this.internal = (IMEInventoryHandler<T>) i;
        } else {
            this.internal = new MEPassThrough<>(i, channel);
        }

        this.myPriority = 0;
        this.myWhitelist = IncludeExclude.WHITELIST;
        this.setBaseAccess(AccessRestriction.READ_WRITE);
        this.myPartitionList = new DefaultPriorityList<>();
        this.myExtractPartitionList = new DefaultPriorityList<>();
    }

    public IncludeExclude getWhitelist() {
        return this.myWhitelist;
    }

    public void setWhitelist(final IncludeExclude myWhitelist) {
        this.myWhitelist = myWhitelist;
    }

    public AccessRestriction getBaseAccess() {
        return this.myAccess;
    }

    public void setBaseAccess(final AccessRestriction myAccess) {
        this.myAccess = myAccess;
        this.cachedAccessRestriction = this.myAccess.restrictPermissions(this.internal.getAccess());
        this.hasReadAccess = this.cachedAccessRestriction.hasPermission(AccessRestriction.READ);
        this.hasWriteAccess = this.cachedAccessRestriction.hasPermission(AccessRestriction.WRITE);
    }

    public IPartitionList<T> getExtractPartitionList() {
        return this.myExtractPartitionList;
    }

    public void setExtractPartitionList(IPartitionList<T> myExtractPartitionList) {
        this.myExtractPartitionList = myExtractPartitionList;
    }

    public IPartitionList<T> getPartitionList() {
        return this.myPartitionList;
    }

    public void setPartitionList(final IPartitionList<T> myPartitionList) {
        this.myPartitionList = myPartitionList;
    }

    @Override
    public T injectItemsNotSave(T input, Actionable type, BaseActionSource src) {
        if (!this.canAccept(input)) {
            return input;
        }

        return this.internal.injectItemsNotSave(input, type, src);
    }

    @Override
    public void _saveChanges() {
        this.internal._saveChanges();
    }

    @Override
    public T injectItems(final T input, final Actionable type, final BaseActionSource src) {
        if (!this.canAccept(input)) {
            return input;
        }

        return this.internal.injectItems(input, type, src);
    }

    @Override
    public List<T> injectMultiItems(IItemList<T> input, Actionable mode, BaseActionSource src) {
        if (!this.canAccept(input)) {
            return input.toList();
        }

        return this.internal.injectMultiItems(input, mode, src);
    }

    @Override
    public T extractItems(final T request, final Actionable type, final BaseActionSource src) {
        if (!this.hasReadAccess) {
            return null;
        }
        if (this.isExtractFilterActive() && !this.myExtractPartitionList.isEmpty()) {
            Predicate<T> filterCondition = this.getExtractFilterCondition();
            if (!filterCondition.test(request)) {
                return null;
            }
        }

        return this.internal.extractItems(request, type, src);
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out, int iteration) {
        if (!this.hasReadAccess && !isVisible()) {
            return out;
        }

        if (this.isExtractFilterActive() && !this.myExtractPartitionList.isEmpty()) {
            return this.filterAvailableItems(out, iteration);
        } else {
            return this.internal.getAvailableItems(out, iteration);
        }
    }

    public boolean isVisible() {
        boolean bool = this.internal instanceof MEMonitorIInventory inv && inv.getMode() == StorageFilter.NONE;
        if (this.internal instanceof MEMonitorPassThrough inv && inv.getMode() == StorageFilter.NONE) bool = true;
        return bool;
    }

    protected IItemList<T> filterAvailableItems(IItemList<T> out, int iteration) {
        final IItemList<T> allAvailableItems = this.internal
                .getAvailableItems((IItemList<T>) this.internal.getChannel().createList(), iteration);
        Predicate<T> filterCondition = this.getExtractFilterCondition();
        for (T item : allAvailableItems) {
            if (filterCondition.test(item)) {
                out.add(item);
            }
        }
        return out;
    }

    @Override
    public T getAvailableItem(@Nonnull T request, int iteration) {
        if (!this.hasReadAccess && !isVisible()) {
            return null;
        }

        if (this.isExtractFilterActive() && !this.myExtractPartitionList.isEmpty()) {
            Predicate<T> filterCondition = this.getExtractFilterCondition();
            if (!filterCondition.test(request)) {
                return null;
            }
        }

        return this.internal.getAvailableItem(request, iteration);
    }

    public Predicate<T> getExtractFilterCondition() {
        return this.myWhitelist == IncludeExclude.WHITELIST ? i -> this.myExtractPartitionList.isListed(i)
                : i -> !this.myExtractPartitionList.isListed(i);
    }

    public boolean isExtractFilterActive() {
        return this.isExtractFilterActive;
    }

    public void setIsExtractFilterActive(boolean isExtractFilterActive) {
        this.isExtractFilterActive = isExtractFilterActive;
    }

    @Override
    public StorageChannel getChannel() {
        return this.internal.getChannel();
    }

    @Override
    public AccessRestriction getAccess() {
        return this.cachedAccessRestriction;
    }

    @Override
    public boolean isPrioritized(final T input) {
        if (this.myWhitelist == IncludeExclude.WHITELIST) {
            return this.myPartitionList.isListed(input) || this.internal.isPrioritized(input);
        }
        return false;
    }

    @Override
    public boolean isPrioritized(IItemList<T> input) {
        if (this.myWhitelist == IncludeExclude.WHITELIST) {
            return this.myPartitionList.isListed(input) || this.internal.isPrioritized(input);
        }
        return false;
    }

    @Override
    public boolean canAccept(final T input) {
        if (!this.hasWriteAccess) {
            return false;
        }

        if (this.myWhitelist == IncludeExclude.BLACKLIST && this.myPartitionList.isListed(input)) {
            return false;
        }
        if (this.myPartitionList.isEmpty() || this.myWhitelist == IncludeExclude.BLACKLIST) {
            return this.internal.canAccept(input);
        }
        return this.myPartitionList.isListed(input) && this.internal.canAccept(input);
    }

    public boolean canAccept(final IItemList<T> input) {
        if (!this.hasWriteAccess) {
            return false;
        }

        if (this.myWhitelist == IncludeExclude.BLACKLIST) {
            for (T t : input) {
                if (this.myPartitionList.isListed(t))
                    return false;
            }
        }
        if (this.myPartitionList.isEmpty() || this.myWhitelist == IncludeExclude.BLACKLIST) {
            for (T t : input) {
                if (!this.internal.canAccept(t))
                    return false;
            }
            return true;
        }
        for (T t : input) {
            if (!this.myExtractPartitionList.isListed(t) || !this.internal.canAccept(t))
                return false;
        }
        return true;
    }


    @Override
    public int getPriority() {
        return this.myPriority;
    }

    public void setPriority(final int myPriority) {
        this.myPriority = myPriority;
    }

    @Override
    public int getSlot() {
        return this.internal.getSlot();
    }

    @Override
    public boolean validForPass(final int i) {
        return true;
    }

    @Override
    public boolean getSticky() {
        return isSticky || this.internal.getSticky();
    }

    public IMEInventory<T> getInternal() {
        return this.internal;
    }

    public void setSticky(boolean value) {
        isSticky = value;
    }
}
