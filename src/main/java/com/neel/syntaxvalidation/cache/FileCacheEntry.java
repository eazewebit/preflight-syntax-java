package com.neel.syntaxvalidation.cache;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a file held in the in-memory {@link FileCache}.
 *
 * <p>It stores the file's lines (without line terminators), its absolute path,
 * the file-system last-modified timestamp captured at load time, and the
 * wall-clock instant at which the entry was created. The last-modified value is
 * used to detect staleness so that the cache can transparently reload a file
 * that changed on disk.
 */
public final class FileCacheEntry {

    private final String absolutePath;
    private final List<String> lines;
    private final long lastModified;
    private final long loadedAt;

    /**
     * @param absolutePath the absolute path of the file.
     * @param lines        the file's lines (without terminators); a defensive copy is taken.
     * @param lastModified the file-system last-modified timestamp in milliseconds.
     * @param loadedAt     the wall-clock instant (millis) at which the entry was created.
     */
    public FileCacheEntry(String absolutePath, List<String> lines, long lastModified, long loadedAt) {
        this.absolutePath = Objects.requireNonNull(absolutePath, "absolutePath");
        this.lines = List.copyOf(lines);
        this.lastModified = lastModified;
        this.loadedAt = loadedAt;
    }

    /** @return the absolute path of the cached file. */
    public String getAbsolutePath() {
        return absolutePath;
    }

    /** @return an unmodifiable view of the file's lines (without terminators). */
    public List<String> getLines() {
        return lines;
    }

    /** @return the file's content reconstructed with {@code \n} line separators. */
    public String getContent() {
        return String.join("\n", lines);
    }

    /** @return the number of lines in the file. */
    public int getLineCount() {
        return lines.size();
    }

    /** @return the file-system last-modified timestamp captured at load time (millis). */
    public long getLastModified() {
        return lastModified;
    }

    /** @return the wall-clock instant at which the entry was created (millis). */
    public long getLoadedAt() {
        return loadedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileCacheEntry that)) {
            return false;
        }
        return lastModified == that.lastModified
                && Objects.equals(absolutePath, that.absolutePath)
                && Objects.equals(lines, that.lines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(absolutePath, lines, lastModified);
    }

    @Override
    public String toString() {
        return "FileCacheEntry{path='" + absolutePath + "', lines=" + lines.size()
                + ", lastModified=" + lastModified + "}";
    }
}
