package com.n0hana.echoes_server.infra.web;

/**
 * Limites de paginação vindos do cliente (S19): evita consultas gigantes (DoS).
 */
public final class PageLimits {

    public static final int MAX_PAGE_SIZE = 100;

    private PageLimits() {
    }

    /** tamanho de página entre 1 e {@value #MAX_PAGE_SIZE} */
    public static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /** número da página nunca negativo */
    public static int clampPage(int page) {
        return Math.max(page, 0);
    }
}
