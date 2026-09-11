package com.n0hana.echoes_server.notifier;

import com.n0hana.echoes_server.mfa.TwoFactorDTO;

public interface TwoFactorNotifier {
  void send(TwoFactorDTO dto);
}
