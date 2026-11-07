package com.neel.syntaxvalidation.cache;

import com.neel.syntaxvalidation.modification.Document;
import com.neel.syntaxvalidation.modification.LineEnding;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a file held in the in-memory {@link FileCache}.
 *
 * <p>The authoritative state is a byte-faithful {@link Document}: terminator-free lines
 * plus each line's original terminator, a leading-BOM flag and the coarse
 * {@link LineEnding} convention derived from them. Alongside these it records the path,
 * the file-system last-modified timestamp and size (used for staleness detection) and
 * the wall-clock instants at which the entry was loaded and last accessed.
 *
 * <p>Keeping per-line terminators is what lets a read &rarr; modify &rarr; write round-trip
 * reproduce every byte outside an edited region: mixed CRLF/LF endings, CR-only files,
 * a missing final newline and a leading UTF-8 BOM all survive untouched.
 */
public final class FileCacheEntry {

    /** Sentinel for "size on disk unknown" (entries built from in-memory lines). */
    public static final long UNKNOWN_SIZE = -1L;

    private final String absolutePath;
    private final Document document;
    private final LineEnding lineEnding;
    private final long lastModified;
    private final long sizeBytes;
    private final long loadedAt;
    private volatile long lastAccessed;

    /**
     * Full canonical constructor.
     *
     * @param absolutePath the absolute path of the file.
     * @param document     the byte-faithful document (lines + terminators + BOM flag).
     * @param lastModified the file-system last-modified timestamp in milliseconds.
     * @param loadedAt     the wall-clock instant (millis) at which the entry was created.
     * @param sizeBytes    the file size in bytes captured at load time, or
     *                     {@link #UNKNOWN_SIZE} when not known.
     */
    public FileCacheEntry(String absolutePath, Document document, long lastModified, long loadedAt,
                          long sizeBytes) {
        this.absolutePath = Objects.requireNonNull(absolutePath, "absolutePath");
        this.document = Objects.requireNonNull(document, "document");
        this.lineEnding = document.lineEnding();
        this.lastModified = lastModified;
        this.loadedAt = loadedAt;
        this.sizeBytes = sizeBytes;
        this.lastAccessed = loadedAt;
    }

    /**
     * Convenience constructor that synthesizes a uniform-layout document from lines and
     * their shared convention.
     *
     * @param absolutePath the absolute path of the file.
     * @param lines        the file's lines (without terminators); a defensive copy is taken.
     * @param lastModified the file-system last-modified timestamp in milliseconds.
     * @param loadedAt     the wall-clock instant (millis) at which the entry was created.
     * @param lineEnding   the detected line-ending convention; must not be {@code null}.
     */
    public FileCacheEntry(String absolutePath, List<String> lines, long lastModified, long loadedAt,
                          LineEnding lineEnding) {
        this(absolutePath, Document.of(lines, lineEnding), lastModified, loadedAt, UNKNOWN_SIZE);
    }

    /**
     * Creates an entry with a default LF convention and no trailing newline.
     *
     * @param absolutePath the absolute path of the file.
     * @param lines        the file's lines (without terminators); a defensive copy is taken.
     * @param lastModified the file-system last-modified timestamp in milliseconds.
     * @param loadedAt     the wall-clock instant (millis) at which the entry was created.
     */
    public FileCacheEntry(String absolutePath, List<String> lines, long lastModified, long loadedAt) {
        this(absolutePath, lines, lastModified, loadedAt, new LineEnding(LineEnding.LF, false));
    }

    /** @return the absolute path of the cached file. */
    public String getAbsolutePath() {
        return absolutePath;
    }

    /**
     * @return the byte-faithful document (lines + per-line terminators + BOM flag).
     *         This is the authoritative content representation for edit round-trips.
     */
    public Document getDocument() {
        return document;
    }

    /** @return an unmodifiable view of the file's lines (without terminators or BOM). */
    public List<String> getLines() {
        return document.lines();
    }

    /** @return the file's content reconstructed with {@code \n} line separators. */
    public String getContent() {
        return String.join("\n", document.lines());
    }

    /**
     * @return the original file's coarse line-ending convention (dominant separator +
     *         trailing-newline flag). Prefer {@link #getDocument()}'s rendering, which also
     *         preserves mixed endings; joining through this value normalizes them.
     */
    public LineEnding getLineEnding() {
        return lineEnding;
    }

    /** @return the number of lines in the file. */
    public int getLineCount() {
        return document.size();
    }

    /** @return {@code true} when the original file started with a UTF-8 BOM. */
    public boolean hasLeadingBom() {
        return document.hasLeadingBom();
    }

    /** @return the file-system last-modified timestamp captured at load time (millis). */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * @return the file size in bytes captured at load time, or {@link #UNKNOWN_SIZE}
     *         when the entry was built without one.
     */
    public long getSizeBytes() {
        return sizeBytes;
    }

    /** @return the wall-clock instant at which the entry was created (millis). */
    public long getLoadedAt() {
        return loadedAt;
    }

    /** @return the wall-clock instant at which the entry was last accessed (millis). */
    public long getLastAccessed() {
        return lastAccessed;
    }

    /** Updates the last-accessed timestamp to the current time. */
    public void touch() {
        this.lastAccessed = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileCacheEntry)) {
            return false;
        }
        FileCacheEntry that = (FileCacheEntry) o;
        return lastModified == that.lastModified
                && Objects.equals(absolutePath, that.absolutePath)
                && document.lines().equals(that.document.lines());
    }

    @Override
    public int hashCode() {
        return Objects.hash(absolutePath, document.lines(), lastModified);
    }

    @Override
    public String toString() {
        return "FileCacheEntry{path='" + absolutePath + "', lines=" + document.size()
                + ", lastModified=" + lastModified + ", bom=" + document.hasLeadingBom()
                + ", lineEnding=" + lineEnding + "}";
    }
}
