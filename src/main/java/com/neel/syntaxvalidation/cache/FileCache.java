package com.neel.syntaxvalidation.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe, in-memory cache of file contents.
 *
 * <p>The cache keeps a {@link FileCacheEntry} per file keyed by its absolute
 * path. On {@link #getOrLoad(Path)} it returns the cached entry, automatically
 * reloading the file when its last-modified timestamp on disk has advanced past
 * the value recorded in the cache. This guarantees that validation always runs
 * against a fresh copy while still avoiding redundant disk reads for unchanged
 * files.
 *
 * <p>The cache never writes to disk &mdash; it only reads. Modifications are
 * applied to in-memory copies produced by the {@code ModificationApplier}, so
 * the original file on disk is left untouched.
 */
public class FileCache {

    private final ConcurrentMap<String, FileCacheEntry> cache = new ConcurrentHashMap<>();

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
            return existing;
        }
        FileCacheEntry entry = loadEntry(path, currentModified);
        cache.put(key, entry);
        return entry;
    }

    /**
     * @param path the file path.
     * @return the currently cached entry, if present, without touching the disk.
     */
    public Optional<FileCacheEntry> get(Path path) {
        return Optional.ofNullable(cache.get(toKey(path)));
    }

    /** @return {@code true} if an entry for the path currently resides in the cache. */
    public boolean contains(Path path) {
        return cache.containsKey(toKey(path));
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
