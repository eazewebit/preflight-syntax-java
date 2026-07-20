package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.model.ValidationResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for {@link PythonSyntaxEngine}.
 *
 * <p>Covers the full pipeline (lexing + parsing + pattern checks),
 * including valid programs, invalid programs, edge cases, and
 * Python 3.14-specific features.
 */
@DisplayName("PythonSyntaxEngine")
class PythonSyntaxEngineTest {

    // ==================================================================
    //  Empty / null input
    // ==================================================================

    @Nested
    @DisplayName("empty and null input")
    class EmptyInput {

        @Test @DisplayName("null source is valid")
        void nullSource() { assertThat(PythonSyntaxEngine.validate(null).isValid()).isTrue(); }

        @Test @DisplayName("empty source is valid")
        void emptySource() { assertThat(PythonSyntaxEngine.validate("").isValid()).isTrue(); }

        @Test @DisplayName("blank source is valid")
        void blankSource() { assertThat(PythonSyntaxEngine.validate("   \n  \n  ").isValid()).isTrue(); }
    }

    // ==================================================================
    //  Valid programs
    // ==================================================================

    @Nested
    @DisplayName("valid programs")
    class ValidPrograms {

        @Test @DisplayName("simple assignment")
        void simpleAssignment() { assertThat(PythonSyntaxEngine.validate("x = 1\n").isValid()).isTrue(); }

        @Test @DisplayName("function definition")
        void functionDef() {
            assertThat(PythonSyntaxEngine.validate("def hello():\n    print('Hello, World!')\n").isValid()).isTrue();
        }

        @Test @DisplayName("class definition")
        void classDef() {
            assertThat(PythonSyntaxEngine.validate("class MyClass:\n    def __init__(self):\n        self.x = 1\n").isValid()).isTrue();
        }

