# Agent Guide — Syntax Validation Library (Java)

> **Project root:** `F:\code-helper-mcp-library-java`
> **Language:** Java 21+ | **Build:** Gradle (Groovy DSL) | **Testing:** JUnit 5 + AssertJ

---

## 1 · Project Overview

A **pluggable Java library** that validates the syntactic correctness of proposed source-code modifications *before* they are applied. Designed for integration with AI coding agents, code editors, and CI pipelines.

The library takes an original source file plus a set of proposed modifications (line-range replacements), produces a modified snapshot in memory, then validates the syntax of the result. External tool invocations (`node --check`, `python -c compile(...)`, `php -l`, `stylelint`, `vnu`) are optional—**every language also has a zero-dependency, pure-Java fallback engine** for fast structural validation.

### Supported Languages (5)

| Language | Enum Value | Extensions | Validator Class | Pure-Java Engine |
|---|---|---|---|---|
| JavaScript | `JAVASCRIPT` | `.js`, `.mjs`, `.cjs`, `.jsx` | `JavaScriptValidator` | `JavaScriptSyntaxEngine` |
| CSS | `CSS` | `.css` | `CssValidator` | `CssSyntaxEngine` |
| HTML | `HTML` | `.html`, `.htm`, `.xhtml` | `HtmlValidator` | `HtmlSyntaxEngine` |
| PHP | `PHP` | `.php`, `.phtml`, `.phps` | `PhpValidator` | `PhpSyntaxEngine` |
| Mixed Content | `MIXED` | `.html`, `.htm`, `.php` (when mixed content detected) | `MixedContentValidator` | `MixedContentSyntaxEngine` |

### Core Design Principles

1. **External-tool-first, pure-Java fallback** — every validator attempts the real external tool first; if the binary is missing it silently falls back to the embedded engine.
2. **Immutable, structured results** — `ValidationResult` is a record-like value object with `isValid`, `message`, and `List<ValidationError>`. Each error carries 1-based line, column, message, and optional raw tool output.
3. **Strategy pattern** — `LanguageValidator` (interface) → `AbstractLanguageValidator` (base) → concrete per-language validators. `ValidatorFactory` resolves the correct validator for a `Language` enum.
4. **Modification pipeline** — `ModificationApplier` applies line-range replacements to produce a candidate snapshot without touching disk. The library validates this snapshot.
5. **In-memory file cache** — `FileCache` holds `FileCacheEntry` snapshots keyed by `Path`, avoiding repeated disk reads during batch validation.
6. **Stateless engines** — all syntax engines (`CssSyntaxEngine`, `HtmlSyntaxEngine`, `JavaScriptSyntaxEngine`, `PhpSyntaxEngine`, `MixedContentSyntaxEngine`) are stateless singletons, safe for concurrent use.

---

## 2 · Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21+ |
| Build tool | Gradle (Groovy DSL) |
| Testing | JUnit 5 + AssertJ |
| External tools (optional) | `node`, `python`, `php`, `stylelint`, `vnu.jar` |
| Gradle cache | `org.gradle.caching=true`, parallel execution enabled |

**build.gradle** — standard `java-library` plugin, group `com.neel`, version `1.0.0`, description: *A pluggable Java library that validates the syntactic correctness of proposed source-code modifications before they are applied.*

---

## 3 · Project Structure

