/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.cache;

import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import appeng.api.storage.IListenerInjectItems;
import appeng.api.storage.data.IAEItemStack;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.events.MENetworkStorageEvent;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.ItemWatcher;
import appeng.util.IterationCounter;
import appeng.util.item.LazyItemList;

public class NetworkMonitor<T extends IAEStack<T>> implements IMEMonitor<T> {

    @Nonnull
    private static final Deque<NetworkMonitor<?>> GLOBAL_DEPTH = Lists.newLinkedList();

    @Nonnull
    private final GridStorageCache myGridCache;

    @Nonnull
    private final StorageChannel myChannel;

    @Nonnull
    private final IItemList<T> cachedList;

    @Nonnull
    private final Map<IMEMonitorHandlerReceiver<T>, Object> listeners;

    private boolean sendEvent = false;
    private boolean hasChanged = false;

    @Nonnegative
    private int localDepthSemaphore = 0;

    private List<IListenerInjectItems<T>> injectListeners = Lists.newArrayList();

    public NetworkMonitor(final GridStorageCache cache, final StorageChannel chan) {
        this.myGridCache = cache;
        this.myChannel = chan;
        this.cachedList = (IItemList<T>) chan.createList();
        this.listeners = new HashMap<>();
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver<T> l, final Object verificationToken) {
        if (l instanceof IListenerInjectItems) {
            this.injectListeners.add((IListenerInjectItems<T>) l);
            return;
        }
        this.listeners.put(l, verificationToken);
    }

    @Override
    public boolean canAccept(final T input) {
        return this.getHandler().canAccept(input);
    }

    @Override
    public T extractItems(final T request, final Actionable mode, final BaseActionSource src) {
        if (mode == Actionable.SIMULATE) {
            return this.getHandler().extractItems(request, mode, src);
        }

        localDepthSemaphore++;
        final T leftover = this.getHandler().extractItems(request, mode, src);
        localDepthSemaphore--;

        if (localDepthSemaphore == 0) {
            this.monitorDifference(request.copy(), leftover, true, src);
        }

        return leftover;
    }

    @Override
    public AccessRestriction getAccess() {
        return this.getHandler().getAccess();
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList out, int iteration) {
        return this.getHandler().getAvailableItems(out, iteration);
    }

    @Override
    public StorageChannel getChannel() {
        return this.getHandler().getChannel();
    }

    @Override
    public int getPriority() {
        return this.getHandler().getPriority();
    }

    @Override
    public int getSlot() {
        return this.getHandler().getSlot();
    }

    @Nonnull
    @Override
    public IItemList<T> getStorageList() {
        if (this.hasChanged) {
            this.hasChanged = false;
            this.cachedList.resetStatus();
            return this.getAvailableItems(this.cachedList, IterationCounter.fetchNewId());
        }

        return this.cachedList;
    }

    @Override
    public T injectItems(T input, final Actionable mode, final BaseActionSource src) {
        for (final IListenerInjectItems<T> listener : this.injectListeners) {
            input = listener.preInject(input, mode, src);
            if (input == null) {
                return null;
            }
        }
        if (input == null) {
            return null;
        }
        if (this.getHandler() == null) {
            return input;
        }
        if (mode == Actionable.SIMULATE) {
            return this.getHandler().injectItems(input, mode, src);
        }

        localDepthSemaphore++;
        final T leftover = this.getHandler().injectItems(input, mode, src);
        localDepthSemaphore--;

        if (localDepthSemaphore == 0) {
            this.monitorDifference(input.copy(), leftover, false, src);
        }

        return leftover;
    }

    @Override
    public boolean isPrioritized(final T input) {
        return this.getHandler().isPrioritized(input);
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver<T> l) {
        if (l instanceof IListenerInjectItems) {
            this.injectListeners.remove(l);
            return;
        }
        this.listeners.remove(l);
    }

