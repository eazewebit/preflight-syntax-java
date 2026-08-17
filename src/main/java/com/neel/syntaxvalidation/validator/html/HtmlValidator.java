package com.neel.syntaxvalidation.validator.html;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.binary.manager.BinaryManager;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;
import com.neel.syntaxvalidation.validator.LanguageValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlValidator extends AbstractLanguageValidator implements LanguageValidator {

    static final String BINARY_NAME = "vnu";
    private static final HtmlSyntaxEngine ENGINE = HtmlSyntaxEngine.getInstance();




    /**
     * Regex pattern passed to vnu's {@code --filterpattern} option.
     * Filters out ARIA/accessibility attribute checks that are NOT syntax errors.
     * Only genuine structural/syntax violations are reported.
     */
    private static final String VNU_SYNTAX_ONLY_FILTER =
            ".*aria-[a-z]+.*attribute.*must not.*" +
            "|.*role.*attribute.*must not.*" +
            "|.*must not be specified.*unless.*role.*";



    public HtmlValidator(String preferredBinaryPath, BinaryResolver binaryResolver, ProcessExecutor processExecutor) { super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor); }
    public HtmlValidator(BinaryManager binaryManager) { super(null, BINARY_NAME, binaryManager, new ProcessExecutor()); }

    @Override public Language getLanguage() { return Language.HTML; }
    @Override public ValidationResult validate(String content) { return validate(content, "validate.html"); }

    @Override
    public ValidationResult validate(String content, String fileName) {
        String safeContent = content == null ? "" : content;
        String preprocessedContent = stripCustomNamespaceAttributes(safeContent);
        return super.validate(preprocessedContent, fileName);
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        if (binaryPath != null && binaryPath.toLowerCase().endsWith(".jar")) {
            String javaExe = resolveJavaExecutable();
            log.info("[HTML-VALIDATOR] JAR detected - using Java runtime: {}", javaExe);
            return List.of(javaExe, "-jar", binaryPath,
                    "--format", "json", "--errors-only",
                    "--filterpattern", VNU_SYNTAX_ONLY_FILTER,
                    tempFile.toAbsolutePath().toString());
        }
        return List.of(binaryPath,
                "--format", "json", "--errors-only",
                "--filterpattern", VNU_SYNTAX_ONLY_FILTER,
                tempFile.toAbsolutePath().toString());
    }
    private String resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            String exeName = isWindows ? "java.exe" : "java";
            Path javaBin = Path.of(javaHome, "bin", exeName);
            if (Files.exists(javaBin)) return javaBin.toAbsolutePath().toString();
            Path parent = Path.of(javaHome).getParent();
            if (parent != null) {
                Path parentJavaBin = parent.resolve("bin").resolve(exeName);
                if (Files.exists(parentJavaBin)) return parentJavaBin.toAbsolutePath().toString();
            }
        }
        return "java";
    }

    @Override protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        ValidationResult parsed = parseVnuJsonOutput(result);
        return parsed != null ? parsed : ValidationResult.invalid("HTML validation: unable to parse output.");
    }
    @Override protected String getFileExtension() { return ".html"; }
    @Override protected String binaryNotFoundMessage() {
        return "vnu.jar binary not found. Install the Nu Html Checker or provide a path via the 'vnu.path' system property. Falling back to the built-in HTML syntax engine.";
    }

    private ValidationResult parseVnuJsonOutput(ProcessResult result) {
        String output = result.stderr();
        if (output == null || output.isBlank()) output = result.stdout();
        if (output == null || output.isBlank()) {
            if (result.succeeded()) return ValidationResult.valid("HTML is valid (verified by vnu).");
            return null;
        }
        try { return parseVnuJson(output.trim()); } catch (Exception e) {
            log.debug("[HTML-VALIDATOR] JSON parsing failed, trying text fallback: {}", e.getMessage());
        }
        return parseVnuTextOutput(output);
    }

    private ValidationResult parseVnuJson(String json) {
        List<ValidationError> errors = new ArrayList<>();
        int messagesIdx = json.indexOf("\"messages\"");
        if (messagesIdx < 0) return ValidationResult.valid("HTML is valid (verified by vnu).");
        String messagesSection = json.substring(messagesIdx);
        int arrayStart = messagesSection.indexOf('[');
        int arrayEnd = messagesSection.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd < 0 || arrayEnd <= arrayStart) return ValidationResult.valid("HTML is valid (verified by vnu).");
        String arrayContent = messagesSection.substring(arrayStart + 1, arrayEnd).trim();
        if (arrayContent.isEmpty()) return ValidationResult.valid("HTML is valid (verified by vnu).");
        String[] messageBlocks = arrayContent.split("\\}\\s*,\\s*\\{");
        for (String block : messageBlocks) {
            String type = extractJsonValue(block, "type");
            if (!"error".equalsIgnoreCase(type)) continue;
            int lastLine = extractJsonInt(block, "lastLine");
            int lastColumn = extractJsonInt(block, "lastColumn");
            String message = extractJsonValue(block, "message");
            if (message != null && !message.isBlank()) {
                errors.add(new ValidationError(lastLine > 0 ? lastLine : -1, lastColumn > 0 ? lastColumn : -1, message, block.trim()));
            }
        }
        if (errors.isEmpty()) return ValidationResult.valid("HTML is valid (verified by vnu).");
        return ValidationResult.invalid(String.format("HTML validation detected %d error(s).", errors.size()), errors);
    }

    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (!m.find()) return null;
        int valueStart = m.end();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = valueStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { sb.append(c); escaped = false; }
            else if (c == '\\') { escaped = true; }
            else if (c == '"') break;
            else sb.append(c);
        }
        return sb.toString();
    }

    private static int extractJsonInt(String json, String key) {
        String pattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (m.find()) { try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return -1; } }
        return -1;
    }

    private static final Pattern VNU_TEXT_PATTERN = Pattern.compile(":(\\d+)(?:\\.(\\d+))?:\\s+error:\\s+(.+)");

    private ValidationResult parseVnuTextOutput(String output) {
        List<ValidationError> errors = new ArrayList<>();
        for (String line : output.lines().toList()) {
            Matcher m = VNU_TEXT_PATTERN.matcher(line);
            if (m.find()) {
                int lineNum = Integer.parseInt(m.group(1));
                int col = m.group(2) != null ? Integer.parseInt(m.group(2)) : -1;
                String msg = m.group(3).trim();
                errors.add(new ValidationError(lineNum, col, msg, line));
            }
        }
        if (errors.isEmpty() && !output.isBlank()) errors.add(new ValidationError(-1, -1, output.trim(), output.trim()));
        if (errors.isEmpty()) return ValidationResult.valid("HTML is valid (verified by vnu).");
        return ValidationResult.invalid(String.format("HTML validation detected %d error(s).", errors.size()), errors);
    }

    public ValidationResult validateWithBuiltInEngine(String content) { return ENGINE.validate(content); }

    // ----------------------------------------------------------------
    //  Custom namespace attribute stripping
    // ----------------------------------------------------------------

    private static final Set<String> STANDARD_XML_ATTRIBUTES = Set.of("xml:lang", "xml:space", "xml:base", "xml:id");

    static String stripCustomNamespaceAttributes(String content) {
        if (content == null || content.isEmpty()) return content;
        StringBuilder result = new StringBuilder(content.length());
        int i = 0;
        int len = content.length();
        while (i < len) {
            if (i + 3 < len && content.charAt(i) == '<' && content.charAt(i + 1) == '!'
                    && content.charAt(i + 2) == '-' && content.charAt(i + 3) == '-') {
                int endComment = content.indexOf("-->", i + 4);
                if (endComment == -1) { result.append(content, i, len); break; }
                result.append(content, i, endComment + 3);
                i = endComment + 3;
                continue;
            }
            if (content.charAt(i) == '<' && i + 1 < len
                    && (Character.isLetter(content.charAt(i + 1)) || content.charAt(i + 1) == '/')) {
                int tagEnd = findTagEnd(content, i);
                if (tagEnd == -1) { result.append(content.charAt(i)); i++; continue; }
                String tagContent = content.substring(i, tagEnd + 1);
                String cleanedTag = stripNamespaceAttrsFromTag(tagContent);
                result.append(cleanedTag);
                i = tagEnd + 1;
                continue;
            }
            result.append(content.charAt(i));
            i++;
        }
        return result.toString();
    }

    private static int findTagEnd(String content, int start) {
        int len = content.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = start + 1; i < len; i++) {
            char c = content.charAt(i);
            if (inSingleQuote) { if (c == '\'') inSingleQuote = false; continue; }
            if (inDoubleQuote) { if (c == '"') inDoubleQuote = false; continue; }
            if (c == '\'') inSingleQuote = true;
            else if (c == '"') inDoubleQuote = true;
            else if (c == '>') return i;
        }
        return -1;
    }

    private static String stripNamespaceAttrsFromTag(String tag) {
        // Step 1: Protect quoted values with unique placeholders
        java.util.List<String> protectedValues = new java.util.ArrayList<>();
        java.util.regex.Matcher quoteMatcher = java.util.regex.Pattern.compile("\"[^\"]*\"|'[^']*'").matcher(tag);
        StringBuilder protectedTag = new StringBuilder();
        int lastEnd = 0;
        while (quoteMatcher.find()) {
            protectedTag.append(tag, lastEnd, quoteMatcher.start());
            String placeholder = "\u00abV" + protectedValues.size() + "\u00bb";
            protectedValues.add(quoteMatcher.group());
            protectedTag.append(placeholder);
            lastEnd = quoteMatcher.end();
        }
        protectedTag.append(tag.substring(lastEnd));
        String safeTag = protectedTag.toString();

        // Step 2: Match namespace attributes using correct regex
        // Pattern: \s+(ATTR_NAME)(ATTR_VALUE)?
        // ATTR_NAME: xml:triple:colon OR prefix:base
        // ATTR_VALUE: \s*=\s*(quoted-placeholder | unquoted)
        String regexPattern = "\\s+((xml:[a-zA-Z_][a-zA-Z0-9_.-]*:[a-zA-Z_][a-zA-Z0-9_.-]*" +
                "|[a-zA-Z_][a-zA-Z0-9_.-]*:([a-zA-Z_][a-zA-Z0-9_.-]*))" +
                ")(\\s*=\\s*(?:\u00abV\\d+\u00bb|[^\\s>]*))?";
        Pattern namespaceAttrPattern = Pattern.compile(regexPattern);
        Matcher matcher = namespaceAttrPattern.matcher(safeTag);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String fullAttrName = matcher.group(1);
            String baseAttrName = matcher.group(3);
            if (STANDARD_XML_ATTRIBUTES.contains(fullAttrName)) continue;
            if (fullAttrName.startsWith("xmlns:")) { matcher.appendReplacement(sb, ""); continue; }
            if (fullAttrName.startsWith("xml:")) { matcher.appendReplacement(sb, ""); continue; }
            if (baseAttrName != null && !baseAttrName.isEmpty()) {
                String replacement = " " + baseAttrName + "=\"#\"";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);

        // Step 3: Restore quoted values
        String result = sb.toString();
        for (int i = 0; i < protectedValues.size(); i++) {
            result = result.replace("\u00abV" + i + "\u00bb", protectedValues.get(i));
        }
        return result;
    }
}
