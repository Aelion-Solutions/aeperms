package net.beteax.aeperm.common.sync;

import java.util.UUID;
import java.util.function.Consumer;

public interface SyncBus extends AutoCloseable {

    void start();

    void publishUserInvalidate(UUID uuid);

    void publishGroupInvalidate(String group);

    void publishReloadAll();

    void onMessage(Consumer<SyncMessage> listener);

    @Override
    void close();
}
