package com.n0hana.echoes_server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.dto.ClassroomDTO;
import com.n0hana.echoes_server.model.Classroom;
import com.n0hana.echoes_server.model.ClassroomContent;
import com.n0hana.echoes_server.model.Enrollment;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.model.UserRole;
import com.n0hana.echoes_server.repository.ClassroomContentRepository;
import com.n0hana.echoes_server.repository.ClassroomRepository;
import com.n0hana.echoes_server.repository.EnrollmentRepository;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceTest {

    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private ClassroomContentRepository contentRepository;

    @InjectMocks
    private ClassroomService classroomService;

    private User teacher;
    private User student;
    private Classroom classroom;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(UUID.randomUUID());
        teacher.setName("Professor Hugo");
        teacher.setRole(UserRole.TEACHER);

        student = new User();
        student.setId(UUID.randomUUID());
        student.setName("Aluno Teste");
        student.setRole(UserRole.STUDENT);

        classroom = new Classroom();
        classroom.setId(UUID.randomUUID());
        classroom.setName("Segurança Ofensiva");
        classroom.setCode("A1B2C3D4");
        classroom.setTeacher(teacher);
        classroom.setCreatedAt(Instant.now());
    }

    @Test
    void createClassroom_ShouldReturnResponse_WhenValidData() {
        // Arrange
        ClassroomDTO.CreateRequest request = new ClassroomDTO.CreateRequest("Segurança Ofensiva", "Introdução a Pentest");
        when(classroomRepository.findByCode(anyString())).thenReturn(Optional.empty()); // Simula código não existente
        when(classroomRepository.save(any(Classroom.class))).thenReturn(classroom);

        // Act
        ClassroomDTO.ClassroomResponse response = classroomService.createClassroom(request, teacher);

        // Assert
        assertNotNull(response);
        assertEquals(classroom.getName(), response.name());
        verify(classroomRepository, times(1)).save(any(Classroom.class));
    }

    @Test
    void enrollStudent_ShouldEnroll_WhenCodeIsValidAndNotEnrolled() {
        // Arrange
        when(classroomRepository.findByCode(classroom.getCode())).thenReturn(Optional.of(classroom));
        when(enrollmentRepository.existsByStudentAndClassroom(student, classroom)).thenReturn(false);
        
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setClassroom(classroom);
        when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);

        // Act
        ClassroomDTO.ClassroomResponse response = classroomService.enrollStudent(classroom.getCode(), student);

        // Assert
        assertNotNull(response);
        assertEquals(classroom.getName(), response.name());
        verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
    }

    @Test
    void enrollStudent_ShouldThrowException_WhenAlreadyEnrolled() {
        // Arrange
        when(classroomRepository.findByCode(classroom.getCode())).thenReturn(Optional.of(classroom));
        when(enrollmentRepository.existsByStudentAndClassroom(student, classroom)).thenReturn(true);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            classroomService.enrollStudent(classroom.getCode(), student);
        });
        assertEquals("Você já está matriculado nesta turma.", exception.getMessage());
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void addContent_ShouldAdd_WhenUserIsTeacherOfClassroom() {
        // Arrange
        ClassroomDTO.CreateContentRequest request = new ClassroomDTO.CreateContentRequest("OSINT", "Aula sobre OSINT.");
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        
        ClassroomContent content = new ClassroomContent();
        content.setId(UUID.randomUUID());
        content.setTitle(request.title());
        content.setBody(request.body());
        content.setClassroom(classroom);
        content.setCreatedAt(Instant.now());
        
        when(contentRepository.save(any(ClassroomContent.class))).thenReturn(content);

        // Act
        ClassroomDTO.ContentResponse response = classroomService.addContent(classroom.getId(), request, teacher);

        // Assert
        assertNotNull(response);
        assertEquals("OSINT", response.title());
        verify(contentRepository, times(1)).save(any(ClassroomContent.class));
    }

    @Test
    void addContent_ShouldThrowException_WhenUserIsNotTheTeacher() {
        // Arrange
        ClassroomDTO.CreateContentRequest request = new ClassroomDTO.CreateContentRequest("OSINT", "Aula");
        User anotherTeacher = new User();
        anotherTeacher.setId(UUID.randomUUID());
        
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            classroomService.addContent(classroom.getId(), request, anotherTeacher);
        });
        assertEquals("Apenas o professor da turma pode adicionar conteúdo.", exception.getMessage());
    }
}
