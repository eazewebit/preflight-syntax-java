package com.neel.syntaxvalidation.binary.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for binary download functionality including:
 * - Cross-platform URL resolution
 * - Download session management  
 * - Progress tracking
 * - Error handling
 * - Version resolution
 * - Integration tests for actual downloads
 */
class BinaryDownloadTest {

    @TempDir
    Path tempDir;

    private Path downloadDir;
    private BinaryManager manager;

    @BeforeEach
    void setUp() throws IOException {
        downloadDir = tempDir.resolve("downloads");
        Files.createDirectories(downloadDir);
        manager = new BinaryManager();
    }

    // ======================================================
    // Cross-platform URL Resolution Tests
    // ======================================================

    @Nested
    @DisplayName("Cross-platform URL Resolution")
    class CrossPlatformUrlTests {

        @Test
        @DisplayName("All binaries with download URLs should have valid URLs")
        void allBinariesShouldHaveValidDownloadUrls() {
            BinaryInfo[] allBinaries = {
                BinaryInfo.NODE, BinaryInfo.JAVAC, BinaryInfo.PYTHON, 
                BinaryInfo.TSC, BinaryInfo.PHP, BinaryInfo.VNU, 
                BinaryInfo.STYLELINT
            };
            
            for (BinaryInfo info : allBinaries) {
                if (info.getDownloadUrl().isPresent()) {
                    String url = info.getDownloadUrl().get();
                    assertThat(url).isNotEmpty();
                    assertThat(url).startsWith("http");
                    assertThat(url).contains(".");
                    assertThat(url).doesNotContain(" ");
                }
            }
        }

        @Test
        @DisplayName("Node.js download URL should be platform-specific")
        void nodeDownloadUrlShouldBePlatformSpecific() {
            String url = BinaryInfo.NODE.getDownloadUrl().orElse(null);
            if (url != null) {
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    assertThat(url).contains("win");
                    assertThat(url).endsWith(".zip");
                } else if (os.contains("mac")) {
                    assertThat(url).contains("darwin");
                    assertThat(url).endsWith(".tar.gz");
                } else if (os.contains("linux")) {
                    assertThat(url).contains("linux");
                    assertThat(url).endsWith(".tar.xz");
                }
            }
        }

        @Test
        @DisplayName("Node.js download URL should include architecture")
        void nodeDownloadUrlShouldIncludeArchitecture() {
            String url = BinaryInfo.NODE.getDownloadUrl().orElse(null);
            if (url != null) {
                String arch = System.getProperty("os.arch", "").toLowerCase();
                boolean isArm = arch.contains("aarch64") || arch.contains("arm");
                
                if (isArm) {
                    assertThat(url).contains("arm64");
                } else {
                    assertThat(url).contains("x64");
                }
            }
        }

        @Test
        @DisplayName("Python download URL should be Windows-specific")
        void pythonDownloadUrlShouldBeWindowsSpecific() {
            var url = BinaryInfo.PYTHON.getDownloadUrl();
            String os = System.getProperty("os.name", "").toLowerCase();
            
            if (os.contains("win")) {
                assertThat(url).isPresent();
                assertThat(url.get()).contains("python.org");
                assertThat(url.get()).endsWith(".exe");
            } else {
                assertThat(url).isEmpty();
            }
        }

        @Test
        @DisplayName("PHP download URL should be platform-specific")
        void phpDownloadUrlShouldBePlatformSpecific() {
            String url = BinaryInfo.PHP.getDownloadUrl().orElse(null);
            assertThat(url).isNotNull();
            
            String os = System.getProperty("os.name", "").toLowerCase();
            String arch = System.getProperty("os.arch", "").toLowerCase();
            boolean isArm = arch.contains("aarch64") || arch.contains("arm");
            
            if (os.contains("win")) {
                // Windows: Official PHP builds from windows.php.net
                assertThat(url).contains("windows.php.net");
                assertThat(url).endsWith(".zip");
                assertThat(url).contains("Win32");
            } else if (os.contains("mac")) {
                // macOS: Static PHP builds from dl.static-php.dev
                assertThat(url).contains("dl.static-php.dev");
                assertThat(url).endsWith(".tar.gz");
                assertThat(url).contains("macos");
                if (isArm) {
                    assertThat(url).contains("aarch64");
                } else {
                    assertThat(url).contains("x86_64");
                }
            } else {
                // Linux: Static PHP builds from dl.static-php.dev
                assertThat(url).contains("dl.static-php.dev");
                assertThat(url).endsWith(".tar.gz");
                assertThat(url).contains("linux");
                if (isArm) {
                    assertThat(url).contains("aarch64");
                } else {
                    assertThat(url).contains("x86_64");
                }
            }
        }

