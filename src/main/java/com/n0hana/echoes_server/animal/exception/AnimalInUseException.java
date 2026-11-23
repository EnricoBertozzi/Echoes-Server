package com.n0hana.echoes_server.animal.exception;

public class AnimalInUseException extends RuntimeException {
  public AnimalInUseException() {
    super("O animal está associado a dispositivos e não pode ser removido. Desative-o.");
  }
}
