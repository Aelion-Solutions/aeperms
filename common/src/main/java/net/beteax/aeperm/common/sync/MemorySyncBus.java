package net.beteax.aeperm.common.sync;

import com.google.gson.Gson;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import java.util.function.Consumer;

public final class MemorySyncBus implements SyncBus {

    private final String originServerId;
    private final boolean syncUsers;
    private final boolean syncGroups;
    private final List<Consumer<SyncMessage>> listeners = new CopyOnWriteArrayList<>();
    private final List<MemorySyncBus> peers = new CopyOnWriteArrayList<>();
    private final Gson gson = new Gson();

    public MemorySyncBus(String originServerId, boolean syncUsers, boolean syncGroups) {
        this.originServerId = originServerId;
        this.syncUsers = syncUsers;
        this.syncGroups = syncGroups;
    }

    public void link(MemorySyncBus peer) {
        if (peer != this && !peers.contains(peer)) {
            peers.add(peer);
            peer.peers.add(this);
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void publishUserInvalidate(UUID uuid) {
        if (!syncUsers) {
            return;
        }
        broadcast(SyncMessage.user(originServerId, uuid));
    }

    @Override
    public void publishGroupInvalidate(String group) {
        if (!syncGroups) {
            return;
        }
        broadcast(SyncMessage.group(originServerId, group));
    }

    @Override
    public void publishReloadAll() {
        broadcast(SyncMessage.reload(originServerId));
    }

    @Override
    public void onMessage(Consumer<SyncMessage> listener) {
        listeners.add(listener);
    }

    @Override
    public void close() {
        listeners.clear();
        peers.clear();
    }

    public String encode(SyncMessage message) {
        return gson.toJson(message);
    }

    public SyncMessage decode(String json) {
        return gson.fromJson(json, SyncMessage.class);
    }

    private void broadcast(SyncMessage message) {
        for (MemorySyncBus peer : peers) {
            peer.deliver(message);
        }
    }

    private void deliver(SyncMessage message) {
        if (originServerId.equals(message.originServerId())) {
            return;
        }
        for (Consumer<SyncMessage> listener : listeners) {
            listener.accept(message);
        }
    }
}
