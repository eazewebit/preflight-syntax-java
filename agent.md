# Agent Guide — Syntax Validation Library (Java)

> **Project root:** `F:\code-helper-mcp-library-java`
> **Language:** Java 25 | **Build:** Gradle (Groovy DSL) | **Testing:** JUnit 5 + AssertJ

---

## 1 · Project Overview

A **pluggable Java library** that validates the syntactic correctness of proposed source-code modifications *before* they are applied. Designed for integration with AI coding agents, code editors, and CI pipelines.

The library takes an original source file plus a set of proposed modifications (line-range replacements), produces a modified snapshot in memory, then validates the syntax of the result. External tool invocations (`node --check`, `python -c compile(...)`, `php -l`, `stylelint`, `vnu`) are optional—**every language also has a zero-dependency, pure-Java fallback engine** for fast structural validation.

### Supported Languages (7)

| Language | Enum Value | Extensions | Validator Class | Pure-Java Engine |
|---|---|---|---|---|
| JavaScript | `JAVASCRIPT` | `.js`, `.mjs`, `.cjs`, `.jsx` | `JavaScriptValidator` | `JavaScriptSyntaxEngine` |
| CSS | `CSS` | `.css` | `CssValidator` | `CssSyntaxEngine` |
| HTML | `HTML` | `.html`, `.htm`, `.xhtml` | `HtmlValidator` | `HtmlSyntaxEngine` |
| PHP | `PHP` | `.php`, `.phtml`, `.phps` | `PhpValidator` | `PhpSyntaxEngine` |
| Java | `JAVA` | `.java` | `JavaValidator` | `JavaLexer` + `JavaSyntaxEngine` + 3 checkers |
| TypeScript | `TYPESCRIPT` | `.ts` | — | _(placeholder, future)_ |
| Python | `PYTHON` | `.py` | `PythonValidator` | `PythonLexer` + `PythonParser` + `PythonSyntaxEngine` (two-phase: engine + python3 binary) |
| Mixed Content | _(via `ValidatorFactory.getMixedContentValidator()`)_ | `.html`, `.htm`, `.php` (when mixed content detected) | `MixedContentValidator` | `MixedContentSyntaxEngine` |

1. **External-tool-first, pure-Java fallback** — every validator attempts the real external tool first; if the binary is missing it silently falls back to the embedded engine.
2. **Immutable, structured results** — `ValidationResult` is a record-like value object with `isValid`, `message`, and `List<ValidationError>`. Each error carries 1-based line, column, message, and optional raw tool output.
3. **Strategy pattern** — `LanguageValidator` (interface) → `AbstractLanguageValidator` (base) → concrete per-language validators. `ValidatorFactory` resolves the correct validator for a `Language` enum.
4. **Modification pipeline** — `ModificationApplier` applies line-range replacements to produce a candidate snapshot without touching disk. The library validates this snapshot.
5. **In-memory file cache** — `FileCache` holds `FileCacheEntry` snapshots keyed by `Path`, avoiding repeated disk reads during batch validation.
6. **Stateless engines** — all syntax engines (`CssSyntaxEngine`, `HtmlSyntaxEngine`, `JavaScriptSyntaxEngine`, `PhpSyntaxEngine`, `MixedContentSyntaxEngine`, `PythonSyntaxEngine`) are stateless singletons, safe for concurrent use.

---

