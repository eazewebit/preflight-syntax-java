package com.neel.syntaxvalidation.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory cache of file contents with optional LRU eviction.
 *
 * <p>The cache keeps a {@link FileCacheEntry} per file keyed by its absolute
 * path. On {@link #getOrLoad(Path)} it returns the cached entry, automatically
 * reloading the file when its last-modified timestamp on disk has advanced past
 * the value recorded in the cache. This guarantees that validation always runs
 * against a fresh copy while still avoiding redundant disk reads for unchanged
 * files.
 *
 * <p>When constructed with a maximum size, the cache uses LRU (Least Recently Used)
 * eviction to prevent unbounded memory growth in long-running applications. When
 * the maximum size is reached, the least recently accessed entry is removed before
 * adding a new one.
 *
 * <p>The cache never writes to disk &mdash; it only reads. Modifications are
 * applied to in-memory copies produced by the {@code ModificationApplier}, so
 * the original file on disk is left untouched.
 */
public class FileCache {

    /** Default maximum cache size (0 = unlimited). */
    public static final int DEFAULT_MAX_SIZE = 0;

    private final ConcurrentHashMap<String, FileCacheEntry> cache = new ConcurrentHashMap<>();
    private final int maxSize;

    /**
     * Creates a cache with unlimited size.
     */
    public FileCache() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * Creates a cache with a maximum number of entries.
     *
     * @param maxSize the maximum number of entries to keep (0 = unlimited).
     */
    public FileCache(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize must be >= 0");
        }
        this.maxSize = maxSize;
    }

    /**
     * Returns the cached entry for the given path, reloading it from disk if the
     * file is new or has been modified since it was cached.
     *
     * @param path the file path; must not be {@code null}.
     * @return a fresh or cached {@link FileCacheEntry}.
     * @throws IOException if the file cannot be read.
     */
    public FileCacheEntry getOrLoad(Path path) throws IOException {
        String key = toKey(path);
        long currentModified = Files.getLastModifiedTime(path).toMillis();
        FileCacheEntry existing = cache.get(key);
        if (existing != null && existing.getLastModified() == currentModified) {
            existing.touch();
            return existing;
        }
        FileCacheEntry entry = loadEntry(path, currentModified);
        putEntry(key, entry);
        return entry;
    }

    /**
     * @param path the file path.
     * @return the currently cached entry, if present, without touching the disk.
     */
    public Optional<FileCacheEntry> get(Path path) {
        FileCacheEntry entry = cache.get(toKey(path));
        if (entry != null) {
            entry.touch();
        }
        return Optional.ofNullable(entry);
    }

    /** @return {@code true} if an entry for the path currently resides in the cache. */
    public boolean contains(Path path) {
        FileCacheEntry entry = cache.get(toKey(path));
        if (entry != null) {
            entry.touch();
            return true;
        }
        return false;
    }

    /**
     * Forces the next {@link #getOrLoad(Path)} to reload the file from disk.
     *
     * @param path the file path.
     * @return {@code true} if a cached entry was actually removed.
     */
    public boolean invalidate(Path path) {
        return cache.remove(toKey(path)) != null;
    }

    /** Removes every cached entry. */
    public void clear() {
        cache.clear();
    }

    /** @return the number of entries currently held in the cache. */
    public int size() {
        return cache.size();
    }

    /** @return the configured maximum cache size (0 = unlimited). */
    public int getMaxSize() {
        return maxSize;
    }

    private void putEntry(String key, FileCacheEntry entry) {
        if (maxSize > 0 && cache.size() >= maxSize) {
            evictLRU();
        }
        cache.put(key, entry);
    }

    /**
     * Evicts the least recently used entry from the cache.
     * Uses a simple approach: find the entry with the oldest access time.
     */
    private void evictLRU() {
        if (cache.isEmpty()) {
            return;
        }
        
        String oldestKey = null;
        long oldestAccess = Long.MAX_VALUE;
        
        for (Map.Entry<String, FileCacheEntry> e : cache.entrySet()) {
            long accessTime = e.getValue().getLastAccessed();
            if (accessTime < oldestAccess) {
                oldestAccess = accessTime;
                oldestKey = e.getKey();
            }
        }
        
        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }

    private FileCacheEntry loadEntry(Path path, long lastModified) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return new FileCacheEntry(
                path.toAbsolutePath().toString(),
                lines,
                lastModified,
                System.currentTimeMillis());
    }

    private static String toKey(Path path) {
        return path.toAbsolutePath().toString();
    }
}
