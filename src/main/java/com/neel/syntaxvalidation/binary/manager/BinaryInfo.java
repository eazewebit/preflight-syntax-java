package com.neel.syntaxvalidation.binary.manager;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable metadata describing an external binary dependency required by the
 * syntax validation library.
 *
 * <p>Each {@code BinaryInfo} captures:
 * <ul>
 *   <li>the logical name used internally and in log messages;</li>
 *   <li>the executable name as it appears on the system (e.g. {@code "node.exe"});</li>
 *   <li>the minimum version required, if any;</li>
 *   <li>the official download URL for the binary;</li>
 *   <li>whether the binary is installed via npm (a Node.js package);</li>
 *   <li>which programming languages are enabled when this binary is available.</li>
 * </ul>
 *
 * <p>Instances are created via the static factory methods for each known binary.
 */
public final class BinaryInfo {

    // ----------------------------------------------------------------
    //  Known binary definitions
    // ----------------------------------------------------------------

    /** Node.js runtime – enables JavaScript and TypeScript validation. */
    public static final BinaryInfo NODE = new BinaryInfo(
            "node",
            "node",
            "node.exe",
            "20.0.0",
            resolveNodeDownloadUrl(),
            false,
            null,
            "JavaScript", "TypeScript"
    );

    /** Java compiler ({@code javac}) – enables Java validation. */
    public static final BinaryInfo JAVAC = new BinaryInfo(
            "javac",
            "javac",
            "javac.exe",
            "17.0.0",
            resolveJdkDownloadUrl(),
            false,
            null,
            "Java"
    );

    /** TypeScript compiler – an npm package installed globally. */
    public static final BinaryInfo TSC = new BinaryInfo(
            "tsc",
            "tsc",
            "tsc.cmd",
            "5.0.0",
            "https://registry.npmjs.org/typescript/-/typescript-7.0.2.tgz",
            true,
            "typescript",
            "TypeScript"
    );

    /** Python interpreter – enables Python validation. */
    public static final BinaryInfo PYTHON = new BinaryInfo(
            "python",
            "python3",
            "python.exe",
            "3.10.0",
            resolvePythonDownloadUrl(),
            false,
            null,
            "Python"
    );

    /** PHP CLI – enables PHP validation. */
    public static final BinaryInfo PHP = new BinaryInfo(
            "php",
            "php",
            "php.exe",
            "8.1.0",
            resolvePhpDownloadUrl(),
            false,
            null,
            "PHP"
    );

    /**
     * Nu HTML Checker ({@code vnu.jar}) – enables HTML validation.
     * This is a standalone JAR file requiring a Java runtime.
     */
    public static final BinaryInfo VNU = new BinaryInfo(
            "vnu",
            "vnu",
            "vnu.jar",
            "24.11.11",
            "https://github.com/validator/validator/releases/download/latest/vnu.jar",
            false,
            null,
            "HTML"
    );

    /** Stylelint – an npm package for CSS linting. */
    public static final BinaryInfo STYLELINT = new BinaryInfo(
            "stylelint",
            "stylelint",
            "stylelint.cmd",
            "16.0.0",
            "https://registry.npmjs.org/stylelint/-/stylelint-16.12.0.tgz",
            true,
            "stylelint",
            "CSS"
    );

    /** All known binary definitions. */
    public static final BinaryInfo[] ALL = { NODE, JAVAC, TSC, PYTHON, PHP, VNU, STYLELINT };

    // ----------------------------------------------------------------
    //  Instance fields
    // ----------------------------------------------------------------

    private final String id;
    private final String commandName;
    private final String windowsExecutable;
    private final String minimumVersion;
    private final String downloadUrl;
    private final boolean npmPackage;
    private final String npmPackageName;
    private final String[] enabledLanguages;

    private BinaryInfo(String id, String commandName, String windowsExecutable,
                       String minimumVersion, String downloadUrl,
                       boolean npmPackage, String npmPackageName,
                       String... enabledLanguages) {
        this.id = Objects.requireNonNull(id, "id");
        this.commandName = Objects.requireNonNull(commandName, "commandName");
        this.windowsExecutable = Objects.requireNonNull(windowsExecutable, "windowsExecutable");
        this.minimumVersion = minimumVersion;
        this.downloadUrl = downloadUrl;
        this.npmPackage = npmPackage;
        this.npmPackageName = npmPackageName;
        this.enabledLanguages = enabledLanguages != null ? enabledLanguages.clone() : new String[0];
    }

    // ----------------------------------------------------------------
    //  Accessors
    // ----------------------------------------------------------------

    /** Logical identifier used internally and in log messages. */
    public String getId() { return id; }

    /** The command name as it appears on the system PATH (Unix). */
    public String getCommandName() { return commandName; }

    /** The executable filename on Windows (including {@code .exe} or {@code .cmd}). */
    public String getWindowsExecutable() { return windowsExecutable; }

    /** The minimum required version, or empty if no minimum is specified. */
    public Optional<String> getMinimumVersion() { return Optional.ofNullable(minimumVersion); }

    /** The official download URL, or empty if no direct download is available. */
    public Optional<String> getDownloadUrl() { return Optional.ofNullable(downloadUrl); }

    /** Whether this binary is an npm package (installed via {@code npm install}). */
    public boolean isNpmPackage() { return npmPackage; }

    /** The npm package name, or empty if not an npm package. */
    public Optional<String> getNpmPackageName() { return Optional.ofNullable(npmPackageName); }

    /** The programming languages that are enabled when this binary is available. */
    public String[] getEnabledLanguages() { return enabledLanguages.clone(); }

