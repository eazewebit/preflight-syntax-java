package com.neel.syntaxvalidation.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageTest {

    @ParameterizedTest
    @CsvSource({
            "js,   JAVASCRIPT",
            "JS,   JAVASCRIPT",
            "mjs,  JAVASCRIPT",
            "cjs,  JAVASCRIPT",
            "ts,   TYPESCRIPT",
            "py,   PYTHON",
            "java, JAVA"
    })
    void fromExtension_recognisesKnownExtensions(String extension, Language expected) {
        assertThat(Language.fromExtension(extension)).contains(expected);
    }

    @Test
    void fromExtension_acceptsDottedExtension() {
        assertThat(Language.fromExtension(".js")).contains(Language.JAVASCRIPT);
    }

    @ParameterizedTest
    @CsvSource({
            "'',  ",
            "rb,  ",
            "txt, "
    })
    void fromExtension_returnsEmptyForUnknown(String extension, String ignored) {
        assertThat(Language.fromExtension(extension)).isEmpty();
    }

    @Test
    void fromExtension_handlesNull() {
        assertThat(Language.fromExtension(null)).isEmpty();
    }

    @Test
    void fromPath_detectsLanguageFromFileName() {
        assertThat(Language.fromPath(Path.of("/project/src/app.js"))).contains(Language.JAVASCRIPT);
        assertThat(Language.fromPath(Path.of("C:\\code\\script.py"))).contains(Language.PYTHON);
    }

    @Test
    void fromPath_returnsEmptyForFilesWithoutExtension() {
        assertThat(Language.fromPath(Path.of("/project/Makefile"))).isEmpty();
        assertThat(Language.fromPath(Path.of("/project/file."))).isEmpty();
    }

    @Test
    void fromPath_handlesNull() {
        assertThat(Language.fromPath(null)).isEmpty();
    }

    @Test
    void getExtensions_returnsDefensiveCopy() {
        Language javascript = Language.JAVASCRIPT;
        String[] first = javascript.getExtensions();
        first[0] = "mutated";
        assertThat(javascript.getExtensions()).contains("js");
    }

    @Test
    void everyLanguageHasAtLeastOneExtension() {
        for (Language language : Language.values()) {
            Optional<Language> roundTrip = Language.fromExtension(language.getExtensions()[0]);
            assertThat(roundTrip).contains(language);
        }
    }
}
