package com.n0hana.echoes_server.scenario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.infra.file.exception.StorageFileNotFoundException;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.user.UserRepository;

@WebMvcTest(ScenarioController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
@ActiveProfiles("test")
class ScenarioAudioControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ScenarioService service;

  @MockitoBean
  private JwtTokenService jwtTokenService;

  @MockitoBean
  private UserRepository userRepository;

  @TempDir
  Path dir;

  @Test
  @DisplayName("GET /api/v1/scenarios/{id}/audio devolve o mp3 (200 OK)")
  void shouldDownloadAudio() throws Exception {
    UUID id = UUID.randomUUID();
    byte[] bytes = { 1, 2, 3, 4 };
    Path file = Files.write(dir.resolve("sopro.mp3"), bytes);
    when(service.findAudioPath(eq(id), any())).thenReturn(file);

    mockMvc.perform(get("/api/v1/scenarios/{id}/audio", id))
        .andExpect(status().isOk())
        .andExpect(content().contentType("audio/mpeg"))
        .andExpect(content().bytes(bytes));
  }

  @Test
  @DisplayName("GET /api/v1/scenarios/{id}/audio sem arquivo no disco (404 Not Found)")
  void shouldReturnNotFoundWhenFileMissing() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.findAudioPath(eq(id), any())).thenThrow(new StorageFileNotFoundException());

    mockMvc.perform(get("/api/v1/scenarios/{id}/audio", id))
        .andExpect(status().isNotFound());
  }
}
