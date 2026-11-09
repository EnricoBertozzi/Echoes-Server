package com.n0hana.echoes_server.classroom;

import java.util.UUID;

/**
 * DTO para exibição de informação sobre as turmas do sistema
 * 
 * @param id              Identificador da turma
 * @param title           Titutlo da turma
 * @param description     Descrição da turma
 * @param institutionId   Identificador da instituição
 * @param institutionName Nome da instituição
 * @param period          Périodo da turma
 * @param members         Quantidade de membros
 * @param status          Estado da turma
 */
public record ClassroomInfoDTO(
        UUID id,
        String title,
        String description,
        UUID institutionId,
        String institutionName,
        String period,
        Integer members,
        ClassroomStatus status) {

    /**
     * Transforma os dados de um objeto do domínio da aplicação em um DTO
     * 
     * @param classroom Objeto do domínio da aplicação
     * @return {@link ClassroomInfoDTO} DTO com dados da turma
     */
    public static ClassroomInfoDTO from(ClassroomModel classroom) {
        return new ClassroomInfoDTO(
                classroom.getId(),
                classroom.getTitle(),
                classroom.getDescription(),
                classroom.getInstitution().getId(),
                classroom.getInstitution().getName(),
                classroom.getPeriod(),
                classroom.getMembers(),
                classroom.getStatus());
    }
}
