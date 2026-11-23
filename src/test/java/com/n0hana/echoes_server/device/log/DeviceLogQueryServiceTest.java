package com.n0hana.echoes_server.device.log;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceScope;
import com.n0hana.echoes_server.device.DeviceService;
import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

class DeviceLogQueryServiceTest {

  private final DeviceLogRepository repository = mock(DeviceLogRepository.class);
  private final DeviceService deviceService = mock(DeviceService.class);
  private final DeviceScopeResolver scopeResolver = mock(DeviceScopeResolver.class);
  private final ClassroomMemberRepository members = mock(ClassroomMemberRepository.class);
  private final DeviceLogQueryService service = new DeviceLogQueryService(repository, deviceService, scopeResolver,
      members);

  private final UUID deviceId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final User user = mock(User.class);
  private final DeviceModel device = new DeviceModel();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    when(user.getId()).thenReturn(userId);
    when(deviceService.findDeviceById(deviceId, user)).thenReturn(device);
    when(scopeResolver.resolve(user)).thenReturn(new DeviceScope.InstitutionScope(UUID.randomUUID()));
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
  }

  private void withClassroom() {
    ClassroomModel classroom = mock(ClassroomModel.class);
    when(classroom.getId()).thenReturn(classroomId);
    device.setClassroom(classroom);
  }

  @Test
  void teacherWithoutClassroomOnDeviceGets404() {
    when(user.getUserRole()).thenReturn(UserRole.TEACHER);

    assertThrows(DeviceNotFoundException.class,
        () -> service.find(user, deviceId, null, null, null, null, 0, 20));
  }

  @Test
  @SuppressWarnings("unchecked")
  void teacherNotMemberOfTheDeviceClassroomGets404() {
    when(user.getUserRole()).thenReturn(UserRole.TEACHER);
    withClassroom();
    when(members.existsByClassroomIdAndUserId(classroomId, userId)).thenReturn(false);

    assertThrows(DeviceNotFoundException.class,
        () -> service.find(user, deviceId, null, null, null, null, 0, 20));
    verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void teacherMemberOfTheDeviceClassroomCanRead() {
    when(user.getUserRole()).thenReturn(UserRole.TEACHER);
    withClassroom();
    when(members.existsByClassroomIdAndUserId(classroomId, userId)).thenReturn(true);

    service.find(user, deviceId, null, null, null, null, 0, 20);

    verify(repository).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void managerDoesNotNeedAClassroom() {
    when(user.getUserRole()).thenReturn(UserRole.MANAGER);

    service.find(user, deviceId, null, null, null, null, 0, 20);

    verify(repository).findAll(any(Specification.class), any(Pageable.class));
  }
}
