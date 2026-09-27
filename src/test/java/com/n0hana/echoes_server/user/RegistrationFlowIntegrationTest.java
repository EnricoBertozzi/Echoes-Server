package com.n0hana.echoes_server.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.term.TermRepository;
import com.n0hana.echoes_server.term.UserTermAcceptanceRepository;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;

/**
 * Fluxo completo dos dois passos de registro contra Redis real (H2 no lugar
 * do MySQL). Ignorado quando o Redis está fora do ar — localmente ele está
 * desligado; no CI o serviço redis está disponível e o teste roda de ponta a
 * ponta, incluindo as queries JPQL que os testes unitários nunca executam.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@EnabledIf("redisReachable")
class RegistrationFlowIntegrationTest {

    private static final String STRONG_PASSWORD = "SenhaForte123!";

    /**
     * Os três termos obrigatórios em JSON. O CompleteRegistrationDTO exige
     * acceptedTerms (@NotNull) + o TermService valida que TERMS_OF_USE,
     * PRIVACY_POLICY e COOKIES_POLICY estão presentes — sem isso, o POST
     * falha em Bean Validation antes mesmo de chegar ao UserService.
     */
    private static final String TERMS_JSON = "\"acceptedTerms\":[\"TERMS_OF_USE\",\"PRIVACY_POLICY\",\"COOKIES_POLICY\"]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTermAcceptanceRepository userTermAcceptanceRepository;

    static boolean redisReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 6379), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void seedRequiredTerms() {
        for (DocumentType type : List.of(
                DocumentType.TERMS_OF_USE,
                DocumentType.PRIVACY_POLICY,
                DocumentType.COOKIES_POLICY)) {

            boolean ja = termRepository
                    .findFirstByTypeAndStatusOrderByTimestampDesc(type, TermStatus.PUBLISHED)
                    .isPresent();

            if (!ja) {
                TermModel term = new TermModel();
                term.setType(type);
                term.setVersion("1.0.0");
                term.setStatus(TermStatus.PUBLISHED);
                term.setContent("# " + type + " 1.0.0");
                termRepository.save(term);
            }
        }
    }

    @Autowired
    private TermRepository termRepository;

    @Test
    @DisplayName("Passo 1 guarda pendência no Redis sem criar linha; passo 2 cria o usuário e consome o convite")
    void fullTwoStepFlowKeepsUserPendingUntilConfirmation() throws Exception {
        String email = "aluno@example.com";
        UUID institutionId = UUID.randomUUID();
        userTermAcceptanceRepository.deleteAll();
        userRepository.deleteAll();
        pendingRegistrationRepository.deleteByEmail(email);

        mockMvc.perform(post("/users/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Aluno\",\"email\":\"" + email + "\","
                        + "\"institutionId\":\"" + institutionId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.name").value("Aluno"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.institutionId").value(institutionId.toString()))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist());

        assertFalse(userRepository.existsByEmail(email),
                "nenhum usuário deve existir no banco antes da confirmação");

        String code = pendingRegistrationRepository.findByEmail(email).orElseThrow().code();

        mockMvc.perform(post("/users/2fa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + STRONG_PASSWORD
                        + "\",\"code\":\"000000\"," + TERMS_JSON + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Código inválido"));

        mockMvc.perform(post("/users/2fa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + STRONG_PASSWORD
                        + "\",\"code\":\"" + code + "\"," + TERMS_JSON + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.institutionId").value(institutionId.toString()));

        assertTrue(pendingRegistrationRepository.findByEmail(email).isEmpty(),
                "convite deve ser consumido após a confirmação");
        assertTrue(userRepository.existsByEmail(email));

        mockMvc.perform(get("/users/student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/users/student").param("institutionId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Cinco códigos errados invalidam o convite; reconvite reenvia novo código")
    void fiveWrongCodesInvalidateInviteAndReinviteResends() throws Exception {
        String email = "novo-admin@example.com";
        userTermAcceptanceRepository.deleteAll();
        userRepository.deleteAll();
        pendingRegistrationRepository.deleteByEmail(email);

        mockMvc.perform(post("/users/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo Admin\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());

        for (int i = 0; i < UserService.MAX_VERIFICATION_ATTEMPTS; i++) {
            mockMvc.perform(post("/users/2fa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + STRONG_PASSWORD
                            + "\",\"code\":\"000000\"," + TERMS_JSON + "}"))
                    .andExpect(status().isBadRequest());
        }

        assertTrue(pendingRegistrationRepository.findByEmail(email).isEmpty(),
                "convite deve ser invalidado após esgotar as tentativas");

        mockMvc.perform(post("/users/2fa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + STRONG_PASSWORD
                        + "\",\"code\":\"000000\"," + TERMS_JSON + "}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/users/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo Admin\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());

        assertTrue(pendingRegistrationRepository.findByEmail(email).isPresent(),
                "reconvite deve criar nova pendência (reenvio de código)");
    }

    @Test
    @DisplayName("Senha fraca na confirmação → 400 'Senha fraca' (política S-RF010)")
    void weakPasswordIsRejectedAtConfirmation() throws Exception {
        String email = "gestor@example.com";
        userTermAcceptanceRepository.deleteAll();
        userRepository.deleteAll();
        pendingRegistrationRepository.deleteByEmail(email);

        mockMvc.perform(post("/users/manager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Gestor\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());

        String code = pendingRegistrationRepository.findByEmail(email).orElseThrow().code();

        mockMvc.perform(post("/users/2fa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"senha12345"
                        + "\",\"code\":\"" + code + "\"," + TERMS_JSON + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").value("Senha fraca"));
    }
}
