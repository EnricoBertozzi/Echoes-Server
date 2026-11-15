package com.n0hana.echoes_server.classroom;

import java.security.Principal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ClassroomDTO.Info> create(@RequestBody @Valid ClassroomDTO.Request dto,
            Principal principal) {
        ClassroomModel classroom = service.create(principal.getName(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClassroomDTO.Info.from(classroom));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER', 'STUDENT')")
    public ResponseEntity<Page<ClassroomDTO.Info>> findAll(@RequestParam(defaultValue = "") String name,
            Principal principal, Pageable pageable) {
        return ResponseEntity.ok(service.findAll(principal.getName(), name, pageable).map(ClassroomDTO.Info::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER', 'STUDENT')")
    public ResponseEntity<ClassroomDTO.Info> findById(@PathVariable UUID id, Principal principal) {
        ClassroomModel classroom = service.findById(principal.getName(), id);
        String code = service.findJoinCode(principal.getName(), id);
        return ResponseEntity.ok(ClassroomDTO.Info.from(classroom, code));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER', 'STUDENT')")
    public ResponseEntity<Page<ClassroomDTO.MemberInfo>> findMembers(@PathVariable UUID id, Principal principal,
            Pageable pageable) {
        return ResponseEntity.ok(service.findMembers(principal.getName(), id, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ClassroomDTO.Info> update(@PathVariable UUID id,
            @RequestBody @Valid ClassroomDTO.Request dto, Principal principal) {
        return ResponseEntity.ok(ClassroomDTO.Info.from(service.update(principal.getName(), id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        service.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> archive(@PathVariable UUID id, Principal principal) {
        service.archive(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/unarchive")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> unarchive(@PathVariable UUID id, Principal principal) {
        service.unarchive(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/code")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassroomDTO.CodeResponse> generateCode(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generateCode(principal.getName(), id));
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ClassroomDTO.Info> join(@RequestBody @Valid ClassroomDTO.JoinRequest dto,
            Principal principal) {
        return ResponseEntity.ok(ClassroomDTO.Info.from(service.join(principal.getName(), dto)));
    }

    @DeleteMapping("/{id}/leave")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ResponseEntity<Void> leave(@PathVariable UUID id, Principal principal) {
        service.leave(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> addMember(@PathVariable UUID id, @PathVariable UUID userId, Principal principal) {
        service.addMember(principal.getName(), id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId,
            Principal principal) {
        service.removeMember(principal.getName(), id, userId);
        return ResponseEntity.noContent().build();
    }

    // remove todos os alunos de uma vez, usado na virada de semestre
    @DeleteMapping("/{id}/students")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> clearStudents(@PathVariable UUID id, Principal principal) {
        service.clearStudents(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
