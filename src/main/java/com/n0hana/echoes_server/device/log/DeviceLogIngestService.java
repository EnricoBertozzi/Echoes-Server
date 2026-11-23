package com.n0hana.echoes_server.device.log;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.institution.InstitutionModel;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Grava os lotes de log de um dispositivo autenticado.
 *
 * <p>
 * Regras: reenvio de lote é idempotente (dispositivo + boot + seq); o horário
 * de cada evento vem do relógio do servidor; o texto é saneado (sem
 * caracteres de controle) e truncado; e há um limite de lotes por minuto por
 * dispositivo para um firmware com defeito não encher o banco.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceLogIngestService {

  static final int MAX_MESSAGE = 300;
  private static final int BATCHES_PER_MINUTE = 30;
  private static final long UINT32 = 0xFFFFFFFFL;
  /** Evento mais velho que isso vira "agora": o uptime informado não é plausível. */
  private static final Duration MAX_AGE = Duration.ofDays(8);

  private final DeviceLogRepository repository;

  private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

  public void ingest(DeviceModel device, DeviceLogRequest request) {
    if (device == null)
      throw new AccessDeniedException("Dispositivo não autenticado");

    InstitutionModel institution = device.getInstitution();
    if (institution == null)
      throw new AccessDeniedException("Dispositivo sem instituição");

    if (!buckets.computeIfAbsent(device.getId(), id -> newBucket()).tryConsume(1))
      throw new TooManyDeviceLogsException();

    Instant received = Instant.now();

    Map<Long, DeviceLogRequest.Event> bySeq = new LinkedHashMap<>();
    for (DeviceLogRequest.Event event : request.events())
      bySeq.putIfAbsent(event.seq(), event);

    Set<Long> existing = Set.copyOf(repository.findExistingSeqs(device.getId(), request.bootId(), bySeq.keySet()));

    List<DeviceLogModel> rows = new ArrayList<>();
    long lastSeq = 0;
    for (DeviceLogRequest.Event event : bySeq.values()) {
      lastSeq = Math.max(lastSeq, event.seq());
      if (existing.contains(event.seq()))
        continue;
      rows.add(toRow(device, institution, request, event, received));
    }

    int dropped = request.dropped() == null ? 0 : request.dropped();
    if (dropped > 0 && !existing.contains(-lastSeq)) {
      // seq negativo = aviso gerado pelo servidor; único por lote, então também é idempotente
      DeviceLogModel notice = base(device, institution, request.bootId(), -lastSeq, received);
      notice.setOccurredAt(received);
      notice.setLevel(DeviceLogLevel.WARN);
      notice.setCategory(DeviceLogCategory.SYSTEM);
      notice.setCode("LOGS_DROPPED");
      notice.setMessage(dropped + " log(s) antigo(s) foram descartados no dispositivo porque a fila encheu "
          + "(ficou muito tempo offline ou gerou eventos demais).");
      rows.add(notice);
    }

    if (rows.isEmpty())
      return;

    try {
      repository.saveAll(rows);
    } catch (DataIntegrityViolationException race) {
      // Lote idêntico chegou ao mesmo tempo: grava um a um e ignora os já existentes
      for (DeviceLogModel row : rows) {
        try {
          repository.save(row);
        } catch (DataIntegrityViolationException duplicate) {
          log.debug("Log de dispositivo duplicado ignorado");
        }
      }
    }
  }

  private DeviceLogModel toRow(DeviceModel device, InstitutionModel institution, DeviceLogRequest request,
      DeviceLogRequest.Event event, Instant received) {
    DeviceLogModel row = base(device, institution, request.bootId(), event.seq(), received);
    row.setOccurredAt(occurredAt(received, request.up(), event.up()));
    row.setLevel(event.lvl());
    row.setCategory(event.cat());
    row.setCode(event.code());
    row.setMessage(sanitize(event.msg()));
    row.setOccurrences(event.n() == null || event.n() < 1 ? 1 : event.n());
    return row;
  }

  private DeviceLogModel base(DeviceModel device, InstitutionModel institution, String bootId, long seq,
      Instant received) {
    DeviceLogModel row = new DeviceLogModel();
    row.setDeviceId(device.getId());
    row.setInstitutionId(institution.getId());
    row.setBootId(bootId);
    row.setSeq(seq);
    row.setReceivedAt(received);
    return row;
  }

  /** millis() do ESP é 32 bits e dá a volta a cada ~49 dias; a subtração mascarada cobre isso. */
  static Instant occurredAt(Instant received, long batchUp, long eventUp) {
    long ageMs = (batchUp - eventUp) & UINT32;
    Duration age = Duration.ofMillis(ageMs);
    return age.compareTo(MAX_AGE) > 0 ? received : received.minus(age);
  }

  /** Remove caracteres de controle (evita forjar linhas de log) e limita o tamanho. */
  static String sanitize(String text) {
    String cleaned = text.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").strip();
    if (cleaned.codePointCount(0, cleaned.length()) > MAX_MESSAGE) {
      int end = cleaned.offsetByCodePoints(0, MAX_MESSAGE);
      cleaned = cleaned.substring(0, end);
    }
    return cleaned.isEmpty() ? "-" : cleaned;
  }

  private static Bucket newBucket() {
    return Bucket.builder()
        .addLimit(Bandwidth.builder()
            .capacity(BATCHES_PER_MINUTE)
            .refillGreedy(BATCHES_PER_MINUTE, Duration.ofMinutes(1))
            .build())
        .build();
  }
}
