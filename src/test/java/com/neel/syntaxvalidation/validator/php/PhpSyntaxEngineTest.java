package com.neel.syntaxvalidation.validator.php;

import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PhpSyntaxEngine")
class PhpSyntaxEngineTest {

    private PhpSyntaxEngine engine;

    @BeforeEach void setUp() { engine = new PhpSyntaxEngine(); }

    @Nested @DisplayName("empty and null input")
    class EmptyAndNullInput {
        @Test void nullSource_returnsValid() { assertThat(engine.validate(null).isValid()).isTrue(); }
        @Test void emptySource_returnsValid() { assertThat(engine.validate("").isValid()).isTrue(); }
        @Test void whitespaceOnly_returnsValid() { assertThat(engine.validate("   \n\t  ").isValid()).isTrue(); }
    }

    @Nested @DisplayName("PHP tags")
    class PhpTags {
        @ParameterizedTest @DisplayName("valid PHP tags")
        @ValueSource(strings = { "<?php echo 'hello'; ?>", "<?='hello'?>", "<?php\n$x = 1;\n?>", "<?php $x = 1; ?>" })
        void validTags(String src) { assertThat(engine.validate(src).isValid()).isTrue(); }
        @Test void purePhpWithoutClosingTag() { assertThat(engine.validate("<?php\n$x = 1;\necho $x;\n").isValid()).isTrue(); }
        @Test void mixedHtmlAndPhp() { assertThat(engine.validate("<html><body>\n<?php echo 'Hello'; ?>\n</body></html>").isValid()).isTrue(); }
    }

    @Nested @DisplayName("variables and data types")
    class VariablesAndDataTypes {
        @Test void basicVariables() {
            assertThat(engine.validate("<?php\n$s = 'hello';\n$i = 42;\n$f = 3.14;\n$b = true;\n$n = null;\n$a = [1,2];\n$h = 0xFF;\n$bin = 0b1010;\n$o = 0o77;\n$e = 1.5e10;\n?>").isValid()).isTrue();
        }
        @Test void numericUnderscores() { assertThat(engine.validate("<?php\n$m = 1_000_000;\n?>").isValid()).isTrue(); }
        @Test void stringTypes() { assertThat(engine.validate("<?php\n$s = 'single';\n$d = \"double\";\n?>").isValid()).isTrue(); }
        @Test void heredoc() {
            String src = "<?php\n$v = <<<EOT\nhello\nEOT;\n?>";
            assertThat(engine.validate(src).isValid()).isTrue();
        }
    }

    @Nested @DisplayName("control structures")
    class ControlStructures {
        @Test void ifElseifElse() {
            assertThat(engine.validate("<?php\nif ($x > 0) {\n    echo 'p';\n} elseif ($x < 0) {\n    echo 'n';\n} else {\n    echo 'z';\n}\n?>").isValid()).isTrue();
        }
        @Test void alternativeIfSyntax() { assertThat(engine.validate("<?php if ($h): ?>\n<h1>H</h1>\n<?php endif; ?>").isValid()).isTrue(); }
        @Test void forLoop() { assertThat(engine.validate("<?php\nfor ($i = 0; $i < 10; $i++) { echo $i; }\n?>").isValid()).isTrue(); }
        @Test void foreachLoop() { assertThat(engine.validate("<?php\nforeach ([1,2,3] as $i) { echo $i; }\n?>").isValid()).isTrue(); }
        @Test void whileLoop() { assertThat(engine.validate("<?php\n$i = 0;\nwhile ($i < 10) { $i++; }\n?>").isValid()).isTrue(); }
        @Test void doWhileLoop() { assertThat(engine.validate("<?php\n$i = 0;\ndo { $i++; } while ($i < 10);\n?>").isValid()).isTrue(); }
        @Test void switchStatement() { assertThat(engine.validate("<?php\nswitch ($c) {\n    case 'a': echo 'A'; break;\n    default: echo '?';\n}\n?>").isValid()).isTrue(); }
        @Test void matchExpression() { assertThat(engine.validate("<?php\n$r = match($s) { 200 => 'OK', 404 => 'NF', default => '?' };\n?>").isValid()).isTrue(); }
    }

    @Nested @DisplayName("functions")
    class Functions {
        @Test void basicFunction() { assertThat(engine.validate("<?php\nfunction greet(string $n): string {\n    return \"Hello\";\n}\n?>").isValid()).isTrue(); }
        @Test void defaultParameters() { assertThat(engine.validate("<?php\nfunction f(string $a, string $b = 'x'): void {}\n?>").isValid()).isTrue(); }
        @Test void variadicParameters() { assertThat(engine.validate("<?php\nfunction sum(int ...$n): int { return array_sum($n); }\n?>").isValid()).isTrue(); }
        @Test void unionTypes() { assertThat(engine.validate("<?php\nfunction fmt(int|float|string $v): string { return (string)$v; }\n?>").isValid()).isTrue(); }
        @Test void nullableType() { assertThat(engine.validate("<?php\nfunction find(int $id): ?User { return null; }\n?>").isValid()).isTrue(); }
        @Test void neverReturnType() { assertThat(engine.validate("<?php\nfunction err(string $m): never { throw new RuntimeException($m); }\n?>").isValid()).isTrue(); }
        @Test void referenceReturn() { assertThat(engine.validate("<?php\nfunction &get(): int { return $GLOBALS['v']; }\n?>").isValid()).isTrue(); }
    }

