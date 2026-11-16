package com.n0hana.echoes_server.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.device.DeviceScenarioDTO.DeviceConfig;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.PointConfig;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.ScenarioRef;
import com.n0hana.echoes_server.device.exception.InvalidDeviceUpdateException;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.user.UserRepository;

@WebMvcTest(DeviceScenarioController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
@ActiveProfiles("test")
class DeviceScenarioControllerTests {

  private static final String BASE = "/api/v1/devices";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private DeviceScenarioService service;

  @MockitoBean
  private JwtTokenService jwtTokenService;

  @MockitoBean
  private UserRepository userRepository;

  @Test
  @DisplayName("GET /api/v1/devices/me/scenario sem dispositivo autenticado (403 Forbidden)")
  void shouldRejectWithoutDevice() throws Exception {
    mockMvc.perform(get(BASE + "/me/scenario"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("GET /api/v1/devices/{id}/scenario devolve os pontos com o cenário atual (200 OK)")
  void shouldReturnConfig() throws Exception {
    UUID deviceId = UUID.randomUUID();
    UUID pointId = UUID.randomUUID();
    UUID scenarioId = UUID.randomUUID();
    DeviceConfig config = new DeviceConfig(deviceId, List.of(
        new PointConfig(pointId, "Aorta",
            new ScenarioRef(scenarioId, "Sopro", "Sopro mitral", "/api/v1/scenarios/" + scenarioId + "/audio")),
        new PointConfig(UUID.randomUUID(), "Pulmão", null)));
    when(service.findConfig(any(), eq(deviceId))).thenReturn(config);

    mockMvc.perform(get(BASE + "/{id}/scenario", deviceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
        .andExpect(jsonPath("$.points[0].position").value("Aorta"))
        .andExpect(jsonPath("$.points[0].scenario.audioUri").value("/api/v1/scenarios/" + scenarioId + "/audio"))
        .andExpect(jsonPath("$.points[1].scenario").isEmpty());
  }

  @Test
  @DisplayName("PUT /api/v1/devices/{id}/points/{pointId}/scenario com cenário válido (204 No Content)")
  void shouldSelectScenario() throws Exception {
    UUID deviceId = UUID.randomUUID();
    UUID pointId = UUID.randomUUID();
    UUID scenarioId = UUID.randomUUID();

    mockMvc.perform(put(BASE + "/{id}/points/{pointId}/scenario", deviceId, pointId)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"scenarioId\": \"" + scenarioId + "\"}"))
        .andExpect(status().isNoContent());

    verify(service).select(any(), eq(deviceId), eq(pointId), eq(scenarioId));
  }

  @Test
  @DisplayName("PUT /api/v1/devices/{id}/points/{pointId}/scenario sem cenário (400 Bad Request)")
  void shouldRejectMissingScenarioId() throws Exception {
    mockMvc.perform(put(BASE + "/{id}/points/{pointId}/scenario", UUID.randomUUID(), UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("PUT /api/v1/devices/{id}/points/{pointId}/scenario com cenário de outro ponto (400 Bad Request)")
  void shouldRejectScenarioOfOtherPoint() throws Exception {
    UUID scenarioId = UUID.randomUUID();
    doThrow(new InvalidDeviceUpdateException("O cenário não pertence a este ponto"))
        .when(service).select(any(), any(UUID.class), any(UUID.class), eq(scenarioId));

    mockMvc.perform(put(BASE + "/{id}/points/{pointId}/scenario", UUID.randomUUID(), UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"scenarioId\": \"" + scenarioId + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("O cenário não pertence a este ponto"));
  }
}
