package com.neel.syntaxvalidation.io;

import java.io.IOException;

/**
 * Thrown when text cannot be encoded as UTF-8 and therefore must not be written.
 *
 * <p>The JDK's convenience writers replace unmappable characters (such as an unpaired
 * surrogate) with {@code '?'} without any warning, silently destroying source content.
 * This library encodes strictly instead: nothing is written unless every character maps
 * to a valid UTF-8 sequence, and this exception reports where the offending character is.
 */
public class UnmappableTextException extends IOException {

    private static final long serialVersionUID = 1L;

    private final String path;
    private final int charOffset;

    /**
     * @param path       the file that was about to be written (may be {@code null}).
     * @param charOffset 0-based character offset of the unmappable text, or {@code -1}
     *                   when unknown.
     * @param cause      the underlying encoding failure.
     */
    public UnmappableTextException(String path, int charOffset, Throwable cause) {
        super(buildMessage(path, charOffset), cause);
        this.path = path;
        this.charOffset = charOffset;
    }

    private static String buildMessage(String path, int charOffset) {
        StringBuilder sb = new StringBuilder("Text cannot be encoded as UTF-8");
        if (path != null) {
            sb.append(" for file '").append(path).append('\'');
        }
        if (charOffset >= 0) {
            sb.append(" (unmappable character at char offset ").append(charOffset).append(')');
        }
        sb.append(". Nothing was written; refusing to substitute lossy '?' characters. ")
          .append("The text most likely contains an unpaired surrogate.");
        return sb.toString();
    }

    /** @return the path that was about to be written, or {@code null} when unknown. */
    public String getPath() {
        return path;
    }

    /** @return the character offset of the unmappable text, or {@code -1} when unknown. */
    public int getCharOffset() {
        return charOffset;
    }
}
