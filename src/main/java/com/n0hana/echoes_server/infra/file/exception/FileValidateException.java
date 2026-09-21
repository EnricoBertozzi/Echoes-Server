package com.n0hana.echoes_server.infra.file.exception;

public class FileValidateException extends RuntimeException {
  public FileValidateException() {
    super("Arquivo Inválido");
  }
}