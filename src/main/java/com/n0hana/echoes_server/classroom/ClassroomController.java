package com.n0hana.echoes_server.classroom;

import java.security.Principal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.infra.security.SecurityConfig;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/classrooms")
@SecurityRequirement(name = SecurityConfig.SECURITY)
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService service;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassroomDTO.Info> create(@RequestBody @Valid ClassroomDTO.Request dto,
            Principal principal) {
        ClassroomModel classroom = service.create(principal.getName(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClassroomDTO.Info.from(classroom));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'STUDENT')")
    public ResponseEntity<Page<ClassroomDTO.Info>> findAll(Principal principal, Pageable pageable) {
        return ResponseEntity.ok(service.findAll(principal.getName(), pageable).map(ClassroomDTO.Info::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'STUDENT')")
    public ResponseEntity<ClassroomDTO.Info> findById(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok(ClassroomDTO.Info.from(service.findById(principal.getName(), id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassroomDTO.Info> update(@PathVariable UUID id,
            @RequestBody @Valid ClassroomDTO.Request dto, Principal principal) {
        return ResponseEntity.ok(ClassroomDTO.Info.from(service.update(principal.getName(), id, dto)));
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> archive(@PathVariable UUID id, Principal principal) {
        service.archive(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/unarchive")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> unarchive(@PathVariable UUID id, Principal principal) {
        service.unarchive(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
