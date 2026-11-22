package com.n0hana.echoes_server.scenario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("com.n0hana.echoes_server.support.RedisAvailability#redisReachable")
class ScenarioTeacherAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenService jwt;

    private User teacher;

    @BeforeEach
    void setUp() {
        Teacher t = new Teacher();
        t.setEmail("prof-" + UUID.randomUUID() + "@test.local");
        t.setName("Professor");
        t.setPassword("hash");
        teacher = users.save(t);
    }

    @AfterEach
    void tearDown() {
        users.delete(users.findById(teacher.getId()).orElse(teacher));
    }

    private String bearer() {
        return "Bearer " + jwt.generate(teacher);
    }

    @Test
    @DisplayName("Professor não cria cenário (403)")
    void teacherCannotCreate() throws Exception {
        MockMultipartFile dto = new MockMultipartFile("dto", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"name\":\"Cenario\",\"description\":\"Descricao valida\",\"pointId\":\"91748f73-53c0-4c63-934a-28b09c996314\"}".getBytes());
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", "x".getBytes());

        mockMvc.perform(multipart("/api/v1/scenarios").file(dto).file(file).header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Professor não edita nem exclui cenário (403)")
    void teacherCannotUpdateOrDelete() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/scenarios/{id}", id).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Novo nome\",\"description\":\"Descricao valida\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/scenarios/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Professor não troca o áudio (403) nem baixa o áudio do ESP (403)")
    void teacherCannotReuploadOrDownloadDeviceAudio() throws Exception {
        UUID id = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", "x".getBytes());

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PATCH, "/api/v1/scenarios/{id}/upload", id)
                .file(file).header("Authorization", bearer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/scenarios/{id}/audio", id).header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }
}
