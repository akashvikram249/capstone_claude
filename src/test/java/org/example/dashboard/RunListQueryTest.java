package org.example.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunListQueryTest {
    private static final String LITERAL_MESSAGE = "limit must be a whole number from 1 to 100.";

    @Test
    void absentOrEmptyQueryUsesTheDefaultOf20() {
        assertEquals(20, RunListQuery.parseLimit(null));
        assertEquals(20, RunListQuery.parseLimit(""));
        assertEquals(20, RunListQuery.parseLimit("other=1"));
    }

    @Test
    void unrelatedParametersAndEmptySegmentsAreIgnored() {
        assertEquals(20, RunListQuery.parseLimit("Limit=500"));
        assertEquals(20, RunListQuery.parseLimit("&&"));
        assertEquals(20, RunListQuery.parseLimit("&a=1&"));
        assertEquals(20, RunListQuery.parseLimit("limits=500&xlimit=3"));
        assertEquals(7, RunListQuery.parseLimit("&&limit=7&"));
    }

    @Test
    void acceptsWholeNumbersFromOneToOneHundred() {
        assertEquals(1, RunListQuery.parseLimit("limit=1"));
        assertEquals(20, RunListQuery.parseLimit("limit=20"));
        assertEquals(100, RunListQuery.parseLimit("limit=100"));
    }

    @Test
    void acceptsLeadingZerosUnlikeRunDurationConfig() {
        // RunDurationConfig rejects leading zeros for RUN_DURATION_MS; the approved limit rules accept them.
        assertEquals(5, RunListQuery.parseLimit("limit=05"));
        assertEquals(100, RunListQuery.parseLimit("limit=0100"));
        assertEquals(5, RunListQuery.parseLimit("limit=000000000000000000005"));
    }

    @Test
    void decodesPercentEncodedNamesAndValues() {
        assertEquals(5, RunListQuery.parseLimit("limit=%35"));
        assertEquals(7, RunListQuery.parseLimit("%6Cimit=7"));
    }

    @Test
    void firstLimitSegmentDecides() {
        assertEquals(5, RunListQuery.parseLimit("limit=5&limit=10"));
        assertEquals(5, RunListQuery.parseLimit("limit=5&limit=abc"));
        assertInvalid("limit=abc&limit=5");
        assertInvalid("limit=&limit=5");
        assertInvalid("limit&limit=5");
    }

    @Test
    void segmentWithAnUndecodableNameIsSkipped() {
        assertEquals(3, RunListQuery.parseLimit("%zz=1&limit=3"));
        assertEquals(20, RunListQuery.parseLimit("%zz=1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "limit=0",
            "limit=00",
            "limit=101",
            "limit=abc",
            "limit=2.5",
            "limit=",
            "limit",
            "limit=+5",
            "limit=%2B5",
            "limit=%205",
            "limit=5%20",
            "limit=5+",
            "limit=-1",
            "limit=1e1",
            "limit=%zz",
            "limit=%",
            "limit=99999999999",
            "limit=2147483648",
            "limit=1000",
            "limit=0000000000000000000000000000000000000001000",
            "limit=1111111111111111111111111111111111111111111111111111",
            "limit=٥",
            "limit=５"
    })
    void rejectsEveryOtherForm(String query) {
        assertInvalid(query);
    }

    private static void assertInvalid(String query) {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> RunListQuery.parseLimit(query), query);
        assertEquals(LITERAL_MESSAGE, error.getMessage());
    }
}
