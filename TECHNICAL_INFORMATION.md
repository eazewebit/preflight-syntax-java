# Technical Information — Syntax Validation Library

**Version:** 1.0.0
**Language:** Java 25
**Build System:** Gradle 9.3.1 (Groovy DSL)
**License:** _Not yet specified_

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Supported Languages](#supported-languages)
4. [Quick Start](#quick-start)
5. [API Reference](#api-reference)
6. [Retrieving Modified Content](#retrieving-modified-content)
7. [Usage Examples](#usage-examples)
8. [Configuration](#configuration)
9. [Validation Strategies](#validation-strategies)
10. [File Cache](#file-cache)
11. [Thread Safety](#thread-safety)
12. [Error Handling](#error-handling)
13. [Extending the Library](#extending-the-library)
14. [Building from Source](#building-from-source)
15. [Running Tests](#running-tests)
16. [Project Structure](#project-structure)
17. [Dependencies](#dependencies)
18. [Known Limitations](#known-limitations)
19. [Changelog](#changelog)

---

## Project Overview

The Syntax Validation Library is a pluggable Java library that validates the **syntactic correctness** of proposed source-code modifications *before* they are applied. It is designed for integration with AI coding agents, IDEs, and CI/CD pipelines to prevent malformed edits from corrupting source files.

### Key Features

- **Multi-language support** — Validates syntax for Java, Python, JavaScript, TypeScript, CSS, HTML, and PHP files (plus mixed HTML/CSS/JS content)
- **Dual validation strategy** — Uses fast in-process parsers where possible, falling back to external tools (`javac`, `node`, `python`, etc.)
- **Modification simulation** — Applies proposed edits to cached file state and validates the result without touching disk
- **In-memory file caching** — Thread-safe, LRU-bounded cache avoids redundant disk I/O
- **Zero runtime dependencies** — Uses only JDK standard library classes
- **Thread-safe** — Safe for concurrent use from multiple threads

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│              SyntaxValidationLibrary                 │
│              (Public API Facade)                     │
└──────────┬─────────────────────────┬────────────────┘
           │                         │
    ┌──────▼──────┐          ┌───────▼────────┐
    │  FileCache   │          │ Modification   │
    │ (Concurrent  │          │   Applier      │
    │  HashMap)    │          │                │
    └──────┬──────┘          └───────┬────────┘
           │                         │
    ┌──────▼─────────────────────────▼────────┐
    │          ValidatorFactory                 │
    │  (Language → Validator Registry)         │
    └──────┬──────────────────────────────────┘
           │
    ┌──────▼──────────────────────────────────┐
    │      LanguageValidator (Interface)        │
    │      ┌──────────────────────────────┐   │
    │      │ AbstractLanguageValidator     │   │
    │      │  (Template Method Pattern)    │   │
    │      └──────────┬───────────────────┘   │
    │                 │                        │
    │  ┌──────────────┼──────────────────┐    │
    │  │              │                  │    │
    │  ▼              ▼                  ▼    │
    │ JavaValidator  PythonValidator  JsVal.  │
    │ ...            ...              ...     │
    └─────────────────────────────────────────┘
           │                         │
    ┌──────▼──────┐          ┌───────▼────────┐
    │ In-Process   │          │  ProcessExecutor│
    │ SyntaxEngine │          │  (External Tool)│
    └─────────────┘          └────────────────┘
```

### Design Patterns Used

| Pattern | Implementation | Purpose |
|---------|---------------|---------|
| **Strategy** | `LanguageValidator` interface | Decouples validation algorithm from language |
| **Factory** | `ValidatorFactory` | Creates language-specific validators |
| **Template Method** | `AbstractLanguageValidator` | Defines validation skeleton with hooks |
| **Builder** | `ModificationRequest.Builder` | Flexible construction of edit requests |
| **Immutable Value** | Final classes with defensive copies | Thread-safe, side-effect-free data carriers |

---

## Supported Languages

| Language | Enum Value | External Tool | In-Process Engine | Primary Strategy |
|----------|-----------|---------------|-------------------|-----------------|
| Java | `Language.JAVA` | `javac` (JDK) | `JavaSyntaxEngine` + `JavaLexer` + checkers | External (`javac`) preferred, falls back to in-process |
| Python | `Language.PYTHON` | `python` | `PythonSyntaxEngine` + `PythonLexer` + `PythonParser` | In-process (pure Java) |
| JavaScript | `Language.JAVASCRIPT` | `node --check` | `JavaScriptSyntaxEngine` + `JavaScriptSyntaxTokenizer` | In-process preferred, falls back to `node` |
| TypeScript | `Language.TYPESCRIPT` | `tsc` | `TypeScriptSyntaxEngine` + `TypeScriptSyntaxTokenizer` | In-process preferred, falls back to `tsc` |
| CSS | `Language.CSS` | `stylelint` | `CssSyntaxEngine` | In-process preferred, falls back to `stylelint` |
| HTML | `Language.HTML` | `html-validate` / `vnu.jar` | `HtmlSyntaxEngine` / `MixedContentSyntaxEngine` | In-process preferred, falls back to external |
| PHP | `Language.PHP` | `php -l` | `PhpSyntaxEngine` | In-process preferred, falls back to `php -l` |

**Note:** Mixed HTML/CSS/JS content (HTML with embedded `<style>` and `<script>` tags) is validated through `SyntaxValidationLibrary.validateMixedContent()` using the `MixedContentSyntaxEngine`, which orchestrates the HTML, CSS, and JavaScript engines internally.

---

## Quick Start

### 1. Add to Your Project

**Gradle (Groovy DSL):**
```groovy
dependencies {
    implementation project(':syntax-validation')
    // Or, if published as a JAR:
    // implementation 'com.neel:syntax-validation:1.0.0'
}
```

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation(project(":syntax-validation"))
}
```

### 2. Basic Usage

```java
import com.neel.syntaxvalidation.SyntaxValidationLibrary;
import com.neel.syntaxvalidation.model.*;

// Create the library instance (thread-safe, reuse across your application)
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

// Describe the proposed modification using the Builder
ModificationRequest request = ModificationRequest.builder()
    .filePath("src/main/java/com/example/Foo.java")
    .fromLine(10)
    .toLine(15)
    .replacement("    public String greet(String name) {\n"
               + "        return \"Hello, \" + name;\n"
               + "    }\n")
    .build();

// Validate
ValidationResult result = library.validate(request);

if (result.isValid()) {
    System.out.println("✓ Modification is syntactically valid");
} else {
    System.out.println("✗ " + result.getMessage());
    for (ValidationError error : result.getErrors()) {
        System.out.printf("  Line %d, Col %d: %s%n",
            error.getLine(), error.getColumn(), error.getMessage());
    }
}
```

---

## API Reference

### `SyntaxValidationLibrary`

The main entry point. Thread-safe; intended to be a long-lived singleton.

```java
// Default construction (all defaults)
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

// Construction with a custom ValidatorFactory (e.g., for custom binary paths)
ValidatorFactory factory = new ValidatorFactory();
SyntaxValidationLibrary library = new SyntaxValidationLibrary(factory);

// Primary validation method
ValidationResult validate(ModificationRequest request);

// Mixed content validation (HTML with embedded CSS/JS)
ValidationResult validateMixedContent(String htmlSource);
ValidationResult validateMixedContent(ModificationRequest request);

// Modified content retrieval
String getModifiedContent(ModificationRequest request);
String getModifiedContent(BatchModificationRequest request);
List<String> getModifiedLines(ModificationRequest request);
List<String> getModifiedLines(BatchModificationRequest request);

// Cache management
boolean invalidateCache(Path path);  // Evict a single file from cache
void clearCache();                    // Evict all cached files
```

### `ModificationRequest`

Immutable description of a proposed source-code modification. Created exclusively through its `Builder`.

```java
// Create using the builder
ModificationRequest request = ModificationRequest.builder()
    .filePath("src/script.js")
    .fromLine(3)
    .toLine(5)
    .replacement("function add(a, b) {\n  return a + b;\n}")
    .build();

// Getters
String getFilePath();       // The file path (absolute or relative)
int getFromLine();          // 1-based starting line (inclusive)
int getToLine();            // 1-based ending line (inclusive)
String getReplacement();    // The replacement text (never null, defaults to "")
```

**Constraints (enforced by the Builder):**
- `fromLine >= 1`
- `toLine >= fromLine`
- `filePath` must not be null or blank
- `replacement` defaults to empty string if null

### `BatchModificationRequest`

Immutable description of a proposed batch source-code modification with multiple replacements. Created exclusively through its `Builder`.

```java
// Create using the builder
BatchModificationRequest request = BatchModificationRequest.builder()
    .filePath("src/main/java/MyClass.java")
    .addAllReplacements(List.of(
        LineReplacement.builder().fromLine(5).toLine(5).replacement("new line 5").build(),
        LineReplacement.builder().fromLine(10).toLine(12).replacement("new lines 10-12").build()
    ))
    .build();

// Getters
String filePath();                      // The file path
List<LineReplacement> replacements();   // Unmodifiable list of replacements
```

### `ValidationResult`

Immutable outcome of a syntax validation. Created via static factory methods.

```java
// Factory methods
ValidationResult result = ValidationResult.valid("Validation passed");
ValidationResult result = ValidationResult.invalid("Syntax errors found");
ValidationResult result = ValidationResult.invalid("Syntax errors found", List.of(error1, error2));
ValidationResult result = ValidationResult.invalid("Syntax errors found", singleError);

// Accessors
boolean isValid();                      // true if no errors
String getMessage();                    // Overall explanation message (never null)
List<ValidationError> getErrors();      // Unmodifiable list of detailed errors (empty when valid)
boolean hasErrors();                    // true if at least one error recorded
```

**Note:** `ValidationResult` is a `final class`, not a Java record. It uses static factory methods `valid()` and `invalid()` for construction.

### `ValidationError`

Immutable single diagnostic from a validator.

```java
// Constructor
ValidationError error = new ValidationError(line, column, message, toolOutput);

// Accessors
int getLine();          // 1-based line number (0 if unknown)
int getColumn();        // 1-based column number (0 if unknown)
String getMessage();    // Human-readable error description (never null)
String getToolOutput(); // Raw output from the external tool (may be null for in-process errors)
```

### `Language`

Enum of supported source languages.

```java
public enum Language {
    JAVASCRIPT,
    HTML,
    CSS,
    PHP,
    TYPESCRIPT,
    PYTHON,
    JAVA;
    
    // Returns an unmodifiable set of file extensions for this language
    // e.g., JAVA → {".java"}, PYTHON → {".py"}
    Set<String> getExtensions();
    
    // Find language by file extension (case-insensitive)
    // Returns Optional.empty() for unsupported extensions
    static Optional<Language> fromExtension(String extension);
    
    // Find language by file path (extracts extension and delegates to fromExtension)
    // Returns Optional.empty() for unsupported paths
    static Optional<Language> fromPath(Path path);
}
```

**Usage:**
```java
Optional<Language> lang = Language.fromExtension(".java");
// → Optional.of(Language.JAVA)

Optional<Language> lang = Language.fromPath(Path.of("src/app.py"));
// → Optional.of(Language.PYTHON)
```

---

## Retrieving Modified Content

The library provides methods to retrieve the modified file content after validation, without writing to disk. This is useful for:

- Writing the modified content to a different location
- Passing the content to other tools for further processing
- Generating diffs between original and modified content
- Applying modifications only after all validations pass

### Available Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getModifiedContent(ModificationRequest)` | `String` | Returns modified content as a single string |
| `getModifiedContent(BatchModificationRequest)` | `String` | Returns batch-modified content as a single string |
| `getModifiedLines(ModificationRequest)` | `List<String>` | Returns modified content as a list of lines |
| `getModifiedLines(BatchModificationRequest)` | `List<String>` | Returns batch-modified content as a list of lines |

### Single Modification Example

```java
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

ModificationRequest request = ModificationRequest.builder()
        .filePath("src/main.java")
        .fromLine(10)
        .toLine(15)
        .replacement("    public void newMethod() {\n" +
                     "        // new implementation\n" +
                     "    }")
        .build();

// Get modified content as a string
String modifiedContent = library.getModifiedContent(request);

// Get modified content as a list of lines
List<String> modifiedLines = library.getModifiedLines(request);

// Validate first, then retrieve content
ValidationResult result = library.validate(request);
if (result.isValid()) {
    String safeContent = library.getModifiedContent(request);
    Files.writeString(Path.of("output.java"), safeContent);
}
```

### Batch Modification Example

```java
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

List<LineReplacement> replacements = List.of(
    LineReplacement.builder()
        .fromLine(5)
        .toLine(5)
        .replacement("    private static final int MAX_SIZE = 100;")
        .build(),
    LineReplacement.builder()
        .fromLine(20)
        .toLine(25)
        .replacement("    @Override\n" +
                     "    public String toString() {\n" +
                     "        return \"Modified class\";\n" +
                     "    }")
        .build()
);

BatchModificationRequest batchRequest = BatchModificationRequest.builder()
        .filePath("src/main/java/MyClass.java")
        .addAllReplacements(replacements)
        .build();

// Get modified content
String modifiedContent = library.getModifiedContent(batchRequest);
List<String> modifiedLines = library.getModifiedLines(batchRequest);

// Validate and apply
ValidationResult result = library.validate(batchRequest);
if (result.isValid()) {
    Files.writeString(Path.of(batchRequest.filePath()), modifiedContent);
}
```

### Complete Workflow Example

```java
import com.neel.syntaxvalidation.SyntaxValidationLibrary;
import com.neel.syntaxvalidation.model.*;
import java.nio.file.*;

public class CodeModifier {
    private final SyntaxValidationLibrary library = new SyntaxValidationLibrary();
    
    /**
     * Validates and applies a modification to a file.
     * Returns true if the modification was applied successfully.
     */
    public boolean applyModification(String filePath, int fromLine, int toLine, 
                                     String replacement) throws IOException {
        ModificationRequest request = ModificationRequest.builder()
                .filePath(filePath)
                .fromLine(fromLine)
                .toLine(toLine)
                .replacement(replacement)
                .build();
        
        ValidationResult result = library.validate(request);
        
        if (!result.isValid()) {
            System.err.println("Validation failed:");
            System.err.println(result.getMessage());
            result.getErrors().forEach(e -> 
                System.err.printf("  Line %d: %s%n", e.getLine(), e.getMessage()));
            return false;
        }
        
        String modifiedContent = library.getModifiedContent(request);
        Path path = Path.of(filePath);
        Files.writeString(path, modifiedContent);
        library.invalidateCache(path);
        
        return true;
    }
    
    /**
     * Applies multiple modifications to a file in a single operation.
     */
    public boolean applyBatchModifications(String filePath, 
                                           List<LineReplacement> replacements) 
            throws IOException {
        BatchModificationRequest request = BatchModificationRequest.builder()
                .filePath(filePath)
                .addAllReplacements(replacements)
                .build();
        
        ValidationResult result = library.validate(request);
        
        if (!result.isValid()) {
            System.err.println("Batch validation failed:");
            System.err.println(result.getMessage());
            return false;
        }
        
        String modifiedContent = library.getModifiedContent(request);
        Files.writeString(Path.of(filePath), modifiedContent);
        library.invalidateCache(Path.of(filePath));
        
        return true;
    }
}
```

### Error Handling

The `getModifiedContent()` and `getModifiedLines()` methods throw exceptions in the following cases:

| Exception | When | Cause |
|-----------|------|-------|
| `IllegalArgumentException` | Null request | Request parameter is null |
| `IllegalStateException` | File not found | The file specified in the request does not exist |
| `IllegalStateException` | I/O error | The file cannot be read |

```java
try {
    String modifiedContent = library.getModifiedContent(request);
    // Use the modified content
} catch (IllegalArgumentException e) {
    System.err.println("Invalid request: " + e.getMessage());
} catch (IllegalStateException e) {
    System.err.println("Cannot read file: " + e.getMessage());
}
```

---

## Usage Examples

### Example 1: Validate a Java Method Replacement

```java
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

ModificationRequest request = ModificationRequest.builder()
    .filePath("src/main/java/com/example/Calculator.java")
    .fromLine(25)
    .toLine(30)
    .replacement("""
        public int add(int a, int b) {
            return a + b;
        }
    """)
    .build();

ValidationResult result = library.validate(request);
assert result.isValid() : "Java syntax should be valid";
```

### Example 2: Validate a Python Function

```java
ModificationRequest request = ModificationRequest.builder()
    .filePath("src/utils/helpers.py")
    .fromLine(10)
    .toLine(15)
    .replacement("""
    def calculate_total(items: list[float]) -> float:
        \\"\\"\\"Calculate the sum of all items.\\"\\"\\"
        total = 0.0
        for item in items:
            total += item
        return total
    """)
    .build();

ValidationResult result = library.validate(request);
```

### Example 3: Validate JavaScript with Error Handling

```java
try {
    ModificationRequest request = ModificationRequest.builder()
        .filePath("src/app.js")
        .fromLine(1)
        .toLine(5)
        .replacement("""
        function fetchData(url) {
            const response = await fetch(url);
            return response.json();
        }
        """)
        .build();

    ValidationResult result = library.validate(request);

    if (!result.isValid()) {
        for (ValidationError error : result.getErrors()) {
            System.err.printf("Line %d, Col %d — %s%n",
                error.getLine(), error.getColumn(), error.getMessage());
        }
    }
} catch (IllegalArgumentException e) {
    System.err.println("Invalid request parameters: " + e.getMessage());
}
```

### Example 4: Validate CSS Stylesheet

```java
ModificationRequest request = ModificationRequest.builder()
    .filePath("styles/main.css")
    .fromLine(1)
    .toLine(10)
    .replacement("""
    .container {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 2rem;
        background-color: var(--bg-primary);
    }

    .container.active {
        opacity: 1;
    }
    """)
    .build();

ValidationResult result = library.validate(request);
System.out.println("CSS valid: " + result.isValid());
```

### Example 5: Validate PHP Code

```java
ModificationRequest request = ModificationRequest.builder()
    .filePath("src/Controller/UserController.php")
    .fromLine(20)
    .toLine(35)
    .replacement("""
    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'name' => 'required|string|max:255',
            'email' => 'required|email|unique:users',
        ]);

        $user = User::create($validated);

        return response()->json($user, 201);
    }
    """)
    .build();

ValidationResult result = library.validate(request);
```

### Example 6: Validate Mixed HTML/CSS/JS Content

```java
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

String htmlContent = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <title>Dashboard</title>
        <style>
            body { font-family: sans-serif; margin: 0; }
            .header { background: #333; color: white; padding: 1rem; }
        </style>
    </head>
    <body>
        <div class="header">
            <h1>Dashboard</h1>
        </div>
        <script>
            document.querySelector('.header').addEventListener('click', () => {
                console.log('Header clicked');
            });
        </script>
    </body>
    </html>
    """;

// Validate all embedded content (HTML structure + CSS + JavaScript)
ValidationResult result = library.validateMixedContent(htmlContent);
System.out.println("Mixed content valid: " + result.isValid());
```

### Example 7: Batch Validation

```java
List<ModificationRequest> requests = List.of(
    ModificationRequest.builder().filePath("A.java").fromLine(1).toLine(5).replacement("class A {}").build(),
    ModificationRequest.builder().filePath("b.py").fromLine(1).toLine(3).replacement("def f():\n    pass").build(),
    ModificationRequest.builder().filePath("c.js").fromLine(1).toLine(3).replacement("const x = 1;").build()
);

SyntaxValidationLibrary library = new SyntaxValidationLibrary();

List<ValidationResult> results = requests.stream()
    .map(library::validate)
    .toList();

long errorCount = results.stream()
    .filter(r -> !r.isValid())
    .count();

System.out.printf("Validated %d files: %d valid, %d with errors%n",
    results.size(), results.size() - errorCount, errorCount);
```

### Example 8: Auto-detect Language from File Path

```java
Path filePath = Path.of("src/utils/parser.py");
Optional<Language> language = Language.fromPath(filePath);

if (language.isPresent()) {
    ModificationRequest request = ModificationRequest.builder()
        .filePath(filePath.toString())
        .fromLine(1)
        .toLine(10)
        .replacement("def parse(input: str) -> dict:\n    return {}")
        .build();

    ValidationResult result = library.validate(request);
} else {
    System.err.println("Unsupported file type: " + filePath);
}
```

---

## Configuration

### Library Construction

```java
// Default construction
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

// With custom ValidatorFactory
ValidatorFactory factory = new ValidatorFactory();
SyntaxValidationLibrary library = new SyntaxValidationLibrary(factory);
```

### External Tool Requirements

For **subprocess-based validation** (fallback when in-process engine is unavailable or as primary strategy for Java), the following tools must be available on `PATH`:

| Tool | Required Version | Install Command |
|------|-----------------|-----------------|
| `javac` | JDK 17+ | [Adoptium](https://adoptium.net/) |
| `node` | 18+ | [Node.js](https://nodejs.org/) |
| `python` | 3.8+ | [Python](https://python.org/) |
| `tsc` | 5+ | `npm install -g typescript` |
| `php` | 8.0+ | [php.net](https://php.net/) |
| `stylelint` | 15+ | `npm install -g stylelint` |
| `html-validate` | 8+ | `npm install -g html-validate` |

**Note:** External tools are only required for their respective language's fallback strategy. The in-process validation engines (pure Java) work without any external tools.

---

## Validation Strategies

Each language validator uses a **two-tier strategy**:

### Tier 1: In-Process Engine (Preferred)

Hand-written lexers and parsers implemented in pure Java. These provide:
- Zero subprocess overhead
- No external tool dependencies
- Deterministic behavior across environments
- Immediate validation results

**Available for:** Java (partial), Python, JavaScript, TypeScript, CSS, HTML, PHP, Mixed HTML/CSS/JS

### Tier 2: External Tool (Fallback)

Shells out to the language's native compiler/linter via `ProcessExecutor`. Used when:
- In-process engine produces ambiguous results
- External tool provides better error messages
- For Java: `javac` is the primary strategy for full-fidelity checking

**Process flow:**
1. Write modified source to a temp file
2. Invoke the external tool with the temp file path
3. Parse stdout/stderr for error messages
4. Map errors to `ValidationError` objects with line/column information
5. Clean up temp file

---

## File Cache

The `FileCache` maintains an in-memory snapshot of files that have been read during validation.

### Behavior

- **Cache-first reads** — When a file is requested, the cache is checked before hitting disk
- **Automatic population** — Files are cached on first read
- **Version tracking** — Each `FileCacheEntry` records the file's last-modified timestamp. Subsequent reads check if the on-disk file has changed and refresh if needed
- **LRU eviction** — When the cache exceeds its capacity, the least-recently-used entry is removed
- **Thread-safe** — Uses `ConcurrentHashMap` internally

### Cache Lifecycle

```
File requested → Cache lookup
  ├─ HIT + version matches → Return cached content
  ├─ HIT + version mismatch → Re-read from disk, update cache
  └─ MISS → Read from disk, populate cache, return content
```

### Manual Cache Control

```java
// Evict a specific file from cache (e.g., after external modification)
library.invalidateCache(Path.of("src/main/Foo.java"));

// Clear the entire cache
library.clearCache();
```

---

## Thread Safety

The library is designed for safe concurrent use:

| Component | Thread Safety | Mechanism |
|-----------|--------------|-----------|
| `SyntaxValidationLibrary` | ✅ Safe | Stateless; delegates to thread-safe components |
| `FileCache` | ✅ Safe | `ConcurrentHashMap` |
| `FileCacheEntry` | ✅ Safe | Immutable final class with defensive copies |
| `ModificationApplier` | ✅ Safe | Stateless utility |
| `ValidatorFactory` | ✅ Safe | Stateless factory |
| All validators | ✅ Safe | Stateless; fresh instances created per request |
| `ProcessExecutor` | ✅ Safe | No shared mutable state; each call creates a new `ProcessBuilder` |
| All model classes | ✅ Safe | Immutable final classes with defensive copies |

**Usage guideline:** Create one `SyntaxValidationLibrary` instance and share it across threads.

```java
// Safe: single instance, multiple threads
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

ExecutorService executor = Executors.newFixedThreadPool(8);
requests.forEach(request ->
    executor.submit(() -> {
        ValidationResult result = library.validate(request);
        // process result...
    })
);
```

---

## Error Handling

### Exception Hierarchy

The library throws the following exceptions:

| Exception | When | Cause |
|-----------|------|-------|
| `IllegalArgumentException` | Invalid `ModificationRequest` parameters | `fromLine < 1`, `toLine < fromLine`, null/blank `filePath` |
| `NullPointerException` | Null arguments to non-null parameters | Null `language`, `source`, etc. |
| `IllegalStateException` | No validator registered for language | Rare; indicates misconfiguration |

### Validation Failure vs. Exception

The library distinguishes between:
- **Validation failure** (returned as `ValidationResult` with `isValid() == false`): The modification has syntax errors
- **Exception** (thrown): The request itself is invalid or the library cannot perform validation

```java
try {
    ValidationResult result = library.validate(request);

    if (!result.isValid()) {
        // Handle syntax errors — the modification would produce invalid code
        result.getErrors().forEach(error ->
            System.out.printf("Line %d: %s%n", error.getLine(), error.getMessage()));
    }
} catch (IllegalArgumentException e) {
    // Handle invalid request parameters
    System.err.println("Bad request: " + e.getMessage());
} catch (IllegalStateException e) {
    // Handle missing validator
    System.err.println("Configuration error: " + e.getMessage());
}
```

---

## Extending the Library

### Adding a New Language Validator

1. **Add the language enum value** to `Language.java`:

```java
RUST(".rs"),
```

2. **Create the validator class** extending `AbstractLanguageValidator`:

```java
package com.neel.syntaxvalidation.validator.rust;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;

public class RustValidator extends AbstractLanguageValidator {

    public RustValidator(BinaryResolver binaryResolver, ProcessExecutor processExecutor) {
        super(binaryResolver, processExecutor);
    }

    @Override
    public Language getLanguage() {
        return Language.RUST;
    }

    @Override
    protected ValidationResult validateByExternalTool(String sourceCode, String tempFilePath) {
        // Implement rustc-based validation
        // ...
    }

    @Override
    protected ValidationResult validateBySyntaxEngine(String sourceCode) {
        // Implement in-process Rust parser (optional)
        // ...
    }
}
```

3. **Register in `ValidatorFactory`**:

```java
// In ValidatorFactory.java
case RUST -> Optional.of(new RustValidator(binaryResolver, processExecutor));
```

4. **Write tests** following the existing pattern (see `JavaValidatorTest`, `PythonValidatorTest`, etc.)

---

## Building from Source

### Prerequisites

- **JDK 25** (or compatible version)
- No additional dependencies required (Gradle wrapper handles Gradle itself)

### Build Commands

```bash
# Full build (compile + test + package)
./gradlew build

# Compile only
./gradlew compileJava

# Create JAR
./gradlew jar

# Clean build
./gradlew clean build
```

### Gradle Configuration

**`gradle.properties`:**
```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.parallel=true
```

**`build.gradle`:**
```groovy
plugins {
    id 'java-library'
}

group = 'com.neel'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testImplementation 'org.assertj:assertj-core:3.27.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

---

## Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.neel.syntaxvalidation.validator.java.JavaValidatorTest"

# Run specific test method
./gradlew test --tests "com.neel.syntaxvalidation.validator.java.JavaValidatorTest.validJavaCodeShouldReturnSuccess"

# Run tests with detailed output
./gradlew test --info

# Generate test report (HTML)
# Report available at: build/reports/tests/test/index.html
```

### Test Suite Overview

| Test Class | Component Tested |
|-----------|-----------------|
| `SyntaxValidationLibraryTest` | End-to-end integration |
| `JavaValidatorTest` | Java validation (external + in-process) |
| `PythonValidatorTest` | Python validation (in-process) |
| `JavaScriptSyntaxEngineTest` | JavaScript in-process engine |
| `TypeScriptSyntaxEngineTest` | TypeScript in-process engine |
| `CssSyntaxEngineTest` | CSS in-process engine |
| `HtmlSyntaxEngineTest` | HTML in-process engine |
| `PhpSyntaxEngineTest` | PHP in-process engine |
| `FileCacheTest` | Cache behavior and eviction |
| `ProcessExecutorTest` | External process execution |
| `BinaryResolverTest` | Tool binary resolution |
| `ModificationApplierTest` | Line-range modification application |
| `ValidationResultTest` | Result model behavior |

---

## Project Structure

```
syntax-validation/
├── build.gradle                          # Build configuration
├── settings.gradle                       # Project settings
├── gradle.properties                     # Gradle JVM and build options
├── gradlew / gradlew.bat                 # Gradle wrapper scripts
├── gradle/wrapper/                       # Gradle wrapper JARs and properties
├── README.md                             # Project README
├── TECHNICAL_INFORMATION.md              # This file
├── PRODUCTION_READINESS_REPORT.md        # Production readiness assessment
├── agent.md                              # AI agent integration guide
│
└── src/
    ├── main/java/com/neel/syntaxvalidation/
    │   ├── SyntaxValidationLibrary.java       # Public API facade
    │   │
    │   ├── model/                             # Immutable data types
    │   │   ├── Language.java                  # Supported language enum
    │   │   ├── ModificationRequest.java       # Edit description (builder pattern)
    │   │   ├── BatchModificationRequest.java  # Batch edit description (builder pattern)
    │   │   ├── LineReplacement.java           # Single line replacement
    │   │   ├── ValidationResult.java          # Validation outcome (factory methods)
    │   │   └── ValidationError.java           # Single diagnostic
    │   │
    │   ├── binary/                            # Tool resolution
    │   │   └── BinaryResolver.java            # Finds external tool paths
    │   │
    │   ├── cache/                             # File caching
    │   │   ├── FileCache.java                 # Thread-safe ConcurrentHashMap cache
    │   │   └── FileCacheEntry.java            # Cached file snapshot
    │   │
    │   ├── modification/                      # Code modification
    │   │   └── ModificationApplier.java       # Applies line-range edits
    │   │
    │   ├── process/                           # External process execution
    │   │   ├── ProcessExecutor.java           # Runs external tools
    │   │   └── ProcessResult.java             # Process outcome
    │   │
    │   └── validator/                         # Validation engines
    │       ├── LanguageValidator.java          # Strategy interface
    │       ├── ValidatorFactory.java           # Factory/registry
    │       ├── AbstractLanguageValidator.java  # Template method base
    │       │
    │       ├── java/                          # Java validation
    │       │   ├── JavaValidator.java
    │       │   ├── JavaSyntaxEngine.java
    │       │   ├── JavacOutputParser.java
    │       │   ├── JavaLexer.java
    │       │   ├── JavaToken.java
    │       │   ├── JavaTokenType.java
    │       │   └── checker/
    │       │       ├── SyntaxChecker.java
    │       │       ├── DelimiterBalanceChecker.java
    │       │       ├── TokenizationErrorChecker.java
    │       │       └── KeywordUsageChecker.java
    │       │
    │       ├── python/                        # Python validation
    │       │   ├── PythonValidator.java
    │       │   ├── PythonSyntaxEngine.java
    │       │   ├── PythonOutputParser.java
    │       │   ├── PythonLexer.java
    │       │   ├── PythonParser.java
    │       │   ├── PythonToken.java
    │       │   └── PythonTokenType.java
    │       │
    │       ├── javascript/                    # JavaScript validation
    │       │   ├── JavaScriptValidator.java
    │       │   ├── JavaScriptSyntaxEngine.java
    │       │   └── JavaScriptSyntaxTokenizer.java
    │       │
    │       ├── typescript/                    # TypeScript validation
    │       │   ├── TypeScriptValidator.java
    │       │   ├── TypeScriptSyntaxEngine.java
    │       │   ├── TypeScriptSyntaxTokenizer.java
    │       │   ├── TsToken.java
    │       │   └── TsTokenType.java
    │       │
    │       ├── css/                           # CSS validation
    │       │   ├── CssValidator.java
    │       │   ├── CssSyntaxEngine.java
    │       │   └── StylelintOutputParser.java
    │       │
    │       ├── html/                          # HTML validation
    │       │   ├── HtmlValidator.java
    │       │   └── HtmlSyntaxEngine.java
    │       │
    │       ├── php/                           # PHP validation
    │       │   ├── PhpValidator.java
    │       │   └── PhpSyntaxEngine.java
    │       │
    │       └── mixed/                         # Mixed content validation
    │           ├── MixedContentValidator.java
    │           ├── MixedContentSyntaxEngine.java
    │           └── HtmlContentExtractor.java
    │
    └── test/java/com/neel/syntaxvalidation/
        ├── SyntaxValidationLibraryTest.java
        ├── model/
        │   └── ValidationResultTest.java
        ├── binary/
        │   └── BinaryResolverTest.java
        ├── cache/
        │   └── FileCacheTest.java
        ├── process/
        │   └── ProcessExecutorTest.java
        ├── modification/
        │   └── ModificationApplierTest.java
        └── validator/
            ├── java/
            │   └── JavaValidatorTest.java
            ├── python/
            │   └── PythonValidatorTest.java
            ├── javascript/
            │   └── JavaScriptSyntaxEngineTest.java
            ├── typescript/
            │   └── TypeScriptSyntaxEngineTest.java
            ├── css/
            │   └── CssSyntaxEngineTest.java
            ├── html/
            │   └── HtmlSyntaxEngineTest.java
            └── php/
                └── PhpSyntaxEngineTest.java
```

---

## Dependencies

### Runtime Dependencies

**None.** The library has zero runtime dependencies beyond the Java standard library (JDK 25).

### Build/Test Dependencies

| Dependency | Version | Scope | Purpose |
|-----------|---------|-------|---------|
| JUnit Jupiter | 5.11.4 | test | Unit testing framework |
| AssertJ | 3.27.0 | test | Fluent assertions |
| JUnit Platform Launcher | — | testRuntime | Test execution |

### Gradle Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `java-library` | (built-in) | Java library conventions |
| Gradle Wrapper | 9.3.1 | Consistent build environment |

---

## Known Limitations

1. **Java validation** — In-process `JavaSyntaxEngine` provides basic structural checks (delimiter balance, keyword usage, tokenization errors). For full-fidelity validation (generics, annotations, complex expressions), the library falls back to `javac`, which requires a JDK installation.

2. **TypeScript validation** — In-process `TypeScriptSyntaxEngine` validates syntax structure but does not perform full type checking. For type-level validation, `tsc` is used as the external tool.

3. **Mixed content** — `validateMixedContent()` extracts `<script>` and `<style>` blocks from HTML and validates them independently. It does not validate the interactions between HTML, CSS, and JavaScript.

4. **External tool availability** — When external tools are not on `PATH`, the library gracefully degrades to in-process validation only. Error messages may be less detailed in this mode.

5. **File encoding** — All file I/O assumes UTF-8 encoding. Files with other encodings may produce incorrect validation results.

6. **Large files** — Files larger than available heap memory may cause `OutOfMemoryError`. The in-process engines load entire files into memory for parsing.

---

## Changelog

### Version 1.0.0 (Current)

- Initial release
- Support for 7 languages: Java, Python, JavaScript, TypeScript, CSS, HTML, PHP
- Mixed HTML/CSS/JS content validation via `validateMixedContent()`
- Dual validation strategy (in-process + external tools)
- Thread-safe in-memory file cache with LRU eviction
- Builder pattern for `ModificationRequest` construction
- Factory methods for `ValidationResult` (`valid()` / `invalid()`)
- Comprehensive test suite across all language validators
- Zero runtime dependencies
- **New:** `getModifiedContent()` and `getModifiedLines()` methods for retrieving modified file content without writing to disk
- **New:** `BatchModificationRequest` support for multiple line replacements in a single operation

**Version:** 1.0.0
**Language:** Java 25
**Build System:** Gradle 9.3.1 (Groovy DSL)
**License:** _Not yet specified_

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Supported Languages](#supported-languages)
4. [Quick Start](#quick-start)
5. [API Reference](#api-reference)
6. [Usage Examples](#usage-examples)
7. [Configuration](#configuration)
8. [Validation Strategies](#validation-strategies)
9. [File Cache](#file-cache)
10. [Thread Safety](#thread-safety)
11. [Error Handling](#error-handling)
12. [Extending the Library](#extending-the-library)
13. [Building from Source](#building-from-source)
14. [Running Tests](#running-tests)
15. [Project Structure](#project-structure)
16. [Dependencies](#dependencies)
17. [Known Limitations](#known-limitations)
18. [Changelog](#changelog)

---

## Project Overview

The Syntax Validation Library is a pluggable Java library that validates the **syntactic correctness** of proposed source-code modifications *before* they are applied. It is designed for integration with AI coding agents, IDEs, and CI/CD pipelines to prevent malformed edits from corrupting source files.

### Key Features

- **Multi-language support** — Validates syntax for Java, Python, JavaScript, TypeScript, CSS, HTML, and PHP files (plus mixed HTML/CSS/JS content)
- **Dual validation strategy** — Uses fast in-process parsers where possible, falling back to external tools (`javac`, `node`, `python`, etc.)
- **Modification simulation** — Applies proposed edits to cached file state and validates the result without touching disk
- **In-memory file caching** — Thread-safe, LRU-bounded cache avoids redundant disk I/O
- **Zero runtime dependencies** — Uses only JDK standard library classes
- **Thread-safe** — Safe for concurrent use from multiple threads

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│              SyntaxValidationLibrary                 │
│              (Public API Facade)                     │
└──────────┬─────────────────────────┬────────────────┘
           │                         │
    ┌──────▼──────┐          ┌───────▼────────┐
    │  FileCache   │          │ Modification   │
    │ (Concurrent  │          │   Applier      │
    │  HashMap)    │          │                │
    └──────┬──────┘          └───────┬────────┘
           │                         │
    ┌──────▼─────────────────────────▼────────┐
    │          ValidatorFactory                 │
    │  (Language → Validator Registry)         │
    └──────┬──────────────────────────────────┘
           │
    ┌──────▼──────────────────────────────────┐
    │      LanguageValidator (Interface)        │
    │      ┌──────────────────────────────┐   │
    │      │ AbstractLanguageValidator     │   │
    │      │  (Template Method Pattern)    │   │
    │      └──────────┬───────────────────┘   │
    │                 │                        │
    │  ┌──────────────┼──────────────────┐    │
    │  │              │                  │    │
    │  ▼              ▼                  ▼    │
    │ JavaValidator  PythonValidator  JsVal.  │
    │ ...            ...              ...     │
    └─────────────────────────────────────────┘
           │                         │
    ┌──────▼──────┐          ┌───────▼────────┐
    │ In-Process   │          │  ProcessExecutor│
    │ SyntaxEngine │          │  (External Tool)│
    └─────────────┘          └────────────────┘
```

### Design Patterns Used

| Pattern | Implementation | Purpose |
|---------|---------------|---------|
| **Strategy** | `LanguageValidator` interface | Decouples validation algorithm from language |
| **Factory** | `ValidatorFactory` | Creates language-specific validators |
| **Template Method** | `AbstractLanguageValidator` | Defines validation skeleton with hooks |
| **Builder** | `ModificationRequest.Builder` | Flexible construction of edit requests |
| **Immutable Value** | Final classes with defensive copies | Thread-safe, side-effect-free data carriers |

---

## Supported Languages

| Language | Enum Value | External Tool | In-Process Engine | Primary Strategy |
|----------|-----------|---------------|-------------------|-----------------|
| Java | `Language.JAVA` | `javac` (JDK) | `JavaSyntaxEngine` + `JavaLexer` + checkers | External (`javac`) preferred, falls back to in-process |
| Python | `Language.PYTHON` | `python` | `PythonSyntaxEngine` + `PythonLexer` + `PythonParser` | In-process (pure Java) |
| JavaScript | `Language.JAVASCRIPT` | `node --check` | `JavaScriptSyntaxEngine` + `JavaScriptSyntaxTokenizer` | In-process preferred, falls back to `node` |
| TypeScript | `Language.TYPESCRIPT` | `tsc` | `TypeScriptSyntaxEngine` + `TypeScriptSyntaxTokenizer` | In-process preferred, falls back to `tsc` |
| CSS | `Language.CSS` | `stylelint` | `CssSyntaxEngine` | In-process preferred, falls back to `stylelint` |
| HTML | `Language.HTML` | `html-validate` / `vnu.jar` | `HtmlSyntaxEngine` / `MixedContentSyntaxEngine` | In-process preferred, falls back to external |
| PHP | `Language.PHP` | `php -l` | `PhpSyntaxEngine` | In-process preferred, falls back to `php -l` |

**Note:** Mixed HTML/CSS/JS content (HTML with embedded `<style>` and `<script>` tags) is validated through `SyntaxValidationLibrary.validateMixedContent()` using the `MixedContentSyntaxEngine`, which orchestrates the HTML, CSS, and JavaScript engines internally.

---

## Quick Start

### 1. Add to Your Project

**Gradle (Groovy DSL):**
```groovy
dependencies {
    implementation project(':syntax-validation')
    // Or, if published as a JAR:
    // implementation 'com.neel:syntax-validation:1.0.0'
}
```

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation(project(":syntax-validation"))
}
```

### 2. Basic Usage

```java
import com.neel.syntaxvalidation.SyntaxValidationLibrary;
import com.neel.syntaxvalidation.model.*;

// Create the library instance (thread-safe, reuse across your application)
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

// Describe the proposed modification using the Builder
ModificationRequest request = ModificationRequest.builder()
    .filePath("src/main/java/com/example/Foo.java")
    .fromLine(10)
    .toLine(15)
    .replacement("    public String greet(String name) {\n"
               + "        return \"Hello, \" + name;\n"
               + "    }\n")
    .build();

// Validate
ValidationResult result = library.validate(request);

if (result.isValid()) {
    System.out.println("✓ Modification is syntactically valid");
} else {
    System.out.println("✗ " + result.getMessage());
    for (ValidationError error : result.getErrors()) {
        System.out.printf("  Line %d, Col %d: %s%n",
            error.getLine(), error.getColumn(), error.getMessage());
    }
}
```

---

## API Reference

### `SyntaxValidationLibrary`

The main entry point. Thread-safe; intended to be a long-lived singleton.

```java
// Default construction (all defaults)
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

// Construction with a custom ValidatorFactory (e.g., for custom binary paths)
ValidatorFactory factory = new ValidatorFactory();
SyntaxValidationLibrary library = new SyntaxValidationLibrary(factory);

// Primary validation method
ValidationResult validate(ModificationRequest request);

// Mixed content validation (HTML with embedded CSS/JS)
ValidationResult validateMixedContent(String htmlSource);
ValidationResult validateMixedContent(ModificationRequest request);

// Cache management
boolean invalidateCache(Path path);  // Evict a single file from cache
void clearCache();                    // Evict all cached files
```

### `ModificationRequest`

Immutable description of a proposed source-code modification. Created exclusively through its `Builder`.

```java
// Create using the builder
ModificationRequest request = ModificationRequest.builder()
    .filePath("src/script.js")
    .fromLine(3)
    .toLine(5)
    .replacement("function add(a, b) {\n  return a + b;\n}")
    .build();

// Getters
String getFilePath();       // The file path (absolute or relative)
int getFromLine();          // 1-based starting line (inclusive)
int getToLine();            // 1-based ending line (inclusive)
String getReplacement();    // The replacement text (never null, defaults to "")
```

**Constraints (enforced by the Builder):**
- `fromLine >= 1`
- `toLine >= fromLine`
- `filePath` must not be null or blank
- `replacement` defaults to empty string if null

### `ValidationResult`

Immutable outcome of a syntax validation. Created via static factory methods.

```java
// Factory methods
ValidationResult result = ValidationResult.valid("Validation passed");
ValidationResult result = ValidationResult.invalid("Syntax errors found");
ValidationResult result = ValidationResult.invalid("Syntax errors found", List.of(error1, error2));
ValidationResult result = ValidationResult.invalid("Syntax errors found", singleError);

// Accessors
boolean isValid();                      // true if no errors
String getMessage();                    // Overall explanation message (never null)
List<ValidationError> getErrors();      // Unmodifiable list of detailed errors (empty when valid)
boolean hasErrors();                    // true if at least one error recorded
```

**Note:** `ValidationResult` is a `final class`, not a Java record. It uses static factory methods `valid()` and `invalid()` for construction.

### `ValidationError`

Immutable single diagnostic from a validator.

```java
// Constructor
ValidationError error = new ValidationError(line, column, message, toolOutput);

// Accessors
int getLine();          // 1-based line number (0 if unknown)
int getColumn();        // 1-based column number (0 if unknown)
String getMessage();    // Human-readable error description (never null)
String getToolOutput(); // Raw output from the external tool (may be null for in-process errors)
```

### `Language`

Enum of supported source languages.

```java
public enum Language {
    JAVASCRIPT,
    HTML,
    CSS,
    PHP,
    TYPESCRIPT,
    PYTHON,
    JAVA;
    
    // Returns an unmodifiable set of file extensions for this language
    // e.g., JAVA → {".java"}, PYTHON → {".py"}
    Set<String> getExtensions();
    
    // Find language by file extension (case-insensitive)
    // Returns Optional.empty() for unsupported extensions
    static Optional<Language> fromExtension(String extension);
    
    // Find language by file path (extracts extension and delegates to fromExtension)
    // Returns Optional.empty() for unsupported paths
    static Optional<Language> fromPath(Path path);
}
```

**Usage:**
```java
Optional<Language> lang = Language.fromExtension(".java");
// → Optional.of(Language.JAVA)

Optional<Language> lang = Language.fromPath(Path.of("src/app.py"));
// → Optional.of(Language.PYTHON)
```

---

## Usage Examples

### Example 1: Validate a Java Method Replacement

```java
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

ModificationRequest request = ModificationRequest.builder()
    .filePath("src/main/java/com/example/Calculator.java")
    .fromLine(25)
    .toLine(30)
    .replacement("""
        public int add(int a, int b) {
            return a + b;
        }
    """)
    .build();

ValidationResult result = library.validate(request);
assert result.isValid() : "Java syntax should be valid";
```

### Example 2: Validate a Python Function

```java
ModificationRequest request = ModificationRequest.builder()
    .filePath("src/utils/helpers.py")
    .fromLine(10)
    .toLine(15)
    .replacement("""
    def calculate_total(items: list[float]) -> float:
        \\"\\"\\"Calculate the sum of all items.\\"\\"\\"
        total = 0.0
        for item in items:
            total += item
        return total
    """)
    .build();

ValidationResult result = library.validate(request);
```

### Example 3: Validate JavaScript with Error Handling

```java
try {
    ModificationRequest request = ModificationRequest.builder()
        .filePath("src/app.js")
        .fromLine(1)
        .toLine(5)
        .replacement("""
        function fetchData(url) {
            const response = await fetch(url);
            return response.json();
        }
        """)
        .build();

    ValidationResult result = library.validate(request);

    if (!result.isValid()) {
        for (ValidationError error : result.getErrors()) {
            System.err.printf("Line %d, Col %d — %s%n",
                error.getLine(), error.getColumn(), error.getMessage());
        }
    }
} catch (IllegalArgumentException e) {
    System.err.println("Invalid request parameters: " + e.getMessage());
}
```

### Example 4: Validate CSS Stylesheet

```java
ModificationRequest request = ModificationRequest.builder()
    .filePath("styles/main.css")
    .fromLine(1)
    .toLine(10)
    .replacement("""
    .container {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 2rem;
        background-color: var(--bg-primary);
    }

    .container.active {
        opacity: 1;
    }
    """)
    .build();

ValidationResult result = library.validate(request);
System.out.println("CSS valid: " + result.isValid());
```

### Example 5: Validate PHP Code

```java
ModificationRequest request = ModificationRequest.builder()
    .filePath("src/Controller/UserController.php")
    .fromLine(20)
    .toLine(35)
    .replacement("""
    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'name' => 'required|string|max:255',
            'email' => 'required|email|unique:users',
        ]);

        $user = User::create($validated);

        return response()->json($user, 201);
    }
    """)
    .build();

ValidationResult result = library.validate(request);
```

### Example 6: Validate Mixed HTML/CSS/JS Content

```java
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

String htmlContent = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <title>Dashboard</title>
        <style>
            body { font-family: sans-serif; margin: 0; }
            .header { background: #333; color: white; padding: 1rem; }
        </style>
    </head>
    <body>
        <div class="header">
            <h1>Dashboard</h1>
        </div>
        <script>
            document.querySelector('.header').addEventListener('click', () => {
                console.log('Header clicked');
            });
        </script>
    </body>
    </html>
    """;

// Validate all embedded content (HTML structure + CSS + JavaScript)
ValidationResult result = library.validateMixedContent(htmlContent);
System.out.println("Mixed content valid: " + result.isValid());
```

### Example 7: Batch Validation

```java
List<ModificationRequest> requests = List.of(
    ModificationRequest.builder().filePath("A.java").fromLine(1).toLine(5).replacement("class A {}").build(),
    ModificationRequest.builder().filePath("b.py").fromLine(1).toLine(3).replacement("def f():\n    pass").build(),
    ModificationRequest.builder().filePath("c.js").fromLine(1).toLine(3).replacement("const x = 1;").build()
);

SyntaxValidationLibrary library = new SyntaxValidationLibrary();

List<ValidationResult> results = requests.stream()
    .map(library::validate)
    .toList();

long errorCount = results.stream()
    .filter(r -> !r.isValid())
    .count();

System.out.printf("Validated %d files: %d valid, %d with errors%n",
    results.size(), results.size() - errorCount, errorCount);
```

### Example 8: Auto-detect Language from File Path

```java
Path filePath = Path.of("src/utils/parser.py");
Optional<Language> language = Language.fromPath(filePath);

if (language.isPresent()) {
    ModificationRequest request = ModificationRequest.builder()
        .filePath(filePath.toString())
        .fromLine(1)
        .toLine(10)
        .replacement("def parse(input: str) -> dict:\n    return {}")
        .build();

    ValidationResult result = library.validate(request);
} else {
    System.err.println("Unsupported file type: " + filePath);
}
```

---

## Configuration

### Library Construction

```java
// Default construction
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

// With custom ValidatorFactory
ValidatorFactory factory = new ValidatorFactory();
SyntaxValidationLibrary library = new SyntaxValidationLibrary(factory);
```

### External Tool Requirements

For **subprocess-based validation** (fallback when in-process engine is unavailable or as primary strategy for Java), the following tools must be available on `PATH`:

| Tool | Required Version | Install Command |
|------|-----------------|-----------------|
| `javac` | JDK 17+ | [Adoptium](https://adoptium.net/) |
| `node` | 18+ | [Node.js](https://nodejs.org/) |
| `python` | 3.8+ | [Python](https://python.org/) |
| `tsc` | 5+ | `npm install -g typescript` |
| `php` | 8.0+ | [php.net](https://php.net/) |
| `stylelint` | 15+ | `npm install -g stylelint` |
| `html-validate` | 8+ | `npm install -g html-validate` |

**Note:** External tools are only required for their respective language's fallback strategy. The in-process validation engines (pure Java) work without any external tools.

---

## Validation Strategies

Each language validator uses a **two-tier strategy**:

### Tier 1: In-Process Engine (Preferred)

Hand-written lexers and parsers implemented in pure Java. These provide:
- Zero subprocess overhead
- No external tool dependencies
- Deterministic behavior across environments
- Immediate validation results

**Available for:** Java (partial), Python, JavaScript, TypeScript, CSS, HTML, PHP, Mixed HTML/CSS/JS

### Tier 2: External Tool (Fallback)

Shells out to the language's native compiler/linter via `ProcessExecutor`. Used when:
- In-process engine produces ambiguous results
- External tool provides better error messages
- For Java: `javac` is the primary strategy for full-fidelity checking

**Process flow:**
1. Write modified source to a temp file
2. Invoke the external tool with the temp file path
3. Parse stdout/stderr for error messages
4. Map errors to `ValidationError` objects with line/column information
5. Clean up temp file

---

## File Cache

The `FileCache` maintains an in-memory snapshot of files that have been read during validation.

### Behavior

- **Cache-first reads** — When a file is requested, the cache is checked before hitting disk
- **Automatic population** — Files are cached on first read
- **Version tracking** — Each `FileCacheEntry` records the file's last-modified timestamp. Subsequent reads check if the on-disk file has changed and refresh if needed
- **LRU eviction** — When the cache exceeds its capacity, the least-recently-used entry is removed
- **Thread-safe** — Uses `ConcurrentHashMap` internally

### Cache Lifecycle

```
File requested → Cache lookup
  ├─ HIT + version matches → Return cached content
  ├─ HIT + version mismatch → Re-read from disk, update cache
  └─ MISS → Read from disk, populate cache, return content
```

### Manual Cache Control

```java
// Evict a specific file from cache (e.g., after external modification)
library.invalidateCache(Path.of("src/main/Foo.java"));

// Clear the entire cache
library.clearCache();
```

---

## Thread Safety

The library is designed for safe concurrent use:

| Component | Thread Safety | Mechanism |
|-----------|--------------|-----------|
| `SyntaxValidationLibrary` | ✅ Safe | Stateless; delegates to thread-safe components |
| `FileCache` | ✅ Safe | `ConcurrentHashMap` |
| `FileCacheEntry` | ✅ Safe | Immutable final class with defensive copies |
| `ModificationApplier` | ✅ Safe | Stateless utility |
| `ValidatorFactory` | ✅ Safe | Stateless factory |
| All validators | ✅ Safe | Stateless; fresh instances created per request |
| `ProcessExecutor` | ✅ Safe | No shared mutable state; each call creates a new `ProcessBuilder` |
| All model classes | ✅ Safe | Immutable final classes with defensive copies |

**Usage guideline:** Create one `SyntaxValidationLibrary` instance and share it across threads.

```java
// Safe: single instance, multiple threads
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

ExecutorService executor = Executors.newFixedThreadPool(8);
requests.forEach(request ->
    executor.submit(() -> {
        ValidationResult result = library.validate(request);
        // process result...
    })
);
```

---

## Error Handling

### Exception Hierarchy

The library throws the following exceptions:

| Exception | When | Cause |
|-----------|------|-------|
| `IllegalArgumentException` | Invalid `ModificationRequest` parameters | `fromLine < 1`, `toLine < fromLine`, null/blank `filePath` |
| `NullPointerException` | Null arguments to non-null parameters | Null `language`, `source`, etc. |
| `IllegalStateException` | No validator registered for language | Rare; indicates misconfiguration |

### Validation Failure vs. Exception

The library distinguishes between:
- **Validation failure** (returned as `ValidationResult` with `isValid() == false`): The modification has syntax errors
- **Exception** (thrown): The request itself is invalid or the library cannot perform validation

```java
try {
    ValidationResult result = library.validate(request);

    if (!result.isValid()) {
        // Handle syntax errors — the modification would produce invalid code
        result.getErrors().forEach(error ->
            System.out.printf("Line %d: %s%n", error.getLine(), error.getMessage()));
    }
} catch (IllegalArgumentException e) {
    // Handle invalid request parameters
    System.err.println("Bad request: " + e.getMessage());
} catch (IllegalStateException e) {
    // Handle missing validator
    System.err.println("Configuration error: " + e.getMessage());
}
```

---

## Extending the Library

### Adding a New Language Validator

1. **Add the language enum value** to `Language.java`:

```java
RUST(".rs"),
```

2. **Create the validator class** extending `AbstractLanguageValidator`:

```java
package com.neel.syntaxvalidation.validator.rust;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;

public class RustValidator extends AbstractLanguageValidator {

    public RustValidator(BinaryResolver binaryResolver, ProcessExecutor processExecutor) {
        super(binaryResolver, processExecutor);
    }

    @Override
    public Language getLanguage() {
        return Language.RUST;
    }

    @Override
    protected ValidationResult validateByExternalTool(String sourceCode, String tempFilePath) {
        // Implement rustc-based validation
        // ...
    }

    @Override
    protected ValidationResult validateBySyntaxEngine(String sourceCode) {
        // Implement in-process Rust parser (optional)
        // ...
    }
}
```

3. **Register in `ValidatorFactory`**:

```java
// In ValidatorFactory.java
case RUST -> Optional.of(new RustValidator(binaryResolver, processExecutor));
```

4. **Write tests** following the existing pattern (see `JavaValidatorTest`, `PythonValidatorTest`, etc.)

---

## Building from Source

### Prerequisites

- **JDK 25** (or compatible version)
- No additional dependencies required (Gradle wrapper handles Gradle itself)

### Build Commands

```bash
# Full build (compile + test + package)
./gradlew build

# Compile only
./gradlew compileJava

# Create JAR
./gradlew jar

# Clean build
./gradlew clean build
```

### Gradle Configuration

**`gradle.properties`:**
```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.parallel=true
```

**`build.gradle`:**
```groovy
plugins {
    id 'java-library'
}

group = 'com.neel'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testImplementation 'org.assertj:assertj-core:3.27.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

---

## Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.neel.syntaxvalidation.validator.java.JavaValidatorTest"

# Run specific test method
./gradlew test --tests "com.neel.syntaxvalidation.validator.java.JavaValidatorTest.validJavaCodeShouldReturnSuccess"

# Run tests with detailed output
./gradlew test --info

# Generate test report (HTML)
# Report available at: build/reports/tests/test/index.html
```

### Test Suite Overview

| Test Class | Component Tested |
|-----------|-----------------|
| `SyntaxValidationLibraryTest` | End-to-end integration |
| `JavaValidatorTest` | Java validation (external + in-process) |
| `PythonValidatorTest` | Python validation (in-process) |
| `JavaScriptSyntaxEngineTest` | JavaScript in-process engine |
| `TypeScriptSyntaxEngineTest` | TypeScript in-process engine |
| `CssSyntaxEngineTest` | CSS in-process engine |
| `HtmlSyntaxEngineTest` | HTML in-process engine |
| `PhpSyntaxEngineTest` | PHP in-process engine |
| `FileCacheTest` | Cache behavior and eviction |
| `ProcessExecutorTest` | External process execution |
| `BinaryResolverTest` | Tool binary resolution |
| `ModificationApplierTest` | Line-range modification application |
| `ValidationResultTest` | Result model behavior |

---

## Project Structure

```
syntax-validation/
├── build.gradle                          # Build configuration
├── settings.gradle                       # Project settings
├── gradle.properties                     # Gradle JVM and build options
├── gradlew / gradlew.bat                 # Gradle wrapper scripts
├── gradle/wrapper/                       # Gradle wrapper JARs and properties
├── README.md                             # Project README
├── TECHNICAL_INFORMATION.md              # This file
├── PRODUCTION_READINESS_REPORT.md        # Production readiness assessment
├── agent.md                              # AI agent integration guide
│
└── src/
    ├── main/java/com/neel/syntaxvalidation/
    │   ├── SyntaxValidationLibrary.java       # Public API facade
    │   │
    │   ├── model/                             # Immutable data types
    │   │   ├── Language.java                  # Supported language enum
    │   │   ├── ModificationRequest.java       # Edit description (builder pattern)
    │   │   ├── ValidationResult.java          # Validation outcome (factory methods)
    │   │   └── ValidationError.java           # Single diagnostic
    │   │
    │   ├── binary/                            # Tool resolution
    │   │   └── BinaryResolver.java            # Finds external tool paths
    │   │
    │   ├── cache/                             # File caching
    │   │   ├── FileCache.java                 # Thread-safe ConcurrentHashMap cache
    │   │   └── FileCacheEntry.java            # Cached file snapshot
    │   │
    │   ├── modification/                      # Code modification
    │   │   └── ModificationApplier.java       # Applies line-range edits
    │   │
    │   ├── process/                           # External process execution
    │   │   ├── ProcessExecutor.java           # Runs external tools
    │   │   └── ProcessResult.java             # Process outcome
    │   │
    │   └── validator/                         # Validation engines
    │       ├── LanguageValidator.java          # Strategy interface
    │       ├── ValidatorFactory.java           # Factory/registry
    │       ├── AbstractLanguageValidator.java  # Template method base
    │       │
    │       ├── java/                          # Java validation
    │       │   ├── JavaValidator.java
    │       │   ├── JavaSyntaxEngine.java
    │       │   ├── JavacOutputParser.java
    │       │   ├── JavaLexer.java
    │       │   ├── JavaToken.java
    │       │   ├── JavaTokenType.java
    │       │   └── checker/
    │       │       ├── SyntaxChecker.java
    │       │       ├── DelimiterBalanceChecker.java
    │       │       ├── TokenizationErrorChecker.java
    │       │       └── KeywordUsageChecker.java
    │       │
    │       ├── python/                        # Python validation
    │       │   ├── PythonValidator.java
    │       │   ├── PythonSyntaxEngine.java
    │       │   ├── PythonOutputParser.java
    │       │   ├── PythonLexer.java
    │       │   ├── PythonParser.java
    │       │   ├── PythonToken.java
    │       │   └── PythonTokenType.java
    │       │
    │       ├── javascript/                    # JavaScript validation
    │       │   ├── JavaScriptValidator.java
    │       │   ├── JavaScriptSyntaxEngine.java
    │       │   └── JavaScriptSyntaxTokenizer.java
    │       │
    │       ├── typescript/                    # TypeScript validation
    │       │   ├── TypeScriptValidator.java
    │       │   ├── TypeScriptSyntaxEngine.java
    │       │   ├── TypeScriptSyntaxTokenizer.java
    │       │   ├── TsToken.java
    │       │   └── TsTokenType.java
    │       │
    │       ├── css/                           # CSS validation
    │       │   ├── CssValidator.java
    │       │   ├── CssSyntaxEngine.java
    │       │   └── StylelintOutputParser.java
    │       │
    │       ├── html/                          # HTML validation
    │       │   ├── HtmlValidator.java
    │       │   └── HtmlSyntaxEngine.java
    │       │
    │       ├── php/                           # PHP validation
    │       │   ├── PhpValidator.java
    │       │   └── PhpSyntaxEngine.java
    │       │
    │       └── mixed/                         # Mixed content validation
    │           ├── MixedContentValidator.java
    │           ├── MixedContentSyntaxEngine.java
    │           └── HtmlContentExtractor.java
    │
    └── test/java/com/neel/syntaxvalidation/
        ├── SyntaxValidationLibraryTest.java
        ├── model/
        │   └── ValidationResultTest.java
        ├── binary/
        │   └── BinaryResolverTest.java
        ├── cache/
        │   └── FileCacheTest.java
        ├── process/
        │   └── ProcessExecutorTest.java
        ├── modification/
        │   └── ModificationApplierTest.java
        └── validator/
            ├── java/
            │   └── JavaValidatorTest.java
            ├── python/
            │   └── PythonValidatorTest.java
            ├── javascript/
            │   └── JavaScriptSyntaxEngineTest.java
            ├── typescript/
            │   └── TypeScriptSyntaxEngineTest.java
            ├── css/
            │   └── CssSyntaxEngineTest.java
            ├── html/
            │   └── HtmlSyntaxEngineTest.java
            └── php/
                └── PhpSyntaxEngineTest.java
```

---

## Dependencies

### Runtime Dependencies

**None.** The library has zero runtime dependencies beyond the Java standard library (JDK 25).

### Build/Test Dependencies

| Dependency | Version | Scope | Purpose |
|-----------|---------|-------|---------|
| JUnit Jupiter | 5.11.4 | test | Unit testing framework |
| AssertJ | 3.27.0 | test | Fluent assertions |
| JUnit Platform Launcher | — | testRuntime | Test execution |

### Gradle Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `java-library` | (built-in) | Java library conventions |
| Gradle Wrapper | 9.3.1 | Consistent build environment |

---

## Known Limitations

1. **Java validation** — In-process `JavaSyntaxEngine` provides basic structural checks (delimiter balance, keyword usage, tokenization errors). For full-fidelity validation (generics, annotations, complex expressions), the library falls back to `javac`, which requires a JDK installation.

2. **TypeScript validation** — In-process `TypeScriptSyntaxEngine` validates syntax structure but does not perform full type checking. For type-level validation, `tsc` is used as the external tool.

3. **Mixed content** — `validateMixedContent()` extracts `<script>` and `<style>` blocks from HTML and validates them independently. It does not validate the interactions between HTML, CSS, and JavaScript.

4. **External tool availability** — When external tools are not on `PATH`, the library gracefully degrades to in-process validation only. Error messages may be less detailed in this mode.

5. **File encoding** — All file I/O assumes UTF-8 encoding. Files with other encodings may produce incorrect validation results.

6. **Large files** — Files larger than available heap memory may cause `OutOfMemoryError`. The in-process engines load entire files into memory for parsing.

---

## Changelog

### Version 1.0.0 (Current)

- Initial release
- Support for 7 languages: Java, Python, JavaScript, TypeScript, CSS, HTML, PHP
- Mixed HTML/CSS/JS content validation via `validateMixedContent()`
- Dual validation strategy (in-process + external tools)
- Thread-safe in-memory file cache with LRU eviction
- Builder pattern for `ModificationRequest` construction
- Factory methods for `ValidationResult` (`valid()` / `invalid()`)
- Comprehensive test suite across all language validators
- Zero runtime dependencies