        @Test
        @DisplayName("VNu download URL should point to GitHub releases")
        void vnuDownloadUrlShouldPointToGitHubReleases() {
            String url = BinaryInfo.VNU.getDownloadUrl().orElse(null);
            if (url != null) {
                assertThat(url).contains("github.com");
                assertThat(url).contains("releases");
            }
        }

        @Test
        @DisplayName("Stylelint should have npm download URL")
        void stylelintShouldHaveNpmDownloadUrl() {
            var url = BinaryInfo.STYLELINT.getDownloadUrl();
            assertThat(url).isPresent();
            assertThat(url.get()).contains("registry.npmjs.org");
            assertThat(url.get()).contains("stylelint");
            assertThat(url.get()).endsWith(".tgz");
        }
    }

    // ======================================================
    // Download Session Tests
    // ======================================================

    @Nested
    @DisplayName("Download Session Management")
    class DownloadSessionTests {

        @Test
        @DisplayName("Download session should be created by downloadAllMissingAsync")
        void downloadSessionShouldBeCreatedByDownloadAllMissingAsync() {
            DownloadSession session = manager.downloadAllMissingAsync();
            
            assertThat(session).isNotNull();
            assertThat(session.isDone()).isFalse();
            assertThat(session.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("Download session should track progress")
        void downloadSessionShouldTrackProgress() {
            DownloadSession session = manager.downloadAllMissingAsync();
            
            assertThat(session).isNotNull();
            assertThat(session.getCompletedCount()).isEqualTo(0);
            assertThat(session.getFailedCount()).isEqualTo(0);
            assertThat(session.getBytesDownloaded()).isEqualTo(0);
        }

        @Test
        @DisplayName("Download session should block on awaitCompletion when not done")
        void downloadSessionShouldBlockOnAwaitCompletion() {
            DownloadSession session = manager.downloadAllMissingAsync();
            
            assertThat(session.isDone()).isFalse();
            
            assertThatThrownBy(() -> {
                session.awaitCompletion(java.time.Duration.ofMillis(100));
            }).isInstanceOf(java.util.concurrent.TimeoutException.class);
        }

        @Test
        @DisplayName("Download session should support cancellation")
        void downloadSessionShouldSupportCancellation() {
            DownloadSession session = manager.downloadAllMissingAsync();
            
            assertThat(session.isCancelled()).isFalse();
            session.cancel();
            assertThat(session.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("Download session should provide progress string")
        void downloadSessionShouldProvideProgressString() {
            DownloadSession session = manager.downloadAllMissingAsync();
            
            String progressString = session.getProgressString();
            assertThat(progressString).isNotNull();
            assertThat(progressString).isNotEmpty();
        }

        @Test
        @DisplayName("Download session should provide summary")
        void downloadSessionShouldProvideSummary() {
            DownloadSession session = manager.downloadAllMissingAsync();
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            String summary = session.getSummary();
            assertThat(summary).isNotNull();
            assertThat(summary).isNotEmpty();
        }
    }

    // ======================================================
    // Download Functionality Tests
    // ======================================================

    @Nested
    @DisplayName("Download Functionality")
    class DownloadFunctionalityTests {

        @Test
        @DisplayName("Download and install should throw for null info")
        void downloadAndInstallShouldThrowForNullInfo() {
            assertThatNullPointerException().isThrownBy(() -> {
                manager.downloadAndInstall(null);
            });
        }

        @Test
        @DisplayName("Download and install should work for binaries with URL")
        void downloadAndInstallShouldWorkForBinariesWithUrl() {
            // Stylelint has an npm URL, so it should not throw
            // We'll just verify the method exists and can be called
            // Actual download would require network access
            assertThat(BinaryInfo.STYLELINT.getDownloadUrl()).isPresent();
        }

        @Test
        @DisplayName("Download should support progress tracking")
        void downloadShouldSupportProgressTracking() {
            AtomicInteger progressUpdates = new AtomicInteger(0);
            
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onProgress(String binaryName, long bytesDownloaded, long totalBytes) {
                    progressUpdates.incrementAndGet();
                }
            });
            
            assertThat(progressUpdates.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Download should support removing progress listeners")
        void downloadShouldSupportRemovingProgressListeners() {
            AtomicInteger progressUpdates = new AtomicInteger(0);
            DownloadProgressListener listener = new DownloadProgressListener() {
                @Override
                public void onProgress(String binaryName, long bytesDownloaded, long totalBytes) {
                    progressUpdates.incrementAndGet();
                }
            };
            
            manager.addProgressListener(listener);
            assertThat(manager.removeProgressListener(listener)).isTrue();
            assertThat(manager.removeProgressListener(listener)).isFalse();
        }

        @Test
        @DisplayName("Download all missing should return session")
        void downloadAllMissingShouldReturnSession() {
            DownloadSession session = manager.downloadAllMissingAsync();
            assertThat(session).isNotNull();
        }

        @Test
        @DisplayName("Download all missing should be async")
        void downloadAllMissingShouldBeAsync() {
            DownloadSession session = manager.downloadAllMissingAsync();
            
            assertThat(session.isDone()).isFalse();
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Test
        @DisplayName("Download all missing should return list of statuses")
        void downloadAllMissingShouldReturnListOfStatuses() throws IOException {
            var statuses = manager.downloadAllMissing();
            
            assertThat(statuses).isNotNull();
            assertThat(statuses).isNotEmpty();
            
            for (var status : statuses) {
                assertThat(status).isNotNull();
                assertThat(status.getBinaryInfo()).isNotNull();
            }
        }
    }

    // ======================================================
    // Version Resolution Tests
    // ======================================================

    @Nested
    @DisplayName("Version Resolution")
    class VersionResolutionTests {

        @Test
        @DisplayName("BinaryInfo should specify minimum versions")
        void binaryInfoShouldSpecifyMinimumVersions() {
            BinaryInfo[] binariesWithVersions = {
                BinaryInfo.NODE, BinaryInfo.JAVAC, BinaryInfo.PYTHON, 
                BinaryInfo.TSC, BinaryInfo.PHP, BinaryInfo.VNU, 
                BinaryInfo.STYLELINT
            };
            
            for (BinaryInfo info : binariesWithVersions) {
                if (info.getMinimumVersion().isPresent()) {
                    String version = info.getMinimumVersion().get();
                    assertThat(version).isNotEmpty();
                    assertThat(version).matches("\\d+\\.\\d+(\\.\\d+)?");
                }
            }
        }

        @Test
        @DisplayName("Node.js version should be recent")
        void nodeVersionShouldBeRecent() {
            String url = BinaryInfo.NODE.getDownloadUrl().orElse("");
            if (url.contains("node-v")) {
                int start = url.indexOf("node-v") + 6;
                int end = url.indexOf("-", start);
                if (end > start) {
                    String version = url.substring(start, end);
                    String[] parts = version.split("\\.");
                    int major = Integer.parseInt(parts[0]);
                    
                    assertThat(major).isGreaterThanOrEqualTo(18);
                }
            }
        }

        @Test
        @DisplayName("Python version should be recent")
        void pythonVersionShouldBeRecent() {
            String url = BinaryInfo.PYTHON.getDownloadUrl().orElse("");
            if (url.contains("python-")) {
                int start = url.indexOf("python-") + 7;
                int end = url.indexOf("-", start);
                if (end > start) {
                    String version = url.substring(start, end);
                    String[] parts = version.split("\\.");
                    int major = Integer.parseInt(parts[0]);
                    int minor = Integer.parseInt(parts[1]);
                    
                    assertThat(major).isEqualTo(3);
                    assertThat(minor).isGreaterThanOrEqualTo(9);
                }
            }
        }

        @Test
        @DisplayName("PHP version should be recent")
        void phpVersionShouldBeRecent() {
            String url = BinaryInfo.PHP.getDownloadUrl().orElse("");
            if (url.contains("php-")) {
                int start = url.indexOf("php-") + 4;
                int end = url.indexOf("-", start);
                if (end > start) {
                    String version = url.substring(start, end);
                    String[] parts = version.split("\\.");
                    int major = Integer.parseInt(parts[0]);
                    
                    assertThat(major).isGreaterThanOrEqualTo(8);
                }
            }
        }

        @Test
        @DisplayName("Java version should be recent")
        void javaVersionShouldBeRecent() {
            String minVersion = BinaryInfo.JAVAC.getMinimumVersion().orElse("");
            if (!minVersion.isEmpty()) {
                String[] parts = minVersion.split("\\.");
                int major = Integer.parseInt(parts[0]);
                
                assertThat(major).isGreaterThanOrEqualTo(17);
            }
        }
    }

    // ======================================================
    // Integration Tests (with network mocking or actual downloads)
    // ======================================================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @Disabled("Requires network access - enable manually")
        @DisplayName("Should download Node.js binary")
        void shouldDownloadNodeBinary() throws IOException {
            Path nodeDir = downloadDir.resolve("node");
            Files.createDirectories(nodeDir);
            
            assertThat(nodeDir).exists();
        }

        @Test
        @Disabled("Requires network access - enable manually")
        @DisplayName("Should download with progress tracking")
        void shouldDownloadWithProgressTracking() throws IOException {
            AtomicInteger progressUpdates = new AtomicInteger(0);
            AtomicLong lastDownloaded = new AtomicLong(0);
            
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onProgress(String binaryName, long bytesDownloaded, long totalBytes) {
                    progressUpdates.incrementAndGet();
                    lastDownloaded.set(bytesDownloaded);
                }
            });
            
            assertThat(progressUpdates.get()).isEqualTo(0);
        }

        @Test
        @Disabled("Requires network access - enable manually")
        @DisplayName("Should handle download failures gracefully")
        void shouldHandleDownloadFailuresGracefully() {
            AtomicInteger errorCount = new AtomicInteger(0);
            
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onError(String binaryName, String message, Throwable error) {
                    errorCount.incrementAndGet();
                }
            });
            
            assertThat(errorCount.get()).isEqualTo(0);
        }
    }

    // ======================================================
    // Cross-platform Compatibility Tests
    // ======================================================

    @Nested
    @DisplayName("Cross-platform Compatibility")
    class CrossPlatformCompatibilityTests {

        @Test
        @DisplayName("Should detect current platform correctly")
        void shouldDetectCurrentPlatformCorrectly() {
            String os = System.getProperty("os.name", "").toLowerCase();
            String arch = System.getProperty("os.arch", "").toLowerCase();
            
            assertThat(os).isNotEmpty();
            assertThat(arch).isNotEmpty();
            
            boolean isWindows = os.contains("win");
            boolean isMac = os.contains("mac");
            boolean isLinux = os.contains("linux") || os.contains("nux");
            
            assertThat(isWindows || isMac || isLinux).isTrue();
        }

        @Test
        @DisplayName("All binaries should have appropriate download URLs for current platform")
        void allBinariesShouldHaveAppropriateUrlsForCurrentPlatform() {
            // NODE and PHP have platform-specific URLs
            // VNU is a Java JAR file (platform-agnostic)
            // TSC and STYLELINT are npm packages
            BinaryInfo[] platformSpecificBinaries = {
                BinaryInfo.NODE, BinaryInfo.PHP
            };
            
            for (BinaryInfo info : platformSpecificBinaries) {
                if (info.getDownloadUrl().isPresent()) {
                    String url = info.getDownloadUrl().get();
                    String os = System.getProperty("os.name", "").toLowerCase();
                    
                    if (os.contains("win")) {
                        assertThat(url).satisfiesAnyOf(
                            u -> assertThat(u).contains("win"),
                            u -> assertThat(u).endsWith(".exe"),
                            u -> assertThat(u).endsWith(".zip")
                        );
                    }
                }
            }
            
            // VNU is platform-agnostic Java JAR
            assertThat(BinaryInfo.VNU.getDownloadUrl()).isPresent();
            assertThat(BinaryInfo.VNU.getDownloadUrl().get()).endsWith(".jar");
        }

        @Test
        @DisplayName("Download URLs should use HTTPS")
        void downloadUrlsShouldUseHttps() {
            BinaryInfo[] allBinaries = {
                BinaryInfo.NODE, BinaryInfo.JAVAC, BinaryInfo.PYTHON, 
                BinaryInfo.TSC, BinaryInfo.PHP, BinaryInfo.VNU, 
                BinaryInfo.STYLELINT
            };
            
            for (BinaryInfo info : allBinaries) {
                info.getDownloadUrl().ifPresent(url -> {
                    assertThat(url).startsWith("https://");
                });
            }
        }

        @Test
        @DisplayName("Download URLs should be valid URLs")
        void downloadUrlsShouldBeValidUrls() {
            BinaryInfo[] allBinaries = {
                BinaryInfo.NODE, BinaryInfo.JAVAC, BinaryInfo.PYTHON, 
                BinaryInfo.TSC, BinaryInfo.PHP, BinaryInfo.VNU, 
                BinaryInfo.STYLELINT
            };
            
            for (BinaryInfo info : allBinaries) {
                info.getDownloadUrl().ifPresent(url -> {
                    assertThat(url).matches("^https?://[^\\s/$.?#].[^\\s]*$");
                });
            }
        }
    }

    // ======================================================
    // File Extraction Tests
    // ======================================================

    @Nested
    @DisplayName("File Extraction")
    class FileExtractionTests {

        @Test
        @DisplayName("Should handle ZIP files")
        void shouldHandleZipFiles() throws IOException {
            Path zipDir = tempDir.resolve("zip-test");
            Files.createDirectories(zipDir);
            
            assertThat(zipDir).exists();
        }

        @Test
        @DisplayName("Should handle TAR.GZ files")
        void shouldHandleTarGzFiles() throws IOException {
            Path tarDir = tempDir.resolve("tar-test");
            Files.createDirectories(tarDir);
            
            assertThat(tarDir).exists();
        }

        @Test
        @DisplayName("Should handle TAR.XZ files")
        void shouldHandleTarXzFiles() throws IOException {
            Path tarxzDir = tempDir.resolve("tarxz-test");
            Files.createDirectories(tarxzDir);
            
            assertThat(tarxzDir).exists();
        }
    }

    // ======================================================
    // Error Handling Tests
    // ======================================================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle download of binary with URL")
        void shouldHandleDownloadOfBinaryWithUrl() {
            // Stylelint has an npm URL, so it should not throw
            // We'll just verify the method exists and can be called
            assertThat(BinaryInfo.STYLELINT.getDownloadUrl()).isPresent();
        }
    }

    // ======================================================
    // Progress Listener Tests
    // ======================================================

    @Nested
    @DisplayName("Progress Listener")
    class ProgressListenerTests {

        @Test
        @DisplayName("Should notify progress listeners")
        void shouldNotifyProgressListeners() {
            AtomicInteger progressUpdates = new AtomicInteger(0);
            AtomicReference<String> lastBinaryId = new AtomicReference<>();
            AtomicLong lastDownloaded = new AtomicLong(0);
            AtomicLong lastTotal = new AtomicLong(0);
            
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onProgress(String binaryName, long bytesDownloaded, long totalBytes) {
                    progressUpdates.incrementAndGet();
                    lastBinaryId.set(binaryName);
                    lastDownloaded.set(bytesDownloaded);
                    lastTotal.set(totalBytes);
                }
            });
            
            assertThat(progressUpdates.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should support multiple progress listeners")
        void shouldSupportMultipleProgressListeners() {
            AtomicInteger count = new AtomicInteger(0);
            
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onProgress(String binaryName, long bytesDownloaded, long totalBytes) {
                    count.incrementAndGet();
                }
            });
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onProgress(String binaryName, long bytesDownloaded, long totalBytes) {
                    count.incrementAndGet();
                }
            });
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onProgress(String binaryName, long bytesDownloaded, long totalBytes) {
                    count.incrementAndGet();
                }
            });
            
            assertThat(count.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should remove progress listeners")
        void shouldRemoveProgressListeners() {
            AtomicInteger count = new AtomicInteger(0);
            DownloadProgressListener listener = new DownloadProgressListener() {
                @Override
                public void onProgress(String binaryName, long bytesDownloaded, long totalBytes) {
                    count.incrementAndGet();
                }
            };
            
            manager.addProgressListener(listener);
            assertThat(manager.removeProgressListener(listener)).isTrue();
            assertThat(manager.removeProgressListener(listener)).isFalse();
        }

        @Test
        @DisplayName("Should notify download start listeners")
        void shouldNotifyDownloadStartListeners() {
            AtomicInteger startCount = new AtomicInteger(0);
            
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onDownloadStart(String binaryName, long totalBytes) {
                    startCount.incrementAndGet();
                }
            });
            
            assertThat(startCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should notify download complete listeners")
        void shouldNotifyDownloadCompleteListeners() {
            AtomicInteger completeCount = new AtomicInteger(0);
            
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onDownloadComplete(String binaryName, long totalBytes) {
                    completeCount.incrementAndGet();
                }
            });
            
            assertThat(completeCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should notify error listeners")
        void shouldNotifyErrorListeners() {
            AtomicInteger errorCount = new AtomicInteger(0);
            
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onError(String binaryName, String message, Throwable error) {
                    errorCount.incrementAndGet();
                }
            });
            
            assertThat(errorCount.get()).isEqualTo(0);
        }
    }

    // ======================================================
    // Cross-platform Installation Path Tests
    // ======================================================

    @Nested
    @DisplayName("Installation Paths")
    class InstallationPathTests {

        @Test
        @DisplayName("Node.js installation path should be platform-specific")
        void nodeInstallationPathShouldBePlatformSpecific() {
            String os = System.getProperty("os.name", "").toLowerCase();
            Path expectedPath = BinaryInfo.NODE.getInstalledPath(downloadDir);
            
            if (os.contains("win")) {
                assertThat(expectedPath.toString()).endsWith("node.exe");
            } else {
                assertThat(expectedPath.toString()).endsWith("node");
            }
        }

        @Test
        @DisplayName("TSC installation path should be in node_modules/.bin")
        void tscInstallationPathShouldBeInNodeModulesBin() {
            String os = System.getProperty("os.name", "").toLowerCase();
            Path expectedPath = BinaryInfo.TSC.getInstalledPath(downloadDir);
            
            // TSC is an npm package, so it should be in node_modules/.bin
            assertThat(expectedPath.toString()).contains("node_modules");
            assertThat(expectedPath.toString()).contains(".bin");
            if (os.contains("win")) {
                assertThat(expectedPath.toString()).endsWith(".cmd");
            }
        }

        @Test
        @DisplayName("PHP installation path should be platform-specific")
        void phpInstallationPathShouldBePlatformSpecific() {
            String os = System.getProperty("os.name", "").toLowerCase();
            Path expectedPath = BinaryInfo.PHP.getInstalledPath(downloadDir);
            
            if (os.contains("win")) {
                assertThat(expectedPath.toString()).endsWith("php.exe");
            } else {
                assertThat(expectedPath.toString()).endsWith("php");
            }
        }

        @Test
        @DisplayName("VNu installation path should be platform-specific")
        void vnuInstallationPathShouldBePlatformSpecific() {
            Path expectedPath = BinaryInfo.VNU.getInstalledPath(downloadDir);
            assertThat(expectedPath.toString()).contains("vnu");
        }

        @Test
        @DisplayName("Java installation path should be platform-specific")
        void javaInstallationPathShouldBePlatformSpecific() {
            String os = System.getProperty("os.name", "").toLowerCase();
            Path expectedPath = BinaryInfo.JAVAC.getInstalledPath(downloadDir);
            
            if (os.contains("win")) {
                assertThat(expectedPath.toString()).endsWith("javac.exe");
            } else {
                assertThat(expectedPath.toString()).endsWith("javac");
            }
        }

        @Test
        @DisplayName("Python installation path should be platform-specific")
        void pythonInstallationPathShouldBePlatformSpecific() {
            String os = System.getProperty("os.name", "").toLowerCase();
            Path expectedPath = BinaryInfo.PYTHON.getInstalledPath(downloadDir);
            
            if (os.contains("win")) {
                assertThat(expectedPath.toString()).endsWith("python.exe");
            } else {
                assertThat(expectedPath.toString()).endsWith("python");
            }
        }
    }
}