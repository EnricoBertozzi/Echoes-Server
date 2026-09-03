package com.n0hana.echoes_server.repository.memory;

import java.time.Duration;
import java.util.Optional;

public interface InMemoryRepository<K, V> {
    
    V save(K key, V value, Duration tls);

    void delete(K key);

    Optional<V> find(K key);

    boolean exists(K key);

    String buildKey(K key);

}
