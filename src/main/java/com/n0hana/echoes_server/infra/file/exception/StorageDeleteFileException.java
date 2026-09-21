package com.n0hana.echoes_server.infra.file.exception;

public class StorageDeleteFileException extends RuntimeException {
  public StorageDeleteFileException() {
    super("Falha ao deletar o arquivo");
  }
}
