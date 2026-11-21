package com.n0hana.echoes_server.infra.logs;

import java.util.regex.Pattern;

/**
 * L11 / RNF10: tira dados pessoais e credenciais de texto livre que vai para o
 * {@code audit_log.details} ou para os logs (mensagens de exceção incluídas).
 *
 * <p>
 * Cobre e-mail, JWT, {@code Bearer <token>}, pares {@code senha/password/token/secret=valor}
 * e CPF formatado. O nome de pessoa não tem formato reconhecível: quem monta a
 * mensagem não deve incluí-lo (o {@code audit_log} guarda o id do autor).
 * </p>
 */
public final class PiiMasker {

    public static final String EMAIL = "[e-mail oculto]";
    public static final String TOKEN = "[token oculto]";
    public static final String SECRET = "[oculto]";
    public static final String CPF = "[cpf oculto]";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
    private static final Pattern JWT_PATTERN =
            Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_PAIR_PATTERN =
            Pattern.compile("(?i)\\b(password|passwd|senha|secret|token|api[-_]?key|authorization)\\b(\\s*[=:]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&]+)");
    private static final Pattern CPF_PATTERN = Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b");

    private PiiMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String masked = BEARER_PATTERN.matcher(text).replaceAll("Bearer " + TOKEN);
        masked = JWT_PATTERN.matcher(masked).replaceAll(TOKEN);
        masked = SECRET_PAIR_PATTERN.matcher(masked).replaceAll("$1$2" + SECRET);
        masked = EMAIL_PATTERN.matcher(masked).replaceAll(EMAIL);
        return CPF_PATTERN.matcher(masked).replaceAll(CPF);
    }

    /** Versão para campos com limite de tamanho: mascara e corta. */
    public static String mask(String text, int maxLength) {
        String masked = mask(text);
        return masked != null && masked.length() > maxLength ? masked.substring(0, maxLength) : masked;
    }
}
