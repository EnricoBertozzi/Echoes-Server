package com.n0hana.echoes_server.infra.javers.retention;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JaversRetentionPolicyRepository extends JpaRepository<JaversRetentionPolicy, String> {

    List<JaversRetentionPolicy> findByActiveTrue();
}
