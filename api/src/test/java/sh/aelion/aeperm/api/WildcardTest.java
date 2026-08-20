package sh.aelion.aeperm.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildcardTest {

    @Test
    void matchesStarAndNested() {
        assertTrue(Wildcard.match(Map.of("*", true), "a.b"));
        assertTrue(Wildcard.match(Map.of("a.*", true), "a.b.c"));
        assertTrue(Wildcard.match(Map.of("minecraft.*", true), "minecraft.command.gamemode"));
        assertTrue(Wildcard.match(Map.of("minecraft.*", true), "minecraft"));
        assertFalse(Wildcard.match(Map.of("a.*", false), "a.b"));
        assertFalse(Wildcard.match(Map.of(), "a.b"));
    }

    @Test
    void prefersMostSpecificWildcard() {
        Map<String, Boolean> nodes = Map.of(
                "*", true,
                "minecraft.command.ban.*", false,
                "minecraft.*", true
        );
        assertFalse(Wildcard.match(nodes, "minecraft.command.ban.ip"));
        assertTrue(Wildcard.match(nodes, "minecraft.command.gamemode"));
        assertTrue(Wildcard.match(nodes, "bukkit.command.help"));
    }

    @Test
    void expandAppliesWildcardsOntoKnownNodes() {
        Map<String, Boolean> expanded = Wildcard.expand(
                Map.of("*", true, "minecraft.command.ban", false),
                java.util.List.of("minecraft.command.gamemode", "minecraft.command.ban", "bukkit.command.plugins")
        );
        assertEquals(true, expanded.get("*"));
        assertEquals(true, expanded.get("minecraft.command.gamemode"));
        assertEquals(false, expanded.get("minecraft.command.ban"));
        assertEquals(true, expanded.get("bukkit.command.plugins"));
    }

    @Test
    void lookupExactBeatsWildcard() {
        Map<String, Boolean> nodes = Map.of("minecraft.*", true, "minecraft.command.gamemode", false);
        assertFalse(Wildcard.match(nodes, "minecraft.command.gamemode"));
        assertTrue(Wildcard.lookup(nodes, "minecraft.command.give").orElseThrow());
    }

    @Test
    void normalizeAndNegation() {
        assertEquals("foo.bar", Wildcard.normalize("-foo.bar"));
        assertEquals("foo.bar", Wildcard.normalize("Foo.Bar"));
        assertTrue(Wildcard.isNegated("-foo"));
        assertFalse(Wildcard.isNegated("foo"));
    }
}
