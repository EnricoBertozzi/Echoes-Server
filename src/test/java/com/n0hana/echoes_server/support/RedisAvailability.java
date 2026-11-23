package com.n0hana.echoes_server.support;

import java.net.InetSocketAddress;
import java.net.Socket;

// com -DrequireRedis=true (CI) a ausência do Redis falha o teste em vez de pulá-lo
public final class RedisAvailability {

    private RedisAvailability() {
    }

    public static boolean redisReachable() {
        String host = System.getProperty("spring.data.redis.host", "localhost");
        int port = Integer.getInteger("spring.data.redis.port", 6379);
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }

        if (!reachable && Boolean.getBoolean("requireRedis")) {
            throw new IllegalStateException(
                    "Redis indisponível em " + host + ":" + port + " e -DrequireRedis=true está ativo");
        }
        return reachable;
    }
}
