package com.n0hana.echoes_server.device.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.user.model.User;

@DisplayName("Logs de dispositivo: controller, handler e modelos")
class DeviceLogControllerAndSupportTests {

  private final DeviceLogIngestService ingestService = mock(DeviceLogIngestService.class);
  private final DeviceLogQueryService queryService = mock(DeviceLogQueryService.class);
  private final DeviceLogController controller = new DeviceLogController(ingestService, queryService);

  @Test
  @DisplayName("POST /me/logs responde 202 e delega ao serviço de ingestão")
  void ingestReturnsAccepted() {
    DeviceModel device = new DeviceModel();
    DeviceLogRequest request = new DeviceLogRequest("a1b2c3d4", 1L, 0, List.of());

    ResponseEntity<Void> response = controller.ingest(device, request);

    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    verify(ingestService).ingest(device, request);
  }

  @Test
  @DisplayName("GET /{id}/logs repassa filtros e devolve a página")
  void listDelegatesToQueryService() {
    UUID id = UUID.randomUUID();
    User user = mock(User.class);
    Instant from = Instant.parse("2026-11-20T00:00:00Z");
    Instant to = Instant.parse("2026-11-23T00:00:00Z");
    Page<DeviceLogResponse> page = new PageImpl<>(List.of());
    when(queryService.find(user, id, DeviceLogLevel.WARN, DeviceLogCategory.AUDIO, from, to, 2, 30)).thenReturn(page);

    ResponseEntity<Page<DeviceLogResponse>> response = controller.list(id, user, DeviceLogLevel.WARN,
        DeviceLogCategory.AUDIO, from, to, 2, 30);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(page, response.getBody());
  }

  @Test
  @DisplayName("handler devolve 429 com a mensagem da exceção")
  void exceptionHandlerReturns429() {
    TooManyDeviceLogsException exception = new TooManyDeviceLogsException();

    ResponseEntity<java.util.Map<String, String>> response = new DeviceLogExceptionHandler().handleTooMany(exception);

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    assertEquals(exception.getMessage(), response.getBody().get("message"));
    assertTrue(exception.getMessage().toLowerCase().contains("log"));
  }

  @Test
  @DisplayName("resposta espelha o modelo")
  void responseMirrorsModel() {
    DeviceLogModel model = new DeviceLogModel();
    model.setId(UUID.randomUUID());
    model.setOccurredAt(Instant.parse("2026-11-23T10:00:00Z"));
    model.setLevel(DeviceLogLevel.INFO);
    model.setCategory(DeviceLogCategory.REGISTER);
    model.setCode("REGISTERED");
    model.setMessage("ok");
    model.setOccurrences(4);
    model.setBootId("deadbeef");

    DeviceLogResponse response = DeviceLogResponse.from(model);

    assertEquals(model.getId(), response.id());
    assertEquals(model.getOccurredAt(), response.occurredAt());
    assertEquals("REGISTERED", response.code());
    assertEquals("ok", response.message());
    assertEquals(4, response.occurrences());
    assertEquals("deadbeef", response.bootId());
  }

  @Test
  @DisplayName("modelo nasce com 1 ocorrência e guarda todos os campos")
  void modelDefaultsAndAccessors() {
    DeviceLogModel model = new DeviceLogModel();
    UUID deviceId = UUID.randomUUID();
    UUID institutionId = UUID.randomUUID();
    Instant now = Instant.now();

    model.setDeviceId(deviceId);
    model.setInstitutionId(institutionId);
    model.setSeq(9);
    model.setReceivedAt(now);

    assertEquals(1, model.getOccurrences());
    assertEquals(deviceId, model.getDeviceId());
    assertEquals(institutionId, model.getInstitutionId());
    assertEquals(9, model.getSeq());
    assertEquals(now, model.getReceivedAt());
  }

  @Test
  @DisplayName("níveis são ordenados do menos ao mais grave")
  void levelsAreOrdered() {
    assertEquals(List.of("DEBUG", "INFO", "WARN", "ERROR"),
        java.util.Arrays.stream(DeviceLogLevel.values()).map(Enum::name).toList());
    assertEquals(7, DeviceLogCategory.values().length);
  }
}
