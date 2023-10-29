package com.jfrmerger.common.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeRangeTest {
    @Test
    void testTimeRange() {
        long start = 1698513910000L;
        long end = 1698513911000L;

        TimeRange timeRange = TimeRange.of(start, end);

        var actualStart = timeRange.start().atZone(ZoneOffset.UTC);
        var actualEnd = timeRange.end().atZone(ZoneOffset.UTC);

        assertEquals(2023, actualStart.getLong(ChronoField.YEAR));
        assertEquals(10, actualStart.getLong(ChronoField.MONTH_OF_YEAR));
        assertEquals(28, actualStart.getLong(ChronoField.DAY_OF_MONTH));
        assertEquals(17, actualStart.getLong(ChronoField.HOUR_OF_DAY));
        assertEquals(25, actualStart.getLong(ChronoField.MINUTE_OF_HOUR));
        assertEquals(10, actualStart.getLong(ChronoField.SECOND_OF_MINUTE));

        assertEquals(2023, actualEnd.getLong(ChronoField.YEAR));
        assertEquals(10, actualEnd.getLong(ChronoField.MONTH_OF_YEAR));
        assertEquals(28, actualEnd.getLong(ChronoField.DAY_OF_MONTH));
        assertEquals(17, actualEnd.getLong(ChronoField.HOUR_OF_DAY));
        assertEquals(25, actualEnd.getLong(ChronoField.MINUTE_OF_HOUR));
        assertEquals(11, actualEnd.getLong(ChronoField.SECOND_OF_MINUTE));
    }
}