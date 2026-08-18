package net.beteax.aeperm.common.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AepermConfig {

    private String serverId = "server-1";
    private StorageConfig storage = new StorageConfig();
    private CacheConfig cache = new CacheConfig();
    private ServerSyncConfig serversync = new ServerSyncConfig();

    public String serverId() {
        return serverId;
    }

    public void serverId(String serverId) {
        this.serverId = serverId;
    }

    public StorageConfig storage() {
        return storage;
    }

    public CacheConfig cache() {
        return cache;
    }

    public ServerSyncConfig serversync() {
        return serversync;
    }

    @SuppressWarnings("unchecked")
    public static AepermConfig fromMap(Map<String, Object> root) {
        AepermConfig config = new AepermConfig();
        if (root.get("server-id") instanceof String id) {
            config.serverId = id;
        }
        if (root.get("storage") instanceof Map<?, ?> storageMap) {
            config.storage = StorageConfig.fromMap((Map<String, Object>) storageMap);
        }
        if (root.get("cache") instanceof Map<?, ?> cacheMap) {
            config.cache = CacheConfig.fromMap((Map<String, Object>) cacheMap);
        }
        if (root.get("serversync") instanceof Map<?, ?> syncMap) {
            config.serversync = ServerSyncConfig.fromMap((Map<String, Object>) syncMap);
        }
        return config;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("server-id", serverId);
        map.put("storage", storage.toMap());
        map.put("cache", cache.toMap());
        map.put("serversync", serversync.toMap());
        return map;
    }

    public static final class StorageConfig {
        private String url = "jdbc:postgresql://localhost:5432/aeperm";
        private String user = "aeperm";
        private String password = "aeperm";
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;

        public String url() {
            return url;
        }

        public String user() {
            return user;
        }

        public String password() {
            return password;
        }

        public int maximumPoolSize() {
            return maximumPoolSize;
        }

        public int minimumIdle() {
            return minimumIdle;
        }

        static StorageConfig fromMap(Map<String, Object> map) {
            StorageConfig c = new StorageConfig();
            if (map.get("url") instanceof String v) {
                c.url = v;
            }
            if (map.get("user") instanceof String v) {
                c.user = v;
            }
            if (map.get("password") instanceof String v) {
                c.password = v;
            }
            if (map.get("pool") instanceof Map<?, ?> pool) {
                Object max = pool.get("maximum-pool-size");
                Object min = pool.get("minimum-idle");
                if (max instanceof Number n) {
                    c.maximumPoolSize = n.intValue();
                }
                if (min instanceof Number n) {
                    c.minimumIdle = n.intValue();
                }
            }
            return c;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("url", url);
            map.put("user", user);
            map.put("password", password);
            Map<String, Object> pool = new LinkedHashMap<>();
            pool.put("maximum-pool-size", maximumPoolSize);
            pool.put("minimum-idle", minimumIdle);
            map.put("pool", pool);
            return map;
        }
    }

    public static final class CacheConfig {
        private int userTtlSeconds = 300;

        public int userTtlSeconds() {
            return userTtlSeconds;
        }

        static CacheConfig fromMap(Map<String, Object> map) {
            CacheConfig c = new CacheConfig();
            if (map.get("user-ttl-seconds") instanceof Number n) {
                c.userTtlSeconds = n.intValue();
            }
            return c;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("user-ttl-seconds", userTtlSeconds);
            return map;
        }
    }

    public static final class ServerSyncConfig {
        private boolean enabled;
        private String group = "aelion-main";
        private String redis = "redis://localhost:6379";
        private String channelPrefix = "aeperm:sync";
        private boolean syncUsers = true;
        private boolean syncGroups = true;

        public boolean enabled() {
            return enabled;
        }

        public String group() {
            return group;
        }

        public String redis() {
            return redis;
        }

        public String channelPrefix() {
            return channelPrefix;
        }

        public boolean syncUsers() {
            return syncUsers;
        }

        public boolean syncGroups() {
            return syncGroups;
        }

        static ServerSyncConfig fromMap(Map<String, Object> map) {
            ServerSyncConfig c = new ServerSyncConfig();
            if (map.get("enabled") instanceof Boolean b) {
                c.enabled = b;
            }
            if (map.get("group") instanceof String v) {
                c.group = v;
            }
            if (map.get("redis") instanceof String v) {
                c.redis = v;
            }
            if (map.get("channel-prefix") instanceof String v) {
                c.channelPrefix = v;
            }
            if (map.get("rules") instanceof Map<?, ?> rules) {
                if (rules.get("sync-users") instanceof Boolean b) {
                    c.syncUsers = b;
                }
                if (rules.get("sync-groups") instanceof Boolean b) {
                    c.syncGroups = b;
                }
            }
            return c;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("enabled", enabled);
            map.put("group", group);
            map.put("redis", redis);
            map.put("channel-prefix", channelPrefix);
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("sync-users", syncUsers);
            rules.put("sync-groups", syncGroups);
            map.put("rules", rules);
            return map;
        }
    }
}
