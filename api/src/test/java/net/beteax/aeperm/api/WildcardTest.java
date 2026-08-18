package net.beteax.aeperm.api;

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
        assertFalse(Wildcard.match(Map.of("a.*", false), "a.b"));
        assertFalse(Wildcard.match(Map.of(), "a.b"));
    }

    @Test
    void normalizeAndNegation() {
        assertEquals("foo.bar", Wildcard.normalize("-foo.bar"));
        assertEquals("foo.bar", Wildcard.normalize("Foo.Bar"));
        assertTrue(Wildcard.isNegated("-foo"));
        assertFalse(Wildcard.isNegated("foo"));
    }
}
