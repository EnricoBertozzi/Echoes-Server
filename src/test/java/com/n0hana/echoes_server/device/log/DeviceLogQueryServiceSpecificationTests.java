package com.n0hana.echoes_server.device.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceScope;
import com.n0hana.echoes_server.device.DeviceService;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

@DisplayName("DeviceLogQueryService: filtros e paginação")
@SuppressWarnings({ "unchecked", "rawtypes" })
class DeviceLogQueryServiceSpecificationTests {

  private final DeviceLogRepository repository = mock(DeviceLogRepository.class);
  private final DeviceService deviceService = mock(DeviceService.class);
  private final DeviceScopeResolver scopeResolver = mock(DeviceScopeResolver.class);
  private final ClassroomMemberRepository members = mock(ClassroomMemberRepository.class);
  private final User manager = mock(User.class);

  private final DeviceLogQueryService service = new DeviceLogQueryService(repository, deviceService, scopeResolver,
      members);

  private final UUID deviceId = UUID.randomUUID();
  private final UUID institutionId = UUID.randomUUID();

  private final Root root = mock(Root.class);
  private final CriteriaQuery query = mock(CriteriaQuery.class);
  private final CriteriaBuilder cb = mock(CriteriaBuilder.class, Mockito.RETURNS_MOCKS);
  private final Path levelPath = mock(Path.class);
  private final Path institutionPath = mock(Path.class);

  @BeforeEach
  void setUp() {
    when(manager.getUserRole()).thenReturn(UserRole.MANAGER);
    when(deviceService.findDeviceById(deviceId, manager)).thenReturn(new DeviceModel());
    when(scopeResolver.resolve(manager)).thenReturn(new DeviceScope.InstitutionScope(institutionId));
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
    when(root.get("level")).thenReturn(levelPath);
    when(root.get("institutionId")).thenReturn(institutionPath);
    when(root.get(Mockito.argThat((String s) -> !"level".equals(s) && !"institutionId".equals(s))))
        .thenReturn(mock(Path.class));
  }

  private Specification<DeviceLogModel> capturedSpec() {
    ArgumentCaptor<Specification<DeviceLogModel>> captor = ArgumentCaptor.forClass(Specification.class);
    verify(repository).findAll(captor.capture(), any(Pageable.class));
    return captor.getValue();
  }

  private Pageable capturedPageable() {
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(any(Specification.class), captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("gestor: filtra pela instituição do escopo")
  void managerIsRestrictedToInstitution() {
    service.find(manager, deviceId, null, null, null, null, 0, 20);

    capturedSpec().toPredicate(root, query, cb);

    verify(cb).equal(institutionPath, institutionId);
  }

  @Test
  @DisplayName("admin: sem filtro de instituição")
  void adminSeesAllInstitutions() {
    when(scopeResolver.resolve(manager)).thenReturn(new DeviceScope.All());

    service.find(manager, deviceId, null, null, null, null, 0, 20);

    capturedSpec().toPredicate(root, query, cb);

    verify(root, never()).get("institutionId");
  }

  @Test
  @DisplayName("minLevel WARN traz WARN e ERROR")
  void minLevelIncludesMoreSevere() {
    service.find(manager, deviceId, DeviceLogLevel.WARN, null, null, null, 0, 20);

    capturedSpec().toPredicate(root, query, cb);

    verify(levelPath).in((Collection<?>) EnumSet.of(DeviceLogLevel.WARN, DeviceLogLevel.ERROR));
  }

  @Test
  @DisplayName("sem minLevel não filtra por nível")
  void noLevelFilterByDefault() {
    service.find(manager, deviceId, null, null, null, null, 0, 20);

    capturedSpec().toPredicate(root, query, cb);

    verify(levelPath, never()).in(any(Collection.class));
  }

  @Test
  @DisplayName("categoria e limite superior entram no filtro quando informados")
  void categoryAndUpperBoundAreApplied() {
    Instant to = Instant.now();

    service.find(manager, deviceId, null, DeviceLogCategory.MQTT, null, to, 0, 20);

    capturedSpec().toPredicate(root, query, cb);

    verify(cb).equal(any(), org.mockito.ArgumentMatchers.eq(DeviceLogCategory.MQTT));
    verify(cb).lessThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class),
        org.mockito.ArgumentMatchers.eq(to));
  }