## 2 · Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Build tool | Gradle (Groovy DSL) |
| Testing | JUnit 5 + AssertJ |
| External tools (optional) | `node`, `python`, `php`, `stylelint`, `vnu.jar`, `javac` (JDK) |

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
    │   ├── model/
    │   │   ├── Language.java                  ← enum: JAVASCRIPT, CSS, HTML, PHP, TYPESCRIPT, PYTHON, JAVA
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
    │   │   │   └── PhpOutputParser.java
    │   │   │
    │   │   ├── java/
    │   │   │   ├── JavaValidator.java          ← two-phase validator (syntax + javac fallback)
    │   │   │   ├── JavaSyntaxEngine.java       ← pure-Java syntax checker orchestrator
    │   │   │   ├── JavaLexer.java              ← hand-written dependency-free lexical analyser
    │   │   │   ├── JavaToken.java              ← immutable token record (type, text, line, column)
    │   │   │   ├── JavaTokenType.java          ← 9 token-type enum (NUMBER, KEYWORD, …, EOF)
    │   │   │   ├── JavacOutputParser.java      ← parses javac error output
    │   │   │   └── checker/
    │   │   │       ├── SyntaxChecker.java          ← functional interface for checkers
    │   │   │       ├── TokenizationErrorChecker.java  ← detects lexer ERROR tokens
    │   │   │       ├── DelimiterBalanceChecker.java   ← validates (), {}, [] nesting
    │   │   │       └── KeywordUsageChecker.java      ← validates keyword/modifier placement
    │   │   │
    │   │   └── mixed/
    │   │   │   ├── PhpSyntaxEngine.java
    │   │   │   └── PhpOutputParser.java
    │   │   │
    │   │   └── mixed/
    │   │       ├── MixedContentValidator.java
    │   │       ├── MixedContentSyntaxEngine.java
    │   │       ├── MixedContentValidator.java
    │   │       ├── MixedContentSyntaxEngine.java
    │   │       ├── HtmlContentExtractor.java
    │   │       └── ExtractedBlock.java
    │   │
    │   │   └── python/
    │   │       ├── PythonValidator.java          ← two-phase validator (engine + python binary)
    │   │       ├── PythonSyntaxEngine.java       ← pure-Java syntax engine (lex → parse → checks)
    │   │       ├── PythonLexer.java              ← hand-written dependency-free Python 3.14 lexer
    │   │       ├── PythonParser.java             ← structural parser with 10+ validation checks
    │   │       ├── PythonToken.java              ← immutable token record (type, text, line, column)
    │   │       ├── PythonTokenType.java          ← 78 token-type enum (keywords, literals, operators, …, EOF)
    │   │       └── PythonOutputParser.java       ← parses python3 binary error output
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
        │   │   ├── JsTokenTest.java
        │   │   ├── JsTokenTypeTest.java
        │   │   └── NodeCheckOutputParserTest.java
        │   ├── css/
        │   │   ├── CssValidatorTest.java
        │   │   ├── CssSyntaxEngineTest.java
        │   │   └── StylelintOutputParserTest.java
        │   ├── html/
        │   │   ├── HtmlValidatorTest.java
        │   │   ├── HtmlSyntaxEngineTest.java
        │   │   ├── VnuOutputParserTest.java
        │   │   └── PhpOutputParserTest.java
        │   ├── java/
        │   │   ├── JavaValidatorTest.java
        │   │   ├── JavaSyntaxEngineTest.java
        │   │   ├── JavaLexerTest.java
        │   │   ├── JavaTokenTest.java
        │   │   ├── JavaTokenTypeTest.java
        │   │   ├── JavacOutputParserTest.java
        │   │   └── checker/
        │   │       ├── TokenizationErrorCheckerTest.java
        │   │       ├── DelimiterBalanceCheckerTest.java
        │   │       └── KeywordUsageCheckerTest.java
        │   └── mixed/
        │       ├── MixedContentValidatorTest.java
        │       ├── MixedContentSyntaxEngineTest.java
        │       ├── MixedContentIntegrationTest.java
        │       ├── PhpMixedContentIntegrationTest.java
        │       ├── HtmlContentExtractorTest.java
        │   └── mixed/
        │       ├── MixedContentValidatorTest.java
        │       ├── MixedContentSyntaxEngineTest.java
        │       ├── MixedContentIntegrationTest.java
        │       ├── PhpMixedContentIntegrationTest.java
        │       ├── HtmlContentExtractorTest.java
        │       └── ExtractedBlockTest.java
        │   └── python/
        │       ├── PythonValidatorTest.java
        │       ├── PythonSyntaxEngineTest.java
        │       ├── PythonLexerTest.java
        │       ├── PythonParserTest.java
        │       └── PythonOutputParserTest.java
        │   └── FileCacheEntryTest.java
        ├── process/
        │   ├── ProcessExecutorTest.java
        │   └── ProcessResultTest.java
        └── modification/
            └── ModificationApplierTest.java

