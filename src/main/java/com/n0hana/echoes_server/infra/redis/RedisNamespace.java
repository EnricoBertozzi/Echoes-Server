package com.n0hana.echoes_server.infra.redis;

import java.time.Duration;

public interface RedisNamespace {

    String getPrefix();

    Duration getDuration();
}
