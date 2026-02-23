package com.snac.data.runtime.caching;

/**
 * Listener interface for receiving events from a {@link Cache}.
 * <p>
 * Listeners must be registered with a specific {@code Cache} instance using
 * {@link Cache#register(CacheListener)} in order to receive notifications.
 * All listener methods are called <strong>before</strong> the corresponding action is performed on the cache.
 *
 * <p>Example usage:
 * <pre>{@code
 * Cache<String> cache = new Cache.CacheBuilder<String>().build();
 * cache.register(new CacheListener() {
 *     @Override
 *     public void onCachedObjectExpire(Cache.CachedObject<?> object) {
 *         // Called before the object expires
 *     }
 *
 *     @Override
 *     public void onCachedObjectRemove(Cache.CachedObject<?> object) {
 *         // Called before the object is removed
 *     }
 *
 *     @Override
 *     public void onCachedObjectAdd(Cache.CachedObject<?> object) {
 *         // Called before the object is added to the cache
 *     }
 * });
 * }</pre>
 *
 * <p>You can unregister a listener using {@link Cache#unregister(CacheListener)}.
 */
public interface CacheListener {

    /**
     * Called before a cached object expires.
     *
     * @param object the object that is about to expire
     */
    void onCachedObjectExpire(Cache.CachedObject<?> object);

    /**
     * Called before a cached object is removed from the cache.
     *
     * @param object the object that is about to be removed
     */
    void onCachedObjectRemove(Cache.CachedObject<?> object);

    /**
     * Called before a new object is added to the cache.
     *
     * @param object the object that is about to be added
     */
    void onCachedObjectAdd(Cache.CachedObject<?> object);

    /**
     * Called when a cached object got fetched via {@link Cache.CachedObject#use()}.
     *
     * @param object the object which is about to be fetched
     */
    void onCachedObjectUse(Cache.CachedObject<?> object);
}