**Total: 43 test files**

### 4.1 Main Facade
| `SyntaxValidationLibrary.java` | The primary entry point. Exposes `validateModification(path, original, modifications)` and `validateSource(language, source)`. Orchestrates `ValidatorFactory`, `ModificationApplier`, and `FileCache`. |

### 4.2 Model Layer

| File | Summary |
|---|---|
| `Language.java` | Enum with values: `JAVASCRIPT`, `CSS`, `HTML`, `PHP`, `TYPESCRIPT`, `PYTHON`, `JAVA`. Each value carries its file extensions and a display name. Provides `fromExtension(String)` for filename→language mapping. `TYPESCRIPT` and `PYTHON` are placeholders for future validators. |
| `ModificationRequest.java` | Immutable descriptor: `startLine`, `endLine`, `newContent`, optional `description`. All indices are 1-based and inclusive. |
| `ValidationResult.java` | Immutable outcome: `boolean valid`, `String message`, `List<ValidationError> errors`. Provides factory methods `valid(msg)` and `invalid(msg, errors)`. |
| `ValidationError.java` | Immutable diagnostic: `int line`, `int column`, `String message`, `String toolOutput` (nullable). |

### 4.3 Validator Layer

| File | Summary |
|---|---|
| `LanguageValidator.java` | Interface: `getLanguage()`, `validateSource(String)`, `validateFile(Path)` |
| `AbstractLanguageValidator.java` | Base class implementing the external-tool-first, pure-Java-fallback pattern. Uses `BinaryResolver` to check for external tool, `ProcessExecutor` to run it, and delegates to a `*SyntaxEngine` for the fallback. |
| `ValidatorFactory.java` | Resolves `Language → LanguageValidator`. Maintains a map of all registered validators (JavaScript, CSS, HTML, PHP, Java). Also provides `getMixedContentValidator()` for mixed HTML/PHP content. |

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

### 4.8 Java Validator

| File | Summary |
|---|---|
| `JavaValidator.java` | Extends `AbstractLanguageValidator`. External tool: `javac` (JDK). Two-phase validation: (1) pure-Java `JavaSyntaxEngine` for instant feedback, (2) `javac` fallback for deep semantic checks if no syntax errors found. |
| `JavaSyntaxEngine.java` | Pure-Java syntax engine orchestrator. Runs a pipeline of `SyntaxChecker` instances over the token stream produced by `JavaLexer`. Provides both a default checker pipeline and a custom-pipeline overload. |
| `JavaLexer.java` | Hand-written, dependency-free lexical analyser (~500 lines). Tokenises the full Java grammar: keywords (Java 25 set), identifiers, numeric literals (decimal, hex, octal, binary, floats, with underscores), string literals (with escapes), char literals, line comments, block comments, operators, and punctuation. Emits `JavaToken` list with accurate line/column tracking. |
| `JavaToken.java` | Immutable record: `JavaTokenType type`, `String text`, `int line`, `int column`. Compact constructor validates non-null type/text and line/column ≥ 1. |
| `JavaTokenType.java` | Enum with 9 values: `NUMBER`, `STRING`, `CHAR`, `IDENTIFIER`, `KEYWORD`, `PUNCTUATION`, `COMMENT`, `ERROR`, `EOF`. |
| `JavacOutputParser.java` | Parses `javac` error output into `ValidationError` list. Extracts file, line, column, and message from diagnostic lines. Filters out source-echo lines, caret indicators, and summary lines. |
| `checker/SyntaxChecker.java` | Functional interface: `check(List<JavaToken>) → List<ValidationError>`. Allows custom validation strategies to be composed into the `JavaSyntaxEngine` pipeline. |
| `checker/TokenizationErrorChecker.java` | Detects lexer-produced `ERROR` tokens (unterminated strings, illegal characters, malformed numeric literals). Produces detailed error messages with position info and truncation for long lexemes. |
| `checker/DelimiterBalanceChecker.java` | Validates balanced `()`, `{}`, `[]` nesting across the token stream. Tracks opening delimiters on a stack and detects: unclosed delimiters, mismatched closers, and stray closing delimiters. Skips `COMMENT` and `STRING`/`CHAR` tokens. |
| `checker/KeywordUsageChecker.java` | Validates correct usage of Java keywords and modifiers: `package`, `import`, class/interface/enum/record declarations, method signatures, control structures, and modifier placement. Ensures statement completeness (missing semicolons, unclosed blocks). |

