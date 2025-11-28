package com.snac.data.runtime.caching;

import com.snac.util.Loop;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A thread-safe, generic cache for storing objects of type {@code T} with advanced expiration and access control.
 * <p>
 * This cache maintains the insertion order of objects and provides fine-grained control over:
 * <ul>
 *   <li>Temporal expiration based on time added or last access</li>
 *   <li>Index-based expiration (older entries beyond a configurable index threshold can expire)</li>
 *   <li>Automatic deletion of expired objects</li>
 *   <li>Listener notifications for add, remove, use, and expiration events</li>
 * </ul>
 * <p>
 * <b>Key Features:</b>
 * <ul>
 *   <li>Thread-safe using {@link ReentrantReadWriteLock}</li>
 *   <li>Prevents duplicate entries by key</li>
 *   <li>Provides {@link CachedObject#peek()} to inspect objects without affecting expiration or index</li>
 *   <li>{@link CachedObject#use()} resets the index and updates last-used timestamp, affecting expiration</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * Cache<Image> imageCache = new Cache.CacheBuilder<Image>()
 *     .objectsExpireAfter(5, TimeUnit.MINUTES)
 *     .temporalExpirationOnlyWhenUnused(true)
 *     .indexExpireAfter(100)
 *     .deleteObjectsWhenExpired(true)
 *     .build();
 *
 * imageCache.add("player_idle_image", new Image("resources/player_idle.png"));
 *
 * Image playerIdle = imageCache.get("player_idle_image"); // marks object as used
 * Image playerIdlePeek = imageCache.getKey("player_idle_image"); // or via stream.peek() - does not mark as used
 * }</pre>
 *
 * <p>
 * <b>Thread Safety Notes:</b>
 * All public methods are thread-safe. Internally, the cache uses a {@link ReentrantReadWriteLock} for reading and writing operations.
 * Operations that fetch or modify cached objects are synchronized appropriately.
 * </p>
 *
 * <b>Behavior Notes:</b>
 * <ul>
 *   <li>Calling {@link #get(String)} or {@link CachedObject#use()} resets the index of the object (object is set to most recent position).</li>
 *   <li>Calling {@link CachedObject#peek()} does not affect index or last-used timestamp.</li>
 *   <li>Expired objects may be automatically deleted depending on {@link CacheBuilder#deleteObjectsWhenExpired(boolean)}.</li>
 *   <li>Index-based expiration ensures that only the most recent {@code indexExpireAfter} objects remain active.</li>
 * </ul>
 *
 * @param <T> the type of objects to be stored in the cache
 */
@Getter
@Slf4j
public class Cache<T> {
    protected final ReentrantReadWriteLock lock;
    @Getter(AccessLevel.NONE)
    protected final LinkedHashSet<CachedObject<T>> cached;
    @Getter(AccessLevel.NONE)
    protected final List<CacheListener> listeners;
    protected final int expiresAfter;
    protected final TimeUnit expireTimeUnit;
    protected final boolean deleteAfterExpiration;
    protected final boolean temporalExpirationOnlyWhenUnused;
    protected final boolean oldIndexesExpire;
    protected final int indexExpireAfter;

    /**
     * Creates a new cache instance.
     * <p>
     * Please use {@link CacheBuilder} for constructing cache instances.
     *
     * @param expiresAfter                     The duration after which an object will expire. Disable with values like 0 or lower
     * @param expireTimeUnit                   The time unit corresponding to {@code expiresAfter}
     * @param deleteAfterExpiration            Whether expired objects should be automatically removed from the cache
     * @param temporalExpirationOnlyWhenUnused If true, the expiration time is dependent on the last time the object got used instead of the time it got added
     * @param oldIndexExpire                   Whether entries with outdated indexes should expire
     * @param indexExpireAfter                 The index threshold after which old entries should expire
     */
    protected Cache(int expiresAfter, TimeUnit expireTimeUnit, boolean deleteAfterExpiration,
                    boolean temporalExpirationOnlyWhenUnused, boolean oldIndexExpire, int indexExpireAfter) {
        this.expiresAfter = expiresAfter;
        this.expireTimeUnit = expireTimeUnit;
        this.deleteAfterExpiration = deleteAfterExpiration;
        this.temporalExpirationOnlyWhenUnused = temporalExpirationOnlyWhenUnused;
        this.oldIndexesExpire = oldIndexExpire;
        this.indexExpireAfter = indexExpireAfter;

        this.lock = new ReentrantReadWriteLock();
        this.cached = new LinkedHashSet<>();
        this.listeners = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Used to register a {@link CacheListener} for this cache.
     *
     * @param listener The {@link CacheListener} to register
     */
    public void register(CacheListener listener) {
        listeners.add(listener);
    }

    /**
     * Used to remove a {@link CacheListener} from this cache.
     *
     * @param listener The {@link CacheListener} to remove
     */
    public void unregister(CacheListener listener) {
        listeners.remove(listener);
    }

    /**
     * This method is called by a static {@link CacheBuilder} method,
     * which calls every tick method from every created cache 20-times a second.
     * <p>The method takes care of the cache logic. So everything works fine</p>
     * All registered {@link CacheListener} will be notified.
     */
    protected void tick() {
        if (getExpiresAfter() > 0) {
            lock.readLock().lock();
            try {
                cached.stream()
                        .filter(c -> !c.isExpired())
                        .filter(obj -> (System.currentTimeMillis() - (isTemporalExpirationOnlyWhenUnused()
                                ? obj.getLastUsed()
                                : obj.getTimeAdded())) >= getExpireTimeUnit().toMillis(getExpiresAfter()))
                        .forEach(CachedObject::expire);
            } finally {
                lock.readLock().unlock();
            }
        }

        if (isDeleteAfterExpiration()) {
            List<CachedObject<?>> toRemove;
            lock.readLock().lock();
            try {
                toRemove = cached.stream()
                        .filter(CachedObject::isExpired)
                        .collect(Collectors.toList());
            } finally {
                lock.readLock().unlock();
            }

            toRemove.forEach(this::remove);
        }


        var ueIndexes = cached.stream().filter(obj -> !obj.isExpired()).count();
        if (isOldIndexesExpire() && ueIndexes > getIndexExpireAfter()) {
            lock.readLock().lock();
            try {
                var targetExpirations = ueIndexes - getIndexExpireAfter();
                var expired = 0;

                var it = cached.iterator();
                while (it.hasNext() && expired < targetExpirations) {
                    var obj = it.next();
                    if (!obj.isExpired()) {
                        obj.expire();
                        expired++;
                    }
                }
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * Add an object to the cache.
     * <p>
     * All registered {@link CacheListener} will be notified
     * via {@link CacheListener#onCachedObjectAdd(CachedObject)}
     * </p>
     *
     * @param key The unique key the object gets saved on. If the key isn't unique, objects with the same key will be overridden
     * @param object The object you want to add
     */
    public void add(String key, T object) {
        CachedObject<T> cachedObject = new CachedObject<>(this, key, object);
        lock.writeLock().lock();
        try {
            listeners.forEach(listener -> listener.onCachedObjectAdd(cachedObject));
            cached.add(cachedObject);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get a cached object from its key.
     *
     * @param key The key of the object
     * @return {@code null} if this key/object doesn't exist, otherwise the object of the key
     */
    @Nullable
    public T get(String key) {
        lock.readLock().lock();
        try {
            return cached.stream()
                    .filter(obj -> obj.getKey().equals(key))
                    .findFirst()
                    .map(CachedObject::use)
                    .orElse(null);

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get a key based on the object of this key.<br>
     * The objects index or last-used-time won't be affected.
     *
     * @param object The object you want to know the key from
     * @return {@code null} if this object doesn't exist, otherwise the key of the object
     */
    @Nullable
    public String getKey(T object) {
        lock.readLock().lock();
        try {
            return cached.stream().filter(obj -> obj.peek().equals(object))
                    .map(CachedObject::getKey)
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if the cache contains a specific object.
     *
     * @param object The object to check if it exists
     * @return {@code true} if the object is in the cache, otherwise {@code false}
     */
    public boolean contains(T object) {
        return getKey(object) != null;
    }

    /**
     * Checks if the cache contains a specific key.
     *
     * @param key The key to check if it exists
     * @return {@code true} if the key is in the cache, otherwise {@code false}
     */
    public boolean contains(String key) {
        return get(key) != null;
    }

    /**
     * Checks if the cache contains a specific {@link CachedObject}
     *
     * @param cObject The {@link CachedObject} to check if it exists
     * @return {@code true} if the {@link CachedObject} is in the cache, otherwise {@code false}
     */
    public boolean contains(@NotNull CachedObject<?> cObject) {
        return contains(cObject.getKey());
    }

    /**
     * Removes an object based on its key. Will do nothing if the given key doesn't exist.
     * <p>
     * All registered {@link CacheListener} will be notified
     * via {@link CacheListener#onCachedObjectRemove(CachedObject)}
     * </p>
     *
     * @param key The key of the object to be removed from the cache
     */
    public void remove(String key) {
        lock.writeLock().lock();
        try {
            cached.stream()
                    .filter(obj -> obj.getKey().equals(key))
                    .forEach(obj -> {
                        listeners.forEach(lstnr -> lstnr.onCachedObjectRemove(obj));
                        cached.remove(obj);
                    });
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a specific object from the cache. Will do nothing if the object doesn't exist.
     * <p>
     * All registered {@link CacheListener} will be notified
     * via {@link CacheListener#onCachedObjectRemove(CachedObject)}
     * </p>
     *
     * @param object The object to be removed from the cache
     */
    public void remove(T object) {
        lock.readLock().lock();
        try {
            cached.stream()
                    .filter(obj -> obj.peek().equals(object))
                    .forEach(obj -> remove(obj.getKey()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes a specific {@link CachedObject} from the cache.
     * Will do nothing if the {@link CachedObject} doesn't exist.
     * <p>
     * All registered {@link CacheListener} will be notified
     * via {@link CacheListener#onCachedObjectRemove(CachedObject)}
     * </p>
     *
     * @param cObject The {@link CachedObject} to be removed from the cache
     */
    public void remove(CachedObject<?> cObject) {
        lock.writeLock().lock();
        try {
            listeners.forEach(lstnr -> lstnr.onCachedObjectRemove(cObject));
            cached.remove(cObject);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a {@link Stream} of all currently cached {@link CachedObject} instances.
     * <p>
     * This method allows you to inspect the cache contents, e.g., for debugging,
     * statistics, or searching for specific entries.
     * </p>
     *
     * <h3>Important Notes:</h3>
     * <ul>
     *   <li>
     *     Accessing the cached object via {@link CachedObject#use()} affects it's last-used-time and index
     *     (May affect the whole cache functionality).<br>
     *     It's recommended to use {@link CachedObject#peek()}.
     *   </li>
     *   <li>
     *     The returned stream is based on a snapshot copy of the current cache contents.
     *     Later modifications to the cache will not affect the stream.
     *   </li>
     *   <li>Also see {@link CachedObject} for more information</li>
     * </ul>
     *
     * @return A snapshot stream of currently cached {@link CachedObject} entries
     */
    public Stream<CachedObject<T>> stream() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(cached).stream();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears every {@link CachedObject} from this Cache.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cached.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * This class stores any relevant data for cached objects. It is for internal use in Caches.
     * <p>
     * If you want to access the stored object without possible impacts on the cache functionality,
     * you should access the stored object via {@link #peek()} instead of {@link #use()}.
     * For example when using {@link Cache#stream()}
     * </p>
     *
     * @param <T> The type of the object the class instance stores.
     */
    @Getter
    public static class CachedObject<T> {
        protected final Cache<T> cache;
        protected final String key;
        @Getter(AccessLevel.NONE)
        protected final T object;
        protected final long timeAdded;
        protected long lastUsed;
        protected boolean expired;

        /**
         * This class is for internal use only. You don't need to create instances except you're building a cache class by your own
         *
         * @param cache  The parent cache
         * @param key    The key of the stored object
         * @param object The object to store
         */
        protected CachedObject(Cache<T> cache, String key, T object) {
            this.cache = cache;
            this.key = key;
            this.object = object;
            this.timeAdded = System.currentTimeMillis();
            this.lastUsed = timeAdded;
        }

        /**
         * Returns the object stored in this {@code CachedObject}
         * but other than {@link #use()} this won't affect functions like
         * {@link CacheBuilder#indexExpireAfter(int)} or {@link CacheBuilder#temporalExpirationOnlyWhenUnused(boolean)},
         * because nothing else will be done except of returning the actual object stored in this {@code CachedObject}.
         *
         * @return the core object stored by this {@code CachedObject}
         */
        public T peek() {
            return object;
        }

        /**
         * Returns the object stored in this {@code CachedObject}.
         * It also sets a new {@link #lastUsed} time and resets this object index.<br>
         * This is necessary for {@link CacheBuilder#indexExpireAfter(int)}
         * and {@link CacheBuilder#temporalExpirationOnlyWhenUnused(boolean)} to work as intended.
         * <p>
         * Resetting the index of an object prevents {@link CacheBuilder#indexExpireAfter(int)}
         * from expire objects which got fetched recently.
         *
         * @return the core object stored by this {@code CachedObject}
         */
        public T use() {
            cache.listeners.forEach(lstnr -> lstnr.onCacheObjectUse(this));
            this.lastUsed = System.currentTimeMillis();
            resetIndex();
            return object;
        }

        /**
         * Resets the objects index in the cache.
         * This is used in {@link #use()} to provide correct functionality of {@link CacheBuilder#indexExpireAfter(int)}.
         */
        public void resetIndex() {
            cache.lock.writeLock().lock();
            try {
                cache.cached.remove(this);
                cache.cached.add(this);
            } finally {
                cache.lock.writeLock().unlock();
            }
        }

        /**
         * By calling this, the stored object will count as expired and will maybe be deleted by the cache stored in.
         */
        protected void expire() {
            cache.listeners.forEach(lstnr -> lstnr.onCachedObjectExpire(this));
            expired = true;
        }

        /**
         * See {@link Object#toString()}
         */
        @Override
        public String toString() {
            return "CachedObject{" +
                    "key='" + key + '\'' +
                    ", object=" + object +
                    ", timeAdded=" + timeAdded +
                    ", lastUpdated=" + lastUsed +
                    ", expired=" + expired +
                    '}';
        }
    }

    /**
     * This class is to build a {@link Cache} instance.
     *
     * @param <T> The type of objects to be stored in the cache
     */
    public static class CacheBuilder<T> {
        protected static final Set<Cache<?>> caches = Collections.synchronizedSet(new HashSet<>());
        protected int expiresAfter = -1;
        protected TimeUnit expireTimeUnit = TimeUnit.MINUTES;
        protected boolean deleteAfterExpiration = false;
        protected boolean temporalExpirationOnlyWhenUnused = false;
        protected int indexExpireAfter = -1;

        static {
            startTicking();
        }

        /**
         * This method is called once by a static initializer block to initialize the loop
         * and uses the {@link Loop} class to tick every created cache 20-times a second.
         * This is necessary to provide full cache functionality.
         */
        protected static void startTicking() {
            Loop.builder()
                    .runOnThread(true)
                    .threadName("Caching-Thread")
                    .build()
                    .start(20, (fps, deltaTime) -> {
                        synchronized (caches) {
                            caches.forEach(Cache::tick);
                        }
                    });
        }

        /**
         * Sets the time objects should expire automatically.
         * Use 0 or lower as {@code expiresAfter} value to disable this feature.
         *
         * @param expiresAfter   The duration after which an object may expire
         * @param expireTimeUnit The time unit corresponding to {@code expiresAfter}
         * @return this {@link CacheBuilder} instance for method chaining
         */
        public CacheBuilder<T> objectsExpireAfter(final int expiresAfter, final TimeUnit expireTimeUnit) {
            this.expiresAfter = expiresAfter;
            this.expireTimeUnit = expireTimeUnit;
            return this;
        }

        /**
         * Whether expired objects should be automatically removed from the cache.
         *
         * @param deleteAfterExpiration Set to {@code true} expired objects will be deleted automatically
         * @return this {@link CacheBuilder} instance for method chaining
         */
        public CacheBuilder<T> deleteObjectsWhenExpired(final boolean deleteAfterExpiration) {
            this.deleteAfterExpiration = deleteAfterExpiration;
            return this;
        }

        /**
         * Configures whether temporal expiration should be based on object usage rather than insertion time.
         * <p>
         * If {@code true}, cached objects will expire based on the time they were last accessed,
         * instead of the time they were added.
         * <p>
         * This method only works if {@link #objectsExpireAfter(int, TimeUnit)} was set before.<br>
         * When {@link #objectsExpireAfter(int, TimeUnit)} is set, it will be overridden.
         * This means the expiration time will count from the last time the object was used instead of the time it got
         * <p>
         * This setting has no effect for index-based expiration via {@link #indexExpireAfter(int)}.
         * <p>
         * <b>For more details on when objects are considered "used",
         * see {@link Cache#stream()} and {@link CachedObject}
         * (especially {@link CachedObject#use()} and {@link CachedObject#peek()}).</b>
         *
         * @param temporalExpirationOnlyWhenUnused {@code true} to enable usage-based expiration;
         *                                         {@code false} to use insertion-based expiration
         * @return this {@link CacheBuilder} instance for method chaining
         */
        public CacheBuilder<T> temporalExpirationOnlyWhenUnused(final boolean temporalExpirationOnlyWhenUnused) {
            this.temporalExpirationOnlyWhenUnused = temporalExpirationOnlyWhenUnused;
            return this;
        }

        /**
         * Sets the index threshold for automatic expiration of older cached entries.
         * <p>
         * If the given {@code indexExpireAfter} is greater than {@code 0}, older cached entries
         * will expire automatically to prevent the cache from having more entries than this value sets.
         * If the value is {@code 0} or less, index-based expiration is disabled.
         * <p>
         * When objects are fetched, their index is reset to {@code 0}.
         * (in case those objects got fetched via {@link #get(String)} or directly with
         * {@link CachedObject#use()} ({@link CachedObject#peek()} won't reset the index))
         * This means that recently used objects are unlikely to be affected.
         *
         * @param indexExpireAfter the number of recent indices to keep; older entries will expire automatically.
         *                         A value of {@code 0} or less disables this behavior.
         * @return this {@link CacheBuilder} instance for method chaining
         */
        public CacheBuilder<T> indexExpireAfter(final int indexExpireAfter) {
            this.indexExpireAfter = indexExpireAfter;
            return this;
        }

        /**
         * @return The {@link Cache} instance you build with this Builder.
         */
        public final Cache<T> build() {
            Cache<T> cache = new Cache<>(
                    expiresAfter,
                    expireTimeUnit,
                    deleteAfterExpiration,
                    temporalExpirationOnlyWhenUnused,
                    indexExpireAfter > 0,
                    indexExpireAfter);
            caches.add(cache);
            log.info("Initialized new cache");
            return cache;
        }
    }
}
