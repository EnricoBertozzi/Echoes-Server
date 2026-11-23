package com.n0hana.echoes_server.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;

@ExtendWith(MockitoExtension.class)
class TermInitializerCoverageTests {

    @Mock
    private TermRepository termRepository;

    @Mock
    private TermService termService;

    @InjectMocks
    private TermInitializer initializer;

    @Test
    @DisplayName("run sincroniza todos os documentos empacotados (banco vazio grava um de cada tipo)")
    void runSynchronizesAllBundledDocuments() {
        when(termRepository.existsByType(any())).thenReturn(false);

        initializer.run(null);

        verify(termRepository, org.mockito.Mockito.times(TermInitializer.BUNDLED.size())).save(any(TermModel.class));
        for (DocumentType type : DocumentType.values()) {
            if (TermInitializer.BUNDLED.stream().anyMatch(d -> d.type() == type)) {
                verify(termRepository).existsByType(type);
            }
        }
    }

    @Test
    @DisplayName("run chama synchronize para cada documento")
    void runCallsSynchronizeForEach() {
        TermInitializer spy = spy(initializer);
        org.mockito.Mockito.doNothing().when(spy).synchronize(any());

        spy.run(null);

        TermInitializer.BUNDLED.forEach(doc -> verify(spy).synchronize(doc));
    }

    @Test
    @DisplayName("readContent falha com IllegalStateException quando o recurso não existe")
    void readContentMissingResource() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> initializer.readContent("legal-test/nao-existe.md"));

        assertTrue(ex.getMessage().contains("legal-test/nao-existe.md"));
        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    @DisplayName("readContent falha quando o documento está em branco")
    void readContentBlankResource() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> initializer.readContent("legal-test/blank.md"));

        assertTrue(ex.getMessage().contains("vazio"));
    }

    @Test
    @DisplayName("Sem versão publicada e versão empacotada ausente do histórico: publica pelo TermService")
    void publishesWhenNoPublishedVersionExists() {
        TermInitializer.BundledDocument doc = TermInitializer.BUNDLED.get(0);
        when(termRepository.existsByType(doc.type())).thenReturn(true);
        when(termRepository.findAllByTypes(List.of(doc.type()))).thenReturn(List.of());
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            doc.type(), com.n0hana.echoes_server.term.model.TermStatus.PUBLISHED))
            .thenReturn(java.util.Optional.empty());

        initializer.synchronize(doc);

        verify(termService).createTerm(any());
        verify(termRepository, never()).save(any());
    }

    @Test
    @DisplayName("readContent devolve o conteúdo sem espaços nas pontas")
    void readContentStripsContent() {
        String content = initializer.readContent(TermInitializer.BUNDLED.get(0).resourcePath());

        assertEquals(content.strip(), content);
    }
}
