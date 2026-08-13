# Syntax Validation Library

A pluggable Java library that validates the **syntactic correctness** of proposed
source-code modifications *before* they are applied. It is designed for
integration with MCP (Model Context Protocol) tooling so that AI-driven code
edits can be checked for syntax errors without ever touching the file on disk.

## How it works

Given a modification request (file path + inclusive line range + replacement
text), the library:

1. Detects the source language from the file extension.
2. Loads the file's current content into an in-memory cache.
3. Applies the requested change to an **in-memory copy** (the original file is
   never modified).
4. Runs the language-specific validator (e.g. `node --check` for JavaScript).
5. Returns a structured result: a boolean plus a detailed explanation including
   line numbers, error descriptions and raw tool output.

## Supported Languages

| Language    | Enum Value    | Extensions                    | External Tool      | Status      |
|-------------|---------------|-------------------------------|--------------------|-------------|
| JavaScript  | `JAVASCRIPT`  | `.js`, `.mjs`, `.cjs`, `.jsx` | `node --check`     | **Complete**|
| CSS         | `CSS`         | `.css`                        | `stylelint`        | **Complete**|
| HTML        | `HTML`        | `.html`, `.htm`, `.xhtml`     | `vnu.jar`          | **Complete**|
| PHP         | `PHP`         | `.php`, `.phtml`, `.phps`     | `php -l`           | **Complete**|
| Java        | `JAVA`        | `.java`                       | `javac`            | **Complete**|
| Python      | `PYTHON`      | `.py`                         | `python3`          | **Complete**|
| TypeScript  | `TYPESCRIPT`  | `.ts`, `.tsx`, `.jsx`          | `tsc` (optional)   | **Complete**|

### Mixed Content Support

The library automatically validates HTML/PHP files containing embedded `<style>`, `<script>`, and `<?php ... ?>` blocks via the `MixedContentValidator`. Error line numbers are remapped to original document positions.

## Technology stack

| Concern     | Choice                     |
|-------------|----------------------------|
| Build tool  | Gradle 9.3 (wrapper)       |
| Java        | 25 (via Gradle toolchain)  |
| Base package| `com.neel.syntaxvalidation`|
| Testing     | JUnit 5 + AssertJ          |

## Quick start

### Basic Modification Validation

```java
import com.neel.syntaxvalidation.SyntaxValidationLibrary;
import com.neel.syntaxvalidation.model.*;

SyntaxValidationLibrary library = new SyntaxValidationLibrary();

// Validate a JavaScript modification
ModificationRequest request = ModificationRequest.builder()
        .filePath("src/script.js")
        .fromLine(3)
        .toLine(5)
        .replacement("function add(a, b) {\n  return a + b;\n}")
        .build();

ValidationResult result = library.validate(request);

if (result.isValid()) {
    System.out.println("Safe to apply!");
} else {
    System.out.println(result.getMessage());
    result.getErrors().forEach(e ->
            System.out.printf("  line %d: %s%n", e.getLine(), e.getMessage()));
}
```

### Direct Source Validation

```java
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

// Validate Java source code directly
ValidationResult javaResult = library.validateSource(Language.JAVA,
    "public class Main {\n" +
    "    public static void main(String[] args) {\n" +
    "        System.out.println(\"Hello, World!\");\n" +
    "    }\n" +
    "}\n");

if (javaResult.isValid()) {
    System.out.println("Java syntax is valid!");
}

// Validate Python source code
ValidationResult pythonResult = library.validateSource(Language.PYTHON,
    "def greet(name):\n" +
    "    print(f\"Hello, {name}!\")\n" +
    "\n" +
    "greet(\"World\")\n");

if (pythonResult.isValid()) {
    System.out.println("Python syntax is valid!");
}
```

### Language-Specific Examples

```java
// CSS validation
ValidationResult cssResult = library.validateSource(Language.CSS,
    "body {\n" +
    "    background-color: #f0f0f0;\n" +
    "    font-family: Arial, sans-serif;\n" +
    "}\n");

// HTML validation
ValidationResult htmlResult = library.validateSource(Language.HTML,
    "<!DOCTYPE html>\n" +
    "<html>\n" +
    "<head><title>Test</title></head>\n" +
    "<body><h1>Hello</h1></body>\n" +
    "</html>\n");

// PHP validation
ValidationResult phpResult = library.validateSource(Language.PHP,
    "<?php\n" +
    "class User {\n" +
    "    public function __construct(\n" +
    "        private readonly string $name,\n" +
    "        private readonly int $age\n" +
    "    ) {}\n" +
    "}\n");
```

