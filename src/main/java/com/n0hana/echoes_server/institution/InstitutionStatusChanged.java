package com.n0hana.echoes_server.institution;

import java.util.UUID;

/**
 * Publicado quando uma instituição é ativada, inativada ou excluída (soft
 * delete). Os dispositivos dela deixam de operar; quem precisar reagir (ex.:
 * avisar os ESPs por MQTT) escuta este evento.
 *
 * @param institutionId instituição afetada
 * @param active        estado ativo depois da mudança
 * @param deleted       true quando a instituição foi excluída
 */
public record InstitutionStatusChanged(UUID institutionId, boolean active, boolean deleted) {

  /** Os dispositivos da instituição só operam se ela estiver ativa e existente. */
  public boolean blocksDevices() {
    return deleted || !active;
  }
}
