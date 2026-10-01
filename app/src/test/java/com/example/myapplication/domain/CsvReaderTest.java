package com.example.myapplication.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CsvReaderTest {

    @Test
    public void splitsFieldsAndKeepsEmptyOnes() {
        List<List<String>> records = CsvReader.parse("a,,b\n,,\n");

        assertEquals(2, records.size());
        assertEquals(Arrays.asList("a", "", "b"), records.get(0));
        assertEquals(Arrays.asList("", "", ""), records.get(1));
    }

    @Test
    public void skipsBlankLinesAndHandlesCrLf() {
        List<List<String>> records = CsvReader.parse("a,b\r\n\r\n\nc,d");

        assertEquals(2, records.size());
        assertEquals(Arrays.asList("c", "d"), records.get(1));
    }

    @Test
    public void handlesQuotesWithCommasNewlinesAndEscapedQuotes() {
        List<List<String>> records = CsvReader.parse("\"x, y\",\"linea1\nlinea2\",\"dice \"\"hola\"\"\"\n");

        assertEquals(1, records.size());
        assertEquals(Arrays.asList("x, y", "linea1\nlinea2", "dice \"hola\""), records.get(0));
    }

    @Test
    public void emptyQuotedFieldCountsAsRecord() {
        List<List<String>> records = CsvReader.parse("\"\"\n");

        assertEquals(1, records.size());
        assertEquals(Arrays.asList(""), records.get(0));
    }
}
