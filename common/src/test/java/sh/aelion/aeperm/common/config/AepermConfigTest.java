package sh.aelion.aeperm.common.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AepermConfigTest {

    @Test
    void roundTrip() {
        AepermConfig config = new AepermConfig();
        config.serverId("hub-1");
        Map<String, Object> map = config.toMap();
        AepermConfig loaded = AepermConfig.fromMap(map);
        assertThat(loaded.serverId()).isEqualTo("hub-1");
        assertThat(loaded.storage().maximumPoolSize()).isEqualTo(10);
        assertThat(loaded.serversync().enabled()).isFalse();
        assertThat(loaded.cache().userTtlSeconds()).isEqualTo(300);
    }
}
