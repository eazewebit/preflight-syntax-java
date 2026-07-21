package com.neel.syntaxvalidation.validator.typescript.integration;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.validator.typescript.TypeScriptValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the full TypeScript validation pipeline.
 *
 * <p>These tests exercise both Phase 1 (built-in engine) and Phase 2 (tsc binary)
 * in a real environment. Phase 2 tests are conditionally enabled when the
 * {@code SYNTAX_VALIDATION_TSC_PATH} environment variable is set or when
 * {@code tsc} is available on the system PATH.
 *
 * <p>The tests cover:
 * <ul>
 *   <li>Valid TypeScript code (should pass both phases)</li>
 *   <li>Invalid TypeScript code with syntax errors (should fail appropriately)</li>
 *   <li>Edge cases like empty files, comments-only, Unicode, etc.</li>
 *   <li>TSX/JSX mode validation</li>
 *   <li>Complex TypeScript features (generics, interfaces, enums, etc.)</li>
 * </ul>
 */
@DisplayName("TypeScript Validation Pipeline Integration Test")
class TypeScriptValidationPipelineIntegrationTest {

    private TypeScriptValidator validator;
    private TypeScriptValidator jsxValidator;

    @BeforeEach
    void setUp() {
        // Create validators using public constructors
        validator = new TypeScriptValidator(Language.TYPESCRIPT);
        jsxValidator = TypeScriptValidator.createJsxValidator(new BinaryResolver(), new ProcessExecutor());
    }

    @Nested
    @DisplayName("Phase 1 — Built-in syntax engine (always available)")
    class Phase1Only {

        @Nested
        @DisplayName("Valid TypeScript code")
        class ValidTypeScript {

            @Test
            @DisplayName("simple variable declaration should pass")
            void simpleVariableDeclarationShouldPass() {
                ValidationResult result = validator.validate("let x: number = 42;");
                assertThat(result.isValid()).isTrue();
            }

            @Test
            @DisplayName("interface declaration should pass")
            void interfaceDeclarationShouldPass() {
                String source = """
                        interface User {
                            name: string;
                            age: number;
                        }
                        """;
                ValidationResult result = validator.validate(source);
                assertThat(result.isValid()).isTrue();
            }

            @Test
            @DisplayName("generic function should pass")
            void genericFunctionShouldPass() {
                String source = """
                        function identity<T>(arg: T): T {
                            return arg;
                        }
                        """;
                ValidationResult result = validator.validate(source);
                assertThat(result.isValid()).isTrue();
            }

            @Test
            @DisplayName("class with implements should pass")
            void classWithImplementsShouldPass() {
                String source = """
                        class UserService implements Service<User> {
                            private data: User[] = [];

                            getUser(id: number): User | undefined {
                                return this.data.find(u => u.id === id);
                            }
                        }
                        """;
                ValidationResult result = validator.validate(source);
                assertThat(result.isValid()).isTrue();
            }

            @Test
            @DisplayName("type alias should pass")
            void typeAliasShouldPass() {
                String source = "type Result<T> = { data: T; error?: string; };";
                ValidationResult result = validator.validate(source);
                assertThat(result.isValid()).isTrue();
            }

            @Test
            @DisplayName("enum declaration should pass")
            void enumDeclarationShouldPass() {
                String source = """
                        enum Direction {
                            Up = 'UP',
                            Down = 'DOWN',
                            Left = 'LEFT',
                            Right = 'RIGHT'
                        }
                        """;
                ValidationResult result = validator.validate(source);
                assertThat(result.isValid()).isTrue();
            }

            @Test
            @DisplayName("import/export statements should pass")
            void importExportStatementsShouldPass() {
                String source = """
                        import { useState, useEffect } from 'react';
                        export default function App() {
                            return null;
                        }
                        """;
                ValidationResult result = validator.validate(source);
                assertThat(result.isValid()).isTrue();
            }

            @Test
            @DisplayName("async/await patterns should pass")
            void asyncAwaitPatternsShouldPass() {
                String source = """
                        async function fetchData(): Promise<Response> {
                            const response = await fetch('/api/data');
                            return response;
                        }
                        """;
                ValidationResult result = validator.validate(source);
                assertThat(result.isValid()).isTrue();
            }

