package com.neel.syntaxvalidation.validator.javascript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for {@link JsTokenType}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>All enum constants exist and are accessible</li>
 *   <li>{@code valueOf} round-trip for each constant</li>
 *   <li>{@code values()} returns all expected constants</li>
 *   <li>Enum ordinal ordering</li>
 *   <li>{@code name()} matches expected string representation</li>
 * </ul>
 */
@DisplayName("JsTokenType")
class JsTokenTypeTest {

    // =========================================================================
    //  ENUM CONSTANTS EXIST
    // =========================================================================

    @Nested
    @DisplayName("enum constants exist")
    class EnumConstantsExist {

        @Test
        @DisplayName("NUMBER constant exists")
        void numberExists() {
            assertThat(JsTokenType.NUMBER).isNotNull();
        }

        @Test
        @DisplayName("STRING constant exists")
        void stringExists() {
            assertThat(JsTokenType.STRING).isNotNull();
        }

        @Test
        @DisplayName("TEMPLATE constant exists")
        void templateExists() {
            assertThat(JsTokenType.TEMPLATE).isNotNull();
        }

        @Test
        @DisplayName("REGEX constant exists")
        void regexExists() {
            assertThat(JsTokenType.REGEX).isNotNull();
        }

        @Test
        @DisplayName("IDENTIFIER constant exists")
        void identifierExists() {
            assertThat(JsTokenType.IDENTIFIER).isNotNull();
        }

        @Test
        @DisplayName("KEYWORD constant exists")
        void keywordExists() {
            assertThat(JsTokenType.KEYWORD).isNotNull();
        }

        @Test
        @DisplayName("PUNCTUATION constant exists")
        void punctuationExists() {
            assertThat(JsTokenType.PUNCTUATION).isNotNull();
        }

        @Test
        @DisplayName("COMMENT constant exists")
        void commentExists() {
            assertThat(JsTokenType.COMMENT).isNotNull();
        }

        @Test
        @DisplayName("ERROR constant exists")
        void errorExists() {
            assertThat(JsTokenType.ERROR).isNotNull();
        }

        @Test
        @DisplayName("EOF constant exists")
        void eofExists() {
            assertThat(JsTokenType.EOF).isNotNull();
        }
    }

    // =========================================================================
    //  VALUES AND VALUEOF
    // =========================================================================

    @Nested
    @DisplayName("values and valueOf")
    class ValuesAndValueOf {

        @Test
        @DisplayName("values() returns exactly 10 constants")
        void valuesCount() {
            assertThat(JsTokenType.values()).hasSize(10);
        }

        @Test
        @DisplayName("values() contains all expected constants")
        void valuesContainsAll() {
            assertThat(JsTokenType.values()).containsExactly(
                    JsTokenType.NUMBER,
                    JsTokenType.STRING,
                    JsTokenType.TEMPLATE,
                    JsTokenType.REGEX,
                    JsTokenType.IDENTIFIER,
                    JsTokenType.KEYWORD,
                    JsTokenType.PUNCTUATION,
                    JsTokenType.COMMENT,
                    JsTokenType.ERROR,
                    JsTokenType.EOF
            );
        }

        @Test
        @DisplayName("valueOf('NUMBER') returns NUMBER")
        void valueOfNumber() {
            assertThat(JsTokenType.valueOf("NUMBER")).isEqualTo(JsTokenType.NUMBER);
        }

        @Test
        @DisplayName("valueOf('STRING') returns STRING")
        void valueOfString() {
            assertThat(JsTokenType.valueOf("STRING")).isEqualTo(JsTokenType.STRING);
        }

        @Test
        @DisplayName("valueOf('TEMPLATE') returns TEMPLATE")
        void valueOfTemplate() {
            assertThat(JsTokenType.valueOf("TEMPLATE")).isEqualTo(JsTokenType.TEMPLATE);
        }

        @Test
        @DisplayName("valueOf('REGEX') returns REGEX")
        void valueOfRegex() {
            assertThat(JsTokenType.valueOf("REGEX")).isEqualTo(JsTokenType.REGEX);
        }

        @Test
        @DisplayName("valueOf('IDENTIFIER') returns IDENTIFIER")
        void valueOfIdentifier() {
            assertThat(JsTokenType.valueOf("IDENTIFIER")).isEqualTo(JsTokenType.IDENTIFIER);
        }

        @Test
        @DisplayName("valueOf('KEYWORD') returns KEYWORD")
        void valueOfKeyword() {
            assertThat(JsTokenType.valueOf("KEYWORD")).isEqualTo(JsTokenType.KEYWORD);
        }

        @Test
        @DisplayName("valueOf('PUNCTUATION') returns PUNCTUATION")
        void valueOfPunctuation() {
            assertThat(JsTokenType.valueOf("PUNCTUATION")).isEqualTo(JsTokenType.PUNCTUATION);
        }

        @Test
        @DisplayName("valueOf('COMMENT') returns COMMENT")
        void valueOfComment() {
            assertThat(JsTokenType.valueOf("COMMENT")).isEqualTo(JsTokenType.COMMENT);
        }

        @Test
        @DisplayName("valueOf('ERROR') returns ERROR")
        void valueOfError() {
            assertThat(JsTokenType.valueOf("ERROR")).isEqualTo(JsTokenType.ERROR);
        }

        @Test
        @DisplayName("valueOf('EOF') returns EOF")
        void valueOfEof() {
            assertThat(JsTokenType.valueOf("EOF")).isEqualTo(JsTokenType.EOF);
        }

