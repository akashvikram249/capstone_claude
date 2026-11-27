package org.example.dashboard;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Parses the query string of {@code GET /api/runs}. Pure: no HTTP types, no output. */
final class RunListQuery {
    static final String INVALID_LIMIT_MESSAGE = "limit must be a whole number from 1 to 100.";

    private static final int MAX_SIGNIFICANT_DIGITS = 3;

    private RunListQuery() {}

    /**
     * Returns the requested limit, or the default when {@code limit} is absent.
     *
     * @param rawQuery the raw (still percent-encoded) query string, or null
     * @throws IllegalArgumentException with {@link #INVALID_LIMIT_MESSAGE} when the limit is invalid
     */
    static int parseLimit(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return ExecutionService.DEFAULT_LIST_LIMIT;
        }
        for (String segment : rawQuery.split("&")) {
            if (segment.isEmpty()) {
                continue;
            }
            int equals = segment.indexOf('=');
            String rawName = equals < 0 ? segment : segment.substring(0, equals);
            String name;
            try {
                name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException error) {
                continue; // an undecodable name is not "limit"; ignore the segment
            }
            if (!"limit".equals(name)) {
                continue;
            }
            // The first limit segment decides; later ones are never examined.
            if (equals < 0) {
                throw invalid();
            }
            String value;
            try {
                value = URLDecoder.decode(segment.substring(equals + 1), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException error) {
                throw invalid();
            }
            return parseValue(value);
        }
        return ExecutionService.DEFAULT_LIST_LIMIT;
    }

    private static int parseValue(String value) {
        if (value.isEmpty()) {
            throw invalid();
        }
        int firstSignificant = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < '0' || character > '9') { // ASCII digits only, not Character.isDigit
                throw invalid();
            }
        }
        // Unlike RunDurationConfig, leading zeros are accepted: "05" is 5.
        while (firstSignificant < value.length() && value.charAt(firstSignificant) == '0') {
            firstSignificant++;
        }
        int significant = value.length() - firstSignificant;
        if (significant == 0 || significant > MAX_SIGNIFICANT_DIGITS) {
            throw invalid();
        }
        int limit = Integer.parseInt(value.substring(firstSignificant));
        if (limit < 1 || limit > ExecutionService.MAX_LIST_LIMIT) {
            throw invalid();
        }
        return limit;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(INVALID_LIMIT_MESSAGE);
    }
}
