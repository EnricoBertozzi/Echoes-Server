package com.n0hana.echoes_server.infra.file.exception;

public class StorageSaveFileException extends RuntimeException {
  public StorageSaveFileException() {
    super("Arquivo não encontrado");
  }
}
