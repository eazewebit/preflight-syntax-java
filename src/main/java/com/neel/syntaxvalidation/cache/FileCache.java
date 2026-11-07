package com.neel.syntaxvalidation.cache;

import com.neel.syntaxvalidation.io.UnsupportedFileEncodingException;
import com.neel.syntaxvalidation.modification.Document;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory cache of file contents with optional LRU eviction.
 *
 * <p>The cache keeps a {@link FileCacheEntry} per file keyed by its absolute path. On
 * {@link #getOrLoad(Path)} it returns the cached entry, automatically reloading the file
 * when its last-modified timestamp on disk has advanced past the recorded value or its
 * size no longer matches. Size is part of the staleness test because a write that lands
 * in the same clock tick as the cached timestamp would otherwise be missed, leaving an
 * editor validating (and then editing) stale content.
 *
 * <p>When constructed with a maximum size, the cache uses LRU (Least Recently Used)
 * eviction to prevent unbounded memory growth in long-running applications.
 *
 * <p><b>The cache never writes to disk &mdash; it only reads.</b> Modifications are applied
 * to the in-memory {@link Document} produced by the {@code ModificationApplier}, so the
 * original file is left untouched until a caller explicitly persists it (see
 * {@code com.neel.syntaxvalidation.io.SourceFileWriter}).
 *
 * <p><b>Encoding contract.</b> Files are decoded strictly as UTF-8: no lossy fallback and
 * no silent replacement characters. A byte sequence that is not valid UTF-8 fails loudly
 * with {@link UnsupportedFileEncodingException}, naming the path and byte offset, because
 * a lossy read would make the next write rewrite bytes nobody asked to change. A leading
 * UTF-8 BOM is stripped from line&nbsp;1 (so language detection and first-line content stay
 * clean) and recorded on the entry so it can be re-emitted verbatim on write.
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
     * Returns the cached entry for the given path, reloading it from disk if the file is
     * new, has been modified since it was cached, or no longer matches its cached size.
     *
     * @param path the file path; must not be {@code null}.
     * @return a fresh or cached {@link FileCacheEntry}.
     * @throws UnsupportedFileEncodingException if the file is not valid UTF-8.
     * @throws IOException                      if the file cannot be read.
     */
    public FileCacheEntry getOrLoad(Path path) throws IOException {
        String key = toKey(path);
        long currentModified = Files.getLastModifiedTime(path).toMillis();
        long currentSize = Files.size(path);

        FileCacheEntry existing = cache.get(key);
        if (existing != null && isFresh(existing, currentModified, currentSize)) {
            existing.touch();
            return existing;
        }
        FileCacheEntry entry = loadEntry(path, currentModified, currentSize);
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
     * Forces the next {@link #getOrLoad(Path)} to reload the file from disk. Call this after
     * writing a cached file so subsequent reads observe the new bytes immediately.
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

    // ==================================================================
    //  Internals
    // ==================================================================

    private static boolean isFresh(FileCacheEntry entry, long currentModified, long currentSize) {
        if (entry.getLastModified() != currentModified) {
            return false;
        }
        // Size guards against a same-timestamp rewrite (coarse filesystem clocks).
        return entry.getSizeBytes() == FileCacheEntry.UNKNOWN_SIZE
                || entry.getSizeBytes() == currentSize;
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

    private FileCacheEntry loadEntry(Path path, long lastModified, long sizeBytes) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String text = decodeUtf8Strict(bytes, path);
        Document document = Document.parse(text);
        return new FileCacheEntry(
                path.toAbsolutePath().toString(),
                document,
                lastModified,
                System.currentTimeMillis(),
                sizeBytes);
    }

    /**
     * Decodes bytes as UTF-8 with no lossy substitution.
     *
     * <p>A leading BOM is left in the decoded text (it becomes {@code U+FEFF}) so that
     * {@link Document#parse(String)} can record it as a flag; every other invalid sequence
     * aborts the read with an {@link UnsupportedFileEncodingException} carrying the byte
     * offset at which decoding stopped.
     *
     * @param bytes the raw file bytes.
     * @param path  the file path, used for diagnostics only.
     * @return the decoded text.
     * @throws UnsupportedFileEncodingException if the bytes are not valid UTF-8.
     */
    private static String decodeUtf8Strict(byte[] bytes, Path path) throws IOException {
        java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        try {
            return decoder.decode(buffer).toString();
        } catch (CharacterCodingException e) {
            throw new UnsupportedFileEncodingException(
                    path.toAbsolutePath().toString(), buffer.position(), e);
        }
    }

    private static String toKey(Path path) {
        return path.toAbsolutePath().toString();
    }
}
