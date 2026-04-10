package com.bcad.h2h.iso8583.exception;

public class IsoMessageParseException extends RuntimeException {

    private final String rawMessage;

    public IsoMessageParseException(String message) {
        super(message);
        this.rawMessage = null;
    }

    public IsoMessageParseException(String message, Throwable cause) {
        super(message, cause);
        this.rawMessage = null;
    }

    public IsoMessageParseException(String message, String rawMessage) {
        super(message);
        this.rawMessage = rawMessage;
    }

    public IsoMessageParseException(String message, String rawMessage, Throwable cause) {
        super(message, cause);
        this.rawMessage = rawMessage;
    }

    public String getRawMessage() {
        return rawMessage;
    }
}
