package com.n0hana.echoes_server.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.n0hana.echoes_server.dto.ClassroomDTO;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.service.ClassroomService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    // --- Rotas do Professor ---

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassroomDTO.ClassroomResponse> createClassroom(
            @RequestBody @Valid ClassroomDTO.CreateRequest request,
            @AuthenticationPrincipal User teacher) {
        return ResponseEntity.ok(classroomService.createClassroom(request, teacher));
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<ClassroomDTO.ClassroomResponse>> getTeacherClassrooms(
            @AuthenticationPrincipal User teacher) {
        return ResponseEntity.ok(classroomService.getTeacherClassrooms(teacher));
    }

    @PostMapping("/{classroomId}/content")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassroomDTO.ContentResponse> addContent(
            @PathVariable UUID classroomId,
            @RequestBody @Valid ClassroomDTO.CreateContentRequest request,
            @AuthenticationPrincipal User teacher) {
        return ResponseEntity.ok(classroomService.addContent(classroomId, request, teacher));
    }

    // --- Rotas do Aluno ---

    @PostMapping("/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ClassroomDTO.ClassroomResponse> enrollStudent(
            @RequestBody @Valid ClassroomDTO.EnrollRequest request,
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(classroomService.enrollStudent(request.code(), student));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<ClassroomDTO.ClassroomResponse>> getStudentClassrooms(
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(classroomService.getStudentClassrooms(student));
    }

    // --- Rotas Compartilhadas (Professor e Aluno matriculado) ---

    @GetMapping("/{classroomId}/content")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<ClassroomDTO.ContentResponse>> getClassroomContents(
            @PathVariable UUID classroomId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(classroomService.getClassroomContents(classroomId, user));
    }
}
