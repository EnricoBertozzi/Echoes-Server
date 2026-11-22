package com.n0hana.echoes_server.infra.logs;

import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class PiiMaskingThrowableConverter extends ExtendedWhitespaceThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.mask(super.convert(event));
    }
}
