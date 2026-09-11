package com.n0hana.echoes_server.user;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.user.dto.CompleteRegistrationDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.ExpiredTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.InvalidTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.RegistrationAlreadyCompletedException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;

@WebMvcTest(User2FAController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class User2FAControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

    private final UUID id = UUID.randomUUID();

    @Test
    @DisplayName("POST /users/2fa com dados válidos → 200 + UserDTO sem senha")
    void completeRegistrationValidReturns200WithoutPassword() throws Exception {
        when(service.completeRegistration(any(CompleteRegistrationDTO.class)))
                .thenReturn(new UserDTO(id, "Joao", "joao@example.com", null, UserRole.STUDENT));

        mockMvc.perform(post("/users/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"joao@example.com\",\"password\":\"senha12345\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Joao"))
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /users/2fa com código errado → 400 + mensagem")
    void wrongCodeReturns400WithMessage() throws Exception {
        when(service.completeRegistration(any(CompleteRegistrationDTO.class)))
                .thenThrow(new InvalidTwoFactorCodeException());

        mockMvc.perform(post("/users/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"joao@example.com\",\"password\":\"senha12345\",\"code\":\"999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Código inválido"));
    }

    @Test
    @DisplayName("POST /users/2fa com código expirado → 400 + mensagem")
    void expiredCodeReturns400WithMessage() throws Exception {
        when(service.completeRegistration(any(CompleteRegistrationDTO.class)))
                .thenThrow(new ExpiredTwoFactorCodeException());

        mockMvc.perform(post("/users/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"joao@example.com\",\"password\":\"senha12345\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Código expirado"));
    }

    @Test
    @DisplayName("POST /users/2fa com cadastro já finalizado → 409 + mensagem")
    void alreadyCompletedReturns409WithMessage() throws Exception {
        when(service.completeRegistration(any(CompleteRegistrationDTO.class)))
                .thenThrow(new RegistrationAlreadyCompletedException());

        mockMvc.perform(post("/users/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"joao@example.com\",\"password\":\"senha12345\",\"code\":\"123456\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cadastro já finalizado"));
    }

    @Test
    @DisplayName("POST /users/2fa com e-mail desconhecido → 404 + mensagem")
    void unknownEmailReturns404WithMessage() throws Exception {
        when(service.completeRegistration(any(CompleteRegistrationDTO.class)))
                .thenThrow(new UserNotFoundException());

        mockMvc.perform(post("/users/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"desconhecido@example.com\",\"password\":\"senha12345\",\"code\":\"123456\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    @Test
    @DisplayName("POST /users/2fa com senha em branco → 400 de validação")
    void blankPasswordReturns400Validation() throws Exception {
        mockMvc.perform(post("/users/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"joao@example.com\",\"password\":\"\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").value(anyOf(
                        equalTo("A senha é obrigatória"),
                        equalTo("A senha deve ter no mínimo 8 caracteres"))));
    }
}
