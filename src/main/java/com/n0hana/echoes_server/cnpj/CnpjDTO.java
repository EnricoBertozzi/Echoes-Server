package com.n0hana.echoes_server.cnpj;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Retorno tipado da Brasil API para consulta de CNPJ.
 *
 * Usa {@code @JsonAlias} (não {@code @JsonProperty}) para mapear o snake_case
 * da API sem alterar a serialização — os acessores continuam camelCase.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CnpjDTO(
    String cnpj,
    @JsonAlias("razao_social") String razaoSocial,
    @JsonAlias("nome_fantasia") String nomeFantasia,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String municipio,
    String uf,
    String cep,
    @JsonAlias("add_telefone_1") String telefone
) {}
