package com.neel.syntaxvalidation.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Language}.
 */
@DisplayName("Language")
class LanguageTest {

    // ---------------------------------------------------------------
    // Enum values
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("enum values")
    class EnumValues {

        @Test
        @DisplayName("HTML enum value exists")
        void htmlExists() {
            assertThat(Language.HTML).isNotNull();
        }

        @Test
        @DisplayName("CSS enum value exists")
        void cssExists() {
            assertThat(Language.CSS).isNotNull();
        }

        @Test
        @DisplayName("JAVASCRIPT enum value exists")
        void javascriptExists() {
            assertThat(Language.JAVASCRIPT).isNotNull();
        }

        @Test
        @DisplayName("has at least three core values")
        void hasCoreValues() {
            assertThat(Language.values()).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    // ---------------------------------------------------------------
    // fromExtension
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("fromExtension")
    class FromExtension {

        @Test
        @DisplayName("detects .html extension")
        void detectsHtml() {
            assertThat(Language.fromExtension("html")).contains(Language.HTML);
        }

        @Test
        @DisplayName("detects .htm extension")
        void detectsHtm() {
            assertThat(Language.fromExtension("htm")).contains(Language.HTML);
        }

        @Test
        @DisplayName("detects .css extension")
        void detectsCss() {
            assertThat(Language.fromExtension("css")).contains(Language.CSS);
        }

        @Test
        @DisplayName("detects .js extension")
        void detectsJs() {
            assertThat(Language.fromExtension("js")).contains(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("detects .mjs extension")
        void detectsMjs() {
            assertThat(Language.fromExtension("mjs")).contains(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("detects .cjs extension")
        void detectsCjs() {
            assertThat(Language.fromExtension("cjs")).contains(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("detects .ts extension")
        void detectsTs() {
            assertThat(Language.fromExtension("ts")).contains(Language.TYPESCRIPT);
        }

        @Test
        @DisplayName("detects .py extension")
        void detectsPy() {
            assertThat(Language.fromExtension("py")).contains(Language.PYTHON);
        }

        @Test
        @DisplayName("detects .java extension")
        void detectsJava() {
            assertThat(Language.fromExtension("java")).contains(Language.JAVA);
        }

        @Test
        @DisplayName("returns empty for unknown extension")
        void unknownExtension() {
            assertThat(Language.fromExtension("json")).isEmpty();
        }

        @Test
        @DisplayName("returns empty for blank extension")
        void blankExtension() {
            assertThat(Language.fromExtension("")).isEmpty();
        }

        @Test
        @DisplayName("returns empty for null extension")
        void nullExtension() {
            assertThat(Language.fromExtension(null)).isEmpty();
        }

        @Test
        @DisplayName("is case-insensitive")
        void caseInsensitive() {
            assertThat(Language.fromExtension("CSS")).contains(Language.CSS);
            assertThat(Language.fromExtension("JS")).contains(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("handles dot-prefixed extension")
        void dotPrefixed() {
            assertThat(Language.fromExtension(".html")).contains(Language.HTML);
            assertThat(Language.fromExtension(".css")).contains(Language.CSS);
        }
    }

    // ---------------------------------------------------------------
    // fromPath
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("fromPath")
    class FromPath {

        @Test
        @DisplayName("detects HTML from Path")
        void detectsHtmlFromPath() {
            Path path = Path.of("/home/user/project/index.html");
            assertThat(Language.fromPath(path)).contains(Language.HTML);
        }

        @Test
        @DisplayName("detects CSS from Path")
        void detectsCssFromPath() {
            Path path = Path.of("/home/user/project/styles.css");
            assertThat(Language.fromPath(path)).contains(Language.CSS);
        }

        @Test
        @DisplayName("detects JavaScript from Path")
        void detectsJsFromPath() {
            Path path = Path.of("/home/user/project/app.js");
            assertThat(Language.fromPath(path)).contains(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("returns empty for unknown Path extension")
        void unknownPathExtension() {
            Path path = Path.of("/home/user/project/data.xml");
            assertThat(Language.fromPath(path)).isEmpty();
        }

        @Test
        @DisplayName("handles Windows-style paths")
        void windowsPaths() {
            Path path = Path.of("C:\\Users\\project\\index.html");
            assertThat(Language.fromPath(path)).contains(Language.HTML);
        }

        @Test
        @DisplayName("returns empty for null path")
        void nullPath() {
            assertThat(Language.fromPath(null)).isEmpty();
        }

        @Test
        @DisplayName("returns empty for path with no filename")
        void noFilename() {
            assertThat(Language.fromPath(Path.of("/"))).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // getExtensions
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getExtensions")
    class GetExtensions {

        @Test
        @DisplayName("HTML has html and htm extensions")
        void htmlExtensions() {
            assertThat(Language.HTML.getExtensions()).contains("html", "htm");
        }

        @Test
        @DisplayName("CSS has css extension")
        void cssExtensions() {
            assertThat(Language.CSS.getExtensions()).containsExactly("css");
        }

        @Test
        @DisplayName("JAVASCRIPT has js, mjs, cjs extensions")
        void jsExtensions() {
            assertThat(Language.JAVASCRIPT.getExtensions()).contains("js", "mjs", "cjs");
        }

        @Test
        @DisplayName("getExtensions returns defensive copy")
        void defensiveCopy() {
            String[] ext1 = Language.HTML.getExtensions();
            String[] ext2 = Language.HTML.getExtensions();
            assertThat(ext1).isNotSameAs(ext2);
        }
    }
}
