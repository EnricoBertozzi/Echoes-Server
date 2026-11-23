package com.n0hana.echoes_server.infra.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Descobre o IP real do cliente atrás de um proxy reverso (nginx).
 *
 * <p>
 * O {@code X-Forwarded-For} só é lido quando o vizinho direto da conexão (o
 * {@code getRemoteAddr()}) é um proxy de confiança, listado em
 * {@code TRUSTED_PROXIES}. Sem isso, qualquer cliente poderia forjar o IP
 * gravado na auditoria, no limite de tentativas e no alerta de novo acesso.
 * </p>
 *
 * <p>
 * Quando há proxies na cadeia, percorre o cabeçalho da direita para a
 * esquerda e usa o primeiro endereço que não é proxy de confiança. Entradas
 * à esquerda dele são informadas pelo próprio cliente e são ignoradas.
 * </p>
 */
public final class ClientIpResolver {

    private static final int MAX_IP_LENGTH = 45;

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request, boolean trustForwardedFor, String trustedProxies) {
        String peer = request.getRemoteAddr();
        if (!trustForwardedFor || trustedProxies == null || trustedProxies.isBlank()) {
            return peer;
        }
        List<IpAddressMatcher> proxies = parse(trustedProxies);
        if (!isTrusted(peer, proxies)) {
            return peer;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return peer;
        }

        List<String> chain = new ArrayList<>();
        for (String entry : forwarded.split(",")) {
            String ip = entry.trim();
            if (!ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                chain.add(ip);
            }
        }
        if (chain.isEmpty()) {
            return peer;
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            String ip = chain.get(i);
            if (!isValidLiteral(ip)) {
                return peer;
            }
            if (!isTrusted(ip, proxies)) {
                return ip;
            }
        }
        return chain.get(0);
    }

    /**
     * Converte a lista {@code TRUSTED_PROXIES} (IPs ou CIDR, separados por vírgula).
     * Entradas inválidas são ignoradas aqui; a validação na subida do servidor
     * recusa a configuração.
     */
    public static List<IpAddressMatcher> parse(String trustedProxies) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String entry : trustedProxies.split(",")) {
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                matchers.add(new IpAddressMatcher(value));
            } catch (IllegalArgumentException ignored) {
                // validado na subida; em tempo de execução, entrada inválida não confia em ninguém
            }
        }
        return matchers;
    }

    static boolean isTrusted(String ip, List<IpAddressMatcher> proxies) {
        if (!isValidLiteral(ip)) {
            return false;
        }
        for (IpAddressMatcher proxy : proxies) {
            if (proxy.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    /** Aceita apenas caracteres de endereço IPv4/IPv6 e limita o tamanho (a coluna da auditoria tem tamanho fixo). */
    static boolean isValidLiteral(String ip) {
        if (ip == null || ip.isEmpty() || ip.length() > MAX_IP_LENGTH) {
            return false;
        }
        for (int i = 0; i < ip.length(); i++) {
            char c = ip.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
