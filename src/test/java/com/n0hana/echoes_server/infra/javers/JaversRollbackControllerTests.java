package com.n0hana.echoes_server.infra.javers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.n0hana.echoes_server.device.DeviceJwtService;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointService;
import com.n0hana.echoes_server.infra.javers.retention.JaversRetentionService;
import com.n0hana.echoes_server.infra.javers.rollback.JaversRollbackService;
import com.n0hana.echoes_server.infra.javers.rollback.RollbackNotAvailableException;
import com.n0hana.echoes_server.infra.javers.rollback.RollbackResult;
import com.n0hana.echoes_server.term.TermService;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.User;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JaversRollbackControllerTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JaversRollbackService rollback;
    @MockitoBean private JaversCheckpointService checkpoints;
    @MockitoBean private JaversRetentionService retention;
    @MockitoBean private DeviceJwtService deviceJwt;
    @MockitoBean private TermService termService;

    private static final String BASE = "/api/v1/admin";

    private RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private RequestPostProcessor nonAdmin() {
        return user("aluno").roles("STUDENT");
    }

    private RequestPostProcessor adminEntity(User principal) {
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private Admin newAdmin() {
        Admin a = new Admin();
        a.setId(UUID.randomUUID());
        a.setEmail("a@test.local");
        return a;
    }

    private String rollbackBody() {
        return "{\"checkpointId\":\"" + UUID.randomUUID() + "\",\"entityType\":\"User\",\"entityId\":\"" + UUID.randomUUID() + "\"}";
    }

    @Test
    @DisplayName("Todos os endpoints negam quem não é ADMIN (403)")
    void nonAdminIsForbiddenEverywhere() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get(BASE + "/javers/history/User/" + id).with(nonAdmin())).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/javers/rollback").with(nonAdmin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(rollbackBody())).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/javers/rollback/checkpoint/" + id).with(nonAdmin()).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/javers/checkpoint/" + id + "/approve").with(nonAdmin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/javers/prune/run").with(nonAdmin()).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/devices/" + id + "/revoke-tokens").with(nonAdmin()).with(csrf())).andExpect(status().isForbidden());

        verify(deviceJwt, never()).revokeAllForDevice(any());
        verify(retention, never()).pruneAll();
    }

    @Test
    @DisplayName("Sem autenticação nenhum endpoint responde 2xx")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post(BASE + "/javers/prune/run").with(csrf())).andExpect(status().is4xxClientError());
        mockMvc.perform(post(BASE + "/devices/" + UUID.randomUUID() + "/revoke-tokens").with(csrf())).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("ADMIN revoga os tokens de um dispositivo (204) e o serviço é chamado")
    void adminRevokesDeviceTokens() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(BASE + "/devices/" + id + "/revoke-tokens").with(admin()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(deviceJwt).revokeAllForDevice(id);
    }

    @Test
    @DisplayName("ADMIN dispara a poda manual e recebe a contagem")
    void adminRunsPrune() throws Exception {
        when(retention.pruneAll()).thenReturn(42);

        mockMvc.perform(post(BASE + "/javers/prune/run").with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(42));
    }

    @Test
    @DisplayName("ADMIN executa o rollback; tipo sem histórico responde 409")
    void adminRollsBack() throws Exception {
        org.mockito.Mockito.doReturn(User.class).when(rollback).resolveType("User");
        UUID entityId = UUID.randomUUID();
        when(rollback.rollbackEntity(any(), eq(User.class), eq(entityId)))
                .thenReturn(new RollbackResult(entityId, User.class.getName(), Instant.now(), List.of(), "x"));

        mockMvc.perform(post(BASE + "/javers/rollback").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"checkpointId\":\"" + UUID.randomUUID() + "\",\"entityType\":\"User\",\"entityId\":\"" + entityId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityId").value(entityId.toString()))
                .andExpect(jsonPath("$.manualActionRequired").value("x"));

        when(rollback.rollbackEntity(any(), eq(User.class), any()))
                .thenThrow(new RollbackNotAvailableException("arquivo de áudio não existe mais; reupar antes de restaurar"));
        mockMvc.perform(post(BASE + "/javers/rollback").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(rollbackBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("arquivo de áudio não existe mais; reupar antes de restaurar"));
    }

    @Test
    @DisplayName("Body inválido no rollback responde 400")
    void invalidRollbackBody() throws Exception {
        mockMvc.perform(post(BASE + "/javers/rollback").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"entityType\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rollback em lote do checkpoint aprovado")
    void adminRollsBackCheckpoint() throws Exception {
        UUID id = UUID.randomUUID();
        when(rollback.rollbackCheckpoint(id)).thenReturn(List.of());

        mockMvc.perform(post(BASE + "/javers/rollback/checkpoint/" + id).with(admin()).with(csrf()))
                .andExpect(status().isOk());

        verify(rollback).rollbackCheckpoint(id);
    }

    @Test
    @DisplayName("Aprovação usa o usuário autenticado como aprovador; approvedBy diferente no body é recusado")
    void approveUsesAuthenticatedUser() throws Exception {
        UUID id = UUID.randomUUID();
        Admin principal = newAdmin();

        mockMvc.perform(post(BASE + "/javers/checkpoint/" + id + "/approve").with(adminEntity(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        verify(checkpoints).approve(id, principal.getId().toString());

        mockMvc.perform(post(BASE + "/javers/checkpoint/" + id + "/approve").with(adminEntity(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"approvedBy\":\"outra-pessoa\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Aprovar com o mesmo solicitante (IllegalStateException do serviço) responde 400")
    void approveBySameRequesterIs400() throws Exception {
        UUID id = UUID.randomUUID();
        Admin principal = newAdmin();
        org.mockito.Mockito.doThrow(new IllegalStateException("Quem aprova precisa ser diferente de quem solicitou"))
                .when(checkpoints).approve(eq(id), any());

        mockMvc.perform(post(BASE + "/javers/checkpoint/" + id + "/approve").with(adminEntity(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quem aprova precisa ser diferente de quem solicitou"));
    }

    @Test
    @DisplayName("ADMIN consulta o histórico (lista vazia para id sem commits)")
    void adminReadsHistory() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doReturn(User.class).when(rollback).resolveType("User");
        org.mockito.Mockito.doReturn(com.n0hana.echoes_server.user.model.Student.class).when(rollback).concreteClass(User.class, id);

        mockMvc.perform(get(BASE + "/javers/history/User/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
