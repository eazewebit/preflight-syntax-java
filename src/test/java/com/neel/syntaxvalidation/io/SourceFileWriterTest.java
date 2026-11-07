package com.neel.syntaxvalidation.io;

import com.neel.syntaxvalidation.modification.Document;
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
 * Unit tests for {@link SourceFileWriter}: strict UTF-8 encoding (never a lossy '?') and
 * atomic replacement (the target is only ever replaced by a complete file).
 */
@DisplayName("SourceFileWriter (strict UTF-8, atomic replace)")
class SourceFileWriterTest {

    @TempDir
    Path tempDir;

    private final SourceFileWriter writer = new SourceFileWriter();

    @Test
    @DisplayName("Writes exactly the given text as UTF-8, preserving line endings verbatim")
    void write_preservesTextVerbatim() throws IOException {
        Path target = tempDir.resolve("verbatim.txt");
        String content = "h\u00E9llo \uD83C\uDF89\r\nsecond\n";

        writer.write(target, content);

        assertThat(Files.readAllBytes(target)).isEqualTo(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Replaces an existing file completely (no leftover tail from the old bytes)")
    void write_replacesExistingFileCompletely() throws IOException {
        Path target = tempDir.resolve("replace.txt");
        Files.write(target, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n".getBytes(StandardCharsets.UTF_8));

        writer.write(target, "short\n");

        assertThat(Files.readAllBytes(target)).isEqualTo("short\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Refuses unmappable text (unpaired surrogate) and leaves the target untouched")
    void write_refusesUnmappableText() throws IOException {
        Path target = tempDir.resolve("strict.txt");
        byte[] original = "original\n".getBytes(StandardCharsets.UTF_8);
        Files.write(target, original);

        assertThatThrownBy(() -> writer.write(target, "ok\uD800broken"))
                .isInstanceOf(UnmappableTextException.class)
                .hasMessageContaining("unmappable character at char offset 2");

        assertThat(Files.readAllBytes(target)).isEqualTo(original);
    }

    @Test
    @DisplayName("No temp files are left behind, on success or after a refused write")
    void write_leavesNoTempFiles() throws IOException {
        Path target = tempDir.resolve("clean.txt");
        writer.write(target, "fine\n");
        try {
            writer.write(target, "bad\uD800");
        } catch (UnmappableTextException expected) {
            // refused by the strict encoder
        }

        try (Stream<Path> entries = Files.list(tempDir)) {
            assertThat(entries.filter(p -> p.getFileName().toString().contains(".tmp-write-")))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Fails with a clear message when the parent directory does not exist")
    void write_failsWhenParentMissing() {
        Path target = tempDir.resolve("missing-dir").resolve("file.txt");

        assertThatThrownBy(() -> writer.write(target, "x"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("parent directory does not exist");
    }

    @Test
    @DisplayName("writeDocument re-emits a parsed document's BOM and terminators")
    void writeDocument_preservesBomAndTerminators() throws IOException {
        Path target = tempDir.resolve("doc.txt");
        Document document = Document.parse("\uFEFFa\r\nb");

        writer.writeDocument(target, document);

        byte[] expected = concat(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "a\r\nb".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.readAllBytes(target)).isEqualTo(expected);
    }

    @Test
    @DisplayName("WriteResult reports the exact byte count and line count written")
    void write_reportsBytesAndLines() throws IOException {
        Path target = tempDir.resolve("report.txt");

        WriteResult result = writer.write(target, "a\nb\r\nc");

        assertThat(result.path()).isEqualTo(target.toAbsolutePath());
        assertThat(result.bytesWritten()).isEqualTo(6L);
        assertThat(result.lineCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("encodeStrict accepts astral characters and rejects unpaired surrogates")
    void encodeStrict_strictness() throws Exception {
        assertThat(SourceFileWriter.encodeStrict("\uD83C\uDF89"))
                .isEqualTo("\uD83C\uDF89".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> SourceFileWriter.encodeStrict("x\uDFFFy"))
                .isInstanceOf(UnmappableTextException.class);
    }

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] out = new byte[head.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }
}