package com.n0hana.echoes_server.term;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class TermInitializer implements ApplicationRunner {

    private final String INITIAL_VERSION = "1.0.0";

    private final TermRepository termRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initialize(
            DocumentType.TERMS_OF_USE,
            "legal/terms/1.0.0.md"
        );

        initialize(
            DocumentType.PRIVACY_POLICY,
            "legal/privacy-policy/1.0.0.md"
        );

        initialize(
            DocumentType.COOKIES_POLICY,
            "legal/cookies-policy/1.0.0.md"
        );

        initialize(
            DocumentType.PROCESSING_OPERATIONS,
            "legal/operations/1.0.0.md"
        );
    }

    private void initialize(DocumentType type, String resourcePath) {
        if (termRepository.existsByType(type)) {
            log.debug(
                "Documento {} já cadastrado. Inicialização ignorada.",
                type
            );
            return;
        }

        String content = readContent(resourcePath);

        TermModel term = new TermModel();
        term.setType(type);
        term.setVersion(INITIAL_VERSION);
        term.setContent(content);
        term.setStatus(TermStatus.PUBLISHED);

        termRepository.save(term);

        log.info(
            "Documento inicial cadastrado: tipo={}, versão={}",
            type,
            INITIAL_VERSION
        );
    }

    private String readContent(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);

        try (InputStream input = resource.getInputStream()) {
            String content = new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            ).strip();

            if (content.isBlank()) {
                throw new IllegalStateException(
                    "Documento inicial vazio: " + resourcePath
                );
            }

            return content;
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Não foi possível carregar o documento inicial: "
                    + resourcePath,
                exception
            );
        }
    }
}
