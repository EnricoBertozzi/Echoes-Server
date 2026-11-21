package com.n0hana.echoes_server.infra.observability;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Perfil dev: o Prometheus é exposto, mas continua exigindo autenticação, e traz os gauges do Javers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "dev" })
class ActuatorPrometheusDevProfileTests {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("/actuator/prometheus exige autenticação e expõe javers_snapshot_count e javers_snapshot_bytes")
    void prometheusExposesJaversGauges() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());

        String body = mockMvc.perform(get("/actuator/prometheus").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("javers_snapshot_count"), "gauge de contagem ausente");
        assertTrue(body.contains("javers_snapshot_bytes"), "gauge de bytes ausente");
    }
}