```
F:\code-helper-mcp-library-java\
├── build.gradle
├── gradle.properties
├── README.md
├── agent.md                              ← this file
├── gradle/                               ← Gradle wrapper
└── src/
    ├── main/java/com/neel/syntaxvalidation/
    │   ├── SyntaxValidationLibrary.java      ← MAIN FACADE / ENTRY POINT
    │   │
    │   ├── model/
    │   │   ├── Language.java                  ← enum: JAVASCRIPT, CSS, HTML, PHP, MIXED
    │   │   ├── ModificationRequest.java       ← line-range replacement descriptor
    │   │   ├── ValidationResult.java          ← immutable result (valid + message + errors)
    │   │   └── ValidationError.java           ← single diagnostic (line, col, message, toolOutput)
    │   │
    │   ├── validator/
    │   │   ├── LanguageValidator.java          ← strategy interface
    │   │   ├── AbstractLanguageValidator.java  ← base class: external-tool-first + fallback
    │   │   ├── ValidatorFactory.java           ← resolves Language → LanguageValidator
    │   │   │
    │   │   ├── javascript/
    │   │   │   ├── JavaScriptValidator.java
    │   │   │   ├── JavaScriptSyntaxEngine.java
    │   │   │   ├── JavaScriptSyntaxTokenizer.java
    │   │   │   ├── JsToken.java
    │   │   │   └── JsTokenType.java
    │   │   │
    │   │   ├── css/
    │   │   │   ├── CssValidator.java
    │   │   │   └── CssSyntaxEngine.java
    │   │   │
    │   │   ├── html/
    │   │   │   ├── HtmlValidator.java
    │   │   │   └── HtmlSyntaxEngine.java
    │   │   │
    │   │   ├── php/
    │   │   │   ├── PhpValidator.java
    │   │   │   ├── PhpSyntaxEngine.java
    │   │   │   └── PhpOutputParser.java
    │   │   │
    │   │   └── mixed/
    │   │       ├── MixedContentValidator.java
    │   │       ├── MixedContentSyntaxEngine.java
    │   │       ├── HtmlContentExtractor.java
    │   │       └── ExtractedBlock.java
    │   │
    │   ├── process/
    │   │   ├── ProcessExecutor.java             ← runs external CLI tools
    │   │   └── ProcessResult.java               ← exit code + stdout + stderr capture
    │   │
    │   ├── binary/
    │   │   └── BinaryResolver.java              ← resolves external tool paths via env/config
    │   │
    │   ├── cache/
    │   │   ├── FileCache.java                   ← in-memory file content cache
    │   │   └── FileCacheEntry.java              ← snapshot: lines + content + hash + timestamp
    │   │
    │   └── modification/
    │       └── ModificationApplier.java         ← applies line-range replacements to source
    │
    └── test/java/com/neel/syntaxvalidation/
        ├── SyntaxValidationLibraryTest.java
        ├── model/
        │   ├── LanguageTest.java
        │   ├── ModificationRequestTest.java
        │   ├── ValidationResultTest.java
        │   └── ValidationErrorTest.java
        ├── validator/
        │   ├── AbstractLanguageValidatorTest.java
        │   ├── ValidatorFactoryTest.java
        │   ├── javascript/
        │   │   ├── JavaScriptValidatorTest.java
        │   │   ├── JavaScriptSyntaxEngineTest.java
        │   │   ├── JavaScriptSyntaxTokenizerTest.java
        │   │   └── NodeCheckOutputParserTest.java
        │   ├── css/
        │   │   ├── CssValidatorTest.java
        │   │   ├── CssSyntaxEngineTest.java
        │   │   └── StylelintOutputParserTest.java
        │   ├── html/
        │   │   ├── HtmlValidatorTest.java
        │   │   ├── HtmlSyntaxEngineTest.java
        │   │   └── VnuOutputParserTest.java
        │   ├── php/
        │   │   ├── PhpValidatorTest.java
        │   │   ├── PhpSyntaxEngineTest.java
        │   │   └── PhpOutputParserTest.java
        │   └── mixed/
        │       ├── MixedContentValidatorTest.java
        │       ├── MixedContentSyntaxEngineTest.java
        │       ├── MixedContentIntegrationTest.java
        │       ├── PhpMixedContentIntegrationTest.java
        │       ├── HtmlContentExtractorTest.java
        │       └── ExtractedBlockTest.java
        ├── process/
        │   └── ProcessExecutorTest.java
        ├── binary/
        │   └── BinaryResolverTest.java
        ├── cache/
        │   └── FileCacheTest.java
        └── modification/
            └── ModificationApplierTest.java
```

**Total: 30 test files**

---

## 4 · Key Source Files

### 4.1 Main Facade

| File | Summary |
|---|---|
| `SyntaxValidationLibrary.java` | The primary entry point. Exposes `validateModification(path, original, modifications)` and `validateSource(language, source)`. Orchestrates `ValidatorFactory`, `ModificationApplier`, and `FileCache`. |

### 4.2 Model Layer

| File | Summary |
|---|---|
| `Language.java` | Enum with values: `JAVASCRIPT`, `CSS`, `HTML`, `PHP`, `MIXED`. Each value carries its file extensions and a display name. Provides `fromExtension(String)` for filename→language mapping. |
| `ModificationRequest.java` | Immutable descriptor: `startLine`, `endLine`, `newContent`, optional `description`. All indices are 1-based and inclusive. |
| `ValidationResult.java` | Immutable outcome: `boolean valid`, `String message`, `List<ValidationError> errors`. Provides factory methods `valid(msg)` and `invalid(msg, errors)`. |
| `ValidationError.java` | Immutable diagnostic: `int line`, `int column`, `String message`, `String toolOutput` (nullable). |

