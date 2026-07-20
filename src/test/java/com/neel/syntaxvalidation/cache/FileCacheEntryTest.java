package com.neel.syntaxvalidation.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Comprehensive tests for {@link FileCacheEntry}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Constructor with defensive copy of lines</li>
 *   <li>All getter methods: {@code getAbsolutePath()}, {@code getLines()},
 *       {@code getContent()}, {@code getLineCount()}, {@code getLastModified()},
 *       {@code getLoadedAt()}</li>
 *   <li>{@code equals} and {@code hashCode} contracts (excludes {@code loadedAt})</li>
 *   <li>{@code toString} representation</li>
 *   <li>Edge cases: empty lines, single line, multi-line, unicode, special chars</li>
 *   <li>Null-safety for constructor parameters</li>
 *   <li>Immutability guarantees (unmodifiable list)</li>
 * </ul>
 */
@DisplayName("FileCacheEntry")
class FileCacheEntryTest {

    // =========================================================================
    //  CONSTRUCTION AND GETTERS
    // =========================================================================

    @Nested
    @DisplayName("construction and getters")
    class ConstructionAndAccessors {

        @Test
        @DisplayName("getAbsolutePath returns the path stored at construction")
        void absolutePathIsStored() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path/to/file.java", List.of("line1"), 1000L, 2000L);
            assertThat(entry.getAbsolutePath()).isEqualTo("/path/to/file.java");
        }

        @Test
        @DisplayName("getLines returns the lines stored at construction")
        void linesAreStored() {
            List<String> lines = List.of("line1", "line2", "line3");
            FileCacheEntry entry = new FileCacheEntry("/path", lines, 1000L, 2000L);
            assertThat(entry.getLines()).containsExactly("line1", "line2", "line3");
        }