    /**
     * Returns the expected executable path inside an installation directory.
     *
     * @param installDir the root installation directory.
     * @return the expected path to the executable.
     */
    public Path getInstalledPath(Path installDir) {
        if (isNpmPackage()) {
            String suffix = isWindows() ? ".cmd" : "";
            return installDir.resolve("node_modules").resolve(".bin")
                    .resolve(npmPackageName + suffix);
        }
        if ("vnu".equals(id)) {
            return installDir.resolve("vnu.jar");
        }
        if (isWindows()) {
            return installDir.resolve(windowsExecutable);
        }
        return installDir.resolve("bin").resolve(commandName);
    }

    // ----------------------------------------------------------------
    //  Object methods
    // ----------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BinaryInfo{");
        sb.append("id=").append(id);
        sb.append(", cmd=").append(commandName);
        sb.append(", version>=").append(minimumVersion);
        if (npmPackage) sb.append(", npm=").append(npmPackageName);
        sb.append(", languages=[").append(String.join(", ", enabledLanguages));
        sb.append("]}");
        return sb.toString();
    }

    // ----------------------------------------------------------------
    //  Internal helpers
    // ----------------------------------------------------------------

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String resolveNodeDownloadUrl() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean isArm = arch.contains("aarch64") || arch.contains("arm");

        // Node.js v22.23.2 is the latest LTS version
        String version = "v22.23.2";
        
        if (os.contains("win")) {
            return "https://nodejs.org/dist/" + version + "/node-" + version + "-win-" + (isArm ? "arm64" : "x64") + ".zip";
        } else if (os.contains("mac")) {
            return "https://nodejs.org/dist/" + version + "/node-" + version + "-darwin-" + (isArm ? "arm64" : "x64") + ".tar.gz";
        } else {
            return "https://nodejs.org/dist/" + version + "/node-" + version + "-linux-" + (isArm ? "arm64" : "x64") + ".tar.xz";
        }
    }

    private static String resolvePythonDownloadUrl() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "https://www.python.org/ftp/python/3.14.7/python-3.14.7-amd64.exe";
        }
        // On Linux/macOS, Python is typically installed via the system package manager.
        return null;
    }

    /**
     * Resolves the JDK download URL using Eclipse Adoptium (Temurin) API.
     * <p>
     * Uses the Adoptium API to get the latest LTS JDK (version 21) for the current platform.
     * Downloads are available for Windows, Linux, and macOS (both x64 and ARM64).
     *
     * @return the download URL for JDK, or {@code null} if not available for the platform
     */
    private static String resolveJdkDownloadUrl() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean isArm = arch.contains("aarch64") || arch.contains("arm");

        // Eclipse Adoptium Temurin JDK 25 (LTS)
        // API: https://api.adoptium.net/q/swagger-ui/
        String jdkVersion = "25";  // JDK 25 LTS (latest)
        String imageType = "jdk";
        String vendor = "eclipse";
        String heapSize = "normal";
        String project = "jdk";

        if (os.contains("win")) {
            String archParam = isArm ? "aarch64" : "x64";
            return "https://api.adoptium.net/v3/binary/latest/" + jdkVersion + "/ga/windows/" + archParam + "/" + imageType + "/hotspot/" + heapSize + "/" + vendor;
        } else if (os.contains("mac")) {
            String archParam = isArm ? "aarch64" : "x64";
            return "https://api.adoptium.net/v3/binary/latest/" + jdkVersion + "/ga/mac/" + archParam + "/" + imageType + "/hotspot/" + heapSize + "/" + vendor;
        } else if (os.contains("linux")) {
            String archParam = isArm ? "aarch64" : "x64";
            return "https://api.adoptium.net/v3/binary/latest/" + jdkVersion + "/ga/linux/" + archParam + "/" + imageType + "/hotspot/" + heapSize + "/" + vendor;
        }

        // Unsupported platform
        return null;
    }

    /**
     * Resolves the PHP download URL based on the current platform.
     * <p>
     * Uses different sources for different platforms:
     * <ul>
     *   <li><b>Windows</b>: Official PHP builds from windows.php.net</li>
     *   <li><b>Linux/macOS</b>: Static PHP builds from dl.static-php.dev (cross-platform static binaries)</li>
     * </ul>
     *
     * @return the download URL for PHP CLI, or {@code null} if not available for the platform
     */
    private static String resolvePhpDownloadUrl() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean isArm = arch.contains("aarch64") || arch.contains("arm");

        // PHP versions differ by platform source:
        // - windows.php.net: latest stable 8.3.x (8.4.14)
        // - dl.static-php.dev: latest available 8.3.x (8.4.14)
        // Each platform uses its own latest version for best compatibility

        if (os.contains("win")) {
            // Windows: Official PHP builds from windows.php.net
            // Uses latest stable 8.4.x (vs17 compiler)
            String winVersion = "8.4.24";
            return "https://windows.php.net/downloads/releases/php-" + winVersion + "-Win32-vs17-x64.zip";
        } else if (os.contains("mac")) {
            // macOS: Static PHP builds from dl.static-php.dev (cross-platform static binaries)
            // Uses latest available 8.4.x on static-php.dev
            String macVersion = "8.4.23";
            String macArch = isArm ? "aarch64" : "x86_64";
            return "https://dl.static-php.dev/static-php-cli/common/php-" + macVersion + "-cli-macos-" + macArch + ".tar.gz";
        } else {
            // Linux: Static PHP builds from dl.static-php.dev (cross-platform static binaries)
            // Uses latest available 8.4.x on static-php.dev
            String linuxVersion = "8.4.23";
            String linuxArch = isArm ? "aarch64" : "x86_64";
            return "https://dl.static-php.dev/static-php-cli/common/php-" + linuxVersion + "-cli-linux-" + linuxArch + ".tar.gz";
        }
    }
}
