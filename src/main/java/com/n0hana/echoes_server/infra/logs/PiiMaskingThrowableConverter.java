package com.n0hana.echoes_server.infra.logs;

import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

import ch.qos.logback.classic.spi.ILoggingEvent;

/** Substitui o {@code %wEx}: a mensagem das exceções e os stack traces também passam pelo mascaramento (L11). */
public class PiiMaskingThrowableConverter extends ExtendedWhitespaceThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.mask(super.convert(event));
    }
}
