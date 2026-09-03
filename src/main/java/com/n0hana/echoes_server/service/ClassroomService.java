package com.n0hana.echoes_server.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.dto.ClassroomDTO;
import com.n0hana.echoes_server.model.Classroom;
import com.n0hana.echoes_server.model.ClassroomContent;
import com.n0hana.echoes_server.model.Enrollment;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.repository.ClassroomContentRepository;
import com.n0hana.echoes_server.repository.ClassroomRepository;
import com.n0hana.echoes_server.repository.EnrollmentRepository;
import com.n0hana.echoes_server.service.logs.Auditable;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ClassroomService {

  private final ClassroomRepository classroomRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final ClassroomContentRepository contentRepository;

  @Auditable(action = "Criação de Turma", entity = "CLASSROOOM")
  public ClassroomDTO.ClassroomResponse createClassroom(ClassroomDTO.CreateRequest dto, User teacher) {
    Classroom classroom = new Classroom();
    classroom.setName(dto.name());
    classroom.setDescription(dto.description());
    classroom.setTeacher(teacher);

    // Gera um código alfanumerico unico de 8 ClassroomContentRepository
    String code;
    do {
      code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    } while (classroomRepository.findByCode(code).isPresent());

    classroom.setCode(code);
    classroom = classroomRepository.save(classroom);

    return this.toResponse(classroom);
  }

  @Auditable(action = "Matricula de Aluno em Turma", entity = "ENROLLMENT")
  public ClassroomDTO.ClassroomResponse enrollStudent(String code, User student) {
    Classroom classroom = classroomRepository.findByCode(code)
        .orElseThrow(() -> new RuntimeException("Código da turma invalido ou não encontrado."));

    if (enrollmentRepository.existsByStudentAndClassroom(student, classroom)) {
      throw new RuntimeException("Você já está matriculado nesta turma");
    }

    Enrollment enrollment = new Enrollment();
    enrollment.setStudent(student);
    enrollment.setClassroom(classroom);
    enrollmentRepository.save(enrollment);

    return this.toResponse(classroom);
  }

public List<ClassroomDTO.ClassroomResponse> getTeacherClassrooms(User teacher) {
    return classroomRepository.findAllByTeacher(teacher)
        .stream().map(this::toResponse).toList();
}

public List<ClassroomDTO.ClassroomResponse> getStudentClassrooms(User student) {
    return enrollmentRepository.findAllByStudent(student)
        .stream().map(e -> toResponse(e.getClassroom())).toList();

}

  @Auditable(action = "Criação de Conteúdo da Turma", entity = "CLASSROOM_CONTENT")
  public ClassroomDTO.ContentResponse addContent(UUID classroomId, ClassroomDTO.CreateContentRequest dto,
      User teacher) {
    Classroom classroom = classroomRepository.findById(classroomId)
        .orElseThrow(() -> new RuntimeException("Turma não encontrada."));

    if (!classroom.getTeacher().getId().equals(teacher.getId())) {
      throw new RuntimeException("Apenas o professor da turma pode adicionar conteúdo.");
    }

    ClassroomContent content = new ClassroomContent();
    content.setClassroom(classroom);
    content.setTitle(dto.title());
    content.setBody(dto.body());
    content = contentRepository.save(content);

    return new ClassroomDTO.ContentResponse(content.getId(), content.getTitle(), content.getBody(),
        content.getCreatedAt());
  }

  public List<ClassroomDTO.ContentResponse> getClassroomContents(UUID classroomId, User user) {
    Classroom classroom = classroomRepository.findById(classroomId)
        .orElseThrow(() -> new RuntimeException("Turma não encontrada."));

    // Verifica se é o professor ou um aluno matriculado
    boolean isTeacher = classroom.getTeacher().getId().equals(user.getId());
    boolean isStudentEnrolled = enrollmentRepository.existsByStudentAndClassroom(user, classroom);

    if (!isTeacher && !isStudentEnrolled) {
      throw new RuntimeException("Acesso negado. Você não pertence a esta turma.");
    }

    return contentRepository.findAllByClassroomOrderByCreatedAtDesc(classroom)
        .stream()
        .map(c -> new ClassroomDTO.ContentResponse(c.getId(), c.getTitle(), c.getBody(), c.getCreatedAt()))
        .toList();
  }

  private ClassroomDTO.ClassroomResponse toResponse(Classroom classroom) {
    return new ClassroomDTO.ClassroomResponse(
        classroom.getId(),
        classroom.getName(),
        classroom.getDescription(),
        classroom.getCode(),
        classroom.getTeacher().getName(),
        classroom.getCreatedAt());
  };
}
