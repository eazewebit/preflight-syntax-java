package com.neel.syntaxvalidation;

import com.neel.syntaxvalidation.cache.FileCache;
import com.neel.syntaxvalidation.cache.FileCacheEntry;
import com.neel.syntaxvalidation.io.UnmappableTextException;
import com.neel.syntaxvalidation.io.UnsupportedFileEncodingException;
import com.neel.syntaxvalidation.model.ModificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for byte-level edit fidelity: what the library renders and writes must
 * differ from the original file <em>only</em> inside the edited line range.
 *
 * <p>Every test works on real files written to a temporary directory, comparing raw bytes
 * (never a re-normalized string), because each defect below produced "the edit changed lines
 * I did not touch" symptoms that only byte comparison can detect:
 *
 * <ol>
 *   <li><b>Mixed line endings rewritten.</b> Split-and-rejoin with one dominant separator
 *       turned a lone LF inside a CRLF file into CRLF.</li>
 *   <li><b>CR-only files converted to LF</b> wholesale.</li>
 *   <li><b>UTF-8 BOM leaked into content</b>, polluting line&nbsp;1 and breaking position-0
 *       detection such as {@code <!DOCTYPE>}.</li>
 *   <li><b>No write path / lossy writes.</b> Consumers hand-rolled
 *       {@code Files.writeString}, which truncates in place (partial file on failure) and
 *       silently replaces unmappable characters with '?'.</li>
 *   <li><b>Non-UTF-8 files</b> failed with an opaque "Input length = 2" message.</li>
 * </ol>
 */
class EncodingFidelityRegressionTest {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;

    private final SyntaxValidationLibrary library = new SyntaxValidationLibrary();

    // ==================================================================
    //  1. Per-line terminator fidelity
    // ==================================================================

    @Test
    @DisplayName("Mixed endings: editing one line never rewrites another line's lone LF")
    void mixedEndings_untouchedLinesKeepTheirOwnTerminator() throws IOException {
        byte[] original = bytes("a\r\nb\nc\r\n");
        Path file = write("mixed.js", original);

        String modified = library.getModifiedContent(replace(file, 3, 3, "C"));

        // Line 2 keeps its lone LF; only line 3's content changed.
        assertThat(modified).isEqualTo("a\r\nb\nC\r\n");
        assertThat(count(modified, '\r')).isEqualTo(2);
        assertThat(count(modified, '\n')).isEqualTo(3);
    }

    @Test
    @DisplayName("Mixed endings: the untouched prefix is byte-for-byte identical")
    void mixedEndings_untouchedPrefixIsByteIdentical() throws IOException {
        byte[] original = bytes("l1\r\nl2\nl3\r\nl4\nl5\r\n");
        Path file = write("mixed-batch.js", original);

        String modified = library.getModifiedContent(replace(file, 4, 4, "L4"));

        assertThat(modified).isEqualTo("l1\r\nl2\nl3\r\nL4\nl5\r\n");
    }

    @Test
    @DisplayName("CR-only file stays CR-only after an edit (no wholesale LF conversion)")
    void crOnlyFile_keepsCarriageReturnTerminators() throws IOException {
        byte[] original = bytes("a\rb\r");
        Path file = write("cr.js", original);

        String modified = library.getModifiedContent(replace(file, 1, 1, "A"));

        assertThat(modified).isEqualTo("A\rb\r");
    }

    @Test
    @DisplayName("Inserted lines adopt the dominant separator; boundary terminator is inherited")
    void insertedLines_useDominantSeparatorAndInheritBoundary() throws IOException {
        // Dominant separator is CRLF; file has no trailing newline.
        byte[] original = bytes("a\r\nb");
        Path file = write("insert.js", original);

        String modified = library.getModifiedContent(replace(file, 1, 1, "X1\nX2"));

        assertThat(modified).isEqualTo("X1\r\nX2\r\nb");
    }

