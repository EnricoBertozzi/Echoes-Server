package com.n0hana.echoes_server.service.notifier;

import com.n0hana.echoes_server.dto.TwoFactorDto;

public interface TwoFactorNotifier {
  void send(TwoFactorDto dto);
}
