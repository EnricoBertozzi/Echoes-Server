package com.n0hana.echoes_server.infra.file;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/files")
public class FileStorageController {

  private Path fileStorageLocation;

  public FileStorageController(FileStorageProperties fileStorageProperties) {
    this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
        .toAbsolutePath()
        .normalize();
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> uploadFile(@RequestBody MultipartFile file) {
    String fileName = StringUtils.cleanPath(file.getOriginalFilename());

    try {
      Path targetLocation = fileStorageLocation.resolve(fileName);

      file.transferTo(targetLocation);

      String fileDownloadUri = ServletUriComponentsBuilder
          .fromCurrentContextPath()
          .path("/api/files/download/")
          .path(fileName)
          .toUriString();

      return ResponseEntity.ok("Upload Completed! Download link" + fileDownloadUri);
    } catch (IOException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @GetMapping("/download/{fileName:.+}")
  public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request)
      throws IOException {
    Path filePath = fileStorageLocation.resolve(fileName).normalize();

    try {
      Resource resource = new UrlResource(filePath.toUri());

      String contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());

      if (contentType == null)
        contentType = "application/octet-stream";

      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType(contentType))
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename = \"" + resource.getFilename() + "\"")
          .build();
    } catch (MalformedURLException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @GetMapping("/file")
  public ResponseEntity<List<String>> listFiles() throws IOException {
    List<String> filesNames = Files.list(fileStorageLocation)
        .map(Path::getFileName)
        .map(Path::toString)
        .toList();

    return ResponseEntity.ok(filesNames);
  }
}
