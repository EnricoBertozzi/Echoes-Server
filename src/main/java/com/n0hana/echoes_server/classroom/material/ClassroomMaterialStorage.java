package com.n0hana.echoes_server.classroom.material;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.n0hana.echoes_server.infra.file.FileStorageProperties;
import com.n0hana.echoes_server.infra.file.UploadFileCopy;
import com.n0hana.echoes_server.infra.file.exception.FileValidateException;

/** Pasta isolada: nunca expor como conteúdo estático do servidor. */
@Component
public class ClassroomMaterialStorage {
    private static final Logger log = LoggerFactory.getLogger(ClassroomMaterialStorage.class);
    private final Path root;
    private final Set<String> allowedExtensions;

    public ClassroomMaterialStorage(
            FileStorageProperties properties,
            @Value("#{'${api.material.extensions-allowed:pdf,docx,jpg,jpeg,png}'.split(',')}") List<String> allowedExtensions) {
        if (properties.getUploadDir() == null || properties.getUploadDir().isBlank())
            throw new IllegalStateException("file.upload-dir não está configurado");
        root = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize().resolve("classroom-materials");
        this.allowedExtensions = allowedExtensions.stream()
                .map(e -> e.trim().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public record Stored(String name, String originalName, String contentType, long size) {}

    public Stored store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw MaterialException.invalid("Selecione um arquivo");
        if (file.getSize() > UploadFileCopy.MAX_BYTES) throw MaterialException.invalid(UploadFileCopy.SIZE_LIMIT_MESSAGE);
        String original = file.getOriginalFilename();
        MaterialFileType type = MaterialFileType.fromFilename(original);
        // só os tipos configurados em MATERIAL_EXTENSIONS_ALLOWED são aceitos em upload
        if (!allowedExtensions.contains(type.extension()))
            throw MaterialException.invalid("Tipo de arquivo não permitido para material: ." + type.extension());
        String storedName = UUID.randomUUID() + "." + type.extension();
        Path target = pathFor(storedName);
        try {
            Files.createDirectories(root);
            // Impede sobrescrita e limita bytes de verdade, não apenas o tamanho declarado no multipart.
            long size;
            try (InputStream input = file.getInputStream(); var out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                size = UploadFileCopy.copy(input, out);
            } catch (FileValidateException ex) {
                throw MaterialException.invalid(ex.getMessage());
            }
            type.validateContent(target);
            removeIfTransactionRollsBack(storedName);
            return new Stored(storedName, original, type.contentType(), size);
        } catch (IOException ex) {
            deleteQuietly(storedName);
            throw new MaterialException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Falha ao armazenar arquivo");
        } catch (RuntimeException ex) {
            deleteQuietly(storedName);
            throw ex;
        }
    }

    public Resource load(String storedName) {
        Path file = pathFor(storedName);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(file))
            throw MaterialException.notFound();
        return new FileSystemResource(file);
    }

    public void deleteAfterCommit(List<String> storedNames) {
        if (storedNames.isEmpty()) return;
        Runnable cleanup = () -> storedNames.forEach(this::deleteQuietly);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        } else {
            cleanup.run();
        }
    }

    private void removeIfTransactionRollsBack(String storedName) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) deleteQuietly(storedName);
                }
            });
        }
    }

    private Path pathFor(String storedName) {
        if (storedName == null || !storedName.matches("[0-9a-f-]{36}\\.[a-z0-9]{2,5}"))
            throw MaterialException.notFound();
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) throw MaterialException.notFound();
        return target;
    }

    private void deleteQuietly(String storedName) {
        try {
            Files.deleteIfExists(pathFor(storedName));
        } catch (IOException ex) {
            log.error("Não foi possível remover o arquivo de material {}", storedName, ex);
        }
    }
}
