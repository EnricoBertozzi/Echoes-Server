package com.n0hana.echoes_server.classroom.material;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
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

/** Pasta isolada: nunca expor como conteúdo estático do servidor. */
@Component
public class ClassroomMaterialStorage {
    private static final Logger log = LoggerFactory.getLogger(ClassroomMaterialStorage.class);
    public static final long MAX_BYTES = 18L * 1024 * 1024;
    private final Path root;

    public ClassroomMaterialStorage(FileStorageProperties properties) {
        if (properties.getUploadDir() == null || properties.getUploadDir().isBlank())
            throw new IllegalStateException("file.upload-dir não está configurado");
        root = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize().resolve("classroom-materials");
    }

    public record Stored(String name, String originalName, String contentType, long size) {}

    public Stored store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw MaterialException.invalid("Selecione um arquivo");
        if (file.getSize() > MAX_BYTES) throw MaterialException.invalid("O limite é de 18 MB");
        String original = file.getOriginalFilename();
        MaterialFileType type = MaterialFileType.fromFilename(original);
        String storedName = UUID.randomUUID() + "." + type.extension();
        Path target = pathFor(storedName);
        try {
            Files.createDirectories(root);
            // Impede sobrescrita e limita bytes de verdade, não apenas o tamanho declarado no multipart.
            try (InputStream input = file.getInputStream(); var out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[65536];
                long size = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > MAX_BYTES) throw MaterialException.invalid("O limite é de 18 MB");
                    out.write(buffer, 0, count);
                }
            }
            long size = Files.size(target);
            if (size == 0) throw MaterialException.invalid("Arquivo vazio");
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
