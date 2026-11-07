package com.neel.syntaxvalidation;

import com.neel.syntaxvalidation.cache.FileCache;
import com.neel.syntaxvalidation.cache.FileCacheEntry;
import com.neel.syntaxvalidation.model.BatchModificationRequest;
import com.neel.syntaxvalidation.model.LineReplacement;
import com.neel.syntaxvalidation.model.ModificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end regression tests for two real defects found in audit, each exercised
 * against genuine files written to a temporary directory (never mocked content).
 *
 * <ol>
 *   <li><b>Silent append on out-of-bounds {@code fromLine}.</b> A fully out-of-bounds
 *       start line used to be appended as a new trailing line and reported as success,
 *       letting hallucinated/stale line numbers silently corrupt files. It must now fail
 *       loudly. (A {@code toLine} past EOF stays a graceful clamp.)</li>
 *   <li><b>CRLF &rarr; LF normalization + stripped trailing newline.</b> Every replace
 *       rewrote all line endings to LF and dropped the final newline, producing huge diffs
 *       in CRLF projects. Line-ending style and the trailing-newline flag must be preserved.</li>
 * </ol>
 */
class LineRangeDefectRegressionTest {

    @TempDir
    Path tempDir;

    private final SyntaxValidationLibrary library = new SyntaxValidationLibrary();

    // ==================================================================
    //  Defect 1: fully out-of-bounds fromLine must error, not append
    // ==================================================================

