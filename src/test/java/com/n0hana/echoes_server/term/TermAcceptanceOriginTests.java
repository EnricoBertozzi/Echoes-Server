package com.n0hana.echoes_server.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class TermAcceptanceOriginTests {

    @Mock
    private TermRepository termRepository;

    @Mock
    private UserTermAcceptanceRepository userTermAcceptanceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TermService termService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = Mockito.mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        TermModel published = new TermModel(
            7L, "1.1.0", "conteúdo", Instant.now(), DocumentType.PRIVACY_POLICY, TermStatus.PUBLISHED);
        when(termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(
            DocumentType.PRIVACY_POLICY, TermStatus.PUBLISHED))
            .thenReturn(Optional.of(published));
        when(userTermAcceptanceRepository.findByUserIdAndTermsId(userId, 7L))
            .thenReturn(Optional.empty());
        when(userTermAcceptanceRepository.save(any(UserTermAcceptance.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/terms/accept");
        request.setRemoteAddr("203.0.113.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Test
    @DisplayName("Grava IP e User-Agent da requisição no aceite")
    void storesIpAndUserAgent() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Firefox/140.0");

        UserTermAcceptance acceptance = termService.acceptTerms(userId, DocumentType.PRIVACY_POLICY);

        assertEquals("203.0.113.10", acceptance.getIpAddress());
        assertEquals("Mozilla/5.0 (X11; Linux x86_64) Firefox/140.0", acceptance.getUserAgent());
    }

    @Test
    @DisplayName("Ignora X-Forwarded-For quando o proxy não é confiável")
    void ignoresForwardedForByDefault() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.99");

        UserTermAcceptance acceptance = termService.acceptTerms(userId, DocumentType.PRIVACY_POLICY);

        assertEquals("203.0.113.10", acceptance.getIpAddress());
    }

    @Test
    @DisplayName("Usa X-Forwarded-For atrás de proxy confiável")
    void usesForwardedForBehindTrustedProxy() {
        ReflectionTestUtils.setField(termService, "trustForwardedFor", true);
        MockHttpServletRequest request = bindRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.99, 10.0.0.1");

        UserTermAcceptance acceptance = termService.acceptTerms(userId, DocumentType.PRIVACY_POLICY);

        assertEquals("198.51.100.99", acceptance.getIpAddress());
    }

    @Test
    @DisplayName("Corta User-Agent maior que a coluna")
    void truncatesLongUserAgent() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader("User-Agent", "a".repeat(2000));

        UserTermAcceptance acceptance = termService.acceptTerms(userId, DocumentType.PRIVACY_POLICY);

        assertEquals(UserTermAcceptance.USER_AGENT_MAX_LENGTH, acceptance.getUserAgent().length());
    }

    @Test
    @DisplayName("Fora de uma requisição HTTP o aceite fica sem origem")
    void noRequestLeavesOriginEmpty() {
        UserTermAcceptance acceptance = termService.acceptTerms(userId, DocumentType.PRIVACY_POLICY);

        assertNull(acceptance.getIpAddress());
        assertNull(acceptance.getUserAgent());
    }
}
