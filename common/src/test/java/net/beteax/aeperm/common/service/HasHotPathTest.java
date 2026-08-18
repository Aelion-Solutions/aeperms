package net.beteax.aeperm.common.service;

import net.beteax.aeperm.api.ContextSet;
import net.beteax.aeperm.common.AepermBootstrap;
import net.beteax.aeperm.common.config.AepermConfig;
import net.beteax.aeperm.common.sync.NoopSyncBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HasHotPathTest {

    private AepermBootstrap bootstrap;

    @AfterEach
    void tearDown() {
        if (bootstrap != null) {
            bootstrap.close();
        }
    }

    @Test
    void cacheHitHasIsCheapAndDoesNotGrowCache() {
        bootstrap = AepermBootstrap.createForTests(new AepermConfig(), new NoopSyncBus(), new StaticContextProvider("s1"));
        UUID uuid = UUID.randomUUID();
        bootstrap.permissions().userAdd(uuid, "hot.node", ContextSet.empty(), null);
        assertThat(bootstrap.permissions().has(uuid, "hot.node")).isTrue();
        int size = bootstrap.permissions().cache().userCount();
        long start = System.nanoTime();
        for (int i = 0; i < 25_000; i++) {
            bootstrap.permissions().has(uuid, "hot.node");
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(bootstrap.permissions().cache().userCount()).isEqualTo(size);
        assertThat(elapsedMs).isLessThan(2_000L);
    }
}
