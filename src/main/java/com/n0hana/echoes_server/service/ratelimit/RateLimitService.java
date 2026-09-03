package com.n0hana.echoes_server.service.ratelimit;

import io.github.bucket4j.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, k -> createBucket());
    }

    private Bucket createBucket() {
        return Bucket.builder()
              .addLimit(limit -> limit
              .capacity(5)
                .refillIntervally(1, Duration.ofMinutes(1))
              )
              .build();
    }
}