### 4.3 Validator Layer

| File | Summary |
|---|---|
| `LanguageValidator.java` | Interface: `getLanguage()`, `validateSource(String)`, `validateFile(Path)` |
| `AbstractLanguageValidator.java` | Base class implementing the external-tool-first, pure-Java-fallback pattern. Uses `BinaryResolver` to check for external tool, `ProcessExecutor` to run it, and delegates to a `*SyntaxEngine` for the fallback. |
| `ValidatorFactory.java` | Resolves `Language → LanguageValidator`. Maintains a map of all registered validators (JavaScript, CSS, HTML, PHP, Mixed). |

### 4.4 JavaScript Validator

| File | Summary |
|---|---|
| `JavaScriptValidator.java` | Extends `AbstractLanguageValidator`. External tool: `node --check`. |
| `JavaScriptSyntaxEngine.java` | Pure-Java engine. Uses `JavaScriptSyntaxTokenizer` for lexing, then performs bracket/balance checks and statement-level grammar validation. |
| `JavaScriptSyntaxTokenizer.java` | Hand-written lexer. Emits `JsToken` list. Handles: strings (single/double/template), regex, numbers, identifiers, operators, comments (single/multi-line). |
| `JsToken.java` | Immutable record: `JsTokenType type`, `String text`, `int line`, `int column`. |
| `JsTokenType.java` | Enum: `KEYWORD`, `IDENTIFIER`, `NUMBER`, `STRING`, `REGEX`, `OPERATOR`, `PUNCTUATION`, `COMMENT`, `TEMPLATE_*`. |

### 4.5 CSS Validator

| File | Summary |
|---|---|
| `CssValidator.java` | Extends `AbstractLanguageValidator`. External tool: `stylelint`. |
| `CssSyntaxEngine.java` | Pure-Java engine. Tokeniser + bracket/balance checks + declaration validation (`property: value;`), at-rule validation, selector validation. |

### 4.6 HTML Validator

| File | Summary |
|---|---|
| `HtmlValidator.java` | Extends `AbstractLanguageValidator`. External tool: `vnu.jar` (Nu Html Checker). |
| `HtmlSyntaxEngine.java` | Pure-Java engine. Tag matching, attribute validation, void-element handling, nesting-depth checks. |

### 4.7 PHP Validator

| File | Summary |
|---|---|
| `PhpValidator.java` | Extends `AbstractLanguageValidator`. External tool: `php -l`. |
| `PhpSyntaxEngine.java` | Pure-Java engine. Three-phase validation: (1) tokeniser with full PHP 8.3+ support, (2) bracket/brace/parenthesis balance checks, (3) grammar-level validation of declarations, function signatures, control structures, use statements, and common error patterns. Covers: namespaces, traits, interfaces, enums, generators, constructor promotion, named arguments, match expressions, union/intersection/DNF types, readonly properties/classes, PHP 8.0 attributes. |
| `PhpOutputParser.java` | Parses `php -l` output into `ValidationResult`. Handles: `Parse error`, `Fatal error`, `Warning`, `Deprecated` messages. Supports both PHP 7.x and 8.x error formats. |

### 4.8 Mixed Content Validator

| File | Summary |
|---|---|
| `MixedContentValidator.java` | Extends `AbstractLanguageValidator`. Validates HTML/PHP documents containing embedded CSS, JavaScript, and PHP blocks. |
| `MixedContentSyntaxEngine.java` | Orchestrates validation across four dimensions: (1) HTML structure via `HtmlSyntaxEngine`, (2) `<style>` blocks via `CssSyntaxEngine`, (3) `<script>` blocks via `JavaScriptSyntaxEngine`, (4) `<?php … ?>` blocks via `PhpSyntaxEngine`. Remaps embedded-content error line numbers to original document positions. |
| `HtmlContentExtractor.java` | Regex-based extractor that locates `<style>`, `<script>`, and `<?php … ?>` blocks in HTML/PHP documents. Tracks original line numbers for error remapping. Handles: multi-line opening tags, empty blocks, legacy HTML comment wrappers, case-insensitive tags, PHP short open tags, XML processing instruction exclusion. |
| `ExtractedBlock.java` | Immutable record: `Language language`, `String content`, `int startLine`, `int contentStartLine`, `int contentStartColumn`, `int contentEndLine`. Provides `mapToOriginalLine(int)` for line number remapping. |

