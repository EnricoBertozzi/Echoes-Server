package com.n0hana.echoes_server.scenario.exception;

public class ScenarioNotFoundException extends RuntimeException {
  public ScenarioNotFoundException() {
    super("Cenário não encontrado");
  }
}