    @Nested @DisplayName("closures and arrow functions")
    class ClosuresAndArrowFunctions {
        @Test void basicClosure() { assertThat(engine.validate("<?php\n$f = function(string $n): string { return \"Hello\"; };\n?>").isValid()).isTrue(); }
        @Test void closureWithUse() { assertThat(engine.validate("<?php\n$msg = 'Hi';\n$f = function(string $n) use ($msg): string { return $msg; };\n?>").isValid()).isTrue(); }
        @Test void arrowFunction() { assertThat(engine.validate("<?php\n$d = array_map(fn($x) => $x * 2, [1,2,3]);\n?>").isValid()).isTrue(); }
    }

    @Nested @DisplayName("classes")
    class Classes {
        @Test void basicClass() {
            assertThat(engine.validate("<?php\nclass User {\n    public string $name;\n    public function __construct(string $name) {\n        $this->name = $name;\n    }\n    public function getName(): string { return $this->name; }\n}\n?>").isValid()).isTrue();
        }
        @Test void classInheritance() { assertThat(engine.validate("<?php\nclass Animal {}\nclass Dog extends Animal {}\n?>").isValid()).isTrue(); }
        @Test void abstractClass() { assertThat(engine.validate("<?php\nabstract class Shape {\n    abstract public function area(): float;\n}\n?>").isValid()).isTrue(); }
        @Test void constructorPromotion() { assertThat(engine.validate("<?php\nclass Point {\n    public function __construct(\n        public readonly float $x = 0.0,\n        public readonly float $y = 0.0,\n    ) {}\n}\n?>").isValid()).isTrue(); }
        @Test void readonlyClass() { assertThat(engine.validate("<?php\nreadonly class Coord {\n    public function __construct(\n        public float $lat,\n        public float $lng,\n    ) {}\n}\n?>").isValid()).isTrue(); }
    }

    @Nested @DisplayName("interfaces")
    class Interfaces {
        @Test void basicInterface() { assertThat(engine.validate("<?php\ninterface Renderable {\n    public function render(): string;\n}\n?>").isValid()).isTrue(); }
        @Test void interfaceInheritance() { assertThat(engine.validate("<?php\ninterface A { public function a(): void; }\ninterface B extends A { public function b(): void; }\n?>").isValid()).isTrue(); }
        @Test void classImplementsInterface() { assertThat(engine.validate("<?php\ninterface Logger { public function log(string $m): void; }\nclass FileLogger implements Logger { public function log(string $m): void {}\n}\n?>").isValid()).isTrue(); }
    }

    @Nested @DisplayName("traits")
    class Traits {
        @Test void basicTrait() { assertThat(engine.validate("<?php\ntrait Timestamps {\n    public function createdAt(): DateTime { return $this->created_at; }\n}\n?>").isValid()).isTrue(); }
        @Test void usingTrait() { assertThat(engine.validate("<?php\ntrait Loggable { public function log(string $m): void {} }\nclass Order { use Loggable; }\n?>").isValid()).isTrue(); }
        @Test void traitConflictResolution() {
            assertThat(engine.validate("<?php\ntrait A { public function f() { echo 'a'; } }\ntrait B { public function f() { echo 'b'; } }\nclass C { use A, B { B::f insteadof A; } }\n?>").isValid()).isTrue();
        }
    }

    @Nested @DisplayName("enums (PHP 8.1+)")
    class Enums {
        @Test void basicEnum() { assertThat(engine.validate("<?php\nenum Suit { case Hearts; case Diamonds; }\n?>").isValid()).isTrue(); }
        @Test void backedEnumString() { assertThat(engine.validate("<?php\nenum Color: string { case Red = 'red'; case Blue = 'blue'; }\n?>").isValid()).isTrue(); }
        @Test void backedEnumInt() { assertThat(engine.validate("<?php\nenum Status: int { case OK = 200; case NF = 404; }\n?>").isValid()).isTrue(); }
        @Test void enumImplements() {
            assertThat(engine.validate("<?php\ninterface HasLabel { public function label(): string; }\nenum Priority: int implements HasLabel {\n    case Low = 1;\n    case High = 10;\n    public function label(): string { return match($this) { Priority::Low => 'L', Priority::High => 'H' }; }\n}\n?>").isValid()).isTrue();
        }
    }

