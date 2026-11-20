package com.n0hana.echoes_server.support;

import java.net.InetSocketAddress;
import java.net.Socket;

// com -DrequireRedis=true (CI) a ausência do Redis falha o teste em vez de pulá-lo
public final class RedisAvailability {

    private RedisAvailability() {
    }

    public static boolean redisReachable() {
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 6379), 500);
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }

        if (!reachable && Boolean.getBoolean("requireRedis")) {
            throw new IllegalStateException(
                    "Redis indisponível em localhost:6379 e -DrequireRedis=true está ativo");
        }
        return reachable;
    }
}
