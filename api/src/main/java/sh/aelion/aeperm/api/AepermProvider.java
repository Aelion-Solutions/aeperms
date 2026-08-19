package sh.aelion.aeperm.api;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide lookup for the running {@link AepermAPI}.
 */
public final class AepermProvider {

    private static final AtomicReference<AepermAPI> INSTANCE = new AtomicReference<>();

    private AepermProvider() {
    }

    public static AepermAPI get() {
        AepermAPI api = INSTANCE.get();
        if (api == null) {
            throw new IllegalStateException("AePerm is not loaded");
        }
        return api;
    }

    public static Optional<AepermAPI> getIfPresent() {
        return Optional.ofNullable(INSTANCE.get());
    }

    public static void register(AepermAPI api) {
        Objects.requireNonNull(api, "api");
        if (!INSTANCE.compareAndSet(null, api)) {
            throw new IllegalStateException("AePerm API is already registered");
        }
    }

    public static void unregister() {
        INSTANCE.set(null);
    }
}
