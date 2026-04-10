package com.bcad.h2h.iso8583.iso;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Data
@NoArgsConstructor
public class IsoMessage {

    private String mti;
    private Map<Integer, String> fields = new HashMap<>();
    private byte[] rawMessage;

    public IsoMessage(String mti) {
        this.mti = mti;
    }

    public String getField(int fieldNumber) {
        return fields.get(fieldNumber);
    }

    public void setField(int fieldNumber, String value) {
        if (value != null) {
            fields.put(fieldNumber, value);
        }
    }

    public boolean hasField(int fieldNumber) {
        return fields.containsKey(fieldNumber) && fields.get(fieldNumber) != null;
    }

    public Set<Integer> getBitmapFields() {
        return new TreeSet<>(fields.keySet());
    }

    public void removeField(int fieldNumber) {
        fields.remove(fieldNumber);
    }

    @Override
    public String toString() {
        return "IsoMessage{mti='" + mti + "', fields=" + new TreeSet<>(fields.keySet()) + "}";
    }
}
