# Project Agent Configuration

## Architecture Rules

### Mixed Content Validation (PHP-First Strategy)

When validating PHP mixed content (PHP tags embedded in HTML structure):

1. **Detection**: `isPhpMixedContent()` checks for both PHP tags (`<?php`, `<?=`) AND HTML structure (`<html>`, `<head>`, `<body>`, `<!DOCTYPE>`, `<div>`)
2. **PHP-First Validation**: When PHP binary is available:
   - Run `php -l` on the ENTIRE original file first
   - If PHP validation passes → skip VNU/stylelint/node entirely
   - If PHP validation fails → report PHP errors and stop
3. **Rationale**:
   - `php -l` correctly parses mixed PHP/HTML files (PHP natively supports this)
   - PHP blocks stripped from HTML/CSS/JS create false errors (empty `<title>`, empty `action=""`, invalid CSS values, JS syntax errors)
   - PHP generates valid HTML at runtime; validation should check PHP syntax, not extracted fragments
4. **Fallback**: When no PHP binary available, use context-aware sanitization:
   - HTML: Replace PHP tags with "php" (valid text/attribute value)
   - CSS: Replace PHP tags with "inherit" (valid CSS keyword)
   - JS: Replace PHP tags with "null" (valid JS literal)

### File Responsibilities

- `MixedContentSyntaxEngine.java`: Core validation logic, PHP-first strategy
- `MixedContentValidator.java`: Public API, delegates to engine
- `HtmlContentExtractor.java`: Extracts `<style>`, `<script>`, `<?php...?>` blocks
- `PhpValidator.java`: Invokes `php -l` binary for PHP syntax validation

### Binary Dependencies

| Validator | Binary | Purpose |
|-----------|--------|---------|
| HTML | vnu.jar | HTML5 validation (syntax-only mode with `--errors-only` and `--filterpattern` to skip ARIA/accessibility checks) |
| CSS | stylelint | CSS linting |
| JS | node | JavaScript syntax |
| PHP | php.exe | PHP syntax (`php -l`) |

### HTML Validator (vnu) Configuration

The HTML validator operates in **syntax-only mode** by passing these flags to vnu:
- `--errors-only`: Only report errors, not warnings
- `--filterpattern`: Regex pattern to filter out non-syntax messages

The filter pattern `VNU_SYNTAX_ONLY_FILTER` excludes:
- ARIA attribute restrictions (e.g., `aria-label` on `<div>` without proper role)
- Role attribute restrictions
- Accessibility-related attribute requirements

This focuses validation on **structural/syntax correctness** (AST-level) rather than semantic/accessibility compliance.

### Byte-Faithful Edit Pipeline (`Document` model)

An edit must change bytes **only** inside the edited line range. Split-and-rejoin with one
"dominant" separator violated that (mixed CRLF/LF files, CR-only files, missing final
newline) and a leaked UTF-8 BOM corrupted line 1. The pipeline is now:

1. **`modification/Document.java`** — authoritative immutable text model: terminator-free
   lines + the exact terminator of each line + `leadingBom` flag.
   `parse(raw)` records terminators and strips a leading U+FEFF into the flag; `render()`
   is disk-faithful (re-emits the BOM), `renderWithoutBom()` feeds validators.
2. **`modification/LineEnding.java`** — coarse convention value type (separator +
   `trailingNewline`). Descriptor/back-compat only: `join()` normalizes mixed endings, so it
   must never be used to produce file output; render through `Document` instead.
3. **`ModificationApplier`** — two flavours sharing one `validateBounds` helper (identical
   accept/reject semantics): content-only `apply` / `applyAll` on `List<String>`, and
   byte-faithful `applyToDocument` / `applyAllToDocument`. They are deliberately **not**
   overloads of the List methods: same-arity overloads made `applier.apply(null, 1, 1, x)`
   ambiguous for callers. Terminator rules: kept lines keep their own terminators; inserted
   lines take the dominant separator; the last inserted line inherits the terminator that
   followed the replaced range (so a missing final newline stays missing); deletions leave
   surrounding terminators untouched.
4. **`FileCache`** — reads bytes once and decodes **strict** UTF-8 (`CharsetDecoder` with
   `CodingErrorAction.REPORT`) → `io.UnsupportedFileEncodingException(path, byteOffset)`;
   never a lossy decode. Staleness = lastModified **and** size (size catches a rewrite that
   lands in the same clock tick as the cached timestamp).
5. **`SyntaxValidationLibrary`** — validators always receive `renderWithoutBom()` (a BOM must
   never reach external tools or break position-0 detection such as `<!DOCTYPE>`);
   `getModifiedContent(...)` returns disk-faithful `render()`. `applyModification` /
   `applyBatchModification` persist the edit and invalidate the cache entry.
6. **`io/SourceFileWriter`** — strict UTF-8 encode (unmappable text such as an unpaired
   surrogate throws `UnmappableTextException` and nothing is written) + atomic replace: write
   sibling temp file (`CREATE_NEW`) then `ATOMIC_MOVE` with a plain-replace fallback. This is
   why consumers must not hand-roll `Files.writeString` (truncate-in-place + silent `?`).

**Invariant:** no lossy fallbacks anywhere — no `CodingErrorAction.REPLACE`, no `'?'`
substitution, no charset guessing. UTF-8-only is the contract; fail loudly with a message
that names the path and offset.

### Tooling Notes (editing this repo)

- The editor's syntax verifier rejects arrow-form `switch` payloads — use `if/else` chains in
  new code (javac itself is fine with either).
- For line-range edits, take line numbers from `searchRegexInFile` / javac output; the range
  reader's displayed numbering can be off by one. Always re-verify an edit with
  `gradlew --offline compileJava`, since the verifier is weaker than javac.
- Baseline (pristine HEAD) already has 89 failing tests, all external-binary/environment
  integration suites (Python/PHP/vnu/stylelint). Compare failure sets before blaming a change.
- Editor file-tool relative paths resolve against the editor install dir (`C:\Program Files\EazeEditor`),
  NOT this project root. Always pass absolute `F:\code-helper-mcp-library-java\...` paths to
  read/write/search tools; shell sessions also start outside the repo — `Set-Location` first.

### Line-Ending Policy (repo-level, `.gitattributes`)

- The repo stores **LF** for every text file and checkouts emit LF. `*.bat` / `*.cmd` are
  `text eol=crlf` because `cmd.exe` needs CRLF on disk; known binary extensions (including
  `gradle/wrapper/gradle-wrapper.jar`) are marked `binary`, so their bytes are never converted.
- `.gitattributes` takes precedence over `core.autocrlf`, so endings no longer depend on any
  clone's local config. Do not reintroduce autocrlf-driven behaviour in tooling or scripts.
- Scope: this policy governs *repo sources* only. The library still preserves whatever ending a
  *user* file uses (byte-faithful edit pipeline above); the two concerns are independent.
- Verify with `git ls-files --eol` (expect `i/lf w/lf`, plus `w/crlf` for `.bat`) and
  `git check-attr text eol -- <path>`. A whole-file diff in a future commit means an EOL rule was
  bypassed: write with explicit UTF-8 + LF rather than relying on tool defaults.
