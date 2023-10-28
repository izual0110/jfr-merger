package com.jfrmerger.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsoleArgumentsTest {

    @Test
    void testParser() {
        String[] strings = {"--jfr", "1", "2", "3", "4", "5", "--from", "666", "--to", "777"};
        ConsoleArguments arguments = ConsoleArguments.of(strings);

        assertEquals(5, arguments.getFiles().size());
        assertEquals("1", arguments.getFiles().get(0));
        assertEquals(666L, arguments.getFrom());
        assertEquals(777L, arguments.getTo());
    }

    @Test
    void testErrorParser() {
        String[] strings = {"--jfr", "1", "2", "3", "4", "5", "--from", "666", "--to"};
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> ConsoleArguments.of(strings));
    }

    @Test
    void testErrorParserWithUnknownArg() {
        String[] strings = {"--jfrs"};
        assertThrows(IllegalArgumentException.class, () -> ConsoleArguments.of(strings));
    }
}