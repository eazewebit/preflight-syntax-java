package com.neel.syntaxvalidation.validator.typescript;

import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TypeScriptSyntaxEngine}.
 */
@DisplayName("TypeScriptSyntaxEngine")
class TypeScriptSyntaxEngineTest {

    private TypeScriptSyntaxEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TypeScriptSyntaxEngine();
    }

    @Nested
    @DisplayName("empty and null input")
    class EmptyAndNullInput {

        @Test
        @DisplayName("should return success for null input")
        void shouldReturnSuccessForNullInput() {
            ValidationResult result = engine.validate(null);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should return success for empty string")
        void shouldReturnSuccessForEmptyString() {
            ValidationResult result = engine.validate("");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should return success for blank string")
        void shouldReturnSuccessForBlankString() {
            ValidationResult result = engine.validate("   \n\t  ");
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("valid TypeScript code")
    class ValidTypeScriptCode {

        @Test
        @DisplayName("should accept simple variable declaration")
        void shouldAcceptSimpleVariableDeclaration() {
            ValidationResult result = engine.validate("let x: number = 42;");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should accept interface declaration")
        void shouldAcceptInterfaceDeclaration() {
            String source = """
                    interface User {
                        name: string;
                        age: number;
                    }
                    """;
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should accept function with type parameters")
        void shouldAcceptFunctionWithTypeParameters() {
            String source = """
                    function identity<T>(arg: T): T {
                        return arg;
                    }
                    """;
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should accept type alias")
        void shouldAcceptTypeAlias() {
            String source = "type Result<T> = { data: T; error?: string; };";
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should accept class with implements")
        void shouldAcceptClassWithImplements() {
            String source = """
                    class UserService implements Service<User> {
                        private data: User[] = [];
                    
                        getUser(id: number): User | undefined {
                            return this.data.find(u => u.id === id);
                        }
                    }
                    """;
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should accept arrow functions with type annotations")
        void shouldAcceptArrowFunctionsWithTypeAnnotations() {
            String source = "const add = (a: number, b: number): number => a + b;";
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should accept enum declaration")
        void shouldAcceptEnumDeclaration() {
            String source = """
                    enum Direction {
                        Up = 'UP',
                        Down = 'DOWN',
                        Left = 'LEFT',
                        Right = 'RIGHT'
                    }
                    """;
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should accept import/export statements")
        void shouldAcceptImportExportStatements() {
            String source = """
                    import { useState, useEffect } from 'react';
                    export default function App() {
                        return null;
                    }
                    """;
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should accept async/await patterns")
        void shouldAcceptAsyncAwaitPatterns() {
            String source = """
                    async function fetchData(): Promise<Response> {
                        const response = await fetch('/api/data');
                        return response;
                    }
                    """;
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should accept generic constraints with extends")
        void shouldAcceptGenericConstraintsWithExtends() {
            String source = """
                    function getProperty<T, K extends keyof T>(obj: T, key: K): T[K] {
                        return obj[key];
                    }
                    """;
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should accept complex nested structures")
        void shouldAcceptComplexNestedStructures() {
            String source = """
                    const config: {
                        database: {
                            host: string;
                            port: number;
                            options: {
                                timeout: number;
                                retries: number;
                            };
                        };
                    } = {
                        database: {
                            host: 'localhost',
                            port: 5432,
                            options: {
                                timeout: 5000,
                                retries: 3
                            }
                        }
                    };
                    """;
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("unbalanced delimiters")
    class UnbalancedDelimiters {

        @Test
        @DisplayName("should detect unclosed brace")
        void shouldDetectUnclosedBrace() {
            ValidationResult result = engine.validate("function foo() {");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("unclosed brace");
        }

        @Test
        @DisplayName("should detect unclosed parenthesis")
        void shouldDetectUnclosedParenthesis() {
            ValidationResult result = engine.validate("console.log('hello'");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("unclosed parenthesis");
        }

        @Test
        @DisplayName("should detect unclosed bracket")
        void shouldDetectUnclosedBracket() {
            ValidationResult result = engine.validate("const arr = [1, 2, 3;");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("unclosed bracket");
        }

        @Test
        @DisplayName("should detect unexpected closing brace")
        void shouldDetectUnexpectedClosingBrace() {
            ValidationResult result = engine.validate("}");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("unexpected closing brace");
        }

        @Test
        @DisplayName("should detect unexpected closing parenthesis")
        void shouldDetectUnexpectedClosingParenthesis() {
            ValidationResult result = engine.validate(")");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("unexpected closing parenthesis");
        }

        @Test
        @DisplayName("should detect unexpected closing bracket")
        void shouldDetectUnexpectedClosingBracket() {
            ValidationResult result = engine.validate("]");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("unexpected closing bracket");
        }

        @Test
        @DisplayName("should detect multiple unclosed delimiters")
        void shouldDetectMultipleUnclosedDelimiters() {
            ValidationResult result = engine.validate("function foo() { if (true) {");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().size()).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("JSX validation")
    class JsxValidation {

        @Test
        @DisplayName("should validate simple JSX in JSX mode")
        void shouldValidateSimpleJsxInJsxMode() {
            engine.enableJsxMode();
            ValidationResult result = engine.validate("<div>hello</div>");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should detect unclosed JSX tags")
        void shouldDetectUnclosedJsxTags() {
            engine.enableJsxMode();
            ValidationResult result = engine.validate("<div>hello");
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("should detect mismatched JSX tags")
        void shouldDetectMismatchedJsxTags() {
            engine.enableJsxMode();
            ValidationResult result = engine.validate("<div>hello</span>");
            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("unclosed strings")
    class UnclosedStrings {

        @Test
        @DisplayName("should detect unclosed single-quoted string")
        void shouldDetectUnclosedSingleQuotedString() {
            ValidationResult result = engine.validate("const s = 'hello");
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("should detect unclosed double-quoted string")
        void shouldDetectUnclosedDoubleQuotedString() {
            ValidationResult result = engine.validate("const s = \"hello");
            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("error details")
    class ErrorDetails {

        @Test
        @DisplayName("should include line number in error")
        void shouldIncludeLineNumberInError() {
            String source = """
                    let x = 1;
                    function foo() {
                    """;
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(2);
        }

        @Test
        @DisplayName("should include column number in error")
        void shouldIncludeColumnNumberInError() {
            ValidationResult result = engine.validate("function foo() {");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getColumn()).isPositive();
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle code with only comments")
        void shouldHandleCodeWithOnlyComments() {
            ValidationResult result = engine.validate("// this is a comment\n/* block comment */");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should handle code with only whitespace")
        void shouldHandleCodeWithOnlyWhitespace() {
            ValidationResult result = engine.validate("   \n\n\t\t  \n  ");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should handle deeply nested structures")
        void shouldHandleDeeplyNestedStructures() {
            String source = "(((((((((())))))))))";
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should handle code with unicode characters")
        void shouldHandleCodeWithUnicodeCharacters() {
            ValidationResult result = engine.validate("const greeting = 'こんにちは';");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should handle very long code")
        void shouldHandleVeryLongCode() {
            String source = "let x = 1;\n".repeat(1000);
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }
    }
}
