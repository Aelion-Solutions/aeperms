package net.beteax.aeperm.common.calc;

import net.beteax.aeperm.api.CalculatedUser;
import net.beteax.aeperm.api.ContextSet;
import net.beteax.aeperm.api.PermissionNode;
import net.beteax.aeperm.common.model.GroupData;
import net.beteax.aeperm.common.model.UserData;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
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
}
