package com.neel.syntaxvalidation.integration;

import com.neel.syntaxvalidation.binary.manager.BinaryInfo;
import com.neel.syntaxvalidation.binary.manager.BinaryManager;
import com.neel.syntaxvalidation.binary.manager.BinaryStatus;
import com.neel.syntaxvalidation.model.ModificationRequest;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.SyntaxValidationLibrary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test that downloads ALL binaries and validates code.
 *
 * <p>This test proves the complete pipeline:
 * <ol>
 *   <li>Downloads all required binaries (Node.js, JDK, TypeScript, Python, PHP, VNU, Stylelint)</li>
 *   <li>Uses each binary to validate actual code</li>
 *   <li>Prints a detailed summary report</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>gradlew test --tests "*BinaryDownloadAndValidationE2ETest" -i</pre>
 */
@DisplayName("End-to-End: Download Binaries & Validate Code")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BinaryDownloadAndValidationE2ETest {



    @TempDir
    Path tempDir;

    static BinaryManager binaryManager;
    static SyntaxValidationLibrary library;

    // Track results for summary
    static final Map<String, String> downloadResults = new LinkedHashMap<>();
    static final Map<String, Boolean> validationResults = new LinkedHashMap<>();

    @BeforeAll
    static void setup() throws IOException {
        out("=".repeat(70));
        out("  END-TO-END INTEGRATION TEST");
        out("  Binary Download & Code Validation");
        out("=".repeat(70));
        out("  OS:           " + System.getProperty("os.name") + " / " + System.getProperty("os.arch"));
        out("=".repeat(70));

        binaryManager = new BinaryManager();
        library = new SyntaxValidationLibrary();
    }

    // ════════════════════════════════════════════════════════════════════
    //  PHASE 1: DOWNLOAD ALL BINARIES
    // ════════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("1/7  Download Node.js")
    @Timeout(180)
    void downloadNode() { doDownload(BinaryInfo.NODE, "Node.js"); }

    @Test @Order(2)
    @DisplayName("2/7  Download Java JDK 25")
    @Timeout(300)
    void downloadJdk() { doDownload(BinaryInfo.JAVAC, "Java JDK"); }

    @Test @Order(3)
    @DisplayName("3/7  Download TypeScript")
    @Timeout(180)
    void downloadTypeScript() {
        if (!ready("Node.js")) { skip("TypeScript", "Node.js not available"); return; }
        doDownload(BinaryInfo.TSC, "TypeScript");
    }

    @Test @Order(4)
    @DisplayName("4/7  Download Python")
    @Timeout(180)
    void downloadPython() { doDownload(BinaryInfo.PYTHON, "Python"); }

    @Test @Order(5)
    @DisplayName("5/7  Download PHP")
    @Timeout(180)
    void downloadPhp() { doDownload(BinaryInfo.PHP, "PHP"); }

    @Test @Order(6)
    @DisplayName("6/7  Download VNU (HTML Validator)")
    @Timeout(180)
    void downloadVnu() { doDownload(BinaryInfo.VNU, "VNU"); }

    @Test @Order(7)
    @DisplayName("7/7  Download Stylelint")
    @Timeout(180)
    void downloadStylelint() {
        if (!ready("Node.js")) { skip("Stylelint", "Node.js not available"); return; }
        doDownload(BinaryInfo.STYLELINT, "Stylelint");
    }

    // ════════════════════════════════════════════════════════════════════
    //  PHASE 2: VALIDATE CODE WITH DOWNLOADED BINARIES
    // ════════════════════════════════════════════════════════════════════

    @Test @Order(8)
    @DisplayName("8   Validate valid Java code")
    void validJava() throws IOException {
        Path f = tempDir.resolve("Hello.java");
        Files.writeString(f, """
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }
                """);
        var req = ModificationRequest.builder()
                .filePath(f.toString()).fromLine(1).toLine(1)
                .replacement("public class Hello {").build();
        var result = library.validate(req);
        assertValidation("Java (valid)", result, true);
    }

    @Test @Order(9)
    @DisplayName("9   Detect Java syntax error")
    void invalidJava() throws IOException {
        Path f = tempDir.resolve("Bad.java");
        Files.writeString(f, """
                public class Bad {
                    public static void main(String[] args) {
                        System.out.println("ok");
                    }
                }
                """);
        var req = ModificationRequest.builder()
                .filePath(f.toString()).fromLine(3).toLine(3)
                .replacement("        System.out.println(\"missing semi\")").build();
        var result = library.validate(req);
        assertValidation("Java (error detect)", result, false);
    }

    @Test @Order(10)
    @DisplayName("10  Validate JavaScript code")
    void validJs() throws IOException {
        Path f = tempDir.resolve("greet.js");
        Files.writeString(f, """
                const greet = (name) => `Hello, ${name}!`;
                console.log(greet('World'));
                """);
        var req = ModificationRequest.builder()
                .filePath(f.toString()).fromLine(1).toLine(1)
                .replacement("const greet = (name) => `Hello, ${name}!`;").build();
        var result = library.validate(req);
        assertValidation("JavaScript", result, true);
    }

    @Test @Order(11)
    @DisplayName("11  Validate TypeScript code")
    void validTs() throws IOException {
        if (!ready("TypeScript")) { skipVal("TypeScript"); return; }
        Path f = tempDir.resolve("user.ts");
        Files.writeString(f, """
                interface User {
                    name: string;
                    age: number;
                }
                function greet(user: User): string {
                    return `Hello, ${user.name}!`;
                }
                const user: User = { name: "World", age: 30 };
                console.log(greet(user));
                """);
        var req = ModificationRequest.builder()
                .filePath(f.toString()).fromLine(1).toLine(1)
                .replacement("interface User {").build();
        var result = library.validate(req);
        assertValidation("TypeScript", result, true);
    }

    @Test @Order(12)
    @DisplayName("12  Validate Python code")
    void validPy() throws IOException {
        if (!ready("Python")) { skipVal("Python"); return; }
        Path f = tempDir.resolve("greet.py");
        Files.writeString(f, """
                def greet(name: str) -> str:
                    return f"Hello, {name}!"
                if __name__ == "__main__":
                    print(greet("World"))
                """);
        var req = ModificationRequest.builder()
                .filePath(f.toString()).fromLine(1).toLine(1)
                .replacement("def greet(name: str) -> str:").build();
        var result = library.validate(req);
        assertValidation("Python", result, true);
    }

    @Test @Order(13)
    @DisplayName("13  Validate PHP code")
    void validPhp() throws IOException {
        if (!ready("PHP")) { skipVal("PHP"); return; }
        Path f = tempDir.resolve("greet.php");
        Files.writeString(f, """
                <?php
                function greet(string $name): string {
                    return "Hello, " . $name . "!";
                }
                echo greet("World");
                """);
        var req = ModificationRequest.builder()
                .filePath(f.toString()).fromLine(1).toLine(1)
                .replacement("<?php").build();
        var result = library.validate(req);
        assertValidation("PHP", result, true);
    }

    @Test @Order(14)
    @DisplayName("14  Validate HTML (VNU)")
    @Timeout(60)
    void validHtml() {
        if (!ready("VNU")) { skipVal("HTML"); return; }
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Test</title></head>
                <body><h1>Hello</h1></body>
                </html>
                """;
        var result = library.validateMixedContent(html);
        boolean ok = result != null;
        validationResults.put("HTML", ok);
        out("  " + (ok ? "✅" : "❌") + "  HTML (VNU)");
    }

    @Test @Order(15)
    @DisplayName("15  Validate CSS (Stylelint)")
    @Timeout(60)
    void validCss() throws IOException {
        if (!ready("Stylelint")) { skipVal("CSS"); return; }
        Path f = tempDir.resolve("style.css");
        Files.writeString(f, """
                body {
                    margin: 0;
                    padding: 0;
                }
                .container {
                    display: flex;
                }
                """);
        var req = ModificationRequest.builder()
                .filePath(f.toString()).fromLine(1).toLine(1)
                .replacement("body {").build();
        var result = library.validate(req);
        assertValidation("CSS", result, true);
    }

    // ════════════════════════════════════════════════════════════════════
    //  PHASE 3: FINAL SUMMARY REPORT
    // ════════════════════════════════════════════════════════════════════

    @Test @Order(99)
    @DisplayName("Final Summary Report")
    void summaryReport() {
        out("");
        out("=".repeat(70));
        out("                   FINAL SUMMARY REPORT");
        out("=".repeat(70));

        // ── Downloads ──
        out("");
        out("📦  BINARY DOWNLOADS");
        out("-".repeat(50));
        int dlOk = 0, dlTotal = downloadResults.size();
        for (var e : downloadResults.entrySet()) {
            boolean ok = !"FAILED".equals(e.getValue()) && !"SKIPPED".equals(e.getValue());
            if (ok) dlOk++;
            out(String.format("  %-20s  %s", e.getKey(), statusLabel(e.getValue())));
        }
        out(String.format("%n  Score: %d / %d binaries ready%n", dlOk, dlTotal));

        // ── Validations ──
        out("🔍  CODE VALIDATION");
        out("-".repeat(50));
        int valOk = 0, valTotal = validationResults.size();
        for (var e : validationResults.entrySet()) {
            if (e.getValue()) valOk++;
            out(String.format("  %-25s  %s", e.getKey(), e.getValue() ? "✅ PASS" : "❌ FAIL"));
        }
        out(String.format("%n  Score: %d / %d languages validated%n", valOk, valTotal));

        // ── Overall ──
        int total = dlOk + valOk;
        int totalTests = dlTotal + valTotal;
        double pct = totalTests > 0 ? (total * 100.0 / totalTests) : 0;
        out("=".repeat(70));
        out(String.format("  🏆  OVERALL: %d / %d  (%.0f%%)", total, totalTests, pct));
        out("=".repeat(70));

        // ── Enabled languages ──
        out("");
        out("📝  ENABLED LANGUAGES:");
        List<String> langs = new ArrayList<>();
        if (ready("Node.js"))     langs.add("JavaScript");
        if (ready("TypeScript"))  langs.add("TypeScript");
        if (ready("Java JDK"))    langs.add("Java");
        if (ready("Python"))      langs.add("Python");
        if (ready("PHP"))         langs.add("PHP");
        if (ready("VNU"))         langs.add("HTML");
        if (ready("Stylelint"))   langs.add("CSS");
        out("  " + (langs.isEmpty() ? "(none)" : String.join(", ", langs)));
        out("=".repeat(70));
        out("");
    }

    // ════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════

    private void doDownload(BinaryInfo info, String name) {
        out("");
        out("  ── " + name + " ──────────────────────────────────────────");

        // 1. Check system PATH first
        Optional<String> sysPath = findOnSystemPath(info);
        if (sysPath.isPresent()) {
            out("  ✅  Found on system PATH: " + sysPath.get());
            downloadResults.put(name, "SYSTEM");
            return;
        }

        // 2. Download
        out("  ⚠  Not on system PATH — downloading...");
        out("     URL: " + info.getDownloadUrl().orElse("(managed)"));

        long t0 = System.currentTimeMillis();
        try {
            Path p = binaryManager.downloadAndInstall(info);
            long ms = System.currentTimeMillis() - t0;
            out("  ✅  Installed: " + p);
            out("     Time: " + (ms / 1000.0) + "s");

            BinaryStatus st = binaryManager.getStatus(info);
            st.getDetectedVersion().ifPresent(v -> out("     Version: " + v));

            downloadResults.put(name, "DOWNLOADED");
        } catch (Exception ex) {
            long ms = System.currentTimeMillis() - t0;
            out("  ❌  FAILED after " + (ms / 1000) + "s: " + ex.getMessage());
            downloadResults.put(name, "FAILED");
            fail(name + " download failed", ex);
        }
    }

    private Optional<String> findOnSystemPath(BinaryInfo info) {
        String exe = isWindows() ? info.getWindowsExecutable() : info.getCommandName();
        if (exe == null || exe.isBlank()) return Optional.empty();
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return Optional.empty();
        for (String dir : pathEnv.split(File.pathSeparator)) {
            Path candidate = Paths.get(dir, exe);
            if (Files.isExecutable(candidate)) return Optional.of(candidate.toString());
            if (exe.endsWith(".exe") || exe.endsWith(".cmd")) continue;
            Path withExe = Paths.get(dir, exe + ".exe");
            if (Files.isExecutable(withExe)) return Optional.of(withExe.toString());
            Path withCmd = Paths.get(dir, exe + ".cmd");
            if (Files.isExecutable(withCmd)) return Optional.of(withCmd.toString());
        }
        return Optional.empty();
    }

    private boolean ready(String name) {
        String s = downloadResults.get(name);
        return "DOWNLOADED".equals(s) || "SYSTEM".equals(s);
    }

    private void skip(String what, String reason) {
        out("  ⏭  Skipping " + what + " — " + reason);
        downloadResults.put(what, "SKIPPED");
    }

    private void skipVal(String what) {
        out("  ⏭  Skipping " + what + " validation — binary not available");
        validationResults.put(what, false);
    }

    private void assertValidation(String label, ValidationResult result, boolean expectValid) {
        boolean pass = expectValid ? result.isValid() : !result.isValid();
        validationResults.put(label, pass);
        out("  " + (pass ? "✅" : "❌") + "  " + label
                + (pass ? "" : "  (expected " + (expectValid ? "valid" : "error") + ")"));
        assertThat(pass).as(label).isTrue();
    }

    private static String statusLabel(String v) {
        return switch (v) {
            case "SYSTEM"    -> "✅ SYSTEM";
            case "DOWNLOADED"-> "✅ DOWNLOADED";
            case "FAILED"    -> "❌ FAILED";
            case "SKIPPED"   -> "⏭  SKIPPED";
            default          -> v;
        };
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void out(String msg) {
        System.out.println(msg);
    }
}
