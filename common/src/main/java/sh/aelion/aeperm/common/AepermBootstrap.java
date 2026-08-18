package sh.aelion.aeperm.common;

import lombok.Getter;
import sh.aelion.aeperm.api.event.GroupChangedEvent;
import sh.aelion.aeperm.api.event.PermissionChangedEvent;
import sh.aelion.aeperm.common.cache.LocalCache;
import sh.aelion.aeperm.common.calc.PermissionCalculator;
import sh.aelion.aeperm.common.config.AepermConfig;
import sh.aelion.aeperm.common.service.ContextProvider;
import sh.aelion.aeperm.common.service.PermissionService;
import sh.aelion.aeperm.common.service.StaticContextProvider;
import sh.aelion.aeperm.common.storage.SqlStorage;
import sh.aelion.aeperm.common.storage.MemoryStorage;
import sh.aelion.aeperm.common.storage.Storage;
import sh.aelion.aeperm.common.sync.NoopSyncBus;
import sh.aelion.aeperm.common.sync.RedisSyncBus;
import sh.aelion.aeperm.common.sync.SyncBus;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class AepermBootstrap implements AutoCloseable {

    @Getter
    private final AepermConfig config;
    private final Storage storage;
    @Getter
    private final SyncBus syncBus;
    @Getter
    private final PermissionService permissions;
    private final Executor async;
    @Getter
    private final Logger logger;

    private AepermBootstrap(
            AepermConfig config,
            Storage storage,
            SyncBus syncBus,
            PermissionService permissions,
            Executor async,
            Logger logger
    ) {
        this.config = config;
        this.storage = storage;
        this.syncBus = syncBus;
        this.permissions = permissions;
        this.async = async;
        this.logger = logger;
    }

    public static AepermBootstrap create(
            Path dataFolder,
            Logger logger,
            ContextProvider contextProvider,
            Consumer<PermissionChangedEvent> permissionListener,
            Consumer<GroupChangedEvent> groupListener,
            boolean memoryOnly
    ) throws IOException {
        return create(dataFolder, logger, contextProvider, permissionListener, groupListener, src -> {
        }, memoryOnly);
    }

    public static AepermBootstrap create(
            Path dataFolder,
            Logger logger,
            ContextProvider contextProvider,
            Consumer<PermissionChangedEvent> permissionListener,
            Consumer<GroupChangedEvent> groupListener,
            Consumer<String> reloadListener,
            boolean memoryOnly
    ) throws IOException {
        Files.createDirectories(dataFolder);
        Path configFile = dataFolder.resolve("config.yml");
        AepermConfig config = loadOrCreateConfig(configFile);

        Storage storage = memoryOnly
                ? new MemoryStorage()
                : new SqlStorage(config.storage());
        storage.init();

        SyncBus syncBus;
        if (config.serversync().enabled()) {
            syncBus = new RedisSyncBus(config.serverId(), config.serversync(), logger);
            syncBus.start();
            logger.info("ServerSync enabled group=" + config.serversync().group());
        } else {
            syncBus = new NoopSyncBus();
            syncBus.start();
        }

        Clock clock = Clock.systemUTC();
        LocalCache cache = new LocalCache(clock, Duration.ofSeconds(config.cache().userTtlSeconds()));
        PermissionCalculator calculator = new PermissionCalculator(clock);
        Executor async = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "aep-async");
            t.setDaemon(true);
            return t;
        });

        ContextProvider contexts = contextProvider == null
                ? new StaticContextProvider(config.serverId())
                : contextProvider;

        PermissionService service = new PermissionService(
                storage,
                cache,
                calculator,
                syncBus,
                contexts,
                clock,
                async,
                permissionListener,
                groupListener,
                reloadListener
        );
        service.warmGroups();

        logger.info("AePerm ready server-id=" + config.serverId()
                + " groups=" + service.groupNames().size()
                + " cacheUsers=" + cache.userCount());

        return new AepermBootstrap(config, storage, syncBus, service, async, logger);
    }

    /**
     * Used in test suite
     * @param config AEP Config
     * @param syncBus SyncBus instance
     * @param contexts ContextProvider instance
     * @return AepermBootstrap instance
     */
    public static AepermBootstrap createForTests(AepermConfig config, SyncBus syncBus, ContextProvider contexts) {
        Storage storage = new MemoryStorage();
        storage.init();
        Clock clock = Clock.systemUTC();
        LocalCache cache = new LocalCache(clock, Duration.ofSeconds(config.cache().userTtlSeconds()));
        PermissionCalculator calculator = new PermissionCalculator(clock);
        Executor async = Runnable::run;
        PermissionService service = new PermissionService(
                storage,
                cache,
                calculator,
                syncBus,
                contexts,
                clock,
                async,
                e -> {
                },
                e -> {
                }
        );
        service.warmGroups();
        return new AepermBootstrap(config, storage, syncBus, service, async, Logger.getLogger("aeperm-test"));
    }

    @SuppressWarnings("unchecked")
    private static AepermConfig loadOrCreateConfig(Path configFile) throws IOException {
        Yaml yaml = yaml();
        if (!Files.exists(configFile)) {
            AepermConfig defaults = new AepermConfig();
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                yaml.dump(defaults.toMap(), writer);
            }
            return defaults;
        }
        try (InputStream in = Files.newInputStream(configFile)) {
            Object loaded = yaml.load(in);
            if (loaded instanceof Map<?, ?> map) {
                return AepermConfig.fromMap((Map<String, Object>) map);
            }
        }
        return new AepermConfig();
    }

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }



    @Override
    public void close() {
        syncBus.close();
        storage.close();
        if (async instanceof java.util.concurrent.ExecutorService service) {
            service.shutdownNow();
        } else if (async instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