### Mixed Content Validation

```java
// Validate HTML with embedded CSS, JavaScript, and PHP
String mixedContent = "<!DOCTYPE html>\n" +
    "<html>\n" +
    "<head>\n" +
    "    <style>\n" +
    "        body { margin: 0; }\n" +
    "    </style>\n" +
    "</head>\n" +
    "<body>\n" +
    "    <script>\n" +
    "        console.log('Hello');\n" +
    "    </script>\n" +
    "    <?php echo 'Hi'; ?>\n" +
    "</body>\n" +
    "</html>\n";

ValidationResult result = library.validateSource(Language.HTML, mixedContent);
```

## Binary resolution strategy

Every validator accepts an optional **preferred binary path**. Resolution order:

1. If a preferred path is supplied and points to an executable, it is used.
2. Otherwise the system `PATH` is searched for the bare binary name
   (e.g. `node`), accounting for platform suffixes (`.exe`, `.cmd`, … on Windows).
3. If neither is available, validation **fails gracefully** with a clear message.

You can supply a preferred binary when constructing a validator and registering
it in the factory:

```java
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;

ValidatorFactory factory = new ValidatorFactory();
factory.register(Language.JAVASCRIPT,
        new JavaScriptValidator("/usr/local/bin/node"));
SyntaxValidationLibrary library = new SyntaxValidationLibrary(factory);
```

## Architecture

The design follows classic OOP and design-pattern principles:

- **Strategy** &mdash; [`LanguageValidator`](src/main/java/com/neel/syntaxvalidation/validator/LanguageValidator.java)
  defines the contract; each language provides its own implementation.
- **Template Method** &mdash; [`AbstractLanguageValidator`](src/main/java/com/neel/syntaxvalidation/validator/AbstractLanguageValidator.java)
  owns the shared algorithm (binary resolution, temp-file creation, process
  execution, graceful error handling) and delegates language-specific steps to
  subclasses.
- **Factory / Registry** &mdash; [`ValidatorFactory`](src/main/java/com/neel/syntaxvalidation/validator/ValidatorFactory.java)
  creates and holds validators keyed by [`Language`](src/main/java/com/neel/syntaxvalidation/model/Language.java).
- **Builder** &mdash; [`ModificationRequest.Builder`](src/main/java/com/neel/syntaxvalidation/model/ModificationRequest.java)
  validates invariants at construction time.

```
com.neel.syntaxvalidation
├── SyntaxValidationLibrary          # public facade
├── model
│   ├── Language                     # enum + extension detection
│   ├── ModificationRequest          # immutable request (+ Builder)
│   ├── ValidationError              # single diagnostic
│   └── ValidationResult             # boolean + message + errors
├── cache
│   ├── FileCache                    # in-memory, stale-aware cache
│   └── FileCacheEntry               # immutable file snapshot
├── modification
│   └── ModificationApplier          # applies line-range replacement to a copy
├── binary
│   └── BinaryResolver               # preferred-path -> PATH fallback
├── process
│   ├── ProcessExecutor              # runs a tool, captures output, timeout
│   └── ProcessResult                # captured exit code / stdout / stderr
└── validator
    ├── LanguageValidator            # strategy interface
    ├── AbstractLanguageValidator    # template-method base class
    ├── ValidatorFactory             # factory + registry
    ├── javascript/
    │   ├── JavaScriptValidator      # node --check
    │   ├── JavaScriptSyntaxEngine   # pure-Java fallback
    │   ├── JavaScriptSyntaxTokenizer
    │   ├── JsToken / JsTokenType
    │   └── NodeCheckOutputParser
    ├── css/
    │   ├── CssValidator             # stylelint
    │   └── CssSyntaxEngine          # pure-Java fallback
    ├── html/
    │   ├── HtmlValidator            # vnu.jar
    │   └── HtmlSyntaxEngine         # pure-Java fallback
    ├── php/
    │   ├── PhpValidator             # php -l
    │   ├── PhpSyntaxEngine          # pure-Java fallback
    │   └── PhpOutputParser
    ├── java/
    │   ├── JavaValidator            # javac (two-phase: engine + javac)
    │   ├── JavaSyntaxEngine         # orchestrator
    │   ├── JavaLexer                # hand-written lexer
    │   ├── JavaToken / JavaTokenType
    │   ├── JavacOutputParser
    │   └── checker/
    │       ├── SyntaxChecker        # functional interface
    │       ├── TokenizationErrorChecker
    │       ├── DelimiterBalanceChecker
    │       └── KeywordUsageChecker
    ├── python/
    │   ├── PythonValidator          # python3 (two-phase: engine + binary)
    │   ├── PythonSyntaxEngine       # pure-Java fallback
    │   ├── PythonLexer              # hand-written Python 3.14 lexer
    │   ├── PythonParser             # structural parser
    │   ├── PythonToken / PythonTokenType
    │   └── PythonOutputParser
    ├── typescript/
    │   ├── TypeScriptValidator      # tsc (optional, two-phase)
    │   ├── TypeScriptSyntaxEngine   # pure-Java fallback
    │   └── TscOutputParser          # tsc diagnostic parsing
    └── mixed/
        ├── MixedContentValidator    # HTML/PHP with embedded CSS/JS/PHP
        ├── MixedContentSyntaxEngine # orchestrates all sub-engines
        ├── HtmlContentExtractor     # extracts <style>/<script>/<?php> blocks
        └── ExtractedBlock           # immutable block record
```

