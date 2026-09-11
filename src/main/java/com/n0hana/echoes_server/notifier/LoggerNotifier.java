package com.n0hana.echoes_server.notifier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.mfa.TwoFactorDTO;

@Service
@ConditionalOnProperty(name = "twofactor.provider", havingValue = "logger")
public class LoggerNotifier implements TwoFactorNotifier {
  @Override
  public void send(TwoFactorDTO dto) {
    System.out.println(dto.email() + " : " + dto.code());
  
  }
}
