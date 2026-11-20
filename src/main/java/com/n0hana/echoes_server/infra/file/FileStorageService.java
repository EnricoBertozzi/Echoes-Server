package com.n0hana.echoes_server.infra.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.infra.file.exception.StorageDeleteFileException;
import com.n0hana.echoes_server.infra.file.exception.StorageFileNotFoundException;
import com.n0hana.echoes_server.infra.file.exception.StorageSaveFileException;

/**
 * Service para gerenciar o armazenamento de arquivos do projeto.
 * 
 * @author Enrico Bertozzi
 * @since 0.1.0
 * @see {@link FileStorageProperties}
 */
@Service
public class FileStorageService {

    private Path fileStorageLocation;

    private FileValidatorService fileValidator;

    /**
     * Construtor com injeção das propriedades de armazenamento, permite localizar o
     * diretório de uploads
     * 
     * @param fileStorageProperties Objeto com as propriedades de armazenamento de
     *                              arquivos do Spring
     */
    public FileStorageService(FileStorageProperties fileStorageProperties, FileValidatorService validator) {
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath()
                .normalize();

        this.fileValidator = validator;
    }

    /**
     * Inicializa o diretório de uploads
     */
    public void init() {
        try {
            Files.createDirectories(fileStorageLocation);
        } catch (IOException ex) {
            // TODO adicionar erro de inicialização
        }
    }

    /**
     * Salva um novo arquivo dentro da pasta de uploads do servidor
     * 
     * @param file    Arquivo Multipart recebido pela API
     * @param newName Nome do arquivo a ser salvo.
     * 
     * @return {@link String} contendo o nome gerado para o arquivo
     */
    public String store(MultipartFile file, String newName) {
        fileValidator.validate(file);
        return saveToRoot(file, newName);
    }

    /**
     * Salva um áudio de cenário na raiz de uploads, validando extensão mp3 e
     * assinatura do arquivo (B3). Mantém o mesmo local usado por {@link #store}.
     *
     * @param file    Áudio recebido pela API
     * @param newName Nome base do arquivo
     *
     * @return {@link String} com o nome gerado para o arquivo
     */
    public String storeAudio(MultipartFile file, String newName) {
        fileValidator.validateAudio(file);
        return saveToRoot(file, newName);
    }

    private String saveToRoot(MultipartFile file, String newName) {
        String fileName = this.generateNewName(file.getOriginalFilename(), newName);

        try {
            Path targetLocation = fileStorageLocation.resolve(fileName);
            file.transferTo(targetLocation);

            return targetLocation.getFileName().toString();
        } catch (IOException e) {
            throw new StorageSaveFileException();
        }
    }

    /**
     * Salva um novo arquivo dentro de uma subpasta da pasta de uploads. A subpasta
     * é criada se ainda não existir.
     * 
     * @param file              Arquivo Multipart recebido pela API
     * @param newName           Nome do arquivo a ser salvo.
     * @param subDir            Subpasta dentro de uploads (ex.: id da turma)
     * @param allowedExtensions Extensões aceitas para este arquivo
     * 
     * @return {@link String} contendo o nome gerado para o arquivo
     */
    public String store(MultipartFile file, String newName, String subDir, List<String> allowedExtensions) {
        fileValidator.validate(file, allowedExtensions);

        String fileName = this.generateNewName(file.getOriginalFilename(), newName);

        try {
            Path directory = fileStorageLocation.resolve(subDir).normalize();
            Files.createDirectories(directory);

            Path targetLocation = directory.resolve(fileName);
            file.transferTo(targetLocation);

            return targetLocation.getFileName().toString();
        } catch (IOException e) {
            throw new StorageSaveFileException();
        }
    }

    /**
     * Localiza o caminho do arquivo dentro do sistema
     * 
     * @param fileName Nome do arquivo
     * 
     * @return {@link Path} caminho do arquivo
     */
    public Path load(String fileName) {
        return fileStorageLocation
                .resolve(fileName)
                .normalize();
    }

    /**
     * Localiza o caminho de um arquivo dentro de uma subpasta. Recusa caminhos que
     * saiam da pasta de uploads.
     * 
     * @param subDir   Subpasta dentro de uploads
     * @param fileName Nome do arquivo
     * 
     * @return {@link Path} caminho do arquivo
     */
    public Path load(String subDir, String fileName) {
        Path path = fileStorageLocation
                .resolve(subDir)
                .resolve(fileName)
                .normalize();

        if (!path.startsWith(fileStorageLocation))
            throw new StorageFileNotFoundException();

        return path;
    }

    /**
     * Exclui um arquivo presente no uploads do projeto
     * 
     * @param filename Nome do arquivo a ser exclúido,
     */
    public void delete(String filename) {
        try {
            Files.deleteIfExists(fileStorageLocation.resolve(filename));
        } catch (IOException ex) {
            throw new StorageDeleteFileException();
        }
    }

    /**
     * Exclui um arquivo de uma subpasta do uploads
     * 
     * @param subDir   Subpasta dentro de uploads
     * @param filename Nome do arquivo a ser excluído
     */
    public void delete(String subDir, String filename) {
        try {
            Files.deleteIfExists(this.load(subDir, filename));
        } catch (IOException ex) {
            throw new StorageDeleteFileException();
        }
    }

    /**
     * Renomeia um arquivo com um novo nome fornecido.
     * 
     * @param oldName Nome do antigo arquivo
     * @param newName Novo nome para o arquivo
     * 
     * @return {@link String} Nome do novo arquivo gerado
     */
    public String rename(String oldName, String newName) {
        Path oldFilePath = this.load(oldName);

        if (!Files.exists(oldFilePath))
            throw new StorageFileNotFoundException();

        String fileName = this.generateNewName(oldName, newName);
        try {
            Path newFilePath = fileStorageLocation.resolve(fileName);

            // sem REPLACE_EXISTING: nunca sobrescreve outro arquivo (B8)
            Files.move(oldFilePath, newFilePath);

            return fileName;
        } catch (IOException ex) {
            throw new StorageSaveFileException();
        }
    }

    /**
     * Gera o novo nome para o arquivo adicionado.
     * 
     * @param originalName Nome original do arquivo
     * @param newName      Novo nome para o arquivo
     * 
     * @return {@link String} novo nome do arquivo
     */
    private String generateNewName(String originalName, String newName) {
        String fileExtension = originalName.substring(originalName.lastIndexOf("."));

        String formatedName = newName.trim().replaceAll(" ", "_");
        String fileName = UUID.randomUUID() + "_" + formatedName + fileExtension;
        return fileName;
    }

}
