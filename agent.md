# Agent Guide — Syntax Validation Library (Java)

> **Project root:** `F:\code-helper-mcp-library-java`
> **Language:** Java 21+ | **Build:** Gradle (Groovy DSL) | **Testing:** JUnit 5 + AssertJ

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Directory Structure](#2-directory-structure)
3. [Core Architecture](#3-core-architecture)
4. [Setup & Development Workflow](#4-setup--development-workflow)
5. [Key Classes Reference](#5-key-classes-reference)
6. [Adding Support for a New Language](#6-adding-support-for-a-new-language)
7. [Handling Project Conversions](#7-handling-project-conversions)
8. [Testing & Quality](#8-testing--quality)
9. [External Binary Configuration](#9-external-binary-configuration)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Project Overview

This is a **pluggable Java library** that validates the syntactic correctness of proposed source-code modifications **before** they are applied. It is designed for integration with LLM-powered coding agents and IDE tooling.

**Current language support:**

| Language   | Extensions        | External Binary | Embedded Fallback Engine     |
|------------|-------------------|-----------------|------------------------------|
| JavaScript | `.js`, `.mjs`, `.cjs` | `node` (--check) | `JavaScriptSyntaxEngine` (brace/paren/bracket balance, statement analysis) |
| HTML       | `.html`, `.htm`   | `vnu.jar` (Nu Html Checker) | `HtmlSyntaxEngine` (tag matching, attribute validation, void elements, DOCTYPE) |
| CSS        | `.css`            | `stylelint`     | `CssSyntaxEngine` (brace balance, selector validation, declaration checks, at-rule checks) |
| Mixed Content (HTML + embedded CSS/JS) | — | Delegates to above | `MixedContentSyntaxEngine` (orchestrates all three) |

**Placeholder entries** (enum defined, no validator yet): `TYPESCRIPT`, `PYTHON`, `JAVA`.

**Key design principle:** Every validator uses a **dual-strategy** approach — an external binary is tried first for deep validation; if unavailable, a pure-Java embedded engine provides structural fallback. The library never modifies the original file on disk.

---

## 2. Directory Structure

```
src/
├── main/java/com/neel/syntaxvalidation/
│   ├── SyntaxValidationLibrary.java          # PUBLIC FACADE — entry point for all consumers
│   ├── model/
│   │   ├── Language.java                     # Enum mapping file extensions → languages
│   │   ├── ModificationRequest.java          # Immutable DTO: file path + line range + replacement text
│   │   ├── ValidationResult.java             # Immutable outcome: valid/invalid + message + errors
│   │   └── ValidationError.java              # Single diagnostic: line, column, message, source
│   ├── cache/
│   │   ├── FileCache.java                    # Thread-safe in-memory file content cache
│   │   └── FileCacheEntry.java               # Immutable snapshot (path, lines, lastModified)
│   ├── modification/
│   │   └── ModificationApplier.java          # Applies line-range replacement to in-memory lines
│   ├── binary/
│   │   └── BinaryResolver.java               # Resolves external tool paths (sys prop → env var → PATH)
│   ├── process/
│   │   ├── ProcessExecutor.java              # Runs external binaries with timeout + output capture
│   │   └── ProcessResult.java                # Immutable capture: exitCode, stdout, stderr, timedOut
│   └── validator/
│       ├── LanguageValidator.java            # STRATEGY INTERFACE — validate(String) → ValidationResult
│       ├── AbstractLanguageValidator.java    # Template: binary resolution, temp file, command building
│       ├── ValidatorFactory.java             # Registry: Language → LanguageValidator; also provides MixedContentValidator
│       ├── javascript/
│       │   ├── JavaScriptValidator.java      # External (node --check) + embedded fallback
│       │   ├── JavaScriptSyntaxEngine.java   # Pure-Java JS brace/paren/bracket/statement analysis
│       │   ├── JavaScriptSyntaxTokenizer.java # Hand-written JS lexer
│       │   ├── JsToken.java / JsTokenType.java # Token model + enum
│       │   └── NodeCheckOutputParser.java    # Parses `node --check` stderr into ValidationErrors
│       ├── html/
│       │   ├── HtmlValidator.java            # External (vnu.jar --format json) + embedded fallback
│       │   ├── HtmlSyntaxEngine.java         # Pure-Java HTML tag matching, attribute, void-element checks
│       │   └── VnuOutputParser.java          # Parses vnu JSON output into ValidationErrors
│       ├── css/
│       │   ├── CssValidator.java             # External (stylelint --formatter json) + embedded fallback
│       │   ├── CssSyntaxEngine.java          # Pure-Java CSS structural checks
│       │   └── StylelintOutputParser.java    # Parses stylelint JSON into ValidationErrors
│       └── mixed/
│           ├── MixedContentValidator.java    # Orchestrates HTML + embedded CSS + embedded JS validation
│           ├── MixedContentSyntaxEngine.java # Engine coordinating sub-validators with line remapping
│           ├── HtmlContentExtractor.java     # Extracts <style>/<script> blocks from HTML
│           └── ExtractedBlock.java           # Immutable model: language, content, startLine
└── test/java/com/neel/syntaxvalidation/
    ├── SyntaxValidationLibraryTest.java      # Integration tests for the facade
    ├── model/                                # Unit tests for Language, ModificationRequest, ValidationResult, ValidationError
    ├── cache/                                # Unit tests for FileCache
    ├── modification/                         # Unit tests for ModificationApplier
    ├── binary/                               # Unit tests for BinaryResolver
    ├── process/                              # Unit tests for ProcessExecutor
    └── validator/
        ├── ValidatorFactoryTest.java
        ├── javascript/JavaScriptValidatorTest.java
        ├── html/HtmlValidatorTest.java
        ├── css/CssValidatorTest.java
        └── mixed/MixedContentValidatorTest.java
```

**Build files:**
- `build.gradle` — Java library plugin, group `com.neel`, version `1.0.0`
- `settings.gradle` — root project name `syntax-validation`
- `gradle.properties` — JVM args, caching, parallel builds enabled

---

## 3. Core Architecture

### 3.1 Validation Flow

```
Consumer calls SyntaxValidationLibrary.validate(ModificationRequest)
    │
    ├─ 1. Language.fromPath(filePath)     → detect language from extension
    ├─ 2. ValidatorFactory.getValidator() → get LanguageValidator for that language
    ├─ 3. FileCache.getOrLoad(filePath)   → load file content into memory
    ├─ 4. ModificationApplier.apply()     → apply replacement to in-memory lines
    └─ 5. LanguageValidator.validate()    → run external binary OR embedded engine
                                                    │
                                                    ├─ Binary available? → run ProcessExecutor → parse output
                                                    └─ Binary missing?   → run EmbeddedEngine  → return structural errors
```

### 3.2 Key Interfaces & Contracts

**`LanguageValidator`** (Strategy interface):
```java
public interface LanguageValidator {
    Language getLanguage();
    ValidationResult validate(String content);
}
```

**`ModificationRequest`** (Builder pattern):
```java
ModificationRequest.builder()
    .filePath("/path/to/file.js")
    .fromLine(10)
    .toLine(15)
    .replacement("new code here")
    .build();
```

**`ValidationResult`** (Factory methods):
```java
ValidationResult.valid("message")
ValidationResult.invalid("message", List.of(errors))
```

### 3.3 Thread Safety

The library is **fully thread-safe**:
- `FileCache` uses `ConcurrentHashMap`
- All validators are stateless or use thread-safe collaborators
- `ModificationApplier` never mutates input lists

---

## 4. Setup & Development Workflow

### 4.1 Prerequisites

- **JDK 21+** (uses `List.of()`, `Files.writeString()`, pattern matching, etc.)
- **Gradle 8.1+** (wrapper included)

### 4.2 Build Commands

```bash
# Full build (compile + test)
./gradlew build

# Compile only
./gradlew classes

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.neel.syntaxvalidation.validator.javascript.JavaScriptValidatorTest"

# Generate JAR
./gradlew jar

# Clean build
./gradlew clean build
```

### 4.3 Running Tests

```bash
# All tests with verbose output
./gradlew test --info

# Specific test method
./gradlew test --tests "*.JavaScriptValidatorTest.should detect syntax error"
```

### 4.4 Project Navigation Tips

| What you want to do | Where to look |
|---------------------|---------------|
| Understand the public API | `SyntaxValidationLibrary.java` |
| Add a new language | `Language.java` enum + `validator/` package + `ValidatorFactory.java` |
| Debug validation failures | `*SyntaxEngine.java` (embedded) or `*OutputParser.java` (external) |
| Understand modification application | `ModificationApplier.java` |
| Configure external tools | `BinaryResolver.java` (sys prop → env var → PATH) |
| See how external tools are invoked | `ProcessExecutor.java` + `AbstractLanguageValidator.java` |

### 4.5 Best Practices

1. **Never modify files on disk.** The library validates in-memory copies only.
2. **Always use the builder** for `ModificationRequest` — it validates inputs.
3. **Line numbers are 1-based** throughout the codebase (not 0-based).
4. **Use `@TempDir`** in tests for any file I/O — never write to shared directories.
5. **Follow the dual-strategy pattern** when adding validators: external binary first, embedded fallback second.
6. **All model classes are immutable** — use factory methods and builders, not setters.

---

## 5. Key Classes Reference

### 5.1 `SyntaxValidationLibrary` (Facade)

The single entry point for consumers. Three main methods:

| Method | Purpose |
|--------|---------|
| `validate(ModificationRequest)` | Validate a modification against a file (auto-detects language) |
| `validateMixedContent(String)` | Validate raw HTML including embedded `<style>` and `<script>` blocks |
| `validateMixedContent(ModificationRequest)` | Validate a modification to an HTML file with mixed content |

### 5.2 `Language` (Enum)

Maps file extensions to language identifiers. Resolution order in `ValidatorFactory`:
1. Exact language match
2. `MixedContentValidator` for HTML files (if registered)

### 5.3 `ValidatorFactory` (Registry)

Maintains a `Map<Language, LanguageValidator>` and a `MixedContentValidator`.

```java
ValidatorFactory factory = new ValidatorFactory();
// Uses default validators (JavaScript, HTML, CSS, Mixed)

// Custom binary paths:
ValidatorFactory factory = new ValidatorFactory(Map.of(
    Language.JAVASCRIPT, new JavaScriptValidator("/usr/local/bin/node"),
    Language.HTML, new HtmlValidator("/opt/vnu/vnu.jar"),
    Language.CSS, new CssValidator("/usr/local/bin/stylelint")
));
```

### 5.4 `AbstractLanguageValidator` (Template)

Base class for binary-backed validators. Subclasses implement:

| Method | Responsibility |
|--------|----------------|
| `getFileExtension()` | Temp file extension (e.g., `.js`, `.html`, `.css`) |
| `buildCommand(binaryPath, tempFile)` | CLI command list for the external tool |
| `parseOutput(ProcessResult, Path)` | Convert tool output to `ValidationResult` |
| `binaryNotFoundMessage()` | User-facing message when binary is missing |

### 5.5 `BinaryResolver`

Resolves external tool paths in this priority order:

1. **System property:** `syntaxvalidation.bin.<name>` (e.g., `syntaxvalidation.bin.node`)
2. **Environment variable:** `SYNTAX_VALIDATION_<NAME>` (e.g., `SYNTAX_VALIDATION_NODE`)
3. **System PATH:** searches for executable named `<name>`

---

## 6. Adding Support for a New Language

This section provides a complete step-by-step guide for extending the library with a new language (using **Python** as an example).

### Step 1: Update the `Language` Enum

**File:** `src/main/java/com/neel/syntaxvalidation/model/Language.java`

The enum already has a `PYTHON("py")` placeholder. If your language isn't listed, add it:

```java
// Existing placeholder — no changes needed for Python
PYTHON("py"),

// Example: adding Rust support
RUST("rs"),
```

Each constant takes one or more file extensions (without leading dots).

### Step 2: Create the Embedded Syntax Engine

**New file:** `src/main/java/com/neel/syntaxvalidation/validator/python/PythonSyntaxEngine.java`

The embedded engine provides structural validation when the external binary is unavailable. Implement a singleton with a `validate(String content)` method:

```java
package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java fallback engine for Python syntax validation.
 * Performs structural checks: indentation consistency, bracket matching,
 * basic statement validation, string literal integrity.
 */
public final class PythonSyntaxEngine {

    private static final PythonSyntaxEngine INSTANCE = new PythonSyntaxEngine();

    public static PythonSyntaxEngine getInstance() {
        return INSTANCE;
    }

    private PythonSyntaxEngine() {}

    public ValidationResult validate(String content) {
        if (content == null || content.isBlank()) {
            return ValidationResult.valid("Empty content is trivially valid.");
        }

        List<ValidationError> errors = new ArrayList<>();
        String[] lines = content.split("\\R", -1);

        // Example: check for unclosed brackets
        // Example: check for mixed tabs/spaces
        // Example: check for incomplete statements at EOF

        if (errors.isEmpty()) {
            return ValidationResult.valid("Python syntax appears structurally correct.");
        }
        return ValidationResult.invalid(
                "Python syntax validation found " + errors.size() + " issue(s).", errors);
    }
}
```

**Validation checks to consider for embedded engines:**
- Bracket/parenthesis/brace balance
- String literal integrity (unclosed quotes)
- Indentation consistency (Python-specific)
- Empty block detection (`def`, `class`, `if` without body)
- Comment syntax validation

### Step 3: Create the Output Parser (for External Binary)

**New file:** `src/main/java/com/neel/syntaxvalidation/validator/python/PythonOutputParser.java`

Parses the external tool's output (typically JSON or regex-matched stderr) into `ValidationResult`:

```java
package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PythonOutputParser {

    // Pattern: "file.py:10:5: E999 SyntaxError: invalid syntax"
    private static final Pattern ERROR_PATTERN = 
        Pattern.compile("^.+?:(\\d+):(\\d+):\\s+(.+)$", Pattern.MULTILINE);

    private PythonOutputParser() {}

    public static ValidationResult parse(String output) {
        if (output == null || output.isBlank()) {
            return ValidationResult.valid("No output from Python syntax checker.");
        }

        List<ValidationError> errors = new ArrayList<>();
        Matcher matcher = ERROR_PATTERN.matcher(output);
        while (matcher.find()) {
            int line = Integer.parseInt(matcher.group(1));
            int col = Integer.parseInt(matcher.group(2));
            String message = matcher.group(3);
            errors.add(new ValidationError(line, col, message, output));
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid("Python syntax checker reported no errors.");
        }
        return ValidationResult.invalid(
                "Python syntax checker found " + errors.size() + " error(s).", errors);
    }
}
```

### Step 4: Create the Validator Class

**New file:** `src/main/java/com/neel/syntaxvalidation/validator/python/PythonValidator.java`

Extend `AbstractLanguageValidator` following the dual-strategy pattern:

```java
package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PythonValidator extends AbstractLanguageValidator {

    private static final String BINARY_NAME = "python3"; // or "pyflakes", "mypy"

    private final BinaryResolver binaryResolver;
    private final ProcessExecutor processExecutor;

    public PythonValidator() {
        this(null, new BinaryResolver(), new ProcessExecutor());
    }

    public PythonValidator(String preferredBinaryPath) {
        this(preferredBinaryPath, new BinaryResolver(), new ProcessExecutor());
    }

    public PythonValidator(String preferredBinaryPath,
                           BinaryResolver binaryResolver,
                           ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME,
                Objects.requireNonNull(binaryResolver),
                Objects.requireNonNull(processExecutor));
        this.binaryResolver = binaryResolver;
        this.processExecutor = processExecutor;
    }

    @Override
    public Language getLanguage() {
        return Language.PYTHON;
    }

    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;
        Optional<String> binary = resolveBinary();

        if (binary.isPresent()) {
            Path tempFile = null;
            try {
                tempFile = createTempFile();
                Files.writeString(tempFile, safeContent, StandardCharsets.UTF_8);
                List<String> command = buildCommand(binary.get(), tempFile);
                ProcessResult result = processExecutor.execute(command);
                return parseOutput(result, tempFile);
            } catch (IOException | InterruptedException e) {
                // Fall through to embedded engine
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally {
                if (tempFile != null) deleteQuietly(tempFile);
            }
        }

        // Fallback
        ValidationResult embedded = PythonSyntaxEngine.getInstance().validate(safeContent);
        String prefix = binary.isEmpty() ? binaryNotFoundMessage() + " " : "";
        return embedded.isValid()
                ? ValidationResult.valid(prefix + embedded.getMessage())
                : ValidationResult.invalid(prefix + embedded.getMessage(), embedded.getErrors());
    }

    @Override
    protected String getFileExtension() { return ".py"; }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        // Example: python3 -m py_compile <file>
        return List.of(binaryPath, "-m", "py_compile", tempFile.toString());
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        String combined = result.stdout() + "\n" + result.stderr();
        return PythonOutputParser.parse(combined);
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "Python syntax checker is not available — falling back to built-in engine. "
                + "Install Python 3 and ensure 'python3' is on your PATH.";
    }

    private static void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
    }
}
```

### Step 5: Register in `ValidatorFactory`

**File:** `src/main/java/com/neel/syntaxvalidation/validator/ValidatorFactory.java`

Add an import and register the new validator in the `createDefaults()` method:

```java
// Add import:
import com.neel.syntaxvalidation.validator.python.PythonValidator;

// In createDefaults(), add:
validators.put(Language.PYTHON, new PythonValidator());
```

### Step 6: Write Tests

**New file:** `src/test/java/com/neel/syntaxvalidation/validator/python/PythonValidatorTest.java`

Follow the existing test patterns:

```java
package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PythonValidatorTest {

    private PythonValidator validator;
    private BinaryResolver binaryResolver;
    private ProcessExecutor processExecutor;

    @BeforeEach
    void setUp() {
        binaryResolver = new BinaryResolver();
        processExecutor = new ProcessExecutor();
        validator = new PythonValidator(null, binaryResolver, processExecutor);
    }

    @Test
    void shouldReturnCorrectLanguage() {
        assertThat(validator.getLanguage()).isEqualTo(Language.PYTHON);
    }

    @Test
    void shouldValidateEmptyContentAsValid() {
        ValidationResult result = validator.validate("");
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldFallbackToEmbeddedEngine() {
        // When no binary is available, embedded engine should run
        ValidationResult result = validator.validate("print('hello')");
        assertThat(result).isNotNull();
    }

    @Test
    void shouldDetectSyntaxError() {
        // Missing closing parenthesis
        ValidationResult result = validator.validate("print('hello'");
        assertThat(result.isValid()).isFalse();
    }
}
```

### Step 7: Update Integration Tests

**File:** `src/test/java/com/neel/syntaxvalidation/SyntaxValidationLibraryTest.java`

Add a test that validates through the full facade:

```java
@Test
void shouldValidatePythonModification() {
    // Setup: create a temp .py file, build a ModificationRequest
    // Call syntaxValidationLibrary.validate(request)
    // Assert on the ValidationResult
}
```

### Step 8: Verification Checklist

After implementing a new language, verify:

- [ ] `Language` enum has the new constant with correct extensions
- [ ] `Language.fromExtension("ext")` resolves to the new language
- [ ] Embedded engine validates valid code as valid
- [ ] Embedded engine detects common syntax errors
- [ ] External binary path resolution works (sys prop, env var, PATH)
- [ ] Output parser correctly converts tool output to `ValidationError` list
- [ ] Fallback works when external binary is missing
- [ ] `ValidatorFactory` returns the new validator for the language
- [ ] `SyntaxValidationLibrary.validate()` works end-to-end with `.ext` files
- [ ] All existing tests still pass (`./gradlew test`)

---

## 7. Handling Project Conversions

When adapting or transforming code between languages/frameworks to use this library, follow these procedures:

### 7.1 Converting an Existing Validation System to Use This Library

**Scenario:** You have a project that validates syntax using custom code and want to adopt this library.

**Steps:**

1. **Identify your validation targets:** Map your file types to the `Language` enum values.

2. **Replace direct validator calls with the facade:**
   ```java
   // Before (custom code):
   boolean isValid = MyCustomValidator.checkSyntax(fileContent, ".js");

   // After (this library):
   SyntaxValidationLibrary lib = new SyntaxValidationLibrary();
   ModificationRequest request = ModificationRequest.builder()
       .filePath("file.js")
       .fromLine(1).toLine(oldLines.size())
       .replacement(newContent)
       .build();
   ValidationResult result = lib.validate(request);
   boolean isValid = result.isValid();
   ```

3. **Map error reporting:** Replace your custom error types with `ValidationError`:
   ```java
   // Convert your errors
   List<ValidationError> errors = result.getErrors();
   for (ValidationError error : errors) {
       int line = error.getLine();        // 1-based
       int column = error.getColumn();    // 1-based, or -1
       String message = error.getMessage();
       String source = error.getSourceOutput(); // raw tool output
   }
   ```

4. **Preserve file-on-disk safety:** The library never writes to disk. If your system modified files in-place, refactor to use the in-memory validation flow.

### 7.2 Adapting Code for a Different Language/Framework

**Scenario:** You're migrating a codebase from one language to another (e.g., JavaScript → TypeScript) and need to validate intermediate states.

**Steps:**

1. **Validate the target language:** If the target language is supported, use the library directly. If not, follow [Section 6](#6-adding-support-for-a-new-language) to add it.

2. **Handle mixed-language files:** For files containing multiple languages (e.g., HTML with embedded JS/CSS), use `validateMixedContent()`:
   ```java
   ValidationResult result = lib.validateMixedContent(htmlSource);
   ```

3. **Line-range modification during migration:** When making incremental changes to migrate code:
   ```java
   // Validate only the changed section
   ModificationRequest request = ModificationRequest.builder()
       .filePath("src/legacy-module.js")
       .fromLine(45).toLine(60)          // just the migrated section
       .replacement(newTypescriptCode)
       .build();
   ValidationResult result = lib.validate(request);
   ```

4. **Invalidate cache after external changes:** If files are modified outside the library (e.g., by a migration tool):
   ```java
   lib.invalidateCache(Path.of("src/migrated-file.ts"));
   // Next validate() call will reload from disk
   ```

### 7.3 Building a Conversion Pipeline

For automated code conversion workflows:

```java
public class ConversionValidator {

    private final SyntaxValidationLibrary lib = new SyntaxValidationLibrary();

    /**
     * Validates a batch of modifications across multiple files.
     * Returns a map of file → validation result.
     */
    public Map<String, ValidationResult> validateBatch(
            List<ModificationRequest> requests) {
        
        Map<String, ValidationResult> results = new LinkedHashMap<>();
        for (ModificationRequest request : requests) {
            ValidationResult result = lib.validate(request);
            results.put(request.getFilePath(), result);
            
            if (result.isValid()) {
                // Safe to apply the modification
                applyModification(request);
            } else {
                // Log errors, skip this modification, or attempt repair
                logErrors(request, result.getErrors());
            }
        }
        return results;
    }

    /**
     * Two-phase validation: validate the proposed change, then validate
     * the entire file after application.
     */
    public ValidationResult twoPhaseValidate(
            Path filePath, int fromLine, int toLine, String replacement) {
        
        // Phase 1: Validate the modification in isolation
        ModificationRequest request = ModificationRequest.builder()
            .filePath(filePath.toString())
            .fromLine(fromLine).toLine(toLine)
            .replacement(replacement)
            .build();
        
        ValidationResult phase1 = lib.validate(request);
        if (!phase1.isValid()) return phase1;

        // Phase 2: Apply and re-validate the whole file
        // (use FileCache to get current lines, apply modification,
        //  then validate the full content)
        return phase1; // or perform additional checks
    }
}
```

### 7.4 Integrating with LLM Coding Agents

The library is designed for LLM agent integration:

```java
// Agent receives a proposed code edit from an LLM
public class LLMEditValidator {

    private final SyntaxValidationLibrary lib = new SyntaxValidationLibrary();

    /**
     * Validates an LLM-proposed edit before applying it.
     * Returns an error message suitable for feeding back to the LLM.
     */
    public Optional<String> validateBeforeApply(
            String filePath, int startLine, int endLine, String newCode) {
        
        ModificationRequest request = ModificationRequest.builder()
            .filePath(filePath)
            .fromLine(startLine).toLine(endLine)
            .replacement(newCode)
            .build();

        ValidationResult result = lib.validate(request);
        
        if (result.isValid()) {
            return Optional.empty(); // safe to apply
        }

        // Build feedback for the LLM
        StringBuilder feedback = new StringBuilder();
        feedback.append("Syntax validation failed: ").append(result.getMessage()).append("\n");
        for (ValidationError error : result.getErrors()) {
            feedback.append("  Line ").append(error.getLine());
            if (error.getColumn() > 0) {
                feedback.append(", Column ").append(error.getColumn());
            }
            feedback.append(": ").append(error.getMessage()).append("\n");
        }
        return Optional.of(feedback.toString());
    }
}
```

---

## 8. Testing & Quality

### 8.1 Test Structure

Tests mirror the main source structure. Each validator has dedicated tests covering:

- **Valid content:** Empty strings, well-formed code, edge cases
- **Invalid content:** Missing brackets, unclosed tags, malformed declarations
- **Fallback behavior:** When external binary is unavailable
- **Output parsing:** Both JSON and regex-based parser tests
- **Boundary conditions:** Null inputs, extremely large files, mixed line endings

### 8.2 Running Tests

```bash
# Full suite
./gradlew test

# With coverage (if jacoco plugin is added)
./gradlew test jacocoTestReport

# Specific validator tests
./gradlew test --tests "*.validator.css.*"
./gradlew test --tests "*.validator.html.*"
./gradlew test --tests "*.validator.javascript.*"
./gradlew test --tests "*.validator.mixed.*"
```

### 8.3 Writing Tests for New Validators

Follow these conventions:

1. Use `@TempDir` for file system operations
2. Use `BinaryResolver` and `ProcessExecutor` as injectable dependencies (constructor injection)
3. Test both binary-available and binary-unavailable paths
4. Use AssertJ fluent assertions (`assertThat`)
5. Test `ValidationResult.isValid()`, `.getMessage()`, `.getErrors()`, and `.hasErrors()`

### 8.4 Test Dependencies

```groovy
// From build.gradle
testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
testImplementation 'org.assertj:assertj-core:3.26.3'
```

---

## 9. External Binary Configuration

### 9.1 Binary Resolution Order

`BinaryResolver` searches in this priority:

| Priority | Method | Example (for `stylelint`) |
|----------|--------|---------------------------|
| 1 | System property | `-Dsyntaxvalidation.bin.stylelint=/path/to/stylelint` |
| 2 | Environment variable | `SYNTAX_VALIDATION_STYLELINT=/path/to/stylelint` |
| 3 | System PATH | `stylelint` executable on `PATH` |

### 9.2 Supported Binaries

| Language | Binary Key | Tool | Install |
|----------|-----------|------|---------|
| JavaScript | `node` | Node.js | [nodejs.org](https://nodejs.org) |
| HTML | `vnu` | Nu Html Checker | [github.com/validator/validator](https://github.com/validator/validator/releases) |
| CSS | `stylelint` | Stylelint | `npm install -g stylelint stylelint-config-standard` |

### 9.3 Custom Binary Paths via Factory

```java
// Explicit binary paths — no PATH lookup needed
ValidatorFactory factory = new ValidatorFactory(Map.of(
    Language.JAVASCRIPT, new JavaScriptValidator("C:\\tools\\node.exe"),
    Language.HTML, new HtmlValidator("C:\\tools\\vnu.jar"),
    Language.CSS, new CssValidator("C:\\tools\\stylelint.cmd")
));
SyntaxValidationLibrary lib = new SyntaxValidationLibrary(factory);
```

### 9.4 Timeout Configuration

External process timeouts are set per-validator:
- **JavaScript (node):** 30 seconds
- **HTML (vnu.jar):** 120 seconds
- **CSS (stylelint):** 60 seconds

These are currently hardcoded as `static final` constants in each validator class.

---

## 10. Troubleshooting

### Common Issues

| Problem | Cause | Solution |
|---------|-------|----------|
| "No validator is registered for language" | Missing enum entry or factory registration | Add language to `Language` enum and register in `ValidatorFactory` |
| "Unable to detect a supported language" | File extension not mapped | Check `Language.fromExtension()` — ensure extension is listed |
| Fallback engine always used | External binary not on PATH | Set env var or system property per Section 9.1 |
| Tests fail with `IOException` | Temp file issues | Ensure `@TempDir` is used; check disk permissions |
| `InterruptedException` swallowed | Process timeout | Check if timeout is too short; increase in validator |

### Debugging Tips

1. **Enable verbose logging:** Add `--info` or `--debug` to Gradle commands
2. **Check binary resolution:** Temporarily add `System.out.println` in `BinaryResolver.resolve()`
3. **Inspect temp files:** Modify `AbstractLanguageValidator` to not delete temp files during debugging
4. **Test embedded engines directly:** Bypass the external binary path by calling `*SyntaxEngine.getInstance().validate(content)` directly

---

## Appendix: File Extension → Language Mapping

| Extension | Language | Validator |
|-----------|----------|-----------|
| `.js` | JAVASCRIPT | `JavaScriptValidator` |
| `.mjs` | JAVASCRIPT | `JavaScriptValidator` |
| `.cjs` | JAVASCRIPT | `JavaScriptValidator` |
| `.html` | HTML | `HtmlValidator` |
| `.htm` | HTML | `HtmlValidator` |
| `.css` | CSS | `CssValidator` |
| `.ts` | TYPESCRIPT | *(placeholder — no validator yet)* |
| `.py` | PYTHON | *(placeholder — no validator yet)* |
| `.java` | JAVA | *(placeholder — no validator yet)* |