    // ==================================================================
    //  2. UTF-8 BOM handling
    // ==================================================================

    @Test
    @DisplayName("BOM is stripped from line 1 but re-emitted on render")
    void bom_strippedFromLines_reEmittedOnRender() throws IOException {
        byte[] original = concat(UTF8_BOM, bytes("<?php\n$x = 1;\n"));
        Path file = write("bom.php", original);

        FileCacheEntry entry = new FileCache().getOrLoad(file);
        assertThat(entry.hasLeadingBom()).isTrue();
        assertThat(entry.getLines().get(0)).isEqualTo("<?php");   // no U+FEFF prefix
        assertThat(entry.getLineCount()).isEqualTo(2);

        String modified = library.getModifiedContent(replace(file, 2, 2, "$x = 2;"));

        assertThat(modified).startsWith("\uFEFF");
        assertThat(modified.substring(1)).isEqualTo("<?php\n$x = 2;\n");
    }

    @Test
    @DisplayName("BOM-prefixed HTML keeps a clean line 1 so position-0 detection works")
    void bom_htmlDoctypeStillDetectedAtOffsetZero() throws IOException {
        byte[] original = concat(UTF8_BOM, bytes("<!DOCTYPE html>\n<html></html>\n"));
        Path file = write("bom.html", original);

        FileCacheEntry entry = new FileCache().getOrLoad(file);

        assertThat(entry.getLines().get(0)).startsWith("<!DOCTYPE");
    }

    @Test
    @DisplayName("BOM survives a real write: bytes on disk keep the BOM and the edit")
    void bom_survivesWriteToDisk() throws IOException {
        byte[] original = concat(UTF8_BOM, bytes("a\nb\n"));
        Path file = write("bom-write.txt", original);

        library.applyModification(replace(file, 1, 1, "A"));

        assertThat(Files.readAllBytes(file))
                .isEqualTo(concat(UTF8_BOM, bytes("A\nb\n")));
    }

    // ==================================================================
    //  3. Strict UTF-8 read / write
    // ==================================================================

