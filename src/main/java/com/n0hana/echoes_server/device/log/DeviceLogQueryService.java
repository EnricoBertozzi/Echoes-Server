package com.n0hana.echoes_server.device.log;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceScope;
import com.n0hana.echoes_server.device.DeviceService;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.infra.web.PageLimits;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * Consulta de logs por professor, gestor e admin.
 *
 * <p>
 * O dispositivo é buscado com o escopo do usuário (outra instituição = 404, como
 * nas demais telas); o professor ainda precisa ser membro da turma do
 * dispositivo. Os logs ainda são filtrados pela instituição gravada no
 * momento do log, para o histórico anterior a uma mudança de instituição não
 * vazar para a nova.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DeviceLogQueryService {

  private final DeviceLogRepository repository;
  private final DeviceService deviceService;
  private final DeviceScopeResolver scopeResolver;
  private final ClassroomMemberRepository memberRepository;

  @Value("${device-log.retention-days:7}")
  private int retentionDays = 7;

  /**
   * Professor só vê logs de dispositivo vinculado a uma turma da qual é membro.
   * Responde 404 (não 403) para não revelar que o dispositivo existe.
   */
  private void requireTeacherClassroom(User user, DeviceModel device) {
    if (user.getUserRole() != UserRole.TEACHER)
      return;
    if (device.getClassroom() == null
        || !memberRepository.existsByClassroomIdAndUserId(device.getClassroom().getId(), user.getId()))
      throw new DeviceNotFoundException();
  }

  @Transactional(readOnly = true)
  public Page<DeviceLogResponse> find(User user, UUID deviceId, DeviceLogLevel minLevel,
      DeviceLogCategory category, Instant from, Instant to, int page, int size) {
    DeviceModel device = deviceService.findDeviceById(deviceId, user); // 404 fora do escopo
    requireTeacherClassroom(user, device);
    DeviceScope scope = scopeResolver.resolve(user);

    // Nada mais velho que a retenção, mesmo que o expurgo ainda não tenha rodado
    Instant floor = Instant.now().minus(Duration.ofDays(retentionDays));
    Instant effectiveFrom = from == null || from.isBefore(floor) ? floor : from;

    Specification<DeviceLogModel> spec = (root, query, cb) -> {
      List<Predicate> where = new ArrayList<>();
      where.add(cb.equal(root.get("deviceId"), deviceId));
      where.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), effectiveFrom));
      if (scope instanceof DeviceScope.InstitutionScope institution)
        where.add(cb.equal(root.get("institutionId"), institution.institutionId()));
      if (to != null)
        where.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
      if (category != null)
        where.add(cb.equal(root.get("category"), category));
      if (minLevel != null)
        where.add(root.get("level").in(EnumSet.range(minLevel, DeviceLogLevel.ERROR)));
      return cb.and(where.toArray(Predicate[]::new));
    };

    PageRequest pageable = PageRequest.of(PageLimits.clampPage(page), PageLimits.clamp(size),
        Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("seq")));

    return repository.findAll(spec, pageable).map(DeviceLogResponse::from);
  }
}