### Adding a new language

1. Add a constant to `Language` (e.g. `RUST("rs")`).
2. Subclass `AbstractLanguageValidator`, implementing `getFileExtension()`,
   `buildCommand(...)`, `parseOutput(...)` and `binaryNotFoundMessage()`.
3. Create a `*SyntaxEngine` for the pure-Java fallback validation.
4. Register it: `factory.register(Language.RUST, new RustValidator());`

No changes to the cache, applier, facade, or process infrastructure are needed.

## Pure-Java Engine Capabilities

Every language has a **zero-dependency, pure-Java fallback engine** that runs when external tools are unavailable:

| Language | Engine | Key Features |
|----------|--------|--------------|
| JavaScript | `JavaScriptSyntaxEngine` | Balance checks, grammar validation, string/regex/template literal handling |
| CSS | `CssSyntaxEngine` | Property-value validation, at-rule syntax, selector validation |
| HTML | `HtmlSyntaxEngine` | Tag matching, attribute validation, void elements, nesting checks |
| PHP | `PhpSyntaxEngine` | Full PHP 8.3+ support, classes/interfaces/traits/enums, attributes, match expressions |
| Java | `JavaSyntaxEngine` | Modular checker pipeline: tokenization, delimiter balance, keyword usage |
| Python | `PythonSyntaxEngine` | Python 3.14 lexer + parser, indentation-aware validation |
| TypeScript | `TypeScriptSyntaxEngine` | Token-based scan, delimiter balance, JSX tag matching, template literals |
| Mixed | `MixedContentSyntaxEngine` | Orchestrates all engines with line-number remapping |

## Building & testing

```bash
# Build everything (compiles + runs the full test suite)
./gradlew build

# Run only tests
./gradlew test

# Clean build
./gradlew clean build
```

The test suite includes pure unit tests (validators, cache, binary resolution,
parsing, result handling) and end-to-end integration tests. Integration tests
invoke real external binaries and skip gracefully when tools are unavailable,
so the suite remains deterministic and network-free.

## Thread safety

The `SyntaxValidationLibrary` and its collaborators (`FileCache`,
`ValidatorFactory`, `BinaryResolver`, `ProcessExecutor`) are thread-safe and
hold no per-invocation mutable state, so a single shared instance may be used
concurrently.

## API Reference

### Core Classes

| Class | Description |
|-------|-------------|
| `SyntaxValidationLibrary` | Main facade. Entry point for all validation operations. |
| `ModificationRequest` | Immutable descriptor for line-range replacements. Use `builder()` to construct. |
| `ValidationResult` | Immutable result with `isValid()`, `getMessage()`, and `getErrors()`. |
| `ValidationError` | Single diagnostic with `getLine()`, `getColumn()`, `getMessage()`. |
| `Language` | Enum of supported languages with extension mapping. |

### Factory Methods

```java
// Create library with default validators
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

// Create library with custom factory
ValidatorFactory factory = new ValidatorFactory();
SyntaxValidationLibrary library = new SyntaxValidationLibrary(factory);

// Validate a modification
ValidationResult result = library.validate(request);

// Validate source code directly
ValidationResult result = library.validateSource(language, sourceCode);
```

## License

This project is provided as-is for integration with AI coding tools and MCP infrastructure.
