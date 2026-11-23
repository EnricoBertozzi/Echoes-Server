package com.n0hana.echoes_server.infra.logs;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class PiiMaskingConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.mask(super.convert(event));
    }
}