  @Test
  @DisplayName("sem 'from' usa o limite da retenção (7 dias)")
  void defaultsToRetentionFloor() {
    service.find(manager, deviceId, null, null, null, null, 0, 20);

    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    capturedSpec().toPredicate(root, query, cb);
    verify(cb).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), from.capture());

    Duration age = Duration.between(from.getValue(), Instant.now());
    assertTrue(age.compareTo(Duration.ofDays(7)) >= 0 && age.compareTo(Duration.ofDays(7).plusMinutes(1)) < 0);
  }

  @Test
  @DisplayName("'from' mais antigo que a retenção é trocado pelo limite")
  void fromOlderThanRetentionIsClamped() {
    Instant tooOld = Instant.now().minus(Duration.ofDays(30));

    service.find(manager, deviceId, null, null, tooOld, null, 0, 20);

    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    capturedSpec().toPredicate(root, query, cb);
    verify(cb).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), from.capture());

    assertTrue(from.getValue().isAfter(tooOld));
  }

  @Test
  @DisplayName("'from' recente dentro da retenção é respeitado")
  void recentFromIsKept() {
    Instant recent = Instant.now().minus(Duration.ofHours(2));

    service.find(manager, deviceId, null, null, recent, null, 0, 20);

    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    capturedSpec().toPredicate(root, query, cb);
    verify(cb).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), from.capture());

    assertEquals(recent, from.getValue());
  }

  @Test
  @DisplayName("prazo de retenção configurável")
  void retentionIsConfigurable() {
    ReflectionTestUtils.setField(service, "retentionDays", 1);

    service.find(manager, deviceId, null, null, null, null, 0, 20);

    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    capturedSpec().toPredicate(root, query, cb);
    verify(cb).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), from.capture());

    assertTrue(Duration.between(from.getValue(), Instant.now()).compareTo(Duration.ofDays(2)) < 0);
  }

  @Test
  @DisplayName("limita página e tamanho e ordena do mais recente ao mais antigo")
  void clampsPagingAndSortsNewestFirst() {
    service.find(manager, deviceId, null, null, null, null, -5, 5000);

    Pageable pageable = capturedPageable();
    assertEquals(0, pageable.getPageNumber());
    assertEquals(100, pageable.getPageSize());
    assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("occurredAt").getDirection());
    assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("seq").getDirection());
  }

  @Test
  @DisplayName("converte as linhas da página em respostas")
  void mapsRowsToResponses() {
    DeviceLogModel row = new DeviceLogModel();
    row.setId(UUID.randomUUID());
    row.setOccurredAt(Instant.parse("2026-11-23T12:00:00Z"));
    row.setLevel(DeviceLogLevel.ERROR);
    row.setCategory(DeviceLogCategory.SYNC);
    row.setCode("MANIFEST_DENIED");
    row.setMessage("msg");
    row.setOccurrences(2);
    row.setBootId("a1b2c3d4");
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row)));

    Page<DeviceLogResponse> page = service.find(manager, deviceId, null, null, null, null, 0, 20);

    DeviceLogResponse response = page.getContent().get(0);
    assertEquals(row.getId(), response.id());
    assertEquals("MANIFEST_DENIED", response.code());
    assertEquals(DeviceLogLevel.ERROR, response.level());
    assertEquals(DeviceLogCategory.SYNC, response.category());
    assertEquals(2, response.occurrences());
    assertEquals("a1b2c3d4", response.bootId());
    assertFalse(page.isEmpty());
  }
}