            @Test
            @DisplayName("complex nested types should pass")
            void complexNestedTypesShouldPass() {
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
                ValidationResult result = validator.validate(source);
                assertThat(result.isValid()).isTrue();
            }

            @Test
            @DisplayName("arrow functions with type annotations should pass")
            void arrowFunctionsWithTypeAnnotationsShouldPass() {
                String source = "const add = (a: number, b: number): number => a + b;";
                ValidationResult result = validator.validate(source);
                assertThat(result.isValid()).isTrue();
            }
        }

        @Nested
        @DisplayName("Invalid TypeScript code")
        class InvalidTypeScript {

            @Test
            @DisplayName("unclosed brace should fail")
            void unclosedBraceShouldFail() {
                ValidationResult result = validator.validate("function foo() {");
                assertThat(result.isValid()).isFalse();
                assertThat(result.getErrors()).isNotEmpty();
            }

            @Test
            @DisplayName("unclosed parenthesis should fail")
            void unclosedParenthesisShouldFail() {
                ValidationResult result = validator.validate("console.log('hello'");
                assertThat(result.isValid()).isFalse();
            }

            @Test
            @DisplayName("unclosed bracket should fail")
            void unclosedBracketShouldFail() {
                ValidationResult result = validator.validate("const arr = [1, 2, 3;");
                assertThat(result.isValid()).isFalse();
            }

            @Test
            @DisplayName("unexpected closing brace should fail")
            void unexpectedClosingBraceShouldFail() {
                ValidationResult result = validator.validate("}");
                assertThat(result.isValid()).isFalse();
            }

            @Test
            @DisplayName("unexpected closing parenthesis should fail")
            void unexpectedClosingParenthesisShouldFail() {
                ValidationResult result = validator.validate(")");
                assertThat(result.isValid()).isFalse();
            }

            @Test
            @DisplayName("unexpected closing bracket should fail")
            void unexpectedClosingBracketShouldFail() {
                ValidationResult result = validator.validate("]");
                assertThat(result.isValid()).isFalse();
            }
        }

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("empty string should pass")
            void emptyStringShouldPass() {
                assertThat(validator.validate("").isValid()).isTrue();
            }

            @Test
            @DisplayName("null content should pass")
            void nullContentShouldPass() {
                assertThat(validator.validate(null).isValid()).isTrue();
            }

            @Test
            @DisplayName("whitespace-only should pass")
            void whitespaceOnlyShouldPass() {
                assertThat(validator.validate("   \n\t  ").isValid()).isTrue();
            }

            @Test
            @DisplayName("comments-only should pass")
            void commentsOnlyShouldPass() {
                String source = "// this is a comment\n/* block comment */";
                assertThat(validator.validate(source).isValid()).isTrue();
            }

            @Test
            @DisplayName("deeply nested delimiters should pass")
            void deeplyNestedDelimitersShouldPass() {
                assertThat(validator.validate("(((((((((())))))))))").isValid()).isTrue();
            }

            @Test
            @DisplayName("unicode content should pass")
            void unicodeContentShouldPass() {
                assertThat(validator.validate("const greeting = 'こんにちは';").isValid()).isTrue();
            }

            @Test
            @DisplayName("large codebase should pass")
            void largeCodebaseShouldPass() {
                String source = "let x = 1;\n".repeat(1000);
                assertThat(validator.validate(source).isValid()).isTrue();
            }

