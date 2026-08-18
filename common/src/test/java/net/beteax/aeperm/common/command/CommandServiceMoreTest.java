package net.beteax.aeperm.common.command;

import net.beteax.aeperm.common.AepermBootstrap;
import net.beteax.aeperm.common.config.AepermConfig;
import net.beteax.aeperm.common.service.StaticContextProvider;
import net.beteax.aeperm.common.sync.NoopSyncBus;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommandServiceMoreTest {

    private AepermBootstrap bootstrap;
    private CommandService commands;
    private final List<Component> messages = new ArrayList<>();
    private Audience audience;

    @BeforeEach
    void setUp() {
        bootstrap = AepermBootstrap.createForTests(new AepermConfig(), new NoopSyncBus(), new StaticContextProvider("s1"));
        commands = new CommandService(bootstrap.permissions(), name -> Optional.empty());
        audience = new Audience() {
            @Override
            public void sendMessage(Component message) {
                messages.add(message);
            }
        };
    }

    @AfterEach
    void tearDown() {
        bootstrap.close();
    }

    @Test
    void coversErrorAndMutationPaths() {
        commands.handle(audience, new String[]{});
        commands.handle(audience, new String[]{"nope"});
        commands.handle(audience, new String[]{"user"});
        commands.handle(audience, new String[]{"user", "missing", "info"});
        UUID uuid = UUID.randomUUID();
        commands.handle(audience, new String[]{"user", uuid.toString(), "permission", "set", "-deny.node"});
        commands.handle(audience, new String[]{"user", uuid.toString(), "permission", "unset", "deny.node"});
        commands.handle(audience, new String[]{"group", "create", "mods"});
        commands.handle(audience, new String[]{"group", "mods", "parent", "add", "default"});
        commands.handle(audience, new String[]{"group", "mods", "parent", "remove", "default"});
        commands.handle(audience, new String[]{"group", "mods", "weight", "20"});
        commands.handle(audience, new String[]{"group", "mods", "info"});
        commands.handle(audience, new String[]{"group", "list"});
        commands.handle(audience, new String[]{"info"});
        commands.handle(audience, new String[]{"group", "mods", "permission", "unset", "x"});
        commands.handle(audience, new String[]{"group", "delete", "mods"});
        commands.handle(audience, new String[]{"group", "delete", "default"});
        commands.handle(audience, new String[]{"sync", "reload"});
        commands.handle(audience, new String[]{"cache", "wat"});
        commands.handle(audience, new String[]{"sync", "wat"});
        assertThat(messages).isNotEmpty();
    }
}
