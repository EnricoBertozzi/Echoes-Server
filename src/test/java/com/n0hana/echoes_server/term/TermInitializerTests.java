package com.n0hana.echoes_server.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.term.TermInitializer.BundledDocument;
import com.n0hana.echoes_server.term.dto.CreateTermRequestDTO;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;

@ExtendWith(MockitoExtension.class)
class TermInitializerTests {

    @Mock
    private TermRepository termRepository;

    @Mock
    private TermService termService;

    @InjectMocks
    private TermInitializer initializer;

    private static final BundledDocument PRIVACY = TermInitializer.BUNDLED.stream()
        .filter(d -> d.type() == DocumentType.PRIVACY_POLICY)
        .findFirst()
        .orElseThrow();

    private static TermModel term(String version, TermStatus status) {
        return new TermModel(1L, version, "conteúdo", Instant.now(), DocumentType.PRIVACY_POLICY, status);
    }

    @Test
    @DisplayName("Todos os documentos empacotados existem e não estão vazios")
    void bundledDocumentsAreReadable() {
        TermInitializer.BUNDLED.forEach(document -> {
            String content = initializer.readContent(document.resourcePath());
            assertFalse(content.isBlank(), document.resourcePath());
            assertTrue(content.contains(document.version()),
                document.resourcePath() + " deve citar a própria versão");
        });
    }

    @Test
    @DisplayName("Banco vazio: grava a versão empacotada como publicada")
    void seedsWhenEmpty() {
        when(termRepository.existsByType(DocumentType.PRIVACY_POLICY)).thenReturn(false);

        initializer.synchronize(PRIVACY);

        ArgumentCaptor<TermModel> saved = ArgumentCaptor.forClass(TermModel.class);
        verify(termRepository).save(saved.capture());
        assertEquals("1.1.0", saved.getValue().getVersion());
        assertEquals(TermStatus.PUBLISHED, saved.getValue().getStatus());
        verify(termService, never()).createTerm(any());
    }

    @Test
    @DisplayName("Versão publicada menor: publica a empacotada pelo TermService")
    void upgradesOlderPublishedVersion() {
        when(termRepository.existsByType(DocumentType.PRIVACY_POLICY)).thenReturn(true);
        when(termRepository.findAllByTypes(List.of(DocumentType.PRIVACY_POLICY)))
            .thenReturn(List.of(term("1.0.0", TermStatus.PUBLISHED)));
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.PRIVACY_POLICY, TermStatus.PUBLISHED))
            .thenReturn(Optional.of(term("1.0.0", TermStatus.PUBLISHED)));

        initializer.synchronize(PRIVACY);

        ArgumentCaptor<CreateTermRequestDTO> dto = ArgumentCaptor.forClass(CreateTermRequestDTO.class);
        verify(termService).createTerm(dto.capture());
        assertEquals("1.1.0", dto.getValue().version());
        assertEquals(TermStatus.PUBLISHED, dto.getValue().status());
        assertEquals(DocumentType.PRIVACY_POLICY, dto.getValue().type());
    }

    @Test
    @DisplayName("Versão publicada maior (feita pela administração): mantém")
    void keepsNewerPublishedVersion() {
        when(termRepository.existsByType(DocumentType.PRIVACY_POLICY)).thenReturn(true);
        when(termRepository.findAllByTypes(List.of(DocumentType.PRIVACY_POLICY)))
            .thenReturn(List.of(term("2.0.0", TermStatus.PUBLISHED)));
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.PRIVACY_POLICY, TermStatus.PUBLISHED))
            .thenReturn(Optional.of(term("2.0.0", TermStatus.PUBLISHED)));

        initializer.synchronize(PRIVACY);

        verify(termService, never()).createTerm(any());
        verify(termRepository, never()).save(any());
    }

    @Test
    @DisplayName("Versão empacotada já no histórico (ex.: rascunho): não duplica")
    void doesNotDuplicateExistingVersion() {
        when(termRepository.existsByType(DocumentType.PRIVACY_POLICY)).thenReturn(true);
        when(termRepository.findAllByTypes(List.of(DocumentType.PRIVACY_POLICY)))
            .thenReturn(List.of(term("1.1.0", TermStatus.DRAFT), term("1.0.0", TermStatus.PUBLISHED)));

        initializer.synchronize(PRIVACY);

        verify(termService, never()).createTerm(any());
        verify(termRepository, never()).save(any());
    }

    @Test
    @DisplayName("Compara versões semânticas numericamente")
    void comparesVersionsNumerically() {
        assertTrue(TermService.isVersionGreater("1.1.0", "1.0.0"));
        assertTrue(TermService.isVersionGreater("1.10.0", "1.9.0"));
        assertFalse(TermService.isVersionGreater("1.0.0", "1.0.0"));
        assertFalse(TermService.isVersionGreater("1.0.9", "1.1.0"));
    }
}
