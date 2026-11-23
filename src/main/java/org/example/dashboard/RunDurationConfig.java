package org.example.dashboard;

import java.time.Duration;

/**
 * Parses the RUN_DURATION_MS setting. Pure function: no environment access, printing, or exit.
 */
final class RunDurationConfig {
    static final int MIN_MS = 100;
    static final int MAX_MS = 10000;
    static final String INVALID_MESSAGE = "RUN_DURATION_MS must be a whole number from 100 to 10000.";

    private static final int MIN_DIGITS = 3;
    private static final int MAX_DIGITS = 5;

    private RunDurationConfig() {}

    /**
     * @param raw the raw environment value, or null when unset
     * @return the default duration for null or blank input, otherwise the configured duration
     * @throws IllegalArgumentException with {@link #INVALID_MESSAGE} for any other value
     */
    static Duration parse(String raw) {
        if (raw == null) {
            return ExecutionService.DEFAULT_RUN_DURATION;
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            return ExecutionService.DEFAULT_RUN_DURATION;
        }
        // The length check comes first so Integer.parseInt can never overflow or see a non-digit.
        if (value.length() < MIN_DIGITS || value.length() > MAX_DIGITS || !isAsciiDigits(value)
                || value.charAt(0) == '0') {
            throw invalid();
        }
        int millis = Integer.parseInt(value);
        if (millis < MIN_MS || millis > MAX_MS) {
            throw invalid();
        }
        return Duration.ofMillis(millis);
    }

    private static boolean isAsciiDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(INVALID_MESSAGE);
    }
}