        @Test
        @DisplayName("valueOf with invalid name throws IllegalArgumentException")
        void valueOfInvalidThrows() {
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> JsTokenType.valueOf("INVALID_TYPE")
            );
        }
    }

    // =========================================================================
    //  NAME AND ORDINAL
    // =========================================================================

    @Nested
    @DisplayName("name and ordinal")
    class NameAndOrdinal {

        @Test
        @DisplayName("NUMBER name is 'NUMBER'")
        void numberName() {
            assertThat(JsTokenType.NUMBER.name()).isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("STRING name is 'STRING'")
        void stringName() {
            assertThat(JsTokenType.STRING.name()).isEqualTo("STRING");
        }

        @Test
        @DisplayName("TEMPLATE name is 'TEMPLATE'")
        void templateName() {
            assertThat(JsTokenType.TEMPLATE.name()).isEqualTo("TEMPLATE");
        }

        @Test
        @DisplayName("REGEX name is 'REGEX'")
        void regexName() {
            assertThat(JsTokenType.REGEX.name()).isEqualTo("REGEX");
        }

        @Test
        @DisplayName("IDENTIFIER name is 'IDENTIFIER'")
        void identifierName() {
            assertThat(JsTokenType.IDENTIFIER.name()).isEqualTo("IDENTIFIER");
        }

        @Test
        @DisplayName("KEYWORD name is 'KEYWORD'")
        void keywordName() {
            assertThat(JsTokenType.KEYWORD.name()).isEqualTo("KEYWORD");
        }

        @Test
        @DisplayName("PUNCTUATION name is 'PUNCTUATION'")
        void punctuationName() {
            assertThat(JsTokenType.PUNCTUATION.name()).isEqualTo("PUNCTUATION");
        }

        @Test
        @DisplayName("COMMENT name is 'COMMENT'")
        void commentName() {
            assertThat(JsTokenType.COMMENT.name()).isEqualTo("COMMENT");
        }

        @Test
        @DisplayName("ERROR name is 'ERROR'")
        void errorName() {
            assertThat(JsTokenType.ERROR.name()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("EOF name is 'EOF'")
        void eofName() {
            assertThat(JsTokenType.EOF.name()).isEqualTo("EOF");
        }

        @Test
        @DisplayName("ordinals are sequential starting from 0")
        void ordinalsSequential() {
            assertThat(JsTokenType.NUMBER.ordinal()).isZero();
            assertThat(JsTokenType.STRING.ordinal()).isEqualTo(1);
            assertThat(JsTokenType.TEMPLATE.ordinal()).isEqualTo(2);
            assertThat(JsTokenType.REGEX.ordinal()).isEqualTo(3);
            assertThat(JsTokenType.IDENTIFIER.ordinal()).isEqualTo(4);
            assertThat(JsTokenType.KEYWORD.ordinal()).isEqualTo(5);
            assertThat(JsTokenType.PUNCTUATION.ordinal()).isEqualTo(6);
            assertThat(JsTokenType.COMMENT.ordinal()).isEqualTo(7);
            assertThat(JsTokenType.ERROR.ordinal()).isEqualTo(8);
            assertThat(JsTokenType.EOF.ordinal()).isEqualTo(9);
        }
    }

    // =========================================================================
    //  EQUALITY AND IDENTITY
    // =========================================================================

    @Nested
    @DisplayName("equality and identity")
    class EqualityAndIdentity {

        @Test
        @DisplayName("same constant is equal to itself")
        void sameConstantEqual() {
            assertThat(JsTokenType.KEYWORD).isEqualTo(JsTokenType.KEYWORD);
        }

        @Test
        @DisplayName("different constants are not equal")
        void differentConstantsNotEqual() {
            assertThat(JsTokenType.KEYWORD).isNotEqualTo(JsTokenType.IDENTIFIER);
        }

        @Test
        @DisplayName("valueOf round-trip preserves equality")
        void valueOfRoundTrip() {
            for (JsTokenType type : JsTokenType.values()) {
                assertThat(JsTokenType.valueOf(type.name())).isEqualTo(type);
            }
        }

        @Test
        @DisplayName("enum uses identity comparison (same references)")
        void identityComparison() {
            assertThat(JsTokenType.EOF).isSameAs(JsTokenType.valueOf("EOF"));
        }
    }

    // =========================================================================
    //  COMPARETO
    // =========================================================================

    @Nested
        @DisplayName("compareTo")
        class CompareTo {

            @Test
            @DisplayName("compareTo returns 0 for same constant")
            void compareToSame() {
                assertThat(JsTokenType.KEYWORD.compareTo(JsTokenType.KEYWORD)).isZero();
            }

            @Test
            @DisplayName("compareTo returns negative for earlier ordinal")
            void compareToEarlier() {
                assertThat(JsTokenType.NUMBER.compareTo(JsTokenType.EOF)).isNegative();
            }

            @Test
            @DisplayName("compareTo returns positive for later ordinal")
            void compareToLater() {
                assertThat(JsTokenType.EOF.compareTo(JsTokenType.NUMBER)).isPositive();
            }

            @Test
            @DisplayName("compareTo follows ordinal ordering")
            void compareToOrdinal() {
                JsTokenType[] values = JsTokenType.values();
                for (int i = 0; i < values.length - 1; i++) {
                    assertThat(values[i].compareTo(values[i + 1])).isNegative();
                }
            }
        }
}