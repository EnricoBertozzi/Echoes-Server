package com.n0hana.echoes_server.scenario.exception;

public class ScenarioInUseException extends RuntimeException {
  public ScenarioInUseException() {
    super("O cenário está associado a dispositivos e não pode ser removido. Desative-o.");
  }
}
