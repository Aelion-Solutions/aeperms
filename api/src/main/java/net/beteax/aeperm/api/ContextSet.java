package net.beteax.aeperm.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ContextSet {

    public static final String SERVER = "server";
    public static final String WORLD = "world";
    public static final String PROXY = "proxy";

    private final Map<String, String> values;

    private ContextSet(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static ContextSet empty() {
        return new ContextSet(Map.of());
    }

    public static ContextSet of(String key, String value) {
        return builder().with(key, value).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, String> asMap() {
        return values;
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean matches(ContextSet required) {
        if (required.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> entry : required.values.entrySet()) {
            String actual = values.get(entry.getKey());
            if (actual == null || !actual.equalsIgnoreCase(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContextSet that)) {
            return false;
        }
        return values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }

    public static final class Builder {
        private final Map<String, String> values = new LinkedHashMap<>();

        public Builder with(String key, String value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            values.put(key.toLowerCase(), value);
            return this;
        }

        public Builder server(String serverId) {
            return with(SERVER, serverId);
        }

        public Builder world(String world) {
            return with(WORLD, world);
        }

        public Builder proxy(String proxyId) {
            return with(PROXY, proxyId);
        }

        public ContextSet build() {
            return new ContextSet(new LinkedHashMap<>(values));
        }
    }
}
