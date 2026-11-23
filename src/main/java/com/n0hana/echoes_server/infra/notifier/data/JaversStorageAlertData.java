package com.n0hana.echoes_server.infra.notifier.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JaversStorageAlertData implements MessageData {
    private long bytes;
    private long thresholdMb;
}
