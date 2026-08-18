package sh.aelion.aeperm.common;

import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.common.config.AepermConfig;
import sh.aelion.aeperm.common.service.StaticContextProvider;
import sh.aelion.aeperm.common.sync.NoopSyncBus;
import sh.aelion.aeperm.common.sync.SyncMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class AepermBootstrapTest {

    @TempDir
    Path temp;

    @Test
    void loadsConfigAndStartsMemoryMode() throws Exception {
        AepermBootstrap bootstrap = AepermBootstrap.create(
                temp,
                Logger.getLogger("test"),
                new StaticContextProvider("local"),
                e -> {
                },
                e -> {
                },
                true
        );
        assertThat(Files.exists(temp.resolve("config.yml"))).isTrue();
        assertThat(bootstrap.permissions().groupNames()).contains("default");
        bootstrap.permissions().userAdd(UUID.randomUUID(), "x.y", ContextSet.empty(), null);
        bootstrap.close();
    }

    @Test
    void noopSyncIsSafe() {
        NoopSyncBus bus = new NoopSyncBus();
        bus.start();
        bus.onMessage(m -> {
        });
        bus.publishUserInvalidate(UUID.randomUUID());
        bus.publishGroupInvalidate("g");
        bus.publishReloadAll();
        bus.close();
        SyncMessage reload = SyncMessage.reload("origin");
        assertThat(reload.type()).isEqualTo(SyncMessage.Type.RELOAD_ALL);
    }

    @Test
    void createForTestsWorks() {
        AepermBootstrap bootstrap = AepermBootstrap.createForTests(
                new AepermConfig(),
                new NoopSyncBus(),
                new StaticContextProvider("s")
        );
        assertThat(bootstrap.config().serverId()).isEqualTo("server-1");
        bootstrap.close();
    }
}
