package com.n0hana.echoes_server.institution;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.br.CNPJ;

import java.util.UUID;

/**
 * DTO para consumo de endpoints relacionados as instituições
 * 
 * @param id      Identificador único da instituição
 * @param name    Nome da instituição
 * @param acronym Acrônimo da instituiçãp
 * @param cnpj    Cnpj da instituição
 * @param email   Email de contato
 * @param phone   Telefone de contato
 * @param address Endereço da instituição
 * @param active  Estado da instituição.
 */
public record InstitutionDTO(
        UUID id,
        @NotBlank(message = "O nome é obrigatório") String name,
        @NotBlank(message = "A sigla é obrigatória") String acronym,
        @NotBlank(message = "O CNPJ é obrigatório") @CNPJ(message = "CNPJ inválido") String cnpj,
        @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,
        String phone,
        String address,
        Boolean active) {

    /**
     * Mapeia um objeto do domínio da aplicação {@code InstitutionModel} para um
     * objeto de DTO {@code InstitutionDTO}.
     * 
     * @param model Objeto do domínio da aplicação.
     * 
     * @return {@link InstitutionDTO} com os dados mapeados do modelo.
     */
    public static InstitutionDTO fromModel(InstitutionModel model) {
        return new InstitutionDTO(
                model.getId(),
                model.getName(),
                model.getAcronym(),
                model.getCnpj(),
                model.getEmail(),
                model.getPhone(),
                model.getAddress(),
                model.isActive());
    }

    /**
     * Mapeia o objeto {@code InstitutionDTO} para um objeto do domínio da aplicação
     * {@code InstitutionModel}
     * 
     * @return {@link InstitutionModel} com os dados mapeados do DTO.
     */
    public InstitutionModel toModel() {
        return new InstitutionModel.InstitutionModelBuilder()
                .id(this.id())
                .name(this.name())
                .acronym(this.acronym())
                .cnpj(this.cnpj())
                .email(this.email())
                .phone(this.phone())
                .address(this.address())
                .build();
    }

}
