package com.n0hana.echoes_server.institution;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.n0hana.echoes_server.infra.security.SecurityConfig;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.List;
import java.util.UUID;

/**
 * Controlador REST responsável pelo gerenciamento de instituições.
 * 
 * @author Miguel Santana da Costa
 * @since 0.1.0
 * @see {@link InstitutionModel}
 * @see {@link InstitutionService}
 */
@RestController
@RequestMapping("/api/v1/institutions")
@SecurityRequirement(name = SecurityConfig.SECURITY)
public class InstitutionController {

    @Autowired
    private InstitutionService service;

    /**
     * Registra uma nova instituição no sistema.
     *
     * @param dto Objeto contendo os dados de cadastro de uma instituição.
     * @return {@link ResponseEntity} contendo a instituição criada e o status HTTP
     *         201 (Created).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InstitutionDTO> create(@RequestBody @Valid InstitutionDTO dto) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(InstitutionDTO.fromModel(service.create(dto.toModel())));
    }

    /**
     * Busca as instituições cadastradas no sistema.
     *
     * @param name Nome da instituição para filtro.
     * @param page Número da página para consulta no banco de dados.
     * @param size Número de resultados por página.
     * @param sort Ordenação dos elementos.
     * @return {@link ResponseEntity} contendo as instituições cadastradas e o
     *         status HTTP 200 (SUCCESS).
     */
    @GetMapping
    public ResponseEntity<List<InstitutionDTO>> findAll(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {

        List<InstitutionDTO> list = service.findAll(name, size, page, sort)
                .stream()
                .map(InstitutionDTO::fromModel)
                .toList();
        return ResponseEntity.ok(list);
    }

    /**
     * Busca uma instituição filtrando por id.
     *
     * @param id Id da instituição para filtro.
     * @return {@link ResponseEntity} contendo a instituição e o status HTTP 200
     *         (SUCCESS).
     */
    @GetMapping("/{id}")
    public ResponseEntity<InstitutionDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(InstitutionDTO.fromModel(service.findById(id)));
    }

    /**
     * Atualiza os dados de uma instituição.
     *
     * @param id  Id da instituição para atualização.
     * @param dto Objeto contendo os dados para alteração.
     * 
     * @return {@link ResponseEntity} contendo a instituição e o status HTTP 200
     *         (SUCCESS).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InstitutionDTO> update(
            @PathVariable UUID id, @RequestBody @Valid InstitutionDTO dto) {
        InstitutionDTO responseDTO = InstitutionDTO.fromModel(service.update(id, dto.toModel()));
        return ResponseEntity.ok(responseDTO);
    }

    /**
     * Altera o estado ativo de uma instituição.
     *
     * @param id Id da instituição para alteração.
     * 
     * @return {@link ResponseEntity} contendo a instituição e o status HTTP 204 (NO
     *         CONTENT).
     */
    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> toggleStatus(@PathVariable UUID id) {
        service.toggleStatus(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Desativa uma instituição do sistema.
     *
     * @param id Id da instituição para desativação.
     * 
     * @return {@link ResponseEntity} sem conteúdo e com o status HTTP 204 (NO
     *         CONTENT).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
