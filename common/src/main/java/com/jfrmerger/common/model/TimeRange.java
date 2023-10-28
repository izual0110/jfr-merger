package com.jfrmerger.common.model;

import lombok.Builder;

import java.time.Instant;

@Builder
public class TimeRange {
    private final Instant start;
    private final Instant end;

    public boolean validate(Instant date) {
        return date.isAfter(start) && date.isBefore(end);
    }
}
