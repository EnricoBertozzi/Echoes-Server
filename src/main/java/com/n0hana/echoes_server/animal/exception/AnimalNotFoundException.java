package com.n0hana.echoes_server.animal.exception;

public class AnimalNotFoundException extends RuntimeException {
  public AnimalNotFoundException() {
    super("Animal não encontrado");
  }
}
