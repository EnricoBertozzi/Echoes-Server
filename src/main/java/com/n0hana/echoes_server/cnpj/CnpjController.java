package com.n0hana.echoes_server.cnpj;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Endpoint público de consulta de CNPJ.
 *
 * <p>
 * Usado pelo frontend para autopreencher o formulário de nova instituição.
 * Contrato: 200 com {@link CnpjDTO}; 400 CNPJ inválido; 404 não encontrado;
 * 503 todos os provedores indisponíveis (front permite cadastro manual).
 * </p>
 */
@RestController
@RequestMapping("/api/v1/cnpj")
@RequiredArgsConstructor
public class CnpjController {

    private final CnpjService cnpjService;

    @GetMapping("/{cnpj}")
    public ResponseEntity<CnpjDTO> consultar(@PathVariable("cnpj") String cnpj) {
        return ResponseEntity.ok(cnpjService.consultar(cnpj));
    }
}
