package net.beteax.aeperm.common.command;

import net.beteax.aeperm.common.AepermBootstrap;
import net.beteax.aeperm.common.command.subcommand.CommandMeta;
import net.beteax.aeperm.common.config.AepermConfig;
import net.beteax.aeperm.common.service.StaticContextProvider;
import net.beteax.aeperm.common.sync.NoopSyncBus;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommandServiceTest {

    private AepermBootstrap bootstrap;
    private CommandService commands;
    private final List<Component> messages = new ArrayList<>();
    private Audience audience;

    @BeforeEach
    void setUp() {
        bootstrap = AepermBootstrap.createForTests(new AepermConfig(), new NoopSyncBus(), new StaticContextProvider("s1"));
        commands = new CommandService(
                bootstrap.permissions(),
                name -> Optional.empty(),
                () -> List.of("Variiuz", "Steve"),
                CommandMeta.of("1.0-TEST", false)
        );
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
    void handlesUserGroupCacheSync() {
        UUID uuid = UUID.randomUUID();
        bootstrap.permissions().updateUserName(uuid, "Alex");

        commands.handle(audience, new String[]{"group", "create", "staff"});
        commands.handle(audience, new String[]{"group", "staff", "permission", "set", "staff.use"});
        commands.handle(audience, new String[]{"user", uuid.toString(), "group", "add", "staff"});
        commands.handle(audience, new String[]{"user", uuid.toString(), "permission", "set", "own.node"});
        commands.handle(audience, new String[]{"user", uuid.toString(), "check", "staff.use"});
        commands.handle(audience, new String[]{"user", uuid.toString(), "info"});
        commands.handle(audience, new String[]{"cache", "stats"});
        commands.handle(audience, new String[]{"sync", "status"});
        commands.handle(audience, new String[]{"cache", "clear"});

        assertThat(bootstrap.permissions().has(uuid, "staff.use")).isTrue();
        assertThat(bootstrap.permissions().has(uuid, "own.node")).isTrue();
        assertThat(messages).isNotEmpty();
        assertThat(commands.suggestRoot("c")).contains("cache");
        assertThat(commands.suggestGroups("sta")).contains("staff");
    }

    @Test
    void suggestCompletesCurrentTokenOnly() {
        bootstrap.permissions().createGroup("admin");
        bootstrap.permissions().updateUserName(UUID.randomUUID(), "Variiuz");

        assertThat(commands.suggest(new String[]{"user", "Var"}))
                .contains("Variiuz")
                .doesNotContain("user Variiuz");
        assertThat(commands.suggest(new String[]{"group", "ad"}))
                .contains("admin")
                .doesNotContain("group admin");
        assertThat(commands.suggest(new String[]{"group", "li"}))
                .contains("list");
        assertThat(commands.suggest(new String[]{"user", "Variiuz", "per"}))
                .contains("permission")
                .doesNotContain("user Variiuz permission");
        assertThat(commands.suggest(new String[]{"group", "admin", "par"}))
                .contains("parent")
                .doesNotContain("group admin parent");
        assertThat(commands.suggest(new String[]{"u"}))
                .contains("user");
        assertThat(commands.suggest(new String[]{"group", ""}))
                .contains("list", "create", "delete", "admin");
    }

    @Test
    void infoCardContainsVersionAndAuthor() {
        commands.handle(audience, new String[]{"info"});
        String joined = messages.stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(joined).contains("Plugin Info");
        assertThat(joined).contains("1.0-TEST");
        assertThat(joined).contains("Variiuz");
        assertThat(joined).contains("beteax.net");
        assertThat(joined).contains("standalone");
    }

    @Test
    void groupListShowsCreatedGroups() {
        commands.handle(audience, new String[]{"group", "create", "staff"});
        commands.handle(audience, new String[]{"group", "staff", "weight", "50"});
        messages.clear();
        commands.handle(audience, new String[]{"group", "list"});
        String joined = messages.stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(joined).contains("Groups");
        assertThat(joined).contains("staff");
        assertThat(joined).contains("weight=50");
    }
}
