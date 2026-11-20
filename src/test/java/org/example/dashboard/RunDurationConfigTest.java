package org.example.dashboard;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunDurationConfigTest {
    // Asserted as a literal on purpose, so changing the production constant makes these tests fail.
    private static final String MESSAGE = "RUN_DURATION_MS must be a whole number from 100 to 10000.";

    @Test
    void unsetAndBlankValuesUseTheDefaultOf1400Milliseconds() {
        // \u2003 is EM SPACE: whitespace for String.strip(), so it is blank.
        String[] blanks = {null, "", "   ", "\t\n", "\u2003"};
        for (String blank : blanks) {
            assertEquals(Duration.ofMillis(1400), RunDurationConfig.parse(blank), "input: " + blank);
            assertEquals(ExecutionService.DEFAULT_RUN_DURATION, RunDurationConfig.parse(blank));
        }
    }

    @Test
    void acceptsBoundariesAndStripsSurroundingWhitespace() {
        assertEquals(Duration.ofMillis(100), RunDurationConfig.parse("100"));
        assertEquals(Duration.ofMillis(10000), RunDurationConfig.parse("10000"));
        assertEquals(Duration.ofMillis(200), RunDurationConfig.parse("200"));
        assertEquals(Duration.ofMillis(500), RunDurationConfig.parse(" 500 "));
        // EM SPACE (\u2003) is stripped like ASCII whitespace.
        assertEquals(Duration.ofMillis(500), RunDurationConfig.parse("\u2003500\u2003"));
        assertEquals(Duration.ofMillis(500), RunDurationConfig.parse("\t500\n"));
    }

    @Test
    void rejectsInvalidValuesWithTheStandardMessageOnly() {
        String[] invalid = {
                "99", "10001", "0", "0100", "+100", "-5", "-100", "100.5", "1e3", "abc", "1 000",
                "99999999999", "2147483648", "1".repeat(5000),
                "\u0661\u0660\u0660",       // Arabic-Indic digits, not ASCII
                "\u00a0500", "500\u00a0",   // NBSP is not whitespace for String.strip()
                "10 0", "1_000", "0x64", "100L", "\uff11\uff10\uff10" // fullwidth digits
        };
        for (String value : invalid) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> RunDurationConfig.parse(value), "input: " + value);
            assertEquals(MESSAGE, error.getMessage(), "input: " + value);
            assertEquals(IllegalArgumentException.class, error.getClass(), "input: " + value);
        }
    }

    @Test
    void errorMessageDoesNotEchoTheRawValue() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RunDurationConfig.parse("secret-value"));
        assertEquals(MESSAGE, error.getMessage());
    }
}
