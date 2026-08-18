package sh.aelion.aeperm.api;

import sh.aelion.aeperm.api.event.GroupChangedEvent;
import sh.aelion.aeperm.api.event.PermissionChangedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCoverageTest {

    @Test
    void contextSetBuildersAndEquality() {
        ContextSet a = ContextSet.builder().server("s1").world("w").proxy("p").build();
        ContextSet b = ContextSet.builder().server("s1").world("w").proxy("p").build();
        ContextSet c = ContextSet.empty();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertFalse(a.isEmpty());
        assertTrue(c.isEmpty());
        assertTrue(a.toString().contains("server"));
        assertEquals(Map.of("server", "s1", "world", "w", "proxy", "p"), a.asMap());
    }

    @Test
    void permissionNodeEqualityAndFactories() {
        PermissionNode allow = PermissionNode.allow("a.b");
        PermissionNode deny = PermissionNode.deny("a.b");
        PermissionNode timed = new PermissionNode("a.b", true, ContextSet.of("server", "x"), Instant.EPOCH);
        assertEquals(allow, new PermissionNode("a.b", true, ContextSet.empty(), null));
        assertEquals(allow.hashCode(), new PermissionNode("A.B", true, ContextSet.empty(), null).hashCode());
        assertNotEquals(allow, deny);
        assertTrue(timed.expiry().isPresent());
        assertEquals("a.b", allow.permission());
        assertTrue(allow.value());
        assertFalse(deny.value());
    }

    @Test
    void calculatedTypesAndEvents() {
        UUID id = UUID.randomUUID();
        CalculatedUser user = new CalculatedUser(id, null, null, Set.of(), Map.of("*", true));
        assertTrue(user.has("anything"));
        assertTrue(user.name().isEmpty());
        assertTrue(user.primaryGroup().isEmpty());
        assertEquals(id, user.uuid());
        assertTrue(user.permissions().containsKey("*"));

        CalculatedGroup group = new CalculatedGroup("Admin", 1, Set.of(), Map.of());
        assertEquals("admin", group.name());
        assertEquals(1, group.weight());

        PermissionChangedEvent pe = new PermissionChangedEvent(id, "local");
        GroupChangedEvent ge = new GroupChangedEvent("staff", "serversync");
        assertEquals(id, pe.uuid());
        assertEquals("local", pe.source());
        assertEquals("staff", ge.group());
        assertEquals("serversync", ge.source());
    }
}