    @Nested @DisplayName("namespaces and use statements")
    class Namespaces {
        @Test void namespaceDeclaration() { assertThat(engine.validate("<?php\nnamespace App\\Models;\nclass User { public string $name; }\n?>").isValid()).isTrue(); }
        @Test void useStatements() {
            // Simplified test to avoid namespace resolution issues
            String source = "<?php\nuse App\\Models\\User;\nuse function array_map;\nuse const PHP_EOL;\nclass Controller {}\n?>";
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).as("Errors: " + result.getErrors()).isTrue();
        }
    }

    @Nested @DisplayName("generators")
    class Generators {
        @Test void basicGenerator() { assertThat(engine.validate("<?php\nfunction gen(): Generator { yield 1; yield 2; }\n?>").isValid()).isTrue(); }
        @Test void yieldFrom() { assertThat(engine.validate("<?php\nfunction inner(): Generator { yield 1; }\nfunction outer(): Generator { yield 's'; yield from inner(); yield 'e'; }\n?>").isValid()).isTrue(); }
    }

    @Nested @DisplayName("exception handling")
    class ExceptionHandling {
        @Test void tryCatchFinally() {
            assertThat(engine.validate("<?php\ntry {\n    riskyOp();\n} catch (ValueError $e) {\n    echo $e->getMessage();\n} finally {\n    cleanup();\n}\n?>").isValid()).isTrue();
        }
    }

    @Nested @DisplayName("attributes (PHP 8.0+)")
    class Attributes {
        @Test void basicAttributes() {
            String source = "<?php\n#[Route('/api')]\nclass Controller { #[Route('/list')] public function list(): array { return []; } }\n?>";
            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).as("Errors: " + result.getErrors()).isTrue();
        }
    }

    @Nested @DisplayName("error cases")
    class ErrorCases {
        @Test void unclosedParenthesis() {
            ValidationResult r = engine.validate("<?php\n$x = (1 + 2;\n?>");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).isNotEmpty();
        }
        @Test void unmatchedClosingBrace() {
            ValidationResult r = engine.validate("<?php\nfunction foo() { echo 'hi'; }\n}\n?>");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).isNotEmpty();
        }
        @Test void unclosedSingleString() {
            ValidationResult r = engine.validate("<?php $x = 'hello world;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).isNotEmpty();
        }
        @Test void unclosedDoubleString() {
            ValidationResult r = engine.validate("<?php $x = \"hello world;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).isNotEmpty();
        }
        @Test void unclosedMultiLineComment() {
            ValidationResult r = engine.validate("<?php\n/* unclosed comment\n$x = 1;\n?>");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).isNotEmpty();
        }
        @Test void unclosedBracket() {
            ValidationResult r = engine.validate("<?php\n$arr = [1, 2, 3;\n?>");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).isNotEmpty();
        }
    }

    @Nested @DisplayName("edge cases")
    class EdgeCases {
        @Test void emptyPhpBlock() { assertThat(engine.validate("<?php ?>").isValid()).isTrue(); }
        @Test void onlyComments() { assertThat(engine.validate("<?php\n// comment\n# comment\n/* block */\n?>").isValid()).isTrue(); }
        @Test void deeplyNested() {
            assertThat(engine.validate("<?php\nif (true) {\n    for ($i = 0; $i < 10; $i++) {\n        foreach ($items as $item) {\n            while ($item->hasNext()) {\n                switch ($item->type()) {\n                    case 'a': echo 'A'; break;\n                    default: echo '?';\n                }\n            }\n        }\n    }\n}\n?>").isValid()).isTrue();
        }
        @Test void complexPhp8xCode() {
            String source = "<?php\n" +
                    "declare(strict_types=1);\n" +
                    "\n" +
                    "namespace App\\Services;\n" +
                    "\n" +
                    "use App\\Contracts\\RepositoryInterface;\n" +
                    "use App\\DTOs\\UserDTO;\n" +
                    "\n" +
                    "readonly class UserService {\n" +
                    "    public function __construct(\n" +
                    "        private RepositoryInterface $repository,\n" +
                    "        private CacheManager $cache,\n" +
                    "    ) {}\n" +
                    "\n" +
                    "    public function findUser(int $id): UserDTO {\n" +
                    "        return $this->cache->remember(\n" +
                    "            \"user:\" . $id,\n" +
                    "            fn() => $this->repository->find($id),\n" +
                    "        );\n" +
                    "    }\n" +
                    "\n" +
                    "    public function processUsers(array $ids): Generator {\n" +
                    "        foreach ($ids as $id) {\n" +
                    "            yield $id => match(true) {\n" +
                    "                $id > 100 => $this->processAdmin($id),\n" +
                    "                default => $this->processRegular($id),\n" +
                    "            };\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    private function processAdmin(int $id): array {\n" +
                    "        return ['type' => 'admin', 'id' => $id];\n" +
                    "    }\n" +
                    "\n" +
                    "    private function processRegular(int $id): array {\n" +
                    "        return ['type' => 'regular', 'id' => $id];\n" +
                    "    }\n" +
                    "}\n" +
                    "?>";
            assertThat(engine.validate(source).isValid()).isTrue();
        }
        @Test void multiplePhpBlocks() {
            assertThat(engine.validate("<!DOCTYPE html>\n<html>\n<head>\n    <title><?php echo $title; ?></title>\n</head>\n<body>\n<?php if ($loggedIn): ?>\n    <p>Welcome, <?php echo $user->name; ?>!</p>\n<?php else: ?>\n    <p>Please log in.</p>\n<?php endif; ?>\n</body>\n</html>").isValid()).isTrue();
        }
    }
}
