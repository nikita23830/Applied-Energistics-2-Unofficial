package appeng.api.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEStack;

public interface IListenerInjectItems<T extends IAEStack<T>> extends IMEMonitorHandlerReceiver<T> {
    T preInject(T input, Actionable type, BaseActionSource src);

    @Override
    default boolean isValid(Object verificationToken) {
        return true;
    }

    @Override
    default void postChange(IBaseMonitor<T> monitor, Iterable<T> change, BaseActionSource actionSource) {

    }

    @Override
    default void onListUpdate() {

    }
}