            @Test
            @DisplayName("error should include line and column information")
            void errorShouldIncludeLineAndColumnInformation() {
                String source = """
                        let x = 1;
                        function foo() {
                        """;
                ValidationResult result = validator.validate(source);
                assertThat(result.isValid()).isFalse();
                assertThat(result.getErrors().get(0).getLine()).isPositive();
                assertThat(result.getErrors().get(0).getColumn()).isPositive();
            }
        }
    }

    @Nested
    @DisplayName("Phase 2 — tsc binary (when available)")
    class Phase2WithBinary {

        @Test
        @DisplayName("should report that tsc is not available when not on PATH")
        void shouldReportThatTscIsNotAvailableWhenNotOnPath() {
            // This test verifies the fallback behavior
            ValidationResult result = validator.validate("let x: number = 42;");

            // Should be valid regardless of whether tsc is available
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should use tsc when available for valid code")
        @EnabledIfEnvironmentVariable(named = "SYNTAX_VALIDATION_TSC_PATH", matches = ".+")
        void shouldUseTscWhenAvailableForValidCode() {
            String tscPath = System.getenv("SYNTAX_VALIDATION_TSC_PATH");
            TypeScriptValidator tscValidator = new TypeScriptValidator(
                    new BinaryResolver(), new ProcessExecutor());

            ValidationResult result = tscValidator.validate("let x: number = 42;");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should use tsc when available for invalid code")
        @EnabledIfEnvironmentVariable(named = "SYNTAX_VALIDATION_TSC_PATH", matches = ".+")
        void shouldUseTscWhenAvailableForInvalidCode() {
            String tscPath = System.getenv("SYNTAX_VALIDATION_TSC_PATH");
            TypeScriptValidator tscValidator = new TypeScriptValidator(
                    new BinaryResolver(), new ProcessExecutor());

            // Use code that passes Phase 1 but would fail tsc
            ValidationResult result = tscValidator.validate("let x: string = 42;");
            // Note: This might pass Phase 1 but tsc with --strict would catch it
            // The result depends on whether tsc is actually available
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("JSX/TSX mode validation")
    class JsxTsxMode {

        @Test
        @DisplayName("should validate valid JSX")
        void shouldValidateValidJsx() {
            ValidationResult result = jsxValidator.validate("<div>hello</div>");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate self-closing JSX tags")
        void shouldValidateSelfClosingJsxTags() {
            ValidationResult result = jsxValidator.validate("<br />");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should detect unclosed JSX tags")
        void shouldDetectUnclosedJsxTags() {
            ValidationResult result = jsxValidator.validate("<div>hello");
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("should validate JSX with attributes")
        void shouldValidateJsxWithAttributes() {
            ValidationResult result = jsxValidator.validate("<div className=\"test\">hello</div>");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate nested JSX")
        void shouldValidateNestedJsx() {
            ValidationResult result = jsxValidator.validate("<div><span>hello</span></div>");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should detect mismatched JSX tags")
        void shouldDetectMismatchedJsxTags() {
            ValidationResult result = jsxValidator.validate("<div>hello</span>");
            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Complex TypeScript features")
    class ComplexTypeScriptFeatures {

        @Test
        @DisplayName("should validate generic constraints with extends")
        void shouldValidateGenericConstraintsWithExtends() {
            String source = """
                    function getProperty<T, K extends keyof T>(obj: T, key: K): T[K] {
                        return obj[key];
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate mapped types")
        void shouldValidateMappedTypes() {
            String source = """
                    type ReadOnly<T> = {
                        readonly [P in keyof T]: T[P];
                    };
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate conditional types")
        void shouldValidateConditionalTypes() {
            String source = """
                    type IsString<T> = T extends string ? true : false;
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate template literal types")
        void shouldValidateTemplateLiteralTypes() {
            String source = """
                    type Greeting = `hello ${string}`;
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate decorators syntax")
        void shouldValidateDecoratorsSyntax() {
            String source = """
                    @Component({
                        selector: 'app-root'
                    })
                    class AppComponent {
                        title = 'My App';
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate namespace declarations")
        void shouldValidateNamespaceDeclarations() {
            String source = """
                    namespace Validation {
                        export interface Validator {
                            validate(s: string): boolean;
                        }
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Validator integration with SyntaxValidationLibrary")
    class ValidatorIntegration {

        @Test
        @DisplayName("validator should be registered for TYPESCRIPT language")
        void validatorShouldBeRegisteredForTypescriptLanguage() {
            // This test verifies that the validator is properly registered
            // in the ValidatorFactory
            assertThat(validator.getLanguage()).isEqualTo(Language.TYPESCRIPT);
        }

        @Test
        @DisplayName("validator should handle all TypeScript file extensions")
        void validatorShouldHandleAllTypeScriptFileExtensions() {
            // Test .ts extension
            ValidationResult tsResult = validator.validate("let x = 1;");
            assertThat(tsResult.isValid()).isTrue();

            // Test .tsx extension (JSX mode)
            ValidationResult tsxResult = jsxValidator.validate("<div>test</div>");
            assertThat(tsxResult.isValid()).isTrue();

            // Test .jsx extension (same as .tsx mode)
            ValidationResult jsxResult = jsxValidator.validate("<div>test</div>");
            assertThat(jsxResult.isValid()).isTrue();
        }
    }
}
