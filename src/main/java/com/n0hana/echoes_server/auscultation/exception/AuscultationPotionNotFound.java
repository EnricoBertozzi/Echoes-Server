package com.n0hana.echoes_server.auscultation.exception;

public class AuscultationPotionNotFound extends RuntimeException {
  public AuscultationPotionNotFound() {
    super("Ponto de ausculta não encontrado");
  }
}
