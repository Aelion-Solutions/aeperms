package sh.aelion.aeperm.common.sync;

public final class NoopSyncBus implements SyncBus {

    @Override
    public void start() {
    }

    @Override
    public void publishUserInvalidate(java.util.UUID uuid) {
    }

    @Override
    public void publishGroupInvalidate(String group) {
    }

    @Override
    public void publishReloadAll() {
    }

    @Override
    public void onMessage(java.util.function.Consumer<SyncMessage> listener) {
    }

    @Override
    public void close() {
    }
}