        @Test
        @DisplayName("getLastModified returns the value stored at construction")
        void lastModifiedIsStored() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("line1"), 12345L, 2000L);
            assertThat(entry.getLastModified()).isEqualTo(12345L);
        }

        @Test
        @DisplayName("getLoadedAt returns the value stored at construction")
        void loadedAtIsStored() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("line1"), 1000L, 98765L);
            assertThat(entry.getLoadedAt()).isEqualTo(98765L);
        }

        @Test
        @DisplayName("null absolutePath throws NullPointerException")
        void nullAbsolutePathThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new FileCacheEntry(null, List.of("line"), 1000L, 2000L));
        }

        @Test
        @DisplayName("null lines throws NullPointerException")
        void nullLinesThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new FileCacheEntry("/path", null, 1000L, 2000L));
        }
    }

    // =========================================================================
    //  GETCONTENT
    // =========================================================================

    @Nested
    @DisplayName("getContent")
    class GetContent {

        @Test
        @DisplayName("getContent joins lines with newline separator")
        void joinsLinesWithNewline() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("line1", "line2", "line3"), 1000L, 2000L);
            assertThat(entry.getContent()).isEqualTo("line1\nline2\nline3");
        }

        @Test
        @DisplayName("getContent returns empty string for empty lines")
        void emptyLinesReturnsEmpty() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of(), 1000L, 2000L);
            assertThat(entry.getContent()).isEmpty();
        }

        @Test
        @DisplayName("getContent returns single line without separator")
        void singleLineNoSeparator() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("single line"), 1000L, 2000L);
            assertThat(entry.getContent()).isEqualTo("single line");
        }

        @Test
        @DisplayName("getContent with unicode content")
        void unicodeContent() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("// こんにちは", "String s = \"你好\";"), 1000L, 2000L);
            assertThat(entry.getContent()).isEqualTo("// こんにちは\nString s = \"你好\";");
        }

        @Test
        @DisplayName("getContent with special characters")
        void specialCharacters() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("class Foo {", "\tvoid bar() {", "\t}"), 1000L, 2000L);
            assertThat(entry.getContent()).isEqualTo("class Foo {\n\tvoid bar() {\n\t}");
        }
    }

    // =========================================================================
    //  GETLINECOUNT
    // =========================================================================

    @Nested
    @DisplayName("getLineCount")
    class GetLineCount {

        @Test
        @DisplayName("getLineCount returns 0 for empty lines")
        void emptyLinesReturnsZero() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of(), 1000L, 2000L);
            assertThat(entry.getLineCount()).isZero();
        }

        @Test
        @DisplayName("getLineCount returns 1 for single line")
        void singleLine() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("line1"), 1000L, 2000L);
            assertThat(entry.getLineCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getLineCount returns correct count for multiple lines")
        void multipleLines() {
            List<String> lines = List.of("line1", "line2", "line3", "line4", "line5");
            FileCacheEntry entry = new FileCacheEntry("/path", lines, 1000L, 2000L);
            assertThat(entry.getLineCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("getLineCount returns large count for many lines")
        void manyLines() {
            List<String> lines = Collections.nCopies(10000, "line");
            FileCacheEntry entry = new FileCacheEntry("/path", lines, 1000L, 2000L);
            assertThat(entry.getLineCount()).isEqualTo(10000);
        }
    }

    // =========================================================================
    //  GETLINES IMMUTABILITY
    // =========================================================================

    @Nested
    @DisplayName("getLines immutability")
    class LinesImmutability {

        @Test
        @DisplayName("getLines returns an unmodifiable list")
        void unmodifiableList() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("line1", "line2"), 1000L, 2000L);
            List<String> lines = entry.getLines();
            assertThat(lines).isUnmodifiable();
        }

        @Test
        @DisplayName("constructor takes a defensive copy — mutating the source list does not affect the entry")
        void defensiveCopy() {
            java.util.ArrayList<String> mutable = new java.util.ArrayList<>(List.of("line1", "line2"));
            FileCacheEntry entry = new FileCacheEntry("/path", mutable, 1000L, 2000L);
            mutable.add("line3");
            assertThat(entry.getLines()).containsExactly("line1", "line2");
            assertThat(entry.getLineCount()).isEqualTo(2);
        }
    }

    // =========================================================================
    //  EQUALS AND HASHCODE
    // =========================================================================

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("equal entries (same path, lines, lastModified) are equal")
        void equalEntriesAreEqual() {
            FileCacheEntry a = new FileCacheEntry("/path", List.of("line"), 1000L, 2000L);
            FileCacheEntry b = new FileCacheEntry("/path", List.of("line"), 1000L, 9999L);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("equal entries have same hashCode")
        void equalEntriesHaveSameHashCode() {
            FileCacheEntry a = new FileCacheEntry("/path", List.of("line"), 1000L, 2000L);
            FileCacheEntry b = new FileCacheEntry("/path", List.of("line"), 1000L, 9999L);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("entries with different paths are not equal")
        void differentPathsNotEqual() {
            FileCacheEntry a = new FileCacheEntry("/path1", List.of("line"), 1000L, 2000L);
            FileCacheEntry b = new FileCacheEntry("/path2", List.of("line"), 1000L, 2000L);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("entries with different lines are not equal")
        void differentLinesNotEqual() {
            FileCacheEntry a = new FileCacheEntry("/path", List.of("line1"), 1000L, 2000L);
            FileCacheEntry b = new FileCacheEntry("/path", List.of("line2"), 1000L, 2000L);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("entries with different lastModified are not equal")
        void differentLastModifiedNotEqual() {
            FileCacheEntry a = new FileCacheEntry("/path", List.of("line"), 1000L, 2000L);
            FileCacheEntry b = new FileCacheEntry("/path", List.of("line"), 2000L, 2000L);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("entries with different loadedAt are still equal (loadedAt excluded from equals)")
        void differentLoadedAtStillEqual() {
            FileCacheEntry a = new FileCacheEntry("/path", List.of("line"), 1000L, 2000L);
            FileCacheEntry b = new FileCacheEntry("/path", List.of("line"), 1000L, 5000L);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("entry is not equal to null")
        void notEqualToNull() {
            FileCacheEntry entry = new FileCacheEntry("/path", List.of("line"), 1000L, 2000L);
            assertThat(entry).isNotEqualTo(null);
        }

        @Test
        @DisplayName("entry is not equal to different type")
        void notEqualToDifferentType() {
            FileCacheEntry entry = new FileCacheEntry("/path", List.of("line"), 1000L, 2000L);
            assertThat(entry).isNotEqualTo("a string");
        }

        @Test
        @DisplayName("entry equals itself (reflexive)")
        void reflexive() {
            FileCacheEntry entry = new FileCacheEntry("/path", List.of("line"), 1000L, 2000L);
            assertThat(entry).isEqualTo(entry);
        }

        @Test
        @DisplayName("equals is symmetric")
        void symmetric() {
            FileCacheEntry a = new FileCacheEntry("/path", List.of("line"), 1000L, 2000L);
            FileCacheEntry b = new FileCacheEntry("/path", List.of("line"), 1000L, 9999L);
            assertThat(a).isEqualTo(b);
            assertThat(b).isEqualTo(a);
        }

        @Test
        @DisplayName("equals is transitive")
        void transitive() {
            FileCacheEntry a = new FileCacheEntry("/path", List.of("line"), 1000L, 2000L);
            FileCacheEntry b = new FileCacheEntry("/path", List.of("line"), 1000L, 5000L);
            FileCacheEntry c = new FileCacheEntry("/path", List.of("line"), 1000L, 8000L);
            assertThat(a).isEqualTo(b);
            assertThat(b).isEqualTo(c);
            assertThat(a).isEqualTo(c);
        }
    }

    // =========================================================================
    //  TOSTRING
    // =========================================================================

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString contains path")
        void containsPath() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path/to/file.java", List.of("line"), 1000L, 2000L);
            assertThat(entry.toString()).contains("/path/to/file.java");
        }

        @Test
        @DisplayName("toString contains line count")
        void containsLineCount() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("a", "b", "c"), 1000L, 2000L);
            assertThat(entry.toString()).contains("3");
        }

        @Test
        @DisplayName("toString contains lastModified")
        void containsLastModified() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("line"), 12345L, 2000L);
            assertThat(entry.toString()).contains("12345");
        }

        @Test
        @DisplayName("toString is not null or empty")
        void notNullOrEmpty() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("line"), 1000L, 2000L);
            assertThat(entry.toString()).isNotNull().isNotEmpty();
        }
    }

    // =========================================================================
    //  EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty lines list")
        void emptyLines() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of(), 1000L, 2000L);
            assertThat(entry.getLines()).isEmpty();
            assertThat(entry.getLineCount()).isZero();
            assertThat(entry.getContent()).isEmpty();
        }

        @Test
        @DisplayName("single line content")
        void singleLine() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("single line"), 1000L, 2000L);
            assertThat(entry.getLines()).hasSize(1);
            assertThat(entry.getLineCount()).isEqualTo(1);
            assertThat(entry.getContent()).isEqualTo("single line");
        }

        @Test
        @DisplayName("multi-line content with correct line count")
        void multiLineContent() {
            List<String> lines = List.of("line1", "line2", "line3", "line4", "line5");
            FileCacheEntry entry = new FileCacheEntry("/path", lines, 1000L, 2000L);
            assertThat(entry.getLines()).hasSize(5);
            assertThat(entry.getLineCount()).isEqualTo(5);
            assertThat(entry.getContent()).isEqualTo("line1\nline2\nline3\nline4\nline5");
        }

        @Test
        @DisplayName("very large file (10000 lines)")
        void veryLargeFile() {
            List<String> lines = Collections.nCopies(10000, "line");
            FileCacheEntry entry = new FileCacheEntry("/path/to/large.java", lines, 1000L, 2000L);
            assertThat(entry.getLines()).hasSize(10000);
            assertThat(entry.getLineCount()).isEqualTo(10000);
        }

        @Test
        @DisplayName("empty path string is allowed")
        void emptyPathAllowed() {
            FileCacheEntry entry = new FileCacheEntry(
                    "", List.of("content"), 1000L, 2000L);
            assertThat(entry.getAbsolutePath()).isEmpty();
        }

        @Test
        @DisplayName("path with spaces and special characters")
        void pathWithSpaces() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path/to/my file (copy).java", List.of("content"), 1000L, 2000L);
            assertThat(entry.getAbsolutePath()).isEqualTo("/path/to/my file (copy).java");
        }

        @Test
        @DisplayName("Windows-style path")
        void windowsPath() {
            FileCacheEntry entry = new FileCacheEntry(
                    "C:\\Users\\dev\\project\\src\\Main.java", List.of("content"), 1000L, 2000L);
            assertThat(entry.getAbsolutePath()).isEqualTo("C:\\Users\\dev\\project\\src\\Main.java");
        }

        @Test
        @DisplayName("zero lastModified and loadedAt are valid")
        void zeroTimestamps() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("line"), 0L, 0L);
            assertThat(entry.getLastModified()).isZero();
            assertThat(entry.getLoadedAt()).isZero();
        }

        @Test
        @DisplayName("negative timestamps are accepted (no validation)")
        void negativeTimestamps() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("line"), -1L, -1L);
            assertThat(entry.getLastModified()).isEqualTo(-1L);
            assertThat(entry.getLoadedAt()).isEqualTo(-1L);
        }

        @Test
        @DisplayName("lines containing empty strings are valid")
        void linesWithEmptyStrings() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("", "", ""), 1000L, 2000L);
            assertThat(entry.getLineCount()).isEqualTo(3);
            assertThat(entry.getContent()).isEqualTo("\n\n");
        }

        @Test
        @DisplayName("lines with only whitespace")
        void whitespaceLines() {
            FileCacheEntry entry = new FileCacheEntry(
                    "/path", List.of("  ", "\t", "   "), 1000L, 2000L);
            assertThat(entry.getLineCount()).isEqualTo(3);
            assertThat(entry.getContent()).isEqualTo("  \n\t\n   ");
        }
    }
}