package com.n0hana.echoes_server.auscultation.exception;

public class AuscultationPointNotFound extends RuntimeException {
  public AuscultationPointNotFound() {
    super("Ponto de ausculta não encontrado");
  }
}
