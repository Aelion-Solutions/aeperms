package sh.aelion.sql;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

public final class NamedCache {

    private final Cache<Object, Object> cache;

    NamedCache(Duration ttl) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Objects.requireNonNull(ttl, "ttl"))
                .build();
    }

    @SuppressWarnings("unchecked")
    public <K, V> V get(K key, Supplier<V> loader) {
        return (V) cache.get(key, ignored -> loader.get());
    }

    public void invalidate(Object key) {
        cache.invalidate(key);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}
