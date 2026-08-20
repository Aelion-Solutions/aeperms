package sh.aelion.aeperm.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class Wildcard {

    private Wildcard() {
    }

    /**
     * Exact node, then the most specific matching wildcard, including {@code *}.
     */
    public static Optional<Boolean> lookup(Map<String, Boolean> permissions, String node) {
        String normalized = normalize(node);
        Boolean exact = permissions.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }
        return resolve(permissions, normalized);
    }

    /**
     * {@code true} when an exact or wildcard node allows {@code node}.
     * Missing nodes are {@code false}, same as a deny.
     */
    public static boolean match(Map<String, Boolean> permissions, String node) {
        return lookup(permissions, node).orElse(false);
    }

    /**
     * Copies {@code permissions} and applies matching wildcards onto {@code known} node names.
     * Used when the platform only understands exact Superperms keys.
     */
    public static Map<String, Boolean> expand(Map<String, Boolean> permissions, Iterable<String> known) {
        Map<String, Boolean> out = new LinkedHashMap<>(permissions);
        for (String raw : known) {
            String node = normalize(raw);
            if (out.containsKey(node)) {
                continue;
            }
            resolve(permissions, node).ifPresent(value -> out.put(node, value));
        }
        return out;
    }

    /**
     * Wildcard-only match. {@code node} must already be normalized.
     * More specific prefixes win over {@code *}.
     */
    public static Optional<Boolean> resolve(Map<String, Boolean> permissions, String node) {
        Boolean self = permissions.get(node + ".*");
        if (self != null) {
            return Optional.of(self);
        }
        String current = node;
        while (true) {
            int idx = current.lastIndexOf('.');
            if (idx < 0) {
                break;
            }
            current = current.substring(0, idx);
            Boolean value = permissions.get(current + ".*");
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.ofNullable(permissions.get("*"));
    }

    public static String normalize(String raw) {
        String node = raw.trim().toLowerCase();
        if (node.startsWith("-")) {
            return node.substring(1);
        }
        return node;
    }

    public static boolean isNegated(String raw) {
        return raw.trim().startsWith("-");
    }
}
