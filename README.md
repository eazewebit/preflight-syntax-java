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

## Technology stack

| Concern     | Choice                     |
|-------------|----------------------------|
| Build tool  | Gradle 9.3 (wrapper)       |
| Java        | 25 (via Gradle toolchain)  |
| Base package| `com.neel.syntaxvalidation`|
| Testing     | JUnit 5 + AssertJ          |

## Quick start

```java
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

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

## Binary resolution strategy

Every validator accepts an optional **preferred binary path**. Resolution order:

1. If a preferred path is supplied and points to an executable, it is used.
2. Otherwise the system `PATH` is searched for the bare binary name
   (e.g. `node`), accounting for platform suffixes (`.exe`, `.cmd`, … on Windows).
3. If neither is available, validation **fails gracefully** with a clear message.

You can supply a preferred binary when constructing a validator and registering
it in the factory:

```java
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
    └── javascript
        ├── JavaScriptValidator      # reference implementation (node --check)
        └── NodeCheckOutputParser    # pure node-output parser
```

### Adding a new language

1. Add a constant to `Language` (e.g. `PYTHON("py")`).
2. Subclass `AbstractLanguageValidator`, implementing `getFileExtension()`,
   `buildCommand(...)`, `parseOutput(...)` and `binaryNotFoundMessage()`.
3. Register it: `factory.register(Language.PYTHON, new PythonValidator());`

No changes to the cache, applier, facade, or process infrastructure are needed.

## Supported languages

| Language   | Tool          | Status      |
|------------|---------------|-------------|
| JavaScript | `node --check`| **Complete**|
| TypeScript | _planned_     | Placeholder |
| Python     | _planned_     | Placeholder |
| Java       | _planned_     | Placeholder |

## Building & testing

```bash
# Build everything (compiles + runs the full test suite)
./gradlew build

# Run only tests
./gradlew test
```

The test suite includes pure unit tests (validators, cache, binary resolution,
parsing, result handling) and end-to-end integration tests. The JavaScript
integration tests invoke a real `node` binary and skip gracefully when Node.js
is unavailable, so the suite remains deterministic and network-free.

## Thread safety

The `SyntaxValidationLibrary` and its collaborators (`FileCache`,
`ValidatorFactory`, `BinaryResolver`, `ProcessExecutor`) are thread-safe and
hold no per-invocation mutable state, so a single shared instance may be used
concurrently.
