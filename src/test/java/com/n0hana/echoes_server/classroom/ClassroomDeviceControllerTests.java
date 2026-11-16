package com.n0hana.echoes_server.classroom;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceStatus;
import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.user.UserRepository;

@WebMvcTest(ClassroomDeviceController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ClassroomDeviceControllerTests {

    private static final String EMAIL = "usuario@email.com";
    private static final Principal PRINCIPAL = () -> EMAIL;
    private static final String BASE = "/api/v1/classrooms/";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClassroomDeviceService service;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/classrooms/{id}/devices lista os dispositivos (200 OK)")
    void shouldListDevices() throws Exception {
        UUID id = UUID.randomUUID();
        DeviceModel device = DeviceModel.builder()
                .id(UUID.randomUUID())
                .name("Sensor Norte")
                .hardwareId("AA:BB:CC:DD:EE:FF")
                .publicKey("pem")
                .status(DeviceStatus.ACTIVE)
                .build();
        when(service.findDevices(EMAIL, id)).thenReturn(List.of(device));

        mockMvc.perform(get(BASE + id + "/devices").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sensor Norte"))
                .andExpect(jsonPath("$[0].hardwareId").value("AA:BB:CC:DD:EE:FF"));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id}/devices sem acesso (403 Forbidden)")
    void shouldReturnForbiddenWhenListing() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findDevices(EMAIL, id))
                .thenThrow(new ClassroomForbiddenException("Sem acesso aos dispositivos desta turma"));

        mockMvc.perform(get(BASE + id + "/devices").principal(PRINCIPAL))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/classrooms/{id}/devices/{deviceId} adiciona o dispositivo (204 No Content)")
    void shouldAddDevice() throws Exception {
        UUID id = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        mockMvc.perform(put(BASE + id + "/devices/" + deviceId).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).addDevice(EMAIL, id, deviceId);
    }

    @Test
    @DisplayName("PUT /api/v1/classrooms/{id}/devices/{deviceId} turma arquivada (409 Conflict)")
    void shouldReturnConflictWhenArchived() throws Exception {
        UUID id = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(service.addDevice(EMAIL, id, deviceId)).thenThrow(new ClassroomArchivedException());

        mockMvc.perform(put(BASE + id + "/devices/" + deviceId).principal(PRINCIPAL))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /api/v1/classrooms/{id}/devices/{deviceId} dispositivo inexistente (404 Not Found)")
    void shouldReturnNotFoundWhenDeviceDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(service.addDevice(EMAIL, id, deviceId)).thenThrow(new DeviceNotFoundException());

        mockMvc.perform(put(BASE + id + "/devices/" + deviceId).principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Dispositivo não encontrado"));
    }

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{id}/devices/{deviceId} remove o dispositivo (204 No Content)")
    void shouldRemoveDevice() throws Exception {
        UUID id = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        mockMvc.perform(delete(BASE + id + "/devices/" + deviceId).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).removeDevice(EMAIL, id, deviceId);
    }

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{id}/devices/{deviceId} dispositivo fora da turma (404 Not Found)")
    void shouldReturnNotFoundWhenDeviceIsNotInClassroom() throws Exception {
        UUID id = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(service.removeDevice(EMAIL, id, deviceId))
                .thenThrow(new ClassroomNotFoundException("Dispositivo não encontrado na turma"));

        mockMvc.perform(delete(BASE + id + "/devices/" + deviceId).principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Dispositivo não encontrado na turma"));
    }
}
