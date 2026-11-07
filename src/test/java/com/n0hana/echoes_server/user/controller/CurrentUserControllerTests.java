package com.n0hana.echoes_server.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.infra.security.JwtFilter;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.UserRole;
import com.n0hana.echoes_server.user.service.UserInfoService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

@WebMvcTest(CurrentUserController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CurrentUserControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserInfoService userInfoService;

    @MockitoBean
    private JwtFilter filter;

    @BeforeEach
    void setup() throws Exception {
        // Configura o filtro JWT para endpoints que exigem autenticação
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);

            chain.doFilter(request, response);

            return null;
        }).when(filter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/users/me retorna os dados do usuário autenticado")
    void findCurrentUserAndReturnsUserInfo() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        String name = "example";
        String email = "usuario@email.com";
        UUID institutionId = UUID.randomUUID();
        UserRole role = UserRole.MANAGER;

        UserDTO userDTO = new UserDTO(id, name, email, institutionId, role);

        when(userInfoService.findUserByEmail(email)).thenReturn(userDTO);

        // Act e Assert
        mockMvc.perform(get("/api/v1/users/me")
                .with(user(email)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(userInfoService).findUserByEmail(email);
    }

    @Test
    @DisplayName("GET /api/v1/users/me quando o usuário não existe")
    void findCurrentUserAndReturnsNotFound() throws Exception {
        // Arrange
        String email = "usuario@email.com";

        when(userInfoService.findUserByEmail(email)).thenThrow(new UserNotFoundException());

        // Act e Assert
        mockMvc.perform(get("/api/v1/users/me")
                .with(user(email)))
                .andExpect(status().isNotFound());

        verify(userInfoService).findUserByEmail(email);
    }

    @Test
    @DisplayName("GET /api/v1/users/me quando não autenticado")
    void findCurrentUserWithoutAuthentication() throws Exception {
        // Act e Assert
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());

        verifyNoInteractions(userInfoService);
    }

}