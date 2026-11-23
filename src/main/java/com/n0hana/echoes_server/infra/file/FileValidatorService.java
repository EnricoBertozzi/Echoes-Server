package com.n0hana.echoes_server.infra.file;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;
import java.util.Locale;

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

  /**
   * Realiza validações de segurança sobre um arquivo
   * 
   * @param file Arquivo para ser avaliado
   */
  public void validate(MultipartFile file) {
    validate(file, ALLOWED_EXTENSIONS);
  }

  /**
   * Extensões de áudio de cenário aceitas em upload. Vem de
   * {@code SCENARIO_AUDIO_EXTENSIONS}; o padrão do código vale só sem Spring (testes).
   */
  @Value("#{'${api.scenario.audio-extensions:mp3,wav,ogg,m4a}'.split(',')}")
  private List<String> audioExtensions = List.of("mp3", "wav", "ogg", "m4a");

  /**
   * Valida um áudio de cenário: extensão permitida pela configuração, formato
   * reconhecido e conteúdo compatível com a extensão.
   *
   * @param file Arquivo enviado
   */
  public void validateAudio(MultipartFile file) {
    validate(file, audioExtensions);

    String extension = getExtension(file.getOriginalFilename());
    AudioFileType type = AudioFileType.fromExtension(extension)
            .orElseThrow(() -> new FileValidateException("Formato de áudio não permitido"));

    if (!matchesSignature(file, type))
      throw new FileValidateException("O conteúdo do áudio não corresponde ao formato ." + type.extension());
  }

  private boolean matchesSignature(MultipartFile file, AudioFileType type) {
    byte[] head;
    try (InputStream in = file.getInputStream()) {
      head = in.readNBytes(16);
    } catch (IOException e) {
      throw new FileValidateException("Não foi possível ler o arquivo de áudio");
    }
    return type.matches(head);
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
      throw new FileValidateException("Selecione um arquivo que não esteja vazio");

    // Verifica o tamanho do arquivo
    if (file.getSize() > UploadFileCopy.MAX_BYTES)
      throw new FileValidateException(UploadFileCopy.SIZE_LIMIT_MESSAGE);

    // Verifica o nome do arquivo
    String originalName = file.getOriginalFilename();
    if (!StringUtils.hasText(originalName) || originalName.contains("..")
        || originalName.contains("/") || originalName.contains("\\"))
      throw new FileValidateException("Nome de arquivo inválido");

    // Verifica a extensão do arquivo
    String extension = this.getExtension(originalName).toLowerCase(Locale.ROOT);
    if (!allowedExtensions.contains(extension))
      throw new FileValidateException("Extensão de arquivo não permitida");

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
