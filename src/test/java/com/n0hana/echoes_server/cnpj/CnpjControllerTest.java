package com.n0hana.echoes_server.cnpj;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.cnpj.exception.CnpjInvalidoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjNaoEncontradoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjProviderIndisponivelException;

@WebMvcTest(CnpjController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class CnpjControllerTest {

    private static final String CNPJ = "19131243000197";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CnpjService service;

    @Test
    @DisplayName("GET /api/v1/cnpj/{cnpj} válido → 200 + DTO em camelCase")
    void consultaValidaRetorna200() throws Exception {
        CnpjDTO dto = new CnpjDTO(CNPJ, "Empresa X", "Fantasia",
            "Rua X", "100", null, "Centro", "São Paulo", "SP", "01001000" ,
            null);

        when(service.consultar(eq(CNPJ))).thenReturn(dto);

        mockMvc.perform(get("/api/v1/cnpj/{cnpj}", CNPJ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.razaoSocial").value("Empresa X"))
            .andExpect(jsonPath("$.nomeFantasia").value("Fantasia"))
            .andExpect(jsonPath("$.razao_social").doesNotExist());
    }

    @Test
    @DisplayName("CNPJ inválido → 400 + message")
    void cnpjInvalidoRetorna400() throws Exception {
        when(service.consultar(eq(CNPJ))).thenThrow(new CnpjInvalidoException("CNPJ inválido"));

        mockMvc.perform(get("/api/v1/cnpj/{cnpj}", CNPJ))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("CNPJ inválido"));
    }

    @Test
    @DisplayName("CNPJ não encontrado → 404 + message")
    void cnpjNaoEncontradoRetorna404() throws Exception {
        when(service.consultar(eq(CNPJ))).thenThrow(new CnpjNaoEncontradoException("CNPJ não encontrado"));

        mockMvc.perform(get("/api/v1/cnpj/{cnpj}", CNPJ))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("CNPJ não encontrado"));
    }

    @Test
    @DisplayName("Provedores indisponíveis → 503 + message")
    void todosProvedoresCaidosRetorna503() throws Exception {
        when(service.consultar(eq(CNPJ)))
            .thenThrow(new CnpjProviderIndisponivelException("Serviço indisponível"));

        mockMvc.perform(get("/api/v1/cnpj/{cnpj}", CNPJ))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value("Serviço indisponível"));
    }
}