### 4.9 Mixed Content Validator

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

### Java (`JavaLexer` + `JavaSyntaxEngine` + checkers)
- **Lexer** (`JavaLexer`): Hand-written, dependency-free (~500 lines). Supports full Java grammar: keywords (Java 25 set incl. `sealed`, `non-sealed`, `permits`, `record`, `yield`, `var`), identifiers, numeric literals (decimal, hex `0x`, octal `0`, binary `0b`, floats with exponents, underscore separators), string literals (with escape sequences), char literals, line comments (`//`), block comments (`/* */`), all operators and punctuation.
- **Token model**: `JavaToken` record (type, text, line, column) with `JavaTokenType` enum (9 categories: `NUMBER`, `STRING`, `CHAR`, `IDENTIFIER`, `KEYWORD`, `PUNCTUATION`, `COMMENT`, `ERROR`, `EOF`).
- **Checker pipeline** (`JavaSyntaxEngine`): Pluggable `SyntaxChecker` functional interface with three default checkers:
  - `TokenizationErrorChecker` — detects lexer-produced `ERROR` tokens (unterminated strings/chars, illegal characters, malformed numerics). Truncates long lexemes in error messages.
  - `DelimiterBalanceChecker` — validates `()`, `{}`, `[]` nesting via stack-based tracking. Detects unclosed, mismatched, and stray closing delimiters.
  - `KeywordUsageChecker` — validates keyword/modifier placement: `package`, `import`, class/interface/enum/record declarations, method signatures, control structures, modifier ordering, statement completeness.
- **External tool fallback**: `JavaValidator` attempts pure-Java validation first (instant feedback). If no syntax errors and `javac` binary is available, runs `javac -Xlint:all` for deep semantic checks and parses output via `JavacOutputParser`.

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
- Extension matching: `.js`/`.mjs`/`.cjs`/`.jsx` → JAVASCRIPT; `.css` → CSS; `.html`/`.htm`/`.xhtml` → HTML; `.php`/`.phtml`/`.phps` → PHP; `.java` → JAVA; `.ts` → TYPESCRIPT; `.py` → PYTHON.

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
validators.put(Language.JAVA, new JavaValidator());
// MixedContentValidator is NOT registered by Language key — it is
// accessible via ValidatorFactory.getMixedContentValidator() for
// HTML/PHP files that contain embedded CSS, JS, or PHP blocks.
// TYPESCRIPT and PYTHON are placeholder enum values with no registered
// validator yet.
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

## 9 · Test Coverage (39 Test Files)

### Model Tests (4)
| Test | Covers |
|---|---|
| `LanguageTest` | Enum values (7 languages), extension mapping (.java/.ts/.py/.php etc.), case insensitivity, fromPath, exact constant set |
| `ModificationRequestTest` | Construction, validation, equality |
| `ValidationResultTest` | Factory methods, validity, error lists |
| `ValidationErrorTest` | Construction, field access, equality |

### Validator Tests (4)
| Test | Covers |
|---|---|
| `AbstractLanguageValidatorTest` | Base class contract, fallback behavior |
| `ValidatorFactoryTest` | Language→validator resolution, Java validator registration, supported languages |
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
### Java Engine Tests (9)
| Test | Covers |
|---|---|
| `JavaValidatorTest` | Two-phase validation (syntax + javac fallback), language contract, binary discovery, command construction |
| `JavaSyntaxEngineTest` | Default pipeline, custom checker pipeline, valid/invalid programs, static method, edge cases |
| `JavaLexerTest` | Keywords (Java 25 set), identifiers, numeric literals (hex/oct/bin/float), strings (escapes), chars, comments (line/block), operators, position tracking, composite programs |
| `JavaTokenTest` | Record construction, compact-constructor validation (null type/text, line/column < 1), accessors, equality, hashCode contract, toString, realistic lexer-produced tokens |
| `JavaTokenTypeTest` | Enum constants (exact count of 9), valueOf resolution, name/toString, ordinal stability, EnumSet operations, semantic properties for downstream checkers |
| `JavacOutputParserTest` | Empty/null output, error diagnostics, warnings, source-echo filtering, caret lines, summary lines, unexpected output |
| `TokenizationErrorCheckerTest` | Clean source, unterminated string/char/block comment, illegal characters, multiple errors, long-text truncation, position preservation |
| `DelimiterBalanceCheckerTest` | Balanced nesting, unclosed delimiters, mismatched closers, stray closers, string/comment skipping |
| `KeywordUsageCheckerTest` | Modifier placement, reserved keywords, annotations, statement completeness, complex programs |

