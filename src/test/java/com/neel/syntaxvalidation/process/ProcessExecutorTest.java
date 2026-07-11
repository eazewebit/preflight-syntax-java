package com.neel.syntaxvalidation.process;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessExecutorTest {

    private final ProcessExecutor executor = new ProcessExecutor();

    @Test
    void execute_capturesSuccessfulOutput() throws Exception {
        List<String> command = List.of("java", "-version");

        ProcessResult result = executor.execute(command);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.exitCode()).isZero();
        // java -version writes the version banner to stderr.
        assertThat(result.stderr()).contains("version");
    }

    @Test
    void execute_capturesNonZeroExitCode() throws Exception {
        // An unknown flag causes java to exit with a non-zero code.
        List<String> command = List.of("java", "-thisFlagDoesNotExist");

        ProcessResult result = executor.execute(command);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.exitCode()).isNotZero();
    }

    @Test
    void execute_reportsTimeoutWhenProcessHangs() throws Exception {
        ProcessExecutor shortTimeoutExecutor = new ProcessExecutor(Duration.ofMillis(200));
        List<String> command = longRunningCommand();

        ProcessResult result = shortTimeoutExecutor.execute(command);

        assertThat(result.timedOut()).isTrue();
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void execute_rejectsEmptyCommand() {
        assertThatThrownBy(() -> executor.execute(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * @return a command that intentionally runs long enough to exceed a short timeout,
     *         chosen per platform.
     */
    private static List<String> longRunningCommand() {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        if (windows) {
            // ping with a high count blocks for many seconds.
            return List.of("ping", "-n", "30", "127.0.0.1");
        }
        return List.of("sleep", "30");
    }
}
