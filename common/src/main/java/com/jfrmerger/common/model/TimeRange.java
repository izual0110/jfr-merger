package com.jfrmerger.common.model;

import java.time.Instant;

public record TimeRange(Instant start, Instant end) {

    public static TimeRange of(Long start, Long end) {
        if (start == null || end == null) {
            return null;
        }
        return new TimeRange(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end));
    }

    public boolean validate(Instant date) {
        return date.isAfter(start) && date.isBefore(end);
    }
}
