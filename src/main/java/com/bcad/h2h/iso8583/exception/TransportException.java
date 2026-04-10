package com.bcad.h2h.iso8583.exception;

public class TransportException extends RuntimeException {

    private final String host;
    private final int port;

    public TransportException(String message) {
        super(message);
        this.host = null;
        this.port = 0;
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
        this.host = null;
        this.port = 0;
    }

    public TransportException(String message, String host, int port) {
        super(message);
        this.host = host;
        this.port = port;
    }

    public TransportException(String message, String host, int port, Throwable cause) {
        super(message, cause);
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
