package sh.aelion.aeperm.common.service;

import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.common.AepermBootstrap;
import sh.aelion.aeperm.common.config.AepermConfig;
import sh.aelion.aeperm.common.sync.MemorySyncBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionServiceTest {

    private AepermBootstrap a;
    private AepermBootstrap b;
    private MemorySyncBus busA;
    private MemorySyncBus busB;

    @BeforeEach
    void setUp() {
        AepermConfig configA = new AepermConfig();
        configA.serverId("a");
        AepermConfig configB = new AepermConfig();
        configB.serverId("b");
        busA = new MemorySyncBus("a", true, true);
        busB = new MemorySyncBus("b", true, true);
        busA.link(busB);
        a = AepermBootstrap.createForTests(configA, busA, new StaticContextProvider("a"));
        b = AepermBootstrap.createForTests(configB, busB, new StaticContextProvider("b"));
    }

    @AfterEach
    void tearDown() {
        a.close();
        b.close();
    }

    @Test
    void mutatesUsersGroupsAndSyncs() {
        UUID uuid = UUID.randomUUID();
        a.permissions().createGroup("staff");
        a.permissions().groupAdd("staff", "staff.use", ContextSet.empty(), null);
        a.permissions().addToGroup(uuid, "staff", null);
        a.permissions().userAdd(uuid, "user.own", ContextSet.empty(), null);

        assertThat(a.permissions().has(uuid, "staff.use")).isTrue();
        assertThat(a.permissions().has(uuid, "user.own")).isTrue();

        AtomicInteger hits = new AtomicInteger();
        busB.onMessage(msg -> hits.incrementAndGet());
        a.permissions().userAdd(uuid, "sync.node", ContextSet.empty(), null);
        assertThat(hits.get()).isGreaterThan(0);
        assertThat(b.permissions().cache().userAny(uuid)).isEmpty();
    }

    @Test
    void reloadNetworkClearsPeerCacheWithoutEcho() {
        UUID uuid = UUID.randomUUID();
        b.permissions().userAdd(uuid, "keep.node", ContextSet.empty(), null);
        assertThat(b.permissions().has(uuid, "keep.node")).isTrue();
        AtomicInteger reloads = new AtomicInteger();
        busB.onMessage(msg -> {
            if (msg.type() == sh.aelion.aeperm.common.sync.SyncMessage.Type.RELOAD_ALL) {
                reloads.incrementAndGet();
            }
        });
        a.permissions().reloadNetwork();
        assertThat(reloads.get()).isEqualTo(1);
        assertThat(b.permissions().cache().userCount()).isZero();
        int after = reloads.get();
        a.permissions().reloadAll();
        assertThat(reloads.get()).isEqualTo(after);
    }

    @Test
    void historyLogsApiAndCommandActors() {
        sh.aelion.aeperm.common.history.ActingContext.run(
                sh.aelion.aeperm.common.history.Actor.command("Variiuz"),
                () -> a.permissions().createGroup("qa")
        );
        var commandRows = a.permissions().history("qa", 1);
        assertThat(commandRows).isNotEmpty();
        assertThat(commandRows.get(0).actor()).isEqualTo("Variiuz");
        assertThat(commandRows.get(0).source()).isEqualTo("command");
        a.permissions().createGroup("from-api");
        var apiRows = a.permissions().history("from-api", 1);
        assertThat(apiRows.get(0).source()).isEqualTo("api");
    }

    @Test
    void contextKeyedCacheSeparatesWorlds() {
        UUID uuid = UUID.randomUUID();
        ContextSet hub = ContextSet.builder().server("a").world("world").build();
        ContextSet nether = ContextSet.builder().server("a").world("nether").build();
        a.permissions().userAdd(uuid, "hub.only", hub, null);
        assertThat(a.permissions().has(uuid, "hub.only", hub)).isTrue();
        assertThat(a.permissions().has(uuid, "hub.only", nether)).isFalse();
        assertThat(a.permissions().cache().userCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void tempMembershipAndPrimaryGroup() {
        UUID uuid = UUID.randomUUID();
        a.permissions().createGroup("vip");
        a.permissions().addToGroup(uuid, "vip", Duration.ofHours(1));
        a.permissions().setPrimaryGroup(uuid, "vip");
        assertThat(a.permissions().user(uuid).orElseThrow().primaryGroup()).contains("vip");
        a.permissions().removeFromGroup(uuid, "vip");
        assertThat(a.permissions().user(uuid).orElseThrow().groups()).doesNotContain("vip");
    }

    @Test
    void rejectsDefaultDeleteAndCycles() {
        assertThatThrownBy(() -> a.permissions().deleteGroup("default"))
                .isInstanceOf(IllegalArgumentException.class);
        a.permissions().createGroup("a");
        a.permissions().createGroup("b");
        a.permissions().addParent("a", "b");
        assertThatThrownBy(() -> a.permissions().addParent("b", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeDecodeSyncMessages() {
        var msg = busA.decode(busA.encode(sh.aelion.aeperm.common.sync.SyncMessage.user("a", UUID.randomUUID())));
        assertThat(msg.type()).isEqualTo(sh.aelion.aeperm.common.sync.SyncMessage.Type.USER_INVALIDATE);
    }

    @Test
    void parentGroupChangeInvalidatesChildMembers() {
        UUID uuid = UUID.randomUUID();
        a.permissions().createGroup("staff");
        a.permissions().addParent("staff", "default");
        a.permissions().addToGroup(uuid, "staff", null);
        a.permissions().groupAdd("default", "basic.use", ContextSet.empty(), null);
        assertThat(a.permissions().has(uuid, "basic.use")).isTrue();

        a.permissions().groupRemove("default", "basic.use", ContextSet.empty());
        assertThat(a.permissions().has(uuid, "basic.use")).isFalse();
    }

    @Test
    void parentGroupChangeInvalidatesPeerChildMembers() {
        UUID uuid = UUID.randomUUID();
        for (AepermBootstrap boot : java.util.List.of(a, b)) {
            boot.permissions().createGroup("staff");
            boot.permissions().addParent("staff", "default");
            boot.permissions().addToGroup(uuid, "staff", null);
            boot.permissions().groupAdd("default", "basic.use", ContextSet.empty(), null);
        }
        assertThat(b.permissions().has(uuid, "basic.use")).isTrue();
        assertThat(b.permissions().cache().userAny(uuid)).isPresent();

        a.permissions().groupRemove("default", "basic.use", ContextSet.empty());
        assertThat(b.permissions().cache().userAny(uuid)).isEmpty();
    }

    @Test
    void tempMembershipExpiryDropsCacheBeforeUserTtl() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        AepermBootstrap boot = AepermBootstrap.createForTests(
                new AepermConfig(),
                new sh.aelion.aeperm.common.sync.NoopSyncBus(),
                new StaticContextProvider("s"),
                clock
        );
        try {
            UUID uuid = UUID.randomUUID();
            boot.permissions().createGroup("vip");
            boot.permissions().groupAdd("vip", "vip.perk", ContextSet.empty(), null);
            boot.permissions().addToGroup(uuid, "vip", Duration.ofSeconds(30));
            assertThat(boot.permissions().has(uuid, "vip.perk")).isTrue();

            clock.set(start.plusSeconds(31));
            assertThat(boot.permissions().has(uuid, "vip.perk")).isFalse();
        } finally {
            boot.close();
        }
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
        public java.time.ZoneOffset getZone() {
            return java.time.ZoneOffset.UTC;
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