    @Override
    public boolean validForPass(final int i) {
        return this.getHandler().validForPass(i);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public IMEInventoryHandler<T> getHandler() {
        switch (this.myChannel) {
            case ITEMS -> {
                return (IMEInventoryHandler<T>) this.myGridCache.getItemInventoryHandler();
            }
            case FLUIDS -> {
                return (IMEInventoryHandler<T>) this.myGridCache.getFluidInventoryHandler();
            }
            default -> {}
        }
        return null;
    }

    private Iterator<Entry<IMEMonitorHandlerReceiver<T>, Object>> getListeners() {
        return this.listeners.entrySet().iterator();
    }

    private T monitorDifference(final IAEStack original, final T leftOvers, final boolean extraction,
            final BaseActionSource src) {
        final T diff = (T) original.copy();

        if (extraction) {
            diff.setStackSize(leftOvers == null ? 0 : -leftOvers.getStackSize());
        } else if (leftOvers != null) {
            diff.decStackSize(leftOvers.getStackSize());
        }

        if (diff.getStackSize() != 0) {
            this.postChangesToListeners(ImmutableList.of(diff), src);
        }

        return leftOvers;
    }

    private void notifyListenersOfChange(final Iterable<T> diff, final BaseActionSource src) {
        this.hasChanged = true;
        final Iterator<Entry<IMEMonitorHandlerReceiver<T>, Object>> i = this.getListeners();

        while (i.hasNext()) {
            final Entry<IMEMonitorHandlerReceiver<T>, Object> o = i.next();
            final IMEMonitorHandlerReceiver<T> receiver = o.getKey();
            if (receiver.isValid(o.getValue())) {
                receiver.postChange(this, diff, src);
            } else {
                i.remove();
            }
        }
    }

    private void postChangesToListeners(final Iterable<T> changes, final BaseActionSource src) {
        this.postChange(true, changes, src);
    }

    protected void postChange(final boolean add, final Iterable<T> changes, final BaseActionSource src) {
        if (localDepthSemaphore > 0 || GLOBAL_DEPTH.contains(this)) {
            return;
        }

        GLOBAL_DEPTH.push(this);
        localDepthSemaphore++;

        this.sendEvent = true;

        this.notifyListenersOfChange(changes, src);

        for (final T changedItem : changes) {
            if (changedItem == null) {
                continue;
            }

            T difference = changedItem;

            if (!add) {
                difference = changedItem.copy();
                difference.setStackSize(-changedItem.getStackSize());
            }

            if (this.myGridCache.getInterestManager().containsKey(changedItem)) {
                final Collection<ItemWatcher> list = this.myGridCache.getInterestManager().get(changedItem);

                if (!list.isEmpty()) {
                    IAEStack<T> fullStack = this.getHandler()
                            .getAvailableItem(changedItem, IterationCounter.fetchNewId());

                    if (fullStack == null) {
                        fullStack = changedItem.copy();
                        fullStack.setStackSize(0);
                    }

                    this.myGridCache.getInterestManager().enableTransactions();

                    IItemList<T> itemList = new LazyItemList<>(this::getStorageList);
                    for (final ItemWatcher iw : list) {
                        iw.getHost().onStackChange(itemList, fullStack, difference, src, this.getChannel());
                    }

                    this.myGridCache.getInterestManager().disableTransactions();
                }
            }
        }

        final NetworkMonitor<?> last = GLOBAL_DEPTH.pop();
        localDepthSemaphore--;

        if (last != this) {
            throw new IllegalStateException("Invalid Access to Networked Storage API detected.");
        }
    }

    void forceUpdate() {
        this.hasChanged = true;

        final Iterator<Entry<IMEMonitorHandlerReceiver<T>, Object>> i = this.getListeners();
        while (i.hasNext()) {
            final Entry<IMEMonitorHandlerReceiver<T>, Object> o = i.next();
            final IMEMonitorHandlerReceiver<T> receiver = o.getKey();

            if (receiver.isValid(o.getValue())) {
                receiver.onListUpdate();
            } else {
                i.remove();
            }
        }
    }

    void onTick() {
        if (this.sendEvent) {
            this.sendEvent = false;
            this.myGridCache.getGrid().postEvent(new MENetworkStorageEvent(this, this.myChannel));
        }
    }
}
