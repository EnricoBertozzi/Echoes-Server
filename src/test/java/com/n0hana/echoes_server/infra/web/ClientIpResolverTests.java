package com.n0hana.echoes_server.infra.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTests {

    private static final String NGINX = "127.0.0.1";
    private static final String PROXIES = NGINX + ",172.18.0.0/16";

    private static MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(peer);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    @DisplayName("Sem a flag, usa o vizinho direto mesmo com X-Forwarded-For")
    void flagOffUsesPeer() {
        assertEquals("127.0.0.1", ClientIpResolver.resolve(request(NGINX, "203.0.113.9"), false, PROXIES));
    }

    @Test
    @DisplayName("Lista vazia não confia em ninguém")
    void emptyListUsesPeer() {
        assertEquals(NGINX, ClientIpResolver.resolve(request(NGINX, "203.0.113.9"), true, ""));
    }

    @Test
    @DisplayName("Vizinho que não é proxy de confiança: X-Forwarded-For é ignorado")
    void untrustedPeerIgnoresHeader() {
        assertEquals("198.51.100.7",
                ClientIpResolver.resolve(request("198.51.100.7", "203.0.113.9"), true, PROXIES));
    }

    @Test
    @DisplayName("Atrás do nginx, usa o IP real do cliente (o proxy é adicionado à direita)")
    void behindNginxUsesClient() {
        assertEquals("203.0.113.9",
                ClientIpResolver.resolve(request(NGINX, "203.0.113.9"), true, PROXIES));
    }

    @Test
    @DisplayName("IP forjado à esquerda do que o nginx anexou é ignorado")
    void forgedLeftmostIgnored() {
        assertEquals("203.0.113.9",
                ClientIpResolver.resolve(request(NGINX, "1.2.3.4, 203.0.113.9"), true, PROXIES));
    }

    @Test
    @DisplayName("Cadeia com outro proxy de confiança à direita: pula os proxies")
    void skipsTrustedHops() {
        assertEquals("203.0.113.9",
                ClientIpResolver.resolve(request(NGINX, "203.0.113.9, 172.18.0.5"), true, PROXIES));
    }

    @Test
    @DisplayName("Valor inválido no cabeçalho cai no vizinho direto")
    void invalidValueFallsBackToPeer() {
        assertEquals(NGINX,
                ClientIpResolver.resolve(request(NGINX, "<script>"), true, PROXIES));
    }

    @Test
    @DisplayName("Subida recusa TRUSTED_PROXIES com entrada inválida")
    void validatorRejectsInvalidEntry() {
        TrustedProxiesValidator validator = new TrustedProxiesValidator("127.0.0.1,isto-nao-e-ip");
        assertThrows(IllegalStateException.class, validator::validate);
    }
}
