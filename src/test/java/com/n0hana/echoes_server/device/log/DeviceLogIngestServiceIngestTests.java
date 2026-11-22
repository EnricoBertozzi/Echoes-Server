package com.n0hana.echoes_server.device.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.institution.InstitutionModel;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceLogIngestService.ingest")
class DeviceLogIngestServiceIngestTests {

  private static final String BOOT = "a1b2c3d4";

  @Mock
  private DeviceLogRepository repository;

  @Captor
  private ArgumentCaptor<List<DeviceLogModel>> rowsCaptor;

  @Captor
  private ArgumentCaptor<Collection<Long>> lookupCaptor;

  private DeviceLogIngestService service;
  private DeviceModel device;
  private UUID institutionId;

  @BeforeEach
  void setUp() {
    service = new DeviceLogIngestService(repository);
    institutionId = UUID.randomUUID();
    device = deviceOf(UUID.randomUUID());
  }

  private DeviceModel deviceOf(UUID id) {
    InstitutionModel institution = mock(InstitutionModel.class);
    lenient().when(institution.getId()).thenReturn(institutionId);
    DeviceModel model = new DeviceModel();
    model.setId(id);
    model.setInstitution(institution);
    return model;
  }

  private static DeviceLogRequest.Event event(long seq, long up, String msg, Integer n) {
    return new DeviceLogRequest.Event(seq, up, DeviceLogLevel.WARN, DeviceLogCategory.WIFI, "WIFI_DISCONNECTED", msg,
        n);
  }

  private static DeviceLogRequest request(Integer dropped, DeviceLogRequest.Event... events) {
    return new DeviceLogRequest(BOOT, 70_000L, dropped, List.of(events));
  }

  private List<DeviceLogModel> savedRows() {
    verify(repository).saveAll(rowsCaptor.capture());
    return rowsCaptor.getValue();
  }

  @Test
  @DisplayName("recusa dispositivo nulo")
  void rejectsNullDevice() {
    assertThrows(AccessDeniedException.class, () -> service.ingest(null, request(null, event(1, 1, "x", 1))));
  }

