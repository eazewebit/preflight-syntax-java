# Implementation Guide — Syntax Validation Library

> **Version:** 2.0.0  
> **Purpose:** Comprehensive guide for integrating, extending, and customizing the Syntax Validation Library in your projects.

---

## Table of Contents

1. [Quick Start Integration](#1-quick-start-integration)
2. [Language Support Architecture](#2-language-support-architecture)
3. [Binary Definition & Setup](#3-binary-definition--setup)
   - [3.5 Binary Download Manager](#35-binary-download-manager)
4. [Fallback Mode Deep Dive](#4-fallback-mode-deep-dive)
5. [Adding a New Language](#5-adding-a-new-language)
6. [Customizing Existing Validators](#6-customizing-existing-validators)
7. [Integration Patterns](#7-integration-patterns)
8. [Configuration & Environment](#8-configuration--environment)
9. [Testing Strategies](#9-testing-strategies)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Quick Start Integration

### 1.1 Add Dependency

**Gradle (Groovy DSL):**
```groovy
dependencies {
    implementation 'com.neel:syntaxvalidation:1.0.0'
}
```

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation("com.neel:syntaxvalidation:1.0.0")
}
```

**Maven:**
```xml
<dependency>
    <groupId>com.neel</groupId>
    <artifactId>syntaxvalidation</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 1.2 Basic Usage

```java
import com.neel.syntaxvalidation.SyntaxValidationLibrary;
import com.neel.syntaxvalidation.model.*;

// Initialize the library
SyntaxValidationLibrary library = new SyntaxValidationLibrary();

// Validate a file modification
Path filePath = Path.of("src/main/java/com/example/MyClass.java");
List<ModificationRequest> modifications = List.of(
    new ModificationRequest(10, 15, "    public void newMethod() {\n        // new code\n    }\n")
);

ValidationResult result = library.validateModification(filePath, modifications);

if (result.isValid()) {
    System.out.println("✓ Modification is syntactically valid");
} else {
    System.out.println("✗ Syntax errors found:");
    for (ValidationError error : result.getErrors()) {
        System.out.printf("  Line %d, Col %d: %s%n", 
            error.getLine(), error.getColumn(), error.getMessage());
    }
}
```

### 1.3 Direct Source Validation

```java
// Validate raw source code directly
String javaCode = """
    public class Example {
        public static void main(String[] args) {
            System.out.println("Hello, World!");
        }
    }
    """;

ValidationResult result = library.validateSource(Language.JAVA, javaCode);
```

---

## 2. Language Support Architecture

### 2.1 Supported Languages

| Language | Enum Value | File Extensions | External Binary | Pure-Java Engine |
|----------|------------|-----------------|-----------------|------------------|
| JavaScript | `JAVASCRIPT` | `.js`, `.mjs`, `.cjs`, `.jsx` | `node --check` | `JavaScriptSyntaxEngine` |
| TypeScript | `TYPESCRIPT` | `.ts`, `.tsx`, `.jsx` | `tsc` | `TypeScriptSyntaxEngine` |
| Python | `PYTHON` | `.py` | `python3 -m py_compile` | `PythonSyntaxEngine` |
| Java | `JAVA` | `.java` | `javac` | `JavaSyntaxEngine` |
| CSS | `CSS` | `.css` | `stylelint` | `CssSyntaxEngine` |
| HTML | `HTML` | `.html`, `.htm`, `.xhtml` | `vnu.jar` | `HtmlSyntaxEngine` |
| PHP | `PHP` | `.php`, `.phtml`, `.phps` | `php -l` | `PhpSyntaxEngine` |
| Mixed Content | N/A | `.html`, `.htm`, `.php` | N/A | `MixedContentSyntaxEngine` |

### 2.2 Language Enum

The `Language` enum is the central registry for all supported languages:

```java
public enum Language {
    JAVASCRIPT(Set.of(".js", ".mjs", ".cjs", ".jsx"), "JavaScript"),
    TYPESCRIPT(Set.of(".ts", ".tsx", ".jsx"), "TypeScript"),
    PYTHON(Set.of(".py"), "Python"),
    JAVA(Set.of(".java"), "Java"),
    CSS(Set.of(".css"), "CSS"),
    HTML(Set.of(".html", ".htm", ".xhtml"), "HTML"),
    PHP(Set.of(".php", ".phtml", ".phps"), "PHP");

    private final Set<String> extensions;
    private final String displayName;

    // Constructor and methods...
    
    public static Optional<Language> fromExtension(String extension) {
        // Case-insensitive lookup
    }
    
    public static Optional<Language> fromPath(Path filePath) {
        // Extracts extension and delegates to fromExtension()
    }
}
```

### 2.3 Validator Resolution Flow

```
File Path → Language.fromPath() → Language enum
                                      ↓
                              ValidatorFactory.getValidator(language)
                                      ↓
                              LanguageValidator instance
                                      ↓
                              validate(content) → ValidationResult
```

---

## 3. Binary Definition & Setup

### 3.1 BinaryResolver Architecture

The `BinaryResolver` class handles automatic discovery of external validation tools. It searches in the following order:

1. **Preferred binary path** (explicitly provided)
2. **System property** (`syntaxvalidation.bin.<toolname>`)
3. **Environment variable** (`SYNTAX_VALIDATION_<TOOLNAME>`)
4. **System PATH** (standard executable lookup)

```java
public class BinaryResolver {
    
    /**
     * Resolves the path to an external validation tool.
     * 
     * @param preferredPath Explicit path, or null to search automatically
     * @param binaryName    Name of the binary (e.g., "node", "python3", "javac")
     * @return Optional containing the resolved path, or empty if not found
     */
    public Optional<Path> resolve(String preferredPath, String binaryName) {
        // 1. Check preferred path
        if (preferredPath != null && !preferredPath.isBlank()) {
            Path path = Path.of(preferredPath);
            if (Files.isExecutable(path)) {
                return Optional.of(path);
            }
        }
        
        // 2. Check system property
        String sysProp = System.getProperty("syntaxvalidation.bin." + binaryName);
        if (sysProp != null && !sysProp.isBlank()) {
            Path path = Path.of(sysProp);
            if (Files.isExecutable(path)) {
                return Optional.of(path);
            }
        }
        
        // 3. Check environment variable
        String envVar = System.getenv("SYNTAX_VALIDATION_" + binaryName.toUpperCase());
        if (envVar != null && !envVar.isBlank()) {
            Path path = Path.of(envVar);
            if (Files.isExecutable(path)) {
                return Optional.of(path);
            }
        }
        
        // 4. Search system PATH
        return findOnPath(binaryName);
    }
    
    private Optional<Path> findOnPath(String binaryName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return Optional.empty();
        
        String separator = System.getProperty("os.name").toLowerCase().contains("windows") ? ";" : ":";
        for (String dir : pathEnv.split(separator)) {
            Path candidate = Path.of(dir, binaryName);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
            // Windows: also check with .exe, .cmd, .bat extensions
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                for (String ext : List.of(".exe", ".cmd", ".bat")) {
                    candidate = Path.of(dir, binaryName + ext);
                    if (Files.isExecutable(candidate)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }
}
```

### 3.2 Binary Names by Language

| Language | Binary Name | System Property | Environment Variable |
|----------|-------------|-----------------|----------------------|
| JavaScript | `node` | `syntaxvalidation.bin.node` | `SYNTAX_VALIDATION_NODE` |
| TypeScript | `tsc` | `syntaxvalidation.bin.tsc` | `SYNTAX_VALIDATION_TSC` |
| Python | `python3` | `syntaxvalidation.bin.python3` | `SYNTAX_VALIDATION_PYTHON3` |
| Java | `javac` | `syntaxvalidation.bin.javac` | `SYNTAX_VALIDATION_JAVAC` |
| CSS | `stylelint` | `syntaxvalidation.bin.stylelint` | `SYNTAX_VALIDATION_STYLELINT` |
| HTML | `vnu` | `syntaxvalidation.bin.vnu` | `SYNTAX_VALIDATION_VNU` |
| PHP | `php` | `syntaxvalidation.bin.php` | `SYNTAX_VALIDATION_PHP` |

### 3.3 Setting Up External Binaries

#### JavaScript (Node.js)
```bash
# Install Node.js (includes node binary)
# Windows: Download from https://nodejs.org/
# macOS: brew install node
# Linux: sudo apt install nodejs

# Verify installation
node --version

# Set custom path (optional)
export SYNTAX_VALIDATION_NODE=/usr/local/bin/node
# Or via system property
-Dsyntaxvalidation.bin.node=/usr/local/bin/node
```

#### TypeScript
```bash
# Install TypeScript globally
npm install -g typescript

# Verify installation
tsc --version

# Set custom path (optional)
export SYNTAX_VALIDATION_TSC=/usr/local/bin/tsc
```

#### Python
```bash
# Python 3 is usually pre-installed on Linux/macOS
# Windows: Download from https://www.python.org/

# Verify installation
python3 --version

# Set custom path (optional)
export SYNTAX_VALIDATION_PYTHON3=/usr/bin/python3
```

#### Java (JDK)
```bash
# Install JDK 25+ (for javac)
# Windows: Download from https://adoptium.net/
# macOS: brew install openjdk@21
# Linux: sudo apt install openjdk-25-jdk

# Verify installation
javac --version

# Set custom path (optional)
export SYNTAX_VALIDATION_JAVAC=/usr/lib/jvm/java-21/bin/javac
```

#### CSS (Stylelint)
```bash
# Install stylelint globally
npm install -g stylelint stylelint-config-standard

# Verify installation
stylelint --version

# Set custom path (optional)
export SYNTAX_VALIDATION_STYLELINT=/usr/local/bin/stylelint
```

#### HTML (Nu Html Checker)
```bash
# Download vnu.jar from https://github.com/validator/validator/releases
# Example: vnu.jar_24.10.17.zip

# Extract and set path
export SYNTAX_VALIDATION_VNU=/path/to/vnu.jar

# Or via system property
-Dsyntaxvalidation.bin.vnu=/path/to/vnu.jar
```

#### PHP
```bash
# Install PHP
# Windows: Download from https://windows.php.net/
# macOS: brew install php
# Linux: sudo apt install php-cli

# Verify installation
php --version

# Set custom path (optional)
export SYNTAX_VALIDATION_PHP=/usr/bin/php
```

### 3.4 Programmatic Binary Configuration

```java
// Create validator with explicit binary path
JavaScriptValidator jsValidator = new JavaScriptValidator("/custom/path/to/node");

// Or configure via ValidatorFactory
ValidatorFactory factory = new ValidatorFactory();
// Factory automatically uses BinaryResolver for discovery

// Create library with custom factory
SyntaxValidationLibrary library = new SyntaxValidationLibrary(factory);
```

---

### 3.5 Binary Download Manager

The **Binary Download Manager** provides cross-platform binary download, extraction, and installation capabilities. It handles downloading binaries from official sources, extracting archives, and managing installation paths.

#### 3.5.1 Supported Binaries

| Binary | Version | Platforms | Source |
|--------|---------|-----------|--------|
| **NODE** | v22.23.2 | Windows x64, macOS x64/arm64, Linux x64/arm64 | [nodejs.org](https://nodejs.org) |
| **TSC** (TypeScript) | 7.0.2 | All (npm package) | [npmjs.com](https://www.npmjs.com/package/typescript) |
| **PYTHON** | 3.14.7 | Windows x64 | [python.org](https://www.python.org) |
| **PHP** | 8.4.24 (Windows) / 8.4.23 (Static) | Windows x64, macOS x64/arm64, Linux x64/arm64 | [windows.php.net](https://windows.php.net) / [dl.static-php.dev](https://dl.static-php.dev) |
| **VNU** | 20.6.30 | All (Java JAR) | [GitHub releases](https://github.com/validator/validator) |
| **STYLELINT** | 16.12.0 | All (npm package) | [npmjs.com](https://www.npmjs.com/package/stylelint) |

#### 3.5.2 Quick Start - Download a Binary

```java
import com.neel.syntaxvalidation.binary.manager.*;

// Download Node.js binary
BinaryInfo binary = BinaryInfo.NODE;
DownloadSession session = BinaryManager.download(binary, installDir);

// Wait for completion
session.awaitCompletion();

if (session.isSuccess()) {
    System.out.println("Node.js installed at: " + binary.getExecutablePath(installDir));
} else {
    System.err.println("Download failed: " + session.getErrorMessage());
}
```

#### 3.5.3 Async Download with Progress Tracking

```java
import com.neel.syntaxvalidation.binary.manager.*;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

Path installDir = Path.of("binaries");

// Create progress listener
DownloadProgressListener listener = new DownloadProgressListener() {
    @Override
    public void onProgressUpdate(long bytesRead, long totalBytes) {
        if (totalBytes > 0) {
            int percent = (int) ((bytesRead * 100) / totalBytes);
            System.out.printf("\rDownloading: %d%% (%d/%d bytes)", percent, bytesRead, totalBytes);
        }
    }

    @Override
    public void onDownloadStart(String fileName, long totalBytes) {
        System.out.println("Starting download: " + fileName);
    }

    @DownloadProgressListener.Override
    public void onDownloadComplete(Path downloadedFile) {
        System.out.println("\nDownloaded to: " + downloadedFile);
    }

    @Override
    public void onError(String message, Exception exception) {
        System.err.println("Error: " + message);
    }
};

// Start async download with listener
DownloadSession session = BinaryManager.downloadAsync(
    BinaryInfo.NODE,
    installDir,
    listener
);

// Track progress
while (session.isInProgress()) {
    DownloadSession.DownloadProgress progress = session.getProgress();
    System.out.printf("Progress: %d completed, %d failed, %d bytes downloaded%n",
        progress.getCompletedCount(),
        progress.getFailedCount(),
        progress.getBytesDownloaded()
    );
    Thread.sleep(1000);
}

// Wait for completion with timeout
session.awaitCompletion(5, TimeUnit.MINUTES);
```

#### 3.5.4 Cross-Platform URL Resolution

The Binary Download Manager automatically resolves platform-specific download URLs:

```java
import com.neel.syntaxvalidation.binary.manager.*;

// Get the download URL for the current platform
String nodeUrl = BinaryInfo.NODE.getDownloadUrl();
System.out.println("Node.js URL: " + nodeUrl);

// Platform-specific examples:
// Windows x64: https://nodejs.org/dist/v22.23.2/node-v22.23.2-win-x64.zip
// macOS x64:   https://nodejs.org/dist/v22.23.2/node-v22.23.2-darwin-x64.tar.gz
// macOS ARM64: https://nodejs.org/dist/v22.23.2/node-v22.23.2-darwin-arm64.tar.gz
// Linux x64:   https://nodejs.org/dist/v22.23.2/node-v22.23.2-linux-x64.tar.xz
// Linux ARM64: https://nodejs.org/dist/v22.23.2/node-v22.23.2-linux-arm64.tar.xz
```

#### 3.5.5 Platform Detection

```java
import com.neel.syntaxvalidation.binary.manager.BinaryUtils;

// Detect current platform
String os = BinaryUtils.getOs();           // "windows", "mac", or "linux"
String arch = BinaryUtils.getArch();       // "x64" or "arm64"
boolean isWindows = BinaryUtils.isWindows();
boolean isMac = BinaryUtils.isMac();
boolean isLinux = BinaryUtils.isLinux();

System.out.printf("Platform: %s %s%n", os, arch);
```

#### 3.5.6 Installation Paths

Each binary type has a specific executable path pattern:

```java
import com.neel.syntaxvalidation.binary.manager.*;
import java.nio.file.Path;

Path installDir = Path.of("binaries");

// Node.js - Node executable
Path nodeExe = BinaryInfo.NODE.getExecutablePath(installDir);
// Windows: binaries/node-v22.23.2-win-x64/node.exe
// Linux:   binaries/node-v22.23.2-linux-x64/bin/node

// TypeScript - npm package (node_modules/.bin/tsc)
Path tscExe = BinaryInfo.TSC.getExecutablePath(installDir);
// Windows: binaries/node_modules/.bin/tsc.cmd
// Linux:   binaries/node_modules/.bin/tsc

// Python - Python executable
Path pythonExe = BinaryInfo.PYTHON.getExecutablePath(installDir);
// Windows: binaries/python-3.14.7-amd64.exe

// PHP - PHP executable
Path phpExe = BinaryInfo.PHP.getExecutablePath(installDir);
// Windows: binaries/php-8.4.24-Win32-vs17-x64/php.exe
// Linux:   binaries/php-8.4.23-cli-linux-x86_64/bin/php

// VNU - Java JAR file (run with: java -jar vnu.jar)
Path vnuJar = BinaryInfo.VNU.getExecutablePath(installDir);
// All: binaries/vnu.jar

// Stylelint - npm package (node_modules/.bin/stylelint)
Path stylelintExe = BinaryInfo.STYLELINT.getExecutablePath(installDir);
// Windows: binaries/node_modules/.bin/stylelint.cmd
// Linux:   binaries/node_modules/.bin/stylelint
```

#### 3.5.7 Binary Status Checking

```java
import com.neel.syntaxvalidation.binary.manager.*;
import java.util.Optional;

// Check if a binary is available on the system
BinaryStatus status = BinaryManager.checkStatus(BinaryInfo.NODE);

if (status.isAvailable()) {
    System.out.println("Node.js found at: " + status.getPath());
    System.out.println("Version: " + status.getVersion());
} else {
    System.out.println("Node.js not found. Download required.");
}

// Get status for all binaries
for (BinaryInfo binary : BinaryInfo.values()) {
    BinaryStatus bs = BinaryManager.checkStatus(binary);
    System.out.printf("%-10s: %s%n", binary.name(), 
        bs.isAvailable() ? "✅ " + bs.getPath() : "❌ Not found");
}
```

#### 3.5.8 Batch Download Multiple Binaries

```java
import com.neel.syntaxvalidation.binary.manager.*;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

Path installDir = Path.of("binaries");
List<DownloadSession> sessions = new ArrayList<>();

// Start downloads for multiple binaries
for (BinaryInfo binary : List.of(BinaryInfo.NODE, BinaryInfo.PHP, BinaryInfo.PYTHON)) {
    DownloadSession session = BinaryManager.downloadAsync(binary, installDir, null);
    sessions.add(session);
}

// Wait for all to complete
for (DownloadSession session : sessions) {
    session.awaitCompletion();
    System.out.printf("Session: completed=%d, failed=%d%n",
        session.getProgress().getCompletedCount(),
        session.getProgress().getFailedCount());
}
```

#### 3.5.9 Cancellation Support

```java
import com.neel.syntaxvalidation.binary.manager.*;
import java.nio.file.Path;

Path installDir = Path.of("binaries");
DownloadSession session = BinaryManager.downloadAsync(BinaryInfo.NODE, installDir, null);

// Cancel after 5 seconds
Thread.sleep(5000);
session.cancel();

if (session.isCancelled()) {
    System.out.println("Download was cancelled.");
}

// Or check progress summary
System.out.println(session.getProgressSummary());
// Output: "Download Session: completed=1, failed=0, bytes=15728640"
```

#### 3.5.10 File Extraction

The download manager automatically handles different archive formats:

| Format | Extensions | Extraction |
|--------|-----------|------------|
| ZIP | `.zip` | `java.util.zip.ZipInputStream` |
| TAR.GZ | `.tar.gz`, `.tgz` | `GzipCompressorInputStream` + `TarArchiveInputStream` |
| TAR.XZ | `.tar.xz` | `XZCompressorInputStream` + `TarArchiveInputStream` |

```java
import com.neel.syntaxvalidation.binary.manager.BinaryUtils;
import java.nio.file.Path;

// Extract a downloaded archive
Path archivePath = Path.of("downloads/node-v22.23.2-linux-x64.tar.xz");
Path extractDir = Path.of("binaries/node-v22.23.2-linux-x64");

// Automatic format detection based on file extension
BinaryUtils.extract(archivePath, extractDir);
```

#### 3.5.11 Download URL Reference

| Binary | Platform | Download URL |
|--------|----------|--------------|
| NODE | Windows x64 | `https://nodejs.org/dist/v22.23.2/node-v22.23.2-win-x64.zip` |
| NODE | macOS x64 | `https://nodejs.org/dist/v22.23.2/node-v22.23.2-darwin-x64.tar.gz` |
| NODE | macOS ARM64 | `https://nodejs.org/dist/v22.23.2/node-v22.23.2-darwin-arm64.tar.gz` |
| NODE | Linux x64 | `https://nodejs.org/dist/v22.23.2/node-v22.23.2-linux-x64.tar.xz` |
| NODE | Linux ARM64 | `https://nodejs.org/dist/v22.23.2/node-v22.23.2-linux-arm64.tar.xz` |
| TSC | All | `https://registry.npmjs.org/typescript/-/typescript-7.0.2.tgz` |
| PYTHON | Windows x64 | `https://www.python.org/ftp/python/3.14.7/python-3.14.7-amd64.exe` |
| PHP | Windows x64 | `https://windows.php.net/downloads/releases/php-8.4.24-Win32-vs17-x64.zip` |
| PHP | macOS x64 | `https://dl.static-php.dev/static-php-cli/common/php-8.4.23-cli-macos-x86_64.tar.gz` |
| PHP | macOS ARM64 | `https://dl.static-php.dev/static-php-cli/common/php-8.4.23-cli-macos-aarch64.tar.gz` |
| PHP | Linux x64 | `https://dl.static-php.dev/static-php-cli/common/php-8.4.23-cli-linux-x86_64.tar.gz` |
| PHP | Linux ARM64 | `https://dl.static-php.dev/static-php-cli/common/php-8.4.23-cli-linux-aarch64.tar.gz` |
| VNU | All | `https://github.com/validator/validator/releases/download/20.6.30/vnu.jar_20.6.30.zip` |
| STYLELINT | All | `https://registry.npmjs.org/stylelint/-/stylelint-16.12.0.tgz` |

#### 3.5.12 Integration Example - Full Validation Pipeline

```java
import com.neel.syntaxvalidation.binary.manager.*;
import com.neel.syntaxvalidation.SyntaxValidationLibrary;
import java.nio.file.Path;

public class ValidationPipeline {
    
    private static final Path BINARY_DIR = Path.of("binaries");
    
    public static void main(String[] args) throws Exception {
        // Step 1: Check and download required binaries
        ensureBinaryAvailable(BinaryInfo.NODE);
        ensureBinaryAvailable(BinaryInfo.PHP);
        
        // Step 2: Run validation
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();
        var result = library.validateFile(Path.of("src/main.java"));
        
        System.out.println("Validation: " + (result.isPassed() ? "✅ PASSED" : "❌ FAILED"));
    }
    
    private static void ensureBinaryAvailable(BinaryInfo binary) throws Exception {
        BinaryStatus status = BinaryManager.checkStatus(binary);
        
        if (!status.isAvailable()) {
            System.out.println("Downloading " + binary.name() + "...");
            DownloadSession session = BinaryManager.download(binary, BINARY_DIR);
            session.awaitCompletion();
            
            if (!session.isSuccess()) {
                throw new RuntimeException("Failed to download " + binary.name());
            }
            
            System.out.println("✅ " + binary.name() + " installed at: " + 
                binary.getExecutablePath(BINARY_DIR));
        } else {
            System.out.println("✅ " + binary.name() + " already available: " + 
                status.getPath());
        }
    }
}
```

#### 3.5.13 Error Handling

```java
import com.neel.syntaxvalidation.binary.manager.*;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

try {
    DownloadSession session = BinaryManager.download(BinaryInfo.NODE, Path.of("binaries"));
    
    // Wait with timeout
    session.awaitCompletion(10, java.util.concurrent.TimeUnit.MINUTES);
    
    if (session.isCancelled()) {
        System.out.println("Download was cancelled");
    } else if (!session.isSuccess()) {
        System.err.println("Download failed: " + session.getErrorMessage());
    }
    
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    System.err.println("Download interrupted");
    
} catch (TimeoutException e) {
    System.err.println("Download timed out");
    
} catch (Exception e) {
    System.err.println("Unexpected error: " + e.getMessage());
}
```

---


## 4. Fallback Mode Deep Dive

### 4.1 Fallback Strategy Overview

Every validator implements a **two-phase validation strategy**:

1. **Phase 1: External Binary (Primary)** — Attempts to use the real external tool for deep, specification-level validation
2. **Phase 2: Pure-Java Engine (Fallback)** — If external binary is unavailable, uses embedded pure-Java engine for structural validation

```
validate(content)
    ↓
[External Binary Available?]
    ├─ Yes → Run external tool → Parse output → Return result
    └─ No  → Run pure-Java engine → Return result
```

### 4.2 AbstractLanguageValidator Pattern

The base class implements the fallback pattern:

```java
public abstract class AbstractLanguageValidator implements LanguageValidator {
    
    protected final BinaryResolver binaryResolver;
    protected final ProcessExecutor processExecutor;
    private final String preferredBinaryPath;
    private final String binaryName;
    
    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;
        
        // Phase 1: Attempt external binary
        Optional<String> binary = resolveBinary();
        
        if (binary.isPresent()) {
            try {
                Path tempFile = createTempFile();
                Files.writeString(tempFile, safeContent, StandardCharsets.UTF_8);
                
                List<String> command = buildCommand(binary.get(), tempFile);
                ProcessResult result = processExecutor.execute(command);
                
                ValidationResult externalResult = parseOutput(result, tempFile);
                
                // Clean up temp file
                deleteQuietly(tempFile);
                
                return externalResult;
                
            } catch (IOException | InterruptedException e) {
                // Fall through to embedded engine
            }
        }
        
        // Phase 2: Fallback to pure-Java engine
        return validateWithEmbeddedEngine(safeContent);
    }
    
    protected Optional<String> resolveBinary() {
        return binaryResolver.resolve(preferredBinaryPath, binaryName)
                .map(Path::toString);
    }
    
    // Abstract methods for subclasses
    protected abstract List<String> buildCommand(String binaryPath, Path tempFile);
    protected abstract ValidationResult parseOutput(ProcessResult result, Path tempFile);
    protected abstract ValidationResult validateWithEmbeddedEngine(String content);
    protected abstract String getFileExtension();
}
```

### 4.3 Two-Phase Validators (Java, Python, TypeScript)

Some validators run the pure-Java engine **first** for instant feedback, then optionally run the external binary for deeper analysis:

```java
// JavaValidator example
@Override
public ValidationResult validate(String content) {
    String safeContent = content == null ? "" : content;
    
    // Phase 1: Pure-Java engine (always runs, zero external deps)
    ValidationResult engineResult = JavaSyntaxEngine.validateStatic(safeContent);
    if (!engineResult.isValid()) {
        return engineResult; // Return immediately if syntax errors found
    }
    
    // Phase 2: External binary (optional, for deeper analysis)
    Optional<String> binary = resolveBinary();
    if (binary.isEmpty()) {
        return ValidationResult.valid(
            "Java syntax is valid (validated by the built-in Java syntax engine; "
            + "javac not available for deeper analysis).");
    }
    
    // Run javac for semantic checks
    String javacContent = stripPublicModifier(safeContent);
    return super.validate(javacContent);
}
```

### 4.4 Fallback Engine Capabilities

Each pure-Java engine provides different levels of validation:

| Language | Engine Capabilities |
|----------|---------------------|
| **JavaScript** | Tokenization, bracket balance, statement grammar, function/class syntax, module syntax |
| **TypeScript** | Tokenization, bracket balance, generic syntax, JSX/TSX support, TypeScript keywords |
| **Python** | Full lexer (78 token types), parser, indentation validation, block structure, bracket balance |
| **Java** | Full lexer (Java 25 keywords), 3 pluggable checkers: tokenization errors, delimiter balance, keyword usage |
| **CSS** | Property-value pairs, at-rules, selectors, brace balance, declaration validation |
| **HTML** | Tag matching, attribute validation, void elements, nesting depth |
| **PHP** | Full PHP 8.3+ tokenizer, bracket balance, class/function/control structure grammar |
| **Mixed Content** | Extracts and validates embedded CSS/JS/PHP blocks with line-number remapping |

### 4.5 Customizing Fallback Behavior

#### Disable External Binary (Force Fallback)
```java
// Create validator with null binary path (forces fallback)
JavaScriptValidator validator = new JavaScriptValidator(null);

// Or set environment variable to empty
System.setProperty("syntaxvalidation.bin.node", "");
```

#### Custom Fallback Engine
```java
public class CustomJavaScriptValidator extends AbstractLanguageValidator {
    
    private final CustomSyntaxEngine customEngine;
    
    @Override
    protected ValidationResult validateWithEmbeddedEngine(String content) {
        // Use custom engine instead of default
        return customEngine.validate(content);
    }
    
    // ... implement other abstract methods
}
```

#### Priority Override (External First vs Engine First)
```java
public class EngineFirstValidator extends AbstractLanguageValidator {
    
    @Override
    public ValidationResult validate(String content) {
        // Always run engine first
        ValidationResult engineResult = validateWithEmbeddedEngine(content);
        
        // Only run external binary if engine passes
        if (engineResult.isValid()) {
            Optional<String> binary = resolveBinary();
            if (binary.isPresent()) {
                // Run external binary for deeper analysis
                return super.validate(content);
            }
        }
        
        return engineResult;
    }
}
```

---

## 5. Adding a New Language

### 5.1 Step-by-Step Guide

#### Step 1: Add Language Enum Value

```java
// In Language.java
public enum Language {
    // ... existing languages ...
    
    RUST(Set.of(".rs"), "Rust"),
    GO(Set.of(".go"), "Go"),
    RUBY(Set.of(".rb"), "Ruby");
    
    // Update fromExtension() to handle new extensions
}
```

#### Step 2: Create Validator Class

```java
package com.neel.syntaxvalidation.validator.rust;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;

public class RustValidator extends AbstractLanguageValidator {
    
    static final String BINARY_NAME = "rustc";
    
    public RustValidator() {
        this(null);
    }
    
    public RustValidator(String preferredBinaryPath) {
        super(preferredBinaryPath, BINARY_NAME);
    }
    
    public RustValidator(String preferredBinaryPath, 
                         BinaryResolver binaryResolver,
                         ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
    }
    
    @Override
    public Language getLanguage() {
        return Language.RUST;
    }
    
    @Override
    protected String getFileExtension() {
        return ".rs";
    }
    
    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        // rustc --edition 2021 --check <file>
        return List.of(binaryPath, "--edition", "2021", "--check", tempFile.toString());
    }
    
    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        // Parse rustc output
        return RustcOutputParser.parse(result.stderr());
    }
    
    @Override
    protected ValidationResult validateWithEmbeddedEngine(String content) {
        // Use pure-Java fallback engine
        return RustSyntaxEngine.getInstance().validate(content);
    }
    
    @Override
    protected String binaryNotFoundMessage() {
        return "rustc is not installed or could not be resolved — "
            + "falling back to the built-in Rust syntax engine. "
            + "For full Rust validation, install Rust from https://rustup.rs/";
    }
}
```

#### Step 3: Create Pure-Java Fallback Engine

```java
package com.neel.syntaxvalidation.validator.rust;

import com.neel.syntaxvalidation.model.ValidationResult;

public class RustSyntaxEngine {
    
    private static final RustSyntaxEngine INSTANCE = new RustSyntaxEngine();
    
    public static RustSyntaxEngine getInstance() {
        return INSTANCE;
    }
    
    public ValidationResult validate(String content) {
        if (content == null || content.isBlank()) {
            return ValidationResult.valid("Empty Rust source is valid.");
        }
        
        // Phase 1: Tokenization
        RustLexer lexer = new RustLexer(content);
        List<RustToken> tokens = lexer.tokenize();
        
        // Phase 2: Structural validation
        List<ValidationError> errors = new ArrayList<>();
        
        // Check bracket balance
        errors.addAll(checkBracketBalance(tokens));
        
        // Check string literal termination
        errors.addAll(checkStringLiterals(tokens));
        
        // Check common Rust patterns
        errors.addAll(checkRustPatterns(tokens));
        
        if (errors.isEmpty()) {
            return ValidationResult.valid("Rust syntax is valid.");
        } else {
            return ValidationResult.invalid("Rust syntax errors found.", errors);
        }
    }
    
    private List<ValidationError> checkBracketBalance(List<RustToken> tokens) {
        // Implementation: track (), {}, [] nesting
    }
    
    private List<ValidationError> checkStringLiterals(List<RustToken> tokens) {
        // Implementation: verify string termination
    }
    
    private List<ValidationError> checkRustPatterns(List<RustToken> tokens) {
        // Implementation: validate fn, struct, enum, impl blocks
    }
}
```

#### Step 4: Create Output Parser (for external binary)

```java
package com.neel.syntaxvalidation.validator.rust;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

public class RustcOutputParser {
    
    // Example rustc error format:
    // error[E0308]: mismatched types
    //  --> src/main.rs:5:12
    //   |
    // 5 |     let x: i32 = "hello";
    //   |            ---   ^^^^^^^ expected `i32`, found `&str`
    //   |            |
    //   |            expected due to this
    
    private static final Pattern ERROR_PATTERN = 
        Pattern.compile("^error\\[(E\\d+)\\]:\\s+(.+)$");
    private static final Pattern LOCATION_PATTERN = 
        Pattern.compile("^\\s*-->\\s+(.+):(\\d+):(\\d+)$");
    
    public static ValidationResult parse(String output) {
        if (output == null || output.isBlank()) {
            return ValidationResult.valid("No errors found.");
        }
        
        List<ValidationError> errors = new ArrayList<>();
        String[] lines = output.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            Matcher errorMatcher = ERROR_PATTERN.matcher(lines[i]);
            if (errorMatcher.find()) {
                String errorCode = errorMatcher.group(1);
                String message = errorMatcher.group(2);
                
                // Look for location on next line
                int line = -1, column = -1;
                if (i + 1 < lines.length) {
                    Matcher locMatcher = LOCATION_PATTERN.matcher(lines[i + 1]);
                    if (locMatcher.find()) {
                        line = Integer.parseInt(locMatcher.group(2));
                        column = Integer.parseInt(locMatcher.group(3));
                    }
                }
                
                errors.add(new ValidationError(line, column, 
                    "[" + errorCode + "] " + message, output));
            }
        }
        
        if (errors.isEmpty()) {
            return ValidationResult.valid("No parseable errors found.");
        }
        
        return ValidationResult.invalid(
            errors.size() + " error(s) found.", errors);
    }
}
```

#### Step 5: Create Lexer (for pure-Java engine)

```java
package com.neel.syntaxvalidation.validator.rust;

public class RustLexer {
    
    private final String source;
    private int pos = 0;
    private int line = 1;
    private int column = 1;
    
    public RustLexer(String source) {
        this.source = source;
    }
    
    public List<RustToken> tokenize() {
        List<RustToken> tokens = new ArrayList<>();
        
        while (pos < source.length()) {
            skipWhitespace();
            if (pos >= source.length()) break;
            
            char c = source.charAt(pos);
            
            if (Character.isLetter(c) || c == '_') {
                tokens.add(readIdentifierOrKeyword());
            } else if (Character.isDigit(c)) {
                tokens.add(readNumber());
            } else if (c == '"' || c == '\'') {
                tokens.add(readStringOrChar());
            } else if (c == '/' && peek() == '/') {
                tokens.add(readLineComment());
            } else if (c == '/' && peek() == '*') {
                tokens.add(readBlockComment());
            } else {
                tokens.add(readOperatorOrPunctuation());
            }
        }
        
        tokens.add(new RustToken(RustTokenType.EOF, "", line, column));
        return tokens;
    }
    
    // Helper methods for reading different token types...
}
```

#### Step 6: Register in ValidatorFactory

```java
// In ValidatorFactory.java
public class ValidatorFactory {
    
    private final Map<Language, LanguageValidator> validators = new EnumMap<>(Language.class);
    
    public ValidatorFactory() {
        // ... existing validators ...
        
        validators.put(Language.RUST, new RustValidator());
    }
}
```

#### Step 7: Write Tests

```java
package com.neel.syntaxvalidation.validator.rust;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RustValidatorTest {
    
    @Test
    void validRustCode() {
        RustValidator validator = new RustValidator();
        String code = """
            fn main() {
                println!("Hello, world!");
            }
            """;
        
        ValidationResult result = validator.validate(code);
        assertTrue(result.isValid());
    }
    
    @Test
    void invalidRustCode_missingClosingBrace() {
        RustValidator validator = new RustValidator();
        String code = """
            fn main() {
                println!("Hello, world!");
            """;  // Missing closing brace
        
        ValidationResult result = validator.validate(code);
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }
    
    @Test
    void fallbackWhenBinaryNotAvailable() {
        // Force fallback by providing invalid path
        RustValidator validator = new RustValidator("/nonexistent/rustc");
        String code = "fn main() {}";
        
        ValidationResult result = validator.validate(code);
        // Should still work via fallback engine
        assertTrue(result.isValid());
    }
}
```

### 5.2 Advanced: Modular Engine Architecture

For complex languages, follow the Java validator's modular pattern:

```
Validator
    ↓
SyntaxEngine
    ↓
Lexer → Token Stream → Checker Pipeline → ValidationResult
                    ↓
            [Checker 1] → errors
            [Checker 2] → errors
            [Checker N] → errors
```

```java
// SyntaxChecker functional interface
@FunctionalInterface
public interface SyntaxChecker {
    List<ValidationError> check(List<RustToken> tokens);
}

// RustSyntaxEngine with pluggable checkers
public class RustSyntaxEngine {
    
    private final List<SyntaxChecker> defaultCheckers = List.of(
        new TokenizationErrorChecker(),
        new DelimiterBalanceChecker(),
        new PatternMatchingChecker(),
        new LifetimeChecker()
    );
    
    public ValidationResult validate(String content) {
        List<RustToken> tokens = new RustLexer(content).tokenize();
        
        List<ValidationError> allErrors = new ArrayList<>();
        for (SyntaxChecker checker : defaultCheckers) {
            allErrors.addAll(checker.check(tokens));
        }
        
        return allErrors.isEmpty() 
            ? ValidationResult.valid("Valid")
            : ValidationResult.invalid("Errors found", allErrors);
    }
}
```

---

## 6. Customizing Existing Validators

### 6.1 Extending AbstractLanguageValidator

```java
public class CustomCssValidator extends CssValidator {
    
    private final CustomCssRules customRules;
    
    public CustomCssValidator(CustomCssRules rules) {
        super();
        this.customRules = rules;
    }
    
    @Override
    protected ValidationResult validateWithEmbeddedEngine(String content) {
        // Run default engine first
        ValidationResult defaultResult = super.validateWithEmbeddedEngine(content);
        
        if (!defaultResult.isValid()) {
            return defaultResult;
        }
        
        // Apply custom rules
        List<ValidationError> customErrors = customRules.validate(content);
        
        if (customErrors.isEmpty()) {
            return defaultResult;
        }
        
        // Merge errors
        List<ValidationError> allErrors = new ArrayList<>(defaultResult.getErrors());
        allErrors.addAll(customErrors);
        
        return ValidationResult.invalid("Custom CSS rules violated.", allErrors);
    }
}
```

### 6.2 Custom Syntax Checker (Java Example)

```java
// Create custom checker
public class CustomSecurityChecker implements SyntaxChecker {
    
    @Override
    public List<ValidationError> check(List<JavaToken> tokens) {
        List<ValidationError> errors = new ArrayList<>();
        
        for (int i = 0; i < tokens.size(); i++) {
            JavaToken token = tokens.get(i);
            
            // Check for System.exit() calls
            if (token.type() == JavaTokenType.IDENTIFIER 
                && token.text().equals("System")) {
                if (i + 2 < tokens.size() 
                    && tokens.get(i + 1).text().equals(".")
                    && tokens.get(i + 2).text().equals("exit")) {
                    errors.add(new ValidationError(
                        token.line(), token.column(),
                        "System.exit() is not allowed in this context.",
                        null
                    ));
                }
            }
        }
        
        return errors;
    }
}

// Use custom checker in validator
public class SecureJavaValidator extends JavaValidator {
    
    @Override
    protected ValidationResult validateWithEmbeddedEngine(String content) {
        // Create engine with custom checker pipeline
        JavaSyntaxEngine engine = new JavaSyntaxEngine(
            new TokenizationErrorChecker(),
            new DelimiterBalanceChecker(),
            new KeywordUsageChecker(),
            new CustomSecurityChecker()  // Add custom checker
        );
        
        return engine.validate(content);
    }
}
```

### 6.3 Custom Output Parser

```java
public class CustomNodeOutputParser {
    
    public static ValidationResult parse(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return ValidationResult.valid("No errors.");
        }
        
        List<ValidationError> errors = new ArrayList<>();
        
        // Custom parsing logic for node --check output
        // Example: "SyntaxError: Unexpected token '}'"
        Pattern pattern = Pattern.compile("^(.+?):\\s+(.+?)$");
        
        for (String line : stderr.split("\n")) {
            Matcher matcher = pattern.matcher(line.trim());
            if (matcher.find()) {
                String type = matcher.group(1);
                String message = matcher.group(2);
                
                // Extract line/column if available
                int lineNum = extractLine(line);
                int colNum = extractColumn(line);
                
                errors.add(new ValidationError(lineNum, colNum, message, line));
            }
        }
        
        return errors.isEmpty() 
            ? ValidationResult.valid("No parseable errors.")
            : ValidationResult.invalid(errors.size() + " error(s).", errors);
    }
}
```

---

## 7. Integration Patterns

### 7.1 CI/CD Pipeline Integration

```java
public class CIPipelineValidator {
    
    private final SyntaxValidationLibrary library;
    
    public CIPipelineValidator() {
        this.library = new SyntaxValidationLibrary();
    }
    
    public boolean validatePullRequest(List<Path> changedFiles) {
        boolean allValid = true;
        
        for (Path file : changedFiles) {
            String content = readFile(file);
            Language language = Language.fromPath(file).orElse(null);
            
            if (language == null) {
                System.out.println("⚠ Skipping unsupported file: " + file);
                continue;
            }
            
            ValidationResult result = library.validateSource(language, content);
            
            if (!result.isValid()) {
                allValid = false;
                System.out.println("✗ " + file + ":");
                for (ValidationError error : result.getErrors()) {
                    System.out.printf("  Line %d: %s%n", 
                        error.getLine(), error.getMessage());
                }
            } else {
                System.out.println("✓ " + file);
            }
        }
        
        return allValid;
    }
}
```

### 7.2 IDE Plugin Integration

```java
public class IDEValidatorService {
    
    private final SyntaxValidationLibrary library;
    private final Map<Path, ValidationResult> cache = new ConcurrentHashMap<>();
    
    public IDEValidatorService() {
        this.library = new SyntaxValidationLibrary();
    }
    
    public ValidationResult validateDocument(Path filePath, String content) {
        // Check cache first
        ValidationResult cached = cache.get(filePath);
        if (cached != null && cached.getContentHash().equals(hash(content))) {
            return cached;
        }
        
        // Validate
        Language language = Language.fromPath(filePath).orElse(null);
        if (language == null) {
            return ValidationResult.valid("Unsupported language.");
        }
        
        ValidationResult result = library.validateSource(language, content);
        
        // Update cache
        cache.put(filePath, result);
        
        return result;
    }
    
    public List<Diagnostic> toDiagnostics(ValidationResult result) {
        return result.getErrors().stream()
            .map(error -> new Diagnostic(
                new Range(
                    new Position(error.getLine() - 1, error.getColumn() - 1),
                    new Position(error.getLine() - 1, error.getColumn())
                ),
                error.getMessage(),
                DiagnosticSeverity.Error
            ))
            .collect(Collectors.toList());
    }
}
```

### 7.3 Agent Integration (AI Code Assistant)

```java
public class AICodeAssistant {
    
    private final SyntaxValidationLibrary validator;
    
    public AICodeAssistant() {
        this.validator = new SyntaxValidationLibrary();
    }
    
    public String applyModificationWithValidation(
            String originalCode, 
            Language language,
            List<ModificationRequest> modifications) {
        
        // Apply modifications
        String modifiedCode = ModificationApplier.apply(originalCode, modifications);
        
        // Validate result
        ValidationResult result = validator.validateSource(language, modifiedCode);
        
        if (result.isValid()) {
            return modifiedCode;
        } else {
            // Rollback or attempt fix
            throw new ValidationException(
                "Modification introduces syntax errors", 
                result.getErrors()
            );
        }
    }
    
    public ValidationResult suggestFix(
            String code, 
            Language language, 
            ValidationError error) {
        
        // Analyze error and suggest fix
        String suggestion = analyzeError(code, error);
        
        // Validate suggestion
        String fixedCode = applySuggestion(code, error, suggestion);
        return validator.validateSource(language, fixedCode);
    }
}
```

### 7.4 Batch Validation

```java
public class BatchValidator {
    
    private final SyntaxValidationLibrary library;
    private final ExecutorService executor;
    
    public BatchValidator(int threadCount) {
        this.library = new SyntaxValidationLibrary();
        this.executor = Executors.newFixedThreadPool(threadCount);
    }
    
    public Map<Path, ValidationResult> validateBatch(List<Path> files) {
        List<CompletableFuture<Map.Entry<Path, ValidationResult>>> futures = 
            files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> {
                    String content = readFile(file);
                    Language lang = Language.fromPath(file).orElse(null);
                    ValidationResult result = lang != null 
                        ? library.validateSource(lang, content)
                        : ValidationResult.valid("Unsupported");
                    return Map.entry(file, result);
                }, executor))
                .collect(Collectors.toList());
        
        return futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toMap(
                Map.Entry::getKey, 
                Map.Entry::getValue
            ));
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
```

---

## 8. Configuration & Environment

### 8.1 System Properties

| Property | Description | Default |
|----------|-------------|---------|
| `syntaxvalidation.bin.<tool>` | Custom path to external binary | None |
| `syntaxvalidation.timeout.<lang>` | Validation timeout in seconds | 60 |
| `syntaxvalidation.cache.enabled` | Enable file cache | true |
| `syntaxvalidation.cache.maxSize` | Maximum cache entries | 1000 |
| `syntaxvalidation.fallback.only` | Force fallback mode for all languages | false |

### 8.2 Environment Variables

| Variable | Description |
|----------|-------------|
| `SYNTAX_VALIDATION_<TOOL>` | Path to external binary (uppercase) |
| `SYNTAX_VALIDATION_TIMEOUT` | Global timeout in seconds |
| `SYNTAX_VALIDATION_CACHE_DIR` | Cache directory location |

### 8.3 Configuration File (Optional)

Create `syntaxvalidation.properties` in classpath:

```properties
# Binary paths
syntaxvalidation.bin.node=/usr/local/bin/node
syntaxvalidation.bin.python3=/usr/bin/python3
syntaxvalidation.bin.javac=/usr/lib/jvm/java-21/bin/javac

# Timeouts
syntaxvalidation.timeout.javascript=30
syntaxvalidation.timeout.python=60
syntaxvalidation.timeout.java=120

# Cache
syntaxvalidation.cache.enabled=true
syntaxvalidation.cache.maxSize=5000

# Fallback
syntaxvalidation.fallback.only=false
```

Load configuration:

```java
public class ConfigurableValidator {
    
    public SyntaxValidationLibrary createFromConfig() {
        Properties props = loadProperties("syntaxvalidation.properties");
        
        // Apply system properties
        props.forEach((key, value) -> 
            System.setProperty(key.toString(), value.toString()));
        
        return new SyntaxValidationLibrary();
    }
    
    private Properties loadProperties(String resource) {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(resource)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // Log warning
        }
        return props;
    }
}
```

---

## 9. Testing Strategies

### 9.1 Unit Testing Validators

```java
class JavaScriptValidatorTest {
    
    private JavaScriptValidator validator;
    
    @BeforeEach
    void setUp() {
        // Use null to force fallback mode in tests
        validator = new JavaScriptValidator(null);
    }
    
    @Test
    void validJavaScript() {
        String code = "const x = 1;\nconsole.log(x);";
        ValidationResult result = validator.validate(code);
        assertTrue(result.isValid());
    }
    
    @Test
    void invalidJavaScript_unclosedBrace() {
        String code = "function foo() {\n  return 1;";
        ValidationResult result = validator.validate(code);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.getMessage().contains("brace")));
    }
    
    @Test
    void emptySource() {
        ValidationResult result = validator.validate("");
        assertTrue(result.isValid());
    }
    
    @Test
    void nullSource() {
        ValidationResult result = validator.validate(null);
        assertTrue(result.isValid());
    }
}
```

### 9.2 Integration Testing with External Binaries

```java
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class JavaScriptValidatorIntegrationTest {
    
    @Test
    void validatesWithNodeBinary() {
        // Only runs when node is available
        Assumptions.assumeTrue(isBinaryAvailable("node"));
        
        JavaScriptValidator validator = new JavaScriptValidator();
        String code = "const x = 1;\nconsole.log(x);";
        
        ValidationResult result = validator.validate(code);
        assertTrue(result.isValid());
    }
    
    @Test
    void detectsNodeErrors() {
        Assumptions.assumeTrue(isBinaryAvailable("node"));
        
        JavaScriptValidator validator = new JavaScriptValidator();
        String code = "const x = ;\nconsole.log(x);";
        
        ValidationResult result = validator.validate(code);
        assertFalse(result.isValid());
    }
    
    private boolean isBinaryAvailable(String name) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{name, "--version"});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 9.3 Mocking External Dependencies

```java
class CssValidatorTest {
    
    @Test
    void fallsbackWhenBinaryNotFound() {
        BinaryResolver mockResolver = mock(BinaryResolver.class);
        when(mockResolver.resolve(any(), eq("stylelint")))
            .thenReturn(Optional.empty());
        
        ProcessExecutor mockExecutor = mock(ProcessExecutor.class);
        
        CssValidator validator = new CssValidator(
            null, mockResolver, mockExecutor);
        
        String css = "body { color: red; }";
        ValidationResult result = validator.validate(css);
        
        assertTrue(result.isValid());
        assertTrue(result.getMessage().contains("fallback"));
    }
    
    @Test
    void usesBinaryWhenAvailable() throws Exception {
        BinaryResolver mockResolver = mock(BinaryResolver.class);
        when(mockResolver.resolve(any(), eq("stylelint")))
            .thenReturn(Optional.of(Path.of("/usr/bin/stylelint")));
        
        ProcessExecutor mockExecutor = mock(ProcessExecutor.class);
        when(mockExecutor.execute(any())).thenReturn(
            new ProcessResult(0, "[]", "", false));
        
        CssValidator validator = new CssValidator(
            null, mockResolver, mockExecutor);
        
        String css = "body { color: red; }";
        ValidationResult result = validator.validate(css);
        
        assertTrue(result.isValid());
        verify(mockExecutor).execute(any());
    }
}
```

### 9.4 Testing Custom Validators

```java
class CustomRustValidatorTest {
    
    @Test
    void customCheckerDetectsIssues() {
        RustSyntaxEngine engine = new RustSyntaxEngine(
            new CustomUnsafeChecker()
        );
        
        String rustCode = """
            fn main() {
                unsafe {
                    let x = 5;
                }
            }
            """;
        
        ValidationResult result = engine.validate(rustCode);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.getMessage().contains("unsafe")));
    }
}
```

---

## 10. Troubleshooting

### 10.1 Common Issues

#### Binary Not Found
```
Problem: "node is not installed or could not be resolved"
Solution:
1. Verify installation: `node --version`
2. Check PATH: `echo $PATH` (Linux/macOS) or `echo %PATH%` (Windows)
3. Set explicit path: `export SYNTAX_VALIDATION_NODE=/path/to/node`
4. Or pass to validator: `new JavaScriptValidator("/path/to/node")`
```

#### Validation Timeout
```
Problem: "Validation timed out"
Solution:
1. Increase timeout: `-Dsyntaxvalidation.timeout.java=120`
2. Check for infinite loops in code being validated
3. Use fallback mode: `new JavaScriptValidator(null)`
```

#### Incorrect Line Numbers
```
Problem: Error line numbers don't match source
Solution:
1. Ensure 1-based line numbering (library standard)
2. Check for CRLF vs LF line endings
3. Verify ModificationApplier line calculations
```

### 10.2 Debug Logging

Enable debug logging:

```java
// Via system property
System.setProperty("syntaxvalidation.debug", "true");

// Or via logging framework
Logger logger = Logger.getLogger("com.neel.syntaxvalidation");
logger.setLevel(Level.FINE);
```

### 10.3 Performance Tuning

```java
// Disable cache for single-use scenarios
System.setProperty("syntaxvalidation.cache.enabled", "false");

// Increase cache for batch processing
System.setProperty("syntaxvalidation.cache.maxSize", "10000");

// Use thread pool for parallel validation
ExecutorService executor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);
```

---

## Appendix A: Complete Example - Adding Ruby Support

See [Section 5.1](#51-step-by-step-guide) for the complete walkthrough. Key files to create:

1. `Language.java` - Add `RUBY` enum value
2. `validator/ruby/RubyValidator.java` - Main validator
3. `validator/ruby/RubySyntaxEngine.java` - Pure-Java engine
4. `validator/ruby/RubyLexer.java` - Tokenizer
5. `validator/ruby/RubyToken.java` - Token model
6. `validator/ruby/RubyTokenType.java` - Token types
7. `validator/ruby/RubyOutputParser.java` - External tool parser
8. `ValidatorFactory.java` - Register validator
9. Test files in `test/java/.../validator/ruby/`

---

## Appendix B: Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    SyntaxValidationLibrary                      │
│                         (Facade)                                │
└───────────────────────────────┬─────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐      ┌───────────────┐      ┌───────────────┐
│  Validator    │      │  Modification │      │   FileCache   │
│   Factory     │      │    Applier    │      │               │
└───────┬───────┘      └───────────────┘      └───────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│                   LanguageValidator (Interface)                  │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│               AbstractLanguageValidator (Base Class)             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ BinaryResolver  │  │ ProcessExecutor │  │  Temp File Mgmt │ │
│  └────────┬────────┘  └────────┬────────┘  └─────────────────┘ │
│           │                    │                                │
│           ▼                    ▼                                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              validate(content)                           │   │
│  │  ┌─────────────────────────────────────────────────┐    │   │
│  │  │ Phase 1: External Binary (Primary)              │    │   │
│  │  │  - Resolve binary via BinaryResolver            │    │   │
│  │  │  - Execute via ProcessExecutor                  │    │   │
│  │  │  - Parse output via OutputParser                │    │   │
│  │  └─────────────────────────────────────────────────┘    │   │
│  │  ┌─────────────────────────────────────────────────┐    │   │
│  │  │ Phase 2: Pure-Java Engine (Fallback)            │    │   │
│  │  │  - Run embedded SyntaxEngine                    │    │   │
│  │  │  - Zero external dependencies                   │    │   │
│  │  └─────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────┘   │
└───────────────────────────────┬─────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐      ┌───────────────┐      ┌───────────────┐
│  JavaScript   │      │    Python     │      │     Java      │
│  Validator    │      │   Validator   │      │   Validator   │
│               │      │               │      │               │
│ ┌───────────┐ │      │ ┌───────────┐ │      │ ┌───────────┐ │
│ │  node     │ │      │ │  python3  │ │      │ │   javac   │ │
│ │ --check   │ │      │ │-m compile │ │      │ │  -proc:   │ │
│ └───────────┘ │      │ └───────────┘ │      │ │  none     │ │
│ ┌───────────┐ │      │ ┌───────────┐ │      │ └───────────┘ │
│ │ JS Syntax │ │      │ │ Python    │ │      │ ┌───────────┐ │
│ │  Engine   │ │      │ │  Engine   │ │      │ │ Java      │ │
│ └───────────┘ │      │ └───────────┘ │      │ │  Engine   │ │
└───────────────┘      └───────────────┘      │ │ + Checkers│ │
                                              │ └───────────┘ │
                                              └───────────────┘
```

---

## Appendix C: Quick Reference Card

### Adding a New Language (Checklist)

- [ ] Add enum value to `Language.java` with extensions
- [ ] Create `validator/<lang>/<Lang>Validator.java`
- [ ] Create `validator/<lang>/<Lang>SyntaxEngine.java`
- [ ] (Optional) Create `validator/<lang>/<Lang>Lexer.java`
- [ ] (Optional) Create `validator/<lang>/<Lang>Token.java`
- [ ] (Optional) Create `validator/<lang>/<Lang>TokenType.java`
- [ ] Create `validator/<lang>/<Lang>OutputParser.java`
- [ ] Register in `ValidatorFactory.java`
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Update documentation

### Binary Configuration (Quick Reference)

```bash
# JavaScript
export SYNTAX_VALIDATION_NODE=/usr/local/bin/node

# TypeScript
export SYNTAX_VALIDATION_TSC=/usr/local/bin/tsc

# Python
export SYNTAX_VALIDATION_PYTHON3=/usr/bin/python3

# Java
export SYNTAX_VALIDATION_JAVAC=/usr/lib/jvm/java-21/bin/javac

# CSS
export SYNTAX_VALIDATION_STYLELINT=/usr/local/bin/stylelint

# HTML
export SYNTAX_VALIDATION_VNU=/path/to/vnu.jar

# PHP
export SYNTAX_VALIDATION_PHP=/usr/bin/php
```

### Validation Modes

| Mode | Use Case | Setup |
|------|----------|-------|
| **External Binary** | Deep, spec-level validation | Install tool, ensure on PATH |
| **Pure-Java Fallback** | Offline, fast structural checks | No setup required |
| **Two-Phase** | Instant feedback + deep analysis | Both available |
| **Force Fallback** | Testing, CI without tools | Pass `null` as binary path |

---

**End of Implementation Guide**

*For more information, see the [README.md](README.md) and [TECHNICAL_INFORMATION.md](TECHNICAL_INFORMATION.md).*
