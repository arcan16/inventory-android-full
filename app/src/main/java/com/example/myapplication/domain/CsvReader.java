package com.example.myapplication.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Lector CSV minimo (RFC 4180: coma como separador, comillas dobles para
 * campos con comas o saltos de linea, "" como comilla escapada). Equivale al
 * CSVFormat.DEFAULT con setIgnoreEmptyLines(true) de commons-csv que usaba el
 * backend: las lineas completamente vacias se descartan, pero una linea con
 * solo comas si cuenta como registro.
 */
final class CsvReader {

    private CsvReader() {
    }

    static List<List<String>> parse(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        // true si la linea actual ya tiene algo (aunque sea una coma o un campo entre comillas vacio).
        boolean lineHasContent = false;

        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < length && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            if (c == '"') {
                inQuotes = true;
                lineHasContent = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
                lineHasContent = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (lineHasContent || field.length() > 0) {
                    record.add(field.toString());
                    records.add(record);
                    record = new ArrayList<>();
                }
                field.setLength(0);
                lineHasContent = false;
            } else {
                field.append(c);
            }
        }

        if (lineHasContent || field.length() > 0) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}
