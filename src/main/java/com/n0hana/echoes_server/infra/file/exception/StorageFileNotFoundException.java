package com.n0hana.echoes_server.infra.file.exception;

public class StorageFileNotFoundException extends RuntimeException {
  public StorageFileNotFoundException() {
    super("Falha ao salvar o arquivo");
  }
}
