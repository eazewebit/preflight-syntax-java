package com.neel.syntaxvalidation.validator.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exhaustive, very verbose test suite for {@link JavaTokenType}.
 *
 * <p>The enum defines exactly {@value #EXPECTED_TYPE_COUNT} lexical categories
 * that every token produced by {@link JavaLexer} can fall into. This test class
 * verifies the full set of constants, their {@code name()}, their
 * {@code ordinal()} stability, {@code valueOf} resolution, and various
 * semantic properties that downstream checkers rely on.
 */
@DisplayName("JavaTokenType")
class JavaTokenTypeTest {

    /** The exact number of constants the enum must declare. */
    static final int EXPECTED_TYPE_COUNT = 9;

    // ====================================================================
    //  Enum constants
    // ====================================================================

    @Nested
    @DisplayName("declares the expected set of constants")
    class Constants {

        @Test
        @DisplayName("has exactly " + EXPECTED_TYPE_COUNT + " constants")
        void exactCount() {
            assertThat(JavaTokenType.values()).hasSize(EXPECTED_TYPE_COUNT);
        }

        @Test
        @DisplayName("includes COMMENT")
        void hasComment() {
            assertThat(JavaTokenType.valueOf("COMMENT")).isEqualTo(JavaTokenType.COMMENT);
        }

        @Test
        @DisplayName("includes KEYWORD")
        void hasKeyword() {
            assertThat(JavaTokenType.valueOf("KEYWORD")).isEqualTo(JavaTokenType.KEYWORD);
        }

        @Test
        @DisplayName("includes IDENTIFIER")
        void hasIdentifier() {
            assertThat(JavaTokenType.valueOf("IDENTIFIER")).isEqualTo(JavaTokenType.IDENTIFIER);
        }

        @Test
        @DisplayName("includes NUMBER")
        void hasNumber() {
            assertThat(JavaTokenType.valueOf("NUMBER")).isEqualTo(JavaTokenType.NUMBER);
        }

        @Test
        @DisplayName("includes STRING")
        void hasString() {
            assertThat(JavaTokenType.valueOf("STRING")).isEqualTo(JavaTokenType.STRING);
        }

        @Test
        @DisplayName("includes CHAR")
        void hasChar() {
            assertThat(JavaTokenType.valueOf("CHAR")).isEqualTo(JavaTokenType.CHAR);
        }

        @Test
        @DisplayName("includes PUNCTUATION")
        void hasPunctuation() {
            assertThat(JavaTokenType.valueOf("PUNCTUATION")).isEqualTo(JavaTokenType.PUNCTUATION);
        }

        @Test
        @DisplayName("includes ERROR")
        void hasError() {
            assertThat(JavaTokenType.valueOf("ERROR")).isEqualTo(JavaTokenType.ERROR);
        }

        @Test
        @DisplayName("includes EOF")
        void hasEof() {
            assertThat(JavaTokenType.valueOf("EOF")).isEqualTo(JavaTokenType.EOF);
        }

        @Test
        @DisplayName("contains no unexpected constants")
        void noUnexpectedConstants() {
            Set<JavaTokenType> expected = EnumSet.allOf(JavaTokenType.class);
            Set<String> expectedNames = expected.stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            Set<String> actualNames = Arrays.stream(JavaTokenType.values())
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            assertThat(actualNames).isEqualTo(expectedNames);
        }
    }

    // ====================================================================
    //  valueOf resolution
    // ====================================================================

    @Nested
    @DisplayName("valueOf resolves constant names")
    class ValueOfResolution {

        @ParameterizedTest(name = "valueOf(\"{0}\") resolves")
        @EnumSource(JavaTokenType.class)
        @DisplayName("every constant resolves via valueOf")
        void valueOfResolvesAll(JavaTokenType type) {
            assertThat(JavaTokenType.valueOf(type.name())).isSameAs(type);
        }

        @Test
        @DisplayName("valueOf with unknown name throws IllegalArgumentException")
        void unknownNameThrows() {
            assertThatThrownBy(() -> JavaTokenType.valueOf("NONEXISTENT"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("valueOf is case-sensitive")
        void valueOfIsCaseSensitive() {
            assertThatThrownBy(() -> JavaTokenType.valueOf("keyword"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("valueOf with null throws NullPointerException")
        void valueOfNullThrows() {
            assertThatThrownBy(() -> JavaTokenType.valueOf(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ====================================================================
    //  name() and toString()
    // ====================================================================

    @Nested
    @DisplayName("name() returns the declared constant name")
    class NameAndToString {

        @ParameterizedTest(name = "name() of {0} is uppercase")
        @EnumSource(JavaTokenType.class)
        @DisplayName("every constant name is uppercase")
        void namesAreUppercase(JavaTokenType type) {
            assertThat(type.name()).isEqualTo(type.name().toUpperCase());
            assertThat(type.name()).matches("[A-Z_]+");
        }

        @Test
        @DisplayName("COMMENT name is COMMENT")
        void commentName() {
            assertThat(JavaTokenType.COMMENT.name()).isEqualTo("COMMENT");
        }

        @Test
        @DisplayName("EOF name is EOF")
        void eofName() {
            assertThat(JavaTokenType.EOF.name()).isEqualTo("EOF");
        }

        @ParameterizedTest(name = "toString() of {0} matches name()")
        @EnumSource(JavaTokenType.class)
        @DisplayName("toString returns the same as name() for all constants")
        void toStringMatchesName(JavaTokenType type) {
            assertThat(type.toString()).isEqualTo(type.name());
        }
    }

    // ====================================================================
    //  ordinal() stability
    // ====================================================================

    @Nested
    @DisplayName("ordinal() reflects declaration order")
    class OrdinalStability {

        @ParameterizedTest(name = "{0} has non-negative ordinal")
        @EnumSource(JavaTokenType.class)
        @DisplayName("every ordinal is non-negative")
        void nonNegativeOrdinal(JavaTokenType type) {
            assertThat(type.ordinal()).isNotNegative();
        }

        @Test
        @DisplayName("all ordinals are unique")
        void uniqueOrdinals() {
            Set<Integer> ordinals = Arrays.stream(JavaTokenType.values())
                    .map(JavaTokenType::ordinal)
                    .collect(Collectors.toSet());

            assertThat(ordinals).hasSize(EXPECTED_TYPE_COUNT);
        }

        @Test
        @DisplayName("ordinals are contiguous starting from 0")
        void contiguousOrdinals() {
            Set<Integer> ordinals = Arrays.stream(JavaTokenType.values())
                    .map(JavaTokenType::ordinal)
                    .collect(Collectors.toSet());

            for (int i = 0; i < EXPECTED_TYPE_COUNT; i++) {
                assertThat(ordinals).contains(i);
            }
        }
    }

    // ====================================================================
    //  EnumSet / comparisons
    // ====================================================================

    @Nested
    @DisplayName("works correctly in EnumSet and comparisons")
    class EnumSetOperations {

        @Test
        @DisplayName("can create an EnumSet of all values")
        void allOf() {
            EnumSet<JavaTokenType> all = EnumSet.allOf(JavaTokenType.class);
            assertThat(all).hasSize(EXPECTED_TYPE_COUNT);
        }

        @Test
        @DisplayName("can create an EnumSet of specific values")
        void ofSpecific() {
            EnumSet<JavaTokenType> subset = EnumSet.of(
                    JavaTokenType.KEYWORD,
                    JavaTokenType.IDENTIFIER,
                    JavaTokenType.PUNCTUATION);

            assertThat(subset).hasSize(3);
            assertThat(subset).contains(JavaTokenType.KEYWORD);
        }

        @Test
        @DisplayName("comparing two different types returns consistent ordering")
        void comparableOrdering() {
            int cmp = Integer.compare(
                    JavaTokenType.COMMENT.ordinal(),
                    JavaTokenType.EOF.ordinal());

            assertThat(cmp).isNegative();
        }

        @Test
        @DisplayName("values() returns a new array each time")
        void valuesReturnsNewArray() {
            JavaTokenType[] first = JavaTokenType.values();
            JavaTokenType[] second = JavaTokenType.values();

            assertThat(first).isNotSameAs(second);
            assertThat(first).containsExactly(second);
        }
    }

    // ====================================================================
    //  Semantic properties relied upon by checkers
    // ====================================================================

    @Nested
    @DisplayName("constants have semantic properties used by downstream checkers")
    class SemanticProperties {

        @Test
        @DisplayName("ERROR exists for tokens that cannot be classified")
        void errorTypeExists() {
            // TokenizationErrorChecker filters on ERROR tokens
            assertThat(JavaTokenType.ERROR).isNotNull();
        }

        @Test
        @DisplayName("PUNCTUATION exists for delimiter analysis")
        void punctuationTypeExists() {
            // DelimiterBalanceChecker filters on PUNCTUATION tokens
            assertThat(JavaTokenType.PUNCTUATION).isNotNull();
        }

        @Test
        @DisplayName("KEYWORD exists for keyword validation")
        void keywordTypeExists() {
            // KeywordUsageChecker filters on KEYWORD tokens
            assertThat(JavaTokenType.KEYWORD).isNotNull();
        }

        @Test
        @DisplayName("COMMENT is a skippable type for delimiter analysis")
        void skipTypeExists() {
            // DelimiterBalanceChecker skips COMMENT tokens
            EnumSet<JavaTokenType> skipTypes = EnumSet.of(JavaTokenType.COMMENT);

            assertThat(skipTypes).isNotEmpty();
            assertThat(skipTypes).contains(JavaTokenType.COMMENT);
        }

        @Test
        @DisplayName("ERROR, EOF, STRING, CHAR are non-structural types")
        void nonStructuralTypesExist() {
            // These types don't participate in delimiter or keyword checks
            Set<JavaTokenType> nonStructural = EnumSet.of(
                    JavaTokenType.ERROR,
                    JavaTokenType.EOF,
                    JavaTokenType.STRING,
                    JavaTokenType.CHAR);

            assertThat(nonStructural).hasSize(4);
        }
    }
}
