package com.n0hana.echoes_server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n0hana.echoes_server.dto.ClassroomDTO;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.service.ClassroomService;
import com.n0hana.echoes_server.service.auth.JwtTokenService;

@WebMvcTest(ClassroomController.class)
@AutoConfigureMockMvc(addFilters = false) // Desativa os filtros JWT genéricos para testar focado no @PreAuthorize
class ClassroomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Mock
    private ClassroomService classroomService;

    // Precisamos mockar dependências globais de segurança se elas forem carregadas pelo contexto do WebMvcTest
    @Mock 
    private JwtTokenService jwtTokenService; 

    @Test
    @WithMockUser(roles = "TEACHER") // Simula um usuário com a role TEACHER
    void createClassroom_ShouldReturn200_WhenUserIsTeacher() throws Exception {
        // Arrange
        ClassroomDTO.CreateRequest request = new ClassroomDTO.CreateRequest("Redes", "Redes de Computadores");
        ClassroomDTO.ClassroomResponse response = new ClassroomDTO.ClassroomResponse(
            UUID.randomUUID(), "Redes", "Redes de Computadores", "XYZ12345", "Prof", Instant.now()
        );

        when(classroomService.createClassroom(any(ClassroomDTO.CreateRequest.class), any(User.class)))
            .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/classrooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("XYZ12345"))
                .andExpect(jsonPath("$.name").value("Redes"));
    }

    @Test
    @WithMockUser(roles = "STUDENT") // Simula um aluno tentando criar turma
    void createClassroom_ShouldReturn403_WhenUserIsStudent() throws Exception {
        // Arrange
        ClassroomDTO.CreateRequest request = new ClassroomDTO.CreateRequest("Redes", "Redes de Computadores");

        // Act & Assert (O Spring Security deve barrar antes mesmo de chegar no Service)
        mockMvc.perform(post("/api/classrooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void enrollStudent_ShouldReturn200_WhenUserIsStudent() throws Exception {
        // Arrange
        ClassroomDTO.EnrollRequest request = new ClassroomDTO.EnrollRequest("XYZ12345");
        ClassroomDTO.ClassroomResponse response = new ClassroomDTO.ClassroomResponse(
            UUID.randomUUID(), "Redes", "Redes", "XYZ12345", "Prof", Instant.now()
        );

        when(classroomService.enrollStudent(eq("XYZ12345"), any(User.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/classrooms/enroll")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("XYZ12345"));
    }
}
