package sh.aelion.aeperm.common.cache;

import sh.aelion.aeperm.api.CalculatedUser;
import sh.aelion.aeperm.common.model.GroupData;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCacheTest {

    @Test
    void ttlAndInvalidate() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        LocalCache cache = new LocalCache(clock, Duration.ofSeconds(5));
        UUID uuid = UUID.randomUUID();
        CalculatedUser user = new CalculatedUser(uuid, "A", "default", Set.of("default", "staff"), Map.of());
        cache.putUser(user);
        cache.putGroup(new GroupData("staff"));

        assertThat(cache.user(uuid, sh.aelion.aeperm.api.ContextSet.empty())).isPresent();
        clock.set(start.plusSeconds(6));
        assertThat(cache.user(uuid, sh.aelion.aeperm.api.ContextSet.empty())).isEmpty();

        cache.putUser(user);
        cache.invalidateUsersInGroup("staff");
        assertThat(cache.userAny(uuid)).isEmpty();
        cache.clear();
        assertThat(cache.groupCount()).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
