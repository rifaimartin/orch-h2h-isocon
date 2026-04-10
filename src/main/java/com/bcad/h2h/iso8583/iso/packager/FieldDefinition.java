package com.bcad.h2h.iso8583.iso.packager;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FieldDefinition {

    public enum FieldType {
        NUMERIC,
        ALPHA,
        ALPHANUM,
        LLVAR,
        LLLVAR,
        BINARY
    }

    private int fieldNumber;
    private FieldType type;
    private int maxLength;
    private String description;
}
