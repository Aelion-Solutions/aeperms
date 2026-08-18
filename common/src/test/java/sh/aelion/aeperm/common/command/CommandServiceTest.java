package sh.aelion.aeperm.common.command;

import sh.aelion.aeperm.common.AepermBootstrap;
import sh.aelion.aeperm.common.command.subcommand.CommandMeta;
import sh.aelion.aeperm.common.config.AepermConfig;
import sh.aelion.aeperm.common.service.StaticContextProvider;
import sh.aelion.aeperm.common.sync.NoopSyncBus;
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
                .contains("permission", "permissions")
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
    void userSuggestEmptyTokenIsOnlineOnly() {
        bootstrap.permissions().updateUserName(UUID.randomUUID(), "OfflineOnly");
        List<String> empty = commands.suggest(new String[]{"user", ""});
        assertThat(empty).contains("Variiuz", "Steve").doesNotContain("OfflineOnly");
        assertThat(commands.suggest(new String[]{"user", "Off"})).contains("OfflineOnly");
    }

    @Test
    void bareUserAndGroupShowInfo() {
        UUID uuid = UUID.randomUUID();
        bootstrap.permissions().updateUserName(uuid, "Alex");
        commands.handle(audience, new String[]{"group", "create", "staff"});
        commands.handle(audience, new String[]{"user", uuid.toString(), "group", "add", "staff"});
        messages.clear();

        commands.handle(audience, new String[]{"user", uuid.toString()});
        String userInfo = plain();
        assertThat(userInfo).contains("Name").contains("Alex").contains("UUID").contains("permission");

        messages.clear();
        commands.handle(audience, new String[]{"group", "staff"});
        String groupInfo = plain();
        assertThat(groupInfo).contains("Name").contains("staff").contains("Weight").contains("permission");
    }

    @Test
    void permissionsCommandPaginates() {
        UUID uuid = UUID.randomUUID();
        bootstrap.permissions().updateUserName(uuid, "Alex");
        for (int i = 0; i < 10; i++) {
            bootstrap.permissions().userAdd(uuid, "node." + i, sh.aelion.aeperm.api.ContextSet.empty(), null);
        }
        messages.clear();
        commands.handle(audience, new String[]{"user", "Alex", "permissions"});
        String page1 = plain();
        assertThat(page1).contains("Permissions").contains("Page 1/2").contains("+");

        messages.clear();
        commands.handle(audience, new String[]{"user", "Alex", "permissions", "2"});
        assertThat(plain()).contains("Page 2/2");
    }

    @Test
    void infoLineContainsVersionAndAuthor() {
        commands.handle(audience, new String[]{"info"});
        String joined = plain();
        assertThat(joined).contains("AePerm");
        assertThat(joined).contains("1.0-TEST");
        assertThat(joined).contains("Variiuz");
        assertThat(joined).contains("standalone");
    }

    @Test
    void groupListShowsCreatedGroups() {
        commands.handle(audience, new String[]{"group", "create", "staff"});
        commands.handle(audience, new String[]{"group", "staff", "weight", "50"});
        messages.clear();
        commands.handle(audience, new String[]{"group", "list"});
        String joined = plain();
        assertThat(joined).contains("Groups");
        assertThat(joined).contains("staff");
        assertThat(joined).contains("weight=50");
    }

    private String plain() {
        return messages.stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .reduce("", (a, b) -> a + "\n" + b);
    }
}
