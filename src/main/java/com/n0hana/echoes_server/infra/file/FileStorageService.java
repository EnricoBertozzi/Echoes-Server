package com.n0hana.echoes_server.infra.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

            Files.move(oldFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);

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
