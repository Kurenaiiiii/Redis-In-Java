package dev.redisinjava.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.redisinjava.util.Raw;
import org.junit.jupiter.api.Test;

/** Unit tests for lazy expiry, versions and TTL handling in the keyspace. */
class KeySpaceTest {
    private static RedisStringValue str(String value) {
        return new RedisStringValue(Raw.encode(value));
    }

    @Test
    void putGetDelete() {
        KeySpace space = new KeySpace();
        space.put("k", str("v"));
        assertEquals("v", Raw.decode(((RedisStringValue) space.get("k")).data()));
        assertTrue(space.delete("k"));
        assertFalse(space.delete("k"));
        assertNull(space.get("k"));
    }

    @Test
    void expiredKeysVanishLazily() throws Exception {
        KeySpace space = new KeySpace();
        space.put("k", str("v"));
        space.setExpireAt("k", System.currentTimeMillis() + 30);
        assertTrue(space.contains("k"));
        Thread.sleep(80);
        assertNull(space.get("k"));
        assertEquals(0, space.size());
    }

    @Test
    void overwriteDropsTtlWhilePutKeepsIt() {
        KeySpace space = new KeySpace();
        space.put("k", str("v"));
        space.setExpireAt("k", System.currentTimeMillis() + 100000);
        space.put("k", str("v2"));
        assertTrue(space.expireAt("k") > 0);
        space.overwrite("k", str("v3"));
        assertEquals(-1, space.expireAt("k"));
    }

    @Test
    void versionsBumpOnEveryMutation() {
        KeySpace space = new KeySpace();
        long v0 = space.version("k");
        space.put("k", str("v"));
        long v1 = space.version("k");
        assertNotEquals(v0, v1);
        space.delete("k");
        assertNotEquals(v1, space.version("k"));
    }

    @Test
    void sweepExpiresWithoutReads() throws Exception {
        KeySpace space = new KeySpace();
        space.put("k", str("v"));
        space.setExpireAt("k", System.currentTimeMillis() + 20);
        Thread.sleep(60);
        int expired = 0;
        for (int i = 0; i < 10 && expired == 0; i++) {
            expired += space.sweepOnce();
        }
        assertEquals(1, expired);
        assertEquals(0, space.size());
    }
}
