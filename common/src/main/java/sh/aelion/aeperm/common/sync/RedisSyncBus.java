package sh.aelion.aeperm.common.sync;

import com.google.gson.Gson;
import sh.aelion.aeperm.common.config.AepermConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.JedisPooled;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RedisSyncBus implements SyncBus {

    private final String originServerId;
    private final AepermConfig.ServerSyncConfig config;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final List<Consumer<SyncMessage>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private JedisPooled pooled;
    private ExecutorService subscriber;
    private JedisPubSub pubSub;
    private String channel;
    private URI redisUri;

    public RedisSyncBus(String originServerId, AepermConfig.ServerSyncConfig config, Logger logger) {
        this.originServerId = originServerId;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public void start() {
        channel = config.channelPrefix() + ":" + config.group();
        redisUri = URI.create(config.redis());
        pooled = new JedisPooled(redisUri);
        pooled.ping();

        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String ch, String message) {
                SyncMessage syncMessage = gson.fromJson(message, SyncMessage.class);
                if (syncMessage == null || originServerId.equals(syncMessage.originServerId())) {
                    return;
                }
                if (syncMessage.type() == SyncMessage.Type.USER_INVALIDATE && !config.syncUsers()) {
                    return;
                }
                if (syncMessage.type() == SyncMessage.Type.GROUP_INVALIDATE && !config.syncGroups()) {
                    return;
                }
                for (Consumer<SyncMessage> listener : listeners) {
                    listener.accept(syncMessage);
                }
            }
        };
        subscriber = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "aep-serversync");
            t.setDaemon(true);
            return t;
        });
        running.set(true);
        subscriber.execute(() -> {
            while (running.get()) {
                try (Jedis jedis = new Jedis(redisUri)) {
                    jedis.subscribe(pubSub, channel);
                } catch (Exception e) {
                    if (!running.get()) {
                        return;
                    }
                    logger.log(Level.WARNING, "ServerSync subscribe lost, retrying", e);
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    @Override
    public void publishUserInvalidate(UUID uuid) {
        if (!config.syncUsers()) {
            return;
        }
        publish(SyncMessage.user(originServerId, uuid));
    }

    @Override
    public void publishGroupInvalidate(String group) {
        if (!config.syncGroups()) {
            return;
        }
        publish(SyncMessage.group(originServerId, group));
    }

    @Override
    public void publishReloadAll() {
        publish(SyncMessage.reload(originServerId));
    }

    @Override
    public void onMessage(Consumer<SyncMessage> listener) {
        listeners.add(listener);
    }

    @Override
    public void close() {
        running.set(false);
        if (pubSub != null && pubSub.isSubscribed()) {
            try {
                pubSub.unsubscribe();
            } catch (Exception ignored) {
            }
        }
        if (subscriber != null) {
            subscriber.shutdownNow();
        }
        if (pooled != null) {
            pooled.close();
        }
    }

    private void publish(SyncMessage message) {
        if (pooled == null) {
            return;
        }
        pooled.publish(channel, gson.toJson(message));
    }
}
