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
 *   <li>Calling {@link CachedObject#peek()} to access stored objects doesn't affect the object in any way
 *   (like index reset or last-used timestamp update).
 *   This method should only be used for debugging or searching for objects,
 *   otherwise {@link #get(String)} (Or directly {@link CachedObject#use()}) should be used.</li>
 *   <li>Expired objects may be automatically deleted depending on {@link CacheBuilder#deleteObjectsWhenExpired(boolean)}.</li>
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
     * This list is a snapshot of the current cached contents.<br>
     * The list is unmodifiable, but the stored objects are the originals, which means they can be modified.
     * <p>
     * In this case it is mostly reccommendet to use {@link CachedObject#peek()} instead of
     * {@link CachedObject#use()} to access the wrapped objects.<br>
     * As {@link CachedObject#peek()} won't affect the object's last-used-time and index,
     * object lifetime or internal behavior in cache also won't be affected.
     */
    protected final List<CachedObject<T>> cachedCopy;

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
        this.cachedCopy = Collections.unmodifiableList(new ArrayList<>());
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
                for (var obj : cached) {
                    if (obj.isExpired()) continue;

                    long lifetime = System.currentTimeMillis() - (isTemporalExpirationOnlyWhenUnused()
                            ? obj.getLastUsed()
                            : obj.getTimeAdded());

                    if (lifetime >= getExpireTimeUnit().toMillis(getExpiresAfter())) {
                        obj.expire();
                    }
                }
            } finally {
                lock.readLock().unlock();
            }
        }

        if (isDeleteAfterExpiration()) {
            lock.writeLock().lock();
            try {
                cached.removeIf(CachedObject::isExpired);
            } finally {
                lock.writeLock().unlock();
            }
        }


        if (isOldIndexesExpire()) {
            lock.readLock().lock();
            try {
                int ueIndexes = 0;
                for (var obj : cached) {
                    if (!obj.isExpired()) {
                        ueIndexes++;
                    }
                }

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
        var cachedObject = new CachedObject<>(this, key, object);
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
     * @return the object of this key, or {@code null} if the key doesn't exist
     */
    @Nullable
    public T get(String key) {
        lock.readLock().lock();
        try {
            for (var obj : cached) {
                if (obj.getKey().equals(key)) {
                    return obj.use();
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return null;
    }

    /**
     * Get a key based on the object of this key.<br>
     * The object index or last-used-time won't be affected.
     *
     * @param object The object you want to know the key from
     * @return the key of the object, or {@code null} if this object doesn't exist
     */
    @Nullable
    public String getKey(T object) {
        lock.readLock().lock();
        try {
            for (var obj : cached) {
                if (obj.peek().equals(object)) {
                    return obj.getKey();
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return null;
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
     * @return {@code true} if the object was removed, {@code false} if the key didn't exist
     */
    public boolean remove(String key) {
        lock.writeLock().lock();
        try {
            return cached.removeIf(obj -> {
                if (obj.getKey().equals(key)) {
                    listeners.forEach(lstnr -> lstnr.onCachedObjectRemove(obj));
                    return true;
                }
                return false;
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a specific object from the cache.
     * <p>
     * All registered {@link CacheListener} will be notified
     * via {@link CacheListener#onCachedObjectRemove(CachedObject)}
     * </p>
     *
     * @param object The object to be removed from the cache
     * @return {@code true} if the object was removed, {@code false} if the object didn't exist
     */
    public boolean remove(T object) {
        lock.writeLock().lock();
        try {
            return cached.removeIf(obj -> obj.peek().equals(object));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a specific {@link CachedObject} from the cache.
     * <p>
     * All registered {@link CacheListener} will be notified
     * via {@link CacheListener#onCachedObjectRemove(CachedObject)}
     * </p>
     *
     * @param cObject The {@link CachedObject} to be removed from the cache
     * @return {@code true} if the {@link CachedObject} was removed, {@code false} if the {@link CachedObject} didn't exist
     */
    public boolean remove(CachedObject<?> cObject) {
        lock.writeLock().lock();
        try {
            listeners.forEach(lstnr -> lstnr.onCachedObjectRemove(cObject));
            return cached.remove(cObject);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @deprecated Use {@link #getCachedCopy()} to access an unmodifiable copy of the cache,
     * which can be used for debugging purposes or somehing like that.<br><br>
     *
     * Returns a {@link Stream} of all currently cached {@link CachedObject} instances.
     * <p>
     * This method allows you to inspect the cache contents, e.g., for debugging,
     * statistics, or searching for specific entries.
     * </p>
     *
     * <h3>Important Notes:</h3>
     * <ul>
     *   <li>
     *     In this case it is reccommendet to use {@link CachedObject#peek()} instead of
     *     {@link CachedObject#use()} to access objects.<br>
     *     As {@link CachedObject#peek()} won't affect the object's last-used-time and index,
     *     object lifetime or internal behavior in cache also won't be affected.
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
    @Deprecated
    public Stream<CachedObject<T>> stream() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(cached).stream();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears every {@link CachedObject} from this Cache. Also notifies every Listener
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            for (var obj : cached) {
                listeners.forEach(lstnr -> lstnr.onCachedObjectRemove(obj));
            }
            cached.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * This class stores any relevant data for cached objects. It is for internal use in Caches.
     * <p>
     * In general, it is recommended to use {@link #use()} to access the wrapped object.<p>
     * But in some cases (like debugging or something) also {@link #peek()}
     * can be used so objects can be inspected without affecting expiration or index.
     * This means the used object will behave as no access would have taken place.
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
         * Returns the object stored in this {@code CachedObject}.
         *
         * <p>
         * Use to inspect objects without affecting expiration or index.
         * </p>
         *
         * Other than {@link #use()} this won't affect the objects last-used-time and index.
         * This can cause the cache to behave differently than expected.<br>
         * This method is intended only for debugging purposes or internal functionality.
         *
         * @return the core object stored by this {@code CachedObject}
         */
        public T peek() {
            return object;
        }

        /**
         * Returns the object stored in this {@code CachedObject}.
         * It also sets a new {@link #lastUsed} time and resets this object index.
         *
         * <p>
         * This is necessary so cache functionality like for example {@link CacheBuilder#indexExpireAfter(int)}
         * or {@link CacheBuilder#temporalExpirationOnlyWhenUnused(boolean)} works as intended.
         * </p>
         *
         * Resetting the index of an object prevents {@link CacheBuilder#indexExpireAfter(int)}
         * from expire objects which got fetched recently.
         *
         * @return the core object stored by this {@code CachedObject}
         */
        public T use() {
            cache.listeners.forEach(lstnr -> lstnr.onCachedObjectUse(this));
            this.lastUsed = System.currentTimeMillis();
            resetIndex();
            return object;
        }

        /**
         * Resets the object index in the cache.
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
         * By calling this, the stored object will count as expired and may be deleted by the cache it's stored in.
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
     * Builder class to build a {@link Cache} instance.
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
            new Loop(true, "Caching-Thread", null)
                    .startTickLoop(20, (tps, deltaTime) -> {
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
