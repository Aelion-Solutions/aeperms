package sh.aelion.aeperm.api.event;

import java.util.UUID;

public record PermissionChangedEvent(UUID uuid, String source) {
}