### 4.9 Process / Binary Layer

| File | Summary |
|---|---|
| `ProcessExecutor.java` | Runs external CLI commands with timeout. Captures `exitCode`, `stdout`, `stderr` into `ProcessResult`. |
| `ProcessResult.java` | Immutable record: `int exitCode`, `String stdout`, `String stderr`. |
| `BinaryResolver.java` | Resolves external tool paths via environment variables, PATH lookup, and common installation directories. Returns `Optional<Path>`. |

### 4.10 Cache Layer

| File | Summary |
|---|---|
| `FileCache.java` | In-memory cache keyed by `Path`. Stores `FileCacheEntry` snapshots. Thread-safe. |
| `FileCacheEntry.java` | Immutable snapshot: `List<String> lines`, `String content`, `String hash`, `long timestamp`. |

### 4.11 Modification Layer

| File | Summary |
|---|---|
| `ModificationApplier.java` | Applies a list of `ModificationRequest` to original source lines. Returns the modified source as a `String`. Does not write to disk. |

---

## 5 · How the Validation Pipeline Works

```
1. User calls SyntaxValidationLibrary.validateModification(file, modifications)
2. Library reads the original file (or uses FileCache)
3. ModificationApplier produces a modified source snapshot
4. Library detects language from file extension via Language.fromExtension()
5. ValidatorFactory resolves the correct LanguageValidator
6. Validator checks for external binary (BinaryResolver)
   a. If found: runs external tool (ProcessExecutor) and parses output
   b. If not found: uses pure-Java fallback engine (*SyntaxEngine)
7. Returns ValidationResult with isValid(), message, and errors
```

For mixed-content files (HTML/PHP with embedded CSS/JS/PHP):
```
1. MixedContentSyntaxEngine extracts embedded blocks via HtmlContentExtractor
2. Validates HTML structure via HtmlSyntaxEngine
3. Validates each <style> block via CssSyntaxEngine
4. Validates each <script> block via JavaScriptSyntaxEngine
5. Validates each <?php … ?> block via PhpSyntaxEngine
6. Merges all results with line-number remapping to original document positions
```

---

## 6 · Pure-Java Engine Capabilities

### JavaScript (`JavaScriptSyntaxEngine`)
- **Tokeniser**: Hand-written lexer supporting single/double/template strings, regex, numbers (hex, octal, binary, bigint), identifiers, operators, comments.
- **Balance checks**: Parentheses, braces, brackets, template literal nesting.
- **Grammar checks**: Function declarations, arrow functions, class syntax, control structures, variable declarations, module syntax (import/export), optional chaining, nullish coalescing.

### CSS (`CssSyntaxEngine`)
- **Tokeniser**: Property-value pairs, at-rules, selectors, comments.
- **Balance checks**: Braces, parentheses, brackets.
- **Declaration checks**: `property: value;` format, at-rule syntax, selector validity.

### HTML (`HtmlSyntaxEngine`)
- **Tag matching**: Opening/closing tag pairing, void elements (`<br>`, `<img>`, etc.).
- **Attribute validation**: Quoted attributes, duplicate detection.
- **Nesting checks**: Maximum depth validation.

### PHP (`PhpSyntaxEngine`)
- **Tokeniser**: Full PHP 8.3+ support — keywords, identifiers, variables (`$var`), numbers (hex, binary, octal, scientific, underscores), strings (single/double-quoted, heredoc, nowdoc), operators (including `??=`, `<=>`, `**`), PHP tags (`<?php`, `<?=`, `?>`), attributes (`#[...]`), comments (`//`, `#`, `/* */`).
- **Balance checks**: Parentheses, braces, brackets.
- **Grammar checks**: Class/interface/trait/enum declarations, function signatures (union/intersection/DNF types, nullable, never return), parameter lists, control structures (if/elseif/else, for, foreach, while, do-while, switch, match), namespace/use statements, generator patterns (yield, yield from), constructor promotion, readonly properties/classes, enum backed types.

