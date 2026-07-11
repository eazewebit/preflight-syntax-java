package com.neel.syntaxvalidation.process;

import java.util.Objects;

/**
 * Immutable capture of a finished external process's outcome.
 *
 * @param exitCode  the process exit value; {@code -1} when the process was killed
 *                  after a timeout.
 * @param stdout    the captured standard output (UTF-8 decoded); never {@code null}.
 * @param stderr    the captured standard error (UTF-8 decoded); never {@code null}.
 * @param timedOut  {@code true} if the process exceeded its deadline and was
 *                  forcibly destroyed.
 */
public record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {

    /**
     * @param exitCode the process exit value.
     * @param stdout   captured standard output.
     * @param stderr   captured standard error.
     * @param timedOut whether the process timed out.
     */
    public ProcessResult {
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
    }

    /** @return {@code true} when the process exited with code {@code 0} and did not time out. */
    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }
}
