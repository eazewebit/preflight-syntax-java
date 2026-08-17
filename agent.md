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
