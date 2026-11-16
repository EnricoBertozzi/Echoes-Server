package com.n0hana.echoes_server.device;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.device.port.DeviceAccessPolicy;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.institution.InstitutionService;

/**
 * Prova contra o banco que uma instituição inativa ou excluída (soft delete)
 * impede o dispositivo de operar. Soft delete só é aplicado pelo Hibernate
 * contra um banco real, então mocks não bastam.
 */
@SpringBootTest
@ActiveProfiles("test")
class DeviceInstitutionBlockIntegrationTest {

  private static final String HARDWARE_ID = "AA:BB:CC:DD:EE:77";

  @Autowired
  private DeviceRepository deviceRepository;
  @Autowired
  private InstitutionRepository institutionRepository;
  @Autowired
  private InstitutionService institutionService;
  @Autowired
  private DeviceAccessPolicy accessPolicy;
  @Autowired
  private JdbcTemplate jdbc;

  private InstitutionModel institution;

  @BeforeEach
  void setUp() {
    // Native query: deleteAll() respeita @SQLRestriction e não limpa soft-deleted
    jdbc.update("DELETE FROM device_tb");
    jdbc.update("DELETE FROM institutions");

    institution = institutionRepository.saveAndFlush(InstitutionModel.builder()
        .name("Instituição Bloqueio")
        .acronym("IB")
        .cnpj("19131243000197")
        .email("bloqueio@teste.com")
        .build());

    deviceRepository.saveAndFlush(DeviceModel.builder()
        .name("ESP de teste")
        .hardwareId(HARDWARE_ID)
        .publicKey("pem")
        .status(DeviceStatus.ACTIVE)
        .institution(institution)
        .build());
  }

  private DeviceModel reload() {
    return deviceRepository.findByHardwareId(HARDWARE_ID).orElseThrow();
  }

  @Test
  @DisplayName("Dispositivo ativo de instituição ativa opera")
  void activeDeviceOfActiveInstitutionOperates() {
    assertThat(accessPolicy.canOperate(reload())).isTrue();
  }

  @Test
  @DisplayName("Instituição inativada → dispositivo deixa de operar")
  void inactiveInstitutionBlocksDevice() {
    institutionService.toggleStatus(institution.getId());

    assertThat(accessPolicy.canOperate(reload())).isFalse();
  }

  @Test
  @DisplayName("Instituição soft-deleted → dispositivo carrega sem erro e não opera")
  void softDeletedInstitutionBlocksDevice() {
    institutionService.delete(institution.getId());

    DeviceModel device = reload();

    assertThat(device.getInstitution()).isNull();
    assertThat(accessPolicy.canOperate(device)).isFalse();
  }

  @Test
  @DisplayName("Listagem de dispositivos não quebra com instituição soft-deleted")
  void listingSurvivesSoftDeletedInstitution() {
    institutionService.delete(institution.getId());

    assertThat(deviceRepository.findAll(org.springframework.data.domain.Pageable.unpaged()))
        .hasSize(1);
  }
}