        @Test @DisplayName("if/elif/else")
        void ifElifElse() {
            assertThat(PythonSyntaxEngine.validate("x = 1\nif x > 0:\n    pass\nelif x == 0:\n    pass\nelse:\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("for loop with else")
        void forLoopElse() {
            assertThat(PythonSyntaxEngine.validate("for i in range(10):\n    pass\nelse:\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("while loop")
        void whileLoop() { assertThat(PythonSyntaxEngine.validate("while True:\n    break\n").isValid()).isTrue(); }

        @Test @DisplayName("try/except/finally")
        void tryExceptFinally() {
            assertThat(PythonSyntaxEngine.validate("try:\n    pass\nexcept ValueError:\n    pass\nfinally:\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("with statement")
        void withStatement() {
            assertThat(PythonSyntaxEngine.validate("with open('file') as f:\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("lambda expression")
        void lambdaExpression() { assertThat(PythonSyntaxEngine.validate("f = lambda x, y: x + y\n").isValid()).isTrue(); }

        @Test @DisplayName("decorator")
        void decorator() {
            assertThat(PythonSyntaxEngine.validate("@property\ndef name(self):\n    return self._name\n").isValid()).isTrue();
        }

        @Test @DisplayName("async function")
        void asyncFunction() {
            assertThat(PythonSyntaxEngine.validate("async def fetch():\n    await something()\n").isValid()).isTrue();
        }

        @Test @DisplayName("generator function")
        void generatorFunction() {
            assertThat(PythonSyntaxEngine.validate("def gen():\n    yield 1\n    yield 2\n").isValid()).isTrue();
        }

        @Test @DisplayName("list comprehension")
        void listComprehension() {
            assertThat(PythonSyntaxEngine.validate("squares = [x**2 for x in range(10)]\n").isValid()).isTrue();
        }

        @Test @DisplayName("dictionary comprehension")
        void dictComprehension() {
            assertThat(PythonSyntaxEngine.validate("d = {k: v for k, v in items}\n").isValid()).isTrue();
        }

        @Test @DisplayName("set comprehension")
        void setComprehension() {
            assertThat(PythonSyntaxEngine.validate("s = {x for x in range(10)}\n").isValid()).isTrue();
        }

        @Test @DisplayName("multiple assignment")
        void multipleAssignment() { assertThat(PythonSyntaxEngine.validate("a = b = c = 1\n").isValid()).isTrue(); }

        @Test @DisplayName("tuple unpacking")
        void tupleUnpacking() { assertThat(PythonSyntaxEngine.validate("a, b, c = 1, 2, 3\n").isValid()).isTrue(); }

        @Test @DisplayName("global and nonlocal")
        void globalNonlocal() {
            assertThat(PythonSyntaxEngine.validate("x = 0\ndef f():\n    global x\n    x = 1\n").isValid()).isTrue();
        }

        @Test @DisplayName("assert statement")
        void assertStatement() {
            assertThat(PythonSyntaxEngine.validate("assert x > 0, 'x must be positive'\n").isValid()).isTrue();
        }

        @Test @DisplayName("raise statement")
        void raiseStatement() {
            assertThat(PythonSyntaxEngine.validate("raise ValueError('bad value')\n").isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Invalid programs
    // ==================================================================

    @Nested
    @DisplayName("invalid programs")
    class InvalidPrograms {

        @Test @DisplayName("unclosed parenthesis")
        void unclosedParen() { assertThat(PythonSyntaxEngine.validate("x = (1 + 2\n").isValid()).isFalse(); }

        @Test @DisplayName("unclosed bracket")
        void unclosedBracket() { assertThat(PythonSyntaxEngine.validate("x = [1, 2, 3\n").isValid()).isFalse(); }

        @Test @DisplayName("unclosed brace")
        void unclosedBrace() { assertThat(PythonSyntaxEngine.validate("x = {'a': 1\n").isValid()).isFalse(); }

        @Test @DisplayName("mismatched delimiters")
        void mismatchedDelimiters() { assertThat(PythonSyntaxEngine.validate("x = (1 + 2]\n").isValid()).isFalse(); }

        @Test @DisplayName("literal as assignment target")
        void literalAsTarget() { assertThat(PythonSyntaxEngine.validate("1 = x\n").isValid()).isFalse(); }

        @Test @DisplayName("from without import")
        void fromWithoutImport() { assertThat(PythonSyntaxEngine.validate("from os\n").isValid()).isFalse(); }

        @Test @DisplayName("def without name")
        void defWithoutName() { assertThat(PythonSyntaxEngine.validate("def ():\n    pass\n").isValid()).isFalse(); }

        @Test @DisplayName("try without except or finally")
        void tryWithoutExcept() { assertThat(PythonSyntaxEngine.validate("try:\n    pass\n").isValid()).isFalse(); }

        @Test @DisplayName("double equals confusion")
        void doubleEqualsConfusion() { assertThat(PythonSyntaxEngine.validate("x = = 1\n").isValid()).isFalse(); }

        @Test @DisplayName("unterminated string")
        void unterminatedString() { assertThat(PythonSyntaxEngine.validate("x = 'hello\n").isValid()).isFalse(); }

        @Test @DisplayName("Python 2 print statement")
        void python2Print() {
            ValidationResult r = PythonSyntaxEngine.validate("print hello\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Python 2"));
        }

        @Test @DisplayName("Python 2 except syntax")
        void python2Except() {
            ValidationResult r = PythonSyntaxEngine.validate("try:\n    pass\nexcept Exception, e:\n    pass\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Python 2"));
        }
    }

    // ==================================================================
    //  Python 3.14 specific features
    // ==================================================================

    @Nested
    @DisplayName("Python 3.14 features")
    class Python314Features {

        @Test @DisplayName("match statement (PEP 634)")
        void matchStatement() {
            assertThat(PythonSyntaxEngine.validate("match command:\n    case 'quit':\n        pass\n    case _:\n        pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("type alias (PEP 695)")
        void typeAlias() {
            assertThat(PythonSyntaxEngine.validate("type Point = tuple[int, int]\n").isValid()).isTrue();
        }

        @Test @DisplayName("f-string with nested expressions")
        void fStringNested() {
            assertThat(PythonSyntaxEngine.validate("x = f'value: {obj.method(arg)}'\n").isValid()).isTrue();
        }

        @Test @DisplayName("t-string (PEP 750)")
        void tString() { assertThat(PythonSyntaxEngine.validate("x = t'hello {name}'\n").isValid()).isTrue(); }

        @Test @DisplayName("walrus operator")
        void walrusOperator() {
            assertThat(PythonSyntaxEngine.validate("if (n := len(data)) > 10:\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("async for loop")
        void asyncFor() {
            assertThat(PythonSyntaxEngine.validate("async def f():\n    async for item in aiter():\n        pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("async with statement")
        void asyncWith() {
            assertThat(PythonSyntaxEngine.validate("async def f():\n    async with lock:\n        pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("positional-only parameters (PEP 570)")
        void positionalOnlyParams() {
            assertThat(PythonSyntaxEngine.validate("def foo(a, b, /, c, d):\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("keyword-only parameters")
        void keywordOnlyParams() {
            assertThat(PythonSyntaxEngine.validate("def foo(a, *, b, c):\n    pass\n").isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Mixed tabs and spaces
    // ==================================================================

    @Nested
    @DisplayName("mixed tabs and spaces")
    class MixedTabsSpaces {

        @Test @DisplayName("mixed tabs and spaces in indentation")
        void mixedIndentation() {
            ValidationResult r = PythonSyntaxEngine.validate("if True:\n\t pass\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getMessage().contains("Mixed tabs and spaces"));
        }

        @Test @DisplayName("consistent spaces")
        void consistentSpaces() {
            assertThat(PythonSyntaxEngine.validate("if True:\n    pass\n").isValid()).isTrue();
        }

        @Test @DisplayName("consistent tabs")
        void consistentTabs() {
            assertThat(PythonSyntaxEngine.validate("if True:\n\tpass\n").isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Edge cases
    // ==================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test @DisplayName("very large source code")
        void largeSource() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) sb.append("x").append(i).append(" = ").append(i).append("\n");
            assertThat(PythonSyntaxEngine.validate(sb.toString()).isValid()).isTrue();
        }

        @Test @DisplayName("source with only comments")
        void onlyComments() { assertThat(PythonSyntaxEngine.validate("# comment 1\n# comment 2\n# comment 3\n").isValid()).isTrue(); }

        @Test @DisplayName("single-character source")
        void singleChar() { assertThat(PythonSyntaxEngine.validate("x\n").isValid()).isTrue(); }

        @Test @DisplayName("deeply nested structures")
        void deeplyNested() {
            assertThat(PythonSyntaxEngine.validate("x = " + "(".repeat(50) + "1" + ")".repeat(50) + "\n").isValid()).isTrue();
        }

        @Test @DisplayName("multiple errors are all reported")
        void multipleErrors() {
            ValidationResult r = PythonSyntaxEngine.validate("1 = x\nx = (\nfrom os\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test @DisplayName("result has descriptive message when valid")
        void validMessage() {
            ValidationResult r = PythonSyntaxEngine.validate("x = 1\n");
            assertThat(r.isValid()).isTrue();
            assertThat(r.getMessage()).containsIgnoringCase("valid");
        }

        @Test @DisplayName("result has descriptive message when invalid")
        void invalidMessage() {
            ValidationResult r = PythonSyntaxEngine.validate("1 = x\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getMessage()).containsIgnoringCase("error");
        }

        @Test @DisplayName("error messages include line numbers")
        void errorMessagesIncludeLineNumbers() {
            ValidationResult r = PythonSyntaxEngine.validate("x = 1\ny = 2\n1 = z\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getLine() > 0);
        }

        @Test @DisplayName("error messages include column numbers")
        void errorMessagesIncludeColumns() {
            ValidationResult r = PythonSyntaxEngine.validate("1 = x\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).anyMatch(e -> e.getColumn() > 0);
        }
    }

    // ==================================================================
    //  Complete programs
    // ==================================================================

    @Nested
    @DisplayName("complete programs")
    class CompletePrograms {

        @Test @DisplayName("fizzbuzz program")
        void fizzbuzz() {
            String source = """
                    for i in range(1, 101):
                        if i % 15 == 0:
                            print("FizzBuzz")
                        elif i % 3 == 0:
                            print("Fizz")
                        elif i % 5 == 0:
                            print("Buzz")
                        else:
                            print(i)
                    """;
            assertThat(PythonSyntaxEngine.validate(source).isValid()).isTrue();
        }

        @Test @DisplayName("class with multiple methods")
        void classWithMethods() {
            String source = """
                    class Calculator:
                        def __init__(self):
                            self.result = 0

                        def add(self, x):
                            self.result += x
                            return self

                        def subtract(self, x):
                            self.result -= x
                            return self

                        def get_result(self):
                            return self.result
                    """;
            assertThat(PythonSyntaxEngine.validate(source).isValid()).isTrue();
        }

        @Test @DisplayName("decorator pattern")
        void decoratorPattern() {
            String source = """
                    def memoize(func):
                        cache = {}
                        def wrapper(*args):
                            if args not in cache:
                                cache[args] = func(*args)
                            return cache[args]
                        return wrapper

                    @memoize
                    def fibonacci(n):
                        if n < 2:
                            return n
                        return fibonacci(n - 1) + fibonacci(n - 2)
                    """;
            assertThat(PythonSyntaxEngine.validate(source).isValid()).isTrue();
        }

        @Test @DisplayName("context manager pattern")
        void contextManager() {
            String source = """
                    class FileManager:
                        def __init__(self, filename, mode):
                            self.filename = filename
                            self.mode = mode

                        def __enter__(self):
                            self.file = open(self.filename, self.mode)
                            return self.file

                        def __exit__(self, exc_type, exc_val, exc_tb):
                            self.file.close()
                    """;
            assertThat(PythonSyntaxEngine.validate(source).isValid()).isTrue();
        }

        @Test @DisplayName("dataclass-like pattern")
        void dataclassPattern() {
            String source = """
                    from dataclasses import dataclass
                    from typing import Optional

                    @dataclass
                    class User:
                        name: str
                        age: int
                        email: Optional[str] = None

                        def greet(self) -> str:
                            return f"Hello, I'm {self.name}"
                    """;
            assertThat(PythonSyntaxEngine.validate(source).isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Error message quality
    // ==================================================================

    @Nested
    @DisplayName("error message quality")
    class ErrorMessageQuality {

        @Test @DisplayName("unclosed paren error is descriptive")
        void unclosedParenMessage() {
            ValidationResult r = PythonSyntaxEngine.validate("x = (1 + 2\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getMessage())
                    .contains("Unclosed parenthesis").contains("line");
        }

        @Test @DisplayName("literal target error mentions the literal")
        void literalTargetMessage() {
            ValidationResult r = PythonSyntaxEngine.validate("1 = x\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getMessage())
                    .contains("Invalid assignment target").contains("1");
        }

        @Test @DisplayName("from import error is clear")
        void fromImportMessage() {
            ValidationResult r = PythonSyntaxEngine.validate("from import os\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getMessage()).contains("Expected module name");
        }

        @Test @DisplayName("try block error suggests correct structure")
        void tryBlockMessage() {
            ValidationResult r = PythonSyntaxEngine.validate("try:\n    pass\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getMessage()).contains("except").contains("finally");
        }
    }
}
