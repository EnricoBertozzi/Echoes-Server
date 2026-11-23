package com.n0hana.echoes_server.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import com.n0hana.echoes_server.term.dto.AcceptTermRequestDTO;
import com.n0hana.echoes_server.term.dto.CreateTermRequestDTO;
import com.n0hana.echoes_server.term.dto.TermResponseDTO;
import com.n0hana.echoes_server.term.dto.UpdateTermContentRequestDTO;
import com.n0hana.echoes_server.term.dto.UpdateTermStatusRequestDTO;
import com.n0hana.echoes_server.term.exception.TermNotFoundException;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class TermControllerCoverageTests {

    private static final String EMAIL = "aluno@example.com";

    @Mock
    private TermService termService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TermController controller;

    private static final Instant NOW = Instant.parse("2025-01-01T10:00:00Z");

    private static TermModel term(long id, String version, DocumentType type, TermStatus status) {
        return new TermModel(id, version, "# conteúdo " + version, NOW, type, status);
    }

    private static UserDetails principal() {
        UserDetails details = mock(UserDetails.class);
        when(details.getUsername()).thenReturn(EMAIL);
        return details;
    }

    private User existingUser() {
        User user = new Student();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("getAllTerms repassa os filtros ao serviço e converte cada termo para DTO")
    void getAllTermsMapsToDto() {
        TermModel a = term(1, "1.0.0", DocumentType.PRIVACY_POLICY, TermStatus.PUBLISHED);
        TermModel b = term(2, "0.9.0", DocumentType.PRIVACY_POLICY, TermStatus.ARCHIVED);
        when(termService.getAllTerms(DocumentType.PRIVACY_POLICY, null)).thenReturn(List.of(a, b));

        ResponseEntity<List<TermResponseDTO>> response =
            controller.getAllTerms(DocumentType.PRIVACY_POLICY, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<TermResponseDTO> body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.size());
        assertEquals(new TermResponseDTO(1L, "1.0.0", "# conteúdo 1.0.0",
            DocumentType.PRIVACY_POLICY, TermStatus.PUBLISHED, NOW), body.get(0));
        assertEquals(TermStatus.ARCHIVED, body.get(1).status());
    }

    @Test
    @DisplayName("getAllTerms sem resultados devolve lista vazia")
    void getAllTermsEmpty() {
        when(termService.getAllTerms(null, TermStatus.DRAFT)).thenReturn(List.of());

        ResponseEntity<List<TermResponseDTO>> response = controller.getAllTerms(null, TermStatus.DRAFT);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    @DisplayName("getPendingTerms consulta pelo id do usuário autenticado")
    void getPendingTerms() {
        User user = existingUser();
        when(termService.getPendingRequiredTerms(user.getId()))
            .thenReturn(List.of(DocumentType.TERMS_OF_USE, DocumentType.COOKIES_POLICY));

        ResponseEntity<List<DocumentType>> response = controller.getPendingTerms(principal());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(DocumentType.TERMS_OF_USE, DocumentType.COOKIES_POLICY), response.getBody());
    }

    @Test
    @DisplayName("getPendingTerms lança UserNotFoundException quando o usuário não existe")
    void getPendingTermsUnknownUser() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> controller.getPendingTerms(principal()));

        verify(termService, never()).getPendingRequiredTerms(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("getLatestTerms devolve o markdown com Content-Type UTF-8 e nosniff")
    void getLatestTermsHeaders() {
        when(termService.getActiveTerms(DocumentType.TERMS_OF_USE))
            .thenReturn(term(3, "2.0.0", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED));

        ResponseEntity<String> response = controller.getLatestTerms(DocumentType.TERMS_OF_USE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("# conteúdo 2.0.0", response.getBody());
        MediaType contentType = response.getHeaders().getContentType();
        assertNotNull(contentType);
        assertEquals("text", contentType.getType());
        assertEquals("markdown", contentType.getSubtype());
        assertEquals(java.nio.charset.StandardCharsets.UTF_8, contentType.getCharset());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    @DisplayName("getLatestTerms propaga TermNotFoundException quando não há termo ativo")
    void getLatestTermsNotFound() {
        when(termService.getActiveTerms(DocumentType.COOKIES_POLICY))
            .thenThrow(new TermNotFoundException("sem termo"));

        assertThrows(TermNotFoundException.class,
            () -> controller.getLatestTerms(DocumentType.COOKIES_POLICY));
    }

    @Test
    @DisplayName("getLatestTermsJson devolve o termo ativo como DTO")
    void getLatestTermsJson() {
        when(termService.getActiveTerms(DocumentType.PRIVACY_POLICY))
            .thenReturn(term(4, "1.2.0", DocumentType.PRIVACY_POLICY, TermStatus.PUBLISHED));

        ResponseEntity<TermResponseDTO> response = controller.getLatestTermsJson(DocumentType.PRIVACY_POLICY);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(4L, response.getBody().id());
        assertEquals("1.2.0", response.getBody().version());
        assertEquals(DocumentType.PRIVACY_POLICY, response.getBody().type());
        assertEquals(NOW, response.getBody().timestamp());
    }

    @Test
    @DisplayName("hasAcceptedLatestTerms devolve true/false conforme o serviço")
    void hasAccepted() {
        User user = existingUser();
        when(termService.hasAcceptedLatestTerms(user.getId(), DocumentType.TERMS_OF_USE)).thenReturn(true);
        when(termService.hasAcceptedLatestTerms(user.getId(), DocumentType.PRIVACY_POLICY)).thenReturn(false);

        assertEquals(Boolean.TRUE, controller.hasAcceptedLatestTerms(principal(), DocumentType.TERMS_OF_USE).getBody());
        assertEquals(Boolean.FALSE, controller.hasAcceptedLatestTerms(principal(), DocumentType.PRIVACY_POLICY).getBody());
    }

    @Test
    @DisplayName("hasAcceptedLatestTerms lança UserNotFoundException para usuário inexistente")
    void hasAcceptedUnknownUser() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
            () -> controller.hasAcceptedLatestTerms(principal(), DocumentType.TERMS_OF_USE));
    }

    @Test
    @DisplayName("acceptTerms registra o aceite e devolve o termo aceito")
    void acceptTerms() {
        User user = existingUser();
        TermModel accepted = term(5, "1.1.0", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED);
        when(termService.acceptTerms(user.getId(), DocumentType.TERMS_OF_USE))
            .thenReturn(new UserTermAcceptance(user, accepted));

        ResponseEntity<TermResponseDTO> response =
            controller.acceptTerms(principal(), new AcceptTermRequestDTO(DocumentType.TERMS_OF_USE));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5L, response.getBody().id());
        assertEquals("1.1.0", response.getBody().version());
    }

    @Test
    @DisplayName("acceptTerms lança UserNotFoundException e não registra aceite para usuário inexistente")
    void acceptTermsUnknownUser() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
            () -> controller.acceptTerms(principal(), new AcceptTermRequestDTO(DocumentType.TERMS_OF_USE)));

        verify(termService, never()).acceptTerms(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("revokeConsent chama o serviço com o usuário e responde 204")
    void revokeConsent() {
        User user = existingUser();

        ResponseEntity<Void> response = controller.revokeConsent(principal());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(termService).revoke(user);
    }

    @Test
    @DisplayName("revokeConsent lança UserNotFoundException para usuário inexistente")
    void revokeConsentUnknownUser() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> controller.revokeConsent(principal()));

        verify(termService, never()).revoke(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("createTerm responde 201 com o termo criado")
    void createTerm() {
        CreateTermRequestDTO dto = new CreateTermRequestDTO("1.0.0", "texto", DocumentType.PROCESSING_OPERATIONS, null);
        when(termService.createTerm(dto))
            .thenReturn(term(6, "1.0.0", DocumentType.PROCESSING_OPERATIONS, TermStatus.DRAFT));

        ResponseEntity<TermResponseDTO> response = controller.createTerm(dto);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(6L, response.getBody().id());
        assertEquals(TermStatus.DRAFT, response.getBody().status());
    }

    @Test
    @DisplayName("updateTermContent repassa id e conteúdo ao serviço")
    void updateTermContent() {
        TermModel updated = term(7, "1.0.1", DocumentType.TERMS_OF_USE, TermStatus.DRAFT);
        updated.setContent("novo texto");
        when(termService.updateTermContent(7L, "novo texto")).thenReturn(updated);

        ResponseEntity<TermResponseDTO> response =
            controller.updateTermContent(7L, new UpdateTermContentRequestDTO("novo texto"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("novo texto", response.getBody().content());
    }

    @Test
    @DisplayName("updateTermStatus repassa id e status ao serviço")
    void updateTermStatus() {
        when(termService.updateTermStatus(8L, TermStatus.PUBLISHED))
            .thenReturn(term(8, "1.0.2", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED));

        ResponseEntity<TermResponseDTO> response =
            controller.updateTermStatus(8L, new UpdateTermStatusRequestDTO(TermStatus.PUBLISHED));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(TermStatus.PUBLISHED, response.getBody().status());
    }
}
