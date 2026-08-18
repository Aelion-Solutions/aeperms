package net.beteax.aeperm.common;

import net.beteax.aeperm.api.event.GroupChangedEvent;
import net.beteax.aeperm.api.event.PermissionChangedEvent;
import net.beteax.aeperm.common.cache.LocalCache;
import net.beteax.aeperm.common.calc.PermissionCalculator;
import net.beteax.aeperm.common.config.AepermConfig;
import net.beteax.aeperm.common.service.ContextProvider;
import net.beteax.aeperm.common.service.PermissionService;
import net.beteax.aeperm.common.service.StaticContextProvider;
import net.beteax.aeperm.common.storage.EbeanStorage;
import net.beteax.aeperm.common.storage.MemoryStorage;
import net.beteax.aeperm.common.storage.Storage;
import net.beteax.aeperm.common.sync.NoopSyncBus;
import net.beteax.aeperm.common.sync.RedisSyncBus;
import net.beteax.aeperm.common.sync.SyncBus;
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

    private final AepermConfig config;
    private final Storage storage;
    private final SyncBus syncBus;
    private final PermissionService permissions;
    private final Executor async;
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
                : new EbeanStorage(config.storage());
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
            Thread t = new Thread(r, "aeperm-async");
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

    public AepermConfig config() {
        return config;
    }

    public PermissionService permissions() {
        return permissions;
    }

    public SyncBus syncBus() {
        return syncBus;
    }

    public Logger logger() {
        return logger;
    }

    @Override
    public void close() {
        syncBus.close();
        storage.close();
        if (async instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        } else if (async instanceof java.util.concurrent.ExecutorService service) {
            service.shutdownNow();
        }
    }
}
