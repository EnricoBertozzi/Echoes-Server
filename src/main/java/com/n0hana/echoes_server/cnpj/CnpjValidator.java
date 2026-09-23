package com.n0hana.echoes_server.cnpj;

import com.n0hana.echoes_server.cnpj.exception.CnpjInvalidoException;

/**
 * Validação e normalização de CNPJ.
 *
 * <p>
 * Preparado para CNPJ alfanumérico (2026): a normalização preserva letras
 * maiúsculas, e a validação de dígito verificador só é aplicada quando a
 * entrada é estritamente numérica.
 * </p>
 */
public final class CnpjValidator {

    private CnpjValidator() {}

    /**
     * Normaliza o CNPJ para 14 caracteres {@code [A-Z0-9]}, sem validar DV.
     * Retorna {@code null} se a entrada for {@code null}.
     *
     * <p>Use quando o CNPJ já foi validado a montante (por Bean Validation,
     * por exemplo) e só precisa ser canonizado.</p>
     */
    public static String normalizar(String cnpj) {
        if (cnpj == null) {
            return null;
        }
        return cnpj.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /**
     * Normaliza e valida o CNPJ. Lança {@link CnpjInvalidoException} se o
     * valor for vazio, tamanho incorreto, todos dígitos repetidos, ou DV
     * inválido (quando numérico).
     */
    public static String normalizarEValidar(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) {
            throw new CnpjInvalidoException("CNPJ não pode ser vazio");
        }

        String normalizado = normalizar(cnpj);

        if (normalizado.length() != 14) {
            throw new CnpjInvalidoException("CNPJ deve conter 14 caracteres");
        }

        if (normalizado.matches("^(\\d)\\1{13}$")) {
            throw new CnpjInvalidoException("CNPJ não pode ter todos os dígitos repetidos");
        }

        // DV só é aplicável ao formato numérico legado; alfanumérico fica para 2026
        if (normalizado.matches("\\d{14}") && !isDigitosValidos(normalizado)) {
            throw new CnpjInvalidoException("Dígitos verificadores do CNPJ são inválidos");
        }

        return normalizado;
    }

    private static boolean isDigitosValidos(String cnpj) {
        int sm = 0, peso = 2, r, num;
        for (int i = 11; i >= 0; i--) {
            num = cnpj.charAt(i) - 48;
            sm += num * peso;
            peso = (peso == 9) ? 2 : peso + 1;
        }
        r = sm % 11;
        char dig13 = (r == 0 || r == 1) ? '0' : (char) ((11 - r) + 48);

        sm = 0;
        peso = 2;
        for (int i = 12; i >= 0; i--) {
            num = cnpj.charAt(i) - 48;
            sm += num * peso;
            peso = (peso == 9) ? 2 : peso + 1;
        }
        r = sm % 11;
        char dig14 = (r == 0 || r == 1) ? '0' : (char) ((11 - r) + 48);

        return dig13 == cnpj.charAt(12) && dig14 == cnpj.charAt(13);
    }
}
