package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.institution.InstitutionModel;

class DefaultDeviceAccessPolicyTests {

  private final DefaultDeviceAccessPolicy policy = new DefaultDeviceAccessPolicy();

  private static InstitutionModel institution(boolean active) {
    return InstitutionModel.builder().active(active).build();
  }

  private static DeviceModel device(DeviceStatus status, InstitutionModel institution) {
    return DeviceModel.builder().status(status).institution(institution).build();
  }

  @Test
  @DisplayName("Opera quando está ACTIVE e a instituição está ativa")
  void shouldOperateWhenActiveAndInstitutionActive() {
    assertTrue(policy.canOperate(device(DeviceStatus.ACTIVE, institution(true))));
  }

  @Test
  @DisplayName("Não opera quando está PENDING")
  void shouldNotOperateWhenPending() {
    assertFalse(policy.canOperate(device(DeviceStatus.PENDING, null)));
  }

  @Test
  @DisplayName("Não opera quando está INACTIVE")
  void shouldNotOperateWhenInactive() {
    assertFalse(policy.canOperate(device(DeviceStatus.INACTIVE, institution(true))));
  }

  @Test
  @DisplayName("Não opera quando a instituição está inativa, e volta a operar ao reativá-la")
  void shouldFollowInstitutionStatus() {
    InstitutionModel institution = institution(false);
    DeviceModel device = device(DeviceStatus.ACTIVE, institution);

    assertFalse(policy.canOperate(device));

    institution.setActive(true);

    assertTrue(policy.canOperate(device));
  }

  @Test
  @DisplayName("Não opera sem instituição ou sem dispositivo")
  void shouldNotOperateWithoutInstitutionOrDevice() {
    assertFalse(policy.canOperate(device(DeviceStatus.ACTIVE, null)));
    assertFalse(policy.canOperate(null));
  }
}
