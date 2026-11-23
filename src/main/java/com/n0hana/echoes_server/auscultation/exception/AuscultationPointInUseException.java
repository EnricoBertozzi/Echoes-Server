package com.n0hana.echoes_server.auscultation.exception;

public class AuscultationPointInUseException extends RuntimeException {
  public AuscultationPointInUseException() {
    super("O ponto de ausculta está associado a dispositivos e não pode ser removido. Desative-o.");
  }
}
