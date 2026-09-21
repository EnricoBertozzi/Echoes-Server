package com.n0hana.echoes_server.infra.file;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.infra.file.exception.FileValidateException;

/**
 * Service para validação de entradas de arquivos
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Service 
public class FileValidatorService {

  @Value("#{'${api.security.files-extensions-allowed}'.split(',')}")
  private List<String> ALLOWED_EXTENSIONS;

  private static final Long MAX_FILE_SIZE = 50L * 1024L * 1024L;

  /**
   * Realiza validações de segurança sobre um arquivo
   * 
   * @param file Arquivo para ser avaliado
   */
  public void validate(MultipartFile file) {

    // Verifica se o arquivo existe
    if (file == null || file.isEmpty())
      throw new FileValidateException();

    // Verifica o tamanho do arquivo
    if (file.getSize() > MAX_FILE_SIZE)
      throw new FileValidateException();

    // Verifica o nome do arquivo
    String originalName = StringUtils.cleanPath(file.getOriginalFilename());
    if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\"))
      throw new FileValidateException();

    // Verifica a extensão do arquivo
    String extension = this.getExtension(originalName).toLowerCase();
    if (!ALLOWED_EXTENSIONS.contains(extension))
      throw new FileValidateException();

  }

  /**
   * Retorna a extensão do arquivo
   * 
   * @param filename Nome do arquivo.
   * 
   * @return {@link String} com a extensão do arquivo
   */
  private String getExtension(String filename) {
    int index = filename.lastIndexOf(".");
    if (index == -1)
      return "";
    return filename.substring(index + 1);
  }
}