### Mixed Content Tests (6)
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
2. Create `NewLangValidator extends AbstractLanguageValidator` in `validator/newlang/`.
3. Create `NewLangSyntaxEngine` as the pure-Java fallback. For complex languages, consider the modular pattern used by the Java validator: `Lexer` → `Token` model → `SyntaxChecker` pipeline (see `validator/java/` as a reference implementation).
4. (Optional) Create `NewLangOutputParser` if an external tool produces parseable output.
5. Register in `ValidatorFactory.java`.
6. Write tests: `NewLangValidatorTest`, `NewLangSyntaxEngineTest`, token model tests, checker tests, (optional) `NewLangOutputParserTest`.

### Adding a New Pure-Java Engine Rule

1. Locate the relevant `*SyntaxEngine.java`.
2. For Java: implement a new `SyntaxChecker` and add it to the `JavaSyntaxEngine` default pipeline (or pass it via the custom-pipeline overload).
3. For other languages: add the validation rule in the appropriate phase (tokeniser, balance, grammar).
4. Add test cases in the corresponding `*SyntaxEngineTest.java` or `*CheckerTest.java`.
3. Add test cases in the corresponding `*SyntaxEngineTest.java`.

### Adding Mixed Content Support for a New Language

1. Add extraction patterns in `HtmlContentExtractor.java` for the new tag/block type.
2. Add a new case in `MixedContentSyntaxEngine.validate()` to validate extracted blocks.
3. Add tests in `HtmlContentExtractorTest.java` and `MixedContentSyntaxEngineTest.java`.

---

## 11 · Important Notes

- **Java validator has a modular architecture** — `JavaValidator` → `JavaSyntaxEngine` → `JavaLexer` + `SyntaxChecker` pipeline (3 default checkers: `TokenizationErrorChecker`, `DelimiterBalanceChecker`, `KeywordUsageChecker`). New checkers can be plugged in via the `SyntaxChecker` functional interface.
- **`JavaValidator` uses two-phase validation** — phase 1 runs the pure-Java `JavaSyntaxEngine` for instant feedback; phase 2 runs `javac` (if available) for deep semantic checks. Phase 2 only runs when phase 1 finds no syntax errors.
- **`TYPESCRIPT` and `PYTHON` are placeholder enum values** — `Language.TYPESCRIPT` and `Language.PYTHON` exist for extension mapping, but no validators are registered yet. `ValidatorFactory.get(Language.TYPESCRIPT)` returns `Optional.empty()`.
- **External tools are optional** — the library works fully offline with pure-Java engines.
- **File cache is in-memory** — not persisted across JVM restarts.
- **ModificationApplier operates in-memory** — never writes to disk.
- **All line numbers are 1-based** throughout the API.
- **Column numbers use -1** to indicate "unknown" when the engine cannot determine the column.
- **`MixedContentSyntaxEngine` always validates all four dimensions** (HTML, CSS, JS, PHP) regardless of which are present; missing dimensions simply produce no errors.
- **PHP engine supports PHP 8.0+ attributes** (`#[Route('/api')]`), **readonly classes**, **match expressions**, **union/intersection/DNF types**, **constructor promotion**, **named arguments**, and **enums with backed types**.
- **Mixed content line remapping** ensures error line numbers in embedded CSS/JS/PHP blocks refer to positions in the original HTML/PHP document, not the extracted fragment.
