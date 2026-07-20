package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.model.ValidationResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for {@link PythonParser}.
 *
 * <p>Covers delimiter balance, compound statement colon checks,
 * assignment target validation, import structure, def/class headers,
 * try block structure, match/case syntax, async/await validity,
 * and edge cases.
 */
@DisplayName("PythonParser")
class PythonParserTest {

    private ValidationResult parse(String source) {
        PythonLexer lexer = new PythonLexer(source);
        List<PythonToken> tokens = lexer.tokenize();
        return PythonParser.validate(tokens);
    }

    // ==================================================================
    //  Empty / null input
    // ==================================================================

    @Nested
    @DisplayName("empty and null input")
    class EmptyInput {

        @Test
        @DisplayName("null tokens returns valid")
        void nullTokens() {
            assertThat(PythonParser.validate(null).isValid()).isTrue();
        }

        @Test
        @DisplayName("empty token list returns valid")
        void emptyTokens() {
            assertThat(PythonParser.validate(List.of()).isValid()).isTrue();
        }

        @Test
        @DisplayName("only EOF returns valid")
        void onlyEndmarker() {
            assertThat(PythonParser.validate(new PythonLexer("").tokenize()).isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Delimiter balance
    // ==================================================================

    @Nested
    @DisplayName("delimiter balance")
    class DelimiterBalance {

        @Test @DisplayName("balanced parentheses are valid")
        void balancedParens() { assertThat(parse("x = (1 + 2)\n").isValid()).isTrue(); }

        @Test @DisplayName("balanced brackets are valid")
        void balancedBrackets() { assertThat(parse("x = [1, 2, 3]\n").isValid()).isTrue(); }

        @Test @DisplayName("balanced braces are valid")
        void balancedBraces() { assertThat(parse("x = {'a': 1}\n").isValid()).isTrue(); }

        @Test @DisplayName("unmatched closing parenthesis")
        void unmatchedClosingParen() {
            ValidationResult r = parse("x = )\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Unmatched closing parenthesis"));
        }

        @Test @DisplayName("unmatched opening parenthesis")
        void unmatchedOpeningParen() {
            ValidationResult r = parse("x = (\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Unclosed parenthesis"));
        }

        @Test @DisplayName("unmatched closing bracket")
        void unmatchedClosingBracket() {
            ValidationResult r = parse("x = ]\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Unmatched closing bracket"));
        }

        @Test @DisplayName("unmatched opening bracket")
        void unmatchedOpeningBracket() {
            ValidationResult r = parse("x = [\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Unclosed bracket"));
        }

