package com.snac.data.runtime.caching;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CacheTest {
    private Cache<Integer> cache;

    @Before
    public void setUp() {
        this.cache = new Cache.CacheBuilder<Integer>()
                .objectsExpireAfter(2, TimeUnit.SECONDS)
                .deleteObjectsWhenExpired(true)
                .temporalExpirationOnlyWhenUnused(true)
                .indexExpireAfter(2)
                .build();
        cache.add("test1", 6);
        cache.add("test2", 7);
    }

    @AfterClass
    public static void tearDown() {
        Cache.CacheBuilder.shutdown();
    }

    @Test
    public void testGet() {
        assertEquals("Cache should have the size 2", 2, cache.cached.size());
        assertTrue("Value of key 'test1' should be 6", Objects.equals(cache.get("test1"), 6));
        assertEquals("Key of Value 6 should be 'test1'", "test1", cache.getKey(6));
    }

    @Test
    public void testRemove() {
        cache.remove("test1");
        assertEquals("Cache should have the size 1", 1, cache.cached.size());
    }

    @Test
    public void testIndexExpiration() throws InterruptedException {
        cache.add("test3", 8);
        TimeUnit.SECONDS.sleep(2);

        assertEquals("Cache should have only 2 unexpired entries",
                2, cache.cached.stream().filter(e -> !e.expired).count());
        assertTrue("Oldest entry should be removed first", cache.getCachedObject("test1").isExpired());
    }

    @Test
    public void testTemporalExpiration() throws InterruptedException {
        TimeUnit.SECONDS.sleep(2);
        cache.get("test1");
        TimeUnit.SECONDS.sleep(1);
        assertEquals("Cache should contain only one entry", 1, cache.cached.size());
    }

}
