package sh.aelion.aeperm.common.history;

import java.time.Instant;

public record HistoryRecord(
        Instant at,
        String actor,
        String source,
        String action,
        String target,
        String detail
) {
}
