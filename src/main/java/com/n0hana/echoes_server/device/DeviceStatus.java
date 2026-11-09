package com.n0hana.echoes_server.device;

/**
 * Estado do ciclo de vida de um dispositivo.
 *
 * <ul>
 * <li>{@link #PENDING}: autocadastrado, aguardando aprovação do admin; não
 * tem instituição e não pode operar.</li>
 * <li>{@link #ACTIVE}: aprovado e associado a uma instituição.</li>
 * <li>{@link #INACTIVE}: bloqueado pelo admin.</li>
 * </ul>
 *
 * <p>
 * Estar {@code ACTIVE} não basta para operar: a instituição também precisa
 * estar ativa. Essa regra combinada fica em
 * {@link com.n0hana.echoes_server.device.port.DeviceAccessPolicy}.
 * </p>
 */
public enum DeviceStatus {
  PENDING,
  ACTIVE,
  INACTIVE
}
