package net.beteax.aeperm.api;

import java.util.Map;

public final class Wildcard {

    private Wildcard() {
    }

    public static boolean match(Map<String, Boolean> permissions, String node) {
        Boolean star = permissions.get("*");
        if (star != null) {
            return star;
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
                return value;
            }
        }
        return false;
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
