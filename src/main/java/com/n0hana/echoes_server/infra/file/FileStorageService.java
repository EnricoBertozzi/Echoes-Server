package com.n0hana.echoes_server.infra.file;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

  private Path fileStorageLocation;

  public FileStorageService(FileStorageProperties fileStorageProperties) {
    this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
        .toAbsolutePath()
        .normalize();
  }

  public Path saveFile(MultipartFile file, String newName) {
    String fileName = StringUtils.cleanPath(newName);

    try {
      Path targetLocation = fileStorageLocation.resolve(fileName);

      file.transferTo(targetLocation);
      return targetLocation;
    } catch (IOException e) {
      throw new RuntimeException("Erro ao salvar o arquivo");
    }
  }

  public Path findFile(String fileName) {
    return fileStorageLocation
        .resolve(fileName)
        .normalize();
  }
}
