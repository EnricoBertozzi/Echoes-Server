package com.n0hana.echoes_server.device;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserUtils;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

@Component
public class DefaultDeviceScopeResolver implements DeviceScopeResolver {

  @Override
  public DeviceScope resolve(User user) {
    if (user == null)
      throw new AccessDeniedException("Usuário não autenticado");

    UserRole role = user.getUserRole();

    if (role == UserRole.ADMIN)
      return new DeviceScope.All();

    if (role == UserRole.MANAGER || role == UserRole.TEACHER) {
      InstitutionModel institution = UserUtils.institutionOf(user);
      if (institution != null)
        return new DeviceScope.InstitutionScope(institution.getId());
    }

    throw new AccessDeniedException("Sem acesso a dispositivos");
  }
}