    @Test
    @DisplayName("Defect 1: single replace with far-out-of-bounds fromLine throws instead of appending")
    void outOfBoundsFromLine_throwsInsteadOfAppending() throws IOException {
        Path file = writeFileBytes("app.js", "line one\nline two\nline three\n");

        ModificationRequest request = ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(99)
                .toLine(99)
                .replacement("GHOST")
                .build();

        assertThatThrownBy(() -> library.getModifiedContent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of bounds");

        // The file on disk must be untouched.
        assertThat(readBytes(file)).isEqualTo("line one\nline two\nline three\n");
    }

    @Test
    @DisplayName("Defect 1: batch replace with an out-of-bounds entry throws")
    void outOfBoundsFromLine_batchThrows() throws IOException {
        Path file = writeFileBytes("app.js", "a\nb\nc\n");

        LineReplacement valid = LineReplacement.builder()
                .fromLine(2).toLine(2).replacement("B").build();
        LineReplacement ghost = LineReplacement.builder()
                .fromLine(42).toLine(42).replacement("GHOST").build();

        BatchModificationRequest batch = BatchModificationRequest.builder()
                .filePath(file.toString())
                .addReplacement(valid)
                .addReplacement(ghost)
                .build();

        assertThatThrownBy(() -> library.getModifiedContent(batch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of bounds");
    }

    @Test
    @DisplayName("Defect 1: fromLine == last line is valid; toLine past EOF still clamps")
    void boundaryFromLineValid_toLineClamped() throws IOException {
        Path file = writeFileBytes("app.js", "a\nb\nc\n");

        // Replacing the very last line (fromLine == size) is legal.
        ModificationRequest replaceLast = ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(3).toLine(3).replacement("C")
                .build();
        assertThat(library.getModifiedContent(replaceLast)).isEqualTo("a\nb\nC\n");

        // toLine past EOF remains a graceful clamp (removes the tail from fromLine on).
        ModificationRequest clamp = ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(2).toLine(100).replacement("TAIL")
                .build();
        assertThat(library.getModifiedContent(clamp)).isEqualTo("a\nTAIL\n");
    }

    // ==================================================================
    //  Defect 2: preserve CRLF style and trailing newline on real files
    // ==================================================================

    @Test
    @DisplayName("Defect 2: editing one line of a CRLF file keeps every other line's CRLF + trailing newline")
    void crlfFile_singleEdit_preservesEndingsAndTrailingNewline() throws IOException {
        String original = "const a = 1;\r\nconst b = 2;\r\nconst c = 3;\r\n";
        Path file = writeFileBytes("crlf.js", original);

        ModificationRequest request = ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(2).toLine(2).replacement("const b = 99;")
                .build();

        String modified = library.getModifiedContent(request);

        // Only line 2 changed; CRLF endings and the trailing newline are preserved.
        assertThat(modified)
                .isEqualTo("const a = 1;\r\nconst b = 99;\r\nconst c = 3;\r\n");
        assertThat(countOccurrences(modified, '\r')).isEqualTo(3);
        assertThat(countOccurrences(modified, '\n')).isEqualTo(3);

        // The untouched neighbours are byte-for-byte identical to the originals.
        assertThat(modified).contains("const a = 1;\r\n");
        assertThat(modified).contains("\r\nconst c = 3;\r\n");
    }

    @Test
    @DisplayName("Defect 2: batch edit of a CRLF file preserves endings and trailing newline")
    void crlfFile_batchEdit_preservesEndingsAndTrailingNewline() throws IOException {
        String original = "l1\r\nl2\r\nl3\r\nl4\r\n";
        Path file = writeFileBytes("crlf-batch.js", original);

        LineReplacement r1 = LineReplacement.builder().fromLine(1).toLine(1).replacement("A").build();
        LineReplacement r2 = LineReplacement.builder().fromLine(3).toLine(3).replacement("C").build();

        BatchModificationRequest batch = BatchModificationRequest.builder()
                .filePath(file.toString())
                .addReplacement(r1)
                .addReplacement(r2)
                .build();

        String modified = library.getModifiedContent(batch);

        assertThat(modified).isEqualTo("A\r\nl2\r\nC\r\nl4\r\n");
        assertThat(countOccurrences(modified, '\r')).isEqualTo(4);
    }

    @Test
    @DisplayName("Defect 2: LF file keeps LF and trailing newline (no regression)")
    void lfFile_preservesLfAndTrailingNewline() throws IOException {
        String original = "x\ny\nz\n";
        Path file = writeFileBytes("lf.js", original);

        ModificationRequest request = ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(2).toLine(2).replacement("Y")
                .build();

        assertThat(library.getModifiedContent(request)).isEqualTo("x\nY\nz\n");
    }

    @Test
    @DisplayName("Defect 2: file without a trailing newline stays without one after edit")
    void noTrailingNewline_staysAbsentAfterEdit() throws IOException {
        String original = "p\r\nq"; // CRLF, NO trailing terminator
        Path file = writeFileBytes("no-trail.js", original);

        ModificationRequest request = ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(1).toLine(1).replacement("P")
                .build();

        String modified = library.getModifiedContent(request);

        assertThat(modified).isEqualTo("P\r\nq");
        assertThat(modified.endsWith("\n")).isFalse();
    }

    @Test
    @DisplayName("Defect 2: FileCache detects CRLF + trailing-newline convention from a real file")
    void fileCache_detectsConventionFromRealFile() throws IOException {
        Path crlf = writeFileBytes("detect-crlf.js", "a\r\nb\r\n");
        Path lfNoTrail = writeFileBytes("detect-lf.js", "a\nb");

        FileCache cache = new FileCache();

        FileCacheEntry crlfEntry = cache.getOrLoad(crlf);
        assertThat(crlfEntry.getLineEnding().separator()).isEqualTo("\r\n");
        assertThat(crlfEntry.getLineEnding().hasTrailingNewline()).isTrue();
        // Lines are still terminator-free.
        assertThat(crlfEntry.getLines()).containsExactly("a", "b");

        FileCacheEntry lfEntry = cache.getOrLoad(lfNoTrail);
        assertThat(lfEntry.getLineEnding().separator()).isEqualTo("\n");
        assertThat(lfEntry.getLineEnding().hasTrailingNewline()).isFalse();
    }

    // ==================================================================
    //  Helpers: write/read raw bytes so line endings are exactly controlled
    // ==================================================================

    private Path writeFileBytes(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String readBytes(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
