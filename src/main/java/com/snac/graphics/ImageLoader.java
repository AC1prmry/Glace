package com.snac.graphics;

import com.snac.data.runtime.caching.Cache;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * A generic, abstract image loader to simplify loading and caching of image resources
 * for specific {@link Renderer Renderer} implementations.
 * <p>
 * This class provides a unified interface for loading images from paths and storing them
 * in a {@link Cache}.
 * It also includes fallback handling and supports custom cache configurations.
 * </p>
 * <p>
 * <strong>Usage recommendation:</strong> One {@code ImageLoader} instance per {@code Renderer} type.
 * </p>
 *
 * @param <I> The type of the image the loader handles
 */
@Getter
@Slf4j
public abstract class ImageLoader<I> {

    /**
     * The cache used to store loaded image instances.
     */
    protected final Cache<I> cache;

    /**
     * Creates a new {@code ImageLoader} using a default {@link Cache} configuration.
     * <ul>
     *     <li>Entries expire after 7 minutes of inactivity</li>
     *     <li>Expiration only applies when unused</li>
     *     <li>Expired entries are removed automatically</li>
     * </ul>
     */
    public ImageLoader() {
        this(new Cache.CacheBuilder<I>()
                .objectsExpireAfter(7, TimeUnit.MINUTES)
                .temporalExpirationOnlyWhenUnused(true)
                .deleteObjectsWhenExpired(true)
                .build());
    }

    /**
     * Creates a new {@code ImageLoader} with a custom {@link Cache} instance.
     *
     * @param imageCache The cache implementation to be used for storing image instances
     */
    public ImageLoader(Cache<I> imageCache) {
        this.cache = imageCache;
    }

    /**
     * Returns a cached image for the given path if available,
     * otherwise loads it using {@link #load(String)} and caches it under a hashed name.
     *
     * @param path The path to the image resource
     * @return The cached or newly loaded image instance
     */
    public I getCachedOrLoad(String path) {
        return getCachedOrLoad(path, String.valueOf(path.hashCode()));
    }

    /**
     * Returns a cached image for the given name if available,
     * otherwise caches the provided image manually.
     *
     * @param image The image to cache if not already present
     * @param name  The unique name used as a cache key
     * @return The cached or provided image
     */
    public I getCachedOrLoad(I image, String name) {
        return cache
                .get(name)
                .orElse(cache(image, name));
    }

    /**
     * Returns a cached image for the given name if available,
     * otherwise loads it from the given path and stores it under that name.
     *
     * @param path The path to the image resource
     * @param name The unique name to associate with the cached image
     * @return The cached or newly loaded image instance
     */
    public I getCachedOrLoad(String path, String name) {
        return cache
                .get(name)
                .orElse(cache(load(path), name));
    }

    /**
     * Returns the cached image associated with the given name.
     * If not found, the {@link #getFallback()} image is returned instead.
     *
     * @param name The cache key
     * @return The cached image or the fallback image
     */
    public I getCached(String name) {
        return cache.get(name).orElse(getFallback());
    }

    /**
     * Manually caches the given image instance under the specified name.
     *
     * @param image The image to cache
     * @param name  The unique name to associate with the image
     * @return The cached image
     */
    public I cache(I image, String name) {
        cache.add(name, image);
        log.info("Cached image '{}'", name);
        return image;
    }

    /**
     * Loads an image from the given path.
     * <p>This method must be implemented by subclasses to define actual loading logic.</p>
     *
     * @param path The path to the image resource
     * @return The loaded image
     */
    public abstract I load(String path);

    /**
     * Returns a fallback image to use when loading fails or no cached version is available.
     *
     * @return The fallback image
     */
    public abstract I getFallback();
}