  @Test
  @DisplayName("recusa dispositivo sem instituição")
  void rejectsDeviceWithoutInstitution() {
    DeviceModel orphan = new DeviceModel();
    orphan.setId(UUID.randomUUID());

    assertThrows(AccessDeniedException.class, () -> service.ingest(orphan, request(null, event(1, 1, "x", 1))));
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("grava o evento com instituição, horário do servidor e texto saneado")
  void persistsNewEvents() {
    service.ingest(device, request(null, event(5, 60_000, "caiu\nFAKE linha\r", 3)));

    List<DeviceLogModel> rows = savedRows();
    assertEquals(1, rows.size());
    DeviceLogModel row = rows.get(0);
    assertEquals(device.getId(), row.getDeviceId());
    assertEquals(institutionId, row.getInstitutionId());
    assertEquals(BOOT, row.getBootId());
    assertEquals(5, row.getSeq());
    assertEquals(DeviceLogLevel.WARN, row.getLevel());
    assertEquals(DeviceLogCategory.WIFI, row.getCategory());
    assertEquals("WIFI_DISCONNECTED", row.getCode());
    assertEquals(3, row.getOccurrences());
    assertEquals("caiu FAKE linha", row.getMessage());
    // lote enviado com up=70s e evento com up=60s => aconteceu 10 s antes de chegar
    assertEquals(Duration.ofSeconds(10), Duration.between(row.getOccurredAt(), row.getReceivedAt()));
  }

  @Test
  @DisplayName("ocorrências ausentes ou menores que 1 viram 1")
  void normalizesOccurrences() {
    service.ingest(device, request(null, event(1, 1, "a", null), event(2, 1, "b", 0), event(3, 1, "c", 7)));

    List<DeviceLogModel> rows = savedRows();
    assertEquals(1, rows.get(0).getOccurrences());
    assertEquals(1, rows.get(1).getOccurrences());
    assertEquals(7, rows.get(2).getOccurrences());
  }

  @Test
  @DisplayName("ignora seq que já foi gravado (reenvio de lote)")
  void skipsExistingSeqs() {
    when(repository.findExistingSeqs(eq(device.getId()), eq(BOOT), anyCollection())).thenReturn(List.of(1L));

    service.ingest(device, request(null, event(1, 1, "a", 1), event(2, 1, "b", 1)));

    List<DeviceLogModel> rows = savedRows();
    assertEquals(1, rows.size());
    assertEquals(2, rows.get(0).getSeq());
  }

  @Test
  @DisplayName("descarta seq repetido dentro do mesmo lote")
  void dropsDuplicatedSeqInsideBatch() {
    service.ingest(device, request(null, event(4, 1, "primeiro", 1), event(4, 1, "repetido", 1)));

    List<DeviceLogModel> rows = savedRows();
    assertEquals(1, rows.size());
    assertEquals("primeiro", rows.get(0).getMessage());
  }

  @Test
  @DisplayName("não grava nada quando todos os eventos já existiam")
  void savesNothingWhenEverythingExists() {
    when(repository.findExistingSeqs(eq(device.getId()), eq(BOOT), anyCollection())).thenReturn(List.of(1L));

    service.ingest(device, request(null, event(1, 1, "a", 1)));

    verify(repository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("cria o aviso LOGS_DROPPED com seq negativo quando o ESP descartou logs")
  void addsDroppedNotice() {
    service.ingest(device, request(5, event(1, 1, "a", 1), event(2, 1, "b", 1)));

    List<DeviceLogModel> rows = savedRows();
    assertEquals(3, rows.size());
    DeviceLogModel notice = rows.get(2);
    assertEquals(-3, notice.getSeq());
    assertEquals("LOGS_DROPPED", notice.getCode());
    assertEquals(DeviceLogLevel.WARN, notice.getLevel());
    assertEquals(DeviceLogCategory.SYSTEM, notice.getCategory());
    assertTrue(notice.getMessage().startsWith("5 log(s)"));
    assertEquals(notice.getReceivedAt(), notice.getOccurredAt());

    verify(repository).findExistingSeqs(eq(device.getId()), eq(BOOT), lookupCaptor.capture());
    assertTrue(lookupCaptor.getValue().contains(-3L), "a consulta de idempotência inclui o aviso");
  }

  @Test
  @DisplayName("não repete o aviso LOGS_DROPPED em um reenvio")
  void doesNotRepeatDroppedNotice() {
    when(repository.findExistingSeqs(eq(device.getId()), eq(BOOT), anyCollection())).thenReturn(List.of(1L, -2L));

    service.ingest(device, request(5, event(1, 1, "a", 1)));

    verify(repository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("seq 0 não colide com o seq do aviso")
  void noticeSeqNeverCollidesWithSeqZero() {
    service.ingest(device, request(2, event(0, 1, "a", 1)));

    List<DeviceLogModel> rows = savedRows();
    assertEquals(0, rows.get(0).getSeq());
    assertEquals(-1, rows.get(1).getSeq());
  }

  @Test
  @DisplayName("sem dropped informado não cria aviso")
  void zeroDroppedAddsNoNotice() {
    service.ingest(device, request(0, event(1, 1, "a", 1)));

    assertEquals(1, savedRows().size());
  }

  @Test
  @DisplayName("em corrida de lotes idênticos grava um a um e ignora duplicados")
  void fallsBackToOneByOneOnRace() {
    doThrow(new DataIntegrityViolationException("uk")).when(repository).saveAll(anyList());
    when(repository.save(org.mockito.ArgumentMatchers.any(DeviceLogModel.class)))
        .thenThrow(new DataIntegrityViolationException("duplicado"))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.ingest(device, request(null, event(1, 1, "a", 1), event(2, 1, "b", 1)));

    verify(repository, times(2)).save(org.mockito.ArgumentMatchers.any(DeviceLogModel.class));
  }

  @Test
  @DisplayName("limita a 30 lotes por minuto por dispositivo")
  void rateLimitsPerDevice() {
    DeviceLogRequest request = request(null, event(1, 1, "a", 1));
    for (int i = 0; i < 30; i++)
      service.ingest(device, request);

    assertThrows(TooManyDeviceLogsException.class, () -> service.ingest(device, request));

    // outro dispositivo tem o próprio limite
    DeviceModel other = deviceOf(UUID.randomUUID());
    service.ingest(other, request);
  }
}