    @Test
    @DisplayName("Non-UTF-8 file fails loudly with a clear message and byte offset")
    void nonUtf8File_failsWithClearEncodingMessage() throws IOException {
        Path file = write("cp1252.txt", new byte[]{'c', 'a', 'f', (byte) 0xE9, '\n'});

        assertThatThrownBy(() -> new FileCache().getOrLoad(file))
                .isInstanceOf(UnsupportedFileEncodingException.class)
                .hasMessageContaining("not valid UTF-8")
                .hasMessageContaining("byte offset 3");

        assertThatThrownBy(() -> library.getModifiedContent(replace(file, 1, 1, "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    @DisplayName("Unmappable text (unpaired surrogate) is refused; the original file stays intact")
    void unmappableText_isRefusedWithoutTouchingTheOriginal() throws IOException {
        byte[] original = bytes("ok\nline\n");
        Path file = write("surrogate.txt", original);

        ModificationRequest request = replace(file, 2, 2, "bad\uD800text");

        assertThatThrownBy(() -> library.applyModification(request))
                .isInstanceOf(UnmappableTextException.class)
                .hasMessageContaining("cannot be encoded as UTF-8")
                .hasMessageContaining("Nothing was written");

        // No partial write, no '?' corruption.
        assertThat(Files.readAllBytes(file)).isEqualTo(original);
    }

    @Test
    @DisplayName("Multi-byte and astral-plane characters round-trip byte-for-byte")
    void unicodeContent_roundTripsByteForByte() throws IOException {
        byte[] original = bytes("h\u00E9llo w\u00F6rld \uD83C\uDF89\r\nx\r\n");
        Path file = write("unicode.txt", original);

        library.applyModification(replace(file, 2, 2, "y"));

        assertThat(Files.readAllBytes(file)).isEqualTo(bytes("h\u00E9llo w\u00F6rld \uD83C\uDF89\r\ny\r\n"));
    }

    // ==================================================================
    //  4. Atomic write behaviour
    // ==================================================================

    @Test
    @DisplayName("applyModification writes atomically: no temp files are left behind")
    void applyModification_leavesNoTempFiles() throws IOException {
        Path file = write("atomic.txt", bytes("a\nb\nc\n"));

        library.applyModification(replace(file, 2, 2, "B"));

        assertThat(Files.readAllBytes(file)).isEqualTo(bytes("a\nB\nc\n"));
        try (Stream<Path> entries = Files.list(tempDir)) {
            assertThat(entries.filter(p -> p.getFileName().toString().contains(".tmp-write-")))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("A file without a trailing newline stays without one after an on-disk edit")
    void applyModification_preservesMissingTrailingNewline() throws IOException {
        Path file = write("no-trail.txt", bytes("p\r\nq"));

        library.applyModification(replace(file, 1, 1, "P"));

        assertThat(Files.readAllBytes(file)).isEqualTo(bytes("P\r\nq"));
    }

    @Test
    @DisplayName("Cache is invalidated by a write: the next read sees the new bytes")
    void applyModification_invalidatesCacheSoNextReadIsFresh() throws IOException {
        Path file = write("cache.txt", bytes("one\ntwo\n"));

        library.applyModification(replace(file, 1, 1, "ONE"));
        FileCacheEntry afterWrite = new FileCache().getOrLoad(file);
        assertThat(afterWrite.getLines()).containsExactly("ONE", "two");

        // A second edit must apply to the freshly written content, not a stale copy.
        library.applyModification(replace(file, 2, 2, "TWO"));
        assertThat(Files.readAllBytes(file)).isEqualTo(bytes("ONE\nTWO\n"));
    }

    @Test
    @DisplayName("Writing into a missing directory fails with a clear message")
    void applyModification_intoMissingDirectoryFails() throws IOException {
        Path file = tempDir.resolve("does-not-exist").resolve("f.txt");
        Files.write(tempDir.resolve("seed.txt"), bytes("x\n"));

        assertThatThrownBy(() -> library.getFileWriter().write(file, "content"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("parent directory does not exist");
    }

    // ==================================================================
    //  5. Full-layout round-trip matrix (no edit => byte identical)
    // ==================================================================

    @Test
    @DisplayName("Round-trip matrix: read + render reproduces every layout byte-for-byte")
    void roundTripMatrix_isByteIdenticalForEveryLayout() throws IOException {
        String[][] layouts = {
                {"lf", "a\nb\nc\n"},
                {"lf-no-final", "a\nb\nc"},
                {"crlf", "a\r\nb\r\nc\r\n"},
                {"crlf-no-final", "a\r\nb\r\nc"},
                {"cr", "a\rb\rc\r"},
                {"mixed-crlf-lf", "a\r\nb\nc\r\n"},
                {"mixed-lf-cr", "a\nb\rc\n"},
                {"blank-lines", "a\n\n\nb\n"},
                {"only-newline", "\n"},
                {"single-char-no-eol", "x"},
                {"empty", ""},
        };

        FileCache cache = new FileCache();
        for (String[] layout : layouts) {
            Path file = write("layout-" + layout[0] + ".txt", bytes(layout[1]));
            FileCacheEntry entry = cache.getOrLoad(file);
            assertThat(entry.getDocument().render())
                    .as("layout %s must render byte-identically", layout[0])
                    .isEqualTo(layout[1]);
        }
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private static ModificationRequest replace(Path file, int fromLine, int toLine, String replacement) {
        return ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(fromLine)
                .toLine(toLine)
                .replacement(replacement)
                .build();
    }

    private Path write(String name, byte[] content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] out = new byte[head.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }

    private static int count(String text, char target) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) {
                total++;
            }
        }
        return total;
    }
}
