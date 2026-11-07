package com.neel.syntaxvalidation.io;

import com.neel.syntaxvalidation.modification.Document;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Writes source files strictly as UTF-8 and atomically.
 *
 * <p><b>Why this exists.</b> The usual consumer idiom &mdash;
 * {@code Files.writeString(path, content)} &mdash; opens the target with
 * {@code TRUNCATE_EXISTING} and writes into it in place: if the process dies, the disk
 * fills up or an encoder trips halfway through, the original file is already destroyed
 * and a half-written file remains. It also replaces unmappable characters (an unpaired
 * surrogate) with {@code '?'} silently. This writer removes both failure modes:
 *
 * <ol>
 *   <li><b>Strict encoding.</b> Text is encoded to UTF-8 up front with
 *       {@link CodingErrorAction#REPORT}; if any character cannot be encoded, an
 *       {@link UnmappableTextException} is thrown and the target is left untouched.</li>
 *   <li><b>Atomic replace.</b> The bytes go into a fresh sibling temp file
 *       ({@code CREATE_NEW}), which is then moved onto the target with
 *       {@link StandardCopyOption#ATOMIC_MOVE} (falling back to a plain replace only on
 *       filesystems that cannot move atomically). Readers therefore see either the whole
 *       old file or the whole new one &mdash; never a partial file.</li>
 * </ol>
 *
 * <p>The text is written verbatim: whatever line endings and leading BOM the caller put
 * in the string are what land on disk. Pair this writer with {@link Document#render()},
 * which reproduces the original file's exact byte layout outside the edited region, to
 * keep an edit round-trip lossless.
 *
 * <p>Instances are stateless and thread-safe; concurrent writes to the same path each
 * land as a complete file (last move wins).
 */
public final class SourceFileWriter {

    /** The only charset this writer accepts or produces: UTF-8, always explicit. */
    public static final java.nio.charset.Charset CHARSET = StandardCharsets.UTF_8;

    private static final String TMP_INFIX = ".tmp-write-";

    /**
     * Writes {@code content} to {@code target} as UTF-8, atomically replacing it.
     *
     * @param target  the file to write; its parent directory must already exist.
     * @param content the exact text to store (line endings and BOM included).
     * @return the outcome of the write.
     * @throws UnmappableTextException if {@code content} cannot be encoded as UTF-8
     *         (nothing is written in that case).
     * @throws IOException if the parent directory is missing, the target is a directory,
     *         the file is locked by another process, or the move fails.
     */
    public WriteResult write(Path target, String content) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");

        // Encode first: a bad character must fail before anything touches the filesystem.
        byte[] bytes = encodeStrict(target, content);

        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Cannot write '" + absolute + "': its parent directory does not exist.");
        }
        if (Files.isDirectory(absolute)) {
            throw new DirectoryNotEmptyException(
                    "Cannot write '" + absolute + "': a directory with that name exists.");
        }

        Path temp = parent.resolve(absolute.getFileName() + TMP_INFIX + UUID.randomUUID());
        try {
            Files.write(temp, bytes, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
            try {
                Files.move(temp, absolute,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Filesystem without atomic-move support (some network / FUSE mounts):
                // still replace in one step rather than truncating the original first.
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IOException("Failed to write '" + absolute + "': " + e.getMessage(), e);
        } finally {
            deleteQuietly(temp);
        }

        return new WriteResult(absolute, bytes.length, Document.parse(content).size());
    }

    /**
     * Writes a {@link Document} exactly as it appears on disk (BOM included when the
     * document was parsed from a BOM-prefixed file).
     *
     * @param target   the file to write.
     * @param document the document to persist; must not be {@code null}.
     * @return the outcome of the write.
     * @throws UnmappableTextException if the text cannot be encoded as UTF-8.
     * @throws IOException             on any filesystem failure.
     */
    public WriteResult writeDocument(Path target, Document document) throws IOException {
        Objects.requireNonNull(document, "document");
        return write(target, document.render());
    }

    /**
     * Encodes text as UTF-8 without lossy substitution.
     *
     * @param content the text to encode.
     * @return the exact UTF-8 bytes.
     * @throws UnmappableTextException if some character has no UTF-8 mapping.
     */
    public static byte[] encodeStrict(String content) throws UnmappableTextException {
        return encodeStrict(null, content);
    }

    private static byte[] encodeStrict(Path target, String content) throws UnmappableTextException {
        CharBuffer input = CharBuffer.wrap(content);
        try {
            ByteBuffer encoded = CHARSET.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(input);
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new UnmappableTextException(
                    target == null ? null : target.toAbsolutePath().toString(), input.position(), e);
        }
    }

    private static void deleteQuietly(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // Best effort: a leftover temp file must never mask the write result.
        }
    }
}
