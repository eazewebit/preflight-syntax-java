package com.neel.syntaxvalidation.io;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Outcome of a successful, atomic file write.
 *
 * @param path         the absolute path that was written.
 * @param bytesWritten the exact number of UTF-8 bytes now stored in the file.
 * @param lineCount    the number of lines in the written text.
 */
public record WriteResult(Path path, long bytesWritten, int lineCount) {

    /**
     * Creates a write result.
     *
     * @param path         absolute path written; must not be {@code null}.
     * @param bytesWritten byte count written; must be {@code >= 0}.
     * @param lineCount    number of lines written; must be {@code >= 0}.
     */
    public WriteResult {
        Objects.requireNonNull(path, "path");
        if (bytesWritten < 0) {
            throw new IllegalArgumentException("bytesWritten must be >= 0, but was " + bytesWritten);
        }
        if (lineCount < 0) {
            throw new IllegalArgumentException("lineCount must be >= 0, but was " + lineCount);
        }
    }

    @Override
    public String toString() {
        return "WriteResult{path=" + path + ", bytes=" + bytesWritten + ", lines=" + lineCount + "}";
    }
}
