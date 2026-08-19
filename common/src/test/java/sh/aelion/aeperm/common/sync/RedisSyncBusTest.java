package sh.aelion.aeperm.common.sync;

import sh.aelion.aeperm.common.config.AepermConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RedisSyncBusTest {

    @Test
    void malformedPayloadDoesNotThrowOrDeliver() {
        RedisSyncBus bus = new RedisSyncBus("a", new AepermConfig().serversync(), Logger.getLogger("aeperm-test"));
        AtomicInteger hits = new AtomicInteger();
        bus.onMessage(msg -> hits.incrementAndGet());

        assertThatCode(() -> bus.handleIncoming("not-json{")).doesNotThrowAnyException();
        assertThatCode(() -> bus.handleIncoming("{")).doesNotThrowAnyException();
        assertThatCode(() -> bus.handleIncoming("{}")).doesNotThrowAnyException();
        assertThat(hits.get()).isZero();
    }

    @Test
    void validPeerPayloadIsDelivered() {
        RedisSyncBus bus = new RedisSyncBus("a", new AepermConfig().serversync(), Logger.getLogger("aeperm-test"));
        AtomicInteger hits = new AtomicInteger();
        bus.onMessage(msg -> hits.incrementAndGet());

        MemorySyncBus encoder = new MemorySyncBus("b", true, true);
        UUID uuid = UUID.randomUUID();
        bus.handleIncoming(encoder.encode(SyncMessage.user("b", uuid)));
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void originEchoIsIgnored() {
        RedisSyncBus bus = new RedisSyncBus("a", new AepermConfig().serversync(), Logger.getLogger("aeperm-test"));
        AtomicInteger hits = new AtomicInteger();
        bus.onMessage(msg -> hits.incrementAndGet());

        MemorySyncBus encoder = new MemorySyncBus("a", true, true);
        bus.handleIncoming(encoder.encode(SyncMessage.reload("a")));
        assertThat(hits.get()).isZero();
    }
}
