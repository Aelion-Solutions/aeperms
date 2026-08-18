package net.beteax.aeperm.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiModelTest {

    @Test
    void contextMatching() {
        ContextSet active = ContextSet.builder().server("s1").world("world").build();
        assertTrue(active.matches(ContextSet.empty()));
        assertTrue(active.matches(ContextSet.of("server", "s1")));
        assertFalse(active.matches(ContextSet.of("server", "s2")));
        assertEquals("world", active.get(ContextSet.WORLD).orElseThrow());
    }

    @Test
    void permissionNodeExpiry() {
        PermissionNode node = new PermissionNode("a.b", true, ContextSet.empty(), Instant.parse("2020-01-01T00:00:00Z"));
        assertTrue(node.expired(Instant.parse("2021-01-01T00:00:00Z")));
        assertFalse(PermissionNode.allow("x").expired(Instant.now()));
        assertFalse(PermissionNode.deny("x").value());
    }

    @Test
    void calculatedUserHas() {
        CalculatedUser user = new CalculatedUser(
                UUID.randomUUID(),
                "Steve",
                "default",
                Set.of("default"),
                Map.of("demo.use", true, "demo.*", true)
        );
        assertTrue(user.has("demo.use"));
        assertTrue(user.has("demo.other"));
        assertEquals("Steve", user.name().orElseThrow());
    }

    @Test
    void calculatedGroup() {
        CalculatedGroup group = new CalculatedGroup("mod", 10, Set.of("default"), Map.of("mod.use", true));
        assertEquals("mod", group.name());
        assertEquals(10, group.weight());
        assertTrue(group.parents().contains("default"));
    }
}
