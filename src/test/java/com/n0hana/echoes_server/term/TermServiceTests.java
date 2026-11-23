package com.n0hana.echoes_server.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.term.dto.CreateTermRequestDTO;
import com.n0hana.echoes_server.term.exception.TermStatusException;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class TermServiceTests {

    @Mock
    private TermRepository termRepository;

    @Mock
    private UserTermAcceptanceRepository userTermAcceptanceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TermService termService;

    private TermModel term(long id, TermStatus status) {
        return new TermModel(
            id,
            "1.0.0",
            "conteúdo original",
            Instant.now(),
            DocumentType.TERMS_OF_USE,
            status
        );
    }

    @Test
    @DisplayName("Permite editar o conteúdo de um termo DRAFT")
    void allowsContentUpdateForDraft() {
        TermModel draft = term(1L, TermStatus.DRAFT);
        when(termRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(termRepository.save(any(TermModel.class))).thenAnswer(inv -> inv.getArgument(0));

        TermModel updated = termService.updateTermContent(1L, "conteúdo revisado");

        assertEquals("conteúdo revisado", updated.getContent());
        verify(termRepository).save(draft);
    }

    @Test
    @DisplayName("Bloqueia edição de conteúdo em IN_REVIEW")
    void blocksContentUpdateInReview() {
        TermModel inReview = term(2L, TermStatus.IN_REVIEW);
        when(termRepository.findById(2L)).thenReturn(Optional.of(inReview));

        assertThrows(TermStatusException.class,
            () -> termService.updateTermContent(2L, "tentativa"));

        verify(termRepository, never()).save(any(TermModel.class));
    }

    @Test
    @DisplayName("Bloqueia edição de conteúdo em APPROVED")
    void blocksContentUpdateApproved() {
        TermModel approved = term(3L, TermStatus.APPROVED);
        when(termRepository.findById(3L)).thenReturn(Optional.of(approved));

        assertThrows(TermStatusException.class,
            () -> termService.updateTermContent(3L, "tentativa"));

        verify(termRepository, never()).save(any(TermModel.class));
    }

    @Test
    @DisplayName("Bloqueia edição de conteúdo em PUBLISHED")
    void blocksContentUpdatePublished() {
        TermModel published = term(4L, TermStatus.PUBLISHED);
        when(termRepository.findById(4L)).thenReturn(Optional.of(published));

        assertThrows(TermStatusException.class,
            () -> termService.updateTermContent(4L, "tentativa"));

        verify(termRepository, never()).save(any(TermModel.class));
    }

    @Test
    @DisplayName("Publicar termo arquiva automaticamente o publicado anterior do mesmo tipo")
    void publishingArchivesPreviousPublishedTerm() {
        TermModel previous = term(10L, TermStatus.PUBLISHED);
        TermModel approved = term(11L, TermStatus.APPROVED);
        approved.setVersion("1.1.0");

        when(termRepository.findById(11L)).thenReturn(Optional.of(approved));
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED))
            .thenReturn(Optional.of(previous));
        when(termRepository.save(any(TermModel.class))).thenAnswer(inv -> inv.getArgument(0));

        TermModel published = termService.updateTermStatus(11L, TermStatus.PUBLISHED);

        assertEquals(TermStatus.ARCHIVED, previous.getStatus());
        assertEquals(TermStatus.PUBLISHED, published.getStatus());
    }

    @Test
    @DisplayName("Não permite despublicar o termo atualmente publicado")
    void cannotUnpublishCurrentTerm() {
        TermModel published = term(20L, TermStatus.PUBLISHED);
        when(termRepository.findById(20L)).thenReturn(Optional.of(published));

        assertThrows(TermStatusException.class,
            () -> termService.updateTermStatus(20L, TermStatus.ARCHIVED));

        verify(termRepository, never()).save(any(TermModel.class));
    }

    @Test
    @DisplayName("Criar termo sem status cria como DRAFT e não altera o publicado atual")
    void createsDraftWithoutPublishing() {
        when(termRepository.save(any(TermModel.class))).thenAnswer(inv -> inv.getArgument(0));

        TermModel created = termService.createTerm(new CreateTermRequestDTO(
            "2.0.0",
            "novo conteúdo",
            DocumentType.PRIVACY_POLICY,
            null
        ));

        assertEquals(TermStatus.DRAFT, created.getStatus());
        verify(termRepository, never())
            .findFirstByTypeAndStatusOrderByTimestampDesc(any(), any());
    }

    @Test
    @DisplayName("Considera aceito quando existe aceite para o termo PUBLISHED atual")
    void acceptedWhenAcceptanceMatchesCurrentPublishedTerm() {
        UUID userId = UUID.randomUUID();
        TermModel published = term(30L, TermStatus.PUBLISHED);

        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED))
            .thenReturn(Optional.of(published));
        when(userTermAcceptanceRepository.findByUserIdAndTermsId(userId, 30L))
            .thenReturn(Optional.of(new UserTermAcceptance()));

        assertTrue(termService.hasAcceptedLatestTerms(userId, DocumentType.TERMS_OF_USE));
    }

    @Test
    @DisplayName("Volta a exigir aceite quando o termo PUBLISHED muda")
    void notAcceptedWhenPublishedTermChanged() {
        UUID userId = UUID.randomUUID();
        TermModel newPublished = term(31L, TermStatus.PUBLISHED);

        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED))
            .thenReturn(Optional.of(newPublished));
        when(userTermAcceptanceRepository.findByUserIdAndTermsId(userId, 31L))
            .thenReturn(Optional.empty());

        assertFalse(termService.hasAcceptedLatestTerms(userId, DocumentType.TERMS_OF_USE));
    }

    @Test
    @DisplayName("Lista apenas os termos obrigatórios pendentes de aceite, na ordem canônica")
    void listsPendingRequiredTermsInCanonicalOrder() {
        UUID userId = UUID.randomUUID();
        when(termRepository.findTypesWithoutAcceptance(
            eq(userId), anyCollection(), eq(TermStatus.PUBLISHED)))
            .thenReturn(List.of(DocumentType.COOKIES_POLICY, DocumentType.TERMS_OF_USE));

        List<DocumentType> pending = termService.getPendingRequiredTerms(userId);

        assertEquals(List.of(DocumentType.TERMS_OF_USE, DocumentType.COOKIES_POLICY), pending);
    }

    @Test
    @DisplayName("Aceite do termo ativo é idempotente")
    void acceptingSameActiveTermTwiceDoesNotDuplicate() {
        UUID userId = UUID.randomUUID();
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(userId);

        TermModel published = term(40L, TermStatus.PUBLISHED);
        UserTermAcceptance existing = new UserTermAcceptance(user, published);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED))
            .thenReturn(Optional.of(published));
        when(userTermAcceptanceRepository.findByUserIdAndTermsId(userId, 40L))
            .thenReturn(Optional.of(existing));

        UserTermAcceptance result = termService.acceptTerms(userId, DocumentType.TERMS_OF_USE);

        assertEquals(existing, result);
        verify(userTermAcceptanceRepository, never()).save(any(UserTermAcceptance.class));
    }

    @Test
    @DisplayName("Lista o histórico de termos filtrando por tipo e status")
    void listsTermsFilteredByTypeAndStatus() {
        TermModel draft = term(50L, TermStatus.DRAFT);
        TermModel published = new TermModel(
            51L,
            "2.0.0",
            "conteúdo novo",
            Instant.now(),
            DocumentType.PRIVACY_POLICY,
            TermStatus.PUBLISHED
        );

        when(termRepository.findAllByTypes(anyCollection()))
            .thenReturn(List.of(published, draft));

        List<TermModel> result = termService.getAllTerms(null, TermStatus.DRAFT);

        assertEquals(List.of(draft), result);
    }
}
