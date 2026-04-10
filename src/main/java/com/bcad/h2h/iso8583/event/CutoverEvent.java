package com.bcad.h2h.iso8583.event;

import com.bcad.h2h.iso8583.iso.IsoMessage;
import org.springframework.context.ApplicationEvent;

/**
 * Published by TcpConnectionManager when BCA sends unsolicited 0800/BIT70=201 (Cutover).
 * Listeners can update business date or perform cutover-related logic.
 */
public class CutoverEvent extends ApplicationEvent {

    private final IsoMessage message;

    public CutoverEvent(Object source, IsoMessage message) {
        super(source);
        this.message = message;
    }

    public IsoMessage getMessage() {
        return message;
    }
}
