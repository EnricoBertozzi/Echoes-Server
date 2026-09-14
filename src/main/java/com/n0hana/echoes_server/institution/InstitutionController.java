package com.n0hana.echoes_server.institution;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/institutions")
@RequiredArgsConstructor
public class InstitutionController {

    private final InstitutionService service;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InstitutionDTO> create(@RequestBody @Valid InstitutionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @GetMapping
    public ResponseEntity<Page<InstitutionDTO>> findAll(
        @RequestParam(required = false) String name,
        @RequestParam(defaultValue = "0")       int page,
        @RequestParam(defaultValue = "10")      int size,
        @RequestParam(defaultValue = "name,asc") String sort) {

    Sort sortSpec = parseSort(sort);
    Pageable pageable = PageRequest.of(page, size, sortSpec);

    return ResponseEntity.ok(service.findAll(name, pageable));
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].equalsIgnoreCase("desc"))
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    return Sort.by(direction, field);
    }     

    @GetMapping("/{id}")
    public ResponseEntity<InstitutionDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InstitutionDTO> update(
            @PathVariable UUID id, @RequestBody @Valid InstitutionDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> toggleStatus(@PathVariable UUID id) {
        service.toggleStatus(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
