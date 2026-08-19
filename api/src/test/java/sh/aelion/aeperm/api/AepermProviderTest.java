package sh.aelion.aeperm.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AepermProviderTest {

    @AfterEach
    void tearDown() {
        AepermProvider.unregister();
    }

    @Test
    void registerGetAndUnregister() {
        AepermAPI api = stub();
        assertTrue(AepermProvider.getIfPresent().isEmpty());
        assertThrows(IllegalStateException.class, AepermProvider::get);

        AepermProvider.register(api);
        assertSame(api, AepermProvider.get());
        assertSame(api, AepermProvider.getIfPresent().orElseThrow());
        assertThrows(IllegalStateException.class, () -> AepermProvider.register(stub()));

        AepermProvider.unregister();
        assertTrue(AepermProvider.getIfPresent().isEmpty());
    }

    private static AepermAPI stub() {
        return (AepermAPI) Proxy.newProxyInstance(
                AepermAPI.class.getClassLoader(),
                new Class<?>[]{AepermAPI.class},
                (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) {
                        return false;
                    }
                    if (type == void.class) {
                        return null;
                    }
                    return null;
                }
        );
    }
}
