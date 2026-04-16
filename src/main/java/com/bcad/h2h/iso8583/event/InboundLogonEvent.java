package com.bcad.h2h.iso8583.event;

import com.bcad.h2h.iso8583.iso.IsoMessage;
import org.springframework.context.ApplicationEvent;

/**
 * Published by TcpConnectionManager when BCA sends unsolicited 0800/BIT70=001 (Logon).
 * NetworkSessionManager listens to this and sets loggedIn = true.
 */
public class InboundLogonEvent extends ApplicationEvent {

    private final IsoMessage message;

    public InboundLogonEvent(Object source, IsoMessage message) {
        super(source);
        this.message = message;
    }

    public IsoMessage getMessage() {
        return message;
    }
}
