package com.n0hana.echoes_server.auth.password;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.user.UserRepository;

@WebMvcTest(PasswordController.class)
@ActiveProfiles("test")
@WithMockUser
@AutoConfigureMockMvc
class PasswordControllerTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PasswordService service;
    @MockitoBean private JwtTokenService jwtTokenService;
    @MockitoBean private UserRepository userRepository;

    private void json(String path, String body, int expected) throws Exception {
        mockMvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected));
    }

    @Test
    @DisplayName("POST /forgot com e-mail válido responde 204")
    void forgot() throws Exception {
        json("/api/v1/password/forgot", "{\"email\":\"a@b.com\"}", 204);
        verify(service).request("a@b.com");
    }

    @Test
    @DisplayName("POST /forgot com e-mail inválido responde 400 e não chama o serviço")
    void forgotInvalid() throws Exception {
        json("/api/v1/password/forgot", "{\"email\":\"nao-e-email\"}", 400);
        verify(service, never()).request(anyString());
    }

    @Test
    @DisplayName("POST /validate devolve o token de redefinição")
    void validate() throws Exception {
        when(service.validate("a@b.com", "123456")).thenReturn("tok-1");
        mockMvc.perform(post("/api/v1/password/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok-1"));
    }

    @Test
    @DisplayName("POST /validate com código de tamanho errado responde 400")
    void validateBadCode() throws Exception {
        json("/api/v1/password/validate", "{\"email\":\"a@b.com\",\"code\":\"12\"}", 400);
        json("/api/v1/password/validate", "{\"email\":\"a@b.com\",\"code\":\"1234567\"}", 400);
        json("/api/v1/password/validate", "{\"email\":\"a@b.com\",\"code\":\"12345a\"}", 400);
        verify(service, never()).validate(anyString(), anyString());
    }

    @Test
    @DisplayName("POST /reset com senha forte responde 204; com senha fraca responde 400")
    void reset() throws Exception {
        json("/api/v1/password/reset", "{\"email\":\"a@b.com\",\"token\":\"t\",\"newPassword\":\"Nova@Senha1\"}", 204);
        verify(service).reset("a@b.com", "t", "Nova@Senha1");

        json("/api/v1/password/reset", "{\"email\":\"a@b.com\",\"token\":\"t\",\"newPassword\":\"fraca\"}", 400);
    }

    @Test
    void resetValidatesBcryptLimitInUtf8BeforeConsumingToken() throws Exception {
        String atLimit = "Senha@1a" + "é".repeat(32);
        String overLimit = atLimit + "a";
        String body = "{\"email\":\"a@b.com\",\"token\":\"t\",\"newPassword\":\"%s\"}";
        json("/api/v1/password/reset", body.formatted(overLimit), 400);
        verify(service, never()).reset("a@b.com", "t", overLimit);
        json("/api/v1/password/reset", body.formatted(atLimit), 204);
        verify(service).reset("a@b.com", "t", atLimit);
    }
}