        @Test @DisplayName("unmatched closing brace")
        void unmatchedClosingBrace() {
            ValidationResult r = parse("x = }\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Unmatched closing brace"));
        }

        @Test @DisplayName("unmatched opening brace")
        void unmatchedOpeningBrace() {
            ValidationResult r = parse("x = {\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Unclosed brace"));
        }

        @Test @DisplayName("mismatched delimiters")
        void mismatchedDelimiters() {
            ValidationResult r = parse("x = (]\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Mismatched closing delimiter"));
        }

        @Test @DisplayName("deeply nested balanced delimiters")
        void deeplyNested() { assertThat(parse("x = (((((1)))))\n").isValid()).isTrue(); }
    }

    // ==================================================================
    //  Assignment targets
    // ==================================================================

    @Nested
    @DisplayName("assignment targets")
    class AssignmentTargets {

        @Test @DisplayName("valid variable assignment")
        void validAssignment() { assertThat(parse("x = 1\n").isValid()).isTrue(); }

        @Test @DisplayName("valid augmented assignment")
        void augmentedAssignment() { assertThat(parse("x += 1\n").isValid()).isTrue(); }

        @Test @DisplayName("literal as assignment target")
        void literalAsTarget() {
            ValidationResult r = parse("1 = x\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Invalid assignment target"));
        }

        @Test @DisplayName("string literal as target")
        void stringAsTarget() {
            ValidationResult r = parse("'hello' = x\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Invalid assignment target"));
        }

        @Test @DisplayName("True as assignment target")
        void trueAsTarget() {
            ValidationResult r = parse("True = x\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Invalid assignment target"));
        }

        @Test @DisplayName("None as assignment target")
        void noneAsTarget() {
            ValidationResult r = parse("None = x\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Invalid assignment target"));
        }

        @Test @DisplayName("tuple unpacking is valid")
        void tupleUnpacking() { assertThat(parse("a, b = 1, 2\n").isValid()).isTrue(); }
    }

    // ==================================================================
    //  Import structure
    // ==================================================================

    @Nested
    @DisplayName("import structure")
    class ImportStructure {

        @Test @DisplayName("valid import")
        void validImport() { assertThat(parse("import os\n").isValid()).isTrue(); }

        @Test @DisplayName("valid from import")
        void validFromImport() { assertThat(parse("from os import path\n").isValid()).isTrue(); }

        @Test @DisplayName("valid from import multiple names")
        void validFromImportMultiple() { assertThat(parse("from os import path, getcwd\n").isValid()).isTrue(); }

        @Test @DisplayName("valid from import star")
        void validFromImportStar() { assertThat(parse("from os import *\n").isValid()).isTrue(); }

        @Test @DisplayName("from import with dot module")
        void fromImportDotModule() { assertThat(parse("from os.path import join\n").isValid()).isTrue(); }

        @Test @DisplayName("from without import keyword")
        void fromWithoutImport() {
            ValidationResult r = parse("from os\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Expected 'import'"));
        }

        @Test @DisplayName("from without module name")
        void fromWithoutModule() {
            ValidationResult r = parse("from import os\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Expected module name"));
        }

        @Test @DisplayName("import with as alias")
        void importWithAlias() { assertThat(parse("import os as operating_system\n").isValid()).isTrue(); }
    }

    // ==================================================================
    //  Def / class headers
    // ==================================================================

    @Nested
    @DisplayName("def / class headers")
    class DefClassHeaders {

        @Test @DisplayName("valid function definition")
        void validDef() { assertThat(parse("def foo():\n    pass\n").isValid()).isTrue(); }

        @Test @DisplayName("valid class definition")
        void validClass() { assertThat(parse("class Foo:\n    pass\n").isValid()).isTrue(); }

        @Test @DisplayName("class with inheritance")
        void classWithInheritance() { assertThat(parse("class Foo(Bar):\n    pass\n").isValid()).isTrue(); }

        @Test @DisplayName("def without name")
        void defWithoutName() {
            ValidationResult r = parse("def ():\n    pass\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Expected identifier after 'def'"));
        }

        @Test @DisplayName("class without name")
        void classWithoutName() {
            ValidationResult r = parse("class :\n    pass\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Expected identifier after 'class'"));
        }

        @Test @DisplayName("def with parameters")
        void defWithParameters() { assertThat(parse("def foo(a, b, *args, **kwargs):\n    pass\n").isValid()).isTrue(); }

        @Test @DisplayName("async def")
        void asyncDef() { assertThat(parse("async def foo():\n    pass\n").isValid()).isTrue(); }
    }

    // ==================================================================
    //  Try block structure
    // ==================================================================

    @Nested
    @DisplayName("try block structure")
    class TryBlockStructure {

        @Test @DisplayName("valid try/except")
        void validTryExcept() { assertThat(parse("try:\n    pass\nexcept:\n    pass\n").isValid()).isTrue(); }

        @Test @DisplayName("valid try/finally")
        void validTryFinally() { assertThat(parse("try:\n    pass\nfinally:\n    pass\n").isValid()).isTrue(); }

        @Test @DisplayName("valid try/except/else/finally")
        void validTryExceptElseFinally() {
            assertThat(parse("try:\n    pass\nexcept:\n    pass\nelse:\n    pass\nfinally:\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("try without except or finally")
        void tryWithoutExceptFinally() {
            ValidationResult r = parse("try:\n    pass\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("must be followed by"));
        }

        @Test @DisplayName("except with specific exception type")
        void exceptWithSpecificType() {
            assertThat(parse("try:\n    pass\nexcept ValueError:\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("except with as alias")
        void exceptWithAlias() {
            assertThat(parse("try:\n    pass\nexcept ValueError as e:\n    pass\n").isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Match/case structure (PEP 634)
    // ==================================================================

    @Nested
    @DisplayName("match/case structure")
    class MatchCaseStructure {

        @Test @DisplayName("valid match/case")
        void validMatchCase() {
            assertThat(parse("match x:\n    case 1:\n        pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("match with multiple cases")
        void multipleCases() {
            assertThat(parse("match x:\n    case 1:\n        pass\n    case 2:\n        pass\n").isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Async/await validity
    // ==================================================================

    @Nested
    @DisplayName("async/await validity")
    class AsyncAwaitValidity {

        @Test @DisplayName("await with expression")
        void awaitWithExpression() {
            assertThat(PythonParser.validate(new PythonLexer("await foo()\n").tokenize()).isValid()).isTrue();
        }

        @Test @DisplayName("await without expression")
        void awaitWithoutExpression() {
            ValidationResult r = PythonParser.validate(new PythonLexer("await\n").tokenize());
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("'await'"));
        }

        @Test @DisplayName("yield with expression")
        void yieldWithExpression() {
            assertThat(PythonParser.validate(new PythonLexer("yield 1\n").tokenize()).isValid()).isTrue();
        }

        @Test @DisplayName("bare yield")
        void bareYield() {
            assertThat(PythonParser.validate(new PythonLexer("yield\n").tokenize()).isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Duplicate tokens
    // ==================================================================

    @Nested
    @DisplayName("duplicate tokens")
    class DuplicateTokens {

        @Test @DisplayName("double equals is valid")
        void doubleEquals() { assertThat(parse("x == 1\n").isValid()).isTrue(); }

        @Test @DisplayName("double assignment (x = = 1)")
        void doubleAssignment() {
            ValidationResult r = parse("x = = 1\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Unexpected '='"));
        }
    }

    // ==================================================================
    //  Complex valid programs
    // ==================================================================

    @Nested
    @DisplayName("complex valid programs")
    class ComplexValidPrograms {

        @Test @DisplayName("function with decorators")
        void decoratedFunction() {
            assertThat(parse("@decorator\ndef foo():\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("list comprehension in assignment")
        void listComprehension() {
            assertThat(parse("x = [i for i in range(10)]\n").isValid()).isTrue();
        }

        @Test @DisplayName("dictionary comprehension")
        void dictComprehension() {
            assertThat(parse("x = {k: v for k, v in items}\n").isValid()).isTrue();
        }

        @Test @DisplayName("walrus operator in condition")
        void walrusOperator() {
            assertThat(parse("if (n := len(x)) > 10:\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("nested function definitions")
        void nestedFunctions() {
            assertThat(parse("def outer():\n    def inner():\n        pass\n    return inner\n").isValid()).isTrue();
        }

        @Test @DisplayName("class with methods")
        void classWithMethods() {
            assertThat(parse("class Foo:\n    def __init__(self):\n        pass\n\n    def method(self):\n        pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("type alias (PEP 695)")
        void typeAlias() {
            assertThat(parse("type Alias = int\n").isValid()).isTrue();
        }
    }
}
