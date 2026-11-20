package com.n0hana.echoes_server.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.term.dto.CreateTermRequestDTO;
import com.n0hana.echoes_server.term.exception.TermNotFoundException;
import com.n0hana.echoes_server.term.exception.TermStatusException;
import com.n0hana.echoes_server.term.exception.TermVersionException;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.RequiredTermsNotAcceptedException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class TermServiceCoverageTests {

    @Mock
    private TermRepository termRepository;

    @Mock
    private UserTermAcceptanceRepository acceptanceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TermService service;

    private static TermModel term(long id, String version, DocumentType type, TermStatus status) {
        return new TermModel(id, version, "conteúdo " + version, Instant.now(), type, status);
    }

    private static User user() {
        User user = new Student();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static final List<DocumentType> REQUIRED =
        List.of(DocumentType.TERMS_OF_USE, DocumentType.PRIVACY_POLICY, DocumentType.COOKIES_POLICY);

    // ---------------------------------------------------------------- consultas

    @Test
    @DisplayName("getActiveTerms devolve o termo publicado e falha quando não há")
    void getActiveTerms() {
        TermModel published = term(1, "1.0.0", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED);
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)).thenReturn(Optional.of(published));
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.COOKIES_POLICY, TermStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertSame(published, service.getActiveTerms(DocumentType.TERMS_OF_USE));
        TermNotFoundException ex = assertThrows(TermNotFoundException.class,
            () -> service.getActiveTerms(DocumentType.COOKIES_POLICY));
        assertTrue(ex.getMessage().contains("COOKIES_POLICY"));
    }

    @Test
    @DisplayName("getLatestTerms devolve o mais recente (qualquer status) e falha quando não há")
    void getLatestTerms() {
        TermModel draft = term(2, "2.0.0", DocumentType.PRIVACY_POLICY, TermStatus.DRAFT);
        when(termRepository.findTopByTypeOrderByTimestampDesc(DocumentType.PRIVACY_POLICY))
            .thenReturn(Optional.of(draft));
        when(termRepository.findTopByTypeOrderByTimestampDesc(DocumentType.PROCESSING_OPERATIONS))
            .thenReturn(Optional.empty());

        assertSame(draft, service.getLatestTerms(DocumentType.PRIVACY_POLICY));
        assertThrows(TermNotFoundException.class,
            () -> service.getLatestTerms(DocumentType.PROCESSING_OPERATIONS));
    }

    @Test
    @DisplayName("hasAcceptedLatestTerms é true quando não há termo publicado")
    void hasAcceptedWithoutPublished() {
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertTrue(service.hasAcceptedLatestTerms(UUID.randomUUID(), DocumentType.TERMS_OF_USE));
        verify(acceptanceRepository, never()).findByUserIdAndTermsId(any(), any());
    }

    @Test
    @DisplayName("hasAcceptedLatestTerms é false quando o usuário não aceitou o termo publicado")
    void hasAcceptedFalseWhenNoAcceptance() {
        UUID userId = UUID.randomUUID();
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED))
            .thenReturn(Optional.of(term(7, "1.0.0", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)));
        when(acceptanceRepository.findByUserIdAndTermsId(userId, 7L)).thenReturn(Optional.empty());

        assertFalse(service.hasAcceptedLatestTerms(userId, DocumentType.TERMS_OF_USE));
    }

    @Test
    @DisplayName("getUserAcceptances delega ao repositório")
    void getUserAcceptances() {
        UUID userId = UUID.randomUUID();
        List<UserTermAcceptance> list = List.of(new UserTermAcceptance());
        when(acceptanceRepository.findAllByUserId(userId)).thenReturn(list);

        assertSame(list, service.getUserAcceptances(userId));
    }

    // ---------------------------------------------------------------- aceites

    @Test
    @DisplayName("validateRequiredAccepted com null exige todos os termos obrigatórios")
    void validateNull() {
        RequiredTermsNotAcceptedException ex =
            assertThrows(RequiredTermsNotAcceptedException.class, () -> service.validateRequiredAccepted(null));

        assertEquals(REQUIRED, ex.getMissing());
    }

    @Test
    @DisplayName("validateRequiredAccepted informa somente os termos que faltam")
    void validateMissing() {
        RequiredTermsNotAcceptedException ex = assertThrows(RequiredTermsNotAcceptedException.class,
            () -> service.validateRequiredAccepted(List.of(DocumentType.TERMS_OF_USE, DocumentType.PROCESSING_OPERATIONS)));

        assertEquals(List.of(DocumentType.PRIVACY_POLICY, DocumentType.COOKIES_POLICY), ex.getMissing());
    }

    @Test
    @DisplayName("validateRequiredAccepted passa quando todos os obrigatórios estão presentes")
    void validateOk() {
        service.validateRequiredAccepted(REQUIRED);
        service.validateRequiredAccepted(List.of(DocumentType.COOKIES_POLICY, DocumentType.PRIVACY_POLICY,
            DocumentType.TERMS_OF_USE, DocumentType.PROCESSING_OPERATIONS));
    }

    @Test
    @DisplayName("registerInitialAcceptances grava um aceite por tipo, ignorando duplicados, sem origem fora de requisição")
    void registerInitialAcceptances() {
        User user = user();
        for (int i = 0; i < REQUIRED.size(); i++) {
            DocumentType type = REQUIRED.get(i);
            TermModel published = term(10 + i, "1.0.0", type, TermStatus.PUBLISHED);
            when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(type, TermStatus.PUBLISHED))
                .thenReturn(Optional.of(published));
            when(acceptanceRepository.findByUserIdAndTermsId(user.getId(), published.getId()))
                .thenReturn(Optional.empty());
        }
        when(acceptanceRepository.save(any(UserTermAcceptance.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registerInitialAcceptances(user, List.of(DocumentType.TERMS_OF_USE, DocumentType.PRIVACY_POLICY,
            DocumentType.COOKIES_POLICY, DocumentType.TERMS_OF_USE));

        ArgumentCaptor<UserTermAcceptance> saved = ArgumentCaptor.forClass(UserTermAcceptance.class);
        verify(acceptanceRepository, org.mockito.Mockito.times(3)).save(saved.capture());
        assertTrue(saved.getAllValues().stream().allMatch(a -> a.getUser() == user));
        assertTrue(saved.getAllValues().stream().allMatch(a -> a.getIpAddress() == null && a.getUserAgent() == null));
    }

    @Test
    @DisplayName("registerInitialAcceptances revalida e não grava nada quando falta obrigatório")
    void registerInitialAcceptancesRevalidates() {
        assertThrows(RequiredTermsNotAcceptedException.class,
            () -> service.registerInitialAcceptances(user(), List.of(DocumentType.TERMS_OF_USE)));

        verify(acceptanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("acceptTerms lança UserNotFoundException para usuário inexistente")
    void acceptTermsUnknownUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.acceptTerms(id, DocumentType.TERMS_OF_USE));
    }

    @Test
    @DisplayName("acceptTerms sem termo publicado lança TermNotFoundException")
    void acceptTermsWithoutPublished() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThrows(TermNotFoundException.class, () -> service.acceptTerms(user.getId(), DocumentType.TERMS_OF_USE));
        verify(acceptanceRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- admin

    @Test
    @DisplayName("getAllTerms com tipo consulta só aquele tipo e com status nulo não filtra")
    void getAllTermsByType() {
        TermModel a = term(1, "1.0.0", DocumentType.COOKIES_POLICY, TermStatus.PUBLISHED);
        TermModel b = term(2, "0.9.0", DocumentType.COOKIES_POLICY, TermStatus.ARCHIVED);
        when(termRepository.findAllByTypes(List.of(DocumentType.COOKIES_POLICY))).thenReturn(List.of(a, b));

        assertEquals(List.of(a, b), service.getAllTerms(DocumentType.COOKIES_POLICY, null));
        assertEquals(List.of(b), service.getAllTerms(DocumentType.COOKIES_POLICY, TermStatus.ARCHIVED));
    }

    @Test
    @DisplayName("getAllTerms sem tipo consulta todos os tipos suportados")
    void getAllTermsAllTypes() {
        when(termRepository.findAllByTypes(any())).thenReturn(List.of());

        assertTrue(service.getAllTerms(null, null).isEmpty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<DocumentType>> types = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(termRepository).findAllByTypes(types.capture());
        assertEquals(4, types.getValue().size());
        assertTrue(types.getValue().contains(DocumentType.PROCESSING_OPERATIONS));
    }

    @Test
    @DisplayName("getTermById falha com TermNotFoundException quando não existe")
    void getTermByIdMissing() {
        when(termRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TermNotFoundException.class, () -> service.getTermById(99L));
    }

    @Test
    @DisplayName("createTerm publicado sem termo ativo apenas salva como PUBLISHED")
    void createPublishedWithoutActive() {
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)).thenReturn(Optional.empty());
        when(termRepository.save(any(TermModel.class))).thenAnswer(inv -> inv.getArgument(0));

        TermModel created = service.createTerm(
            new CreateTermRequestDTO("1.0.0", "texto", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED));

        assertEquals(TermStatus.PUBLISHED, created.getStatus());
        assertEquals("texto", created.getContent());
        verify(termRepository).save(created);
    }

    @Test
    @DisplayName("createTerm publicado com versão maior arquiva o termo ativo anterior")
    void createPublishedArchivesPrevious() {
        TermModel active = term(5, "1.0.0", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED);
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)).thenReturn(Optional.of(active));
        when(termRepository.save(any(TermModel.class))).thenAnswer(inv -> inv.getArgument(0));

        TermModel created = service.createTerm(
            new CreateTermRequestDTO("1.0.1", "novo", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED));

        assertEquals(TermStatus.ARCHIVED, active.getStatus());
        assertEquals(TermStatus.PUBLISHED, created.getStatus());
        verify(termRepository).save(active);
    }

    @Test
    @DisplayName("createTerm publicado com versão igual ou menor é recusado e nada é salvo")
    void createPublishedRejectsLowerVersion() {
        TermModel active = term(5, "2.0.0", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED);
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)).thenReturn(Optional.of(active));

        TermVersionException ex = assertThrows(TermVersionException.class, () -> service.createTerm(
            new CreateTermRequestDTO("2.0.0", "x", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)));

        assertTrue(ex.getMessage().contains("2.0.0"));
        assertEquals(TermStatus.PUBLISHED, active.getStatus());
        verify(termRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTerm com status explícito não publicado preserva o status")
    void createKeepsExplicitStatus() {
        when(termRepository.save(any(TermModel.class))).thenAnswer(inv -> inv.getArgument(0));

        TermModel created = service.createTerm(
            new CreateTermRequestDTO("1.0.0", "x", DocumentType.PRIVACY_POLICY, TermStatus.IN_REVIEW));

        assertEquals(TermStatus.IN_REVIEW, created.getStatus());
        verify(termRepository, never()).findFirstByTypeAndStatusOrderByTimestampDesc(any(), any());
    }

    @Test
    @DisplayName("createTerm rejeita versões fora do formato major.minor.patch")
    void createRejectsInvalidVersion() {
        for (String version : List.of("1.0", "1.0.0.0", "a.b.c", "1.x.0", "")) {
            assertThrows(TermVersionException.class, () -> service.createTerm(
                new CreateTermRequestDTO(version, "x", DocumentType.TERMS_OF_USE, TermStatus.DRAFT)), version);
        }
        verify(termRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTermContent rejeita conteúdo nulo ou em branco")
    void updateContentRequiresContent() {
        assertThrows(TermStatusException.class, () -> service.updateTermContent(1L, null));
        assertThrows(TermStatusException.class, () -> service.updateTermContent(1L, "   "));

        verify(termRepository, never()).findById(any());
    }

    @Test
    @DisplayName("updateTermContent de termo inexistente lança TermNotFoundException")
    void updateContentMissingTerm() {
        when(termRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(TermNotFoundException.class, () -> service.updateTermContent(3L, "novo"));
    }

    @Test
    @DisplayName("updateTermStatus rejeita status nulo")
    void updateStatusNull() {
        assertThrows(TermStatusException.class, () -> service.updateTermStatus(1L, null));
        verify(termRepository, never()).findById(any());
    }

    @Test
    @DisplayName("updateTermStatus com o mesmo status devolve o termo sem salvar")
    void updateStatusSame() {
        TermModel draft = term(1, "1.0.0", DocumentType.TERMS_OF_USE, TermStatus.DRAFT);
        when(termRepository.findById(1L)).thenReturn(Optional.of(draft));

        assertSame(draft, service.updateTermStatus(1L, TermStatus.DRAFT));
        verify(termRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTermStatus para PUBLISHED sem outro ativo apenas publica")
    void publishWithoutOtherActive() {
        TermModel draft = term(1, "1.0.0", DocumentType.TERMS_OF_USE, TermStatus.DRAFT);
        when(termRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)).thenReturn(Optional.empty());
        when(termRepository.save(draft)).thenReturn(draft);

        TermModel result = service.updateTermStatus(1L, TermStatus.PUBLISHED);

        assertEquals(TermStatus.PUBLISHED, result.getStatus());
    }

    @Test
    @DisplayName("updateTermStatus para PUBLISHED arquiva o ativo de outro id")
    void publishArchivesOtherActive() {
        TermModel draft = term(1, "2.0.0", DocumentType.TERMS_OF_USE, TermStatus.APPROVED);
        TermModel active = term(2, "1.0.0", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED);
        when(termRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)).thenReturn(Optional.of(active));
        when(termRepository.save(any(TermModel.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateTermStatus(1L, TermStatus.PUBLISHED);

        assertEquals(TermStatus.ARCHIVED, active.getStatus());
        assertEquals(TermStatus.PUBLISHED, draft.getStatus());
    }

    @Test
    @DisplayName("updateTermStatus para PUBLISHED não arquiva o próprio termo quando ele é o ativo encontrado")
    void publishIgnoresSameIdActive() {
        TermModel draft = term(1, "2.0.0", DocumentType.TERMS_OF_USE, TermStatus.DRAFT);
        TermModel sameId = term(1, "2.0.0", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED);
        when(termRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)).thenReturn(Optional.of(sameId));
        when(termRepository.save(draft)).thenReturn(draft);

        service.updateTermStatus(1L, TermStatus.PUBLISHED);

        assertEquals(TermStatus.PUBLISHED, sameId.getStatus());
        verify(termRepository, never()).save(sameId);
    }

    @Test
    @DisplayName("updateTermStatus de DRAFT para IN_REVIEW salva o novo status")
    void moveDraftToReview() {
        TermModel draft = term(1, "1.0.0", DocumentType.TERMS_OF_USE, TermStatus.DRAFT);
        when(termRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(termRepository.save(draft)).thenReturn(draft);

        TermModel result = service.updateTermStatus(1L, TermStatus.IN_REVIEW);

        assertEquals(TermStatus.IN_REVIEW, result.getStatus());
        verify(termRepository).save(draft);
    }

    // ---------------------------------------------------------------- versões

    @Test
    @DisplayName("isVersionGreater compara major, minor e patch em ordem")
    void versionComparison() {
        assertTrue(TermService.isVersionGreater("2.0.0", "1.9.9"));
        assertTrue(TermService.isVersionGreater("1.2.0", "1.1.9"));
        assertTrue(TermService.isVersionGreater("1.1.2", "1.1.1"));
        assertFalse(TermService.isVersionGreater("1.1.1", "1.1.2"));
        assertFalse(TermService.isVersionGreater("1.1.9", "1.2.0"));
        assertFalse(TermService.isVersionGreater("1.9.9", "2.0.0"));
        assertThrows(TermVersionException.class, () -> TermService.isVersionGreater("1.0", "1.0.0"));
    }
}
