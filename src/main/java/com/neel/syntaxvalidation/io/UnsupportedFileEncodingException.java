package com.neel.syntaxvalidation.io;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Thrown when a file's bytes are not valid UTF-8.
 *
 * <p>This library reads and writes source files strictly as UTF-8 and never decodes
 * with a lossy fallback: silently mapping invalid bytes to {@code U+FFFD} would make
 * an edit rewrite bytes the caller never intended to touch, corrupting the file on
 * write-back. Instead the read fails loudly, naming the path and the exact byte
 * offset where the malformed sequence starts.
 */
public class UnsupportedFileEncodingException extends IOException {

    private static final long serialVersionUID = 1L;

    private final String path;
    private final long byteOffset;

    /**
     * @param path       the file that could not be decoded.
     * @param byteOffset 0-based offset of the malformed byte sequence, or {@code -1}
     *                   when unknown.
     * @param cause      the underlying decoding failure.
     */
    public UnsupportedFileEncodingException(String path, long byteOffset, Throwable cause) {
        super(buildMessage(path, byteOffset), cause);
        this.path = path;
        this.byteOffset = byteOffset;
    }

    private static String buildMessage(String path, long byteOffset) {
        StringBuilder sb = new StringBuilder("File '").append(path)
                .append("' is not valid UTF-8");
        if (byteOffset >= 0) {
            sb.append(" (malformed byte sequence at byte offset ").append(byteOffset).append(')');
        }
        sb.append(". This library reads and writes source files as UTF-8 only; ")
          .append("convert the file to UTF-8 before editing it.");
        return sb.toString();
    }

    /** @return the path of the file that failed to decode. */
    public String getPath() {
        return path;
    }

    /** @return the byte offset of the malformed sequence, or {@code -1} when unknown. */
    public long getByteOffset() {
        return byteOffset;
    }

    /** Convenience factory that extracts the offset context from a decode failure. */
    public static UnsupportedFileEncodingException forPath(Path path, Throwable cause) {
        return new UnsupportedFileEncodingException(path.toAbsolutePath().toString(), -1L, cause);
    }
}
