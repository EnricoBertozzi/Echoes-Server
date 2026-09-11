package com.n0hana.echoes_server.user;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.user.dto.CreateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UpdateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final UserService service;

    @PostMapping
    public ResponseEntity<UserDTO> create(@RequestBody @Valid CreateInstitutionUserDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTeacher(dto));
    }

    @GetMapping
    public ResponseEntity<Page<UserDTO>> findAll(
            @RequestParam(required = false) UUID institutionId, Pageable pageable) {
        return ResponseEntity.ok(service.findTeachers(institutionId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findTeacherById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserDTO> update(@PathVariable UUID id, @RequestBody @Valid UpdateInstitutionUserDTO dto) {
        return ResponseEntity.ok(service.updateTeacher(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteTeacher(id);
        return ResponseEntity.noContent().build();
    }
}
