package com.n0hana.echoes_server.term;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.term.dto.CreateTermRequestDTO;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publica os documentos legais empacotados em {@code resources/legal}.
 *
 * <p>
 * Banco vazio: grava a versão empacotada como PUBLISHED. Banco com versão
 * publicada menor: publica a versão empacotada pelo {@link TermService}, que
 * arquiva a anterior; os documentos obrigatórios passam a exigir novo aceite
 * no próximo acesso ({@code TermsAcceptanceFilter}). Se a administração já
 * publicou uma versão igual ou maior, ou se a versão empacotada já existe no
 * histórico (por exemplo, como rascunho), nada é alterado.
 * </p>
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class TermInitializer implements ApplicationRunner {

    /** documento legal empacotado: tipo, versão e caminho no classpath */
    record BundledDocument(DocumentType type, String version, String resourcePath) {
    }

    static final List<BundledDocument> BUNDLED = List.of(
        new BundledDocument(DocumentType.TERMS_OF_USE, "1.1.0", "legal/terms/1.1.0.md"),
        new BundledDocument(DocumentType.PRIVACY_POLICY, "1.1.0", "legal/privacy-policy/1.1.0.md"),
        new BundledDocument(DocumentType.COOKIES_POLICY, "1.1.0", "legal/cookies-policy/1.1.0.md"),
        new BundledDocument(DocumentType.PROCESSING_OPERATIONS, "1.1.0", "legal/operations/1.1.0.md")
    );

    private final TermRepository termRepository;
    private final TermService termService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        BUNDLED.forEach(this::synchronize);
    }

    void synchronize(BundledDocument document) {
        DocumentType type = document.type();

        if (!termRepository.existsByType(type)) {
            seed(document);
            return;
        }

        if (versionExists(type, document.version())) {
            log.debug("Documento {} versão {} já registrado. Nada a fazer.", type, document.version());
            return;
        }

        Optional<TermModel> published = termRepository
            .findFirstByTypeAndStatusOrderByTimestampDesc(type, TermStatus.PUBLISHED);

        if (published.isPresent()
                && !TermService.isVersionGreater(document.version(), published.get().getVersion())) {
            log.info("Documento {}: versão publicada {} é igual ou maior que a empacotada {}. Mantida.",
                type, published.get().getVersion(), document.version());
            return;
        }

        termService.createTerm(new CreateTermRequestDTO(
            document.version(), readContent(document.resourcePath()), type, TermStatus.PUBLISHED));

        log.info("Documento {} atualizado para a versão {} (anterior: {}). Novo aceite será exigido.",
            type, document.version(), published.map(TermModel::getVersion).orElse("nenhuma publicada"));
    }

    private boolean versionExists(DocumentType type, String version) {
        return termRepository.findAllByTypes(List.of(type)).stream()
            .anyMatch(term -> version.equals(term.getVersion()));
    }

    private void seed(BundledDocument document) {
        TermModel term = new TermModel();
        term.setType(document.type());
        term.setVersion(document.version());
        term.setContent(readContent(document.resourcePath()));
        term.setStatus(TermStatus.PUBLISHED);

        termRepository.save(term);

        log.info("Documento inicial cadastrado: tipo={}, versão={}", document.type(), document.version());
    }

    String readContent(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);

        try (InputStream input = resource.getInputStream()) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();

            if (content.isBlank()) {
                throw new IllegalStateException("Documento legal vazio: " + resourcePath);
            }

            return content;
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Não foi possível carregar o documento legal: " + resourcePath, exception);
        }
    }
}
