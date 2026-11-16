package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;

class DefaultDeviceScopeResolverTests {

  private final DefaultDeviceScopeResolver resolver = new DefaultDeviceScopeResolver();
  private final UUID institutionId = UUID.randomUUID();

  private InstitutionModel institution() {
    return InstitutionModel.builder().id(institutionId).build();
  }

  @Test
  @DisplayName("ADMIN tem escopo global")
  void adminIsAll() {
    assertInstanceOf(DeviceScope.All.class, resolver.resolve(new Admin()));
  }

  @Test
  @DisplayName("Gestor tem escopo da própria instituição")
  void managerIsInstitution() {
    Manager manager = new Manager();
    manager.setInstitution(institution());

    DeviceScope scope = resolver.resolve(manager);

    assertEquals(institutionId, assertInstanceOf(DeviceScope.InstitutionScope.class, scope).institutionId());
  }

  @Test
  @DisplayName("Professor tem escopo da própria instituição")
  void teacherIsInstitution() {
    Teacher teacher = new Teacher();
    teacher.setInstitution(institution());

    assertInstanceOf(DeviceScope.InstitutionScope.class, resolver.resolve(teacher));
  }

  @Test
  @DisplayName("Estudante e gestor sem instituição não têm escopo")
  void noScope() {
    assertThrows(AccessDeniedException.class, () -> resolver.resolve(new Student()));
    assertThrows(AccessDeniedException.class, () -> resolver.resolve(new Manager()));
    assertThrows(AccessDeniedException.class, () -> resolver.resolve(null));
  }
}
