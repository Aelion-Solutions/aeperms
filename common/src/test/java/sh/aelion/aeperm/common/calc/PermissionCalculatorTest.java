package sh.aelion.aeperm.common.calc;

import sh.aelion.aeperm.api.CalculatedUser;
import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.api.PermissionNode;
import sh.aelion.aeperm.common.model.GroupData;
import sh.aelion.aeperm.common.model.UserData;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCalculatorTest {

    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final PermissionCalculator calculator = new PermissionCalculator(Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void userOverridesGroupAndSupportsWildcardsNegationContextsExpiry() {
        GroupData staff = new GroupData("staff");
        staff.weight(10);
        staff.nodes().add(PermissionNode.allow("chat.color"));
        staff.nodes().add(PermissionNode.allow("mod.kick"));
        staff.nodes().add(new PermissionNode("server.only", true, ContextSet.of("server", "hub"), null));

        GroupData defaultGroup = new GroupData("default");
        defaultGroup.nodes().add(PermissionNode.allow("basic.use"));

        staff.parents().add("default");

        UserData user = new UserData(UUID.randomUUID());
        user.groups().add("staff");
        user.nodes().add(PermissionNode.deny("mod.kick"));
        user.nodes().add(PermissionNode.allow("vip.*"));
        user.nodes().add(new PermissionNode("temp.fly", true, ContextSet.empty(), Instant.parse("2025-01-01T00:00:00Z")));

        Map<String, GroupData> groups = new HashMap<>();
        groups.put("staff", staff);
        groups.put("default", defaultGroup);

        CalculatedUser calculated = calculator.calculateUser(user, groups, ContextSet.of("server", "hub"));
        assertThat(calculator.check(calculated.permissions(), "basic.use")).isTrue();
        assertThat(calculator.check(calculated.permissions(), "chat.color")).isTrue();
        assertThat(calculator.check(calculated.permissions(), "mod.kick")).isFalse();
        assertThat(calculator.check(calculated.permissions(), "vip.fly")).isTrue();
        assertThat(calculator.check(calculated.permissions(), "server.only")).isTrue();
        assertThat(calculator.check(calculated.permissions(), "temp.fly")).isFalse();

        CalculatedUser elsewhere = calculator.calculateUser(user, groups, ContextSet.of("server", "survival"));
        assertThat(calculator.check(elsewhere.permissions(), "server.only")).isFalse();
    }

    @Test
    void starAndNamespaceWildcardsGrantVanillaCommandNodes() {
        UserData user = new UserData(UUID.randomUUID());
        user.groups().add("default");
        user.nodes().add(PermissionNode.allow("*"));

        GroupData defaultGroup = new GroupData("default");
        Map<String, GroupData> groups = Map.of("default", defaultGroup);

        CalculatedUser star = calculator.calculateUser(user, groups, ContextSet.empty());
        assertThat(calculator.check(star.permissions(), "minecraft.command.gamemode")).isTrue();

        UserData namespaced = new UserData(UUID.randomUUID());
        namespaced.groups().add("default");
        namespaced.nodes().add(PermissionNode.allow("minecraft.*"));
        namespaced.nodes().add(PermissionNode.deny("minecraft.command.ban"));
        CalculatedUser minecraft = calculator.calculateUser(namespaced, groups, ContextSet.empty());
        assertThat(calculator.check(minecraft.permissions(), "minecraft.command.gamemode")).isTrue();
        assertThat(calculator.check(minecraft.permissions(), "minecraft.command.ban")).isFalse();
        assertThat(calculator.check(minecraft.permissions(), "bukkit.command.plugins")).isFalse();
    }

    @Test
    void detectsParentCycles() {
        GroupData a = new GroupData("a");
        GroupData b = new GroupData("b");
        a.parents().add("b");
        Map<String, GroupData> groups = Map.of("a", a, "b", b);
        assertThat(PermissionCalculator.wouldCreateCycle("b", "a", groups)).isTrue();
        assertThat(PermissionCalculator.wouldCreateCycle("b", "c", groups)).isFalse();
    }

    @Test
    void defaultGroupWhenEmpty() {
        UserData user = new UserData(UUID.randomUUID());
        CalculatedUser calculated = calculator.calculateUser(user, Map.of(), ContextSet.empty());
        assertThat(calculated.groups()).contains(PermissionCalculator.DEFAULT_GROUP);
    }

    @Test
    void groupsInheritingIncludesDescendants() {
        GroupData defaults = new GroupData("default");
        GroupData staff = new GroupData("staff");
        staff.parents().add("default");
        GroupData admin = new GroupData("admin");
        admin.parents().add("staff");
        Map<String, GroupData> groups = new HashMap<>();
        groups.put("default", defaults);
        groups.put("staff", staff);
        groups.put("admin", admin);

        assertThat(PermissionCalculator.groupsInheriting("default", groups))
                .containsExactlyInAnyOrder("default", "staff", "admin");
        assertThat(PermissionCalculator.groupsInheriting("staff", groups))
                .containsExactlyInAnyOrder("staff", "admin");
        assertThat(PermissionCalculator.userInGroups(Set.of("staff"), PermissionCalculator.groupsInheriting("default", groups)))
                .isTrue();
    }
}
