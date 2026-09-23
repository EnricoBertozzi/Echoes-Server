package com.n0hana.echoes_server.cnpj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.cnpj.exception.CnpjInvalidoException;

class CnpjValidatorTest {

    @Test
    @DisplayName("Aceita CNPJ numérico válido sem máscara")
    void aceitaCnpjValidoSemMascara() {
        assertEquals("19131243000197", CnpjValidator.normalizarEValidar("19131243000197"));
    }

    @Test
    @DisplayName("Aceita CNPJ válido com máscara e retorna normalizado")
    void aceitaCnpjValidoComMascara() {
        assertEquals("19131243000197", CnpjValidator.normalizarEValidar("19.131.243/0001-97"));
    }

    @Test
    @DisplayName("Rejeita CNPJ com todos os dígitos repetidos")
    void rejeitaCnpjComDigitoRepetido() {
        assertThrows(CnpjInvalidoException.class,
            () -> CnpjValidator.normalizarEValidar("11.111.111/1111-11"));
    }

    @Test
    @DisplayName("Rejeita CNPJ com dígito verificador inválido")
    void rejeitaCnpjComDvInvalido() {
        assertThrows(CnpjInvalidoException.class,
            () -> CnpjValidator.normalizarEValidar("19.131.243/0001-99"));
    }

    @Test
    @DisplayName("Rejeita CNPJ com tamanho incorreto")
    void rejeitaCnpjTamanhoIncorreto() {
        assertThrows(CnpjInvalidoException.class, () -> CnpjValidator.normalizarEValidar("123"));
        assertThrows(CnpjInvalidoException.class,
            () -> CnpjValidator.normalizarEValidar("19.131.243/0001-9700"));
    }

    @Test
    @DisplayName("Rejeita CNPJ nulo ou em branco")
    void rejeitaCnpjNuloOuEmBranco() {
        assertThrows(CnpjInvalidoException.class, () -> CnpjValidator.normalizarEValidar(null));
        assertThrows(CnpjInvalidoException.class, () -> CnpjValidator.normalizarEValidar("   "));
    }

    @Test
    @DisplayName("normalizar preserva letras e remove máscara")
    void normalizarPreservaLetras() {
        assertEquals("12ABC34501DE35", CnpjValidator.normalizar("12.ABC.345/01DE-35"));
    }

    @Test
    @DisplayName("normalizar retorna null para entrada null")
    void normalizarRetornaNullParaNull() {
        assertNull(CnpjValidator.normalizar(null));
    }

    @Test
    @DisplayName("normalizarEValidar aceita CNPJ alfanumérico com 14 caracteres")
    void aceitaCnpjAlfanumerico() {
        assertEquals("12ABC34501DE35", CnpjValidator.normalizarEValidar("12ABC34501DE35"));
    }
}