### Mixed Content (`MixedContentSyntaxEngine`)
- **Extraction**: Locates `<style>`, `<script>`, and `<?php … ?>` blocks via regex.
- **Validation**: Delegates to the appropriate sub-engine for each block type.
- **Line remapping**: Maps error line numbers from extracted blocks back to original document positions.
- **Edge cases**: Multi-line opening tags, empty blocks, legacy HTML comment wrappers, case-insensitive tags, PHP short open tags (excluding `<?xml`).

---

## 7 · Model Invariants

### `Language` enum
- Each value has `extensions` (Set<String>), `displayName` (String).
- `fromExtension(String)` returns `Optional<Language>`, case-insensitive.
- Extension matching: `.js`/`.mjs`/`.cjs`/`.jsx` → JAVASCRIPT; `.css` → CSS; `.html`/`.htm`/`.xhtml` → HTML; `.php`/`.phtml`/`.phps` → PHP.

### `ModificationRequest`
- `startLine` and `endLine` are 1-based, inclusive.
- `endLine >= startLine` always.
- `newContent` may be empty (deletion) but never null.

### `ValidationResult`
- `valid == true` implies `errors` is empty.
- `valid == false` implies `errors` is non-empty.
- `message` is always non-null.

### `ValidationError`
- `line` and `column` are 1-based; `-1` means "unknown".
- `toolOutput` is nullable (only present when validation used an external tool).

---

## 8 · Key Implementation Patterns

### External-Tool-First with Pure-Java Fallback
Every `AbstractLanguageValidator` subclass follows this pattern:
```java
// 1. Check for external binary
Optional<Path> binary = binaryResolver.resolve("node");
if (binary.isPresent()) {
    // 2a. Run external tool
    ProcessResult result = processExecutor.execute(binary.get(), "--check", tempFile);
    // 2b. Parse output
    return outputParser.parse(result.stderr());
}
// 3. Fallback to pure-Java engine
return syntaxEngine.validate(source);
```

### Validator Registration (ValidatorFactory)
```java
validators.put(Language.JAVASCRIPT, new JavaScriptValidator());
validators.put(Language.CSS, new CssValidator());
validators.put(Language.HTML, new HtmlValidator());
validators.put(Language.PHP, new PhpValidator());
validators.put(Language.MIXED, new MixedContentValidator());
```

### Singleton Syntax Engines
All syntax engines use the singleton pattern:
```java
private static final PhpSyntaxEngine INSTANCE = new PhpSyntaxEngine();
public static PhpSyntaxEngine getInstance() { return INSTANCE; }
```

### Line Number Remapping (Mixed Content)
```java
// In ExtractedBlock:
public int mapToOriginalLine(int contentLocalLine) {
    return contentStartLine + (contentLocalLine - 1);
}

// In MixedContentSyntaxEngine:
private ValidationResult remapLineNumbers(ValidationResult result, ExtractedBlock block) {
    List<ValidationError> remapped = result.getErrors().stream()
        .map(error -> new ValidationError(
            block.mapToOriginalLine(error.getLine()),
            error.getColumn(),
            prependLanguageContext(error, block),
            error.getToolOutput()
        ))
        .collect(Collectors.toList());
    return ValidationResult.invalid(result.getMessage(), remapped);
}
```

---

## 9 · Test Coverage (30 Test Files)

### Model Tests (4)
| Test | Covers |
|---|---|
| `LanguageTest` | Enum values, extension mapping, case insensitivity |
| `ModificationRequestTest` | Construction, validation, equality |
| `ValidationResultTest` | Factory methods, validity, error lists |
| `ValidationErrorTest` | Construction, field access, equality |

### Validator Tests (4)
| Test | Covers |
|---|---|
| `AbstractLanguageValidatorTest` | Base class contract, fallback behavior |
| `ValidatorFactoryTest` | Language→validator resolution, supported languages |
| `JavaScriptValidatorTest` | Integration: valid/invalid JS, edge cases |
| `CssValidatorTest` | Integration: valid/invalid CSS, edge cases |

### JavaScript Engine Tests (4)
| Test | Covers |
|---|---|
| `JavaScriptSyntaxEngineTest` | Balance checks, grammar validation, edge cases |
| `JavaScriptSyntaxTokenizerTest` | Token types, string handling, regex, numbers |
| `NodeCheckOutputParserTest` | `node --check` stderr parsing |

