package net.beteax.aeperm.common.sync;

import java.util.UUID;

public record SyncMessage(
        Type type,
        String originServerId,
        UUID userId,
        String groupName
) {
    public enum Type {
        USER_INVALIDATE,
        GROUP_INVALIDATE,
        RELOAD_ALL
    }

    public static SyncMessage user(String origin, UUID uuid) {
        return new SyncMessage(Type.USER_INVALIDATE, origin, uuid, null);
    }

    public static SyncMessage group(String origin, String group) {
        return new SyncMessage(Type.GROUP_INVALIDATE, origin, null, group);
    }

    public static SyncMessage reload(String origin) {
        return new SyncMessage(Type.RELOAD_ALL, origin, null, null);
    }
}
