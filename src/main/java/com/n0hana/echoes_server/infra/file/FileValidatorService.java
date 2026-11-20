package com.n0hana.echoes_server.infra.file;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.io.InputStream;

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
    validate(file, ALLOWED_EXTENSIONS);
  }

  /** áudio de cenário: só mp3, independente de EXTENSIONS_ALLOWED (B3) */
  private static final List<String> AUDIO_EXTENSIONS = List.of("mp3");

  /**
   * Valida um áudio de cenário: extensão mp3 e assinatura MP3 (ID3 ou frame sync)
   *
   * @param file Arquivo enviado
   */
  public void validateAudio(MultipartFile file) {
    validate(file, AUDIO_EXTENSIONS);
    validateMp3Signature(file);
  }

  private void validateMp3Signature(MultipartFile file) {
    byte[] head;
    try (InputStream in = file.getInputStream()) {
      head = in.readNBytes(3);
    } catch (IOException e) {
      throw new FileValidateException();
    }

    boolean id3 = head.length == 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3';
    boolean frameSync = head.length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0;

    if (!id3 && !frameSync)
      throw new FileValidateException();
  }

  /**
   * Realiza validações de segurança sobre um arquivo usando uma lista própria de
   * extensões permitidas (ex.: material de aula)
   * 
   * @param file              Arquivo para ser avaliado
   * @param allowedExtensions Extensões aceitas, sem ponto
   */
  public void validate(MultipartFile file, List<String> allowedExtensions) {

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
    if (!allowedExtensions.contains(extension))
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