### CSS Engine Tests (3)
| Test | Covers |
|---|---|
| `CssSyntaxEngineTest` | Declarations, at-rules, selectors, balance |
| `StylelintOutputParserTest` | `stylelint` JSON output parsing |

### HTML Engine Tests (3)
| Test | Covers |
|---|---|
| `HtmlValidatorTest` | Integration: valid/invalid HTML, edge cases |
| `HtmlSyntaxEngineTest` | Tag matching, attributes, void elements, nesting |
| `VnuOutputParserTest` | `vnu.jar` output parsing |

### PHP Engine Tests (3)
| Test | Covers |
|---|---|
| `PhpValidatorTest` | Integration: valid/invalid PHP, modern PHP 8.x features, extension detection, factory integration |
| `PhpSyntaxEngineTest` | Tokenisation, balance checks, grammar validation for all PHP constructs (classes, interfaces, traits, enums, generators, closures, arrow functions, attributes, match expressions, union types, readonly, constructor promotion) |
| `PhpOutputParserTest` | `php -l` output parsing: parse errors, fatal errors, warnings, deprecated notices, PHP 7.x/8.x formats |

### Mixed Content Tests (6)
| Test | Covers |
|---|---|
| `MixedContentValidatorTest` | Integration: HTML with embedded CSS/JS/PHP |
| `MixedContentSyntaxEngineTest` | Orchestration, line remapping, error merging |
| `MixedContentIntegrationTest` | End-to-end mixed content validation |
| `PhpMixedContentIntegrationTest` | PHP-specific mixed content scenarios |
| `HtmlContentExtractorTest` | Block extraction, edge cases, line tracking |
| `ExtractedBlockTest` | Record construction, line remapping |

### Infrastructure Tests (4)
| Test | Covers |
|---|---|
| `ProcessExecutorTest` | Command execution, timeout, output capture |
| `BinaryResolverTest` | Path resolution, environment variables |
| `FileCacheTest` | Cache operations, invalidation, thread safety |
| `ModificationApplierTest` | Line-range replacements, multi-modification |

### Library Integration Tests (2)
| Test | Covers |
|---|---|
| `SyntaxValidationLibraryTest` | End-to-end: modification validation, source validation, language detection |

---

## 10 · Common Modification Scenarios

### Adding a New Language Validator

1. Add enum value to `Language.java` with extensions and display name.
2. Create `NewLangValidator extends AbstractLanguageValidator` in `validator.newlang/`.
3. Create `NewLangSyntaxEngine` as the pure-Java fallback.
4. (Optional) Create `NewLangOutputParser` if an external tool produces parseable output.
5. Register in `ValidatorFactory.java`.
6. Write tests: `NewLangValidatorTest`, `NewLangSyntaxEngineTest`, (optional) `NewLangOutputParserTest`.

### Adding a New Pure-Java Engine Rule

1. Locate the relevant `*SyntaxEngine.java`.
2. Add the validation rule in the appropriate phase (tokeniser, balance, grammar).
3. Add test cases in the corresponding `*SyntaxEngineTest.java`.

### Adding Mixed Content Support for a New Language

1. Add extraction patterns in `HtmlContentExtractor.java` for the new tag/block type.
2. Add a new case in `MixedContentSyntaxEngine.validate()` to validate extracted blocks.
3. Add tests in `HtmlContentExtractorTest.java` and `MixedContentSyntaxEngineTest.java`.

---

## 11 · Important Notes

- **No `Language.JAVA` support** — the library does not validate Java source files.
- **External tools are optional** — the library works fully offline with pure-Java engines.
- **File cache is in-memory** — not persisted across JVM restarts.
- **ModificationApplier operates in-memory** — never writes to disk.
- **All line numbers are 1-based** throughout the API.
- **Column numbers use -1** to indicate "unknown" when the engine cannot determine the column.
- **`MixedContentSyntaxEngine` always validates all four dimensions** (HTML, CSS, JS, PHP) regardless of which are present; missing dimensions simply produce no errors.
- **PHP engine supports PHP 8.0+ attributes** (`#[Route('/api')]`), **readonly classes**, **match expressions**, **union/intersection/DNF types**, **constructor promotion**, **named arguments**, and **enums with backed types**.
- **Mixed content line remapping** ensures error line numbers in embedded CSS/JS/PHP blocks refer to positions in the original HTML/PHP document, not the extracted fragment.
